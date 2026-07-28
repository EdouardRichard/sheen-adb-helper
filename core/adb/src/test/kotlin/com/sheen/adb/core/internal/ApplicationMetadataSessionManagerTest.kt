package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.ApplicationMetadataStatus
import com.sheen.adb.core.ApplicationMutationResult
import com.sheen.adb.core.internal.applications.ApplicationMetadataParseFailure
import com.sheen.adb.core.internal.applications.ApplicationMetadataParseResult
import com.sheen.adb.core.internal.applications.ParsedApplicationMetadata
import com.sheen.adb.core.internal.applications.RemoteApkReadFailure
import com.sheen.adb.core.internal.applications.RemoteApkReadResult
import com.sheen.adb.core.internal.applications.RemoteApkReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.milliseconds

class ApplicationMetadataSessionManagerTest {
    @Test
    fun `package snapshot returns before metadata and flow emits one owned update per package`() = runBlocking {
        val client = metadataClient(listOf("com.example.one", "com.example.two"))
        val requested = CopyOnWriteArrayList<String>()
        val reader = RemoteApkReader { request ->
            requested += request.packageName
            RemoteApkReadResult.Success(request.packageName.toByteArray(), "/data/app/redacted/base.apk")
        }
        val manager = connectedManager(client, reader, successfulParser())

        val snapshot = (manager.listApplications() as AdbOperationResult.Success).value

        assertTrue(requested.isEmpty(), "metadata must not delay the package snapshot")
        val updates = manager.observeApplicationMetadata(snapshot.sessionId, listOf("zh-CN", "en-US")).toList()
        assertEquals(requested, snapshot.applications.map { it.packageName })
        assertEquals(updates.size, 2)
        updates.forEachIndexed { index, result ->
            assertTrue(result is AdbOperationResult.Success)
            val update = (result as AdbOperationResult.Success).value
            assertEquals(update.sessionId, snapshot.sessionId)
            assertEquals(update.userId, snapshot.userId)
            assertEquals(update.packageName, snapshot.applications[index].packageName)
            assertEquals(update.status, ApplicationMetadataStatus.AVAILABLE)
            assertEquals(update.displayName, snapshot.applications[index].packageName)
        }
    }

    @Test
    fun `metadata failures are classified per package and one failure does not hide later packages`() = runBlocking {
        val packages = listOf(
            "com.example.large",
            "com.example.missing",
            "com.example.broken",
            "com.example.changed",
            "com.example.after",
        )
        val requested = CopyOnWriteArrayList<String>()
        val reader = RemoteApkReader { request ->
            requested += request.packageName
            when (request.packageName) {
                "com.example.large" -> RemoteApkReadResult.Failure(RemoteApkReadFailure.TOO_LARGE)
                "com.example.missing" -> RemoteApkReadResult.Failure(RemoteApkReadFailure.UNAVAILABLE)
                "com.example.changed" -> RemoteApkReadResult.Failure(RemoteApkReadFailure.SESSION_CHANGED)
                else -> RemoteApkReadResult.Success(request.packageName.toByteArray(), "/data/app/redacted/base.apk")
            }
        }
        val parser: (ByteArray, List<String>) -> ApplicationMetadataParseResult = { _, _ ->
            ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.PARSE_FAILED)
        }
        val manager = connectedManager(metadataClient(packages), reader, parser)
        val snapshot = (manager.listApplications() as AdbOperationResult.Success).value

        val updates = manager.observeApplicationMetadata(snapshot.sessionId).toList()

