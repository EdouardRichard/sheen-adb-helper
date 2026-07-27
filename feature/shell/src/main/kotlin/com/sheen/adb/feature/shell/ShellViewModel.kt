package com.sheen.adb.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.InteractiveShellCloseReason
import com.sheen.adb.core.InteractiveShellResult
import com.sheen.adb.core.InteractiveShellSession
import com.sheen.adb.core.ShellInputInterpreter
import com.sheen.adb.core.ShellInputPlan
import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier
import com.sheen.adb.core.TerminalOutputEvent
import com.sheen.adb.core.TerminalOutputKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShellStreamStatus {
    DISCONNECTED,
    OPENING,
    READY,
    REMOTE_ACTIVE,
    CLOSED,
    TIMED_OUT,
    CANCELLED,
    UNSUPPORTED,
    OUTCOME_UNKNOWN,
    ERROR,
}

data class ShellUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val terminal: ShellTerminalState = ShellTerminalState(sessionId = ""),
    val filter: String = "",
    val autoScroll: Boolean = true,
    val outputGeneration: Long = 0,
    val streamStatus: ShellStreamStatus = ShellStreamStatus.DISCONNECTED,
    val streamResult: InteractiveShellResult? = null,
    val timeoutSeconds: Int = 30,
    val outputWasDropped: Boolean = false,
    val showRiskNotice: Boolean = true,
    val pendingHostWrapper: ShellInputPlan.ConfirmHostWrapper? = null,
    val pendingRiskExecution: ShellExecutionRequest? = null,
    val error: AdbError? = null,
) {
    val commandInput: String get() = terminal.draft
    val draft: String get() = terminal.draft
    val isRunning: Boolean get() =
        streamStatus == ShellStreamStatus.OPENING ||
            terminal.phase == ShellTerminalPhase.REMOTE_ACTIVE
    val records: List<ShellTerminalRecord> get() = terminal.records
    val visibleRecords: List<ShellTerminalRecord>
        get() {
            val needle = filter.trim()
            return if (needle.isEmpty()) records else records.filter {
                it.text.contains(needle, ignoreCase = true)
            }
        }
    val entries: List<ShellEntry>
        get() = records.mapIndexed { index, record ->
            ShellEntry(
                sequence = index.toLong(),
                command = if (record.kind == ShellTerminalRecordKind.SESSION_SEPARATOR) "" else record.text,
                stdout = if (record.kind == ShellTerminalRecordKind.OUTPUT) record.text else "",
                status = ShellEntryStatus.SUCCEEDED,
            )
        }
}

data class ShellExecutionRequest(
    val displayedCommand: String,
    val commandToExecute: String,
    val dispatchMode: ShellDispatchMode,
)

data class ShellDraftState(
    val draft: String = "",
    val pendingRiskCommand: String? = null,
)

sealed class ShellDraftTransition(
    open val state: ShellDraftState,
    open val remoteInputs: List<TerminalInput> = emptyList(),
    open val commandToSubmit: String? = null,
) {
    data class Changed(
        override val state: ShellDraftState,
        override val remoteInputs: List<TerminalInput> = emptyList(),
        override val commandToSubmit: String? = null,
    ) : ShellDraftTransition(state, remoteInputs, commandToSubmit)

    data class NoOp(
        override val state: ShellDraftState,
    ) : ShellDraftTransition(state)
}

object ShellCommandPolicy {
    fun editDraft(current: ShellDraftState, value: String): ShellDraftTransition =
        ShellDraftTransition.Changed(current.copy(draft = value, pendingRiskCommand = null))

    fun requestSubmission(current: ShellDraftState): ShellDraftTransition {
        if (current.draft.isBlank()) return ShellDraftTransition.NoOp(current)
        return if (isHighRisk(current.draft)) {
            ShellDraftTransition.Changed(current.copy(pendingRiskCommand = current.draft))
        } else {
            ShellDraftTransition.Changed(current, commandToSubmit = current.draft)
        }
    }

    fun cancelRiskConfirmation(current: ShellDraftState): ShellDraftTransition =
        ShellDraftTransition.Changed(current.copy(pendingRiskCommand = null))

    fun isHighRisk(command: String): Boolean = HIGH_RISK.containsMatchIn(command)

    private val HIGH_RISK =
        Regex("(?i)(^|[;&|\\s])(rm\\s+-rf|reboot|factory_reset|wipe|dd\\s+if=|mkfs)(\\s|$)")
}

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

