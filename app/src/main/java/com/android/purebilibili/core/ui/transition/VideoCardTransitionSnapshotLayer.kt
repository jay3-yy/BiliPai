package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier

/**
 * 每帧内多次读取同一 frame 时，用 (progress, phase, …) 缓存避免重复纯函数计算。
 */
internal class VideoCardTransitionBackgroundFrameCache {
    private var lastProgress = Float.NaN
    private var lastMaskProgress = Float.NaN
    private var lastPhase: VideoCardTransitionBackgroundPhase? = null
    private var lastMotionTier: MotionTier? = null
    private var lastIsLightBackground: Boolean? = null
    private var lastGestureRestoreInProgress: Boolean? = null
    private var lastDensity = Float.NaN
    private var lastDeviceCornerRadiusPx = Float.NaN
    private var lastScaleReduction = Float.NaN
    private var cached = VideoCardTransitionBackgroundFrame(
        blurRadiusPx = 0f,
        blurAlpha = 0f,
        scrimAlpha = 0f,
        contentScale = 1f,
    )

    fun resolve(
        progress: Float,
        phase: VideoCardTransitionBackgroundPhase,
        motionTier: MotionTier,
        isLightBackground: Boolean,
        isGestureRestoreInProgress: Boolean,
        density: Float,
        deviceCornerRadiusPx: Float,
        scaleReduction: Float,
        maskProgress: Float = progress,
    ): VideoCardTransitionBackgroundFrame {
        if (
            progress != lastProgress ||
            maskProgress != lastMaskProgress ||
            phase != lastPhase ||
            motionTier != lastMotionTier ||
            isLightBackground != lastIsLightBackground ||
            isGestureRestoreInProgress != lastGestureRestoreInProgress ||
            density != lastDensity ||
            deviceCornerRadiusPx != lastDeviceCornerRadiusPx ||
            scaleReduction != lastScaleReduction
        ) {
            lastProgress = progress
            lastMaskProgress = maskProgress
            lastPhase = phase
            lastMotionTier = motionTier
            lastIsLightBackground = isLightBackground
            lastGestureRestoreInProgress = isGestureRestoreInProgress
            lastDensity = density
            lastDeviceCornerRadiusPx = deviceCornerRadiusPx
            lastScaleReduction = scaleReduction
            cached = resolveVideoCardTransitionBackgroundFrame(
                progress = progress,
                phase = phase,
                motionTier = motionTier,
                isLightBackground = isLightBackground,
                isGestureRestoreInProgress = isGestureRestoreInProgress,
                density = density,
                deviceCornerRadiusPx = deviceCornerRadiusPx,
                scaleReduction = scaleReduction,
                maskProgress = maskProgress,
            )
        }
        return cached
    }
}

/** Bounded per-owner cache so reversing a gesture can reuse previously visited blur radii. */
internal class VideoCardTransitionBlurEffectCache {
    private val effects = LinkedHashMap<Float, BlurEffect>()

    fun resolve(radiusPx: Float, sdkInt: Int = Build.VERSION.SDK_INT): BlurEffect? {
        if (radiusPx <= 0.01f || !radiusPx.isFinite() || sdkInt < 31) return null
        return effects.getOrPut(radiusPx) {
            if (effects.size >= 256) effects.remove(effects.keys.first())
            BlurEffect(radiusX = radiusPx, radiusY = radiusPx, edgeTreatment = TileMode.Clamp)
        }
    }

    fun clear() = effects.clear()
}

/**
 * 冻结层状态：开场首帧 record 后停止重录 feed，只对静态 display list
 * 更新 scale / BlurEffect / scrim，实现「看起来实时的动态模糊」与稳帧共存。
 */
internal class VideoCardTransitionSnapshotLayerState {
    // Lifecycle state, updated by effects rather than by per-frame draw callbacks.
    private val sourceRenderers = mutableStateMapOf<Any, Boolean>()
    val hasReadySourceRenderer: Boolean get() = sourceRenderers.values.any { it }
    val hasAttachedSourceRenderer: Boolean get() = sourceRenderers.isNotEmpty()

    fun attachSourceRenderer(token: Any) { sourceRenderers[token] = false }
    fun detachSourceRenderer(token: Any) { sourceRenderers.remove(token) }
    fun markSourceRendererReady(token: Any, ready: Boolean) {
        if (token in sourceRenderers) sourceRenderers[token] = ready
    }

