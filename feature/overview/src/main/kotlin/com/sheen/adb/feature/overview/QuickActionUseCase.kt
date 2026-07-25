package com.sheen.adb.feature.overview

import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.AdbCaptureSink
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
import com.sheen.adb.data.ArtifactCreateRequest
import com.sheen.adb.data.ArtifactFormat
import com.sheen.adb.data.ArtifactRef
import com.sheen.adb.data.ArtifactStoreResult
import com.sheen.adb.data.ArtifactTerminationReason
import com.sheen.adb.data.ArtifactWriteHandle
import com.sheen.adb.data.QuickActionArtifactStore
import com.sheen.adb.data.SafBinaryExportResult
import com.sheen.adb.data.SafBinaryExporter
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class QuickActionUseCase internal constructor(
    private val manager: AdbSessionManager,
    private val data: QuickActionDataGateway,
    private val nowMillis: () -> Long,
) {
    constructor(
        manager: AdbSessionManager,
        artifactStore: QuickActionArtifactStore,
        exporter: SafBinaryExporter,
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        manager = manager,
        data = CoreQuickActionDataGateway(artifactStore, exporter),
        nowMillis = nowMillis,
    )

    suspend fun captureScreenshot(
        expectedSessionId: String,
        progress: (QuickActionProgress) -> Unit = {},
    ): QuickActionUseCaseResult = capture(
        expectedSessionId = expectedSessionId,
        format = QuickActionArtifactFormat.PNG,
        maxBytes = QuickActionLimits.SCREENSHOT_MAX_BYTES,
        expectedKind = QuickActionKind.SCREENSHOT,
        expectedCaptureFormat = CaptureFormat.PNG,
    ) { handle ->
        manager.captureScreenshot(
            request = ScreenshotCaptureRequest(expectedSessionId, 10.seconds),
            sink = handle.sink,
            progress = progress,
        )
    }

    suspend fun recordScreen(
        expectedSessionId: String,
        progress: (QuickActionProgress) -> Unit = {},
    ): QuickActionUseCaseResult = capture(
        expectedSessionId = expectedSessionId,
        format = QuickActionArtifactFormat.MP4,
        maxBytes = QuickActionLimits.SCREEN_RECORD_MAX_BYTES,
        expectedKind = QuickActionKind.SCREEN_RECORD,
        expectedCaptureFormat = CaptureFormat.MP4,
    ) { handle ->
        manager.recordScreen(
            request = ScreenRecordRequest(
                expectedSessionId = expectedSessionId,
                timeout = QuickActionLimits.SCREEN_RECORD_MAX_DURATION + 10.seconds,
                maxDuration = QuickActionLimits.SCREEN_RECORD_MAX_DURATION,
                maxBytes = QuickActionLimits.SCREEN_RECORD_MAX_BYTES,
            ),
            sink = handle.sink,
            progress = progress,
        )
    }

    suspend fun stopScreenRecord(
        expectedSessionId: String,
    ): ScreenRecordStopResult = when (val result = manager.stopScreenRecord(expectedSessionId)) {
        is QuickActionResult.Success -> ScreenRecordStopResult.Requested
        is QuickActionResult.Failure -> ScreenRecordStopResult.Failed(
            reason = "screen-record-stop-failed",
            technicalCode = result.error.technicalCode,
        )
        QuickActionResult.Cancelled -> ScreenRecordStopResult.Cancelled
        is QuickActionResult.StaleSession -> ScreenRecordStopResult.StaleSession(
            result.expectedSessionId,
        )
        is QuickActionResult.ResultUnknown -> ScreenRecordStopResult.Failed(
            reason = "screen-record-stop-result-unknown",
            technicalCode = "QUICK_ACTION_RESULT_UNKNOWN",
        )
    }

    suspend fun reboot(expectedSessionId: String): QuickActionUseCaseResult =
        when (val result = manager.reboot(RebootRequest(expectedSessionId, 10.seconds))) {
            is QuickActionResult.Success -> QuickActionUseCaseResult.RebootRequested
            is QuickActionResult.Failure -> QuickActionUseCaseResult.Failed(
                reason = "reboot-failed",
                technicalCode = result.error.technicalCode,
            )
            QuickActionResult.Cancelled -> QuickActionUseCaseResult.Cancelled
            is QuickActionResult.StaleSession -> QuickActionUseCaseResult.StaleSession(
                result.expectedSessionId,
            )
            is QuickActionResult.ResultUnknown -> QuickActionUseCaseResult.ResultUnknown(
                result.expectedSessionId,
            )
        }

    suspend fun export(
        artifact: QuickActionArtifactRef,
        destination: ExportDestination?,
    ): QuickActionExportResult = data.export(artifact, destination)

    private suspend fun capture(
        expectedSessionId: String,
        format: QuickActionArtifactFormat,
        maxBytes: Long,
        expectedKind: QuickActionKind,
        expectedCaptureFormat: CaptureFormat,
        runCapture: suspend (QuickActionArtifactHandle) -> QuickActionResult<CaptureMetadata>,
    ): QuickActionUseCaseResult {
        require(expectedSessionId.isNotBlank())
        val handle = when (
            val created = data.create(
                QuickActionArtifactRequest(
                    sessionId = expectedSessionId,
                    format = format,
                    maxBytes = maxBytes,
                    expiresAtMillis = nowMillis() + QuickActionLimits.ARTIFACT_TTL_MILLIS,
                ),
            )
        ) {
            is QuickActionDataResult.Success -> created.value
            is QuickActionDataResult.Failure -> return QuickActionUseCaseResult.Failed(
                created.reason,
                created.technicalCode,
            )
        }

        return when (val result = runCapture(handle)) {
            is QuickActionResult.Success -> {
                val metadata = result.value
                if (
                    metadata.expectedSessionId != expectedSessionId ||
                    metadata.kind != expectedKind ||
                    metadata.format != expectedCaptureFormat ||
                    metadata.bytesWritten <= 0L ||
                    metadata.bytesWritten != handle.sink.bytesWritten
                ) {
                    data.discard(handle, QuickActionDiscardReason.FAILED)
                    QuickActionUseCaseResult.Failed(
                        reason = "metadata-mismatch",
                        technicalCode = "QUICK_ACTION_METADATA",
                    )
                } else {
                    when (val completed = data.complete(handle, metadata)) {
                        is QuickActionDataResult.Success ->
                            QuickActionUseCaseResult.ArtifactReady(completed.value)
                        is QuickActionDataResult.Failure -> {
                            data.discard(handle, QuickActionDiscardReason.FAILED)
                            QuickActionUseCaseResult.Failed(
                                completed.reason,
                                completed.technicalCode,
                            )
                        }
                    }
                }
            }
            is QuickActionResult.Failure -> {
                data.discard(handle, QuickActionDiscardReason.FAILED)
                QuickActionUseCaseResult.Failed(
                    reason = "capture-failed",
                    technicalCode = result.error.technicalCode,
                )
            }
            QuickActionResult.Cancelled -> {
                data.discard(handle, QuickActionDiscardReason.CANCELLED)
                QuickActionUseCaseResult.Cancelled
            }
            is QuickActionResult.StaleSession -> {
                data.discard(handle, QuickActionDiscardReason.STALE_SESSION)
                QuickActionUseCaseResult.StaleSession(result.expectedSessionId)
            }
            is QuickActionResult.ResultUnknown -> {
                data.discard(handle, QuickActionDiscardReason.FAILED)
                QuickActionUseCaseResult.ResultUnknown(result.expectedSessionId)
            }
        }
    }
}

