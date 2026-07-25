package com.sheen.adb.data

import android.content.Context
import android.net.Uri
import java.io.OutputStream
sealed interface SafBinaryExportResult {
    data class Success(val destinationName: String?) : SafBinaryExportResult
    data object UserCancelled : SafBinaryExportResult
    data object SourceReadFailed : SafBinaryExportResult
    data object TargetWriteFailed : SafBinaryExportResult
    data object TargetUnreadable : SafBinaryExportResult
    data object SourceCleanupFailed : SafBinaryExportResult
}

internal interface BinaryExportTarget : AutoCloseable {
    fun write(source: ByteArray, offset: Int, length: Int)
    fun finish()
    fun readableSizeBytes(): Long?
}

internal fun interface BinaryExportDestinationOpener {
    fun open(destination: ExportDestination): BinaryExportTarget
}

class SafBinaryExporter internal constructor(
    private val destinationOpener: BinaryExportDestinationOpener,
    private val discardSource: suspend (ArtifactId, ArtifactTerminationReason) -> ArtifactStoreResult<Unit>,
    private val chunkSizeBytes: Int = 64 * 1024,
) {
    constructor(
        context: Context,
        artifactStore: QuickActionArtifactStore,
    ) : this(
        destinationOpener = ContentResolverDestinationOpener(context),
        discardSource = artifactStore::discard,
    )

    init {
        require(chunkSizeBytes > 0)
    }

    suspend fun export(
        artifactId: ArtifactId,
        source: ArtifactSource,
        destination: ExportDestination?,
    ): SafBinaryExportResult {
        if (destination == null) {
            return cleanupResult(
                primary = SafBinaryExportResult.UserCancelled,
                artifactId = artifactId,
                reason = ArtifactTerminationReason.CANCELLED,
            )
        }

        var target: BinaryExportTarget? = null
        val primary = try {
            target = destinationOpener.open(destination)
            val buffer = ByteArray(chunkSizeBytes)
            var offset = 0L
            var failure: SafBinaryExportResult? = null
            while (offset < source.sizeBytes) {
                val wanted = minOf(buffer.size.toLong(), source.sizeBytes - offset).toInt()
                when (val read = source.read(offset, buffer, 0, wanted)) {
                    is ArtifactStoreResult.Failure -> {
                        failure = SafBinaryExportResult.SourceReadFailed
                        break
                    }
                    is ArtifactStoreResult.Success -> {
                        if (read.value <= 0 || read.value > wanted) {
                            failure = SafBinaryExportResult.SourceReadFailed
                            break
                        }
                        target.write(buffer, 0, read.value)
                        offset += read.value
                    }
                }
            }
            if (failure != null) {
                failure
            } else {
                target.finish()
                if (target.readableSizeBytes() == source.sizeBytes) {
                    SafBinaryExportResult.Success(destination.displayName)
                } else {
                    SafBinaryExportResult.TargetUnreadable
                }
            }
        } catch (_: Throwable) {
            SafBinaryExportResult.TargetWriteFailed
        } finally {
            runCatching { target?.close() }
        }

        return cleanupResult(
            primary = primary,
            artifactId = artifactId,
            reason = if (primary is SafBinaryExportResult.Success) {
                ArtifactTerminationReason.SUCCEEDED
            } else {
                ArtifactTerminationReason.FAILED
            },
        )
    }

    private suspend fun cleanupResult(
        primary: SafBinaryExportResult,
        artifactId: ArtifactId,
        reason: ArtifactTerminationReason,
    ): SafBinaryExportResult = when (discardSource(artifactId, reason)) {
        is ArtifactStoreResult.Success -> primary
        is ArtifactStoreResult.Failure -> SafBinaryExportResult.SourceCleanupFailed
    }
}

private class ContentResolverDestinationOpener(
    context: Context,
) : BinaryExportDestinationOpener {
    private val resolver = context.contentResolver

    override fun open(destination: ExportDestination): BinaryExportTarget {
        val target = Uri.parse(destination.opaqueId)
        val output = requireNotNull(resolver.openOutputStream(target, "w"))
        return ContentResolverBinaryExportTarget(
            output = output,
            readableSize = {
                resolver.openInputStream(target)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                    }
                    total
                }
            },
        )
    }
}

private class ContentResolverBinaryExportTarget(
    private val output: OutputStream,
    private val readableSize: () -> Long?,
) : BinaryExportTarget {
    private var finished = false

    override fun write(source: ByteArray, offset: Int, length: Int) {
        check(!finished)
        output.write(source, offset, length)
    }

    override fun finish() {
        output.flush()
        output.close()
        finished = true
    }

    override fun readableSizeBytes(): Long? {
        check(finished)
        return readableSize()
    }

    override fun close() {
        runCatching { output.close() }
    }
}
