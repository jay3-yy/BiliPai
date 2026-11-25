@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalTransitionApi::class)

package com.android.purebilibili.navigation3

import android.util.Log
import android.view.animation.AnimationUtils
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.transition.navigationBlurBlend
import com.android.purebilibili.core.ui.transition.resolveNavigationBlurBlendFrame
import com.android.purebilibili.feature.settings.LocalSettingsContainerSharedScope
import com.android.purebilibili.feature.settings.SettingsSharedContainerOrigin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import kotlin.math.abs

private const val SETTINGS_SHARED_DRAW_TAG = "SettingsSharedDraw"
private const val SHARED_CONTAINER_BACKGROUND_ZOOM = 0.10f
private const val SHARED_CONTAINER_BLUR_QUANTUM_DP = 4f

/** 导航层保留手势和背景模糊，条目与页面的边界由 Compose 共享转场绘制。 */
internal class SettingsSharedContainerTransition(
    val origin: SettingsSharedContainerOrigin,
    // 目的地在返回栈中的深度；多级同时返回时用它给共享叠层排序，
    // 离场 entry 保留创建时的转场实例，深度也随之冻结。
    val destinationZIndex: Float = 0f,
    private val pageColor: () -> Color,
    private val realtimeBlurEnabled: Boolean,
    private val blurMotionTier: () -> MotionTier,
    val navigationControlFallback: Boolean = false,
    val scaleDestinationToWidth: Boolean = navigationControlFallback,
    val stiffnessScale: Float = 1f,
) : NavTransition {
    override val preparesEnterFrame = true
    override val handlesOwnClipping = true
    private val effectiveScale = stiffnessScale.coerceIn(0.45f, 1.5f)
    override val motion = if (navigationControlFallback) {
        // 对齐金标卡片的平滑减速曲线：单调收敛，返回略快于打开，收紧尾部避免播放器 Surface 久等。
        NavMotion(
            programmatic = NavSettleSpec.Spring(
                dampingRatio = 1f,
                stiffness = 310f * effectiveScale,
                visibilityThreshold = 0.002f,
            ),
            commit = NavSettleSpec.Spring(
                dampingRatio = 1f,
                stiffness = 390f * effectiveScale,
                visibilityThreshold = 0.002f,
            ),
            cancel = NavSettleSpec.Spring(
                dampingRatio = 1f,
                stiffness = 520f * effectiveScale,
                visibilityThreshold = 0.002f,
            ),
        )
    } else {
        val settle = NavSettleSpec.Spring(stiffness = 500f, visibilityThreshold = 0.0005f)
        NavMotion(
            programmatic = settle,
            commit = settle,
            cancel = NavSettleSpec.Spring(stiffness = 700f, visibilityThreshold = 0.0005f),
        )
    }

    override fun scrimFraction(scope: NavTransitionScope): Float = 0f

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
        navigationBlurBlend {
            val covered = if (scope.relativeDepth.isFinite()) scope.relativeDepth.coerceIn(0f, 1f) else 0f
            val tier = blurMotionTier()
            val lightBackground = pageColor().luminance() > 0.5f
            val density = scope.density.density
            val maxBlurRadiusPx = (if (lightBackground) 16f else 18f) * density
            val blurProgress = if (realtimeBlurEnabled && tier != MotionTier.Reduced) {
                resolveSettingsContainerBlurProgress(covered)
            } else 0f
            val rawRadiusPx = maxBlurRadiusPx * blurProgress
            val blurQuantumPx = (SHARED_CONTAINER_BLUR_QUANTUM_DP * density).coerceAtLeast(4f)
            // 超过最小步长后直接用 alpha=1f 绘制真半径模糊，避免下层清晰文字透过半透明模糊层；
            // 步长按屏幕密度对齐为 4dp，避免高分屏上频繁切换大半径 RenderEffect。
            val (radiusPx, blurAlpha) = when {
                rawRadiusPx <= 0.01f -> 0f to 0f
                rawRadiusPx < blurQuantumPx ->
                    blurQuantumPx to (rawRadiusPx / blurQuantumPx).coerceIn(0f, 1f)
                else -> {
                    val quantized = (kotlin.math.round(rawRadiusPx / blurQuantumPx) *
                        blurQuantumPx).coerceIn(blurQuantumPx, maxBlurRadiusPx)
                    quantized to 1f
                }
            }
            val contentScale = if (tier != MotionTier.Reduced) {
                1f + SHARED_CONTAINER_BACKGROUND_ZOOM * covered
            } else 1f
            val maxScrimAlpha = when {
                tier == MotionTier.Reduced -> if (lightBackground) 0.08f else 0.14f
                navigationControlFallback -> if (lightBackground) 0.22f else 0.42f
                else -> if (lightBackground) 0.18f else 0.34f
            }
            resolveNavigationBlurBlendFrame(
                progress = blurAlpha,
                motionTier = tier,
                isLightBackground = lightBackground,
                maxBlurRadiusPx = radiusPx,
                contentScale = contentScale,
                scrimAlpha = maxScrimAlpha * covered,
            )
        }
}

