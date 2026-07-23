package com.sheen.adb.feature.devices

import com.sheen.adb.core.VerifiedWirelessDeviceId
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DevicesDiscoveryReducerTest {
    private val reducer = DevicesDiscoveryReducer()

    @Test
    fun `start empty failure and cancellation remain distinct states with manual fallback`() {
        val scanning = reduce(event = DevicesDiscoveryEvent.Start(generation = 7L))
        assertEquals(scanning.state.phase, DevicesDiscoveryPhase.SCANNING)
        assertEquals(scanning.state.generation, 7L)

        val empty = reduce(scanning.state, DevicesDiscoveryEvent.Snapshot(WirelessDiscoveryState(7L)))
        assertEquals(empty.state.phase, DevicesDiscoveryPhase.EMPTY)
        assertTrue(empty.state.items.isEmpty())

        val failed = reduce(empty.state, DevicesDiscoveryEvent.Failed(DevicesDiscoveryFailure.NETWORK_UNAVAILABLE))
        assertEquals(failed.state.phase, DevicesDiscoveryPhase.ERROR)
        assertEquals(failed.state.failure, DevicesDiscoveryFailure.NETWORK_UNAVAILABLE)

        val cancelled = reduce(scanning.state, DevicesDiscoveryEvent.Cancelled)
        assertEquals(cancelled.state.phase, DevicesDiscoveryPhase.CANCELLED)

        val manual = reduce(cancelled.state, DevicesDiscoveryEvent.UseManualAddress)
        assertTrue(manual.effects.single() is DevicesDiscoveryEffect.OpenManualAddress)
    }

    @Test
    fun `only equal verified identity merges pairing and connect while matching name and address stay unknown`() {
        val identity = VerifiedWirelessDeviceId("verified-synthetic")
        val sharedAddress = WirelessAddress.Ipv4(192, 0, 2, 30)
        val snapshot = WirelessDiscoveryState(
            generation = 3L,
            services = listOf(
                observation("verified-pair", WirelessServiceType.PAIRING, identity, sharedAddress, 41_001),
                observation("verified-connect", WirelessServiceType.CONNECT, identity, sharedAddress, 41_002),
                observation("unknown-pair", WirelessServiceType.PAIRING, null, sharedAddress, 41_001),
                observation("unknown-connect", WirelessServiceType.CONNECT, null, sharedAddress, 41_002),
            ),
        )

        val result = reduce(
            DevicesDiscoveryState(phase = DevicesDiscoveryPhase.SCANNING, generation = 3L),
            DevicesDiscoveryEvent.Snapshot(snapshot),
        )

        assertEquals(result.state.phase, DevicesDiscoveryPhase.CONTENT)
        assertEquals(result.state.items.size, 3)
        val merged = result.state.items.single { it.relation == DevicesDiscoveryRelation.VERIFIED }
        assertEquals(merged.serviceTypes, setOf(WirelessServiceType.PAIRING, WirelessServiceType.CONNECT))
        assertTrue(merged.pairingTarget != null)
        assertTrue(merged.connectTarget != null)
        assertEquals(result.state.items.count { it.relation == DevicesDiscoveryRelation.UNKNOWN }, 2)
        assertTrue(result.state.items.filter { it.relation == DevicesDiscoveryRelation.UNKNOWN }.all { it.serviceTypes.size == 1 })
        assertFalse(result.state.toString().contains("192.0.2.30"))
        assertFalse(result.state.toString().contains("synthetic-service"))
    }

    @Test
    fun `lost and port changed observations replace an item without presenting stale reachability`() {
        val initial = WirelessDiscoveryState(
            generation = 4L,
            services = listOf(observation("changing", WirelessServiceType.CONNECT, port = 42_001)),
        )
        val content = reduce(
            DevicesDiscoveryState(phase = DevicesDiscoveryPhase.SCANNING, generation = 4L),
            DevicesDiscoveryEvent.Snapshot(initial),
        )
        assertEquals(content.state.items.single().endpointLabel, "IPv4 · 端口 42001")
        assertTrue(content.state.items.single().selectable)

        val changed = initial.services.single().copy(port = 42_099, lastSeenAt = 2L)
        val changedResult = reduce(content.state, DevicesDiscoveryEvent.Snapshot(initial.copy(services = listOf(changed))))
        assertEquals(changedResult.state.items.single().endpointLabel, "IPv4 · 端口 42099")

        val lost = changed.copy(status = WirelessServiceStatus.LOST, lastSeenAt = 3L)
        val lostResult = reduce(changedResult.state, DevicesDiscoveryEvent.Snapshot(initial.copy(services = listOf(lost))))
        assertEquals(lostResult.state.items.single().reachability, DevicesDiscoveryReachability.LOST)
        assertFalse(lostResult.state.items.single().selectable)
        assertNull(lostResult.state.items.single().connectTarget)
    }

    @Test
    fun `target selection always requires explicit confirmation and emits only project owned effects`() {
        val snapshot = WirelessDiscoveryState(
            generation = 5L,
            services = listOf(
                observation("pair", WirelessServiceType.PAIRING, port = 43_001),
                observation("connect", WirelessServiceType.CONNECT, port = 43_002),
            ),
        )
        val content = reduce(
            DevicesDiscoveryState(phase = DevicesDiscoveryPhase.SCANNING, generation = 5L),
            DevicesDiscoveryEvent.Snapshot(snapshot),
        ).state
        val pairingItem = content.items.single { WirelessServiceType.PAIRING in it.serviceTypes }
        val connectItem = content.items.single { WirelessServiceType.CONNECT in it.serviceTypes }

        val pairingPending = reduce(content, DevicesDiscoveryEvent.SelectPairing(pairingItem.pairingTarget!!))
        assertTrue(pairingPending.state.pendingSelection is DevicesDiscoverySelection.Pairing)
        assertTrue(pairingPending.effects.isEmpty())
        val pairingConfirmed = reduce(pairingPending.state, DevicesDiscoveryEvent.ConfirmSelection)
        assertTrue(pairingConfirmed.effects.single() is DevicesDiscoveryEffect.OpenCodePairing)

        val connectPending = reduce(content, DevicesDiscoveryEvent.SelectConnect(connectItem.connectTarget!!))
        assertTrue(connectPending.state.pendingSelection is DevicesDiscoverySelection.Connect)
        assertTrue(connectPending.effects.isEmpty())
        val connectConfirmed = reduce(connectPending.state, DevicesDiscoveryEvent.ConfirmSelection)
        assertTrue(connectConfirmed.effects.single() is DevicesDiscoveryEffect.Connect)

        val dismissed = reduce(connectPending.state, DevicesDiscoveryEvent.DismissSelection)
        assertNull(dismissed.state.pendingSelection)
        assertTrue(dismissed.effects.isEmpty())
    }

    @Test
    fun `late generation snapshot is ignored`() {
        val state = DevicesDiscoveryState(phase = DevicesDiscoveryPhase.SCANNING, generation = 9L)
        val late = WirelessDiscoveryState(
            generation = 8L,
            services = listOf(observation("late", WirelessServiceType.CONNECT)),
        )

        assertEquals(reduce(state, DevicesDiscoveryEvent.Snapshot(late)).state, state)
    }

    private fun reduce(
        state: DevicesDiscoveryState = DevicesDiscoveryState(),
        event: DevicesDiscoveryEvent,
    ): DevicesDiscoveryReduction = reducer.reduce(state, event)

    private fun observation(
        id: String,
        type: WirelessServiceType,
        verifiedDeviceId: VerifiedWirelessDeviceId? = null,
        address: WirelessAddress = WirelessAddress.Ipv4(192, 0, 2, 40),
        port: Int = 44_001,
    ): WirelessServiceObservation = WirelessServiceObservation(
        observationId = WirelessObservationId(id),
        serviceType = type,
        serviceName = "synthetic-service",
        addresses = listOf(address),
        port = port,
        status = WirelessServiceStatus.RESOLVED,
        verifiedDeviceId = verifiedDeviceId,
        lastSeenAt = 1L,
    )
}
