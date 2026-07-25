package com.sheen.adb.feature.overview

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.DeviceOverview
import com.sheen.adb.core.DynamicDeviceMetrics
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewPresentationTest {
    @Test
    fun `complete overview preserves every controlled device value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = DeferredOverviewManager(connected("session-a"))
            val viewModel = OverviewViewModel(manager.instance)
            runCurrent()

            viewModel.refresh()
            runCurrent()
            val complete = DeviceOverview(
                brand = "Fixture Brand",
                manufacturer = "Fixture Manufacturer",
                model = "Fixture Model",
                deviceCode = "fixture-device",
                androidVersion = "fixture-version",
                sdk = "fixture-sdk",
                buildDisplay = "fixture-build",
                buildFingerprint = "fixture-fingerprint",
                securityPatch = "fixture-patch",
                cpuAbi = "fixture-abi",
                availableCores = 8,
                memoryTotalBytes = 8_000L,
                memoryAvailableBytes = 4_000L,
                storageTotalBytes = 64_000L,
                storageAvailableBytes = 32_000L,
                batteryPercent = 75,
                chargingState = "fixture-charging",
                temperatureCelsius = 31.5,
                uptimeSeconds = 12_000L,
                networkAddresses = listOf("fixture-address"),
            )
            manager.completeNextOverview(AdbOperationResult.Success(complete))
            runCurrent()

            assertEquals(viewModel.state.value.overview, complete)
            assertEquals(viewModel.state.value.sessionId, "session-a")
            assertFalse(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `missing fields remain unavailable and are never replaced by zero`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = DeferredOverviewManager(connected("session-a"))
            val viewModel = OverviewViewModel(manager.instance)
            runCurrent()

            viewModel.refresh()
            runCurrent()
            manager.completeNextOverview(
                AdbOperationResult.Success(
                    DeviceOverview(
                        model = "Fixture Model",
                        availableCores = null,
                        memoryTotalBytes = null,
                        memoryAvailableBytes = null,
                        storageTotalBytes = null,
                        storageAvailableBytes = null,
                        batteryPercent = null,
                        temperatureCelsius = null,
                        uptimeSeconds = null,
                    ),
                ),
            )
            runCurrent()

            val overview = requireNotNull(viewModel.state.value.overview)
            assertEquals(overview.model, "Fixture Model")
            assertNull(overview.availableCores)
            assertNull(overview.memoryTotalBytes)
            assertNull(overview.memoryAvailableBytes)
            assertNull(overview.storageTotalBytes)
            assertNull(overview.storageAvailableBytes)
            assertNull(overview.batteryPercent)
            assertNull(overview.temperatureCelsius)
            assertNull(overview.uptimeSeconds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `disconnect immediately clears identity and overview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = DeferredOverviewManager(connected("session-a"))
            val viewModel = OverviewViewModel(manager.instance)
            runCurrent()
            viewModel.refresh()
            runCurrent()
            manager.completeNextOverview(
                AdbOperationResult.Success(DeviceOverview(model = "Old Fixture Model", batteryPercent = 80)),
            )
            runCurrent()
            assertTrue(viewModel.state.value.overview != null)

            manager.connectionState.value = AdbConnectionState.Disconnected()
            runCurrent()

            assertFalse(viewModel.state.value.isConnected)
            assertNull(viewModel.state.value.sessionId)
            assertNull(viewModel.state.value.overview)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `late full overview from old Session never overwrites replacement Session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = DeferredOverviewManager(connected("session-old"))
            val viewModel = OverviewViewModel(manager.instance)
            runCurrent()
            viewModel.refresh()
            runCurrent()
            assertEquals(manager.pendingOverviewCount, 1)

            manager.connectionState.value = connected("session-new")
            runCurrent()
            assertEquals(viewModel.state.value.sessionId, "session-new")
            assertNull(viewModel.state.value.overview)

            manager.completeNextOverview(
                AdbOperationResult.Success(DeviceOverview(model = "Must Not Reach New Session")),
            )
            runCurrent()

            assertEquals(viewModel.state.value.sessionId, "session-new")
            assertNull(viewModel.state.value.overview)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `late dynamic refresh from old Session never mutates replacement overview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = DeferredOverviewManager(connected("session-old"))
            val viewModel = OverviewViewModel(manager.instance)
            runCurrent()
            viewModel.refresh()
            runCurrent()
            manager.completeNextOverview(
                AdbOperationResult.Success(DeviceOverview(model = "Old Model", batteryPercent = 60)),
            )
            runCurrent()

            viewModel.setForeground(true)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(manager.pendingDynamicCount, 1)

            manager.connectionState.value = connected("session-new")
            runCurrent()
            assertEquals(manager.pendingOverviewCount, 1)
            manager.completeNextOverview(
                AdbOperationResult.Success(DeviceOverview(model = "New Model", batteryPercent = 10)),
            )
            runCurrent()

            manager.completeNextDynamic(
                AdbOperationResult.Success(DynamicDeviceMetrics(batteryPercent = 99, uptimeSeconds = 99_999L)),
            )
            runCurrent()
            viewModel.setForeground(false)

            val overview = requireNotNull(viewModel.state.value.overview)
            assertEquals(viewModel.state.value.sessionId, "session-new")
            assertEquals(overview.model, "New Model")
            assertEquals(overview.batteryPercent, 10)
            assertNull(overview.uptimeSeconds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun connected(sessionId: String): AdbConnectionState.Connected = AdbConnectionState.Connected(
        endpoint = AdbEndpoint("$sessionId.invalid", 4711),
        sessionId = sessionId,
    )

    private class DeferredOverviewManager(
        initialConnection: AdbConnectionState,
    ) {
        val connectionState = MutableStateFlow(initialConnection)
        private val diagnostics = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        private val overviewContinuations =
            ArrayDeque<Continuation<AdbOperationResult<DeviceOverview>>>()
        private val dynamicContinuations =
            ArrayDeque<Continuation<AdbOperationResult<DynamicDeviceMetrics>>>()

        val pendingOverviewCount: Int
            get() = overviewContinuations.size
        val pendingDynamicCount: Int
            get() = dynamicContinuations.size

        val instance: AdbSessionManager = Proxy.newProxyInstance(
            AdbSessionManager::class.java.classLoader,
            arrayOf(AdbSessionManager::class.java),
        ) { _, method, args ->
            when (method.name.substringBefore('-')) {
                "getConnectionState" -> connectionState
                "getDiagnosticEvents" -> diagnostics
                "loadDeviceOverview" -> {
                    @Suppress("UNCHECKED_CAST")
                    overviewContinuations +=
                        args!!.last() as Continuation<AdbOperationResult<DeviceOverview>>
                    COROUTINE_SUSPENDED
                }
                "refreshDynamicMetrics" -> {
                    @Suppress("UNCHECKED_CAST")
                    dynamicContinuations +=
                        args!!.last() as Continuation<AdbOperationResult<DynamicDeviceMetrics>>
                    COROUTINE_SUSPENDED
                }
                "clearDiagnosticEvents", "close" -> null
                else -> AdbOperationResult.Cancelled
            }
        } as AdbSessionManager

        fun completeNextOverview(result: AdbOperationResult<DeviceOverview>) {
            overviewContinuations.removeFirst().resume(result)
        }

        fun completeNextDynamic(result: AdbOperationResult<DynamicDeviceMetrics>) {
            dynamicContinuations.removeFirst().resume(result)
        }
    }
}
