package com.sheen.adb.feature.shell

import com.sheen.adb.core.ShellInputPlan
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellCommandPolicyTest {
    @Test
    fun `ordinary execution preserves the exact command`() {
        val command = "  echo ready  \n"

        val request = exactShellExecution(command)

        assertEquals(request.displayedCommand, command)
        assertEquals(request.commandToExecute, command)
        assertEquals(request.dispatchMode, ShellDispatchMode.EXACT)
    }

    @Test
    fun `confirmed host wrapper keeps original for audit and dispatches device command`() {
        val plan = ShellInputPlan.ConfirmHostWrapper("adb shell echo ready", "echo ready")

        val request = confirmedHostWrapperExecution(plan)!!

        assertEquals(request.displayedCommand, plan.originalCommand)
        assertEquals(request.commandToExecute, plan.remoteCommand)
        assertEquals(request.dispatchMode, ShellDispatchMode.CONFIRMED_HOST_WRAPPER_REMOVAL)
    }

    @Test
    fun `host wrapper without device command cannot be transformed`() {
        val plan = ShellInputPlan.ConfirmHostWrapper("adb shell", null)

        assertNull(confirmedHostWrapperExecution(plan))
    }

    @Test
    fun `editing a local draft never creates remote input`() {
        val transition = ShellCommandPolicy.editDraft(
            current = ShellDraftState(),
            value = "echo local only",
        )

        assertEquals(transition.state.draft, "echo local only")
        assertTrue(transition.remoteInputs.isEmpty())
    }

    @Test
    fun `high risk submission sends no bytes or newline before confirmation`() {
        val draft = ShellDraftState(draft = "reboot")

        val transition = ShellCommandPolicy.requestSubmission(draft)

        assertEquals(transition.state.draft, "reboot")
        assertEquals(transition.state.pendingRiskCommand, "reboot")
        assertTrue(transition.remoteInputs.isEmpty())
    }

    @Test
    fun `cancelling risk confirmation preserves the complete draft`() {
        val pending = ShellCommandPolicy.requestSubmission(
            ShellDraftState(draft = "rm -rf /data/local/tmp/fixture"),
        ).state

        val cancelled = ShellCommandPolicy.cancelRiskConfirmation(pending)

        assertEquals(cancelled.state.draft, "rm -rf /data/local/tmp/fixture")
        assertNull(cancelled.state.pendingRiskCommand)
        assertTrue(cancelled.remoteInputs.isEmpty())
    }

    @Test
    fun `blank draft is ignored without opening or writing a stream`() {
        val transition = ShellCommandPolicy.requestSubmission(
            ShellDraftState(draft = " \t\n"),
        )

        assertTrue(transition is ShellDraftTransition.NoOp)
        assertTrue(transition.remoteInputs.isEmpty())
    }
}
