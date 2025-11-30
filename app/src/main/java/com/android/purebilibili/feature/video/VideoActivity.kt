package com.android.purebilibili.feature.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class VideoActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    // 手动管理全屏状态，默认 false (竖屏)
    private var isFullscreen by mutableStateOf(false)
    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥🔥 修复 1: 强制初始状态为竖屏
        // 防止上次奔溃或退出时卡在横屏状态
        if (savedInstanceState == null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val bvid = intent.getStringExtra("bvid")
        if (bvid.isNullOrBlank()) {
            finish()
            return
        }

        // 根据当前实际配置更新 UI 状态
        updateStateFromConfig(resources.configuration)

        setContent {
            MaterialTheme {
                // 如果 build.gradle 没同步好导致报错，可临时改回 viewModel.uiState.collectAsState()
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

                val playerState = rememberVideoPlayerState(
                    context = this,
                    viewModel = viewModel,
                    bvid = bvid
                )

                // 🔥🔥 修复 2: 拦截返回键
                // 如果当前是全屏，按返回键先退出全屏，而不是直接关闭页面
                BackHandler(enabled = isFullscreen) {
                    toggleFullscreen()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // === 播放器区域 ===
                    Box(
                        modifier = if (isFullscreen) {
                            Modifier.fillMaxSize() // 全屏模式：填满屏幕
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f) // 竖屏模式：固定 16:9
                        }
                    ) {
                        VideoPlayerSection(
                            playerState = playerState,
                            uiState = uiState,
                            isFullscreen = isFullscreen,
                            isInPipMode = isInPipMode,
                            onToggleFullscreen = { toggleFullscreen() },
                            onQualityChange = { quality, pos ->
                                viewModel.changeQuality(quality, pos)
                            },
                            onBack = {
                                // 点击左上角返回按钮：如果是全屏就切竖屏，否则退出
                                if (isFullscreen) toggleFullscreen() else finish()
                            }
                        )
                    }

                    // === 竖屏时的下方内容 (评论/详情) ===
                    if (!isFullscreen && !isInPipMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "评论区与详情页 (待实现)", color = Color.Gray)
                            // TODO: 这里放 VideoDetailInfo(uiState.info) 和 CommentList
                        }
                    }
                }
            }
        }
    }

    // 监听系统配置变化 (Manifest 中必须配置 configChanges)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateStateFromConfig(newConfig)
    }

    // 统一更新状态和系统栏显隐
    private fun updateStateFromConfig(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        isFullscreen = isLandscape

        // 控制状态栏/导航栏 (沉浸式)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isLandscape) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 🔥🔥 修复 3: 切换全屏的核心逻辑
    private fun toggleFullscreen() {
        if (isFullscreen) {
            // 当前是横屏 -> 切回竖屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            // 当前是竖屏 -> 切为横屏
            // 使用 SENSOR_LANDSCAPE 让用户可以左右180度翻转手机
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // --- 画中画 (PiP) 支持 ---
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = viewModel.uiState.value
            // 只有视频加载成功且正在播放时才进入画中画
            if (state is PlayerUiState.Success) {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    companion object {
        fun start(context: Context, bvid: String) {
            val intent = Intent(context, VideoActivity::class.java).apply {
                putExtra("bvid", bvid)
            }
            context.startActivity(intent)
        }
    }
}