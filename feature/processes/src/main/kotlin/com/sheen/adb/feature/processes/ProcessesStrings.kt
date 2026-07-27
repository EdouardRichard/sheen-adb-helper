package com.sheen.adb.feature.processes

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class ProcessesStringKey(val semanticKey: String) {
    TITLE("processes.title"),
    SEARCH_PLACEHOLDER("processes.search_placeholder"),
    CPU_LABEL("processes.cpu_label"),
    MEMORY_LABEL("processes.memory_label"),
    UNKNOWN_METRIC("processes.unknown_metric"),
    END_PROCESS("processes.end_process"),
    LOADING("processes.loading"),
    EMPTY("processes.empty"),
    ERROR("processes.error"),
    CANCELLED("processes.cancelled"),
    DISCONNECTED("processes.disconnected"),
    UNSUPPORTED("processes.unsupported"),
    OUTCOME_UNKNOWN("processes.outcome_unknown"),
    CHOOSE_END_SCOPE("processes.choose_end_scope"),
    END_SINGLE_PROCESS("processes.end_single_process"),
    END_APPLICATION("processes.end_application"),
    APPLICATION_SCOPE_UNAVAILABLE("processes.application_scope_unavailable"),
    CONFIRM_END_SINGLE("processes.confirm_end_single"),
    CONFIRM_END_APPLICATION("processes.confirm_end_application"),
    TERMINATION_SUCCEEDED("processes.termination_succeeded"),
    TERMINATION_FAILED("processes.termination_failed"),
    TERMINATION_REJECTED("processes.termination_rejected"),
    TERMINATION_TIMED_OUT("processes.termination_timed_out"),
    TERMINATION_CANCELLED("processes.termination_cancelled"),
    PROCESS_ROW_CONTENT_DESCRIPTION("processes.process_row_content_description"),
    CPU_CONTENT_DESCRIPTION("processes.cpu_content_description"),
    MEMORY_CONTENT_DESCRIPTION("processes.memory_content_description"),
    END_PROCESS_CONTENT_DESCRIPTION("processes.end_process_content_description"),
    RESULT_LIVE_ANNOUNCEMENT("processes.result_live_announcement"),
}

object ProcessesStrings {
    const val OWNER = "processes"

