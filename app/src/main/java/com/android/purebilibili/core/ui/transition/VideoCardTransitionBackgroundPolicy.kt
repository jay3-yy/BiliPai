package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation.isVideoCardReturnTargetRoute
import kotlin.math.roundToInt

// 景深层（与 Hero 卡片放大配合，progress 0→1 同源）：
// 1) 页面绕被点击卡片中心轻微后退（无 bounds 时退回屏幕中心），系统状态栏保持原位
// 2) 飞卡由 overlay 独自承担放大；背景缩放只做远平面，避免双重缩放
// 3) blur：空间纵深（冻结层 + BlurEffect）。半径按 **dp** 定义、按密度换算
// 4) scrim 压暗：聚焦/可读
// - 冻结层：首帧 record 一次后只改 BlurEffect，禁止 live 重录
// - 压暗全程保留（含 HELD），避免打开完成后景深断裂
// - 返回：景深读取主驱动的已缓动进度，空间 landing pulse 不进入 effects
private const val VIDEO_CARD_TRANSITION_MAX_BLUR_RADIUS_DP = 12f
private const val VIDEO_CARD_TRANSITION_BLUR_QUANTUM_PX = VideoHeroMotionTokens.OPEN_BLUR_QUANTUM_PX
/** 返回与进场共用步长，中途反向不切换半径网格。 */
internal const val VIDEO_CARD_TRANSITION_RETURN_BLUR_QUANTUM_PX = VideoHeroMotionTokens.RETURN_BLUR_QUANTUM_PX
// 页面内部只负责模糊与暗化。整个来源页的景深缩放由 Miuix 导航 entry 统一承担，
// 独立首页 chrome、壁纸与冻结层不得再次缩放；脱离 entry 的快照由 Host 同几何兜底。
internal const val VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION = 0f
private const val VIDEO_CARD_TRANSITION_RELATED_SCALE_REDUCTION =
    VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION
private const val VIDEO_CARD_TRANSITION_PARTITION_SCALE_REDUCTION =
    VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION
// 保持遮罩克制，让页面后退与 shared 卡片放大承担主要层级对比。
private const val VIDEO_CARD_TRANSITION_MAX_SCRIM_ALPHA_DARK = 0.14f
private const val VIDEO_CARD_TRANSITION_MAX_SCRIM_ALPHA_LIGHT = 0.08f
private const val VIDEO_CARD_TRANSITION_REDUCED_SCRIM_ALPHA = 0.08f
/** 景深缩放露出的边缘：至少压到这个 tint 强度，避免浅色主题读成「白条」。 */
private const val VIDEO_CARD_TRANSITION_SCALE_GAP_MIN_TINT_LIGHT = 0.36f
private const val VIDEO_CARD_TRANSITION_SCALE_GAP_MIN_TINT_DARK = 0.44f
private val VIDEO_CARD_TRANSITION_DARK_GAP_BASE = Color(0xFF121212)

/**
 * 退后页满深度圆角兜底（dp）。
 * 优先用设备物理圆角（API 31+ WindowInsets）；未上报 / OEM 返回 0 时用此值。
 * 取 max(设备, 兜底)：缩到 90% 后过小的物理圆角读成尖角卡片。
 */
internal const val VIDEO_CARD_TRANSITION_BACKGROUND_CORNER_FALLBACK_DP = 24f

// 开场与返回时长由共享元素速度设置提供；取消仍固定为短恢复动画。
// 与共享元素标准时长对齐，避免景深先清完、封面还在赶路。
internal const val VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS = 420
internal const val VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS = 160

internal enum class VideoCardTransitionBackgroundPhase {
    IDLE,
    OPENING,
    HELD,
    RETURNING
}

/** 来源页类型：详情相关推荐复用首页景深，内嵌分区保留独立预算。 */
internal enum class VideoCardTransitionBackgroundSource {
    Home,
    RelatedVideo,
    Partition,
}

internal fun resolveVideoCardTransitionBackgroundSource(
    sourceRoute: String?,
): VideoCardTransitionBackgroundSource {
    return when (normalizeVideoCardTransitionRoute(sourceRoute)) {
        "partition" -> VideoCardTransitionBackgroundSource.Partition
        else -> if (sourceRoute?.substringBefore("?")?.startsWith("video/") == true) {
            VideoCardTransitionBackgroundSource.RelatedVideo
        } else {
            VideoCardTransitionBackgroundSource.Home
        }
    }
}

internal fun resolveVideoCardTransitionBackgroundScaleReduction(
    source: VideoCardTransitionBackgroundSource,
): Float = when (source) {
    VideoCardTransitionBackgroundSource.Home ->
        VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION
    VideoCardTransitionBackgroundSource.RelatedVideo ->
        VIDEO_CARD_TRANSITION_RELATED_SCALE_REDUCTION
    VideoCardTransitionBackgroundSource.Partition ->
        VIDEO_CARD_TRANSITION_PARTITION_SCALE_REDUCTION
}

/** Whether the retained source snapshot should receive the progress-driven blur effect. */
internal fun shouldUseRealtimeVideoCardTransitionBackgroundBlur(
    realtimeBlurEnabled: Boolean,
): Boolean = realtimeBlurEnabled

/**
 * 详情压详情时保留真实父 entry，不录制整页 Host 快照。
 * Android player Surface 不属于 Compose GraphicsLayer，录制结果会把播放器区域变黑；
 * 返回相关推荐时直接让 NavDisplay 的父详情参与预览，才能保留真实组件和画面。
 */
internal fun shouldUseHostOwnedVideoCardTransitionSnapshot(
    sourceRoute: String?,
): Boolean = sourceRoute
    ?.substringBefore('?')
    ?.startsWith("video/") != true

/**
 * 首页 feed 自己录冻结层，顶栏 overlay 才能留在快照之上做独立滑入/滑出。
 */
internal fun shouldHomeFeedOwnVideoCardTransitionSnapshot(
    sourceRoute: String?,
    hasSnapshotHandle: Boolean,
): Boolean {
    if (!hasSnapshotHandle) return false
    if (!shouldUseHostOwnedVideoCardTransitionSnapshot(sourceRoute)) return false
    return sourceRoute?.substringBefore("?") == "home"
}

/** 首页 feed 已接管快照时，路由壳只钉页、不再整页录顶栏。 */
internal fun shouldApplyVideoCardTransitionSnapshotOnRouteShell(
    entryRoute: String?,
    sourceRoute: String?,
    activeMainHostRoute: String?,
): Boolean {
    if (!shouldUseHostOwnedVideoCardTransitionSnapshot(sourceRoute)) return false
    if (
        !shouldApplyVideoCardTransitionBackgroundToRoute(
            entryRoute = entryRoute,
            sourceRoute = sourceRoute,
            activeMainHostRoute = activeMainHostRoute,
        )
    ) {
        return false
    }
    return !shouldHomeFeedOwnVideoCardTransitionSnapshot(
        sourceRoute = sourceRoute,
        hasSnapshotHandle = true,
    )
}

internal data class VideoCardTransitionBackgroundFrame(
    val blurRadiusPx: Float,
    /** 模糊混合强度（引擎 BLUR_ALPHA 通道语义）：恒定半径下经 layerAlpha 合成渐进模糊。 */
    val blurAlpha: Float = 1f,
    val scrimAlpha: Float,
    val contentScale: Float,
    val useLightScrimTint: Boolean = false,
    /** 退后页面（冻结层）的圆角半径，随景深线性建立。 */
    val cornerRadiusPx: Float = 0f,
    /** 随景深建立的外围漫反射阴影高度（px），使缩放空隙呈现下沉实体落阴。 */
    val shadowElevationPx: Float = 0f,
)

