package com.android.purebilibili.navigation3

import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure

private const val QUICK_RETURN_THRESHOLD_MILLIS = 500L

internal const val RETURN_SESSION_RECONCILE_GRACE_MS = 450L
internal const val RETURN_SESSION_STALE_TIMEOUT_MS = 2_000L
internal const val CARD_TRANSITION_STUCK_RECOVERY_TIMEOUT_MS = 1_500L

internal fun hasReturningDetailDestination(backStack: List<BiliPaiNavKey>): Boolean =
    backStack.any { it is BiliPaiNavKey.VideoDetail || it is BiliPaiNavKey.ArticleDetail }

internal fun hasCardMorphDestination(backStack: List<BiliPaiNavKey>): Boolean =
    backStack.any { isCardMorphDestinationNavKey(it) }

internal enum class ReturnSessionReconciliationAction {
    NONE,
    WAIT_FOR_SUPPRESSION_WINDOW,
}

/**
 * State-derived check for whether a latched returning-from-detail session no longer has an
 * owning detail destination on the back stack and is waiting only for the post-return animation
 * suppression window to finish.
 */
internal fun resolveReturnSessionReconciliationAction(
    isReturningFromDetail: Boolean,
    hasReturningDetailOnStack: Boolean,
    isNavigationSettled: Boolean,
    isCardTransitionIdle: Boolean,
    isCardGestureInProgress: Boolean,
): ReturnSessionReconciliationAction {
    if (!isReturningFromDetail || isCardGestureInProgress) {
        return ReturnSessionReconciliationAction.NONE
    }
    if (!hasReturningDetailOnStack && isNavigationSettled && isCardTransitionIdle) {
        return ReturnSessionReconciliationAction.WAIT_FOR_SUPPRESSION_WINDOW
    }
    return ReturnSessionReconciliationAction.NONE
}

/**
 * State-derived check for card morph state left in HELD/AutoReturn/etc. even though no card
 * morph destination remains on the stack and the host navigation transition has settled.
 */
internal fun shouldResetStuckCardTransition(
    hasCardMorphDestinationOnStack: Boolean,
    isCardTransitionIdle: Boolean,
    isCardGestureInProgress: Boolean,
    isHostNavigationSettled: Boolean,
): Boolean = !isCardTransitionIdle &&
    !isCardGestureInProgress &&
    !hasCardMorphDestinationOnStack &&
    isHostNavigationSettled

/**
 * Source-level watchdog check for card morph state remaining non-Idle after all morph
 * destinations have left the back stack (for example when NavDisplay drops its final settle).
 */
internal fun shouldForceResetCardTransitionOnTimeout(
    hasCardMorphDestinationOnStack: Boolean,
    isCardTransitionIdle: Boolean,
    isCardGestureInProgress: Boolean,
): Boolean = !isCardTransitionIdle &&
    !isCardGestureInProgress &&
    !hasCardMorphDestinationOnStack

/** Only a real prefix contraction consumes sources; cancelled back and replacement do not. */
internal fun countSuccessfulVideoDetailPops(
    before: List<BiliPaiNavKey>,
    after: List<BiliPaiNavKey>,
): Int {
    if (after.isEmpty() || after.size >= before.size) return 0
    if (after.indices.any { after[it] != before[it] }) return 0
    var count = 0
    for (index in after.size until before.size) {
        if (before[index] is BiliPaiNavKey.VideoDetail) count++
    }
    return count
}

internal data class RelatedReturnSourceRestoreDecision(
    val transitionObserved: Boolean,
    val shouldRestore: Boolean,
)

/**
 * Never swap nested source metadata by elapsed time. Predictive settle duration depends on the
 * gesture release point and may outlive the configured tween. The older parent session becomes
 * safe only after this return has entered a non-idle exposure and subsequently reached Idle.
 */
internal fun resolveRelatedReturnSourceRestoreDecision(
    restorePending: Boolean,
    transitionObserved: Boolean,
    transitionAnimated: Boolean,
    exposure: VideoCardTransitionExposure,
): RelatedReturnSourceRestoreDecision {
    if (!restorePending) {
        return RelatedReturnSourceRestoreDecision(
            transitionObserved = false,
            shouldRestore = false,
        )
    }
    if (!transitionAnimated) {
        return RelatedReturnSourceRestoreDecision(
            transitionObserved = false,
            shouldRestore = true,
        )
    }
    if (exposure != VideoCardTransitionExposure.Idle) {
        return RelatedReturnSourceRestoreDecision(
            transitionObserved = true,
            shouldRestore = false,
        )
    }
    return RelatedReturnSourceRestoreDecision(
        transitionObserved = transitionObserved,
        shouldRestore = transitionObserved,
    )
}

