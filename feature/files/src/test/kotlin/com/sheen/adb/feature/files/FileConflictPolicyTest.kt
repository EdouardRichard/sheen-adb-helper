package com.sheen.adb.feature.files

import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FileConflictPolicyTest {
    @Test
    fun `conflict starts with cancel and waits without changing original target`() {
        val task = FileTask("task", "session", FileTaskKind.UPLOAD, FileTaskStatus.Preparing)
        val started = FileTaskLifecycle.start(FilesUiState(sessionId = "session"), task)

        val waiting = FileConflictReducer.awaitDecision(started, "task", "report.txt")

        assertEquals(waiting.activeTask?.status, FileTaskStatus.AwaitingConflict)
        assertEquals(waiting.activeTask?.conflictPolicy, FileConflictPolicy.CANCEL)
        assertEquals(waiting.pendingConflict?.displayName, "report.txt")
        assertTrue(waiting.pendingConflict?.originalMustRemainUnchanged == true)
    }

    @Test
    fun `overwrite and auto rename require explicit decisions`() {
        val waiting = waitingState()

        val overwrite = FileConflictReducer.resolve(waiting, "task", FileConflictPolicy.OVERWRITE)
        assertEquals(overwrite.activeTask?.conflictPolicy, FileConflictPolicy.OVERWRITE)
        assertEquals(overwrite.activeTask?.status, FileTaskStatus.Preparing)
        assertNull(overwrite.pendingConflict)

        val renamed = FileConflictReducer.resolve(waiting, "task", FileConflictPolicy.AUTO_RENAME)
        assertEquals(renamed.activeTask?.conflictPolicy, FileConflictPolicy.AUTO_RENAME)
        assertEquals(renamed.activeTask?.status, FileTaskStatus.Preparing)
        assertNull(renamed.pendingConflict)
    }

    @Test
    fun `dismissed or stale conflict defaults to cancel without touching another task`() {
        val waiting = waitingState()

        val dismissed = FileConflictReducer.resolve(waiting, "task", null)
        assertEquals(dismissed.activeTask?.status, FileTaskStatus.Cancelled)
        assertEquals(dismissed.activeTask?.conflictPolicy, FileConflictPolicy.CANCEL)
        assertNull(dismissed.pendingConflict)

        val stale = FileConflictReducer.resolve(waiting, "other", FileConflictPolicy.OVERWRITE)
        assertEquals(stale, waiting)
    }

    private fun waitingState(): FilesUiState {
        val task = FileTask("task", "session", FileTaskKind.DOWNLOAD, FileTaskStatus.Preparing)
        return FileConflictReducer.awaitDecision(
            FileTaskLifecycle.start(FilesUiState(sessionId = "session"), task),
            "task",
            "report.txt",
        )
    }
}
