package com.sheen.adb.feature.logcat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.LogcatConfig
import com.sheen.adb.core.LogcatLevel
import com.sheen.adb.data.TextExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LogcatUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val isCapturing: Boolean = false,
    val isPaused: Boolean = false,
    val minimumLevel: LogcatLevel = LogcatLevel.INFO,
    val buffers: Set<com.sheen.adb.core.LogcatBuffer> = setOf(
        com.sheen.adb.core.LogcatBuffer.MAIN,
        com.sheen.adb.core.LogcatBuffer.SYSTEM,
        com.sheen.adb.core.LogcatBuffer.CRASH,
    ),
    val keyword: String = "",
    val visibleLines: List<String> = emptyList(),
    val droppedOldest: Boolean = false,
    val error: AdbError? = null,
    val exportNotice: String? = null,
)

class LogcatViewModel(
    private val manager: AdbSessionManager,
    private val exporter: TextExporter,
) : ViewModel() {
    private val window = LogcatWindow()
    private val mutableState = MutableStateFlow(LogcatUiState())
    val state: StateFlow<LogcatUiState> = mutableState.asStateFlow()
    private var capture: Job? = null
    private var captureGeneration = 0L
    private var foreground = false

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId) {
                    stop()
                    window.reset()
                    mutableState.value = mutableState.value.resetForSession(
                        isConnected = connected != null,
                        sessionId = connected?.sessionId,
                    )
                } else if (connected == null) {
                    stop()
                    window.reset()
                    mutableState.value = mutableState.value.resetForSession(false, null)
                } else mutableState.update { it.copy(isConnected = true) }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground = value; if (!value) stop() }
    fun setLevel(level: LogcatLevel) { if (!mutableState.value.isCapturing) mutableState.update { it.copy(minimumLevel = level) } }
    fun toggleBuffer(value: com.sheen.adb.core.LogcatBuffer) {
        if (mutableState.value.isCapturing) return
        mutableState.update { current ->
            val updated = if (value in current.buffers) current.buffers - value else current.buffers + value
            current.copy(buffers = updated.takeIf { it.isNotEmpty() } ?: current.buffers)
        }
    }
    fun updateKeyword(value: String) {
        window.updateKeyword(value)
        mutableState.update { it.copy(keyword = value) }
        publish()
    }

    fun start() {
        val current = mutableState.value
        if (!foreground || !current.isConnected || current.isCapturing || current.buffers.isEmpty()) return
        val captureSessionId = current.sessionId ?: return
        val generation = ++captureGeneration
        window.resume()
        mutableState.update { it.copy(isCapturing = true, isPaused = false, error = null, exportNotice = null) }
        capture = viewModelScope.launch {
            val config = LogcatConfig(current.minimumLevel, current.buffers)
            try {
                manager.streamLogcat(config).collect { result ->
                    if (generation != captureGeneration || mutableState.value.sessionId != captureSessionId) {
                        return@collect
                    }
                    when (result) {
                        is AdbOperationResult.Success -> {
                            val prefix = if (result.value.fromStandardError) "[stderr] " else ""
                            window.add(prefix + result.value.text)
                            if (!mutableState.value.isPaused) publish()
                        }
                        is AdbOperationResult.Failure -> mutableState.update { it.copy(error = result.error) }
                        AdbOperationResult.Cancelled -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (generation == captureGeneration && mutableState.value.sessionId == captureSessionId) {
                    mutableState.update { it.stopped() }
                    capture = null
                }
            }
        }
    }

    fun stop() {
        captureGeneration++
        capture?.cancel()
        capture = null
        mutableState.update { it.stopped() }
        publish()
    }

    fun togglePause() {
        if (mutableState.value.isPaused) {
            window.resume()
            mutableState.update { it.copy(isPaused = false) }
            publish()
        } else {
            window.pause()
            mutableState.update { it.copy(isPaused = true) }
        }
    }

    fun clear() { window.clear(); publish() }

    fun export(target: Uri) {
        val text = mutableState.value.visibleLines.joinToString("\n")
        viewModelScope.launch {
            val success = exporter.writeUtf8(target, text)
            mutableState.update { it.copy(exportNotice = if (success) "导出完成" else "导出失败，请重新选择位置") }
        }
    }

    fun visibleText(): String = mutableState.value.visibleLines.joinToString("\n")

    private fun publish() = mutableState.update { current ->
        current.copy(
            visibleLines = window.snapshot(),
            droppedOldest = window.droppedOldest,
        )
    }

    override fun onCleared() { stop(); window.reset(); super.onCleared() }
}

internal fun LogcatUiState.stopped(): LogcatUiState = copy(isCapturing = false, isPaused = false)

internal fun LogcatUiState.resetForSession(
    isConnected: Boolean,
    sessionId: String?,
): LogcatUiState = LogcatUiState(isConnected = isConnected, sessionId = sessionId)
