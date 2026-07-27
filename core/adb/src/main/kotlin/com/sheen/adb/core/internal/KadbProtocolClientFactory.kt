package com.sheen.adb.core.internal

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.flyfishxu.kadb.stream.AdbSyncDirEntry
import com.flyfishxu.kadb.stream.AdbSyncDirEntryV2
import com.flyfishxu.kadb.stream.AdbSyncStat
import com.flyfishxu.kadb.stream.AdbSyncStatV2
import com.sheen.adb.core.AdbEndpoint
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.time.Duration

internal suspend fun stageApk(
    client: AdbProtocolClient,
    stagedRemotePath: String,
    source: InputStream,
    sourceSizeBytes: Long?,
    timeout: Duration,
    noProgressTimeout: Duration,
    progress: (Long) -> Unit,
): Long = withTimeout(timeout) {
    KadbRemoteFileProtocol.send(
        client = client,
        path = stagedRemotePath,
        source = source,
        mode = 0x1A4,
        modifiedEpochMillis = System.currentTimeMillis(),
        noProgressTimeout = noProgressTimeout,
        progress = progress,
    ).also { transferred ->
        if (sourceSizeBytes != null && sourceSizeBytes != transferred) {
            throw ProtocolLocalSourceException(
                IllegalStateException("APK source size changed during staging"),
            )
        }
    }
}

internal suspend fun installStagedApk(
    client: AdbProtocolClient,
    stagedRemotePath: String,
    replaceExisting: Boolean,
    allowDowngrade: Boolean,
    timeout: Duration,
): ProtocolShellResponse = withTimeout(timeout) {
    runInterruptible(Dispatchers.IO) {
        client.openShellCommand(
            com.sheen.adb.core.internal.applications.ApplicationPackageProtocol.stagedInstall(
                stagedRemotePath = stagedRemotePath,
                replaceExisting = replaceExisting,
                allowDowngrade = allowDowngrade,
            ),
        ).use(ProtocolShellCommand::execute)
    }
}

internal suspend fun deleteStagedApk(
    client: AdbProtocolClient,
    stagedRemotePath: String,
    timeout: Duration,
): Boolean = withTimeout(timeout) {
    runInterruptible(Dispatchers.IO) {
        val response = client.openShellCommand(
            com.sheen.adb.core.internal.applications.ApplicationPackageProtocol
                .deleteStagedApk(stagedRemotePath),
        ).use(ProtocolShellCommand::execute)
        response.exitCode == 0
    }
}

internal class KadbProtocolClientFactory(context: Context) : AdbProtocolClientFactory {
    private val privateKeyStore = AndroidKeystorePrivateKeyStore(context.applicationContext)

    init {
        KadbCert.configure(
            store = privateKeyStore,
            policy = KadbCertPolicy(
                keySizeBits = 2048,
                certValidityDays = 3650,
                autoHealInvalidPrivateKey = false,
                subject = KadbCertPolicy.Subject(
                    cn = "Sheen ADB Helper",
                    ou = "Local ADB Host",
                    o = "Sheen",
                ),
            ),
        )
    }

    override fun open(endpoint: AdbEndpoint): AdbProtocolClient =
        openWithSocketTimeout(endpoint, KadbProtocolTimeouts.SOCKET_IO_TIMEOUT_MS)

    override fun openForConnectionProbe(endpoint: AdbEndpoint): AdbConnectionProbeClient =
        AdbConnectionProbeClient(
            client = openWithSocketTimeout(endpoint, CONNECTION_HANDSHAKE_TIMEOUT_MS),
            reusableForSession = false,
        )

