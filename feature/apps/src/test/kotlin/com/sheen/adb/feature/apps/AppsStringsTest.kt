package com.sheen.adb.feature.apps

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppsStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsStrings.kt")

    @Test
    fun `applications owns matching Chinese and English page keys`() {
        assertTrue(sourceFile.isFile, "AppsStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class AppsStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "TITLE",
            "SEARCH_PLACEHOLDER",
            "UNKNOWN_APP_NAME",
            "EXTRACT_APK",
            "DISABLE_APP",
            "ENABLE_APP",
            "FORCE_STOP_APP",
            "UNINSTALL_APP",
            "INSTALL_APK",
        ).forEach { token -> assertTrue(source.contains(token), "missing apps string token $token") }
        assertTrue(source.contains("请输入应用名或包名。"))
        assertTrue(source.contains("Enter an app name or package name."))
    }

    @Test
    fun `catalog covers destructive confirmations stages component outcomes and accessibility`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "UNINSTALL_CONFIRMATION",
            "UNINSTALL_PRIVATE_DATA_WARNING",
            "FORCE_STOP_CONFIRMATION",
            "FORCE_INSTALL_CONFIRMATION",
            "SIGNATURE_MISMATCH_CONFIRMATION",
            "UNINSTALLING_OLD",
            "INSTALLING_NEW",
            "COMPONENT_SUCCEEDED",
            "COMPONENT_FAILED",
            "COMPLETE_SUCCESS",
            "INSTALL_SUCCESS",
            "PARTIAL_SUCCESS",
            "NONE_COMMITTED",
            "PROGRESS",
            "CANCELLED",
            "UNSUPPORTED",
            "OUTCOME_UNKNOWN",
            "CONTENT_DESCRIPTION",
        ).forEach { token -> assertTrue(source.contains(token), "missing semantic/a11y key $token") }
    }

    @Test
    fun `package app name and technical code are typed verbatim arguments not translated prose`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "LocalizedTextRef",
            "TypedTextArgument",
            "TextArgumentType.VERBATIM",
            "packageName",
            "applicationName",
            "technicalCode",
        ).forEach { token -> assertTrue(source.contains(token), "missing verbatim contract $token") }
        assertFalse(source.contains(".userMessage"), "core error prose must not be a final app-page string")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not be a final app-page string")
    }
}
