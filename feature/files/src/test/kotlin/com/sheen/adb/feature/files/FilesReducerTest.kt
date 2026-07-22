package com.sheen.adb.feature.files

import com.sheen.adb.core.RemoteBreadcrumb
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.core.RemoteLinkResolution
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.RemoteDirectorySnapshot
import com.sheen.adb.core.RemoteDirectorySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FilesReducerTest {
    @Test
    fun `browser starts at shared storage and supports root breadcrumbs refresh and selection`() {
        var state = FilesUiState(sessionId = "s")
        state = FilesReducer.reduce(state, FilesAction.OpenSharedStorage)
        assertTrue(state.browser is FilesBrowserState.Loading)
        assertEquals((state.browser as FilesBrowserState.Loading).path, null)

        state = FilesReducer.reduce(state, FilesAction.OpenDeviceRoot)
        assertEquals((state.browser as FilesBrowserState.Loading).path, "/")
        state = FilesReducer.reduce(state, FilesAction.OpenBreadcrumb("/storage"))
        assertEquals((state.browser as FilesBrowserState.Loading).path, "/storage")
        state = FilesReducer.reduce(state, FilesAction.Refresh)
        assertEquals((state.browser as FilesBrowserState.Loading).path, "/storage")

        state = FilesReducer.reduce(state, FilesAction.Select("/storage/file.txt"))
        assertEquals(state.selectedPath, "/storage/file.txt")
    }

    @Test
    fun `breadcrumb display path contains separators without button padding gaps`() {
        assertEquals(
            breadcrumbDisplayPath(
                listOf(
                    RemoteBreadcrumb("/", "/"),
                    RemoteBreadcrumb("storage", "/storage"),
                    RemoteBreadcrumb("emulated", "/storage/emulated"),
                    RemoteBreadcrumb("0", "/storage/emulated/0"),
                ),
            ),
            "/storage/emulated/0",
        )
    }

    @Test
    fun `content exposes link badge and all terminal browser states`() {
        val link = FileBrowserEntry(
            absolutePath = "/sdcard/link",
            name = "link",
            kind = RemoteFileKind.SYMLINK,
            linkResolution = RemoteLinkResolution.PERMISSION_DENIED,
            targetKind = null,
            sizeBytes = null,
        )
        var state = FilesReducer.reduce(
            FilesUiState(sessionId = "s"),
            FilesAction.ShowContent("/sdcard", listOf(link), listOf(RemoteBreadcrumb("sdcard", "/sdcard"))),
        )
        assertTrue(state.browser is FilesBrowserState.Content)
        assertEquals(link.badge, "链接 · 无权限")
        assertFalse(link.enterable)

        state = FilesReducer.reduce(state, FilesAction.ShowEmpty("/empty", emptyList()))
        assertTrue(state.browser is FilesBrowserState.Empty)
        state = FilesReducer.reduce(
            state,
            FilesAction.ShowError(
                FileTaskError(FileTaskErrorCategory.PERMISSION_DENIED, "无权限", "返回上级", "PERMISSION_DENIED"),
            ),
        )
        assertTrue(state.browser is FilesBrowserState.Error)
        state = FilesReducer.reduce(state, FilesAction.Disconnected)
        assertTrue(state.browser is FilesBrowserState.Disconnected)
        state = FilesReducer.reduce(state, FilesAction.Cancelled)
        assertTrue(state.browser is FilesBrowserState.Cancelled)
    }

    @Test
    fun `view model initially loads shared storage and maps structured errors`() = runBlocking {
        val gateway = FakeGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(gateway, scope)
        gateway.connection.value = AdbConnectionState.Connected(AdbEndpoint("device.invalid", 1), "s1")
        awaitState { viewModel.state.value.browser is FilesBrowserState.Empty }
        assertEquals(gateway.paths.single(), null)

        gateway.result = AdbOperationResult.Failure(AdbError.RemotePermissionDenied)
        viewModel.refresh()
        awaitState { viewModel.state.value.browser is FilesBrowserState.Error }
        val error = (viewModel.state.value.browser as FilesBrowserState.Error).error
        assertEquals(error.category, FileTaskErrorCategory.PERMISSION_DENIED)
        scope.cancel()
    }

    @Test
    fun `view model ignores stale session results and cancellation is visible`() = runBlocking {
        val deferred = CompletableDeferred<AdbOperationResult<RemoteDirectorySnapshot>>()
        val gateway = FakeGateway().apply { suspendedResult = deferred }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(gateway, scope)
        gateway.connection.value = AdbConnectionState.Connected(AdbEndpoint("one.invalid", 1), "s1")
        awaitState { gateway.paths.isNotEmpty() }
        gateway.connection.value = AdbConnectionState.Connected(AdbEndpoint("two.invalid", 2), "s2")
        deferred.complete(AdbOperationResult.Success(snapshot("s1", "/stale")))
        delay(50)
        assertEquals(viewModel.state.value.sessionId, "s2")
        assertFalse(viewModel.state.value.browser is FilesBrowserState.Content)

        gateway.suspendedResult = null
        gateway.result = AdbOperationResult.Cancelled
        viewModel.refresh()
        awaitState { viewModel.state.value.browser is FilesBrowserState.Cancelled }
        scope.cancel()
    }

    @Test
    fun `view model refresh race keeps only the newest request`() = runBlocking {
        val gateway = FakeGateway()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = FilesViewModel(gateway, scope)
        gateway.connection.value = AdbConnectionState.Connected(AdbEndpoint("race.invalid", 1), "s1")
        awaitState { viewModel.state.value.browser is FilesBrowserState.Empty }

        val older = CompletableDeferred<AdbOperationResult<RemoteDirectorySnapshot>>()
        gateway.suspendedResult = older
        viewModel.refresh()
        awaitState { gateway.paths.size >= 2 }
        val newer = CompletableDeferred<AdbOperationResult<RemoteDirectorySnapshot>>()
        gateway.suspendedResult = newer
        viewModel.refresh()
        awaitState { gateway.paths.size >= 3 }

        newer.complete(AdbOperationResult.Success(snapshot("s1", "/new")))
        older.complete(AdbOperationResult.Success(snapshot("s1", "/old")))
        awaitState { (viewModel.state.value.browser as? FilesBrowserState.Empty)?.path == "/new" }
        assertEquals((viewModel.state.value.browser as FilesBrowserState.Empty).path, "/new")
        scope.cancel()
    }

    private suspend fun awaitState(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(5)
        }
        throw AssertionError("state was not reached")
    }

    private fun snapshot(sessionId: String, path: String) = RemoteDirectorySnapshot(
        sessionId = sessionId,
        directory = path,
        entries = emptyList(),
        sourceCapabilities = setOf(RemoteDirectorySource.LIST_V2),
        loadedAtMonotonicMillis = 1,
    )

    private class FakeGateway : RemoteBrowserGateway {
        override val connection = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected())
        val paths = java.util.Collections.synchronizedList(mutableListOf<String?>())
        var result: AdbOperationResult<RemoteDirectorySnapshot> = AdbOperationResult.Success(
            RemoteDirectorySnapshot(
                sessionId = "s1",
                directory = "/sdcard",
                entries = emptyList(),
                sourceCapabilities = setOf(RemoteDirectorySource.LIST_V2),
                loadedAtMonotonicMillis = 1,
            ),
        )
        var suspendedResult: CompletableDeferred<AdbOperationResult<RemoteDirectorySnapshot>>? = null

        override suspend fun load(path: String?, sessionId: String): AdbOperationResult<RemoteDirectorySnapshot> {
            paths += path
            return suspendedResult?.await() ?: when (val current = result) {
                is AdbOperationResult.Success -> AdbOperationResult.Success(current.value.copy(sessionId = sessionId))
                else -> current
            }
        }
    }
}
