package com.sheen.adb.core

import java.util.Collections

data class WirelessObservationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Observation identifier must not be blank." }
    }

    override fun toString(): String = "WirelessObservationId(redacted)"
}

data class WirelessNetworkKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Network identifier must not be blank." }
    }

    override fun toString(): String = "WirelessNetworkKey(redacted)"
}

data class VerifiedWirelessDeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Verified device identifier must not be blank." }
    }

    override fun toString(): String = "VerifiedWirelessDeviceId(redacted)"
}

sealed interface WirelessAddress {
    data class Ipv4(
        val firstOctet: Int,
        val secondOctet: Int,
        val thirdOctet: Int,
        val fourthOctet: Int,
    ) : WirelessAddress {
        init {
            listOf(firstOctet, secondOctet, thirdOctet, fourthOctet).forEach {
                require(it in 0..255) { "IPv4 octets must be between 0 and 255." }
            }
        }

        override fun toString(): String = "WirelessAddress.Ipv4(redacted)"
    }

    class Ipv6(
        segments: List<Int>,
        val scopeId: String? = null,
    ) : WirelessAddress {
        val segments: List<Int> = immutableList(segments)

        init {
            require(segments.size == 8) { "IPv6 addresses must have exactly eight segments." }
            require(segments.all { it in 0..0xffff }) {
                "IPv6 segments must be between 0 and 65535."
            }
            require(scopeId == null || scopeId.isNotBlank()) { "IPv6 scope identifier must not be blank." }
        }

        fun copy(
            segments: List<Int> = this.segments,
            scopeId: String? = this.scopeId,
        ): Ipv6 = Ipv6(segments, scopeId)

        override fun equals(other: Any?): Boolean =
            other is Ipv6 && segments == other.segments && scopeId == other.scopeId

        override fun hashCode(): Int = 31 * segments.hashCode() + (scopeId?.hashCode() ?: 0)

        override fun toString(): String = "WirelessAddress.Ipv6(redacted)"
    }
}

enum class WirelessServiceType {
    PAIRING,
    CONNECT;

    companion object {
        fun fromDnsSdType(value: String): WirelessServiceType? = when (value) {
            "_adb-tls-pairing._tcp" -> PAIRING
            "_adb-tls-connect._tcp" -> CONNECT
            else -> null
        }
    }
}

enum class WirelessServiceStatus {
    DISCOVERED,
    RESOLVING,
    RESOLVED,
    LOST,
    UNREACHABLE,
    FAILED,
}

class WirelessServiceObservation(
    val observationId: WirelessObservationId,
    val serviceType: WirelessServiceType,
    val serviceName: String,
    addresses: List<WirelessAddress>,
    val port: Int,
    val status: WirelessServiceStatus,
    val verifiedDeviceId: VerifiedWirelessDeviceId? = null,
    val lastSeenAt: Long,
) {
    val addresses: List<WirelessAddress> = immutableList(addresses)

    init {
        require(serviceName.isNotBlank()) { "Service name must not be blank." }
        require(port in 1..65535) { "Port must be between 1 and 65535." }
    }

    override fun toString(): String = "WirelessServiceObservation(redacted)"
}

class WirelessDisplayDevice(
    val verifiedDeviceId: VerifiedWirelessDeviceId?,
    observations: List<WirelessServiceObservation>,
) {
    val observations: List<WirelessServiceObservation> = immutableList(observations)
    val serviceTypes: Set<WirelessServiceType> = immutableSet(observations.map { it.serviceType })

    init {
        require(observations.isNotEmpty()) { "A display device must contain an observation." }
    }

    override fun toString(): String = "WirelessDisplayDevice(redacted)"
}

class WirelessDiscoveryState(
    val generation: Long,
    val networkKey: WirelessNetworkKey? = null,
    services: List<WirelessServiceObservation> = emptyList(),
    devices: List<WirelessDisplayDevice> = emptyList(),
) {
    val services: List<WirelessServiceObservation> = immutableList(services)
    val devices: List<WirelessDisplayDevice> = immutableList(devices)

    override fun toString(): String =
        "WirelessDiscoveryState(generation=$generation, serviceCount=${services.size}, deviceCount=${devices.size})"
}

enum class WirelessDiscoveryMode {
    LAN_FOREGROUND,
    LOCAL_PAIRING,
}

enum class WirelessDiscoveryPhase {
    IDLE,
    STARTING,
    DISCOVERING,
    STOPPING,
    STOPPED,
    FAILED,
}

class WirelessDiscoverySession(
    val generation: Long,
    val mode: WirelessDiscoveryMode,
    val networkKey: WirelessNetworkKey,
    val startedAt: Long,
    val deadline: Long,
    val phase: WirelessDiscoveryPhase,
    services: List<WirelessServiceObservation> = emptyList(),
) {
    val services: List<WirelessServiceObservation> = immutableList(services)

    init {
        require(deadline >= startedAt) { "Discovery deadline must not precede its start." }
    }

    override fun toString(): String =
        "WirelessDiscoverySession(generation=$generation, phase=$phase, serviceCount=${services.size})"
}

sealed interface WirelessDiscoveryEvent {
    val generation: Long

    data class ServiceObserved(
        override val generation: Long,
        val observation: WirelessServiceObservation,
    ) : WirelessDiscoveryEvent {
        override fun toString(): String = "WirelessDiscoveryEvent.ServiceObserved(redacted)"
    }
}

private fun <T> immutableList(values: List<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
