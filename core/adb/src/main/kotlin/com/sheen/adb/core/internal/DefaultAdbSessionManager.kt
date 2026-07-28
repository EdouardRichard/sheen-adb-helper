package com.sheen.adb.core.internal

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbCaptureSink
import com.sheen.adb.core.AdbDiagnosticEvent
import com.sheen.adb.core.AdbDiagnosticOutcome
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbExclusiveOperationKind
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ApkComponentTransferReceipt
import com.sheen.adb.core.ApkExtractionHandle
import com.sheen.adb.core.ApkExtractionRequest
import com.sheen.adb.core.ApkInstallMode
import com.sheen.adb.core.ApkInstallRequest
import com.sheen.adb.core.ApkInstallResult
import com.sheen.adb.core.ApkInstallStage
import com.sheen.adb.core.ApplicationField
import com.sheen.adb.core.ApplicationClassification
import com.sheen.adb.core.ApplicationMetadataStatus
import com.sheen.adb.core.ApplicationMetadataUpdate
import com.sheen.adb.core.ApplicationMutationResult
import com.sheen.adb.core.ApplicationSnapshot
import com.sheen.adb.core.ApplicationUninstallPreparation
import com.sheen.adb.core.ApplicationUninstallRequest
import com.sheen.adb.core.ApplicationUninstallResult
import com.sheen.adb.core.ApplicationUninstallStage
import com.sheen.adb.core.AndroidUidIdentity
import com.sheen.adb.core.DiagnosticRedactor
import com.sheen.adb.core.DeviceOverview
import com.sheen.adb.core.DynamicDeviceMetrics
import com.sheen.adb.core.DisconnectionReason
import com.sheen.adb.core.ExclusiveAdbOperationLease
import com.sheen.adb.core.CaptureFormat
import com.sheen.adb.core.CaptureMetadata
import com.sheen.adb.core.CaptureSinkResult
import com.sheen.adb.core.FileTransferProgress
import com.sheen.adb.core.LogcatConfig
import com.sheen.adb.core.LogcatLine
import com.sheen.adb.core.LocalPairingController
import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingEndpointHandle
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.ProcessSnapshot
import com.sheen.adb.core.ProcessAnalysisSnapshot
import com.sheen.adb.core.ProcessApplicationAssociation
import com.sheen.adb.core.ProcessAssociationUnknownReason
import com.sheen.adb.core.ProcessRecordAssociation
import com.sheen.adb.core.ProcessSnapshotEntry
import com.sheen.adb.core.ProcessIdentity
import com.sheen.adb.core.ProcessTerminationRequest
import com.sheen.adb.core.ProcessTerminationResult
import com.sheen.adb.core.ProcessTerminationOutcome
import com.sheen.adb.core.ProcessTerminationScope
import com.sheen.adb.core.QuickActionCapabilities
import com.sheen.adb.core.QuickActionCapability
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.core.QuickActionProgress
import com.sheen.adb.core.QuickActionProgressPhase
import com.sheen.adb.core.QuickActionResult
import com.sheen.adb.core.RebootRequest
import com.sheen.adb.core.ScreenshotCaptureRequest
import com.sheen.adb.core.ScreenRecordRequest
import com.sheen.adb.core.QrPairingMaterial
import com.sheen.adb.core.RemoteApplication
import com.sheen.adb.core.RemoteApplicationEnabledState
import com.sheen.adb.core.RemoteDirectorySnapshot
import com.sheen.adb.core.RemoteDirectorySource
import com.sheen.adb.core.RemoteFileKind
import com.sheen.adb.core.RemoteLinkResolution
import com.sheen.adb.core.RemotePathEntry
import com.sheen.adb.core.RemoteFileTransferReceipt
import com.sheen.adb.core.RemoteFileConflictPolicy
import com.sheen.adb.core.RemoteUploadCommitReceipt
import com.sheen.adb.core.RemoteUploadPlan
import com.sheen.adb.core.ShellResult
import com.sheen.adb.core.ShellOutputMode
import com.sheen.adb.core.InteractiveShellCloseReason
import com.sheen.adb.core.InteractiveShellResult
import com.sheen.adb.core.InteractiveShellSession
import com.sheen.adb.core.TerminalInput
import com.sheen.adb.core.TerminalOutputEvent
import com.sheen.adb.core.TerminalOutputKind
import com.sheen.adb.core.StructuredLogcatKind
import com.sheen.adb.core.StructuredLogcatLevel
import com.sheen.adb.core.StructuredLogcatRecord
import com.sheen.adb.core.StructuredLogcatTimestamp
import com.sheen.adb.core.WirelessAddress
import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessDiscoveryMode
import com.sheen.adb.core.WirelessDiscoverySource
import com.sheen.adb.core.WirelessDiscoverySourceFactory
import com.sheen.adb.core.WirelessDiscoverySourceFailure
import com.sheen.adb.core.WirelessDiscoverySourceObserver
import com.sheen.adb.core.WirelessDiscoverySourceRequest
import com.sheen.adb.core.WirelessDiscoverySourceStartResult
import com.sheen.adb.core.WirelessDiscoveryState
import com.sheen.adb.core.WirelessDiscoveryTarget
import com.sheen.adb.core.WirelessServiceObservation
import com.sheen.adb.core.WirelessServiceStatus
import com.sheen.adb.core.WirelessServiceType
import com.sheen.adb.core.VerifiedWirelessDeviceId
import com.sheen.adb.core.internal.applications.ApplicationMetadataLoadStatus
import com.sheen.adb.core.internal.applications.ApplicationMetadataLoader
import com.sheen.adb.core.internal.applications.ApplicationMetadataParseResult
import com.sheen.adb.core.internal.applications.ApplicationMetadataParser
import com.sheen.adb.core.internal.applications.ApplicationPackageProtocol
import com.sheen.adb.core.internal.applications.BoundedRemoteApkReader
import com.sheen.adb.core.internal.applications.RemoteApkReader
import com.sheen.adb.core.internal.diagnostics.ProcessAssociation
import com.sheen.adb.core.internal.diagnostics.StructuredLogcatParser
import com.sheen.adb.core.internal.discovery.WirelessDiscoveryReducer
import com.sheen.adb.core.internal.pairing.MonotonicClock
import com.sheen.adb.core.internal.pairing.LocalPairingCoordinator
import com.sheen.adb.core.internal.pairing.LocalPairingNotificationPolicy
import com.sheen.adb.core.internal.pairing.QrPairingCoordinator
import com.sheen.adb.core.internal.processes.ProcessApplicationAssociationResolver
import com.sheen.adb.core.internal.processes.ProcessSnapshotParser
import com.sheen.adb.core.internal.processes.ProcessTerminationPolicy
import java.io.Closeable
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DefaultAdbSessionManager(
    private val clientFactory: AdbProtocolClientFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transferNoProgressTimeout: Duration = 30.seconds,
    private val transferCancellationGrace: Duration = 3.seconds,
    private val wirelessDiscoverySourceFactory: WirelessDiscoverySourceFactory? = null,
    private val lanDiscoveryWindow: Duration = 10.seconds,
    private val pairingIdentityFingerprint: (AdbEndpoint) -> ByteArray? = { null },
    private val connectedIdentityFingerprint: (AdbProtocolClient) -> ByteArray? = { null },
    private val metadataReaderFactory: ((AdbProtocolClient, () -> Boolean) -> RemoteApkReader)? = null,
    private val metadataParser: (ByteArray, List<String>) -> ApplicationMetadataParseResult =
        ApplicationMetadataParser()::parse,
    private val metadataBatchTimeout: Duration = 10.seconds,
    private val metadataCancellationGrace: Duration = 3.seconds,
    private val qrPairingCoordinator: QrPairingCoordinator = QrPairingCoordinator(
        clock = MonotonicClock { System.nanoTime() / 1_000_000L },
        secureRandom = SecureRandom(),
    ),
    private val localPairingClock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000L },
    private val quickActionProtocol: QuickActionProtocol = DefaultQuickActionProtocol(ioDispatcher),
    private val sessionHealthInterval: Duration = 5.seconds,
    private val sessionHealthTimeout: Duration = 3.seconds,
    private val sessionHealthFailureThreshold: Int = 2,
) : AdbSessionManager, Closeable {
    private data class ActiveSession(
        val id: String,
        val endpoint: AdbEndpoint,
        val client: AdbProtocolClient,
    )

    private data class LanPairingAssociation(
        val observation: WirelessServiceObservation,
        val verifiedDeviceId: VerifiedWirelessDeviceId?,
    )

    private data class ActiveExclusiveOperation(
        val token: String,
        val sessionId: String,
        val kind: AdbExclusiveOperationKind,
        val active: AtomicBoolean = AtomicBoolean(true),
    )

    private data class ActiveScreenRecording(
        val sessionId: String,
        val stopRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private data class PendingApplicationUninstall(
        val preparation: ApplicationUninstallPreparation,
    )

    private class ActiveInteractiveShell(
        val expectedSessionId: String,
        val streamGeneration: Long,
        val protocol: ProtocolInteractiveShell,
        val events: Channel<TerminalOutputEvent>,
    ) {
        val closed = AtomicBoolean(false)
        var readerJob: Job? = null
    }

    private sealed interface WirelessDiscoverySignal {
        data class Event(val value: WirelessDiscoveryEvent) : WirelessDiscoverySignal

        data class Terminal(val error: AdbError) : WirelessDiscoverySignal
    }

    private sealed interface WirelessDiscoveryCallResult<out T> {
        data class Value<T>(val value: T) : WirelessDiscoveryCallResult<T>

        data class Cancelled(val cancellation: CancellationException) : WirelessDiscoveryCallResult<Nothing>

        data object Failed : WirelessDiscoveryCallResult<Nothing>
    }

    private class ActiveWirelessDiscovery(
        val generation: Long,
        val ownerSessionId: String?,
        val mode: WirelessDiscoveryMode,
    ) {
        val signals = Channel<WirelessDiscoverySignal>(Channel.UNLIMITED)
        val terminalCompletion = CompletableDeferred<AdbError>()

        private val stateLock = Any()
        private val sourceLock = Any()
        private var terminal = false
        private var terminalError: AdbError? = null
        private var terminalPublished = false
        private var source: WirelessDiscoverySource? = null
        private var sourceAttachmentComplete = false
        private var sourceCloseState = SourceCloseState.OPEN

        fun isTerminal(): Boolean = synchronized(stateLock) { terminal }

        fun terminalError(): AdbError? = synchronized(stateLock) { terminalError }

        fun markTerminal(error: AdbError): Boolean = synchronized(stateLock) {
            if (terminal) return@synchronized false
            terminal = true
            terminalError = error
            true
        }

        fun markRetired(): Boolean = synchronized(stateLock) {
            if (terminal) return@synchronized false
            terminal = true
            true
        }

        fun attachSource(value: WirelessDiscoverySource) = synchronized(sourceLock) {
            check(!sourceAttachmentComplete) { "Wireless discovery source attachment already completed." }
            source = value
            sourceAttachmentComplete = true
        }

        fun markSourceUnavailable() = synchronized(sourceLock) {
            if (!sourceAttachmentComplete) sourceAttachmentComplete = true
        }

        fun closeSourceIfReady(): Boolean {
            var closeNow: WirelessDiscoverySource? = null
            synchronized(sourceLock) {
                if (!sourceAttachmentComplete) return false
                when (sourceCloseState) {
                    SourceCloseState.CLOSING -> return false
                    SourceCloseState.CLOSED -> return true
                    SourceCloseState.OPEN -> {
                        sourceCloseState = SourceCloseState.CLOSING
                        closeNow = source
                    }
                }
            }
            closeNow?.let { runCatching { it.close() } }
            synchronized(sourceLock) {
                sourceCloseState = SourceCloseState.CLOSED
            }
            return true
        }

        fun publishTerminal(error: AdbError) {
            val shouldPublish = synchronized(stateLock) {
                if (terminalPublished) false else {
                    terminalPublished = true
                    true
                }
            }
            if (!shouldPublish) return
            terminalCompletion.complete(error)
            signals.trySend(WirelessDiscoverySignal.Terminal(error))
            signals.close()
        }

        fun finishRetirement() {
            signals.close()
        }

        private enum class SourceCloseState {
            OPEN,
            CLOSING,
            CLOSED,
        }
    }

    private val mutex = Mutex()
    private val applicationMutex = Mutex()
    private val exclusiveOperationLock = Any()
    private val wirelessDiscoveryLock = Any()
    private val closed = AtomicBoolean(false)
    private val diagnosticSequence = AtomicLong(0)
    private val wirelessDiscoveryGeneration = AtomicLong(0)
    private val processAnalysisGeneration = AtomicLong(0)
    private val applicationGeneration = AtomicLong(0)
    private val processRefreshRequestedGeneration = AtomicLong(0)
    private val interactiveShellGeneration = AtomicLong(0)
    private val interactiveShellMutex = Mutex()
    private val processTerminationRequestLock = Any()
    private val consumedProcessTerminationRequestIds = linkedSetOf<String>()
    @Volatile
    private var active: ActiveSession? = null
    @Volatile
    private var activeQrAttemptId: PairingAttemptId? = null
    private var activeExclusiveOperation: ActiveExclusiveOperation? = null
    private var activeScreenRecording: ActiveScreenRecording? = null
    private var activeWirelessDiscovery: ActiveWirelessDiscovery? = null
    private var latestLanDiscoveryState: WirelessDiscoveryState? = null
    private var latestPairingDiscoveryState: WirelessDiscoveryState? = null
    private val lanPairingAssociations = linkedMapOf<PairingAttemptId, LanPairingAssociation>()
    private val wirelessIdentitySalt = ByteArray(32).also(SecureRandom()::nextBytes)
    private var applicationSnapshot: ApplicationSnapshot? = null
    private var pendingApplicationUninstall: PendingApplicationUninstall? = null
    @Volatile
    private var activeInteractiveShell: ActiveInteractiveShell? = null
    private val applicationMetadataMutex = Mutex()
    private var applicationMetadataLoader: ApplicationMetadataLoader? = null
    private var applicationMetadataLoaderSessionId: String? = null
    private var applicationMetadataClient: AdbProtocolClient? = null
    @Volatile
    private var latestProcessAnalysis: ProcessAnalysisSnapshot? = null
    private val mutableState = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected())
    override val connectionState: StateFlow<AdbConnectionState> = mutableState.asStateFlow()
    private val mutableDiagnosticEvents = MutableStateFlow<List<AdbDiagnosticEvent>>(emptyList())
    override val diagnosticEvents: StateFlow<List<AdbDiagnosticEvent>> = mutableDiagnosticEvents.asStateFlow()
    private val localPairingScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val connectionProbeScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val localPairingDiscoveryLock = Any()
    private var localPairingDiscoveryJob: Job? = null
    private var sessionHealthJob: Job? = null
    private val localPairingCoordinator = LocalPairingCoordinator(
        clock = localPairingClock,
        notificationPolicy = LocalPairingNotificationPolicy(),
        pairObservation = ::pairLocalPairingObservation,
        stopDiscovery = ::stopLocalPairingDiscovery,
        startGuard = {
            when {
                closed.get() -> AdbError.PairingUnsupported
                active != null -> AdbError.PairingSessionConflict
                else -> null
            }
        },
    )
    override val localPairingController: LocalPairingController = object : LocalPairingController {
        override val state get() = localPairingCoordinator.state

        override fun start(
            attemptId: PairingAttemptId,
            windowId: LocalPairingWindowId,
        ): AdbOperationResult<LocalPairingWindow> = localPairingCoordinator.start(attemptId, windowId).also {
            if (it is AdbOperationResult.Success) startLocalPairingDiscovery()
        }

        override fun updateNotification(
            deviceUnlocked: Boolean,
            capability: LocalPairingNotificationCapability,
        ): LocalPairingNotificationDecision =
            localPairingCoordinator.updateNotification(deviceUnlocked, capability)

        override suspend fun submit(
            windowId: LocalPairingWindowId,
            secret: PairingSecret,
        ): AdbOperationResult<Unit> = localPairingCoordinator.submit(windowId, secret)

        override fun cancel(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
            localPairingCoordinator.cancel(windowId)

        override fun onSystemTimeout(windowId: LocalPairingWindowId): AdbOperationResult<Unit> =
            localPairingCoordinator.onSystemTimeout(windowId)
    }

    override suspend fun acquireExclusiveOperation(
        kind: AdbExclusiveOperationKind,
        expectedSessionId: String,
    ): AdbOperationResult<ExclusiveAdbOperationLease> = mutex.withLock {
        val session = active
        if (session == null || session.id != expectedSessionId) {
            return@withLock operationFailure(AdbError.SessionInvalid(kind), session?.endpoint, null)
        }

        val (acquired, conflictingKind) = synchronized(exclusiveOperationLock) {
            val current = activeExclusiveOperation?.takeIf { it.active.get() }
            if (current != null) {
                null to current.kind
            } else {
                ActiveExclusiveOperation(
                    token = UUID.randomUUID().toString(),
                    sessionId = session.id,
                    kind = kind,
                ).also { activeExclusiveOperation = it } to null
            }
        }
        if (acquired == null) {
            return@withLock operationFailure(
                AdbError.OperationConflict(kind, checkNotNull(conflictingKind)),
                session.endpoint,
                null,
            )
        }
        AdbOperationResult.Success(ManagerExclusiveOperationLease(acquired))
    }

    override suspend fun quickActionCapabilities(
        expectedSessionId: String,
    ): QuickActionResult<QuickActionCapabilities> = QuickActionCapabilityResolver(
        currentSessionId = { active?.id },
        screenshotProbe = { sessionId ->
            probeQuickActionCapability(sessionId, "command -v screencap >/dev/null 2>&1")
        },
        screenRecordProbe = { sessionId ->
            probeQuickActionCapability(sessionId, "command -v screenrecord >/dev/null 2>&1")
        },
        rebootProbe = { sessionId ->
            probeQuickActionCapability(
                sessionId,
                "cmd power help >/dev/null 2>&1 || command -v reboot >/dev/null 2>&1",
            )
        },
    ).resolve(expectedSessionId)

    override suspend fun captureScreenshot(
        request: ScreenshotCaptureRequest,
        sink: AdbCaptureSink,
        progress: (QuickActionProgress) -> Unit,
    ): QuickActionResult<CaptureMetadata> {
        val session = active?.takeIf { it.id == request.expectedSessionId }
            ?: return QuickActionResult.StaleSession(request.expectedSessionId)
        val lease = when (
            val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.QUICK_ACTION,
                request.expectedSessionId,
            )
        ) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return QuickActionResult.Failure(acquired.error)
            AdbOperationResult.Cancelled -> return QuickActionResult.Cancelled
        }
        val started = TimeSource.Monotonic.markNow()
        var completed = false
        try {
            val protocolResult = try {
                withTimeout(request.timeout) {
                    quickActionProtocol.captureScreenshot(session.client, sink) { bytes ->
                        progress(
                            QuickActionProgress(
                                expectedSessionId = request.expectedSessionId,
                                kind = QuickActionKind.SCREENSHOT,
                                phase = QuickActionProgressPhase.CAPTURING,
                                bytesWritten = bytes,
                                elapsed = started.elapsedNow(),
                                maxBytes = null,
                                maxDuration = request.timeout,
                            ),
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                QuickActionProtocolCaptureResult.TimedOut
            }
            if (active?.id != request.expectedSessionId) {
                return QuickActionResult.StaleSession(request.expectedSessionId)
            }
            return when (protocolResult) {
                is QuickActionProtocolCaptureResult.Completed -> {
                    if (protocolResult.bytesWritten <= 0L ||
                        sink.finish() is CaptureSinkResult.Rejected
                    ) {
                        QuickActionResult.Failure(AdbError.Unknown(AdbOperationStage.QUICK_ACTION))
                    } else {
                        completed = true
                        QuickActionResult.Success(
                            CaptureMetadata(
                                expectedSessionId = request.expectedSessionId,
                                kind = QuickActionKind.SCREENSHOT,
                                bytesWritten = protocolResult.bytesWritten,
                                elapsed = started.elapsedNow(),
                                format = CaptureFormat.PNG,
                            ),
                        )
                    }
                }
                QuickActionProtocolCaptureResult.TimedOut ->
                    QuickActionResult.Failure(AdbError.Timeout(AdbOperationStage.QUICK_ACTION))
                QuickActionProtocolCaptureResult.EmptyOutput,
                is QuickActionProtocolCaptureResult.InvalidOutput,
                -> QuickActionResult.Failure(AdbError.Unknown(AdbOperationStage.QUICK_ACTION))
            }
        } catch (_: CancellationException) {
            return QuickActionResult.Cancelled
        } finally {
            withContext(NonCancellable) {
                if (!completed) sink.abort()
                lease.release()
            }
        }
    }

    override suspend fun recordScreen(
        request: ScreenRecordRequest,
        sink: AdbCaptureSink,
        progress: (QuickActionProgress) -> Unit,
    ): QuickActionResult<CaptureMetadata> {
        val session = active?.takeIf { it.id == request.expectedSessionId }
            ?: return QuickActionResult.StaleSession(request.expectedSessionId)
        val lease = when (
            val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.QUICK_ACTION,
                request.expectedSessionId,
            )
        ) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return QuickActionResult.Failure(acquired.error)
            AdbOperationResult.Cancelled -> return QuickActionResult.Cancelled
        }
        val started = TimeSource.Monotonic.markNow()
        val recording = ActiveScreenRecording(request.expectedSessionId)
        synchronized(exclusiveOperationLock) {
            activeScreenRecording = recording
        }
        var completed = false
        try {
            val protocolResult = try {
                withTimeout(request.timeout) {
                    quickActionProtocol.recordScreen(
                        client = session.client,
                        sink = sink,
                        maxBytes = request.maxBytes,
                        maxDuration = request.maxDuration,
                        stopRequested = recording.stopRequested::get,
                    ) { bytes ->
                        progress(
                            QuickActionProgress(
                                expectedSessionId = request.expectedSessionId,
                                kind = QuickActionKind.SCREEN_RECORD,
                                phase = if (recording.stopRequested.get()) {
                                    QuickActionProgressPhase.STOPPING
                                } else {
                                    QuickActionProgressPhase.CAPTURING
                                },
                                bytesWritten = bytes,
                                elapsed = started.elapsedNow(),
                                maxBytes = request.maxBytes,
                                maxDuration = request.maxDuration,
                            ),
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                QuickActionProtocolCaptureResult.TimedOut
            }
            if (active?.id != request.expectedSessionId) {
                return QuickActionResult.StaleSession(request.expectedSessionId)
            }
            return when (protocolResult) {
                is QuickActionProtocolCaptureResult.Completed -> {
                    if (protocolResult.bytesWritten <= 0L ||
                        protocolResult.bytesWritten > request.maxBytes ||
                        sink.finish() is CaptureSinkResult.Rejected
                    ) {
                        QuickActionResult.Failure(AdbError.Unknown(AdbOperationStage.QUICK_ACTION))
                    } else {
                        completed = true
                        QuickActionResult.Success(
                            CaptureMetadata(
                                expectedSessionId = request.expectedSessionId,
                                kind = QuickActionKind.SCREEN_RECORD,
                                bytesWritten = protocolResult.bytesWritten,
                                elapsed = started.elapsedNow(),
                                format = CaptureFormat.MP4,
                            ),
                        )
                    }
                }
                QuickActionProtocolCaptureResult.TimedOut ->
                    QuickActionResult.Failure(AdbError.Timeout(AdbOperationStage.QUICK_ACTION))
                QuickActionProtocolCaptureResult.EmptyOutput,
                is QuickActionProtocolCaptureResult.InvalidOutput,
                -> QuickActionResult.Failure(AdbError.Unknown(AdbOperationStage.QUICK_ACTION))
            }
        } catch (_: CancellationException) {
            return QuickActionResult.Cancelled
        } finally {
            withContext(NonCancellable) {
                synchronized(exclusiveOperationLock) {
                    if (activeScreenRecording === recording) activeScreenRecording = null
                }
                if (!completed) sink.abort()
                lease.release()
            }
        }
    }

    override suspend fun stopScreenRecord(
        expectedSessionId: String,
    ): QuickActionResult<Unit> {
        if (active?.id != expectedSessionId) {
            return QuickActionResult.StaleSession(expectedSessionId)
        }
        val recording = synchronized(exclusiveOperationLock) {
            activeScreenRecording?.takeIf { it.sessionId == expectedSessionId }
        } ?: return QuickActionResult.Cancelled
        recording.stopRequested.set(true)
        return QuickActionResult.Success(Unit)
    }

    override suspend fun reboot(
        request: RebootRequest,
    ): QuickActionResult<Unit> {
        val session = active?.takeIf { it.id == request.expectedSessionId }
            ?: return QuickActionResult.StaleSession(request.expectedSessionId)
        val capabilities = when (val resolved = quickActionCapabilities(request.expectedSessionId)) {
            is QuickActionResult.Success -> resolved.value
            is QuickActionResult.StaleSession -> return resolved
            is QuickActionResult.Failure -> return resolved
            QuickActionResult.Cancelled -> return QuickActionResult.Cancelled
            is QuickActionResult.ResultUnknown -> return resolved
        }
        when (capabilities.reboot) {
            QuickActionCapability.Supported -> Unit
            is QuickActionCapability.Unsupported,
            is QuickActionCapability.PolicyRejected,
            is QuickActionCapability.ProbeFailed,
            QuickActionCapability.Unknown,
            -> return QuickActionResult.Failure(AdbError.DeviceRejected(AdbOperationStage.QUICK_ACTION))
        }
        val lease = when (
            val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.QUICK_ACTION,
                request.expectedSessionId,
            )
        ) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return QuickActionResult.Failure(acquired.error)
            AdbOperationResult.Cancelled -> return QuickActionResult.Cancelled
        }
        try {
            val protocolResult = try {
                withTimeout(request.timeout) { quickActionProtocol.reboot(session.client) }
            } catch (_: TimeoutCancellationException) {
                return QuickActionResult.Failure(AdbError.Timeout(AdbOperationStage.QUICK_ACTION))
            }
            if (active?.id != request.expectedSessionId &&
                protocolResult != QuickActionProtocolRebootResult.DisconnectedAfterDispatch
            ) {
                return QuickActionResult.StaleSession(request.expectedSessionId)
            }
            return when (protocolResult) {
                QuickActionProtocolRebootResult.Accepted -> QuickActionResult.Success(Unit)
                QuickActionProtocolRebootResult.Rejected ->
                    QuickActionResult.Failure(AdbError.DeviceRejected(AdbOperationStage.QUICK_ACTION))
                QuickActionProtocolRebootResult.DisconnectedAfterDispatch -> {
                    closeSession(session)
                    mutableState.value = AdbConnectionState.Disconnected()
                    QuickActionResult.ResultUnknown(request.expectedSessionId)
                }
            }
        } catch (_: CancellationException) {
            return QuickActionResult.Cancelled
        } finally {
            withContext(NonCancellable) { lease.release() }
        }
    }

    private suspend fun probeQuickActionCapability(
        expectedSessionId: String,
        command: String,
    ): QuickActionCapability = withContext(ioDispatcher) {
        val session = active?.takeIf { it.id == expectedSessionId }
            ?: return@withContext QuickActionCapability.Unknown
        try {
            when (session.client.execute(command).exitCode) {
                0 -> QuickActionCapability.Supported
                126 -> QuickActionCapability.PolicyRejected("device-policy")
                127 -> QuickActionCapability.Unsupported("command-unavailable")
                else -> QuickActionCapability.ProbeFailed("probe-failed")
            }
        } catch (_: Exception) {
            QuickActionCapability.ProbeFailed("probe-failed")
        }
    }

    override fun observeWirelessServices(
        mode: WirelessDiscoveryMode,
        timeout: Duration,
    ): Flow<AdbOperationResult<WirelessDiscoveryState>> = flow {
        val discovery = claimWirelessDiscovery(mode)
        if (discovery == null) {
            val error = if (closed.get()) AdbError.DiscoveryManagerClosed else AdbError.DiscoveryConflict
            emit(operationFailure(error, null, null))
            return@flow
        }

        var state = WirelessDiscoveryState(generation = discovery.generation)
        val reducer = WirelessDiscoveryReducer()
        updateLatestDiscoveryState(mode, state)
        var pendingSourceCancellation: CancellationException? = null
        try {
            try {
                val effectiveTimeout = if (mode == WirelessDiscoveryMode.LAN_FOREGROUND) {
                    minOf(timeout, lanDiscoveryWindow)
                } else {
                    timeout
                }
                withTimeout(effectiveTimeout) {
                    val factory = wirelessDiscoverySourceFactory
                    if (discovery.isTerminal()) {
                        discovery.markSourceUnavailable()
                        completePendingWirelessDiscovery(discovery)
                    } else if (factory == null) {
                        discovery.markSourceUnavailable()
                        terminateWirelessDiscovery(discovery, AdbError.DiscoveryPlatformFailure)
                    } else {
                        val observer = object : WirelessDiscoverySourceObserver {
                            override fun onEvent(event: WirelessDiscoveryEvent) {
                                publishWirelessDiscoveryEvent(discovery, event)
                            }

                            override fun onFailure(failure: WirelessDiscoverySourceFailure) {
                                terminateWirelessDiscovery(discovery, discoveryError(failure))
                            }
                        }
                        val sourceResult = try {
                            runInterruptible(ioDispatcher) {
                                captureWirelessDiscoveryCall {
                                    factory.create(observer).also(discovery::attachSource)
                                }
                            }
                        } catch (error: CancellationException) {
                            discovery.markSourceUnavailable()
                            completePendingWirelessDiscovery(discovery)
                            throw error
                        }
                        val source = when (sourceResult) {
                            is WirelessDiscoveryCallResult.Value -> sourceResult.value
                            is WirelessDiscoveryCallResult.Cancelled -> {
                                discovery.markSourceUnavailable()
                                pendingSourceCancellation = sourceResult.cancellation
                                return@withTimeout
                            }
                            WirelessDiscoveryCallResult.Failed -> {
                                discovery.markSourceUnavailable()
                                terminateWirelessDiscovery(discovery, AdbError.DiscoveryPlatformFailure)
                                null
                            }
                        }
                        if (source != null) {
                            completePendingWirelessDiscovery(discovery)
                            if (!discovery.isTerminal()) {
                                val startCallResult = try {
                                    runInterruptible(ioDispatcher) {
                                        captureWirelessDiscoveryCall {
                                            source.start(
                                                WirelessDiscoverySourceRequest(
                                                    generation = discovery.generation,
                                                    mode = mode,
                                                ),
                                            )
                                        }
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                }
                                val startResult = when (startCallResult) {
                                    is WirelessDiscoveryCallResult.Value -> startCallResult.value
                                    is WirelessDiscoveryCallResult.Cancelled -> {
                                        pendingSourceCancellation = startCallResult.cancellation
                                        return@withTimeout
                                    }
                                    WirelessDiscoveryCallResult.Failed -> {
                                        terminateWirelessDiscovery(discovery, AdbError.DiscoveryPlatformFailure)
                                        null
                                    }
                                }
                                when (startResult) {
                                    WirelessDiscoverySourceStartResult.Started -> Unit
                                    is WirelessDiscoverySourceStartResult.Rejected -> {
                                        terminateWirelessDiscovery(discovery, discoveryError(startResult.failure))
                                    }
                                    null -> Unit
                                }
                            }
                        }
                    }

                    if (discovery.isTerminal()) {
                        val error = discovery.terminalCompletion.await()
                        emit(operationFailure(error, null, null))
                        return@withTimeout
                    }

                    appendDiagnostic(
                        AdbOperationStage.DISCOVERY,
                        AdbDiagnosticOutcome.STARTED,
                        "ADB_DISCOVERY_STARTED",
                        null,
                    )
                    emit(AdbOperationResult.Success(state))
                    for (signal in discovery.signals) {
                        when (signal) {
                            is WirelessDiscoverySignal.Event -> {
                                state = reducer.reduce(state, signal.value)
                                updateLatestDiscoveryState(mode, state)
                                emit(AdbOperationResult.Success(state))
                            }

                            is WirelessDiscoverySignal.Terminal -> {
                                emit(operationFailure(signal.error, null, null))
                                return@withTimeout
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                terminateWirelessDiscovery(discovery, AdbError.DiscoveryTimeout)
                val error = discovery.terminalCompletion.await()
                emit(operationFailure(error, null, null))
            }
            pendingSourceCancellation?.let { throw it }
        } catch (error: CancellationException) {
            appendDiagnostic(
                AdbOperationStage.DISCOVERY,
                AdbDiagnosticOutcome.CANCELLED,
                "ADB_DISCOVERY_CANCELLED",
                null,
            )
            throw error
        } finally {
            discovery.markSourceUnavailable()
            completePendingWirelessDiscovery(discovery)
            retireWirelessDiscovery(discovery)
            appendDiagnostic(
                AdbOperationStage.DISCOVERY,
                AdbDiagnosticOutcome.RESOURCE_CLOSED,
                "ADB_DISCOVERY_SOURCE_CLOSED",
                null,
            )
        }
    }

    override suspend fun pairDiscoveredService(
        target: WirelessDiscoveryTarget,
        attemptId: PairingAttemptId,
        secret: PairingSecret,
        timeout: Duration,
    ): AdbOperationResult<WirelessDiscoveryState> {
        val selection = currentLanSelection(target, WirelessServiceType.PAIRING)
        if (selection == null || synchronized(wirelessDiscoveryLock) { attemptId in lanPairingAssociations }) {
            secret.clear()
            return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        }
        val endpointHandle = PairingEndpointHandle.resolved(
            token = "${target.generation}:${target.observationId.hashCode()}",
            reference = selection,
        )
        val endpoint = endpointHandle.toPairingEndpoint()
        if (endpoint == null) {
            secret.clear()
            return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        }
        return when (
            val result = pairWithSecret(
                pairingEndpoint = endpoint,
                pairingSecret = secret,
                method = PairingMethod.SIX_DIGIT_CODE,
                timeout = timeout,
            )
        ) {
            is AdbOperationResult.Success -> {
                val verifiedDeviceId = verifiedIdentity(
                    runCatching { pairingIdentityFingerprint(endpoint) }.getOrNull(),
                )
                synchronized(wirelessDiscoveryLock) {
                    lanPairingAssociations[attemptId] = LanPairingAssociation(selection, verifiedDeviceId)
                    trimLanPairingAssociations()
                }
                AdbOperationResult.Success(
                    updateLanIdentityState(selection, verifiedDeviceId),
                )
            }
            is AdbOperationResult.Failure -> result
            AdbOperationResult.Cancelled -> AdbOperationResult.Cancelled
        }
    }

    override suspend fun connectDiscoveredService(
        target: WirelessDiscoveryTarget,
        expectedPairingAttemptId: PairingAttemptId?,
        timeout: Duration,
    ): AdbOperationResult<WirelessDiscoveryState> {
        val selection = currentLanSelection(target, WirelessServiceType.CONNECT)
            ?: return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        if (active != null) {
            return operationFailure(AdbError.PairingSessionConflict, null, null)
        }
        val endpoint = selection.toPairingEndpoint()
            ?: return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        val pairingAssociation = expectedPairingAttemptId?.let { attemptId ->
            synchronized(wirelessDiscoveryLock) { lanPairingAssociations[attemptId] }
        }
        return when (val result = connect(endpoint, timeout)) {
            is AdbOperationResult.Success -> {
                val connectedIdentity = active?.client?.let { client ->
                    verifiedIdentity(runCatching { connectedIdentityFingerprint(client) }.getOrNull())
                }
                var updated = latestLanStateOr(target.generation)
                if (pairingAssociation != null) {
                    updated = WirelessDiscoveryReducer().withVerifiedIdentity(
                        state = updated,
                        observation = pairingAssociation.observation,
                        verifiedDeviceId = pairingAssociation.verifiedDeviceId,
                    )
                }
                updated = WirelessDiscoveryReducer().withVerifiedIdentity(
                    state = updated,
                    observation = selection,
                    verifiedDeviceId = connectedIdentity,
                )
                updateLatestLanDiscoveryState(updated)
                AdbOperationResult.Success(updated)
            }
            is AdbOperationResult.Failure -> result
            AdbOperationResult.Cancelled -> AdbOperationResult.Cancelled
        }
    }

    override suspend fun connectLocalPairedDevice(
        pairingAttemptId: PairingAttemptId,
        timeout: Duration,
    ): AdbOperationResult<Unit> {
        val association = synchronized(wirelessDiscoveryLock) {
            lanPairingAssociations[pairingAttemptId]
        } ?: return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        val pairingAddresses = association.observation.addresses.toSet()
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < timeout) {
            currentCoroutineContext().ensureActive()
            val remaining = timeout - started.elapsedNow()
            var selected: WirelessServiceObservation? = null
            var terminalError: AdbError? = null
            var cancelled = false
            observeWirelessServices(
                mode = WirelessDiscoveryMode.LAN_FOREGROUND,
                timeout = remaining,
            ).firstOrNull { result ->
                when (result) {
                    is AdbOperationResult.Success -> {
                        selected = result.value.services
                            .asSequence()
                            .filter {
                                it.serviceType == WirelessServiceType.CONNECT &&
                                    it.status == WirelessServiceStatus.RESOLVED &&
                                    it.port != LEGACY_ADB_TCP_PORT &&
                                    it.addresses.any(pairingAddresses::contains)
                            }
                            .maxByOrNull(WirelessServiceObservation::lastSeenAt)
                        selected != null
                    }
                    is AdbOperationResult.Failure -> {
                        terminalError = result.error
                        true
                    }
                    AdbOperationResult.Cancelled -> {
                        cancelled = true
                        true
                    }
                }
            }
            if (cancelled) return AdbOperationResult.Cancelled
            val observation = selected
            if (observation != null) {
                val endpoint = observation.toPairingEndpoint()
                    ?: return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
                val connectTimeout = timeout - started.elapsedNow()
                if (connectTimeout <= Duration.ZERO) {
                    return operationFailure(AdbError.DiscoveryTimeout, null, null)
                }
                val connected = connect(endpoint, connectTimeout)
                if (connected !is AdbOperationResult.Success || association.verifiedDeviceId == null) {
                    return connected
                }
                val connectedIdentity = active?.client?.let { client ->
                    verifiedIdentity(runCatching { connectedIdentityFingerprint(client) }.getOrNull())
                }
                if (connectedIdentity == association.verifiedDeviceId) return connected
                disconnect()
                return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
            }
            val error = terminalError ?: AdbError.DiscoveryResolutionFailed
            if (!error.isRetryablePairedConnectDiscoveryFailure()) {
                return operationFailure(error, null, null)
            }
            val retryRemaining = timeout - started.elapsedNow()
            if (retryRemaining <= Duration.ZERO) break
            delay(minOf(PAIRED_CONNECT_DISCOVERY_RETRY_DELAY, retryRemaining))
        }
        return operationFailure(AdbError.DiscoveryTimeout, null, null)
    }

    override suspend fun loadRemoteDirectory(
        path: String?,
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<RemoteDirectorySnapshot> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId) {
            return operationFailure(AdbError.RemoteSessionInvalid, session?.endpoint, null)
        }
        return try {
            val (directory, listing) = if (path == null) {
                val userId = when (val user = currentUser(timeout)) {
                    is AdbOperationResult.Success -> user.value
                    else -> 0
                }
                resolveSharedStorage(session, userId, timeout)
            } else {
                if (!RemoteFileCapabilities.isValidAbsolutePath(path)) {
                    return operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
                }
                path to KadbRemoteFileProtocol.list(session.client, path, timeout)
            }
            try {
                RemoteFileCapabilities.requireDirectoryCapacity(listing.entries.size)
            } catch (_: RemoteDirectoryCapacityException) {
                return operationFailure(AdbError.RemoteDirectoryCapacityExceeded, session.endpoint, null)
            }
            val currentStat = runCatching {
                KadbRemoteFileProtocol.stat(session.client, directory, timeout)
            }.getOrNull()
            val entries = listing.entries.map { entry ->
                toRemotePathEntry(session, directory, entry, listing.version, currentStat, timeout)
            }.sortedWith(compareBy<RemotePathEntry>({ kindOrder(it.kind) }, { it.displayName.lowercase() }))
            if (mutex.withLock { active?.id } != expectedSessionId) {
                return operationFailure(AdbError.RemoteSessionInvalid, session.endpoint, null)
            }
            AdbOperationResult.Success(
                RemoteDirectorySnapshot(
                    sessionId = session.id,
                    directory = directory,
                    entries = entries,
                    sourceCapabilities = if (listing.version == ProtocolSyncVersion.V2) {
                        setOf(RemoteDirectorySource.LIST_V2, RemoteDirectorySource.STAT_V2)
                    } else {
                        setOf(RemoteDirectorySource.SYNC_V1_DEGRADED)
                    },
                    loadedAtMonotonicMillis = System.nanoTime() / 1_000_000,
                ),
            )
        } catch (error: TimeoutCancellationException) {
            operationFailure(AdbError.Timeout(AdbOperationStage.FILE_BROWSER), session.endpoint, error)
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            operationFailure(remoteFileError(error), session.endpoint, error)
        }
    }

    override suspend fun pullRemoteFile(
        remoteFile: RemotePathEntry,
        destination: OutputStream,
        expectedSessionId: String,
        progress: (FileTransferProgress) -> Unit,
        externalLease: ExclusiveAdbOperationLease?,
    ): AdbOperationResult<RemoteFileTransferReceipt> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
        if (!RemoteFileCapabilities.isValidAbsolutePath(remoteFile.absolutePath) || !remoteFile.selectable) {
            return operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
        }
        val (lease, ownsLease) = when (val acquired = obtainFileTransferLease(expectedSessionId, externalLease)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        val forcedSessionClose = AtomicBoolean(false)
        return try {
            val forcedClose: () -> Unit = {
                forcedSessionClose.set(true)
                runCatching { session.client.close() }
            }
            val (transferred, stable) = try {
                val receipt = KadbRemoteFileProtocol.receiveVerified(
                    client = session.client,
                    path = remoteFile.absolutePath,
                    destination = destination,
                    noProgressTimeout = transferNoProgressTimeout,
                    cancellationGrace = transferCancellationGrace,
                    onForcedSessionClose = forcedClose,
                ) { bytes ->
                    progress(FileTransferProgress(bytes, remoteFile.sizeBytes))
                }
                receipt.transferredBytes to (
                    receipt.before.sameTransferIdentity(receipt.after) &&
                        receipt.transferredBytes == receipt.before.size
                    )
            } catch (fallback: ProtocolReceiveDigestFallbackRequired) {
                val digestBefore = remoteDigest(session.client, remoteFile.absolutePath)
                    ?: return operationFailure(AdbError.RemoteIntegrityUnavailable, session.endpoint, fallback)
                val fallbackTransferred = KadbRemoteFileProtocol.receive(
                    client = session.client,
                    path = remoteFile.absolutePath,
                    destination = destination,
                    noProgressTimeout = transferNoProgressTimeout,
                    cancellationGrace = transferCancellationGrace,
                    onForcedSessionClose = forcedClose,
                ) { bytes ->
                    progress(FileTransferProgress(bytes, remoteFile.sizeBytes))
                }
                val after = KadbRemoteFileProtocol.stat(
                    session.client,
                    remoteFile.absolutePath,
                    FILE_PREPARE_TIMEOUT,
                )
                val digestAfter = remoteDigest(session.client, remoteFile.absolutePath)
                    ?: return operationFailure(AdbError.RemoteIntegrityUnavailable, session.endpoint, null)
                fallbackTransferred to (
                    digestBefore == digestAfter && fallbackTransferred == after.size
                    )
            }
            if (!stable) return operationFailure(AdbError.RemoteSourceChanged, session.endpoint, null)
            if (mutex.withLock { active?.id } != expectedSessionId || !lease.isActive) {
                return operationFailure(
                    AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                    session.endpoint,
                    null,
                )
            }
            AdbOperationResult.Success(RemoteFileTransferReceipt(session.id, transferred))
        } catch (error: ProtocolNoProgressTimeoutException) {
            operationFailure(AdbError.NoProgressTimeout, session.endpoint, error)
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            operationFailure(fileTransferError(error), session.endpoint, error)
        } finally {
            if (forcedSessionClose.get()) {
                withContext(NonCancellable) { invalidateForcedTransferSession(session) }
            }
            if (ownsLease) lease.release()
        }
    }

    override suspend fun pushRemoteFile(
        source: InputStream,
        sourceSize: Long?,
        stagedRemotePath: String,
        expectedSessionId: String,
        progress: (FileTransferProgress) -> Unit,
        externalLease: ExclusiveAdbOperationLease?,
    ): AdbOperationResult<RemoteFileTransferReceipt> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
        if (!RemoteFileCapabilities.isValidAbsolutePath(stagedRemotePath)) {
            return operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
        }
        val (lease, ownsLease) = when (val acquired = obtainFileTransferLease(expectedSessionId, externalLease)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        val forcedSessionClose = AtomicBoolean(false)
        return try {
            val transferred = KadbRemoteFileProtocol.send(
                client = session.client,
                path = stagedRemotePath,
                source = source,
                mode = DEFAULT_REMOTE_FILE_MODE,
                modifiedEpochMillis = System.currentTimeMillis(),
                noProgressTimeout = transferNoProgressTimeout,
                cancellationGrace = transferCancellationGrace,
                onForcedSessionClose = {
                    forcedSessionClose.set(true)
                    runCatching { session.client.close() }
                },
            ) { bytes -> progress(FileTransferProgress(bytes, sourceSize)) }
            val uploaded = KadbRemoteFileProtocol.stat(session.client, stagedRemotePath, FILE_PREPARE_TIMEOUT)
            if ((sourceSize != null && sourceSize != transferred) || uploaded.size != transferred) {
                return operationFailure(AdbError.RemoteSourceChanged, session.endpoint, null)
            }
            if (mutex.withLock { active?.id } != expectedSessionId || !lease.isActive) {
                return operationFailure(
                    AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                    session.endpoint,
                    null,
                )
            }
            AdbOperationResult.Success(RemoteFileTransferReceipt(session.id, transferred))
        } catch (error: ProtocolNoProgressTimeoutException) {
            operationFailure(AdbError.NoProgressTimeout, session.endpoint, error)
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            operationFailure(fileTransferError(error), session.endpoint, error)
        } finally {
            if (forcedSessionClose.get()) {
                withContext(NonCancellable) { invalidateForcedTransferSession(session) }
            }
            if (ownsLease) lease.release()
        }
    }

    override suspend fun prepareRemoteUpload(
        remoteDirectory: String,
        displayName: String,
        expectedSessionId: String,
    ): AdbOperationResult<RemoteUploadPlan> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
        return try {
            val finalPath = RemoteFileCapabilities.safeChild(remoteDirectory, displayName)
            val listing = KadbRemoteFileProtocol.list(session.client, remoteDirectory, FILE_PREPARE_TIMEOUT)
            val names = listing.entries.mapTo(mutableSetOf()) { it.name }
            val stagedName = generateSequence {
                ".sheen-${UUID.randomUUID().toString().replace("-", "").take(16)}.part"
            }.first { it !in names }
            AdbOperationResult.Success(
                RemoteUploadPlan(
                    sessionId = session.id,
                    directory = remoteDirectory,
                    requestedName = displayName,
                    stagedPath = RemoteFileCapabilities.safeChild(remoteDirectory, stagedName),
                    finalPath = finalPath,
                    conflictExists = displayName in names,
                ),
            )
        } catch (error: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (_: IllegalArgumentException) {
            operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
        } catch (error: Throwable) {
            operationFailure(remoteFileError(error), session.endpoint, error)
        }
    }

    override suspend fun commitRemoteUpload(
        plan: RemoteUploadPlan,
        conflictPolicy: RemoteFileConflictPolicy,
        expectedSessionId: String,
        externalLease: ExclusiveAdbOperationLease?,
    ): AdbOperationResult<RemoteUploadCommitReceipt> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId || plan.sessionId != expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
        if (!isValidUploadPlan(plan)) {
            return operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
        }
        val (lease, ownsLease) = when (val acquired = obtainFileTransferLease(expectedSessionId, externalLease)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        return try {
            val names = KadbRemoteFileProtocol.list(
                session.client,
                plan.directory,
                FILE_PREPARE_TIMEOUT,
            ).entries.mapTo(mutableSetOf()) { it.name }
            val targetExists = plan.requestedName in names
            if (targetExists && conflictPolicy == RemoteFileConflictPolicy.CANCEL) {
                return operationFailure(AdbError.RemoteConflict, session.endpoint, null)
            }
            val targetPath = when {
                targetExists && conflictPolicy == RemoteFileConflictPolicy.AUTO_RENAME -> {
                    val renamed = autoRenamedName(plan.requestedName, names)
                        ?: return operationFailure(AdbError.RemoteConflict, session.endpoint, null)
                    RemoteFileCapabilities.safeChild(plan.directory, renamed)
                }
                else -> plan.finalPath
            }
            val replaced = targetExists && conflictPolicy == RemoteFileConflictPolicy.OVERWRITE
            val committed = if (replaced) {
                commitRemoteOverwrite(session.client, plan, targetPath, names)
            } else {
                remoteMove(session.client, plan.stagedPath, targetPath)
            }
            if (!committed) return operationFailure(AdbError.RemoteCommitFailed, session.endpoint, null)
            if (mutex.withLock { active?.id } != expectedSessionId || !lease.isActive) {
                return operationFailure(
                    AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                    session.endpoint,
                    null,
                )
            }
            AdbOperationResult.Success(RemoteUploadCommitReceipt(session.id, targetPath, replaced))
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            operationFailure(AdbError.RemoteCommitFailed, session.endpoint, error)
        } finally {
            if (ownsLease) lease.release()
        }
    }

    override suspend fun cleanupRemoteStaging(
        stagedRemotePath: String,
        expectedSessionId: String,
        externalLease: ExclusiveAdbOperationLease?,
    ): AdbOperationResult<Unit> {
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
        if (!RemoteFileCapabilities.isValidAbsolutePath(stagedRemotePath) ||
            !stagedRemotePath.substringAfterLast('/').startsWith(".sheen-")
        ) {
            return operationFailure(AdbError.RemotePathInvalid, session.endpoint, null)
        }
        val leaseUse = when (val acquired = obtainFileTransferLease(expectedSessionId, externalLease)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        return try {
            if (remoteDelete(session.client, stagedRemotePath)) {
                AdbOperationResult.Success(Unit)
            } else {
                operationFailure(AdbError.RemoteCleanupFailed, session.endpoint, null)
            }
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            operationFailure(AdbError.RemoteCleanupFailed, session.endpoint, error)
        } finally {
            if (leaseUse.second) leaseUse.first.release()
        }
    }

    private suspend fun obtainFileTransferLease(
        expectedSessionId: String,
        externalLease: ExclusiveAdbOperationLease?,
    ): AdbOperationResult<Pair<ExclusiveAdbOperationLease, Boolean>> {
        if (externalLease == null) {
            return when (val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.FILE_TRANSFER,
                expectedSessionId,
            )) {
                is AdbOperationResult.Success -> AdbOperationResult.Success(acquired.value to true)
                is AdbOperationResult.Failure -> acquired
                AdbOperationResult.Cancelled -> AdbOperationResult.Cancelled
            }
        }
        val valid = externalLease.kind == AdbExclusiveOperationKind.FILE_TRANSFER &&
            externalLease.sessionId == expectedSessionId &&
            externalLease.isActive &&
            synchronized(exclusiveOperationLock) {
                val activeLease = activeExclusiveOperation
                activeLease?.token == externalLease.token && activeLease.sessionId == expectedSessionId
            }
        return if (valid) {
            AdbOperationResult.Success(externalLease to false)
        } else {
            val session = mutex.withLock { active }
            operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.FILE_TRANSFER),
                session?.endpoint,
                null,
            )
        }
    }

    private fun isValidUploadPlan(plan: RemoteUploadPlan): Boolean =
        RemoteFileCapabilities.isValidAbsolutePath(plan.directory) &&
            RemoteFileCapabilities.safeChild(plan.directory, plan.requestedName) == plan.finalPath &&
            plan.stagedPath.substringBeforeLast('/', "").ifEmpty { "/" } ==
            plan.directory.trimEnd('/').ifEmpty { "/" } &&
            plan.stagedPath.substringAfterLast('/').startsWith(".sheen-") &&
            plan.stagedPath.endsWith(".part")

    private fun autoRenamedName(requestedName: String, existing: Set<String>): String? {
        val dot = requestedName.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { requestedName.substring(0, it) } ?: requestedName
        val extension = dot?.let { requestedName.substring(it) }.orEmpty()
        return (1..MAX_AUTO_RENAME_ATTEMPTS)
            .asSequence()
            .map { "$stem ($it)$extension" }
            .firstOrNull { it !in existing }
    }

    private suspend fun commitRemoteOverwrite(
        client: AdbProtocolClient,
        plan: RemoteUploadPlan,
        targetPath: String,
        existing: Set<String>,
    ): Boolean {
        val backupName = generateSequence {
            ".sheen-${UUID.randomUUID().toString().replace("-", "").take(16)}.bak"
        }.first { it !in existing }
        val backupPath = RemoteFileCapabilities.safeChild(plan.directory, backupName)
        if (!remoteMove(client, targetPath, backupPath)) return false
        if (!remoteMove(client, plan.stagedPath, targetPath)) {
            remoteMove(client, backupPath, targetPath)
            return false
        }
        if (!remoteDelete(client, backupPath)) {
            remoteMove(client, backupPath, targetPath)
            return false
        }
        return true
    }

    private suspend fun remoteMove(client: AdbProtocolClient, source: String, target: String): Boolean =
        executeRemoteFileCommand(client, "mv ${shellQuote(source)} ${shellQuote(target)}").exitCode == 0

    private suspend fun remoteDelete(client: AdbProtocolClient, target: String): Boolean =
        executeRemoteFileCommand(client, "rm -f ${shellQuote(target)}").exitCode == 0

    private suspend fun executeRemoteFileCommand(
        client: AdbProtocolClient,
        command: String,
    ): ProtocolShellResponse = runInterruptible(ioDispatcher) { client.execute(command) }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun ProtocolRemoteStat.hasReliableTransferMetadata(): Boolean =
        size >= 0L && modifiedEpochSeconds > 0L

    private fun ProtocolRemoteStat.sameTransferIdentity(other: ProtocolRemoteStat): Boolean =
        size == other.size &&
            modifiedEpochSeconds == other.modifiedEpochSeconds &&
            (deviceId == null || other.deviceId == null || deviceId == other.deviceId) &&
            (inode == null || other.inode == null || inode == other.inode)

    private suspend fun remoteDigest(client: AdbProtocolClient, path: String): String? {
        val escaped = path.replace("'", "'\\''")
        val commands = listOf(
            "toybox sha256sum -- '$escaped'",
            "sha256sum -- '$escaped'",
        )
        val response = commands.firstNotNullOfOrNull { command ->
            executeRemoteFileCommand(client, command).takeIf { it.exitCode == 0 }
        } ?: return null
        return response.stdout.trim().substringBefore(' ').takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
    }

    private fun fileTransferError(error: Throwable): AdbError {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val messages = causes.mapNotNull { it.message?.lowercase() }
        return when {
            causes.any { it is ProtocolLocalSourceException } -> AdbError.LocalFileReadFailed
            causes.any { it is ProtocolLocalDestinationException } -> AdbError.LocalFileWriteFailed
            messages.any { "permission denied" in it || "access denied" in it } ->
                AdbError.RemoteFilePermissionDenied
            messages.any { "no such file" in it || "not found" in it } ->
                AdbError.RemoteFilePathNotFound
            causes.any { it is java.io.EOFException || it.javaClass.simpleName.contains("StreamClosed", true) } ->
                AdbError.RemoteFileStreamClosed
            else -> AdbExceptionMapper.map(error, AdbOperationStage.FILE_TRANSFER)
        }
    }

    private suspend fun resolveSharedStorage(
        session: ActiveSession,
        userId: Int,
        timeout: Duration,
    ): Pair<String, ProtocolDirectoryListing> {
        for (candidate in RemoteFileCapabilities.sharedStorageCandidates(userId)) {
            val listing = try {
                KadbRemoteFileProtocol.list(session.client, candidate, timeout)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            if (listing != null) return candidate to listing
        }
        throw IOException("No shared storage path")
    }

    private suspend fun toRemotePathEntry(
        session: ActiveSession,
        directory: String,
        entry: ProtocolRemoteEntry,
        version: ProtocolSyncVersion,
        currentDirectoryStat: ProtocolRemoteStat?,
        timeout: Duration,
    ): RemotePathEntry {
        val kind = fileKind(entry.mode)
        val child = RemoteFileCapabilities.safeChild(directory, entry.name)
        var resolution = if (kind == RemoteFileKind.SYMLINK) RemoteLinkResolution.UNSUPPORTED else RemoteLinkResolution.NOT_A_LINK
        var targetKind: RemoteFileKind? = null
        if (kind == RemoteFileKind.SYMLINK && version == ProtocolSyncVersion.V2) {
            try {
                val target = KadbRemoteFileProtocol.stat(session.client, child, timeout)
                targetKind = fileKind(target.mode)
                resolution = if (
                    currentDirectoryStat?.deviceId != null && currentDirectoryStat.inode != null &&
                    target.deviceId == currentDirectoryStat.deviceId && target.inode == currentDirectoryStat.inode
                ) RemoteLinkResolution.LOOP else RemoteLinkResolution.VERIFIED
            } catch (error: Throwable) {
                resolution = when {
                    error.message.orEmpty().contains("permission", ignoreCase = true) -> RemoteLinkResolution.PERMISSION_DENIED
                    else -> RemoteLinkResolution.MISSING
                }
            }
        }
        return RemotePathEntry(
            absolutePath = child,
            displayName = entry.name,
            kind = kind,
            sizeBytes = entry.size.takeIf { kind == RemoteFileKind.FILE },
            modifiedEpochSeconds = entry.modifiedEpochSeconds,
            mode = entry.mode,
            deviceId = entry.deviceId,
            inode = entry.inode,
            linkResolution = resolution,
            targetKind = targetKind,
        )
    }

    private fun fileKind(mode: Int): RemoteFileKind = when (mode and 0xF000) {
        0x4000 -> RemoteFileKind.DIRECTORY
        0x8000 -> RemoteFileKind.FILE
        0xA000 -> RemoteFileKind.SYMLINK
        else -> RemoteFileKind.OTHER
    }

    private fun kindOrder(kind: RemoteFileKind): Int = when (kind) {
        RemoteFileKind.DIRECTORY -> 0
        RemoteFileKind.SYMLINK -> 1
        RemoteFileKind.FILE -> 2
        RemoteFileKind.OTHER -> 3
    }

    private fun remoteFileError(error: Throwable): AdbError = when {
        error.message.orEmpty().contains("permission", ignoreCase = true) -> AdbError.RemotePermissionDenied
        error.message.orEmpty().contains("not found", ignoreCase = true) -> AdbError.RemotePathNotFound
        else -> AdbExceptionMapper.map(error, AdbOperationStage.FILE_BROWSER)
    }

    override suspend fun connect(endpoint: AdbEndpoint, timeout: Duration): AdbOperationResult<Unit> =
        mutex.withLock {
            if (closed.get()) return@withLock failure(AdbError.Unknown(AdbOperationStage.CONNECT), endpoint, null)
            localPairingCoordinator.onSessionChanged()
            cancelActiveQrPairing()
            terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
            runInterruptible(ioDispatcher) { closeActiveIfPresent() }
            appendDiagnostic(AdbOperationStage.CONNECT, AdbDiagnosticOutcome.STARTED, "ADB_CONNECT_STARTED", endpoint)
            mutableState.value = AdbConnectionState.Connecting(endpoint)
            var candidate: AdbProtocolClient? = null
            var adopted = false
            var awaitingAuthorization = false
            try {
                withTimeout(timeout) {
                    var ready = false
                    while (!ready) {
                        try {
                            val opened = openAndProbeCancellable(
                                endpoint = endpoint,
                                openClient = clientFactory::openForConnectionProbe,
                            )
                            val sessionReady = if (opened.probeClient.reusableForSession) {
                                opened
                            } else {
                                withContext(NonCancellable + ioDispatcher) {
                                    runCatching { opened.probeClient.client.close() }
                                }
                                openAndProbeCancellable(
                                    endpoint = endpoint,
                                    openClient = {
                                        AdbConnectionProbeClient(
                                            client = clientFactory.open(it),
                                            reusableForSession = true,
                                        )
                                    },
                                )
                            }
                            candidate = sessionReady.probeClient.client
                            val probe = sessionReady.response
                            if (probe.exitCode != 0) throw ProtocolProbeException()
                            ready = true
                        } catch (error: Throwable) {
                            if (!isLegacyAuthorization(error, endpoint)) throw error
                            withContext(NonCancellable + ioDispatcher) { runCatching { candidate?.close() } }
                            candidate = null
                            awaitingAuthorization = true
                            mutableState.value = AdbConnectionState.AwaitingAuthorization(endpoint)
                            appendDiagnostic(
                                AdbOperationStage.AUTHENTICATE,
                                AdbDiagnosticOutcome.STARTED,
                                "ADB_AWAITING_RSA_AUTHORIZATION",
                                endpoint,
                            )
                            delay(AUTHORIZATION_RETRY_MILLIS)
                        }
                    }
                }
                val session = ActiveSession(UUID.randomUUID().toString(), endpoint, checkNotNull(candidate))
                active = session
                adopted = true
                mutableState.value = AdbConnectionState.Connected(endpoint, session.id)
                startSessionHealthMonitor(session)
                appendDiagnostic(AdbOperationStage.CONNECT, AdbDiagnosticOutcome.SUCCEEDED, "ADB_CONNECT_SUCCEEDED", endpoint)
                AdbOperationResult.Success(Unit)
            } catch (error: TimeoutCancellationException) {
                if (awaitingAuthorization) {
                    failure(AdbError.DeviceRejected(AdbOperationStage.AUTHENTICATE), endpoint, error)
                } else {
                    failure(AdbError.Timeout(AdbOperationStage.CONNECT), endpoint, error)
                }
            } catch (error: CancellationException) {
                mutableState.value = AdbConnectionState.Disconnected(DisconnectionReason.CONNECT_CANCELLED)
                appendDiagnostic(AdbOperationStage.CONNECT, AdbDiagnosticOutcome.CANCELLED, "ADB_CONNECT_CANCELLED", endpoint)
                AdbOperationResult.Cancelled
            } catch (error: Throwable) {
                val mapped = AdbExceptionMapper.map(error, AdbOperationStage.CONNECT)
                failure(mapped, endpoint, error)
            } finally {
                if (!adopted && candidate != null) {
                    withContext(NonCancellable + ioDispatcher) { runCatching { candidate.close() } }
                    appendDiagnostic(
                        AdbOperationStage.CONNECT,
                        AdbDiagnosticOutcome.RESOURCE_CLOSED,
                        "ADB_CANDIDATE_CLOSED",
                        endpoint,
                    )
                }
            }
        }

    private suspend fun openAndProbeCancellable(
        endpoint: AdbEndpoint,
        openClient: (AdbEndpoint) -> AdbConnectionProbeClient,
    ): OpenedConnectionProbe {
            val opened = AtomicReference<AdbProtocolClient?>()
            val result = AtomicReference<OpenedConnectionProbe?>()
            val failure = AtomicReference<Throwable?>()
            val cancellationRequested = AtomicBoolean(false)
            val worker = connectionProbeScope.launch {
                try {
                    val completed = runInterruptible {
                        val probeClient = openClient(endpoint)
                        opened.set(probeClient.client)
                        OpenedConnectionProbe(
                            probeClient = probeClient,
                            response = probeClient.client.execute(CONNECTION_PROBE),
                        )
                    }
                    if (cancellationRequested.get()) {
                        runCatching { completed.probeClient.client.close() }
                    } else {
                        result.set(completed)
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            return try {
                suspendCancellableCoroutine<OpenedConnectionProbe> { continuation ->
                    continuation.invokeOnCancellation {
                        cancellationRequested.set(true)
                        runCatching { opened.get()?.close() }
                        worker.cancel()
                    }
                    worker.invokeOnCompletion { completionError ->
                        if (!continuation.isActive) return@invokeOnCompletion
                        val error = failure.get() ?: completionError
                        if (error != null) {
                            continuation.resumeWithException(error)
                        } else {
                            continuation.resume(checkNotNull(result.get()))
                        }
                    }
                }
            } catch (error: Throwable) {
                runCatching { opened.get()?.close() }
                worker.cancel()
                withContext(NonCancellable) {
                    withTimeoutOrNull(CONNECTION_CANCELLATION_GRACE) { worker.join() }
                }
                if (cancellationRequested.get()) {
                    throw CancellationException("Connection probe cancelled", error)
                }
                currentCoroutineContext().ensureActive()
                throw error
            }
    }

    private data class OpenedConnectionProbe(
        val probeClient: AdbConnectionProbeClient,
        val response: ProtocolShellResponse,
    )

    override suspend fun pair(
        pairingEndpoint: AdbEndpoint,
        pairingCode: CharArray,
        timeout: Duration,
    ): AdbOperationResult<Unit> = pairWithSecret(
        pairingEndpoint = pairingEndpoint,
        pairingSecret = PairingSecret(pairingCode),
        method = PairingMethod.SIX_DIGIT_CODE,
        timeout = timeout,
    )

    override suspend fun pairWithSecret(
        pairingEndpoint: AdbEndpoint,
        pairingSecret: PairingSecret,
        method: PairingMethod,
        timeout: Duration,
    ): AdbOperationResult<Unit> = mutex.withLock {
        val pairingChars = pairingSecret.withChars { it }
        if (closed.get()) {
            pairingSecret.clear()
            return@withLock failure(AdbError.Unknown(AdbOperationStage.PAIR), pairingEndpoint, null)
        }
        if (active != null) {
            pairingSecret.clear()
            return@withLock operationFailure(AdbError.PairingSessionConflict, pairingEndpoint, null)
        }
        if (method == PairingMethod.NONE) {
            pairingSecret.clear()
            return@withLock operationFailure(AdbError.PairingUnsupported, pairingEndpoint, null)
        }
        if (method != PairingMethod.QR) cancelActiveQrPairing()
        terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
        appendDiagnostic(AdbOperationStage.PAIR, AdbDiagnosticOutcome.STARTED, "ADB_PAIR_STARTED", pairingEndpoint)
        mutableState.value = AdbConnectionState.Pairing(pairingEndpoint)
        try {
            when (method) {
                PairingMethod.QR -> require(pairingChars.isNotEmpty())
                PairingMethod.SIX_DIGIT_CODE -> require(
                    pairingChars.size == 6 && pairingChars.all { it in '0'..'9' },
                )
                PairingMethod.NONE -> error("Pairing method was checked before dispatch.")
            }
            withTimeout(timeout) { withContext(ioDispatcher) { clientFactory.pair(pairingEndpoint, pairingChars) } }
            mutableState.value = AdbConnectionState.Disconnected()
            appendDiagnostic(AdbOperationStage.PAIR, AdbDiagnosticOutcome.SUCCEEDED, "ADB_PAIR_SUCCEEDED", pairingEndpoint)
            AdbOperationResult.Success(Unit)
        } catch (error: TimeoutCancellationException) {
            failure(AdbError.Timeout(AdbOperationStage.PAIR), pairingEndpoint, error)
        } catch (error: CancellationException) {
            mutableState.value = AdbConnectionState.Disconnected(DisconnectionReason.PAIR_CANCELLED)
            appendDiagnostic(AdbOperationStage.PAIR, AdbDiagnosticOutcome.CANCELLED, "ADB_PAIR_CANCELLED", pairingEndpoint)
            AdbOperationResult.Cancelled
        } catch (error: IllegalArgumentException) {
            failure(AdbError.DeviceRejected(AdbOperationStage.PAIR), pairingEndpoint, error)
        } catch (error: Throwable) {
            val mapped = AdbExceptionMapper.map(error, AdbOperationStage.PAIR)
            failure(mapped, pairingEndpoint, error)
        } finally {
            pairingSecret.clear()
        }
    }

    private fun startLocalPairingDiscovery() {
        val previous = synchronized(localPairingDiscoveryLock) { localPairingDiscoveryJob }
        lateinit var job: Job
        job = localPairingScope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.cancelAndJoin()
                coroutineScope {
                    launch {
                        delay(LOCAL_PAIRING_INPUT_WINDOW_MILLIS)
                        localPairingCoordinator.onClockAdvanced()
                    }
                    launch {
                        observeWirelessServices(
                            mode = WirelessDiscoveryMode.LOCAL_PAIRING,
                            timeout = 30.seconds,
                        ).collect { result ->
                            when (result) {
                                is AdbOperationResult.Success -> {
                                    localPairingCoordinator.onDiscoveryState(result.value)
                                }
                                is AdbOperationResult.Failure -> when (result.error) {
                                    AdbError.DiscoveryTimeout -> localPairingCoordinator.onDiscoveryTimedOut()
                                    AdbError.DiscoveryPermissionUnavailable,
                                    AdbError.DiscoveryNetworkUnavailable,
                                    AdbError.DiscoveryPlatformFailure,
                                    -> localPairingCoordinator.onDiscoveryUnsupported()
                                    AdbError.DiscoverySessionChanged -> localPairingCoordinator.onSessionChanged()
                                    else -> localPairingCoordinator.onDiscoveryFailed()
                                }
                                AdbOperationResult.Cancelled -> {
                                    localPairingCoordinator.state.value.window?.windowId?.let {
                                        localPairingCoordinator.cancel(it)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Coordinator terminal paths own the public result; cancellation only releases discovery resources.
            } finally {
                synchronized(localPairingDiscoveryLock) {
                    if (localPairingDiscoveryJob === job) localPairingDiscoveryJob = null
                }
            }
        }
        synchronized(localPairingDiscoveryLock) { localPairingDiscoveryJob = job }
        job.start()
    }

    private fun stopLocalPairingDiscovery() {
        synchronized(localPairingDiscoveryLock) { localPairingDiscoveryJob }?.cancel()
    }

    private suspend fun pairLocalPairingObservation(
        observation: WirelessServiceObservation,
        secret: PairingSecret,
    ): AdbOperationResult<Unit> {
        val pairingAttemptId = localPairingCoordinator.state.value.window?.attemptId
        synchronized(localPairingDiscoveryLock) { localPairingDiscoveryJob }?.cancelAndJoin()
        val endpoint = observation.toPairingEndpoint()
        if (endpoint == null) {
            secret.clear()
            return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        }
        val result = pairWithSecret(
            pairingEndpoint = endpoint,
            pairingSecret = secret,
            method = PairingMethod.SIX_DIGIT_CODE,
        )
        if (result is AdbOperationResult.Success && pairingAttemptId != null) {
            val verifiedDeviceId = verifiedIdentity(
                runCatching { pairingIdentityFingerprint(endpoint) }.getOrNull(),
            )
            synchronized(wirelessDiscoveryLock) {
                lanPairingAssociations[pairingAttemptId] =
                    LanPairingAssociation(observation, verifiedDeviceId)
                trimLanPairingAssociations()
            }
        }
        return result
    }

    override suspend fun createQrPairingAttempt(
        attemptId: PairingAttemptId,
    ): AdbOperationResult<QrPairingMaterial> = mutex.withLock {
        if (closed.get()) {
            return@withLock operationFailure(AdbError.Unknown(AdbOperationStage.PAIR), null, null)
        }
        if (active != null) {
            return@withLock operationFailure(AdbError.PairingSessionConflict, null, null)
        }
        return@withLock try {
            AdbOperationResult.Success(qrPairingCoordinator.start(attemptId)).also {
                activeQrAttemptId = attemptId
            }
        } catch (error: IllegalStateException) {
            operationFailure(AdbError.DeviceRejected(AdbOperationStage.PAIR), null, error)
        } catch (error: IllegalArgumentException) {
            operationFailure(AdbError.DeviceRejected(AdbOperationStage.PAIR), null, error)
        } catch (error: Throwable) {
            operationFailure(AdbExceptionMapper.map(error, AdbOperationStage.PAIR), null, error)
        }
    }

    override suspend fun pairQrObservation(
        attemptId: PairingAttemptId,
        observation: WirelessServiceObservation,
        timeout: Duration,
    ): AdbOperationResult<Unit> {
        if (closed.get()) {
            return operationFailure(AdbError.Unknown(AdbOperationStage.PAIR), null, null)
        }
        val match = qrPairingCoordinator.match(attemptId, observation)
            ?: return operationFailure(qrMatchFailure(attemptId), null, null)
        val endpoint = observation.toPairingEndpoint()
        if (endpoint == null) {
            qrPairingCoordinator.complete(attemptId, PairingAttemptPhase.FAILED)
            return operationFailure(AdbError.DiscoveryResolutionFailed, null, null)
        }
        return try {
            val result = pairWithSecret(
                pairingEndpoint = endpoint,
                pairingSecret = match.secret,
                method = PairingMethod.QR,
                timeout = timeout,
            )
            val terminal = when (result) {
                is AdbOperationResult.Success -> PairingAttemptPhase.SUCCEEDED
                is AdbOperationResult.Failure -> PairingAttemptPhase.FAILED
                AdbOperationResult.Cancelled -> PairingAttemptPhase.CANCELLED
            }
            val finalResult = if (qrPairingCoordinator.complete(attemptId, terminal)) {
                result
            } else {
                qrResultAfterCompetingTerminal(attemptId, result)
            }
            if (finalResult is AdbOperationResult.Success) {
                val verifiedDeviceId = verifiedIdentity(
                    runCatching { pairingIdentityFingerprint(endpoint) }.getOrNull(),
                )
                synchronized(wirelessDiscoveryLock) {
                    lanPairingAssociations[attemptId] = LanPairingAssociation(
                        observation = observation,
                        verifiedDeviceId = verifiedDeviceId,
                    )
                    trimLanPairingAssociations()
                }
            }
            clearActiveQrAttempt(attemptId)
            finalResult
        } catch (error: CancellationException) {
            qrPairingCoordinator.complete(attemptId, PairingAttemptPhase.CANCELLED)
            clearActiveQrAttempt(attemptId)
            throw error
        } catch (error: Throwable) {
            qrPairingCoordinator.complete(attemptId, PairingAttemptPhase.FAILED)
            clearActiveQrAttempt(attemptId)
            throw error
        }
    }

    override suspend fun cancelQrPairing(
        attemptId: PairingAttemptId,
    ): AdbOperationResult<Unit> {
        if (closed.get()) {
            return operationFailure(AdbError.Unknown(AdbOperationStage.PAIR), null, null)
        }
        if (qrPairingCoordinator.complete(attemptId, PairingAttemptPhase.CANCELLED)) {
            clearActiveQrAttempt(attemptId)
            return AdbOperationResult.Success(Unit)
        }
        return operationFailure(qrMatchFailure(attemptId), null, null)
    }

    private fun qrMatchFailure(attemptId: PairingAttemptId): AdbError {
        val phase = qrPairingCoordinator.state(attemptId)
        if (phase in QR_TERMINAL_PHASES) clearActiveQrAttempt(attemptId)
        return if (phase == PairingAttemptPhase.EXPIRED) {
            AdbError.Timeout(AdbOperationStage.PAIR)
        } else {
            AdbError.DeviceRejected(AdbOperationStage.PAIR)
        }
    }

    private fun cancelActiveQrPairing() {
        val attemptId = activeQrAttemptId ?: return
        qrPairingCoordinator.complete(attemptId, PairingAttemptPhase.CANCELLED)
        clearActiveQrAttempt(attemptId)
    }

    private fun clearActiveQrAttempt(attemptId: PairingAttemptId) {
        if (activeQrAttemptId == attemptId) activeQrAttemptId = null
    }

    private fun qrResultAfterCompetingTerminal(
        attemptId: PairingAttemptId,
        protocolResult: AdbOperationResult<Unit>,
    ): AdbOperationResult<Unit> = when (qrPairingCoordinator.state(attemptId)) {
        PairingAttemptPhase.SUCCEEDED -> AdbOperationResult.Success(Unit)
        PairingAttemptPhase.CANCELLED -> AdbOperationResult.Cancelled
        PairingAttemptPhase.EXPIRED -> AdbOperationResult.Failure(AdbError.Timeout(AdbOperationStage.PAIR))
        PairingAttemptPhase.FAILED,
        PairingAttemptPhase.UNSUPPORTED,
        -> if (protocolResult is AdbOperationResult.Failure) {
            protocolResult
        } else {
            AdbOperationResult.Failure(AdbError.DeviceRejected(AdbOperationStage.PAIR))
        }
        else -> AdbOperationResult.Cancelled
    }

    private fun WirelessServiceObservation.toPairingEndpoint(): AdbEndpoint? {
        val address = addresses.firstOrNull() ?: return null
        val host = when (address) {
            is WirelessAddress.Ipv4 -> listOf(
                address.firstOctet,
                address.secondOctet,
                address.thirdOctet,
                address.fourthOctet,
            ).joinToString(".")
            is WirelessAddress.Ipv6 -> buildString {
                append(address.segments.joinToString(":") { it.toString(16) })
                address.scopeId?.let { append('%').append(it) }
            }
        }
        return runCatching { AdbEndpoint(host, port) }.getOrNull()
    }

    private fun PairingEndpointHandle.toPairingEndpoint(): AdbEndpoint? =
        resolve(WirelessServiceObservation::class.java)?.toPairingEndpoint()

    override suspend fun executeShell(command: String, timeout: Duration): AdbOperationResult<ShellResult> =
        mutex.withLock {
            val session = active ?: return@withLock failure(
                AdbError.RemoteClosed(AdbOperationStage.SHELL),
                null,
                null,
            )
            appendDiagnostic(AdbOperationStage.SHELL, AdbDiagnosticOutcome.STARTED, "ADB_SHELL_STARTED", session.endpoint)
            val mark = TimeSource.Monotonic.markNow()
            var commandStream: ProtocolShellCommand? = null
            try {
                val response = withTimeout(timeout) {
                    runInterruptible(ioDispatcher) {
                        session.client.openShellCommand(command).also { commandStream = it }.execute()
                    }
                }
                if (active?.id != session.id) {
                    return@withLock operationFailure(
                        AdbError.RemoteClosed(AdbOperationStage.SHELL),
                        session.endpoint,
                        null,
                    )
                }
                AdbOperationResult.Success(
                    ShellResult(
                        response.stdout,
                        response.stderr,
                        response.exitCode,
                        mark.elapsedNow(),
                        if (response.streamsSeparated) ShellOutputMode.SEPARATED else ShellOutputMode.MERGED,
                        response.wasTruncated,
                    ),
                ).also {
                    appendDiagnostic(
                        AdbOperationStage.SHELL,
                        AdbDiagnosticOutcome.SUCCEEDED,
                        "ADB_SHELL_SUCCEEDED",
                        session.endpoint,
                    )
                }
            } catch (error: TimeoutCancellationException) {
                operationFailure(AdbError.Timeout(AdbOperationStage.SHELL), session.endpoint, error)
            } catch (error: CancellationException) {
                appendDiagnostic(AdbOperationStage.SHELL, AdbDiagnosticOutcome.CANCELLED, "ADB_SHELL_CANCELLED", session.endpoint)
                AdbOperationResult.Cancelled
            } catch (error: Throwable) {
                val mapped = AdbExceptionMapper.map(error, AdbOperationStage.SHELL)
                operationFailure(mapped, session.endpoint, error)
            } finally {
                withContext(NonCancellable + ioDispatcher) { runCatching { commandStream?.close() } }
                if (commandStream != null) {
                    appendDiagnostic(
                        AdbOperationStage.SHELL,
                        AdbDiagnosticOutcome.RESOURCE_CLOSED,
                        "ADB_SHELL_STREAM_CLOSED",
                        session.endpoint,
                    )
                }
            }
        }

    override suspend fun loadDeviceOverview(timeout: Duration): AdbOperationResult<DeviceOverview> {
        val outputs = mutableListOf<String>()
        for (command in listOf(
            AdbCommands.PROPERTIES,
            AdbCommands.MEMORY,
            AdbCommands.STORAGE,
            AdbCommands.BATTERY,
            AdbCommands.UPTIME,
            AdbCommands.CORES,
            AdbCommands.NETWORK,
        )) {
            when (val result = executeShell(command, timeout)) {
                is AdbOperationResult.Success -> outputs += result.value.stdout
                is AdbOperationResult.Failure -> return result
                AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
            }
        }
        return AdbOperationResult.Success(
            AdbCapabilityParsers.overview(
                propertiesText = outputs[0],
                memoryText = outputs[1],
                storageText = outputs[2],
                batteryText = outputs[3],
                uptimeText = outputs[4],
                coresText = outputs[5],
                networkText = outputs[6],
            ),
        )
    }

    override suspend fun refreshDynamicMetrics(timeout: Duration): AdbOperationResult<DynamicDeviceMetrics> {
        val outputs = mutableListOf<String>()
        for (command in listOf(AdbCommands.MEMORY, AdbCommands.BATTERY, AdbCommands.UPTIME)) {
            when (val result = executeShell(command, timeout)) {
                is AdbOperationResult.Success -> outputs += result.value.stdout
                is AdbOperationResult.Failure -> return result
                AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
            }
        }
        return AdbOperationResult.Success(AdbCapabilityParsers.dynamic(outputs[0], outputs[1], outputs[2]))
    }

    override suspend fun listProcesses(timeout: Duration): AdbOperationResult<ProcessSnapshot> {
        val primary = executeShell(AdbCommands.PROCESSES_EXTENDED, timeout)
        if (primary is AdbOperationResult.Success && primary.value.exitCode == 0) {
            val parsed = AdbCapabilityParsers.processes(primary.value.stdout)
            if (parsed.processes.isNotEmpty()) return AdbOperationResult.Success(parsed)
        } else if (primary is AdbOperationResult.Failure) {
            return primary
        } else if (primary is AdbOperationResult.Cancelled) {
            return AdbOperationResult.Cancelled
        }
        return when (val fallback = executeShell(AdbCommands.PROCESSES_FALLBACK, timeout)) {
            is AdbOperationResult.Success -> AdbOperationResult.Success(AdbCapabilityParsers.processes(fallback.value.stdout))
            is AdbOperationResult.Failure -> fallback
            AdbOperationResult.Cancelled -> AdbOperationResult.Cancelled
        }
    }

    override suspend fun loadProcessAnalysis(
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<ProcessAnalysisSnapshot> {
        val initialSession = mutex.withLock { active }
        if (initialSession == null || initialSession.id != expectedSessionId) {
            return applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.PROCESSES),
                initialSession?.endpoint,
            )
        }
        return try {
            withTimeout(timeout) {
                val applications = when (val result = listApplications(timeout)) {
                    is AdbOperationResult.Success -> result.value
                    is AdbOperationResult.Failure -> return@withTimeout result
                    AdbOperationResult.Cancelled -> return@withTimeout AdbOperationResult.Cancelled
                }
                if (applications.sessionId != expectedSessionId || mutex.withLock { active?.id } != expectedSessionId) {
                    return@withTimeout applicationFailure(
                        AdbError.ApplicationSessionInvalid(AdbOperationStage.PROCESSES),
                        initialSession.endpoint,
                    )
                }
                val processes = when (val result = listProcesses(timeout)) {
                    is AdbOperationResult.Success -> result.value
                    is AdbOperationResult.Failure -> return@withTimeout result
                    AdbOperationResult.Cancelled -> return@withTimeout AdbOperationResult.Cancelled
                }
                if (mutex.withLock { active?.id } != expectedSessionId) {
                    return@withTimeout applicationFailure(
                        AdbError.ApplicationSessionInvalid(AdbOperationStage.PROCESSES),
                        initialSession.endpoint,
                    )
                }
                val generation = processAnalysisGeneration.incrementAndGet()
                val analysis = ProcessAssociation.resolve(
                    expectedSessionId = expectedSessionId,
                    applicationSessionId = applications.sessionId,
                    expectedGeneration = generation,
                    applicationGeneration = generation,
                    applications = applications.applications,
                    processes = processes.processes,
                    degradedReason = processes.degradedReason,
                )
                if (mutex.withLock { active?.id } != expectedSessionId) {
                    return@withTimeout applicationFailure(
                        AdbError.ApplicationSessionInvalid(AdbOperationStage.PROCESSES),
                        initialSession.endpoint,
                    )
                }
                latestProcessAnalysis = analysis
                AdbOperationResult.Success(analysis)
            }
        } catch (_: TimeoutCancellationException) {
            applicationFailure(AdbError.Timeout(AdbOperationStage.PROCESSES), initialSession.endpoint)
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        }
    }

    /* P2 process operations are implemented by the session facade below. */
    override suspend fun refreshProcesses(
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<List<ProcessSnapshotEntry>> {
        val initialSession = mutex.withLock { active }
        if (initialSession == null || initialSession.id != expectedSessionId) {
            return applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.PROCESSES),
                initialSession?.endpoint,
            )
        }
        return continueRefreshProcesses(initialSession, expectedSessionId, timeout)
    }

    override suspend fun openInteractiveShell(
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<InteractiveShellSession> = interactiveShellMutex.withLock {
        mutex.withLock {
        val currentSession = active
        if (currentSession?.id != expectedSessionId) {
            return@withLock failure(
                AdbError.RemoteClosed(AdbOperationStage.SHELL),
                currentSession?.endpoint,
                null,
            )
        }
        if (activeInteractiveShell != null) {
            return@withLock failure(
                AdbError.IoFailure(AdbOperationStage.SHELL),
                currentSession.endpoint,
                IllegalStateException("INTERACTIVE_SHELL_BUSY"),
            )
        }

        var protocol: ProtocolInteractiveShell? = null
        try {
            protocol = withTimeout(timeout) {
                runInterruptible(ioDispatcher) {
                    ProtocolInteractiveShell(currentSession.client.openInteractiveShell())
                }
            }
            val streamGeneration = interactiveShellGeneration.incrementAndGet()
            val events = Channel<TerminalOutputEvent>(
                capacity = INTERACTIVE_SHELL_EVENT_CAPACITY,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            val owned = ActiveInteractiveShell(
                expectedSessionId = expectedSessionId,
                streamGeneration = streamGeneration,
                protocol = protocol,
                events = events,
            )
            activeInteractiveShell = owned
            owned.readerJob = localPairingScope.launch {
                try {
                    while (currentCoroutineContext().isActive && !owned.closed.get()) {
                        when (val packet = runInterruptible(ioDispatcher) { owned.protocol.read() }) {
                            is ProtocolShellPacket.StandardOutput -> {
                                if (packet.bytes.isEmpty()) {
                                    emitInteractiveShellState(
                                        owned,
                                        TerminalOutputKind.REMOTE_READY,
                                    )
                                } else {
                                    publishInteractiveShellEvent(
                                        owned,
                                        TerminalOutputKind.STDOUT,
                                        packet.bytes,
                                    )
                                }
                            }
                            is ProtocolShellPacket.StandardError -> publishInteractiveShellEvent(
                                owned,
                                TerminalOutputKind.STDERR,
                                packet.bytes,
                            )
                            is ProtocolShellPacket.Exit -> {
                                publishInteractiveShellEvent(
                                    owned,
                                    TerminalOutputKind.OUTPUT_CLOSED,
                                    ByteArray(0),
                                )
                                break
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // The owner performs deterministic cleanup.
                } catch (_: Throwable) {
                    publishInteractiveShellEvent(
                        owned,
                        TerminalOutputKind.OUTPUT_CLOSED,
                        ByteArray(0),
                    )
                } finally {
                    withContext(NonCancellable) {
                        closeInteractiveShellChild(
                            owned,
                            InteractiveShellCloseReason.REMOTE_CLOSED,
                            cancelReader = false,
                        )
                    }
                }
            }

            val publicSession = object : InteractiveShellSession {
                override val expectedSessionId = owned.expectedSessionId
                override val streamGeneration = owned.streamGeneration
                override val outputEvents: Flow<TerminalOutputEvent> = owned.events.receiveAsFlow()

                override suspend fun sendSubmittedCommand(
                    completeCommand: String,
                ): InteractiveShellResult = interactiveShellWrite(owned, writeStarted = true) {
                    owned.protocol.sendSubmittedCommand(completeCommand)
                    emitInteractiveShellState(owned, TerminalOutputKind.REMOTE_ACTIVE)
                }

                override suspend fun sendTerminalInput(
                    input: TerminalInput,
                ): InteractiveShellResult = interactiveShellWrite(owned, writeStarted = true) {
                    owned.protocol.sendTerminalInput(input)
                }

                override suspend fun close(
                    reason: InteractiveShellCloseReason,
                ): InteractiveShellResult {
                    closeInteractiveShellChild(owned, reason)
                    return InteractiveShellResult.Closed
                }
            }
            AdbOperationResult.Success(publicSession)
        } catch (error: TimeoutCancellationException) {
            runCatching { protocol?.close() }
            operationFailure(AdbError.Timeout(AdbOperationStage.SHELL), currentSession.endpoint, error)
        } catch (error: CancellationException) {
            runCatching { protocol?.close() }
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            runCatching { protocol?.close() }
            val mapped = if (error is UnsupportedOperationException) {
                AdbError.ProtocolIncompatible(AdbOperationStage.SHELL)
            } else {
                AdbExceptionMapper.map(error, AdbOperationStage.SHELL)
            }
            operationFailure(mapped, currentSession.endpoint, error)
        }
        }
    }

    private suspend fun interactiveShellWrite(
        owned: ActiveInteractiveShell,
        writeStarted: Boolean,
        block: suspend () -> Unit,
    ): InteractiveShellResult {
        val currentSessionId = active?.id
        if (owned.closed.get() ||
            owned.expectedSessionId != currentSessionId ||
            owned.streamGeneration != activeInteractiveShell?.streamGeneration
        ) {
            return InteractiveShellResult.Disconnected()
        }
        return try {
            block()
            InteractiveShellResult.Accepted
        } catch (_: TimeoutCancellationException) {
            InteractiveShellResult.TimedOut()
        } catch (_: CancellationException) {
            InteractiveShellResult.Cancelled()
        } catch (error: UnsupportedOperationException) {
            InteractiveShellResult.Unsupported()
        } catch (error: Throwable) {
            when (AdbExceptionMapper.interactiveShellTechnicalCode(error, writeStarted)) {
                AdbExceptionMapper.INTERACTIVE_SHELL_OUTCOME_UNKNOWN ->
                    InteractiveShellResult.OutcomeUnknown()
                else -> InteractiveShellResult.Failed(
                    AdbExceptionMapper.interactiveShellTechnicalCode(error, writeStarted),
                )
            }
        }
    }

    private fun publishInteractiveShellEvent(
        owner: ActiveInteractiveShell,
        kind: TerminalOutputKind,
        bytes: ByteArray,
    ) {
        val event = TerminalOutputEvent(
            expectedSessionId = owner.expectedSessionId,
            streamGeneration = owner.streamGeneration,
            kind = kind,
            text = bytes.toString(Charsets.UTF_8),
            hadDecodingReplacement = bytes.toString(Charsets.UTF_8).contains('\uFFFD'),
        )
        val currentSessionId = active?.id
        if (event.expectedSessionId != currentSessionId ||
            event.streamGeneration != activeInteractiveShell?.streamGeneration
        ) {
            return
        }
        owner.events.trySend(event)
    }

    private fun emitInteractiveShellState(
        owner: ActiveInteractiveShell,
        kind: TerminalOutputKind,
    ) = publishInteractiveShellEvent(owner, kind, ByteArray(0))

    private suspend fun closeInteractiveShellChild(
        owner: ActiveInteractiveShell,
        reason: InteractiveShellCloseReason,
        cancelReader: Boolean = true,
    ) {
        if (!owner.closed.compareAndSet(false, true)) return
        if (activeInteractiveShell === owner) activeInteractiveShell = null
        withContext(NonCancellable + ioDispatcher) {
            runCatching { owner.protocol.closeInput() }
            runCatching { owner.protocol.closeOutput() }
            runCatching { owner.protocol.close() }
        }
        owner.events.close()
        if (cancelReader) owner.readerJob?.cancel()
        @Suppress("UNUSED_VARIABLE")
        val closeReason = reason
    }
    private suspend fun continueRefreshProcesses(
        initialSession: ActiveSession,
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<List<ProcessSnapshotEntry>> {
        val generation = processAnalysisGeneration.incrementAndGet()
        processRefreshRequestedGeneration.set(generation)
        return try {
            withTimeout(maxOf(timeout, PROCESS_REFRESH_BUDGET)) {
                val cachedApplications = mutex.withLock {
                    applicationSnapshot?.takeIf { it.sessionId == expectedSessionId }
                }
                if (!isCurrentProcessRefresh(expectedSessionId, generation)) {
                    return@withTimeout AdbOperationResult.Cancelled
                }
                val extendedPsText = when (val result = executeShell(AdbCommands.PROCESSES_EXTENDED, timeout)) {
                    is AdbOperationResult.Success -> result.value.stdout
                    is AdbOperationResult.Failure -> return@withTimeout result
                    AdbOperationResult.Cancelled -> return@withTimeout AdbOperationResult.Cancelled
                }
                if (!isCurrentProcessRefresh(expectedSessionId, generation)) {
                    return@withTimeout AdbOperationResult.Cancelled
                }
                var psText = extendedPsText
                var parsedEntries = ProcessSnapshotParser.parse(
                    psText = psText,
                    sessionId = expectedSessionId,
                    generation = generation,
                )
                if (parsedEntries.isEmpty() ||
                    parsedEntries.any { it.cpuPercent == null || it.pssMiB == null }
                ) {
                    val fallback = executeShell(AdbCommands.PROCESSES_FALLBACK, timeout)
                    val fallbackText = (fallback as? AdbOperationResult.Success)?.value?.stdout
                    if (!fallbackText.isNullOrBlank()) {
                        val fallbackEntries = ProcessSnapshotParser.parse(
                            psText = fallbackText,
                            sessionId = expectedSessionId,
                            generation = generation,
                        )
                        if (fallbackEntries.isNotEmpty()) psText = fallbackText
                    }

                    val firstCountersText = (executeShell(
                        AdbCommands.PROCESS_COUNTERS,
                        timeout,
                    ) as? AdbOperationResult.Success)?.value?.stdout
                    delay(PROCESS_COUNTER_SAMPLE_DELAY)
                    val secondCountersText = (executeShell(
                        AdbCommands.PROCESS_COUNTERS,
                        timeout,
                    ) as? AdbOperationResult.Success)?.value?.stdout
                    val processorCount = (
                        executeShell(AdbCommands.CORES, timeout) as? AdbOperationResult.Success
                    )?.value?.stdout?.trim()?.lineSequence()?.firstOrNull()?.toIntOrNull() ?: 1
                    val firstCounters = firstCountersText
                        ?.let(AdbCapabilityParsers::processCounters)
                        .orEmpty()
                    val secondCounters = secondCountersText
                        ?.let(AdbCapabilityParsers::processCounters)
                        .orEmpty()
                    val firstTotal = firstCountersText?.let(AdbCapabilityParsers::totalCpuTicks)
                    val secondTotal = secondCountersText?.let(AdbCapabilityParsers::totalCpuTicks)
                    parsedEntries = ProcessSnapshotParser.parse(
                        psText = psText,
                        sessionId = expectedSessionId,
                        generation = generation,
                        firstProcessTicks = firstCounters.mapValues { it.value.cpuTicks },
                        secondProcessTicks = secondCounters.mapValues { it.value.cpuTicks },
                        elapsedTotalTicks = if (
                            firstTotal != null && secondTotal != null && secondTotal >= firstTotal
                        ) {
                            secondTotal - firstTotal
                        } else {
                            null
                        },
                        processorCount = processorCount,
                        startTimeTicksByPid = secondCounters.mapValues { it.value.startTimeTicks },
                    )
                }
                val applicationNames = cachedApplications
                    ?.applications
                    ?.associate { it.packageName to null }
                    .orEmpty()
                val entries = parsedEntries.map { entry ->
                    val resolved = ProcessApplicationAssociationResolver.resolve(
                        entry.processName,
                        applicationNames,
                    )
                    val inferredPackage = entry.processName
                        .substringBefore(':')
                        .takeIf(ApplicationParsers::isValidPackageName)
                        ?.takeIf { entry.identity.uid.orEmpty().matches(APP_PROCESS_USER_PATTERN) }
                    entry.copy(
                        applicationName = resolved?.applicationName ?: "无法解析应用名",
                        applicationPackage = resolved?.packageName ?: inferredPackage,
                    )
                }
                if (!isCurrentProcessRefresh(expectedSessionId, generation)) {
                    AdbOperationResult.Cancelled
                } else {
                    AdbOperationResult.Success(entries)
                }
            }
        } catch (_: TimeoutCancellationException) {
            applicationFailure(AdbError.Timeout(AdbOperationStage.PROCESSES), initialSession.endpoint)
        } catch (_: CancellationException) {
            AdbOperationResult.Cancelled
        }
    }

    private suspend fun isCurrentProcessRefresh(expectedSessionId: String, generation: Long): Boolean =
        processRefreshRequestedGeneration.get() == generation &&
            mutex.withLock { active?.id == expectedSessionId }

    override suspend fun terminateProcess(
        request: ProcessTerminationRequest,
        timeout: Duration,
    ): AdbOperationResult<ProcessTerminationResult> {
        fun result(
            outcome: ProcessTerminationOutcome,
            messageCode: String,
            verified: Set<ProcessIdentity> = emptySet(),
            remaining: Set<ProcessIdentity> = emptySet(),
        ) = AdbOperationResult.Success(
            ProcessTerminationResult(
                sessionId = request.sessionId,
                scope = request.scope,
                outcome = outcome,
                verifiedTerminated = verified,
                remaining = remaining,
                messageCode = messageCode,
            ),
        )
        val current = mutex.withLock { active }
            ?: return result(ProcessTerminationOutcome.DISCONNECTED, "SESSION_DISCONNECTED")
        if (request.sessionId != current.id) {
            return result(ProcessTerminationOutcome.DISCONNECTED, "SESSION_CHANGED")
        }
        val confirmationAccepted = request.riskAcknowledged &&
            (request.scope != ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP || request.forceStopImpactAcknowledged) &&
            rememberProcessTerminationRequest(request.requestId)
        if (!confirmationAccepted) {
            return result(ProcessTerminationOutcome.POLICY_REJECTED, "CONFIRMATION_REQUIRED")
        }
        if (!ProcessTerminationPolicy.allows(
                request.scope,
                request.targetProcess,
                request.targetPackage,
                request.confirmedProcessSet,
            )
        ) {
            return result(ProcessTerminationOutcome.POLICY_REJECTED, "TARGET_NOT_ALLOWED")
        }
        return try {
            withTimeout(timeout) {
                val before = when (val refreshed = refreshProcesses(request.sessionId, PROCESS_REFRESH_BUDGET)) {
                    is AdbOperationResult.Success -> refreshed.value
                    is AdbOperationResult.Failure ->
                        return@withTimeout result(ProcessTerminationOutcome.DISCONNECTED, "PRECHECK_FAILED")
                    AdbOperationResult.Cancelled ->
                        return@withTimeout result(ProcessTerminationOutcome.CANCELLED, "PRECHECK_CANCELLED")
                }
                val targetNow = before.firstOrNull { it.pid == request.targetProcess.pid }
                    ?: return@withTimeout result(ProcessTerminationOutcome.ALREADY_EXITED, "TARGET_ALREADY_EXITED")
                if (!sameProcessIdentity(request.targetProcess, targetNow.identity)) {
                    return@withTimeout result(ProcessTerminationOutcome.IDENTITY_CHANGED, "PROCESS_IDENTITY_CHANGED")
                }
                val beforeScope = when (request.scope) {
                    ProcessTerminationScope.SINGLE_PROCESS -> setOf(targetNow.identity)
                    ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP -> {
                        val packageName = checkNotNull(request.targetPackage)
                        val currentSet = before.filter { it.applicationPackage == packageName }.map { it.identity }.toSet()
                        if (!sameProcessSet(request.confirmedProcessSet, currentSet)) {
                            return@withTimeout result(
                                ProcessTerminationOutcome.IDENTITY_CHANGED,
                                "APPLICATION_PROCESS_SET_CHANGED",
                            )
                        }
                        currentSet
                    }
                }
                val command = when (request.scope) {
                    ProcessTerminationScope.SINGLE_PROCESS -> "kill -TERM ${targetNow.pid}"
                    ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP -> {
                        val userId = processUserId(targetNow.identity.uid)
                            ?: return@withTimeout result(ProcessTerminationOutcome.UNSUPPORTED, "USER_ID_UNAVAILABLE")
                        "am force-stop --user $userId ${request.targetPackage}"
                    }
                }
                when (val dispatched = executeShell(command, timeout)) {
                    is AdbOperationResult.Success -> if (dispatched.value.exitCode != 0) {
                        return@withTimeout result(ProcessTerminationOutcome.POLICY_REJECTED, "COMMAND_REJECTED")
                    }
                    is AdbOperationResult.Failure ->
                        return@withTimeout result(ProcessTerminationOutcome.UNKNOWN, "COMMAND_OUTCOME_UNKNOWN")
                    AdbOperationResult.Cancelled ->
                        return@withTimeout result(ProcessTerminationOutcome.CANCELLED, "COMMAND_CANCELLED")
                }
                val after = when (val refreshed = refreshProcesses(request.sessionId, PROCESS_REFRESH_BUDGET)) {
                    is AdbOperationResult.Success -> refreshed.value
                    is AdbOperationResult.Failure ->
                        return@withTimeout result(ProcessTerminationOutcome.UNKNOWN, "VERIFY_FAILED")
                    AdbOperationResult.Cancelled ->
                        return@withTimeout result(ProcessTerminationOutcome.CANCELLED, "VERIFY_CANCELLED")
                }
                when (request.scope) {
                    ProcessTerminationScope.SINGLE_PROCESS -> {
                        val remains = after.any { sameProcessIdentity(request.targetProcess, it.identity) }
                        if (remains) {
                            result(
                                ProcessTerminationOutcome.UNKNOWN,
                                "TARGET_STILL_PRESENT",
                                remaining = setOf(request.targetProcess),
                            )
                        } else {
                            result(
                                ProcessTerminationOutcome.TERMINATED,
                                "TARGET_EXIT_VERIFIED",
                                verified = setOf(request.targetProcess),
                            )
                        }
                    }
                    ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP -> {
                        val remaining = after
                            .filter { it.applicationPackage == request.targetPackage }
                            .map { it.identity }
                            .toSet()
                        val verified = beforeScope.filterTo(linkedSetOf()) { expected ->
                            remaining.none { sameProcessIdentity(expected, it) }
                        }
                        when {
                            remaining.isEmpty() -> result(
                                ProcessTerminationOutcome.TERMINATED,
                                "APPLICATION_STOP_VERIFIED",
                                verified = beforeScope,
                            )
                            verified.isNotEmpty() -> result(
                                ProcessTerminationOutcome.PARTIAL,
                                "APPLICATION_PARTIALLY_STOPPED",
                                verified = verified,
                                remaining = remaining,
                            )
                            else -> result(
                                ProcessTerminationOutcome.UNKNOWN,
                                "APPLICATION_STILL_PRESENT",
                                remaining = remaining,
                            )
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            result(ProcessTerminationOutcome.TIMED_OUT, "TIMEOUT")
        } catch (_: CancellationException) {
            result(ProcessTerminationOutcome.CANCELLED, "CANCELLED")
        }
    }

    private fun rememberProcessTerminationRequest(requestId: String): Boolean =
        synchronized(processTerminationRequestLock) {
            if (requestId.isBlank() || requestId in consumedProcessTerminationRequestIds) {
                false
            } else {
                consumedProcessTerminationRequestIds += requestId
                while (consumedProcessTerminationRequestIds.size > MAX_CONSUMED_PROCESS_REQUESTS) {
                    consumedProcessTerminationRequestIds.remove(consumedProcessTerminationRequestIds.first())
                }
                true
            }
        }

    private fun sameProcessIdentity(first: ProcessIdentity, second: ProcessIdentity): Boolean =
        first.sessionId == second.sessionId &&
            first.pid == second.pid &&
            first.startTimeTicks != null &&
            first.startTimeTicks == second.startTimeTicks &&
            first.uid == second.uid &&
            first.processName == second.processName

    private fun sameProcessSet(first: Set<ProcessIdentity>, second: Set<ProcessIdentity>): Boolean =
        first.size == second.size && first.all { expected -> second.any { sameProcessIdentity(expected, it) } }

    private fun processUserId(uid: String?): Int? =
        uid?.toIntOrNull()?.takeIf { it >= 0 }?.div(100_000)
            ?: Regex("^u(\\d+)_a\\d+$").matchEntire(uid.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()

    override suspend fun listApplications(timeout: Duration): AdbOperationResult<ApplicationSnapshot> =
        applicationMutex.withLock {
            val session = mutex.withLock { active } ?: return@withLock applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.APPLICATIONS_LIST),
                null,
            )
            appendDiagnostic(
                AdbOperationStage.APPLICATIONS_LIST,
                AdbDiagnosticOutcome.STARTED,
                "ADB_APP_LIST_STARTED",
                session.endpoint,
            )
            try {
                withTimeout(timeout) { loadApplicationSnapshot(session, timeout, remember = true) }
            } catch (error: TimeoutCancellationException) {
                applicationFailure(AdbError.Timeout(AdbOperationStage.APPLICATIONS_LIST), session.endpoint)
            } catch (error: CancellationException) {
                appendDiagnostic(
                    AdbOperationStage.APPLICATIONS_LIST,
                    AdbDiagnosticOutcome.CANCELLED,
                    "ADB_APP_LIST_CANCELLED",
                    session.endpoint,
                )
                AdbOperationResult.Cancelled
            }
        }

    override suspend fun openApkExtraction(
        request: ApkExtractionRequest,
        timeout: Duration,
    ): AdbOperationResult<ApkExtractionHandle> {
        val session = mutex.withLock { active }
        if (session == null || session.id != request.expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.APK_EXTRACTION),
                session?.endpoint,
                null,
            )
        }
        if (!ApplicationParsers.isValidPackageName(request.packageName)) {
            return operationFailure(
                AdbError.DeviceRejected(AdbOperationStage.APK_EXTRACTION),
                session.endpoint,
                null,
            )
        }
        val lease = when (
            val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.APK_EXTRACTION,
                request.expectedSessionId,
            )
        ) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        return try {
            val resolved = ApkExtractionProtocol.discover(
                client = session.client,
                userId = request.userId,
                packageName = request.packageName,
                timeout = timeout,
            )
            if (mutex.withLock { active?.id } != request.expectedSessionId || !lease.isActive) {
                lease.release()
                return operationFailure(
                    AdbError.SessionInvalid(AdbExclusiveOperationKind.APK_EXTRACTION),
                    session.endpoint,
                    null,
                )
            }
            AdbOperationResult.Success(
                createApkExtractionHandle(
                    session = session,
                    expectedSessionId = request.expectedSessionId,
                    resolved = resolved,
                    lease = lease,
                ),
            )
        } catch (error: TimeoutCancellationException) {
            lease.release()
            operationFailure(AdbError.Timeout(AdbOperationStage.APK_EXTRACTION), session.endpoint, error)
        } catch (_: CancellationException) {
            lease.release()
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            lease.release()
            operationFailure(
                AdbError.DeviceRejected(AdbOperationStage.APK_EXTRACTION),
                session.endpoint,
                error,
            )
        }
    }

    private fun createApkExtractionHandle(
        session: ActiveSession,
        expectedSessionId: String,
        resolved: List<ResolvedApkComponent>,
        lease: ExclusiveAdbOperationLease,
    ): ApkExtractionHandle {
        val closed = AtomicBoolean(false)
        val transferMutex = Mutex()
        val pathsById = resolved.associate { it.publicComponent.componentId to it.remotePath }
        val publicComponents = resolved.map(ResolvedApkComponent::publicComponent)
        return object : ApkExtractionHandle {
            override val expectedSessionId = expectedSessionId
            override val components = publicComponents

            override suspend fun transfer(
                componentId: String,
                destination: OutputStream,
                noProgressTimeout: Duration,
                progress: (FileTransferProgress) -> Unit,
            ): AdbOperationResult<ApkComponentTransferReceipt> = transferMutex.withLock {
                val path = pathsById[componentId]
                if (path == null || closed.get() || !lease.isActive ||
                    mutex.withLock { active?.id } != expectedSessionId
                ) {
                    return@withLock operationFailure(
                        AdbError.SessionInvalid(AdbExclusiveOperationKind.APK_EXTRACTION),
                        session.endpoint,
                        null,
                    )
                }
                try {
                    val transferred = KadbRemoteFileProtocol.receive(
                        client = session.client,
                        path = path,
                        destination = destination,
                        noProgressTimeout = noProgressTimeout,
                        cancellationGrace = transferCancellationGrace,
                        onForcedSessionClose = { runCatching { session.client.close() } },
                    ) { bytes ->
                        progress(
                            FileTransferProgress(
                                transferredBytes = bytes,
                                totalBytes = components.firstOrNull { it.componentId == componentId }
                                    ?.expectedSizeBytes,
                            ),
                        )
                    }
                    if (!lease.isActive || mutex.withLock { active?.id } != expectedSessionId) {
                        operationFailure(
                            AdbError.SessionInvalid(AdbExclusiveOperationKind.APK_EXTRACTION),
                            session.endpoint,
                            null,
                        )
                    } else {
                        AdbOperationResult.Success(
                            ApkComponentTransferReceipt(expectedSessionId, componentId, transferred),
                        )
                    }
                } catch (error: ProtocolNoProgressTimeoutException) {
                    operationFailure(AdbError.NoProgressTimeout, session.endpoint, error)
                } catch (_: CancellationException) {
                    AdbOperationResult.Cancelled
                } catch (error: Throwable) {
                    operationFailure(fileTransferError(error), session.endpoint, error)
                }
            }

            override fun close() {
                if (closed.compareAndSet(false, true)) lease.release()
            }
        }
    }

    override suspend fun installApk(
        request: ApkInstallRequest,
        progress: (ApkInstallStage) -> Unit,
        timeout: Duration,
    ): AdbOperationResult<ApkInstallResult> {
        val session = mutex.withLock { active }
        if (session == null || session.id != request.expectedSessionId) {
            return operationFailure(
                AdbError.SessionInvalid(AdbExclusiveOperationKind.APK_INSTALL),
                session?.endpoint,
                null,
            )
        }
        if (!request.displayName.lowercase().endsWith(".apk") ||
            request.expectedPackageName?.let(ApplicationParsers::isValidPackageName) == false
        ) {
            return AdbOperationResult.Success(
                ApkInstallResult.Rejected(request.expectedSessionId, "INPUT_NOT_STANDALONE"),
            )
        }
        val lease = when (
            val acquired = acquireExclusiveOperation(
                AdbExclusiveOperationKind.APK_INSTALL,
                request.expectedSessionId,
            )
        ) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> return acquired
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        val stagedRemotePath = "/data/local/tmp/.sheen-${UUID.randomUUID()}.apk"
        var mutationStarted = false
        var cleanupConfirmed = false
        val outcome = try {
            withTimeout(timeout) {
                progress(ApkInstallStage.STAGING)
                request.source().use { source ->
                    stageApk(
                        client = session.client,
                        stagedRemotePath = stagedRemotePath,
                        source = source,
                        sourceSizeBytes = request.sourceSizeBytes,
                        timeout = timeout,
                        noProgressTimeout = transferNoProgressTimeout,
                        progress = {},
                    )
                }

                val targetPackage = request.expectedPackageName
                val packageVersionsBeforeInstall = if (targetPackage == null) {
                    installedPackageVersionsForVerification(session.client, request.userId, timeout)
                } else {
                    null
                }
                val packagesBeforeInstall = if (targetPackage == null) {
                    installedPackageNamesForVerification(session.client, request.userId, timeout)
                } else {
                    null
                }
                var oldRemoved = false
                if (request.mode == ApkInstallMode.UNINSTALL_THEN_INSTALL) {
                    if (targetPackage == null) {
                        return@withTimeout ApkInstallResult.Rejected(
                            request.expectedSessionId,
                            "PACKAGE_IDENTITY_REQUIRED",
                        )
                    }
                    mutationStarted = true
                    val uninstall = executePackageCommand(
                        session.client,
                        ApplicationPackageProtocol.uninstallForUser(request.userId, targetPackage),
                        timeout,
                    )
                    if (!uninstall.commandSucceeded()) {
                        return@withTimeout ApkInstallResult.Rejected(
                            request.expectedSessionId,
                            "UNINSTALL_FAILED",
                        )
                    }
                    oldRemoved = !verifyInstalledPackage(
                        session.client,
                        request.userId,
                        targetPackage,
                        timeout,
                    )
                    if (!oldRemoved) {
                        return@withTimeout ApkInstallResult.Rejected(
                            request.expectedSessionId,
                            "SYSTEM_BASE_RETAINED",
                        )
                    }
                }

                mutationStarted = true
                progress(ApkInstallStage.INSTALLING)
                val install = installStagedApk(
                    client = session.client,
                    stagedRemotePath = stagedRemotePath,
                    replaceExisting = request.mode == ApkInstallMode.REPLACE ||
                        request.mode == ApkInstallMode.REPLACE_OR_DOWNGRADE,
                    allowDowngrade = request.mode == ApkInstallMode.REPLACE_OR_DOWNGRADE,
                    timeout = timeout,
                )
                if (!install.commandSucceeded()) {
                    val installError = AdbError.DeviceRejected(AdbOperationStage.APK_INSTALL)
                    return@withTimeout if (oldRemoved && targetPackage != null) {
                        ApkInstallResult.OldRemovedNoRollback(
                            expectedSessionId = request.expectedSessionId,
                            packageName = targetPackage,
                            cause = installError,
                        )
                    } else {
                        ApkInstallResult.Rejected(request.expectedSessionId, "INSTALL_FAILED")
                    }
                }

                progress(ApkInstallStage.VERIFYING)
                val verifiedTarget = targetPackage ?: discoverSingleInstalledPackage(
                    client = session.client,
                    userId = request.userId,
                    packageVersionsBeforeInstall = packageVersionsBeforeInstall,
                    packagesBeforeInstall = packagesBeforeInstall,
                    timeout = timeout,
                )
                if (verifiedTarget == null) {
                    when (request.mode) {
                        ApkInstallMode.STANDARD ->
                            ApkInstallResult.Rejected(request.expectedSessionId, "INSTALL_FAILED")
                        ApkInstallMode.REPLACE,
                        ApkInstallMode.REPLACE_OR_DOWNGRADE,
                        -> ApkInstallResult.PackageManagerAccepted(
                            expectedSessionId = request.expectedSessionId,
                            privateDataPreserved = true,
                        )
                        ApkInstallMode.UNINSTALL_THEN_INSTALL -> ApkInstallResult.OutcomeUnknown(
                            request.expectedSessionId,
                            ApkInstallStage.VERIFYING,
                        )
                    }
                } else if (!verifyInstalledPackage(
                        session.client,
                        request.userId,
                        verifiedTarget,
                        timeout,
                    )
                ) {
                    ApkInstallResult.OutcomeUnknown(
                        request.expectedSessionId,
                        ApkInstallStage.VERIFYING,
                    )
                } else {
                    ApkInstallResult.VerifiedInstalled(
                        expectedSessionId = request.expectedSessionId,
                        packageName = verifiedTarget,
                        privateDataPreserved = request.mode == ApkInstallMode.REPLACE ||
                            request.mode == ApkInstallMode.REPLACE_OR_DOWNGRADE,
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            ApkInstallResult.TimedOut(
                expectedSessionId = request.expectedSessionId,
                stage = if (mutationStarted) ApkInstallStage.VERIFYING else ApkInstallStage.STAGING,
            )
        } catch (_: CancellationException) {
            ApkInstallResult.Cancelled(request.expectedSessionId, mutationStarted)
        } catch (error: Throwable) {
            val technicalCode = apkInstallFailureCode(error, mutationStarted)
            appendDiagnostic(
                AdbOperationStage.APK_INSTALL,
                AdbDiagnosticOutcome.FAILED,
                technicalCode,
                session.endpoint,
                error,
            )
            ApkInstallResult.OutcomeUnknown(
                expectedSessionId = request.expectedSessionId,
                stage = if (mutationStarted) ApkInstallStage.INSTALLING else ApkInstallStage.STAGING,
                technicalCode = technicalCode,
            )
        } finally {
            progress(ApkInstallStage.CLEANING)
            cleanupConfirmed = withContext(NonCancellable) {
                cleanupStagedApk(session.client, stagedRemotePath)
            }
            lease.release()
        }
        val cleanupAwareOutcome = if (!cleanupConfirmed &&
            outcome is ApkInstallResult.VerifiedInstalled
        ) {
            ApkInstallResult.OutcomeUnknown(request.expectedSessionId, ApkInstallStage.CLEANING)
        } else {
            outcome
        }
        return AdbOperationResult.Success(cleanupAwareOutcome)
    }

    private fun apkInstallFailureCode(error: Throwable, mutationStarted: Boolean): String = when (error) {
        is ProtocolLocalSourceException -> "APK_SOURCE_READ_FAILED"
        is ProtocolNoProgressTimeoutException -> "APK_TRANSFER_NO_PROGRESS"
        is SecurityException -> "APK_SOURCE_PERMISSION_DENIED"
        else -> AdbExceptionMapper.map(
            error,
            if (mutationStarted) AdbOperationStage.APK_INSTALL else AdbOperationStage.FILE_TRANSFER,
        ).technicalCode
    }

    private suspend fun discoverSingleInstalledPackage(
        client: AdbProtocolClient,
        userId: Int,
        packageVersionsBeforeInstall: Map<String, Long>?,
        packagesBeforeInstall: Set<String>?,
        timeout: Duration,
    ): String? {
        val versionsAfter = installedPackageVersionsForVerification(client, userId, timeout)
        ApplicationPackageProtocol.singleInstalledOrChanged(
            packageVersionsBeforeInstall,
            versionsAfter,
        )?.let { return it }
        val before = packagesBeforeInstall ?: return null
        val after = installedPackageNamesForVerification(client, userId, timeout) ?: return null
        return (after - before).singleOrNull()
    }

    private suspend fun installedPackageVersionsForVerification(
        client: AdbProtocolClient,
        userId: Int,
        timeout: Duration,
    ): Map<String, Long>? {
        val response = executePackageCommand(
            client,
            ApplicationPackageProtocol.versionedPackages(userId),
            timeout,
        )
        if (!response.commandSucceeded()) return null
        return ApplicationPackageProtocol.parseVersionedPackages(response.stdout)
    }

    private suspend fun installedPackageNamesForVerification(
        client: AdbProtocolClient,
        userId: Int,
        timeout: Duration,
    ): Set<String>? {
        val response = executePackageCommand(client, "pm list packages --user $userId", timeout)
        if (!response.commandSucceeded()) return null
        return when (val parsed = ApplicationParsers.packageNames(response.stdout)) {
            is PackageNamesParse.Success -> parsed.names
            PackageNamesParse.Empty -> emptySet()
            PackageNamesParse.Malformed,
            PackageNamesParse.CapacityExceeded,
            -> null
        }
    }

    private suspend fun executePackageCommand(
        client: AdbProtocolClient,
        command: String,
        timeout: Duration,
    ): ProtocolShellResponse = withTimeout(timeout) {
        runInterruptible(ioDispatcher) {
            client.openShellCommand(command).use(ProtocolShellCommand::execute)
        }
    }

    private suspend fun verifyInstalledPackage(
        client: AdbProtocolClient,
        userId: Int,
        packageName: String,
        timeout: Duration,
    ): Boolean {
        val response = executePackageCommand(
            client,
            ApplicationPackageProtocol.packagePresence(userId, packageName),
            timeout,
        )
        return response.commandSucceeded() &&
            response.stdout.lineSequence().map(String::trim).any { it == "package:$packageName" }
    }

    private suspend fun cleanupStagedApk(
        client: AdbProtocolClient,
        stagedRemotePath: String,
    ): Boolean = runCatching {
        withTimeout(APK_CLEANUP_TIMEOUT) {
            deleteStagedApk(client, stagedRemotePath, APK_CLEANUP_TIMEOUT)
        }
    }.getOrDefault(false)

    private fun ProtocolShellResponse.commandSucceeded(): Boolean =
        exitCode == 0 &&
            !stdout.contains("Failure [", ignoreCase = true) &&
            !stderr.contains("Failure [", ignoreCase = true) &&
            !stderr.contains("permission denied", ignoreCase = true)

    override fun observeApplicationMetadata(
        expectedSessionId: String,
        preferredLocaleTags: List<String>,
    ): Flow<AdbOperationResult<ApplicationMetadataUpdate>> = flow {
        applicationMetadataMutex.lock()
        try {
            val session = mutex.withLock { active }
            val snapshot = applicationMutex.withLock {
                applicationSnapshot?.takeIf { it.sessionId == expectedSessionId }
            }
            if (session == null || session.id != expectedSessionId || snapshot == null) {
                emit(
                    applicationFailure(
                        AdbError.ApplicationSessionInvalid(AdbOperationStage.APPLICATIONS_LIST),
                        session?.endpoint,
                    ),
                )
                return@flow
            }

            val loader = applicationMetadataLoader
                ?.takeIf { applicationMetadataLoaderSessionId == expectedSessionId }
                ?: run {
                    clearApplicationMetadata()
                    createApplicationMetadataLoader(session).also {
                        applicationMetadataLoader = it
                        applicationMetadataLoaderSessionId = expectedSessionId
                    }
                }
            loader.retainSession(expectedSessionId)
            try {
                loader.load(
                    sessionId = expectedSessionId,
                    userId = snapshot.userId,
                    packageNames = snapshot.applications.map { it.packageName },
                    preferredLocaleTags = preferredLocaleTags,
                ).collect { update ->
                    val stillOwned = mutex.withLock { active?.id == expectedSessionId } &&
                        applicationMutex.withLock {
                            applicationSnapshot
                                ?.takeIf { it.sessionId == update.sessionId && it.userId == update.userId }
                                ?.applications
                                ?.any { it.packageName == update.packageName } == true
                        }
                    if (!stillOwned) {
                        clearApplicationMetadata()
                        clearProcessAnalysis()
                        emit(
                            AdbOperationResult.Success(
                                ApplicationMetadataUpdate(
                                    sessionId = update.sessionId,
                                    userId = update.userId,
                                    packageName = update.packageName,
                                    displayName = null,
                                    status = ApplicationMetadataStatus.SESSION_CHANGED,
                                ),
                            ),
                        )
                        throw ApplicationMetadataOwnershipLost()
                    }
                    emit(AdbOperationResult.Success(update.toPublic()))
                }
            } catch (_: ApplicationMetadataOwnershipLost) {
                Unit
            }
        } finally {
            clearApplicationMetadata()
            applicationMetadataMutex.unlock()
        }
    }

    private suspend fun createApplicationMetadataLoader(session: ActiveSession): ApplicationMetadataLoader {
        val isCurrent = { active?.id == session.id }
        val reader = metadataReaderFactory?.invoke(session.client, isCurrent) ?: run {
            val childClient = runInterruptible(ioDispatcher) { clientFactory.open(session.endpoint) }
            applicationMetadataClient = childClient
            BoundedRemoteApkReader(
                client = childClient,
                sessionIsCurrent = isCurrent,
                ioDispatcher = ioDispatcher,
                cancellationGrace = metadataCancellationGrace,
                onForcedSessionClose = { runCatching { childClient.close() } },
            )
        }
        return ApplicationMetadataLoader(
            reader = reader,
            parseMetadata = metadataParser,
            batchTimeout = metadataBatchTimeout,
        )
    }

    private fun com.sheen.adb.core.internal.applications.ApplicationMetadataLoadUpdate.toPublic(): ApplicationMetadataUpdate {
        return ApplicationMetadataUpdate(
            sessionId = sessionId,
            userId = userId,
            packageName = packageName,
            displayName = metadata?.displayName,
            status = when (status) {
                ApplicationMetadataLoadStatus.AVAILABLE -> ApplicationMetadataStatus.AVAILABLE
                ApplicationMetadataLoadStatus.UNAVAILABLE -> ApplicationMetadataStatus.UNAVAILABLE
                ApplicationMetadataLoadStatus.TOO_LARGE -> ApplicationMetadataStatus.TOO_LARGE
                ApplicationMetadataLoadStatus.PARSE_FAILED -> ApplicationMetadataStatus.PARSE_FAILED
                ApplicationMetadataLoadStatus.SESSION_CHANGED -> ApplicationMetadataStatus.SESSION_CHANGED
                ApplicationMetadataLoadStatus.TIMED_OUT -> ApplicationMetadataStatus.TIMED_OUT
            },
        )
    }

    private fun clearApplicationMetadata() {
        applicationMetadataLoader?.clear()
        applicationMetadataLoader = null
        applicationMetadataLoaderSessionId = null
        applicationMetadataClient?.let { child ->
            applicationMetadataClient = null
            runCatching { child.close() }
        }
    }

    private fun clearProcessAnalysis() {
        latestProcessAnalysis = null
    }

    private class ApplicationMetadataOwnershipLost : RuntimeException()

    override suspend fun prepareApplicationUninstall(
        expectedSessionId: String,
        userId: Int,
        packageName: String,
        expectedGeneration: Long,
    ): AdbOperationResult<ApplicationUninstallPreparation> = applicationMutex.withLock {
        val session = mutex.withLock { active }
        val snapshot = applicationSnapshot
        if (session == null || session.id != expectedSessionId ||
            snapshot == null || snapshot.sessionId != expectedSessionId ||
            snapshot.userId != userId || snapshot.generation != expectedGeneration
        ) {
            return@withLock applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.APPLICATION_UNINSTALL),
                session?.endpoint,
            )
        }
        val target = snapshot.applications.singleOrNull { it.packageName == packageName }
            ?: return@withLock applicationFailure(
                AdbError.ApplicationPackageNotFound(AdbOperationStage.APPLICATION_UNINSTALL),
                session.endpoint,
            )
        val preparation = ApplicationUninstallPreparation(
            expectedSessionId = expectedSessionId,
            userId = userId,
            packageName = packageName,
            expectedGeneration = expectedGeneration,
            confirmationNonce = UUID.randomUUID().toString(),
            classification = target.classification,
        )
        pendingApplicationUninstall = PendingApplicationUninstall(preparation)
        AdbOperationResult.Success(preparation)
    }

    override suspend fun uninstallApplication(
        request: ApplicationUninstallRequest,
        timeout: Duration,
    ): AdbOperationResult<ApplicationUninstallResult> = applicationMutex.withLock {
        val session = mutex.withLock { active }
        if (session == null || session.id != request.expectedSessionId) {
            return@withLock applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.APPLICATION_UNINSTALL),
                session?.endpoint,
            )
        }
        val preparation = validateUninstallConfirmation(request)
            ?: return@withLock AdbOperationResult.Success(
                ApplicationUninstallResult.Rejected(request.expectedSessionId, "CONFIRMATION_INVALID"),
            )
        pendingApplicationUninstall = null
        if (preparation.classification != ApplicationClassification.ORDINARY ||
            !request.deletePrivateDataAcknowledged
        ) {
            return@withLock AdbOperationResult.Success(
                ApplicationUninstallResult.PolicyRejected(
                    request.expectedSessionId,
                    preparation.classification,
                ),
            )
        }

        var dispatched = false
        try {
            withTimeout(timeout) {
                val fresh = loadApplicationSnapshot(session, timeout, remember = true)
                val snapshot = (fresh as? AdbOperationResult.Success)?.value
                    ?: return@withTimeout ApplicationUninstallResult.OutcomeUnknown(
                        request.expectedSessionId,
                        ApplicationUninstallStage.PREPARING,
                    )
                val target = snapshot.applications.singleOrNull {
                    it.packageName == request.packageName && it.userId == request.userId
                } ?: return@withTimeout ApplicationUninstallResult.VerifiedRemoved(
                    request.expectedSessionId,
                    request.packageName,
                )
                if (target.classification != ApplicationClassification.ORDINARY) {
                    return@withTimeout ApplicationUninstallResult.PolicyRejected(
                        request.expectedSessionId,
                        target.classification,
                    )
                }

                dispatched = true
                val response = executePackageCommand(
                    session.client,
                    ApplicationPackageProtocol.uninstallForUser(request.userId, request.packageName),
                    timeout,
                )
                if (!response.commandSucceeded()) {
                    return@withTimeout ApplicationUninstallResult.Rejected(
                        request.expectedSessionId,
                        "UNINSTALL_REJECTED",
                    )
                }
                when (
                    val remaining = verifyApplicationRemoval(
                        session,
                        request.expectedSessionId,
                        request.userId,
                        request.packageName,
                        timeout,
                    )
                ) {
                    null -> ApplicationUninstallResult.VerifiedRemoved(
                        request.expectedSessionId,
                        request.packageName,
                    )
                    else -> if (remaining.classification == ApplicationClassification.SYSTEM) {
                        ApplicationUninstallResult.SystemBaseRetained(
                            request.expectedSessionId,
                            request.packageName,
                        )
                    } else {
                        ApplicationUninstallResult.OutcomeUnknown(
                            request.expectedSessionId,
                            ApplicationUninstallStage.VERIFYING,
                        )
                    }
                }
            }.let { AdbOperationResult.Success(it) }
        } catch (_: TimeoutCancellationException) {
            AdbOperationResult.Success(
                if (dispatched) {
                    ApplicationUninstallResult.OutcomeUnknown(
                        request.expectedSessionId,
                        ApplicationUninstallStage.UNINSTALLING,
                    )
                } else {
                    ApplicationUninstallResult.TimedOut(
                        request.expectedSessionId,
                        ApplicationUninstallStage.PREPARING,
                    )
                },
            )
        } catch (_: CancellationException) {
            if (dispatched) {
                AdbOperationResult.Success(
                    ApplicationUninstallResult.OutcomeUnknown(
                        request.expectedSessionId,
                        ApplicationUninstallStage.UNINSTALLING,
                    ),
                )
            } else {
                AdbOperationResult.Cancelled
            }
        }
    }

    private fun validateUninstallConfirmation(
        request: ApplicationUninstallRequest,
    ): ApplicationUninstallPreparation? {
        val prepared = pendingApplicationUninstall?.preparation ?: return null
        return prepared.takeIf {
            it.expectedSessionId == request.expectedSessionId &&
                it.userId == request.userId &&
                it.packageName == request.packageName &&
                it.expectedGeneration == request.expectedGeneration &&
                it.confirmationNonce == request.confirmationNonce
        }
    }

    private suspend fun verifyApplicationRemoval(
        session: ActiveSession,
        expectedSessionId: String,
        userId: Int,
        packageName: String,
        timeout: Duration,
    ): RemoteApplication? {
        if (mutex.withLock { active?.id } != expectedSessionId) return null
        val refreshed = loadApplicationSnapshot(session, timeout, remember = true)
        val snapshot = (refreshed as? AdbOperationResult.Success)?.value ?: return null
        if (snapshot.sessionId != expectedSessionId || snapshot.userId != userId) return null
        return snapshot.applications.singleOrNull { it.packageName == packageName }
    }

    override suspend fun forceStopApplication(
        packageName: String,
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<ApplicationMutationResult> = applicationMutex.withLock {
        mutateApplication(
            packageName = packageName,
            expectedSessionId = expectedSessionId,
            timeout = timeout,
            stage = AdbOperationStage.APPLICATION_FORCE_STOP,
            enabled = null,
        )
    }

    override suspend fun setApplicationEnabled(
        packageName: String,
        enabled: Boolean,
        expectedSessionId: String,
        timeout: Duration,
    ): AdbOperationResult<ApplicationMutationResult> = applicationMutex.withLock {
        mutateApplication(
            packageName = packageName,
            expectedSessionId = expectedSessionId,
            timeout = timeout,
            stage = AdbOperationStage.APPLICATION_SET_ENABLED,
            enabled = enabled,
        )
    }

    private suspend fun mutateApplication(
        packageName: String,
        expectedSessionId: String,
        timeout: Duration,
        stage: AdbOperationStage,
        enabled: Boolean?,
    ): AdbOperationResult<ApplicationMutationResult> {
        val session = mutex.withLock { active }
            ?: return applicationFailure(AdbError.ApplicationSessionInvalid(stage), null)
        if (session.id != expectedSessionId) {
            return applicationFailure(AdbError.ApplicationSessionInvalid(stage), session.endpoint)
        }
        val remembered = applicationSnapshot
        val rememberedTarget = remembered?.takeIf { it.sessionId == expectedSessionId }
            ?.applications?.singleOrNull { it.packageName == packageName }
        if (!isAllowedApplicationTarget(session, rememberedTarget, packageName, enabled != null)) {
            return applicationFailure(AdbError.ApplicationTargetNotAllowed(stage), session.endpoint)
        }

        appendDiagnostic(stage, AdbDiagnosticOutcome.STARTED, "ADB_APP_MUTATION_STARTED", session.endpoint)
        var dispatched = false
        return try {
            withTimeout(timeout) {
                val fresh = when (val loaded = loadApplicationSnapshot(session, timeout, remember = true)) {
                    is AdbOperationResult.Success -> loaded.value
                    is AdbOperationResult.Failure -> return@withTimeout loaded
                    AdbOperationResult.Cancelled -> return@withTimeout AdbOperationResult.Cancelled
                }
                val freshTarget = fresh.applications.singleOrNull { it.packageName == packageName }
                    ?: return@withTimeout applicationFailure(AdbError.ApplicationPackageNotFound(stage), session.endpoint)
                if (!isAllowedApplicationTarget(session, freshTarget, packageName, enabled != null)) {
                    return@withTimeout applicationFailure(AdbError.ApplicationTargetNotAllowed(stage), session.endpoint)
                }
                if (mutex.withLock { active?.id } != expectedSessionId) {
                    return@withTimeout applicationFailure(AdbError.ApplicationSessionInvalid(stage), session.endpoint)
                }

                val command = if (enabled == null) {
                    ApplicationCommands.forceStop(fresh.userId, packageName)
                } else {
                    ApplicationCommands.setEnabled(fresh.userId, packageName, enabled)
                }
                dispatched = true
                val commandResponse = executePackageCommand(session.client, command, timeout)
                val rejection = ApplicationParsers.rejectedOutput(
                    commandResponse.stdout,
                    commandResponse.stderr,
                    commandResponse.exitCode,
                )
                if (rejection != null) {
                    return@withTimeout when (rejection) {
                        ApplicationCommandRejection.PACKAGE_NOT_FOUND -> applicationFailure(
                            AdbError.ApplicationPackageNotFound(stage),
                            session.endpoint,
                        )
                        ApplicationCommandRejection.POLICY -> applicationFailure(
                            AdbError.ApplicationPolicyRejected(stage),
                            session.endpoint,
                        )
                    }
                }

                if (enabled == null) {
                    val running = executePackageCommand(
                        session.client,
                        "pidof $packageName",
                        timeout,
                    ).stdout.trim()
                    if (running.isNotEmpty()) {
                        return@withTimeout applicationFailure(
                            AdbError.ApplicationStateVerifyFailed,
                            session.endpoint,
                        )
                    }
                    appendDiagnostic(stage, AdbDiagnosticOutcome.SUCCEEDED, "ADB_APP_FORCE_STOP_ACCEPTED", session.endpoint)
                    return@withTimeout AdbOperationResult.Success(ApplicationMutationResult.RequestAccepted(session.id))
                }

                val verifiedSnapshot = when (val loaded = loadApplicationSnapshot(session, timeout, remember = true)) {
                    is AdbOperationResult.Success -> loaded.value
                    is AdbOperationResult.Failure -> return@withTimeout unknownMutation(session, stage)
                    AdbOperationResult.Cancelled -> return@withTimeout unknownMutation(session, stage)
                }
                val verifiedTarget = verifiedSnapshot.applications.singleOrNull { it.packageName == packageName }
                    ?: return@withTimeout applicationFailure(
                        AdbError.ApplicationPackageNotFound(AdbOperationStage.APPLICATION_VERIFY),
                        session.endpoint,
                    )
                val expectedState = if (enabled) RemoteApplicationEnabledState.ENABLED else RemoteApplicationEnabledState.DISABLED
                if (verifiedTarget.enabledState != expectedState) {
                    return@withTimeout applicationFailure(AdbError.ApplicationStateVerifyFailed, session.endpoint)
                }
                appendDiagnostic(
                    AdbOperationStage.APPLICATION_VERIFY,
                    AdbDiagnosticOutcome.SUCCEEDED,
                    "ADB_APP_STATE_VERIFIED",
                    session.endpoint,
                )
                AdbOperationResult.Success(ApplicationMutationResult.Verified(session.id, verifiedTarget))
            }
        } catch (error: TimeoutCancellationException) {
            if (dispatched) {
                if (active?.id == session.id) withContext(NonCancellable) { closeSession(session) }
                unknownMutation(session, stage)
            }
            else applicationFailure(AdbError.Timeout(stage), session.endpoint)
        } catch (error: CancellationException) {
            if (dispatched) {
                if (active?.id == session.id) withContext(NonCancellable) { closeSession(session) }
                unknownMutation(session, stage)
            } else AdbOperationResult.Cancelled
        } catch (_: Throwable) {
            if (dispatched) {
                if (active?.id == session.id) withContext(NonCancellable) { closeSession(session) }
                unknownMutation(session, stage)
            } else {
                applicationFailure(AdbError.RemoteClosed(stage), session.endpoint)
            }
        }
    }

    private suspend fun loadApplicationSnapshot(
        session: ActiveSession,
        timeout: Duration,
        remember: Boolean,
    ): AdbOperationResult<ApplicationSnapshot> {
        val userId = when (val current = currentUser(timeout)) {
            is AdbOperationResult.Success -> current.value
            is AdbOperationResult.Failure -> return current
            AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        if (mutex.withLock { active?.id } != session.id) {
            return applicationFailure(
                AdbError.ApplicationSessionInvalid(AdbOperationStage.APPLICATIONS_LIST),
                session.endpoint,
            )
        }
        val all = when (val parsed = queryPackageNames(userId, disabledOnly = false, timeout)) {
            is PackageQueryResult.Names -> parsed
            PackageQueryResult.Empty -> PackageQueryResult.Names(linkedSetOf(), emptyMap())
            PackageQueryResult.CapacityExceeded -> return applicationFailure(
                AdbError.ApplicationListCapacityExceeded,
                session.endpoint,
            )
            PackageQueryResult.Unsupported -> return applicationFailure(
                AdbError.ApplicationListUnsupported,
                session.endpoint,
            )
            is PackageQueryResult.OperationFailure -> return parsed.result
            PackageQueryResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        var degradedReason: String? = OPTIONAL_FIELDS_UNAVAILABLE_REASON
        val system = when (val parsed = querySystemPackageNames(userId, timeout)) {
            is PackageQueryResult.Names -> parsed
            PackageQueryResult.Empty -> PackageQueryResult.Names(linkedSetOf(), emptyMap())
            PackageQueryResult.CapacityExceeded -> return applicationFailure(
                AdbError.ApplicationListCapacityExceeded,
                session.endpoint,
            )
            PackageQueryResult.Unsupported -> PackageQueryResult.Names(linkedSetOf(), emptyMap()).also {
                degradedReason = "$OPTIONAL_FIELDS_UNAVAILABLE_REASON; SYSTEM_CLASSIFICATION_UNAVAILABLE"
            }
            is PackageQueryResult.OperationFailure -> return parsed.result
            PackageQueryResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        val disabled = when (val parsed = queryPackageNames(userId, disabledOnly = true, timeout)) {
            is PackageQueryResult.Names -> parsed.names
            PackageQueryResult.Empty -> linkedSetOf()
            PackageQueryResult.CapacityExceeded -> return applicationFailure(
                AdbError.ApplicationListCapacityExceeded,
                session.endpoint,
            )
            PackageQueryResult.Unsupported -> null.also {
                degradedReason = "$OPTIONAL_FIELDS_UNAVAILABLE_REASON；ROM 未提供可靠启用状态，相关操作已禁用"
            }
            is PackageQueryResult.OperationFailure -> return parsed.result
            PackageQueryResult.Cancelled -> return AdbOperationResult.Cancelled
        }
        val packageNames = linkedSetOf<String>().apply {
            addAll(all.names)
            addAll(system.names)
        }
        val applications = packageNames.map { packageName ->
            val androidUid = (all.uidsByPackage[packageName] ?: system.uidsByPackage[packageName])
                ?.takeIf { rawUid ->
                AndroidUidIdentity.fromRawUid(rawUid)?.userId == userId
            }
            val classification = ApplicationClassificationResolver.fromSystemFlag(packageName in system.names)
            RemoteApplication(
                packageName = packageName,
                userId = userId,
                enabledState = when {
                    disabled == null -> RemoteApplicationEnabledState.UNKNOWN
                    packageName in disabled -> RemoteApplicationEnabledState.DISABLED
                    else -> RemoteApplicationEnabledState.ENABLED
                },
                isSystem = classification == ApplicationClassification.SYSTEM,
                androidUid = androidUid,
                classification = classification,
            )
        }
        val snapshot = ApplicationSnapshot(
            sessionId = session.id,
            userId = userId,
            applications = applications,
            unavailableFields = ApplicationField.entries.toSet(),
            degradedReason = degradedReason,
            generation = applicationGeneration.incrementAndGet(),
        )
        if (remember && mutex.withLock { active?.id } == session.id) applicationSnapshot = snapshot
        appendDiagnostic(
            AdbOperationStage.APPLICATIONS_LIST,
            AdbDiagnosticOutcome.SUCCEEDED,
            "ADB_APP_LIST_SUCCEEDED",
            session.endpoint,
        )
        return AdbOperationResult.Success(snapshot)
    }

    private suspend fun currentUser(timeout: Duration): AdbOperationResult<Int> {
        for (command in listOf(ApplicationCommands.CURRENT_USER, ApplicationCommands.CURRENT_USER_FALLBACK)) {
            when (val result = executeShell(command, timeout)) {
                is AdbOperationResult.Success -> if (result.value.exitCode == 0) {
                    ApplicationParsers.currentUser(result.value.stdout)?.let { return AdbOperationResult.Success(it) }
                }
                is AdbOperationResult.Failure -> return restagedFailure(result, AdbOperationStage.APPLICATIONS_LIST)
                AdbOperationResult.Cancelled -> return AdbOperationResult.Cancelled
            }
        }
        return applicationFailure(AdbError.ApplicationCurrentUserUnavailable, mutex.withLock { active?.endpoint })
    }

    private suspend fun queryPackageNames(
        userId: Int,
        disabledOnly: Boolean,
        timeout: Duration,
    ): PackageQueryResult {
        repeat(2) { index ->
            val command = if (disabledOnly) {
                ApplicationCommands.listDisabledThirdParty(userId, fallback = index == 1)
            } else {
                ApplicationCommands.listThirdParty(userId, fallback = index == 1)
            }
            when (val result = executeShell(command, timeout)) {
                is AdbOperationResult.Success -> if (result.value.exitCode == 0) {
                    when (val parsed = ApplicationParsers.packageNames(result.value.stdout)) {
                        is PackageNamesParse.Success -> return PackageQueryResult.Names(
                            parsed.names,
                            parsed.uidsByPackage,
                        )
                        PackageNamesParse.Empty -> return PackageQueryResult.Empty
                        PackageNamesParse.CapacityExceeded -> return PackageQueryResult.CapacityExceeded
                        PackageNamesParse.Malformed -> Unit
                    }
                }
                is AdbOperationResult.Failure -> return PackageQueryResult.OperationFailure(
                    restagedFailure(result, AdbOperationStage.APPLICATIONS_LIST),
                )
                AdbOperationResult.Cancelled -> return PackageQueryResult.Cancelled
            }
        }
        return PackageQueryResult.Unsupported
    }

    private suspend fun querySystemPackageNames(
        userId: Int,
        timeout: Duration,
    ): PackageQueryResult {
        repeat(2) { index ->
            when (
                val result = executeShell(
                    ApplicationCommands.listSystem(userId, fallback = index == 1),
                    timeout,
                )
            ) {
                is AdbOperationResult.Success -> if (result.value.exitCode == 0) {
                    when (val parsed = ApplicationParsers.packageNames(result.value.stdout)) {
                        is PackageNamesParse.Success -> return PackageQueryResult.Names(
                            parsed.names,
                            parsed.uidsByPackage,
                        )
                        PackageNamesParse.Empty -> return PackageQueryResult.Empty
                        PackageNamesParse.CapacityExceeded -> return PackageQueryResult.CapacityExceeded
                        PackageNamesParse.Malformed -> Unit
                    }
                }
                is AdbOperationResult.Failure -> return PackageQueryResult.OperationFailure(
                    restagedFailure(result, AdbOperationStage.APPLICATIONS_LIST),
                )
                AdbOperationResult.Cancelled -> return PackageQueryResult.Cancelled
            }
        }
        return PackageQueryResult.Unsupported
    }

    private fun isAllowedApplicationTarget(
        session: ActiveSession,
        target: RemoteApplication?,
        packageName: String,
        stateMutation: Boolean,
    ): Boolean {
        if (!ApplicationParsers.isValidPackageName(packageName)) return false
        if (target == null || target.packageName != packageName || target.userId < 0) return false
        val action = if (stateMutation) {
            com.sheen.adb.core.ApplicationAction.SET_ENABLED
        } else {
            com.sheen.adb.core.ApplicationAction.FORCE_STOP
        }
        if (!ApplicationCapabilityPolicy.isAllowed(target.classification, action)) return false
        if (target.enabledState == RemoteApplicationEnabledState.UNKNOWN) return false
        if (stateMutation && target.enabledState == RemoteApplicationEnabledState.UNKNOWN) return false
        val local = session.endpoint.host.equals("127.0.0.1", true) ||
            session.endpoint.host.equals("::1", true) || session.endpoint.host.equals("localhost", true)
        return !(local && packageName == SELF_PACKAGE_NAME)
    }

    private fun unknownMutation(
        session: ActiveSession,
        stage: AdbOperationStage,
    ): AdbOperationResult.Success<ApplicationMutationResult> {
        val reason = AdbError.ApplicationOutcomeUnknown(stage)
        val state = mutableState.value
        if (active?.id != session.id) {
            mutableState.value = if (state is AdbConnectionState.Error) {
                state.copy(error = reason)
            } else {
                AdbConnectionState.Error(
                    reason,
                    "code=${reason.technicalCode}; target=${session.endpoint.redacted()}",
                )
            }
        }
        appendDiagnostic(stage, AdbDiagnosticOutcome.FAILED, reason.technicalCode, session.endpoint)
        return AdbOperationResult.Success(ApplicationMutationResult.OutcomeUnknown(session.id, reason))
    }

    private fun applicationFailure(
        error: AdbError,
        endpoint: AdbEndpoint?,
    ): AdbOperationResult.Failure {
        appendDiagnostic(error.stage, AdbDiagnosticOutcome.FAILED, error.technicalCode, endpoint)
        return AdbOperationResult.Failure(error)
    }

    private fun restagedFailure(
        failure: AdbOperationResult.Failure,
        stage: AdbOperationStage,
    ): AdbOperationResult.Failure {
        val error = when (failure.error) {
            is AdbError.NetworkUnreachable -> AdbError.NetworkUnreachable(stage)
            is AdbError.Timeout -> AdbError.Timeout(stage)
            is AdbError.AuthenticationFailed -> AdbError.AuthenticationFailed(stage)
            is AdbError.DeviceRejected -> AdbError.DeviceRejected(stage)
            is AdbError.ProtocolIncompatible -> AdbError.ProtocolIncompatible(stage)
            is AdbError.RemoteClosed -> AdbError.RemoteClosed(stage)
            is AdbError.Unknown -> AdbError.Unknown(stage)
            else -> failure.error
        }
        val state = mutableState.value
        if (state is AdbConnectionState.Error) mutableState.value = state.copy(error = error)
        return AdbOperationResult.Failure(error)
    }

    private sealed interface PackageQueryResult {
        data class Names(
            val names: LinkedHashSet<String>,
            val uidsByPackage: Map<String, Int?>,
        ) : PackageQueryResult
        data class OperationFailure(val result: AdbOperationResult.Failure) : PackageQueryResult
        data object Empty : PackageQueryResult
        data object Unsupported : PackageQueryResult
        data object CapacityExceeded : PackageQueryResult
        data object Cancelled : PackageQueryResult
    }

    override fun streamLogcat(config: LogcatConfig): Flow<AdbOperationResult<LogcatLine>> = flow {
        val session = mutex.withLock { active }
        if (session == null) {
            emit(AdbOperationResult.Failure(AdbError.RemoteClosed(AdbOperationStage.LOGCAT)))
            return@flow
        }
        val lease = when (val acquired = acquireExclusiveOperation(AdbExclusiveOperationKind.LOGCAT, session.id)) {
            is AdbOperationResult.Success -> acquired.value
            is AdbOperationResult.Failure -> {
                emit(acquired)
                return@flow
            }
            AdbOperationResult.Cancelled -> {
                emit(AdbOperationResult.Cancelled)
                return@flow
            }
        }
        val leaseCompletion = currentCoroutineContext()[Job]?.invokeOnCompletion { lease.release() }
        appendDiagnostic(AdbOperationStage.LOGCAT, AdbDiagnosticOutcome.STARTED, "ADB_LOGCAT_STARTED", session.endpoint)
        var stream: ProtocolShellStream? = null
        val stdout = LineChunkDecoder()
        val stderr = LineChunkDecoder()
        try {
            val openedStream = runInterruptible(ioDispatcher) {
                session.client.openShellStream(AdbCommands.logcat(config))
            }
            stream = openedStream
            var finished = false
            while (!finished) {
                when (val packet = runInterruptible(ioDispatcher) { openedStream.read() }) {
                    is ProtocolShellPacket.StandardOutput -> stdout.append(packet.bytes).forEach {
                        emit(AdbOperationResult.Success(LogcatLine(it)))
                    }
                    is ProtocolShellPacket.StandardError -> stderr.append(packet.bytes).forEach {
                        emit(AdbOperationResult.Success(LogcatLine(it, fromStandardError = true)))
                    }
                    is ProtocolShellPacket.Exit -> {
                        emit(
                            operationFailure(
                                AdbError.CommandStreamClosed(AdbOperationStage.LOGCAT),
                                session.endpoint,
                                null,
                            ),
                        )
                        finished = true
                    }
                }
            }
            stdout.finish().forEach { emit(AdbOperationResult.Success(LogcatLine(it))) }
            stderr.finish().forEach { emit(AdbOperationResult.Success(LogcatLine(it, true))) }
        } catch (error: CancellationException) {
            appendDiagnostic(AdbOperationStage.LOGCAT, AdbDiagnosticOutcome.CANCELLED, "ADB_LOGCAT_CANCELLED", session.endpoint)
            throw error
        } catch (error: Throwable) {
            val mapped = AdbExceptionMapper.map(error, AdbOperationStage.LOGCAT)
            emit(operationFailure(mapped, session.endpoint, error))
        } finally {
            withContext(NonCancellable + ioDispatcher) { runCatching { stream?.close() } }
            withContext(NonCancellable) { lease.release() }
            leaseCompletion?.dispose()
            appendDiagnostic(
                AdbOperationStage.LOGCAT,
                AdbDiagnosticOutcome.RESOURCE_CLOSED,
                "ADB_LOGCAT_STREAM_CLOSED",
                session.endpoint,
            )
        }
    }

    override fun streamStructuredLogcat(
        config: LogcatConfig,
        expectedSessionId: String,
        expectedProcessGeneration: Long,
    ): Flow<AdbOperationResult<StructuredLogcatRecord>> = flow {
        val snapshot = latestProcessAnalysis
        val session = mutex.withLock { active }
        if (session == null || session.id != expectedSessionId || snapshot == null ||
            snapshot.sessionId != expectedSessionId || snapshot.generation != expectedProcessGeneration
        ) {
            emit(AdbOperationResult.Failure(AdbError.RemoteClosed(AdbOperationStage.LOGCAT)))
            return@flow
        }

        var sequence = 0L
        try {
            streamLogcat(config).collect { result ->
                val stillOwned = mutex.withLock { active?.id == expectedSessionId } &&
                    latestProcessAnalysis?.let {
                        it.sessionId == expectedSessionId && it.generation == expectedProcessGeneration
                    } == true
                if (!stillOwned) throw StructuredDiagnosticOwnershipLost()
                when (result) {
                    is AdbOperationResult.Success -> {
                        val parsed = StructuredLogcatParser.parse(
                            sequence = sequence++,
                            rawText = result.value.text,
                            fromStandardError = result.value.fromStandardError,
                        )
                        emit(
                            AdbOperationResult.Success(
                                parsed.toPublicStructuredRecord(expectedSessionId, snapshot),
                            ),
                        )
                    }
                    is AdbOperationResult.Failure -> {
                        if (result.error !is AdbError.CommandStreamClosed) emit(result)
                    }
                    AdbOperationResult.Cancelled -> emit(AdbOperationResult.Cancelled)
                }
            }
        } catch (_: StructuredDiagnosticOwnershipLost) {
            Unit
        }
    }

    private fun com.sheen.adb.core.internal.diagnostics.StructuredLogcatRecord.toPublicStructuredRecord(
        sessionId: String,
        snapshot: ProcessAnalysisSnapshot,
    ): StructuredLogcatRecord {
        val processAssociation = associateStructuredRecord(snapshot)
        return StructuredLogcatRecord(
            sessionId = sessionId,
            snapshotGeneration = snapshot.generation,
            sequence = sequence,
            rawText = rawText,
            kind = when (kind) {
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatKind.PARSED -> StructuredLogcatKind.PARSED
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatKind.UNPARSED -> StructuredLogcatKind.UNPARSED
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatKind.STDERR -> StructuredLogcatKind.STDERR
            },
            timestamp = timestamp?.let {
                StructuredLogcatTimestamp(it.month, it.day, it.hour, it.minute, it.second, it.millisecond)
            },
            uid = uid,
            pid = pid,
            tid = tid,
            level = when (level) {
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.VERBOSE -> StructuredLogcatLevel.VERBOSE
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.DEBUG -> StructuredLogcatLevel.DEBUG
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.INFO -> StructuredLogcatLevel.INFO
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.WARN -> StructuredLogcatLevel.WARN
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.ERROR -> StructuredLogcatLevel.ERROR
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.FATAL -> StructuredLogcatLevel.FATAL
                com.sheen.adb.core.internal.diagnostics.StructuredLogcatLevel.ASSERT -> StructuredLogcatLevel.ASSERT
                null -> null
            },
            tag = tag,
            message = message,
            processName = processAssociation.processName,
            applicationAssociation = processAssociation.applicationAssociation,
        )
    }

    private fun com.sheen.adb.core.internal.diagnostics.StructuredLogcatRecord.associateStructuredRecord(
        snapshot: ProcessAnalysisSnapshot,
    ): ProcessRecordAssociation {
        val recordPid = pid ?: return ProcessRecordAssociation(
            processName = null,
            applicationAssociation = ProcessApplicationAssociation.Unknown(ProcessAssociationUnknownReason.NO_MATCH),
        )
        val entry = snapshot.entries.singleOrNull { it.process.pid == recordPid }
            ?: return ProcessRecordAssociation(
                processName = null,
                applicationAssociation = ProcessApplicationAssociation.Unknown(
                    ProcessAssociationUnknownReason.PROCESS_EXITED,
                ),
            )
        val logIdentity = AndroidUidIdentity.fromRawUid(uid)
            ?: return ProcessRecordAssociation(
                processName = entry.process.name,
                applicationAssociation = ProcessApplicationAssociation.Unknown(
                    ProcessAssociationUnknownReason.MISSING_UID,
                ),
            )
        val processIdentity = ProcessAssociation.parseProcessUid(entry.process.uid)
        if (processIdentity == null || processIdentity != logIdentity) {
            return ProcessRecordAssociation(
                processName = null,
                applicationAssociation = ProcessApplicationAssociation.Unknown(
                    ProcessAssociationUnknownReason.PID_REUSED,
                ),
            )
        }
        return ProcessAssociation.associatePid(snapshot, snapshot.sessionId, snapshot.generation, recordPid)
    }

    private class StructuredDiagnosticOwnershipLost : RuntimeException()

    private suspend fun claimWirelessDiscovery(mode: WirelessDiscoveryMode): ActiveWirelessDiscovery? = mutex.withLock {
        synchronized(wirelessDiscoveryLock) {
            if (closed.get() || activeWirelessDiscovery != null) return@synchronized null
            ActiveWirelessDiscovery(
                generation = wirelessDiscoveryGeneration.incrementAndGet(),
                ownerSessionId = active?.id,
                mode = mode,
            ).also { activeWirelessDiscovery = it }
        }
    }

    private fun currentLanSelection(
        target: WirelessDiscoveryTarget,
        expectedType: WirelessServiceType,
    ): WirelessServiceObservation? = synchronized(wirelessDiscoveryLock) {
        val discovery = activeWirelessDiscovery
        if (discovery != null &&
            (discovery.generation != target.generation ||
                (expectedType == WirelessServiceType.CONNECT &&
                    discovery.mode != WirelessDiscoveryMode.LAN_FOREGROUND))
        ) {
            return@synchronized null
        }
        val candidateStates = when (expectedType) {
            WirelessServiceType.CONNECT -> listOfNotNull(latestLanDiscoveryState)
            WirelessServiceType.PAIRING -> listOfNotNull(
                latestPairingDiscoveryState,
                latestLanDiscoveryState,
            )
        }
        candidateStates
            .firstOrNull { it.generation == target.generation }
            ?.services
            ?.singleOrNull {
                it.observationId == target.observationId &&
                    it.serviceType == expectedType &&
                    it.status == WirelessServiceStatus.RESOLVED &&
                    it.addresses.isNotEmpty()
            }
    }

    private fun updateLatestDiscoveryState(
        mode: WirelessDiscoveryMode,
        state: WirelessDiscoveryState,
    ) {
        when (mode) {
            WirelessDiscoveryMode.LAN_FOREGROUND -> updateLatestLanDiscoveryState(state)
            WirelessDiscoveryMode.LOCAL_PAIRING -> synchronized(wirelessDiscoveryLock) {
                val current = latestPairingDiscoveryState
                if (current == null || state.generation >= current.generation) {
                    latestPairingDiscoveryState = state
                }
            }
        }
    }

    private fun updateLatestLanDiscoveryState(state: WirelessDiscoveryState) {
        synchronized(wirelessDiscoveryLock) {
            val current = latestLanDiscoveryState
            if (current == null || state.generation >= current.generation) latestLanDiscoveryState = state
        }
    }

    private fun latestLanStateOr(generation: Long): WirelessDiscoveryState =
        synchronized(wirelessDiscoveryLock) {
            latestLanDiscoveryState?.takeIf { it.generation == generation }
                ?: WirelessDiscoveryState(generation)
        }

    private fun updateLanIdentityState(
        observation: WirelessServiceObservation,
        verifiedDeviceId: VerifiedWirelessDeviceId?,
    ): WirelessDiscoveryState {
        val current = latestLanStateOr(
            synchronized(wirelessDiscoveryLock) {
                latestLanDiscoveryState?.generation ?: wirelessDiscoveryGeneration.get()
            },
        )
        return WirelessDiscoveryReducer().withVerifiedIdentity(current, observation, verifiedDeviceId).also {
            updateLatestLanDiscoveryState(it)
        }
    }

    private fun verifiedIdentity(fingerprint: ByteArray?): VerifiedWirelessDeviceId? {
        if (fingerprint == null || fingerprint.isEmpty()) {
            fingerprint?.fill(0)
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(wirelessIdentitySalt)
            val opaque = digest.digest(fingerprint).joinToString("") { byte -> "%02x".format(byte) }
            VerifiedWirelessDeviceId(opaque)
        } finally {
            fingerprint.fill(0)
        }
    }

    private fun trimLanPairingAssociations() {
        while (lanPairingAssociations.size > MAX_LAN_PAIRING_ASSOCIATIONS) {
            val oldest = lanPairingAssociations.entries.firstOrNull()?.key ?: return
            lanPairingAssociations.remove(oldest)
        }
    }

    private fun AdbError.isRetryablePairedConnectDiscoveryFailure(): Boolean = when (this) {
        AdbError.DiscoveryConflict,
        AdbError.DiscoveryPlatformFailure,
        AdbError.DiscoveryResolutionFailed,
        AdbError.DiscoveryTimeout,
        -> true
        else -> false
    }

    private fun publishWirelessDiscoveryEvent(
        discovery: ActiveWirelessDiscovery,
        event: WirelessDiscoveryEvent,
    ) {
        val ownerChanged = synchronized(wirelessDiscoveryLock) {
            activeWirelessDiscovery === discovery && active?.id != discovery.ownerSessionId
        }
        if (ownerChanged) {
            terminateWirelessDiscovery(discovery, AdbError.DiscoverySessionChanged)
            return
        }
        val accepted = synchronized(wirelessDiscoveryLock) {
            activeWirelessDiscovery === discovery &&
                !discovery.isTerminal() &&
                event.generation == discovery.generation
        }
        if (accepted) discovery.signals.trySend(WirelessDiscoverySignal.Event(event))
    }

    private fun terminateWirelessDiscovery(
        discovery: ActiveWirelessDiscovery,
        error: AdbError,
    ): Boolean {
        val terminated = synchronized(wirelessDiscoveryLock) {
            activeWirelessDiscovery === discovery && discovery.markTerminal(error)
        }
        if (terminated || discovery.terminalError() != null) {
            completePendingWirelessDiscovery(discovery)
        }
        return terminated
    }

    private fun terminateActiveWirelessDiscovery(error: AdbError) {
        val discovery = synchronized(wirelessDiscoveryLock) {
            val current = activeWirelessDiscovery ?: return@synchronized null
            current.markTerminal(error)
            current
        }
        discovery?.let(::completePendingWirelessDiscovery)
    }

    private fun completePendingWirelessDiscovery(discovery: ActiveWirelessDiscovery): Boolean {
        val error = discovery.terminalError() ?: return false
        if (!discovery.closeSourceIfReady()) return false
        synchronized(wirelessDiscoveryLock) {
            if (activeWirelessDiscovery === discovery) activeWirelessDiscovery = null
        }
        discovery.publishTerminal(error)
        return true
    }

    private fun retireWirelessDiscovery(discovery: ActiveWirelessDiscovery) {
        if (discovery.terminalError() != null) {
            completePendingWirelessDiscovery(discovery)
            return
        }
        val ownsRetirement = synchronized(wirelessDiscoveryLock) {
            activeWirelessDiscovery === discovery && discovery.markRetired()
        }
        if (!ownsRetirement) return
        if (discovery.closeSourceIfReady()) {
            synchronized(wirelessDiscoveryLock) {
                if (activeWirelessDiscovery === discovery) activeWirelessDiscovery = null
            }
            discovery.finishRetirement()
        }
    }

    private fun <T> captureWirelessDiscoveryCall(block: () -> T): WirelessDiscoveryCallResult<T> = try {
        WirelessDiscoveryCallResult.Value(block())
    } catch (error: InterruptedException) {
        throw error
    } catch (error: CancellationException) {
        WirelessDiscoveryCallResult.Cancelled(error)
    } catch (_: Throwable) {
        WirelessDiscoveryCallResult.Failed
    }

    private fun discoveryError(failure: WirelessDiscoverySourceFailure): AdbError = when (failure) {
        WirelessDiscoverySourceFailure.NETWORK_UNAVAILABLE -> AdbError.DiscoveryNetworkUnavailable
        WirelessDiscoverySourceFailure.PERMISSION_UNAVAILABLE -> AdbError.DiscoveryPermissionUnavailable
        WirelessDiscoverySourceFailure.RESOLUTION_FAILED -> AdbError.DiscoveryResolutionFailed
        WirelessDiscoverySourceFailure.PLATFORM_FAILURE -> AdbError.DiscoveryPlatformFailure
    }

    override suspend fun disconnect(timeout: Duration): AdbOperationResult<Unit> = mutex.withLock {
        activeInteractiveShell?.let {
            closeInteractiveShellChild(it, InteractiveShellCloseReason.DISCONNECTED)
        }
        localPairingCoordinator.onSessionChanged()
        terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
        val sessionToClose = active
        appendDiagnostic(AdbOperationStage.DISCONNECT, AdbDiagnosticOutcome.STARTED, "ADB_DISCONNECT_STARTED", sessionToClose?.endpoint)
        mutableState.value = AdbConnectionState.Disconnecting
        return@withLock try {
            withTimeout(timeout) { runInterruptible(ioDispatcher) { closeActiveIfPresent() } }
            mutableState.value = AdbConnectionState.Disconnected()
            appendDiagnostic(AdbOperationStage.DISCONNECT, AdbDiagnosticOutcome.SUCCEEDED, "ADB_DISCONNECT_SUCCEEDED", null)
            AdbOperationResult.Success(Unit)
        } catch (error: TimeoutCancellationException) {
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            failure(AdbError.Timeout(AdbOperationStage.DISCONNECT), null, error)
        } catch (error: CancellationException) {
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            mutableState.value = AdbConnectionState.Disconnected(DisconnectionReason.DISCONNECT_CANCELLED)
            appendDiagnostic(AdbOperationStage.DISCONNECT, AdbDiagnosticOutcome.CANCELLED, "ADB_DISCONNECT_CANCELLED", null)
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            failure(AdbExceptionMapper.map(error, AdbOperationStage.DISCONNECT), null, error)
        } finally {
            if (sessionToClose != null) {
                invalidateExclusiveOperation(sessionToClose.id)
                if (active?.id == sessionToClose.id) active = null
                withContext(NonCancellable + ioDispatcher) { runCatching { sessionToClose.client.close() } }
            }
        }
    }

    override suspend fun clearHostIdentity(timeout: Duration): AdbOperationResult<Unit> = mutex.withLock {
        localPairingCoordinator.onSessionChanged()
        terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
        return@withLock try {
            withTimeout(timeout) {
                runInterruptible(ioDispatcher) {
                    closeActiveIfPresent()
                    clientFactory.clearIdentity()
                }
            }
            mutableState.value = AdbConnectionState.Disconnected()
            AdbOperationResult.Success(Unit)
        } catch (error: TimeoutCancellationException) {
            failure(AdbError.Timeout(AdbOperationStage.AUTHENTICATE), null, error)
        } catch (error: CancellationException) {
            AdbOperationResult.Cancelled
        } catch (error: Throwable) {
            failure(AdbExceptionMapper.map(error, AdbOperationStage.AUTHENTICATE), null, error)
        }
    }

    override fun clearDiagnosticEvents() {
        mutableDiagnosticEvents.value = emptyList()
    }

    override fun reportInvalidAddress(userMessage: String) {
        failure(AdbError.InvalidAddress(userMessage.take(200)), null, null)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            sessionHealthJob?.cancel()
            sessionHealthJob = null
            activeInteractiveShell?.let { shell ->
                activeInteractiveShell = null
                shell.closed.set(true)
                shell.readerJob?.cancel()
                runCatching { shell.protocol.closeInput() }
                runCatching { shell.protocol.closeOutput() }
                runCatching { shell.protocol.close() }
                shell.events.close()
            }
            localPairingCoordinator.close()
            localPairingScope.cancel()
            connectionProbeScope.cancel()
            qrPairingCoordinator.close()
            activeQrAttemptId = null
            terminateActiveWirelessDiscovery(AdbError.DiscoveryManagerClosed)
            val session = active
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            invalidateExclusiveOperation(session?.id)
            runCatching { session?.client?.close() }
            if (session != null) {
                appendDiagnostic(
                    AdbOperationStage.DISCONNECT,
                    AdbDiagnosticOutcome.RESOURCE_CLOSED,
                    "ADB_SESSION_CLOSED",
                    session.endpoint,
                )
            }
            mutableState.value = AdbConnectionState.Disconnected()
        }
    }

    private fun closeActiveIfPresent() {
        sessionHealthJob?.cancel()
        sessionHealthJob = null
        val session = active ?: return
        active = null
        applicationSnapshot = null
        clearApplicationMetadata()
        clearProcessAnalysis()
        invalidateExclusiveOperation(session.id)
        try {
            session.client.close()
        } finally {
            appendDiagnostic(
                AdbOperationStage.DISCONNECT,
                AdbDiagnosticOutcome.RESOURCE_CLOSED,
                "ADB_SESSION_CLOSED",
                session.endpoint,
            )
        }
    }

    private suspend fun closeSession(session: ActiveSession) {
        if (active?.id == session.id) {
            sessionHealthJob?.cancel()
            sessionHealthJob = null
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
        }
        invalidateExclusiveOperation(session.id)
        withContext(NonCancellable + ioDispatcher) { runCatching { session.client.close() } }
        appendDiagnostic(
            AdbOperationStage.DISCONNECT,
            AdbDiagnosticOutcome.RESOURCE_CLOSED,
            "ADB_SESSION_CLOSED",
            session.endpoint,
        )
    }

    private fun startSessionHealthMonitor(session: ActiveSession) {
        require(sessionHealthFailureThreshold > 0)
        sessionHealthJob?.cancel()
        sessionHealthJob = connectionProbeScope.launch {
            var consecutiveFailures = 0
            while (currentCoroutineContext().isActive) {
                delay(sessionHealthInterval)
                if (active?.id != session.id) return@launch
                val operationBusy = synchronized(exclusiveOperationLock) {
                    activeExclusiveOperation != null
                }
                if (operationBusy || activeInteractiveShell != null) continue
                val healthy = probeSessionHealth(session)
                consecutiveFailures = if (healthy) 0 else consecutiveFailures + 1
                if (consecutiveFailures < sessionHealthFailureThreshold) continue
                retireUnhealthySession(session)
                return@launch
            }
        }
    }

    private suspend fun probeSessionHealth(session: ActiveSession): Boolean {
        if (active?.id != session.id) return false
        var commandStream: ProtocolShellCommand? = null
        return try {
            val response = withTimeout(sessionHealthTimeout) {
                runInterruptible(ioDispatcher) {
                    session.client.openShellCommand(CONNECTION_PROBE)
                        .also { commandStream = it }
                        .execute()
                }
            }
            active?.id == session.id && response.exitCode == 0
        } catch (_: TimeoutCancellationException) {
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        } finally {
            withContext(NonCancellable + ioDispatcher) { runCatching { commandStream?.close() } }
        }
    }

    private suspend fun retireUnhealthySession(session: ActiveSession) {
        if (active?.id != session.id) return
        // Closing the transport first releases an in-flight feature command that may currently
        // own the session mutex. Otherwise the health monitor cannot publish Disconnected until
        // that command's full timeout expires.
        withContext(NonCancellable + ioDispatcher) { runCatching { session.client.close() } }
        mutex.withLock {
            if (active?.id != session.id) return@withLock
            sessionHealthJob = null
            active = null
            applicationSnapshot = null
            clearApplicationMetadata()
            clearProcessAnalysis()
            terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
            invalidateExclusiveOperation(session.id)
            mutableState.value = AdbConnectionState.Disconnected()
            appendDiagnostic(
                AdbOperationStage.DISCONNECT,
                AdbDiagnosticOutcome.RESOURCE_CLOSED,
                "ADB_REMOTE_SESSION_CLOSED",
                session.endpoint,
            )
        }
    }

    private suspend fun invalidateForcedTransferSession(session: ActiveSession) = mutex.withLock {
        if (active?.id != session.id) return@withLock
        active = null
        applicationSnapshot = null
        clearApplicationMetadata()
        clearProcessAnalysis()
        terminateActiveWirelessDiscovery(AdbError.DiscoverySessionChanged)
        invalidateExclusiveOperation(session.id)
        mutableState.value = AdbConnectionState.Disconnected()
        appendDiagnostic(
            AdbOperationStage.FILE_TRANSFER,
            AdbDiagnosticOutcome.RESOURCE_CLOSED,
            "ADB_TRANSFER_SESSION_FORCE_CLOSED",
            session.endpoint,
        )
    }

    private fun invalidateExclusiveOperation(sessionId: String?) {
        synchronized(exclusiveOperationLock) {
            val operation = activeExclusiveOperation ?: return
            if (sessionId == null || operation.sessionId == sessionId) {
                operation.active.set(false)
                activeExclusiveOperation = null
            }
        }
    }

    private inner class ManagerExclusiveOperationLease(
        private val operation: ActiveExclusiveOperation,
    ) : ExclusiveAdbOperationLease {
        override val token: String get() = operation.token
        override val sessionId: String get() = operation.sessionId
        override val kind: AdbExclusiveOperationKind get() = operation.kind

        override val isActive: Boolean
            get() = operation.active.get() && synchronized(exclusiveOperationLock) {
                activeExclusiveOperation === operation
            }

        override fun release() {
            if (!operation.active.compareAndSet(true, false)) return
            synchronized(exclusiveOperationLock) {
                if (activeExclusiveOperation === operation) activeExclusiveOperation = null
            }
        }
    }

    private fun failure(error: AdbError, endpoint: AdbEndpoint?, cause: Throwable?): AdbOperationResult.Failure {
        val details = cause?.let { AdbExceptionMapper.safeTechnicalDetails(it, endpoint) }
            ?: "code=${error.technicalCode}; target=${endpoint?.redacted() ?: "<无目标>"}"
        mutableState.value = AdbConnectionState.Error(error, DiagnosticRedactor.redact(details))
        appendDiagnostic(error.stage, AdbDiagnosticOutcome.FAILED, error.technicalCode, endpoint, cause)
        return AdbOperationResult.Failure(error)
    }

    private fun operationFailure(
        error: AdbError,
        endpoint: AdbEndpoint?,
        cause: Throwable?,
    ): AdbOperationResult.Failure {
        appendDiagnostic(error.stage, AdbDiagnosticOutcome.FAILED, error.technicalCode, endpoint, cause)
        return AdbOperationResult.Failure(error)
    }

    private fun appendDiagnostic(
        stage: AdbOperationStage,
        outcome: AdbDiagnosticOutcome,
        technicalCode: String,
        endpoint: AdbEndpoint?,
        cause: Throwable? = null,
    ) {
        val event = AdbDiagnosticEvent(
            sequence = diagnosticSequence.incrementAndGet(),
            stage = stage,
            outcome = outcome,
            technicalCode = technicalCode,
            redactedTarget = endpoint?.redacted() ?: "<无目标>",
            causeType = cause?.javaClass?.simpleName?.ifBlank { "Throwable" },
        )
        mutableDiagnosticEvents.update { current -> (current + event).takeLast(MAX_DIAGNOSTIC_EVENTS) }
    }

    private class ProtocolProbeException : Exception()

    private fun isLegacyAuthorization(error: Throwable, endpoint: AdbEndpoint): Boolean =
        generateSequence(error) { it.cause }.take(8).any {
            it.javaClass.simpleName == "AdbAuthException" ||
                (endpoint.port == LEGACY_ADB_TCP_PORT && it is ProtocolConnectionHandshakeTimeoutException)
        }

    private class LineChunkDecoder {
        private var pending = ""

        fun append(bytes: ByteArray): List<String> {
            val combined = pending + bytes.toString(Charsets.UTF_8)
            val parts = combined.split('\n')
            pending = parts.last()
            return parts.dropLast(1).map { it.removeSuffix("\r") }
        }

        fun finish(): List<String> = pending.takeIf(String::isNotEmpty)?.let(::listOf).orEmpty().also { pending = "" }
    }

    private companion object {
        const val INTERACTIVE_SHELL_EVENT_CAPACITY = 128
        const val CONNECTION_PROBE = "echo sheen-session-ready"
        const val MAX_DIAGNOSTIC_EVENTS = 100
        const val AUTHORIZATION_RETRY_MILLIS = 1_000L
        const val LEGACY_ADB_TCP_PORT = 5555
        val APP_PROCESS_USER_PATTERN = Regex("^u\\d+_a\\d+$")
        const val SELF_PACKAGE_NAME = "com.sheen.adbhelper"
        const val OPTIONAL_FIELDS_UNAVAILABLE_REASON =
            "设备基础包列表可用；版本号、版本名和安装器字段未通过跨 ROM 可靠性验证，已明确省略"
        val PROCESS_REFRESH_BUDGET = 30.seconds
        val PROCESS_COUNTER_SAMPLE_DELAY = 250.milliseconds
        const val MAX_CONSUMED_PROCESS_REQUESTS = 128
        val FILE_PREPARE_TIMEOUT = 5.seconds
        val APK_CLEANUP_TIMEOUT = 5.seconds
        const val DEFAULT_REMOTE_FILE_MODE = 0x81A4
        const val MAX_AUTO_RENAME_ATTEMPTS = 1_000
        const val LOCAL_PAIRING_INPUT_WINDOW_MILLIS = 120_000L
        val CONNECTION_CANCELLATION_GRACE = 100.milliseconds
        val PAIRED_CONNECT_DISCOVERY_RETRY_DELAY = 250.milliseconds
        const val MAX_LAN_PAIRING_ASSOCIATIONS = 16
        val QR_TERMINAL_PHASES = setOf(
            PairingAttemptPhase.SUCCEEDED,
            PairingAttemptPhase.CANCELLED,
            PairingAttemptPhase.EXPIRED,
            PairingAttemptPhase.FAILED,
            PairingAttemptPhase.UNSUPPORTED,
        )
    }
}
