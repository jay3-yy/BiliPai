package com.android.purebilibili.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.BackToTopSettingsStore
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.LocalFloatingChromeBackdrop
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.roundToInt

/**
 * App-level back-to-top button with real backdrop glass, long-press drag positioning,
 * position memorization, and a safe opaque fallback.
 */
@Composable
fun AppLiquidGlassBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalFloatingChromeBackdrop.current,
    contentDescription: String = "回到顶部",
    draggable: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val glassActive = LocalAppThemeConfig.current.liquidGlassEnabled && !isLowBlurBudgetForced()
    val localBackdrop = if (glassActive && backdrop == null) rememberLayerBackdrop() else null
    val effectiveBackdrop = backdrop ?: localBackdrop
    val elasticState = rememberAppElasticPressState(visualSize = 48.dp)
    val glassMotion = glassActive && effectiveBackdrop != null
    val elasticEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val dockColor = AppSurfaceTokens.surfaceContainerHigh()

    val persistedOffsetDp by remember(context) {
        BackToTopSettingsStore.getCustomOffsetDp(context)
    }.collectAsStateWithLifecycle(initialValue = BackToTopSettingsStore.getCachedOffsetDp())

    var dragOffsetPx by remember {
        mutableStateOf(
            with(density) {
                Offset(
                    BackToTopSettingsStore.getCachedOffsetDp().first.dp.toPx(),
                    BackToTopSettingsStore.getCachedOffsetDp().second.dp.toPx(),
                )
            }
        )
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragJustFinished by remember { mutableStateOf(false) }

    LaunchedEffect(persistedOffsetDp) {
        if (!isDragging) {
            dragOffsetPx = with(density) {
                Offset(persistedOffsetDp.first.dp.toPx(), persistedOffsetDp.second.dp.toPx())
            }
        }
    }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val minOffsetX = -(screenWidthPx - with(density) { 72.dp.toPx() })
    val maxOffsetX = with(density) { 12.dp.toPx() }
    val minOffsetY = -(screenHeightPx * 0.72f)
    val maxOffsetY = with(density) { 24.dp.toPx() }

    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1.0f,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "back_to_top_drag_scale",
    )
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.88f else 1.0f,
        label = "back_to_top_drag_alpha",
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.offset(y = (-28).dp),
        enter = fadeIn(animationSpec = AppMotionTokens.standardSpec()) +
            scaleIn(animationSpec = AppMotionTokens.standardSpec(), initialScale = 0.92f),
        exit = fadeOut(animationSpec = AppMotionTokens.expressiveSpec()) +
            scaleOut(animationSpec = AppMotionTokens.expressiveSpec(), targetScale = 0.92f),
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = dragOffsetPx.x.roundToInt(),
                        y = dragOffsetPx.y.roundToInt(),
                    )
                }
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                    alpha = dragAlpha
                }
                .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (glassActive && backdrop == null && localBackdrop != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0f)
                        .layerBackdrop(localBackdrop)
                        .background(AppSurfaceTokens.background())
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Short drags deform the whole glass button and spring back on release.
                    // The long-press handler below retains ownership of saved positioning.
                    .appElasticPress(
                        dragEnabled = true,
                        state = elasticState,
                        transformInBackdrop = glassMotion,
                    )
                    .then(
                        if (glassActive && effectiveBackdrop != null) {
                            Modifier.biliPaiFloatingDockShell(
                                backdrop = effectiveBackdrop,
                                interactionLayerBlock = elasticState.layerBlock,
                                interactionState = elasticState,
                                containerColor = dockColor,
                                pressProgress = if (isDragging) 0.15f else 0f,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier.background(dockColor, CircleShape)
                        }
                    )
                    .clip(CircleShape)
                    .then(
                        if (draggable) {
                            Modifier.pointerInput(minOffsetX, maxOffsetX, minOffsetY, maxOffsetY) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isDragging = true
                                        dragJustFinished = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx = Offset(
                                            x = (dragOffsetPx.x + dragAmount.x).coerceIn(minOffsetX, maxOffsetX),
                                            y = (dragOffsetPx.y + dragAmount.y).coerceIn(minOffsetY, maxOffsetY),
                                        )
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val finalXDp = with(density) { dragOffsetPx.x.toDp().value }
                                        val finalYDp = with(density) { dragOffsetPx.y.toDp().value }
                                        BackToTopSettingsStore.updateCachedOffset(finalXDp, finalYDp)
                                        scope.launch(Dispatchers.IO) {
                                            BackToTopSettingsStore.setCustomOffsetDp(context, finalXDp, finalYDp)
                                        }
                                        scope.launch {
                                            delay(250)
                                            dragJustFinished = false
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        val finalXDp = with(density) { dragOffsetPx.x.toDp().value }
                                        val finalYDp = with(density) { dragOffsetPx.y.toDp().value }
                                        BackToTopSettingsStore.updateCachedOffset(finalXDp, finalYDp)
                                        scope.launch(Dispatchers.IO) {
                                            BackToTopSettingsStore.setCustomOffsetDp(context, finalXDp, finalYDp)
                                        }
                                        scope.launch {
                                            delay(250)
                                            dragJustFinished = false
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = if (elasticEnabled) null else ripple(),
                        onClick = {
                            if (!isDragging && !dragJustFinished) {
                                onClick()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = rememberAppChevronUpIcon(),
                    contentDescription = contentDescription,
                    tint = com.android.purebilibili.core.ui.AppContentColors.selectionAccent(glassActive),
                )
            }
        }
    }
}
