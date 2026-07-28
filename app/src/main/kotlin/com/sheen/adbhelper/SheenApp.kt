package com.sheen.adbhelper

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
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
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.feature.apps.AppsRoute as FeatureAppsRoute
import com.sheen.adb.feature.apps.AppsTaskKind
import com.sheen.adb.feature.apps.AppsTaskPhase
import com.sheen.adb.feature.apps.AppsUiState
import com.sheen.adb.feature.apps.AppsViewModel
import com.sheen.adb.feature.devices.DeviceHistoryMenu
import com.sheen.adb.feature.devices.DevicesPairingOverlayRoute
import com.sheen.adb.feature.devices.DevicesRoute
import com.sheen.adb.feature.devices.DevicesUiState
import com.sheen.adb.feature.devices.DevicesViewModel
import com.sheen.adb.feature.files.FilesRoute as FeatureFilesRoute
import com.sheen.adb.feature.files.FilesViewModel
import com.sheen.adb.feature.files.FileTaskSummaryBar
import com.sheen.adb.feature.files.FileTaskStatus
import com.sheen.adb.feature.files.taskSummary
import com.sheen.adb.feature.logcat.LogcatRoute as FeatureLogcatRoute
import com.sheen.adb.feature.logcat.LogcatSaveStatus
import com.sheen.adb.feature.logcat.LogcatUiState
import com.sheen.adb.feature.logcat.LogcatViewModel
import com.sheen.adb.data.LogcatOutputStore
import com.sheen.adb.feature.overview.OverviewRoute
import com.sheen.adb.feature.overview.OverviewViewModel
import com.sheen.adb.feature.overview.QuickActionArtifactFormat
import com.sheen.adb.feature.overview.QuickActionArtifactRef
import com.sheen.adb.feature.overview.QuickActionOutputLifecycle
import com.sheen.adb.feature.overview.QuickActionOutputPhase
import com.sheen.adb.feature.overview.QuickActionUiState
import com.sheen.adb.feature.processes.ProcessesRoute as FeatureProcessesRoute
import com.sheen.adb.feature.processes.ProcessesViewModel
import com.sheen.adb.feature.shell.ShellRoute as FeatureShellRoute
import com.sheen.adb.feature.shell.ShellViewModel
import com.sheen.adb.feature.settings.SettingsRoute
import com.sheen.adb.feature.settings.SettingsViewModel
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenIcons
import com.sheen.adb.ui.SheenShapes
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings
import com.sheen.adb.data.ExportDestination
import com.sheen.adb.data.LanguagePreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal class RootPairingOverlayController(
    private val onStateChanged: (RootOverlayState?) -> Unit = {},
) {
    var state: RootOverlayState? = null
        private set

    private var ownerSessionId: String? = null
    private var cancelAndClear: (() -> Unit)? = null

    fun show(
        attemptId: String,
        ownerSessionId: String,
        cancelAndClear: () -> Unit,
    ) {
        if (state != null) return
        this.ownerSessionId = ownerSessionId
        this.cancelAndClear = cancelAndClear
        updateState(RootOverlayState.Pairing(attemptId))
    }

    fun onOutsideTap() {
        dismiss()
    }

    fun onBackPressed(): Boolean {
        if (state !is RootOverlayState.Pairing) return false
        dismiss()
        return true
    }

    fun onHostForegroundChanged(
        isForeground: Boolean,
        preserveForWirelessSettings: Boolean = false,
    ) {
        if (!isForeground && !preserveForWirelessSettings) dismiss()
    }

    fun onSessionChanged(sessionId: String?) {
        if (state is RootOverlayState.Pairing && sessionId != ownerSessionId) dismiss()
    }

    fun dismiss() {
        if (state !is RootOverlayState.Pairing) return
        val cleanup = cancelAndClear
        ownerSessionId = null
        cancelAndClear = null
        updateState(null)
        cleanup?.invoke()
    }

    private fun updateState(value: RootOverlayState?) {
        state = value
        onStateChanged(value)
    }
}

