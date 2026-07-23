package com.sheen.adb.core.internal.pairing

import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.LocalPairingController
import com.sheen.adb.core.LocalPairingControllerState
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingSubmissionDecision
import com.sheen.adb.core.LocalPairingSubmissionRejection
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class LocalPairingCoordinator(
    private val clock: MonotonicClock,
    private val notificationPolicy: LocalPairingNotificationPolicy,
    private val pairObservation: suspend (WirelessServiceObservation, PairingSecret) -> AdbOperationResult<Unit>,
    private val stopDiscovery: () -> Unit,
    private val startGuard: () -> AdbError? = { null },
) : LocalPairingController, AutoCloseable {
    private val lock = Any()
    private val usedAttemptIds = mutableSetOf<PairingAttemptId>()
    private val usedWindowIds = mutableSetOf<LocalPairingWindowId>()
    private val mutableState = MutableStateFlow(LocalPairingControllerState())
    override val state: StateFlow<LocalPairingControllerState> = mutableState.asStateFlow()

    private var selectedObservation: WirelessServiceObservation? = null
    private var deviceUnlocked = false
    private var notificationCapability = LocalPairingNotificationCapability.AVAILABLE
    private var closed = false

    override fun start(
        attemptId: PairingAttemptId,
        windowId: LocalPairingWindowId,
    ): AdbOperationResult<LocalPairingWindow> = synchronized(lock) {
        startGuard()?.let { return@synchronized AdbOperationResult.Failure(it) }
        if (closed) return@synchronized AdbOperationResult.Failure(AdbError.PairingUnsupported)
        if (mutableState.value.window != null) {
            return@synchronized AdbOperationResult.Failure(AdbError.PairingSessionConflict)
        }
        if (!usedAttemptIds.add(attemptId) || !usedWindowIds.add(windowId)) {
            return@synchronized AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }
        val startedAt = clock.nowMillis()
        if (startedAt < 0L || startedAt > Long.MAX_VALUE - LOCAL_PAIRING_WINDOW_MILLIS) {
            return@synchronized AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }
        val window = LocalPairingWindow(
            windowId = windowId,
            attemptId = attemptId,
            startedAtMillis = startedAt,
            deadlineMillis = startedAt + LOCAL_PAIRING_WINDOW_MILLIS,
        )
        selectedObservation = null
        deviceUnlocked = false
        notificationCapability = LocalPairingNotificationCapability.AVAILABLE
        mutableState.value = LocalPairingControllerState(
            window = window,
            discoveryStatus = LocalPairingDiscoveryStatus.SEARCHING,
        )
        AdbOperationResult.Success(window)
    }

    override fun updateNotification(
        deviceUnlocked: Boolean,
        capability: LocalPairingNotificationCapability,
    ): LocalPairingNotificationDecision = synchronized(lock) {
        this.deviceUnlocked = deviceUnlocked
        notificationCapability = capability
        val window = mutableState.value.window
        if (window == null) return@synchronized mutableState.value.notificationDecision ?: unavailableDecision()
        val decision = notificationPolicy.decide(window, clock.nowMillis(), deviceUnlocked, capability)
        mutableState.value = mutableState.value.copy(
            window = window.copy(notificationState = decision.state),
            notificationDecision = decision,
        )
        decision
    }

    override suspend fun submit(
        windowId: LocalPairingWindowId,
        secret: PairingSecret,
    ): AdbOperationResult<Unit> {
        val prepared = synchronized(lock) {
            val window = mutableState.value.window
            val observation = selectedObservation
            if (window == null || observation == null) return@synchronized PreparedSubmission.Rejected(
                rejection = LocalPairingSubmissionRejection.SERVICE_UNAVAILABLE,
            )
            val validation = secret.withChars { code ->
                notificationPolicy.validateSubmission(
                    window = window,
                    submittedWindowId = windowId,
                    nowMillis = clock.nowMillis(),
                    deviceUnlocked = deviceUnlocked,
                    code = code,
                )
            }
            when (validation) {
                is LocalPairingSubmissionDecision.Accepted -> PreparedSubmission.Accepted(
                    windowId = window.windowId,
                    observation = observation,
                    secret = validation.secret,
                )
                is LocalPairingSubmissionDecision.Rejected -> PreparedSubmission.Rejected(validation.reason)
            }
        }
        if (prepared is PreparedSubmission.Rejected) {
            secret.clear()
            return rejectionResult(prepared.rejection)
        }
        prepared as PreparedSubmission.Accepted
        return try {
            val result = pairObservation(prepared.observation, prepared.secret)
            val reason = when (result) {
                is AdbOperationResult.Success -> LocalPairingStopReason.SUCCEEDED
                is AdbOperationResult.Failure -> LocalPairingStopReason.FAILED
                AdbOperationResult.Cancelled -> LocalPairingStopReason.CANCELLED
            }
            finish(prepared.windowId, reason)
            result
        } finally {
            prepared.secret.clear()
            secret.clear()
        }
    }

    suspend fun submit(
        windowId: LocalPairingWindowId,
        code: CharArray,
    ): AdbOperationResult<Unit> = submit(windowId, PairingSecret(code))

    override fun cancel(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
        if (finish(windowId, LocalPairingStopReason.CANCELLED)) {
            AdbOperationResult.Success(Unit)
        } else {
            AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }

    override fun onSystemTimeout(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
        if (finish(windowId, LocalPairingStopReason.SYSTEM_TIMEOUT)) {
            AdbOperationResult.Success(Unit)
        } else {
            AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }

    fun onClockAdvanced() {
        val current = synchronized(lock) { mutableState.value.window } ?: return
        val now = clock.nowMillis()
        if (now >= current.deadlineMillis) {
            finish(current.windowId, LocalPairingStopReason.DEADLINE_REACHED)
            return
        }
        if (now - current.startedAtMillis >= LOCAL_DISCOVERY_INITIAL_RESULT_MILLIS) {
            synchronized(lock) {
                val latest = mutableState.value
                if (latest.window?.windowId == current.windowId && selectedObservation == null &&
                    latest.discoveryStatus == LocalPairingDiscoveryStatus.SEARCHING
                ) {
                    mutableState.value = latest.copy(discoveryStatus = LocalPairingDiscoveryStatus.NOT_FOUND)
                }
            }
        }
    }

    fun onDiscoveryState(discovery: WirelessDiscoveryState) {
        val resolved = discovery.services.filter {
            it.serviceType == WirelessServiceType.PAIRING && it.status == WirelessServiceStatus.RESOLVED
        }
        var lostWindowId: LocalPairingWindowId? = null
        synchronized(lock) {
            val controllerState = mutableState.value
            val window = controllerState.window ?: return
            val selected = selectedObservation
            if (selected != null) {
                val refreshed = resolved.singleOrNull { it.observationId == selected.observationId }
                if (refreshed == null && resolved.size <= 1) {
                    lostWindowId = window.windowId
                } else if (resolved.size > 1) {
                    selectedObservation = null
                    val updatedWindow = window.copy(hasLivePairingService = false)
                    val decision = notificationPolicy.decide(
                        updatedWindow,
                        clock.nowMillis(),
                        deviceUnlocked,
                        notificationCapability,
                    )
                    mutableState.value = controllerState.copy(
                        window = updatedWindow.copy(notificationState = decision.state),
                        discoveryStatus = LocalPairingDiscoveryStatus.AMBIGUOUS,
                        notificationDecision = decision,
                    )
                } else {
                    selectedObservation = refreshed
                }
                return@synchronized
            }
            val (status, observation) = when (resolved.size) {
                0 -> {
                    val elapsed = clock.nowMillis() - window.startedAtMillis
                    val status = if (elapsed >= LOCAL_DISCOVERY_INITIAL_RESULT_MILLIS) {
                        LocalPairingDiscoveryStatus.NOT_FOUND
                    } else {
                        LocalPairingDiscoveryStatus.SEARCHING
                    }
                    status to null
                }
                1 -> LocalPairingDiscoveryStatus.FOUND to resolved.single()
                else -> LocalPairingDiscoveryStatus.AMBIGUOUS to null
            }
            selectedObservation = observation
            val updatedWindow = window.copy(hasLivePairingService = observation != null)
            val decision = notificationPolicy.decide(
                updatedWindow,
                clock.nowMillis(),
                deviceUnlocked,
                notificationCapability,
            )
            mutableState.value = controllerState.copy(
                window = updatedWindow.copy(notificationState = decision.state),
                discoveryStatus = status,
                notificationDecision = decision,
            )
        }
        lostWindowId?.let { finish(it, LocalPairingStopReason.SERVICE_LOST) }
    }

    fun onDiscoveryUnsupported() {
        synchronized(lock) {
            val current = mutableState.value
            val window = current.window ?: return
            selectedObservation = null
            val updatedWindow = window.copy(hasLivePairingService = false)
            val decision = notificationPolicy.decide(
                updatedWindow,
                clock.nowMillis(),
                deviceUnlocked,
                LocalPairingNotificationCapability.INLINE_INPUT_UNAVAILABLE,
            )
            mutableState.value = current.copy(
                window = updatedWindow.copy(notificationState = decision.state),
                discoveryStatus = LocalPairingDiscoveryStatus.UNSUPPORTED,
                notificationDecision = decision,
            )
        }
    }

    fun onDiscoveryFailed() {
        val windowId = synchronized(lock) { mutableState.value.window?.windowId } ?: return
        finish(windowId, LocalPairingStopReason.FAILED)
    }

    fun onSessionChanged() {
        val windowId = synchronized(lock) { mutableState.value.window?.windowId } ?: return
        finish(windowId, LocalPairingStopReason.SESSION_CHANGED)
    }

    override fun close() {
        val windowId = synchronized(lock) {
            closed = true
            mutableState.value.window?.windowId
        }
        if (windowId != null) finish(windowId, LocalPairingStopReason.CANCELLED)
    }

    private fun finish(
        windowId: LocalPairingWindowId,
        reason: LocalPairingStopReason,
    ): Boolean {
        val finished = synchronized(lock) {
            val current = mutableState.value
            val window = current.window
            if (window == null || window.windowId != windowId) return@synchronized false
            val terminalWindow = window.copy(stopReason = reason, hasLivePairingService = false)
            val decision = notificationPolicy.decide(
                terminalWindow,
                clock.nowMillis(),
                deviceUnlocked,
                notificationCapability,
            )
            selectedObservation = null
            mutableState.value = LocalPairingControllerState(
                window = null,
                discoveryStatus = LocalPairingDiscoveryStatus.STOPPED,
                notificationDecision = decision,
                stopReason = reason,
            )
            true
        }
        if (finished) stopDiscovery()
        return finished
    }

    private fun rejectionResult(rejection: LocalPairingSubmissionRejection): AdbOperationResult<Unit> =
        when (rejection) {
            LocalPairingSubmissionRejection.EXPIRED -> {
                AdbOperationResult.Failure(AdbError.Timeout(AdbOperationStage.PAIR))
            }
            else -> AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }

    private fun unavailableDecision(): LocalPairingNotificationDecision = LocalPairingNotificationDecision(
        state = LocalPairingNotificationState.INPUT_UNAVAILABLE,
        inputActionAvailable = false,
        submitAllowed = false,
        actionWindowId = null,
        applicationInputAvailable = false,
        suggestNativeNotificationStyle = false,
        stopReason = mutableState.value.stopReason,
    )

    private sealed interface PreparedSubmission {
        data class Accepted(
            val windowId: LocalPairingWindowId,
            val observation: WirelessServiceObservation,
            val secret: PairingSecret,
        ) : PreparedSubmission

        data class Rejected(
            val rejection: LocalPairingSubmissionRejection,
        ) : PreparedSubmission
    }

    private companion object {
        const val LOCAL_DISCOVERY_INITIAL_RESULT_MILLIS = 5_000L
        const val LOCAL_PAIRING_WINDOW_MILLIS = 120_000L
    }
}