internal data class VideoCardTransitionBackgroundState(
    val progressProvider: () -> Float = { 0f },
    /** 背景遮罩独立深度（引擎 maskAlpha 通道）；null 时跟随 [progressProvider]。 */
    val maskProgressProvider: (() -> Float)? = null,
    val sourceRouteProvider: () -> String? = { null },
    val sourceKeyProvider: () -> String? = { null },
    val phaseProvider: () -> VideoCardTransitionBackgroundPhase = {
        VideoCardTransitionBackgroundPhase.IDLE
    },
    val exposureProvider: () -> VideoCardTransitionExposure = {
        VideoCardTransitionExposure.Idle
    },
    val sourceCornerDpProvider: () -> Int? = { null },
    /** 被点击卡片的宿主坐标，供景深缩放绕卡片中心后退。 */
    val sourceBoundsProvider: () -> Rect? = { null },
    val snapshotHandle: VideoCardTransitionSnapshotHandle? = null,
    val isReturnGestureInProgressProvider: () -> Boolean = { false },
    val isGestureRestoreInProgressProvider: () -> Boolean = { false },
    val isQuickReturnFromDetailProvider: () -> Boolean = { false },
    /**
     * 为 true 时完整源卡常驻下层，由上层 live 画面在落点窗口内淡出交接。
     * 默认值仅供未接入导航宿主的调用方兜底；导航宿主应显式选择返回策略。
     */
    val preferWholeCardReturnProvider: () -> Boolean = { true },
    val motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    val isLightBackgroundProvider: () -> Boolean = { false },
    val realtimeBlurEnabledProvider: () -> Boolean = { false },
)

internal val LocalVideoCardTransitionBackgroundState = compositionLocalOf {
    VideoCardTransitionBackgroundState()
}

/**
 * 整卡过渡的景深档位只服从系统“减少动态效果”。
 *
 * 用户显式开启实时模糊后，运行时性能守卫不得把一次掉帧记忆成 scrim-only；否则后续
 * OPENING / RETURNING 都会失去动态模糊。来源页仍只冻结录制一次，性能由该策略控制。
 */
internal fun resolveVideoCardTransitionMotionTier(
    reduceMotion: Boolean,
): MotionTier = if (reduceMotion) MotionTier.Reduced else MotionTier.Normal

internal fun resolveVideoCardTransitionScrimAlpha(
    progress: Float,
    isLightBackground: Boolean,
    motionTier: MotionTier,
): Float {
    val clamped = progress.coerceIn(0f, 1f)
    val maxAlpha = when {
        motionTier == MotionTier.Reduced ->
            VIDEO_CARD_TRANSITION_REDUCED_SCRIM_ALPHA
        isLightBackground ->
            VIDEO_CARD_TRANSITION_MAX_SCRIM_ALPHA_LIGHT
        else ->
            VIDEO_CARD_TRANSITION_MAX_SCRIM_ALPHA_DARK
    }
    return maxAlpha * clamped
}

/** Shared by the scrim-only path and the complete depth frame; no blur geometry is needed. */
internal fun resolveVideoCardTransitionBackgroundScrimAlpha(
    depthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    motionTier: MotionTier,
    isLightBackground: Boolean,
): Float = if (phase == VideoCardTransitionBackgroundPhase.IDLE) {
    0f
} else {
    resolveVideoCardTransitionScrimAlpha(
        progress = depthProgress,
        isLightBackground = isLightBackground,
        motionTier = motionTier,
    )
}

internal fun resolveVideoCardTransitionContentScale(
    progress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    motionTier: MotionTier,
    scaleReduction: Float = VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION,
): Float {
    if (phase == VideoCardTransitionBackgroundPhase.IDLE || motionTier == MotionTier.Reduced) {
        return 1f
    }
    val depthProgress = resolveVideoCardTransitionDepthProgress(
        progress = progress,
        phase = phase,
    )
    return 1f - scaleReduction.coerceIn(0f, 0.08f) * depthProgress
}

/**
 * 背景作为整页远平面围绕屏幕中心退后，点击不同卡片时不改变后退方向。
 * 飞卡自己的 bounds morph 仍从被点击卡片出发；这里只控制背后的列表与 chrome。
 */
@Suppress("UNUSED_PARAMETER")
internal fun resolveVideoCardTransitionBackgroundScalePivot(
    sourceBounds: Rect?,
    canvasWidth: Float,
    canvasHeight: Float,
): Offset = Offset(0.5f, 0f)

internal fun resolveVideoCardTransitionOverlayDepthPivot(
    sourceBounds: Rect?,
    canvasWidth: Float,
    canvasHeight: Float,
    overlayWidth: Float,
    overlayHeight: Float,
): Offset {
    val canvasPivot = resolveVideoCardTransitionBackgroundScalePivot(
        sourceBounds = sourceBounds,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
    )
    val width = overlayWidth.coerceAtLeast(1f)
    val height = overlayHeight.coerceAtLeast(1f)
    return Offset(
        x = (canvasPivot.x * canvasWidth.coerceAtLeast(0f)) / width,
        y = (canvasPivot.y * canvasHeight.coerceAtLeast(0f)) / height,
    )
}

internal fun Modifier.videoCardTransitionChromeReveal(
    revealProvider: () -> Float,
    slideDown: Boolean,
): Modifier = this
    .graphicsLayer {
        alpha = revealProvider().coerceIn(0f, 1f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // Progress changes placement, not constraints or size. Keep the read here
            // so predictive-back frames do not remeasure the dock and its children.
            val reveal = revealProvider().coerceIn(0f, 1f)
            val dy = ((1f - reveal) * placeable.height).roundToInt()
            placeable.place(0, if (slideDown) dy else -dy)
        }
    }

internal fun Modifier.videoCardTransitionOverlayDepthEffect(
    progressProvider: () -> Float,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    motionTierProvider: () -> MotionTier,
    sourceBoundsProvider: () -> Rect?,
    scaleReductionProvider: () -> Float = { VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION },
    densityProvider: () -> Float,
): Modifier {
    // Reuse the effect while quantization keeps the radius unchanged. Keep this cache local
    // to the modifier, outside snapshot state, so layer updates do not trigger composition.
    var cachedBlurRadiusPx = Float.NaN
    var cachedBlurEffect: BlurEffect? = null
    val blurEffects = VideoCardTransitionBlurEffectCache()
    return this.graphicsLayer {
        val progress = progressProvider()
        val phase = phaseProvider()
        val motionTier = motionTierProvider()
        // Keep the live chrome on the same visual frame contract as the retained source page.
        // In particular, phase-specific radius quantization must match on both layers or their
        // shared edge reads as a sharp blur step during predictive back.
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = progress,
            phase = phase,
            motionTier = motionTier,
            isGestureRestoreInProgress = false,
            density = densityProvider(),
            scaleReduction = scaleReductionProvider(),
        )
        // The caller supplies a full-viewport layer, so use its measured coordinates instead
        // of LocalConfiguration. This keeps the pivot identical under edge-to-edge insets.
        val canvasW = size.width
        val canvasH = size.height
        val pivot = resolveVideoCardTransitionOverlayDepthPivot(
            sourceBounds = sourceBoundsProvider(),
            canvasWidth = canvasW,
            canvasHeight = canvasH,
            overlayWidth = size.width,
            overlayHeight = size.height,
        )
        scaleX = frame.contentScale
        scaleY = frame.contentScale
        transformOrigin = TransformOrigin(pivot.x, pivot.y)
        if (frame.blurRadiusPx != cachedBlurRadiusPx) {
            cachedBlurRadiusPx = frame.blurRadiusPx
            cachedBlurEffect = blurEffects.resolve(frame.blurRadiusPx)
        }
        renderEffect = cachedBlurEffect
    }
}

