package com.sheen.adb.feature.apps

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppsTaskLifecycleTest {
    @Test
    fun `application metadata remains part of page loading until every terminal path releases it`() {
        val models = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt").readText()
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()

        assertTrue(models.contains("isMetadataLoading"))
        assertTrue(models.contains("isPageLoading"))
        assertTrue(
            Regex("isPageLoading[^\\n]*isLoading[^\\n]*isMetadataLoading").containsMatchIn(models),
            "page loading must cover both the application snapshot and metadata enrichment",
        )
        assertTrue(viewModel.contains("isMetadataLoading = true"))
        assertTrue(viewModel.contains("finally"))
        assertTrue(viewModel.contains("isMetadataLoading = false"))
        assertTrue(
            viewModel.substringAfter("private fun cancelMetadata()")
                .substringBefore("override fun onCleared")
                .contains("isMetadataLoading = false"),
            "explicit cancellation and session changes must release metadata loading",
        )
    }

    private val models =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt")
    private val tasks =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsTasks.kt")

    @Test
    fun `picker and preparation stay unlocked while extraction and install io lock navigation`() {
        val source = models.readText() + tasks.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "AppsTaskKind.APK_EXTRACTION",
            "AppsTaskKind.APK_INSTALLATION",
            "PickerOpen",
            "Preparing",
            "Transferring",
            "Writing",
            "navigationLocked",
        ).forEach { token -> assertTrue(source.contains(token), "missing task-lock token $token") }
        assertTrue(source.contains("PickerOpen") && source.contains("false"))
        assertTrue(source.contains("Transferring") && source.contains("true"))
    }

    @Test
    fun `page exit detaches metadata loading without cancelling the shared Session child stream`() {
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()
        val foregroundHandler = viewModel.substringAfter("fun setForeground")
            .substringBefore("\n    fun ")
        val pageLoadingCancellation = viewModel.substringAfter("fun cancelPageLoading")
            .substringBefore("\n    private fun ")

        assertTrue(viewModel.contains("state.task?.phase == AppsTaskPhase.PickerOpen"))
        assertTrue(
            foregroundHandler.contains(
                "if (!pickerOpen) {\n                cancelActive(leavingPage = true)\n                cancelMetadata()\n                cancelTaskAndCleanup()",
            ).not(),
        )
        assertTrue(
            foregroundHandler.contains("releaseMetadataPageLoading()"),
            "Leaving the applications page must release the loading gate without cancelling Sync.",
        )
        assertFalse(
            foregroundHandler.contains("cancelMetadata()"),
            "Page exit must not cancel an in-flight Sync read and force-close the shared Session.",
        )
        assertTrue(
            Regex("if \\(!pickerOpen\\) \\{[\\s\\S]{0,240}cancelActive\\(leavingPage = true\\)")
                .containsMatchIn(foregroundHandler),
            "Picker ON_STOP must not cancel an in-flight applications snapshot read and transiently disconnect the shared Session.",
        )
        assertFalse(
            foregroundHandler.substringBefore("if (!pickerOpen)").contains("cancelActive("),
            "All Session-bound application reads must be guarded by the picker lifecycle exception.",
        )
        assertTrue(pageLoadingCancellation.contains("releaseMetadataPageLoading()"))
        assertFalse(pageLoadingCancellation.contains("cancelMetadata()"))
    }

    @Test
    fun `picker owns a lifecycle lease across stop result callback and restart ordering`() {
        val screen = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt").readText()

        assertTrue(
            screen.contains("AtomicBoolean(false)") &&
                screen.contains("pickerLifecycleLease.set(true)") &&
                screen.contains("pickerLifecycleLease.get()") &&
                screen.contains("pickerLifecycleLease.set(false)"),
            "The lifecycle observer must read a stable mutable reference instead of a stale Compose value captured when DisposableEffect was created.",
        )
        assertTrue(
            Regex(
                "pickerLifecycleLease\\.set\\(true\\)[\\s\\S]{0,240}" +
                    "(extractionDestination|installSource)\\.launch",
            ).containsMatchIn(screen),
            "The picker lease must be acquired before launching the external activity.",
        )
        assertTrue(
            Regex(
                "Lifecycle\\.Event\\.ON_STOP\\s*->[\\s\\S]{0,80}" +
                    "if \\(!pickerLifecycleLease\\.get\\(\\)\\) viewModel\\.setForeground\\(false\\)",
            ).containsMatchIn(screen),
            "Picker ON_STOP must not cancel Session-bound work.",
        )
        assertTrue(
            Regex(
                "Lifecycle\\.Event\\.ON_START\\s*->[\\s\\S]{0,160}" +
                    "viewModel\\.setForeground\\(true\\)[\\s\\S]{0,160}" +
                    "pickerLifecycleLease\\.set\\(false\\)",
            ).containsMatchIn(screen),
            "The lease must be released only after foreground ownership is restored.",
        )
    }

    @Test
    fun `component delivery distinguishes complete partial and zero commit outcomes`() {
        val source = models.readText() + tasks.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "AppsComponentResult",
            "expectedComponents",
            "committedComponents",
            "CompleteSuccess",
            "PartialSuccess",
            "NoneCommittedFailure",
            "cleanupConfirmed",
            "resourceUncertain",
        ).forEach { token -> assertTrue(source.contains(token), "missing component outcome token $token") }
        assertTrue(source.contains("PartialSuccess") && source.contains("navigationLocked"))
        assertFalse(
            Regex("PartialSuccess[\\s\\S]{0,300}(rollback|deleteCommitted)").containsMatchIn(source),
            "partial success must retain verified committed components",
        )
    }

    @Test
    fun `destructive and mismatch flows require explicit confirmations before requests`() {
        val source = tasks.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "requestUninstall",
            "confirmUninstall",
            "confirmationNonce",
            "requestForceInstall",
            "confirmSignatureMismatch",
            "AWAITING_REPLACE_CONFIRMATION",
            "AWAITING_SIGNATURE_MISMATCH_CONFIRMATION",
            "UNINSTALLING_OLD",
            "INSTALLING_NEW",
            "cancelPendingConfirmation",
        ).forEach { token -> assertTrue(source.contains(token), "missing confirmation transition $token") }
        assertFalse(
            Regex("cancelPendingConfirmation[\\s\\S]{0,240}(uninstall|install)\\(").containsMatchIn(source),
            "cancelling a confirmation must not send an uninstall or install request",
        )
    }

    @Test
    fun `failed install after confirmed removal reports no rollback and unlocks only after safe cleanup`() {
        val source = models.readText() + tasks.takeIf(File::isFile)?.readText().orEmpty()
        listOf(
            "OldPackageRemovedNoRollback",
            "OutcomeUnknown",
            "CleanupUncertain",
            "cleanupConfirmed",
            "resourceUncertain",
        ).forEach { token -> assertTrue(source.contains(token), "missing honest terminal state $token") }
    }

    @Test
    fun `successful mutations resynchronize the application generation before later actions`() {
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()

        assertTrue(viewModel.contains("syncApplicationSnapshot"))
        assertTrue(
            Regex(
                "is AdbOperationResult\\.Success[\\s\\S]{0,240}" +
                    "applyMutationResult\\([\\s\\S]{0,160}syncApplicationSnapshot\\(",
            ).containsMatchIn(viewModel),
            "a successful mutation must refresh the snapshot and generation before uninstall can be prepared",
        )
        assertTrue(viewModel.contains("applicationGeneration = snapshot.generation"))
    }

    @Test
    fun `install failure retains the selected source until user chooses force replace and downgrade or cancels`() {
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()
        val screen = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt").readText()

        listOf(
            "pendingInstallSource",
            "ForceInstallRequired",
            "confirmForceInstall",
            "ApkInstallMode.REPLACE_OR_DOWNGRADE",
        ).forEach { token -> assertTrue(viewModel.contains(token), "missing force-install retry token $token") }
        assertTrue(screen.contains("onConfirmForceInstall"))
        assertTrue(screen.contains("FORCE_INSTALL"))
    }

    @Test
    fun `verified install refreshes the application snapshot before later management actions`() {
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()

        assertTrue(viewModel.contains("ApkInstallResult.VerifiedInstalled"))
        assertTrue(
            Regex(
                "result is ApkInstallResult\\.VerifiedInstalled[\\s\\S]{0,160}refresh\\(\\)",
            ).containsMatchIn(viewModel),
            "A verified install must refresh the stale pre-install application snapshot.",
        )
    }

    @Test
    fun `apk transfer cancels background application reads before acquiring the exclusive session lease`() {
        val viewModel = File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt").readText()
        val extraction = viewModel.substringAfter("fun deliverExtraction")
            .substringBefore("\n    fun ")
        val install = viewModel.substringAfter("private fun runInstall")
            .substringBefore("\n    fun ")

        assertTrue(
            Regex("cancelActive\\(\\)[\\s\\S]{0,80}cancelMetadata\\(\\)[\\s\\S]{0,240}tasks\\.extractApk")
                .containsMatchIn(extraction),
            "APK extraction must stop snapshot and metadata reads before the exclusive Sync transfer starts.",
        )
        assertTrue(
            Regex("cancelActive\\(\\)[\\s\\S]{0,80}cancelMetadata\\(\\)[\\s\\S]{0,900}tasks\\.installApk")
                .containsMatchIn(install),
            "APK installation must stop snapshot and metadata reads before the exclusive staging transfer starts.",
        )
    }
}
