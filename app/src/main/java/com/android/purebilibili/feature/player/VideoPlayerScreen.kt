package com.android.purebilibili.feature.player

import androidx.compose.animation.animateContentSize
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
// 👇 图标导入
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ThumbUp
// 👇 Material 3
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.BiliDanmakuParser
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.ScreenUtils
import com.android.purebilibili.core.util.StreamDataSource
import com.android.purebilibili.core.util.bouncyClickable
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.delay
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    bvid: String,
    cid: Long,
    viewModel: PlayerViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var isDanmakuOn by remember { mutableStateOf(true) }

    // 统一的返回逻辑
    val handleBackPress = {
        if (isFullscreen) {
            isFullscreen = false
            ScreenUtils.setFullScreen(context, false)
        } else {
            onBack()
        }
    }

    val danmakuContext = remember {
        DanmakuContext.create().apply {
            setDanmakuStyle(0, 3f)
            isDuplicateMergingEnabled = true
            setScrollSpeedFactor(1.2f)
            setScaleTextSize(1.0f)
        }
    }
    var danmakuViewRef by remember { mutableStateOf<IDanmakuView?>(null) }

    LaunchedEffect(bvid) { viewModel.loadVideo(bvid) }

    val player = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            danmakuViewRef?.release()
            ScreenUtils.setFullScreen(context, false)
        }
    }

    LaunchedEffect(state) {
        if (state is PlayerUiState.Success) {
            val s = state as PlayerUiState.Success
            val url = s.playUrl

            if (player.currentMediaItem?.localConfiguration?.uri.toString() != url) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                if (s.startPosition > 0) player.seekTo(s.startPosition)
                player.play()
            }

            if (s.danmakuStream != null && danmakuViewRef != null) {
                val parser = BiliDanmakuParser()
                val dataSource = s.danmakuStream
                try {
                    parser.load(StreamDataSource(dataSource))
                    danmakuViewRef?.prepare(parser, danmakuContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(player.isPlaying) {
        while (true) {
            if (danmakuViewRef?.isPrepared == true) {
                if (player.isPlaying && isDanmakuOn) {
                    if (danmakuViewRef?.isPaused == true) danmakuViewRef?.resume()
                    val dTime = danmakuViewRef!!.currentTime
                    val pTime = player.currentPosition
                    if (abs(pTime - dTime) > 1000) {
                        danmakuViewRef!!.seekTo(pTime)
                    }
                } else {
                    if (danmakuViewRef?.isPaused == false) danmakuViewRef?.pause()
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(isDanmakuOn) {
        if (isDanmakuOn) danmakuViewRef?.show() else danmakuViewRef?.hide()
    }

    BackHandler(enabled = true) {
        handleBackPress()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFullscreen) Modifier.weight(1f)
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AndroidView(
                factory = { ctx ->
                    DanmakuView(ctx).apply {
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

            if (state is PlayerUiState.Success) {
                val s = state as PlayerUiState.Success
                VideoPlayerOverlay(
                    player = player,
                    title = s.info.title,
                    isFullscreen = isFullscreen,
                    isDanmakuOn = isDanmakuOn,
                    currentQualityLabel = s.qualityLabels.getOrNull(s.qualityIds.indexOf(s.currentQuality)) ?: "自动",
                    qualityLabels = s.qualityLabels,
                    onQualitySelected = { index ->
                        val newQualityId = s.qualityIds[index]
                        viewModel.changeQuality(newQualityId, player.currentPosition)
                    },
                    onToggleDanmaku = { isDanmakuOn = !isDanmakuOn },
                    onBack = handleBackPress,
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                        ScreenUtils.setFullScreen(context, isFullscreen)
                    }
                )
            }
        }

        if (!isFullscreen) {
            when (val s = state) {
                is PlayerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BiliPink) }
                is PlayerUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载失败: ${s.msg}", color = Color.Red) }
                is PlayerUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item { VideoHeaderSection(s.info) }
                        item { ActionButtonsRow(s.info) }
                        item { DescriptionSection(s.info.desc) }
                        item { Divider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }
                        item { Text("更多推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
                        items(s.related) { video ->
                            RelatedVideoItem(video, onClick = {
                                player.stop()
                                player.clearMediaItems()
                                danmakuViewRef?.release()
                                viewModel.loadVideo(video.bvid)
                            })
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerOverlay(
    player: ExoPlayer,
    title: String,
    isFullscreen: Boolean,
    isDanmakuOn: Boolean,
    currentQualityLabel: String,
    qualityLabels: List<String>,
    onQualitySelected: (Int) -> Unit,
    onToggleDanmaku: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showQualityMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible, isPlaying) {
        if (isVisible && isPlaying) {
            delay(3000)
            isVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = player.currentPosition
            val rawDuration = player.duration
            duration = if (rawDuration < 0) 0L else rawDuration
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isVisible = !isVisible }
    ) {
        // 使用 AnimatedVisibility 让显示隐藏更平滑
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // --- 顶部渐变 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                )

                // --- 底部渐变 (稍微加高一点，保证控制栏背景清晰) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                )

                // --- 顶部操作栏 ---
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }

                // --- 中间播放/暂停按钮 ---
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // --- 底部控制栏 (重构版：更紧凑、更贴底) ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding() // 避开手势条
                        // 🔥 核心修改：极小的底部间距，让内容贴近底部
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                ) {
                    // 第一行：进度条 + 时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(32.dp) // 限制高度
                    ) {
                        Text(
                            text = FormatUtils.formatDuration((currentPosition / 1000).toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp // 🔥 缩小字体
                        )

                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = {
                                val seekTime = (it * duration).toLong()
                                player.seekTo(seekTime)
                                currentPosition = seekTime
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp), // 减小 Slider 左右间距
                            colors = SliderDefaults.colors(
                                thumbColor = BiliPink,
                                activeTrackColor = BiliPink,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Text(
                            text = FormatUtils.formatDuration((duration / 1000).toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp // 🔥 缩小字体
                        )
                    }

                    // 第二行：功能按钮 (靠右)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onToggleDanmaku) {
                            Icon(
                                imageVector = if (isDanmakuOn) Icons.Outlined.Subtitles else Icons.Outlined.SubtitlesOff,
                                contentDescription = "Danmaku",
                                tint = if (isDanmakuOn) BiliPink else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 🔥 优化：清晰度按钮 (半透明圆角背景，去边框)
                        Box {
                            Surface(
                                onClick = { showQualityMenu = true },
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f), // 半透明背景
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = currentQualityLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false }
                            ) {
                                qualityLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { onQualitySelected(index); showQualityMenu = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Full",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoHeaderSection(info: ViewInfo) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(info.owner.face)).crossfade(true).build(),
                contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = info.owner.name, style = MaterialTheme.typography.titleSmall, color = BiliPink)
                Text(text = "UP主", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = BiliPink), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("+ 关注", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        var expanded by remember { mutableStateOf(false) }
        Text(text = info.title, style = MaterialTheme.typography.titleLarge, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded })
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = Color.Gray)
            Text(" ${FormatUtils.formatStat(info.stat.view)}  ", fontSize = 12.sp, color = Color.Gray)
            Icon(Icons.Default.FormatListBulleted, null, Modifier.size(16.dp), tint = Color.Gray)
            Text(" ${FormatUtils.formatStat(info.stat.danmaku)}  ", fontSize = 12.sp, color = Color.Gray)
            Text("  ${info.bvid}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ActionButtonsRow(info: ViewInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionButton(Icons.Rounded.ThumbUp, FormatUtils.formatStat(info.stat.like))
        ActionButton(Icons.Default.MonetizationOn, "投币")
        ActionButton(Icons.Default.Star, "收藏")
        ActionButton(Icons.Default.Share, "分享")
    }
}

@Composable
fun ActionButton(icon: ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .bouncyClickable { }
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun DescriptionSection(desc: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
        if (desc.isNotBlank()) {
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(text = if (expanded) "收起" else "展开更多", color = Color.Gray, fontSize = 12.sp)
                Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RelatedVideoItem(video: RelatedVideo, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.width(140.dp).height(88.dp).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(video.pic)).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(text = FormatUtils.formatDuration(video.duration), color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp)).padding(horizontal = 2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).height(88.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(text = video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = video.owner.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}