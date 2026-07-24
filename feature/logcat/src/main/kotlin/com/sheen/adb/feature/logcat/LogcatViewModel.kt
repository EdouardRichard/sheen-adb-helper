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
import com.sheen.adb.core.StructuredLogcatLevel
import com.sheen.adb.data.TextExporter
import com.sheen.adb.data.LogcatShareFileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class LogcatAnalysisStatus {
    DISCONNECTED,
    READY,
    LOADING_PROCESSES,
    CAPTURING,
    PAUSED,
    STOPPED,
    CANCELLED,
    ERROR,
}

data class LogcatUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val processGeneration: Long = 0,
    val isCapturing: Boolean = false,
    val isPaused: Boolean = false,
    val minimumLevel: LogcatLevel = LogcatLevel.INFO,
    val buffers: Set<com.sheen.adb.core.LogcatBuffer> = setOf(
        com.sheen.adb.core.LogcatBuffer.MAIN,
        com.sheen.adb.core.LogcatBuffer.SYSTEM,
        com.sheen.adb.core.LogcatBuffer.CRASH,
    ),
    val levels: Set<StructuredLogcatLevel> = emptySet(),
    val tagQuery: String = "",
    val keyword: String = "",
    val pidQuery: String = "",
    val processQuery: String = "",
    val applicationQuery: String = "",
    val visibleRecords: List<String> = emptyList(),
    val visibleLines: List<String> = emptyList(),
    val droppedOldest: Boolean = false,
    val parseDegraded: Boolean = false,
    val processDegradedReason: String? = null,
    val status: LogcatAnalysisStatus = LogcatAnalysisStatus.DISCONNECTED,
    val error: AdbError? = null,
    val exportNotice: String? = null,
) {
    val filter: LogcatAnalysisFilter
        get() = LogcatAnalysisFilter(levels, tagQuery, keyword, pidQuery, processQuery, applicationQuery)
}

