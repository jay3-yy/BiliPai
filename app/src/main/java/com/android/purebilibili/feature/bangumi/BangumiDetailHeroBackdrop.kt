package com.android.purebilibili.feature.bangumi

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.feature.space.SpaceBannerFusionEffect
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun bangumiHeroCollapseFraction(firstVisibleIndex: Int, offsetPx: Int, heightPx: Float): Float =
    if (firstVisibleIndex > 0) 1f else (offsetPx / heightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** Reuse the UP-space banner's parallax, progressive blur and droplet/cutout fusion.
 * The native non-glass header stays on its original rendering and scroll behavior.
 */
@Composable
internal fun BangumiDetailHeroBackdrop(
    listState: LazyListState,
    height: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val reducedMotion = rememberSystemReduceMotion()
    val lowBudget = isLowBlurBudgetForced()
    val motionEnabled = LocalComponentMotionEnabled.current && !reducedMotion && !lowBudget
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val parallaxLimit = with(LocalDensity.current) { topInset.toPx().coerceAtLeast(0f) }
    LaunchedEffect(listState, glassEnabled, motionEnabled, heightPx) {
        if (!glassEnabled || !motionEnabled) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling && listState.firstVisibleItemIndex == 0) {
                val offset = listState.firstVisibleItemScrollOffset
                if (offset > 0 && offset < heightPx) {
                    // Settle to an endpoint, so the banner is never stranded as a half droplet.
                    listState.animateScrollToItem(if (offset >= heightPx / 2f) 1 else 0)
                }
            }
        }
    }
    if (glassEnabled) {
        SpaceBannerFusionEffect(
            collapseFractionProvider = {
                bangumiHeroCollapseFraction(listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset, heightPx)
            },
            scrollOffsetPxProvider = {
                if (listState.firstVisibleItemIndex > 0) heightPx
                else listState.firstVisibleItemScrollOffset.toFloat()
            },
            maxParallaxTranslationPx = parallaxLimit,
            scrollState = listState,
            modifier = modifier.graphicsLayer {
                translationY = if (motionEnabled) {
                    (listState.firstVisibleItemScrollOffset * 0.75f).coerceIn(0f, parallaxLimit)
                } else 0f
            },
            content = content,
        )
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
    }
}
