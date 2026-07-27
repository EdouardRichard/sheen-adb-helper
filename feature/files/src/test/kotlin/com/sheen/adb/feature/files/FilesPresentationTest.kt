package com.sheen.adb.feature.files

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FilesPresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt")

    @Test
    fun `screen follows html path list trailing actions and upload structure`() {
        val source = screen.readText()
        listOf(
            "LazyColumn",
            "items(",
            "key =",
            "breadcrumbDisplaySegments",
            "horizontalScroll",
            "SheenIcons.Folder",
            "SheenIcons.NavigateNext",
            "SheenIcons.Download",
            "SheenIcons.UploadToDevice",
            "FloatingActionButton",
            "minimumTouchTarget",
        ).forEach { token -> assertTrue(source.contains(token), "missing files HTML structure $token") }
        assertFalse(
            Regex("FileEntryRow[\\s\\S]{0,180}\\.clickable\\s*\\{\\s*onEntry").containsMatchIn(source),
            "file row body must not own enter/download click",
        )
    }

    @Test
    fun `all semantic states and hostile verbatim projection use one design grammar`() {
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
            "SafeVerbatimText",
            "maxCodePoints",
            "MaterialTheme.colorScheme.surfaceContainerLow",
            "SheenShapes",
        ).forEach { token -> assertTrue(source.contains(token), "missing files state/safety token $token") }
        assertFalse(source.contains("verticalScroll(rememberScrollState())"))
    }

    @Test
    fun `task overlay derives its title from the actual terminal status`() {
        val source = screen.readText()

        assertTrue(source.contains("FilesStrings.taskTitleKey(task.status)"))
        assertFalse(
            Regex("else\\s*->\\s*FilesVisualState\\.Confirmation").containsMatchIn(source),
            "success, failure, and cancellation must not reuse the same-name conflict title",
        )
    }
}
