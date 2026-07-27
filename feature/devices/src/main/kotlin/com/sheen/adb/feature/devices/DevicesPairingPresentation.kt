package com.sheen.adb.feature.devices

import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.LocalPairingDiscoveryStatus
import com.sheen.adb.ui.UiLanguage

internal data class DevicesPairingPresentation(
    val methodOptions: List<PairingMethod>,
    val title: String,
    val guidance: String,
    val statusText: String,
    val showQrMatrix: Boolean,
    val showCodeInputs: Boolean,
    val showStart: Boolean,
    val submitCodeEnabled: Boolean,
    val showCancel: Boolean,
    val showRetry: Boolean,
    val showCodeFallback: Boolean,
    val showSessionReplacementConfirmation: Boolean,
    val sessionReplacementText: String,
)

internal fun DevicesPairingState.toPresentation(
    language: UiLanguage = UiLanguage.ZH_CN,
): DevicesPairingPresentation {
    val invalidCode = failure == DevicesPairingFailure.INVALID_CODE
    return DevicesPairingPresentation(
        methodOptions = listOf(PairingMethod.QR, PairingMethod.SIX_DIGIT_CODE),
        title = DevicesStrings.text(
            language,
            when {
                isLocalMode -> DevicesStringKey.LOCAL_PAIRING_TITLE
                method == PairingMethod.QR -> DevicesStringKey.PAIRING_QR_TITLE
                else -> DevicesStringKey.PAIRING_CODE_TITLE
            },
        ),
        guidance = when (method) {
            PairingMethod.QR -> DevicesStrings.text(language, DevicesStringKey.PAIRING_QR_INSTRUCTION)
            PairingMethod.SIX_DIGIT_CODE -> DevicesStrings.text(
                language,
                DevicesStringKey.LOCAL_PAIRING_INSTRUCTION,
            )
            PairingMethod.NONE -> DevicesStrings.text(language, DevicesStringKey.PAIRING_CONFIRMATION)
        },
        statusText = when {
            invalidCode -> DevicesStrings.text(language, DevicesStringKey.PAIRING_INVALID_CODE)
            localDiscoveryStatus == LocalPairingDiscoveryStatus.SEARCHING ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_PORT_SCANNING)
            localDiscoveryStatus == LocalPairingDiscoveryStatus.FOUND ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_PORT_FOUND)
            phase == PairingAttemptPhase.EXPIRED &&
                localDiscoveryStatus == LocalPairingDiscoveryStatus.STOPPED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_PORT_NOT_FOUND)
            phase == PairingAttemptPhase.PAIRING ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_SUBMITTING)
            phase == PairingAttemptPhase.SUCCEEDED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_SUCCEEDED_DETAIL)
            phase == PairingAttemptPhase.UNSUPPORTED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_UNSUPPORTED_QR)
            phase == PairingAttemptPhase.FAILED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_FAILED_RETRY)
            phase == PairingAttemptPhase.EXPIRED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_EXPIRED)
            phase == PairingAttemptPhase.CANCELLED && isLocalMode ->
                DevicesStrings.text(language, DevicesStringKey.LOCAL_PAIRING_CANCELLED)
            phase == PairingAttemptPhase.CANCELLED ->
                DevicesStrings.text(language, DevicesStringKey.PAIRING_CANCELLED)
            else -> DevicesStrings.text(language, DevicesStringKey.PAIRING_CHOOSE_METHOD)
        },
        showQrMatrix = method == PairingMethod.QR &&
            phase == PairingAttemptPhase.WAITING_FOR_TARGET &&
            qrMatrix != null,
        showCodeInputs = method == PairingMethod.SIX_DIGIT_CODE &&
            phase == PairingAttemptPhase.WAITING_FOR_CODE &&
            (!isLocalMode || localDiscoveryStatus == LocalPairingDiscoveryStatus.FOUND),
        showStart = method != PairingMethod.NONE &&
            phase == PairingAttemptPhase.IDLE &&
            !awaitingSessionReplacementConfirmation,
        submitCodeEnabled = method == PairingMethod.SIX_DIGIT_CODE &&
            phase == PairingAttemptPhase.WAITING_FOR_CODE &&
            codeInput.length == SIX_DIGIT_CODE_LENGTH &&
            codeInput.all { it in '0'..'9' },
        showCancel = awaitingSessionReplacementConfirmation || phase in ACTIVE_PHASES,
        showRetry = phase in RETRYABLE_PHASES,
        showCodeFallback = method == PairingMethod.QR && codeFallbackAvailable,
        showSessionReplacementConfirmation = awaitingSessionReplacementConfirmation,
        sessionReplacementText = DevicesStrings.text(
            language,
            DevicesStringKey.SESSION_REPLACEMENT_CONFIRMATION,
        ),
    )
}

private const val SIX_DIGIT_CODE_LENGTH = 6

private val ACTIVE_PHASES = setOf(
    PairingAttemptPhase.PREPARING,
    PairingAttemptPhase.WAITING_FOR_TARGET,
    PairingAttemptPhase.WAITING_FOR_CODE,
    PairingAttemptPhase.PAIRING,
)

private val RETRYABLE_PHASES = setOf(
    PairingAttemptPhase.FAILED,
    PairingAttemptPhase.CANCELLED,
    PairingAttemptPhase.EXPIRED,
)
