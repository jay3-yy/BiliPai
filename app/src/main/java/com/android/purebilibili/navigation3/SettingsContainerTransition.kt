package com.android.purebilibili.navigation3

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import com.android.purebilibili.core.ui.transition.videoCardTransitionSnapshotBlur
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundFrameCache
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardCoveredShapeCache
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardCoveredScale
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardCoveredCornerPx
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import com.android.purebilibili.feature.settings.SettingsContainerSource
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/** 设置与直播共用的来源容器过渡；页面始终按最终尺寸布局。 */
internal class SettingsContainerTransition(
    private val source: SettingsContainerSource,
    private val pageColor: () -> Color,
    private val realtimeBlurEnabled: Boolean = false,
    private val blurMotionTierProvider: () -> MotionTier = { MotionTier.Normal },
    private val durationMillis: Int = SETTINGS_CONTAINER_DURATION_MILLIS,
    // 仅直播卡片边界启用首页卡片景深；设置容器保留原有模糊与布局行为。
    private val cardBackgroundEffectsEnabled: Boolean = false,
    private val isLightBackground: () -> Boolean = { pageColor().luminance() > 0.5f },
    private val videoCardMotionEnabled: Boolean = false,
    // 实时直播保留子绘制指令；实际画面失效仍更新，不录制冻结的视频快照。
    private val isolateContentDrawing: Boolean = false,
    private val useSharedSnapshotBlur: Boolean = false,
) : NavTransition {
    private val blurCache = BlurRenderEffectCache()
    private val cardFrameCache = VideoCardTransitionBackgroundFrameCache()
    private val coveredShapeCache = MiuixVideoCardCoveredShapeCache()

    private fun cardBackgroundFrame(scope: NavTransitionScope) = cardFrameCache.resolve(
        progress = scope.relativeDepth.coerceIn(0f, 1f),
        phase = when {
            scope.relativeDepth >= 1f -> VideoCardTransitionBackgroundPhase.HELD
            scope.change == NavChange.Pop || scope.change is NavChange.MultiPop || scope.gesture != null ->
                VideoCardTransitionBackgroundPhase.RETURNING
            else -> VideoCardTransitionBackgroundPhase.OPENING
        },
        motionTier = blurMotionTierProvider(),
        isLightBackground = isLightBackground(),
        isGestureRestoreInProgress = scope.settle?.phase == NavSettlePhase.Cancel,
        density = scope.density.density,
        deviceCornerRadiusPx = 0f,
        scaleReduction = 0f, // 整页只在导航图层缩放一次。
    )
    override val handlesOwnClipping = true
    override val preparesEnterFrame = !videoCardMotionEnabled
    override val retainsSpringOvershoot = !videoCardMotionEnabled
    private val enterMotion = NavMotion(
        programmatic = NavSettleSpec.Tween(durationMillis, LinearEasing),
        commit = NavSettleSpec.Spring(dampingRatio = 0.82f, stiffness = 500f, clampOvershoot = false),
        cancel = NavSettleSpec.Spring(dampingRatio = 1f, stiffness = 700f),
    )
    private val returnMotion = NavMotion(
        programmatic = containerReturnSpringSpec(
            500f * (300f / durationMillis.coerceAtLeast(1)).let { it * it },
        ).let { NavSettleSpec.Spring(it.dampingRatio, it.stiffness, clampOvershoot = false, visibilityThreshold = 0.0001f) },
        commit = containerReturnSpringSpec().let {
            NavSettleSpec.Spring(it.dampingRatio, it.stiffness, clampOvershoot = false, visibilityThreshold = 0.0001f)
        },
        cancel = NavSettleSpec.Spring(dampingRatio = 1f, stiffness = 700f),
    )

    private val videoMotionSpec = com.android.purebilibili.core.ui.transition.resolveVideoHeroMotionSpec(durationMillis)
    private val videoEnterMotion = com.android.purebilibili.navigation3.predictiveback.resolveVideoHeroNavMotion(
        videoMotionSpec, returning = false,
    )
    private val videoReturnMotion = com.android.purebilibili.navigation3.predictiveback.resolveVideoHeroNavMotion(
        videoMotionSpec, returning = true,
    )

    override val motion: NavMotion get() = if (videoCardMotionEnabled) {
        if (source.hasOpened) videoReturnMotion else videoEnterMotion
    } else if (source.hasOpened) returnMotion else enterMotion

    override fun scrimFraction(scope: NavTransitionScope): Float = 0f

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier {
        val returnInFlight = {
            scope.role == NavRole.Outgoing && scope.isRunning
        }
        return this
            .then(if (cardBackgroundEffectsEnabled) Modifier.graphicsLayer {
                // 与首页视频卡片共用后退缩放与圆角，只变换被覆盖页，前景容器不叠加景深。
                val covered = scope.role == NavRole.Covered || scope.relativeDepth > 0f
                val depth = if (covered) scope.relativeDepth.coerceIn(0f, 1f) else 0f
                val reduced = blurMotionTierProvider() == MotionTier.Reduced
                val coveredScale = resolveMiuixVideoCardCoveredScale(depth, reduced)
                scaleX = coveredScale
                scaleY = coveredScale
                transformOrigin = TransformOrigin.Center
                clip = coveredScale < 1f
                shape = coveredShapeCache.resolve(resolveMiuixVideoCardCoveredCornerPx(
                    depth, reduced, scope.density.density,
                ))
                renderEffect = if (covered && realtimeBlurEnabled && !useSharedSnapshotBlur) {
                    blurCache.resolve(cardBackgroundFrame(scope).blurRadiusPx)
                } else null
            } else if (realtimeBlurEnabled) Modifier.graphicsLayer {
                // 只模糊被覆盖的来源页；当前页文字与容器轮廓保持清晰。
                // 进度在图层读取，进入、返回与取消共用导航时钟，不录制静态背景。
                val progress = resolveSettingsContainerBlurProgress(scope.relativeDepth)
                val frame = resolvePredictiveBackBlurFrame(
                    progress = progress,
                    motionTier = blurMotionTierProvider(),
                    isLightBackground = pageColor().luminance() > 0.5f,
                )
                renderEffect = blurCache.resolve(frame.blurRadiusPx)
            } else Modifier)
            .drawWithContent {
                // 逐帧深度只在绘制阶段读取，避免整个直播 entry 跟随导航进度重组。
                if (scope.relativeDepth < 0f) source.returnInFlight = returnInFlight
                // 来源页景深在外层图层统一处理，前景展开仍只由自己的容器几何负责。
                if (scope.role == NavRole.Covered || scope.relativeDepth > 0f) {
                    drawContent()
                    if (cardBackgroundEffectsEnabled) {
                        val background = cardBackgroundFrame(scope)
                        if (background.scrimAlpha > 0f) {
                            drawRect((if (isLightBackground()) VIDEO_CARD_TRANSITION_LIGHT_SCRIM_TINT else Color.Black)
                                .copy(alpha = background.scrimAlpha))
                        }
                    }
                    return@drawWithContent
                }
                if (scope.relativeDepth == 0f) {
                    source.hasOpened = true
                    drawContent()
                    return@drawWithContent
                }
                val destination = Rect(Offset.Zero, size)
                val expectedSourceScale = if (cardBackgroundEffectsEnabled) resolveMiuixVideoCardCoveredScale(
                    (1f + scope.relativeDepth).coerceIn(0f, 1f),
                    reducedMotion = blurMotionTierProvider() == MotionTier.Reduced,
                ) else 1f
                if (!source.isUsable(scope.layoutSize, expectedSourceScale)) {
                    // 窗口变化或来源已卸载时只淡化，不能收回旧窗口中的错误位置。
                    val alpha = (1f + scope.relativeDepth).coerceIn(0f, 1f)
                    drawContext.canvas.saveLayer(destination, Paint().apply { this.alpha = alpha })
                    drawContent()
                    drawContext.canvas.restore()
                    return@drawWithContent
                }
                val frame = if (videoCardMotionEnabled) {
                    // 视频导航驱动已经缓动；几何直接读取同一深度，禁止再套设置页曲线。
                    resolveSettingsContainerFrame(
                        source.bounds, destination, scope.relativeDepth,
                        returning = false, springDriven = true,
                    )
                } else resolveSettingsContainerFrame(
                    source.bounds, destination, scope.relativeDepth,
                    returning = source.hasOpened && (scope.change == NavChange.Pop || scope.gesture != null ||
                        scope.change is NavChange.MultiPop || scope.settle?.phase == NavSettlePhase.Cancel ||
                        scope.settle?.phase == NavSettlePhase.Commit),
                    springDriven = source.hasOpened && scope.settle?.phase != NavSettlePhase.Cancel,
                )
                // 头像入口按自身尺寸提前交接，不能沿用设置行的整页内容放大。
                // 两种材质共用此入口；直播封面卡片与设置容器保留原有时机和缩放。
                val avatarFrame = if (source.avatarSource) {
                    resolveLiveAvatarContainerContentFrame(frame.bounds.width / source.bounds.width)
                } else null
                val contentAlpha = avatarFrame?.contentAlpha ?: frame.contentAlpha
                val sourceAlpha = avatarFrame?.sourceAlpha ?: (1f - frame.contentAlpha)
                val sourceScale = avatarFrame?.sourceScale ?: (frame.bounds.width / source.bounds.width)
                val top = CornerRadius(source.topRadius * frame.cornerFraction)
                val bottom = CornerRadius(source.bottomRadius * frame.cornerFraction)
                val path = Path().apply {
                    addRoundRect(RoundRect(frame.bounds, top, top, bottom, bottom))
                }
                clipPath(path) {
                    drawRect(lerp(source.color, pageColor(), contentAlpha), frame.bounds.topLeft, frame.bounds.size)
                    // 源行与目标页共用一个透明度时钟；目标页出现后不残留源文字和图标。
                    if (sourceAlpha > 0f) {
                        if (sourceAlpha < 1f) {
                            drawContext.canvas.saveLayer(frame.bounds, Paint().apply { alpha = sourceAlpha })
                        }
                        // 仅重放当前行自己的图层，避免递归采集整页或玻璃背景。
                        translate(frame.bounds.left, frame.bounds.top) {
                            scale(sourceScale, sourceScale, pivot = Offset.Zero) { drawLayer(source.layer) }
                        }
                        if (sourceAlpha < 1f) drawContext.canvas.restore()
                    }
                    if (contentAlpha > 0f) {
                        val needsContentLayer = contentAlpha < 1f || !isolateContentDrawing
                        if (needsContentLayer) {
                            drawContext.canvas.saveLayer(destination, Paint().apply { alpha = contentAlpha })
                        }
                        // 页面按原始大小渲染，容器边界移动而不是反复测量页面。
                        translate(frame.bounds.left, frame.bounds.top) { this@drawWithContent.drawContent() }
                        if (needsContentLayer) drawContext.canvas.restore()
                    }
                }
                // 昵称与直播标记位于圆形主容器之外，在同一飞行层按主容器几何移动，避免被圆形裁掉。
                val companionAlpha = sourceAlpha
                if (companionAlpha > 0f && source.companions.isNotEmpty()) {
                    val factor = sourceScale
                    if (companionAlpha < 1f) {
                        drawContext.canvas.saveLayer(destination, Paint().apply { alpha = companionAlpha })
                    }
                    source.companions.forEach { companion ->
                        val offset = frame.bounds.topLeft + (companion.bounds.topLeft - source.bounds.topLeft) * factor
                        translate(offset.x, offset.y) {
                            scale(factor, factor, pivot = Offset.Zero) { drawLayer(companion.layer) }
                        }
                    }
                    if (companionAlpha < 1f) drawContext.canvas.restore()
                }
            }
            .then(if (useSharedSnapshotBlur) Modifier.videoCardTransitionSnapshotBlur(
                frameProvider = {
                    if (cardBackgroundEffectsEnabled && realtimeBlurEnabled &&
                        (scope.role == NavRole.Covered || scope.relativeDepth > 0f)
                    ) cardBackgroundFrame(scope) else null
                },
                refreshSourceProvider = { source.hasOpened && scope.relativeDepth < 1f },
            ) else Modifier)
            .then(if (isolateContentDrawing) Modifier.graphicsLayer() else Modifier)
    }
}
