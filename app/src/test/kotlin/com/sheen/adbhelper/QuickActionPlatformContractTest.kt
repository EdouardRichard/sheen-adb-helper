package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionPlatformContractTest {
    private val appSource = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()
    private val activitySource = File("src/main/kotlin/com/sheen/adbhelper/MainActivity.kt").readText()
    private val featureSources = listOf(
        File("../feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt"),
        File("../feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewViewModel.kt"),
    ).joinToString("\n") { it.readText() }

    @Test
    fun `PNG and MP4 CreateDocument launch only from completed artifact callback`() {
        assertTrue(appSource.contains("ActivityResultContracts.CreateDocument(\"image/png\")"))
        assertTrue(appSource.contains("ActivityResultContracts.CreateDocument(\"video/mp4\")"))
        assertTrue(appSource.contains("onExportRequested = { artifact ->"))
        assertTrue(appSource.contains("QuickActionArtifactFormat.PNG"))
        assertTrue(appSource.contains("QuickActionArtifactFormat.MP4"))
        assertFalse(appSource.contains("MediaProjection"))
        assertFalse(activitySource.contains("MediaProjection"))
    }

    @Test
    fun `platform selection becomes opaque destination and cancellation reaches use case`() {
        assertTrue(appSource.contains("ExportDestination(uri.toString()"))
        assertTrue(appSource.contains("overview.exportQuickAction(destination)"))
        assertTrue(appSource.contains("val destination = uri?.let"))
        assertFalse(appSource.contains("openOutputStream"))
        assertFalse(appSource.contains("ContentResolver"))
    }

    @Test
    fun `overview UI and ViewModel contain no URI resolver stream or raw ADB`() {
        listOf(
            "android.net.Uri",
            "ContentResolver",
            "OutputStream",
            "openOutputStream",
            "shell(",
            "screencap",
            "screenrecord",
        ).forEach { forbidden ->
            assertFalse(featureSources.contains(forbidden), "Feature leaks platform/raw ADB: $forbidden")
        }
    }
}
