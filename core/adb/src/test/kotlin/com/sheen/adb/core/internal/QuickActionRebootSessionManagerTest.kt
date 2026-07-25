package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.QuickActionResult
import com.sheen.adb.core.RebootRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class QuickActionRebootSessionManagerTest {
    @Test
    fun `typed reboot request sends once only when the confirmed caller invokes core`() = runBlocking {
        val client = RebootClient()
        val protocol = FixtureQuickActionProtocol(QuickActionProtocolRebootResult.Accepted)
        val factory = CountingFactory(client)
        val manager = manager(factory, protocol)
        val sessionId = manager.connectAndGetSessionId()

        assertTrue(RebootRequest::class.java.declaredFields.none { it.type == Boolean::class.javaPrimitiveType })
        assertEquals(protocol.rebootCalls, 0)

        val result = manager.reboot(RebootRequest(sessionId, 5.seconds))

        assertTrue(result is QuickActionResult.Success<*>)
        assertEquals(protocol.rebootCalls, 1)
        assertEquals(factory.openCalls, 1)
    }

    @Test
    fun `unsupported and policy rejected reboot never dispatch a reboot command`() = runBlocking {
        listOf(127, 126).forEach { probeExitCode ->
            val client = RebootClient(rebootProbeExitCode = probeExitCode)
            val protocol = FixtureQuickActionProtocol(QuickActionProtocolRebootResult.Accepted)
            val manager = manager(CountingFactory(client), protocol)
            val sessionId = manager.connectAndGetSessionId()

            val result = manager.reboot(RebootRequest(sessionId, 5.seconds))

            assertTrue(result is QuickActionResult.Failure)
            assertEquals(protocol.rebootCalls, 0)
        }
    }

    @Test
    fun `stale session request is rejected without dispatch`() = runBlocking {
        val client = RebootClient()
        val protocol = FixtureQuickActionProtocol(QuickActionProtocolRebootResult.Accepted)
        val manager = manager(CountingFactory(client), protocol)
        manager.connectAndGetSessionId()

        val result = manager.reboot(RebootRequest("session-stale", 5.seconds))

        assertEquals(result, QuickActionResult.StaleSession("session-stale"))
        assertEquals(protocol.rebootCalls, 0)
    }

    @Test
    fun `disconnect after reboot dispatch is result unknown and never reconnects`() = runBlocking {
        val client = RebootClient()
        val protocol = FixtureQuickActionProtocol(QuickActionProtocolRebootResult.DisconnectedAfterDispatch)
        val factory = CountingFactory(client)
        val manager = manager(factory, protocol)
        val sessionId = manager.connectAndGetSessionId()

        val result = manager.reboot(RebootRequest(sessionId, 5.seconds))

        assertEquals(result, QuickActionResult.ResultUnknown(sessionId))
        assertEquals(protocol.rebootCalls, 1)
        assertEquals(factory.openCalls, 1, "Reboot must never start an automatic reconnect")
        assertTrue(client.closed.get())
        assertFalse(manager.connectionState.value is AdbConnectionState.Connected)
    }

    private fun manager(
        factory: AdbProtocolClientFactory,
        protocol: QuickActionProtocol,
    ): DefaultAdbSessionManager = DefaultAdbSessionManager(
        clientFactory = factory,
        ioDispatcher = Dispatchers.IO,
        quickActionProtocol = protocol,
    )

    private suspend fun DefaultAdbSessionManager.connectAndGetSessionId(): String {
        val connected = connect(AdbEndpoint("controlled.invalid", 4711))
        assertTrue(connected is AdbOperationResult.Success)
        return (connectionState.value as AdbConnectionState.Connected).sessionId
    }

    private class RebootClient(
        private val rebootProbeExitCode: Int = 0,
    ) : AdbProtocolClient {
        val closed = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse = when {
            command.contains("command -v screencap") ->
                response(exitCode = 0)
            command.contains("command -v screenrecord") ->
                response(exitCode = 0)
            command.contains("cmd power help") || command.contains("command -v reboot") ->
                response(exitCode = rebootProbeExitCode)
            else -> response(exitCode = 0)
        }

        override fun openShellStream(command: String): ProtocolShellStream = object : ProtocolShellStream {
            override fun read(): ProtocolShellPacket = ProtocolShellPacket.Exit(0)
            override fun close() = Unit
        }

        override fun close() {
            closed.set(true)
        }

        private fun response(exitCode: Int): ProtocolShellResponse = ProtocolShellResponse(
            stdout = "",
            stderr = "",
            exitCode = exitCode,
            streamsSeparated = true,
            wasTruncated = false,
        )
    }

    private class FixtureQuickActionProtocol(
        private val rebootResult: QuickActionProtocolRebootResult,
    ) : QuickActionProtocol {
        var rebootCalls = 0
            private set

        override suspend fun reboot(client: AdbProtocolClient): QuickActionProtocolRebootResult {
            rebootCalls++
            return rebootResult
        }
    }

    private class CountingFactory(
        private val client: AdbProtocolClient,
    ) : AdbProtocolClientFactory {
        var openCalls = 0
            private set

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient {
            openCalls++
            return client
        }

        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit

        override fun clearIdentity() = Unit
    }
}
