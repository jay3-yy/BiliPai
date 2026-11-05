package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.ui.AppContentColors

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow

import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.res.painterResource
import com.android.purebilibili.R

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.LocalClickToPlayEnabled
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionVisualSpec
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardCoverOverlayTextShadow
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnChromeAlpha
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnCoverAlpha
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.data.model.response.ArchiveMajor

/**
 * 对齐 BiliPai 的动态视频呈现：
 * 1. 手机和平板都保持纵向视频卡
 * 2. 封面使用 16:10 比例
 * 3. 统计信息压到封面渐变层，正文只保留标题信息
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun VideoCardLarge(
    archive: ArchiveMajor,
    onClick: () -> Unit,
    publishTs: Long = 0L,
    isCollection: Boolean = false,
    collectionTitle: String = "",
    cornerBadgeText: String? = null,
    sharedElementKey: Any? = null
) {
    val context = LocalContext.current
    val coverUrl = remember(archive.cover) { normalizeDynamicCoverUrl(archive.cover) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val cardBoundsRef = remember { object { var value: LayoutCoordinates? = null } }
    val coverBoundsRef = remember { object { var value: LayoutCoordinates? = null } }
    val infoBoundsRef = remember { object { var value: LayoutCoordinates? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(archive.bvid)
    val stationaryCoverRequest = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
            .crossfade(false)
            .memoryCacheKey(coverUrl)
            .diskCacheKey(coverUrl)
            .build()
    }
    val triggerClick = dynamicCardClick@{
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@dynamicCardClick
        }
        cardBoundsRef.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()?.let { bounds ->
            val sourceCoverBounds = coverBoundsRef.value
                ?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()
            CardPositionManager.recordVideoCardPosition(
                bvid = archive.bvid,
                sourceRoute = sourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = density.density,
                sourceCornerDp = 10,
                coverBounds = sourceCoverBounds,
                infoBounds = infoBoundsRef.value
                    ?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot(),
                sourceLayout = VideoCardSourceLayout.STACKED,
                sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                    title = archive.title,
                    ownerName = collectionTitle.takeIf { isCollection }.orEmpty(),
                    ownerFaceUrl = "",
                    viewText = archive.stat.play,
                    danmakuText = archive.stat.danmaku,
                    durationText = archive.duration_text,
                    infoPresentation = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSourceInfoPresentation(
                            publishTimeText = "",
                            // Dynamic cards paint duration/stats on the cover, not below it.
                            showStatsInInfo = false,
                        ),
                    coverPresentation = VideoCardSourceCoverPresentation(
                        showGradientMask = true,
                        showStatsOnCover = true,
                        showSecondaryStatOnCover = true,
                        showDurationOnCover = true,
                    ),
                    coverUrl = coverUrl,
                    coverCacheKey = coverUrl,
                ).withMeasuredCoverDecodeSize(sourceCoverBounds),
            )
            nativeCardSnapshot.capture()
        }
        onClick()
    }

    var modifier = Modifier
        .fillMaxWidth()
        .appActionClickable(onClick = triggerClick)

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedTransitionEnabled = LocalSharedTransitionEnabled.current
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val sharedElementReady = sharedTransitionEnabled &&
        archive.bvid.isNotBlank() &&
        sourceRoute != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val sharedTransitionMotionSpec = remember(
        sourceRoute,
        sharedTransitionEnabled,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = sharedTransitionEnabled,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val autoPlayOnOpenEnabled = LocalClickToPlayEnabled.current
    val videoSharedPlaybackIntent = remember(autoPlayOnOpenEnabled) {
        resolveVideoSharedTransitionPlaybackIntent(
            clickToPlayEnabled = autoPlayOnOpenEnabled
        )
    }
    val sharedTransitionVisualSpec = remember(
        sourceRoute,
        videoSharedPlaybackIntent,
        transitionAdaptiveInfo,
    ) {
        resolveVideoSharedTransitionVisualSpec(
            sourceRoute = sourceRoute,
            sourceCornerDp = 10,
            playbackIntent = videoSharedPlaybackIntent,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val coverShape = RoundedCornerShape(sharedTransitionVisualSpec.sourceCornerDp.dp)
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedElementReady
    )

    Column(
        modifier = modifier
            .videoCardSourceAnchor(archive.bvid, coverShape)
            .then(nativeCardSnapshot.modifier)
            .onGloballyPositioned { coordinates ->
                cardBoundsRef.value = coordinates
            }
    ) {
        VideoCardLargeCover(
            archive = archive,
            coverUrl = coverUrl,
            coverRequest = stationaryCoverRequest,
            isCollection = isCollection,
            cornerBadgeText = cornerBadgeText,
            coverShape = coverShape,
            overlayModifier = nativeCardSnapshot.coverOverlayModifier,
            modifier = Modifier
                .videoCardShellReturnCoverAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = archive.bvid,
                    sourceRoute = sourceRoute,
                )
                .onGloballyPositioned { coordinates ->
                    coverBoundsRef.value = coordinates
                },
        )
        Column(
            modifier = Modifier.onGloballyPositioned { infoBoundsRef.value = it }
                .videoCardShellReturnChromeAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = archive.bvid,
                    sourceRoute = sourceRoute,
                )
        ) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))
            VideoCardLargeInfo(
                archive = archive,
                isCollection = isCollection,
                collectionTitle = collectionTitle,
                publishTs = publishTs
            )
        }
    }
}

@Composable
private fun VideoCardLargeCover(
    archive: ArchiveMajor,
    coverUrl: String,
    coverRequest: ImageRequest,
    isCollection: Boolean,
    cornerBadgeText: String?,
    coverShape: androidx.compose.ui.graphics.Shape,
    overlayModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(coverShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val coverOverlayTextStyle = remember {
            TextStyle(shadow = resolveVideoCardCoverOverlayTextShadow())
        }
        if (coverUrl.isNotEmpty()) {
            PageAwareAsyncImage(
                model = coverRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(modifier = Modifier.fillMaxSize().then(overlayModifier)) {
        val badgeText = cornerBadgeText ?: if (isCollection) "合集" else null
        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppSpacingTokens.Small)
                    .background(AppContentColors.accent(), AppShapes.container(ContainerLevel.Tag))
                    .padding(horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Micro)
            ) {
                AppText(
                    text = badgeText,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = AppContentColors.onAccent()
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MediaContrastPalette.Scrim.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = AppSpacingTokens.Small + AppSpacingTokens.Micro, end = AppSpacingTokens.Small, bottom = AppSpacingTokens.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (archive.duration_text.isNotBlank()) {
                    AppText(
                        text = archive.duration_text,
                        color = MediaContrastPalette.Foreground,
                        style = feedContentTypography().coverBadge
                            .copy(fontWeight = FontWeight.Medium)
                            .merge(coverOverlayTextStyle),
                        maxLines = 1,
                        tapToCopyEnabled = false,
                    )
                    Spacer(modifier = Modifier.size(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))
                }

                HorizontalVideoStatRow(
                    playText = archive.stat.play,
                    danmakuText = archive.stat.danmaku,
                    contentColor = MediaContrastPalette.Foreground,
                )
                Spacer(modifier = Modifier.weight(1f))

                AppIcon(
                    painter = painterResource(R.drawable.ic_pili_tv_play),
                    contentDescription = "播放视频",
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(50.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun VideoCardLargeInfo(
    archive: ArchiveMajor,
    isCollection: Boolean,
    collectionTitle: String,
    publishTs: Long,
    titleModifier: Modifier = Modifier
) {
    if (isCollection && collectionTitle.isNotBlank()) {
        AppText(
            text = collectionTitle,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            color = AppContentColors.primaryText()
        )
        Spacer(modifier = Modifier.height(AppSpacingTokens.Micro))
        AppText(
            text = archive.title,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            color = AppContentColors.secondaryText(),
            modifier = titleModifier
        )
    } else {
        AppText(
            text = archive.title,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
            color = AppContentColors.primaryText(),
            modifier = titleModifier
        )
    }
}

@Composable
private fun VideoCardLargeMetaText(
    text: String
) {
    AppText(
        text = text,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        color = MediaContrastPalette.Foreground,
        maxLines = 1
    )
}

@Composable
fun VideoCardSmall(
    archive: ArchiveMajor,
    publishTs: Long = 0L,
    onClick: () -> Unit
) {
    VideoCardLarge(
        archive = archive,
        onClick = onClick,
        publishTs = publishTs
    )
}

private fun normalizeDynamicCoverUrl(rawCover: String): String {
    val raw = rawCover.trim()
    return when {
        raw.startsWith("https://") -> raw
        raw.startsWith("http://") -> raw.replace("http://", "https://")
        raw.startsWith("//") -> "https:$raw"
        raw.isNotEmpty() -> "https://$raw"
        else -> ""
    }
}
