package com.sheen.adb.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
@JvmInline
value class ArtifactId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

enum class ArtifactFormat {
    PNG,
    MP4,
}

enum class ArtifactStoreError {
    SIZE_LIMIT,
    EMPTY,
    INVALID_FORMAT,
    NO_SPACE,
    IO,
    NOT_FOUND,
    CLEANUP_FAILED,
}

enum class ArtifactTerminationReason {
    SUCCEEDED,
    CANCELLED,
    DISCONNECTED,
    EXPIRED,
    STARTUP,
    FAILED,
}

sealed interface ArtifactStoreResult<out T> {
    data class Success<T>(val value: T) : ArtifactStoreResult<T>
    data class Failure(val error: ArtifactStoreError) : ArtifactStoreResult<Nothing>
}

data class ArtifactCreateRequest(
    val expectedSessionId: String,
    val format: ArtifactFormat,
    val maxBytes: Long,
    val expiresAtMillis: Long,
) {
    init {
        require(expectedSessionId.isNotBlank())
        require(maxBytes > 0L)
    }
}

data class ArtifactRef(
    val artifactId: ArtifactId,
    val expectedSessionId: String,
    val format: ArtifactFormat,
    val sizeBytes: Long,
    val expiresAtMillis: Long,
)

interface ArtifactSink {
    val bytesWritten: Long

    suspend fun write(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ): ArtifactStoreResult<Unit>
}

interface ArtifactSource {
    val sizeBytes: Long

    suspend fun read(
        sourceOffset: Long,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ): ArtifactStoreResult<Int>
}

data class ArtifactWriteHandle(
    val artifactId: ArtifactId,
    val expectedSessionId: String,
    val format: ArtifactFormat,
    val maxBytes: Long,
    val expiresAtMillis: Long,
    val sink: ArtifactSink,
)

data class ArtifactCleanupResult(
    val deletedCount: Int,
    val failedCount: Int,
)

data class ExportDestination(
    val opaqueId: String,
    val displayName: String?,
) {
    init {
        require(opaqueId.isNotBlank())
    }
}

internal data class ArtifactStorageDescriptor(
    val artifactId: ArtifactId,
    val expectedSessionId: String,
    val format: ArtifactFormat,
    val maxBytes: Long,
    val expiresAtMillis: Long,
)

internal interface QuickActionArtifactBackend {
    fun create(descriptor: ArtifactStorageDescriptor): ArtifactStoreError?

    fun append(
        artifactId: ArtifactId,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): ArtifactStoreError?

    fun read(
        artifactId: ArtifactId,
        sourceOffset: Long,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ): ArtifactStoreResult<Int>

    fun descriptor(artifactId: ArtifactId): ArtifactStorageDescriptor?

    fun size(artifactId: ArtifactId): Long?

    fun allDescriptors(): List<ArtifactStorageDescriptor>

    fun delete(artifactId: ArtifactId): Boolean
}

