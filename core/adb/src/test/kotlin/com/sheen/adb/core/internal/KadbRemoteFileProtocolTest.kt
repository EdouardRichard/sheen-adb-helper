package com.sheen.adb.core.internal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertThrows
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KadbRemoteFileProtocolTest {
    @Test
    fun `sync v1 and v2 receive and send stream bytes with progress and close`() = runBlocking {
        for (version in ProtocolSyncVersion.entries) {
            val received = "remote-$version".encodeToByteArray()
            val receiveSync = FakeSync(version = version, receivePayload = received)
            val destination = ByteArrayOutputStream()
            val receiveProgress = mutableListOf<Long>()

            val receivedBytes = KadbRemoteFileProtocol.receive(
                client = FakeClient(receiveSync),
                path = "/remote/source.bin",
                destination = destination,
                noProgressTimeout = 1.seconds,
                progress = receiveProgress::add,
            )

            assertEquals(destination.toByteArray(), received)
            assertEquals(receivedBytes, received.size.toLong())
            assertEquals(receiveProgress.last(), received.size.toLong())
            assertTrue(receiveSync.closed)

            val sent = "local-$version".encodeToByteArray()
            val sendSync = FakeSync(version = version)
            val sendProgress = mutableListOf<Long>()
            val sentBytes = KadbRemoteFileProtocol.send(
                client = FakeClient(sendSync),
                path = "/remote/staged.part",
                source = ByteArrayInputStream(sent),
                mode = 0x81A4,
                modifiedEpochMillis = 123_000L,
                noProgressTimeout = 1.seconds,
                progress = sendProgress::add,
            )

            assertEquals(sendSync.sentPayload(), sent)
            assertEquals(sentBytes, sent.size.toLong())
            assertEquals(sendProgress.last(), sent.size.toLong())
            assertTrue(sendSync.closed)
        }
    }

    @Test
    fun `transfer chunks stay within 64 KiB and counters cross Int max as Long`() = runBlocking {
        val total = Int.MAX_VALUE.toLong() + 65_537L
        val sync = FakeSync(simulatedReceiveBytes = total, requestedSendBufferBytes = 128 * 1024)
        val destination = CountingOutputStream()

        val received = KadbRemoteFileProtocol.receive(
            client = FakeClient(sync),
            path = "/remote/large.bin",
            destination = destination,
            noProgressTimeout = 5.seconds,
        )

        assertEquals(received, total)
        assertEquals(destination.count, total)
        assertTrue(destination.maxWrite <= 64 * 1024)

        val source = CountingInputStream(total)
        val sendSync = FakeSync(requestedSendBufferBytes = 128 * 1024, captureSentPayload = false)
        val sent = KadbRemoteFileProtocol.send(
            client = FakeClient(sendSync),
            path = "/remote/large.part",
            source = source,
            mode = 0x81A4,
            modifiedEpochMillis = 0L,
            noProgressTimeout = 5.seconds,
        )

        assertEquals(sent, total)
        assertTrue(source.maxRead <= 64 * 1024)
    }

    @Test
    fun `unknown length source streams until eof and cancellation closes sync`() = runBlocking {
        val unknownLength = object : InputStream() {
            private val delegate = ByteArrayInputStream("unknown".encodeToByteArray())
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
        }
        val sync = FakeSync()
        assertEquals(
            KadbRemoteFileProtocol.send(
                FakeClient(sync),
                "/remote/unknown.part",
                unknownLength,
                0x81A4,
                0L,
                1.seconds,
            ),
            7L,
        )

        val blocked = FakeSync(blockReceive = true)
        val job = async(Dispatchers.Default) {
            KadbRemoteFileProtocol.receive(
                FakeClient(blocked),
                "/remote/blocked.bin",
                ByteArrayOutputStream(),
                5.seconds,
            )
        }
        assertTrue(blocked.entered.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(blocked.closed)
    }

    @Test
    fun `v2 list and stat retain mode device and inode including empty directories`() = runBlocking {
        val sync = FakeSync(
            version = ProtocolSyncVersion.V2,
            entries = listOf(ProtocolRemoteEntry("a b;\$x", 0x81A4, 4, 5, 6, 7)),
            lstat = ProtocolRemoteStat(0xA1FF, 4, 5, 6, 7),
            stat = ProtocolRemoteStat(0x41ED, 4, 5, 8, 9),
        )
        val client = FakeClient(sync)

        assertEquals(KadbRemoteFileProtocol.list(client, "/sdcard", 500.milliseconds).entries.single().name, "a b;\$x")
        assertFalse(client.shellCalled)
        assertEquals(KadbRemoteFileProtocol.lstat(client, "/sdcard/link", 500.milliseconds).deviceId, 6)
        assertEquals(KadbRemoteFileProtocol.stat(client, "/sdcard/link", 500.milliseconds).inode, 9)
        assertEquals(KadbRemoteFileProtocol.list(FakeClient(FakeSync()), "/empty", 500.milliseconds).entries, emptyList<Any>())
    }

    @Test
    fun `v1 list and lstat expose degraded source without inventing identity`() = runBlocking {
        val sync = FakeSync(
            entries = listOf(ProtocolRemoteEntry("legacy", 0x81A4, 2, 3, null, null)),
            lstat = ProtocolRemoteStat(0x81A4, 2, 3, null, null),
        )
        val listed = KadbRemoteFileProtocol.list(FakeClient(sync), "/sdcard", 500.milliseconds)
        assertEquals(listed.version, ProtocolSyncVersion.V1)
        assertEquals(KadbRemoteFileProtocol.lstat(FakeClient(sync), "/x", 500.milliseconds).deviceId, null)
    }

    @Test
    fun `cancellation and short timeout close sync child stream`() = runBlocking {
        val cancelledSync = FakeSync(block = true)
        val job = async(Dispatchers.Default) {
            KadbRemoteFileProtocol.list(FakeClient(cancelledSync), "/slow", 5_000.milliseconds)
        }
        assertTrue(cancelledSync.entered.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(cancelledSync.closed)

        val timedSync = FakeSync(block = true)
        assertThrows(Exception::class.java) {
            runBlocking { KadbRemoteFileProtocol.list(FakeClient(timedSync), "/slow", 20.milliseconds) }
        }
        assertTrue(timedSync.closed)
    }

    @Test
    fun `sync v1 zero byte remote close retries once for receive and send`() = runBlocking {
        val payload = "legacy-transfer".encodeToByteArray()
        val receiveClient = SequencedClient(
            RemoteCloseSync(),
            FakeSync(receivePayload = payload),
        )
        val destination = ByteArrayOutputStream()

        val received = KadbRemoteFileProtocol.receive(
            receiveClient,
            "/sdcard/source.bin",
            destination,
            1.seconds,
        )

        assertEquals(received, payload.size.toLong())
        assertEquals(destination.toByteArray(), payload)
        assertEquals(receiveClient.openCount.get(), 2)

        val sendClient = SequencedClient(
            RemoteCloseSync(failSend = true),
            FakeSync(),
        )
        val sent = KadbRemoteFileProtocol.send(
            sendClient,
            "/sdcard/staged.part",
            ByteArrayInputStream(payload),
            0x81A4,
            0L,
            1.seconds,
        )

        assertEquals(sent, payload.size.toLong())
        assertEquals(sendClient.openCount.get(), 2)
    }

    @Test
    fun `v1 transfer retry is independent from v2 metadata capability`() = runBlocking {
        val payload = "mixed-capability".encodeToByteArray()
        val client = SequencedClient(
            RemoteCloseSync(
                version = ProtocolSyncVersion.V2,
                transferVersion = ProtocolSyncVersion.V1,
            ),
            FakeSync(
                version = ProtocolSyncVersion.V2,
                transferVersion = ProtocolSyncVersion.V1,
                receivePayload = payload,
            ),
        )
        val destination = ByteArrayOutputStream()

        val received = KadbRemoteFileProtocol.receive(
            client,
            "/sdcard/source.bin",
            destination,
            1.seconds,
        )

        assertEquals(received, payload.size.toLong())
        assertEquals(destination.toByteArray(), payload)
        assertEquals(client.openCount.get(), 2)
    }

    @Test
    fun `sync retry excludes progress local io explicit fail and v2`() = runBlocking {
        val partialClient = SequencedClient(
            RemoteCloseSync(receiveBeforeClose = byteArrayOf(1)),
            FakeSync(receivePayload = byteArrayOf(2)),
        )
        assertThrows(EOFException::class.java) {
            runBlocking {
                KadbRemoteFileProtocol.receive(
                    partialClient,
                    "/sdcard/source.bin",
                    ByteArrayOutputStream(),
                    1.seconds,
                )
            }
        }
        assertEquals(partialClient.openCount.get(), 1)

        val localSinkClient = SequencedClient(
            FakeSync(receivePayload = byteArrayOf(1)),
            FakeSync(receivePayload = byteArrayOf(2)),
        )
        val failingDestination = object : OutputStream() {
            override fun write(value: Int) = throw IOException("local destination failed")
            override fun write(buffer: ByteArray, offset: Int, length: Int) =
                throw IOException("local destination failed")
        }
        assertThrows(IOException::class.java) {
            runBlocking {
                KadbRemoteFileProtocol.receive(
                    localSinkClient,
                    "/sdcard/source.bin",
                    failingDestination,
                    1.seconds,
                )
            }
        }
        assertEquals(localSinkClient.openCount.get(), 1)

        val localSourceClient = SequencedClient(FakeSync(), FakeSync())
        val failingSource = object : InputStream() {
            override fun read(): Int = throw IOException("local source failed")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw IOException("local source failed")
        }
        assertThrows(IOException::class.java) {
            runBlocking {
                KadbRemoteFileProtocol.send(
                    localSourceClient,
                    "/sdcard/staged.part",
                    failingSource,
                    0x81A4,
                    0L,
                    1.seconds,
                )
            }
        }
        assertEquals(localSourceClient.openCount.get(), 1)

        val explicitFailClient = SequencedClient(
            RemoteCloseSync(receiveFailure = IOException("Permission denied")),
            FakeSync(receivePayload = byteArrayOf(2)),
        )
        assertThrows(IOException::class.java) {
            runBlocking {
                KadbRemoteFileProtocol.receive(
                    explicitFailClient,
                    "/sdcard/source.bin",
                    ByteArrayOutputStream(),
                    1.seconds,
                )
            }
        }
        assertEquals(explicitFailClient.openCount.get(), 1)

        val v2Client = SequencedClient(
            RemoteCloseSync(version = ProtocolSyncVersion.V2),
            FakeSync(version = ProtocolSyncVersion.V2, receivePayload = byteArrayOf(2)),
        )
        assertThrows(EOFException::class.java) {
            runBlocking {
                KadbRemoteFileProtocol.receive(
                    v2Client,
                    "/sdcard/source.bin",
                    ByteArrayOutputStream(),
                    1.seconds,
                )
            }
        }
        assertEquals(v2Client.openCount.get(), 1)
    }

    private class SequencedClient(vararg syncs: ProtocolSyncStream) : AdbProtocolClient {
        private val queue = ArrayDeque(syncs.toList())
        val openCount = AtomicInteger(0)
        override fun execute(command: String): ProtocolShellResponse = error("unused")
        override fun openShellStream(command: String): ProtocolShellStream = error("unused")
        override fun openSync(): ProtocolSyncStream {
            openCount.incrementAndGet()
            return queue.removeFirst()
        }
        override fun close() = Unit
    }

    private class RemoteCloseSync(
        override val version: ProtocolSyncVersion = ProtocolSyncVersion.V1,
        override val transferVersion: ProtocolSyncVersion = version,
        private val receiveBeforeClose: ByteArray = ByteArray(0),
        private val receiveFailure: IOException = EOFException("remote sync stream closed"),
        private val failSend: Boolean = false,
    ) : ProtocolSyncStream {
        override fun list(path: String) = emptyList<ProtocolRemoteEntry>()
        override fun lstat(path: String) = ProtocolRemoteStat(0x81A4, 0, 0, null, null)
        override fun stat(path: String) = lstat(path)
        override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
            if (receiveBeforeClose.isNotEmpty()) sink(receiveBeforeClose, 0, receiveBeforeClose.size)
            throw receiveFailure
        }
        override fun send(
            path: String,
            mode: Int,
            modifiedEpochMillis: Long,
            source: (ByteArray) -> Int,
        ) {
            if (failSend) throw EOFException("remote sync stream closed")
            while (source(ByteArray(64 * 1024)) >= 0) Unit
        }
        override fun close() = Unit
    }

    private class FakeClient(private val sync: FakeSync) : AdbProtocolClient {
        var shellCalled = false
        override fun execute(command: String): ProtocolShellResponse {
            shellCalled = true
            error("file names must not use shell")
        }
        override fun openShellStream(command: String): ProtocolShellStream = error("unused")
        override fun openSync(): ProtocolSyncStream = sync.copyForOpen()
        override fun close() = Unit
    }

    private class FakeSync(
        override val version: ProtocolSyncVersion = ProtocolSyncVersion.V1,
        override val transferVersion: ProtocolSyncVersion = version,
        private val entries: List<ProtocolRemoteEntry> = emptyList(),
        private val lstat: ProtocolRemoteStat = ProtocolRemoteStat(0x41ED, 0, 0, null, null),
        private val stat: ProtocolRemoteStat = lstat,
        private val block: Boolean = false,
        private val receivePayload: ByteArray = ByteArray(0),
        private val simulatedReceiveBytes: Long? = null,
        private val requestedSendBufferBytes: Int = 64 * 1024,
        private val blockReceive: Boolean = false,
        private val captureSentPayload: Boolean = true,
        private val shared: FakeSync? = null,
    ) : ProtocolSyncStream {
        val entered: CountDownLatch get() = (shared ?: this).enteredField
        private val enteredField = CountDownLatch(1)
        var closed: Boolean
            get() = (shared ?: this).closedField
            private set(value) { (shared ?: this).closedField = value }
        private var closedField = false
        private val sent = ByteArrayOutputStream()

        fun copyForOpen() = FakeSync(
            version,
            transferVersion,
            entries,
            lstat,
            stat,
            block,
            receivePayload,
            simulatedReceiveBytes,
            requestedSendBufferBytes,
            blockReceive,
            captureSentPayload,
            shared ?: this,
        )
        fun sentPayload(): ByteArray = (shared ?: this).sent.toByteArray()
        override fun list(path: String): List<ProtocolRemoteEntry> {
            if (block) {
                entered.countDown()
                Thread.sleep(30_000)
            }
            return entries
        }
        override fun lstat(path: String): ProtocolRemoteStat = lstat
        override fun stat(path: String): ProtocolRemoteStat = stat
        override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
            if (blockReceive) {
                entered.countDown()
                Thread.sleep(30_000)
            }
            val simulated = simulatedReceiveBytes
            if (simulated != null) {
                val chunk = ByteArray(64 * 1024)
                var remaining = simulated
                while (remaining > 0L) {
                    val count = minOf(chunk.size.toLong(), remaining).toInt()
                    sink(chunk, 0, count)
                    remaining -= count
                }
            } else if (receivePayload.isNotEmpty()) {
                sink(receivePayload, 0, receivePayload.size)
            }
        }
        override fun send(
            path: String,
            mode: Int,
            modifiedEpochMillis: Long,
            source: (ByteArray) -> Int,
        ) {
            val target = (shared ?: this).sent
            val buffer = ByteArray(requestedSendBufferBytes)
            while (true) {
                val count = source(buffer)
                if (count < 0) break
                if (captureSentPayload) target.write(buffer, 0, count)
            }
        }
        override fun close() { closed = true }
    }

    private class CountingOutputStream : OutputStream() {
        var count = 0L
        var maxWrite = 0
        override fun write(value: Int) {
            count++
            maxWrite = maxOf(maxWrite, 1)
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            count += length
            maxWrite = maxOf(maxWrite, length)
        }
    }

    private class CountingInputStream(private var remaining: Long) : InputStream() {
        var maxRead = 0
        override fun read(): Int = if (remaining-- > 0L) 0 else -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            remaining -= count
            maxRead = maxOf(maxRead, count)
            return count
        }
    }
}
