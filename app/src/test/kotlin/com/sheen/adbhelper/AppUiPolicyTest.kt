package com.sheen.adbhelper

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppUiPolicyTest {
    private val endpoint = AdbEndpoint("example.local", 37001)

    @Test
    fun `renders all allowed connection status labels`() {
        assertEquals(connectionStatusName(AdbConnectionState.Disconnected()), "未连接")
        assertEquals(connectionStatusName(AdbConnectionState.Connecting(endpoint)), "连接中")
        assertEquals(connectionStatusName(AdbConnectionState.AwaitingAuthorization(endpoint)), "等待设备授权")
        assertEquals(connectionStatusName(AdbConnectionState.Connected(endpoint, "session")), "已连接")
        assertEquals(connectionStatusName(AdbConnectionState.Pairing(endpoint)), "配对中")
        assertEquals(connectionStatusName(AdbConnectionState.Disconnecting), "断开中")
        assertEquals(
            connectionStatusName(AdbConnectionState.Error(AdbError.Unknown(AdbOperationStage.CONNECT), "safe")),
            "错误",
        )
    }

    @Test
    fun `connection dependent menu is disabled while offline`() {
        assertFalse(isMenuEnabled(requiresConnection = true, connected = false))
        assertTrue(isMenuEnabled(requiresConnection = false, connected = false))
        assertTrue(isMenuEnabled(requiresConnection = true, connected = true))
    }

    @Test
    fun `application management is ordered after overview and is connection dependent`() {
        assertEquals(
            Destination.entries.map { it.label },
            listOf("设备列表 / 首页", "设备概览", "应用管理", "Shell 终端", "进程监控", "Logcat", "设置与隐私"),
        )
        assertTrue(Destination.APPS.requiresConnection)
        assertFalse(isMenuEnabled(Destination.APPS.requiresConnection, connected = false))
    }

    @Test
    fun `disconnect returns connection dependent destinations to home`() {
        assertEquals(destinationAfterConnectionChange(Destination.APPS, connected = false), Destination.DEVICES)
        assertEquals(destinationAfterConnectionChange(Destination.SETTINGS, connected = false), Destination.SETTINGS)
        assertEquals(destinationAfterConnectionChange(Destination.APPS, connected = true), Destination.APPS)
    }

}
