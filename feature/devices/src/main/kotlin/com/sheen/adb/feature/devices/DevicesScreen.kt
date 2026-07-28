package com.sheen.adb.feature.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.data.DeviceProfile
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings

@Composable
fun DevicesRoute(
    viewModel: DevicesViewModel,
    onOpenWirelessDebuggingSettings: () -> Unit = {},
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var localPairingPromptWirelessEnabled by remember { mutableStateOf<Boolean?>(null) }
    var openWirelessSettingsWhenPairingReady by remember { mutableStateOf(false) }
    val keepLocalPairingWhileOpeningSettings by rememberUpdatedState(
        state.keepLocalPairingWhileOpeningSettings,
    )
    DisposableEffect(lifecycle) {
        viewModel.onDiscoveryForeground()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (keepLocalPairingWhileOpeningSettings) {
                        viewModel.onLocalWirelessSettingsReturned()
                    }
                    viewModel.onDiscoveryForeground()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onDiscoveryBackground()
                    if (!keepLocalPairingWhileOpeningSettings) viewModel.closePairing()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onDiscoveryBackground()
            if (!keepLocalPairingWhileOpeningSettings) viewModel.closePairing()
        }
    }
    LaunchedEffect(
        state.keepLocalPairingWhileOpeningSettings,
        openWirelessSettingsWhenPairingReady,
    ) {
        if (
            openWirelessSettingsWhenPairingReady &&
            state.keepLocalPairingWhileOpeningSettings
        ) {
            openWirelessSettingsWhenPairingReady = false
            onOpenWirelessDebuggingSettings()
        }
    }
    DevicesScreen(
        state = state,
        pairingState = pairingState,
        discoveryState = discoveryState,
        actions = viewModel,
        language = language,
        onRequestLocalPairing = {
            localPairingPromptWirelessEnabled = context.isWirelessDebuggingEnabled()
        },
        onOpenWirelessDebuggingSettings = {
            viewModel.onLocalWirelessSettingsOpened()
            onOpenWirelessDebuggingSettings()
        },
    )
    localPairingPromptWirelessEnabled?.let { wirelessDebuggingEnabled ->
        AlertDialog(
            onDismissRequest = { localPairingPromptWirelessEnabled = null },
            title = { Text(localized(language, "本机配对", "Pair this device")) },
            text = {
                Text(
                    if (wirelessDebuggingEnabled) {
                        localized(
                            language,
                            "无线调试已打开。请打开“使用配对码配对设备”模式，随后在通知栏输入配对码。",
                            "Wireless debugging is enabled. Open “Pair device with pairing code”, then enter the code from the notification.",
                        )
                    } else {
                        localized(
                            language,
                            "未检测到无线调试。请先打开无线调试并打开“使用配对码配对设备”模式，随后在通知栏输入配对码。",
                            "Wireless debugging is not enabled. Enable it, open “Pair device with pairing code”, then enter the code from the notification.",
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        localPairingPromptWirelessEnabled = null
                        openWirelessSettingsWhenPairingReady = true
                        viewModel.enterLocalPairingMode(openingWirelessSettings = true)
                    },
                ) {
                    Text(localized(language, "确定", "OK"))
                }
            },
            dismissButton = {
                TextButton(onClick = { localPairingPromptWirelessEnabled = null }) {
                    Text(localized(language, "取消", "Cancel"))
                }
            },
        )
    }
}

