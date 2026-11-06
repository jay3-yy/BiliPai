package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * 卡片 ↔ 详情 **唯一时间轴契约**。
 *
 * ## 语义（morphFraction）
 * - **0** = 落在列表源卡（景深清晰）
 * - **1** = 落在详情全屏（景深满糊）
 *
 * 背景 blur / scrim 和壁纸 depth 读取 [depthProgress]。卡片位置、尺寸与圆角
 * 由同一导航 settle 时间采样各属性弹簧，不注册独立帧循环。
 *
 * Miuix 主链路：只跟随 NavDisplay，包含预测 seek/commit/cancel 和真实结束帧。
 * ## 无 Nav 主驱动的兼容路径优先级（高 → 低）
 * 1. **预测手势** [gestureBackProgress]
 * 2. **Shared morph**（详情 AVS 与 sharedBounds 同一 Transition 的 progress）
 * 3. **Host fallback Animatable**（无 shared / 首帧详情未挂上时）
 *
 * Shared bounds 仍由 Compose SharedTransition 播 bounds（无法外注 progress），
 * 但 AVS `animateFloat` 与 boundsTransform **强制同一 duration + easing**，
 * 再把 AVS progress 回灌本时钟 → 墙钟与曲线同源。
 */
@Stable
internal class VideoCardTransitionClock {
    val landingFeedback = VideoCardLandingFeedbackState()

    private var navigationDepthProvider: (() -> Float?)? = null
    private var navigationMaskDepthProvider: (() -> Float?)? = null
    private var navigationSettleState: VideoCardTransitionSettleState? by mutableStateOf(null)
    var initialVelocity: Float = 0f
        private set

    val settleState: VideoCardTransitionSettleState
        get() = navigationSettleState ?: when {
            gestureRestoreInProgress -> VideoCardTransitionSettleState.CancelRestore
            gestureBackProgress != null -> VideoCardTransitionSettleState.InteractiveSeek
            phase == VideoCardTransitionBackgroundPhase.OPENING -> VideoCardTransitionSettleState.AutoEnter
            phase == VideoCardTransitionBackgroundPhase.RETURNING -> VideoCardTransitionSettleState.AutoReturn
            phase == VideoCardTransitionBackgroundPhase.HELD -> VideoCardTransitionSettleState.Held
            else -> VideoCardTransitionSettleState.Idle
        }

    /** Miuix owns position AND lifetime. Do not start a hidden fallback alongside it. */
    fun bindNavigationDriver(
        provider: (() -> Float?)?,
        maskProvider: (() -> Float?)? = null,
    ) {
        navigationDepthProvider = provider
        navigationMaskDepthProvider = maskProvider
        if (provider == null) navigationSettleState = null
    }

    fun followNavigationDriver(state: VideoCardTransitionSettleState, velocity: Float = 0f) {
        navigationSettleState = state
        initialVelocity = velocity.takeIf { it.isFinite() } ?: 0f
        gestureRestoreInProgress = state == VideoCardTransitionSettleState.CancelRestore
        phase = when (state) {
            VideoCardTransitionSettleState.AutoEnter -> VideoCardTransitionBackgroundPhase.OPENING
            VideoCardTransitionSettleState.AutoReturn -> VideoCardTransitionBackgroundPhase.RETURNING
            VideoCardTransitionSettleState.Idle -> VideoCardTransitionBackgroundPhase.IDLE
            else -> VideoCardTransitionBackgroundPhase.HELD
        }
        if (state == VideoCardTransitionSettleState.Idle) {
            sourceRoute = null
            returnDepthFloor = null
        }
    }
    var phase: VideoCardTransitionBackgroundPhase by mutableStateOf(
        VideoCardTransitionBackgroundPhase.IDLE,
    )
        private set

    var sourceRoute: String? by mutableStateOf(null)

    /** 预测返回：系统 back progress 0→1；null = 无手势。 */
    var gestureBackProgress: Float? by mutableStateOf(null)

    /** 手势开始时的 depth（通常 1，OPENING 中途可能 <1）。 */
    var gestureStartDepth: Float by mutableFloatStateOf(1f)

    var gestureRestoreInProgress: Boolean by mutableStateOf(false)
        private set

