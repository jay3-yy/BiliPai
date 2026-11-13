package com.android.purebilibili.feature.live

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.android.purebilibili.core.ui.adaptive.onSecondaryClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppTypographyTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.liveCoverSharedElementKey
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy

internal data class LiveRoomCardUiModel(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val hostName: String,
    val viewerCount: Int,
    val areaName: String,
    val viewerText: String = "",
    val feedback: List<com.android.purebilibili.data.model.response.LiveFeedbackGroup> = emptyList(),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LiveRoomCard(
    model: LiveRoomCardUiModel,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    enableSharedCoverTransition: Boolean = false,
    modifier: Modifier = Modifier,
    onFeedback: ((com.android.purebilibili.core.ui.components.AppGlassMenuSource) -> Unit)? = null,
    searchResult: Boolean = false,
) {
    val topChromePolicy = rememberAppTopChromePolicy()
    val visualSpec = resolveLiveVisualSpec(topChromePolicy.tabPresentation)
    val metrics = visualSpec.homeMetrics
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val palette = rememberLiveChromePalette()
    val containerTransition = rememberLiveCardContainerTransition(
        roomId = model.roomId,
        title = model.title,
        hostName = model.hostName,
        color = AppSurfaceTokens.cardContainer(),
        cornerRadius = metrics.cardRadiusDp.dp,
        onClick = onClick,
        enabled = enableSharedCoverTransition,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(containerTransition.modifier)
            .onSecondaryClick(onLongPress?.let { callback -> { _: Offset -> callback() } })
            .combinedClickable(
                onClick = containerTransition.onClick,
                onLongClick = onLongPress,
                role = Role.Button
            )
    ) {
    AppSurface(
        shape = RoundedCornerShape(metrics.cardRadiusDp.dp),
        color = AppSurfaceTokens.cardContainer(),
        border = null,
        tonalElevation = AppSpacingTokens.None,
        shadowElevation = AppSpacingTokens.None,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(metrics.coverAspectRatio)
                    .then(
                        if (
                            enableSharedCoverTransition &&
                            !containerTransition.enabled &&
                            sharedTransitionScope != null &&
                            animatedVisibilityScope != null
                        ) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(
                                        key = liveCoverSharedElementKey(model.roomId),
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                AsyncImage(
                    model = model.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.54f)))),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = if (searchResult) model.hostName else model.areaName.ifBlank { "直播间" },
                        color = LiveStatusPalette.MediaContent.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AppSpacingTokens.Small))
                    AppText(
                        text = model.viewerText.ifBlank { "${formatLiveViewerCount(model.viewerCount)}人看过" },
                        color = LiveStatusPalette.MediaContent,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = AppTypographyTokens.TabularNumerals
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ((if (searchResult) 60 else visualSpec.roomCardDetailsMinHeightDp) * LocalDensity.current.fontScale).dp)
                    .padding(start = 5.dp, top = 8.dp, end = 5.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = model.title,
                    color = AppContentColors.primaryText(),
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.3.sp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!searchResult) {
                AppText(
                    text = model.hostName,
                    color = AppContentColors.secondaryText(),
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                }
            }
        }
    }
    if (model.feedback.isNotEmpty() && onFeedback != null) {
        val feedbackSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
        com.android.purebilibili.core.ui.components.AppIconButton(
            onClick = { onFeedback(feedbackSource) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(29.dp)
                .appGlassMenuSource(feedbackSource, onOpen = { onFeedback(feedbackSource) }),
        ) {
            com.android.purebilibili.core.ui.components.AppIcon(
                androidx.compose.material.icons.Icons.Outlined.MoreVert,
                contentDescription = "不感兴趣与推荐反馈",
                tint = AppContentColors.secondaryText(),
                modifier = Modifier.size(17.dp),
            )
        }
    }
    }
}