@Composable
fun DevicesPairingOverlayRoute(
    viewModel: DevicesViewModel,
    onOpenWirelessDebuggingSettings: () -> Unit = {},
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    PairingCard(
        state = state,
        pairingState = pairingState,
        actions = viewModel,
        onOpenWirelessDebuggingSettings = {
            viewModel.onLocalWirelessSettingsOpened()
            onOpenWirelessDebuggingSettings()
        },
        language = language,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DevicesScreen(
    state: DevicesUiState,
    pairingState: DevicesPairingState,
    actions: DevicesViewModel,
    discoveryState: DevicesDiscoveryState = DevicesDiscoveryState(),
    onOpenWirelessDebuggingSettings: () -> Unit = {},
    onRequestLocalPairing: () -> Unit = actions::enterLocalPairingMode,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val context = LocalContext.current
    PullToRefreshBox(
        isRefreshing = discoveryState.phase == DevicesDiscoveryPhase.SCANNING,
        onRefresh = actions::onDiscoveryPullRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        DisconnectedContent(
            state = state,
            discoveryState = discoveryState,
            actions = actions,
            language = language,
            context = context,
            onRequestLocalPairing = onRequestLocalPairing,
        )
    }
    val pairingPresentation = pairingState.toPresentation(language)
    if (pairingPresentation.showSessionReplacementConfirmation) {
        AlertDialog(
            onDismissRequest = actions::dismissPairingSessionReplacement,
            title = { Text(localized(language, "断开当前设备？", "Disconnect the current device?")) },
            text = { Text(pairingPresentation.sessionReplacementText) },
            confirmButton = {
                TextButton(onClick = actions::confirmPairingSessionReplacement) { Text(localized(language, "断开并继续", "Disconnect and continue")) }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissPairingSessionReplacement) { Text(localized(language, "保留当前连接", "Keep current connection")) }
            },
        )
    }
    if (state.awaitingDiscoverySessionReplacement) {
        AlertDialog(
            onDismissRequest = actions::dismissDiscoverySessionReplacement,
            title = { Text(localized(language, "断开当前设备并连接发现目标？", "Disconnect and connect to the discovered target?")) },
            text = { Text(localized(language, "发现结果不会自动替换当前 ADB Session。确认后先断开当前设备，再重新验证并连接所选服务。", "Discovery does not replace the current ADB session automatically. Confirm to disconnect, revalidate, and connect.")) },
            confirmButton = {
                TextButton(onClick = actions::confirmDiscoverySessionReplacement) { Text(localized(language, "断开并连接", "Disconnect and connect")) }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDiscoverySessionReplacement) { Text(localized(language, "保留当前连接", "Keep current connection")) }
            },
        )
    }
}

@Composable
private fun DisconnectedContent(
    state: DevicesUiState,
    discoveryState: DevicesDiscoveryState,
    actions: DevicesViewModel,
    language: UiLanguage,
    context: Context,
    onRequestLocalPairing: () -> Unit,
) {
    val currentConnectionError = state.connectionState as? AdbConnectionState.Error
    val errorIdentity = currentConnectionError?.let { "${it.error.technicalCode}:${it.technicalDetails}" }
    var dismissedConnectionError by remember(errorIdentity) { mutableStateOf<String?>(null) }
    var showConnectionErrorDetails by remember(errorIdentity) { mutableStateOf(false) }
    val visibleInputError = ConnectionPagePresentation.visibleInputError(
        inputError = state.inputError,
        connection = state.connectionState,
    )
    val inputErrorIdentity = visibleInputError
    var dismissedInputError by remember(inputErrorIdentity) { mutableStateOf<String?>(null) }
    val semanticState = ConnectionPagePresentation.disconnectedContentState(
        connection = state.connectionState,
        discovery = discoveryState,
    )
    Column(
        Modifier.fillMaxSize().padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            visibleInputError
                ?.takeUnless { it == dismissedInputError }
                ?.let { error ->
                    DismissibleInputError(
                        error = error,
                        language = language,
                        onDismiss = { dismissedInputError = inputErrorIdentity },
                    )
                }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.connectionState.isBusy()) {
                OutlinedButton(onClick = actions::cancelCurrentOperation) {
                    Text(localized(language, "取消当前操作", "Cancel current operation"))
                }
            }
            currentConnectionError?.takeUnless { errorIdentity == dismissedConnectionError }?.let { error ->
                CompactConnectionError(
                    error = error,
                    language = language,
                    onShowDetails = { showConnectionErrorDetails = true },
                    onDismiss = {
                        showConnectionErrorDetails = false
                        dismissedConnectionError = errorIdentity
                    },
                )
            }
            Text(
                V01Strings.text(language, V01StringKey.PAIRING_SCAN),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when (semanticState) {
                DisconnectedPageContentState.Loading,
                DisconnectedPageContentState.Content,
                DisconnectedPageContentState.Empty,
                DisconnectedPageContentState.Error,
                DisconnectedPageContentState.Cancelled,
                DisconnectedPageContentState.Disconnected,
                DisconnectedPageContentState.Unsupported,
                -> DevicesDiscoveryPanel(discoveryState, actions, language)
            }
        }
        PairingActionButtons(actions, language, onRequestLocalPairing)
    }
    if (showConnectionErrorDetails && currentConnectionError != null) {
        AlertDialog(
            onDismissRequest = { showConnectionErrorDetails = false },
            title = { Text(localized(language, "错误详情", "Error details")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        SafeVerbatimText.render(
                            raw = currentConnectionError.error.technicalCode,
                            policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 64),
                        ).display,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        SafeVerbatimText.render(
                            raw = currentConnectionError.technicalDetails,
                            policy = SafeVerbatimPolicy.MultiLine(maxCodePoints = 2_048),
                        ).display,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copy(
                            context,
                            localized(language, "Sheen ADB 脱敏技术详情", "Sheen ADB redacted technical details"),
                            currentConnectionError.technicalDetails,
                        )
                    },
                ) {
                    Text(localized(language, "复制", "Copy"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionErrorDetails = false }) {
                    Text(localized(language, "关闭", "Close"))
                }
            },
        )
    }
}

