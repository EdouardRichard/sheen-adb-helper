package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.CaptureSinkResult
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class QuickActionScreenRecordProtocolTest {
    @Test
    fun `recording polls bounded state then stop finalizes and transfers one MP4`() {
        val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val client = PollingRecordingClient(
                ArrayDeque(listOf("RUNNING 1024", "FINISHED 2048")),
            )
            val progress = mutableListOf<Long>()
            var stopChecks = 0

            val result = runBlocking {
                DefaultQuickActionProtocol(io, Duration.ZERO).recordScreen(
                    client = client,
                    sink = CountingSink(),
                    maxBytes = MAX_BYTES,
                    maxDuration = 5.minutes,
                    stopRequested = { ++stopChecks >= 1 },
                    progress = progress::add,
                )
            }

            assertTrue(result is QuickActionProtocolCaptureResult.Completed)
            assertEquals((result as QuickActionProtocolCaptureResult.Completed).bytesWritten, 7L)
            assertEquals(progress, listOf(1024L, 2048L))
            assertEquals(client.openedCommands.size, 1)
            assertTrue(client.openedCommands.single().contains("cat"))
            assertFalse(client.openedCommands.single().contains("--time-limit"))
            assertEquals(client.startCount, 1)
            assertEquals(client.stopCount, 1)
            assertTrue(client.stopCommand.contains("kill -INT"))
            assertTrue(client.stopCommand.contains("/proc/"))
            assertTrue(client.cleanupCount >= 1)
        } finally {
            io.close()
        }
    }

    @Test
    fun `remote size limit requests stop before transfer and never starts a second segment`() {
        val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val client = PollingRecordingClient(
                ArrayDeque(listOf("RUNNING $MAX_BYTES", "FINISHED $MAX_BYTES")),
            )

            val result = runBlocking {
                DefaultQuickActionProtocol(io, Duration.ZERO).recordScreen(
                    client = client,
                    sink = CountingSink(),
                    maxBytes = MAX_BYTES,
                    maxDuration = 5.minutes,
                    stopRequested = { false },
                    progress = {},
                )
            }

            assertTrue(result is QuickActionProtocolCaptureResult.Completed)
            assertEquals(client.startCount, 1)
            assertEquals(client.stopCount, 1)
            assertEquals(client.openedCommands.size, 1)
        } finally {
            io.close()
        }
    }

    private class PollingRecordingClient(
        private val statuses: ArrayDeque<String>,
    ) : AdbProtocolClient {
        val openedCommands = mutableListOf<String>()
        var startCount = 0
        var stopCount = 0
        var cleanupCount = 0
        var stopCommand = ""

        override fun execute(command: String): ProtocolShellResponse = when {
            command.contains("sheen-screenrecord-start") -> {
                assertTrue(openedCommands.isEmpty(), "Transfer stream opened before recording finished")
                startCount++
                response("STARTED")
            }
            command.contains("sheen-screenrecord-status") -> {
                assertTrue(openedCommands.isEmpty(), "Transfer stream opened while recording was running")
                response(statuses.removeFirst())
            }
            command.contains("sheen-screenrecord-stop") -> {
                stopCount++
                stopCommand = command
                response("STOP_REQUESTED")
            }
            command.contains("sheen-screenrecord-cleanup") -> {
                cleanupCount++
                response("CLEANED")
            }
            else -> error("Unexpected command")
        }

        override fun openShellStream(command: String): ProtocolShellStream {
            openedCommands += command
            return PacketStream(
                ProtocolShellPacket.StandardOutput("fixture".encodeToByteArray()),
                ProtocolShellPacket.Exit(0),
            )
        }

        override fun close() = Unit

        private fun response(stdout: String) =
            ProtocolShellResponse("$stdout\n", "", 0, streamsSeparated = true, wasTruncated = false)
    }

    private class PacketStream(
        vararg packets: ProtocolShellPacket,
    ) : ProtocolShellStream {
        private val packets = ArrayDeque(packets.toList())

        override fun read(): ProtocolShellPacket = packets.removeFirst()

        override fun close() = Unit
    }

    private class CountingSink : AdbCaptureSink {
        override var bytesWritten: Long = 0L
            private set

        override suspend fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): CaptureSinkResult {
            bytesWritten += length
            return CaptureSinkResult.Accepted
        }

        override suspend fun finish(): CaptureSinkResult = CaptureSinkResult.Accepted

        override suspend fun abort() = Unit
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024L * 1024L
    }
}
