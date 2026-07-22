package com.sheen.adb.core.internal.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessDiscoverySource
import com.sheen.adb.core.WirelessDiscoverySourceFactory
import com.sheen.adb.core.WirelessDiscoverySourceFailure
import com.sheen.adb.core.WirelessDiscoverySourceObserver
import com.sheen.adb.core.WirelessDiscoverySourceRequest
import com.sheen.adb.core.WirelessDiscoverySourceStartResult
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

internal class AndroidNsdWirelessDiscoverySourceFactory(context: Context) : WirelessDiscoverySourceFactory {
    private val applicationContext = context.applicationContext

    override fun create(observer: WirelessDiscoverySourceObserver): WirelessDiscoverySource =
        AndroidNsdWirelessDiscoverySource(applicationContext, observer)
}

private class AndroidNsdWirelessDiscoverySource(
    private val applicationContext: Context,
    private val observer: WirelessDiscoverySourceObserver,
) : WirelessDiscoverySource {
    private val monitor = Any()
    private var adapter: AndroidNsdDiscoveryAdapter? = null
    private var gateway: AndroidNsdDiscoveryPlatformGateway? = null
    private var closed = false

    override fun start(request: WirelessDiscoverySourceRequest): WirelessDiscoverySourceStartResult {
        val components = try {
            synchronized(monitor) {
                if (closed) {
                    return WirelessDiscoverySourceStartResult.Rejected(
                        WirelessDiscoverySourceFailure.PLATFORM_FAILURE,
                    )
                }
                val platform = gateway ?: AndroidNsdDiscoveryPlatformGateway(applicationContext).also { gateway = it }
                val activeAdapter = adapter ?: AndroidNsdDiscoveryAdapter(
                    platform = platform,
                    policy = NsdDiscoveryPolicy(),
                    scheduler = AndroidNsdScheduler(),
                    observer = object : NsdDiscoveryObserver {
                        override fun onEvent(event: WirelessDiscoveryEvent) = observer.onEvent(event)

                        override fun onFailure(failure: NsdDiscoveryFailure) {
                            observer.onFailure(failure.toSourceFailure())
                        }
                    },
                ).also { adapter = it }
                platform to activeAdapter
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (_: SecurityException) {
            return WirelessDiscoverySourceStartResult.Rejected(WirelessDiscoverySourceFailure.PERMISSION_UNAVAILABLE)
        } catch (_: Exception) {
            return WirelessDiscoverySourceStartResult.Rejected(WirelessDiscoverySourceFailure.PLATFORM_FAILURE)
        }
        val (platform, activeAdapter) = components
        val result = try {
            activeAdapter.start(
                NsdDiscoveryRequest(
                    generation = request.generation,
                    apiLevel = Build.VERSION.SDK_INT,
                    currentNetwork = platform.currentNetwork(),
                ),
            ).toSourceResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (_: SecurityException) {
            WirelessDiscoverySourceStartResult.Rejected(WirelessDiscoverySourceFailure.PERMISSION_UNAVAILABLE)
        } catch (_: Exception) {
            WirelessDiscoverySourceStartResult.Rejected(WirelessDiscoverySourceFailure.PLATFORM_FAILURE)
        }
        if (synchronized(monitor) { closed }) {
            activeAdapter.close()
            return WirelessDiscoverySourceStartResult.Rejected(WirelessDiscoverySourceFailure.PLATFORM_FAILURE)
        }
        return result
    }

    override fun close() {
        val adapterToClose = synchronized(monitor) {
            if (closed) {
                null
            } else {
                closed = true
                adapter.also {
                    adapter = null
                    gateway = null
                }
            }
        }
        adapterToClose?.close()
    }

    private fun NsdDiscoveryStartResult.toSourceResult(): WirelessDiscoverySourceStartResult = when (this) {
        NsdDiscoveryStartResult.Started -> WirelessDiscoverySourceStartResult.Started
        is NsdDiscoveryStartResult.Rejected -> WirelessDiscoverySourceStartResult.Rejected(failure.toSourceFailure())
    }

    private fun NsdDiscoveryFailure.toSourceFailure(): WirelessDiscoverySourceFailure = when (this) {
        NsdDiscoveryFailure.NETWORK_UNAVAILABLE -> WirelessDiscoverySourceFailure.NETWORK_UNAVAILABLE
        NsdDiscoveryFailure.PLATFORM_RESOLVE_FAILED -> WirelessDiscoverySourceFailure.RESOLUTION_FAILED
        NsdDiscoveryFailure.PLATFORM_DISCOVERY_FAILED,
        NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED,
        -> WirelessDiscoverySourceFailure.PLATFORM_FAILURE
    }
}

class AndroidNsdDiscoveryAdapter(
    private val platform: NsdDiscoveryPlatformGateway,
    private val policy: NsdDiscoveryPolicy,
    private val scheduler: NsdScheduler,
    private val observer: NsdDiscoveryObserver,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val monitor = Any()
    private var active: ActiveDiscovery? = null
    private var closed = false

    fun start(request: NsdDiscoveryRequest): NsdDiscoveryStartResult = synchronized(monitor) {
        if (closed) return NsdDiscoveryStartResult.Rejected(NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
        end(active)
        val decision = policy.decisionFor(request)
            ?: return NsdDiscoveryStartResult.Rejected(NsdDiscoveryFailure.NETWORK_UNAVAILABLE)
        val session = ActiveDiscovery(request.generation, decision)
        active = session

        try {
            if (decision.acquireMulticastLock) {
                session.track(platform.acquireMulticastLock())
                if (!isActive(session)) return rejectedTerminal(session)
            }
            if (decision.observeNetworkChanges) {
                session.track(platform.registerNetworkChangeCallback(requireNotNull(decision.network), networkCallbacks(session)))
                if (!isActive(session)) return rejectedTerminal(session)
            }
            for (serviceType in NsdDiscoveryPolicy.APPROVED_SERVICE_TYPES) {
                session.track(platform.discover(serviceType, decision.network, discoveryCallbacks(session, serviceType)))
                if (!isActive(session)) return rejectedTerminal(session)
            }
            session.track(
                scheduler.schedule(NsdDiscoveryPolicy.DEFAULT_LAN_DISCOVERY_CUTOFF_MILLIS) {
                    synchronized(monitor) { if (isActive(session)) end(session) }
                },
            )
            if (isActive(session)) NsdDiscoveryStartResult.Started else rejectedTerminal(session)
        } catch (error: CancellationException) {
            endIgnoringCleanupControl(session)
            throw error
        } catch (error: InterruptedException) {
            endIgnoringCleanupControl(session)
            throw error
        } catch (error: SecurityException) {
            endIgnoringCleanupControl(session)
            throw error
        } catch (_: Exception) {
            end(session)
            rejectedTerminal(session)
        }
    }

    fun stop() = synchronized(monitor) { end(active) }

    fun cancel() = stop()

    override fun close() = synchronized(monitor) {
        if (closed) return@synchronized
        closed = true
        end(active)
    }

    private fun discoveryCallbacks(session: ActiveDiscovery, serviceType: String): NsdDiscoveryCallbacks =
        object : NsdDiscoveryCallbacks {
            override fun onServiceFound(service: NsdServiceRef) {
                var shouldNotifyFailure = false
                synchronized(monitor) {
                    if (!isActive(session) || service.serviceType != serviceType) return@synchronized
                    try {
                        session.track(platform.resolve(service, session.decision.network, resolveCallbacks(session)))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (error: SecurityException) {
                        throw error
                    } catch (_: Exception) {
                        shouldNotifyFailure = failLocked(session, NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
                    }
                }
                if (shouldNotifyFailure) notifyFailure(NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
            }

            override fun onDiscoveryFailure(failure: NsdPlatformFailure) {
                val mapped = failure.asDiscoveryFailure()
                val shouldNotifyFailure = synchronized(monitor) {
                    isActive(session) && failLocked(session, mapped)
                }
                if (shouldNotifyFailure) notifyFailure(mapped)
            }
        }

    private fun resolveCallbacks(session: ActiveDiscovery): NsdResolveCallbacks = object : NsdResolveCallbacks {
        override fun onResolved(service: NsdResolvedService) {
            var event: WirelessDiscoveryEvent? = null
            var shouldNotifyFailure = false
            synchronized(monitor) {
                if (!isActive(session)) return@synchronized
                try {
                    val type = WirelessServiceType.fromDnsSdType(service.service.serviceType) ?: return@synchronized
                    val addresses = if (session.decision.publishAllAddresses) service.allAddresses else listOf(service.primaryAddress)
                    val observation = WirelessServiceObservation(
                        observationId = session.observationIdFor(service.service),
                        serviceType = type,
                        serviceName = service.service.serviceName,
                        addresses = addresses,
                        port = service.port,
                        status = WirelessServiceStatus.RESOLVED,
                        lastSeenAt = monotonicNanos(),
                    )
                    event = WirelessDiscoveryEvent.ServiceObserved(session.generation, observation)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: InterruptedException) {
                    throw error
                } catch (error: SecurityException) {
                    throw error
                } catch (_: Exception) {
                    shouldNotifyFailure = failLocked(session, NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
                }
            }
            event?.let(::notifyEvent)
            if (shouldNotifyFailure) notifyFailure(NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)
        }

        override fun onResolveFailure(failure: NsdPlatformFailure) {
            val mapped = failure.asResolveFailure()
            val shouldNotifyFailure = synchronized(monitor) {
                isActive(session) && failLocked(session, mapped)
            }
            if (shouldNotifyFailure) notifyFailure(mapped)
        }
    }

    private fun networkCallbacks(session: ActiveDiscovery): NsdNetworkChangeCallbacks = object : NsdNetworkChangeCallbacks {
        override fun onNetworkChanged(network: NsdNetworkRef) {
            val failure = NsdDiscoveryFailure.NETWORK_UNAVAILABLE
            val shouldNotifyFailure = synchronized(monitor) {
                isActive(session) && failLocked(session, failure)
            }
            if (shouldNotifyFailure) notifyFailure(failure)
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

    private fun failLocked(session: ActiveDiscovery, failure: NsdDiscoveryFailure): Boolean {
        if (session.ended) return false
        session.terminalFailure = failure
        end(session)
        return true
    }

    private fun notifyFailure(failure: NsdDiscoveryFailure) {
        try {
            observer.onFailure(failure)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            // Observers are outside the platform callback boundary.
        }
    }

    private fun notifyEvent(event: WirelessDiscoveryEvent) {
        try {
            observer.onEvent(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            // A consumer failure must not escape an Android callback.
        }
    }

    private fun isActive(session: ActiveDiscovery): Boolean = active === session && !session.ended

    private fun rejectedTerminal(session: ActiveDiscovery): NsdDiscoveryStartResult.Rejected =
        NsdDiscoveryStartResult.Rejected(session.terminalFailure ?: NsdDiscoveryFailure.PLATFORM_OPERATION_FAILED)

    private fun end(session: ActiveDiscovery?) {
        if (session == null || session.ended) return
        session.ended = true
        if (active === session) active = null
        var controlFailure: Exception? = null
        session.resources.forEach { resource ->
            try {
                resource.cancel()
            } catch (error: CancellationException) {
                if (controlFailure == null) controlFailure = error
            } catch (error: InterruptedException) {
                if (controlFailure == null) controlFailure = error
            } catch (_: Exception) {
                // Platform cleanup is best-effort and intentionally silent.
            }
        }
        controlFailure?.let { throw it }
    }

    private fun endIgnoringCleanupControl(session: ActiveDiscovery) {
        try {
            end(session)
        } catch (_: CancellationException) {
            // Preserve the control-flow exception that initiated cleanup.
        } catch (_: InterruptedException) {
            // Preserve the control-flow exception that initiated cleanup.
        }
    }

    private class ActiveDiscovery(
        val generation: Long,
        val decision: NsdDiscoveryDecision,
    ) {
        val resources = mutableListOf<NsdPlatformResource>()
        private val observationIds = mutableMapOf<ObservationKey, WirelessObservationId>()
        var ended = false
        var terminalFailure: NsdDiscoveryFailure? = null
        var nextObservationId = 0

        fun observationIdFor(service: NsdServiceRef): WirelessObservationId = observationIds.getOrPut(
            ObservationKey(decision.network, service.serviceType, service.serviceName),
        ) {
            WirelessObservationId("nsd-$generation-${nextObservationId++}")
        }

        fun track(resource: NsdPlatformResource) {
            if (ended) {
                try {
                    resource.cancel()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: InterruptedException) {
                    throw error
                } catch (_: Exception) {
                    // A synchronously-completed operation still owns this resource.
                }
            } else {
                resources += resource
            }
        }

        private data class ObservationKey(
            val network: NsdNetworkRef?,
            val serviceType: String,
            val serviceName: String,
        )
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
    private val monitor = Any()
    private val networks = mutableMapOf<NsdNetworkRef, Network>()
    private val discoveredServices = mutableMapOf<NsdServiceRef, StoredService>()

    fun currentNetwork(): NsdNetworkRef? =
        if (apiLevel < NsdDiscoveryPolicy.NETWORK_BOUND_DISCOVERY_API) null
        else connectivityManager.activeNetwork?.let { network ->
            synchronized(monitor) { rememberNetwork(network) }
        }

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
        val registration = Any()
        var registrationActive = true
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!synchronized(monitor) { registrationActive }) return
                val reference = serviceInfo.toServiceRef(serviceType) ?: run {
                    callbacks.onDiscoveryFailure(NsdPlatformFailure.OPERATION_FAILED)
                    return
                }
                val accepted = synchronized(monitor) {
                    if (!registrationActive) false else {
                        discoveredServices[reference] = StoredService(serviceInfo, registration)
                        true
                    }
                }
                if (accepted) callbacks.onServiceFound(reference)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                serviceInfo.toServiceRef(serviceType)?.let { reference ->
                    synchronized(monitor) { removeDiscoveredService(reference, registration) }
                }
            }
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
        CancelOnceResource {
            synchronized(monitor) {
                registrationActive = false
                discoveredServices.entries.removeAll { it.value.registration === registration }
            }
            nsdManager.stopServiceDiscovery(listener)
        }
    }

    override fun resolve(
        service: NsdServiceRef,
        network: NsdNetworkRef?,
        callbacks: NsdResolveCallbacks,
    ): NsdPlatformResource = platformOperation {
        val info = synchronized(monitor) {
            discoveredServices[service]?.info
        } ?: throw NsdPlatformOperationException(NsdPlatformFailure.OPERATION_FAILED)
        if (apiLevel >= NsdDiscoveryPolicy.NETWORK_BOUND_DISCOVERY_API) {
            info.setNetwork(requireNetwork(network))
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
        val networkRef = network
        val expected = requireNetwork(network)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (network != expected) callbacks.onNetworkChanged(NsdNetworkRef("network-changed"))
            }

            override fun onLost(network: Network) {
                if (network == expected) {
                    synchronized(monitor) { removeNetwork(network, networkRef) }
                    callbacks.onNetworkChanged(NsdNetworkRef("network-lost"))
                }
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (exception: Exception) {
            synchronized(monitor) { removeNetwork(expected, networkRef) }
            throw exception
        }
        CancelOnceResource {
            synchronized(monitor) { removeNetwork(expected, networkRef) }
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun requireNetwork(reference: NsdNetworkRef?): Network = synchronized(monitor) {
        reference?.let(networks::get) ?: throw NsdPlatformOperationException(NsdPlatformFailure.OPERATION_FAILED)
    }

    private fun rememberNetwork(network: Network): NsdNetworkRef =
        NsdNetworkRef("network-${network.networkHandle}").also { networks[it] = network }

    private fun removeNetwork(network: Network, reference: NsdNetworkRef) {
        if (networks[reference] == network) networks.remove(reference)
    }

    private fun removeDiscoveredService(reference: NsdServiceRef, registration: Any) {
        if (discoveredServices[reference]?.registration === registration) discoveredServices.remove(reference)
    }

    private fun NsdServiceInfo.toServiceRef(expectedType: String): NsdServiceRef? = try {
        if (serviceName.isNullOrBlank()) null
        else NsdServiceRef(serviceType, serviceName).takeIf { it.serviceType == expectedType }
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
    } catch (error: CancellationException) {
        throw error
    } catch (error: InterruptedException) {
        throw error
    } catch (error: SecurityException) {
        throw error
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
        if (!handler.postDelayed(runnable, delayMillis)) {
            throw NsdPlatformOperationException(NsdPlatformFailure.OPERATION_FAILED)
        }
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

private data class StoredService(
    val info: NsdServiceInfo,
    val registration: Any,
)
