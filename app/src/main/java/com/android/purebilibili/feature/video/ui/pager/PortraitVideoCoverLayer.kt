package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.BiliTvAnimatedBufferingIndicator
import kotlin.math.roundToInt

/** Match the video's content rectangle even when its poster has a different aspect ratio. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun resolvePortraitCoverImageSize(viewport: Size, videoAspect: Float, resizeMode: Int): Size {
    val source = Size(videoAspect, 1f)
    val scale = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds
        else -> ContentScale.Fit
    }.computeScaleFactor(source, viewport)
    return Size(source.width * scale.scaleX, source.height * scale.scaleY)
}

/** Poster and player share viewport, material-specific offset and fit settings. */
@Composable
internal fun PortraitVideoCoverLayer(
    cover: String,
    currentVideoAspect: Float,
    viewportAspect: Float,
    fillContainer: Boolean,
    aspectRatio: VideoAspectRatio,
    resizeMode: Int,
    videoWidth: Int,
    videoHeight: Int,
    showBuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        PortraitVideoViewportContainer(
            currentVideoAspect = viewportAspect,
            fillContainer = fillContainer,
            aspectRatio = aspectRatio,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                val context = LocalContext.current
                val density = LocalDensity.current
                val imageLoadingAllowed = LocalPageImageLoadingAllowed.current
                val imageSize = with(density) {
                    resolvePortraitCoverImageSize(
                        Size(maxWidth.toPx(), maxHeight.toPx()), currentVideoAspect, resizeMode,
                    )
                }
                val targetWidthPx = imageSize.width.roundToInt().coerceAtLeast(1)
                val targetHeightPx = imageSize.height.roundToInt().coerceAtLeast(1)
                val coverUrl = remember(cover) { FormatUtils.fixImageUrl(cover) }
                val imageModel = remember(context, coverUrl, imageLoadingAllowed, targetWidthPx, targetHeightPx) {
                    if (!imageLoadingAllowed || coverUrl.isBlank()) {
                        null
                    } else {
                        ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(targetWidthPx, targetHeightPx)
                            .build()
                    }
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.requiredSize(
                        with(density) { imageSize.width.toDp() },
                        with(density) { imageSize.height.toDp() },
                    ),
                    // Thumbnail dimensions are independent of the actual video format.
                    contentScale = ContentScale.Crop,
                )
            }
        }
        if (showBuffering) {
            BiliTvAnimatedBufferingIndicator(
                modifier = Modifier.align(Alignment.Center),
                tint = AppContentColors.accent(),
            )
        }
    }
}
