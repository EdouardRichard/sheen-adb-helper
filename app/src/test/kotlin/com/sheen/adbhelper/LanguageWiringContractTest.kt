package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LanguageWiringContractTest {
    @Test
    fun `app container owns one shared preference repository and one adb manager`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt").readText()

        assertEquals(source.windowed("DataStoreDeviceProfileRepository.create(application)".length)
            .count { it == "DataStoreDeviceProfileRepository.create(application)" }, 1)
        assertEquals(source.windowed("AdbManagerProvider.create(application)".length)
            .count { it == "AdbManagerProvider.create(application)" }, 1)
    }

    @Test
    fun `app observes persisted language without recreating adb manager`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(source.contains("container.deviceProfiles.languagePreference"))
        assertTrue(source.contains("collectAsStateWithLifecycle"))
        assertTrue(source.contains("UiLanguage.fromPreference"))
        assertTrue(source.contains("SettingsViewModel("))
        assertTrue(source.contains("repository = container.deviceProfiles"))
    }

    @Test
    fun `app has one root language collector and distributes one UiLanguage to every v1 route`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()
        val collection = "container.deviceProfiles.languagePreference.collectAsStateWithLifecycle"

        assertEquals(
            source.windowed(collection.length).count { it == collection },
            1,
            "language preference must be collected once at the app root",
        )
        listOf(
            "DevicesRoute",
            "OverviewRoute",
            "FilesRoute",
            "AppsRoute",
            "ProcessesRoute",
            "ShellRoute",
            "LogcatRoute",
            "SettingsRoute",
        ).forEach { route ->
            val invocation = source.substringAfter("$route(", missingDelimiterValue = "")
                .substringBefore(")")
            assertTrue(invocation.contains("language"), "$route does not receive root UiLanguage")
        }
    }

    @Test
    fun `v1 language wiring introduces no second locale mechanism`() {
        val sourceRoots = listOf(File("src/main"), File("../core"), File("../feature"))
        val forbidden = listOf(
            "LocaleManager",
            "AppCompatDelegate.setApplicationLocales",
            "setApplicationLocales(",
            "Activity.recreate(",
            ".recreate()",
            "ui_language_v2",
        )
        val violations = sourceRoots
            .filter(File::exists)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter(text::contains).map { token -> "${file.path}: $token" }
            }

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
        assertFalse(File("src/main/res/values-zh-rCN").exists(), "second resource-locale source introduced")
    }
}
