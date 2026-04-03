// File: feature/video/usecase/VideoPlaybackUseCase.kt
package com.android.purebilibili.feature.video.usecase

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import com.android.purebilibili.core.cooldown.CooldownStatus
import com.android.purebilibili.core.cooldown.PlaybackCooldownManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.feature.video.playback.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.feature.video.playback.dash.buildLocalDashManifest
import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode
import com.android.purebilibili.feature.video.playback.policy.buildAdaptiveDashTrackSet
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.video.controller.PlaybackProgressManager
import com.android.purebilibili.feature.video.controller.QualityManager
import com.android.purebilibili.feature.video.playback.session.PlaybackUserActionTracker
import com.android.purebilibili.feature.video.ui.overlay.PlaybackUserActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Video Playback UseCase
 * 
 * Handles video loading, playback, quality switching, and page switching.
 */

/**
 * Video playback result
 */
sealed class VideoLoadResult {
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val audioUrl: String?,
        val related: List<RelatedVideo>,
        val quality: Int,
        val qualityIds: List<Int>,
        val qualityLabels: List<String>,
        val cachedDashVideos: List<DashVideo>,
        val cachedDashAudios: List<DashAudio>,
        val emoteMap: Map<String, String>,
        val isLoggedIn: Boolean,
        val isVip: Boolean,
        val isFollowing: Boolean,
        val isFavorited: Boolean,
        val isLiked: Boolean,
        val coinCount: Int,
        // [New] Duration (ms) from PlayUrlData
        val duration: Long = 0,
        // [New] Codec Info for UI display
        val videoCodecId: Int = 0,
        val audioCodecId: Int = 0,
        // [New] AI Translation Info
        val aiAudio: AiAudioInfo? = null,
        val curAudioLang: String? = null,
        val adaptiveDashSource: AdaptiveDashPlaybackSource? = null
    ) : VideoLoadResult()
    
    data class Error(
        val error: VideoLoadError,
        val canRetry: Boolean = true
    ) : VideoLoadResult()
}

/**
 * Quality switch result
 */
data class QualitySwitchResult(
    val videoUrl: String,
    val audioUrl: String?,
    val actualQuality: Int,
    val wasFallback: Boolean,
    val adaptiveDashSource: AdaptiveDashPlaybackSource? = null,
    val cachedDashVideos: List<DashVideo>,
    val cachedDashAudios: List<DashAudio>,
    val qualityIds: List<Int> = emptyList(),
    val qualityLabels: List<String> = emptyList()
)

data class PlaybackSelectionResult(
    val videoUrl: String,
    val audioUrl: String?,
    val actualQuality: Int,
    val isDashPlayback: Boolean,
    val adaptiveDashSource: AdaptiveDashPlaybackSource? = null,
    val cachedDashVideos: List<DashVideo>,
    val cachedDashAudios: List<DashAudio>,
    val qualityIds: List<Int>,
    val qualityLabels: List<String>
)

internal fun shouldPreparePlayerOnLoad(playWhenReady: Boolean): Boolean = true

// TODO: Re-enable local adaptive DASH playback after the generated MPD seek path
// is validated on real devices and covered by a playback-level regression test.
private const val LOCAL_ADAPTIVE_DASH_PLAYBACK_ENABLED = false

internal fun shouldUseAdaptiveDashPlayback(
    adaptiveDashSource: AdaptiveDashPlaybackSource?,
    audioUrl: String?
): Boolean {
    // Local MPD playback regressed manual seeks on real devices in v7.3.2:
    // the UI position updates, but decoded video jumps back to the opening frame.
    // Keep building the adaptive source for future recovery work, but route
    // playback through the previous legacy DASH path until seek stability is fixed.
    return LOCAL_ADAPTIVE_DASH_PLAYBACK_ENABLED && adaptiveDashSource != null
}

internal fun resolveLocalDashManifestFileName(manifest: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(manifest.toByteArray())
    val key = digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "local_dash_$key.mpd"
}

internal fun shouldPreparePlayerBeforeExplicitPlay(
    playbackState: Int,
    hasMediaItems: Boolean
): Boolean {
    return hasMediaItems && playbackState == Player.STATE_IDLE
}

internal fun playPlayerFromUserAction(player: Player) {
    playPlayerForUserIntent(player, trackUserAction = true)
}

private fun playPlayerForUserIntent(
    player: Player,
    trackUserAction: Boolean
) {
    if (trackUserAction) {
        PlaybackUserActionTracker.recordAction(
            player = player,
            type = PlaybackUserActionType.PLAY
        )
    }
    Logger.d(
        "VideoPlaybackUseCase",
        "USER_DBG playPlayerFromUserAction before: " +
            "state=${player.playbackState}, isPlaying=${player.isPlaying}, " +
            "playWhenReady=${player.playWhenReady}, mediaItemCount=${player.mediaItemCount}, pos=${player.currentPosition}"
    )
    if (shouldPreparePlayerBeforeExplicitPlay(player.playbackState, player.mediaItemCount > 0)) {
        player.prepare()
    }
    player.play()
    Logger.d(
        "VideoPlaybackUseCase",
        "USER_DBG playPlayerFromUserAction after: " +
            "state=${player.playbackState}, isPlaying=${player.isPlaying}, " +
            "playWhenReady=${player.playWhenReady}, pos=${player.currentPosition}"
    )
}

