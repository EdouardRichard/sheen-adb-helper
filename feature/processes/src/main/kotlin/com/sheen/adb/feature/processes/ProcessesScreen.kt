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
import com.sheen.adb.core.ProcessAnalysisEntry
import com.sheen.adb.core.ProcessApplicationAssociation
import com.sheen.adb.core.ProcessAssociationUnknownReason
import com.sheen.adb.ui.SheenDimensions

@Composable
fun ProcessesRoute(viewModel: ProcessesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProcessesScreen(state, viewModel)
}

@Composable
fun ProcessesScreen(state: ProcessesUiState, actions: ProcessesViewModel) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("进程分析", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = actions::refresh, enabled = state.isConnected && !state.isLoading) {
                        Text("刷新快照")
                    }
                }
                Text("只读快照；本页面不提供结束进程、强制停止或应用管理。")
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
                    label = { Text("筛选关联应用包名") },
                    singleLine = true,
                )
                if (state.isLoading) {
                    CircularProgressIndicator()
                    OutlinedButton(onClick = actions::cancel) { Text("取消刷新") }
                }
                AnalysisStatus(state)
                state.degradedReason?.let { Text(it) }
                state.error?.let { Text("${it.userMessage} ${it.nextStep}", color = MaterialTheme.colorScheme.error) }
                if (!state.isLoading && state.entries.isNotEmpty() && state.visibleEntries.isEmpty()) {
                    Text("当前组合条件没有匹配进程")
                }
            }
        }
        items(
            items = state.visibleEntries,
            key = { "${state.generation}:${it.process.pid}" },
        ) { entry -> ProcessCard(entry, context) }
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
        ProcessesAnalysisStatus.UNSUPPORTED -> "设备未提供进程分析所需字段"
        ProcessesAnalysisStatus.CANCELLED -> "进程快照刷新已取消"
        ProcessesAnalysisStatus.ERROR -> null
    }
    message?.let { Text(it) }
}

@Composable
private fun ProcessCard(entry: ProcessAnalysisEntry, context: Context) {
    val process = entry.process
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(process.name, style = MaterialTheme.typography.titleMedium)
            Text("PID ${process.pid} · UID ${process.uid ?: "设备未提供"} · 状态 ${process.state ?: "设备未提供"}")
            Text("常驻内存：${process.residentMemoryBytes?.let { "$it B" } ?: "设备未提供"}")
            Text(associationText(entry.applicationAssociation))
            if (entry.unavailableCapabilities.isNotEmpty()) {
                Text("部分字段不可用：${entry.unavailableCapabilities.joinToString { it.name }}")
            }
            Row {
                TextButton(onClick = { copy(context, "PID", process.pid.toString()) }) { Text("复制 PID") }
                TextButton(onClick = { copy(context, "进程名", process.name) }) { Text("复制进程名") }
            }
        }
    }
}

private fun associationText(association: ProcessApplicationAssociation): String = when (association) {
    is ProcessApplicationAssociation.Verified -> "已可靠关联应用：${association.packageName}"
    is ProcessApplicationAssociation.Multiple ->
        "共享 UID，无法唯一归属：${association.packageNames.sorted().joinToString()}"
    is ProcessApplicationAssociation.Unknown -> "应用归属未知：${association.reason.userText()}"
}

private fun ProcessAssociationUnknownReason.userText(): String = when (this) {
    ProcessAssociationUnknownReason.MISSING_UID -> "设备未提供 UID"
    ProcessAssociationUnknownReason.INVALID_UID -> "UID 格式不可识别"
    ProcessAssociationUnknownReason.NO_MATCH -> "当前应用快照无匹配项"
    ProcessAssociationUnknownReason.SESSION_MISMATCH -> "Session 已变化"
    ProcessAssociationUnknownReason.GENERATION_MISMATCH -> "快照已过期"
    ProcessAssociationUnknownReason.PID_REUSED -> "PID 已被复用"
    ProcessAssociationUnknownReason.PROCESS_EXITED -> "进程已退出"
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
