package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class RootPairingOverlayTest {
    @Test
    fun `first local pairing waits for notification authorization before opening system settings`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(source.contains("pendingWirelessDebuggingSettingsLaunch"))
        assertTrue(source.contains("openPendingWirelessDebuggingSettings"))
        assertTrue(
            Regex(
                "notificationPermissionRequestGeneration\\s*>\\s*" +
                    "handledNotificationPermissionGeneration",
            ).containsMatchIn(source),
        )
    }

    @Test
    fun `pairing is hosted as the app root overlay`() {
        val fixture = Fixture()

        fixture.show()

        assertEquals(
            fixture.controller.state,
            RootOverlayState.Pairing(ATTEMPT_ID),
        )
        assertEquals(fixture.cancelAndClearCalls, 0)
    }

    @Test
    fun `outside tap closes the root pairing overlay and clears sensitive state once`() {
        val fixture = Fixture().apply { show() }

        fixture.controller.onOutsideTap()
        fixture.controller.onOutsideTap()

        fixture.assertClosedAndClearedOnce()
    }

    @Test
    fun `back closes the root pairing overlay before page navigation and clears sensitive state`() {
        val fixture = Fixture().apply { show() }

        assertTrue(fixture.controller.onBackPressed())

        fixture.assertClosedAndClearedOnce()
        assertFalse(
            fixture.controller.onBackPressed(),
            "a consumed pairing-overlay back action must not be replayed after dismissal",
        )
    }

    @Test
    fun `backgrounding closes the pairing overlay and clears sensitive state`() {
        val fixture = Fixture().apply { show() }

        fixture.controller.onHostForegroundChanged(isForeground = false)

        fixture.assertClosedAndClearedOnce()
    }

    @Test
    fun `opening system wireless settings keeps local pairing alive until the app returns`() {
        val fixture = Fixture().apply { show() }

        fixture.controller.onHostForegroundChanged(
            isForeground = false,
            preserveForWirelessSettings = true,
        )

        assertEquals(fixture.controller.state, RootOverlayState.Pairing(ATTEMPT_ID))
        assertEquals(fixture.cancelAndClearCalls, 0)
    }

    @Test
    fun `session switch closes the pairing overlay while an unchanged session keeps it open`() {
        val fixture = Fixture().apply { show() }

        fixture.controller.onSessionChanged(OWNER_SESSION_ID)
        assertEquals(fixture.controller.state, RootOverlayState.Pairing(ATTEMPT_ID))
        assertEquals(fixture.cancelAndClearCalls, 0)

        fixture.controller.onSessionChanged("replacement-session")

        fixture.assertClosedAndClearedOnce()
    }

    private class Fixture {
        val controller = RootPairingOverlayController()
        var cancelAndClearCalls: Int = 0
        var qrMaterialPresent: Boolean = true
        var endpointMaterialPresent: Boolean = true
        val pairingSecret: CharArray = charArrayOf('s', 'e', 'c', 'r', 'e', 't')

        fun show() {
            controller.show(
                attemptId = ATTEMPT_ID,
                ownerSessionId = OWNER_SESSION_ID,
                cancelAndClear = {
                    cancelAndClearCalls += 1
                    pairingSecret.fill('\u0000')
                    qrMaterialPresent = false
                    endpointMaterialPresent = false
                },
            )
        }

        fun assertClosedAndClearedOnce() {
            assertNull(controller.state)
            assertEquals(cancelAndClearCalls, 1)
            assertTrue(pairingSecret.all { it == '\u0000' })
            assertFalse(qrMaterialPresent)
            assertFalse(endpointMaterialPresent)
        }
    }

    private companion object {
        const val ATTEMPT_ID = "opaque-attempt"
        const val OWNER_SESSION_ID = "owner-session"
    }
}
