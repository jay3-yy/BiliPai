package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier

/**
 * 详情 → 来源卡片返回的**单一时间轴契约**（纯 Kotlin，无 Compose）。
 *
 * 目标：
 * - 导航 seek / sharedBounds morph / 景深 blur / 封面 ownership / 源卡 chrome 共用同一套数字
 * - feature 接线层只读这里的结果，不再各自发明时长或 ownership 分支
 *
 * 三条封面路径：
 * - [VideoCardReturnCoverOwnership.LIVE_SURFACE]：实时画面跟壳缩（一镜到底）
 * - [VideoCardReturnCoverOwnership.RESIDENT_COVER]：常驻封面接管
 * - [VideoCardReturnCoverOwnership.FALLBACK_NO_SHARED]：无 shared 配对，依赖路由层动画
 */

/**
 * 源卡 sharedBounds Enter 延后淡入比例（遗留字段）。
 *
 * **当前策略：始终 0 / 不延后整壳 Enter。**
 * 来源卡资源全程待命；Miuix 路径由飞行详情 entry 内的封面/文字在返回后半段
 * 分段接管。整壳 delayed fadeIn 会在 overlay 卸层瞬间造成二次叠化，是落位闪烁主因。
 */
internal const val VIDEO_CARD_RETURN_SOURCE_ENTER_FADE_DELAY_RATIO = 0f

/**
 * 源卡 chrome（标题/UP）在返回 settle 进度上的淡入起点。
 * live 正文在此点起让位。该字段保留给详情页次要 chrome；来源卡像素改由
 * [resolveVideoCardLiveReturnVisualHandoffAlpha] 控制。
 */
internal object VideoCardTransitionVisualTimeline {
    const val DETAIL_CHROME_ENTER_START = 0.18f
    const val DETAIL_CHROME_ENTER_END = 0.72f
    const val SECONDARY_CONTENT_ENTER_START = 0.24f
    const val SECONDARY_CONTENT_ENTER_END = 0.82f
    // Keep content readable at gesture pickup, then yield alongside viewport contraction.
    // Finish at the source chrome handoff, without an empty shell between the two.
    const val NATIVE_BODY_RETURN_START = 0.10f
    const val NATIVE_BODY_RETURN_END = 0.82f
    // The frozen background page starts yielding before the flying card's internal content morph.
    // This only refreshes the backdrop; it never reveals the source card as a stationary substitute.
    const val WHOLE_SOURCE_CARD_RETURN_START = 0.55f
    // Must equal [MEDIA_RETURN_START] so the opening snapshot is gone before native chrome.
    const val WHOLE_SOURCE_CARD_RETURN_END = 0.82f
    // Live player → cover. Keep the live frame dominant until the card is very close to landing.
    const val MEDIA_RETURN_START = 0.82f
    const val MEDIA_RETURN_END = 0.98f
    // 整卡位图比单独封面更早接管，完整像素与媒体在同一几何端点完成交接。
    const val WHOLE_CARD_RETURN_START = WHOLE_SOURCE_CARD_RETURN_START
    const val WHOLE_CARD_RETURN_END = MEDIA_RETURN_END
    // Cover pixels and the info band under the cover must hand off together. Opening still
    // uses [DETAIL_CHROME_ENTER_*] for the detail body; return chrome shares the live → cover
    // window so title/stats never appear on an empty cover slot.
    const val SOURCE_CHROME_RETURN_START = MEDIA_RETURN_START
    const val SOURCE_CHROME_RETURN_END = MEDIA_RETURN_END
    // Remeasure the flying media to the frozen cover box BEFORE title/UP/stats fade in.
    const val MEDIA_LAYOUT_RETURN_START = 0f
    const val MEDIA_LAYOUT_RETURN_END = SOURCE_CHROME_RETURN_START
    const val SECONDARY_CONTENT_TRANSLATION_DP = 8
    const val REDUCED_MOTION_DURATION_MILLIS = 140
}

/** Spatial landing compression never enters this coordinate system. Do not ease it a second time. */
internal fun resolveVisualProgress(
    morphProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    direction: VideoSharedTransitionDirection,
): Float {
    val depth = if (phase == VideoCardTransitionBackgroundPhase.IDLE) 0f else {
        morphProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    }
    return if (direction == VideoSharedTransitionDirection.RETURN) 1f - depth else depth
}

/**
 * Frozen source-page snapshot release progress during a Miuix return.
 *
 * This releases the stale depth snapshot so the live list card can punch through the flying
 * entry. It must finish before the live → cover window, otherwise the opening-time snapshot
 * (titles hidden) covers the native chrome.
 */
internal fun resolveVideoCardWholeSourceReturnAlpha(
    morphDepthProgress: Float,
): Float = resolveVideoCardTimelineWindowProgress(
    progress = resolveVideoCardReturnSettleFromMorphDepth(morphDepthProgress),
    start = VideoCardTransitionVisualTimeline.WHOLE_SOURCE_CARD_RETURN_START,
    end = VideoCardTransitionVisualTimeline.WHOLE_SOURCE_CARD_RETURN_END,
)

