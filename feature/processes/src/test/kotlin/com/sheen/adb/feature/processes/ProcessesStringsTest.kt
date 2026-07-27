package com.sheen.adb.feature.processes

import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.UiLanguage
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ProcessesStringsTest {
    @Test
    fun `processes owns complete matching Chinese and English page catalogs`() {
        val required = setOf(
            ProcessesStringKey.TITLE,
            ProcessesStringKey.SEARCH_PLACEHOLDER,
            ProcessesStringKey.CPU_LABEL,
            ProcessesStringKey.MEMORY_LABEL,
            ProcessesStringKey.UNKNOWN_METRIC,
            ProcessesStringKey.END_PROCESS,
            ProcessesStringKey.LOADING,
            ProcessesStringKey.EMPTY,
            ProcessesStringKey.ERROR,
            ProcessesStringKey.CANCELLED,
            ProcessesStringKey.DISCONNECTED,
            ProcessesStringKey.UNSUPPORTED,
            ProcessesStringKey.OUTCOME_UNKNOWN,
        )

        assertTrue(ProcessesStrings.requiredKeys(UiLanguage.ZH_CN).containsAll(required))
        assertTrue(ProcessesStrings.requiredKeys(UiLanguage.EN_US).containsAll(required))
        assertEquals(
            ProcessesStrings.requiredKeys(UiLanguage.ZH_CN),
            ProcessesStrings.requiredKeys(UiLanguage.EN_US),
        )
        required.forEach { key ->
            assertTrue(ProcessesStrings.text(UiLanguage.ZH_CN, key).isNotBlank(), "blank zh-CN key $key")
            assertTrue(ProcessesStrings.text(UiLanguage.EN_US, key).isNotBlank(), "blank en-US key $key")
        }
        assertEquals(ProcessesStrings.text(UiLanguage.ZH_CN, ProcessesStringKey.UNKNOWN_METRIC), "\u672a\u77e5")
        assertEquals(ProcessesStrings.text(UiLanguage.EN_US, ProcessesStringKey.UNKNOWN_METRIC), "Unknown")
    }

    @Test
    fun `scope confirmation terminal results and accessibility have bilingual coverage`() {
        val required = setOf(
            ProcessesStringKey.CHOOSE_END_SCOPE,
            ProcessesStringKey.END_SINGLE_PROCESS,
            ProcessesStringKey.END_APPLICATION,
            ProcessesStringKey.APPLICATION_SCOPE_UNAVAILABLE,
            ProcessesStringKey.CONFIRM_END_SINGLE,
            ProcessesStringKey.CONFIRM_END_APPLICATION,
            ProcessesStringKey.TERMINATION_SUCCEEDED,
            ProcessesStringKey.TERMINATION_FAILED,
            ProcessesStringKey.TERMINATION_REJECTED,
            ProcessesStringKey.TERMINATION_TIMED_OUT,
            ProcessesStringKey.TERMINATION_CANCELLED,
            ProcessesStringKey.OUTCOME_UNKNOWN,
            ProcessesStringKey.PROCESS_ROW_CONTENT_DESCRIPTION,
            ProcessesStringKey.CPU_CONTENT_DESCRIPTION,
            ProcessesStringKey.MEMORY_CONTENT_DESCRIPTION,
            ProcessesStringKey.END_PROCESS_CONTENT_DESCRIPTION,
            ProcessesStringKey.RESULT_LIVE_ANNOUNCEMENT,
        )

        required.forEach { key ->
            val zh = ProcessesStrings.text(UiLanguage.ZH_CN, key)
            val en = ProcessesStrings.text(UiLanguage.EN_US, key)
            assertTrue(zh.isNotBlank(), "blank zh-CN semantic/a11y key $key")
            assertTrue(en.isNotBlank(), "blank en-US semantic/a11y key $key")
            assertFalse(zh == en, "human-facing key must be translated: $key")
        }
    }

    @Test
    fun `process name PID and technical code remain typed verbatim arguments`() {
        val rawName = "worker\u202E\u0000:name"
        val rawPid = "00042"
        val rawTechnicalCode = "PROCESS_RESULT_UNKNOWN_07"

        val ref = ProcessesStrings.targetRef(
            key = ProcessesStringKey.OUTCOME_UNKNOWN,
            processName = rawName,
            pid = rawPid,
            technicalCode = rawTechnicalCode,
        )

        assertEquals(ref.owner, ProcessesStrings.OWNER)
        assertEquals(
            ref.arguments.associate { it.name to it.value },
            mapOf(
                "processName" to rawName,
                "pid" to rawPid,
                "technicalCode" to rawTechnicalCode,
            ),
        )
        assertTrue(ref.arguments.all { it.type == TextArgumentType.VERBATIM })

        val source = File(
            "src/main/kotlin/com/sheen/adb/feature/processes/ProcessesStrings.kt",
        ).takeIf(File::isFile)?.readText().orEmpty()
        assertFalse(source.contains(".userMessage"), "core error prose must not be final process-page text")
        assertFalse(source.contains(".nextStep"), "core next-step prose must not be final process-page text")
    }
}
