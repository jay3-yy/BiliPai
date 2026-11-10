package com.android.purebilibili.feature.home

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.android.purebilibili.core.ui.skeleton.skeletonBackground
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 首页骨架扫光的唯一读口。返回 [State] 而非裸 Float:
 * 调用方把 state 包进 provider 传给骨架卡,值只在 draw 阶段被 [drawBehind] 读取,
 * 骨架展示期间逐帧仅触发重绘,不再让屏幕级组合作用域逐帧失效。
 */
@Composable
internal fun rememberHomeFeedSkeletonPulseState(animated: Boolean = true): State<Float> =
    com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState(animated = animated)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun HomeFeedSkeletonCard(
    pulse: () -> Float,
    wallpaperTintEnabled: Boolean,
    wallpaperEffectMode: HomeWallpaperEffectMode,
    isDataSaverActive: Boolean,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    modifier: Modifier = Modifier,
    compactMetadata: Boolean = false,
    compactStatsOnCover: Boolean = false,
    titleMinLines: Int = 2,
    showPublishTime: Boolean = true,
    storyStyle: Boolean = false,
    storyHorizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val cardLevel = if (storyStyle) ContainerLevel.ProminentCard else ContainerLevel.Card
    val cardCornerRadius = AppShapes.containerCornerDp(cardLevel)
    val cardShape = AppShapes.container(cardLevel)
    val isDarkCardTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f
    val infoSurfaceAppearance = remember(
        wallpaperTintEnabled,
        wallpaperEffectMode,
        isDarkCardTheme,
        isDataSaverActive
    ) {
        resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = wallpaperTintEnabled,
            wallpaperEffectMode = wallpaperEffectMode,
            isDarkTheme = isDarkCardTheme,
            isDataSaverActive = isDataSaverActive
        )
    }
    val blockBaseColor = AppContentColors.primaryText()
    // Same top-only cover and outer material-aware clip as VideoCard, even without wallpaper.
    val coverShape = if (storyStyle) cardShape else remember(cardCornerRadius) {
        resolveHomeSkeletonCoverShape(cardCornerRadius)
    }
    val infoSurfaceShape = remember(cardCornerRadius) {
        resolveHomeSkeletonInfoShape(cardCornerRadius)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (storyStyle) Modifier.padding(horizontal = storyHorizontalPadding, vertical = 8.dp)
                else Modifier.clip(cardShape)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio)
                .clip(coverShape)
                .homeFeedSkeletonBlockBackground(pulse, blockBaseColor, isDarkCardTheme)
        )

        val infoModifier = if (!storyStyle && infoSurfaceAppearance.useTintedSurface) {
            Modifier
                .fillMaxWidth()
                .background(
                    color = AppSurfaceTokens.cardContainer()
                        .copy(alpha = infoSurfaceAppearance.containerAlpha),
                    shape = infoSurfaceShape
                )
                .border(
                    border = BorderStroke(
                        width = AppSpacingTokens.Micro * 0.4f,
                        color = AppContentColors.primaryText()
                            .copy(alpha = infoSurfaceAppearance.borderAlpha)
                    ),
                    shape = infoSurfaceShape
                )
        } else {
            Modifier.fillMaxWidth()
        }

        val typography = com.android.purebilibili.core.ui.feedContentTypography(
            if (storyStyle && !compactMetadata) com.android.purebilibili.core.ui.FeedTitleHierarchy.Standard
            else com.android.purebilibili.core.ui.FeedTitleHierarchy.Compact,
        )
        val bottomPadding = if (compactMetadata) 6.dp else 8.dp
        val blockColor = homeFeedSkeletonFill(pulse, blockBaseColor, isDarkCardTheme)
        Column(
            modifier = infoModifier.padding(
                start = if (storyStyle) 0.dp else 10.dp,
                end = if (storyStyle) 0.dp else 10.dp,
                top = if (storyStyle) 0.dp else bottomPadding,
                bottom = if (storyStyle) 0.dp else bottomPadding,
            ),
        ) {
            com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
                text = List(titleMinLines.coerceAtLeast(1)) { "推荐视频标题占位" }.joinToString("\n"),
                style = typography.title,
                colorProvider = blockColor,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minLines = titleMinLines,
            )
            val titleToMetadataSpacing = if (storyStyle) {
                if (compactMetadata) 6.dp else 8.dp
            } else {
                if (compactMetadata) 4.dp else 6.dp
            }
            Spacer(Modifier.height(titleToMetadataSpacing))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (storyStyle) 0.dp else 24.dp),
            ) {
                if (storyStyle || !compactStatsOnCover) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
                            "▷ 00.0万", typography.statistic, blockColor,
                        )
                        com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
                            "▤ 0000", typography.statistic, blockColor,
                        )
                    }
                }
                if (storyStyle) Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars) {
                        HomeFeedSkeletonBlock(
                            pulse, blockBaseColor, isDarkCardTheme,
                            Modifier.size(if (storyStyle) 24.dp else 18.dp), CircleShape,
                        )
                    }
                    com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
                        "UP 主名称", typography.author, blockColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showPublishTime && !storyStyle) {
                    Spacer(Modifier.height(if (compactMetadata) 4.dp else 6.dp))
                    com.android.purebilibili.core.ui.skeleton.ContentSkeletonText(
                        "00-00", typography.statistic, blockColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeedSkeletonBlock(
    pulse: () -> Float,
    baseColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .homeFeedSkeletonBlockBackground(pulse, baseColor, isDarkTheme),
    )
}

/**
 * 首页横幅（Hero Carousel）骨架占位。
 * 与真实横幅 [HomeHeroCarousel] 对齐：垂直 padding、当前窗口限宽限高、卡片圆角。
 */
@Composable
internal fun HomeFeedHeroCarouselSkeleton(
    pulse: () -> Float,
    modifier: Modifier = Modifier
) {
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val isDarkCardTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f
    val blockBaseColor = AppContentColors.primaryText()
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacingTokens.ExtraSmall)
    ) {
        val layout = remember(
            maxWidth,
            windowSizeClass.widthDp,
            windowSizeClass.heightDp,
        ) {
            resolveHomeHeroCarouselLayout(
                containerWidthDp = maxWidth.value,
                windowWidthDp = windowSizeClass.widthDp.value,
                windowHeightDp = windowSizeClass.heightDp.value,
            )
        }
        Box(
            modifier = Modifier
                .width(layout.widthDp.dp)
                .aspectRatio(layout.aspectRatio)
                .clip(cardShape)
                .homeFeedSkeletonBlockBackground(pulse, blockBaseColor, isDarkCardTheme)
                .align(Alignment.Center)
        )
    }
}