internal const val VIDEO_CARD_RETURN_CHROME_REVEAL_START =
    VideoCardTransitionVisualTimeline.SOURCE_CHROME_RETURN_START

/**
 * 来源卡标题/UP/统计在飞卡上的交接窗口。与 live → 封面同一时段，
 * 封面和底部信息作为一张卡同步出现，而不是字先于封面露出来。
 */
internal fun resolveVideoCardSourceChromeReturnAlpha(
    morphDepthProgress: Float,
): Float = resolveVideoCardLiveReturnVisualHandoffAlpha(morphDepthProgress)

/** 交接窗两端斜率归零；只用于内容叠化，不改变跟手几何。 */
private fun resolveVideoCardSmoothHandoffProgress(
    progress: Float,
    start: Float,
    end: Float,
): Float {
    val u = resolveVideoCardTimelineWindowProgress(progress, start, end)
    return u * u * (3f - 2f * u)
}

/**
 * 飞行卡内部内容形变窗口。详情控制器/信息在此窗口退出，来源卡标题/统计按互补
 * alpha 进入；两者都跟随同一个 shared-bounds 壳移动，不在列表原位静态交接。
 */
internal const val VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_START =
    VideoCardTransitionVisualTimeline.SOURCE_CHROME_RETURN_START
internal const val VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_END =
    VideoCardTransitionVisualTimeline.SOURCE_CHROME_RETURN_END

/**
 * 实时画面缩回时，播放器在 82%–98% 的最后落位段才变为封面。
 * 来源标题/UP/统计走同一窗口，和封面同步交接。
 */
internal const val VIDEO_CARD_LIVE_RETURN_VISUAL_HANDOFF_START =
    VideoCardTransitionVisualTimeline.MEDIA_RETURN_START

/**
 * live surface → 常驻封面的唯一媒体交接 alpha。
 *
 * 资源可以全程驻留，但在实时画面主导阶段必须保持透明；否则列表封面会与 player
 * surface 叠层。详情侧 resident cover、来源卡封面和标题/UP/统计都使用此窗口。
 */
internal fun resolveVideoCardLiveReturnVisualHandoffAlpha(
    morphDepthProgress: Float,
): Float = resolveVideoCardSmoothHandoffProgress(
    progress = resolveVideoCardReturnSettleFromMorphDepth(morphDepthProgress),
    start = VideoCardTransitionVisualTimeline.MEDIA_RETURN_START,
    end = VideoCardTransitionVisualTimeline.MEDIA_RETURN_END,
)

/** 整卡快照的交接与媒体共用深度、平滑端点和完成边界，反向 seek 可直接恢复。 */
internal fun resolveVideoCardWholeCardReturnHandoffAlpha(
    morphDepthProgress: Float,
): Float = resolveVideoCardSmoothHandoffProgress(
    progress = resolveVideoCardReturnSettleFromMorphDepth(morphDepthProgress),
    start = VideoCardTransitionVisualTimeline.WHOLE_CARD_RETURN_START,
    end = VideoCardTransitionVisualTimeline.WHOLE_CARD_RETURN_END,
)

/**
 * Stacked media reaches its cover slot before chrome reveal. Horizontal media and its
 * adjacent info region share one handoff, so shrinking media never exposes an empty panel.
 */
internal fun resolveVideoDetailReturnMediaLayoutHandoffProgress(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    sourceLayout: VideoCardSourceLayout,
    sourceChromeAlpha: Float? = null,
): Float {
    val depth = morphDepthProgress.coerceIn(0f, 1f)
    if (sourceLayout == VideoCardSourceLayout.SIDE_BY_SIDE) {
        return sourceChromeAlpha?.coerceIn(0f, 1f) ?: (1f - resolveVideoCardDetailChromeAlpha(
            morphDepthProgress = depth,
            phase = phase,
            isReturnGestureInProgress = isReturnGestureInProgress,
            sourceLayout = sourceLayout,
        ))
    }
    // Remeasure only on an actual return. HELD with a tiny depth dip is enough to squash a
    // 16:9 inline player toward a related-card 16:10 cover while the user is still watching.
    if (
        !isReturnGestureInProgress &&
        phase != VideoCardTransitionBackgroundPhase.RETURNING
    ) {
        return 0f
    }
    if (sourceLayout == VideoCardSourceLayout.COVER_ONLY) return 0f
    return resolveVideoCardTimelineWindowProgress(
        progress = resolveVideoCardReturnSettleFromMorphDepth(depth),
        start = VideoCardTransitionVisualTimeline.MEDIA_LAYOUT_RETURN_START,
        end = VideoCardTransitionVisualTimeline.MEDIA_LAYOUT_RETURN_END,
    )
}

