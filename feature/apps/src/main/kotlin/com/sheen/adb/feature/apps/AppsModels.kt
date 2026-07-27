package com.sheen.adb.feature.apps

import com.sheen.adb.core.AdbError
import com.sheen.adb.core.ApkComponent
import com.sheen.adb.core.ApkInstallResult
import com.sheen.adb.core.ApplicationClassification
import com.sheen.adb.core.ApplicationField
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState

enum class AppsFilter {
    ALL,
    ENABLED,
    DISABLED,
}

enum class AppsOperation {
    LOADING,
    FORCE_STOP,
    DISABLE,
    ENABLE,
}

data class AppsConfirmation(
    val operation: AppsOperation,
    val packageName: String,
    val sessionId: String,
    val deviceDisplayName: String,
    val userId: Int,
    val expectedGeneration: Long = 0L,
    val confirmationNonce: String? = null,
)

data class AppsOperationNotice(
    val message: String,
    val outcomeUnknown: Boolean = false,
)

enum class AppsRowAction {
    EXTRACT_APK,
    SET_ENABLED,
    FORCE_STOP,
    UNINSTALL,
}

enum class AppsTaskKind {
    APK_EXTRACTION,
    APK_INSTALLATION,
}

sealed interface AppsTaskPhase {
    val navigationLocked: Boolean

    data object PickerOpen : AppsTaskPhase {
        override val navigationLocked = false
    }

    data object Preparing : AppsTaskPhase {
        override val navigationLocked = false
    }

    data class Transferring(
        val componentId: String?,
        val transferredBytes: Long,
    ) : AppsTaskPhase {
        override val navigationLocked = true
    }

    data object Writing : AppsTaskPhase {
        override val navigationLocked = true
    }

    data object AwaitingCleanup : AppsTaskPhase {
        override val navigationLocked = true
    }
}

sealed interface AppsComponentResult {
    val expectedComponents: Int
    val committedComponents: Int
    val cleanupConfirmed: Boolean
    val resourceUncertain: Boolean

    data class CompleteSuccess(
        override val expectedComponents: Int,
        override val committedComponents: Int = expectedComponents,
        override val cleanupConfirmed: Boolean = true,
        override val resourceUncertain: Boolean = false,
    ) : AppsComponentResult

    data class PartialSuccess(
        override val expectedComponents: Int,
        override val committedComponents: Int,
        override val cleanupConfirmed: Boolean,
        override val resourceUncertain: Boolean = false,
    ) : AppsComponentResult

    data class NoneCommittedFailure(
        override val expectedComponents: Int,
        override val committedComponents: Int = 0,
        override val cleanupConfirmed: Boolean,
        override val resourceUncertain: Boolean,
    ) : AppsComponentResult
}

sealed interface AppsTaskTerminal {
    data class Extraction(val result: AppsComponentResult) : AppsTaskTerminal
    data class OldPackageRemovedNoRollback(val packageName: String) : AppsTaskTerminal
    data class OutcomeUnknown(
        val taskKind: AppsTaskKind,
        val technicalCode: String = "APK_INSTALL_OUTCOME_UNKNOWN",
    ) : AppsTaskTerminal
    data class CleanupUncertain(val taskKind: AppsTaskKind) : AppsTaskTerminal
    data object ForceInstallRequired : AppsTaskTerminal
    data object Cancelled : AppsTaskTerminal
    data object Success : AppsTaskTerminal
}

internal object AppsInstallOutcomePolicy {
    fun terminal(result: ApkInstallResult?, forceAttempt: Boolean): AppsTaskTerminal = when (result) {
        is ApkInstallResult.VerifiedInstalled -> AppsTaskTerminal.Success
        is ApkInstallResult.PackageManagerAccepted -> AppsTaskTerminal.Success
        is ApkInstallResult.OldRemovedNoRollback ->
            AppsTaskTerminal.OldPackageRemovedNoRollback(result.packageName)
        is ApkInstallResult.Cancelled ->
            if (result.mutationStarted) {
                AppsTaskTerminal.OutcomeUnknown(
                    AppsTaskKind.APK_INSTALLATION,
                    "APK_INSTALL_CANCELLED_AFTER_MUTATION",
                )
            } else {
                AppsTaskTerminal.Cancelled
            }
        is ApkInstallResult.Rejected ->
            if (!forceAttempt && result.reason == "INSTALL_FAILED") {
                AppsTaskTerminal.ForceInstallRequired
            } else {
                AppsTaskTerminal.OutcomeUnknown(
                    AppsTaskKind.APK_INSTALLATION,
                    "APK_INSTALL_${result.reason}",
                )
            }
        is ApkInstallResult.OutcomeUnknown -> AppsTaskTerminal.OutcomeUnknown(
            AppsTaskKind.APK_INSTALLATION,
            result.technicalCode,
        )
        is ApkInstallResult.TimedOut -> AppsTaskTerminal.OutcomeUnknown(
            AppsTaskKind.APK_INSTALLATION,
            "APK_INSTALL_TIMEOUT_${result.stage.name}",
        )
        null -> AppsTaskTerminal.OutcomeUnknown(
            AppsTaskKind.APK_INSTALLATION,
            "APK_INSTALL_RESULT_UNAVAILABLE",
        )
    }
}

