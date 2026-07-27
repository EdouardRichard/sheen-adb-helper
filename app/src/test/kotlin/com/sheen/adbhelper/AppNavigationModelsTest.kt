package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppNavigationModelsTest {
    private val modelFile =
        File("src/main/kotlin/com/sheen/adbhelper/AppNavigationModels.kt")

    @Test
    fun `main destinations have one immutable six page order`() {
        assertTrue(modelFile.isFile, "AppNavigationModels.kt is missing")
        val source = modelFile.takeIf(File::isFile)?.readText().orEmpty()
        val block = source.substringAfter("enum class MainDestination")
            .substringAfter("{")
            .substringBefore("}")
        val actual = Regex("\\b(CONNECTION|FILES|APPLICATIONS|PROCESSES|SHELL|LOGCAT)\\b")
            .findAll(block)
            .map { it.value }
            .toList()

        assertEquals(
            actual,
            listOf("CONNECTION", "FILES", "APPLICATIONS", "PROCESSES", "SHELL", "LOGCAT"),
        )
    }

    @Test
    fun `navigation lock contains exactly seven persistent delivery kinds`() {
        assertTrue(modelFile.isFile, "AppNavigationModels.kt is missing")
        val source = modelFile.takeIf(File::isFile)?.readText().orEmpty()
        val expected = listOf(
            "FILE_UPLOAD",
            "FILE_DOWNLOAD",
            "APK_EXTRACTION",
            "APK_INSTALLATION",
            "LOGCAT_SAVE",
            "SCREENSHOT_SAVE",
            "SCREEN_RECORDING_SAVE_OR_EXPORT",
        )
        val kindBlock = source.substringAfter("enum class DeliveryKind")
            .substringAfter("{")
            .substringBefore("}")

        expected.forEach { assertTrue(kindBlock.contains(it), "missing delivery kind $it") }
        assertEquals(Regex("^\\s*[A-Z][A-Z0-9_]*[,;]?\\s*$", RegexOption.MULTILINE).findAll(kindBlock).count(), 7)
        listOf("SHELL_STREAM", "LOGCAT_COLLECTION", "COPY", "VIEW_RESULT").forEach {
            assertFalse(kindBlock.contains(it), "$it must not become a navigation lock")
        }
    }

    @Test
    fun `picker phases stay unlocked and only safe cleanup terminals release the lock`() {
        assertTrue(modelFile.isFile, "AppNavigationModels.kt is missing")
        val source = modelFile.takeIf(File::isFile)?.readText().orEmpty()

        listOf(
            "AWAITING_SELECTION",
            "PREPARING",
            "TRANSFERRING",
            "WRITING",
            "VERIFYING",
            "CANCELLING",
            "CLEANING",
            "TERMINAL",
            "PARTIAL",
            "UNKNOWN",
            "UNCERTAIN",
            "cleanupConfirmed",
            "toNavigationLockOrNull",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing lock-state contract $token")
        }
        assertTrue(
            Regex(
                "AWAITING_SELECTION\\s*,\\s*(?:DeliveryPhase\\.)?PREPARING\\s*->\\s*null",
            ).containsMatchIn(source),
            "picker/preparation phase must map to no lock",
        )
        assertTrue(
            source.contains("UNCERTAIN") && source.contains("NavigationLock("),
            "uncertain ownership must remain representable as locked",
        )
    }
}
