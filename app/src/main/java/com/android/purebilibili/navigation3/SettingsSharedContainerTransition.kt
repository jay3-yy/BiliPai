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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

private const val SETTINGS_SHARED_DRAW_TAG = "SettingsSharedDraw"

/** 导航层保留手势和背景模糊，条目与页面的边界由 Compose 共享转场绘制。 */
internal class SettingsSharedContainerTransition(
    val origin: SettingsSharedContainerOrigin,
    // 目的地在返回栈中的深度；多级同时返回时用它给共享叠层排序，
    // 离场 entry 保留创建时的转场实例，深度也随之冻结。
    val destinationZIndex: Float = 0f,
    private val pageColor: () -> Color,
    private val realtimeBlurEnabled: Boolean,
    private val blurMotionTier: () -> MotionTier,
) : NavTransition {
    override val preparesEnterFrame = true
    override val handlesOwnClipping = true
    private val settle = NavSettleSpec.Spring(stiffness = 500f)
    override val motion = NavMotion(
        programmatic = settle,
        commit = settle,
        cancel = NavSettleSpec.Spring(stiffness = 700f),
    )

    override fun scrimFraction(scope: NavTransitionScope): Float = 0f

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
        if (!realtimeBlurEnabled) this else navigationBlurBlend {
            resolveNavigationBlurBlendFrame(
                progress = resolveSettingsContainerBlurProgress(scope.relativeDepth),
                motionTier = blurMotionTier(),
                isLightBackground = pageColor().luminance() > 0.5f,
            )
        }
}

@Composable
internal fun SettingsSharedTransitionLayout(enabled: Boolean, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSettingsContainerSharedScope provides if (enabled) this else null) {
            content()
        }
    }
}

internal class SettingsSharedContainerBounds(
    val origin: SettingsSharedContainerOrigin,
    val destinationZIndex: Float,
    val sourceVisibility: AnimatedVisibilityScope,
    val pageVisibility: AnimatedVisibilityScope,
    val open: State<Float>,
    val navigationScope: State<NavTransitionScope>,
    val transition: Transition<Boolean>,
) {
    var sourcePlaced by mutableStateOf(false)
    var destinationPlaced by mutableStateOf(false)
    var destinationCorners by mutableStateOf(WindowCornerRadii())
    val clipShape = settingsSharedContainerShape(origin, open) { destinationCorners }

    val traceDraw = BuildConfig.DEBUG && Log.isLoggable(SETTINGS_SHARED_DRAW_TAG, Log.DEBUG)

    fun trace(stage: String, detail: () -> String) {
        // 诊断读取不能使原本可复用的绘制指令跟随转场状态重新录制。
        Snapshot.withoutReadObservation {
            Log.d(
                SETTINGS_SHARED_DRAW_TAG,
                "frame_ms=${AnimationUtils.currentAnimationTimeMillis()} monotonic_ns=${System.nanoTime()} " +
                    "session=${System.identityHashCode(this)} stage=$stage open=${open.value} " +
                    "depth=${navigationScope.value.relativeDepth} " +
                    "state=${transition.currentState}->${transition.targetState} " +
                    "available=${origin.available} placed=$sourcePlaced/$destinationPlaced ${detail()}",
            )
        }
    }
}

@Composable
internal fun settingsSharedContainerDestination(
    origin: SettingsSharedContainerOrigin,
    navScope: NavTransitionScope,
    color: Color,
    destinationZIndex: Float = 0f,
): Modifier {
    val state = remember(origin) { SeekableTransitionState(navScope.relativeDepth >= 0f) }
    val transition = rememberTransition(state, label = "Settings container")
    val originVisibility = transition.settingsVisibilityScope("Settings row", visibleWhenOpen = false)
    val pageVisibility = transition.settingsVisibilityScope("Settings page", visibleWhenOpen = true)
    val open = transition.animateFloat(
        transitionSpec = { tween(SETTINGS_CONTAINER_DURATION_MILLIS, easing = LinearEasing) },
        label = "Settings container openness",
    ) { if (it) 1f else 0f }
    val latestScope = rememberUpdatedState(navScope)
    val bounds = remember(origin, originVisibility, pageVisibility, open, transition) {
        SettingsSharedContainerBounds(
            origin,
            destinationZIndex,
            originVisibility,
            pageVisibility,
            open,
            latestScope,
            transition,
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
            Triple(fraction, scope.isRunning, bounds.destinationPlaced)
        }.collect { (fraction, running, destinationPlaced) ->
            when {
                !running && fraction == 1f -> state.snapTo(true)
                !running && fraction == 0f && latestScope.value.isRemoving -> state.snapTo(false)
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
        origin.returnLanding.request()
    }
    val view = LocalView.current
    val activity = LocalActivity.current
    return Modifier
        .onGloballyPositioned {
            // 取目标页面的布局边界；共享叠层中每帧变化的矩形只负责裁剪插值。
            bounds.destinationCorners = resolveWindowCornerRadii(
                view,
                Rect(it.positionInWindow(), it.size.toSize()),
                isInMultiWindow = activity?.isInMultiWindowMode == true,
            )
        }
        .then(settingsSharedContainerBounds(bounds, pageColor = color))
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

@Composable
internal fun settingsSharedContainerBounds(
    bounds: SettingsSharedContainerBounds?,
    pageColor: Color? = null,
): Modifier {
    val sharedScope = LocalSettingsContainerSharedScope.current ?: return Modifier
    if (bounds == null) return Modifier
    val origin = bounds.origin
    val source = pageColor == null
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
        val isMorphing = bounds.transition.currentState != bounds.transition.targetState
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
                animatedVisibilityScope = if (source) bounds.sourceVisibility else bounds.pageVisibility,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                // 导航进度已经包含缓动和松手的弹簧，边界只按这个进度取样。
                boundsTransform = { _, _ -> tween(SETTINGS_CONTAINER_DURATION_MILLIS, easing = LinearEasing) },
                resizeMode = if (source) RemeasureToBounds else
                    scaleToBounds(ContentScale.None, Alignment.TopStart),
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
            .then(if (pageColor == null) Modifier else Modifier.drawBehind {
                val open = bounds.open.value
                // 静止在起点时由父页绘制条目底色，避免在原位置再叠一层底色。
                if (open > 0f && isMorphing && isTransitionActive &&
                    sharedContent.isMatchFound && origin.available
                ) {
                    val colorProgress = if (bounds.transition.currentState) {
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
                        1f - resolveSettingsContainerContentAlpha(bounds.open.value, bounds.transition.currentState)
                    } else 1f
                    origin.available -> resolveSettingsContainerContentAlpha(
                        open = bounds.open.value,
                        returning = bounds.transition.currentState,
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
