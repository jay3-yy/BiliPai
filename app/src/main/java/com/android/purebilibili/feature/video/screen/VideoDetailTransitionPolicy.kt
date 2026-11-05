package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase

import androidx.compose.animation.core.Easing
import com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.isVideoCardLiveReturnMorphOwnership
import com.android.purebilibili.core.ui.transition.normalizeSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.resolveReturnSessionLockedCoverOwnership
import com.android.purebilibili.core.ui.transition.resolveVideoCardLiveMorphSecondaryContentAlpha
import com.android.purebilibili.core.ui.transition.resolveVideoCardLiveReturnVisualHandoffAlpha
import com.android.purebilibili.core.ui.transition.resolveVideoCardReturnCoverOwnership
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionEnterEasing
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionReturnEasing
import com.android.purebilibili.core.ui.transition.resolveVideoSharedCoverCacheKey
import com.android.purebilibili.core.ui.transition.shouldHandVisualOwnershipToResidentCoverForOwnership
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardLiveReturnMorph

private const val COVER_TAKEOVER_PRE_BACK_DELAY_MILLIS = 0L
private const val RESIDENT_COVER_RETURN_TAKEOVER_DEPTH_SPAN = 0.82f
internal const val VIDEO_CONTENT_COMMENT_TAB_INDEX = 1

/**
 * 嵌套详情共享同一组 CompositionLocal，但一次卡片 morph 只能由目标详情 entry 消费。
 * 例如 video/A -> video/B 时，活动 source 是 video/A，因此仅 B 的 entry sourceRoute
 * 能匹配；作为返回预览的 A 不得误用 B 的封面快照和卡片落位信息。
 */
internal fun isVideoDetailEntryActiveMiuixTransitionSource(
    entrySourceRoute: String?,
    activeSourceRoute: String?,
    miuixTransitionEnabled: Boolean,
): Boolean {
    // Source metadata survives even when the navigation host disables the card morph.
    if (!miuixTransitionEnabled) return false
    val normalizedEntrySource =
        normalizeSharedElementSourceRoute(entrySourceRoute) ?: return false
    val normalizedActiveSource =
        normalizeSharedElementSourceRoute(activeSourceRoute) ?: return false
    return normalizedEntrySource == normalizedActiveSource
}

/**
 * A restored parent session is armed for its next return, but must not retake visual ownership
 * while the navigation clock is idle. Nested pop restores that session after the child lands;
 * consuming its cover/chrome at that point causes a deterministic delayed parent-cover flash.
 */
internal fun shouldConsumeMiuixTransitionVisualAssets(
    entryOwnsMiuixCardTransition: Boolean,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    isGestureRestoreInProgress: Boolean = false,
): Boolean {
    return entryOwnsMiuixCardTransition && (
        phase != VideoCardTransitionBackgroundPhase.IDLE ||
            isReturnGestureInProgress ||
            isGestureRestoreInProgress
        )
}

/**
 * Keep source chrome mounted through the morph. Its draw-phase alpha shares the detail
 * body's handoff timeline, so ordinary return and predictive return reveal the same pixels.
 */
internal fun shouldDrawFlyingReconstructedSourceChrome(
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    isGestureRestoreInProgress: Boolean = false,
): Boolean {
    if (isReturnGestureInProgress || isGestureRestoreInProgress) return true
    return phase != VideoCardTransitionBackgroundPhase.IDLE
}

/** Frozen cover overlay pixels are usable only after the list card actually recorded them. */
internal fun shouldDrawNativeCoverOverlay(
    overlayWidthPx: Float,
    overlayHeightPx: Float,
): Boolean = overlayWidthPx > 1f && overlayHeightPx > 1f

/** @return false: list pixels cannot show through a Miuix flying clip. */
internal fun shouldPunchThroughFlyingMediaToNativeListCard(
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    isGestureRestoreInProgress: Boolean = false,
): Boolean {
    @Suppress("UNUSED_PARAMETER")
    val ignored = phase to isReturnGestureInProgress to isGestureRestoreInProgress
    return false
}

internal fun resolveForceCoverOnlyForReturn(
    forceCoverOnlyOnReturn: Boolean,
    transitionEnabled: Boolean = true,
    isCardReturnExitInProgress: Boolean = false
): Boolean {
    if (!transitionEnabled || isCardReturnExitInProgress) return false
    return forceCoverOnlyOnReturn
}

