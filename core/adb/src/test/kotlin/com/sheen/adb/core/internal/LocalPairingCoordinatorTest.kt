package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.core.internal.pairing.LocalPairingCoordinator
import com.sheen.adb.core.internal.pairing.LocalPairingNotificationPolicy
import com.sheen.adb.core.internal.pairing.MonotonicClock
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

internal class LocalPairingCoordinatorTest {
    @Test
    fun `window defaults to code pairing with five second status and two minute hard deadline`() {
        val fixture = Fixture()
        val started = fixture.coordinator.start(ATTEMPT_ONE, WINDOW_ONE)

        val window = (started as AdbOperationResult.Success<*>).value as LocalPairingWindow
        assertEquals(window.method, PairingMethod.SIX_DIGIT_CODE)
        assertEquals(window.deadlineMillis - window.startedAtMillis, 120_000L)
        assertEquals(fixture.coordinator.state.value.discoveryStatus, LocalPairingDiscoveryStatus.SEARCHING)

        fixture.nowMillis = 5_000L
        fixture.coordinator.onClockAdvanced()
        assertEquals(fixture.coordinator.state.value.discoveryStatus, LocalPairingDiscoveryStatus.NOT_FOUND)
        assertTrue(fixture.coordinator.state.value.window != null)

        fixture.nowMillis = 120_000L
        fixture.coordinator.onClockAdvanced()
        assertEquals(fixture.coordinator.state.value.stopReason, LocalPairingStopReason.DEADLINE_REACHED)
        assertNull(fixture.coordinator.state.value.window)
        assertEquals(fixture.stopDiscoveryCalls, 1)
    }

    @Test
    fun `one resolved pairing service becomes the only live target and multiple candidates stay ambiguous`() {
        val fixture = Fixture()
        fixture.coordinator.start(ATTEMPT_ONE, WINDOW_ONE)

        fixture.coordinator.onDiscoveryState(discoveryState(observation("observation-one")))
        assertEquals(fixture.coordinator.state.value.discoveryStatus, LocalPairingDiscoveryStatus.FOUND)
        assertTrue(fixture.coordinator.state.value.window?.hasLivePairingService == true)

        fixture.coordinator.cancel(WINDOW_ONE)
        fixture.coordinator.start(ATTEMPT_TWO, WINDOW_TWO)
        fixture.coordinator.onDiscoveryState(
            discoveryState(
                observation("observation-two"),
                observation("observation-three"),
            ),
        )
        assertEquals(fixture.coordinator.state.value.discoveryStatus, LocalPairingDiscoveryStatus.AMBIGUOUS)
        assertFalse(fixture.coordinator.state.value.window?.hasLivePairingService == true)
    }

    @Test
    fun `locked notification becomes input ready only after unlock with a live service`() {
        val fixture = Fixture()
        fixture.coordinator.start(ATTEMPT_ONE, WINDOW_ONE)
        fixture.coordinator.onDiscoveryState(discoveryState(observation("observation-ready")))

        val locked = fixture.coordinator.updateNotification(
            deviceUnlocked = false,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )
        assertEquals(locked.state, LocalPairingNotificationState.PRIVATE_LOCKED)
        assertFalse(locked.inputActionAvailable)

        val unlocked = fixture.coordinator.updateNotification(
            deviceUnlocked = true,
            capability = LocalPairingNotificationCapability.AVAILABLE,
        )
        assertEquals(unlocked.state, LocalPairingNotificationState.INPUT_READY)
        assertEquals(unlocked.actionWindowId, WINDOW_ONE)
    }

    @Test
    fun `notification and application callers share one submit path and always clear secrets`() = runBlocking {
        val fixture = Fixture()

        fixture.startReady(ATTEMPT_ONE, WINDOW_ONE, "observation-notification")
        val notificationResult = fixture.coordinator.submit(WINDOW_ONE, "0".repeat(6).toCharArray())
        assertTrue(notificationResult is AdbOperationResult.Success<*>)
        assertEquals(fixture.coordinator.state.value.stopReason, LocalPairingStopReason.SUCCEEDED)

        fixture.startReady(ATTEMPT_TWO, WINDOW_TWO, "observation-application")
        val applicationResult = fixture.coordinator.submit(WINDOW_TWO, "1".repeat(6).toCharArray())
        assertTrue(applicationResult is AdbOperationResult.Success<*>)

        assertEquals(fixture.pairCalls, 2)
        assertTrue(fixture.receivedSecrets.all { secret -> secret.withChars { chars -> chars.all { it == '\u0000' } } })
        assertNull(fixture.coordinator.state.value.window)
        assertEquals(fixture.stopDiscoveryCalls, 2)
    }