    // Reused by both source and host renderers for the lifetime of the captured display list.
    private val blurEffects = VideoCardTransitionBlurEffectCache()
    fun blurEffect(radiusPx: Float, sdkInt: Int = Build.VERSION.SDK_INT): BlurEffect? =
        blurEffects.resolve(radiusPx, sdkInt)
    val frameCache = VideoCardTransitionBackgroundFrameCache()
    var freezeRecording: Boolean = false
    var hasRecordedContent: Boolean = false
    /**
     * Host 共享层在来源 Scene dispose 后 display list 往往已失效（黑/空），
     * 但 [hasRecordedContent] 仍为 true。返回/预测时必须重录真实首页，禁止再 draw 空层。
     */
    var displayListStale: Boolean = false
    /**
     * 源页 dispose 后置 true：下一次源页挂上 BackPreview/Returning 时强制重录真实首页。
     * 与 [displayListStale] 不同：预测返回可暂用 Host 的冻结层作为首帧景深，随后由来源页
     * 重录真实内容；普通 pop 仍等待来源页刷新以避免黑帧。
     */
    var needsSourceRefresh: Boolean = false
    var lastBlurRadiusPx: Float = Float.NaN
    var lastCornerRadiusPx: Float = Float.NaN

    fun invalidateRecordedContent() {
        blurEffects.clear()
        freezeRecording = false
        hasRecordedContent = false
        displayListStale = false
        needsSourceRefresh = false
        lastBlurRadiusPx = Float.NaN
        lastCornerRadiusPx = Float.NaN
    }

    fun markDisplayListStale() {
        // 保留 hasRecordedContent 语义给调试；绘制侧以 stale 为准强制重录。
        displayListStale = true
        freezeRecording = false
        lastBlurRadiusPx = Float.NaN
        lastCornerRadiusPx = Float.NaN
    }

    fun markDisplayListFresh() {
        hasRecordedContent = true
        displayListStale = false
        needsSourceRefresh = false
        freezeRecording = true
    }

    fun markSourceDetachedForRefresh() {
        // 下一次 BackPreview 先让 Host 维持冻结景深一个绘制帧，再由来源页重录并接手。
        needsSourceRefresh = true
        freezeRecording = false
        lastBlurRadiusPx = Float.NaN
    }

    fun reset() = invalidateRecordedContent()
}

/** 冻结层是否可安全 drawLayer（有内容且 display list 未过期）。 */
internal fun isVideoCardTransitionSnapshotDrawable(
    hasRecordedContent: Boolean,
    displayListStale: Boolean,
): Boolean = hasRecordedContent && !displayListStale

internal class VideoCardTransitionSnapshotHandle(
    val contentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    private val sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    val state: VideoCardTransitionSnapshotLayerState,
) {
    private var effectRasterScale = Float.NaN
    /**
     * 来源内容与动态景深分层。内层只在快照刷新时录制，使用独立离屏缓存承载复杂页面；
     * 外层仅引用内层，模糊、透明度与圆角更新不会同时改动内层的图层属性。
     * 两层均由会话持有，不借用实时页面的 graphicsLayer，来源卸载后仍按原规则刷新。
     */
    fun recordSource(scope: DrawScope, drawBlock: DrawScope.() -> Unit) {
        sourceLayer.compositingStrategy = CompositingStrategy.Offscreen
        with(scope) {
            sourceLayer.record(block = drawBlock)
        }
        effectRasterScale = Float.NaN
    }

    /** 仅供刚完成录制的同一帧复用清晰底图，不再次执行来源页面的绘制。 */
    fun drawJustRecordedSource(scope: DrawScope) {
        with(scope) { drawLayer(sourceLayer) }
    }

    /** 只重录效果壳的单条图层引用，不重新执行首页的绘制或改变原始来源缓存。 */
    fun prepareEffectLayer(scope: DrawScope, blurRadiusPx: Float): Float {
        val rasterScale = resolveVideoCardBlurRasterScale(blurRadiusPx)
        val rasterSize = resolveVideoCardBlurRasterSize(
            Size(sourceLayer.size.width.toFloat(), sourceLayer.size.height.toFloat()),
            rasterScale,
        )
        if (effectRasterScale != rasterScale || contentLayer.size != rasterSize) {
            contentLayer.record(
                density = scope,
                layoutDirection = scope.layoutDirection,
                size = rasterSize,
            ) {
                scale(rasterScale, rasterScale, pivot = Offset.Zero) {
                    drawLayer(sourceLayer)
                }
            }
            effectRasterScale = rasterScale
            // Outline 的默认尺寸取当前纹理尺寸，切换分辨率后必须同步重建。
            state.lastCornerRadiusPx = Float.NaN
        }
        return rasterScale
    }

    fun clearRenderEffect() {
        contentLayer.renderEffect = null
        contentLayer.shadowElevation = 0f
        state.lastBlurRadiusPx = Float.NaN
        state.lastCornerRadiusPx = Float.NaN
    }

    fun releaseSession() {
        clearRenderEffect()
        effectRasterScale = Float.NaN
        // 标记失效不会清除 GraphicsLayer 的绘制闭包和子层引用。
        // 会话结束后录入空内容，归还旧列表的封面与离屏图层。
        contentLayer.record(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            size = androidx.compose.ui.unit.IntSize(1, 1),
        ) {}
        // 同时移除内层的旧内容与离屏缓存；下次会话重新按当前视口尺寸录制。
        sourceLayer.compositingStrategy = CompositingStrategy.Auto
        sourceLayer.record(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            size = androidx.compose.ui.unit.IntSize(1, 1),
        ) {}
        state.reset()
    }
}

