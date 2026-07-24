package com.sheen.adb.feature.logcat

import com.sheen.adb.core.StructuredLogcatLevel
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import java.nio.file.Files
import java.nio.file.Path

class LogcatPresentationTest {
    @Test
    fun `screen exposes extraction controls without analysis or pause controls`() {
        val source = String(
            Files.readAllBytes(Path.of("src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt")),
        )
        assertTrue(source.contains("Logcat 日志提取"))
        assertTrue(source.contains("本版本不提供日志筛选、进程关联或分析"))
        assertFalse(source.contains("结果等级筛选"))
        assertFalse(source.contains("清除全部筛选"))
        assertFalse(source.contains("暂停"))
    }
    @Test
    fun `presentation exposes extraction without analysis controls`() {
        assertTrue(LogcatPresentationPolicy.controls.isEmpty())
        assertTrue(LogcatPresentationPolicy.filterSummary(LogcatAnalysisFilter()).contains("提取"))
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

        assertTrue(summary.contains("提取"))
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
