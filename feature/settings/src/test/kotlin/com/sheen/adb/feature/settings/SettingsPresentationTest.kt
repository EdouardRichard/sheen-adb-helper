package com.sheen.adb.feature.settings

import com.sheen.adb.data.LanguagePreference
import com.sheen.adb.ui.UiLanguage
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class SettingsPresentationTest {
    @Test
    fun `clear data requires explicit confirmation`() {
        val requested = SettingsUiState("0.0.1", clearResult = "old").requestClearConfirmation()
        assertTrue(requested.showClearConfirmation)
        assertNull(requested.clearResult)
        assertFalse(requested.dismissClearConfirmation().showClearConfirmation)
    }

    @Test
    fun `Chinese and English selections are mutually exclusive and immediate`() {
        val initial = SettingsUiState("0.1.0")
        val english = initial.selectLanguage(LanguagePreference.EN_US)
        assertEquals(english.language, LanguagePreference.EN_US)
        assertTrue(english.isSavingLanguage)
        assertNull(english.languageError)

        val chinese = english.selectLanguage(LanguagePreference.ZH_CN)
        assertEquals(chinese.language, LanguagePreference.ZH_CN)
        assertTrue(chinese.isSavingLanguage)
    }

    @Test
    fun `save failure is visible without changing the selected language`() {
        val saving = SettingsUiState("0.1.0")
            .selectLanguage(LanguagePreference.EN_US)
        val failed = saving.finishLanguageSave(success = false)

        assertEquals(failed.language, LanguagePreference.EN_US)
        assertFalse(failed.isSavingLanguage)
        assertEquals(failed.languageMessage, SettingsMessageCode.LANGUAGE_SAVE_FAILED)
    }

    @Test
    fun `language feedback is resolved by the settings catalog at presentation time`() {
        assertEquals(
            SettingsStrings.resolve(UiLanguage.ZH_CN, SettingsStringKey.LANGUAGE_SAVE_FAILED),
            "语言偏好保存失败，请重试。",
        )
        assertEquals(
            SettingsStrings.resolve(UiLanguage.EN_US, SettingsStringKey.LANGUAGE_SAVE_FAILED),
            "Could not save the language preference. Try again.",
        )
        assertEquals(
            SettingsStrings.requiredKeys(UiLanguage.ZH_CN),
            SettingsStrings.requiredKeys(UiLanguage.EN_US),
        )
    }

    @Test
    fun `settings route receives root language and owns no second language source`() {
        val source = File(
            "src/main/kotlin/com/sheen/adb/feature/settings/SettingsScreen.kt",
        ).readText()

        assertTrue(source.contains("fun SettingsRoute("))
        assertTrue(source.contains("language: UiLanguage"))
        assertTrue(source.contains("SettingsStrings.resolve("))
        assertFalse(source.contains("languagePreference.collect"))
        assertFalse(source.contains("\"语言偏好保存失败，请重试。\""))
    }

    @Test
    fun `successful clear falls back to simplified Chinese`() {
        val english = SettingsUiState(
            versionLabel = "0.1.0",
            language = LanguagePreference.EN_US,
        )
        val cleared = english.applyClearResult(success = true)

        assertEquals(cleared.language, LanguagePreference.ZH_CN)
        assertFalse(cleared.isClearing)
    }
}
