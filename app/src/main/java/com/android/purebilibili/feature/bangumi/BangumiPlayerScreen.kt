// 文件路径: feature/bangumi/BangumiPlayerScreen.kt
package com.android.purebilibili.feature.bangumi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import kotlinx.coroutines.launch
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
//  使用提取后的组件
import com.android.purebilibili.feature.bangumi.ui.player.BangumiPlayerView
import com.android.purebilibili.feature.bangumi.ui.player.BangumiMiniProgressBar
import com.android.purebilibili.feature.bangumi.ui.player.BangumiPlayerContent
import com.android.purebilibili.feature.bangumi.ui.player.BangumiErrorContent

/**
 * 番剧播放页面
 * 
 *  [重构] 简化后的主屏幕，播放器组件已拆分到 ui/player/ 目录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiPlayerScreen(
    seasonId: Long,
    epId: Long,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: BangumiPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsState()
    
    //  空降助手状态
    val sponsorSegment by viewModel.currentSponsorSegment.collectAsState()
    val showSponsorSkipButton by viewModel.showSkipButton.collectAsState()
    val sponsorBlockEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSponsorBlockEnabled(context)
        .collectAsState(initial = false)
    
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // 创建 ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    
    // 附加播放器到 ViewModel 并加载番剧
    // 使用同一个 LaunchedEffect 确保顺序执行，避免竞态条件
    LaunchedEffect(exoPlayer, seasonId, epId) {
        // 先附加播放器
        viewModel.attachPlayer(exoPlayer)
        // 然后加载番剧
        viewModel.loadBangumiPlay(seasonId, epId)
    }
    
    //  [优化] 播放错误监听 - 记录日志并触发重试
    DisposableEffect(exoPlayer) {
        val errorListener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("BangumiPlayer", "❌ 播放错误: ${error.errorCodeName} - ${error.message}", error)
                // 可以在这里触发 Toast 或重试逻辑
            }
            
            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                if (error != null) {
                    android.util.Log.w("BangumiPlayer", "⚠️ 播放器错误变化: ${error.errorCodeName}")
                }
            }
            
            //  [调试] 新增：监听播放状态变化
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                    androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "READY"
                    androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("BangumiPlayer", "🎬 播放状态变化: $stateName, isPlaying=${exoPlayer.isPlaying}")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("BangumiPlayer", "▶️ 播放状态: isPlaying=$isPlaying")
            }
        }
        exoPlayer.addListener(errorListener)
        onDispose { exoPlayer.removeListener(errorListener) }
    }
    
    //  空降助手：定期检查播放位置
    LaunchedEffect(sponsorBlockEnabled, uiState) {
        if (sponsorBlockEnabled && uiState is BangumiPlayerState.Success) {
            while (true) {
                kotlinx.coroutines.delay(500)
                viewModel.checkAndSkipSponsor(context)
            }
        }
    }
    
    //  [重构] 弹幕管理器 - 使用单例确保横竖屏切换时保持状态
    val danmakuManager = rememberDanmakuManager()
    
    // 弹幕开关设置
    val scope = rememberCoroutineScope()  //  用于弹幕开关和设置保存
    val danmakuEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuEnabled(context)
        .collectAsState(initial = true)
    
    //  倍速状态
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    
    //  弹幕设置状态
    val danmakuOpacity by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuOpacity(context)
        .collectAsState(initial = 0.85f)
    val danmakuFontScale by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuFontScale(context)
        .collectAsState(initial = 1.0f)
    val danmakuSpeed by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuSpeed(context)
        .collectAsState(initial = 1.0f)
    val danmakuDisplayArea by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuArea(context)
        .collectAsState(initial = 0.5f)
    
    //  弹幕设置变化时实时应用到 DanmakuManager
    LaunchedEffect(danmakuOpacity, danmakuFontScale, danmakuSpeed, danmakuDisplayArea) {
        danmakuManager.updateSettings(
            opacity = danmakuOpacity,
            fontScale = danmakuFontScale,
            speed = danmakuSpeed,
            displayArea = danmakuDisplayArea
        )
    }
    
    // 获取当前剧集 cid
    val currentCid = (uiState as? BangumiPlayerState.Success)?.currentEpisode?.cid ?: 0L
    
    // 加载弹幕 - 在父级组件管理
    //  [修复] 等待播放器 duration 可用后再加载弹幕，启用 Protobuf API
    LaunchedEffect(currentCid, danmakuEnabled) {
        android.util.Log.d("BangumiPlayer", "🎯 Parent Danmaku LaunchedEffect: cid=$currentCid, enabled=$danmakuEnabled")
        if (currentCid > 0 && danmakuEnabled) {
            danmakuManager.isEnabled = true
            
            //  [修复] 等待播放器准备好并获取 duration (最多等待 5 秒)
            var durationMs = 0L
            var retries = 0
            while (durationMs <= 0 && retries < 50) {
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                if (durationMs <= 0) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
            }
            
            android.util.Log.d("BangumiPlayer", "🎯 Loading danmaku for cid=$currentCid, duration=${durationMs}ms (after $retries retries)")
            danmakuManager.loadDanmaku(currentCid, durationMs)  //  传入时长启用 Protobuf API
        } else {
            danmakuManager.isEnabled = false
        }
    }
    
    // 绑定 Player
    DisposableEffect(exoPlayer) {
        danmakuManager.attachPlayer(exoPlayer)
        onDispose { /* Player 在另一个 DisposableEffect 中释放 */ }
    }
    
    // 清理弹幕管理器（解绑视图但不释放数据，单例会保持状态）
    DisposableEffect(Unit) {
        onDispose {
            danmakuManager.clearViewReference()
        }
    }
    
    // 清理播放器 +  屏幕常亮管理
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        
        //  [修复] 进入番剧播放页时保持屏幕常亮，防止自动熄屏
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            exoPlayer.release()
            //  恢复默认方向，避免离开播放器后卡在横屏
            context.findActivity()?.requestedOrientation = 
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            //  [修复] 离开番剧播放页时取消屏幕常亮
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            //  [修复] 恢复系统亮度控制，解除亮度锁定
            window?.let { w ->
                val params = w.attributes
                params.screenBrightness = -1f  // -1f 表示跟随系统亮度
                w.attributes = params
            }
        }
    }
    
    // 辅助函数：切换屏幕方向
    fun toggleOrientation() {
        val activity = context.findActivity() ?: return
        if (isLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    
    //  自动检测设备方向变化并解锁旋转
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val orientationEventListener = object : android.view.OrientationEventListener(context) {
            private var lastOrientation = -1
            
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                val newOrientation = when (orientation) {
                    in 315..360, in 0..45 -> 0   // Portrait
                    in 46..134 -> 270           // Landscape (reverse)
                    in 135..224 -> 180          // Portrait (upside down)
                    in 225..314 -> 90           // Landscape
                    else -> lastOrientation
                }
                
                if (newOrientation != lastOrientation && lastOrientation != -1) {
                    val isDeviceLandscape = newOrientation == 90 || newOrientation == 270
                    val isDevicePortrait = newOrientation == 0 || newOrientation == 180
                    
                    activity?.let { act ->
                        if (isLandscape && isDevicePortrait) {
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else if (!isLandscape && isDeviceLandscape) {
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }
                lastOrientation = newOrientation
            }
        }
        
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        
        onDispose {
            orientationEventListener.disable()
        }
    }
    
    // 拦截系统返回键
    BackHandler(enabled = isLandscape) {
        toggleOrientation()
    }
    
    // 沉浸式状态栏控制
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity())?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            if (isLandscape) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = 
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.statusBarColor = Color.Black.toArgb()
                window.navigationBarColor = Color.Black.toArgb()
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLandscape) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        //  获取清晰度数据
        val successState = uiState as? BangumiPlayerState.Success
        
    //  [修复] 使用 movableContentOf 保持播放器实例跨布局移动时不被销毁
    val playerContent = remember(successState, danmakuEnabled, currentSpeed, danmakuOpacity, danmakuFontScale, danmakuSpeed, danmakuDisplayArea) {
        movableContentOf { isFullscreenMode: Boolean ->
            BangumiPlayerView(
                exoPlayer = exoPlayer,
                danmakuManager = danmakuManager,
                danmakuEnabled = danmakuEnabled,
                onDanmakuToggle = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuEnabled(context, !danmakuEnabled)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                isFullscreen = isFullscreenMode,
                currentQuality = successState?.quality ?: 0,
                acceptQuality = successState?.acceptQuality ?: emptyList(),
                acceptDescription = successState?.acceptDescription ?: emptyList(),
                onQualityChange = { viewModel.changeQuality(it) },
                onBack = if (isFullscreenMode) { { toggleOrientation() } } else onBack,
                onToggleFullscreen = { toggleOrientation() },
                sponsorSegment = sponsorSegment,
                showSponsorSkipButton = showSponsorSkipButton,
                onSponsorSkip = { viewModel.skipCurrentSponsorSegment() },
                onSponsorDismiss = { viewModel.dismissSponsorSkipButton() },
                //  倍速控制
                currentSpeed = currentSpeed,
                onSpeedChange = { currentSpeed = it },
                //  弹幕设置
                danmakuOpacity = danmakuOpacity,
                danmakuFontScale = danmakuFontScale,
                danmakuSpeed = danmakuSpeed,
                danmakuDisplayArea = danmakuDisplayArea,
                onDanmakuOpacityChange = { scope.launch { com.android.purebilibili.core.store.SettingsManager.setDanmakuOpacity(context, it) } },
                onDanmakuFontScaleChange = { scope.launch { com.android.purebilibili.core.store.SettingsManager.setDanmakuFontScale(context, it) } },
                onDanmakuSpeedChange = { scope.launch { com.android.purebilibili.core.store.SettingsManager.setDanmakuSpeed(context, it) } },
                onDanmakuDisplayAreaChange = { scope.launch { com.android.purebilibili.core.store.SettingsManager.setDanmakuArea(context, it) } }
            )
        }
    }


        
        if (isLandscape) {
            // 全屏播放
            playerContent(true)
        } else {
            // 竖屏：播放器 + 内容
            Column(modifier = Modifier.fillMaxSize()) {
                //  播放器区域 - 放大为 2:3 比例
                val screenWidthDp = configuration.screenWidthDp.dp
                val playerHeight = screenWidthDp * 2f / 3f
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(playerHeight)
                        .background(Color.Black)
                ) {
                    playerContent(false)
                }
                
                // 内容区域（进度条已集成到播放器控制层内）
                
                // 内容区域
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (val state = uiState) {
                        is BangumiPlayerState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CupertinoActivityIndicator()
                            }
                        }
                        
                        is BangumiPlayerState.Error -> {
                            BangumiErrorContent(
                                message = state.message,
                                isVipRequired = state.isVipRequired,
                                isLoginRequired = state.isLoginRequired,
                                canRetry = state.canRetry,
                                onRetry = { viewModel.retry() },
                                onLogin = onNavigateToLogin
                            )
                        }
                        
                        is BangumiPlayerState.Success -> {
                            BangumiPlayerContent(
                                detail = state.seasonDetail,
                                currentEpisode = state.currentEpisode,
                                onEpisodeClick = { viewModel.switchEpisode(it) },
                                onFollowClick = { viewModel.toggleFollow() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 辅助函数：从 Context 获取 Activity
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
