package com.android.purebilibili.feature.nearby

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.video.handoff.PlaybackHandoffCodec
import com.android.purebilibili.feature.video.handoff.PlaybackHandoffPayload
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/**
 * 统一桥接普通视频（MiniPlayerManager）、番剧（BangumiPlayerScreen）与直播（LivePlayerScreen）
 * 的当前播放快照与播放器句柄。所有 ExoPlayer 状态读写都必须在主线程执行。
 */
internal object NearbyPlaybackBridge {
    private data class VideoControlBinding(
        val bvid: String,
        val cid: Long,
        val availableQualityIds: List<Int>,
        val availableAudioQualityIds: List<Int>,
        val availableAudioLangs: List<String>,
        val playerRef: WeakReference<ExoPlayer>,
        val onChangeQuality: (Int) -> Unit,
        val onChangeAudioQuality: ((Int) -> Unit)?,
        val onChangeAudioLang: ((String?) -> Unit)?,
    )

    private data class BangumiBinding(
        val seasonId: Long,
        val epId: Long,
        val bvid: String,
        val cid: Long,
        val title: String,
        val cover: String,
        val qualityId: Int,
        val qualityLabel: String,
        val danmakuEnabled: Boolean?,
        val audioQualityId: Int,
        val audioQualityLabel: String,
        val availableQualityIds: List<Int>,
        val availableAudioQualityIds: List<Int>,
        val playerRef: WeakReference<ExoPlayer>,
        val onChangeQuality: ((Int) -> Unit)?,
        val onChangeAudioQuality: ((Int) -> Unit)?,
    )

    private data class LiveBinding(
        val roomId: Long,
        val title: String,
        val uname: String,
        val cover: String,
        val qualityId: Int,
        val qualityLabel: String,
        val danmakuEnabled: Boolean?,
        val availableQualityIds: List<Int>,
        val playerRef: WeakReference<ExoPlayer>,
        val onChangeQuality: ((Int) -> Unit)?,
    )

    private data class AudioModeBinding(
        val bvid: String,
        val cid: Long,
        val title: String,
        val cover: String,
        val audioQualityId: Int,
        val audioQualityLabel: String,
        val availableAudioQualityIds: List<Int>,
        val playerRef: WeakReference<Player>,
        val onChangeAudioQuality: ((Int) -> Unit)?,
    )

    @Volatile private var videoControlBinding: VideoControlBinding? = null
    @Volatile private var audioModeBinding: AudioModeBinding? = null
    @Volatile private var bangumiBinding: BangumiBinding? = null
    @Volatile private var liveBinding: LiveBinding? = null

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bindAudioMode(
        bvid: String,
        cid: Long,
        title: String,
        cover: String,
        player: Player,
        audioQualityId: Int = 0,
        audioQualityLabel: String = "",
        availableAudioQualityIds: List<Int> = emptyList(),
        onChangeAudioQuality: ((Int) -> Unit)? = null,
    ) {
        if (bvid.isBlank() || cid <= 0L) return
        audioModeBinding = AudioModeBinding(
            bvid = bvid,
            cid = cid,
            title = title,
            cover = cover,
            audioQualityId = audioQualityId.coerceAtLeast(0),
            audioQualityLabel = resolveNearbyAudioQualityLabel(audioQualityId = audioQualityId, rawLabel = audioQualityLabel),
            availableAudioQualityIds = availableAudioQualityIds,
            playerRef = WeakReference(player),
            onChangeAudioQuality = onChangeAudioQuality,
        )
        _revision.update { it + 1 }
    }

    fun unbindAudioMode(player: Player) {
        if (audioModeBinding?.playerRef?.get() === player) {
            audioModeBinding = null
            _revision.update { it + 1 }
        }
    }

    fun bindVideoControls(
        bvid: String,
        cid: Long,
        availableQualityIds: List<Int>,
        player: ExoPlayer,
        onChangeQuality: (Int) -> Unit,
        availableAudioQualityIds: List<Int> = emptyList(),
        availableAudioLangs: List<String> = emptyList(),
        onChangeAudioQuality: ((Int) -> Unit)? = null,
        onChangeAudioLang: ((String?) -> Unit)? = null,
    ) {
        if (bvid.isBlank() || cid <= 0L) return
        videoControlBinding = VideoControlBinding(
            bvid = bvid,
            cid = cid,
            availableQualityIds = availableQualityIds,
            availableAudioQualityIds = availableAudioQualityIds,
            availableAudioLangs = availableAudioLangs,
            playerRef = WeakReference(player),
            onChangeQuality = onChangeQuality,
            onChangeAudioQuality = onChangeAudioQuality,
            onChangeAudioLang = onChangeAudioLang,
        )
        _revision.update { it + 1 }
    }

