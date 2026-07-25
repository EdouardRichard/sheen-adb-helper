package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.LocalPairingController
import com.sheen.adb.core.LocalPairingControllerState
import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.data.DeviceProfile
import com.sheen.adb.data.DeviceProfileRepository
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesConnectionViewModelTest {
    @Test
    fun `valid manual connection converges within ten seconds and follows manager Session`() = connectionTest {
        val manager = FakeManager()
        val repository = FakeRepository()
        val endpoint = AdbEndpoint("192.0.2.10", 45_010)
        manager.connectBehavior = {
            manager.publish(AdbConnectionState.Connecting(endpoint))
            manager.publish(AdbConnectionState.Connected(endpoint, "session-manual"))
            AdbOperationResult.Success(Unit)
        }
        val viewModel = viewModel(manager, repository)

        viewModel.updateEndpoint("192.0.2.10:45010")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(manager.connectEndpoints, listOf(endpoint))
        assertEquals(manager.connectTimeouts, listOf(encodedDuration(10.seconds)))
        assertEquals(
            (viewModel.state.value.connectionState as AdbConnectionState.Connected).sessionId,
            "session-manual",
        )
        assertEquals(repository.recordedEndpoints, listOf(endpoint))
    }

    @Test
    fun `invalid endpoint performs zero manager operations`() = connectionTest {
        val manager = FakeManager()
        val viewModel = viewModel(manager)

        viewModel.updateEndpoint("not-an-endpoint")
        viewModel.connect()
        advanceUntilIdle()

        assertTrue(manager.operationCalls.isEmpty())
        assertTrue(viewModel.state.value.inputError?.isNotBlank() == true)
    }

    @Test
    fun `active Session replacement waits for confirmation and cancellation preserves it`() = connectionTest {
        val oldEndpoint = AdbEndpoint("192.0.2.20", 45_020)
        val manager = FakeManager(AdbConnectionState.Connected(oldEndpoint, "session-old"))
        val viewModel = viewModel(manager)
        val replacement = profile("replacement", "192.0.2.21", 45_021)
        runCurrent()

        viewModel.reconnect(replacement)
        runCurrent()

        assertTrue(viewModel.state.value.awaitingDiscoverySessionReplacement)
        assertEquals(manager.operationCalls, emptyList<String>())

        viewModel.dismissDiscoverySessionReplacement()
        runCurrent()

        assertFalse(viewModel.state.value.awaitingDiscoverySessionReplacement)
        assertEquals(
            (viewModel.state.value.connectionState as AdbConnectionState.Connected).sessionId,
            "session-old",
        )
        assertEquals(manager.operationCalls, emptyList<String>())
    }

    @Test
    fun `cancelled and timed out connections settle without success`() = connectionTest {
        val manager = FakeManager()
        val viewModel = viewModel(manager)
        manager.connectBehavior = { AdbOperationResult.Cancelled }

        viewModel.updateEndpoint("192.0.2.30:45030")
        viewModel.connect()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.notice.orEmpty().contains("成功"))

        manager.connectBehavior = {
            val error = AdbError.Timeout(AdbOperationStage.CONNECT)
            manager.publish(AdbConnectionState.Error(error, error.technicalCode))
            AdbOperationResult.Failure(error)
        }
        viewModel.updateEndpoint("192.0.2.31:45031")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(manager.connectTimeouts.last(), encodedDuration(10.seconds))
        assertTrue(viewModel.state.value.connectionState is AdbConnectionState.Error)
        assertFalse(viewModel.state.value.notice.orEmpty().contains("成功"))
    }

    @Test
    fun `rotation background and process recreation derive state from actual Session`() = connectionTest {
        val endpointA = AdbEndpoint("192.0.2.40", 45_040)
        val endpointB = AdbEndpoint("192.0.2.41", 45_041)
        val manager = FakeManager(AdbConnectionState.Connected(endpointA, "session-a"))
        val beforeRecreation = viewModel(manager)
        runCurrent()

        beforeRecreation.onDiscoveryBackground()
        runCurrent()
        assertEquals(
            (beforeRecreation.state.value.connectionState as AdbConnectionState.Connected).sessionId,
            "session-a",
        )
        assertEquals(manager.disconnectObservedSessionIds, emptyList<String?>())

        manager.publish(AdbConnectionState.Connected(endpointB, "session-b"))
        val afterProcessRecreation = viewModel(manager)
        runCurrent()

        assertEquals(
            (afterProcessRecreation.state.value.connectionState as AdbConnectionState.Connected).sessionId,
            "session-b",
        )
        assertEquals(manager.connectEndpoints, emptyList<AdbEndpoint>())
    }

    @Test
    fun `old connection result cannot overwrite a newer Session`() = connectionTest {
        val requested = AdbEndpoint("192.0.2.50", 45_050)
        val current = AdbEndpoint("192.0.2.51", 45_051)
        val manager = FakeManager()
        val repository = FakeRepository()
        manager.connectBehavior = {
            manager.publish(AdbConnectionState.Connected(current, "session-new"))
            AdbOperationResult.Success(Unit)
        }
        val viewModel = viewModel(manager, repository)

        viewModel.updateEndpoint("192.0.2.50:45050")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(
            (viewModel.state.value.connectionState as AdbConnectionState.Connected).sessionId,
            "session-new",
        )
        assertNull(viewModel.state.value.notice)
        assertEquals(repository.recordedEndpoints, emptyList<AdbEndpoint>())
        assertEquals(manager.connectEndpoints, listOf(requested))
    }

    @Test
    fun `disconnect observes current session and only reports terminal state after manager result`() = connectionTest {
        val endpoint = AdbEndpoint("192.0.2.60", 45_060)
        val manager = FakeManager(AdbConnectionState.Connected(endpoint, "session-disconnect"))
        val viewModel = viewModel(manager)
        runCurrent()
        manager.disconnectBehavior = {
            manager.publish(AdbConnectionState.Disconnecting)
            val error = AdbError.Timeout(AdbOperationStage.DISCONNECT)
            manager.publish(AdbConnectionState.Error(error, error.technicalCode))
            AdbOperationResult.Failure(error)
        }

        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(manager.disconnectObservedSessionIds, listOf("session-disconnect"))
        assertEquals(manager.disconnectTimeouts, listOf(encodedDuration(5.seconds)))
        assertTrue(viewModel.state.value.connectionState is AdbConnectionState.Error)
        assertFalse(viewModel.state.value.notice.orEmpty().contains("已断开"))
    }

    private fun connectionTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        manager: FakeManager,
        repository: FakeRepository = FakeRepository(),
    ): DevicesViewModel = DevicesViewModel(
        manager = manager.instance,
        repository = repository.instance,
        clock = Clock.systemUTC(),
    )

    private fun profile(id: String, host: String, port: Int) = DeviceProfile(
        id = id,
        displayName = id,
        host = host,
        debugPort = port,
        firstConnectedAtEpochMillis = 1L,
        lastConnectedAtEpochMillis = 1L,
        isLocal = false,
        identityReference = "host-key",
    )

    /**
     * Duration is an inline value class. Calls observed through the Java proxy
     * carry Kotlin's nanosecond encoding rather than a boxed Duration.
     */
    private fun encodedDuration(duration: Duration): Long = duration.inWholeNanoseconds * 2L

    private class FakeRepository {
        private val profiles = MutableStateFlow<List<DeviceProfile>>(emptyList())
        val recordedEndpoints = mutableListOf<AdbEndpoint>()

        val instance: DeviceProfileRepository = Proxy.newProxyInstance(
            DeviceProfileRepository::class.java.classLoader,
            arrayOf(DeviceProfileRepository::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getProfiles" -> profiles
                "recordSuccessfulConnection" -> {
                    val endpoint = AdbEndpoint(args!![0] as String, args[1] as Int)
                    recordedEndpoints += endpoint
                    DeviceProfile(
                        id = "recorded-${recordedEndpoints.size}",
                        displayName = endpoint.host,
                        host = endpoint.host,
                        debugPort = endpoint.port,
                        firstConnectedAtEpochMillis = 1L,
                        lastConnectedAtEpochMillis = 1L,
                        isLocal = false,
                        identityReference = "host-key",
                    )
                }
                "rename" -> false
                "delete" -> null
                "clearAll" -> null
                else -> null
            }
        } as DeviceProfileRepository
    }

    private class FakeManager(
        initialConnectionState: AdbConnectionState = AdbConnectionState.Disconnected(),
    ) {
        private val connection = MutableStateFlow(initialConnectionState)
        private val diagnostics = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        private val localController = UnsupportedLocalController()
        val operationCalls = mutableListOf<String>()
        val connectEndpoints = mutableListOf<AdbEndpoint>()
        val connectTimeouts = mutableListOf<Long>()
        val disconnectTimeouts = mutableListOf<Long>()
        val disconnectObservedSessionIds = mutableListOf<String?>()
        var connectBehavior: () -> AdbOperationResult<Unit> = { AdbOperationResult.Success(Unit) }
        var disconnectBehavior: () -> AdbOperationResult<Unit> = {
            publish(AdbConnectionState.Disconnected())
            AdbOperationResult.Success(Unit)
        }

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connection
                "getDiagnosticEvents" -> diagnostics
                "getLocalPairingController" -> localController
                "connect" -> {
                    operationCalls += "connect"
                    connectEndpoints += args!![0] as AdbEndpoint
                    connectTimeouts += args[1] as Long
                    connectBehavior()
                }
                "disconnect" -> {
                    operationCalls += "disconnect"
                    disconnectObservedSessionIds +=
                        (connection.value as? AdbConnectionState.Connected)?.sessionId
                    disconnectTimeouts += args!![0] as Long
                    disconnectBehavior()
                }
                "reportInvalidAddress" -> {
                    operationCalls += "reportInvalidAddress"
                    null
                }
                "clearDiagnosticEvents", "close" -> null
                "streamLogcat" -> flowOf(AdbOperationResult.Cancelled)
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        fun publish(state: AdbConnectionState) {
            connection.value = state
        }
    }

    private class UnsupportedLocalController : LocalPairingController {
        override val state = MutableStateFlow(LocalPairingControllerState())

        override fun start(
            attemptId: PairingAttemptId,
            windowId: LocalPairingWindowId,
        ): AdbOperationResult<LocalPairingWindow> = AdbOperationResult.Failure(AdbError.PairingUnsupported)

        override fun updateNotification(
            deviceUnlocked: Boolean,
            capability: LocalPairingNotificationCapability,
        ): LocalPairingNotificationDecision = LocalPairingNotificationDecision(
            state = LocalPairingNotificationState.INPUT_UNAVAILABLE,
            inputActionAvailable = false,
            submitAllowed = false,
            actionWindowId = null,
            applicationInputAvailable = true,
            suggestNativeNotificationStyle = false,
        )

        override suspend fun submit(
            windowId: LocalPairingWindowId,
            secret: PairingSecret,
        ): AdbOperationResult<Unit> {
            secret.clear()
            return AdbOperationResult.Failure(AdbError.PairingUnsupported)
        }

        override fun cancel(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
            AdbOperationResult.Failure(AdbError.PairingUnsupported)

        override fun onSystemTimeout(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
            AdbOperationResult.Failure(AdbError.PairingUnsupported)
    }
}
