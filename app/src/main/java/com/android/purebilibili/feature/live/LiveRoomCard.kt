package com.android.purebilibili.feature.live

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
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LiveRoomCard(
    model: LiveRoomCardUiModel,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    enableSharedCoverTransition: Boolean = false,
) {
    val topChromePolicy = rememberAppTopChromePolicy()
    val visualSpec = resolveLiveVisualSpec(topChromePolicy.tabPresentation)
    val metrics = visualSpec.homeMetrics
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val palette = rememberLiveChromePalette()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                role = Role.Button
            )
    ) {
    AppSurface(
        shape = AppShapes.borderedContainer(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        border = BorderStroke(AppSurfaceTokens.OutlineWidth, palette.border),
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
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    palette.scrim.copy(alpha = if (palette.isDark) 0.28f else 0.18f),
                                    palette.scrim,
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppSpacingTokens.Medium,
                            vertical = AppSpacingTokens.Small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = model.areaName.ifBlank { "直播间" },
                        color = LiveStatusPalette.MediaContent.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AppSpacingTokens.Small))
                    AppText(
                        text = model.viewerText.ifBlank { "${formatLiveViewerCount(model.viewerCount)}人看过" },
                        color = LiveStatusPalette.MediaContent,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = AppTypographyTokens.TabularNumerals
                        ),
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = visualSpec.roomCardDetailsMinHeightDp.dp)
                    .padding(AppSpacingTokens.Medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                AppText(
                    text = model.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = model.hostName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}
