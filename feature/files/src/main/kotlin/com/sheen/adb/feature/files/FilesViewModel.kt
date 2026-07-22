package com.sheen.adb.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.FileTransferProgress
import com.sheen.adb.core.ExclusiveAdbOperationLease
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.RemoteDirectorySnapshot
import com.sheen.adb.core.RemoteFileConflictPolicy
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.core.RemoteFileTransferReceipt
import com.sheen.adb.core.RemotePathEntry
import com.sheen.adb.core.RemoteUploadCommitReceipt
import com.sheen.adb.core.RemoteUploadPlan
import com.sheen.adb.data.SafConflictPolicy
import com.sheen.adb.data.SafDocumentStore
import com.sheen.adb.data.SafStoreError
import com.sheen.adb.data.SafStoreResult
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface RemoteBrowserGateway {
    val connection: StateFlow<AdbConnectionState>
    suspend fun load(path: String?, sessionId: String): AdbOperationResult<RemoteDirectorySnapshot>
}

internal interface RemoteTransferGateway {
    suspend fun acquireFileLease(sessionId: String): AdbOperationResult<ExclusiveAdbOperationLease>

    suspend fun prepareUpload(
        directory: String,
        displayName: String,
        sessionId: String,
    ): AdbOperationResult<RemoteUploadPlan>

    suspend fun upload(
        source: InputStream,
        sourceSize: Long?,
        plan: RemoteUploadPlan,
        sessionId: String,
        progress: (FileTransferProgress) -> Unit,
        lease: ExclusiveAdbOperationLease,
    ): AdbOperationResult<RemoteFileTransferReceipt>

    suspend fun commitUpload(
        plan: RemoteUploadPlan,
        policy: RemoteFileConflictPolicy,
        sessionId: String,
        lease: ExclusiveAdbOperationLease,
    ): AdbOperationResult<RemoteUploadCommitReceipt>

    suspend fun cleanupUpload(
        stagedPath: String,
        sessionId: String,
        lease: ExclusiveAdbOperationLease? = null,
    ): AdbOperationResult<Unit>

    suspend fun download(
        source: FileBrowserEntry,
        destination: OutputStream,
        sessionId: String,
        progress: (FileTransferProgress) -> Unit,
        lease: ExclusiveAdbOperationLease,
    ): AdbOperationResult<RemoteFileTransferReceipt>
}

internal sealed interface LocalDocumentResult<out T> {
    data class Success<T>(val value: T) : LocalDocumentResult<T>
    data class Failure(val error: FileTaskError) : LocalDocumentResult<Nothing>
}

internal data class LocalTransferSource(
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String,
    val open: () -> InputStream,
    val verifyUnchanged: () -> LocalDocumentResult<Unit>,
)

internal data class LocalTransferTarget(
    val displayName: String,
    val conflictExists: Boolean,
    val availableBytes: Long?,
    val open: () -> OutputStream,
    val commit: (FileConflictPolicy) -> LocalDocumentResult<String>,
    val cleanup: () -> LocalDocumentResult<Unit>,
)

internal interface LocalDocumentGateway {
    fun openSource(reference: String): LocalDocumentResult<LocalTransferSource>
    fun prepareTarget(
        treeReference: String,
        displayName: String,
        mimeType: String,
    ): LocalDocumentResult<LocalTransferTarget>
}

