package com.sheen.adb.feature.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.core.LogcatBuffer
import com.sheen.adb.core.LogcatLevel
import com.sheen.adb.ui.SheenDimensions

enum class LogcatFilterControl { LEVEL, TAG, KEYWORD, PID, PROCESS, APPLICATION }

enum class LogcatPresentationStatus {
    DISCONNECTED,
    LOADING,
    ACTIVE,
    EMPTY,
    TRUNCATED,
    PARSE_DEGRADED,
    STOPPED,
    ERROR,
}

data class LogcatVisibleTransfer(
    val enabled: Boolean,
    val requiresExplicitUserAction: Boolean,
    val text: String,
    val recordCount: Int,
)

object LogcatPresentationPolicy {
    val controls: List<LogcatFilterControl> = emptyList()

    fun labels(): List<String> = listOf("日志等级", "标签", "关键字", "PID", "进程", "应用")

    fun filterSummary(filter: LogcatAnalysisFilter): String = "日志提取模式，不进行应用内分析或筛选"

    fun status(state: LogcatUiState): LogcatPresentationStatus = when {
        !state.isConnected -> LogcatPresentationStatus.DISCONNECTED
        state.status == LogcatAnalysisStatus.ERROR -> LogcatPresentationStatus.ERROR
        state.status == LogcatAnalysisStatus.LOADING_PROCESSES -> LogcatPresentationStatus.LOADING
        state.droppedOldest -> LogcatPresentationStatus.TRUNCATED
        state.parseDegraded -> LogcatPresentationStatus.PARSE_DEGRADED
        state.status == LogcatAnalysisStatus.STOPPED -> LogcatPresentationStatus.STOPPED
        state.visibleLines.isEmpty() -> LogcatPresentationStatus.EMPTY
        else -> LogcatPresentationStatus.ACTIVE
    }

    fun visibleTransfer(state: LogcatUiState): LogcatVisibleTransfer = LogcatVisibleTransfer(
        enabled = state.visibleLines.isNotEmpty(),
        requiresExplicitUserAction = true,
        text = state.visibleLines.joinToString("\n"),
        recordCount = state.visibleLines.size,
    )
}

@Composable
fun LogcatRoute(viewModel: LogcatViewModel) {
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
    LogcatScreen(state, viewModel)
}

@Composable
fun LogcatScreen(state: LogcatUiState, actions: LogcatViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val transfer = LogcatPresentationPolicy.visibleTransfer(state)
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(actions::export)
    }
    LaunchedEffect(state.visibleLines.size) {
        if (state.visibleLines.isNotEmpty()) listState.scrollToItem(state.visibleLines.size)
    }
    LazyColumn(
        Modifier.padding(SheenDimensions.screenPadding),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing)) {
                Text("Logcat 日志提取", style = MaterialTheme.typography.headlineSmall)
                Text("仅在本页前台且你点击“开始”后采集；日志只在当前进程内有界保留。")
                if (!state.isConnected) Text("请先连接设备")
                CaptureControls(state, actions)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.isCapturing) Button(actions::start, enabled = state.isConnected) { Text("开始") }
                    if (state.isCapturing) {
                        Button(actions::stop) { Text("停止") }
                    }
                    OutlinedButton(actions::clear) { Text("清屏") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copy(context, "当前可见 Logcat", transfer.text) },
                        enabled = transfer.enabled,
                    ) { Text("复制当前可见") }
                    OutlinedButton(
                        onClick = { createDocument.launch("sheen-logcat.txt") },
                        enabled = transfer.enabled,
                    ) { Text("导出当前可见") }
                    OutlinedButton(
                        onClick = {
                            actions.prepareShareFile()?.let { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.logcat.share",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "发送日志文件"))
                            }
                        },
                        enabled = transfer.enabled,
                    ) { Text("分享日志文件") }
                }
                PresentationStatus(state)
                Text("本版本不提供日志筛选、进程关联或分析，仅提取原始文本。")
                state.exportNotice?.let { Text(it) }
                state.error?.let { Text("${it.userMessage} ${it.nextStep}", color = MaterialTheme.colorScheme.error) }
            }
        }
        items(state.visibleLines) { line ->
            Card(Modifier.fillMaxWidth()) {
                Text(line, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CaptureControls(state: LogcatUiState, actions: LogcatViewModel) {
    Text("采集最低等级")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LogcatLevel.entries.forEach { level ->
            FilterChip(
                selected = state.minimumLevel == level,
                onClick = { actions.setLevel(level) },
                label = { Text(level.displayName()) },
                enabled = !state.isCapturing,
            )
        }
    }
    Text("采集缓冲区")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LogcatBuffer.entries.forEach { buffer ->
            FilterChip(
                selected = buffer in state.buffers,
                onClick = { actions.toggleBuffer(buffer) },
                label = { Text(buffer.argument) },
                enabled = !state.isCapturing,
            )
        }
    }
}

@Composable
private fun PresentationStatus(state: LogcatUiState) {
    val message = when (LogcatPresentationPolicy.status(state)) {
        LogcatPresentationStatus.DISCONNECTED -> "诊断未连接"
        LogcatPresentationStatus.LOADING -> "正在准备日志提取"
        LogcatPresentationStatus.ACTIVE -> "正在显示 ${state.visibleRecords.size} 条原始日志"
        LogcatPresentationStatus.EMPTY -> "当前没有日志"
        LogcatPresentationStatus.TRUNCATED -> "已达到有界缓冲上限，最早记录已淘汰"
        LogcatPresentationStatus.PARSE_DEGRADED -> "部分记录无法按 threadtime 解析，已保留原始文本"
        LogcatPresentationStatus.STOPPED -> "采集已停止；当前可见内容仍可由你复制或导出"
        LogcatPresentationStatus.ERROR -> "采集发生错误"
    }
    Text(
        text = message,
        color = if (state.droppedOldest || state.parseDegraded || state.error != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun LogcatLevel.displayName(): String = when (this) {
    LogcatLevel.VERBOSE -> "详细 V"
    LogcatLevel.DEBUG -> "调试 D"
    LogcatLevel.INFO -> "信息 I"
    LogcatLevel.WARN -> "警告 W"
    LogcatLevel.ERROR -> "错误 E"
    LogcatLevel.FATAL -> "致命 F"
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
