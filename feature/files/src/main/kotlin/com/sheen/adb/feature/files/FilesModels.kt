package com.sheen.adb.feature.files

import com.sheen.adb.core.RemoteBreadcrumb
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.core.RemoteLinkResolution

enum class FileTaskKind {
    UPLOAD,
    DOWNLOAD,
    APK_EXTRACTION,
}

enum class FileConflictPolicy {
    CANCEL,
    OVERWRITE,
    AUTO_RENAME,
}

data class PendingFileConflict(
    val taskId: String,
    val displayName: String,
    val originalMustRemainUnchanged: Boolean = true,
)

data class FileTask(
    val taskId: String,
    val sessionId: String,
    val kind: FileTaskKind,
    val status: FileTaskStatus,
    val conflictPolicy: FileConflictPolicy = FileConflictPolicy.CANCEL,
) {
    init {
        require(taskId.isNotBlank())
        require(sessionId.isNotBlank())
    }
}

data class FilesUiState(
    val sessionId: String? = null,
    val activeTask: FileTask? = null,
    val browser: FilesBrowserState = FilesBrowserState.Initial,
    val selectedPath: String? = null,
    val pendingConflict: PendingFileConflict? = null,
    val pickerRequest: FilePickerRequest? = null,
)

sealed interface FilePickerRequest {
    data object UploadSource : FilePickerRequest
    data class DownloadTarget(val displayName: String) : FilePickerRequest
}

data class FileTaskSummary(
    val kind: FileTaskKind,
    val status: FileTaskStatus,
)

data class FileTaskPresentation(
    val stageLabel: String,
    val transferredBytes: Long? = null,
    val totalBytes: Long? = null,
    val showConflict: Boolean = false,
    val conflictDisplayName: String? = null,
    val canCancel: Boolean = false,
    val canDismiss: Boolean = false,
    val errorMessage: String? = null,
    val errorNextStep: String? = null,
)

val FilesUiState.taskSummary: FileTaskSummary?
    get() = activeTask?.let { FileTaskSummary(it.kind, it.status) }

internal fun fileTaskPresentation(state: FilesUiState): FileTaskPresentation? {
    val task = state.activeTask ?: return null
    val status = task.status
    val error = when (status) {
        is FileTaskStatus.Failed -> status.error
        is FileTaskStatus.CleanupFailed -> status.error
        else -> null
    }
    return FileTaskPresentation(
        stageLabel = when (status) {
            FileTaskStatus.Preparing -> "正在准备"
            FileTaskStatus.AwaitingConflict -> "等待处理同名文件"
            is FileTaskStatus.Transferring -> "正在传输"
            FileTaskStatus.Verifying -> "正在验证完整性"
            FileTaskStatus.Committing -> "正在安全提交"
            FileTaskStatus.Succeeded -> "传输完成"
            is FileTaskStatus.Failed -> "传输失败"
            FileTaskStatus.Cancelled -> "传输已取消"
            is FileTaskStatus.CleanupFailed -> "清理失败"
        },
        transferredBytes = (status as? FileTaskStatus.Transferring)?.transferredBytes,
        totalBytes = (status as? FileTaskStatus.Transferring)?.totalBytes,
        showConflict = status == FileTaskStatus.AwaitingConflict && state.pendingConflict != null,
        conflictDisplayName = state.pendingConflict?.displayName,
        canCancel = !status.isTerminal,
        canDismiss = status.isTerminal,
        errorMessage = error?.userMessage,
        errorNextStep = error?.nextStep,
    )
}

data class FileBrowserEntry(
    val absolutePath: String,
    val name: String,
    val kind: RemoteFileKind,
    val linkResolution: RemoteLinkResolution,
    val targetKind: RemoteFileKind?,
    val sizeBytes: Long?,
    val modifiedEpochSeconds: Long? = null,
    val mode: Int? = null,
    val deviceId: Long? = null,
    val inode: Long? = null,
) {
    val enterable: Boolean get() = kind == RemoteFileKind.DIRECTORY ||
        (kind == RemoteFileKind.SYMLINK && linkResolution == RemoteLinkResolution.VERIFIED && targetKind == RemoteFileKind.DIRECTORY)
    val badge: String? get() = when {
        kind != RemoteFileKind.SYMLINK -> null
        linkResolution == RemoteLinkResolution.VERIFIED -> "链接"
        linkResolution == RemoteLinkResolution.PERMISSION_DENIED -> "链接 · 无权限"
        linkResolution == RemoteLinkResolution.MISSING -> "链接 · 已失效"
        linkResolution == RemoteLinkResolution.LOOP -> "链接 · 循环"
        else -> "链接 · 不可验证"
    }
    val selectable: Boolean get() = kind == RemoteFileKind.FILE ||
        (kind == RemoteFileKind.SYMLINK && linkResolution == RemoteLinkResolution.VERIFIED && targetKind == RemoteFileKind.FILE)
}

sealed interface FilesBrowserState {
    data object Initial : FilesBrowserState
    data class Loading(val path: String?) : FilesBrowserState
    data class Content(
        val path: String,
        val entries: List<FileBrowserEntry>,
        val breadcrumbs: List<RemoteBreadcrumb>,
    ) : FilesBrowserState
    data class Empty(val path: String, val breadcrumbs: List<RemoteBreadcrumb>) : FilesBrowserState
    data class Error(val error: FileTaskError, val path: String? = null) : FilesBrowserState
    data object Disconnected : FilesBrowserState
    data object Cancelled : FilesBrowserState
}

