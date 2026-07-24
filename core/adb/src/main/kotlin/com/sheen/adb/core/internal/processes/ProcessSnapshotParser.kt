package com.sheen.adb.core.internal.processes

import com.sheen.adb.core.ProcessFieldState
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessIdentity

internal object ProcessSnapshotParser {
    fun parse(
        psText: String,
        sessionId: String,
        generation: Long,
        cpuPercent: Double? = null,
        firstProcessTicks: Map<Int, Long> = emptyMap(),
        secondProcessTicks: Map<Int, Long> = emptyMap(),
        elapsedTotalTicks: Long? = null,
        processorCount: Int = 1,
        pssKiBByPid: Map<Int, Long> = emptyMap(),
        startTimeTicksByPid: Map<Int, Long> = emptyMap(),
    ): List<ProcessSnapshotEntry> {
        val lines = psText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return emptyList()
        val headers = lines.first().split(Regex("\\s+")).map(String::uppercase)
        val idx = { names: List<String> -> names.firstNotNullOfOrNull { n -> headers.indexOf(n).takeIf { it >= 0 } } ?: -1 }
        val user = idx(listOf("USER", "UID"))
        val pid = idx(listOf("PID"))
        val ppid = idx(listOf("PPID"))
        val name = idx(listOf("NAME", "CMD", "COMMAND"))
        return lines.drop(1).mapNotNull { line ->
            val f = line.split(Regex("\\s+"))
            val p = f.getOrNull(pid)?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val n = f.getOrNull(name)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uidValue = f.getOrNull(user)
            val sampledCpu = calculateCpu(
                pid = p,
                firstProcessTicks = firstProcessTicks,
                secondProcessTicks = secondProcessTicks,
                elapsedTotalTicks = elapsedTotalTicks,
                processorCount = processorCount,
            )
            val resolvedCpu = sampledCpu.value ?: cpuPercent?.coerceIn(0.0, 100.0)
            val pssKiB = pssKiBByPid[p]?.takeIf { it >= 0L }
            ProcessSnapshotEntry(
                identity = ProcessIdentity(sessionId, p, startTimeTicksByPid[p], uidValue, n, generation),
                cpuPercent = resolvedCpu,
                cpuState = when {
                    resolvedCpu != null -> ProcessFieldState.AVAILABLE
                    sampledCpu.invalid -> ProcessFieldState.UNKNOWN
                    else -> ProcessFieldState.CALCULATING
                },
                pssMiB = pssKiB?.div(KIB_PER_MIB),
                pssState = if (pssKiB == null) ProcessFieldState.UNKNOWN else ProcessFieldState.AVAILABLE,
                parentPid = f.getOrNull(ppid)?.toIntOrNull(),
            )
        }
    }

    private fun calculateCpu(
        pid: Int,
        firstProcessTicks: Map<Int, Long>,
        secondProcessTicks: Map<Int, Long>,
        elapsedTotalTicks: Long?,
        processorCount: Int,
    ): CpuCalculation {
        val first = firstProcessTicks[pid]
        val second = secondProcessTicks[pid]
        if (first == null || second == null || elapsedTotalTicks == null) return CpuCalculation()
        if (second < first || elapsedTotalTicks <= 0L || processorCount <= 0) {
            return CpuCalculation(invalid = true)
        }
        val percent = (second - first).toDouble() / elapsedTotalTicks.toDouble() *
            processorCount.toDouble() * 100.0
        return CpuCalculation(percent.coerceIn(0.0, 100.0))
    }

    private data class CpuCalculation(
        val value: Double? = null,
        val invalid: Boolean = false,
    )

    private const val KIB_PER_MIB = 1024.0
}
