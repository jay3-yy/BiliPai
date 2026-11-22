package com.android.purebilibili.feature.dynamic.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

private const val LAYOUT_PROGRESS_MIN = 0f
private const val LAYOUT_PROGRESS_MAX = 1f
private const val FALLBACK_START_SCALE = 0.96f
private const val IMAGE_PREVIEW_OPEN_DURATION_MS = 260
private const val IMAGE_PREVIEW_CANCEL_RECOVER_DURATION_MS = 180
private const val IMAGE_PREVIEW_VERTICAL_DISMISS_FRACTION = 0.18f
private const val IMAGE_PREVIEW_BLUR_QUANTUM_PX = 2f

internal data class ImagePreviewTransitionFrame(
    val layoutProgress: Float,
    val visualProgress: Float,
    val cornerRadiusDp: Float,
    val fallbackScale: Float
)

internal data class ImagePreviewVisualFrame(
    val contentAlpha: Float,
    val backdropAlpha: Float,
    val blurRadiusPx: Float
)

internal data class ImagePreviewDismissMotion(
    /** 关闭落点：一镜到底直落到 0，不再 overshoot。 */
    val overshootTarget: Float,
    val settleTarget: Float,
    /** 预测返回取消后的回弹时长。 */
    val cancelRecoverDurationMillis: Int,
    /** 打开时长；关闭使用 spring，由位移和速度决定收敛时间。 */
    val openDurationMillis: Int
)

internal data class ImagePreviewDismissTransform(
    val scale: Float,
    val translationXPx: Float,
    val translationYPx: Float
)

internal data class ImagePreviewDismissRectFrame(
    val rect: Rect,
    val dismissFraction: Float
)

internal data class ImagePreviewVerticalDragFrame(
    val progress: Float,
    val scale: Float,
    val backdropAlphaMultiplier: Float
)

internal enum class ImagePreviewVerticalDismissDecision {
    DISMISS,
    SNAP_BACK
}

enum class ImagePreviewTextPlacement {
    OVERLAY_BOTTOM,
    TOP_BAR
}

data class ImagePreviewTextContent(
    val headline: String = "",
    val body: String = "",
    val perImageCaptions: List<String> = emptyList(),
    val placement: ImagePreviewTextPlacement = ImagePreviewTextPlacement.OVERLAY_BOTTOM,
    val commentContext: ImagePreviewCommentContext? = null
)

data class ImagePreviewCommentContext(
    val replyId: Long = 0L,
    val authorName: String = "",
    val avatarUrl: String = "",
    val timeText: String = "",
    val body: String = "",
    val originalSizeLabels: List<String> = emptyList(),
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val onLikeClick: (() -> Unit)? = null,
    val onReplyClick: (() -> Unit)? = null
)

internal data class ImagePreviewResolvedText(
    val headline: String,
    val body: String,
    val pageIndicator: String
)

internal data class ImagePreviewTextTransform(
    val rotationX: Float,
    val alpha: Float,
    val translateYDp: Float
)

internal data class CommentImagePreviewPageTransform(
    val rotationY: Float,
    val pivotFractionX: Float,
    val translationXPx: Float,
    val scale: Float,
    val alpha: Float
)

internal data class ImagePreviewGalleryPageTransform(
    val rotationY: Float,
    val pivotFractionX: Float,
    val translationXPx: Float,
    val scale: Float,
    val alpha: Float
)

internal data class ImagePreviewOverlayPadding(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp
)

internal fun resolveImagePreviewTransitionFrame(
    rawProgress: Float,
    hasSourceRect: Boolean,
    sourceCornerRadiusDp: Float
): ImagePreviewTransitionFrame {
    val layoutProgress = rawProgress.coerceIn(LAYOUT_PROGRESS_MIN, LAYOUT_PROGRESS_MAX)
    val visualProgress = rawProgress.coerceIn(0f, 1f)
    val cornerRadiusDp = resolveImagePreviewPresentedCornerRadiusDp(
        visualProgress = visualProgress,
        verticalDragProgress = 0f,
        hasSourceRect = hasSourceRect,
        sourceCornerRadiusDp = sourceCornerRadiusDp
    )
    val fallbackScale = lerpFloat(FALLBACK_START_SCALE, 1f, visualProgress)
    return ImagePreviewTransitionFrame(
        layoutProgress = layoutProgress,
        visualProgress = visualProgress,
        cornerRadiusDp = cornerRadiusDp,
        fallbackScale = fallbackScale
    )
}

