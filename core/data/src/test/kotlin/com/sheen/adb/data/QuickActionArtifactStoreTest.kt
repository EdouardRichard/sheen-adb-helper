package com.sheen.adb.data

import java.lang.reflect.Modifier
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionArtifactStoreTest {
    @Test
    fun `bounded private artifacts accept valid PNG and MP4 without exposing storage`() = runBlocking {
        val backend = InMemoryArtifactBackend()
        val store = store(backend)

        val png = create(store, "session-png", ArtifactFormat.PNG, maxBytes = 32L)
        assertSuccess(png.sink.write(validPng()))
        val pngRef = success(store.complete(png))

        val mp4 = create(store, "session-mp4", ArtifactFormat.MP4, maxBytes = 32L)
        assertSuccess(mp4.sink.write(validMp4()))
        val mp4Ref = success(store.complete(mp4))

        assertEquals(pngRef.format, ArtifactFormat.PNG)
        assertEquals(pngRef.sizeBytes, validPng().size.toLong())
        assertEquals(mp4Ref.format, ArtifactFormat.MP4)
        assertEquals(mp4Ref.sizeBytes, validMp4().size.toLong())
        assertEquals(backend.records.size, 2)
        assertTrue(backend.records.values.all { it.privateToApplication })
        assertFalse(pngRef.toString().contains(backend.rootDescription))
        assertFalse(mp4Ref.toString().contains(backend.rootDescription))
    }

    @Test
    fun `size limit empty output and invalid formats fail and remove partial artifacts`() = runBlocking {
        val backend = InMemoryArtifactBackend()
        val store = store(backend)

        val oversized = create(store, "session-limit", ArtifactFormat.PNG, maxBytes = 8L)
        assertFailure(oversized.sink.write(ByteArray(9)), ArtifactStoreError.SIZE_LIMIT)
        assertFalse(backend.records.containsKey(oversized.artifactId))

        val empty = create(store, "session-empty", ArtifactFormat.PNG, maxBytes = 32L)
        assertFailure(store.complete(empty), ArtifactStoreError.EMPTY)
        assertFalse(backend.records.containsKey(empty.artifactId))

        val invalidPng = create(store, "session-invalid-png", ArtifactFormat.PNG, maxBytes = 32L)
        assertSuccess(invalidPng.sink.write("not-png".encodeToByteArray()))
        assertFailure(store.complete(invalidPng), ArtifactStoreError.INVALID_FORMAT)

        val invalidMp4 = create(store, "session-invalid-mp4", ArtifactFormat.MP4, maxBytes = 32L)
        assertSuccess(invalidMp4.sink.write("not-mp4".encodeToByteArray()))
        assertFailure(store.complete(invalidMp4), ArtifactStoreError.INVALID_FORMAT)
        assertTrue(backend.records.isEmpty())
    }

    @Test
    fun `space and IO failures are structured and clean partial data`() = runBlocking {
        val backend = InMemoryArtifactBackend()
        val store = store(backend)

        val noSpace = create(store, "session-space", ArtifactFormat.PNG, maxBytes = 32L)
        backend.nextWriteFailure = ArtifactStoreError.NO_SPACE
        assertFailure(noSpace.sink.write(validPng()), ArtifactStoreError.NO_SPACE)
        assertFalse(backend.records.containsKey(noSpace.artifactId))

        val ioFailure = create(store, "session-io", ArtifactFormat.PNG, maxBytes = 32L)
        backend.nextWriteFailure = ArtifactStoreError.IO
        assertFailure(ioFailure.sink.write(validPng()), ArtifactStoreError.IO)
        assertFalse(backend.records.containsKey(ioFailure.artifactId))
    }

    @Test
    fun `cancel disconnect success and expiry cleanup are explicit and idempotent`() = runBlocking {
        var nowMillis = 1_000L
        val backend = InMemoryArtifactBackend()
        val store = store(backend) { nowMillis }
        val cancelled = create(store, "session-cancel", ArtifactFormat.PNG, expiresAtMillis = 2_000L)
        val disconnected = create(store, "session-disconnect", ArtifactFormat.PNG, expiresAtMillis = 2_000L)
        val delivered = create(store, "session-success", ArtifactFormat.PNG, expiresAtMillis = 2_000L)
        val expired = create(store, "session-expired", ArtifactFormat.PNG, expiresAtMillis = 1_500L)

        assertSuccess(store.discard(cancelled.artifactId, ArtifactTerminationReason.CANCELLED))
        assertSuccess(store.discard(disconnected.artifactId, ArtifactTerminationReason.DISCONNECTED))
        assertSuccess(store.discard(delivered.artifactId, ArtifactTerminationReason.SUCCEEDED))
        assertSuccess(store.discard(delivered.artifactId, ArtifactTerminationReason.SUCCEEDED))
        assertTrue(backend.records.containsKey(expired.artifactId))

        nowMillis = 1_501L
        val cleanup = store.cleanupExpired(nowMillis)
        assertEquals(cleanup.deletedCount, 1)
        assertEquals(cleanup.failedCount, 0)
        assertTrue(backend.records.isEmpty())
        assertEquals(store.cleanupExpired(nowMillis).deletedCount, 0)
    }

    @Test
    fun `next startup cleanup removes interrupted artifacts and remains idempotent`() = runBlocking {
        val backend = InMemoryArtifactBackend()
        val firstProcess = store(backend)
        create(firstProcess, "session-cancelled-process", ArtifactFormat.PNG)
        create(firstProcess, "session-disconnected-process", ArtifactFormat.MP4)
        assertEquals(backend.records.size, 2)

        val restartedProcess = store(backend)
        val firstCleanup = restartedProcess.cleanupAll(ArtifactTerminationReason.STARTUP)
        val secondCleanup = restartedProcess.cleanupAll(ArtifactTerminationReason.STARTUP)

        assertEquals(firstCleanup.deletedCount, 2)
        assertEquals(firstCleanup.failedCount, 0)
        assertEquals(secondCleanup.deletedCount, 0)
        assertEquals(secondCleanup.failedCount, 0)
        assertTrue(backend.records.isEmpty())
    }

    @Test
    fun `public artifact and destination contracts expose only project owned values`() {
        val destination = ExportDestination(
            opaqueId = "destination-fixture",
            displayName = "capture.png",
        )
        assertEquals(destination.displayName, "capture.png")

        val contractTypes = listOf(
            ArtifactId::class.java,
            ArtifactRef::class.java,
            ArtifactSink::class.java,
            ArtifactSource::class.java,
            ArtifactWriteHandle::class.java,
            ArtifactStoreResult::class.java,
            ExportDestination::class.java,
            QuickActionArtifactStore::class.java,
        )
        val forbidden = listOf(
            "java.io.File",
            "java.io.OutputStream",
            "android.content.ContentResolver",
            "android.net.Uri",
            "java.net.URI",
        )

        contractTypes.forEach { type ->
            val publicSignatures = buildList {
                type.declaredFields.filter { Modifier.isPublic(it.modifiers) }
                    .forEach { add("${it.name}:${it.genericType.typeName}") }
                type.declaredMethods.filter { Modifier.isPublic(it.modifiers) }
                    .forEach { method ->
                        add(method.genericReturnType.typeName)
                        method.genericParameterTypes.forEach { add(it.typeName) }
                    }
            }.joinToString(" ")
            forbidden.forEach { leaked ->
                assertFalse(
                    publicSignatures.contains(leaked, ignoreCase = true),
                    "${type.name} leaks $leaked",
                )
            }
            assertFalse(publicSignatures.contains("uri", ignoreCase = true))
            assertFalse(publicSignatures.contains("path", ignoreCase = true))
        }
    }

    private fun store(
        backend: InMemoryArtifactBackend,
        nowMillis: () -> Long = { 1_000L },
    ): QuickActionArtifactStore = QuickActionArtifactStore(
        backend = backend,
        nowMillis = nowMillis,
        artifactIdFactory = { ArtifactId("artifact-${backend.createdCount + 1}") },
    )

    private suspend fun create(
        store: QuickActionArtifactStore,
        sessionId: String,
        format: ArtifactFormat,
        maxBytes: Long = 64L,
        expiresAtMillis: Long = 10_000L,
    ): ArtifactWriteHandle = success(
        store.create(
            ArtifactCreateRequest(
                expectedSessionId = sessionId,
                format = format,
                maxBytes = maxBytes,
                expiresAtMillis = expiresAtMillis,
            ),
        ),
    )

    private fun validPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private fun validMp4(): ByteArray = byteArrayOf(
        0x00, 0x00, 0x00, 0x10, 0x66, 0x74, 0x79, 0x70,
        0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x00, 0x00,
    )

    private fun assertSuccess(result: ArtifactStoreResult<*>) {
        assertTrue(result is ArtifactStoreResult.Success<*>, "Expected success but was $result")
    }

    private fun assertFailure(result: ArtifactStoreResult<*>, expected: ArtifactStoreError) {
        assertEquals((result as ArtifactStoreResult.Failure).error, expected)
    }

    private fun <T> success(result: ArtifactStoreResult<T>): T =
        (result as ArtifactStoreResult.Success<T>).value

    private class InMemoryArtifactBackend : QuickActionArtifactBackend {
        data class Record(
            val descriptor: ArtifactStorageDescriptor,
            val privateToApplication: Boolean = true,
            val bytes: MutableList<Byte> = mutableListOf(),
        )

        val rootDescription = "fixture-private-root"
        val records = linkedMapOf<ArtifactId, Record>()
        var createdCount = 0
        var nextWriteFailure: ArtifactStoreError? = null

        override fun create(descriptor: ArtifactStorageDescriptor): ArtifactStoreError? {
            createdCount++
            records[descriptor.artifactId] = Record(descriptor)
            return null
        }

        override fun append(
            artifactId: ArtifactId,
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): ArtifactStoreError? {
            nextWriteFailure?.let { failure ->
                nextWriteFailure = null
                return failure
            }
            val record = records[artifactId] ?: return ArtifactStoreError.NOT_FOUND
            repeat(length) { index -> record.bytes += bytes[offset + index] }
            return null
        }

        override fun read(
            artifactId: ArtifactId,
            sourceOffset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): ArtifactStoreResult<Int> {
            val record = records[artifactId] ?: return ArtifactStoreResult.Failure(ArtifactStoreError.NOT_FOUND)
            if (sourceOffset >= record.bytes.size) return ArtifactStoreResult.Success(0)
            val count = minOf(length, record.bytes.size - sourceOffset.toInt())
            repeat(count) { index ->
                destination[destinationOffset + index] = record.bytes[sourceOffset.toInt() + index]
            }
            return ArtifactStoreResult.Success(count)
        }

        override fun descriptor(artifactId: ArtifactId): ArtifactStorageDescriptor? =
            records[artifactId]?.descriptor

        override fun size(artifactId: ArtifactId): Long? = records[artifactId]?.bytes?.size?.toLong()

        override fun allDescriptors(): List<ArtifactStorageDescriptor> =
            records.values.map { it.descriptor }

        override fun delete(artifactId: ArtifactId): Boolean = records.remove(artifactId) != null
    }
}
