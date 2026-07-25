package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionStartupCleanupContractTest {
    private val applicationSource =
        File("src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt").readText()
    private val cleanerSource =
        File("../core/data/src/main/kotlin/com/sheen/adb/data/TemporaryDataCleaner.kt").readText()

    @Test
    fun `quick action artifact cleaner participates in settings clear path`() {
        assertTrue(cleanerSource.contains("class QuickActionArtifactTemporaryDataCleaner"))
        assertTrue(cleanerSource.contains("artifactStore.cleanupAll("))
        assertTrue(applicationSource.contains("QuickActionArtifactTemporaryDataCleaner("))
        assertTrue(applicationSource.contains("CompositeTemporaryDataCleaner("))
    }

    @Test
    fun `application performs non blocking best effort startup cleanup`() {
        assertTrue(applicationSource.contains("override fun onCreate()"))
        assertTrue(applicationSource.contains("applicationScope.launch"))
        assertTrue(applicationSource.contains("quickActionArtifactStore.cleanupAll("))
        assertTrue(applicationSource.contains("ArtifactTerminationReason.STARTUP"))
        assertTrue(applicationSource.contains("runCatching"))
        assertFalse(applicationSource.contains("artifactId.value"))
        assertFalse(applicationSource.contains("printStackTrace"))
    }
}