    /**
     * 返回消糊起点（同步写入）。shared-only 进场后 fallback Animatable 常仍为 0，
     * beginReturning 若只改 phase，在 suspend snapFallback 跑到之前 depth 会读成 0 → 无糊。
     * 此 floor 在 snapFallback 前顶住满糊（或手势提交时的 depth），snap 后清空。
     */
    var returnDepthFloor: Float? by mutableStateOf(null)
        private set

    private val fallback = Animatable(0f)

    /**
     * Shared morph 回灌：与详情 shell 同一 Transition 的 fraction（0 卡 / 1 详情）。
     * null = 本帧无有效回灌。
     */
    // 共享 morph 的进度由详情 AVS 每帧回灌。它必须是 Snapshot state，才能让背景
    // drawWithContent 在同一帧失效重绘；普通字段会让背景只在 fallback 状态变化时
    // 才刷新，视觉上就像先顿住、再追上卡片本体。
    private var sharedMorphFraction: Float? by mutableStateOf(null)
    private var sharedMorphActive: Boolean by mutableStateOf(false)

    val fallbackValue: Float
        get() = fallback.value

    /**
     * 当前景深 / morph 进度（只读入口）。
     * 所有视觉层必须走这里，禁止再读独立 Animatable。
     */
    fun depthProgress(): Float {
        navigationDepthProvider?.invoke()?.let { return it.coerceIn(0f, 1f) }
        return resolveVideoCardClockDepthProgress(
            gestureBackProgress = gestureBackProgress,
            gestureStartDepth = gestureStartDepth,
            phase = phase,
            sharedMorphActive = sharedMorphActive,
            sharedMorphFraction = sharedMorphFraction,
            fallbackProgress = fallback.value,
            gestureRestoreInProgress = gestureRestoreInProgress,
            returnDepthFloor = returnDepthFloor,
        )
    }

    /**
     * 背景遮罩深度（独立弹簧通道，压暗先于模糊到位）。
     * 无导航驱动或回灌路径时退化为 [depthProgress]，与旧单通道行为一致。
     */
    fun maskProgress(): Float {
        navigationMaskDepthProvider?.invoke()?.let { return it.coerceIn(0f, 1f) }
        return depthProgress()
    }

    /**
     * 详情侧每帧回灌 shared morph progress。
     * [morphFraction]：Visible=1、PostExit/PreEnter=0。
     * [active]：shared 或 enter/exit transition 进行中。
     * 相位仍由 Host 的 beginOpening/beginReturning/mark* 拥有；此处只灌 fraction。
     */
    fun reportSharedMorphProgress(morphFraction: Float, active: Boolean) {
        if (navigationDepthProvider != null) return
        sharedMorphActive = active
        sharedMorphFraction = if (active) {
            morphFraction.coerceIn(0f, 1f)
        } else {
            null
        }
        // 进场 shared 跑完：升到 HELD（Host fallback 可能已先 markHeld，幂等）
        if (!active &&
            phase == VideoCardTransitionBackgroundPhase.OPENING &&
            morphFraction >= 0.999f
        ) {
            phase = VideoCardTransitionBackgroundPhase.HELD
        }
        // 返回：shared 结束（含详情 dispose / Exit.None 瞬间 PostExit）**不得**立刻 IDLE。
        // Nav3 NO_OP_SHARED_ELEMENT 常让 shared 先于景深消糊结束；若此处 IDLE，
        // 源页 effect 与 sourceRoute 被摘掉，背景看不到模糊→清晰。由 Host fallback
        // animateFallbackTo(0) 结束后 markIdle。
    }

    fun clearSharedMorphProgress() {
        sharedMorphActive = false
        sharedMorphFraction = null
    }

    /** sharedBounds 已开始回灌时，背景应直接读取它，而非启动第二条 fallback 补间。 */
    fun hasActiveSharedMorphProgress(): Boolean =
        sharedMorphActive && sharedMorphFraction != null

    fun beginOpening(sourceRoute: String?) {
        landingFeedback.clear()
        this.sourceRoute = sourceRoute
        phase = VideoCardTransitionBackgroundPhase.OPENING
        gestureBackProgress = null
        gestureRestoreInProgress = false
        returnDepthFloor = null
        clearSharedMorphProgress()
    }