private class CoreRemoteFilesGateway(private val manager: AdbSessionManager) :
    RemoteBrowserGateway,
    RemoteTransferGateway {
    override val connection = manager.connectionState
    override suspend fun acquireFileLease(sessionId: String) = manager.acquireExclusiveOperation(
        AdbExclusiveOperationKind.FILE_TRANSFER,
        sessionId,
    )
    override suspend fun load(path: String?, sessionId: String) =
        manager.loadRemoteDirectory(path = path, expectedSessionId = sessionId)

    override suspend fun prepareUpload(directory: String, displayName: String, sessionId: String) =
        manager.prepareRemoteUpload(directory, displayName, sessionId)

    override suspend fun upload(
        source: InputStream,
        sourceSize: Long?,
        plan: RemoteUploadPlan,
        sessionId: String,
        progress: (FileTransferProgress) -> Unit,
        lease: ExclusiveAdbOperationLease,
    ) = manager.pushRemoteFile(
        source,
        sourceSize,
        plan.stagedPath,
        sessionId,
        progress,
        externalLease = lease,
    )

    override suspend fun commitUpload(
        plan: RemoteUploadPlan,
        policy: RemoteFileConflictPolicy,
        sessionId: String,
        lease: ExclusiveAdbOperationLease,
    ) = manager.commitRemoteUpload(plan, policy, sessionId, externalLease = lease)

    override suspend fun cleanupUpload(
        stagedPath: String,
        sessionId: String,
        lease: ExclusiveAdbOperationLease?,
    ) = manager.cleanupRemoteStaging(stagedPath, sessionId, externalLease = lease)

    override suspend fun download(
        source: FileBrowserEntry,
        destination: OutputStream,
        sessionId: String,
        progress: (FileTransferProgress) -> Unit,
        lease: ExclusiveAdbOperationLease,
    ) = manager.pullRemoteFile(
        source.toRemotePathEntry(),
        destination,
        sessionId,
        progress,
        externalLease = lease,
    )
}

private class SafLocalDocumentGateway(private val store: SafDocumentStore) : LocalDocumentGateway {
    override fun openSource(reference: String): LocalDocumentResult<LocalTransferSource> = when (
        val result = store.openSource(reference)
    ) {
        is SafStoreResult.Success -> LocalDocumentResult.Success(
            LocalTransferSource(
                displayName = result.value.metadata.displayName,
                sizeBytes = result.value.metadata.sizeBytes,
                mimeType = result.value.metadata.mimeType,
                open = result.value::open,
                verifyUnchanged = {
                    when (val verified = store.verifySourceUnchanged(result.value)) {
                        is SafStoreResult.Success -> LocalDocumentResult.Success(Unit)
                        is SafStoreResult.Failure -> LocalDocumentResult.Failure(verified.error.toFileTaskError())
                    }
                },
            ),
        )
        is SafStoreResult.Failure -> LocalDocumentResult.Failure(result.error.toFileTaskError())
    }

    override fun prepareTarget(
        treeReference: String,
        displayName: String,
        mimeType: String,
    ): LocalDocumentResult<LocalTransferTarget> = when (
        val result = store.prepareTarget(treeReference, displayName, mimeType)
    ) {
        is SafStoreResult.Success -> {
            val target = result.value
            LocalDocumentResult.Success(
                LocalTransferTarget(
                    displayName = displayName,
                    conflictExists = target.original != null,
                    availableBytes = target.availableBytes,
                    open = { store.openTarget(target) },
                    commit = { policy ->
                        val safPolicy = when (policy) {
                            FileConflictPolicy.CANCEL -> SafConflictPolicy.CANCEL
                            FileConflictPolicy.OVERWRITE -> SafConflictPolicy.OVERWRITE
                            FileConflictPolicy.AUTO_RENAME -> SafConflictPolicy.AUTO_RENAME
                        }
                        when (val committed = store.commit(target, safPolicy)) {
                            is SafStoreResult.Success -> LocalDocumentResult.Success(committed.value.displayName)
                            is SafStoreResult.Failure -> LocalDocumentResult.Failure(committed.error.toFileTaskError())
                        }
                    },
                    cleanup = {
                        when (val cleanup = store.cleanup(target)) {
                            is SafStoreResult.Success -> LocalDocumentResult.Success(Unit)
                            is SafStoreResult.Failure -> LocalDocumentResult.Failure(cleanup.error.toFileTaskError())
                        }
                    },
                ),
            )
        }
        is SafStoreResult.Failure -> LocalDocumentResult.Failure(result.error.toFileTaskError())
    }
}

