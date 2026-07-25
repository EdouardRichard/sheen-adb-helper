package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class StrictUiDesignContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun `shared tokens and icons match the reviewed design source`() {
        val theme = source("../core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt")
        val icons = File("../core/ui/src/main/kotlin/com/sheen/adb/ui/SheenIcons.kt")

        assertTrue(theme.contains("topBarVisualHeight = 44.dp"))
        assertTrue(theme.contains("bottomBarHeight = 60.dp"))
        assertTrue(theme.contains("drawerWidth = 288.dp"))
        assertTrue(icons.isFile)
        val iconSource = icons.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "Menu", "Link", "LinkOff", "Phone", "Wireless",
            "Folder", "Apps", "Processes", "Terminal", "BugReport",
            "Screenshot", "ScreenRecord", "Power", "Settings", "Info",
        ).forEach { assertTrue(iconSource.contains("val $it: ImageVector"), it) }
    }

    @Test
    fun `app shell uses design-specific top bottom and drawer layouts`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")

        assertTrue(app.contains("SheenConnectionTopBar("))
        assertTrue(app.contains("SheenBottomNavigation("))
        assertTrue(app.contains("DrawerHeader("))
        assertTrue(app.contains("DrawerFooter("))
        assertFalse(app.contains("NavigationBar {"))
        assertFalse(app.contains("navigationGlyph()"))
        assertFalse(app.contains("onLocal: () -> Unit"))
    }

    @Test
    fun `disconnected page preserves the html hierarchy`() {
        val devices = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt",
        )
        val discovery = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt",
        )

        assertTrue(devices.contains("DisconnectedContent("))
        assertTrue(devices.contains("PairingActionButtons("))
        assertTrue(devices.contains("Modifier.weight(1f).verticalScroll"))
        assertTrue(discovery.contains("DiscoveryDeviceCard("))
        assertFalse(discovery.contains("仅发现系统公布"))
    }

    @Test
    fun `connected page uses the designed identity metrics and quick actions`() {
        val overview = source(
            "../feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt",
        )

        assertTrue(overview.contains("DeviceIdentityCard("))
        assertTrue(overview.contains("OverviewMetricGrid("))
        assertTrue(overview.contains("CpuUsageRing("))
        assertTrue(overview.contains("UsageProgressBar("))
        assertTrue(overview.contains("QuickActionDesignButton("))
    }

    @Test
    fun `drawer history uses active and offline design rows`() {
        val history = source(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenu.kt",
        )

        assertTrue(history.contains("HistoryDeviceRow("))
        assertTrue(history.contains("ActiveDeviceIndicator("))
        assertTrue(history.contains("Offline"))
    }
}