    /**
     * Card clicks pre-arm OPENING before the destination is added to the back stack so the
     * destination's very first frame can consume the frozen source cover. The Nav host observes
     * that same stack change later; do not restart the clock there or clear progress already
     * reported by the newly mounted entry.
     */
    fun beginOpeningIfNeeded(sourceRoute: String?) {
        if (
            phase == VideoCardTransitionBackgroundPhase.OPENING &&
            this.sourceRoute == sourceRoute
        ) {
            return
        }
        beginOpening(sourceRoute)
    }

    /**
     * @param startDepth 消糊起点。HELD 稳态后必须为 1（满糊），否则 shared-only 进场
     * 留下的 fallback=0 会让返回首帧立刻清晰、看不到模糊过程。
     */
    fun beginReturning(sourceRoute: String?, startDepth: Float = 1f) {
        this.sourceRoute = sourceRoute
        // 同步钉死起点，再改 phase，保证本帧 depth 已是满糊。
        returnDepthFloor = startDepth.coerceIn(0f, 1f)
        phase = VideoCardTransitionBackgroundPhase.RETURNING
        gestureBackProgress = null
        gestureRestoreInProgress = false
        // 保留 shared 回灌通道；fallback 由 Host 再 snap 后 animate
    }

    fun markHeld() {
        if (navigationDepthProvider != null) return
        if (phase == VideoCardTransitionBackgroundPhase.OPENING ||
            phase == VideoCardTransitionBackgroundPhase.RETURNING
        ) {
            phase = VideoCardTransitionBackgroundPhase.HELD
            returnDepthFloor = null
        }
    }

    fun markIdle() {
        if (navigationDepthProvider != null) return
        phase = VideoCardTransitionBackgroundPhase.IDLE
        sourceRoute = null
        gestureBackProgress = null
        gestureRestoreInProgress = false
        returnDepthFloor = null
        clearSharedMorphProgress()
    }

    fun beginGesture(backProgress: Float) {
        if (gestureBackProgress == null) {
            // HELD 合同为满糊：不得采到 0（fallback 稳态常为 0），否则整段手势无糊。
            gestureStartDepth = resolveVideoCardGestureStartDepth(
                phase = phase,
                currentDepth = depthProgress(),
            )
        }
        gestureBackProgress = backProgress.coerceIn(0f, 1f)
    }

    fun endGesture() {
        gestureBackProgress = null
    }

    fun beginGestureRestore() {
        gestureRestoreInProgress = true
    }

    fun endGestureRestore() {
        gestureRestoreInProgress = false
    }

    suspend fun snapFallback(value: Float) {
        if (navigationDepthProvider != null) return
        fallback.snapTo(value.coerceIn(0f, 1f))
        // fallback 已接管；清 floor 才能继续 animate 到 0。
        returnDepthFloor = null
    }

    /**
     * 仅当 shared 未接管时跑 fallback。
     * shared 一旦 active，读路径走 shared，fallback 在后台跟到终点以免切换跳变。
     */
    suspend fun animateFallbackTo(
        target: Float,
        durationMillis: Int,
        easing: Easing,
    ) {
        if (navigationDepthProvider != null) return
        val safeDuration = durationMillis.coerceAtLeast(0)
        if (safeDuration <= 0) {
            fallback.snapTo(target.coerceIn(0f, 1f))
            returnDepthFloor = null
            return
        }
        fallback.animateTo(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = safeDuration, easing = easing),
        )
    }

    /** Legacy/non-Nav driver: start at the visible position and keep its release velocity. */
    suspend fun settleFromCurrent(
        target: Float,
        motion: VideoHeroMotionSpec,
        releaseVelocity: Float = fallback.velocity,
    ) {
        if (navigationDepthProvider != null) return
        val current = depthProgress()
        initialVelocity = releaseVelocity.takeIf { it.isFinite() } ?: 0f
        val cancel = target > current
        if (cancel) beginGestureRestore() else beginReturning(sourceRoute, current)
        gestureBackProgress = null
        clearSharedMorphProgress()
        snapFallback(current)
        if (motion.returnDurationMillis == 0 || motion.reducedMotion) {
            animateFallbackTo(target, motion.remainingDuration(current, target, cancel), motion.effectsEasing)
        } else {
            fallback.animateTo(
                targetValue = target.coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = VideoHeroMotionTokens.SPRING_DAMPING,
                    stiffness = if (cancel) motion.cancelStiffness else motion.commitStiffness,
                    visibilityThreshold = 0.0025f,
                ),
                initialVelocity = initialVelocity,
            )
        }
        if (cancel) {
            endGestureRestore()
            markHeld()
        } else {
            markIdle()
        }
    }

    suspend fun snapClearAndIdle() {
        clearSharedMorphProgress()
        gestureBackProgress = null
        gestureRestoreInProgress = false
        returnDepthFloor = null
        fallback.snapTo(0f)
        phase = VideoCardTransitionBackgroundPhase.IDLE
    }
}

