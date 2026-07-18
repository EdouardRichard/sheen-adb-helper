package com.sheen.adb.feature.shell

import com.sheen.adb.core.ShellInputPlan
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
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
}