internal fun resolveVideoCardTransitionBackgroundScalePivotOffset(
    sourceBounds: Rect?,
    canvasSize: Size,
): Offset {
    val pivot = resolveVideoCardTransitionBackgroundScalePivot(
        sourceBounds = sourceBounds,
        canvasWidth = canvasSize.width,
        canvasHeight = canvasSize.height,
    )
    return Offset(
        x = pivot.x * canvasSize.width.coerceAtLeast(0f),
        y = pivot.y * canvasSize.height.coerceAtLeast(0f),
    )
}

internal fun resolveVideoCardTransitionBackgroundFrame(
    progress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    motionTier: MotionTier = MotionTier.Normal,
    isLightBackground: Boolean = false,
    isGestureRestoreInProgress: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
    /** 屏幕密度（px/dp）。模糊/圆角按 dp 标定，调用方须传 DrawScope density。 */
    density: Float = 1f,
    /** 设备物理屏圆角（px）；0 表示未知，走 24dp 兜底。 */
    deviceCornerRadiusPx: Float = 0f,
    scaleReduction: Float = VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION,
    /** 背景遮罩独立深度（引擎 maskAlpha 通道）；缺省跟随 progress。 */
    maskProgress: Float = progress,
): VideoCardTransitionBackgroundFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val depthProgress = resolveVideoCardTransitionDepthProgress(
        progress = clamped,
        phase = phase,
    )
    val maskDepthProgress = resolveVideoCardTransitionDepthProgress(
        progress = maskProgress.coerceIn(0f, 1f),
        phase = phase,
    )
    val blurStrength = resolveVideoCardTransitionBlurStrength(depthProgress)
    val maxBlurRadiusPx = resolveVideoCardTransitionMaxBlurRadiusPx(motionTier, density)
    // 仅系统减弱动画(Reduced) / API<31 跳过 GPU 模糊；不按机型降级峰值。
    val rawBlurRadiusPx = if (
        phase != VideoCardTransitionBackgroundPhase.IDLE &&
        maxBlurRadiusPx > 0f &&
        sdkInt >= Build.VERSION_CODES.S
    ) {
        maxBlurRadiusPx * blurStrength
    } else {
        0f
    }

    val blurQuantumPx = resolveVideoCardTransitionBlurQuantumPx(
        motionTier = motionTier,
        phase = phase,
    )
    val cornerRadiusPx = if (
        scaleReduction > 0.001f &&
        phase != VideoCardTransitionBackgroundPhase.IDLE &&
        motionTier != MotionTier.Reduced
    ) {
        resolveVideoCardTransitionBackgroundCornerRadiusPx(
            depthProgress = depthProgress,
            motionTier = motionTier,
            density = density,
            deviceCornerRadiusPx = deviceCornerRadiusPx,
        )
    } else {
        0f
    }
    val shadowElevationPx = if (cornerRadiusPx > 0.01f) {
        (16f * density * depthProgress).coerceIn(0f, 24f * density)
    } else {
        0f
    }
    return VideoCardTransitionBackgroundFrame(
        blurRadiusPx = quantizeVideoCardTransitionBlurRadius(
            radiusPx = rawBlurRadiusPx,
            maxRadiusPx = maxBlurRadiusPx,
            quantumPx = blurQuantumPx,
        ),
        blurAlpha = blurStrength,
        scrimAlpha = resolveVideoCardTransitionBackgroundScrimAlpha(
            depthProgress = maskDepthProgress,
            phase = phase,
            motionTier = motionTier,
            isLightBackground = isLightBackground,
        ),
        contentScale = resolveVideoCardTransitionContentScale(
            progress = clamped,
            phase = phase,
            motionTier = motionTier,
            scaleReduction = scaleReduction,
        ),
        useLightScrimTint = isLightBackground,
        cornerRadiusPx = cornerRadiusPx,
        shadowElevationPx = shadowElevationPx,
    )
}

/**
 * 读设备物理屏圆角（px）。取四角最大值。
 * API < 31 或 insets 未就绪 / OEM 返回 0 → 0（由 fallback dp 兜底）。
 */