    fun unbindVideoControls(player: ExoPlayer) {
        if (videoControlBinding?.playerRef?.get() === player) {
            videoControlBinding = null
            _revision.update { it + 1 }
        }
    }

    fun bindBangumi(
        seasonId: Long,
        epId: Long,
        bvid: String,
        cid: Long,
        title: String,
        cover: String,
        player: ExoPlayer,
        qualityId: Int = 0,
        qualityLabel: String = "",
        danmakuEnabled: Boolean? = null,
        audioQualityId: Int = 0,
        audioQualityLabel: String = "",
        availableQualityIds: List<Int> = emptyList(),
        availableAudioQualityIds: List<Int> = emptyList(),
        onChangeQuality: ((Int) -> Unit)? = null,
        onChangeAudioQuality: ((Int) -> Unit)? = null,
    ) {
        if (seasonId <= 0L || epId <= 0L) return
        bangumiBinding = BangumiBinding(
            seasonId = seasonId,
            epId = epId,
            bvid = bvid,
            cid = cid,
            title = title,
            cover = cover,
            qualityId = qualityId.coerceAtLeast(0),
            qualityLabel = resolveNearbyQualityLabel(qualityId = qualityId, rawLabel = qualityLabel, isLive = false),
            danmakuEnabled = danmakuEnabled,
            audioQualityId = audioQualityId.coerceAtLeast(0),
            audioQualityLabel = resolveNearbyAudioQualityLabel(audioQualityId = audioQualityId, rawLabel = audioQualityLabel),
            availableQualityIds = availableQualityIds,
            availableAudioQualityIds = availableAudioQualityIds,
            playerRef = WeakReference(player),
            onChangeQuality = onChangeQuality,
            onChangeAudioQuality = onChangeAudioQuality,
        )
        _revision.update { it + 1 }
    }

    fun unbindBangumi(player: ExoPlayer) {
        if (bangumiBinding?.playerRef?.get() === player) {
            bangumiBinding = null
            _revision.update { it + 1 }
        }
    }

    fun bindLive(
        roomId: Long,
        title: String,
        uname: String,
        cover: String,
        player: ExoPlayer,
        qualityId: Int = 0,
        qualityLabel: String = "",
        danmakuEnabled: Boolean? = null,
        availableQualityIds: List<Int> = emptyList(),
        onChangeQuality: ((Int) -> Unit)? = null,
    ) {
        if (roomId <= 0L) return
        liveBinding = LiveBinding(
            roomId = roomId,
            title = title,
            uname = uname,
            cover = cover,
            qualityId = qualityId.coerceAtLeast(0),
            qualityLabel = resolveNearbyQualityLabel(qualityId = qualityId, rawLabel = qualityLabel, isLive = true),
            danmakuEnabled = danmakuEnabled,
            availableQualityIds = availableQualityIds,
            playerRef = WeakReference(player),
            onChangeQuality = onChangeQuality,
        )
        _revision.update { it + 1 }
    }

    fun unbindLive(player: ExoPlayer) {
        if (liveBinding?.playerRef?.get() === player) {
            liveBinding = null
            _revision.update { it + 1 }
        }
    }

