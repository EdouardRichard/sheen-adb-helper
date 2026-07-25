package com.sheen.adb.feature.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings

@Composable
fun DevicesRoute(
    viewModel: DevicesViewModel,
    onOpenWirelessDebuggingSettings: () -> Unit = {},
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var openingWirelessDebuggingSettings by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        viewModel.onDiscoveryForeground()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    openingWirelessDebuggingSettings = false
                    viewModel.onDiscoveryForeground()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onDiscoveryBackground()
                    if (!openingWirelessDebuggingSettings) viewModel.closePairing()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onDiscoveryBackground()
            if (!openingWirelessDebuggingSettings) viewModel.closePairing()
        }
    }
    DevicesScreen(
        state = state,
        pairingState = pairingState,
        discoveryState = discoveryState,
        actions = viewModel,
        language = language,
        onOpenWirelessDebuggingSettings = {
            openingWirelessDebuggingSettings = true
            viewModel.onLocalWirelessSettingsOpened()
            onOpenWirelessDebuggingSettings()
        },
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
            pairingState = pairingState,
            discoveryState = discoveryState,
            actions = actions,
            language = language,
            context = context,
            onOpenWirelessDebuggingSettings = onOpenWirelessDebuggingSettings,
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
    pairingState: DevicesPairingState,
    discoveryState: DevicesDiscoveryState,
    actions: DevicesViewModel,
    language: UiLanguage,
    context: Context,
    onOpenWirelessDebuggingSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.connectionState.isBusy()) {
                OutlinedButton(onClick = actions::cancelCurrentOperation) {
                    Text(localized(language, "取消当前操作", "Cancel current operation"))
                }
            }
            (state.connectionState as? AdbConnectionState.Error)?.let { error ->
                CompactConnectionError(error, context, language)
            }
            Text(
                V01Strings.text(language, V01StringKey.PAIRING_SCAN),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DevicesDiscoveryPanel(discoveryState, actions, language)
            if (pairingState.method != PairingMethod.NONE || pairingState.isLocalMode) {
                PairingCard(state, pairingState, actions, onOpenWirelessDebuggingSettings, language)
            }
        }
        PairingActionButtons(actions, language)
    }
}

@Composable
private fun CompactConnectionError(
    error: AdbConnectionState.Error,
    context: Context,
    language: UiLanguage,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, SheenShapes.large)
            .border(1.dp, MaterialTheme.colorScheme.error, SheenShapes.large)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            error.error.userMessage,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        TextButton(onClick = { copy(context, "Sheen ADB 脱敏技术详情", error.technicalDetails) }) {
            Text(localized(language, "详情", "Details"))
        }
    }
}

@Composable
private fun PairingActionButtons(
    actions: DevicesViewModel,
    language: UiLanguage,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DesignActionButton(
            icon = SheenIcons.QrCodeScanner,
            label = V01Strings.text(language, V01StringKey.PAIRING_QR),
            onClick = {
                actions.selectPairingMethod(PairingMethod.QR)
                actions.startSelectedPairing()
            },
        )
        DesignActionButton(
            icon = SheenIcons.PairingCode,
            label = V01Strings.text(language, V01StringKey.PAIRING_CODE),
            onClick = {
                actions.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
                actions.startSelectedPairing()
            },
        )
        DesignActionButton(
            icon = SheenIcons.CellTower,
            label = V01Strings.text(language, V01StringKey.PAIRING_LOCAL),
            onClick = actions::enterLocalPairingMode,
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SheenShapes.extraLarge)
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
                                onClick = { actions.selectPairingMethod(method) },
                                enabled = !presentation.showCancel,
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { actions.selectPairingMethod(method) },
                                enabled = !presentation.showCancel,
                            ) { Text(label) }
                        }
                    }
                }
            }
            Text(
                if (pairingState.isLocalMode) pairingState.localStatusText(language) else presentation.statusText,
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
                    OutlinedButton(onClick = actions::onPairingPageLeft) { Text(localized(language, "取消", "Cancel")) }
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

private fun DevicesPairingState.localStatusText(language: UiLanguage): String = when (localDiscoveryStatus) {
    LocalPairingDiscoveryStatus.IDLE -> localized(language, "等待开始本机端口扫描", "Waiting to scan local pairing ports")
    LocalPairingDiscoveryStatus.SEARCHING -> localized(language, "正在扫描本机无线调试配对端口", "Scanning local wireless debugging pairing ports")
    LocalPairingDiscoveryStatus.FOUND -> localized(language, "已发现本机配对端口，请输入系统显示的 6 位配对码", "Pairing port found. Enter the 6-digit code shown by the system.")
    LocalPairingDiscoveryStatus.NOT_FOUND -> localized(language, "暂未发现配对端口，请确认系统配对码对话框保持打开", "No pairing port found. Keep the system pairing-code dialog open.")
    LocalPairingDiscoveryStatus.AMBIGUOUS -> localized(language, "发现多个本机配对端口，请在系统设置中保留当前配对窗口后重试", "Multiple pairing ports were found. Keep only the current system pairing window open and retry.")
    LocalPairingDiscoveryStatus.UNSUPPORTED -> localized(language, "当前系统未提供可发现的本机无线调试配对服务", "This system does not expose a discoverable local wireless pairing service.")
    LocalPairingDiscoveryStatus.STOPPED -> localized(language, "本机配对端口扫描已停止", "Local pairing-port scan stopped")
}

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
