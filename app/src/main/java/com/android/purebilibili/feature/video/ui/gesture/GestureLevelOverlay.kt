package com.android.purebilibili.feature.video.ui.gesture

import androidx.compose.ui.res.colorResource
import com.android.purebilibili.R
import android.os.SystemClock
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.key
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.video.ui.components.AnimatedGesturePercentText
import com.android.purebilibili.feature.video.ui.components.shouldTriggerGesturePercentHaptic
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import com.android.purebilibili.feature.video.ui.section.resolveVideoGestureMotionSpec
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Theme-native volume / brightness feedback:
 * - MD3: compact horizontal feedback at the top of the player
 * - iOS: centered frosted capsule
 * - MIUIX: native animated horizontal slider at the top of the player
 */
@Composable
fun BoxScope.GestureLevelOverlayHost(
    visible: Boolean,
    mode: VideoGestureMode,
    percent: Float,
    modifier: Modifier = Modifier
) {
    val kind = resolveGestureLevelKind(mode) ?: return
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val style = rememberGestureLevelOverlayStyle(playerChromeProfile.tabPresentation)
    val motionSpec = remember { resolveVideoGestureMotionSpec() }
    val brightnessColor = colorResource(R.color.player_brightness_accent)
    val colorScheme = MaterialTheme.colorScheme
    val miuixColorScheme = MiuixTheme.colorScheme
    val spec = remember(style, kind, percent, colorScheme, miuixColorScheme, brightnessColor) {
        resolveGestureLevelOverlaySpec(
            style = style,
            kind = kind,
            percent = percent,
            brightnessColor = brightnessColor,
            colorScheme = colorScheme,
            miuixContainerColor = miuixColorScheme.surfaceContainerHigh,
            miuixContentColor = miuixColorScheme.onSurface
        )
    }
    val progress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        animationSpec = tween(motionSpec.levelProgressDurationMillis),
        label = "gesture-level-progress"
    )
    val icon = resolveGestureLevelIcon(style = style, kind = kind, percent = percent)
    val percentInt = (percent.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
    GestureLevelStepHaptics(
        style = style,
        kind = kind,
        percent = percentInt,
        active = visible
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .align(spec.alignment)
            .then(
                when (style) {
                    GestureLevelOverlayStyle.Md3 -> Modifier.fillMaxSize()
                    GestureLevelOverlayStyle.Miuix -> Modifier
                    GestureLevelOverlayStyle.Ios -> Modifier.padding(horizontal = 22.dp)
                }
            )
            .zIndex(40f),
        enter = if (style == GestureLevelOverlayStyle.Md3) {
            fadeIn(animationSpec = tween(motionSpec.levelOverlayEnterFadeDurationMillis))
        } else fadeIn(animationSpec = tween(motionSpec.levelOverlayEnterFadeDurationMillis)) +
            scaleIn(
                initialScale = if (style == GestureLevelOverlayStyle.Miuix) 0.92f else 0.84f,
                animationSpec = tween(motionSpec.levelOverlayEnterTransformDurationMillis)
            ) +
            slideInVertically(
                initialOffsetY = { if (style == GestureLevelOverlayStyle.Ios) it / 8 else 0 },
                animationSpec = tween(motionSpec.levelOverlayEnterTransformDurationMillis)
            ),
        exit = if (style == GestureLevelOverlayStyle.Md3) {
            fadeOut(animationSpec = tween(motionSpec.levelOverlayExitDurationMillis))
        } else fadeOut(animationSpec = tween(motionSpec.levelOverlayExitDurationMillis)) +
            scaleOut(
                targetScale = 0.92f,
                animationSpec = tween(motionSpec.levelOverlayExitDurationMillis)
            ) +
            slideOutVertically(
                targetOffsetY = { if (style == GestureLevelOverlayStyle.Ios) -it / 10 else 0 },
                animationSpec = tween(motionSpec.levelOverlayExitDurationMillis)
            )
    ) {
        when (style) {
            GestureLevelOverlayStyle.Md3 -> Md3GestureLevelIndicator(
                spec = spec,
                icon = icon,
                progress = { percent.coerceIn(0f, 1f) },
                percent = percentInt,
                modifier = Modifier.fillMaxSize()
            )
            GestureLevelOverlayStyle.Ios -> IosGestureLevelCapsule(
                spec = spec,
                icon = icon,
                progress = progress,
                percent = percentInt
            )
            GestureLevelOverlayStyle.Miuix -> MiuixGestureLevelSlider(
                spec = spec,
                icon = icon,
                progress = percent.coerceIn(0f, 1f),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                    .padding(horizontal = 16.dp)
                    .padding(top = spec.topInsetDp.dp)
                    .width(200.dp)
            )
        }
    }
}

