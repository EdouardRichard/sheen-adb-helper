package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.ExclusiveAdbOperationLease
import com.sheen.adb.core.QuickActionKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AdbExclusiveOperationCoordinatorTest {
    @Test
    fun `screenshot recording and reboot share one mutually exclusive lease group`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)

        QuickActionKind.entries.forEach { activeAction ->
            val active = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, sessionId)
                as AdbOperationResult.Success<ExclusiveAdbOperationLease>

            QuickActionKind.entries.forEach { requestedAction ->
                val conflict = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, sessionId)
                assertTrue(
                    conflict is AdbOperationResult.Failure &&
                        conflict.error is AdbError.OperationConflict,
                    "$requestedAction must conflict while $activeAction owns the quick-action lease",
                )
            }
            active.value.release()
        }
    }

    @Test
    fun `quick actions conflict with file transfer apk extraction and logcat in both directions`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)
        val existingLongOperations = listOf(
            AdbExclusiveOperationKind.FILE_TRANSFER,
            AdbExclusiveOperationKind.APK_EXTRACTION,
            AdbExclusiveOperationKind.LOGCAT,
        )

        existingLongOperations.forEach { longOperation ->
            val longLease = manager.acquireExclusiveOperation(longOperation, sessionId)
                as AdbOperationResult.Success<ExclusiveAdbOperationLease>
            val blockedQuickAction =
                manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, sessionId)
            assertTrue(blockedQuickAction is AdbOperationResult.Failure)
            assertSame(
                (blockedQuickAction as AdbOperationResult.Failure).error.let {
                    (it as AdbError.OperationConflict).activeKind
                },
                longOperation,
            )
            longLease.value.release()

            val quickActionLease =
                manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, sessionId)
                    as AdbOperationResult.Success<ExclusiveAdbOperationLease>
            val blockedLongOperation = manager.acquireExclusiveOperation(longOperation, sessionId)
            assertTrue(blockedLongOperation is AdbOperationResult.Failure)
            assertSame(
                (blockedLongOperation as AdbOperationResult.Failure).error.let {
                    (it as AdbError.OperationConflict).activeKind
                },
                AdbExclusiveOperationKind.QUICK_ACTION,
            )
            quickActionLease.value.release()
        }
    }

    @Test
    fun `quick action cancellation releases its lease`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)
        val started = CompletableDeferred<Unit>()
        val operation = launch {
            withQuickActionLease(manager, sessionId) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        operation.cancelAndJoin()

        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, sessionId)
                is AdbOperationResult.Success,
        )
    }

    @Test
    fun `quick action exception releases its lease`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)

        val failure = runCatching {
            withQuickActionLease(manager, sessionId) {
                error("synthetic quick-action failure")
            }
        }

        assertTrue(failure.isFailure)
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId)
                is AdbOperationResult.Success,
        )
    }

    @Test
    fun `quick action lease is invalidated when Session changes`() = runBlocking {
        val manager = connectedManager()
        val oldSessionId = connectedSessionId(manager)
        val oldLease =
            (manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, oldSessionId)
                as AdbOperationResult.Success<ExclusiveAdbOperationLease>).value

        manager.connect(AdbEndpoint("replacement.invalid", 40003))
        val newSessionId = connectedSessionId(manager)

        assertFalse(oldLease.isActive)
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, oldSessionId)
                .let { it is AdbOperationResult.Failure && it.error is AdbError.SessionInvalid },
        )
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, newSessionId)
                is AdbOperationResult.Success,
        )
    }

    @Test
    fun `atomic competition grants exactly one exclusive lease`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)

        val results = coroutineScope {
            List(32) {
                async(Dispatchers.Default) {
                    manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, sessionId)
                }
            }.awaitAll()
        }

        assertEquals(results.count { it is AdbOperationResult.Success<*> }, 1)
        assertEquals(results.count { it is AdbOperationResult.Failure }, 31)
        results.filterIsInstance<AdbOperationResult.Failure>().forEach {
            assertTrue(it.error is AdbError.OperationConflict)
        }
    }

    @Test
    fun `conflict preserves the existing lease`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)
        val first = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId)
            as AdbOperationResult.Success<ExclusiveAdbOperationLease>

        val conflict = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.APK_EXTRACTION, sessionId)

        assertTrue(first.value.isActive)
        assertTrue(conflict is AdbOperationResult.Failure)
        assertEquals((conflict as AdbOperationResult.Failure).error.technicalCode, "OPERATION_CONFLICT")
        assertSame((conflict.error as AdbError.OperationConflict).activeKind, AdbExclusiveOperationKind.LOGCAT)
    }

    @Test
    fun `lease is bound to its session and invalidated by session switch`() = runBlocking {
        val manager = connectedManager()
        val oldSessionId = connectedSessionId(manager)
        val oldLease = (manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, oldSessionId)
            as AdbOperationResult.Success<ExclusiveAdbOperationLease>).value

        manager.connect(AdbEndpoint("replacement.invalid", 40002))
        val newSessionId = connectedSessionId(manager)

        assertFalse(oldLease.isActive)
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.APK_EXTRACTION, oldSessionId)
                .let { it is AdbOperationResult.Failure && it.error is AdbError.SessionInvalid },
        )
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.APK_EXTRACTION, newSessionId)
                is AdbOperationResult.Success,
        )
    }

    @Test
    fun `release is idempotent and permits the next operation`() = runBlocking {
        val manager = connectedManager()
        val sessionId = connectedSessionId(manager)
        val lease = (manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, sessionId)
            as AdbOperationResult.Success<ExclusiveAdbOperationLease>).value

        lease.release()
        lease.release()
        lease.close()

        assertFalse(lease.isActive)
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId)
                is AdbOperationResult.Success,
        )
    }

    @Test
    fun `only long running operation kinds require a lease`() {
        assertEquals(
            AdbExclusiveOperationKind.entries.toSet(),
            setOf(
                AdbExclusiveOperationKind.FILE_TRANSFER,
                AdbExclusiveOperationKind.APK_EXTRACTION,
                AdbExclusiveOperationKind.LOGCAT,
                AdbExclusiveOperationKind.QUICK_ACTION,
            ),
        )
    }

    private suspend fun <T> withQuickActionLease(
        manager: DefaultAdbSessionManager,
        sessionId: String,
        block: suspend () -> T,
    ): T {
        val lease = (
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.QUICK_ACTION, sessionId)
                as AdbOperationResult.Success<ExclusiveAdbOperationLease>
            ).value
        return try {
            block()
        } finally {
            withContext(NonCancellable) { lease.release() }
        }
    }

    private suspend fun connectedManager(): DefaultAdbSessionManager =
        DefaultAdbSessionManager(SingleFactory(), Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("synthetic.invalid", 40001)) is AdbOperationResult.Success)
        }

    private fun connectedSessionId(manager: DefaultAdbSessionManager): String =
        (manager.connectionState.value as AdbConnectionState.Connected).sessionId

    private class SingleFactory : AdbProtocolClientFactory {
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = Client()
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private class Client : AdbProtocolClient {
        override fun execute(command: String) =
            ProtocolShellResponse("ready\n", "", 0, streamsSeparated = true, wasTruncated = false)

        override fun openShellStream(command: String): ProtocolShellStream = object : ProtocolShellStream {
            override fun read(): ProtocolShellPacket = ProtocolShellPacket.Exit(0)
            override fun close() = Unit
        }

        override fun close() = Unit
    }
}
