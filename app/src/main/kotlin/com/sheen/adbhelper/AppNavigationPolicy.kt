package com.sheen.adbhelper

import kotlin.math.abs

internal const val HORIZONTAL_DISTANCE_THRESHOLD_DP = 48f
internal const val HORIZONTAL_VELOCITY_THRESHOLD_DP_PER_SECOND = 600f
internal const val HORIZONTAL_DOMINANCE_RATIO = 1.2f
internal val FIRST_DESTINATION = MainDestination.CONNECTION
internal val LAST_DESTINATION = MainDestination.LOGCAT
internal const val PAGE_LOAD_WAIT_TIMEOUT_MILLIS = 8_000L
internal const val PAGE_LOAD_CLEANUP_GRACE_MILLIS = 400L
internal const val PAGE_LOAD_RECOVERY_TIMEOUT_MILLIS = 2_000L

enum class NavigationInput {
    BOTTOM_BAR,
    GESTURE,
    RESTORE,
    DEEP_LINK,
}

enum class NavigationRejectReason {
    SAME_DESTINATION,
    LOCKED,
    VERTICAL_INTENT,
    BELOW_THRESHOLD,
    OUTSIDE_ENDPOINT,
    NOT_ADJACENT,
    SUPERSEDED,
    DISCONNECTED,
}

enum class NavigationAdjacency {
    ADJACENT,
    NON_ADJACENT,
}

sealed interface PageLifecycleEvent {
    data class Hidden(val destination: MainDestination) : PageLifecycleEvent
    data class Visible(val destination: MainDestination) : PageLifecycleEvent
}

sealed interface NavigationDecision {
    data class Accepted(
        val target: MainDestination,
        val supersede: MainDestination?,
        val lifecycleEvents: List<PageLifecycleEvent>,
    ) : NavigationDecision

    data class Rejected(
        val reason: NavigationRejectReason,
        val lifecycleEvents: List<PageLifecycleEvent> = emptyList(),
    ) : NavigationDecision
}

sealed interface ControlledPageAvailability {
    data object Available : ControlledPageAvailability

    data class Unavailable(
        val requested: MainDestination,
        val input: NavigationInput,
        val guidance: com.sheen.adb.ui.LocalizedTextRef =
            AppStrings.ref(AppStringKey.CONNECT_FIRST),
        val deviceRequestAllowed: Boolean = false,
    ) : ControlledPageAvailability
}

class DisconnectedDestinationGate {
    fun evaluate(
        target: MainDestination,
        input: NavigationInput,
        connected: Boolean,
    ): ControlledPageAvailability =
        if (connected || target == MainDestination.CONNECTION) {
            ControlledPageAvailability.Available
        } else {
            ControlledPageAvailability.Unavailable(
                requested = target,
                input = input,
                deviceRequestAllowed = false,
            )
        }

    fun acceptedTarget(
        availability: ControlledPageAvailability,
        requested: MainDestination,
    ): MainDestination = when (availability) {
        ControlledPageAvailability.Available -> requested
        is ControlledPageAvailability.Unavailable -> MainDestination.CONNECTION
    }
}

object PageLoadNavigationGate {
    fun onRequest(
        state: PageLoadNavigationGateState,
        current: MainDestination,
        target: MainDestination,
        currentPageLoading: Boolean,
        nowMillis: Long,
    ): PageLoadNavigationTransition {
        if (state.phase == PageLoadNavigationPhase.RECOVERING && state.owner == current) {
            return PageLoadNavigationTransition(
                state = state.copy(pendingTarget = target),
                effect = PageLoadNavigationEffect.NONE,
            )
        }
        if (!currentPageLoading) {
            return navigate(target)
        }
        val keepWaitStart =
            state.phase == PageLoadNavigationPhase.WAITING && state.owner == current
        return PageLoadNavigationTransition(
            state = PageLoadNavigationGateState(
                phase = PageLoadNavigationPhase.WAITING,
                owner = current,
                pendingTarget = target,
                waitStartedAtMillis = if (keepWaitStart) state.waitStartedAtMillis else nowMillis,
            ),
            effect = PageLoadNavigationEffect.NONE,
        )
    }