    @Test
    fun `service lost and session change are terminal and invalidate the window token`() {
        val serviceLost = Fixture()
        serviceLost.startReady(ATTEMPT_ONE, WINDOW_ONE, "observation-lost")
        serviceLost.coordinator.onDiscoveryState(discoveryState())
        assertEquals(serviceLost.coordinator.state.value.stopReason, LocalPairingStopReason.SERVICE_LOST)
        assertNull(serviceLost.coordinator.state.value.window)
        val staleCode = "0".repeat(6).toCharArray()
        val staleSubmit = runBlocking { serviceLost.coordinator.submit(WINDOW_ONE, staleCode) }
        assertFalse(staleSubmit is AdbOperationResult.Success<*>)
        assertTrue(staleCode.all { it == '\u0000' })

        val sessionChanged = Fixture()
        sessionChanged.startReady(ATTEMPT_TWO, WINDOW_TWO, "observation-session")
        sessionChanged.coordinator.onSessionChanged()
        assertEquals(sessionChanged.coordinator.state.value.stopReason, LocalPairingStopReason.SESSION_CHANGED)
        assertNull(sessionChanged.coordinator.state.value.window)
        assertEquals(sessionChanged.stopDiscoveryCalls, 1)
    }

    @Test
    fun `cancel system timeout and explicit pairing failure stop discovery once`() = runBlocking {
        val cancelled = Fixture()
        cancelled.coordinator.start(ATTEMPT_ONE, WINDOW_ONE)
        assertTrue(cancelled.coordinator.cancel(WINDOW_ONE) is AdbOperationResult.Success<*>)
        assertEquals(cancelled.coordinator.state.value.stopReason, LocalPairingStopReason.CANCELLED)

        val timedOut = Fixture()
        timedOut.coordinator.start(ATTEMPT_ONE, WINDOW_ONE)
        timedOut.coordinator.onSystemTimeout(WINDOW_ONE)
        assertEquals(timedOut.coordinator.state.value.stopReason, LocalPairingStopReason.SYSTEM_TIMEOUT)

        val failed = Fixture(pairResult = AdbOperationResult.Failure(com.sheen.adb.core.AdbError.PairingUnsupported))
        failed.startReady(ATTEMPT_ONE, WINDOW_ONE, "observation-failure")
        val result = failed.coordinator.submit(WINDOW_ONE, "0".repeat(6).toCharArray())
        assertTrue(result is AdbOperationResult.Failure)
        assertEquals(failed.coordinator.state.value.stopReason, LocalPairingStopReason.FAILED)

        assertEquals(cancelled.stopDiscoveryCalls, 1)
        assertEquals(timedOut.stopDiscoveryCalls, 1)
        assertEquals(failed.stopDiscoveryCalls, 1)
    }

    private class Fixture(
        private val pairResult: AdbOperationResult<Unit> = AdbOperationResult.Success(Unit),
    ) {
        var nowMillis = 0L
        var pairCalls = 0
        var stopDiscoveryCalls = 0
        val receivedSecrets = mutableListOf<PairingSecret>()
        val coordinator = LocalPairingCoordinator(
            clock = MonotonicClock { nowMillis },
            notificationPolicy = LocalPairingNotificationPolicy(),
            pairObservation = { _: WirelessServiceObservation, secret: PairingSecret ->
                pairCalls++
                receivedSecrets += secret
                pairResult
            },
            stopDiscovery = { stopDiscoveryCalls++ },
        )

        fun startReady(
            attemptId: PairingAttemptId,
            windowId: LocalPairingWindowId,
            observationId: String,
        ) {
            coordinator.start(attemptId, windowId)
            coordinator.onDiscoveryState(discoveryState(observation(observationId)))
            coordinator.updateNotification(true, LocalPairingNotificationCapability.AVAILABLE)
        }
    }

    private companion object {
        val ATTEMPT_ONE = PairingAttemptId.of("attempt-one")
        val ATTEMPT_TWO = PairingAttemptId.of("attempt-two")
        val WINDOW_ONE = LocalPairingWindowId.of("window-one")
        val WINDOW_TWO = LocalPairingWindowId.of("window-two")

        fun discoveryState(vararg observations: WirelessServiceObservation): WirelessDiscoveryState =
            WirelessDiscoveryState(generation = 1L, services = observations.toList())

        fun observation(id: String): WirelessServiceObservation = WirelessServiceObservation(
            observationId = WirelessObservationId(id),
            serviceType = WirelessServiceType.PAIRING,
            serviceName = "synthetic-service",
            addresses = emptyList(),
            port = 4711,
            status = WirelessServiceStatus.RESOLVED,
            lastSeenAt = 1L,
        )
    }
}
