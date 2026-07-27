package com.sheen.adb.feature.shell

import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier

enum class ShellTerminalPhase {
    CLOSED,
    LOCAL_EDITING,
    REMOTE_ACTIVE,
}

enum class ShellTerminalRecordKind {
    OUTPUT,
    SESSION_SEPARATOR,
}

data class ShellTerminalRecord(
    val streamGeneration: Long,
    val kind: ShellTerminalRecordKind,
    val text: String,
) {
    companion object {
        fun output(
            streamGeneration: Long,
            text: String,
        ): ShellTerminalRecord = ShellTerminalRecord(
            streamGeneration = streamGeneration,
            kind = ShellTerminalRecordKind.OUTPUT,
            text = text,
        )

        fun sessionSeparator(streamGeneration: Long): ShellTerminalRecord = ShellTerminalRecord(
            streamGeneration = streamGeneration,
            kind = ShellTerminalRecordKind.SESSION_SEPARATOR,
            text = "",
        )
    }
}

data class ShellTerminalState(
    val sessionId: String,
    val streamGeneration: Long = 0,
    val phase: ShellTerminalPhase = ShellTerminalPhase.CLOSED,
    val draft: String = "",
    val history: List<String> = emptyList(),
    val historyCursor: Int? = null,
    val records: List<ShellTerminalRecord> = emptyList(),
    val modifier: TerminalModifier? = null,
    val pendingRiskCommand: String? = null,
)

data class ShellTerminalTransition(
    val state: ShellTerminalState,
    val remoteInputs: List<TerminalInput> = emptyList(),
    val closeChildStream: Boolean = false,
)

object ShellTerminalStateMachine {
    fun handleInput(
        current: ShellTerminalState,
        input: TerminalInput,
    ): ShellTerminalTransition = when (current.phase) {
        ShellTerminalPhase.REMOTE_ACTIVE -> remoteInput(current, input)
        ShellTerminalPhase.LOCAL_EDITING,
        ShellTerminalPhase.CLOSED,
        -> localInput(current, input)
    }

    fun toggleModifier(
        current: ShellTerminalState,
        requested: TerminalModifier,
    ): ShellTerminalState = current.copy(
        modifier = toggle(current.modifier, requested),
    )

    fun pageHidden(current: ShellTerminalState): ShellTerminalTransition = ShellTerminalTransition(
        state = current.copy(
            phase = ShellTerminalPhase.CLOSED,
            draft = "",
            historyCursor = null,
            modifier = null,
            pendingRiskCommand = null,
        ),
        closeChildStream = current.phase != ShellTerminalPhase.CLOSED,
    )

    fun streamOpened(
        current: ShellTerminalState,
        sessionId: String,
        streamGeneration: Long,
    ): ShellTerminalState {
        require(sessionId.isNotBlank())
        require(streamGeneration > 0)
        val retained = if (current.sessionId == sessionId) {
            current
        } else {
            sessionChanged(current, sessionId)
        }
        return retained.copy(
            sessionId = sessionId,
            streamGeneration = streamGeneration,
            phase = ShellTerminalPhase.LOCAL_EDITING,
            draft = "",
            historyCursor = null,
            modifier = null,
            pendingRiskCommand = null,
            records = retained.records + ShellTerminalRecord.sessionSeparator(streamGeneration),
        )
    }

    fun sessionChanged(
        current: ShellTerminalState,
        sessionId: String,
    ): ShellTerminalState {
        require(sessionId.isNotBlank())
        if (current.sessionId == sessionId) return current
        return ShellTerminalState(sessionId = sessionId)
    }

    private fun remoteInput(
        current: ShellTerminalState,
        input: TerminalInput,
    ): ShellTerminalTransition {
        val modifier = current.modifier
        val semanticInput = if (modifier != null && input !is TerminalInput.Submit) {
            TerminalInput.Modified(modifier, input)
        } else {
            input
        }
        return ShellTerminalTransition(
            state = current.copy(modifier = null),
            remoteInputs = listOf(semanticInput),
        )
    }

    private fun localInput(
        current: ShellTerminalState,
        input: TerminalInput,
    ): ShellTerminalTransition {
        val next = when (input) {
            is TerminalInput.Text -> current.copy(
                draft = current.draft + input.value,
                historyCursor = null,
            )
            TerminalInput.Tab -> current.copy(
                draft = current.draft + '\t',
                historyCursor = null,
            )
            TerminalInput.ArrowUp -> previousHistory(current)
            TerminalInput.ArrowDown -> nextHistory(current)
            TerminalInput.Escape -> current.copy(historyCursor = null)
            TerminalInput.Submit -> current
            is TerminalInput.Modified -> localInput(current, input.input).state
        }
        return ShellTerminalTransition(state = next.copy(modifier = null))
    }

    private fun previousHistory(current: ShellTerminalState): ShellTerminalState {
        if (current.history.isEmpty()) return current
        val index = (current.historyCursor ?: current.history.size)
            .minus(1)
            .coerceAtLeast(0)
        return current.copy(
            draft = current.history[index],
            historyCursor = index,
        )
    }

    private fun nextHistory(current: ShellTerminalState): ShellTerminalState {
        val cursor = current.historyCursor ?: return current
        val index = cursor + 1
        return if (index >= current.history.size) {
            current.copy(draft = "", historyCursor = null)
        } else {
            current.copy(draft = current.history[index], historyCursor = index)
        }
    }

    private fun toggle(
        current: TerminalModifier?,
        requested: TerminalModifier,
    ): TerminalModifier? {
        val currentMask = current.mask()
        val requestedMask = requested.mask()
        return (currentMask xor requestedMask).modifier()
    }

    private fun TerminalModifier?.mask(): Int = when (this) {
        null -> 0
        TerminalModifier.CTRL -> CTRL_MASK
        TerminalModifier.ALT -> ALT_MASK
        TerminalModifier.CTRL_ALT -> CTRL_MASK or ALT_MASK
    }

    private fun Int.modifier(): TerminalModifier? = when (this) {
        0 -> null
        CTRL_MASK -> TerminalModifier.CTRL
        ALT_MASK -> TerminalModifier.ALT
        else -> TerminalModifier.CTRL_ALT
    }

    private const val CTRL_MASK = 1
    private const val ALT_MASK = 2
}
