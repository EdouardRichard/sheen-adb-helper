package com.sheen.adb.core

class PairingAttemptId private constructor(
    private val token: String,
) {
    override fun equals(other: Any?): Boolean = other is PairingAttemptId && token == other.token

    override fun hashCode(): Int = token.hashCode()

    override fun toString(): String = "PairingAttemptId(redacted)"

    companion object {
        fun of(token: String): PairingAttemptId {
            require(token.isNotBlank()) { "Pairing attempt ID must not be blank." }
            return PairingAttemptId(token)
        }

        internal fun sentinel(): PairingAttemptId = PairingAttemptId("")
    }
}

class PairingSecret(
    private val chars: CharArray,
) {
    fun clear() {
        chars.fill('\u0000')
    }

    internal fun <T> withChars(block: (CharArray) -> T): T = block(chars)

    override fun toString(): String = "PairingSecret(redacted)"
}

interface QrPairingMaterial {
    val attemptId: PairingAttemptId
    val deadlineMillis: Long
    val payload: String?
}

class LocalPairingWindowId private constructor(
    private val token: String,
) {
    override fun equals(other: Any?): Boolean = other is LocalPairingWindowId && token == other.token

    override fun hashCode(): Int = token.hashCode()

    override fun toString(): String = "LocalPairingWindowId(redacted)"

    companion object {
        fun of(token: String): LocalPairingWindowId {
            require(token.isNotBlank()) { "Local pairing window ID must not be blank." }
            return LocalPairingWindowId(token)
        }
    }
}

enum class LocalPairingNotificationState {
    HIDDEN,
    PRIVATE_LOCKED,
    INPUT_READY,
    INPUT_UNAVAILABLE,
    RESULT,
}

enum class LocalPairingNotificationCapability {
    AVAILABLE,
    PERMISSION_DENIED,
    NOTIFICATIONS_DISABLED,
    INLINE_INPUT_UNAVAILABLE,
}

enum class LocalPairingStopReason {
    SUCCEEDED,
    CANCELLED,
    SERVICE_LOST,
    SESSION_CHANGED,
    DEADLINE_REACHED,
    SYSTEM_TIMEOUT,
    FAILED,
}

enum class LocalPairingSubmissionRejection {
    TOKEN_MISMATCH,
    EXPIRED,
    DEVICE_LOCKED,
    SERVICE_UNAVAILABLE,
    INVALID_CODE,
    WINDOW_STOPPED,
}

data class LocalPairingWindow(
    val windowId: LocalPairingWindowId,
    val attemptId: PairingAttemptId,
    val method: PairingMethod = PairingMethod.SIX_DIGIT_CODE,
    val startedAtMillis: Long,
    val deadlineMillis: Long,
    val notificationState: LocalPairingNotificationState = LocalPairingNotificationState.HIDDEN,
    val hasLivePairingService: Boolean = false,
    val stopReason: LocalPairingStopReason? = null,
) {
    init {
        require(method == PairingMethod.SIX_DIGIT_CODE) { "Local pairing must use the six digit code method." }
        require(startedAtMillis >= 0L && deadlineMillis >= 0L) {
            "Local pairing monotonic times must not be negative."
        }
        require(deadlineMillis >= startedAtMillis) { "Local pairing deadline must not precede its start." }
        require(deadlineMillis - startedAtMillis <= MAX_LOCAL_PAIRING_WINDOW_MILLIS) {
            "Local pairing window must not exceed two minutes."
        }
    }

    override fun toString(): String =
        "LocalPairingWindow(windowId=$windowId, attemptId=$attemptId, method=$method, " +
            "startedAtMillis=$startedAtMillis, deadlineMillis=$deadlineMillis, " +
            "notificationState=$notificationState, hasLivePairingService=$hasLivePairingService, " +
            "stopReason=$stopReason)"
}

data class LocalPairingNotificationDecision(
    val state: LocalPairingNotificationState,
    val inputActionAvailable: Boolean,
    val submitAllowed: Boolean,
    val actionWindowId: LocalPairingWindowId?,
    val applicationInputAvailable: Boolean,
    val suggestNativeNotificationStyle: Boolean,
    val stopReason: LocalPairingStopReason? = null,
)

sealed interface LocalPairingSubmissionDecision {
    class Accepted(
        val secret: PairingSecret,
    ) : LocalPairingSubmissionDecision {
        override fun toString(): String = "LocalPairingSubmissionDecision.Accepted(redacted)"
    }

    data class Rejected(
        val reason: LocalPairingSubmissionRejection,
    ) : LocalPairingSubmissionDecision
}

enum class PairingMethod {
    NONE,
    QR,
    SIX_DIGIT_CODE,
}

enum class PairingAttemptPhase {
    IDLE,
    PREPARING,
    WAITING_FOR_TARGET,
    WAITING_FOR_CODE,
    PAIRING,
    SUCCEEDED,
    CANCELLED,
    EXPIRED,
    FAILED,
    UNSUPPORTED,
}

enum class PairingFailure {
    NO_ACTIVE_ATTEMPT,
    CANCELLED,
    EXPIRED,
    EXPLICIT_FAILURE,
    ACTION_FAILED,
    UNSUPPORTED,
}

enum class PairingCommandRejection {
    NO_ACTIVE_ATTEMPT,
    CLOSED,
    INVALID_CODE,
    INVALID_PHASE,
    NOT_EXPIRED,
    STALE_ATTEMPT,
    TERMINAL_ATTEMPT,
    ATTEMPT_ID_REUSED,
    ACTIVE_ATTEMPT_EXISTS,
}

data class PairingAttemptState(
    val attemptId: PairingAttemptId,
    val method: PairingMethod,
    val phase: PairingAttemptPhase,
    val deadlineMillis: Long,
    val failure: PairingFailure? = null,
)

data class PairingCommandResult(
    val state: PairingAttemptState,
    val rejection: PairingCommandRejection? = null,
)

private const val MAX_LOCAL_PAIRING_WINDOW_MILLIS = 120_000L
