package com.sheen.adb.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.DeviceOverview
import com.sheen.adb.core.DynamicDeviceMetrics
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.core.QuickActionProgress
import com.sheen.adb.data.ExportDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OverviewUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val overview: DeviceOverview? = null,
    val error: AdbError? = null,
    val sessionId: String? = null,
)

class OverviewViewModel(
    private val manager: AdbSessionManager,
    private val quickActions: QuickActionUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = mutableState.asStateFlow()
    private var foreground = false
    private var polling: Job? = null
    private var load: Job? = null
    private var quickActionJob: Job? = null
    private var quickActionStopJob: Job? = null
    private val quickActionReducer = QuickActionPresentationReducer()
    private val mutableQuickActionState = MutableStateFlow<QuickActionUiState>(QuickActionUiState.Idle)
    val quickActionState: StateFlow<QuickActionUiState> = mutableQuickActionState.asStateFlow()

    init {
        viewModelScope.launch {
            manager.connectionState.collect { connection ->
                val connected = connection as? AdbConnectionState.Connected
                val changed = connected?.sessionId != mutableState.value.sessionId
                mutableState.update {
                    it.copy(
                        isConnected = connected != null,
                        sessionId = connected?.sessionId,
                        overview = if (changed) null else it.overview,
                        error = if (changed) null else it.error,
                    )
                }
                reduceQuickAction(
                    QuickActionPresentationEvent.SessionChanged(connected?.sessionId),
                )
                if (changed) {
                    quickActionJob?.cancel()
                    quickActionStopJob?.cancel()
                }
                if (connected == null) stopWork() else if (foreground) startWork()
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value && mutableState.value.isConnected) startWork() else stopWork()
    }

    fun refresh() {
        val expectedSessionId = mutableState.value.sessionId ?: return
        load?.cancel()
        load = viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            when (val result = manager.loadDeviceOverview()) {
                is AdbOperationResult.Success -> updateForSession(expectedSessionId) {
                    it.copy(isLoading = false, overview = result.value, error = null)
                }
                is AdbOperationResult.Failure -> updateForSession(expectedSessionId) {
                    it.copy(isLoading = false, error = result.error)
                }
                AdbOperationResult.Cancelled -> updateForSession(expectedSessionId) {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    fun requestScreenshot() = startCapture(QuickActionKind.SCREENSHOT)

    fun requestScreenRecord() {
        val running = mutableQuickActionState.value as? QuickActionUiState.Running
        if (running?.kind == QuickActionKind.SCREEN_RECORD) {
            if (!running.isStopping) requestScreenRecordStop(running.sessionId)
            return
        }
        startCapture(QuickActionKind.SCREEN_RECORD)
    }

    fun requestReboot() {
        val sessionId = mutableState.value.sessionId ?: return
        reduceQuickAction(QuickActionPresentationEvent.RebootRequested(sessionId))
    }

    fun dismissRebootConfirmation() {
        val sessionId = mutableState.value.sessionId ?: return
        reduceQuickAction(QuickActionPresentationEvent.RebootConfirmationDismissed(sessionId))
    }

    fun confirmReboot() {
        val sessionId = mutableState.value.sessionId ?: return
        val useCase = quickActions ?: return
        reduceQuickAction(QuickActionPresentationEvent.RebootConfirmed(sessionId))
        if (mutableQuickActionState.value !is QuickActionUiState.Running) return
        quickActionJob = viewModelScope.launch {
            applyUseCaseResult(QuickActionKind.REBOOT, sessionId, useCase.reboot(sessionId))
        }
    }

    fun exportQuickAction(destination: ExportDestination?) {
        val awaiting = mutableQuickActionState.value as? QuickActionUiState.AwaitingExport ?: return
        val useCase = quickActions ?: return
        reduceQuickAction(
            QuickActionPresentationEvent.ExportStarted(awaiting.kind, awaiting.sessionId),
        )
        quickActionJob = viewModelScope.launch {
            when (val result = useCase.export(awaiting.artifact, destination)) {
                is QuickActionExportResult.Succeeded -> reduceQuickAction(
                    QuickActionPresentationEvent.ExportSucceeded(
                        awaiting.kind,
                        awaiting.sessionId,
                        result.destinationName,
                    ),
                )
                QuickActionExportResult.Cancelled -> reduceQuickAction(
                    QuickActionPresentationEvent.Cancelled(
                        awaiting.kind,
                        awaiting.sessionId,
                        "export-cancelled",
                    ),
                )
                is QuickActionExportResult.Failed -> reduceQuickAction(
                    QuickActionPresentationEvent.Failed(
                        awaiting.kind,
                        awaiting.sessionId,
                        result.reason,
                        result.technicalCode,
                    ),
                )
            }
        }
    }

    private fun startWork() {
        if (mutableState.value.overview == null && load?.isActive != true) refresh()
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive && foreground && mutableState.value.isConnected) {
                delay(5_000)
                val expectedSessionId = mutableState.value.sessionId ?: break
                when (val result = manager.refreshDynamicMetrics()) {
                    is AdbOperationResult.Success ->
                        if (mutableState.value.sessionId == expectedSessionId) applyDynamic(result.value)
                    is AdbOperationResult.Failure -> updateForSession(expectedSessionId) {
                        it.copy(error = result.error)
                    }
                    AdbOperationResult.Cancelled -> Unit
                }
            }
        }
    }

    private fun applyDynamic(metrics: DynamicDeviceMetrics) = mutableState.update { current ->
        current.copy(
            overview = current.overview?.copy(
                memoryTotalBytes = metrics.memoryTotalBytes,
                memoryAvailableBytes = metrics.memoryAvailableBytes,
                batteryPercent = metrics.batteryPercent,
                chargingState = metrics.chargingState,
                temperatureCelsius = metrics.temperatureCelsius,
                uptimeSeconds = metrics.uptimeSeconds,
            ),
            error = null,
        )
    }

    private fun stopWork() {
        polling?.cancel()
        polling = null
        load?.cancel()
        load = null
        mutableState.update { it.copy(isLoading = false) }
    }

    private fun startCapture(kind: QuickActionKind) {
        val sessionId = mutableState.value.sessionId ?: return
        val useCase = quickActions ?: return
        reduceQuickAction(QuickActionPresentationEvent.StartRequested(kind, sessionId))
        if (mutableQuickActionState.value !is QuickActionUiState.Running) return
        if (kind == QuickActionKind.SCREEN_RECORD) stopWork()
        quickActionJob = viewModelScope.launch {
            try {
                val result = when (kind) {
                    QuickActionKind.SCREENSHOT ->
                        useCase.captureScreenshot(sessionId) { applyProgress(sessionId, it) }
                    QuickActionKind.SCREEN_RECORD ->
                        useCase.recordScreen(sessionId) { applyProgress(sessionId, it) }
                    QuickActionKind.REBOOT -> return@launch
                }
                applyUseCaseResult(kind, sessionId, result)
            } finally {
                if (kind == QuickActionKind.SCREEN_RECORD &&
                    foreground &&
                    mutableState.value.isConnected
                ) {
                    startWork()
                }
            }
        }
    }

    private fun requestScreenRecordStop(sessionId: String) {
        val useCase = quickActions ?: return
        reduceQuickAction(QuickActionPresentationEvent.ScreenRecordStopRequested(sessionId))
        quickActionStopJob = viewModelScope.launch {
            when (val result = useCase.stopScreenRecord(sessionId)) {
                ScreenRecordStopResult.Requested -> Unit
                ScreenRecordStopResult.Cancelled -> reduceQuickAction(
                    QuickActionPresentationEvent.Cancelled(
                        QuickActionKind.SCREEN_RECORD,
                        sessionId,
                        "stop-cancelled",
                    ),
                )
                is ScreenRecordStopResult.StaleSession -> reduceQuickAction(
                    QuickActionPresentationEvent.Cancelled(
                        QuickActionKind.SCREEN_RECORD,
                        sessionId,
                        "stale-session",
                    ),
                )
                is ScreenRecordStopResult.Failed -> reduceQuickAction(
                    QuickActionPresentationEvent.Failed(
                        QuickActionKind.SCREEN_RECORD,
                        sessionId,
                        result.reason,
                        result.technicalCode,
                    ),
                )
            }
        }
    }

    private fun applyProgress(sessionId: String, progress: QuickActionProgress) {
        val fractionByBytes = progress.maxBytes?.takeIf { it > 0L }?.let {
            progress.bytesWritten.toFloat() / it.toFloat()
        } ?: 0f
        val fractionByTime = progress.maxDuration.inWholeMilliseconds.takeIf { it > 0L }?.let {
            progress.elapsed.inWholeMilliseconds.toFloat() / it.toFloat()
        } ?: 0f
        reduceQuickAction(
            QuickActionPresentationEvent.Progressed(
                kind = progress.kind,
                sessionId = sessionId,
                progress = maxOf(fractionByBytes, fractionByTime),
                elapsedMillis = progress.elapsed.inWholeMilliseconds,
                bytesWritten = progress.bytesWritten,
            ),
        )
    }

    private fun applyUseCaseResult(
        kind: QuickActionKind,
        sessionId: String,
        result: QuickActionUseCaseResult,
    ) {
        val event = when (result) {
            is QuickActionUseCaseResult.ArtifactReady ->
                QuickActionPresentationEvent.ArtifactReady(kind, sessionId, result.artifact)
            QuickActionUseCaseResult.RebootRequested ->
                QuickActionPresentationEvent.Cancelled(kind, sessionId, "device-restarting")
            QuickActionUseCaseResult.Cancelled ->
                QuickActionPresentationEvent.Cancelled(kind, sessionId, "cancelled")
            is QuickActionUseCaseResult.StaleSession ->
                QuickActionPresentationEvent.Cancelled(kind, sessionId, "stale-session")
            is QuickActionUseCaseResult.ResultUnknown ->
                QuickActionPresentationEvent.ResultUnknown(
                    kind,
                    sessionId,
                    "device-disconnected-after-request",
                )
            is QuickActionUseCaseResult.Failed ->
                QuickActionPresentationEvent.Failed(
                    kind,
                    sessionId,
                    result.reason,
                    result.technicalCode,
                )
        }
        reduceQuickAction(event)
    }

    private fun reduceQuickAction(event: QuickActionPresentationEvent) {
        mutableQuickActionState.update { quickActionReducer.reduce(it, event) }
    }

    private inline fun updateForSession(
        expectedSessionId: String,
        transform: (OverviewUiState) -> OverviewUiState,
    ) {
        mutableState.update { current ->
            if (current.sessionId == expectedSessionId) transform(current) else current
        }
    }

    override fun onCleared() {
        quickActionStopJob?.cancel()
        quickActionJob?.cancel()
        stopWork()
        super.onCleared()
    }
}
