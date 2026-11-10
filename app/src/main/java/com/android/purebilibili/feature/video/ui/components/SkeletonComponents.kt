package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.feature.home.HomeFeedCardLayout
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.android.purebilibili.core.ui.skeleton.skeletonBackground
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 *  骨架屏组件 - iOS 风格加载占位
 */

internal val LocalVideoDetailLoadingTitle = staticCompositionLocalOf { "" }

private val LocalVideoSkeletonPulse = staticCompositionLocalOf<androidx.compose.runtime.State<Float>?> { null }
private val LocalVideoSkeletonAnimationAllowed = staticCompositionLocalOf { true }

// 基础 Shimmer 容器
@Composable
fun ShimmerContainer(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    sharedPulse: androidx.compose.runtime.State<Float>? = null,
    content: @Composable () -> Unit
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val animationAllowed = animated && LocalVideoSkeletonAnimationAllowed.current
    val pulse = if (animationAllowed) {
        sharedPulse ?: LocalVideoSkeletonPulse.current ?: com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState(
            animated = animationGate.active,
        )
    } else {
        androidx.compose.runtime.rememberUpdatedState(Float.NaN)
    }
    CompositionLocalProvider(
        LocalVideoSkeletonPulse provides pulse,
        LocalVideoSkeletonAnimationAllowed provides animationAllowed,
    ) {
        Box(modifier = modifier.then(animationGate.modifier)) {
            content()
        }
    }
}

// 骨架方块
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    SkeletonBlock(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(cornerRadius)
    )
}

// 圆形骨架 (头像)
@Composable
fun SkeletonCircle(size: Dp = 48.dp) {
    SkeletonBlock(modifier = Modifier.size(size), shape = CircleShape)
}

/**
 *  视频详情页内容骨架屏（不包含播放器区域）
 */
@Composable
fun VideoDetailSkeleton(
    animated: Boolean = true,
    sharedPulse: androidx.compose.runtime.State<Float>? = null,
) {
    ShimmerContainer(
        modifier = Modifier.fillMaxSize(),
        animated = animated,
        sharedPulse = sharedPulse,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            VideoDetailTabBarSkeleton()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                VideoDetailUpInfoSkeleton()
                VideoDetailTitleInfoSkeleton()
                VideoDetailActionButtonsSkeleton()
            }
            repeat(RELATED_VIDEO_SKELETON_ROW_COUNT) {
                RelatedVideoGridRowSkeleton()
            }
        }
    }
}

/** Loading content follows the pane bounds when a foldable changes posture. */
@Composable
internal fun VideoDetailInfoPaneSkeleton(modifier: Modifier = Modifier) {
    ShimmerContainer(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                VideoDetailUpInfoSkeleton()
                VideoDetailTitleInfoSkeleton()
                VideoDetailActionButtonsSkeleton()
            }
        }
    }
}

@Composable
internal fun VideoDetailSecondaryPaneSkeleton(
    showComments: Boolean,
    modifier: Modifier = Modifier,
    showIntro: Boolean = false,
) {
    ShimmerContainer(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { VideoDetailTabBarSkeleton() }
            if (showIntro) {
                item {
                    VideoDetailUpInfoSkeleton()
                    VideoDetailTitleInfoSkeleton()
                    VideoDetailActionButtonsSkeleton()
                }
            } else items(if (showComments) 6 else RELATED_VIDEO_SKELETON_ROW_COUNT) {
                if (showComments) {
                    com.android.purebilibili.core.ui.skeleton.CommentListItemSkeleton()
                } else {
                    RelatedVideoGridRowSkeleton()
                }
            }
        }
    }
}

@Composable
private fun VideoDetailTabBarSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(
            modifier = Modifier
                .weight(1f)
                .padding(start = 0.dp, top = 5.dp, end = 8.dp, bottom = 5.dp),
            height = 36.dp,
            cornerRadius = 18.dp
        )
        SkeletonBox(
            modifier = Modifier.width(54.dp),
            height = 28.dp,
            cornerRadius = 14.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        SkeletonBox(
            modifier = Modifier.width(64.dp),
            height = 28.dp,
            cornerRadius = 14.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        SkeletonBox(
            modifier = Modifier.size(28.dp),
            height = 28.dp,
            cornerRadius = 14.dp
        )
    }
}

@Composable
private fun VideoDetailUpInfoSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonCircle(size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.width(116.dp), height = 16.dp, cornerRadius = 8.dp)
            Spacer(modifier = Modifier.height(4.dp))
            SkeletonBox(modifier = Modifier.width(88.dp), height = 12.dp, cornerRadius = 6.dp)
        }
        SkeletonBox(modifier = Modifier.width(76.dp), height = 36.dp, cornerRadius = 18.dp)
    }
}

@Composable
private fun VideoDetailTitleInfoSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            val loadingTitle = LocalVideoDetailLoadingTitle.current
            if (loadingTitle.isNotBlank()) {
                com.android.purebilibili.core.ui.components.AppText(
                    text = loadingTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppContentColors.primaryText(),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            } else {
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 18.dp,
                    cornerRadius = 9.dp
                )
            }
            Spacer(Modifier.width(8.dp))
            SkeletonBox(
                modifier = Modifier.size(20.dp),
                height = 20.dp,
                cornerRadius = 10.dp
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(modifier = Modifier.width(56.dp), height = 11.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(10.dp))
            SkeletonBox(modifier = Modifier.width(54.dp), height = 11.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(10.dp))
            SkeletonBox(modifier = Modifier.width(72.dp), height = 11.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(10.dp))
            SkeletonBox(modifier = Modifier.width(84.dp), height = 11.dp, cornerRadius = 6.dp)
        }
        Spacer(Modifier.height(6.dp))
        SkeletonBox(modifier = Modifier.width(104.dp), height = 24.dp, cornerRadius = 10.dp)
    }
}

