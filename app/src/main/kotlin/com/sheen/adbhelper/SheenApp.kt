package com.sheen.adbhelper

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.feature.apps.AppsRoute
import com.sheen.adb.feature.apps.AppsViewModel
import com.sheen.adb.feature.devices.DeviceHistoryMenu
import com.sheen.adb.feature.devices.DevicesRoute
import com.sheen.adb.feature.devices.DevicesUiState
import com.sheen.adb.feature.devices.DevicesViewModel
import com.sheen.adb.feature.files.FilesRoute
import com.sheen.adb.feature.files.FilesViewModel
import com.sheen.adb.feature.files.FileTaskSummaryBar
import com.sheen.adb.feature.files.taskSummary
import com.sheen.adb.feature.logcat.LogcatRoute
import com.sheen.adb.feature.logcat.LogcatViewModel
import com.sheen.adb.feature.overview.OverviewRoute
import com.sheen.adb.feature.overview.OverviewViewModel
import com.sheen.adb.feature.overview.QuickActionArtifactFormat
import com.sheen.adb.feature.overview.QuickActionArtifactRef
import com.sheen.adb.feature.processes.ProcessesRoute
import com.sheen.adb.feature.processes.ProcessesViewModel
import com.sheen.adb.feature.shell.ShellRoute
import com.sheen.adb.feature.shell.ShellViewModel
import com.sheen.adb.feature.settings.SettingsRoute
import com.sheen.adb.feature.settings.SettingsViewModel
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings
import com.sheen.adb.data.ExportDestination
import com.sheen.adb.data.LanguagePreference
import kotlinx.coroutines.launch

internal enum class Destination(val label: String, val requiresConnection: Boolean) {
    DEVICES("连接", false),
    FILES("文件", true),
    APPS("应用", true),
    PROCESSES("进程", true),
    SHELL("终端", true),
    LOGCAT("日志", true),
}