/**
 * 返回「离开态」（次要内容淡出、标记离开等）。
 * 不等于封面接管：ImmediatePlayback 的 live morph 路径下播放器应保持可见。
 *
 * 预测拖动未提交时 isSessionReturningToCard 应为 false（尚未 markReturning）。
 */
internal fun shouldUseReturningVideoDetailVisualState(
    forceCoverOnlyForReturn: Boolean,
    isCardReturnExitInProgress: Boolean = false,
    isSessionReturningToCard: Boolean = false,
): Boolean {
    return forceCoverOnlyForReturn ||
        isCardReturnExitInProgress ||
        isSessionReturningToCard
}

/**
 * A nested video return exposes both entries to the same app-level return-session flag. Only the
 * outgoing detail whose source route owns the active Miuix card transition may consume that flag;
 * the parent entry is the return target and must keep its complete body composed.
 */
internal fun shouldConsumeMiuixReturnSessionForVideoDetailEntry(
    entryOwnsMiuixCardTransition: Boolean,
    isReturningFromDetail: Boolean,
    transitionEnabled: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean,
): Boolean {
    return entryOwnsMiuixCardTransition &&
        isReturningFromDetail &&
        transitionEnabled &&
        sharedBoundsActive &&
        !keepLoadedContentForBackPreview
}

/**
 * 是否已**提交**卡片返回（可与封面做 landing handoff）。
 *
 * 与 [shouldUseReturningVideoDetailVisualState] 不同：
 * - 预测返回 seek 中 `targetState=PostExit` 会让离开态为 true，但尚未松手提交
 * - 封面/播放器 alpha 的 handoff **只认提交**，否则手势一开始封面会盖死实时画面
 *
 * 提交信号：按钮返回 [isActuallyLeaving]，或导航层 [isSessionReturningToCard]（markReturning）。
 */
internal fun shouldTreatVideoDetailCardReturnAsCommitted(
    isActuallyLeaving: Boolean,
    isSessionReturningToCard: Boolean,
): Boolean {
    return isActuallyLeaving || isSessionReturningToCard
}

/**
 * 详情 → 来源卡片 Miuix entry morph：实时画面跟手缩小（一镜到底）。
 * 实现收口到 [shouldUseVideoCardLiveReturnMorph]（[VideoCardReturnTimeline]）。
 */
internal fun shouldUseLiveReturnMorph(
    transitionEnabled: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent,
    detailContentReady: Boolean = true,
    hasRenderableLiveFrame: Boolean = true,
    liveSurfaceCardTransitionEnabled: Boolean = false,
): Boolean = shouldUseVideoCardLiveReturnMorph(
    transitionEnabled = transitionEnabled,
    sharedBoundsActive = sharedBoundsActive,
    keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
    playbackIntent = playbackIntent,
    detailContentReady = detailContentReady,
    hasRenderableLiveFrame = hasRenderableLiveFrame,
    liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
)

/**
 * 详情页下方推荐/简介等是否已可安全参与 live morph。
 * Loading / 错误态仍可能画骨架或空壳，快速返回时不能当「实时组件」。
 */
internal fun shouldTreatVideoDetailContentReadyForLiveReturnMorph(
    hasSuccessfulDetailContent: Boolean,
): Boolean = hasSuccessfulDetailContent

/**
 * 入口播放意图只决定首次展开的视觉路径。
 * 当前视频已有可靠首帧后，返回应优先保留实时 surface，不能再退回静态封面。
 */
internal fun resolveVideoDetailReturnPlaybackIntent(
    entryPlaybackIntent: VideoSharedTransitionPlaybackIntent,
    hasRenderableLiveFrame: Boolean,
): VideoSharedTransitionPlaybackIntent {
    return if (hasRenderableLiveFrame) {
        VideoSharedTransitionPlaybackIntent.ImmediatePlayback
    } else {
        entryPlaybackIntent
    }
}

/**
 * 是否把视觉主导权交给常驻封面（forceCover / 藏 surface）。
 * live morph 时必须为 false，否则会出现「先切封面再缩小」。
 * ownership 真相见 [resolveVideoCardReturnCoverOwnership]。
 */
internal fun shouldHandVisualOwnershipToResidentCover(
    useReturningVisualState: Boolean,
    hasResidentCover: Boolean,
    liveReturnMorph: Boolean,
): Boolean {
    // 保持与 timeline 一致：live 时永不把视觉交给封面
    if (liveReturnMorph) return false
    return useReturningVisualState && hasResidentCover
}

/**
 * 详情返回 ownership 表入口（供 StateHolder / 测试直接断言三条路径）。
 */
