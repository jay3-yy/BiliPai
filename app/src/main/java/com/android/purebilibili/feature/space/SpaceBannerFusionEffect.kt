package com.android.purebilibili.feature.space

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.feature.profile.ProfileAvatarFusionView
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics

// The phases overlap so velocity stays continuous while the photograph becomes a droplet.
private fun spaceBannerSmoothProgress(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private data class SpaceBannerMorph(
    val width: Float,
    val height: Float,
    val roundness: Float,
    val fusion: Float,
)

private fun spaceBannerMorph(
    progress: Float,
    width: Float,
    height: Float,
    droplet: Float,
    endSize: Float,
): SpaceBannerMorph {
    val shape = spaceBannerSmoothProgress(progress / 0.7f)
    val fusion = spaceBannerSmoothProgress((progress - 0.5f) / 0.5f)
    val shapedWidth = width + (droplet - width) * shape
    val shapedHeight = height + (droplet - height) * shape
    return SpaceBannerMorph(
        width = shapedWidth + (endSize - shapedWidth) * fusion,
        height = shapedHeight + (endSize - shapedHeight) * fusion,
        roundness = shape,
        fusion = fusion,
    )
}

/** Reuse the profile avatar's cutout fusion while retaining the banner's pager composition. */
@Composable
internal fun SpaceBannerFusionEffect(
    collapseFractionProvider: () -> Float,
    scrollOffsetPxProvider: () -> Float,
    maxParallaxTranslationPx: Float,
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    fusionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!fusionEnabled) {
        Box(modifier = modifier) { content() }
        return
    }
    val parentComposition = rememberCompositionContext()
    val dropletSizePx = with(LocalDensity.current) { 72f * density }
    val latestDropletSize = rememberUpdatedState(dropletSizePx)
    val latestContent = rememberUpdatedState(content)
    val latestScrollState = rememberUpdatedState(scrollState)
    val latestProgress = rememberUpdatedState(collapseFractionProvider)
    val latestScrollOffset = rememberUpdatedState(scrollOffsetPxProvider)
    val latestParallaxLimit = rememberUpdatedState(maxParallaxTranslationPx)
    val lowBudget = isLowBlurBudgetForced()
    val latestLowBudget = rememberUpdatedState(lowBudget)
    // 导航宿主已监听系统动效设置。列表挂载头图时直接共享该状态。
    val enabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current && !lowBudget
    val latestEnabled = rememberUpdatedState(enabled)
    val host = remember { arrayOfNulls<ProfileAvatarFusionView>(1) }
    // Refresh the native display list even when Compose reuses its cached draw layer,
    // or the fully absorbed view has no visible child to invalidate it.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(latestProgress.value(), latestEnabled.value, latestLowBudget.value) }
            .collect { host[0]?.prepareFrame() }
    }
    AndroidView(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                host[0]?.setViewportOrigin(origin.x, origin.y)
            }
            .drawWithContent {
                latestProgress.value()
                host[0]?.prepareFrame()
                drawContent()
            },
        factory = { context ->
            ProfileAvatarFusionView(context, !lowBudget).apply {
                host[0] = this
                setRectangularContent(true)
                val banner = ComposeView(context).apply {
                    setParentCompositionContext(parentComposition)
                    pivotX = 0f
                    pivotY = 0f
                    setContent {
                        val clip = remember { Path() }
                        Box(
                            Modifier.appScrollBoundaryHaptics(latestScrollState.value)
                                .scrollable(latestScrollState.value, Orientation.Vertical, reverseDirection = true)
                                .drawWithContent {
                                    val progress = if (latestEnabled.value) latestProgress.value().coerceIn(0f, 1f) else 0f
                                    val cutout = host[0]?.notchInfo
                                    val endSize = if (cutout != null) minOf(cutout.bounds.width(), cutout.bounds.height()) * 0.5f
                                        else 24f * context.resources.displayMetrics.density
                                    val morph = spaceBannerMorph(
                                        progress, size.width, size.height,
                                        latestDropletSize.value.coerceAtMost(size.height), endSize,
                                    )
                                    val sx = (morph.width / size.width.coerceAtLeast(1f)).coerceAtLeast(0.001f)
                                    val sy = (morph.height / size.height.coerceAtLeast(1f)).coerceAtLeast(0.001f)
                                    val imageScale = maxOf(sx, sy)
                                    // Equal radii in the displayed image; undo native scaling for the clip path.
                                    val radius = minOf(morph.width, morph.height) * morph.roundness / 2f
                                    clip.reset()
                                    clip.addRoundRect(RoundRect(
                                        0f, 0f, size.width, size.height,
                                        CornerRadius(radius / sx, radius / sy),
                                    ))
                                    clipPath(clip) {
                                        // Compensate the host's independent X/Y scales: crop, never stretch the photo.
                                        withTransform({ scale(imageScale / sx, imageScale / sy, center) }) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                                },
                        ) { latestContent.value() }
                    }
                }
                addView(banner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                setFrameUpdater {
                    setHardwareEffectsEnabled(!latestLowBudget.value)
                    val progress = latestProgress.value().coerceIn(0f, 1f)
                    banner.visibility = if (latestEnabled.value && progress >= 1f) View.INVISIBLE else View.VISIBLE
                    if (latestEnabled.value && width > 0 && height > 0) {
                        val cutout = notchInfo
                        val deviceDensity = context.resources.displayMetrics.density
                        val endSize = if (cutout != null) minOf(cutout.bounds.width(), cutout.bounds.height()) * 0.5f
                            else 24f * deviceDensity
                        val holeCenterX = cutout?.bounds?.centerX() ?: (width / 2f)
                        val holeWindowCenterY = cutout?.bounds?.centerY()?.plus(getViewportTop()) ?: -12f * deviceDensity
                        val holeWindowBottom = cutout?.bounds?.bottom?.plus(getViewportTop()) ?: 0f
                        // Undo the grid scroll and the parent's parallax in window coordinates.
                        // This also handles a header recreated mid-scroll or a resized window.
                        val offset = latestScrollOffset.value().coerceIn(0f, height.toFloat())
                        val parallax = (offset * 0.75f).coerceIn(0f, latestParallaxLimit.value)
                        val expandedWindowTop = getViewportTop() + offset - parallax
                        val droplet = latestDropletSize.value.coerceAtMost(height.toFloat())
                        val morph = spaceBannerMorph(progress, width.toFloat(), height.toFloat(), droplet, endSize)
                        val shapeProgress = morph.roundness
                        val fusion = morph.fusion
                        val drawnWidth = morph.width
                        val drawnHeight = morph.height
                        val startCenterY = expandedWindowTop + height / 2f
                        val dropletCenterY = holeWindowBottom + droplet / 2f + 12f * deviceDensity
                        val shapedCenterY = startCenterY + (dropletCenterY - startCenterY) * shapeProgress
                        val windowCenterY = shapedCenterY + (holeWindowCenterY - shapedCenterY) * fusion
                        val centerX = width / 2f + (holeCenterX - width / 2f) * shapeProgress
                        banner.scaleX = drawnWidth / width
                        banner.scaleY = drawnHeight / height
                        banner.translationX = centerX - drawnWidth / 2f
                        banner.translationY = windowCenterY - getViewportTop() - drawnHeight / 2f
                        updateFrame(fusion, progress, true)
                    } else {
                        banner.scaleX = 1f
                        banner.scaleY = 1f
                        banner.translationX = 0f
                        banner.translationY = 0f
                        updateFrame(0f, false)
                    }
                }
            }
        },
        onRelease = { view ->
            (view.getChildAt(0) as? ComposeView)?.disposeComposition()
            view.release()
            if (host[0] === view) host[0] = null
        },
    )
}
