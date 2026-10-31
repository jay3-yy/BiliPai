// 文件路径: feature/video/player/MiniPlayerManager.kt
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.player

import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import coil3.request.allowHardware
import coil3.request.transformations

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.android.purebilibili.core.util.Logger
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.VideoSize
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import com.android.purebilibili.R
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.HiResCompatibleRenderersFactory
import com.android.purebilibili.core.player.PlaybackMediaCache
import com.android.purebilibili.core.player.PlayerVolumeController
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.store.normalizeAppIconKey
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.MediaUtils
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.resolveVideoPlaybackAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.VideoActivity
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackWakeMode
import com.android.purebilibili.feature.video.playback.session.resolveShouldContinuePlaybackDuringPause
import com.android.purebilibili.feature.video.state.isPlaybackActiveForLifecycle
import com.android.purebilibili.feature.video.usecase.VideoLoadResult
import com.android.purebilibili.feature.video.usecase.VideoPlaybackUseCase
import com.android.purebilibili.feature.video.usecase.pausePlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction

private const val TAG = "MiniPlayerManager"
private const val NOTIFICATION_ID = 1002
private const val FOREGROUND_START_DEBOUNCE_MS = 1500L
private const val USER_LEAVE_HINT_WINDOW_MS = 1500L
private const val SESSION_COMMAND_SKIP_TO_PREVIOUS = "SKIP_TO_PREVIOUS"
private const val SESSION_COMMAND_SKIP_TO_NEXT = "SKIP_TO_NEXT"

internal fun shouldShowInAppMiniPlayerByPolicy(
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    isNavigatingToVideo: Boolean,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (stopPlaybackOnExit) return false
    return mode.supportsInAppMiniPlayer && isActive && !isNavigatingToVideo
}

internal fun shouldEnterPipByPolicy(
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (stopPlaybackOnExit) return false
    return mode.supportsSystemPip && isActive
}

internal fun shouldContinueBackgroundAudioByPolicy(
    backgroundPlaybackEnabled: Boolean,
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    isLeavingByNavigation: Boolean,
    stopPlaybackOnExit: Boolean,
    shouldKeepPlaybackForPipTransition: Boolean = false,
    keepForAudioNowPlaying: Boolean = false,
): Boolean {
    if (stopPlaybackOnExit) return false
    if (!isActive) return false
    if (keepForAudioNowPlaying) return true
    if (!backgroundPlaybackEnabled) return false
    if (isLeavingByNavigation) return false
    return when (mode) {
        SettingsManager.MiniPlayerMode.OFF -> true
        SettingsManager.MiniPlayerMode.IN_APP_ONLY -> true
        SettingsManager.MiniPlayerMode.SYSTEM_PIP,
        SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP -> !shouldKeepPlaybackForPipTransition
    }
}

internal fun shouldKeepPlaybackForPipTransition(
    isSystemPipActive: Boolean,
    hasPendingPlaybackRoutePip: Boolean
): Boolean {
    return isSystemPipActive || hasPendingPlaybackRoutePip
}

internal fun resolveHandleAudioFocusByPolicy(audioFocusEnabled: Boolean): Boolean {
    return audioFocusEnabled
}

internal fun shouldKeepPlaybackForAudioNowPlayingBar(
    sessionActive: Boolean,
    barEnabled: Boolean = true,
): Boolean = sessionActive && barEnabled

internal fun shouldClearPlaybackNotificationOnNavigationExit(
    mode: SettingsManager.MiniPlayerMode,
    stopPlaybackOnExit: Boolean,
    keepForAudioNowPlaying: Boolean = false,
): Boolean {
    if (stopPlaybackOnExit) return true
    if (keepForAudioNowPlaying) return false
    return mode == SettingsManager.MiniPlayerMode.OFF ||
        mode == SettingsManager.MiniPlayerMode.SYSTEM_PIP
}

internal fun shouldHandleNavigationLeaveForBvid(
    expectedBvid: String?,
    currentBvid: String?
): Boolean {
    val expected = expectedBvid?.trim().orEmpty()
    val current = currentBvid?.trim().orEmpty()
    if (expected.isBlank() || current.isBlank()) return true
    return expected == current
}

internal fun shouldResumePlaybackOnMiniPlayerEntry(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return isPlaybackActiveForLifecycle(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState
    )
}

internal fun shouldContinuePlaybackDuringPause(
    isMiniMode: Boolean,
    isPip: Boolean,
    isBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean
): Boolean {
    return resolveShouldContinuePlaybackDuringPause(
        isMiniMode = isMiniMode,
        isPip = isPip,
        isBackgroundAudio = isBackgroundAudio,
        wasPlaybackActive = wasPlaybackActive
    )
}

internal const val SHORT_BACKGROUND_LIGHT_MODE_MS = 15_000L
internal const val BACKGROUND_PAUSED_RELEASE_DELAY_MS = 30_000L

internal fun shouldReleasePausedBackgroundPlayback(
    isInBackground: Boolean,
    ownsBackgroundPlayer: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    hasMediaItems: Boolean,
    isPipActiveOrPending: Boolean,
    alreadyReleased: Boolean,
): Boolean = isInBackground && ownsBackgroundPlayer &&
    !playWhenReady && !isPlaying && hasMediaItems &&
    !isPipActiveOrPending && !alreadyReleased

internal fun shouldApplyHeavyBackgroundVideoOptimization(
    backgroundElapsedMs: Long,
    shortBackgroundLightModeMs: Long = SHORT_BACKGROUND_LIGHT_MODE_MS
): Boolean {
    return backgroundElapsedMs >= shortBackgroundLightModeMs
}

internal fun shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(level: Int): Boolean {
    return level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
}

internal fun shouldRunHeavyBackgroundVideoOptimization(
    shouldDisableVideoTrack: Boolean,
    stillInBackground: Boolean,
    backgroundElapsedMs: Long,
    shortBackgroundLightModeMs: Long = SHORT_BACKGROUND_LIGHT_MODE_MS,
    forceDueToMemoryPressure: Boolean = false
): Boolean {
    if (!shouldDisableVideoTrack || !stillInBackground) return false
    if (forceDueToMemoryPressure) return true
    return shouldApplyHeavyBackgroundVideoOptimization(
        backgroundElapsedMs = backgroundElapsedMs,
        shortBackgroundLightModeMs = shortBackgroundLightModeMs
    )
}

internal fun shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
    isLowMemoryMode: Boolean,
    stillInBackground: Boolean,
    alreadyAppliedHeavyOptimization: Boolean,
    shouldDisableVideoTrack: Boolean,
    forceDueToMemoryPressure: Boolean
): Boolean {
    return isLowMemoryMode &&
        stillInBackground &&
        !alreadyAppliedHeavyOptimization &&
        shouldDisableVideoTrack &&
        forceDueToMemoryPressure
}

internal fun shouldDisableVideoTrackOnEnterBackground(
    shouldPauseBuffering: Boolean,
    shouldContinueBackgroundAudio: Boolean
): Boolean {
    return shouldPauseBuffering || shouldContinueBackgroundAudio
}

internal fun shouldRetainBackgroundAudioSession(
    shouldContinueBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean
): Boolean {
    return shouldContinueBackgroundAudio && wasPlaybackActive
}

internal fun shouldClearVideoSurfaceOnEnterBackground(
    shouldDisableVideoTrack: Boolean
): Boolean {
    // 关视频轨后一律清 surface：活跃仅音频与暂停态闲置释放都需要。
    return shouldDisableVideoTrack
}

internal fun shouldStopIdlePlaybackForBackgroundOptimization(
    shouldContinueBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean
): Boolean {
    return !shouldRetainBackgroundAudioSession(
        shouldContinueBackgroundAudio = shouldContinueBackgroundAudio,
        wasPlaybackActive = wasPlaybackActive
    )
}

internal fun shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
    isLowMemoryMode: Boolean,
    stillInBackground: Boolean,
    alreadyAppliedHeavyOptimization: Boolean,
    alreadyAppliedIdleRelease: Boolean,
    shouldContinueBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean,
    requestIdlePlaybackRelease: Boolean
): Boolean {
    return isLowMemoryMode &&
        stillInBackground &&
        alreadyAppliedHeavyOptimization &&
        !alreadyAppliedIdleRelease &&
        shouldStopIdlePlaybackForBackgroundOptimization(
            shouldContinueBackgroundAudio = shouldContinueBackgroundAudio,
            wasPlaybackActive = wasPlaybackActive
        ) &&
        requestIdlePlaybackRelease
}

internal fun shouldTrimDanmakuCachesOnEnterBackground(
    shouldDisableVideoTrack: Boolean
): Boolean {
    return shouldDisableVideoTrack
}

internal fun shouldRefreshVideoFrameOnEnterForeground(
    hadSavedTrackParams: Boolean,
    hasMediaItems: Boolean,
    playbackState: Int,
    retainedBackgroundAudio: Boolean = false,
): Boolean {
    // 后台音频仍在连续播放时，即使 seek 到 currentPosition，Media3 也可能回退到
    // 前一个音频同步点，造成回前台后重复播放约 2 秒。恢复视频轨和 surface 即可
    // 重新产出画面，不要打断连续音频时间线。
    return !retainedBackgroundAudio &&
        hadSavedTrackParams &&
        hasMediaItems &&
        playbackState != Player.STATE_IDLE
}

internal fun shouldKickPlaybackAfterForegroundTrackRestore(
    hadSavedTrackParams: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    hasForegroundResumeIntent: Boolean = false,
    isLeavingByNavigation: Boolean = false
): Boolean {
    if (isLeavingByNavigation) return false
    return hadSavedTrackParams &&
        (playWhenReady || hasForegroundResumeIntent) &&
        playbackState != Player.STATE_IDLE
}

internal fun shouldPreparePlaybackOnForegroundResume(
    hasForegroundResumeIntent: Boolean,
    hasMediaItems: Boolean,
    playbackState: Int,
    isLeavingByNavigation: Boolean = false
): Boolean {
    if (isLeavingByNavigation) return false
    return hasForegroundResumeIntent && hasMediaItems && playbackState == Player.STATE_IDLE
}

internal fun shouldResumePlaybackOnEnterForeground(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int
): Boolean {
    return playWhenReady && !isPlaying && playbackState == Player.STATE_READY
}

internal fun resolveTrackSelectionParametersForBackground(
    currentTrackSelectionParameters: androidx.media3.common.TrackSelectionParameters,
    shouldDisableVideoTrack: Boolean
): androidx.media3.common.TrackSelectionParameters {
    if (!shouldDisableVideoTrack) return currentTrackSelectionParameters
    return currentTrackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
        .setMaxVideoSize(0, 0)
        .build()
}

internal fun shouldPauseBackgroundBuffering(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return !isPlaybackActiveForLifecycle(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState
    )
}

internal fun shouldPauseBufferingOnEnterBackground(
    shouldPauseBuffering: Boolean,
    shouldContinueBackgroundAudio: Boolean
): Boolean {
    return shouldPauseBuffering && !shouldContinueBackgroundAudio
}

internal fun resolveNotificationIsPlaying(
    playerIsPlaying: Boolean?,
    cachedIsPlaying: Boolean
): Boolean {
    return playerIsPlaying ?: cachedIsPlaying
}

internal fun shouldRefreshNotificationOnPlaybackStateChange(
    isActive: Boolean,
    title: String
): Boolean {
    return isActive && title.isNotBlank()
}

/** A buffering player that is about to play still owns the media session; removing its
 * notification makes some system media panels send `stop` and leaves the player idle. */
internal fun shouldKeepPlaybackNotificationVisible(
    isActive: Boolean,
    title: String,
    isPlaying: Boolean,
    appInBackground: Boolean,
    isStartingPlayback: Boolean = false
): Boolean {
    return isActive && title.isNotBlank() && (isPlaying || isStartingPlayback || appInBackground)
}

internal fun isPlayerStartingPlayback(playWhenReady: Boolean, playbackState: Int): Boolean {
    return playWhenReady && playbackState == Player.STATE_BUFFERING
}

internal fun shouldClearStalePlaybackNotificationOnAppResume(
    isActive: Boolean,
    playerIsPlaying: Boolean
): Boolean {
    return !isActive || !playerIsPlaying
}

internal fun resolveEffectiveNotificationCoverUrl(
    incomingCoverUrl: String,
    cachedCoverUrl: String
): String {
    return incomingCoverUrl.takeIf { it.isNotBlank() } ?: cachedCoverUrl
}

internal fun <T> resolveEffectiveNotificationArtwork(
    incomingArtwork: T?,
    cachedArtwork: T?
): T? {
    return incomingArtwork ?: cachedArtwork
}

// 通知 small icon 必须是单色透明底图形：自适应启动图标自带彩色背景层，在部分系统上
// 会被直接渲染成纯色圆形，因此无论启动器图标能否解析，都使用所选图标变体配套的单色资源。
internal fun resolveNotificationIconRes(iconKey: String): Int =
    resolveNotificationSmallIconRes(iconKey)

