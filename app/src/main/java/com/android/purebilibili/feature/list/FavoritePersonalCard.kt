package com.android.purebilibili.feature.list

import coil3.request.crossfade

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.personal.PersonalCardSelectMask
import com.android.purebilibili.feature.personal.PersonalMediaCardFrame
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import kotlin.math.roundToInt

internal fun resolveFavoriteDateLabel(
    timestampSeconds: Long,
    nowMs: Long = System.currentTimeMillis(),
): String =
    FormatUtils.formatPublishTime(timestampSeconds, nowMs).takeIf { it.isNotBlank() }
        ?.let { "${it}收藏" }
        .orEmpty()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FavoritePersonalCard(
    item: VideoItem,
    stacked: Boolean = false,
    transitionEnabled: Boolean,
    batchMode: Boolean,
    selected: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val speedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val sharedElementReady = transitionEnabled &&
        item.bvid.isNotBlank() &&
        sourceRoute != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val motionSpec = remember(sourceRoute, transitionEnabled, speedSettings, transitionAdaptiveInfo) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute,
            transitionEnabled,
            speedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val useSharedBounds = shouldUseVideoCardShellSharedBounds(sourceRoute, sharedElementReady)
    val cardBounds = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val coverBounds = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(item.bvid)
    val progressState = remember(item.progress, item.duration, item.view_at) {
        resolveVideoDisplayProgressState(
            serverProgressSec = item.progress,
            durationSec = item.duration,
            viewAt = item.view_at,
        )
    }
    val stationaryCoverUrl = remember(item.pic) { FormatUtils.fixImageUrl(item.pic) }
    val stationaryCoverRequest = remember(stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val cardCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value.roundToInt()
    val triggerClick = {
        if (!batchMode) {
            cardBounds.value?.let { bounds ->
                val sourceCoverBounds = coverBounds.value
                CardPositionManager.recordVideoCardPosition(
                    bvid = item.bvid,
                    sourceRoute = sourceRoute,
                    bounds = bounds,
                    screenWidth = configuration.screenWidthDp * density.density,
                    screenHeight = configuration.screenHeightDp * density.density,
                    sourceCornerDp = cardCornerRadiusDp,
                    coverBounds = sourceCoverBounds,
                    sourceLayout = if (stacked) VideoCardSourceLayout.STACKED else VideoCardSourceLayout.SIDE_BY_SIDE,
                    sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                        title = item.title,
                        ownerName = item.owner.name.ifBlank { "未知UP主" },
                        ownerFaceUrl = item.owner.face,
                        viewText = FormatUtils.formatStat(item.stat.view.toLong()),
                        danmakuText = FormatUtils.formatStat(item.stat.danmaku.toLong()),
                        durationText = FormatUtils.formatDuration(item.duration),
                        infoPresentation = com.android.purebilibili.core.ui.transition
                            .resolveVideoCardSourceInfoPresentation(
                                publishTimeText = resolveFavoriteDateLabel(item.view_at),
                                showStatsInInfo = true,
                                ownerBeforePublish = true,
                                showOverflowMenu = !batchMode && canRemove && onRemove != null,
                            ),
                        coverPresentation = VideoCardSourceCoverPresentation(
                            showDurationOnCover = true,
                            showHistoryProgressBar = progressState.showProgressBar,
                            historyProgressFraction = progressState.progressFraction,
                        ),
                        coverUrl = stationaryCoverUrl,
                        coverCacheKey = stationaryCoverUrl,
                    ).withMeasuredCoverDecodeSize(sourceCoverBounds),
                )
                nativeCardSnapshot.capture()
            }
        }
        onClick()
    }

    PersonalMediaCardFrame(
        stacked = stacked,
        coverModifier = Modifier.onGloballyPositioned {
            coverBounds.value = it.boundsInRoot()
        },
        modifier = modifier
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = item.bvid,
                sourceRoute = sourceRoute,
                motionSpec = motionSpec,
                clipShape = AppShapes.container(ContainerLevel.Card),
                crossfadeSourceContent = true,
            )
            .onGloballyPositioned { cardBounds.value = it.boundsInRoot() },
        nativeSnapshotModifier = nativeCardSnapshot.modifier,
        coverOverlayModifier = nativeCardSnapshot.coverOverlayModifier,
        headlineContent = {
            AppText(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = videoCardTitleMaxLines(),
                overflow = videoCardTitleOverflow(),
            )
        },
        supportingContent = {
            Column {
                UpBadgeName(
                    name = item.owner.name.ifBlank { "未知UP主" },
                    nameStyle = MaterialTheme.typography.bodySmall,
                    nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Visible,
                )
                val dateLabel = resolveFavoriteDateLabel(item.view_at)
                if (dateLabel.isNotBlank()) {
                    AppText(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        overflow = TextOverflow.Visible,
                    )
                }
                HorizontalVideoStatRow(
                    playText = FormatUtils.formatStat(item.stat.view.toLong()),
                    danmakuText = FormatUtils.formatStat(item.stat.danmaku.toLong()),
                )
            }
        },
        coverContent = {
            AsyncImage(
                model = stationaryCoverRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        coverOverlayContent = {
            VideoCardCoverDurationText(
                text = if (progressState.progressSec > 0 || progressState.progressSec == -1) {
                    resolveHistoryProgressLabel(progressState.progressSec, item.duration)
                } else {
                    FormatUtils.formatDuration(item.duration)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppSpacingTokens.ExtraSmall),
            )
            if (progressState.showProgressBar) {
                AppLinearProgressIndicator(
                    progress = { progressState.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
            PersonalCardSelectMask(selected = selected)
        },
        selected = selected,
        trailingContent = if (!batchMode && canRemove && onRemove != null) {
            {
                AppIconButton(
                    onClick = onRemove,
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移出收藏夹",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        onClick = triggerClick,
        onLongClick = onLongClick,
    )
}
