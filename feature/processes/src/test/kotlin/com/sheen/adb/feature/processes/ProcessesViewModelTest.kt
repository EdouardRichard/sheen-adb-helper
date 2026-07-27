package com.sheen.adb.feature.processes

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ProcessFieldState
import com.sheen.adb.core.ProcessIdentity
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessTerminationOutcome
import com.sheen.adb.core.ProcessTerminationRequest
import com.sheen.adb.core.ProcessTerminationResult
import com.sheen.adb.core.ProcessTerminationScope
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNotEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ProcessesViewModelTest {
    @Test
    fun `visible foreground page refreshes immediately then sequentially every five virtual seconds`() = runBlocking {
        val manager = DeferredProcessesManager(connected("session-a"))
        val clock = VirtualRefreshClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ProcessesViewModel(
            manager = manager.instance,
            scope = scope,
            delayMillis = clock::delay,
        )

        viewModel.setForeground(true)
        assertEquals(manager.requestedSessions, emptyList<String>())
        viewModel.onPageVisible(true)
        assertEquals(manager.requestedSessions, listOf("session-a"), "first refresh must be immediate")

        manager.complete("session-a", AdbOperationResult.Success(listOf(entry(101, generation = 1))))
        clock.advanceBy(4_999)
        assertEquals(manager.requestedSessions.size, 1)
        clock.advanceBy(1)
        assertEquals(manager.requestedSessions, listOf("session-a", "session-a"))

        manager.complete("session-a", AdbOperationResult.Success(listOf(entry(101, generation = 2))))
        clock.advanceBy(5_000)
        assertEquals(manager.requestedSessions.size, 3)
        scope.cancel()
    }

    @Test
    fun `slow refresh never overlaps or accumulates missed virtual ticks`() = runBlocking {
        val manager = DeferredProcessesManager(connected("session-a"))
        val clock = VirtualRefreshClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ProcessesViewModel(manager.instance, scope, clock::delay)
        viewModel.setForeground(true)
        viewModel.onPageVisible(true)

        clock.advanceBy(30_000)
        assertEquals(manager.requestedSessions.size, 1, "slow request must suppress all overlapping ticks")
        assertEquals(manager.maxActiveRequests, 1)

        manager.complete("session-a", AdbOperationResult.Success(emptyList()))
        clock.advanceBy(4_999)
        assertEquals(manager.requestedSessions.size, 1)
        clock.advanceBy(1)
        assertEquals(manager.requestedSessions.size, 2, "next interval starts after the prior request completes")
        assertEquals(manager.maxActiveRequests, 1)
        scope.cancel()
    }

    @Test
    fun `leaving page backgrounding or disconnecting stops future refreshes`() = runBlocking {
        assertPollingStops { viewModel, _ -> viewModel.onPageVisible(false) }
        assertPollingStops { viewModel, _ -> viewModel.setForeground(false) }
        assertPollingStops { _, manager -> manager.connectionState.value = AdbConnectionState.Disconnected() }
    }

    @Test
    fun `session switch cancels old polling rejects late result and starts one replacement loop`() = runBlocking {
        val manager = DeferredProcessesManager(connected("session-old"))
        val clock = VirtualRefreshClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ProcessesViewModel(manager.instance, scope, clock::delay)
        viewModel.setForeground(true)
        viewModel.onPageVisible(true)
        assertEquals(manager.requestedSessions, listOf("session-old"))

        manager.connectionState.value = connected("session-new")
        assertEquals(manager.requestedSessions, listOf("session-old", "session-new"))
        manager.complete(
            "session-old",
            AdbOperationResult.Success(listOf(entry(901, sessionId = "session-old", generation = 1))),
        )
        assertEquals(viewModel.state.value.sessionId, "session-new")
        assertTrue(viewModel.state.value.entries.none { it.identity.sessionId == "session-old" })

        manager.complete(
            "session-new",
            AdbOperationResult.Success(listOf(entry(902, sessionId = "session-new", generation = 1))),
        )
        clock.advanceBy(5_000)
        assertEquals(manager.requestedSessions.last(), "session-new")
        assertEquals(manager.requestedSessions.count { it == "session-old" }, 1)
        scope.cancel()
    }

    @Test
    fun `whole application scope appears only for reliable package association`() {
        val associated = entry(101, generation = 4, packageName = "com.example.client")
        val unknown = entry(102, generation = 4)

        assertEquals(
            ProcessesPolicy.terminationScopes(associated),
            listOf(ProcessTerminationScope.SINGLE_PROCESS, ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP),
        )
        assertEquals(
            ProcessesPolicy.terminationScopes(unknown),
            listOf(ProcessTerminationScope.SINGLE_PROCESS),
        )
    }

    @Test
    fun `confirmation is one shot and cancellation produces no request`() {
        val entry = entry(101, generation = 4, packageName = "com.example.client")
        val first = ProcessesPolicy.newConfirmation(entry, ProcessTerminationScope.SINGLE_PROCESS)
        val second = ProcessesPolicy.newConfirmation(entry, ProcessTerminationScope.SINGLE_PROCESS)
        assertNotEquals(first.nonce, second.nonce)

        val cancelled = ProcessesPolicy.cancelConfirmation(
            ProcessesUiState(
                isConnected = true,
                sessionId = "session-a",
                entries = listOf(entry),
                pendingTermination = first,
            ),
        )
        assertEquals(cancelled.pendingTermination, null)
        assertEquals(cancelled.terminationRequestCount, 0)
        assertFalse(ProcessesPolicy.canConfirm(cancelled, first.nonce))
    }

    @Test
    fun `stale generation and session cannot confirm or replace current snapshot`() {
        val current = ProcessesUiState(
            isConnected = true,
            sessionId = "session-a",
            generation = 8,
            entries = listOf(entry(101, generation = 8)),
        )
        val stale = listOf(entry(102, generation = 7))
        val otherSession = listOf(entry(103, sessionId = "session-b", generation = 9))

        assertFalse(ProcessesPolicy.acceptSnapshot(current, stale))
        assertFalse(ProcessesPolicy.acceptSnapshot(current, otherSession))
        assertTrue(ProcessesPolicy.acceptSnapshot(current, listOf(entry(104, generation = 9))))
    }

    @Test
    fun `confirmed application set contains only the selected package and field states stay explicit`() {
        val selected = entry(101, generation = 4, packageName = "com.example.client")
        val worker = entry(102, generation = 4, packageName = "com.example.client")
        val other = entry(201, generation = 4, packageName = "com.example.other")

        assertEquals(
            ProcessesPolicy.confirmedApplicationSet(selected, listOf(selected, worker, other)),
            setOf(selected.identity, worker.identity),
        )
        assertEquals(selected.cpuState, ProcessFieldState.CALCULATING)
        assertEquals(selected.pssState, ProcessFieldState.UNKNOWN)
    }

    @Test
    fun `termination pauses polling until verification completes then resumes immediately`() = runBlocking {
        val manager = DeferredProcessesManager(connected("session-a"))
        val clock = VirtualRefreshClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ProcessesViewModel(manager.instance, scope, clock::delay)
        viewModel.setForeground(true)
        viewModel.onPageVisible(true)
        val target = entry(101, generation = 1)
        manager.complete("session-a", AdbOperationResult.Success(listOf(target)))

        viewModel.requestTermination(target)
        clock.advanceBy(20_000)
        assertEquals(manager.requestedSessions.size, 1, "confirmation must keep its process generation stable")
        viewModel.selectTerminationScope(ProcessTerminationScope.SINGLE_PROCESS)
        val nonce = checkNotNull(viewModel.state.value.pendingTermination).nonce
        viewModel.confirmTermination(nonce)
        assertTrue(viewModel.state.value.terminationInProgress)
        assertEquals(manager.terminationRequests.size, 1)

        clock.advanceBy(20_000)
        assertEquals(manager.requestedSessions.size, 1, "polling must not supersede termination verification")

        manager.completeTermination(
            AdbOperationResult.Success(
                ProcessTerminationResult(
                    sessionId = "session-a",
                    scope = ProcessTerminationScope.SINGLE_PROCESS,
                    outcome = ProcessTerminationOutcome.TERMINATED,
                    messageCode = "TARGET_EXIT_VERIFIED",
                ),
            ),
        )
        assertFalse(viewModel.state.value.terminationInProgress)
        assertEquals(manager.requestedSessions.size, 2, "polling must resume immediately after verification")
        scope.cancel()
    }

    private fun entry(
        pid: Int,
        sessionId: String = "session-a",
        generation: Long,
        packageName: String? = null,
    ) = ProcessSnapshotEntry(
        identity = ProcessIdentity(sessionId, pid, 900L + pid, "u0_a123", "process.$pid", generation),
        applicationPackage = packageName,
        cpuState = ProcessFieldState.CALCULATING,
        pssState = ProcessFieldState.UNKNOWN,
        parentPid = 1,
    )

    private suspend fun assertPollingStops(
        stop: (ProcessesViewModel, DeferredProcessesManager) -> Unit,
    ) {
        val manager = DeferredProcessesManager(connected("session-a"))
        val clock = VirtualRefreshClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ProcessesViewModel(manager.instance, scope, clock::delay)
        viewModel.setForeground(true)
        viewModel.onPageVisible(true)
        manager.complete("session-a", AdbOperationResult.Success(emptyList()))
        assertEquals(manager.requestedSessions.size, 1)

        stop(viewModel, manager)
        clock.advanceBy(60_000)
        assertEquals(manager.requestedSessions.size, 1)
        assertEquals(manager.activeRequestCount, 0)
        scope.cancel()
    }

    private fun connected(sessionId: String) = AdbConnectionState.Connected(
        endpoint = AdbEndpoint("$sessionId.invalid", 4711),
        sessionId = sessionId,
    )

    private class VirtualRefreshClock {
        private data class Waiter(
            val deadlineMillis: Long,
            val continuation: CancellableContinuation<Unit>,
        )

        private var nowMillis = 0L
        private val waiters = mutableListOf<Waiter>()

        suspend fun delay(durationMillis: Long) {
            require(durationMillis >= 0)
            suspendCancellableCoroutine { continuation ->
                val waiter = Waiter(nowMillis + durationMillis, continuation)
                waiters += waiter
                continuation.invokeOnCancellation { waiters.remove(waiter) }
            }
        }

        fun advanceBy(durationMillis: Long) {
            require(durationMillis >= 0)
            nowMillis += durationMillis
            val ready = waiters.filter { it.deadlineMillis <= nowMillis }
            waiters.removeAll(ready.toSet())
            ready.forEach { waiter ->
                if (waiter.continuation.isActive) waiter.continuation.resume(Unit)
            }
        }
    }

    private class DeferredProcessesManager(initialConnection: AdbConnectionState) {
        private data class Pending(
            val sessionId: String,
            val continuation: Continuation<AdbOperationResult<List<ProcessSnapshotEntry>>>,
        )

        val connectionState = MutableStateFlow(initialConnection)
        private val diagnosticEvents = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        private val pending = ArrayDeque<Pending>()
        private var pendingTermination:
            Continuation<AdbOperationResult<ProcessTerminationResult>>? = null
        val requestedSessions = mutableListOf<String>()
        val terminationRequests = mutableListOf<ProcessTerminationRequest>()
        var activeRequestCount = 0
            private set
        var maxActiveRequests = 0
            private set

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connectionState
                "getDiagnosticEvents" -> diagnosticEvents
                "refreshProcesses" -> {
                    val sessionId = args!![0] as String
                    @Suppress("UNCHECKED_CAST")
                    val continuation =
                        args.last() as Continuation<AdbOperationResult<List<ProcessSnapshotEntry>>>
                    requestedSessions += sessionId
                    pending += Pending(sessionId, continuation)
                    activeRequestCount += 1
                    maxActiveRequests = maxOf(maxActiveRequests, activeRequestCount)
                    COROUTINE_SUSPENDED
                }
                "terminateProcess" -> {
                    terminationRequests += args!![0] as ProcessTerminationRequest
                    @Suppress("UNCHECKED_CAST")
                    val continuation =
                        args.last() as Continuation<AdbOperationResult<ProcessTerminationResult>>
                    pendingTermination = continuation
                    COROUTINE_SUSPENDED
                }
                "clearDiagnosticEvents", "close" -> null
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        fun complete(
            sessionId: String,
            result: AdbOperationResult<List<ProcessSnapshotEntry>>,
        ) {
            val match = pending.first { it.sessionId == sessionId }
            pending.remove(match)
            activeRequestCount -= 1
            match.continuation.resume(result)
        }

        fun completeTermination(result: AdbOperationResult<ProcessTerminationResult>) {
            val continuation = checkNotNull(pendingTermination)
            pendingTermination = null
            continuation.resume(result)
        }
    }
}
