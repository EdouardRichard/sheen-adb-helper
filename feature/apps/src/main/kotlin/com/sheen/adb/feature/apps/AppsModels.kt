package com.sheen.adb.feature.apps

import com.sheen.adb.core.AdbError
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
)

data class AppsOperationNotice(
    val message: String,
    val outcomeUnknown: Boolean = false,
)

data class AppsUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val deviceDisplayName: String = "当前设备",
    val userId: Int? = null,
    val isLoading: Boolean = false,
    val activeOperation: AppsOperation? = null,
    val activePackageName: String? = null,
    val applications: List<RemoteApplication> = emptyList(),
    val displayNameByPackage: Map<String, String?> = emptyMap(),
    val query: String = "",
    val filter: AppsFilter = AppsFilter.ALL,
    val degradedReason: String? = null,
    val unavailableFields: Set<ApplicationField> = emptySet(),
    val error: AdbError? = null,
    val pendingConfirmation: AppsConfirmation? = null,
    val operationNotice: AppsOperationNotice? = null,
    val isLocalSession: Boolean = false,
) {
    val visibleApplications: List<RemoteApplication>
        get() {
            val needle = query.trim()
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

    val isBusy: Boolean get() = activeOperation != null
}

internal object AppsPolicy {
    const val SELF_PACKAGE_NAME = "com.sheen.adbhelper"

    fun canMutate(state: AppsUiState, application: RemoteApplication): Boolean =
        state.isConnected && !state.isBusy && state.userId == application.userId &&
            state.applications.any { it.packageName == application.packageName } &&
            !application.isSystem && application.enabledState != RemoteApplicationEnabledState.UNKNOWN &&
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
