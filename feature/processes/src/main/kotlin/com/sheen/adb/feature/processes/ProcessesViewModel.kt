package com.sheen.adb.feature.processes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessTerminationRequest
import com.sheen.adb.core.ProcessTerminationResult
import com.sheen.adb.core.ProcessTerminationScope
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ProcessesAnalysisStatus {
    DISCONNECTED,
    LOADING,
    READY,
    EMPTY,
    PROCESSES_EXITED,
    UNSUPPORTED,
    CANCELLED,
    ERROR,
}

data class ProcessTerminationConfirmation(
    val nonce: String,
    val entry: ProcessSnapshotEntry,
    val scope: ProcessTerminationScope? = null,
)

data class ProcessesUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val generation: Long = 0,
    val entries: List<ProcessSnapshotEntry> = emptyList(),
    val query: String = "",
    val status: ProcessesAnalysisStatus = ProcessesAnalysisStatus.DISCONNECTED,
    val degradedReason: String? = null,
    val error: AdbError? = null,
    val pendingTermination: ProcessTerminationConfirmation? = null,
    val terminationResult: ProcessTerminationResult? = null,
    val terminationRequestCount: Int = 0,
    val terminationInProgress: Boolean = false,
) {
    val visibleEntries: List<ProcessSnapshotEntry>
        get() {
            val needle = query.trim()
            return if (needle.isEmpty()) {
                entries
            } else {
                entries.filter { it.processName.contains(needle, ignoreCase = true) }
            }
        }
}

object ProcessesPolicy {
    fun classifyRefresh(
        previous: List<ProcessSnapshotEntry>,
        current: List<ProcessSnapshotEntry>,
        degradedReason: String?,
    ): ProcessesAnalysisStatus = when {
        current.isEmpty() && !degradedReason.isNullOrBlank() -> ProcessesAnalysisStatus.UNSUPPORTED
        current.isEmpty() -> ProcessesAnalysisStatus.EMPTY
        previous.isNotEmpty() && previous.any { old -> current.none { it.pid == old.pid } } ->
            ProcessesAnalysisStatus.PROCESSES_EXITED
        else -> ProcessesAnalysisStatus.READY
    }

    fun cancelledStatus(): ProcessesAnalysisStatus = ProcessesAnalysisStatus.CANCELLED

    fun changedSession(
        current: ProcessesUiState,
        connected: Boolean,
        sessionId: String?,
    ): ProcessesUiState {
        if (connected && sessionId == current.sessionId) return current.copy(isConnected = true)
        return ProcessesUiState(
            isConnected = connected,
            sessionId = sessionId,
            status = if (connected) ProcessesAnalysisStatus.EMPTY else ProcessesAnalysisStatus.DISCONNECTED,
        )
    }

    fun terminationScopes(entry: ProcessSnapshotEntry): List<ProcessTerminationScope> = buildList {
        add(ProcessTerminationScope.SINGLE_PROCESS)
        if (entry.applicationPackage != null) add(ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP)
    }

    fun newConfirmation(
        entry: ProcessSnapshotEntry,
        scope: ProcessTerminationScope? = null,
    ) = ProcessTerminationConfirmation(UUID.randomUUID().toString(), entry, scope)

    fun cancelConfirmation(state: ProcessesUiState): ProcessesUiState =
        state.copy(pendingTermination = null)

    fun canConfirm(state: ProcessesUiState, nonce: String): Boolean {
        val pending = state.pendingTermination ?: return false
        return state.isConnected &&
            pending.scope != null &&
            pending.nonce == nonce &&
            pending.entry.identity.sessionId == state.sessionId &&
            pending.entry.identity.observedGeneration == state.generation
    }

    fun acceptSnapshot(
        current: ProcessesUiState,
        snapshotSessionId: String,
        snapshotGeneration: Long,
        entries: List<ProcessSnapshotEntry>,
    ): Boolean {
        val sessionId = current.sessionId ?: return false
        if (snapshotSessionId != sessionId || snapshotGeneration < current.generation) return false
        return entries.all {
            it.identity.sessionId == snapshotSessionId &&
                it.identity.observedGeneration == snapshotGeneration
        }
    }

