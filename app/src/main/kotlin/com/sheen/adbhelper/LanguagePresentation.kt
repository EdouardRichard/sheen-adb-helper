package com.sheen.adbhelper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sheen.adb.data.LanguagePreference
import com.sheen.adb.ui.UiLanguage

fun LanguagePreference.toUiLanguage(): UiLanguage = when (this) {
    LanguagePreference.ZH_CN -> UiLanguage.ZH_CN
    LanguagePreference.EN_US -> UiLanguage.EN_US
}

val LocalV1UiLanguage = staticCompositionLocalOf { UiLanguage.ZH_CN }

@Composable
fun V1LanguageBoundary(
    language: UiLanguage,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalV1UiLanguage provides language,
        content = content,
    )
}

fun deliveryKindLabel(
    language: UiLanguage,
    kind: DeliveryKind,
): String = when (language) {
    UiLanguage.ZH_CN -> when (kind) {
        DeliveryKind.FILE_UPLOAD -> "文件上传"
        DeliveryKind.FILE_DOWNLOAD -> "文件下载"
        DeliveryKind.APK_EXTRACTION -> "APK 提取"
        DeliveryKind.APK_INSTALLATION -> "APK 安装"
        DeliveryKind.LOGCAT_SAVE -> "日志保存"
        DeliveryKind.SCREENSHOT_SAVE -> "截屏保存"
        DeliveryKind.SCREEN_RECORDING_SAVE_OR_EXPORT -> "录屏保存或导出"
    }
    UiLanguage.EN_US -> when (kind) {
        DeliveryKind.FILE_UPLOAD -> "File upload"
        DeliveryKind.FILE_DOWNLOAD -> "File download"
        DeliveryKind.APK_EXTRACTION -> "APK extraction"
        DeliveryKind.APK_INSTALLATION -> "APK installation"
        DeliveryKind.LOGCAT_SAVE -> "Log save"
        DeliveryKind.SCREENSHOT_SAVE -> "Screenshot save"
        DeliveryKind.SCREEN_RECORDING_SAVE_OR_EXPORT -> "Screen recording save or export"
    }
}
