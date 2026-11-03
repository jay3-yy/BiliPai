package com.android.purebilibili.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.core.events.BrandSuccessEvents
import com.android.purebilibili.core.events.BrandSuccessFeedback
import com.android.purebilibili.core.events.BrandSuccessKind
import com.android.purebilibili.core.ui.components.AppText
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Small, non-modal feedback. Only collect while the app can visibly present it. */
@Composable
fun BoxScope.BrandSuccessFeedbackHost(
    enabled: Boolean = true,
    bottomContentInset: Dp = 0.dp,
    extraBottomClearance: Dp = 0.dp
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var feedback by remember { mutableStateOf<BrandSuccessFeedback?>(null) }
    LaunchedEffect(lifecycleOwner, enabled) {
        feedback = null
        if (!enabled) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                BrandSuccessEvents.events.collectLatest { event ->
                    feedback = event
                    // A final guard also dismisses a feedback slot that failed to lay out.
                    delay(2_600L)
                    if (feedback?.id == event.id) feedback = null
                }
            } finally {
                feedback = null
            }
        }
    }
    val current = feedback ?: return
    val safePadding = WindowInsets.safeDrawing.union(WindowInsets.ime).asPaddingValues()
    val bottomPadding = maxOf(safePadding.calculateBottomPadding(), bottomContentInset + extraBottomClearance) + 16.dp
    val window = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.windowSizeClass
    val availableHeight = (window.heightDp - bottomPadding - safePadding.calculateTopPadding()).value
    if (availableHeight < 120f) return // Leave room for input when the keyboard occupies a tiny window.
    val availableWidth = window.widthDp.value - safePadding.calculateLeftPadding(LocalLayoutDirection.current).value -
        safePadding.calculateRightPadding(LocalLayoutDirection.current).value - 56f
    if (availableWidth < 64f) return
    val illustrationSize = minOf((availableHeight - 72f).coerceIn(64f, 144f), availableWidth).dp
    // Capture once per event: sheet dismissal or later layout reports must not move
    // an already visible card from the fallback corner to a newly available anchor.
    val anchorBounds = remember(current.id) {
        when (current.kind) {
            BrandSuccessKind.FAVORITE ->
                com.android.purebilibili.feature.video.ui.feedback.FavoriteActionAnchorRegistry.favoriteIcon.bounds
            BrandSuccessKind.FOLLOW, BrandSuccessKind.UNFOLLOW ->
                com.android.purebilibili.feature.video.ui.feedback.FollowActionAnchorRegistry.followButton.bounds
            BrandSuccessKind.DOWNLOAD ->
                com.android.purebilibili.feature.video.ui.feedback.DownloadActionAnchorRegistry.downloadIcon.bounds
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val topSafePx = with(density) { safePadding.calculateTopPadding().toPx() }
    val bottomSafePx = with(density) { safePadding.calculateBottomPadding().toPx() }
    val endSafePx = with(density) { safePadding.calculateEndPadding(LocalLayoutDirection.current).toPx() }
    val anchorGapPx = with(density) { 8.dp.toPx() }
    Column(
        modifier = Modifier
            .then(
                if (anchorBounds != null) {
                    Modifier
                        .align(Alignment.TopStart)
                        .layout { measurable, constraints ->
                            // Measure and position together: the first drawn frame already
                            // uses the real card size, with no size-state correction later.
                            val placeable = measurable.measure(constraints)
                            val minX = endSafePx.roundToInt()
                            val maxX = (screenWidthPx - endSafePx - placeable.width)
                                .roundToInt().coerceAtLeast(minX)
                            val minY = topSafePx.roundToInt()
                            val maxY = (screenHeightPx - bottomSafePx - placeable.height)
                                .roundToInt().coerceAtLeast(minY)
                            val x = (anchorBounds.center.x - placeable.width / 2f)
                                .roundToInt().coerceIn(minX, maxX)
                            val y = (anchorBounds.top - placeable.height - anchorGapPx)
                                .roundToInt().coerceIn(minY, maxY)
                            layout(placeable.width, placeable.height) {
                                placeable.place(x, y)
                            }
                        }
                } else {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = safePadding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                            bottom = bottomPadding
                        )
                }
            )
            .widthIn(max = 192.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BlueSnowMaidAnimation(
            animation = when (current.kind) {
                BrandSuccessKind.FAVORITE -> MaidAnimation.FAVORITE_SAVED
                BrandSuccessKind.DOWNLOAD -> MaidAnimation.DOWNLOAD_COMPLETE
                BrandSuccessKind.FOLLOW -> MaidAnimation.FOLLOW_SUCCESS
                BrandSuccessKind.UNFOLLOW -> MaidAnimation.UNFOLLOW_COMPLETE
            },
            modifier = Modifier.size(illustrationSize),
            replayKey = current.id,
            completionHoldDurationMs = if (current.kind == BrandSuccessKind.UNFOLLOW) 500L else 800L,
            onFinished = { if (feedback?.id == current.id) feedback = null }
        )
        val completionText = when (current.kind) {
            BrandSuccessKind.FAVORITE -> "收藏成功"
            BrandSuccessKind.DOWNLOAD -> "缓存完成"
            BrandSuccessKind.FOLLOW, BrandSuccessKind.UNFOLLOW -> null
        }
        if (completionText != null) {
            AppText(
                text = completionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                tapToCopyEnabled = false
            )
        }
        current.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            AppText(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false
            )
        }
    }
}
