package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.motion.AppMotionTokens

private val EpisodeSortShiftDistance = 12.dp
private const val EpisodeSortStartAlpha = 0.86f

@Composable
internal fun isEpisodeSortMotionEnabled(): Boolean =
    !LocalSystemReduceMotion.current && LocalComponentMotionEnabled.current

internal fun resolveEpisodeSortTransitionDirection(descending: Boolean): Int =
    if (descending) -1 else 1

internal fun resolveCollectionSortTransitionDirection(sortMode: CollectionSortMode): Int =
    when (sortMode) {
        CollectionSortMode.DESCENDING -> -1
        CollectionSortMode.ASCENDING,
        CollectionSortMode.RECENT -> 1
    }

/**
 * 分P/合集排序切换时的列表方向微位移与淡入承接。
 * 仅在用户切换排序后触发，首帧打开与分区切换不播放；逐帧数值仅在 draw 阶段读取。
 */
@Composable
internal fun rememberEpisodeSortListTransitionModifier(
    sortKey: Any,
    scopeKey: Any,
    direction: Int,
    enabled: Boolean,
): Modifier {
    val progress = remember(scopeKey) { Animatable(1f) }
    var previousSortKey by remember(scopeKey) { mutableStateOf(sortKey) }
    var activeDirection by remember(scopeKey) { mutableIntStateOf(direction) }
    val transitionSpec = AppMotionTokens.emphasizedSpec<Float>()

    LaunchedEffect(sortKey, scopeKey, enabled) {
        if (sortKey == previousSortKey) return@LaunchedEffect
        previousSortKey = sortKey
        activeDirection = direction
        if (enabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = transitionSpec)
        } else {
            progress.snapTo(1f)
        }
    }

    if (!enabled) return Modifier
    return Modifier.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        if (p < 1f) {
            alpha = EpisodeSortStartAlpha + (1f - EpisodeSortStartAlpha) * p
            translationY = EpisodeSortShiftDistance.toPx() * activeDirection * (1f - p)
        }
    }
}

@Composable
internal fun LazyItemScope.episodeSortItemModifier(enabled: Boolean): Modifier {
    if (!enabled) return Modifier
    val fadeSpec = AppMotionTokens.standardSpec<Float>()
    val placementSpec = remember { AppMotionTokens.resolveSpatialSpec<IntOffset>() }
    return Modifier.animateItem(
        fadeInSpec = fadeSpec,
        placementSpec = placementSpec,
        fadeOutSpec = fadeSpec,
    )
}
