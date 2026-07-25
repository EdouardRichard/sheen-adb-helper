package com.sheen.adb.feature.overview

import com.sheen.adb.core.QuickActionKind
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionPresentationTest {
    private val reducer = QuickActionPresentationReducer()

    @Test
    fun `one running quick action blocks every other quick action`() {
        val running = reducer.reduce(
            QuickActionUiState.Idle,
            QuickActionPresentationEvent.StartRequested(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-a",
            ),
        )
        assertTrue(running is QuickActionUiState.Running)

        val blockedRecording = reducer.reduce(
            running,
            QuickActionPresentationEvent.StartRequested(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
            ),
        )
        val blockedReboot = reducer.reduce(
            running,
            QuickActionPresentationEvent.RebootRequested(sessionId = "session-a"),
        )

        assertEquals(blockedRecording, running)
        assertEquals(blockedReboot, running)
        assertEquals((running as QuickActionUiState.Running).kind, QuickActionKind.SCREENSHOT)
    }

    @Test
    fun `reboot remains confirmation only until explicit confirmation`() {
        val confirming = reducer.reduce(
            QuickActionUiState.Idle,
            QuickActionPresentationEvent.RebootRequested(sessionId = "session-a"),
        )

        assertEquals(
            confirming,
            QuickActionUiState.Confirming(
                kind = QuickActionKind.REBOOT,
                sessionId = "session-a",
            ),
        )

        val dismissed = reducer.reduce(
            confirming,
            QuickActionPresentationEvent.RebootConfirmationDismissed(sessionId = "session-a"),
        )
        assertEquals(
            dismissed,
            QuickActionUiState.Cancelled(
                kind = QuickActionKind.REBOOT,
                sessionId = "session-a",
                reason = "confirmation-dismissed",
            ),
        )

        val confirmed = reducer.reduce(
            confirming,
            QuickActionPresentationEvent.RebootConfirmed(sessionId = "session-a"),
        )
        assertTrue(confirmed is QuickActionUiState.Running)
        assertEquals((confirmed as QuickActionUiState.Running).kind, QuickActionKind.REBOOT)
        assertEquals(confirmed.sessionId, "session-a")
    }

    @Test
    fun `progress and artifact readiness update only the owning Session`() {
        val running = QuickActionUiState.Running(
            kind = QuickActionKind.SCREEN_RECORD,
            sessionId = "session-a",
            progress = 0f,
            startedAtEpochMillis = 100L,
            elapsedMillis = 0L,
            bytesWritten = 0L,
        )
        val staleProgress = reducer.reduce(
            running,
            QuickActionPresentationEvent.Progressed(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-old",
                progress = 0.5f,
                elapsedMillis = 1_000L,
                bytesWritten = 4_096L,
            ),
        )
        assertEquals(staleProgress, running)

        val progressed = reducer.reduce(
            running,
            QuickActionPresentationEvent.Progressed(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
                progress = 0.5f,
                elapsedMillis = 1_000L,
                bytesWritten = 4_096L,
            ),
        )
        assertEquals((progressed as QuickActionUiState.Running).progress, 0.5f)
        assertEquals(progressed.elapsedMillis, 1_000L)
        assertEquals(progressed.bytesWritten, 4_096L)

        val artifact = QuickActionArtifactRef("artifact-a")
        val awaitingExport = reducer.reduce(
            progressed,
            QuickActionPresentationEvent.ArtifactReady(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
                artifact = artifact,
            ),
        )
        assertEquals(
            awaitingExport,
            QuickActionUiState.AwaitingExport(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
                artifact = artifact,
            ),
        )
    }

    @Test
    fun `recording stop is idempotent and remains eligible for artifact export`() {
        val running = QuickActionUiState.Running(
            kind = QuickActionKind.SCREEN_RECORD,
            sessionId = "session-a",
            elapsedMillis = 2_000L,
            bytesWritten = 1_024L,
        )

        val stopping = reducer.reduce(
            running,
            QuickActionPresentationEvent.ScreenRecordStopRequested("session-a"),
        )
        assertEquals(
            stopping,
            running.copy(isStopping = true),
        )
        assertEquals(
            reducer.reduce(
                stopping,
                QuickActionPresentationEvent.ScreenRecordStopRequested("session-a"),
            ),
            stopping,
        )
        assertEquals(
            reducer.reduce(
                stopping,
                QuickActionPresentationEvent.ScreenRecordStopRequested("session-old"),
            ),
            stopping,
        )

        val progressed = reducer.reduce(
            stopping,
            QuickActionPresentationEvent.Progressed(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
                progress = 0.2f,
                elapsedMillis = 2_500L,
                bytesWritten = 2_048L,
            ),
        )
        val artifact = QuickActionArtifactRef("artifact-recording")
        val awaiting = reducer.reduce(
            progressed,
            QuickActionPresentationEvent.ArtifactReady(
                kind = QuickActionKind.SCREEN_RECORD,
                sessionId = "session-a",
                artifact = artifact,
            ),
        )

        assertEquals(
            awaiting,
            QuickActionUiState.AwaitingExport(
                QuickActionKind.SCREEN_RECORD,
                "session-a",
                artifact,
            ),
        )
    }

    @Test
    fun `export has explicit awaiting exporting and succeeded states`() {
        val artifact = QuickActionArtifactRef("artifact-export")
        val awaiting = QuickActionUiState.AwaitingExport(
            kind = QuickActionKind.SCREENSHOT,
            sessionId = "session-a",
            artifact = artifact,
        )

        val exporting = reducer.reduce(
            awaiting,
            QuickActionPresentationEvent.ExportStarted(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-a",
            ),
        )
        assertEquals(
            exporting,
            QuickActionUiState.Exporting(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-a",
                artifact = artifact,
            ),
        )

        val succeeded = reducer.reduce(
            exporting,
            QuickActionPresentationEvent.ExportSucceeded(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-a",
                destinationName = "controlled-screen.png",
            ),
        )
        assertEquals(
            succeeded,
            QuickActionUiState.Succeeded(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-a",
                destinationName = "controlled-screen.png",
            ),
        )
    }

    @Test
    fun `cancel failure and result unknown remain different terminal outcomes`() {
        val running = QuickActionUiState.Running(
            kind = QuickActionKind.REBOOT,
            sessionId = "session-a",
            progress = 0f,
            startedAtEpochMillis = 100L,
            elapsedMillis = 0L,
            bytesWritten = 0L,
        )

        val cancelled = reducer.reduce(
            running,
            QuickActionPresentationEvent.Cancelled(
                kind = QuickActionKind.REBOOT,
                sessionId = "session-a",
                reason = "user-cancelled",
            ),
        )
        val failed = reducer.reduce(
            running,
            QuickActionPresentationEvent.Failed(
                kind = QuickActionKind.REBOOT,
                sessionId = "session-a",
                reason = "request-rejected",
                technicalCode = "QUICK_ACTION_REJECTED",
            ),
        )
        val unknown = reducer.reduce(
            running,
            QuickActionPresentationEvent.ResultUnknown(
                kind = QuickActionKind.REBOOT,
                sessionId = "session-a",
                reason = "device-disconnected-after-request",
            ),
        )

        assertTrue(cancelled is QuickActionUiState.Cancelled)
        assertTrue(failed is QuickActionUiState.Failed)
        assertTrue(unknown is QuickActionUiState.ResultUnknown)
        assertFalse(unknown is QuickActionUiState.Succeeded)
        assertEquals((failed as QuickActionUiState.Failed).technicalCode, "QUICK_ACTION_REJECTED")
    }

    @Test
    fun `old Session state and artifact are never delivered to replacement Session`() {
        val oldArtifact = QuickActionArtifactRef("artifact-old")
        val awaitingOldExport = QuickActionUiState.AwaitingExport(
            kind = QuickActionKind.SCREENSHOT,
            sessionId = "session-old",
            artifact = oldArtifact,
        )

        val invalidated = reducer.reduce(
            awaitingOldExport,
            QuickActionPresentationEvent.SessionChanged(activeSessionId = "session-new"),
        )
        assertEquals(
            invalidated,
            QuickActionUiState.Cancelled(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-old",
                reason = "stale-session",
            ),
        )

        val lateOldExport = reducer.reduce(
            invalidated,
            QuickActionPresentationEvent.ExportSucceeded(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-old",
                destinationName = "must-not-be-delivered.png",
            ),
        )
        assertEquals(lateOldExport, invalidated)

        val runningNew = reducer.reduce(
            invalidated,
            QuickActionPresentationEvent.StartRequested(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-new",
            ),
        )
        val lateOldArtifact = reducer.reduce(
            runningNew,
            QuickActionPresentationEvent.ArtifactReady(
                kind = QuickActionKind.SCREENSHOT,
                sessionId = "session-old",
                artifact = oldArtifact,
            ),
        )

        assertEquals(lateOldArtifact, runningNew)
        assertTrue(runningNew is QuickActionUiState.Running)
        assertEquals((runningNew as QuickActionUiState.Running).sessionId, "session-new")
        assertFalse(lateOldArtifact.toString().contains("artifact-old"))
    }
}
