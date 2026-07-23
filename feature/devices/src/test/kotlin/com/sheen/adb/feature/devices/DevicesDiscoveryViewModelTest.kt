package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryMode
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessDiscoveryTarget
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
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesDiscoveryViewModelTest {
    @Test
    fun `foreground starts one ten second LAN scan and delivers content`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val flow = manager.enqueueDiscovery()
            val viewModel = viewModel(manager)

            viewModel.onDiscoveryForeground()
            runCurrent()

            assertEquals(manager.discoveryRequests, listOf(WirelessDiscoveryMode.LAN_FOREGROUND to 10.seconds))
            flow.emit(AdbOperationResult.Success(snapshot(11L, "foreground")))
            runCurrent()

            assertEquals(viewModel.discoveryState.value.phase, DevicesDiscoveryPhase.CONTENT)
            assertEquals(viewModel.discoveryState.value.generation, 11L)
            assertEquals(viewModel.discoveryState.value.items.size, 1)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh cancels the old collection and rejects its late generation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val oldFlow = manager.enqueueDiscovery()
            val newFlow = manager.enqueueDiscovery()
            val viewModel = viewModel(manager)
            viewModel.onDiscoveryForeground()
            runCurrent()
            oldFlow.emit(AdbOperationResult.Success(snapshot(20L, "old-initial")))
            runCurrent()

            viewModel.refreshDiscovery()
            runCurrent()
            oldFlow.emit(AdbOperationResult.Success(snapshot(20L, "old-late")))
            newFlow.emit(AdbOperationResult.Success(snapshot(21L, "new")))
            runCurrent()

            assertEquals(manager.discoveryRequests.size, 2)
            assertEquals(viewModel.discoveryState.value.generation, 21L)
            assertEquals(viewModel.discoveryState.value.items.single().connectTarget?.generation, 21L)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `background and explicit cancel stop collection and publish cancelled state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val backgroundFlow = manager.enqueueDiscovery()
            val cancelFlow = manager.enqueueDiscovery()
            val viewModel = viewModel(manager)

            viewModel.onDiscoveryForeground()
            runCurrent()
            assertEquals(backgroundFlow.subscriptionCount.value, 1)
            viewModel.onDiscoveryBackground()
            runCurrent()
            assertEquals(backgroundFlow.subscriptionCount.value, 0)
            assertEquals(viewModel.discoveryState.value.phase, DevicesDiscoveryPhase.CANCELLED)

            viewModel.onDiscoveryForeground()
            runCurrent()
            assertEquals(cancelFlow.subscriptionCount.value, 1)
            viewModel.cancelDiscovery()
            runCurrent()
            assertEquals(cancelFlow.subscriptionCount.value, 0)
            assertEquals(viewModel.discoveryState.value.phase, DevicesDiscoveryPhase.CANCELLED)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `confirmed pairing selection submits through discovered target with one attempt`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val flow = manager.enqueueDiscovery()
            val attemptId = PairingAttemptId.of("attempt-discovered")
            val viewModel = viewModel(manager, attemptId)
            viewModel.onDiscoveryForeground()
            runCurrent()
            flow.emit(AdbOperationResult.Success(snapshot(30L, "pairing", WirelessServiceType.PAIRING)))
            runCurrent()
            val target = viewModel.discoveryState.value.items.single().pairingTarget!!

            viewModel.selectDiscoveryPairing(target)
            viewModel.confirmDiscoverySelection()
            viewModel.updatePairingCode("4".repeat(6))
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(manager.pairTargets, listOf(target))
            assertEquals(manager.pairAttempts, listOf(attemptId))
            assertEquals(manager.manualPairCalls, 0)
            assertEquals(viewModel.state.value.pairingCode, "")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `existing Session requires replacement confirmation before discovered connect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager(
                AdbConnectionState.Connected(
                    endpoint = AdbEndpoint("synthetic.invalid", 45_001),
                    sessionId = "session-existing",
                ),
            )
            val flow = manager.enqueueDiscovery()
            val viewModel = viewModel(manager)
            viewModel.onDiscoveryForeground()
            runCurrent()
            flow.emit(AdbOperationResult.Success(snapshot(40L, "connect", WirelessServiceType.CONNECT)))
            runCurrent()
            val target = viewModel.discoveryState.value.items.single().connectTarget!!

            viewModel.selectDiscoveryConnect(target)
            viewModel.confirmDiscoverySelection()
            runCurrent()

            assertTrue(viewModel.state.value.awaitingDiscoverySessionReplacement)
            assertTrue(manager.connectTargets.isEmpty())
            assertEquals(manager.disconnectCalls, 0)

            viewModel.confirmDiscoverySessionReplacement()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.awaitingDiscoverySessionReplacement)
            assertEquals(manager.disconnectCalls, 1)
            assertEquals(manager.connectTargets, listOf(target))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        manager: FakeManager,
        attemptId: PairingAttemptId = PairingAttemptId.of("attempt-default"),
    ): DevicesViewModel = DevicesViewModel(
        manager = manager.instance,
        repository = fakeRepository(),
        clock = Clock.systemUTC(),
        pairingReducer = DevicesPairingReducer(),
        qrEncoder = QrMatrixEncoder(),
        pairingAttemptIdFactory = { attemptId },
    )

    private fun snapshot(
        generation: Long,
        id: String,
        type: WirelessServiceType = WirelessServiceType.CONNECT,
    ): WirelessDiscoveryState = WirelessDiscoveryState(
        generation = generation,
        services = listOf(
            WirelessServiceObservation(
                observationId = WirelessObservationId(id),
                serviceType = type,
                serviceName = "synthetic-service",
                addresses = listOf(WirelessAddress.Ipv4(192, 0, 2, 50)),
                port = 45_050,
                status = WirelessServiceStatus.RESOLVED,
                lastSeenAt = 1L,
            ),
        ),
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

    private class FakeManager(
        initialConnectionState: AdbConnectionState = AdbConnectionState.Disconnected(),
    ) {
        private val connection = MutableStateFlow(initialConnectionState)
        private val diagnostics = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        private val discoveries = ArrayDeque<MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>>>()
        val discoveryRequests = mutableListOf<Pair<WirelessDiscoveryMode, Duration>>()
        val pairTargets = mutableListOf<WirelessDiscoveryTarget>()
        val pairAttempts = mutableListOf<PairingAttemptId>()
        val connectTargets = mutableListOf<WirelessDiscoveryTarget>()
        var manualPairCalls = 0
        var disconnectCalls = 0

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connection
                "getDiagnosticEvents" -> diagnostics
                "observeWirelessServices" -> {
                    discoveryRequests += (args!![0] as WirelessDiscoveryMode) to (args[1] as Duration)
                    discoveries.removeFirst()
                }
                "pairDiscoveredService" -> {
                    val target = args!![0] as WirelessDiscoveryTarget
                    pairTargets += target
                    pairAttempts += args[1] as PairingAttemptId
                    (args[2] as PairingSecret).clear()
                    AdbOperationResult.Success(WirelessDiscoveryState(target.generation))
                }
                "connectDiscoveredService" -> {
                    val target = args!![0] as WirelessDiscoveryTarget
                    connectTargets += target
                    AdbOperationResult.Success(WirelessDiscoveryState(target.generation))
                }
                "pairWithSecret" -> {
                    manualPairCalls++
                    (args!![1] as PairingSecret).clear()
                    AdbOperationResult.Success(Unit)
                }
                "disconnect" -> {
                    disconnectCalls++
                    connection.value = AdbConnectionState.Disconnected()
                    AdbOperationResult.Success(Unit)
                }
                "clearDiagnosticEvents", "reportInvalidAddress", "close" -> null
                "streamLogcat" -> flowOf(AdbOperationResult.Cancelled)
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        fun enqueueDiscovery(): MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>> =
            MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>>(extraBufferCapacity = 4).also(discoveries::add)
    }
}