    fun acceptSnapshot(
        current: ProcessesUiState,
        entries: List<ProcessSnapshotEntry>,
    ): Boolean {
        if (entries.isEmpty()) return true
        val generation = entries.first().identity.observedGeneration
        return acceptSnapshot(
            current = current,
            snapshotSessionId = entries.first().identity.sessionId,
            snapshotGeneration = generation,
            entries = entries,
        )
    }

    fun confirmedApplicationSet(
        selected: ProcessSnapshotEntry,
        entries: List<ProcessSnapshotEntry>,
    ) = selected.applicationPackage?.let { packageName ->
        entries.filter { it.applicationPackage == packageName }.map { it.identity }.toSet()
    }.orEmpty()
}

class ProcessesViewModel(
    private val manager: AdbSessionManager,
    scope: CoroutineScope? = null,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) : ViewModel() {
    private val executionScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(ProcessesUiState())
    val state: StateFlow<ProcessesUiState> = mutableState.asStateFlow()

    private var pageVisible = false
    private var appForeground = false
    private var pollingJob: Job? = null
    private var manualRefreshJob: Job? = null
    private var terminationJob: Job? = null
    private var refreshEpoch = 0L

    init {
        executionScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId || connected == null) {
                    stopPolling(markCancelled = false)
                    terminationJob?.cancel()
                    terminationJob = null
                    mutableState.value = ProcessesPolicy.changedSession(
                        current = mutableState.value,
                        connected = connected != null,
                        sessionId = connected?.sessionId,
                    )
                } else {
                    mutableState.update { it.copy(isConnected = true) }
                }
                updatePolling()
            }
        }
    }

    fun updateQuery(value: String) = mutableState.update {
        it.copy(query = value.take(MAX_QUERY_LENGTH))
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun setForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        updatePolling()
    }

    fun onPageVisible(visible: Boolean) {
        if (pageVisible == visible) return
        pageVisible = visible
        updatePolling()
    }

    fun refresh() {
        if (pollingJob?.isActive == true || manualRefreshJob?.isActive == true) return
        val sessionId = mutableState.value.sessionId ?: return
        val epoch = ++refreshEpoch
        manualRefreshJob = executionScope.launch {
            refreshOnce(sessionId, epoch)
            if (refreshEpoch == epoch) manualRefreshJob = null
        }
    }

    fun cancel() {
        stopPolling(markCancelled = true)
        manualRefreshJob?.cancel()
        manualRefreshJob = null
        mutableState.update { current ->
            if (!current.isLoading) current else current.copy(
                isLoading = false,
                status = ProcessesPolicy.cancelledStatus(),
            )
        }
    }

    fun requestTermination(entry: ProcessSnapshotEntry) {
        val current = mutableState.value
        if (current.sessionId != entry.identity.sessionId ||
            current.generation != entry.identity.observedGeneration
        ) {
            return
        }
        stopPolling(markCancelled = false)
        mutableState.update {
            it.copy(
                pendingTermination = ProcessesPolicy.newConfirmation(entry),
                terminationResult = null,
            )
        }
    }

    fun selectTerminationScope(scope: ProcessTerminationScope) {
        mutableState.update { current ->
            val pending = current.pendingTermination ?: return@update current
            if (scope !in ProcessesPolicy.terminationScopes(pending.entry)) current
            else current.copy(pendingTermination = pending.copy(scope = scope))
        }
    }

    fun cancelTermination() {
        mutableState.update(ProcessesPolicy::cancelConfirmation)
        updatePolling()
    }

    fun confirmTermination(nonce: String) {
        val current = mutableState.value
        if (!ProcessesPolicy.canConfirm(current, nonce) || terminationJob?.isActive == true) return
        val pending = checkNotNull(current.pendingTermination)
        val scope = checkNotNull(pending.scope)
        val session = checkNotNull(current.sessionId)
        val confirmedSet = if (scope == ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP) {
            ProcessesPolicy.confirmedApplicationSet(pending.entry, current.entries)
        } else {
            emptySet()
        }
        stopPolling(markCancelled = false)
        mutableState.update {
            it.copy(
                pendingTermination = null,
                terminationResult = null,
                terminationInProgress = true,
                terminationRequestCount = it.terminationRequestCount + 1,
                error = null,
            )
        }
        terminationJob = executionScope.launch {
            val result = manager.terminateProcess(
                ProcessTerminationRequest(
                    requestId = nonce,
                    sessionId = session,
                    scope = scope,
                    targetProcess = pending.entry.identity,
                    targetPackage = pending.entry.applicationPackage,
                    confirmedProcessSet = confirmedSet,
                    riskAcknowledged = true,
                    forceStopImpactAcknowledged = scope == ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP,
                ),
            )
            mutableState.update { latest ->
                if (latest.sessionId != session) {
                    latest
                } else {
                    latest.copy(
                        terminationInProgress = false,
                        terminationResult = (result as? AdbOperationResult.Success)?.value,
                        status = if (result is AdbOperationResult.Failure) {
                            ProcessesAnalysisStatus.ERROR
                        } else {
                            latest.status
                        },
                        error = (result as? AdbOperationResult.Failure)?.error,
                    )
                }
            }
            terminationJob = null
            updatePolling()
        }
    }

    private fun updatePolling() {
        val shouldPoll = pageVisible && appForeground && mutableState.value.isConnected
        if (!shouldPoll) {
            stopPolling(markCancelled = false)
            return
        }
        if (pollingJob?.isActive == true) return
        val sessionId = mutableState.value.sessionId ?: return
        val epoch = ++refreshEpoch
        pollingJob = executionScope.launch {
            while (isActive && shouldContinuePolling(sessionId, epoch)) {
                refreshOnce(sessionId, epoch)
                if (!isActive || !shouldContinuePolling(sessionId, epoch)) break
                delayMillis(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun refreshOnce(sessionId: String, epoch: Long) {
        mutableState.update { current ->
            if (!isCurrent(sessionId, epoch)) current else current.copy(
                isLoading = true,
                status = ProcessesAnalysisStatus.LOADING,
                degradedReason = null,
                error = null,
            )
        }
        when (val result = manager.refreshProcesses(sessionId)) {
            is AdbOperationResult.Success -> mutableState.update { current ->
                val generation = result.value.firstOrNull()?.identity?.observedGeneration
                    ?: (current.generation + 1)
                if (!isCurrent(sessionId, epoch) ||
                    !ProcessesPolicy.acceptSnapshot(current, sessionId, generation, result.value)
                ) {
                    current
                } else {
                    current.copy(
                        isLoading = false,
                        generation = generation,
                        entries = result.value,
                        status = ProcessesPolicy.classifyRefresh(current.entries, result.value, null),
                        error = null,
                    )
                }
            }
            is AdbOperationResult.Failure -> mutableState.update { current ->
                if (!isCurrent(sessionId, epoch)) current else current.copy(
                    isLoading = false,
                    status = ProcessesAnalysisStatus.ERROR,
                    error = result.error,
                )
            }
            AdbOperationResult.Cancelled -> mutableState.update { current ->
                if (!isCurrent(sessionId, epoch)) current else current.copy(
                    isLoading = false,
                    status = ProcessesPolicy.cancelledStatus(),
                )
            }
        }
    }

    private fun shouldContinuePolling(sessionId: String, epoch: Long): Boolean =
        pageVisible &&
            appForeground &&
            mutableState.value.isConnected &&
            mutableState.value.sessionId == sessionId &&
            refreshEpoch == epoch

    private fun isCurrent(sessionId: String, epoch: Long): Boolean =
        refreshEpoch == epoch && mutableState.value.sessionId == sessionId

    private fun stopPolling(markCancelled: Boolean) {
        refreshEpoch += 1
        pollingJob?.cancel()
        pollingJob = null
        if (mutableState.value.isLoading) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    status = if (markCancelled) ProcessesAnalysisStatus.CANCELLED else it.status,
                )
            }
        }
    }

    override fun onCleared() {
        stopPolling(markCancelled = false)
        manualRefreshJob?.cancel()
        terminationJob?.cancel()
        manualRefreshJob = null
        terminationJob = null
        super.onCleared()
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 255
        const val REFRESH_INTERVAL_MILLIS = 5_000L
    }
}
