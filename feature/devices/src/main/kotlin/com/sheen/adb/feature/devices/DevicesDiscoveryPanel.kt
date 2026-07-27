package com.sheen.adb.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes

internal enum class DevicesDiscoveryAction {
    CONNECT,
}

internal data class DevicesDiscoveryItemPresentation(
    val deviceName: String?,
    val endpointLabel: String,
    val rolesText: String,
    val relationText: String,
    val statusText: String,
    val actions: Set<DevicesDiscoveryAction>,
    val pairingTarget: WirelessDiscoveryTarget?,
    val connectTarget: WirelessDiscoveryTarget?,
    val requiresPairing: Boolean,
)

internal data class DevicesDiscoveryPresentation(
    val statusText: String,
    val showProgress: Boolean,
    val showCancel: Boolean,
    val showRefresh: Boolean,
    val showManualAddress: Boolean,
    val items: List<DevicesDiscoveryItemPresentation>,
    val selectionExpired: Boolean,
    val canConfirmSelection: Boolean,
    val selectionMessage: String,
)

internal fun DevicesDiscoveryState.toDiscoveryPresentation(
    language: UiLanguage = UiLanguage.ZH_CN,
): DevicesDiscoveryPresentation {
    val selectedTarget = pendingSelection?.target
    val targetStillCurrent = selectedTarget == null || items.any { item ->
        item.selectable && (item.pairingTarget == selectedTarget || item.connectTarget == selectedTarget)
    }
    val selectionExpired = selectedTarget != null && !targetStillCurrent
    return DevicesDiscoveryPresentation(
        statusText = discoveryStatusText(language),
        showProgress = phase == DevicesDiscoveryPhase.SCANNING,
        showCancel = phase == DevicesDiscoveryPhase.SCANNING,
        showRefresh = phase != DevicesDiscoveryPhase.SCANNING,
        showManualAddress = true,
        items = items.take(MAX_DISCOVERY_ITEMS).map { item ->
            DevicesDiscoveryItemPresentation(
                deviceName = item.deviceName?.trim()?.takeIf(String::isNotEmpty),
                endpointLabel = item.endpointLabel,
                rolesText = if (language == UiLanguage.ZH_CN) "调试服务" else "Debugging service",
                relationText = when (item.relation) {
                    DevicesDiscoveryRelation.VERIFIED -> if (language == UiLanguage.ZH_CN) "已验证，可直接连接" else "Verified; ready to connect"
                    DevicesDiscoveryRelation.UNKNOWN -> if (language == UiLanguage.ZH_CN) "尚未验证，需先配对" else "Not verified; pairing required"
                },
                statusText = when (item.reachability) {
                    DevicesDiscoveryReachability.RESOLVED -> if (language == UiLanguage.ZH_CN) "当前可选择" else "Available"
                    DevicesDiscoveryReachability.LOST -> if (language == UiLanguage.ZH_CN) "服务已离线或端口已变化，请刷新" else "Service is offline or changed; refresh"
                    DevicesDiscoveryReachability.UNAVAILABLE -> if (language == UiLanguage.ZH_CN) "服务暂不可用，请刷新" else "Service unavailable; refresh"
                },
                actions = buildSet {
                    if (item.connectTarget != null && item.selectable) add(DevicesDiscoveryAction.CONNECT)
                },
                pairingTarget = item.pairingTarget,
                connectTarget = item.connectTarget,
                requiresPairing = item.requiresPairing,
            )
        },
        selectionExpired = selectionExpired,
        canConfirmSelection = selectedTarget != null && targetStillCurrent,
        selectionMessage = when {
            selectionExpired -> if (language == UiLanguage.ZH_CN) "该服务已过期或端口已变化，请刷新后重新选择。" else "This service expired or changed. Refresh and select it again."
            pendingSelection is DevicesDiscoverySelection.Pairing -> if (language == UiLanguage.ZH_CN) "确认使用该系统公布的配对服务？仍需输入 6 位配对码。" else "Use this advertised pairing service? A 6-digit code is still required."
            pendingSelection is DevicesDiscoverySelection.Connect -> {
                val selected = items.firstOrNull { it.connectTarget == pendingSelection.target }
                if (selected?.requiresPairing == true) {
                    if (language == UiLanguage.ZH_CN) {
                        "该调试服务尚未配对。确认后打开二维码配对页面，配对成功后自动连接。"
                    } else {
                        "This debugging service is not paired. Continue to pair by QR code, then connect automatically."
                    }
                } else {
                    if (language == UiLanguage.ZH_CN) {
                        "确认直接连接该调试服务？"
                    } else {
                        "Connect directly to this debugging service?"
                    }
                }
            }
            else -> ""
        },
    )
}

