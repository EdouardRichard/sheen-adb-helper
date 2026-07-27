package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticOutcome
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.LogcatConfig
import java.io.EOFException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DefaultAdbSessionManagerTest {
    @Test
    fun `protocol connection has no idle read timeout for foreground streams`() {
        assertEquals(KadbProtocolTimeouts.SOCKET_IO_TIMEOUT_MS, 0)
    }

    @Test
    fun `connect validates protocol then executes command and disconnects`() = runBlocking {
        val client = FakeClient()
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        val endpoint = AdbEndpoint("device.local", 42111)

        assertTrue(manager.connect(endpoint) is AdbOperationResult.Success)
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)

        val shell = manager.executeShell("test-command")
        assertTrue(shell is AdbOperationResult.Success)
        shell as AdbOperationResult.Success
        assertEquals("ok\n", shell.value.stdout)

        assertTrue(manager.disconnect() is AdbOperationResult.Success)
        assertTrue(client.closed.get())
        assertEquals(AdbConnectionState.Disconnected(), manager.connectionState.value)
    }

    @Test
    fun `new connection closes the previous single active session`() = runBlocking {
        val first = FakeClient()
        val second = FakeClient()
        val manager = DefaultAdbSessionManager(QueueFactory(first, second), Dispatchers.IO)

        manager.connect(AdbEndpoint("first.local", 40001))
        manager.connect(AdbEndpoint("second.local", 40002))

        assertTrue(first.closed.get())
        assertTrue(!second.closed.get())
        val state = manager.connectionState.value as AdbConnectionState.Connected
        assertEquals("second.local", state.endpoint.host)
    }

    @Test
    fun `remote transport loss is detected while idle and publishes disconnected state`() = runBlocking {
        val client = DisconnectAfterHandshakeClient()
        val manager = DefaultAdbSessionManager(
            clientFactory = FakeFactory(client),
            ioDispatcher = Dispatchers.IO,
            sessionHealthInterval = 10.milliseconds,
            sessionHealthTimeout = 50.milliseconds,
            sessionHealthFailureThreshold = 1,
        )

        assertTrue(manager.connect(AdbEndpoint("remote.local", 40011)) is AdbOperationResult.Success)
        manager.connectionState.first { it is AdbConnectionState.Disconnected }

        assertTrue(client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Disconnected)
    }

    @Test
    fun `remote transport loss retires session while a feature command is blocked`() = runBlocking {
        val client = DisconnectDuringFeatureCommandClient()
        val manager = DefaultAdbSessionManager(
            clientFactory = FakeFactory(client),
            ioDispatcher = Dispatchers.IO,
            sessionHealthInterval = 10.milliseconds,
            sessionHealthTimeout = 50.milliseconds,
            sessionHealthFailureThreshold = 1,
        )

        assertTrue(manager.connect(AdbEndpoint("remote.local", 40012)) is AdbOperationResult.Success)
        val featureCommand = async(Dispatchers.Default) {
            manager.executeShell("blocked-feature-command", 5.seconds)
        }
        assertTrue(client.featureCommandStarted.await(2, TimeUnit.SECONDS))

        withTimeout(500.milliseconds) {
            manager.connectionState.first { it is AdbConnectionState.Disconnected }
        }

        assertTrue(client.closed.get())
        assertTrue(featureCommand.await() is AdbOperationResult.Failure)
    }

    @Test
    fun `timeout and cancellation close candidate resources`() = runBlocking {
        val timeoutClient = FakeClient(block = true)
        val timeoutManager = DefaultAdbSessionManager(FakeFactory(timeoutClient), Dispatchers.IO)
        val endpoint = AdbEndpoint("timeout.local", 40003)

        val result = timeoutManager.connect(endpoint, 50.milliseconds)
        assertTrue(result is AdbOperationResult.Failure)
        assertTrue(timeoutClient.closed.get())

        val cancelClient = FakeClient(block = true)
        val cancelManager = DefaultAdbSessionManager(FakeFactory(cancelClient), Dispatchers.IO)
        val operation = async(Dispatchers.Default) { cancelManager.connect(endpoint, 5.seconds) }
        while (!cancelClient.started.get()) Thread.yield()
        operation.cancel()
        operation.join()
        assertTrue(cancelClient.closed.get())
        val cancelledState = cancelManager.connectionState.value as AdbConnectionState.Disconnected
        assertEquals(com.sheen.adb.core.DisconnectionReason.CONNECT_CANCELLED, cancelledState.reason)
    }

    @Test
    fun `cancellation force closes a probe that ignores thread interruption`() = runBlocking {
        val client = SlowInterruptIgnoringClient()
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        val endpoint = AdbEndpoint("cancel-probe.invalid", 45555)
        val operation = async(Dispatchers.Default) { manager.connect(endpoint, 5.seconds) }
        assertTrue(client.started.await(2, TimeUnit.SECONDS))

        val cancelMillis = measureTimeMillis {
            operation.cancel()
            operation.join()
        }

        assertTrue(cancelMillis < 250L, "cancellation took ${cancelMillis}ms")
        assertTrue(client.closed.get())
        assertEquals(
            (manager.connectionState.value as AdbConnectionState.Disconnected).reason,
            com.sheen.adb.core.DisconnectionReason.CONNECT_CANCELLED,
        )
    }

    @Test
    fun `diagnostics are bounded cleared and never contain endpoint or command output`() = runBlocking {
        val client = FakeClient()
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        val endpoint = AdbEndpoint("sensitive-device.local", 40123)

        manager.connect(endpoint)
        manager.executeShell("secret-command")
        repeat(60) { manager.disconnect() }

        val events = manager.diagnosticEvents.value
        assertEquals(100, events.size)
        assertTrue(events.any { it.outcome == AdbDiagnosticOutcome.SUCCEEDED })
        val rendered = events.joinToString()
        assertTrue("sensitive-device.local" !in rendered)
        assertTrue("secret-command" !in rendered)
        assertTrue("ok" !in rendered)

        manager.clearDiagnosticEvents()
        assertTrue(manager.diagnosticEvents.value.isEmpty())
    }

    @Test
    fun `pairing code and host identity are cleared`() = runBlocking {
        val factory = FakeFactory(FakeClient())
        val manager = DefaultAdbSessionManager(factory, Dispatchers.IO)
        val code = charArrayOf('0', '1', '2', '3', '4', '5')

        assertTrue(manager.pair(AdbEndpoint("pair.local", 40005), code) is AdbOperationResult.Success)
        assertTrue(code.all { it == '\u0000' })
        assertTrue(manager.clearHostIdentity() is AdbOperationResult.Success)
        assertTrue(factory.identityCleared.get())
    }

    @Test
    fun `cancelling logcat closes only its stream`() = runBlocking {
        val stream = BlockingStream()
        val client = FakeClient(stream = stream)
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        manager.connect(AdbEndpoint("logcat.local", 40006))
        val sessionIdBeforeCancellation =
            (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val collection = async(Dispatchers.Default) { manager.streamLogcat(LogcatConfig()).collect() }
        while (!stream.started.get()) Thread.yield()
        collection.cancel()
        collection.join()

        assertTrue(stream.closed.get())
        assertTrue(!client.closed.get())
        val stateAfterCancellation = manager.connectionState.value
        assertTrue(stateAfterCancellation is AdbConnectionState.Connected)
        assertEquals(
            (stateAfterCancellation as AdbConnectionState.Connected).sessionId,
            sessionIdBeforeCancellation,
        )
    }

    @Test
    fun `probe socket failure is returned instead of escaping the worker`() = runBlocking {
        val client = ThrowingProbeClient()
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)

        val result = manager.connect(AdbEndpoint("unreachable.invalid", 5555), 30.seconds)

        assertTrue(result is AdbOperationResult.Failure)
        assertTrue(client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Error)
    }

    @Test
    fun `logcat remains active while packets continue and closes only stream on stop`() = runBlocking {
        val stream = OneThenBlockingStream()
        val client = FakeClient(stream = stream)
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        manager.connect(AdbEndpoint("logcat.local", 40007))

        val received = AtomicBoolean(false)
        val collection = async(Dispatchers.Default) {
            manager.streamLogcat(LogcatConfig()).collect { result ->
                if (result is AdbOperationResult.Success) received.set(true)
            }
        }
        while (!received.get() || !stream.blockingReadStarted.get()) Thread.yield()
        assertTrue(collection.isActive)
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)

        collection.cancel()
        collection.join()
        assertTrue(stream.closed.get())
        assertTrue(!client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
        assertTrue("line" !in manager.diagnosticEvents.value.joinToString())
    }

    @Test
    fun `disconnect stops logcat and releases both stream and session`() = runBlocking {
        val client = LifecycleClient()
        val manager = DefaultAdbSessionManager(FakeFactory(client), Dispatchers.IO)
        manager.connect(AdbEndpoint("disconnect.local", 40008))

        val collection = async(Dispatchers.Default) { manager.streamLogcat(LogcatConfig()).collect() }
        while (!client.stream.started.get()) Thread.yield()
        assertTrue(manager.disconnect() is AdbOperationResult.Success)
        collection.join()

        assertTrue(client.closed.get())
        assertTrue(client.stream.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Disconnected)
    }

    @Test
    fun `session switch stops old logcat without corrupting new connection state`() = runBlocking {
        val first = LifecycleClient()
        val second = FakeClient()
        val manager = DefaultAdbSessionManager(QueueFactory(first, second), Dispatchers.IO)
        manager.connect(AdbEndpoint("old.local", 40009))
        val oldSessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val collection = async(Dispatchers.Default) { manager.streamLogcat(LogcatConfig()).collect() }
        while (!first.stream.started.get()) Thread.yield()
        assertTrue(manager.connect(AdbEndpoint("new.local", 40010)) is AdbOperationResult.Success)
        collection.join()

        val current = manager.connectionState.value as AdbConnectionState.Connected
        assertTrue(first.closed.get())
        assertTrue(first.stream.closed.get())
        assertTrue(current.sessionId != oldSessionId)
        assertEquals(current.endpoint.host, "new.local")
    }

    private class FakeClient(
        private val block: Boolean = false,
        private val stream: ProtocolShellStream? = null,
    ) : AdbProtocolClient {
        val closed = AtomicBoolean(false)
        val started = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse {
            started.set(true)
            if (block) Thread.sleep(10_000)
            return ProtocolShellResponse("ok\n", "", 0, streamsSeparated = true, wasTruncated = false)
        }

        override fun openShellStream(command: String): ProtocolShellStream = stream ?: object : ProtocolShellStream {
            override fun read(): ProtocolShellPacket = ProtocolShellPacket.Exit(0)
            override fun close() = Unit
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class BlockingStream : ProtocolShellStream {
        val started = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        override fun read(): ProtocolShellPacket {
            started.set(true)
            Thread.sleep(10_000)
            return ProtocolShellPacket.Exit(0)
        }
        override fun close() { closed.set(true) }
    }

    private class SlowInterruptIgnoringClient : AdbProtocolClient {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse {
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750)
            while (System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    // Models a lazy Kadb handshake whose socket is not yet owned by close().
                }
            }
            return ProtocolShellResponse("late\n", "", 0, streamsSeparated = true, wasTruncated = false)
        }

        override fun openShellStream(command: String): ProtocolShellStream =
            object : ProtocolShellStream {
                override fun read(): ProtocolShellPacket = ProtocolShellPacket.Exit(0)
                override fun close() = Unit
            }

        override fun close() {
            closed.set(true)
        }
    }

    private class ThrowingProbeClient : AdbProtocolClient {
        val closed = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse =
            throw SocketTimeoutException("synthetic timeout")

        override fun openShellStream(command: String): ProtocolShellStream =
            error("stream should not be opened")

        override fun close() {
            closed.set(true)
        }
    }

    private class DisconnectAfterHandshakeClient : AdbProtocolClient {
        val closed = AtomicBoolean(false)
        private val handshakeComplete = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse {
            if (handshakeComplete.compareAndSet(false, true)) {
                return ProtocolShellResponse("ok\n", "", 0, streamsSeparated = true, wasTruncated = false)
            }
            throw EOFException("synthetic remote close")
        }

        override fun openShellStream(command: String): ProtocolShellStream = error("unused")

        override fun close() {
            closed.set(true)
        }
    }

    private class DisconnectDuringFeatureCommandClient : AdbProtocolClient {
        val featureCommandStarted = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        private val calls = AtomicInteger(0)
        private val connectionClosed = CountDownLatch(1)

        override fun execute(command: String): ProtocolShellResponse =
            when (calls.incrementAndGet()) {
                1 -> ProtocolShellResponse("ok\n", "", 0, streamsSeparated = true, wasTruncated = false)
                2 -> {
                    featureCommandStarted.countDown()
                    check(connectionClosed.await(5, TimeUnit.SECONDS))
                    throw EOFException("synthetic remote close")
                }
                else -> throw EOFException("synthetic remote close")
            }

        override fun openShellStream(command: String): ProtocolShellStream = error("unused")

        override fun close() {
            closed.set(true)
            connectionClosed.countDown()
        }
    }

    private class OneThenBlockingStream : ProtocolShellStream {
        private val delivered = AtomicBoolean(false)
        val blockingReadStarted = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        override fun read(): ProtocolShellPacket {
            if (delivered.compareAndSet(false, true)) return ProtocolShellPacket.StandardOutput("line\n".encodeToByteArray())
            blockingReadStarted.set(true)
            Thread.sleep(10_000)
            return ProtocolShellPacket.Exit(0)
        }
        override fun close() { closed.set(true) }
    }

    private class LifecycleClient : AdbProtocolClient {
        val stream = CloseReleasedStream()
        val closed = AtomicBoolean(false)

        override fun execute(command: String) =
            ProtocolShellResponse("ok\n", "", 0, streamsSeparated = true, wasTruncated = false)

        override fun openShellStream(command: String): ProtocolShellStream = stream

        override fun close() {
            closed.set(true)
            stream.releaseFromConnectionClose()
        }
    }

    private class CloseReleasedStream : ProtocolShellStream {
        private val connectionClosed = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val closed = AtomicBoolean(false)

        override fun read(): ProtocolShellPacket {
            started.set(true)
            check(connectionClosed.await(10, TimeUnit.SECONDS))
            throw ProtocolCommandStreamException()
        }

        fun releaseFromConnectionClose() {
            connectionClosed.countDown()
        }

        override fun close() {
            closed.set(true)
        }
    }

    private open class FakeFactory(private val client: AdbProtocolClient) : AdbProtocolClientFactory {
        val identityCleared = AtomicBoolean(false)
        override fun open(endpoint: AdbEndpoint) = client
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() { identityCleared.set(true) }
    }

    private class QueueFactory(vararg clients: AdbProtocolClient) : AdbProtocolClientFactory {
        private val queue = ArrayDeque(clients.toList())
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = queue.removeFirst()
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }
}