internal fun resolveDeviceDisplayCornerRadiusPx(
    rootWindowInsets: android.view.WindowInsets?,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Float {
    if (
        sdkInt < Build.VERSION_CODES.S ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        rootWindowInsets == null
    ) {
        return 0f
    }
    val positions = intArrayOf(
        android.view.RoundedCorner.POSITION_TOP_LEFT,
        android.view.RoundedCorner.POSITION_TOP_RIGHT,
        android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
        android.view.RoundedCorner.POSITION_BOTTOM_RIGHT,
    )
    var maxRadius = 0
    for (position in positions) {
        val radius = rootWindowInsets.getRoundedCorner(position)?.radius ?: 0
        if (radius > maxRadius) maxRadius = radius
    }
    return maxRadius.toFloat()
}

/**
 * 满深度圆角（dp）：max(设备物理圆角换算 dp, [fallbackDp])。
 */
internal fun resolveVideoCardTransitionBackgroundCornerRadiusDp(
    deviceCornerRadiusPx: Float,
    density: Float,
    fallbackDp: Float = VIDEO_CARD_TRANSITION_BACKGROUND_CORNER_FALLBACK_DP,
): Float {
    val densitySafe = density.coerceAtLeast(0.01f)
    val deviceDp = if (deviceCornerRadiusPx > 0f) {
        deviceCornerRadiusPx / densitySafe
    } else {
        0f
    }
    return maxOf(deviceDp, fallbackDp.coerceAtLeast(0f))
}

/**
 * 退后页面圆角半径（px）：随景深线性建立。
 * 满深度 = max(设备圆角, 24dp 兜底) × density。
 * Reduced 档不缩放也不模糊，圆角同样跳过。
 */
internal fun resolveVideoCardTransitionBackgroundCornerRadiusPx(
    depthProgress: Float,
    motionTier: MotionTier,
    density: Float,
    deviceCornerRadiusPx: Float = 0f,
): Float {
    if (motionTier == MotionTier.Reduced) return 0f
    val fullRadiusDp = resolveVideoCardTransitionBackgroundCornerRadiusDp(
        deviceCornerRadiusPx = deviceCornerRadiusPx,
        density = density,
    )
    return fullRadiusDp *
        density.coerceAtLeast(0f) *
        depthProgress.coerceIn(0f, 1f)
}

/**
 * 预测式返回手势进行中时，把系统回退进度(0→1)映射为背景虚化进度(1→0)。
 *
 * - 手势起点(0)保持满虚化，与 [VideoCardTransitionBackgroundPhase.HELD] 无缝衔接；
 * - 拖到底(1)则背景基本清晰，从而让全屏 GPU 模糊随手势实时消退，
 *   与共享元素 morph 落位同步，避免提交返回后再补一段独立模糊。
 */
internal fun resolveVideoCardTransitionBackgroundGestureProgress(
    backProgress: Float
): Float {
    val clamped = backProgress.coerceIn(0f, 1f)
    return 1f - clamped
}

/**
 * [VideoCardTransitionBackgroundPhase.OPENING] 阶段预测式返回：以当前开场虚化进度为起点，
 * 随手势线性消退至清晰。与 HELD 满值起点的 [resolveVideoCardTransitionBackgroundGestureProgress] 区分。
 */
internal fun resolveVideoCardTransitionBackgroundOpeningGestureProgress(
    openingBlurProgress: Float,
    backProgress: Float,
): Float {
    val clampedOpening = openingBlurProgress.coerceIn(0f, 1f)
    val clampedBack = backProgress.coerceIn(0f, 1f)
    return clampedOpening * (1f - clampedBack)
}

internal fun isVideoCardTransitionBackgroundGesturePhase(
    phase: VideoCardTransitionBackgroundPhase,
): Boolean {
    return phase == VideoCardTransitionBackgroundPhase.HELD ||
        phase == VideoCardTransitionBackgroundPhase.OPENING
}

internal fun resolveVideoCardTransitionBackgroundGestureBlurProgress(
    phase: VideoCardTransitionBackgroundPhase,
    currentBlurProgress: Float,
    backProgress: Float,
): Float {
    return when (phase) {
        VideoCardTransitionBackgroundPhase.HELD ->
            resolveVideoCardTransitionBackgroundGestureProgress(backProgress)
        VideoCardTransitionBackgroundPhase.OPENING ->
            resolveVideoCardTransitionBackgroundOpeningGestureProgress(
                openingBlurProgress = currentBlurProgress,
                backProgress = backProgress,
            )
        else -> currentBlurProgress
    }
}

/**
 * 景深返回与共享元素使用同一个满进度时长；被打断时只按实际剩余进度缩短，
 * 不再切换到另一套“快速返回”节奏。
 */
internal fun resolveVideoCardTransitionReturnFullDurationMillis(
    baseDurationMillis: Int,
): Int {
    return baseDurationMillis.coerceAtLeast(0)
}

/**
 * 返回动画提交时，若手势已消解部分虚化(startProgress < 1)，剩余 [RETURNING] 动画按比例缩短。
 *
 * 默认 [minDurationMs] 仅用于**非 morph 对齐**的取消收尾；与 shell morph 同墙钟时请用
 * [resolveMorphAlignedDepthClearDurationMs]（min=0，禁止把糊拖过落位）。
 */
internal fun resolveVideoCardTransitionBackgroundReturnDurationMs(
    startProgress: Float,
    fullDurationMs: Int = VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS,
    minDurationMs: Int = VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS
): Int {
    val clamped = startProgress.coerceIn(0f, 1f)
    val safeFull = fullDurationMs.coerceAtLeast(0)
    val raw = (safeFull * clamped).roundToInt()
    if (minDurationMs <= 0) return raw.coerceIn(0, safeFull.coerceAtLeast(0))
    val safeMin = minDurationMs.coerceAtMost(safeFull.coerceAtLeast(minDurationMs))
    return raw.coerceIn(safeMin, safeFull.coerceAtLeast(safeMin))
}

/**
 * 景深消糊时长与 shared morph 剩余时长锁步。
 *
 * - morphRemainingMs：shell bounds 后半段（或满程）墙钟
 * - blurStartProgress：提交时剩余模糊 1→0
 * - 结果 = morph * blur，Linear 播完时二者同时到 0，避免「卡已落位、背景还糊」
 */
internal fun resolveMorphAlignedDepthClearDurationMs(
    morphRemainingMs: Int,
    blurStartProgress: Float,
): Int {
    return resolveVideoCardTransitionBackgroundReturnDurationMs(
        startProgress = blurStartProgress,
        fullDurationMs = morphRemainingMs.coerceAtLeast(0),
        minDurationMs = 0,
    )
}

/**
 * OPENING 中途被返回打断时，必须从当前 progress 反转，禁止先补完进场再关。
 */
internal fun shouldInterruptVideoCardOpeningOnReturn(
    phase: VideoCardTransitionBackgroundPhase,
): Boolean = phase == VideoCardTransitionBackgroundPhase.OPENING

internal fun shouldApplyVideoCardTransitionBackgroundToRoute(
    entryRoute: String?,
    sourceRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    val normalizedEntryRoute = normalizeVideoCardTransitionRoute(entryRoute) ?: return false
    val normalizedSourceRoute = normalizeVideoCardTransitionRoute(sourceRoute) ?: return false
    if (!isVideoCardReturnTargetRoute(normalizedSourceRoute)) return false
    if (normalizedEntryRoute == normalizedSourceRoute) return true
    val normalizedActiveMainHostRoute = normalizeVideoCardTransitionRoute(activeMainHostRoute)
    if (
        normalizedEntryRoute == "main_host" &&
        normalizedActiveMainHostRoute == normalizedSourceRoute
    ) {
        return true
    }
    // 首页顶栏内嵌分区：共享元素 source 是 partition，视觉宿主仍是 home / main_host(home)。
    if (normalizedSourceRoute == "partition") {
        if (normalizedEntryRoute == "home") return true
        if (normalizedEntryRoute == "main_host" && normalizedActiveMainHostRoute == "home") {
            return true
        }
    }
    return false
}

/**
 * 详情压详情的真实父页景深。
 *
 * 这里直接变换仍在 NavDisplay 中的父 entry，不把整页录进 GraphicsLayer。这样既保留
 * 模糊、收缩、圆角和 scrim，也不会把独立渲染的 Player Surface 固化成黑色快照。
 */
@Composable
internal fun Modifier.videoCardTransitionLiveBackgroundEffect(
    progressProvider: () -> Float,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    exposureProvider: () -> VideoCardTransitionExposure,
    isGestureRestoreInProgressProvider: () -> Boolean = { false },
    motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    isLightBackgroundProvider: () -> Boolean = { false },
    realtimeBlurEnabledProvider: () -> Boolean = { false },
    scaleReductionProvider: () -> Float = { VIDEO_CARD_TRANSITION_RELATED_SCALE_REDUCTION },
    sourceBoundsProvider: () -> Rect? = { null },
): Modifier {
    val view = LocalView.current
    val screenDensity = LocalDensity.current.density
    var deviceCornerRadiusPx by remember { mutableFloatStateOf(0f) }
    SideEffect {
        deviceCornerRadiusPx = resolveDeviceDisplayCornerRadiusPx(view.rootWindowInsets)
    }
    val frameState = remember(
        progressProvider,
        phaseProvider,
        exposureProvider,
        isGestureRestoreInProgressProvider,
        motionTierProvider,
        isLightBackgroundProvider,
        scaleReductionProvider,
        screenDensity,
    ) {
        derivedStateOf {
            val exposure = exposureProvider()
            val renderDecision = resolveVideoCardTransitionRenderDecision(exposure)
            val retainHeldDepth = shouldPaintRetainedSourceWithoutTransitionBackground(
                renderDecision
            )
            val activePhase = if (
                renderDecision.drawTransitionBackground || retainHeldDepth
            ) {
                phaseProvider()
            } else {
                VideoCardTransitionBackgroundPhase.IDLE
            }
            resolveVideoCardTransitionBackgroundFrame(
                progress = if (retainHeldDepth) 1f else progressProvider(),
                phase = activePhase,
                motionTier = motionTierProvider(),
                isLightBackground = isLightBackgroundProvider(),
                isGestureRestoreInProgress = isGestureRestoreInProgressProvider(),
                density = screenDensity,
                deviceCornerRadiusPx = deviceCornerRadiusPx,
                scaleReduction = scaleReductionProvider(),
            )
        }
    }

    // Reuse the render effect and clip shape while the quantized radius stays unchanged. Kept
    // local to the modifier (outside snapshot state) so per-frame layer updates do not rebuild
    // BlurEffect/RoundedCornerShape, matching the overlay depth layer above. This is uniform for
    // every device: blur stays enabled, only redundant allocations are avoided.
    var cachedLiveBlurRadiusPx = Float.NaN
    var cachedLiveBlurEffect: BlurEffect? = null
    // Retain quantized radii across gesture reversals and ordinary recompositions.
    val liveBlurEffects = remember { VideoCardTransitionBlurEffectCache() }
    var cachedLiveCornerRadiusPx = Float.NaN
    var cachedLiveClipShape: RoundedCornerShape? = null

    return this
        .drawWithContent {
            val frame = frameState.value
            if (shouldDrawVideoCardTransitionScaleGapFill(frame.contentScale)) {
                drawRect(
                    resolveVideoCardTransitionScaleGapFillColor(
                        isLightBackground = frame.useLightScrimTint,
                        scrimAlpha = frame.scrimAlpha,
                    )
                )
            }
            drawContent()
            if (frame.scrimAlpha > 0.001f) {
                val scrimColor = if (frame.useLightScrimTint) {
                    VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
                } else {
                    Color.Black
                }
                drawRect(scrimColor.copy(alpha = frame.scrimAlpha))
            }
        }
        .graphicsLayer {
            val frame = frameState.value
            val pivot = resolveVideoCardTransitionBackgroundScalePivot(
                sourceBounds = sourceBoundsProvider(),
                canvasWidth = size.width,
                canvasHeight = size.height,
            )
            scaleX = frame.contentScale
            scaleY = frame.contentScale
            transformOrigin = TransformOrigin(pivot.x, pivot.y)
            val shouldClip = frame.cornerRadiusPx > 0.01f
            clip = shouldClip
            if (shouldClip) {
                val cornerRadiusPx = frame.cornerRadiusPx
                if (cornerRadiusPx != cachedLiveCornerRadiusPx) {
                    cachedLiveCornerRadiusPx = cornerRadiusPx
                    cachedLiveClipShape = RoundedCornerShape(
                        (cornerRadiusPx / screenDensity.coerceAtLeast(0.01f)).dp
                    )
                }
                cachedLiveClipShape?.let { shape = it }
                shadowElevation = frame.shadowElevationPx
            } else {
                shadowElevation = 0f
            }
            val liveBlurRadiusPx = if (realtimeBlurEnabledProvider()) {
                frame.blurRadiusPx
            } else {
                0f
            }
            if (liveBlurRadiusPx != cachedLiveBlurRadiusPx) {
                cachedLiveBlurRadiusPx = liveBlurRadiusPx
                cachedLiveBlurEffect = liveBlurEffects.resolve(liveBlurRadiusPx)
            }
            renderEffect = cachedLiveBlurEffect
        }
}

/**
 * 视频卡片过渡期间 Nav 层全屏 backdrop：填补 sharedBounds morph / 预测式返回
 * 在屏幕边缘露出的窗口底色，视觉上延续首页虚化后的色调。
 */
internal data class VideoCardTransitionNavBackdropFrame(
    val scrimAlpha: Float,
    val useLightScrimTint: Boolean,
)

internal fun shouldShowVideoCardTransitionNavBackdrop(
    cardTransitionEnabled: Boolean,
    exposure: VideoCardTransitionExposure,
    isVideoDetailOnStack: Boolean,
    isReturningToVideoDetail: Boolean = false,
): Boolean {
    if (!cardTransitionEnabled || isReturningToVideoDetail) return false
    val decision = resolveVideoCardTransitionRenderDecision(exposure)
    // pop 提交后栈顶已是来源页，但共享壳仍在 overlay 中回收；背景必须留到 Returning 结束。
    if (exposure == VideoCardTransitionExposure.Returning) return decision.drawNavBackdrop
    if (!isVideoDetailOnStack) return false
    return decision.drawNavBackdrop
}

internal fun resolveVideoCardTransitionNavBackdropFrame(
    progress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isLightBackground: Boolean,
): VideoCardTransitionNavBackdropFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val scrimAlpha = when (phase) {
        VideoCardTransitionBackgroundPhase.OPENING,
        VideoCardTransitionBackgroundPhase.HELD,
        VideoCardTransitionBackgroundPhase.RETURNING ->
            resolveVideoCardTransitionScrimAlpha(
                progress = clamped,
                isLightBackground = isLightBackground,
                motionTier = MotionTier.Normal,
            )
        else -> 0f
    }
    return VideoCardTransitionNavBackdropFrame(
        scrimAlpha = scrimAlpha,
        useLightScrimTint = isLightBackground,
    )
}

