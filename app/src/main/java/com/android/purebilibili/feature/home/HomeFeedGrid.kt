package com.android.purebilibili.feature.home

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid
import com.android.purebilibili.core.ui.components.FeedVerticalStaggeredGrid
import com.android.purebilibili.core.ui.components.videoListItemModifier as nativeVideoListItemModifier
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.motion.AppMotionEasing

/** Home-only content contract: header spans and per-card identity are shared by both layouts. */
internal interface HomeFeedGridScope {
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        fullLine: Boolean = false,
        content: @Composable HomeFeedItemScope.() -> Unit,
    )
    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        contentType: (Int) -> Any? = { null },
        content: @Composable HomeFeedItemScope.(Int) -> Unit,
    )
}

internal interface HomeFeedItemScope {
    @Composable fun videoListItemModifier(
        enabled: Boolean = true,
        dissolveReflowEnabled: Boolean = false,
        dissolveReflowActive: Boolean = false,
    ): Modifier
    fun animateItemModifier(): Modifier
}

private val homeDissolvePlacementSpec = tween<IntOffset>(240, easing = AppMotionEasing.M1Decelerate)
private val homeImmediatePlacementSpec = snap<IntOffset>()

/** Keep lazy placement identity ready for deletion even when ordinary placement is disabled. */
@Composable
private fun homeFeedPlacementOverride(
    enabled: Boolean,
    dissolveReflowEnabled: Boolean,
    dissolveReflowActive: Boolean,
): FiniteAnimationSpec<IntOffset>? = when {
    dissolveReflowEnabled && dissolveReflowActive -> homeDissolvePlacementSpec
    dissolveReflowEnabled && !(enabled && LocalAppThemeConfig.current.uiEntranceAnimationEnabled) ->
        homeImmediatePlacementSpec
    else -> null
}

private class RegularItemScope(val scope: LazyGridItemScope) : HomeFeedItemScope {
    @Composable override fun videoListItemModifier(
        enabled: Boolean, dissolveReflowEnabled: Boolean, dissolveReflowActive: Boolean,
    ): Modifier = with(scope) {
        val spec = homeFeedPlacementOverride(enabled, dissolveReflowEnabled, dissolveReflowActive)
        if (spec != null) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spec)
        else nativeVideoListItemModifier(enabled)
    }
    override fun animateItemModifier(): Modifier = with(scope) { Modifier.animateItem() }
}

private class WaterfallItemScope(val scope: LazyStaggeredGridItemScope) : HomeFeedItemScope {
    @Composable override fun videoListItemModifier(
        enabled: Boolean, dissolveReflowEnabled: Boolean, dissolveReflowActive: Boolean,
    ): Modifier = with(scope) {
        val spec = homeFeedPlacementOverride(enabled, dissolveReflowEnabled, dissolveReflowActive)
        if (spec != null) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spec)
        else nativeVideoListItemModifier(enabled)
    }
    override fun animateItemModifier(): Modifier = with(scope) { Modifier.animateItem() }
}

private class RegularGridScope(val scope: LazyGridScope) : HomeFeedGridScope {
    override fun item(key: Any?, contentType: Any?, fullLine: Boolean, content: @Composable HomeFeedItemScope.() -> Unit) {
        scope.item(
            key = key,
            span = if (fullLine) ({ GridItemSpan(maxLineSpan) }) else null,
            contentType = contentType,
        ) {
            val itemScope = remember(this) { RegularItemScope(this) }
            content(itemScope)
        }
    }
    override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, content: @Composable HomeFeedItemScope.(Int) -> Unit) {
        scope.items(count = count, key = key, contentType = contentType) { index ->
            val itemScope = remember(this) { RegularItemScope(this) }
            content(itemScope, index)
        }
    }
}

private class WaterfallGridScope(val scope: LazyStaggeredGridScope) : HomeFeedGridScope {
    override fun item(key: Any?, contentType: Any?, fullLine: Boolean, content: @Composable HomeFeedItemScope.() -> Unit) {
        scope.item(
            key = key,
            contentType = contentType,
            span = if (fullLine) StaggeredGridItemSpan.FullLine else null,
        ) {
            val itemScope = remember(this) { WaterfallItemScope(this) }
            content(itemScope)
        }
    }
    override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, content: @Composable HomeFeedItemScope.(Int) -> Unit) {
        scope.items(count = count, key = key, contentType = contentType) { index ->
            val itemScope = remember(this) { WaterfallItemScope(this) }
            content(itemScope, index)
        }
    }
}

@Composable
internal fun HomeFeedGrid(
    state: HomeFeedGridState,
    columns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalItemSpacing: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: HomeFeedGridScope.() -> Unit,
) {
    if (state.regularLayout) {
        val direction = LocalLayoutDirection.current
        val topInset = contentPadding.calculateTopPadding()
        val hasInsetItem = topInset > verticalItemSpacing && topInset > 0.dp
        AppLazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state.regular,
            modifier = modifier,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(direction),
                end = contentPadding.calculateEndPadding(direction),
                top = if (hasInsetItem) 0.dp else topInset,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(verticalItemSpacing),
            horizontalArrangement = horizontalArrangement,
        ) {
            if (hasInsetItem) {
                item(key = "feed_chrome_top_inset", contentType = "feed_chrome_top_inset", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(topInset - verticalItemSpacing))
                }
            }
            content(RegularGridScope(this))
        }
    } else {
        FeedVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state.waterfall,
            modifier = modifier,
            contentPadding = contentPadding,
            verticalItemSpacing = verticalItemSpacing,
            horizontalArrangement = horizontalArrangement,
        ) { content(WaterfallGridScope(this)) }
    }
}
