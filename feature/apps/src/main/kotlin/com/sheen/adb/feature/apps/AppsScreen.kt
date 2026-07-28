package com.sheen.adb.feature.apps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.ApplicationClassification
import com.sheen.adb.core.ApplicationUninstallPreparation
import com.sheen.adb.core.ApkInstallMode
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.data.SafComponentOutputStore
import com.sheen.adb.data.SafDocumentStore
import com.sheen.adb.data.SafStoreResult
import java.util.concurrent.atomic.AtomicBoolean

private const val DESIGN_SEARCH_PLACEHOLDER_ZH = "请输入应用名或包名。"

@Composable
fun AppsRoute(
    viewModel: AppsViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingUninstall by viewModel.pendingUninstall.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val documentStore = remember(context) { SafDocumentStore(context) }
    val pickerLifecycleLease = remember { AtomicBoolean(false) }
    val extractionDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            viewModel.dismissTaskResult()
        } else {
            viewModel.deliverExtraction(
                destinationTreeId = uri.toString(),
                outputs = SafComponentOutputStore(documentStore),
            )
        }
    }
    val installSource = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            viewModel.dismissTaskResult()
        } else {
            when (val source = documentStore.openSource(uri.toString())) {
                is SafStoreResult.Success -> viewModel.installSelectedApk(
                    source = source.value,
                    mode = ApkInstallMode.STANDARD,
                    expectedPackageName = null,
                )
                is SafStoreResult.Failure -> viewModel.dismissTaskResult()
            }
        }
    }
    LaunchedEffect(state.task?.taskId) {
        val pickerTask = state.task?.takeIf { it.phase == AppsTaskPhase.PickerOpen } ?: return@LaunchedEffect
        pickerLifecycleLease.set(true)
        when (pickerTask.kind) {
            AppsTaskKind.APK_EXTRACTION -> extractionDestination.launch(null)
            AppsTaskKind.APK_INSTALLATION -> installSource.launch(
                arrayOf("application/vnd.android.package-archive"),
            )
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.setForeground(true)
                    pickerLifecycleLease.set(false)
                }
                Lifecycle.Event.ON_STOP ->
                    if (!pickerLifecycleLease.get()) viewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        viewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setForeground(false)
        }
    }
    AppsScreen(
        state = state,
        language = language,
        pendingUninstall = pendingUninstall,
        onDraftQuery = viewModel::updateDraftQuery,
        onSearch = viewModel::applySearch,
        onRefresh = viewModel::refresh,
        onExtract = viewModel::beginApkExtraction,
        onForceStop = viewModel::requestForceStop,
        onDisable = viewModel::requestDisable,
        onEnable = viewModel::requestEnable,
        onUninstall = viewModel::requestUninstall,
        onInstall = viewModel::beginApkInstallation,
        onConfirmMutation = viewModel::confirmPending,
        onDismissMutation = viewModel::dismissConfirmation,
        onConfirmUninstall = viewModel::confirmUninstall,
        onDismissUninstall = viewModel::cancelPendingConfirmation,
        onConfirmForceInstall = viewModel::confirmForceInstall,
        onDismissError = viewModel::dismissError,
        onDismissTask = viewModel::dismissTaskResult,
    )
}

