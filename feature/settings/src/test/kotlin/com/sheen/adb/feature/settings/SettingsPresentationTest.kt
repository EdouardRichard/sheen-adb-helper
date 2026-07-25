package com.sheen.adb.feature.settings

import com.sheen.adb.data.LanguagePreference
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
        assertEquals(failed.languageError, "语言偏好保存失败，请重试。")
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
