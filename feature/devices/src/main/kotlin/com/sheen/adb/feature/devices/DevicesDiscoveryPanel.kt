package com.sheen.adb.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.core.WirelessServiceType

internal enum class DevicesDiscoveryAction {
    PAIR,
    CONNECT,
}

internal data class DevicesDiscoveryItemPresentation(
    val endpointLabel: String,
    val rolesText: String,
    val relationText: String,
    val statusText: String,
    val actions: Set<DevicesDiscoveryAction>,
    val pairingTarget: WirelessDiscoveryTarget?,
    val connectTarget: WirelessDiscoveryTarget?,
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

internal fun DevicesDiscoveryState.toDiscoveryPresentation(): DevicesDiscoveryPresentation {
    val selectedTarget = pendingSelection?.target
    val targetStillCurrent = selectedTarget == null || items.any { item ->
        item.selectable && (item.pairingTarget == selectedTarget || item.connectTarget == selectedTarget)
    }
    val selectionExpired = selectedTarget != null && !targetStillCurrent
    return DevicesDiscoveryPresentation(
        statusText = discoveryStatusText(),
        showProgress = phase == DevicesDiscoveryPhase.SCANNING,
        showCancel = phase == DevicesDiscoveryPhase.SCANNING,
        showRefresh = phase != DevicesDiscoveryPhase.SCANNING,
        showManualAddress = true,
        items = items.take(MAX_DISCOVERY_ITEMS).map { item ->
            DevicesDiscoveryItemPresentation(
                endpointLabel = item.endpointLabel,
                rolesText = item.serviceTypes.sortedBy(WirelessServiceType::ordinal).joinToString(" / ") {
                    when (it) {
                        WirelessServiceType.PAIRING -> "配对服务"
                        WirelessServiceType.CONNECT -> "连接服务"
                    }
                },
                relationText = when (item.relation) {
                    DevicesDiscoveryRelation.VERIFIED -> "已通过当前 Session 身份验证关联"
                    DevicesDiscoveryRelation.UNKNOWN -> "配对与连接尚未验证关联，请分别确认"
                },
                statusText = when (item.reachability) {
                    DevicesDiscoveryReachability.RESOLVED -> "当前可选择"
                    DevicesDiscoveryReachability.LOST -> "服务已离线或端口已变化，请刷新"
                    DevicesDiscoveryReachability.UNAVAILABLE -> "服务暂不可用，请刷新"
                },
                actions = buildSet {
                    if (item.pairingTarget != null && item.selectable) add(DevicesDiscoveryAction.PAIR)
                    if (item.connectTarget != null && item.selectable) add(DevicesDiscoveryAction.CONNECT)
                },
                pairingTarget = item.pairingTarget,
                connectTarget = item.connectTarget,
            )
        },
        selectionExpired = selectionExpired,
        canConfirmSelection = selectedTarget != null && targetStillCurrent,
        selectionMessage = when {
            selectionExpired -> "该服务已过期或端口已变化，请刷新后重新选择。"
            pendingSelection is DevicesDiscoverySelection.Pairing -> "确认使用该系统公布的配对服务？仍需输入 6 位配对码。"
            pendingSelection is DevicesDiscoverySelection.Connect -> "确认连接该系统公布的调试服务？应用不会自动替换当前 Session。"
            else -> ""
        },
    )
}

@Composable
internal fun DevicesDiscoveryPanel(
    state: DevicesDiscoveryState,
    actions: DevicesViewModel,
) {
    val presentation = state.toDiscoveryPresentation()
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("局域网无线调试设备", style = MaterialTheme.typography.titleLarge)
            Text("仅发现系统公布的 ADB TLS 服务，不枚举子网或探测端口。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (presentation.showProgress) CircularProgressIndicator()
                if (presentation.showCancel) {
                    OutlinedButton(onClick = actions::cancelDiscovery) { Text("取消扫描") }
                }
                if (presentation.showRefresh) {
                    Button(onClick = actions::refreshDiscovery) { Text("刷新") }
                }
                if (presentation.showManualAddress) {
                    TextButton(onClick = actions::useManualDiscoveryAddress) { Text("手动输入地址") }
                }
            }
            Text(presentation.statusText)
            presentation.items.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(item.rolesText, style = MaterialTheme.typography.titleMedium)
                        Text(item.endpointLabel)
                        Text(item.relationText, style = MaterialTheme.typography.bodySmall)
                        Text(item.statusText, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (DevicesDiscoveryAction.PAIR in item.actions) {
                                item.pairingTarget?.let { target ->
                                    Button(onClick = { actions.selectDiscoveryPairing(target) }) { Text("选择配对") }
                                }
                            }
                            if (DevicesDiscoveryAction.CONNECT in item.actions) {
                                item.connectTarget?.let { target ->
                                    Button(onClick = { actions.selectDiscoveryConnect(target) }) { Text("选择连接") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.pendingSelection != null) {
        AlertDialog(
            onDismissRequest = actions::dismissDiscoverySelection,
            title = { Text("确认发现目标") },
            text = { Text(presentation.selectionMessage) },
            confirmButton = {
                TextButton(
                    onClick = actions::confirmDiscoverySelection,
                    enabled = presentation.canConfirmSelection,
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDiscoverySelection) { Text("取消") }
            },
        )
    }
}

private fun DevicesDiscoveryState.discoveryStatusText(): String = when (phase) {
    DevicesDiscoveryPhase.IDLE -> "进入设备页后开始 10 秒前台扫描。"
    DevicesDiscoveryPhase.SCANNING -> "正在扫描，最多显示 15 个系统公布的服务。"
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
