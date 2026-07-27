package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppNavigationLifecycleTest {
    private val appFile = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
    private val activityFile = File("src/main/kotlin/com/sheen/adbhelper/MainActivity.kt")

    @Test
    fun `accepted transition dispatches exactly one hidden and one visible callback`() {
        val source = appFile.readText()

        listOf(
            "dispatchPageHidden(",
            "dispatchPageVisible(",
            "transitionGeneration",
            "PageTransition.FADING_OUT",
            "PageTransition.FADING_IN",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing exactly-once lifecycle token $token")
        }
    }

    @Test
    fun `background session switch and back priority are app owned`() {
        val app = appFile.readText()
        val activity = activityFile.readText()

        listOf(
            "BackPriority.ROOT_OVERLAY",
            "BackPriority.OPERATION_CONFIRMATION",
            "BackPriority.LONG_TASK",
            "BackPriority.EXIT_APPLICATION",
            "awaitTaskCleanup",
            "onHostForegroundChanged",
            "onSessionChanged",
        ).forEach { token ->
            assertTrue(app.contains(token) || activity.contains(token), "missing lifecycle owner $token")
        }
        assertTrue(activity.contains("onStart()"))
        assertTrue(activity.contains("onStop()"))
    }
}