    /** 必须在主线程调用，采集当前正在播放或待续播的媒体快照（含画质与弹幕偏好）。 */
    fun readCurrentMedia(
        miniPlayer: MiniPlayerManager,
        videoDanmakuEnabled: Boolean? = null,
        liveDanmakuEnabled: Boolean? = null,
    ): NearbyVideo? {
        val nowMs = System.currentTimeMillis()
        bangumiBinding?.let { bangumi ->
            val player = bangumi.playerRef.get()
            if (player != null) {
                val media = NearbyVideo(
                    bvid = bangumi.bvid,
                    cid = bangumi.cid,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    speed = player.playbackParameters.speed.coerceIn(0.25f, 4f),
                    title = bangumi.title.take(200),
                    cover = normalizeNearbyCover(bangumi.cover),
                    mediaType = NEARBY_MEDIA_BANGUMI,
                    seasonId = bangumi.seasonId,
                    epId = bangumi.epId,
                    playing = player.isPlaying || player.playWhenReady,
                    updatedAtMs = nowMs,
                    qualityId = bangumi.qualityId,
                    qualityLabel = bangumi.qualityLabel,
                    danmakuEnabled = bangumi.danmakuEnabled ?: videoDanmakuEnabled,
                    audioQualityId = bangumi.audioQualityId,
                    audioQualityLabel = bangumi.audioQualityLabel,
                )
                return runCatching { media.validate(); media }.getOrNull()
            }
        }

        liveBinding?.let { live ->
            val player = live.playerRef.get()
            if (player != null) {
                val media = NearbyVideo(
                    title = live.title.take(200),
                    cover = normalizeNearbyCover(live.cover),
                    mediaType = NEARBY_MEDIA_LIVE,
                    roomId = live.roomId,
                    uname = live.uname.take(64),
                    playing = player.isPlaying || player.playWhenReady,
                    updatedAtMs = nowMs,
                    qualityId = live.qualityId,
                    qualityLabel = live.qualityLabel,
                    danmakuEnabled = live.danmakuEnabled ?: liveDanmakuEnabled,
                )
                return runCatching { media.validate(); media }.getOrNull()
            }
        }

        audioModeBinding?.let { audio ->
            val audioPlayer = audio.playerRef.get()
            if (audioPlayer != null) {
                val media = NearbyVideo(
                    bvid = audio.bvid,
                    cid = audio.cid,
                    positionMs = audioPlayer.currentPosition.coerceAtLeast(0L),
                    speed = audioPlayer.playbackParameters.speed.coerceIn(0.25f, 4f),
                    title = audio.title.take(200),
                    cover = normalizeNearbyCover(audio.cover),
                    mediaType = NEARBY_MEDIA_VIDEO,
                    playing = audioPlayer.isPlaying || audioPlayer.playWhenReady,
                    updatedAtMs = nowMs,
                    audioQualityId = audio.audioQualityId,
                    audioQualityLabel = audio.audioQualityLabel,
                    audioOnly = true,
                )
                return runCatching { media.validate(); media }.getOrNull()
            }
        }

        val player = miniPlayer.activeMediaPlayer ?: return null
        if (miniPlayer.isLiveMode && miniPlayer.currentRoomId > 0L) {
            val media = NearbyVideo(
                title = miniPlayer.currentTitle.take(200),
                cover = normalizeNearbyCover(miniPlayer.currentCover),
                mediaType = NEARBY_MEDIA_LIVE,
                roomId = miniPlayer.currentRoomId,
                uname = miniPlayer.currentLiveUname.take(64),
                playing = player.isPlaying || player.playWhenReady,
                updatedAtMs = nowMs,
                danmakuEnabled = liveDanmakuEnabled,
            )
            return runCatching { media.validate(); media }.getOrNull()
        }

        val bvid = miniPlayer.currentBvid.orEmpty()
        val cid = miniPlayer.currentCid
        if (bvid.isBlank() || cid <= 0L) return null
        val cached = miniPlayer.cachedUiState?.takeIf {
            it.info.bvid.equals(bvid, ignoreCase = true) && (it.info.cid == cid || it.info.cid == 0L)
        }
        val isAudioOnlySession = miniPlayer.isRemoteAudioActive || AudioNowPlayingSession.active.value
        val qualityId = if (isAudioOnlySession) 0 else (cached?.currentQuality?.coerceAtLeast(0) ?: 0)
        val rawQualityLabel = if (isAudioOnlySession) {
            ""
        } else {
            cached?.let { state ->
                state.qualityLabels.getOrNull(state.qualityIds.indexOf(qualityId)).orEmpty()
            }.orEmpty()
        }
        val audioQualityId = cached?.selectedAudioQuality?.takeIf { it > 0 } ?: 0
        val rawAudioQualityLabel = cached?.availableAudioQualities
            ?.firstOrNull { it.preferenceId == audioQualityId }
            ?.label
            .orEmpty()
        val audioLang = cached?.currentAudioLang?.trim().orEmpty().take(16)
        val audioLangLabel = if (audioLang.isNotBlank()) {
            cached?.aiAudio?.items?.firstOrNull { it.langCode == audioLang }?.langDoc.orEmpty().take(24)
        } else ""
        val media = NearbyVideo(
            bvid = bvid,
            cid = cid,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            speed = player.playbackParameters.speed.coerceIn(0.25f, 4f),
            title = miniPlayer.currentTitle.take(200),
            cover = normalizeNearbyCover(miniPlayer.currentCover),
            mediaType = NEARBY_MEDIA_VIDEO,
            playing = player.isPlaying || player.playWhenReady,
            updatedAtMs = nowMs,
            qualityId = qualityId,
            qualityLabel = resolveNearbyQualityLabel(qualityId = qualityId, rawLabel = rawQualityLabel, isLive = false),
            danmakuEnabled = if (isAudioOnlySession) null else videoDanmakuEnabled,
            audioQualityId = audioQualityId,
            audioQualityLabel = resolveNearbyAudioQualityLabel(audioQualityId = audioQualityId, rawLabel = rawAudioQualityLabel),
            audioLang = audioLang,
            audioLangLabel = audioLangLabel,
            audioOnly = isAudioOnlySession,
        )
        return runCatching { media.validate(); media }.getOrNull()
    }

