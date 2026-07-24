package com.sheen.adb.feature.apps

import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import org.testng.Assert.assertEquals
import org.testng.annotations.Test

class AppsPresentationTest {
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
}
