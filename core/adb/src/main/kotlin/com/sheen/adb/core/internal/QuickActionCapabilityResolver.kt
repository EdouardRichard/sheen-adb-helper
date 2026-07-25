package com.sheen.adb.core.internal

import com.sheen.adb.core.QuickActionCapabilities
import com.sheen.adb.core.QuickActionCapability
import com.sheen.adb.core.QuickActionResult

internal class QuickActionCapabilityResolver(
    private val currentSessionId: () -> String?,
    private val screenshotProbe: suspend (String) -> QuickActionCapability,
    private val screenRecordProbe: suspend (String) -> QuickActionCapability,
    private val rebootProbe: suspend (String) -> QuickActionCapability,
) {
    suspend fun resolve(expectedSessionId: String): QuickActionResult<QuickActionCapabilities> {
        if (!isCurrent(expectedSessionId)) return QuickActionResult.StaleSession(expectedSessionId)
        val screenshot = screenshotProbe(expectedSessionId)
        if (!isCurrent(expectedSessionId)) return QuickActionResult.StaleSession(expectedSessionId)
        val screenRecord = screenRecordProbe(expectedSessionId)
        if (!isCurrent(expectedSessionId)) return QuickActionResult.StaleSession(expectedSessionId)
        val reboot = rebootProbe(expectedSessionId)
        if (!isCurrent(expectedSessionId)) return QuickActionResult.StaleSession(expectedSessionId)
        return QuickActionResult.Success(
            QuickActionCapabilities(
                expectedSessionId = expectedSessionId,
                screenshot = screenshot,
                screenRecord = screenRecord,
                reboot = reboot,
            ),
        )
    }

    private fun isCurrent(expectedSessionId: String): Boolean =
        currentSessionId() == expectedSessionId
}