internal data class BiliPaiReturnSessionState(
    val isReturningFromDetail: Boolean = false,
    val isQuickReturnFromDetail: Boolean = false,
    val lastVideoSourceRoute: String? = null,
    val lastVideoSourceKey: String? = null,
    /**
     * Dual-column left/right origin captured at detail enter. Used when live
     * CardPositionManager state is gone or key-matching fails on pop.
     */
    val lastCardSourceDirection: BiliPaiNavCardSourceDirection =
        BiliPaiNavCardSourceDirection.NONE,
    val transitionSession: VideoCardTransitionSession? = null,
    /** 每次详情压详情前冻结当前来源，pop settle 后逐层恢复。 */
    val previousVideoSources: List<BiliPaiVideoSource> = emptyList(),
    val previousTransitionSessions: List<VideoCardTransitionSession> = emptyList(),
    val detailEnteredAtMillis: Long? = null,
    /** Successful detail pops, not back requests, awaiting the navigation driver's endpoint. */
    val pendingRelatedReturnCount: Int = 0,
    /** Distinguishes 1 -> 0 -> 1 pending writes even if Compose merges their recompositions. */
    val relatedReturnRequestId: Long = 0L,
    /** Invalidates completion callbacks when a push/replacement starts a new source session. */
    val sourceGeneration: Long = 0L,
) {
    fun recordVideoSource(source: BiliPaiVideoSource): BiliPaiReturnSessionState {
        val restored = restorePendingRelatedReturns()
        if (restored !== this) return restored.recordVideoSource(source)
        val relatedDetailSource = source.route?.substringBefore("?")?.startsWith("video/") == true
        val previousSource = BiliPaiVideoSource(lastVideoSourceRoute, lastVideoSourceKey)
        val updatedHistory = if (relatedDetailSource && previousSource.route != null) {
            previousVideoSources + previousSource
        } else if (relatedDetailSource) {
            previousVideoSources
        } else {
            emptyList()
        }
        return copy(
            lastVideoSourceRoute = source.route,
            lastVideoSourceKey = source.key,
            previousVideoSources = updatedHistory,
            sourceGeneration = sourceGeneration + 1,
        )
    }

    fun recordTransitionSession(
        session: VideoCardTransitionSession,
        preserveCurrentSession: Boolean = session.sourceRoute
            ?.substringBefore("?")
            ?.startsWith("video/") == true,
    ): BiliPaiReturnSessionState {
        // A new push must start from the actual parent, even if its preceding pop is settling.
        val restored = restorePendingRelatedReturns()
        if (restored !== this) return restored.recordTransitionSession(session, preserveCurrentSession)
        val currentSession = transitionSession
        val updatedSessionHistory = if (preserveCurrentSession && currentSession != null) {
            previousTransitionSessions + currentSession
        } else if (preserveCurrentSession) {
            previousTransitionSessions
        } else {
            emptyList()
        }
        val currentSource = BiliPaiVideoSource(lastVideoSourceRoute, lastVideoSourceKey)
        val updatedSourceHistory = if (preserveCurrentSession && currentSource.route != null) {
            previousVideoSources + currentSource
        } else if (preserveCurrentSession) {
            previousVideoSources
        } else {
            emptyList()
        }
        return copy(
            lastVideoSourceRoute = session.sourceRoute,
            lastVideoSourceKey = session.sourceKey,
            lastCardSourceDirection = session.cardSourceDirection,
            transitionSession = session,
            previousVideoSources = updatedSourceHistory,
            previousTransitionSessions = updatedSessionHistory,
            sourceGeneration = sourceGeneration + 1,
        )
    }

    fun recordCardSourceDirection(
        direction: BiliPaiNavCardSourceDirection
    ): BiliPaiReturnSessionState {
        return copy(lastCardSourceDirection = direction)
    }

    fun recordSuccessfulVideoDetailPop(): BiliPaiReturnSessionState {
        val restorableCount = maxOf(previousTransitionSessions.size, previousVideoSources.size)
        if (pendingRelatedReturnCount >= restorableCount) return this
        return copy(
            pendingRelatedReturnCount = pendingRelatedReturnCount + 1,
            relatedReturnRequestId = relatedReturnRequestId + 1,
        )
    }

    fun restorePendingRelatedReturns(
        expectedGeneration: Long = sourceGeneration,
        expectedRequestId: Long = relatedReturnRequestId,
    ): BiliPaiReturnSessionState {
        if (expectedGeneration != sourceGeneration || expectedRequestId != relatedReturnRequestId ||
            pendingRelatedReturnCount == 0
        ) return this
        var restored = copy(pendingRelatedReturnCount = 0)
        repeat(pendingRelatedReturnCount) {
            restored = restored.restorePreviousVideoSourceAfterRelatedReturn()
        }
        return restored
    }

    fun restorePreviousVideoSourceAfterRelatedReturn(): BiliPaiReturnSessionState {
        val restoredSession = previousTransitionSessions.lastOrNull()
        val restoredSource = previousVideoSources.lastOrNull()
        if (restoredSession == null && restoredSource == null) return this
        return copy(
            lastVideoSourceRoute = restoredSession?.sourceRoute ?: restoredSource?.route,
            lastVideoSourceKey = restoredSession?.sourceKey ?: restoredSource?.key,
            lastCardSourceDirection = restoredSession?.cardSourceDirection
                ?: lastCardSourceDirection,
            transitionSession = restoredSession,
            previousVideoSources = previousVideoSources.dropLast(1),
            previousTransitionSessions = previousTransitionSessions.dropLast(1),
        )
    }

    fun recordVideoSourceRoute(sourceRoute: String?): BiliPaiReturnSessionState {
        return copy(
            lastVideoSourceRoute = normalizeBiliPaiVideoSourceRoute(sourceRoute),
            lastVideoSourceKey = null,
            transitionSession = null,
            previousVideoSources = emptyList(),
            previousTransitionSessions = emptyList(),
            pendingRelatedReturnCount = 0,
            sourceGeneration = sourceGeneration + 1,
        )
    }

    fun markDetailEntered(nowMillis: Long): BiliPaiReturnSessionState {
        return copy(
            isReturningFromDetail = false,
            isQuickReturnFromDetail = false,
            detailEnteredAtMillis = nowMillis
        )
    }

    fun markReturning(nowMillis: Long): BiliPaiReturnSessionState {
        val elapsed = detailEnteredAtMillis?.let { nowMillis - it } ?: Long.MAX_VALUE
        return copy(
            isReturningFromDetail = true,
            isQuickReturnFromDetail = elapsed in 0L..QUICK_RETURN_THRESHOLD_MILLIS
        )
    }

    fun clearReturning(): BiliPaiReturnSessionState {
        return copy(
            isReturningFromDetail = false,
            isQuickReturnFromDetail = false
        )
    }
}