@Composable
private fun Md3GestureLevelIndicator(
    spec: GestureLevelOverlaySpec,
    icon: ImageVector,
    progress: () -> Float,
    percent: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.windowInsetsPadding(
            WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)
        )
    ) {
        val panelWidth = (maxWidth.value - 24f).coerceIn(0f, 200f).dp
        val panelModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = (maxHeight.value * 0.08f).coerceIn(16f, 48f).dp)
            .width(panelWidth)
            .clip(RoundedCornerShape(20.dp))
            .background(spec.containerColor)
            .semantics(mergeDescendants = true) {
                contentDescription = resolveGestureLevelLabel(spec.kind)
                stateDescription = "$percent%"
            }
        Row(
            modifier = panelModifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(imageVector = icon, contentDescription = null, tint = spec.iconTint, modifier = Modifier.size(20.dp))
            Md3GestureLevelRail(spec, progress, vertical = false, modifier = Modifier.weight(1f).height(4.dp))
            key(spec.kind) {
                Md3GesturePercentText(
                    percent = percent,
                    color = spec.textColor,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

/** Roll the level value in a fixed slot without shifting the progress rail. */
@Composable
private fun Md3GesturePercentText(
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Gesture feedback remains animated independently of card/navigation animation settings.
    val motionEnabled = !rememberSystemReduceMotion()
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (motionEnabled) {
            AnimatedContent(
                targetState = percent,
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    val direction = if (targetState > initialState) -1 else 1
                    ((fadeIn(tween(120)) + slideInVertically(AppMotionTokens.spatialSpec()) { it / 2 * direction })
                        togetherWith (fadeOut(tween(120)) + slideOutVertically(AppMotionTokens.spatialSpec()) { -it / 2 * direction }))
                        .using(SizeTransform(clip = false))
                },
                label = "md3-gesture-percent-roll"
            ) { value ->
                val blur by transition.animateFloat(
                    transitionSpec = { tween(120) },
                    label = "md3-gesture-percent-blur"
                ) { state -> if (state == EnterExitState.Visible) 0f else 1.5f }
                AppText(
                    text = value.toString(),
                    color = color,
                    style = textStyle,
                    modifier = Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded),
                    maxLines = 1,
                    tapToCopyEnabled = false
                )
            }
        } else {
            AppText(
                text = percent.toString(), color = color, style = textStyle,
                textAlign = TextAlign.Start,
                maxLines = 1, tapToCopyEnabled = false
            )
        }
    }
}

@Composable
private fun Md3GestureLevelRail(
    spec: GestureLevelOverlaySpec,
    progress: () -> Float,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = if (vertical) size.width else size.height
        val start = if (vertical) Offset(size.width / 2f, size.height - stroke / 2f)
            else Offset(stroke / 2f, size.height / 2f)
        val end = if (vertical) Offset(size.width / 2f, stroke / 2f)
            else Offset(size.width - stroke / 2f, size.height / 2f)
        drawLine(spec.trackColor, start, end, stroke, StrokeCap.Round)
        val level = progress().coerceIn(0f, 1f)
        if (level > 0f) drawLine(spec.fillColor, start, start + (end - start) * level, stroke, StrokeCap.Round)
    }
}

@Composable
private fun IosGestureLevelCapsule(
    spec: GestureLevelOverlaySpec,
    icon: ImageVector,
    progress: Float,
    percent: Int
) {
    val shape = AppShapes.container(ContainerLevel.Pill)
    AppSurface(
        shape = shape,
        color = spec.containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, spec.borderColor),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = spec.capsuleMinWidthDp.dp, max = 188.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GestureLevelIconSlot(
                icon = icon,
                tint = spec.accentColor,
                sizeDp = spec.iconSizeDp,
                glowColor = spec.accentColor.copy(alpha = 0.34f)
            )
            if (spec.showLabel) {
                AppText(
                    text = resolveGestureLevelLabel(spec.kind),
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
            AnimatedGesturePercentText(
                percent = percent,
                color = spec.textColor,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                fontWeight = FontWeight.Bold,
                label = "ios-gesture-level-percent",
                // Host-level GestureLevelStepHaptics already ticks for all themes.
                enableHaptic = false
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(spec.trackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    spec.fillColor.copy(alpha = 0.7f),
                                    spec.fillColor
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun MiuixGestureLevelSlider(
    spec: GestureLevelOverlaySpec,
    icon: ImageVector,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colors = MiuixTheme.colorScheme
    // This is feedback for the player's gesture, so disable slider input while
    // retaining a readable active appearance over video.
    val sliderColors = SliderDefaults.sliderColors(
        disabledForegroundColor = if (spec.kind == GestureLevelKind.Brightness) spec.fillColor
            else androidx.compose.ui.graphics.lerp(colors.primary, Color.White, 0.30f),
        disabledBackgroundColor = Color.White.copy(alpha = 0.14f),
        disabledThumbColor = Color.White
    )
    top.yukonga.miuix.kmp.basic.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xFF16181D).copy(alpha = 0.72f),
        contentColor = Color.White,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = progress,
                onValueChange = {},
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = resolveGestureLevelLabel(spec.kind)
                        stateDescription = "${(progress * 100).roundToInt()}%"
                    },
                enabled = false,
                height = 20.dp,
                colors = sliderColors
            )
        }
    }
}

