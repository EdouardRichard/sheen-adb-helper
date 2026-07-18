package com.sheen.adb.feature.logcat

import java.nio.charset.StandardCharsets

class LogcatBuffer(
    private val maxLines: Int = 10_000,
    private val maxBytes: Int = 4 * 1024 * 1024,
) {
    private val lines = ArrayDeque<String>()
    private var bytes = 0
    var droppedOldest: Boolean = false
        private set

    fun add(line: String) {
        var accepted = line
        val lineBytes = byteSize(accepted)
        if (lineBytes > maxBytes) {
            accepted = tailUtf8(accepted, (maxBytes - 1).coerceAtLeast(0))
            droppedOldest = true
        }
        lines.addLast(accepted)
        bytes += byteSize(accepted)
        while (lines.size > maxLines || bytes > maxBytes) {
            bytes -= byteSize(lines.removeFirst())
            droppedOldest = true
        }
    }

    fun snapshot(): List<String> = lines.toList()

    fun clear() {
        lines.clear()
        bytes = 0
        droppedOldest = false
    }

    private fun byteSize(value: String) = value.toByteArray(StandardCharsets.UTF_8).size + 1

    private fun tailUtf8(value: String, limit: Int): String {
        var start = value.length
        var used = 0
        while (start > 0) {
            val previous = value.offsetByCodePoints(start, -1)
            val count = value.substring(previous, start).toByteArray(StandardCharsets.UTF_8).size
            if (used + count > limit) break
            used += count
            start = previous
        }
        return value.substring(start)
    }
}

internal class LogcatWindow(
    private val buffer: LogcatBuffer = LogcatBuffer(),
    private val visibleLimit: Int = 100,
) {
    private var keyword: String = ""
    private var paused = false
    private val visibleLines = ArrayDeque<String>()

    init {
        require(visibleLimit > 0)
    }

    fun add(line: String) {
        buffer.add(line)
        if (!paused && matches(line)) {
            visibleLines.addLast(line)
            while (visibleLines.size > visibleLimit) visibleLines.removeFirst()
        }
    }

    fun updateKeyword(value: String) {
        keyword = value
        refresh()
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        refresh()
    }

    fun clear() {
        buffer.clear()
        visibleLines.clear()
    }

    fun reset() {
        clear()
        keyword = ""
        paused = false
    }

    fun snapshot(): List<String> = visibleLines.toList()

    val droppedOldest: Boolean
        get() = buffer.droppedOldest

    private fun refresh() {
        val refreshed = buffer.snapshot()
            .asSequence()
            .filter(::matches)
            .toList()
            .takeLast(visibleLimit)
        visibleLines.clear()
        visibleLines.addAll(refreshed)
    }

    private fun matches(line: String): Boolean =
        keyword.trim().let { it.isEmpty() || line.contains(it, ignoreCase = true) }
}
