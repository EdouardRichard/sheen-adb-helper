package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbEndpoint
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class ProtocolShellResponse(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val streamsSeparated: Boolean,
    val wasTruncated: Boolean,
)

internal sealed interface ProtocolShellPacket {
    data class StandardOutput(val bytes: ByteArray) : ProtocolShellPacket
    data class StandardError(val bytes: ByteArray) : ProtocolShellPacket
    data class Exit(val code: Int) : ProtocolShellPacket
}

internal interface ProtocolShellStream : AutoCloseable {
    fun read(): ProtocolShellPacket
}

internal interface ProtocolShellCommand : AutoCloseable {
    fun execute(): ProtocolShellResponse
}

internal enum class ProtocolSyncVersion { V1, V2 }

internal data class ProtocolRemoteEntry(
    val name: String,
    val mode: Int,
    val size: Long,
    val modifiedEpochSeconds: Long,
    val deviceId: Long?,
    val inode: Long?,
)

internal data class ProtocolRemoteStat(
    val mode: Int,
    val size: Long,
    val modifiedEpochSeconds: Long,
    val deviceId: Long?,
    val inode: Long?,
)

internal data class ProtocolDirectoryListing(
    val version: ProtocolSyncVersion,
    val entries: List<ProtocolRemoteEntry>,
)

internal interface ProtocolSyncStream : AutoCloseable {
    val version: ProtocolSyncVersion
    val transferVersion: ProtocolSyncVersion get() = version
    fun list(path: String): List<ProtocolRemoteEntry>
    fun lstat(path: String): ProtocolRemoteStat
    fun stat(path: String): ProtocolRemoteStat
    fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit): Unit =
        throw UnsupportedOperationException("sync receive unavailable")
    fun send(
        path: String,
        mode: Int,
        modifiedEpochMillis: Long,
        source: (ByteArray) -> Int,
    ): Unit = throw UnsupportedOperationException("sync send unavailable")
}

internal interface AdbProtocolClient : AutoCloseable {
    fun execute(command: String): ProtocolShellResponse
    fun openShellCommand(command: String): ProtocolShellCommand = object : ProtocolShellCommand {
        override fun execute(): ProtocolShellResponse = this@AdbProtocolClient.execute(command)
        override fun close() = Unit
    }
    fun openShellStream(command: String): ProtocolShellStream
    fun openSync(): ProtocolSyncStream = throw UnsupportedOperationException("sync unavailable")
}

internal object KadbRemoteFileProtocol {
    const val MAX_TRANSFER_CHUNK_BYTES = 64 * 1024

    suspend fun list(
        client: AdbProtocolClient,
        path: String,
        timeout: Duration,
    ): ProtocolDirectoryListing = withTimeout(timeout) {
        runInterruptible(Dispatchers.IO) {
            client.openSync().use { sync ->
                ProtocolDirectoryListing(
                    version = sync.version,
                    entries = sync.list(path).filterNot { it.name == "." || it.name == ".." },
                )
            }
        }
    }

    suspend fun lstat(
        client: AdbProtocolClient,
        path: String,
        timeout: Duration,
    ): ProtocolRemoteStat = withTimeout(timeout) {
        runInterruptible(Dispatchers.IO) { client.openSync().use { it.lstat(path) } }
    }

    suspend fun stat(
        client: AdbProtocolClient,
        path: String,
        timeout: Duration,
    ): ProtocolRemoteStat = withTimeout(timeout) {
        runInterruptible(Dispatchers.IO) { client.openSync().use { it.stat(path) } }
    }

    suspend fun receive(
        client: AdbProtocolClient,
        path: String,
        destination: OutputStream,
        noProgressTimeout: Duration,
        cancellationGrace: Duration = 3.seconds,
        onForcedSessionClose: () -> Unit = { client.close() },
        progress: (Long) -> Unit = {},
    ): Long {
        require(noProgressTimeout.isPositive())
        require(cancellationGrace.isPositive())
        var retried = false
        while (true) {
            val transferred = AtomicLong(0L)
            var syncVersion: ProtocolSyncVersion? = null
            try {
                return runTransferWithNoProgressTimeout(
                    client,
                    noProgressTimeout,
                    cancellationGrace,
                    onForcedSessionClose,
                ) { sync, markProgress ->
                    syncVersion = sync.transferVersion
                    sync.recv(path) { buffer, offset, length ->
                        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
                        var cursor = offset
                        var remaining = length
                        while (remaining > 0) {
                            val count = minOf(remaining, MAX_TRANSFER_CHUNK_BYTES)
                            try {
                                destination.write(buffer, cursor, count)
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                throw ProtocolLocalDestinationException(error)
                            }
                            val total = transferred.addAndGet(count.toLong())
                            markProgress()
                            progress(total)
                            cursor += count
                            remaining -= count
                        }
                    }
                    try {
                        destination.flush()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        throw ProtocolLocalDestinationException(error)
                    }
                    transferred.get()
                }
            } catch (error: Throwable) {
                if (shouldRetryLegacyZeroByteTransfer(error, syncVersion, transferred.get(), retried)) {
                    retried = true
                    continue
                }
                throw error
            }
        }
    }

