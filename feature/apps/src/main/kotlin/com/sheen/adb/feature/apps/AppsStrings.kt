package com.sheen.adb.feature.apps

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class AppsStringKey(val semanticKey: String) {
    TITLE("apps.title"),
    SEARCH_PLACEHOLDER("apps.search_placeholder"),
    UNKNOWN_APP_NAME("apps.unknown_app_name"),
    EXTRACT_APK("apps.extract_apk"),
    DISABLE_APP("apps.disable_app"),
    ENABLE_APP("apps.enable_app"),
    FORCE_STOP_APP("apps.force_stop_app"),
    UNINSTALL_APP("apps.uninstall_app"),
    INSTALL_APK("apps.install_apk"),
    UNINSTALL_CONFIRMATION("apps.uninstall_confirmation"),
    UNINSTALL_PRIVATE_DATA_WARNING("apps.uninstall_private_data_warning"),
    FORCE_STOP_CONFIRMATION("apps.force_stop_confirmation"),
    FORCE_INSTALL_CONFIRMATION("apps.force_install_confirmation"),
    FORCE_INSTALL("apps.force_install"),
    SIGNATURE_MISMATCH_CONFIRMATION("apps.signature_mismatch_confirmation"),
    UNINSTALLING_OLD("apps.uninstalling_old"),
    INSTALLING_NEW("apps.installing_new"),
    COMPONENT_SUCCEEDED("apps.component_succeeded"),
    COMPONENT_FAILED("apps.component_failed"),
    COMPLETE_SUCCESS("apps.complete_success"),
    INSTALL_SUCCESS("apps.install_success"),
    PARTIAL_SUCCESS("apps.partial_success"),
    NONE_COMMITTED("apps.none_committed"),
    PROGRESS("apps.progress"),
    CANCELLED("apps.cancelled"),
    UNSUPPORTED("apps.unsupported"),
    OUTCOME_UNKNOWN("apps.outcome_unknown"),
    ERROR("apps.error"),
    RETRY("apps.retry"),
    CANCEL("apps.cancel"),
    CONFIRM("apps.confirm"),
    CONTENT_DESCRIPTION("apps.content_description"),
}

object AppsStrings {
    const val OWNER = "apps"

