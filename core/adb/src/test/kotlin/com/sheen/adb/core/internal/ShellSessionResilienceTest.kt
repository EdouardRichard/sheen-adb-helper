package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.milliseconds

class ShellSessionResilienceTest {
    @Test
    fun `success and nonzero exit close only command streams and keep exact commands reusable`() = runBlocking {
        val client = CommandClient(
            Behavior.Response(response("first")),
            Behavior.Response(response("failed", exitCode = 7)),
            Behavior.Response(response("third")),
        )
        val manager = connectedManager(client)

        val first = manager.executeShell("  first command\n") as AdbOperationResult.Success
        val failed = manager.executeShell("second-command") as AdbOperationResult.Success
        val third = manager.executeShell("third-command") as AdbOperationResult.Success

        assertEquals(first.value.exitCode, 0)
        assertEquals(failed.value.exitCode, 7)
        assertEquals(third.value.exitCode, 0)
        assertEquals(client.receivedCommands, listOf("  first command\n", "second-command", "third-command"))
        assertTrue(client.commandClosed.all(AtomicBoolean::get))
        assertFalse(client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
    }

    @Test
    fun `timeout closes command stream but keeps session available for next command`() = runBlocking {
        val client = CommandClient(Behavior.Block, Behavior.Response(response("recovered")))
        val manager = connectedManager(client)

        val timedOut = manager.executeShell("slow-command", 30.milliseconds)
        assertTrue(timedOut is AdbOperationResult.Failure)
        assertTrue((timedOut as AdbOperationResult.Failure).error is AdbError.Timeout)
        assertTrue(client.commandClosed.first().get())
        assertFalse(client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
        assertTrue(manager.executeShell("next-command") is AdbOperationResult.Success)
    }

    @Test
    fun `cancellation closes command stream but keeps session available for next command`() = runBlocking {
        val client = CommandClient(Behavior.Block, Behavior.Response(response("recovered")))
        val manager = connectedManager(client)

        val running = async(Dispatchers.Default) { manager.executeShell("cancel-command") }
        while (!client.commandStarted.firstOrNull()?.get().orFalse()) Thread.yield()
        running.cancel()
        running.join()

        assertTrue(client.commandClosed.first().get())
        assertFalse(client.closed.get())
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
        assertTrue(manager.executeShell("next-command") is AdbOperationResult.Success)
    }

    @Test
    fun `known stream and io failures are structured without invalidating session`() = runBlocking {
        val client = CommandClient(
            Behavior.Throw(ProtocolCommandStreamException()),
            Behavior.Throw(IOException()),
            Behavior.Response(response("recovered")),
        )
        val manager = connectedManager(client)

        val streamClosed = manager.executeShell("stream-command") as AdbOperationResult.Failure
        val ioFailure = manager.executeShell("io-command") as AdbOperationResult.Failure
        assertTrue(streamClosed.error is AdbError.CommandStreamClosed)
        assertTrue(ioFailure.error is AdbError.IoFailure)
        assertEquals(streamClosed.error.technicalCode, "ADB_COMMAND_STREAM_CLOSED")
        assertEquals(ioFailure.error.technicalCode, "ADB_IO_FAILURE")
        assertTrue(client.commandClosed.all(AtomicBoolean::get))
        assertTrue(manager.connectionState.value is AdbConnectionState.Connected)
        assertTrue(manager.executeShell("next-command") is AdbOperationResult.Success)
        assertFalse(client.closed.get())
    }

    private suspend fun connectedManager(client: CommandClient): DefaultAdbSessionManager {
        val manager = DefaultAdbSessionManager(SingleFactory(client), Dispatchers.IO)
        assertTrue(manager.connect(AdbEndpoint("test.invalid", 37001)) is AdbOperationResult.Success)
        return manager
    }

    private sealed interface Behavior {
        data class Response(val response: ProtocolShellResponse) : Behavior
        data class Throw(val error: Throwable) : Behavior
        data object Block : Behavior
    }

    private class CommandClient(vararg behavior: Behavior) : AdbProtocolClient {
        private val behaviors = ArrayDeque(behavior.toList())
        val receivedCommands = CopyOnWriteArrayList<String>()
        val commandStarted = CopyOnWriteArrayList<AtomicBoolean>()
        val commandClosed = CopyOnWriteArrayList<AtomicBoolean>()
        val closed = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse = response("probe")

        override fun openShellCommand(command: String): ProtocolShellCommand {
            receivedCommands += command
            val selected = behaviors.removeFirst()
            val started = AtomicBoolean(false).also(commandStarted::add)
            val streamClosed = AtomicBoolean(false).also(commandClosed::add)
            return object : ProtocolShellCommand {
                override fun execute(): ProtocolShellResponse {
                    started.set(true)
                    return when (selected) {
                        is Behavior.Response -> selected.response
                        is Behavior.Throw -> throw selected.error
                        Behavior.Block -> {
                            Thread.sleep(10_000)
                            response("late")
                        }
                    }
                }

                override fun close() { streamClosed.set(true) }
            }
        }

        override fun openShellStream(command: String): ProtocolShellStream = error("unused")
        override fun close() { closed.set(true) }
    }

    private class SingleFactory(private val client: AdbProtocolClient) : AdbProtocolClientFactory {
        override fun open(endpoint: AdbEndpoint) = client
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    companion object {
        private fun response(stdout: String, exitCode: Int = 0) = ProtocolShellResponse(
            stdout = stdout,
            stderr = "",
            exitCode = exitCode,
            streamsSeparated = true,
            wasTruncated = false,
        )
    }
}
