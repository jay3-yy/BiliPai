package com.android.purebilibili.feature.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness7
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
import com.android.purebilibili.core.util.ScreenUtils
import com.android.purebilibili.core.util.StreamDataSource
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

    // ------------- 手势控制状态 -------------
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var isGestureVisible by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // 手势处理
    val gestureModifier = if (isFullscreen) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    isGestureVisible = true
                    if (offset.x < size.width / 2) {
                        // 亮度
                        gestureIcon = Icons.Rounded.Brightness7
                        val activity = context.findActivity()
                        val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                        gesturePercent = if (currentBrightness < 0) {
                            try {
                                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                            } catch (e: Exception) { 0.5f }
                        } else {
                            currentBrightness
                        }
                    } else {
                        // 音量
                        gestureIcon = Icons.Rounded.VolumeUp
                        gesturePercent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    }
                },
                onDragEnd = { isGestureVisible = false },
                onDragCancel = { isGestureVisible = false }
            ) { _, dragAmount ->
                val delta = -dragAmount / (size.height / 2)
                val newPercent = (gesturePercent + delta).coerceIn(0f, 1f)

                if (gestureIcon == Icons.Rounded.Brightness7) {
                    val activity = context.findActivity()
                    val lp = activity?.window?.attributes
                    lp?.screenBrightness = newPercent
                    activity?.window?.attributes = lp
                    gesturePercent = newPercent
                } else {
                    val newVolume = (newPercent * maxVolume).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    gesturePercent = newPercent
                }
            }
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
            // 恢复系统亮度
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

            // 手势反馈 UI
            if (isGestureVisible && isFullscreen) {
                Box(
                    modifier = Modifier.align(Alignment.Center).size(100.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(gestureIcon ?: Icons.Rounded.Brightness7, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { gesturePercent }, modifier = Modifier.width(60.dp).height(4.dp), color = BiliPink, trackColor = Color.White.copy(0.3f))
                    }
                }
            }

            if (state is PlayerUiState.Success) {
                val s = state as PlayerUiState.Success
                // 调用 Overlay 组件
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
                        // 调用组件
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