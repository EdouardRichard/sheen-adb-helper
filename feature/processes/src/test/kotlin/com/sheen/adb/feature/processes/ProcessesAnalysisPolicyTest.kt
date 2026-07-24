package com.sheen.adb.feature.processes

import com.sheen.adb.core.ProcessIdentity
import com.sheen.adb.core.ProcessSnapshotEntry
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ProcessesAnalysisPolicyTest {
    @Test
    fun `pid process name and application filters accept partial text and combine with AND`() {
        val entries = listOf(
            entry(1234, "fixture.worker", "com.example.reader"),
            entry(2345, "fixture.remote", "com.example.writer"),
        )
        val state = ProcessesUiState(
            isConnected = true,
            sessionId = "session-a",
            entries = entries,
            pidQuery = "23",
            processQuery = "WORK",
            applicationQuery = "example.read",
        )

        assertEquals(state.visibleEntries.map { it.pid }, listOf(1234))
        assertEquals(state.copy(pidQuery = "1234").visibleEntries.single().pid, 1234)
        assertTrue(state.copy(pidQuery = "", processQuery = "", applicationQuery = "").visibleEntries == entries)
        assertTrue(state.copy(processQuery = "remote").visibleEntries.isEmpty())
    }

    @Test
    fun `unknown association never matches application filter`() {
        val known = entry(100, "fixture.shared", "com.example.shared.one")
        val unknown = entry(101, "fixture.unknown", null)
        val state = ProcessesUiState(
            isConnected = true,
            entries = listOf(known, unknown),
            applicationQuery = "shared.one",
        )

        assertEquals(state.visibleEntries.single().applicationPackage, "com.example.shared.one")
        assertTrue(state.copy(applicationQuery = "unknown").visibleEntries.isEmpty())
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
            pidQuery = "100",
            processQuery = "old",
            applicationQuery = "example",
            status = ProcessesAnalysisStatus.PROCESSES_EXITED,
        )

        val switched = ProcessesPolicy.changedSession(dirty, connected = true, sessionId = "session-b")

        assertTrue(switched.entries.isEmpty())
        assertEquals(switched.generation, 0)
        assertEquals(switched.pidQuery, "")
        assertEquals(switched.processQuery, "")
        assertEquals(switched.applicationQuery, "")
        assertEquals(switched.status, ProcessesAnalysisStatus.EMPTY)
        assertEquals(
            ProcessesPolicy.changedSession(switched, connected = false, sessionId = null).status,
            ProcessesAnalysisStatus.DISCONNECTED,
        )
    }

    private fun entry(
        pid: Int,
        name: String,
        packageName: String?,
    ) = ProcessSnapshotEntry(
        identity = ProcessIdentity("session-a", pid, 900L + pid, "u0_a123", name, 1),
        applicationPackage = packageName,
    )
}