internal fun resolveVideoDetailReturnCoverOwnership(
    transitionEnabled: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent,
    detailContentReady: Boolean,
    hasResidentCover: Boolean,
    hasRenderableLiveFrame: Boolean = true,
    liveSurfaceCardTransitionEnabled: Boolean = false,
    sourceLayout: VideoCardSourceLayout? = null,
) = resolveVideoCardReturnCoverOwnership(
    transitionEnabled = transitionEnabled,
    sharedBoundsActive = sharedBoundsActive,
    keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
    playbackIntent = playbackIntent,
    detailContentReady = detailContentReady,
    hasResidentCover = hasResidentCover,
    hasRenderableLiveFrame = hasRenderableLiveFrame,
    liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
    sourceLayout = sourceLayout,
)

internal fun isLiveReturnMorphFromOwnership(
    ownership: VideoCardReturnCoverOwnership,
): Boolean = isVideoCardLiveReturnMorphOwnership(ownership)

internal fun shouldHandResidentCoverFromOwnership(
    ownership: VideoCardReturnCoverOwnership,
    useReturningVisualState: Boolean,
    hasResidentCover: Boolean,
): Boolean = shouldHandVisualOwnershipToResidentCoverForOwnership(
    ownership = ownership,
    useReturningVisualState = useReturningVisualState,
    hasResidentCover = hasResidentCover,
)

/**
 * 返回会话 ownership 锁定包装（供详情页 state 接线）。
 * @see resolveReturnSessionLockedCoverOwnership
 */
internal fun resolveVideoDetailReturnSessionLockedOwnership(
    lockedOwnership: VideoCardReturnCoverOwnership?,
    isReturnSessionActive: Boolean,
    candidateOwnership: VideoCardReturnCoverOwnership,
): Pair<VideoCardReturnCoverOwnership?, VideoCardReturnCoverOwnership> {
    return resolveReturnSessionLockedCoverOwnership(
        lockedOwnership = lockedOwnership,
        isReturnSessionActive = isReturnSessionActive,
        candidateOwnership = candidateOwnership,
    )
}

internal data class VideoDetailReturnMediaFrame(
    val coverAlpha: Float,
    val playerAlpha: Float,
)

/**
 * One ownership decision for the two media layers. The resident cover is drawn above TextureView
 * and SurfaceView, including entry bootstrap before the player renders its first frame. The outer
 * navigation entry alpha is no longer responsible for hiding platform video surfaces.
 */
internal fun resolveVideoDetailReturnMediaFrame(
    transitionProgress: Float,
    isCommittedCardReturn: Boolean,
    hasResidentCover: Boolean,
    liveReturnMorph: Boolean = false,
    isReturnGestureInProgress: Boolean = false,
    showResidentCoverUntilFirstFrame: Boolean = false,
    progressiveResidentCover: Boolean = false,
    entryCoverAlpha: Float = 0f,
): VideoDetailReturnMediaFrame {
    if (!hasResidentCover) {
        return VideoDetailReturnMediaFrame(coverAlpha = 0f, playerAlpha = 1f)
    }
    if (showResidentCoverUntilFirstFrame) {
        return VideoDetailReturnMediaFrame(coverAlpha = 1f, playerAlpha = 0f)
    }
    val returnActive = isCommittedCardReturn || isReturnGestureInProgress
    if (!returnActive) {
        // First frame is already rendered: keep it opaque beneath the fading cover.
        // Return/seek ownership below deliberately ignores this entry-only effect.
        return VideoDetailReturnMediaFrame(
            coverAlpha = entryCoverAlpha.coerceIn(0f, 1f),
            playerAlpha = 1f,
        )
    }
    if (!liveReturnMorph) {
        if (progressiveResidentCover) {
            // Take over early, while the playback surface stays fully opaque underneath.
            // The same depth reverses on gesture cancellation; no new reveal is scheduled.
            val fraction = ((1f - transitionProgress.coerceIn(0f, 1f)) /
                RESIDENT_COVER_RETURN_TAKEOVER_DEPTH_SPAN)
                .coerceIn(0f, 1f)
            val coverAlpha = fraction * fraction * (3f - 2f * fraction)
            return VideoDetailReturnMediaFrame(
                coverAlpha = coverAlpha,
                playerAlpha = if (coverAlpha < 1f) 1f else 0f,
            )
        }
        return VideoDetailReturnMediaFrame(coverAlpha = 1f, playerAlpha = 0f)
    }
    // Keep the flying media slot opaque. Transparent cover + fading player is the empty
    // card: Miuix will not composite the list card through the flying clip.
    val coverTakeover = resolveVideoCardLiveReturnVisualHandoffAlpha(transitionProgress)
    return VideoDetailReturnMediaFrame(
        coverAlpha = coverTakeover,
        // 封面覆盖在视频上。两层互补淡出会在中点只剩 75% 合成不透明度。
        playerAlpha = if (coverTakeover < 1f) 1f else 0f,
    )
}

