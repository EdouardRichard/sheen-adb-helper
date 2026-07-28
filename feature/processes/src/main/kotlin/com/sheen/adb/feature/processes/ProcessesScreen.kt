package com.sheen.adb.feature.processes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.ProcessFieldState
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessTerminationOutcome
import com.sheen.adb.core.ProcessTerminationScope
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.ui.UiLanguage
import java.util.Locale

@Composable
fun ProcessesRoute(
    viewModel: ProcessesViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProcessesScreen(state, viewModel, language)
}

@Composable
fun ProcessesScreen(
    state: ProcessesUiState,
    actions: ProcessesViewModel,
    language: UiLanguage,
) {
    state.pendingTermination?.let { pending ->
        TerminationDialog(
            pending = pending,
            language = language,
            onSelectScope = actions::selectTerminationScope,
            onConfirm = { actions.confirmTermination(pending.nonce) },
            onDismiss = actions::cancelTermination,
        )
    }

    Column(Modifier.fillMaxSize()) {
        SearchSection(
            query = state.query,
            language = language,
            onQueryChanged = actions::updateQuery,
        )
        state.terminationResult?.let { result ->
            TerminationResultBanner(result.outcome, language)
        }
        when (val visualState = processesVisualState(state)) {
            ProcessesVisualState.Content -> ProcessList(state, language, actions::requestTermination)
            else -> ProcessesStatePanel(
                visualState = visualState,
                state = state,
                language = language,
                onDismissError = actions::dismissError,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TerminationResultBanner(
    outcome: ProcessTerminationOutcome,
    language: UiLanguage,
) {
    val key = when (outcome) {
        ProcessTerminationOutcome.TERMINATED,
        ProcessTerminationOutcome.ALREADY_EXITED,
        -> ProcessesStringKey.TERMINATION_SUCCEEDED
        ProcessTerminationOutcome.POLICY_REJECTED -> ProcessesStringKey.TERMINATION_REJECTED
        ProcessTerminationOutcome.TIMED_OUT -> ProcessesStringKey.TERMINATION_TIMED_OUT
        ProcessTerminationOutcome.CANCELLED -> ProcessesStringKey.TERMINATION_CANCELLED
        else -> ProcessesStringKey.TERMINATION_FAILED
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = ProcessesStrings.text(
                    language,
                    ProcessesStringKey.RESULT_LIVE_ANNOUNCEMENT,
                )
            },
        shape = SheenShapes.large,
        color = if (key == ProcessesStringKey.TERMINATION_SUCCEEDED) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Text(
            ProcessesStrings.text(language, key),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SearchSection(
    query: String,
    language: UiLanguage,
    onQueryChanged: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface, SheenShapes.large)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = SheenTonalLayers.subtleOutlineAlpha,
                    ),
                    SheenShapes.large,
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(SheenIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { input ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    ProcessesStrings.text(language, ProcessesStringKey.SEARCH_PLACEHOLDER),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            input()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProcessList(
    state: ProcessesUiState,
    language: UiLanguage,
    onEndProcess: (ProcessSnapshotEntry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(
            items = state.visibleEntries,
            key = { entry ->
                val identity = entry.identity
                "${identity.sessionId}:${identity.observedGeneration}:${identity.pid}:${identity.startTimeTicks}"
            },
        ) { entry ->
            ProcessRow(entry, language, onEndProcess)
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun ProcessRow(
    entry: ProcessSnapshotEntry,
    language: UiLanguage,
    onEndProcess: (ProcessSnapshotEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = ProcessesStrings.text(
                    language,
                    ProcessesStringKey.PROCESS_ROW_CONTENT_DESCRIPTION,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = safeProcessName(entry.processName),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip(
                    icon = SheenIcons.Cpu,
                    label = ProcessesStrings.text(language, ProcessesStringKey.CPU_LABEL),
                    value = entry.cpuPercent.metricText(entry.cpuState, "%", language),
                    description = ProcessesStrings.text(language, ProcessesStringKey.CPU_CONTENT_DESCRIPTION),
                )
                MetricChip(
                    icon = SheenIcons.Memory,
                    label = ProcessesStrings.text(language, ProcessesStringKey.MEMORY_LABEL),
                    value = entry.pssMiB.metricText(entry.pssState, " MiB", language),
                    description = ProcessesStrings.text(language, ProcessesStringKey.MEMORY_CONTENT_DESCRIPTION),
                )
            }
        }
        IconButton(
            onClick = { onEndProcess(entry) },
            enabled = entry.identity.sessionId.isNotBlank(),
            modifier = Modifier
                .size(SheenDimensions.minimumTouchTarget)
                .semantics {
                    contentDescription = ProcessesStrings.text(
                        language,
                        ProcessesStringKey.END_PROCESS_CONTENT_DESCRIPTION,
                    )
                },
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = SheenShapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(SheenIcons.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = "$description $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProcessesStatePanel(
    visualState: ProcessesVisualState,
    state: ProcessesUiState,
    language: UiLanguage,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    val key = when (visualState) {
        ProcessesVisualState.Loading -> ProcessesStringKey.LOADING
        ProcessesVisualState.Empty -> ProcessesStringKey.EMPTY
        ProcessesVisualState.Error -> ProcessesStringKey.ERROR
        ProcessesVisualState.Cancelled -> ProcessesStringKey.CANCELLED
        ProcessesVisualState.Disconnected -> ProcessesStringKey.DISCONNECTED
        ProcessesVisualState.Unsupported -> ProcessesStringKey.UNSUPPORTED
        ProcessesVisualState.OutcomeUnknown -> ProcessesStringKey.OUTCOME_UNKNOWN
        ProcessesVisualState.Confirmation -> ProcessesStringKey.CHOOSE_END_SCOPE
        ProcessesVisualState.Progress -> ProcessesStringKey.LOADING
        ProcessesVisualState.Content -> ProcessesStringKey.TITLE
    }
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = SheenShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .2f)),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .semantics {
                        if (visualState == ProcessesVisualState.OutcomeUnknown ||
                            visualState == ProcessesVisualState.Error
                        ) {
                            liveRegion = LiveRegionMode.Polite
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onDismissError) {
                            Icon(
                                SheenIcons.Close,
                                contentDescription = ProcessesStrings.text(
                                    language,
                                    ProcessesStringKey.TERMINATION_CANCELLED,
                                ),
                            )
                        }
                    }
                }
                if (visualState == ProcessesVisualState.Loading ||
                    visualState == ProcessesVisualState.Progress
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                Text(ProcessesStrings.text(language, key))
                state.error?.technicalCode?.let { code ->
                    Text(
                        safeTechnicalCode(code),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminationDialog(
    pending: ProcessTerminationConfirmation,
    language: UiLanguage,
    onSelectScope: (ProcessTerminationScope) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val identity = pending.entry.identity
    val safeName = safeProcessName(identity.processName)
    val safePid = safePid(identity.pid)
    if (pending.scope == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(ProcessesStrings.text(language, ProcessesStringKey.CHOOSE_END_SCOPE))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(safeName, fontFamily = FontFamily.Monospace)
                    Text("PID $safePid", fontFamily = FontFamily.Monospace)
                    if (pending.entry.applicationPackage == null) {
                        Text(
                            ProcessesStrings.text(
                                language,
                                ProcessesStringKey.APPLICATION_SCOPE_UNAVAILABLE,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = { onSelectScope(ProcessTerminationScope.SINGLE_PROCESS) },
                    ) {
                        Text(ProcessesStrings.text(language, ProcessesStringKey.END_SINGLE_PROCESS))
                    }
                    if (ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP in
                        ProcessesPolicy.terminationScopes(pending.entry)
                    ) {
                        TextButton(
                            onClick = {
                                onSelectScope(ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP)
                            },
                        ) {
                            Text(ProcessesStrings.text(language, ProcessesStringKey.END_APPLICATION))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(ProcessesStrings.text(language, ProcessesStringKey.TERMINATION_CANCELLED))
                }
            },
        )
    } else {
        val confirmationKey = if (pending.scope == ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP) {
            ProcessesStringKey.CONFIRM_END_APPLICATION
        } else {
            ProcessesStringKey.CONFIRM_END_SINGLE
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(ProcessesStrings.text(language, confirmationKey)) },
            text = {
                Text("$safeName · PID $safePid", fontFamily = FontFamily.Monospace)
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(ProcessesStrings.text(language, ProcessesStringKey.END_PROCESS))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(ProcessesStrings.text(language, ProcessesStringKey.TERMINATION_CANCELLED))
                }
            },
        )
    }
}

private enum class ProcessesVisualState {
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
}

private fun processesVisualState(state: ProcessesUiState): ProcessesVisualState = when {
    !state.isConnected -> ProcessesVisualState.Disconnected
    state.terminationInProgress -> ProcessesVisualState.Progress
    state.pendingTermination != null -> ProcessesVisualState.Confirmation
    state.terminationResult?.outcome == ProcessTerminationOutcome.UNKNOWN ->
        ProcessesVisualState.OutcomeUnknown
    state.status == ProcessesAnalysisStatus.UNSUPPORTED -> ProcessesVisualState.Unsupported
    state.status == ProcessesAnalysisStatus.CANCELLED -> ProcessesVisualState.Cancelled
    state.status == ProcessesAnalysisStatus.ERROR -> ProcessesVisualState.Error
    state.isLoading && state.entries.isEmpty() -> ProcessesVisualState.Loading
    state.visibleEntries.isEmpty() -> ProcessesVisualState.Empty
    else -> ProcessesVisualState.Content
}

private fun Double?.metricText(
    fieldState: ProcessFieldState,
    suffix: String,
    language: UiLanguage,
): String = if (fieldState == ProcessFieldState.AVAILABLE && this != null) {
    String.format(Locale.ROOT, "%.1f%s", this, suffix)
} else {
    ProcessesStrings.text(language, ProcessesStringKey.UNKNOWN_METRIC)
}

private fun safeProcessName(raw: String): String =
    SafeVerbatimText.render(raw, SafeVerbatimPolicy.SingleLine(96)).display

private fun safePid(pid: Int): String =
    SafeVerbatimText.render(pid.toString(), SafeVerbatimPolicy.SingleLine(24)).display

private fun safeTechnicalCode(raw: String): String =
    SafeVerbatimText.render(raw, SafeVerbatimPolicy.SingleLine(64)).display