@Composable
fun AppsScreen(
    state: AppsUiState,
    language: UiLanguage,
    pendingUninstall: ApplicationUninstallPreparation?,
    onDraftQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onExtract: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onInstall: () -> Unit,
    onConfirmMutation: () -> Unit,
    onDismissMutation: () -> Unit,
    onConfirmUninstall: () -> Unit,
    onDismissUninstall: () -> Unit,
    onConfirmForceInstall: () -> Unit,
    onDismissError: () -> Unit,
    onDismissTask: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val pageMargin = if (maxWidth >= 600.dp) {
            SheenDimensions.expandedPageHorizontalMargin
        } else {
            SheenDimensions.compactPageHorizontalMargin
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = pageMargin),
            ) {
                SearchBar(
                    value = state.draftQuery,
                    language = language,
                    enabled = state.task == null && state.activeOperation == null,
                    onValueChange = onDraftQuery,
                    onSearch = onSearch,
                )
                ApplicationsContent(
                    state = state,
                    language = language,
                    onRefresh = onRefresh,
                    onExtract = onExtract,
                    onForceStop = onForceStop,
                    onDisable = onDisable,
                    onEnable = onEnable,
                    onUninstall = onUninstall,
                    onDismissError = onDismissError,
                    modifier = Modifier.weight(1f),
                )
            }
            FloatingActionButton(
                onClick = onInstall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = pageMargin, bottom = 16.dp)
                    .size(56.dp)
                    .semantics {
                        contentDescription = AppsStrings.text(language, AppsStringKey.INSTALL_APK)
                    },
                shape = SheenShapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(SheenIcons.UploadToDevice, contentDescription = null)
            }
        }
    }

    state.pendingConfirmation?.let {
        MutationConfirmationDialog(
            confirmation = it,
            language = language,
            onConfirm = onConfirmMutation,
            onDismiss = onDismissMutation,
        )
    }
    pendingUninstall?.let {
        UninstallConfirmationDialog(
            preparation = it,
            language = language,
            onConfirm = onConfirmUninstall,
            onDismiss = onDismissUninstall,
        )
    }
    state.task?.let {
        AppsTaskDialog(it, language, onConfirmForceInstall, onDismissTask)
    }
}

@Composable
private fun SearchBar(
    value: String,
    language: UiLanguage,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, SheenShapes.extraLarge)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.subtleOutlineAlpha,
                ),
                SheenShapes.extraLarge,
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(SheenDimensions.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeholder = if (language == UiLanguage.ZH_CN) {
            DESIGN_SEARCH_PLACEHOLDER_ZH
        } else {
            AppsStrings.text(language, AppsStringKey.SEARCH_PLACEHOLDER)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch()
                focusManager.clearFocus()
            }),
        )
        IconButton(
            onClick = onSearch,
            enabled = enabled,
            modifier = Modifier
                .size(SheenDimensions.minimumTouchTarget)
                .background(MaterialTheme.colorScheme.secondaryContainer, SheenShapes.large)
                .semantics {
                    contentDescription = AppsStrings.text(language, AppsStringKey.SEARCH_PLACEHOLDER)
                },
        ) {
            Icon(SheenIcons.Search, contentDescription = null)
        }
    }
}

@Composable
private fun ApplicationsContent(
    state: AppsUiState,
    language: UiLanguage,
    onRefresh: () -> Unit,
    onExtract: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    val visualState = appsVisualState(state)
    if (visualState != AppsVisualState.Content) {
        AppsStatePanel(visualState, state, language, onRefresh, onDismissError, modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = state.visibleApplications,
            key = { application -> "${state.sessionId}:${application.userId}:${application.packageName}" },
        ) { application ->
            ApplicationCard(
                application = application,
                displayName = state.displayNameByPackage[application.packageName]
                    ?.takeIf(String::isNotBlank)
                    ?: AppsStrings.text(language, AppsStringKey.UNKNOWN_APP_NAME),
                language = language,
                enabled = !state.isBusy,
                onExtract = { onExtract(application.packageName) },
                onForceStop = { onForceStop(application.packageName) },
                onDisable = { onDisable(application.packageName) },
                onEnable = { onEnable(application.packageName) },
                onUninstall = { onUninstall(application.packageName) },
            )
        }
    }
}

