// 文件路径: feature/story/StoryScreen.kt
package com.android.purebilibili.feature.story

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.repository.StoryPlayUrls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun StoryScreen(
    viewModel: StoryViewModel = viewModel(),
    onBack: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit = { _, _, _ -> }  // bvid, aid, title
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // 全屏沉浸式
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        if (uiState.isLoading && uiState.items.isEmpty()) {
            // 加载中
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else if (uiState.error != null && uiState.items.isEmpty()) {
            // 错误状态
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.error ?: "加载失败", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.refresh() }) {
                    Text("重试")
                }
            }
        } else {
            // 内容
            val pagerState = rememberPagerState(pageCount = { uiState.items.size })
            
            // 监听页面变化
            LaunchedEffect(pagerState.currentPage) {
                viewModel.updateCurrentIndex(pagerState.currentPage)
            }
            
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = uiState.items.getOrNull(page)
                if (item != null) {
                    StoryPageContent(
                        item = item,
                        isCurrentPage = page == pagerState.currentPage,
                        onVideoClick = { 
                            onVideoClick(
                                item.playerArgs?.bvid ?: "",
                                item.playerArgs?.aid ?: 0,
                                item.title
                            )
                        }
                    )
                }
            }
        }
        
        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun StoryPageContent(
    item: StoryItem,
    isCurrentPage: Boolean,
    onVideoClick: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var playUrls by remember { mutableStateOf<StoryPlayUrls?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    
    // 获取播放 URL - 使用 aid 而非 bvid (Story API 不返回 bvid)
    LaunchedEffect(item.playerArgs?.aid, item.playerArgs?.cid) {
        val aid = item.playerArgs?.aid ?: return@LaunchedEffect
        val cid = item.playerArgs?.cid ?: return@LaunchedEffect
        if (aid <= 0 || cid <= 0) return@LaunchedEffect
        
        isLoading = true
        playUrls = com.android.purebilibili.data.repository.StoryRepository.getVideoPlayUrlByAid(aid, cid)
        isLoading = false
    }
    
    // 创建 ExoPlayer
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE  // 循环播放
            volume = 1.0f  // 确保音量开启
        }
    }
    
    // 创建 MediaSourceFactory
    val mediaSourceFactory = remember(context) {
        DefaultMediaSourceFactory(context)
    }
    
    // 设置视频源 (支持 DASH 音视频分离)
    LaunchedEffect(playUrls) {
        val urls = playUrls ?: return@LaunchedEffect
        
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(urls.videoUrl))
        
        val finalSource = if (!urls.audioUrl.isNullOrEmpty()) {
            // DASH 格式：合并视频和音频流
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(urls.audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            // durl 格式：音视频合一，直接使用视频源
            videoSource
        }
        
        exoPlayer.setMediaSource(finalSource)
        exoPlayer.prepare()
        if (isCurrentPage) {
            exoPlayer.play()
        }
    }
    
    // 当前页面状态变化时控制播放
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && playUrls != null) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }
    
    // 手动控制播放/暂停
    LaunchedEffect(isPlaying) {
        if (isCurrentPage && playUrls != null) {
            if (isPlaying) exoPlayer.play() else exoPlayer.pause()
        }
    }
    
    // 释放播放器
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        isPlaying = !isPlaying 
                    },
                    onDoubleTap = {
                        // 双击点赞动画
                        showDoubleTapHeart = true
                    },
                    onLongPress = {
                        // 长按进入全屏视频详情页
                        onVideoClick()
                    }
                )
            }
    ) {
        // 视频背景：先显示封面，视频加载后显示播放器
        if (playUrls == null || isLoading) {
            // 封面图
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 加载指示器
            if (isLoading && isCurrentPage) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp
                )
            }
        } else {
            // ExoPlayer 视频播放器
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false  // 隐藏默认控制器
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 暂停图标
        if (!isPlaying && isCurrentPage && playUrls != null) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // 双击爱心动画
        if (showDoubleTapHeart) {
            LaunchedEffect(Unit) {
                delay(800)
                showDoubleTapHeart = false
            }
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(100.dp),
                tint = Color.Red
            )
        }
        
        // 右侧互动栏
        RightActionBar(
            item = item,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
        
        // 底部信息栏
        BottomInfoBar(
            item = item,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun RightActionBar(
    item: StoryItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()
    
    // 状态管理
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(item.stat?.like ?: 0) }
    var isFavorited by remember { mutableStateOf(false) }
    var favoriteCount by remember { mutableIntStateOf(item.stat?.favorite ?: 0) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // UP 主头像
        Box(
            modifier = Modifier.clickable {
                haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                // TODO: 点击跳转到 UP 主空间
            }
        ) {
            AsyncImage(
                model = item.owner?.face,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
            // 关注按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6699))
                    .clickable {
                        haptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                        // TODO: 关注 UP 主
                    }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "关注",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 点赞
        ActionButton(
            icon = Icons.Filled.Favorite,
            count = formatCount(likeCount),
            tint = if (isLiked) Color.Red else Color.White,
            onClick = {
                haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                val newLiked = !isLiked
                isLiked = newLiked
                likeCount += if (newLiked) 1 else -1
                
                // 调用 API
                scope.launch {
                    try {
                        val aid = item.playerArgs?.aid ?: return@launch
                        com.android.purebilibili.core.network.NetworkModule.api.likeVideo(
                            aid = aid,
                            like = if (newLiked) 1 else 2,
                            csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
                        )
                    } catch (e: Exception) {
                        // 失败时回退状态
                        isLiked = !newLiked
                        likeCount += if (newLiked) -1 else 1
                    }
                }
            }
        )
        
        // 评论
        ActionButton(
            icon = Icons.Filled.ChatBubble,
            count = formatCount(item.stat?.reply ?: 0),
            tint = Color.White,
            onClick = {
                haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                // TODO: 打开评论弹窗
                android.widget.Toast.makeText(context, "评论功能开发中", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
        
        // 收藏
        ActionButton(
            icon = Icons.Filled.Star,
            count = formatCount(favoriteCount),
            tint = if (isFavorited) Color(0xFFFFC107) else Color.White,
            onClick = {
                haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                isFavorited = !isFavorited
                favoriteCount += if (isFavorited) 1 else -1
                // TODO: 调用收藏 API
                android.widget.Toast.makeText(context, if (isFavorited) "已收藏" else "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
        
        // 分享
        ActionButton(
            icon = Icons.Filled.Share,
            count = formatCount(item.stat?.share ?: 0),
            tint = Color.White,
            onClick = {
                haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                // 分享功能
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, 
                        "【${item.owner?.name}】${item.title}\nhttps://www.bilibili.com/video/av${item.playerArgs?.aid}")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享到"))
            }
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    count: String,
    tint: Color,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = count,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BottomInfoBar(
    item: StoryItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(end = 60.dp)  // 留出右侧互动栏空间
        ) {
            // UP 主名称
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${item.owner?.name ?: ""}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                // 认证标识
                if (item.owner?.officialVerify?.type != -1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color(0xFF00AEEC),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 视频标题
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // 标签
            item.tag?.tagName?.let { tagName ->
                if (tagName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "#$tagName",
                        color = Color(0xFF00AEEC),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1f万", count / 10000f)
        count >= 1000 -> String.format("%.1fk", count / 1000f)
        else -> count.toString()
    }
}
