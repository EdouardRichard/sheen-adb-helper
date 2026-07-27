package com.sheen.adb.ui

enum class V1SharedStringKey(
    val semanticKey: String,
    val placeholderSchema: Map<String, TextArgumentType> = emptyMap(),
) {
    NAV_CONNECTION("nav.connection"),
    NAV_FILES("nav.files"),
    NAV_APPLICATIONS("nav.applications"),
    NAV_PROCESSES("nav.processes"),
    NAV_SHELL("nav.shell"),
    NAV_LOGCAT("nav.logcat"),
    ACTION_CANCEL("action.cancel"),
    ACTION_RETRY("action.retry"),
    ACTION_CLOSE("action.close"),
    ACTION_CONFIRM("action.confirm"),
    ACTION_START("action.start"),
    STATE_LOADING("state.loading"),
    STATE_EMPTY("state.empty"),
    STATE_ERROR("state.error"),
    STATE_CANCELLED("state.cancelled"),
    STATE_DISCONNECTED("state.disconnected"),
    STATE_UNSUPPORTED("state.unsupported"),
    STATE_OUTCOME_UNKNOWN("state.outcome_unknown"),
    VALUE_UNKNOWN("value.unknown"),
    VALUE_UNAVAILABLE("value.unavailable"),
    TECHNICAL_CODE(
        "technical.code",
        mapOf("code" to TextArgumentType.VERBATIM),
    ),
}

object V1SharedStrings {
    const val OWNER = "shared"

    private val catalogs: Map<UiLanguage, Map<V1SharedStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            V1SharedStringKey.NAV_CONNECTION to "连接",
            V1SharedStringKey.NAV_FILES to "文件",
            V1SharedStringKey.NAV_APPLICATIONS to "应用",
            V1SharedStringKey.NAV_PROCESSES to "进程",
            V1SharedStringKey.NAV_SHELL to "终端",
            V1SharedStringKey.NAV_LOGCAT to "日志",
            V1SharedStringKey.ACTION_CANCEL to "取消",
            V1SharedStringKey.ACTION_RETRY to "重试",
            V1SharedStringKey.ACTION_CLOSE to "关闭",
            V1SharedStringKey.ACTION_CONFIRM to "确认",
            V1SharedStringKey.ACTION_START to "开始",
            V1SharedStringKey.STATE_LOADING to "正在加载",
            V1SharedStringKey.STATE_EMPTY to "暂无内容",
            V1SharedStringKey.STATE_ERROR to "操作失败",
            V1SharedStringKey.STATE_CANCELLED to "操作已取消",
            V1SharedStringKey.STATE_DISCONNECTED to "设备已断开",
            V1SharedStringKey.STATE_UNSUPPORTED to "当前设备不支持此功能",
            V1SharedStringKey.STATE_OUTCOME_UNKNOWN to "操作结果未知",
            V1SharedStringKey.VALUE_UNKNOWN to "未知",
            V1SharedStringKey.VALUE_UNAVAILABLE to "不可用",
            V1SharedStringKey.TECHNICAL_CODE to "技术代码：{code}",
        ),
        UiLanguage.EN_US to mapOf(
            V1SharedStringKey.NAV_CONNECTION to "Connection",
            V1SharedStringKey.NAV_FILES to "Files",
            V1SharedStringKey.NAV_APPLICATIONS to "Apps",
            V1SharedStringKey.NAV_PROCESSES to "Processes",
            V1SharedStringKey.NAV_SHELL to "Shell",
            V1SharedStringKey.NAV_LOGCAT to "Logcat",
            V1SharedStringKey.ACTION_CANCEL to "Cancel",
            V1SharedStringKey.ACTION_RETRY to "Retry",
            V1SharedStringKey.ACTION_CLOSE to "Close",
            V1SharedStringKey.ACTION_CONFIRM to "Confirm",
            V1SharedStringKey.ACTION_START to "Start",
            V1SharedStringKey.STATE_LOADING to "Loading",
            V1SharedStringKey.STATE_EMPTY to "No content",
            V1SharedStringKey.STATE_ERROR to "Operation failed",
            V1SharedStringKey.STATE_CANCELLED to "Operation cancelled",
            V1SharedStringKey.STATE_DISCONNECTED to "Device disconnected",
            V1SharedStringKey.STATE_UNSUPPORTED to "This feature is not supported",
            V1SharedStringKey.STATE_OUTCOME_UNKNOWN to "Operation outcome is unknown",
            V1SharedStringKey.VALUE_UNKNOWN to "Unknown",
            V1SharedStringKey.VALUE_UNAVAILABLE to "Unavailable",
            V1SharedStringKey.TECHNICAL_CODE to "Technical code: {code}",
        ),
    )

    private val technicalLabels = listOf(
        "all",
        "debug",
        "info",
        "error",
        "Esc",
        "Tab",
        "Ctrl",
        "Alt",
    )

    fun requiredKeys(language: UiLanguage): Set<V1SharedStringKey> =
        catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: V1SharedStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return key.placeholderSchema
    }

    fun fixedTechnicalLabels(language: UiLanguage): List<String> {
        require(catalogs.containsKey(language))
        return technicalLabels
    }

    fun text(language: UiLanguage, key: V1SharedStringKey): String {
        require(key.placeholderSchema.isEmpty()) { "Parameterized shared text requires a typed reference" }
        return resolve(language, LocalizedTextRef.shared(key))
    }

    fun resolve(language: UiLanguage, ref: LocalizedTextRef): String {
        require(ref.owner == OWNER) { "Text owner mismatch" }
        val key = V1SharedStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown shared semantic key")
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.keys == key.placeholderSchema.keys) { "Placeholder set mismatch" }
        key.placeholderSchema.forEach { (name, type) ->
            require(arguments.getValue(name).type == type) { "Placeholder type mismatch" }
        }
        return ref.arguments.fold(catalogs.getValue(language).getValue(key)) { text, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> SafeVerbatimText.render(
                    raw = argument.value as String,
                    policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 160),
                ).display
                else -> argument.value.toString()
            }
            text.replace("{${argument.name}}", value)
        }
    }
}
