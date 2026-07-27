package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.ApkComponent
import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.internal.applications.ApkPackagePolicy
import com.sheen.adb.core.internal.applications.ApplicationPackageProtocol
import com.sheen.adb.core.internal.applications.InstalledApkComponent
import com.sheen.adb.core.internal.applications.InstalledComponentDecision
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.UUID
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

internal interface ProtocolInteractiveShellStream : AutoCloseable {
    fun read(): ProtocolShellPacket
    fun write(bytes: ByteArray)
    fun closeInput()
    fun closeOutput()
}

internal class ProtocolInteractiveShell(
    private val stream: ProtocolInteractiveShellStream,
) : AutoCloseable {
    private val writeMutex = Mutex()
    private val decodedPackets = ArrayDeque<ProtocolShellPacket>()
    private val readyMarker = "__SHEEN_READY_${UUID.randomUUID().toString().replace("-", "")}__"
    private val readyMarkerBytes = readyMarker.encodeToByteArray()
    @Volatile
    private var awaitingReadyMarker = false
    private var retainedStandardOutput = ByteArray(0)

    fun read(): ProtocolShellPacket {
        decodedPackets.pollFirst()?.let { return it }
        while (true) {
            when (val packet = stream.read()) {
                is ProtocolShellPacket.StandardOutput -> decodeStandardOutput(packet.bytes)
                is ProtocolShellPacket.Exit -> {
                    flushRetainedStandardOutput()
                    decodedPackets.addLast(packet)
                }
                else -> decodedPackets.addLast(packet)
            }
            decodedPackets.pollFirst()?.let { return it }
        }
    }

    suspend fun sendSubmittedCommand(completeCommand: String) {
        val submittedCommand = buildString {
            append(completeCommand)
            append('\n')
            append("printf '")
            append(readyMarker)
            append("'")
            append('\n')
        }
        val submittedPayload = submittedCommand.encodeToByteArray()
        writeMutex.withLock {
            awaitingReadyMarker = true
            try {
                runInterruptible(Dispatchers.IO) { stream.write(submittedPayload) }
            } catch (error: Throwable) {
                awaitingReadyMarker = false
                retainedStandardOutput = ByteArray(0)
                throw error
            }
        }
    }

    suspend fun sendTerminalInput(terminalInput: TerminalInput) {
        val encoded = TerminalInputEncoder.encode(terminalInput)
        writeMutex.withLock {
            runInterruptible(Dispatchers.IO) { stream.write(encoded) }
        }
    }

    fun closeInput() = stream.closeInput()

    fun closeOutput() = stream.closeOutput()

    override fun close() = stream.close()

    private fun decodeStandardOutput(bytes: ByteArray) {
        if (!awaitingReadyMarker) {
            if (bytes.isNotEmpty()) decodedPackets.addLast(ProtocolShellPacket.StandardOutput(bytes))
            return
        }
        val combined = retainedStandardOutput + bytes
        val markerIndex = combined.indexOf(readyMarkerBytes)
        if (markerIndex >= 0) {
            val beforeMarker = combined.copyOfRange(0, markerIndex)
            val afterMarker = combined.copyOfRange(markerIndex + readyMarkerBytes.size, combined.size)
            retainedStandardOutput = ByteArray(0)
            awaitingReadyMarker = false
            if (beforeMarker.isNotEmpty()) {
                decodedPackets.addLast(ProtocolShellPacket.StandardOutput(beforeMarker))
            }
            // An empty stdout packet is an internal command-completion boundary. Raw streams never
            // produce empty stdout packets: EOF is mapped to ProtocolShellPacket.Exit.
            decodedPackets.addLast(ProtocolShellPacket.StandardOutput(ByteArray(0)))
            if (afterMarker.isNotEmpty()) {
                decodedPackets.addLast(ProtocolShellPacket.StandardOutput(afterMarker))
            }
            return
        }

        val retainedCount = minOf(readyMarkerBytes.size - 1, combined.size)
        val emitCount = combined.size - retainedCount
        if (emitCount > 0) {
            decodedPackets.addLast(
                ProtocolShellPacket.StandardOutput(combined.copyOfRange(0, emitCount)),
            )
        }
        retainedStandardOutput = combined.copyOfRange(emitCount, combined.size)
    }

    private fun flushRetainedStandardOutput() {
        if (retainedStandardOutput.isNotEmpty()) {
            decodedPackets.addLast(ProtocolShellPacket.StandardOutput(retainedStandardOutput))
            retainedStandardOutput = ByteArray(0)
        }
        awaitingReadyMarker = false
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        if (size < needle.size) return -1
        for (start in 0..size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }
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

internal data class ProtocolVerifiedReceiveReceipt(
    val before: ProtocolRemoteStat,
    val after: ProtocolRemoteStat,
    val transferredBytes: Long,
)

internal class ProtocolReceiveDigestFallbackRequired(
    val before: ProtocolRemoteStat,
) : IOException()

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
    fun openInteractiveShell(): ProtocolInteractiveShellStream =
        throw UnsupportedOperationException("interactive shell unavailable")
    fun openSync(): ProtocolSyncStream = throw UnsupportedOperationException("sync unavailable")
}

internal data class ResolvedApkComponent(
    val publicComponent: ApkComponent,
    val remotePath: String,
)

internal class ProtocolApkComponentException : IOException()

internal object ApkExtractionProtocol {
    suspend fun discover(
        client: AdbProtocolClient,
        userId: Int,
        packageName: String,
        timeout: Duration,
    ): List<ResolvedApkComponent> = withTimeout(timeout) {
        val response = runInterruptible(Dispatchers.IO) {
            client.openShellCommand(
                ApplicationPackageProtocol.installedPaths(userId, packageName),
            ).use(ProtocolShellCommand::execute)
        }
        if (response.exitCode != 0 || response.stderr.isNotBlank()) {
            throw ProtocolApkComponentException()
        }
        val paths = response.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line ->
                if (!line.startsWith("package:")) throw ProtocolApkComponentException()
                line.removePrefix("package:").trim()
            }
            .toList()
        val accepted = ApkPackagePolicy.resolveInstalledComponents(paths)
            as? InstalledComponentDecision.Accepted
            ?: throw ProtocolApkComponentException()
        accepted.components.map(::toResolvedComponent)
    }

    private fun toResolvedComponent(component: InstalledApkComponent) = ResolvedApkComponent(
        publicComponent = ApkComponent(
            componentId = component.componentId,
            role = component.role,
            displayName = component.displayName,
            expectedSizeBytes = null,
        ),
        remotePath = component.remotePath,
    )
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
                        if (Thread.currentThread().isInterrupted) throw CancellationException()
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

    suspend fun receiveVerified(
        client: AdbProtocolClient,
        path: String,
        destination: OutputStream,
        noProgressTimeout: Duration,
        cancellationGrace: Duration = 3.seconds,
        onForcedSessionClose: () -> Unit = { client.close() },
        progress: (Long) -> Unit = {},
    ): ProtocolVerifiedReceiveReceipt {
        require(noProgressTimeout.isPositive())
        require(cancellationGrace.isPositive())
        val transferred = AtomicLong(0L)
        return runTransferWithNoProgressTimeout(
            client,
            noProgressTimeout,
            cancellationGrace,
            onForcedSessionClose,
        ) { sync, markProgress ->
            val before = sync.stat(path)
            if (before.size < 0L || before.modifiedEpochSeconds <= 0L) {
                throw ProtocolReceiveDigestFallbackRequired(before)
            }
            sync.recv(path) { buffer, offset, length ->
                if (Thread.currentThread().isInterrupted) throw CancellationException()
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
            ProtocolVerifiedReceiveReceipt(
                before = before,
                after = sync.stat(path),
                transferredBytes = transferred.get(),
            )
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
                        if (Thread.currentThread().isInterrupted) throw CancellationException()
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

internal class ProtocolConnectionHandshakeTimeoutException(cause: Throwable) : java.io.IOException(cause)

internal interface AdbProtocolClientFactory {
    fun open(endpoint: AdbEndpoint): AdbProtocolClient
    fun openForConnectionProbe(endpoint: AdbEndpoint): AdbConnectionProbeClient =
        AdbConnectionProbeClient(open(endpoint), reusableForSession = true)
    suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray)
    fun clearIdentity()
}

internal data class AdbConnectionProbeClient(
    val client: AdbProtocolClient,
    val reusableForSession: Boolean,
)