/** Conservative SurfaceView/Compose gate; older systems retain cover-only return. */
internal fun shouldUseProgressiveResidentCoverReturn(
    sdkInt: Int,
    ownsInlineCardTransition: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasDecodedResidentCover: Boolean,
    liveReturnMorph: Boolean,
    forceCoverOnly: Boolean,
    reduceMotion: Boolean,
): Boolean = sdkInt >= 35 && ownsInlineCardTransition && hasRenderedFirstFrame &&
    hasDecodedResidentCover && !liveReturnMorph && !forceCoverOnly && !reduceMotion

internal fun resolveVideoDetailReturnCoverAlpha(
    transitionProgress: Float,
    isCommittedCardReturn: Boolean,
    hasResidentCover: Boolean,
    liveReturnMorph: Boolean = false,
    keepLivePlayerForPredictiveBack: Boolean = false,
    showResidentCoverUntilFirstFrame: Boolean = false,
): Float = resolveVideoDetailReturnMediaFrame(
    transitionProgress = transitionProgress,
    isCommittedCardReturn = isCommittedCardReturn,
    hasResidentCover = hasResidentCover,
    liveReturnMorph = liveReturnMorph,
    isReturnGestureInProgress = keepLivePlayerForPredictiveBack,
    showResidentCoverUntilFirstFrame = showResidentCoverUntilFirstFrame,
).coverAlpha

/**
 * 返回画面交接使用的唯一进度源。
 *
 * 预测返回松手时，Nav3 会把 seek 阶段切换为已提交的 exit transition；这次切换中
 * AnimatedVisibility 的 progress 可能短暂投影到端点。实时 shared morph 若读取该值，
 * 会把常驻封面误判为已经落位并盖住播放器一帧。LIVE 路径因此只读与 sharedBounds、
 * 手势和提交补间连续的 card morph depth；封面优先/非 shared 路径继续使用 AVS。
 */
internal fun resolveVideoDetailReturnVisualProgress(
    animatedVisibilityProgress: Float,
    morphDepthProgress: Float,
    liveReturnMorph: Boolean,
): Float = if (liveReturnMorph) {
    morphDepthProgress.coerceIn(0f, 1f)
} else {
    animatedVisibilityProgress.coerceIn(0f, 1f)
}

internal fun resolveVideoDetailReturnPlayerAlpha(
    transitionProgress: Float,
    isCommittedCardReturn: Boolean,
    hasResidentCover: Boolean,
    liveReturnMorph: Boolean = false,
    keepLivePlayerForPredictiveBack: Boolean = false,
    showResidentCoverUntilFirstFrame: Boolean = false,
): Float = resolveVideoDetailReturnMediaFrame(
    transitionProgress = transitionProgress,
    isCommittedCardReturn = isCommittedCardReturn,
    hasResidentCover = hasResidentCover,
    liveReturnMorph = liveReturnMorph,
    isReturnGestureInProgress = keepLivePlayerForPredictiveBack,
    showResidentCoverUntilFirstFrame = showResidentCoverUntilFirstFrame,
).playerAlpha

/**
 * Resident / player-section cover — must match the **stationary list card** Coil request.
 *
 * Priority: session snapshot → click snapshot → retained entry source → home prefetch
 * registry → route cover (last resort, may not match list pixels).
 */
internal data class VideoDetailResidentCoverSource(
    val url: String,
    val cacheKey: String,
    val decodeWidthPx: Int = 0,
    val decodeHeightPx: Int = 0,
)