    fun onTick(
        state: PageLoadNavigationGateState,
        currentPageLoading: Boolean,
        nowMillis: Long,
    ): PageLoadNavigationTransition = when (state.phase) {
        PageLoadNavigationPhase.IDLE ->
            PageLoadNavigationTransition(state, PageLoadNavigationEffect.NONE)
        PageLoadNavigationPhase.WAITING -> {
            val target = state.pendingTarget ?: return idle()
            val waitStarted = state.waitStartedAtMillis ?: nowMillis
            when {
                !currentPageLoading -> navigate(target)
                nowMillis - waitStarted >= PAGE_LOAD_WAIT_TIMEOUT_MILLIS ->
                    cancelAndRecover(state, nowMillis)
                else -> PageLoadNavigationTransition(state, PageLoadNavigationEffect.NONE)
            }
        }
        PageLoadNavigationPhase.RECOVERING -> {
            val target = state.pendingTarget ?: return idle()
            val recoveryStarted = state.recoveryStartedAtMillis ?: nowMillis
            val elapsed = nowMillis - recoveryStarted
            if ((!currentPageLoading && elapsed >= PAGE_LOAD_CLEANUP_GRACE_MILLIS) ||
                elapsed >= PAGE_LOAD_RECOVERY_TIMEOUT_MILLIS
            ) {
                navigate(target)
            } else {
                PageLoadNavigationTransition(state, PageLoadNavigationEffect.NONE)
            }
        }
    }

    fun cancelAndRecover(
        state: PageLoadNavigationGateState,
        nowMillis: Long,
    ): PageLoadNavigationTransition {
        if (state.pendingTarget == null) return idle()
        return PageLoadNavigationTransition(
            state = state.copy(
                phase = PageLoadNavigationPhase.RECOVERING,
                recoveryStartedAtMillis = nowMillis,
            ),
            effect = PageLoadNavigationEffect.CANCEL_CURRENT_LOAD,
        )
    }

    private fun navigate(target: MainDestination) = PageLoadNavigationTransition(
        state = PageLoadNavigationGateState(),
        effect = PageLoadNavigationEffect.NAVIGATE,
        target = target,
    )

    private fun idle() = PageLoadNavigationTransition(
        state = PageLoadNavigationGateState(),
        effect = PageLoadNavigationEffect.NONE,
    )
}

class AppNavigationPolicy {
    var latestAcceptedTarget: MainDestination? = null
        private set

    fun resolveRequest(
        current: MainDestination,
        target: MainDestination,
        input: NavigationInput,
        navigationLock: NavigationLock?,
    ): NavigationDecision {
        if (navigationLock != null) {
            return NavigationDecision.Rejected(NavigationRejectReason.LOCKED)
        }
        if (current == target) {
            return NavigationDecision.Rejected(NavigationRejectReason.SAME_DESTINATION)
        }
        if (input == NavigationInput.GESTURE && adjacency(current, target) != NavigationAdjacency.ADJACENT) {
            return NavigationDecision.Rejected(NavigationRejectReason.NOT_ADJACENT)
        }
        val supersede = latestAcceptedTarget
        latestAcceptedTarget = target
        return NavigationDecision.Accepted(
            target = target,
            supersede = supersede?.takeIf { it != target },
            lifecycleEvents = listOf(
                PageLifecycleEvent.Hidden(current),
                PageLifecycleEvent.Visible(target),
            ),
        )
    }

    fun resolveGesture(
        current: MainDestination,
        deltaXDp: Float,
        deltaYDp: Float,
        velocityXDpPerSecond: Float,
        navigationLock: NavigationLock?,
        childConsumedHorizontal: Boolean = false,
    ): NavigationDecision {
        if (navigationLock != null) {
            return NavigationDecision.Rejected(NavigationRejectReason.LOCKED)
        }
        if (childConsumedHorizontal) {
            return NavigationDecision.Rejected(NavigationRejectReason.SUPERSEDED)
        }
        val horizontal = abs(deltaXDp)
        val vertical = abs(deltaYDp)
        if (horizontal < vertical * HORIZONTAL_DOMINANCE_RATIO) {
            return NavigationDecision.Rejected(NavigationRejectReason.VERTICAL_INTENT)
        }
        val intentional = horizontal >= HORIZONTAL_DISTANCE_THRESHOLD_DP ||
            abs(velocityXDpPerSecond) >= HORIZONTAL_VELOCITY_THRESHOLD_DP_PER_SECOND
        if (!intentional) {
            return NavigationDecision.Rejected(NavigationRejectReason.BELOW_THRESHOLD)
        }
        val direction = if (deltaXDp < 0f || velocityXDpPerSecond < 0f) 1 else -1
        val targetIndex = current.ordinal + direction
        if (targetIndex !in FIRST_DESTINATION.ordinal..LAST_DESTINATION.ordinal) {
            return NavigationDecision.Rejected(NavigationRejectReason.OUTSIDE_ENDPOINT)
        }
        return resolveRequest(
            current = current,
            target = MainDestination.entries[targetIndex],
            input = NavigationInput.GESTURE,
            navigationLock = navigationLock,
        )
    }

    fun transitionSettled(destination: MainDestination) {
        if (latestAcceptedTarget == destination) latestAcceptedTarget = null
    }

    private fun adjacency(
        current: MainDestination,
        target: MainDestination,
    ): NavigationAdjacency =
        if (abs(current.ordinal - target.ordinal) == 1) {
            NavigationAdjacency.ADJACENT
        } else {
            NavigationAdjacency.NON_ADJACENT
        }
}
