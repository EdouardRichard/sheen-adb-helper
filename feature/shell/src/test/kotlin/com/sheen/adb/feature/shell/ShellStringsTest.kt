package com.sheen.adb.feature.shell

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/shell/ShellStrings.kt")

    @Test
    fun `shell owns one matching zh CN and en US page catalog`() {
        assertTrue(sourceFile.isFile, "ShellStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class ShellStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "requiredKeys",
            "placeholderSchema",
            "TITLE",
            "CLEAR",
            "FILTER",
            "AUTO_SCROLL",
            "COMMAND_INPUT",
            "SESSION_SEPARATOR",
        ).forEach { token -> assertTrue(source.contains(token), "missing Shell catalog token $token") }
        assertTrue(
            source.contains("requiredKeys(UiLanguage.ZH_CN)") &&
                source.contains("requiredKeys(UiLanguage.EN_US)"),
            "both languages must expose the same required key set",
        )
    }

    @Test
    fun `risk confirmation has complete bilingual action and consequence semantics`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "RISK_CONFIRMATION_TITLE",
            "RISK_CONFIRMATION_BODY",
            "RISK_CONFIRMATION_SEND",
            "RISK_CONFIRMATION_CANCEL",
            "COMMAND_IDENTITY",
            "LocalizedTextRef",
        ).forEach { token -> assertTrue(source.contains(token), "missing risk-confirmation key $token") }
    }

    @Test
    fun `stream terminal catalog covers every structured close result`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "STREAM_OPENING",
            "STREAM_ACTIVE",
            "STREAM_CLOSED",
            "STREAM_TIMEOUT",
            "STREAM_CANCELLED",
            "STREAM_UNSUPPORTED",
            "STREAM_DISCONNECTED",
            "STREAM_PROTOCOL_FAILURE",
            "STREAM_OUTPUT_LIMIT",
            "STREAM_OUTCOME_UNKNOWN",
            "STREAM_CLEANUP_UNCERTAIN",
        ).forEach { token -> assertTrue(source.contains(token), "missing stream terminal key $token") }
        assertFalse(source.contains(".userMessage"), "core error prose must not be final Shell text")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not be final Shell text")
    }

    @Test
    fun `every toolbar key and IME action has localized accessibility coverage`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "CLEAR_CONTENT_DESCRIPTION",
            "FILTER_CONTENT_DESCRIPTION",
            "AUTO_SCROLL_CONTENT_DESCRIPTION",
            "ESC_CONTENT_DESCRIPTION",
            "TAB_CONTENT_DESCRIPTION",
            "CTRL_CONTENT_DESCRIPTION",
            "ALT_CONTENT_DESCRIPTION",
            "ARROW_UP_CONTENT_DESCRIPTION",
            "ARROW_DOWN_CONTENT_DESCRIPTION",
            "KEYBOARD_RETURN_CONTENT_DESCRIPTION",
            "LIVE_REGION_STATUS",
        ).forEach { token -> assertTrue(source.contains(token), "missing Shell accessibility key $token") }
    }

    @Test
    fun `commands output technical codes and fixed key labels remain typed verbatim`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "TypedTextArgument",
            "TextArgumentType.VERBATIM",
            "VERBATIM_COMMAND",
            "VERBATIM_OUTPUT",
            "VERBATIM_TECHNICAL_CODE",
            "VERBATIM_KEY_LABEL",
            "SafeVerbatimText",
        ).forEach { token -> assertTrue(source.contains(token), "missing verbatim contract $token") }

        listOf("Esc", "Tab", "Ctrl", "Alt").forEach { label ->
            assertTrue(source.contains("\"$label\""), "fixed technical key label missing: $label")
        }
        assertTrue(
            source.contains("listOf(\"Esc\", \"Tab\", \"Ctrl\", \"Alt\")"),
            "fixed key labels must share one language-independent verbatim source",
        )
        assertFalse(
            Regex("(translate|localized)[A-Za-z]*\\([^)]*(Esc|Tab|Ctrl|Alt)").containsMatchIn(source),
            "technical key labels must not enter translation logic",
        )
    }
}