internal fun shouldResumePlaybackAfterUserSeek(
    playWhenReadyBeforeSeek: Boolean,
    playbackStateBeforeSeek: Int
): Boolean {
    return playWhenReadyBeforeSeek || playbackStateBeforeSeek == Player.STATE_ENDED
}

internal fun seekPlayerFromUserAction(
    player: Player,
    positionMs: Long,
    shouldResumePlaybackOverride: Boolean? = null
) {
    val shouldResume = shouldResumePlaybackOverride ?: shouldResumePlaybackAfterUserSeek(
        playWhenReadyBeforeSeek = player.playWhenReady,
        playbackStateBeforeSeek = player.playbackState
    )
    Logger.d(
        "VideoPlaybackUseCase",
        "USER_DBG seekPlayerFromUserAction: target=$positionMs, shouldResume=$shouldResume, " +
            "beforeState=${player.playbackState}, beforePlaying=${player.isPlaying}, beforePwr=${player.playWhenReady}"
    )
    player.seekTo(positionMs)
    if (shouldResume) {
        playPlayerForUserIntent(player, trackUserAction = false)
    }
}

internal fun togglePlayerPlaybackFromUserAction(player: Player) {
    Logger.d(
        "VideoPlaybackUseCase",
        "USER_DBG togglePlayerPlaybackFromUserAction before: " +
            "state=${player.playbackState}, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, pos=${player.currentPosition}"
    )
    if (player.playbackState == Player.STATE_ENDED) {
        Logger.d(
            "VideoPlaybackUseCase",
            "USER_DBG togglePlayerPlaybackFromUserAction restart from beginning"
        )
        player.seekTo(0L)
        playPlayerFromUserAction(player)
        return
    }
    if (player.isPlaying) {
        PlaybackUserActionTracker.recordAction(
            player = player,
            type = PlaybackUserActionType.PAUSE
        )
        player.pause()
        Logger.d(
            "VideoPlaybackUseCase",
            "USER_DBG togglePlayerPlaybackFromUserAction paused: " +
                "state=${player.playbackState}, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, pos=${player.currentPosition}"
        )
        return
    }
    playPlayerFromUserAction(player)
}

