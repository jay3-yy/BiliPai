// 文件路径: feature/video/VideoPlayerState.kt
package com.android.purebilibili.feature.video

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.ScreenUtils
import kotlinx.coroutines.delay
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

class VideoPlayerState(
    val player: ExoPlayer,
    val danmakuView: DanmakuView
) {
    var isDanmakuOn by mutableStateOf(true)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    context: Context,
    viewModel: PlayerViewModel,
    bvid: String
): VideoPlayerState {
    // 播放器初始化
    val player = remember {
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
            .setDefaultRequestProperties(headers)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                // 🔥🔥 核心修复 1: 必须调用 prepare() 才会开始缓冲数据！
                prepare()
                // 🔥🔥 核心修复 2: 开启自动播放
                playWhenReady = true
            }
    }

    // 弹幕初始化
    val danmakuContext = remember {
        DanmakuContext.create().apply {
            setDanmakuStyle(0, 3f)
            isDuplicateMergingEnabled = true
            setScrollSpeedFactor(1.2f)
            setScaleTextSize(1.0f)
        }
    }
    val danmakuView = remember { DanmakuView(context) }

    // 状态保持类
    val holder = remember { VideoPlayerState(player, danmakuView) }

    // 生命周期绑定
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            danmakuView.release()
            ScreenUtils.setFullScreen(context, false)
            (context as? ComponentActivity)?.window?.attributes?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    // 逻辑绑定
    LaunchedEffect(bvid) { viewModel.loadVideo(bvid) }
    LaunchedEffect(player) { viewModel.attachPlayer(player) }

    // 弹幕同步逻辑
    LaunchedEffect(player.isPlaying) {
        while (true) {
            if (danmakuView.isPrepared && holder.isDanmakuOn) {
                if (player.isPlaying) {
                    if (danmakuView.isPaused) danmakuView.resume()
                    if (abs(player.currentPosition - danmakuView.currentTime) > 1000) {
                        danmakuView.seekTo(player.currentPosition)
                    }
                } else if (!danmakuView.isPaused) {
                    danmakuView.pause()
                }
            }
            delay(500)
        }
    }
    LaunchedEffect(holder.isDanmakuOn) {
        if (holder.isDanmakuOn) danmakuView.show() else danmakuView.hide()
    }

    return holder
}