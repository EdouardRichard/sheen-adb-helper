package com.sheen.adb.core.internal

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ApplicationUninstallSessionManagerTest {
    private val models = source("../AdbModels.kt")
    private val sessionPort = source("../AdbSessionManager.kt")
    private val manager = source("DefaultAdbSessionManager.kt")
    private val capabilities = source("ApplicationCapabilities.kt")

    @Test
    fun `uninstall request binds target snapshot deletion acknowledgement and confirmation nonce`() {
        assertContainsAll(
            models,
            "ApplicationUninstallRequest",
            "expectedSessionId",
            "userId",
            "packageName",
            "expectedGeneration",
            "confirmationNonce",
            "deletePrivateDataAcknowledged",
            "ApplicationUninstallStage",
        )
        assertContainsAll(
            sessionPort,
            "prepareApplicationUninstall(",
            "uninstallApplication(",
            "ApplicationUninstallRequest",
            "ApplicationUninstallResult",
        )
        assertContainsAll(manager, "validateUninstallConfirmation", "confirmationNonce")
    }

    @Test
    fun `ordinary uninstall verifies private data deleting removal and rejects unsafe classifications`() {
        assertContainsAll(
            models,
            "VerifiedRemoved",
            "PrivateDataDeleted",
            "PolicyRejected",
            "ApplicationClassification",
        )
        assertContainsAll(
            manager,
            "verifyApplicationRemoval",
            "deletePrivateDataAcknowledged",
            "ApplicationClassification.ORDINARY",
        )
        assertContainsAll(capabilities, "ApplicationAction.UNINSTALL", "ApplicationClassification.ORDINARY")
        assertFalse(manager.contains("--keep-data"), "v1.0 uninstall must not retain app private data")
    }

    @Test
    fun `retained system base refusal and uncertain dispatched outcomes are never complete success`() {
        assertContainsAll(
            models,
            "SystemBaseRetained",
            "PolicyRejected",
            "OutcomeUnknown",
            "TimedOut",
            "Rejected",
        )
        assertContainsAll(manager, "SystemBaseRetained", "OutcomeUnknown")
        assertTrue(
            manager.contains("expectedSessionId"),
            "uninstall result must remain bound to the originating Session",
        )
        assertFalse(manager.contains("systemBaseRetained = false"),
            "a retained system base must not be hard-coded away")
    }

    private fun source(relativePath: String): String =
        File("src/main/kotlin/com/sheen/adb/core/internal", relativePath)
            .takeIf(File::isFile)
            ?.readText()
            .orEmpty()

    private fun assertContainsAll(source: String, vararg tokens: String) {
        tokens.forEach { token ->
            assertTrue(source.contains(token), "Missing uninstall contract token: $token")
        }
    }
}
