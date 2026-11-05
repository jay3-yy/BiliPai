package com.android.purebilibili.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appActionClickable

/** 首页仅传递提示状态句柄，显示和消失只更新提示本身。 */
@Composable
internal fun HomeRefreshDeltaTip(
    text: State<String?>,
    listTopPadding: Dp,
    isLiquidGlassEnabled: Boolean,
    isHeaderBlurEnabled: Boolean,
    isBottomBarBlurEnabled: Boolean,
) {
    val refreshDeltaTipText = text.value
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    val overlayChromeColors = rememberHomeGlassChromeColors(
        glassEnabled = isLiquidGlassEnabled,
        blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
    )
    val refreshTipAppearance = remember(isLiquidGlassEnabled, isHeaderBlurEnabled, isBottomBarBlurEnabled) {
        resolveHomeRefreshTipAppearance(
            liquidGlassEnabled = isLiquidGlassEnabled,
            blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
        )
    }
    AnimatedVisibility(
        visible = refreshDeltaTipText != null,
        enter = fadeIn(animationSpec = tween(overlayMotionSpec.refreshTipEnterFadeDurationMillis)) + slideInVertically(
            animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
            initialOffsetY = { -it / 2 }
        ),
        exit = fadeOut(animationSpec = tween(overlayMotionSpec.refreshTipExitFadeDurationMillis)) + slideOutVertically(
            animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
            targetOffsetY = { -it / 2 }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = listTopPadding + AppSpacingTokens.Small)
            .zIndex(90f)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Pill),
                color = if (refreshTipAppearance.surfaceStyle == HomeRefreshTipSurfaceStyle.PLAIN) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    overlayChromeColors.containerColor
                },
                border = if (refreshTipAppearance.borderWidthDp > 0f) {
                    BorderStroke(
                        refreshTipAppearance.borderWidthDp.dp,
                        overlayChromeColors.borderColor
                    )
                } else {
                    null
                },
                tonalElevation = refreshTipAppearance.tonalElevationDp.dp,
                shadowElevation = refreshTipAppearance.shadowElevationDp.dp
            ) {
                AppText(
                    text = refreshDeltaTipText.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.padding(horizontal = AppSpacingTokens.Medium + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Small)
                )
            }
        }
    }
}

/** 撤销可用状态与听视频横条的变化只影响悬浮入口。 */
@Composable
internal fun HomeRefreshUndoHost(
    viewModel: HomeViewModel,
    currentCategory: HomeCategory,
    refreshNewItemsKey: Long,
    homeListBottomPadding: Dp,
    oldContentLocatorVisible: Boolean,
    overlayPillColors: HomeGlassResolvedColors,
) {
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val nowPlayingBarOverlayVisible by com.android.purebilibili.feature.audio.player
        .AudioNowPlayingSession.barOverlayVisible
        .collectAsStateWithLifecycle()
    HomeRefreshUndoOverlay(
        undoAvailable = undoAvailable,
        nowPlayingBarOverlayVisible = nowPlayingBarOverlayVisible,
        isRecommendation = currentCategory == HomeCategory.RECOMMEND,
        refreshNewItemsKey = refreshNewItemsKey,
        homeListBottomPadding = homeListBottomPadding,
        oldContentLocatorVisible = oldContentLocatorVisible,
        overlayPillColors = overlayPillColors,
        onUndo = viewModel::undoRefresh,
    )
}

@Composable
private fun HomeRefreshUndoOverlay(
    undoAvailable: Boolean,
    nowPlayingBarOverlayVisible: Boolean,
    isRecommendation: Boolean,
    refreshNewItemsKey: Long,
    homeListBottomPadding: Dp,
    oldContentLocatorVisible: Boolean,
    overlayPillColors: HomeGlassResolvedColors,
    onUndo: () -> Unit,
) {
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    //  与「定位上次刷新」胶囊共用同一底部锚点：跟随听视频横条上浮，且在定位胶囊
    //  可见时再抬一个胶囊位（胶囊高约 36dp + 8dp 间距），避免两者互相遮挡。
    val undoVisible = undoAvailable && isRecommendation
    //  手动关闭撤销胶囊；下次撤销可用时自动复位
    var undoDismissed by remember(refreshNewItemsKey) { mutableStateOf(false) }
    var undoDissolving by remember(refreshNewItemsKey) { mutableStateOf(false) }
    val undoPillBottomPadding = homeListBottomPadding + AppSpacingTokens.Medium +
        120.dp +
        (if (nowPlayingBarOverlayVisible) 76.dp else 0.dp) +
        (if (oldContentLocatorVisible) 52.dp else 0.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(95f),
        contentAlignment = Alignment.BottomEnd
    ) {
        AnimatedVisibility(
            visible = (undoVisible || undoDissolving) && !undoDismissed,
            enter = fadeIn(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideInVertically(
                animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                initialOffsetY = { it }
            ),
            exit = if (undoDismissed) androidx.compose.animation.ExitTransition.None else
                fadeOut(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideOutVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    targetOffsetY = { it }
                ),
            modifier = Modifier.padding(
                end = AppSpacingTokens.Large,
                bottom = undoPillBottomPadding,
            )
        ) {
            DissolvableVideoCard(
                isDissolving = undoDissolving,
                onDissolveComplete = {
                    undoDismissed = true
                    undoDissolving = false
                },
                cardId = "undo-refresh-$refreshNewItemsKey",
                preset = com.android.purebilibili.core.ui.animation.DissolveAnimationPreset.TELEGRAM_FAST,
                collapseAfterDissolve = false,
                publishGlobalDissolveState = false,
                keepInvisibleAfterDissolve = true,
            ) {
                com.android.purebilibili.feature.home.components.HomeOverlayPillButton(
                    overlayPillColors = overlayPillColors,
                    onClick = { if (!undoDissolving) onUndo() },
                    modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                        }
                    },
                ) {
                    AppText(
                        text = "⟲",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = "撤销刷新",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .appActionClickable(enabled = !undoDissolving) { undoDissolving = true },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
