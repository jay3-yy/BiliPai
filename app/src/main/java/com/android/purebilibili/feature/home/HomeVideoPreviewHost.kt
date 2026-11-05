package com.android.purebilibili.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.video.share.VideoSharePayload
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop

/** 预览、分享和封面保存的动作留在独立宿主，导航只通过视频点击回调上交。 */
@Composable
internal fun HomeVideoPreviewHost(
    viewModel: HomeViewModel,
    targetVideoItemState: MutableState<VideoItem?>,
    pendingVideoShare: MutableState<VideoSharePayload?>,
    currentCategory: HomeCategory,
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onDismissVideoCallback: (VideoItem) -> Unit,
    hazeState: HazeState?,
    miuixBackdrop: Backdrop?,
    backgroundBlurEnabled: Boolean,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    com.android.purebilibili.feature.video.share.VideoShareSheetHost(
        payload = pendingVideoShare.value,
        onDismiss = { pendingVideoShare.value = null },
    )

    // [Feature] Video Preview Overlay with Animation
    androidx.compose.animation.AnimatedVisibility(
        visible = targetVideoItemState.value != null,
        enter = fadeIn(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(
                overlayMotionSpec.previewOverlayScaleDurationMillis,
                easing = AppMotionEasing.M1Standard
            )
        ),
        exit = fadeOut(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(
                overlayMotionSpec.previewOverlayScaleDurationMillis,
                easing = AppMotionEasing.M1Standard
            )
        ),
        modifier = Modifier.fillMaxSize().zIndex(100f) // Ensure on top
    ) {
        val item = targetVideoItemState.value
        if (item != null) {
            com.android.purebilibili.feature.home.components.VideoPreviewDialog(
                video = item,
                onDismiss = { targetVideoItemState.value = null },
                onPlay = {
                    onVideoClick(
                        HomeVideoClickRequest(
                            bvid = item.bvid,
                            cid = item.cid,
                            coverUrl = item.pic,
                            isVerticalVideo = item.resolveIsVerticalForNavigation(),
                            source = HomeVideoClickSource.PREVIEW,
                            sourceRoute = resolveHomeCategoryVideoSourceRoute(currentCategory),
                        )
                    )
                    targetVideoItemState.value = null
                },
                onWatchLater = {
                    viewModel.addToWatchLater(item.bvid, item.aid)
                    targetVideoItemState.value = null
                },
                onSaveCover = {
                    val coverUrl = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(item.pic)
                    if (coverUrl.isBlank()) {
                        android.widget.Toast.makeText(context, "无法获取封面地址", android.widget.Toast.LENGTH_SHORT).show()
                        targetVideoItemState.value = null
                    } else {
                        coroutineScope.launch {
                            val success = com.android.purebilibili.feature.download.DownloadManager
                                .saveImageToGallery(context, coverUrl, item.title)
                            val message = if (success) "封面已保存到相册" else "保存失败"
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        targetVideoItemState.value = null
                    }
                },
                onShare = {
                    pendingVideoShare.value = com.android.purebilibili.feature.video.share.buildVideoSharePayload(
                        title = item.title,
                        bvid = item.bvid,
                        coverUrl = item.pic,
                    )
                    targetVideoItemState.value = null
                },
                onNotInterested = { onDismissVideoCallback(item) },
                onBlockCreator = {
                    viewModel.blockCreator(item)
                    targetVideoItemState.value = null
                },
                onGetPreviewUrl = { bvid, cid -> viewModel.getPreviewVideoUrl(bvid, cid) },
                hazeState = hazeState,
                miuixBackdrop = miuixBackdrop,
                backgroundBlurEnabled = backgroundBlurEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