sealed interface FilesAction {
    data object OpenSharedStorage : FilesAction
    data object OpenDeviceRoot : FilesAction
    data class OpenBreadcrumb(val path: String) : FilesAction
    data object Refresh : FilesAction
    data class Select(val path: String?) : FilesAction
    data class ShowContent(
        val path: String,
        val entries: List<FileBrowserEntry>,
        val breadcrumbs: List<RemoteBreadcrumb>,
    ) : FilesAction
    data class ShowEmpty(val path: String, val breadcrumbs: List<RemoteBreadcrumb>) : FilesAction
    data class ShowError(val error: FileTaskError, val path: String? = null) : FilesAction
    data object Disconnected : FilesAction
    data object Cancelled : FilesAction
}

internal object FilesReducer {
    fun reduce(state: FilesUiState, action: FilesAction): FilesUiState = when (action) {
        FilesAction.OpenSharedStorage -> state.copy(browser = FilesBrowserState.Loading(null), selectedPath = null)
        FilesAction.OpenDeviceRoot -> state.copy(browser = FilesBrowserState.Loading("/"), selectedPath = null)
        is FilesAction.OpenBreadcrumb -> state.copy(browser = FilesBrowserState.Loading(action.path), selectedPath = null)
        FilesAction.Refresh -> state.copy(browser = FilesBrowserState.Loading(currentPath(state.browser)))
        is FilesAction.Select -> state.copy(selectedPath = action.path)
        is FilesAction.ShowContent -> state.copy(
            browser = FilesBrowserState.Content(action.path, action.entries, action.breadcrumbs),
            selectedPath = state.selectedPath?.takeIf { selected -> action.entries.any { it.absolutePath == selected } },
        )
        is FilesAction.ShowEmpty -> state.copy(
            browser = FilesBrowserState.Empty(action.path, action.breadcrumbs),
            selectedPath = null,
        )
        is FilesAction.ShowError -> state.copy(browser = FilesBrowserState.Error(action.error, action.path))
        FilesAction.Disconnected -> state.copy(browser = FilesBrowserState.Disconnected, selectedPath = null)
        FilesAction.Cancelled -> state.copy(browser = FilesBrowserState.Cancelled)
    }

    private fun currentPath(browser: FilesBrowserState): String? = when (browser) {
        is FilesBrowserState.Loading -> browser.path
        is FilesBrowserState.Content -> browser.path
        is FilesBrowserState.Empty -> browser.path
        is FilesBrowserState.Error -> browser.path
        else -> null
    }
}

internal fun breadcrumbDisplaySegments(
    breadcrumbs: List<RemoteBreadcrumb>,
): List<Pair<String, String>> = breadcrumbs.mapIndexed { index, breadcrumb ->
    val text = when (index) {
        0 -> "/"
        1 -> breadcrumb.label
        else -> "/${breadcrumb.label}"
    }
    text to breadcrumb.path
}

internal fun breadcrumbDisplayPath(breadcrumbs: List<RemoteBreadcrumb>): String =
    breadcrumbDisplaySegments(breadcrumbs).joinToString(separator = "") { it.first }.ifEmpty { "/" }

internal object FileTaskLifecycle {
    fun start(state: FilesUiState, task: FileTask): FilesUiState {
        if (state.sessionId != task.sessionId || state.activeTask != null) return state
        return state.copy(activeTask = task)
    }

    fun transition(
        state: FilesUiState,
        taskId: String,
        sessionId: String,
        status: FileTaskStatus,
    ): FilesUiState {
        val task = state.activeTask ?: return state
        if (state.sessionId != sessionId || task.sessionId != sessionId || task.taskId != taskId) return state
        if (task.status.isTerminal) return state
        return state.copy(activeTask = task.copy(status = status))
    }

    fun dismissTerminal(state: FilesUiState): FilesUiState =
        if (state.activeTask?.status?.isTerminal == true) state.copy(activeTask = null) else state

    fun changeSession(state: FilesUiState, sessionId: String?): FilesUiState =
        if (state.sessionId == sessionId) state else FilesUiState(sessionId = sessionId)
}

internal object FileConflictReducer {
    fun awaitDecision(state: FilesUiState, taskId: String, displayName: String): FilesUiState {
        val task = state.activeTask ?: return state
        if (task.taskId != taskId || task.status.isTerminal) return state
        return state.copy(
            activeTask = task.copy(
                status = FileTaskStatus.AwaitingConflict,
                conflictPolicy = FileConflictPolicy.CANCEL,
            ),
            pendingConflict = PendingFileConflict(taskId, displayName),
        )
    }

    fun resolve(
        state: FilesUiState,
        taskId: String,
        decision: FileConflictPolicy?,
    ): FilesUiState {
        val task = state.activeTask ?: return state
        val pending = state.pendingConflict ?: return state
        if (task.taskId != taskId || pending.taskId != taskId || task.status != FileTaskStatus.AwaitingConflict) {
            return state
        }
        val selected = decision ?: FileConflictPolicy.CANCEL
        val status = if (selected == FileConflictPolicy.CANCEL) {
            FileTaskStatus.Cancelled
        } else {
            FileTaskStatus.Preparing
        }
        return state.copy(
            activeTask = task.copy(status = status, conflictPolicy = selected),
            pendingConflict = null,
        )
    }
}