class LogcatViewModel(
    private val manager: AdbSessionManager,
    private val exporter: TextExporter,
    private val shareStore: LogcatShareFileStore? = null,
) : ViewModel() {
    private val rawBuffer = LogcatBuffer(maxBytes = 10 * 1024 * 1024)
    private val mutableState = MutableStateFlow(LogcatUiState())
    val state: StateFlow<LogcatUiState> = mutableState.asStateFlow()
    private var capture: Job? = null
    private var captureGeneration = 0L
    private var foreground = false

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId || connected == null) {
                    stopCapture(clearWindow = true)
                    mutableState.value = mutableState.value.resetForSession(
                        isConnected = connected != null,
                        sessionId = connected?.sessionId,
                    )
                } else {
                    mutableState.update { it.copy(isConnected = true) }
                }
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) stopCapture(clearWindow = true)
    }

    fun setLevel(level: LogcatLevel) {
        if (!mutableState.value.isCapturing) mutableState.update { it.copy(minimumLevel = level) }
    }

    fun toggleBuffer(value: com.sheen.adb.core.LogcatBuffer) {
        if (mutableState.value.isCapturing) return
        mutableState.update { current ->
            val updated = if (value in current.buffers) current.buffers - value else current.buffers + value
            current.copy(buffers = updated.takeIf { it.isNotEmpty() } ?: current.buffers)
        }
    }

    fun toggleAnalysisLevel(value: StructuredLogcatLevel) = Unit
    fun updateTagQuery(value: String) = Unit
    fun updateKeyword(value: String) = Unit
    fun updatePidQuery(value: String) = Unit
    fun updateProcessQuery(value: String) = Unit
    fun updateApplicationQuery(value: String) = Unit
    fun clearFilters() = Unit

    fun start() {
        val current = mutableState.value
        if (!foreground || !current.isConnected || current.isCapturing || current.buffers.isEmpty()) return
        val captureSessionId = current.sessionId ?: return
        val generation = ++captureGeneration
        mutableState.update {
            it.copy(
                isCapturing = true,
                isPaused = false,
                status = LogcatAnalysisStatus.CAPTURING,
                error = null,
                exportNotice = null,
            )
        }
        capture = viewModelScope.launch {
            try {
                collectRaw(captureSessionId, generation, current)
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (isCurrent(captureSessionId, generation)) {
                    mutableState.update { it.stopped() }
                    capture = null
                }
            }
        }
    }

    private suspend fun collectRaw(
        sessionId: String,
        generation: Long,
        initial: LogcatUiState,
    ) {
        val config = LogcatConfig(initial.minimumLevel, initial.buffers)
        withTimeout(10 * 60 * 1000L) {
            manager.streamLogcat(config).collect { result ->
            if (!isCurrent(sessionId, generation)) return@collect
            when (result) {
                is AdbOperationResult.Success -> {
                    rawBuffer.add(result.value.text)
                    publish()
                }
                is AdbOperationResult.Failure -> mutableState.update {
                    it.copy(status = LogcatAnalysisStatus.ERROR, error = result.error)
                }
                AdbOperationResult.Cancelled -> mutableState.update {
                    it.copy(status = LogcatAnalysisStatus.CANCELLED)
                }
            }
            }
        }
    }

    fun stop() = stopCapture(clearWindow = false)

    private fun stopCapture(clearWindow: Boolean) {
        val wasActive = mutableState.value.isCapturing
        captureGeneration += 1
        capture?.cancel()
        capture = null
        if (clearWindow) {
            rawBuffer.clear()
            mutableState.update {
                it.copy(
                    processGeneration = 0,
                    visibleRecords = emptyList(),
                    visibleLines = emptyList(),
                    droppedOldest = false,
                    parseDegraded = false,
                    processDegradedReason = null,
                ).stopped()
            }
        } else {
            mutableState.update { current ->
                current.stopped().copy(
                    status = if (wasActive) LogcatAnalysisStatus.STOPPED else current.status,
                )
            }
            publish()
        }
    }

    fun togglePause() {
        return
    }

    fun clear() {
        rawBuffer.clear()
        publish()
    }

    fun export(target: Uri) {
        val text = visibleText()
        viewModelScope.launch {
            val success = exporter.writeUtf8(target, text)
            mutableState.update {
                it.copy(exportNotice = if (success) "导出完成" else "导出失败，请重新选择位置")
            }
        }
    }

    fun prepareShareFile(): java.io.File? {
        val store = shareStore ?: return null
        val text = mutableState.value.visibleLines.joinToString("\n")
        if (text.isBlank()) return null
        return store.prepare(text, System.currentTimeMillis()).also(store::markChooserOpened).file
    }

    fun visibleText(): String = mutableState.value.visibleLines.joinToString("\n")

    private fun publish() = mutableState.update { current ->
        current.copy(
            visibleRecords = rawBuffer.snapshot(),
            visibleLines = rawBuffer.snapshot(),
            droppedOldest = rawBuffer.droppedOldest,
            parseDegraded = false,
        )
    }

    private fun isCurrent(sessionId: String, generation: Long): Boolean =
        generation == captureGeneration && mutableState.value.sessionId == sessionId

    private inline fun MutableStateFlow<LogcatUiState>.updateIfCurrent(
        sessionId: String,
        generation: Long,
        transform: (LogcatUiState) -> LogcatUiState,
    ) {
        update { if (isCurrent(sessionId, generation)) transform(it) else it }
    }

    override fun onCleared() {
        stopCapture(clearWindow = true)
        super.onCleared()
    }

    private companion object {
    }
}

internal fun LogcatUiState.stopped(): LogcatUiState = copy(
    isCapturing = false,
    isPaused = false,
    status = when (status) {
        LogcatAnalysisStatus.CANCELLED,
        LogcatAnalysisStatus.ERROR,
        LogcatAnalysisStatus.DISCONNECTED,
        -> status
        else -> LogcatAnalysisStatus.STOPPED
    },
)

internal fun LogcatUiState.resetForSession(
    isConnected: Boolean,
    sessionId: String?,
): LogcatUiState = LogcatUiState(
    isConnected = isConnected,
    sessionId = sessionId,
    status = if (isConnected) LogcatAnalysisStatus.READY else LogcatAnalysisStatus.DISCONNECTED,
)
