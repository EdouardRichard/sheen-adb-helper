package com.sheen.adb.feature.files

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.FileTransferProgress
import com.sheen.adb.core.ExclusiveAdbOperationLease
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.RemoteDirectorySnapshot
import com.sheen.adb.core.RemoteFileConflictPolicy
import com.sheen.adb.core.RemoteFileTransferReceipt
import com.sheen.adb.core.RemoteUploadCommitReceipt
import com.sheen.adb.core.RemoteUploadPlan
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FileTaskLifecycleTest {
    @Test
    fun `cancelling an awaiting conflict surfaces staging cleanup failure`() = runBlocking {
        val remote = FakeTransferGateway().apply {
            conflictExists = true
            cleanupFailure = true
        }
        val local = FakeLocalGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(remote, remote, local, scope)
        remote.connection.value = AdbConnectionState.Connected(AdbEndpoint("device.invalid", 1), "session")
        awaitState { viewModel.state.value.sessionId == "session" }
        viewModel.requestUpload()
        viewModel.onUploadSourceSelected("source")
        awaitState { viewModel.state.value.activeTask?.status == FileTaskStatus.AwaitingConflict }

        viewModel.resolveConflict(null)

        awaitState { viewModel.state.value.activeTask?.status is FileTaskStatus.CleanupFailed }
        scope.cancel()
    }

    @Test
    fun `transfer failure does not hide a subsequent cleanup failure`() = runBlocking {
        val remote = FakeTransferGateway().apply {
            uploadFailure = true
            cleanupFailure = true
        }
        val local = FakeLocalGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(remote, remote, local, scope)
        remote.connection.value = AdbConnectionState.Connected(AdbEndpoint("device.invalid", 1), "session")
        awaitState { viewModel.state.value.sessionId == "session" }
        viewModel.requestUpload()
        viewModel.onUploadSourceSelected("source")
        remote.finishUpload.complete(Unit)

        awaitState { viewModel.state.value.activeTask?.status is FileTaskStatus.CleanupFailed }
        scope.cancel()
    }

    @Test
    fun `ui contract exposes picker conflict progress cancel terminal close and cleanup failure`() {
        val waiting = FileConflictReducer.awaitDecision(
            stateWithTask(FileTaskStatus.Preparing),
            "task-one",
            "report.txt",
        )
        val waitingUi = fileTaskPresentation(waiting)
        assertTrue(waitingUi?.showConflict == true)
        assertEquals(waitingUi?.conflictDisplayName, "report.txt")
        assertTrue(waitingUi?.canCancel == true)

        val progressUi = fileTaskPresentation(stateWithTask(FileTaskStatus.Transferring(64, 128)))
        assertEquals(progressUi?.transferredBytes, 64L)
        assertEquals(progressUi?.totalBytes, 128L)
        assertTrue(progressUi?.canCancel == true)
        assertFalse(progressUi?.canDismiss == true)

        val cleanupError = FileTaskError(
            FileTaskErrorCategory.CLEANUP_FAILED,
            "临时文件清理失败",
            "请检查目标位置",
            "CLEANUP_FAILED",
        )
        val cleanupUi = fileTaskPresentation(stateWithTask(FileTaskStatus.CleanupFailed(cleanupError)))
        assertEquals(cleanupUi?.errorMessage, "临时文件清理失败")
        assertTrue(cleanupUi?.canDismiss == true)
        assertFalse(cleanupUi?.canCancel == true)
    }

    @Test
    fun `view model runs one upload reports progress and keeps a global summary across pages`() = runBlocking {
        val remote = FakeTransferGateway()
        val local = FakeLocalGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(remote, remote, local, scope)
        remote.connection.value = AdbConnectionState.Connected(AdbEndpoint("device.invalid", 1), "session")
        awaitState { viewModel.state.value.sessionId == "session" }

        viewModel.requestUpload()
        assertEquals(viewModel.state.value.pickerRequest, FilePickerRequest.UploadSource)
        viewModel.onUploadSourceSelected("source")
        awaitState { viewModel.state.value.activeTask?.status is FileTaskStatus.Transferring }
        assertEquals(
            (viewModel.state.value.activeTask?.status as FileTaskStatus.Transferring).transferredBytes,
            64L,
        )
        assertTrue(viewModel.state.value.taskSummary != null)

        viewModel.requestUpload()
        assertEquals(local.openSourceCalls, 1, "an active task must block another picker/task")
        remote.finishUpload.complete(Unit)
        awaitState { viewModel.state.value.activeTask?.status == FileTaskStatus.Succeeded }
        assertTrue(remote.leaseReleased)
        scope.cancel()
    }

    @Test
    fun `background cancels transfer while configuration change retains it and session switch clears it`() = runBlocking {
        val remote = FakeTransferGateway()
        val local = FakeLocalGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(remote, remote, local, scope)
        remote.connection.value = AdbConnectionState.Connected(AdbEndpoint("device.invalid", 1), "session")
        awaitState { viewModel.state.value.sessionId == "session" }
        viewModel.requestUpload()
        viewModel.onUploadSourceSelected("source")
        awaitState { viewModel.state.value.activeTask?.status is FileTaskStatus.Transferring }

        viewModel.onHostStopped(isChangingConfigurations = true)
        delay(30)
        assertTrue(viewModel.state.value.activeTask?.status is FileTaskStatus.Transferring)

        viewModel.onHostStopped(isChangingConfigurations = false)
        awaitState { viewModel.state.value.activeTask?.status == FileTaskStatus.Cancelled }
        assertTrue(remote.cleanupCalls > 0)

        viewModel.dismissTask()
        remote.reset()
        viewModel.requestUpload()
        viewModel.onUploadSourceSelected("source")
        awaitState { viewModel.state.value.activeTask?.status is FileTaskStatus.Transferring }
        remote.connection.value = AdbConnectionState.Connected(AdbEndpoint("other.invalid", 2), "other")
        awaitState { viewModel.state.value.sessionId == "other" }
        assertNull(viewModel.state.value.activeTask)
        awaitState { remote.cleanupCalls > 0 }
        scope.cancel()
    }

    @Test
    fun `task kinds cover upload download and apk extraction only`() {
        assertEquals(
            FileTaskKind.entries.toSet(),
            setOf(FileTaskKind.UPLOAD, FileTaskKind.DOWNLOAD, FileTaskKind.APK_EXTRACTION),
        )
    }

    @Test
    fun `task phases classify active and terminal states`() {
        val error = FileTaskError(
            FileTaskErrorCategory.CLEANUP_FAILED,
            "清理失败",
            "请检查受影响目标",
            "CLEANUP_FAILED",
        )
        val active = listOf(
            FileTaskStatus.Preparing,
            FileTaskStatus.AwaitingConflict,
            FileTaskStatus.Transferring(0L, null),
            FileTaskStatus.Verifying,
            FileTaskStatus.Committing,
        )
        val terminal = listOf(
            FileTaskStatus.Succeeded,
            FileTaskStatus.Failed(error),
            FileTaskStatus.Cancelled,
            FileTaskStatus.CleanupFailed(error),
        )

        assertTrue(active.none { it.isTerminal })
        assertTrue(terminal.all { it.isTerminal })
    }

    @Test
    fun `structured error categories cover the v003 contract`() {
        assertEquals(
            FileTaskErrorCategory.entries.toSet(),
            setOf(
                FileTaskErrorCategory.PERMISSION_DENIED,
                FileTaskErrorCategory.PATH_NOT_FOUND,
                FileTaskErrorCategory.PATH_TOO_LONG,
                FileTaskErrorCategory.UNSUPPORTED_TYPE,
                FileTaskErrorCategory.SYMLINK_DENIED,
                FileTaskErrorCategory.SYMLINK_MISSING,
                FileTaskErrorCategory.SYMLINK_LOOP,
                FileTaskErrorCategory.DIRECTORY_CAPACITY_EXCEEDED,
                FileTaskErrorCategory.SPACE_INSUFFICIENT,
                FileTaskErrorCategory.CONFLICT,
                FileTaskErrorCategory.OPERATION_CONFLICT,
                FileTaskErrorCategory.PROVIDER_UNSUPPORTED,
                FileTaskErrorCategory.SOURCE_CHANGED,
                FileTaskErrorCategory.INTEGRITY_UNAVAILABLE,
                FileTaskErrorCategory.APK_INCOMPLETE,
                FileTaskErrorCategory.NO_PROGRESS_TIMEOUT,
                FileTaskErrorCategory.STARTUP_TIMEOUT,
                FileTaskErrorCategory.SESSION_INVALID,
                FileTaskErrorCategory.STREAM_CLOSED,
                FileTaskErrorCategory.CANCELLED,
                FileTaskErrorCategory.CLEANUP_FAILED,
                FileTaskErrorCategory.LOGCAT_CAPABILITY_LIMITED,
            ),
        )
    }

    @Test
    fun `task progresses from preparing through transfer to success`() {
        val initial = FilesUiState(sessionId = "session-one")
        val started = FileTaskLifecycle.start(
            initial,
            FileTask("task-one", "session-one", FileTaskKind.DOWNLOAD, FileTaskStatus.Preparing),
        )
        val transferring = FileTaskLifecycle.transition(
            started,
            taskId = "task-one",
            sessionId = "session-one",
            status = FileTaskStatus.Transferring(transferredBytes = 64L, totalBytes = 128L),
        )
        val succeeded = FileTaskLifecycle.transition(
            transferring,
            taskId = "task-one",
            sessionId = "session-one",
            status = FileTaskStatus.Succeeded,
        )

        assertTrue(started.activeTask?.status is FileTaskStatus.Preparing)
        assertEquals((transferring.activeTask?.status as FileTaskStatus.Transferring).transferredBytes, 64L)
        assertSame(succeeded.activeTask?.status, FileTaskStatus.Succeeded)
        assertTrue(succeeded.activeTask?.status?.isTerminal == true)
    }

    @Test
    fun `failure and cancellation are terminal and reject later updates`() {
        val failure = FileTaskStatus.Failed(
            FileTaskError(
                category = FileTaskErrorCategory.PERMISSION_DENIED,
                userMessage = "目标不可访问",
                nextStep = "请选择其他目标",
                technicalCode = "PERMISSION_DENIED",
            ),
        )
        val failed = stateWithTask(FileTaskStatus.Preparing).let {
            FileTaskLifecycle.transition(it, "task-one", "session-one", failure)
        }
        val afterFailure = FileTaskLifecycle.transition(
            failed,
            "task-one",
            "session-one",
            FileTaskStatus.Transferring(1L, null),
        )
        val cancelled = stateWithTask(FileTaskStatus.Preparing).let {
            FileTaskLifecycle.transition(it, "task-one", "session-one", FileTaskStatus.Cancelled)
        }

        assertSame(afterFailure, failed)
        assertTrue(failed.activeTask?.status is FileTaskStatus.Failed)
        assertSame(cancelled.activeTask?.status, FileTaskStatus.Cancelled)
        assertTrue(cancelled.activeTask?.status?.isTerminal == true)
    }

    @Test
    fun `only a terminal task can be dismissed`() {
        val transferring = stateWithTask(FileTaskStatus.Transferring(1L, null))
        val retained = FileTaskLifecycle.dismissTerminal(transferring)
        val succeeded = transferring.copy(
            activeTask = transferring.activeTask?.copy(status = FileTaskStatus.Succeeded),
        )

        assertSame(retained, transferring)
        assertNull(FileTaskLifecycle.dismissTerminal(succeeded).activeTask)
    }

    @Test
    fun `session change clears task and stale updates are ignored`() {
        val oldState = stateWithTask(FileTaskStatus.Transferring(64L, 128L))
        val switched = FileTaskLifecycle.changeSession(oldState, "session-two")
        val stale = FileTaskLifecycle.transition(
            switched,
            "task-one",
            "session-one",
            FileTaskStatus.Succeeded,
        )

        assertEquals(switched.sessionId, "session-two")
        assertNull(switched.activeTask)
        assertSame(stale, switched)
    }

    @Test
    fun `task cannot start for a different session or replace an active task`() {
        val state = FilesUiState(sessionId = "session-one")
        val wrongSession = FileTask("wrong", "session-two", FileTaskKind.UPLOAD, FileTaskStatus.Preparing)
        val active = stateWithTask(FileTaskStatus.Preparing)
        val replacement = FileTask("replacement", "session-one", FileTaskKind.UPLOAD, FileTaskStatus.Preparing)

        assertSame(FileTaskLifecycle.start(state, wrongSession), state)
        assertSame(FileTaskLifecycle.start(active, replacement), active)
    }

    private fun stateWithTask(status: FileTaskStatus): FilesUiState = FilesUiState(
        sessionId = "session-one",
        activeTask = FileTask("task-one", "session-one", FileTaskKind.DOWNLOAD, status),
    )

    private suspend fun awaitState(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(5)
        }
        throw AssertionError("state was not reached")
    }

    private class FakeTransferGateway : RemoteBrowserGateway, RemoteTransferGateway {
        override val connection = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected())
        var finishUpload = CompletableDeferred<Unit>()
        var cleanupCalls = 0
        var leaseActive = false
        var leaseReleased = false
        var conflictExists = false
        var cleanupFailure = false
        var uploadFailure = false

        override suspend fun acquireFileLease(sessionId: String): AdbOperationResult<ExclusiveAdbOperationLease> {
            leaseActive = true
            leaseReleased = false
            return AdbOperationResult.Success(object : ExclusiveAdbOperationLease {
                override val token = "lease"
                override val sessionId = sessionId
                override val kind = AdbExclusiveOperationKind.FILE_TRANSFER
                override val isActive: Boolean get() = leaseActive
                override fun release() {
                    leaseActive = false
                    leaseReleased = true
                }
            })
        }

        override suspend fun load(path: String?, sessionId: String): AdbOperationResult<RemoteDirectorySnapshot> =
            AdbOperationResult.Cancelled

        override suspend fun prepareUpload(directory: String, displayName: String, sessionId: String) =
            AdbOperationResult.Success(
                RemoteUploadPlan(
                    sessionId,
                    directory,
                    displayName,
                    "$directory/.sheen-test.part",
                    "$directory/$displayName",
                    conflictExists,
                ),
            )

        override suspend fun upload(
            source: InputStream,
            sourceSize: Long?,
            plan: RemoteUploadPlan,
            sessionId: String,
            progress: (FileTransferProgress) -> Unit,
            lease: ExclusiveAdbOperationLease,
        ): AdbOperationResult<RemoteFileTransferReceipt> {
            assertTrue(leaseActive)
            progress(FileTransferProgress(64, sourceSize))
            finishUpload.await()
            return if (uploadFailure) {
                AdbOperationResult.Failure(AdbError.IoFailure(com.sheen.adb.core.AdbOperationStage.FILE_TRANSFER))
            } else {
                AdbOperationResult.Success(RemoteFileTransferReceipt(sessionId, 64))
            }
        }

        override suspend fun commitUpload(
            plan: RemoteUploadPlan,
            policy: RemoteFileConflictPolicy,
            sessionId: String,
            lease: ExclusiveAdbOperationLease,
        ): AdbOperationResult<RemoteUploadCommitReceipt> {
            assertTrue(leaseActive)
            return AdbOperationResult.Success(RemoteUploadCommitReceipt(sessionId, plan.finalPath, false))
        }

        override suspend fun cleanupUpload(
            stagedPath: String,
            sessionId: String,
            lease: ExclusiveAdbOperationLease?,
        ): AdbOperationResult<Unit> {
            cleanupCalls++
            return if (cleanupFailure) {
                AdbOperationResult.Failure(AdbError.RemoteCleanupFailed)
            } else {
                AdbOperationResult.Success(Unit)
            }
        }

        override suspend fun download(
            source: FileBrowserEntry,
            destination: java.io.OutputStream,
            sessionId: String,
            progress: (FileTransferProgress) -> Unit,
            lease: ExclusiveAdbOperationLease,
        ): AdbOperationResult<RemoteFileTransferReceipt> = error("unused")

        fun reset() {
            finishUpload = CompletableDeferred()
            cleanupCalls = 0
            leaseActive = false
            leaseReleased = false
            conflictExists = false
            cleanupFailure = false
            uploadFailure = false
        }
    }

    private class FakeLocalGateway : LocalDocumentGateway {
        var openSourceCalls = 0
        override fun openSource(reference: String): LocalDocumentResult<LocalTransferSource> {
            openSourceCalls++
            return LocalDocumentResult.Success(
                LocalTransferSource(
                    displayName = "upload.bin",
                    sizeBytes = 64,
                    mimeType = "application/octet-stream",
                    open = { ByteArrayInputStream(ByteArray(64)) },
                    verifyUnchanged = { LocalDocumentResult.Success(Unit) },
                ),
            )
        }

        override fun prepareTarget(
            treeReference: String,
            displayName: String,
            mimeType: String,
        ): LocalDocumentResult<LocalTransferTarget> = error("unused")
    }
}
