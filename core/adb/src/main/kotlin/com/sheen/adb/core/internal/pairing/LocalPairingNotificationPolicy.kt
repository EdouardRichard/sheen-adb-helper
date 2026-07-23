package com.sheen.adb.core.internal.pairing

import com.sheen.adb.core.LocalPairingNotificationCapability
import com.sheen.adb.core.LocalPairingNotificationDecision
import com.sheen.adb.core.LocalPairingNotificationState
import com.sheen.adb.core.LocalPairingStopReason
import com.sheen.adb.core.LocalPairingSubmissionDecision
import com.sheen.adb.core.LocalPairingSubmissionRejection
import com.sheen.adb.core.LocalPairingWindow
import com.sheen.adb.core.LocalPairingWindowId
import com.sheen.adb.core.PairingSecret

internal class LocalPairingNotificationPolicy {
    fun decide(
        window: LocalPairingWindow,
        nowMillis: Long,
        deviceUnlocked: Boolean,
        capability: LocalPairingNotificationCapability,
    ): LocalPairingNotificationDecision {
        val stopReason = window.stopReason
            ?: LocalPairingStopReason.DEADLINE_REACHED.takeIf { nowMillis >= window.deadlineMillis }
        if (stopReason != null) return terminalDecision(stopReason)

        if (capability != LocalPairingNotificationCapability.AVAILABLE) {
            return LocalPairingNotificationDecision(
                state = LocalPairingNotificationState.INPUT_UNAVAILABLE,
                inputActionAvailable = false,
                submitAllowed = false,
                actionWindowId = null,
                applicationInputAvailable = true,
                suggestNativeNotificationStyle =
                    capability == LocalPairingNotificationCapability.INLINE_INPUT_UNAVAILABLE,
            )
        }
        if (!deviceUnlocked) {
            return LocalPairingNotificationDecision(
                state = LocalPairingNotificationState.PRIVATE_LOCKED,
                inputActionAvailable = false,
                submitAllowed = false,
                actionWindowId = null,
                applicationInputAvailable = true,
                suggestNativeNotificationStyle = false,
            )
        }
        if (!window.hasLivePairingService) {
            return LocalPairingNotificationDecision(
                state = LocalPairingNotificationState.HIDDEN,
                inputActionAvailable = false,
                submitAllowed = false,
                actionWindowId = null,
                applicationInputAvailable = true,
                suggestNativeNotificationStyle = false,
            )
        }
        return LocalPairingNotificationDecision(
            state = LocalPairingNotificationState.INPUT_READY,
            inputActionAvailable = true,
            submitAllowed = true,
            actionWindowId = window.windowId,
            applicationInputAvailable = true,
            suggestNativeNotificationStyle = false,
        )
    }

    fun validateSubmission(
        window: LocalPairingWindow,
        submittedWindowId: LocalPairingWindowId,
        nowMillis: Long,
        deviceUnlocked: Boolean,
        code: CharArray,
    ): LocalPairingSubmissionDecision {
        val rejection = when {
            window.stopReason != null -> LocalPairingSubmissionRejection.WINDOW_STOPPED
            submittedWindowId != window.windowId -> LocalPairingSubmissionRejection.TOKEN_MISMATCH
            nowMillis >= window.deadlineMillis -> LocalPairingSubmissionRejection.EXPIRED
            !deviceUnlocked -> LocalPairingSubmissionRejection.DEVICE_LOCKED
            !window.hasLivePairingService -> LocalPairingSubmissionRejection.SERVICE_UNAVAILABLE
            code.size != SIX_DIGIT_CODE_LENGTH || !code.all { it in '0'..'9' } -> {
                LocalPairingSubmissionRejection.INVALID_CODE
            }
            else -> null
        }
        if (rejection != null) {
            code.fill('\u0000')
            return LocalPairingSubmissionDecision.Rejected(rejection)
        }
        return LocalPairingSubmissionDecision.Accepted(PairingSecret(code))
    }

    private fun terminalDecision(reason: LocalPairingStopReason): LocalPairingNotificationDecision =
        LocalPairingNotificationDecision(
            state = LocalPairingNotificationState.RESULT,
            inputActionAvailable = false,
            submitAllowed = false,
            actionWindowId = null,
            applicationInputAvailable = false,
            suggestNativeNotificationStyle = false,
            stopReason = reason,
        )

    private companion object {
        const val SIX_DIGIT_CODE_LENGTH = 6
    }
}
