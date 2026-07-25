package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
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
}
