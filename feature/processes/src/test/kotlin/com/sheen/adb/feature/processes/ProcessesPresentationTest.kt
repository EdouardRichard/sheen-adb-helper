package com.sheen.adb.feature.processes

import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ProcessesPresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/processes/ProcessesScreen.kt")

    @Test
    fun `screen follows html single search and process row structure`() {
        val source = screen.readText()
        assertEquals(source.windowed("BasicTextField(".length).count { it == "BasicTextField(" }, 1)
        listOf(
            "height(48.dp)",
            "LazyColumn",
            "contentAlignment = Alignment.CenterStart",
            "SheenIcons.Search",
            "heightIn(min = 72.dp)",
            "SheenIcons.Cpu",
            "SheenIcons.Memory",
            "MetricChip",
            "SheenIcons.Stop",
            "minimumTouchTarget",
        ).forEach { token -> assertTrue(source.contains(token), "missing process HTML token $token") }
        assertFalse(source.contains("OutlinedTextField("), "forced Material field height clips text")
        assertFalse(source.contains("onSearch"), "process filtering must not expose a search action button")
        assertFalse(source.contains("actions::refresh"), "visible process polling replaces a manual refresh button")
    }

    @Test
    fun `screen represents every required process state`() {
        val source = screen.readText()
        listOf(
            "Loading",
            "Content",
            "Empty",
            "Error",
            "Cancelled",
            "Disconnected",
            "Unsupported",
            "OutcomeUnknown",
            "Confirmation",
            "Progress",
            "MaterialTheme.colorScheme.surfaceContainerLow",
            "SheenShapes",
        ).forEach { token -> assertTrue(source.contains(token), "missing process state token $token") }
    }

    @Test
    fun `verified termination result remains visible with the refreshed process list`() {
        val source = screen.readText()

        assertTrue(source.contains("TerminationResultBanner("))
        assertTrue(source.contains("state.terminationResult?.let"))
        assertTrue(source.contains("TERMINATION_SUCCEEDED"))
        assertTrue(source.contains("RESULT_LIVE_ANNOUNCEMENT"))
    }

    @Test
    fun `hostile process identity is bounded and isolated without changing confirmation target`() {
        val rawName = "system\u0000\u202E\n" + "worker".repeat(80)
        val rawPid = "42\u0085"
        val nameIdentity = rawName.toCharArray().copyOf()
        val pidIdentity = rawPid.toCharArray().copyOf()

        val nameDisplay = SafeVerbatimText.render(rawName, SafeVerbatimPolicy.SingleLine(96))
        val pidDisplay = SafeVerbatimText.render(rawPid, SafeVerbatimPolicy.SingleLine(24))

        assertEquals(rawName.toCharArray().toList(), nameIdentity.toList())
        assertEquals(rawPid.toCharArray().toList(), pidIdentity.toList())
        assertTrue(nameDisplay.truncated)
        assertTrue(nameDisplay.replacementCount >= 2)
        assertTrue(nameDisplay.display.startsWith("\u2066") && nameDisplay.display.endsWith("\u2069"))
        assertFalse(nameDisplay.display.contains('\n'))
        assertTrue(pidDisplay.replacementCount >= 1)

        val source = screen.readText()
        assertTrue(source.contains("SafeVerbatimText"))
        assertTrue(source.contains("pending.entry.identity"))
        assertTrue(source.contains("identity.pid"))
    }
}