@Composable
private fun VideoDetailActionButtonsSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(6) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SkeletonBlock(modifier = Modifier.size(24.dp), shape = CircleShape)
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonBox(modifier = Modifier.width(42.dp), height = 11.dp, cornerRadius = 6.dp)
            }
        }
    }
}

@Composable
private fun RelatedVideoGridRowSkeleton() {
    val cardLayout = rememberRelatedVideoCardLayout()
    RelatedVideoGridRowSkeleton(cardLayout = cardLayout)
}

/** Same viewport, cover ratio and horizontal/stacked frame as a real recommendation row. */
@Composable
internal fun RelatedVideoGridRowSkeleton(
    cardLayout: HomeFeedCardLayout,
    modifier: Modifier = Modifier,
    cardPresentation: RelatedVideoCardLayout? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val pulse = rememberRelatedSkeletonSweepState(
        animationGate.active && LocalVideoSkeletonAnimationAllowed.current,
    )
    CompositionLocalProvider(LocalVideoSkeletonPulse provides pulse) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .then(animationGate.modifier)
                .clearAndSetSemantics { }
                .padding(horizontal = cardLayout.outerPaddingDp.dp, vertical = 2.dp),
        ) {
            RelatedVideoItemSkeleton(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                coverAspectRatio = cardLayout.coverAspectRatio,
                stacked = (cardPresentation ?: resolveRelatedVideoCardLayout(maxWidth.value.toInt())) == RelatedVideoCardLayout.STACKED,
            )
        }
    }
}

/** 可见行共用帧时间作为扫光相位，移出视口后停止逐帧工作。 */
@Composable
private fun rememberRelatedSkeletonSweepState(active: Boolean): androidx.compose.runtime.State<Float> {
    val enabled = com.android.purebilibili.core.ui.skeleton.rememberSkeletonBreathingEnabled()
    if (!active || !enabled) return androidx.compose.runtime.rememberUpdatedState(Float.NaN)
    return androidx.compose.runtime.produceState(initialValue = Float.NaN) {
        while (true) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameTime ->
                val phase = (frameTime % 1_500_000_000L).toFloat() / 1_500_000_000f
                value = -1.5f + phase * 3f
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun RelatedVideoItemSkeleton(
    modifier: Modifier = Modifier,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    stacked: Boolean = false,
) {
    val typography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    HorizontalVideoCardFrame(
        plainHorizontal = true,
        fillInfoHeight = true,
        coverAspectRatio = coverAspectRatio,
        stacked = stacked,
        trailingContent = { Spacer(Modifier.size(29.dp)) },
        modifier = modifier,
        coverContent = {
            SkeletonBlock(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.ui.graphics.RectangleShape,
            )
        },
        infoContent = {
            RelatedSkeletonText(
                text = "推荐视频标题占位\n第二行标题占位",
                style = typography.title,
                maxLines = videoCardTitleMaxLines(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(com.android.purebilibili.core.ui.AppSpacingTokens.ExtraSmall)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(com.android.purebilibili.core.ui.AppSpacingTokens.Small),
                ) {
                    RelatedSkeletonText("00-00", MaterialTheme.typography.labelMedium)
                    if (com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars) {
                        SkeletonBlock(modifier = Modifier.size(16.dp), shape = CircleShape)
                    }
                    RelatedSkeletonText("UP 主名称", MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RelatedSkeletonText("▷ 000.0万", typography.statistic)
                    RelatedSkeletonText("▤ 0000", typography.statistic)
                }
            }
        },
    )
}

/** Measure text with the same typography/font scale as the loaded card. */
@Composable
private fun RelatedSkeletonText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val color = rememberVideoSkeletonBlockColor()
    com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
        text = text,
        style = style,
        colorProvider = color,
        modifier = modifier,
        maxLines = maxLines,
    )
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape = AppShapes.container(ContainerLevel.Chip)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .skeletonBackground(rememberVideoSkeletonBlockColor())
    )
}

@Composable
private fun rememberVideoSkeletonBlockColor(): () -> Color {
    val pulse = LocalVideoSkeletonPulse.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = AppContentColors.primaryText()
    return androidx.compose.runtime.remember(pulse, isDarkTheme, base) {
        com.android.purebilibili.core.ui.skeleton.skeletonFill(
            base = base.copy(alpha = if (isDarkTheme) VIDEO_SKELETON_DARK_MIN_ALPHA else VIDEO_SKELETON_LIGHT_MAX_ALPHA),
            highlight = base.copy(alpha = if (isDarkTheme) VIDEO_SKELETON_DARK_MAX_ALPHA else VIDEO_SKELETON_LIGHT_MIN_ALPHA),
            position = { pulse?.value ?: Float.NaN },
        )
    }
}

private const val VIDEO_SKELETON_LIGHT_MIN_ALPHA = 0.06f
private const val VIDEO_SKELETON_LIGHT_MAX_ALPHA = 0.11f
private const val VIDEO_SKELETON_DARK_MIN_ALPHA = 0.10f
private const val VIDEO_SKELETON_DARK_MAX_ALPHA = 0.16f