internal enum class VideoCardTransitionSettleState {
    Idle, InteractiveSeek, AutoEnter, Held, AutoReturn, CancelRestore,
}

/**
 * 详情 → 时钟的回灌口（CompositionLocal）。
 */
internal fun interface VideoCardMorphProgressReporter {
    fun report(morphFraction: Float, active: Boolean)
}

internal val LocalVideoCardMorphProgressReporter =
    compositionLocalOf<VideoCardMorphProgressReporter?> { null }

internal val LocalVideoCardTransitionClock =
    compositionLocalOf<VideoCardTransitionClock?> { null }

/**
 * 开合 **唯一** duration/easing 表：boundsTransform、AVS progress、fallback 必须同源。
 */
internal data class VideoCardTransitionTimelineSpec(
    val durationMillis: Int,
    val enterEasing: Easing,
    val returnEasing: Easing,
) {
    companion object {
        fun fromDurationMillis(durationMillis: Int): VideoCardTransitionTimelineSpec {
            val hero = resolveVideoHeroMotionSpec(durationMillis)
            return VideoCardTransitionTimelineSpec(
                durationMillis = hero.enterDurationMillis,
                enterEasing = hero.enterSpatialSpec,
                returnEasing = hero.predictiveSeekSpec,
            )
        }
    }
}

internal fun resolveVideoCardTimelineSpec(durationMillis: Int): VideoCardTransitionTimelineSpec =
    VideoCardTransitionTimelineSpec.fromDurationMillis(durationMillis)

/**
 * 是否应采用 shared morph 回灌作为 depth 主源。
 */
internal fun shouldPreferSharedMorphProgress(
    sharedMorphActive: Boolean,
    hasSharedFraction: Boolean,
    gestureActive: Boolean,
): Boolean {
    if (gestureActive) return false
    return sharedMorphActive && hasSharedFraction
}

/**
 * 解析最终 depth（纯函数，供测试）。
 *
 * [VideoCardTransitionBackgroundPhase.HELD] 合同为满糊（1）：shared-only 进场结束后
 * fallback 可能仍停在 0，若直接读 fallback 会在返回首帧瞬间变清晰。
 *
 * 预测手势取消回弹（[gestureRestoreInProgress]）时 HELD 必须读 fallback，
 * 才能播清晰→满糊；否则恒 1 会看起来「取消也没有模糊过程」。
 *
 * [VideoCardTransitionBackgroundPhase.RETURNING]：shared / fallback / [returnDepthFloor]
 * 取较大值。HELD 稳态后 fallback 常为 0；floor 在 snapFallback 前顶住满糊，避免返回
 * 首帧 depth=0 导致「完全进详情后再返回完全没有模糊」。
 */

/**
 * 预测手势起点 depth。HELD 强制 1（满糊合同）；其余用当前 depth（OPENING 可能未满）。
 */
internal fun resolveVideoCardGestureStartDepth(
    phase: VideoCardTransitionBackgroundPhase,
    currentDepth: Float,
): Float {
    return when (phase) {
        VideoCardTransitionBackgroundPhase.HELD -> 1f
        VideoCardTransitionBackgroundPhase.OPENING -> currentDepth.coerceIn(0f, 1f)
        VideoCardTransitionBackgroundPhase.RETURNING,
        VideoCardTransitionBackgroundPhase.IDLE -> currentDepth.coerceIn(0f, 1f)
    }
}

