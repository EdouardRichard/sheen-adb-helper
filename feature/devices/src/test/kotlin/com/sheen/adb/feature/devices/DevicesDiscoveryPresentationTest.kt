package com.sheen.adb.feature.devices

import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceType
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DevicesDiscoveryPresentationTest {
    @Test
    fun `scanning content empty error and cancelled states expose bounded actions`() {
        val scanning = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.SCANNING,
            generation = 1L,
        ).toDiscoveryPresentation()
        assertTrue(scanning.showProgress)
        assertTrue(scanning.showCancel)
        assertFalse(scanning.showRefresh)
        assertTrue(scanning.showManualAddress)

        val content = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CONTENT,
            generation = 1L,
            items = (1..15).map(::item),
        ).toDiscoveryPresentation()
        assertEquals(content.items.size, 15)
        assertFalse(content.showProgress)
        assertTrue(content.showRefresh)
        assertTrue(content.items.all { it.actions.isNotEmpty() })

        val empty = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.EMPTY,
            generation = 1L,
        ).toDiscoveryPresentation()
        assertTrue(empty.statusText.contains("未发现"))
        assertTrue(empty.statusText.contains("VPN"))
        assertTrue(empty.statusText.contains("热点隔离"))

        val error = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.ERROR,
            generation = 1L,
            failure = DevicesDiscoveryFailure.PERMISSION_UNAVAILABLE,
        ).toDiscoveryPresentation()
        assertTrue(error.statusText.contains("权限"))
        assertTrue(error.showRefresh)

        val cancelled = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CANCELLED,
            generation = 1L,
        ).toDiscoveryPresentation()
        assertTrue(cancelled.statusText.contains("已停止"))
        assertTrue(cancelled.showRefresh)
    }

    @Test
    fun `unknown association and lost target remain explicit without duplicate or connected claims`() {
        val unknown = item(1).copy(
            relation = DevicesDiscoveryRelation.UNKNOWN,
            reachability = DevicesDiscoveryReachability.RESOLVED,
        )
        val lost = item(2).copy(
            pairingTarget = null,
            connectTarget = null,
            reachability = DevicesDiscoveryReachability.LOST,
            selectable = false,
        )
        val presentation = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CONTENT,
            generation = 2L,
            items = listOf(unknown, lost),
        ).toDiscoveryPresentation()

        assertEquals(presentation.items.size, 2)
        assertTrue(presentation.items.first().relationText.contains("尚未验证关联"))
        assertFalse(presentation.items.first().relationText.contains("已连接"))
        assertTrue(presentation.items.last().statusText.contains("离线"))
        assertTrue(presentation.items.last().actions.isEmpty())
    }

    @Test
    fun `expired pending target disables confirmation and directs refresh`() {
        val expiredTarget = target(9L, "expired")
        val state = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CONTENT,
            generation = 10L,
            items = listOf(item(3, generation = 10L)),
            pendingSelection = DevicesDiscoverySelection.Connect(expiredTarget),
        )

        val presentation = state.toDiscoveryPresentation()

        assertTrue(presentation.selectionExpired)
        assertFalse(presentation.canConfirmSelection)
        assertTrue(presentation.selectionMessage.contains("刷新"))
    }

    @Test
    fun `copy never promises network policy bypass`() {
        DevicesDiscoveryFailure.entries.forEach { failure ->
            val text = DevicesDiscoveryState(
                phase = DevicesDiscoveryPhase.ERROR,
                generation = 1L,
                failure = failure,
            ).toDiscoveryPresentation().statusText

            assertFalse(text.contains("绕过"))
            assertFalse(text.contains("强制扫描"))
        }
    }

    private fun item(
        index: Int,
        generation: Long = 1L,
    ): DevicesDiscoveryItem = DevicesDiscoveryItem(
        serviceTypes = setOf(WirelessServiceType.CONNECT),
        pairingTarget = null,
        connectTarget = target(generation, "target-$index"),
        endpointLabel = "IPv4 · 端口 ${45_000 + index}",
        relation = DevicesDiscoveryRelation.UNKNOWN,
        reachability = DevicesDiscoveryReachability.RESOLVED,
        selectable = true,
    )

    private fun target(generation: Long, id: String): WirelessDiscoveryTarget =
        WirelessDiscoveryTarget(generation, WirelessObservationId(id))
}
