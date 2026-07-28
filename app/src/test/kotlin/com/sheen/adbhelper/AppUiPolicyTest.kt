package com.sheen.adbhelper

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import java.nio.file.Files
import java.nio.file.Path
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
            connectionStatusName(
                AdbConnectionState.Error(AdbError.Unknown(AdbOperationStage.CONNECT), "safe"),
            ),
            "错误",
        )
    }

    @Test
    fun `connection dependent navigation is disabled while offline`() {
        assertFalse(isMenuEnabled(requiresConnection = true, connected = false))
        assertTrue(isMenuEnabled(requiresConnection = false, connected = false))
        assertTrue(isMenuEnabled(requiresConnection = true, connected = true))
    }

    @Test
    fun `v01 shell has exactly six ordered destinations`() {
        assertEquals(
            MainDestination.entries.map { it.name },
            listOf("CONNECTION", "FILES", "APPLICATIONS", "PROCESSES", "SHELL", "LOGCAT"),
        )
        assertEquals(MainDestination.CONNECTION.ordinal, 0)
        assertTrue(MainDestination.entries.drop(1).isNotEmpty())
    }

    @Test
    fun `disconnect returns every dependent destination to connection`() {
        MainDestination.entries.drop(1).forEach {
            assertEquals(destinationAfterConnectionChange(it, connected = false), MainDestination.CONNECTION)
        }
        assertEquals(
            destinationAfterConnectionChange(MainDestination.CONNECTION, connected = false),
            MainDestination.CONNECTION,
        )
        assertEquals(
            destinationAfterConnectionChange(MainDestination.APPLICATIONS, connected = true),
            MainDestination.APPLICATIONS,
        )
    }

    @Test
    fun `mobile and expanded shells share destinations and accessible touch targets`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        assertTrue(app.contains("SheenBottomNavigation"))
        assertTrue(app.contains("PermanentNavigationDrawer"))
        assertTrue(app.contains("MainDestination.entries"))
        assertTrue(app.contains("heightIn(min = SheenDimensions.minimumTouchTarget)"))
        assertTrue(app.contains("if (isMenuEnabled(destination.requiresConnection, connected))"))
        listOf("FilesRoute(", "AppsRoute(", "ProcessesRoute(", "ShellRoute(", "LogcatRoute(").forEach {
            assertTrue(app.contains(it), "Existing deferred route must remain wired: $it")
        }
        assertFalse(app.contains("WebView"))
        assertFalse(app.contains("cdn.tailwindcss.com"))
    }

    @Test
    fun `app assembly does not perform remote file or SAF operations directly`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        listOf(
            "loadRemoteDirectory(",
            "openSync(",
            "OpenDocument",
            "OpenDocumentTree",
            "ContentResolver",
        ).forEach { assertFalse(app.contains(it), "App assembly must not directly use $it") }
    }

    @Test
    fun `files view model has one activity owner and shell exposes cross page summary`() {
        val activity = source("src/main/kotlin/com/sheen/adbhelper/MainActivity.kt")
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        assertTrue(activity.contains("by viewModels<FilesViewModel>"))
        assertTrue(activity.contains("filesViewModel.onHostStopped"))
        assertFalse(app.contains("FilesViewModel(container.adbManager)"))
        assertTrue(app.contains("FileTaskSummaryBar"))
        assertTrue(app.contains("showViewAction = pageHostState.current != MainDestination.FILES"))
    }

    @Test
    fun `real background stop cancels but configuration stop retains file task`() {
        assertTrue(shouldCancelFileTasksOnStop(isChangingConfigurations = false))
        assertFalse(shouldCancelFileTasksOnStop(isChangingConfigurations = true))
    }

    @Test
    fun `host start releases the file picker lease only after a stable foreground window`() {
        val activity = source("src/main/kotlin/com/sheen/adbhelper/MainActivity.kt")
        val onStart = activity.substringAfter("override fun onStart()")
            .substringBefore("\n    override fun ")

        assertTrue(onStart.contains("filesViewModel.onHostStarted()"))
        assertTrue(
            onStart.contains("lifecycleScope.launch") &&
                onStart.contains("delay(FILE_PICKER_RETURN_STABILITY_MILLIS)") &&
                onStart.contains("lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)"),
            "A transient restart between DocumentsUI and its permission confirmation must not release the picker lease.",
        )
        assertTrue(
            onStart.indexOf("hostForegroundState.value = true") <
                onStart.indexOf("filesViewModel.onHostStarted()"),
            "The lease is released only after foreground stability has been observed.",
        )
    }

    @Test
    fun `connection shell preserves Compose conversion and disconnect fallback`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        val devices = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt",
        )
        val strings = source("../core/ui/src/main/kotlin/com/sheen/adb/ui/V01Strings.kt")
        assertTrue(app.contains("V01StringKey.MENU"))
        assertTrue(app.contains("V01StringKey.CONNECTION_ENDPOINT_CONTENT_DESCRIPTION"))
        assertFalse(app.contains("contentDescription = \"ADB"))
        assertTrue(app.contains("V01StringKey.CONNECTION_ENDPOINT_HINT"))
        assertTrue(app.contains("V01StringKey.TOP_CONNECT"))
        assertTrue(devices.contains("V01StringKey.PAIRING_SCAN"))
        assertTrue(devices.contains("V01StringKey.PAIRING_QR"))
        assertTrue(devices.contains("V01StringKey.PAIRING_CODE"))
        assertTrue(devices.contains("V01StringKey.PAIRING_LOCAL"))
        assertTrue(strings.contains("请输入 IP:端口"))
        assertTrue(app.contains("actions::disconnect"))
        assertTrue(app.contains("destinationAfterConnectionChange(pageHostState.current, connected)"))
        assertFalse(devices.contains("WebView"))
        assertFalse(devices.contains("cdn.tailwindcss.com"))
    }

    @Test
    fun `connection endpoint field preserves vertical glyph space`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        val field = app.substringAfter("BasicTextField(").substringBefore("IconButton(")

        assertTrue(field.contains("height(SheenDimensions.topBarVisualHeight - 8.dp)"))
        assertTrue(field.contains("padding(horizontal = 12.dp)"))
        assertFalse(field.contains("vertical = 6.dp"))
    }

    @Test
    fun `device connection page no longer exposes historical or diagnostic list`() {
        val devices = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt",
        )
        assertFalse(devices.contains("诊断事件"))
        assertFalse(devices.contains("历史连接设备"))
    }

    @Test
    fun `drawer follows menu design and about is user initiated`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        val history = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenu.kt",
        )
        val strings = source("../core/ui/src/main/kotlin/com/sheen/adb/ui/V01Strings.kt")
        assertTrue(history.contains("DevicesStringKey.HISTORY_TITLE"))
        assertTrue(app.contains("DeviceHistoryMenu("))
        assertTrue(app.contains("V01StringKey.SETTINGS"))
        assertTrue(app.contains("V01StringKey.ABOUT"))
        assertTrue(strings.contains("V01StringKey.ABOUT_SUPPORT"))
        assertTrue(app.contains("https://github.com/EdouardRichard/sheen-adb-helper"))
        assertTrue(app.contains("Intent(Intent.ACTION_VIEW"))
        assertTrue(app.contains("BuildConfig.VERSION_NAME"))
        assertFalse(app.contains("v2.4.1-测试版"))
        assertFalse(app.contains("LaunchedEffect(ABOUT_REPOSITORY_URL"))
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(Path.of(path)))
}
