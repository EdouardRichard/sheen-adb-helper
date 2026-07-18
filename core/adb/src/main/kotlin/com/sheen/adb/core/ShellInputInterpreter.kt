package com.sheen.adb.core

sealed interface ShellInputPlan {
    val originalCommand: String

    data class Exact(
        override val originalCommand: String,
    ) : ShellInputPlan

    data class ConfirmHostWrapper(
        override val originalCommand: String,
        val remoteCommand: String?,
    ) : ShellInputPlan
}

/**
 * Recognizes the computer-side `adb shell` wrapper without silently changing user input.
 * Feature code must ask for explicit confirmation before dispatching [ConfirmHostWrapper.remoteCommand].
 */
object ShellInputInterpreter {
    fun plan(input: String): ShellInputPlan {
        var index = input.indexOfFirst { !it.isWhitespace() }
        if (index < 0 || !input.startsWithToken("adb", index)) return ShellInputPlan.Exact(input)
        index += "adb".length
        val shellStart = input.nextTokenStart(index) ?: return ShellInputPlan.Exact(input)
        if (!input.startsWithToken("shell", shellStart)) return ShellInputPlan.Exact(input)
        val afterShell = shellStart + "shell".length
        val commandStart = input.nextTokenStart(afterShell)
        return ShellInputPlan.ConfirmHostWrapper(
            originalCommand = input,
            remoteCommand = commandStart?.let(input::substring),
        )
    }

    private fun String.startsWithToken(token: String, start: Int): Boolean {
        if (!startsWith(token, start)) return false
        val end = start + token.length
        return end == length || this[end].isWhitespace()
    }

    private fun String.nextTokenStart(after: Int): Int? {
        if (after >= length || !this[after].isWhitespace()) return null
        var index = after
        while (index < length && this[index].isWhitespace()) index++
        return index.takeIf { it < length }
    }
}
