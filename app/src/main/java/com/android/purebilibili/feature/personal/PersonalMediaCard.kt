package com.android.purebilibili.feature.personal

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonPulse
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
    coverAspectRatio: Float = PERSONAL_LIST_HORIZONTAL_COVER_ASPECT_RATIO,
    coverWidth: Dp? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    coverOverlayContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    nativeSnapshotModifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val minimumHeight = resolvePersonalMediaCardMinHeightDp(fontScale).dp
    val cardShape = AppShapes.container(ContainerLevel.Card)

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight)
            .clip(cardShape)
            .then(nativeSnapshotModifier)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        color = AppSurfaceTokens.cardContainer(),
    ) {
        Box {
            if (stacked) {
                Column {
                    Box(
                        modifier = coverModifier.fillMaxWidth().aspectRatio(coverAspectRatio).clip(cardShape),
                    ) {
                        coverContent()
                        Box(modifier = Modifier.fillMaxSize().then(coverOverlayModifier)) {
                            coverOverlayContent?.invoke(this)
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(
                            modifier = Modifier.weight(1f).padding(AppSpacingTokens.Small),
                            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                        ) {
                            overlineContent?.invoke()
                            headlineContent()
                            supportingContent?.invoke()
                        }
                        trailingContent?.let { content ->
                            Row(modifier = Modifier.padding(end = AppSpacingTokens.Small), content = content)
                        }
                    }
                }
            } else {
                HorizontalVideoCardFrame(
                    coverContent = coverContent,
                    coverModifier = coverModifier,
                    coverOverlayModifier = coverOverlayModifier,
                    coverOverlayContent = coverOverlayContent,
                    coverWidth = coverWidth ?: HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP.dp,
                    coverAspectRatio = coverAspectRatio,
                    minimumHeight = minimumHeight,
                    infoContent = {
                        overlineContent?.invoke()
                        headlineContent()
                        supportingContent?.invoke()
                    },
                    trailingContent = trailingContent?.let { content ->
                        {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = AppSpacingTokens.Small,
                                        bottom = AppSpacingTokens.Small,
                                    ),
                                verticalAlignment = Alignment.Bottom,
                                content = content,
                            )
                        }
                    },
                )
            }

            if (selected) {
                AppCheckbox(
                    checked = true,
                    onCheckedChange = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacingTokens.Small),
                )
            }
        }
    }
}

@Composable
internal fun PersonalMediaCardSkeleton(
    modifier: Modifier = Modifier,
    blockColor: Color? = null,
) {
    val pulse = if (blockColor == null) rememberContentSkeletonPulse() else 0f
    val color = blockColor ?: rememberContentSkeletonBlockColor(pulse)
    val minimumHeight = resolvePersonalMediaCardMinHeightDp(LocalDensity.current.fontScale).dp
    val cardShape = AppShapes.container(ContainerLevel.Card)

    AppSurface(
        modifier = modifier
            .fillMaxWidth(),
        shape = cardShape,
        color = AppSurfaceTokens.cardContainer(),
    ) {
        HorizontalVideoCardFrame(
            minimumHeight = minimumHeight,
            coverContent = {
                ContentSkeletonBlock(
                    color = color,
                    shape = AppShapes.mediaCover(),
                    modifier = Modifier.fillMaxSize(),
                )
            },
            infoVerticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            infoContent = {
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(16.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
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
            trailingContent = {
                ContentSkeletonBlock(
                    color = color,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(end = AppSpacingTokens.Small)
                        .size(24.dp)
                        .align(Alignment.CenterEnd),
                )
            },
        )
    }
}
