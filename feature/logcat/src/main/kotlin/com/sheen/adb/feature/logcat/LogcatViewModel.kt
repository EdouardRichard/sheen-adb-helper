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
import com.sheen.adb.core.StructuredLogcatKind
import com.sheen.adb.core.StructuredLogcatLevel
import com.sheen.adb.data.LogcatShareFileStore
import com.sheen.adb.data.LogcatOutputMode
import com.sheen.adb.data.LogcatOutputPhase
import com.sheen.adb.data.LogcatOutputSaveResult
import com.sheen.adb.data.LogcatOutputSnapshot
import com.sheen.adb.data.LogcatOutputStore
import com.sheen.adb.data.TextExporter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

enum class LogcatAnalysisStatus {
    NEVER_STARTED,
    READY,
    STARTING,
    LOADING_PROCESSES,
    COLLECTING,
    CAPTURING,
    PAUSED,
    STOPPED,
    LIMIT_TIME,
    LIMIT_BYTES,
    CANCELLED,
    DISCONNECTED,
    UNSUPPORTED,
    OUTCOME_UNKNOWN,
    ERROR,
}

enum class LogcatSaveStatus {
    IDLE,
    WRITING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    OUTCOME_UNKNOWN,
}

data class LogcatUiState(
    val isConnected: Boolean = false,
    val sessionId: String? = null,
    val windowId: String? = null,
    val processGeneration: Long = 0,
    val isCapturing: Boolean = false,
    val isPaused: Boolean = false,
    val minimumLevel: LogcatLevel = LogcatLevel.VERBOSE,
    val buffers: Set<com.sheen.adb.core.LogcatBuffer> = setOf(
        com.sheen.adb.core.LogcatBuffer.MAIN,
        com.sheen.adb.core.LogcatBuffer.SYSTEM,
        com.sheen.adb.core.LogcatBuffer.CRASH,
    ),
    val displayLevel: LogcatDisplayLevel = LogcatDisplayLevel.ALL,
    val textFilter: String = "",
    val levels: Set<StructuredLogcatLevel> = emptySet(),
    val tagQuery: String = "",
    val keyword: String = "",
    val pidQuery: String = "",
    val processQuery: String = "",
    val applicationQuery: String = "",
    val rawWindowSnapshot: List<String> = emptyList(),
    val visibleRecords: List<String> = emptyList(),
    val visibleLines: List<String> = emptyList(),
    val droppedOldest: Boolean = false,
    val parseDegraded: Boolean = false,
    val processDegradedReason: String? = null,
    val status: LogcatAnalysisStatus = LogcatAnalysisStatus.DISCONNECTED,
    val error: AdbError? = null,
    val saveStatus: LogcatSaveStatus = LogcatSaveStatus.IDLE,
    val saveTaskId: String? = null,
    val exportNotice: String? = null,
) {
    val filter: LogcatAnalysisFilter
        get() = LogcatAnalysisFilter(levels, tagQuery, keyword, pidQuery, processQuery, applicationQuery)

    val isSaveWriting: Boolean
        get() = saveStatus == LogcatSaveStatus.WRITING
}

