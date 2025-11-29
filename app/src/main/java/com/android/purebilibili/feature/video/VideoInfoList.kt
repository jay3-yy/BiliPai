// 文件路径: feature/video/VideoInfoList.kt
package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.theme.BiliPink
import kotlinx.coroutines.launch
import master.flame.danmaku.ui.widget.DanmakuView

@Composable
fun VideoInfoList(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    player: ExoPlayer,
    danmakuView: DanmakuView
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 到底自动加载评论逻辑
    val shouldLoadComments by remember {
        derivedStateOf {
            if (uiState !is PlayerUiState.Success) return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 3
                    && !uiState.isRepliesLoading && uiState.repliesError == null && !uiState.isRepliesEnd
        }
    }

    LaunchedEffect(shouldLoadComments) {
        if (shouldLoadComments && uiState is PlayerUiState.Success) {
            viewModel.loadComments(uiState.info.aid)
        }
    }

    when (uiState) {
        is PlayerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BiliPink) }
        is PlayerUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载失败: ${uiState.msg}", color = Color.Red) }
        is PlayerUiState.Success -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                // --- 复用 VideoPlayerComponents.kt 中的组件 ---
                item { VideoHeaderSection(uiState.info) }

                item {
                    ActionButtonsRow(
                        info = uiState.info,
                        onCommentClick = {
                            coroutineScope.launch {
                                // 点击评论按钮，滚动到评论区
                                val targetIndex = 5 + uiState.related.size
                                if (uiState.replies.isNotEmpty()) {
                                    listState.animateScrollToItem(targetIndex)
                                } else {
                                    viewModel.loadComments(uiState.info.aid)
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    )
                }

                item { if (uiState.info.desc.isNotBlank()) DescriptionSection(uiState.info.desc) }

                item { HorizontalDivider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }

                item { Text("更多推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
                items(uiState.related) { video ->
                    RelatedVideoItem(video, onClick = {
                        player.stop(); player.clearMediaItems(); danmakuView.release()
                        viewModel.loadVideo(video.bvid)
                    })
                }

                item { HorizontalDivider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }

                // --- 评论列表 ---
                if (uiState.replies.isNotEmpty()) {
                    item { ReplyHeader(count = uiState.replyCount) }
                    items(uiState.replies) { reply ->
                        ReplyItemView(
                            item = reply,
                            emoteMap = uiState.emoteMap,
                            onClick = {},
                            onSubClick = { rootReply -> viewModel.openSubReply(rootReply) }
                        )
                    }
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(if (uiState.isRepliesEnd) "没有更多评论了~" else "加载中...", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                } else if (uiState.isRepliesLoading) {
                    item { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BiliPink) } }
                } else if (uiState.repliesError != null) {
                    item { Button(onClick = { viewModel.loadComments(uiState.info.aid) }) { Text("点击重试") } }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            if (uiState.isRepliesEnd) Text("暂无评论", color = Color.LightGray)
                            else TextButton(onClick = { viewModel.loadComments(uiState.info.aid) }) { Text("点击加载评论", color = BiliPink) }
                        }
                    }
                }
            }
        }
    }
}