@Composable
fun SheenApp(
    container: AppContainer,
    files: FilesViewModel,
    overview: OverviewViewModel,
) {
    val context = LocalContext.current
    val devices: DevicesViewModel = viewModel(factory = factory {
        DevicesViewModel(container.adbManager, container.deviceProfiles)
    })
    val apps: AppsViewModel = viewModel(factory = factory { AppsViewModel(container.adbManager) })
    val shell: ShellViewModel = viewModel(factory = factory { ShellViewModel(container.adbManager) })
    val processes: ProcessesViewModel = viewModel(factory = factory { ProcessesViewModel(container.adbManager) })
    val logcat: LogcatViewModel = viewModel(factory = factory {
        LogcatViewModel(container.adbManager, container.textExporter, container.logcatShareFileStore)
    })
    val settings: SettingsViewModel = viewModel(factory = factory {
        SettingsViewModel(
            versionLabel = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
            repository = container.deviceProfiles,
            manager = container.adbManager,
            temporaryDataCleaner = container.temporaryDataCleaner,
        )
    })
    val devicesState by devices.state.collectAsStateWithLifecycle()
    val languagePreference by container.deviceProfiles.languagePreference.collectAsStateWithLifecycle(
        initialValue = LanguagePreference.ZH_CN,
    )
    val language = UiLanguage.fromPreference(languagePreference.persistedValue)
    val localPairingControllerState by container.localPairingBridge.state.collectAsStateWithLifecycle()
    val filesState by files.state.collectAsStateWithLifecycle()
    val connected = devicesState.connectionState is AdbConnectionState.Connected
    var destination by rememberSaveable { mutableStateOf(Destination.DEVICES) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var handledNotificationPermissionGeneration by rememberSaveable { mutableStateOf(0L) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val available = granted && context.notificationsEnabled()
        container.localPairingBridge.onNotificationPermissionResult(
            granted = available,
            deviceUnlocked = context.isDeviceUnlocked(),
        )
        devices.onLocalNotificationPermissionResult(available)
    }
    val screenshotExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val destination = uri?.let { ExportDestination(uri.toString(), "controlled-screen.png") }
        overview.exportQuickAction(destination)
    }
    val recordingExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        val destination = uri?.let { ExportDestination(uri.toString(), "controlled-screen.mp4") }
        overview.exportQuickAction(destination)
    }
    val onOverviewExportRequested: (QuickActionArtifactRef) -> Unit = { artifact ->
        when (artifact.format) {
            QuickActionArtifactFormat.PNG ->
                screenshotExportLauncher.launch("controlled-screen.png")
            QuickActionArtifactFormat.MP4 ->
                recordingExportLauncher.launch("controlled-screen.mp4")
            null -> overview.exportQuickAction(null)
        }
    }

    LaunchedEffect(localPairingControllerState) {
        container.localPairingBridge.synchronizeService()
    }

    LaunchedEffect(devicesState.notificationPermissionRequestGeneration) {
        val requestGeneration = devicesState.notificationPermissionRequestGeneration
        if (requestGeneration == 0L || requestGeneration <= handledNotificationPermissionGeneration) {
            return@LaunchedEffect
        }
        handledNotificationPermissionGeneration = requestGeneration
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val available = context.notificationsEnabled()
            container.localPairingBridge.onNotificationPermissionResult(
                granted = available,
                deviceUnlocked = context.isDeviceUnlocked(),
            )
            devices.onLocalNotificationPermissionResult(available)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openWirelessDebuggingSettings = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    LaunchedEffect(connected) {
        destination = destinationAfterConnectionChange(destination, connected)
    }

    LaunchedEffect(devicesState.connectionState, devicesState.profiles) {
        val connection = devicesState.connectionState as? AdbConnectionState.Connected
        val displayName = connection?.let { connectedState ->
            devicesState.profiles.firstOrNull {
                it.host == connectedState.endpoint.host && it.debugPort == connectedState.endpoint.port
            }?.displayName
        }
        if (displayName != null) apps.setDeviceDisplayName(displayName)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        Modifier.width(SheenDimensions.expandedPaneWidth).fillMaxHeight(),
                    ) {
                        PrimaryNavigation(
                            selected = destination,
                            connected = connected,
                            language = language,
                            onDestination = { requested ->
                                if (isMenuEnabled(requested.requiresConnection, connected)) {
                                    destination = requested
                                    showSettings = false
                                }
                            },
                        )
                    }
                },
            ) {
                ModalNavigationDrawer(
                    drawerState = drawer,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(SheenDimensions.drawerWidth),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            drawerShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        ) {
                            DrawerContent(
                                devicesState = devicesState,
                                devices = devices,
                                onClose = { scope.launch { drawer.close() } },
                                onSettings = {
                                    showSettings = true
                                    scope.launch { drawer.close() }
                                },
                                onAbout = {
                                    showAbout = true
                                    scope.launch { drawer.close() }
                                },
                                language = language,
                            )
                        }
                    },
                ) {
                    AppScaffold(
                    destination,
                    devicesState.connectionState,
                    devicesState,
                    devices,
                    { scope.launch { drawer.open() } },
                    compact = false,
                    connected = connected,
                    language = language,
                    onDestination = { requested ->
                        if (isMenuEnabled(requested.requiresConnection, connected)) {
                            destination = requested
                            showSettings = false
                        }
                    },
                    fileTaskSummary = {
                        FileTaskSummaryBar(
                            summary = filesState.taskSummary,
                            showViewAction = destination != Destination.FILES,
                            onView = { destination = Destination.FILES },
                            onCancel = files::cancelActiveTask,
                        )
                    },
                    ) {
                        DestinationContent(
                        destination,
                        devices,
                        overview,
                        files,
                        apps,
                        shell,
                        processes,
                        logcat,
                        settings,
                        openWirelessDebuggingSettings,
                        onOverviewExportRequested,
                        connected,
                        showSettings,
                        language,
                        )
                    }
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(SheenDimensions.drawerWidth),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        drawerShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    ) {
                        DrawerContent(
                            devicesState,
                            devices,
                            onClose = { scope.launch { drawer.close() } },
                            onSettings = {
                                showSettings = true
                                scope.launch { drawer.close() }
                            },
                            onAbout = {
                                showAbout = true
                                scope.launch { drawer.close() }
                            },
                            language = language,
                        )
                    }
                },
            ) {
                AppScaffold(
                    destination,
                    devicesState.connectionState,
                    devicesState,
                    devices,
                    { scope.launch { drawer.open() } },
                    compact = true,
                    connected = connected,
                    language = language,
                    onDestination = { requested ->
                        if (isMenuEnabled(requested.requiresConnection, connected)) {
                            destination = requested
                            showSettings = false
                        }
                    },
                    fileTaskSummary = {
                        FileTaskSummaryBar(
                            summary = filesState.taskSummary,
                            showViewAction = destination != Destination.FILES,
                            onView = { destination = Destination.FILES },
                            onCancel = files::cancelActiveTask,
                        )
                    },
                ) {
                    DestinationContent(
                        destination,
                        devices,
                        overview,
                        files,
                        apps,
                        shell,
                        processes,
                        logcat,
                        settings,
                        openWirelessDebuggingSettings,
                        onOverviewExportRequested,
                        connected,
                        showSettings,
                        language,
                    )
                }
            }
        }
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(V01Strings.text(language, V01StringKey.ABOUT)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(V01Strings.text(language, V01StringKey.ABOUT_SUPPORT))
                    Text(ABOUT_REPOSITORY_URL, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_REPOSITORY_URL)),
                        )
                    },
                ) { Text(V01Strings.text(language, V01StringKey.ABOUT_OPEN_GITHUB)) }
            },
            dismissButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun PrimaryNavigation(
    selected: Destination,
    connected: Boolean,
    language: UiLanguage,
    onDestination: (Destination) -> Unit,
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Sheen ADB 助手",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(12.dp),
        )
        Destination.entries.forEach { destination ->
            val enabled = isMenuEnabled(destination.requiresConnection, connected)
            NavigationDrawerItem(
                label = { Text(destination.localizedLabel(language)) },
                selected = selected == destination,
                onClick = {
                    if (isMenuEnabled(destination.requiresConnection, connected)) {
                        onDestination(destination)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .alpha(if (enabled) 1f else 0.38f)
                    .semantics {
                        contentDescription = destination.localizedLabel(language)
                        if (!enabled) disabled()
                    },
            )
        }
    }
}

