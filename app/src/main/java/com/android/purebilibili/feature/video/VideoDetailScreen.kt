package com.android.purebilibili.feature.video

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem   // 必须加这行！
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun VideoDetailScreen(
    bvid: String,
    coverUrl: String,
    onBack: () -> Unit,
    isInPipMode: Boolean = false,
    isVisible: Boolean = true,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var isPipMode by remember { mutableStateOf(isInPipMode) }

    val playerState = rememberVideoPlayerState(
        context = context,
        viewModel = viewModel,
        bvid = bvid
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }

    if (!view.isInEditMode) {
        DisposableEffect(isFullscreen, isLightBackground) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (isFullscreen) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window.statusBarColor = Color.Black.toArgb()
                window.navigationBarColor = Color.Black.toArgb()
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = isLightBackground
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }

            onDispose {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = isLightBackground
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (isFullscreen) {
            VideoPlayerSection(
                playerState = playerState,
                uiState = uiState,
                isFullscreen = true,
                isInPipMode = isPipMode,
                onToggleFullscreen = { isFullscreen = false },
                onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                onBack = { isFullscreen = false }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    VideoPlayerSection(
                        playerState = playerState,
                        uiState = uiState,
                        isFullscreen = false,
                        isInPipMode = isPipMode,
                        onToggleFullscreen = { isFullscreen = true },
                        onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                        onBack = onBack
                    )
                }

                when (uiState) {
                    is PlayerUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BiliPink)
                        }
                    }

                    is PlayerUiState.Success -> {
                        val success = uiState as PlayerUiState.Success
                        VideoContentSection(
                            info = success.info,
                            relatedVideos = success.related,
                            replies = success.replies,                    // 评论列表
                            replyCount = success.replyCount,              // 评论总数
                            emoteMap = success.emoteMap,                  // 表情包
                            isRepliesLoading = success.isRepliesLoading,
                            onRelatedVideoClick = { /* TODO */ }
                        )
                    }

                    is PlayerUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((uiState as PlayerUiState.Error).msg)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.loadVideo(bvid) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
                                ) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoContentSection(
    info: ViewInfo,
    relatedVideos: List<RelatedVideo>,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    onRelatedVideoClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 评论区开始的 index（推荐视频之后）
    val commentStartIndex = 6 + relatedVideos.size

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { VideoHeaderSection(info = info) }

        item {
            ActionButtonsRow(
                info = info,
                onCommentClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(commentStartIndex.coerceAtLeast(0))
                    }
                }
            )
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        item { DescriptionSection(desc = info.desc) }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }

        item { RelatedVideosHeader() }

        items(relatedVideos, key = { it.bvid }) { video ->
            RelatedVideoItem(video = video, onClick = { onRelatedVideoClick(video.bvid) })
        }

        // 评论区开始
        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }

        item {
            ReplyHeader(count = replyCount)
        }

        if (replies.isEmpty() && replyCount > 0 && isRepliesLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliPink)
                }
            }
        } else {
            items(replies, key = { it.rpid }) { reply ->
                ReplyItemView(
                    item = reply,
                    emoteMap = emoteMap,
                    onClick = { /* 打开楼层页 */ },
                    onSubClick = { /* 回复此人 */ }
                )
            }

            if (replies.size < replyCount) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("加载更多...", color = BiliPink)
                    }
                }
            }
        }
    }
}