@Composable
internal fun SettingsSharedTransitionLayout(enabled: Boolean, content: @Composable () -> Unit) {
    // 保持固定槽位结构：横竖屏切换（如视频上滑全屏导致 settingsPersistentPanes 变化）或
    // 动效开关变化时会动态翻转 enabled，若拆成不同分支会销毁并重建包裹 NavDisplay 的子树，
    // 在交接帧触发 SaveableStateHolder 重复 key 崩溃（Key ... was used multiple times）。
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSettingsContainerSharedScope provides if (enabled) this else null) {
            content()
        }
    }
}

internal data class DeterministicSharedContainerGeometry(
    val outerScaleX: Float,
    val outerScaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val innerScaleX: Float,
    val innerScaleY: Float,
    val innerTransformOrigin: TransformOrigin,
)

internal data class DeterministicSharedContainerAlpha(
    val containerAlpha: Float,
    val contentAlpha: Float,
    val sourceOverlayAlpha: Float,
    val sourceCardHidden: Boolean,
)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val u = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/**
 * 纯几何容器插值：完全由导航深度 open ∈ [0, 1] 与实测来源矩形决定，
 * 不依赖 Lookahead/MFR 状态机，避免详情页折叠或切 Tab 后返回路线漂移。
 */
internal fun resolveDeterministicSharedContainerGeometry(
    source: Rect,
    destinationWidth: Float,
    destinationHeight: Float,
    open: Float,
    scaleDestinationToWidth: Boolean,
): DeterministicSharedContainerGeometry {
    val w = destinationWidth.coerceAtLeast(1f)
    val h = destinationHeight.coerceAtLeast(1f)
    val t = open.coerceIn(0f, 1f)
    val current = lerpRect(source, Rect(0f, 0f, w, h), t)
    val outerScaleX = (current.width / w).coerceAtLeast(0.01f)
    val outerScaleY = (current.height / h).coerceAtLeast(0.01f)
    return if (scaleDestinationToWidth) {
        DeterministicSharedContainerGeometry(
            outerScaleX = outerScaleX,
            outerScaleY = outerScaleY,
            translationX = current.left,
            translationY = current.top,
            innerScaleX = 1f,
            innerScaleY = outerScaleX / outerScaleY,
            innerTransformOrigin = TransformOrigin(0.5f, 0f),
        )
    } else {
        DeterministicSharedContainerGeometry(
            outerScaleX = outerScaleX,
            outerScaleY = outerScaleY,
            translationX = current.left,
            translationY = current.top,
            innerScaleX = 1f / outerScaleX,
            innerScaleY = 1f / outerScaleY,
            innerTransformOrigin = TransformOrigin(0f, 0f),
        )
    }
}

internal fun resolveDeterministicSharedContainerAlpha(
    open: Float,
    returning: Boolean,
    hasSourceOverlay: Boolean = true,
): DeterministicSharedContainerAlpha {
    val t = open.coerceIn(0f, 1f)
    if (!hasSourceOverlay) {
        // 源快照层在首帧绘制期尚未就绪（如冷启首次点击或相关推荐点击）时，
        // preparesEnterFrame 首帧（t <= 0.001）必须让目标壳完全透明，避免未加载的黑色播放器与骨架直接盖在来源卡片上。
        return if (returning) {
            DeterministicSharedContainerAlpha(
                containerAlpha = smoothstep(0f, 0.08f, t),
                contentAlpha = 1f,
                sourceOverlayAlpha = 0f,
                sourceCardHidden = t > 0.08f,
            )
        } else {
            DeterministicSharedContainerAlpha(
                containerAlpha = smoothstep(0.001f, 0.14f, t),
                contentAlpha = 1f,
                sourceOverlayAlpha = 0f,
                sourceCardHidden = t > 0.14f,
            )
        }
    }
    // 复用 0.10.1 共享容器叠化窗口：
    // 展开 0..0.22 完整展示来源卡片并遮住加载骨架，0.22..0.50 平滑切到详情页；
    // 返回 0.75..0.45 将来源卡片叠回飞行壳内，0.45..0 以完整来源卡精准落槽。
    // t <= 0.001 时下方真实来源卡片处于可见态，整壳保持透明避免壁纸底图或垫底色在首末帧双层叠加。
    val contentAlpha = resolveSettingsContainerContentAlpha(t, returning)
    return DeterministicSharedContainerAlpha(
        containerAlpha = if (t <= 0.001f) 0f else 1f,
        contentAlpha = contentAlpha,
        sourceOverlayAlpha = 1f - contentAlpha,
        sourceCardHidden = t > 0.001f,
    )
}

