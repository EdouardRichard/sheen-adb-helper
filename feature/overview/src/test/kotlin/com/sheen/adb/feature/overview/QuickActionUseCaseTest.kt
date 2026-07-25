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
import com.sheen.adb.core.QuickActionResult
import com.sheen.adb.core.RebootRequest
import com.sheen.adb.core.ScreenRecordRequest
import com.sheen.adb.core.ScreenshotCaptureRequest
import com.sheen.adb.data.ExportDestination
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionUseCaseTest {
    @Test
    fun `screenshot creates bounded sink validates metadata and returns opaque artifact`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager()
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })

        val result = useCase.captureScreenshot("session-a")

        assertEquals(data.created.single().format, QuickActionArtifactFormat.PNG)
        assertEquals(data.created.single().maxBytes, QuickActionLimits.SCREENSHOT_MAX_BYTES)
        assertEquals(adb.screenshotRequests.single().expectedSessionId, "session-a")
        assertTrue(adb.lastSink === data.sink)
        assertTrue(result is QuickActionUseCaseResult.ArtifactReady)
        assertEquals((result as QuickActionUseCaseResult.ArtifactReady).artifact.opaqueId, "artifact-a")
        assertEquals(data.completed.single().metadata.format, CaptureFormat.PNG)
        assertTrue(data.discarded.isEmpty())
    }

    @Test
    fun `screen recording uses one bounded artifact and forwards progress`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager()
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })
        val progress = mutableListOf<QuickActionProgress>()

        val result = useCase.recordScreen("session-a", progress::add)

        assertEquals(data.created.size, 1)
        assertEquals(data.created.single().format, QuickActionArtifactFormat.MP4)
        assertEquals(data.created.single().maxBytes, QuickActionLimits.SCREEN_RECORD_MAX_BYTES)
        assertEquals(adb.recordRequests.single().maxBytes, QuickActionLimits.SCREEN_RECORD_MAX_BYTES)
        assertEquals(adb.recordRequests.single().maxDuration, QuickActionLimits.SCREEN_RECORD_MAX_DURATION)
        assertEquals(progress.size, 1)
        assertTrue(result is QuickActionUseCaseResult.ArtifactReady)
    }

    @Test
    fun `screen recording stop delegates the owning Session without replacing its artifact`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager()
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })

        val result = useCase.stopScreenRecord("session-a")

        assertEquals(result, ScreenRecordStopResult.Requested)
        assertEquals(adb.stopRecordSessions, listOf("session-a"))
        assertTrue(data.created.isEmpty())
        assertTrue(data.discarded.isEmpty())
    }

    @Test
    fun `metadata mismatch fails and always discards private artifact`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager().apply {
            screenshotResult = QuickActionResult.Success(
                CaptureMetadata(
                    expectedSessionId = "session-other",
                    kind = QuickActionKind.SCREENSHOT,
                    bytesWritten = 8,
                    elapsed = kotlin.time.Duration.ZERO,
                    format = CaptureFormat.PNG,
                ),
            )
        }
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })

        val result = useCase.captureScreenshot("session-a")

        assertEquals(result, QuickActionUseCaseResult.Failed("metadata-mismatch", "QUICK_ACTION_METADATA"))
        assertEquals(data.discarded.single().reason, QuickActionDiscardReason.FAILED)
    }

    @Test
    fun `ADB cancellation and stale Session discard unfinished artifact`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager().apply {
            screenshotResult = QuickActionResult.Cancelled
            recordResult = QuickActionResult.StaleSession("session-a")
        }
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })

        assertEquals(useCase.captureScreenshot("session-a"), QuickActionUseCaseResult.Cancelled)
        assertEquals(
            useCase.recordScreen("session-a"),
            QuickActionUseCaseResult.StaleSession("session-a"),
        )
        assertEquals(
            data.discarded.map { it.reason },
            listOf(QuickActionDiscardReason.CANCELLED, QuickActionDiscardReason.STALE_SESSION),
        )
    }

    @Test
    fun `export delegates opaque destination and private source is consumed on every terminal result`() = runTest {
        val data = FakeQuickActionDataGateway()
        val useCase = QuickActionUseCase(FakeQuickActionManager().instance, data, nowMillis = { 1_000L })
        val artifact = QuickActionArtifactRef(
            opaqueId = "artifact-a",
            sessionId = "session-a",
            format = QuickActionArtifactFormat.PNG,
            sizeBytes = 8,
        )

        val success = useCase.export(
            artifact,
            ExportDestination("destination-a", "controlled-screen.png"),
        )
        assertEquals(success, QuickActionExportResult.Succeeded("controlled-screen.png"))
        assertEquals(requireNotNull(data.exports.single().destination).opaqueId, "destination-a")

        data.exportResult = QuickActionExportResult.Cancelled
        val cancelled = useCase.export(artifact, null)
        assertEquals(cancelled, QuickActionExportResult.Cancelled)
        assertEquals(data.exports.size, 2)
    }

    @Test
    fun `reboot has no artifact and preserves result unknown`() = runTest {
        val data = FakeQuickActionDataGateway()
        val adb = FakeQuickActionManager().apply {
            rebootResult = QuickActionResult.ResultUnknown("session-a")
        }
        val useCase = QuickActionUseCase(adb.instance, data, nowMillis = { 1_000L })

        val result = useCase.reboot("session-a")

        assertEquals(result, QuickActionUseCaseResult.ResultUnknown("session-a"))
        assertTrue(data.created.isEmpty())
        assertTrue(data.discarded.isEmpty())
    }

    @Test
    fun `public use case surface contains no platform stream URI or raw ADB types`() {
        val forbidden = listOf(
            "java.io.File",
            "java.io.OutputStream",
            "android.content.ContentResolver",
            "android.net.Uri",
            "java.net.URI",
            "com.sheen.adb.core.internal",
        )
        val signatures = (QuickActionUseCase::class.java.methods.toList() +
            QuickActionUseCaseResult::class.java.declaredClasses.flatMap { it.declaredMethods.toList() })
            .joinToString("\n") { it.toGenericString() }

        forbidden.forEach { assertFalse(signatures.contains(it), "Leaked forbidden type: $it") }
    }

    private class FakeQuickActionDataGateway : QuickActionDataGateway {
        data class Completion(
            val handle: QuickActionArtifactHandle,
            val metadata: CaptureMetadata,
        )

        data class Discard(
            val handle: QuickActionArtifactHandle,
            val reason: QuickActionDiscardReason,
        )

        data class Export(
            val artifact: QuickActionArtifactRef,
            val destination: ExportDestination?,
        )

        val created = mutableListOf<QuickActionArtifactRequest>()
        val completed = mutableListOf<Completion>()
        val discarded = mutableListOf<Discard>()
        val exports = mutableListOf<Export>()
        val sink = object : AdbCaptureSink {
            override val bytesWritten: Long = 8
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) =
                CaptureSinkResult.Accepted
            override suspend fun finish() = CaptureSinkResult.Accepted
            override suspend fun abort() = Unit
        }
        var exportResult: QuickActionExportResult =
            QuickActionExportResult.Succeeded("controlled-screen.png")

        override suspend fun create(
            request: QuickActionArtifactRequest,
        ): QuickActionDataResult<QuickActionArtifactHandle> {
            created += request
            return QuickActionDataResult.Success(
                QuickActionArtifactHandle("handle-${created.size}", request.sessionId, request.format, sink),
            )
        }

        override suspend fun complete(
            handle: QuickActionArtifactHandle,
            metadata: CaptureMetadata,
        ): QuickActionDataResult<QuickActionArtifactRef> {
            completed += Completion(handle, metadata)
            return QuickActionDataResult.Success(
                QuickActionArtifactRef("artifact-a", handle.sessionId, handle.format, metadata.bytesWritten),
            )
        }

        override suspend fun discard(
            handle: QuickActionArtifactHandle,
            reason: QuickActionDiscardReason,
        ) {
            discarded += Discard(handle, reason)
        }

        override suspend fun export(
            artifact: QuickActionArtifactRef,
            destination: ExportDestination?,
        ): QuickActionExportResult {
            exports += Export(artifact, destination)
            return exportResult
        }
    }

    private class FakeQuickActionManager {
        val screenshotRequests = mutableListOf<ScreenshotCaptureRequest>()
        val recordRequests = mutableListOf<ScreenRecordRequest>()
        val stopRecordSessions = mutableListOf<String>()
        var lastSink: AdbCaptureSink? = null
        var screenshotResult: QuickActionResult<CaptureMetadata> = captureMetadata(
            QuickActionKind.SCREENSHOT,
            CaptureFormat.PNG,
        )
        var recordResult: QuickActionResult<CaptureMetadata> = captureMetadata(
            QuickActionKind.SCREEN_RECORD,
            CaptureFormat.MP4,
        )
        var rebootResult: QuickActionResult<Unit> = QuickActionResult.Success(Unit)

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected())
                "getDiagnosticEvents" -> MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
                "captureScreenshot" -> {
                    screenshotRequests += args!![0] as ScreenshotCaptureRequest
                    lastSink = args[1] as AdbCaptureSink
                    @Suppress("UNCHECKED_CAST")
                    val callback = args[2] as (QuickActionProgress) -> Unit
                    callback(progress(QuickActionKind.SCREENSHOT))
                    screenshotResult
                }
                "recordScreen" -> {
                    recordRequests += args!![0] as ScreenRecordRequest
                    lastSink = args[1] as AdbCaptureSink
                    @Suppress("UNCHECKED_CAST")
                    val callback = args[2] as (QuickActionProgress) -> Unit
                    callback(progress(QuickActionKind.SCREEN_RECORD))
                    recordResult
                }
                "stopScreenRecord" -> {
                    stopRecordSessions += args!![0] as String
                    QuickActionResult.Success(Unit)
                }
                "reboot" -> {
                    @Suppress("UNUSED_VARIABLE")
                    val request = args!![0] as RebootRequest
                    rebootResult
                }
                "clearDiagnosticEvents", "close" -> null
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        private fun captureMetadata(
            kind: QuickActionKind,
            format: CaptureFormat,
        ): QuickActionResult<CaptureMetadata> = QuickActionResult.Success(
            CaptureMetadata(
                expectedSessionId = "session-a",
                kind = kind,
                bytesWritten = 8,
                elapsed = kotlin.time.Duration.ZERO,
                format = format,
            ),
        )

        private fun progress(kind: QuickActionKind) = QuickActionProgress(
            expectedSessionId = "session-a",
            kind = kind,
            phase = com.sheen.adb.core.QuickActionProgressPhase.CAPTURING,
            bytesWritten = 8,
            elapsed = kotlin.time.Duration.ZERO,
            maxBytes = QuickActionLimits.SCREEN_RECORD_MAX_BYTES,
            maxDuration = QuickActionLimits.SCREEN_RECORD_MAX_DURATION,
        )
    }
}
