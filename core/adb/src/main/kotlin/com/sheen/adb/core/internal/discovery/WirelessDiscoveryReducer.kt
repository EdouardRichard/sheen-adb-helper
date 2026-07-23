package com.sheen.adb.core.internal.discovery

import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.VerifiedWirelessDeviceId

class WirelessDiscoveryReducer {
    fun reduce(
        state: WirelessDiscoveryState,
        event: WirelessDiscoveryEvent,
    ): WirelessDiscoveryState {
        if (event.generation != state.generation) return state

        return when (event) {
            is WirelessDiscoveryEvent.ServiceObserved -> state.withUpdatedObservation(event.observation)
        }
    }

    private fun WirelessDiscoveryState.withUpdatedObservation(
        observation: WirelessServiceObservation,
    ): WirelessDiscoveryState {
        val existingIndex = services.indexOfFirst { it.observationId == observation.observationId }
        val updatedServices = if (existingIndex < 0) {
            if (services.size >= MAX_DISCOVERY_SERVICES) services else services + observation
        } else {
            services.toMutableList().also { it[existingIndex] = observation }
        }

        return WirelessDiscoveryState(
            generation = generation,
            networkKey = networkKey,
            services = updatedServices,
        )
    }

    fun withVerifiedIdentity(
        state: WirelessDiscoveryState,
        observation: WirelessServiceObservation,
        verifiedDeviceId: VerifiedWirelessDeviceId?,
    ): WirelessDiscoveryState {
        val verified = observation.copy(verifiedDeviceId = verifiedDeviceId)
        val existingIndex = state.services.indexOfFirst { it.observationId == verified.observationId }
        val services = if (existingIndex >= 0) {
            state.services.toMutableList().also { it[existingIndex] = verified }
        } else {
            (listOf(verified) + state.services).take(MAX_DISCOVERY_SERVICES)
        }
        return WirelessDiscoveryState(
            generation = state.generation,
            networkKey = state.networkKey,
            services = services,
        )
    }

    private companion object {
        const val MAX_DISCOVERY_SERVICES = 15
    }
}
