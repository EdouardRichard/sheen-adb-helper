package com.sheen.adb.feature.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalModifier
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.SheenColors
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V1SharedStringKey
import com.sheen.adb.ui.V1SharedStrings

@Composable
fun ShellRoute(
    viewModel: ShellViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShellScreen(state = state, actions = viewModel, language = language)
}

@Composable
fun ShellScreen(
    state: ShellUiState,
    actions: ShellViewModel,
    language: UiLanguage,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val requestIme = {
        focusRequester.requestFocus()
        keyboardController?.show()
        Unit
    }

    ShellDialogs(state, actions, language)
    Column(Modifier.fillMaxSize()) {
        ShellUtilityBar(
            state = state,
            language = language,
            clear = actions::clear,
            filter = actions::updateFilter,
            setAutoScroll = actions::setAutoScroll,
        )
        ShellTerminalPanel(
            state = state,
            language = language,
            onDismissError = actions::dismissError,
            modifier = Modifier.weight(1f),
        )
        ShellCommandInput(
            state = state,
            language = language,
            focusRequester = focusRequester,
            onDraftChange = actions::updateCommand,
            onSubmit = actions::execute,
        )
        ShellKeyboardAccessory(
            state = state,
            language = language,
            onInput = actions::handleTerminalInput,
            onModifier = actions::toggleModifier,
            requestIme = requestIme,
        )
    }
}