class FilesViewModel internal constructor(
    private val browserGateway: RemoteBrowserGateway,
    private val transferGateway: RemoteTransferGateway?,
    private val documentGateway: LocalDocumentGateway?,
    private val externalScope: CoroutineScope?,
) : ViewModel() {
    constructor(manager: AdbSessionManager, store: SafDocumentStore) : this(
        CoreRemoteFilesGateway(manager).let { gateway -> gateway },
        CoreRemoteFilesGateway(manager),
        SafLocalDocumentGateway(store),
        null,
    )

    constructor(manager: AdbSessionManager) : this(CoreRemoteFilesGateway(manager), null, null, null)

    internal constructor(gateway: RemoteBrowserGateway, externalScope: CoroutineScope?) :
        this(gateway, gateway as? RemoteTransferGateway, null, externalScope)

    private sealed interface PendingTransfer {
        val taskId: String
        val sessionId: String

        data class Upload(
            override val taskId: String,
            override val sessionId: String,
            val source: LocalTransferSource,
            val plan: RemoteUploadPlan,
        ) : PendingTransfer

        data class Download(
            override val taskId: String,
            override val sessionId: String,
            val source: FileBrowserEntry,
            val target: LocalTransferTarget,
        ) : PendingTransfer
    }

    private val scope: CoroutineScope get() = externalScope ?: viewModelScope
    private val mutableState = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var taskJob: Job? = null
    private var pendingTransfer: PendingTransfer? = null
    private var activeTransferLease: ExclusiveAdbOperationLease? = null
    private var requestSequence = 0L
    private var latestEntries = emptyMap<String, FileBrowserEntry>()

    init {
        scope.launch {
            browserGateway.connection.collect { connection ->
                when (connection) {
                    is AdbConnectionState.Connected -> {
                        if (mutableState.value.sessionId != connection.sessionId) {
                            cancelAndCleanupForSessionChange()
                            loadJob?.cancel()
                            mutableState.value = FileTaskLifecycle.changeSession(mutableState.value, connection.sessionId)
                            load(path = null)
                        }
                    }
                    else -> {
                        cancelAndCleanupForSessionChange()
                        loadJob?.cancel()
                        mutableState.value = FileTaskLifecycle.changeSession(mutableState.value, null)
                        mutableState.value = FilesReducer.reduce(mutableState.value, FilesAction.Disconnected)
                    }
                }
            }
        }
    }

    fun openSharedStorage() = load(null)
    fun openDeviceRoot() = load("/")
    fun openBreadcrumb(path: String) = load(path)
    fun openEntry(entry: FileBrowserEntry) {
        if (entry.enterable) load(entry.absolutePath) else select(entry.absolutePath)
    }
    fun refresh() = load(currentPath())
    fun select(path: String?) {
        mutableState.value = FilesReducer.reduce(mutableState.value, FilesAction.Select(path))
    }
    fun cancelLoad() {
        loadJob?.cancel()
        mutableState.value = FilesReducer.reduce(mutableState.value, FilesAction.Cancelled)
    }

    fun requestUpload() {
        if (!canStartPicker()) return
        mutableState.value = mutableState.value.copy(pickerRequest = FilePickerRequest.UploadSource)
    }

    fun requestDownload() {
        if (!canStartPicker()) return
        val selected = mutableState.value.selectedPath?.let(latestEntries::get)?.takeIf { it.selectable } ?: return
        mutableState.value = mutableState.value.copy(
            pickerRequest = FilePickerRequest.DownloadTarget(selected.name),
        )
    }

    fun onUploadSourceSelected(reference: String?) {
        mutableState.value = mutableState.value.copy(pickerRequest = null)
        if (reference == null || !canStartTask()) return
        val documents = documentGateway ?: return
        val remote = transferGateway ?: return
        val sessionId = mutableState.value.sessionId ?: return
        val directory = currentPath() ?: "/sdcard"
        when (val local = documents.openSource(reference)) {
            is LocalDocumentResult.Failure -> showImmediateFailure(FileTaskKind.UPLOAD, sessionId, local.error)
            is LocalDocumentResult.Success -> {
                val taskId = startTask(FileTaskKind.UPLOAD, sessionId) ?: return
                taskJob = scope.launch {
                    try {
                        when (val plan = remote.prepareUpload(directory, local.value.displayName, sessionId)) {
                            is AdbOperationResult.Success -> {
                                val pending = PendingTransfer.Upload(taskId, sessionId, local.value, plan.value)
                                pendingTransfer = pending
                                if (plan.value.conflictExists) {
                                    mutableState.value = FileConflictReducer.awaitDecision(
                                        mutableState.value,
                                        taskId,
                                        local.value.displayName,
                                    )
                                } else {
                                    executeUpload(pending, FileConflictPolicy.CANCEL)
                                }
                            }
                            is AdbOperationResult.Failure -> failTask(taskId, sessionId, plan.error.toFileTaskError())
                            AdbOperationResult.Cancelled -> cancelTaskState(taskId, sessionId)
                        }
                    } catch (_: CancellationException) {
                        cleanupPending()
                        cancelTaskState(taskId, sessionId)
                    }
                }
            }
        }
    }

    fun onDownloadTreeSelected(reference: String?) {
        mutableState.value = mutableState.value.copy(pickerRequest = null)
        if (reference == null || !canStartTask()) return
        val documents = documentGateway ?: return
        val sessionId = mutableState.value.sessionId ?: return
        val selected = mutableState.value.selectedPath?.let(latestEntries::get)?.takeIf { it.selectable } ?: return
        when (val target = documents.prepareTarget(reference, selected.name, "application/octet-stream")) {
            is LocalDocumentResult.Failure -> showImmediateFailure(FileTaskKind.DOWNLOAD, sessionId, target.error)
            is LocalDocumentResult.Success -> {
                if (target.value.availableBytes != null && selected.sizeBytes != null &&
                    target.value.availableBytes < selected.sizeBytes
                ) {
                    target.value.cleanup()
                    showImmediateFailure(FileTaskKind.DOWNLOAD, sessionId, spaceInsufficientError())
                    return
                }
                val taskId = startTask(FileTaskKind.DOWNLOAD, sessionId) ?: return
                val pending = PendingTransfer.Download(taskId, sessionId, selected, target.value)
                pendingTransfer = pending
                if (target.value.conflictExists) {
                    mutableState.value = FileConflictReducer.awaitDecision(mutableState.value, taskId, selected.name)
                } else {
                    taskJob = scope.launch { executeDownload(pending, FileConflictPolicy.CANCEL) }
                }
            }
        }
    }

    fun resolveConflict(decision: FileConflictPolicy?) {
        val pending = pendingTransfer ?: return
        val selected = decision ?: FileConflictPolicy.CANCEL
        if (selected == FileConflictPolicy.CANCEL) {
            taskJob = scope.launch {
                cleanupPending()
                if (mutableState.value.activeTask?.status !is FileTaskStatus.CleanupFailed) {
                    mutableState.value = FileConflictReducer.resolve(
                        mutableState.value,
                        pending.taskId,
                        null,
                    )
                }
            }
            return
        }
        mutableState.value = FileConflictReducer.resolve(mutableState.value, pending.taskId, selected)
        taskJob = scope.launch {
            when (pending) {
                is PendingTransfer.Upload -> executeUpload(pending, selected)
                is PendingTransfer.Download -> executeDownload(pending, selected)
            }
        }
    }

    fun cancelActiveTask() {
        val task = mutableState.value.activeTask ?: return
        if (task.status.isTerminal) return
        val running = taskJob
        if (running?.isActive == true) {
            running.cancel()
        } else {
            scope.launch {
                cleanupPending()
                if (mutableState.value.activeTask?.status?.isTerminal != true) {
                    cancelTaskState(task.taskId, task.sessionId)
                }
            }
        }
    }

    fun onHostStopped(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations) cancelActiveTask()
    }

    fun dismissTask() {
        mutableState.value = FileTaskLifecycle.dismissTerminal(mutableState.value)
    }

    private suspend fun cancelAndCleanupForSessionChange() {
        val running = taskJob
        if (running?.isActive == true) running.cancelAndJoin()
        if (pendingTransfer != null) cleanupPending()
        activeTransferLease?.release()
        activeTransferLease = null
    }

    private suspend fun executeUpload(pending: PendingTransfer.Upload, policy: FileConflictPolicy) {
        val remote = transferGateway ?: return
        val lease = when (val acquired = remote.acquireFileLease(pending.sessionId)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> {
                failTask(pending.taskId, pending.sessionId, acquired.error.toFileTaskError())
                return
            }
            AdbOperationResult.Cancelled -> {
                cancelTaskState(pending.taskId, pending.sessionId)
                return
            }
        }
        activeTransferLease = lease
        try {
            transition(pending, FileTaskStatus.Transferring(0, pending.source.sizeBytes))
            val uploaded = pending.source.open().use { input ->
                remote.upload(input, pending.source.sizeBytes, pending.plan, pending.sessionId, { progress ->
                    transition(
                        pending,
                        FileTaskStatus.Transferring(progress.transferredBytes, progress.totalBytes),
                    )
                }, lease)
            }
            if (uploaded !is AdbOperationResult.Success) {
                cleanupPending()
                if (!hasCleanupFailure()) finishFromAdbResult(pending, uploaded)
                return
            }
            transition(pending, FileTaskStatus.Verifying)
            val verified = pending.source.verifyUnchanged()
            if (verified is LocalDocumentResult.Failure) {
                cleanupPending()
                if (!hasCleanupFailure()) failTask(pending.taskId, pending.sessionId, verified.error)
                return
            }
            transition(pending, FileTaskStatus.Committing)
            val corePolicy = when (policy) {
                FileConflictPolicy.CANCEL -> RemoteFileConflictPolicy.CANCEL
                FileConflictPolicy.OVERWRITE -> RemoteFileConflictPolicy.OVERWRITE
                FileConflictPolicy.AUTO_RENAME -> RemoteFileConflictPolicy.AUTO_RENAME
            }
            when (val committed = remote.commitUpload(pending.plan, corePolicy, pending.sessionId, lease)) {
                is AdbOperationResult.Success -> transition(pending, FileTaskStatus.Succeeded)
                is AdbOperationResult.Failure -> {
                    cleanupPending()
                    if (!hasCleanupFailure()) {
                        failTask(pending.taskId, pending.sessionId, committed.error.toFileTaskError())
                    }
                }
                AdbOperationResult.Cancelled -> {
                    cleanupPending()
                    if (!hasCleanupFailure()) cancelTaskState(pending.taskId, pending.sessionId)
                }
            }
            pendingTransfer = null
        } catch (_: CancellationException) {
            cleanupPending()
            cancelTaskState(pending.taskId, pending.sessionId)
        } finally {
            lease.release()
            if (activeTransferLease === lease) activeTransferLease = null
        }
    }

    private suspend fun executeDownload(pending: PendingTransfer.Download, policy: FileConflictPolicy) {
        val remote = transferGateway ?: return
        val lease = when (val acquired = remote.acquireFileLease(pending.sessionId)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> {
                failTask(pending.taskId, pending.sessionId, acquired.error.toFileTaskError())
                return
            }
            AdbOperationResult.Cancelled -> {
                cancelTaskState(pending.taskId, pending.sessionId)
                return
            }
        }
        activeTransferLease = lease
        try {
            transition(pending, FileTaskStatus.Transferring(0, pending.source.sizeBytes))
            val downloaded = pending.target.open().use { output ->
                remote.download(pending.source, output, pending.sessionId, { progress ->
                    transition(
                        pending,
                        FileTaskStatus.Transferring(progress.transferredBytes, progress.totalBytes),
                    )
                }, lease)
            }
            if (downloaded !is AdbOperationResult.Success) {
                cleanupPending()
                if (!hasCleanupFailure()) finishFromAdbResult(pending, downloaded)
                return
            }
            transition(pending, FileTaskStatus.Verifying)
            transition(pending, FileTaskStatus.Committing)
            when (val committed = pending.target.commit(policy)) {
                is LocalDocumentResult.Success -> transition(pending, FileTaskStatus.Succeeded)
                is LocalDocumentResult.Failure -> {
                    cleanupPending()
                    if (!hasCleanupFailure()) failTask(pending.taskId, pending.sessionId, committed.error)
                }
            }
            pendingTransfer = null
        } catch (_: CancellationException) {
            cleanupPending()
            cancelTaskState(pending.taskId, pending.sessionId)
        } finally {
            lease.release()
            if (activeTransferLease === lease) activeTransferLease = null
        }
    }

    private suspend fun cleanupPending() {
        val pending = pendingTransfer ?: return
        val result = withContext(NonCancellable) {
            when (pending) {
                is PendingTransfer.Upload -> transferGateway?.cleanupUpload(
                    pending.plan.stagedPath,
                    pending.sessionId,
                    activeTransferLease,
                )
                is PendingTransfer.Download -> pending.target.cleanup()
            }
        }
        pendingTransfer = null
        val failed = when (result) {
            is AdbOperationResult.Failure -> result.error.toFileTaskError()
            is LocalDocumentResult.Failure -> result.error
            else -> null
        }
        if (failed != null) {
            transition(pending, FileTaskStatus.CleanupFailed(failed))
        }
    }

    private fun finishFromAdbResult(pending: PendingTransfer, result: AdbOperationResult<*>) {
        when (result) {
            is AdbOperationResult.Failure -> failTask(pending.taskId, pending.sessionId, result.error.toFileTaskError())
            AdbOperationResult.Cancelled -> cancelTaskState(pending.taskId, pending.sessionId)
            is AdbOperationResult.Success -> Unit
        }
    }

    private fun hasCleanupFailure(): Boolean =
        mutableState.value.activeTask?.status is FileTaskStatus.CleanupFailed

    private fun transition(pending: PendingTransfer, status: FileTaskStatus) {
        mutableState.value = FileTaskLifecycle.transition(
            mutableState.value,
            pending.taskId,
            pending.sessionId,
            status,
        )
    }

    private fun startTask(kind: FileTaskKind, sessionId: String): String? {
        if (!canStartTask()) return null
        val taskId = UUID.randomUUID().toString()
        mutableState.value = FileTaskLifecycle.start(
            mutableState.value,
            FileTask(taskId, sessionId, kind, FileTaskStatus.Preparing),
        )
        return taskId.takeIf { mutableState.value.activeTask?.taskId == taskId }
    }

    private fun showImmediateFailure(kind: FileTaskKind, sessionId: String, error: FileTaskError) {
        val taskId = startTask(kind, sessionId) ?: return
        failTask(taskId, sessionId, error)
    }

    private fun failTask(taskId: String, sessionId: String, error: FileTaskError) {
        mutableState.value = FileTaskLifecycle.transition(
            mutableState.value,
            taskId,
            sessionId,
            FileTaskStatus.Failed(error),
        )
    }

    private fun cancelTaskState(taskId: String, sessionId: String) {
        mutableState.value = FileTaskLifecycle.transition(
            mutableState.value,
            taskId,
            sessionId,
            FileTaskStatus.Cancelled,
        )
    }

    private fun canStartPicker(): Boolean =
        mutableState.value.sessionId != null &&
            mutableState.value.activeTask == null &&
            mutableState.value.pickerRequest == null

    private fun canStartTask(): Boolean = mutableState.value.sessionId != null && mutableState.value.activeTask == null

    private fun load(path: String?) {
        val sessionId = mutableState.value.sessionId ?: return
        loadJob?.cancel()
        val requestId = ++requestSequence
        mutableState.value = when (path) {
            null -> FilesReducer.reduce(mutableState.value, FilesAction.OpenSharedStorage)
            "/" -> FilesReducer.reduce(mutableState.value, FilesAction.OpenDeviceRoot)
            else -> FilesReducer.reduce(mutableState.value, FilesAction.OpenBreadcrumb(path))
        }
        loadJob = scope.launch {
            val result = browserGateway.load(path, sessionId)
            if (requestId != requestSequence || mutableState.value.sessionId != sessionId) return@launch
            mutableState.value = when (result) {
                is AdbOperationResult.Success -> reduceSnapshot(result.value, sessionId)
                is AdbOperationResult.Failure -> FilesReducer.reduce(
                    mutableState.value,
                    FilesAction.ShowError(result.error.toFileTaskError(), path),
                )
                AdbOperationResult.Cancelled -> FilesReducer.reduce(mutableState.value, FilesAction.Cancelled)
            }
        }
    }

    private fun reduceSnapshot(snapshot: RemoteDirectorySnapshot, sessionId: String): FilesUiState {
        if (snapshot.sessionId != sessionId) return mutableState.value
        val entries = snapshot.entries.map {
            FileBrowserEntry(
                absolutePath = it.absolutePath,
                name = it.displayName,
                kind = it.kind,
                linkResolution = it.linkResolution,
                targetKind = it.targetKind,
                sizeBytes = it.sizeBytes,
                modifiedEpochSeconds = it.modifiedEpochSeconds,
                mode = it.mode,
                deviceId = it.deviceId,
                inode = it.inode,
            )
        }
        latestEntries = entries.associateBy(FileBrowserEntry::absolutePath)
        val action = if (entries.isEmpty()) {
            FilesAction.ShowEmpty(snapshot.directory, snapshot.breadcrumbs)
        } else {
            FilesAction.ShowContent(snapshot.directory, entries, snapshot.breadcrumbs)
        }
        return FilesReducer.reduce(mutableState.value, action)
    }

    private fun currentPath(): String? = when (val browser = mutableState.value.browser) {
        is FilesBrowserState.Loading -> browser.path
        is FilesBrowserState.Content -> browser.path
        is FilesBrowserState.Empty -> browser.path
        is FilesBrowserState.Error -> browser.path
        else -> null
    }

    override fun onCleared() {
        loadJob?.cancel()
        taskJob?.cancel()
        super.onCleared()
    }
}

