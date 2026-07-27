package com.sheen.adb.feature.logcat

import android.net.Uri
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ProcessAnalysisSnapshot
import com.sheen.adb.core.StructuredLogcatKind
import com.sheen.adb.core.StructuredLogcatLevel
import com.sheen.adb.core.StructuredLogcatRecord
import com.sheen.adb.data.TextExporter
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNotEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatLifecycleTest {
    @Test
    fun `connected visible page performs zero reads until explicit start and owns one stream`() = runBlocking {
        val fixture = fixture("session-a")
        fixture.viewModel.setForeground(true)
        fixture.viewModel.onPageVisible(true)

        assertEquals(fixture.manager.streamStartCount, 0)
        assertEquals(fixture.manager.activeStreamCount, 0)

        fixture.viewModel.start()
        val windowId = fixture.viewModel.state.value.windowId
        assertTrue(windowId != null)
        assertEquals(fixture.manager.streamStartCount, 1)
        assertEquals(fixture.manager.activeStreamCount, 1)

        fixture.viewModel.start()
        fixture.viewModel.start()
        assertEquals(fixture.manager.streamStartCount, 1, "rapid Start must not duplicate collection")
        assertEquals(fixture.manager.maxActiveStreams, 1)
        fixture.close()
    }

    @Test
    fun `leaving stops and retains window returning does not auto start and explicit restart clears it`() = runBlocking {
        val fixture = fixture("session-a")
        fixture.viewModel.setForeground(true)
        fixture.viewModel.onPageVisible(true)
        fixture.viewModel.start()
        val firstWindowId = requireNotNull(fixture.viewModel.state.value.windowId)
        fixture.manager.emit("synthetic retained line")
        assertEquals(fixture.viewModel.state.value.visibleLines.size, 1)

        fixture.viewModel.onPageVisible(false)
        assertEquals(fixture.manager.activeStreamCount, 0)
        assertEquals(fixture.viewModel.state.value.visibleLines.size, 1)
        assertEquals(fixture.viewModel.state.value.windowId, firstWindowId)

        fixture.viewModel.onPageVisible(true)
        assertEquals(fixture.manager.streamStartCount, 1, "returning must show the static snapshot")
        assertEquals(fixture.manager.activeStreamCount, 0)
        assertEquals(fixture.viewModel.state.value.visibleLines.size, 1)

        fixture.viewModel.start()
        assertEquals(fixture.manager.streamStartCount, 2)
        assertTrue(fixture.viewModel.state.value.visibleLines.isEmpty())
        assertNotEquals(fixture.viewModel.state.value.windowId, firstWindowId)
        fixture.close()
    }

    @Test
    fun `background stops without clearing or auto restarting the same session window`() = runBlocking {
        val fixture = fixture("session-a")
        fixture.viewModel.setForeground(true)
        fixture.viewModel.onPageVisible(true)
        fixture.viewModel.start()
        fixture.manager.emit("synthetic retained line")
        val windowId = fixture.viewModel.state.value.windowId

        fixture.viewModel.setForeground(false)
        assertEquals(fixture.manager.activeStreamCount, 0)
        assertEquals(fixture.viewModel.state.value.visibleLines.size, 1)
        assertEquals(fixture.viewModel.state.value.windowId, windowId)

        fixture.viewModel.setForeground(true)
        assertEquals(fixture.manager.streamStartCount, 1)
        assertEquals(fixture.manager.activeStreamCount, 0)
        fixture.close()
    }

    @Test
    fun `session switch or disconnect stops stream and clears retained window identity`() = runBlocking {
        val fixture = fixture("session-old")
        fixture.viewModel.setForeground(true)
        fixture.viewModel.onPageVisible(true)
        fixture.viewModel.start()
        fixture.manager.emit("synthetic stale line")

        fixture.manager.connectionState.value = connected("session-new")
        assertEquals(fixture.manager.activeStreamCount, 0)
        assertEquals(fixture.viewModel.state.value.sessionId, "session-new")
        assertTrue(fixture.viewModel.state.value.visibleLines.isEmpty())
        assertNull(fixture.viewModel.state.value.windowId)
        assertEquals(fixture.manager.streamStartCount, 1, "Session switch must not auto-start")

        fixture.viewModel.start()
        fixture.manager.emit("synthetic replacement line")
        assertEquals(fixture.manager.streamStartCount, 2)
        fixture.manager.connectionState.value = AdbConnectionState.Disconnected()
        assertEquals(fixture.manager.activeStreamCount, 0)
        assertTrue(fixture.viewModel.state.value.visibleLines.isEmpty())
        assertNull(fixture.viewModel.state.value.windowId)
        fixture.close()
    }

    private fun fixture(sessionId: String): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = ControlledLogcatManager(connected(sessionId))
        val viewModel = LogcatViewModel(
            manager = manager.instance,
            exporter = NoOpTextExporter,
            scope = scope,
        )
        return Fixture(manager, viewModel, scope)
    }

    private fun connected(sessionId: String) = AdbConnectionState.Connected(
        endpoint = AdbEndpoint("$sessionId.invalid", 4711),
        sessionId = sessionId,
    )

    private data class Fixture(
        val manager: ControlledLogcatManager,
        val viewModel: LogcatViewModel,
        val scope: CoroutineScope,
    ) {
        fun close() = scope.cancel()
    }

    private object NoOpTextExporter : TextExporter {
        override suspend fun writeUtf8(target: Uri, text: String): Boolean = true
    }

    private class ControlledLogcatManager(initialConnection: AdbConnectionState) {
        val connectionState = MutableStateFlow(initialConnection)
        private val diagnosticEvents = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        private val events = Channel<AdbOperationResult<StructuredLogcatRecord>>(Channel.UNLIMITED)
        var streamStartCount = 0
            private set
        var activeStreamCount = 0
            private set
        var maxActiveStreams = 0
            private set

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, _ ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connectionState
                "getDiagnosticEvents" -> diagnosticEvents
                "loadProcessAnalysis" -> AdbOperationResult.Success(
                    ProcessAnalysisSnapshot(
                        sessionId = (connectionState.value as AdbConnectionState.Connected).sessionId,
                        generation = PROCESS_GENERATION,
                        entries = emptyList(),
                    ),
                )
                "streamStructuredLogcat" -> controlledFlow()
                "clearDiagnosticEvents", "close" -> null
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        suspend fun emit(text: String) {
            val sessionId = (connectionState.value as AdbConnectionState.Connected).sessionId
            events.send(
                AdbOperationResult.Success(
                    StructuredLogcatRecord(
                        sessionId = sessionId,
                        snapshotGeneration = PROCESS_GENERATION,
                        sequence = streamStartCount.toLong(),
                        rawText = text,
                        kind = StructuredLogcatKind.PARSED,
                        level = StructuredLogcatLevel.INFO,
                    ),
                ),
            )
        }

        private fun controlledFlow(): Flow<AdbOperationResult<StructuredLogcatRecord>> = flow {
            streamStartCount += 1
            activeStreamCount += 1
            maxActiveStreams = maxOf(maxActiveStreams, activeStreamCount)
            try {
                for (event in events) emit(event)
            } finally {
                activeStreamCount -= 1
            }
        }

        private companion object {
            const val PROCESS_GENERATION = 7L
        }
    }
}
