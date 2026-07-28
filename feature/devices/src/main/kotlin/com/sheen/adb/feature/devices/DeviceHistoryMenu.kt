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
import com.sheen.adb.ui.SheenTonalLayers
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
            DevicesStrings.text(language, DevicesStringKey.HISTORY_TITLE),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (presentation.isEmpty) {
            Text(
                DevicesStrings.text(language, DevicesStringKey.HISTORY_EMPTY),
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
            title = {
                Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_RENAME_TITLE))
            },
            text = {
                OutlinedTextField(
                    value = state.renameInput,
                    onValueChange = actions::updateRename,
                    label = {
                        Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_DISPLAY_NAME))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = actions::confirmRename) {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_SAVE))
                }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissRename) {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_CANCEL))
                }
            },
        )
    }
    state.pendingDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = actions::dismissDelete,
            title = {
                Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_DELETE_TITLE))
            },
            text = {
                Text(
                    DevicesStrings.resolve(
                        language,
                        DevicesStrings.historyDeleteBodyRef(profile.displayName),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = actions::confirmDelete) {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_CONFIRM_DELETE))
                }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDelete) {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_CANCEL))
                }
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
                    if (item.isOnline) MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                        alpha = SheenTonalLayers.subtleOutlineAlpha,
                    )
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
                    else DevicesStrings.text(language, DevicesStringKey.HISTORY_OFFLINE),
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
                text = {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_RENAME))
                },
                onClick = {
                    showActions = false
                    callbacks.onRename(item.profile)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(DevicesStrings.text(language, DevicesStringKey.HISTORY_DELETE))
                },
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