        assertEquals(
            updates.map { (it as AdbOperationResult.Success).value.status },
            listOf(
                ApplicationMetadataStatus.TOO_LARGE,
                ApplicationMetadataStatus.UNAVAILABLE,
                ApplicationMetadataStatus.PARSE_FAILED,
                ApplicationMetadataStatus.SESSION_CHANGED,
                ApplicationMetadataStatus.SESSION_CHANGED,
            ),
        )
        assertEquals(requested, packages.take(4))
        assertEquals(updates.map { (it as AdbOperationResult.Success).value.packageName }, packages)
    }

    @Test
    fun `stale session is rejected before reader access`() = runBlocking {
        val requested = CopyOnWriteArrayList<String>()
        val reader = RemoteApkReader { request ->
            requested += request.packageName
            RemoteApkReadResult.Failure(RemoteApkReadFailure.UNAVAILABLE)
        }
        val manager = connectedManager(metadataClient(listOf("com.example.one")), reader, successfulParser())
        manager.listApplications()

        val results = manager.observeApplicationMetadata("stale-session").toList()

        assertEquals(results.size, 1)
        assertTrue(results.single() is AdbOperationResult.Failure)
        assertTrue((results.single() as AdbOperationResult.Failure).error is AdbError.ApplicationSessionInvalid)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `metadata enrichment does not change existing application mutation policy`() = runBlocking {
        val packageName = "com.example.client"
        val reader = RemoteApkReader { request ->
            RemoteApkReadResult.Success(request.packageName.toByteArray(), "/data/app/redacted/base.apk")
        }
        val manager = connectedManager(metadataClient(listOf(packageName)), reader, successfulParser())
        val snapshot = (manager.listApplications() as AdbOperationResult.Success).value
        manager.observeApplicationMetadata(snapshot.sessionId).toList()

        val result = manager.forceStopApplication(packageName, snapshot.sessionId)

        assertTrue(result is AdbOperationResult.Success)
        assertTrue((result as AdbOperationResult.Success).value is ApplicationMutationResult.RequestAccepted)
    }

    @Test
    fun `metadata transport cleanup keeps the primary Session usable`() = runBlocking {
        val packageName = "com.example.client"
        val primary = ClosableMetadataClient(metadataClient(listOf(packageName)))
        val child = MetadataChildClient(packageName)
        val factory = SequencedMetadataFactory(primary, child)
        val manager = DefaultAdbSessionManager(
            clientFactory = factory,
            ioDispatcher = Dispatchers.IO,
            metadataParser = successfulParser(),
            metadataBatchTimeout = 50.milliseconds,
            metadataCancellationGrace = 20.milliseconds,
        )
        assertTrue(manager.connect(AdbEndpoint("device.local", 37_001)) is AdbOperationResult.Success)
        val snapshot = (manager.listApplications() as AdbOperationResult.Success).value

        val updates = manager.observeApplicationMetadata(snapshot.sessionId).toList()
        val afterMetadata = manager.executeShell("process-probe")

        assertEquals(factory.openCount, 2, "Metadata must use a Session child client.")
        assertTrue(child.closed.get(), "The metadata child client must close after the batch.")
        assertFalse(primary.closed.get(), "Metadata cleanup must never close the primary Session client.")
        assertTrue(manager.connectionState.value is com.sheen.adb.core.AdbConnectionState.Connected)
        assertTrue(afterMetadata is AdbOperationResult.Success)
        assertEquals(
            (updates.single() as AdbOperationResult.Success).value.status,
            ApplicationMetadataStatus.TIMED_OUT,
        )
    }

    private suspend fun connectedManager(
        client: AdbProtocolClient,
        reader: RemoteApkReader,
        parser: (ByteArray, List<String>) -> ApplicationMetadataParseResult,
    ): DefaultAdbSessionManager {
        val manager = DefaultAdbSessionManager(
            clientFactory = SingleMetadataFactory(client),
            ioDispatcher = Dispatchers.IO,
            metadataReaderFactory = { _, _ -> reader },
            metadataParser = parser,
        )
        assertTrue(manager.connect(AdbEndpoint("device.local", 37001)) is AdbOperationResult.Success)
        return manager
    }

    private fun successfulParser(): (ByteArray, List<String>) -> ApplicationMetadataParseResult = { bytes, _ ->
        val packageName = bytes.toString(Charsets.UTF_8)
        ApplicationMetadataParseResult.Success(
            ParsedApplicationMetadata(
                packageName = packageName,
                displayName = packageName,
            ),
        )
    }

    private fun metadataClient(packages: List<String>) = ScriptedMetadataClient { command ->
        when {
            command == "am get-current-user" -> response("0\n")
            command.startsWith("pm list packages -3 -d --user 0") -> response("")
            command.startsWith("pm list packages -3 -U --user 0") -> response(
                packages.mapIndexed { index, packageName -> "package:$packageName uid:${10_123 + index}" }
                    .joinToString(separator = "\n", postfix = "\n"),
            )
            command.startsWith("am force-stop --user 0 ") -> response("")
            command.startsWith("pidof ") -> response("", exitCode = 1)
            else -> response("ok\n")
        }
    }

    private class ScriptedMetadataClient(
        private val script: (String) -> ProtocolShellResponse,
    ) : AdbProtocolClient {
        override fun execute(command: String): ProtocolShellResponse = script(command)
        override fun openShellStream(command: String): ProtocolShellStream = error("unused")
        override fun close() = Unit
    }

    private class ClosableMetadataClient(
        private val delegate: AdbProtocolClient,
    ) : AdbProtocolClient {
        val closed = AtomicBoolean(false)

        override fun execute(command: String): ProtocolShellResponse = delegate.execute(command)

        override fun openShellStream(command: String): ProtocolShellStream =
            delegate.openShellStream(command)

        override fun close() {
            closed.set(true)
        }
    }

    private class MetadataChildClient(
        private val packageName: String,
    ) : AdbProtocolClient {
        val closed = AtomicBoolean(false)
        private var syncCount = 0

        override fun execute(command: String): ProtocolShellResponse =
            response("package:/data/app/redacted/base.apk\n")

        override fun openShellStream(command: String): ProtocolShellStream = error("unused")

        override fun openSync(): ProtocolSyncStream {
            syncCount += 1
            return object : ProtocolSyncStream {
                override val version = ProtocolSyncVersion.V2

                override fun list(path: String): List<ProtocolRemoteEntry> = emptyList()

                override fun lstat(path: String): ProtocolRemoteStat = stat()

                override fun stat(path: String): ProtocolRemoteStat = stat()

                override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
                    if (syncCount == 2) {
                        val releaseAt = System.nanoTime() + 200.milliseconds.inWholeNanoseconds
                        while (System.nanoTime() < releaseAt) {
                            LockSupport.parkNanos(1_000_000L)
                            Thread.interrupted()
                        }
                    }
                }

                override fun send(
                    path: String,
                    mode: Int,
                    modifiedEpochMillis: Long,
                    source: (ByteArray) -> Int,
                ) = Unit

                override fun close() = Unit

                private fun stat() = ProtocolRemoteStat(
                    mode = 0,
                    size = packageName.toByteArray().size.toLong(),
                    modifiedEpochSeconds = 0,
                    deviceId = null,
                    inode = null,
                )
            }
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class SequencedMetadataFactory(
        private val primary: AdbProtocolClient,
        private val child: AdbProtocolClient,
    ) : AdbProtocolClientFactory {
        var openCount = 0
            private set

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient =
            if (openCount++ == 0) primary else child

        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit

        override fun clearIdentity() = Unit
    }

    private class SingleMetadataFactory(private val client: AdbProtocolClient) : AdbProtocolClientFactory {
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = client
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private companion object {
        fun response(stdout: String, stderr: String = "", exitCode: Int = 0) =
            ProtocolShellResponse(stdout, stderr, exitCode, streamsSeparated = true, wasTruncated = false)
    }
}