internal fun resolveVideoDetailResidentCoverSource(
    sourceChromeSnapshot: VideoCardSourceChromeSnapshot?,
    routeCoverUrl: String?,
    bvid: String,
    clickChromeSnapshot: VideoCardSourceChromeSnapshot? = null,
    prefetchUrl: String? = null,
    prefetchCacheKey: String? = null,
    prefetchDecodeWidthPx: Int = 0,
    prefetchDecodeHeightPx: Int = 0,
    retainedStationarySource: VideoDetailResidentCoverSource? = null,
): VideoDetailResidentCoverSource? {
    fun fromSnapshot(snapshot: VideoCardSourceChromeSnapshot?): VideoDetailResidentCoverSource? {
        val url = snapshot?.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val key = snapshot.coverCacheKey.trim().takeIf { it.isNotBlank() } ?: url
        return VideoDetailResidentCoverSource(
            url = url,
            cacheKey = key,
            decodeWidthPx = snapshot.coverDecodeWidthPx.coerceAtLeast(0),
            decodeHeightPx = snapshot.coverDecodeHeightPx.coerceAtLeast(0),
        )
    }
    fromSnapshot(sourceChromeSnapshot)?.let { return it }
    fromSnapshot(clickChromeSnapshot)?.let { return it }
    // 导航可能先清理来源元数据，再卸载详情 entry；落位末帧不能换成兜底图片。
    retainedStationarySource?.let { return it }
    val prefetch = prefetchUrl?.trim()?.takeIf { it.isNotBlank() }
    val prefetchKey = prefetchCacheKey?.trim()?.takeIf { it.isNotBlank() }
    if (prefetch != null && prefetchKey != null) {
        return VideoDetailResidentCoverSource(
            url = prefetch,
            cacheKey = prefetchKey,
            decodeWidthPx = prefetchDecodeWidthPx.coerceAtLeast(0),
            decodeHeightPx = prefetchDecodeHeightPx.coerceAtLeast(0),
        )
    }
    val route = routeCoverUrl?.trim().orEmpty().let { url ->
        when {
            url.isBlank() -> ""
            url.startsWith("https://") -> url
            url.startsWith("http://") -> url.replace("http://", "https://")
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }
    if (route.isBlank()) return null
    return VideoDetailResidentCoverSource(
        url = route,
        cacheKey = resolveVideoSharedCoverCacheKey(bvid.trim()),
    )
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveVideoDetailReturnContentAlpha(
    transitionProgress: Float,
    isCommittedCardReturn: Boolean,
    holdFullyOpaqueAfterBackPreview: Boolean = false,
    liveReturnMorph: Boolean = false,
    depthBlurProgress: Float? = null,
    isQuickReturn: Boolean = false,
    /**
     * Miuix live 返回只消费 shared flying-card 的 morph depth；保留可空参数以兼容
     * 非 Miuix 调用。
     */
    morphDepthProgress: Float? = null,
): Float {
    // The detail controls/body and source-card text share one late morph window. The moving card
    // shell remains opaque; only corresponding contents change ownership inside it.
    if (liveReturnMorph) {
        return resolveVideoCardLiveMorphSecondaryContentAlpha(
            transitionProgress = transitionProgress,
            depthBlurProgress = depthBlurProgress,
            morphDepthProgress = morphDepthProgress,
        )
    }
    if (isCommittedCardReturn) return 0f
    if (holdFullyOpaqueAfterBackPreview) return 1f
    return transitionProgress.coerceIn(0f, 1f)
}

internal fun shouldTreatVideoDetailCardExitAsReturning(
    isExitTransitionInProgress: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean = false,
    entryOwnsCardTransition: Boolean = true,
): Boolean {
    return entryOwnsCardTransition &&
        isExitTransitionInProgress &&
        sharedBoundsActive &&
        !keepLoadedContentForBackPreview
}

/**
 * Whether the detail page should treat the current frame as an exit/return morph.
 *
 * Primary signal: AnimatedVisibility [EnterExitState.PostExit].
 * Fallback for Navigation3 1.2 + [ExitTransition.None]: AVS may settle without a durable
 * PostExit observation, while [VideoCardTransitionBackgroundPhase.RETURNING] is still true.
 */
internal fun shouldTreatVideoDetailExitTransitionInProgress(
    animatedVisibilityTargetIsPostExit: Boolean,
    videoCardBackgroundPhase: VideoCardTransitionBackgroundPhase?,
): Boolean {
    if (animatedVisibilityTargetIsPostExit) return true
    return videoCardBackgroundPhase == VideoCardTransitionBackgroundPhase.RETURNING
}

/**
 * 返回 morph 期间是否保活播放会话（surface）。
 *
 * 预测返回 **轻滑即松手** 时：栈先 pop → 入口 `isVisible=false`，但详情仍在
 * sharedBounds overlay 里缩回。若此时掐掉 playbackSession，surface 变黑，
 * 慢放可见「动画前半段卡片消失」；手指拖到一半再松手时，seek 阶段仍 visible，
 * 所以往往复现不了。
 *
 * 规则：栈顶可见 **或**（shell shared 且正在 exit transition）→ 保活。
 */
internal fun shouldKeepPlaybackSessionActiveForSharedReturnMorph(
    isVisible: Boolean,
    sharedBoundsActive: Boolean,
    isExitTransitionInProgress: Boolean,
): Boolean {
    if (isVisible) return true
    return sharedBoundsActive && isExitTransitionInProgress
}

/**
 * 返回 shared morph 时强制展开详情播放器视口。
 *
 * 用户在简介/相关列表下滑后，inline 播放器会折叠到很矮的高度；若此时手势返回，
 * sharedBounds 从折叠框起算 + ContentScale.Crop → 封面只剩中间一截（截图里的「不完整」）。
 * 顶部未下滑时视口是完整 16:9，所以返回封面正常。
 *
 * 规则：只要处于返回手势或已提交的 exit morph，布局层把折叠进度压为 0，
 * 让飞行壳带着完整封面缩回列表卡。
 */
internal fun shouldExpandPlayerViewportForSharedReturn(
    isExitTransitionInProgress: Boolean,
    isReturnGestureInProgress: Boolean,
    isGestureRestoreInProgress: Boolean = false,
    sharedReturnLikely: Boolean = true,
): Boolean {
    if (!sharedReturnLikely) return false
    return isExitTransitionInProgress ||
        isReturnGestureInProgress ||
        isGestureRestoreInProgress
}

/**
 * 是否在飞行详情壳内绘制信息区。
 *
 * **必须为 true**：sharedBounds 飞行层盖在列表之上，列表真卡即使 alpha=1 也看不见；
 * 信息区只能画在飞行壳上。列表真卡在 morph 结束后再露（cover/chrome stationary reveal）。
 *
 * 文案来自点击时冻结的 [VideoCardSourceChromeSnapshot] + 详情 ViewInfo，尽量与列表卡一致。
 */
/**
 * 布局用折叠进度：返回 morph 中强制 0（展开），其余沿用手势/评论折叠进度。
 */
internal fun resolvePlayerCollapseProgressForLayout(
    manualOrCompactCollapseProgress: Float,
    expandForSharedReturn: Boolean,
): Float {
    if (expandForSharedReturn) return 0f
    return manualOrCompactCollapseProgress.coerceIn(0f, 1f)
}

internal fun resolveVideoDetailCollapseShadowAlpha(collapseProgress: Float): Float =
    collapseProgress.coerceIn(0f, 1f) * 0.16f

/** Darkens the still-visible media while it collapses, without washing it out in light theme. */
internal fun resolveCollapsedPlayerMediaScrimAlpha(collapseProgress: Float): Float =
    collapseProgress.coerceIn(0f, 1f) * 0.42f

/** The solid toolbar should only take over near the end of the user-driven collapse. */
internal fun resolveCollapsedPlayerToolbarAlpha(collapseProgress: Float): Float =
    ((collapseProgress.coerceIn(0f, 1f) - 0.55f) / 0.45f).coerceIn(0f, 1f)

internal fun shouldForceBackPreviewPlayerCover(
    keepLoadedContentForBackPreview: Boolean,
    bindLivePlayerForBackPreview: Boolean
): Boolean {
    return keepLoadedContentForBackPreview && !bindLivePlayerForBackPreview
}

/**
 * 相关推荐「详情压详情」返回：父页刚从 back-preview 恢复时，
 * 若立刻按进场过渡把内容 alpha 从 0 淡入，会整页闪一下（滚动位置仍在）。
 */
internal fun shouldSuppressVideoDetailEnterFadeAfterBackPreview(
    wasKeptAsBackPreview: Boolean,
    keepLoadedContentForBackPreview: Boolean,
): Boolean {
    return wasKeptAsBackPreview && !keepLoadedContentForBackPreview
}

internal fun shouldUseVideoDetailRootTransitionProgress(
    detailShellSharedBoundsEnabled: Boolean,
    hasAnimatedVisibilityScope: Boolean,
    keepLoadedContentForBackPreview: Boolean,
): Boolean {
    return detailShellSharedBoundsEnabled &&
        hasAnimatedVisibilityScope &&
        !keepLoadedContentForBackPreview
}

/**
 * 详情正文是否挂载。
 *
 * 首次入场显式延后重正文时，以静态骨架承接数据先到、导航未落位的区间，
 * 不在 morph 中触发简介列表的子组合与测量。已加载的返回预览不重新关闭门控。
 * 未启用首次挂载门控的入口仍允许根过渡控制提前挂载正文的 alpha。
 * 退场仍要求入场已完成，避免已让位的正文再次出现。
 */
internal fun shouldShowVideoDetailContent(
    isTransitionFinished: Boolean,
    isLeaving: Boolean,
    rootTransitionOwnsContentAlpha: Boolean,
    keepContentVisibleAfterBackPreview: Boolean = false,
    deferFreshEntryContent: Boolean = false,
): Boolean {
    if (keepContentVisibleAfterBackPreview && !isLeaving) return true
    if (!isLeaving && rootTransitionOwnsContentAlpha && !deferFreshEntryContent) return true
    return isTransitionFinished && (!isLeaving || rootTransitionOwnsContentAlpha)
}

internal fun resolveCoverTakeoverDelayBeforeBackNavigationMillis(): Long {
    // 封面常驻并直接读取根过渡进度，不再需要先抢一帧切换封面再导航。
    return COVER_TAKEOVER_PRE_BACK_DELAY_MILLIS
}

internal data class VideoDetailRouteSheetMotion(
    val enabled: Boolean,
    val durationMillis: Int,
    val mainDurationMillis: Int,
    val settleDurationMillis: Int,
    val initialScale: Float,
    val initialTranslationYDp: Float,
    val initialCornerDp: Float,
    val initialBackgroundScrimAlpha: Float,
    val settleScaleDelta: Float,
    val settleTranslationDp: Float,
    val enterEasing: Easing,
    val returnEasing: Easing
)

internal enum class VideoDetailRouteSheetSettleDirection {
    None,
    Enter,
    Return
}

internal data class VideoDetailRouteSheetFrame(
    val scale: Float,
    val translationYDp: Float,
    val cornerDp: Float,
    val backgroundScrimAlpha: Float,
    val settleProgress: Float
)

internal data class VideoDetailSecondaryContentTiming(
    val enterDelayMillis: Int,
    val enterDurationMillis: Int,
    val returnDelayMillis: Int,
    val returnDurationMillis: Int
)

internal fun resolveVideoDetailSecondaryContentTiming(
    fullDurationMillis: Int,
    contentDelayMillis: Int,
    contentDurationMillis: Int,
): VideoDetailSecondaryContentTiming {
    val safeDuration = fullDurationMillis.coerceAtLeast(0)
    val safeEnterDelay = contentDelayMillis.coerceIn(0, safeDuration)
    val safeEnterDuration = contentDurationMillis
        .coerceAtLeast(0)
        .coerceAtMost(safeDuration - safeEnterDelay)
    val safeReturnDuration = contentDurationMillis.coerceIn(0, safeDuration)
    return VideoDetailSecondaryContentTiming(
        enterDelayMillis = safeEnterDelay,
        enterDurationMillis = safeEnterDuration,
        returnDelayMillis = 0,
        returnDurationMillis = safeReturnDuration
    )
}

internal data class VideoDetailMotionSpec(
    val entryPhaseDurationMillis: Int,
    val contentSwapFadeDurationMillis: Int,
    val contentRevealFadeDurationMillis: Int
)

private const val VIDEO_DETAIL_ENTRY_PHASE_MIN_DURATION_MILLIS = 120
private const val VIDEO_DETAIL_CONTENT_PHASE_MIN_DURATION_MILLIS = 180
private const val HOME_VIDEO_ROUTE_SHEET_MAIN_DURATION_MILLIS = 320
private const val HOME_VIDEO_ROUTE_SHEET_SETTLE_DURATION_MILLIS = 96
private const val HOME_VIDEO_ROUTE_SHEET_DURATION_MILLIS =
    HOME_VIDEO_ROUTE_SHEET_MAIN_DURATION_MILLIS + HOME_VIDEO_ROUTE_SHEET_SETTLE_DURATION_MILLIS
private const val HOME_VIDEO_ROUTE_SHEET_INITIAL_SCALE = 0.965f
private const val HOME_VIDEO_ROUTE_SHEET_INITIAL_TRANSLATION_Y_DP = 56f
private const val HOME_VIDEO_ROUTE_SHEET_INITIAL_CORNER_DP = 28f
private const val HOME_VIDEO_ROUTE_SHEET_INITIAL_SCRIM_ALPHA = 0.18f
private const val HOME_VIDEO_ROUTE_SHEET_SETTLE_SCALE_DELTA = 0.0015f
private const val HOME_VIDEO_ROUTE_SHEET_SETTLE_TRANSLATION_DP = 1.5f

internal fun resolveVideoDetailMotionSpec(
    transitionEnterDurationMillis: Int
): VideoDetailMotionSpec {
    return VideoDetailMotionSpec(
        entryPhaseDurationMillis = transitionEnterDurationMillis
            .coerceAtLeast(VIDEO_DETAIL_ENTRY_PHASE_MIN_DURATION_MILLIS),
        contentSwapFadeDurationMillis = transitionEnterDurationMillis
            .coerceAtLeast(VIDEO_DETAIL_CONTENT_PHASE_MIN_DURATION_MILLIS),
        contentRevealFadeDurationMillis = transitionEnterDurationMillis
            .coerceAtLeast(VIDEO_DETAIL_CONTENT_PHASE_MIN_DURATION_MILLIS)
    )
}

internal fun resolveVideoDetailRouteSheetMotion(
    sourceRoute: String?,
    transitionEnabled: Boolean
): VideoDetailRouteSheetMotion {
    val enabled = transitionEnabled &&
        com.android.purebilibili.navigation.isVideoCardReturnTargetRoute(sourceRoute)
    return VideoDetailRouteSheetMotion(
        enabled = enabled,
        durationMillis = HOME_VIDEO_ROUTE_SHEET_DURATION_MILLIS,
        mainDurationMillis = HOME_VIDEO_ROUTE_SHEET_MAIN_DURATION_MILLIS,
        settleDurationMillis = HOME_VIDEO_ROUTE_SHEET_SETTLE_DURATION_MILLIS,
        initialScale = HOME_VIDEO_ROUTE_SHEET_INITIAL_SCALE,
        initialTranslationYDp = HOME_VIDEO_ROUTE_SHEET_INITIAL_TRANSLATION_Y_DP,
        initialCornerDp = HOME_VIDEO_ROUTE_SHEET_INITIAL_CORNER_DP,
        initialBackgroundScrimAlpha = HOME_VIDEO_ROUTE_SHEET_INITIAL_SCRIM_ALPHA,
        settleScaleDelta = HOME_VIDEO_ROUTE_SHEET_SETTLE_SCALE_DELTA,
        settleTranslationDp = HOME_VIDEO_ROUTE_SHEET_SETTLE_TRANSLATION_DP,
        enterEasing = resolveVideoCardSharedTransitionEnterEasing(),
        returnEasing = resolveVideoCardSharedTransitionReturnEasing()
    )
}

internal fun resolveVideoDetailRouteSheetFrame(
    rawProgress: Float,
    settleProgress: Float = 0f,
    settleDirection: VideoDetailRouteSheetSettleDirection = VideoDetailRouteSheetSettleDirection.None,
    motion: VideoDetailRouteSheetMotion
): VideoDetailRouteSheetFrame {
    if (!motion.enabled) {
        return VideoDetailRouteSheetFrame(
            scale = 1f,
            translationYDp = 0f,
            cornerDp = 0f,
            backgroundScrimAlpha = 0f,
            settleProgress = 0f
        )
    }
    val progress = rawProgress.coerceIn(0f, 1f)
    val safeSettleProgress = settleProgress.coerceIn(0f, 1f)
    val settleScale = when (settleDirection) {
        VideoDetailRouteSheetSettleDirection.Enter -> motion.settleScaleDelta * safeSettleProgress
        VideoDetailRouteSheetSettleDirection.Return -> -motion.settleScaleDelta * safeSettleProgress
        VideoDetailRouteSheetSettleDirection.None -> 0f
    }
    val settleTranslation = when (settleDirection) {
        VideoDetailRouteSheetSettleDirection.Enter -> -motion.settleTranslationDp * safeSettleProgress
        VideoDetailRouteSheetSettleDirection.Return -> motion.settleTranslationDp * safeSettleProgress
        VideoDetailRouteSheetSettleDirection.None -> 0f
    }
    return VideoDetailRouteSheetFrame(
        scale = lerpVideoDetailFloat(motion.initialScale, 1f, progress) + settleScale,
        translationYDp = lerpVideoDetailFloat(motion.initialTranslationYDp, 0f, progress) + settleTranslation,
        cornerDp = lerpVideoDetailFloat(motion.initialCornerDp, 0f, progress),
        backgroundScrimAlpha = lerpVideoDetailFloat(motion.initialBackgroundScrimAlpha, 0f, progress),
        settleProgress = safeSettleProgress
    )
}

private fun lerpVideoDetailFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
