package com.sheen.adb.feature.overview

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class OverviewStringKey(
    val semanticKey: String,
    val placeholderSchema: Map<String, TextArgumentType> = emptyMap(),
) {
    TITLE("overview.title"),
    QUICK_ACTIONS("overview.quick_actions"),
    SCREENSHOT("overview.action.screenshot"),
    SCREEN_RECORD("overview.action.screen_record"),
    STOP_SCREEN_RECORD("overview.action.stop_screen_record"),
    REBOOT("overview.action.reboot"),

    SCREENSHOT_CONTENT_DESCRIPTION("overview.accessibility.screenshot"),
    SCREEN_RECORD_CONTENT_DESCRIPTION("overview.accessibility.screen_record"),
    STOP_SCREEN_RECORD_CONTENT_DESCRIPTION("overview.accessibility.stop_screen_record"),
    REBOOT_CONTENT_DESCRIPTION("overview.accessibility.reboot"),
    SAVE_CONTENT_DESCRIPTION("overview.accessibility.save"),
    SAVE_STATUS_LIVE_ANNOUNCEMENT("overview.accessibility.save_status"),

    REBOOT_CONFIRMATION("overview.reboot.confirmation"),
    REBOOT_RISK("overview.reboot.risk"),
    CONFIRM_REBOOT("overview.reboot.confirm"),
    CANCEL_REBOOT("overview.reboot.cancel"),

    CAPTURING_SCREENSHOT("overview.status.capturing_screenshot"),
    SCREEN_RECORDING("overview.status.screen_recording"),
    FINALIZING_SCREEN_RECORD("overview.status.finalizing_screen_record"),
    REBOOT_REQUESTED("overview.status.reboot_requested"),
    AWAITING_DESTINATION("overview.save.awaiting_destination"),
    SAVE_SCREENSHOT("overview.save.screenshot"),
    SAVE_SCREEN_RECORD("overview.save.screen_record"),
    SAVE_WRITING("overview.save.writing"),
    SAVE_CANCELLING("overview.save.cancelling"),
    SAVE_CLEANING("overview.save.cleaning"),
    SAVE_SUCCEEDED("overview.save.succeeded"),
    SAVE_FAILED("overview.save.failed"),
    SAVE_CANCELLED("overview.save.cancelled"),

    DISCONNECTED("overview.result.disconnected"),
    UNSUPPORTED("overview.result.unsupported"),
    OUTCOME_UNKNOWN("overview.result.outcome_unknown"),

    ARTIFACT_LABEL(
        "overview.verbatim.artifact_label",
        mapOf("artifactLabel" to TextArgumentType.VERBATIM),
    ),
    TECHNICAL_CODE(
        "overview.verbatim.technical_code",
        mapOf("technicalCode" to TextArgumentType.VERBATIM),
    ),
}

object OverviewStrings {
    const val OWNER = "overview"

