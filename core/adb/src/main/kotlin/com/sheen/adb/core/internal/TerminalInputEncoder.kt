package com.sheen.adb.core.internal

import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier

internal object TerminalInputEncoder {
    private val escape = byteArrayOf(0x1B)

    fun encode(input: TerminalInput): ByteArray = when (input) {
        is TerminalInput.Text -> input.value.encodeToByteArray()
        TerminalInput.Submit -> byteArrayOf(0x0A)
        TerminalInput.Escape -> escape.copyOf()
        TerminalInput.Tab -> byteArrayOf(0x09)
        TerminalInput.ArrowUp -> byteArrayOf(0x1B, 0x5B, 0x41)
        TerminalInput.ArrowDown -> byteArrayOf(0x1B, 0x5B, 0x42)
        is TerminalInput.Modified -> when (input.modifier) {
            TerminalModifier.CTRL -> encodeCtrl(input.input)
            TerminalModifier.ALT -> escape + encode(input.input)
            TerminalModifier.CTRL_ALT -> escape + encodeCtrl(input.input)
        }
    }

    private fun encodeCtrl(input: TerminalInput): ByteArray {
        val text = input as? TerminalInput.Text
            ?: throw IllegalArgumentException("Ctrl requires one ASCII text input")
        require(text.value.length == 1 && text.value[0].code in 0x40..0x7F) {
            "Ctrl requires one ASCII character in the control range"
        }
        return byteArrayOf((text.value[0].code and 0x1F).toByte())
    }
}
