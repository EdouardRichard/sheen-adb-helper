package com.sheen.adb.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ApplicationMutationResult
import com.sheen.adb.core.ApplicationUninstallPreparation
import com.sheen.adb.core.ApplicationUninstallResult
import com.sheen.adb.core.ApkExtractionRequest
import com.sheen.adb.core.ApkInstallMode
import com.sheen.adb.core.ApkInstallResult
import com.sheen.adb.data.SafComponentOutputStore
import com.sheen.adb.data.SafSource
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppsViewModel(private val manager: AdbSessionManager) : ViewModel() {
    private val mutableState = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = mutableState.asStateFlow()
    private val mutablePendingUninstall = MutableStateFlow<ApplicationUninstallPreparation?>(null)
    val pendingUninstall: StateFlow<ApplicationUninstallPreparation?> =
        mutablePendingUninstall.asStateFlow()
    private var operation: Job? = null
    private var taskJob: Job? = null
    private var metadataJob: Job? = null
    private var metadataGeneration = 0L
    private val tasks = AppsTasks(manager)
    private var pendingInstallSource: SafSource? = null
    private var operationGeneration = 0L
    private var foreground = false
    private var loadAttemptedSessionId: String? = null

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                val changed = connected?.sessionId != mutableState.value.sessionId
                if (changed) {
                    cancelJobWithoutNotice()
                    cancelMetadata()
                    cancelTaskAndCleanup()
                    mutablePendingUninstall.value = null
                    pendingInstallSource = null
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
            val state = mutableState.value
            val pickerOpen = state.task?.phase == AppsTaskPhase.PickerOpen
            dismissConfirmation()
            tasks.cancelPendingConfirmation()
            if (!pickerOpen) {
                if (state.activeOperation == AppsOperation.LOADING) {
                    loadAttemptedSessionId = null
                }
                cancelActive(leavingPage = true)
                releaseMetadataPageLoading()
                cancelTaskAndCleanup()
            }
        } else {
            val sessionId = mutableState.value.sessionId
            if (mutableState.value.isConnected && sessionId != null && loadAttemptedSessionId != sessionId) refresh()
        }
    }

    fun setDeviceDisplayName(value: String) {
        val safe = value.trim().take(80)
        if (safe.isNotEmpty()) mutableState.update { it.copy(deviceDisplayName = safe) }
    }

    fun updateQuery(value: String) = mutableState.update {
        val safe = value.take(255)
        it.copy(query = safe, draftQuery = safe, appliedQuery = safe)
    }

    fun setFilter(value: AppsFilter) = mutableState.update { it.copy(filter = value) }

    fun refresh() {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (!foreground || !current.isConnected || current.isBusy) return
        cancelMetadata()
        loadAttemptedSessionId = sessionId
        startOperation(AppsOperation.LOADING, null) { generation ->
            when (val result = manager.listApplications()) {
                is AdbOperationResult.Success -> if (isCurrent(generation, result.value.sessionId)) {
                    mutableState.update {
                        it.copy(
                            userId = result.value.userId,
                            applications = result.value.applications,
                            displayNameByPackage = result.value.applications.associate { it.packageName to null },
                            unavailableFields = result.value.unavailableFields,
                            degradedReason = result.value.degradedReason,
                            applicationGeneration = result.value.generation,
                            error = null,
                            operationNotice = null,
                        )
                    }
                    observeMetadata(result.value.sessionId)
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

    fun beginApkExtraction(packageName: String) {
        val state = mutableState.value
        val sessionId = state.sessionId ?: return
        val userId = state.userId ?: return
        if (state.task != null || state.applications.none { it.packageName == packageName }) return
        mutableState.update {
            it.copy(
                task = AppsTaskState(
                    taskId = UUID.randomUUID().toString(),
                    expectedSessionId = sessionId,
                    kind = AppsTaskKind.APK_EXTRACTION,
                    phase = AppsTaskPhase.PickerOpen,
                ),
                activePackageName = packageName,
            )
        }
    }

    fun beginApkInstallation() {
        val sessionId = mutableState.value.sessionId ?: return
        if (mutableState.value.task != null) return
        mutableState.update {
            it.copy(
                task = AppsTaskState(
                    taskId = UUID.randomUUID().toString(),
                    expectedSessionId = sessionId,
                    kind = AppsTaskKind.APK_INSTALLATION,
                    phase = AppsTaskPhase.PickerOpen,
                ),
            )
        }
    }

    fun deliverExtraction(
        destinationTreeId: String,
        outputs: SafComponentOutputStore,
    ) {
        val current = mutableState.value
        val task = current.task?.takeIf { it.kind == AppsTaskKind.APK_EXTRACTION } ?: return
        val packageName = current.activePackageName ?: return
        val userId = current.userId ?: return
        cancelActive()
        cancelMetadata()
        mutableState.update { state ->
            if (state.task?.taskId == task.taskId) state.copy(activePackageName = packageName) else state
        }
        taskJob = viewModelScope.launch {
            val result = tasks.extractApk(
                request = ApkExtractionRequest(task.expectedSessionId, userId, packageName),
                destinationTreeId = destinationTreeId,
                directoryDisplayName = "$packageName-apk",
                outputs = outputs,
            ) { phase -> updateTaskPhase(task.taskId, phase) }
            mutableState.update { state ->
                if (state.task?.taskId != task.taskId) state else state.copy(
                    task = state.task.copy(
                        componentResult = result,
                        terminal = AppsTaskTerminal.Extraction(result),
                    ),
                )
            }
        }
    }

    fun installSelectedApk(
        source: SafSource,
        mode: ApkInstallMode,
        expectedPackageName: String?,
    ) {
        pendingInstallSource = source
        runInstall(source, mode, expectedPackageName, forceAttempt = mode != ApkInstallMode.STANDARD)
    }

    private fun runInstall(
        source: SafSource,
        mode: ApkInstallMode,
        expectedPackageName: String?,
        forceAttempt: Boolean,
    ) {
        val current = mutableState.value
        val task = current.task?.takeIf { it.kind == AppsTaskKind.APK_INSTALLATION } ?: return
        val userId = current.userId ?: return
        cancelActive()
        cancelMetadata()
        mutableState.update { state ->
            if (state.task?.taskId != task.taskId) state else state.copy(
                task = state.task.copy(
                    phase = AppsTaskPhase.Preparing,
                    terminal = null,
                    installStep = if (forceAttempt) {
                        AppsInstallStep.INSTALLING_NEW
                    } else {
                        AppsInstallStep.VALIDATING
                    },
                ),
            )
        }
        taskJob = viewModelScope.launch {
            val result = tasks.installApk(
                source = source,
                expectedSessionId = task.expectedSessionId,
                userId = userId,
                mode = mode,
                expectedPackageName = expectedPackageName,
            ) { phase -> updateTaskPhase(task.taskId, phase) }
            mutableState.update { state ->
                if (state.task?.taskId != task.taskId) state else state.copy(
                    task = state.task.copy(
                        terminal = AppsInstallOutcomePolicy.terminal(result, forceAttempt),
                        installStep = if (
                            AppsInstallOutcomePolicy.terminal(result, forceAttempt) ==
                            AppsTaskTerminal.ForceInstallRequired
                        ) {
                            AppsInstallStep.AWAITING_REPLACE_CONFIRMATION
                        } else {
                            state.task.installStep
                        },
                    ),
                )
            }
            if (AppsInstallOutcomePolicy.terminal(result, forceAttempt) != AppsTaskTerminal.ForceInstallRequired) {
                pendingInstallSource = null
            }
            if (result is ApkInstallResult.VerifiedInstalled) refresh()
        }
    }

    fun confirmForceInstall() {
        val source = pendingInstallSource ?: return
        val task = mutableState.value.task ?: return
        if (task.terminal != AppsTaskTerminal.ForceInstallRequired) return
        runInstall(
            source = source,
            mode = ApkInstallMode.REPLACE_OR_DOWNGRADE,
            expectedPackageName = null,
            forceAttempt = true,
        )
    }

    fun requestUninstall(packageName: String) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        val userId = current.userId ?: return
        if (current.isBusy) return
        taskJob = viewModelScope.launch {
            mutablePendingUninstall.value = tasks.requestUninstall(
                sessionId,
                userId,
                packageName,
                current.applicationGeneration,
            )
        }
    }

    fun confirmUninstall() {
        if (mutablePendingUninstall.value == null) return
        mutablePendingUninstall.value = null
        taskJob = viewModelScope.launch {
            when (val result = tasks.confirmUninstall(deletePrivateDataAcknowledged = true)) {
                is ApplicationUninstallResult.VerifiedRemoved -> mutableState.update { state ->
                    state.copy(
                        applications = state.applications.filterNot {
                            it.packageName == result.packageName
                        },
                        displayNameByPackage = state.displayNameByPackage - result.packageName,
                        operationNotice = AppsOperationNotice("应用已卸载。"),
                        error = null,
                    )
                }
                else -> mutableState.update {
                    it.copy(
                        operationNotice = AppsOperationNotice(
                            "卸载结果未确认，请刷新应用列表。",
                            outcomeUnknown = true,
                        ),
                    )
                }
            }
        }
    }

    fun requestForceInstall(signatureMismatch: Boolean) =
        tasks.requestForceInstall(signatureMismatch)

    fun confirmSignatureMismatch(nonce: String): Boolean =
        tasks.confirmSignatureMismatch(nonce)

    fun cancelPendingConfirmation() {
        mutablePendingUninstall.value = null
        tasks.cancelPendingConfirmation()
    }

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
                is AdbOperationResult.Success -> {
                    applyMutationResult(result.value, confirmation)
                    syncApplicationSnapshot(generation, confirmation.sessionId)
                }
                is AdbOperationResult.Failure -> mutableState.update { it.copy(error = result.error) }
                AdbOperationResult.Cancelled -> mutableState.update {
                    it.copy(operationNotice = AppsOperationNotice(UNKNOWN_NOTICE, outcomeUnknown = true))
                }
            }
        }
    }

    private suspend fun syncApplicationSnapshot(generation: Long, expectedSessionId: String) {
        val result = manager.listApplications()
        val snapshot = (result as? AdbOperationResult.Success)?.value ?: return
        if (!isCurrent(generation, expectedSessionId) || snapshot.sessionId != expectedSessionId) return
        val packageNames = snapshot.applications.mapTo(hashSetOf()) { it.packageName }
        mutableState.update { current ->
            if (current.sessionId != expectedSessionId) {
                current
            } else {
                current.copy(
                    userId = snapshot.userId,
                    applications = snapshot.applications,
                    applicationGeneration = snapshot.generation,
                    displayNameByPackage = current.displayNameByPackage.filterKeys(packageNames::contains),
                )
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

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun dismissTaskResult() {
        pendingInstallSource = null
        mutableState.update { state ->
            if (state.navigationLocked) state else state.copy(task = null, activePackageName = null)
        }
    }

    fun cancelPageLoading() {
        if (mutableState.value.activeOperation == AppsOperation.LOADING) {
            loadAttemptedSessionId = null
        }
        cancelActive(leavingPage = false)
        releaseMetadataPageLoading()
    }

    private fun updateTaskPhase(taskId: String, phase: AppsTaskPhase) {
        mutableState.update { state ->
            if (state.task?.taskId != taskId) state else state.copy(task = state.task.copy(phase = phase))
        }
    }

    private fun cancelTaskAndCleanup() {
        taskJob?.cancel()
        taskJob = null
        pendingInstallSource = null
        mutableState.update { state ->
            if (state.task?.navigationLocked == true) {
                state.copy(
                    task = state.task.copy(
                        phase = AppsTaskPhase.AwaitingCleanup,
                        terminal = AppsTaskTerminal.CleanupUncertain(state.task.kind),
                    ),
                )
            } else {
                state.copy(task = null, activePackageName = null)
            }
        }
    }

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


    private fun cancelJobWithoutNotice() {
        operationGeneration++
        operation?.cancel()
        operation = null
    }

    private fun observeMetadata(expectedSessionId: String) {
        cancelMetadata()
        val generation = ++metadataGeneration
        mutableState.update { current ->
            if (current.sessionId == expectedSessionId) {
                current.copy(isMetadataLoading = true)
            } else {
                current
            }
        }
        metadataJob = viewModelScope.launch {
            try {
                manager.observeApplicationMetadata(expectedSessionId).collect { result ->
                    val update = (result as? AdbOperationResult.Success)?.value ?: return@collect
                    mutableState.update { state ->
                        if (
                            state.sessionId != update.sessionId ||
                            state.userId != update.userId ||
                            state.applications.none { it.packageName == update.packageName }
                        ) {
                            state
                        } else {
                            state.copy(
                                displayNameByPackage =
                                    state.displayNameByPackage + (update.packageName to update.displayName),
                            )
                        }
                    }
                }
            } finally {
                if (metadataGeneration == generation) {
                    metadataJob = null
                    mutableState.update { current ->
                        if (current.sessionId == expectedSessionId) {
                            current.copy(isMetadataLoading = false)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun cancelMetadata() {
        metadataGeneration += 1L
        metadataJob?.cancel()
        metadataJob = null
        mutableState.update { it.copy(isMetadataLoading = false) }
    }

    private fun releaseMetadataPageLoading() {
        mutableState.update { it.copy(isMetadataLoading = false) }
    }

    override fun onCleared() {
        foreground = false
        cancelJobWithoutNotice()
        cancelMetadata()
        super.onCleared()
    }

    fun updateDraftQuery(value: String) = mutableState.update {
        it.copy(draftQuery = value.take(255))
    }

    fun applySearch() = mutableState.update {
        it.copy(appliedQuery = it.draftQuery, query = it.draftQuery)
    }

    private companion object {
        const val UNKNOWN_NOTICE = "结果未知：操作可能已在设备执行。请重新连接并刷新，以设备实际状态为准。"
    }
}
