package com.sheen.adb.feature.apps

import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppsPresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt")

    @Test
    fun `screen follows html search card and install action structure`() {
        val source = screen.readText()
        listOf(
            "LazyColumn",
            "OutlinedTextField",
            "请输入应用名或包名。",
            "SheenIcons.Search",
            "minimumTouchTarget",
            "FloatingActionButton",
            "SheenIcons.UploadToDevice",
            "BoxWithConstraints",
        ).forEach { token -> assertTrue(source.contains(token), "missing applications HTML token $token") }
        assertFalse(source.contains("SheenIcons.Add"), "install action must not use a plus icon")
    }

    @Test
    fun `IME search submits the current application query immediately`() {
        val source = screen.readText()

        listOf(
            "KeyboardOptions(imeAction = ImeAction.Search)",
            "KeyboardActions(onSearch =",
            "onSearch()",
            "focusManager.clearFocus()",
        ).forEach { token -> assertTrue(source.contains(token), "missing IME search behavior $token") }
    }

    @Test
    fun `application card keeps package first and exact trailing action order`() {
        val source = screen.readText().substringAfter("private fun ApplicationCard")
        val packagePosition = source.indexOf("Text(application.packageName")
        val displayNamePosition = source.indexOf("Text(displayName")
        assertTrue(packagePosition >= 0 && displayNamePosition >= 0 && packagePosition < displayNamePosition)

        assertOrdered(
            source,
            "SheenIcons.Download",
            "SheenIcons.Disable",
            "SheenIcons.ForceStop",
            "SheenIcons.Uninstall",
        )
        listOf(
            "ApplicationClassification.SYSTEM",
            "ApplicationClassification.UNKNOWN",
            "listOf(AppsRowAction.EXTRACT_APK)",
            "Arrangement.End",
        ).forEach { token -> assertTrue(source.contains(token), "missing restricted-row token $token") }
        assertFalse(source.contains("Spacer("), "hidden actions must not retain empty slots")
    }

    @Test
    fun `all app states use the shared technical design grammar`() {
        val source = screen.readText()
        listOf(
            "Initial",
            "Loading",
            "Content",
            "Empty",
            "Error",
            "Cancelled",
            "Disconnected",
            "Unsupported",
            "OutcomeUnknown",
            "Confirmation",
            "Progress",
            "PartialSuccess",
            "MaterialTheme.colorScheme.surfaceContainerLow",
            "SheenShapes",
        ).forEach { token -> assertTrue(source.contains(token), "missing applications state token $token") }
    }

    @Test
    fun `hostile package and app labels are bounded and isolated without changing identity`() {
        val rawPackage = "com.example.\u0000\u202E" + "segment".repeat(80)
        val rawName = "reader\u0085\nname"
        val packageIdentity = rawPackage.toCharArray().copyOf()
        val nameIdentity = rawName.toCharArray().copyOf()

        val packageDisplay = SafeVerbatimText.render(rawPackage, SafeVerbatimPolicy.SingleLine(96))
        val nameDisplay = SafeVerbatimText.render(rawName, SafeVerbatimPolicy.SingleLine(64))

        assertEquals(rawPackage.toCharArray().toList(), packageIdentity.toList())
        assertEquals(rawName.toCharArray().toList(), nameIdentity.toList())
        assertTrue(packageDisplay.truncated)
        assertTrue(packageDisplay.replacementCount >= 2)
        assertTrue(packageDisplay.display.startsWith("\u2066") && packageDisplay.display.endsWith("\u2069"))
        assertFalse(nameDisplay.display.contains('\n'))
        assertTrue(screen.readText().contains("SafeVerbatimText"))
        assertTrue(screen.readText().contains("UNKNOWN_APP_NAME"))
    }

    @Test
    fun `unknown label keeps explicit fallback and package identity`() {
        val app = RemoteApplication("com.example.reader", 0, RemoteApplicationEnabledState.ENABLED, isSystem = false)
        val state = AppsUiState(
            applications = listOf(app),
            displayNameByPackage = mapOf(app.packageName to null),
        )
        assertEquals(state.visibleApplications.single().packageName, "com.example.reader")
        assertEquals(state.displayNameByPackage[app.packageName], null)
    }

    @Test
    fun `search matches label or package without icon metadata`() {
        val first = RemoteApplication("com.example.reader", 0, RemoteApplicationEnabledState.ENABLED, isSystem = false)
        val second = RemoteApplication("org.example.other", 0, RemoteApplicationEnabledState.ENABLED, isSystem = false)
        val state = AppsUiState(
            applications = listOf(first, second),
            displayNameByPackage = mapOf(first.packageName to "阅读器", second.packageName to null),
        )
        assertEquals(state.copy(query = "阅读").visibleApplications.single().packageName, first.packageName)
        assertEquals(state.copy(query = "other").visibleApplications.single().packageName, second.packageName)
    }

    @Test
    fun `view model consumes session bound metadata updates and cancels enrichment off page`() {
        val source = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()

        assertTrue(source.contains("observeApplicationMetadata(expectedSessionId)"))
        assertTrue(source.contains("displayNameByPackage + (update.packageName to update.displayName)"))
        assertTrue(source.contains("metadataJob?.cancel()"))
    }

    @Test
    fun `verified installation uses installation outcome text not extraction text`() {
        val source = screen.readText()

        assertTrue(source.contains("AppsTaskTerminal.Success -> AppsStringKey.INSTALL_SUCCESS"))
    }

    private fun assertOrdered(source: String, vararg tokens: String) {
        val positions = tokens.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "missing ordered token from ${tokens.toList()}")
        assertEquals(positions, positions.sorted(), "application actions are not in the approved order")
    }
}
