package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.CaptureSinkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface QuickActionProtocolCaptureResult {
    data class Completed(val bytesWritten: Long) : QuickActionProtocolCaptureResult
    data object EmptyOutput : QuickActionProtocolCaptureResult
    data class InvalidOutput(val reason: String) : QuickActionProtocolCaptureResult
    data object TimedOut : QuickActionProtocolCaptureResult
}

internal sealed interface QuickActionProtocolRebootResult {
    data object Accepted : QuickActionProtocolRebootResult
    data object DisconnectedAfterDispatch : QuickActionProtocolRebootResult
    data object Rejected : QuickActionProtocolRebootResult
}

internal interface QuickActionProtocol {
    suspend fun captureScreenshot(
        client: AdbProtocolClient,
        sink: AdbCaptureSink,
        progress: (Long) -> Unit,
    ): QuickActionProtocolCaptureResult = QuickActionProtocolCaptureResult.InvalidOutput("unsupported")

    suspend fun recordScreen(
        client: AdbProtocolClient,
        sink: AdbCaptureSink,
        maxBytes: Long,
        maxDuration: Duration,
        stopRequested: () -> Boolean = { false },
        progress: (Long) -> Unit,
    ): QuickActionProtocolCaptureResult = QuickActionProtocolCaptureResult.InvalidOutput("unsupported")

    suspend fun reboot(client: AdbProtocolClient): QuickActionProtocolRebootResult =
        QuickActionProtocolRebootResult.Rejected
}

