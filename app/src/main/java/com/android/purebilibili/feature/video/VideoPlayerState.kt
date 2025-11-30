// 文件路径: feature/video/VideoPlayerState.kt
package com.android.purebilibili.feature.video

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.purebilibili.core.network.NetworkModule
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
    // 🔥 增加 context 作为 key，防止 Context 变化时复用旧实例
    val player = remember(context) {
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
                prepare()
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
    // 🔥 增加 context 作为 key
    val danmakuView = remember(context) { DanmakuView(context) }

    val holder = remember(player, danmakuView) { VideoPlayerState(player, danmakuView) }

    // 生命周期绑定
    DisposableEffect(player, danmakuView) {
        onDispose {
            player.release()
            danmakuView.release()
            // 🔥🔥🔥 删除下面这一行！不要在组件销毁时强制退出全屏，
            // 这会导致全屏切换逻辑冲突，或者在 Activity 重建时强制变回竖屏。
            // ScreenUtils.setFullScreen(context, false)

            // 恢复亮度是可以的
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
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(holder.isDanmakuOn) {
        if (holder.isDanmakuOn) danmakuView.show() else danmakuView.hide()
    }

    return holder
}