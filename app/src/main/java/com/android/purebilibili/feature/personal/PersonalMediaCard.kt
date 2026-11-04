package com.android.purebilibili.feature.personal

import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.components.wallpaperMediaCardSurface
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonPulseState
import com.android.purebilibili.feature.home.components.cards.HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame

/**
 * Shared visual frame for personal-list media rows.
 *
 * The frame owns geometry and selection chrome only. History, favorites and
 * watch-later cards provide their own badges, metadata and actions through slots.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PersonalMediaCardFrame(
    headlineContent: @Composable () -> Unit,
    coverContent: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    coverOverlayModifier: Modifier = Modifier,
    selected: Boolean = false,
    stacked: Boolean = false,
    enabled: Boolean = true,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    allowPortraitCover: Boolean = false,
    horizontalCoverMaxWidthDp: Float? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    coverOverlayContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    nativeSnapshotModifier: Modifier = Modifier,
    stackedSupportingContentAtBottom: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val minimumHeight = resolvePersonalMediaCardMinHeightDp(fontScale).dp
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val cardSurface = wallpaperMediaCardSurface(AppSurfaceTokens.cardContainer(), cardShape)

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight)
            .clip(cardShape)
            .then(nativeSnapshotModifier)
            .then(cardSurface.modifier)
            .combinedClickable(
                enabled = enabled,
                onClick = appActionWithFeedback(onClick),
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        color = cardSurface.containerColor,
    ) {
        Box {
            if (stacked) {
                Column(
                    modifier = if (stackedSupportingContentAtBottom) Modifier.fillMaxHeight() else Modifier,
                ) {
                    Box(
                        modifier = coverModifier.fillMaxWidth().aspectRatio(coverAspectRatio).clip(cardShape),
                    ) {
                        coverContent()
                        Box(modifier = Modifier.fillMaxSize().then(coverOverlayModifier)) {
                            coverOverlayContent?.invoke(this)
                        }
                    }
                    // 多列的标题占满信息区；底部操作只与辅助信息并排。
                    // 玻璃和原生材质共用此布局，单列仍使用原有横向卡片。
                    Column(
                        modifier = (if (stackedSupportingContentAtBottom) Modifier.weight(1f) else Modifier)
                            .padding(AppSpacingTokens.Small),
                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                    ) {
                        overlineContent?.invoke()
                        headlineContent()
                        // 等高行把剩余空间放在标题之后，辅助信息和操作保持相同底部边距。
                        // 由入口显式启用；不改变其他列表及单列卡片的自然高度布局。
                        if (stackedSupportingContentAtBottom) Spacer(Modifier.weight(1f))
                        if (trailingContent == null) {
                            supportingContent?.invoke()
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                                ) {
                                    supportingContent?.invoke()
                                }
                                trailingContent()
                            }
                        }
                    }
                }
            } else {
                HorizontalVideoCardFrame(
                    coverContent = coverContent,
                    coverModifier = coverModifier,
                    coverOverlayModifier = coverOverlayModifier,
                    coverOverlayContent = coverOverlayContent,
                    coverAspectRatio = coverAspectRatio,
                    allowPortraitCover = allowPortraitCover,
                    horizontalCoverMaxWidthDp = horizontalCoverMaxWidthDp,
                    infoContent = {
                        overlineContent?.invoke()
                        androidx.compose.material3.ProvideTextStyle(
                            com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title,
                        ) { headlineContent() }
                    },
                    supportingContent = supportingContent?.let { content ->
                        {
                            androidx.compose.material3.ProvideTextStyle(
                                com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().author,
                                content = content,
                            )
                        }
                    },
                    trailingContent = trailingContent?.let { content ->
                        {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                content = content,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * PiliPlus 式多选遮罩：选中时在缩略图上铺黑色 60% 蒙层并居中显示对勾圆圈，
 * 蒙层完整覆盖缩略图并淡入淡出，仅中心勾选标记做弹性缩放。
 */
@Composable
internal fun PersonalCardSelectMask(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalSystemReduceMotion.current
    AnimatedVisibility(
        visible = selected,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(120)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(
                            initialScale = if (reduceMotion) 1f else 0.7f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                        ),
                        exit = scaleOut(
                            targetScale = if (reduceMotion) 1f else 0.85f,
                            animationSpec = tween(120),
                        ),
                    )
                    .size(34.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun PersonalMediaCardSkeleton(
    modifier: Modifier = Modifier,
    blockColor: (() -> Color)? = null,
    stacked: Boolean = false,
    horizontalCoverMaxWidthDp: Float? = null,
) {
    val color = blockColor ?: rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState())
    val cardShape = AppShapes.container(ContainerLevel.Card)

    AppSurface(
        modifier = modifier
            .fillMaxWidth(),
        shape = cardShape,
        color = AppSurfaceTokens.cardContainer(),
    ) {
        HorizontalVideoCardFrame(
            stacked = stacked,
            horizontalCoverMaxWidthDp = horizontalCoverMaxWidthDp,
            coverContent = {
                ContentSkeletonBlock(
                    color = color,
                    shape = AppShapes.mediaCover(),
                    modifier = Modifier.fillMaxSize(),
                )
            },
            infoContent = {
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(16.dp),
                )
            },
            supportingContent = {
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .height(12.dp),
                )
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(12.dp),
                )
            },
        )
    }
}