@Composable
private fun ApplicationCard(
    application: RemoteApplication,
    displayName: String,
    language: UiLanguage,
    enabled: Boolean,
    onExtract: () -> Unit,
    onForceStop: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onUninstall: () -> Unit,
) {
    val rowActions = when (application.classification) {
        ApplicationClassification.ORDINARY -> listOf(
            AppsRowAction.EXTRACT_APK,
            AppsRowAction.SET_ENABLED,
            AppsRowAction.FORCE_STOP,
            AppsRowAction.UNINSTALL,
        )
        ApplicationClassification.SYSTEM,
        ApplicationClassification.UNKNOWN,
        -> listOf(AppsRowAction.EXTRACT_APK)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                .5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.quietOutlineAlpha,
                ),
                SheenShapes.extraLarge,
            ),
        shape = SheenShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(application.packageName.let(::safePackageName),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(displayName.let(::safeApplicationName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowActions.forEach { action ->
                    when (action) {
                        AppsRowAction.EXTRACT_APK -> AppActionButton(
                            icon = SheenIcons.Download,
                            description = AppsStrings.text(language, AppsStringKey.EXTRACT_APK),
                            enabled = enabled,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onExtract,
                        )
                        AppsRowAction.SET_ENABLED -> AppActionButton(
                            icon = if (application.enabledState == RemoteApplicationEnabledState.DISABLED) {
                                SheenIcons.Enable
                            } else {
                                SheenIcons.Disable
                            },
                            description = AppsStrings.text(
                                language,
                                if (application.enabledState == RemoteApplicationEnabledState.DISABLED) {
                                    AppsStringKey.ENABLE_APP
                                } else {
                                    AppsStringKey.DISABLE_APP
                                },
                            ),
                            enabled = enabled &&
                                application.enabledState != RemoteApplicationEnabledState.UNKNOWN,
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = if (application.enabledState == RemoteApplicationEnabledState.DISABLED) {
                                onEnable
                            } else {
                                onDisable
                            },
                        )
                        AppsRowAction.FORCE_STOP -> AppActionButton(
                            icon = SheenIcons.ForceStop,
                            description = AppsStrings.text(language, AppsStringKey.FORCE_STOP_APP),
                            enabled = enabled,
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = onForceStop,
                        )
                        AppsRowAction.UNINSTALL -> AppActionButton(
                            icon = SheenIcons.Uninstall,
                            description = AppsStrings.text(language, AppsStringKey.UNINSTALL_APP),
                            enabled = enabled,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onUninstall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(SheenDimensions.minimumTouchTarget)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun AppsStatePanel(
    visualState: AppsVisualState,
    state: AppsUiState,
    language: UiLanguage,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    val key = when (visualState) {
        AppsVisualState.Disconnected -> AppsStringKey.ERROR
        AppsVisualState.Unsupported -> AppsStringKey.UNSUPPORTED
        AppsVisualState.OutcomeUnknown -> AppsStringKey.OUTCOME_UNKNOWN
        AppsVisualState.Cancelled -> AppsStringKey.CANCELLED
        AppsVisualState.PartialSuccess -> AppsStringKey.PARTIAL_SUCCESS
        AppsVisualState.Empty -> AppsStringKey.NONE_COMMITTED
        AppsVisualState.Initial,
        AppsVisualState.Loading,
        AppsVisualState.Progress,
        -> AppsStringKey.PROGRESS
        AppsVisualState.Error -> AppsStringKey.ERROR
        AppsVisualState.Confirmation -> AppsStringKey.CONFIRM
        AppsVisualState.Content -> AppsStringKey.TITLE
    }
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = SheenShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .2f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onDismissError) {
                            Icon(SheenIcons.Close, contentDescription = AppsStrings.text(language, AppsStringKey.CANCEL))
                        }
                    }
                }
                if (visualState == AppsVisualState.Loading || visualState == AppsVisualState.Progress) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                Text(AppsStrings.text(language, key))
                state.error?.technicalCode?.let {
                    Text(
                        AppsStrings.safeSingleLine(it, 64),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (visualState == AppsVisualState.Error ||
                    visualState == AppsVisualState.Unsupported ||
                    visualState == AppsVisualState.Cancelled
                ) {
                    TextButton(onClick = onRefresh) {
                        Text(AppsStrings.text(language, AppsStringKey.RETRY))
                    }
                }
            }
        }
    }
}

@Composable
private fun MutationConfirmationDialog(
    confirmation: AppsConfirmation,
    language: UiLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                AppsStrings.text(
                    language,
                    if (confirmation.operation == AppsOperation.FORCE_STOP) {
                        AppsStringKey.FORCE_STOP_CONFIRMATION
                    } else {
                        AppsStringKey.CONFIRM
                    },
                ),
            )
        },
        text = { Text(safePackageName(confirmation.packageName), fontFamily = FontFamily.Monospace) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(AppsStrings.text(language, AppsStringKey.CONFIRM)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppsStrings.text(language, AppsStringKey.CANCEL)) }
        },
    )
}

