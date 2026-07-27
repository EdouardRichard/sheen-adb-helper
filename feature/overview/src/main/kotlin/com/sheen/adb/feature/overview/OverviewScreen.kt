package com.sheen.adb.feature.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.DeviceOverview
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings
import com.sheen.adb.ui.V1SharedStringKey
import com.sheen.adb.ui.V1SharedStrings
import java.util.Locale

@Composable
fun OverviewRoute(
    viewModel: OverviewViewModel,
    onExportRequested: (QuickActionArtifactRef) -> Unit = {},
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickAction by viewModel.quickActionState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
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
    OverviewScreen(
        state = state,
        quickAction = quickAction,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::dismissError,
        onScreenshot = viewModel::requestScreenshot,
        onScreenRecord = viewModel::requestScreenRecord,
        onReboot = viewModel::requestReboot,
        onConfirmReboot = viewModel::confirmReboot,
        onDismissReboot = viewModel::dismissRebootConfirmation,
        onExportRequested = onExportRequested,
        language = language,
    )
}

@Composable
fun OverviewScreen(
    state: OverviewUiState,
    quickAction: QuickActionUiState = QuickActionUiState.Idle,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onScreenRecord: () -> Unit = {},
    onReboot: () -> Unit = {},
    onConfirmReboot: () -> Unit = {},
    onDismissReboot: () -> Unit = {},
    onExportRequested: (QuickActionArtifactRef) -> Unit = {},
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeviceIdentityCard(state, language)
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SheenShapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOf(
                            OverviewStrings.text(language, OverviewStringKey.OUTCOME_UNKNOWN),
                            OverviewStrings.resolve(language, OverviewStrings.technicalCode(it.technicalCode)),
                        ).joinToString(" · "),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
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
        }
        state.overview?.let { OverviewMetricGrid(it, language) }
        if (state.isConnected) {
            QuickActionsCard(
                state = quickAction,
                onScreenshot = onScreenshot,
                onScreenRecord = onScreenRecord,
                onReboot = onReboot,
                onExportRequested = onExportRequested,
                language = language,
            )
        } else {
            Text(
                OverviewStrings.text(language, OverviewStringKey.DISCONNECTED),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (quickAction is QuickActionUiState.Confirming &&
        quickAction.kind == QuickActionKind.REBOOT
    ) {
        AlertDialog(
            onDismissRequest = onDismissReboot,
            title = { Text(OverviewStrings.text(language, OverviewStringKey.REBOOT_CONFIRMATION)) },
            text = {
                Text(OverviewStrings.text(language, OverviewStringKey.REBOOT_RISK))
            },
            confirmButton = {
                Button(onClick = onConfirmReboot) {
                    Text(OverviewStrings.text(language, OverviewStringKey.CONFIRM_REBOOT))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReboot) {
                    Text(OverviewStrings.text(language, OverviewStringKey.CANCEL_REBOOT))
                }
            },
        )
    }
}

@Composable
private fun DeviceIdentityCard(state: OverviewUiState, language: UiLanguage) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, SheenShapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SheenIcons.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.overview?.model ?: "被控端设备",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (state.isConnected) {
                        "● ${localized(language, "已通过 TCP/IP 连接", "Connected over TCP/IP")}"
                    } else "○ ${OverviewStrings.text(language, OverviewStringKey.DISCONNECTED)}",
                    color = if (state.isConnected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricGrid(info: DeviceOverview, language: UiLanguage) {
    val unavailable = V01Strings.text(language, V01StringKey.VALUE_UNAVAILABLE)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CpuMetricCard(info, language, unavailable, Modifier.weight(1f))
            UsageMetricCard(
                icon = SheenIcons.MemoryAlt,
                label = localized(language, "运行内存", "Memory"),
                total = info.memoryTotalBytes,
                available = info.memoryAvailableBytes,
                unavailable = unavailable,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UsageMetricCard(
                icon = SheenIcons.Storage,
                label = localized(language, "存储空间", "Storage"),
                total = info.storageTotalBytes,
                available = info.storageAvailableBytes,
                unavailable = unavailable,
                modifier = Modifier.weight(1f),
            )
            BatteryMetricCard(info, language, unavailable, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricContainer(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier
            .aspectRatio(1f)
            .heightIn(min = 164.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

@Composable
private fun ProcessorMetricContainer(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier
            .aspectRatio(1f)
            .heightIn(min = 164.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun MetricHeader(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CpuMetricCard(
    info: DeviceOverview,
    language: UiLanguage,
    unavailable: String,
    modifier: Modifier,
) {
    ProcessorMetricContainer(modifier) {
        MetricHeader(
            SheenIcons.Cpu,
            localized(language, "处理器", "Processor"),
            Modifier.align(Alignment.TopStart),
        )
        CpuUsageRing(
            progress = null,
            centerText = info.availableCores?.let {
                localized(language, "$it 核", "$it cores")
            } ?: unavailable,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CpuUsageRing(
    progress: Float?,
    centerText: String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.tertiary
    Box(modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(88.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (progress != null) {
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = 270f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(centerText, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UsageMetricCard(
    icon: ImageVector,
    label: String,
    total: Long?,
    available: Long?,
    unavailable: String,
    modifier: Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val used = if (total != null && available != null) (total - available).coerceAtLeast(0L) else null
    val fraction = if (used != null && total != null && total > 0L) used.toFloat() / total else null
    MetricContainer(modifier) {
        MetricHeader(icon, label)
        MetricBottomContent(
            used = used,
            total = total,
            unavailable = unavailable,
            progress = fraction,
            accent = accent,
        )
    }
}

@Composable
private fun MetricBottomContent(
    used: Long?,
    total: Long?,
    unavailable: String,
    progress: Float?,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricValueRow(used, total, unavailable)
        UsageProgressBar(progress, accent)
    }
}

@Composable
private fun MetricValueRow(
    used: Long?,
    total: Long?,
    unavailable: String,
) {
    val usedAmount = used?.let(::formatMetricAmount)
    val totalAmount = total?.let(::formatMetricAmount)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (usedAmount == null || totalAmount == null) {
            Text(
                unavailable,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Row {
                Text(
                    usedAmount.value,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    usedAmount.unit,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                "/ ${totalAmount.value}${totalAmount.unit}",
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun UsageProgressBar(
    progress: Float?,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, SheenShapes.full),
    ) {
        if (progress != null) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .heightIn(min = 8.dp)
                    .background(color, SheenShapes.full),
            )
        }
    }
}

@Composable
private fun BatteryMetricCard(
    info: DeviceOverview,
    language: UiLanguage,
    unavailable: String,
    modifier: Modifier,
) {
    MetricContainer(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricHeader(SheenIcons.BatteryCharging, localized(language, "电池", "Battery"))
            info.chargingState?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        BatteryBottomContent(info, unavailable)
    }
}

@Composable
private fun BatteryBottomContent(
    info: DeviceOverview,
    unavailable: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            info.batteryPercent?.let { "$it%" } ?: unavailable,
            style = MaterialTheme.typography.headlineLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(SheenIcons.Temperature, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Text(
                info.temperatureCelsius?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: unavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionsCard(
    state: QuickActionUiState,
    onScreenshot: () -> Unit,
    onScreenRecord: () -> Unit,
    onReboot: () -> Unit,
    onExportRequested: (QuickActionArtifactRef) -> Unit,
    language: UiLanguage,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                OverviewStrings.text(language, OverviewStringKey.QUICK_ACTIONS),
                style = MaterialTheme.typography.titleMedium,
            )
            val busy = state is QuickActionUiState.Running ||
                state is QuickActionUiState.Confirming ||
                state is QuickActionUiState.AwaitingExport ||
                state is QuickActionUiState.Exporting
            val recording = state as? QuickActionUiState.Running
            val isScreenRecording = recording?.kind == QuickActionKind.SCREEN_RECORD
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionDesignButton(
                    SheenIcons.Screenshot,
                    OverviewStrings.text(language, OverviewStringKey.SCREENSHOT),
                    MaterialTheme.colorScheme.primary,
                    !busy,
                    onScreenshot,
                    Modifier.weight(1f),
                )
                QuickActionDesignButton(
                    SheenIcons.ScreenRecord,
                    OverviewStrings.text(
                        language,
                        if (isScreenRecording) {
                            OverviewStringKey.STOP_SCREEN_RECORD
                        } else {
                            OverviewStringKey.SCREEN_RECORD
                        },
                    ),
                    MaterialTheme.colorScheme.tertiary,
                    !busy || (isScreenRecording && recording?.isStopping == false),
                    onScreenRecord,
                    Modifier.weight(1f),
                )
                QuickActionDesignButton(
                    SheenIcons.Power,
                    OverviewStrings.text(language, OverviewStringKey.REBOOT),
                    MaterialTheme.colorScheme.onSurface,
                    !busy,
                    onReboot,
                    Modifier.weight(1f),
                )
            }
            QuickActionStatus(state, onExportRequested, language)
        }
    }
}

@Composable
private fun QuickActionDesignButton(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .heightIn(min = 100.dp)
            .alpha(if (enabled) 1f else .38f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SheenShapes.large)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp), tint = tint)
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun QuickActionStatus(
    state: QuickActionUiState,
    onExportRequested: (QuickActionArtifactRef) -> Unit,
    language: UiLanguage,
) {
    when (state) {
        QuickActionUiState.Idle,
        is QuickActionUiState.Confirming -> Unit
        is QuickActionUiState.Running -> {
            val status = OverviewStrings.text(language, requireNotNull(state.statusKey))
            Text(
                "$status · ${state.elapsedMillis / 1_000}s · ${formatBytes(state.bytesWritten)}",
                fontFamily = FontFamily.Monospace,
            )
        }
        is QuickActionUiState.AwaitingExport -> Button(
            onClick = { onExportRequested(state.artifact) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(saveActionLabel(state.kind, language))
        }
        is QuickActionUiState.Exporting -> Text(
            OverviewStrings.text(language, OverviewStringKey.SAVE_WRITING),
        )
        is QuickActionUiState.Succeeded -> {
            val destination = state.destinationName?.let {
                OverviewStrings.resolve(language, OverviewStrings.artifactLabel(it))
            }
            Text(
                listOfNotNull(
                    OverviewStrings.text(language, OverviewStringKey.SAVE_SUCCEEDED),
                    destination,
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        is QuickActionUiState.Cancelled -> Text(
            listOf(
                OverviewStrings.text(language, OverviewStringKey.SAVE_CANCELLED),
                safeOverviewValue(state.reason, maxCodePoints = 64),
            ).joinToString(" · "),
        )
        is QuickActionUiState.Failed -> {
            val technicalCode = OverviewStrings.resolve(
                language,
                OverviewStrings.technicalCode(state.technicalCode),
            )
            Text(
                listOf(
                    OverviewStrings.text(language, OverviewStringKey.SAVE_FAILED),
                    technicalCode,
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.error,
            )
        }
        is QuickActionUiState.ResultUnknown -> Text(
            OverviewStrings.text(language, OverviewStringKey.OUTCOME_UNKNOWN),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun saveActionLabel(
    kind: QuickActionKind,
    language: UiLanguage,
): String = OverviewStrings.text(
    language,
    if (kind == QuickActionKind.SCREENSHOT) {
        OverviewStringKey.SAVE_SCREENSHOT
    } else {
        OverviewStringKey.SAVE_SCREEN_RECORD
    },
)

private fun safeOverviewValue(
    raw: String,
    maxCodePoints: Int,
): String = SafeVerbatimText.render(
    raw = raw,
    policy = SafeVerbatimPolicy.SingleLine(maxCodePoints),
).display

private fun usedOfTotal(total: Long?, available: Long?): String {
    if (total == null || available == null) return "不可用"
    return "${formatBytes((total - available).coerceAtLeast(0L))} / ${formatBytes(total)}"
}

private fun formatBytes(bytes: Long?): String? = bytes?.let {
    when {
        it >= 1024L * 1024 * 1024 ->
            String.format(Locale.ROOT, "%.1f GiB", it / (1024.0 * 1024 * 1024))
        it >= 1024L * 1024 ->
            String.format(Locale.ROOT, "%.1f MiB", it / (1024.0 * 1024))
        else -> "$it B"
    }
}

private data class MetricAmount(
    val value: String,
    val unit: String,
)

private fun formatMetricAmount(bytes: Long): MetricAmount = when {
    bytes >= 1024L * 1024 * 1024 -> MetricAmount(
        value = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024 * 1024)),
        unit = "GB",
    )
    bytes >= 1024L * 1024 -> MetricAmount(
        value = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024)),
        unit = "MB",
    )
    else -> MetricAmount(bytes.toString(), "B")
}

private fun localized(language: UiLanguage, zh: String, en: String): String =
    if (language == UiLanguage.ZH_CN) zh else en
