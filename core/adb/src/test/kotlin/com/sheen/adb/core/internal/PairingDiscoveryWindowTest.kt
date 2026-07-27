package com.sheen.adb.core.internal

import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingDiscoveryPhase
import com.sheen.adb.core.PairingEndpointHandle
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.core.internal.pairing.PairingDiscoverySource
import com.sheen.adb.core.internal.pairing.PairingDiscoveryWindow
import java.util.concurrent.atomic.AtomicBoolean
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class PairingDiscoveryWindowTest {
    @Test
    fun `pairing discovery uses only the approved NSD service and expires at thirty seconds`() {
        val clock = FakeMonotonicClock()
        val source = FakePairingDiscoverySource()
        val window = PairingDiscoveryWindow(clock::nowMillis, source)

        assertTrue(window.start(ATTEMPT_ONE))
        assertEquals(source.starts.single().serviceType, WirelessServiceType.PAIRING)
        assertEquals(window.state.phase, PairingDiscoveryPhase.SCANNING)
        assertEquals(window.state.deadlineMillis, 30_000L)

        clock.advanceBy(29_999L)
        window.onClockAdvanced()
        assertEquals(window.state.phase, PairingDiscoveryPhase.SCANNING)
        assertFalse(source.starts.single().closed.get())

        clock.advanceBy(1L)
        window.onClockAdvanced()
        assertEquals(window.state.phase, PairingDiscoveryPhase.TIMED_OUT)
        assertTrue(source.starts.single().closed.get())
        assertNull(window.state.endpointHandle)

        clock.advanceBy(30_000L)
        window.onClockAdvanced()
        assertEquals(source.starts.size, 1, "timeout must not start another discovery attempt")
    }

    @Test
    fun `an active attempt is unique and timeout requires an explicit retry`() {
        val clock = FakeMonotonicClock()
        val source = FakePairingDiscoverySource()
        val window = PairingDiscoveryWindow(clock::nowMillis, source)

        assertTrue(window.start(ATTEMPT_ONE))
        assertFalse(window.start(ATTEMPT_ONE), "duplicate starts must not overlap")
        assertFalse(window.start(ATTEMPT_TWO), "a replacement attempt must not overlap")
        assertEquals(source.starts.size, 1)

        clock.advanceBy(30_000L)
        window.onClockAdvanced()
        assertEquals(window.state.phase, PairingDiscoveryPhase.TIMED_OUT)
        assertEquals(source.starts.size, 1)

        assertTrue(window.retry(ATTEMPT_TWO))
        assertEquals(source.starts.size, 2)
        assertEquals(window.state.attemptId, ATTEMPT_TWO)
        assertEquals(window.state.phase, PairingDiscoveryPhase.SCANNING)
        assertEquals(window.state.deadlineMillis, 60_000L)

        val staleHandle = PairingEndpointHandle.opaque("stale-observation")
        source.starts.first().onResolved(staleHandle)
        assertEquals(window.state.phase, PairingDiscoveryPhase.SCANNING)
        assertNull(window.state.endpointHandle, "late callbacks from an expired attempt must be ignored")

        val currentHandle = PairingEndpointHandle.opaque("current-observation")
        source.starts.last().onResolved(currentHandle)
        assertEquals(window.state.phase, PairingDiscoveryPhase.RESOLVED)
        assertSame(window.state.endpointHandle, currentHandle)
        assertTrue(source.starts.last().closed.get())
    }

    @Test
    fun `resolved device address and port remain behind an opaque endpoint handle`() {
        val clock = FakeMonotonicClock()
        val source = FakePairingDiscoverySource()
        val window = PairingDiscoveryWindow(clock::nowMillis, source)
        val endpointHandle = PairingEndpointHandle.opaque("opaque-observation-token")

        assertTrue(window.start(ATTEMPT_ONE))
        source.starts.single().onResolved(endpointHandle)

        assertSame(window.state.endpointHandle, endpointHandle)
        assertFalse(endpointHandle.toString().contains("opaque-observation-token"))
        val exposedNames = PairingEndpointHandle::class.java.methods
            .map { it.name.lowercase() }
            .filterNot { it in setOf("equals", "hashcode", "tostring") }
        assertTrue(
            exposedNames.none { name ->
                name.contains("host") ||
                    name.contains("address") ||
                    name.contains("port") ||
                    name.contains("subnet") ||
                    name.contains("probe")
            },
            "endpoint handles must not expose probing or raw endpoint APIs: $exposedNames",
        )
    }

    private class FakeMonotonicClock {
        private var nowMillis: Long = 0L

        fun nowMillis(): Long = nowMillis

        fun advanceBy(durationMillis: Long) {
            require(durationMillis >= 0L)
            nowMillis += durationMillis
        }
    }

    private class FakePairingDiscoverySource : PairingDiscoverySource {
        val starts = mutableListOf<Start>()

        override fun start(
            attemptId: PairingAttemptId,
            serviceType: WirelessServiceType,
            onResolved: (PairingEndpointHandle) -> Unit,
        ): AutoCloseable {
            val start = Start(
                attemptId = attemptId,
                serviceType = serviceType,
                onResolved = onResolved,
            )
            starts += start
            return AutoCloseable { start.closed.set(true) }
        }
    }

    private data class Start(
        val attemptId: PairingAttemptId,
        val serviceType: WirelessServiceType,
        val onResolved: (PairingEndpointHandle) -> Unit,
        val closed: AtomicBoolean = AtomicBoolean(false),
    )

    private companion object {
        val ATTEMPT_ONE = PairingAttemptId.of("pairing-attempt-one")
        val ATTEMPT_TWO = PairingAttemptId.of("pairing-attempt-two")
    }
}