@Composable
internal fun DevicesDiscoveryPanel(
    state: DevicesDiscoveryState,
    actions: DevicesViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val presentation = state.toDiscoveryPresentation(language)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (presentation.items.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (presentation.showProgress) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Text(
                    presentation.statusText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (presentation.showRefresh) {
                    TextButton(onClick = actions::refreshDiscovery) {
                        Text(localized(language, "刷新", "Refresh"))
                    }
                }
            }
        } else {
            presentation.items.forEach { item ->
                DiscoveryDeviceCard(item, actions)
            }
        }
    }

    if (state.pendingSelection != null) {
        AlertDialog(
            onDismissRequest = actions::dismissDiscoverySelection,
            title = { Text(localized(language, "确认发现目标", "Confirm discovered target")) },
            text = { Text(presentation.selectionMessage) },
            confirmButton = {
                TextButton(
                    onClick = actions::confirmDiscoverySelection,
                    enabled = presentation.canConfirmSelection,
                ) { Text(localized(language, "确认", "Confirm")) }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDiscoverySelection) { Text(localized(language, "取消", "Cancel")) }
            },
        )
    }
}

@Composable
private fun DiscoveryDeviceCard(
    item: DevicesDiscoveryItemPresentation,
    actions: DevicesViewModel,
) {
    val onSelect: (() -> Unit)? = when {
        DevicesDiscoveryAction.CONNECT in item.actions && item.connectTarget != null ->
            ({ actions.selectDiscoveryConnect(item.connectTarget) })
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, SheenShapes.extraLarge)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SheenShapes.extraLarge)
            .clickable(enabled = onSelect != null) { onSelect?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(SheenDimensions.deviceIconContainer)
                .background(MaterialTheme.colorScheme.surfaceVariant, SheenShapes.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SheenIcons.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.deviceName ?: item.rolesText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                item.endpointLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onSelect?.invoke() },
            enabled = onSelect != null,
            modifier = Modifier.size(SheenDimensions.minimumTouchTarget),
        ) {
            Icon(
                SheenIcons.Wireless,
                contentDescription = item.statusText,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun DevicesDiscoveryState.discoveryStatusText(language: UiLanguage): String =
    if (language == UiLanguage.EN_US) when (phase) {
        DevicesDiscoveryPhase.IDLE -> "A 10-second foreground scan starts when this page opens."
        DevicesDiscoveryPhase.SCANNING -> "Scanning for 10 seconds."
        DevicesDiscoveryPhase.CONTENT -> "Found ${items.size} advertised targets."
        DevicesDiscoveryPhase.EMPTY -> "No services found. Refresh or enter an address manually."
        DevicesDiscoveryPhase.CANCELLED -> "Scan cancelled. Refresh to try again."
        DevicesDiscoveryPhase.ERROR -> when (failure) {
            DevicesDiscoveryFailure.NETWORK_UNAVAILABLE -> "Network unavailable. Check the connection and refresh."
            DevicesDiscoveryFailure.PERMISSION_UNAVAILABLE -> "System discovery capability is unavailable."
            DevicesDiscoveryFailure.RESOLUTION_FAILED -> "Service resolution failed or the target expired."
            DevicesDiscoveryFailure.TIMED_OUT -> "The 10-second scan timed out."
            DevicesDiscoveryFailure.SESSION_CHANGED -> "The ADB session changed. Refresh the results."
            DevicesDiscoveryFailure.PLATFORM_FAILURE, null -> "Wireless service discovery failed."
        }
    } else when (phase) {
    DevicesDiscoveryPhase.IDLE -> "进入设备页后开始 10 秒前台扫描。"
    DevicesDiscoveryPhase.SCANNING -> "正在进行 10 秒扫描，最多显示 15 个系统公布的服务。"
    DevicesDiscoveryPhase.CONTENT -> "已发现 ${items.size} 个可展示目标；未知关系不会按名称或地址合并。"
    DevicesDiscoveryPhase.EMPTY ->
        "未发现服务。VPN、热点隔离、当前网络或 ROM 策略可能限制发现，可刷新或手动输入。"
    DevicesDiscoveryPhase.CANCELLED -> "扫描已停止，可刷新后重试。"
    DevicesDiscoveryPhase.ERROR -> when (failure) {
        DevicesDiscoveryFailure.NETWORK_UNAVAILABLE -> "当前网络不可用；请检查 VPN、热点隔离或网络连接后刷新。"
        DevicesDiscoveryFailure.PERMISSION_UNAVAILABLE -> "系统发现权限或能力不可用，请检查系统设置后刷新。"
        DevicesDiscoveryFailure.RESOLUTION_FAILED -> "服务解析失败或目标已过期，请刷新后重新选择。"
        DevicesDiscoveryFailure.TIMED_OUT -> "10 秒扫描窗口已结束，可刷新或手动输入地址。"
        DevicesDiscoveryFailure.SESSION_CHANGED -> "ADB Session 已变化，本轮结果已失效，请刷新。"
        DevicesDiscoveryFailure.PLATFORM_FAILURE, null -> "系统无线服务发现失败，请稍后刷新或手动输入。"
    }
    }

private const val MAX_DISCOVERY_ITEMS = 15

private fun localized(language: UiLanguage, zh: String, en: String): String =
    if (language == UiLanguage.ZH_CN) zh else en
