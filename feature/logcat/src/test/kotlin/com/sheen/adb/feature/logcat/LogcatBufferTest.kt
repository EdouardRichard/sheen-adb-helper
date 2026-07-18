package com.sheen.adb.feature.logcat

import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatBufferTest {
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
}
