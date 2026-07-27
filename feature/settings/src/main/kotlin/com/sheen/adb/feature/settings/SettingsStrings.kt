package com.sheen.adb.feature.settings

import com.sheen.adb.ui.UiLanguage

enum class SettingsMessageCode {
    LANGUAGE_SAVE_FAILED,
}

enum class SettingsStringKey {
    TITLE,
    LANGUAGE,
    LANGUAGE_CHINESE,
    LANGUAGE_ENGLISH,
    LANGUAGE_SAVE_FAILED,
}

object SettingsStrings {
    private val catalogs: Map<UiLanguage, Map<SettingsStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            SettingsStringKey.TITLE to "设置与隐私",
            SettingsStringKey.LANGUAGE to "语言",
            SettingsStringKey.LANGUAGE_CHINESE to "简体中文",
            SettingsStringKey.LANGUAGE_ENGLISH to "English",
            SettingsStringKey.LANGUAGE_SAVE_FAILED to "语言偏好保存失败，请重试。",
        ),
        UiLanguage.EN_US to mapOf(
            SettingsStringKey.TITLE to "Settings & Privacy",
            SettingsStringKey.LANGUAGE to "Language",
            SettingsStringKey.LANGUAGE_CHINESE to "简体中文",
            SettingsStringKey.LANGUAGE_ENGLISH to "English",
            SettingsStringKey.LANGUAGE_SAVE_FAILED to
                "Could not save the language preference. Try again.",
        ),
    )

    fun requiredKeys(language: UiLanguage): Set<SettingsStringKey> =
        catalogs.getValue(language).keys

    fun resolve(language: UiLanguage, key: SettingsStringKey): String =
        catalogs.getValue(language).getValue(key)

    fun keyFor(message: SettingsMessageCode): SettingsStringKey = when (message) {
        SettingsMessageCode.LANGUAGE_SAVE_FAILED -> SettingsStringKey.LANGUAGE_SAVE_FAILED
    }
}