/**
 * Detail body (player 下方) visual frame during flying-card morph.
 *
 * [scale] shrinks toward the source-card info size so alpha crossfade is not the only cue —
 * size mismatch between detail typography and card chrome is the main 穿帮 source.
 */
internal data class VideoCardSecondaryContentVisualFrame(
    val alpha: Float,
    val translationYDp: Float,
    /** 1 = full detail layout; approaches [VIDEO_CARD_SECONDARY_YIELD_MIN_SCALE] as chrome takes over. */
    val scale: Float = 1f,
    /** 0 = full detail ownership; 1 = fully yielded to source chrome. */
    val handoffProgress: Float = 0f,
)

/**
 * Source-card chrome visual frame; complementary to [VideoCardSecondaryContentVisualFrame].
 *
 * [layoutScaleMultiplier] is always **1** at every handoff step so the resting frame matches the
 * stationary list card (no mid-flight size boost that leaves a second plate at land).
 */
internal data class VideoCardSourceChromeVisualFrame(
    val alpha: Float,
    val layoutScaleMultiplier: Float = 1f,
    val handoffProgress: Float = 0f,
)

/** Detail body minimum uniform scale at full handoff (still readable mid-blend, not a hard pop). */
internal const val VIDEO_CARD_SECONDARY_YIELD_MIN_SCALE = 0.88f

internal fun resolveVideoCardTimelineWindowProgress(
    progress: Float,
    start: Float,
    end: Float,
): Float {
    val value = progress.coerceIn(0f, 1f)
    val safeStart = start.coerceIn(0f, 1f)
    val safeEnd = end.coerceIn(safeStart, 1f)
    if (value <= safeStart) return 0f
    if (safeEnd <= safeStart) return 1f
    return ((value - safeStart) / (safeEnd - safeStart)).coerceIn(0f, 1f)
}

/** Shared, reversible envelope for native body opacity and the flying viewport's height. */
internal fun resolveVideoCardNativeBodyPresence(morphDepthProgress: Float): Float {
    val progress = resolveVideoCardTimelineWindowProgress(
        progress = 1f - morphDepthProgress,
        start = VideoCardTransitionVisualTimeline.NATIVE_BODY_RETURN_START,
        end = VideoCardTransitionVisualTimeline.NATIVE_BODY_RETURN_END,
    )
    return 1f - progress * progress * (3f - 2f * progress)
}

/**
 * Whether detail body / controls must yield to source-card chrome on the flying entry.
 *
 * Miuix predictive seek often keeps [VideoCardTransitionBackgroundPhase.HELD] while
 * [morphDepthProgress] drops with the finger. Requiring only RETURNING/gesture flags leaves
 * detail content fully opaque under a fading-in landing chrome → visual 穿帮.
 */
internal fun isVideoCardReturnContentYieldActive(
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    morphDepthProgress: Float,
): Boolean {
    if (isReturnGestureInProgress) return true
    if (phase == VideoCardTransitionBackgroundPhase.RETURNING) return true
    // Depth-only return seek: HELD contract is full detail (1); any dip means yield window.
    if (phase == VideoCardTransitionBackgroundPhase.HELD &&
        morphDepthProgress.coerceIn(0f, 1f) < 0.999f
    ) {
        return true
    }
    return false
}

/** Shared 0→1 handoff used by detail body and source chrome (alpha + size). */
internal fun resolveVideoCardContentHandoffProgress(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
): Float {
    val depth = resolveVisualProgress(morphDepthProgress, phase, VideoSharedTransitionDirection.ENTER)
    if (!isVideoCardReturnContentYieldActive(
            phase = phase,
            isReturnGestureInProgress = isReturnGestureInProgress,
            morphDepthProgress = depth,
        )
    ) {
        return 0f
    }
    return resolveVideoCardSourceChromeReturnAlpha(depth)
}

