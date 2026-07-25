package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.CaptureFormat
import com.sheen.adb.core.CaptureSinkResult
import com.sheen.adb.core.QuickActionResult
import com.sheen.adb.core.ScreenshotCaptureRequest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class QuickActionScreenshotSessionManagerTest {
    @Test
    fun `controlled-device PNG bytes reach project sink without text ShellResult`() = runBlocking {
        val png = validPngFixture()
        val protocol = FixtureQuickActionProtocol {
            write(png, 0, 5)
            write(png, 5, png.size - 5)
            QuickActionProtocolCaptureResult.Completed(png.size.toLong())
        }
        val client = FixtureClient()
        val manager = manager(client, protocol)
        val sessionId = manager.connectAndSessionId()
        val shellCallsAfterConnect = client.executeCalls.get()
        val sink = RecordingCaptureSink()
        val progressBytes = mutableListOf<Long>()

        val result = manager.captureScreenshot(
            request = ScreenshotCaptureRequest(sessionId, 10.seconds),
            sink = sink,
            progress = { progressBytes += it.bytesWritten },
        )

        assertTrue(result is QuickActionResult.Success<*>)
        val metadata = (result as QuickActionResult.Success).value
        assertEquals(metadata.expectedSessionId, sessionId)
        assertEquals(metadata.format, CaptureFormat.PNG)
        assertEquals(metadata.bytesWritten, png.size.toLong())
        assertEquals(sink.bytes.toByteArray().toList(), png.toList())
        assertTrue(sink.finished)
        assertFalse(sink.aborted)
        assertTrue(progressBytes.isNotEmpty())
        assertEquals(client.executeCalls.get(), shellCallsAfterConnect)
    }

    @Test
    fun `ten second request timeout aborts only capture sink and preserves Session`() = runBlocking {
        val protocol = FixtureQuickActionProtocol {
            QuickActionProtocolCaptureResult.TimedOut
        }
        val client = FixtureClient()
        val manager = manager(client, protocol)
        val sessionId = manager.connectAndSessionId()
        val sink = RecordingCaptureSink()
        val request = ScreenshotCaptureRequest(sessionId, 10.seconds)

        val result = manager.captureScreenshot(
            request = request,
            sink = sink,
        )

        assertEquals(request.timeout, 10.seconds)
        assertTrue(result is QuickActionResult.Failure)
        assertTrue((result as QuickActionResult.Failure).error.technicalCode.contains("TIMEOUT"))
        assertTrue(sink.aborted)
        assertFalse(client.closed)
        assertEquals(
            (manager.connectionState.value as AdbConnectionState.Connected).sessionId,
            sessionId,
        )
    }

    @Test
    fun `empty and invalid controlled-device images are rejected and cleaned`() = runBlocking {
        val protocol = QueueQuickActionProtocol(
            QuickActionProtocolCaptureResult.EmptyOutput,
            QuickActionProtocolCaptureResult.InvalidOutput("fixture-not-png"),
        )
        val client = FixtureClient()
        val manager = manager(client, protocol)
        val sessionId = manager.connectAndSessionId()

        val emptySink = RecordingCaptureSink()
        val empty = manager.captureScreenshot(
            ScreenshotCaptureRequest(sessionId, 10.seconds),
            emptySink,
        )
        val invalidSink = RecordingCaptureSink()
        val invalid = manager.captureScreenshot(
            ScreenshotCaptureRequest(sessionId, 10.seconds),
            invalidSink,
        )

        assertTrue(empty is QuickActionResult.Failure)
        assertTrue(invalid is QuickActionResult.Failure)
        assertTrue(emptySink.aborted)
        assertTrue(invalidSink.aborted)
        assertFalse(emptySink.finished)
        assertFalse(invalidSink.finished)
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
    }

    @Test
    fun `capture cancellation is typed and does not close the active Session`() = runBlocking {
        val protocol = FixtureQuickActionProtocol {
            throw CancellationException("fixture-cancel")
        }
        val client = FixtureClient()
        val manager = manager(client, protocol)
        val sessionId = manager.connectAndSessionId()
        val sink = RecordingCaptureSink()

        val result = manager.captureScreenshot(
            ScreenshotCaptureRequest(sessionId, 10.seconds),
            sink,
        )

        assertEquals(result, QuickActionResult.Cancelled)
        assertTrue(sink.aborted)
        assertFalse(client.closed)
        assertEquals(
            (manager.connectionState.value as AdbConnectionState.Connected).sessionId,
            sessionId,
        )
    }

    @Test
    fun `old Session request never opens a screenshot stream or writes the new Session sink`() = runBlocking {
        val first = FixtureClient()
        val second = FixtureClient()
        val protocol = QueueQuickActionProtocol(
            QuickActionProtocolCaptureResult.Completed(validPngFixture().size.toLong()),
        )
        val manager = DefaultAdbSessionManager(
            clientFactory = QueueFactory(first, second),
            ioDispatcher = Dispatchers.IO,
            quickActionProtocol = protocol,
        )
        val oldSessionId = manager.connectAndSessionId("old.synthetic.invalid", 45_001)
        val newSessionId = manager.connectAndSessionId("new.synthetic.invalid", 45_002)
        val sink = RecordingCaptureSink()

        val stale = manager.captureScreenshot(
            ScreenshotCaptureRequest(oldSessionId, 10.seconds),
            sink,
        )

        assertEquals(stale, QuickActionResult.StaleSession(oldSessionId))
        assertEquals(protocol.captureCalls, 0)
        assertTrue(sink.bytes.isEmpty())
        assertFalse(sink.finished)
        assertFalse(sink.aborted)
        assertEquals(
            (manager.connectionState.value as AdbConnectionState.Connected).sessionId,
            newSessionId,
        )
    }

    private fun manager(
        client: FixtureClient,
        protocol: QuickActionProtocol,
    ): DefaultAdbSessionManager = DefaultAdbSessionManager(
        clientFactory = QueueFactory(client),
        ioDispatcher = Dispatchers.IO,
        quickActionProtocol = protocol,
    )

    private suspend fun DefaultAdbSessionManager.connectAndSessionId(
        host: String = "controlled.synthetic.invalid",
        port: Int = 45_000,
    ): String {
        assertTrue(connect(AdbEndpoint(host, port)) is AdbOperationResult.Success)
        return (connectionState.value as AdbConnectionState.Connected).sessionId
    }

    private fun validPngFixture(): ByteArray = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
    )

    private class RecordingCaptureSink : AdbCaptureSink {
        val bytes = mutableListOf<Byte>()
        var finished = false
        var aborted = false

        override val bytesWritten: Long
            get() = bytes.size.toLong()

        override suspend fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): CaptureSinkResult {
            repeat(length) { index -> this.bytes += bytes[offset + index] }
            return CaptureSinkResult.Accepted
        }

        override suspend fun finish(): CaptureSinkResult {
            finished = true
            return CaptureSinkResult.Accepted
        }

        override suspend fun abort() {
            aborted = true
            bytes.clear()
        }
    }

    private class FixtureQuickActionProtocol(
        private val capture: suspend AdbCaptureSink.() -> QuickActionProtocolCaptureResult,
    ) : QuickActionProtocol {
        override suspend fun captureScreenshot(
            client: AdbProtocolClient,
            sink: AdbCaptureSink,
            progress: (Long) -> Unit,
        ): QuickActionProtocolCaptureResult = sink.capture().also {
            progress(sink.bytesWritten)
        }
    }

    private class QueueQuickActionProtocol(
        vararg results: QuickActionProtocolCaptureResult,
    ) : QuickActionProtocol {
        private val queue = ArrayDeque(results.toList())
        var captureCalls = 0

        override suspend fun captureScreenshot(
            client: AdbProtocolClient,
            sink: AdbCaptureSink,
            progress: (Long) -> Unit,
        ): QuickActionProtocolCaptureResult {
            captureCalls++
            return queue.removeFirst()
        }
    }

    private class FixtureClient : AdbProtocolClient {
        val executeCalls = AtomicInteger(0)
        var closed = false

        override fun execute(command: String): ProtocolShellResponse {
            executeCalls.incrementAndGet()
            return ProtocolShellResponse("", "", 0, streamsSeparated = true, wasTruncated = false)
        }

        override fun openShellStream(command: String): ProtocolShellStream =
            error("Screenshot test must use the injected binary protocol seam")

        override fun close() {
            closed = true
        }
    }

    private class QueueFactory(
        vararg clients: AdbProtocolClient,
    ) : AdbProtocolClientFactory {
        private val queue = ArrayDeque(clients.toList())

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = queue.removeFirst()

        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit

        override fun clearIdentity() = Unit
    }
}
