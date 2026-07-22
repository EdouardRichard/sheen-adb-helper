package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.core.RemoteLinkResolution
import com.sheen.adb.core.RemotePathEntry
import com.sheen.adb.core.RemoteFileConflictPolicy
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

class RemoteFileSessionManagerTest {
    @Test
    fun `remote upload plan probes conflict and keeps staging in target directory`() = runBlocking {
        val factory = CommitFactory(mutableSetOf("report.txt"))
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.prepareRemoteUpload("/sdcard/Download", "report.txt", sessionId)

        assertTrue(result is AdbOperationResult.Success)
        val plan = (result as AdbOperationResult.Success).value
        assertTrue(plan.conflictExists)
        assertEquals(plan.finalPath, "/sdcard/Download/report.txt")
        assertTrue(plan.stagedPath.startsWith("/sdcard/Download/.sheen-"))
        assertTrue(plan.stagedPath.endsWith(".part"))
    }

    @Test
    fun `remote upload plan can safely commit into device root`() = runBlocking {
        val factory = CommitFactory(mutableSetOf())
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val plan = (manager.prepareRemoteUpload("/", "report.txt", sessionId) as AdbOperationResult.Success).value
        factory.names += plan.stagedPath.substringAfterLast('/')

        val committed = manager.commitRemoteUpload(plan, RemoteFileConflictPolicy.CANCEL, sessionId)

        assertTrue(committed is AdbOperationResult.Success)
        assertEquals((committed as AdbOperationResult.Success).value.finalPath, "/report.txt")
    }

    @Test
    fun `remote upload staging rejects a parent that cannot fit a safe temporary name`() = runBlocking {
        val manager = connected(CommitFactory(mutableSetOf()))
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val longDirectory = "/" + "a".repeat(1_000)

        val result = withTimeout(200.milliseconds) {
            manager.prepareRemoteUpload(longDirectory, "x", sessionId)
        }

        assertTrue(result is AdbOperationResult.Failure && result.error is AdbError.RemotePathInvalid)
    }

