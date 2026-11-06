package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalView
import com.android.purebilibili.core.ui.adaptive.MotionTier

/**
 * Host 持有的会话级冻结景深层：画在 [NavDisplay] 下方。
 *
 * 与来源 Scene 的 dispose 解耦——SinglePane 在 HELD 卸掉首页后，预测返回仍可
 * 立刻从满糊跟手消到清晰，不必等 previousScene 重新挂 effect 再补录。
 */
@Composable
internal fun VideoCardTransitionHostDepthLayer(
    enabled: Boolean,
    snapshotHandle: VideoCardTransitionSnapshotHandle,
    progressProvider: () -> Float,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    exposureProvider: () -> VideoCardTransitionExposure,
    isGestureRestoreInProgressProvider: () -> Boolean = { false },
    motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    isLightBackgroundProvider: () -> Boolean = { false },
    realtimeBlurEnabledProvider: () -> Boolean = { false },
    scaleReductionProvider: () -> Float = { VIDEO_CARD_TRANSITION_BACKGROUND_SCALE_REDUCTION },
    sourceBoundsProvider: () -> Rect? = { null },
    /** 背景遮罩独立深度（引擎 maskAlpha 通道）；缺省跟随 [progressProvider]。 */
    maskProgressProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val contentLayer = snapshotHandle.contentLayer
    val snapshotState = snapshotHandle.state
    if (!enabled) {
        SideEffect { snapshotHandle.clearRenderEffect() }
        return
    }
    val view = LocalView.current
    var deviceCornerRadiusPx by remember { mutableFloatStateOf(0f) }
    SideEffect {
        deviceCornerRadiusPx = resolveDeviceDisplayCornerRadiusPx(view.rootWindowInsets)
        if (!realtimeBlurEnabledProvider()) snapshotHandle.clearRenderEffect()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                // Host 层无子内容；只绘制冻结快照。
                val exposure = exposureProvider()
                val motionTier = motionTierProvider()
                val realtimeBlur = realtimeBlurEnabledProvider()
                if (
                    !shouldPaintHostOwnedDepthLayer(
                        exposure = exposure,
                        hasRecordedContent = snapshotState.hasRecordedContent,
                        displayListStale = snapshotState.displayListStale,
                        motionTier = motionTier,
                        realtimeBlurEnabled = realtimeBlur,
                        sourceRendererReady = snapshotState.hasReadySourceRenderer,
                    )
                ) {
                    return@drawWithContent
                }
                val phase = phaseProvider()
                // SettledHidden：详情盖住时仍保持满糊，手势首帧无「先清晰再糊」跳变。
                val progress = resolveHostOwnedDepthProgress(
                    exposure = exposure,
                    liveProgress = progressProvider(),
                )
                // 遮罩走独立弹簧通道（引擎 maskAlpha）：压暗先于模糊到位。
                val maskProgress = resolveHostOwnedDepthProgress(
                    exposure = exposure,
                    liveProgress = maskProgressProvider?.invoke() ?: progress,
                )
                val frozenLayerAlpha = resolveVideoCardTransitionFrozenLayerAlpha(
                    exposure = exposure,
                    depthProgress = progress,
                )
                if (!shouldDrawVideoCardTransitionFrozenLayer(frozenLayerAlpha)) {
                    // Source and host use the same handoff boundary. No invisible blur
                    // updates during preparation; retain the layer for gesture reversal.
                    val isLight = isLightBackgroundProvider()
                    val scrimAlpha = resolveVideoCardTransitionBackgroundScrimAlpha(
                        depthProgress = resolveVideoCardTransitionDepthProgress(maskProgress, phase),
                        phase = phase,
                        motionTier = motionTier,
                        isLightBackground = isLight,
                    )
                    if (scrimAlpha > 0.001f) {
                        drawRect((if (isLight) VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT else Color.Black)
                            .copy(alpha = scrimAlpha))
                    }
                    return@drawWithContent
                }
                val frame = snapshotState.frameCache.resolve(
                    progress = progress,
                    phase = phase,
                    motionTier = motionTier,
                    isLightBackground = isLightBackgroundProvider(),
                    isGestureRestoreInProgress = isGestureRestoreInProgressProvider(),
                    density = density,
                    deviceCornerRadiusPx = deviceCornerRadiusPx,
                    scaleReduction = scaleReductionProvider(),
                    maskProgress = maskProgress,
                )
                // 恒定半径 + 合成混合：冻结内容静态，固定半径的模糊只在首帧光栅化一次，
                // 锐利原层与模糊层按混合强度叠合成渐进模糊——不依赖 live 内容垫底，
                // 不会透出窗口底色，免除逐帧变半径的 RenderThread 重光栅化。
                val fixedBlurRadiusPx = if (frame.blurRadiusPx > 0.01f) {
                    resolveVideoCardTransitionMaxBlurRadiusPx(motionTier, density)
                } else {
                    0f
                }
                val blendPivot = resolveVideoCardTransitionBackgroundScalePivotOffset(
                    sourceBounds = sourceBoundsProvider(),
                    canvasSize = size,
                )
                applyVideoCardTransitionSnapshotFrame(
                    contentLayer = contentLayer,
                    snapshotState = snapshotState,
                    frame = frame,
                    canvasSize = size,
                    sourceBounds = sourceBoundsProvider(),
                    layerAlpha = frozenLayerAlpha,
                    rasterScale = snapshotHandle.prepareEffectLayer(this, fixedBlurRadiusPx),
                    blurRadiusPxOverride = fixedBlurRadiusPx,
                )
                if (shouldDrawVideoCardTransitionScaleGapFill(frame.contentScale)) {
                    drawRect(
                        resolveVideoCardTransitionScaleGapFillColor(
                            isLightBackground = frame.useLightScrimTint,
                            scrimAlpha = frame.scrimAlpha,
                        )
                    )
                }
                snapshotHandle.drawSharpSourceAligned(
                    scope = this,
                    contentScale = frame.contentScale,
                    pivot = blendPivot,
                    alpha = resolveVideoCardSharpSnapshotAlpha(frozenLayerAlpha, frame.blurAlpha),
                )
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
            },
    )
}

