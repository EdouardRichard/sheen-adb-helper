package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adbhelper/AppStrings.kt")

    @Test
    fun `app catalog owns only navigation root overlay and host accessibility semantics`() {
        assertTrue(sourceFile.isFile, "AppStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        val requiredKeys = listOf(
            "NAVIGATION_LOCKED",
            "NAVIGATION_DISABLED_REASON",
            "LONG_TASK_BACK_TITLE",
            "CONTINUE_TASK",
            "CANCEL_TASK_AND_LEAVE",
            "ROOT_OVERLAY_CLOSE",
            "PAIRING_OVERLAY_DISMISS",
            "CONNECT_FIRST",
        )

        requiredKeys.forEach { key ->
            assertTrue(source.contains("$key(") || source.contains("$key,"), "missing App key $key")
            assertEquals(
                Regex("AppStringKey\\.$key").findAll(source).count(),
                3,
                "$key must occur once in schema plus once per language catalog",
            )
        }
        listOf("files.", "apps.", "processes.", "shell.", "logcat.").forEach { pagePrefix ->
            assertTrue(!source.contains("\"$pagePrefix"), "page business key leaked into App: $pagePrefix")
        }
    }

    @Test
    fun `app Chinese and English catalogs declare the same typed placeholders`() {
        assertTrue(sourceFile.isFile, "AppStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()

        assertTrue(source.contains("UiLanguage.ZH_CN to mapOf("))
        assertTrue(source.contains("UiLanguage.EN_US to mapOf("))
        assertTrue(source.contains("placeholderSchemas"))
        assertTrue(source.contains("TypedTextArgument"))
        assertTrue(source.contains("LocalizedTextRef"))
    }

    @Test
    fun `language presentation is a pure preference mapping`() {
        val file = File("src/main/kotlin/com/sheen/adbhelper/LanguagePresentation.kt")
        assertTrue(file.isFile, "LanguagePresentation.kt is missing")
        val source = file.takeIf(File::isFile)?.readText().orEmpty()

        assertTrue(source.contains("LanguagePreference.ZH_CN -> UiLanguage.ZH_CN"))
        assertTrue(source.contains("LanguagePreference.EN_US -> UiLanguage.EN_US"))
        listOf(
            "ViewModel",
            "LaunchedEffect",
            "DisposableEffect",
            "AdbSession",
            "NavigationLock",
            "DataStore",
        ).forEach { forbidden ->
            assertTrue(!source.contains(forbidden), "language mapping owns $forbidden")
        }
    }
}
