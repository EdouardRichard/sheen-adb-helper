package com.sheen.adb.feature.processes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.ProcessFieldState
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessTerminationScope
import com.sheen.adb.ui.SheenDimensions

@Composable
fun ProcessesRoute(viewModel: ProcessesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProcessesScreen(state, viewModel)
}

@Composable
fun ProcessesScreen(state: ProcessesUiState, actions: ProcessesViewModel) {
    val context = LocalContext.current
    state.pendingTermination?.let { pending ->
        if (pending.scope == null) {
            AlertDialog(
                onDismissRequest = actions::cancelTermination,
                title = { Text("选择终止范围") },
                text = { Text("目标：${pending.entry.applicationName} · ${pending.entry.processName} · PID ${pending.entry.pid}") },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            actions.selectTerminationScope(ProcessTerminationScope.SINGLE_PROCESS)
                        }) { Text("单个进程") }
                        if (ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP in ProcessesPolicy.terminationScopes(pending.entry)) {
                            TextButton(onClick = {
                                actions.selectTerminationScope(ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP)
                            }) { Text("整个应用") }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = actions::cancelTermination) { Text("取消") } },
            )
        } else {
            val whole = pending.scope == ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP
            AlertDialog(
                onDismissRequest = actions::cancelTermination,
                title = { Text("终止风险确认") },
                text = {
                    Text(
                        if (whole) {
                            "将对 ${pending.entry.applicationPackage ?: "无法解析应用"} 执行 force-stop，会停止该应用的进程、服务和任务，并可能在用户再次显式启动前阻止后台恢复；也可能导致应用异常、未保存数据丢失、服务中断或设备不稳定。"
                        } else {
                            "将终止进程 ${pending.entry.processName}（PID ${pending.entry.pid}），可能导致应用异常、未保存数据丢失、服务中断或设备不稳定。"
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { actions.confirmTermination(pending.nonce) }) { Text("我了解") }
                },
                dismissButton = { TextButton(onClick = actions::cancelTermination) { Text("取消") } },
            )
        }
    }
    LazyColumn(
        Modifier.padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("进程管理", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = actions::refresh, enabled = state.isConnected && !state.isLoading) { Text("刷新") }
                }
                Text("终止进程可能导致应用异常、数据丢失、服务中断或设备不稳定。")
                OutlinedTextField(
                    value = state.pidQuery,
                    onValueChange = actions::updatePidQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("筛选 PID（支持部分数字）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.processQuery,
                    onValueChange = actions::updateProcessQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("筛选进程名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.applicationQuery,
                    onValueChange = actions::updateApplicationQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("筛选应用名或包名") },
                    singleLine = true,
                )
                if (state.isLoading) {
                    CircularProgressIndicator()
                    OutlinedButton(onClick = actions::cancel) { Text("取消") }
                }
                AnalysisStatus(state)
                state.error?.let { Text("${it.userMessage} ${it.nextStep}", color = MaterialTheme.colorScheme.error) }
            }
        }
        items(state.visibleEntries, key = { "${it.identity.observedGeneration}:${it.pid}" }) {
            ProcessCard(it, context, actions)
        }
    }
}

@Composable
private fun AnalysisStatus(state: ProcessesUiState) {
    val message = when (state.status) {
        ProcessesAnalysisStatus.DISCONNECTED -> "请先连接设备"
        ProcessesAnalysisStatus.LOADING -> null
        ProcessesAnalysisStatus.READY -> "当前快照共 ${state.entries.size} 个进程"
        ProcessesAnalysisStatus.EMPTY -> "当前快照没有可显示的进程"
        ProcessesAnalysisStatus.PROCESSES_EXITED -> "刷新后发现部分进程已退出，列表已更新"
        ProcessesAnalysisStatus.UNSUPPORTED -> "设备未提供进程信息"
        ProcessesAnalysisStatus.CANCELLED -> "进程快照刷新已取消"
        ProcessesAnalysisStatus.ERROR -> null
    }
    message?.let { Text(it) }
}

@Composable
private fun ProcessCard(entry: ProcessSnapshotEntry, context: Context, actions: ProcessesViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.applicationName, style = MaterialTheme.typography.titleMedium)
            Text("应用名：${entry.applicationName}")
            Text("包名：${entry.applicationPackage ?: "无法解析应用名"}")
            Text("进程名：${entry.processName}")
            Text("CPU：${entry.cpuPercent.render(entry.cpuState)}")
            Text("内存：${entry.pssMiB.render(entry.pssState)}")
            Text("父 PID：${entry.parentPid ?: "未知"} · 进程 PID：${entry.pid}")
            Row {
                TextButton(onClick = { copy(context, "PID", entry.pid.toString()) }) { Text("复制 PID") }
                TextButton(onClick = { copy(context, "进程名", entry.processName) }) { Text("复制进程名") }
            }
            TextButton(onClick = { actions.requestTermination(entry) }) { Text("终止") }
        }
    }
}

private fun Double?.render(state: ProcessFieldState): String = when (state) {
    ProcessFieldState.AVAILABLE -> "${this ?: 0.0} "
    ProcessFieldState.CALCULATING -> "计算中"
    ProcessFieldState.UNKNOWN -> "未知"
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
