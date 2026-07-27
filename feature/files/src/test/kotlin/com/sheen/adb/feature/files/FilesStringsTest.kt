package com.sheen.adb.feature.files

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FilesStringsTest {
    private val sourceFile =
        File("src/main/kotlin/com/sheen/adb/feature/files/FilesStrings.kt")

    @Test
    fun `files owns complete bilingual semantic catalog and accessibility keys`() {
        assertTrue(sourceFile.isFile, "FilesStrings.kt is missing")
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "enum class FilesStringKey",
            "UiLanguage.ZH_CN",
            "UiLanguage.EN_US",
            "PATH",
            "LOADING",
            "EMPTY",
            "ERROR",
            "CANCELLED",
            "DISCONNECTED",
            "UNSUPPORTED",
            "OUTCOME_UNKNOWN",
            "CONFIRMATION",
            "PROGRESS",
            "ENTER_FOLDER",
            "DOWNLOAD_FILE",
            "UPLOAD_FILE",
            "LocalizedTextRef",
            "SafeVerbatimText",
        ).forEach { token -> assertTrue(source.contains(token), "missing files string token $token") }
    }

    @Test
    fun `files catalog maps structured errors without rendering core prose`() {
        val source = sourceFile.takeIf(File::isFile)?.readText().orEmpty()

        assertTrue(source.contains("technicalCode"))
        assertTrue(source.contains("FileTaskErrorCategory"))
        assertFalse(source.contains(".userMessage"))
        assertFalse(source.contains(".nextStep"))
    }

    @Test
    fun `file task terminal titles do not reuse same-name conflict confirmation`() {
        val failure = FileTaskError(
            category = FileTaskErrorCategory.PERMISSION_DENIED,
            userMessage = "not rendered",
            nextStep = "not rendered",
            technicalCode = "PERMISSION_DENIED",
        )

        assertEquals(FilesStrings.taskTitleKey(FileTaskStatus.Succeeded), FilesStringKey.SUCCEEDED)
        assertEquals(FilesStrings.taskTitleKey(FileTaskStatus.Cancelled), FilesStringKey.TASK_CANCELLED)
        assertEquals(
            FilesStrings.taskTitleKey(FileTaskStatus.Failed(failure)),
            FilesStringKey.ERROR,
        )
        assertEquals(
            FilesStrings.taskTitleKey(FileTaskStatus.CleanupFailed(failure)),
            FilesStringKey.OUTCOME_UNKNOWN,
        )
    }
}
