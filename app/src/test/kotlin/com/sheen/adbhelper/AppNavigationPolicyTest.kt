package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppNavigationPolicyTest {
    private val policyFile =
        File("src/main/kotlin/com/sheen/adbhelper/AppNavigationPolicy.kt")

    @Test
    fun `policy defines one adjacent direction locked gesture with endpoint clamping`() {
        assertTrue(policyFile.isFile, "AppNavigationPolicy.kt is missing")
        val source = policyFile.takeIf(File::isFile)?.readText().orEmpty()

        listOf(
            "HORIZONTAL_DISTANCE_THRESHOLD_DP",
            "HORIZONTAL_VELOCITY_THRESHOLD_DP_PER_SECOND",
            "HORIZONTAL_DOMINANCE_RATIO",
            "GESTURE",
            "ADJACENT",
            "FIRST_DESTINATION",
            "LAST_DESTINATION",
            "VERTICAL_INTENT",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing gesture policy $token")
        }
        assertFalse(source.contains("HorizontalPager"))
    }

    @Test
    fun `rapid requests converge and rejected navigation emits no lifecycle event`() {
        assertTrue(policyFile.isFile, "AppNavigationPolicy.kt is missing")
        val source = policyFile.takeIf(File::isFile)?.readText().orEmpty()

        listOf(
            "latestAcceptedTarget",
            "supersede",
            "NavigationDecision.Rejected",
            "NavigationDecision.Accepted",
            "navigationLock",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing request convergence contract $token")
        }
        assertTrue(
            Regex("lifecycleEvents[^=]*=\\s*emptyList\\(\\)").containsMatchIn(source),
            "rejected navigation must emit no lifecycle event",
        )
    }

    @Test
    fun `page load gate keeps latest target and releases when loading completes`() {
        val waitingForProcesses = PageLoadNavigationGate.onRequest(
            state = PageLoadNavigationGateState(),
            current = MainDestination.APPLICATIONS,
            target = MainDestination.PROCESSES,
            currentPageLoading = true,
            nowMillis = 1_000L,
        )
        assertEquals(waitingForProcesses.effect, PageLoadNavigationEffect.NONE)
        assertEquals(waitingForProcesses.state.phase, PageLoadNavigationPhase.WAITING)

        val latestWins = PageLoadNavigationGate.onRequest(
            state = waitingForProcesses.state,
            current = MainDestination.APPLICATIONS,
            target = MainDestination.SHELL,
            currentPageLoading = true,
            nowMillis = 1_100L,
        )
        assertEquals(latestWins.state.pendingTarget, MainDestination.SHELL)
        assertEquals(latestWins.state.waitStartedAtMillis, 1_000L)

        val released = PageLoadNavigationGate.onTick(
            state = latestWins.state,
            currentPageLoading = false,
            nowMillis = 1_200L,
        )
        assertEquals(released.effect, PageLoadNavigationEffect.NAVIGATE)
        assertEquals(released.target, MainDestination.SHELL)
        assertEquals(released.state.phase, PageLoadNavigationPhase.IDLE)
    }

    @Test
    fun `page load gate times out through cancellation and bounded cleanup recovery`() {
        val waiting = PageLoadNavigationGate.onRequest(
            state = PageLoadNavigationGateState(),
            current = MainDestination.PROCESSES,
            target = MainDestination.APPLICATIONS,
            currentPageLoading = true,
            nowMillis = 5_000L,
        ).state

        val cancel = PageLoadNavigationGate.onTick(
            state = waiting,
            currentPageLoading = true,
            nowMillis = 5_000L + PAGE_LOAD_WAIT_TIMEOUT_MILLIS,
        )
        assertEquals(cancel.effect, PageLoadNavigationEffect.CANCEL_CURRENT_LOAD)
        assertEquals(cancel.state.phase, PageLoadNavigationPhase.RECOVERING)

        val stillCleaning = PageLoadNavigationGate.onTick(
            state = cancel.state,
            currentPageLoading = false,
            nowMillis = 5_000L + PAGE_LOAD_WAIT_TIMEOUT_MILLIS +
                PAGE_LOAD_CLEANUP_GRACE_MILLIS - 1L,
        )
        assertEquals(stillCleaning.effect, PageLoadNavigationEffect.NONE)

        val recovered = PageLoadNavigationGate.onTick(
            state = cancel.state,
            currentPageLoading = false,
            nowMillis = 5_000L + PAGE_LOAD_WAIT_TIMEOUT_MILLIS +
                PAGE_LOAD_CLEANUP_GRACE_MILLIS,
        )
        assertEquals(recovered.effect, PageLoadNavigationEffect.NAVIGATE)
        assertEquals(recovered.target, MainDestination.APPLICATIONS)

        val forcedRecovery = PageLoadNavigationGate.onTick(
            state = cancel.state,
            currentPageLoading = true,
            nowMillis = 5_000L + PAGE_LOAD_WAIT_TIMEOUT_MILLIS +
                PAGE_LOAD_RECOVERY_TIMEOUT_MILLIS,
        )
        assertEquals(forcedRecovery.effect, PageLoadNavigationEffect.NAVIGATE)
    }

    @Test
    fun `explicit escape requests cancellation before navigation`() {
        val waiting = PageLoadNavigationGate.onRequest(
            state = PageLoadNavigationGateState(),
            current = MainDestination.APPLICATIONS,
            target = MainDestination.PROCESSES,
            currentPageLoading = true,
            nowMillis = 10L,
        ).state

        val escape = PageLoadNavigationGate.cancelAndRecover(waiting, nowMillis = 20L)

        assertEquals(escape.effect, PageLoadNavigationEffect.CANCEL_CURRENT_LOAD)
        assertEquals(escape.state.phase, PageLoadNavigationPhase.RECOVERING)
        assertEquals(escape.state.pendingTarget, MainDestination.PROCESSES)
    }
}
