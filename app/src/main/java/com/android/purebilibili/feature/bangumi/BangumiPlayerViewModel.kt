// 文件路径: feature/bangumi/BangumiPlayerViewModel.kt
package com.android.purebilibili.feature.bangumi

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.player.BasePlayerViewModel
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.util.MediaUtils
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.core.util.resolvePlaybackDefaultQualityId
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.download.DownloadManager
import com.android.purebilibili.feature.download.DownloadTask
import com.android.purebilibili.core.player.PlaybackProgressManager
import com.android.purebilibili.feature.video.playback.audio.AudioFallbackReason
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.playback.audio.resolveAudioStreamSelection
import com.android.purebilibili.feature.video.playback.audio.resolveRequestedAudioQuality
import com.android.purebilibili.feature.video.playback.policy.shouldRefreshPremiumAudioForPlaybackSpeedChange
import com.android.purebilibili.feature.video.usecase.VideoInteractionUseCase
import com.android.purebilibili.feature.plugin.PlaybackCdnPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal fun resolveBangumiPlaybackAuthState(
    hasSessionCookie: Boolean,
    hasAccessToken: Boolean,
    cachedIsVip: Boolean,
    seasonUserVip: Boolean
): Pair<Boolean, Boolean> {
    val isLoggedIn = hasSessionCookie || hasAccessToken
    val isVip = isLoggedIn && (cachedIsVip || seasonUserVip)
    return isLoggedIn to isVip
}

internal fun shouldRestoreBangumiCachedPlayback(
    requestedSeasonId: Long,
    requestedEpisodeId: Long,
    loadedSeasonId: Long,
    loadedEpisodeId: Long,
    cachedVideoUrl: String?,
    cachedAudioUrl: String?,
    attachedPlayerMediaItemCount: Int
): Boolean {
    return requestedSeasonId == loadedSeasonId &&
        requestedEpisodeId == loadedEpisodeId &&
        cachedVideoUrl?.isNotBlank() == true &&
        cachedAudioUrl?.isNotBlank() == true &&
        attachedPlayerMediaItemCount == 0
}

/**
 * 番剧播放器 UI 状态
 */
sealed class BangumiPlayerState {
    object Loading : BangumiPlayerState()
    
    data class Success(
        val seasonDetail: BangumiDetail,
        val currentEpisode: BangumiEpisode,
        val currentEpisodeIndex: Int,
        val playUrl: String?,
        val audioUrl: String?,
        val quality: Int,
        val acceptQuality: List<Int>,
        val acceptDescription: List<String>,
        val cachedDash: Dash? = null,
        val requestedAudioQuality: Int = -1,
        val selectedAudioQuality: Int = -1,
        val availableAudioQualities: List<AudioQualityOption> = emptyList(),
        val audioFallbackReason: AudioFallbackReason? = null,
        val isPreview: Boolean = false,
        val hasPaid: Boolean = false,
        val playbackStatus: Int = 0,
        val playbackErrorMessage: String? = null,
        val isLoggedIn: Boolean = false,
        val isVip: Boolean = false,
        val isLiked: Boolean = false,
        val coinCount: Int = 0
    ) : BangumiPlayerState()
    
    data class Error(
        val message: String,
        val isVipRequired: Boolean = false,
        val isLoginRequired: Boolean = false,
        val canRetry: Boolean = true
    ) : BangumiPlayerState()
}

internal data class BangumiExternalPlaylist(
    val playlistItems: List<PlaylistItem>,
    val startIndex: Int
)

internal fun buildExternalPlaylistFromBangumi(
    detail: BangumiDetail,
    currentEpisodeId: Long
): BangumiExternalPlaylist? {
    val seasonId = detail.seasonId
    val episodes = detail.episodes.orEmpty().filter { it.id > 0L }
    if (seasonId <= 0L || episodes.isEmpty()) return null

    val playlistItems = episodes.map { episode ->
        PlaylistItem(
            bvid = episode.bvid,
            title = episode.title.ifBlank { episode.longTitle.ifBlank { "第${episode.id}集" } },
            cover = episode.cover.ifBlank { detail.cover },
            owner = detail.title,
            duration = (episode.duration / 1000L).coerceAtLeast(0L),
            isBangumi = true,
            seasonId = seasonId,
            epId = episode.id
        )
    }

    val startIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        .takeIf { it >= 0 }
        ?: 0

    return BangumiExternalPlaylist(
        playlistItems = playlistItems,
        startIndex = startIndex
    )
}

/**
 * 番剧播放器 ViewModel
 * 
 *  [重构] 继承 BasePlayerViewModel，复用空降助手、DASH 播放、弹幕等公共功能
 */
class BangumiPlayerViewModel : BasePlayerViewModel() {
    private val interactionUseCase = VideoInteractionUseCase()
    
    private val _uiState = MutableStateFlow<BangumiPlayerState>(BangumiPlayerState.Loading)
    val uiState = _uiState.asStateFlow()
    
    //  Toast 事件通道
    private val _toastEvent = Channel<String>()
    val toastEvent = _toastEvent.receiveAsFlow()

    private val _coinDialogVisible = MutableStateFlow(false)
    val coinDialogVisible = _coinDialogVisible.asStateFlow()

    private val _userCoinBalance = MutableStateFlow<Double?>(null)
    val userCoinBalance = _userCoinBalance.asStateFlow()

    private fun resolveConfiguredAudioQuality(): Int {
        val context = NetworkModule.appContext ?: return -1
        return resolveRequestedAudioQuality(
            defaultAudioQuality = PlayerSettingsStore.getCachedDefaultAudioQuality(context),
            rememberedAudioQuality = PlayerSettingsStore.getCachedLastSelectedAudioQuality(context)
        )
    }
    
    private var currentSeasonId: Long = 0
    private var currentEpId: Long = 0
    private var isCourseMode: Boolean = false
    private var bangumiHeartbeatJob: Job? = null
    /** Only the latest episode request may update the player state. */
    private var playbackLoadJob: Job? = null
    private var openingSkippedEpisodeId: Long = 0L
    private var endingSkippedEpisodeId: Long = 0L
    private var progressManager: PlaybackProgressManager? = null

    //  [修复] 与详情页保持一致的追番状态缓存
    private val followStatusCache = mutableMapOf<Long, Boolean>()
    private val followStatusValueCache = mutableMapOf<Long, Int>()
    private val followedSeasonIds = mutableSetOf<Long>()
    private val loadedFollowTypes = mutableSetOf<Int>()
    
    //  [重构] 覆盖基类的空降跳过回调，显示 toast
    override fun onSponsorSkipped(segment: SponsorSegment) {
        viewModelScope.launch {
            _toastEvent.send("已跳过: ${segment.categoryName}")
        }
    }
    