/**
 * 全屏打开时圆角为 0；返回/竖滑退出时插值到缩略图圆角，贴回格子更自然。
 */
internal fun resolveImagePreviewPresentedCornerRadiusDp(
    visualProgress: Float,
    verticalDragProgress: Float,
    hasSourceRect: Boolean,
    sourceCornerRadiusDp: Float,
    openCornerRadiusDp: Float = 0f
): Float {
    if (!hasSourceRect) return openCornerRadiusDp.coerceAtLeast(0f)
    val source = sourceCornerRadiusDp.coerceAtLeast(0f)
    val open = openCornerRadiusDp.coerceAtLeast(0f)
    val morphCorner = lerpFloat(source, open, visualProgress.coerceIn(0f, 1f))
    val dragCorner = lerpFloat(open, source, verticalDragProgress.coerceIn(0f, 1f))
    return maxOf(morphCorner, dragCorner)
}

internal fun resolveImagePreviewVisualFrame(
    visualProgress: Float,
    transitionEnabled: Boolean,
    maxBlurRadiusPx: Float,
    blurEnabled: Boolean = true,
): ImagePreviewVisualFrame {
    val progress = visualProgress.coerceIn(0f, 1f)
    if (!transitionEnabled) {
        return ImagePreviewVisualFrame(
            contentAlpha = 1f,
            backdropAlpha = progress,
            blurRadiusPx = 0f
        )
    }

    return ImagePreviewVisualFrame(
        // Match PiliPlus Hero behavior: only the route backdrop fades; the shared image
        // stays fully opaque throughout the flight, avoiding the initial dark flash.
        contentAlpha = 1f,
        backdropAlpha = progress,
        blurRadiusPx = if (blurEnabled) {
            resolveImagePreviewBlurRadiusPx(
                visualProgress = progress,
                maxBlurRadiusPx = maxBlurRadiusPx,
            )
        } else {
            0f
        }
    )
}

/** Keep a smooth ease-out landing while shortening the route response. */
internal fun imagePreviewOpenTween(): TweenSpec<Float> =
    tween(
        durationMillis = IMAGE_PREVIEW_OPEN_DURATION_MS,
        easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.StandardEaseOut,
    )

/**
 * 关闭回位用临界阻尼 spring 而非 easeIn tween：easeIn 在 t=0 斜率为 0，
 * 松手后画面会先"停一下"再窜出，且末端速度最大造成硬着陆。
 * 临界阻尼 spring 起步即可携带手势速度，落地自带减速，与评论区下拉关闭的手感一致。
 */
internal fun imagePreviewCloseSpring(): SpringSpec<Float> =
    spring(dampingRatio = 1f, stiffness = 600f)

/** 关闭回位允许携带的手势速度上限（px/s），避免极端快挥把画面甩过落点方向。 */
internal fun clampImagePreviewDismissVelocity(velocityY: Float): Float =
    velocityY.coerceIn(-3000f, 3000f)

/** Project px/s onto the return path, then convert to progress/s. Never reverse the exit. */
internal fun resolveImagePreviewDismissProgressVelocity(
    velocityY: Float,
    startRect: Rect?,
    targetRect: Rect?,
    containerHeightPx: Float,
    startProgress: Float = 1f,
): Float {
    val velocity = clampImagePreviewDismissVelocity(velocityY)
    val progress = startProgress.coerceIn(0f, 1f)
    val projectedVelocity = if (startRect != null && targetRect != null) {
        val dx = targetRect.center.x - startRect.center.x
        val dy = targetRect.center.y - startRect.center.y
        -velocity * dy / (dx * dx + dy * dy).coerceAtLeast(120f * 120f)
    } else {
        -kotlin.math.abs(velocity) / containerHeightPx.coerceAtLeast(120f)
    }
    // Below the critically damped spring's natural frequency: no overshoot past the source.
    return (projectedVelocity * progress).coerceIn(-12f * progress, 0f)
}

