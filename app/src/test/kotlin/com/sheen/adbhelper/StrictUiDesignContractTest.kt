package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
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
    fun `v1 terminal design tokens use the reviewed local palette and dimensions`() {
        val theme = source("../core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt")

        listOf(
            "0xFF0B1326",
            "0xFF31394D",
            "0xFF060E20",
            "0xFF131B2E",
            "0xFF171F33",
            "0xFF222A3D",
            "0xFF2D3449",
            "0xFFADC6FF",
            "0xFF4D8EFF",
            "0xFF4EDEA3",
            "0xFF00A572",
            "0xFFFFB95F",
            "0xFFCA8100",
            "0xFFFFB4AB",
        ).forEach { token ->
            assertTrue(theme.contains(token), "missing reviewed color token $token")
        }
        listOf(
            "minimumTouchTarget = 44.dp",
            "compactPageHorizontalMargin = 16.dp",
            "expandedPageHorizontalMargin = 24.dp",
            "commonGutter = 12.dp",
            "terminalBackground =",
        ).forEach { token ->
            assertTrue(theme.contains(token), "missing reviewed dimension/token $token")
        }
    }

    @Test
    fun `v1 action icons are project owned vectors`() {
        val icons = source("../core/ui/src/main/kotlin/com/sheen/adb/ui/SheenIcons.kt")

        listOf(
            "UploadToDevice",
            "Download",
            "Disable",
            "Enable",
            "ForceStop",
            "Uninstall",
            "Delete",
            "KeyboardReturn",
            "ArrowUp",
            "ArrowDown",
        ).forEach { name ->
            assertTrue(icons.contains("val $name: ImageVector"), "missing local icon $name")
        }
        assertFalse(icons.contains("Icons.Default.Add"), "APK install icon must not be a plus")
    }

    @Test
    fun `v1 screens remain Compose only and load no remote UI assets`() {
        val roots = listOf(
            File("src/main"),
            File("../core/ui/src/main"),
            File("../feature"),
        )
        val sourceFiles = roots
            .filter(File::exists)
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension in setOf("kt", "kts", "xml") }
                    .toList()
            }
        val forbidden = listOf(
            "android.webkit.WebView",
            "WebView(",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "material-symbols",
            "@font-face",
        )
        val violations = sourceFiles.flatMap { file ->
            val text = file.readText()
            forbidden.filter(text::contains).map { token -> "${file.path}: $token" }
        }

        assertEquals(violations, emptyList<String>(), violations.joinToString("\n"))
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
    fun `every destination reuses the designed adb session top bar`() {
        val app = source("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt")
        val scaffold = app.substringAfter("private fun AppScaffold(")
            .substringBefore("private fun V1AnimatedDestinationContent(")

        assertTrue(scaffold.contains("topBar = {\n            SheenConnectionTopBar("))
        assertFalse(scaffold.contains("if (destination == MainDestination.CONNECTION)"))
        assertFalse(scaffold.contains("TopAppBar("))
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
        assertTrue(history.contains("DevicesStringKey.HISTORY_OFFLINE"))
    }

    @Test
    fun `all v1 destinations share restrained tonal layering tokens`() {
        val screens = listOf(
            "src/main/kotlin/com/sheen/adbhelper/SheenApp.kt",
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt",
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt",
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenu.kt",
            "../feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt",
            "../feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt",
            "../feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt",
            "../feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesScreen.kt",
            "../feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellScreen.kt",
            "../feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt",
            "../feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsScreen.kt",
        )

        screens.forEach { path ->
            val screen = source(path)
            assertTrue(
                screen.contains("SheenTonalLayers."),
                "$path must use the shared tonal-layer contract",
            )
        }
    }
}
