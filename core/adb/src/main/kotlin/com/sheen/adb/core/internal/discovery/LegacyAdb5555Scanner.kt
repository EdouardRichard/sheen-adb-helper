package com.sheen.adb.core.internal.discovery

import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.core.WirelessDiscoveryMode
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class LegacyAdbSubnet(
    val address: Inet4Address,
    val prefixLength: Int,
) {
    init {
        require(prefixLength in 0..32)
    }
}

internal object LegacyAdbDiscoveryIntegrationPolicy {
    fun shouldStart(mode: WirelessDiscoveryMode): Boolean =
        mode == WirelessDiscoveryMode.LAN_FOREGROUND
}

internal object LegacyAdbSubnetPolicy {
    fun candidates(subnets: List<LegacyAdbSubnet>): List<Inet4Address> {
        val iterators = subnets.map { candidates(it).iterator() }.toMutableList()
        val selected = linkedSetOf<Inet4Address>()
        while (iterators.isNotEmpty() && selected.size < MAX_TOTAL_CANDIDATES) {
            val round = iterators.iterator()
            while (round.hasNext() && selected.size < MAX_TOTAL_CANDIDATES) {
                val candidates = round.next()
                if (candidates.hasNext()) {
                    selected += candidates.next()
                } else {
                    round.remove()
                }
            }
        }
        return selected.toList()
    }

    fun candidates(subnet: LegacyAdbSubnet): List<Inet4Address> {
        if (!subnet.address.isSiteLocalAddress || subnet.prefixLength > 30) return emptyList()
        val boundedPrefix = subnet.prefixLength.coerceAtLeast(MIN_SCAN_PREFIX)
        val raw = subnet.address.address
        val addressValue = raw.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff) }
        val hostBits = 32 - boundedPrefix
        val mask = if (boundedPrefix == 0) 0 else -1 shl hostBits
        val network = addressValue and mask
        val broadcast = network or mask.inv()
        return (network + 1 until broadcast)
            .asSequence()
            .filterNot { it == addressValue }
            .take(MAX_CANDIDATES)
            .map(::toAddress)
            .toList()
    }

    private fun toAddress(value: Int): Inet4Address =
        java.net.InetAddress.getByAddress(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        ) as Inet4Address

    private const val MIN_SCAN_PREFIX = 24
    private const val MAX_CANDIDATES = 253
    private const val MAX_TOTAL_CANDIDATES = 512
}

internal class LegacyAdb5555Scanner(
    private val probe: (Inet4Address, Int, Int) -> Boolean = ::socketProbe,
    private val workerCount: Int = DEFAULT_WORKERS,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    fun start(
        generation: Long,
        candidates: List<Inet4Address>,
        onReachable: (WirelessServiceObservation) -> Unit,
    ): NsdPlatformResource {
        require(generation > 0)
        val cancelled = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(workerCount.coerceIn(1, MAX_WORKERS))
        candidates.take(MAX_CANDIDATES).forEach { candidate ->
            executor.execute {
                if (cancelled.get()) return@execute
                val reachable = runCatching {
                    probe(candidate, LEGACY_ADB_PORT, CONNECT_TIMEOUT_MILLIS)
                }.getOrDefault(false)
                if (!reachable || cancelled.get()) return@execute
                onReachable(candidate.toObservation(monotonicMillis()))
            }
        }
        executor.shutdown()
        return ScannerResource(cancelled, executor)
    }

    private fun Inet4Address.toObservation(nowMillis: Long): WirelessServiceObservation =
        WirelessServiceObservation(
            observationId = WirelessObservationId("legacy-${opaqueId(address)}"),
            serviceType = WirelessServiceType.CONNECT,
            serviceName = LEGACY_SERVICE_NAME,
            addresses = listOf(
                WirelessAddress.Ipv4(
                    address[0].toInt() and 0xff,
                    address[1].toInt() and 0xff,
                    address[2].toInt() and 0xff,
                    address[3].toInt() and 0xff,
                ),
            ),
            port = LEGACY_ADB_PORT,
            status = WirelessServiceStatus.RESOLVED,
            lastSeenAt = nowMillis,
        )

    private class ScannerResource(
        private val cancelled: AtomicBoolean,
        private val executor: ExecutorService,
    ) : NsdPlatformResource {
        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) executor.shutdownNow()
        }
    }

    private companion object {
        const val LEGACY_ADB_PORT = 5555
        const val LEGACY_SERVICE_NAME = "legacy-adb-5555"
        const val CONNECT_TIMEOUT_MILLIS = 250
        const val DEFAULT_WORKERS = 16
        const val MAX_WORKERS = 16
        // Two bounded /24 networks cover the common Wi-Fi + TAP topology without
        // turning legacy discovery into an unbounded port sweep.
        const val MAX_CANDIDATES = 512

        fun socketProbe(address: Inet4Address, port: Int, timeoutMillis: Int): Boolean =
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMillis)
                true
            }

        fun opaqueId(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .take(8)
                .joinToString("") { "%02x".format(it) }
    }
}
