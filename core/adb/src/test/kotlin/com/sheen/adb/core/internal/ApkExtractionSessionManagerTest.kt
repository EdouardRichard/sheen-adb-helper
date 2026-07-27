package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.ApkComponentRole
import com.sheen.adb.core.ApkExtractionRequest
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.milliseconds

class ApkExtractionSessionManagerTest {
    @Test
    fun `discovery returns opaque base and split components bound to the current session`() = runBlocking {
        val factory = ExtractionFactory()
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val opened = manager.openApkExtraction(
            ApkExtractionRequest(
                expectedSessionId = sessionId,
                userId = 0,
                packageName = "com.example.client",
            ),
        ) as AdbOperationResult.Success
        val handle = opened.value

        assertEquals(handle.expectedSessionId, sessionId)
        assertEquals(handle.components.map { it.role }, listOf(ApkComponentRole.BASE, ApkComponentRole.SPLIT))
        assertEquals(handle.components.map { it.displayName }, listOf("base.apk", "split_config.en.apk"))
        assertEquals(handle.components.map { it.componentId }.distinct().size, 2)
        assertTrue(handle.components.none { it.toString().contains("/data/app/") })
        handle.close()
    }

    @Test
    fun `one extraction lease covers component progress and is released only after close`() = runBlocking {
        val factory = ExtractionFactory()
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val handle = (
            manager.openApkExtraction(ApkExtractionRequest(sessionId, 0, "com.example.client"))
                as AdbOperationResult.Success
            ).value
        val progress = mutableListOf<Long>()

        val receipt = handle.transfer(
            componentId = handle.components.first().componentId,
            destination = ByteArrayOutputStream(),
            progress = { progress += it.transferredBytes },
        )

        assertTrue(receipt is AdbOperationResult.Success)
        assertEquals(progress, listOf(3L, 6L))
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, sessionId)
                is AdbOperationResult.Failure,
        )

        handle.close()
        val afterClose = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, sessionId)
        assertTrue(afterClose is AdbOperationResult.Success)
        (afterClose as AdbOperationResult.Success).value.release()
    }

    @Test
    fun `cancellation and no progress timeout close sync resources before releasing ownership`() = runBlocking {
        val cancelFactory = ExtractionFactory(blockReceive = true)
        val cancelManager = connected(cancelFactory)
        val cancelSession = (cancelManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val cancelHandle = (
            cancelManager.openApkExtraction(ApkExtractionRequest(cancelSession, 0, "com.example.client"))
                as AdbOperationResult.Success
            ).value

        val transfer = async(Dispatchers.Default) {
            cancelHandle.transfer(
                cancelHandle.components.first().componentId,
                ByteArrayOutputStream(),
                noProgressTimeout = 5_000.milliseconds,
            )
        }
        assertTrue(cancelFactory.receiveStarted.await(2, TimeUnit.SECONDS))
        transfer.cancel()
        transfer.join()
        cancelHandle.close()
        assertTrue(cancelFactory.syncClosed.get())
        assertTrue(
            cancelManager.acquireExclusiveOperation(AdbExclusiveOperationKind.FILE_TRANSFER, cancelSession)
                is AdbOperationResult.Success,
        )

        val timeoutFactory = ExtractionFactory(blockReceive = true)
        val timeoutManager = connected(timeoutFactory)
        val timeoutSession = (timeoutManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val timeoutHandle = (
            timeoutManager.openApkExtraction(ApkExtractionRequest(timeoutSession, 0, "com.example.client"))
                as AdbOperationResult.Success
            ).value
        val timedOut = timeoutHandle.transfer(
            timeoutHandle.components.first().componentId,
            ByteArrayOutputStream(),
            noProgressTimeout = 20.milliseconds,
        )
        timeoutHandle.close()
        assertTrue(timedOut is AdbOperationResult.Failure)
        assertTrue(timeoutFactory.syncClosed.get())
    }

    @Test
    fun `disconnect and stale session reject results without exposing a previous component path`() = runBlocking {
        val factory = ExtractionFactory()
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        assertTrue(
            manager.openApkExtraction(ApkExtractionRequest("old-session", 0, "com.example.client"))
                is AdbOperationResult.Failure,
        )

        val handle = (
            manager.openApkExtraction(ApkExtractionRequest(sessionId, 0, "com.example.client"))
                as AdbOperationResult.Success
            ).value
        manager.disconnect()
        val stale = handle.transfer(
            handle.components.first().componentId,
            ByteArrayOutputStream(),
        )
        assertTrue(stale is AdbOperationResult.Failure || stale is AdbOperationResult.Cancelled)
        assertFalse(stale.toString().contains("/data/app/"))
        handle.close()
    }

    private suspend fun connected(factory: ExtractionFactory): DefaultAdbSessionManager =
        DefaultAdbSessionManager(factory, Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("extract.invalid", 41001)) is AdbOperationResult.Success)
        }

    private class ExtractionFactory(
        private val blockReceive: Boolean = false,
    ) : AdbProtocolClientFactory {
        val receiveStarted = CountDownLatch(1)
        val syncClosed = AtomicBoolean(false)

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = object : AdbProtocolClient {
            override fun execute(command: String): ProtocolShellResponse = response(
                if (" path " in " $command ") {
                    "package:/data/app/example/base.apk\n" +
                        "package:/data/app/example/split_config.en.apk\n"
                } else {
                    ""
                },
            )

            override fun openShellStream(command: String): ProtocolShellStream = error("unused")

            override fun openSync(): ProtocolSyncStream = object : ProtocolSyncStream {
                override val version = ProtocolSyncVersion.V2

                override fun list(path: String): List<ProtocolRemoteEntry> = emptyList()

                override fun lstat(path: String): ProtocolRemoteStat = stat(path)

                override fun stat(path: String): ProtocolRemoteStat =
                    ProtocolRemoteStat(0x81A4, 6, 1, 7, path.hashCode().toLong())

                override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
                    receiveStarted.countDown()
                    if (blockReceive) {
                        while (!syncClosed.get()) Thread.sleep(5)
                    } else {
                        val first = byteArrayOf(1, 2, 3)
                        val second = byteArrayOf(4, 5, 6)
                        sink(first, 0, first.size)
                        sink(second, 0, second.size)
                    }
                }

                override fun close() {
                    syncClosed.set(true)
                }
            }

            override fun close() {
                syncClosed.set(true)
            }
        }

        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit

        override fun clearIdentity() = Unit
    }

    private companion object {
        fun response(stdout: String) = ProtocolShellResponse(
            stdout = stdout,
            stderr = "",
            exitCode = 0,
            streamsSeparated = true,
            wasTruncated = false,
        )
    }
}