@Composable
private fun ShellUtilityBar(
    state: ShellUiState,
    language: UiLanguage,
    clear: () -> Unit,
    filter: (String) -> Unit,
    setAutoScroll: (Boolean) -> Unit,
) {
    var filterVisible by remember { mutableStateOf(state.filter.isNotEmpty()) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UtilityButton(
                label = ShellStrings.text(language, ShellStringKey.CLEAR),
                description = ShellStrings.text(language, ShellStringKey.CLEAR_CONTENT_DESCRIPTION),
                onClick = clear,
            )
            UtilityButton(
                label = ShellStrings.text(language, ShellStringKey.FILTER),
                description = ShellStrings.text(language, ShellStringKey.FILTER_CONTENT_DESCRIPTION),
                icon = SheenIcons.Filter,
                onClick = { filterVisible = !filterVisible },
            )
            Spacer(Modifier.weight(1f))
            Text(
                ShellStrings.text(language, ShellStringKey.AUTO_SCROLL),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
            Switch(
                checked = state.autoScroll,
                onCheckedChange = setAutoScroll,
                modifier = Modifier.semantics {
                    contentDescription = ShellStrings.text(
                        language,
                        ShellStringKey.AUTO_SCROLL_CONTENT_DESCRIPTION,
                    )
                },
            )
        }
        if (filterVisible) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = filter,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(ShellStrings.text(language, ShellStringKey.FILTER)) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun UtilityButton(
    label: String,
    description: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SheenShapes.default,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(label, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
        }
    }
}

@Composable
private fun ShellTerminalPanel(
    state: ShellUiState,
    language: UiLanguage,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    val visible = state.visibleRecords
    val listState = rememberLazyListState()
    var scrollState by remember {
        mutableStateOf(ShellScrollState(autoScroll = state.autoScroll))
    }
    LaunchedEffect(state.outputGeneration, state.autoScroll, state.filter, visible.size) {
        val decision = if (scrollState.autoScroll != state.autoScroll) {
            ShellAutoScrollPolicy.setEnabled(scrollState, state.autoScroll, visible.size)
        } else {
            ShellAutoScrollPolicy.onOutputChanged(
                previous = scrollState,
                itemCount = visible.size,
                outputGeneration = state.outputGeneration,
            )
        }
        scrollState = decision.state
        decision.targetIndex?.let { listState.scrollToItem(it) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(SheenColors.terminalBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                count = visible.size,
                key = { index ->
                    val record = visible[index]
                    "${record.streamGeneration}:${record.kind}:$index"
                },
            ) { index ->
                val record = visible[index]
                if (record.kind == ShellTerminalRecordKind.SESSION_SEPARATOR) {
                    Text(
                        ShellStrings.text(language, ShellStringKey.SESSION_SEPARATOR),
                        color = MaterialTheme.colorScheme.secondary,
                        style = terminalTextStyle,
                    )
                } else {
                    Text(
                        safeOutput(record.text),
                        color = Color(0xFFE5E7EB),
                        style = terminalTextStyle,
                    )
                }
            }
        }
        ShellStateOverlay(state, language, onDismissError, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ShellStateOverlay(
    state: ShellUiState,
    language: UiLanguage,
    onDismissError: () -> Unit,
    modifier: Modifier,
) {
    val visualState = shellVisualState(state)
    if (visualState == ShellVisualState.Content) return
    val key = when (visualState) {
        ShellVisualState.Loading,
        ShellVisualState.Progress,
        -> ShellStringKey.STREAM_OPENING
        ShellVisualState.Empty -> ShellStringKey.STREAM_ACTIVE
        ShellVisualState.Error -> ShellStringKey.STREAM_PROTOCOL_FAILURE
        ShellVisualState.Cancelled -> ShellStringKey.STREAM_CANCELLED
        ShellVisualState.Disconnected -> ShellStringKey.STREAM_DISCONNECTED
        ShellVisualState.Unsupported -> ShellStringKey.STREAM_UNSUPPORTED
        ShellVisualState.OutcomeUnknown -> ShellStringKey.STREAM_OUTCOME_UNKNOWN
        ShellVisualState.Confirmation -> ShellStringKey.RISK_CONFIRMATION_TITLE
        ShellVisualState.Content -> ShellStringKey.STREAM_ACTIVE
    }
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = SheenShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            if (visualState == ShellVisualState.Loading ||
                visualState == ShellVisualState.Progress
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            Text(ShellStrings.text(language, key))
            state.error?.technicalCode?.let { code ->
                Text(
                    safeCommand(code),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ShellCommandInput(
    state: ShellUiState,
    language: UiLanguage,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(SheenColors.terminalBackground)
            .focusRequester(focusRequester),
        placeholder = {
            Text(ShellStrings.text(language, ShellStringKey.COMMAND_INPUT))
        },
        enabled = state.isConnected && state.terminal.phase != ShellTerminalPhase.REMOTE_ACTIVE,
        singleLine = true,
        textStyle = terminalTextStyle.copy(color = Color(0xFFE5E7EB)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (state.draft.isNotBlank()) onSubmit() }),
    )
}

@Composable
private fun ShellKeyboardAccessory(
    state: ShellUiState,
    language: UiLanguage,
    onInput: (TerminalInput) -> Unit,
    onModifier: (TerminalModifier) -> Unit,
    requestIme: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccessoryKey(
            label = "Esc",
            description = ShellStrings.text(language, ShellStringKey.ESC_CONTENT_DESCRIPTION),
            onClick = { onInput(TerminalInput.Escape) },
        )
        AccessoryKey(
            label = "Tab",
            description = ShellStrings.text(language, ShellStringKey.TAB_CONTENT_DESCRIPTION),
            onClick = { onInput(TerminalInput.Tab) },
        )
        AccessoryKey(
            label = "Ctrl",
            selected = state.terminal.modifier == TerminalModifier.CTRL ||
                state.terminal.modifier == TerminalModifier.CTRL_ALT,
            description = ShellStrings.text(language, ShellStringKey.CTRL_CONTENT_DESCRIPTION),
            onClick = { onModifier(TerminalModifier.CTRL) },
        )
        AccessoryKey(
            label = "Alt",
            selected = state.terminal.modifier == TerminalModifier.ALT ||
                state.terminal.modifier == TerminalModifier.CTRL_ALT,
            description = ShellStrings.text(language, ShellStringKey.ALT_CONTENT_DESCRIPTION),
            onClick = { onModifier(TerminalModifier.ALT) },
        )
        AccessoryKey(
            icon = SheenIcons.ArrowUp,
            description = ShellStrings.text(language, ShellStringKey.ARROW_UP_CONTENT_DESCRIPTION),
            onClick = { onInput(TerminalInput.ArrowUp) },
        )
        AccessoryKey(
            icon = SheenIcons.ArrowDown,
            description = ShellStrings.text(language, ShellStringKey.ARROW_DOWN_CONTENT_DESCRIPTION),
            onClick = { onInput(TerminalInput.ArrowDown) },
        )
        Spacer(Modifier.weight(1f))
        AccessoryKey(
            icon = SheenIcons.KeyboardReturn,
            primary = true,
            description = ShellStrings.text(
                language,
                ShellStringKey.KEYBOARD_RETURN_CONTENT_DESCRIPTION,
            ),
            onClick = requestIme,
        )
    }
}

@Composable
private fun AccessoryKey(
    description: String,
    onClick: () -> Unit,
    label: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    primary: Boolean = false,
) {
    Box(
        Modifier
            .width(44.dp)
            .height(SheenDimensions.minimumTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .width(44.dp)
                .height(36.dp)
                .semantics { contentDescription = description },
            shape = SheenShapes.default,
            color = when {
                primary -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = when {
                primary -> MaterialTheme.colorScheme.onPrimary
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            border = if (primary || selected) null else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        label.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellDialogs(
    state: ShellUiState,
    actions: ShellViewModel,
    language: UiLanguage,
) {
    state.pendingHostWrapper?.let { plan ->
        AlertDialog(
            onDismissRequest = actions::dismissHostWrapper,
            title = { Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_TITLE)) },
            text = {
                Text(
                    safeCommand(plan.remoteCommand ?: plan.originalCommand),
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                if (plan.remoteCommand != null) {
                    TextButton(onClick = actions::confirmHostWrapper) {
                        Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_SEND))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = actions::executeHostWrapperExactly) {
                    Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_CANCEL))
                }
            },
        )
    }
    state.pendingRiskExecution?.let { pending ->
        AlertDialog(
            onDismissRequest = actions::dismissRisk,
            title = { Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_TITLE)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_BODY))
                    Text(safeCommand(pending.displayedCommand), fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = actions::confirmRisk) {
                    Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_SEND))
                }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissRisk) {
                    Text(ShellStrings.text(language, ShellStringKey.RISK_CONFIRMATION_CANCEL))
                }
            },
        )
    }
}