    /** 返回当前活跃播放页可切换的画质档位列表，用于判断是否提供一键切换按钮。 */
    fun availableQualityIdsFor(miniPlayer: MiniPlayerManager, media: NearbyVideo): List<Int> = when (media.mediaType) {
        NEARBY_MEDIA_BANGUMI -> bangumiBinding
            ?.takeIf { it.seasonId == media.seasonId && it.epId == media.epId }
            ?.availableQualityIds
            .orEmpty()
        NEARBY_MEDIA_LIVE -> liveBinding
            ?.takeIf { it.roomId == media.roomId }
            ?.availableQualityIds
            .orEmpty()
        else -> {
            val bound = videoControlBinding?.takeIf {
                it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid
            }
            if (bound != null && bound.availableQualityIds.isNotEmpty()) {
                bound.availableQualityIds
            } else {
                miniPlayer.cachedUiState
                    ?.takeIf { it.info.bvid.equals(media.bvid, ignoreCase = true) }
                    ?.let { state -> state.switchableQualityIds.ifEmpty { state.qualityIds } }
                    .orEmpty()
            }
        }
    }

    /** 必须在主线程调用，向当前绑定的播放控制器请求切换画质。 */
    fun requestQualitySwitch(media: NearbyVideo, targetQualityId: Int): Boolean {
        if (targetQualityId <= 0) return false
        return when (media.mediaType) {
            NEARBY_MEDIA_BANGUMI -> {
                val binding = bangumiBinding?.takeIf {
                    it.seasonId == media.seasonId && it.epId == media.epId && targetQualityId in it.availableQualityIds
                } ?: return false
                binding.onChangeQuality?.invoke(targetQualityId) != null
            }
            NEARBY_MEDIA_LIVE -> {
                val binding = liveBinding?.takeIf {
                    it.roomId == media.roomId && targetQualityId in it.availableQualityIds
                } ?: return false
                binding.onChangeQuality?.invoke(targetQualityId) != null
            }
            else -> {
                val binding = videoControlBinding?.takeIf {
                    it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid &&
                        targetQualityId in it.availableQualityIds
                } ?: return false
                binding.onChangeQuality.invoke(targetQualityId)
                true
            }
        }
    }

    /** 返回当前活跃播放页可切换的音质档位列表（如杜比全景声 / Hi-Res / 192K）。 */
    fun availableAudioQualityIdsFor(miniPlayer: MiniPlayerManager, media: NearbyVideo): List<Int> = when (media.mediaType) {
        NEARBY_MEDIA_BANGUMI -> bangumiBinding
            ?.takeIf { it.seasonId == media.seasonId && it.epId == media.epId }
            ?.availableAudioQualityIds
            .orEmpty()
        NEARBY_MEDIA_LIVE -> emptyList()
        else -> {
            val bound = videoControlBinding?.takeIf {
                it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid
            }
            if (bound != null && bound.availableAudioQualityIds.isNotEmpty()) {
                bound.availableAudioQualityIds
            } else {
                miniPlayer.cachedUiState
                    ?.takeIf { it.info.bvid.equals(media.bvid, ignoreCase = true) }
                    ?.availableAudioQualities
                    ?.map { it.preferenceId }
                    ?.filter { it > 0 }
                    .orEmpty()
            }
        }
    }

    /** 返回当前活跃视频支持的多语言音轨代码列表。 */
    fun availableAudioLangsFor(miniPlayer: MiniPlayerManager, media: NearbyVideo): List<String> {
        if (!media.isVideo) return emptyList()
        val bound = videoControlBinding?.takeIf {
            it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid
        }
        if (bound != null && bound.availableAudioLangs.isNotEmpty()) {
            return bound.availableAudioLangs
        }
        return miniPlayer.cachedUiState
            ?.takeIf { it.info.bvid.equals(media.bvid, ignoreCase = true) }
            ?.aiAudio
            ?.items
            ?.mapNotNull { it.langCode.takeIf { code -> code.isNotBlank() } }
            .orEmpty()
    }