internal fun resolveImagePreviewDismissCornerRadiusDp(
    remainingProgress: Float,
    startCornerRadiusDp: Float,
    targetCornerRadiusDp: Float,
): Float = lerpFloat(
    targetCornerRadiusDp.coerceAtLeast(0f),
    startCornerRadiusDp.coerceAtLeast(0f),
    remainingProgress.coerceIn(0f, 1f),
)

/**
 * 模糊随回位进度线性爬升。此前用 returnProgress² 会让模糊集中在关闭后半段
 * 突然涌出，叠加量化步进呈现"跳级"感；线性曲线整段均匀，步进仍用于防抖动。
 */
internal fun resolveImagePreviewBlurRadiusPx(
    visualProgress: Float,
    maxBlurRadiusPx: Float,
): Float {
    val returnProgress = 1f - visualProgress.coerceIn(0f, 1f)
    val maxRadius = maxBlurRadiusPx.coerceAtLeast(0f)
    val linearRadius = maxRadius * returnProgress
    return ((linearRadius / IMAGE_PREVIEW_BLUR_QUANTUM_PX).roundToInt() *
        IMAGE_PREVIEW_BLUR_QUANTUM_PX).coerceIn(0f, maxRadius)
}

internal fun imagePreviewDismissMotion(): ImagePreviewDismissMotion {
    return ImagePreviewDismissMotion(
        // 一镜到底：单段连续 morph 到缩略图，不做 overshoot + spring 二次落点。
        overshootTarget = 0f,
        settleTarget = 0f,
        cancelRecoverDurationMillis = IMAGE_PREVIEW_CANCEL_RECOVER_DURATION_MS,
        openDurationMillis = IMAGE_PREVIEW_OPEN_DURATION_MS
    )
}

internal fun resolveImagePreviewDismissTransform(
    transitionProgress: Float,
    sourceRect: Rect?,
    displayedImageRect: Rect?
): ImagePreviewDismissTransform {
    if (sourceRect == null || displayedImageRect == null) {
        return ImagePreviewDismissTransform(
            scale = 1f,
            translationXPx = 0f,
            translationYPx = 0f
        )
    }

    // 几何插值保持线性；速度曲线只交给 Animatable 的 Continuity easing。
    val dismissFraction = resolveImagePreviewDismissFraction(transitionProgress)
    val targetScale = min(
        sourceRect.width / displayedImageRect.width,
        sourceRect.height / displayedImageRect.height
    ).coerceIn(0f, 1f)
    val containerCenterX = (displayedImageRect.left + displayedImageRect.right) / 2f
    val containerCenterY = (displayedImageRect.top + displayedImageRect.bottom) / 2f
    val sourceCenterX = (sourceRect.left + sourceRect.right) / 2f
    val sourceCenterY = (sourceRect.top + sourceRect.bottom) / 2f
    val targetTranslationX = sourceCenterX - containerCenterX
    val targetTranslationY = sourceCenterY - containerCenterY

    return ImagePreviewDismissTransform(
        scale = lerpFloat(1f, targetScale, dismissFraction).coerceAtLeast(0.01f),
        translationXPx = lerpFloat(0f, targetTranslationX, dismissFraction),
        translationYPx = lerpFloat(0f, targetTranslationY, dismissFraction)
    )
}

internal fun resolveImagePreviewDismissRectFrame(
    transitionProgress: Float,
    sourceRect: Rect?,
    displayedImageRect: Rect?
): ImagePreviewDismissRectFrame? {
    if (sourceRect == null || displayedImageRect == null) return null

    val dismissFraction = resolveImagePreviewDismissFraction(transitionProgress)
    val displayedCenterX = (displayedImageRect.left + displayedImageRect.right) / 2f
    val displayedCenterY = (displayedImageRect.top + displayedImageRect.bottom) / 2f
    val sourceCenterX = (sourceRect.left + sourceRect.right) / 2f
    val sourceCenterY = (sourceRect.top + sourceRect.bottom) / 2f
    val width = lerpFloat(displayedImageRect.width, sourceRect.width, dismissFraction)
    val height = lerpFloat(displayedImageRect.height, sourceRect.height, dismissFraction)
    val centerX = lerpFloat(displayedCenterX, sourceCenterX, dismissFraction)
    val centerY = lerpFloat(displayedCenterY, sourceCenterY, dismissFraction)

    return ImagePreviewDismissRectFrame(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        ),
        dismissFraction = dismissFraction
    )
}

