package com.sheen.adb.feature.devices

import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingMethod
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

internal class DevicesPairingPresentationTest {
    @Test
    fun `method selector always offers QR and six digit code`() {
        val presentation = DevicesPairingState().toPresentation()

        assertEquals(
            presentation.methodOptions,
            listOf(PairingMethod.QR, PairingMethod.SIX_DIGIT_CODE),
        )
        assertEquals(presentation.statusText, "请选择配对方式")
        assertFalse(presentation.showQrMatrix)
        assertFalse(presentation.showCodeInputs)
    }

    @Test
    fun `QR guidance directs the controlled device system to scan without camera wording`() {
        val presentation = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.WAITING_FOR_TARGET,
            qrMatrix = QrMatrix(1, booleanArrayOf(true)),
        ).toPresentation()

        assertTrue(presentation.guidance.contains("由被控端系统扫描"))
        assertFalse(presentation.guidance.contains("主控端扫描"))
        assertFalse(presentation.guidance.contains("相机"))
        assertTrue(presentation.showQrMatrix)
        assertTrue(presentation.showCancel)
        assertFalse(presentation.showCodeInputs)
    }

    @Test
    fun `six digit waiting state exposes endpoint and protected code inputs`() {
        val presentation = DevicesPairingState(
            method = PairingMethod.SIX_DIGIT_CODE,
            phase = PairingAttemptPhase.WAITING_FOR_CODE,
        ).toPresentation()

        assertTrue(presentation.showCodeInputs)
        assertTrue(presentation.submitCodeEnabled.not())
        assertTrue(presentation.guidance.contains("被控端显示的 6 位配对码"))

        val ready = DevicesPairingState(
            method = PairingMethod.SIX_DIGIT_CODE,
            phase = PairingAttemptPhase.WAITING_FOR_CODE,
            codeInput = "0".repeat(6),
        ).toPresentation()
        assertTrue(ready.submitCodeEnabled)
    }

    @Test
    fun `unsupported QR is distinct and offers code fallback`() {
        val presentation = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.UNSUPPORTED,
            failure = DevicesPairingFailure.UNSUPPORTED,
            codeFallbackAvailable = true,
        ).toPresentation()

        assertEquals(presentation.statusText, "当前系统不支持二维码配对")
        assertTrue(presentation.showCodeFallback)
        assertFalse(presentation.showQrMatrix)
        assertFalse(presentation.showRetry)
    }

    @Test(dataProvider = "terminalStates")
    fun `terminal state has explicit safe wording and no sensitive controls`(
        phase: PairingAttemptPhase,
        failure: DevicesPairingFailure?,
        expectedStatus: String,
        retry: Boolean,
    ) {
        val presentation = DevicesPairingState(
            method = PairingMethod.QR,
            phase = phase,
            codeInput = "0".repeat(6),
            qrMatrix = QrMatrix(1, booleanArrayOf(true)),
            failure = failure,
        ).toPresentation()

        assertEquals(presentation.statusText, expectedStatus)
        assertEquals(presentation.showRetry, retry)
        assertFalse(presentation.showQrMatrix)
        assertFalse(presentation.showCodeInputs)
        assertFalse(presentation.showCancel)
        assertFalse(presentation.toString().contains("000000"))
    }

    @DataProvider
    fun terminalStates(): Array<Array<Any?>> = arrayOf(
        arrayOf(PairingAttemptPhase.SUCCEEDED, null, "配对成功，授权已建立；连接设备仍需用户确认", false),
        arrayOf(PairingAttemptPhase.FAILED, DevicesPairingFailure.EXPLICIT_FAILURE, "配对失败，请重试", true),
        arrayOf(PairingAttemptPhase.CANCELLED, DevicesPairingFailure.CANCELLED, "配对已取消", true),
        arrayOf(PairingAttemptPhase.EXPIRED, DevicesPairingFailure.EXPIRED, "配对已过期，请重新开始", true),
    )

    @Test
    fun `invalid code is distinguished without echoing input`() {
        val presentation = DevicesPairingState(
            method = PairingMethod.SIX_DIGIT_CODE,
            phase = PairingAttemptPhase.WAITING_FOR_CODE,
            failure = DevicesPairingFailure.INVALID_CODE,
        ).toPresentation()

        assertEquals(presentation.statusText, "配对码必须是 6 位数字")
        assertTrue(presentation.showCodeInputs)
    }

    @Test
    fun `active session replacement is a blocking explicit confirmation`() {
        val presentation = DevicesPairingState(
            method = PairingMethod.QR,
            phase = PairingAttemptPhase.IDLE,
            hasActiveSession = true,
            awaitingSessionReplacementConfirmation = true,
        ).toPresentation()

        assertTrue(presentation.showSessionReplacementConfirmation)
        assertFalse(presentation.showStart)
        assertFalse(presentation.showQrMatrix)
        assertEquals(presentation.sessionReplacementText, "开始新配对前必须先断开当前 ADB Session。是否继续？")
    }
}