    @Test
    fun `remote commit defaults to cancel and auto rename never overwrites`() = runBlocking {
        val cancelFactory = CommitFactory(mutableSetOf("report.txt"))
        val cancelManager = connected(cancelFactory)
        val cancelSession = (cancelManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val cancelPlan = (cancelManager.prepareRemoteUpload(
            "/sdcard/Download",
            "report.txt",
            cancelSession,
        ) as AdbOperationResult.Success).value

        val cancelled = cancelManager.commitRemoteUpload(
            cancelPlan,
            RemoteFileConflictPolicy.CANCEL,
            cancelSession,
        )
        assertTrue(cancelled is AdbOperationResult.Failure && cancelled.error is AdbError.RemoteConflict)
        assertTrue(cancelFactory.fileCommands.isEmpty())

        val renameFactory = CommitFactory(mutableSetOf("report.txt"))
        val renameManager = connected(renameFactory)
        val renameSession = (renameManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val renamePlan = (renameManager.prepareRemoteUpload(
            "/sdcard/Download",
            "report.txt",
            renameSession,
        ) as AdbOperationResult.Success).value
        renameFactory.names += renamePlan.stagedPath.substringAfterLast('/')
        val renamed = renameManager.commitRemoteUpload(
            renamePlan,
            RemoteFileConflictPolicy.AUTO_RENAME,
            renameSession,
        )

        assertTrue(renamed is AdbOperationResult.Success)
        assertEquals((renamed as AdbOperationResult.Success).value.finalPath, "/sdcard/Download/report (1).txt")
        assertTrue("report.txt" in renameFactory.names)
        assertTrue("report (1).txt" in renameFactory.names)
    }

    @Test
    fun `remote overwrite uses backup rolls back failed commit and reports cleanup failure`() = runBlocking {
        val factory = CommitFactory(mutableSetOf("report.txt"), failMoveNumber = 2)
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val plan = (manager.prepareRemoteUpload(
            "/sdcard/Download",
            "report.txt",
            sessionId,
        ) as AdbOperationResult.Success).value
        factory.names += plan.stagedPath.substringAfterLast('/')

        val failed = manager.commitRemoteUpload(plan, RemoteFileConflictPolicy.OVERWRITE, sessionId)

        assertTrue(failed is AdbOperationResult.Failure)
        assertTrue("report.txt" in factory.names, "the original target must be restored")
        assertTrue(factory.fileCommands.count { it.startsWith("mv ") } >= 3, "rollback move must run")

        factory.failDeletes = true
        val cleanup = manager.cleanupRemoteStaging(plan.stagedPath, sessionId)
        assertTrue(cleanup is AdbOperationResult.Failure && cleanup.error is AdbError.RemoteCleanupFailed)
    }

    @Test
    fun `remote overwrite restores original when backup cleanup fails`() = runBlocking {
        val factory = CommitFactory(mutableSetOf("report.txt"))
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val plan = (manager.prepareRemoteUpload(
            "/sdcard/Download",
            "report.txt",
            sessionId,
        ) as AdbOperationResult.Success).value
        factory.names += plan.stagedPath.substringAfterLast('/')
        factory.failDeletes = true

        val result = manager.commitRemoteUpload(plan, RemoteFileConflictPolicy.OVERWRITE, sessionId)

        assertTrue(result is AdbOperationResult.Failure && result.error is AdbError.RemoteCommitFailed)
        assertEquals(factory.names, setOf("report.txt"), "failed commit must restore the original target")
        assertTrue(factory.fileCommands.count { it.startsWith("mv ") } >= 3, "rollback move must run")
    }

    @Test
    fun `remote upload commit commands run on injected io dispatcher`() = runBlocking {
        val factory = CommitFactory(mutableSetOf())
        val ioDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "remote-file-io")
        }.asCoroutineDispatcher()
        try {
            val manager = DefaultAdbSessionManager(factory, ioDispatcher).also {
                assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success)
            }
            val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
            val plan = (manager.prepareRemoteUpload(
                "/sdcard/Download",
                "report.txt",
                sessionId,
            ) as AdbOperationResult.Success).value
            factory.names += plan.stagedPath.substringAfterLast('/')

            val result = manager.commitRemoteUpload(plan, RemoteFileConflictPolicy.CANCEL, sessionId)

            assertTrue(result is AdbOperationResult.Success)
            assertTrue(factory.fileCommandThreads.isNotEmpty())
            assertTrue(factory.fileCommandThreads.all { it.startsWith("remote-file-io") })
        } finally {
            ioDispatcher.close()
        }
    }

    @Test
    fun `remote staging cleanup contains io exception as structured failure`() = runBlocking {
        val factory = CommitFactory(mutableSetOf()).apply { throwDeletes = true }
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.cleanupRemoteStaging(
            "/sdcard/Download/.sheen-0123456789abcdef.part",
            sessionId,
        )

        assertTrue(result is AdbOperationResult.Failure && result.error is AdbError.RemoteCleanupFailed)
    }

    @Test
    fun `download verifies stable source reports Long progress and releases lease`() = runBlocking {
        val payload = "stable-transfer".encodeToByteArray()
        val stat = ProtocolRemoteStat(0x81A4, payload.size.toLong(), 100, 7, 9)
        val factory = TransferFactory(payload = payload, stats = listOf(stat, stat))
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val result = manager.pullRemoteFile(
            remoteFile = transferEntry(payload.size.toLong()),
            destination = destination,
            expectedSessionId = sessionId,
            progress = { progress += it.transferredBytes },
        )

        assertTrue(result is AdbOperationResult.Success)
        assertEquals((result as AdbOperationResult.Success).value.transferredBytes, payload.size.toLong())
        assertEquals(destination.toByteArray(), payload)
        assertEquals(progress.last(), payload.size.toLong())
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId) is AdbOperationResult.Success,
        )
    }

    @Test
    fun `caller owned file lease remains active across transfer verification and commit gap`() = runBlocking {
        val payload = byteArrayOf(1, 2, 3)
        val stat = ProtocolRemoteStat(0x81A4, 3, 100, 7, 9)
        val manager = connected(TransferFactory(payload = payload, stats = listOf(stat, stat)))
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val lease = (manager.acquireExclusiveOperation(
            AdbExclusiveOperationKind.FILE_TRANSFER,
            sessionId,
        ) as AdbOperationResult.Success).value

        val transfer = manager.pullRemoteFile(
            transferEntry(3),
            ByteArrayOutputStream(),
            sessionId,
            externalLease = lease,
        )

        assertTrue(transfer is AdbOperationResult.Success)
        assertTrue(lease.isActive)
        val conflict = manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId)
        assertTrue(conflict is AdbOperationResult.Failure && conflict.error is AdbError.OperationConflict)
        lease.release()
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId) is AdbOperationResult.Success,
        )
    }

    @Test
    fun `download rejects changed source and unavailable integrity fallback`() = runBlocking {
        val before = ProtocolRemoteStat(0x81A4, 3, 100, 7, 9)
        val after = ProtocolRemoteStat(0x81A4, 4, 101, 7, 9)
        val changedManager = connected(TransferFactory(payload = byteArrayOf(1, 2, 3), stats = listOf(before, after)))
        val changedSession = (changedManager.connectionState.value as AdbConnectionState.Connected).sessionId

        val changed = changedManager.pullRemoteFile(
            transferEntry(3),
            ByteArrayOutputStream(),
            changedSession,
        )
        assertTrue(changed is AdbOperationResult.Failure && changed.error is AdbError.RemoteSourceChanged)

        val degraded = ProtocolRemoteStat(0x81A4, 3, 0, null, null)
        val unavailableManager = connected(
            TransferFactory(
                payload = byteArrayOf(1, 2, 3),
                stats = listOf(degraded),
                digestResponses = emptyList(),
            ),
        )
        val unavailableSession = (unavailableManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val unavailable = unavailableManager.pullRemoteFile(
            transferEntry(3),
            ByteArrayOutputStream(),
            unavailableSession,
        )
        assertTrue(unavailable is AdbOperationResult.Failure && unavailable.error is AdbError.RemoteIntegrityUnavailable)
    }

    @Test
    fun `download uses digest fallback and maps no progress timeout while closing and releasing`() = runBlocking {
        val degraded = ProtocolRemoteStat(0x81A4, 3, 0, null, null)
        val digestManager = connected(
            TransferFactory(
                payload = byteArrayOf(1, 2, 3),
                stats = listOf(degraded, degraded),
                digestResponses = listOf("a".repeat(64), "a".repeat(64)),
            ),
        )
        val digestSession = (digestManager.connectionState.value as AdbConnectionState.Connected).sessionId
        assertTrue(
            digestManager.pullRemoteFile(
                transferEntry(3),
                ByteArrayOutputStream(),
                digestSession,
            ) is AdbOperationResult.Success,
        )

        val blockedFactory = TransferFactory(payload = ByteArray(0), blockReceive = true)
        val blockedManager = DefaultAdbSessionManager(
            blockedFactory,
            Dispatchers.IO,
            transferNoProgressTimeout = 20.milliseconds,
            transferCancellationGrace = 20.milliseconds,
        ).also { assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success) }
        val blockedSession = (blockedManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val timedOut = blockedManager.pullRemoteFile(
            transferEntry(0),
            ByteArrayOutputStream(),
            blockedSession,
        )
        assertTrue(timedOut is AdbOperationResult.Failure && timedOut.error is AdbError.NoProgressTimeout)
        assertTrue(blockedFactory.syncClosed)
        assertTrue(
            blockedManager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, blockedSession) is AdbOperationResult.Success,
        )
    }

    @Test
    fun `transfer errors preserve local remote and session failure identity`() = runBlocking {
        suspend fun downloadWith(
            factory: TransferFactory,
            destination: OutputStream = ByteArrayOutputStream(),
        ): AdbOperationResult<*> {
            val manager = connected(factory)
            val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
            return manager.pullRemoteFile(transferEntry(1), destination, sessionId)
        }

        val localDestination = object : OutputStream() {
            override fun write(value: Int) = throw IOException("local destination failed")
            override fun write(buffer: ByteArray, offset: Int, length: Int) =
                throw IOException("local destination failed")
        }
        val localWrite = downloadWith(TransferFactory(byteArrayOf(1)), localDestination)
        assertEquals((localWrite as AdbOperationResult.Failure).error.technicalCode, "LOCAL_FILE_WRITE_FAILED")

        val uploadManager = connected(TransferFactory(ByteArray(0)))
        val uploadSession = (uploadManager.connectionState.value as AdbConnectionState.Connected).sessionId
        val localSource = object : InputStream() {
            override fun read(): Int = throw IOException("local source failed")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw IOException("local source failed")
        }
        val localRead = uploadManager.pushRemoteFile(
            localSource,
            1,
            "/sdcard/Download/.sheen-0123456789abcdef.part",
            uploadSession,
        )
        assertEquals((localRead as AdbOperationResult.Failure).error.technicalCode, "LOCAL_FILE_READ_FAILED")

        val permission = downloadWith(
            TransferFactory(byteArrayOf(1), receiveFailure = IOException("Permission denied")),
        )
        assertEquals((permission as AdbOperationResult.Failure).error.technicalCode, "REMOTE_FILE_PERMISSION_DENIED")

        val missing = downloadWith(
            TransferFactory(byteArrayOf(1), receiveFailure = IOException("No such file or directory")),
        )
        assertEquals((missing as AdbOperationResult.Failure).error.technicalCode, "REMOTE_FILE_PATH_NOT_FOUND")

        val streamClosed = downloadWith(
            TransferFactory(byteArrayOf(1), receiveFailure = EOFException("remote sync stream closed")),
        )
        assertEquals((streamClosed as AdbOperationResult.Failure).error.technicalCode, "REMOTE_FILE_STREAM_CLOSED")

        val staleManager = connected(TransferFactory(byteArrayOf(1)))
        val stale = staleManager.pullRemoteFile(
            transferEntry(1),
            ByteArrayOutputStream(),
            "stale-session",
        )
        assertEquals((stale as AdbOperationResult.Failure).error.technicalCode, "SESSION_INVALID")
    }

    @Test
    fun `cancelled download closes child stream and releases transfer lease`() = runBlocking {
        val factory = TransferFactory(payload = ByteArray(0), blockReceive = true)
        val manager = DefaultAdbSessionManager(
            factory,
            Dispatchers.IO,
            transferNoProgressTimeout = 5.seconds,
            transferCancellationGrace = 20.milliseconds,
        ).also { assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success) }
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val job = async(Dispatchers.Default) {
            manager.pullRemoteFile(transferEntry(0), ByteArrayOutputStream(), sessionId)
        }
        assertTrue(factory.receiveEntered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(factory.syncClosed)
        assertTrue(
            manager.acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, sessionId) is AdbOperationResult.Success,
        )
    }

    @Test
    fun `cancelled download closes old session when child ignores cancellation grace`() = runBlocking {
        val factory = TransferFactory(
            payload = ByteArray(0),
            blockReceive = true,
            stubbornReceiveMillis = 250,
        )
        val manager = DefaultAdbSessionManager(
            factory,
            Dispatchers.IO,
            transferNoProgressTimeout = 5.seconds,
            transferCancellationGrace = 20.milliseconds,
        ).also { assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success) }
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId
        val job = async(Dispatchers.Default) {
            manager.pullRemoteFile(transferEntry(0), ByteArrayOutputStream(), sessionId)
        }
        assertTrue(factory.receiveEntered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        val startedAt = System.nanoTime()

        job.cancelAndJoin()

        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(factory.clientClosed, "the old session client must be closed after the grace period")
        assertTrue(elapsedMillis < 200, "session fallback must unblock cancellation before the stubborn child returns")
        assertTrue(manager.connectionState.value is AdbConnectionState.Disconnected)
    }

    @Test
    fun `default shared storage root snapshot and breadcrumbs are session bound`() = runBlocking {
        val factory = BrowserFactory(
            mapOf(
                "/sdcard" to listOf(
                    ProtocolRemoteEntry("folder", 0x41ED, 0, 1, 1, 2),
                    ProtocolRemoteEntry("note.txt", 0x81A4, 9, 2, 1, 3),
                    ProtocolRemoteEntry("link", 0xA1FF, 0, 3, 1, 4),
                ),
            ),
        )
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.loadRemoteDirectory(null, sessionId, 2.seconds)
            as AdbOperationResult.Success
        assertEquals(result.value.directory, "/sdcard")
        assertEquals(result.value.sessionId, sessionId)
        assertEquals(result.value.breadcrumbs.map { it.path }, listOf("/", "/sdcard"))
        assertEquals(result.value.entries.map { it.kind }, listOf(RemoteFileKind.DIRECTORY, RemoteFileKind.SYMLINK, RemoteFileKind.FILE))
        assertEquals(result.value.entries[1].linkResolution, RemoteLinkResolution.VERIFIED)
    }

    @Test
    fun `root and empty directory are complete snapshots while denied and stale sessions are structured failures`() = runBlocking {
        val factory = BrowserFactory(mapOf("/" to emptyList(), "/denied" to null))
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        assertEquals(
            (manager.loadRemoteDirectory("/", sessionId) as AdbOperationResult.Success).value.entries,
            emptyList<Any>(),
        )
        val denied = manager.loadRemoteDirectory("/denied", sessionId)
        assertTrue(denied is AdbOperationResult.Failure && denied.error is AdbError.RemotePermissionDenied)
        val stale = manager.loadRemoteDirectory("/", "old-session")
        assertTrue(stale is AdbOperationResult.Failure && stale.error is AdbError.RemoteSessionInvalid)
    }

    @Test
    fun `directory over capacity returns no partial snapshot`() = runBlocking {
        val entries = List(10_001) { ProtocolRemoteEntry("f$it", 0x81A4, 0, 0, null, null) }
        val manager = connected(BrowserFactory(mapOf("/many" to entries)))
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.loadRemoteDirectory("/many", sessionId)
        assertTrue(result is AdbOperationResult.Failure && result.error is AdbError.RemoteDirectoryCapacityExceeded)
    }

    @Test
    fun `legacy sync dot entries are ignored instead of reported as protocol incompatible`() = runBlocking {
        val manager = connected(
            BrowserFactory(
                listings = mapOf(
                    "/sdcard" to listOf(
                        ProtocolRemoteEntry(".", 0x41ED, 0, 0, null, null),
                        ProtocolRemoteEntry("..", 0x41ED, 0, 0, null, null),
                        ProtocolRemoteEntry("Download", 0x41ED, 0, 1, null, null),
                    ),
                ),
                version = ProtocolSyncVersion.V1,
            ),
        )
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.loadRemoteDirectory(null, sessionId)

        assertTrue(result is AdbOperationResult.Success)
        assertEquals(
            (result as AdbOperationResult.Success).value.entries.map { it.displayName },
            listOf("Download"),
        )
    }

    @Test
    fun `legacy shared storage symlink is accepted when its directory can be listed`() = runBlocking {
        val manager = connected(
            BrowserFactory(
                listings = mapOf("/sdcard" to emptyList()),
                version = ProtocolSyncVersion.V1,
                directoryStatMode = 0xA1FF,
            ),
        )
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.loadRemoteDirectory(null, sessionId)

        assertTrue(result is AdbOperationResult.Success)
        assertEquals((result as AdbOperationResult.Success).value.directory, "/sdcard")
    }

    @Test
    fun `shared storage probe preserves the first candidate timeout`() = runBlocking {
        val factory = BrowserFactory(
            listings = mapOf("/sdcard" to emptyList()),
            listDelayMillis = 5_000,
        )
        val manager = connected(factory)
        val sessionId = (manager.connectionState.value as AdbConnectionState.Connected).sessionId

        val result = manager.loadRemoteDirectory(null, sessionId, 20.milliseconds)

        assertTrue(result is AdbOperationResult.Failure && result.error is AdbError.Timeout)
        assertEquals(factory.listCalls.get(), 1)
    }

    private suspend fun connected(factory: BrowserFactory) =
        DefaultAdbSessionManager(factory, Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success)
        }

    private suspend fun connected(factory: TransferFactory) =
        DefaultAdbSessionManager(factory, Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success)
        }

    private suspend fun connected(factory: CommitFactory) =
        DefaultAdbSessionManager(factory, Dispatchers.IO).also {
            assertTrue(it.connect(AdbEndpoint("files.invalid", 40001)) is AdbOperationResult.Success)
        }

    private fun transferEntry(size: Long) = RemotePathEntry(
        absolutePath = "/sdcard/source.bin",
        displayName = "source.bin",
        kind = RemoteFileKind.FILE,
        sizeBytes = size,
        modifiedEpochSeconds = 100,
        mode = 0x81A4,
        deviceId = 7,
        inode = 9,
    )

    private class TransferFactory(
        private val payload: ByteArray,
        stats: List<ProtocolRemoteStat> = listOf(ProtocolRemoteStat(0x81A4, payload.size.toLong(), 100, 7, 9)),
        digestResponses: List<String> = listOf("a".repeat(64), "a".repeat(64)),
        private val blockReceive: Boolean = false,
        private val stubbornReceiveMillis: Long = 0,
        private val receiveFailure: IOException? = null,
    ) : AdbProtocolClientFactory {
        private val statQueue = ArrayDeque(stats)
        private val digestQueue = ArrayDeque(digestResponses)
        val receiveEntered = java.util.concurrent.CountDownLatch(1)
        @Volatile var syncClosed = false
        @Volatile var clientClosed = false

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = object : AdbProtocolClient {
            override fun execute(command: String): ProtocolShellResponse = when {
                command.contains("sha256sum") -> digestQueue.pollFirst()?.let {
                    ProtocolShellResponse("$it  source\n", "", 0, true, false)
                } ?: ProtocolShellResponse("", "unsupported", 127, true, false)
                command.contains("current-user") -> ProtocolShellResponse("0\n", "", 0, true, false)
                else -> ProtocolShellResponse("ready\n", "", 0, true, false)
            }
            override fun openShellStream(command: String): ProtocolShellStream = error("unused")
            override fun openSync(): ProtocolSyncStream = object : ProtocolSyncStream {
                override val version = ProtocolSyncVersion.V2
                override fun list(path: String) = emptyList<ProtocolRemoteEntry>()
                override fun lstat(path: String) = stat(path)
                override fun stat(path: String): ProtocolRemoteStat =
                    statQueue.pollFirst() ?: statQueue.peekLast() ?: ProtocolRemoteStat(0x81A4, 0, 0, null, null)
                override fun recv(path: String, sink: (ByteArray, Int, Int) -> Unit) {
                    receiveEntered.countDown()
                    if (blockReceive && stubbornReceiveMillis > 0) {
                        val deadline = System.nanoTime() + stubbornReceiveMillis * 1_000_000
                        while (!clientClosed && System.nanoTime() < deadline) {
                            try {
                                Thread.sleep(10)
                            } catch (_: InterruptedException) {
                                // Simulates a protocol read that ignores coroutine/thread cancellation.
                            }
                        }
                    } else if (blockReceive) {
                        Thread.sleep(30_000)
                    }
                    receiveFailure?.let { throw it }
                    if (payload.isNotEmpty()) sink(payload, 0, payload.size)
                }
                override fun send(
                    path: String,
                    mode: Int,
                    modifiedEpochMillis: Long,
                    source: (ByteArray) -> Int,
                ) {
                    val buffer = ByteArray(64 * 1024)
                    while (source(buffer) >= 0) Unit
                }
                override fun close() { syncClosed = true }
            }
            override fun close() { clientClosed = true }
        }
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private class CommitFactory(
        val names: MutableSet<String>,
        private val failMoveNumber: Int? = null,
    ) : AdbProtocolClientFactory {
        val fileCommands = mutableListOf<String>()
        val fileCommandThreads = mutableListOf<String>()
        var failDeletes = false
        var throwDeletes = false
        private var moveNumber = 0

        override fun open(endpoint: AdbEndpoint): AdbProtocolClient = object : AdbProtocolClient {
            override fun execute(command: String): ProtocolShellResponse {
                if (command == "echo sheen-session-ready") {
                    return ProtocolShellResponse("ready\n", "", 0, true, false)
                }
                if (command.contains("current-user")) {
                    return ProtocolShellResponse("0\n", "", 0, true, false)
                }
                fileCommands += command
                fileCommandThreads += Thread.currentThread().name
                if (command.startsWith("mv ")) {
                    moveNumber++
                    if (moveNumber == failMoveNumber) return ProtocolShellResponse("", "failed", 1, true, false)
                    val paths = quotedPaths(command)
                    if (paths.size == 2) {
                        names -= paths[0].substringAfterLast('/')
                        names += paths[1].substringAfterLast('/')
                    }
                }
                if (command.startsWith("rm ")) {
                    if (throwDeletes) throw IOException("simulated child stream close")
                    if (failDeletes) return ProtocolShellResponse("", "failed", 1, true, false)
                    quotedPaths(command).firstOrNull()?.let { names -= it.substringAfterLast('/') }
                }
                return ProtocolShellResponse("", "", 0, true, false)
            }
            override fun openShellStream(command: String): ProtocolShellStream = error("unused")
            override fun openSync(): ProtocolSyncStream = object : ProtocolSyncStream {
                override val version = ProtocolSyncVersion.V1
                override fun list(path: String) = names.map {
                    ProtocolRemoteEntry(it, 0x81A4, 1, 1, null, null)
                }
                override fun lstat(path: String) = ProtocolRemoteStat(0x81A4, 1, 1, null, null)
                override fun stat(path: String) = lstat(path)
                override fun close() = Unit
            }
            override fun close() = Unit
        }
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit

        private fun quotedPaths(command: String): List<String> =
            Regex("'([^']*)'").findAll(command).map { it.groupValues[1] }.toList()
    }

    private class BrowserFactory(
        private val listings: Map<String, List<ProtocolRemoteEntry>?>,
        private val version: ProtocolSyncVersion = ProtocolSyncVersion.V2,
        private val directoryStatMode: Int = 0x41ED,
        private val listDelayMillis: Long = 0,
    ) : AdbProtocolClientFactory {
        val listCalls = java.util.concurrent.atomic.AtomicInteger(0)
        override fun open(endpoint: AdbEndpoint): AdbProtocolClient =
            BrowserClient(listings, version, directoryStatMode, listDelayMillis, listCalls)
        override suspend fun pair(endpoint: AdbEndpoint, pairingCode: CharArray) = Unit
        override fun clearIdentity() = Unit
    }

    private class BrowserClient(
        private val listings: Map<String, List<ProtocolRemoteEntry>?>,
        private val syncVersion: ProtocolSyncVersion,
        private val directoryStatMode: Int,
        private val listDelayMillis: Long,
        private val listCalls: java.util.concurrent.atomic.AtomicInteger,
    ) : AdbProtocolClient {
        override fun execute(command: String) = ProtocolShellResponse(
            stdout = if (command.contains("current-user")) "0\n" else "ready\n",
            stderr = "",
            exitCode = 0,
            streamsSeparated = true,
            wasTruncated = false,
        )
        override fun openShellStream(command: String): ProtocolShellStream = error("unused")
        override fun openSync(): ProtocolSyncStream = object : ProtocolSyncStream {
            override val version = syncVersion
            override fun list(path: String): List<ProtocolRemoteEntry> {
                listCalls.incrementAndGet()
                if (listDelayMillis > 0) Thread.sleep(listDelayMillis)
                return listings[path] ?: throw java.io.IOException("Permission denied")
            }
            override fun lstat(path: String) = ProtocolRemoteStat(directoryStatMode, 0, 0, 1, 1)
            override fun stat(path: String) = when {
                path.endsWith("/link") -> ProtocolRemoteStat(0x41ED, 0, 0, 1, 8)
                else -> ProtocolRemoteStat(directoryStatMode, 0, 0, 1, 1)
            }
            override fun close() = Unit
        }
        override fun close() = Unit
    }
}
