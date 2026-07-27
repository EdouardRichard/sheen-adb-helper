package com.sheen.adb.feature.overview

import com.sheen.adb.core.QuickActionKind

sealed interface QuickActionUiState {
    data object Idle : QuickActionUiState

    data class Confirming(
        val kind: QuickActionKind,
        val sessionId: String,
    ) : QuickActionUiState

    data class Running(
        val kind: QuickActionKind,
        val sessionId: String,
        val progress: Float = 0f,
        val startedAtEpochMillis: Long = System.currentTimeMillis(),
        val elapsedMillis: Long = 0L,
        val bytesWritten: Long = 0L,
        val isStopping: Boolean = false,
    ) : QuickActionUiState

    data class AwaitingExport(
        val kind: QuickActionKind,
        val sessionId: String,
        val artifact: QuickActionArtifactRef,
    ) : QuickActionUiState

    data class Exporting(
        val kind: QuickActionKind,
        val sessionId: String,
        val artifact: QuickActionArtifactRef,
    ) : QuickActionUiState

    data class Succeeded(
        val kind: QuickActionKind,
        val sessionId: String,
        val destinationName: String?,
    ) : QuickActionUiState

    data class Cancelled(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
    ) : QuickActionUiState

    data class Failed(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
        val technicalCode: String,
    ) : QuickActionUiState

    data class ResultUnknown(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
    ) : QuickActionUiState
}

internal val QuickActionUiState.statusKey: OverviewStringKey?
    get() = when (this) {
        QuickActionUiState.Idle,
        is QuickActionUiState.Confirming,
        -> null
        is QuickActionUiState.Running -> when (kind) {
            QuickActionKind.SCREENSHOT -> OverviewStringKey.CAPTURING_SCREENSHOT
            QuickActionKind.SCREEN_RECORD -> if (isStopping) {
                OverviewStringKey.FINALIZING_SCREEN_RECORD
            } else {
                OverviewStringKey.SCREEN_RECORDING
            }
            QuickActionKind.REBOOT -> OverviewStringKey.REBOOT_REQUESTED
        }
        is QuickActionUiState.AwaitingExport -> OverviewStringKey.AWAITING_DESTINATION
        is QuickActionUiState.Exporting -> OverviewStringKey.SAVE_WRITING
        is QuickActionUiState.Succeeded -> OverviewStringKey.SAVE_SUCCEEDED
        is QuickActionUiState.Cancelled -> OverviewStringKey.SAVE_CANCELLED
        is QuickActionUiState.Failed -> OverviewStringKey.SAVE_FAILED
        is QuickActionUiState.ResultUnknown -> OverviewStringKey.OUTCOME_UNKNOWN
    }

sealed interface QuickActionPresentationEvent {
    data class StartRequested(val kind: QuickActionKind, val sessionId: String) :
        QuickActionPresentationEvent

    data class RebootRequested(val sessionId: String) : QuickActionPresentationEvent
    data class RebootConfirmed(val sessionId: String) : QuickActionPresentationEvent
    data class RebootConfirmationDismissed(val sessionId: String) : QuickActionPresentationEvent
    data class ScreenRecordStopRequested(val sessionId: String) : QuickActionPresentationEvent

    data class Progressed(
        val kind: QuickActionKind,
        val sessionId: String,
        val progress: Float,
        val elapsedMillis: Long,
        val bytesWritten: Long,
    ) : QuickActionPresentationEvent

    data class ArtifactReady(
        val kind: QuickActionKind,
        val sessionId: String,
        val artifact: QuickActionArtifactRef,
    ) : QuickActionPresentationEvent

    data class ExportStarted(val kind: QuickActionKind, val sessionId: String) :
        QuickActionPresentationEvent

    data class ExportSucceeded(
        val kind: QuickActionKind,
        val sessionId: String,
        val destinationName: String?,
    ) : QuickActionPresentationEvent

    data class Cancelled(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
    ) : QuickActionPresentationEvent

    data class Failed(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
        val technicalCode: String,
    ) : QuickActionPresentationEvent

    data class ResultUnknown(
        val kind: QuickActionKind,
        val sessionId: String,
        val reason: String,
    ) : QuickActionPresentationEvent

    data class SessionChanged(val activeSessionId: String?) : QuickActionPresentationEvent
}