/** Source thumbnail to the full preview surface, preserving a rect flight for image clipping. */
internal fun resolveImagePreviewOpenRect(
    transitionProgress: Float,
    sourceRect: Rect?,
    previewSurfaceRect: Rect?
): Rect? {
    if (sourceRect == null || previewSurfaceRect == null) return null
    val progress = transitionProgress.coerceIn(0f, 1f)
    return Rect(
        left = lerpFloat(sourceRect.left, previewSurfaceRect.left, progress),
        top = lerpFloat(sourceRect.top, previewSurfaceRect.top, progress),
        right = lerpFloat(sourceRect.right, previewSurfaceRect.right, progress),
        bottom = lerpFloat(sourceRect.bottom, previewSurfaceRect.bottom, progress)
    )
}

internal fun resolveImagePreviewDismissStartRect(
    previewSurfaceRect: Rect?,
    displayedImageRect: Rect?,
    preferPreviewSurface: Boolean
): Rect? {
    return if (preferPreviewSurface) {
        previewSurfaceRect ?: displayedImageRect
    } else {
        displayedImageRect ?: previewSurfaceRect
    }
}

internal fun resolveImagePreviewOverlayPadding(
    safeInsetStart: Dp,
    safeInsetTop: Dp,
    safeInsetEnd: Dp,
    safeInsetBottom: Dp,
    extraHorizontal: Dp = 16.dp,
    extraVertical: Dp = 16.dp
): ImagePreviewOverlayPadding {
    val resolvedHorizontal = extraHorizontal.coerceAtLeast(0.dp)
    val resolvedVertical = extraVertical.coerceAtLeast(0.dp)
    return ImagePreviewOverlayPadding(
        start = safeInsetStart.coerceAtLeast(0.dp) + resolvedHorizontal,
        top = safeInsetTop.coerceAtLeast(0.dp) + resolvedVertical,
        end = safeInsetEnd.coerceAtLeast(0.dp) + resolvedHorizontal,
        bottom = safeInsetBottom.coerceAtLeast(0.dp) + resolvedVertical
    )
}

