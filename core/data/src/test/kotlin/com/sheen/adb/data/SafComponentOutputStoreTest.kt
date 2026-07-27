package com.sheen.adb.data

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class SafComponentOutputStoreTest {
    private val componentStore = File(
        "src/main/kotlin/com/sheen/adb/data/SafComponentOutputStore.kt",
    ).takeIf(File::isFile)?.readText().orEmpty()
    private val documentStore = File(
        "src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt",
    ).readText()

    @Test
    fun `split output owns one dedicated directory and stages verifies and commits each component independently`() {
        assertContainsAll(
            componentStore,
            "SafComponentOutputStore",
            "createComponentDirectory",
            "stageComponent",
            "verifyComponent",
            "commitComponent",
            "componentId",
            "expectedSizeBytes",
        )
        assertContainsAll(
            documentStore,
            "prepareDirectory",
            "SafStagedTarget",
            "openTarget",
            "commit(",
        )
    }

    @Test
    fun `later component failure cleans only current temporary and retains committed components`() {
        assertContainsAll(
            componentStore,
            "cleanupCurrentTemporary",
            "committedComponents",
            "retainCommitted",
            "ComponentOutputResult.COMMITTED",
            "ComponentOutputResult.FAILED",
        )
        assertFalse(
            componentStore.contains("rollbackAll"),
            "component output has no cross-file rollback contract",
        )
        assertFalse(
            componentStore.contains("deleteCommitted"),
            "already verified committed files must survive later failure or cancellation",
        )
        assertFalse(
            componentStore.contains("atomicCommit"),
            "the provider is not required to support a multi-file atomic transaction",
        )
    }

    @Test
    fun `terminal report preserves every item and distinguishes complete partial and zero commit`() {
        assertContainsAll(
            componentStore,
            "ComponentOutputItem",
            "ComponentOutputSummary",
            "COMPLETE_SUCCESS",
            "PARTIAL_SUCCESS",
            "FAILED_NONE_COMMITTED",
            "CANCELLED",
            "OUTCOME_UNKNOWN",
            "notAttempted",
        )
        assertTrue(
            componentStore.contains("items"),
            "every terminal result must retain one item result per expected APK component",
        )
    }

    private fun assertContainsAll(source: String, vararg tokens: String) {
        tokens.forEach { token ->
            assertTrue(source.contains(token), "Missing SAF component output contract token: $token")
        }
    }
}