enum class AppsInstallStep {
    SELECTED,
    VALIDATING,
    READY,
    AWAITING_REPLACE_CONFIRMATION,
    AWAITING_SIGNATURE_MISMATCH_CONFIRMATION,
    UNINSTALLING_OLD,
    INSTALLING_NEW,
}

data class AppsTaskState(
    val taskId: String,
    val expectedSessionId: String,
    val kind: AppsTaskKind,
    val phase: AppsTaskPhase,
    val expectedComponents: List<ApkComponent> = emptyList(),
    val componentResult: AppsComponentResult? = null,
    val terminal: AppsTaskTerminal? = null,
    val installStep: AppsInstallStep? = null,
) {
    val navigationLocked: Boolean
        get() = phase.navigationLocked &&
            when (val value = componentResult) {
                is AppsComponentResult.PartialSuccess -> value.resourceUncertain || !value.cleanupConfirmed
                else -> terminal is AppsTaskTerminal.CleanupUncertain || terminal == null
            }
}

data class AppsUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val deviceDisplayName: String = "当前设备",
    val userId: Int? = null,
    val isLoading: Boolean = false,
    val isMetadataLoading: Boolean = false,
    val activeOperation: AppsOperation? = null,
    val activePackageName: String? = null,
    val applications: List<RemoteApplication> = emptyList(),
    val displayNameByPackage: Map<String, String?> = emptyMap(),
    val query: String = "",
    val draftQuery: String = query,
    val appliedQuery: String = query,
    val filter: AppsFilter = AppsFilter.ALL,
    val degradedReason: String? = null,
    val unavailableFields: Set<ApplicationField> = emptySet(),
    val error: AdbError? = null,
    val pendingConfirmation: AppsConfirmation? = null,
    val operationNotice: AppsOperationNotice? = null,
    val isLocalSession: Boolean = false,
    val applicationGeneration: Long = 0L,
    val task: AppsTaskState? = null,
) {
    val visibleApplications: List<RemoteApplication>
        get() {
            val needle = appliedQuery.ifBlank { query }.trim()
            return applications.filter { application ->
                val displayName = displayNameByPackage[application.packageName]
                (needle.isEmpty() ||
                    application.packageName.contains(needle, ignoreCase = true) ||
                    displayName?.contains(needle, ignoreCase = true) == true) && when (filter) {
                    AppsFilter.ALL -> true
                    AppsFilter.ENABLED -> application.enabledState == RemoteApplicationEnabledState.ENABLED
                    AppsFilter.DISABLED -> application.enabledState == RemoteApplicationEnabledState.DISABLED
                }
            }
        }

    val isBusy: Boolean get() = activeOperation != null || task?.terminal == null && task != null
    val isPageLoading: Boolean get() = isLoading || isMetadataLoading
    val navigationLocked: Boolean get() = task?.navigationLocked == true
}

internal object AppsPolicy {
    const val SELF_PACKAGE_NAME = "com.sheen.adbhelper"

    fun rowActions(application: RemoteApplication): List<AppsRowAction> = when (application.classification) {
        ApplicationClassification.ORDINARY -> listOf(
            AppsRowAction.EXTRACT_APK,
            AppsRowAction.SET_ENABLED,
            AppsRowAction.FORCE_STOP,
            AppsRowAction.UNINSTALL,
        )
        ApplicationClassification.SYSTEM,
        ApplicationClassification.UNKNOWN,
        -> listOf(AppsRowAction.EXTRACT_APK)
    }

    fun canMutate(state: AppsUiState, application: RemoteApplication): Boolean =
        state.isConnected && !state.isBusy && state.userId == application.userId &&
            state.applications.any { it.packageName == application.packageName } &&
            !application.isSystem && application.classification == ApplicationClassification.ORDINARY &&
            application.enabledState != RemoteApplicationEnabledState.UNKNOWN &&
            !(state.isLocalSession && application.packageName == SELF_PACKAGE_NAME)

    fun confirmation(state: AppsUiState, packageName: String, operation: AppsOperation): AppsConfirmation? {
        if (operation == AppsOperation.LOADING) return null
        val application = state.applications.singleOrNull { it.packageName == packageName } ?: return null
        if (!canMutate(state, application)) return null
        val operationMatchesState = when (operation) {
            AppsOperation.FORCE_STOP -> true
            AppsOperation.DISABLE -> application.enabledState == RemoteApplicationEnabledState.ENABLED
            AppsOperation.ENABLE -> application.enabledState == RemoteApplicationEnabledState.DISABLED
            AppsOperation.LOADING -> false
        }
        if (!operationMatchesState) return null
        return AppsConfirmation(
            operation = operation,
            packageName = packageName,
            sessionId = state.sessionId ?: return null,
            deviceDisplayName = state.deviceDisplayName,
            userId = state.userId ?: return null,
        )
    }

    fun changedSession(current: AppsUiState, connected: Boolean, sessionId: String?, deviceName: String, local: Boolean) =
        if (sessionId != current.sessionId) {
            AppsUiState(
                isConnected = connected,
                sessionId = sessionId,
                deviceDisplayName = deviceName,
                isLocalSession = local,
            )
        } else {
            current.copy(isConnected = connected, deviceDisplayName = deviceName, isLocalSession = local)
        }
}
