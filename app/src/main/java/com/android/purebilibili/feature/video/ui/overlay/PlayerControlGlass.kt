package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.LocalElasticPressEnabled
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled
import com.android.purebilibili.core.ui.components.LocalAppIconButtonGlassSurfaceProvided
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.resolveBiliPaiBottomBarContainerColor
import top.yukonga.miuix.kmp.blur.Backdrop

private val LocalPlayerGlassGroupSurface = compositionLocalOf { false }

private val LocalPlayerControlBackdrop = compositionLocalOf<Backdrop?> { null }
internal val LocalPlayerControlGlassEnabled = compositionLocalOf { false }
internal val LocalPlayerLevelGestureActive = compositionLocalOf { false }

/** The page owns capture; the controls only consume sibling content layers. */
internal val LocalPlayerPageBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
internal fun currentPlayerControlBackdrop(): Backdrop? = LocalPlayerControlBackdrop.current

@Composable
internal fun PlayerControlsBackdropHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = enabled && LocalAppThemeConfig.current.liquidGlassEnabled
    val backdrop = LocalPlayerPageBackdrop.current
    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalPlayerControlBackdrop provides backdrop.takeIf { glass },
            com.android.purebilibili.core.ui.components.LocalAppGlassMenuBackdrop provides backdrop.takeIf { glass },
            LocalPlayerControlGlassEnabled provides glass,
        ) { content() }
    }
}

internal const val PLAYER_GLASS_VISIBILITY_DURATION_MS = 300

/** The progress track retains pointer ownership; this thumb is visual only. */
@Composable
internal fun PlayerControlGlassThumb(size: Dp, dragging: Boolean, modifier: Modifier) {
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging) 1.18f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "playerGlassSeekThumb",
    )
    Box(
        modifier = modifier
            .size(size)
            .biliPaiFloatingDockShell(
                backdrop = LocalPlayerControlBackdrop.current,
                shape = CircleShape,
                containerColor = Color.White.copy(alpha = 0.35f),
                pressProgress = if (dragging) 1f else 0f,
                lensIntensity = 0.3f,
                liquidGlassTuning = LocalLiquidGlassRenderConfig.current.tuning,
                darkTheme = true,
                interactionLayerBlock = {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            ),
    )
}

/** 玻璃单图标采用等宽高槽位；文字胶囊与原生控件保留各自尺寸。 */
internal val PlayerGlassIconButtonSize = 34.dp

