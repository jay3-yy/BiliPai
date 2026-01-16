// 文件路径: feature/video/player/MiniPlayerManager.kt
package com.android.purebilibili.feature.video.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import com.android.purebilibili.core.util.Logger
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.VideoActivity

private const val TAG = "MiniPlayerManager"
private const val NOTIFICATION_ID = 1002
private const val CHANNEL_ID = "mini_player_channel"
private const val THEME_COLOR = 0xFFFB7299.toInt()

/**
 *  全局小窗管理器
 * 
 * 负责管理跨导航的视频播放状态，支持：
 * 1. 在视频详情页和首页之间保持播放连续性
 * 2. 小窗模式下的播放控制
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MiniPlayerManager private constructor(private val context: Context) : 
    com.android.purebilibili.core.lifecycle.BackgroundManager.BackgroundStateListener {

    companion object {
        @Volatile
        private var INSTANCE: MiniPlayerManager? = null

        fun getInstance(context: Context): MiniPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MiniPlayerManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
        
        //  [新增] 媒体控制常量
        const val ACTION_MEDIA_CONTROL = "com.android.purebilibili.MEDIA_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val ACTION_PREVIOUS = 1
        const val ACTION_PLAY_PAUSE = 2
        const val ACTION_NEXT = 3
    }

    // --- 协程作用域 ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 🔋 [后台优化] 低内存模式状态
    private var isLowMemoryMode = false
    private var savedTrackParams: androidx.media3.common.TrackSelectionParameters? = null
    
    //  [新增] 媒体控制广播接收器
    private val mediaControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_CONTROL) {
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    ACTION_PREVIOUS -> {
                        Logger.d(TAG, "🔔 通知栏: 上一曲")
                        playPrevious()
                    }
                    ACTION_PLAY_PAUSE -> {
                        Logger.d(TAG, "🔔 通知栏: 播放/暂停")
                        togglePlayPause()
                    }
                    ACTION_NEXT -> {
                        Logger.d(TAG, "🔔 通知栏: 下一曲")
                        playNext()
                    }
                }
            }
        }
    }
    
    init {
        //  注册媒体控制广播接收器
        val filter = android.content.IntentFilter(ACTION_MEDIA_CONTROL)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            mediaControlReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Logger.d(TAG, " 媒体控制广播接收器已注册")
        
        // 🔋 注册后台状态监听
        com.android.purebilibili.core.lifecycle.BackgroundManager.addListener(this)
        Logger.d(TAG, "🔋 后台状态监听器已注册")
    }
    
    // ========== 🔋 后台状态回调 ==========
    
    override fun onEnterBackground() {
        if (!isActive) return
        
        isLowMemoryMode = true
        val currentPlayer = player ?: return
        
        // 判断是否需要后台音频
        if (shouldContinueBackgroundAudio()) {
            // 保存原始轨道参数
            savedTrackParams = currentPlayer.trackSelectionParameters
            
            // 禁用视频轨道，只播放音频
            currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(0, 0)
                .build()
            
            Logger.d(TAG, "🔋 后台模式：禁用视频轨道，仅保留音频")
        }
    }
    
    override fun onEnterForeground() {
        if (!isLowMemoryMode) return
        
        isLowMemoryMode = false
        val currentPlayer = player ?: return
        
        // 恢复视频轨道
        savedTrackParams?.let { originalParams ->
            currentPlayer.trackSelectionParameters = originalParams
            savedTrackParams = null
            Logger.d(TAG, "🌅 前台模式：恢复视频轨道")
        }
    }


    // --- 播放器状态 (可观察) ---
    var isActive by mutableStateOf(false)
        private set
    
    var isMiniMode by mutableStateOf(false)
        private set
    
    // 🚀 [新增] 导航抑制标志：在导航到视频页面期间不显示小窗
    var isNavigatingToVideo by mutableStateOf(false)
    
    // 🎯 [新增] 导航离开标志：区分"应用导航离开"和"应用进入后台"
    // true = 用户通过返回按钮离开视频页面，应该停止播放
    // false = 用户按 Home 键离开应用，应该继续后台播放
    var isLeavingByNavigation by mutableStateOf(false)

    var isPlaying by mutableStateOf(false)
        private set

    var currentPosition by mutableLongStateOf(0L)
        private set

    var duration by mutableLongStateOf(0L)
        private set
    
    var progress by mutableFloatStateOf(0f)
        private set

    // --- 当前视频信息 ---
    var currentBvid by mutableStateOf<String?>(null)
        private set

    var currentTitle by mutableStateOf("")
        private set

    var currentCover by mutableStateOf("")
        private set

    var currentOwner by mutableStateOf("")
        private set
    
    //  [新增] 当前视频的 cid，用于弹幕加载
    var currentCid by mutableLongStateOf(0L)
        private set
    
    //  [新增] 缓存的视频详情页 UI 状态，用于从小窗返回时恢复
    var cachedUiState: PlayerUiState.Success? = null
        private set
    
    //  [新增] 小窗入场方向：true=从左边进入，false=从右边进入
    var entryFromLeft by mutableStateOf(false)
        private set
    
    //  [新增] 缓存 UI 状态
    fun cacheUiState(state: PlayerUiState.Success) {
        cachedUiState = state
        com.android.purebilibili.core.util.Logger.d(TAG, " 缓存 UI 状态: ${state.info.title}")
    }
    
    //  [新增] 获取并清除缓存的 UI 状态
    fun consumeCachedUiState(): PlayerUiState.Success? {
        val state = cachedUiState
        // 不清除缓存，允许多次复用
        return state
    }

    // --- ExoPlayer 实例 ---
    private var _player: ExoPlayer? = null
    //  外部播放器引用（来自 VideoDetailScreen 的 VideoPlayerState）
    private var _externalPlayer: ExoPlayer? = null
    //  优先使用外部播放器（如果存在）
    val player: ExoPlayer?
        get() = _externalPlayer ?: _player
    
    //  [修复2] 检查是否有外部播放器
    val hasExternalPlayer: Boolean
        get() = _externalPlayer != null
    
    //  [修复2] 清除外部播放器引用（从小窗返回全屏时调用）
    fun resetExternalPlayer() {
        Logger.d(TAG, " resetExternalPlayer: clearing external player reference")
        _externalPlayer = null
    }

    // --- MediaSession ---
    private var mediaSession: MediaSession? = null
    
    //  [新增] MediaSession 回调处理器，支持系统媒体控件
    private val mediaSessionCallback = object : MediaSession.Callback {
        //  处理系统媒体按钮事件
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            Logger.d(TAG, " onMediaButtonEvent: action=${intent.action}")
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
        
        //  处理自定义命令（上一首/下一首）
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            Logger.d(TAG, " onCustomCommand: ${customCommand.customAction}")
            when (customCommand.customAction) {
                "SKIP_TO_PREVIOUS" -> playPrevious()
                "SKIP_TO_NEXT" -> playNext()
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
        
        //  设置可用操作
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Logger.d(TAG, " onConnect: ${controller.packageName}")
            // 允许所有连接并启用所有播放命令
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(androidx.media3.session.SessionCommand("SKIP_TO_PREVIOUS", android.os.Bundle.EMPTY))
                        .add(androidx.media3.session.SessionCommand("SKIP_TO_NEXT", android.os.Bundle.EMPTY))
                        .build()
                )
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS  // 默认包含 seek/play/pause 等
                )
                .build()
        }
    }
    
    // ==========  小窗模式判断方法 ==========
    
    /**
     * 获取当前小窗模式设置
     */
    fun getCurrentMode(): com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode {
        return com.android.purebilibili.core.store.SettingsManager.getMiniPlayerModeSync(context)
    }
    
    /**
     * 判断是否应该显示应用内小窗（返回首页时）
     *  只有 IN_APP_ONLY 模式才显示应用内悬浮小窗
     */
    fun shouldShowInAppMiniPlayer(): Boolean {
        val mode = getCurrentMode()
        val result = mode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY 
            && isActive && !isNavigatingToVideo
        Logger.d(TAG, "📲 shouldShowInAppMiniPlayer: mode=$mode, isActive=$isActive, navigating=$isNavigatingToVideo, result=$result")
        return result
    }
    
    /**
     * 判断是否应该进入系统画中画模式（按 Home 键时）
     */
    fun shouldEnterPip(): Boolean {
        val mode = getCurrentMode()
        val result = mode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP && isActive
        Logger.d(TAG, " shouldEnterPip: mode=$mode, isActive=$isActive, result=$result")
        return result
    }
    
    /**
     * 🎯 判断是否应该继续后台音频播放
     * 
     * OFF模式（官方B站行为）：
     * - 切到桌面 → 继续后台播放
     * - 通过返回按钮离开视频页 → 停止播放
     */
    fun shouldContinueBackgroundAudio(): Boolean {
        val mode = getCurrentMode()
        return when (mode) {
            com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF -> {
                // 默认模式：只有切到桌面（非导航离开）才继续后台播放
                isActive && !isLeavingByNavigation
            }
            com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY -> {
                // 应用内小窗模式：由小窗接管，不需要后台音频
                false
            }
            com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP -> {
                // 画中画模式：由 PiP 接管
                false
            }
        }
    }
    
    /**
     * 🔄 重置导航离开标志（在视频页计入时调用）
     */
    fun resetNavigationFlag() {
        isLeavingByNavigation = false
        Logger.d(TAG, "🔄 resetNavigationFlag: isLeavingByNavigation=false")
    }
    
    /**
     * 🎯 标记通过导航离开（在返回按钮点击时调用）
     */
    fun markLeavingByNavigation() {
        isLeavingByNavigation = true
        Logger.d(TAG, "🎯 markLeavingByNavigation: isLeavingByNavigation=true")
    }
    
    /**
     * 判断小窗功能是否完全关闭
     * 🔄 [简化] 现在只有 OFF 和 SYSTEM_PIP，OFF 模式下返回 false（因为支持后台播放）
     */
    fun isMiniPlayerDisabled(): Boolean {
        // 两种模式都支持某种形式的后台播放，所以不再"完全关闭"
        return false
    }


    /**
     * 初始化播放器（如果尚未初始化）
     */
    fun ensurePlayer(): ExoPlayer {
        if (_player == null) {
            Logger.d(TAG, "Creating new ExoPlayer instance")
            
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
                .setDefaultRequestProperties(headers)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            _player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    addListener(playerListener)
                    //  [修复] 确保音量正常
                    volume = 1.0f
                    prepare()
                }
            
            // 创建 MediaSession
            val sessionIntent = Intent(context, VideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(context, _player!!)
                .setSessionActivity(pendingIntent)
                .setCallback(mediaSessionCallback)  //  支持系统媒体控件
                .build()
        }
        return _player!!
    }


    /**
     * 开始播放新视频
     */
    fun startVideo(
        bvid: String,
        title: String,
        cover: String,
        owner: String,
        videoUrl: String,
        audioUrl: String?
    ) {
        Logger.d(TAG, "startVideo: bvid=$bvid, title=$title")
        
        ensurePlayer()
        
        // 如果是同一个视频，不重新加载
        if (currentBvid == bvid && _player?.isPlaying == true) {
            Logger.d(TAG, "Same video already playing, skip reload")
            return
        }

        currentBvid = bvid
        currentTitle = title
        currentCover = cover
        currentOwner = owner
        isActive = true
        isMiniMode = false

        // 构建媒体源
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
            .setDefaultRequestProperties(headers)

        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        if (audioUrl != null) {
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            _player?.setMediaSource(mergedSource)
        } else {
            _player?.setMediaSource(videoSource)
        }

        //  [修复] 确保音量正常
        _player?.volume = 1.0f
        _player?.prepare()
        _player?.playWhenReady = true

        // 更新媒体元数据
        updateMediaMetadata(title, owner, cover)
    }

    /**
     * 进入小窗模式
     * @param forced 强制进入（点击小窗按钮时使用），忽略模式检查
     */
    fun enterMiniMode(forced: Boolean = false) {
        val mode = getCurrentMode()
        Logger.d(TAG, "📲 enterMiniMode called: isActive=$isActive, forced=$forced, mode=$mode")
        
        // 非强制模式下，只有 IN_APP_ONLY 才自动进入小窗
        if (!forced && mode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY) {
            Logger.d(TAG, "⚠️ Auto mini player only works in IN_APP_ONLY mode, current mode=$mode")
            return
        }
        
        if (!isActive) {
            Logger.w(TAG, "⚠️ Cannot enter mini mode: isActive is false!")
            return
        }
        Logger.d(TAG, "📲 Entering mini mode for video: $currentTitle (forced=$forced)")
        isMiniMode = true
        
        // 🔔 [修复] 进入小窗时更新媒体通知（系统控制中心显示）
        if (currentTitle.isNotEmpty()) {
            updateMediaMetadata(currentTitle, currentOwner, currentCover)
        }
    }

    //  [新增] 是否执行退出动画 (用于在点击新视频时瞬间消失，避免闪烁)
    var shouldAnimateExit by mutableStateOf(true)
        private set

    /**
     * 退出小窗模式（返回全屏详情页）
     * @param animate 是否执行退出动画
     */
    fun exitMiniMode(animate: Boolean = true) {
        Logger.d(TAG, "Exiting mini mode, animate=$animate")
        shouldAnimateExit = animate
        isMiniMode = false
    }

    /**
     * 停止播放并关闭小窗
     */
    fun dismiss() {
        Logger.d(TAG, "Dismissing mini player")
        
        //  [修复] 先停止所有播放器的声音
        _externalPlayer?.let { 
            it.pause()
            it.stop()
            Logger.d(TAG, "🔇 Stopped external player")
        }
        _player?.let {
            it.pause()
            it.stop()
            Logger.d(TAG, "🔇 Stopped internal player")
        }
        
        isMiniMode = false
        isActive = false
        isPlaying = false  //  [修复] 同步播放状态
        _externalPlayer = null
        currentBvid = null
        cachedUiState = null  //  [修复] 清除缓存的 UI 状态
        
        // 清除通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     *  设置视频信息并关联外部播放器（用于小窗模式）
     * 这个方法不创建新播放器，而是使用 VideoDetailScreen 的播放器
     * @param fromLeft  是否从左边进入（用于小窗动画方向）
     */
    fun setVideoInfo(
        bvid: String,
        title: String,
        cover: String,
        owner: String,
        cid: Long,  //  [新增] cid 用于弹幕加载
        externalPlayer: ExoPlayer,
        fromLeft: Boolean = false  //  [新增] 入场方向
    ) {
        Logger.d(TAG, "setVideoInfo: bvid=$bvid, title=$title, cid=$cid, fromLeft=$fromLeft")
        currentBvid = bvid
        currentTitle = title
        currentCover = cover
        currentOwner = owner
        currentCid = cid  //  保存 cid
        entryFromLeft = fromLeft  //  保存入场方向
        _externalPlayer = externalPlayer
        isActive = true
        isMiniMode = false
        
        // 同步播放状态
        isPlaying = externalPlayer.isPlaying
        duration = externalPlayer.duration.coerceAtLeast(0L)
    }
    
    /**
     *  设置小窗入场方向
     */
    fun setEntryDirection(fromLeft: Boolean) {
        entryFromLeft = fromLeft
        Logger.d(TAG, "setEntryDirection: fromLeft=$fromLeft")
    }

    /**
     * 暂停/播放切换
     */
    fun togglePlayPause() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
        } else {
            currentPlayer.play()
        }
    }

    /**
     * Seek 到指定位置
     */
    fun seekTo(position: Long) {
        player?.seekTo(position)
    }
    
    // ==========  [新增] 播放列表控制 ==========
    
    /**
     *  播放下一曲
     */
    fun playNext(): Boolean {
        val nextItem = PlaylistManager.playNext()
        if (nextItem != null) {
            if (nextItem.isBangumi) {
                // 番剧需要特殊处理，通过事件通知
                Logger.d(TAG, " 下一集是番剧，需要特殊处理")
                return false  // TODO: 实现番剧切换
            } else {
                // 普通视频：通过回调通知 ViewModel 加载
                Logger.d(TAG, " 播放下一曲: ${nextItem.title}")
                onPlayNextCallback?.invoke(nextItem)
                return true
            }
        }
        return false
    }
    
    /**
     *  播放上一曲
     */
    fun playPrevious(): Boolean {
        val prevItem = PlaylistManager.playPrevious()
        if (prevItem != null) {
            if (prevItem.isBangumi) {
                Logger.d(TAG, "⏮️ 上一集是番剧，需要特殊处理")
                return false  // TODO: 实现番剧切换
            } else {
                Logger.d(TAG, "⏮️ 播放上一曲: ${prevItem.title}")
                onPlayPreviousCallback?.invoke(prevItem)
                return true
            }
        }
        return false
    }
    
    /**
     *  切换播放模式
     */
    fun togglePlayMode(): PlayMode {
        return PlaylistManager.togglePlayMode()
    }
    
    /**
     *  获取当前播放模式
     */
    fun getPlayMode(): PlayMode = PlaylistManager.playMode.value
    
    // 回调函数（由 PlayerViewModel 设置）
    var onPlayNextCallback: ((PlaylistItem) -> Unit)? = null
    var onPlayPreviousCallback: ((PlaylistItem) -> Unit)? = null


    /**
     * 释放所有资源
     */
    fun release() {
        Logger.d(TAG, "Releasing all resources")
        dismiss()
        mediaSession?.release()
        mediaSession = null
        _player?.removeListener(playerListener)
        _player?.release()
        _player = null
        INSTANCE = null
    }

    // --- 播放器监听器 ---
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            Logger.d(TAG, "isPlaying changed: $playing")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    duration = _player?.duration ?: 0L
                    Logger.d(TAG, "Player ready, duration=$duration")
                }
                Player.STATE_ENDED -> {
                    Logger.d(TAG, "Playback ended")
                }
            }
        }
    }

    /**
     * 更新媒体元数据和通知
     */
    private fun updateMediaMetadata(title: String, artist: String, coverUrl: String) {
        val currentItem = _player?.currentMediaItem ?: return

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

        _player?.replaceMediaItem(_player?.currentMediaItemIndex ?: 0, newItem)

        // 异步加载封面并推送通知
        scope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(coverUrl)
            launch(Dispatchers.Main) {
                pushNotification(title, artist, bitmap)
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
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
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun pushNotification(title: String, artist: String, bitmap: Bitmap?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "小窗播放", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "小窗播放控制"
                    setShowBadge(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionCompatToken)
            .setShowActionsInCompactView(0, 1, 2)  //  显示前三个按钮

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setStyle(style)
            .setColor(THEME_COLOR)
            .setColorized(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(mediaSession?.sessionActivity)
        
        //  [新增] 添加控制按钮
        // 上一曲按钮
        val prevIntent = android.app.PendingIntent.getBroadcast(
            context, ACTION_PREVIOUS,
            android.content.Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, ACTION_PREVIOUS),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_previous,
                "上一曲",
                prevIntent
            ).build()
        )
        
        // 播放/暂停按钮
        val playPauseIntent = android.app.PendingIntent.getBroadcast(
            context, ACTION_PLAY_PAUSE,
            android.content.Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, ACTION_PLAY_PAUSE),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "暂停" else "播放"
        builder.addAction(
            NotificationCompat.Action.Builder(
                playPauseIcon,
                playPauseText,
                playPauseIntent
            ).build()
        )
        
        // 下一曲按钮
        val nextIntent = android.app.PendingIntent.getBroadcast(
            context, ACTION_NEXT,
            android.content.Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, ACTION_NEXT),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_next,
                "下一曲",
                nextIntent
            ).build()
        )

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to show notification", e)
        }
    }
}
