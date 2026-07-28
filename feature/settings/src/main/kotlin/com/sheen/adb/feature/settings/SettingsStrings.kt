package com.sheen.adb.feature.settings

import com.sheen.adb.ui.UiLanguage

enum class SettingsMessageCode {
    LANGUAGE_SAVE_FAILED,
    MANUAL_SETTINGS_PATH,
    CLEAR_SUCCEEDED,
    CLEAR_FAILED,
}

enum class SettingsStringKey {
    TITLE,
    LANGUAGE,
    LANGUAGE_CHINESE,
    LANGUAGE_ENGLISH,
    LANGUAGE_SAVE_FAILED,
    VERSION,
    PRIVACY_TITLE,
    PRIVACY_BODY,
    SUPPORT_TITLE,
    SUPPORT_BODY,
    LICENSES_TITLE,
    LICENSES_BODY,
    PAIRING_HELP_TITLE,
    PAIRING_HELP_BODY,
    OPEN_WIRELESS_DEBUGGING,
    OPEN_DEVELOPER_OPTIONS,
    MANUAL_SETTINGS_PATH,
    CLEAR_ALL_DATA,
    CLEARING_DATA,
    CLEAR_SUCCEEDED,
    CLEAR_FAILED,
    CLEAR_CONFIRM_TITLE,
    CLEAR_CONFIRM_BODY,
    CLEAR_CONFIRM,
    CANCEL,
}