class ShellViewModel(
    private val manager: AdbSessionManager,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val executionScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = mutableState.asStateFlow()

    private var pageVisible = false
    private var appForeground = false
    private var streamEpoch = 0L
    private var stream: InteractiveShellSession? = null
    private var openJob: Job? = null
    private var outputJob: Job? = null
    private var inputJob: Job? = null

    init {
        executionScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                val previousSessionId = mutableState.value.sessionId
                if (connected?.sessionId != previousSessionId) {
                    closeChild(
                        if (connected == null) {
                            InteractiveShellCloseReason.DISCONNECTED
                        } else {
                            InteractiveShellCloseReason.SESSION_CHANGED
                        },
                    )
                    val terminal = if (connected == null) {
                        ShellTerminalState(sessionId = "")
                    } else {
                        ShellTerminalStateMachine.sessionChanged(
                            mutableState.value.terminal,
                            connected.sessionId,
                        )
                    }
                    mutableState.value = ShellUiState(
                        isConnected = connected != null,
                        sessionId = connected?.sessionId,
                        terminal = terminal,
                        showRiskNotice = mutableState.value.showRiskNotice,
                        streamStatus = if (connected == null) {
                            ShellStreamStatus.DISCONNECTED
                        } else {
                            ShellStreamStatus.CLOSED
                        },
                    )
                } else {
                    mutableState.update { it.copy(isConnected = connected != null) }
                }
                updateStreamLifecycle()
            }
        }
    }

    fun setForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        if (!foreground) closeChild(InteractiveShellCloseReason.APP_BACKGROUNDED)
        updateStreamLifecycle()
    }

    fun onPageVisible(visible: Boolean) {
        if (pageVisible == visible) return
        pageVisible = visible
        if (!visible) closeChild(InteractiveShellCloseReason.PAGE_HIDDEN)
        updateStreamLifecycle()
    }

    fun updateCommand(value: String) {
        mutableState.update { current ->
            current.copy(
                terminal = current.terminal.copy(draft = value, pendingRiskCommand = null),
                pendingRiskExecution = null,
                error = null,
            )
        }
    }

    fun updateFilter(value: String) = mutableState.update { it.copy(filter = value.take(MAX_FILTER_LENGTH)) }

    fun setAutoScroll(enabled: Boolean) = mutableState.update { it.copy(autoScroll = enabled) }

    fun setTimeout(seconds: Int) = mutableState.update { it.copy(timeoutSeconds = seconds.coerceIn(1, 300)) }

    fun dismissRiskNotice() = mutableState.update { it.copy(showRiskNotice = false) }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun clear() = mutableState.update {
        it.copy(
            terminal = it.terminal.copy(records = emptyList()),
            outputGeneration = it.outputGeneration + 1,
            outputWasDropped = false,
        )
    }

    fun execute() {
        val current = mutableState.value
        val command = current.terminal.draft
        if (command.isBlank() ||
            current.terminal.phase == ShellTerminalPhase.REMOTE_ACTIVE ||
            !current.isConnected
        ) {
            return
        }
        when (val plan = ShellInputInterpreter.plan(command)) {
            is ShellInputPlan.Exact -> requestExecution(exactShellExecution(plan.originalCommand))
            is ShellInputPlan.ConfirmHostWrapper -> mutableState.update {
                it.copy(pendingHostWrapper = plan)
            }
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
        mutableState.update {
            it.copy(
                pendingRiskExecution = null,
                terminal = it.terminal.copy(pendingRiskCommand = null),
            )
        }
        submitNow(request)
    }

    fun dismissRisk() = mutableState.update {
        it.copy(
            pendingRiskExecution = null,
            terminal = it.terminal.copy(pendingRiskCommand = null),
        )
    }

    fun toggleModifier(modifier: TerminalModifier) = mutableState.update {
        it.copy(terminal = ShellTerminalStateMachine.toggleModifier(it.terminal, modifier))
    }

    fun handleTerminalInput(input: TerminalInput) {
        if (input == TerminalInput.Submit) {
            execute()
            return
        }
        val transition = ShellTerminalStateMachine.handleInput(mutableState.value.terminal, input)
        mutableState.update { it.copy(terminal = transition.state) }
        transition.remoteInputs.forEach(::sendLiveInput)
    }

    fun cancel() {
        closeChild(InteractiveShellCloseReason.CANCELLED)
        mutableState.update { it.copy(streamStatus = ShellStreamStatus.CANCELLED) }
    }

    private fun requestExecution(request: ShellExecutionRequest) {
        if (ShellCommandPolicy.isHighRisk(request.commandToExecute)) {
            mutableState.update {
                it.copy(
                    pendingRiskExecution = request,
                    terminal = it.terminal.copy(pendingRiskCommand = request.displayedCommand),
                )
            }
        } else {
            submitNow(request)
        }
    }

    private fun submitNow(request: ShellExecutionRequest) {
        val owned = stream ?: return
        if (inputJob?.isActive == true) return
        val expectedSession = mutableState.value.sessionId ?: return
        val expectedGeneration = owned.streamGeneration
        inputJob = executionScope.launch {
            val result = owned.sendSubmittedCommand(request.commandToExecute)
            if (!matches(expectedSession, expectedGeneration)) return@launch
            if (result == InteractiveShellResult.Accepted) {
                mutableState.update { current ->
                    val terminal = current.terminal
                    current.copy(
                        terminal = terminal.copy(
                            phase = ShellTerminalPhase.REMOTE_ACTIVE,
                            draft = "",
                            history = (terminal.history + request.displayedCommand)
                                .takeLast(MAX_HISTORY),
                            historyCursor = null,
                            modifier = null,
                            pendingRiskCommand = null,
                            records = boundedRecords(
                                terminal.records +
                                    ShellTerminalRecord.output(
                                        expectedGeneration,
                                        "$ ${request.displayedCommand}",
                                    ),
                            ),
                        ),
                        streamStatus = ShellStreamStatus.REMOTE_ACTIVE,
                        streamResult = result,
                        outputGeneration = current.outputGeneration + 1,
                    )
                }
            } else {
                publishResult(result)
            }
            inputJob = null
        }
    }

    private fun sendLiveInput(input: TerminalInput) {
        val owned = stream ?: return
        val expectedSession = mutableState.value.sessionId ?: return
        val expectedGeneration = owned.streamGeneration
        executionScope.launch {
            val result = owned.sendTerminalInput(input)
            if (matches(expectedSession, expectedGeneration) && result != InteractiveShellResult.Accepted) {
                publishResult(result)
            }
        }
    }

    private fun updateStreamLifecycle() {
        if (!pageVisible || !appForeground || !mutableState.value.isConnected) return
        if (stream != null || openJob?.isActive == true) return
        val expectedSessionId = mutableState.value.sessionId ?: return
        val epoch = ++streamEpoch
        mutableState.update { it.copy(streamStatus = ShellStreamStatus.OPENING, streamResult = null) }
        openJob = executionScope.launch {
            when (val opened = manager.openInteractiveShell(expectedSessionId)) {
                is AdbOperationResult.Success -> {
                    if (!shouldOwn(expectedSessionId, epoch)) {
                        opened.value.close(InteractiveShellCloseReason.SESSION_CHANGED)
                        return@launch
                    }
                    stream = opened.value
                    mutableState.update { current ->
                        current.copy(
                            terminal = ShellTerminalStateMachine.streamOpened(
                                current = current.terminal,
                                sessionId = expectedSessionId,
                                streamGeneration = opened.value.streamGeneration,
                            ),
                            streamStatus = ShellStreamStatus.READY,
                        )
                    }
                    collectOutput(opened.value, expectedSessionId, epoch)
                }
                is AdbOperationResult.Failure -> mutableState.update {
                    it.copy(streamStatus = ShellStreamStatus.ERROR, error = opened.error)
                }
                AdbOperationResult.Cancelled -> mutableState.update {
                    it.copy(streamStatus = ShellStreamStatus.CANCELLED)
                }
            }
            openJob = null
        }
    }

    private fun collectOutput(
        owned: InteractiveShellSession,
        expectedSessionId: String,
        epoch: Long,
    ) {
        outputJob?.cancel()
        outputJob = executionScope.launch {
            owned.outputEvents.collect { event ->
                if (!shouldOwn(expectedSessionId, epoch) ||
                    event.expectedSessionId != expectedSessionId ||
                    event.streamGeneration != owned.streamGeneration
                ) {
                    return@collect
                }
                acceptOutput(event)
            }
        }
    }

    private fun acceptOutput(event: TerminalOutputEvent) {
        mutableState.update { current ->
            when (event.kind) {
                TerminalOutputKind.STDOUT,
                TerminalOutputKind.STDERR,
                -> current.copy(
                    terminal = current.terminal.copy(
                        records = boundedRecords(
                            current.terminal.records +
                                ShellTerminalRecord.output(event.streamGeneration, event.text),
                        ),
                    ),
                    outputWasDropped = current.outputWasDropped ||
                        current.terminal.records.size >= MAX_RECORDS,
                    outputGeneration = current.outputGeneration + 1,
                )
                TerminalOutputKind.REMOTE_ACTIVE -> current.copy(
                    terminal = current.terminal.copy(phase = ShellTerminalPhase.REMOTE_ACTIVE),
                    streamStatus = ShellStreamStatus.REMOTE_ACTIVE,
                )
                TerminalOutputKind.REMOTE_READY -> current.copy(
                    terminal = current.terminal.copy(phase = ShellTerminalPhase.LOCAL_EDITING),
                    streamStatus = ShellStreamStatus.READY,
                )
                TerminalOutputKind.OUTPUT_CLOSED -> current.copy(
                    terminal = ShellTerminalStateMachine.pageHidden(current.terminal).state,
                    streamStatus = ShellStreamStatus.CLOSED,
                )
            }
        }
    }

    private fun publishResult(result: InteractiveShellResult) = mutableState.update {
        it.copy(
            streamResult = result,
            streamStatus = when (result) {
                InteractiveShellResult.Accepted -> it.streamStatus
                InteractiveShellResult.Closed -> ShellStreamStatus.CLOSED
                is InteractiveShellResult.TimedOut -> ShellStreamStatus.TIMED_OUT
                is InteractiveShellResult.Cancelled -> ShellStreamStatus.CANCELLED
                is InteractiveShellResult.Unsupported -> ShellStreamStatus.UNSUPPORTED
                is InteractiveShellResult.OutcomeUnknown -> ShellStreamStatus.OUTCOME_UNKNOWN
                is InteractiveShellResult.Disconnected -> ShellStreamStatus.DISCONNECTED
                is InteractiveShellResult.Failed -> ShellStreamStatus.ERROR
            },
        )
    }

    private fun closeChild(reason: InteractiveShellCloseReason) {
        streamEpoch += 1
        openJob?.cancel()
        outputJob?.cancel()
        inputJob?.cancel()
        openJob = null
        outputJob = null
        inputJob = null
        val owned = stream
        stream = null
        if (owned != null) executionScope.launch { owned.close(reason) }
        mutableState.update { current ->
            current.copy(
                terminal = ShellTerminalStateMachine.pageHidden(current.terminal).state,
                pendingHostWrapper = null,
                pendingRiskExecution = null,
                streamStatus = if (current.isConnected) {
                    ShellStreamStatus.CLOSED
                } else {
                    ShellStreamStatus.DISCONNECTED
                },
            )
        }
    }

    private fun shouldOwn(expectedSessionId: String, epoch: Long): Boolean =
        pageVisible &&
            appForeground &&
            mutableState.value.sessionId == expectedSessionId &&
            streamEpoch == epoch

    private fun matches(expectedSessionId: String, generation: Long): Boolean =
        mutableState.value.sessionId == expectedSessionId &&
            stream?.streamGeneration == generation

    override fun onCleared() {
        closeChild(InteractiveShellCloseReason.CANCELLED)
        super.onCleared()
    }

    private companion object {
        const val MAX_FILTER_LENGTH = 255
        const val MAX_HISTORY = 200
        const val MAX_RECORDS = 2_000
        const val MAX_RECORD_BYTES = 10 * 1024 * 1024

        fun boundedRecords(records: List<ShellTerminalRecord>): List<ShellTerminalRecord> {
            val boundedCount = records.takeLast(MAX_RECORDS)
            var bytes = 0
            val kept = ArrayDeque<ShellTerminalRecord>()
            for (record in boundedCount.asReversed()) {
                val size = record.text.encodeToByteArray().size
                if (kept.isNotEmpty() && bytes + size > MAX_RECORD_BYTES) break
                kept.addFirst(
                    if (size <= MAX_RECORD_BYTES) {
                        record
                    } else {
                        record.copy(text = record.text.takeLast(MAX_RECORD_BYTES / 4))
                    },
                )
                bytes += size.coerceAtMost(MAX_RECORD_BYTES)
            }
            return kept.toList()
        }
    }
}
