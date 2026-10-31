@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.overlay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.audiofx.LoudnessEnhancer
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog
import com.android.purebilibili.core.ui.components.AppOutlinedTextField as OutlinedTextField
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.player.PiliPlayerMenuStore
import com.android.purebilibili.feature.video.ui.pager.buildPortraitCachedMediaSourceFactory
import com.android.purebilibili.feature.web.WebViewScreen
import kotlinx.coroutines.launch

/** Actions must belong to the displayed media, never to an unrelated detail ViewModel. */
data class PiliPlayerMenuActions(
    val onWatchLater: (() -> Unit)? = null,
    val onShowNotes: (() -> Unit)? = null,
    val onDownloadVideo: (() -> Unit)? = null,
    val onReplacePlaybackSources: ((String, Long, String, String) -> Unit)? = null,
    val videoUrlCandidates: List<String> = emptyList(),
    val audioUrlCandidates: List<String> = emptyList(),
)

/** PiliPlus lists decoder formats at the first quality, not codecs from unrelated qualities. */
internal fun resolvePiliAvailableCodecIds(
    videos: List<com.android.purebilibili.data.model.response.DashVideo>,
): List<String> {
    val quality = videos.firstOrNull()?.id ?: return emptyList()
    return buildList {
        for (video in videos) if (video.id == quality) {
            val codec = video.codecs
            if (codec.isNotBlank() && codec !in this) add(codec)
        }
    }
}

internal fun resolvePiliSelectedVideoCodec(tracks: Tracks): String? {
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            val format = group.getTrackFormat(index)
            return format.codecs?.takeIf { it.isNotBlank() } ?: when (format.sampleMimeType) {
                MimeTypes.VIDEO_H264 -> "avc1"
                MimeTypes.VIDEO_H265 -> "hev1"
                MimeTypes.VIDEO_AV1 -> "av01"
                MimeTypes.VIDEO_DOLBY_VISION -> "dvh1"
                else -> null
            }
        }
    }
    return null
}

data class PiliPlayerMenuSession(
    val onEditPlayUrl: (() -> Unit)?,
    val onReport: (() -> Unit)?,
    val playerVolume: Float,
    val onPlayerVolumeChange: ((Float) -> Unit)?,
    val playerVolumeUnavailableReason: String?,
    val isBackgroundPlaybackEnabled: Boolean,
    val onBackgroundPlaybackChange: (Boolean) -> Unit,
    val onWatchLater: (() -> Unit)?,
    val onShowNotes: (() -> Unit)?,
    val onDownloadVideo: (() -> Unit)?,
    val sleepTimerMinutes: Int?,
    val onSleepTimerChange: (Int?) -> Unit,
    val sleepWaitForVideoEnd: Boolean,
    val onSleepWaitForVideoEndChange: (Boolean) -> Unit,
    val sleepExitApp: Boolean,
    val onSleepExitAppChange: (Boolean) -> Unit,
    val cdnServiceLabels: List<String>,
    val currentCdnServiceIndex: Int,
    val onCdnServiceSelected: ((Int) -> Unit)?,
    val playingVideoCodec: String?,
)

/** Ignore queued callbacks from an effect that has been replaced or released. */
internal fun resolveAudioEffectControlAvailability(
    currentEffect: Any?,
    callbackEffect: Any,
    granted: Boolean,
    readEnabled: () -> Boolean,
): Boolean? {
    if (currentEffect !== callbackEffect) return null
    if (!granted) return false
    return try {
        readEnabled()
    } catch (_: RuntimeException) {
        // The native effect can be released between the identity check and the read.
        false
    }
}

