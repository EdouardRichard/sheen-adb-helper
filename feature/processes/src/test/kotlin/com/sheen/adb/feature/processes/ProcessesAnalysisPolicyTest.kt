package com.sheen.adb.feature.processes

import com.sheen.adb.core.ProcessIdentity
import com.sheen.adb.core.ProcessFieldState
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessTerminationScope
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ProcessesAnalysisPolicyTest {
    @Test
    fun `single query filters process name immediately and never matches pid or application`() {
        val entries = listOf(
            entry(1234, "fixture.worker", "com.example.reader"),
            entry(2345, "fixture.remote", "com.example.writer"),
        )
        val state = ProcessesUiState(
            isConnected = true,
            sessionId = "session-a",
            entries = entries,
            query = "WORK",
        )

        assertEquals(state.visibleEntries.map { it.pid }, listOf(1234))
        assertEquals(state.copy(query = "remote").visibleEntries.map { it.pid }, listOf(2345))
        assertTrue(state.copy(query = "").visibleEntries == entries)
        assertTrue(state.copy(query = "2345").visibleEntries.isEmpty(), "PID must not be a search field")
        assertTrue(
            state.copy(query = "example.reader").visibleEntries.isEmpty(),
            "application package must not be a search field",
        )
    }

    @Test
    fun `unknown cpu and pss stay unknown instead of being projected as zero`() {
        val unknown = entry(
            pid = 101,
            name = "fixture.unknown",
            packageName = null,
            cpuPercent = null,
            pssMiB = null,
        )

        assertNull(unknown.cpuPercent)
        assertNull(unknown.pssMiB)
        assertEquals(unknown.cpuState, ProcessFieldState.UNKNOWN)
        assertEquals(unknown.pssState, ProcessFieldState.UNKNOWN)
        assertFalse(unknown.cpuPercent == 0.0)
        assertFalse(unknown.pssMiB == 0.0)
    }

    @Test
    fun `termination scope offers whole application only for a reliable association`() {
        val associated = entry(100, "fixture.shared", "com.example.shared")
        val unassociated = entry(101, "fixture.unknown", null)

        assertEquals(
            ProcessesPolicy.terminationScopes(associated),
            listOf(
                ProcessTerminationScope.SINGLE_PROCESS,
                ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP,
            ),
        )
        assertEquals(
            ProcessesPolicy.terminationScopes(unassociated),
            listOf(ProcessTerminationScope.SINGLE_PROCESS),
        )
        assertTrue(
            ProcessesPolicy.confirmedApplicationSet(unassociated, listOf(associated, unassociated)).isEmpty(),
        )
    }

    @Test
    fun `refresh classification distinguishes empty exited unsupported and cancelled`() {
        val previous = listOf(entry(100, "fixture.old", "com.example.old"))
        val current = listOf(entry(101, "fixture.new", "com.example.new"))

        assertEquals(
            ProcessesPolicy.classifyRefresh(previous, current, degradedReason = null),
            ProcessesAnalysisStatus.PROCESSES_EXITED,
        )
        assertEquals(
            ProcessesPolicy.classifyRefresh(emptyList(), emptyList(), degradedReason = null),
            ProcessesAnalysisStatus.EMPTY,
        )
        assertEquals(
            ProcessesPolicy.classifyRefresh(emptyList(), emptyList(), degradedReason = "unsupported"),
            ProcessesAnalysisStatus.UNSUPPORTED,
        )
        assertEquals(ProcessesPolicy.cancelledStatus(), ProcessesAnalysisStatus.CANCELLED)
    }

    @Test
    fun `session switch clears entries filters exit state and stale generation`() {
        val dirty = ProcessesUiState(
            isConnected = true,
            sessionId = "session-a",
            generation = 7,
            entries = listOf(entry(100, "fixture.old", "com.example.old")),
            query = "old",
            status = ProcessesAnalysisStatus.PROCESSES_EXITED,
        )

        val switched = ProcessesPolicy.changedSession(dirty, connected = true, sessionId = "session-b")

        assertTrue(switched.entries.isEmpty())
        assertEquals(switched.generation, 0)
        assertEquals(switched.query, "")
        assertEquals(switched.status, ProcessesAnalysisStatus.EMPTY)
        assertEquals(
            ProcessesPolicy.changedSession(switched, connected = false, sessionId = null).status,
            ProcessesAnalysisStatus.DISCONNECTED,
        )
    }

    @Test
    fun `snapshot acceptance binds even an empty snapshot to session and generation`() {
        val current = ProcessesUiState(
            isConnected = true,
            sessionId = "session-a",
            generation = 7,
            entries = listOf(entry(100, "fixture.old", "com.example.old")),
        )

        assertTrue(
            ProcessesPolicy.acceptSnapshot(
                current = current,
                snapshotSessionId = "session-a",
                snapshotGeneration = 8,
                entries = emptyList(),
            ),
        )
        assertFalse(
            ProcessesPolicy.acceptSnapshot(
                current = current,
                snapshotSessionId = "session-b",
                snapshotGeneration = 8,
                entries = emptyList(),
            ),
        )
        assertFalse(
            ProcessesPolicy.acceptSnapshot(
                current = current,
                snapshotSessionId = "session-a",
                snapshotGeneration = 6,
                entries = emptyList(),
            ),
        )
    }

    private fun entry(
        pid: Int,
        name: String,
        packageName: String?,
        cpuPercent: Double? = null,
        pssMiB: Double? = null,
    ) = ProcessSnapshotEntry(
        identity = ProcessIdentity("session-a", pid, 900L + pid, "u0_a123", name, 1),
        applicationPackage = packageName,
        cpuPercent = cpuPercent,
        cpuState = if (cpuPercent == null) ProcessFieldState.UNKNOWN else ProcessFieldState.AVAILABLE,
        pssMiB = pssMiB,
        pssState = if (pssMiB == null) ProcessFieldState.UNKNOWN else ProcessFieldState.AVAILABLE,
    )
}
