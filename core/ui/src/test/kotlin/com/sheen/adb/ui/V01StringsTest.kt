package com.sheen.adb.ui

import org.testng.Assert.assertEquals
import org.testng.Assert.assertNotEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class V01StringsTest {
    @Test
    fun `supported preferences resolve to stable languages`() {
        assertEquals(UiLanguage.fromPreference("zh-CN"), UiLanguage.ZH_CN)
        assertEquals(UiLanguage.fromPreference("en-US"), UiLanguage.EN_US)
    }

    @Test
    fun `missing or unknown preference falls back to simplified Chinese`() {
        assertEquals(UiLanguage.fromPreference(null), UiLanguage.ZH_CN)
        assertEquals(UiLanguage.fromPreference(""), UiLanguage.ZH_CN)
        assertEquals(UiLanguage.fromPreference("unexpected-locale"), UiLanguage.ZH_CN)
    }

    @Test
    fun `unknown semantic key remains visible instead of becoming blank`() {
        val missingKey = "missing.semantic.key"

        assertEquals(V01Strings.text(UiLanguage.ZH_CN, missingKey), missingKey)
        assertEquals(V01Strings.text(UiLanguage.EN_US, missingKey), missingKey)
    }

    @Test
    fun `v01 catalog covers every required text category in both languages`() {
        val requiredKeys = listOf(
            V01StringKey.MENU,
            V01StringKey.HISTORY_DEVICES,
            V01StringKey.SETTINGS,
            V01StringKey.ABOUT,
            V01StringKey.TOP_CONNECT,
            V01StringKey.TOP_DISCONNECT,
            V01StringKey.NAV_CONNECT,
            V01StringKey.NAV_FILES,
            V01StringKey.NAV_APPS,
            V01StringKey.NAV_PROCESSES,
            V01StringKey.NAV_TERMINAL,
            V01StringKey.NAV_LOGS,
            V01StringKey.CONNECTION_CONNECTING,
            V01StringKey.CONNECTION_CONNECTED,
            V01StringKey.PAIRING_SCAN,
            V01StringKey.PAIRING_QR,
            V01StringKey.PAIRING_CODE,
            V01StringKey.PAIRING_LOCAL,
            V01StringKey.ERROR_INVALID_ENDPOINT,
            V01StringKey.ERROR_TIMEOUT,
            V01StringKey.ERROR_CANCELLED,
            V01StringKey.EMPTY_DISCOVERY,
            V01StringKey.EMPTY_HISTORY,
            V01StringKey.ABOUT_SUPPORT,
            V01StringKey.ABOUT_OPEN_GITHUB,
            V01StringKey.QUICK_SCREENSHOT,
            V01StringKey.QUICK_RECORD,
            V01StringKey.QUICK_REBOOT,
            V01StringKey.QUICK_STOP,
            V01StringKey.QUICK_UNSUPPORTED,
        )

        requiredKeys.forEach { key ->
            UiLanguage.entries.forEach { language ->
                assertTrue(
                    V01Strings.text(language, key).isNotBlank(),
                    "$language is missing $key",
                )
            }
            assertNotEquals(
                V01Strings.text(UiLanguage.ZH_CN, key),
                V01Strings.text(UiLanguage.EN_US, key),
                "$key should have explicit Chinese and English text",
            )
        }
    }
}
