package com.sheen.adb.feature.overview

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class OverviewMetricLayoutContractTest {
    private val source by lazy {
        locate("feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt")
            .readText()
    }

    @Test
    fun `processor icon and ring follow the design card anchors`() {
        assertTrue(source.contains("SheenIcons.Cpu,"))
        assertTrue(source.contains("ProcessorMetricContainer"))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.Center)"))
        assertFalse(source.contains("info.cpuAbi ?: unavailable"))
    }

    @Test
    fun `memory and storage use separate baseline aligned value columns`() {
        val metricContainer = source
            .substringAfter("private fun MetricContainer")
            .substringBefore("private fun ProcessorMetricContainer")
        assertTrue(metricContainer.contains(".fillMaxSize()"))
        assertTrue(source.contains("icon = SheenIcons.MemoryAlt"))
        assertTrue(source.contains("MetricValueRow("))
        assertTrue(source.contains("Modifier.alignByBaseline()"))
        assertTrue(source.contains("MetricBottomContent("))
    }

    @Test
    fun `battery groups percentage and temperature at the bottom`() {
        assertTrue(source.contains("BatteryBottomContent("))
        assertTrue(source.contains("MetricHeader(SheenIcons.BatteryCharging"))
        assertFalse(source.contains("MetricHeader(SheenIcons.Battery,"))
    }

    private fun locate(relative: String): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Cannot locate $relative from ${System.getProperty("user.dir")}")
    }
}