internal fun resolveVideoCardTransitionNavBackdropColor(
    baseBackgroundColor: Color,
    frame: VideoCardTransitionNavBackdropFrame,
): Color {
    return resolveVideoCardTransitionScaleGapFillColor(
        isLightBackground = frame.useLightScrimTint,
        scrimAlpha = frame.scrimAlpha,
        baseBackgroundColor = baseBackgroundColor,
    )
}

/**
 * 景深 scale<1 时，缩放层四周会露出父级/窗口底色。
 * 用与 blur scrim 同向的不透明填充盖住空隙，避免预测性返回读成右侧白条。
 */
internal fun shouldDrawVideoCardTransitionScaleGapFill(contentScale: Float): Boolean {
    return contentScale < 0.999f
}

internal fun resolveVideoCardTransitionScaleGapFillColor(
    isLightBackground: Boolean,
    scrimAlpha: Float,
    baseBackgroundColor: Color = if (isLightBackground) {
        Color.White
    } else {
        VIDEO_CARD_TRANSITION_DARK_GAP_BASE
    },
): Color {
    val tint = if (isLightBackground) {
        VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
    } else {
        Color.Black
    }
    val minTint = if (isLightBackground) {
        VIDEO_CARD_TRANSITION_SCALE_GAP_MIN_TINT_LIGHT
    } else {
        VIDEO_CARD_TRANSITION_SCALE_GAP_MIN_TINT_DARK
    }
    val fraction = maxOf(scrimAlpha, minTint).coerceIn(0f, 1f)
    return lerp(
        start = baseBackgroundColor,
        stop = tint,
        fraction = fraction,
    )
}

internal fun shouldUseVideoCardTransitionSnapshotBlur(
    exposure: VideoCardTransitionExposure,
    motionTier: MotionTier,
    realtimeBlurEnabled: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    if (!resolveVideoCardTransitionRenderDecision(exposure).updateBlurEffect) return false
    if (motionTier == MotionTier.Reduced) return false
    if (!realtimeBlurEnabled) return false
    return sdkInt >= Build.VERSION_CODES.S
}

/** 浅色 scrim 色，供 Host 景深层与源页 effect 共用。 */
internal val VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT = Color(0xFF8E8E93)

/**
 * 是否对来源页做每帧 live 重录。
 *
 * 真机 gfxinfo：OPENING/RETURNING 全页 live 重录+模糊会把 p90/p99 拉到百毫秒级
 *（Slow UI thread / Slow issue draw commands）。默认关闭 live，改用冻结层 +
 * 进度驱动 BlurEffect——仍是动态模糊观感，成本可控。
 */
internal fun shouldLiveRecordVideoCardTransitionSnapshot(
    phase: VideoCardTransitionBackgroundPhase,
): Boolean {
    return false
}