class LogcatViewModel(
    private val manager: AdbSessionManager,
    private val exporter: TextExporter,
    private val shareStore: LogcatShareFileStore? = null,
    private val outputStore: LogcatOutputStore? = null,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val taskScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(LogcatUiState())
    val state: StateFlow<LogcatUiState> = mutableState.asStateFlow()

    private var analysisWindow: LogcatAnalysisWindow? = null
    private var capture: Job? = null
    private var save: Job? = null
    private var pendingSaveSnapshot: LogcatOutputSnapshot? = null
    private var captureGeneration = 0L
    private var foreground = false
    private var pageVisible = false

    init {
        taskScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                if (connected?.sessionId != mutableState.value.sessionId || connected == null) {
                    stopCapture(clearWindow = true)
                    cancelSave()
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
        if (!value) {
            stopCapture(clearWindow = false)
            cancelSave()
        }
    }

    fun onPageVisible(value: Boolean) {
        pageVisible = value
        if (!value) stopCapture(clearWindow = false)
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

    fun setDisplayLevel(value: LogcatDisplayLevel) {
        analysisWindow?.updateDisplayLevel(value)
        mutableState.update { it.copy(displayLevel = value) }
        publishWindow()
    }

    fun updateTextFilter(value: String) {
        analysisWindow?.updateTextFilter(value)
        mutableState.update { it.copy(textFilter = value) }
        publishWindow()
    }

    fun toggleAnalysisLevel(value: StructuredLogcatLevel) {
        val current = mutableState.value
        val levels = if (value in current.levels) current.levels - value else current.levels + value
        updateLegacyFilter(current.filter.copy(levels = levels))
    }

    fun updateTagQuery(value: String) = updateLegacyFilter(mutableState.value.filter.copy(tagQuery = value))

    fun updateKeyword(value: String) = updateLegacyFilter(mutableState.value.filter.copy(keyword = value))

    fun updatePidQuery(value: String) = updateLegacyFilter(mutableState.value.filter.copy(pidQuery = value))

    fun updateProcessQuery(value: String) = updateLegacyFilter(mutableState.value.filter.copy(processQuery = value))

    fun updateApplicationQuery(value: String) =
        updateLegacyFilter(mutableState.value.filter.copy(applicationQuery = value))

    fun clearFilters() {
        analysisWindow?.updateFilter(LogcatAnalysisFilter())
        analysisWindow?.updateTextFilter("")
        analysisWindow?.updateDisplayLevel(LogcatDisplayLevel.ALL)
        mutableState.update {
            it.copy(
                displayLevel = LogcatDisplayLevel.ALL,
                textFilter = "",
                levels = emptySet(),
                tagQuery = "",
                keyword = "",
                pidQuery = "",
                processQuery = "",
                applicationQuery = "",
            )
        }
        publishWindow()
    }

    fun start() {
        val current = mutableState.value
        if (!foreground || !pageVisible || !current.isConnected || current.isCapturing ||
            current.buffers.isEmpty() || current.isSaveWriting
        ) {
            return
        }
        val captureSessionId = current.sessionId ?: return
        val generation = ++captureGeneration
        val windowId = UUID.randomUUID().toString()
        analysisWindow = null
        mutableState.update {
            it.copy(
                windowId = windowId,
                processGeneration = 0,
                isCapturing = true,
                isPaused = false,
                rawWindowSnapshot = emptyList(),
                visibleRecords = emptyList(),
                visibleLines = emptyList(),
                droppedOldest = false,
                parseDegraded = false,
                processDegradedReason = null,
                status = LogcatAnalysisStatus.STARTING,
                error = null,
                saveStatus = LogcatSaveStatus.IDLE,
                saveTaskId = null,
                exportNotice = null,
            )
        }
        capture = taskScope.launch {
            try {
                collectStructured(captureSessionId, generation, current)
            } catch (_: LogcatByteLimitReached) {
                updateIfCurrent(captureSessionId, generation) {
                    it.copy(
                        isCapturing = false,
                        status = LogcatAnalysisStatus.LIMIT_BYTES,
                    )
                }
            } catch (_: TimeoutCancellationException) {
                updateIfCurrent(captureSessionId, generation) {
                    it.copy(
                        isCapturing = false,
                        status = LogcatAnalysisStatus.LIMIT_TIME,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (isCurrent(captureSessionId, generation)) {
                    mutableState.update { currentState ->
                        if (currentState.isCapturing) currentState.stopped() else currentState
                    }
                    capture = null
                }
            }
        }
    }

    private suspend fun collectStructured(
        sessionId: String,
        generation: Long,
        initial: LogcatUiState,
    ) {
        updateIfCurrent(sessionId, generation) { it.copy(status = LogcatAnalysisStatus.LOADING_PROCESSES) }
        val processSnapshot = when (val result = manager.loadProcessAnalysis(sessionId)) {
            is AdbOperationResult.Success -> result.value
            is AdbOperationResult.Failure -> {
                updateIfCurrent(sessionId, generation) { it.failure(result.error) }
                return
            }
            AdbOperationResult.Cancelled -> {
                updateIfCurrent(sessionId, generation) {
                    it.copy(isCapturing = false, status = LogcatAnalysisStatus.CANCELLED)
                }
                return
            }
        }
        if (!isCurrent(sessionId, generation)) return

        analysisWindow = LogcatAnalysisWindow(
            sessionId = sessionId,
            processGeneration = processSnapshot.generation,
            maxLines = MAX_RECORDS,
            maxBytes = MAX_CAPTURE_BYTES,
            visibleLimit = MAX_RECORDS,
        ).also { window ->
            window.updateDisplayLevel(mutableState.value.displayLevel)
            window.updateTextFilter(mutableState.value.textFilter)
            window.updateFilter(mutableState.value.filter)
        }
        updateIfCurrent(sessionId, generation) {
            it.copy(
                processGeneration = processSnapshot.generation,
                processDegradedReason = processSnapshot.degradedReason,
                status = LogcatAnalysisStatus.COLLECTING,
            )
        }

        val config = LogcatConfig(LogcatLevel.VERBOSE, initial.buffers)
        withTimeout(MAX_CAPTURE_MILLIS) {
            manager.streamStructuredLogcat(config, sessionId, processSnapshot.generation).collect { result ->
                if (!isCurrent(sessionId, generation)) return@collect
                when (result) {
                    is AdbOperationResult.Success -> {
                        val accepted = analysisWindow?.add(result.value) == true
                        if (accepted) {
                            publishWindow()
                            if (analysisWindow?.droppedOldest == true) throw LogcatByteLimitReached
                        }
                    }
                    is AdbOperationResult.Failure -> {
                        updateIfCurrent(sessionId, generation) { it.failure(result.error) }
                        return@collect
                    }
                    AdbOperationResult.Cancelled -> {
                        updateIfCurrent(sessionId, generation) {
                            it.copy(isCapturing = false, status = LogcatAnalysisStatus.CANCELLED)
                        }
                        return@collect
                    }
                }
            }
        }
    }

    fun stop() = stopCapture(clearWindow = false)

    private fun stopCapture(clearWindow: Boolean) {
        val current = mutableState.value
        val wasActive = current.isCapturing
        captureGeneration += 1
        capture?.cancel()
        capture = null
        if (clearWindow) {
            analysisWindow = null
            mutableState.update {
                it.copy(
                    windowId = null,
                    processGeneration = 0,
                    isCapturing = false,
                    isPaused = false,
                    rawWindowSnapshot = emptyList(),
                    visibleRecords = emptyList(),
                    visibleLines = emptyList(),
                    droppedOldest = false,
                    parseDegraded = false,
                    processDegradedReason = null,
                )
            }
        } else {
            mutableState.update { state ->
                if (wasActive) state.stopped() else state.copy(isCapturing = false, isPaused = false)
            }
            publishWindow()
        }
    }

    fun togglePause() = Unit

    fun clear() {
        analysisWindow?.clear()
        publishWindow()
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun export(target: Uri) {
        val records = mutableState.value.rawWindowSnapshot.toList()
        if (records.isEmpty() || mutableState.value.isSaveWriting) return
        val taskId = UUID.randomUUID().toString()
        save = taskScope.launch {
            mutableState.update {
                it.copy(
                    saveStatus = LogcatSaveStatus.WRITING,
                    saveTaskId = taskId,
                    exportNotice = null,
                )
            }
            val success = exporter.writeUtf8(target, records.joinToString("\n"))
            mutableState.update { current ->
                if (current.saveTaskId != taskId) {
                    current
                } else {
                    current.copy(
                        saveStatus = if (success) LogcatSaveStatus.SUCCEEDED else LogcatSaveStatus.FAILED,
                        saveTaskId = null,
                    )
                }
            }
            save = null
        }
    }

    fun prepareSaveSelection(): Boolean {
        val current = mutableState.value
        val windowId = current.windowId ?: return false
        if (current.rawWindowSnapshot.isEmpty() || current.isSaveWriting || save != null) return false
        pendingSaveSnapshot = LogcatOutputSnapshot.from(
            windowId = windowId,
            records = current.rawWindowSnapshot,
        )
        mutableState.update {
            it.copy(
                saveStatus = LogcatSaveStatus.IDLE,
                saveTaskId = null,
                exportNotice = null,
            )
        }
        return true
    }

    fun onSaveTreeSelected(treeId: String?) {
        val snapshot = pendingSaveSnapshot
        pendingSaveSnapshot = null
        if (treeId == null) {
            mutableState.update {
                it.copy(saveStatus = LogcatSaveStatus.CANCELLED, saveTaskId = null)
            }
            return
        }
        val store = outputStore
        val expectedSessionId = mutableState.value.sessionId
        if (snapshot == null || store == null || expectedSessionId == null || save != null) {
            mutableState.update {
                it.copy(saveStatus = LogcatSaveStatus.FAILED, saveTaskId = null)
            }
            return
        }

        val taskId = UUID.randomUUID().toString()
        save = taskScope.launch {
            try {
                val result = runInterruptible(Dispatchers.IO) {
                    store.save(
                        treeId = treeId,
                        mode = LogcatOutputMode.SNAPSHOT,
                        snapshot = snapshot,
                        onPhase = { phase ->
                            updateSavePhase(
                                taskId = taskId,
                                expectedSessionId = expectedSessionId,
                                phase = phase,
                            )
                        },
                    )
                }
                mutableState.update { current ->
                    if (current.sessionId != expectedSessionId || current.saveTaskId != taskId) {
                        current
                    } else {
                        current.copy(
                            saveStatus = when (result) {
                                is LogcatOutputSaveResult.Success -> LogcatSaveStatus.SUCCEEDED
                                is LogcatOutputSaveResult.Failure -> LogcatSaveStatus.FAILED
                                LogcatOutputSaveResult.Cancelled -> LogcatSaveStatus.CANCELLED
                            },
                            saveTaskId = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                mutableState.update { current ->
                    if (current.saveTaskId == taskId) {
                        current.copy(
                            saveStatus = LogcatSaveStatus.CANCELLED,
                            saveTaskId = null,
                        )
                    } else {
                        current
                    }
                }
                throw error
            } finally {
                save = null
            }
        }
    }

    fun cancelSave() {
        val hadPendingSelection = pendingSaveSnapshot != null
        pendingSaveSnapshot = null
        val activeSave = save
        if (!hadPendingSelection && activeSave == null) return
        if (activeSave != null) {
            // Keep the write lock until runInterruptible has returned and the store has cleaned
            // its staged target; the job's cancellation handler publishes the safe terminal state.
            activeSave.cancel()
        } else {
            mutableState.update {
                it.copy(saveStatus = LogcatSaveStatus.CANCELLED, saveTaskId = null)
            }
        }
    }

    fun prepareShareFile(): java.io.File? {
        val store = shareStore ?: return null
        val text = rawWindowText()
        if (text.isBlank()) return null
        return store.prepare(text, System.currentTimeMillis()).also(store::markChooserOpened).file
    }

    fun visibleText(): String = mutableState.value.visibleLines.joinToString("\n")

    fun rawWindowText(): String = mutableState.value.rawWindowSnapshot.joinToString("\n")

    private fun updateLegacyFilter(filter: LogcatAnalysisFilter) {
        analysisWindow?.updateFilter(filter)
        mutableState.update {
            it.copy(
                levels = filter.levels,
                tagQuery = filter.tagQuery,
                keyword = filter.keyword,
                pidQuery = filter.pidQuery,
                processQuery = filter.processQuery,
                applicationQuery = filter.applicationQuery,
            )
        }
        publishWindow()
    }

    private fun publishWindow() {
        val window = analysisWindow
        if (window == null) return
        val raw = window.rawSnapshot()
        val visible = window.visibleSnapshot()
        mutableState.update {
            it.copy(
                rawWindowSnapshot = raw.map { record -> record.rawText },
                visibleRecords = visible.map { record -> record.rawText },
                visibleLines = visible.map { record -> record.rawText },
                droppedOldest = window.droppedOldest,
                parseDegraded = raw.any { record -> record.kind != StructuredLogcatKind.PARSED },
            )
        }
    }

    private fun updateSavePhase(
        taskId: String,
        expectedSessionId: String,
        phase: LogcatOutputPhase,
    ) {
        mutableState.update { current ->
            if (current.sessionId != expectedSessionId ||
                (current.saveTaskId != null && current.saveTaskId != taskId)
            ) {
                current
            } else {
                current.copy(
                    saveStatus = if (phase.persistentIoStarted) {
                        LogcatSaveStatus.WRITING
                    } else {
                        current.saveStatus
                    },
                    saveTaskId = if (phase.persistentIoStarted) taskId else current.saveTaskId,
                )
            }
        }
    }

    private fun isCurrent(sessionId: String, generation: Long): Boolean =
        generation == captureGeneration && mutableState.value.sessionId == sessionId

    private inline fun updateIfCurrent(
        sessionId: String,
        generation: Long,
        transform: (LogcatUiState) -> LogcatUiState,
    ) {
        mutableState.update { if (isCurrent(sessionId, generation)) transform(it) else it }
    }

    override fun onCleared() {
        stopCapture(clearWindow = true)
        cancelSave()
        super.onCleared()
    }

    private object LogcatByteLimitReached : RuntimeException()

    private companion object {
        const val MAX_CAPTURE_BYTES = 10 * 1024 * 1024
        const val MAX_CAPTURE_MILLIS = 10 * 60 * 1000L
        const val MAX_RECORDS = 100_000
    }
}

private fun LogcatUiState.failure(error: AdbError): LogcatUiState = copy(
    isCapturing = false,
    status = when (error) {
        is AdbError.ProtocolIncompatible -> LogcatAnalysisStatus.UNSUPPORTED
        is AdbError.RemoteClosed -> LogcatAnalysisStatus.DISCONNECTED
        else -> if (error.technicalCode.contains("OUTCOME_UNKNOWN")) {
            LogcatAnalysisStatus.OUTCOME_UNKNOWN
        } else {
            LogcatAnalysisStatus.ERROR
        }
    },
    error = error,
)

internal fun LogcatUiState.stopped(): LogcatUiState = copy(
    isCapturing = false,
    isPaused = false,
    status = when (status) {
        LogcatAnalysisStatus.CANCELLED,
        LogcatAnalysisStatus.ERROR,
        LogcatAnalysisStatus.DISCONNECTED,
        LogcatAnalysisStatus.UNSUPPORTED,
        LogcatAnalysisStatus.OUTCOME_UNKNOWN,
        LogcatAnalysisStatus.LIMIT_TIME,
        LogcatAnalysisStatus.LIMIT_BYTES,
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
    status = if (isConnected) LogcatAnalysisStatus.NEVER_STARTED else LogcatAnalysisStatus.DISCONNECTED,
)
