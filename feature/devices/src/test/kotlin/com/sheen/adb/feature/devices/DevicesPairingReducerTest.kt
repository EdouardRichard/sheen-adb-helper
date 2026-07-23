package com.sheen.adb.feature.devices

import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.core.LocalPairingNotificationState
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DevicesPairingReducerTest {
    private val reducer = DevicesPairingReducer()

    @Test
    fun `QR selection prepares then waits without producing a connect effect`() {
        val selected = reduce(event = DevicesPairingEvent.SelectMethod(PairingMethod.QR))
        assertEquals(selected.state.method, PairingMethod.QR)
        assertEquals(selected.state.phase, PairingAttemptPhase.IDLE)

        val started = reduce(selected.state, DevicesPairingEvent.StartRequested)
        assertEquals(started.state.phase, PairingAttemptPhase.PREPARING)
        assertEquals((started.effects.single() as DevicesPairingEffect.Begin).method, PairingMethod.QR)

        val matrix = QrMatrix(1, booleanArrayOf(true))
        val waiting = reduce(started.state, DevicesPairingEvent.QrPrepared(matrix))
        assertEquals(waiting.state.phase, PairingAttemptPhase.WAITING_FOR_TARGET)
        assertEquals(waiting.state.qrMatrix, matrix)
        assertFalse(waiting.effects.any { it.javaClass.simpleName.contains("connect", ignoreCase = true) })
    }

    @Test
    fun `six digit selection waits for code and valid submit clears visible input immediately`() {
        val selected = reduce(event = DevicesPairingEvent.SelectMethod(PairingMethod.SIX_DIGIT_CODE))
        val started = reduce(selected.state, DevicesPairingEvent.StartRequested)
        assertEquals(started.state.phase, PairingAttemptPhase.WAITING_FOR_CODE)
        assertEquals((started.effects.single() as DevicesPairingEffect.Begin).method, PairingMethod.SIX_DIGIT_CODE)

        val entered = reduce(started.state, DevicesPairingEvent.CodeChanged("12a34-56"))
        assertEquals(entered.state.codeInput, "123456")
        assertFalse(entered.state.toString().contains("123456"))
        assertFalse(DevicesPairingEvent.CodeChanged("123456").toString().contains("123456"))
        val submitted = reduce(entered.state, DevicesPairingEvent.SubmitCode)

        assertEquals(submitted.state.codeInput, "")
        assertEquals(submitted.state.phase, PairingAttemptPhase.PAIRING)
        val effect = submitted.effects.single() as DevicesPairingEffect.SubmitCode
        assertEquals(effect.secret.toString(), "PairingSecret(redacted)")
        assertFalse(effect.toString().contains("123456"))
    }

    @Test
    fun `invalid code is distinguished and cleared without a pairing effect`() {
        val waiting = DevicesPairingState(
            method = PairingMethod.SIX_DIGIT_CODE,
            phase = PairingAttemptPhase.WAITING_FOR_CODE,
            codeInput = "12345",
        )

        val result = reduce(waiting, DevicesPairingEvent.SubmitCode)

        assertEquals(result.state.phase, PairingAttemptPhase.WAITING_FOR_CODE)
        assertEquals(result.state.failure, DevicesPairingFailure.INVALID_CODE)
        assertEquals(result.state.codeInput, "")
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `success failure cancellation and expiry are terminal and release sensitive display state`() {
        val matrix = QrMatrix(1, booleanArrayOf(true))
        val pairing = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.PAIRING,
            codeInput = "123456",
            qrMatrix = matrix,
        )

        val success = reduce(pairing, DevicesPairingEvent.Succeeded)
        assertTerminal(success.state, PairingAttemptPhase.SUCCEEDED, null)
        assertEquals(reduce(success.state, DevicesPairingEvent.Failed).state, success.state)

        val failure = reduce(pairing, DevicesPairingEvent.Failed)
        assertTerminal(failure.state, PairingAttemptPhase.FAILED, DevicesPairingFailure.EXPLICIT_FAILURE)

        val cancelled = reduce(pairing, DevicesPairingEvent.Cancelled)
        assertTerminal(cancelled.state, PairingAttemptPhase.CANCELLED, DevicesPairingFailure.CANCELLED)
        assertTrue(cancelled.effects.single() is DevicesPairingEffect.CancelCurrent)

        val expired = reduce(pairing, DevicesPairingEvent.Expired)
        assertTerminal(expired.state, PairingAttemptPhase.EXPIRED, DevicesPairingFailure.EXPIRED)
    }

    @Test
    fun `unsupported QR is distinct and offers an explicit code fallback`() {
        val waiting = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.WAITING_FOR_TARGET,
            qrMatrix = QrMatrix(1, booleanArrayOf(true)),
        )

        val unsupported = reduce(waiting, DevicesPairingEvent.Unsupported)
        assertTerminal(unsupported.state, PairingAttemptPhase.UNSUPPORTED, DevicesPairingFailure.UNSUPPORTED)
        assertTrue(unsupported.state.codeFallbackAvailable)

        val fallback = reduce(unsupported.state, DevicesPairingEvent.UseCodeFallback)
        assertEquals(fallback.state.method, PairingMethod.SIX_DIGIT_CODE)
        assertEquals(fallback.state.phase, PairingAttemptPhase.WAITING_FOR_CODE)
        assertNull(fallback.state.failure)
        assertEquals((fallback.effects.single() as DevicesPairingEffect.Begin).method, PairingMethod.SIX_DIGIT_CODE)

        val activeFallback = reduce(
            unsupported.state.copy(hasActiveSession = true),
            DevicesPairingEvent.UseCodeFallback,
        )
        assertEquals(activeFallback.state.method, PairingMethod.SIX_DIGIT_CODE)
        assertEquals(activeFallback.state.phase, PairingAttemptPhase.IDLE)
        assertTrue(activeFallback.state.awaitingSessionReplacementConfirmation)
        assertTrue(activeFallback.effects.isEmpty())
    }

    @Test
    fun `active Session requires confirmation before any replacement or pairing effect`() {
        val withSession = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.IDLE,
            hasActiveSession = true,
        )

        val blocked = reduce(withSession, DevicesPairingEvent.StartRequested)
        assertTrue(blocked.state.awaitingSessionReplacementConfirmation)
        assertEquals(blocked.state.phase, PairingAttemptPhase.IDLE)
        assertTrue(blocked.effects.isEmpty())

        val confirmed = reduce(blocked.state, DevicesPairingEvent.ConfirmSessionReplacement)
        assertFalse(confirmed.state.awaitingSessionReplacementConfirmation)
        assertEquals(confirmed.state.phase, PairingAttemptPhase.PREPARING)
        assertEquals(
            (confirmed.effects.single() as DevicesPairingEffect.DisconnectSessionAndBegin).method,
            PairingMethod.QR,
        )
        assertFalse(confirmed.effects.any { it.javaClass.simpleName == "Connect" })
    }

    @Test
    fun `entering local mode defaults to code starts discovery and requests first notification authorization`() {
        val entered = reduce(event = DevicesPairingEvent.EnterLocalMode)

        assertTrue(entered.state.isLocalMode)
        assertEquals(entered.state.method, PairingMethod.SIX_DIGIT_CODE)
        assertEquals(entered.state.phase, PairingAttemptPhase.WAITING_FOR_CODE)
        assertEquals(entered.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.SEARCHING)
        assertTrue(entered.state.applicationInputAvailable)
        assertTrue(entered.state.localWindowActive)
        assertEquals(
            entered.effects.map { it::class.java.simpleName }.toSet(),
            setOf("StartLocalWindow", "RequestNotificationPermission"),
        )
    }

    @Test
    fun `local discovery preserves found not found ambiguous and unsupported states`() {
        val local = reduce(event = DevicesPairingEvent.EnterLocalMode).state

        val found = reduce(local, DevicesPairingEvent.LocalDiscoveryChanged(LocalPairingDiscoveryStatus.FOUND))
        assertEquals(found.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.FOUND)
        assertFalse(found.state.requiresLocalTargetSelection)

        val notFound = reduce(found.state, DevicesPairingEvent.LocalDiscoveryChanged(LocalPairingDiscoveryStatus.NOT_FOUND))
        assertEquals(notFound.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.NOT_FOUND)

        val ambiguous = reduce(
            notFound.state,
            DevicesPairingEvent.LocalDiscoveryChanged(LocalPairingDiscoveryStatus.AMBIGUOUS),
        )
        assertTrue(ambiguous.state.requiresLocalTargetSelection)
        assertTrue(ambiguous.state.applicationInputAvailable)

        val unsupported = reduce(
            ambiguous.state,
            DevicesPairingEvent.LocalDiscoveryChanged(LocalPairingDiscoveryStatus.UNSUPPORTED),
        )
        assertEquals(unsupported.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.UNSUPPORTED)
        assertTrue(unsupported.state.applicationInputAvailable)
    }

    @Test
    fun `locked waiting input ready and unavailable notifications never remove application input`() {
        val local = reduce(event = DevicesPairingEvent.EnterLocalMode).state
        val locked = reduce(
            local,
            DevicesPairingEvent.LocalNotificationChanged(LocalPairingNotificationState.PRIVATE_LOCKED),
        )
        assertEquals(locked.state.localNotificationState, LocalPairingNotificationState.PRIVATE_LOCKED)
        assertTrue(locked.state.applicationInputAvailable)

        val ready = reduce(
            locked.state,
            DevicesPairingEvent.LocalNotificationChanged(LocalPairingNotificationState.INPUT_READY),
        )
        assertEquals(ready.state.localNotificationState, LocalPairingNotificationState.INPUT_READY)
        assertTrue(ready.state.applicationInputAvailable)

        val unavailable = reduce(
            ready.state,
            DevicesPairingEvent.LocalNotificationChanged(
                state = LocalPairingNotificationState.INPUT_UNAVAILABLE,
                suggestNativeNotificationStyle = true,
            ),
        )
        assertEquals(unavailable.state.localNotificationState, LocalPairingNotificationState.INPUT_UNAVAILABLE)
        assertTrue(unavailable.state.applicationInputAvailable)
        assertTrue(unavailable.state.suggestNativeNotificationStyle)
    }

    @Test
    fun `notification authorization is requested only on first explicit local entry and retry stays in app`() {
        val entered = reduce(event = DevicesPairingEvent.EnterLocalMode)
        val denied = reduce(entered.state, DevicesPairingEvent.NotificationPermissionResult(granted = false))
        assertTrue(denied.state.notificationPermissionRequested)
        assertEquals(denied.state.localNotificationState, LocalPairingNotificationState.INPUT_UNAVAILABLE)
        assertTrue(denied.state.applicationInputAvailable)

        val retry = reduce(denied.state, DevicesPairingEvent.RetryLocalMode)
        assertEquals(retry.effects.count { it is DevicesPairingEffect.StartLocalWindow }, 1)
        assertFalse(retry.effects.any { it is DevicesPairingEffect.RequestNotificationPermission })
        assertEquals(retry.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.SEARCHING)
    }

    @Test
    fun `page leave keeps only an active window opened for system wireless settings`() {
        val active = reduce(event = DevicesPairingEvent.EnterLocalMode).state

        val toSettings = reduce(
            active,
            DevicesPairingEvent.LocalPageLeft(openingWirelessSettings = true),
        )
        assertTrue(toSettings.effects.single() is DevicesPairingEffect.KeepLocalWindow)
        assertTrue(toSettings.state.localWindowActive)

        val ordinaryLeave = reduce(
            active,
            DevicesPairingEvent.LocalPageLeft(openingWirelessSettings = false),
        )
        assertTrue(ordinaryLeave.effects.single() is DevicesPairingEffect.StopLocalWindow)
        assertFalse(ordinaryLeave.state.localWindowActive)
        assertEquals(ordinaryLeave.state.phase, PairingAttemptPhase.CANCELLED)

        val inactive = reduce(
            active.copy(localWindowActive = false),
            DevicesPairingEvent.LocalPageLeft(openingWirelessSettings = true),
        )
        assertTrue(inactive.effects.single() is DevicesPairingEffect.StopLocalWindow)
    }

    @Test
    fun `every local terminal state closes its window and is stable across a later page leave`() {
        val active = reduce(event = DevicesPairingEvent.EnterLocalMode).state
        val terminalEvents = listOf(
            DevicesPairingEvent.Succeeded,
            DevicesPairingEvent.Failed,
            DevicesPairingEvent.Cancelled,
            DevicesPairingEvent.Expired,
            DevicesPairingEvent.Unsupported,
        )

        terminalEvents.forEach { event ->
            val terminal = reduce(active, event)

            assertFalse(terminal.state.localWindowActive)
            assertEquals(terminal.state.localDiscoveryStatus, LocalPairingDiscoveryStatus.STOPPED)
            assertEquals(terminal.state.localNotificationState, LocalPairingNotificationState.RESULT)
            assertFalse(terminal.state.requiresLocalTargetSelection)

            val afterLeave = reduce(
                terminal.state,
                DevicesPairingEvent.LocalPageLeft(openingWirelessSettings = false),
            )
            assertEquals(afterLeave.state, terminal.state)
            assertTrue(afterLeave.effects.isEmpty())
        }
    }

    @Test
    fun `pairing state is not a SavedState compatible secret container`() {
        val interfaces = DevicesPairingState::class.java.interfaces.map { it.name }
        val fieldNames = DevicesPairingState::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse("java.io.Serializable" in interfaces)
        assertFalse("android.os.Parcelable" in interfaces)
        assertTrue(fieldNames.none { it.contains("payload") || it.contains("secret") })
    }

    private fun reduce(
        state: DevicesPairingState = DevicesPairingState(),
        event: DevicesPairingEvent,
    ): DevicesPairingReduction = reducer.reduce(state, event)

    private fun assertTerminal(
        state: DevicesPairingState,
        phase: PairingAttemptPhase,
        failure: DevicesPairingFailure?,
    ) {
        assertEquals(state.phase, phase)
        assertEquals(state.failure, failure)
        assertEquals(state.codeInput, "")
        assertNull(state.qrMatrix)
        assertFalse(state.awaitingSessionReplacementConfirmation)
    }
}