internal fun resolveVideoCardDetailChromeAlpha(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    /**
     * 卡片返回是否**已提交**（`markReturning` / 用户点返回）。
     *
     * 提交后 morph 会走到 `phase == IDLE`，但此时详情 entry 往往还没被卸载
     * （NavHost 退场动画仍在跑）。若不区分，`else -> 1f` 会把已经让位的详情 chrome
     * 重新弹成不透明，表现为「飞行壳已落位、详情 chrome 却整块闪现在卡槽」。
     */
    isCommittedCardReturn: Boolean = false,
    sourceLayout: VideoCardSourceLayout? = null,
): Float {
    val depth = resolveVisualProgress(morphDepthProgress, phase, VideoSharedTransitionDirection.ENTER)
    // Horizontal cover geometry and right-hand chrome must follow the same envelope as
    // the native shell height. A late chrome-only handoff leaves a full-width player
    // inside an already short clip, cutting faces and turning the cover into a strip.
    if (sourceLayout == VideoCardSourceLayout.SIDE_BY_SIDE &&
        (phase == VideoCardTransitionBackgroundPhase.OPENING ||
            isVideoCardReturnContentYieldActive(phase, isReturnGestureInProgress, depth))
    ) return resolveVideoCardNativeBodyPresence(depth)
    val handoff = resolveVideoCardContentHandoffProgress(
        morphDepthProgress = depth,
        phase = phase,
        isReturnGestureInProgress = isReturnGestureInProgress,
    )
    return when {
        // Complementary to source chrome; same window as body yield.
        handoff > 0f ||
            isVideoCardReturnContentYieldActive(
                phase = phase,
                isReturnGestureInProgress = isReturnGestureInProgress,
                morphDepthProgress = depth,
            ) -> 1f - handoff
        phase == VideoCardTransitionBackgroundPhase.OPENING ->
            resolveVideoCardTimelineWindowProgress(
                progress = depth,
                start = VideoCardTransitionVisualTimeline.DETAIL_CHROME_ENTER_START,
                end = VideoCardTransitionVisualTimeline.DETAIL_CHROME_ENTER_END,
            )
        // IDLE 且返回已提交：morph 已 settle，详情页正在退场，chrome 必须保持让位。
        isCommittedCardReturn && phase == VideoCardTransitionBackgroundPhase.IDLE -> 0f
        else -> 1f
    }
}

internal fun resolveVideoCardSecondaryContentVisualFrame(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    motionTier: MotionTier,
    sourceLayout: VideoCardSourceLayout = VideoCardSourceLayout.STACKED,
    /**
     * 卡片返回是否**已提交**（`markReturning` / 用户点返回）。见
     * [resolveVideoCardDetailChromeAlpha] 的同名参数：用于堵住 `phase == IDLE`
     * 时 `else -> 1f` 把已让位的正文弹回不透明的缺口。
     */
    isCommittedCardReturn: Boolean = false,
    preserveNativeViewport: Boolean = false,
): VideoCardSecondaryContentVisualFrame {
    val depth = resolveVisualProgress(morphDepthProgress, phase, VideoSharedTransitionDirection.ENTER)
    val returning = isVideoCardReturnContentYieldActive(
        phase = phase,
        isReturnGestureInProgress = isReturnGestureInProgress,
        morphDepthProgress = depth,
    )
    // Only the existing native-viewport path uses the shared envelope. Other material /
    // shared-bounds paths keep their own timing; media and source chrome keep the late handoff.
    val handoff = resolveVideoCardSourceChromeVisualFrame(
        morphDepthProgress = depth,
        phase = phase,
        isReturnGestureInProgress = isReturnGestureInProgress,
        sourceLayout = sourceLayout,
    ).handoffProgress
    val alpha = when {
        preserveNativeViewport && motionTier != MotionTier.Reduced && returning ->
            resolveVideoCardNativeBodyPresence(depth)
        preserveNativeViewport && motionTier != MotionTier.Reduced &&
            phase == VideoCardTransitionBackgroundPhase.OPENING ->
            resolveVideoCardNativeBodyPresence(depth)
        // Detail body yields in the exact live → cover/source-card chrome window.
        returning -> 1f - handoff
        motionTier == MotionTier.Reduced -> depth
        phase == VideoCardTransitionBackgroundPhase.OPENING ->
            resolveVideoCardTimelineWindowProgress(
                progress = depth,
                start = VideoCardTransitionVisualTimeline.SECONDARY_CONTENT_ENTER_START,
                end = VideoCardTransitionVisualTimeline.SECONDARY_CONTENT_ENTER_END,
            )
        // IDLE 且返回已提交：morph 已 settle 但详情 entry 可能仍在退场动画里。
        // 此时正文必须保持让位（0），否则标题/简介/评论会以不透明整块闪现在卡槽。
        isCommittedCardReturn && phase == VideoCardTransitionBackgroundPhase.IDLE -> 0f
        else -> 1f
    }
    val scale = if (returning && motionTier != MotionTier.Reduced) {
        1f - (1f - VIDEO_CARD_SECONDARY_YIELD_MIN_SCALE) * handoff
    } else {
        1f
    }
    return VideoCardSecondaryContentVisualFrame(
        alpha = alpha,
        translationYDp = if (motionTier == MotionTier.Reduced) {
            0f
        } else {
            VideoCardTransitionVisualTimeline.SECONDARY_CONTENT_TRANSLATION_DP.toFloat() *
                (1f - alpha)
        },
        scale = scale.coerceIn(VIDEO_CARD_SECONDARY_YIELD_MIN_SCALE, 1f),
        handoffProgress = if (returning) handoff else 0f,
    )
}

/**
 * Source chrome alpha (title / UP / stats + card shell under live media).
 *
 * [VideoCardSourceLayout.STACKED] and [VideoCardSourceLayout.SIDE_BY_SIDE] share the live →
 * cover window so title/stats appear with the cover, not before it.
 */