/**
 * 卡片开合景深（来源路由上的录制 / 绘制端）：
 * - OPENING：首帧 record 进 Host [snapshotHandle]，BlurEffect 跟进度
 * - HELD / 预测 / 返回：Host 拥有冻结层生命周期；本 Modifier **dispose 不得 invalidate**
 *   Host 快照。源页若仍在 composition，可继续用同一层跟手改半径；否则由
 *   [VideoCardTransitionHostDepthLayer] 在 NavDisplay 下绘制。
 * - IDLE：Host 释放会话
 * - API 31 以下 / 实时模糊关闭：保留 scrim 与元素级缩放
 * - Reduced：只保留轻 scrim
 */
@Composable
internal fun Modifier.videoCardTransitionBackgroundEffect(
    progressProvider: () -> Float,
    maskProgressProvider: (() -> Float)? = null,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    exposureProvider: () -> VideoCardTransitionExposure,
    isGestureRestoreInProgressProvider: () -> Boolean = { false },
    motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    isLightBackgroundProvider: () -> Boolean = { false },
    realtimeBlurEnabledProvider: () -> Boolean = { false },
    scaleReductionProvider: () -> Float = { VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION },
    sourceBoundsProvider: () -> Rect? = { null },
    snapshotHandle: VideoCardTransitionSnapshotHandle? = null,
    reuseJustRecordedSourceForHandoff: Boolean = false,
): Modifier {
    val isHostOwnedSnapshot = snapshotHandle != null
    // A host-owned handle already supplies both resources; allocate the fallback only when
    // this source actually owns its snapshot (including after a handle ownership change).
    val effectiveSnapshotHandle = snapshotHandle ?: rememberVideoCardTransitionSnapshotHandle()
    val contentLayer = effectiveSnapshotHandle.contentLayer
    val snapshotState = effectiveSnapshotHandle.state
    val sourceRendererToken = remember(snapshotState, contentLayer) { Any() }
    val view = LocalView.current
    var deviceCornerRadiusPx by remember { mutableFloatStateOf(0f) }
    // Keep the shared session snapshot when the last source detaches. A remounted
    // source refreshes it after Host bridges the preparation frame.
    DisposableEffect(snapshotState, contentLayer, isHostOwnedSnapshot) {
        if (isHostOwnedSnapshot) snapshotState.attachSourceRenderer(sourceRendererToken)
        onDispose {
            if (isHostOwnedSnapshot) snapshotState.detachSourceRenderer(sourceRendererToken)
            if (shouldInvalidateSnapshotOnSourceDispose(isHostOwnedSnapshot = isHostOwnedSnapshot)) {
                contentLayer.renderEffect = null
                snapshotState.invalidateRecordedContent()
            } else if (!snapshotState.hasAttachedSourceRenderer) {
                // Host 会话层：保留 OPENING 冻结帧供 SettledHidden 满糊预热。
                // 不标 displayListStale（否则完整进详情后 Host 无法预热 → 返回无糊）。
                // 仅标记 needsSourceRefresh，源页再次挂上时重录真实首页。
                contentLayer.renderEffect = null
                snapshotState.markSourceDetachedForRefresh()
            }
        }
    }
    // insets 首帧可能为空；每次重组刷新，开场前通常已就绪。
    SideEffect {
        deviceCornerRadiusPx = resolveDeviceDisplayCornerRadiusPx(view.rootWindowInsets)
    }
    val phase = phaseProvider()
    val exposure = exposureProvider()
    val motionTier = motionTierProvider()
    val renderDecision = resolveVideoCardTransitionRenderDecision(exposure)
    val useSnapshotBlur = shouldUseVideoCardTransitionSnapshotBlur(
        exposure = exposure,
        motionTier = motionTier,
        realtimeBlurEnabled = realtimeBlurEnabledProvider(),
    )
    val retainSnapshot = renderDecision.retainSourceSnapshot &&
        motionTier != MotionTier.Reduced &&
        realtimeBlurEnabledProvider() &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(phase, exposure, retainSnapshot, isHostOwnedSnapshot, sourceRendererToken) {
        if (!retainSnapshot) {
            // Host 会话层由 Host IDLE 释放；源页不得因 SettledHidden 误 reset。
            if (!isHostOwnedSnapshot) {
                effectiveSnapshotHandle.releaseSession()
            }
            snapshotState.markSourceRendererReady(sourceRendererToken, true)
            return@LaunchedEffect
        }
        when (exposure) {
            VideoCardTransitionExposure.Opening -> {
                // 新开一场：允许首帧 record；draw 侧只录一次后冻结。
                snapshotState.freezeRecording = false
                snapshotState.hasRecordedContent = false
                snapshotState.displayListStale = false
                withFrameNanos { }
                snapshotState.freezeRecording = true
            }
            VideoCardTransitionExposure.BackPreview,
            VideoCardTransitionExposure.Returning,
            VideoCardTransitionExposure.Restoring -> {
                // 源页重新挂上：stale 或 dispose 后的 needsSourceRefresh 都允许重录。
                val mustRefresh = snapshotState.needsSourceRefresh ||
                    !isVideoCardTransitionSnapshotDrawable(
                        hasRecordedContent = snapshotState.hasRecordedContent,
                        displayListStale = snapshotState.displayListStale,
                    )
                if (mustRefresh) {
                    snapshotState.markSourceRendererReady(sourceRendererToken, false)
                    snapshotState.freezeRecording = false
                    withFrameNanos { }
                } else {
                    snapshotState.freezeRecording = true
                }
            }
            VideoCardTransitionExposure.SettledHidden -> {
                snapshotState.freezeRecording = true
            }
            VideoCardTransitionExposure.Idle -> {
                if (!isHostOwnedSnapshot) {
                    effectiveSnapshotHandle.releaseSession()
                }
            }
        }
        snapshotState.markSourceRendererReady(sourceRendererToken, true)
    }

    SideEffect {
        // Host 拥有层时 SettledHidden 不在源页 clear（源页通常已 dispose）。
        if (!renderDecision.updateBlurEffect) {
            if (isHostOwnedSnapshot &&
                exposure == VideoCardTransitionExposure.SettledHidden
            ) {
                return@SideEffect
            }
            if (!isHostOwnedSnapshot) {
                contentLayer.renderEffect = null
                snapshotState.lastBlurRadiusPx = Float.NaN
            }
        }
    }

    val liveRecordingActive = useSnapshotBlur &&
        shouldLiveRecordVideoCardTransitionSnapshot(
            phase = phase,
        )
    VideoCardTransitionLiveBlurHitchLogger(
        phaseProvider = phaseProvider,
        liveRecordingActive = liveRecordingActive,
    )

    // 无模糊时隔离实时内容的绘制指令：外层遮罩更新不重新执行列表绘制，
    // 图片、数据和来源卡显隐仍按自身失效信号更新，不冻结为整页快照。
    // 模糊路径继续直接录制来源，避免冻结层持有随实时内容变化的子图层。
    val isolateLiveContent = motionTier == MotionTier.Reduced ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !realtimeBlurEnabledProvider()
    return this.drawWithContent {
        val activePhase = phaseProvider()
        val activeExposure = exposureProvider()
        val activeDecision = resolveVideoCardTransitionRenderDecision(activeExposure)
        if (isHostOwnedSnapshot && shouldPaintHostOwnedDepthLayer(
                exposure = activeExposure,
                hasRecordedContent = snapshotState.hasRecordedContent,
                displayListStale = snapshotState.displayListStale,
                motionTier = motionTierProvider(),
                realtimeBlurEnabled = realtimeBlurEnabledProvider(),
                sourceRendererReady = snapshotState.hasReadySourceRenderer,
            )
        ) {
            // Host owns the preparation frame. The effect transfers ownership after
            // a frame boundary; both draw sites observe the same readiness state.
            return@drawWithContent
        }
        // Once prepared, the source owns both held and exposed depth drawing.
        // Missing/stale snapshots always fall back to live content rather than an empty frame.
        if (!activeDecision.drawTransitionBackground) {
            if (activeDecision.drawSourceNormally) {
                drawContent()
            } else if (shouldPaintRetainedSourceWithoutTransitionBackground(activeDecision)) {
                // SettledHidden：有可用快照则按满糊画；否则 live 防黑。
                if (
                    isVideoCardTransitionSnapshotDrawable(
                        hasRecordedContent = snapshotState.hasRecordedContent,
                        displayListStale = snapshotState.displayListStale,
                    )
                ) {
                    val resolvedHeldFrame = snapshotState.frameCache.resolve(
                        progress = 1f,
                        phase = VideoCardTransitionBackgroundPhase.HELD,
                        motionTier = motionTierProvider(),
                        isLightBackground = isLightBackgroundProvider(),
                        isGestureRestoreInProgress = false,
                        density = density,
                        deviceCornerRadiusPx = deviceCornerRadiusPx,
                        scaleReduction = scaleReductionProvider(),
                    )
                    val heldFrame = if (realtimeBlurEnabledProvider()) {
                        resolvedHeldFrame
                    } else {
                        resolvedHeldFrame.copy(blurRadiusPx = 0f)
                    }
                    applyVideoCardTransitionSnapshotFrame(
                        contentLayer = contentLayer,
                        snapshotState = snapshotState,
                        frame = heldFrame,
                        canvasSize = size,
                        sourceBounds = sourceBoundsProvider(),
                        rasterScale = effectiveSnapshotHandle.prepareEffectLayer(this, heldFrame.blurRadiusPx),
                    )
                    drawLayer(contentLayer)
                    VideoCardTransitionDiagnostics.onSourceLayerDrawn()
                } else {
                    drawContent()
                }
            }
            return@drawWithContent
        }
        val activeProgress = progressProvider()
        // 遮罩走独立弹簧通道（引擎 maskAlpha）：压暗先于模糊到位。
        val activeMaskProgress = maskProgressProvider?.invoke() ?: activeProgress
        val activeMotionTier = motionTierProvider()
        val snapshotBlurActive = shouldUseVideoCardTransitionSnapshotBlur(
            exposure = activeExposure,
            motionTier = activeMotionTier,
            realtimeBlurEnabled = realtimeBlurEnabledProvider(),
        )
        if (!snapshotBlurActive) {
            // Blur off / Reduced / older APIs: only scrim changes. Do not compute or
            // allocate the unused blur, corner, shadow and scale frame on every draw.
            drawContent()
            val isLightBackground = isLightBackgroundProvider()
            val scrimAlpha = resolveVideoCardTransitionBackgroundScrimAlpha(
                depthProgress = resolveVideoCardTransitionDepthProgress(activeProgress, activePhase),
                phase = activePhase,
                motionTier = activeMotionTier,
                isLightBackground = isLightBackground,
            )
            if (scrimAlpha > 0.001f) {
                val scrimColor = if (isLightBackground) {
                    VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
                } else {
                    Color.Black
                }
                drawRect(scrimColor.copy(alpha = scrimAlpha))
            }
            return@drawWithContent
        }

        val frozenLayerAlpha = resolveVideoCardTransitionFrozenLayerAlpha(
            exposure = activeExposure,
            depthProgress = activeProgress,
        )
        if (!shouldDrawVideoCardTransitionFrozenLayer(frozenLayerAlpha)) {
            // The live page has taken over. Skip recording and invisible blur updates.
            // Keep refresh state intact so gesture reversal can reacquire a fresh snapshot.
            drawContent()
            val isLight = isLightBackgroundProvider()
            val scrimAlpha = resolveVideoCardTransitionBackgroundScrimAlpha(
                depthProgress = resolveVideoCardTransitionDepthProgress(activeMaskProgress, activePhase),
                phase = activePhase,
                motionTier = activeMotionTier,
                isLightBackground = isLight,
            )
            if (scrimAlpha > 0.001f) {
                drawRect((if (isLight) VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT else Color.Black)
                    .copy(alpha = scrimAlpha))
            }
            return@drawWithContent
        }

        val frame = snapshotState.frameCache.resolve(
            progress = activeProgress,
            phase = activePhase,
            motionTier = activeMotionTier,
            isLightBackground = isLightBackgroundProvider(),
            isGestureRestoreInProgress = isGestureRestoreInProgressProvider(),
            density = density,
            deviceCornerRadiusPx = deviceCornerRadiusPx,
            scaleReduction = scaleReductionProvider(),
            maskProgress = activeMaskProgress,
        )

        // OPENING 首录；预测/返回 DL stale 时重录。
        // 防黑：失效层绝不 drawLayer。防无糊：成功 record 后只画带 BlurEffect 的冻结层，
        // 不要先 drawContent 再叠层——部分机型上 live 底会压过/闪掉景深。
        val needsRecord = snapshotState.needsSourceRefresh ||
            !isVideoCardTransitionSnapshotDrawable(
                hasRecordedContent = snapshotState.hasRecordedContent,
                displayListStale = snapshotState.displayListStale,
            )
        if (needsRecord) {
            if (size.width <= 0f || size.height <= 0f) {
                drawContent()
                if (frame.scrimAlpha > 0.001f) {
                    val scrimColor = if (frame.useLightScrimTint) {
                        VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
                    } else {
                        Color.Black
                    }
                    drawRect(scrimColor.copy(alpha = frame.scrimAlpha))
                }
                return@drawWithContent
            }
            effectiveSnapshotHandle.recordSource(this) {
                this@drawWithContent.drawContent()
            }
            snapshotState.markDisplayListFresh()
            // 强制下一帧路径重绑 BlurEffect（dispose 时 renderEffect 已清）。
            snapshotState.lastBlurRadiusPx = Float.NaN
            VideoCardTransitionDiagnostics.onSnapshotRecorded()
        }

        val canDrawFrozenLayer = isVideoCardTransitionSnapshotDrawable(
            hasRecordedContent = snapshotState.hasRecordedContent,
            displayListStale = snapshotState.displayListStale,
        )
        if (!canDrawFrozenLayer) {
            // 唯一允许的清晰 live：冻结层不可用时（防黑）。
            drawContent()
            if (frame.scrimAlpha > 0.001f) {
                val scrimColor = if (frame.useLightScrimTint) {
                    VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
                } else {
                    Color.Black
                }
                drawRect(scrimColor.copy(alpha = frame.scrimAlpha))
            }
            return@drawWithContent
        }

        // 返回末段逐渐把画面交还给 live content。GraphicsLayer 冻结快照无法可靠
        // 捕获 PlayerView/TextureView 的实时像素，若它一直不透明地盖到 progress=0，
        // 上一级页面会在手势即将完成时仍发黑、发糊。交接区间与宽卡片的源内容
        // crossfade 保持一致，使实时页面与返回卡片同步显现。
        val liveHandoffActive = frozenLayerAlpha < 0.999f

        // 返回末段 / 预览中段：先画 live content 让 hazeSource 重新登记，
        // 否则顶栏/底栏 unifiedBlur 在预测返回后会一直空白，直到再次进详情 remount。
        val shouldPrimeHazeSources = shouldPrimeLiveContentForHazeDuringDepthDraw(
            exposure = activeExposure,
            depthProgress = activeProgress,
        )
        if (shouldPrimeHazeSources || liveHandoffActive) {
            if (needsRecord && reuseJustRecordedSourceForHandoff) {
                // 刚刚录制时来源及采样节点已执行过 draw。当前帧复用同一份清晰
                // 像素作交接底图，避免重录快照后再遍历一次实时页面；下一帧恢复 live。
                effectiveSnapshotHandle.drawJustRecordedSource(this)
            } else {
                drawContent()
            }
        }

        // 恒定半径 + 合成混合，与 Host 层同语义：模糊只光栅化一次，进度驱动 layerAlpha。
        val fixedBlurRadiusPx = if (frame.blurRadiusPx > 0.01f) {
            resolveVideoCardTransitionMaxBlurRadiusPx(activeMotionTier, density)
        } else {
            0f
        }
        applyVideoCardTransitionSnapshotFrame(
            contentLayer = contentLayer,
            snapshotState = snapshotState,
            frame = frame,
            canvasSize = size,
            sourceBounds = sourceBoundsProvider(),
            layerAlpha = frozenLayerAlpha * frame.blurAlpha,
            rasterScale = effectiveSnapshotHandle.prepareEffectLayer(this, fixedBlurRadiusPx),
            blurRadiusPxOverride = fixedBlurRadiusPx,
        )
        if (!liveHandoffActive && shouldDrawVideoCardTransitionScaleGapFill(frame.contentScale)) {
            drawRect(
                resolveVideoCardTransitionScaleGapFillColor(
                    isLightBackground = frame.useLightScrimTint,
                    scrimAlpha = frame.scrimAlpha,
                )
            )
        }
        drawLayer(contentLayer)
        VideoCardTransitionDiagnostics.onSourceLayerDrawn()

        if (frame.scrimAlpha > 0.001f) {
            val scrimColor = if (frame.useLightScrimTint) {
                VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
            } else {
                Color.Black
            }
            drawRect(scrimColor.copy(alpha = frame.scrimAlpha))
        }
    }.then(if (isolateLiveContent) Modifier.graphicsLayer() else Modifier)
}

