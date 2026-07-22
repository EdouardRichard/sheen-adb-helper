package com.sheen.adb.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun FilesRoute(viewModel: FilesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uploadSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onUploadSourceSelected(uri?.toString())
    }
    val downloadTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onDownloadTreeSelected(uri?.toString())
    }
    LaunchedEffect(state.pickerRequest) {
        when (state.pickerRequest) {
            FilePickerRequest.UploadSource -> uploadSource.launch(arrayOf("*/*"))
            is FilePickerRequest.DownloadTarget -> downloadTarget.launch(null)
            null -> Unit
        }
    }
    FilesScreen(
        state = state,
        onSharedStorage = viewModel::openSharedStorage,
        onDeviceRoot = viewModel::openDeviceRoot,
        onRefresh = viewModel::refresh,
        onBreadcrumb = viewModel::openBreadcrumb,
        onEntry = viewModel::openEntry,
        onCancel = viewModel::cancelLoad,
        onUpload = viewModel::requestUpload,
        onDownload = viewModel::requestDownload,
        onResolveConflict = viewModel::resolveConflict,
        onCancelTask = viewModel::cancelActiveTask,
        onDismissTask = viewModel::dismissTask,
    )
}

@Composable
fun FilesScreen(
    state: FilesUiState,
    onSharedStorage: () -> Unit,
    onDeviceRoot: () -> Unit,
    onRefresh: () -> Unit,
    onBreadcrumb: (String) -> Unit,
    onEntry: (FileBrowserEntry) -> Unit,
    onCancel: () -> Unit,
    onUpload: () -> Unit = {},
    onDownload: () -> Unit = {},
    onResolveConflict: (FileConflictPolicy?) -> Unit = {},
    onCancelTask: () -> Unit = {},
    onDismissTask: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSharedStorage) { Text("共享存储") }
            OutlinedButton(onClick = onDeviceRoot) { Text("设备根目录") }
            OutlinedButton(onClick = onRefresh) { Text("刷新") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUpload, enabled = state.activeTask == null) { Text("上传文件") }
            Button(
                onClick = onDownload,
                enabled = state.activeTask == null && state.selectedPath != null,
            ) { Text("下载所选") }
        }
        FileTaskPanel(
            presentation = fileTaskPresentation(state),
            onCancel = onCancelTask,
            onDismiss = onDismissTask,
        )
        when (val browser = state.browser) {
            FilesBrowserState.Initial -> Text("正在准备文件浏览…")
            is FilesBrowserState.Loading -> {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "目录加载中" })
                TextButton(onClick = onCancel) { Text("取消") }
            }
            is FilesBrowserState.Content -> {
                Breadcrumbs(browser.breadcrumbs, onBreadcrumb)
                browser.entries.forEach { entry -> FileEntryRow(entry, onEntry) }
            }
            is FilesBrowserState.Empty -> {
                Breadcrumbs(browser.breadcrumbs, onBreadcrumb)
                Text("此目录为空")
            }
            is FilesBrowserState.Error -> {
                Text(browser.error.userMessage, color = MaterialTheme.colorScheme.error)
                Text(browser.error.nextStep)
                Button(onClick = onRefresh) { Text("重试") }
            }
            FilesBrowserState.Disconnected -> Text("设备已断开，请重新连接后浏览文件。")
            FilesBrowserState.Cancelled -> {
                Text("目录加载已取消")
                Button(onClick = onRefresh) { Text("重新加载") }
            }
        }
    }
    val task = fileTaskPresentation(state)
    if (task?.showConflict == true) {
        AlertDialog(
            onDismissRequest = { onResolveConflict(null) },
            title = { Text("目标已存在同名文件") },
            text = { Text("${task.conflictDisplayName.orEmpty()}：请选择处理方式。默认取消，不会覆盖原文件。") },
            confirmButton = {
                TextButton(onClick = { onResolveConflict(FileConflictPolicy.OVERWRITE) }) { Text("覆盖") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onResolveConflict(FileConflictPolicy.AUTO_RENAME) }) { Text("自动重命名") }
                    TextButton(onClick = { onResolveConflict(null) }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun FileTaskPanel(
    presentation: FileTaskPresentation?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (presentation == null) return
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(presentation.stageLabel, style = MaterialTheme.typography.titleMedium)
        val transferred = presentation.transferredBytes
        val total = presentation.totalBytes
        if (transferred != null && total != null && total > 0L) {
            LinearProgressIndicator(
                progress = { (transferred.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$transferred / $total B", style = MaterialTheme.typography.labelSmall)
        } else if (transferred != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("已传输 $transferred B", style = MaterialTheme.typography.labelSmall)
        }
        presentation.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        presentation.errorNextStep?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (presentation.canCancel) TextButton(onClick = onCancel) { Text("取消任务") }
            if (presentation.canDismiss) TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

@Composable
fun FileTaskSummaryBar(
    summary: FileTaskSummary?,
    showViewAction: Boolean,
    onView: () -> Unit,
    onCancel: () -> Unit,
) {
    if (summary == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (summary.kind) {
                FileTaskKind.UPLOAD -> "文件上传"
                FileTaskKind.DOWNLOAD -> "文件下载"
                FileTaskKind.APK_EXTRACTION -> "APK 提取"
            },
            modifier = Modifier.weight(1f),
        )
        if (showViewAction) TextButton(onClick = onView) { Text("查看") }
        if (!summary.status.isTerminal) TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun Breadcrumbs(items: List<com.sheen.adb.core.RemoteBreadcrumb>, onBreadcrumb: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        breadcrumbDisplaySegments(items).forEach { (segment, path) ->
            Text(
                text = segment,
                modifier = Modifier.clickable { onBreadcrumb(path) },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FileEntryRow(entry: FileBrowserEntry, onEntry: (FileBrowserEntry) -> Unit) {
    TextButton(
        onClick = { onEntry(entry) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "文件条目 ${entry.name}" },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(entry.name)
            entry.badge?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            entry.sizeBytes?.let { Text("$it B", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
