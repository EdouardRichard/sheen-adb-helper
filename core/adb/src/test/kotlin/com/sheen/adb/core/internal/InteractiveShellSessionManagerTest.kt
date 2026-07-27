package com.sheen.adb.core.internal

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class InteractiveShellSessionManagerTest {
    private val models = source("../AdbModels.kt")
    private val sessionPort = source("../AdbSessionManager.kt")
    private val manager = source("DefaultAdbSessionManager.kt")
    private val adapter = source("AdbProtocolAdapter.kt")
    private val exceptionMapper = source("AdbExceptionMapper.kt")

    @Test
    fun `interactive shell handle is bound to expected Session and one stream generation`() {
        assertContainsAll(
            models,
            "InteractiveShellSession",
            "expectedSessionId",
            "streamGeneration",
            "TerminalInput",
            "TerminalOutputEvent",
            "InteractiveShellCloseReason",
        )
        assertContainsAll(
            sessionPort,
            "openInteractiveShell(",
            "expectedSessionId",
            "InteractiveShellSession",
        )
        assertContainsAll(
            manager,
            "activeInteractiveShell",
            "streamGeneration",
            "expectedSessionId",
            "INTERACTIVE_SHELL_BUSY",
        )
        assertTrue(
            manager.contains("compareAndSet") || manager.contains("Mutex"),
            "rapid opens must atomically grant at most one child stream",
        )
    }

    @Test
    fun `timeout cancellation unsupported and uncertain outcomes are structured`() {
        assertContainsAll(
            models,
            "InteractiveShellResult",
            "TimedOut",
            "Cancelled",
            "Unsupported",
            "OutcomeUnknown",
            "Disconnected",
        )
        assertContainsAll(
            exceptionMapper,
            "INTERACTIVE_SHELL_UNSUPPORTED",
            "INTERACTIVE_SHELL_TIMEOUT",
            "INTERACTIVE_SHELL_OUTCOME_UNKNOWN",
        )
        assertFalse(
            manager.contains("GlobalScope"),
            "interactive child stream must remain owned by the active Session",
        )
    }

    @Test
    fun `stale output is rejected by Session and generation before publication`() {
        assertContainsAll(
            manager,
            "event.expectedSessionId",
            "event.streamGeneration",
            "currentSessionId",
            "activeInteractiveShell",
        )
        assertTrue(
            Regex(
                """event\.expectedSessionId\s*!=\s*currentSessionId|""" +
                    """event\.streamGeneration\s*!=\s*activeInteractiveShell""",
            ).containsMatchIn(manager),
            "old Session or old generation output must be discarded",
        )
    }

    @Test
    fun `interactive shell open is serialized with one shot command teardown on the shared session`() {
        val openBlock = manager.substringAfter("override suspend fun openInteractiveShell")
            .substringBefore("\n    private suspend fun interactiveShellWrite")

        assertTrue(
            Regex("interactiveShellMutex\\.withLock\\s*\\{[\\s\\S]{0,160}mutex\\.withLock\\s*\\{")
                .containsMatchIn(openBlock),
            "Opening an interactive child must wait for an in-flight one-shot command to close its child stream.",
        )
        assertFalse(
            Regex("mutex\\.withLock\\s*\\{[\\s\\S]{0,160}interactiveShellMutex\\.withLock")
                .containsMatchIn(openBlock),
            "The lock order must remain interactive-child lock then shared Session command lock.",
        )
    }

    @Test
    fun `every terminal path closes only child resources deterministically`() {
        assertContainsAll(
            adapter,
            "ProtocolInteractiveShell",
            "closeInput",
            "closeOutput",
        )
        assertContainsAll(
            manager,
            "NonCancellable",
            "finally",
            "closeInteractiveShellChild",
            "InteractiveShellCloseReason",
        )
        assertFalse(
            manager.contains("closeMainSessionForInteractiveShell"),
            "leaving Shell must not close the main ADB Session",
        )
        assertFalse(
            manager.contains("disconnectForInteractiveShell"),
            "child cleanup must not be implemented as a main Session disconnect",
        )
    }

    private fun source(relativePath: String): String =
        File("src/main/kotlin/com/sheen/adb/core/internal", relativePath)
            .takeIf(File::isFile)
            ?.readText()
            .orEmpty()

    private fun assertContainsAll(source: String, vararg tokens: String) {
        tokens.forEach { token ->
            assertTrue(source.contains(token), "Missing interactive Shell contract token: $token")
        }
    }
}