class QuickActionPresentationReducer {
    fun reduce(
        state: QuickActionUiState,
        event: QuickActionPresentationEvent,
    ): QuickActionUiState = when (event) {
        is QuickActionPresentationEvent.StartRequested ->
            if (state.isBusy) state else QuickActionUiState.Running(event.kind, event.sessionId)

        is QuickActionPresentationEvent.RebootRequested ->
            if (state.isBusy) state else {
                QuickActionUiState.Confirming(QuickActionKind.REBOOT, event.sessionId)
            }

        is QuickActionPresentationEvent.RebootConfirmed ->
            if (state is QuickActionUiState.Confirming &&
                state.kind == QuickActionKind.REBOOT &&
                state.sessionId == event.sessionId
            ) {
                QuickActionUiState.Running(QuickActionKind.REBOOT, event.sessionId)
            } else {
                state
            }

        is QuickActionPresentationEvent.RebootConfirmationDismissed ->
            if (state.matches(QuickActionKind.REBOOT, event.sessionId) &&
                state is QuickActionUiState.Confirming
            ) {
                QuickActionUiState.Cancelled(
                    QuickActionKind.REBOOT,
                    event.sessionId,
                    "confirmation-dismissed",
                )
            } else {
                state
            }

        is QuickActionPresentationEvent.ScreenRecordStopRequested ->
            if (state is QuickActionUiState.Running &&
                state.matches(QuickActionKind.SCREEN_RECORD, event.sessionId)
            ) {
                state.copy(isStopping = true)
            } else {
                state
            }

        is QuickActionPresentationEvent.Progressed ->
            if (state is QuickActionUiState.Running && state.matches(event.kind, event.sessionId)) {
                state.copy(
                    progress = event.progress.coerceIn(0f, 1f),
                    elapsedMillis = event.elapsedMillis.coerceAtLeast(0L),
                    bytesWritten = event.bytesWritten.coerceAtLeast(0L),
                )
            } else {
                state
            }

        is QuickActionPresentationEvent.ArtifactReady ->
            if (state is QuickActionUiState.Running && state.matches(event.kind, event.sessionId)) {
                QuickActionUiState.AwaitingExport(event.kind, event.sessionId, event.artifact)
            } else {
                state
            }

        is QuickActionPresentationEvent.ExportStarted ->
            if (state is QuickActionUiState.AwaitingExport &&
                state.matches(event.kind, event.sessionId)
            ) {
                QuickActionUiState.Exporting(event.kind, event.sessionId, state.artifact)
            } else {
                state
            }

        is QuickActionPresentationEvent.ExportSucceeded ->
            if (state is QuickActionUiState.Exporting && state.matches(event.kind, event.sessionId)) {
                QuickActionUiState.Succeeded(
                    event.kind,
                    event.sessionId,
                    event.destinationName,
                )
            } else {
                state
            }

        is QuickActionPresentationEvent.Cancelled ->
            if (state.matches(event.kind, event.sessionId) && state.isBusy) {
                QuickActionUiState.Cancelled(event.kind, event.sessionId, event.reason)
            } else {
                state
            }

        is QuickActionPresentationEvent.Failed ->
            if (state.matches(event.kind, event.sessionId) && state.isBusy) {
                QuickActionUiState.Failed(
                    event.kind,
                    event.sessionId,
                    event.reason,
                    event.technicalCode,
                )
            } else {
                state
            }

        is QuickActionPresentationEvent.ResultUnknown ->
            if (state.matches(event.kind, event.sessionId) && state.isBusy) {
                QuickActionUiState.ResultUnknown(event.kind, event.sessionId, event.reason)
            } else {
                state
            }

        is QuickActionPresentationEvent.SessionChanged -> {
            val owner = state.owner ?: return state
            if (owner.second == event.activeSessionId) {
                state
            } else if (state.isBusy) {
                QuickActionUiState.Cancelled(owner.first, owner.second, "stale-session")
            } else {
                state
            }
        }
    }

    private val QuickActionUiState.owner: Pair<QuickActionKind, String>?
        get() = when (this) {
            QuickActionUiState.Idle -> null
            is QuickActionUiState.Confirming -> kind to sessionId
            is QuickActionUiState.Running -> kind to sessionId
            is QuickActionUiState.AwaitingExport -> kind to sessionId
            is QuickActionUiState.Exporting -> kind to sessionId
            is QuickActionUiState.Succeeded -> kind to sessionId
            is QuickActionUiState.Cancelled -> kind to sessionId
            is QuickActionUiState.Failed -> kind to sessionId
            is QuickActionUiState.ResultUnknown -> kind to sessionId
        }

    private val QuickActionUiState.isBusy: Boolean
        get() = this is QuickActionUiState.Confirming ||
            this is QuickActionUiState.Running ||
            this is QuickActionUiState.AwaitingExport ||
            this is QuickActionUiState.Exporting

    private fun QuickActionUiState.matches(
        kind: QuickActionKind,
        sessionId: String,
    ): Boolean = owner == kind to sessionId
}
