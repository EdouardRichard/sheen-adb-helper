package com.sheen.adb.core.internal

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ApkInstallSessionManagerTest {
    private val models = source("../AdbModels.kt")
    private val sessionPort = source("../AdbSessionManager.kt")
    private val manager = source("DefaultAdbSessionManager.kt")
    private val protocolFactory = source("KadbProtocolClientFactory.kt")

    @Test
    fun `single APK install port is Session bound and reports bounded stages`() {
        assertContainsAll(
            models,
            "ApkInstallRequest",
            "expectedSessionId",
            "sourceFingerprint",
            "sourceSizeBytes",
            "ApkInstallMode",
            "ApkInstallStage",
            "STAGING",
            "INSTALLING",
            "VERIFYING",
            "CLEANING",
            "ApkInstallResult",
        )
        assertContainsAll(sessionPort, "installApk(", "ApkInstallRequest", "ApkInstallResult")
        assertContainsAll(models, "APK_INSTALL")
    }

    @Test
    fun `install stages remotely verifies outcome and always performs bounded cleanup`() {
        assertContainsAll(
            manager,
            "stageApk",
            "verifyInstalledPackage",
            "cleanupStagedApk",
            "NonCancellable",
            "finally",
        )
        assertContainsAll(protocolFactory, "stageApk", "installStagedApk", "deleteStagedApk")
        assertTrue(
            protocolFactory.contains("mode = 0x1A4"),
            "Sync SEND mode is the documented 0644 permission field",
        )
        assertContainsAll(
            protocolFactory,
            "kadb.openShell(",
            "cat >",
            "uploadStream.write",
            "uploadStream.closeStdin()",
            "uploadStream.readAll()",
            "MAX_SHELL_UPLOAD_SEGMENT_BYTES",
            "cat >>",
        )
        assertFalse(
            protocolFactory.contains("legacySendSync"),
            "uploads must not use the Sync path that closes on supported Android adbd",
        )
        assertTrue(
            Regex("""withTimeout(?:OrNull)?\s*\(""").containsMatchIn(manager),
            "remote staging, install, verification and cleanup must all remain bounded",
        )
        assertFalse(manager.contains("GlobalScope"), "install must remain owned by the active Session")
        assertTrue(
            manager.contains("apkInstallFailureCode"),
            "an unknown install outcome must retain a sanitized technical cause instead of hiding the failed stage",
        )
        assertTrue(models.contains("technicalCode"))
    }

    @Test
    fun `standard install discovers exactly one new package before claiming verified success`() {
        assertContainsAll(
            manager,
            "packagesBeforeInstall",
            "discoverSingleInstalledPackage",
            "pm list packages --user",
            "VerifiedInstalled",
        )
        assertFalse(
            Regex("targetPackage\\s*==\\s*null[\\s\\S]{0,120}OutcomeUnknown").containsMatchIn(manager),
            "a successful standard install must attempt bounded before/after package verification",
        )
    }

    @Test
    fun `unchanged identity requests force once then accepts an explicit replacement`() {
        assertContainsAll(
            models,
            "PackageManagerAccepted",
            "privateDataPreserved",
        )
        assertContainsAll(
            manager,
            "ApkInstallMode.STANDARD ->",
            "ApkInstallResult.Rejected(request.expectedSessionId, \"INSTALL_FAILED\")",
            "ApkInstallResult.PackageManagerAccepted",
        )
    }

    @Test
    fun `duplicate clicks are busy and mismatch removal followed by install failure never claims rollback`() {
        assertContainsAll(
            models,
            "AdbExclusiveOperationKind",
            "APK_INSTALL",
            "OldRemovedNoRollback",
            "OutcomeUnknown",
            "Cancelled",
            "TimedOut",
        )
        assertContainsAll(manager, "OperationConflict", "OldRemovedNoRollback")
        assertFalse(manager.contains("rollbackApkInstall"), "v1.0 has no APK install rollback contract")
        assertFalse(protocolFactory.contains("ProcessBuilder("), "host adb must never be launched")
        assertFalse(protocolFactory.contains("su -c"), "APK install must never use Root")
    }

    private fun source(relativePath: String): String =
        File("src/main/kotlin/com/sheen/adb/core/internal", relativePath)
            .takeIf(File::isFile)
            ?.readText()
            .orEmpty()

    private fun assertContainsAll(source: String, vararg tokens: String) {
        tokens.forEach { token ->
            assertTrue(source.contains(token), "Missing APK install contract token: $token")
        }
    }
}
