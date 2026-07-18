package com.sheen.adb.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.ApplicationField
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import com.sheen.adb.ui.SheenDimensions

@Composable
fun AppsRoute(viewModel: AppsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    AppsScreen(state, viewModel)
}

@Composable
fun AppsScreen(state: AppsUiState, actions: AppsViewModel) {
    LazyColumn(
        Modifier.padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("应用管理", style = MaterialTheme.typography.headlineSmall)
                    Button(
                        onClick = actions::refresh,
                        enabled = state.isConnected && !state.isBusy,
                        modifier = Modifier.semantics { contentDescription = "刷新当前用户第三方应用列表" },
                    ) { Text("刷新") }
                }
                Text("仅显示被控端当前 Android 用户的第三方包；包名是主显示名称，数据不会保存。")
                if (!state.isConnected) Text("请先连接设备")
                if (state.isConnected && state.userId != null) {
                    Text("设备：${state.deviceDisplayName} · 当前用户：${state.userId}")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("按包名搜索") },
                    singleLine = true,
                    enabled = !state.isBusy,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.filter == AppsFilter.ALL, { actions.setFilter(AppsFilter.ALL) }, { Text("全部") }, enabled = !state.isBusy)
                    FilterChip(state.filter == AppsFilter.ENABLED, { actions.setFilter(AppsFilter.ENABLED) }, { Text("已启用") }, enabled = !state.isBusy)
                    FilterChip(state.filter == AppsFilter.DISABLED, { actions.setFilter(AppsFilter.DISABLED) }, { Text("已禁用") }, enabled = !state.isBusy)
                }
                if (state.isBusy) {
                    CircularProgressIndicator()
                    Text(activeOperationLabel(state))
                    OutlinedButton(onClick = { actions.cancelActive() }) {
                        Text(if (state.activeOperation == AppsOperation.LOADING) "取消加载" else "取消操作")
                    }
                }
                state.degradedReason?.let { Text("降级说明：$it") }
                if (state.unavailableFields.isNotEmpty()) {
                    Text("设备未可靠提供：${state.unavailableFields.joinToString("、", transform = ApplicationField::displayName)}")
                }
                state.error?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(it.userMessage, color = MaterialTheme.colorScheme.error)
                            Text("下一步：${it.nextStep}")
                            Text("技术代码：${it.technicalCode}")
                        }
                    }
                }
                state.operationNotice?.let { notice ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                notice.message,
                                color = if (notice.outcomeUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = actions::dismissNotice) { Text("关闭提示") }
                        }
                    }
                }
                if (state.isConnected && !state.isLoading && state.error == null && state.applications.isEmpty()) {
                    Text("当前用户没有可显示的第三方应用")
                } else if (!state.isLoading && state.applications.isNotEmpty() && state.visibleApplications.isEmpty()) {
                    Text("没有符合当前搜索和筛选条件的应用")
                }
            }
        }
        items(state.visibleApplications, key = { it.packageName }) { application ->
            ApplicationCard(application, state, actions)
        }
    }
    state.pendingConfirmation?.let { ApplicationConfirmationDialog(it, actions) }
}

@Composable
private fun ApplicationCard(application: RemoteApplication, state: AppsUiState, actions: AppsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(application.packageName, style = MaterialTheme.typography.titleMedium)
            Text("状态：${application.enabledState.displayName()}")
            application.versionName?.let { Text("版本名：$it") }
            application.versionCode?.let { Text("版本号：$it") }
            application.installerPackage?.let { Text("安装器：$it") }
            if (AppsPolicy.canMutate(state, application)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { actions.requestForceStop(application.packageName) },
                        modifier = Modifier.semantics { contentDescription = "强制停止 ${application.packageName}" },
                    ) { Text("强制停止") }
                    when (application.enabledState) {
                        RemoteApplicationEnabledState.ENABLED -> Button(
                            onClick = { actions.requestDisable(application.packageName) },
                            modifier = Modifier.semantics { contentDescription = "禁用 ${application.packageName}" },
                        ) { Text("禁用") }
                        RemoteApplicationEnabledState.DISABLED -> Button(
                            onClick = { actions.requestEnable(application.packageName) },
                            modifier = Modifier.semantics { contentDescription = "重新启用 ${application.packageName}" },
                        ) { Text("重新启用") }
                        RemoteApplicationEnabledState.UNKNOWN -> Unit
                    }
                }
            } else if (application.enabledState == RemoteApplicationEnabledState.UNKNOWN) {
                Text("启用状态无法可靠确认；请刷新，当前不提供修改入口。")
            } else if (state.isLocalSession && application.packageName == AppsPolicy.SELF_PACKAGE_NAME) {
                Text("本机连接中禁止操作 Sheen ADB 助手自身。")
            }
        }
    }
}

@Composable
private fun ApplicationConfirmationDialog(confirmation: AppsConfirmation, actions: AppsViewModel) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(confirmation) { cancelFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = actions::dismissConfirmation,
        title = { Text("确认${confirmation.operation.displayName()}？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("应用：${confirmation.packageName}")
                Text("设备：${confirmation.deviceDisplayName}")
                Text("当前用户：${confirmation.userId}")
                Text(confirmation.operation.consequence())
                Text("每次操作都必须单独确认；结果以设备实际状态为准。")
            }
        },
        confirmButton = {
            TextButton(onClick = actions::confirmPending) { Text(confirmation.operation.displayName()) }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissConfirmation, modifier = Modifier.focusRequester(cancelFocus)) { Text("取消") }
        },
    )
}

private fun activeOperationLabel(state: AppsUiState): String = when (state.activeOperation) {
    AppsOperation.LOADING -> "正在读取当前用户第三方应用……"
    AppsOperation.FORCE_STOP -> "正在强制停止：${state.activePackageName.orEmpty()}"
    AppsOperation.DISABLE -> "正在禁用：${state.activePackageName.orEmpty()}"
    AppsOperation.ENABLE -> "正在重新启用：${state.activePackageName.orEmpty()}"
    null -> ""
}

private fun ApplicationField.displayName(): String = when (this) {
    ApplicationField.VERSION_CODE -> "版本号"
    ApplicationField.VERSION_NAME -> "版本名"
    ApplicationField.INSTALLER_PACKAGE -> "安装器"
}

private fun RemoteApplicationEnabledState.displayName(): String = when (this) {
    RemoteApplicationEnabledState.ENABLED -> "已启用"
    RemoteApplicationEnabledState.DISABLED -> "已禁用"
    RemoteApplicationEnabledState.UNKNOWN -> "设备未可靠提供"
}

private fun AppsOperation.displayName(): String = when (this) {
    AppsOperation.FORCE_STOP -> "强制停止"
    AppsOperation.DISABLE -> "禁用"
    AppsOperation.ENABLE -> "重新启用"
    AppsOperation.LOADING -> "加载"
}

private fun AppsOperation.consequence(): String = when (this) {
    AppsOperation.FORCE_STOP -> "可能立即中断前台任务、下载、通知和未保存工作；应用之后仍可能自动重启。"
    AppsOperation.DISABLE -> "应用入口、通知、后台任务和关联功能可能不可用。"
    AppsOperation.ENABLE -> "只修改 enabled state，不承诺恢复运行状态、通知、任务或数据。"
    AppsOperation.LOADING -> ""
}
