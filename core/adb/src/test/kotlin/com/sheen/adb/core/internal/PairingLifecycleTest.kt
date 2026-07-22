package com.sheen.adb.core.internal

import com.sheen.adb.core.PairingAttemptId
import com.sheen.adb.core.PairingAttemptPhase
import com.sheen.adb.core.PairingFailure
import com.sheen.adb.core.PairingMethod
import com.sheen.adb.core.PairingSecret
import com.sheen.adb.core.internal.pairing.MonotonicClock
import com.sheen.adb.core.internal.pairing.PairingAction
import com.sheen.adb.core.internal.pairing.PairingLifecycle
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class PairingLifecycleTest {
    @Test
    fun `QR attempt prepares then waits for target and clears its password on success`() {
        val clock = FakeClock(nowMillis = 10)
        val action = FakePairingAction()
        val lifecycle = PairingLifecycle(clock, action)
        val attemptId = attemptId("qr")
        val password = "qr-secret-synthetic".toCharArray()

        assertEquals(
            lifecycle.startQr(attemptId, PairingSecret(password), deadlineMillis = 20).phase,
            PairingAttemptPhase.PREPARING,
        )
        assertEquals(lifecycle.awaitTarget(attemptId).phase, PairingAttemptPhase.WAITING_FOR_TARGET)
        assertEquals(lifecycle.onTargetReady(attemptId).phase, PairingAttemptPhase.SUCCEEDED)

        assertEquals(action.methods, listOf(PairingMethod.QR))
        assertCleared(password)
    }

    @Test
    fun `six digit attempt waits for code and is the local default method`() {
        val lifecycle = PairingLifecycle(FakeClock(), FakePairingAction())
        val attemptId = attemptId("code")

        val state = lifecycle.startSixDigit(attemptId, deadlineMillis = 20)

        assertEquals(state.method, PairingMethod.SIX_DIGIT_CODE)
        assertEquals(state.phase, PairingAttemptPhase.WAITING_FOR_CODE)
    }

    @Test
    fun `six digit attempt accepts exactly six ASCII digits`() {
        val action = FakePairingAction()
        val lifecycle = PairingLifecycle(FakeClock(), action)
        val attemptId = attemptId("valid")
        val code = "012345".toCharArray()
        lifecycle.startSixDigit(attemptId, deadlineMillis = 20)

        val state = lifecycle.submitCode(attemptId, code)

        assertEquals(state.phase, PairingAttemptPhase.SUCCEEDED)
        assertEquals(action.methods, listOf(PairingMethod.SIX_DIGIT_CODE))
        assertCleared(code)
    }

    @Test
    fun `six digit attempt rejects non ASCII six digit input and clears every rejected input`() {
        listOf(
            "12345",
            "1234567",
            "12a456",
            "12 456",
            "１２３４５６",
            "١٢٣٤٥٦",
            "𝟙𝟚𝟛𝟜𝟝𝟞",
        ).forEachIndexed { index, value ->
            val action = FakePairingAction()
            val lifecycle = PairingLifecycle(FakeClock(), action)
            val attemptId = attemptId("invalid-$index")
            val code = value.toCharArray()
            lifecycle.startSixDigit(attemptId, deadlineMillis = 20)

            val state = lifecycle.submitCode(attemptId, code)

            assertEquals(state.phase, PairingAttemptPhase.FAILED, value)
            assertEquals(state.failure, PairingFailure.INVALID_CODE, value)
            assertTrue(action.methods.isEmpty(), value)
            assertCleared(code)
        }
    }

    @Test
    fun `attempt IDs are nonblank opaque and redacted from rendering`() {
        val token = "attempt-synthetic-redacted-token"
        val id = PairingAttemptId.of(token)

        assertFalse(id.toString().contains(token))
        assertFalse(runCatching { PairingAttemptId.of("   ") }.isSuccess)
    }

    @Test
    fun `stale target token is rejected clears retained QR secret and cannot advance`() {
        val lifecycle = PairingLifecycle(FakeClock(), FakePairingAction())
        val activeId = attemptId("active")
        val staleId = attemptId("stale")
        val password = "qr-password-synthetic".toCharArray()
        lifecycle.startQr(activeId, PairingSecret(password), deadlineMillis = 20)
        lifecycle.awaitTarget(activeId)

        val stale = lifecycle.onTargetReady(staleId)
        val retried = lifecycle.onTargetReady(activeId)

        assertEquals(stale.phase, PairingAttemptPhase.FAILED)
        assertEquals(stale.failure, PairingFailure.STALE_ATTEMPT)
        assertEquals(retried, stale)
        assertCleared(password)
    }

    @Test
    fun `expired attempt becomes terminal at its monotonic deadline and is idempotent`() {
        val clock = FakeClock(nowMillis = 20)
        val lifecycle = PairingLifecycle(clock, FakePairingAction())
        val attemptId = attemptId("expired")
        val code = "012345".toCharArray()
        lifecycle.startSixDigit(attemptId, deadlineMillis = 20)

        val expired = lifecycle.submitCode(attemptId, code)
        val repeated = lifecycle.expire(attemptId)

        assertEquals(expired.phase, PairingAttemptPhase.EXPIRED)
        assertEquals(expired.failure, PairingFailure.EXPIRED)
        assertEquals(repeated, expired)
        assertCleared(code)
    }

    @Test
    fun `cancellation explicit failure and unsupported are idempotent terminal states`() {
        listOf<(PairingLifecycle, PairingAttemptId) -> Any>(
            { lifecycle, id -> lifecycle.cancel(id) },
            { lifecycle, id -> lifecycle.fail(id) },
            { lifecycle, id -> lifecycle.markUnsupported(id) },
        ).forEachIndexed { index, terminal ->
            val lifecycle = PairingLifecycle(FakeClock(), FakePairingAction())
            val attemptId = attemptId("terminal-$index")
            val password = "retained-secret-$index".toCharArray()
            lifecycle.startQr(attemptId, PairingSecret(password), deadlineMillis = 20)

            val terminalState = terminal(lifecycle, attemptId)
            val afterRepeatedTerminal = lifecycle.cancel(attemptId)
            val afterActiveCallback = lifecycle.onTargetReady(attemptId)

            assertEquals(afterRepeatedTerminal, terminalState)
            assertEquals(afterActiveCallback, terminalState)
            assertCleared(password)
        }
    }

    @Test
    fun `successful terminal attempt is idempotent and cannot be revived`() {
        val lifecycle = PairingLifecycle(FakeClock(), FakePairingAction())
        val attemptId = attemptId("success")
        val code = "012345".toCharArray()
        lifecycle.startSixDigit(attemptId, deadlineMillis = 20)

        val success = lifecycle.submitCode(attemptId, code)

        assertEquals(lifecycle.cancel(attemptId), success)
        assertEquals(lifecycle.fail(attemptId), success)
        assertEquals(lifecycle.markUnsupported(attemptId), success)
        assertEquals(lifecycle.submitCode(attemptId, "654321".toCharArray()), success)
        assertCleared(code)
    }

    @Test
    fun `thrown pairing action clears supplied and retained secrets and exposes only a safe failure`() {
        val rawException = "raw-exception-synthetic"
        val action = FakePairingAction(throwable = IllegalStateException(rawException))
        val lifecycle = PairingLifecycle(FakeClock(), action)
        val attemptId = attemptId("throws")
        val password = "qr-secret;service-synthetic;192.0.2.44".toCharArray()
        lifecycle.startQr(attemptId, PairingSecret(password), deadlineMillis = 20)
        lifecycle.awaitTarget(attemptId)

        val state = lifecycle.onTargetReady(attemptId)
        val rendered = state.toString()

        assertEquals(state.phase, PairingAttemptPhase.FAILED)
        assertEquals(state.failure, PairingFailure.ACTION_FAILED)
        assertCleared(password)
        listOf("qr-secret", "service-synthetic", "192.0.2.44", rawException).forEach {
            assertFalse(rendered.contains(it))
            assertFalse(state.failure.toString().contains(it))
        }
    }

    @Test
    fun `stale submitted code is cleared before a safe terminal rejection`() {
        val lifecycle = PairingLifecycle(FakeClock(), FakePairingAction())
        val activeId = attemptId("current-code")
        val staleId = attemptId("old-code")
        val staleCode = "012345".toCharArray()
        lifecycle.startSixDigit(activeId, deadlineMillis = 20)

        val state = lifecycle.submitCode(staleId, staleCode)

        assertEquals(state.phase, PairingAttemptPhase.FAILED)
        assertEquals(state.failure, PairingFailure.STALE_ATTEMPT)
        assertCleared(staleCode)
    }

    private fun attemptId(suffix: String): PairingAttemptId =
        PairingAttemptId.of("attempt-synthetic-$suffix")

    private fun assertCleared(value: CharArray) {
        assertTrue(value.all { it == '\u0000' })
    }

    private class FakeClock(
        var nowMillis: Long = 0,
    ) : MonotonicClock {
        override fun nowMillis(): Long = nowMillis
    }

    private class FakePairingAction(
        private val throwable: Throwable? = null,
    ) : PairingAction {
        val methods = mutableListOf<PairingMethod>()

        override fun pair(method: PairingMethod, secret: CharArray) {
            methods += method
            throwable?.let { throw it }
        }
    }
}