    private val catalogs = mapOf(
        UiLanguage.ZH_CN to mapOf(
            ProcessesStringKey.TITLE to "\u8fdb\u7a0b\u7ba1\u7406",
            ProcessesStringKey.SEARCH_PLACEHOLDER to "\u8bf7\u8f93\u5165\u8fdb\u7a0b\u540d",
            ProcessesStringKey.CPU_LABEL to "CPU",
            ProcessesStringKey.MEMORY_LABEL to "\u5185\u5b58",
            ProcessesStringKey.UNKNOWN_METRIC to "\u672a\u77e5",
            ProcessesStringKey.END_PROCESS to "\u7ed3\u675f\u8fdb\u7a0b",
            ProcessesStringKey.LOADING to "\u6b63\u5728\u52a0\u8f7d\u8fdb\u7a0b",
            ProcessesStringKey.EMPTY to "\u672a\u627e\u5230\u8fdb\u7a0b",
            ProcessesStringKey.ERROR to "\u65e0\u6cd5\u8bfb\u53d6\u8fdb\u7a0b",
            ProcessesStringKey.CANCELLED to "\u64cd\u4f5c\u5df2\u53d6\u6d88",
            ProcessesStringKey.DISCONNECTED to "\u8bbe\u5907\u5df2\u65ad\u5f00\uff0c\u8bf7\u91cd\u65b0\u8fde\u63a5",
            ProcessesStringKey.UNSUPPORTED to "\u5f53\u524d\u8bbe\u5907\u4e0d\u652f\u6301\u6b64\u64cd\u4f5c",
            ProcessesStringKey.OUTCOME_UNKNOWN to "\u7ed3\u679c\u672a\u77e5\uff0c\u8bf7\u5237\u65b0\u8fdb\u7a0b\u5217\u8868\u540e\u786e\u8ba4",
            ProcessesStringKey.CHOOSE_END_SCOPE to "\u9009\u62e9\u7ed3\u675f\u8303\u56f4",
            ProcessesStringKey.END_SINGLE_PROCESS to "\u4ec5\u7ed3\u675f\u6b64\u8fdb\u7a0b",
            ProcessesStringKey.END_APPLICATION to "\u7ed3\u675f\u6574\u4e2a\u5e94\u7528",
            ProcessesStringKey.APPLICATION_SCOPE_UNAVAILABLE to "\u65e0\u6cd5\u53ef\u9760\u5173\u8054\u5e94\u7528\uff0c\u53ea\u80fd\u7ed3\u675f\u5355\u4e2a\u8fdb\u7a0b",
            ProcessesStringKey.CONFIRM_END_SINGLE to "\u786e\u8ba4\u7ed3\u675f\u6b64\u8fdb\u7a0b\uff1f",
            ProcessesStringKey.CONFIRM_END_APPLICATION to "\u786e\u8ba4\u7ed3\u675f\u6b64\u5e94\u7528\u7684\u6240\u6709\u5173\u8054\u8fdb\u7a0b\uff1f",
            ProcessesStringKey.TERMINATION_SUCCEEDED to "\u8fdb\u7a0b\u7ed3\u675f\u8bf7\u6c42\u5df2\u9a8c\u8bc1",
            ProcessesStringKey.TERMINATION_FAILED to "\u7ed3\u675f\u8fdb\u7a0b\u5931\u8d25",
            ProcessesStringKey.TERMINATION_REJECTED to "\u8bbe\u5907\u62d2\u7edd\u7ed3\u675f\u8fdb\u7a0b",
            ProcessesStringKey.TERMINATION_TIMED_OUT to "\u7ed3\u675f\u8fdb\u7a0b\u8d85\u65f6",
            ProcessesStringKey.TERMINATION_CANCELLED to "\u5df2\u53d6\u6d88\u7ed3\u675f\u8fdb\u7a0b",
            ProcessesStringKey.PROCESS_ROW_CONTENT_DESCRIPTION to "\u8fdb\u7a0b\u8be6\u60c5",
            ProcessesStringKey.CPU_CONTENT_DESCRIPTION to "CPU \u5360\u7528",
            ProcessesStringKey.MEMORY_CONTENT_DESCRIPTION to "\u5185\u5b58\u5360\u7528",
            ProcessesStringKey.END_PROCESS_CONTENT_DESCRIPTION to "\u7ed3\u675f\u6240\u9009\u8fdb\u7a0b",
            ProcessesStringKey.RESULT_LIVE_ANNOUNCEMENT to "\u8fdb\u7a0b\u64cd\u4f5c\u7ed3\u679c\u5df2\u66f4\u65b0",
        ),
        UiLanguage.EN_US to mapOf(
            ProcessesStringKey.TITLE to "Processes",
            ProcessesStringKey.SEARCH_PLACEHOLDER to "Enter a process name",
            ProcessesStringKey.CPU_LABEL to "CPU",
            ProcessesStringKey.MEMORY_LABEL to "Memory",
            ProcessesStringKey.UNKNOWN_METRIC to "Unknown",
            ProcessesStringKey.END_PROCESS to "End process",
            ProcessesStringKey.LOADING to "Loading processes",
            ProcessesStringKey.EMPTY to "No processes found",
            ProcessesStringKey.ERROR to "Unable to read processes",
            ProcessesStringKey.CANCELLED to "Operation cancelled",
            ProcessesStringKey.DISCONNECTED to "The device disconnected. Reconnect to continue.",
            ProcessesStringKey.UNSUPPORTED to "This operation is unsupported by the device",
            ProcessesStringKey.OUTCOME_UNKNOWN to "Result unknown. Refresh the process list to verify.",
            ProcessesStringKey.CHOOSE_END_SCOPE to "Choose termination scope",
            ProcessesStringKey.END_SINGLE_PROCESS to "End this process only",
            ProcessesStringKey.END_APPLICATION to "End the entire application",
            ProcessesStringKey.APPLICATION_SCOPE_UNAVAILABLE to
                "No reliable application association is available. Only this process can be ended.",
            ProcessesStringKey.CONFIRM_END_SINGLE to "End this process?",
            ProcessesStringKey.CONFIRM_END_APPLICATION to "End all associated processes for this application?",
            ProcessesStringKey.TERMINATION_SUCCEEDED to "Process termination was verified",
            ProcessesStringKey.TERMINATION_FAILED to "Process termination failed",
            ProcessesStringKey.TERMINATION_REJECTED to "The device rejected process termination",
            ProcessesStringKey.TERMINATION_TIMED_OUT to "Process termination timed out",
            ProcessesStringKey.TERMINATION_CANCELLED to "Process termination cancelled",
            ProcessesStringKey.PROCESS_ROW_CONTENT_DESCRIPTION to "Process details",
            ProcessesStringKey.CPU_CONTENT_DESCRIPTION to "CPU usage",
            ProcessesStringKey.MEMORY_CONTENT_DESCRIPTION to "Memory usage",
            ProcessesStringKey.END_PROCESS_CONTENT_DESCRIPTION to "End selected process",
            ProcessesStringKey.RESULT_LIVE_ANNOUNCEMENT to "Process operation result updated",
        ),
    )

    init {
        val expected = ProcessesStringKey.entries.toSet()
        require(catalogs.values.all { it.keys == expected }) {
            "Processes catalogs must contain the same complete key set"
        }
        require(catalogs.values.all { catalog -> catalog.values.all(String::isNotBlank) }) {
            "Processes catalog entries must not be blank"
        }
    }

    fun requiredKeys(language: UiLanguage): Set<ProcessesStringKey> =
        catalogs.getValue(language).keys

    fun text(
        language: UiLanguage,
        key: ProcessesStringKey,
    ): String = catalogs.getValue(language).getValue(key)

    fun targetRef(
        key: ProcessesStringKey,
        processName: String,
        pid: String,
        technicalCode: String,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = listOf(
            TypedTextArgument("processName", processName, TextArgumentType.VERBATIM),
            TypedTextArgument("pid", pid, TextArgumentType.VERBATIM),
            TypedTextArgument("technicalCode", technicalCode, TextArgumentType.VERBATIM),
        ),
    )
}
