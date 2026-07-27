package com.sheen.adb.feature.logcat

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class LogcatStringKey(
    val semanticKey: String,
    val placeholderSchema: Map<String, TextArgumentType> = emptyMap(),
) {
    TITLE("logcat.title"),
    START("logcat.start"),
    STOP("logcat.stop"),
    FILTER("logcat.filter"),
    CLEAR("logcat.clear"),
    DOWNLOAD("logcat.download"),

    NEVER_STARTED("logcat.lifecycle.never_started"),
    STARTING("logcat.lifecycle.starting"),
    COLLECTING("logcat.lifecycle.collecting"),
    STOPPED("logcat.lifecycle.stopped"),
    LIMIT_TIME("logcat.lifecycle.limit_time"),
    LIMIT_BYTES("logcat.lifecycle.limit_bytes"),
    ERROR("logcat.lifecycle.error"),
    CANCELLED("logcat.lifecycle.cancelled"),
    DISCONNECTED("logcat.lifecycle.disconnected"),
    UNSUPPORTED("logcat.lifecycle.unsupported"),
    OUTCOME_UNKNOWN("logcat.lifecycle.outcome_unknown"),
    SAVE_WRITING("logcat.save.writing"),
    SAVE_SUCCEEDED("logcat.save.succeeded"),
    SAVE_FAILED("logcat.save.failed"),
    SAVE_CANCELLED("logcat.save.cancelled"),

    START_CONTENT_DESCRIPTION("logcat.accessibility.start"),
    STOP_CONTENT_DESCRIPTION("logcat.accessibility.stop"),
    FILTER_CONTENT_DESCRIPTION("logcat.accessibility.filter"),
    CLEAR_CONTENT_DESCRIPTION("logcat.accessibility.clear"),
    DOWNLOAD_CONTENT_DESCRIPTION("logcat.accessibility.download"),
    LIVE_REGION_STATUS("logcat.accessibility.live_region_status"),

    VERBATIM_LOG_LINE(
        "logcat.verbatim.log_line",
        mapOf("logLine" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_TECHNICAL_CODE(
        "logcat.verbatim.technical_code",
        mapOf("technicalCode" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_LEVEL_LABEL(
        "logcat.verbatim.level_label",
        mapOf("levelLabel" to TextArgumentType.VERBATIM),
    ),
}

object LogcatStrings {
    const val OWNER = "feature_logcat"

    private val catalogs: Map<UiLanguage, Map<LogcatStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            LogcatStringKey.TITLE to "日志",
            LogcatStringKey.START to "开始",
            LogcatStringKey.STOP to "停止",
            LogcatStringKey.FILTER to "过滤日志",
            LogcatStringKey.CLEAR to "清空日志",
            LogcatStringKey.DOWNLOAD to "保存日志",
            LogcatStringKey.NEVER_STARTED to "点击开始后才会读取设备日志",
            LogcatStringKey.STARTING to "正在启动日志采集",
            LogcatStringKey.COLLECTING to "正在采集日志",
            LogcatStringKey.STOPPED to "日志采集已停止，当前快照已保留",
            LogcatStringKey.LIMIT_TIME to "本次日志采集已达到十分钟上限",
            LogcatStringKey.LIMIT_BYTES to "本次日志采集已达到 10 MiB 上限",
            LogcatStringKey.ERROR to "日志采集失败",
            LogcatStringKey.CANCELLED to "日志采集已取消",
            LogcatStringKey.DISCONNECTED to "设备已断开，日志窗口已清空",
            LogcatStringKey.UNSUPPORTED to "当前设备不支持此日志采集方式",
            LogcatStringKey.OUTCOME_UNKNOWN to "日志采集结果未知",
            LogcatStringKey.SAVE_WRITING to "正在写入日志文件",
            LogcatStringKey.SAVE_SUCCEEDED to "日志文件已保存",
            LogcatStringKey.SAVE_FAILED to "日志文件保存失败",
            LogcatStringKey.SAVE_CANCELLED to "日志保存已取消",
            LogcatStringKey.START_CONTENT_DESCRIPTION to "开始采集当前设备日志",
            LogcatStringKey.STOP_CONTENT_DESCRIPTION to "停止采集并保留当前日志快照",
            LogcatStringKey.FILTER_CONTENT_DESCRIPTION to "立即过滤屏幕上显示的日志",
            LogcatStringKey.CLEAR_CONTENT_DESCRIPTION to "清空本次完整日志窗口",
            LogcatStringKey.DOWNLOAD_CONTENT_DESCRIPTION to "将本次完整日志窗口保存到文件",
            LogcatStringKey.LIVE_REGION_STATUS to "日志状态已更新",
            LogcatStringKey.VERBATIM_LOG_LINE to "{logLine}",
            LogcatStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
            LogcatStringKey.VERBATIM_LEVEL_LABEL to "{levelLabel}",
        ),
        UiLanguage.EN_US to mapOf(
            LogcatStringKey.TITLE to "Logcat",
            LogcatStringKey.START to "Start",
            LogcatStringKey.STOP to "Stop",
            LogcatStringKey.FILTER to "Filter logs",
            LogcatStringKey.CLEAR to "Clear logs",
            LogcatStringKey.DOWNLOAD to "Save logs",
            LogcatStringKey.NEVER_STARTED to "Device logs are read only after you select Start",
            LogcatStringKey.STARTING to "Starting log collection",
            LogcatStringKey.COLLECTING to "Collecting logs",
            LogcatStringKey.STOPPED to "Log collection stopped and the current snapshot was retained",
            LogcatStringKey.LIMIT_TIME to "This collection reached the ten-minute limit",
            LogcatStringKey.LIMIT_BYTES to "This collection reached the 10 MiB limit",
            LogcatStringKey.ERROR to "Log collection failed",
            LogcatStringKey.CANCELLED to "Log collection cancelled",
            LogcatStringKey.DISCONNECTED to "The device disconnected and the log window was cleared",
            LogcatStringKey.UNSUPPORTED to "This log collection mode is unsupported on the device",
            LogcatStringKey.OUTCOME_UNKNOWN to "Log collection outcome is unknown",
            LogcatStringKey.SAVE_WRITING to "Writing log file",
            LogcatStringKey.SAVE_SUCCEEDED to "Log file saved",
            LogcatStringKey.SAVE_FAILED to "Log file save failed",
            LogcatStringKey.SAVE_CANCELLED to "Log save cancelled",
            LogcatStringKey.START_CONTENT_DESCRIPTION to "Start collecting logs from the current device",
            LogcatStringKey.STOP_CONTENT_DESCRIPTION to "Stop collection and retain the current log snapshot",
            LogcatStringKey.FILTER_CONTENT_DESCRIPTION to "Immediately filter the logs shown on screen",
            LogcatStringKey.CLEAR_CONTENT_DESCRIPTION to "Clear the complete current log window",
            LogcatStringKey.DOWNLOAD_CONTENT_DESCRIPTION to "Save the complete current log window to a file",
            LogcatStringKey.LIVE_REGION_STATUS to "Log status updated",
            LogcatStringKey.VERBATIM_LOG_LINE to "{logLine}",
            LogcatStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
            LogcatStringKey.VERBATIM_LEVEL_LABEL to "{levelLabel}",
        ),
    )

    private val fixedLevelLabels = listOf("all", "debug", "info", "error")

    init {
        val expectedKeys = LogcatStringKey.entries.toSet()
        UiLanguage.entries.forEach { language ->
            require(requiredKeys(language) == expectedKeys) {
                "Logcat catalog is incomplete for $language"
            }
        }
        require(requiredKeys(UiLanguage.ZH_CN) == requiredKeys(UiLanguage.EN_US)) {
            "Logcat catalogs must expose identical keys"
        }
        LogcatStringKey.entries.forEach { key ->
            require(
                placeholderSchema(UiLanguage.ZH_CN, key) ==
                    placeholderSchema(UiLanguage.EN_US, key),
            ) {
                "Logcat placeholder schema mismatch for ${key.semanticKey}"
            }
        }
    }

    fun requiredKeys(language: UiLanguage): Set<LogcatStringKey> =
        catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: LogcatStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return key.placeholderSchema
    }

    fun text(
        language: UiLanguage,
        key: LogcatStringKey,
    ): String {
        require(key.placeholderSchema.isEmpty()) { "Arguments required for ${key.semanticKey}" }
        return catalogs.getValue(language).getValue(key)
    }

    fun ref(
        key: LogcatStringKey,
        vararg arguments: TypedTextArgument,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = arguments.toList(),
    ).also { ref ->
        require(ref.arguments.associate { it.name to it.type } == key.placeholderSchema) {
            "Logcat text argument schema mismatch for ${key.semanticKey}"
        }
    }

    fun verbatimLogLine(logLine: String): LocalizedTextRef =
        verbatimRef(LogcatStringKey.VERBATIM_LOG_LINE, "logLine", logLine)

    fun verbatimTechnicalCode(technicalCode: String): LocalizedTextRef =
        verbatimRef(LogcatStringKey.VERBATIM_TECHNICAL_CODE, "technicalCode", technicalCode)

    fun fixedLevelLabelRef(levelLabel: String): LocalizedTextRef {
        require(levelLabel in fixedLevelLabels)
        return verbatimRef(LogcatStringKey.VERBATIM_LEVEL_LABEL, "levelLabel", levelLabel)
    }

    fun fixedLevelLabels(language: UiLanguage): List<String> {
        require(catalogs.containsKey(language))
        return fixedLevelLabels
    }

    fun resolve(
        language: UiLanguage,
        ref: LocalizedTextRef,
    ): String {
        require(ref.owner == OWNER) { "Text owner mismatch" }
        val key = LogcatStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown Logcat semantic key")
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.mapValues { it.value.type } == key.placeholderSchema) {
            "Logcat text argument schema mismatch"
        }
        return arguments.values.fold(catalogs.getValue(language).getValue(key)) { resolved, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> SafeVerbatimText.render(
                    raw = argument.value as String,
                    policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = MAX_LOG_LINE_CODE_POINTS),
                ).display
                TextArgumentType.TEXT,
                TextArgumentType.NUMBER,
                -> argument.value.toString()
            }
            resolved.replace("{${argument.name}}", value)
        }
    }

    private fun verbatimRef(
        key: LogcatStringKey,
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

    private const val MAX_LOG_LINE_CODE_POINTS = 4_096
}
