package com.android.purebilibili.feature.bangumi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonPulseState

@Composable
internal fun BangumiPosterSkeletonItem(
    modifier: Modifier = Modifier,
    blockColor: (() -> Color)? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val color = blockColor ?: rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState(animated = animationGate.active))
    Column(modifier = modifier.then(animationGate.modifier).fillMaxWidth()) {
        ContentSkeletonBlock(
            color = color,
            shape = AppShapes.container(ContainerLevel.Chip),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BANGUMI_POSTER_ASPECT_RATIO),
        )
        Spacer(modifier = Modifier.height(7.dp))
        ContentSkeletonBlock(
            color = color,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(14.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        ContentSkeletonBlock(
            color = color,
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .height(12.dp),
        )
    }
}

@Composable
internal fun BangumiPosterSkeletonRow(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
    blockColor: (() -> Color)? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val color = blockColor ?: rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState(animated = itemCount > 0 && animationGate.active))
    LazyRow(
        modifier = modifier.then(animationGate.modifier).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(List(itemCount.coerceAtLeast(0)) { it }, key = { "bangumi_poster_row_skeleton_$it" }) {
            BangumiPosterSkeletonItem(
                modifier = Modifier.width(104.dp),
                blockColor = color,
            )
        }
    }
}

@Composable
internal fun BangumiPosterGridSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val pulse = rememberContentSkeletonPulseState(animated = itemCount > 0 && animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    BoxWithConstraints(modifier = modifier.then(animationGate.modifier).fillMaxWidth()) {
        val spacing = 10.dp
        val columnCount = ((maxWidth.value + spacing.value) / (112f + spacing.value))
            .toInt()
            .coerceAtLeast(1)
        val rowCount = (itemCount.coerceAtLeast(0) + columnCount - 1) / columnCount
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(rowCount) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    repeat(columnCount) { column ->
                        if (row * columnCount + column < itemCount) {
                            BangumiPosterSkeletonItem(
                                modifier = Modifier.weight(1f),
                                blockColor = blockColor,
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BangumiTimelineSkeleton(modifier: Modifier = Modifier) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    Column(
        modifier = modifier.then(animationGate.modifier).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) {
                ContentSkeletonBlock(
                    color = blockColor,
                    shape = AppShapes.container(ContainerLevel.Pill),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
            }
        }
        BangumiPosterSkeletonRow(itemCount = 4, blockColor = blockColor)
    }
}

@Composable
internal fun BangumiFilterSkeleton(modifier: Modifier = Modifier) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    Column(
        modifier = modifier.then(animationGate.modifier).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .width(38.dp)
                        .height(14.dp),
                )
                repeat(3) {
                    ContentSkeletonBlock(
                        color = blockColor,
                        shape = AppShapes.container(ContainerLevel.Chip),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BangumiFollowManagerSkeleton(modifier: Modifier = Modifier) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(310.dp),
        modifier = modifier.then(animationGate.modifier).fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(6, key = { "bangumi_follow_manager_skeleton_$it" }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContentSkeletonBlock(
                    color = blockColor,
                    shape = AppShapes.container(ContainerLevel.Chip),
                    modifier = Modifier
                        .width(104.dp)
                        .aspectRatio(BANGUMI_POSTER_ASPECT_RATIO),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ContentSkeletonBlock(color = blockColor, modifier = Modifier.fillMaxWidth(0.88f).height(16.dp))
                    ContentSkeletonBlock(color = blockColor, modifier = Modifier.fillMaxWidth(0.62f).height(13.dp))
                    ContentSkeletonBlock(color = blockColor, modifier = Modifier.fillMaxWidth(0.46f).height(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    ContentSkeletonBlock(
                        color = blockColor,
                        shape = CircleShape,
                        modifier = Modifier.width(28.dp).height(28.dp),
                    )
                }
            }
        }
    }
}
