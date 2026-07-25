package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.data.DeviceProfile
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DeviceHistoryMenuPresentationTest {
    @Test
    fun `profiles are newest first and online requires matching host and port`() {
        val old = profile("old", "Old", "device.local", 5555, 100)
        val sameHostWrongPort = profile("wrong", "Wrong port", "online.local", 4444, 200)
        val online = profile("online", "Online", "online.local", 5555, 300)

        val presentation = deviceHistoryMenuPresentation(
            profiles = listOf(old, online, sameHostWrongPort),
            connectionState = AdbConnectionState.Connected(
                endpoint = AdbEndpoint("online.local", 5555),
                sessionId = "session-a",
            ),
        )

        assertEquals(presentation.items.map { it.profile.id }, listOf("online", "wrong", "old"))
        assertTrue(presentation.items[0].isOnline)
        assertFalse(presentation.items[1].isOnline)
        assertEquals(presentation.items[0].endpointLabel, "online.local:5555")
    }

    @Test
    fun `empty profile list exposes a real empty state without examples`() {
        val presentation = deviceHistoryMenuPresentation(
            profiles = emptyList(),
            connectionState = AdbConnectionState.Disconnected(),
        )

        assertTrue(presentation.items.isEmpty())
        assertTrue(presentation.isEmpty)
        assertFalse(presentation.toString().contains("Pixel"))
    }

    @Test
    fun `menu callbacks preserve existing reconnect rename and delete operations`() {
        val target = profile("target", "Target", "target.local", 5555, 100)
        val calls = mutableListOf<String>()
        val callbacks = DeviceHistoryMenuCallbacks(
            onReconnect = { calls += "reconnect:${it.id}" },
            onRename = { calls += "rename:${it.id}" },
            onDelete = { calls += "delete:${it.id}" },
        )

        callbacks.onReconnect(target)
        callbacks.onRename(target)
        callbacks.onDelete(target)

        assertEquals(calls, listOf("reconnect:target", "rename:target", "delete:target"))
    }

    private fun profile(
        id: String,
        name: String,
        host: String,
        port: Int,
        lastConnected: Long,
    ) = DeviceProfile(
        id = id,
        displayName = name,
        host = host,
        debugPort = port,
        firstConnectedAtEpochMillis = 1,
        lastConnectedAtEpochMillis = lastConnected,
        isLocal = false,
        identityReference = "identity-$id",
    )
}
