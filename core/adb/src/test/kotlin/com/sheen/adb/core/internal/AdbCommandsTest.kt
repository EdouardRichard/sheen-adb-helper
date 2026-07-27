package com.sheen.adb.core.internal

import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AdbCommandsTest {
    @Test
    fun `process counter command uses braced positional parameters`() {
        assertTrue(AdbCommands.PROCESS_COUNTERS.contains("\${20}"))
        assertTrue(AdbCommands.PROCESS_COUNTERS.contains("\${12}"))
        assertTrue(AdbCommands.PROCESS_COUNTERS.contains("\${13}"))
        assertFalse(AdbCommands.PROCESS_COUNTERS.contains("\$20"))
        assertFalse(AdbCommands.PROCESS_COUNTERS.contains("\$12"))
        assertFalse(AdbCommands.PROCESS_COUNTERS.contains("\$13"))
    }
}