@Composable
private fun UninstallConfirmationDialog(
    preparation: ApplicationUninstallPreparation,
    language: UiLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppsStrings.text(language, AppsStringKey.UNINSTALL_CONFIRMATION)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(safePackageName(preparation.packageName), fontFamily = FontFamily.Monospace)
                Text(AppsStrings.text(language, AppsStringKey.UNINSTALL_PRIVATE_DATA_WARNING))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(AppsStrings.text(language, AppsStringKey.UNINSTALL_APP))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppsStrings.text(language, AppsStringKey.CANCEL)) }
        },
    )
}

@Composable
private fun AppsTaskDialog(
    task: AppsTaskState,
    language: UiLanguage,
    onConfirmForceInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val key = when (val terminal = task.terminal) {
        is AppsTaskTerminal.Extraction -> when (terminal.result) {
            is AppsComponentResult.CompleteSuccess -> AppsStringKey.COMPLETE_SUCCESS
            is AppsComponentResult.PartialSuccess -> AppsStringKey.PARTIAL_SUCCESS
            is AppsComponentResult.NoneCommittedFailure -> AppsStringKey.NONE_COMMITTED
        }
        is AppsTaskTerminal.OldPackageRemovedNoRollback,
        is AppsTaskTerminal.OutcomeUnknown,
        is AppsTaskTerminal.CleanupUncertain,
        -> AppsStringKey.OUTCOME_UNKNOWN
        AppsTaskTerminal.Cancelled -> AppsStringKey.CANCELLED
        AppsTaskTerminal.ForceInstallRequired -> AppsStringKey.FORCE_INSTALL_CONFIRMATION
        AppsTaskTerminal.Success -> AppsStringKey.INSTALL_SUCCESS
        null -> AppsStringKey.PROGRESS
    }
    AlertDialog(
        onDismissRequest = { if (!task.navigationLocked && task.terminal != null) onDismiss() },
        title = { Text(AppsStrings.text(language, key)) },
        text = {
            val terminal = task.terminal
            if (terminal == null) {
                CircularProgressIndicator()
            } else if (terminal is AppsTaskTerminal.OutcomeUnknown) {
                Text(
                    AppsStrings.safeSingleLine(terminal.technicalCode, 64),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            if (task.terminal == AppsTaskTerminal.ForceInstallRequired) {
                TextButton(onClick = onConfirmForceInstall) {
                    Text(AppsStrings.text(language, AppsStringKey.FORCE_INSTALL))
                }
            } else if (!task.navigationLocked && task.terminal != null) {
                TextButton(onClick = onDismiss) {
                    Text(AppsStrings.text(language, AppsStringKey.CONFIRM))
                }
            }
        },
        dismissButton = {
            if (task.terminal == AppsTaskTerminal.ForceInstallRequired) {
                TextButton(onClick = onDismiss) {
                    Text(AppsStrings.text(language, AppsStringKey.CANCEL))
                }
            }
        },
    )
}

private fun appsVisualState(state: AppsUiState): AppsVisualState = when {
    !state.isConnected -> AppsVisualState.Disconnected
    state.isLoading -> AppsVisualState.Loading
    state.error?.technicalCode?.contains("UNSUPPORTED") == true -> AppsVisualState.Unsupported
    state.error != null -> AppsVisualState.Error
    state.operationNotice?.outcomeUnknown == true -> AppsVisualState.OutcomeUnknown
    state.task?.terminal is AppsTaskTerminal.Cancelled -> AppsVisualState.Cancelled
    state.task?.componentResult is AppsComponentResult.PartialSuccess -> AppsVisualState.PartialSuccess
    state.task != null && state.task.terminal == null -> AppsVisualState.Progress
    state.pendingConfirmation != null -> AppsVisualState.Confirmation
    state.applications.isEmpty() || state.visibleApplications.isEmpty() -> AppsVisualState.Empty
    else -> AppsVisualState.Content
}

private enum class AppsVisualState {
    Initial,
    Loading,
    Content,
    Empty,
    Error,
    Cancelled,
    Disconnected,
    Unsupported,
    OutcomeUnknown,
    Confirmation,
    Progress,
    PartialSuccess,
}

private fun safePackageName(raw: String): String =
    SafeVerbatimText.render(raw, SafeVerbatimPolicy.SingleLine(96)).display

private fun safeApplicationName(raw: String): String =
    SafeVerbatimText.render(raw, SafeVerbatimPolicy.SingleLine(64)).display