internal class DefaultQuickActionProtocol(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val recordingPollInterval: Duration = 500.milliseconds,
) : QuickActionProtocol {
    override suspend fun captureScreenshot(
        client: AdbProtocolClient,
        sink: AdbCaptureSink,
        progress: (Long) -> Unit,
    ): QuickActionProtocolCaptureResult = withContext(ioDispatcher) {
        val prefix = ArrayList<Byte>(PNG_SIGNATURE.size)
        var bytesWritten = 0L
        val stream = client.openShellStream("screencap -p")
        try {
            while (true) {
                when (val packet = stream.read()) {
                    is ProtocolShellPacket.StandardOutput -> {
                        packet.bytes.take(PNG_SIGNATURE.size - prefix.size).forEach(prefix::add)
                        when (sink.write(packet.bytes, 0, packet.bytes.size)) {
                            CaptureSinkResult.Accepted -> {
                                bytesWritten += packet.bytes.size
                                progress(bytesWritten)
                            }
                            is CaptureSinkResult.Rejected ->
                                return@withContext QuickActionProtocolCaptureResult.InvalidOutput("sink-rejected")
                        }
                    }
                    is ProtocolShellPacket.StandardError ->
                        return@withContext QuickActionProtocolCaptureResult.InvalidOutput("device-error")
                    is ProtocolShellPacket.Exit -> {
                        if (packet.code != 0) {
                            return@withContext QuickActionProtocolCaptureResult.InvalidOutput("capture-failed")
                        }
                        break
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            stream.close()
        }
        when {
            bytesWritten == 0L -> QuickActionProtocolCaptureResult.EmptyOutput
            prefix.toByteArray().contentEquals(PNG_SIGNATURE).not() ->
                QuickActionProtocolCaptureResult.InvalidOutput("invalid-png")
            else -> QuickActionProtocolCaptureResult.Completed(bytesWritten)
        }
    }

    override suspend fun recordScreen(
        client: AdbProtocolClient,
        sink: AdbCaptureSink,
        maxBytes: Long,
        maxDuration: Duration,
        stopRequested: () -> Boolean,
        progress: (Long) -> Unit,
    ): QuickActionProtocolCaptureResult {
        val timeLimitSeconds = maxDuration.inWholeSeconds.coerceAtLeast(1L)
        val cleanupCommand = screenRecordCleanupCommand()
        var stream: ProtocolShellStream? = null
        var lastReportedBytes = 0L
        try {
            runInterruptible(ioDispatcher) { client.execute(cleanupCommand) }
            val started = runInterruptible(ioDispatcher) {
                client.execute(screenRecordStartCommand(timeLimitSeconds))
            }
            if (started.exitCode != 0 || started.stdout.lineSequence().none { it.trim() == "STARTED" }) {
                return QuickActionProtocolCaptureResult.InvalidOutput("recording-start-failed")
            }

            var stopSent = false
            var finalRemoteBytes = 0L
            while (true) {
                val statusResponse = runInterruptible(ioDispatcher) {
                    client.execute(screenRecordStatusCommand())
                }
                val status = parseScreenRecordStatus(statusResponse)
                    ?: return QuickActionProtocolCaptureResult.InvalidOutput("recording-status-invalid")
                finalRemoteBytes = status.bytes
                if (status.bytes > lastReportedBytes) {
                    lastReportedBytes = status.bytes
                    progress(lastReportedBytes)
                }
                if (status.finished) break

                if (!stopSent && (stopRequested() || status.bytes >= maxBytes)) {
                    val stopped = runInterruptible(ioDispatcher) {
                        client.execute(screenRecordStopCommand())
                    }
                    if (stopped.exitCode != 0 ||
                        stopped.stdout.lineSequence().none { it.trim() == "STOP_REQUESTED" }
                    ) {
                        return QuickActionProtocolCaptureResult.InvalidOutput("recording-stop-failed")
                    }
                    stopSent = true
                }
                if (recordingPollInterval.isPositive()) delay(recordingPollInterval)
            }

            if (finalRemoteBytes <= 0L) return QuickActionProtocolCaptureResult.EmptyOutput
            if (finalRemoteBytes > maxBytes) {
                return QuickActionProtocolCaptureResult.InvalidOutput("recording-size-limit")
            }

            stream = runInterruptible(ioDispatcher) {
                client.openShellStream("cat \"$SCREEN_RECORD_FILE\"")
            }
            while (sink.bytesWritten < maxBytes) {
                when (val packet = runInterruptible(ioDispatcher) { stream.read() }) {
                    is ProtocolShellPacket.StandardOutput -> {
                        val remaining = maxBytes - sink.bytesWritten
                        val acceptedLength = minOf(packet.bytes.size.toLong(), remaining).toInt()
                        if (acceptedLength == 0) break
                        when (sink.write(packet.bytes, 0, acceptedLength)) {
                            CaptureSinkResult.Accepted -> {
                                if (sink.bytesWritten > lastReportedBytes) {
                                    lastReportedBytes = sink.bytesWritten
                                    progress(lastReportedBytes)
                                }
                            }
                            is CaptureSinkResult.Rejected ->
                                return QuickActionProtocolCaptureResult.InvalidOutput("sink-rejected")
                        }
                        if (acceptedLength < packet.bytes.size) break
                    }
                    is ProtocolShellPacket.StandardError ->
                        return QuickActionProtocolCaptureResult.InvalidOutput("device-error")
                    is ProtocolShellPacket.Exit -> {
                        if (packet.code != 0) {
                            return QuickActionProtocolCaptureResult.InvalidOutput("recording-failed")
                        }
                        break
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return QuickActionProtocolCaptureResult.InvalidOutput("recording-io-failed")
        } finally {
            withContext(NonCancellable + ioDispatcher) {
                runCatching { stream?.close() }
                runCatching { client.execute(cleanupCommand) }
            }
        }
        return if (sink.bytesWritten == 0L) {
            QuickActionProtocolCaptureResult.EmptyOutput
        } else {
            QuickActionProtocolCaptureResult.Completed(sink.bytesWritten)
        }
    }

    override suspend fun reboot(client: AdbProtocolClient): QuickActionProtocolRebootResult {
        val stream = try {
            runInterruptible(ioDispatcher) { client.openShellStream("reboot") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return QuickActionProtocolRebootResult.Rejected
        }
        return try {
            while (true) {
                when (val packet = runInterruptible(ioDispatcher) { stream.read() }) {
                    is ProtocolShellPacket.StandardOutput,
                    is ProtocolShellPacket.StandardError,
                    -> Unit
                    is ProtocolShellPacket.Exit -> {
                        return if (packet.code == 0) {
                            QuickActionProtocolRebootResult.Accepted
                        } else {
                            QuickActionProtocolRebootResult.Rejected
                        }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            QuickActionProtocolRebootResult.Accepted
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            QuickActionProtocolRebootResult.DisconnectedAfterDispatch
        } finally {
            withContext(NonCancellable + ioDispatcher) {
                runCatching { stream.close() }
            }
        }
    }

    private companion object {
        const val SCREEN_RECORD_PID_FILE =
            "/data/local/tmp/.sheen-adb-helper-screenrecord.pid"
        const val SCREEN_RECORD_FILE =
            "/data/local/tmp/.sheen-adb-helper-screenrecord.mp4"

        fun screenRecordCleanupCommand(): String =
            ": sheen-screenrecord-cleanup; " +
                "recording_pid_file=\"$SCREEN_RECORD_PID_FILE\"; " +
                "recording_file=\"$SCREEN_RECORD_FILE\"; " +
                "if [ -f \"\$recording_pid_file\" ]; then " +
                "recording_pid=\$(cat \"\$recording_pid_file\"); " +
                "case \"\$recording_pid\" in ''|*[!0-9]*) ;; *) " +
                "recording_command=\$(tr '\\000' ' ' < \"/proc/\$recording_pid/cmdline\" 2>/dev/null); " +
                "case \"\$recording_command\" in *screenrecord*\"\$recording_file\"*) " +
                "kill -TERM \"\$recording_pid\" >/dev/null 2>&1 || true ;; esac ;; esac; fi; " +
                "rm -f \"\$recording_pid_file\" \"\$recording_file\"; echo CLEANED"

        fun screenRecordStartCommand(timeLimitSeconds: Long): String =
            ": sheen-screenrecord-start; " +
                "recording_file=\"$SCREEN_RECORD_FILE\"; " +
                "screenrecord --bit-rate 6M --time-limit $timeLimitSeconds " +
                "\"\$recording_file\" >/dev/null 2>&1 </dev/null & " +
                "recording_pid=\$!; echo \"\$recording_pid\" > \"$SCREEN_RECORD_PID_FILE\"; " +
                "echo STARTED"

        fun screenRecordStatusCommand(): String =
            ": sheen-screenrecord-status; " +
                "recording_pid_file=\"$SCREEN_RECORD_PID_FILE\"; " +
                "recording_file=\"$SCREEN_RECORD_FILE\"; " +
                "recording_size=\$(wc -c < \"\$recording_file\" 2>/dev/null || echo 0); " +
                "if [ -f \"\$recording_pid_file\" ]; then " +
                "recording_pid=\$(cat \"\$recording_pid_file\"); " +
                "if kill -0 \"\$recording_pid\" >/dev/null 2>&1; then " +
                "echo \"RUNNING \$recording_size\"; exit 0; fi; fi; " +
                "rm -f \"\$recording_pid_file\"; echo \"FINISHED \$recording_size\""

        fun screenRecordStopCommand(): String =
            ": sheen-screenrecord-stop; " +
                "recording_pid_file=\"$SCREEN_RECORD_PID_FILE\"; " +
                "recording_file=\"$SCREEN_RECORD_FILE\"; " +
                "[ -f \"\$recording_pid_file\" ] || exit 1; " +
                "recording_pid=\$(cat \"\$recording_pid_file\"); " +
                "case \"\$recording_pid\" in ''|*[!0-9]*) exit 1 ;; esac; " +
                "recording_command=\$(tr '\\000' ' ' < \"/proc/\$recording_pid/cmdline\" 2>/dev/null); " +
                "case \"\$recording_command\" in *screenrecord*\"\$recording_file\"*) " +
                "kill -INT \"\$recording_pid\" >/dev/null 2>&1 || exit 1 ;; *) exit 1 ;; esac; " +
                "echo STOP_REQUESTED"

        fun parseScreenRecordStatus(
            response: ProtocolShellResponse,
        ): ScreenRecordStatus? {
            if (response.exitCode != 0 || response.wasTruncated) return null
            val match = SCREEN_RECORD_STATUS.matchEntire(response.stdout.trim()) ?: return null
            val bytes = match.groupValues[2].toLongOrNull() ?: return null
            return ScreenRecordStatus(
                finished = match.groupValues[1] == "FINISHED",
                bytes = bytes,
            )
        }

        val SCREEN_RECORD_STATUS = Regex("(RUNNING|FINISHED) ([0-9]+)")

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }

    private data class ScreenRecordStatus(
        val finished: Boolean,
        val bytes: Long,
    )
}