class QuickActionArtifactStore internal constructor(
    private val backend: QuickActionArtifactBackend,
    private val nowMillis: () -> Long,
    private val artifactIdFactory: () -> ArtifactId,
) {
    constructor(context: Context) : this(
        backend = FileQuickActionArtifactBackend(
            File(context.cacheDir, PRIVATE_DIRECTORY_NAME),
        ),
        nowMillis = System::currentTimeMillis,
        artifactIdFactory = { ArtifactId(UUID.randomUUID().toString()) },
    )

    suspend fun create(request: ArtifactCreateRequest): ArtifactStoreResult<ArtifactWriteHandle> {
        val artifactId = artifactIdFactory()
        val descriptor = ArtifactStorageDescriptor(
            artifactId = artifactId,
            expectedSessionId = request.expectedSessionId,
            format = request.format,
            maxBytes = request.maxBytes,
            expiresAtMillis = request.expiresAtMillis,
        )
        backend.create(descriptor)?.let { return ArtifactStoreResult.Failure(it) }
        val sink = BackendArtifactSink(descriptor)
        return ArtifactStoreResult.Success(
            ArtifactWriteHandle(
                artifactId = artifactId,
                expectedSessionId = request.expectedSessionId,
                format = request.format,
                maxBytes = request.maxBytes,
                expiresAtMillis = request.expiresAtMillis,
                sink = sink,
            ),
        )
    }

    suspend fun complete(handle: ArtifactWriteHandle): ArtifactStoreResult<ArtifactRef> {
        val descriptor = backend.descriptor(handle.artifactId)
            ?: return ArtifactStoreResult.Failure(ArtifactStoreError.NOT_FOUND)
        val size = backend.size(handle.artifactId)
            ?: return failAndDelete(handle.artifactId, ArtifactStoreError.IO)
        if (size == 0L) return failAndDelete(handle.artifactId, ArtifactStoreError.EMPTY)
        if (size > descriptor.maxBytes) {
            return failAndDelete(handle.artifactId, ArtifactStoreError.SIZE_LIMIT)
        }
        val prefix = ByteArray(minOf(size.toInt(), FORMAT_PREFIX_BYTES))
        when (val read = backend.read(handle.artifactId, 0L, prefix, 0, prefix.size)) {
            is ArtifactStoreResult.Failure -> return failAndDelete(handle.artifactId, read.error)
            is ArtifactStoreResult.Success -> if (read.value != prefix.size) {
                return failAndDelete(handle.artifactId, ArtifactStoreError.IO)
            }
        }
        if (!descriptor.format.matches(prefix)) {
            return failAndDelete(handle.artifactId, ArtifactStoreError.INVALID_FORMAT)
        }
        return ArtifactStoreResult.Success(
            ArtifactRef(
                artifactId = descriptor.artifactId,
                expectedSessionId = descriptor.expectedSessionId,
                format = descriptor.format,
                sizeBytes = size,
                expiresAtMillis = descriptor.expiresAtMillis,
            ),
        )
    }

    suspend fun openSource(reference: ArtifactRef): ArtifactStoreResult<ArtifactSource> {
        val descriptor = backend.descriptor(reference.artifactId)
            ?: return ArtifactStoreResult.Failure(ArtifactStoreError.NOT_FOUND)
        val size = backend.size(reference.artifactId)
            ?: return ArtifactStoreResult.Failure(ArtifactStoreError.IO)
        if (descriptor.expectedSessionId != reference.expectedSessionId || size != reference.sizeBytes) {
            return ArtifactStoreResult.Failure(ArtifactStoreError.IO)
        }
        return ArtifactStoreResult.Success(object : ArtifactSource {
            override val sizeBytes: Long = size

            override suspend fun read(
                sourceOffset: Long,
                destination: ByteArray,
                destinationOffset: Int,
                length: Int,
            ): ArtifactStoreResult<Int> =
                backend.read(reference.artifactId, sourceOffset, destination, destinationOffset, length)
        })
    }

    suspend fun discard(
        artifactId: ArtifactId,
        reason: ArtifactTerminationReason,
    ): ArtifactStoreResult<Unit> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredReason = reason
        backend.delete(artifactId)
        return ArtifactStoreResult.Success(Unit)
    }

    suspend fun cleanupExpired(atMillis: Long = nowMillis()): ArtifactCleanupResult =
        cleanup(backend.allDescriptors().filter { it.expiresAtMillis <= atMillis })

    suspend fun cleanupAll(reason: ArtifactTerminationReason): ArtifactCleanupResult {
        @Suppress("UNUSED_VARIABLE")
        val ignoredReason = reason
        return cleanup(backend.allDescriptors())
    }

    private fun cleanup(descriptors: List<ArtifactStorageDescriptor>): ArtifactCleanupResult {
        var deleted = 0
        var failed = 0
        descriptors.forEach {
            if (backend.delete(it.artifactId)) deleted++ else failed++
        }
        return ArtifactCleanupResult(deleted, failed)
    }

    private fun <T> failAndDelete(
        artifactId: ArtifactId,
        error: ArtifactStoreError,
    ): ArtifactStoreResult<T> {
        backend.delete(artifactId)
        return ArtifactStoreResult.Failure(error)
    }

    private inner class BackendArtifactSink(
        private val descriptor: ArtifactStorageDescriptor,
    ) : ArtifactSink {
        private var terminal = false

        override val bytesWritten: Long
            get() = backend.size(descriptor.artifactId) ?: 0L

        override suspend fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): ArtifactStoreResult<Unit> {
            if (terminal || offset < 0 || length < 0 || offset + length > bytes.size) {
                return ArtifactStoreResult.Failure(ArtifactStoreError.IO)
            }
            if (bytesWritten + length > descriptor.maxBytes) {
                terminal = true
                return failAndDelete(descriptor.artifactId, ArtifactStoreError.SIZE_LIMIT)
            }
            backend.append(descriptor.artifactId, bytes, offset, length)?.let { error ->
                terminal = true
                return failAndDelete(descriptor.artifactId, error)
            }
            return ArtifactStoreResult.Success(Unit)
        }
    }

    private fun ArtifactFormat.matches(prefix: ByteArray): Boolean = when (this) {
        ArtifactFormat.PNG -> prefix.size >= PNG_SIGNATURE.size &&
            prefix.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
        ArtifactFormat.MP4 -> prefix.size >= 8 &&
            prefix.copyOfRange(4, 8).contentEquals(MP4_FTYP)
    }

    private companion object {
        const val PRIVATE_DIRECTORY_NAME = "quick-action-artifacts"
        const val FORMAT_PREFIX_BYTES = 12
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val MP4_FTYP = byteArrayOf(0x66, 0x74, 0x79, 0x70)
    }
}