    /** 必须在主线程调用，向当前绑定的播放控制器请求切换音质（如杜比 / Hi-Res）。 */
    fun requestAudioQualitySwitch(media: NearbyVideo, targetAudioQualityId: Int): Boolean {
        if (targetAudioQualityId <= 0) return false
        return when (media.mediaType) {
            NEARBY_MEDIA_BANGUMI -> {
                val binding = bangumiBinding?.takeIf {
                    it.seasonId == media.seasonId && it.epId == media.epId &&
                        targetAudioQualityId in it.availableAudioQualityIds
                } ?: return false
                binding.onChangeAudioQuality?.invoke(targetAudioQualityId) != null
            }
            NEARBY_MEDIA_VIDEO -> {
                val binding = videoControlBinding?.takeIf {
                    it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid &&
                        targetAudioQualityId in it.availableAudioQualityIds
                } ?: return false
                binding.onChangeAudioQuality?.invoke(targetAudioQualityId) != null
            }
            else -> false
        }
    }

    /** 必须在主线程调用，向当前绑定的视频播放器请求切换多语言音轨。 */
    fun requestAudioLangSwitch(media: NearbyVideo, targetAudioLang: String): Boolean {
        if (!media.isVideo || targetAudioLang.isBlank()) return false
        val binding = videoControlBinding?.takeIf {
            it.bvid.equals(media.bvid, ignoreCase = true) && it.cid == media.cid &&
                targetAudioLang in it.availableAudioLangs
        } ?: return false
        binding.onChangeAudioLang?.invoke(targetAudioLang) ?: return false
        return true
    }

    /** 必须在主线程调用，返回与 [media] 匹配的当前活跃 Media3 Player（兼容纯听独立/后台进程播放器）。 */
    fun resolvePlayerForMedia(miniPlayer: MiniPlayerManager, media: NearbyVideo): Player? {
        return when (media.mediaType) {
            NEARBY_MEDIA_BANGUMI -> {
                val binding = bangumiBinding ?: return null
                if (binding.seasonId == media.seasonId && binding.epId == media.epId) {
                    binding.playerRef.get()
                } else null
            }
            NEARBY_MEDIA_LIVE -> {
                val binding = liveBinding
                if (binding != null && binding.roomId == media.roomId) {
                    binding.playerRef.get()
                } else if (miniPlayer.isLiveMode && miniPlayer.currentRoomId == media.roomId) {
                    miniPlayer.player
                } else null
            }
            else -> {
                val audioBinding = audioModeBinding
                if (audioBinding != null &&
                    audioBinding.bvid.equals(media.bvid, ignoreCase = true) &&
                    audioBinding.cid == media.cid
                ) {
                    audioBinding.playerRef.get()?.let { return it }
                }
                if (!miniPlayer.isLiveMode &&
                    miniPlayer.currentBvid.equals(media.bvid, ignoreCase = true) &&
                    miniPlayer.currentCid == media.cid
                ) {
                    miniPlayer.activeMediaPlayer
                } else null
            }
        }
    }

    fun toNavigationRoute(media: NearbyVideo): String = when (media.mediaType) {
        NEARBY_MEDIA_BANGUMI -> PlaybackHandoffCodec.toRoute(
            PlaybackHandoffPayload.V1.Bangumi(
                seasonId = media.seasonId,
                epId = media.epId,
                resumePositionMs = media.positionMs,
            )
        )
        NEARBY_MEDIA_LIVE -> ScreenRoutes.Live.createRoute(
            roomId = media.roomId.toString(),
            title = media.title,
            uname = media.uname,
        )
        else -> if (media.audioOnly) {
            ScreenRoutes.AudioMode.createRoute(
                bvid = media.bvid,
                cid = media.cid,
                resumePositionMs = media.positionMs,
            )
        } else {
            PlaybackHandoffCodec.toRoute(
                PlaybackHandoffPayload.V1.Video(
                    bvid = media.bvid,
                    cid = media.cid,
                    resumePositionMs = media.positionMs,
                )
            )
        }
    }

