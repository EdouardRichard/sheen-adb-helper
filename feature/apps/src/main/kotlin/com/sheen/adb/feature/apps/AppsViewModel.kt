package com.sheen.adb.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ApplicationMutationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppsViewModel(private val manager: AdbSessionManager) : ViewModel() {
    private val mutableState = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var metadataOperation: Job? = null
    private var operationGeneration = 0L
    private var metadataGeneration = 0L
    private var foreground = false
    private var loadAttemptedSessionId: String? = null

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                val changed = connected?.sessionId != mutableState.value.sessionId
                if (changed) {
                    cancelJobWithoutNotice()
                    loadAttemptedSessionId = null
                }
                val endpoint = connected?.endpoint
                val local = endpoint?.host.equals("127.0.0.1", true) || endpoint?.host.equals("::1", true)
                val name = when {
                    connected == null -> "当前设备"
                    local -> "本机设备"
                    else -> endpoint?.host ?: "当前设备"
                }
                mutableState.value = AppsPolicy.changedSession(
                    mutableState.value,
                    connected = connected != null,
                    sessionId = connected?.sessionId,
                    deviceName = name,
                    local = local,
                )
                if (foreground && connected != null && loadAttemptedSessionId != connected.sessionId) refresh()
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            dismissConfirmation()
            cancelMetadataLoad()
            loadAttemptedSessionId = null
            cancelActive(leavingPage = true)
        } else {
            val sessionId = mutableState.value.sessionId
            if (mutableState.value.isConnected && sessionId != null && loadAttemptedSessionId != sessionId) refresh()
        }
    }

    fun setDeviceDisplayName(value: String) {
        val safe = value.trim().take(80)
        if (safe.isNotEmpty()) mutableState.update { it.copy(deviceDisplayName = safe) }
    }

    fun updateQuery(value: String) = mutableState.update { it.copy(query = value.take(255)) }

    fun setFilter(value: AppsFilter) = mutableState.update { it.copy(filter = value) }

    fun refresh() {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (!foreground || !current.isConnected || current.isBusy) return
        cancelMetadataLoad()
        loadAttemptedSessionId = sessionId
        startOperation(AppsOperation.LOADING, null) { generation ->
            when (val result = manager.listApplications()) {
                is AdbOperationResult.Success -> if (isCurrent(generation, result.value.sessionId)) {
                    mutableState.update {
                        it.copy(
                            userId = result.value.userId,
                            applications = result.value.applications,
                            metadataByPackage = result.value.applications.associate {
                                it.packageName to AppsApplicationMetadata()
                            },
                            unavailableFields = result.value.unavailableFields,
                            degradedReason = result.value.degradedReason,
                            error = null,
                            operationNotice = null,
                        )
                    }
                    startMetadataLoad(result.value.sessionId, result.value.userId)
                }
                is AdbOperationResult.Failure -> if (isCurrent(generation, sessionId)) {
                    mutableState.update { it.copy(error = result.error) }
                }
                AdbOperationResult.Cancelled -> Unit
            }
        }
    }

    fun requestForceStop(packageName: String) = requestConfirmation(packageName, AppsOperation.FORCE_STOP)
    fun requestDisable(packageName: String) = requestConfirmation(packageName, AppsOperation.DISABLE)
    fun requestEnable(packageName: String) = requestConfirmation(packageName, AppsOperation.ENABLE)

    private fun requestConfirmation(packageName: String, action: AppsOperation) {
        val confirmation = AppsPolicy.confirmation(mutableState.value, packageName, action) ?: return
        mutableState.update { it.copy(pendingConfirmation = confirmation, error = null, operationNotice = null) }
    }

    fun dismissConfirmation() = mutableState.update { it.copy(pendingConfirmation = null) }

    fun confirmPending() {
        val confirmation = mutableState.value.pendingConfirmation ?: return
        if (confirmation.sessionId != mutableState.value.sessionId) {
            dismissConfirmation()
            return
        }
        mutableState.update { it.copy(pendingConfirmation = null) }
        startOperation(confirmation.operation, confirmation.packageName) { generation ->
            val result = when (confirmation.operation) {
                AppsOperation.FORCE_STOP -> manager.forceStopApplication(
                    confirmation.packageName,
                    confirmation.sessionId,
                )
                AppsOperation.DISABLE -> manager.setApplicationEnabled(
                    confirmation.packageName,
                    enabled = false,
                    expectedSessionId = confirmation.sessionId,
                )
                AppsOperation.ENABLE -> manager.setApplicationEnabled(
                    confirmation.packageName,
                    enabled = true,
                    expectedSessionId = confirmation.sessionId,
                )
                AppsOperation.LOADING -> return@startOperation
            }
            if (!isCurrent(generation, confirmation.sessionId)) return@startOperation
            when (result) {
                is AdbOperationResult.Success -> applyMutationResult(result.value, confirmation)
                is AdbOperationResult.Failure -> mutableState.update { it.copy(error = result.error) }
                AdbOperationResult.Cancelled -> mutableState.update {
                    it.copy(operationNotice = AppsOperationNotice(UNKNOWN_NOTICE, outcomeUnknown = true))
                }
            }
        }
    }

    private fun applyMutationResult(result: ApplicationMutationResult, confirmation: AppsConfirmation) {
        if (result.sessionId != mutableState.value.sessionId) return
        when (result) {
            is ApplicationMutationResult.Verified -> mutableState.update { current ->
                current.copy(
                    applications = current.applications.map {
                        if (it.packageName == result.application.packageName) result.application else it
                    },
                    error = null,
                    operationNotice = AppsOperationNotice(
                        if (confirmation.operation == AppsOperation.DISABLE) "已验证应用处于禁用状态。"
                        else "已验证应用处于重新启用状态；这不表示应用已运行或数据已恢复。",
                    ),
                )
            }
            is ApplicationMutationResult.RequestAccepted -> mutableState.update {
                it.copy(error = null, operationNotice = AppsOperationNotice("已发送强制停止请求；应用仍可能由系统或其他组件重新启动。"))
            }
            is ApplicationMutationResult.OutcomeUnknown -> mutableState.update {
                it.copy(error = null, operationNotice = AppsOperationNotice(UNKNOWN_NOTICE, outcomeUnknown = true))
            }
        }
    }

    fun cancelActive(leavingPage: Boolean = false) {
        val active = mutableState.value.activeOperation
        if (active == null) return
        val mutation = active != AppsOperation.LOADING
        operationGeneration++
        operation?.cancel()
        operation = null
        mutableState.update {
            it.copy(
                isLoading = false,
                activeOperation = null,
                activePackageName = null,
                pendingConfirmation = if (leavingPage) null else it.pendingConfirmation,
                operationNotice = if (mutation) AppsOperationNotice(UNKNOWN_NOTICE, true) else it.operationNotice,
            )
        }
    }

    fun dismissNotice() = mutableState.update { it.copy(operationNotice = null) }

    private fun startOperation(action: AppsOperation, packageName: String?, block: suspend (Long) -> Unit) {
        if (mutableState.value.isBusy) return
        val generation = ++operationGeneration
        mutableState.update {
            it.copy(
                isLoading = action == AppsOperation.LOADING,
                activeOperation = action,
                activePackageName = packageName,
                error = null,
                operationNotice = null,
            )
        }
        operation = viewModelScope.launch {
            try {
                block(generation)
            } finally {
                if (generation == operationGeneration) {
                    operation = null
                    mutableState.update {
                        it.copy(isLoading = false, activeOperation = null, activePackageName = null)
                    }
                }
            }
        }
    }

    private fun isCurrent(generation: Long, sessionId: String): Boolean =
        generation == operationGeneration && foreground && mutableState.value.sessionId == sessionId

    private fun startMetadataLoad(sessionId: String, userId: Int) {
        cancelMetadataLoad()
        val generation = ++metadataGeneration
        metadataOperation = viewModelScope.launch {
            try {
                manager.observeApplicationMetadata(sessionId).collect { result ->
                    if (!isCurrentMetadata(generation, sessionId, userId)) return@collect
                    when (result) {
                        is AdbOperationResult.Success -> applyMetadataUpdate(result.value, generation)
                        is AdbOperationResult.Failure -> mutableState.update { it.copy(error = result.error) }
                        AdbOperationResult.Cancelled -> Unit
                    }
                }
            } finally {
                if (generation == metadataGeneration) metadataOperation = null
            }
        }
    }

    private fun applyMetadataUpdate(
        update: com.sheen.adb.core.ApplicationMetadataUpdate,
        generation: Long,
    ) {
        if (!isCurrentMetadata(generation, update.sessionId, update.userId)) return
        mutableState.update { current ->
            if (current.applications.none { it.packageName == update.packageName }) return@update current
            val next = current.metadataByPackage.toMutableMap()
            update.evictedIconPackages.forEach { packageName ->
                next[packageName]?.let { retained -> next[packageName] = retained.copy(icon = null) }
            }
            next[update.packageName] = AppsApplicationMetadata(
                displayName = update.displayName,
                icon = update.icon,
                status = update.status,
            )
            current.copy(metadataByPackage = next, error = null)
        }
    }

    private fun isCurrentMetadata(generation: Long, sessionId: String, userId: Int): Boolean =
        generation == metadataGeneration && foreground && mutableState.value.let {
            it.sessionId == sessionId && it.userId == userId
        }

    private fun cancelMetadataLoad() {
        metadataGeneration++
        metadataOperation?.cancel()
        metadataOperation = null
    }

    private fun cancelJobWithoutNotice() {
        operationGeneration++
        operation?.cancel()
        operation = null
        cancelMetadataLoad()
    }

    override fun onCleared() {
        foreground = false
        cancelJobWithoutNotice()
        super.onCleared()
    }

    private companion object {
        const val UNKNOWN_NOTICE = "结果未知：操作可能已在设备执行。请重新连接并刷新，以设备实际状态为准。"
    }
}
