package com.sheen.adb.feature.overview

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class OverviewStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/overview/OverviewStrings.kt")

    @Test
    fun `overview owns matching Chinese and English connected page catalogs`() {
        assertTrue(sourceFile.isFile, "OverviewStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class OverviewStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "requiredKeys",
            "placeholderSchema",
            "TITLE",
            "QUICK_ACTIONS",
            "SCREENSHOT",
            "SCREEN_RECORD",
            "STOP_SCREEN_RECORD",
            "REBOOT",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing Overview catalog token $token")
        }
        assertTrue(
            source.contains("requiredKeys(UiLanguage.ZH_CN)") &&
                source.contains("requiredKeys(UiLanguage.EN_US)"),
            "both languages must expose the same required key set",
        )
    }

    @Test
    fun `screenshot recording and reboot have bilingual action and confirmation semantics`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "SCREENSHOT_CONTENT_DESCRIPTION",
            "SCREEN_RECORD_CONTENT_DESCRIPTION",
            "STOP_SCREEN_RECORD_CONTENT_DESCRIPTION",
            "REBOOT_CONTENT_DESCRIPTION",
            "REBOOT_CONFIRMATION",
            "REBOOT_RISK",
            "CONFIRM_REBOOT",
            "CANCEL_REBOOT",
            "CAPTURING_SCREENSHOT",
            "SCREEN_RECORDING",
            "FINALIZING_SCREEN_RECORD",
            "REBOOT_REQUESTED",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing quick-action semantic/a11y key $token")
        }
    }

    @Test
    fun `save phases and terminal results have complete bilingual semantics`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "AWAITING_DESTINATION",
            "SAVE_SCREENSHOT",
            "SAVE_SCREEN_RECORD",
            "SAVE_WRITING",
            "SAVE_CANCELLING",
            "SAVE_CLEANING",
            "SAVE_SUCCEEDED",
            "SAVE_FAILED",
            "SAVE_CANCELLED",
            "DISCONNECTED",
            "UNSUPPORTED",
            "OUTCOME_UNKNOWN",
            "SAVE_CONTENT_DESCRIPTION",
            "SAVE_STATUS_LIVE_ANNOUNCEMENT",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing save lifecycle/result key $token")
        }
        assertFalse(source.contains(".userMessage"), "core error prose must not become final Overview text")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not become final Overview text")
    }

    @Test
    fun `artifact label and technical code remain typed verbatim arguments`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "LocalizedTextRef",
            "TypedTextArgument",
            "TextArgumentType.VERBATIM",
            "artifactLabel",
            "technicalCode",
            "SafeVerbatimText",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing Overview verbatim contract $token")
        }
    }

    @Test
    fun `devices catalog does not own connected quick action strings`() {
        val devicesSource = File(
            "../devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesStrings.kt",
        ).takeIf(File::isFile)?.readText().orEmpty()

        listOf(
            "SCREENSHOT",
            "SCREEN_RECORD",
            "REBOOT",
            "SAVE_SCREENSHOT",
            "SAVE_SCREEN_RECORD",
        ).forEach { forbidden ->
            assertFalse(
                devicesSource.contains(forbidden),
                "connected quick-action key leaked into Devices: $forbidden",
            )
        }
    }
}