private enum class ShellVisualState {
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

private fun shellVisualState(state: ShellUiState): ShellVisualState = when {
    !state.isConnected || state.streamStatus == ShellStreamStatus.DISCONNECTED ->
        ShellVisualState.Disconnected
    state.pendingRiskExecution != null || state.pendingHostWrapper != null ->
        ShellVisualState.Confirmation
    state.streamStatus == ShellStreamStatus.OPENING -> ShellVisualState.Loading
    state.streamStatus == ShellStreamStatus.CANCELLED -> ShellVisualState.Cancelled
    state.streamStatus == ShellStreamStatus.UNSUPPORTED -> ShellVisualState.Unsupported
    state.streamStatus == ShellStreamStatus.OUTCOME_UNKNOWN -> ShellVisualState.OutcomeUnknown
    state.streamStatus == ShellStreamStatus.ERROR || state.streamStatus == ShellStreamStatus.TIMED_OUT ->
        ShellVisualState.Error
    state.terminal.phase == ShellTerminalPhase.REMOTE_ACTIVE && state.records.isEmpty() ->
        ShellVisualState.Progress
    state.visibleRecords.isEmpty() -> ShellVisualState.Empty
    else -> ShellVisualState.Content
}

private val terminalTextStyle =
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)

private fun safeCommand(raw: String): String =
    SafeVerbatimText.render(
        raw = raw,
        policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 128),
    ).display

private fun safeOutput(raw: String): String =
    SafeVerbatimText.render(
        raw = raw,
        policy = SafeVerbatimPolicy.MultiLine(maxCodePoints = 16_384),
    ).display
