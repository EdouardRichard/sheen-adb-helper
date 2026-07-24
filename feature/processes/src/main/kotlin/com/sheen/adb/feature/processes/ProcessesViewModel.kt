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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
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
    val pidQuery: String = "",
    val processQuery: String = "",
    val applicationQuery: String = "",
    val status: ProcessesAnalysisStatus = ProcessesAnalysisStatus.DISCONNECTED,
    val degradedReason: String? = null,
    val error: AdbError? = null,
    val pendingTermination: ProcessTerminationConfirmation? = null,
    val terminationResult: ProcessTerminationResult? = null,
    val terminationRequestCount: Int = 0,
) {
    val visibleEntries: List<ProcessSnapshotEntry>
        get() {
            val pidNeedle = pidQuery.trim()
            val processNeedle = processQuery.trim()
            val applicationNeedle = applicationQuery.trim()
            return entries.filter { entry ->
                (pidNeedle.isEmpty() || entry.pid.toString().contains(pidNeedle)) &&
                    (processNeedle.isEmpty() || entry.processName.contains(processNeedle, ignoreCase = true)) &&
                    (
                        applicationNeedle.isEmpty() ||
                            entry.applicationName.contains(applicationNeedle, ignoreCase = true) ||
                            entry.applicationPackage?.contains(applicationNeedle, ignoreCase = true) == true
                        )
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

    fun acceptSnapshot(current: ProcessesUiState, entries: List<ProcessSnapshotEntry>): Boolean {
        if (entries.isEmpty()) return true
        val sessionId = current.sessionId ?: return false
        val generations = entries.map { it.identity.observedGeneration }.distinct()
        return entries.all { it.identity.sessionId == sessionId } &&
            generations.size == 1 &&
            generations.single() >= current.generation
    }

    fun confirmedApplicationSet(
        selected: ProcessSnapshotEntry,
        entries: List<ProcessSnapshotEntry>,
    ) = selected.applicationPackage?.let { packageName ->
        entries.filter { it.applicationPackage == packageName }.map { it.identity }.toSet()
    }.orEmpty()
}

class ProcessesViewModel(private val manager: AdbSessionManager) : ViewModel() {
    private val mutableState = MutableStateFlow(ProcessesUiState())
    val state: StateFlow<ProcessesUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var operationGeneration = 0L

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId || connected == null) {
                    operationGeneration += 1
                    operation?.cancel()
                    operation = null
                    mutableState.value = ProcessesPolicy.changedSession(
                        current = mutableState.value,
                        connected = connected != null,
                        sessionId = connected?.sessionId,
                    )
                } else {
                    mutableState.update { it.copy(isConnected = true) }
                }
            }
        }
    }

    fun updatePidQuery(value: String) = mutableState.update { it.copy(pidQuery = value.take(MAX_QUERY_LENGTH)) }
    fun updateProcessQuery(value: String) = mutableState.update { it.copy(processQuery = value.take(MAX_QUERY_LENGTH)) }
    fun updateApplicationQuery(value: String) = mutableState.update {
        it.copy(applicationQuery = value.take(MAX_QUERY_LENGTH))
    }

    fun refresh() {
        val expectedSessionId = mutableState.value.sessionId ?: return
        if (!mutableState.value.isConnected || operation?.isActive == true) return
        val expectedOperation = ++operationGeneration
        operation = viewModelScope.launch {
            mutableState.update {
                it.copy(isLoading = true, status = ProcessesAnalysisStatus.LOADING, degradedReason = null, error = null)
            }
            when (val result = manager.refreshProcesses(expectedSessionId)) {
                is AdbOperationResult.Success -> mutableState.update { current ->
                    if (!isCurrent(expectedSessionId, expectedOperation) ||
                        !ProcessesPolicy.acceptSnapshot(current, result.value)
                    ) {
                        current
                    } else {
                        val generation = result.value.firstOrNull()?.identity?.observedGeneration
                            ?: (current.generation + 1)
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
                    if (!isCurrent(expectedSessionId, expectedOperation)) current else current.copy(
                        isLoading = false,
                        status = ProcessesAnalysisStatus.ERROR,
                        error = result.error,
                    )
                }
                AdbOperationResult.Cancelled -> mutableState.update { current ->
                    if (!isCurrent(expectedSessionId, expectedOperation)) current else current.copy(
                        isLoading = false,
                        status = ProcessesPolicy.cancelledStatus(),
                    )
                }
            }
            if (isCurrent(expectedSessionId, expectedOperation)) operation = null
        }
    }

    fun cancel() {
        operationGeneration += 1
        operation?.cancel()
        operation = null
        mutableState.update { current ->
            if (!current.isLoading) current else current.copy(
                isLoading = false,
                status = ProcessesPolicy.cancelledStatus(),
            )
        }
    }

    fun requestTermination(entry: ProcessSnapshotEntry) {
        val state = mutableState.value
        if (state.sessionId != entry.identity.sessionId || state.generation != entry.identity.observedGeneration) return
        mutableState.update { it.copy(pendingTermination = ProcessesPolicy.newConfirmation(entry)) }
    }

    fun selectTerminationScope(scope: ProcessTerminationScope) {
        mutableState.update { current ->
            val pending = current.pendingTermination ?: return@update current
            if (scope !in ProcessesPolicy.terminationScopes(pending.entry)) current
            else current.copy(pendingTermination = pending.copy(scope = scope))
        }
    }

    fun cancelTermination() = mutableState.update(ProcessesPolicy::cancelConfirmation)

    fun confirmTermination(nonce: String) {
        val current = mutableState.value
        if (!ProcessesPolicy.canConfirm(current, nonce) || operation?.isActive == true) return
        val pending = checkNotNull(current.pendingTermination)
        val scope = checkNotNull(pending.scope)
        val session = checkNotNull(current.sessionId)
        val confirmedSet = if (scope == ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP) {
            ProcessesPolicy.confirmedApplicationSet(pending.entry, current.entries)
        } else {
            emptySet()
        }
        mutableState.update {
            it.copy(
                pendingTermination = null,
                isLoading = true,
                terminationRequestCount = it.terminationRequestCount + 1,
            )
        }
        val expectedOperation = ++operationGeneration
        operation = viewModelScope.launch {
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
            mutableState.update {
                if (!isCurrent(session, expectedOperation)) it else it.copy(
                    isLoading = false,
                    terminationResult = (result as? AdbOperationResult.Success)?.value,
                    status = if (result is AdbOperationResult.Failure) ProcessesAnalysisStatus.ERROR else it.status,
                    error = (result as? AdbOperationResult.Failure)?.error,
                )
            }
            if (isCurrent(session, expectedOperation)) operation = null
        }
    }

    private fun isCurrent(expectedSessionId: String, expectedOperation: Long): Boolean =
        operationGeneration == expectedOperation && mutableState.value.sessionId == expectedSessionId

    override fun onCleared() {
        operationGeneration += 1
        operation?.cancel()
        operation = null
        super.onCleared()
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 255
    }
}
