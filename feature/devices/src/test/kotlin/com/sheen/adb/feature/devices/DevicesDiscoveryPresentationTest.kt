package com.sheen.adb.feature.devices

import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.core.WirelessObservationId
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.ui.UiLanguage
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DevicesDiscoveryPresentationTest {
    @Test
    fun `device rows use optional name followed by protocol address and current port`() {
        val namedIpv4 = item(1).copy(
            deviceName = "Lab phone",
            endpointLabel = "IPv4 · 192.0.2.10 · 37001",
        )
        val unnamedIpv6 = item(2).copy(
            deviceName = null,
            endpointLabel = "IPv6 · 2001:db8:0:0:0:0:0:7 · 55555",
        )
        val changedPort = item(3).copy(
            deviceName = "Lab phone",
            endpointLabel = "IPv4 · 192.0.2.10 · 37002",
        )
        val blankName = item(4).copy(
            deviceName = "   ",
            endpointLabel = "IPv4 · 192.0.2.11 · 37003",
        )

        val rows = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CONTENT,
            generation = 1L,
            items = listOf(namedIpv4, unnamedIpv6, changedPort, blankName),
        ).toDiscoveryPresentation().items

        assertEquals(rows[0].deviceName, "Lab phone")
        assertEquals(rows[0].endpointLabel, "IPv4 · 192.0.2.10 · 37001")
        assertNull(rows[1].deviceName)
        assertEquals(rows[1].endpointLabel, "IPv6 · 2001:db8:0:0:0:0:0:7 · 55555")
        assertEquals(rows[2].endpointLabel, "IPv4 · 192.0.2.10 · 37002")
        assertNull(rows[3].deviceName, "Unreliable blank names must not create an empty name line")
    }

    @Test
    fun `scanning empty cancelled timeout and unavailable states expose recovery`() {
        val scanning = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.SCANNING,
            generation = 1L,
        ).toDiscoveryPresentation()
        assertTrue(scanning.showProgress)
        assertTrue(scanning.showCancel)
        assertFalse(scanning.showRefresh)
        assertTrue(scanning.showManualAddress)
        assertTrue(scanning.statusText.contains("10"))

        val empty = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.EMPTY,
            generation = 2L,
        ).toDiscoveryPresentation()
        assertTrue(empty.items.isEmpty())
        assertTrue(empty.showRefresh)
        assertTrue(empty.showManualAddress)

        val cancelled = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.CANCELLED,
            generation = 3L,
        ).toDiscoveryPresentation()
        assertFalse(cancelled.showProgress)
        assertTrue(cancelled.showRefresh)
        assertTrue(cancelled.showManualAddress)

        val timeout = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.ERROR,
            generation = 4L,
            failure = DevicesDiscoveryFailure.TIMED_OUT,
        ).toDiscoveryPresentation()
        assertTrue(timeout.statusText.contains("10"))
        assertTrue(timeout.showRefresh)
        assertTrue(timeout.showManualAddress)

        val unavailable = DevicesDiscoveryState(
            phase = DevicesDiscoveryPhase.ERROR,
            generation = 5L,
            failure = DevicesDiscoveryFailure.PERMISSION_UNAVAILABLE,
        ).toDiscoveryPresentation()
        assertFalse(unavailable.showProgress)
        assertTrue(unavailable.showRefresh)
        assertTrue(unavailable.showManualAddress)
        assertTrue(unavailable.statusText.isNotBlank())
    }

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

    @Test
    fun `discovery loading empty failure cancellation and timeout are bilingual`() {
        val phases = listOf(
            DevicesDiscoveryState(DevicesDiscoveryPhase.SCANNING, generation = 1L),
            DevicesDiscoveryState(DevicesDiscoveryPhase.EMPTY, generation = 1L),
            DevicesDiscoveryState(
                DevicesDiscoveryPhase.ERROR,
                generation = 1L,
                failure = DevicesDiscoveryFailure.PERMISSION_UNAVAILABLE,
            ),
            DevicesDiscoveryState(
                DevicesDiscoveryPhase.ERROR,
                generation = 1L,
                failure = DevicesDiscoveryFailure.TIMED_OUT,
            ),
            DevicesDiscoveryState(DevicesDiscoveryPhase.CANCELLED, generation = 1L),
        )
        phases.forEach { state ->
            val chinese = state.toDiscoveryPresentation(UiLanguage.ZH_CN).statusText
            val english = state.toDiscoveryPresentation(UiLanguage.EN_US).statusText
            assertFalse(chinese == english)
            assertFalse(english.any { it.code > 127 })
        }
    }

    private fun item(
        index: Int,
        generation: Long = 1L,
    ): DevicesDiscoveryItem = DevicesDiscoveryItem(
        serviceTypes = setOf(WirelessServiceType.CONNECT),
        pairingTarget = null,
        connectTarget = target(generation, "target-$index"),
        endpointLabel = "IPv4 · 192.0.2.${index} · ${45_000 + index}",
        relation = DevicesDiscoveryRelation.UNKNOWN,
        reachability = DevicesDiscoveryReachability.RESOLVED,
        selectable = true,
    )

    private fun target(generation: Long, id: String): WirelessDiscoveryTarget =
        WirelessDiscoveryTarget(generation, WirelessObservationId(id))
}