internal fun resolveVideoCardSourceChromeVisualFrame(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
    isReturnGestureInProgress: Boolean = true,
    sourceLayout: VideoCardSourceLayout = VideoCardSourceLayout.STACKED,
): VideoCardSourceChromeVisualFrame {
    val depth = morphDepthProgress.coerceIn(0f, 1f)
    val yieldActive = isVideoCardReturnContentYieldActive(
        phase = phase,
        isReturnGestureInProgress = isReturnGestureInProgress,
        morphDepthProgress = depth,
    )
    val handoff = when {
        !yieldActive -> 0f
        // COVER_ONLY has no below/side info band; other layouts share the media handoff.
        sourceLayout == VideoCardSourceLayout.COVER_ONLY -> 0f
        else -> resolveVideoCardSourceChromeReturnAlpha(depth)
    }
    return VideoCardSourceChromeVisualFrame(
        alpha = handoff,
        layoutScaleMultiplier = 1f,
        handoffProgress = handoff,
    )
}

/**
 * 详情侧返回时封面视觉主导权。
 *
 * morph 中途禁止在 LIVE ↔ RESIDENT 之间切换，否则会出现「先切封面再缩小」或黑闪。
 */
internal enum class VideoCardReturnCoverOwnership {
    /** ImmediatePlayback + 正文就绪：player 可见，cover alpha=0 */
    LIVE_SURFACE,

    /** Loading / CoverFirst / 无可靠 surface：常驻封面主导 */
    RESIDENT_COVER,

    /** 无 shell/shared 配对：不得假设 morph 存在 */
    FALLBACK_NO_SHARED,
}

/**
 * 列表源卡在返回落位期间的封面资源契约。
 * 保证：请求与缓存始终待命，且 URL 不在卸层瞬间重建；像素可见性另由 live handoff 决定。
 */
internal data class VideoCardReturnListCoverContract(
    val pinCoverSource: Boolean,
    val enableCoilCrossfade: Boolean,
)

/**
 * 一次返回会话的时间轴参数（与设置里的共享过渡时长对齐）。
 */
internal data class VideoCardReturnTimeline(
    val morphDurationMillis: Int,
    val settleBufferMillis: Long,
    val chromeRevealStart: Float,
    val sourceEnterFadeDelayRatio: Float,
) {
    val suppressionWindowMillis: Long
        get() = morphDurationMillis.coerceAtLeast(0).toLong() + settleBufferMillis
}

/**
 * 返回会话相位（逻辑相位，可与景深 [VideoCardTransitionBackgroundPhase] 对照）。
 */
internal enum class VideoCardReturnSessionPhase {
    Idle,
    Opening,
    Held,
    PredictiveSeek,
    ReturningMorph,
    CancelRestore,
}

internal fun resolveVideoCardReturnTimeline(
    morphDurationMillis: Int,
    isQuickReturn: Boolean = false,
): VideoCardReturnTimeline {
    return VideoCardReturnTimeline(
        morphDurationMillis = morphDurationMillis.coerceAtLeast(0),
        settleBufferMillis = resolveVideoCardReturnSpringSettleBufferMs(),
        chromeRevealStart = if (isQuickReturn) {
            0f
        } else {
            VIDEO_CARD_RETURN_CHROME_REVEAL_START
        },
        // 整壳 Enter 永不延后；快速/普通返回一致。
        sourceEnterFadeDelayRatio = VIDEO_CARD_RETURN_SOURCE_ENTER_FADE_DELAY_RATIO,
    )
}

/**
 * 源卡 shell sharedBounds 是否延后 Enter（整壳 fadeIn）。
 *
 * 一律 **false**：封面必须在列表位待命，卸层时零叠化；
 * 文字过渡只走 [resolveHomeCardChromeAlphaDuringShellReturnMorph] / chrome reveal。
 * [isQuickReturnFromDetail] 保留签名兼容。
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldDelaySourceCardEnterOnReturn(
    isQuickReturnFromDetail: Boolean,
): Boolean = false

/**
 * 预测返回 / 普通返回：实时画面 + 稳定封面 + 文字能否共存。
 *
 * **可以，且应始终共存**，分工如下：
 * - **LIVE_SURFACE**（详情壳 overlay）：一镜到底缩回，跟手/seek
 * - **来源封面**：飞行 media / resident cover 末段交接；列表封面 morph 中保持透明
 * - **标题/UP/统计**：由仍然存活的真实来源卡片随 sharedBounds 一起反向还原；
 *   列表真卡在卸层后露出（飞行层盖住列表，morph 中无法露出原位组件）
 *
 * 禁止：整壳 delayed Enter、中途 LIVE↔RESIDENT 切换、卸层瞬间改 Coil 请求。
 */
internal fun canCoexistLiveSurfaceStableCoverAndChromeOnReturn(): Boolean = true