    suspend fun send(
        client: AdbProtocolClient,
        path: String,
        source: InputStream,
        mode: Int,
        modifiedEpochMillis: Long,
        noProgressTimeout: Duration,
        cancellationGrace: Duration = 3.seconds,
        onForcedSessionClose: () -> Unit = { client.close() },
        progress: (Long) -> Unit = {},
    ): Long {
        require(noProgressTimeout.isPositive())
        require(cancellationGrace.isPositive())
        var retried = false
        while (true) {
            val transferred = AtomicLong(0L)
            var syncVersion: ProtocolSyncVersion? = null
            try {
                return runTransferWithNoProgressTimeout(
                    client,
                    noProgressTimeout,
                    cancellationGrace,
                    onForcedSessionClose,
                ) { sync, markProgress ->
                    syncVersion = sync.transferVersion
                    sync.send(path, mode, modifiedEpochMillis) { requested ->
                        val maximum = minOf(requested.size, MAX_TRANSFER_CHUNK_BYTES)
                        val count = try {
                            var read = source.read(requested, 0, maximum)
                            if (read == 0) {
                                val single = source.read()
                                read = if (single < 0) -1 else 1.also { requested[0] = single.toByte() }
                            }
                            read
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            throw ProtocolLocalSourceException(error)
                        }
                        if (count > 0) {
                            val total = transferred.addAndGet(count.toLong())
                            markProgress()
                            progress(total)
                        }
                        count
                    }
                    transferred.get()
                }
            } catch (error: Throwable) {
                if (shouldRetryLegacyZeroByteTransfer(error, syncVersion, transferred.get(), retried)) {
                    retried = true
                    continue
                }
                throw error
            }
        }
    }

    private fun shouldRetryLegacyZeroByteTransfer(
        error: Throwable,
        version: ProtocolSyncVersion?,
        transferredBytes: Long,
        alreadyRetried: Boolean,
    ): Boolean {
        if (alreadyRetried || version != ProtocolSyncVersion.V1 || transferredBytes != 0L) return false
        if (error is CancellationException || error is ProtocolNoProgressTimeoutException) return false
        if (error is ProtocolLocalSourceException || error is ProtocolLocalDestinationException) return false
        return generateSequence(error) { it.cause }
            .take(8)
            .any { it is EOFException || it.javaClass.simpleName.contains("StreamClosed", ignoreCase = true) }
    }

    private suspend fun <T> runTransferWithNoProgressTimeout(
        client: AdbProtocolClient,
        noProgressTimeout: Duration,
        cancellationGrace: Duration,
        onForcedSessionClose: () -> Unit,
        transfer: (ProtocolSyncStream, markProgress: () -> Unit) -> T,
    ): T = coroutineScope {
        val lastProgressNanos = AtomicLong(System.nanoTime())
        val timedOut = AtomicBoolean(false)
        val forcedClosed = AtomicBoolean(false)
        val openedSync = AtomicReference<ProtocolSyncStream?>()
        val forceCloseSession = {
            if (forcedClosed.compareAndSet(false, true)) {
                runCatching(onForcedSessionClose)
            }
        }
        val worker = async(Dispatchers.IO) {
            runInterruptible {
                client.openSync().use { sync ->
                    openedSync.set(sync)
                    transfer(sync) { lastProgressNanos.set(System.nanoTime()) }
                }
            }
        }
        val pollMillis = (noProgressTimeout.inWholeMilliseconds / 4).coerceIn(1L, 100L)
        val watchdog = launch {
            while (worker.isActive) {
                delay(pollMillis)
                if (System.nanoTime() - lastProgressNanos.get() >= noProgressTimeout.inWholeNanoseconds) {
                    timedOut.set(true)
                    runCatching { openedSync.get()?.close() }
                    worker.cancel()
                    val released = withTimeoutOrNull(cancellationGrace) {
                        worker.join()
                        true
                    } ?: false
                    if (!released) forceCloseSession()
                    break
                }
            }
        }
        try {
            worker.await()
        } catch (error: CancellationException) {
            if (timedOut.get()) throw ProtocolNoProgressTimeoutException()
            runCatching { openedSync.get()?.close() }
            withContext(NonCancellable) {
                val released = withTimeoutOrNull(cancellationGrace) {
                    worker.join()
                    true
                } ?: false
                if (!released) {
                    forceCloseSession()
                    withTimeoutOrNull(cancellationGrace) { worker.join() }
                }
            }
            throw error
        } finally {
            withContext(NonCancellable) { watchdog.cancelAndJoin() }
        }
    }
}

internal class ProtocolNoProgressTimeoutException : java.io.IOException()

internal class ProtocolLocalSourceException(cause: Throwable) : IOException(cause)

internal class ProtocolLocalDestinationException(cause: Throwable) : IOException(cause)

internal class ProtocolCommandStreamException : java.io.IOException()

internal interface AdbProtocolClientFactory {
    fun open(endpoint: AdbEndpoint): AdbProtocolClient
    suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray)
    fun clearIdentity()
}