@Composable
private fun DismissibleInputError(
    error: String,
    language: UiLanguage,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, SheenShapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = SheenTonalLayers.emphasisOutlineAlpha),
                SheenShapes.large,
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = SafeVerbatimText.render(
                raw = error,
                policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 64),
            ).display,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = SheenIcons.Close,
                contentDescription = DevicesStrings.text(
                    language,
                    DevicesStringKey.DISMISS_ERROR_CONTENT_DESCRIPTION,
                ),
            )
        }
    }
}

@Composable
private fun CompactConnectionError(
    error: AdbConnectionState.Error,
    language: UiLanguage,
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, SheenShapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = SheenTonalLayers.emphasisOutlineAlpha),
                SheenShapes.large,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            DevicesStrings.text(language, DevicesStringKey.CONNECTION_ERROR),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            SafeVerbatimText.render(
                raw = error.error.technicalCode,
                policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 64),
            ).display,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        TextButton(onClick = onShowDetails) {
            Text(localized(language, "详情", "Details"))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = SheenIcons.Close,
                contentDescription = DevicesStrings.text(
                    language,
                    DevicesStringKey.DISMISS_ERROR_CONTENT_DESCRIPTION,
                ),
            )
        }
    }
}

@Composable
private fun PairingActionButtons(
    actions: DevicesViewModel,
    language: UiLanguage,
    onRequestLocalPairing: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DesignActionButton(
            icon = SheenIcons.QrCodeScanner,
            label = V01Strings.text(language, V01StringKey.PAIRING_QR),
            onClick = { actions.beginPairingFromConnectionPage(PairingMethod.QR) },
        )
        DesignActionButton(
            icon = SheenIcons.PairingCode,
            label = V01Strings.text(language, V01StringKey.PAIRING_CODE),
            onClick = { actions.beginPairingFromConnectionPage(PairingMethod.SIX_DIGIT_CODE) },
        )
        DesignActionButton(
            icon = SheenIcons.CellTower,
            label = V01Strings.text(language, V01StringKey.PAIRING_LOCAL),
            onClick = onRequestLocalPairing,
        )
    }
}