internal fun resolveActivePlaybackLaunchIntent(
    context: Context,
    bvid: String?,
): Intent {
    return Intent(context, com.android.purebilibili.MainActivity::class.java).apply {
        bvid?.takeIf { it.isNotBlank() }?.let { activeBvid ->
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.bilibili.com/video/$activeBvid")
        }
        putExtra(com.android.purebilibili.EXTRA_OPEN_ACTIVE_PLAYBACK, true)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

internal fun shouldRebindMediaSessionPlayer(
    sessionPlayer: Any?,
    playbackPlayer: Any?
): Boolean {
    val unwrappedSessionPlayer = when (sessionPlayer) {
        is SessionPlayerBindingHandle -> sessionPlayer.boundPlayer
        else -> sessionPlayer
    }
    return playbackPlayer != null && unwrappedSessionPlayer !== playbackPlayer
}

internal fun resolveNotificationSmallIconRes(iconKey: String): Int {
    val normalizedKey = normalizeAppIconKey(iconKey)
    return when (normalizedKey) {
        "icon_blue_snow_maid" -> R.mipmap.ic_launcher_blue_snow_maid_monochrome
        "icon_blue_snow_maid_front" -> R.mipmap.ic_launcher_blue_snow_maid_front_monochrome
        // BiliPai 各变体的自适应启动图标与彩色前景图都不能作为 small icon，
        // 与它们 monochrome 层共用同一白色字形资源。
        "icon_bilipai" -> R.mipmap.ic_launcher_bilipai_monet_foreground
        "icon_bilipai_pink" -> R.mipmap.ic_launcher_bilipai_monet_foreground
        "icon_bilipai_white" -> R.mipmap.ic_launcher_bilipai_monet_foreground
        "icon_bilipai_monet" -> R.mipmap.ic_launcher_bilipai_monet_foreground
        "icon_3d" -> R.drawable.ic_launcher_foreground_flat
        else -> R.mipmap.ic_launcher_blue_snow_maid_monochrome
    }
}

internal enum class MediaControlType {
    PREVIOUS,
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT
}

internal interface SessionPlayerBindingHandle {
    val boundPlayer: Any?
}

internal enum class PlaylistSkipExecutionMode {
    CALLBACK,
    DIRECT_BACKGROUND,
    NONE
}

internal fun resolveMediaControlType(controlType: Int): MediaControlType? {
    return when (controlType) {
        MiniPlayerManager.ACTION_PREVIOUS -> MediaControlType.PREVIOUS
        MiniPlayerManager.ACTION_PLAY -> MediaControlType.PLAY
        MiniPlayerManager.ACTION_PAUSE -> MediaControlType.PAUSE
        MiniPlayerManager.ACTION_PLAY_PAUSE -> MediaControlType.PLAY_PAUSE
        MiniPlayerManager.ACTION_NEXT -> MediaControlType.NEXT
        else -> null
    }
}

internal fun resolveMediaButtonControlType(keyCode: Int, action: Int): MediaControlType? {
    if (action != KeyEvent.ACTION_DOWN) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaControlType.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_NEXT -> MediaControlType.NEXT
        KeyEvent.KEYCODE_MEDIA_PLAY -> MediaControlType.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaControlType.PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MediaControlType.PLAY_PAUSE
        else -> null
    }
}

internal fun resolvePipPlaybackControlType(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Int {
    return if (
        isPlaybackActiveForLifecycle(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState
        )
    ) {
        MiniPlayerManager.ACTION_PAUSE
    } else {
        MiniPlayerManager.ACTION_PLAY
    }
}

internal fun applyPlaybackMediaControlToPlayer(
    player: Player,
    controlType: MediaControlType
): Boolean {
    when (controlType) {
        MediaControlType.PLAY -> playPlayerFromUserAction(player)
        MediaControlType.PAUSE -> pausePlayerFromUserAction(player)
        MediaControlType.PLAY_PAUSE -> togglePlayerPlaybackFromUserAction(player)
        else -> return false
    }
    return true
}

internal fun resolvePlayingStateAfterMediaControl(
    controlType: MediaControlType,
    playerIsPlaying: Boolean
): Boolean {
    return when (controlType) {
        MediaControlType.PLAY -> true
        MediaControlType.PAUSE -> false
        MediaControlType.PLAY_PAUSE -> !playerIsPlaying
        MediaControlType.PREVIOUS,
        MediaControlType.NEXT -> playerIsPlaying
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
internal fun buildPipPlaybackRemoteActions(
    context: Context,
    player: Player?
): List<RemoteAction> {
    val actionType = resolvePipPlaybackControlType(
        isPlaying = player?.isPlaying == true,
        playWhenReady = player?.playWhenReady == true,
        playbackState = player?.playbackState ?: Player.STATE_IDLE
    )
    val isPauseAction = actionType == MiniPlayerManager.ACTION_PAUSE
    val intent = PendingIntent.getBroadcast(
        context,
        actionType,
        Intent(MiniPlayerManager.ACTION_MEDIA_CONTROL)
            .setPackage(context.packageName)
            .putExtra(MiniPlayerManager.EXTRA_CONTROL_TYPE, actionType),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return listOf(
        RemoteAction(
            Icon.createWithResource(
                context,
                if (isPauseAction) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            if (isPauseAction) "暂停" else "播放",
            if (isPauseAction) "暂停播放" else "继续播放",
            intent
        )
    )
}

internal fun resolveExternalTransportActionOrder(): IntArray {
    return intArrayOf(
        MiniPlayerManager.ACTION_NEXT,
        MiniPlayerManager.ACTION_PLAY_PAUSE,
        MiniPlayerManager.ACTION_PREVIOUS
    )
}

internal fun shouldEnableQueueNavigation(playlistSize: Int): Boolean {
    return playlistSize > 1
}

internal fun resolveQueueCurrentIndexForPlaylistRebuild(
    playlist: List<PlaylistItem>,
    currentBvid: String?,
    fallbackIndex: Int
): Int {
    if (playlist.isEmpty()) return -1
    val targetBvid = currentBvid?.trim().orEmpty()
    if (targetBvid.isNotEmpty()) {
        val matchedIndex = playlist.indexOfFirst { it.bvid == targetBvid }
        if (matchedIndex >= 0) return matchedIndex
    }
    return fallbackIndex.coerceIn(0, playlist.lastIndex)
}

internal fun resolvePlaylistIndexSyncFromQueue(
    playerCurrentIndex: Int,
    playlistSize: Int,
    currentPlaylistIndex: Int
): Int? {
    if (playerCurrentIndex !in 0 until playlistSize) return null
    if (playerCurrentIndex == currentPlaylistIndex) return null
    return playerCurrentIndex
}

internal fun buildQueueMetadataItems(playlist: List<PlaylistItem>): List<MediaItem> {
    return playlist.map { item ->
        MediaItem.Builder()
            .setMediaId(item.bvid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .setArtist(item.owner)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
}

internal data class QueueNavigationAvailability(
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    val isEnabled: Boolean
        get() = hasNext || hasPrevious
}

internal fun resolveQueueNavigationCommandIds(
    hasNext: Boolean,
    hasPrevious: Boolean
): Set<Int> {
    val commands = mutableSetOf<Int>()
    if (hasNext) {
        commands.add(Player.COMMAND_SEEK_TO_NEXT)
        commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }
    if (hasPrevious) {
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }
    return commands
}

internal fun buildQueueAwarePlayerCommands(
    baseCommands: Player.Commands,
    hasNext: Boolean,
    hasPrevious: Boolean
): Player.Commands {
    val builder = baseCommands.buildUpon()
    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    resolveQueueNavigationCommandIds(
        hasNext = hasNext,
        hasPrevious = hasPrevious
    ).forEach { command ->
        builder.add(command)
    }
    return builder.build()
}

internal fun shouldUseStandardQueueNavigation(
    playlist: List<PlaylistItem>,
    isLiveMode: Boolean
): Boolean {
    return !isLiveMode && playlist.isNotEmpty() && playlist.none { it.isBangumi }
}

internal fun shouldExposeVirtualQueueToSession(
    playlistSize: Int,
    timelineWindowCount: Int,
    isLiveMode: Boolean
): Boolean {
    if (isLiveMode || playlistSize <= 0) return false
    return timelineWindowCount == playlistSize
}

internal fun resolvePlaylistSkipExecutionMode(
    item: PlaylistItem?,
    callbackAvailable: Boolean,
    hasDirectPlaybackContext: Boolean
): PlaylistSkipExecutionMode {
    if (item == null) return PlaylistSkipExecutionMode.NONE
    if (item.isBangumi) {
        return if (callbackAvailable) PlaylistSkipExecutionMode.CALLBACK else PlaylistSkipExecutionMode.NONE
    }
    if (callbackAvailable) return PlaylistSkipExecutionMode.CALLBACK
    return if (hasDirectPlaybackContext) {
        PlaylistSkipExecutionMode.DIRECT_BACKGROUND
    } else {
        PlaylistSkipExecutionMode.NONE
    }
}

internal fun dispatchPlaylistNavigation(
    item: PlaylistItem?,
    callback: ((PlaylistItem) -> Unit)?
): Boolean {
    if (item == null || item.isBangumi || callback == null) return false
    callback(item)
    return true
}

internal fun dispatchBangumiNavigation(
    item: PlaylistItem?,
    callback: ((PlaylistItem) -> Unit)?
): Boolean {
    if (item == null || !item.isBangumi || callback == null) return false
    val seasonId = item.seasonId ?: 0L
    val epId = item.epId ?: 0L
    if (seasonId <= 0L || epId <= 0L) return false
    callback(item)
    return true
}

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

        fun getInstanceOrNull(): MiniPlayerManager? = INSTANCE
        
        //  [新增] 媒体控制常量
        const val ACTION_MEDIA_CONTROL = "com.android.purebilibili.MEDIA_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val ACTION_PREVIOUS = 1
        const val ACTION_PLAY_PAUSE = 2
        const val ACTION_NEXT = 3
        const val ACTION_PLAY = 4
        const val ACTION_PAUSE = 5
    }

    // --- 协程作用域 ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val remoteAudio = com.android.purebilibili.feature.audio.player.BackgroundAudioConnection(context) { remote ->
        val extras = remote.currentMediaItem?.mediaMetadata?.extras ?: android.os.Bundle.EMPTY
        val bvid = extras.getString("bvid")
        if (!bvid.isNullOrBlank()) {
            if (currentBvid != bvid || currentCid != extras.getLong("cid")) {
                cachedUiState = null
                val item = PlaylistManager.playlist.value.firstOrNull { it.bvid == bvid }
                item?.let { PlaylistManager.adoptCurrentPlayback(it.copy(cid = extras.getLong("cid"))) }
            }
            currentCover = extras.getString("cover").orEmpty().ifBlank { currentCover }
            currentBvid = bvid
            currentCid = extras.getLong("cid")
            currentTitle = remote.mediaMetadata.title?.toString().orEmpty()
            currentOwner = remote.mediaMetadata.artist?.toString().orEmpty()
        }
        isPlaying = remote.isPlaying
        currentPosition = remote.currentPosition.coerceAtLeast(0L)
        duration = remote.duration.takeIf { it > 0L } ?: duration
        progress = if (duration > 0L) currentPosition.toFloat() / duration else 0f
    }
    private var remoteTransferJob: Job? = null
    private var playbackStopGeneration = 0L
    private var remotePreferencesJob: Job? = null
    private var remoteBackgroundOverride: Pair<String, Boolean>? = null
    private var pendingBackgroundOverride: Pair<String, Boolean>? = null
    private val remoteReclaimMutex = kotlinx.coroutines.sync.Mutex()

    /** Only detached listen-video sessions enter this path; mounted video/PiP keeps its player. */
    private fun tryTransferDetachedAudio() {
        if (remoteAudio.active || remoteTransferJob?.isActive == true) return
        val local = player ?: return
        val state = cachedUiState ?: return
        if (!local.playWhenReady || !shouldContinueBackgroundAudio()) return
        // Preserve the native loudness pipeline until it has a separate-process equivalent.
        if (SettingsManager.getLoudnessNormalizationEnabledSync(context)) return
        val queue = PlaylistManager.playlist.value
        val index = queue.indexOfFirst { it.bvid == currentBvid && (it.cid == currentCid || it.cid == 0L) }
        val request = com.android.purebilibili.feature.audio.player.BackgroundAudioSnapshot(
            queue = queue.toMutableList().also { if (index in it.indices) it[index] = it[index].copy(cid = currentCid) },
            index = index, positionMs = local.currentPosition.coerceAtLeast(0L), playing = true,
            speed = local.playbackParameters.speed, accountMid = TokenManager.midCache,
            playMode = PlaylistManager.playMode.value, shuffle = PlaylistManager.shuffleEnabled.value,
            quality = state.currentQuality, audioQuality = state.selectedAudioQuality,
            audioUrl = state.audioUrl.orEmpty(), audioLanguage = state.currentAudioLang,
            volume = local.volume, handleAudioFocus = SettingsManager.getAudioFocusEnabledSync(context),
            reportHistory = !SettingsManager.isPrivacyModeEnabledSync(context),
            savedPlayMode = PlaylistManager.savedTransport.value.playMode,
            savedShuffle = PlaylistManager.savedTransport.value.shuffleEnabled,
            backgroundPlaybackOverride = PlayerBackgroundPlaybackSessions.selection(local, "video:$currentBvid"),
            backgroundOverrideBvid = currentBvid,
        )
        if (!com.android.purebilibili.feature.audio.player.canTransferBackgroundAudio(
                listening = AudioNowPlayingSession.active.value,
                hasPageOwner = pageOwnership.isRetained(local),
                liveOrPip = isLiveMode || isSystemPipActive || isMiniMode,
                hasSeparatePlaybackAccount = NetworkModule.playbackAccount() != null,
                snapshot = request
            )) return
        val stopGeneration = playbackStopGeneration
        remoteTransferJob = scope.launch {
            var stoppedLocal = false
            var transferred = false
            try {
                transferred = remoteAudio.transfer(request) {
                    if (player !== local || pageOwnership.isRetained(local) || !AudioNowPlayingSession.active.value ||
                        !local.playWhenReady || !shouldContinueBackgroundAudio() ||
                        isLiveMode || isSystemPipActive || isMiniMode) null else {
                        val handoffPosition = local.currentPosition.coerceAtLeast(0L)
                        resetBackgroundOptimizationState()
                        stoppedLocal = true
                        local.pause()
                        local.clearVideoSurface()
                        local.stop()
                        releaseMediaSession()
                        handoffPosition
                    }
                }
                if (transferred) {
                    remoteBackgroundOverride = request.backgroundPlaybackOverride?.let { value ->
                        request.backgroundOverrideBvid?.let { it to value }
                    }
                    clearPlaybackNotificationArtifacts(stopServiceDirectly = true)
                    cachedUiState = null
                    if (player === local && !pageOwnership.isRetained(local)) {
                        local.removeListener(playerListener)
                        if (_externalPlayer === local) _externalPlayer = null
                        if (_player === local) _player = null
                        local.release()
                    }
                    observeRemoteAudioPreferences()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Remote audio transfer unavailable; retaining local playback", e)
            } finally {
                if (!transferred && stopGeneration == playbackStopGeneration) {
                    remoteAudio.close()
                    if (stoppedLocal && player === local && !pageOwnership.isRetained(local)) {
                        local.prepare()
                        local.playWhenReady = true
                        updateMediaSession(local)
                    }
                }
            }
        }
    }

    /** Called by the video entry effect before preparing its local player. */
    internal suspend fun reclaimRemoteAudioForVideo(): com.android.purebilibili.feature.audio.player.BackgroundAudioSnapshot? =
        remoteReclaimMutex.withLock {
            remoteTransferJob?.cancel()
            remoteTransferJob?.join()
            val state = try {
                if (!remoteAudio.active) recoverRemoteAudioIfPresentLocked(failOnError = true)
                if (!remoteAudio.active) return@withLock null
                remotePreferencesJob?.cancel()
                remoteAudio.reclaim() ?: return@withLock null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unresponsive audio process must not keep the video page from loading.
                // Stopping the service also stops its playback, so local audio cannot double up.
                Logger.w(TAG, "Background audio did not return; stopping it before video playback", e)
                remotePreferencesJob?.cancel()
                remoteAudio.close()
                return@withLock null
            }
            pendingBackgroundOverride = state.backgroundPlaybackOverride?.let { value ->
                state.backgroundOverrideBvid?.takeIf { it == state.current?.bvid }?.let { it to value }
            }
            remoteBackgroundOverride = null
            state.current?.let { item ->
                com.android.purebilibili.core.player.PlaybackProgressManager.getInstance(context).apply {
                    // A return near the beginning must replace an older (>5s) cached position too.
                    clearPosition(item.bvid, item.cid)
                    savePosition(item.bvid, item.cid, state.positionMs)
                }
                PlaylistManager.adoptCurrentPlayback(item)
            }
            cachedUiState = null
            isPlaying = false
            state
        }

    internal suspend fun recoverRemoteAudioIfPresent(failOnError: Boolean = false) =
        remoteReclaimMutex.withLock { recoverRemoteAudioIfPresentLocked(failOnError) }

    private suspend fun recoverRemoteAudioIfPresentLocked(failOnError: Boolean) {
        if (remoteAudio.active) return
        try {
            val hasSaved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                TokenManager.awaitRestore(timeoutMs = 5000L)
                com.android.purebilibili.core.store.MultiProcessPreferences.get(context, "background_audio")
                    .data.first()[androidx.datastore.preferences.core.stringPreferencesKey("snapshot")] != null
            }
            if (!hasSaved) return
            val state = remoteAudio.recover()
            if (state == null || state.accountMid != TokenManager.midCache) {
                remoteAudio.close()
                return
            }
            state.current?.let { item ->
                currentBvid = item.bvid; currentCid = item.cid; currentTitle = item.title
                currentOwner = item.owner; currentCover = item.cover
                PlaylistManager.adoptCurrentPlayback(item)
            }
            isActive = true
            AudioNowPlayingSession.markListening()
            observeRemoteAudioPreferences()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // This connection may belong to an already-playing service after main-process death.
            // Canceling the UI's recovery must not stop that independent playback owner.
            remoteAudio.disconnect()
            throw e
        } catch (e: Exception) {
            remoteAudio.disconnect()
            Logger.w(TAG, "Unable to reconnect background audio", e)
            if (failOnError) throw e
        }
    }

    private fun observeRemoteAudioPreferences() {
        remotePreferencesJob?.cancel()
        remotePreferencesJob = scope.launch {
            combine(PlaylistManager.playMode, PlaylistManager.shuffleEnabled,
                SettingsManager.getPrivacyModeEnabled(context), PlaylistManager.playlist,
                PlaylistManager.savedTransport) { mode, shuffle, privacy, queue, saved ->
                (Triple(mode, shuffle, privacy) to queue) to saved
            }.collect { (runtime, saved) ->
                val (preferences, queue) = runtime
                val (mode, shuffle, privacy) = preferences
                if (!remoteAudio.active) return@collect
                remoteAudio.controller?.sendCustomCommand(
                    androidx.media3.session.SessionCommand(
                        com.android.purebilibili.feature.audio.player.AUDIO_TRANSPORT, android.os.Bundle.EMPTY
                    ), android.os.Bundle().apply {
                        putString("mode", mode.name); putBoolean("shuffle", shuffle)
                        putString("saved_mode", saved.playMode.name)
                        putBoolean("saved_shuffle", saved.shuffleEnabled)
                        putBoolean("report_history", !privacy)
                        if (queue.size in 1..128 && queue.none { it.isBangumi }) {
                            val json = com.android.purebilibili.feature.audio.player.backgroundAudioJson.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(PlaylistItem.serializer()), queue
                            )
                            if (json.toByteArray(Charsets.UTF_8).size <= 256 * 1024) putString("queue", json)
                        }
                    }
                )
            }
        }
    }

    private val backgroundPlaybackUseCase = VideoPlaybackUseCase()
    private var backgroundSkipJob: kotlinx.coroutines.Job? = null
    private var notificationMetadataJob: kotlinx.coroutines.Job? = null
    private var notificationThemeJob: Job? = null
    private var notificationThemeSettings: AppThemeSettings? = null
    private var notificationThemeMetadata: Pair<String, String>? = null
    internal var onNotificationRefreshRequested: (() -> Unit)? = null
    
    // 🔋 [后台优化] 低内存模式状态
    private var isLowMemoryMode = false
    private var backgroundOptimizationPlayer: Player? = null
    private var savedTrackParams: androidx.media3.common.TrackSelectionParameters? = null
    private var backgroundHeavyOptimizationJob: Job? = null
    private var backgroundPausedReleaseJob: Job? = null
    private var enteredBackgroundAtMs: Long = 0L
    @Volatile
    private var didApplyHeavyBackgroundVideoOptimization = false
    @Volatile
    private var didApplyIdlePlaybackRelease = false
    @Volatile
    private var pendingHeavyBackgroundVideoOptimization = false
    /**
     * 供 UI 在 Activity ON_RESUME 读取。
     * ProcessLifecycle 前台回调早于 Activity resume，因此不能在 onEnterForeground 里立刻清掉。
     */
    @Volatile
    private var pendingForegroundSurfaceRecovery = false

    /**
     * 前台恢复时是否需要强制重绑 surface。
     * 短后台轻量模式未拆视频链路时为 false，避免固定顿一下。
     */
    val needsForegroundSurfaceRecovery: Boolean
        get() = pendingForegroundSurfaceRecovery

    fun consumeForegroundSurfaceRecoveryNeed(): Boolean {
        val need = pendingForegroundSurfaceRecovery
        pendingForegroundSurfaceRecovery = false
        return need
    }
    
    //  [新增] 媒体控制广播接收器
    private val mediaControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_CONTROL) {
                val controlType = resolveMediaControlType(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                if (controlType != null) {
                    Logger.d(TAG, "🔔 通知栏控制: $controlType")
                    performMediaControl(controlType)
                }
            }
        }
    }
    private var mediaControlReceiverRegistered = false
    private var backgroundListenerRegistered = false
    
    init {
        backgroundPlaybackUseCase.initWithContext(context)
        //  注册媒体控制广播接收器
        val filter = android.content.IntentFilter(ACTION_MEDIA_CONTROL)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            mediaControlReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        mediaControlReceiverRegistered = true
        Logger.d(TAG, " 媒体控制广播接收器已注册")
        
        // 🔋 注册后台状态监听
        com.android.purebilibili.core.lifecycle.BackgroundManager.addListener(this)
        backgroundListenerRegistered = true
        Logger.d(TAG, "🔋 后台状态监听器已注册")
    }
    
    // ========== 🔋 后台状态回调 ==========
    
    /** Cancel work and discard resume flags before handing off or closing a player. */
    private fun resetBackgroundOptimizationState() {
        backgroundPausedReleaseJob?.cancel()
        backgroundPausedReleaseJob = null
        backgroundHeavyOptimizationJob?.cancel()
        backgroundHeavyOptimizationJob = null
        val owner = backgroundOptimizationPlayer
        val originalParams = savedTrackParams
        // Restore the departing player's own parameters while it is still managed/alive.
        // Never apply these parameters to the replacement player.
        if (owner != null && owner === player && originalParams != null) {
            runCatching { owner.trackSelectionParameters = originalParams }
                .onFailure { Logger.w(TAG, "Failed to restore departing player's tracks", it) }
        }
        backgroundOptimizationPlayer = null
        savedTrackParams = null
        isLowMemoryMode = false
        enteredBackgroundAtMs = 0L
        didApplyHeavyBackgroundVideoOptimization = false
        didApplyIdlePlaybackRelease = false
        pendingHeavyBackgroundVideoOptimization = false
        pendingForegroundSurfaceRecovery = false
        foregroundResumeIntent = false
    }

    private fun optimizeReplacementPlayerIfInBackground() {
        if (BackgroundManager.isInBackground && backgroundOptimizationPlayer !== player) {
            onEnterBackground()
        }
    }

    override fun onEnterBackground() {
        if (remoteAudio.active) return
        tryTransferDetachedAudio()
        if (!isActive) return
        val currentPlayer = player ?: return
        resetBackgroundOptimizationState()
        backgroundOptimizationPlayer = currentPlayer
        isLowMemoryMode = true
        enteredBackgroundAtMs = SystemClock.elapsedRealtime()
        foregroundResumeIntent = isPlaybackActiveForLifecycle(
            isPlaying = currentPlayer.isPlaying,
            playWhenReady = currentPlayer.playWhenReady,
            playbackState = currentPlayer.playbackState
        )
        
        // 🔧 [优化] 如果未在播放，直接暂停/停止缓冲，避免浪费 CDN 请求
        val shouldPauseBuffering = shouldPauseBackgroundBuffering(
            isPlaying = currentPlayer.isPlaying,
            playWhenReady = currentPlayer.playWhenReady,
            playbackState = currentPlayer.playbackState
        )
        val shouldKeepBackgroundAudio = shouldContinueBackgroundAudio()
        val shouldPauseOnBackground = shouldPauseBufferingOnEnterBackground(
            shouldPauseBuffering = shouldPauseBuffering,
            shouldContinueBackgroundAudio = shouldKeepBackgroundAudio
        )
        val shouldDisableVideoTrack = shouldDisableVideoTrackOnEnterBackground(
            shouldPauseBuffering = shouldPauseOnBackground,
            shouldContinueBackgroundAudio = shouldKeepBackgroundAudio
        )
        if (shouldPauseOnBackground) {
            currentPlayer.pause()
            Logger.d(TAG, "🔋 后台轻量模式：未播放，先暂停缓冲")
        } else if (shouldKeepBackgroundAudio) {
            Logger.d(TAG, "🔋 后台轻量模式：先保留视频链路，延迟切到仅音频")
        }

        if (!shouldDisableVideoTrack) {
            return
        }

        pendingHeavyBackgroundVideoOptimization = true
        // 优化：未处于播放状态且无后台音频意图时，直接拆解视频链路并释放 Surface/解码器，无需等待 15 秒延迟
        if (shouldPauseOnBackground && !shouldKeepBackgroundAudio) {
            Logger.d(TAG, "🔋 后台即时优化：未在播放且无后台音频，立即拆除视频链路与释放闲置资源")
            applyHeavyBackgroundVideoOptimization(
                currentPlayer = currentPlayer,
                shouldKeepBackgroundAudio = false,
                wasPlaybackActive = foregroundResumeIntent,
                requestIdlePlaybackRelease = true
            )
            return
        }

        // 短后台不立刻关视频轨/清 surface/清弹幕，降低回前台固定顿一下的概率（仅针对活跃后台音频）。
        scheduleBackgroundVideoOptimization(currentPlayer)
    }

    private fun scheduleBackgroundVideoOptimization(currentPlayer: Player) {
        backgroundHeavyOptimizationJob?.cancel()
        backgroundHeavyOptimizationJob = scope.launch {
            delay(SHORT_BACKGROUND_LIGHT_MODE_MS)
            val elapsedMs = (SystemClock.elapsedRealtime() - enteredBackgroundAtMs).coerceAtLeast(0L)
            // The timeout belongs to the player that entered background, not whichever
            // player happens to be current when the coroutine wakes up.
            if (player !== currentPlayer || backgroundOptimizationPlayer !== currentPlayer) return@launch
            val activePlayer = currentPlayer
            if (
                !shouldRunHeavyBackgroundVideoOptimization(
                    shouldDisableVideoTrack = pendingHeavyBackgroundVideoOptimization,
                    stillInBackground = isLowMemoryMode && BackgroundManager.isInBackground,
                    backgroundElapsedMs = elapsedMs
                )
            ) {
                return@launch
            }
            applyHeavyBackgroundVideoOptimization(
                currentPlayer = activePlayer,
                shouldKeepBackgroundAudio = shouldContinueBackgroundAudio(),
                wasPlaybackActive = foregroundResumeIntent,
                // 超时后的无后台音频会话直接 stop，丢掉解码器侧缓冲。
                requestIdlePlaybackRelease = true
            )
        }
    }

    /**
     * 系统内存压力回调：短后台轻量窗口内提前拆视频链路；
     * 更高压力时可对无后台音频意图的会话执行闲置 stop 释放。
     * 由 Application.onTrimMemory 转发。
     */
    fun onMemoryPressureTrim(
        level: Int,
        requestIdlePlaybackRelease: Boolean =
            com.android.purebilibili.app.PureApplicationRuntimeConfig
                .shouldRequestIdlePlaybackReleaseOnTrimLevel(level)
    ) {
        val forceDueToMemoryPressure = shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(level)
        val shouldKeepBackgroundAudio = shouldContinueBackgroundAudio()
        val activePlayer = player
        if (activePlayer == null || backgroundOptimizationPlayer !== activePlayer) return

        if (
            shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
                isLowMemoryMode = isLowMemoryMode,
                stillInBackground = BackgroundManager.isInBackground,
                alreadyAppliedHeavyOptimization = didApplyHeavyBackgroundVideoOptimization,
                shouldDisableVideoTrack = pendingHeavyBackgroundVideoOptimization,
                forceDueToMemoryPressure = forceDueToMemoryPressure
            )
        ) {
            if (activePlayer != null) {
                backgroundHeavyOptimizationJob?.cancel()
                backgroundHeavyOptimizationJob = null
                Logger.d(TAG, "🔋 内存压力(level=$level)：提前进入后台重度模式")
                applyHeavyBackgroundVideoOptimization(
                    currentPlayer = activePlayer,
                    shouldKeepBackgroundAudio = shouldKeepBackgroundAudio,
                    wasPlaybackActive = foregroundResumeIntent,
                    requestIdlePlaybackRelease = requestIdlePlaybackRelease
                )
            }
            return
        }

        if (
            activePlayer != null &&
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = isLowMemoryMode,
                stillInBackground = BackgroundManager.isInBackground,
                alreadyAppliedHeavyOptimization = didApplyHeavyBackgroundVideoOptimization,
                alreadyAppliedIdleRelease = didApplyIdlePlaybackRelease,
                shouldContinueBackgroundAudio = shouldKeepBackgroundAudio,
                wasPlaybackActive = foregroundResumeIntent,
                requestIdlePlaybackRelease = requestIdlePlaybackRelease
            )
        ) {
            Logger.d(TAG, "🔋 内存压力(level=$level)：升级为闲置 stop 释放")
            applyIdlePlaybackRelease(activePlayer)
        }
    }
    
    override fun onEnterForeground() {
        if (remoteAudio.active) return
        backgroundPausedReleaseJob?.cancel()
        backgroundPausedReleaseJob = null
        if (!isLowMemoryMode) return
        if (player == null || backgroundOptimizationPlayer !== player) {
            resetBackgroundOptimizationState()
            return
        }
        isLowMemoryMode = false
        pendingHeavyBackgroundVideoOptimization = false
        backgroundHeavyOptimizationJob?.cancel()
        backgroundHeavyOptimizationJob = null
        val currentPlayer = player ?: return
        val hadSavedTrackParams = savedTrackParams != null
        val appliedHeavyOptimization = didApplyHeavyBackgroundVideoOptimization
        val appliedIdleRelease = didApplyIdlePlaybackRelease
        val retainedBackgroundAudio = shouldRetainBackgroundAudioSession(
            shouldContinueBackgroundAudio = shouldContinueBackgroundAudio(),
            wasPlaybackActive = isPlaybackActiveForLifecycle(
                isPlaying = currentPlayer.isPlaying,
                playWhenReady = currentPlayer.playWhenReady,
                playbackState = currentPlayer.playbackState,
            ),
        )
        
        // 恢复视频轨道
        savedTrackParams?.let { originalParams ->
            currentPlayer.trackSelectionParameters = originalParams
            savedTrackParams = null
            Logger.d(TAG, "🌅 前台模式：恢复视频轨道")
        }
        didApplyHeavyBackgroundVideoOptimization = false
        didApplyIdlePlaybackRelease = false

        if (shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = hadSavedTrackParams,
                hasMediaItems = currentPlayer.mediaItemCount > 0,
                playbackState = currentPlayer.playbackState,
                retainedBackgroundAudio = retainedBackgroundAudio,
            )
        ) {
            val restorePositionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
            currentPlayer.seekTo(restorePositionMs)
            Logger.d(TAG, "🎬 前台模式：请求重新渲染当前帧，避免返回视频页黑屏")
        } else if (hadSavedTrackParams && retainedBackgroundAudio) {
            Logger.d(TAG, "🎵 前台模式：保留连续音频时间线，恢复视频轨时跳过 seek")
        }

        val shouldPrepareForegroundPlayback = shouldPreparePlaybackOnForegroundResume(
            hasForegroundResumeIntent = foregroundResumeIntent,
            hasMediaItems = currentPlayer.mediaItemCount > 0,
            playbackState = currentPlayer.playbackState,
            isLeavingByNavigation = isLeavingByNavigation
        )
        val shouldPrepareAfterIdleRelease = appliedIdleRelease &&
            !isLeavingByNavigation &&
            currentPlayer.mediaItemCount > 0 &&
            currentPlayer.playbackState == Player.STATE_IDLE
        if (shouldPrepareForegroundPlayback || shouldPrepareAfterIdleRelease) {
            currentPlayer.prepare()
        }
        if (shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = hadSavedTrackParams,
                playWhenReady = currentPlayer.playWhenReady,
                playbackState = currentPlayer.playbackState,
                hasForegroundResumeIntent = foregroundResumeIntent,
                isLeavingByNavigation = isLeavingByNavigation
            )
            || shouldPrepareForegroundPlayback
        ) {
            currentPlayer.playWhenReady = true
            currentPlayer.play()
            foregroundResumeIntent = false
            Logger.d(TAG, "▶️ 前台模式：恢复视频轨道后主动唤醒渲染链路")
        } else if (shouldResumePlaybackOnEnterForeground(
                playWhenReady = currentPlayer.playWhenReady,
                isPlaying = currentPlayer.isPlaying,
                playbackState = currentPlayer.playbackState
            )
        ) {
            currentPlayer.playWhenReady = true
            currentPlayer.play()
            foregroundResumeIntent = false
            Logger.d(TAG, "▶️ 前台模式：恢复卡在 READY 的播放会话")
        }
        if (isLeavingByNavigation) {
            foregroundResumeIntent = false
        }
        if (!appliedHeavyOptimization && !hadSavedTrackParams) {
            Logger.d(TAG, "🌅 前台模式：短后台轻量恢复，跳过视频轨重建")
        }
    }

    private fun applyHeavyBackgroundVideoOptimization(
        currentPlayer: Player,
        shouldKeepBackgroundAudio: Boolean,
        wasPlaybackActive: Boolean,
        requestIdlePlaybackRelease: Boolean = false
    ) {
        if (player !== currentPlayer || backgroundOptimizationPlayer !== currentPlayer) return
        if (savedTrackParams == null) {
            savedTrackParams = currentPlayer.trackSelectionParameters
        }
        currentPlayer.trackSelectionParameters = resolveTrackSelectionParametersForBackground(
            currentTrackSelectionParameters = currentPlayer.trackSelectionParameters,
            shouldDisableVideoTrack = true
        )
        if (shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = true)) {
            currentPlayer.clearVideoSurface()
        }
        if (shouldTrimDanmakuCachesOnEnterBackground(shouldDisableVideoTrack = true)) {
            com.android.purebilibili.feature.video.danmaku.DanmakuSessionFactory.trimCachesForBackground()
        }
        pendingHeavyBackgroundVideoOptimization = false
        didApplyHeavyBackgroundVideoOptimization = true
        pendingForegroundSurfaceRecovery = true
        if (
            requestIdlePlaybackRelease &&
            shouldStopIdlePlaybackForBackgroundOptimization(
                shouldContinueBackgroundAudio = shouldKeepBackgroundAudio,
                wasPlaybackActive = wasPlaybackActive
            )
        ) {
            applyIdlePlaybackRelease(currentPlayer)
        } else if (
            shouldRetainBackgroundAudioSession(
                shouldContinueBackgroundAudio = shouldKeepBackgroundAudio,
                wasPlaybackActive = wasPlaybackActive
            )
        ) {
            Logger.d(TAG, "🔋 后台重度模式：禁用视频轨道，仅保留音频")
        } else {
            Logger.d(TAG, "🔋 后台重度模式：暂停缓冲并禁用视频轨道")
        }
    }

    private fun applyIdlePlaybackRelease(currentPlayer: Player) {
        if (player !== currentPlayer || backgroundOptimizationPlayer !== currentPlayer) return
        if (didApplyIdlePlaybackRelease) return
        runCatching {
            currentPlayer.clearVideoSurface()
            currentPlayer.stop()
        }.onFailure { error ->
            Logger.w(TAG, "闲置 stop 释放失败: ${error.message}")
        }
        didApplyIdlePlaybackRelease = true
        pendingForegroundSurfaceRecovery = true
        Logger.d(TAG, "🔋 后台闲置释放：stop 播放器并保留 media item")
    }

    /** Re-evaluate a pause made after entering background, including media-session controls. */
    private fun onBackgroundPlayWhenReadyChanged(playWhenReady: Boolean) {
        backgroundPausedReleaseJob?.cancel()
        backgroundPausedReleaseJob = null
        val currentPlayer = player ?: return
        if (!isLowMemoryMode || !BackgroundManager.isInBackground ||
            backgroundOptimizationPlayer !== currentPlayer
        ) return

        // The current request supersedes the snapshot taken while entering background.
        // In particular, returning to the app must not undo an explicit background pause.
        foregroundResumeIntent = playWhenReady
        if (playWhenReady) {
            if (didApplyIdlePlaybackRelease) {
                didApplyIdlePlaybackRelease = false
                if (currentPlayer.mediaItemCount > 0 && currentPlayer.playbackState == Player.STATE_IDLE) {
                    currentPlayer.prepare()
                }
            }
            if (pendingHeavyBackgroundVideoOptimization && !didApplyHeavyBackgroundVideoOptimization) {
                scheduleBackgroundVideoOptimization(currentPlayer)
            }
            return
        }

        fun canRelease(): Boolean = shouldReleasePausedBackgroundPlayback(
            isInBackground = isLowMemoryMode && BackgroundManager.isInBackground,
            ownsBackgroundPlayer = isActive && player === currentPlayer &&
                backgroundOptimizationPlayer === currentPlayer,
            playWhenReady = currentPlayer.playWhenReady,
            isPlaying = currentPlayer.isPlaying,
            hasMediaItems = currentPlayer.mediaItemCount > 0,
            isPipActiveOrPending = shouldKeepPlaybackForPipTransition(),
            alreadyReleased = didApplyIdlePlaybackRelease,
        )
        if (!canRelease()) return
        // The older deadline must not stop a newly paused session before its grace period.
        backgroundHeavyOptimizationJob?.cancel()
        backgroundHeavyOptimizationJob = null
        backgroundPausedReleaseJob = scope.launch {
            delay(BACKGROUND_PAUSED_RELEASE_DELAY_MS)
            backgroundPausedReleaseJob = null
            if (!canRelease()) return@launch
            // Retire the older "keep background audio" decision before dropping buffers.
            backgroundHeavyOptimizationJob?.cancel()
            backgroundHeavyOptimizationJob = null
            applyHeavyBackgroundVideoOptimization(
                currentPlayer = currentPlayer,
                shouldKeepBackgroundAudio = false,
                wasPlaybackActive = false,
                requestIdlePlaybackRelease = true,
            )
        }
    }


    // --- 播放器状态 (可观察) ---
    var isActive by mutableStateOf(false)
        private set
    
    var miniPlayerSessionId by mutableLongStateOf(0L)
        private set

    var isMiniMode by mutableStateOf(false)
        private set

    /**
     * 迷你播放器在屏幕上的最新像素边界，由 MiniPlayerOverlay 回报，
     * 供系统 PIP 的 sourceRectHint 做无缝过渡；无小窗时为 null。
     */
    var miniPlayerSourceBoundsPx by mutableStateOf<android.graphics.Rect?>(null)
    
    // 🚀 [新增] 导航抑制标志：在导航到视频页面期间不显示小窗
    var isNavigatingToVideo by mutableStateOf(false)
    
    // 🎯 [新增] 导航离开标志：区分"应用导航离开"和"应用进入后台"
    // true = 用户通过返回按钮离开视频页面，应该停止播放
    // false = 用户按 Home 键离开应用，应该继续后台播放
    var isLeavingByNavigation by mutableStateOf(false)
    @Volatile
    private var lastUserLeaveHintAtMs: Long = 0L

    var isPlaying by mutableStateOf(false)
        private set

    var isSystemPipActive by mutableStateOf(false)
        private set

    private var pendingPlaybackRoutePip by mutableStateOf(false)

    var currentPosition by mutableLongStateOf(0L)
        private set

    var duration by mutableLongStateOf(0L)
        private set
    
    var progress by mutableFloatStateOf(0f)
        private set

    @Volatile
    private var playbackServiceRequested = false
    @Volatile
    private var lastForegroundStartAtMs = 0L
    @Volatile
    private var foregroundResumeIntent = false

    // --- 当前视频信息 ---
    var currentBvid by mutableStateOf<String?>(null)
        private set

    var currentAudioMediaId by mutableStateOf<String?>(null)
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

    //  [新增] 当前视频的 aid，用于弹幕元数据加载
    var currentAid by mutableLongStateOf(0L)
        private set
    
    //  [新增] 缓存的视频详情页 UI 状态，用于从小窗返回时恢复
    var cachedUiState: VideoPlaybackUiState.Success? = null
        private set
    
    //  [新增] 小窗入场方向：true=从左边进入，false=从右边进入
    var entryFromLeft by mutableStateOf(false)
        private set

    // 📺 [新增] 直播小窗模式
    var isLiveMode by mutableStateOf(false)
        private set
    var currentRoomId by mutableLongStateOf(0L)
        private set
    // 直播主播名（展开时传回 LivePlayerScreen）
    var currentLiveUname by mutableStateOf("")
        private set

    // 📐 [新增] 视频原始流分辨率与高宽比，用于小窗和系统画中画自适应
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    var videoAspectRatio by mutableFloatStateOf(16f / 9f)
        private set

    // [新增] 保存当前通知实例，供 PlaybackService 使用
    var currentNotification: android.app.Notification? = null
        private set
    private var cachedArtworkBitmap: Bitmap? = null
    

    fun updateCachedVideoTags(bvid: String, tags: List<com.android.purebilibili.data.model.response.VideoTag>) {
        val cached = cachedUiState ?: return
        if (cached.info.bvid == bvid) {
            cachedUiState = cached.copy(videoTags = tags)
        }
    }

    /**
     * 同步由详情 ViewModel 在后台完成的换集结果。
     *
     * 后台/熄屏时 Compose 的 lifecycle-aware state collection 会暂停，不能依赖页面侧的
     * setVideoInfo 来刷新通知和当前媒体身份，否则播放器已换集而通知、返回目标仍停在旧视频。
     */
    fun syncCurrentVideoInfo(state: VideoPlaybackUiState.Success, expectedPlayer: ExoPlayer) {
        // Covered pages may finish loading after another detail has reclaimed the session.
        if (player !== expectedPlayer) return
        val info = state.info
        currentBvid = info.bvid
        currentAudioMediaId = null
        currentTitle = info.title
        currentCover = info.pic
        currentOwner = info.owner.name
        currentCid = info.cid
        currentAid = info.aid
        cachedUiState = state
        isActive = true
        isLiveMode = false
        currentRoomId = 0L
        currentLiveUname = ""
        updateMediaMetadata(
            title = info.title,
            artist = info.owner.name,
            coverUrl = info.pic
        )
    }
    
    //  [新增] 获取并清除缓存的 UI 状态
    fun consumeCachedUiState(): VideoPlaybackUiState.Success? {
        val state = cachedUiState
        // 不清除缓存，允许多次复用
        return state
    }

    // --- ExoPlayer 实例 ---
    private var _player by mutableStateOf<ExoPlayer?>(null)
    //  外部播放器引用（来自 VideoDetailScreen 的 VideoPlayerState）
    private var _externalPlayer by mutableStateOf<ExoPlayer?>(null)
    private val pageOwnership = PlaybackPageOwnership()
    //  优先使用外部播放器（如果存在）
    val player: ExoPlayer?
        get() = _externalPlayer ?: _player
    
    //  [修复2] 检查是否有外部播放器
    val hasExternalPlayer: Boolean
        get() = _externalPlayer != null

    /**
     * 判断指定 player 是否仍由 MiniPlayerManager 持有。
     * 仅用于销毁阶段的身份校验，避免误保留旧实例。
     */
    fun isPlayerManaged(target: ExoPlayer): Boolean {
        return _externalPlayer === target || _player === target
    }

    internal fun retainPlayerForPage(target: ExoPlayer) {
        remoteTransferJob?.cancel()
        // A detail can reclaim the same instance from the bottom bar. Replacing the
        // reference is then a no-op, so explicitly restore the saved video tracks and
        // cancel delayed background jobs before the page binds its new video surface.
        if (target === player) resetBackgroundOptimizationState()
        pageOwnership.retain(target)
    }

    internal fun releasePlayerFromPage(target: ExoPlayer) {
        pageOwnership.release(target)
        tryTransferDetachedAudio()
    }

    internal fun isPlayerRetainedByPage(target: ExoPlayer): Boolean =
        pageOwnership.isRetained(target)

    /**
     * 仅当外部播放器引用匹配目标实例时才清理，避免误清理新播放器引用。
     */
    fun clearExternalPlayerIfMatches(target: ExoPlayer): Boolean {
        if (_externalPlayer === target) {
            resetBackgroundOptimizationState()
            Logger.d(TAG, "clearExternalPlayerIfMatches: cleared external player ${target.hashCode()}")
            target.removeListener(playerListener)
            _externalPlayer = null
            // 会话包装器也持有播放器；只释放仍绑定该实例的会话。
            if (mediaSession?.let { !shouldRebindMediaSessionPlayer(it.player, target) } == true) {
                releaseMediaSession()
            }
            return true
        }
        return false
    }
    
    //  [修复2] 清除外部播放器引用（从小窗返回全屏时调用）
    fun resetExternalPlayer() {
        resetBackgroundOptimizationState()
        Logger.d(TAG, " resetExternalPlayer: clearing external player reference")
        _externalPlayer?.removeListener(playerListener)
        _externalPlayer = null
    }

    // --- MediaSession ---
    var mediaSession: MediaSession? = null
    private var mediaSessionNavigationAvailability: QueueNavigationAvailability? = null

    private inner class QueueAwareSessionPlayer(
        private val delegatePlayer: Player
    ) : ForwardingPlayer(delegatePlayer), SessionPlayerBindingHandle {

        override val boundPlayer: Any?
            get() = delegatePlayer

        private fun queueItems(): List<MediaItem> {
            val playlist = PlaylistManager.playlist.value
            return if (shouldUseStandardQueueNavigation(playlist, isLiveMode)) {
                buildQueueMetadataItems(playlist)
            } else {
                emptyList()
            }
        }

        private fun sessionQueueItems(): List<MediaItem> {
            val items = queueItems()
            return if (
                shouldExposeVirtualQueueToSession(
                    playlistSize = items.size,
                    timelineWindowCount = delegatePlayer.currentTimeline.windowCount,
                    isLiveMode = isLiveMode
                )
            ) {
                items
            } else {
                emptyList()
            }
        }

        private fun queueCurrentIndex(): Int {
            val items = sessionQueueItems()
            return resolveQueueCurrentIndexForPlaylistRebuild(
                playlist = PlaylistManager.playlist.value,
                currentBvid = currentBvid,
                fallbackIndex = PlaylistManager.currentIndex.value
                    .takeIf { it >= 0 }
                    ?: delegatePlayer.currentMediaItemIndex
            ).coerceIn(-1, (items.size - 1).coerceAtLeast(-1))
        }

        override fun getAvailableCommands(): Player.Commands {
            val navigationAvailability = resolveSessionNavigationAvailability()
            return buildQueueAwarePlayerCommands(
                baseCommands = super.getAvailableCommands(),
                hasNext = navigationAvailability.hasNext,
                hasPrevious = navigationAvailability.hasPrevious
            )
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun getMediaItemCount(): Int {
            val items = sessionQueueItems()
            return if (items.isNotEmpty()) items.size else super.getMediaItemCount()
        }

        override fun getCurrentMediaItemIndex(): Int {
            val items = sessionQueueItems()
            return if (items.isNotEmpty()) queueCurrentIndex() else super.getCurrentMediaItemIndex()
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val items = sessionQueueItems()
            val index = queueCurrentIndex()
            return items.getOrNull(index) ?: super.getCurrentMediaItem()
        }

        override fun getMediaItemAt(index: Int): MediaItem {
            val items = sessionQueueItems()
            return items.getOrNull(index) ?: super.getMediaItemAt(index)
        }

        override fun hasNextMediaItem(): Boolean {
            val items = queueItems()
            return if (items.isNotEmpty()) {
                resolveSessionNavigationAvailability().hasNext
            } else {
                super.hasNextMediaItem()
            }
        }

        override fun hasPreviousMediaItem(): Boolean {
            val items = queueItems()
            return if (items.isNotEmpty()) {
                resolveSessionNavigationAvailability().hasPrevious
            } else {
                super.hasPreviousMediaItem()
            }
        }

        override fun seekToNextMediaItem() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playNext()
            } else {
                super.seekToNextMediaItem()
            }
        }

        override fun seekToPreviousMediaItem() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playPrevious()
            } else {
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToNext() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playNext()
            } else {
                super.seekToNext()
            }
        }

        override fun seekToPrevious() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playPrevious()
            } else {
                super.seekToPrevious()
            }
        }
    }
    
    //  [新增] MediaSession 回调处理器，支持系统媒体控件
    private val mediaSessionCallback = object : MediaSession.Callback {
        //  处理系统媒体按钮事件
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            Logger.d(TAG, " onMediaButtonEvent: action=${intent.action}")
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }
            val controlType = keyEvent?.let { resolveMediaButtonControlType(it.keyCode, it.action) }
            if (controlType != null) {
                Logger.d(TAG, "🎮 媒体按键控制: $controlType")
                performMediaControl(controlType)
                return true
            }
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
                SESSION_COMMAND_SKIP_TO_PREVIOUS -> performMediaControl(MediaControlType.PREVIOUS)
                SESSION_COMMAND_SKIP_TO_NEXT -> performMediaControl(MediaControlType.NEXT)
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
            val navigationAvailability = resolveSessionNavigationAvailability()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                )
                .setAvailablePlayerCommands(
                    buildQueueAwarePlayerCommands(
                        baseCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                        hasNext = navigationAvailability.hasNext,
                        hasPrevious = navigationAvailability.hasPrevious
                    )
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
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        val result = shouldShowInAppMiniPlayerByPolicy(
            mode = mode,
            isActive = isActive,
            isNavigatingToVideo = isNavigatingToVideo,
            stopPlaybackOnExit = stopPlaybackOnExit
        )
        Logger.d(TAG, "📲 shouldShowInAppMiniPlayer: mode=$mode, isActive=$isActive, navigating=$isNavigatingToVideo, result=$result")
        return result
    }
    
    /**
     * 判断是否应该进入系统画中画模式（按 Home 键时）
     */
    fun shouldEnterPip(): Boolean {
        val mode = getCurrentMode()
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        val result = shouldEnterPipByPolicy(
            mode = mode,
            isActive = isActive,
            stopPlaybackOnExit = stopPlaybackOnExit
        )
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
        val backgroundKey = if (isLiveMode) "live:$currentRoomId" else currentBvid?.takeIf { it.isNotBlank() }?.let { "video:$it" }
        val backgroundPlaybackEnabled = remoteBackgroundOverride?.takeIf {
            remoteAudio.active && it.first == currentBvid
        }?.second ?: PlayerBackgroundPlaybackSessions.effective(
            player, backgroundKey, SettingsManager.getBackgroundPlaybackEnabledSync(context),
        )
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        return shouldContinueBackgroundAudioByPolicy(
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            mode = mode,
            isActive = isActive,
            isLeavingByNavigation = isLeavingByNavigation,
            stopPlaybackOnExit = stopPlaybackOnExit,
            shouldKeepPlaybackForPipTransition = shouldKeepPlaybackForPipTransition(),
            keepForAudioNowPlaying = shouldKeepPlaybackForAudioNowPlayingBar(
                sessionActive = AudioNowPlayingSession.active.value,
                barEnabled = SettingsManager.getAudioNowPlayingBarEnabledSync(context),
            ),
        )
    }

    fun refreshMediaSessionBinding() {
        val currentPlayer = player ?: return
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, currentPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            updateMediaSession(currentPlayer)
        }
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = currentPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
    }

    fun clearPlaybackNotificationIfIdleOnResume() {
        if (!shouldClearStalePlaybackNotificationOnAppResume(
                isActive = isActive,
                playerIsPlaying = player?.isPlaying == true
            )
        ) {
            return
        }
        playbackServiceRequested = false
        clearPlaybackNotificationArtifacts()
    }
    
    /**
     * 🔄 重置导航离开标志（在视频页计入时调用）
     */
    fun resetNavigationFlag() {
        isLeavingByNavigation = false
        Logger.d(TAG, "🔄 resetNavigationFlag: isLeavingByNavigation=false")
    }

    fun markUserLeaveHint() {
        lastUserLeaveHintAtMs = SystemClock.elapsedRealtime()
    }

    fun clearUserLeaveHint() {
        lastUserLeaveHintAtMs = 0L
    }

    fun updatePlaybackRoutePipRequest(shouldTriggerPip: Boolean) {
        pendingPlaybackRoutePip = shouldTriggerPip
    }

    fun updateSystemPipActive(isActive: Boolean) {
        isSystemPipActive = isActive
        if (isActive) {
            pendingPlaybackRoutePip = false
        }
    }

    fun clearPlaybackRoutePipState() {
        pendingPlaybackRoutePip = false
        isSystemPipActive = false
    }

    fun shouldKeepPlaybackForPipTransition(): Boolean {
        return shouldKeepPlaybackForPipTransition(
            isSystemPipActive = isSystemPipActive,
            hasPendingPlaybackRoutePip = pendingPlaybackRoutePip
        )
    }

    fun hasRecentUserLeaveHint(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val last = lastUserLeaveHintAtMs
        if (last <= 0L) return false
        val delta = nowElapsedMs - last
        return delta in 0L..USER_LEAVE_HINT_WINDOW_MS
    }
    
    /**
     * 🎯 标记通过导航离开（在返回按钮点击时调用）
     *  [修复] 在默认模式和画中画模式下立即暂停播放，解决生命周期时序问题
     */
    /**
     * @param deferPlaybackStop 为 true 时只打离开标记，不立刻 pause/清 player。
     * 卡片 sharedBounds 返回（一镜到底）需要表面跟壳缩；过早停播会让落位变成
     * 「黑壳瞬间卸掉、列表卡已在原位」。dispose / 返回结束后仍会释放。
     */
    /**
     * 从合集列表等入口进入「另一支」视频时，立刻挂起当前全局 player 的声音。
     * 不释放 player、不清会话元数据，避免打断返回上一级时的恢复链路。
     */
    fun haltForeignPlaybackForIncomingVideo(incomingBvid: String) {
        val target = incomingBvid.trim()
        if (target.isBlank()) return
        val likelyActive = isActive ||
            isPlaying ||
            _externalPlayer?.let { it.isPlaying || it.playWhenReady } == true ||
            _player?.let { it.isPlaying || it.playWhenReady } == true
        if (
            !com.android.purebilibili.feature.video.state.shouldHaltForeignPlaybackOnVideoEntry(
                incomingBvid = target,
                activeBvid = currentBvid,
                isPlaybackLikelyActive = likelyActive
            )
        ) {
            return
        }
        Logger.d(
            TAG,
            "🔇 haltForeignPlaybackForIncomingVideo: active=$currentBvid -> incoming=$target"
        )
        _externalPlayer?.let { player ->
            player.volume = 0f
            player.playWhenReady = false
            if (player.isPlaying) player.pause()
        }
        _player?.let { player ->
            player.volume = 0f
            player.playWhenReady = false
            if (player.isPlaying) player.pause()
        }
        isPlaying = false
    }

    /** Publish the current resume entry before exit cleanup, including stop-on-exit sessions. */
    fun prepareAudioNowPlayingForNavigationExit(expectedBvid: String? = null): Boolean {
        if (!isActive || player == null) return false
        if (!shouldHandleNavigationLeaveForBvid(expectedBvid, currentBvid)) return false
        if (!shouldActivateAudioBarOnVideoExit(
                barEnabled = SettingsManager.getAudioNowPlayingBarEnabledSync(context),
                hasVideoIdentity = !currentBvid.isNullOrBlank() && currentCid > 0L,
                isLive = isLiveMode,
                isMiniOrPip = isMiniMode || isSystemPipActive,
                isNavigatingToVideo = isNavigatingToVideo,
                // Explicit video-window settings take priority over the audio entry handoff.
                inAppMiniPlayerEnabled = getCurrentMode().supportsInAppMiniPlayer,
                stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context),
            )) return false
        PlaylistManager.adoptCurrentPlayback(
            PlaylistItem(
                bvid = currentBvid.orEmpty(),
                cid = currentCid,
                title = currentTitle,
                cover = currentCover,
                owner = currentOwner,
                duration = duration.coerceAtLeast(0L) / 1000L,
            )
        )
        AudioNowPlayingSession.markListening()
        return true
    }

    fun markLeavingByNavigation(
        expectedBvid: String? = null,
        forceStop: Boolean = false,
        deferPlaybackStop: Boolean = false,
    ) {
        if (!shouldHandleNavigationLeaveForBvid(expectedBvid = expectedBvid, currentBvid = currentBvid)) {
            Logger.d(
                TAG,
                "⏭️ markLeavingByNavigation ignored: expected=$expectedBvid, current=$currentBvid"
            )
            return
        }
        if (!forceStop) prepareAudioNowPlayingForNavigationExit(expectedBvid)
        isLeavingByNavigation = true
        Logger.d(
            TAG,
            "🎯 markLeavingByNavigation: isLeavingByNavigation=true deferStop=$deferPlaybackStop"
        )
        
        //  [修复] 默认模式和画中画模式下，通过导航离开时应立即停止播放
        // 原因：ON_PAUSE 事件可能在此标志设置之前触发，导致音频继续播放
        // 画中画模式说明："切到桌面进入系统画中画"，返回主页时应停止
        // shared 返回 morph：deferPlaybackStop=true，等壳落位后再由 dispose 收尾。
        if (deferPlaybackStop && !forceStop) {
            Logger.d(TAG, "🎬 defer playback stop for shared return morph")
            return
        }
        val mode = getCurrentMode()
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        val keepForAudioNowPlaying = !forceStop &&
            shouldKeepPlaybackForAudioNowPlayingBar(
                sessionActive = AudioNowPlayingSession.active.value,
                barEnabled = SettingsManager.getAudioNowPlayingBarEnabledSync(context),
            )
        if (
            forceStop ||
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = mode,
                stopPlaybackOnExit = stopPlaybackOnExit,
                keepForAudioNowPlaying = keepForAudioNowPlaying,
            )
        ) {
            // Keep the paused resume entry when its switch is on. This does not keep the
            // player alive or override stop-on-exit; force-stop/replacement still clears it.
            if (forceStop || !keepForAudioNowPlaying) AudioNowPlayingSession.dismiss()
            isPlaying = false
            isMiniMode = false
            Logger.d(TAG, "🔇 ${mode.label}：通过导航离开，立即停止播放")
            // 停止所有播放器（外部和内部）
            _externalPlayer?.let { player ->
                player.volume = 0f
                player.playWhenReady = false
                player.pause()
            }
            _player?.let { player ->
                player.volume = 0f
                player.playWhenReady = false
                player.pause()
            }
            
            // 🔧 [修复] 标记非活跃状态，允许 VideoPlayerState.onDispose 正确释放资源
            // 解决音频泄漏问题：返回首页后音频仍继续播放
            resetBackgroundOptimizationState()
            isActive = false
            playbackServiceRequested = false
            _externalPlayer?.removeListener(playerListener)
            _externalPlayer = null
            releaseMediaSession()
            clearPlaybackNotificationArtifacts()
            Logger.d(TAG, "🔧 标记 isActive=false，清除外部播放器引用")
        }
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
            Logger.d(TAG) { "Creating new ExoPlayer instance" }
            
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            val upstreamFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
                .setDefaultRequestProperties(headers)
            val dataSourceFactory: DataSource.Factory =
                PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            val miniPlayerMode = SettingsManager.getMiniPlayerModeSync(context)
            val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
            val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)

            // 封面律动在公共音频入口按需读取开关。
            val renderersFactory = if (SettingsManager.getLoudnessNormalizationEnabledSync(context)) {
                com.android.purebilibili.core.player.LoudnessAwareRenderersFactory(context)
            } else {
                HiResCompatibleRenderersFactory(context)
            }.setExtensionRendererMode(
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            ).withAppVideoDecoderPreference(context)
            _player = ExoPlayer.Builder(context)
                .apply {
                    if (SettingsManager.getResilientVideoBufferSync(context)) {
                        setLoadControl(com.android.purebilibili.feature.video.state.createVideoLoadControl(context))
                    }
                }
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(
                    audioAttributes,
                    resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(
                    resolvePlaybackWakeMode(
                        miniPlayerMode = miniPlayerMode,
                        stopPlaybackOnExit = stopPlaybackOnExit
                    )
                )
                .build().withAppVideoDecoderDiagnostics(context)
                .apply {
                    addListener(playerListener)
                    //  [修复] 确保音量正常
                    volume = com.android.purebilibili.core.player.PlayerVolumeController
                        .preferredVolumeSync()
                    setPlaybackSpeed(SettingsManager.getPreferredPlaybackSpeedSync(context))
                    prepare()
                }
            
            // Every media entry targets the canonical UI host, never a launcher routing activity.
            val sessionIntent = resolveActivePlaybackLaunchIntent(context, currentBvid)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(context, QueueAwareSessionPlayer(_player!!))
                .setSessionActivity(pendingIntent)
                .setCallback(mediaSessionCallback)  //  支持系统媒体控件
                .build()
            mediaSessionNavigationAvailability = resolveSessionNavigationAvailability()
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
        Logger.d(TAG) { "startVideo: bvid=$bvid, title=$title" }
        
        ensurePlayer()
        
        // 如果是同一个视频，不重新加载
        if (currentBvid == bvid && _player?.isPlaying == true) {
            Logger.d(TAG) { "Same video already playing, skip reload" }
            return
        }

        currentBvid = bvid
        currentAudioMediaId = null
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
        val upstreamFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory: DataSource.Factory =
            PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)

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
        _player?.let {
            com.android.purebilibili.core.player.PlayerVolumeController.applyPreferredVolume(it)
        }
        _player?.prepare()
        _player?.playWhenReady = true
        requestForegroundServiceIfNeeded()

        // 更新媒体元数据
        updateMediaMetadata(title, owner, cover)
    }

    /**
     * Starts a standalone audio item on the manager-owned player. The player and
     * MediaSession outlive the screen that initiated playback.
     */
    fun startAudio(
        mediaId: String,
        title: String,
        cover: String,
        artist: String,
        audioUrl: String
    ): ExoPlayer {
        resetExternalPlayer()
        val audioPlayer = ensurePlayer()
        PlaylistManager.clearPlaylist()
        onNavigateNextCallback = null
        onNavigatePreviousCallback = null
        onHasNextNavigationCallback = null
        onHasPreviousNavigationCallback = null

        currentAudioMediaId = mediaId
        currentBvid = null
        currentCid = 0L
        currentAid = 0L
        currentTitle = title
        currentCover = cover
        currentOwner = artist
        isLiveMode = false
        currentRoomId = 0L
        currentLiveUname = ""
        isActive = true
        isMiniMode = false
        isLeavingByNavigation = false
        requestForegroundServiceIfNeeded()

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDisplayTitle(title)
            .setIsPlayable(true)
            .apply {
                if (cover.isNotBlank()) {
                    setArtworkUri(Uri.parse(FormatUtils.fixImageUrl(cover)))
                }
            }
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(audioUrl)
            .setMediaMetadata(metadata)
            .build()

        audioPlayer.setMediaItem(mediaItem)
        PlayerVolumeController.applyPreferredVolume(audioPlayer)
        audioPlayer.prepare()
        audioPlayer.playWhenReady = true
        audioPlayer.play()
        updateMediaSession(audioPlayer)
        updateMediaMetadata(title, artist, cover)
        return audioPlayer
    }

    /** 返回列表前交接当前直播播放器；不重新请求播放地址或创建播放器。 */
    fun prepareLiveMiniPlayerOnExit(roomId: Long, enabled: Boolean): Boolean {
        val currentPlayer = player ?: return false
        if (!com.android.purebilibili.feature.live.shouldAutoEnterLiveMiniPlayer(
                enabled = enabled,
                isActive = isActive,
                isMatchingLiveRoom = isLiveMode && roomId > 0L && currentRoomId == roomId,
                playWhenReady = currentPlayer.playWhenReady,
                playbackState = currentPlayer.playbackState,
                stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context),
            )
        ) return false
        // 直播独立偏好允许应用内接管，不依赖普通视频选择的小窗模式。
        enterMiniMode(forced = true)
        return isMiniMode
    }

    /**
     * 进入小窗模式。
     * @param forced 显式小窗入口或独立直播偏好允许时跳过普通视频模式检查。
     */
    fun enterMiniMode(forced: Boolean = false) {
        val mode = getCurrentMode()
        Logger.d(TAG) { "📲 enterMiniMode called: isActive=$isActive, forced=$forced, mode=$mode" }
        
        // 非强制模式下，只有支持应用内小窗的模式才自动进入小窗
        if (!forced && !mode.supportsInAppMiniPlayer) {
            Logger.d(TAG) { "⚠️ Auto mini player only works in in-app mini modes, current mode=$mode" }
            return
        }
        
        if (!isActive) {
            Logger.w(TAG, "⚠️ Cannot enter mini mode: isActive is false!")
            return
        }
        val currentPlayer = player
        val shouldResumePlayback = currentPlayer?.let {
            shouldResumePlaybackOnMiniPlayerEntry(
                isPlaying = it.isPlaying,
                playWhenReady = it.playWhenReady,
                playbackState = it.playbackState
            )
        } == true
        Logger.d(TAG) { "📲 Entering mini mode for video: $currentTitle (forced=$forced)" }
        isLeavingByNavigation = false
        if (!isMiniMode) miniPlayerSessionId++
        isMiniMode = true
        // Detail-session handoff may have muted the shared player; always restore for mini window.
        currentPlayer?.let(PlayerVolumeController::applyPreferredVolume)
        if (shouldResumePlayback && currentPlayer != null) {
            currentPlayer.playWhenReady = true
            currentPlayer.play()
            isPlaying = true
        }
        
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
        Logger.d(TAG) { "Exiting mini mode, animate=$animate" }
        shouldAnimateExit = animate
        isMiniMode = false
    }

    /**
     * 停止播放并关闭小窗
     */
    /** Stop listening and transfer system media controls to the incoming bangumi player. */
    fun beginBangumiPlayback(incomingPlayer: ExoPlayer) {
        AudioNowPlayingSession.dismiss()
        AudioNowPlayingSession.publishBarOverlayVisible(false)
        if (_externalPlayer === incomingPlayer) return
        _externalPlayer?.let { old ->
            old.pause()
            clearExternalPlayerIfMatches(old)
        }
        dismiss()
        currentTitle = ""
        currentCover = ""
        currentOwner = ""
        currentCid = 0L
        currentAid = 0L
        _externalPlayer = incomingPlayer
        incomingPlayer.addListener(playerListener)
        updateMediaSession(incomingPlayer)
    }

    fun endBangumiPlayback(expectedPlayer: ExoPlayer) {
        if (clearExternalPlayerIfMatches(expectedPlayer)) {
            PlayerBackgroundPlaybackSessions.clear(expectedPlayer)
            PlaylistManager.endPlaybackPreferenceSession()
            isPlaying = false
            releaseMediaSession()
            clearPlaybackNotificationArtifacts()
        }
    }

    fun updateBangumiPlaybackMetadata(expectedPlayer: ExoPlayer, title: String, coverUrl: String) {
        if (_externalPlayer !== expectedPlayer) return
        updateMediaMetadata(title = title, artist = "番剧", coverUrl = coverUrl)
    }

    fun dismiss() = dismiss(taskRemoved = false)

    internal fun dismissForTaskRemoval() {
        AudioNowPlayingSession.dismiss()
        AudioNowPlayingSession.publishBarOverlayVisible(false)
        dismiss(taskRemoved = true)
    }

    private fun dismiss(taskRemoved: Boolean) {
        PlayerBackgroundPlaybackSessions.clear(_externalPlayer)
        PlayerBackgroundPlaybackSessions.clear(_player)
        remoteBackgroundOverride = null
        pendingBackgroundOverride = null
        PlaylistManager.endPlaybackPreferenceSession()
        playbackStopGeneration++
        remoteTransferJob?.cancel()
        remotePreferencesJob?.cancel()
        if (taskRemoved) {
            // 独立音频服务由自己的任务移除回调清理快照，避免提前销毁中断清理。
            remoteAudio.disconnect()
        } else {
            remoteAudio.close()
        }
        resetBackgroundOptimizationState()
        Logger.d(TAG) { "Dismissing mini player (isLiveMode=$isLiveMode)" }
        
        //  [修复] 先停止所有播放器的声音
        _externalPlayer?.let { 
            it.pause()
            it.stop()
            Logger.d(TAG) { "🔇 Stopped external player" }
        }
        _player?.let {
            it.pause()
            it.stop()
            Logger.d(TAG, "🔇 Stopped internal player")
        }
        
        // A retained page still owns its player. A disposed page has transferred it to us.
        val playerToRelease = _externalPlayer
        if (playerToRelease != null && !pageOwnership.isRetained(playerToRelease)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (!pageOwnership.isRetained(playerToRelease) && !isPlayerManaged(playerToRelease)) {
                    playerToRelease.release()
                }
            }
        }
        
        isMiniMode = false
        isActive = false
        playbackServiceRequested = false
        lastForegroundStartAtMs = 0L
        isPlaying = false  //  [修复] 同步播放状态
        _externalPlayer = null
        currentBvid = null
        cachedUiState = null  //  [修复] 清除缓存的 UI 状态
        isLiveMode = false  // 📺 清除直播模式
        currentRoomId = 0L
        currentLiveUname = ""
        videoWidth = 0
        videoHeight = 0
        videoAspectRatio = 16f / 9f
        
        releaseMediaSession()
        clearPlaybackNotificationArtifacts(stopServiceDirectly = taskRemoved)
    }

    private fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
        mediaSessionNavigationAvailability = null
    }

    private fun clearPlaybackNotificationArtifacts(stopServiceDirectly: Boolean = false) {
        notificationThemeMetadata = null
        notificationThemeJob?.cancel()
        notificationThemeJob = null
        notificationThemeSettings = null
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        currentNotification = null
        cachedArtworkBitmap = null

        try {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_STOP_FOREGROUND
            }
            if (stopServiceDirectly) context.stopService(serviceIntent) else context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to stop playback service", e)
        }
    }



    private fun replaceExternalPlayer(incomingPlayer: ExoPlayer) {
        if (remoteAudio.active) {
            remotePreferencesJob?.cancel()
            remoteAudio.close()
        }
        if (player !== incomingPlayer) resetBackgroundOptimizationState()
        val previousPlayer = _externalPlayer
        if (previousPlayer != null && previousPlayer !== incomingPlayer) {
            previousPlayer.removeListener(playerListener)
            previousPlayer.pause()
            // Switching sessions must not invalidate a parent detail still on the back stack.
            // Once its page is disposed, the manager owns cleanup of the transferred player.
            if (!pageOwnership.isRetained(previousPlayer)) {
                previousPlayer.stop()
                previousPlayer.release()
            }
        }
        _externalPlayer = incomingPlayer
        incomingPlayer.removeListener(playerListener)
        incomingPlayer.addListener(playerListener)
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
        aid: Long = 0, // [新增] aid
        externalPlayer: ExoPlayer,
        fromLeft: Boolean = false  //  [新增] 入场方向
    ) {
        Logger.d(TAG, "setVideoInfo: bvid=$bvid, title=$title, cid=$cid, aid=$aid, fromLeft=$fromLeft")
        currentBvid = bvid
        currentAudioMediaId = null
        currentTitle = title
        currentCover = cover
        currentOwner = owner
        currentCid = cid  //  保存 cid
        currentAid = aid  //  保存 aid
        entryFromLeft = fromLeft  //  保存入场方向
        isLiveMode = false  // 📺 视频模式
        
        replaceExternalPlayer(externalPlayer)
        pendingBackgroundOverride?.takeIf { it.first == bvid }?.let { (_, value) ->
            PlayerBackgroundPlaybackSessions.forContent(externalPlayer, "video:$bvid").select(value)
        }
        pendingBackgroundOverride = null
        isActive = true
        isMiniMode = false
        // A visible detail reclaim ends the previous navigation handoff.
        isLeavingByNavigation = false

        // 🎯 [修复] 统一 MediaSession 管理：将外部播放器关联到全局 Session
        // 这样在 Activity 销毁后，后台服务仍能通过此 Session 控制播放
        updateMediaSession(externalPlayer)
        optimizeReplacementPlayerIfInBackground()
        requestForegroundServiceIfNeeded()
        
        // 同步播放状态
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = externalPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        duration = externalPlayer.duration.coerceAtLeast(0L)
        val vs = externalPlayer.videoSize
        if (vs.width > 0 && vs.height > 0) {
            videoWidth = vs.width
            videoHeight = vs.height
            videoAspectRatio = vs.width.toFloat() / vs.height.toFloat()
        }
    }
    
    /**
     * 📺 [新增] 设置直播信息并关联外部播放器（用于直播小窗模式）
     * 与 setVideoInfo 类似，但使用 roomId 标识直播间
     */
    fun setLiveInfo(
        roomId: Long,
        title: String,
        cover: String,
        uname: String,
        externalPlayer: ExoPlayer,
        fromLeft: Boolean = false
    ) {
        Logger.d(TAG, "📺 setLiveInfo: roomId=$roomId, title=$title, uname=$uname")
        val keepMiniMode = isMiniMode && isLiveMode && currentRoomId == roomId &&
            isPlayerManaged(externalPlayer)
        currentRoomId = roomId
        currentTitle = title
        currentCover = cover
        currentOwner = uname
        currentLiveUname = uname
        currentBvid = null  // 直播没有 bvid
        currentAudioMediaId = null
        currentCid = 0L
        currentAid = 0L
        isLiveMode = true
        entryFromLeft = fromLeft
        
        replaceExternalPlayer(externalPlayer)
        isActive = true
        isMiniMode = keepMiniMode

        updateMediaSession(externalPlayer)
        optimizeReplacementPlayerIfInBackground()
        requestForegroundServiceIfNeeded()
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = externalPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        duration = 0L  // 直播没有固定时长
        val vs = externalPlayer.videoSize
        if (vs.width > 0 && vs.height > 0) {
            videoWidth = vs.width
            videoHeight = vs.height
            videoAspectRatio = vs.width.toFloat() / vs.height.toFloat()
        }

        // 📺 直播也需要推送媒体元数据与前台通知，避免后台被系统快速回收。
        updateMediaMetadata(
            title = title.ifBlank { "直播中" },
            artist = uname.ifBlank { "直播" },
            coverUrl = cover
        )
    }
    
    /**
     * 🎯 [新增] 更新 MediaSession 关联的播放器
     * 允许在内部播放器和外部播放器（Activity 提供）之间平滑切换
     */
    private fun updateMediaSession(newPlayer: Player) {
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, newPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            Logger.d(
                TAG,
                "🎯 Updating MediaSession player: ${mediaSession?.player.hashCode()} -> ${newPlayer.hashCode()}, navigation=$navigationAvailability"
            )
            
            // 如果已经存在 session，先释放旧的
            mediaSession?.release()
            
            // 构建新的 Session
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context, 0,
                resolveActivePlaybackLaunchIntent(context, currentBvid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val sessionId = "bilipai_shared_session" // 使用固定 ID 保持一致性
            mediaSession = MediaSession.Builder(context, QueueAwareSessionPlayer(newPlayer))
                .setId(sessionId)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(mediaSessionCallback)
                .build()
            mediaSessionNavigationAvailability = navigationAvailability
            
            Logger.d(TAG, "✅ MediaSession updated and bound to new player")
        }
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
    fun togglePlayPause(): Boolean {
        if (!remoteAudio.active && player == null) return false
        performMediaControl(MediaControlType.PLAY_PAUSE)
        return true
    }

    /** Explicit pause also clears playback intent while the player is buffering. */
    fun pausePlayback(): Boolean {
        if (!remoteAudio.active && player == null) return false
        performMediaControl(MediaControlType.PAUSE)
        return true
    }

    internal fun performMediaControl(controlType: MediaControlType) {
        if (remoteAudio.active) {
            remoteAudio.controller?.let { remote ->
                when (controlType) {
                    MediaControlType.PLAY -> remote.play()
                    MediaControlType.PAUSE -> remote.pause()
                    MediaControlType.PLAY_PAUSE -> if (remote.playWhenReady) remote.pause() else remote.play()
                    MediaControlType.NEXT -> remote.seekToNextMediaItem()
                    MediaControlType.PREVIOUS -> remote.seekToPreviousMediaItem()
                }
            }
            return
        }
        when (controlType) {
            MediaControlType.PREVIOUS -> playPrevious()
            MediaControlType.PLAY,
            MediaControlType.PAUSE,
            MediaControlType.PLAY_PAUSE -> player?.let { currentPlayer ->
                val previousIsPlaying = currentPlayer.isPlaying
                val willPlay = when (controlType) {
                    MediaControlType.PLAY -> true
                    MediaControlType.PAUSE -> false
                    MediaControlType.PLAY_PAUSE -> !previousIsPlaying
                    else -> false
                }
                if (willPlay) {
                    // Shared detail player may still be at volume 0 after session-inactive mute.
                    PlayerVolumeController.applyPreferredVolume(currentPlayer)
                }
                if (applyPlaybackMediaControlToPlayer(currentPlayer, controlType)) {
                    isPlaying = resolvePlayingStateAfterMediaControl(
                        controlType = controlType,
                        playerIsPlaying = previousIsPlaying
                    )
                    if (shouldRefreshNotificationOnPlaybackStateChange(isActive = isActive, title = currentTitle)) {
                        pushNotification(currentTitle, currentOwner, bitmap = null)
                    }
                }
            }
            MediaControlType.NEXT -> playNext()
        }
    }

    private fun hasDirectBackgroundPlaybackContext(): Boolean {
        return player != null
    }

    private fun resolveSessionNavigationAvailability(): QueueNavigationAvailability {
        val playlist = PlaylistManager.playlist.value
        if (!shouldUseStandardQueueNavigation(playlist, isLiveMode)) {
            return QueueNavigationAvailability(hasNext = false, hasPrevious = false)
        }
        val hasNext = onHasNextNavigationCallback?.invoke()
            ?: (shouldEnableQueueNavigation(playlist.size) && PlaylistManager.hasNext())
        val hasPrevious = onHasPreviousNavigationCallback?.invoke()
            ?: (shouldEnableQueueNavigation(playlist.size) && PlaylistManager.hasPrevious())
        return QueueNavigationAvailability(
            hasNext = hasNext,
            hasPrevious = hasPrevious
        )
    }

    private fun isStandardQueueNavigationEnabled(): Boolean {
        return resolveSessionNavigationAvailability().isEnabled
    }

    private fun restorePlaylistIndexIfNeeded(index: Int) {
        if (index >= 0) {
            PlaylistManager.playAt(index)
        }
    }

    private fun playVideoDirectlyFromPlaylistItem(
        item: PlaylistItem,
        rollbackIndex: Int
    ): Boolean {
        val currentPlayer = player ?: return false
        backgroundSkipJob?.cancel()
        backgroundSkipJob = scope.launch {
            backgroundPlaybackUseCase.attachPlayer(currentPlayer)
            val isLoggedIn = VideoRepository.isPlaybackLoggedIn()
            val storedQuality = NetworkUtils.getDefaultQualityId(context)
            val autoHighestEnabled = SettingsManager.getAutoHighestQualitySync(context)
            val effectiveVip = VideoRepository.refreshVipStatusForPreferredQualityIfNeeded(
                isLoggedIn = isLoggedIn,
                cachedIsVip = VideoRepository.isPlaybackVip(),
                storedQuality = storedQuality,
                autoHighestEnabled = autoHighestEnabled
            )
            val defaultQuality = com.android.purebilibili.core.util.resolvePlaybackDefaultQualityId(
                storedQuality = storedQuality,
                autoHighestEnabled = autoHighestEnabled,
                isLoggedIn = isLoggedIn,
                isVip = effectiveVip
            )
            val audioQualityPreference = PlayerSettingsStore.getCachedLastSelectedAudioQuality(context)
            val videoCodecPreference = SettingsManager.getVideoCodecSync(context)
            val videoSecondCodecPreference = SettingsManager.getVideoSecondCodecSync(context)

            when (
                val loadResult = backgroundPlaybackUseCase.loadVideo(
                    bvid = item.bvid,
                    defaultQuality = defaultQuality,
                    audioQualityPreference = audioQualityPreference,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    playWhenReady = true,
                    isHdrSupportedOverride = MediaUtils.isHdrSupported(context),
                    isDolbyVisionSupportedOverride = MediaUtils.isDolbyVisionSupported(context)
                )
            ) {
                is VideoLoadResult.Success -> {
                    if (loadResult.audioUrl != null) {
                        backgroundPlaybackUseCase.playDashVideo(
                            videoUrl = loadResult.playUrl,
                            audioUrl = loadResult.audioUrl,
                            seekTo = 0L,
                            playWhenReady = true
                        )
                    } else {
                        backgroundPlaybackUseCase.playVideo(
                            url = loadResult.playUrl,
                            seekTo = 0L,
                            playWhenReady = true
                        )
                    }
                    currentBvid = loadResult.info.bvid
                    currentCid = loadResult.info.cid
                    currentAid = loadResult.info.aid
                    currentTitle = loadResult.info.title.ifBlank { item.title }
                    currentOwner = loadResult.info.owner.name.ifBlank { item.owner }
                    currentCover = resolveEffectiveNotificationCoverUrl(
                        incomingCoverUrl = loadResult.info.pic,
                        cachedCoverUrl = item.cover
                    )
                    cachedUiState = null
                    isActive = true
                    isLiveMode = false
                    currentRoomId = 0L
                    currentLiveUname = ""
                    duration = loadResult.duration.coerceAtLeast(0L)
                    isPlaying = true
                    updateMediaMetadata(currentTitle, currentOwner, currentCover)
                    Logger.d(TAG, "🎵 direct background skip: ${currentTitle}")
                }
                is VideoLoadResult.Error -> {
                    restorePlaylistIndexIfNeeded(rollbackIndex)
                    Logger.w(TAG, "⚠️ direct background skip failed: ${item.bvid}, error=${loadResult.error}")
                }
            }
        }
        return true
    }

    /**
     * Seek 到指定位置
     */
    fun seekTo(position: Long) {
        if (remoteAudio.active) remoteAudio.controller?.seekTo(position) else player?.seekTo(position)
    }
    
    // ==========  [新增] 播放列表控制 ==========
    
    /**
     *  播放下一曲
     */
    fun playNext(): Boolean {
        if (remoteAudio.active) {
            remoteAudio.controller?.seekToNextMediaItem()
            return true
        }
        onNavigateNextCallback?.let { navigate ->
            if (navigate()) {
                Logger.d(TAG, "🎵 navigation callback handled next request")
                return true
            }
        }
        val previousIndex = PlaylistManager.currentIndex.value
        val nextItem = PlaylistManager.playNext()
        if (nextItem?.isBangumi == true) {
            return when (
                resolvePlaylistSkipExecutionMode(
                    item = nextItem,
                    callbackAvailable = onPlayNextBangumiCallback != null,
                    hasDirectPlaybackContext = false
                )
            ) {
                PlaylistSkipExecutionMode.CALLBACK -> {
                    val handled = dispatchBangumiNavigation(nextItem, onPlayNextBangumiCallback)
                    if (!handled) {
                        restorePlaylistIndexIfNeeded(previousIndex)
                        Logger.w(TAG, "⚠️ playNext bangumi ignored: callback/context missing")
                    }
                    handled
                }
                PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
                PlaylistSkipExecutionMode.NONE -> {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playNext bangumi ignored: callback/context missing")
                    false
                }
            }
        }
        return when (
            resolvePlaylistSkipExecutionMode(
                item = nextItem,
                callbackAvailable = false,
                hasDirectPlaybackContext = hasDirectBackgroundPlaybackContext()
            )
        ) {
            PlaylistSkipExecutionMode.CALLBACK -> false
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND -> {
                val handled = nextItem?.let { playVideoDirectlyFromPlaylistItem(it, previousIndex) } ?: false
                if (handled) {
                    Logger.d(TAG, "🎵 直切下一曲: ${nextItem.title}")
                } else {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playNext ignored: direct playback context missing")
                }
                handled
            }
            PlaylistSkipExecutionMode.NONE -> {
                restorePlaylistIndexIfNeeded(previousIndex)
                Logger.w(TAG, "⚠️ playNext ignored: no callback bound")
                false
            }
        }
    }
    
    /**
     *  播放上一曲
     */
    fun playPrevious(): Boolean {
        if (remoteAudio.active) {
            remoteAudio.controller?.seekToPreviousMediaItem()
            return true
        }
        onNavigatePreviousCallback?.let { navigate ->
            if (navigate()) {
                Logger.d(TAG, "🎵 navigation callback handled previous request")
                return true
            }
        }
        val previousIndex = PlaylistManager.currentIndex.value
        val prevItem = PlaylistManager.playPrevious()
        if (prevItem?.isBangumi == true) {
            return when (
                resolvePlaylistSkipExecutionMode(
                    item = prevItem,
                    callbackAvailable = onPlayPreviousBangumiCallback != null,
                    hasDirectPlaybackContext = false
                )
            ) {
                PlaylistSkipExecutionMode.CALLBACK -> {
                    val handled = dispatchBangumiNavigation(prevItem, onPlayPreviousBangumiCallback)
                    if (!handled) {
                        restorePlaylistIndexIfNeeded(previousIndex)
                        Logger.w(TAG, "⚠️ playPrevious bangumi ignored: callback/context missing")
                    }
                    handled
                }
                PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
                PlaylistSkipExecutionMode.NONE -> {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playPrevious bangumi ignored: callback/context missing")
                    false
                }
            }
        }
        return when (
            resolvePlaylistSkipExecutionMode(
                item = prevItem,
                callbackAvailable = false,
                hasDirectPlaybackContext = hasDirectBackgroundPlaybackContext()
            )
        ) {
            PlaylistSkipExecutionMode.CALLBACK -> false
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND -> {
                val handled = prevItem?.let { playVideoDirectlyFromPlaylistItem(it, previousIndex) } ?: false
                if (handled) {
                    Logger.d(TAG, "🎵 直切上一曲: ${prevItem.title}")
                } else {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playPrevious ignored: direct playback context missing")
                }
                handled
            }
            PlaylistSkipExecutionMode.NONE -> {
                restorePlaylistIndexIfNeeded(previousIndex)
                Logger.w(TAG, "⚠️ playPrevious ignored: no callback bound")
                false
            }
        }
    }

    /**
     *  切换播放模式
     */
    fun togglePlayMode(): PlayMode = PlaylistManager.togglePlayMode()

    /**
     *  获取当前播放模式
     */
    fun getPlayMode(): PlayMode = PlaylistManager.playMode.value
    
    // 回调函数（由 VideoPlaybackViewModel 设置）
    var onNavigateNextCallback: (() -> Boolean)? = null
    var onNavigatePreviousCallback: (() -> Boolean)? = null
    var onHasNextNavigationCallback: (() -> Boolean)? = null
    var onHasPreviousNavigationCallback: (() -> Boolean)? = null
    var onPlayNextBangumiCallback: ((PlaylistItem) -> Unit)? = null
    var onPlayPreviousBangumiCallback: ((PlaylistItem) -> Unit)? = null


    /**
     * 释放所有资源
     */
    fun release() {
        Logger.d(TAG, "Releasing all resources")
        dismiss()
        if (mediaControlReceiverRegistered) {
            runCatching { context.unregisterReceiver(mediaControlReceiver) }
                .onFailure { Logger.w(TAG, "Failed to unregister media control receiver: ${it.message}") }
            mediaControlReceiverRegistered = false
        }
        if (backgroundListenerRegistered) {
            com.android.purebilibili.core.lifecycle.BackgroundManager.removeListener(this)
            backgroundListenerRegistered = false
        }
        _player?.removeListener(playerListener)
        _player?.release()
        _player = null
        // 音频输出入口释放时按会话所有权清理律动，不清空其他仍在播放的会话。
        scope.cancel()
        INSTANCE = null
    }

    // --- 播放器监听器 ---
    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (remoteAudio.active) return
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                onBackgroundPlayWhenReadyChanged(playWhenReady)
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (remoteAudio.active) return
            isPlaying = playing
            Logger.d(TAG, "isPlaying changed: $playing")
            if (!shouldRefreshNotificationOnPlaybackStateChange(isActive = isActive, title = currentTitle)) {
                return
            }

            val titleSnapshot = currentTitle
            val ownerSnapshot = currentOwner
            val coverSnapshot = currentCover
            val artworkMissing = cachedArtworkBitmap == null && coverSnapshot.isNotBlank()

            if (artworkMissing) {
                scope.launch(Dispatchers.IO) {
                    val bitmap = loadBitmap(coverSnapshot)
                    launch(Dispatchers.Main) {
                        pushNotification(titleSnapshot, ownerSnapshot, bitmap)
                    }
                }
            } else {
                pushNotification(titleSnapshot, ownerSnapshot, bitmap = null)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    duration = player?.duration ?: 0L
                    Logger.d(TAG, "Player ready, duration=$duration")
                }
                Player.STATE_ENDED -> {
                    Logger.d(TAG, "Playback ended")
                    playbackServiceRequested = false
                    clearPlaybackNotificationArtifacts()
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                Logger.d(TAG, "📐 onVideoSizeChanged: ${videoSize.width}x${videoSize.height}, aspect=$videoAspectRatio")
            }
        }
    }

    /**
     * 更新媒体元数据和通知
     */
    fun updateMediaMetadata(title: String, artist: String, coverUrl: String) {
        val currentPlayer = player ?: return
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, currentPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            updateMediaSession(currentPlayer)
        }
        val previousCoverUrl = currentCover
        val effectiveCoverUrl = resolveEffectiveNotificationCoverUrl(
            incomingCoverUrl = coverUrl,
            cachedCoverUrl = currentCover
        )
        currentTitle = title
        currentOwner = artist
        if (effectiveCoverUrl.isNotBlank()) {
            currentCover = effectiveCoverUrl
        }
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = currentPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        val currentItem = currentPlayer.currentMediaItem ?: return

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDisplayTitle(title)
            .setIsPlayable(true)
        if (effectiveCoverUrl.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(FormatUtils.fixImageUrl(effectiveCoverUrl)))
        }
        val metadata = metadataBuilder.build()

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newItem)

        // 异步加载封面并推送通知
        val shouldReloadArtwork = effectiveCoverUrl.isNotBlank() &&
            (cachedArtworkBitmap == null || previousCoverUrl != effectiveCoverUrl)
        notificationMetadataJob?.cancel()
        notificationMetadataJob = scope.launch(Dispatchers.IO) {
            val bitmap = if (shouldReloadArtwork) {
                loadBitmap(effectiveCoverUrl)
            } else {
                null
            }
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
            ((result as? SuccessResult)?.image as? coil3.BitmapImage)?.bitmap
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun pushNotification(title: String, artist: String, bitmap: Bitmap?) {
        if (remoteAudio.active) return
        val notificationIsPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = player?.isPlaying,
            cachedIsPlaying = isPlaying
        )
        isPlaying = notificationIsPlaying
        if (!shouldKeepPlaybackNotificationVisible(
                isActive = isActive,
                title = title,
                isPlaying = notificationIsPlaying,
                appInBackground = BackgroundManager.isInBackground,
                // Cold-start artwork can arrive while the first segments are still buffering.
                isStartingPlayback = player?.let {
                    isPlayerStartingPlayback(it.playWhenReady, it.playbackState)
                } == true
            )
        ) {
            playbackServiceRequested = false
            clearPlaybackNotificationArtifacts()
            return
        }
        val effectiveArtworkBitmap = resolveEffectiveNotificationArtwork(
            incomingArtwork = bitmap,
            cachedArtwork = cachedArtworkBitmap
        )
        if (effectiveArtworkBitmap != null) {
            cachedArtworkBitmap = effectiveArtworkBitmap
        }

        // Observe only while a notification is requested. Release/clear cancels this
        // owner; do not launch settings work in init or block the main thread to read it.
        notificationThemeMetadata = title to artist
        if (notificationThemeJob?.isActive != true) {
            notificationThemeJob = scope.launch {
                SettingsManager.getAppThemeSettings(context).collect { settings ->
                    notificationThemeSettings = settings
                    notificationThemeMetadata?.let { (latestTitle, latestArtist) ->
                        pushNotification(latestTitle, latestArtist, bitmap = null)
                    }
                }
            }
        }
        if (notificationThemeSettings == null) return
        requestForegroundServiceIfNeeded()
        onNotificationRefreshRequested?.invoke()
    }

    /** 只补充品牌外观和当前内容；播放状态、按钮和通知发布由媒体服务负责。 */
    internal fun decoratePlaybackNotification(notification: android.app.Notification): android.app.Notification {
        val builder = android.app.Notification.Builder.recoverBuilder(context, notification)
            .setSmallIcon(resolveNotificationIconRes(SettingsManager.getAppIconSync(context)))
            .setContentTitle(currentTitle)
            .setContentText(currentOwner)
            .setContentIntent(PendingIntent.getActivity(
                context, 0, resolveActivePlaybackLaunchIntent(context, currentBvid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        cachedArtworkBitmap?.let { builder.setLargeIcon(it) }
        notificationThemeSettings?.let { settings ->
            builder.setColor(resolvePlaybackNotificationColor(context, settings)).setColorized(true)
        }
        return builder.build().also { currentNotification = it }
    }

    private fun requestForegroundServiceIfNeeded() {
        if (!isActive) return
        val now = SystemClock.elapsedRealtime()
        if (playbackServiceRequested && now - lastForegroundStartAtMs < FOREGROUND_START_DEBOUNCE_MS) {
            return
        }

        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_FOREGROUND
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            playbackServiceRequested = true
            lastForegroundStartAtMs = now
        } catch (e: Exception) {
            playbackServiceRequested = false
            Logger.e(TAG, "Failed to request foreground playback service", e)
        }
    }
}
