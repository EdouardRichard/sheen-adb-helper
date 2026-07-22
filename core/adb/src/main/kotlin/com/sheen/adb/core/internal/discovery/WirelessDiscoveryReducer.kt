package com.sheen.adb.core.internal.discovery

import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessDisplayDevice
import com.sheen.adb.core.WirelessServiceObservation

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
            services + observation
        } else {
            services.toMutableList().also { it[existingIndex] = observation }
        }

        return WirelessDiscoveryState(
            generation = generation,
            networkKey = networkKey,
            services = updatedServices,
            devices = updatedServices.toDisplayDevices(),
        )
    }

    private fun List<WirelessServiceObservation>.toDisplayDevices(): List<WirelessDisplayDevice> {
        val groupedByVerifiedIdentity = linkedMapOf<Any, MutableList<WirelessServiceObservation>>()
        forEach { observation ->
            val groupingKey: Any = observation.verifiedDeviceId ?: observation.observationId
            groupedByVerifiedIdentity.getOrPut(groupingKey) { mutableListOf() }.add(observation)
        }
        return groupedByVerifiedIdentity.map { (groupingKey, observations) ->
            WirelessDisplayDevice(
                verifiedDeviceId = groupingKey as? com.sheen.adb.core.VerifiedWirelessDeviceId,
                observations = observations,
            )
        }
    }
}