internal fun isDeterministicSharedSourceCardHidden(
    bounds: SettingsSharedContainerBounds,
    hasSourceOverlay: Boolean,
): Boolean {
    val scope = bounds.navigationScope.value
    val morphing = isDeterministicSharedContainerMorphing(
        relativeDepth = scope.relativeDepth,
        isRunning = scope.isRunning,
        isRemoving = scope.isRemoving,
        gestureActive = scope.gesture != null,
    )
    if (!morphing) return false
    val openFraction = (1f + scope.relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
    val returning = bounds.isReturning(scope, openFraction)
    return resolveDeterministicSharedContainerAlpha(
        open = openFraction,
        returning = returning,
        hasSourceOverlay = hasSourceOverlay,
    ).sourceCardHidden
}

private class DeterministicSharedContainerClipShape : Shape {
    var tlX = 0f
    var tlY = 0f
    var trX = 0f
    var trY = 0f
    var brX = 0f
    var brY = 0f
    var blX = 0f
    var blY = 0f
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var cachedOutline: Outline? = null

    fun matches(
        tlX: Float, tlY: Float,
        trX: Float, trY: Float,
        brX: Float, brY: Float,
        blX: Float, blY: Float,
    ): Boolean = abs(this.tlX - tlX) <= 0.05f && abs(this.tlY - tlY) <= 0.05f &&
        abs(this.trX - trX) <= 0.05f && abs(this.trY - trY) <= 0.05f &&
        abs(this.brX - brX) <= 0.05f && abs(this.brY - brY) <= 0.05f &&
        abs(this.blX - blX) <= 0.05f && abs(this.blY - blY) <= 0.05f

    fun update(
        tlX: Float, tlY: Float,
        trX: Float, trY: Float,
        brX: Float, brY: Float,
        blX: Float, blY: Float,
    ) {
        this.tlX = tlX
        this.tlY = tlY
        this.trX = trX
        this.trY = trY
        this.brX = brX
        this.brY = brY
        this.blX = blX
        this.blY = blY
        cachedOutline = null
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val current = cachedOutline
        if (current != null && lastWidth == size.width && lastHeight == size.height) return current
        lastWidth = size.width
        lastHeight = size.height
        val maxX = size.width * 0.5f
        val maxY = size.height * 0.5f
        val outline = Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topLeft = CornerRadius(tlX.coerceIn(0f, maxX), tlY.coerceIn(0f, maxY)),
                topRight = CornerRadius(trX.coerceIn(0f, maxX), trY.coerceIn(0f, maxY)),
                bottomRight = CornerRadius(brX.coerceIn(0f, maxX), brY.coerceIn(0f, maxY)),
                bottomLeft = CornerRadius(blX.coerceIn(0f, maxX), blY.coerceIn(0f, maxY)),
            ),
        )
        cachedOutline = outline
        return outline
    }
}

private class DeterministicSharedContainerShapeCache {
    private val shapes = arrayOf(DeterministicSharedContainerClipShape(), DeterministicSharedContainerClipShape())
    private var index = 0

    fun resolve(
        tlPx: Float, trPx: Float, brPx: Float, blPx: Float,
        outerScaleX: Float, outerScaleY: Float,
    ): Shape {
        val sx = outerScaleX.coerceAtLeast(0.01f)
        val sy = outerScaleY.coerceAtLeast(0.01f)
        val tlX = tlPx / sx
        val tlY = tlPx / sy
        val trX = trPx / sx
        val trY = trPx / sy
        val brX = brPx / sx
        val brY = brPx / sy
        val blX = blPx / sx
        val blY = blPx / sy
        if (tlX <= 0.05f && trX <= 0.05f && brX <= 0.05f && blX <= 0.05f) return RectangleShape
        val current = shapes[index]
        if (current.matches(tlX, tlY, trX, trY, brX, brY, blX, blY)) return current
        index = 1 - index
        return shapes[index].also { it.update(tlX, tlY, trX, trY, brX, brY, blX, blY) }
    }
}

