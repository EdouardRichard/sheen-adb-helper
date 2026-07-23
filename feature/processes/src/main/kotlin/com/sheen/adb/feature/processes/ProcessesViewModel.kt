package com.sheen.adb.feature.processes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ProcessAnalysisEntry
import com.sheen.adb.core.ProcessApplicationAssociation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class ProcessesUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val generation: Long = 0,
    val entries: List<ProcessAnalysisEntry> = emptyList(),
    val pidQuery: String = "",
    val processQuery: String = "",
    val applicationQuery: String = "",
    val status: ProcessesAnalysisStatus = ProcessesAnalysisStatus.DISCONNECTED,
    val degradedReason: String? = null,
    val error: AdbError? = null,
) {
    val visibleEntries: List<ProcessAnalysisEntry>
        get() {
            val pidNeedle = pidQuery.trim()
            val processNeedle = processQuery.trim()
            val applicationNeedle = applicationQuery.trim()
            return entries.filter { entry ->
                (pidNeedle.isEmpty() || entry.process.pid.toString().contains(pidNeedle)) &&
                    (processNeedle.isEmpty() || entry.process.name.contains(processNeedle, ignoreCase = true)) &&
                    (applicationNeedle.isEmpty() || entry.applicationAssociation.matches(applicationNeedle))
            }
        }
}

object ProcessesPolicy {
    fun classifyRefresh(
        previous: List<ProcessAnalysisEntry>,
        current: List<ProcessAnalysisEntry>,
        degradedReason: String?,
    ): ProcessesAnalysisStatus = when {
        current.isEmpty() && !degradedReason.isNullOrBlank() -> ProcessesAnalysisStatus.UNSUPPORTED
        current.isEmpty() -> ProcessesAnalysisStatus.EMPTY
        previous.isNotEmpty() && previous.any { old -> current.none { it.process.pid == old.process.pid } } ->
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
}

private fun ProcessApplicationAssociation.matches(query: String): Boolean = when (this) {
    is ProcessApplicationAssociation.Verified -> packageName.contains(query, ignoreCase = true)
    is ProcessApplicationAssociation.Multiple -> packageNames.any { it.contains(query, ignoreCase = true) }
    is ProcessApplicationAssociation.Unknown -> false
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
                it.copy(
                    isLoading = true,
                    status = ProcessesAnalysisStatus.LOADING,
                    degradedReason = null,
                    error = null,
                )
            }
            when (val result = manager.loadProcessAnalysis(expectedSessionId)) {
                is AdbOperationResult.Success -> mutableState.update { current ->
                    if (!isCurrent(expectedSessionId, expectedOperation) || result.value.sessionId != expectedSessionId) {
                        current
                    } else {
                        current.copy(
                            isLoading = false,
                            generation = result.value.generation,
                            entries = result.value.entries,
                            status = ProcessesPolicy.classifyRefresh(
                                previous = current.entries,
                                current = result.value.entries,
                                degradedReason = result.value.degradedReason,
                            ),
                            degradedReason = result.value.degradedReason,
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