@Composable
private fun GestureLevelIconSlot(
    icon: ImageVector,
    tint: Color,
    sizeDp: Int,
    glowColor: Color
) {
    val standardMotion = AppMotionTokens.standardSpec<Float>()
    val emphasizedMotion = AppMotionTokens.emphasizedSpec<Float>()
    val expressiveMotion = AppMotionTokens.expressiveSpec<Float>()
    Box(
        modifier = Modifier.size((sizeDp + 16).dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glowColor, CircleShape)
                .graphicsLayer { alpha = 0.9f }
        )
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(standardMotion) +
                    scaleIn(initialScale = 0.8f, animationSpec = emphasizedMotion))
                    .togetherWith(
                        fadeOut(expressiveMotion) +
                            scaleOut(
                                targetScale = 1.15f,
                                animationSpec = standardMotion
                            )
                    )
            },
            label = "gesture-level-icon"
        ) { target ->
            AppIcon(
                imageVector = target,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(sizeDp.dp)
            )
        }
    }
}

/**
 * Stepped haptics for all three skins while dragging volume / brightness.
 * MD3: throttled 5% ticks with boundary confirmation. iOS: every 5%.
 * MIUIX: every ~7% (closer to system stream steps).
 */
@Composable
private fun GestureLevelStepHaptics(
    style: GestureLevelOverlayStyle,
    kind: GestureLevelKind,
    percent: Int,
    active: Boolean
) {
    val haptic = rememberHapticFeedback()
    if (style == GestureLevelOverlayStyle.Md3) {
        val policy = remember(kind) { Md3GestureLevelHapticPolicy() }
        LaunchedEffect(active, percent, policy) {
            when (policy.update(percent, active, SystemClock.uptimeMillis())) {
                GestureLevelHapticFeedback.Tick -> haptic(HapticType.SELECTION)
                GestureLevelHapticFeedback.Boundary -> haptic(HapticType.LIGHT)
                null -> Unit
            }
        }
        return
    }
    var previousPercent by remember { mutableIntStateOf(percent) }
    val stepPercent = when (style) {
        GestureLevelOverlayStyle.Miuix -> 7
        GestureLevelOverlayStyle.Md3 -> 5
        GestureLevelOverlayStyle.Ios -> 5
    }
    LaunchedEffect(active, percent, style) {
        if (!active) {
            previousPercent = percent
            return@LaunchedEffect
        }
        if (
            shouldTriggerGesturePercentHaptic(
                previousPercent = previousPercent,
                currentPercent = percent,
                stepPercent = stepPercent
            )
        ) {
            haptic(
                when (style) {
                    GestureLevelOverlayStyle.Miuix -> HapticType.LIGHT
                    GestureLevelOverlayStyle.Md3 -> HapticType.SELECTION
                    GestureLevelOverlayStyle.Ios -> HapticType.SELECTION
                }
            )
        }
        previousPercent = percent
    }
}

/** Convenience for non-BoxScope hosts (fullscreen / bangumi / offline). */
@Composable
fun GestureLevelOverlayContent(
    mode: VideoGestureMode,
    percent: Float,
    style: GestureLevelOverlayStyle,
    modifier: Modifier = Modifier
) {
    val kind = resolveGestureLevelKind(mode) ?: return
    val motionSpec = remember { resolveVideoGestureMotionSpec() }
    val brightnessColor = colorResource(R.color.player_brightness_accent)
    val colorScheme = MaterialTheme.colorScheme
    val miuixColorScheme = MiuixTheme.colorScheme
    val spec = remember(style, kind, percent, colorScheme, miuixColorScheme, brightnessColor) {
        resolveGestureLevelOverlaySpec(
            style = style,
            kind = kind,
            percent = percent,
            brightnessColor = brightnessColor,
            colorScheme = colorScheme,
            miuixContainerColor = miuixColorScheme.surfaceContainerHigh,
            miuixContentColor = miuixColorScheme.onSurface
        )
    }
    val progress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        animationSpec = tween(motionSpec.levelProgressDurationMillis),
        label = "gesture-level-progress-content"
    )
    val icon = resolveGestureLevelIcon(style = style, kind = kind, percent = percent)
    val percentInt = (percent.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
    GestureLevelStepHaptics(
        style = style,
        kind = kind,
        percent = percentInt,
        active = true
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (style) {
            GestureLevelOverlayStyle.Md3 -> Md3GestureLevelIndicator(
                spec = spec,
                icon = icon,
                progress = { percent.coerceIn(0f, 1f) },
                percent = percentInt,
                modifier = Modifier.fillMaxSize()
            )
            GestureLevelOverlayStyle.Ios -> IosGestureLevelCapsule(
                spec = spec,
                icon = icon,
                progress = progress,
                percent = percentInt
            )
            GestureLevelOverlayStyle.Miuix -> MiuixGestureLevelSlider(
                spec = spec,
                icon = icon,
                progress = percent.coerceIn(0f, 1f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                    .padding(horizontal = 16.dp)
                    .padding(top = spec.topInsetDp.dp)
                    .width(200.dp)
            )
        }
    }
}