private class CoreQuickActionDataGateway(
    private val artifactStore: QuickActionArtifactStore,
    private val exporter: SafBinaryExporter,
) : QuickActionDataGateway {
    private val writeHandles = ConcurrentHashMap<String, ArtifactWriteHandle>()
    private val artifacts = ConcurrentHashMap<String, ArtifactRef>()

    override suspend fun create(
        request: QuickActionArtifactRequest,
    ): QuickActionDataResult<QuickActionArtifactHandle> = when (
        val created = artifactStore.create(
            ArtifactCreateRequest(
                expectedSessionId = request.sessionId,
                format = request.format.toDataFormat(),
                maxBytes = request.maxBytes,
                expiresAtMillis = request.expiresAtMillis,
            ),
        )
    ) {
        is ArtifactStoreResult.Failure -> created.toFeatureFailure()
        is ArtifactStoreResult.Success -> {
            val handle = created.value
            val opaqueId = handle.artifactId.value
            writeHandles[opaqueId] = handle
            QuickActionDataResult.Success(
                QuickActionArtifactHandle(
                    opaqueId = opaqueId,
                    sessionId = request.sessionId,
                    format = request.format,
                    sink = StoreCaptureSink(handle, artifactStore),
                ),
            )
        }
    }

    override suspend fun complete(
        handle: QuickActionArtifactHandle,
        metadata: CaptureMetadata,
    ): QuickActionDataResult<QuickActionArtifactRef> {
        val storageHandle = writeHandles.remove(handle.opaqueId)
            ?: return QuickActionDataResult.Failure(
                "artifact-not-found",
                "QUICK_ACTION_ARTIFACT_NOT_FOUND",
            )
        return when (val completed = artifactStore.complete(storageHandle)) {
            is ArtifactStoreResult.Failure -> completed.toFeatureFailure()
            is ArtifactStoreResult.Success -> {
                val reference = completed.value
                artifacts[handle.opaqueId] = reference
                QuickActionDataResult.Success(
                    QuickActionArtifactRef(
                        opaqueId = handle.opaqueId,
                        sessionId = handle.sessionId,
                        format = handle.format,
                        sizeBytes = reference.sizeBytes,
                    ),
                )
            }
        }
    }

    override suspend fun discard(
        handle: QuickActionArtifactHandle,
        reason: QuickActionDiscardReason,
    ) {
        writeHandles.remove(handle.opaqueId)
        artifacts.remove(handle.opaqueId)
        artifactStore.discard(
            artifactId = com.sheen.adb.data.ArtifactId(handle.opaqueId),
            reason = when (reason) {
                QuickActionDiscardReason.CANCELLED -> ArtifactTerminationReason.CANCELLED
                QuickActionDiscardReason.STALE_SESSION -> ArtifactTerminationReason.DISCONNECTED
                QuickActionDiscardReason.FAILED -> ArtifactTerminationReason.FAILED
            },
        )
    }

    override suspend fun export(
        artifact: QuickActionArtifactRef,
        destination: ExportDestination?,
    ): QuickActionExportResult {
        val storageReference = artifacts.remove(artifact.opaqueId)
            ?: return QuickActionExportResult.Failed(
                "artifact-not-found",
                "QUICK_ACTION_ARTIFACT_NOT_FOUND",
            )
        val source = when (val opened = artifactStore.openSource(storageReference)) {
            is ArtifactStoreResult.Failure -> {
                artifactStore.discard(
                    storageReference.artifactId,
                    ArtifactTerminationReason.FAILED,
                )
                return opened.toExportFailure()
            }
            is ArtifactStoreResult.Success -> opened.value
        }
        return when (
            val result = exporter.export(storageReference.artifactId, source, destination)
        ) {
            is SafBinaryExportResult.Success ->
                QuickActionExportResult.Succeeded(result.destinationName)
            SafBinaryExportResult.UserCancelled -> QuickActionExportResult.Cancelled
            SafBinaryExportResult.SourceReadFailed ->
                QuickActionExportResult.Failed("source-read-failed", "QUICK_ACTION_SOURCE_READ")
            SafBinaryExportResult.TargetWriteFailed ->
                QuickActionExportResult.Failed("target-write-failed", "QUICK_ACTION_TARGET_WRITE")
            SafBinaryExportResult.TargetUnreadable ->
                QuickActionExportResult.Failed("target-unreadable", "QUICK_ACTION_TARGET_UNREADABLE")
            SafBinaryExportResult.SourceCleanupFailed ->
                QuickActionExportResult.Failed("cleanup-failed", "QUICK_ACTION_CLEANUP")
        }
    }

    private fun QuickActionArtifactFormat.toDataFormat(): ArtifactFormat = when (this) {
        QuickActionArtifactFormat.PNG -> ArtifactFormat.PNG
        QuickActionArtifactFormat.MP4 -> ArtifactFormat.MP4
    }

    private fun ArtifactStoreResult.Failure.toFeatureFailure() =
        QuickActionDataResult.Failure(
            reason = error.name.lowercase(),
            technicalCode = "QUICK_ACTION_ARTIFACT_${error.name}",
        )

    private fun ArtifactStoreResult.Failure.toExportFailure() =
        QuickActionExportResult.Failed(
            reason = error.name.lowercase(),
            technicalCode = "QUICK_ACTION_ARTIFACT_${error.name}",
        )
}

private class StoreCaptureSink(
    private val handle: ArtifactWriteHandle,
    private val artifactStore: QuickActionArtifactStore,
) : AdbCaptureSink {
    override val bytesWritten: Long
        get() = handle.sink.bytesWritten

    override suspend fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): CaptureSinkResult = when (val result = handle.sink.write(bytes, offset, length)) {
        is ArtifactStoreResult.Success -> CaptureSinkResult.Accepted
        is ArtifactStoreResult.Failure -> CaptureSinkResult.Rejected(result.error.name)
    }

    override suspend fun finish(): CaptureSinkResult = CaptureSinkResult.Accepted

    override suspend fun abort() {
        artifactStore.discard(handle.artifactId, ArtifactTerminationReason.FAILED)
    }
}