/**
 * 单时钟 morph 深度 → settle。
 *
 * [morphDepthProgress] 与 [VideoCardTransitionClock.depthProgress] 同语义：
 * - 1 = 详情全屏
 * - 0 = 列表落位
 *
 * settle = 1 - depth：0 刚开始缩回，1 完全落位。
 * chrome / 详情正文 / 景深 **只读这一路**，禁止再 max(AVS, depth)。
 */
internal fun resolveVideoCardReturnSettleFromMorphDepth(morphDepthProgress: Float): Float {
    return resolveVisualProgress(morphDepthProgress, VideoCardTransitionBackgroundPhase.RETURNING,
        VideoSharedTransitionDirection.RETURN)
}

/**
 * 统一返回 settle 进度 0→1（刚开始缩回 → 完全落位）。
 *
 * 优先使用 [morphDepthProgress]（单时钟）。若未提供则回退旧双源 max 语义，
 * 仅供遗留调用；新接线应只传 morphDepth。
 */
internal fun resolveVideoCardReturnSettleProgress(
    transitionProgress: Float? = null,
    depthBlurProgress: Float? = null,
    morphDepthProgress: Float? = null,
): Float {
    if (morphDepthProgress != null) {
        return resolveVideoCardReturnSettleFromMorphDepth(morphDepthProgress)
    }
    var settle = 0f
    var hasSource = false
    if (transitionProgress != null) {
        settle = maxOf(settle, 1f - transitionProgress.coerceIn(0f, 1f))
        hasSource = true
    }
    if (depthBlurProgress != null) {
        settle = maxOf(settle, 1f - depthBlurProgress.coerceIn(0f, 1f))
        hasSource = true
    }
    return if (hasSource) settle.coerceIn(0f, 1f) else 0f
}

/**
 * live morph 详情次要内容 alpha：settle 过 [yieldStart] 后淡出，给源卡标题让位。
 *
 * 优先 [morphDepthProgress] 单时钟；与源卡 chrome 的 settle 同源。
 */
internal fun resolveVideoCardLiveMorphSecondaryContentAlpha(
    transitionProgress: Float = 1f,
    depthBlurProgress: Float? = null,
    yieldStart: Float = VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_START,
    yieldEnd: Float = VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_END,
    morphDepthProgress: Float? = null,
): Float {
    val settle = resolveVideoCardReturnSettleProgress(
        transitionProgress = if (morphDepthProgress == null) transitionProgress else null,
        depthBlurProgress = if (morphDepthProgress == null) depthBlurProgress else null,
        morphDepthProgress = morphDepthProgress,
    )
    return resolveVideoCardLiveMorphSecondaryContentAlphaFromSettle(
        settleProgress = settle,
        yieldStart = yieldStart,
        yieldEnd = yieldEnd,
    )
}

/**
 * 由 settle 直接算详情正文 alpha（可单测）。
 * settle≤yieldStart → 1；settle=1 → 0；中间线性让位。
 */
internal fun resolveVideoCardLiveMorphSecondaryContentAlphaFromSettle(
    settleProgress: Float,
    yieldStart: Float = VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_START,
    yieldEnd: Float = VIDEO_CARD_RETURN_LIVE_CONTENT_YIELD_END,
): Float {
    val settle = settleProgress.coerceIn(0f, 1f)
    val start = yieldStart.coerceIn(0f, 1f)
    val end = yieldEnd.coerceIn(start, 1f)
    if (settle <= start) return 1f
    if (end <= start) return 0f
    return (1f - (settle - start) / (end - start)).coerceIn(0f, 1f)
}

/**
 * 返回会话 ownership 稳定策略（保留实时画面优先）：
 *
 * - 会话外：不锁，跟 candidate
 * - 会话内首次：采样 candidate
 * - **允许 RESIDENT/FALLBACK → LIVE 升级**（首帧就绪后一镜到底跟壳缩，不能锁死封面）
 * - **禁止 LIVE → RESIDENT 降级**（中途 forceCover / 短暂无帧不得掐掉实时画面）
 * - 其它降级/同级：保持已锁值，避免 cover↔player 来回对切
 *
 * @return first = 写入 state 的 lock（非返回中为 null），second = 本帧生效 ownership
 */
internal fun resolveReturnSessionLockedCoverOwnership(
    lockedOwnership: VideoCardReturnCoverOwnership?,
    isReturnSessionActive: Boolean,
    candidateOwnership: VideoCardReturnCoverOwnership,
): Pair<VideoCardReturnCoverOwnership?, VideoCardReturnCoverOwnership> {
    if (!isReturnSessionActive) {
        return null to candidateOwnership
    }
    val locked = lockedOwnership
    if (locked == null) {
        return candidateOwnership to candidateOwnership
    }
    // 升级到 LIVE：实时 surface 可用时必须放开，否则进入/返回一镜到底变死封面。
    if (candidateOwnership == VideoCardReturnCoverOwnership.LIVE_SURFACE) {
        return VideoCardReturnCoverOwnership.LIVE_SURFACE to
            VideoCardReturnCoverOwnership.LIVE_SURFACE
    }
    // 已是 LIVE：禁止降级到封面路径。
    if (locked == VideoCardReturnCoverOwnership.LIVE_SURFACE) {
        return locked to locked
    }
    // RESIDENT/FALLBACK 之间保持首次采样，避免无意义抖动。
    return locked to locked
}

