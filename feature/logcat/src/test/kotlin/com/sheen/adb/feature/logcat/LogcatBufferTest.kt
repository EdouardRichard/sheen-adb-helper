package com.sheen.adb.feature.logcat

import com.sheen.adb.core.StructuredLogcatKind
import com.sheen.adb.core.StructuredLogcatLevel
import com.sheen.adb.core.StructuredLogcatRecord
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatBufferTest {
    @Test
    fun `raw window remains complete while visible records derive from filters`() {
        val window = structuredWindow()
        listOf(
            record(1, StructuredLogcatLevel.DEBUG, "fixture hidden"),
            record(2, StructuredLogcatLevel.INFO, "fixture visible"),
            record(3, StructuredLogcatLevel.ERROR, "another hidden"),
        ).forEach(window::add)

        window.updateTextFilter("VISIBLE")

        assertEquals(window.rawSnapshot().map { it.sequence }, listOf(1L, 2L, 3L))
        assertEquals(window.visibleSnapshot().map { it.sequence }, listOf(2L))
        window.updateTextFilter("")
        assertEquals(window.rawSnapshot().map { it.sequence }, listOf(1L, 2L, 3L))
    }

    @Test
    fun `all debug info and error apply approved minimum severity thresholds`() {
        val window = structuredWindow()
        val levels = listOf(
            StructuredLogcatLevel.VERBOSE,
            StructuredLogcatLevel.DEBUG,
            StructuredLogcatLevel.INFO,
            StructuredLogcatLevel.WARN,
            StructuredLogcatLevel.ERROR,
            StructuredLogcatLevel.FATAL,
        )
        levels.forEachIndexed { index, level -> window.add(record(index.toLong(), level, "fixture-$index")) }

        val expected = mapOf(
            LogcatDisplayLevel.ALL to levels,
            LogcatDisplayLevel.DEBUG to levels.drop(1),
            LogcatDisplayLevel.INFO to levels.drop(2),
            LogcatDisplayLevel.ERROR to levels.takeLast(2),
        )
        expected.forEach { (threshold, expectedLevels) ->
            window.updateDisplayLevel(threshold)
            assertEquals(window.visibleSnapshot().map { it.level }, expectedLevels, "threshold=$threshold")
        }
    }

    @Test
    fun `fatal is visible in every approved display level`() {
        val window = structuredWindow()
        window.add(record(1, StructuredLogcatLevel.FATAL, "fatal fixture"))

        LogcatDisplayLevel.entries.forEach { threshold ->
            window.updateDisplayLevel(threshold)
            assertEquals(window.visibleSnapshot().map { it.sequence }, listOf(1L), "threshold=$threshold")
        }
    }

    @Test
    fun `text filter matches the complete raw line immediately and case insensitively`() {
        val window = structuredWindow()
        window.add(record(1, StructuredLogcatLevel.INFO, "prefix full-line marker suffix"))
        window.add(record(2, StructuredLogcatLevel.INFO, "other fixture"))

        window.updateTextFilter("FULL-LINE MARKER")

        assertEquals(window.visibleSnapshot().map { it.sequence }, listOf(1L))
        assertEquals(window.rawSnapshot().size, 2)
    }

    @Test
    fun `clear removes both raw capture window and derived visible projection`() {
        val window = structuredWindow()
        window.add(record(1, StructuredLogcatLevel.INFO, "fixture"))
        window.updateTextFilter("fixture")

        window.clear()

        assertTrue(window.rawSnapshot().isEmpty())
        assertTrue(window.visibleSnapshot().isEmpty())
    }

    @Test
    fun `raw capture window enforces exact ten MiB utf8 boundary`() {
        val oneMiBRecord = "x".repeat((1024 * 1024) - 1)
        val window = LogcatAnalysisWindow(
            sessionId = "session-a",
            processGeneration = 7,
            maxLines = 100,
            maxBytes = 10 * 1024 * 1024,
            visibleLimit = 100,
        )
        repeat(11) { index ->
            window.add(record(index.toLong(), StructuredLogcatLevel.INFO, oneMiBRecord))
        }

        assertEquals(window.rawSnapshot().size, 10)
        assertEquals(window.rawSnapshot().first().sequence, 1L)
        assertTrue(window.droppedOldest)
    }

    @Test
    fun `enforces line limit by dropping oldest`() {
        val buffer = LogcatBuffer(maxLines = 3, maxBytes = 100)
        repeat(5) { buffer.add("line-$it") }
        assertEquals(buffer.snapshot(), listOf("line-2", "line-3", "line-4"))
        assertTrue(buffer.droppedOldest)
    }

    @Test
    fun `enforces utf8 byte limit`() {
        val buffer = LogcatBuffer(maxLines = 100, maxBytes = 8)
        buffer.add("中文中文")
        assertEquals(buffer.snapshot().single(), "中文")
        assertTrue(buffer.droppedOldest)
    }

    @Test
    fun `window always exposes latest one hundred matching lines`() {
        val window = LogcatWindow(LogcatBuffer(maxLines = 1_000, maxBytes = 100_000))
        window.updateKeyword("match")
        repeat(250) { index ->
            window.add(if (index % 2 == 0) "match-$index" else "other-$index")
        }
        assertEquals(window.snapshot().size, 100)
        assertEquals(window.snapshot().first(), "match-50")
        assertEquals(window.snapshot().last(), "match-248")
    }

    @Test
    fun `pause freezes presentation resume catches up and clear keeps receiving`() {
        val window = LogcatWindow(LogcatBuffer(maxLines = 1_000, maxBytes = 100_000))
        window.add("before")
        window.pause()
        window.add("during-1")
        window.add("during-2")
        assertEquals(window.snapshot(), listOf("before"))

        window.resume()
        assertEquals(window.snapshot(), listOf("before", "during-1", "during-2"))
        window.clear()
        assertTrue(window.snapshot().isEmpty())
        window.add("after-clear")
        assertEquals(window.snapshot(), listOf("after-clear"))
    }

    @Test
    fun `stop disconnect and session switch states stop capture and clear stale lines`() {
        val active = LogcatUiState(
            isConnected = true,
            sessionId = "old",
            isCapturing = true,
            isPaused = true,
            visibleLines = listOf("stale"),
            error = com.sheen.adb.core.AdbError.Timeout(com.sheen.adb.core.AdbOperationStage.LOGCAT),
        )
        assertTrue(active.stopped().let { !it.isCapturing && !it.isPaused })

        val switched = active.resetForSession(isConnected = true, sessionId = "new")
        assertTrue(switched.visibleLines.isEmpty())
        assertEquals(switched.sessionId, "new")
        assertTrue(!switched.isCapturing && !switched.isPaused)

        val disconnected = active.resetForSession(isConnected = false, sessionId = null)
        assertTrue(!disconnected.isConnected && disconnected.visibleLines.isEmpty())
    }

    private fun structuredWindow() = LogcatAnalysisWindow(
        sessionId = "session-a",
        processGeneration = 7,
        maxLines = 100,
        maxBytes = 10 * 1024 * 1024,
        visibleLimit = 100,
    )

    private fun record(
        sequence: Long,
        level: StructuredLogcatLevel,
        rawText: String,
    ) = StructuredLogcatRecord(
        sessionId = "session-a",
        snapshotGeneration = 7,
        sequence = sequence,
        rawText = rawText,
        kind = StructuredLogcatKind.PARSED,
        level = level,
        message = rawText,
    )
}
