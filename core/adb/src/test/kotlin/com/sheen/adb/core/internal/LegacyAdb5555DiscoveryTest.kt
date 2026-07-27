package com.sheen.adb.core.internal

import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.core.internal.discovery.LegacyAdb5555Scanner
import com.sheen.adb.core.internal.discovery.LegacyAdbDiscoveryIntegrationPolicy
import com.sheen.adb.core.internal.discovery.LegacyAdbSubnet
import com.sheen.adb.core.internal.discovery.LegacyAdbSubnetPolicy
import java.net.Inet4Address
import java.net.InetAddress
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LegacyAdb5555DiscoveryTest {
    @Test
    fun `Android gateway plans legacy probes from every connected private subnet`() {
        val source = File(
            "src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt",
        ).readText()
        val method = source.substringAfter("fun currentLegacySubnets()")
            .substringBefore("\n    override fun ")

        assertTrue(
            method.contains("manager.allNetworks"),
            "TAP and Wi-Fi can coexist, so legacy discovery must not inspect only activeNetwork.",
        )
        assertTrue(
            method.contains("NetworkInterface.getNetworkInterfaces"),
            "A statically configured TAP interface may not be registered as an Android Network.",
        )
        assertTrue(
            method.contains("distinctBy"),
            "The same private subnet can be exposed by more than one Android Network.",
        )
        assertTrue(
            source.contains("LegacyAdbSubnetPolicy.candidates(platform.currentLegacySubnets())"),
            "The bounded probe budget must be shared across the discovered networks.",
        )
    }

    @Test
    fun `legacy probe is added only to foreground LAN discovery`() {
        assertTrue(
            LegacyAdbDiscoveryIntegrationPolicy.shouldStart(
                com.sheen.adb.core.WirelessDiscoveryMode.LAN_FOREGROUND,
            ),
        )
        assertFalse(
            LegacyAdbDiscoveryIntegrationPolicy.shouldStart(
                com.sheen.adb.core.WirelessDiscoveryMode.LOCAL_PAIRING,
            ),
        )
    }

    @Test
    fun `candidate planning stays on the active private subnet excludes controller and remains bounded`() {
        val subnet = LegacyAdbSubnet(address("192.168.50.23"), prefixLength = 20)
        val candidates = LegacyAdbSubnetPolicy.candidates(subnet)

        assertEquals(candidates.size, 253)
        assertFalse(candidates.contains(subnet.address))
        assertTrue(candidates.all { it.hostAddress.startsWith("192.168.50.") })
        assertFalse(candidates.any { it.hostAddress.endsWith(".0") || it.hostAddress.endsWith(".255") })
    }

    @Test
    fun `multi-network candidate planning shares its bounded budget across later subnets`() {
        val candidates = LegacyAdbSubnetPolicy.candidates(
            listOf(
                LegacyAdbSubnet(address("192.168.40.23"), prefixLength = 24),
                LegacyAdbSubnet(address("192.168.41.23"), prefixLength = 24),
                LegacyAdbSubnet(address("192.168.42.23"), prefixLength = 24),
            ),
        )

        assertTrue(candidates.size <= 512)
        assertTrue(
            candidates.any { it.hostAddress.startsWith("192.168.42.") },
            "A later TAP subnet must receive probes before the global budget is exhausted.",
        )
    }

    @Test
    fun `scanner publishes only reachable port 5555 as a resolved connect observation and cancellation is terminal`() {
        val reachable = address("192.168.60.7")
        val latch = CountDownLatch(1)
        val observations = mutableListOf<com.sheen.adb.core.WirelessServiceObservation>()
        val scanner = LegacyAdb5555Scanner(
            probe = { candidate, port, _ -> candidate == reachable && port == 5555 },
            workerCount = 2,
        )

        val resource = scanner.start(
            generation = 42,
            candidates = listOf(address("192.168.60.6"), reachable),
        ) {
            synchronized(observations) { observations += it }
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        resource.cancel()
        resource.cancel()
        val published = synchronized(observations) { observations.single() }
        assertEquals(published.serviceType, WirelessServiceType.CONNECT)
        assertEquals(published.status, WirelessServiceStatus.RESOLVED)
        assertEquals(published.port, 5555)
        assertEquals(published.addresses.size, 1)
    }

    @Test
    fun `scanner retains a bounded second subnet when Wi-Fi and TAP coexist`() {
        val wifiCandidates = LegacyAdbSubnetPolicy.candidates(
            LegacyAdbSubnet(address("192.168.70.23"), prefixLength = 24),
        )
        val tapReachable = address("192.168.71.9")
        val latch = CountDownLatch(1)
        val scanner = LegacyAdb5555Scanner(
            probe = { candidate, port, _ ->
                candidate == tapReachable && port == 5555
            },
            workerCount = 16,
        )

        val resource = scanner.start(
            generation = 43,
            candidates = wifiCandidates + tapReachable,
        ) {
            latch.countDown()
        }

        assertTrue(
            latch.await(2, TimeUnit.SECONDS),
            "A full first /24 must not consume the complete multi-network scan budget.",
        )
        resource.cancel()
    }

    private fun address(value: String): Inet4Address =
        InetAddress.getByName(value) as Inet4Address
}
