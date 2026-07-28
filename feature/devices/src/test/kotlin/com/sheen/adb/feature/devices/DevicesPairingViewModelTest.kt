package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
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
import com.sheen.adb.core.VerifiedWirelessDeviceId
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryMode
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.data.DeviceProfileRepository
import java.io.File
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
    fun `connection page pairing actions expose the root overlay before work starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val material = FakeMaterial(PairingAttemptId.of("attempt-connection-page"))
            manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))

            viewModel.beginPairingFromConnectionPage(PairingMethod.QR)
            runCurrent()

            assertTrue(viewModel.state.value.showPairing)
            assertEquals(viewModel.pairingState.value.method, PairingMethod.QR)
            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
            assertTrue(viewModel.pairingState.value.qrMatrix != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `QR manager flow automatically connects the paired debug service`() = runTest {
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
            assertEquals(manager.localConnectAttempts, listOf(material.attemptId))
            assertFalse(viewModel.state.value.showPairing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `discovered six digit pairing automatically connects the paired debug service`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val attemptId = PairingAttemptId.of("attempt-code-auto-connect")
            val manager = FakeManager()
            val pairingDiscovery = manager.enqueueDiscovery()
            val viewModel = viewModel(manager, listOf(attemptId))

            viewModel.beginPairingFromConnectionPage(PairingMethod.SIX_DIGIT_CODE)
            runCurrent()
            pairingDiscovery.emit(
                AdbOperationResult.Success(
                    discoveryState(7L, resolvedObservation("pairing-code-auto-connect")),
                ),
            )
            runCurrent()
            viewModel.updatePairingCode("123456")
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(manager.pairAttempts, listOf(attemptId))
            assertEquals(manager.localConnectAttempts, listOf(attemptId))
            assertFalse(viewModel.state.value.showPairing)
            assertEquals(viewModel.state.value.pairingCode, "")
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
    fun `successful local pairing actively connects the matching local debug service`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val attemptId = PairingAttemptId.of("attempt-local-connect")
            val windowId = LocalPairingWindowId.of("window-local-connect")
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(attemptId),
                windowIds = listOf(windowId),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            runCurrent()
            manager.localController.publish(
                windowId = windowId,
                discoveryStatus = LocalPairingDiscoveryStatus.STOPPED,
                stopReason = LocalPairingStopReason.SUCCEEDED,
            )
            advanceUntilIdle()

            assertEquals(manager.localConnectAttempts, listOf(attemptId))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local entry retires foreground discovery before claiming the pairing window`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            manager.enqueueDiscovery()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-discovery-handoff")),
                windowIds = listOf(LocalPairingWindowId.of("window-discovery-handoff")),
            )

            viewModel.onDiscoveryForeground()
            runCurrent()
            assertTrue(manager.wirelessDiscoveryActive)

            viewModel.enterLocalPairingMode()
            advanceUntilIdle()

            assertFalse(manager.wirelessDiscoveryActive)
            assertEquals(manager.localController.startedWindows.size, 1)
            assertTrue(viewModel.pairingState.value.localWindowActive)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `closing pairing invalidates stale targets and resumes a fresh LAN discovery generation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            manager.enqueueDiscovery()
            manager.enqueueDiscovery()
            manager.enqueueDiscovery()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-refresh-after-pairing")),
            )

            viewModel.onDiscoveryForeground()
            runCurrent()
            viewModel.beginPairingFromConnectionPage(PairingMethod.SIX_DIGIT_CODE)
            runCurrent()
            runCurrent()
            viewModel.closePairing()
            runCurrent()

            assertEquals(
                manager.discoveryModes,
                listOf(
                    WirelessDiscoveryMode.LAN_FOREGROUND,
                    WirelessDiscoveryMode.LOCAL_PAIRING,
                    WirelessDiscoveryMode.LAN_FOREGROUND,
                ),
                "observed discovery modes=${manager.discoveryModes}",
            )
            assertEquals(viewModel.discoveryState.value.phase, DevicesDiscoveryPhase.SCANNING)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `wireless settings handoff preserves the local window only until the app returns`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-settings-handoff")),
                windowIds = listOf(LocalPairingWindowId.of("window-settings-handoff")),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            viewModel.onLocalWirelessSettingsOpened()
            assertTrue(viewModel.state.value.keepLocalPairingWhileOpeningSettings)

            viewModel.onLocalWirelessSettingsReturned()
            assertFalse(viewModel.state.value.keepLocalPairingWhileOpeningSettings)
            assertTrue(viewModel.pairingState.value.localWindowActive)
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

    @Test
    fun `core deadline with a cleared window reaches the local timeout UI`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-deadline")),
                windowIds = listOf(LocalPairingWindowId.of("window-deadline")),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            runCurrent()
            manager.localController.publishStoppedWithoutWindow(LocalPairingStopReason.DEADLINE_REACHED)
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.EXPIRED)
            assertEquals(
                viewModel.pairingState.value.localDiscoveryStatus,
                LocalPairingDiscoveryStatus.STOPPED,
            )
            assertFalse(viewModel.pairingState.value.localWindowActive)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `QR timeout expires the attempt and clears temporary material`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val material = FakeMaterial(PairingAttemptId.of("attempt-qr-timeout"))
            val discovery = manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            discovery.emit(AdbOperationResult.Failure(AdbError.DiscoveryTimeout))
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.EXPIRED)
            assertNull(viewModel.pairingState.value.qrMatrix)
            assertNull(material.payload)
            assertTrue(material.attemptId in manager.cancelledAttempts)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `six digit cancellation clears both reducer and screen input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(manager, emptyList())

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingCode("0".repeat(6))
            viewModel.onPairingPageLeft()
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
            assertEquals(manager.codePairCalls, 0)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `six digit timeout is distinct and never remains in state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager(
                codePairResult = AdbOperationResult.Failure(
                    AdbError.Timeout(AdbOperationStage.PAIR),
                ),
            )
            val viewModel = viewModel(manager, emptyList())

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingEndpoint("synthetic.invalid:4711")
            viewModel.updatePairingCode("0".repeat(6))
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.EXPIRED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
            assertFalse(viewModel.pairingState.value.toString().contains("000000"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed remote six digit pairing retries with a fresh pairing port discovery`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager(
                codePairResult = AdbOperationResult.Failure(
                    AdbError.IoFailure(AdbOperationStage.PAIR),
                ),
            )
            val replacementDiscovery = manager.enqueueDiscovery()
            val replacementAttemptId = PairingAttemptId.of("attempt-code-retry")
            val viewModel = viewModel(manager, listOf(replacementAttemptId))

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingEndpoint("synthetic.invalid:4711")
            viewModel.updatePairingCode("0".repeat(6))
            viewModel.pair()
            advanceUntilIdle()
            assertEquals(PairingAttemptPhase.FAILED, viewModel.pairingState.value.phase)

            viewModel.retryPairing()
            runCurrent()

            assertEquals(
                listOf(WirelessDiscoveryMode.LOCAL_PAIRING),
                manager.discoveryModes,
            )
            assertEquals(
                LocalPairingDiscoveryStatus.SEARCHING,
                viewModel.pairingState.value.localDiscoveryStatus,
            )

            replacementDiscovery.emit(
                AdbOperationResult.Success(
                    discoveryState(11L, resolvedObservation("pairing-code-retry")),
                ),
            )
            runCurrent()

            assertEquals(
                LocalPairingDiscoveryStatus.FOUND,
                viewModel.pairingState.value.localDiscoveryStatus,
            )
            assertEquals("", viewModel.state.value.pairingCode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `session appearance cancels QR material without pairing the new session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val material = FakeMaterial(PairingAttemptId.of("attempt-session-change"))
            manager.enqueueQrAttempt(material)
            val viewModel = viewModel(manager, listOf(material.attemptId))

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            manager.connectionState.value = AdbConnectionState.Connected(
                endpoint = AdbEndpoint("replacement.invalid", 4711),
                sessionId = "session-replacement",
            )
            advanceUntilIdle()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertNull(viewModel.pairingState.value.qrMatrix)
            assertNull(material.payload)
            assertTrue(material.attemptId in manager.cancelledAttempts)
            assertTrue(manager.qrPairCalls.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `session appearance cancels six digit entry and clears its value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(manager, emptyList())
            runCurrent()

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingCode("0".repeat(6))
            manager.connectionState.value = AdbConnectionState.Connected(
                endpoint = AdbEndpoint("replacement.invalid", 4711),
                sessionId = "session-replacement",
            )
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local timeout clears application input and remains distinct from cancellation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val windowId = LocalPairingWindowId.of("window-system-timeout")
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-system-timeout")),
                windowIds = listOf(windowId),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            viewModel.updatePairingCode("0".repeat(6))
            manager.localController.publish(
                windowId = windowId,
                discoveryStatus = LocalPairingDiscoveryStatus.STOPPED,
                stopReason = LocalPairingStopReason.SYSTEM_TIMEOUT,
            )
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.EXPIRED)
            assertEquals(viewModel.pairingState.value.codeInput, "")
            assertEquals(viewModel.state.value.pairingCode, "")
            assertFalse(viewModel.pairingState.value.localWindowActive)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `session appearance cancels the local window and clears its value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val windowId = LocalPairingWindowId.of("window-session-change")
            val viewModel = viewModel(
                manager = manager,
                attemptIds = listOf(PairingAttemptId.of("attempt-session-change")),
                windowIds = listOf(windowId),
            )
            runCurrent()

            viewModel.enterLocalPairingMode()
            viewModel.updatePairingCode("0".repeat(6))
            manager.connectionState.value = AdbConnectionState.Connected(
                endpoint = AdbEndpoint("replacement.invalid", 4711),
                sessionId = "session-replacement",
            )
            runCurrent()

            assertEquals(viewModel.pairingState.value.phase, PairingAttemptPhase.CANCELLED)
            assertEquals(viewModel.state.value.pairingCode, "")
            assertEquals(manager.localController.cancelledWindows, listOf(windowId))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `paired target connects while unpaired target requests a QR root overlay`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pairedManager = FakeManager()
            val pairedDiscovery = pairedManager.enqueueDiscovery()
            val pairedViewModel = viewModel(pairedManager, emptyList())
            pairedViewModel.onDiscoveryForeground()
            runCurrent()
            pairedDiscovery.emit(
                AdbOperationResult.Success(
                    discoveryState(
                        1,
                        resolvedObservation(
                            "paired-connect",
                            WirelessServiceType.CONNECT,
                            verifiedDeviceId = VerifiedWirelessDeviceId("verified-paired-connect"),
                        ),
                    ),
                ),
            )
            runCurrent()
            val paired = pairedViewModel.discoveryState.value.items.single().connectTarget!!

            pairedViewModel.selectDiscoveryConnect(paired)
            pairedViewModel.confirmDiscoverySelection()
            advanceUntilIdle()

            assertEquals(pairedManager.connectTargets, listOf(paired))
            assertFalse(pairedViewModel.state.value.showPairing)

            val unpairedManager = FakeManager()
            unpairedManager.discoveredConnectResult =
                AdbOperationResult.Failure(AdbError.AuthenticationFailed(AdbOperationStage.CONNECT))
            val material = FakeMaterial(PairingAttemptId.of("attempt-unpaired-overlay"))
            val unpairedDiscovery = unpairedManager.enqueueDiscovery()
            unpairedManager.enqueueQrAttempt(material)
            val unpairedViewModel = viewModel(unpairedManager, listOf(material.attemptId))
            unpairedViewModel.onDiscoveryForeground()
            runCurrent()
            unpairedDiscovery.emit(
                AdbOperationResult.Success(
                    discoveryState(1, resolvedObservation("unpaired-connect", WirelessServiceType.CONNECT)),
                ),
            )
            runCurrent()
            val unpaired = unpairedViewModel.discoveryState.value.items.single().connectTarget!!
            unpairedViewModel.selectDiscoveryConnect(unpaired)
            unpairedViewModel.confirmDiscoverySelection()
            runCurrent()

            assertTrue(unpairedViewModel.state.value.showPairing, "unpaired selection must expose a root-overlay intent")
            assertEquals(unpairedViewModel.pairingState.value.method, PairingMethod.QR)
            assertEquals(unpairedViewModel.pairingState.value.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
            assertTrue(unpairedViewModel.pairingState.value.qrMatrix != null)
            assertTrue(unpairedManager.pairTargets.isEmpty(), "opening an overlay must not submit pairing")
            assertEquals(unpairedManager.connectTargets, listOf(unpaired))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching an unpaired selected device to code pairing ignores unrelated first service`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            manager.discoveredConnectResult =
                AdbOperationResult.Failure(AdbError.AuthenticationFailed(AdbOperationStage.CONNECT))
            val qrMaterial = FakeMaterial(PairingAttemptId.of("attempt-targeted-qr"))
            val lanDiscovery = manager.enqueueDiscovery()
            manager.enqueueQrAttempt(qrMaterial)
            val pairingDiscovery = manager.enqueueDiscovery()
            val codeAttempt = PairingAttemptId.of("attempt-targeted-code")
            val viewModel = viewModel(
                manager,
                listOf(qrMaterial.attemptId, codeAttempt),
            )

            viewModel.onDiscoveryForeground()
            runCurrent()
            val selectedConnect = resolvedObservation(
                id = "selected-connect",
                serviceType = WirelessServiceType.CONNECT,
                serviceName = "adb-synthetic-guid-alpha-connectsuffix",
                address = WirelessAddress.Ipv4(192, 0, 2, 50),
                port = 47_101,
            )
            lanDiscovery.emit(AdbOperationResult.Success(discoveryState(21L, selectedConnect)))
            runCurrent()
            val target = viewModel.discoveryState.value.items.single().connectTarget!!
            viewModel.selectDiscoveryConnect(target)
            viewModel.confirmDiscoverySelection()
            runCurrent()
            viewModel.switchPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            runCurrent()

            val unrelated = resolvedObservation(
                id = "unrelated-pairing-first",
                serviceName = "adb-synthetic-guid-beta",
                address = WirelessAddress.Ipv4(192, 0, 2, 51),
                port = 47_102,
            )
            val matching = resolvedObservation(
                id = "matching-pairing-second",
                serviceName = "adb-synthetic-guid-alpha",
                address = WirelessAddress.Ipv4(192, 0, 2, 50),
                port = 47_103,
            )
            pairingDiscovery.emit(
                AdbOperationResult.Success(
                    WirelessDiscoveryState(22L, services = listOf(unrelated, matching)),
                ),
            )
            runCurrent()

            assertEquals(
                viewModel.pairingState.value.localDiscoveryStatus,
                LocalPairingDiscoveryStatus.FOUND,
            )
            assertTrue(viewModel.state.value.pairingEndpointInput.endsWith(":47103"))
            viewModel.updatePairingCode("123456")
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(
                manager.pairTargets,
                listOf(WirelessDiscoveryTarget(22L, matching.observationId)),
            )
            assertEquals(manager.pairAttempts, listOf(codeAttempt))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `editing an auto discovered pairing endpoint submits only the manual endpoint`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val pairingDiscovery = manager.enqueueDiscovery()
            val attemptId = PairingAttemptId.of("attempt-manual-override")
            val viewModel = viewModel(manager, listOf(attemptId))

            viewModel.beginPairingFromConnectionPage(PairingMethod.SIX_DIGIT_CODE)
            runCurrent()
            pairingDiscovery.emit(
                AdbOperationResult.Success(
                    discoveryState(
                        31L,
                        resolvedObservation(
                            id = "auto-discovered-pair",
                            serviceName = "adb-synthetic-guid",
                            port = 47_201,
                        ),
                    ),
                ),
            )
            runCurrent()
            viewModel.updatePairingEndpoint("synthetic.invalid:47202")
            viewModel.updatePairingCode("123456")
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(manager.codePairCalls, 1)
            assertTrue(manager.pairTargets.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `input and IME action submit zero requests while explicit Pair submits once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val viewModel = viewModel(manager, emptyList())

            viewModel.selectPairingMethod(PairingMethod.SIX_DIGIT_CODE)
            viewModel.startSelectedPairing()
            viewModel.updatePairingEndpoint("synthetic.invalid:4711")
            viewModel.updatePairingCode("123456")
            runCurrent()

            assertEquals(manager.codePairCalls, 0, "input changes must never submit")
            val source = File("src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt").readText()
            val imeHandler = source.substringAfter(
                "fun onPairingImeAction",
                missingDelimiterValue = "",
            ).substringBefore("\n    fun ", missingDelimiterValue = "")
            assertTrue(imeHandler.isNotEmpty(), "IME action must be represented explicitly as a zero-submit event")
            assertFalse(imeHandler.contains("pair(") || imeHandler.contains("submit"))
            assertEquals(manager.codePairCalls, 0, "IME completion must never submit")

            viewModel.pair()
            viewModel.pair()
            advanceUntilIdle()

            assertEquals(manager.codePairCalls, 1)
            assertEquals(viewModel.state.value.pairingCode, "")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry rejects another retry while the replacement scan is active`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val manager = FakeManager()
            val first = FakeMaterial(PairingAttemptId.of("attempt-retry-first"))
            val replacement = FakeMaterial(PairingAttemptId.of("attempt-retry-replacement"))
            val forbiddenOverlap = FakeMaterial(PairingAttemptId.of("attempt-retry-overlap"))
            manager.enqueueQrAttempt(first)
            manager.enqueueQrAttempt(replacement)
            manager.enqueueQrAttempt(forbiddenOverlap)
            val viewModel = viewModel(
                manager,
                listOf(first.attemptId, replacement.attemptId, forbiddenOverlap.attemptId),
            )

            viewModel.selectPairingMethod(PairingMethod.QR)
            viewModel.startSelectedPairing()
            runCurrent()
            viewModel.retryPairing()
            runCurrent()
            viewModel.retryPairing()
            runCurrent()

            assertEquals(manager.createdAttempts, listOf(first.attemptId, replacement.attemptId))
            assertNull(first.payload)
            assertTrue(replacement.payload != null)
            assertTrue(forbiddenOverlap.payload != null, "rejected retry must not consume a new attempt")
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

    private fun resolvedObservation(
        id: String,
        serviceType: WirelessServiceType = WirelessServiceType.PAIRING,
        serviceName: String = "synthetic-service",
        address: WirelessAddress = WirelessAddress.Ipv4(192, 0, 2, 50),
        port: Int = 4711,
        verifiedDeviceId: VerifiedWirelessDeviceId? = null,
    ): WirelessServiceObservation = WirelessServiceObservation(
        observationId = WirelessObservationId(id),
        serviceType = serviceType,
        serviceName = serviceName,
        addresses = listOf(address),
        port = port,
        status = WirelessServiceStatus.RESOLVED,
        verifiedDeviceId = verifiedDeviceId,
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
        private val codePairResult: AdbOperationResult<Unit> = AdbOperationResult.Success(Unit),
    ) {
        val connectionState = MutableStateFlow(initialConnectionState)
        val diagnostics = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
        val createdAttempts = mutableListOf<PairingAttemptId>()
        val cancelledAttempts = mutableListOf<PairingAttemptId>()
        val qrPairCalls = mutableListOf<PairingAttemptId>()
        val pairTargets = mutableListOf<WirelessDiscoveryTarget>()
        val pairAttempts = mutableListOf<PairingAttemptId>()
        val connectTargets = mutableListOf<WirelessDiscoveryTarget>()
        val localConnectAttempts = mutableListOf<PairingAttemptId>()
        var connectCalls = 0
        var disconnectCalls = 0
        var codePairCalls = 0
        var wirelessDiscoveryActive = false
        val discoveryModes = mutableListOf<WirelessDiscoveryMode>()
        val localController = FakeLocalPairingController { !wirelessDiscoveryActive }
        var discoveredConnectResult: AdbOperationResult<WirelessDiscoveryState>? = null

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
                    discoveryModes += args!![0] as WirelessDiscoveryMode
                    queuedDiscoveries.removeFirst()
                        .onStart { wirelessDiscoveryActive = true }
                        .onCompletion { wirelessDiscoveryActive = false }
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
                    codePairResult
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
                    discoveredConnectResult
                        ?: AdbOperationResult.Success(WirelessDiscoveryState(target.generation))
                }
                "connectLocalPairedDevice" -> {
                    localConnectAttempts += args!![0] as PairingAttemptId
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

        fun enqueueDiscovery(): MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>> =
            MutableSharedFlow<AdbOperationResult<WirelessDiscoveryState>>(extraBufferCapacity = 1).also {
                queuedDiscoveries += it
            }
    }

    private class FakeLocalPairingController(
        private val startAllowed: () -> Boolean = { true },
    ) : LocalPairingController {
        private val mutableState = MutableStateFlow(LocalPairingControllerState())
        override val state = mutableState
        val startedWindows = mutableListOf<Pair<PairingAttemptId, LocalPairingWindowId>>()
        val cancelledWindows = mutableListOf<LocalPairingWindowId>()
        val submittedWindows = mutableListOf<LocalPairingWindowId>()

        override fun start(
            attemptId: PairingAttemptId,
            windowId: LocalPairingWindowId,
        ): AdbOperationResult<LocalPairingWindow> {
            if (!startAllowed()) {
                return AdbOperationResult.Failure(AdbError.DiscoveryConflict)
            }
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
            stopReason: LocalPairingStopReason? = null,
        ) {
            val attemptId = startedWindows.first { it.second == windowId }.first
            mutableState.value = LocalPairingControllerState(
                window = window(windowId, attemptId),
                discoveryStatus = discoveryStatus,
                notificationDecision = notificationDecision,
                stopReason = stopReason,
            )
        }

        fun publishStoppedWithoutWindow(reason: LocalPairingStopReason) {
            mutableState.value = LocalPairingControllerState(
                window = null,
                discoveryStatus = LocalPairingDiscoveryStatus.STOPPED,
                stopReason = reason,
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