/**
 * Host 层何时绘制：源渲染器尚未就绪，且有可用冻结内容时。
 *
 * - stale / 无内容：永不 paint（防黑屏）。
 * - SettledHidden：源已卸载时维持详情下的满糊快照。
 * - BackPreview / Returning / Restoring：准备期间垫景深；源就绪后停止绘制。
 */
internal fun shouldPaintHostOwnedDepthLayer(
    exposure: VideoCardTransitionExposure,
    hasRecordedContent: Boolean,
    displayListStale: Boolean = false,
    motionTier: MotionTier,
    realtimeBlurEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
    sourceRendererReady: Boolean = false,
): Boolean {
    if (sourceRendererReady) return false
    if (
        !isVideoCardTransitionSnapshotDrawable(
            hasRecordedContent = hasRecordedContent,
            displayListStale = displayListStale,
        )
    ) {
        return false
    }
    if (motionTier == MotionTier.Reduced) return false
    if (!realtimeBlurEnabled) return false
    if (sdkInt < Build.VERSION_CODES.S) return false
    return when (exposure) {
        // SettledHidden：详情下预热（须 drawable）。
        // BackPreview/Returning：drawable 时 Host 在 NavDisplay 下垫一层跟手糊；
        // Prepared source renderers own the layer; Host only fills the preparation gap.
        VideoCardTransitionExposure.SettledHidden,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Restoring,
        VideoCardTransitionExposure.Returning,
        -> true
        VideoCardTransitionExposure.Opening,
        VideoCardTransitionExposure.Idle,
        -> false
    }
}

/** SettledHidden 强制满糊；其余跟 live progress（含预测手势 1→0）。 */
internal fun resolveHostOwnedDepthProgress(
    exposure: VideoCardTransitionExposure,
    liveProgress: Float,
): Float {
    return when (exposure) {
        VideoCardTransitionExposure.SettledHidden -> 1f
        else -> liveProgress.coerceIn(0f, 1f)
    }
}

/**
 * 来源 Scene 的 DisposableEffect 是否可销毁 Host 快照。
 * Host 共享 handle 时禁止 dispose 清层，否则预测返回无糊可用。
 */
internal fun shouldInvalidateSnapshotOnSourceDispose(
    isHostOwnedSnapshot: Boolean,
): Boolean = !isHostOwnedSnapshot

/**
 * Host 会话层：源 dispose 时是否标 displayListStale。
 *
 * **false**：完整进详情后仍保留 OPENING 冻结帧，SettledHidden Host 可预热满糊；
 * 否则（旧 true）dispose→stale→Host 不画 → 只有开场中断返回有糊、看完再返回无糊。
 * 源页刷新改走 [VideoCardTransitionSnapshotLayerState.needsSourceRefresh]。
 * 若冻结帧在部分机型 dispose 后变空，BackPreview 源页重录会换上真实首页。
 */
internal fun shouldMarkDisplayListStaleOnHostOwnedSourceDispose(): Boolean = false

/**
 * 仅 IDLE 才释放 Host 快照 / BlurEffect；SettledHidden 必须保留满糊层。
 */
internal fun shouldReleaseHostOwnedDepthLayer(
    exposure: VideoCardTransitionExposure,
): Boolean = exposure == VideoCardTransitionExposure.Idle