/** A single transformed subtree keeps the shell, labels and icons together. */
@Composable
internal fun PlayerControlGlassDock(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    // Opt in per control: keep readability in the translucent material, not its content.
    minimumReadabilityScrimAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = enabled && LocalAppThemeConfig.current.liquidGlassEnabled && !LocalPlayerGlassGroupSurface.current
    val tuning = LocalLiquidGlassRenderConfig.current.tuning
    val feedbackEnabled = enabled && LocalComponentMotionEnabled.current && LocalElasticPressEnabled.current
    val elastic = rememberAppElasticPressState(feedbackStrength = if (shape == CircleShape) 1f else 0.6f)
    val ownsDrag = remember { mutableStateOf(false) }
    val providedBackdrop = LocalPlayerControlBackdrop.current
    val shellSource = if (glass && providedBackdrop != null) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else null
    val shellTuning = if (
        glass && providedBackdrop != null &&
        minimumReadabilityScrimAlpha > tuning.contentReadabilityScrimAlpha
    ) {
        remember(tuning, minimumReadabilityScrimAlpha) {
            tuning.copy(contentReadabilityScrimAlpha = maxOf(tuning.contentReadabilityScrimAlpha, minimumReadabilityScrimAlpha))
        }
    } else tuning
    Box(
        modifier = modifier
            .appElasticPress(
                enabled = enabled,
                state = elastic,
                transformInBackdrop = glass && providedBackdrop != null,
                highlightShape = shape,
                // Observe the dock's own scroll gesture as well as its background drag.
                allowConsumedDrag = { feedbackEnabled || ownsDrag.value },
            )
            .then(if (enabled) Modifier.pointerInput(Unit) {
                // Controls claim drags inside their hit region; video gestures stay outside.
                // Descendant horizontal scrolling still gets first refusal in Main.
                detectDragGestures(
                    onDragStart = { ownsDrag.value = true },
                    onDragEnd = { ownsDrag.value = false },
                    onDragCancel = { ownsDrag.value = false },
                    onDrag = { change, _ -> change.consume() },
                )
            } else Modifier),
        // 内容层接收与原控件相同的最小尺寸，固定圆形按钮中的对齐与触控范围保持一致。
        propagateMinConstraints = glass && providedBackdrop != null,
    ) {
        if (glass) {
            // 外壳单独录制，正文与子控件只消费录制结果，避免背景源包含自身。
            Box(
                Modifier.matchParentSize()
                    .then(shellSource?.modifier ?: Modifier)
                    .then(if (providedBackdrop == null) {
                        Modifier.background(Color.Black.copy(alpha = maxOf(0.32f, minimumReadabilityScrimAlpha)), shape)
                    } else Modifier.biliPaiFloatingDockShell(
                        backdrop = providedBackdrop,
                        shape = shape,
                        containerColor = resolveBiliPaiBottomBarContainerColor(darkTheme = true, liquidGlassTuning = shellTuning),
                        pressProgress = 0f,
                        lensIntensity = 1f,
                        liquidGlassTuning = shellTuning,
                        darkTheme = true,
                        interactionLayerBlock = elastic.layerBlock,
                        interactionState = elastic,
                    )),
            )
        }
        val dockContent: @Composable BoxScope.() -> Unit = {
            CompositionLocalProvider(
                LocalElasticPressEnabled provides (!feedbackEnabled && LocalElasticPressEnabled.current),
                LocalAppTopBarIconDecorationEnabled provides false,
                // 播放器控件保留媒体表面的白色前景，不跟随外部页面明暗取色。
                LocalAppIconButtonGlassSurfaceProvided provides true,
                androidx.compose.material3.LocalContentColor provides Color.White,
                top.yukonga.miuix.kmp.theme.LocalContentColor provides Color.White,
            ) {
                com.android.purebilibili.core.ui.blur.AppGlassContainerContent(
                    enabled = glass && shellSource != null,
                    backdrop = shellSource?.takeIf { it.isReady }?.backdrop ?: providedBackdrop,
                    ownsDeformation = feedbackEnabled,
                ) { content() }
            }
        }
        if (glass && providedBackdrop != null) {
            Box(Modifier.graphicsLayer(elastic.layerBlock), content = dockContent)
        } else dockContent()
    }
}

/** Shared transport capsule; the seek host retains pointer ownership inside its own slot. */
@Composable
internal fun Modifier.playerControlGlassProgressDock(): Modifier {
    val backdrop = LocalPlayerControlBackdrop.current
    if (!LocalPlayerControlGlassEnabled.current || backdrop == null) return this
    return playerControlGlassSurface(backdrop, shape = RoundedCornerShape(24.dp))
}

/** Shared Miuix material for player chrome and gesture feedback; capture stays caller-owned. */
@Composable
internal fun Modifier.playerControlGlassSurface(
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(50),
    darkTheme: Boolean = true,
    containerColor: Color? = null,
): Modifier {
    if (!LocalAppThemeConfig.current.liquidGlassEnabled || backdrop == null) return this
    val tuning = LocalLiquidGlassRenderConfig.current.tuning
    return biliPaiFloatingDockShell(
        backdrop = backdrop,
        shape = shape,
        containerColor = containerColor ?: resolveBiliPaiBottomBarContainerColor(
            darkTheme = darkTheme,
            liquidGlassTuning = tuning,
        ),
        pressProgress = 0f,
        lensIntensity = 1f,
        liquidGlassTuning = tuning,
        darkTheme = darkTheme,
    )
}

/** A separate circular shell for inline transport controls. */
@Composable
internal fun Modifier.playerControlGlassCircularButton(): Modifier {
    if (!LocalPlayerControlGlassEnabled.current) return this
    return playerControlGlassSurface(LocalPlayerControlBackdrop.current, shape = CircleShape)
}

/** 竖屏相邻操作共用玻璃外壳；关闭分组时不增加布局或改变子控件参数。 */
@Composable
internal fun PlayerControlGlassGroup(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled && LocalAppThemeConfig.current.liquidGlassEnabled) {
        PlayerControlGlassDock(enabled = true, shape = RoundedCornerShape(50)) {
            CompositionLocalProvider(LocalPlayerGlassGroupSurface provides true) { content() }
        }
    } else content()
}