private fun FileBrowserEntry.toRemotePathEntry() = RemotePathEntry(
    absolutePath = absolutePath,
    displayName = name,
    kind = kind,
    sizeBytes = sizeBytes,
    modifiedEpochSeconds = modifiedEpochSeconds,
    mode = mode,
    deviceId = deviceId,
    inode = inode,
    linkResolution = linkResolution,
    targetKind = targetKind,
)

private fun SafStoreError.toFileTaskError(): FileTaskError {
    val category = when (this) {
        SafStoreError.NOT_FOUND -> FileTaskErrorCategory.PATH_NOT_FOUND
        SafStoreError.PERMISSION_DENIED -> FileTaskErrorCategory.PERMISSION_DENIED
        SafStoreError.SPACE_INSUFFICIENT -> FileTaskErrorCategory.SPACE_INSUFFICIENT
        SafStoreError.CONFLICT -> FileTaskErrorCategory.CONFLICT
        SafStoreError.PROVIDER_UNSUPPORTED -> FileTaskErrorCategory.PROVIDER_UNSUPPORTED
        SafStoreError.SOURCE_CHANGED -> FileTaskErrorCategory.SOURCE_CHANGED
        SafStoreError.INTEGRITY_UNAVAILABLE -> FileTaskErrorCategory.INTEGRITY_UNAVAILABLE
        SafStoreError.CLEANUP_FAILED -> FileTaskErrorCategory.CLEANUP_FAILED
        SafStoreError.COMMIT_FAILED, SafStoreError.IO_FAILURE -> FileTaskErrorCategory.STREAM_CLOSED
    }
    return FileTaskError(category, category.defaultMessage(), category.defaultNextStep(), name)
}