/**
 * 是否应对播放器强制封面-only（掐 live surface）。
 *
 * - live ownership 由上层 resident cover 做渐进交接，不折叠播放器
 * - resident ownership 在预测 seek 起点就切为 cover-only，确保 SurfaceView 不穿透封面层
 *
 * [isCommittedCardReturn] 默认跟 [useReturningVisualState] 兼容旧调用；
 * 详情接线应传入真正的提交信号（markReturning / isActuallyLeaving）。
 */
internal fun shouldForceCoverOnlyForReturnOwnership(
    ownership: VideoCardReturnCoverOwnership,
    useReturningVisualState: Boolean,
    forceCoverOnlyOnReturn: Boolean,
    isCommittedCardReturn: Boolean = useReturningVisualState,
): Boolean {
    if (isVideoCardLiveReturnMorphOwnership(ownership)) return false
    return forceCoverOnlyOnReturn || useReturningVisualState || isCommittedCardReturn
}

/**
 * 首帧是否已渲染，足以作为 live morph 的可绘帧。
 * 未出首帧时走 RESIDENT 封面接管，避免黑壳缩回。
 */
internal fun shouldTreatLiveSurfaceRenderableForReturnMorph(
    hasRenderedFirstFrame: Boolean,
    forceCoverUi: Boolean = false,
): Boolean {
    if (forceCoverUi) return false
    return hasRenderedFirstFrame
}

/**
 * Navigation3 [SeekableTransitionState] 预测返回完成后半段时长。
 *
 * 与 NavDisplay 内公式一致：
 * `remaining = ((1 - fraction) * totalDuration).toInt()`
 *
 * 因此返回 bounds **必须**是固定时长 tween（Linear），不能是 spring，
 * 否则 totalDuration 不可靠 → 松手一闪 / 无落位动画。
 *
 * @param seekFraction 当前 seek 进度，0=起点（详情全屏），1=已落位
 * @param fullDurationMs 与进场/返回 morph 主时长一致
 */
internal fun resolveVideoCardSharedMorphRemainingDurationMs(
    seekFraction: Float,
    fullDurationMs: Int,
): Int {
    val fraction = seekFraction.coerceIn(0f, 1f)
    val full = fullDurationMs.coerceAtLeast(0)
    return ((1f - fraction) * full).toInt().coerceAtLeast(0)
}

/**
 * 景深 blur 在提交返回时的剩余动画时长。
 * [blurProgressAtCommit] 为当前虚化强度（1=满糊，0=已清），按比例缩短，与 morph 同速感。
 */
internal fun resolveVideoCardReturnDepthBlurRemainingDurationMs(
    blurProgressAtCommit: Float,
    fullDurationMs: Int,
    minDurationMs: Int = VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS,
): Int {
    return resolveVideoCardTransitionBackgroundReturnDurationMs(
        startProgress = blurProgressAtCommit,
        fullDurationMs = fullDurationMs,
        minDurationMs = minDurationMs,
    )
}

/**
 * 是否允许 live morph（实时 surface 跟壳缩）。
 *
 * 预测返回始终预览实时画面；仅在以下条件不满足时回落封面：
 * - 详情正文未就绪时关闭，避免 Loading 骨架被缩进卡片位
 * - 无首帧 / 强制封面 UI 时关闭，避免黑壳缩回（回落 RESIDENT handoff）
 */
internal fun shouldUseVideoCardLiveReturnMorph(
    transitionEnabled: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent,
    detailContentReady: Boolean,
    hasRenderableLiveFrame: Boolean = true,
    /**
     * 「实时画面转场」用户开关：关则强制封面 morph；开才允许播放器视频帧跟壳缩回。
     * 默认 true 与设置项默认一致。
     */
    liveSurfaceCardTransitionEnabled: Boolean = false,
    sourceLayout: VideoCardSourceLayout? = null,
): Boolean {
    // 纯封面卡（如首页横幅 Banner、音频横条等）在源列表无视频图层，返回时必须使用静止封面接管
    if (sourceLayout == VideoCardSourceLayout.COVER_ONLY) {
        return false
    }
    return transitionEnabled &&
        liveSurfaceCardTransitionEnabled &&
        sharedBoundsActive &&
        !keepLoadedContentForBackPreview &&
        playbackIntent == VideoSharedTransitionPlaybackIntent.ImmediatePlayback &&
        detailContentReady &&
        hasRenderableLiveFrame
}

