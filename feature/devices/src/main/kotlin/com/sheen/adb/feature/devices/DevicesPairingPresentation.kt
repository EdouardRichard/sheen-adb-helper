package com.sheen.adb.feature.devices

import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingMethod
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
        title = if (language == UiLanguage.ZH_CN) "无线调试配对" else "Wireless debugging pairing",
        guidance = when (method) {
            PairingMethod.QR -> if (language == UiLanguage.ZH_CN) "在被控端打开系统“无线调试”，由被控端系统扫描这里显示的临时二维码。" else "Open Wireless debugging on the controlled device and scan this temporary QR code."
            PairingMethod.SIX_DIGIT_CODE -> if (language == UiLanguage.ZH_CN) "请开启无线调试。已检测到配对端口，请输入配对码：" else "Enter the 6-digit pairing code shown by the controlled device."
            PairingMethod.NONE -> if (language == UiLanguage.ZH_CN) "选择二维码或 6 位配对码方式；配对成功只建立授权，之后仍需确认连接。" else "Choose QR code or a 6-digit code. Pairing authorizes; connection still requires confirmation."
        },
        statusText = when {
            invalidCode -> if (language == UiLanguage.ZH_CN) "配对码必须是 6 位数字" else "The pairing code must contain 6 digits"
            isLocalMode && phase == PairingAttemptPhase.CANCELLED -> if (language == UiLanguage.ZH_CN) "本机配对已取消" else "Local pairing cancelled"
            isLocalMode && phase == PairingAttemptPhase.EXPIRED -> if (language == UiLanguage.ZH_CN) "本机配对已超时，请重新开始" else "Local pairing timed out. Start again."
            else -> phase.statusText(method, language)
        },
        showQrMatrix = method == PairingMethod.QR &&
            phase == PairingAttemptPhase.WAITING_FOR_TARGET &&
            qrMatrix != null,
        showCodeInputs = method == PairingMethod.SIX_DIGIT_CODE &&
            phase == PairingAttemptPhase.WAITING_FOR_CODE,
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
        sessionReplacementText = if (language == UiLanguage.ZH_CN) "开始新配对前必须先断开当前 ADB Session。是否继续？" else "The current ADB session must disconnect before pairing. Continue?",
    )
}

private fun PairingAttemptPhase.statusText(method: PairingMethod, language: UiLanguage): String =
    if (language == UiLanguage.EN_US) when (this) {
        PairingAttemptPhase.IDLE -> when (method) {
            PairingMethod.NONE -> "Choose a pairing method"
            PairingMethod.QR -> "QR pairing selected"
            PairingMethod.SIX_DIGIT_CODE -> "6-digit code selected"
        }
        PairingAttemptPhase.PREPARING -> "Preparing a temporary QR code"
        PairingAttemptPhase.WAITING_FOR_TARGET -> "Waiting for the controlled device to scan"
        PairingAttemptPhase.WAITING_FOR_CODE -> "Waiting for a 6-digit code"
        PairingAttemptPhase.PAIRING -> "Pairing"
        PairingAttemptPhase.SUCCEEDED -> "Pairing succeeded; confirm the connection next"
        PairingAttemptPhase.CANCELLED -> "Pairing cancelled"
        PairingAttemptPhase.EXPIRED -> "Pairing timed out. Start again."
        PairingAttemptPhase.FAILED -> "Pairing failed. Try again."
        PairingAttemptPhase.UNSUPPORTED -> "QR pairing is not supported"
    } else when (this) {
    PairingAttemptPhase.IDLE -> when (method) {
        PairingMethod.NONE -> "请选择配对方式"
        PairingMethod.QR -> "已选择二维码配对"
        PairingMethod.SIX_DIGIT_CODE -> "已选择 6 位配对码"
    }
    PairingAttemptPhase.PREPARING -> "正在准备临时二维码"
    PairingAttemptPhase.WAITING_FOR_TARGET -> "等待被控端系统扫描"
    PairingAttemptPhase.WAITING_FOR_CODE -> "等待输入 6 位配对码"
    PairingAttemptPhase.PAIRING -> "正在配对"
    PairingAttemptPhase.SUCCEEDED -> "配对成功，授权已建立；连接设备仍需用户确认"
    PairingAttemptPhase.CANCELLED -> "配对已取消"
    PairingAttemptPhase.EXPIRED -> "配对已过期，请重新开始"
    PairingAttemptPhase.FAILED -> "配对失败，请重试"
    PairingAttemptPhase.UNSUPPORTED -> "当前系统不支持二维码配对"
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
