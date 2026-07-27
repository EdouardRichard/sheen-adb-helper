package com.sheen.adb.feature.shell

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class ShellStringKey(
    val semanticKey: String,
    val placeholderSchema: Map<String, TextArgumentType> = emptyMap(),
) {
    TITLE("shell.title"),
    CLEAR("shell.clear"),
    FILTER("shell.filter"),
    AUTO_SCROLL("shell.auto_scroll"),
    COMMAND_INPUT("shell.command_input"),
    SESSION_SEPARATOR("shell.session_separator"),

    RISK_CONFIRMATION_TITLE("shell.risk_confirmation.title"),
    RISK_CONFIRMATION_BODY("shell.risk_confirmation.body"),
    RISK_CONFIRMATION_SEND("shell.risk_confirmation.send"),
    RISK_CONFIRMATION_CANCEL("shell.risk_confirmation.cancel"),
    COMMAND_IDENTITY(
        "shell.command_identity",
        mapOf("command" to TextArgumentType.VERBATIM),
    ),

    STREAM_OPENING("shell.stream.opening"),
    STREAM_ACTIVE("shell.stream.active"),
    STREAM_CLOSED("shell.stream.closed"),
    STREAM_TIMEOUT("shell.stream.timeout"),
    STREAM_CANCELLED("shell.stream.cancelled"),
    STREAM_UNSUPPORTED("shell.stream.unsupported"),
    STREAM_DISCONNECTED("shell.stream.disconnected"),
    STREAM_PROTOCOL_FAILURE("shell.stream.protocol_failure"),
    STREAM_OUTPUT_LIMIT("shell.stream.output_limit"),
    STREAM_OUTCOME_UNKNOWN("shell.stream.outcome_unknown"),
    STREAM_CLEANUP_UNCERTAIN("shell.stream.cleanup_uncertain"),

    CLEAR_CONTENT_DESCRIPTION("shell.accessibility.clear"),
    FILTER_CONTENT_DESCRIPTION("shell.accessibility.filter"),
    AUTO_SCROLL_CONTENT_DESCRIPTION("shell.accessibility.auto_scroll"),
    ESC_CONTENT_DESCRIPTION("shell.accessibility.esc"),
    TAB_CONTENT_DESCRIPTION("shell.accessibility.tab"),
    CTRL_CONTENT_DESCRIPTION("shell.accessibility.ctrl"),
    ALT_CONTENT_DESCRIPTION("shell.accessibility.alt"),
    ARROW_UP_CONTENT_DESCRIPTION("shell.accessibility.arrow_up"),
    ARROW_DOWN_CONTENT_DESCRIPTION("shell.accessibility.arrow_down"),
    KEYBOARD_RETURN_CONTENT_DESCRIPTION("shell.accessibility.keyboard_return"),
    LIVE_REGION_STATUS("shell.accessibility.live_region_status"),

    VERBATIM_COMMAND(
        "shell.verbatim.command",
        mapOf("command" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_OUTPUT(
        "shell.verbatim.output",
        mapOf("output" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_TECHNICAL_CODE(
        "shell.verbatim.technical_code",
        mapOf("technicalCode" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_KEY_LABEL(
        "shell.verbatim.key_label",
        mapOf("label" to TextArgumentType.VERBATIM),
    ),
}

object ShellStrings {
    const val OWNER = "shell"

    private val catalogs: Map<UiLanguage, Map<ShellStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            ShellStringKey.TITLE to "终端",
            ShellStringKey.CLEAR to "清空",
            ShellStringKey.FILTER to "过滤",
            ShellStringKey.AUTO_SCROLL to "自动滚动",
            ShellStringKey.COMMAND_INPUT to "输入命令",
            ShellStringKey.SESSION_SEPARATOR to "新的终端会话",
            ShellStringKey.RISK_CONFIRMATION_TITLE to "确认发送高风险命令？",
            ShellStringKey.RISK_CONFIRMATION_BODY to
                "此命令可能修改设备状态或中断任务。请核对目标和影响后再发送。",
            ShellStringKey.RISK_CONFIRMATION_SEND to "发送命令",
            ShellStringKey.RISK_CONFIRMATION_CANCEL to "取消",
            ShellStringKey.COMMAND_IDENTITY to "命令：{command}",
            ShellStringKey.STREAM_OPENING to "正在打开终端子流",
            ShellStringKey.STREAM_ACTIVE to "终端子流已连接",
            ShellStringKey.STREAM_CLOSED to "终端子流已关闭",
            ShellStringKey.STREAM_TIMEOUT to "终端子流已超时",
            ShellStringKey.STREAM_CANCELLED to "终端操作已取消",
            ShellStringKey.STREAM_UNSUPPORTED to "当前设备不支持持续交互式终端",
            ShellStringKey.STREAM_DISCONNECTED to "设备已断开，终端子流已关闭",
            ShellStringKey.STREAM_PROTOCOL_FAILURE to "终端协议通信失败",
            ShellStringKey.STREAM_OUTPUT_LIMIT to "终端输出已达到本次会话上限",
            ShellStringKey.STREAM_OUTCOME_UNKNOWN to "终端操作结果未知",
            ShellStringKey.STREAM_CLEANUP_UNCERTAIN to "终端子流清理状态无法确认",
            ShellStringKey.CLEAR_CONTENT_DESCRIPTION to "清空本地终端记录",
            ShellStringKey.FILTER_CONTENT_DESCRIPTION to "过滤本地终端记录",
            ShellStringKey.AUTO_SCROLL_CONTENT_DESCRIPTION to "切换终端自动滚动",
            ShellStringKey.ESC_CONTENT_DESCRIPTION to "输入 Esc 键",
            ShellStringKey.TAB_CONTENT_DESCRIPTION to "输入 Tab 键",
            ShellStringKey.CTRL_CONTENT_DESCRIPTION to "切换下一次输入的 Ctrl 修饰",
            ShellStringKey.ALT_CONTENT_DESCRIPTION to "切换下一次输入的 Alt 修饰",
            ShellStringKey.ARROW_UP_CONTENT_DESCRIPTION to "输入方向上键",
            ShellStringKey.ARROW_DOWN_CONTENT_DESCRIPTION to "输入方向下键",
            ShellStringKey.KEYBOARD_RETURN_CONTENT_DESCRIPTION to "聚焦命令输入并打开输入法",
            ShellStringKey.LIVE_REGION_STATUS to "终端状态已更新",
            ShellStringKey.VERBATIM_COMMAND to "{command}",
            ShellStringKey.VERBATIM_OUTPUT to "{output}",
            ShellStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
            ShellStringKey.VERBATIM_KEY_LABEL to "{label}",
        ),
        UiLanguage.EN_US to mapOf(
            ShellStringKey.TITLE to "Terminal",
            ShellStringKey.CLEAR to "Clear",
            ShellStringKey.FILTER to "Filter",
            ShellStringKey.AUTO_SCROLL to "Auto-scroll",
            ShellStringKey.COMMAND_INPUT to "Enter a command",
            ShellStringKey.SESSION_SEPARATOR to "New terminal session",
            ShellStringKey.RISK_CONFIRMATION_TITLE to "Send high-risk command?",
            ShellStringKey.RISK_CONFIRMATION_BODY to
                "This command may change device state or interrupt work. Review its target and impact before sending.",
            ShellStringKey.RISK_CONFIRMATION_SEND to "Send command",
            ShellStringKey.RISK_CONFIRMATION_CANCEL to "Cancel",
            ShellStringKey.COMMAND_IDENTITY to "Command: {command}",
            ShellStringKey.STREAM_OPENING to "Opening terminal child stream",
            ShellStringKey.STREAM_ACTIVE to "Terminal child stream connected",
            ShellStringKey.STREAM_CLOSED to "Terminal child stream closed",
            ShellStringKey.STREAM_TIMEOUT to "Terminal child stream timed out",
            ShellStringKey.STREAM_CANCELLED to "Terminal operation cancelled",
            ShellStringKey.STREAM_UNSUPPORTED to "Interactive terminal is unsupported on this device",
            ShellStringKey.STREAM_DISCONNECTED to "The device disconnected and the terminal child stream closed",
            ShellStringKey.STREAM_PROTOCOL_FAILURE to "Terminal protocol communication failed",
            ShellStringKey.STREAM_OUTPUT_LIMIT to "Terminal output reached this session's limit",
            ShellStringKey.STREAM_OUTCOME_UNKNOWN to "Terminal operation outcome is unknown",
            ShellStringKey.STREAM_CLEANUP_UNCERTAIN to "Terminal child-stream cleanup could not be confirmed",
            ShellStringKey.CLEAR_CONTENT_DESCRIPTION to "Clear local terminal records",
            ShellStringKey.FILTER_CONTENT_DESCRIPTION to "Filter local terminal records",
            ShellStringKey.AUTO_SCROLL_CONTENT_DESCRIPTION to "Toggle terminal auto-scroll",
            ShellStringKey.ESC_CONTENT_DESCRIPTION to "Input the Esc key",
            ShellStringKey.TAB_CONTENT_DESCRIPTION to "Input the Tab key",
            ShellStringKey.CTRL_CONTENT_DESCRIPTION to "Modify the next input with Ctrl",
            ShellStringKey.ALT_CONTENT_DESCRIPTION to "Modify the next input with Alt",
            ShellStringKey.ARROW_UP_CONTENT_DESCRIPTION to "Input the up arrow key",
            ShellStringKey.ARROW_DOWN_CONTENT_DESCRIPTION to "Input the down arrow key",
            ShellStringKey.KEYBOARD_RETURN_CONTENT_DESCRIPTION to "Focus command input and open the keyboard",
            ShellStringKey.LIVE_REGION_STATUS to "Terminal status updated",
            ShellStringKey.VERBATIM_COMMAND to "{command}",
            ShellStringKey.VERBATIM_OUTPUT to "{output}",
            ShellStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
            ShellStringKey.VERBATIM_KEY_LABEL to "{label}",
        ),
    )

    private val fixedKeyLabels = listOf("Esc", "Tab", "Ctrl", "Alt")

    init {
        val expectedKeys = ShellStringKey.entries.toSet()
        UiLanguage.entries.forEach { language ->
            require(requiredKeys(language) == expectedKeys) {
                "Shell catalog is incomplete for $language"
            }
        }
        require(requiredKeys(UiLanguage.ZH_CN) == requiredKeys(UiLanguage.EN_US)) {
            "Shell catalogs must expose identical keys"
        }
        ShellStringKey.entries.forEach { key ->
            require(
                placeholderSchema(UiLanguage.ZH_CN, key) ==
                    placeholderSchema(UiLanguage.EN_US, key),
            ) {
                "Shell placeholder schema mismatch for ${key.semanticKey}"
            }
        }
    }

    fun requiredKeys(language: UiLanguage): Set<ShellStringKey> =
        catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: ShellStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return key.placeholderSchema
    }

    fun text(
        language: UiLanguage,
        key: ShellStringKey,
    ): String {
        require(key.placeholderSchema.isEmpty()) { "Arguments required for ${key.semanticKey}" }
        return catalogs.getValue(language).getValue(key)
    }

    fun ref(
        key: ShellStringKey,
        vararg arguments: TypedTextArgument,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = arguments.toList(),
    ).also { ref ->
        require(ref.arguments.associate { it.name to it.type } == key.placeholderSchema) {
            "Shell text argument schema mismatch for ${key.semanticKey}"
        }
    }

    fun verbatimCommand(command: String): LocalizedTextRef =
        verbatimRef(ShellStringKey.VERBATIM_COMMAND, "command", command)

    fun verbatimOutput(output: String): LocalizedTextRef =
        verbatimRef(ShellStringKey.VERBATIM_OUTPUT, "output", output)

    fun verbatimTechnicalCode(technicalCode: String): LocalizedTextRef =
        verbatimRef(ShellStringKey.VERBATIM_TECHNICAL_CODE, "technicalCode", technicalCode)

    fun fixedKeyLabelRef(label: String): LocalizedTextRef {
        require(label in fixedKeyLabels)
        return verbatimRef(ShellStringKey.VERBATIM_KEY_LABEL, "label", label)
    }

    fun fixedKeyLabels(language: UiLanguage): List<String> {
        require(catalogs.containsKey(language))
        return fixedKeyLabels
    }

    fun resolve(
        language: UiLanguage,
        ref: LocalizedTextRef,
    ): String {
        require(ref.owner == OWNER) { "Text owner mismatch" }
        val key = ShellStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown Shell semantic key")
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.mapValues { it.value.type } == key.placeholderSchema) {
            "Shell text argument schema mismatch"
        }
        return arguments.values.fold(catalogs.getValue(language).getValue(key)) { resolved, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> safeVerbatim(
                    key = key,
                    raw = argument.value as String,
                )
                TextArgumentType.TEXT,
                TextArgumentType.NUMBER,
                -> argument.value.toString()
            }
            resolved.replace("{${argument.name}}", value)
        }
    }

    private fun verbatimRef(
        key: ShellStringKey,
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

    private fun safeVerbatim(
        key: ShellStringKey,
        raw: String,
    ): String {
        val policy = if (key == ShellStringKey.VERBATIM_OUTPUT) {
            SafeVerbatimPolicy.MultiLine(maxCodePoints = MAX_OUTPUT_DISPLAY_CODE_POINTS)
        } else {
            SafeVerbatimPolicy.SingleLine(maxCodePoints = MAX_SINGLE_LINE_CODE_POINTS)
        }
        return SafeVerbatimText.render(raw = raw, policy = policy).display
    }

    private const val MAX_SINGLE_LINE_CODE_POINTS = 512
    private const val MAX_OUTPUT_DISPLAY_CODE_POINTS = 16_384
}
