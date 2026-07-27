package com.sheen.adb.feature.logcat

import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatPresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt")

    @Test
    fun `screen follows html utility bar and log surface structure`() {
        val source = screen.readText()
        listOf(
            "height(48.dp)",
            "OutlinedTextField",
            "listOf(\"all\", \"debug\", \"info\", \"error\")",
            "SheenIcons.Delete",
            "SheenIcons.Download",
            "LazyColumn",
            "Color.Black",
            "horizontalScroll",
            "padding(16.dp)",
            "FontFamily.Monospace",
            "fontSize = 12.sp",
            "lineHeight = 18.sp",
        ).forEach { token -> assertTrue(source.contains(token), "missing Logcat HTML token $token") }
        assertFalse(source.contains("\"fatal\""), "Fatal must not become a fifth toolbar button")
    }

    @Test
    fun `screen represents all collection save and responsive states`() {
        val source = screen.readText()
        listOf(
            "NeverStarted",
            "Loading",
            "Content",
            "Empty",
            "Error",
            "Cancelled",
            "Disconnected",
            "Unsupported",
            "OutcomeUnknown",
            "Progress",
            "LimitTime",
            "LimitBytes",
            "ProcessSecondaryPane",
            "BoxWithConstraints",
            "MaterialTheme.colorScheme.surfaceContainerLow",
        ).forEach { token -> assertTrue(source.contains(token), "missing Logcat state token $token") }
    }

    @Test
    fun `hostile log text is bounded for display without changing raw save identity`() {
        val raw = "line\u0000\u202E\n" + "payload".repeat(2_000)
        val saveIdentity = raw.toCharArray().copyOf()

        val display = SafeVerbatimText.render(raw, SafeVerbatimPolicy.SingleLine(512))

        assertEquals(raw.toCharArray().toList(), saveIdentity.toList())
        assertTrue(display.truncated)
        assertTrue(display.replacementCount >= 2)
        assertTrue(display.display.startsWith("\u2066") && display.display.endsWith("\u2069"))
        assertFalse(display.display.contains('\n'))

        val source = screen.readText()
        assertTrue(source.contains("SafeVerbatimText"))
        assertTrue(source.contains("rawWindowSnapshot"))
        assertFalse(source.contains("visibleRecords.joinToString"), "screen must not export filtered rows")
    }

    @Test
    fun `filter text stays visible and log viewport owns one shared horizontal scroll`() {
        val source = screen.readText()
        val utilityBar = source.substringAfter("private fun LogcatUtilityBar")
            .substringBefore("@Composable\nprivate fun LevelButton")
        val logSurface = source.substringAfter("private fun LogSurface")
            .substringBefore("@Composable\nprivate fun LogcatStatePanel")

        assertTrue(utilityBar.contains("LocalContentColor.current"))
        assertTrue(utilityBar.contains("cursorColor"))
        assertTrue(utilityBar.contains("widthIn(min = 128.dp)"))
        assertTrue(logSurface.contains("val horizontalScrollState = rememberScrollState()"))
        assertEquals(
            logSurface.windowed("rememberScrollState()".length).count { it == "rememberScrollState()" },
            1,
            "the log viewport must create exactly one horizontal scroll state",
        )
        assertTrue(logSurface.contains("horizontalScroll(horizontalScrollState)"))
        assertFalse(
            Regex("itemsIndexed[\\s\\S]*rememberScrollState\\(\\)").containsMatchIn(logSurface),
            "rows must not own independent horizontal scroll positions",
        )
    }
}
