package com.sheen.adb.feature.shell

import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellPresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/shell/ShellScreen.kt")

    @Test
    fun `screen follows html utility terminal and auto scroll structure`() {
        val source = screen.readText()
        listOf(
            "ShellUtilityBar",
            "ShellTerminalPanel",
            "ShellKeyboardAccessory",
            "clear",
            "filter",
            "autoScroll",
            "SheenColors.terminalBackground",
            "LazyColumn",
            "FontFamily.Monospace",
            "padding(16.dp)",
            "ShellAutoScrollPolicy",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing shell HTML structure $token")
        }
        assertFalse(
            source.contains("verticalScroll(rememberScrollState())"),
            "bounded terminal records must be lazy rather than eagerly composed",
        )
    }

    @Test
    fun `keyboard accessory preserves semantic key order touch target and ime only action`() {
        val source = screen.readText()
        val orderedTokens = listOf(
            "TerminalInput.Escape",
            "TerminalInput.Tab",
            "TerminalModifier.CTRL",
            "TerminalModifier.ALT",
            "TerminalInput.ArrowUp",
            "TerminalInput.ArrowDown",
            "SheenIcons.KeyboardReturn",
        )
        var previous = -1
        orderedTokens.forEach { token ->
            val index = source.indexOf(token)
            assertTrue(index > previous, "missing or out-of-order shell accessory token $token")
            previous = index
        }
        listOf(
            "height(36.dp)",
            "width(44.dp)",
            "minimumTouchTarget",
            "requestIme",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing shell keyboard requirement $token")
        }
        assertFalse(
            source.contains("widthIn(min = 44.dp)"),
            "a minimum-only accessory width can consume the whole row",
        )
        assertFalse(
            Regex("KeyboardReturn[\\s\\S]{0,240}(submit|execute)\\s*\\(")
                .containsMatchIn(source),
            "keyboard-return may focus/open IME but must not submit an empty command",
        )
    }

    @Test
    fun `screen represents every required shell state with one design grammar`() {
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
        ).forEach { token ->
            assertTrue(source.contains(token), "missing shell state token $token")
        }
    }

    @Test
    fun `hostile command and output projections are bounded control safe and bidi isolated`() {
        val rawCommand = "echo\u0000\u202E\n" + "argument".repeat(80)
        val rawOutput = "first\u0085\tline\nsecond\u202D\uFFFD" + "output".repeat(200)
        val commandIdentity = rawCommand.toCharArray().copyOf()
        val outputIdentity = rawOutput.toCharArray().copyOf()

        val commandDisplay = SafeVerbatimText.render(
            rawCommand,
            SafeVerbatimPolicy.SingleLine(maxCodePoints = 128),
        )
        val outputDisplay = SafeVerbatimText.render(
            rawOutput,
            SafeVerbatimPolicy.MultiLine(maxCodePoints = 512),
        )

        assertEquals(rawCommand.toCharArray().toList(), commandIdentity.toList())
        assertEquals(rawOutput.toCharArray().toList(), outputIdentity.toList())
        assertTrue(commandDisplay.truncated)
        assertTrue(outputDisplay.truncated)
        assertTrue(commandDisplay.replacementCount >= 2)
        assertTrue(outputDisplay.replacementCount >= 2)
        assertFalse(commandDisplay.display.contains('\n'))
        assertTrue(outputDisplay.display.contains('\n'))
        assertTrue(commandDisplay.display.startsWith("\u2066") && commandDisplay.display.endsWith("\u2069"))
        assertTrue(outputDisplay.display.startsWith("\u2066") && outputDisplay.display.endsWith("\u2069"))
        assertTrue(outputDisplay.display.contains('\uFFFD'), "undecodable marker must remain safely visible")
    }

    @Test
    fun `display projection cannot flow back into draft or terminal submission`() {
        val source = screen.readText()
        listOf(
            "SafeVerbatimText",
            "SafeVerbatimPolicy.SingleLine",
            "SafeVerbatimPolicy.MultiLine",
            "maxCodePoints",
            ".display",
            "state.draft",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing shell verbatim separation token $token")
        }
        assertFalse(
            Regex("(onDraftChange|updateDraft|sendSubmittedCommand)\\s*\\([^)]*\\.display")
                .containsMatchIn(source),
            "presentation-safe text must never replace the raw draft/command identity",
        )
        assertFalse(
            Regex("(onDraftChange|updateDraft|sendSubmittedCommand)\\s*\\([^)]*SafeVerbatimText")
                .containsMatchIn(source),
            "sanitized projection is display-only and must not become executable input",
        )
    }
}
