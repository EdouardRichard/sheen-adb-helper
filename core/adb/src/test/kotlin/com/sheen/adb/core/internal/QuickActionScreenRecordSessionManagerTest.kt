package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.CaptureFormat
import com.sheen.adb.core.CaptureSinkResult
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.core.QuickActionResult
import com.sheen.adb.core.ScreenRecordRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.minutes

class QuickActionScreenRecordSessionManagerTest {
    @Test
    fun `controlled device recording is one video only segment`() = runBlocking {
        val stream = PacketStream(
            ProtocolShellPacket.StandardOutput("video-only-fixture".encodeToByteArray()),
            ProtocolShellPacket.Exit(0),
        )
        val client = RecordingClient { stream }
        val manager = connectedManager(client)
        val sessionId = connectedSessionId(manager)
        val sink = CountingSink(MAX_RECORDING_BYTES)

        val result = manager.recordScreen(request(sessionId), sink)

        assertTrue(result is QuickActionResult.Success<*>)
        val metadata = (result as QuickActionResult.Success).value
        assertEquals(metadata.expectedSessionId, sessionId)
        assertEquals(metadata.kind, QuickActionKind.SCREEN_RECORD)
        assertEquals(metadata.format, CaptureFormat.MP4)
        assertEquals(client.openedCommands.size, 1, "Recording must not create a second segment")
        val startCommand = client.executedCommands.single { it.contains("sheen-screenrecord-start") }
        assertTrue(startCommand.contains("screenrecord"))
        assertFalse(startCommand.contains("audio", ignoreCase = true))
        assertFalse(startCommand.contains("mic", ignoreCase = true))
        assertTrue(stream.closed.get())
        assertTrue(sink.finished)
        assertFalse(sink.aborted)
    }

    @Test
    fun `five minute device limit stops without continuing into a second segment`() = runBlocking {
        val stream = PacketStream(
            ProtocolShellPacket.StandardOutput("bounded-video".encodeToByteArray()),
            ProtocolShellPacket.Exit(0),
        )
        val client = RecordingClient { stream }
        val manager = connectedManager(client)
        val sessionId = connectedSessionId(manager)

        val result = manager.recordScreen(request(sessionId), CountingSink(MAX_RECORDING_BYTES))

        assertFalse(result is QuickActionResult.StaleSession)
        assertEquals(client.openedCommands.size, 1)
        val command = client.executedCommands.single { it.contains("sheen-screenrecord-start") }
        assertTrue(
            command.contains("--time-limit 300") || command.contains("--time-limit=300"),
            "The controlled-device recorder must receive the five-minute limit: $command",
        )
        assertTrue(stream.closed.get())
    }

    @Test
    fun `two hundred fifty six MiB limit wins first and never opens another stream`() = runBlocking {
        val stream = PacketStream(
            ProtocolShellPacket.StandardOutput(ByteArray(8) { 0x01 }),
            ProtocolShellPacket.StandardOutput(ByteArray(8) { 0x02 }),
            ProtocolShellPacket.Exit(0),
        )
        val client = RecordingClient { stream }
        val manager = connectedManager(client)
        val sessionId = connectedSessionId(manager)
        val sink = CountingSink(
            maxBytes = MAX_RECORDING_BYTES,
            initialBytes = MAX_RECORDING_BYTES - 4L,
        )

        val result = manager.recordScreen(request(sessionId), sink)

        assertFalse(result is QuickActionResult.StaleSession)
        assertTrue(sink.bytesWritten <= MAX_RECORDING_BYTES)
        assertEquals(client.openedCommands.size, 1, "Size stop must not start a continuation segment")
        assertTrue(stream.closed.get())
    }

    @Test
    fun `explicit stop cancellation closes child stream and aborts partial sink`() = runBlocking {
        val stream = BlockingStream()
        val client = RecordingClient { stream }
        val manager = connectedManager(client)
        val sessionId = connectedSessionId(manager)
        val sink = CountingSink(MAX_RECORDING_BYTES)

        val recording = async(Dispatchers.Default) {
            manager.recordScreen(request(sessionId), sink)
        }
        assertTrue(stream.started.await(2, TimeUnit.SECONDS), "recordScreen did not open the child stream")

        recording.cancelAndJoin()

        assertTrue(stream.closed.get())
        assertTrue(sink.aborted)
        assertFalse(sink.finished)
        assertEquals(client.openedCommands.size, 1)
    }

