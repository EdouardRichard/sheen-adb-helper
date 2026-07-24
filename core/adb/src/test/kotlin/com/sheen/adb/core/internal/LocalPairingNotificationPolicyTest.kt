package com.sheen.adb.core.internal

import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingSubmissionDecision
import com.sheen.adb.core.LocalPairingSubmissionRejection
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.internal.pairing.LocalPairingNotificationPolicy
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

internal class LocalPairingNotificationPolicyTest {
    private val policy = LocalPairingNotificationPolicy()

    @Test
    fun `locked window is private and exposes no input or submit action`() {
        val decision = policy.decide(
            window = liveWindow(),
            nowMillis = 1_000L,
            deviceUnlocked = false,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )

        assertEquals(decision.state, LocalPairingNotificationState.PRIVATE_LOCKED)
        assertFalse(decision.inputActionAvailable)
        assertFalse(decision.submitAllowed)
        assertNull(decision.actionWindowId)
        assertTrue(decision.applicationInputAvailable)
    }

    @Test
    fun `only unlocked live unexpired window exposes an input action`() {
        val decision = policy.decide(
            window = liveWindow(),
            nowMillis = 1_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )

        assertEquals(decision.state, LocalPairingNotificationState.INPUT_READY)
        assertTrue(decision.inputActionAvailable)
        assertTrue(decision.submitAllowed)
        assertEquals(decision.actionWindowId, WINDOW_ID)
        assertTrue(decision.toString().contains("LocalPairingWindowId(redacted)"))
        assertFalse(decision.toString().contains("synthetic-service"))
    }

    @Test
    fun `deadline immediately becomes a terminal notification without an input action`() {
        val decision = policy.decide(
            window = liveWindow(),
            nowMillis = 120_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )

        assertEquals(decision.state, LocalPairingNotificationState.RESULT)
        assertEquals(decision.stopReason, LocalPairingStopReason.DEADLINE_REACHED)
        assertFalse(decision.inputActionAvailable)
        assertFalse(decision.submitAllowed)
        assertNull(decision.actionWindowId)
        assertFalse(decision.applicationInputAvailable)
    }

    @Test
    fun `permission notification and OEM restrictions keep application fallback`() {
        val permissionDenied = policy.decide(
            liveWindow(),
            nowMillis = 1_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.PERMISSION_DENIED,
        )
        val notificationsDisabled = policy.decide(
            liveWindow(),
            nowMillis = 1_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.NOTIFICATIONS_DISABLED,
        )
        val oemUnavailable = policy.decide(
            liveWindow(),
            nowMillis = 1_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.INLINE_INPUT_UNAVAILABLE,
        )

        listOf(permissionDenied, notificationsDisabled, oemUnavailable).forEach {
            assertEquals(it.state, LocalPairingNotificationState.INPUT_UNAVAILABLE)
            assertFalse(it.inputActionAvailable)
            assertTrue(it.applicationInputAvailable)
        }
        assertFalse(permissionDenied.suggestNativeNotificationStyle)
        assertFalse(notificationsDisabled.suggestNativeNotificationStyle)
        assertTrue(oemUnavailable.suggestNativeNotificationStyle)
    }

    @Test
    fun `submission rechecks token deadline unlock service and six ASCII digits`() {
        val validCode = "0".repeat(6).toCharArray()
        val accepted = policy.validateSubmission(
            window = liveWindow(),
            submittedWindowId = WINDOW_ID,
            nowMillis = 1_000L,
            deviceUnlocked = true,
            code = validCode,
        )

        assertTrue(accepted is LocalPairingSubmissionDecision.Accepted)
        val secret = (accepted as LocalPairingSubmissionDecision.Accepted).secret
        assertEquals(secret.toString(), "PairingSecret(redacted)")
        assertFalse(accepted.toString().contains("000000"))
        secret.clear()

        assertRejected(LocalPairingSubmissionRejection.TOKEN_MISMATCH) { code ->
            policy.validateSubmission(liveWindow(), OTHER_WINDOW_ID, 1_000L, true, code)
        }
        assertRejected(LocalPairingSubmissionRejection.EXPIRED) { code ->
            policy.validateSubmission(liveWindow(), WINDOW_ID, 120_000L, true, code)
        }
        assertRejected(LocalPairingSubmissionRejection.DEVICE_LOCKED) { code ->
            policy.validateSubmission(liveWindow(), WINDOW_ID, 1_000L, false, code)
        }
        assertRejected(LocalPairingSubmissionRejection.SERVICE_UNAVAILABLE) { code ->
            policy.validateSubmission(liveWindow(hasLivePairingService = false), WINDOW_ID, 1_000L, true, code)
        }
        assertRejected(LocalPairingSubmissionRejection.INVALID_CODE, "１２３４５６".toCharArray()) { code ->
            policy.validateSubmission(liveWindow(), WINDOW_ID, 1_000L, true, code)
        }
    }

    @Test(dataProvider = "stopReasons")
    fun `all stop conditions produce a terminal result without actions`(reason: LocalPairingStopReason) {
        val decision = policy.decide(
            window = liveWindow().copy(stopReason = reason),
            nowMillis = 1_000L,
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )

        assertEquals(decision.state, LocalPairingNotificationState.RESULT)
        assertEquals(decision.stopReason, reason)
        assertFalse(decision.inputActionAvailable)
        assertFalse(decision.submitAllowed)
        assertNull(decision.actionWindowId)
    }

    @DataProvider
    fun stopReasons(): Array<LocalPairingStopReason> = arrayOf(
        LocalPairingStopReason.SUCCEEDED,
        LocalPairingStopReason.CANCELLED,
        LocalPairingStopReason.SERVICE_LOST,
        LocalPairingStopReason.SESSION_CHANGED,
        LocalPairingStopReason.DEADLINE_REACHED,
        LocalPairingStopReason.SYSTEM_TIMEOUT,
        LocalPairingStopReason.FAILED,
    )

    private fun assertRejected(
        expected: LocalPairingSubmissionRejection,
        code: CharArray = "0".repeat(6).toCharArray(),
        validate: (CharArray) -> LocalPairingSubmissionDecision,
    ) {
        val decision = validate(code)
        assertEquals((decision as LocalPairingSubmissionDecision.Rejected).reason, expected)
        assertTrue(code.all { it == '\u0000' })
        assertFalse(decision.toString().contains("000000"))
    }

    private fun liveWindow(
        hasLivePairingService: Boolean = true,
    ): LocalPairingWindow = LocalPairingWindow(
        windowId = WINDOW_ID,
        attemptId = ATTEMPT_ID,
        method = PairingMethod.SIX_DIGIT_CODE,
        startedAtMillis = 0L,
        deadlineMillis = 120_000L,
        notificationState = LocalPairingNotificationState.HIDDEN,
        hasLivePairingService = hasLivePairingService,
    )

    private companion object {
        val WINDOW_ID = LocalPairingWindowId.of("window-synthetic")
        val OTHER_WINDOW_ID = LocalPairingWindowId.of("window-other")
        val ATTEMPT_ID = PairingAttemptId.of("attempt-synthetic")
    }
}