class VideoPlaybackUseCase(
    private var progressManager: PlaybackProgressManager = PlaybackProgressManager(),
    private val qualityManager: QualityManager = QualityManager()
) {

    companion object {
        private val STANDARD_LOW_QUALITIES = listOf(32, 16)
        private const val API_ONLY_VISIBLE_QUALITY_FLOOR = 80
        private const val PREMIUM_API_ONLY_QUALITY_FLOOR = 112
    }

    internal data class QualityMergeResult(
        val switchableQualities: List<Int>,
        val apiOnlyHighQualities: List<Int>,
        val mergedQualityIds: List<Int>
    )

    internal data class QualitySelectionState(
        val qualityIds: List<Int>,
        val qualityLabels: List<String>
    )
    
    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    
    /**
     * Initialize with context for persistent progress storage
     */
    fun initWithContext(context: android.content.Context) {
        appContext = context.applicationContext
        progressManager = PlaybackProgressManager.getInstance(context)
    }
    
    /**
     * Attach ExoPlayer instance
     */
    fun attachPlayer(player: ExoPlayer) {
        exoPlayer = player
        player.volume = 1.0f
    }
    
    /**
     * Load video data
     * 
     * @param defaultQuality 网络感知的默认清晰度 (WiFi=80/1080P, Mobile=64/720P)
     * @param aid [修复] 视频 aid，用于移动端推荐流（可能只返回 aid）
     */
    suspend fun loadVideo(
        bvid: String,
        aid: Long = 0,  // [修复] 新增 aid 参数
        cid: Long = 0L,
        defaultQuality: Int = 64,
        audioQualityPreference: Int = -1,

        videoCodecPreference: String = "hev1",
        videoSecondCodecPreference: String = "avc1",
        audioLang: String? = null, // [New] AI Translation Language

        playWhenReady: Boolean = true,  // [Added] Control auto-play
        isAv1SupportedOverride: Boolean? = null,
        isHdrSupportedOverride: Boolean? = null,
        isDolbyVisionSupportedOverride: Boolean? = null,
        onProgress: (String) -> Unit = {}
    ): VideoLoadResult {
        try {
            //  [风控冷却] 检查是否处于冷却期
            val videoIdentifier = bvid.ifEmpty { "aid:$aid" }
            when (val cooldownStatus = PlaybackCooldownManager.getCooldownStatus(videoIdentifier)) {
                is CooldownStatus.GlobalCooldown -> {
                    Logger.w("VideoPlaybackUseCase", "⏳ 全局冷却中，跳过请求: ${cooldownStatus.remainingMinutes}分${cooldownStatus.remainingSeconds}秒")
                    return VideoLoadResult.Error(
                        error = VideoLoadError.GlobalCooldown(
                            cooldownStatus.remainingMs, 
                            PlaybackCooldownManager.getConsecutiveFailures()
                        ),
                        canRetry = false
                    )
                }
                is CooldownStatus.VideoCooldown -> {
                    Logger.w("VideoPlaybackUseCase", "⏳ 视频冷却中: $videoIdentifier，剩余 ${cooldownStatus.remainingMinutes}分${cooldownStatus.remainingSeconds}秒")
                    return VideoLoadResult.Error(
                        error = VideoLoadError.RateLimited(cooldownStatus.remainingMs, videoIdentifier),
                        canRetry = false
                    )
                }
                is CooldownStatus.Ready -> {
                    // 可以继续请求
                }
            }
            
            onProgress("Loading video info...")
            
            //  [性能优化] 并行请求视频详情、相关推荐。
            // 表情映射在首帧链路中跳过，避免自动播放起播被非关键请求阻塞。
            val (detailResult, relatedVideos, emoteMap) = kotlinx.coroutines.coroutineScope {
                val detailDeferred = async {
                    VideoRepository.getVideoDetails(
                        bvid = bvid,
                        aid = aid,
                        requestedCid = cid,
                        targetQuality = defaultQuality,
                        audioLang = audioLang
                    )
                }
                val relatedDeferred = async { 
                    if (bvid.isNotEmpty()) VideoRepository.getRelatedVideos(bvid) else emptyList() 
                }
                val emoteMap = if (com.android.purebilibili.data.repository.shouldFetchCommentEmoteMapOnVideoLoad()) {
                    com.android.purebilibili.data.repository.CommentRepository.getEmoteMap()
                } else {
                    emptyMap()
                }
                
                Triple(detailDeferred.await(), relatedDeferred.await(), emoteMap)
            }
            
            return detailResult.fold(
                onSuccess = { (info, playData) ->
                    val isLogin = com.android.purebilibili.data.repository.resolveVideoPlaybackAuthState(
                        hasSessionCookie = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty(),
                        hasAccessToken = !com.android.purebilibili.core.store.TokenManager.accessTokenCache.isNullOrEmpty()
                    )
                    var isVip = com.android.purebilibili.core.store.TokenManager.isVipCache
                    if (isLogin && !isVip && com.android.purebilibili.data.repository.shouldRefreshVipStatusOnVideoLoad()) {
                        try {
                            val navResult = VideoRepository.getNavInfo()
                            navResult.onSuccess { navData ->
                                isVip = navData.vip.status == 1
                                com.android.purebilibili.core.store.TokenManager.isVipCache = isVip
                                Logger.d("VideoPlaybackUseCase", " Refreshed VIP status: $isVip")
                            }
                        } catch (e: Exception) {
                            Logger.d("VideoPlaybackUseCase", " Failed to refresh VIP status: ${e.message}")
                        }
                    }

                    //  [网络感知] 使用 API 返回的画质或传入的默认画质
                    // 🚀 [修复] 当 defaultQuality >= 127 时（自动最高画质），选择 accept_quality 中的最高画质
                    val targetQn = if (defaultQuality >= 127) {
                        val isHdrSupported = isHdrSupportedOverride
                            ?: com.android.purebilibili.core.util.MediaUtils.isHdrSupported()
                        val isDolbyVisionSupported = isDolbyVisionSupportedOverride
                            ?: com.android.purebilibili.core.util.MediaUtils.isDolbyVisionSupported()
                        val maxAccept = resolveAutoHighestTargetQuality(
                            acceptQualities = playData.accept_quality,
                            isLoggedIn = isLogin,
                            isVip = isVip,
                            isHdrSupported = isHdrSupported,
                            isDolbyVisionSupported = isDolbyVisionSupported
                        )
                        Logger.d(
                            "VideoPlaybackUseCase",
                            "🚀 自动最高画质: accept_quality=${playData.accept_quality}, isLoggedIn=$isLogin, isVip=$isVip, 设备支持HDR=$isHdrSupported, 杜比=$isDolbyVisionSupported, 选择 $maxAccept"
                        )
                        maxAccept
                    } else {
                        // 🚀 [修复] 优先使用用户设置的 defaultQuality，而不是 API 返回的 playData.quality
                        if (defaultQuality > 0) defaultQuality else playData.quality
                    }
                    
                    val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
                    val isAv1Supported = isAv1SupportedOverride
                        ?: com.android.purebilibili.core.util.MediaUtils.isAv1Supported()

                    val selection = resolvePlaybackSelection(
                        playUrlData = playData,
                        targetQuality = targetQn,
                        audioQualityPreference = audioQualityPreference,
                        videoCodecPreference = videoCodecPreference,
                        videoSecondCodecPreference = videoSecondCodecPreference,
                        isHevcSupported = isHevcSupported,
                        isAv1Supported = isAv1Supported
                    )

                    if (selection == null) {
                        PlaybackCooldownManager.recordFailure(bvid, "播放地址为空")
                        return@fold VideoLoadResult.Error(
                            error = VideoLoadError.PlayUrlEmpty,
                            canRetry = true
                        )
                    }
                    
                    PlaybackCooldownManager.recordSuccess(bvid)
                    
                    // [New] 本地强制解锁 VIP 状态 - REVERTED
                    // val isUnlockHighQuality = ...
                    
                    val isEffectiveVip = isVip // || isUnlockHighQuality
                    // if (isUnlockHighQuality) ...
                    
                    //  [修复] 画质列表优先使用 DASH 实际轨道，避免展示“可选但不可切”的画质。
                    val apiQualities = playData.accept_quality
                    val dashVideoIds = playData.dash?.video?.map { it.id }?.distinct() ?: emptyList()

                    val allowPremiumApiOnlyQualities = !VideoRepository.isAppApiCoolingDown()
                    val qualityMergeResult = mergeQualityOptions(
                        apiQualities = apiQualities,
                        dashVideoIds = dashVideoIds,
                        allowPremiumApiOnlyQualities = allowPremiumApiOnlyQualities
                    )
                    val qualitySelectionState = buildQualitySelectionState(
                        apiQualities = apiQualities,
                        dashVideoIds = dashVideoIds,
                        allowPremiumApiOnlyQualities = allowPremiumApiOnlyQualities
                    )
                    
                    Logger.d(
                        "VideoPlaybackUseCase",
                        " Quality merge: api=$apiQualities, dash=$dashVideoIds, switchable=${qualityMergeResult.switchableQualities}, apiOnlyHigh=${qualityMergeResult.apiOnlyHighQualities}, merged=${qualitySelectionState.qualityIds}"
                    )
                    Logger.d(
                        "VideoPlaybackUseCase",
                        buildPlaybackSelectionSummary(
                            bvid = info.bvid.ifBlank { bvid },
                            cid = info.cid,
                            defaultQuality = defaultQuality,
                            targetQuality = targetQn,
                            returnedQuality = playData.quality,
                            selectedDashQuality = selection.actualQuality.takeIf { selection.isDashPlayback },
                            mergedQualityIds = qualitySelectionState.qualityIds,
                            isLoggedIn = isLogin,
                            isVip = isEffectiveVip
                        )
                    )
                    
                    // 首帧优先：交互状态默认值先返回，延后到 ViewModel 后台刷新。
                    val (isFollowing, isFavorited, isLiked, coinCount) = if (
                        isLogin && com.android.purebilibili.data.repository.shouldFetchInteractionStatusOnVideoLoad()
                    ) {
                        coroutineScope {
                            val followingDeferred = async { ActionRepository.checkFollowStatus(info.owner.mid) }
                            val favoritedDeferred = async { ActionRepository.checkFavoriteStatus(info.aid) }
                            val likedDeferred = async { ActionRepository.checkLikeStatus(info.aid) }
                            val coinDeferred = async { ActionRepository.checkCoinStatus(info.aid) }
                            Quadruple(
                                followingDeferred.await(),
                                favoritedDeferred.await(),
                                likedDeferred.await(),
                                coinDeferred.await()
                            )
                        }
                    } else {
                        Quadruple(false, false, false, 0)
                    }
                    
                    VideoLoadResult.Success(
                        info = info,
                        playUrl = selection.videoUrl,
                        audioUrl = selection.audioUrl,
                        related = relatedVideos,
                        quality = selection.actualQuality,
                        qualityIds = selection.qualityIds,
                        qualityLabels = selection.qualityLabels,
                        cachedDashVideos = selection.cachedDashVideos,
                        cachedDashAudios = selection.cachedDashAudios,
                        emoteMap = emoteMap,
                        isLoggedIn = isLogin,
                        isVip = isEffectiveVip, // Pass effective VIP status (true if actual VIP or Unlocked)
                        isFollowing = isFollowing,
                        isFavorited = isFavorited,
                        isLiked = isLiked,

                        coinCount = coinCount,
                        duration = playData.timelength,
                        aiAudio = playData.aiAudio,
                        curAudioLang = playData.curLanguage,
                        adaptiveDashSource = selection.adaptiveDashSource
                    )
                },
                onFailure = { e ->
                    //  [风控冷却] 加载失败，记录失败
                    PlaybackCooldownManager.recordFailure(bvid, e.message ?: "unknown")
                    // Check if rate limited
                    val error = VideoLoadError.fromException(e)

                    VideoLoadResult.Error(
                        error = VideoLoadError.fromException(e),
                        canRetry = VideoLoadError.fromException(e).isRetryable()
                    )
                }
            )

        } catch (e: kotlinx.coroutines.CancellationException) {
            Logger.d("VideoPlaybackUseCase", "🚫 加载已取消: $bvid")
            throw e
        } catch (e: Exception) {
            //  [风控冷却] 异常失败，记录
            PlaybackCooldownManager.recordFailure(bvid, e.message ?: "exception")
            return VideoLoadResult.Error(
                error = VideoLoadError.fromException(e),
                canRetry = true
            )
        }
    }
    
    /**
     * Get cached position for video
     */
    fun getCachedPosition(bvid: String, cid: Long = 0L): Long {
        return progressManager.getCachedPosition(bvid, cid)
    }
    
    /**
     * Save current playback position
     */
    fun savePosition(bvid: String, cid: Long = 0L) {
        val player = exoPlayer ?: return
        if (bvid.isNotEmpty() && player.currentPosition > 0) {
            progressManager.savePosition(
                bvid = bvid,
                cid = cid,
                positionMs = player.currentPosition
            )
        }
    }
    
    /**
     * Play video with DASH format
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playDashVideo(videoUrl: String, audioUrl: String?, seekTo: Long = 0L, playWhenReady: Boolean = true) {
        playDashVideo(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            adaptiveDashSource = null,
            seekTo = seekTo,
            playWhenReady = playWhenReady
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playDashVideo(
        videoUrl: String,
        audioUrl: String?,
        adaptiveDashSource: AdaptiveDashPlaybackSource?,
        seekTo: Long = 0L,
        playWhenReady: Boolean = true
    ) {
        val player = exoPlayer ?: return
        player.volume = 1.0f

        val finalSource = if (shouldUseAdaptiveDashPlayback(adaptiveDashSource, audioUrl)) {
            createAdaptiveDashMediaSource(adaptiveDashSource)
                ?: createLegacyDashMediaSource(videoUrl, audioUrl)
        } else {
            createLegacyDashMediaSource(videoUrl, audioUrl)
        }

        player.setMediaSource(finalSource)
        if (seekTo > 0) {
            player.seekTo(seekTo)
        }
        if (shouldPreparePlayerOnLoad(playWhenReady)) {
            player.prepare()
        }
        player.playWhenReady = playWhenReady
    }
    
    /**
     * Play simple video URL
     */
    fun playVideo(url: String, seekTo: Long = 0L, playWhenReady: Boolean = true) {
        val player = exoPlayer ?: return
        player.volume = 1.0f
        
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        if (seekTo > 0) {
            player.seekTo(seekTo)
        }
        if (shouldPreparePlayerOnLoad(playWhenReady)) {
            player.prepare()
        }
        player.playWhenReady = playWhenReady
    }
    
    /**
     * Change quality using cached DASH streams
     */
    fun changeQualityFromCache(
        qualityId: Int,
        cachedVideos: List<DashVideo>,
        cachedAudios: List<DashAudio>,
        currentPos: Long,
        durationMs: Long = 0L,
        playbackQualityMode: PlaybackQualityMode = PlaybackQualityMode.AUTO,
        audioQualityPreference: Int = -1, // [新增] 传入音频偏好
        videoCodecPreference: String = "hev1",
        videoSecondCodecPreference: String = "avc1",
        isHevcSupported: Boolean = com.android.purebilibili.core.util.MediaUtils.isHevcSupported(),
        isAv1Supported: Boolean = com.android.purebilibili.core.util.MediaUtils.isAv1Supported()
    ): QualitySwitchResult? {
        if (cachedVideos.isEmpty()) {
            Logger.d("VideoPlaybackUseCase", " changeQualityFromCache: cache is EMPTY, returning null")
            return null
        }
        val effectivePlaybackQualityMode = if (
            playbackQualityMode is PlaybackQualityMode.AUTO && qualityId > 0
        ) {
            PlaybackQualityMode.LOCKED(qualityId)
        } else {
            playbackQualityMode
        }
        
        //  [调试] 输出缓存中的所有画质
        val availableIds = cachedVideos.map { it.id }.distinct().sortedDescending()
        Logger.d("VideoPlaybackUseCase", " changeQualityFromCache: target=$qualityId, available=$availableIds")

        val requestedQuality = qualityId.takeIf { it > 0 } ?: availableIds.firstOrNull() ?: return null
        val exactQualityVideos = if (effectivePlaybackQualityMode is PlaybackQualityMode.AUTO) {
            cachedVideos
        } else {
            cachedVideos.filter { it.id == requestedQuality }
        }
        if (exactQualityVideos.isEmpty()) {
            Logger.d("VideoPlaybackUseCase", " Cache exact match missing for $qualityId, fallback to API")
            return null
        }

        val match = Dash(video = exactQualityVideos).getBestVideo(
            targetQn = requestedQuality,
            preferCodec = videoCodecPreference,
            secondPreferCodec = videoSecondCodecPreference,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        ) ?: run {
            Logger.d("VideoPlaybackUseCase", " Cache exact match has no playable URL for $qualityId")
            return null
        }

        Logger.d(
            "VideoPlaybackUseCase",
            " Match found in cache: quality=${match.id}, codec=${match.codecs.ifBlank { "unknown" }}"
        )
        val videoUrl = match.getValidUrl()
        
        // [修复] 音频也应该重新选择最佳匹配，而不是盲目取第一个
        val dashAudio = if (audioQualityPreference != -1) {
            // 使用 Dash.getBestAudio 逻辑的简化版 (因为这里只有 List<DashAudio>)
            cachedAudios.find { it.id == audioQualityPreference }
                ?: cachedAudios.minByOrNull { kotlin.math.abs(it.id - audioQualityPreference) }
        } else {
            cachedAudios.maxByOrNull { it.bandwidth }
        }
         
        val audioUrl = dashAudio?.getValidUrl()
        val adaptiveDashSource = buildAdaptiveDashPlaybackSource(
            durationMs = durationMs,
            minBufferTimeMs = 1500L,
            dash = Dash(video = cachedVideos, audio = cachedAudios),
            targetQuality = requestedQuality,
            audioQualityPreference = audioQualityPreference,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            playbackQualityMode = effectivePlaybackQualityMode,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        )
        if (videoUrl.isNotEmpty()) {
            playDashVideo(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                adaptiveDashSource = adaptiveDashSource,
                seekTo = currentPos,
                playWhenReady = true
            )
            return QualitySwitchResult(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                actualQuality = match.id,
                wasFallback = false,
                adaptiveDashSource = adaptiveDashSource,
                cachedDashVideos = cachedVideos,
                cachedDashAudios = cachedAudios
            )
        }
        
        //  [降级逻辑] 缓存中没有目标画质，需要返回 null 让调用者请求 API
        Logger.d("VideoPlaybackUseCase", " Target quality $qualityId not in cache, returning null to trigger API request")
        return null
    }
    
    /**
     * Change quality via API request
     */
    suspend fun changeQualityFromApi(
        bvid: String,
        cid: Long,
        qualityId: Int,
        currentPos: Long,
        playbackQualityMode: PlaybackQualityMode = PlaybackQualityMode.AUTO,
        audioQualityPreference: Int = -1, // [新增] 传入音频偏好
        videoCodecPreference: String = "hev1",
        videoSecondCodecPreference: String = "avc1",
        isHevcSupported: Boolean = com.android.purebilibili.core.util.MediaUtils.isHevcSupported(),
        isAv1Supported: Boolean = com.android.purebilibili.core.util.MediaUtils.isAv1Supported()
    ): QualitySwitchResult? {
        Logger.d("VideoPlaybackUseCase", " changeQualityFromApi: bvid=$bvid, cid=$cid, target=$qualityId")
        val effectivePlaybackQualityMode = if (
            playbackQualityMode is PlaybackQualityMode.AUTO && qualityId > 0
        ) {
            PlaybackQualityMode.LOCKED(qualityId)
        } else {
            playbackQualityMode
        }
        
        val playUrlData = VideoRepository.getPlayUrlData(bvid, cid, qualityId) ?: run {
            Logger.d("VideoPlaybackUseCase", " getPlayUrlData returned null")
            return null
        }
        
        //  [调试] 输出 API 返回的画质信息
        val returnedQuality = playUrlData.quality
        val acceptQualities = playUrlData.accept_quality
        val dashVideoIds = playUrlData.dash?.video?.map { it.id }?.distinct()?.sortedDescending()
        Logger.d("VideoPlaybackUseCase", " API returned: quality=$returnedQuality, accept_quality=$acceptQualities")
        Logger.d("VideoPlaybackUseCase", " DASH videos available: $dashVideoIds")

        val selection = resolvePlaybackSelection(
            playUrlData = playUrlData,
            targetQuality = qualityId,
            audioQualityPreference = audioQualityPreference,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            playbackQualityMode = effectivePlaybackQualityMode,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        ) ?: run {
            Logger.d("VideoPlaybackUseCase", " Video URL is empty")
            return null
        }
        
        if (selection.isDashPlayback) {
            playDashVideo(
                videoUrl = selection.videoUrl,
                audioUrl = selection.audioUrl,
                adaptiveDashSource = selection.adaptiveDashSource,
                seekTo = currentPos,
                playWhenReady = true
            ) // Switching quality should always auto-play
        } else {
            playVideo(selection.videoUrl, currentPos, playWhenReady = true)
        }
        
        Logger.d("VideoPlaybackUseCase", " Quality switch result: target=$qualityId, actual=${selection.actualQuality}")
        
        return QualitySwitchResult(
            videoUrl = selection.videoUrl,
            audioUrl = selection.audioUrl,
            actualQuality = selection.actualQuality,
            wasFallback = selection.actualQuality != qualityId,
            adaptiveDashSource = selection.adaptiveDashSource,
            cachedDashVideos = selection.cachedDashVideos,
            cachedDashAudios = selection.cachedDashAudios,
            qualityIds = selection.qualityIds,
            qualityLabels = selection.qualityLabels
        )
    }
    
    /**
     * Get player current position
     */
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    
    /**
     * Get player duration
     */
    fun getDuration(): Long {
        val duration = exoPlayer?.duration ?: 0L
        return if (duration < 0) 0L else duration
    }
    
    /**
     * Seek to position
     */
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun resolvePlaybackSelection(
        playUrlData: PlayUrlData,
        targetQuality: Int,
        audioQualityPreference: Int = -1,
        videoCodecPreference: String = "hev1",
        videoSecondCodecPreference: String = "avc1",
        playbackQualityMode: PlaybackQualityMode = PlaybackQualityMode.AUTO,
        isHevcSupported: Boolean = com.android.purebilibili.core.util.MediaUtils.isHevcSupported(),
        isAv1Supported: Boolean = com.android.purebilibili.core.util.MediaUtils.isAv1Supported()
    ): PlaybackSelectionResult? {
        val dashVideo = playUrlData.dash?.getBestVideo(
            targetQuality,
            preferCodec = videoCodecPreference,
            secondPreferCodec = videoSecondCodecPreference,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        )
        val dashAudio = playUrlData.dash?.getBestAudio(audioQualityPreference)
        val videoUrl = getValidVideoUrl(dashVideo, playUrlData)
        if (videoUrl.isBlank()) return null

        val qualitySelectionState = buildQualitySelectionState(
            apiQualities = playUrlData.accept_quality,
            dashVideoIds = playUrlData.dash?.video?.map { it.id }?.distinct() ?: emptyList(),
            allowPremiumApiOnlyQualities = !VideoRepository.isAppApiCoolingDown()
        )
        val adaptiveDashSource = buildAdaptiveDashPlaybackSource(
            durationMs = playUrlData.timelength,
            minBufferTimeMs = playUrlData.dash?.minBufferTime?.times(1000f)?.toLong() ?: 1500L,
            dash = playUrlData.dash,
            targetQuality = targetQuality,
            audioQualityPreference = audioQualityPreference,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            playbackQualityMode = playbackQualityMode,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        )
        return PlaybackSelectionResult(
            videoUrl = videoUrl,
            audioUrl = dashAudio?.getValidUrl(),
            actualQuality = dashVideo?.id ?: playUrlData.quality,
            isDashPlayback = dashVideo != null,
            adaptiveDashSource = adaptiveDashSource,
            cachedDashVideos = playUrlData.dash?.video ?: emptyList(),
            cachedDashAudios = playUrlData.dash?.audio ?: emptyList(),
            qualityIds = qualitySelectionState.qualityIds,
            qualityLabels = qualitySelectionState.qualityLabels
        )
    }

    private fun buildAdaptiveDashPlaybackSource(
        durationMs: Long,
        minBufferTimeMs: Long,
        dash: Dash?,
        targetQuality: Int,
        audioQualityPreference: Int,
        videoCodecPreference: String,
        videoSecondCodecPreference: String,
        playbackQualityMode: PlaybackQualityMode,
        isHevcSupported: Boolean,
        isAv1Supported: Boolean
    ): AdaptiveDashPlaybackSource? {
        val adaptiveTrackSet = dash?.let { sourceDash ->
            buildAdaptiveDashTrackSet(
                dash = sourceDash,
                mode = playbackQualityMode,
                autoQualityCap = targetQuality,
                preferredAudioQuality = audioQualityPreference,
                preferredVideoCodec = videoCodecPreference,
                secondaryVideoCodec = videoSecondCodecPreference,
                isHevcSupported = isHevcSupported,
                isAv1Supported = isAv1Supported
            )
        } ?: return null

        if (adaptiveTrackSet.videoTracks.isEmpty()) return null

        return AdaptiveDashPlaybackSource(
            manifest = buildLocalDashManifest(
                durationMs = durationMs,
                minBufferTimeMs = minBufferTimeMs,
                videoTracks = adaptiveTrackSet.videoTracks,
                audioTracks = adaptiveTrackSet.audioTracks
            ),
            videoTracks = adaptiveTrackSet.videoTracks,
            audioTracks = adaptiveTrackSet.audioTracks,
            playbackQualityMode = playbackQualityMode
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createLegacyDashMediaSource(videoUrl: String, audioUrl: String?): MediaSource {
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            NetworkModule.playbackOkHttpClient
        ).setDefaultRequestProperties(headers)

        val mediaSourceFactory = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))

        return if (audioUrl != null) {
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
            androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createAdaptiveDashMediaSource(
        adaptiveDashSource: AdaptiveDashPlaybackSource?
    ): MediaSource? {
        val source = adaptiveDashSource ?: return null
        val context = appContext ?: NetworkModule.appContext ?: return null
        val manifestUri = writeAdaptiveDashManifest(context, source.manifest) ?: return null
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val upstreamFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            NetworkModule.playbackOkHttpClient
        ).setDefaultRequestProperties(headers)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, upstreamFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(manifestUri)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build()
        return DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    private fun writeAdaptiveDashManifest(context: Context, manifest: String): Uri? {
        return runCatching {
            val manifestDir = File(context.cacheDir, "dash_manifests").apply { mkdirs() }
            val manifestFile = File(manifestDir, resolveLocalDashManifestFileName(manifest))
            if (!manifestFile.exists() || manifestFile.readText() != manifest) {
                manifestFile.writeText(manifest)
            }
            Uri.fromFile(manifestFile)
        }.onFailure { error ->
            Logger.w("VideoPlaybackUseCase", " Failed to cache adaptive DASH manifest: ${error.message}")
        }.getOrNull()
    }
    
    private fun getValidVideoUrl(dashVideo: DashVideo?, playData: PlayUrlData): String {
        return dashVideo?.getValidUrl()?.takeIf { it.isNotEmpty() }
            ?: playData.dash?.video?.firstOrNull()?.baseUrl?.takeIf { it.isNotEmpty() }
            ?: playData.dash?.video?.firstOrNull()?.backupUrl?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: playData.durl?.firstOrNull()?.url?.takeIf { it.isNotEmpty() }
            ?: playData.durl?.firstOrNull()?.backupUrl?.firstOrNull()
            ?: ""
    }

    internal fun mergeQualityOptions(
        apiQualities: List<Int>,
        dashVideoIds: List<Int>,
        allowPremiumApiOnlyQualities: Boolean = true
    ): QualityMergeResult {
        val normalizedApi = apiQualities.distinct().sortedDescending()
        val normalizedDash = dashVideoIds.distinct().sortedDescending()
        val switchableQualities = if (normalizedDash.isNotEmpty()) normalizedDash else normalizedApi

        // Keep API-advertised login-tier qualities visible so users can re-fetch 1080P+ even when
        // the first DASH payload is temporarily capped at 720P.
        val apiOnlyHighQualities = normalizedApi.filter { qualityId ->
            qualityId >= API_ONLY_VISIBLE_QUALITY_FLOOR &&
                qualityId !in normalizedDash &&
                (qualityId < PREMIUM_API_ONLY_QUALITY_FLOOR || allowPremiumApiOnlyQualities)
        }

        val mergedQualityIds = (switchableQualities + apiOnlyHighQualities + STANDARD_LOW_QUALITIES)
            .distinct()
            .sortedDescending()

        return QualityMergeResult(
            switchableQualities = switchableQualities,
            apiOnlyHighQualities = apiOnlyHighQualities,
            mergedQualityIds = mergedQualityIds
        )
    }

    internal fun buildQualitySelectionState(
        apiQualities: List<Int>,
        dashVideoIds: List<Int>,
        allowPremiumApiOnlyQualities: Boolean = true
    ): QualitySelectionState {
        val mergedQualityIds = mergeQualityOptions(
            apiQualities = apiQualities,
            dashVideoIds = dashVideoIds,
            allowPremiumApiOnlyQualities = allowPremiumApiOnlyQualities
        ).mergedQualityIds
        return QualitySelectionState(
            qualityIds = mergedQualityIds,
            qualityLabels = mergedQualityIds.map(qualityManager::getQualityLabel)
        )
    }

    internal fun resolveAutoHighestTargetQuality(
        acceptQualities: List<Int>,
        isLoggedIn: Boolean,
        isVip: Boolean,
        isHdrSupported: Boolean,
        isDolbyVisionSupported: Boolean
    ): Int {
        val capabilityCeiling = when {
            isVip -> Int.MAX_VALUE
            isLoggedIn -> 80
            else -> 64
        }
        val deviceSafeQualities = acceptQualities.filter { qn ->
            when (qn) {
                126 -> isDolbyVisionSupported
                125 -> isHdrSupported
                else -> true
            }
        }
        val playable = deviceSafeQualities.filter { it <= capabilityCeiling }
        return playable.maxOrNull()
            ?: if (isLoggedIn) 80 else 64
    }

    internal fun buildPlaybackSelectionSummary(
        bvid: String,
        cid: Long,
        defaultQuality: Int,
        targetQuality: Int,
        returnedQuality: Int,
        selectedDashQuality: Int?,
        mergedQualityIds: List<Int>,
        isLoggedIn: Boolean,
        isVip: Boolean
    ): String {
        return "PLAY_DIAG playback_selection bvid=$bvid cid=$cid default=$defaultQuality target=$targetQuality " +
            "returned=$returnedQuality selectedDash=${selectedDashQuality ?: "null"} " +
            "merged=$mergedQualityIds isLoggedIn=$isLoggedIn isVip=$isVip"
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
