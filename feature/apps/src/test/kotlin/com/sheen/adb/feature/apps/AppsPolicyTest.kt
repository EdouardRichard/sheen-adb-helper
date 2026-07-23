package com.sheen.adb.feature.apps

import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.ApplicationField
import com.sheen.adb.core.ApplicationMetadataStatus
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class AppsPolicyTest {
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
            metadataByPackage = mapOf(
                "com.Example.reader_2" to metadata("中文阅读器"),
                "org.sample.disabled_3" to metadata("Reader Pro"),
                "net.example.numeric4" to metadata("Reader Pro"),
            ),
        )

        assertEquals(state.copy(query = "中文").visibleApplications.map { it.packageName }, listOf("com.Example.reader_2"))
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
    fun `same display names remain distinct by package and unavailable metadata keeps package placeholder`() {
        val first = app("com.example.first")
        val second = app("com.example.second")
        val fallback = app("com.example.fallback")
        val state = connectedState().copy(
            applications = listOf(first, second, fallback),
            metadataByPackage = mapOf(
                first.packageName to metadata("同名应用"),
                second.packageName to metadata("同名应用"),
                fallback.packageName to AppsApplicationMetadata(
                    displayName = null,
                    icon = null,
                    status = ApplicationMetadataStatus.UNAVAILABLE,
                ),
            ),
            query = "同名",
        )

        assertEquals(state.visibleApplications.map { it.packageName }, listOf(first.packageName, second.packageName))
        assertEquals(
            state.copy(query = "fallback").visibleApplications.single().packageName,
            fallback.packageName,
        )
        assertEquals(state.metadataByPackage[fallback.packageName]?.status, ApplicationMetadataStatus.UNAVAILABLE)
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
            metadataByPackage = mapOf("com.example.client" to metadata("客户端")),
            userId = 0,
            query = "client",
            filter = AppsFilter.DISABLED,
            degradedReason = "降级",
            unavailableFields = setOf(ApplicationField.VERSION_NAME),
            error = AdbError.Timeout(AdbOperationStage.APPLICATIONS_LIST),
            pendingConfirmation = AppsConfirmation(AppsOperation.DISABLE, "com.example.client", "one", "设备", 0),
            operationNotice = AppsOperationNotice("结果未知", true),
        )
        val switched = AppsPolicy.changedSession(dirty, true, "two", "新设备", false)
        assertTrue(switched.applications.isEmpty())
        assertTrue(switched.metadataByPackage.isEmpty())
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
        assertEquals(connectedState().copy(degradedReason = "字段不可用").degradedReason, "字段不可用")
        assertTrue(connectedState().copy(error = AdbError.ApplicationListUnsupported).error is AdbError.ApplicationListUnsupported)
        assertFalse(connectedState().copy(activeOperation = null, isLoading = false).isBusy)
        assertTrue(connectedState().copy(operationNotice = AppsOperationNotice("结果未知", true)).operationNotice?.outcomeUnknown == true)
    }

    private fun connectedState() = AppsUiState(isConnected = true, sessionId = "one", deviceDisplayName = "设备", userId = 0)

    private fun app(name: String, state: RemoteApplicationEnabledState = RemoteApplicationEnabledState.ENABLED) =
        RemoteApplication(name, userId = 0, enabledState = state, isSystem = false)

    private fun metadata(name: String) = AppsApplicationMetadata(
        displayName = name,
        icon = null,
        status = ApplicationMetadataStatus.AVAILABLE,
    )
}
