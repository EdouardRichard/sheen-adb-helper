package com.sheen.adb.feature.shell

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellTranscriptBufferTest {
    @Test
    fun `drops oldest output and keeps utf8 boundary`() {
        val buffer = ShellTranscriptBuffer(maxOutputBytes = 12)
        buffer.add(ShellEntry(1, "first", stdout = "12345678", status = ShellEntryStatus.SUCCEEDED))
        buffer.add(ShellEntry(2, "second", stdout = "中文中文", status = ShellEntryStatus.SUCCEEDED))

        assertEquals(buffer.snapshot().size, 1)
        assertEquals(buffer.snapshot().single().stdout, "中文中文")
        assertTrue(buffer.droppedOldestOutput)
    }

    @Test
    fun `truncates one oversized entry from the front`() {
        val buffer = ShellTranscriptBuffer(maxOutputBytes = 5)
        buffer.add(ShellEntry(1, "command", stdout = "0123456789", status = ShellEntryStatus.SUCCEEDED))
        assertEquals(buffer.snapshot().single().stdout, "56789")
    }

    @Test
    fun `bounds record count independently from output bytes`() {
        val buffer = ShellTranscriptBuffer(
            maxOutputBytes = 1_024,
            maxEntries = 3,
        )
        (1L..5L).forEach { sequence ->
            buffer.add(
                ShellEntry(
                    sequence = sequence,
                    command = "synthetic-$sequence",
                    stdout = "ok",
                    status = ShellEntryStatus.SUCCEEDED,
                ),
            )
        }

        assertEquals(buffer.snapshot().map(ShellEntry::sequence), listOf(3L, 4L, 5L))
        assertTrue(buffer.droppedOldestOutput)
    }

    @Test
    fun `filter is immediate local derived state and never mutates the bounded transcript`() {
        val buffer = ShellTranscriptBuffer(maxOutputBytes = 1_024, maxEntries = 10)
        buffer.add(ShellEntry(1, "alpha", stdout = "ready", status = ShellEntryStatus.SUCCEEDED))
        buffer.add(ShellEntry(2, "beta", stderr = "Synthetic ERROR", status = ShellEntryStatus.FAILED))
        buffer.add(ShellEntry(3, "gamma", stdout = "error recovered", status = ShellEntryStatus.SUCCEEDED))
        val before = buffer.snapshot()

        assertEquals(buffer.filtered("ERROR").map(ShellEntry::sequence), listOf(2L, 3L))
        assertEquals(buffer.filtered("alpha").map(ShellEntry::sequence), listOf(1L))
        assertEquals(buffer.filtered("").map(ShellEntry::sequence), listOf(1L, 2L, 3L))
        assertEquals(buffer.snapshot(), before, "filtering must not rewrite or re-execute transcript entries")
    }

    @Test
    fun `clear removes local visible records only and accepts later local output`() {
        val buffer = ShellTranscriptBuffer(maxOutputBytes = 1_024, maxEntries = 10)
        buffer.add(ShellEntry(1, "first", stdout = "visible", status = ShellEntryStatus.SUCCEEDED))
        buffer.add(ShellEntry(2, "second", stdout = "visible", status = ShellEntryStatus.SUCCEEDED))

        buffer.clear()

        assertTrue(buffer.snapshot().isEmpty())
        assertFalse(buffer.droppedOldestOutput)
        buffer.add(ShellEntry(3, "after-clear", stdout = "new", status = ShellEntryStatus.SUCCEEDED))
        assertEquals(buffer.snapshot().map(ShellEntry::sequence), listOf(3L))
    }

    @Test
    fun `replace keeps arrival order and a new sequence appends deterministically`() {
        val buffer = ShellTranscriptBuffer(maxOutputBytes = 1_024, maxEntries = 10)
        buffer.add(ShellEntry(20, "first-arrival", status = ShellEntryStatus.RUNNING))
        buffer.add(ShellEntry(10, "second-arrival", status = ShellEntryStatus.RUNNING))

        buffer.replace(
            20,
            ShellEntry(20, "first-arrival", stdout = "done", status = ShellEntryStatus.SUCCEEDED),
        )
        buffer.replace(
            30,
            ShellEntry(30, "third-arrival", stdout = "done", status = ShellEntryStatus.SUCCEEDED),
        )

        assertEquals(buffer.snapshot().map(ShellEntry::sequence), listOf(20L, 10L, 30L))
        assertEquals(buffer.snapshot().first().stdout, "done")
    }

    @Test
    fun `ten MiB fixture stays bounded preserves order and remains locally filterable`() {
        val buffer = ShellTranscriptBuffer(
            maxOutputBytes = TEN_MIB_BYTES,
            maxEntries = 32,
        )
        val oneMiB = "x".repeat(MIB_BYTES)
        (1L..11L).forEach { sequence ->
            buffer.add(
                ShellEntry(
                    sequence = sequence,
                    command = "fixture-$sequence",
                    stdout = oneMiB,
                    status = ShellEntryStatus.SUCCEEDED,
                ),
            )
        }

        val snapshot = buffer.snapshot()
        assertEquals(snapshot.map(ShellEntry::sequence), (2L..11L).toList())
        assertTrue(buffer.droppedOldestOutput)
        assertTrue(
            snapshot.sumOf { it.stdout.encodeToByteArray().size + it.stderr.encodeToByteArray().size } <=
                TEN_MIB_BYTES,
        )
        assertEquals(buffer.filtered("fixture-11").map(ShellEntry::sequence), listOf(11L))
        assertEquals(buffer.snapshot(), snapshot, "the 10 MiB filter fixture must remain immutable")
    }

    private companion object {
        const val MIB_BYTES = 1024 * 1024
        const val TEN_MIB_BYTES = 10 * MIB_BYTES
    }
}