/** Match both renderers' visibility boundary before recording or resolving a blur frame. */
internal fun shouldDrawVideoCardTransitionFrozenLayer(alpha: Float): Boolean = alpha > 0.001f

/**
 * Fades the frozen depth snapshot out over the full source-card takeover window.
 * Depth is 1 at full blur and 0 when the previous page is fully restored.
 *
 * Drawing live content underneath this layer is essential for player surfaces: recording an
 * Android view into a Compose [androidx.compose.ui.graphics.layer.GraphicsLayer] can yield a black
 * frame even though the actual surface is still rendering.
 */
internal fun resolveVideoCardTransitionFrozenLayerAlpha(
    exposure: VideoCardTransitionExposure,
    depthProgress: Float,
): Float {
    val supportsLiveHandoff = when (exposure) {
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring,
        -> true
        else -> false
    }
    if (!supportsLiveHandoff) return 1f
    // 冻结层往往录到了进场时被隐藏标题的卡片。在返回中段就把它交给
    // live source page，否则下方已就绪的标题/UP/统计仍会被无正文快照盖住。
    val handoff = resolveVideoCardWholeSourceReturnAlpha(depthProgress)
    // 只柔化背景所有权交接，不能二次缓动飞卡/手势的几何进度。
    // 窗口两端斜率归零，背景不在开始交接或卸层时突然改变清晰度。
    return 1f - handoff * handoff * (3f - 2f * handoff)
}

