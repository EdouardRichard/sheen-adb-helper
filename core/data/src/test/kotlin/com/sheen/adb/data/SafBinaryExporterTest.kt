package com.sheen.adb.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class SafBinaryExporterTest {
    @Test
    fun `CreateDocument destination succeeds only after complete readable binary write`() = runDataTest {
        val source = FakeArtifactSource("controlled-device-binary".encodeToByteArray())
        val target = FakeBinaryTarget()
        val opener = FakeDestinationOpener(target)
        val cleanup = FakeCleanup()
        val exporter = exporter(opener, cleanup)
        val destination = ExportDestination(
            opaqueId = "create-document-selected",
            displayName = "controlled-device-screen.mp4",
        )

        val result = exporter.export(ARTIFACT_ID, source, destination)

        assertTrue(result is SafBinaryExportResult.Success)
        assertEquals(target.bytes.toByteArray(), "controlled-device-binary".encodeToByteArray())
        assertTrue(target.finished)
        assertTrue(target.closed)
        assertEquals(target.readableSize, source.sizeBytes)
        assertEquals(opener.destinations, listOf(destination))
        assertEquals(cleanup.artifactIds, listOf(ARTIFACT_ID))
    }

    @Test
    fun `user cancellation creates no destination and still cleans source`() = runDataTest {
        val source = FakeArtifactSource("private-binary".encodeToByteArray())
        val opener = FakeDestinationOpener(FakeBinaryTarget())
        val cleanup = FakeCleanup()
        val exporter = exporter(opener, cleanup)

        val result = exporter.export(ARTIFACT_ID, source, destination = null)

        assertTrue(result is SafBinaryExportResult.UserCancelled)
        assertTrue(opener.destinations.isEmpty())
        assertEquals(source.readCalls, 0)
        assertEquals(cleanup.artifactIds, listOf(ARTIFACT_ID))
    }

    @Test
    fun `provider write failure is non success closes target and cleans source`() = runDataTest {
        val source = FakeArtifactSource(ByteArray(12) { it.toByte() })
        val target = FakeBinaryTarget(failAfterBytes = 4)
        val opener = FakeDestinationOpener(target)
        val cleanup = FakeCleanup()
        val exporter = exporter(opener, cleanup)

        val result = exporter.export(
            ARTIFACT_ID,
            source,
            ExportDestination("create-document-write-failure", "capture.png"),
        )

        assertTrue(result is SafBinaryExportResult.TargetWriteFailed)
        assertFalse(result is SafBinaryExportResult.Success)
        assertTrue(target.closed)
        assertFalse(target.finished)
        assertEquals(cleanup.artifactIds, listOf(ARTIFACT_ID))
    }

    @Test
    fun `unreadable or incomplete selected target is never reported successful`() = runDataTest {
        val source = FakeArtifactSource(ByteArray(10) { 0x2A })
        val target = FakeBinaryTarget(reportedReadableSize = 9L)
        val cleanup = FakeCleanup()
        val exporter = exporter(FakeDestinationOpener(target), cleanup)

        val result = exporter.export(
            ARTIFACT_ID,
            source,
            ExportDestination("create-document-incomplete", "capture.png"),
        )

        assertTrue(result is SafBinaryExportResult.TargetUnreadable)
        assertFalse(result is SafBinaryExportResult.Success)
        assertTrue(target.closed)
        assertEquals(cleanup.artifactIds, listOf(ARTIFACT_ID))
    }

    @Test
    fun `source cleanup failure is explicit even after complete target write`() = runDataTest {
        val source = FakeArtifactSource("complete".encodeToByteArray())
        val target = FakeBinaryTarget()
        val cleanup = FakeCleanup(
            result = ArtifactStoreResult.Failure(ArtifactStoreError.CLEANUP_FAILED),
        )
        val exporter = exporter(FakeDestinationOpener(target), cleanup)

        val result = exporter.export(
            ARTIFACT_ID,
            source,
            ExportDestination("create-document-cleanup-failure", "capture.png"),
        )

        assertTrue(target.finished)
        assertEquals(target.readableSize, source.sizeBytes)
        assertTrue(result is SafBinaryExportResult.SourceCleanupFailed)
        assertFalse(result is SafBinaryExportResult.Success)
    }

    @Test
    fun `exporter never creates quick action part files in user tree`() = runDataTest {
        val source = FakeArtifactSource("one-document".encodeToByteArray())
        val target = FakeBinaryTarget()
        val opener = FakeDestinationOpener(target)
        val exporter = exporter(opener, FakeCleanup())
        val selected = ExportDestination(
            opaqueId = "create-document-direct-target",
            displayName = "recording.mp4",
        )

        exporter.export(ARTIFACT_ID, source, selected)

        assertEquals(opener.destinations, listOf(selected))
        assertTrue(
            opener.destinations.none { it.displayName.orEmpty().endsWith(".part") },
            "CreateDocument already selected the final target; exporter must not create provider-side .part files",
        )
    }

    @Test
    fun `public export contract exposes no URI ContentResolver or stream type`() {
        val signatures = listOf(
            ExportDestination::class.java,
            SafBinaryExportResult::class.java,
            SafBinaryExporter::class.java,
        ).flatMap { type ->
            buildList {
                add(type.name)
                type.fields.forEach { add(it.genericType.typeName) }
                type.methods.forEach { method ->
                    add(method.genericReturnType.typeName)
                    method.genericParameterTypes.forEach { add(it.typeName) }
                }
            }
        }.joinToString(" ")

        listOf(
            "android.net.Uri",
            "android.content.ContentResolver",
            "java.net.URI",
            "java.io.OutputStream",
        ).forEach { forbidden ->
            assertFalse(signatures.contains(forbidden), "Public export contract leaks $forbidden")
        }
    }

    private fun exporter(
        opener: FakeDestinationOpener,
        cleanup: FakeCleanup,
    ) = SafBinaryExporter(
        destinationOpener = opener,
        discardSource = cleanup::discard,
        chunkSizeBytes = 4,
    )

    private fun runDataTest(block: suspend () -> Unit) = runBlocking { block() }

    private class FakeArtifactSource(
        private val content: ByteArray,
    ) : ArtifactSource {
        override val sizeBytes: Long = content.size.toLong()
        var readCalls = 0
            private set

        override suspend fun read(
            sourceOffset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): ArtifactStoreResult<Int> {
            readCalls++
            if (sourceOffset >= content.size) return ArtifactStoreResult.Success(0)
            val count = minOf(length, content.size - sourceOffset.toInt())
            content.copyInto(
                destination = destination,
                destinationOffset = destinationOffset,
                startIndex = sourceOffset.toInt(),
                endIndex = sourceOffset.toInt() + count,
            )
            return ArtifactStoreResult.Success(count)
        }
    }

    private class FakeDestinationOpener(
        private val target: BinaryExportTarget,
    ) : BinaryExportDestinationOpener {
        val destinations = mutableListOf<ExportDestination>()

        override fun open(destination: ExportDestination): BinaryExportTarget {
            destinations += destination
            return target
        }
    }

    private class FakeBinaryTarget(
        private val failAfterBytes: Int? = null,
        private val reportedReadableSize: Long? = null,
    ) : BinaryExportTarget {
        val bytes = ArrayList<Byte>()
        var finished = false
            private set
        var closed = false
            private set

        override fun write(source: ByteArray, offset: Int, length: Int) {
            if (failAfterBytes != null && bytes.size + length > failAfterBytes) {
                throw IOException("synthetic provider write failure")
            }
            repeat(length) { index -> bytes += source[offset + index] }
        }

        override fun finish() {
            finished = true
        }

        override fun readableSizeBytes(): Long? = reportedReadableSize ?: bytes.size.toLong()

        override fun close() {
            closed = true
        }

        val readableSize: Long?
            get() = readableSizeBytes()
    }

    private class FakeCleanup(
        private val result: ArtifactStoreResult<Unit> = ArtifactStoreResult.Success(Unit),
    ) {
        val artifactIds = mutableListOf<ArtifactId>()
        val reasons = mutableListOf<ArtifactTerminationReason>()

        suspend fun discard(
            artifactId: ArtifactId,
            reason: ArtifactTerminationReason,
        ): ArtifactStoreResult<Unit> {
            artifactIds += artifactId
            reasons += reason
            return result
        }
    }

    private companion object {
        val ARTIFACT_ID = ArtifactId("artifact-export-source")
    }
}
