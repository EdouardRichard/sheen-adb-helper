package com.sheen.adbhelper

import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.feature.files.FileTaskKind
import com.sheen.adb.feature.files.FileTaskStatus
import com.sheen.adb.feature.files.FilesUiState
import com.sheen.adb.feature.overview.QuickActionUiState

/**
 * Reduces feature-owned delivery state without interpreting ADB or storage failures.
 *
 * Features retain cancellation and cleanup ownership. App only decides whether navigation may
 * leave the owning page. A picker/preparation phase is deliberately represented but maps to no
 * lock through [DeliveryIoState.toNavigationLockOrNull].
 */
class DeliveryLockAggregator {
    fun aggregate(deliveries: Iterable<DeliveryIoState?>): NavigationLock? =
        deliveries
            .mapNotNull { it?.toNavigationLockOrNull() }
            .minWithOrNull(
                compareBy<NavigationLock> { it.owner.ordinal }
                    .thenBy { it.taskId },
            )

    fun fromFiles(state: FilesUiState): DeliveryIoState? {
        val task = state.activeTask ?: return null
        val phase = when (task.status) {
            FileTaskStatus.Preparing,
            FileTaskStatus.AwaitingConflict,
            -> DeliveryPhase.PREPARING
            is FileTaskStatus.Transferring -> DeliveryPhase.TRANSFERRING
            FileTaskStatus.Verifying -> DeliveryPhase.VERIFYING
            FileTaskStatus.Committing -> DeliveryPhase.WRITING
            FileTaskStatus.Succeeded,
            is FileTaskStatus.Failed,
            FileTaskStatus.Cancelled,
            is FileTaskStatus.CleanupFailed,
            -> DeliveryPhase.TERMINAL
        }
        val result = when (task.status) {
            FileTaskStatus.Succeeded -> DeliveryResult.COMPLETE
            is FileTaskStatus.Failed -> DeliveryResult.FAILED
            FileTaskStatus.Cancelled -> DeliveryResult.CANCELLED
            is FileTaskStatus.CleanupFailed -> DeliveryResult.UNKNOWN
            else -> null
        }
        val cleanupFailed = task.status is FileTaskStatus.CleanupFailed
        val terminal = task.status.isTerminal
        val progress = (task.status as? FileTaskStatus.Transferring)?.let {
            DeliveryProgress(completed = it.transferredBytes, total = it.totalBytes)
        }
        return DeliveryIoState(
            owner = MainDestination.FILES,
            taskId = task.taskId,
            sessionId = task.sessionId,
            kind = when (task.kind) {
                FileTaskKind.UPLOAD -> DeliveryKind.FILE_UPLOAD
                FileTaskKind.DOWNLOAD -> DeliveryKind.FILE_DOWNLOAD
                FileTaskKind.APK_EXTRACTION -> DeliveryKind.APK_EXTRACTION
            },
            phase = phase,
            result = result,
            resourceState = when {
                cleanupFailed -> DeliveryResourceState.UNCERTAIN
                terminal -> DeliveryResourceState.RELEASED
                else -> DeliveryResourceState.OWNED
            },
            progress = progress,
            cancelAvailable = !terminal,
            cleanupConfirmed = terminal && !cleanupFailed,
        )
    }

    fun fromQuickAction(state: QuickActionUiState): DeliveryIoState? {
        val exporting = state as? QuickActionUiState.Exporting ?: return null
        val kind = when (exporting.kind) {
            QuickActionKind.SCREENSHOT -> DeliveryKind.SCREENSHOT_SAVE
            QuickActionKind.SCREEN_RECORD -> DeliveryKind.SCREEN_RECORDING_SAVE_OR_EXPORT
            QuickActionKind.REBOOT -> return null
        }
        return DeliveryIoState(
            owner = MainDestination.CONNECTION,
            taskId = "quick-action-export:${exporting.kind.name}:${exporting.sessionId}",
            sessionId = exporting.sessionId,
            kind = kind,
            phase = DeliveryPhase.WRITING,
            resourceState = DeliveryResourceState.OWNED,
            cancelAvailable = false,
            cleanupConfirmed = false,
        )
    }
}
