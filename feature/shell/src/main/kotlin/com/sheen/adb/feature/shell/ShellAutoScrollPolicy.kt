package com.sheen.adb.feature.shell

data class ShellScrollState(
    val autoScroll: Boolean = true,
    val anchorIndex: Int = 0,
    val anchorOffset: Int = 0,
    val outputGeneration: Long = 0,
)

data class ShellScrollDecision(
    val state: ShellScrollState,
    val targetIndex: Int? = null,
)

object ShellAutoScrollPolicy {
    fun onOutputChanged(
        previous: ShellScrollState,
        itemCount: Int,
        outputGeneration: Long,
    ): ShellScrollDecision {
        require(itemCount >= 0)
        if (!previous.autoScroll) {
            return ShellScrollDecision(
                state = previous.copy(outputGeneration = outputGeneration),
            )
        }
        val end = (itemCount - 1).coerceAtLeast(0)
        return ShellScrollDecision(
            state = previous.copy(
                anchorIndex = end,
                anchorOffset = 0,
                outputGeneration = outputGeneration,
            ),
            targetIndex = end.takeIf { itemCount > 0 },
        )
    }

    fun setEnabled(
        previous: ShellScrollState,
        enabled: Boolean,
        itemCount: Int,
    ): ShellScrollDecision {
        require(itemCount >= 0)
        if (!enabled) return ShellScrollDecision(previous.copy(autoScroll = false))
        val end = (itemCount - 1).coerceAtLeast(0)
        return ShellScrollDecision(
            state = previous.copy(autoScroll = true, anchorIndex = end, anchorOffset = 0),
            targetIndex = end.takeIf { itemCount > 0 },
        )
    }

    fun onVisibleRecordsChanged(
        previous: ShellScrollState,
        itemCount: Int,
    ): ShellScrollDecision {
        require(itemCount >= 0)
        if (previous.autoScroll) {
            val end = (itemCount - 1).coerceAtLeast(0)
            return ShellScrollDecision(
                state = previous.copy(anchorIndex = end, anchorOffset = 0),
                targetIndex = end.takeIf { itemCount > 0 },
            )
        }
        val safeAnchor = previous.anchorIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val wasClamped = safeAnchor != previous.anchorIndex || itemCount == 0
        return ShellScrollDecision(
            state = previous.copy(
                anchorIndex = safeAnchor,
                anchorOffset = if (wasClamped) 0 else previous.anchorOffset,
            ),
        )
    }
}
