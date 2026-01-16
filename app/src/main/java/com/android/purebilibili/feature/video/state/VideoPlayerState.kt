// 文件路径: feature/video/VideoPlayerState.kt
package com.android.purebilibili.feature.video.state

import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.VideoActivity
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.android.purebilibili.R
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "media_playback_channel"
private const val THEME_COLOR = 0xFFFB7299.toInt()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoPlayerState(
    val context: Context,
    val player: ExoPlayer,
    val mediaSession: MediaSession,
    //  性能优化：传入受管理的 CoroutineScope，避免内存泄漏
    private val scope: CoroutineScope
) {
    // 📱 竖屏视频状态 - 双重验证机制
    // 来源1: API dimension 字段（预判断，快速可用）
    // 来源2: 播放器 onVideoSizeChanged（精确验证，需要等待加载）
    
    private val _isVerticalVideo = MutableStateFlow(false)
    val isVerticalVideo: StateFlow<Boolean> = _isVerticalVideo.asStateFlow()
    
    // 📐 视频尺寸（来自播放器回调，精确值）
    private val _videoSize = MutableStateFlow(Pair(0, 0))
    val videoSize: StateFlow<Pair<Int, Int>> = _videoSize.asStateFlow()
    
    // 🎯 API 预判断值（用于视频加载前的 UI 显示）
    private val _apiDimension = MutableStateFlow<Pair<Int, Int>?>(null)
    val apiDimension: StateFlow<Pair<Int, Int>?> = _apiDimension.asStateFlow()
    
    // 📱 竖屏全屏模式状态
    private val _isPortraitFullscreen = MutableStateFlow(false)
    val isPortraitFullscreen: StateFlow<Boolean> = _isPortraitFullscreen.asStateFlow()
    
    // 🔍 验证来源标记
    enum class VerticalVideoSource {
        UNKNOWN,  // 未知
        API,      // 来自 API dimension 字段
        PLAYER    // 来自播放器回调（精确）
    }
    private val _verticalVideoSource = MutableStateFlow(VerticalVideoSource.UNKNOWN)
    val verticalVideoSource: StateFlow<VerticalVideoSource> = _verticalVideoSource.asStateFlow()
    
    private val videoSizeListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoSize.value = Pair(videoSize.width, videoSize.height)
                val isVertical = videoSize.height > videoSize.width
                _isVerticalVideo.value = isVertical
                _verticalVideoSource.value = VerticalVideoSource.PLAYER
                
                // 🔍 双重验证：检查是否与 API 预判断一致
                val apiSize = _apiDimension.value
                if (apiSize != null) {
                    val apiVertical = apiSize.second > apiSize.first
                    if (apiVertical != isVertical) {
                        com.android.purebilibili.core.util.Logger.w(
                            "VideoPlayerState",
                            "⚠️ 竖屏判断不一致! API: ${apiSize.first}x${apiSize.second}=$apiVertical, 播放器: ${videoSize.width}x${videoSize.height}=$isVertical"
                        )
                    }
                }
                
                com.android.purebilibili.core.util.Logger.d(
                    "VideoPlayerState",
                    "📱 VideoSize(PLAYER): ${videoSize.width}x${videoSize.height}, isVertical=$isVertical"
                )
            }
        }
    }
    
    init {
        player.addListener(videoSizeListener)
        // 初始检查
        val size = player.videoSize
        if (size.width > 0 && size.height > 0) {
            _videoSize.value = Pair(size.width, size.height)
            _isVerticalVideo.value = size.height > size.width
            _verticalVideoSource.value = VerticalVideoSource.PLAYER
        }
    }
    
    /**
     * 📱 从 API dimension 字段设置预判断值
     * 在视频加载完成但播放器还未解析时调用
     */
    fun setApiDimension(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _apiDimension.value = Pair(width, height)
            // 只有在播放器还没提供精确值时才使用 API 值
            if (_verticalVideoSource.value != VerticalVideoSource.PLAYER) {
                _isVerticalVideo.value = height > width
                _verticalVideoSource.value = VerticalVideoSource.API
                com.android.purebilibili.core.util.Logger.d(
                    "VideoPlayerState",
                    "📱 VideoSize(API): ${width}x${height}, isVertical=${height > width}"
                )
            }
        }
    }
    
    /**
     * 📱 进入/退出竖屏全屏模式
     */
    fun setPortraitFullscreen(enabled: Boolean) {
        _isPortraitFullscreen.value = enabled
        com.android.purebilibili.core.util.Logger.d(
            "VideoPlayerState",
            "📱 PortraitFullscreen: $enabled"
        )
    }
    
    /**
     * 🔄 重置视频尺寸状态（切换视频时调用）
     */
    fun resetVideoSize() {
        _videoSize.value = Pair(0, 0)
        _apiDimension.value = null
        _isVerticalVideo.value = false
        _verticalVideoSource.value = VerticalVideoSource.UNKNOWN
        _isPortraitFullscreen.value = false
    }
    
    fun release() {
        player.removeListener(videoSizeListener)
    }
    fun updateMediaMetadata(title: String, artist: String, coverUrl: String) {
        val currentItem = player.currentMediaItem ?: return

        // 1. 更新 Player 内部元数据
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(FormatUtils.fixImageUrl(coverUrl)))
            .setDisplayTitle(title)
            .setIsPlayable(true)
            .build()

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        player.replaceMediaItem(player.currentMediaItemIndex, newItem)

        // 2.  性能优化：使用传入的 scope 而非裸创建的 CoroutineScope
        scope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(context, coverUrl)

            // 切回主线程操作 Player 和发送通知
            launch(Dispatchers.Main) {
                pushMediaNotification(title, artist, bitmap)
            }
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        return try {
            //  性能优化：使用 Coil 单例，避免重复创建 ImageLoader
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(FormatUtils.fixImageUrl(url))
                .allowHardware(false)
                .scale(Scale.FILL)
                .transformations(RoundedCornersTransformation(16f))
                .size(512, 512)
                .build()
            val result = loader.execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as BitmapDrawable).bitmap }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun pushMediaNotification(title: String, artist: String, bitmap: Bitmap?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保渠道存在
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "媒体播放", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示播放控制"
                    setShowBadge(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionCompatToken)
            .setShowActionsInCompactView(0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setStyle(style)
            .setColor(THEME_COLOR)
            .setColorized(true)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            //  修复点：直接使用 sessionActivity
            .setContentIntent(mediaSession.sessionActivity)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    context: Context,
    viewModel: PlayerViewModel,
    bvid: String
): VideoPlayerState {

    //  尝试复用 MiniPlayerManager 中已加载的 player
    val miniPlayerManager = MiniPlayerManager.getInstance(context)
    val reuseFromMiniPlayer = miniPlayerManager.isActive && miniPlayerManager.currentBvid == bvid
    
    //  [修复] 添加唯一 key 强制在每次进入时重新创建 player
    // 解决重复打开同一视频时 player 已被释放导致无声音的问题
    val playerCreationKey = remember { System.currentTimeMillis() }
    
    val player = remember(context, bvid, reuseFromMiniPlayer, playerCreationKey) {
        // 如果小窗有这个视频的 player，直接复用
        if (reuseFromMiniPlayer) {
            miniPlayerManager.player?.also {
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 复用小窗 player: bvid=$bvid")
            }
        } else {
            null
        } ?: run {
            // 创建新的 player
            com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 创建新 player: bvid=$bvid")
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            )
            val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
                .setDefaultRequestProperties(headers)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            //  [性能优化] 同步读取硬件解码设置，避免 runBlocking 阻塞主线程
            // DataStore 会将数据存储在 datastore/settings 文件中，使用 preferences key
            // 为了同步读取，我们使用 SharedPreferences 作为快速缓存，默认开启硬件解码
            val hwDecodePrefs = context.getSharedPreferences("hw_decode_cache", Context.MODE_PRIVATE)
            val hwDecodeEnabled = hwDecodePrefs.getBoolean("hw_decode_enabled", true)

            //  根据设置选择 RenderersFactory
            val renderersFactory = if (hwDecodeEnabled) {
                // 默认 Factory，优先使用硬件解码
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            } else {
                // 强制使用软件解码
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    .setEnableDecoderFallback(true)
            }

            ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                //  性能优化：自定义缓冲策略，改善播放流畅度
                .setLoadControl(
                    androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            15000,  // 最小缓冲 15s
                            50000,  // 最大缓冲 50s
                            2500,   // 播放开始前缓冲 2.5s
                            5000    // 重新缓冲后缓冲 5s
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)  // 优先保证播放时长
                        .build()
                )
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    //  [修复] 确保音量正常，解决第二次播放静音问题
                    volume = 1.0f
                    //  [重构] 不在此处调用 prepare()，因为还没有媒体源
                    // prepare() 和 playWhenReady 将在 attachPlayer/loadVideo 设置媒体源后调用
                    playWhenReady = true
                }
        }
    }

    val sessionActivityPendingIntent = remember(context, bvid) {
        val intent = Intent(context, VideoActivity::class.java).apply {
            putExtra("bvid", bvid)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // 🚀 [修复] MediaSession 与 player 生命周期同步
    val mediaSession = remember(player) {
        val sessionId = "bilipai_${java.util.UUID.randomUUID()}"
        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "📺 Creating MediaSession with ID: $sessionId")
        MediaSession.Builder(context, player)
            .setId(sessionId)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    //  性能优化：使用 rememberCoroutineScope 创建受管理的协程作用域
    val scope = rememberCoroutineScope()

    val holder = remember(player, mediaSession, scope) {
        VideoPlayerState(context, player, mediaSession, scope)
    }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Success) {
            val info = (uiState as PlayerUiState.Success).info
            holder.updateMediaMetadata(info.title, info.owner.name, info.pic)
        }
    }

    DisposableEffect(player, mediaSession) {
        onDispose {
            //  [新增] 保存播放进度到 ViewModel 缓存
            viewModel.saveCurrentPosition()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)

            //  检查是否有小窗在使用这个 player
            val miniPlayerManager = MiniPlayerManager.getInstance(context)
            //  [修复] 使用 isActive 和 hasExternalPlayer 来判断是否保留 player
            // isMiniMode 可能还没有被设置（AppNavigation.onDispose 可能在之后执行）
            // 但如果 isActive 为 true 且当前 player 是被引用的外部 player，则不释放
            val shouldKeepPlayer = miniPlayerManager.isActive && miniPlayerManager.hasExternalPlayer
            if (shouldKeepPlayer) {
                // 小窗模式下不释放 player，只释放其他资源
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 小窗正在使用此 player，不释放")
            } else {
                // 正常释放所有资源
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 释放所有资源")
                //  [修复2] 清除外部播放器引用，防止状态混乱
                miniPlayerManager.resetExternalPlayer()
                holder.release()  // 📱 释放视频尺寸监听器
                mediaSession.release()
                player.release()
            }
            
            (context as? ComponentActivity)?.window?.attributes?.screenBrightness =
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    //  [后台恢复优化] 监听生命周期，保存/恢复播放状态
    var savedPosition by remember { mutableStateOf(-1L) }
    var wasPlaying by remember { mutableStateOf(false) }
    //  [修复] 记录是否从后台音频模式恢复（后台音频时不应 seek 回旧位置）
    var wasBackgroundAudio by remember { mutableStateOf(false) }
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val miniPlayerManager = MiniPlayerManager.getInstance(context)
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    //  [修复] 保存进度到 ViewModel 缓存（用于跨导航恢复）
                    viewModel.saveCurrentPosition()
                    
                    //  保存播放状态（用于本地恢复）
                    savedPosition = player.currentPosition
                    wasPlaying = player.isPlaying
                    
                    //  [新增] 判断是否应该继续播放
                    // 1. 应用内小窗模式 - 继续播放
                    // 2. 系统 PiP 模式 - 用户按 Home 键返回桌面时继续播放
                    // 3. 后台音频模式 - 继续播放音频
                    val isMiniMode = miniPlayerManager.isMiniMode
                    val isPip = miniPlayerManager.shouldEnterPip()
                    val isBackgroundAudio = miniPlayerManager.shouldContinueBackgroundAudio()
                    val shouldContinuePlayback = isMiniMode || isPip || isBackgroundAudio
                    
                    //  [修复] 记录后台音频状态，恢复时不要 seek 回旧位置
                    wasBackgroundAudio = isBackgroundAudio
                    
                    if (!shouldContinuePlayback) {
                        // 非小窗/PiP/后台模式下暂停
                        player.pause()
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_PAUSE: 暂停播放")
                    } else {
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "🎵 ON_PAUSE: 保持播放 (miniMode=$isMiniMode, pip=$isPip, bg=$isBackgroundAudio)")
                    }
                    com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_PAUSE: pos=$savedPosition, wasPlaying=$wasPlaying, bgAudio=$wasBackgroundAudio")
                }
                // 🔋 注意: ON_STOP/ON_START 的视频轨道禁用/恢复由 MiniPlayerManager 通过 BackgroundManager 统一处理
                // 避免重复处理导致 savedTrackParams 被覆盖
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    //  [修复] 后台音频模式恢复时不 seek，因为播放器一直在播放
                    // 只有在非后台音频模式（即播放暂停了）才恢复位置
                    val shouldRestorePlayback = savedPosition >= 0 
                        && !miniPlayerManager.isMiniMode 
                        && !miniPlayerManager.shouldEnterPip()
                        && !wasBackgroundAudio  //  [关键修复] 后台音频模式不恢复旧位置
                    
                    if (shouldRestorePlayback) {
                        player.seekTo(savedPosition)
                        if (wasPlaying) {
                            player.play()
                        }
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_RESUME: restored pos=$savedPosition, playing=$wasPlaying")
                    } else if (wasBackgroundAudio) {
                        //  [修复] 后台音频模式恢复：不 seek，播放器继续当前位置
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "🎵 ON_RESUME: 后台音频恢复，当前位置=${player.currentPosition}，不 seek 回 $savedPosition")
                        wasBackgroundAudio = false  // 重置标志
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    //  [修复3] 监听播放器错误，智能重试（网络错误最多重试 3 次）
    val retryCountRef = remember { object { var count = 0 } }
    val maxRetries = 3
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayerState", " Player error: ${error.message}, code=${error.errorCode}")
                
                //  判断是否为网络相关错误
                val isNetworkError = error.errorCode in listOf(
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )
                
                if (isNetworkError && retryCountRef.count < maxRetries) {
                    retryCountRef.count++
                    val delayMs = retryCountRef.count * 2000L  // 递增延迟：2s, 4s, 6s
                    com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "🔄 Network error, retry ${retryCountRef.count}/$maxRetries in ${delayMs}ms")
                    
                    // 🚀 [修复] 使用受管理的 scope 避免内存泄漏
                    scope.launch {
                        kotlinx.coroutines.delay(delayMs)
                        viewModel.retry()
                    }
                } else if (retryCountRef.count < 1) {
                    // 非网络错误，只重试一次
                    retryCountRef.count++
                    com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " Auto-retrying video load (non-network error)...")
                    viewModel.retry()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // 播放成功，重置重试计数
                    retryCountRef.count = 0
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    //  [重构] 合并为单个 LaunchedEffect 确保执行顺序
    // 必须先 attachPlayer，再 loadVideo，否则 ViewModel 中的 exoPlayer 引用无效
    LaunchedEffect(player, bvid, reuseFromMiniPlayer) {
        // 1️⃣ 首先绑定 player
        viewModel.attachPlayer(player)
        
        // 2️⃣ 总是调用 loadVideo（loadVideo 内部会处理进度恢复）
        // 不再使用 restoreFromCache，因为它不设置媒体源
        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " Calling loadVideo: $bvid")
        viewModel.loadVideo(bvid)
    }

    return holder
}