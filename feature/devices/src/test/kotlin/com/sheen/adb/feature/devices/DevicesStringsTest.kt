package com.sheen.adb.feature.devices

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DevicesStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/devices/DevicesStrings.kt")

    @Test
    fun `devices owns matching Chinese and English disconnected page catalogs`() {
        assertTrue(sourceFile.isFile, "DevicesStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class DevicesStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "requiredKeys",
            "placeholderSchema",
            "TITLE",
            "DISCOVERY_SCANNING",
            "DISCOVERY_EMPTY",
            "DISCOVERY_ERROR",
            "CONNECTION_ERROR",
            "DISMISS_ERROR",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing Devices catalog token $token")
        }
        assertTrue(
            source.contains("requiredKeys(UiLanguage.ZH_CN)") &&
                source.contains("requiredKeys(UiLanguage.EN_US)"),
            "both languages must expose the same required key set",
        )
    }

    @Test
    fun `QR code and local pairing states have complete bilingual semantics`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "PAIRING_QR_TITLE",
            "PAIRING_QR_INSTRUCTION",
            "SWITCH_TO_PAIRING_CODE",
            "PAIRING_CODE_TITLE",
            "LOCAL_PAIRING_TITLE",
            "LOCAL_PAIRING_INSTRUCTION",
            "PAIRING_PORT_FOUND",
            "PAIRING_CODE_PLACEHOLDER",
            "PAIR",
            "PAIRING_SUBMITTING",
            "PAIRING_SUCCEEDED",
            "PAIRING_FAILED",
            "PAIRING_CANCELLED",
            "PAIRING_PORT_NOT_FOUND",
            "RETRY_PAIRING_SCAN",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing pairing semantic key $token")
        }
    }

    @Test
    fun `pairing scan uses exact Chinese and equivalent English text`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()

        assertTrue(source.contains("\u6b63\u5728\u626b\u63cf\u914d\u5bf9\u7aef\u53e3"))
        assertTrue(
            Regex(
                """UiLanguage\.EN_US[\s\S]{0,6000}(Scanning|Searching)[^"\r\n]*pairing[^"\r\n]*port""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(source),
            "English catalog must provide an equivalent pairing-port scan message",
        )
    }

    @Test
    fun `confirmations errors and accessibility are localized without owning overview actions`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "PAIRING_CONFIRMATION",
            "CANCEL_PAIRING",
            "CLOSE_PAIRING",
            "DEVICE_ROW_CONTENT_DESCRIPTION",
            "DISMISS_ERROR_CONTENT_DESCRIPTION",
            "PAIRING_OVERLAY_CONTENT_DESCRIPTION",
            "PAIR_CONTENT_DESCRIPTION",
            "RETRY_CONTENT_DESCRIPTION",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing confirmation/a11y key $token")
        }
        listOf(
            "SCREENSHOT",
            "SCREEN_RECORD",
            "REBOOT",
            "SAVE_SCREENSHOT",
            "SAVE_RECORDING",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Overview key leaked into Devices: $forbidden")
        }
    }

    @Test
    fun `endpoint pairing code and technical code remain typed verbatim values`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "TypedTextArgument",
            "TextArgumentType.VERBATIM",
            "endpoint",
            "pairingCode",
            "technicalCode",
            "SafeVerbatimText",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing safe verbatim contract $token")
        }
        assertFalse(source.contains(".userMessage"), "core error prose must not become final Devices text")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not become final Devices text")
    }
}
