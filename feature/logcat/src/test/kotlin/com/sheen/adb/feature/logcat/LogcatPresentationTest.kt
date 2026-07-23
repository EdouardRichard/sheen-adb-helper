package com.sheen.adb.feature.logcat

import com.sheen.adb.core.StructuredLogcatLevel
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatPresentationTest {
    @Test
    fun `presentation exposes only the six approved basic analysis controls`() {
        assertEquals(
            LogcatPresentationPolicy.controls,
            listOf(
                LogcatFilterControl.LEVEL,
                LogcatFilterControl.TAG,
                LogcatFilterControl.KEYWORD,
                LogcatFilterControl.PID,
                LogcatFilterControl.PROCESS,
                LogcatFilterControl.APPLICATION,
            ),
        )
        assertFalse(LogcatPresentationPolicy.labels().any { label ->
            listOf("ANR", "崩溃识别", "CPU", "内存趋势", "线程转储").any { label.contains(it, ignoreCase = true) }
        })
    }

    @Test
    fun `filter summary states AND semantics without echoing sensitive query content`() {
        val filter = LogcatAnalysisFilter(
            levels = setOf(StructuredLogcatLevel.INFO, StructuredLogcatLevel.ERROR),
            tagQuery = "fixture-tag",
            keyword = "synthetic-message",
            pidQuery = "123",
            processQuery = "fixture-process",
            applicationQuery = "com.example.fixture",
        )

        val summary = LogcatPresentationPolicy.filterSummary(filter)

        assertTrue(summary.contains("6"))
        assertTrue(summary.contains("AND"))
        assertFalse(summary.contains("fixture", ignoreCase = true))
        assertFalse(summary.contains("com.example", ignoreCase = true))
    }

    @Test
    fun `status distinguishes disconnected loading empty truncated degraded and stopped`() {
        assertEquals(
            LogcatPresentationPolicy.status(LogcatUiState()),
            LogcatPresentationStatus.DISCONNECTED,
        )
        assertEquals(
            LogcatPresentationPolicy.status(
                LogcatUiState(isConnected = true, isCapturing = true, status = LogcatAnalysisStatus.LOADING_PROCESSES),
            ),
            LogcatPresentationStatus.LOADING,
        )
        assertEquals(
            LogcatPresentationPolicy.status(LogcatUiState(isConnected = true, status = LogcatAnalysisStatus.READY)),
            LogcatPresentationStatus.EMPTY,
        )
        assertEquals(
            LogcatPresentationPolicy.status(LogcatUiState(isConnected = true, droppedOldest = true)),
            LogcatPresentationStatus.TRUNCATED,
        )
        assertEquals(
            LogcatPresentationPolicy.status(LogcatUiState(isConnected = true, parseDegraded = true)),
            LogcatPresentationStatus.PARSE_DEGRADED,
        )
        assertEquals(
            LogcatPresentationPolicy.status(LogcatUiState(isConnected = true, status = LogcatAnalysisStatus.STOPPED)),
            LogcatPresentationStatus.STOPPED,
        )
    }

    @Test
    fun `copy and export presentation uses current visible content and explicit user action`() {
        val state = LogcatUiState(
            isConnected = true,
            visibleLines = listOf("synthetic-visible-1", "synthetic-visible-2"),
        )

        val transfer = LogcatPresentationPolicy.visibleTransfer(state)

        assertTrue(transfer.enabled)
        assertTrue(transfer.requiresExplicitUserAction)
        assertEquals(transfer.text, "synthetic-visible-1\nsynthetic-visible-2")
        assertEquals(transfer.recordCount, 2)
        assertFalse(LogcatPresentationPolicy.visibleTransfer(state.copy(visibleLines = emptyList())).enabled)
    }
}