@Composable
fun SheenApp(
    container: AppContainer,
    files: FilesViewModel,
    overview: OverviewViewModel,
    hostForeground: Boolean,
) {
    val context = LocalContext.current
    val devices: DevicesViewModel = viewModel(factory = factory {
        DevicesViewModel(container.adbManager, container.deviceProfiles)
    })
    val apps: AppsViewModel = viewModel(factory = factory { AppsViewModel(container.adbManager) })
    val shell: ShellViewModel = viewModel(factory = factory { ShellViewModel(container.adbManager) })
    val processes: ProcessesViewModel = viewModel(factory = factory { ProcessesViewModel(container.adbManager) })
    val logcat: LogcatViewModel = viewModel(factory = factory {
        LogcatViewModel(
            manager = container.adbManager,
            exporter = container.textExporter,
            shareStore = container.logcatShareFileStore,
            outputStore = LogcatOutputStore(container.safDocumentStore),
        )
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
    val appsState by apps.state.collectAsStateWithLifecycle()
    val processesState by processes.state.collectAsStateWithLifecycle()
    val logcatState by logcat.state.collectAsStateWithLifecycle()
    val quickActionState by overview.quickActionState.collectAsStateWithLifecycle()
    val overviewOutputLifecycle by overview.outputLifecycle.collectAsStateWithLifecycle()
    val connected = devicesState.connectionState is AdbConnectionState.Connected
    var pageHostState by remember { mutableStateOf(PageHostState()) }
    val pairingOverlayController = remember {
        RootPairingOverlayController { overlay ->
            pageHostState = pageHostState.copy(overlay = overlay)
        }
    }
    val navigationPolicy = remember { AppNavigationPolicy() }
    val disconnectedDestinationGate = remember { DisconnectedDestinationGate() }
    var controlledPageAvailability by remember {
        mutableStateOf<ControlledPageAvailability>(ControlledPageAvailability.Available)
    }
    val deliveryLockAggregator = remember { DeliveryLockAggregator() }
    val navigationLock = deliveryLockAggregator.aggregate(
        listOf(
            deliveryLockAggregator.fromFiles(filesState),
            appsDeliveryState(appsState),
            logcatDeliveryState(logcatState),
            overviewOutputDeliveryState(quickActionState, overviewOutputLifecycle),
        ),
    )
    var transitionGeneration by rememberSaveable { mutableStateOf(0L) }
    var pageLoadNavigationGate by remember { mutableStateOf(PageLoadNavigationGateState()) }
    var pageLoadGateNowMillis by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var observedSessionId by remember { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var handledNotificationPermissionGeneration by rememberSaveable { mutableStateOf(0L) }
    var pendingWirelessDebuggingSettingsLaunch by rememberSaveable { mutableStateOf(false) }
    val openPendingWirelessDebuggingSettings = {
        if (pendingWirelessDebuggingSettingsLaunch) {
            pendingWirelessDebuggingSettingsLaunch = false
            context.openWirelessDebuggingSettings()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val available = granted && context.notificationsEnabled()
        container.localPairingBridge.onNotificationPermissionResult(
            granted = available,
            deviceUnlocked = context.isDeviceUnlocked(),
        )
        devices.onLocalNotificationPermissionResult(available)
        openPendingWirelessDebuggingSettings()
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
            openPendingWirelessDebuggingSettings()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openWirelessDebuggingSettings = {
        if (
            devicesState.notificationPermissionRequestGeneration >
            handledNotificationPermissionGeneration
        ) {
            pendingWirelessDebuggingSettingsLaunch = true
        } else {
            context.openWirelessDebuggingSettings()
        }
    }

    val performNavigation: (MainDestination, NavigationInput) -> Unit = { requested, input ->
        val availability = disconnectedDestinationGate.evaluate(
            target = requested,
            input = input,
            connected = connected,
        )
        controlledPageAvailability = availability
        val acceptedTarget = disconnectedDestinationGate.acceptedTarget(
            availability = availability,
            requested = requested,
        )
        when (
            val decision = navigationPolicy.resolveRequest(
                current = pageHostState.current,
                target = acceptedTarget,
                input = input,
                navigationLock = navigationLock,
            )
        ) {
            is NavigationDecision.Accepted -> {
                transitionGeneration += 1L
                pageHostState = pageHostState.copy(
                    pending = decision.target,
                    transition = PageTransition.FADING_OUT,
                )
                showSettings = false
            }
            is NavigationDecision.Rejected -> Unit
        }
    }

    val currentPageLoading = when (pageHostState.current) {
        MainDestination.APPLICATIONS -> appsState.isPageLoading
        MainDestination.PROCESSES -> processesState.isLoading
        else -> false
    }
    val cancelCurrentPageLoading = {
        when (pageLoadNavigationGate.owner) {
            MainDestination.APPLICATIONS -> apps.cancelPageLoading()
            MainDestination.PROCESSES -> processes.cancel()
            else -> Unit
        }
    }
    val requestNavigation: (MainDestination, NavigationInput) -> Unit = { requested, input ->
        if (navigationLock != null ||
            requested == pageHostState.current ||
            pageHostState.transition != PageTransition.IDLE
        ) {
            performNavigation(requested, input)
        } else {
            val transition = PageLoadNavigationGate.onRequest(
                state = pageLoadNavigationGate,
                current = pageHostState.current,
                target = requested,
                currentPageLoading = currentPageLoading,
                nowMillis = SystemClock.elapsedRealtime(),
            )
            pageLoadNavigationGate = transition.state
            if (transition.effect == PageLoadNavigationEffect.NAVIGATE) {
                performNavigation(checkNotNull(transition.target), input)
            }
        }
    }

    LaunchedEffect(pageLoadNavigationGate.phase, currentPageLoading) {
        while (pageLoadNavigationGate.phase != PageLoadNavigationPhase.IDLE) {
            delay(100L)
            val now = SystemClock.elapsedRealtime()
            pageLoadGateNowMillis = now
            val transition = PageLoadNavigationGate.onTick(
                state = pageLoadNavigationGate,
                currentPageLoading = currentPageLoading,
                nowMillis = now,
            )
            pageLoadNavigationGate = transition.state
            when (transition.effect) {
                PageLoadNavigationEffect.NONE -> Unit
                PageLoadNavigationEffect.CANCEL_CURRENT_LOAD -> cancelCurrentPageLoading()
                PageLoadNavigationEffect.NAVIGATE -> {
                    performNavigation(
                        checkNotNull(transition.target),
                        NavigationInput.BOTTOM_BAR,
                    )
                }
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) {
            controlledPageAvailability = ControlledPageAvailability.Available
        }
        val accepted = destinationAfterConnectionChange(pageHostState.current, connected)
        if (accepted != pageHostState.current) {
            pageHostState = pageHostState.copy(current = accepted, pending = null)
            controlledPageAvailability = ControlledPageAvailability.Available
        }
    }

    LaunchedEffect(navigationLock) {
        if (pageHostState.navigationLock != navigationLock) {
            pageHostState = pageHostState.copy(navigationLock = navigationLock)
        }
    }

    LaunchedEffect(pageHostState.pending, pageHostState.transition) {
        when (pageHostState.transition) {
            PageTransition.FADING_OUT -> {
                val target = pageHostState.pending ?: return@LaunchedEffect
                delay(PAGE_FADE_MILLIS.toLong())
                dispatchPageHidden(
                    destination = pageHostState.current,
                    files = files,
                    processes = processes,
                    shell = shell,
                    logcat = logcat,
                    transitionGeneration = transitionGeneration,
                )
                pageHostState = pageHostState.copy(
                    current = target,
                    pending = null,
                    transition = PageTransition.FADING_IN,
                )
                dispatchPageVisible(
                    destination = target,
                    files = files,
                    processes = processes,
                    shell = shell,
                    logcat = logcat,
                    transitionGeneration = transitionGeneration,
                    deviceRequestAllowed = connected ||
                        target == MainDestination.CONNECTION,
                )
            }
            PageTransition.FADING_IN -> {
                delay(PAGE_FADE_MILLIS.toLong())
                navigationPolicy.transitionSettled(pageHostState.current)
                pageHostState = pageHostState.copy(transition = PageTransition.IDLE)
            }
            PageTransition.IDLE -> Unit
        }
    }

    LaunchedEffect(hostForeground) {
        pageHostState = pageHostState.copy(foreground = hostForeground)
        pairingOverlayController.onHostForegroundChanged(
            isForeground = hostForeground,
            preserveForWirelessSettings = devicesState.keepLocalPairingWhileOpeningSettings,
        )
        if (!hostForeground) {
            overview.onHostStopped(
                isChangingConfigurations = (context as? Activity)?.isChangingConfigurations == true,
            )
        }
        onHostForegroundChanged(
            foreground = hostForeground,
            current = pageHostState.current,
            files = files,
            processes = processes,
            shell = shell,
            logcat = logcat,
        )
    }

    val currentSessionId =
        (devicesState.connectionState as? AdbConnectionState.Connected)?.sessionId
    LaunchedEffect(currentSessionId) {
        pairingOverlayController.onSessionChanged(currentSessionId ?: DISCONNECTED_PAIRING_OWNER)
        if (observedSessionId != currentSessionId) {
            onSessionChanged(
                previousSessionId = observedSessionId,
                currentSessionId = currentSessionId,
                shell = shell,
                logcat = logcat,
            )
            observedSessionId = currentSessionId
        }
    }

    LaunchedEffect(
        devicesState.showPairing,
        devicesState.keepLocalPairingWhileOpeningSettings,
        currentSessionId,
        hostForeground,
    ) {
        if (!hostForeground) {
            pairingOverlayController.onHostForegroundChanged(
                isForeground = false,
                preserveForWirelessSettings = devicesState.keepLocalPairingWhileOpeningSettings,
            )
        } else if (devicesState.showPairing) {
            pairingOverlayController.show(
                attemptId = ROOT_PAIRING_ATTEMPT_ID,
                ownerSessionId = currentSessionId ?: DISCONNECTED_PAIRING_OWNER,
                cancelAndClear = devices::closePairing,
            )
        } else {
            pairingOverlayController.dismiss()
        }
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

    V1LanguageBoundary(language = language) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .v1HorizontalNavigationGesture(
                enabled = !showSettings,
                current = pageHostState.current,
                navigationLock = navigationLock,
                policy = navigationPolicy,
                onAccepted = { target ->
                    requestNavigation(target, NavigationInput.GESTURE)
                },
            ),
    ) {
        if (maxWidth >= 700.dp) {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        Modifier.width(SheenDimensions.expandedPaneWidth).fillMaxHeight(),
                    ) {
                        PrimaryNavigation(
                            selected = pageHostState.current,
                            connected = connected,
                            navigationLocked = navigationLock != null,
                            language = language,
                            onDestination = { requested ->
                                if (navigationLock == null &&
                                    isMenuEnabled(requested.requiresConnection, connected)
                                ) {
                                    requestNavigation(requested, NavigationInput.BOTTOM_BAR)
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
                    destination = pageHostState.current,
                    devicesState = devicesState,
                    devices = devices,
                    onMenu = { scope.launch { drawer.open() } },
                    compact = false,
                    connected = connected,
                    navigationLocked = navigationLock != null,
                    language = language,
                    onDestination = { requested ->
                        if (navigationLock == null &&
                            isMenuEnabled(requested.requiresConnection, connected)
                        ) {
                            requestNavigation(requested, NavigationInput.BOTTOM_BAR)
                        }
                    },
                    fileTaskSummary = {
                        FileTaskSummaryBar(
                            summary = filesState.taskSummary,
                            showViewAction = pageHostState.current != MainDestination.FILES,
                            onView = {
                                requestNavigation(MainDestination.FILES, NavigationInput.BOTTOM_BAR)
                            },
                            onCancel = files::cancelActiveTask,
                            language = language,
                        )
                    },
                    ) {
                        V1AnimatedDestinationContent(
                            destination = pageHostState.current,
                            transition = pageHostState.transition,
                            devices = devices,
                            overview = overview,
                            files = files,
                            apps = apps,
                            shell = shell,
                            processes = processes,
                            logcat = logcat,
                            settings = settings,
                            onOpenWirelessDebuggingSettings = openWirelessDebuggingSettings,
                            onOverviewExportRequested = onOverviewExportRequested,
                            connected = connected,
                            showSettings = showSettings,
                            language = language,
                            availability = controlledPageAvailability,
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
                    destination = pageHostState.current,
                    devicesState = devicesState,
                    devices = devices,
                    onMenu = { scope.launch { drawer.open() } },
                    compact = true,
                    connected = connected,
                    navigationLocked = navigationLock != null,
                    language = language,
                    onDestination = { requested ->
                        if (navigationLock == null &&
                            isMenuEnabled(requested.requiresConnection, connected)
                        ) {
                            requestNavigation(requested, NavigationInput.BOTTOM_BAR)
                        }
                    },
                    fileTaskSummary = {
                        FileTaskSummaryBar(
                            summary = filesState.taskSummary,
                            showViewAction = pageHostState.current != MainDestination.FILES,
                            onView = {
                                requestNavigation(MainDestination.FILES, NavigationInput.BOTTOM_BAR)
                            },
                            onCancel = files::cancelActiveTask,
                            language = language,
                        )
                    },
                ) {
                    V1AnimatedDestinationContent(
                        destination = pageHostState.current,
                        transition = pageHostState.transition,
                        devices = devices,
                        overview = overview,
                        files = files,
                        apps = apps,
                        shell = shell,
                        processes = processes,
                        logcat = logcat,
                        settings = settings,
                        onOpenWirelessDebuggingSettings = openWirelessDebuggingSettings,
                        onOverviewExportRequested = onOverviewExportRequested,
                        connected = connected,
                        showSettings = showSettings,
                        language = language,
                        availability = controlledPageAvailability,
                    )
                }
            }
        }
    }
    if (pageLoadNavigationGate.phase != PageLoadNavigationPhase.IDLE) {
        val waiting = pageLoadNavigationGate.phase == PageLoadNavigationPhase.WAITING
        val remainingSeconds = (
            (
                PAGE_LOAD_WAIT_TIMEOUT_MILLIS -
                    (pageLoadGateNowMillis -
                        (pageLoadNavigationGate.waitStartedAtMillis ?: pageLoadGateNowMillis))
                ).coerceAtLeast(0L) + 999L
            ) / 1_000L
        PageLoadNavigationOverlay(
            waiting = waiting,
            remainingSeconds = remainingSeconds,
            language = language,
            onCancel = {
                val transition = PageLoadNavigationGate.cancelAndRecover(
                    pageLoadNavigationGate,
                    SystemClock.elapsedRealtime(),
                )
                pageLoadNavigationGate = transition.state
                cancelCurrentPageLoading()
            },
        )
    }
    if (pageHostState.overlay is RootOverlayState.Pairing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f))
                .clickable(onClick = pairingOverlayController::onOutsideTap),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SheenDimensions.screenPadding)
                    .clickable(onClick = {}),
            ) {
                DevicesPairingOverlayRoute(
                    viewModel = devices,
                    onOpenWirelessDebuggingSettings = openWirelessDebuggingSettings,
                    language = language,
                )
            }
        }
    }
    BackHandler {
        when (
            resolveBackPriority(
                pageHostState = pageHostState.copy(navigationLock = navigationLock),
                hasRootOverlay = showSettings || showAbout,
            )
        ) {
            BackPriority.ROOT_OVERLAY -> {
                showSettings = false
                showAbout = false
                if (!pairingOverlayController.onBackPressed()) {
                    pageHostState = pageHostState.copy(overlay = null)
                }
            }
            BackPriority.OPERATION_CONFIRMATION ->
                pageHostState = pageHostState.copy(overlay = null)
            BackPriority.LONG_TASK -> {
                val lock = navigationLock ?: return@BackHandler
                pageHostState = pageHostState.copy(
                    overlay = RootOverlayState.LongTaskBackConfirmation(lock.taskId),
                )
            }
            BackPriority.EXIT_APPLICATION -> (context as? Activity)?.finish()
        }
    }

    val longTaskBackConfirmation =
        pageHostState.overlay as? RootOverlayState.LongTaskBackConfirmation
    if (longTaskBackConfirmation != null) {
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = {
                pageHostState = pageHostState.copy(overlay = null)
            },
            title = {
                val taskLabel = deliveryKindLabel(
                    language,
                    navigationLock?.kind ?: DeliveryKind.FILE_DOWNLOAD,
                )
                Text(
                    AppStrings.resolve(
                        language,
                        AppStrings.ref(
                            AppStringKey.LONG_TASK_BACK_TITLE,
                            TypedTextArgument(
                                name = "task",
                                value = taskLabel,
                                type = TextArgumentType.TEXT,
                            ),
                        ),
                    ),
                )
            },
            text = {
                val taskLabel = deliveryKindLabel(
                    language,
                    navigationLock?.kind ?: DeliveryKind.FILE_DOWNLOAD,
                )
                Text(
                    AppStrings.resolve(
                        language,
                        AppStrings.ref(
                            AppStringKey.NAVIGATION_DISABLED_REASON,
                            TypedTextArgument(
                                name = "task",
                                value = taskLabel,
                                type = TextArgumentType.TEXT,
                            ),
                        ),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val activeLock = navigationLock
                            if (activeLock != null &&
                                awaitTaskCleanup(
                                    files = files,
                                    logcat = logcat,
                                    lock = activeLock,
                                )
                            ) {
                                (context as? Activity)?.finish()
                            } else {
                                pageHostState = pageHostState.copy(overlay = null)
                            }
                        }
                    },
                ) {
                    Text(
                        AppStrings.resolve(
                            language,
                            AppStrings.ref(AppStringKey.CANCEL_TASK_AND_LEAVE),
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pageHostState = pageHostState.copy(overlay = null) },
                ) {
                    Text(
                        AppStrings.resolve(
                            language,
                            AppStrings.ref(AppStringKey.CONTINUE_TASK),
                        ),
                    )
                }
            },
        )
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
                TextButton(onClick = { showAbout = false }) {
                    Text(
                        if (language == UiLanguage.ZH_CN) "关闭" else "Close",
                    )
                }
            },
        )
    }
    }
}