    private val catalogs = mapOf(
        UiLanguage.ZH_CN to mapOf(
            AppsStringKey.TITLE to "应用管理",
            AppsStringKey.SEARCH_PLACEHOLDER to "请输入应用名或包名。",
            AppsStringKey.UNKNOWN_APP_NAME to "未知应用",
            AppsStringKey.EXTRACT_APK to "提取 APK",
            AppsStringKey.DISABLE_APP to "禁用应用",
            AppsStringKey.ENABLE_APP to "启用应用",
            AppsStringKey.FORCE_STOP_APP to "强制停止应用",
            AppsStringKey.UNINSTALL_APP to "卸载应用",
            AppsStringKey.INSTALL_APK to "安装 APK",
            AppsStringKey.UNINSTALL_CONFIRMATION to "确认卸载此应用？",
            AppsStringKey.UNINSTALL_PRIVATE_DATA_WARNING to "卸载将删除当前用户下的应用私有数据。",
            AppsStringKey.FORCE_STOP_CONFIRMATION to "确认强制停止此应用？",
            AppsStringKey.FORCE_INSTALL_CONFIRMATION to "设备已安装此应用，是否强制安装？",
            AppsStringKey.FORCE_INSTALL to "强制安装",
            AppsStringKey.SIGNATURE_MISMATCH_CONFIRMATION to "签名不一致。继续将先卸载旧应用并删除其私有数据。",
            AppsStringKey.UNINSTALLING_OLD to "正在卸载旧应用",
            AppsStringKey.INSTALLING_NEW to "正在安装新应用",
            AppsStringKey.COMPONENT_SUCCEEDED to "组件已保存",
            AppsStringKey.COMPONENT_FAILED to "组件保存失败",
            AppsStringKey.COMPLETE_SUCCESS to "所有 APK 组件均已保存",
            AppsStringKey.INSTALL_SUCCESS to "APK 安装完成",
            AppsStringKey.PARTIAL_SUCCESS to "部分 APK 组件已保存",
            AppsStringKey.NONE_COMMITTED to "没有 APK 组件成功保存",
            AppsStringKey.PROGRESS to "正在处理",
            AppsStringKey.CANCELLED to "操作已取消",
            AppsStringKey.UNSUPPORTED to "当前设备或文件不支持此操作",
            AppsStringKey.OUTCOME_UNKNOWN to "结果未知，请检查设备和输出位置",
            AppsStringKey.ERROR to "应用操作失败",
            AppsStringKey.RETRY to "重试",
            AppsStringKey.CANCEL to "取消",
            AppsStringKey.CONFIRM to "确认",
            AppsStringKey.CONTENT_DESCRIPTION to "应用操作",
        ),
        UiLanguage.EN_US to mapOf(
            AppsStringKey.TITLE to "Applications",
            AppsStringKey.SEARCH_PLACEHOLDER to "Enter an app name or package name.",
            AppsStringKey.UNKNOWN_APP_NAME to "Unknown application",
            AppsStringKey.EXTRACT_APK to "Extract APK",
            AppsStringKey.DISABLE_APP to "Disable application",
            AppsStringKey.ENABLE_APP to "Enable application",
            AppsStringKey.FORCE_STOP_APP to "Force stop application",
            AppsStringKey.UNINSTALL_APP to "Uninstall application",
            AppsStringKey.INSTALL_APK to "Install APK",
            AppsStringKey.UNINSTALL_CONFIRMATION to "Uninstall this application?",
            AppsStringKey.UNINSTALL_PRIVATE_DATA_WARNING to "Uninstalling deletes this user's private app data.",
            AppsStringKey.FORCE_STOP_CONFIRMATION to "Force stop this application?",
            AppsStringKey.FORCE_INSTALL_CONFIRMATION to "This application is installed. Force installation?",
            AppsStringKey.FORCE_INSTALL to "Force install",
            AppsStringKey.SIGNATURE_MISMATCH_CONFIRMATION to "Signatures differ. Continuing removes the old app and its private data first.",
            AppsStringKey.UNINSTALLING_OLD to "Uninstalling old application",
            AppsStringKey.INSTALLING_NEW to "Installing new application",
            AppsStringKey.COMPONENT_SUCCEEDED to "Component saved",
            AppsStringKey.COMPONENT_FAILED to "Component failed",
            AppsStringKey.COMPLETE_SUCCESS to "All APK components were saved",
            AppsStringKey.INSTALL_SUCCESS to "APK installation complete",
            AppsStringKey.PARTIAL_SUCCESS to "Some APK components were saved",
            AppsStringKey.NONE_COMMITTED to "No APK components were saved",
            AppsStringKey.PROGRESS to "Processing",
            AppsStringKey.CANCELLED to "Operation cancelled",
            AppsStringKey.UNSUPPORTED to "This operation is unsupported by the device or file",
            AppsStringKey.OUTCOME_UNKNOWN to "Result unknown. Check the device and output location",
            AppsStringKey.ERROR to "Application operation failed",
            AppsStringKey.RETRY to "Retry",
            AppsStringKey.CANCEL to "Cancel",
            AppsStringKey.CONFIRM to "Confirm",
            AppsStringKey.CONTENT_DESCRIPTION to "Application action",
        ),
    )

    fun text(language: UiLanguage, key: AppsStringKey): String =
        catalogs.getValue(language).getValue(key)

    fun targetRef(
        key: AppsStringKey,
        packageName: String,
        applicationName: String,
        technicalCode: String,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = listOf(
            TypedTextArgument("packageName", packageName, TextArgumentType.VERBATIM),
            TypedTextArgument("applicationName", applicationName, TextArgumentType.VERBATIM),
            TypedTextArgument("technicalCode", technicalCode, TextArgumentType.VERBATIM),
        ),
    )

    fun safeSingleLine(value: String, maxCodePoints: Int = 160): String =
        SafeVerbatimText.render(
            value,
            SafeVerbatimPolicy.SingleLine(maxCodePoints),
        ).display
}
