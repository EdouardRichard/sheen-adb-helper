package com.sheen.adb.feature.logcat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V1SharedStringKey
import com.sheen.adb.ui.V1SharedStrings

@Composable
fun LogcatRoute(
    viewModel: LogcatViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LogcatScreen(state = state, actions = viewModel, language = language)
}

@Composable
fun LogcatScreen(
    state: LogcatUiState,
    actions: LogcatViewModel,
    language: UiLanguage,
) {
    val openOutputDirectory =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            actions.onSaveTreeSelected(uri?.toString())
        }

    Column(Modifier.fillMaxSize()) {
        LogcatUtilityBar(
            state = state,
            language = language,
            onTextFilterChanged = actions::updateTextFilter,
            onLevelChanged = actions::setDisplayLevel,
            onClear = actions::clear,
            onDownload = {
                if (actions.prepareSaveSelection()) {
                    openOutputDirectory.launch(null)
                }
            },
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showSecondaryPane = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                LogcatContent(
                    state = state,
                    language = language,
                    onStart = actions::start,
                    onStop = actions::stop,
                    onDismissError = actions::dismissError,
                    modifier = Modifier.weight(1f),
                )
                if (showSecondaryPane) {
                    ProcessSecondaryPane(
                        state = state,
                        language = language,
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogcatUtilityBar(
    state: LogcatUiState,
    language: UiLanguage,
    onTextFilterChanged: (String) -> Unit,
    onLevelChanged: (LogcatDisplayLevel) -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.subtleOutlineAlpha,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = state.textFilter,
            onValueChange = onTextFilterChanged,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .widthIn(min = 128.dp),
            placeholder = {
                Text(
                    LogcatStrings.text(language, LogcatStringKey.FILTER),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingIcon = {
                Icon(SheenIcons.Filter, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            textStyle = TextStyle(
                color = LocalContentColor.current,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LocalContentColor.current,
                unfocusedTextColor = LocalContentColor.current,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            shape = SheenShapes.default,
        )
        Row(
            modifier = Modifier
                .weight(2f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fixedLabels = listOf("all", "debug", "info", "error")
            LogcatDisplayLevel.entries.zip(fixedLabels).forEach { (level, label) ->
                LevelButton(
                    label = label,
                    selected = state.displayLevel == level,
                    onClick = { onLevelChanged(level) },
                )
            }
            IconButton(
                onClick = onClear,
                enabled = state.rawWindowSnapshot.isNotEmpty(),
                modifier = Modifier
                    .size(SheenDimensions.minimumTouchTarget)
                    .semantics {
                        contentDescription = LogcatStrings.text(
                            language,
                            LogcatStringKey.CLEAR_CONTENT_DESCRIPTION,
                        )
                    },
            ) {
                Icon(SheenIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onDownload,
                enabled = state.rawWindowSnapshot.isNotEmpty() && !state.isSaveWriting,
                modifier = Modifier
                    .size(SheenDimensions.minimumTouchTarget)
                    .semantics {
                        contentDescription = LogcatStrings.text(
                            language,
                            LogcatStringKey.DOWNLOAD_CONTENT_DESCRIPTION,
                        )
                    },
            ) {
                Icon(SheenIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LevelButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = SheenDimensions.minimumTouchTarget),
        shape = SheenShapes.default,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = SheenTonalLayers.subtleOutlineAlpha,
            ),
        ),
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun LogcatContent(
    state: LogcatUiState,
    language: UiLanguage,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    when (val visualState = logcatVisualState(state)) {
        LogcatVisualState.Content -> LogSurface(state, modifier)
        else -> LogcatStatePanel(
            visualState = visualState,
            state = state,
            language = language,
            onStart = onStart,
            onStop = onStop,
            onDismissError = onDismissError,
            modifier = modifier,
        )
    }
}

@Composable
private fun LogSurface(
    state: LogcatUiState,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    LaunchedEffect(state.visibleLines.size, state.windowId) {
        if (state.visibleLines.isNotEmpty()) {
            listState.scrollToItem(state.visibleLines.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = state.visibleLines,
            key = { index, _ -> "${state.windowId}:$index" },
        ) { _, rawLine ->
            val display = SafeVerbatimText.render(
                raw = rawLine,
                policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 4_096),
            ).display
            Text(
                text = display,
                modifier = Modifier.horizontalScroll(horizontalScrollState),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun LogcatStatePanel(
    visualState: LogcatVisualState,
    state: LogcatUiState,
    language: UiLanguage,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = LogcatStrings.text(
                    language,
                    LogcatStringKey.LIVE_REGION_STATUS,
                )
            },
        ) {
            if (state.error != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismissError) {
                        Icon(
                            SheenIcons.Close,
                            contentDescription = V1SharedStrings.text(
                                language,
                                V1SharedStringKey.ACTION_CLOSE,
                            ),
                        )
                    }
                }
            }
            if (visualState == LogcatVisualState.Loading ||
                visualState == LogcatVisualState.Progress
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            Text(
                text = visualState.message(language),
                color = when (visualState) {
                    LogcatVisualState.Error,
                    LogcatVisualState.Unsupported,
                    LogcatVisualState.OutcomeUnknown,
                    -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            state.error?.technicalCode?.let { technicalCode ->
                Text(
                    text = LogcatStrings.resolve(
                        language,
                        LogcatStrings.verbatimTechnicalCode(technicalCode),
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                state.isCapturing -> {
                    TextButton(onClick = onStop) {
                        Text(LogcatStrings.text(language, LogcatStringKey.STOP))
                    }
                }
                state.isConnected && visualState != LogcatVisualState.Progress -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.semantics {
                            contentDescription = LogcatStrings.text(
                                language,
                                LogcatStringKey.START_CONTENT_DESCRIPTION,
                            )
                        },
                    ) {
                        Text(LogcatStrings.text(language, LogcatStringKey.START))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessSecondaryPane(
    state: LogcatUiState,
    language: UiLanguage,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (language == UiLanguage.ZH_CN) "进程上下文" else "Process context",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (state.processGeneration == 0L) {
                    if (language == UiLanguage.ZH_CN) "尚未加载" else "Not loaded"
                } else {
                    if (language == UiLanguage.ZH_CN) "当前采集窗口的进程快照已加载" else
                        "Process snapshot loaded for this collection window"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            state.processDegradedReason?.let { reason ->
                Text(
                    text = SafeVerbatimText.render(
                        reason,
                        SafeVerbatimPolicy.SingleLine(160),
                    ).display,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private sealed interface LogcatVisualState {
    data object NeverStarted : LogcatVisualState
    data object Loading : LogcatVisualState
    data object Content : LogcatVisualState
    data object Empty : LogcatVisualState
    data object Error : LogcatVisualState
    data object Cancelled : LogcatVisualState
    data object Disconnected : LogcatVisualState
    data object Unsupported : LogcatVisualState
    data object OutcomeUnknown : LogcatVisualState
    data object Progress : LogcatVisualState
    data object LimitTime : LogcatVisualState
    data object LimitBytes : LogcatVisualState
    data object Stopped : LogcatVisualState
}

private fun logcatVisualState(state: LogcatUiState): LogcatVisualState = when {
    !state.isConnected -> LogcatVisualState.Disconnected
    state.isSaveWriting -> LogcatVisualState.Progress
    state.visibleLines.isNotEmpty() -> LogcatVisualState.Content
    state.status == LogcatAnalysisStatus.NEVER_STARTED ||
        state.status == LogcatAnalysisStatus.READY -> LogcatVisualState.NeverStarted
    state.status == LogcatAnalysisStatus.STARTING ||
        state.status == LogcatAnalysisStatus.LOADING_PROCESSES -> LogcatVisualState.Loading
    state.status == LogcatAnalysisStatus.ERROR -> LogcatVisualState.Error
    state.status == LogcatAnalysisStatus.CANCELLED -> LogcatVisualState.Cancelled
    state.status == LogcatAnalysisStatus.UNSUPPORTED -> LogcatVisualState.Unsupported
    state.status == LogcatAnalysisStatus.OUTCOME_UNKNOWN -> LogcatVisualState.OutcomeUnknown
    state.status == LogcatAnalysisStatus.LIMIT_TIME -> LogcatVisualState.LimitTime
    state.status == LogcatAnalysisStatus.LIMIT_BYTES -> LogcatVisualState.LimitBytes
    state.status == LogcatAnalysisStatus.STOPPED -> LogcatVisualState.Stopped
    else -> LogcatVisualState.Empty
}

private fun LogcatVisualState.message(language: UiLanguage): String {
    val key = when (this) {
        LogcatVisualState.NeverStarted -> LogcatStringKey.NEVER_STARTED
        LogcatVisualState.Loading -> LogcatStringKey.STARTING
        LogcatVisualState.Content -> LogcatStringKey.COLLECTING
        LogcatVisualState.Empty -> LogcatStringKey.COLLECTING
        LogcatVisualState.Error -> LogcatStringKey.ERROR
        LogcatVisualState.Cancelled -> LogcatStringKey.CANCELLED
        LogcatVisualState.Disconnected -> LogcatStringKey.DISCONNECTED
        LogcatVisualState.Unsupported -> LogcatStringKey.UNSUPPORTED
        LogcatVisualState.OutcomeUnknown -> LogcatStringKey.OUTCOME_UNKNOWN
        LogcatVisualState.Progress -> LogcatStringKey.SAVE_WRITING
        LogcatVisualState.LimitTime -> LogcatStringKey.LIMIT_TIME
        LogcatVisualState.LimitBytes -> LogcatStringKey.LIMIT_BYTES
        LogcatVisualState.Stopped -> LogcatStringKey.STOPPED
    }
    return LogcatStrings.text(language, key)
}