internal fun resolveVideoCardClockDepthProgress(
    gestureBackProgress: Float?,
    gestureStartDepth: Float,
    phase: VideoCardTransitionBackgroundPhase,
    sharedMorphActive: Boolean,
    sharedMorphFraction: Float?,
    fallbackProgress: Float,
    gestureRestoreInProgress: Boolean = false,
    returnDepthFloor: Float? = null,
): Float {
    if (gestureBackProgress != null) {
        return resolveVideoCardTransitionBackgroundGestureBlurProgress(
            phase = if (phase == VideoCardTransitionBackgroundPhase.OPENING) {
                VideoCardTransitionBackgroundPhase.OPENING
            } else {
                VideoCardTransitionBackgroundPhase.HELD
            },
            currentBlurProgress = gestureStartDepth,
            backProgress = gestureBackProgress,
        )
    }
    val fallback = resolveReturningDepthWithFloor(
        phase = phase,
        fallbackProgress = fallbackProgress,
        returnDepthFloor = returnDepthFloor,
    )
    if (shouldPreferSharedMorphProgress(
            sharedMorphActive = sharedMorphActive,
            hasSharedFraction = sharedMorphFraction != null,
            gestureActive = false,
        )
    ) {
        val shared = sharedMorphFraction!!.coerceIn(0f, 1f)
        if (phase == VideoCardTransitionBackgroundPhase.RETURNING) {
            return maxOf(shared, fallback)
        }
        return shared
    }
    if (phase == VideoCardTransitionBackgroundPhase.HELD) {
        if (gestureRestoreInProgress) return fallbackProgress.coerceIn(0f, 1f)
        return 1f
    }
    return fallback
}

/**
 * RETURNING 时用 [returnDepthFloor] 顶住 snap 前的 0 fallback，保证消糊从满糊起。
 */
internal fun resolveReturningDepthWithFloor(
    phase: VideoCardTransitionBackgroundPhase,
    fallbackProgress: Float,
    returnDepthFloor: Float?,
): Float {
    val fallback = fallbackProgress.coerceIn(0f, 1f)
    if (phase != VideoCardTransitionBackgroundPhase.RETURNING) return fallback
    val floor = returnDepthFloor?.coerceIn(0f, 1f) ?: return fallback
    return maxOf(fallback, floor)
}

/**
 * 预测手势进行中：用系统 back progress 直接映射景深（不依赖 SideEffect 写入时钟的时序）。
 * 手势起点满糊(1)→拖到底清晰(0)；取消回弹走 Host fallback。
 */
internal fun resolveVideoCardPredictiveGestureDepthProgress(
    phase: VideoCardTransitionBackgroundPhase,
    backProgress: Float,
    gestureStartDepth: Float,
): Float {
    return resolveVideoCardTransitionBackgroundGestureBlurProgress(
        phase = phase,
        currentBlurProgress = gestureStartDepth,
        backProgress = backProgress,
    )
}

/**
 * 返回消糊动画起点。
 *
 * 已有真实进度（手势 / shared / fallback）时原样采用；若 HELD/RETURNING 却读到 ~0，
 * 视为 shared-only 进场未写入 fallback，强制从满糊起，保证模糊→清晰连续曲线。
 */
internal fun resolveVideoCardReturnClearStartDepth(
    phase: VideoCardTransitionBackgroundPhase,
    currentDepth: Float,
): Float {
    val clamped = currentDepth.coerceIn(0f, 1f)
    if (clamped > 0.001f) return clamped
    return when (phase) {
        VideoCardTransitionBackgroundPhase.HELD,
        VideoCardTransitionBackgroundPhase.RETURNING,
        -> 1f
        else -> clamped
    }
}

/**
 * morphFraction(0 卡→1 详情) 与 settle(0 刚缩→1 落位) 转换。
 */
internal fun morphFractionToReturnSettle(morphFraction: Float): Float =
    (1f - morphFraction.coerceIn(0f, 1f)).coerceIn(0f, 1f)

internal fun resolveMorphAlignedFallbackDurationMs(
    timelineDurationMs: Int,
    startDepth: Float,
    targetDepth: Float,
): Int {
    val span = kotlin.math.abs(startDepth.coerceIn(0f, 1f) - targetDepth.coerceIn(0f, 1f))
    return (timelineDurationMs.coerceAtLeast(0) * span).roundToInt().coerceAtLeast(0)
}

@Composable
internal fun rememberVideoCardTransitionClock(): VideoCardTransitionClock {
    return androidx.compose.runtime.remember { VideoCardTransitionClock() }
}