object SettingsStrings {
    private val catalogs: Map<UiLanguage, Map<SettingsStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            SettingsStringKey.TITLE to "设置与隐私",
            SettingsStringKey.LANGUAGE to "语言",
            SettingsStringKey.LANGUAGE_CHINESE to "简体中文",
            SettingsStringKey.LANGUAGE_ENGLISH to "English",
            SettingsStringKey.LANGUAGE_SAVE_FAILED to "语言偏好保存失败，请重试。",
            SettingsStringKey.VERSION to "应用版本",
            SettingsStringKey.PRIVACY_TITLE to "纯本地隐私承诺",
            SettingsStringKey.PRIVACY_BODY to
                "无账号、后端、广告、统计、遥测或崩溃上报。设备档案仅保存在本机；Shell、进程与 Logcat 内容默认只在内存中。",
            SettingsStringKey.SUPPORT_TITLE to "风险与支持范围",
            SettingsStringKey.SUPPORT_BODY to
                "主控端 Android 11（API 30）及以上；优先支持 Android 11+ 标准无线调试。旧式 :5555 为尽力兼容。ADB Shell 命令可能改变设备数据。",
            SettingsStringKey.LICENSES_TITLE to "开源与第三方许可证",
            SettingsStringKey.LICENSES_BODY to
                "本项目：Apache-2.0；Kadb 2.1.1：Apache-2.0；AndroidX：Apache-2.0；Coroutines 1.10.2：Apache-2.0；Okio 3.17.0：Apache-2.0；Bouncy Castle 1.83：MIT；spake2-java 1.0.5：Apache-2.0；HiddenApiBypass 6.1：Apache-2.0。",
            SettingsStringKey.PAIRING_HELP_TITLE to "无线调试与配对帮助",
            SettingsStringKey.PAIRING_HELP_BODY to
                "先在被控端手动开启开发者选项和无线调试。首页始终先尝试调试端口；只有认证失败时才使用系统“使用配对码配对设备”中的配对端口和 6 位配对码。配对成功后回到无线调试主页面填写调试端口。",
            SettingsStringKey.OPEN_WIRELESS_DEBUGGING to "打开无线调试设置",
            SettingsStringKey.OPEN_DEVELOPER_OPTIONS to "打开开发者选项",
            SettingsStringKey.MANUAL_SETTINGS_PATH to
                "请手动打开：系统设置 → 关于手机 → 连续点击系统版本开启开发者选项 → 更多设置 → 开发者选项 → 无线调试。",
            SettingsStringKey.CLEAR_ALL_DATA to "清除所有本地数据",
            SettingsStringKey.CLEARING_DATA to "正在清除并验证……",
            SettingsStringKey.CLEAR_SUCCEEDED to "所有本地数据已清除并验证不可继续使用旧身份。",
            SettingsStringKey.CLEAR_FAILED to "清除未完全成功，请重启应用后重试。",
            SettingsStringKey.CLEAR_CONFIRM_TITLE to "清除所有本地数据？",
            SettingsStringKey.CLEAR_CONFIRM_BODY to
                "将删除设备档案、ADB 主机身份、偏好和临时文件。之后可能需要重新配对，且无法撤销。",
            SettingsStringKey.CLEAR_CONFIRM to "确认清除",
            SettingsStringKey.CANCEL to "取消",
        ),
        UiLanguage.EN_US to mapOf(
            SettingsStringKey.TITLE to "Settings & Privacy",
            SettingsStringKey.LANGUAGE to "Language",
            SettingsStringKey.LANGUAGE_CHINESE to "简体中文",
            SettingsStringKey.LANGUAGE_ENGLISH to "English",
            SettingsStringKey.LANGUAGE_SAVE_FAILED to
                "Could not save the language preference. Try again.",
            SettingsStringKey.VERSION to "App version",
            SettingsStringKey.PRIVACY_TITLE to "Local-only privacy promise",
            SettingsStringKey.PRIVACY_BODY to
                "No accounts, backend, ads, analytics, telemetry, or crash reporting. Device profiles stay on this device; Shell, process, and Logcat content remains in memory by default.",
            SettingsStringKey.SUPPORT_TITLE to "Risks and supported devices",
            SettingsStringKey.SUPPORT_BODY to
                "The controller requires Android 11 (API 30) or newer. Standard Android 11+ wireless debugging is preferred, while legacy :5555 support is best effort. ADB Shell commands can change device data.",
            SettingsStringKey.LICENSES_TITLE to "Open-source and third-party licenses",
            SettingsStringKey.LICENSES_BODY to
                "This project: Apache-2.0; Kadb 2.1.1: Apache-2.0; AndroidX: Apache-2.0; Coroutines 1.10.2: Apache-2.0; Okio 3.17.0: Apache-2.0; Bouncy Castle 1.83: MIT; spake2-java 1.0.5: Apache-2.0; HiddenApiBypass 6.1: Apache-2.0.",
            SettingsStringKey.PAIRING_HELP_TITLE to "Wireless debugging and pairing help",
            SettingsStringKey.PAIRING_HELP_BODY to
                "Enable Developer options and Wireless debugging on the controlled device first. The connection page always tries the debugging port before using the pairing port and six-digit code after authentication fails. After pairing succeeds, return to the main Wireless debugging screen and enter the debugging port.",
            SettingsStringKey.OPEN_WIRELESS_DEBUGGING to "Open Wireless debugging settings",
            SettingsStringKey.OPEN_DEVELOPER_OPTIONS to "Open Developer options",
            SettingsStringKey.MANUAL_SETTINGS_PATH to
                "Open Settings → About phone → repeatedly tap the system version to enable Developer options → More settings → Developer options → Wireless debugging.",
            SettingsStringKey.CLEAR_ALL_DATA to "Clear all local data",
            SettingsStringKey.CLEARING_DATA to "Clearing and verifying…",
            SettingsStringKey.CLEAR_SUCCEEDED to
                "All local data was cleared and the old identity can no longer be used.",
            SettingsStringKey.CLEAR_FAILED to
                "Some local data could not be cleared. Restart the app and try again.",
            SettingsStringKey.CLEAR_CONFIRM_TITLE to "Clear all local data?",
            SettingsStringKey.CLEAR_CONFIRM_BODY to
                "This deletes device profiles, the ADB host identity, preferences, and temporary files. You may need to pair again, and this cannot be undone.",
            SettingsStringKey.CLEAR_CONFIRM to "Clear data",
            SettingsStringKey.CANCEL to "Cancel",
        ),
    )

    fun requiredKeys(language: UiLanguage): Set<SettingsStringKey> =
        catalogs.getValue(language).keys

    fun resolve(language: UiLanguage, key: SettingsStringKey): String =
        catalogs.getValue(language).getValue(key)

    fun keyFor(message: SettingsMessageCode): SettingsStringKey = when (message) {
        SettingsMessageCode.LANGUAGE_SAVE_FAILED -> SettingsStringKey.LANGUAGE_SAVE_FAILED
        SettingsMessageCode.MANUAL_SETTINGS_PATH -> SettingsStringKey.MANUAL_SETTINGS_PATH
        SettingsMessageCode.CLEAR_SUCCEEDED -> SettingsStringKey.CLEAR_SUCCEEDED
        SettingsMessageCode.CLEAR_FAILED -> SettingsStringKey.CLEAR_FAILED
    }
}