    /** 为即将发送或拉取的媒体附带当前分 P / 合集 / 外部播单上下文（不包含在轻量 Presence 心跳中）。 */
    fun attachQueueContext(media: NearbyVideo): NearbyVideo {
        if (media.isLive) return media
        val context = sliceNearbyQueueContext(
            playlist = PlaylistManager.playlist.value,
            currentIndex = PlaylistManager.currentIndex.value,
            playMode = PlaylistManager.playMode.value,
            isExternal = PlaylistManager.isExternalPlaylist.value,
            externalSource = PlaylistManager.externalPlaylistSource.value,
            activeMedia = media,
        ) ?: return media
        return media.copy(queue = context)
    }

    /** 接收端在打开接力内容前还原分 P / 合集 / 外部播单上下文，实现连播续接。 */
    fun restoreQueueContext(media: NearbyVideo) {
        val queue = media.queue ?: return
        if (runCatching { queue.validate() }.isFailure) return
        val items = queue.items.map { item ->
            PlaylistItem(
                bvid = item.bvid,
                cid = item.cid,
                title = item.title,
                cover = item.cover,
                owner = item.owner,
                duration = item.duration,
                isBangumi = item.isBangumi,
                seasonId = item.seasonId.takeIf { it > 0L },
                epId = item.epId.takeIf { it > 0L },
            )
        }
        if (items.isEmpty()) return
        if (queue.isExternal) {
            val source = ExternalPlaylistSource.entries.firstOrNull { it.name == queue.externalSource }
                ?: ExternalPlaylistSource.UNKNOWN
            PlaylistManager.setExternalPlaylist(items, startIndex = queue.currentIndex, source = source)
        } else {
            PlaylistManager.setPlaylist(items, startIndex = queue.currentIndex)
        }
        PlayMode.entries.firstOrNull { it.name == queue.playMode }?.let { mode ->
            PlaylistManager.setPlayMode(mode)
        }
    }
}

/**
 * 围绕当前播放项截取最多 [NEARBY_MAX_QUEUE_ITEMS] 条队列窗口（优先保留后续待播剧集），
 * 并确保每条满足传输校验且只有多于 1 项时才附带队列上下文。
 */
internal fun sliceNearbyQueueContext(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    playMode: PlayMode,
    isExternal: Boolean,
    externalSource: ExternalPlaylistSource,
    activeMedia: NearbyVideo,
): NearbyQueueContext? {
    if (playlist.size <= 1) return null
    val matchedIndex = when {
        currentIndex in playlist.indices && matchesPlaylistItem(playlist[currentIndex], activeMedia) -> currentIndex
        else -> playlist.indexOfFirst { matchesPlaylistItem(it, activeMedia) }
    }
    if (matchedIndex !in playlist.indices) return null

    // 最多带 24 条：前面保留最多 6 条历史项，后面尽可能多保留后续待连播剧集
    val maxBefore = 6
    val start = (matchedIndex - maxBefore).coerceAtLeast(0)
    val endExclusive = (start + NEARBY_MAX_QUEUE_ITEMS).coerceAtMost(playlist.size)
    val adjustedStart = (endExclusive - NEARBY_MAX_QUEUE_ITEMS).coerceAtLeast(0)
    val window = playlist.subList(adjustedStart, endExclusive)
    val mapped = mutableListOf<NearbyQueueItem>()
    var mappedCurrentIndex = -1
    window.forEachIndexed { idx, item ->
        val candidate = NearbyQueueItem(
            bvid = item.bvid,
            cid = item.cid.coerceAtLeast(0L),
            title = item.title.take(120),
            cover = normalizeNearbyCover(item.cover),
            owner = item.owner.take(48),
            duration = item.duration.coerceAtLeast(0L),
            isBangumi = item.isBangumi,
            seasonId = item.seasonId ?: 0L,
            epId = item.epId ?: 0L,
        )
        if (runCatching { candidate.validate() }.isSuccess) {
            if (adjustedStart + idx == matchedIndex) {
                mappedCurrentIndex = mapped.size
            }
            mapped += candidate
        }
    }
    if (mapped.size <= 1 || mappedCurrentIndex !in mapped.indices) return null
    return NearbyQueueContext(
        items = mapped,
        currentIndex = mappedCurrentIndex,
        playMode = playMode.name,
        isExternal = isExternal,
        externalSource = externalSource.name,
    )
}

private fun matchesPlaylistItem(item: PlaylistItem, media: NearbyVideo): Boolean = when {
    media.isBangumi -> item.isBangumi && item.epId == media.epId &&
        (item.seasonId == null || item.seasonId == media.seasonId)
    media.isVideo -> !item.isBangumi && item.bvid.equals(media.bvid, ignoreCase = true) &&
        (item.cid <= 0L || item.cid == media.cid)
    else -> false
}