/**
 * 解析详情返回时的封面路径类型（与「当前是否正在离开」无关）。
 *
 * - LIVE_SURFACE：满足 live morph 门闩（含可绘帧）→ 离开时 player 主导
 * - RESIDENT_COVER：有 shared，但不走 live（Loading/CoverFirst/无首帧/COVER_ONLY源卡 等）→ 离开时封面主导
 * - FALLBACK_NO_SHARED：无配对 → 不得假设 shell morph
 *
 * 「现在是否把视觉交给封面」还要乘 [useReturningVisualState]，见
 * [shouldHandVisualOwnershipToResidentCoverForOwnership]。
 */
@Suppress("UNUSED_PARAMETER") // hasResidentCover：handoff 门闩在 shouldHand*，路径类型不依赖是否已有 URL
internal fun resolveVideoCardReturnCoverOwnership(
    transitionEnabled: Boolean,
    sharedBoundsActive: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent,
    detailContentReady: Boolean,
    hasResidentCover: Boolean,
    hasRenderableLiveFrame: Boolean = true,
    liveSurfaceCardTransitionEnabled: Boolean = false,
    sourceLayout: VideoCardSourceLayout? = null,
): VideoCardReturnCoverOwnership {
    if (!transitionEnabled || !sharedBoundsActive) {
        return VideoCardReturnCoverOwnership.FALLBACK_NO_SHARED
    }
    val live = shouldUseVideoCardLiveReturnMorph(
        transitionEnabled = transitionEnabled,
        sharedBoundsActive = sharedBoundsActive,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
        playbackIntent = playbackIntent,
        detailContentReady = detailContentReady,
        hasRenderableLiveFrame = hasRenderableLiveFrame,
        liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
        sourceLayout = sourceLayout,
    )
    if (live) {
        return VideoCardReturnCoverOwnership.LIVE_SURFACE
    }
    // 非 live：路径 B。无封面时 hand 仍为 false（hasResidentCover 门闩），player 保持可见防黑底。
    return VideoCardReturnCoverOwnership.RESIDENT_COVER
}

/**
 * 离开态是否把视觉主导权交给常驻封面。
 * live 路径永远 false；其余路径在 [useReturningVisualState] 且有封面时 true。
 */
internal fun shouldHandVisualOwnershipToResidentCoverForOwnership(
    ownership: VideoCardReturnCoverOwnership,
    useReturningVisualState: Boolean,
    hasResidentCover: Boolean,
): Boolean {
    if (!useReturningVisualState || !hasResidentCover) return false
    return when (ownership) {
        VideoCardReturnCoverOwnership.LIVE_SURFACE -> false
        VideoCardReturnCoverOwnership.RESIDENT_COVER,
        VideoCardReturnCoverOwnership.FALLBACK_NO_SHARED -> true
    }
}

internal fun isVideoCardLiveReturnMorphOwnership(
    ownership: VideoCardReturnCoverOwnership,
): Boolean = ownership == VideoCardReturnCoverOwnership.LIVE_SURFACE

/**
 * 列表源卡封面资源契约：pin 源、关 crossfade；可见 alpha 由 live handoff 统一裁决。
 */
internal fun resolveVideoCardReturnListCoverContract(
    isSharedReturnTarget: Boolean,
    isScrollInProgress: Boolean,
    isReturningFromDetail: Boolean,
    useCoverSharedBounds: Boolean,
): VideoCardReturnListCoverContract {
    val pin = isSharedReturnTarget
    val crossfade = when {
        isScrollInProgress -> false
        useCoverSharedBounds && isSharedReturnTarget -> false
        isReturningFromDetail && isSharedReturnTarget -> false
        else -> true
    }
    return VideoCardReturnListCoverContract(
        pinCoverSource = pin,
        enableCoilCrossfade = crossfade,
    )
}

/**
 * 把景深 phase + 手势标志映射为逻辑返回相位（便于单测与日志）。
 */
internal fun resolveVideoCardReturnSessionPhase(
    backgroundPhase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    isGestureRestoreInProgress: Boolean,
): VideoCardReturnSessionPhase {
    if (isGestureRestoreInProgress) {
        return VideoCardReturnSessionPhase.CancelRestore
    }
    if (isReturnGestureInProgress) {
        return VideoCardReturnSessionPhase.PredictiveSeek
    }
    return when (backgroundPhase) {
        VideoCardTransitionBackgroundPhase.IDLE -> VideoCardReturnSessionPhase.Idle
        VideoCardTransitionBackgroundPhase.OPENING -> VideoCardReturnSessionPhase.Opening
        VideoCardTransitionBackgroundPhase.HELD -> VideoCardReturnSessionPhase.Held
        VideoCardTransitionBackgroundPhase.RETURNING -> VideoCardReturnSessionPhase.ReturningMorph
    }
}

/**
 * 主路径返回 bounds 必须可 seek：固定时长 Linear tween。
 * 结构/策略测试用此开关锁定契约，防止再滑回 spring。
 */
internal fun shouldUseSeekableLinearReturnBoundsTransform(): Boolean = true