@Composable
fun rememberPiliPlayerMenuSession(
    player: ExoPlayer?,
    bvid: String,
    aid: Long,
    cid: Long,
    title: String,
    videoUrl: String,
    audioUrl: String,
    actions: PiliPlayerMenuActions = PiliPlayerMenuActions(),
): PiliPlayerMenuSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val amplification by remember(context) { PiliPlayerMenuStore.amplification(context) }
        .collectAsStateWithLifecycle(initialValue = 1f)
    val savedBackgroundEnabled by remember(context) { SettingsManager.getBackgroundPlaybackEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getBackgroundPlaybackEnabledSync(context))
    val backgroundManager = remember(context) { com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstance(context) }
    val backgroundKey = if (player != null && backgroundManager.isPlayerManaged(player) && backgroundManager.isLiveMode) {
        "live:${backgroundManager.currentRoomId}"
    } else "video:$bvid"
    val backgroundSession = com.android.purebilibili.feature.video.player.rememberPlayerBackgroundPlaybackSession(player, backgroundKey)
    val backgroundOverride by backgroundSession.selection.collectAsStateWithLifecycle()
    val backgroundEnabled = backgroundOverride ?: savedBackgroundEnabled
    val waitForEnd by remember(context) { PiliPlayerMenuStore.sleepWaitForEnd(context) }
        .collectAsStateWithLifecycle<Boolean?>(initialValue = null)
    val exitApp by remember(context) { PiliPlayerMenuStore.sleepExitApp(context) }
        .collectAsStateWithLifecycle<Boolean?>(initialValue = null)
    val currentIdentity by rememberUpdatedState(bvid to cid)
    var volumeAvailable by remember(player) { mutableStateOf(false) }
    var volumeFailure by remember(player) { mutableStateOf<String?>("音频会话尚未就绪") }
    var gainEffect by remember(player) { mutableStateOf<LoudnessEnhancer?>(null) }
    var playingVideoCodec by remember(player) {
        mutableStateOf(player?.currentTracks?.let(::resolvePiliSelectedVideoCodec))
    }
    val currentAmplification by rememberUpdatedState(amplification)
    val storedCdnService by remember(context) { PiliPlayerMenuStore.cdnService(context) }
        .collectAsStateWithLifecycle(initialValue = "BASE")
    val cdnService = remember(storedCdnService) {
        PiliCdnService.entries.firstOrNull { it.name == storedCdnService } ?: PiliCdnService.BASE
    }
    val cdnLabels = remember { PiliCdnService.entries.map { it.label } }
    val sourceKey = remember(videoUrl) { runCatching { java.net.URI(videoUrl).rawPath }.getOrNull().orEmpty() }
    val audioSourceKey = remember(audioUrl) { runCatching { java.net.URI(audioUrl).rawPath }.getOrNull().orEmpty() }
    val originalVideos = remember(player, bvid, cid, sourceKey) {
        actions.videoUrlCandidates.ifEmpty { listOf(videoUrl) }.filter { it.isNotBlank() }
    }
    val originalAudios = remember(player, bvid, cid, audioSourceKey) {
        actions.audioUrlCandidates.ifEmpty { listOf(audioUrl) }.filter { it.isNotBlank() }
    }
    fun applyCdn(service: PiliCdnService) {
        if (originalVideos.isEmpty() || currentIdentity.first != bvid || currentIdentity.second != cid) return
        val video = selectPiliCdnUrl(originalVideos, service)
        val audio = if (originalAudios.isEmpty()) "" else selectPiliCdnUrl(originalAudios, service)
        if (video == videoUrl && audio == audioUrl) return
        val replacement = actions.onReplacePlaybackSources
        if (replacement != null) replacement(bvid, cid, video, audio)
        else if (player != null) replacePiliPlayerSources(context, player, video, audio)
    }
    LaunchedEffect(player, bvid, cid, sourceKey, audioSourceKey, cdnService) {
        if (cdnService != PiliCdnService.BASE) {
            runCatching { applyCdn(cdnService) }.onFailure {
                Toast.makeText(context, it.message ?: "CDN 地址替换失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(player) {
        fun releaseGainEffect() {
            val oldEffect = gainEffect
            gainEffect = null
            if (oldEffect != null) {
                runCatching { oldEffect.setControlStatusListener(null) }
                runCatching { oldEffect.release() }
            }
        }
        fun bind(sessionId: Int) {
            releaseGainEffect()
            volumeAvailable = false
            volumeFailure = "音频会话尚未就绪"
            if (sessionId <= 0) return
            runCatching {
                LoudnessEnhancer(sessionId).also { effect ->
                    try {
                        effect.setTargetGain(playerAmplificationMillibels(currentAmplification))
                        effect.enabled = true
                        check(effect.enabled && effect.hasControl()) { "音频增强效果不可用" }
                        gainEffect = effect
                        effect.setControlStatusListener { _, granted ->
                            val available = resolveAudioEffectControlAvailability(
                                currentEffect = gainEffect,
                                callbackEffect = effect,
                                granted = granted,
                                readEnabled = { effect.enabled },
                            ) ?: return@setControlStatusListener
                            if (gainEffect !== effect) return@setControlStatusListener
                            volumeAvailable = available
                            volumeFailure = if (volumeAvailable) null else "播放器音频增强控制权已被其他音效占用"
                        }
                    } catch (error: RuntimeException) {
                        if (gainEffect === effect) gainEffect = null
                        runCatching { effect.setControlStatusListener(null) }
                        runCatching { effect.release() }
                        throw error
                    }
                }
            }.onSuccess {
                volumeAvailable = runCatching { it.enabled && it.hasControl() }.getOrDefault(false)
                volumeFailure = if (volumeAvailable) null else "播放器音频增强控制权已被其他音效占用"
            }.onFailure { volumeFailure = "此设备的音频会话不支持播放器音量增强" }
        }
        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = bind(audioSessionId)
            override fun onTracksChanged(tracks: Tracks) {
                playingVideoCodec = resolvePiliSelectedVideoCodec(tracks)
            }
        }
        player?.addListener(listener)
        bind(player?.audioSessionId ?: 0)
        onDispose {
            player?.removeListener(listener)
            releaseGainEffect()
        }
    }
    LaunchedEffect(gainEffect, amplification) {
        gainEffect?.let { effect ->
            runCatching { effect.setTargetGain(playerAmplificationMillibels(amplification)) }
                .onFailure {
                    volumeAvailable = false
                    volumeFailure = "音频增强已不可用，请重新准备播放"
                }
        }
    }

    var editUrls by remember(player, bvid, cid) { mutableStateOf(false) }
    var editedVideo by remember(player, bvid, cid, videoUrl) { mutableStateOf(videoUrl) }
    var editedAudio by remember(player, bvid, cid, audioUrl) { mutableStateOf(audioUrl) }
    var showReport by remember(bvid, aid) { mutableStateOf(false) }
    val sleepTimer = remember(player, context) { player?.let { PiliPlayerSleepTimer.forPlayer(it, context) } }
    val timerMinutes by remember(sleepTimer) {
        sleepTimer?.minutes ?: kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    SideEffect {
        sleepTimer?.attachActivity(context.findPiliActivity())
        waitForEnd?.let { sleepTimer?.waitForEnd = it }
        exitApp?.let { sleepTimer?.exitApp = it }
    }

    if (editUrls) {
        val identity = bvid to cid
        AlertDialog(
            onDismissRequest = { editUrls = false },
            title = { Text("编辑播放地址") },
            text = {
                Column {
                    Text(title)
                    OutlinedTextField(editedVideo, { editedVideo = it }, label = { Text("VIDEO URL") })
                    OutlinedTextField(editedAudio, { editedAudio = it }, label = { Text("AUDIO URL（可留空）") })
                }
            },
            confirmButton = {
                TextButton(enabled = isEditablePlaybackUrl(editedVideo) && isEditablePlaybackUrl(editedAudio, true), onClick = {
                    if (identity != currentIdentity) return@TextButton
                    runCatching {
                        val replacement = actions.onReplacePlaybackSources
                        if (replacement != null) replacement(identity.first, identity.second, editedVideo.trim(), editedAudio.trim())
                        else if (player != null) replacePiliPlayerSources(context, player, editedVideo.trim(), editedAudio.trim())
                    }.onSuccess { editUrls = false }.onFailure {
                        Toast.makeText(context, it.message ?: "播放地址替换失败", Toast.LENGTH_LONG).show()
                    }
                }) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { editUrls = false }) { Text("取消") } },
        )
    }
    if (showReport) {
        Dialog(onDismissRequest = { showReport = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                WebViewScreen(url = "https://www.bilibili.com/appeal/?avid=$aid", title = "举报视频", onBack = { showReport = false })
            }
        }
    }
    return PiliPlayerMenuSession(
        onEditPlayUrl = if (player != null || actions.onReplacePlaybackSources != null) ({ editUrls = true }) else null,
        onReport = if (aid > 0) ({ showReport = true }) else null,
        playerVolume = if (volumeAvailable) amplification else 1f,
        onPlayerVolumeChange = if (volumeAvailable) ({ value ->
            scope.launch { PiliPlayerMenuStore.setAmplification(context, normalizePlayerAmplification(value)) }
        }) else null,
        playerVolumeUnavailableReason = volumeFailure,
        isBackgroundPlaybackEnabled = backgroundEnabled,
        onBackgroundPlaybackChange = { value ->
            backgroundSession.select(value)
            scope.launch { backgroundSession.saveIfRemembered(context, value) }
        },
        onWatchLater = actions.onWatchLater,
        onShowNotes = actions.onShowNotes,
        onDownloadVideo = actions.onDownloadVideo,
        sleepTimerMinutes = timerMinutes,
        onSleepTimerChange = { minutes ->
            if (sleepTimer != null) sleepTimer.setTimer(minutes)
            else Toast.makeText(context, "尚无活动播放器", Toast.LENGTH_SHORT).show()
        },
        sleepWaitForVideoEnd = waitForEnd ?: sleepTimer?.waitForEnd ?: false,
        onSleepWaitForVideoEndChange = { value -> scope.launch { PiliPlayerMenuStore.setSleepWaitForEnd(context, value) } },
        sleepExitApp = exitApp ?: sleepTimer?.exitApp ?: false,
        onSleepExitAppChange = { value -> scope.launch { PiliPlayerMenuStore.setSleepExitApp(context, value) } },
        cdnServiceLabels = cdnLabels,
        currentCdnServiceIndex = cdnService.ordinal,
        onCdnServiceSelected = if (player != null || actions.onReplacePlaybackSources != null) ({ index ->
            PiliCdnService.entries.getOrNull(index)?.let { selected ->
                runCatching { applyCdn(selected) }.onSuccess {
                    scope.launch { PiliPlayerMenuStore.setCdnService(context, selected.name) }
                }.onFailure {
                    Toast.makeText(context, it.message ?: "CDN 地址替换失败", Toast.LENGTH_LONG).show()
                }
            }
        }) else null,
        playingVideoCodec = playingVideoCodec,
    )
}

/** Replaces only this ExoPlayer, retaining its metadata, playhead, speed and playback intent. */
fun replacePiliPlayerSources(context: Context, player: ExoPlayer, videoUrl: String, audioUrl: String) {
    require(isEditablePlaybackUrl(videoUrl) && isEditablePlaybackUrl(audioUrl, true)) { "请输入有效的 HTTP(S) 播放地址" }
    val factory = buildPortraitCachedMediaSourceFactory(context)
    val oldItem = player.currentMediaItem
    val videoItem = (oldItem?.buildUpon() ?: MediaItem.Builder())
        .setUri(videoUrl)
        .setMimeType(null)
        .setCustomCacheKey(null)
        .build()
    val videoSource = factory.createMediaSource(videoItem)
    val source = if (audioUrl.isBlank()) videoSource else MergingMediaSource(
        videoSource,
        factory.createMediaSource(MediaItem.Builder().setUri(audioUrl).setMediaId("audio_${videoItem.mediaId}").build()),
    )
    val position = player.currentPosition.coerceAtLeast(0L)
    val intent = player.playWhenReady
    player.setMediaSource(source, position)
    player.prepare()
    player.playWhenReady = intent
}

private tailrec fun Context.findPiliActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPiliActivity()
    else -> null
}
