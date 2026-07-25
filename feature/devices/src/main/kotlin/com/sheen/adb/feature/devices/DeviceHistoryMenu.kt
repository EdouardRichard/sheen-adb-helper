package com.sheen.adb.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.data.DeviceProfile
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.UiLanguage

data class DeviceHistoryMenuItem(
    val profile: DeviceProfile,
    val endpointLabel: String,
    val isOnline: Boolean,
)

data class DeviceHistoryMenuPresentation(
    val items: List<DeviceHistoryMenuItem>,
) {
    val isEmpty: Boolean
        get() = items.isEmpty()
}

data class DeviceHistoryMenuCallbacks(
    val onReconnect: (DeviceProfile) -> Unit,
    val onRename: (DeviceProfile) -> Unit,
    val onDelete: (DeviceProfile) -> Unit,
)

fun deviceHistoryMenuPresentation(
    profiles: List<DeviceProfile>,
    connectionState: AdbConnectionState,
): DeviceHistoryMenuPresentation {
    val connected = connectionState as? AdbConnectionState.Connected
    return DeviceHistoryMenuPresentation(
        profiles
            .sortedByDescending(DeviceProfile::lastConnectedAtEpochMillis)
            .map { profile ->
                DeviceHistoryMenuItem(
                    profile = profile,
                    endpointLabel = if (':' in profile.host) {
                        "[${profile.host}]:${profile.debugPort}"
                    } else {
                        "${profile.host}:${profile.debugPort}"
                    },
                    isOnline = connected?.endpoint?.let {
                        it.host.equals(profile.host, ignoreCase = true) &&
                            it.port == profile.debugPort
                    } == true,
                )
            },
    )
}

@Composable
fun DeviceHistoryMenu(
    state: DevicesUiState,
    actions: DevicesViewModel,
    modifier: Modifier = Modifier,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val presentation = deviceHistoryMenuPresentation(state.profiles, state.connectionState)
    val callbacks = DeviceHistoryMenuCallbacks(
        onReconnect = actions::reconnect,
        onRename = actions::requestRename,
        onDelete = actions::requestDelete,
    )
    Column(
        modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "历史连接设备",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (presentation.isEmpty) {
            Text(
                "暂无历史设备。关闭菜单后可手动连接或扫描设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        presentation.items.forEach { item ->
            HistoryDeviceRow(item, callbacks, language)
        }
    }

    state.pendingRenameProfile?.let {
        AlertDialog(
            onDismissRequest = actions::dismissRename,
            title = { Text("编辑显示名") },
            text = {
                OutlinedTextField(
                    value = state.renameInput,
                    onValueChange = actions::updateRename,
                    label = { Text("显示名") },
                )
            },
            confirmButton = {
                TextButton(onClick = actions::confirmRename) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissRename) { Text("取消") }
            },
        )
    }
    state.pendingDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = actions::dismissDelete,
            title = { Text("删除设备档案？") },
            text = {
                Text("将删除“${profile.displayName}”及不再被其他档案引用的主机身份，之后可能需要重新配对。")
            },
            confirmButton = {
                TextButton(onClick = actions::confirmDelete) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDelete) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryDeviceRow(
    item: DeviceHistoryMenuItem,
    callbacks: DeviceHistoryMenuCallbacks,
    language: UiLanguage,
) {
    var showActions by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(
                    if (item.isOnline) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .5f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                    SheenShapes.large,
                )
                .combinedClickable(
                    onClick = { callbacks.onReconnect(item.profile) },
                    onLongClick = { showActions = true },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (item.isOnline) ActiveDeviceIndicator()
            Icon(
                SheenIcons.Phone,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (item.isOnline) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.profile.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isOnline) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (item.isOnline) item.endpointLabel
                    else if (language == UiLanguage.ZH_CN) "离线" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (item.isOnline) {
                Box(
                    Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, SheenShapes.full),
                )
            }
        }
        DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
            DropdownMenuItem(
                text = { Text(if (language == UiLanguage.ZH_CN) "重命名" else "Rename") },
                onClick = {
                    showActions = false
                    callbacks.onRename(item.profile)
                },
            )
            DropdownMenuItem(
                text = { Text(if (language == UiLanguage.ZH_CN) "删除" else "Delete") },
                onClick = {
                    showActions = false
                    callbacks.onDelete(item.profile)
                },
            )
        }
    }
}

@Composable
private fun ActiveDeviceIndicator() {
    Box(
        Modifier
            .width(4.dp)
            .heightIn(min = 24.dp)
            .background(MaterialTheme.colorScheme.secondary, SheenShapes.full),
    )
}
