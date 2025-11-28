package com.android.purebilibili.feature.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures // 👈 改用通用拖拽检测
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

// 定义手势模式
enum class GestureMode { None, Brightness, Volume, Seek }

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

    // ------------- 手势控制状态 -------------
    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) } // 亮度/音量的百分比
    var seekTargetTime by remember { mutableLongStateOf(0L) }  // 进度调节的目标时间
    var isGestureVisible by remember { mutableStateOf(false) }

    // 记录手势开始时的状态
    var startX by remember { mutableFloatStateOf(0f) }
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0f) }
    var startPosition by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // 手势处理
    val gestureModifier = if (isFullscreen) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isGestureVisible = true
                    gestureMode = GestureMode.None
                    startX = offset.x

                    // 记录初始状态，防止滑动时跳变
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startPosition = viewModel.getPlayerCurrentPosition() // 需要获取当前播放位置

                    val activity = context.findActivity()
                    val lp = activity?.window?.attributes
                    val currentB = lp?.screenBrightness ?: -1f
                    startBrightness = if (currentB < 0) {
                        try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                        } catch (e: Exception) { 0.5f }
                    } else currentB
                },
                onDragEnd = {
                    if (gestureMode == GestureMode.Seek) {
                        viewModel.seekTo(seekTargetTime) // 执行跳转
                    }
                    isGestureVisible = false
                    gestureMode = GestureMode.None
                },
                onDragCancel = {
                    isGestureVisible = false
                    gestureMode = GestureMode.None
                },
                onDrag = { change, dragAmount ->
                    // 1. 判断并锁定方向
                    if (gestureMode == GestureMode.None) {
                        if (abs(dragAmount.x) > abs(dragAmount.y)) {
                            // 横向移动更多 -> 判定为进度调节
                            gestureMode = GestureMode.Seek
                        } else {
                            // 纵向移动更多 -> 判定为亮度或音量
                            // 根据起始按下位置(startX)判断左右
                            gestureMode = if (startX < size.width / 2) GestureMode.Brightness else GestureMode.Volume
                        }
                    }

                    // 2. 根据锁定模式执行逻辑
                    when (gestureMode) {
                        GestureMode.Seek -> {
                            val duration = viewModel.getPlayerDuration()
                            // 灵敏度：屏幕宽度对应约 90秒 (可调整)
                            // 或者：1px = 200ms
                            val seekDelta = (dragAmount.x * 200).toLong()
                            seekTargetTime = (seekTargetTime + seekDelta).coerceIn(0L, duration)
                            // 第一次进入 Seek 模式时，初始化 seekTargetTime 为 startPosition
                            if (seekTargetTime == 0L && startPosition > 0) seekTargetTime = startPosition
                        }
                        GestureMode.Brightness -> {
                            // 向上滑 y 是负数，想要增加亮度，所以要 -delta
                            val delta = -dragAmount.y / (size.height / 2)
                            val newPercent = (gesturePercent + delta).coerceIn(0f, 1f)
                            // 实际上这里应该基于 startBrightness 累加，简化版直接用 percent 累加
                            // 为了更跟手，重新计算：
                            gesturePercent = (gesturePercent + delta).coerceIn(0f, 1f)

                            val activity = context.findActivity()
                            val lp = activity?.window?.attributes
                            lp?.screenBrightness = gesturePercent
                            activity?.window?.attributes = lp
                            gestureIcon = Icons.Rounded.Brightness7
                        }
                        GestureMode.Volume -> {
                            val delta = -dragAmount.y / (size.height / 2)
                            gesturePercent = (gesturePercent + delta).coerceIn(0f, 1f)
                            val newVol = (gesturePercent * maxVolume).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            gestureIcon = Icons.Rounded.VolumeUp
                        }
                        else -> {}
                    }
                }
            )
        }
    } else {
        Modifier
    }

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

    // 初始化播放器
    val player = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    // 把 player 传给 ViewModel 以便获取时长和位置 (或者直接在这里操作)
    // 简便起见，我们在这里直接把 player 赋值给 viewModel 的临时变量，或者直接在 UI 层获取
    // 为了不破坏 ViewModel 结构，我们使用副作用更新 ViewModel 中的 player 引用，或者直接在 UI 操作
    // 这里采用直接在 UI 操作 player 的方式，因为 gesture 是 UI 行为
    LaunchedEffect(player) {
        viewModel.attachPlayer(player)
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            danmakuViewRef?.release()
            ScreenUtils.setFullScreen(context, false)
            val activity = context.findActivity()
            val lp = activity?.window?.attributes
            lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = lp
        }
    }

    LaunchedEffect(state) {
        if (state is PlayerUiState.Success) {
            val s = state as PlayerUiState.Success
            if (player.currentMediaItem?.localConfiguration?.uri.toString() != s.playUrl) {
                player.setMediaItem(MediaItem.fromUri(s.playUrl))
                player.prepare()
                if (s.startPosition > 0) player.seekTo(s.startPosition)
                player.play()
            }
            if (s.danmakuStream != null && danmakuViewRef != null) {
                try {
                    val parser = BiliDanmakuParser()
                    parser.load(StreamDataSource(s.danmakuStream))
                    danmakuViewRef?.prepare(parser, danmakuContext)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    LaunchedEffect(player.isPlaying) {
        while (true) {
            if (danmakuViewRef?.isPrepared == true) {
                if (player.isPlaying && isDanmakuOn) {
                    if (danmakuViewRef?.isPaused == true) danmakuViewRef?.resume()
                    val diff = abs(player.currentPosition - danmakuViewRef!!.currentTime)
                    if (diff > 1000) danmakuViewRef!!.seekTo(player.currentPosition)
                } else {
                    if (danmakuViewRef?.isPaused == false) danmakuViewRef?.pause()
                }
            }
            delay(500)
        }
    }
    LaunchedEffect(isDanmakuOn) { if (isDanmakuOn) danmakuViewRef?.show() else danmakuViewRef?.hide() }

    BackHandler(enabled = true) { handleBackPress() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .then(gestureModifier)
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); useController = false } },
                modifier = Modifier.fillMaxSize()
            )
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

            // ------------- 手势反馈 UI -------------
            if (isGestureVisible && isFullscreen) {
                Box(
                    modifier = Modifier.align(Alignment.Center)
                        .size(120.dp) // 稍微大一点以容纳时间文字
                        .background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (gestureMode == GestureMode.Seek) {
                            // 进度调节显示
                            Icon(
                                if (seekTargetTime > startPosition) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                                null, tint = Color.White, modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val duration = viewModel.getPlayerDuration()
                            Text(
                                text = "${FormatUtils.formatDuration((seekTargetTime/1000).toInt())} / ${FormatUtils.formatDuration((duration/1000).toInt())}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            // 亮度/音量调节显示
                            Icon(gestureIcon ?: Icons.Rounded.Brightness7, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { gesturePercent }, modifier = Modifier.width(60.dp).height(4.dp), color = BiliPink, trackColor = Color.White.copy(0.3f))
                        }
                    }
                }
            }

            if (state is PlayerUiState.Success) {
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

        if (!isFullscreen) {
            when (val s = state) {
                is PlayerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BiliPink) }
                is PlayerUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载失败: ${s.msg}", color = Color.Red) }
                is PlayerUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item { VideoHeaderSection(s.info) }
                        item { ActionButtonsRow(s.info) }
                        item { DescriptionSection(s.info.desc) }
                        item { HorizontalDivider(thickness = 8.dp, color = Color(0xFFF1F2F3)) }
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

// 这个工具函数只保留在 VideoPlayerScreen.kt 中
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}