/**
 * When depth is nearly clear during back preview / return / restore, also paint live
 * content so [hazeSource] areas re-register. Frozen-only draws leave chrome blur empty.
 *
 * Progress is depth (1 = full blur held, 0 = clear). Prime when depth ≤ threshold.
 */
internal fun shouldPrimeLiveContentForHazeDuringDepthDraw(
    exposure: VideoCardTransitionExposure,
    depthProgress: Float,
    clearThreshold: Float = 0.35f,
): Boolean {
    return when (exposure) {
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring ->
            depthProgress.coerceIn(0f, 1f) <= clearThreshold
        else -> false
    }
}

/**
 * 进场/持有/返回：景深 progress 一律线性同源。
 *
 * 返回不做 soft-clear 二次映射——主驱动已应用曲线，再 remap 会让模糊层
 * 落后于壳落位。
 */
internal fun resolveVideoCardTransitionDepthProgress(
    progress: Float,
    phase: VideoCardTransitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
): Float {
    return resolveVisualProgress(progress, phase, VideoSharedTransitionDirection.ENTER)
}

private fun resolveVideoCardTransitionBlurStrength(progress: Float): Float {
    // 与景深进度同源：模糊与背景下沉同步建立/消退，避免“先糊后沉”的分层错位。
    return progress.coerceIn(0f, 1f)
}

/**
 * 开合景深峰值模糊半径（px）。
 * - Reduced（仅系统减弱动画）：0
 * - Normal / Enhanced：统一 **12dp × 密度**，**不按机型降级**。
 *   旧值固定 20px 在高密度手机上仅 ≈7dp（景深偏弱）、低密度平板上 ≈13dp（偏强），
 *   按 dp 标定后各密度观感一致。
 */
internal fun resolveVideoCardTransitionMaxBlurRadiusPx(
    motionTier: MotionTier,
    density: Float = 1f,
): Float {
    return when (motionTier) {
        MotionTier.Reduced -> 0f
        MotionTier.Normal,
        MotionTier.Enhanced ->
            VIDEO_CARD_TRANSITION_MAX_BLUR_RADIUS_DP * density.coerceAtLeast(0f)
    }
}

/**
 * Blur 半径量化步长。
 * 开合与手势恢复共用网格，保证同一深度的清晰度与方向无关。
 * 接近清晰时的细分由量化函数统一处理，Host、来源快照和实时层不得各自取整。
 */
internal fun resolveVideoCardTransitionBlurQuantumPx(
    motionTier: MotionTier,
    phase: VideoCardTransitionBackgroundPhase = VideoCardTransitionBackgroundPhase.IDLE,
): Float {
    @Suppress("UNUSED_PARAMETER")
    val ignored = motionTier
    return if (phase == VideoCardTransitionBackgroundPhase.RETURNING) {
        VIDEO_CARD_TRANSITION_RETURN_BLUR_QUANTUM_PX
    } else {
        VIDEO_CARD_TRANSITION_BLUR_QUANTUM_PX
    }
}

private fun quantizeVideoCardTransitionBlurRadius(
    radiusPx: Float,
    maxRadiusPx: Float,
    quantumPx: Float = VIDEO_CARD_TRANSITION_BLUR_QUANTUM_PX,
): Float {
    if (radiusPx <= 0f || maxRadiusPx <= 0f) return 0f
    // Preserve the full-strength endpoint at fractional display densities.
    if (radiusPx >= maxRadiusPx) return maxRadiusPx
    val step = if (radiusPx <= VideoHeroMotionTokens.CLEAR_BLUR_RANGE_PX) {
        VideoHeroMotionTokens.CLEAR_BLUR_QUANTUM_PX
    } else {
        quantumPx.coerceAtLeast(VideoHeroMotionTokens.CLEAR_BLUR_QUANTUM_PX)
    }
    return ((radiusPx / step).roundToInt() * step).coerceIn(0f, maxRadiusPx)
}

private fun normalizeVideoCardTransitionRoute(route: String?): String? {
    val normalized = route?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (normalized.startsWith("home?category=")) {
        "home"
    } else {
        normalized.substringBefore("?")
    }
}
