package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionWiringContractTest {
    private val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt").readText()

    @Test
    fun `application container only constructs project quick action components`() {
        assertTrue(source.contains("QuickActionArtifactStore(application)"))
        assertTrue(source.contains("SafBinaryExporter(application, quickActionArtifactStore)"))
        assertTrue(
            source.contains(
                "QuickActionUseCase(adbManager, quickActionArtifactStore, quickActionExporter)",
            ),
        )
        assertTrue(source.contains("val quickActionUseCase"))
    }

    @Test
    fun `application container owns no capture export or cleanup workflow`() {
        listOf(
            ".captureScreenshot(",
            ".recordScreen(",
            ".reboot(",
            ".export(",
            "CaptureMetadata",
            "AdbCaptureSink",
            "ContentResolver",
            "OutputStream",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "App container owns quick-action logic: $forbidden")
        }
    }
}
