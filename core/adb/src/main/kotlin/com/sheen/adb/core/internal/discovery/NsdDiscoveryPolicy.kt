package com.sheen.adb.core.internal.discovery

import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryEvent

data class NsdDiscoveryRequest(
    val generation: Long,
    val apiLevel: Int,
    val currentNetwork: NsdNetworkRef?,
)

data class NsdNetworkRef(val value: String) {
    init {
        require(value.isNotBlank()) { "Network reference must not be blank." }
    }

    override fun toString(): String = "NsdNetworkRef(redacted)"
}

data class NsdServiceRef(
    val serviceType: String,
    val serviceName: String,
) {
    init {
        require(serviceType in NsdDiscoveryPolicy.APPROVED_SERVICE_TYPES) { "Unsupported NSD service type." }
        require(serviceName.isNotBlank()) { "Service reference must not be blank." }
    }

    override fun toString(): String = "NsdServiceRef(redacted)"
}

data class NsdResolvedService(
    val service: NsdServiceRef,
    val port: Int,
    val primaryAddress: WirelessAddress,
    val allAddresses: List<WirelessAddress>,
) {
    init {
        require(port in 1..65535) { "Resolved service port must be valid." }
        require(allAddresses.isNotEmpty() && primaryAddress in allAddresses) {
            "Resolved service requires its primary address."
        }
    }

    override fun toString(): String = "NsdResolvedService(redacted)"
}

enum class NsdDiscoveryFailure {
    NETWORK_UNAVAILABLE,
    PLATFORM_DISCOVERY_FAILED,
    PLATFORM_RESOLVE_FAILED,
    PLATFORM_OPERATION_FAILED,
}

enum class NsdPlatformFailure {
    DISCOVERY_FAILED,
    RESOLVE_FAILED,
    OPERATION_FAILED,
}

class NsdPlatformOperationException(
    val failure: NsdPlatformFailure,
) : RuntimeException()

sealed interface NsdDiscoveryStartResult {
    data object Started : NsdDiscoveryStartResult

    data class Rejected(val failure: NsdDiscoveryFailure) : NsdDiscoveryStartResult
}

interface NsdPlatformResource {
    fun cancel()
}

interface NsdScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): NsdPlatformResource
}

interface NsdDiscoveryCallbacks {
    fun onServiceFound(service: NsdServiceRef)
    fun onDiscoveryFailure(failure: NsdPlatformFailure)
}

interface NsdResolveCallbacks {
    fun onResolved(service: NsdResolvedService)
    fun onResolveFailure(failure: NsdPlatformFailure)
}

interface NsdNetworkChangeCallbacks {
    fun onNetworkChanged(network: NsdNetworkRef)
}

interface NsdDiscoveryObserver {
    fun onEvent(event: WirelessDiscoveryEvent)
    fun onFailure(failure: NsdDiscoveryFailure)
}

interface NsdDiscoveryPlatformGateway {
    fun acquireMulticastLock(): NsdPlatformResource

    fun discover(
        serviceType: String,
        network: NsdNetworkRef?,
        callbacks: NsdDiscoveryCallbacks,
    ): NsdPlatformResource

    fun resolve(
        service: NsdServiceRef,
        network: NsdNetworkRef?,
        callbacks: NsdResolveCallbacks,
    ): NsdPlatformResource

    fun registerNetworkChangeCallback(
        network: NsdNetworkRef,
        callbacks: NsdNetworkChangeCallbacks,
    ): NsdPlatformResource
}

class NsdDiscoveryPolicy {
    fun decisionFor(request: NsdDiscoveryRequest): NsdDiscoveryDecision? = when {
        request.apiLevel >= NETWORK_BOUND_DISCOVERY_API && request.currentNetwork == null -> null
        request.apiLevel >= NETWORK_BOUND_DISCOVERY_API -> NsdDiscoveryDecision(
            network = request.currentNetwork,
            acquireMulticastLock = false,
            observeNetworkChanges = true,
            publishAllAddresses = request.apiLevel >= ALL_ADDRESSES_API,
        )

        else -> NsdDiscoveryDecision(
            network = null,
            acquireMulticastLock = true,
            observeNetworkChanges = false,
            publishAllAddresses = false,
        )
    }

    companion object {
        const val DEFAULT_LAN_DISCOVERY_CUTOFF_MILLIS: Long = 10_000L
        const val NETWORK_BOUND_DISCOVERY_API: Int = 33
        const val ALL_ADDRESSES_API: Int = 34
        val APPROVED_SERVICE_TYPES: Set<String> = setOf(
            "_adb-tls-pairing._tcp",
            "_adb-tls-connect._tcp",
        )
    }
}

data class NsdDiscoveryDecision(
    val network: NsdNetworkRef?,
    val acquireMulticastLock: Boolean,
    val observeNetworkChanges: Boolean,
    val publishAllAddresses: Boolean,
)
