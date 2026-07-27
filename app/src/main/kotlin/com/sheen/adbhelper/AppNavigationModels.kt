package com.sheen.adbhelper

enum class MainDestination {
    CONNECTION,
    FILES,
    APPLICATIONS,
    PROCESSES,
    SHELL,
    LOGCAT,
}

enum class PageTransition {
    IDLE,
    FADING_OUT,
    FADING_IN,
}

enum class PageLoadNavigationPhase {
    IDLE,
    WAITING,
    RECOVERING,
}

enum class PageLoadNavigationEffect {
    NONE,
    CANCEL_CURRENT_LOAD,
    NAVIGATE,
}

data class PageLoadNavigationGateState(
    val phase: PageLoadNavigationPhase = PageLoadNavigationPhase.IDLE,
    val owner: MainDestination? = null,
    val pendingTarget: MainDestination? = null,
    val waitStartedAtMillis: Long? = null,
    val recoveryStartedAtMillis: Long? = null,
)

data class PageLoadNavigationTransition(
    val state: PageLoadNavigationGateState,
    val effect: PageLoadNavigationEffect,
    val target: MainDestination? = null,
)

sealed interface RootOverlayState {
    data class Pairing(val attemptId: String) : RootOverlayState
    data class OperationConfirmation(val confirmationId: String) : RootOverlayState
    data class LongTaskBackConfirmation(val taskId: String) : RootOverlayState
}

data class PageHostState(
    val current: MainDestination = MainDestination.CONNECTION,
    val pending: MainDestination? = null,
    val transition: PageTransition = PageTransition.IDLE,
    val foreground: Boolean = true,
    val overlay: RootOverlayState? = null,
    val navigationLock: NavigationLock? = null,
)

enum class DeliveryKind {
    FILE_UPLOAD,
    FILE_DOWNLOAD,
    APK_EXTRACTION,
    APK_INSTALLATION,
    LOGCAT_SAVE,
    SCREENSHOT_SAVE,
    SCREEN_RECORDING_SAVE_OR_EXPORT,
}

enum class DeliveryPhase {
    AWAITING_SELECTION,
    PREPARING,
    TRANSFERRING,
    WRITING,
    VERIFYING,
    CANCELLING,
    CLEANING,
    TERMINAL,
}

enum class DeliveryResult {
    COMPLETE,
    PARTIAL,
    FAILED,
    CANCELLED,
    UNKNOWN,
}

enum class DeliveryResourceState {
    OWNED,
    RELEASING,
    RELEASED,
    UNCERTAIN,
}

data class DeliveryProgress(
    val completed: Long,
    val total: Long?,
) {
    init {
        require(completed >= 0)
        require(total == null || total >= completed)
    }
}

data class DeliveryIoState(
    val owner: MainDestination,
    val taskId: String,
    val sessionId: String,
    val kind: DeliveryKind,
    val phase: DeliveryPhase,
    val result: DeliveryResult? = null,
    val resourceState: DeliveryResourceState,
    val progress: DeliveryProgress? = null,
    val cancelAvailable: Boolean = true,
    val cleanupConfirmed: Boolean = false,
) {
    init {
        require(taskId.isNotBlank())
        require(sessionId.isNotBlank())
        require((phase == DeliveryPhase.TERMINAL) == (result != null)) {
            "Only terminal delivery states carry a business result"
        }
    }
}

data class NavigationLock(
    val owner: MainDestination,
    val taskId: String,
    val sessionId: String,
    val kind: DeliveryKind,
    val phase: DeliveryPhase,
    val result: DeliveryResult?,
    val resourceState: DeliveryResourceState,
    val progress: DeliveryProgress?,
    val cancelAvailable: Boolean,
    val cleanupConfirmed: Boolean,
)

fun DeliveryIoState.toNavigationLockOrNull(): NavigationLock? {
    val ioPhase = when (phase) {
        DeliveryPhase.AWAITING_SELECTION, DeliveryPhase.PREPARING -> null
        else -> phase
    }
    if (ioPhase == null) return null

    val safelyTerminal = phase == DeliveryPhase.TERMINAL &&
        result in setOf(
            DeliveryResult.COMPLETE,
            DeliveryResult.PARTIAL,
            DeliveryResult.FAILED,
            DeliveryResult.CANCELLED,
        ) &&
        resourceState == DeliveryResourceState.RELEASED &&
        cleanupConfirmed
    if (safelyTerminal) return null

    return NavigationLock(
        owner = owner,
        taskId = taskId,
        sessionId = sessionId,
        kind = kind,
        phase = phase,
        result = result,
        resourceState = resourceState,
        progress = progress,
        cancelAvailable = cancelAvailable,
        cleanupConfirmed = cleanupConfirmed,
    )
}
