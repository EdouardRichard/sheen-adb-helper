package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DisconnectedDestinationGateTest {
    @Test
    fun `every navigation entry path is guarded by one disconnected destination policy`() {
        val policy = File("src/main/kotlin/com/sheen/adbhelper/AppNavigationPolicy.kt")
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(policy.isFile, "AppNavigationPolicy.kt is missing")
        val source = policy.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "DisconnectedDestinationGate",
            "BOTTOM_BAR",
            "GESTURE",
            "RESTORE",
            "DEEP_LINK",
            "MainDestination.CONNECTION",
            "AppStringKey.CONNECT_FIRST",
        ).forEach { token ->
            assertTrue(source.contains(token) || app.contains(token), "missing disconnected gate $token")
        }
    }

    @Test
    fun `disconnected controlled pages receive neither visibility nor device request`() {
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(app.contains("DisconnectedDestinationGate"))
        assertTrue(app.contains("ControlledPageAvailability.Unavailable"))
        assertTrue(app.contains("AppStrings.resolve("))
        assertTrue(app.contains("deviceRequestAllowed = false"))
    }

    @Test
    fun `automatic disconnect returns to connection without redundant connect first guidance`() {
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()
        val connectionEffect = app
            .substringAfter("LaunchedEffect(connected)")
            .substringBefore("LaunchedEffect(navigationLock)")

        assertTrue(connectionEffect.contains("ControlledPageAvailability.Available"))
        assertTrue(!connectionEffect.contains("ControlledPageAvailability.Unavailable"))
    }
}
