package com.sheen.adb.feature.apps

import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.ApplicationField
import com.sheen.adb.core.ApkInstallStage
import com.sheen.adb.core.ApkInstallResult
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppsPolicyTest {
    @Test
    fun `failed standard install requests explicit force confirmation instead of reporting cancellation`() {
        val failed = ApkInstallResult.Rejected("session", "INSTALL_FAILED")
        assertEquals(
            AppsInstallOutcomePolicy.terminal(failed, forceAttempt = false),
            AppsTaskTerminal.ForceInstallRequired,
        )
        assertTrue(
            AppsInstallOutcomePolicy.terminal(failed, forceAttempt = true)
                is AppsTaskTerminal.OutcomeUnknown,
        )
        assertEquals(
            AppsInstallOutcomePolicy.terminal(
                ApkInstallResult.Cancelled("session", mutationStarted = false),
                forceAttempt = false,
            ),
            AppsTaskTerminal.Cancelled,
        )
    }

    @Test
    fun `explicit replacement accepted by package manager is a successful terminal state`() {
        assertEquals(
            AppsInstallOutcomePolicy.terminal(
                ApkInstallResult.PackageManagerAccepted(
                    expectedSessionId = "session",
                    privateDataPreserved = true,
                ),
                forceAttempt = true,
            ),
            AppsTaskTerminal.Success,
        )
    }

    @Test
    fun `unknown install outcome retains sanitized stage code for diagnosis`() {
        val terminal = AppsInstallOutcomePolicy.terminal(
            ApkInstallResult.OutcomeUnknown(
                expectedSessionId = "session",
                stage = ApkInstallStage.STAGING,
                technicalCode = "APK_SOURCE_READ_FAILED",
            ),
            forceAttempt = false,
        ) as AppsTaskTerminal.OutcomeUnknown

        assertEquals(terminal.technicalCode, "APK_SOURCE_READ_FAILED")
        assertTrue(
            File("src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt")
                .readText()
                .contains("terminal.technicalCode"),
        )
    }

    private val modelsSource =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt")
    private val viewModelSource =
        File("src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt")

    @Test
    fun `typing changes draft only and explicit search applies name or package query`() {
        val models = modelsSource.readText()
        val viewModel = viewModelSource.readText()

        listOf("draftQuery", "appliedQuery").forEach { token ->
            assertTrue(models.contains(token), "missing two-stage search state $token")
        }
        assertTrue(
            models.substringAfter("val visibleApplications").contains("appliedQuery"),
            "visible rows must be derived from the applied query",
        )
        assertFalse(
            models.substringAfter("val visibleApplications").substringBefore("val isBusy").contains("draftQuery"),
            "draft input must not filter until the search icon is clicked",
        )
        assertTrue(viewModel.contains("updateDraftQuery"), "typing must have a draft-only event")
        assertTrue(viewModel.contains("applySearch"), "search icon must have an explicit apply event")
        assertFalse(
            Regex("updateDraftQuery[\\s\\S]{0,400}(refresh|loadApplications)\\(").containsMatchIn(viewModel),
            "typing must not trigger a remote application-list read",
        )
    }

    @Test
    fun `row action policy preserves exact ordinary order and restricted extraction only`() {
        val models = modelsSource.readText()

        listOf(
            "enum class AppsRowAction",
            "EXTRACT_APK",
            "SET_ENABLED",
            "FORCE_STOP",
            "UNINSTALL",
            "ApplicationClassification.ORDINARY",
            "ApplicationClassification.SYSTEM",
            "ApplicationClassification.UNKNOWN",
        ).forEach { token -> assertTrue(models.contains(token), "missing action-policy token $token") }

        val ordinary = models.substringAfter("ApplicationClassification.ORDINARY")
            .substringBefore("ApplicationClassification.SYSTEM")
        assertOrdered(
            ordinary,
            "AppsRowAction.EXTRACT_APK",
            "AppsRowAction.SET_ENABLED",
            "AppsRowAction.FORCE_STOP",
            "AppsRowAction.UNINSTALL",
        )

        val restricted = models.substringAfter("ApplicationClassification.SYSTEM")
        assertTrue(
            restricted.contains("listOf(AppsRowAction.EXTRACT_APK)") ||
                restricted.contains("setOf(AppsRowAction.EXTRACT_APK)"),
            "system and unknown rows must expose extraction only without hidden action slots",
        )
    }

    @Test
    fun `enabled state changes only the middle action semantic without changing its slot`() {
        val models = modelsSource.readText()

        listOf(
            "RemoteApplicationEnabledState.ENABLED",
            "RemoteApplicationEnabledState.DISABLED",
            "AppsRowAction.SET_ENABLED",
            "enable",
        ).forEach { token -> assertTrue(models.contains(token), "missing enabled-state policy token $token") }
        assertFalse(
            models.contains("AppsRowAction.DISABLE, AppsRowAction.ENABLE"),
            "enable and disable are alternate semantics for one fixed action slot",
        )
    }

    @Test
    fun `search and filters operate only on in-memory snapshot`() {
        val state = connectedState().copy(
            applications = listOf(app("com.Example.enabled"), app("org.sample.disabled", RemoteApplicationEnabledState.DISABLED)),
            query = "example",
        )
        assertEquals(state.visibleApplications.map { it.packageName }, listOf("com.Example.enabled"))
        assertEquals(state.copy(query = "", filter = AppsFilter.ENABLED).visibleApplications.size, 1)
        assertEquals(state.copy(query = "", filter = AppsFilter.DISABLED).visibleApplications.single().packageName, "org.sample.disabled")
    }

    @Test
    fun `search matches display name or package with stable visible characters and intersects enabled filter`() {
        val applications = listOf(
            app("com.Example.reader_2"),
            app("org.sample.disabled_3", RemoteApplicationEnabledState.DISABLED),
            app("net.example.numeric4"),
        )
        val state = connectedState().copy(
            applications = applications,
            displayNameByPackage = mapOf(
                "com.Example.reader_2" to "涓枃闃呰鍣?",
                "org.sample.disabled_3" to "Reader Pro",
                "net.example.numeric4" to "Reader Pro",
            ),
        )

        assertEquals(state.copy(query = "涓枃").visibleApplications.map { it.packageName }, listOf("com.Example.reader_2"))
        assertEquals(
            state.copy(query = "READER PRO").visibleApplications.map { it.packageName },
            listOf("org.sample.disabled_3", "net.example.numeric4"),
        )
        assertEquals(
            state.copy(query = "sample.disabled_3").visibleApplications.map { it.packageName },
            listOf("org.sample.disabled_3"),
        )
        assertEquals(
            state.copy(query = "reader", filter = AppsFilter.DISABLED).visibleApplications.map { it.packageName },
            listOf("org.sample.disabled_3"),
        )
        assertEquals(state.copy(query = "numeric4").visibleApplications.single().packageName, "net.example.numeric4")
    }

    @Test
    fun `every mutation requires a fresh confirmation matching current state`() {
        val enabled = app("com.example.enabled")
        val disabled = app("com.example.disabled", RemoteApplicationEnabledState.DISABLED)
        val state = connectedState().copy(applications = listOf(enabled, disabled), userId = 0)

        assertEquals(AppsPolicy.confirmation(state, enabled.packageName, AppsOperation.FORCE_STOP)?.packageName, enabled.packageName)
        assertEquals(AppsPolicy.confirmation(state, enabled.packageName, AppsOperation.DISABLE)?.operation, AppsOperation.DISABLE)
        assertNull(AppsPolicy.confirmation(state, enabled.packageName, AppsOperation.ENABLE))
        assertEquals(AppsPolicy.confirmation(state, disabled.packageName, AppsOperation.ENABLE)?.operation, AppsOperation.ENABLE)
        assertNull(AppsPolicy.confirmation(state.copy(activeOperation = AppsOperation.LOADING), enabled.packageName, AppsOperation.FORCE_STOP))
    }

    @Test
    fun `system unknown out-of-snapshot and local self targets have no mutation entry`() {
        val normal = app("com.example.client")
        val state = connectedState().copy(applications = listOf(normal), userId = 0)
        assertTrue(AppsPolicy.canMutate(state, normal))
        assertFalse(AppsPolicy.canMutate(state, normal.copy(isSystem = true)))
        assertFalse(AppsPolicy.canMutate(state, normal.copy(enabledState = RemoteApplicationEnabledState.UNKNOWN)))
        assertFalse(AppsPolicy.canMutate(state, app("com.example.outside")))

        val self = app(AppsPolicy.SELF_PACKAGE_NAME)
        val local = state.copy(applications = listOf(self), isLocalSession = true)
        assertFalse(AppsPolicy.canMutate(local, self))
        assertTrue(AppsPolicy.canMutate(local.copy(isLocalSession = false), self))
    }

    @Test
    fun `session switch clears snapshot confirmation filters errors and notices`() {
        val dirty = connectedState().copy(
            applications = listOf(app("com.example.client")),
            displayNameByPackage = mapOf("com.example.client" to "瀹㈡埛绔?"),
            userId = 0,
            query = "client",
            filter = AppsFilter.DISABLED,
            degradedReason = "闄嶇骇",
            unavailableFields = setOf(ApplicationField.VERSION_NAME),
            error = AdbError.Timeout(AdbOperationStage.APPLICATIONS_LIST),
            pendingConfirmation = AppsConfirmation(AppsOperation.DISABLE, "com.example.client", "one", "璁惧", 0),
            operationNotice = AppsOperationNotice("缁撴灉鏈煡", true),
        )
        val switched = AppsPolicy.changedSession(dirty, true, "two", "new device", false)
        assertTrue(switched.applications.isEmpty())
        assertTrue(switched.displayNameByPackage.isEmpty())
        assertEquals(switched.query, "")
        assertEquals(switched.filter, AppsFilter.ALL)
        assertNull(switched.pendingConfirmation)
        assertNull(switched.operationNotice)
        assertNull(switched.error)
    }

    @Test
    fun `state represents disconnected loading empty degraded error cancellation and unknown outcomes`() {
        assertFalse(AppsUiState().isConnected)
        assertTrue(connectedState().copy(activeOperation = AppsOperation.LOADING, isLoading = true).isBusy)
        assertTrue(connectedState().applications.isEmpty())
        assertEquals(connectedState().copy(degradedReason = "field unavailable").degradedReason, "field unavailable")
        assertTrue(connectedState().copy(error = AdbError.ApplicationListUnsupported).error is AdbError.ApplicationListUnsupported)
        assertFalse(connectedState().copy(activeOperation = null, isLoading = false).isBusy)
        assertTrue(connectedState().copy(operationNotice = AppsOperationNotice("缁撴灉鏈煡", true)).operationNotice?.outcomeUnknown == true)
    }

    private fun connectedState() = AppsUiState(isConnected = true, sessionId = "one", deviceDisplayName = "璁惧", userId = 0)

    private fun app(name: String, state: RemoteApplicationEnabledState = RemoteApplicationEnabledState.ENABLED) =
        RemoteApplication(name, userId = 0, enabledState = state, isSystem = false)

    private fun assertOrdered(source: String, vararg tokens: String) {
        val positions = tokens.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "missing ordered token from ${tokens.toList()}")
        assertEquals(positions, positions.sorted(), "row actions are not in the approved order")
    }

}
