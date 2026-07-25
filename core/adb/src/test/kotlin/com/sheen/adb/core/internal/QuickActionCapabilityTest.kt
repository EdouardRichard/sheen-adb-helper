package com.sheen.adb.core.internal

import com.sheen.adb.core.QuickActionCapabilities
import com.sheen.adb.core.QuickActionCapability
import com.sheen.adb.core.QuickActionKind
import com.sheen.adb.core.QuickActionResult
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionCapabilityTest {
    @Test
    fun `each quick action independently preserves every capability outcome`() = runBlocking {
        val outcomes = capabilityOutcomes()

        outcomes.indices.forEach { index ->
            val fixture = ControlledDeviceFixture(
                apiLevel = 35,
                screenshot = outcomes[index],
                screenRecord = outcomes[(index + 1) % outcomes.size],
                reboot = outcomes[(index + 2) % outcomes.size],
            )
            val resolver = resolver(currentSession = { "session-current" }, fixture = { _ -> fixture })

            val result: QuickActionResult<QuickActionCapabilities> = resolver.resolve("session-current")
            val capabilities = result.successValue()

            assertEquals(capabilities.screenshot, fixture.screenshot)
            assertEquals(capabilities.screenRecord, fixture.screenRecord)
            assertEquals(capabilities.reboot, fixture.reboot)
        }
    }

    @Test
    fun `controlled device below Android 10 is probed instead of rejected by version`() = runBlocking {
        val olderControlledDevice = ControlledDeviceFixture(
            apiLevel = 28,
            screenshot = QuickActionCapability.Supported,
            screenRecord = QuickActionCapability.Unsupported("fixture-recorder-missing"),
            reboot = QuickActionCapability.PolicyRejected("fixture-policy"),
        )
        val probed = mutableListOf<QuickActionKind>()
        val resolver = resolver(
            currentSession = { "session-older-device" },
            fixture = { _ -> olderControlledDevice },
            probed = probed,
        )

        val result: QuickActionResult<QuickActionCapabilities> = resolver.resolve("session-older-device")
        val capabilities = result.successValue()

        assertEquals(capabilities.screenshot, QuickActionCapability.Supported)
        assertEquals(
            capabilities.screenRecord,
            QuickActionCapability.Unsupported("fixture-recorder-missing"),
        )
        assertEquals(
            capabilities.reboot,
            QuickActionCapability.PolicyRejected("fixture-policy"),
        )
        assertEquals(probed, QuickActionKind.entries)
    }

    @Test
    fun `session switch invalidates old capabilities and probes the replacement session`() = runBlocking {
        var activeSession = "session-old"
        val oldFixture = ControlledDeviceFixture(
            apiLevel = 35,
            screenshot = QuickActionCapability.Supported,
            screenRecord = QuickActionCapability.Supported,
            reboot = QuickActionCapability.Supported,
        )
        val replacementFixture = ControlledDeviceFixture(
            apiLevel = 28,
            screenshot = QuickActionCapability.ProbeFailed("fixture-screenshot-probe"),
            screenRecord = QuickActionCapability.Unknown,
            reboot = QuickActionCapability.Unsupported("fixture-reboot-missing"),
        )
        val probedSessions = mutableListOf<String>()
        val resolver = QuickActionCapabilityResolver(
            currentSessionId = { activeSession },
            screenshotProbe = { sessionId: String ->
                probedSessions += sessionId
                if (sessionId == "session-old") oldFixture.screenshot else replacementFixture.screenshot
            },
            screenRecordProbe = { sessionId: String ->
                probedSessions += sessionId
                if (sessionId == "session-old") oldFixture.screenRecord else replacementFixture.screenRecord
            },
            rebootProbe = { sessionId: String ->
                probedSessions += sessionId
                if (sessionId == "session-old") oldFixture.reboot else replacementFixture.reboot
            },
        )

        val oldResult: QuickActionResult<QuickActionCapabilities> = resolver.resolve("session-old")
        assertTrue(oldResult is QuickActionResult.Success<*>)
        activeSession = "session-replacement"

        val stale: QuickActionResult<QuickActionCapabilities> = resolver.resolve("session-old")
        val replacementResult: QuickActionResult<QuickActionCapabilities> =
            resolver.resolve("session-replacement")
        val replacement = replacementResult.successValue()

        assertEquals(stale, QuickActionResult.StaleSession("session-old"))
        assertEquals(replacement.expectedSessionId, "session-replacement")
        assertEquals(replacement.screenshot, replacementFixture.screenshot)
        assertEquals(replacement.screenRecord, replacementFixture.screenRecord)
        assertEquals(replacement.reboot, replacementFixture.reboot)
        assertEquals(
            probedSessions,
            listOf(
                "session-old",
                "session-old",
                "session-old",
                "session-replacement",
                "session-replacement",
                "session-replacement",
            ),
        )
    }

    @Test
    fun `session changing during probes returns stale and does not publish mixed capabilities`() = runBlocking {
        var activeSession = "session-probing"
        var screenRecordProbeCalls = 0
        var rebootProbeCalls = 0
        val resolver = QuickActionCapabilityResolver(
            currentSessionId = { activeSession },
            screenshotProbe = { _: String ->
                activeSession = "session-replacement"
                QuickActionCapability.Supported
            },
            screenRecordProbe = { _: String ->
                screenRecordProbeCalls++
                QuickActionCapability.Supported
            },
            rebootProbe = { _: String ->
                rebootProbeCalls++
                QuickActionCapability.Supported
            },
        )

        val result: QuickActionResult<QuickActionCapabilities> = resolver.resolve("session-probing")

        assertEquals(result, QuickActionResult.StaleSession("session-probing"))
        assertEquals(screenRecordProbeCalls, 0)
        assertEquals(rebootProbeCalls, 0)
    }

    private fun resolver(
        currentSession: () -> String?,
        fixture: (String) -> ControlledDeviceFixture,
        probed: MutableList<QuickActionKind> = mutableListOf(),
    ): QuickActionCapabilityResolver = QuickActionCapabilityResolver(
        currentSessionId = currentSession,
        screenshotProbe = { sessionId: String ->
            probed += QuickActionKind.SCREENSHOT
            fixture(sessionId).screenshot
        },
        screenRecordProbe = { sessionId: String ->
            probed += QuickActionKind.SCREEN_RECORD
            fixture(sessionId).screenRecord
        },
        rebootProbe = { sessionId: String ->
            probed += QuickActionKind.REBOOT
            fixture(sessionId).reboot
        },
    )

    private fun capabilityOutcomes(): List<QuickActionCapability> = listOf(
        QuickActionCapability.Supported,
        QuickActionCapability.Unsupported("fixture-unsupported"),
        QuickActionCapability.PolicyRejected("fixture-policy"),
        QuickActionCapability.ProbeFailed("fixture-probe"),
        QuickActionCapability.Unknown,
    )

    private fun QuickActionResult<QuickActionCapabilities>.successValue(): QuickActionCapabilities {
        assertTrue(this is QuickActionResult.Success<*>, "Expected capability success but was $this")
        return (this as QuickActionResult.Success<QuickActionCapabilities>).value
    }

    private data class ControlledDeviceFixture(
        val apiLevel: Int,
        val screenshot: QuickActionCapability,
        val screenRecord: QuickActionCapability,
        val reboot: QuickActionCapability,
    )
}