@Composable
private fun DesignActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, SheenShapes.extraLarge)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.subtleOutlineAlpha,
                ),
                SheenShapes.extraLarge,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            label,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PairingCard(
    state: DevicesUiState,
    pairingState: DevicesPairingState,
    actions: DevicesViewModel,
    onOpenWirelessDebuggingSettings: () -> Unit,
    language: UiLanguage,
) {
    val presentation = pairingState.toPresentation(language)
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (pairingState.isLocalMode) localized(language, "本机无线调试配对", "Pair this device wirelessly") else presentation.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (pairingState.isLocalMode) {
                    localized(language, "先打开系统无线调试，再选择“使用配对码配对设备”。应用会自动查找本机配对端口。", "Open Wireless debugging, choose Pair device with pairing code, and keep the system dialog open while the app finds the local pairing port.")
                } else {
                    presentation.guidance
                },
            )
            if (!pairingState.isLocalMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presentation.methodOptions.forEach { method ->
                        val label = when (method) {
                            PairingMethod.QR -> localized(language, "二维码", "QR code")
                            PairingMethod.SIX_DIGIT_CODE -> localized(language, "6 位配对码", "6-digit code")
                            PairingMethod.NONE -> return@forEach
                        }
                        if (pairingState.method == method) {
                            Button(
                                onClick = { actions.switchPairingMethod(method) },
                                enabled = false,
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { actions.switchPairingMethod(method) },
                            ) { Text(label) }
                        }
                    }
                }
            }
            Text(
                presentation.statusText,
                color = MaterialTheme.colorScheme.primary,
            )
            if (pairingState.isLocalMode) {
                Text(pairingState.localNotificationText(language))
                if (pairingState.suggestNativeNotificationStyle) {
                    Text(
                        localized(language, "当前系统通知样式可能不支持通知栏内输入。请改用系统原生通知样式，或直接在应用内输入配对码。", "The current notification style may not allow inline input. Use the system notification style or enter the code in the app."),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onOpenWirelessDebuggingSettings) { Text(localized(language, "打开系统无线调试设置", "Open Wireless debugging settings")) }
            }
            if (presentation.showQrMatrix) {
                pairingState.qrMatrix?.let {
                    QrMatrixImage(it, Modifier.align(Alignment.CenterHorizontally))
                }
                TextButton(
                    onClick = {
                        actions.switchPairingMethod(PairingMethod.SIX_DIGIT_CODE)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(localized(language, "切换配对码配对", "Switch to pairing code"))
                }
            }
            if (presentation.showCodeInputs) {
                if (!pairingState.isLocalMode) {
                    OutlinedTextField(
                        state.pairingEndpointInput,
                        actions::updatePairingEndpoint,
                        label = { Text(localized(language, "IP:配对端口", "IP:pairing port")) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    state.pairingCode,
                    actions::updatePairingCode,
                    label = { Text(localized(language, "6 位配对码", "6-digit pairing code")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (presentation.showStart) {
                    Button(onClick = actions::startSelectedPairing) { Text(localized(language, "开始配对", "Start pairing")) }
                }
                if (presentation.showCodeInputs) {
                    Button(onClick = actions::pair, enabled = presentation.submitCodeEnabled) { Text(localized(language, "提交配对码", "Submit code")) }
                }
                if (presentation.showCancel) {
                    OutlinedButton(onClick = actions::onPairingPageLeft) {
                        Text(
                            if (pairingState.isLocalMode) {
                                localized(language, "停止本机配对", "Stop local pairing")
                            } else {
                                localized(language, "取消", "Cancel")
                            },
                        )
                    }
                }
                if (presentation.showRetry) {
                    Button(
                        onClick = if (pairingState.isLocalMode) {
                            actions::retryLocalPairingMode
                        } else {
                            actions::retryPairing
                        },
                    ) { Text(localized(language, "重新开始", "Start again")) }
                }
                if (presentation.showCodeFallback) {
                    Button(
                        onClick = {
                            actions.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
                            actions.startSelectedPairing()
                        },
                    ) { Text(localized(language, "改用 6 位配对码", "Use a 6-digit code")) }
                }
            }
        }
    }
}

private fun Context.isWirelessDebuggingEnabled(): Boolean =
    Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1

private fun DevicesPairingState.localNotificationText(language: UiLanguage): String = when (localNotificationState) {
    LocalPairingNotificationState.HIDDEN -> localized(language, "应用内配对码输入始终可用；通知栏输入正在准备", "In-app code entry remains available while notification input is prepared.")
    LocalPairingNotificationState.PRIVATE_LOCKED -> localized(language, "设备已锁定；通知不会显示或接收配对码，解锁后可继续", "The device is locked; unlock it before entering a code from the notification.")
    LocalPairingNotificationState.INPUT_READY -> localized(language, "也可直接在通知栏输入 6 位配对码", "You can also enter the 6-digit code from the notification.")
    LocalPairingNotificationState.INPUT_UNAVAILABLE -> localized(language, "通知栏输入不可用，请在应用内输入配对码", "Notification input is unavailable; enter the code in the app.")
    LocalPairingNotificationState.RESULT -> localized(language, "通知栏配对操作已结束", "The notification pairing action has finished.")
}

@Composable
private fun QrMatrixImage(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .size(256.dp)
            .background(Color.White)
            .clearAndSetSemantics { },
    ) {
        val moduleSize = size.minDimension / matrix.size
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * moduleSize, y * moduleSize),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private fun AdbConnectionState.isBusy(): Boolean = this is AdbConnectionState.Connecting ||
    this is AdbConnectionState.Pairing || this is AdbConnectionState.Disconnecting ||
    this is AdbConnectionState.AwaitingAuthorization

private fun localized(language: UiLanguage, zh: String, en: String): String =
    if (language == UiLanguage.ZH_CN) zh else en

private fun copy(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
