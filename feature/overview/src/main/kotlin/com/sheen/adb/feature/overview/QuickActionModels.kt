package com.sheen.adb.feature.overview

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.CaptureMetadata
import com.sheen.adb.data.ExportDestination
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class QuickActionArtifactFormat {
    PNG,
    MP4,
}

enum class QuickActionOutputPhase(
    val navigationLocked: Boolean,
) {
    AWAITING_DESTINATION(navigationLocked = false),
    WRITING(navigationLocked = true),
    CANCELLING(navigationLocked = true),
    CLEANING(navigationLocked = true),
    COMPLETE(navigationLocked = false),
}

data class QuickActionOutputLifecycle(
    val phase: QuickActionOutputPhase = QuickActionOutputPhase.COMPLETE,
    val cleanupConfirmed: Boolean = true,
    val resourceUncertain: Boolean = false,
) {
    val navigationLocked: Boolean
        get() = phase.navigationLocked || resourceUncertain
}

data class QuickActionArtifactRef(
    val opaqueId: String,
    val sessionId: String? = null,
    val format: QuickActionArtifactFormat? = null,
    val sizeBytes: Long? = null,
) {
    init {
        require(opaqueId.isNotBlank())
    }
}

internal data class QuickActionArtifactRequest(
    val sessionId: String,
    val format: QuickActionArtifactFormat,
    val maxBytes: Long,
    val expiresAtMillis: Long,
)

internal data class QuickActionArtifactHandle(
    val opaqueId: String,
    val sessionId: String,
    val format: QuickActionArtifactFormat,
    val sink: AdbCaptureSink,
)

internal enum class QuickActionDiscardReason {
    CANCELLED,
    STALE_SESSION,
    FAILED,
}

internal sealed interface QuickActionDataResult<out T> {
    data class Success<T>(val value: T) : QuickActionDataResult<T>
    data class Failure(val reason: String, val technicalCode: String) : QuickActionDataResult<Nothing>
}

sealed interface QuickActionExportResult {
    data class Succeeded(val destinationName: String?) : QuickActionExportResult
    data object Cancelled : QuickActionExportResult
    data class Failed(val reason: String, val technicalCode: String) : QuickActionExportResult
}

internal interface QuickActionDataGateway {
    suspend fun create(
        request: QuickActionArtifactRequest,
    ): QuickActionDataResult<QuickActionArtifactHandle>

    suspend fun complete(
        handle: QuickActionArtifactHandle,
        metadata: CaptureMetadata,
    ): QuickActionDataResult<QuickActionArtifactRef>

    suspend fun discard(
        handle: QuickActionArtifactHandle,
        reason: QuickActionDiscardReason,
    )

    suspend fun export(
        artifact: QuickActionArtifactRef,
        destination: ExportDestination?,
    ): QuickActionExportResult
}

sealed interface QuickActionUseCaseResult {
    data class ArtifactReady(val artifact: QuickActionArtifactRef) : QuickActionUseCaseResult
    data object RebootRequested : QuickActionUseCaseResult
    data object Cancelled : QuickActionUseCaseResult
    data class StaleSession(val sessionId: String) : QuickActionUseCaseResult
    data class ResultUnknown(val sessionId: String) : QuickActionUseCaseResult
    data class Failed(val reason: String, val technicalCode: String) : QuickActionUseCaseResult
}

sealed interface ScreenRecordStopResult {
    data object Requested : ScreenRecordStopResult
    data object Cancelled : ScreenRecordStopResult
    data class StaleSession(val sessionId: String) : ScreenRecordStopResult
    data class Failed(val reason: String, val technicalCode: String) : ScreenRecordStopResult
}

object QuickActionLimits {
    const val SCREENSHOT_MAX_BYTES: Long = 32L * 1024L * 1024L
    const val SCREEN_RECORD_MAX_BYTES: Long = 256L * 1024L * 1024L
    val SCREEN_RECORD_MAX_DURATION: Duration = 5.minutes
    const val ARTIFACT_TTL_MILLIS: Long = 60L * 60L * 1_000L
}