/** 扫光仅参与绘制，不改变卡片尺寸、文字度量或材质背景。 */
private fun Modifier.homeFeedSkeletonBlockBackground(
    pulse: () -> Float,
    baseColor: Color,
    isDarkTheme: Boolean,
): Modifier = skeletonBackground(homeFeedSkeletonFill(pulse, baseColor, isDarkTheme))

private fun homeFeedSkeletonFill(pulse: () -> Float, baseColor: Color, dark: Boolean): () -> Color =
    com.android.purebilibili.core.ui.skeleton.skeletonFill(
        base = baseColor.copy(alpha = if (dark) HOME_FEED_SKELETON_DARK_MIN_ALPHA else HOME_FEED_SKELETON_LIGHT_MAX_ALPHA),
        highlight = baseColor.copy(alpha = if (dark) HOME_FEED_SKELETON_DARK_MAX_ALPHA else HOME_FEED_SKELETON_LIGHT_MIN_ALPHA),
        position = pulse,
    )

private const val HOME_FEED_SKELETON_LIGHT_MIN_ALPHA = 0.06f
private const val HOME_FEED_SKELETON_LIGHT_MAX_ALPHA = 0.11f
private const val HOME_FEED_SKELETON_DARK_MIN_ALPHA = 0.10f
private const val HOME_FEED_SKELETON_DARK_MAX_ALPHA = 0.16f
