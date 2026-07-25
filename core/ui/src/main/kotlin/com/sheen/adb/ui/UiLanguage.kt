package com.sheen.adb.ui

enum class UiLanguage(val preferenceValue: String) {
    ZH_CN("zh-CN"),
    EN_US("en-US"),
    ;

    companion object {
        fun fromPreference(value: String?): UiLanguage =
            entries.firstOrNull { language ->
                language.preferenceValue.equals(value?.trim(), ignoreCase = true)
            } ?: ZH_CN
    }
}
