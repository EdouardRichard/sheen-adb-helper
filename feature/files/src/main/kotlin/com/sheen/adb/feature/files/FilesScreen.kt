package com.sheen.adb.feature.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.RemoteBreadcrumb
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V1SharedStringKey
import com.sheen.adb.ui.V1SharedStrings
import java.util.Locale

@Composable
fun FilesRoute(
    viewModel: FilesViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
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
        language = language,
        onRefresh = viewModel::refresh,
        onBreadcrumb = viewModel::openBreadcrumb,
        onEntry = viewModel::openEntry,
        onCancel = viewModel::cancelLoad,
        onUpload = viewModel::requestUpload,
        onDownload = viewModel::requestDownload,
        onResolveConflict = viewModel::resolveConflict,
        onCancelTask = viewModel::cancelActiveTask,
        onDismissError = viewModel::dismissError,
        onDismissTask = viewModel::dismissTask,
    )
}

@Composable
fun FilesScreen(
    state: FilesUiState,
    language: UiLanguage,
    onRefresh: () -> Unit,
    onBreadcrumb: (String) -> Unit,
    onEntry: (FileBrowserEntry) -> Unit,
    onCancel: () -> Unit,
    onUpload: () -> Unit = {},
    onDownload: () -> Unit = {},
    onResolveConflict: (FileConflictPolicy?) -> Unit = {},
    onCancelTask: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onDismissTask: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            FilesPathBar(
                breadcrumbs = state.browser.breadcrumbsOrEmpty(),
                language = language,
                onBreadcrumb = onBreadcrumb,
            )
            FilesBrowserContent(
                state = state,
                language = language,
                onRefresh = onRefresh,
                onEntry = onEntry,
                onDownload = onDownload,
                onCancel = onCancel,
                onDismissError = onDismissError,
                modifier = Modifier.weight(1f),
            )
        }

        FloatingActionButton(
            onClick = onUpload,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(56.dp)
                .semantics {
                    contentDescription = FilesStrings.text(language, FilesStringKey.UPLOAD_FILE)
                },
            shape = SheenShapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(SheenIcons.UploadToDevice, contentDescription = null)
        }
    }

    FileTaskOverlay(
        state = state,
        language = language,
        onCancel = onCancelTask,
        onDismiss = onDismissTask,
    )
    val task = state.activeTask
    if (task?.status == FileTaskStatus.AwaitingConflict && state.pendingConflict != null) {
        FileConflictDialog(
            displayName = state.pendingConflict.displayName,
            language = language,
            onResolve = onResolveConflict,
        )
    }
}