@Composable
private fun PageLoadNavigationOverlay(
    waiting: Boolean,
    remainingSeconds: Long,
    language: UiLanguage,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.scrim.copy(
                    alpha = SheenTonalLayers.framelessOverlayDimAlpha,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                strokeWidth = 3.dp,
            )
            Text(
                text = AppStrings.resolve(
                    language,
                    AppStrings.ref(
                        if (waiting) {
                            AppStringKey.PAGE_LOADING_WAIT
                        } else {
                            AppStringKey.PAGE_LOADING_RECOVERING
                        },
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (waiting) {
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onCancel) {
                    Text(
                        AppStrings.resolve(
                            language,
                            AppStrings.ref(AppStringKey.PAGE_LOADING_CANCEL),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryNavigation(
    selected: MainDestination,
    connected: Boolean,
    navigationLocked: Boolean,
    language: UiLanguage,
    onDestination: (MainDestination) -> Unit,
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Sheen ADB 助手",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(12.dp),
        )
        MainDestination.entries.forEach { destination ->
            val enabled = !navigationLocked &&
                isMenuEnabled(destination.requiresConnection, connected)
            NavigationDrawerItem(
                label = { Text(destination.localizedLabel(language)) },
                selected = selected == destination,
                onClick = {
                    if (!navigationLocked) {
                        if (isMenuEnabled(destination.requiresConnection, connected)) {
                            onDestination(destination)
                        }
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
        DrawerHeader(onClose, language)
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
private fun DrawerHeader(onClose: () -> Unit, language: UiLanguage) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.quietOutlineAlpha,
                ),
            )
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
        Text(
            V01Strings.text(language, V01StringKey.APP_NAME),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(SheenDimensions.minimumTouchTarget)) {
            Icon(
                SheenIcons.Close,
                contentDescription = V01Strings.text(language, V01StringKey.CLOSE_MENU),
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
    destination: MainDestination,
    devicesState: DevicesUiState,
    devices: DevicesViewModel,
    onMenu: (() -> Unit)?,
    compact: Boolean,
    connected: Boolean,
    navigationLocked: Boolean,
    language: UiLanguage,
    onDestination: (MainDestination) -> Unit,
    fileTaskSummary: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            SheenConnectionTopBar(
                state = devicesState,
                actions = devices,
                onMenu = onMenu,
                language = language,
            )
        },
        bottomBar = {
            if (compact) {
                SheenBottomNavigation(
                    selected = destination,
                    connected = connected,
                    navigationLocked = navigationLocked,
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
private fun V1AnimatedDestinationContent(
    destination: MainDestination,
    transition: PageTransition,
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
    availability: ControlledPageAvailability,
) {
    AnimatedVisibility(
        visible = transition != PageTransition.FADING_OUT,
        enter = fadeIn(animationSpec = tween(PAGE_FADE_MILLIS)),
        exit = fadeOut(animationSpec = tween(PAGE_FADE_MILLIS)),
    ) {
        DestinationContent(
            destination = destination,
            devices = devices,
            overview = overview,
            files = files,
            apps = apps,
            shell = shell,
            processes = processes,
            logcat = logcat,
            settings = settings,
            onOpenWirelessDebuggingSettings = onOpenWirelessDebuggingSettings,
            onOverviewExportRequested = onOverviewExportRequested,
            connected = connected,
            showSettings = showSettings,
            language = language,
            availability = availability,
        )
    }
}

@Composable
private fun DestinationContent(
    destination: MainDestination,
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
    availability: ControlledPageAvailability,
) {
    if (showSettings) {
        SettingsRoute(viewModel = settings, language = language)
        return
    }
    if (destination.requiresConnection && !connected) {
        ConnectFirstPanel(language = language, availability = availability)
        return
    }
    when (destination) {
        MainDestination.CONNECTION -> if (connected) {
            OverviewRoute(
                viewModel = overview,
                language = language,
                onExportRequested = { artifact -> onOverviewExportRequested(artifact) },
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                DevicesRoute(devices, onOpenWirelessDebuggingSettings, language)
                val unavailable = availability as? ControlledPageAvailability.Unavailable
                if (unavailable != null) {
                    Text(
                        text = AppStrings.resolve(language, unavailable.guidance),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(SheenDimensions.commonGutter)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                SheenShapes.default,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        MainDestination.FILES -> FilesRoute(viewModel = files, language = language)
        MainDestination.APPLICATIONS -> AppsRoute(viewModel = apps, language = language)
        MainDestination.PROCESSES -> ProcessesRoute(viewModel = processes, language = language)
        MainDestination.SHELL -> ShellRoute(viewModel = shell, language = language)
        MainDestination.LOGCAT -> LogcatRoute(viewModel = logcat, language = language)
    }
}

@Composable
private fun FilesRoute(
    viewModel: FilesViewModel,
    language: UiLanguage,
) {
    check(LocalV1UiLanguage.current == language)
    FeatureFilesRoute(viewModel = viewModel, language = language)
}

@Composable
private fun AppsRoute(
    viewModel: AppsViewModel,
    language: UiLanguage,
) {
    check(LocalV1UiLanguage.current == language)
    FeatureAppsRoute(viewModel = viewModel, language = language)
}

@Composable
private fun ProcessesRoute(
    viewModel: ProcessesViewModel,
    language: UiLanguage,
) {
    check(LocalV1UiLanguage.current == language)
    FeatureProcessesRoute(viewModel = viewModel, language = language)
}

@Composable
private fun ShellRoute(
    viewModel: ShellViewModel,
    language: UiLanguage,
) {
    check(LocalV1UiLanguage.current == language)
    FeatureShellRoute(viewModel = viewModel, language = language)
}

@Composable
private fun LogcatRoute(
    viewModel: LogcatViewModel,
    language: UiLanguage,
) {
    check(LocalV1UiLanguage.current == language)
    FeatureLogcatRoute(viewModel = viewModel, language = language)
}

@Composable
private fun ConnectFirstPanel(
    language: UiLanguage,
    availability: ControlledPageAvailability,
) {
    val unavailable = availability as? ControlledPageAvailability.Unavailable
        ?: ControlledPageAvailability.Unavailable(
            requested = MainDestination.CONNECTION,
            input = NavigationInput.RESTORE,
            deviceRequestAllowed = false,
        )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(SheenDimensions.compactPageHorizontalMargin),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SheenDimensions.commonGutter),
        ) {
            Icon(
                imageVector = SheenIcons.LinkOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = AppStrings.resolve(language, unavailable.guidance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
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
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.subtleOutlineAlpha,
                ),
            )
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
                    .height(SheenDimensions.topBarVisualHeight - 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, SheenShapes.default)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = SheenTonalLayers.subtleOutlineAlpha,
                        ),
                        SheenShapes.default,
                    )
                    .padding(horizontal = 12.dp)
                    .semantics {
                        contentDescription = V01Strings.text(
                            language,
                            V01StringKey.CONNECTION_ENDPOINT_CONTENT_DESCRIPTION,
                        )
                    },
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
    selected: MainDestination,
    connected: Boolean,
    navigationLocked: Boolean,
    language: UiLanguage,
    onDestination: (MainDestination) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(SheenDimensions.bottomBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.quietOutlineAlpha,
                ),
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainDestination.entries.forEach { destination ->
            val enabled = !navigationLocked &&
                isMenuEnabled(destination.requiresConnection, connected)
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
private const val PAGE_FADE_MILLIS = 160
private const val TASK_CLEANUP_TIMEOUT_MILLIS = 5_000L
private const val ROOT_PAIRING_ATTEMPT_ID = "devices-root-pairing"
private const val DISCONNECTED_PAIRING_OWNER = "disconnected-pairing-owner"

private enum class BackPriority {
    ROOT_OVERLAY,
    OPERATION_CONFIRMATION,
    LONG_TASK,
    EXIT_APPLICATION,
}

private fun resolveBackPriority(
    pageHostState: PageHostState,
    hasRootOverlay: Boolean,
): BackPriority = when {
    pageHostState.overlay is RootOverlayState.LongTaskBackConfirmation ->
        BackPriority.LONG_TASK
    pageHostState.overlay is RootOverlayState.OperationConfirmation ->
        BackPriority.OPERATION_CONFIRMATION
    pageHostState.overlay is RootOverlayState.Pairing || hasRootOverlay ->
        BackPriority.ROOT_OVERLAY
    pageHostState.navigationLock != null -> BackPriority.LONG_TASK
    else -> BackPriority.EXIT_APPLICATION
}

private fun dispatchPageHidden(
    destination: MainDestination,
    files: FilesViewModel,
    processes: ProcessesViewModel,
    shell: ShellViewModel,
    logcat: LogcatViewModel,
    transitionGeneration: Long,
) {
    check(transitionGeneration >= 0L)
    when (destination) {
        MainDestination.FILES -> files.onPageVisible(false)
        MainDestination.PROCESSES -> processes.onPageVisible(false)
        MainDestination.SHELL -> shell.onPageVisible(false)
        MainDestination.LOGCAT -> logcat.onPageVisible(false)
        else -> Unit
    }
}

private fun dispatchPageVisible(
    destination: MainDestination,
    files: FilesViewModel,
    processes: ProcessesViewModel,
    shell: ShellViewModel,
    logcat: LogcatViewModel,
    transitionGeneration: Long,
    deviceRequestAllowed: Boolean,
) {
    check(transitionGeneration >= 0L)
    if (destination == MainDestination.FILES && deviceRequestAllowed) {
        files.onPageVisible(true)
    }
    if (destination == MainDestination.PROCESSES && deviceRequestAllowed) {
        processes.onPageVisible(true)
    }
    if (destination == MainDestination.SHELL && deviceRequestAllowed) {
        shell.onPageVisible(true)
    }
    if (destination == MainDestination.LOGCAT && deviceRequestAllowed) {
        logcat.onPageVisible(true)
    }
}

private fun onHostForegroundChanged(
    foreground: Boolean,
    current: MainDestination,
    files: FilesViewModel,
    processes: ProcessesViewModel,
    shell: ShellViewModel,
    logcat: LogcatViewModel,
) {
    processes.setForeground(foreground)
    shell.setForeground(foreground)
    logcat.setForeground(foreground)
    if (foreground) {
        if (current == MainDestination.FILES) files.onPageVisible(true)
        if (current == MainDestination.PROCESSES) processes.onPageVisible(true)
        if (current == MainDestination.SHELL) shell.onPageVisible(true)
        if (current == MainDestination.LOGCAT) logcat.onPageVisible(true)
        return
    }
    if (current == MainDestination.FILES) files.onPageVisible(false)
    if (current == MainDestination.LOGCAT) logcat.onPageVisible(false)
}

private fun onSessionChanged(
    previousSessionId: String?,
    currentSessionId: String?,
    shell: ShellViewModel,
    logcat: LogcatViewModel,
) {
    if (previousSessionId == currentSessionId) return
    shell.cancel()
    logcat.stop()
}

private fun appsDeliveryState(state: AppsUiState): DeliveryIoState? {
    val task = state.task?.takeIf { it.navigationLocked } ?: return null
    val phase = when (task.phase) {
        AppsTaskPhase.PickerOpen,
        AppsTaskPhase.Preparing,
        -> DeliveryPhase.PREPARING
        is AppsTaskPhase.Transferring -> DeliveryPhase.TRANSFERRING
        AppsTaskPhase.Writing -> DeliveryPhase.WRITING
        AppsTaskPhase.AwaitingCleanup -> DeliveryPhase.CLEANING
    }
    val progress = (task.phase as? AppsTaskPhase.Transferring)?.let {
        DeliveryProgress(completed = it.transferredBytes, total = null)
    }
    return DeliveryIoState(
        owner = MainDestination.APPLICATIONS,
        taskId = task.taskId,
        sessionId = task.expectedSessionId,
        kind = when (task.kind) {
            AppsTaskKind.APK_EXTRACTION -> DeliveryKind.APK_EXTRACTION
            AppsTaskKind.APK_INSTALLATION -> DeliveryKind.APK_INSTALLATION
        },
        phase = phase,
        resourceState = if (task.phase == AppsTaskPhase.AwaitingCleanup) {
            DeliveryResourceState.UNCERTAIN
        } else {
            DeliveryResourceState.OWNED
        },
        progress = progress,
        cancelAvailable = true,
        cleanupConfirmed = false,
    )
}

private fun logcatDeliveryState(state: LogcatUiState): DeliveryIoState? {
    if (state.saveStatus != LogcatSaveStatus.WRITING) return null
    val taskId = state.saveTaskId ?: return null
    val sessionId = state.sessionId ?: return null
    return DeliveryIoState(
        owner = MainDestination.LOGCAT,
        taskId = taskId,
        sessionId = sessionId,
        kind = DeliveryKind.LOGCAT_SAVE,
        phase = DeliveryPhase.WRITING,
        resourceState = DeliveryResourceState.OWNED,
        cancelAvailable = true,
        cleanupConfirmed = false,
    )
}

private fun overviewOutputDeliveryState(
    state: QuickActionUiState,
    lifecycle: QuickActionOutputLifecycle,
): DeliveryIoState? {
    if (!lifecycle.navigationLocked) return null
    val owner = when (state) {
        QuickActionUiState.Idle -> return null
        is QuickActionUiState.Confirming -> state.kind to state.sessionId
        is QuickActionUiState.Running -> state.kind to state.sessionId
        is QuickActionUiState.AwaitingExport -> state.kind to state.sessionId
        is QuickActionUiState.Exporting -> state.kind to state.sessionId
        is QuickActionUiState.Succeeded -> state.kind to state.sessionId
        is QuickActionUiState.Cancelled -> state.kind to state.sessionId
        is QuickActionUiState.Failed -> state.kind to state.sessionId
        is QuickActionUiState.ResultUnknown -> state.kind to state.sessionId
    }
    val kind = when (owner.first) {
        QuickActionKind.SCREENSHOT -> DeliveryKind.SCREENSHOT_SAVE
        QuickActionKind.SCREEN_RECORD -> DeliveryKind.SCREEN_RECORDING_SAVE_OR_EXPORT
        QuickActionKind.REBOOT -> return null
    }
    val phase = when (lifecycle.phase) {
        QuickActionOutputPhase.WRITING -> DeliveryPhase.WRITING
        QuickActionOutputPhase.CANCELLING -> DeliveryPhase.CANCELLING
        QuickActionOutputPhase.CLEANING -> DeliveryPhase.CLEANING
        QuickActionOutputPhase.AWAITING_DESTINATION,
        QuickActionOutputPhase.COMPLETE,
        -> return null
    }
    return DeliveryIoState(
        owner = MainDestination.CONNECTION,
        taskId = "overview-output:${owner.first.name}:${owner.second}",
        sessionId = owner.second,
        kind = kind,
        phase = phase,
        resourceState = if (lifecycle.resourceUncertain) {
            DeliveryResourceState.UNCERTAIN
        } else if (phase in setOf(DeliveryPhase.CANCELLING, DeliveryPhase.CLEANING)) {
            DeliveryResourceState.RELEASING
        } else {
            DeliveryResourceState.OWNED
        },
        cancelAvailable = phase == DeliveryPhase.WRITING,
        cleanupConfirmed = lifecycle.cleanupConfirmed,
    )
}

private suspend fun awaitTaskCleanup(
    files: FilesViewModel,
    logcat: LogcatViewModel,
    lock: NavigationLock,
): Boolean = when (lock.owner) {
    MainDestination.FILES -> {
        if (files.state.value.activeTask?.taskId != lock.taskId) return false
        files.cancelActiveTask()
        withTimeoutOrNull(TASK_CLEANUP_TIMEOUT_MILLIS) {
            val state = files.state.first { current ->
                val task = current.activeTask
                task == null || task.taskId != lock.taskId || task.status.isTerminal
            }
            val task = state.activeTask
            task == null ||
                task.taskId != lock.taskId ||
                task.status !is FileTaskStatus.CleanupFailed
        } ?: false
    }
    MainDestination.LOGCAT -> {
        if (logcat.state.value.saveTaskId != lock.taskId) return false
        logcat.cancelSave()
        withTimeoutOrNull(TASK_CLEANUP_TIMEOUT_MILLIS) {
            logcat.state.first { current ->
                current.saveTaskId != lock.taskId &&
                    current.saveStatus != LogcatSaveStatus.WRITING
            }
            true
        } ?: false
    }
    else -> false
}

private val MainDestination.requiresConnection: Boolean
    get() = this != MainDestination.CONNECTION

private fun Modifier.v1HorizontalNavigationGesture(
    enabled: Boolean,
    current: MainDestination,
    navigationLock: NavigationLock?,
    policy: AppNavigationPolicy,
    onAccepted: (MainDestination) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(current, navigationLock) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            var horizontalIntent = false
            var childConsumedHorizontal = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed && !horizontalIntent) {
                    childConsumedHorizontal = true
                }
                total += change.position - change.previousPosition
                if (!horizontalIntent &&
                    kotlin.math.abs(total.x) > viewConfiguration.touchSlop &&
                    kotlin.math.abs(total.x) >
                    kotlin.math.abs(total.y) * HORIZONTAL_DOMINANCE_RATIO
                ) {
                    horizontalIntent = true
                }
                if (change.changedToUpIgnoreConsumed()) {
                    val decision = policy.resolveGesture(
                        current = current,
                        deltaXDp = total.x / density,
                        deltaYDp = total.y / density,
                        velocityXDpPerSecond = 0f,
                        navigationLock = navigationLock,
                        childConsumedHorizontal = childConsumedHorizontal,
                    )
                    if (decision is NavigationDecision.Accepted) {
                        onAccepted(decision.target)
                    }
                    break
                }
                if (horizontalIntent && !childConsumedHorizontal) {
                    change.consume()
                }
            }
        }
    }
}

private fun MainDestination.navigationIcon(): ImageVector = when (this) {
    MainDestination.CONNECTION -> SheenIcons.Remote
    MainDestination.FILES -> SheenIcons.Folder
    MainDestination.APPLICATIONS -> SheenIcons.Apps
    MainDestination.PROCESSES -> SheenIcons.Processes
    MainDestination.SHELL -> SheenIcons.Terminal
    MainDestination.LOGCAT -> SheenIcons.BugReport
}

internal fun isMenuEnabled(requiresConnection: Boolean, connected: Boolean): Boolean =
    connected || !requiresConnection

internal fun destinationAfterConnectionChange(
    current: MainDestination,
    connected: Boolean,
): MainDestination =
    if (!connected && current.requiresConnection) MainDestination.CONNECTION else current

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

private fun MainDestination.localizedLabel(language: UiLanguage): String =
    V01Strings.text(
        language,
        when (this) {
            MainDestination.CONNECTION -> V01StringKey.NAV_CONNECT
            MainDestination.FILES -> V01StringKey.NAV_FILES
            MainDestination.APPLICATIONS -> V01StringKey.NAV_APPS
            MainDestination.PROCESSES -> V01StringKey.NAV_PROCESSES
            MainDestination.SHELL -> V01StringKey.NAV_TERMINAL
            MainDestination.LOGCAT -> V01StringKey.NAV_LOGS
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

private fun Context.openWirelessDebuggingSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