/**
 * 把 [frame] 写到冻结 [contentLayer]（scale / 圆角 / BlurEffect），不负责 draw。
 */
internal fun applyVideoCardTransitionSnapshotFrame(
    contentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    snapshotState: VideoCardTransitionSnapshotLayerState,
    frame: VideoCardTransitionBackgroundFrame,
    canvasSize: androidx.compose.ui.geometry.Size,
    sourceBounds: Rect? = null,
    layerAlpha: Float = 1f,
    rasterScale: Float = 1f,
    /** 恒定半径混合模式：半径不随进度变化，模糊强度经 [layerAlpha] 合成。 */
    blurRadiusPxOverride: Float? = null,
) {
    // Shared with the host renderer: write the owning renderer's final alpha once.
    contentLayer.alpha = layerAlpha
    val pivot = resolveVideoCardTransitionBackgroundScalePivotOffset(
        sourceBounds = sourceBounds,
        canvasSize = canvasSize,
    )
    contentLayer.pivotOffset = pivot * rasterScale
    val translation = resolveVideoCardBlurRasterTranslation(pivot, rasterScale)
    contentLayer.translationX = translation.x
    contentLayer.translationY = translation.y
    contentLayer.scaleX = frame.contentScale / rasterScale
    contentLayer.scaleY = frame.contentScale / rasterScale
    val cornerRadiusPx = frame.cornerRadiusPx * rasterScale
    if (cornerRadiusPx != snapshotState.lastCornerRadiusPx) {
        snapshotState.lastCornerRadiusPx = cornerRadiusPx
        if (cornerRadiusPx > 0.01f) {
            contentLayer.setRoundRectOutline(cornerRadius = cornerRadiusPx)
            contentLayer.clip = true
            contentLayer.shadowElevation = frame.shadowElevationPx * rasterScale
        } else {
            contentLayer.setRectOutline()
            contentLayer.clip = false
            contentLayer.shadowElevation = 0f
        }
    } else if (cornerRadiusPx > 0.01f) {
        contentLayer.shadowElevation = frame.shadowElevationPx * rasterScale
    }
    val blurRadiusPx = (blurRadiusPxOverride ?: frame.blurRadiusPx) * rasterScale
    if (blurRadiusPx != snapshotState.lastBlurRadiusPx) {
        snapshotState.lastBlurRadiusPx = blurRadiusPx
        contentLayer.renderEffect = snapshotState.blurEffect(blurRadiusPx)
        VideoCardTransitionDiagnostics.onBlurEffectUpdated()
    }
}

@Composable
internal fun rememberVideoCardTransitionSnapshotHandle(): VideoCardTransitionSnapshotHandle {
    val layer = rememberGraphicsLayer()
    val sourceLayer = rememberGraphicsLayer()
    return remember(layer, sourceLayer) {
        VideoCardTransitionSnapshotHandle(
            contentLayer = layer,
            sourceLayer = sourceLayer,
            state = VideoCardTransitionSnapshotLayerState(),
        )
    }
}