internal fun isDeterministicSharedContainerMorphing(
    relativeDepth: Float,
    isRunning: Boolean,
    isRemoving: Boolean,
    gestureActive: Boolean,
): Boolean {
    // 被更深一层页面覆盖的父页（relativeDepth > 0 且未离场）不参与自身壳形变。
    if (relativeDepth > 0f && !isRemoving) return false
    val openFraction = (1f + relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
    return isRunning || isRemoving || gestureActive || openFraction < 0.999f
}

internal class SettingsSharedContainerBounds(
    val origin: SettingsSharedContainerOrigin,
    val destinationZIndex: Float,
    val sourceVisibility: AnimatedVisibilityScope?,
    val pageVisibility: AnimatedVisibilityScope?,
    val open: State<Float>,
    val navigationScope: State<NavTransitionScope>,
    val transition: Transition<Boolean>?,
    val scaleDestinationToWidth: Boolean = false,
    val deterministicGeometry: Boolean = false,
) {
    var sourcePlaced by mutableStateOf(false)
    var destinationPlaced by mutableStateOf(false)
    var destinationCorners by mutableStateOf(WindowCornerRadii())
    var hasOpened: Boolean = false
    var returnFallbackReported: Boolean = false
    val clipShape = settingsSharedContainerShape(origin, open) { destinationCorners }

    val isMorphing: Boolean
        get() = if (deterministicGeometry) {
            val scope = navigationScope.value
            isDeterministicSharedContainerMorphing(
                relativeDepth = scope.relativeDepth,
                isRunning = scope.isRunning,
                isRemoving = scope.isRemoving,
                gestureActive = scope.gesture != null,
            )
        } else {
            transition != null && transition.currentState != transition.targetState
        }

    fun isReturning(scope: NavTransitionScope, openFraction: Float): Boolean {
        if (openFraction >= 0.99f && !scope.isRemoving) hasOpened = true
        return hasOpened || scope.isRemoving || scope.gesture != null ||
            scope.change == NavChange.Pop || scope.change is NavChange.MultiPop ||
            scope.settle?.phase == NavSettlePhase.Commit || scope.settle?.phase == NavSettlePhase.Cancel
    }

    val traceDraw = BuildConfig.DEBUG && Log.isLoggable(SETTINGS_SHARED_DRAW_TAG, Log.DEBUG)

    fun trace(stage: String, detail: () -> String) {
        // 诊断读取不能使原本可复用的绘制指令跟随转场状态重新录制。
        Snapshot.withoutReadObservation {
            Log.d(
                SETTINGS_SHARED_DRAW_TAG,
                "frame_ms=${AnimationUtils.currentAnimationTimeMillis()} monotonic_ns=${System.nanoTime()} " +
                    "session=${System.identityHashCode(this)} stage=$stage open=${open.value} " +
                    "depth=${navigationScope.value.relativeDepth} " +
                    "state=${transition?.currentState}->${transition?.targetState} " +
                    "available=${origin.available} placed=$sourcePlaced/$destinationPlaced ${detail()}",
            )
        }
    }
}

private data class SharedContainerSeekSnapshot(
    val fraction: Float,
    val running: Boolean,
    val removing: Boolean,
    val destinationPlaced: Boolean,
)

@Composable
internal fun settingsSharedContainerDestination(
    origin: SettingsSharedContainerOrigin,
    navScope: NavTransitionScope,
    color: Color,
    destinationZIndex: Float = 0f,
    scaleDestinationToWidth: Boolean = false,
    deterministicGeometry: Boolean = false,
    wallpaperBacking: Modifier = Modifier,
): Modifier {
    val latestScope = rememberUpdatedState(navScope)
    val view = LocalView.current
    val activity = LocalActivity.current
    if (deterministicGeometry) {
        val openState = remember {
            object : State<Float> {
                override val value: Float
                    get() = (1f + latestScope.value.relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
            }
        }
        val bounds = remember(origin, openState, scaleDestinationToWidth) {
            SettingsSharedContainerBounds(
                origin = origin,
                destinationZIndex = destinationZIndex,
                sourceVisibility = null,
                pageVisibility = null,
                open = openState,
                navigationScope = latestScope,
                transition = null,
                scaleDestinationToWidth = scaleDestinationToWidth,
                deterministicGeometry = true,
            ).apply {
                val isFreshPush = navScope.change == NavChange.Push && !navScope.isRemoving && navScope.relativeDepth <= 0f
                hasOpened = !isFreshPush && navScope.relativeDepth >= 0f
            }
        }
        DisposableEffect(bounds) {
            origin.sharedBounds = bounds
            onDispose {
                if (origin.sharedBounds === bounds) origin.sharedBounds = null
            }
        }
        LaunchedEffect(bounds) {
            snapshotFlow {
                latestScope.value.isRemoving && origin.available
            }.first { it }
            if (!origin.returnLandingHandledByOwner) origin.returnLanding.request()
        }
        val shapeCache = remember(bounds) { DeterministicSharedContainerShapeCache() }
        return Modifier
            .onGloballyPositioned {
                bounds.destinationPlaced = true
                bounds.destinationCorners = resolveWindowCornerRadii(
                    view,
                    Rect(it.positionInWindow(), it.size.toSize()),
                    isInMultiWindow = activity?.isInMultiWindowMode == true,
                )
            }
            .graphicsLayer {
                val scope = latestScope.value
                val openFraction = (1f + scope.relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
                val morphing = isDeterministicSharedContainerMorphing(
                    relativeDepth = scope.relativeDepth,
                    isRunning = scope.isRunning,
                    isRemoving = scope.isRemoving,
                    gestureActive = scope.gesture != null,
                )
                val returning = bounds.isReturning(scope, openFraction)
                if (!morphing) {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    alpha = 1f
                    clip = false
                    shape = RectangleShape
                    return@graphicsLayer
                }
                val sourceRect = if (origin.available) origin.resolveSourceRectInHost?.invoke() else null
                if (sourceRect == null || sourceRect.width <= 1f || sourceRect.height <= 1f) {
                    if (returning && !bounds.returnFallbackReported) {
                        bounds.returnFallbackReported = true
                        val fallbackReason = when {
                            !origin.available -> "source_unavailable"
                            origin.coordinates?.isAttached != true &&
                                origin.capturedHostSize != androidx.compose.ui.unit.IntSize(
                                    size.width.toInt(),
                                    size.height.toInt(),
                                ) -> "cross_orientation_or_resized"
                            else -> "offscreen_or_detached"
                        }
                        com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics.onReturnFallback(
                            engine = "android_shared",
                            reason = fallbackReason,
                            detail = { "owner=${origin.owner} identity=${origin.sourceIdentity}" },
                        )
                    }
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    alpha = openFraction
                    clip = false
                    shape = RectangleShape
                    return@graphicsLayer
                }
                val geometry = resolveDeterministicSharedContainerGeometry(
                    source = sourceRect,
                    destinationWidth = size.width,
                    destinationHeight = size.height,
                    open = openFraction,
                    scaleDestinationToWidth = scaleDestinationToWidth,
                )
                val hasSourceOverlay = origin.hasActiveSourceOverlay(bounds)
                val alphas = resolveDeterministicSharedContainerAlpha(
                    open = openFraction,
                    returning = returning,
                    hasSourceOverlay = hasSourceOverlay,
                )
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = geometry.outerScaleX
                scaleY = geometry.outerScaleY
                translationX = geometry.translationX
                translationY = geometry.translationY
                alpha = alphas.containerAlpha
                val needsClip = openFraction < 0.999f
                clip = needsClip
                if (!needsClip) {
                    shape = RectangleShape
                } else {
                    val corners = bounds.destinationCorners
                    val startTopPx = origin.topRadius.toPx()
                    val startBottomPx = origin.bottomRadius.toPx()
                    shape = shapeCache.resolve(
                        tlPx = startTopPx + (corners.topLeft - startTopPx) * openFraction,
                        trPx = startTopPx + (corners.topRight - startTopPx) * openFraction,
                        brPx = startBottomPx + (corners.bottomRight - startBottomPx) * openFraction,
                        blPx = startBottomPx + (corners.bottomLeft - startBottomPx) * openFraction,
                        outerScaleX = geometry.outerScaleX,
                        outerScaleY = geometry.outerScaleY,
                    )
                }
            }
            .then(wallpaperBacking)
            .drawWithContent {
                val scope = latestScope.value
                val openFraction = (1f + scope.relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
                val morphing = isDeterministicSharedContainerMorphing(
                    relativeDepth = scope.relativeDepth,
                    isRunning = scope.isRunning,
                    isRemoving = scope.isRemoving,
                    gestureActive = scope.gesture != null,
                )
                val sourceRect = if (morphing && origin.available) origin.resolveSourceRectInHost?.invoke() else null
                val hasValidSource = sourceRect != null && sourceRect.width > 1f && sourceRect.height > 1f
                val returning = if (morphing) bounds.isReturning(scope, openFraction) else false
                val sourceLayer = origin.capturedSourceLayer
                val sourceBitmap = origin.capturedSourceBitmap
                val layerWidth = origin.capturedSourceLayerWidth
                val layerHeight = origin.capturedSourceLayerHeight
                val hasActiveSourceOverlay = origin.hasActiveSourceOverlay(bounds)
                // open <= 0.001f 时下方真实来源卡片处于可见态，跳过飞行壳底色与源层绘制避免首末帧双层叠加或未捕获源层时闪黑；
                // 极近落位区（0.001..0.12）平滑过渡垫底色透明度，缓解液态玻璃与半透明卡片的质感明暗跳变。
                val skipGroundedOverlay = openFraction <= 0.001f
                if (morphing && openFraction < 0.999f && hasValidSource && !skipGroundedOverlay) {
                    val colorProgress = if (returning) {
                        openFraction
                    } else {
                        resolveSettingsContainerContentAlpha(openFraction, returning = false)
                    }
                    val backingAlpha = if (hasActiveSourceOverlay) {
                        smoothstep(0.001f, 0.12f, openFraction)
                    } else 1f
                    if (backingAlpha > 0.001f) {
                        val backingColor = lerp(origin.color, color, colorProgress)
                        drawRect(backingColor.copy(alpha = backingColor.alpha * backingAlpha))
                    }
                }
                drawContent()
                if (morphing && hasValidSource && hasActiveSourceOverlay && !skipGroundedOverlay) {
                    val alphas = resolveDeterministicSharedContainerAlpha(
                        open = openFraction,
                        returning = returning,
                        hasSourceOverlay = true,
                    )
                    if (alphas.sourceOverlayAlpha > 0.001f) {
                        val geometry = resolveDeterministicSharedContainerGeometry(
                            source = sourceRect!!,
                            destinationWidth = size.width,
                            destinationHeight = size.height,
                            open = openFraction,
                            scaleDestinationToWidth = scaleDestinationToWidth,
                        )
                        val uniformScaleX = size.width / layerWidth
                        val uniformScaleY = (geometry.outerScaleX / geometry.outerScaleY) * uniformScaleX
                        if (sourceBitmap != null) {
                            scale(scaleX = uniformScaleX, scaleY = uniformScaleY, pivot = Offset.Zero) {
                                drawImage(sourceBitmap, alpha = alphas.sourceOverlayAlpha)
                            }
                        } else if (sourceLayer != null) {
                            sourceLayer.alpha = alphas.sourceOverlayAlpha
                            scale(scaleX = uniformScaleX, scaleY = uniformScaleY, pivot = Offset.Zero) {
                                drawLayer(sourceLayer)
                            }
                            sourceLayer.alpha = 1f
                        }
                    }
                }
            }
            .graphicsLayer {
                val scope = latestScope.value
                val openFraction = (1f + scope.relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
                val morphing = isDeterministicSharedContainerMorphing(
                    relativeDepth = scope.relativeDepth,
                    isRunning = scope.isRunning,
                    isRemoving = scope.isRemoving,
                    gestureActive = scope.gesture != null,
                )
                if (!morphing) {
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                    return@graphicsLayer
                }
                val sourceRect = if (origin.available) origin.resolveSourceRectInHost?.invoke() else null
                if (sourceRect == null || sourceRect.width <= 1f || sourceRect.height <= 1f) {
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                    return@graphicsLayer
                }
                val returning = bounds.isReturning(scope, openFraction)
                val geometry = resolveDeterministicSharedContainerGeometry(
                    source = sourceRect,
                    destinationWidth = size.width,
                    destinationHeight = size.height,
                    open = openFraction,
                    scaleDestinationToWidth = scaleDestinationToWidth,
                )
                val hasSourceOverlay = origin.hasActiveSourceOverlay(bounds)
                val alphas = resolveDeterministicSharedContainerAlpha(
                    open = openFraction,
                    returning = returning,
                    hasSourceOverlay = hasSourceOverlay,
                )
                transformOrigin = geometry.innerTransformOrigin
                scaleX = geometry.innerScaleX
                scaleY = geometry.innerScaleY
                alpha = alphas.contentAlpha
            }
    }
    val state = remember(origin) {
        val isFreshPush = navScope.change == NavChange.Push && !navScope.isRemoving && navScope.relativeDepth <= 0f
        SeekableTransitionState(!isFreshPush && navScope.relativeDepth >= 0f)
    }
    val transition = rememberTransition(state, label = "Settings container")
    val originVisibility = transition.settingsVisibilityScope("Settings row", visibleWhenOpen = false)
    val pageVisibility = transition.settingsVisibilityScope("Settings page", visibleWhenOpen = true)
    val open = transition.animateFloat(
        transitionSpec = { tween(SETTINGS_CONTAINER_DURATION_MILLIS, easing = LinearEasing) },
        label = "Settings container openness",
    ) { if (it) 1f else 0f }
    val bounds = remember(origin, originVisibility, pageVisibility, open, transition, scaleDestinationToWidth) {
        SettingsSharedContainerBounds(
            origin,
            destinationZIndex,
            originVisibility,
            pageVisibility,
            open,
            latestScope,
            transition,
            scaleDestinationToWidth = scaleDestinationToWidth,
            deterministicGeometry = false,
        )
    }
    DisposableEffect(bounds) {
        origin.sharedBounds = bounds
        if (bounds.traceDraw) bounds.trace("attach") { "owner=${origin.owner}" }
        onDispose {
            if (origin.sharedBounds === bounds) origin.sharedBounds = null
        }
    }
    LaunchedEffect(state, bounds) {
        snapshotFlow {
            val scope = latestScope.value
            val fraction = (1f + scope.relativeDepth).coerceIn(0f, 1f)
            SharedContainerSeekSnapshot(
                fraction = fraction,
                running = scope.isRunning,
                removing = scope.isRemoving,
                destinationPlaced = bounds.destinationPlaced,
            )
        }.collect { (fraction, running, removing, destinationPlaced) ->
            when {
                !running && fraction == 1f && !removing -> state.snapTo(true)
                !running && fraction == 0f && removing -> state.snapTo(false)
                // 两端就位后先让共享转场在来源位置接管绘制，再跟随导航进度展开。
                destinationPlaced && (fraction < 1f || state.currentState != state.targetState) ->
                    state.seekTo(if (state.currentState) 1f - fraction else fraction, !state.currentState)
            }
        }
    }
    LaunchedEffect(bounds) {
        // 预测手势预览不移除页面。只在返回已提交且两端确实参加过共享布局时通知来源。
        snapshotFlow {
            latestScope.value.isRemoving && bounds.sourcePlaced && bounds.destinationPlaced
        }.first { it }
        if (!origin.returnLandingHandledByOwner) origin.returnLanding.request()
    }
    return Modifier
        .onGloballyPositioned {
            // 取目标页面的布局边界；共享叠层中每帧变化的矩形只负责裁剪插值。
            bounds.destinationCorners = resolveWindowCornerRadii(
                view,
                Rect(it.positionInWindow(), it.size.toSize()),
                isInMultiWindow = activity?.isInMultiWindowMode == true,
            )
        }
        .then(settingsSharedContainerBounds(bounds, pageColor = color, wallpaperBacking = wallpaperBacking))
}

@Composable
private fun Transition<Boolean>.settingsVisibilityScope(
    label: String,
    visibleWhenOpen: Boolean,
): AnimatedVisibilityScope {
    val visibility = createChildTransition(label) { open ->
        if (open == visibleWhenOpen) EnterExitState.Visible else EnterExitState.PreEnter
    }
    return remember(visibility) {
        object : AnimatedVisibilityScope {
            override val transition = visibility
        }
    }
}

private fun SettingsSharedContainerOrigin.hasActiveSourceOverlay(
    bounds: SettingsSharedContainerBounds,
): Boolean = sharedBounds === bounds &&
    (capturedSourceLayer != null || capturedSourceBitmap != null) &&
    capturedSourceLayerWidth > 1f &&
    capturedSourceLayerHeight > 1f

@Composable
internal fun settingsSharedContainerBounds(
    bounds: SettingsSharedContainerBounds?,
    pageColor: Color? = null,
    wallpaperBacking: Modifier = Modifier,
): Modifier {
    if (bounds == null) return Modifier
    val origin = bounds.origin
    val source = pageColor == null
    if (bounds.deterministicGeometry) {
        return Modifier
    }
    val sharedScope = LocalSettingsContainerSharedScope.current ?: return Modifier
    val transition = bounds.transition ?: return Modifier
    val visibilityScope = (if (source) bounds.sourceVisibility else bounds.pageVisibility) ?: return Modifier
    return with(sharedScope) {
        val config = remember(origin) {
            object : SharedTransitionScope.SharedContentConfig {
                override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                    get() = origin.available

                override val shouldKeepEnabledForOngoingAnimation: Boolean
                    get() = false
            }
        }
        val sharedContent = rememberSharedContentState(key = bounds, config = config)
        val isMorphing = transition.currentState != transition.targetState
        val side = if (source) "row" else "page"
        // 底色、页面和来源内容在共享叠层里使用同一组物理圆角。
        val clip = remember(bounds, sharedScope) { OverlayClip(bounds.clipShape) }
        val overlayClip = if (bounds.traceDraw) {
            val container = bounds
            object : SharedTransitionScope.OverlayClip {
                override fun getClipPath(
                    sharedContentState: SharedTransitionScope.SharedContentState,
                    bounds: Rect,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Path? {
                    container.trace("overlay_clip") { "side=$side rect=$bounds" }
                    return clip.getClipPath(sharedContentState, bounds, layoutDirection, density)
                }
            }
        } else clip
        // 先让关闭态的来源节点完成布局，Compose 才能从点击行采样上一帧边界。
        val sharedModifier = if (source || bounds.sourcePlaced) {
            Modifier.onGloballyPositioned {
                if (source) bounds.sourcePlaced = true else bounds.destinationPlaced = true
            }.sharedBounds(
                sharedContentState = sharedContent,
                animatedVisibilityScope = visibilityScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                // 导航进度已经包含缓动和松手的弹簧，边界只按这个进度取样。
                boundsTransform = { _, _ -> tween(SETTINGS_CONTAINER_DURATION_MILLIS, easing = LinearEasing) },
                resizeMode = if (source) RemeasureToBounds else if (bounds.scaleDestinationToWidth) {
                    scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
                } else {
                    scaleToBounds(ContentScale.None, Alignment.TopStart)
                },
                clipInOverlayDuringTransition = overlayClip,
                // 已落位的父容器留在页面层，避免二级设置转场把父页一同抬入叠层。
                renderInOverlayDuringTransition = isMorphing,
                // 页面叠层承载底色，来源文字在同一轮廓内从它上方退场。
                // 相同 zIndex 的叠放次序是隐式的，多级同时返回时按栈深度显式排序：
                // 深层容器盖住浅层容器，每层来源文字再压在自己的容器之上。
                zIndexInOverlay = if (source) bounds.destinationZIndex + 0.5f else bounds.destinationZIndex,
            )
        } else Modifier
        val beforeShared = if (bounds.traceDraw) {
            Modifier.drawWithContent {
                bounds.trace("before_shared") {
                    "side=$side size=$size match=${sharedContent.isMatchFound} active=$isTransitionActive"
                }
                drawContent()
            }
        } else Modifier
        val afterShared = if (bounds.traceDraw) {
            Modifier.drawWithContent {
                bounds.trace("content_record") { "side=$side size=$size" }
                drawContent()
            }
        } else Modifier
        // 来源按原行尺寸测量。缩放在绘制中完成，匹配成立时无需为静止的行切换放置图层。
        val sourceSizing = if (source) {
            Modifier
                .drawWithContent {
                    val sourceWidth = origin.coordinates?.size?.width
                    if (sharedContent.isMatchFound && sourceWidth != null && size.width != sourceWidth.toFloat()) {
                        scale(size.width / sourceWidth, pivot = Offset.Zero) {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        drawContent()
                    }
                }
                .skipToLookaheadSize { sharedContent.isMatchFound }
        } else Modifier
        beforeShared.then(sharedModifier)
            // 壁纸和渐变底色一同裁在共享容器圆角内，文字淡入时底图仍遮住后方页面。
            .then(wallpaperBacking)
            .then(if (pageColor == null) Modifier else Modifier.drawBehind {
                val open = bounds.open.value
                // 静止在起点时由父页绘制条目底色，避免在原位置再叠一层底色。
                if (open > 0f && isMorphing && isTransitionActive &&
                    sharedContent.isMatchFound && origin.available
                ) {
                    val colorProgress = if (transition.currentState) {
                        open
                    } else {
                        resolveSettingsContainerContentAlpha(open, returning = false)
                    }
                    // 展开初段仍显示来源内容，底色也沿用条目；子页开始淡入时再过渡到页面色。
                    drawRect(lerp(origin.color, pageColor, colorProgress))
                }
            })
            .then(sourceSizing)
            .graphicsLayer {
                alpha = when {
                    source -> if (sharedContent.isMatchFound && origin.available) {
                        1f - resolveSettingsContainerContentAlpha(bounds.open.value, transition.currentState)
                    } else 1f
                    origin.available -> resolveSettingsContainerContentAlpha(
                        open = bounds.open.value,
                        returning = transition.currentState,
                    )
                    // 来源条目已移出列表时保留返回手势，用淡出交接页面。
                    else -> (1f + bounds.navigationScope.value.relativeDepth).coerceIn(0f, 1f)
                }
                if (bounds.traceDraw) {
                    bounds.trace("content_alpha") { "side=$side alpha=$alpha" }
                }
            }
            .then(afterShared)
    }
}

internal fun settingsSharedContainerShape(
    origin: SettingsSharedContainerOrigin,
    open: State<Float>,
    destinationCorners: () -> WindowCornerRadii,
): AbsoluteRoundedCornerShape {
    fun corner(source: () -> Dp, destination: (WindowCornerRadii) -> Float) =
        object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float {
                val start = with(density) { source().toPx() }
                val end = destination(destinationCorners())
                return start + (end - start) * open.value
            }
        }
    return AbsoluteRoundedCornerShape(
        topLeft = corner({ origin.topRadius }) { it.topLeft },
        topRight = corner({ origin.topRadius }) { it.topRight },
        bottomRight = corner({ origin.bottomRadius }) { it.bottomRight },
        bottomLeft = corner({ origin.bottomRadius }) { it.bottomLeft },
    )
}
