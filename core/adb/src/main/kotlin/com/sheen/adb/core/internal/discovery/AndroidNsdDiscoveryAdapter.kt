package com.sheen.adb.core.internal.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor

class AndroidNsdDiscoveryAdapter(
    private val platform: NsdDiscoveryPlatformGateway,
    private val policy: NsdDiscoveryPolicy,
    private val scheduler: NsdScheduler,
    private val observer: NsdDiscoveryObserver,
) : AutoCloseable {
    private val monitor = Any()
    private var active: ActiveDiscovery? = null

    fun start(request: NsdDiscoveryRequest): NsdDiscoveryStartResult = synchronized(monitor) {
        end(active)
        val decision = policy.decisionFor(request)
            ?: return NsdDiscoveryStartResult.Rejected(NsdDiscoveryFailure.NETWORK_UNAVAILABLE)
        val session = ActiveDiscovery(request.generation, decision)
        active = session

        try {
            if (decision.acquireMulticastLock) session.track(platform.acquireMulticastLock())
            if (decision.observeNetworkChanges) {
                session.track(platform.registerNetworkChangeCallback(requireNotNull(decision.network), networkCallbacks(session)))
            }
            NsdDiscoveryPolicy.APPROVED_SERVICE_TYPES.forEach { serviceType ->
                session.track(platform.discover(serviceType, decision.network, discoveryCallbacks(session, serviceType)))
            }
            session.track(
                scheduler.schedule(NsdDiscoveryPolicy.DEFAULT_LAN_DISCOVERY_CUTOFF_MILLIS) {
                    synchronized(monitor) { if (isActive(session)) end(session) }
                },
            )
            NsdDiscoveryStartResult.Started
        } catch (_: Exception) {
            end(session)
            NsdDiscoveryStartResult.Rejected(NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
        }
    }

    fun stop() = synchronized(monitor) { end(active) }

    fun cancel() = stop()

    override fun close() = stop()

    private fun discoveryCallbacks(session: ActiveDiscovery, serviceType: String): NsdDiscoveryCallbacks =
        object : NsdDiscoveryCallbacks {
            override fun onServiceFound(service: NsdServiceRef) = synchronized(monitor) {
                if (!isActive(session) || service.serviceType != serviceType) return@synchronized
                try {
                    session.track(platform.resolve(service, session.decision.network, resolveCallbacks(session)))
                } catch (_: Exception) {
                    fail(session, NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
                }
            }

            override fun onDiscoveryFailure(failure: NsdPlatformFailure) = synchronized(monitor) {
                if (isActive(session)) fail(session, failure.asDiscoveryFailure())
            }
        }

    private fun resolveCallbacks(session: ActiveDiscovery): NsdResolveCallbacks = object : NsdResolveCallbacks {
        override fun onResolved(service: NsdResolvedService) = synchronized(monitor) {
            if (!isActive(session)) return@synchronized
            val type = WirelessServiceType.fromDnsSdType(service.service.serviceType) ?: return@synchronized
            val addresses = if (session.decision.publishAllAddresses) service.allAddresses else listOf(service.primaryAddress)
            val observation = WirelessServiceObservation(
                observationId = WirelessObservationId("nsd-${session.generation}-${session.nextObservationId++}"),
                serviceType = type,
                serviceName = "redacted",
                addresses = addresses,
                port = service.port,
                status = WirelessServiceStatus.RESOLVED,
                lastSeenAt = 0L,
            )
            notifyEvent(WirelessDiscoveryEvent.ServiceObserved(session.generation, observation))
        }

        override fun onResolveFailure(failure: NsdPlatformFailure) = synchronized(monitor) {
            if (isActive(session)) fail(session, failure.asResolveFailure())
        }
    }

    private fun networkCallbacks(session: ActiveDiscovery): NsdNetworkChangeCallbacks = object : NsdNetworkChangeCallbacks {
        override fun onNetworkChanged(network: NsdNetworkRef) = synchronized(monitor) {
            if (isActive(session)) end(session)
        }
    }

    private fun NsdPlatformFailure.asDiscoveryFailure(): NsdDiscoveryFailure = when (this) {
        NsdPlatformFailure.DISCOVERY_FAILED -> NsdDiscoveryFailure.PLATFORM_DISCOVERY_FAILED
        else -> NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED
    }

    private fun NsdPlatformFailure.asResolveFailure(): NsdDiscoveryFailure = when (this) {
        NsdPlatformFailure.RESOLVE_FAILED -> NsdDiscoveryFailure.PLATFORM_RESOLVE_FAILED
        else -> NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED
    }

    private fun fail(session: ActiveDiscovery, failure: NsdDiscoveryFailure) {
        end(session)
        try {
            observer.onFailure(failure)
        } catch (_: Exception) {
            // Observers are outside the platform callback boundary.
        }
    }

    private fun notifyEvent(event: WirelessDiscoveryEvent) {
        try {
            observer.onEvent(event)
        } catch (_: Exception) {
            // A consumer failure must not escape an Android callback.
        }
    }

    private fun isActive(session: ActiveDiscovery): Boolean = active === session && !session.ended

    private fun end(session: ActiveDiscovery?) {
        if (session == null || session.ended) return
        session.ended = true
        if (active === session) active = null
        session.resources.forEach { resource ->
            try {
                resource.cancel()
            } catch (_: Exception) {
                // Platform cleanup is best-effort and intentionally silent.
            }
        }
    }

    private class ActiveDiscovery(
        val generation: Long,
        val decision: NsdDiscoveryDecision,
    ) {
        val resources = mutableListOf<NsdPlatformResource>()
        var ended = false
        var nextObservationId = 0

        fun track(resource: NsdPlatformResource) {
            if (ended) {
                try {
                    resource.cancel()
                } catch (_: Exception) {
                    // A synchronously-completed operation still owns this resource.
                }
            } else {
                resources += resource
            }
        }
    }
}

internal class AndroidNsdDiscoveryPlatformGateway(
    context: Context,
    private val apiLevel: Int = Build.VERSION.SDK_INT,
    private val callbackExecutor: Executor = context.mainExecutor,
) : NsdDiscoveryPlatformGateway {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val networks = mutableMapOf<NsdNetworkRef, Network>()

    fun currentNetwork(): NsdNetworkRef? = connectivityManager.activeNetwork?.let(::rememberNetwork)

    override fun acquireMulticastLock(): NsdPlatformResource = platformOperation {
        val lock = wifiManager.createMulticastLock("sheen-adb-discovery")
        lock.setReferenceCounted(false)
        lock.acquire()
        MulticastLockResource(lock)
    }

    override fun discover(
        serviceType: String,
        network: NsdNetworkRef?,
        callbacks: NsdDiscoveryCallbacks,
    ): NsdPlatformResource = platformOperation {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val reference = serviceInfo.toServiceRef(serviceType) ?: run {
                    callbacks.onDiscoveryFailure(NsdPlatformFailure.OPERATION_FAILED)
                    return
                }
                callbacks.onServiceFound(reference)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                callbacks.onDiscoveryFailure(NsdPlatformFailure.DISCOVERY_FAILED)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                callbacks.onDiscoveryFailure(NsdPlatformFailure.DISCOVERY_FAILED)
            }
        }
        if (apiLevel >= NsdDiscoveryPolicy.NETWORK_BOUND_DISCOVERY_API) {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                requireNetwork(network),
                callbackExecutor,
                listener,
            )
        } else {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        CancelOnceResource { nsdManager.stopServiceDiscovery(listener) }
    }

    override fun resolve(
        service: NsdServiceRef,
        network: NsdNetworkRef?,
        callbacks: NsdResolveCallbacks,
    ): NsdPlatformResource = platformOperation {
        val info = NsdServiceInfo().apply {
            serviceName = service.serviceName
            serviceType = service.serviceType
            if (apiLevel >= NsdDiscoveryPolicy.NETWORK_BOUND_DISCOVERY_API) setNetwork(requireNetwork(network))
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                callbacks.onResolveFailure(NsdPlatformFailure.RESOLVE_FAILED)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val resolved = serviceInfo.toResolvedService(service) ?: run {
                    callbacks.onResolveFailure(NsdPlatformFailure.OPERATION_FAILED)
                    return
                }
                callbacks.onResolved(resolved)
            }
        }
        if (apiLevel >= NsdDiscoveryPolicy.NETWORK_BOUND_DISCOVERY_API) {
            nsdManager.resolveService(info, callbackExecutor, listener)
        } else {
            nsdManager.resolveService(info, listener)
        }
        CancelOnceResource {
            if (apiLevel >= NsdDiscoveryPolicy.ALL_ADDRESSES_API) nsdManager.stopServiceResolution(listener)
        }
    }

    override fun registerNetworkChangeCallback(
        network: NsdNetworkRef,
        callbacks: NsdNetworkChangeCallbacks,
    ): NsdPlatformResource = platformOperation {
        val expected = requireNetwork(network)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (network != expected) callbacks.onNetworkChanged(rememberNetwork(network))
            }

            override fun onLost(network: Network) {
                if (network == expected) callbacks.onNetworkChanged(NsdNetworkRef("network-lost"))
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        CancelOnceResource { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun requireNetwork(reference: NsdNetworkRef?): Network =
        reference?.let(networks::get) ?: throw NsdPlatformOperationException(NsdPlatformFailure.OPERATION_FAILED)

    private fun rememberNetwork(network: Network): NsdNetworkRef =
        NsdNetworkRef("network-${network.networkHandle}").also { networks[it] = network }

    private fun NsdServiceInfo.toServiceRef(expectedType: String): NsdServiceRef? = try {
        if (serviceType != expectedType || serviceName.isNullOrBlank()) null
        else NsdServiceRef(expectedType, serviceName)
    } catch (_: Exception) {
        null
    }

    private fun NsdServiceInfo.toResolvedService(service: NsdServiceRef): NsdResolvedService? = try {
        val addresses = if (apiLevel >= NsdDiscoveryPolicy.ALL_ADDRESSES_API) hostAddresses else listOfNotNull(host)
        val translated = addresses.mapNotNull { address -> address.toWirelessAddress() }
        val primary = translated.firstOrNull() ?: return null
        NsdResolvedService(service, port, primary, translated)
    } catch (_: Exception) {
        null
    }

    private fun InetAddress.toWirelessAddress(): WirelessAddress? = when (this) {
        is Inet4Address -> WirelessAddress.Ipv4(
            address[0].toInt() and 0xff,
            address[1].toInt() and 0xff,
            address[2].toInt() and 0xff,
            address[3].toInt() and 0xff,
        )

        is Inet6Address -> WirelessAddress.Ipv6(
            segments = address.asList().chunked(2) { (first, second) ->
                ((first.toInt() and 0xff) shl 8) or (second.toInt() and 0xff)
            },
            scopeId = scopeId.takeIf { it != 0 }?.let { "scope-$it" },
        )

        else -> null
    }

    private inline fun <T> platformOperation(block: () -> T): T = try {
        block()
    } catch (exception: NsdPlatformOperationException) {
        throw exception
    } catch (_: Exception) {
        throw NsdPlatformOperationException(NsdPlatformFailure.OPERATION_FAILED)
    }
}

internal class AndroidNsdScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : NsdScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): NsdPlatformResource {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return CancelOnceResource { handler.removeCallbacks(runnable) }
    }
}

private class MulticastLockResource(
    private val lock: WifiManager.MulticastLock,
) : NsdPlatformResource {
    private var released = false

    override fun cancel() {
        if (released) return
        released = true
        if (lock.isHeld) lock.release()
    }
}

private class CancelOnceResource(
    private val cancelAction: () -> Unit,
) : NsdPlatformResource {
    private var cancelled = false

    override fun cancel() {
        if (cancelled) return
        cancelled = true
        cancelAction()
    }
}
