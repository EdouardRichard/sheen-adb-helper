package com.sheen.adb.core.internal

import com.sheen.adb.core.internal.diagnostics.DiagnosticFilter
import com.sheen.adb.core.internal.diagnostics.DiagnosticFilterCriteria
import com.sheen.adb.core.internal.diagnostics.LogcatApplicationAssociation
import com.sheen.adb.core.internal.diagnostics.StructuredLogcatBuffer
import com.sheen.adb.core.internal.diagnostics.StructuredLogcatKind
import com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel
import com.sheen.adb.core.internal.diagnostics.StructuredLogcatParser
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LogcatAnalysisTest {
    @Test
    fun `parses modern threadtime fields without losing raw text`() {
        val raw = "07-23 10:11:12.345  10123  2345  6789 I FixtureTag: synthetic message"

        val record = StructuredLogcatParser.parse(sequence = 7, rawText = raw, fromStandardError = false)

        assertEquals(record.sequence, 7)
        assertEquals(record.kind, StructuredLogcatKind.PARSED)
        assertEquals(record.timestamp?.month, 7)
        assertEquals(record.timestamp?.day, 23)
        assertEquals(record.timestamp?.millisecond, 345)
        assertEquals(record.uid, 10123)
        assertEquals(record.pid, 2345)
        assertEquals(record.tid, 6789)
        assertEquals(record.level, StructuredLogcatLevel.INFO)
        assertEquals(record.tag, "FixtureTag")
        assertEquals(record.message, "synthetic message")
        assertEquals(record.rawText, raw)
    }

    @Test
    fun `unparsed and stderr lines retain raw text without invented identity`() {
        val unknown = StructuredLogcatParser.parse(1, "synthetic unknown format", false)
        val stderr = StructuredLogcatParser.parse(2, "synthetic transport warning", true)

        assertEquals(unknown.kind, StructuredLogcatKind.UNPARSED)
        assertNull(unknown.timestamp)
        assertNull(unknown.pid)
        assertNull(unknown.level)
        assertNull(unknown.tag)
        assertEquals(unknown.rawText, "synthetic unknown format")
        assertEquals(stderr.kind, StructuredLogcatKind.STDERR)
        assertNull(stderr.pid)
        assertNull(stderr.level)
        assertEquals(stderr.rawText, "synthetic transport warning")
    }

    @Test
    fun `all non-empty level tag keyword pid process and verified app filters use AND semantics`() {
        val base = StructuredLogcatParser.parse(
            1,
            "07-23 10:11:12.345  10123  2345  6789 W FixtureTag: synthetic needle",
            false,
        ).copy(
            processName = "fixture.worker",
            applicationAssociation = LogcatApplicationAssociation.Verified("com.example.fixture"),
        )
        val criteria = DiagnosticFilterCriteria(
            levels = setOf(StructuredLogcatLevel.WARN),
            tag = "fixture",
            keyword = "NEEDLE",
            pid = "2345",
            processName = "worker",
            applicationPackage = "example.fixture",
        )

        assertTrue(DiagnosticFilter.matches(base, criteria))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(levels = setOf(StructuredLogcatLevel.ERROR))))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(tag = "other")))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(keyword = "absent")))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(pid = "999")))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(processName = "main")))
        assertFalse(DiagnosticFilter.matches(base, criteria.copy(applicationPackage = "other")))
        assertTrue(DiagnosticFilter.matches(base, DiagnosticFilterCriteria()))

        assertFalse(
            DiagnosticFilter.matches(
                base.copy(applicationAssociation = LogcatApplicationAssociation.Multiple(setOf("com.example.fixture"))),
                DiagnosticFilterCriteria(applicationPackage = "fixture"),
            ),
        )
        assertFalse(
            DiagnosticFilter.matches(
                base.copy(applicationAssociation = LogcatApplicationAssociation.Unknown("ambiguous")),
                DiagnosticFilterCriteria(applicationPackage = "fixture"),
            ),
        )
    }

    @Test
    fun `bounded store evicts over ten thousand records and returns only latest one hundred matches`() {
        val buffer = StructuredLogcatBuffer()
        repeat(10_050) { sequence ->
            buffer.add(StructuredLogcatParser.parse(sequence.toLong(), "synthetic-$sequence", false))
        }

        assertEquals(buffer.size, 10_000)
        assertTrue(buffer.droppedOldest)
        assertEquals(buffer.snapshot().first().sequence, 50)
        val visible = buffer.latest(DiagnosticFilterCriteria(keyword = "synthetic"))
        assertEquals(visible.size, 100)
        assertEquals(visible.first().sequence, 9_950)
        assertEquals(visible.last().sequence, 10_049)
    }

    @Test
    fun `bounded store enforces four MiB raw byte budget before filtering`() {
        val buffer = StructuredLogcatBuffer()
        repeat(5) { sequence ->
            val raw = "${sequence}:" + "x".repeat(1024 * 1024)
            buffer.add(StructuredLogcatParser.parse(sequence.toLong(), raw, false))
        }

        assertTrue(buffer.droppedOldest)
        assertTrue(buffer.totalBytes <= 4L * 1024 * 1024)
        assertTrue(buffer.size < 5)
        assertTrue(buffer.latest(DiagnosticFilterCriteria(), limit = 100).size <= buffer.size)
    }
}
