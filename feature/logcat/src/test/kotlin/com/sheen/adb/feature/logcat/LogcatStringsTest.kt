package com.sheen.adb.feature.logcat

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/logcat/LogcatStrings.kt")

    @Test
    fun `logcat owns one matching zh CN and en US page catalog`() {
        assertTrue(sourceFile.isFile, "LogcatStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class LogcatStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "requiredKeys",
            "placeholderSchema",
            "TITLE",
            "START",
            "STOP",
            "FILTER",
            "CLEAR",
            "DOWNLOAD",
        ).forEach { token -> assertTrue(source.contains(token), "missing Logcat catalog token $token") }
        assertTrue(
            source.contains("requiredKeys(UiLanguage.ZH_CN)") &&
                source.contains("requiredKeys(UiLanguage.EN_US)"),
            "both languages must expose the same required key set",
        )
    }

    @Test
    fun `collection and save lifecycle have complete bilingual terminal semantics`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "NEVER_STARTED",
            "STARTING",
            "COLLECTING",
            "STOPPED",
            "LIMIT_TIME",
            "LIMIT_BYTES",
            "ERROR",
            "CANCELLED",
            "DISCONNECTED",
            "UNSUPPORTED",
            "OUTCOME_UNKNOWN",
            "SAVE_WRITING",
            "SAVE_SUCCEEDED",
            "SAVE_FAILED",
            "SAVE_CANCELLED",
        ).forEach { token -> assertTrue(source.contains(token), "missing lifecycle key $token") }
        assertFalse(source.contains(".userMessage"), "core error prose must not become final Logcat text")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not become final Logcat text")
    }

    @Test
    fun `toolbar and start actions have localized accessibility coverage`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "START_CONTENT_DESCRIPTION",
            "STOP_CONTENT_DESCRIPTION",
            "FILTER_CONTENT_DESCRIPTION",
            "CLEAR_CONTENT_DESCRIPTION",
            "DOWNLOAD_CONTENT_DESCRIPTION",
            "LIVE_REGION_STATUS",
        ).forEach { token -> assertTrue(source.contains(token), "missing accessibility key $token") }
    }

    @Test
    fun `level labels and log text remain typed verbatim`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "TypedTextArgument",
            "TextArgumentType.VERBATIM",
            "VERBATIM_LOG_LINE",
            "VERBATIM_TECHNICAL_CODE",
            "SafeVerbatimText",
            "listOf(\"all\", \"debug\", \"info\", \"error\")",
        ).forEach { token -> assertTrue(source.contains(token), "missing verbatim contract $token") }
        assertFalse(
            Regex("(translate|localized)[A-Za-z]*\\([^)]*(all|debug|info|error)").containsMatchIn(source),
            "fixed technical level labels must not enter translation logic",
        )
    }
}