private fun AdbError.toFileTaskError(): FileTaskError {
    val category = when (this) {
        AdbError.RemotePermissionDenied, AdbError.RemoteFilePermissionDenied -> FileTaskErrorCategory.PERMISSION_DENIED
        AdbError.RemotePathNotFound, AdbError.RemoteFilePathNotFound -> FileTaskErrorCategory.PATH_NOT_FOUND
        AdbError.RemotePathInvalid -> FileTaskErrorCategory.PATH_TOO_LONG
        AdbError.RemoteDirectoryCapacityExceeded -> FileTaskErrorCategory.DIRECTORY_CAPACITY_EXCEEDED
        AdbError.RemoteSourceChanged -> FileTaskErrorCategory.SOURCE_CHANGED
        AdbError.RemoteIntegrityUnavailable -> FileTaskErrorCategory.INTEGRITY_UNAVAILABLE
        AdbError.NoProgressTimeout -> FileTaskErrorCategory.NO_PROGRESS_TIMEOUT
        AdbError.RemoteConflict -> FileTaskErrorCategory.CONFLICT
        AdbError.RemoteCleanupFailed -> FileTaskErrorCategory.CLEANUP_FAILED
        is AdbError.OperationConflict -> FileTaskErrorCategory.OPERATION_CONFLICT
        AdbError.RemoteSessionInvalid, is AdbError.SessionInvalid -> FileTaskErrorCategory.SESSION_INVALID
        AdbError.LocalFileReadFailed,
        AdbError.LocalFileWriteFailed,
        AdbError.RemoteFileStreamClosed,
        is AdbError.CommandStreamClosed,
        is AdbError.RemoteClosed,
        is AdbError.IoFailure,
        -> FileTaskErrorCategory.STREAM_CLOSED
        else -> FileTaskErrorCategory.STREAM_CLOSED
    }
    return FileTaskError(category, userMessage, nextStep, technicalCode)
}