@Composable
private fun DrawerContent(
    devicesState: DevicesUiState,
    devices: DevicesViewModel,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    language: UiLanguage,
) {
    Column(Modifier.fillMaxHeight()) {
        DrawerHeader(onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            DeviceHistoryMenu(
                state = devicesState,
                actions = devices,
                language = language,
            )
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            Text(
                if (language == UiLanguage.ZH_CN) "系统" else "System",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            DrawerMenuRow(
                icon = SheenIcons.Settings,
                label = V01Strings.text(language, V01StringKey.SETTINGS),
                onClick = onSettings,
            )
            DrawerMenuRow(
                icon = SheenIcons.Info,
                label = V01Strings.text(language, V01StringKey.ABOUT),
                onClick = onAbout,
            )
        }
        DrawerFooter()
    }
}

@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, SheenShapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SheenIcons.Adb,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text("ADB 助手", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        IconButton(onClick = onClose, modifier = Modifier.size(SheenDimensions.minimumTouchTarget)) {
            Icon(
                SheenIcons.Close,
                contentDescription = "关闭菜单",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerFooter() {
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    destination: Destination,
    connection: AdbConnectionState,
    devicesState: DevicesUiState,
    devices: DevicesViewModel,
    onMenu: (() -> Unit)?,
    compact: Boolean,
    connected: Boolean,
    language: UiLanguage,
    onDestination: (Destination) -> Unit,
    fileTaskSummary: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            if (destination == Destination.DEVICES) {
                SheenConnectionTopBar(
                    state = devicesState,
                    actions = devices,
                    onMenu = onMenu,
                    language = language,
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(destination.localizedLabel(language))
                            Text(
                                connectionStatusName(connection, language),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        if (onMenu != null) TextButton(
                            onClick = onMenu,
                            modifier = Modifier.semantics { contentDescription = "打开导航菜单" },
                        ) { Text("☰", style = MaterialTheme.typography.headlineSmall) }
                    },
                )
            }
        },
        bottomBar = {
            if (compact) {
                SheenBottomNavigation(
                    selected = destination,
                    connected = connected,
                    language = language,
                    onDestination = onDestination,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            fileTaskSummary()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun DestinationContent(
    destination: Destination,
    devices: DevicesViewModel,
    overview: OverviewViewModel,
    files: FilesViewModel,
    apps: AppsViewModel,
    shell: ShellViewModel,
    processes: ProcessesViewModel,
    logcat: LogcatViewModel,
    settings: SettingsViewModel,
    onOpenWirelessDebuggingSettings: () -> Unit,
    onOverviewExportRequested: (QuickActionArtifactRef) -> Unit,
    connected: Boolean,
    showSettings: Boolean,
    language: UiLanguage,
) {
    if (showSettings) {
        SettingsRoute(settings)
        return
    }
    when (destination) {
        Destination.DEVICES -> if (connected) {
            OverviewRoute(
                overview,
                onExportRequested = { artifact -> onOverviewExportRequested(artifact) },
                language = language,
            )
        } else {
            DevicesRoute(devices, onOpenWirelessDebuggingSettings, language)
        }
        Destination.FILES -> FilesRoute(files)
        Destination.APPS -> AppsRoute(apps)
        Destination.PROCESSES -> ProcessesRoute(processes)
        Destination.SHELL -> ShellRoute(shell)
        Destination.LOGCAT -> LogcatRoute(logcat)
    }
}

@Composable
private fun SheenConnectionTopBar(
    state: DevicesUiState,
    actions: DevicesViewModel,
    onMenu: (() -> Unit)?,
    language: UiLanguage,
) {
    val connectedState = state.connectionState as? AdbConnectionState.Connected
    val canConnect = state.connectionState is AdbConnectionState.Disconnected ||
        state.connectionState is AdbConnectionState.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = SheenDimensions.minimumTouchTarget)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { onMenu?.invoke() },
            enabled = onMenu != null,
            modifier = Modifier.size(SheenDimensions.minimumTouchTarget),
        ) {
            Icon(
                SheenIcons.Menu,
                contentDescription = V01Strings.text(language, V01StringKey.MENU),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (connectedState != null) {
            Text(
                "${connectedState.endpoint.host}:${connectedState.endpoint.port}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        } else {
            BasicTextField(
                value = state.endpointInput,
                onValueChange = actions::updateEndpoint,
                enabled = canConnect,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(SheenDimensions.topBarVisualHeight - 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, SheenShapes.default)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SheenShapes.default)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = "ADB 调试地址" },
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.endpointInput.isBlank()) {
                            Text(
                                V01Strings.text(language, V01StringKey.CONNECTION_ENDPOINT_HINT),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        input()
                    }
                },
            )
        }
        IconButton(
            onClick = if (connectedState != null) actions::disconnect else actions::connect,
            enabled = connectedState != null || canConnect,
            modifier = Modifier.size(SheenDimensions.minimumTouchTarget),
        ) {
            Icon(
                if (connectedState != null) SheenIcons.LinkOff else SheenIcons.Link,
                contentDescription = V01Strings.text(
                    language,
                    if (connectedState != null) V01StringKey.TOP_DISCONNECT else V01StringKey.TOP_CONNECT,
                ),
                tint = if (connectedState != null) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@Composable
private fun SheenBottomNavigation(
    selected: Destination,
    connected: Boolean,
    language: UiLanguage,
    onDestination: (Destination) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(SheenDimensions.bottomBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            val enabled = isMenuEnabled(destination.requiresConnection, connected)
            val active = destination == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SheenDimensions.minimumTouchTarget)
                    .alpha(if (enabled) 1f else .38f)
                    .clickable(enabled = enabled) { onDestination(destination) }
                    .semantics {
                        contentDescription = destination.localizedLabel(language)
                        if (!enabled) disabled()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        destination.navigationIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (active) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        destination.localizedLabel(language),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val ABOUT_REPOSITORY_URL =
    "https://github.com/EdouardRichard/sheen-adb-helper"

private fun Destination.navigationIcon(): ImageVector = when (this) {
    Destination.DEVICES -> SheenIcons.Remote
    Destination.FILES -> SheenIcons.Folder
    Destination.APPS -> SheenIcons.Apps
    Destination.PROCESSES -> SheenIcons.Processes
    Destination.SHELL -> SheenIcons.Terminal
    Destination.LOGCAT -> SheenIcons.BugReport
}

internal fun isMenuEnabled(requiresConnection: Boolean, connected: Boolean): Boolean =
    connected || !requiresConnection

internal fun destinationAfterConnectionChange(current: Destination, connected: Boolean): Destination =
    if (!connected && current.requiresConnection) Destination.DEVICES else current

internal fun connectionStatusName(connection: AdbConnectionState): String = when (connection) {
    is AdbConnectionState.Disconnected -> "未连接"
    is AdbConnectionState.Connecting -> "连接中"
    is AdbConnectionState.AwaitingAuthorization -> "等待设备授权"
    is AdbConnectionState.Connected -> "已连接"
    is AdbConnectionState.Pairing -> "配对中"
    AdbConnectionState.Disconnecting -> "断开中"
    is AdbConnectionState.Error -> "错误"
}

internal fun connectionStatusName(
    connection: AdbConnectionState,
    language: UiLanguage,
): String = when (connection) {
    is AdbConnectionState.Disconnected -> V01Strings.text(language, V01StringKey.CONNECTION_DISCONNECTED)
    is AdbConnectionState.Connecting -> V01Strings.text(language, V01StringKey.CONNECTION_CONNECTING)
    is AdbConnectionState.AwaitingAuthorization ->
        if (language == UiLanguage.ZH_CN) "等待设备授权" else "Waiting for device authorization"
    is AdbConnectionState.Connected -> V01Strings.text(language, V01StringKey.CONNECTION_CONNECTED)
    is AdbConnectionState.Pairing ->
        if (language == UiLanguage.ZH_CN) "配对中" else "Pairing"
    AdbConnectionState.Disconnecting ->
        if (language == UiLanguage.ZH_CN) "断开中" else "Disconnecting"
    is AdbConnectionState.Error ->
        if (language == UiLanguage.ZH_CN) "错误" else "Error"
}

private fun Destination.localizedLabel(language: UiLanguage): String =
    V01Strings.text(
        language,
        when (this) {
            Destination.DEVICES -> V01StringKey.NAV_CONNECT
            Destination.FILES -> V01StringKey.NAV_FILES
            Destination.APPS -> V01StringKey.NAV_APPS
            Destination.PROCESSES -> V01StringKey.NAV_PROCESSES
            Destination.SHELL -> V01StringKey.NAV_TERMINAL
            Destination.LOGCAT -> V01StringKey.NAV_LOGS
        },
    )

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <R : ViewModel> create(modelClass: Class<R>): R = create() as R
    }

private fun Context.isDeviceUnlocked(): Boolean =
    !(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked

private fun Context.notificationsEnabled(): Boolean =
    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