    private fun openWithSocketTimeout(
        endpoint: AdbEndpoint,
        socketTimeoutMillis: Int,
    ): AdbProtocolClient {
        KadbCert.ensureReady()
        val kadb = Kadb.create(
            host = endpoint.host,
            port = endpoint.port,
            connectTimeout = CONNECT_TIMEOUT_MS,
            socketTimeout = socketTimeoutMillis,
        )
        return object : AdbProtocolClient {
            override fun execute(command: String): ProtocolShellResponse =
                try {
                    openShellCommand(command).use { it.execute() }
                } catch (error: Throwable) {
                    if (
                        socketTimeoutMillis > 0 &&
                        generateSequence(error) { it.cause }.take(8).any { it is SocketTimeoutException }
                    ) {
                        throw ProtocolConnectionHandshakeTimeoutException(error)
                    }
                    throw error
                }

            override fun openShellCommand(command: String): ProtocolShellCommand {
                val separated = kadb.supportsFeature("shell_v2")
                if (!separated) {
                    val stream = kadb.open("shell:$command")
                    return object : ProtocolShellCommand {
                        override fun execute(): ProtocolShellResponse {
                            val output = BoundedByteTail(MAX_SHELL_OUTPUT_BYTES)
                            val buffer = Buffer()
                            while (true) {
                                val count = stream.source.read(buffer, STREAM_CHUNK_BYTES)
                                if (count < 0) break
                                output.append(buffer.readByteArray())
                            }
                            return ProtocolShellResponse(
                                stdout = output.text(),
                                stderr = "",
                                exitCode = 0,
                                streamsSeparated = false,
                                wasTruncated = output.truncated,
                            )
                        }

                        override fun close() = stream.close()
                    }
                }
                val stream = kadb.openShell(command)
                return object : ProtocolShellCommand {
                    override fun execute(): ProtocolShellResponse {
                        val stdout = BoundedByteTail(MAX_SHELL_OUTPUT_BYTES)
                        val stderr = BoundedByteTail(MAX_SHELL_OUTPUT_BYTES)
                        var exitCode = 0
                        var finished = false
                        while (!finished) {
                            val packet = try {
                                stream.read()
                            } catch (error: Throwable) {
                                throw normalizedStreamReadFailure(error)
                            }
                            when (packet) {
                                is AdbShellPacket.StdOut -> stdout.append(packet.payload)
                                is AdbShellPacket.StdError -> stderr.append(packet.payload)
                                is AdbShellPacket.Exit -> {
                                    exitCode = packet.payload.firstOrNull()?.toInt()?.and(0xff) ?: 0
                                    finished = true
                                }
                            }
                        }
                        return ProtocolShellResponse(
                            stdout = stdout.text(),
                            stderr = stderr.text(),
                            exitCode = exitCode,
                            streamsSeparated = true,
                            wasTruncated = stdout.truncated || stderr.truncated,
                        )
                    }

                    override fun close() = stream.close()
                }
            }

            override fun openShellStream(command: String): ProtocolShellStream {
                if (!kadb.supportsFeature("shell_v2")) {
                    val adbStream = kadb.open("shell:$command")
                    return object : ProtocolShellStream {
                        private var ended = false
                        override fun read(): ProtocolShellPacket {
                            if (ended) return ProtocolShellPacket.Exit(0)
                            val buffer = Buffer()
                            val count = adbStream.source.read(buffer, STREAM_CHUNK_BYTES)
                            if (count < 0) {
                                ended = true
                                return ProtocolShellPacket.Exit(0)
                            }
                            return ProtocolShellPacket.StandardOutput(buffer.readByteArray())
                        }
                        override fun close() = adbStream.close()
                    }
                }
                val stream = kadb.openShell(command)
                return object : ProtocolShellStream {
                    override fun read(): ProtocolShellPacket = try {
                        when (val packet = stream.read()) {
                            is AdbShellPacket.StdOut -> ProtocolShellPacket.StandardOutput(packet.payload)
                            is AdbShellPacket.StdError -> ProtocolShellPacket.StandardError(packet.payload)
                            is AdbShellPacket.Exit -> ProtocolShellPacket.Exit(
                                packet.payload.firstOrNull()?.toInt()?.and(0xff) ?: 0,
                            )
                        }
                    } catch (error: Throwable) {
                        throw normalizedStreamReadFailure(error)
                    }

                    override fun close() = stream.close()
                }
            }

            override fun openInteractiveShell(): ProtocolInteractiveShellStream {
                val stream = kadb.open("shell:")
                return object : ProtocolInteractiveShellStream {
                    private var outputClosed = false

                    override fun read(): ProtocolShellPacket {
                        if (outputClosed) return ProtocolShellPacket.Exit(0)
                        val buffer = Buffer()
                        val count = stream.source.read(buffer, STREAM_CHUNK_BYTES)
                        if (count < 0) {
                            outputClosed = true
                            return ProtocolShellPacket.Exit(0)
                        }
                        return ProtocolShellPacket.StandardOutput(buffer.readByteArray())
                    }

                    override fun write(bytes: ByteArray) {
                        val buffer = Buffer().write(bytes)
                        stream.sink.write(buffer, bytes.size.toLong())
                        stream.sink.flush()
                    }

                    override fun closeInput() {
                        stream.sink.close()
                    }

                    override fun closeOutput() {
                        outputClosed = true
                        stream.source.close()
                    }

                    override fun close() = stream.close()
                }
            }

            override fun openSync(): ProtocolSyncStream {
                val supportsStatV2 = kadb.supportsFeature("stat_v2")
                val supportsListV2 = kadb.supportsFeature("ls_v2")
                val sync = kadb.openSync()
                return object : ProtocolSyncStream {
                    override val version = if (supportsListV2 && supportsStatV2) {
                        ProtocolSyncVersion.V2
                    } else {
                        ProtocolSyncVersion.V1
                    }
                    override val transferVersion = ProtocolSyncVersion.V1

                    override fun list(path: String): List<ProtocolRemoteEntry> = if (supportsListV2) {
                        sync.listV2(path).map { it.toProtocolEntry() }
                    } else {
                        sync.list(path).map { it.toProtocolEntry() }
                    }

                    override fun lstat(path: String): ProtocolRemoteStat = if (supportsStatV2) {
                        sync.lstatV2(path).toProtocolStat()
                    } else {
                        sync.lstat(path).toProtocolStat()
                    }

                    override fun stat(path: String): ProtocolRemoteStat = if (supportsStatV2) {
                        sync.statV2(path).toProtocolStat()
                    } else {
                        sync.lstat(path).toProtocolStat()
                    }

                    override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
                        sync.recv(
                            object : Sink {
                                override fun write(source: Buffer, byteCount: Long) {
                                    var remaining = byteCount
                                    while (remaining > 0L) {
                                        val count = minOf(remaining, SYNC_TRANSFER_CHUNK_BYTES.toLong()).toInt()
                                        val bytes = source.readByteArray(count.toLong())
                                        sink(bytes, 0, bytes.size)
                                        remaining -= count
                                    }
                                }

                                override fun flush() = Unit
                                override fun timeout(): Timeout = Timeout.NONE
                                override fun close() = Unit
                            },
                            path,
                        )
                    }

                    override fun send(
                        path: String,
                        mode: Int,
                        modifiedEpochMillis: Long,
                        source: (ByteArray) -> Int,
                    ) {
                        val permissions = Integer.toOctalString(mode and 0x1FF)
                        val remotePath = shellQuotedPath(path)
                        var firstSegment = true
                        var sourceEnded = false
                        while (!sourceEnded) {
                            val segment = ByteArrayOutputStream(MAX_SHELL_UPLOAD_SEGMENT_BYTES)
                            while (segment.size() < MAX_SHELL_UPLOAD_SEGMENT_BYTES) {
                                val requested = ByteArray(
                                    minOf(
                                        SYNC_TRANSFER_CHUNK_BYTES,
                                        MAX_SHELL_UPLOAD_SEGMENT_BYTES - segment.size(),
                                    ),
                                )
                                val count = source(requested)
                                if (count < 0) {
                                    sourceEnded = true
                                    break
                                }
                                require(count in 1..requested.size)
                                segment.write(requested, 0, count)
                            }
                            if (segment.size() == 0 && !firstSegment) break

                            val uploadCommand = if (firstSegment) {
                                "cat > $remotePath"
                            } else {
                                "cat >> $remotePath"
                            }
                            val uploadStream = kadb.openShell(uploadCommand)
                            try {
                                val bytes = segment.toByteArray()
                                var offset = 0
                                while (offset < bytes.size) {
                                    val end = minOf(offset + SHELL_UPLOAD_FRAME_BYTES, bytes.size)
                                    uploadStream.write(bytes.copyOfRange(offset, end))
                                    offset = end
                                }
                                uploadStream.closeStdin()
                                val response = uploadStream.readAll()
                                if (response.exitCode != 0) {
                                    throw IOException("Remote upload command failed")
                                }
                            } finally {
                                uploadStream.close()
                            }
                            firstSegment = false
                        }
                        val chmod = kadb.shell("chmod $permissions $remotePath")
                        if (chmod.exitCode != 0) {
                            throw IOException("Remote upload permission update failed")
                        }
                    }

                    override fun close() = sync.close()
                }
            }

            override fun close() = kadb.close()
        }
    }

    override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) {
        try {
            Kadb.pair(
                host = endpoint.host,
                port = endpoint.port,
                pairingCode = pairingCode.concatToString(),
                name = "Sheen ADB Helper",
            )
        } finally {
            pairingCode.fill('\u0000')
        }
    }

    override fun clearIdentity() = KadbCert.clear()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val CONNECTION_HANDSHAKE_TIMEOUT_MS = 1_500
        const val MAX_SHELL_OUTPUT_BYTES = 1024 * 1024
        const val STREAM_CHUNK_BYTES = 16_384L
        const val SYNC_TRANSFER_CHUNK_BYTES = 64 * 1024
        const val SHELL_UPLOAD_FRAME_BYTES = 16 * 1024
        const val MAX_SHELL_UPLOAD_SEGMENT_BYTES = 4 * 1024 * 1024

        fun shellQuotedPath(path: String): String = "'${path.replace("'", "'\\''")}'"
    }

    private fun normalizedStreamReadFailure(error: Throwable): Throwable {
        if (error is ProtocolCommandStreamException) return error
        val type = error.javaClass.simpleName
        val message = error.message.orEmpty().lowercase()
        val isCommandEof = error is EOFException || type.contains("AdbStreamClosed") ||
            type.contains("StreamClosed") ||
            (error is IllegalStateException && (
                message.contains("not listening") ||
                    message.contains("stream closed") ||
                    message.contains("closed stream")
                ))
        return if (isCommandEof) ProtocolCommandStreamException() else error
    }

    private fun AdbSyncDirEntry.toProtocolEntry() = ProtocolRemoteEntry(
        name = name,
        mode = mode,
        size = size,
        modifiedEpochSeconds = mtimeSec,
        deviceId = null,
        inode = null,
    )

    private fun AdbSyncDirEntryV2.toProtocolEntry() = ProtocolRemoteEntry(
        name = name,
        mode = mode,
        size = size,
        modifiedEpochSeconds = mtimeSec,
        deviceId = dev,
        inode = ino,
    )

    private fun AdbSyncStat.toProtocolStat() = ProtocolRemoteStat(
        mode = mode,
        size = size,
        modifiedEpochSeconds = mtimeSec,
        deviceId = null,
        inode = null,
    )

    private fun AdbSyncStatV2.toProtocolStat() = ProtocolRemoteStat(
        mode = mode,
        size = size,
        modifiedEpochSeconds = mtimeSec,
        deviceId = dev,
        inode = ino,
    )

    private class BoundedByteTail(private val limit: Int) {
        private var data = ByteArray(0)
        var truncated = false
            private set

        fun append(value: ByteArray) {
            if (value.isEmpty()) return
            if (value.size >= limit) {
                data = value.copyOfRange(value.size - limit, value.size)
                truncated = true
                return
            }
            val overflow = (data.size + value.size - limit).coerceAtLeast(0)
            if (overflow > 0) truncated = true
            data = data.copyOfRange(overflow.coerceAtMost(data.size), data.size) + value
        }

        fun text(): String {
            var start = 0
            while (start < data.size && data[start].toInt().and(0xC0) == 0x80) start++
            return String(data, start, data.size - start, StandardCharsets.UTF_8)
        }
    }
}

internal object KadbProtocolTimeouts {
    // Long-lived foreground streams must not inherit a connection-wide idle read timeout.
    // Finite commands remain bounded by AdbSessionManager coroutine timeouts.
    const val SOCKET_IO_TIMEOUT_MS = 0
}
