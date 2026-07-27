package com.sheen.adb.feature.shell

import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellStateMachineTest {
    @Test
    fun `special keys stay local before submission and become semantic remote input while active`() {
        val local = terminalState(
            phase = ShellTerminalPhase.LOCAL_EDITING,
            draft = "echo local",
            history = listOf("first", "second"),
        )
        val localInputs = listOf(
            TerminalInput.Escape,
            TerminalInput.Tab,
            TerminalInput.ArrowUp,
            TerminalInput.ArrowDown,
        )
        localInputs.forEach { input ->
            assertTrue(
                ShellTerminalStateMachine.handleInput(local, input).remoteInputs.isEmpty(),
                "$input must not reach the child stream during local editing",
            )
        }
        assertEquals(
            ShellTerminalStateMachine.handleInput(local, TerminalInput.ArrowUp).state.draft,
            "second",
        )

        val remote = local.copy(phase = ShellTerminalPhase.REMOTE_ACTIVE)
        localInputs.forEach { input ->
            val transition = ShellTerminalStateMachine.handleInput(remote, input)
            assertEquals(transition.remoteInputs, listOf(input))
            assertFalse(transition.remoteInputs.single() is TerminalInput.Text)
        }
    }

    @Test
    fun `ctrl and alt modify one next input and never expose encoded bytes`() {
        val remote = terminalState(phase = ShellTerminalPhase.REMOTE_ACTIVE)
        val withCtrl = ShellTerminalStateMachine.toggleModifier(remote, TerminalModifier.CTRL)
        val ctrlInput = ShellTerminalStateMachine.handleInput(withCtrl, TerminalInput.Text("c"))

        assertEquals(
            ctrlInput.remoteInputs,
            listOf(TerminalInput.Modified(TerminalModifier.CTRL, TerminalInput.Text("c"))),
        )
        assertNull(ctrlInput.state.modifier)
        assertEquals(
            ShellTerminalStateMachine.handleInput(ctrlInput.state, TerminalInput.Text("c")).remoteInputs,
            listOf(TerminalInput.Text("c")),
        )

        val combined = ShellTerminalStateMachine.toggleModifier(
            ShellTerminalStateMachine.toggleModifier(remote, TerminalModifier.CTRL),
            TerminalModifier.ALT,
        )
        val ctrlAltInput = ShellTerminalStateMachine.handleInput(combined, TerminalInput.Text("z"))
        assertEquals(
            ctrlAltInput.remoteInputs,
            listOf(TerminalInput.Modified(TerminalModifier.CTRL_ALT, TerminalInput.Text("z"))),
        )
        assertNull(ctrlAltInput.state.modifier)

        val localAlt = ShellTerminalStateMachine.toggleModifier(
            remote.copy(phase = ShellTerminalPhase.LOCAL_EDITING),
            TerminalModifier.ALT,
        )
        val consumedLocally = ShellTerminalStateMachine.handleInput(localAlt, TerminalInput.Tab)
        assertTrue(consumedLocally.remoteInputs.isEmpty())
        assertNull(consumedLocally.state.modifier)
    }

    @Test
    fun `page leave closes child ownership and clears volatile input while retaining same session memory`() {
        val active = terminalState(
            phase = ShellTerminalPhase.REMOTE_ACTIVE,
            draft = "pending",
            history = listOf("kept-command"),
            records = listOf(ShellTerminalRecord.output(3, "kept-output")),
            modifier = TerminalModifier.ALT,
            pendingRiskCommand = "reboot",
        )

        val hidden = ShellTerminalStateMachine.pageHidden(active)

        assertTrue(hidden.closeChildStream)
        assertEquals(hidden.state.phase, ShellTerminalPhase.CLOSED)
        assertEquals(hidden.state.draft, "")
        assertNull(hidden.state.modifier)
        assertNull(hidden.state.pendingRiskCommand)
        assertEquals(hidden.state.history, active.history)
        assertEquals(hidden.state.records, active.records)
        assertEquals(hidden.state.sessionId, "session-a")
    }

    @Test
    fun `returning in the same session opens a new generation and appends a separator`() {
        val hidden = ShellTerminalStateMachine.pageHidden(
            terminalState(
                streamGeneration = 3,
                history = listOf("kept-command"),
                records = listOf(ShellTerminalRecord.output(3, "kept-output")),
            ),
        ).state

        val reopened = ShellTerminalStateMachine.streamOpened(
            current = hidden,
            sessionId = "session-a",
            streamGeneration = 4,
        )

        assertEquals(reopened.sessionId, "session-a")
        assertEquals(reopened.streamGeneration, 4)
        assertEquals(reopened.phase, ShellTerminalPhase.LOCAL_EDITING)
        assertEquals(reopened.history, listOf("kept-command"))
        assertEquals(reopened.records.first(), hidden.records.first())
        assertEquals(reopened.records.last().kind, ShellTerminalRecordKind.SESSION_SEPARATOR)
        assertEquals(reopened.records.last().streamGeneration, 4)
    }

    @Test
    fun `session switch clears records history draft modifier and old generation`() {
        val old = terminalState(
            streamGeneration = 7,
            phase = ShellTerminalPhase.REMOTE_ACTIVE,
            draft = "old draft",
            history = listOf("old command"),
            records = listOf(ShellTerminalRecord.output(7, "old output")),
            modifier = TerminalModifier.CTRL,
            pendingRiskCommand = "old risk",
        )

        val changed = ShellTerminalStateMachine.sessionChanged(old, "session-b")

        assertEquals(changed.sessionId, "session-b")
        assertEquals(changed.streamGeneration, 0)
        assertEquals(changed.phase, ShellTerminalPhase.CLOSED)
        assertEquals(changed.draft, "")
        assertTrue(changed.history.isEmpty())
        assertTrue(changed.records.isEmpty())
        assertNull(changed.modifier)
        assertNull(changed.pendingRiskCommand)
    }

    private fun terminalState(
        streamGeneration: Long = 1,
        phase: ShellTerminalPhase = ShellTerminalPhase.LOCAL_EDITING,
        draft: String = "",
        history: List<String> = emptyList(),
        records: List<ShellTerminalRecord> = emptyList(),
        modifier: TerminalModifier? = null,
        pendingRiskCommand: String? = null,
    ) = ShellTerminalState(
        sessionId = "session-a",
        streamGeneration = streamGeneration,
        phase = phase,
        draft = draft,
        history = history,
        records = records,
        modifier = modifier,
        pendingRiskCommand = pendingRiskCommand,
    )
}
