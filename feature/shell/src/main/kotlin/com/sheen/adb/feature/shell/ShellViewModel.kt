package com.sheen.adb.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ShellInputInterpreter
import com.sheen.adb.core.ShellInputPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class ShellUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val commandInput: String = "",
    val timeoutSeconds: Int = 30,
    val entries: List<ShellEntry> = emptyList(),
    val isRunning: Boolean = false,
    val outputWasDropped: Boolean = false,
    val showRiskNotice: Boolean = true,
    val pendingHostWrapper: ShellInputPlan.ConfirmHostWrapper? = null,
    val pendingRiskExecution: ShellExecutionRequest? = null,
    val error: AdbError? = null,
)

data class ShellExecutionRequest(
    val displayedCommand: String,
    val commandToExecute: String,
    val dispatchMode: ShellDispatchMode,
)

internal fun exactShellExecution(command: String): ShellExecutionRequest =
    ShellExecutionRequest(command, command, ShellDispatchMode.EXACT)

internal fun confirmedHostWrapperExecution(
    plan: ShellInputPlan.ConfirmHostWrapper,
): ShellExecutionRequest? = plan.remoteCommand?.let { remoteCommand ->
    ShellExecutionRequest(
        displayedCommand = plan.originalCommand,
        commandToExecute = remoteCommand,
        dispatchMode = ShellDispatchMode.CONFIRMED_HOST_WRAPPER_REMOVAL,
    )
}

class ShellViewModel(private val manager: AdbSessionManager) : ViewModel() {
    private val buffer = ShellTranscriptBuffer()
    private val mutableState = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var sequence = 0L

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId) {
                    operation?.cancel()
                    buffer.clear()
                    mutableState.value = ShellUiState(
                        isConnected = connected != null,
                        sessionId = connected?.sessionId,
                        showRiskNotice = mutableState.value.showRiskNotice,
                    )
                } else {
                    mutableState.update { it.copy(isConnected = connected != null) }
                }
            }
        }
    }

    fun updateCommand(value: String) = mutableState.update { it.copy(commandInput = value, error = null) }
    fun setTimeout(seconds: Int) = mutableState.update { it.copy(timeoutSeconds = seconds.coerceIn(1, 300)) }
    fun dismissRiskNotice() = mutableState.update { it.copy(showRiskNotice = false) }
    fun clear() { buffer.clear(); publish() }

    fun execute() {
        val command = mutableState.value.commandInput
        if (command.isBlank() || mutableState.value.isRunning || !mutableState.value.isConnected) return
        when (val plan = ShellInputInterpreter.plan(command)) {
            is ShellInputPlan.Exact -> requestExecution(exactShellExecution(plan.originalCommand))
            is ShellInputPlan.ConfirmHostWrapper -> mutableState.update { it.copy(pendingHostWrapper = plan) }
        }
    }

    fun confirmHostWrapper() {
        val plan = mutableState.value.pendingHostWrapper ?: return
        val request = confirmedHostWrapperExecution(plan) ?: return
        mutableState.update { it.copy(pendingHostWrapper = null) }
        requestExecution(request)
    }

    fun executeHostWrapperExactly() {
        val plan = mutableState.value.pendingHostWrapper ?: return
        mutableState.update { it.copy(pendingHostWrapper = null) }
        requestExecution(exactShellExecution(plan.originalCommand))
    }

    fun dismissHostWrapper() = mutableState.update { it.copy(pendingHostWrapper = null) }

    fun confirmRisk() {
        val request = mutableState.value.pendingRiskExecution ?: return
        mutableState.update { it.copy(pendingRiskExecution = null) }
        executeNow(request)
    }

    fun dismissRisk() = mutableState.update { it.copy(pendingRiskExecution = null) }

    private fun requestExecution(request: ShellExecutionRequest) {
        if (isHighRisk(request.commandToExecute)) {
            mutableState.update { it.copy(pendingRiskExecution = request) }
        } else executeNow(request)
    }

    private fun executeNow(request: ShellExecutionRequest) {
        val id = ++sequence
        val running = ShellEntry(
            sequence = id,
            command = request.displayedCommand,
            dispatchMode = request.dispatchMode,
        )
        buffer.add(running)
        mutableState.update {
            it.copy(
                commandInput = "",
                isRunning = true,
                pendingHostWrapper = null,
                pendingRiskExecution = null,
                error = null,
            )
        }
        publish()
        operation = viewModelScope.launch {
            val timeout = mutableState.value.timeoutSeconds.seconds
            when (val result = manager.executeShell(request.commandToExecute, timeout)) {
                is AdbOperationResult.Success -> buffer.replace(
                    id,
                    running.copy(
                        stdout = result.value.stdout,
                        stderr = result.value.stderr,
                        exitCode = result.value.exitCode,
                        outputMode = result.value.outputMode,
                        wasTruncated = result.value.wasTruncated,
                        status = if (result.value.exitCode == 0) ShellEntryStatus.SUCCEEDED else ShellEntryStatus.FAILED,
                    ),
                )
                is AdbOperationResult.Failure -> {
                    val status = when (result.error) {
                        is AdbError.Timeout -> ShellEntryStatus.TIMED_OUT
                        is AdbError.RemoteClosed -> ShellEntryStatus.DISCONNECTED
                        else -> ShellEntryStatus.FAILED
                    }
                    buffer.replace(id, running.copy(status = status))
                    mutableState.update { it.copy(error = result.error) }
                }
                AdbOperationResult.Cancelled -> buffer.replace(id, running.copy(status = ShellEntryStatus.CANCELLED))
            }
            mutableState.update { it.copy(isRunning = false) }
            publish()
            operation = null
        }
    }

    fun cancel() {
        operation?.cancel()
        operation = null
        mutableState.update { it.copy(isRunning = false) }
    }

    private fun publish() = mutableState.update {
        it.copy(entries = buffer.snapshot(), outputWasDropped = buffer.droppedOldestOutput)
    }

    private fun isHighRisk(command: String): Boolean = HIGH_RISK.containsMatchIn(command)

    override fun onCleared() {
        operation?.cancel()
        buffer.clear()
        super.onCleared()
    }

    private companion object {
        val HIGH_RISK = Regex("(?i)(^|[;&|\\s])(rm\\s+-rf|reboot|factory_reset|wipe|dd\\s+if=|mkfs)(\\s|$)")
    }
}