    //  [新增] 播放完成监听器
    private val playbackEndListener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                flushBangumiPlaybackHeartbeat()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                flushBangumiPlaybackHeartbeat()
                // 播放完成，自动播放下一集
                playNextEpisode()
            }
        }
    }

    init {
        viewModelScope.launch {
            ensureFollowedSeasonsLoaded(MY_FOLLOW_TYPE_BANGUMI)
            ensureFollowedSeasonsLoaded(MY_FOLLOW_TYPE_CINEMA)
        }
    }
    
    /**
     *  [新增] 自动播放下一集
     */
    fun playNextEpisode() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val episodes = currentState.seasonDetail.episodes ?: return
        val currentIndex = currentState.currentEpisodeIndex
        
        // 检查是否有下一集
        if (currentIndex < episodes.size - 1) {
            val nextEpisode = episodes[currentIndex + 1]
            viewModelScope.launch {
                val episodeTitle = nextEpisode.title.ifBlank {
                    nextEpisode.longTitle.ifBlank { "第${currentIndex + 2}集" }
                }
                _toastEvent.send("正在播放下一集: $episodeTitle")
            }
            switchEpisode(nextEpisode)
        } else {
            // 已经是最后一集
            viewModelScope.launch {
                _toastEvent.send("已是最后一集")
            }
        }
    }

    fun checkAndSkipEpisodeOpEd() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val player = exoPlayer ?: return
        val episode = currentState.currentEpisode
        val action = resolveBangumiEpisodeSkipAction(
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it > 0L } ?: episode.duration,
            skip = episode.skip,
            openingAlreadySkipped = openingSkippedEpisodeId == episode.id,
            endingAlreadySkipped = endingSkippedEpisodeId == episode.id
        ) ?: return
        when (action.kind) {
            BangumiEpisodeSkipKind.OPENING -> openingSkippedEpisodeId = episode.id
            BangumiEpisodeSkipKind.ENDING -> endingSkippedEpisodeId = episode.id
        }
        player.seekTo(action.seekToMs)
        _toastEvent.trySend("已跳过${action.kind.label}")
    }
    
    /**
     * 绑定播放器
     */
    override fun attachPlayer(player: ExoPlayer) {
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🔗 attachPlayer called, player hashCode: ${player.hashCode()}")
        super.attachPlayer(player)
        NetworkModule.appContext?.let { context ->
            progressManager = PlaybackProgressManager.getInstance(context)
        }
        //  [新增] 添加播放完成监听
        player.addListener(playbackEndListener)
    }
    
    /**
     *  [新增] 清理时移除监听器
     */
    override fun onCleared() {
        flushBangumiPlaybackHeartbeat()
        bangumiHeartbeatJob?.cancel()
        super.onCleared()
        // 监听器会随 player 一起清理，无需手动移除
    }
    
    /**
     * 加载番剧播放（从详情页进入）
     */
    fun loadBangumiPlay(
        seasonId: Long,
        epId: Long,
        resumePositionMs: Long = 0L,
        isCourse: Boolean = false,
        preferredAid: Long = 0L
    ) {
        isCourseMode = isCourse
        val startPositionMs = resumePositionMs.coerceAtLeast(0L)
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📥 loadBangumiPlay: seasonId=$seasonId, epId=$epId, aid=$preferredAid, resume=${startPositionMs}ms, isCourse=$isCourse, exoPlayer=${exoPlayer?.hashCode()}")
        val cachedState = _uiState.value as? BangumiPlayerState.Success
        if (seasonId == currentSeasonId && epId == currentEpId && cachedState != null) {
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "♻️ loadBangumiPlay: restore cached detail")
            val player = exoPlayer
            if (player != null && shouldRestoreBangumiCachedPlayback(
                    requestedSeasonId = seasonId,
                    requestedEpisodeId = epId,
                    loadedSeasonId = cachedState.seasonDetail.seasonId,
                    loadedEpisodeId = cachedState.currentEpisode.id,
                    cachedVideoUrl = cachedState.playUrl,
                    cachedAudioUrl = cachedState.audioUrl,
                    attachedPlayerMediaItemCount = player.mediaItemCount
                )
            ) {
                val restorePositionMs = resolveBangumiPlaybackStartPositionMs(
                    routeResumePositionMs = startPositionMs,
                    savedEpisodePositionMs = progressManager?.getCachedPosition(
                        bvid = cachedState.currentEpisode.bvid,
                        cid = cachedState.currentEpisode.cid
                    ) ?: 0L
                )
                val isCoursePlayback = isCourse || cachedState.seasonDetail.seasonType == 10
                val cachedReferer = if (isCoursePlayback) {
                    "https://www.bilibili.com/cheese/play/ep${cachedState.currentEpisode.id}"
                } else {
                    "https://www.bilibili.com/bangumi/play/ep${cachedState.currentEpisode.id}"
                }
                val cachedDashManifest = cachedState.cachedDash?.let { dash ->
                    val cachedVideo = dash.video.firstOrNull {
                        it.getValidUrl() == cachedState.playUrl
                    } ?: dash.getBestVideo(
                        cachedState.quality,
                        preferCodec = resolveBangumiPreferredCodec(isCoursePlayback)
                    )
                    val cachedAudio = dash.audio.orEmpty().firstOrNull {
                        it.getValidUrl() == cachedState.audioUrl
                    }
                    buildBangumiDashManifest(
                        dash = dash,
                        video = cachedVideo,
                        videoUrl = cachedState.playUrl,
                        audio = cachedAudio,
                        audioUrl = cachedState.audioUrl,
                        durationMs = dash.duration.toLong()
                            .times(1000L)
                            .takeIf { it > 0L }
                            ?: cachedState.currentEpisode.duration.toLong().coerceAtLeast(0L)
                    )
                }
                playDashVideo(
                    videoUrl = requireNotNull(cachedState.playUrl),
                    audioUrl = cachedState.audioUrl,
                    seekToMs = restorePositionMs,
                    referer = cachedReferer,
                    dashManifest = cachedDashManifest
                )
                return
            }
            if (player?.mediaItemCount ?: 0 > 0) {
                player?.seekTo(startPositionMs)
                return
            }
            com.android.purebilibili.core.util.Logger.d(
                "BangumiPlayerVM",
                "Cached playback is incomplete; reload the episode source"
            )
        }
        
        currentSeasonId = seasonId
        currentEpId = epId
        playbackLoadJob?.cancel()
        playbackLoadJob = viewModelScope.launch {
            _uiState.value = BangumiPlayerState.Loading
            
            // 1. 获取番剧/课程详情（包含剧集列表）
            val detailRequest = resolveBangumiDetailRequest(seasonId, epId)
            val detailResult = if (isCourse) {
                BangumiRepository.getPugvSeasonDetail(
                    seasonId = detailRequest.seasonId,
                    epId = detailRequest.epId
                )
            } else {
                BangumiRepository.getSeasonDetail(
                    seasonId = detailRequest.seasonId,
                    epId = detailRequest.epId
                )
            }
            
            detailResult.onSuccess { detail ->
                val resumeTarget = resolveBangumiInitialEpisode(
                    detail = detail,
                    preferredAid = preferredAid,
                    routeEpId = epId,
                    autoResumeEnabled = true
                )
                val targetEpId = resumeTarget?.epId ?: epId
                val targetResumeMs = if (startPositionMs > 0L) {
                    startPositionMs
                } else {
                    resumeTarget?.resumePositionMs ?: 0L
                }

                // 找到当前剧集 (优先 targetEpId，否则首集)
                val episode = detail.episodes?.find { it.id == targetEpId }
                    ?: detail.episodes?.firstOrNull()
                
                if (episode == null) {
                    _uiState.value = BangumiPlayerState.Error("未找到可播放的剧集")
                    return@onSuccess
                }
                
                val episodeIndex = detail.episodes?.indexOfFirst { it.id == episode.id } ?: 0
                
                // 2. 获取播放地址
                fetchPlayUrl(
                    detail = detail,
                    episode = episode,
                    episodeIndex = episodeIndex,
                    startPositionMs = resolveBangumiPlaybackStartPositionMs(
                        routeResumePositionMs = targetResumeMs,
                        savedEpisodePositionMs = progressManager?.getCachedPosition(
                            bvid = episode.bvid,
                            cid = episode.cid
                        ) ?: 0L
                    )
                )
                
            }.onFailure { e ->
                _uiState.value = BangumiPlayerState.Error(
                    message = e.message ?: "加载失败",
                    canRetry = true
                )
            }
        }
    }
    
    /**
     * 番剧视频编码偏好：设备支持 HEVC 时优先 hev1（HDR/杜比视界轨道基本为 HEVC），
     * 不支持时回退 avc1 保证可解码。
     */
    private fun resolveBangumiPreferredCodec(isCourse: Boolean = isCourseMode): String =
        if (isCourse) "avc1" else if (MediaUtils.isHevcSupported()) "hev1" else "avc1"

    /**
     * 与普通视频共用网络默认画质、自动最高画质及账号权限策略。
     */
    private suspend fun resolveBangumiInitialQuality(): Int {
        val context = NetworkModule.appContext ?: return 64
        val isLoggedIn = com.android.purebilibili.data.repository.VideoRepository.isPlaybackLoggedIn()
        val storedQuality = NetworkUtils.getDefaultQualityId(context)
        val autoHighestEnabled = SettingsManager.getAutoHighestQualitySync(context)
        val isVip = com.android.purebilibili.data.repository.VideoRepository
            .refreshVipStatusForPreferredQualityIfNeeded(
                isLoggedIn = isLoggedIn,
                cachedIsVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip(),
                storedQuality = storedQuality,
                autoHighestEnabled = autoHighestEnabled
            )
        return resolvePlaybackDefaultQualityId(
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled,
            isLoggedIn = isLoggedIn,
            isVip = isVip
        )
    }

    /**
     * 获取播放地址
     */
    private suspend fun fetchPlayUrl(
        detail: BangumiDetail,
        episode: BangumiEpisode,
        episodeIndex: Int,
        startPositionMs: Long = 0L
    ) {
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🎬 fetchPlayUrl: epId=${episode.id}, cid=${episode.cid}, aid=${episode.aid}")
        val isCourse = detail.seasonType == 10
        var requestedQn = resolveBangumiInitialQuality()
        var playUrlResult = BangumiRepository.getBangumiPlayUrl(
            epId = episode.id,
            qn = requestedQn,
            cid = episode.cid,
            bvid = episode.bvid,
            seasonId = detail.seasonId,
            aid = episode.aid,
            isCourse = isCourse
        )
        // 高码率档（VIP 1080P 高码率/4K 等）可能下发 Widevine 加密流，而 B 站
        // license 接口未公开、本地无法解密（PiliPlus 同样不解密，靠低档拿清晰流）。
        // 拿到 is_drm 响应时直接预判降档到蓝光，避免黑屏报错后再回退。
        if (playUrlResult.getOrNull()?.isDrm == true && requestedQn > 80) {
            com.android.purebilibili.core.util.Logger.w(
                "BangumiPlayerVM",
                "🔒 qn=$requestedQn returned DRM stream, refetching with qn=80"
            )
            requestedQn = 80
            playUrlResult = BangumiRepository.getBangumiPlayUrl(
                epId = episode.id,
                qn = requestedQn,
                cid = episode.cid,
                bvid = episode.bvid,
                seasonId = detail.seasonId,
                aid = episode.aid,
                isCourse = isCourse
            )
        }
        
        playUrlResult.onSuccess { playData ->
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📡 PlayUrl success: quality=${playData.quality}, hasDash=${playData.dash != null}, hasDurl=${!playData.durl.isNullOrEmpty()}")
            
            // 解析播放地址
            var videoUrl: String? = null
            var audioUrl: String? = null
            var dashManifest: String? = null
            var durlSegmentUrls: List<String> = emptyList()
            var selectedQuality = playData.quality
            val requestedAudioQuality = resolveConfiguredAudioQuality()
            val audioSelection = playData.dash?.let { dash ->
                resolveAudioStreamSelection(
                    dash = dash,
                    requestedAudioQuality = requestedAudioQuality,
                    playbackSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f,
                    isDolbyAudioSupported = MediaUtils.isDolbyAtmosAudioSupported(),
                    isDolbyAudioSoftwareDecoded = MediaUtils.isDolbySoftwareAudioDecoderRequired()
                )
            }
            
            if (playData.dash != null) {
                val checkedPlayDataDash = requireNotNull(playData.dash)
                // DASH 格式
                val dash = checkedPlayDataDash
                val targetQuality = if (requestedQn >= 127) {
                    dash.video.map { it.id }.filter { quality ->
                        when (quality) {
                            126 -> MediaUtils.isDolbyVisionSupported()
                            125 -> MediaUtils.isHdrSupported()
                            else -> true
                        }
                    }.maxOrNull() ?: playData.quality
                } else {
                    requestedQn
                }
                //  设备支持 HEVC 时优先 hev1（HDR/杜比视界轨道基本为 HEVC），否则回退 avc1 保证可解码
                val video = dash.getBestVideo(
                    targetQuality,
                    preferCodec = resolveBangumiPreferredCodec(isCourse),
                    isHevcSupported = MediaUtils.isHevcSupported()
                )
                selectedQuality = video?.id ?: playData.quality
                val audio = audioSelection?.selected?.track
                
                com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📹 DASH videos: ${dash.video.size}, audios: ${dash.audio?.size ?: 0}")
                
                //  [优化] 尝试主 URL，失败则使用备用 URL
                videoUrl = video?.getValidUrl()
                if (videoUrl.isNullOrEmpty() && video?.backupUrl?.isNotEmpty() == true) {
                    videoUrl = video?.backupUrl?.firstOrNull()
                    com.android.purebilibili.core.util.Logger.w("BangumiPlayerVM", " 主 URL 无效，使用备用 CDN: ${videoUrl?.take(60)}...")
                }
                
                audioUrl = audio?.getValidUrl()
                if (audioUrl.isNullOrEmpty() && audio?.backupUrl?.isNotEmpty() == true) {
                    audioUrl = audio?.backupUrl?.firstOrNull()
                }

                // Keep the complete signed playurl candidates intact. The CDN plugin may only
                // reorder these addresses in its safe mode; it never synthesizes a new host.
                PluginManager.getEnabledPlugins(PlaybackCdnPlugin::class).firstOrNull()
                    ?.rewritePlaybackCandidates(
                        videoUrls = buildList {
                            videoUrl?.takeIf { it.isNotBlank() }?.let(::add)
                            video?.backupUrl.orEmpty().filter { it.isNotBlank() }.forEach(::add)
                        },
                        audioUrls = buildList {
                            audioUrl?.takeIf { it.isNotBlank() }?.let(::add)
                            audio?.backupUrl.orEmpty().filter { it.isNotBlank() }.forEach(::add)
                        }
                    )
                    ?.let { rewrite ->
                        videoUrl = rewrite.videoUrls.firstOrNull() ?: videoUrl
                        audioUrl = rewrite.audioUrls.firstOrNull()?.takeIf { it.isNotBlank() } ?: audioUrl
                    }

                dashManifest = buildBangumiDashManifest(
                    dash = dash,
                    video = video,
                    videoUrl = videoUrl,
                    audio = audio,
                    audioUrl = audioUrl,
                    durationMs = playData.timelength.takeIf { it > 0L }
                        ?: dash.duration.toLong() * 1000L
                )
                
                com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", " DASH: video=${videoUrl?.take(60)}..., audio=${audioUrl?.take(40)}...")
                
            } else {
                // FLV/MP4 格式
                durlSegmentUrls = collectPlayableDurlUrls(
                    when {
                        !playData.durl.isNullOrEmpty() -> playData.durl
                        !playData.durls.isNullOrEmpty() -> playData.durls
                        else -> null
                    }
                )
                if (durlSegmentUrls.isNotEmpty()) {
                    videoUrl = durlSegmentUrls.first()
                    audioUrl = null
                    com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📹 DURL: segments=${durlSegmentUrls.size}, first=${videoUrl.take(60)}...")
                } else {
                    com.android.purebilibili.core.util.Logger.e("BangumiPlayerVM", "❌ No dash or durl in response!")
                    if (isCourse) {
                        _uiState.value = BangumiPlayerState.Success(
                            seasonDetail = detail,
                            currentEpisode = episode,
                            currentEpisodeIndex = episodeIndex,
                            playUrl = null,
                            audioUrl = null,
                            quality = 0,
                            acceptQuality = emptyList(),
                            acceptDescription = emptyList(),
                            cachedDash = null,
                            playbackErrorMessage = "该课程需购买后观看"
                        )
                        return
                    }
                    _uiState.value = BangumiPlayerState.Error("无法获取播放地址：服务器未返回视频流")
                    return
                }
            }
            
            if (videoUrl.isNullOrEmpty()) {
                if (isCourse) {
                    _uiState.value = BangumiPlayerState.Success(
                        seasonDetail = detail,
                        currentEpisode = episode,
                        currentEpisodeIndex = episodeIndex,
                        playUrl = null,
                        audioUrl = null,
                        quality = 0,
                        acceptQuality = emptyList(),
                        acceptDescription = emptyList(),
                        cachedDash = null,
                        playbackErrorMessage = "该课程需购买后观看"
                    )
                    return
                }
                _uiState.value = BangumiPlayerState.Error("无法获取播放地址")
                return
            }
            
            val realSeasonId = detail.seasonId
            val followType = defaultMyFollowTypeForSeasonType(detail.seasonType)
            if (!loadedFollowTypes.contains(followType)) {
                ensureFollowedSeasonsLoaded(followType)
            }
            val isFollowed = when {
                followStatusValueCache.containsKey(realSeasonId) -> followStatusValueCache[realSeasonId]!! > 0
                followStatusCache.containsKey(realSeasonId) -> followStatusCache[realSeasonId] == true
                followedSeasonIds.contains(realSeasonId) -> true
                else -> isBangumiFollowed(detail.userStatus)
            }
            followStatusCache[realSeasonId] = isFollowed
            if (isFollowed) {
                followedSeasonIds.add(realSeasonId)
            }
            val userStatus = detail.userStatus
            val correctedDetail = detail.copy(
                userStatus = userStatus?.copy(
                    follow = if (isFollowed) 1 else 0,
                    followStatus = if (isFollowed) {
                        followStatusValueCache[realSeasonId]
                            ?: maxOf(userStatus.followStatus, BANGUMI_FOLLOW_STATUS_WANT)
                    } else {
                        0
                    }
                ) ?: UserStatus(
                    follow = if (isFollowed) 1 else 0,
                    followStatus = if (isFollowed) 1 else 0
                )
            )
            val (isLoggedIn, isVip) = resolveBangumiPlaybackAuthState(
                hasSessionCookie = com.android.purebilibili.data.repository.VideoRepository.hasPlaybackSessionCookie(),
                hasAccessToken = !com.android.purebilibili.data.repository.VideoRepository.playbackAccessToken().isNullOrEmpty(),
                cachedIsVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip(),
                seasonUserVip = detail.userStatus?.vip == 1
            )
            val qualityOptions = resolveBangumiQualityOptions(playData)

            _uiState.value = BangumiPlayerState.Success(
                seasonDetail = correctedDetail,
                currentEpisode = episode,
                currentEpisodeIndex = episodeIndex,
                playUrl = videoUrl,
                audioUrl = audioUrl,
                quality = selectedQuality,
                acceptQuality = qualityOptions.ids,
                acceptDescription = qualityOptions.labels,
                cachedDash = playData.dash,
                requestedAudioQuality = audioSelection?.requestedPreferenceId ?: requestedAudioQuality,
                selectedAudioQuality = audioSelection?.selectedPreferenceId ?: -1,
                availableAudioQualities = audioSelection?.availableOptions.orEmpty(),
                audioFallbackReason = audioSelection?.fallbackReason,
                isPreview = playData.isPreview,
                hasPaid = playData.hasPaid,
                playbackStatus = playData.status,
                isLoggedIn = isLoggedIn,
                isVip = isVip
            )

            refreshEpisodeInteractionState(
                episodeAid = episode.aid,
                episodeId = episode.id
            )

            buildExternalPlaylistFromBangumi(
                detail = correctedDetail,
                currentEpisodeId = episode.id
            )?.let { externalPlaylist ->
                PlaylistManager.setExternalPlaylist(
                    items = externalPlaylist.playlistItems,
                    startIndex = externalPlaylist.startIndex,
                    source = ExternalPlaylistSource.UNKNOWN
                )
            }
            
            //  [修复] 检查播放器是否已附加，添加调试日志
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🎯 About to call playDashVideo, exoPlayer attached: ${exoPlayer != null}")
            if (exoPlayer == null) {
                com.android.purebilibili.core.util.Logger.e("BangumiPlayerVM", "❌ exoPlayer is NULL when trying to play! Video URL: ${videoUrl.take(50)}...")
            }
            
            //  [修复] 构建番剧/课程专用 Referer，解决 CDN 403 播放失败问题
            val isCourse = detail.seasonType == 10
            val referer = if (isCourse) {
                "https://www.bilibili.com/cheese/play/ep${episode.id}"
            } else {
                "https://www.bilibili.com/bangumi/play/ep${episode.id}"
            }
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🔗 Using Referer: $referer")
            
            //  [修复] 多段 durl 使用拼接播放，避免只播第一段
            if (audioUrl.isNullOrEmpty() && durlSegmentUrls.size > 1) {
                playSegmentedVideo(
                    segmentUrls = durlSegmentUrls,
                    seekToMs = startPositionMs,
                    referer = referer
                )
            } else {
                playDashVideo(
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    seekToMs = startPositionMs,
                    referer = referer,
                    dashManifest = dashManifest
                )
            }
            
            //  [重构] 使用基类方法加载弹幕
            //  UI 层 (BangumiPlayerScreen) 已经通过 DanmakuManager 加载了弹幕，此处无需重复加载
            // loadDanmaku(episode.cid)
            
            //  [重构] 使用基类方法加载空降片段
            episode.bvid.takeIf { it.isNotBlank() }?.let { loadSponsorSegments(it) }
            
            startBangumiPlaybackHeartbeat(detail, episode)
            
        }.onFailure { e ->
            val isPaidOrPermission = e.message?.contains("购买") == true || e.message?.contains("权限") == true
            val errorMsg = e.message ?: "获取播放地址失败"

            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            val (authStateLoggedIn, authStateVip) = resolveBangumiPlaybackAuthState(
                hasSessionCookie = com.android.purebilibili.data.repository.VideoRepository.hasPlaybackSessionCookie(),
                hasAccessToken = !com.android.purebilibili.data.repository.VideoRepository.playbackAccessToken().isNullOrEmpty(),
                cachedIsVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip(),
                seasonUserVip = detail.userStatus?.vip == 1
            )

            _uiState.value = BangumiPlayerState.Success(
                seasonDetail = detail,
                currentEpisode = episode,
                currentEpisodeIndex = episodeIndex,
                playUrl = null,
                audioUrl = null,
                quality = 0,
                acceptQuality = emptyList(),
                acceptDescription = emptyList(),
                cachedDash = null,
                isPreview = false,
                hasPaid = !isPaidOrPermission,
                playbackStatus = if (isPaidOrPermission) 1 else 0,
                playbackErrorMessage = errorMsg,
                isLoggedIn = authStateLoggedIn,
                isVip = authStateVip
            )
            _toastEvent.trySend(errorMsg)
        }
    }
    
    /**
     * 切换剧集
     */
    fun switchEpisode(episode: BangumiEpisode) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        
        if (episode.id == currentState.currentEpisode.id && currentState.playUrl != null) return
        
        flushBangumiPlaybackHeartbeat()
        playbackLoadJob?.cancel()
        currentEpId = episode.id
        val newIndex = currentState.seasonDetail.episodes?.indexOfFirst { it.id == episode.id } ?: 0
        
        _uiState.value = currentState.copy(
            currentEpisode = episode,
            currentEpisodeIndex = newIndex,
            playUrl = null,
            audioUrl = null,
            playbackErrorMessage = null
        )
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        playbackLoadJob = viewModelScope.launch {
            fetchPlayUrl(currentState.seasonDetail, episode, newIndex)
        }
    }

    /**
     * 重新加载当前剧集
     */
    fun reloadCurrentEpisode() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        playbackLoadJob?.cancel()
        playbackLoadJob = viewModelScope.launch {
            fetchPlayUrl(currentState.seasonDetail, currentState.currentEpisode, currentState.currentEpisodeIndex)
        }
    }

    /** Enqueue the currently loaded course episode in the existing offline manager. */
    fun downloadCurrentEpisode(context: Context) {
        val state = _uiState.value as? BangumiPlayerState.Success ?: return
        val videoUrl = state.playUrl.orEmpty()
        if (videoUrl.isBlank()) {
            viewModelScope.launch { _toastEvent.send(state.playbackErrorMessage ?: "当前集暂时无法下载") }
            return
        }
        val episode = state.currentEpisode
        val detail = state.seasonDetail
        val owner = detail.upInfo
        val qualityIndex = state.acceptQuality.indexOf(state.quality)
        val qualityDesc = state.acceptDescription.getOrNull(qualityIndex)
            ?: "${state.quality}P"
        val task = DownloadTask(
            aid = episode.aid,
            bvid = episode.bvid,
            cid = episode.cid,
            title = detail.title.ifBlank { episode.title },
            episodeLabel = episode.title.ifBlank { "第${state.currentEpisodeIndex + 1}讲" },
            groupKey = "course:${detail.seasonId}",
            groupTitle = detail.title,
            episodeSortIndex = state.currentEpisodeIndex,
            episodeCount = detail.episodes?.size ?: 1,
            cover = episode.cover.ifBlank { detail.cover },
            ownerName = owner?.uname.orEmpty(),
            ownerFace = owner?.avatar.orEmpty(),
            duration = (episode.duration / 1000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            quality = state.quality,
            qualityDesc = qualityDesc,
            videoUrl = videoUrl,
            audioUrl = state.audioUrl.orEmpty()
        )
        viewModelScope.launch {
            val added = DownloadManager.addTask(task)
            _toastEvent.send(if (added) "已加入课程下载" else "该集已在下载列表")
        }
    }
    
    /**
     * 切换清晰度
     */
    /**
     * DRM 加密流（课程高码率档常见）当前播放器不支持解密。对齐 PiliPlus 的实际表现：
     * 低档清晰度（qn=80 蓝光）下发的是未加密流，因此收到 DRM 错误时自动降档重试一次。
     */
    fun handlePlaybackDrmError() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        if (currentState.quality > 80) {
            viewModelScope.launch {
                _toastEvent.send("当前清晰度受版权保护，已切换为蓝光")
                changeQuality(80)
            }
        } else {
            viewModelScope.launch {
                _toastEvent.send("该内容受版权保护，暂时无法播放")
            }
        }
    }

    fun changeQuality(qualityId: Int) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val currentPos = getPlayerCurrentPosition()
        
        viewModelScope.launch {
            val isCourse = currentState.seasonDetail.seasonType == 10
            val playUrlResult = BangumiRepository.getBangumiPlayUrl(
                epId = currentState.currentEpisode.id,
                qn = qualityId,
                cid = currentState.currentEpisode.cid,
                bvid = currentState.currentEpisode.bvid,
                seasonId = currentState.seasonDetail.seasonId,
                aid = currentState.currentEpisode.aid,
                isCourse = isCourse
            )
            
            playUrlResult.onSuccess { playData ->
                val videoUrl: String?
                val audioUrl: String?
                val dashManifest: String?
                val durlSegmentUrls: List<String>
                val dash = playData.dash
                val audioSelection = dash?.let {
                    resolveAudioStreamSelection(
                        dash = it,
                        requestedAudioQuality = currentState.requestedAudioQuality,
                        playbackSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f,
                        isDolbyAudioSupported = MediaUtils.isDolbyAtmosAudioSupported(),
                        isDolbyAudioSoftwareDecoded = MediaUtils.isDolbySoftwareAudioDecoderRequired()
                    )
                }
                
                if (dash != null) {
                    //  设备支持 HEVC 时优先 hev1（HDR/杜比视界轨道基本为 HEVC），否则回退 avc1 保证可解码
                    val video = dash.getBestVideo(
                        qualityId,
                        preferCodec = resolveBangumiPreferredCodec(isCourse)
                    )
                    val audio = audioSelection?.selected?.track
                    videoUrl = video?.getValidUrl()
                    audioUrl = audio?.getValidUrl()
                    dashManifest = buildBangumiDashManifest(
                        dash = dash,
                        video = video,
                        videoUrl = videoUrl,
                        audio = audio,
                        audioUrl = audioUrl,
                        durationMs = playData.timelength.takeIf { it > 0L }
                            ?: dash.duration.toLong() * 1000L
                    )
                    durlSegmentUrls = emptyList()
                } else {
                    durlSegmentUrls = collectPlayableDurlUrls(
                        when {
                            !playData.durl.isNullOrEmpty() -> playData.durl
                            !playData.durls.isNullOrEmpty() -> playData.durls
                            else -> null
                        }
                    )
                    videoUrl = durlSegmentUrls.firstOrNull()
                    audioUrl = null
                    dashManifest = null
                }
                
                if (videoUrl.isNullOrEmpty()) return@onSuccess
                val qualityOptions = resolveBangumiQualityOptions(playData)
                
                _uiState.value = currentState.copy(
                    playUrl = videoUrl,
                    audioUrl = audioUrl,
                    quality = playData.quality,
                    acceptQuality = qualityOptions.ids.ifEmpty { currentState.acceptQuality },
                    acceptDescription = qualityOptions.labels.ifEmpty { currentState.acceptDescription },
                    cachedDash = dash,
                    requestedAudioQuality = currentState.requestedAudioQuality,
                    selectedAudioQuality = audioSelection?.selectedPreferenceId ?: -1,
                    availableAudioQualities = audioSelection?.availableOptions.orEmpty(),
                    audioFallbackReason = audioSelection?.fallbackReason
                )
                
                //  [修复] 切换清晰度时使用 resetPlayer=false 减少闪烁，并传入正确业务 Referer
                val referer = if (isCourse) {
                    "https://www.bilibili.com/cheese/play/ep${currentState.currentEpisode.id}"
                } else {
                    "https://www.bilibili.com/bangumi/play/ep${currentState.currentEpisode.id}"
                }
                val playWhenReady = exoPlayer?.playWhenReady ?: true
                if (audioUrl.isNullOrEmpty() && durlSegmentUrls.size > 1) {
                    playSegmentedVideo(
                        segmentUrls = durlSegmentUrls,
                        seekToMs = currentPos,
                        resetPlayer = false,
                        referer = referer
                    )
                } else {
                    playDashVideo(
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        seekToMs = currentPos,
                        resetPlayer = false,
                        referer = referer,
                        dashManifest = dashManifest
                    )
                }
                exoPlayer?.playWhenReady = playWhenReady
            }
        }
    }

    /**
     * 切换音质。只有播放器源成功替换后才写入长期记忆。
     */
    fun changeAudioQuality(audioQuality: Int) {
        viewModelScope.launch {
            val switched = switchAudioQuality(audioQuality)
            if (!switched) {
                _toastEvent.send("音质切换失败，请稍后重试")
                return@launch
            }

            NetworkModule.appContext?.let { context ->
                SettingsManager.setAudioQuality(context, audioQuality)
            }
            sendAudioSelectionToast()
        }
    }

    /**
     * 处理 Hi-Res/杜比在高倍速下的临时回退，并在回到兼容倍速时恢复用户选择。
     */
    fun applyPlaybackSpeedFromUi(speed: Float) {
        val player = exoPlayer ?: return
        val previousSpeed = player.playbackParameters.speed
        val normalizedSpeed = speed.coerceAtLeast(0.1f)
        player.setPlaybackSpeed(normalizedSpeed)

        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        if (!shouldRefreshPremiumAudioForPlaybackSpeedChange(
                requestedAudioQuality = currentState.requestedAudioQuality,
                previousPlaybackSpeed = previousSpeed,
                nextPlaybackSpeed = normalizedSpeed
            )
        ) {
            return
        }

        viewModelScope.launch {
            switchAudioQuality(currentState.requestedAudioQuality)
        }
    }

    private fun switchAudioQuality(audioQuality: Int): Boolean {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return false
        val player = exoPlayer ?: return false
        val dash = currentState.cachedDash ?: return false
        val videoUrl = currentState.playUrl?.takeIf { it.isNotBlank() } ?: return false
        val selection = resolveAudioStreamSelection(
            dash = dash,
            requestedAudioQuality = audioQuality,
            playbackSpeed = player.playbackParameters.speed,
            isDolbyAudioSupported = MediaUtils.isDolbyAtmosAudioSupported(),
            isDolbyAudioSoftwareDecoded = MediaUtils.isDolbySoftwareAudioDecoderRequired()
        )
        val audioUrl = selection.selected?.track?.getValidUrl()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val selectedVideo = dash.video.firstOrNull { it.getValidUrl() == videoUrl }
            ?: dash.getBestVideo(
                currentState.quality,
                preferCodec = resolveBangumiPreferredCodec(currentState.seasonDetail.seasonType == 10)
            )
        val dashManifest = buildBangumiDashManifest(
            dash = dash,
            video = selectedVideo,
            videoUrl = videoUrl,
            audio = selection.selected?.track,
            audioUrl = audioUrl,
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
        )
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val referer = if (currentState.seasonDetail.seasonType == 10) {
            "https://www.bilibili.com/cheese/play/ep${currentState.currentEpisode.id}"
        } else {
            "https://www.bilibili.com/bangumi/play/ep${currentState.currentEpisode.id}"
        }

        playDashVideo(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            seekToMs = currentPosition,
            resetPlayer = false,
            referer = referer,
            dashManifest = dashManifest
        )
        player.playWhenReady = playWhenReady
        _uiState.value = currentState.copy(
            audioUrl = audioUrl,
            requestedAudioQuality = audioQuality,
            selectedAudioQuality = selection.selectedPreferenceId,
            availableAudioQualities = selection.availableOptions,
            audioFallbackReason = selection.fallbackReason
        )
        return true
    }

    private suspend fun sendAudioSelectionToast() {
        val state = _uiState.value as? BangumiPlayerState.Success ?: return
        val selectedLabel = state.availableAudioQualities
            .firstOrNull { it.preferenceId == state.selectedAudioQuality }
            ?.label
            ?: "AAC"
        val message = when (state.audioFallbackReason) {
            AudioFallbackReason.SPEED_INCOMPATIBLE ->
                "当前倍速暂不支持所选音质，已临时使用 $selectedLabel"
            AudioFallbackReason.REQUESTED_UNAVAILABLE ->
                "当前视频不支持所选音质，已使用 $selectedLabel"
            AudioFallbackReason.DECODER_ERROR ->
                "当前设备无法稳定解码所选音质，已临时使用 $selectedLabel"
            AudioFallbackReason.NO_PLAYABLE_AUDIO -> "当前视频没有可用音轨"
            null -> "✓ 已切换至 $selectedLabel"
        }
        _toastEvent.send(message)
    }
    
    /**
     * 追番/取消追番
     */
    fun toggleFollow() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val isFollowing = isBangumiFollowed(currentState.seasonDetail.userStatus)
        updateFollowStatus(
            status = if (isFollowing) {
                BANGUMI_FOLLOW_STATUS_UNFOLLOW
            } else {
                BANGUMI_FOLLOW_STATUS_WATCHING
            }
        )
    }

    fun updateFollowStatus(status: Int) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val seasonId = currentState.seasonDetail.seasonId
        val wasFollowing = isBangumiFollowed(currentState.seasonDetail.userStatus) ||
            followStatusCache[seasonId] == true ||
            (followStatusValueCache[seasonId] ?: 0) > 0
        val isCourse = isCourseMode || currentState.seasonDetail.seasonType == 10
        
        viewModelScope.launch {
            val result = when {
                status == BANGUMI_FOLLOW_STATUS_UNFOLLOW -> {
                    BangumiRepository.unfollowBangumi(seasonId, isCourse = isCourse)
                }
                wasFollowing && !isCourse -> {
                    BangumiRepository.updateBangumiFollowStatus(seasonId, status)
                }
                status == BANGUMI_FOLLOW_STATUS_WATCHING || isCourse -> {
                    BangumiRepository.followBangumi(seasonId, isCourse = isCourse)
                }
                else -> {
                    val followResult = BangumiRepository.followBangumi(seasonId, isCourse = isCourse)
                    if (followResult.isSuccess) {
                        BangumiRepository.updateBangumiFollowStatus(seasonId, status)
                    } else {
                        Result.failure(followResult.exceptionOrNull() ?: Exception("追番失败"))
                    }
                }
            }
            
            if (result.isSuccess) {
                //  [修复] 立即更新本地状态，不等待重新获取
                val newIsFollowing = status != BANGUMI_FOLLOW_STATUS_UNFOLLOW
                followStatusCache[seasonId] = newIsFollowing
                if (newIsFollowing) {
                    followedSeasonIds.add(seasonId)
                    followStatusValueCache[seasonId] = status
                } else {
                    followedSeasonIds.remove(seasonId)
                    followStatusValueCache.remove(seasonId)
                }
                val updatedUserStatus = currentState.seasonDetail.userStatus?.copy(
                    follow = if (newIsFollowing) 1 else 0,
                    followStatus = if (newIsFollowing) {
                        status
                    } else {
                        0
                    }
                ) ?: UserStatus(
                    follow = if (newIsFollowing) 1 else 0,
                    followStatus = if (newIsFollowing) 1 else 0
                )
                val updatedDetail = currentState.seasonDetail.copy(userStatus = updatedUserStatus)
                _uiState.value = currentState.copy(seasonDetail = updatedDetail)
                
                //  显示 Toast 反馈
                _toastEvent.send(
                    if (newIsFollowing) {
                        if (isCourse) "收藏成功" else "已标记为${resolveBangumiFollowStatusLabel(updatedUserStatus)}"
                    } else {
                        if (isCourse) "已取消收藏" else "已取消追番"
                    }
                )
            } else {
                _toastEvent.send("操作失败，请重试")
            }
        }
    }

    fun toggleLike() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val aid = currentState.currentEpisode.aid
        if (aid <= 0L) {
            viewModelScope.launch {
                _toastEvent.send("当前剧集暂不支持点赞")
            }
            return
        }

        viewModelScope.launch {
            val result = interactionUseCase.toggleLike(
                aid = aid,
                currentlyLiked = currentState.isLiked,
                bvid = currentState.currentEpisode.bvid
            )
            result.onSuccess { liked ->
                val latestState = _uiState.value as? BangumiPlayerState.Success ?: currentState
                if (latestState.currentEpisode.id != currentState.currentEpisode.id) return@onSuccess
                _uiState.value = updateBangumiSuccessInteractionState(
                    state = latestState,
                    isLiked = liked,
                    coinCount = latestState.coinCount
                )
                _toastEvent.trySend(if (liked) "已点赞" else "已取消点赞")
            }.onFailure { error ->
                _toastEvent.trySend(error.message ?: "点赞失败")
            }
        }
    }

    fun openCoinDialog() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        if (currentState.currentEpisode.aid <= 0L) {
            viewModelScope.launch {
                _toastEvent.send("当前剧集暂不支持投币")
            }
            return
        }
        if (currentState.coinCount >= 2) {
            viewModelScope.launch {
                _toastEvent.send("已投满2个硬币")
            }
            return
        }
        _coinDialogVisible.value = true
        fetchUserCoins()
    }

    fun closeCoinDialog() {
        _coinDialogVisible.value = false
    }

    fun doCoin(count: Int, alsoLike: Boolean) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val aid = currentState.currentEpisode.aid
        if (aid <= 0L) {
            viewModelScope.launch {
                _toastEvent.send("当前剧集暂不支持投币")
            }
            return
        }

        _coinDialogVisible.value = false
        viewModelScope.launch {
            val result = interactionUseCase.doCoin(
                aid = aid,
                count = count,
                alsoLike = alsoLike,
                bvid = currentState.currentEpisode.bvid
            )
            result.onSuccess {
                val latestState = _uiState.value as? BangumiPlayerState.Success ?: currentState
                if (latestState.currentEpisode.id != currentState.currentEpisode.id) return@onSuccess
                _uiState.value = applyBangumiCoinResult(
                    state = latestState,
                    coinDelta = count,
                    alsoLike = alsoLike
                )
                _toastEvent.trySend("投币成功")
            }.onFailure { error ->
                _toastEvent.trySend(error.message ?: "投币失败")
            }
        }
    }
    
    /**
     * 重试
     */
    suspend fun sendEpisodeDanmaku(message: String, color: Int, mode: Int, fontSize: Int): Result<Unit> {
        val state = _uiState.value as? BangumiPlayerState.Success
            ?: return Result.failure(IllegalStateException("剧集未加载"))
        if (!state.isLoggedIn) return Result.failure(IllegalStateException("请先登录"))
        if (state.currentEpisode.aid <= 0 || state.currentEpisode.cid <= 0) {
            return Result.failure(IllegalStateException("该剧集不支持发送弹幕"))
        }
        if (!canShowBangumiDanmaku(state.seasonDetail.rights) ||
            com.android.purebilibili.data.repository.DanmakuRepository.isDanmakuServerDisabled(state.currentEpisode.cid)) {
            return Result.failure(IllegalStateException("该剧集已关闭弹幕"))
        }
        val gradual = color == com.android.purebilibili.feature.video.ui.components.DANMAKU_SEND_VIP_GRADUAL_COLOR
        return com.android.purebilibili.data.repository.DanmakuRepository.sendDanmaku(
            aid = state.currentEpisode.aid, cid = state.currentEpisode.cid, message = message,
            progress = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            color = if (gradual) 16777215 else color, colorful = gradual, mode = mode, fontSize = fontSize,
        ).map { Unit }
    }

    fun saveCover(context: android.content.Context) {
        val state = _uiState.value as? BangumiPlayerState.Success ?: return
        val cover = state.currentEpisode.cover.ifBlank { state.seasonDetail.cover }
        viewModelScope.launch {
            val saved = com.android.purebilibili.feature.download.DownloadManager.saveImageToGallery(
                context, cover, state.seasonDetail.title
            )
            _toastEvent.send(if (saved) "封面已保存到相册" else "保存失败")
        }
    }

    fun downloadAudio() {
        val state = _uiState.value as? BangumiPlayerState.Success ?: return
        val audioUrl = state.audioUrl?.takeIf { it.isNotBlank() }
        if (audioUrl == null) {
            viewModelScope.launch { _toastEvent.send("该剧集没有可下载的独立音频") }
            return
        }
        val episode = state.currentEpisode
        val task = com.android.purebilibili.feature.download.DownloadTask(
            aid = episode.aid, bvid = episode.bvid.ifBlank { "ep${episode.id}" }, cid = episode.cid,
            title = state.seasonDetail.title,
            episodeLabel = listOf(episode.title, episode.longTitle).filter { it.isNotBlank() }.joinToString(" "),
            groupKey = "bangumi-audio:${state.seasonDetail.seasonId}",
            groupTitle = state.seasonDetail.title,
            episodeSortIndex = state.currentEpisodeIndex,
            episodeCount = state.seasonDetail.episodes.orEmpty().size.coerceAtLeast(1),
            cover = episode.cover.ifBlank { state.seasonDetail.cover },
            ownerName = state.seasonDetail.title, ownerFace = "",
            duration = (episode.duration / 1000L).toInt(), quality = 0, qualityDesc = "音频",
            videoUrl = "", audioUrl = audioUrl, isAudioOnly = true,
        )
        viewModelScope.launch {
            val started = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
            _toastEvent.send(if (started) "已开始下载音频" else "该音频已在下载中或已完成")
        }
    }

    fun retry() {
        loadBangumiPlay(currentSeasonId, currentEpId, isCourse = isCourseMode)
    }

    private fun startBangumiPlaybackHeartbeat(
        detail: BangumiDetail,
        episode: BangumiEpisode
    ) {
        bangumiHeartbeatJob?.cancel()
        // 部分番剧集没有 bvid，历史/进度上报仍应进行（epid/sid 维度）
        val bvid = episode.bvid
        bangumiHeartbeatJob = viewModelScope.launch {
            while (isActive) {
                reportBangumiPlaybackHeartbeat(detail, episode, bvid)
                delay(BANGUMI_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun flushBangumiPlaybackHeartbeat() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        viewModelScope.launch {
            reportBangumiPlaybackHeartbeat(
                detail = currentState.seasonDetail,
                episode = currentState.currentEpisode,
                bvid = currentState.currentEpisode.bvid,
                requirePlaying = false
            )
        }
    }

    private suspend fun reportBangumiPlaybackHeartbeat(
        detail: BangumiDetail,
        episode: BangumiEpisode,
        bvid: String,
        requirePlaying: Boolean = true
    ) {
        val currentPositionMs = getPlayerCurrentPosition()
        if (bvid.isNotBlank()) {
            progressManager?.savePosition(
                bvid = bvid,
                cid = episode.cid,
                positionMs = currentPositionMs,
                durationMs = getPlayerDuration()
            )
        }
        val isPlaying = if (requirePlaying) exoPlayer?.isPlaying == true else true
        if (!shouldSendBangumiPlaybackHeartbeat(
                isPlaying = isPlaying,
                bvid = bvid,
                cid = episode.cid,
                currentPositionMs = currentPositionMs,
                epid = episode.id,
                sid = detail.seasonId
            )
        ) {
            return
        }
        try {
            com.android.purebilibili.data.repository.VideoRepository.reportPlayHeartbeat(
                bvid = bvid,
                cid = episode.cid,
                playedTime = currentPositionMs.coerceAtLeast(0L) / 1000L,
                aid = episode.aid,
                epid = episode.id,
                sid = detail.seasonId,
                videoType = 4,
                subType = detail.seasonType.takeIf { it > 0 }
            )
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "Heartbeat reported for bangumi: $bvid cid=${episode.cid}")
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "Heartbeat failed: ${e.message}")
        }
    }

    private suspend fun ensureFollowedSeasonsLoaded(type: Int): Int {
        if (loadedFollowTypes.contains(type)) return 0
        val preloadResult = preloadFollowedSeasonsForType(
            type = type,
            followedSeasonIds = followedSeasonIds
        )
        if (preloadResult.requestSucceeded) {
            loadedFollowTypes.add(type)
        }
        return preloadResult.total
    }

    private fun refreshEpisodeInteractionState(
        episodeAid: Long,
        episodeId: Long
    ) {
        if (episodeAid <= 0L) return

        viewModelScope.launch {
            val isLiked = runCatching {
                ActionRepository.checkLikeStatus(episodeAid)
            }.getOrDefault(false)
            val coinCount = runCatching {
                ActionRepository.checkCoinStatus(episodeAid)
            }.getOrDefault(0)

            val currentState = _uiState.value as? BangumiPlayerState.Success ?: return@launch
            if (currentState.currentEpisode.id != episodeId || currentState.currentEpisode.aid != episodeAid) {
                return@launch
            }

            _uiState.value = updateBangumiSuccessInteractionState(
                state = currentState,
                isLiked = isLiked,
                coinCount = coinCount
            )
        }
    }

    private fun fetchUserCoins() {
        viewModelScope.launch {
            _userCoinBalance.value = null
            try {
                if (TokenManager.sessDataCache.isNullOrEmpty()) {
                    _userCoinBalance.value = -4.0
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    withTimeout(5_000L) {
                        NetworkModule.api.getNavInfo()
                    }
                }

                _userCoinBalance.value = when {
                    result.code == 0 && result.data?.isLogin == true -> requireNotNull(result.data).money
                    result.code == 0 -> -3.0
                    else -> -1.0
                }
            } catch (_: Exception) {
                _userCoinBalance.value = -2.0
            }
        }
    }
}
