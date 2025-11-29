package com.android.purebilibili.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.BiliDanmakuParser
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.ScreenUtils
import com.android.purebilibili.core.util.StreamDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

// 手势枚举
enum class GestureMode { None, Brightness, Volume, Seek }

@OptIn(UnstableApi::class)
@Composable
fun VideoDetailScreen(
    bvid: String,
    isInPipMode: Boolean,
    viewModel: PlayerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val showStatsInfo by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }

    // 滚动状态控制
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var isFullscreen by remember { mutableStateOf(false) }
    val effectiveFullscreen = isInPipMode || isFullscreen

    var isDanmakuOn by remember { mutableStateOf(true) }
    var realVideoSize by remember { mutableStateOf("加载中...") }

    // --- 手势状态 ---
    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var isGestureVisible by remember { mutableStateOf(false) }
    var startX by remember { mutableFloatStateOf(0f) }
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0f) }
    var startPosition by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val gestureModifier = if (effectiveFullscreen && !isInPipMode) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isGestureVisible = true
                    gestureMode = GestureMode.None
                    startX = offset.x
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startPosition = viewModel.getPlayerCurrentPosition()
                    val activity = context.findActivity()
                    val lp = activity?.window?.attributes
                    val currentB = lp?.screenBrightness ?: -1f
                    startBrightness = if (currentB < 0) 0.5f else currentB
                },
                onDragEnd = {
                    if (gestureMode == GestureMode.Seek) viewModel.seekTo(seekTargetTime)
                    isGestureVisible = false
                    gestureMode = GestureMode.None
                },
                onDragCancel = { isGestureVisible = false; gestureMode = GestureMode.None },
                onDrag = { _, dragAmount ->
                    if (gestureMode == GestureMode.None) {
                        if (abs(dragAmount.x) > abs(dragAmount.y)) gestureMode = GestureMode.Seek
                        else gestureMode = if (startX < size.width / 2) GestureMode.Brightness else GestureMode.Volume
                    }
                    when (gestureMode) {
                        GestureMode.Seek -> {
                            val duration = viewModel.getPlayerDuration()
                            val seekDelta = (dragAmount.x * 200).toLong()
                            seekTargetTime = (seekTargetTime + seekDelta).coerceIn(0L, duration)
                        }
                        GestureMode.Brightness -> {
                            val delta = -dragAmount.y / (size.height / 2)
                            gesturePercent = (startBrightness + delta).coerceIn(0f, 1f)
                            context.findActivity()?.window?.attributes?.screenBrightness = gesturePercent
                            gestureIcon = Icons.Rounded.Brightness7
                        }
                        GestureMode.Volume -> {
                            val delta = -dragAmount.y / (size.height / 2)
                            gesturePercent = (delta + (startVolume.toFloat() / maxVolume)).coerceIn(0f, 1f)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (gesturePercent * maxVolume).toInt(), 0)
                            gestureIcon = Icons.Rounded.VolumeUp
                        }
                        else -> {}
                    }
                }
            )
        }
    } else Modifier

    val handleBackPress = {
        if (isFullscreen) {
            isFullscreen = false
            ScreenUtils.setFullScreen(context, false)
        } else {
            onBack()
        }
    }

    val danmakuContext = remember { DanmakuContext.create().apply { setDanmakuStyle(0, 3f); isDuplicateMergingEnabled = true; setScrollSpeedFactor(1.2f); setScaleTextSize(1.0f) } }
    var danmakuViewRef by remember { mutableStateOf<IDanmakuView?>(null) }

    LaunchedEffect(bvid) { viewModel.loadVideo(bvid) }

    val player = remember {
        val headers = mapOf("Referer" to "https://www.bilibili.com", "User-Agent" to "Mozilla/5.0...")
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient).setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }

    LaunchedEffect(player) {
        viewModel.attachPlayer(player)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { realVideoSize = "${videoSize.width} x ${videoSize.height}" }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            danmakuViewRef?.release()
            ScreenUtils.setFullScreen(context, false)
            context.findActivity()?.window?.attributes?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    LaunchedEffect(state) {
        if (state is PlayerUiState.Success) {
            val s = state as PlayerUiState.Success

            // 播放器配置
            if (player.currentMediaItem?.localConfiguration?.uri.toString() != s.playUrl) {
                player.setMediaItem(MediaItem.fromUri(s.playUrl))
                player.prepare()
                if (s.startPosition > 0) player.seekTo(s.startPosition)
                player.play()
            }

            // 弹幕配置
            if (s.danmakuStream != null && danmakuViewRef != null && !danmakuViewRef!!.isPrepared) {
                try {
                    val parser = BiliDanmakuParser()
                    parser.load(StreamDataSource(s.danmakuStream))
                    danmakuViewRef?.prepare(parser, danmakuContext)
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 🔥 新增：自动加载评论 (如果尚未加载)
            if (s.replies.isEmpty() && !s.isRepliesLoading) {
                // 注意：这里需要 aid (av号)，s.info.aid 提供了这个值
                viewModel.loadComments(s.info.aid)
            }
        }
    }

    LaunchedEffect(player.isPlaying) {
        while (true) {
            if (danmakuViewRef?.isPrepared == true && isDanmakuOn) {
                if (player.isPlaying) {
                    if (danmakuViewRef?.isPaused == true) danmakuViewRef?.resume()
                    if (abs(player.currentPosition - danmakuViewRef!!.currentTime) > 1000) danmakuViewRef!!.seekTo(player.currentPosition)
                } else {
                    if (danmakuViewRef?.isPaused == false) danmakuViewRef?.pause()
                }
            }
            delay(500)
        }
    }
    LaunchedEffect(isDanmakuOn) { if (isDanmakuOn) danmakuViewRef?.show() else danmakuViewRef?.hide() }

    BackHandler(enabled = true) { handleBackPress() }

    // 根布局背景设为黑色，以支持列表顶部的圆角效果
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (effectiveFullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .then(gestureModifier)
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); useController = false } },
                modifier = Modifier.fillMaxSize()
            )

            if (!isInPipMode) {
                AndroidView(
                    factory = {
                        DanmakuView(it).apply {
                            danmakuViewRef = this
                            enableDanmakuDrawingCache(true)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setCallback(object : master.flame.danmaku.controller.DrawHandler.Callback {
                                override fun prepared() { start() }
                                override fun updateTimer(timer: master.flame.danmaku.danmaku.model.DanmakuTimer?) {}
                                override fun drawingFinished() {}
                                override fun danmakuShown(danmaku: master.flame.danmaku.danmaku.model.BaseDanmaku?) {}
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isGestureVisible && effectiveFullscreen && !isInPipMode) {
                Box(
                    modifier = Modifier.align(Alignment.Center).size(120.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (gestureMode == GestureMode.Seek) {
                            Icon(if (seekTargetTime > startPosition) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Text(FormatUtils.formatDuration((seekTargetTime / 1000).toInt()), color = Color.White)
                        } else {
                            Icon(gestureIcon ?: Icons.Rounded.Brightness7, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            LinearProgressIndicator(progress = { gesturePercent }, modifier = Modifier.width(60.dp), color = BiliPink)
                        }
                    }
                }
            }

            // Stats for nerds
            if (effectiveFullscreen && showStatsInfo && !isInPipMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 20.dp, start = 20.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "实际渲染: $realVideoSize",
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            if (state is PlayerUiState.Success && !isInPipMode) {
                val s = state as PlayerUiState.Success
                VideoPlayerOverlay(
                    player = player,
                    title = s.info.title,
                    isFullscreen = isFullscreen,
                    isDanmakuOn = isDanmakuOn,
                    currentQualityLabel = s.qualityLabels.getOrNull(s.qualityIds.indexOf(s.currentQuality)) ?: "自动",
                    qualityLabels = s.qualityLabels,
                    onQualitySelected = { viewModel.changeQuality(s.qualityIds[it], player.currentPosition) },
                    onToggleDanmaku = { isDanmakuOn = !isDanmakuOn },
                    onBack = handleBackPress,
                    onToggleFullscreen = { isFullscreen = !isFullscreen; ScreenUtils.setFullScreen(context, isFullscreen) }
                )
            }
        }

        if (!effectiveFullscreen) {
            when (val s = state) {
                is PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BiliPink) }
                is PlayerUiState.Error -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { Text("加载失败: ${s.msg}", color = Color.Red) }
                is PlayerUiState.Success -> {
                    LazyColumn(
                        state = listState, // 🔥 绑定滚动状态
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                        )
                    ) {
                        item { VideoHeaderSection(s.info) }

                        item {
                            // 🔥 传入点击回调：滚动到评论区
                            ActionButtonsRow(
                                info = s.info,
                                onCommentClick = {
                                    coroutineScope.launch {
                                        // 智能计算滚动位置：Header(1) + Actions(1) + Desc(1) + Divider(1) + Title(1) + Related(N) + Divider(1) + CommentHeader(1)
                                        // 简单粗暴的方式：滚动到相关视频数量 + 6 的位置，或者直接使用 animateScrollToItem
                                        val targetIndex = 5 + s.related.size + 1 // 大致定位
                                        // 如果评论列表已存在，直接滚动到 Header
                                        if (s.replies.isNotEmpty()) {
                                            // 找到评论 Header 的索引比较复杂，这里简化为滚动到列表底部或特定偏移
                                            // 更好的方法是使用 sticky header 或 key，但 LazyColumn 简单的索引滚动也够用了
                                            listState.animateScrollToItem(targetIndex)
                                        } else {
                                            // 如果没评论，也可以尝试触发加载
                                            viewModel.loadComments(s.info.aid)
                                        }
                                    }
                                }
                            )
                        }

                        item { if (s.info.desc.isNotBlank()) DescriptionSection(s.info.desc) }

                        item { HorizontalDivider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }

                        // 推荐视频
                        item { Text("更多推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
                        items(s.related) { video ->
                            RelatedVideoItem(video, onClick = {
                                player.stop(); player.clearMediaItems(); danmakuViewRef?.release()
                                viewModel.loadVideo(video.bvid)
                            })
                        }

                        // 🔥 新增：评论区
                        item { HorizontalDivider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }

                        if (s.replies.isNotEmpty()) {
                            item { ReplyHeader(count = s.replyCount) }
                            items(s.replies) { reply ->
                                ReplyItemView(reply, onClick = {})
                            }
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text("没有更多评论了~", color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        } else if (s.isRepliesLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = BiliPink, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else {
                            // 加载完成但为空，或尚未加载
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { viewModel.loadComments(s.info.aid) }) {
                                        Text("点击加载评论", color = BiliPink)
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

// 核心补丁：Context 扩展函数
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}