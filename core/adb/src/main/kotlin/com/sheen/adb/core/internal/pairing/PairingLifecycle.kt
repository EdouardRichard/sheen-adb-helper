package com.sheen.adb.core.internal.pairing

import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingAttemptState
import com.sheen.adb.core.PairingCommandRejection
import com.sheen.adb.core.PairingCommandResult
import com.sheen.adb.core.PairingFailure
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.PairingSecret
import java.util.concurrent.CancellationException

internal fun interface MonotonicClock {
    fun nowMillis(): Long
}

internal fun interface PairingAction {
    fun pair(method: PairingMethod, secret: CharArray)
}

internal class PairingLifecycle(
    private val clock: MonotonicClock,
    private val action: PairingAction,
) : AutoCloseable {
    private val lock = Any()
    private val usedIds = mutableSetOf<PairingAttemptId>()
    private var current: PairingAttemptState? = null
    private var retainedSecret: PairingSecret? = null
    private var closed = false

    fun startQr(
        attemptId: PairingAttemptId,
        password: PairingSecret,
        deadlineMillis: Long,
    ): PairingCommandResult = synchronized(lock) {
        start(attemptId, PairingMethod.QR, PairingAttemptPhase.PREPARING, deadlineMillis, password)
    }

    fun startSixDigit(
        attemptId: PairingAttemptId,
        deadlineMillis: Long,
    ): PairingCommandResult = synchronized(lock) {
        start(attemptId, PairingMethod.SIX_DIGIT_CODE, PairingAttemptPhase.WAITING_FOR_CODE, deadlineMillis, null)
    }

    fun awaitTarget(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        val state = matchingState(attemptId) ?: return@synchronized unmatchedCommand()
        if (isTerminal(state)) return@synchronized rejected(PairingCommandRejection.TERMINAL_ATTEMPT)
        if (isExpired(state)) return@synchronized terminal(PairingAttemptPhase.EXPIRED, PairingFailure.EXPIRED)
        if (state.method != PairingMethod.QR ||
            (state.phase != PairingAttemptPhase.PREPARING && state.phase != PairingAttemptPhase.WAITING_FOR_TARGET)
        ) {
            return@synchronized rejected(PairingCommandRejection.INVALID_PHASE)
        }
        if (state.phase == PairingAttemptPhase.PREPARING) {
            current = state.copy(phase = PairingAttemptPhase.WAITING_FOR_TARGET)
        }
        accepted()
    }

    fun onTargetReady(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        val state = matchingState(attemptId) ?: return@synchronized unmatchedCommand()
        if (isTerminal(state)) return@synchronized rejected(PairingCommandRejection.TERMINAL_ATTEMPT)
        if (isExpired(state)) return@synchronized terminal(PairingAttemptPhase.EXPIRED, PairingFailure.EXPIRED)
        if (state.method != PairingMethod.QR || state.phase != PairingAttemptPhase.WAITING_FOR_TARGET) {
            return@synchronized rejected(PairingCommandRejection.INVALID_PHASE)
        }
        val secret = retainedSecret ?: return@synchronized terminal(PairingAttemptPhase.FAILED, PairingFailure.ACTION_FAILED)
        current = state.copy(phase = PairingAttemptPhase.PAIRING)
        try {
            secret.withChars { chars -> action.pair(PairingMethod.QR, chars) }
            terminal(PairingAttemptPhase.SUCCEEDED, null)
        } catch (error: CancellationException) {
            terminal(PairingAttemptPhase.CANCELLED, PairingFailure.CANCELLED)
            throw error
        } catch (_: Exception) {
            terminal(PairingAttemptPhase.FAILED, PairingFailure.ACTION_FAILED)
        } finally {
            secret.clear()
            if (retainedSecret === secret) retainedSecret = null
        }
    }

    fun submitCode(attemptId: PairingAttemptId, code: CharArray): PairingCommandResult = synchronized(lock) {
        try {
            val state = matchingState(attemptId) ?: return@synchronized unmatchedCommand()
            if (isTerminal(state)) return@synchronized rejected(PairingCommandRejection.TERMINAL_ATTEMPT)
            if (isExpired(state)) return@synchronized terminal(PairingAttemptPhase.EXPIRED, PairingFailure.EXPIRED)
            if (state.method != PairingMethod.SIX_DIGIT_CODE || state.phase != PairingAttemptPhase.WAITING_FOR_CODE) {
                return@synchronized rejected(PairingCommandRejection.INVALID_PHASE)
            }
            if (!isSixAsciiDigits(code)) return@synchronized rejected(PairingCommandRejection.INVALID_CODE)

            current = state.copy(phase = PairingAttemptPhase.PAIRING)
            try {
                action.pair(PairingMethod.SIX_DIGIT_CODE, code)
                terminal(PairingAttemptPhase.SUCCEEDED, null)
            } catch (error: CancellationException) {
                terminal(PairingAttemptPhase.CANCELLED, PairingFailure.CANCELLED)
                throw error
            } catch (_: Exception) {
                terminal(PairingAttemptPhase.FAILED, PairingFailure.ACTION_FAILED)
            }
        } finally {
            code.fill('\u0000')
        }
    }

    fun cancel(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        terminalCommand(attemptId, PairingAttemptPhase.CANCELLED, PairingFailure.CANCELLED)
    }

    fun fail(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        terminalCommand(attemptId, PairingAttemptPhase.FAILED, PairingFailure.EXPLICIT_FAILURE)
    }

    fun markUnsupported(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        terminalCommand(attemptId, PairingAttemptPhase.UNSUPPORTED, PairingFailure.UNSUPPORTED)
    }

    fun expire(attemptId: PairingAttemptId): PairingCommandResult = synchronized(lock) {
        val state = matchingState(attemptId) ?: return@synchronized unmatchedCommand()
        when {
            isTerminal(state) -> accepted()
            !isExpired(state) -> rejected(PairingCommandRejection.NOT_EXPIRED)
            else -> terminal(PairingAttemptPhase.EXPIRED, PairingFailure.EXPIRED)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            retainedSecret?.clear()
            retainedSecret = null
            current = null
            usedIds.clear()
            closed = true
        }
    }

    private fun start(
        attemptId: PairingAttemptId,
        method: PairingMethod,
        initialPhase: PairingAttemptPhase,
        deadlineMillis: Long,
        secret: PairingSecret?,
    ): PairingCommandResult {
        if (closed) {
            secret?.clear()
            return rejected(PairingCommandRejection.CLOSED)
        }
        if (attemptId in usedIds) {
            secret?.clear()
            return rejected(PairingCommandRejection.ATTEMPT_ID_REUSED)
        }
        val state = current
        if (state != null && !isTerminal(state)) {
            secret?.clear()
            return rejected(PairingCommandRejection.ACTIVE_ATTEMPT_EXISTS)
        }
        usedIds += attemptId
        val newState = PairingAttemptState(attemptId, method, initialPhase, deadlineMillis)
        current = newState
        if (isExpired(newState)) {
            secret?.clear()
            return terminal(PairingAttemptPhase.EXPIRED, PairingFailure.EXPIRED)
        }
        retainedSecret = secret
        return accepted()
    }

    private fun terminalCommand(
        attemptId: PairingAttemptId,
        phase: PairingAttemptPhase,
        failure: PairingFailure,
    ): PairingCommandResult {
        val state = matchingState(attemptId) ?: return unmatchedCommand()
        return if (isTerminal(state)) accepted() else terminal(phase, failure)
    }

    private fun terminal(phase: PairingAttemptPhase, failure: PairingFailure?): PairingCommandResult {
        val state = current ?: return rejected(PairingCommandRejection.NO_ACTIVE_ATTEMPT)
        retainedSecret?.clear()
        retainedSecret = null
        current = state.copy(phase = phase, failure = failure)
        return accepted()
    }

    private fun matchingState(attemptId: PairingAttemptId): PairingAttemptState? =
        current?.takeIf { it.attemptId == attemptId }

    private fun unmatchedCommand(): PairingCommandResult = when {
        closed -> rejected(PairingCommandRejection.CLOSED)
        current == null -> rejected(PairingCommandRejection.NO_ACTIVE_ATTEMPT)
        else -> rejected(PairingCommandRejection.STALE_ATTEMPT)
    }

    private fun accepted(): PairingCommandResult = PairingCommandResult(current ?: IDLE_STATE)

    private fun rejected(rejection: PairingCommandRejection): PairingCommandResult =
        PairingCommandResult(current ?: IDLE_STATE, rejection)

    private fun isExpired(state: PairingAttemptState): Boolean = clock.nowMillis() >= state.deadlineMillis

    private fun isTerminal(state: PairingAttemptState): Boolean = state.phase in TERMINAL_PHASES

    private fun isSixAsciiDigits(code: CharArray): Boolean =
        code.size == SIX_DIGIT_CODE_LENGTH && code.all { it in '0'..'9' }

    private companion object {
        const val SIX_DIGIT_CODE_LENGTH = 6
        val IDLE_STATE = PairingAttemptState(
            attemptId = PairingAttemptId.sentinel(),
            method = PairingMethod.NONE,
            phase = PairingAttemptPhase.IDLE,
            deadlineMillis = 0,
            failure = PairingFailure.NO_ACTIVE_ATTEMPT,
        )
        val TERMINAL_PHASES = setOf(
            PairingAttemptPhase.SUCCEEDED,
            PairingAttemptPhase.CANCELLED,
            PairingAttemptPhase.EXPIRED,
            PairingAttemptPhase.FAILED,
            PairingAttemptPhase.UNSUPPORTED,
        )
    }
}
