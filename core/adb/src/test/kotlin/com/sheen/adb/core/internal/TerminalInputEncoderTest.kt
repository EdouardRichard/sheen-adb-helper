package com.sheen.adb.core.internal

import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class TerminalInputEncoderTest {
    @Test
    fun `escape tab and arrows use terminal control sequences`() {
        assertBytes(TerminalInput.Escape, 0x1B)
        assertBytes(TerminalInput.Tab, 0x09)
        assertBytes(TerminalInput.ArrowUp, 0x1B, 0x5B, 0x41)
        assertBytes(TerminalInput.ArrowDown, 0x1B, 0x5B, 0x42)
    }

    @Test
    fun `ctrl modifies exactly one ascii input into its control byte`() {
        assertBytes(
            TerminalInput.Modified(
                modifier = TerminalModifier.CTRL,
                input = TerminalInput.Text("c"),
            ),
            0x03,
        )
        assertBytes(TerminalInput.Text("c"), 0x63)
        assertBytes(
            TerminalInput.Modified(
                modifier = TerminalModifier.CTRL,
                input = TerminalInput.Text("["),
            ),
            0x1B,
        )
    }

    @Test
    fun `alt prefixes exactly one text or special key input with escape`() {
        assertBytes(
            TerminalInput.Modified(
                modifier = TerminalModifier.ALT,
                input = TerminalInput.Text("x"),
            ),
            0x1B,
            0x78,
        )
        assertBytes(
            TerminalInput.Modified(
                modifier = TerminalModifier.ALT,
                input = TerminalInput.ArrowUp,
            ),
            0x1B,
            0x1B,
            0x5B,
            0x41,
        )
        assertBytes(TerminalInput.Text("x"), 0x78)
    }

    @Test
    fun `ctrl alt combination encodes one input without retaining either modifier`() {
        assertBytes(
            TerminalInput.Modified(
                modifier = TerminalModifier.CTRL_ALT,
                input = TerminalInput.Text("z"),
            ),
            0x1B,
            0x1A,
        )
        assertBytes(TerminalInput.Text("z"), 0x7A)
    }

    @Test
    fun `semantic key names never become ordinary commands`() {
        val encodedKeys = listOf(
            "Esc" to TerminalInput.Escape,
            "Tab" to TerminalInput.Tab,
            "ArrowUp" to TerminalInput.ArrowUp,
            "ArrowDown" to TerminalInput.ArrowDown,
        )

        encodedKeys.forEach { (label, input) ->
            val encoded = TerminalInputEncoder.encode(input)
            assertFalse(encoded.contentEquals(label.toByteArray(Charsets.UTF_8)))
            assertFalse(encoded.contentEquals("$label\n".toByteArray(Charsets.UTF_8)))
        }
    }

    private fun assertBytes(
        input: TerminalInput,
        vararg expected: Int,
    ) {
        val encoded = TerminalInputEncoder.encode(input)
        val bytes = expected.map(Int::toByte).toByteArray()
        assertTrue(
            encoded.contentEquals(bytes),
            "expected=${bytes.contentToString()}, actual=${encoded.contentToString()}",
        )
    }
}