internal fun resolveImagePreviewDraggedDisplayRect(
    displayedImageRect: Rect?,
    translationYPx: Float,
    scale: Float,
    translationXPx: Float = 0f
): Rect? {
    if (displayedImageRect == null) return null
    val safeScale = scale.coerceAtLeast(0.01f)
    val width = displayedImageRect.width * safeScale
    val height = displayedImageRect.height * safeScale
    val centerX = (displayedImageRect.left + displayedImageRect.right) / 2f + translationXPx
    val centerY = (displayedImageRect.top + displayedImageRect.bottom) / 2f + translationYPx
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

internal fun shouldEnableImagePreviewVerticalDismiss(zoomScale: Float): Boolean {
    return zoomScale <= 1.01f
}

internal fun resolveImagePreviewVerticalDragFrame(
    dragOffsetYPx: Float,
    containerHeightPx: Float
): ImagePreviewVerticalDragFrame {
    val denominator = (containerHeightPx.coerceAtLeast(1f) * 0.28f).coerceAtLeast(120f)
    val progress = (kotlin.math.abs(dragOffsetYPx) / denominator).coerceIn(0f, 1f)
    return ImagePreviewVerticalDragFrame(
        progress = progress,
        scale = lerpFloat(1f, 0.9f, progress),
        backdropAlphaMultiplier = lerpFloat(1f, 0.18f, progress)
    )
}

internal fun resolveImagePreviewVerticalDismissDecision(
    dragOffsetYPx: Float,
    containerHeightPx: Float
): ImagePreviewVerticalDismissDecision {
    val threshold = maxOf(
        120f,
        containerHeightPx.coerceAtLeast(1f) * IMAGE_PREVIEW_VERTICAL_DISMISS_FRACTION
    )
    return if (kotlin.math.abs(dragOffsetYPx) >= threshold) {
        ImagePreviewVerticalDismissDecision.DISMISS
    } else {
        ImagePreviewVerticalDismissDecision.SNAP_BACK
    }
}

internal fun resolveImagePreviewDismissBackdropAlpha(
    visualProgress: Float,
    startAlpha: Float = 1f,
): Float {
    // 保留下拉结束时的遮罩透明度，再与回位 morph 同步淡出，避免门槛处闪黑。
    return startAlpha.coerceIn(0f, 1f) * visualProgress.coerceIn(0f, 1f)
}

/**
 * Chrome（顶栏/评论条）比图片 morph 更早淡出，避免控件跟着缩变形。
 */
/**
 * Rect 飞行路径的图片全程不透明、自己飞回缩略图，避免初始暗闪；
 * fallback 关闭（无 sourceRect）没有落点，图片必须与遮罩同步淡出，
 * 否则窗口移除瞬间全屏图凭空消失，产生一次闪切。
 */
internal fun resolveImagePreviewDismissContentAlpha(
    hasRectFlight: Boolean,
    isDismissing: Boolean,
    visualProgress: Float
): Float {
    if (hasRectFlight || !isDismissing) return 1f
    return visualProgress.coerceIn(0f, 1f)
}

/**
 * Chrome（顶栏/评论条）比图片 morph 更早淡出，避免控件跟着缩变形。
 * 淡出窗口 [0.05, 0.6]：与背景/图片的节奏错位比旧的 [0.35, 1] 更小，
 * 又仍保证后半段只剩干净的图片飞回。
 */
internal fun resolveImagePreviewChromeAlpha(
    visualProgress: Float,
    isDismissing: Boolean
): Float {
    val progress = visualProgress.coerceIn(0f, 1f)
    if (!isDismissing) return progress
    return ((progress - 0.05f) / 0.55f).coerceIn(0f, 1f)
}

/**
 * 圆角在非均匀缩放图层上会被拉伸成椭圆。按轴分别反除缩放，
 * 屏幕上的圆角在飞行全程保持正圆。
 */
internal data class ImagePreviewCounterScaledCornerRadii(
    val horizontalDp: Float,
    val verticalDp: Float
)

internal fun resolveImagePreviewCounterScaledCornerRadii(
    cornerRadiusDp: Float,
    scaleX: Float,
    scaleY: Float
): ImagePreviewCounterScaledCornerRadii {
    val radius = cornerRadiusDp.coerceAtLeast(0f)
    val cap = radius * 64f
    return ImagePreviewCounterScaledCornerRadii(
        horizontalDp = (radius / scaleX.coerceAtLeast(0.01f)).coerceAtMost(cap),
        verticalDp = (radius / scaleY.coerceAtLeast(0.01f)).coerceAtMost(cap)
    )
}

/** 3D 翻页强度随进度在 [0.85, 1] 平滑进入，替代硬阈值开关造成的尾段跳变。 */
internal fun resolveImagePreviewGallery3DBlend(transitionProgress: Float): Float =
    ((transitionProgress.coerceIn(0f, 1f) - 0.85f) / 0.15f).coerceIn(0f, 1f)

/** 实况照片在 [0.7, 1] 随进度淡入，替代 0.85 处的整帧弹入。 */
internal fun resolveImagePreviewLivePhotoAlpha(visualProgress: Float): Float =
    ((visualProgress.coerceIn(0f, 1f) - 0.7f) / 0.3f).coerceIn(0f, 1f)

internal fun resolveImagePreviewText(
    textContent: ImagePreviewTextContent?,
    currentPage: Int,
    totalPages: Int
): ImagePreviewResolvedText? {
    if (textContent == null) return null
    val headline = textContent.headline.trim().take(48)
    val pageCaption = textContent.perImageCaptions
        .getOrNull(currentPage)
        ?.trim()
        .orEmpty()
    val body = if (pageCaption.isNotEmpty()) {
        pageCaption.take(150)
    } else {
        textContent.body.trim().take(150)
    }
    val indicator = if (totalPages > 1 && currentPage >= 0) {
        "${currentPage + 1} / $totalPages"
    } else {
        ""
    }
    if (headline.isEmpty() && body.isEmpty() && indicator.isEmpty()) return null
    return ImagePreviewResolvedText(
        headline = headline,
        body = body,
        pageIndicator = indicator
    )
}

internal fun resolveImagePreviewTextTransform(pageOffsetFraction: Float): ImagePreviewTextTransform {
    val clampedOffset = pageOffsetFraction.coerceIn(-1f, 1f)
    val absOffset = kotlin.math.abs(clampedOffset)
    return ImagePreviewTextTransform(
        rotationX = absOffset * 22f,
        alpha = (1f - absOffset * 0.38f).coerceIn(0.62f, 1f),
        translateYDp = absOffset * 8f
    )
}

internal fun shouldShowImagePreviewText(
    hasText: Boolean,
    textVisible: Boolean
): Boolean = hasText && textVisible

internal fun resolveImagePreviewInitialTextVisibility(
    hasText: Boolean,
    defaultVisible: Boolean
): Boolean = hasText && defaultVisible

internal fun resolveImagePreviewTextVisibilityAfterToggle(currentVisible: Boolean): Boolean {
    return !currentVisible
}

internal fun resolveCommentImagePreviewPageTransform(
    pageOffsetFraction: Float,
    containerWidthPx: Float
): CommentImagePreviewPageTransform {
    val clampedOffset = pageOffsetFraction.coerceIn(-1f, 1f)
    val absOffset = kotlin.math.abs(clampedOffset)
    val safeWidth = containerWidthPx.coerceAtLeast(1f)
    val pivot = when {
        clampedOffset > 0.001f -> 1f
        clampedOffset < -0.001f -> 0f
        else -> 0.5f
    }
    return CommentImagePreviewPageTransform(
        rotationY = -clampedOffset * 72f,
        pivotFractionX = pivot,
        translationXPx = -clampedOffset * safeWidth * 0.23f,
        scale = lerpFloat(1f, 0.86f, absOffset),
        alpha = lerpFloat(1f, 0.76f, absOffset)
    )
}

/**
 * 普通画廊的可选 3D 翻页。角度和位移比评论面板更轻，减少边缘拉伸与切页时的黑缝。
 */
internal fun resolveImagePreviewGalleryPageTransform(
    pageOffsetFraction: Float,
    containerWidthPx: Float
): ImagePreviewGalleryPageTransform {
    val clampedOffset = pageOffsetFraction.coerceIn(-1f, 1f)
    val absOffset = kotlin.math.abs(clampedOffset)
    val pivot = when {
        clampedOffset > 0.001f -> 1f
        clampedOffset < -0.001f -> 0f
        else -> 0.5f
    }
    return ImagePreviewGalleryPageTransform(
        rotationY = -clampedOffset * 48f,
        pivotFractionX = pivot,
        translationXPx = -clampedOffset * containerWidthPx.coerceAtLeast(1f) * 0.08f,
        scale = lerpFloat(1f, 0.94f, absOffset),
        alpha = lerpFloat(1f, 0.84f, absOffset)
    )
}

internal fun resolveCommentImageOriginalSizeLabel(sizeKb: Float?): String {
    val safeSize = sizeKb?.takeIf { it > 0f } ?: return "查看原图"
    return if (safeSize >= 1024f) {
        "查看原图 (${String.format(java.util.Locale.US, "%.1f", safeSize / 1024f)}M)"
    } else {
        "查看原图 (${safeSize.toInt()}K)"
    }
}

internal fun shouldHandleImagePreviewLongPressSave(
    longPressSaveEnabled: Boolean,
    imageUrl: String,
    isSaving: Boolean
): Boolean {
    return longPressSaveEnabled && imageUrl.isNotBlank() && !isSaving
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/**
 * 进度 1 = 全屏打开，0 = 落回缩略图。
 * 几何插值线性，避免与 Animatable easing 叠加重映射导致末段发黏。
 */
private fun resolveImagePreviewDismissFraction(transitionProgress: Float): Float {
    return (1f - transitionProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}
