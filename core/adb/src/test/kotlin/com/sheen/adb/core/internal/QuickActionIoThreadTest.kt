package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.CaptureSinkResult
import com.sheen.adb.core.QuickActionResult
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class QuickActionIoThreadTest {
    @Test
    fun `capability probes never execute on the caller thread`() {
        dispatchers { caller, io ->
            val client = ThreadRecordingClient()
            val manager = DefaultAdbSessionManager(SingleFactory(client), io)

            val result = runBlocking(caller) {
                assertTrue(
                    manager.connect(AdbEndpoint("thread-fixture.invalid", 47_111)) is
                        AdbOperationResult.Success,
                )
                val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
                manager.quickActionCapabilities(sessionId)
            }

            assertTrue(result is QuickActionResult.Success<*>)
            assertTrue(client.executeThreads.size >= 3)
            assertTrue(client.executeThreads.takeLast(3).all { it.startsWith(IO_THREAD) })
        }
    }

    @Test
    fun `recording opens reads and closes its stream on the IO dispatcher`() {
        dispatchers { caller, io ->
            val client = ThreadRecordingClient()
            val protocol = DefaultQuickActionProtocol(io)

            val result = runBlocking(caller) {
                protocol.recordScreen(
                    client = client,
                    sink = CountingSink(),
                    maxBytes = 1_024,
                    maxDuration = 1.seconds,
                    progress = {},
                )
            }

            assertTrue(result is QuickActionProtocolCaptureResult.Completed)
            assertTrue(client.openThreads.single().startsWith(IO_THREAD))
            val command = client.openCommands.single()
            assertTrue(command.contains("cat \"/data/local/tmp/.sheen-adb-helper-screenrecord.mp4\""))
            assertTrue(command.contains("wait ").not())
            assertTrue(command.contains("trap ").not())
            assertTrue(command.contains("/proc/self/fd/1").not())
            assertTrue(client.readThreads.all { it.startsWith(IO_THREAD) })
            assertTrue(client.closeThreads.single().startsWith(IO_THREAD))
            assertTrue(client.executeThreads.all { it.startsWith(IO_THREAD) })
            assertTrue(client.executeCommands.any { it.contains("sheen-screenrecord-start") })
            assertTrue(client.executeCommands.any { it.contains("sheen-screenrecord-status") })
            assertTrue(client.executeCommands.count { it.contains("sheen-screenrecord-cleanup") } == 2)
            assertTrue(client.executeCommands.none { it.contains("sheen-screenrecord-stop") })
        }
    }

    @Test
    fun `reboot dispatch and stream cleanup stay on the IO dispatcher`() {
        dispatchers { caller, io ->
            val client = ThreadRecordingClient()
            val protocol = DefaultQuickActionProtocol(io)

            val result = runBlocking(caller) { protocol.reboot(client) }

            assertEquals(result, QuickActionProtocolRebootResult.Accepted)
            assertTrue(client.openThreads.single().startsWith(IO_THREAD))
            assertTrue(client.readThreads.all { it.startsWith(IO_THREAD) })
            assertTrue(client.closeThreads.single().startsWith(IO_THREAD))
        }
    }

    private fun dispatchers(
        block: (
            caller: kotlinx.coroutines.ExecutorCoroutineDispatcher,
            io: kotlinx.coroutines.ExecutorCoroutineDispatcher,
        ) -> Unit,
    ) {
        val caller = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, CALLER_THREAD) }
            .asCoroutineDispatcher()
        val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, IO_THREAD) }
            .asCoroutineDispatcher()
        try {
            block(caller, io)
        } finally {
            caller.close()
            io.close()
        }
    }

    private class ThreadRecordingClient : AdbProtocolClient {
        val executeThreads = mutableListOf<String>()
        val executeCommands = mutableListOf<String>()
        val openThreads = mutableListOf<String>()
        val openCommands = mutableListOf<String>()
        val readThreads = mutableListOf<String>()
        val closeThreads = mutableListOf<String>()

        override fun execute(command: String): ProtocolShellResponse {
            executeThreads += Thread.currentThread().name
            executeCommands += command
            val stdout = when {
                command.contains("sheen-screenrecord-start") -> "STARTED"
                command.contains("sheen-screenrecord-status") -> "FINISHED 7"
                command.contains("sheen-screenrecord-cleanup") -> "CLEANED"
                else -> ""
            }
            return ProtocolShellResponse(stdout, "", 0, streamsSeparated = true, wasTruncated = false)
        }

        override fun openShellStream(command: String): ProtocolShellStream {
            openThreads += Thread.currentThread().name
            openCommands += command
            val packets = ArrayDeque(
                listOf(
                    ProtocolShellPacket.StandardOutput("fixture".encodeToByteArray()),
                    ProtocolShellPacket.Exit(0),
                ),
            )
            return object : ProtocolShellStream {
                override fun read(): ProtocolShellPacket {
                    readThreads += Thread.currentThread().name
                    return packets.removeFirst()
                }

                override fun close() {
                    closeThreads += Thread.currentThread().name
                }
            }
        }

        override fun close() = Unit
    }

    private class CountingSink : AdbCaptureSink {
        override var bytesWritten: Long = 0
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

    private class SingleFactory(
        private val client: AdbProtocolClient,
    ) : AdbProtocolClientFactory {
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = client

        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit

        override fun clearIdentity() = Unit
    }

    private companion object {
        const val CALLER_THREAD = "quick-action-caller"
        const val IO_THREAD = "quick-action-io"
    }
}
