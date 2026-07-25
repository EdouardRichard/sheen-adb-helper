package com.sheen.adb.feature.overview

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.CaptureFormat
import com.sheen.adb.core.CaptureMetadata
import com.sheen.adb.core.CaptureSinkResult
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.core.QuickActionProgress
import com.sheen.adb.core.QuickActionProgressPhase
import com.sheen.adb.core.QuickActionResult
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewScreenRecordFlowTest {
    @Test
    fun `second recording click stops finalizes and resumes overview polling`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val manager = RecordingManager()
        var viewModel: OverviewViewModel? = null
        Dispatchers.setMain(main)
        try {
            val data = RecordingDataGateway()
            val useCase = QuickActionUseCase(manager.instance, data, nowMillis = { 1_000L })
            val testedViewModel = OverviewViewModel(manager.instance, useCase)
            viewModel = testedViewModel
            runCurrent()
            testedViewModel.setForeground(true)
            runCurrent()

            testedViewModel.requestScreenRecord()
            runCurrent()

            val running = testedViewModel.quickActionState.value as QuickActionUiState.Running
            assertEquals(running.elapsedMillis, 2_000L)
            assertEquals(running.bytesWritten, 1_024L)
            advanceTimeBy(6_000L)
            runCurrent()
            assertEquals(manager.dynamicRefreshes, 0, "Overview polling must pause during recording")

            testedViewModel.requestScreenRecord()
            runCurrent()

            assertEquals(manager.recordStarts, 1)
            assertEquals(manager.stopRequests, 1)
            assertTrue(
                testedViewModel.quickActionState.value is QuickActionUiState.AwaitingExport,
                testedViewModel.quickActionState.value.toString(),
            )
            assertEquals(data.completed, 1)

            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(manager.dynamicRefreshes > 0, "Overview polling must resume after capture")
        } finally {
            manager.finishIfActive()
            viewModel?.viewModelScope?.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class RecordingManager {
        private val connection = MutableStateFlow<AdbConnectionState>(
            AdbConnectionState.Connected(
                sessionId = SESSION_ID,
                endpoint = com.sheen.adb.core.AdbEndpoint("fixture.invalid", 47_111),
            ),
        )
        private var recordingContinuation:
            Continuation<QuickActionResult<CaptureMetadata>>? = null
        var recordStarts = 0
        var stopRequests = 0
        var dynamicRefreshes = 0

        fun finishIfActive() {
            recordingContinuation?.also {
                recordingContinuation = null
            }?.resume(QuickActionResult.Cancelled)
        }

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connection
                "getDiagnosticEvents" -> MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
                "loadDeviceOverview" -> AdbOperationResult.Cancelled
                "refreshDynamicMetrics" -> {
                    dynamicRefreshes++
                    AdbOperationResult.Cancelled
                }
                "recordScreen" -> {
                    recordStarts++
                    @Suppress("UNCHECKED_CAST")
                    val progress = args!![2] as (QuickActionProgress) -> Unit
                    progress(
                        QuickActionProgress(
                            expectedSessionId = SESSION_ID,
                            kind = QuickActionKind.SCREEN_RECORD,
                            phase = QuickActionProgressPhase.CAPTURING,
                            bytesWritten = 1_024L,
                            elapsed = 2.seconds,
                            maxBytes = QuickActionLimits.SCREEN_RECORD_MAX_BYTES,
                            maxDuration = 5.minutes,
                        ),
                    )
                    @Suppress("UNCHECKED_CAST")
                    recordingContinuation =
                        args.last() as Continuation<QuickActionResult<CaptureMetadata>>
                    COROUTINE_SUSPENDED
                }
                "stopScreenRecord" -> {
                    stopRequests++
                    recordingContinuation?.also {
                        recordingContinuation = null
                    }?.resume(
                        QuickActionResult.Success(
                            CaptureMetadata(
                                expectedSessionId = SESSION_ID,
                                kind = QuickActionKind.SCREEN_RECORD,
                                bytesWritten = 8L,
                                elapsed = 3.seconds,
                                format = CaptureFormat.MP4,
                            ),
                        ),
                    )
                    QuickActionResult.Success(Unit)
                }
                "clearDiagnosticEvents", "close" -> null
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager
    }

    private class RecordingDataGateway : QuickActionDataGateway {
        private val sink = object : AdbCaptureSink {
            override val bytesWritten: Long = 8L
            override suspend fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ): CaptureSinkResult = CaptureSinkResult.Accepted
            override suspend fun finish(): CaptureSinkResult = CaptureSinkResult.Accepted
            override suspend fun abort() = Unit
        }
        var completed = 0

        override suspend fun create(
            request: QuickActionArtifactRequest,
        ): QuickActionDataResult<QuickActionArtifactHandle> =
            QuickActionDataResult.Success(
                QuickActionArtifactHandle("handle", request.sessionId, request.format, sink),
            )

        override suspend fun complete(
            handle: QuickActionArtifactHandle,
            metadata: CaptureMetadata,
        ): QuickActionDataResult<QuickActionArtifactRef> {
            completed++
            return QuickActionDataResult.Success(
                QuickActionArtifactRef(
                    opaqueId = "artifact",
                    sessionId = handle.sessionId,
                    format = handle.format,
                    sizeBytes = metadata.bytesWritten,
                ),
            )
        }

        override suspend fun discard(
            handle: QuickActionArtifactHandle,
            reason: QuickActionDiscardReason,
        ) = Unit

        override suspend fun export(
            artifact: QuickActionArtifactRef,
            destination: com.sheen.adb.data.ExportDestination?,
        ): QuickActionExportResult = QuickActionExportResult.Cancelled
    }

    private companion object {
        const val SESSION_ID = "session-record"
    }
}
