package com.sheen.adb.core

import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellInputInterpreterTest {
    @Test
    fun `ordinary shell input remains byte for byte exact`() {
        val input = "  echo first\necho second  \n"

        val plan = ShellInputInterpreter.plan(input) as ShellInputPlan.Exact

        assertEquals(plan.originalCommand, input)
    }

    @Test
    fun `computer side wrapper requires confirmation and exposes device command`() {
        val input = "  adb   shell \n echo ready  \n"

        val plan = ShellInputInterpreter.plan(input) as ShellInputPlan.ConfirmHostWrapper

        assertEquals(plan.originalCommand, input)
        assertEquals(plan.remoteCommand, "echo ready  \n")
    }

    @Test
    fun `similar or embedded text is never rewritten`() {
        assertTrue(ShellInputInterpreter.plan("echo adb shell") is ShellInputPlan.Exact)
        assertTrue(ShellInputInterpreter.plan("adbx shell echo ready") is ShellInputPlan.Exact)
        assertTrue(ShellInputInterpreter.plan("adb shellx echo ready") is ShellInputPlan.Exact)
    }

    @Test
    fun `interactive host wrapper is detected without inventing a command`() {
        val plan = ShellInputInterpreter.plan("adb shell  ") as ShellInputPlan.ConfirmHostWrapper

        assertNull(plan.remoteCommand)
    }
}