private class FileQuickActionArtifactBackend(
    private val directory: File,
) : QuickActionArtifactBackend {
    private val descriptors = ConcurrentHashMap<ArtifactId, ArtifactStorageDescriptor>()

    override fun create(descriptor: ArtifactStorageDescriptor): ArtifactStoreError? = try {
        if (!directory.exists() && !directory.mkdirs()) return ArtifactStoreError.IO
        val file = fileFor(descriptor.artifactId)
        if (!file.createNewFile()) return ArtifactStoreError.IO
        descriptors[descriptor.artifactId] = descriptor
        null
    } catch (_: SecurityException) {
        ArtifactStoreError.IO
    } catch (_: java.io.IOException) {
        ArtifactStoreError.NO_SPACE
    }

    override fun append(
        artifactId: ArtifactId,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): ArtifactStoreError? = try {
        FileOutputStream(fileFor(artifactId), true).use { it.write(bytes, offset, length) }
        null
    } catch (_: SecurityException) {
        ArtifactStoreError.IO
    } catch (_: java.io.IOException) {
        ArtifactStoreError.NO_SPACE
    }

    override fun read(
        artifactId: ArtifactId,
        sourceOffset: Long,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ): ArtifactStoreResult<Int> = try {
        RandomAccessFile(fileFor(artifactId), "r").use {
            it.seek(sourceOffset)
            val read = it.read(destination, destinationOffset, length)
            ArtifactStoreResult.Success(if (read < 0) 0 else read)
        }
    } catch (_: Throwable) {
        ArtifactStoreResult.Failure(ArtifactStoreError.IO)
    }

    override fun descriptor(artifactId: ArtifactId): ArtifactStorageDescriptor? =
        descriptors[artifactId]

    override fun size(artifactId: ArtifactId): Long? =
        fileFor(artifactId).takeIf(File::isFile)?.length()

    override fun allDescriptors(): List<ArtifactStorageDescriptor> {
        val known = descriptors.values.associateBy { it.artifactId }.toMutableMap()
        directory.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .forEach { file ->
                val id = runCatching { ArtifactId(file.name) }.getOrNull() ?: return@forEach
                known.putIfAbsent(
                    id,
                    ArtifactStorageDescriptor(
                        artifactId = id,
                        expectedSessionId = "startup-cleanup",
                        format = ArtifactFormat.PNG,
                        maxBytes = Long.MAX_VALUE,
                        expiresAtMillis = 0L,
                    ),
                )
            }
        return known.values.toList()
    }

    override fun delete(artifactId: ArtifactId): Boolean {
        descriptors.remove(artifactId)
        val file = fileFor(artifactId)
        return !file.exists() || file.delete()
    }

    private fun fileFor(artifactId: ArtifactId): File =
        File(directory, artifactId.value)
}
