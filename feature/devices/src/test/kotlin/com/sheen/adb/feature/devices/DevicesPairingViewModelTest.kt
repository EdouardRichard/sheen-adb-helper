package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.LocalPairingController
import com.sheen.adb.core.LocalPairingControllerState
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.QrPairingMaterial
import com.sheen.adb.core.WirelessDiscoveryMode
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.data.DeviceProfileRepository
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesPairingViewModelTest {
    @Test
    fun `QR manager flow reaches success without creating a connection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val material = FakeMaterial(PairingAttemptId.of("attempt-success"))
            val discovery = manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
            assertTrue(viewModel.pairingState.value.qrMatrix != null)

            discovery.emit(AdbOperationResult.Success(discoveryState(1, resolvedObservation("observation-success"))))
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.SUCCEEDED)
            assertNull(viewModel.pairingState.value.qrMatrix)
            assertNull(material.payload)
            assertEquals(manager.qrPairCalls, listOf(material.attemptId))
            assertEquals(manager.connectCalls, 0)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry rejects a late result from the previous generation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val oldMaterial = FakeMaterial(PairingAttemptId.of("attempt-old"))
            val newMaterial = FakeMaterial(PairingAttemptId.of("attempt-new"))
            val oldDiscovery = manager.enqueueQrAttempt(oldMaterial)
            manager.enqueueQrAttempt(newMaterial)
            val viewModel = viewModel(manager, listOf(oldMaterial.attemptId, newMaterial.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            viewModel.retryPairing()
            advanceUntilIdle()

            oldDiscovery.emit(AdbOperationResult.Success(discoveryState(1, resolvedObservation("observation-late"))))
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
            assertTrue(viewModel.pairingState.value.qrMatrix != null)
            assertTrue(oldMaterial.attemptId in manager.cancelledAttempts)
            assertNull(oldMaterial.payload)
            assertTrue(newMaterial.payload != null)
            assertTrue(manager.qrPairCalls.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `leaving pairing cancels the active attempt and clears sensitive display state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val material = FakeMaterial(PairingAttemptId.of("attempt-leave"))
            manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            viewModel.onPairingPageLeft()
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertNull(viewModel.pairingState.value.qrMatrix)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertTrue(material.attemptId in manager.cancelledAttempts)
            assertNull(material.payload)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry cleans the old material before creating a fresh attempt`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val first = FakeMaterial(PairingAttemptId.of("attempt-first"))
            val second = FakeMaterial(PairingAttemptId.of("attempt-second"))
            manager.enqueueQrAttempt(first)
            manager.enqueueQrAttempt(second)
            val viewModel = viewModel(manager, listOf(first.attemptId, second.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            viewModel.retryPairing()
            advanceUntilIdle()

            assertEquals(manager.createdAttempts, listOf(first.attemptId, second.attemptId))
            assertTrue(first.attemptId in manager.cancelledAttempts)
            assertNull(first.payload)
            assertTrue(second.payload != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `active session is preserved until explicit replacement confirmation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager(
                initialConnectionState = AdbConnectionState.Connected(
                    endpoint = AdbEndpoint("synthetic.invalid", 4711),
                    sessionId = "session-synthetic",
                ),
            )
            val material = FakeMaterial(PairingAttemptId.of("attempt-after-confirmation"))
            manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))
            runCurrent()

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()

            assertTrue(viewModel.pairingState.value.awaitingSessionReplacementConfirmation)
            assertEquals(manager.disconnectCalls, 0)
            assertTrue(manager.createdAttempts.isEmpty())

            viewModel.confirmPairingSessionReplacement()
            advanceUntilIdle()

            assertEquals(manager.disconnectCalls, 1)
            assertEquals(manager.createdAttempts, listOf(material.attemptId))
            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `existing six digit entry still pairs and clears reducer input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(manager, emptyList())

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingEndpoint("synthetic.invalid:4711")
            viewModel.updatePairingCode("0".repeat(6))
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.SUCCEEDED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
            assertEquals(manager.codePairCalls, 1)
            assertFalse(viewModel.pairingState.value.hasActiveSession)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local entry starts one controller window and maps its discovery and notification flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-local")),
                windowIds = listOf(LocalPairingWindowId.of("window-local")),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            runCurrent()

            assertEquals(manager.localController.startedWindows.size, 1)
            assertEquals(viewModel.pairingState.value.localDiscoveryStatus, LocalPairingDiscoveryStatus.SEARCHING)
            assertEquals(viewModel.state.value.notificationPermissionRequestGeneration, 1L)

            manager.localController.publish(
                windowId = manager.localController.startedWindows.single().second,
                discoveryStatus = LocalPairingDiscoveryStatus.AMBIGUOUS,
                notificationDecision = notificationDecision(
                    LocalPairingNotificationState.INPUT_UNAVAILABLE,
                    suggestNativeStyle = true,
                ),
            )
            runCurrent()

            assertTrue(viewModel.pairingState.value.requiresLocalTargetSelection)
            assertTrue(viewModel.pairingState.value.applicationInputAvailable)
            assertEquals(
                viewModel.pairingState.value.localNotificationState,
                LocalPairingNotificationState.INPUT_UNAVAILABLE,
            )
            assertTrue(viewModel.pairingState.value.suggestNativeNotificationStyle)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local retry invalidates the old window and application submit uses the replacement window`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val oldWindowId = LocalPairingWindowId.of("window-old")
            val newWindowId = LocalPairingWindowId.of("window-new")
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(
                    PairingAttemptId.of("attempt-old"),
                    PairingAttemptId.of("attempt-new"),
                ),
                windowIds = listOf(oldWindowId, newWindowId),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            viewModel.retryLocalPairingMode()
            runCurrent()

            manager.localController.publish(
                windowId = oldWindowId,
                discoveryStatus = LocalPairingDiscoveryStatus.NOT_FOUND,
            )
            runCurrent()
            assertEquals(viewModel.pairingState.value.localDiscoveryStatus, LocalPairingDiscoveryStatus.SEARCHING)

            viewModel.updatePairingCode("0".repeat(6))
            viewModel.submitLocalPairingCode()
            advanceUntilIdle()

            assertEquals(manager.localController.cancelledWindows, listOf(oldWindowId))
            assertEquals(manager.localController.submittedWindows, listOf(newWindowId))
            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.SUCCEEDED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local notification permission request is one shot across an in app retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(
                    PairingAttemptId.of("attempt-first"),
                    PairingAttemptId.of("attempt-retry"),
                ),
                windowIds = listOf(
                    LocalPairingWindowId.of("window-first"),
                    LocalPairingWindowId.of("window-retry"),
                ),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            viewModel.onLocalNotificationPermissionResult(granted = false)
            viewModel.retryLocalPairingMode()
            runCurrent()

            assertEquals(viewModel.state.value.notificationPermissionRequestGeneration, 1L)
            assertEquals(
                viewModel.pairingState.value.localNotificationState,
                LocalPairingNotificationState.HIDDEN,
            )
            assertTrue(viewModel.pairingState.value.applicationInputAvailable)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local terminal result ignores a late controller update after the session window is gone`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val windowId = LocalPairingWindowId.of("window-timeout")
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-timeout")),
                windowIds = listOf(windowId),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            runCurrent()
            viewModel.onPairingPageLeft()
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            val discoveryBeforeLateEvent = viewModel.pairingState.value.localDiscoveryStatus
            manager.localController.publish(
                windowId = windowId,
                discoveryStatus = LocalPairingDiscoveryStatus.FOUND,
                notificationDecision = notificationDecision(LocalPairingNotificationState.INPUT_READY),
            )
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertFalse(viewModel.pairingState.value.localWindowActive)
            assertEquals(viewModel.pairingState.value.localDiscoveryStatus, discoveryBeforeLateEvent)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        manager: FakeManager,
        attemptIds: List<PairingAttemptId>,
        windowIds: List<LocalPairingWindowId> = emptyList(),
    ): DevicesViewModel {
        val ids = ArrayDeque(attemptIds)
        val localWindowIds = ArrayDeque(windowIds)
        return DevicesViewModel(
            manager = manager.instance,
            repository = fakeRepository(),
            clock = Clock.systemUTC(),
            pairingReducer = DevicesPairingReducer(),
            qrEncoder = QrMatrixEncoder(),
            pairingAttemptIdFactory = { ids.removeFirst() },
            localPairingWindowIdFactory = { localWindowIds.removeFirst() },
        )
    }

    private fun notificationDecision(
        state: LocalPairingNotificationState,
        suggestNativeStyle: Boolean = false,
    ): LocalPairingNotificationDecision = LocalPairingNotificationDecision(
        state = state,
        inputActionAvailable = state == LocalPairingNotificationState.INPUT_READY,
        submitAllowed = state == LocalPairingNotificationState.INPUT_READY,
        actionWindowId = null,
        applicationInputAvailable = true,
        suggestNativeNotificationStyle = suggestNativeStyle,
    )

    private fun discoveryState(
        generation: Long,
        observation: WirelessServiceObservation,
    ): WirelessDiscoveryState = WirelessDiscoveryState(generation = generation, services = listOf(observation))

    private fun resolvedObservation(id: String): WirelessServiceObservation = WirelessServiceObservation(
        observationId = WirelessObservationId(id),
        serviceType = WirelessServiceType.PAIRING,
        serviceName = "synthetic-service",
        addresses = emptyList(),
        port = 4711,
        status = WirelessServiceStatus.RESOLVED,
        lastSeenAt = 1L,
    )

    private fun fakeRepository(): DeviceProfileRepository = Proxy.newProxyInstance(
        DeviceProfileRepository::class.java.classLoader,
        arrayOf(DeviceProfileRepository::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getProfiles" -> flowOf(emptyList<Any>())
            "rename" -> false
            else -> null
        }
    } as DeviceProfileRepository

    private class FakeMaterial(
        override val attemptId: PairingAttemptId,
    ) : QrPairingMaterial {
        override val deadlineMillis: Long = Long.MAX_VALUE
        override var payload: String? = "synthetic-qr-payload"
            private set

        fun invalidate() {
            payload = null
        }
    }

    private class FakeManager(
        initialConnectionState: AdbConnectionState = AdbConnectionState.Disconnected(),
    ) {
        val connectionState = MutableStateFlow(initialConnectionState)
        val diagnostics = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        val createdAttempts = mutableListOf<PairingAttemptId>()
        val cancelledAttempts = mutableListOf<PairingAttemptId>()
        val qrPairCalls = mutableListOf<PairingAttemptId>()
        var connectCalls = 0
        var disconnectCalls = 0
        var codePairCalls = 0
        val localController = FakeLocalPairingController()

        private val queuedMaterials = ArrayDeque<FakeMaterial>()
        private val queuedDiscoveries = ArrayDeque<MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>>>()
        private val activeMaterials = mutableMapOf<PairingAttemptId, FakeMaterial>()

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connectionState
                "getDiagnosticEvents" -> diagnostics
                "getLocalPairingController" -> localController
                "createQrPairingAttempt" -> {
                    val attemptId = args!![0] as PairingAttemptId
                    val material = queuedMaterials.removeFirst()
                    assertEquals(material.attemptId, attemptId)
                    createdAttempts += attemptId
                    activeMaterials[attemptId] = material
                    AdbOperationResult.Success(material)
                }
                "observeWirelessServices" -> {
                    assertEquals(args!![0], WirelessDiscoveryMode.LOCAL_PAIRING)
                    queuedDiscoveries.removeFirst()
                }
                "pairQrObservation" -> {
                    val attemptId = args!![0] as PairingAttemptId
                    qrPairCalls += attemptId
                    activeMaterials.remove(attemptId)?.invalidate()
                    AdbOperationResult.Success(Unit)
                }
                "cancelQrPairing" -> {
                    val attemptId = args!![0] as PairingAttemptId
                    cancelledAttempts += attemptId
                    activeMaterials.remove(attemptId)?.invalidate()
                    AdbOperationResult.Success(Unit)
                }
                "pairWithSecret" -> {
                    codePairCalls++
                    (args!![1] as PairingSecret).clear()
                    AdbOperationResult.Success(Unit)
                }
                "connect" -> {
                    connectCalls++
                    AdbOperationResult.Success(Unit)
                }
                "disconnect" -> {
                    disconnectCalls++
                    connectionState.value = AdbConnectionState.Disconnected()
                    AdbOperationResult.Success(Unit)
                }
                "clearDiagnosticEvents", "reportInvalidAddress", "close" -> null
                "streamLogcat" -> flowOf(AdbOperationResult.Cancelled)
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        fun enqueueQrAttempt(material: FakeMaterial): MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>> {
            val discovery = MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>>(extraBufferCapacity = 1)
            queuedMaterials += material
            queuedDiscoveries += discovery
            return discovery
        }
    }

    private class FakeLocalPairingController : LocalPairingController {
        private val mutableState = MutableStateFlow(LocalPairingControllerState())
        override val state = mutableState
        val startedWindows = mutableListOf<Pair<PairingAttemptId, LocalPairingWindowId>>()
        val cancelledWindows = mutableListOf<LocalPairingWindowId>()
        val submittedWindows = mutableListOf<LocalPairingWindowId>()

        override fun start(
            attemptId: PairingAttemptId,
            windowId: LocalPairingWindowId,
        ): AdbOperationResult<LocalPairingWindow> {
            startedWindows += attemptId to windowId
            val window = window(windowId, attemptId)
            mutableState.value = LocalPairingControllerState(
                window = window,
                discoveryStatus = LocalPairingDiscoveryStatus.SEARCHING,
            )
            return AdbOperationResult.Success(window)
        }

        override fun updateNotification(
            deviceUnlocked: Boolean,
            capability: LocalPairingNotificationCapability,
        ): LocalPairingNotificationDecision = LocalPairingNotificationDecision(
            state = LocalPairingNotificationState.INPUT_READY,
            inputActionAvailable = true,
            submitAllowed = true,
            actionWindowId = mutableState.value.window?.windowId,
            applicationInputAvailable = true,
            suggestNativeNotificationStyle = false,
        )

        override suspend fun submit(
            windowId: LocalPairingWindowId,
            secret: PairingSecret,
        ): AdbOperationResult<Unit> {
            submittedWindows += windowId
            secret.clear()
            val active = mutableState.value.window
            mutableState.value = LocalPairingControllerState(
                window = active?.copy(hasLivePairingService = false, stopReason = LocalPairingStopReason.SUCCEEDED),
                discoveryStatus = LocalPairingDiscoveryStatus.STOPPED,
                stopReason = LocalPairingStopReason.SUCCEEDED,
            )
            return AdbOperationResult.Success(Unit)
        }

        override fun cancel(windowId: LocalPairingWindowId): AdbOperationResult<Unit> {
            cancelledWindows += windowId
            return AdbOperationResult.Success(Unit)
        }

        override fun onSystemTimeout(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
            AdbOperationResult.Success(Unit)

        fun publish(
            windowId: LocalPairingWindowId,
            discoveryStatus: LocalPairingDiscoveryStatus,
            notificationDecision: LocalPairingNotificationDecision? = null,
        ) {
            val attemptId = startedWindows.first { it.second == windowId }.first
            mutableState.value = LocalPairingControllerState(
                window = window(windowId, attemptId),
                discoveryStatus = discoveryStatus,
                notificationDecision = notificationDecision,
            )
        }

        private fun window(
            windowId: LocalPairingWindowId,
            attemptId: PairingAttemptId,
        ): LocalPairingWindow = LocalPairingWindow(
            windowId = windowId,
            attemptId = attemptId,
            startedAtMillis = 0L,
            deadlineMillis = 120_000L,
            hasLivePairingService = true,
        )
    }
}