@Composable
private fun FilesPathBar(
    breadcrumbs: List<RemoteBreadcrumb>,
    language: UiLanguage,
    onBreadcrumb: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .2f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SheenIcons.Folder,
            contentDescription = FilesStrings.text(language, FilesStringKey.PATH),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val segments = breadcrumbDisplaySegments(
                breadcrumbs.ifEmpty { listOf(RemoteBreadcrumb("/", "/")) },
            )
            segments.forEachIndexed { index, (_, path) ->
                val rawLabel = breadcrumbs.getOrNull(index)?.label ?: "/"
                TextButton(
                    onClick = { onBreadcrumb(path) },
                    modifier = Modifier.heightIn(min = SheenDimensions.minimumTouchTarget),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    Text(
                        text = safePathSegment(rawLabel),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (index == segments.lastIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (index != segments.lastIndex) {
                    Icon(
                        SheenIcons.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesBrowserContent(
    state: FilesUiState,
    language: UiLanguage,
    onRefresh: () -> Unit,
    onEntry: (FileBrowserEntry) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val browser = state.browser) {
        FilesBrowserState.Initial -> FilesStatePanel(
            icon = SheenIcons.Folder,
            message = FilesStrings.text(language, FilesStringKey.LOADING),
            loading = true,
            modifier = modifier,
        )
        is FilesBrowserState.Loading -> FilesStatePanel(
            icon = SheenIcons.Refresh,
            message = FilesStrings.text(language, FilesStringKey.LOADING),
            loading = true,
            action = FilesStrings.text(language, FilesStringKey.CANCEL_TASK) to onCancel,
            modifier = modifier,
        )
        is FilesBrowserState.Content -> {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = browser.entries,
                    key = { entry -> "${state.sessionId}:${entry.absolutePath}" },
                ) { entry ->
                    FileEntryRow(
                        entry = entry,
                        language = language,
                        onTrailingAction = {
                            onEntry(entry)
                            if (!entry.enterable) onDownload()
                        },
                    )
                }
            }
        }
        is FilesBrowserState.Empty -> FilesStatePanel(
            icon = SheenIcons.Folder,
            message = FilesStrings.text(language, FilesStringKey.EMPTY),
            modifier = modifier,
        )
        is FilesBrowserState.Error -> {
            val visualState = when (
                FilesStrings.errorKey(browser.error.category, browser.error.technicalCode)
            ) {
                FilesStringKey.UNSUPPORTED -> FilesVisualState.Unsupported
                FilesStringKey.OUTCOME_UNKNOWN -> FilesVisualState.OutcomeUnknown
                else -> FilesVisualState.Error
            }
            FilesStatePanel(
                icon = SheenIcons.File,
                message = FilesStrings.text(language, visualState.stringKey),
                technicalCode = browser.error.technicalCode,
                action = FilesStrings.text(language, FilesStringKey.RETRY) to onRefresh,
                closeAction = onDismissError,
                closeDescription = V1SharedStrings.text(language, V1SharedStringKey.ACTION_CLOSE),
                modifier = modifier,
            )
        }
        FilesBrowserState.Disconnected -> FilesStatePanel(
            icon = SheenIcons.File,
            message = FilesStrings.text(language, FilesStringKey.DISCONNECTED),
            modifier = modifier,
        )
        FilesBrowserState.Cancelled -> FilesStatePanel(
            icon = SheenIcons.File,
            message = FilesStrings.text(language, FilesStringKey.CANCELLED),
            action = FilesStrings.text(language, FilesStringKey.RETRY) to onRefresh,
            modifier = modifier,
        )
    }
}

@Composable
private fun FileEntryRow(
    entry: FileBrowserEntry,
    language: UiLanguage,
    onTrailingAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .2f),
                SheenShapes.large,
            ),
        shape = SheenShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, SheenShapes.default),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (entry.enterable) SheenIcons.Folder else SheenIcons.File,
                    contentDescription = null,
                    tint = if (entry.enterable) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = safeFileName(entry.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (entry.enterable) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.sizeBytes?.let {
                        Text(
                            text = formatBytes(it),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entry.mode?.let {
                        Text(
                            text = "0${it.toString(8)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entry.badge?.let {
                        Text(
                            text = FilesStrings.safeSingleLine(it, maxCodePoints = 40),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            IconButton(
                onClick = onTrailingAction,
                modifier = Modifier
                    .size(SheenDimensions.minimumTouchTarget)
                    .semantics {
                        contentDescription = FilesStrings.text(
                            language,
                            if (entry.enterable) {
                                FilesStringKey.ENTER_FOLDER
                            } else {
                                FilesStringKey.DOWNLOAD_FILE
                            },
                        )
                    },
            ) {
                Icon(
                    imageVector = if (entry.enterable) SheenIcons.NavigateNext else SheenIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilesStatePanel(
    icon: ImageVector,
    message: String,
    modifier: Modifier,
    loading: Boolean = false,
    technicalCode: String? = null,
    action: Pair<String, () -> Unit>? = null,
    closeAction: (() -> Unit)? = null,
    closeDescription: String = "",
) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = SheenShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .2f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                closeAction?.let { close ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = close) {
                            Icon(SheenIcons.Close, contentDescription = closeDescription)
                        }
                    }
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Text(message, color = MaterialTheme.colorScheme.onSurface)
                technicalCode?.let {
                    Text(
                        FilesStrings.safeSingleLine(it, maxCodePoints = 64),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action?.let { (label, callback) ->
                    TextButton(onClick = callback) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun FileTaskOverlay(
    state: FilesUiState,
    language: UiLanguage,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val task = state.activeTask ?: return
    if (task.status == FileTaskStatus.AwaitingConflict) return
    AlertDialog(
        onDismissRequest = {
            if (task.status.isTerminal) onDismiss()
        },
        title = {
            Text(
                FilesStrings.text(
                    language,
                    FilesStrings.taskTitleKey(task.status),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val transfer = task.status as? FileTaskStatus.Transferring
                if (transfer != null) {
                    LinearProgressIndicator(
                        progress = {
                            val total = transfer.totalBytes
                            if (total == null || total == 0L) 0f
                            else (transfer.transferredBytes.toFloat() / total).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${formatBytes(transfer.transferredBytes)} / " +
                            (transfer.totalBytes?.let(::formatBytes) ?: "—"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                val error = when (val status = task.status) {
                    is FileTaskStatus.Failed -> status.error
                    is FileTaskStatus.CleanupFailed -> status.error
                    else -> null
                }
                error?.let {
                    Text(
                        FilesStrings.text(language, FilesStrings.errorKey(it.category, it.technicalCode)),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        FilesStrings.safeSingleLine(it.technicalCode, maxCodePoints = 64),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            if (task.status.isTerminal) {
                TextButton(onClick = onDismiss) {
                    Text(FilesStrings.text(language, FilesStringKey.CLOSE))
                }
            }
        },
        dismissButton = {
            if (!task.status.isTerminal) {
                TextButton(onClick = onCancel) {
                    Text(FilesStrings.text(language, FilesStringKey.CANCEL_TASK))
                }
            }
        },
    )
}

@Composable
private fun FileConflictDialog(
    displayName: String,
    language: UiLanguage,
    onResolve: (FileConflictPolicy?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onResolve(null) },
        title = { Text(FilesStrings.text(language, FilesStringKey.CONFIRMATION)) },
        text = {
            Text(
                FilesStrings.safeSingleLine(displayName, maxCodePoints = 120),
                fontFamily = FontFamily.Monospace,
            )
        },
        confirmButton = {
            TextButton(onClick = { onResolve(FileConflictPolicy.OVERWRITE) }) {
                Text(if (language == UiLanguage.ZH_CN) "覆盖" else "Overwrite")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResolve(FileConflictPolicy.AUTO_RENAME) }) {
                    Text(if (language == UiLanguage.ZH_CN) "自动重命名" else "Auto rename")
                }
                TextButton(onClick = { onResolve(null) }) {
                    Text(if (language == UiLanguage.ZH_CN) "取消" else "Cancel")
                }
            }
        },
    )
}

@Composable
fun FileTaskSummaryBar(
    summary: FileTaskSummary?,
    showViewAction: Boolean,
    onView: () -> Unit,
    onCancel: () -> Unit,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    if (summary == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            FilesStrings.text(language, FilesStringKey.PROGRESS),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showViewAction) {
            TextButton(onClick = onView) {
                Text(if (language == UiLanguage.ZH_CN) "查看" else "View")
            }
        }
        if (!summary.status.isTerminal) {
            TextButton(onClick = onCancel) {
                Text(FilesStrings.text(language, FilesStringKey.CANCEL_TASK))
            }
        }
    }
}

private enum class FilesVisualState(val stringKey: FilesStringKey) {
    Error(FilesStringKey.ERROR),
    Unsupported(FilesStringKey.UNSUPPORTED),
    OutcomeUnknown(FilesStringKey.OUTCOME_UNKNOWN),
    Confirmation(FilesStringKey.CONFIRMATION),
    Progress(FilesStringKey.PROGRESS),
}

private fun FilesBrowserState.breadcrumbsOrEmpty(): List<RemoteBreadcrumb> = when (this) {
    is FilesBrowserState.Content -> breadcrumbs
    is FilesBrowserState.Empty -> breadcrumbs
    else -> emptyList()
}

private fun safePathSegment(raw: String): String =
    SafeVerbatimText.render(
        raw,
        SafeVerbatimPolicy.SingleLine(maxCodePoints = 80),
    ).display

private fun safeFileName(raw: String): String =
    SafeVerbatimText.render(
        raw,
        SafeVerbatimPolicy.SingleLine(maxCodePoints = 160),
    ).display

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L ->
        String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L ->
        String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L ->
        String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
