package com.sheen.adbhelper.localpairing

import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingWindowId
import java.nio.file.Files
import java.nio.file.Path
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

internal class LocalPairingPlatformContractTest {
    @Test
    fun `locked notification is private action free and waits for user present`() {
        val facade = FakePlatformFacade(deviceLocked = true)
        val plan = LocalPairingPlatformPolicy(facade).notificationPlan(
            coreDecision = readyDecision(),
            apiLevel = 36,
            windowActive = true,
        )

        assertTrue(plan.visibilityPrivate)
        assertTrue(plan.localOnly)
        assertFalse(plan.showRemoteInput)
        assertFalse(plan.submitActionAvailable)
        assertTrue(plan.registerUserPresentReceiver)
        assertFalse(plan.stopService)
        assertEquals(plan.startForegroundWithinMillis, 5_000L)
    }

    @Test
    fun `unlocked input action is explicit one shot mutable and authenticated on API 31 plus`() {
        val facade = FakePlatformFacade(deviceLocked = false)
        val plan = LocalPairingPlatformPolicy(facade).notificationPlan(
            coreDecision = readyDecision(),
            apiLevel = 36,
            windowActive = true,
        )

        assertTrue(plan.showRemoteInput)
        assertTrue(plan.submitActionAvailable)
        assertTrue(plan.pendingIntentExplicit)
        assertTrue(plan.pendingIntentOneShot)
        assertTrue(plan.pendingIntentMutable)
        assertTrue(plan.authenticationRequired)
        assertFalse(plan.registerUserPresentReceiver)

        val api30 = LocalPairingPlatformPolicy(facade).notificationPlan(readyDecision(), 30, true)
        assertFalse(api30.authenticationRequired)
        assertTrue(api30.pendingIntentOneShot)
        assertTrue(api30.pendingIntentMutable)
    }

    @Test
    fun `permission notification and OEM limits preserve application input fallback`() {
        val notificationsDisabled = LocalPairingPlatformPolicy(
            FakePlatformFacade(notificationsEnabled = false),
        ).notificationPlan(readyDecision(), 36, true)
        val inlineUnavailable = LocalPairingPlatformPolicy(
            FakePlatformFacade(inlineInputSupported = false),
        ).notificationPlan(readyDecision(), 36, true)

        listOf(notificationsDisabled, inlineUnavailable).forEach {
            assertFalse(it.showRemoteInput)
            assertTrue(it.applicationInputAvailable)
            assertFalse(it.stopService)
        }
        assertFalse(notificationsDisabled.suggestNativeNotificationStyle)
        assertTrue(inlineUnavailable.suggestNativeNotificationStyle)
    }

    @Test
    fun `receiver rechecks lock active token and six ASCII digits before creating a secret`() {
        val facade = FakePlatformFacade(deviceLocked = false)
        val policy = LocalPairingPlatformPolicy(facade)
        val valid = "0".repeat(6).toCharArray()
        val accepted = policy.validateRemoteInput(
            windowActive = true,
            tokenMatches = true,
            beforeDeadline = true,
            code = valid,
        )
        assertTrue(accepted != null)
        assertFalse(accepted.toString().contains("000000"))
        accepted?.clear()

        assertRejected(policy, windowActive = false)
        assertRejected(policy, tokenMatches = false)
        assertRejected(policy, beforeDeadline = false)
        assertRejected(policy, code = "１２３４５６".toCharArray())

        facade.deviceLocked = true
        assertRejected(policy)
    }

    @Test(dataProvider = "stopReasons")
    fun `every core terminal reason stops service and removes actions`(reason: LocalPairingStopReason) {
        val plan = LocalPairingPlatformPolicy(FakePlatformFacade()).notificationPlan(
            coreDecision = readyDecision().copy(
                state = LocalPairingNotificationState.RESULT,
                inputActionAvailable = false,
                submitAllowed = false,
                actionWindowId = null,
                applicationInputAvailable = false,
                stopReason = reason,
            ),
            apiLevel = 36,
            windowActive = false,
        )

        assertTrue(plan.stopService)
        assertFalse(plan.showRemoteInput)
        assertFalse(plan.submitActionAvailable)
        assertFalse(plan.registerUserPresentReceiver)
    }

    @DataProvider
    fun stopReasons(): Array<LocalPairingStopReason> = LocalPairingStopReason.entries.toTypedArray()

    @Test
    fun `manifest and service source encode the approved Android contract`() {
        val manifest = String(Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")))
        val source = String(
            Files.readAllBytes(
                Path.of("src/main/kotlin/com/sheen/adbhelper/localpairing/LocalPairingForegroundService.kt"),
            ),
        )

        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"shortService\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains(".localpairing.LocalPairingForegroundService"))

        listOf(
            "START_NOT_STICKY",
            "startForeground(",
            "override fun onTimeout",
            "Notification.VISIBILITY_PRIVATE",
            "Context.RECEIVER_NOT_EXPORTED",
            "PendingIntent.FLAG_ONE_SHOT",
            "PendingIntent.FLAG_MUTABLE",
            "setAuthenticationRequired(true)",
            "RemoteInput.getResultsFromIntent",
            "isDeviceLocked",
            "stopSelf(",
            "notificationManager.cancel(",
        ).forEach { required -> assertTrue(source.contains(required), "Missing platform contract: $required") }
        assertFalse(source.contains("Activity"))
        assertFalse(source.contains("START_STICKY"))
    }

    private fun assertRejected(
        policy: LocalPairingPlatformPolicy,
        windowActive: Boolean = true,
        tokenMatches: Boolean = true,
        beforeDeadline: Boolean = true,
        code: CharArray = "0".repeat(6).toCharArray(),
    ) {
        val result = policy.validateRemoteInput(windowActive, tokenMatches, beforeDeadline, code)
        assertNull(result)
        assertTrue(code.all { it == '\u0000' })
    }

    private fun readyDecision(): LocalPairingNotificationDecision = LocalPairingNotificationDecision(
        state = LocalPairingNotificationState.INPUT_READY,
        inputActionAvailable = true,
        submitAllowed = true,
        actionWindowId = LocalPairingWindowId.of("window-synthetic"),
        applicationInputAvailable = true,
        suggestNativeNotificationStyle = false,
    )

    private class FakePlatformFacade(
        var deviceLocked: Boolean = false,
        private val notificationsEnabled: Boolean = true,
        private val inlineInputSupported: Boolean = true,
    ) : LocalPairingPlatformFacade {
        override fun isDeviceLocked(): Boolean = deviceLocked
        override fun areNotificationsEnabled(): Boolean = notificationsEnabled
        override fun isInlineInputSupported(): Boolean = inlineInputSupported
    }
}