    @Test
    fun `typed stop finalizes the active recording instead of cancelling its artifact`() = runBlocking {
        val protocol = StopAwareProtocol()
        val client = RecordingClient {
            error("Stop-aware protocol does not open a raw stream")
        }
        val manager = DefaultAdbSessionManager(
            clientFactory = SingleFactory(client),
            ioDispatcher = Dispatchers.IO,
            quickActionProtocol = protocol,
        )
        assertTrue(
            manager.connect(AdbEndpoint("controlled-device.invalid", 40_101)) is
                AdbOperationResult.Success,
        )
        val sessionId = connectedSessionId(manager)
        val sink = CountingSink(MAX_RECORDING_BYTES)

        val recording = async(Dispatchers.Default) {
            manager.recordScreen(request(sessionId), sink)
        }
        assertTrue(protocol.started.await(2, TimeUnit.SECONDS))

        assertTrue(manager.stopScreenRecord(sessionId) is QuickActionResult.Success)
        val result = recording.await()

        assertTrue(result is QuickActionResult.Success)
        assertTrue(sink.finished)
        assertFalse(sink.aborted)
        assertTrue(manager.stopScreenRecord("stale-session") is QuickActionResult.StaleSession)
    }

    private fun request(sessionId: String) = ScreenRecordRequest(
        expectedSessionId = sessionId,
        timeout = 5.minutes,
        maxDuration = 5.minutes,
        maxBytes = MAX_RECORDING_BYTES,
    )

    private suspend fun connectedManager(client: RecordingClient): DefaultAdbSessionManager =
        DefaultAdbSessionManager(SingleFactory(client), Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("controlled-device.invalid", 40_101)) is AdbOperationResult.Success)
        }

    private fun connectedSessionId(manager: DefaultAdbSessionManager): String =
        (manager.connectionState.value as AdbConnectionState.Connected).sessionId

    private class CountingSink(
        private val maxBytes: Long,
        initialBytes: Long = 0L,
    ) : AdbCaptureSink {
        override var bytesWritten: Long = initialBytes
            private set
        var finished = false
            private set
        var aborted = false
            private set

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int): CaptureSinkResult {
            if (length < 0 || offset < 0 || offset + length > bytes.size) {
                return CaptureSinkResult.Rejected("invalid-range")
            }
            if (bytesWritten + length > maxBytes) {
                return CaptureSinkResult.Rejected("size-limit")
            }
            bytesWritten += length
            return CaptureSinkResult.Accepted
        }

        override suspend fun finish(): CaptureSinkResult {
            finished = true
            return CaptureSinkResult.Accepted
        }

        override suspend fun abort() {
            aborted = true
        }
    }

    private class RecordingClient(
        private val streamFactory: () -> ProtocolShellStream,
    ) : AdbProtocolClient {
        val openedCommands = mutableListOf<String>()
        val executedCommands = mutableListOf<String>()

        override fun execute(command: String): ProtocolShellResponse {
            executedCommands += command
            return when {
            command.contains("sheen-screenrecord-start") ->
                ProtocolShellResponse("STARTED\n", "", 0, streamsSeparated = true, wasTruncated = false)
            command.contains("sheen-screenrecord-status") ->
                ProtocolShellResponse("FINISHED 8\n", "", 0, streamsSeparated = true, wasTruncated = false)
            command.contains("sheen-screenrecord-cleanup") ->
                ProtocolShellResponse("CLEANED\n", "", 0, streamsSeparated = true, wasTruncated = false)
            else ->
                ProtocolShellResponse("ready\n", "", 0, streamsSeparated = true, wasTruncated = false)
            }
        }

        override fun openShellStream(command: String): ProtocolShellStream {
            openedCommands += command
            return streamFactory()
        }

        override fun close() = Unit
    }

    private class PacketStream(
        vararg packets: ProtocolShellPacket,
    ) : ProtocolShellStream {
        private val remaining = ArrayDeque(packets.toList())
        val closed = AtomicBoolean(false)

        override fun read(): ProtocolShellPacket =
            if (remaining.isEmpty()) ProtocolShellPacket.Exit(0) else remaining.removeFirst()

        override fun close() {
            closed.set(true)
        }
    }

    private class BlockingStream : ProtocolShellStream {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun read(): ProtocolShellPacket {
            started.countDown()
            while (!closed.get()) {
                Thread.sleep(1_000)
            }
            return ProtocolShellPacket.Exit(0)
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class StopAwareProtocol : QuickActionProtocol {
        val started = CountDownLatch(1)

        override suspend fun recordScreen(
            client: AdbProtocolClient,
            sink: AdbCaptureSink,
            maxBytes: Long,
            maxDuration: kotlin.time.Duration,
            stopRequested: () -> Boolean,
            progress: (Long) -> Unit,
        ): QuickActionProtocolCaptureResult {
            started.countDown()
            while (!stopRequested()) {
                kotlinx.coroutines.delay(10)
            }
            sink.write("fixture".encodeToByteArray())
            return QuickActionProtocolCaptureResult.Completed(sink.bytesWritten)
        }
    }

    private class SingleFactory(private val client: RecordingClient) : AdbProtocolClientFactory {
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = client
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private companion object {
        const val MAX_RECORDING_BYTES = 256L * 1024L * 1024L
    }
}