private fun FileTaskErrorCategory.defaultMessage(): String = when (this) {
    FileTaskErrorCategory.SPACE_INSUFFICIENT -> "目标空间不足。"
    FileTaskErrorCategory.PROVIDER_UNSUPPORTED -> "所选位置不支持安全写入。"
    FileTaskErrorCategory.SOURCE_CHANGED -> "传输期间源文件发生变化。"
    FileTaskErrorCategory.INTEGRITY_UNAVAILABLE -> "无法可靠确认文件完整性。"
    FileTaskErrorCategory.CLEANUP_FAILED -> "临时结果清理失败。"
    else -> "文件操作未完成。"
}

private fun FileTaskErrorCategory.defaultNextStep(): String = when (this) {
    FileTaskErrorCategory.SPACE_INSUFFICIENT -> "请释放空间或选择其他位置。"
    FileTaskErrorCategory.PROVIDER_UNSUPPORTED -> "请选择支持创建、写入、重命名和删除的子目录。"
    FileTaskErrorCategory.CLEANUP_FAILED -> "请检查所选目录中的临时项。"
    else -> "请检查源文件和目标位置后重试。"
}

private fun spaceInsufficientError() = FileTaskError(
    FileTaskErrorCategory.SPACE_INSUFFICIENT,
    FileTaskErrorCategory.SPACE_INSUFFICIENT.defaultMessage(),
    FileTaskErrorCategory.SPACE_INSUFFICIENT.defaultNextStep(),
    "SPACE_INSUFFICIENT",
)