    private val catalogs: Map<UiLanguage, Map<OverviewStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            OverviewStringKey.TITLE to "设备概览",
            OverviewStringKey.QUICK_ACTIONS to "快捷操作",
            OverviewStringKey.SCREENSHOT to "截屏",
            OverviewStringKey.SCREEN_RECORD to "录屏",
            OverviewStringKey.STOP_SCREEN_RECORD to "停止录屏",
            OverviewStringKey.REBOOT to "重启",
            OverviewStringKey.SCREENSHOT_CONTENT_DESCRIPTION to "截取被控端屏幕",
            OverviewStringKey.SCREEN_RECORD_CONTENT_DESCRIPTION to "开始录制被控端屏幕",
            OverviewStringKey.STOP_SCREEN_RECORD_CONTENT_DESCRIPTION to "停止录屏并生成可保存文件",
            OverviewStringKey.REBOOT_CONTENT_DESCRIPTION to "请求重启被控端",
            OverviewStringKey.SAVE_CONTENT_DESCRIPTION to "将生成的文件保存到主控端",
            OverviewStringKey.SAVE_STATUS_LIVE_ANNOUNCEMENT to "文件保存状态已更新",
            OverviewStringKey.REBOOT_CONFIRMATION to "确认重启设备？",
            OverviewStringKey.REBOOT_RISK to "设备会立即断开连接，未保存的工作可能丢失。",
            OverviewStringKey.CONFIRM_REBOOT to "确认重启",
            OverviewStringKey.CANCEL_REBOOT to "取消",
            OverviewStringKey.CAPTURING_SCREENSHOT to "正在截取屏幕",
            OverviewStringKey.SCREEN_RECORDING to "正在录制屏幕",
            OverviewStringKey.FINALIZING_SCREEN_RECORD to "正在停止并生成录像",
            OverviewStringKey.REBOOT_REQUESTED to "重启请求已发送",
            OverviewStringKey.AWAITING_DESTINATION to "请选择保存位置",
            OverviewStringKey.SAVE_SCREENSHOT to "保存截屏",
            OverviewStringKey.SAVE_SCREEN_RECORD to "保存录屏",
            OverviewStringKey.SAVE_WRITING to "正在写入文件",
            OverviewStringKey.SAVE_CANCELLING to "正在取消保存",
            OverviewStringKey.SAVE_CLEANING to "正在清理临时文件",
            OverviewStringKey.SAVE_SUCCEEDED to "文件已保存",
            OverviewStringKey.SAVE_FAILED to "文件保存失败",
            OverviewStringKey.SAVE_CANCELLED to "文件保存已取消",
            OverviewStringKey.DISCONNECTED to "设备已断开，请重新连接",
            OverviewStringKey.UNSUPPORTED to "当前设备不支持此操作",
            OverviewStringKey.OUTCOME_UNKNOWN to "操作结果未知，请在设备状态恢复后确认",
            OverviewStringKey.ARTIFACT_LABEL to "{artifactLabel}",
            OverviewStringKey.TECHNICAL_CODE to "{technicalCode}",
        ),
        UiLanguage.EN_US to mapOf(
            OverviewStringKey.TITLE to "Device overview",
            OverviewStringKey.QUICK_ACTIONS to "Quick actions",
            OverviewStringKey.SCREENSHOT to "Screenshot",
            OverviewStringKey.SCREEN_RECORD to "Screen record",
            OverviewStringKey.STOP_SCREEN_RECORD to "Stop recording",
            OverviewStringKey.REBOOT to "Reboot",
            OverviewStringKey.SCREENSHOT_CONTENT_DESCRIPTION to "Capture the controlled device screen",
            OverviewStringKey.SCREEN_RECORD_CONTENT_DESCRIPTION to
                "Start recording the controlled device screen",
            OverviewStringKey.STOP_SCREEN_RECORD_CONTENT_DESCRIPTION to
                "Stop recording and prepare a file to save",
            OverviewStringKey.REBOOT_CONTENT_DESCRIPTION to "Request a reboot of the controlled device",
            OverviewStringKey.SAVE_CONTENT_DESCRIPTION to "Save the generated file to the controller",
            OverviewStringKey.SAVE_STATUS_LIVE_ANNOUNCEMENT to "File save status updated",
            OverviewStringKey.REBOOT_CONFIRMATION to "Reboot this device?",
            OverviewStringKey.REBOOT_RISK to
                "The device will disconnect immediately and unsaved work may be lost.",
            OverviewStringKey.CONFIRM_REBOOT to "Reboot",
            OverviewStringKey.CANCEL_REBOOT to "Cancel",
            OverviewStringKey.CAPTURING_SCREENSHOT to "Capturing screenshot",
            OverviewStringKey.SCREEN_RECORDING to "Recording screen",
            OverviewStringKey.FINALIZING_SCREEN_RECORD to "Stopping and finalizing recording",
            OverviewStringKey.REBOOT_REQUESTED to "Reboot requested",
            OverviewStringKey.AWAITING_DESTINATION to "Choose a save location",
            OverviewStringKey.SAVE_SCREENSHOT to "Save screenshot",
            OverviewStringKey.SAVE_SCREEN_RECORD to "Save screen recording",
            OverviewStringKey.SAVE_WRITING to "Writing file",
            OverviewStringKey.SAVE_CANCELLING to "Cancelling save",
            OverviewStringKey.SAVE_CLEANING to "Cleaning temporary files",
            OverviewStringKey.SAVE_SUCCEEDED to "File saved",
            OverviewStringKey.SAVE_FAILED to "File save failed",
            OverviewStringKey.SAVE_CANCELLED to "File save cancelled",
            OverviewStringKey.DISCONNECTED to "The device disconnected. Reconnect to continue.",
            OverviewStringKey.UNSUPPORTED to "This operation is unsupported by the device",
            OverviewStringKey.OUTCOME_UNKNOWN to
                "The operation outcome is unknown. Verify after the device state recovers.",
            OverviewStringKey.ARTIFACT_LABEL to "{artifactLabel}",
            OverviewStringKey.TECHNICAL_CODE to "{technicalCode}",
        ),
    )

    init {
        val expectedKeys = OverviewStringKey.entries.toSet()
        UiLanguage.entries.forEach { language ->
            require(requiredKeys(language) == expectedKeys) {
                "Overview catalog is incomplete for $language"
            }
        }
        require(requiredKeys(UiLanguage.ZH_CN) == requiredKeys(UiLanguage.EN_US)) {
            "Overview catalogs must expose identical keys"
        }
        OverviewStringKey.entries.forEach { key ->
            require(
                placeholderSchema(UiLanguage.ZH_CN, key) ==
                    placeholderSchema(UiLanguage.EN_US, key),
            ) {
                "Overview placeholder schema mismatch for ${key.semanticKey}"
            }
        }
    }

    fun requiredKeys(language: UiLanguage): Set<OverviewStringKey> =
        catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: OverviewStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return key.placeholderSchema
    }

    fun text(
        language: UiLanguage,
        key: OverviewStringKey,
    ): String {
        require(key.placeholderSchema.isEmpty()) { "Arguments required for ${key.semanticKey}" }
        return catalogs.getValue(language).getValue(key)
    }

    fun ref(
        key: OverviewStringKey,
        vararg arguments: TypedTextArgument,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = arguments.toList(),
    ).also { ref ->
        require(ref.arguments.associate { it.name to it.type } == key.placeholderSchema) {
            "Overview text argument schema mismatch for ${key.semanticKey}"
        }
    }

    fun artifactLabel(artifactLabel: String): LocalizedTextRef =
        verbatimRef(OverviewStringKey.ARTIFACT_LABEL, "artifactLabel", artifactLabel)

    fun technicalCode(technicalCode: String): LocalizedTextRef =
        verbatimRef(OverviewStringKey.TECHNICAL_CODE, "technicalCode", technicalCode)

    fun resolve(
        language: UiLanguage,
        ref: LocalizedTextRef,
    ): String {
        require(ref.owner == OWNER) { "Text owner mismatch" }
        val key = OverviewStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown Overview semantic key")
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.mapValues { it.value.type } == key.placeholderSchema) {
            "Overview text argument schema mismatch"
        }
        return arguments.values.fold(catalogs.getValue(language).getValue(key)) { resolved, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> SafeVerbatimText.render(
                    raw = argument.value as String,
                    policy = SafeVerbatimPolicy.SingleLine(
                        maxCodePoints = if (argument.name == "technicalCode") {
                            MAX_TECHNICAL_CODE_POINTS
                        } else {
                            MAX_ARTIFACT_LABEL_CODE_POINTS
                        },
                    ),
                ).display
                TextArgumentType.TEXT,
                TextArgumentType.NUMBER,
                -> argument.value.toString()
            }
            resolved.replace("{${argument.name}}", value)
        }
    }

    private fun verbatimRef(
        key: OverviewStringKey,
        name: String,
        raw: String,
    ): LocalizedTextRef = ref(
        key,
        TypedTextArgument(
            name = name,
            value = raw,
            type = TextArgumentType.VERBATIM,
        ),
    )

    private const val MAX_ARTIFACT_LABEL_CODE_POINTS = 128
    private const val MAX_TECHNICAL_CODE_POINTS = 64
}
