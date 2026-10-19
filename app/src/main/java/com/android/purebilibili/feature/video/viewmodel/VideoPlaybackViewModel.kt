// File: feature/video/VideoPlaybackViewModel.kt
//  [重构] 简化版 VideoPlaybackViewModel - 使用 UseCase 层
package com.android.purebilibili.feature.video.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.annotation.SuppressLint
import com.android.purebilibili.feature.video.usecase.*

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.android.purebilibili.core.cache.PlayUrlCache
import com.android.purebilibili.core.cooldown.PlaybackCooldownManager
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.store.TodayWatchFeedbackSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.TodayWatchProfileStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.DanmakuThumbupState
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.VideoNoteRepository
import com.android.purebilibili.data.repository.VideoNoteSavePayload
import com.android.purebilibili.data.repository.ViewGrpcRepository
import com.android.purebilibili.data.repository.resolveVideoPlaybackAuthState
import com.android.purebilibili.data.repository.isExactRequestedQualitySelected
import com.android.purebilibili.data.repository.shouldScheduleHdrAutoUpgrade
import com.android.purebilibili.feature.plugin.CdnTransferRuntime
import com.android.purebilibili.feature.plugin.CdnHealthEvent
import com.android.purebilibili.feature.plugin.CdnDashPrefetchRequest
import com.android.purebilibili.feature.plugin.CdnDashSegmentPrefetcher
import com.android.purebilibili.feature.plugin.CdnLineDiagnostic
import com.android.purebilibili.feature.plugin.PlaybackCdnPlugin
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.plugin.applyPlaybackCdnPreference
import com.android.purebilibili.feature.plugin.buildPlaybackCdnCacheKeys
import com.android.purebilibili.feature.plugin.buildCdnLineDiagnostics
import com.android.purebilibili.feature.plugin.buildCdnTrackCacheKey
import com.android.purebilibili.feature.plugin.parseCdnByteRange
import com.android.purebilibili.feature.plugin.SponsorBlockInsightStore
import com.android.purebilibili.feature.plugin.SponsorBlockSkipTrigger
import com.android.purebilibili.feature.plugin.SponsorBlockVideoSnapshot
import com.android.purebilibili.feature.plugin.buildSponsorBlockSkipRecord
import com.android.purebilibili.feature.video.controller.QualityManager
import com.android.purebilibili.feature.video.controller.QualityPermissionResult
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.VideoNoteContentCodec
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteLoadStatus
import com.android.purebilibili.feature.video.note.VideoNotePublicPreview
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.note.buildVideoNoteDraftFromAiSummary
import com.android.purebilibili.feature.video.note.resolveVideoNoteConflictMessage
import com.android.purebilibili.feature.video.note.resolveVideoNoteEditableDocument
import com.android.purebilibili.feature.video.note.resolveVideoNoteSaveFeedback
import com.android.purebilibili.feature.video.note.shouldLoadVideoNote
import com.android.purebilibili.feature.video.playback.policy.shouldRefreshPremiumAudioForPlaybackSpeedChange
import com.android.purebilibili.feature.video.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.playback.coordinator.PlaybackCoordinator
import com.android.purebilibili.feature.video.playback.loader.PlaybackRequest
import com.android.purebilibili.feature.video.playback.loader.PlaybackLoadConfig
import com.android.purebilibili.feature.video.playback.loader.PlaybackLoadResult
import com.android.purebilibili.feature.video.playback.loader.PlaybackLoader
import com.android.purebilibili.core.player.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.feature.video.playback.audio.AudioFallbackReason
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_AUTO
import com.android.purebilibili.feature.video.playback.audio.isPremiumAudioPlaybackFailure
import com.android.purebilibili.feature.video.playback.audio.resolveRequestedAudioQuality
import com.android.purebilibili.feature.video.playback.policy.PlaybackPostLoadTask
import com.android.purebilibili.core.player.policy.PlaybackQualityMode
import com.android.purebilibili.feature.video.playback.policy.PlaybackHeartbeatSnapshot
import com.android.purebilibili.feature.video.playback.policy.resolveOnlineCountPollingDelayMs
import com.android.purebilibili.feature.video.playback.policy.buildPlaybackPostLoadPlan
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackHeartbeatSessionStartTsSec
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackHeartbeatSnapshot
import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackResumeTransitionPosition
import com.android.purebilibili.feature.video.playback.policy.resolvePluginPollingIntervalMs
import com.android.purebilibili.feature.video.playback.policy.shouldRefreshOnlineCount
import com.android.purebilibili.feature.video.playback.policy.shouldSendInitialPlaybackHeartbeat
import com.android.purebilibili.feature.video.playback.policy.shouldSendPlaybackHeartbeat
import com.android.purebilibili.feature.video.playback.policy.shouldFlushPlaybackHeartbeatSnapshot
import com.android.purebilibili.feature.video.playback.policy.shouldDispatchPluginPositionUpdate
import com.android.purebilibili.feature.video.playback.resolver.AudioNextPlaybackStrategy
import com.android.purebilibili.feature.video.playback.resolver.PlaybackNavigationTarget
import com.android.purebilibili.feature.video.playback.resolver.PlayInOrderNextSource
import com.android.purebilibili.feature.video.playback.resolver.resolveAudioNextPlaybackStrategy
import com.android.purebilibili.feature.video.playback.resolver.resolvePlaybackNavigationTargets
import com.android.purebilibili.feature.video.playback.resolver.resolvePlayInOrderNextSource
import com.android.purebilibili.feature.video.playback.resolver.resolvePlayInOrderPreviousSource
import com.android.purebilibili.feature.video.playback.session.PlaybackSessionStore
import com.android.purebilibili.feature.video.interaction.InteractiveChoicePanelUiState
import com.android.purebilibili.feature.video.interaction.InteractiveChoiceUiModel
import com.android.purebilibili.feature.video.interaction.normalizeInteractiveCountdownMs
import com.android.purebilibili.feature.video.interaction.resolveInteractiveAutoChoice
import com.android.purebilibili.feature.video.interaction.resolveInteractiveChoiceCid
import com.android.purebilibili.feature.video.interaction.resolveInteractiveChoiceEdgeId
import com.android.purebilibili.feature.video.interaction.resolveInteractiveCountdownUpdateIntervalMs
import com.android.purebilibili.feature.video.interaction.resolveInteractiveQuestionPollingIntervalMs
import com.android.purebilibili.feature.video.interaction.resolveInteractiveQuestionTriggerMs
import com.android.purebilibili.feature.video.interaction.applyInteractiveNativeAction
import com.android.purebilibili.feature.video.interaction.evaluateInteractiveChoiceCondition
import com.android.purebilibili.feature.video.interaction.shouldTriggerInteractiveQuestion
import com.android.purebilibili.feature.video.policy.resolveFavoriteFolderMediaId
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.subtitle.SubtitleCue
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackMeta
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.isLikelyAiSubtitleTrack
import com.android.purebilibili.feature.video.subtitle.mapPlayerInfoSubtitleTracks
import com.android.purebilibili.feature.video.subtitle.normalizeBilibiliSubtitleUrl
import com.android.purebilibili.feature.video.subtitle.resolveDefaultSubtitleLanguages

private const val PLAYBACK_CDN_FIRST_FRAME_FALLBACK_TIMEOUT_MS = 2_500L
private const val PLAYBACK_STALL_RECOVERY_TIMEOUT_MS = 10_000L

internal data class PlaybackStallRecoveryDecision(
    val nextCdnIndex: Int? = null
) {
    val shouldSwitchCdn: Boolean get() = nextCdnIndex != null
}

internal fun resolvePlaybackStallRecoveryDecision(
    playbackState: Int,
    playWhenReady: Boolean,
    firstFrameRendered: Boolean,
    forwardBufferDurationMs: Long,
    currentCdnIndex: Int,
    cdnCandidateCount: Int,
    attemptedCdnIndexes: Set<Int>,
    usesAdaptivePlayback: Boolean
): PlaybackStallRecoveryDecision {
    if (
        playbackState != Player.STATE_BUFFERING ||
        !playWhenReady ||
        !firstFrameRendered ||
        forwardBufferDurationMs > 0L ||
        cdnCandidateCount <= 1 ||
        usesAdaptivePlayback
    ) {
        return PlaybackStallRecoveryDecision()
    }

    val nextIndex = (1 until cdnCandidateCount)
        .asSequence()
        .map { offset -> (currentCdnIndex + offset) % cdnCandidateCount }
        .firstOrNull { candidate -> candidate !in attemptedCdnIndexes }
    return PlaybackStallRecoveryDecision(nextCdnIndex = nextIndex)
}

data class CommentMentionSearchUiState(
    val query: String = "",
    val users: List<MentionSearchUser> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SponsorSkipUiState(
    val visible: Boolean = false,
    val segmentId: String? = null,
    val skipToMs: Long = 0L,
    val label: String? = null
)

enum class SponsorContributionPhase {
    HIDDEN,
    READY,
    MARKING,
    REVIEW,
    SUBMITTING,
    SUCCESS,
}

data class SponsorContributionUiState(
    val phase: SponsorContributionPhase = SponsorContributionPhase.HIDDEN,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val category: String = SponsorCategory.SPONSOR,
    val actionType: String = "skip",
    val serverBaseUrl: String = "",
    val message: String? = null,
) {
    val showsMarkAction: Boolean
        get() = phase == SponsorContributionPhase.READY || phase == SponsorContributionPhase.MARKING

    val showsReview: Boolean
        get() = phase == SponsorContributionPhase.REVIEW ||
            phase == SponsorContributionPhase.SUBMITTING ||
            phase == SponsorContributionPhase.SUCCESS
}

private data class SponsorContributionRequest(
    val plugin: com.android.purebilibili.feature.plugin.SponsorBlockPlugin,
    val bvid: String,
    val cid: Long,
    val durationSeconds: Float,
    val startMs: Long,
    val endMs: Long,
)

internal fun reduceSponsorSkipUiState(
    previous: SponsorSkipUiState,
    action: SkipAction?
): SponsorSkipUiState {
    return when (action) {
        is SkipAction.ShowButton -> SponsorSkipUiState(
            visible = true,
            segmentId = action.segmentId,
            skipToMs = action.skipToMs,
            label = action.label
        )

        else -> previous.copy(
            visible = false,
            segmentId = null,
            skipToMs = 0L,
            label = null
        )
    }
}

internal fun shouldResumePlaybackAfterSponsorBlockSkip(
    playWhenReadyBeforeSkip: Boolean
): Boolean {
    // 空降助手跳过是用户启用的连续播放能力；跳过后保持暂停会让每个片段都需要再点一次播放。
    return true
}

private const val SPONSOR_SKIP_END_GUARD_MS = 1_000L
private const val PUBLIC_VIDEO_NOTE_PAGE_SIZE = 10

internal fun resolveSponsorBlockSkipTargetPositionMs(
    requestedPositionMs: Long,
    durationMs: Long,
    category: String?,
): Long {
    val safeRequestedPositionMs = requestedPositionMs.coerceAtLeast(0L)
    if (durationMs <= 0L) return safeRequestedPositionMs

    // 片尾跳过允许自然进入播放完成；其他社区片段不能因时间轴误差 seek 到
    // duration 或其后，否则部分 Media3/解码器组合会直接进入 STATE_ENDED。
    if (category == com.android.purebilibili.data.model.response.SponsorCategory.OUTRO) {
        return safeRequestedPositionMs.coerceAtMost(durationMs)
    }
    val latestContinuousPlaybackPositionMs =
        (durationMs - SPONSOR_SKIP_END_GUARD_MS).coerceAtLeast(0L)
    return safeRequestedPositionMs.coerceAtMost(latestContinuousPlaybackPositionMs)
}

internal fun buildSponsorBlockVideoSnapshot(currentState: VideoPlaybackUiState): SponsorBlockVideoSnapshot? {
    val success = currentState as? VideoPlaybackUiState.Success ?: return null
    val info = success.info
    return SponsorBlockVideoSnapshot(
        videoTitle = info.title,
        bvid = info.bvid,
        cid = info.cid,
        videoCoverUrl = info.pic,
        upName = info.owner.name,
        upFaceUrl = info.owner.face,
        upMid = info.owner.mid
    )
}

internal data class AudioModePlaylist(
    val items: List<PlaylistItem>,
    val startIndex: Int
)

internal fun resolveUgcSeasonEpisodeIndex(
    episodes: List<UgcEpisode>,
    currentBvid: String,
    currentCid: Long
): Int {
    if (currentBvid.isBlank()) return -1

    if (currentCid > 0L) {
        val exactIndex = episodes.indexOfFirst { episode ->
            episode.bvid == currentBvid && episode.cid == currentCid
        }
        if (exactIndex >= 0) return exactIndex
    }

    return episodes.indexOfFirst { it.bvid == currentBvid }
}

internal fun buildAudioModeCollectionPlaylist(
    episodes: List<UgcEpisode>,
    currentBvid: String,
    currentCid: Long
): AudioModePlaylist? {
    val playableEpisodes = episodes.filter { it.bvid.isNotBlank() }
    val items = playableEpisodes
        .map { episode ->
            PlaylistItem(
                bvid = episode.bvid,
                cid = episode.cid,
                title = episode.title.ifBlank {
                    episode.arc?.title?.takeIf { title -> title.isNotBlank() } ?: episode.bvid
                },
                cover = episode.arc?.pic.orEmpty(),
                owner = "",
                duration = episode.arc?.duration?.toLong() ?: 0L
            )
        }
    if (items.isEmpty()) return null

    val exactIndex = playableEpisodes.indexOfFirst { episode ->
        episode.bvid == currentBvid &&
            currentCid > 0L &&
            episode.cid == currentCid
    }
    val fallbackIndex = playableEpisodes.indexOfFirst { it.bvid == currentBvid }
    val startIndex = when {
        exactIndex >= 0 -> exactIndex
        fallbackIndex >= 0 -> fallbackIndex
        else -> 0
    }.coerceIn(0, items.lastIndex)

    return AudioModePlaylist(
        items = items,
        startIndex = startIndex
    )
}

internal fun buildAudioModePagePlaylist(
    pages: List<com.android.purebilibili.data.model.response.Page>,
    currentBvid: String,
    currentCid: Long,
    videoTitle: String,
    cover: String,
    owner: String
): AudioModePlaylist? {
    if (pages.size <= 1 || currentBvid.isBlank()) return null

    val items = pages.mapIndexed { index, page ->
        PlaylistItem(
            bvid = currentBvid,
            cid = page.cid,
            title = page.part.ifBlank { if (index == 0) videoTitle else "P${index + 1}" },
            cover = cover,
            owner = owner,
            duration = page.duration
        )
    }
    val startIndex = pages.indexOfFirst { it.cid == currentCid }
        .takeIf { it >= 0 }
        ?: 0
    return AudioModePlaylist(items = items, startIndex = startIndex)
}

/** 听视频模式下，收藏夹/稍后再看等外部队列优先于视频自带合集队列。 */
internal fun shouldApplyAudioModeCollectionPlaylist(
    isInAudioMode: Boolean,
    keepExternalPlaylist: Boolean
): Boolean {
    return isInAudioMode && !keepExternalPlaylist
}

internal data class PlaybackCdnFallbackState(
    val selectedVideoUrl: String = "",
    val selectedAudioUrl: String? = null,
    val fallbackVideoUrl: String? = null,
    val fallbackAudioUrl: String? = null,
    val fallbackCandidates: List<com.android.purebilibili.feature.plugin.PlaybackCdnCandidate> = emptyList(),
    val regionLabel: String? = null,
    val usesCustomRule: Boolean = false,
    val fallbackConsumed: Boolean = false
) {
    val usesCdnRewrite: Boolean
        get() = !fallbackConsumed &&
            !fallbackVideoUrl.isNullOrBlank() &&
            (selectedVideoUrl != fallbackVideoUrl || selectedAudioUrl != fallbackAudioUrl)

    fun markFallbackConsumed(): PlaybackCdnFallbackState = copy(fallbackConsumed = true)

    fun advanceFallback(
        currentFallbackVideoUrl: String,
        currentFallbackAudioUrl: String?
    ): PlaybackCdnFallbackState {
        val currentIndex = fallbackCandidates.indexOfFirst { candidate ->
            candidate.videoUrl == currentFallbackVideoUrl && candidate.audioUrl == currentFallbackAudioUrl
        }
        val next = fallbackCandidates.drop((currentIndex + 1).coerceAtLeast(0)).firstOrNull { candidate ->
            candidate.videoUrl != currentFallbackVideoUrl || candidate.audioUrl != currentFallbackAudioUrl
        }
        return copy(
            selectedVideoUrl = currentFallbackVideoUrl,
            selectedAudioUrl = currentFallbackAudioUrl,
            fallbackVideoUrl = next?.videoUrl,
            fallbackAudioUrl = next?.audioUrl,
            usesCustomRule = false,
            fallbackConsumed = next == null
        )
    }

    companion object {
        val Inactive = PlaybackCdnFallbackState()
    }
}

internal fun buildPlaybackCdnFallbackState(
    selectedVideoUrl: String,
    selectedAudioUrl: String?,
    originalVideoUrl: String,
    originalAudioUrl: String?,
    regionLabel: String?,
    audioFallbackUrl: String? = null,
    fallbackCandidates: List<com.android.purebilibili.feature.plugin.PlaybackCdnCandidate> = emptyList(),
    usesCustomRule: Boolean = false
): PlaybackCdnFallbackState {
    val fallbackAudioUrl = when {
        selectedAudioUrl != originalAudioUrl -> originalAudioUrl
        !audioFallbackUrl.isNullOrBlank() -> audioFallbackUrl
        else -> originalAudioUrl
    }
    val candidates = fallbackCandidates.ifEmpty {
        listOf(
            com.android.purebilibili.feature.plugin.PlaybackCdnCandidate(
                videoUrl = originalVideoUrl,
                audioUrl = fallbackAudioUrl,
                source = com.android.purebilibili.feature.plugin.PlaybackCdnCandidateSource.ORIGINAL
            )
        )
    }
    val firstFallback = candidates.firstOrNull { candidate ->
        candidate.videoUrl != selectedVideoUrl || candidate.audioUrl != selectedAudioUrl
    }
    return PlaybackCdnFallbackState(
        selectedVideoUrl = selectedVideoUrl,
        selectedAudioUrl = selectedAudioUrl,
        fallbackVideoUrl = firstFallback?.videoUrl,
        fallbackAudioUrl = firstFallback?.audioUrl,
        fallbackCandidates = candidates,
        regionLabel = regionLabel,
        usesCustomRule = usesCustomRule
    )
}

internal fun buildPlaybackAudioUrlCandidates(
    audioUrl: String?,
    cachedDashAudios: List<DashAudio>
): List<String> {
    val selectedAudio = audioUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { selectedUrl ->
            cachedDashAudios.firstOrNull { audio ->
                audio.getValidUrl() == selectedUrl ||
                    audio.backupUrl.orEmpty().any { backupUrl -> backupUrl == selectedUrl }
            }
        }

    return buildList {
        audioUrl?.takeIf { it.isNotBlank() }?.let(::add)
        selectedAudio
            ?.backupUrl
            .orEmpty()
            .filter { it.isNotBlank() }
            .let(::addAll)
    }.distinct()
}

internal fun buildPlaybackVideoUrlCandidates(
    videoUrl: String,
    quality: Int,
    cachedDashVideos: List<DashVideo>
): List<String> {
    val selectedVideo = cachedDashVideos.firstOrNull { video ->
        video.id == quality && (
            video.baseUrl == videoUrl ||
                video.backupUrl.orEmpty().any { backupUrl -> backupUrl == videoUrl }
            )
    }

    return buildList {
        if (videoUrl.isNotBlank()) {
            add(videoUrl)
        }
        selectedVideo
            ?.backupUrl
            .orEmpty()
            .filter { it.isNotBlank() }
            .let(::addAll)
    }.distinct()
}

internal fun shouldFallbackFromCdnRewrite(
    state: PlaybackCdnFallbackState,
    playbackReady: Boolean
): Boolean {
    return state.usesCdnRewrite && !playbackReady
}

internal fun shouldFallbackFromCdnRewrite(
    state: PlaybackCdnFallbackState,
    playbackReady: Boolean,
    expectedAudioTrack: Boolean,
    hasSelectedAudioTrack: Boolean,
    audioRendererError: Boolean
): Boolean {
    if (!state.usesCdnRewrite) return false
    if (!playbackReady) return true
    if (audioRendererError) return true
    return expectedAudioTrack && !hasSelectedAudioTrack
}

internal fun hostForPlaybackLog(url: String?): String {
    val value = url?.takeIf { it.isNotBlank() } ?: return ""
    return runCatching { java.net.URI(value).host.orEmpty() }.getOrDefault("")
}

// ========== UI State ==========
sealed class VideoPlaybackUiState {
    enum class RelatedLoadState { LOADING, READY, FAILED }
    data class Loading(
        val retryAttempt: Int = 0,
        val maxAttempts: Int = 4,
        val message: String = "\u52a0\u8f7d\u4e2d..."
    ) : VideoPlaybackUiState() {
        companion object { val Initial = Loading() }
    }
    
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val audioUrl: String? = null,
        val related: List<RelatedVideo> = emptyList(),
        val relatedLoadState: RelatedLoadState = RelatedLoadState.READY,
        val currentQuality: Int = 64,
        val playbackQualityMode: PlaybackQualityMode = PlaybackQualityMode.AUTO,
        val adaptiveDashSource: AdaptiveDashPlaybackSource? = null,
        val qualityLabels: List<String> = emptyList(),
        val qualityIds: List<Int> = emptyList(),
        val switchableQualityIds: List<Int> = emptyList(),
        val startPosition: Long = 0L,
        val pendingPlaybackTransitionPositionMs: Long? = null,
        val cachedDashVideos: List<DashVideo> = emptyList(),
        val cachedDashAudios: List<DashAudio> = emptyList(),
        val cachedDash: Dash? = null,
        val requestedAudioQuality: Int = -1,
        val selectedAudioQuality: Int = -1,
        val availableAudioQualities: List<AudioQualityOption> = emptyList(),
        val audioFallbackReason: AudioFallbackReason? = null,
        val isQualitySwitching: Boolean = false,
        val requestedQuality: Int? = null,
        val isLoggedIn: Boolean = false,
        val isVip: Boolean = false,
        val isFollowing: Boolean = false,
        val isFavorited: Boolean = false,
        val isLiked: Boolean = false,
        val likeStatusKnown: Boolean = false,
        val followStatusKnown: Boolean = false,
        val isDisliked: Boolean = false,
        val coinCount: Int = 0,
        val emoteMap: Map<String, String> = emptyMap(),
        val isInWatchLater: Boolean = false,  //  稍后再看状态
        val followingMids: Set<Long> = emptySet(),  //  已关注用户 ID 列表
        val videoTags: List<VideoTag> = emptyList(),  //  视频标签列表
        //  CDN 线路切换
        val currentCdnIndex: Int = 0,  // 当前使用的 CDN 索引 (0=主线路)
        val allVideoUrls: List<String> = emptyList(),  // 所有可用视频 URL (主+备用)
        val allAudioUrls: List<String> = emptyList(),   // 所有可用音频 URL (主+备用)
        val cdnCandidateSources: List<com.android.purebilibili.feature.plugin.PlaybackCdnCandidateSource> = emptyList(),
        val cdnLineDiagnostics: List<CdnLineDiagnostic> = emptyList(),
        val isCdnProbing: Boolean = false,
        // 🖼️ [新增] 视频预览图数据（用于进度条拖动预览）
        val videoshotData: VideoshotData? = null,
        // 🎞️ [New] Codec & Audio Info
        val videoCodecId: Int = 0,
        val audioCodecId: Int = 0,
        // 👀 [新增] 在线观看人数

        val onlineCount: String = "",
        // [新增] AI Summary & BGM
        val aiSummary: AiSummaryData? = null,
        val aiSummaryPrompt: AiSummaryPromptState? = null,
        val videoNoteState: VideoNoteUiState = VideoNoteUiState(),
        val bgmInfo: BgmInfo? = null,
        val bgmInfoList: List<BgmInfo> = emptyList(),
        // [New] AI Audio Translation
        val aiAudio: AiAudioInfo? = null,
        val currentAudioLang: String? = null,
        val videoDurationMs: Long = 0L,
        val subtitleEnabled: Boolean = false,
        val subtitleOwnerBvid: String? = null,
        val subtitleOwnerCid: Long = 0L,
        val subtitlePrimaryLanguage: String? = null,
        val subtitleSecondaryLanguage: String? = null,
        val subtitlePrimaryTrackKey: String? = null,
        val subtitleSecondaryTrackKey: String? = null,
        val subtitleTracks: List<SubtitleTrackMeta> = emptyList(),
        val subtitlePrimaryLikelyAi: Boolean = false,
        val subtitleSecondaryLikelyAi: Boolean = false,
        val subtitlePrimaryCues: List<SubtitleCue> = emptyList(),
        val subtitleSecondaryCues: List<SubtitleCue> = emptyList(),
        val ownerFollowerCount: Int? = null,
        val ownerVideoCount: Int? = null,
        // SponsorBlock 空降片段生成的标题徽标（如“赞助/恰饭”），空串表示不展示
        val sponsorVideoLabel: String = ""
    ) : VideoPlaybackUiState() {
        val cdnCount: Int get() = allVideoUrls.size.coerceAtLeast(1)
        val currentCdnLabel: String get() = "线路${currentCdnIndex + 1}"
    }
    
    data class Error(
        val error: VideoLoadError,
        val canRetry: Boolean = true
    ) : VideoPlaybackUiState() {
        val msg: String get() = error.toUserMessage()
    }
}

internal fun resolveCommentReplyTargets(replyRpid: Long?, replyRoot: Long?): Pair<Long, Long> {
    val parent = replyRpid?.takeIf { it > 0L } ?: 0L
    if (parent == 0L) return 0L to 0L
    val root = replyRoot?.takeIf { it > 0L } ?: parent
    return root to parent
}

internal fun resolveCommentReplyMessage(
    message: String,
    replyName: String?,
    replyRoot: Long?
): String {
    val normalizedMessage = message.trim()
    val normalizedName = replyName?.trim().orEmpty()
    return if ((replyRoot ?: 0L) > 0L && normalizedName.isNotEmpty()) {
        " 回复 @$normalizedName : $normalizedMessage"
    } else {
        normalizedMessage
    }
}

internal fun resolvePlayerTransientEventChannelCapacity(): Int = Channel.BUFFERED

internal data class FavoriteFolderMutation(
    val addFolderIds: Set<Long>,
    val removeFolderIds: Set<Long>
)

internal data class FavoriteFolderSaveEvent(
    val aid: Long,
    val isFavorited: Boolean,
    val version: Long
)

internal data class ExternalPlaylistSyncDecision(
    val keepExternalPlaylist: Boolean,
    val matchedIndex: Int = -1
)

internal fun resolveFavoriteFolderMutation(
    original: Set<Long>,
    selected: Set<Long>
): FavoriteFolderMutation {
    return FavoriteFolderMutation(
        addFolderIds = selected - original,
        removeFolderIds = original - selected
    )
}

internal fun shouldBootstrapPlayerContext(
    hasBoundContext: Boolean,
    hasGlobalContext: Boolean
): Boolean {
    return !hasBoundContext && hasGlobalContext
}

internal fun resolveFavoriteFolderDialogTargetAid(
    requestedAid: Long?,
    currentAid: Long?
): Long? {
    return requestedAid?.takeIf { it > 0L } ?: currentAid?.takeIf { it > 0L }
}

internal fun resolveCommentSendTargetAid(
    requestedAid: Long?,
    currentAid: Long?
): Long? {
    return requestedAid?.takeIf { it > 0L } ?: currentAid?.takeIf { it > 0L }
}

internal fun shouldSyncFavoriteFolderUiState(
    targetAid: Long?,
    currentAid: Long?
): Boolean {
    return targetAid != null && currentAid != null && targetAid > 0L && targetAid == currentAid
}

internal fun shouldApplyVideoLoadResult(
    activeRequestToken: Long,
    resultRequestToken: Long,
    expectedBvid: String,
    currentBvid: String
): Boolean {
    return activeRequestToken == resultRequestToken && expectedBvid == currentBvid
}

/**
 * When loading a different media identity, halt the attached player immediately so the previous
 * video's audio cannot keep playing under a black/loading surface (collection in-page switch).
 */
internal fun shouldHaltPlaybackForPendingMediaSwitch(
    force: Boolean,
    skipPlayerPrepare: Boolean,
    requestBvid: String,
    requestCid: Long,
    currentBvid: String,
    currentCid: Long,
    uiBvid: String?,
    uiCid: Long
): Boolean {
    if (skipPlayerPrepare) return false
    val effectiveBvid = currentBvid.takeIf { it.isNotBlank() }
        ?: uiBvid?.takeIf { it.isNotBlank() }
        ?: return false
    if (effectiveBvid != requestBvid) return true
    if (force && requestCid > 0L) {
        val effectiveCid = when {
            currentCid > 0L -> currentCid
            uiCid > 0L -> uiCid
            else -> 0L
        }
        if (effectiveCid > 0L && effectiveCid != requestCid) return true
    }
    return false
}

internal fun resolveRequestedStartPositionMs(
    cachedPositionMs: Long,
    fallbackResumePositionMs: Long
): Long {
    val safeCachedPositionMs = cachedPositionMs.coerceAtLeast(0L)
    if (safeCachedPositionMs > 0L) return safeCachedPositionMs
    return fallbackResumePositionMs.coerceAtLeast(0L)
}

internal fun resolvePageSwitchStartPositionMs(
    cachedPositionMs: Long,
    pageDurationSeconds: Long,
    ignoreSavedProgress: Boolean,
    endedRestartThresholdMs: Long = 5_000L
): Long {
    if (ignoreSavedProgress) return 0L
    val safeCachedPositionMs = cachedPositionMs.coerceAtLeast(0L)
    val durationMs = pageDurationSeconds.coerceAtLeast(0L) * 1000L
    val restartBoundaryMs = (durationMs - endedRestartThresholdMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    if (durationMs > 0L && safeCachedPositionMs >= restartBoundaryMs) {
        return 0L
    }
    return safeCachedPositionMs
}

internal fun resolveInitialPlaybackQualityMode(): PlaybackQualityMode = PlaybackQualityMode.AUTO

internal fun resolvePlaybackQualityModeForQualitySelection(qualityId: Int): PlaybackQualityMode {
    return PlaybackQualityMode.fromQualityId(qualityId)
}

internal fun resolveRelatedPlayUrlPreloadCount(
    relatedCount: Int,
    isWifi: Boolean
): Int {
    if (relatedCount <= 0 || !isWifi) return 0
    // Avoid speculative playurl/detail requests for videos the user has not opened.
    return 0
}

internal fun resolvePlaybackIntentForSourceReplacement(
    playWhenReady: Boolean,
    isPlaying: Boolean
): Boolean {
    return playWhenReady || isPlaying
}

internal data class QualitySwitchFailureDialogState(
    val requestedQualityId: Int,
    val requestedQualityLabel: String,
    val title: String,
    val message: String
)

internal enum class InitialQualityUnavailableReason {
    DATA_SAVER,
    LOGIN_REQUIRED,
    VIP_REQUIRED,
    SERVER_DOWNGRADED
}

internal fun resolveInitialQualityUnavailableReason(
    requestedQualityId: Int,
    actualQualityId: Int,
    isLoggedIn: Boolean,
    isVip: Boolean,
    dataSaverLimited: Boolean
): InitialQualityUnavailableReason? {
    if (dataSaverLimited && requestedQualityId > 32) {
        return InitialQualityUnavailableReason.DATA_SAVER
    }
    if (requestedQualityId < 80 || actualQualityId >= requestedQualityId) {
        return null
    }
    if (requestedQualityId >= 112 && !isVip) {
        return InitialQualityUnavailableReason.VIP_REQUIRED
    }
    if (requestedQualityId >= 80 && !isLoggedIn) {
        return InitialQualityUnavailableReason.LOGIN_REQUIRED
    }
    return InitialQualityUnavailableReason.SERVER_DOWNGRADED
}

internal fun resolveInitialQualityWarningTarget(
    requestedQualityId: Int,
    isLoggedIn: Boolean,
    isVip: Boolean,
    resolvedTargetQuality: Int? = null,
    dataSaverLimited: Boolean = false
): Int {
    if (!dataSaverLimited) {
        resolvedTargetQuality?.takeIf { it > 0 }?.let { return it }
    }
    if (requestedQualityId < 127) return requestedQualityId
    return when {
        isVip -> 120
        isLoggedIn -> 80
        else -> 64
    }
}

internal fun shouldShowInitialQualityUnavailableDialog(
    unavailableReason: InitialQualityUnavailableReason?,
    premiumAutoUpgradeScheduled: Boolean
): Boolean = unavailableReason != null && !premiumAutoUpgradeScheduled


internal fun shouldBlockPremiumQualitySwitchDuringCooldown(
    requestedQualityId: Int,
    cacheContainsRequestedQuality: Boolean,
    appApiCooldownRemainingMs: Long
): Boolean {
    return requestedQualityId >= 112 &&
        !cacheContainsRequestedQuality &&
        appApiCooldownRemainingMs > 0L
}

internal fun formatQualitySwitchCooldownMessage(
    requestedQualityLabel: String,
    remainingMs: Long
): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val waitHint = if (minutes > 0L) {
        "大约 ${minutes} 分 ${seconds} 秒后"
    } else {
        "大约 ${seconds} 秒后"
    }
    return "$requestedQualityLabel 当前受接口风控影响，暂时拿不到可切换轨道。请 $waitHint 再试，或切换网络后重试。"
}

internal fun resolveQualitySwitchFailureMessage(
    requestedQualityLabel: String,
    permissionResult: QualityPermissionResult? = null,
    loadError: VideoLoadError? = null,
    hasCachedDashTracks: Boolean = true,
    cacheContainsRequestedQuality: Boolean = true,
    qualityRefetchCooldownRemainingMs: Long? = null,
    initialUnavailableReason: InitialQualityUnavailableReason? = null
): String {
    initialUnavailableReason?.let { reason ->
        return when (reason) {
            InitialQualityUnavailableReason.DATA_SAVER ->
                "$requestedQualityLabel 已被省流量模式限制为 480P。关闭省流量模式或切换到不受限网络后会重新请求高画质。"
            InitialQualityUnavailableReason.LOGIN_REQUIRED ->
                "$requestedQualityLabel 需要有效登录 Cookie，当前取流接口没有通过登录鉴权，所以服务端只返回了低画质。"
            InitialQualityUnavailableReason.VIP_REQUIRED ->
                "$requestedQualityLabel 属于大会员画质，当前账号不是大会员，已自动使用可播放的较低画质。"
            InitialQualityUnavailableReason.SERVER_DOWNGRADED ->
                "服务端没有返回 $requestedQualityLabel 的可播放轨道，可能是该分P不支持、接口临时降档或当前 Cookie 已失效。"
        }
    }

    permissionResult?.let { permission ->
        return when (permission) {
            is QualityPermissionResult.RequiresVip -> "$requestedQualityLabel 需要大会员，当前账号暂时不能切换到这个画质。"
            is QualityPermissionResult.RequiresLogin -> "$requestedQualityLabel 需要先登录，登录后再试一次就好。"
            is QualityPermissionResult.UnsupportedByDevice -> "$requestedQualityLabel 需要当前设备支持对应的显示能力或解码能力。"
            is QualityPermissionResult.Permitted -> ""
        }
    }

    loadError?.let { error ->
        return when (error) {
            is VideoLoadError.Timeout -> "请求 $requestedQualityLabel 超时了，网络或 CDN 可能正在抖动。"
            is VideoLoadError.NetworkError -> "请求 $requestedQualityLabel 失败，当前网络连接不稳定。"
            is VideoLoadError.VipRequired -> "$requestedQualityLabel 需要大会员，服务端没有返回可播放地址。"
            is VideoLoadError.RegionRestricted -> "$requestedQualityLabel 在当前地区不可用。"
            is VideoLoadError.PlayUrlEmpty -> "服务端没有返回 $requestedQualityLabel 的可播放地址，可能是接口临时降档。"
            else -> error.toUserMessage()
        }
    }

    qualityRefetchCooldownRemainingMs?.takeIf { it > 0L }?.let { remainingMs ->
        return formatQualitySwitchCooldownMessage(
            requestedQualityLabel = requestedQualityLabel,
            remainingMs = remainingMs
        )
    }

    if (!hasCachedDashTracks) {
        return "当前页面没有缓存到可切换轨道，重新请求目标画质时也没有拿到结果。"
    }
    if (!cacheContainsRequestedQuality) {
        return "当前视频没有返回 $requestedQualityLabel 的可播放轨道，可能是该分P暂不支持或接口临时降档。"
    }
    return "$requestedQualityLabel 的轨道已经找到，但播放器没能完成切换。"
}

internal fun buildQualitySwitchFailureDialogState(
    requestedQualityId: Int,
    requestedQualityLabel: String,
    permissionResult: QualityPermissionResult? = null,
    loadError: VideoLoadError? = null,
    hasCachedDashTracks: Boolean = true,
    cacheContainsRequestedQuality: Boolean = true,
    qualityRefetchCooldownRemainingMs: Long? = null,
    initialUnavailableReason: InitialQualityUnavailableReason? = null
): QualitySwitchFailureDialogState {
    return QualitySwitchFailureDialogState(
        requestedQualityId = requestedQualityId,
        requestedQualityLabel = requestedQualityLabel,
        title = if (initialUnavailableReason != null) {
            "未能使用 $requestedQualityLabel"
        } else {
            "切换到 $requestedQualityLabel 失败"
        },
        message = resolveQualitySwitchFailureMessage(
            requestedQualityLabel = requestedQualityLabel,
            permissionResult = permissionResult,
            loadError = loadError,
            hasCachedDashTracks = hasCachedDashTracks,
            cacheContainsRequestedQuality = cacheContainsRequestedQuality,
            qualityRefetchCooldownRemainingMs = qualityRefetchCooldownRemainingMs,
            initialUnavailableReason = initialUnavailableReason
        )
    )
}

internal fun shouldApplyPlayerInfoResult(
    activeRequestToken: Long,
    resultRequestToken: Long,
    expectedBvid: String,
    expectedCid: Long,
    currentBvid: String,
    currentCid: Long
): Boolean {
    return activeRequestToken == resultRequestToken &&
        expectedBvid == currentBvid &&
        expectedCid == currentCid
}

internal fun shouldApplySubtitleLoadResult(
    activeSubtitleToken: Long,
    resultSubtitleToken: Long,
    expectedBvid: String,
    expectedCid: Long,
    currentBvid: String,
    currentCid: Long
): Boolean {
    return activeSubtitleToken == resultSubtitleToken &&
        expectedBvid == currentBvid &&
        expectedCid == currentCid
}

internal fun buildSubtitleTrackBindingKey(
    subtitleId: Long,
    subtitleIdStr: String,
    languageCode: String,
    subtitleUrl: String = ""
): String {
    val idPart = subtitleIdStr.takeIf { it.isNotBlank() }
        ?: subtitleId.takeIf { it > 0L }?.toString()
        ?: "no-id"
    val baseKey = "${idPart}|${languageCode.ifBlank { "unknown" }}"
    val normalizedUrl = normalizeBilibiliSubtitleUrl(subtitleUrl)
    if (normalizedUrl.isBlank()) return baseKey
    val urlPathKey = runCatching {
        val uri = java.net.URI(normalizedUrl)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        when {
            host.isNotBlank() && path.isNotBlank() -> "$host$path"
            path.isNotBlank() -> path
            else -> ""
        }
    }.getOrDefault("")
    if (urlPathKey.isBlank()) return baseKey
    return "$baseKey|$urlPathKey"
}

internal fun shouldApplySubtitleTrackBinding(
    expectedTrackKey: String?,
    currentTrackKey: String?,
    expectedLanguage: String?,
    currentLanguage: String?
): Boolean {
    return resolveSubtitleTrackBindingMismatchReason(
        expectedTrackKey = expectedTrackKey,
        currentTrackKey = currentTrackKey,
        expectedLanguage = expectedLanguage,
        currentLanguage = currentLanguage
    ) == null
}

internal fun resolveSubtitleTrackBindingMismatchReason(
    expectedTrackKey: String?,
    currentTrackKey: String?,
    expectedLanguage: String?,
    currentLanguage: String?
): String? {
    val languageMatched = expectedLanguage.isNullOrBlank() || expectedLanguage == currentLanguage
    if (!languageMatched) {
        return "language-mismatch expected=$expectedLanguage current=$currentLanguage"
    }
    if (expectedTrackKey.isNullOrBlank()) return null
    if (expectedTrackKey == currentTrackKey) return null
    return "track-key-mismatch expected=$expectedTrackKey current=$currentTrackKey"
}

internal fun shouldRetrySubtitleLoadWithPlayerInfo(errorMessage: String?): Boolean {
    val msg = errorMessage?.lowercase().orEmpty()
    if (msg.isBlank()) return false
    return msg.contains("http 401") ||
        msg.contains("http 403") ||
        msg.contains("http 404") ||
        msg.contains("http 410") ||
        msg.contains("http 412")
}

internal fun shouldTreatAsSamePlaybackRequest(
    requestBvid: String,
    requestCid: Long,
    currentBvid: String,
    currentCid: Long,
    uiBvid: String?,
    uiCid: Long,
    miniPlayerBvid: String?,
    miniPlayerCid: Long,
    miniPlayerActive: Boolean
): Boolean {
    if (requestCid <= 0L) return false

    val effectiveBvid = currentBvid.takeIf { it.isNotBlank() }
        ?: uiBvid?.takeIf { it.isNotBlank() }
        ?: miniPlayerBvid?.takeIf { miniPlayerActive && it.isNotBlank() }
        ?: return false

    if (effectiveBvid != requestBvid) return false

    val effectiveCid = when {
        currentCid > 0L -> currentCid
        uiCid > 0L -> uiCid
        miniPlayerActive && miniPlayerCid > 0L -> miniPlayerCid
        else -> 0L
    }

    return effectiveCid > 0L && effectiveCid == requestCid
}

internal fun shouldRestoreAttachedPlayerFromLoadedUi(
    force: Boolean,
    requestBvid: String,
    requestCid: Long,
    requestAudioLang: String?,
    ignoreSavedProgress: Boolean,
    videoCodecOverride: String?,
    loadedBvid: String?,
    loadedCid: Long,
    loadedAudioLang: String?,
    loadedDirectPlayUrlAvailable: Boolean,
    loadedAdaptiveDashSourceAvailable: Boolean,
    attachedPlayerMediaItemCount: Int,
): Boolean {
    return shouldKeepLoadedVideoDetailUiWithoutSkeletonReload(
        force = force,
        requestBvid = requestBvid,
        requestCid = requestCid,
        requestAudioLang = requestAudioLang,
        ignoreSavedProgress = ignoreSavedProgress,
        videoCodecOverride = videoCodecOverride,
        loadedBvid = loadedBvid,
        loadedCid = loadedCid,
        loadedAudioLang = loadedAudioLang,
        loadedDirectPlayUrlAvailable = loadedDirectPlayUrlAvailable,
        loadedAdaptiveDashSourceAvailable = loadedAdaptiveDashSourceAvailable,
    ) && attachedPlayerMediaItemCount == 0
}

/**
 * 父详情 → 相关视频 → 返回父详情时，ViewModel 往往仍持有 Success，
 * 但播放器被换新实例。此时禁止再走 Loading.Initial 骨架，否则简介/相关列表
 * 被卸掉，滚动位置无法按直觉恢复。
 */
internal fun shouldKeepLoadedVideoDetailUiWithoutSkeletonReload(
    force: Boolean,
    requestBvid: String,
    requestCid: Long,
    requestAudioLang: String?,
    ignoreSavedProgress: Boolean,
    videoCodecOverride: String?,
    loadedBvid: String?,
    loadedCid: Long,
    loadedAudioLang: String?,
    loadedDirectPlayUrlAvailable: Boolean = true,
    loadedAdaptiveDashSourceAvailable: Boolean = false,
): Boolean {
    return !force &&
        !ignoreSavedProgress &&
        videoCodecOverride == null &&
        (loadedDirectPlayUrlAvailable || loadedAdaptiveDashSourceAvailable) &&
        loadedBvid == requestBvid &&
        (requestCid <= 0L || loadedCid == requestCid) &&
        (requestAudioLang == null || loadedAudioLang == requestAudioLang)
}

internal fun resolveExternalPlaylistSyncDecision(
    isExternalPlaylist: Boolean,
    playlist: List<PlaylistItem>,
    currentBvid: String
): ExternalPlaylistSyncDecision {
    if (!isExternalPlaylist || currentBvid.isBlank()) {
        return ExternalPlaylistSyncDecision(keepExternalPlaylist = false)
    }

    val matchIndex = playlist.indexOfFirst { it.bvid == currentBvid }
    return if (matchIndex >= 0) {
        ExternalPlaylistSyncDecision(
            keepExternalPlaylist = true,
            matchedIndex = matchIndex
        )
    } else {
        ExternalPlaylistSyncDecision(keepExternalPlaylist = false)
    }
}

internal fun clearSubtitleFields(state: VideoPlaybackUiState.Success): VideoPlaybackUiState.Success {
    return state.copy(
        subtitleEnabled = false,
        subtitleOwnerBvid = null,
        subtitleOwnerCid = 0L,
        subtitlePrimaryLanguage = null,
        subtitleSecondaryLanguage = null,
        subtitlePrimaryTrackKey = null,
        subtitleSecondaryTrackKey = null,
        subtitleTracks = emptyList(),
        subtitlePrimaryLikelyAi = false,
        subtitleSecondaryLikelyAi = false,
        subtitlePrimaryCues = emptyList(),
        subtitleSecondaryCues = emptyList()
    )
}

internal fun clearTransientPlaybackPreviewData(state: VideoPlaybackUiState.Success): VideoPlaybackUiState.Success {
    return if (state.videoshotData == null) state else state.copy(videoshotData = null)
}

internal fun shouldApplyVideoshotResult(
    currentState: VideoPlaybackUiState.Success,
    videoshotBvid: String,
    videoshotCid: Long
): Boolean {
    return currentState.info.bvid == videoshotBvid && currentState.info.cid == videoshotCid
}

internal data class SubtitleTrackLoadDecision(
    val primaryLanguage: String?,
    val secondaryLanguage: String?,
    val primaryLikelyAi: Boolean,
    val secondaryLikelyAi: Boolean,
    val primaryCues: List<SubtitleCue>,
    val secondaryCues: List<SubtitleCue>
)

internal fun isLikelyLowQualitySubtitleTrack(
    cues: List<SubtitleCue>,
    otherTrackCueCount: Int
): Boolean {
    if (cues.isEmpty()) return true
    if (otherTrackCueCount < 8) return false

    if (cues.size <= 2 && otherTrackCueCount >= 8) {
        return true
    }

    if (cues.size == 1) {
        val only = cues.first()
        val durationMs = (only.endMs - only.startMs).coerceAtLeast(0L)
        if (durationMs >= 20_000L && otherTrackCueCount >= 6) {
            return true
        }
    }

    return false
}

internal fun resolveSubtitleTrackLoadDecision(
    primaryLanguage: String,
    primaryCues: List<SubtitleCue>,
    primaryLikelyAi: Boolean = false,
    secondaryLanguage: String?,
    secondaryCues: List<SubtitleCue>,
    secondaryLikelyAi: Boolean = false
): SubtitleTrackLoadDecision {
    if (secondaryLanguage.isNullOrBlank()) {
        return SubtitleTrackLoadDecision(
            primaryLanguage = primaryLanguage.takeIf { primaryCues.isNotEmpty() },
            secondaryLanguage = null,
            primaryLikelyAi = primaryLikelyAi,
            secondaryLikelyAi = false,
            primaryCues = primaryCues,
            secondaryCues = emptyList()
        )
    }

    val primaryLowQuality = isLikelyLowQualitySubtitleTrack(
        cues = primaryCues,
        otherTrackCueCount = secondaryCues.size
    )
    val secondaryLowQuality = isLikelyLowQualitySubtitleTrack(
        cues = secondaryCues,
        otherTrackCueCount = primaryCues.size
    )

    return when {
        !primaryLowQuality && !secondaryLowQuality -> SubtitleTrackLoadDecision(
            primaryLanguage = primaryLanguage.takeIf { primaryCues.isNotEmpty() },
            secondaryLanguage = secondaryLanguage.takeIf { secondaryCues.isNotEmpty() },
            primaryLikelyAi = primaryLikelyAi,
            secondaryLikelyAi = secondaryLikelyAi,
            primaryCues = primaryCues,
            secondaryCues = secondaryCues
        )
        primaryLowQuality && !secondaryLowQuality -> SubtitleTrackLoadDecision(
            primaryLanguage = secondaryLanguage.takeIf { secondaryCues.isNotEmpty() },
            secondaryLanguage = null,
            primaryLikelyAi = secondaryLikelyAi,
            secondaryLikelyAi = false,
            primaryCues = secondaryCues,
            secondaryCues = emptyList()
        )
        !primaryLowQuality && secondaryLowQuality -> SubtitleTrackLoadDecision(
            primaryLanguage = primaryLanguage.takeIf { primaryCues.isNotEmpty() },
            secondaryLanguage = null,
            primaryLikelyAi = primaryLikelyAi,
            secondaryLikelyAi = false,
            primaryCues = primaryCues,
            secondaryCues = emptyList()
        )
        else -> {
            val usePrimary = primaryCues.size >= secondaryCues.size
            if (usePrimary) {
                SubtitleTrackLoadDecision(
                    primaryLanguage = primaryLanguage.takeIf { primaryCues.isNotEmpty() },
                    secondaryLanguage = null,
                    primaryLikelyAi = primaryLikelyAi,
                    secondaryLikelyAi = false,
                    primaryCues = primaryCues,
                    secondaryCues = emptyList()
                )
            } else {
                SubtitleTrackLoadDecision(
                    primaryLanguage = secondaryLanguage.takeIf { secondaryCues.isNotEmpty() },
                    secondaryLanguage = null,
                    primaryLikelyAi = secondaryLikelyAi,
                    secondaryLikelyAi = false,
                    primaryCues = secondaryCues,
                    secondaryCues = emptyList()
                )
            }
        }
    }
}

internal enum class QualityChangeReason {
    USER_EXPLICIT,
    INITIAL_AUTO_UPGRADE
}

internal fun buildPremiumAutoUpgradePlaybackKey(
    bvid: String,
    cid: Long,
    audioLang: String?
): String = "$bvid:$cid:${audioLang.orEmpty()}"

internal fun hasExplicitQualitySelectionForPlayback(
    playbackKey: String,
    explicitSelectionKeys: Set<String>
): Boolean = playbackKey in explicitSelectionKeys

internal fun shouldReplacePlaybackSourceForQualityChange(
    reason: QualityChangeReason,
    cdnSelectionChangedUrl: Boolean
): Boolean {
    return reason == QualityChangeReason.INITIAL_AUTO_UPGRADE || cdnSelectionChangedUrl
}

// ========== ViewModel ==========
class VideoPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    // UseCases
    private val playbackUseCase = VideoPlaybackUseCase()
    private val playbackLoader = PlaybackLoader.from(playbackUseCase)
    private val playbackSessionStore = PlaybackSessionStore()
    private val playbackCoordinator = PlaybackCoordinator(playbackSessionStore)
    private val interactionUseCase = VideoInteractionUseCase()
    private val qualityManager = QualityManager()
    private val playbackCdnPreference = SettingsManager
        .getPlaybackCdnPreference(application.applicationContext)
        .map { PlaybackCdnPreference.fromStorageValue(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackCdnPreference.BASE_URL)

    // HDR auto-upgrade state
    private val attemptedUpgradeKeys = mutableSetOf<String>()
    private val explicitQualitySelectionKeys = mutableSetOf<String>()
    private var explicitQualitySelectionGeneration: Long = 0L
    private var hdrAutoUpgradeJob: Job? = null

    //  插件系统（替代旧的SponsorBlockUseCase）
    private var pluginCheckJob: Job? = null
    private var playbackCdnFallbackJob: Job? = null
    private var playbackCdnPrefetchJob: Job? = null
    private var playbackCdnFallbackState: PlaybackCdnFallbackState = PlaybackCdnFallbackState.Inactive
    private var playbackStallRecoveryJob: Job? = null
    private var playbackStallRecoveryFirstFrameRendered = false
    private var playbackStallRecoveryMediaKey = ""
    private val attemptedPlaybackStallRecoveryCdnIndexes = mutableSetOf<Int>()
    
    // State
    private val _uiState = MutableStateFlow<VideoPlaybackUiState>(VideoPlaybackUiState.Loading.Initial)
    // A stale playback snapshot must not overwrite metadata already loaded for this video.
    // Keep labels per bvid/cid, independently of the shared plugin's mutable playback data.
    private val sponsorVideoLabels = MutableStateFlow<Map<Pair<String, Long>, String>>(emptyMap())

    private fun updateSponsorVideoLabel(
        bvid: String,
        cid: Long,
        segments: List<com.android.purebilibili.data.model.response.SponsorSegment>
    ) {
        val label = segments.resolveSponsorVideoLabel()
        val videoKey = bvid to cid
        // Preserve known metadata during reloads; disabling the assistant clears it.
        sponsorVideoLabels.update { labels ->
            if (label.isEmpty() || labels[videoKey] == label) {
                labels
            } else {
                (labels - videoKey + (videoKey to label)).entries.toList().takeLast(16)
                    .associate { it.key to it.value }
            }
        }
        val retainedLabel = sponsorVideoLabels.value[videoKey].orEmpty()
        // 原子 RMW：label 写入慢（插件网络返回后），与其它 uiState 更新交错时
        // 先读后写的 copy 会互相覆盖，表现为徽标概率性丢失。
        _uiState.update { current ->
            if (current is VideoPlaybackUiState.Success &&
                current.info.bvid == bvid && current.info.cid == cid &&
                current.sponsorVideoLabel != retainedLabel
            ) {
                current.copy(sponsorVideoLabel = retainedLabel)
            } else {
                current
            }
        }
    }
    val uiState: StateFlow<VideoPlaybackUiState> = combine(_uiState, sponsorVideoLabels) { state, labels ->
        if (state is VideoPlaybackUiState.Success) {
            val label = labels[state.info.bvid to state.info.cid].orEmpty()
            if (state.sponsorVideoLabel == label) state else state.copy(sponsorVideoLabel = label)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private val _subjectSnapshot = MutableStateFlow<VideoSubjectSnapshot?>(null)
    val subjectSnapshot = _subjectSnapshot.asStateFlow()
    private val _detailState = MutableStateFlow(VideoDetailUiState())
    val detailState = _detailState.asStateFlow()
    private val playbackPreparationAllowed = MutableStateFlow(true)
    private var relatedLoadJob: Job? = null
    private val relatedLoadScheduler = RelatedVideoLoadScheduler()
    private val videoNetworkPressureObserver = VideoNetworkPressureObserver(application)
    private var relatedPlaybackPreparedToken = -1L
    private var relatedFirstFrameToken = -1L
    private val videoLoadTimeline = VideoLoadTimeline(android.os.SystemClock::elapsedRealtime)

    private fun logVideoLoadStage(stage: VideoLoadStage, requestToken: Long) {
        val elapsedMs = videoLoadTimeline.mark(stage, requestToken) ?: return
        Logger.d("VideoLoadTiming", "bvid=$currentBvid token=$requestToken stage=$stage elapsedMs=$elapsedMs")
    }

    fun setPlaybackPreparationAllowed(allowed: Boolean) {
        playbackPreparationAllowed.value = allowed
    }

    fun cancelPendingVideoLoad() {
        activeLoadJob?.cancel()
        activeLoadJob = null
        relatedLoadJob?.cancel()
        relatedLoadJob = null
    }

    private fun publishVideoDetailInfo(info: ViewInfo, requestBvid: String, requestToken: Long) {
        if (currentLoadRequestToken != requestToken) return
        logVideoLoadStage(VideoLoadStage.DETAILS_READY, requestToken)
        _detailState.update { state ->
            if (state.requestToken == requestToken) {
                state.copy(
                    info = info,
                    isLoggedIn = VideoRepository.isPlaybackLoggedIn(),
                    isVip = VideoRepository.isPlaybackVip()
                )
            } else state
        }
        publishSubjectSnapshot(
            VideoPlaybackUiState.Success(
                info = info,
                playUrl = "",
                videoDurationMs = resolveVideoLoadDurationMs(0L, info)
            )
        )
        if (!requestBvid.startsWith("BV", ignoreCase = true) && relatedLoadJob == null) {
            loadRelatedVideosForRequest(info.bvid, requestToken)
        }
    }
    private var subjectGeneration: Long = 0L
    
    private val _toastEvent = Channel<PlayerToastMessage>()
    val toastEvent = _toastEvent.receiveAsFlow()
    private val _qualitySwitchFailureDialog = MutableStateFlow<QualitySwitchFailureDialogState?>(null)
    internal val qualitySwitchFailureDialog = _qualitySwitchFailureDialog.asStateFlow()

    val resumePlaybackSuggestion = playbackSessionStore.state
        .map { session -> session.resumeSuggestion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    

    fun dismissQualitySwitchFailureDialog() {
        _qualitySwitchFailureDialog.value = null
    }

    fun applyPlaybackSpeedFromUi(speed: Float): Boolean {
        val player = exoPlayer ?: return false
        val previousSpeed = player.playbackParameters.speed
        val normalizedSpeed = speed.coerceAtLeast(0.1f)
        player.playbackParameters = PlaybackParameters(normalizedSpeed, 1.0f)

        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return true
        if (current.isQualitySwitching) return true

        val audioPreference = current.requestedAudioQuality
        if (!shouldRefreshPremiumAudioForPlaybackSpeedChange(
                requestedAudioQuality = audioPreference,
                previousPlaybackSpeed = previousSpeed,
                nextPlaybackSpeed = normalizedSpeed
            )
        ) {
            return true
        }

        viewModelScope.launch {
            refreshPlaybackAudioForSpeedCompatibility(
                current = current,
                audioPreference = audioPreference,
                currentPos = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = player.playWhenReady
            )
        }
        return true
    }

    private suspend fun refreshPlaybackAudioForSpeedCompatibility(
        current: VideoPlaybackUiState.Success,
        audioPreference: Int,
        currentPos: Long,
        playWhenReady: Boolean
    ): Boolean {
        val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
        val videoCodecPreference = resolveEffectiveVideoCodecPreference(
            requestCodecOverride = null,
            settingsCodecPreference = _videoCodecPreference.value,
            sessionBlockedCodecs = sessionBlockedCodecs
        )
        val videoSecondCodecPreference = _videoSecondCodecPreference.value
        val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
        val isAv1Supported = resolveEffectiveAv1Support(
            deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
            sessionBlockedCodecs = sessionBlockedCodecs
        )
        val result = playbackUseCase.changeQualityFromCache(
            qualityId = current.currentQuality,
            cachedVideos = current.cachedDashVideos,
            cachedAudios = current.cachedDashAudios,
            cachedDash = current.cachedDash,
            currentPos = currentPos,
            durationMs = current.videoDurationMs,
            playbackQualityMode = current.playbackQualityMode,
            audioQualityPreference = audioPreference,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported,
            playWhenReady = playWhenReady
        ) ?: playbackUseCase.changeQualityFromApi(
            bvid = currentBvid,
            cid = currentCid,
            qualityId = current.currentQuality,
            currentPos = currentPos,
            playbackQualityMode = current.playbackQualityMode,
            audioQualityPreference = audioPreference,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported,
            playWhenReady = playWhenReady
        ) ?: return false

        val nextCachedDashVideos = result.cachedDashVideos.ifEmpty { current.cachedDashVideos }
        val nextCachedDashAudios = result.cachedDashAudios.ifEmpty { current.cachedDashAudios }
        val nextCachedDash = result.cachedDash ?: current.cachedDash
        val cdnSelection = resolvePlaybackCdnCandidateSelection(
            videoUrl = result.videoUrl,
            audioUrl = result.audioUrl,
            quality = result.actualQuality,
            cachedDashVideos = nextCachedDashVideos,
            cachedDashAudios = nextCachedDashAudios,
            adaptiveDashSource = result.adaptiveDashSource
        )
        if (cdnSelection.playUrl != result.videoUrl || cdnSelection.audioUrl != result.audioUrl) {
            playResolvedPlayback(
                videoUrl = cdnSelection.playUrl,
                audioUrl = cdnSelection.audioUrl,
                adaptiveDashSource = cdnSelection.adaptiveDashSource,
                startPositionMs = currentPos,
                playWhenReady = playWhenReady,
                cdnFallbackState = cdnSelection.fallbackState,
                cdnCacheKeysByUrl = cdnSelection.cdnCacheKeysByUrl
            )
        } else {
            armPlaybackCdnFallback(cdnSelection.fallbackState, playWhenReady)
        }
        _uiState.value = current.copy(
            playUrl = cdnSelection.playUrl,
            audioUrl = cdnSelection.audioUrl,
            currentQuality = result.actualQuality,
            adaptiveDashSource = cdnSelection.adaptiveDashSource,
            cachedDashVideos = nextCachedDashVideos,
            cachedDashAudios = nextCachedDashAudios,
            cachedDash = nextCachedDash,
            requestedAudioQuality = result.requestedAudioQuality,
            selectedAudioQuality = result.selectedAudioQuality,
            availableAudioQualities = result.availableAudioQualities
                .ifEmpty { current.availableAudioQualities },
            audioFallbackReason = result.audioFallbackReason,
            allVideoUrls = cdnSelection.allVideoUrls,
            allAudioUrls = cdnSelection.allAudioUrls,
            cdnCandidateSources = cdnSelection.candidateSources,
            cdnLineDiagnostics = cdnSelection.lineDiagnostics,
            currentCdnIndex = 0,
            qualityIds = result.qualityIds.ifEmpty { current.qualityIds },
            qualityLabels = result.qualityLabels.ifEmpty { current.qualityLabels },
            switchableQualityIds = result.switchableQualityIds.ifEmpty { current.switchableQualityIds }
        )
        return true
    }
    
    //  SponsorBlock (via Plugin)
    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton = _showSkipButton.asStateFlow()
    private val _currentSkipReason = MutableStateFlow<String?>( null)
    val currentSkipReason = _currentSkipReason.asStateFlow()
    private val _currentSponsorSegment = MutableStateFlow<SponsorSegment?>(null)
    val currentSponsorSegment = _currentSponsorSegment.asStateFlow()
    private val _sponsorSkipUiState = MutableStateFlow(SponsorSkipUiState())
    private val _sponsorProgressMarkers =
        MutableStateFlow<List<com.android.purebilibili.data.model.response.SponsorProgressMarker>>(emptyList())
    val sponsorProgressMarkers = _sponsorProgressMarkers.asStateFlow()
    private var sponsorBlockLoadedVideo: Pair<String, Long>? = null
    private val sponsorPlaybackPlugin = com.android.purebilibili.feature.plugin.SponsorBlockPlugin()
    private var sponsorBlockRetryVideo: Pair<String, Long>? = null
    private var sponsorBlockRetryAtMs = 0L
    private var sponsorBlockLoadFailures = 0

    /** PluginManager owns settings; mutable playback state belongs to this ViewModel. */
    private fun getSessionPlayerPlugins(): List<com.android.purebilibili.core.plugin.PlayerPlugin> =
        PluginManager.getEnabledPlayerPlugins().map { plugin ->
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) sponsorPlaybackPlugin else plugin
        }

    private val _sponsorContributionUiState = MutableStateFlow(SponsorContributionUiState())
    val sponsorContributionUiState = _sponsorContributionUiState.asStateFlow()
    private var sponsorContributionRequest: SponsorContributionRequest? = null
    private var sponsorMuteRestoreAtMs: Long? = null
    private var sponsorMutedOriginalVolume: Float? = null
    
    //  Download state
    private val _downloadProgress = MutableStateFlow(-1f)
    /** 当前下载进度监听任务；新下载会替换旧监听，避免每次下载都残留一个收集器 */
    private var downloadProgressJob: kotlinx.coroutines.Job? = null

    private fun watchDownloadProgress(taskId: String) {
        downloadProgressJob?.cancel()
        downloadProgressJob = viewModelScope.launch {
            com.android.purebilibili.feature.download.DownloadManager.tasks.collect { tasks ->
                val downloadTask = tasks[taskId]
                _downloadProgress.value = downloadTask?.progress ?: -1f
            }
        }
    }
    val downloadProgress = _downloadProgress.asStateFlow()
    
    //  [新增] 视频章节/看点数据
    private val _viewPoints = MutableStateFlow<List<ViewPoint>>(emptyList())
    val viewPoints = _viewPoints.asStateFlow()
    private val _pbpProgressData = MutableStateFlow<PbpProgressData?>(null)
    val pbpProgressData = _pbpProgressData.asStateFlow()
    /** 本地弹幕密度兜底的进行中任务与已构建 cid 记录。 */
    private var densityFallbackJob: Job? = null
    private var densityFallbackLoadedCid: Long = 0L

    private val _interactiveChoicePanel = MutableStateFlow(InteractiveChoicePanelUiState())
    val interactiveChoicePanel = _interactiveChoicePanel.asStateFlow()

    private var interactiveGraphVersion: Long = 0L
    private var interactiveCurrentEdgeId: Long = 0L
    private var interactiveQuestionMonitorJob: Job? = null
    private var interactiveCountdownJob: Job? = null
    private var isApplyingInteractiveChoice = false
    private var interactivePausedByQuestion = false
    private val interactiveHiddenVariables = mutableMapOf<String, Double>()
    private val interactiveEdgeStartPositionMs = mutableMapOf<Long, Long>()
    
    // [新增] 播放完成选择对话框状态
    private val _showPlaybackEndedDialog = MutableStateFlow(false)
    val showPlaybackEndedDialog = _showPlaybackEndedDialog.asStateFlow()
    
    fun dismissPlaybackEndedDialog() {
        _showPlaybackEndedDialog.value = false
    }
    
    fun showPlaybackEndedDialogIfNeeded() {
        // UX: 用户关闭“自动播放下一个”后，播放结束不再弹强干扰对话框
        _showPlaybackEndedDialog.value = false
    }
    
    // [New] Danmaku Input Dialog State (Kept)

    // [New] Danmaku Input Dialog State
    private val _showDanmakuInputDialog = MutableStateFlow(false)
    val showDanmakuInputDialog = _showDanmakuInputDialog.asStateFlow()

    fun showDanmakuInputDialog() {
        _showDanmakuInputDialog.value = true
    }

    fun dismissDanmakuInputDialog() {
        _showDanmakuInputDialog.value = false
    }

    fun dismissInteractiveChoicePanel() {
        interactiveQuestionMonitorJob?.cancel()
        interactiveCountdownJob?.cancel()
        _interactiveChoicePanel.value = _interactiveChoicePanel.value.copy(visible = false, remainingMs = null)
        if (interactivePausedByQuestion) {
            exoPlayer?.play()
            interactivePausedByQuestion = false
        }
    }

    fun selectInteractiveChoice(edgeId: Long, cid: Long) {
        if (cid <= 0L || isApplyingInteractiveChoice) return
        val selectedChoice = _interactiveChoicePanel.value.choices
            .firstOrNull { it.edgeId == edgeId && it.cid == cid }
        val resolvedEdgeId = selectedChoice?.edgeId ?: edgeId
        if (resolvedEdgeId <= 0L) return
        isApplyingInteractiveChoice = true
        interactiveQuestionMonitorJob?.cancel()
        interactiveCountdownJob?.cancel()
        _interactiveChoicePanel.value = _interactiveChoicePanel.value.copy(visible = false, remainingMs = null)
        viewModelScope.launch {
            selectedChoice?.nativeAction
                ?.takeIf { it.isNotBlank() }
                ?.let { action ->
                    applyInteractiveNativeAction(
                        nativeAction = action,
                        variables = interactiveHiddenVariables
                    )
                }
            interactiveCurrentEdgeId = resolvedEdgeId
            val switched = switchToInteractiveCid(
                targetCid = cid,
                targetEdgeId = resolvedEdgeId
            )
            if (switched) {
                if (interactivePausedByQuestion) {
                    exoPlayer?.play()
                }
            } else {
                toast("互动分支切换失败")
            }
            interactivePausedByQuestion = false
            isApplyingInteractiveChoice = false
        }
    }
    
    // Internal state
    private val playbackSessionState: com.android.purebilibili.feature.video.playback.session.PlaybackSessionState
        get() = playbackSessionStore.state.value

    private var currentBvid: String
        get() = playbackSessionState.currentBvid
        set(value) {
            playbackSessionStore.updateCurrentMedia(
                bvid = value,
                cid = playbackSessionState.currentCid
            )
        }

    private var currentCid: Long
        get() = playbackSessionState.currentCid
        set(value) {
            playbackSessionStore.updateCurrentMedia(
                bvid = playbackSessionState.currentBvid,
                cid = value
            )
        }

    private var exoPlayer: ExoPlayer? = null
    // Invalidates delayed error recovery as soon as a replacement is requested,
    // including switches whose network request has not completed yet.
    internal var playbackRecoveryGeneration: Long = 0L
        private set
    private data class PendingQualityTelemetry(
        val bvid: String,
        val cid: Long,
        val requestToken: Long,
        val collectionGeneration: Long,
        val attempt: com.android.purebilibili.core.util.QualitySwitchTelemetryAttempt
    )
    private var pendingQualityTelemetry: PendingQualityTelemetry? = null
    private val playbackAnalyticsTracker = com.android.purebilibili.core.util.PlaybackAnalyticsTracker()
    private var heartbeatJob: Job? = null
    private var heartbeatSessionStartTsSec: Long = 0L
    private var heartbeatAccumulatedPlayMs: Long = 0L
    private var heartbeatActivePlayStartElapsedMs: Long? = null
    private var lastReportedHeartbeatSnapshot: PlaybackHeartbeatSnapshot? = null
    private var lastPluginDispatchPositionMs: Long? = null
    private var appContext: android.content.Context? = null  //  [新增] 保存 Context 用于网络检测
    private var hasUserStartedPlayback = false  // 🛡️ [修复] 用户是否主动开始播放（用于区分“加载已看完视频”和“自然播放结束”）
    private var isPortraitPlaybackSessionActive = false
    @Volatile
    private var isCommentInteractionActive = false
    private val followStatusCheckInFlight = mutableSetOf<Long>()
    private var cachedFollowingSession: Any? = null
    private var cachedFollowingRevision: Long = -1L
    private var followingMidsRequest: Pair<Any, Long>? = null
    private var cachedFollowingOwnerMid: Long = 0L
    private var cachedFollowingMids: Set<Long> = emptySet()
    private var cachedFollowingLoadedAtMs: Long = 0L
    private var hasFollowingCache: Boolean = false
    private var isFollowingMidsLoading: Boolean = false
    private val followingMidsCacheTtlMs: Long = 10 * 60 * 1000L
    private var lastCreatorSignalPositionSec: Long = -1L

    private var subtitleLoadToken: Long
        get() = playbackSessionState.subtitleLoadToken
        set(value) {
            playbackSessionStore.setSubtitleLoadToken(value)
        }

    private var currentLoadRequestToken: Long
        get() = playbackSessionState.currentLoadRequestToken
        set(value) {
            playbackSessionStore.setCurrentLoadRequestToken(value)
        }

    private var activeLoadJob: Job? = null
    private var pageSwitchJob: Job? = null
    private var pageSwitchGeneration: Long = 0L
    private var pendingPageSwitchCid: Long? = null
    private var onPageIdentityCommitted: ((String, Long) -> Unit)? = null

    /**
     * 最近一次由播放器内部发起的原地换片身份（bvid to cid，如合集/队列自动连播）。
     * 详情页 presentation 守卫用它区分“VM 自己推进了身份”（预期错位：既不重载旧视频，
     * 也不同步 presentation，保持与直开场景一致的陈旧 identity）
     * 与“presentation 领先于 VM”（如从子详情页返回，应恢复旧视频）。
     * UI 发起的切换（合集面板选集等）在调用 loadVideo 前已同步 presentation，不打此标记。
     */
    @Volatile
    private var inPageInitiatedPlaybackIdentity: Pair<String, Long>? = null

    internal fun peekInPageInitiatedPlaybackIdentityBvid(): String? {
        return inPageInitiatedPlaybackIdentity?.first
    }

    private fun markInPageInitiatedPlayback(bvid: String, cid: Long) {
        inPageInitiatedPlaybackIdentity = bvid to cid
    }

    private var playerInfoJob: Job? = null
    private var aiSummaryJob: Job? = null
    private var videoNoteJob: Job? = null
    
    //  Public Player Accessor
    val currentPlayer: Player?
        get() = exoPlayer
        
    /**
     *  UI 仅音频模式状态
     * 
     * 注意：这与 SettingsManager.MiniPlayerMode.BACKGROUND 是两个不同的概念：
     * - isInAudioMode: UI 层的仅音频显示模式，用户主动切换，显示音频播放界面
     * - MiniPlayerMode.BACKGROUND: 设置层的后台音频模式，应用退到后台时的行为
     * 
     * isInAudioMode 控制 UI 显示，MiniPlayerMode.BACKGROUND 控制后台行为
     */
    private val _isInAudioMode = MutableStateFlow(false)
    val isInAudioMode = _isInAudioMode.asStateFlow()
    
    fun setAudioMode(enabled: Boolean) {
        _isInAudioMode.value = enabled
        if (enabled) {
            val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
            updatePlaylist(current.info, current.related)
        }
    }

    fun setPortraitPlaybackSessionActive(active: Boolean) {
        isPortraitPlaybackSessionActive = active
    }

    fun setCommentInteractionActive(active: Boolean) {
        isCommentInteractionActive = active
    }

    //  Sleep Timer State
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()
    private var sleepTimerJob: Job? = null

    /**
     * 设置定时关闭
     * @param minutes 分钟数，null 表示关闭定时
     */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        
        if (minutes != null) {
            sleepTimerJob = viewModelScope.launch {
                Logger.d("PlayerVM", "⏰ 定时关闭已启动: ${minutes}分钟")
                toast("将在 ${minutes} 分钟后停止播放")
                delay(minutes * 60 * 1000L)
                
                // 定时结束
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    exoPlayer?.pause()
                    toast("⏰ 定时结束，已暂停播放")
                    _sleepTimerMinutes.value = null
                    // 如果需要关闭应用或退出页面，可以在这里添加逻辑
                }
            }
        } else {
            Logger.d("PlayerVM", "⏰ 定时关闭已取消")
            toast("定时关闭已取消")
        }
    }
    
    // ========== 收藏夹相关状态 ==========
    private val _favoriteFolderDialogVisible = MutableStateFlow(false)
    val favoriteFolderDialogVisible = _favoriteFolderDialogVisible.asStateFlow()
    
    private val _favoriteFolders = MutableStateFlow<List<com.android.purebilibili.data.model.response.FavFolder>>(emptyList())
    val favoriteFolders = _favoriteFolders.asStateFlow()
    
    private val _isFavoriteFoldersLoading = MutableStateFlow(false)
    val isFavoriteFoldersLoading = _isFavoriteFoldersLoading.asStateFlow()

    private val _favoriteSelectedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSelectedFolderIds = _favoriteSelectedFolderIds.asStateFlow()

    private val _isSavingFavoriteFolders = MutableStateFlow(false)
    val isSavingFavoriteFolders = _isSavingFavoriteFolders.asStateFlow()

    private val _favoriteFolderSaveEvent = MutableStateFlow<FavoriteFolderSaveEvent?>(null)
    internal val favoriteFolderSaveEvent = _favoriteFolderSaveEvent.asStateFlow()

    private var lastSavedFavoriteFolderIds: Set<Long> = emptySet()
    private var favoriteFoldersBoundAid: Long? = null
    private var favoriteFolderSaveEventVersion: Long = 0L

    private val _followGroupDialogVisible = MutableStateFlow(false)
    val followGroupDialogVisible = _followGroupDialogVisible.asStateFlow()

    private val _followGroupTags = MutableStateFlow<List<com.android.purebilibili.data.model.response.RelationTagItem>>(emptyList())
    val followGroupTags = _followGroupTags.asStateFlow()

    private val _followGroupSelectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val followGroupSelectedTagIds = _followGroupSelectedTagIds.asStateFlow()

    private val _isFollowGroupsLoading = MutableStateFlow(false)
    val isFollowGroupsLoading = _isFollowGroupsLoading.asStateFlow()

    private val _isSavingFollowGroups = MutableStateFlow(false)
    val isSavingFollowGroups = _isSavingFollowGroups.asStateFlow()

    private var followGroupTargetMid: Long = 0L
    
    fun showFavoriteFolderDialog(requestedAid: Long? = null) {
        val currentAid = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.aid
        val targetAid = resolveFavoriteFolderDialogTargetAid(
            requestedAid = requestedAid,
            currentAid = currentAid
        ) ?: return
        if (favoriteFoldersBoundAid != null && favoriteFoldersBoundAid != targetAid) {
            lastSavedFavoriteFolderIds = emptySet()
            _favoriteSelectedFolderIds.value = emptySet()
            _favoriteFolders.value = emptyList()
        }
        _favoriteFolderDialogVisible.value = true
        _favoriteSelectedFolderIds.value = lastSavedFavoriteFolderIds
        val hasCacheForCurrentAid =
            favoriteFoldersBoundAid == targetAid && _favoriteFolders.value.isNotEmpty()
        if (!hasCacheForCurrentAid) {
            loadFavoriteFolders(aid = targetAid)
        }
    }
    
    fun dismissFavoriteFolderDialog() {
        _favoriteFolderDialogVisible.value = false
    }

    fun invalidateFavoriteFolderCache() {
        favoriteFoldersBoundAid = null
        lastSavedFavoriteFolderIds = emptySet()
        _favoriteFolders.value = emptyList()
        _favoriteSelectedFolderIds.value = emptySet()
    }
    
    private fun loadFavoriteFolders(aid: Long? = null, keepCurrentSelection: Boolean = false) {
        viewModelScope.launch {
            favoriteFoldersBoundAid = aid
            _isFavoriteFoldersLoading.value = true
            val result = interactionUseCase.getFavoriteFolders(aid)
            result.fold(
                onSuccess = { folders ->
                    _favoriteFolders.value = folders
                    val selectedFromServer = folders
                        .asSequence()
                        .filter { it.fav_state == 1 }
                        .map { resolveFavoriteFolderMediaId(it) }
                        .filter { it > 0L }
                        .toSet()

                    lastSavedFavoriteFolderIds = selectedFromServer

                    _favoriteSelectedFolderIds.value = if (keepCurrentSelection) {
                        val availableFolderIds = folders
                            .asSequence()
                            .map { resolveFavoriteFolderMediaId(it) }
                            .filter { it > 0L }
                            .toSet()
                        val keptSelection = _favoriteSelectedFolderIds.value.intersect(availableFolderIds)
                        if (keptSelection.isEmpty() && selectedFromServer.isNotEmpty()) {
                            selectedFromServer
                        } else {
                            keptSelection
                        }
                    } else {
                        selectedFromServer
                    }

                    updateFavoriteUiState(
                        targetAid = aid,
                        selectedFolderIds = lastSavedFavoriteFolderIds
                    )
                },
                onFailure = { e ->
                    toast("加载收藏夹失败: ${e.message}")
                }
            )
            _isFavoriteFoldersLoading.value = false
        }
    }

    fun toggleFavoriteFolderSelection(folderId: Long) {
        if (folderId <= 0L) return
        _favoriteSelectedFolderIds.update { selected ->
            if (selected.contains(folderId)) {
                selected - folderId
            } else {
                selected + folderId
            }
        }
    }

    fun toggleFavoriteFolderSelection(folder: com.android.purebilibili.data.model.response.FavFolder) {
        toggleFavoriteFolderSelection(resolveFavoriteFolderMediaId(folder))
    }

    fun saveFavoriteFolderSelection() {
        if (_isSavingFavoriteFolders.value) return
        val currentAid = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.aid
        val targetAid = resolveFavoriteFolderDialogTargetAid(
            requestedAid = favoriteFoldersBoundAid,
            currentAid = currentAid
        ) ?: return

        val selectedFolderIds = _favoriteSelectedFolderIds.value
        val originalFolderIds = lastSavedFavoriteFolderIds
        val mutation = resolveFavoriteFolderMutation(
            original = originalFolderIds,
            selected = selectedFolderIds
        )

        if (mutation.addFolderIds.isEmpty() && mutation.removeFolderIds.isEmpty()) {
            dismissFavoriteFolderDialog()
            toast("收藏夹未变更")
            return
        }

        viewModelScope.launch {
            _isSavingFavoriteFolders.value = true
            val result = interactionUseCase.updateFavoriteFolders(
                aid = targetAid,
                addFolderIds = mutation.addFolderIds,
                removeFolderIds = mutation.removeFolderIds
            )

            result.onSuccess {
                lastSavedFavoriteFolderIds = selectedFolderIds
                _favoriteFolders.update { folders ->
                    folders.map { folder ->
                        folder.copy(
                            fav_state = if (selectedFolderIds.contains(resolveFavoriteFolderMediaId(folder))) 1 else 0
                        )
                    }
                }
                if (shouldSyncFavoriteFolderUiState(targetAid = targetAid, currentAid = currentAid)) {
                    applyFavoriteSaveUiState(
                        originalFolderIds = originalFolderIds,
                        selectedFolderIds = selectedFolderIds
                    )
                }
                favoriteFolderSaveEventVersion += 1L
                _favoriteFolderSaveEvent.value = FavoriteFolderSaveEvent(
                    aid = targetAid,
                    isFavorited = selectedFolderIds.isNotEmpty(),
                    version = favoriteFolderSaveEventVersion
                )
                dismissFavoriteFolderDialog()
                toast(if (selectedFolderIds.isEmpty()) "已取消收藏" else "收藏设置已保存")
            }.onFailure { e ->
                toast("收藏失败: ${e.message}")
            }
            _isSavingFavoriteFolders.value = false
        }
    }

    private fun applyFavoriteSaveUiState(
        originalFolderIds: Set<Long>,
        selectedFolderIds: Set<Long>
    ) {
        _uiState.update { state ->
            if (state is VideoPlaybackUiState.Success) {
                val resolvedState = resolveFavoriteSaveUiState(
                    originalFolderIds = originalFolderIds,
                    selectedFolderIds = selectedFolderIds,
                    currentFavoriteCount = state.info.stat.favorite
                )
                state.copy(
                    isFavorited = resolvedState.isFavorited,
                    info = state.info.copy(
                        stat = state.info.stat.copy(favorite = resolvedState.favoriteCount)
                    )
                )
            } else {
                state
            }
        }
    }

    private fun updateFavoriteUiState(targetAid: Long?, selectedFolderIds: Set<Long>) {
        val currentAid = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.aid
        if (!shouldSyncFavoriteFolderUiState(targetAid = targetAid, currentAid = currentAid)) {
            return
        }
        _uiState.update { state ->
            if (state is VideoPlaybackUiState.Success) {
                state.copy(isFavorited = selectedFolderIds.isNotEmpty())
            } else {
                state
            }
        }
    }

    fun createFavoriteFolder(title: String, intro: String = "", isPrivate: Boolean = false) {
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.ActionRepository.createFavFolder(title, intro, isPrivate)
            result.onSuccess {
                toast("创建收藏夹成功")
                loadFavoriteFolders(aid = favoriteFoldersBoundAid, keepCurrentSelection = true)
            }.onFailure { e ->
                toast("创建失败: ${e.message}")
            }
        }
    }

    fun showFollowGroupDialogForUser(mid: Long) {
        if (mid <= 0L) return
        followGroupTargetMid = mid
        _followGroupDialogVisible.value = true
        loadFollowGroupsForTarget()
    }

    fun dismissFollowGroupDialog() {
        _followGroupDialogVisible.value = false
    }

    fun toggleFollowGroupSelection(tagId: Long) {
        if (tagId == 0L) return
        _followGroupSelectedTagIds.update { selected ->
            if (selected.contains(tagId)) selected - tagId else selected + tagId
        }
    }

    fun saveFollowGroupSelection() {
        if (_isSavingFollowGroups.value || followGroupTargetMid <= 0L) return
        val selected = _followGroupSelectedTagIds.value
        viewModelScope.launch {
            _isSavingFollowGroups.value = true
            com.android.purebilibili.data.repository.ActionRepository
                .overwriteFollowGroupIds(
                    targetMids = setOf(followGroupTargetMid),
                    selectedTagIds = selected
                )
                .onSuccess {
                    dismissFollowGroupDialog()
                    toast("分组设置已保存")
                }
                .onFailure { e ->
                    toast("分组设置失败: ${e.message}")
                }
            _isSavingFollowGroups.value = false
        }
    }

    private fun loadFollowGroupsForTarget() {
        val targetMid = followGroupTargetMid
        if (targetMid <= 0L) return
        viewModelScope.launch {
            _isFollowGroupsLoading.value = true
            val tagsResult = com.android.purebilibili.data.repository.ActionRepository.getFollowGroupTags()
            val userGroupResult = com.android.purebilibili.data.repository.ActionRepository.getUserFollowGroupIds(targetMid)

            tagsResult.onSuccess { tags ->
                _followGroupTags.value = tags.filter { it.tagid != 0L }
            }.onFailure { e ->
                _followGroupTags.value = emptyList()
                toast("加载分组失败: ${e.message}")
            }

            userGroupResult.onSuccess { groupIds ->
                _followGroupSelectedTagIds.value = groupIds.filterNot { it == 0L }.toSet()
            }.onFailure {
                _followGroupSelectedTagIds.value = emptySet()
            }

            _isFollowGroupsLoading.value = false
        }
    }
    
    // ========== Public API ==========
    
    /**
     * 初始化持久化存储（需要在使用前调用一次）
     */
    fun initWithContext(context: android.content.Context) {
        val applicationContext = context.applicationContext
        if (appContext === applicationContext) return

        appContext = applicationContext  //  [新增] 保存应用 Context
        playbackUseCase.initWithContext(context)

        val miniPlayerManager = MiniPlayerManager.getInstance(applicationContext)
        miniPlayerManager.onNavigateNextCallback = {
            playNextPageOrRecommended(ignoreSavedProgress = false)
        }
        miniPlayerManager.onNavigatePreviousCallback = {
            playPreviousPageOrRecommended(ignoreSavedProgress = false)
        }
        miniPlayerManager.onHasNextNavigationCallback = {
            hasNextPageOrRecommended()
        }
        miniPlayerManager.onHasPreviousNavigationCallback = {
            hasPreviousPageOrRecommended()
        }
        
        // 🎧 Start observing settings preferences
        viewModelScope.launch {
            // Observe Video Codec
            com.android.purebilibili.core.store.SettingsManager.getVideoCodec(context)
                .collect { _videoCodecPreference.value = it }
        }

        viewModelScope.launch {
            com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodec(context)
                .collect { _videoSecondCodecPreference.value = it }
        }
        
        viewModelScope.launch {
            com.android.purebilibili.core.store.SettingsManager.getAudioQuality(context)
                .collect { 
                    com.android.purebilibili.core.util.Logger.d("VideoPlaybackViewModel", "🎵 Audio preference updated from Settings to: $it")
                    _audioQualityPreference.value = it 
                }
        }
    }

    /**
     * Stop audio immediately when a different video is requested.
     * Keep the last frame if the surface still has content; media is replaced when load succeeds.
     */
    private fun haltPlaybackForPendingMediaSwitch() {
        val player = exoPlayer ?: return
        player.playWhenReady = false
        if (player.isPlaying) {
            player.pause()
        }
        Logger.d("PlayerVM", "Halt playback for pending media switch: current=$currentBvid/$currentCid")
    }

    private fun bootstrapContextIfNeeded() {
        val globalContext = com.android.purebilibili.core.network.NetworkModule.appContext
        if (shouldBootstrapPlayerContext(
                hasBoundContext = appContext != null,
                hasGlobalContext = globalContext != null
            )
        ) {
            initWithContext(requireNotNull(globalContext))
            Logger.d("PlayerVM", "♻️ Bootstrapped VideoPlaybackViewModel context from NetworkModule")
        }
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    fun attachPlayer(player: ExoPlayer) {
        val changed = exoPlayer !== player
        if (changed) playbackRecoveryGeneration++
        val previousPlayer = exoPlayer

        if (changed && previousPlayer != null) {
            cancelPlaybackStallRecovery()
            playbackStallRecoveryFirstFrameRendered = false
            flushPlaybackHeartbeatSnapshot(reason = "replace_player")
            saveCurrentPosition()
            // 切换播放器时立即停止旧实例，避免转场期间双播
            previousPlayer.removeListener(playbackEndListener)
            previousPlayer.removeAnalyticsListener(videoBandwidthListener)
            previousPlayer.playWhenReady = false
            previousPlayer.pause()
        }

        exoPlayer = player
        playbackUseCase.attachPlayer(player)
        com.android.purebilibili.core.player.PlayerVolumeController.applyPreferredVolume(player)
        
        // 防止重复添加同一个 listener（同一 player 多次 attach 的场景）
        player.removeListener(playbackEndListener)
        player.addListener(playbackEndListener)
        player.removeAnalyticsListener(videoBandwidthListener)
        player.addAnalyticsListener(videoBandwidthListener)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private val videoBandwidthListener = object : AnalyticsListener {
        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            videoNetworkPressureObserver.recordBandwidthSample(totalLoadTimeMs, totalBytesLoaded, bitrateEstimate)
        }
    }
    
    //  [新增] 播放完成监听器
    private val playbackEndListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            pollQualitySwitchTelemetry()
            if (CdnTransferRuntime.enabled) {
                CdnTransferRuntime.playback(
                    exoPlayer?.let { (it.bufferedPosition - it.currentPosition).coerceAtLeast(0) } ?: 0L,
                    exoPlayer?.videoFormat?.bitrate?.coerceAtLeast(0)?.toLong() ?: 0L
                )
            }
            if (playbackState == Player.STATE_READY) {
                cancelPlaybackStallRecovery()
                markPlaybackCdnReadyIfMediaReady()
                recordCurrentCdnHealthEvent(CdnHealthEvent.PLAYBACK_READY)
                scheduleCdnDashPrefetch()
            } else if (playbackState == Player.STATE_BUFFERING) {
                playbackCdnPrefetchJob?.cancel()
                playbackCdnPrefetchJob = null
                recordCurrentCdnHealthEvent(CdnHealthEvent.BUFFERING)
                schedulePlaybackStallRecovery()
            } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                playbackCdnPrefetchJob?.cancel()
                playbackCdnPrefetchJob = null
                cancelPlaybackStallRecovery()
            }
            if (playbackState == Player.STATE_ENDED) {
                samplePlaybackAnalytics(ended = true)
                if (shouldSuppressPlaybackCompletionForCommentInteraction(isCommentInteractionActive)) {
                    _showPlaybackEndedDialog.value = false
                    Logger.d("PlayerVM", "评论交互进行中，保持当前视频结束态")
                    return
                }

                // �️ [修复] 仅当用户主动开始播放后才触发自动连播
                // 防止从历史记录加载已看完视频时立即跳转
                if (!hasUserStartedPlayback) {
                    Logger.d("PlayerVM", "🛡️ STATE_ENDED but user hasn't started playback, skip auto-play")
                    return
                }
                
                // �🔧 [修复] 检查自动播放设置 - 使用 SettingsManager 同步读取
                val context = appContext ?: return
                val autoPlayEnabled = com.android.purebilibili.core.store.SettingsManager
                    .getAutoPlaySync(context)
                val externalPlaylistAutoContinueEnabled =
                    com.android.purebilibili.core.store.SettingsManager
                        .getExternalPlaylistAutoContinueSync(context)

                if (isPortraitPlaybackSessionActive) {
                    Logger.d("PlayerVM", "📱 STATE_ENDED in portrait session, handled by portrait pager")
                    return
                }

                if (_isInAudioMode.value) {
                    val didContinue = handleAudioModePlaybackEnded(ignoreSavedProgress = true)
                    if (!didContinue) {
                        _showPlaybackEndedDialog.value = false
                    }
                    return
                }

                val behavior = com.android.purebilibili.core.store.SettingsManager
                    .getPlaybackCompletionBehaviorSync(context)
                val autoPlayNext = SettingsManager.getClickToPlaySync(context)
                // 与 UI/Flow 同源策略对齐后，再写回 ExoPlayer，避免残留 REPEAT_MODE_ONE。
                exoPlayer?.repeatMode = resolvePlaybackCompletionRepeatMode(behavior)
                val (hasNextPage, hasNextSeasonEpisode, _) = resolveCurrentNextAvailability()
                val action = playbackCoordinator.resolvePlaybackEnded(
                    behavior = behavior,
                    autoPlayEnabled = autoPlayEnabled,
                    isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
                    externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled,
                    externalPlaylistSource = PlaylistManager.externalPlaylistSource.value,
                    playMode = PlaylistManager.playMode.value,
                    hasNextPageOrSeasonTarget = hasNextPage || hasNextSeasonEpisode
                )
                val outcome = playbackCoordinator.executePlaybackEndAction(
                    action = action,
                    repeatCurrent = {
                        exoPlayer?.seekTo(0)
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                    },
                    playNextInOrder = { ignoreSavedProgress ->
                        playNextInOrder(ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlayNext)
                    },
                    playNextFromPlaylistLoop = { ignoreSavedProgress ->
                        playNextFromPlaylist(
                            loopAtEnd = true,
                            autoPlay = autoPlayNext,
                            ignoreSavedProgress = ignoreSavedProgress
                        )
                    },
                    autoContinue = { ignoreSavedProgress ->
                        playNextPageOrRecommended(ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlayNext)
                    }
                )
                if (outcome.shouldHidePlaybackEndedDialog) {
                    // 自动播放关闭或后续无法连播：保持结束态，不弹窗打断
                    _showPlaybackEndedDialog.value = false
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            samplePlaybackAnalytics()
            syncHeartbeatPlaybackTracking(
                isActivelyPlaying = isPlaying && !BackgroundManager.isInBackground
            )
            if (isPlaying) {
                // 🛡️ [修复] 用户开始播放时设置标志
                hasUserStartedPlayback = true
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                cancelPlaybackStallRecovery()
            }
        }

        override fun onRenderedFirstFrame() {
            playbackStallRecoveryFirstFrameRendered = true
            if (_uiState.value is VideoPlaybackUiState.Success) {
                if (relatedPlaybackPreparedToken == currentLoadRequestToken) {
                    relatedFirstFrameToken = currentLoadRequestToken
                }
                logVideoLoadStage(VideoLoadStage.FIRST_FRAME, currentLoadRequestToken)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
                exoPlayer?.repeatMode == Player.REPEAT_MODE_ONE
            ) {
                samplePlaybackAnalytics(ended = true, positionMs = oldPosition.positionMs)
            }
            // Rebase on every position jump, including source replacement and plugin skips.
            samplePlaybackAnalytics(discontinuity = true, positionMs = newPosition.positionMs)
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int
        ) {
            cancelPlaybackStallRecovery()
            playbackStallRecoveryFirstFrameRendered = false
        }

        override fun onTracksChanged(tracks: Tracks) {
            markPlaybackCdnReadyIfMediaReady()
        }

        // ExoPlaybackException 的 rendererName/rendererFormat 属 media3 unstable API：错误回退处理在应用层封装，opt-in 标记会级联污染整个 Listener。
        @SuppressLint("UnsafeOptInUsageError")
        override fun onPlayerError(error: PlaybackException) {
            cancelPlaybackStallRecovery()
            Logger.w("PlayerVM", "Playback error: ${error.errorCodeName}, message=${error.message}")
            val current = _uiState.value as? VideoPlaybackUiState.Success
            val exoPlaybackError = error as? ExoPlaybackException
            val errorCategory = com.android.purebilibili.core.util.playbackErrorCategory(error.errorCode)
            AnalyticsHelper.logVideoError(errorCategory, "playback", error.errorCode)
            finishQualitySwitchTelemetry("failed", errorCategory)
            if (
                current != null &&
                isPremiumAudioPlaybackFailure(
                    errorCode = error.errorCode,
                    selectedAudioQuality = current.selectedAudioQuality,
                    rendererName = exoPlaybackError?.rendererName,
                    rendererSampleMimeType = exoPlaybackError?.rendererFormat?.sampleMimeType
                )
            ) {
                Logger.w(
                    "PlayerVM",
                    "Hi-Res audio decoder failed; switching current playback to AAC"
                )
                fallbackFromPremiumAudioPlaybackError()
                return
            }
            recordCurrentCdnHealthEvent(CdnHealthEvent.PLAYER_ERROR)
            fallbackFromCdnRewrite(reason = "player_error")
        }
    }

    private fun schedulePlaybackStallRecovery() {
        val scheduledPlayer = exoPlayer ?: return
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val mediaKey = "${current.info.bvid}:${current.info.cid}"
        if (playbackStallRecoveryMediaKey != mediaKey) {
            playbackStallRecoveryMediaKey = mediaKey
            attemptedPlaybackStallRecoveryCdnIndexes.clear()
        }
        if (playbackStallRecoveryJob?.isActive == true) return
        val ticket = playbackRecoveryGeneration

        playbackStallRecoveryJob = viewModelScope.launch {
            delay(PLAYBACK_STALL_RECOVERY_TIMEOUT_MS)
            val latestPlayer = exoPlayer ?: return@launch
            val latest = _uiState.value as? VideoPlaybackUiState.Success ?: return@launch
            if (ticket != playbackRecoveryGeneration || latestPlayer !== scheduledPlayer ||
                "${latest.info.bvid}:${latest.info.cid}" != mediaKey
            ) {
                return@launch
            }

            attemptedPlaybackStallRecoveryCdnIndexes += latest.currentCdnIndex
            val decision = resolvePlaybackStallRecoveryDecision(
                playbackState = latestPlayer.playbackState,
                playWhenReady = latestPlayer.playWhenReady,
                firstFrameRendered = playbackStallRecoveryFirstFrameRendered,
                forwardBufferDurationMs = (latestPlayer.bufferedPosition - latestPlayer.currentPosition)
                    .coerceAtLeast(0L),
                currentCdnIndex = latest.currentCdnIndex,
                cdnCandidateCount = latest.allVideoUrls.size,
                attemptedCdnIndexes = attemptedPlaybackStallRecoveryCdnIndexes,
                usesAdaptivePlayback = latest.playbackQualityMode == PlaybackQualityMode.AUTO &&
                    latest.adaptiveDashSource != null
            )
            val nextCdnIndex = decision.nextCdnIndex ?: return@launch
            attemptedPlaybackStallRecoveryCdnIndexes += nextCdnIndex
            playbackStallRecoveryJob = null
            Logger.w(
                "PlayerVM",
                "Playback stall recovery: media=$playbackStallRecoveryMediaKey, " +
                    "cdn=${latest.currentCdnIndex + 1}->${nextCdnIndex + 1}, " +
                    "position=${latestPlayer.currentPosition}"
            )
            switchCdnTo(nextCdnIndex)
        }
    }

    private fun cancelPlaybackStallRecovery() {
        playbackStallRecoveryJob?.cancel()
        playbackStallRecoveryJob = null
    }
    
    /**
     * 获取下一个视频的 BVID (用于导航)
     * Side effect: Updates PlaylistManager index
     */
    fun getNextVideoId(): String? {
        val nextItem = PlaylistManager.playNext()
        return nextItem?.bvid
    }

    /**
     * 获取上一个视频的 BVID (用于导航)
     * Side effect: Updates PlaylistManager index
     */
    fun getPreviousVideoId(): String? {
        val prevItem = PlaylistManager.playPrevious()
        return prevItem?.bvid
    }

    /**
     *  [新增] 自动播放推荐视频（使用 PlaylistManager）
     */
    /** Read-only lookahead. Explicit order/loop behavior matches the existing end coordinator. */
    internal fun resolveNextWatchSuggestion(): com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion? {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return null
        val context = appContext ?: return null
        if (isPortraitPlaybackSessionActive || _isInAudioMode.value || _sleepTimerMinutes.value != null) return null
        val behavior = SettingsManager.getPlaybackCompletionBehaviorSync(context)
        if (behavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.REPEAT_ONE) return null
        val info = current.info
        val external = PlaylistManager.isExternalPlaylist.value
        fun suggestion(bvid: String, cid: Long, title: String, cover: String, label: String,
                       page: Int? = null, queue: Int? = null) =
            com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion(
                info.bvid, info.cid, bvid, cid, title, cover, label, page, queue)
        // Explicit playlist loop ignores the collection order, as executePlaybackEndAction does.
        if ((!external || behavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.PLAY_IN_ORDER) &&
            behavior != com.android.purebilibili.core.store.PlaybackCompletionBehavior.LOOP_PLAYLIST) {
            com.android.purebilibili.feature.video.playback.next.resolveCollectionNextWatch(info)?.let { return it }
        }
        if (external || behavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.PLAY_IN_ORDER ||
            behavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.LOOP_PLAYLIST) {
            val items = PlaylistManager.playlist.value
            val index = resolveCurrentPlaylistIndex(items)
            // Do not guess a future item before the queue has adopted this playback identity.
            if (items.getOrNull(index)?.bvid != info.bvid) return null
            val nextIndex = if (index < items.lastIndex) index + 1 else if (
                behavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.LOOP_PLAYLIST) 0 else -1
            val next = items.getOrNull(nextIndex) ?: return null
            if (next.isBangumi || next.bvid.isBlank() || (next.bvid == info.bvid && next.cid == info.cid)) return null
            return suggestion(next.bvid, next.cid, next.title, next.cover, "接下来观看", queue = nextIndex)
        }
        // Ordinary single videos do not auto-play recommendations; this is an explicit click offer.
        val feedback = com.android.purebilibili.core.store.TodayWatchFeedbackStore.getSnapshot(context)
        val next = current.related.firstOrNull {
            it.bvid.isNotBlank() && it.bvid != info.bvid && it.bvid !in feedback.dislikedBvids &&
                it.owner.mid !in feedback.dislikedCreatorMids &&
                feedback.dislikedKeywords.none { keyword -> keyword.isNotBlank() && it.title.contains(keyword, ignoreCase = true) }
        } ?: return null
        return suggestion(next.bvid, 0L, next.title, next.pic, "推荐观看")
    }

    internal fun playNextWatchSuggestion(target: com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion): Boolean {
        if (resolveNextWatchSuggestion() != target) return false
        if (target.pageIndex != null) {
            switchPage(target.pageIndex, ignoreSavedProgress = true)
        } else {
            target.playlistIndex?.let { if (PlaylistManager.playAt(it) == null) return false }
            markInPageInitiatedPlayback(target.bvid, target.cid)
            loadVideo(target.bvid, cid = target.cid, autoPlay = true, ignoreSavedProgress = true)
        }
        return true
    }

    fun playNextRecommended(ignoreSavedProgress: Boolean = false, autoPlay: Boolean = true): Boolean {
        // 使用 PlaylistManager 获取下一曲
        val nextItem = PlaylistManager.playNext()
        
        if (nextItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${nextItem.title}")
            }
            // Automatic continuation passes the saved startup preference; user actions can play immediately.
            markInPageInitiatedPlayback(nextItem.bvid, nextItem.cid)
            loadVideo(
                nextItem.bvid,
                cid = nextItem.cid,
                autoPlay = autoPlay,
                ignoreSavedProgress = ignoreSavedProgress
            )
            return true
        } else {
            // 根据播放模式显示不同提示
            val mode = PlaylistManager.playMode.value
            when (mode) {
                PlayMode.SEQUENTIAL -> toast(" 播放列表结束")
                PlayMode.REPEAT_ONE -> {
                    // 单曲循环：重新播放当前视频
                    exoPlayer?.seekTo(0)
                    exoPlayer?.play()
                }
                else -> toast("没有更多视频")
            }
            return false
        }
    }

    private fun resolveCurrentPlaylistIndex(items: List<PlaylistItem>): Int {
        val currentInfo = (_uiState.value as? VideoPlaybackUiState.Success)?.info
        return PlaylistManager.currentIndex.value
            .takeIf { it in items.indices }
            ?: currentInfo?.bvid?.let { bvid ->
                items.indexOfFirst { it.bvid == bvid }.takeIf { it >= 0 }
            }
            ?: 0
    }

    private fun hasNextInPlaylist(loopAtEnd: Boolean): Boolean {
        val items = PlaylistManager.playlist.value
        if (items.isEmpty()) return false

        val currentIndex = resolveCurrentPlaylistIndex(items)
        return currentIndex < items.lastIndex || loopAtEnd
    }

    private fun playNextFromPlaylist(
        loopAtEnd: Boolean,
        ignoreSavedProgress: Boolean = false,
        autoPlay: Boolean = true,
    ): Boolean {
        val items = PlaylistManager.playlist.value
        if (items.isEmpty()) return false

        val currentIndex = resolveCurrentPlaylistIndex(items)

        val nextIndex = when {
            currentIndex < items.lastIndex -> currentIndex + 1
            loopAtEnd -> 0
            else -> return false
        }

        val target = PlaylistManager.playAt(nextIndex) ?: return false
        markInPageInitiatedPlayback(target.bvid, target.cid)
        loadVideo(
            target.bvid,
            cid = target.cid,
            autoPlay = autoPlay,
            ignoreSavedProgress = ignoreSavedProgress
        )
        return true
    }

    private fun hasPreviousInPlaylist(): Boolean {
        val items = PlaylistManager.playlist.value
        if (items.isEmpty()) return false

        val currentIndex = resolveCurrentPlaylistIndex(items)
        return currentIndex > 0
    }

    private fun playPreviousFromPlaylist(ignoreSavedProgress: Boolean = false): Boolean {
        val items = PlaylistManager.playlist.value
        if (items.isEmpty()) return false

        val currentIndex = resolveCurrentPlaylistIndex(items)
        val previousIndex = currentIndex - 1
        if (previousIndex !in items.indices) return false

        val target = PlaylistManager.playAt(previousIndex) ?: return false
        markInPageInitiatedPlayback(target.bvid, target.cid)
        loadVideo(
            target.bvid,
            cid = target.cid,
            autoPlay = true,
            ignoreSavedProgress = ignoreSavedProgress
        )
        return true
    }

    private fun resolveCurrentNextAvailability(): Triple<Boolean, Boolean, Boolean> {
        val current = _uiState.value as? VideoPlaybackUiState.Success
        val target = current?.info?.let { com.android.purebilibili.feature.video.playback.next.resolveCollectionNextWatch(it) }
        val hasNextPage = target?.pageIndex != null
        val hasNextSeasonEpisode = target != null && target.pageIndex == null

        return Triple(hasNextPage, hasNextSeasonEpisode, hasNextInPlaylist(loopAtEnd = false))
    }

    private fun resolveCurrentPreviousAvailability(): Triple<Boolean, Boolean, Boolean> {
        val current = _uiState.value as? VideoPlaybackUiState.Success
        val hasPreviousPage = current?.let { success ->
            val pages = success.info.pages
            if (pages.size <= 1) {
                false
            } else {
                val currentPageIndex = pages.indexOfFirst { it.cid == currentCid }
                currentPageIndex > 0
            }
        } ?: false

        val hasPreviousSeasonEpisode = current?.info?.ugc_season?.let { season ->
            val allEpisodes = season.sections.flatMap { it.episodes }
            val previousEpIndex = resolveUgcSeasonEpisodeIndex(
                episodes = allEpisodes,
                currentBvid = current.info.bvid,
                currentCid = current.info.cid
            ) - 1
            previousEpIndex >= 0
        } ?: false

        return Triple(hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousInPlaylist())
    }

    private fun playNextInOrder(ignoreSavedProgress: Boolean = false, autoPlay: Boolean = true): Boolean {
        val (hasNextPage, hasNextSeasonEpisode, hasNextPlaylistItem) = resolveCurrentNextAvailability()
        return when (
            resolvePlayInOrderNextSource(
                hasNextPage = hasNextPage,
                hasNextSeasonEpisode = hasNextSeasonEpisode,
                hasNextPlaylistItem = hasNextPlaylistItem
            )
        ) {
            PlayInOrderNextSource.PAGE_OR_SEASON ->
                playNextPageOrSeason(ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlay) ||
                    playNextFromPlaylist(
                        loopAtEnd = false,
                        autoPlay = autoPlay,
                        ignoreSavedProgress = ignoreSavedProgress
                    )
            PlayInOrderNextSource.PLAYLIST ->
                playNextFromPlaylist(
                    loopAtEnd = false,
                    autoPlay = autoPlay,
                    ignoreSavedProgress = ignoreSavedProgress
                )
            PlayInOrderNextSource.NONE -> false
        }
    }

    private fun playNextPageOrSeason(ignoreSavedProgress: Boolean = false, autoPlay: Boolean = true): Boolean {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return false
        val target = com.android.purebilibili.feature.video.playback.next.resolveCollectionNextWatch(current.info)
            ?: return false
        if (target.pageIndex != null) {
            switchPage(target.pageIndex, ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlay)
        } else {
            markInPageInitiatedPlayback(target.bvid, target.cid)
            loadVideo(target.bvid, cid = target.cid, autoPlay = autoPlay, ignoreSavedProgress = ignoreSavedProgress)
        }
        return true
    }

    private fun playPreviousInOrder(ignoreSavedProgress: Boolean = false): Boolean {
        val (hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousPlaylistItem) =
            resolveCurrentPreviousAvailability()
        return when (
            resolvePlayInOrderPreviousSource(
                hasPreviousPage = hasPreviousPage,
                hasPreviousSeasonEpisode = hasPreviousSeasonEpisode,
                hasPreviousPlaylistItem = hasPreviousPlaylistItem
            )
        ) {
            PlayInOrderNextSource.PAGE_OR_SEASON ->
                playPreviousPageOrSeason(ignoreSavedProgress = ignoreSavedProgress) ||
                    playPreviousFromPlaylist(ignoreSavedProgress = ignoreSavedProgress)
            PlayInOrderNextSource.PLAYLIST ->
                playPreviousFromPlaylist(ignoreSavedProgress = ignoreSavedProgress)
            PlayInOrderNextSource.NONE -> false
        }
    }

    private fun playPreviousPageOrSeason(ignoreSavedProgress: Boolean = false): Boolean {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return false

        val pages = current.info.pages
        if (pages.size > 1) {
            val currentPageIndex = pages.indexOfFirst { it.cid == currentCid }
            val previousPageIndex = currentPageIndex - 1

            if (previousPageIndex >= 0) {
                val previousPage = pages[previousPageIndex]
                Logger.d("PlayerVM", "🎵 播放上一个分P: P${previousPageIndex + 1} - ${previousPage.part}")
                switchPage(previousPageIndex, ignoreSavedProgress = ignoreSavedProgress)
                return true
            }
        }

        current.info.ugc_season?.let { season ->
            val allEpisodes = season.sections.flatMap { it.episodes }
            val currentEpIndex = resolveUgcSeasonEpisodeIndex(
                episodes = allEpisodes,
                currentBvid = current.info.bvid,
                currentCid = current.info.cid
            )
            val previousEpIndex = currentEpIndex - 1

            if (previousEpIndex >= 0) {
                val previousEpisode = allEpisodes[previousEpIndex]
                Logger.d("PlayerVM", "📂 播放合集上一集: ${previousEpisode.title}")
                viewModelScope.launch {
                    toast("播放合集上一集: ${previousEpisode.title}")
                }
                markInPageInitiatedPlayback(previousEpisode.bvid, previousEpisode.cid)
                loadVideo(
                    previousEpisode.bvid,
                    autoPlay = true,
                    ignoreSavedProgress = ignoreSavedProgress,
                    cid = previousEpisode.cid
                )
                return true
            }
        }

        return false
    }

    private inline fun executePlaybackNavigationTargets(
        targets: List<PlaybackNavigationTarget>,
        playPageOrSeason: () -> Boolean,
        playPlaylist: () -> Boolean,
        playDirectQueue: () -> Boolean
    ): Boolean {
        targets.forEach { target ->
            val handled = when (target) {
                PlaybackNavigationTarget.PAGE_OR_SEASON -> playPageOrSeason()
                PlaybackNavigationTarget.PLAYLIST -> playPlaylist()
                PlaybackNavigationTarget.DIRECT_QUEUE -> playDirectQueue()
            }
            if (handled) {
                return true
            }
        }
        return false
    }

    private fun hasNextPageOrRecommended(): Boolean {
        val (hasNextPage, hasNextSeasonEpisode, hasNextPlaylistItem) = resolveCurrentNextAvailability()
        return hasNextPage || hasNextSeasonEpisode || hasNextPlaylistItem
    }

    private fun hasPreviousPageOrRecommended(): Boolean {
        val (hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousPlaylistItem) =
            resolveCurrentPreviousAvailability()
        return hasPreviousPage || hasPreviousSeasonEpisode || hasPreviousPlaylistItem
    }

    /**
     * 🎵 [新增] 优先播放下一个分P，如果没有分P则检查合集，最后播放推荐视频
     * 用于分集视频（如音乐合集）的连续播放
     * 优先级: 分P > 合集下一集 > 推荐视频
     */
    fun playNextPageOrRecommended(ignoreSavedProgress: Boolean = false, autoPlay: Boolean = true): Boolean {
        val nextStrategy = resolveAudioNextPlaybackStrategy(
            isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
            externalPlaylistSource = PlaylistManager.externalPlaylistSource.value
        )
        if (nextStrategy == AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST) {
            Logger.d(
                "PlayerVM",
                "🔒 外部播放队列模式：下一首按队列播放 source=${PlaylistManager.externalPlaylistSource.value}"
            )
        }
        val (hasNextPage, hasNextSeasonEpisode, hasNextPlaylistItem) = resolveCurrentNextAvailability()
        return executePlaybackNavigationTargets(
            targets = resolvePlaybackNavigationTargets(
                strategy = nextStrategy,
                hasPageOrSeasonTarget = hasNextPage || hasNextSeasonEpisode,
                hasPlaylistTarget = hasNextPlaylistItem
            ),
            playPageOrSeason = {
                playNextPageOrSeason(ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlay)
            },
            playPlaylist = {
                playNextFromPlaylist(
                    loopAtEnd = false,
                    autoPlay = autoPlay,
                    ignoreSavedProgress = ignoreSavedProgress
                )
            },
            playDirectQueue = {
                Logger.d("PlayerVM", "🎵 播放推荐视频")
                playNextRecommended(ignoreSavedProgress = ignoreSavedProgress, autoPlay = autoPlay)
            }
        )
    }
    
    /**
     *  [新增] 播放上一个视频，优先分P/合集，最后回退到推荐队列
     */
    fun playPreviousPageOrRecommended(ignoreSavedProgress: Boolean = false): Boolean {
        val previousStrategy = resolveAudioNextPlaybackStrategy(
            isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
            externalPlaylistSource = PlaylistManager.externalPlaylistSource.value
        )
        val (hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousPlaylistItem) =
            resolveCurrentPreviousAvailability()
        return executePlaybackNavigationTargets(
            targets = resolvePlaybackNavigationTargets(
                strategy = previousStrategy,
                hasPageOrSeasonTarget = hasPreviousPage || hasPreviousSeasonEpisode,
                hasPlaylistTarget = hasPreviousPlaylistItem
            ),
            playPageOrSeason = {
                playPreviousPageOrSeason(ignoreSavedProgress = ignoreSavedProgress)
            },
            playPlaylist = {
                playPreviousFromPlaylist(ignoreSavedProgress = ignoreSavedProgress)
            },
            playDirectQueue = {
                playPreviousFromRecommendedQueue(ignoreSavedProgress = ignoreSavedProgress)
            }
        )
    }

    fun playPreviousRecommended(ignoreSavedProgress: Boolean = false): Boolean {
        return playPreviousPageOrRecommended(ignoreSavedProgress = ignoreSavedProgress)
    }

    fun playNextAudioModeTrack(ignoreSavedProgress: Boolean = false): Boolean {
        return playAudioModePlaylistItem(
            item = PlaylistManager.playNext(),
            emptyMessage = "播放列表结束",
            ignoreSavedProgress = ignoreSavedProgress
        )
    }

    fun playPreviousAudioModeTrack(ignoreSavedProgress: Boolean = false): Boolean {
        return playAudioModePlaylistItem(
            item = PlaylistManager.playPrevious(),
            emptyMessage = "没有上一个视频",
            ignoreSavedProgress = ignoreSavedProgress
        )
    }

    private fun handleAudioModePlaybackEnded(ignoreSavedProgress: Boolean): Boolean {
        if (PlaylistManager.playMode.value == PlayMode.REPEAT_ONE) {
            exoPlayer?.seekTo(0)
            exoPlayer?.playWhenReady = true
            exoPlayer?.play()
            return exoPlayer != null
        }
        return playNextAudioModeTrack(ignoreSavedProgress = ignoreSavedProgress)
    }

    private fun playAudioModePlaylistItem(
        item: PlaylistItem?,
        emptyMessage: String,
        ignoreSavedProgress: Boolean
    ): Boolean {
        if (item == null) {
            toast(emptyMessage)
            return false
        }
        viewModelScope.launch {
            toast("正在播放: ${item.title}")
        }
        markInPageInitiatedPlayback(item.bvid, item.cid)
        loadVideo(
            bvid = item.bvid,
            cid = item.cid,
            autoPlay = true,
            ignoreSavedProgress = ignoreSavedProgress
        )
        return true
    }

    private fun playPreviousFromRecommendedQueue(ignoreSavedProgress: Boolean = false): Boolean {
        val prevItem = PlaylistManager.playPrevious()

        if (prevItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${prevItem.title}")
            }
            markInPageInitiatedPlayback(prevItem.bvid, prevItem.cid)
            loadVideo(
                prevItem.bvid,
                cid = prevItem.cid,
                autoPlay = true,
                ignoreSavedProgress = ignoreSavedProgress
            )
            return true
        }

        toast("没有上一个视频")
        return false
    }
    
    fun reloadVideo() {
        val bvid = currentBvid.takeIf { it.isNotBlank() } ?: return
        val currentPos = exoPlayer?.currentPosition ?: 0L

        // 💾 [修复] 在清除状态前明确保存进度，防止 loadVideo 读取到 0
        if (currentPos > 0) {
            playbackUseCase.savePosition(bvid, currentCid)
            Logger.d("PlayerVM", "💾 reloadVideo: Saved position $currentPos ms")
        }

        Logger.d("PlayerVM", "🔄 Reloading video (forced)...")
        // 设置标志位，确保 loadVideo 不会跳过
        loadVideo(
            bvid, force = true, autoPlay = true, cid = currentCid,
            fallbackResumePositionMs = currentPos.coerceAtLeast(0L)
        )
    }

    fun retryAiSummary() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.aiSummary != null) return
        loadAiSummary(
            bvid = current.info.bvid,
            cid = current.info.cid,
            upMid = current.info.owner.mid
        )
    }
    
    // [修复] 添加 aid 参数支持，用于移动端推荐流（可能只返回 aid）
    // [Added] autoPlay override: null = use settings, true/false = force
    fun loadVideo(
        bvid: String,
        aid: Long = 0,
        force: Boolean = false,
        autoPlay: Boolean? = null,
        ignoreSavedProgress: Boolean = false,
        audioLang: String? = null,
        videoCodecOverride: String? = null,
        cid: Long = 0L,
        fallbackResumePositionMs: Long = 0L
    ) {
        if (bvid.isBlank()) return
        playbackRecoveryGeneration++
        // A full media load supersedes an in-place page switch. Without this, a slow page request
        // can replace the player after navigation has already started loading another subject.
        pageSwitchJob?.cancel()
        pageSwitchJob = null
        pendingPageSwitchCid = null
        pageSwitchGeneration += 1L
        if (bvid != currentBvid || (cid > 0L && cid != currentCid)) {
            cancelPlaybackStallRecovery()
            playbackStallRecoveryFirstFrameRendered = false
            playbackStallRecoveryMediaKey = "$bvid:$cid"
            attemptedPlaybackStallRecoveryCdnIndexes.clear()
        }
        val playbackRequest = PlaybackRequest.create(
            bvid = bvid,
            aid = aid,
            cid = cid,
            force = force,
            autoPlay = autoPlay,
            ignoreSavedProgress = ignoreSavedProgress,
            audioLang = audioLang,
            videoCodecOverride = videoCodecOverride
        )
        playbackCoordinator.dismissResumeSuggestion()
        bootstrapContextIfNeeded()
        aiSummaryJob?.cancel()
        videoNoteJob?.cancel()
        Logger.d(
            "PlayerVM",
            "SUB_DBG loadVideo start: request=${playbackRequest.bvid}/${playbackRequest.cid}, aid=${playbackRequest.aid}, force=${playbackRequest.force}, current=$currentBvid/$currentCid, ui=${(_uiState.value as? VideoPlaybackUiState.Success)?.info?.bvid}/${(_uiState.value as? VideoPlaybackUiState.Success)?.info?.cid}"
        )
        
        //  防止重复加载：只有在正在加载同一视频时才跳过 (且语言未改变)
        if (!playbackRequest.force &&
            currentBvid == playbackRequest.bvid &&
            playbackSessionState.currentRequest?.cid == playbackRequest.cid &&
            playbackSessionState.currentRequest?.audioLang == playbackRequest.audioLang &&
            playbackSessionState.currentRequest?.videoCodecOverride == playbackRequest.videoCodecOverride &&
            playbackSessionState.currentRequest?.ignoreSavedProgress == playbackRequest.ignoreSavedProgress &&
            playbackSessionState.currentRequest?.autoPlay == playbackRequest.autoPlay &&
            activeLoadJob?.isActive == true
        ) {
            Logger.d("PlayerVM", " Already loading ${playbackRequest.bvid}, skip")
            return
        }
        
        //  [修复] 更智能的重复检测：只有播放器真正在播放同一视频时才跳过
        // 如果播放器已停止、出错或处于空闲状态，应该重新加载
        val player = exoPlayer
        val isPlayerHealthy = player != null && 
            player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) &&
            player.playerError == null // 没有播放错误
        
        val currentSuccess = _uiState.value as? VideoPlaybackUiState.Success
        val miniPlayerManager = appContext?.let { MiniPlayerManager.getInstance(it) }
        val isSamePlaybackRequest = shouldTreatAsSamePlaybackRequest(
            requestBvid = playbackRequest.bvid,
            requestCid = playbackRequest.cid,
            currentBvid = currentBvid,
            currentCid = currentCid,
            uiBvid = currentSuccess?.info?.bvid,
            uiCid = currentSuccess?.info?.cid ?: 0L,
            miniPlayerBvid = miniPlayerManager?.currentBvid,
            miniPlayerCid = miniPlayerManager?.currentCid ?: 0L,
            miniPlayerActive = miniPlayerManager?.isActive == true
        )
        Logger.d(
            "PlayerVM",
            "SUB_DBG same-playback check: request=${playbackRequest.bvid}/${playbackRequest.cid}, current=$currentBvid/$currentCid, mini=${miniPlayerManager?.currentBvid}/${miniPlayerManager?.currentCid}, miniActive=${miniPlayerManager?.isActive == true}, result=$isSamePlaybackRequest"
        )
        
        // 🎯 [关键修复] 即使 currentBvid 为空（新 ViewModel），如果播放器已经在播放这个视频，也不要重新加载
        // 这种情况发生在 Notification -> MainActivity (New Activity/VM) -> VideoDetailScreen -> reuse attached player
        val isPlayerPlayingSameVideo = isPlayerHealthy && isSamePlaybackRequest
        val isUiLoaded = currentSuccess != null &&
            currentSuccess.info.bvid == playbackRequest.bvid &&
            (playbackRequest.cid <= 0L || currentSuccess.info.cid == playbackRequest.cid)

        val keepLoadedUi = currentSuccess != null &&
            shouldKeepLoadedVideoDetailUiWithoutSkeletonReload(
                force = playbackRequest.force,
                requestBvid = playbackRequest.bvid,
                requestCid = playbackRequest.cid,
                requestAudioLang = playbackRequest.audioLang,
                ignoreSavedProgress = playbackRequest.ignoreSavedProgress,
                videoCodecOverride = playbackRequest.videoCodecOverride,
                loadedBvid = currentSuccess.info.bvid,
                loadedCid = currentSuccess.info.cid,
                loadedAudioLang = currentSuccess.currentAudioLang,
                loadedDirectPlayUrlAvailable = currentSuccess.playUrl.isNotBlank(),
                loadedAdaptiveDashSourceAvailable = currentSuccess.adaptiveDashSource != null,
            )
        Logger.w(
            "VideoReturnTrace",
            "load request=${playbackRequest.bvid}/${playbackRequest.cid}, " +
                "loaded=${currentSuccess?.info?.bvid}/${currentSuccess?.info?.cid}, " +
                "keepLoadedUi=$keepLoadedUi, playerAttached=${player != null}"
        )

        if (keepLoadedUi) {
            if (currentSuccess.relatedLoadState == VideoPlaybackUiState.RelatedLoadState.LOADING &&
                relatedLoadJob?.isActive != true
            ) {
                _detailState.update { it.copy(requestToken = currentLoadRequestToken) }
                loadRelatedVideosForRequest(currentSuccess.info.bvid, currentLoadRequestToken)
            }
            Logger.w("VideoReturnTrace", "keep Success UI for ${playbackRequest.bvid}")
            // 小横条可能缓存了标签请求完成前的详情；快速恢复不会执行完整的后加载计划。
            if (currentSuccess.videoTags.isEmpty()) {
                loadVideoTags(playbackRequest.bvid)
            }
            currentBvid = playbackRequest.bvid
            if (currentCid <= 0L && currentSuccess.info.cid > 0L) {
                currentCid = currentSuccess.info.cid
            } else if (currentSuccess.info.cid > 0L) {
                currentCid = currentSuccess.info.cid
            }
            if (player != null) {
                val shouldSoftRestorePlayer =
                    shouldRestoreAttachedPlayerFromLoadedUi(
                        force = playbackRequest.force,
                        requestBvid = playbackRequest.bvid,
                        requestCid = playbackRequest.cid,
                        requestAudioLang = playbackRequest.audioLang,
                        ignoreSavedProgress = playbackRequest.ignoreSavedProgress,
                        videoCodecOverride = playbackRequest.videoCodecOverride,
                        loadedBvid = currentSuccess.info.bvid,
                        loadedCid = currentSuccess.info.cid,
                        loadedAudioLang = currentSuccess.currentAudioLang,
                        loadedDirectPlayUrlAvailable = currentSuccess.playUrl.isNotBlank(),
                        loadedAdaptiveDashSourceAvailable = currentSuccess.adaptiveDashSource != null,
                        attachedPlayerMediaItemCount = player.mediaItemCount,
                    ) ||
                        // 已有 media 但会话不健康（从子详情返回常见）：仍保留 Success UI，只重绑播放器
                        !isPlayerHealthy ||
                        !isPlayerPlayingSameVideo

                if (shouldSoftRestorePlayer) {
                    val restorePositionMs = playbackUseCase.getCachedPosition(currentBvid, currentCid)
                    val shouldAutoPlay = playbackRequest.autoPlay ?: appContext?.let {
                        com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(it)
                    } ?: true
                    relatedPlaybackPreparedToken = currentLoadRequestToken
                    relatedFirstFrameToken = -1L
                    playResolvedPlayback(
                        videoUrl = currentSuccess.playUrl,
                        audioUrl = currentSuccess.audioUrl,
                        adaptiveDashSource = currentSuccess.adaptiveDashSource,
                        startPositionMs = restorePositionMs,
                        playWhenReady = shouldAutoPlay,
                    )
                    Logger.d(
                        "PlayerVM",
                        "Restored player for ${playbackRequest.bvid} keeping loaded detail UI (no skeleton)"
                    )
                } else {
                    relatedPlaybackPreparedToken = currentLoadRequestToken
                    if (playbackStallRecoveryFirstFrameRendered || player.isPlaying) {
                        relatedFirstFrameToken = currentLoadRequestToken
                    }
                    com.android.purebilibili.core.player.PlayerVolumeController.applyPreferredVolume(player)
                    val shouldAutoPlay = playbackRequest.autoPlay ?: appContext?.let {
                        com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(it)
                    } ?: true
                    if (shouldAutoPlay && !player.isPlaying) {
                        player.play()
                    }
                    Logger.d(
                        "PlayerVM",
                        "🎯 ${playbackRequest.bvid} UI already loaded and player healthy, skip reload autoPlay=$shouldAutoPlay"
                    )
                }
            } else {
                Logger.d(
                    "PlayerVM",
                    "Keep loaded detail UI for ${playbackRequest.bvid}; player not attached yet"
                )
            }
            restoreSponsorPlaybackState(currentSuccess)
            return
        }

        if (!playbackRequest.force && isPlayerPlayingSameVideo && isUiLoaded) {
            Logger.d("PlayerVM", "🎯 ${playbackRequest.bvid} already playing healthy + UI loaded, skip reload")
            if (currentSuccess.videoTags.isEmpty()) {
                loadVideoTags(playbackRequest.bvid)
            }
            // 补全 ViewModel 状态：currentBvid 可能为空，需要同步
            if (currentBvid.isEmpty()) {
                currentBvid = playbackRequest.bvid
            }
            if (currentCid <= 0L && currentSuccess.info.cid > 0L) {
                currentCid = currentSuccess.info.cid
            }
            
            //  确保音量正常
            com.android.purebilibili.core.player.PlayerVolumeController.applyPreferredVolume(player)
            if (!player.isPlaying) {
                player.play()
            }
            restoreSponsorPlaybackState(currentSuccess)
            return
        }

        // 如果播放器正在播放目标视频，但 UI 未加载（新 ViewModel），我们需要获取信息但跳过播放器重置
        val shouldSkipPlayerPrepare = !playbackRequest.force && isPlayerPlayingSameVideo
        if (shouldSkipPlayerPrepare) {
            Logger.d("PlayerVM", "🎯 ${playbackRequest.bvid} already playing but UI missing (New VM). Fetching info, skipping player prepare.")
        }
        
        if (currentBvid.isNotEmpty() && currentBvid != playbackRequest.bvid) {
            flushPlaybackHeartbeatSnapshot(reason = "switch_video")
            recordCreatorWatchProgressSnapshot()
            saveCurrentPosition()
        }

        // 合集/页内换片：在异步 load 完成前立刻停掉旧音频，避免黑屏仍播上一集声音。
        if (
            shouldHaltPlaybackForPendingMediaSwitch(
                force = playbackRequest.force,
                skipPlayerPrepare = shouldSkipPlayerPrepare,
                requestBvid = playbackRequest.bvid,
                requestCid = playbackRequest.cid,
                currentBvid = currentBvid,
                currentCid = currentCid,
                uiBvid = currentSuccess?.info?.bvid,
                uiCid = currentSuccess?.info?.cid ?: 0L
            )
        ) {
            haltPlaybackForPendingMediaSwitch()
        }
        
        // 🛡️ [修复] 加载新视频时重置标志
        hasUserStartedPlayback = false
        
        val progressCid = playbackRequest.resolveProgressCid(
            currentBvid = currentBvid,
            currentCid = currentCid,
            uiBvid = currentSuccess?.info?.bvid,
            uiCid = currentSuccess?.info?.cid ?: 0L
        )
        Logger.d(
            "PlayerVM",
            "SUB_DBG loadVideo request resolved progressCid=$progressCid for request=${playbackRequest.bvid}/${playbackRequest.cid}"
        )
        val cachedPosition = playbackUseCase.getCachedPosition(playbackRequest.bvid, progressCid)
        val requestedStartPositionMs = resolveRequestedStartPositionMs(
            cachedPositionMs = cachedPosition,
            fallbackResumePositionMs = fallbackResumePositionMs
        )
        clearInteractiveChoiceRuntime()
        lastCreatorSignalPositionSec = requestedStartPositionMs / 1000L
        val loadRequestContext = playbackSessionStore.beginLoadRequest(playbackRequest)
        val requestToken = loadRequestContext.requestToken
        videoLoadTimeline.start(requestToken)
        relatedPlaybackPreparedToken = -1L
        relatedFirstFrameToken = -1L
        playerInfoJob?.cancel()
        activeLoadJob?.cancel()
        relatedLoadJob?.cancel()
        relatedLoadJob = null
        if (playbackRequest.force) {
            VideoRepository.invalidateVideoLoadCaches(playbackRequest.bvid, playbackRequest.aid, playbackRequest.cid)
        }
        _detailState.value = VideoDetailUiState(
            requestBvid = playbackRequest.bvid,
            requestCid = playbackRequest.cid,
            requestToken = requestToken
        )
        val canStartDataLoad = PlaybackCooldownManager.getCooldownStatus(playbackRequest.bvid) is
            com.android.purebilibili.core.cooldown.CooldownStatus.Ready
        
        activeLoadJob = viewModelScope.launch {
            if (!shouldApplyVideoLoadResult(
                    activeRequestToken = currentLoadRequestToken,
                    resultRequestToken = requestToken,
                    expectedBvid = bvid,
                    currentBvid = currentBvid
                )
            ) {
                Logger.d("PlayerVM", "⏭️ Skip stale load request before start: bvid=${playbackRequest.bvid} token=$requestToken")
                return@launch
            }
            Logger.w("VideoReturnTrace", "show Loading.Initial for ${playbackRequest.bvid}")
            _uiState.value = VideoPlaybackUiState.Loading.Initial
            val videoInfoDeferred = async {
                if (!canStartDataLoad) {
                    return@async Result.failure<ViewInfo>(IllegalStateException("Playback request is in cooldown"))
                }
                VideoRepository.getVideoInfoOnly(
                    bvid = playbackRequest.bvid,
                    aid = playbackRequest.aid,
                    requestedCid = playbackRequest.cid
                ).also { result ->
                    result.onSuccess { info ->
                        publishVideoDetailInfo(info, playbackRequest.bvid, requestToken)
                    }
                }
            }
            if (canStartDataLoad && playbackRequest.bvid.startsWith("BV", ignoreCase = true)) {
                loadRelatedVideosForRequest(playbackRequest.bvid, requestToken)
            }
            
                val isLoggedIn = com.android.purebilibili.data.repository.VideoRepository.isPlaybackLoggedIn()
                var storedQualityForWarning = 64
                var autoHighestQualityEnabledForLoad = false
                val defaultQuality = appContext?.let { context ->
                    val storedQuality = NetworkUtils.getDefaultQualityId(context)
                    val autoHighestEnabled = com.android.purebilibili.core.store.SettingsManager
                        .getAutoHighestQualitySync(context)
                    storedQualityForWarning = storedQuality
                    autoHighestQualityEnabledForLoad = autoHighestEnabled
                    val effectiveVip = VideoRepository.refreshVipStatusForPreferredQualityIfNeeded(
                        isLoggedIn = isLoggedIn,
                        cachedIsVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip(),
                        storedQuality = storedQuality,
                        autoHighestEnabled = autoHighestEnabled
                    )
                    com.android.purebilibili.core.util.resolvePlaybackDefaultQualityId(
                        storedQuality = storedQuality,
                        autoHighestEnabled = autoHighestEnabled,
                        isLoggedIn = isLoggedIn,
                        isVip = effectiveVip
                    )
                } ?: 64
                //  [新增] 获取音频/视频偏好
                val audioQualityPreference = appContext?.let { context ->
                    resolveRequestedAudioQuality(
                        defaultAudioQuality = com.android.purebilibili.core.store.player.PlayerSettingsStore
                            .getCachedDefaultAudioQuality(context),
                        rememberedAudioQuality = com.android.purebilibili.core.store.player.PlayerSettingsStore
                            .getCachedLastSelectedAudioQuality(context)
                    )
                } ?: -1
                val settingsCodecPreference = appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getVideoCodecSync(it)
                } ?: HEVC_CODEC_KEY
                val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
                val videoCodecPreference = resolveEffectiveVideoCodecPreference(
                    requestCodecOverride = playbackRequest.videoCodecOverride,
                    settingsCodecPreference = settingsCodecPreference,
                    sessionBlockedCodecs = sessionBlockedCodecs
                )
                val settingsSecondCodecPreference = appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodecSync(it)
                } ?: AVC_CODEC_KEY
                val videoSecondCodecPreference = resolveEffectiveVideoSecondCodecPreference(
                    requestCodecOverride = playbackRequest.videoCodecOverride,
                    settingsSecondCodecPreference = settingsSecondCodecPreference
                )
                
                // [Added] Determine auto-play behavior
                // If autoPlay arg is present, use it. Otherwise reset to "Click to Play" setting
                val shouldAutoPlay = playbackRequest.autoPlay ?: appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(it)
                } ?: true
                
                Logger.d(
                    "VideoPlaybackViewModel",
                    "⏯️ AutoPlay Decision: arg=${playbackRequest.autoPlay}, setting=${shouldAutoPlay}, Final=$shouldAutoPlay, codec=$videoCodecPreference, blocked=$sessionBlockedCodecs"
                )
            
            // 📉 [省流量] 省流量模式逻辑：
            // - ALWAYS: 任何网络都限制 480P
            // - MOBILE_ONLY: 仅移动数据时限制 480P（WiFi不受限）
            val isOnMobileNetwork = appContext?.let { NetworkUtils.isMobileData(it) } ?: false
            val dataSaverMode = appContext?.let { 
                com.android.purebilibili.core.store.SettingsManager.getDataSaverModeSync(it) 
            } ?: com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
            
            //  判断是否应该限制画质
            val shouldLimitQuality = when (dataSaverMode) {
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF -> false
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.ALWAYS -> true  // 任何网络都限制
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY -> isOnMobileNetwork  // 仅移动数据
            }
            
            var finalQuality = defaultQuality
            val dataSaverLimitedQuality = shouldLimitQuality && finalQuality > 32
            if (dataSaverLimitedQuality) {
                finalQuality = 32
                com.android.purebilibili.core.util.Logger.d("VideoPlaybackViewModel", "📉 省流量模式(${dataSaverMode.label}): 限制画质为480P")
            }
            
            try {
                val capabilityContext = appContext
                val loadQuality = finalQuality
                val playbackSpeed = exoPlayer?.playbackParameters?.speed ?: capabilityContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getPreferredPlaybackSpeedSync(it)
                }
                val loadConfig = withContext(Dispatchers.Default) {
                    PlaybackLoadConfig(
                        defaultQuality = loadQuality,
                        audioQualityPreference = audioQualityPreference,
                        videoCodecPreference = videoCodecPreference,
                        videoSecondCodecPreference = videoSecondCodecPreference,
                        playWhenReady = shouldAutoPlay,
                        isAv1Supported = resolveEffectiveAv1Support(
                            deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                            sessionBlockedCodecs = sessionBlockedCodecs
                        ),
                        isHdrSupported = com.android.purebilibili.core.util.MediaUtils
                            .isHdrSupported(capabilityContext),
                        isDolbyVisionSupported = com.android.purebilibili.core.util.MediaUtils
                            .isDolbyVisionSupported(capabilityContext),
                        onVideoInfo = { info ->
                            publishVideoDetailInfo(info, playbackRequest.bvid, requestToken)
                        },
                        videoInfoLoader = { videoInfoDeferred.await() },
                        playbackSpeed = playbackSpeed
                    )
                }
                // 🛡️ [修复] 增加超时保护，防止加载无限挂起
                val loadResult = kotlinx.coroutines.withTimeout(15000L) {
                    playbackLoader.load(
                        request = playbackRequest,
                        cachedPositionMs = cachedPosition,
                        config = loadConfig
                    )
                }

                when (loadResult) {
                    is PlaybackLoadResult.Success -> {
                        val result = loadResult.payload
                        if (!shouldApplyVideoLoadResult(
                                activeRequestToken = currentLoadRequestToken,
                                resultRequestToken = requestToken,
                                expectedBvid = playbackRequest.bvid,
                                currentBvid = currentBvid
                            )
                        ) {
                            Logger.d("PlayerVM", "⏭️ Ignore stale load success: bvid=${playbackRequest.bvid} token=$requestToken")
                            return@launch
                        }
                        currentCid = result.info.cid
                        logVideoLoadStage(VideoLoadStage.PLAY_URL_READY, requestToken)
                        Logger.d(
                            "PlayerVM",
                            "SUB_DBG loadVideo success: requested=${playbackRequest.bvid}/${playbackRequest.cid}, loaded=${result.info.bvid}/${result.info.cid}, token=$requestToken"
                        )
                        
                        // 🛠️ [修复] 检查是否已播放结束 (余量 < 5秒)
                        // 若上次已看完，则从头开始播放，避免立即触发 STATE_ENDED 导致循环跳转
                        val videoDuration = result.duration
                        var startPos = resolveRequestedStartPositionMs(
                            cachedPositionMs = loadResult.cachedPositionMs,
                            fallbackResumePositionMs = fallbackResumePositionMs
                        )
                        if (videoDuration > 0 && startPos >= videoDuration - 5000) {
                             Logger.d("PlayerVM", "🛡️ Previous position at end ($startPos / $videoDuration), restarting from 0")
                             startPos = 0
                        }

                        val cdnSelection = resolvePlaybackCdnCandidateSelection(
                            videoUrl = result.playUrl,
                            audioUrl = result.audioUrl,
                            quality = result.quality,
                            cachedDashVideos = result.cachedDashVideos,
                            cachedDashAudios = result.cachedDashAudios,
                            adaptiveDashSource = result.adaptiveDashSource
                        )

                        // Play video
                        if (!shouldSkipPlayerPrepare) {
                            playbackPreparationAllowed.first { it }
                            relatedPlaybackPreparedToken = requestToken
                            relatedFirstFrameToken = -1L
                            playResolvedPlayback(
                                videoUrl = cdnSelection.playUrl,
                                audioUrl = cdnSelection.audioUrl,
                                adaptiveDashSource = cdnSelection.adaptiveDashSource,
                                startPositionMs = startPos,
                                playWhenReady = shouldAutoPlay,
                                cdnFallbackState = cdnSelection.fallbackState,
                                cdnCacheKeysByUrl = cdnSelection.cdnCacheKeysByUrl
                            )
                            logVideoLoadStage(VideoLoadStage.PLAYER_PREPARE_REQUESTED, requestToken)
                        } else {
                             relatedPlaybackPreparedToken = requestToken
                             relatedFirstFrameToken = requestToken
                             // 🎯 Skip preparing player, but ensure it's playing if needed
                             Logger.d("PlayerVM", "🎯 Skipping player preparation (already playing)")
                             exoPlayer?.let { p ->
                                 com.android.purebilibili.core.player.PlayerVolumeController.applyPreferredVolume(p)
                                 if (!p.isPlaying) p.play()
                             }
                        }
                        
                        Logger.d(
                            "PlayerVM",
                            "📡 CDN 线路: 视频${cdnSelection.allVideoUrls.size}个, 音频${cdnSelection.allAudioUrls.size}个" +
                                (cdnSelection.regionLabel?.let { ", 属地优选=$it" } ?: "")
                        )
                        
                        val readyState = VideoPlaybackUiState.Success(
                            info = result.info,
                            playUrl = cdnSelection.playUrl,
                            audioUrl = cdnSelection.audioUrl,
                            related = _detailState.value.related,
                            relatedLoadState = _detailState.value.relatedLoadState,
                            currentQuality = result.quality,
                            playbackQualityMode = resolveInitialPlaybackQualityMode(),
                            adaptiveDashSource = cdnSelection.adaptiveDashSource,
                            qualityIds = result.qualityIds,
                            qualityLabels = result.qualityLabels,
                            switchableQualityIds = result.switchableQualityIds,
                            cachedDashVideos = result.cachedDashVideos,
                            cachedDashAudios = result.cachedDashAudios,
                            cachedDash = result.cachedDash,
                            requestedAudioQuality = result.requestedAudioQuality,
                            selectedAudioQuality = result.selectedAudioQuality,
                            availableAudioQualities = result.availableAudioQualities,
                            audioFallbackReason = result.audioFallbackReason,
                            emoteMap = result.emoteMap,
                            isLoggedIn = result.isLoggedIn,
                            isVip = result.isVip,
                            isFollowing = result.isFollowing,
                            isFavorited = result.isFavorited,
                            isLiked = result.isLiked,
                            likeStatusKnown = result.likeStatusKnown,
                            followStatusKnown = result.followStatusKnown,
                            coinCount = result.coinCount,
                            //  CDN 线路
                            currentCdnIndex = 0,
                            allVideoUrls = cdnSelection.allVideoUrls,

                            allAudioUrls = cdnSelection.allAudioUrls,
                            cdnCandidateSources = cdnSelection.candidateSources,
                            cdnLineDiagnostics = cdnSelection.lineDiagnostics,

                            // [New] Codec/Audio info
                            videoCodecId = result.videoCodecId,
                            audioCodecId = result.audioCodecId,
                            // [New] AI Audio
                            aiAudio = result.aiAudio,
                            currentAudioLang = result.curAudioLang,
                            videoDurationMs = result.duration
                        )
                        _uiState.value = readyState
                        publishSubjectSnapshot(readyState)
                        // Do not wait for the foreground Compose collector to mirror this state.
                        // Background collection is lifecycle-paused, while playback can still
                        // advance through a UGC season.
                        appContext?.let { context ->
                            exoPlayer?.let { player ->
                                MiniPlayerManager.getInstance(context).syncCurrentVideoInfo(
                                    readyState,
                                    expectedPlayer = player,
                                )
                            }
                        }

                        // Schedule non-blocking HDR auto-upgrade after SDR fast-start
                        val initialDashVideoIds = result.cachedDashVideos
                            .filter { it.getValidUrl().isNotEmpty() }
                            .map { it.id }
                            .distinct()
                        val hasToken = !com.android.purebilibili.core.store.TokenManager.accessTokenCache.isNullOrEmpty()
                        val premiumAutoUpgradeScheduled = scheduleHdrAutoUpgradeIfNeeded(
                            bvid = result.info.bvid,
                            cid = result.info.cid,
                            audioLang = result.curAudioLang,
                            playableDashVideoIds = initialDashVideoIds,
                            isAutoHighestQuality = autoHighestQualityEnabledForLoad,
                            isVip = result.isVip,
                            isMobileData = isOnMobileNetwork,
                            hasAccessToken = hasToken,
                            initialQuality = result.quality
                        )

                        val requestedQualityForWarning = if (autoHighestQualityEnabledForLoad) {
                            defaultQuality
                        } else {
                            storedQualityForWarning
                        }
                        val initialQualityWarningTarget = resolveInitialQualityWarningTarget(
                            requestedQualityId = requestedQualityForWarning,
                            isLoggedIn = result.isLoggedIn,
                            isVip = result.isVip,
                            resolvedTargetQuality = result.resolvedTargetQuality,
                            dataSaverLimited = dataSaverLimitedQuality
                        )
                        val initialQualityUnavailableReason = resolveInitialQualityUnavailableReason(
                            requestedQualityId = initialQualityWarningTarget,
                            actualQualityId = result.quality,
                            isLoggedIn = result.isLoggedIn,
                            isVip = result.isVip,
                            dataSaverLimited = dataSaverLimitedQuality
                        )
                        // A fast-start payload may legitimately be upgraded in the background.
                        // Do not report it as a terminal failure before that exact-track request finishes.
                        if (shouldShowInitialQualityUnavailableDialog(
                                unavailableReason = initialQualityUnavailableReason,
                                premiumAutoUpgradeScheduled = premiumAutoUpgradeScheduled
                            )
                        ) {
                            showQualitySwitchFailureDialog(
                                requestedQualityId = initialQualityWarningTarget,
                                hasCachedDashTracks = result.cachedDashVideos.isNotEmpty(),
                                cacheContainsRequestedQuality = result.cachedDashVideos.any { it.id == initialQualityWarningTarget },
                                initialUnavailableReason = initialQualityUnavailableReason
                            )
                        }
                        maybeEmitResumePlaybackSuggestion(
                            requestCid = playbackRequest.cid,
                            loadedInfo = result.info
                        )

                        scheduleDeferredPostLoadWork(
                            loadedBvid = result.info.bvid,
                            loadedCid = result.info.cid,
                            loadedAid = result.info.aid,
                            loadedOwnerMid = result.info.owner.mid,
                            isLoggedIn = result.isLoggedIn,
                            requestToken = requestToken
                        )
                        val videoNoteEnabled = appContext?.let {
                            com.android.purebilibili.core.store.SettingsManager.getVideoNoteEnabledSync(it)
                        } ?: true
                        if (shouldLoadVideoNote(videoNoteEnabled, result.info.aid)) {
                            loadVideoNote(
                                loadedBvid = result.info.bvid,
                                loadedAid = result.info.aid
                            )
                        }

                        //  [新增] 更新播放列表
                        updatePlaylist(result.info, readyState.related)
                        samplePlaybackAnalytics()
                    }
                    is PlaybackLoadResult.Error -> {
                        if (!shouldApplyVideoLoadResult(
                                activeRequestToken = currentLoadRequestToken,
                                resultRequestToken = requestToken,
                                expectedBvid = playbackRequest.bvid,
                                currentBvid = currentBvid
                            )
                        ) {
                            Logger.d("PlayerVM", "⏭️ Ignore stale load error: bvid=${playbackRequest.bvid} token=$requestToken")
                            return@launch
                        }
                        AnalyticsHelper.logVideoError(
                            com.android.purebilibili.core.util.loadErrorCategory(loadResult.error), "load"
                        )
                        CrashReporter.reportVideoError(
                            playbackRequest.bvid,
                            "load_failed",
                            loadResult.error.toUserMessage()
                        )
                        Logger.d(
                            "PlayerVM",
                            "SUB_DBG loadVideo error: requested=${playbackRequest.bvid}/${playbackRequest.cid}, token=$requestToken, error=${loadResult.error}"
                        )
                        _uiState.value = VideoPlaybackUiState.Error(loadResult.error, loadResult.canRetry)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (!shouldApplyVideoLoadResult(
                        activeRequestToken = currentLoadRequestToken,
                        resultRequestToken = requestToken,
                        expectedBvid = playbackRequest.bvid,
                        currentBvid = currentBvid
                    )
                ) {
                    Logger.d("PlayerVM", "⏭️ Ignore stale timeout: bvid=${playbackRequest.bvid} token=$requestToken")
                    return@launch
                }
                AnalyticsHelper.logVideoError("timeout", "load")
                Logger.e("PlayerVM", "⚠️ Video load timed out for ${playbackRequest.bvid}")
                PlaybackCooldownManager.recordFailure(playbackRequest.bvid, "timeout")
                _uiState.value = VideoPlaybackUiState.Error(VideoLoadError.Timeout)
            } catch (e: CancellationException) {
                Logger.d("PlayerVM", "loadVideo canceled: bvid=${playbackRequest.bvid} token=$requestToken")
                throw e
            } catch (e: Exception) {
                if (!shouldApplyVideoLoadResult(
                        activeRequestToken = currentLoadRequestToken,
                        resultRequestToken = requestToken,
                        expectedBvid = playbackRequest.bvid,
                        currentBvid = currentBvid
                    )
                ) {
                    Logger.d("PlayerVM", "⏭️ Ignore stale exception: bvid=${playbackRequest.bvid} token=$requestToken")
                    return@launch
                }
                AnalyticsHelper.logVideoError(
                    com.android.purebilibili.core.util.loadErrorCategory(VideoLoadError.fromException(e)), "load"
                )
                Logger.e("PlayerVM", "⚠️ Unexpected load exception", e)
                _uiState.value = VideoPlaybackUiState.Error(VideoLoadError.UnknownError(e))
            } finally {
                videoInfoDeferred.cancel()
                if (activeLoadJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    activeLoadJob = null
                }
            }
        }
    }
    /**
     * Schedule a non-blocking HDR auto-upgrade after SDR fast-start playback.
     *
     * Only invoked for INITIAL requests. The APP access_token API is called in a
     * viewModelScope coroutine — it never blocks the first-frame or the UI thread.
     *
     * Per the plan: same video & same page & same audio language → at most once.
     */
    private fun scheduleHdrAutoUpgradeIfNeeded(
        bvid: String,
        cid: Long,
        audioLang: String?,
        playableDashVideoIds: List<Int>,
        isAutoHighestQuality: Boolean,
        isVip: Boolean,
        isMobileData: Boolean,
        hasAccessToken: Boolean,
        initialQuality: Int
    ): Boolean {
        val playbackKey = buildPremiumAutoUpgradePlaybackKey(bvid, cid, audioLang)
        hdrAutoUpgradeJob?.cancel()
        hdrAutoUpgradeJob = null

        if (!shouldScheduleHdrAutoUpgrade(
                isInitialRequest = true,
                isAutoHighestQuality = isAutoHighestQuality,
                isVip = isVip,
                isMobileData = isMobileData,
                hasAccessToken = hasAccessToken,
                currentPlayableDashVideoIds = playableDashVideoIds,
                userHasExplicitQualitySelection = hasExplicitQualitySelectionForPlayback(
                    playbackKey = playbackKey,
                    explicitSelectionKeys = explicitQualitySelectionKeys
                ),
                upgradeAlreadyAttempted = attemptedUpgradeKeys.contains(playbackKey)
            )
        ) {
            return false
        }

        attemptedUpgradeKeys.add(playbackKey)

        Logger.d(
            "PlayerVM",
            "PLAY_DIAG premium_upgrade_schedule playbackKey=$playbackKey " +
                "target=125 currentActual=$initialQuality " +
                "hasAccessToken=$hasAccessToken reason=INITIAL_SDR_FAST_START"
        )

        hdrAutoUpgradeJob = viewModelScope.launch {
            val selectionGenerationAtStart = explicitQualitySelectionGeneration

            val hdrData = VideoRepository.getExactPremiumPlayUrl(
                bvid = bvid,
                cid = cid,
                targetQn = 125,
                audioLang = audioLang
            )

            if (!isUpgradeResultStillApplicable(playbackKey, selectionGenerationAtStart)) {
                Logger.d(
                    "PlayerVM",
                    "PLAY_DIAG premium_upgrade_cancel playbackKey=$playbackKey reason=STALE_RESULT"
                )
                return@launch
            }

            if (hdrData == null) {
                return@launch
            }

            val hdrDashIds = hdrData.dash?.video?.map { it.id }?.distinct().orEmpty()
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_response playbackKey=$playbackKey target=125 " +
                    "source=APP returnedQuality=${hdrData.quality} dashIds=$hdrDashIds " +
                    "exactTargetFound=${125 in hdrDashIds}"
            )

            if (125 !in hdrDashIds) {
                return@launch
            }

            applyHdrUpgrade(playbackKey, hdrData)
        }
        return true
    }

    /**
     * Validate that the async HDR result is still applicable to the current playback.
     *
     * Guards against:
     * - User navigating to a different video/page
     * - User making an explicit quality selection
     */
    private fun isUpgradeResultStillApplicable(
        playbackKey: String,
        selectionGenerationAtStart: Long
    ): Boolean {
        val currentAudioLang = (_uiState.value as? VideoPlaybackUiState.Success)?.currentAudioLang
        val currentKey = buildPremiumAutoUpgradePlaybackKey(
            currentBvid,
            currentCid,
            currentAudioLang
        )

        if (currentKey != playbackKey) {
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_cancel playbackKey=$playbackKey " +
                    "reason=PLAYBACK_KEY_CHANGED current=$currentKey"
            )
            return false
        }

        if (selectionGenerationAtStart != explicitQualitySelectionGeneration) {
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_cancel playbackKey=$playbackKey " +
                    "reason=USER_EXPLICIT_QUALITY_CHANGE " +
                    "genAtStart=$selectionGenerationAtStart current=$explicitQualitySelectionGeneration"
            )
            return false
        }

        return true
    }

    /**
     * Apply the HDR upgrade result to the player without restarting playback.
     *
     * Preserves current position and play/pause state.
     * Reuses the existing [playResolvedPlayback] infrastructure.
     */
    private fun applyHdrUpgrade(playbackKey: String, hdrData: PlayUrlData) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val wasPlaying = exoPlayer?.isPlaying ?: false

        val dash = hdrData.dash
        if (dash == null || dash.video.isEmpty()) {
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_failed playbackKey=$playbackKey " +
                    "reason=NO_DASH_VIDEO_DATA"
            )
            return
        }

        val videoCodecPreference = _videoCodecPreference.value
        val videoSecondCodecPreference = _videoSecondCodecPreference.value
        val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
        val isAv1Supported = com.android.purebilibili.core.util.MediaUtils.isAv1Supported()
        val audioQualityPreference = current.requestedAudioQuality
        val hdrPlaybackQualityMode = PlaybackQualityMode.LOCKED(125)

        val selection = playbackUseCase.resolvePlaybackSelection(
            playUrlData = hdrData,
            targetQuality = 125,
            audioQualityPreference = audioQualityPreference,
            playbackSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f,
            videoCodecPreference = videoCodecPreference,
            videoSecondCodecPreference = videoSecondCodecPreference,
            playbackQualityMode = hdrPlaybackQualityMode,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        ) ?: run {
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_failed playbackKey=$playbackKey " +
                    "reason=RESOLVE_SELECTION_NULL"
            )
            return
        }

        if (!isExactRequestedQualitySelected(125, selection.actualQuality)) {
            Logger.d(
                "PlayerVM",
                "PLAY_DIAG premium_upgrade_failed playbackKey=$playbackKey " +
                    "reason=ACTUAL_QUALITY_MISMATCH actual=${selection.actualQuality}"
            )
            return
        }

        val cdnSelection = resolvePlaybackCdnCandidateSelection(
            videoUrl = selection.videoUrl,
            audioUrl = selection.audioUrl,
            quality = selection.actualQuality,
            cachedDashVideos = selection.cachedDashVideos,
            cachedDashAudios = selection.cachedDashAudios,
            adaptiveDashSource = selection.adaptiveDashSource
        )

        val playWhenReady = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true

        applyQualityChangeResult(
            payload = QualityChangePayload(
                playUrl = selection.videoUrl,
                audioUrl = selection.audioUrl,
                actualQuality = selection.actualQuality,
                adaptiveDashSource = selection.adaptiveDashSource,
                cachedDashVideos = selection.cachedDashVideos,
                cachedDashAudios = selection.cachedDashAudios,
                cachedDash = selection.cachedDash,
                requestedAudioQuality = selection.requestedAudioQuality,
                selectedAudioQuality = selection.selectedAudioQuality,
                availableAudioQualities = selection.availableAudioQualities,
                audioFallbackReason = selection.audioFallbackReason,
                qualityIds = selection.qualityIds,
                qualityLabels = selection.qualityLabels,
                switchableQualityIds = selection.switchableQualityIds,
                wasFallback = false,
                currentPos = currentPos,
                playWhenReady = playWhenReady,
                playbackQualityMode = hdrPlaybackQualityMode
            ),
            reason = QualityChangeReason.INITIAL_AUTO_UPGRADE,
            cdnSelection = cdnSelection
        )

        Logger.d(
            "PlayerVM",
            "PLAY_DIAG premium_upgrade_applied playbackKey=$playbackKey " +
                "quality=125 position=$currentPos wasPlaying=$wasPlaying"
        )
    }
    
    /**
     * [New] Change Audio Language (AI Translation)
     */
    fun changeAudioLanguage(lang: String?) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.currentAudioLang == lang) return
        
        Logger.d("PlayerVM", "🗣️ Changing audio language to: $lang")
        
        // Reload video with new language
        // We set force=true to ensure it reloads even if bvid is same
        // 🛠️ [修复] 切换语言时，不要自动连播，只是重新加载当前分P
        loadVideo(current.info.bvid, current.info.aid, force = true, autoPlay = true, audioLang = lang)
    }

    /**
     * 点赞弹幕
     */
    fun likeDanmaku(dmid: Long) {
        if (dmid <= 0L) {
            viewModelScope.launch { toast("当前弹幕不支持点赞") }
            return
        }

        val menuState = _danmakuMenuState.value
        val shouldLike = if (menuState.visible && menuState.dmid == dmid && menuState.canVote) {
            !menuState.hasLiked
        } else {
            dmid !in _likedDanmakuIds.value
        }
        likeDanmaku(dmid = dmid, like = shouldLike)
    }

    /**
     * 举报弹幕
     */
    fun reportDanmaku(dmid: Long, reason: Int) {
        reportDanmaku(dmid = dmid, reason = reason, content = "")
    }
    
    /**
     *  [新增] 更新播放列表
     */
    private fun updatePlaylist(currentInfo: com.android.purebilibili.data.model.response.ViewInfo, related: List<com.android.purebilibili.data.model.response.RelatedVideo>) {
        val currentPlaylist = PlaylistManager.playlist.value
        val externalDecision = resolveExternalPlaylistSyncDecision(
            isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
            playlist = currentPlaylist,
            currentBvid = currentInfo.bvid
        )

        // 🔒 外部队列（收藏夹、稍后再看等）始终优先，听视频模式也不应被合集/分P 队列覆盖。
        if (externalDecision.keepExternalPlaylist) {
            val matchIndex = externalDecision.matchedIndex
            if (matchIndex in currentPlaylist.indices) {
                PlaylistManager.playAt(matchIndex)
                Logger.d("PlayerVM", "🔒 外部播放列表模式: 更新索引到 $matchIndex/${currentPlaylist.size}")
            }
            return
        }

        if (shouldApplyAudioModeCollectionPlaylist(
                isInAudioMode = _isInAudioMode.value,
                keepExternalPlaylist = false
            )
        ) {
            val audioPlaylist = if (currentInfo.pages.size > 1) {
                buildAudioModePagePlaylist(
                    pages = currentInfo.pages,
                    currentBvid = currentInfo.bvid,
                    currentCid = currentInfo.cid,
                    videoTitle = currentInfo.title,
                    cover = currentInfo.pic,
                    owner = currentInfo.owner.name
                )
            } else {
                currentInfo.ugc_season?.let { season ->
                    buildAudioModeCollectionPlaylist(
                        episodes = season.sections.flatMap { it.episodes },
                        currentBvid = currentInfo.bvid,
                        currentCid = currentInfo.cid
                    )
                }
            }
            if (audioPlaylist != null) {
                PlaylistManager.setPlaylist(
                    items = audioPlaylist.items,
                    startIndex = audioPlaylist.startIndex
                )
                Logger.d(
                    "PlayerVM",
                    "🎵 听视频分集队列: ${audioPlaylist.items.size} 项, 当前=${audioPlaylist.startIndex}"
                )
                return
            }

            val currentQueuedItem = currentPlaylist.getOrNull(PlaylistManager.currentIndex.value)
            if (
                currentQueuedItem != null &&
                currentQueuedItem.bvid == currentInfo.bvid &&
                (currentQueuedItem.cid <= 0L || currentQueuedItem.cid == currentInfo.cid)
            ) {
                return
            }
        }

        if (PlaylistManager.isExternalPlaylist.value) {
            Logger.d("PlayerVM", "🔓 外部播放列表模式: 当前视频 ${currentInfo.bvid} 不在外部列表，重建为普通队列")
        }

        val currentIndex = PlaylistManager.currentIndex.value
        val currentItemInList = currentPlaylist.getOrNull(currentIndex)

        // 转换推荐视频为播放项
        val relatedItems = related.map { video ->
            PlaylistItem(
                bvid = video.bvid,
                cid = video.cid,
                title = video.title,
                cover = video.pic,
                owner = video.owner.name,
                ownerFace = video.owner.face,
                duration = video.duration.toLong()
            )
        }
        
        // 创建当前视频的播放项 (updated with full info)
        val currentFullItem = PlaylistItem(
            bvid = currentInfo.bvid,
            cid = currentInfo.cid,
            title = currentInfo.title,
            cover = currentInfo.pic,
            owner = currentInfo.owner.name,
            ownerFace = currentInfo.owner.face,
            duration = 0L // ViewInfo 暂无 duration 字段，暂置为 0
        )

        if (currentItemInList != null && currentItemInList.bvid == currentInfo.bvid) {
             // 命中当前播放列表逻辑：保留历史，更新未来
             // 1. 获取当前索引及之前的列表 (历史 + 当前)
             val history = currentPlaylist.take(currentIndex) // 0 .. currentIndex-1
             
             // 2. 组合新列表: 历史 + 当前(更新详情) + 新推荐
             val newPlaylist = history + currentFullItem + relatedItems
             
             // 3. 更新列表，保持当前索引不变
             PlaylistManager.setPlaylist(newPlaylist, currentIndex)
             Logger.d("PlayerVM", "🎵 播放列表已扩展: 保留 ${history.size} 项历史, 更新后续 ${relatedItems.size} 项")
        } else {
            // 新播放逻辑：当前 + 推荐
            val playlist = listOf(currentFullItem) + relatedItems
            PlaylistManager.setPlaylist(playlist, 0)
            Logger.d("PlayerVM", "🎵 播放列表已重置: 1 + ${relatedItems.size} 项")
        }
        
        val preloadCount = resolveRelatedPlayUrlPreloadCount(
            relatedCount = related.size,
            isWifi = appContext?.let { NetworkUtils.isWifi(it) } ?: false
        )
        if (preloadCount > 0) {
            preloadRelatedPlayUrls(related.take(preloadCount))
        }
    }
    
    /**
     * 🚀 [新增] 预加载推荐视频的 PlayUrl
     * 异步获取视频详情（获取 cid）并缓存 PlayUrl，切换视频时更快
     */
    private fun preloadRelatedPlayUrls(videos: List<com.android.purebilibili.data.model.response.RelatedVideo>) {
        if (videos.isEmpty()) return
        val context = appContext ?: return
        if (!NetworkUtils.isWifi(context)) {
            Logger.d("PlayerVM", "🚀 Skip preload on non-WiFi")
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (video in videos) {
                try {
                    // 获取视频详情（主要是为了获取 cid）
                    // getVideoDetails 返回 Pair<ViewInfo, PlayUrlData>
                    val detailResult = com.android.purebilibili.data.repository.VideoRepository.getVideoDetails(video.bvid)
                    val (viewInfo, _) = detailResult.getOrNull() ?: continue
                    
                    // 检查 PlayUrl 是否已缓存
                    if (com.android.purebilibili.core.cache.PlayUrlCache.get(video.bvid, viewInfo.cid) != null) {
                        Logger.d("PlayerVM", "🚀 Preload skip (cached): ${video.bvid}")
                        continue
                    }
                    
                    // 获取默认画质
                    val isLoggedIn = com.android.purebilibili.data.repository.VideoRepository.isPlaybackLoggedIn()
                    val defaultQuality = appContext?.let { context ->
                        val storedQuality = com.android.purebilibili.core.util.NetworkUtils
                            .getDefaultQualityId(context)
                        val autoHighestEnabled = com.android.purebilibili.core.store.SettingsManager
                            .getAutoHighestQualitySync(context)
                        val effectiveVip = com.android.purebilibili.data.repository.VideoRepository
                            .refreshVipStatusForPreferredQualityIfNeeded(
                                isLoggedIn = isLoggedIn,
                                cachedIsVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip(),
                                storedQuality = storedQuality,
                                autoHighestEnabled = autoHighestEnabled
                            )
                        com.android.purebilibili.core.util.resolvePlaybackDefaultQualityId(
                            storedQuality = storedQuality,
                            autoHighestEnabled = autoHighestEnabled,
                            isLoggedIn = isLoggedIn,
                            isVip = effectiveVip
                        )
                    } ?: 64
                    
                    // 预加载 PlayUrl（会自动缓存到 PlayUrlCache）
                    com.android.purebilibili.data.repository.VideoRepository.getPlayUrlData(
                        video.bvid, 
                        viewInfo.cid, 
                        defaultQuality
                    )
                    Logger.d("PlayerVM", "🚀 Preloaded PlayUrl: ${video.bvid}")
                } catch (e: Exception) {
                    // 预加载失败不影响正常播放，静默忽略
                    Logger.d("PlayerVM", "🚀 Preload failed (ignored): ${video.bvid}")
                }
            }
        }
    }
    
    fun retry() {
        val bvid = currentBvid.takeIf { it.isNotBlank() } ?: return
        val fallbackResumePositionMs = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val resumePlaybackAfterRetry = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true
        val currentAudioLang = (_uiState.value as? VideoPlaybackUiState.Success)?.currentAudioLang
        
        //  检查当前错误类型，如果是全局冷却则清除所有冷却
        val currentState = _uiState.value
        if (currentState is VideoPlaybackUiState.Error && 
            currentState.error is VideoLoadError.GlobalCooldown) {
            PlaybackCooldownManager.clearAll()
        } else {
            // 清除该视频的冷却状态，允许用户强制重试
            PlaybackCooldownManager.clearForVideo(bvid)
        }
        
        PlayUrlCache.invalidate(bvid, currentCid)
        playbackSessionStore.clearCurrentMedia()
        loadVideo(
            bvid = bvid,
            autoPlay = resumePlaybackAfterRetry,
            cid = currentCid,
            audioLang = currentAudioLang,
            fallbackResumePositionMs = fallbackResumePositionMs
        )
    }

    /**
     * 解码类错误时按用户的次选编码重试，次选不可用或再次失败时再使用 AVC。
     */
    fun retryWithCodecFallback() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: run {
            retry()
            return
        }

        val bvid = current.info.bvid.takeIf { it.isNotBlank() } ?: return
        val fallbackResumePositionMs = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val resumePlaybackAfterRetry = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true
        val failedCodec = resolvePlaybackVideoCodec(
            videoUrl = current.playUrl,
            cachedDashVideos = current.cachedDashVideos
        ) ?: normalizeCodecFamilyKey(_videoCodecPreference.value)
        val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
        val secondaryCodecPreference = appContext?.let { context ->
            com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodecSync(context)
        } ?: _videoSecondCodecPreference.value
        val fallbackCodec = resolveNextVideoCodecFallback(
            failedCodec = failedCodec,
            secondaryCodecPreference = secondaryCodecPreference,
            isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported(),
            isAv1Supported = resolveEffectiveAv1Support(
                deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                sessionBlockedCodecs = sessionBlockedCodecs
            )
        ) ?: run {
            Logger.w(
                "PlayerVM",
                "Codec fallback unavailable: failed=${failedCodec ?: "unknown"}"
            )
            return
        }
        if (failedCodec == AV1_CODEC_KEY) {
            playbackSessionStore.blockVideoCodec(AV1_CODEC_KEY)
        }
        PlaybackCooldownManager.clearForVideo(bvid)
        PlayUrlCache.invalidate(bvid, current.info.cid)
        playbackSessionStore.clearCurrentMedia()
        Logger.w(
            "PlayerVM",
            "Codec fallback: ${failedCodec ?: "unknown"} -> $fallbackCodec, reason=decoder_error"
        )
        loadVideo(
            bvid = bvid,
            aid = current.info.aid,
            force = true,
            autoPlay = resumePlaybackAfterRetry,
            audioLang = current.currentAudioLang,
            videoCodecOverride = fallbackCodec,
            cid = current.info.cid,
            fallbackResumePositionMs = fallbackResumePositionMs
        )
    }
    
    /**
     *  重载视频 - 保持当前播放位置
     * 用于设置面板的"重载视频"功能
     */

    
    /**
     *  切换 CDN 线路
     * 在当前画质下切换到下一个 CDN
     */
    fun switchCdn() {
        playbackRecoveryGeneration++
        cancelPlaybackStallRecovery()
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return

        if (playbackCdnFallbackState.usesCdnRewrite) {
            fallbackFromCdnRewrite(reason = "player_error")
            return
        }
        
        if (current.cdnCount <= 1) {
            viewModelScope.launch { toast("没有其他可用线路") }
            return
        }
        
        // 计算下一个 CDN 索引（循环）
        val nextIndex = (current.currentCdnIndex + 1) % current.cdnCount
        val nextVideoUrl = current.allVideoUrls.getOrNull(nextIndex) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(nextIndex)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val playWhenReadyAfterSwitch = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换线路: ${current.currentCdnIndex + 1} → ${nextIndex + 1}")
            
            // 使用新的 URL 播放
            playResolvedPlayback(
                videoUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                startPositionMs = currentPos,
                playWhenReady = playWhenReadyAfterSwitch
            )
            
            // 更新状态
            _uiState.value = current.copy(
                playUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                currentCdnIndex = nextIndex
            )
            
            toast("已切换到线路${nextIndex + 1}")
        }
    }
    
    /**
     *  切换到指定 CDN 线路
     */
    fun switchCdnTo(index: Int) {
        cancelPlaybackStallRecovery()
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        
        if (index < 0 || index >= current.cdnCount) return
        if (index == current.currentCdnIndex) {
            viewModelScope.launch { toast("已是当前线路") }
            return
        }
        
        val nextVideoUrl = current.allVideoUrls.getOrNull(index) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(index)
        playbackRecoveryGeneration++
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val playWhenReadyAfterSwitch = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换到线路: ${index + 1}")
            
            playResolvedPlayback(
                videoUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                startPositionMs = currentPos,
                playWhenReady = playWhenReadyAfterSwitch
            )
            
            _uiState.value = current.copy(
                playUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                currentCdnIndex = index
            )
            
            toast("已切换到线路${index + 1}")
        }
    }
    
    // ========== State Restoration ==========
    
    /**
     * [修复] 从缓存恢复 UI 状态，避免在返回前台时重复请求网络导致错误
     */
    fun restoreUiState(state: VideoPlaybackUiState.Success) {
        // 只有当前是非成功状态，或者虽然是成功状态但 BVID 不同时，才允许恢复
        // 这样可以避免覆盖当前可能更新的状态
        if (_uiState.value !is VideoPlaybackUiState.Success || 
            (_uiState.value as? VideoPlaybackUiState.Success)?.info?.bvid != state.info.bvid) {
            
            Logger.d("PlayerVM", "♻️ Restoring UI state from cache: ${state.info.title}")
            _uiState.value = state
            currentBvid = state.info.bvid
            currentCid = state.info.cid
            
            // 恢复播放器引用
            // 注意：restoreUiState 通常伴随着 setVideoInfo/MiniPlayerManager 的恢复
            // 这里主要负责 UI 数据的恢复
            
            // 重新绑定监听器等（如果是全新的 ViewModel）
            // ...
        } else {
            Logger.d("PlayerVM", "♻️ Skipping state restoration, already has valid state")
        }
        (_uiState.value as? VideoPlaybackUiState.Success)?.let(::restoreSponsorPlaybackState)
    }

    // ========== Interaction ==========
    
    fun toggleFollow() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleFollow(current.info.owner.mid, current.isFollowing)
                .onSuccess {
                    _uiState.update { state ->
                        if (state is VideoPlaybackUiState.Success) {
                            val newSet = state.followingMids.toMutableSet()
                            if (it) newSet.add(state.info.owner.mid) else newSet.remove(state.info.owner.mid)
                            state.copy(isFollowing = it, followingMids = newSet)
                        } else {
                            state
                        }
                    }
                    toast(if (it) "关注成功" else "已取消关注")
                    if (it) {
                        showFollowGroupDialogForUser(current.info.owner.mid)
                    }
                }
                .onFailure { toast(it.message ?: "\u64cd\u4f5c\u5931\u8d25") }
        }
    }

    fun toggleFollow(mid: Long, currentlyFollowing: Boolean) {
        viewModelScope.launch {
            interactionUseCase.toggleFollow(mid, currentlyFollowing)
                .onSuccess { isFollowing ->
                    // 更新全局关注列表 cache
                    _uiState.update { state ->
                        if (state is VideoPlaybackUiState.Success) {
                            val newSet = state.followingMids.toMutableSet()
                            if (isFollowing) newSet.add(mid) else newSet.remove(mid)
                            
                            // 如果是当前播放视频的作者，同步更新 isFollowing 状态
                            val newIsFollowing = if (state.info.owner.mid == mid) isFollowing else state.isFollowing
                            
                            state.copy(followingMids = newSet, isFollowing = newIsFollowing)
                        } else state
                    }
                    toast(if (isFollowing) "关注成功" else "已取消关注")
                    if (isFollowing) {
                        showFollowGroupDialogForUser(mid)
                    }
                }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }
    
    fun toggleFavorite() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val targetAid = current.info.aid
        val currentlyFavorited = current.isFavorited
        viewModelScope.launch {
            interactionUseCase.toggleFavorite(
                aid = targetAid,
                currentlyFavorited = currentlyFavorited,
                bvid = current.info.bvid
            ).onSuccess { favorited ->
                _uiState.update { state ->
                    if (state is VideoPlaybackUiState.Success) {
                        val updatedFavoriteCount = (state.info.stat.favorite + if (favorited) 1 else -1)
                            .coerceAtLeast(0)
                        state.copy(
                            isFavorited = favorited,
                            info = state.info.copy(
                                stat = state.info.stat.copy(favorite = updatedFavoriteCount)
                            )
                        )
                    } else {
                        state
                    }
                }
                // 收藏状态已变化，清空缓存，确保下次打开收藏夹弹窗时拉取最新远端选中状态。
                favoriteFoldersBoundAid = null
                _favoriteFolders.value = emptyList()
                if (!favorited) {
                    lastSavedFavoriteFolderIds = emptySet()
                    _favoriteSelectedFolderIds.value = emptySet()
                }
                favoriteFolderSaveEventVersion += 1L
                _favoriteFolderSaveEvent.value = FavoriteFolderSaveEvent(
                    aid = targetAid,
                    isFavorited = favorited,
                    version = favoriteFolderSaveEventVersion
                )
                toast(if (favorited) "已收藏" else "已取消收藏")
            }.onFailure { e ->
                toast(e.message ?: "收藏操作失败")
            }
        }
    }
    
    fun toggleLike() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        toggleLikeForVideo(
            aid = current.info.aid,
            bvid = current.info.bvid,
            currentlyLiked = current.isLiked
        )
    }

    fun toggleLikeForVideo(
        aid: Long,
        bvid: String,
        currentlyLiked: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        if (aid <= 0L || bvid.isBlank()) return
        viewModelScope.launch {
            interactionUseCase.toggleLike(aid, currentlyLiked, bvid)
                .onSuccess { liked ->
                    val current = _uiState.value as? VideoPlaybackUiState.Success
                    if (current != null && current.info.aid == aid && current.info.bvid == bvid) {
                        val newStat = current.info.stat.copy(
                            like = (current.info.stat.like + if (liked) 1 else -1).coerceAtLeast(0)
                        )
                        _uiState.value = current.copy(
                            info = current.info.copy(stat = newStat),
                            isLiked = liked
                        )
                    }
                    onResult?.invoke(liked)
                    //  彩蛋：使用趣味消息（如果设置开启）
                    val message = if (liked && appContext?.let { ctx -> com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(ctx) } == true) {
                        com.android.purebilibili.core.util.EasterEggs.getLikeMessage()
                    } else {
                        if (liked) "已点赞" else "已取消点赞"
                    }
                    toast(message)
                }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }

    fun markVideoNotInterested() {
        val current = _uiState.value as? VideoPlaybackUiState.Success
        if (current == null) {
            toast("视频未加载")
            return
        }
        val context = appContext
        if (context == null) {
            toast("暂时无法记录反馈")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val oldSnapshot = TodayWatchFeedbackStore.getSnapshot(context)
            val mergedKeywords = oldSnapshot.dislikedKeywords + extractDislikeKeywords(current.info.title)
            val snapshot = TodayWatchFeedbackSnapshot(
                dislikedBvids = oldSnapshot.dislikedBvids + current.info.bvid,
                dislikedCreatorMids = oldSnapshot.dislikedCreatorMids + current.info.owner.mid,
                dislikedKeywords = mergedKeywords
            )
            TodayWatchFeedbackStore.saveSnapshot(context, snapshot)
            Logger.d(
                "VideoPlaybackViewModel",
                "Recorded not interested feedback: bvid=${current.info.bvid}, mid=${current.info.owner.mid}"
            )
            withContext(Dispatchers.Main.immediate) {
                toast("已减少此类推荐")
            }
        }
    }

    private fun extractDislikeKeywords(title: String): Set<String> {
        if (title.isBlank()) return emptySet()
        val normalized = title.lowercase()
        val stopWords = setOf("视频", "合集", "最新", "一个", "我们", "你们", "今天", "真的", "这个")
        val zhTokens = Regex("[\\u4e00-\\u9fa5]{2,6}")
            .findAll(normalized)
            .map { it.value }
            .filter { it !in stopWords }
            .take(6)
            .toList()
        val enTokens = Regex("[a-z0-9]{3,}")
            .findAll(normalized)
            .map { it.value }
            .take(4)
            .toList()
        return (zhTokens + enTokens).toSet()
    }

    // ========== 评论发送对话框 ==========
    
    private val _showCommentDialog = MutableStateFlow(false)
    val showCommentDialog = _showCommentDialog.asStateFlow()
    private val _composerDrafts = MutableStateFlow(VideoComposerDraftState())
    val composerDrafts = _composerDrafts.asStateFlow()

    private fun ensureComposerDraftVideo() {
        val videoId = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.bvid.orEmpty()
        _composerDrafts.update { it.forVideo(videoId) }
    }

    // 表情包数据
    private val _emotePackages = MutableStateFlow<List<com.android.purebilibili.data.model.response.EmotePackage>>(emptyList())
    val emotePackages = _emotePackages.asStateFlow()
    private var isEmotesLoaded = false

    private fun loadEmotes() {
        if (isEmotesLoaded) return
        viewModelScope.launch {
            com.android.purebilibili.data.repository.CommentRepository.getEmotePackages()
                .onSuccess { 
                    _emotePackages.value = it 
                    isEmotesLoaded = true
                    android.util.Log.d("VideoPlaybackViewModel", "📦 Emotes loaded: ${it.size} packages")
                }
                .onFailure { Logger.e("VideoPlaybackViewModel", "Failed to load emotes", it) }
        }
    }
    
    fun showCommentInputDialog() {
        android.util.Log.d("VideoPlaybackViewModel", "📝 showCommentInputDialog called")
        ensureComposerDraftVideo()
        _composerDrafts.update { it.copy(comments = it.comments.open()) }
        _showCommentDialog.value = true
        // 懒加载表情包
        loadEmotes()
    }

    fun openRootCommentComposer() {
        clearReplyingTo()
        showCommentInputDialog()
    }
    
    fun hideCommentInputDialog() {
        _showCommentDialog.value = false
        clearReplyingTo()
        clearCommentMentionSearch()
    }

    // ========== 弹幕发送 ==========
    
    private val _showDanmakuDialog = MutableStateFlow(false)
    /** 发弹幕面板打开前是否正在播放，用于关闭后恢复 */
    private var wasPlayingBeforeDanmakuComposer = false
    val showDanmakuDialog = _showDanmakuDialog.asStateFlow()
    
    private val _isSendingDanmaku = MutableStateFlow(false)
    val isSendingDanmaku = _isSendingDanmaku.asStateFlow()
    
    fun showDanmakuSendDialog(): Boolean {
        val current = _uiState.value as? VideoPlaybackUiState.Success
        if (current?.isLoggedIn != true) {
            viewModelScope.launch {
                toast(
                    com.android.purebilibili.feature.video.ui.components
                        .resolveDanmakuComposerLoginBlockedMessage()
                )
            }
            return false
        }
        if (com.android.purebilibili.data.repository.DanmakuRepository
                .isDanmakuServerDisabled(currentCid)
        ) {
            viewModelScope.launch { toast("UP主已关闭弹幕") }
            return false
        }
        // 对齐 PiliPlus：发弹幕时暂停播放，关闭后恢复
        wasPlayingBeforeDanmakuComposer = exoPlayer?.isPlaying == true
        if (wasPlayingBeforeDanmakuComposer) {
            exoPlayer?.pause()
        }
        ensureComposerDraftVideo()
        _showDanmakuDialog.value = true
        return true
    }
    
    fun hideDanmakuSendDialog() {
        _showDanmakuDialog.value = false
        if (wasPlayingBeforeDanmakuComposer) {
            wasPlayingBeforeDanmakuComposer = false
            exoPlayer?.play()
        }
    }

    fun updateDanmakuDraft(text: String, attentionCommand: Boolean) {
        ensureComposerDraftVideo()
        _composerDrafts.update {
            it.copy(danmaku = DanmakuComposerDraft(text, attentionCommand))
        }
    }

    fun updateCommentDraft(
        videoId: String,
        sessionId: Long,
        replyRpid: Long?,
        text: String,
        imageUris: List<Uri>,
        syncToDynamic: Boolean
    ) {
        val currentVideoId = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.bvid.orEmpty()
        if (videoId.isBlank() || (currentVideoId.isNotBlank() && currentVideoId != videoId)) return
        ensureComposerDraftVideo()
        _composerDrafts.update { state ->
            if (
                state.videoId != videoId ||
                state.comments.sessionId != sessionId ||
                sessionId <= state.comments.completedSessionId
            ) state
            else state.copy(
                comments = state.comments.update(
                    sessionId,
                    replyRpid,
                    CommentComposerDraft(text, imageUris, syncToDynamic)
                )
            )
        }
    }
    
    /**
     * 发送弹幕
     * 
     * @param message 弹幕内容
     * @param color 颜色 (十进制 RGB)
     * @param mode 模式: 1=滚动, 4=底部, 5=顶部
     * @param fontSize 字号: 18=小, 25=中, 36=大
     */
    fun sendDanmaku(
        message: String,
        color: Int = 16777215,
        mode: Int = 1,
        fontSize: Int = 25,
        attentionCommand: Boolean = false
    ) {
        if (_isSendingDanmaku.value) return
        val requestCid = currentCid
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: run {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        
        if (currentCid == 0L) {
            viewModelScope.launch { toast("视频未加载") }
            return
        }

        if (com.android.purebilibili.data.repository.DanmakuRepository
                .isDanmakuServerDisabled(currentCid)
        ) {
            viewModelScope.launch { toast("UP主已关闭弹幕") }
            return
        }
        
        val progress = exoPlayer?.currentPosition ?: 0L
        val isVipGradualColor =
            color == com.android.purebilibili.feature.video.ui.components.DANMAKU_SEND_VIP_GRADUAL_COLOR
        val actualColor = if (isVipGradualColor) 16777215 else color
        
        _isSendingDanmaku.value = true
        viewModelScope.launch {
            try {
                val result = if (attentionCommand) {
                    com.android.purebilibili.data.repository.DanmakuRepository
                        .sendAttentionCommandDanmaku(
                            aid = current.info.aid,
                            cid = requestCid,
                            progress = progress
                        )
                        .map { Unit }
                } else {
                    com.android.purebilibili.data.repository.DanmakuRepository
                        .sendDanmaku(
                            aid = current.info.aid,
                            cid = requestCid,
                            message = message,
                            progress = progress,
                            color = actualColor,
                            fontSize = fontSize,
                            mode = mode,
                            colorful = isVipGradualColor,
                            upIdentity = false
                        )
                        .map { Unit }
                }
                result
                    .onSuccess {
                        if (currentCid != requestCid) return@onSuccess
                        toast("发送成功")
                        hideDanmakuSendDialog()
                        _composerDrafts.update {
                            it.copy(danmaku = DanmakuComposerDraft())
                        }
                    
                        // 本地即时显示弹幕
                        // 注意：这需要在 Composable 中通过 DanmakuManager 调用
                        // 这里只发送事件通知
                        if (!attentionCommand) {
                            _danmakuSentEvent.trySend(DanmakuSentData(message, actualColor, mode, fontSize, isVipGradualColor))
                        }
                    }
                    .onFailure { error ->
                        toast(error.message ?: "发送失败")
                    }
            
            } finally {
                _isSendingDanmaku.value = false
            }
        }
    }
    
    // 弹幕发送成功事件（用于本地显示）
    data class DanmakuSentData(
        val text: String,
        val color: Int,
        val mode: Int,
        val fontSize: Int,
        val isVipGradualColor: Boolean = false,
    )
    private val _danmakuSentEvent = Channel<DanmakuSentData>(
        capacity = resolvePlayerTransientEventChannelCapacity()
    )
    val danmakuSentEvent = _danmakuSentEvent.receiveAsFlow()
    
    // ========== 弹幕上下文菜单 ==========
    data class DanmakuMenuState(
        val visible: Boolean = false,
        val text: String = "",
        val dmid: Long = 0,
        val userHash: String = "", // 发送者标识 (可能不是纯数字 UID)
        val isSelf: Boolean = false, // 是否是自己发送的
        val voteCount: Int = 0,
        val hasLiked: Boolean = false,
        val voteLoading: Boolean = false,
        val canVote: Boolean = false
    )
    
    private val _danmakuMenuState = MutableStateFlow(DanmakuMenuState())
    val danmakuMenuState = _danmakuMenuState.asStateFlow()
    
    fun showDanmakuMenu(dmid: Long, text: String, userHash: String = "", isSelf: Boolean = false) {
        val supportsVote = dmid > 0L && currentCid > 0L
        danmakuThumbupJob?.cancel()
        val cachedVote = danmakuThumbupStates[currentCid to dmid]
        _danmakuMenuState.value = DanmakuMenuState(
            visible = true,
            text = text,
            dmid = dmid,
            userHash = userHash,
            isSelf = isSelf,
            voteCount = cachedVote?.likes ?: 0,
            hasLiked = cachedVote?.liked ?: (dmid in _likedDanmakuIds.value),
            voteLoading = (currentCid to dmid) in pendingDanmakuLikes,
            canVote = supportsVote
        )
        if (supportsVote && (currentCid to dmid) !in pendingDanmakuLikes) {
            refreshDanmakuThumbupState(dmid)
        }
        // 暂停播放 (可选，防止弹幕飘走)
        // if (exoPlayer?.isPlaying == true) exoPlayer?.pause()
    }
    
    fun hideDanmakuMenu() {
        _danmakuMenuState.value = _danmakuMenuState.value.copy(visible = false)
        // 恢复播放?
    }

    /** 已点赞弹幕的会话级集合，供点按菜单与弹幕列表共享点赞状态 */
    private var danmakuThumbupJob: Job? = null
    private val danmakuThumbupStates = mutableMapOf<Pair<Long, Long>, DanmakuThumbupState>()
    private val pendingDanmakuLikes = mutableSetOf<Pair<Long, Long>>()
    private val _likedDanmakuIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedDanmakuIds = _likedDanmakuIds.asStateFlow()

    private fun refreshDanmakuThumbupState(dmid: Long) {
        if (dmid <= 0L || currentCid <= 0L) return

        val requestCid = currentCid
        danmakuThumbupJob?.cancel()
        danmakuThumbupJob = viewModelScope.launch {
            com.android.purebilibili.data.repository.DanmakuRepository
                .getDanmakuThumbupState(cid = requestCid, dmid = dmid)
                .onSuccess { thumbupState ->
                    if (currentCid != requestCid) return@onSuccess
                    danmakuThumbupStates[requestCid to dmid] = thumbupState
                    _likedDanmakuIds.update { if (thumbupState.liked) it + dmid else it - dmid }
                    _danmakuMenuState.update { current ->
                        if (!current.visible || current.dmid != dmid) current
                        else current.copy(
                            voteCount = thumbupState.likes,
                            hasLiked = thumbupState.liked,
                            voteLoading = false,
                            canVote = true
                        )
                    }
                }
                // 查询仅补充票数和已赞状态，失败不能判定该弹幕不支持点赞。
                .onFailure {
                    if (currentCid != requestCid) return@onFailure
                    _danmakuMenuState.update { current ->
                        if (!current.visible || current.dmid != dmid) current
                        else current.copy(voteLoading = false, canVote = true)
                    }
                }
        }
    }

    /**
     * 撤回弹幕
     * 仅能撤回自己 2 分钟内的弹幕，每天 3 次机会
     * 
     * @param dmid 弹幕 ID
     */
    fun recallDanmaku(dmid: Long) {
        if (currentCid == 0L) {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        
        viewModelScope.launch {
            com.android.purebilibili.data.repository.DanmakuRepository
                .recallDanmaku(cid = currentCid, dmid = dmid)
                .onSuccess { message ->
                    toast(message.ifEmpty { "撤回成功" })
                }
                .onFailure { error ->
                    toast(error.message ?: "撤回失败")
                }
        }
    }

    /**
     * 点赞弹幕
     * 
     * @param dmid 弹幕 ID
     * @param like true=点赞, false=取消点赞
     */
    fun likeDanmaku(dmid: Long, like: Boolean = true) {
        if (currentCid == 0L) {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        if (dmid <= 0L) {
            viewModelScope.launch { toast("当前弹幕不支持点赞") }
            return
        }

        val requestCid = currentCid
        val requestKey = requestCid to dmid
        if (!pendingDanmakuLikes.add(requestKey)) return
        // 停止旧状态查询，避免请求返回后覆盖这次点赞的结果。
        danmakuThumbupJob?.cancel()
        val wasLiked = dmid in _likedDanmakuIds.value
        _likedDanmakuIds.update { if (like) it + dmid else it - dmid }
        _danmakuMenuState.update { current ->
            if (!current.visible || current.dmid != dmid) current
            else current.copy(voteLoading = true)
        }
        
        viewModelScope.launch {
            try {
                com.android.purebilibili.data.repository.DanmakuRepository
                    .likeDanmaku(cid = requestCid, dmid = dmid, like = like)
                    .onSuccess { confirmedLike ->
                        if (currentCid != requestCid) return@onSuccess
                        val cachedVote = danmakuThumbupStates[requestKey]
                        val menu = _danmakuMenuState.value
                        val previousCount = cachedVote?.likes
                            ?: menu.takeIf { it.visible && it.dmid == dmid }?.voteCount ?: 0
                        val previousLike = cachedVote?.liked ?: wasLiked
                        val delta = when {
                            confirmedLike && !previousLike -> 1
                            !confirmedLike && previousLike -> -1
                            else -> 0
                        }
                        val updatedVote = DanmakuThumbupState(
                            likes = (previousCount + delta).coerceAtLeast(0),
                            liked = confirmedLike
                        )
                        danmakuThumbupStates[requestKey] = updatedVote
                        _likedDanmakuIds.update { if (confirmedLike) it + dmid else it - dmid }
                        _danmakuMenuState.update { current ->
                            if (!current.visible || current.dmid != dmid) current
                            else {
                                current.copy(
                                    hasLiked = updatedVote.liked,
                                    voteCount = updatedVote.likes,
                                    voteLoading = false,
                                    canVote = true
                                )
                            }
                        }
                        toast(if (confirmedLike) "已点赞" else "已取消点赞")
                    }
                    .onFailure { error ->
                        if (currentCid != requestCid) return@onFailure
                        _likedDanmakuIds.update { if (wasLiked) it + dmid else it - dmid }
                        _danmakuMenuState.update { current ->
                            if (!current.visible || current.dmid != dmid) current
                            else current.copy(voteLoading = false)
                        }
                        toast(error.message ?: "操作失败")
                    }
            } finally {
                pendingDanmakuLikes.remove(requestKey)
            }
        }
    }

    /**
     * 举报弹幕
     * 
     * @param dmid 弹幕 ID
     * @param reason 举报原因: 1=违法/2=色情/3=广告/4=引战/5=辱骂/6=剧透/7=刷屏/8=其他
     */
    fun reportDanmaku(dmid: Long, reason: Int, content: String = "") {
        if (currentCid == 0L) {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        
        viewModelScope.launch {
            com.android.purebilibili.data.repository.DanmakuRepository
                .reportDanmaku(cid = currentCid, dmid = dmid, reason = reason, content = content)
                .onSuccess {
                    toast("举报成功")
                }
                .onFailure { error ->
                    toast(error.message ?: "举报失败")
                }
        }
    }
    
    // ========== 评论发送 ==========
    
    private val _commentInput = MutableStateFlow("")
    val commentInput = _commentInput.asStateFlow()
    
    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment = _isSendingComment.asStateFlow()
    
    private val _replyingToComment = MutableStateFlow<com.android.purebilibili.data.model.response.ReplyItem?>(null)
    val replyingToComment = _replyingToComment.asStateFlow()

    private val _commentMentionSearchState = MutableStateFlow(CommentMentionSearchUiState())
    val commentMentionSearchState = _commentMentionSearchState.asStateFlow()

    private var commentMentionSearchJob: Job? = null
    
    fun setCommentInput(text: String) {
        _commentInput.value = text
    }
    
    fun setReplyingTo(comment: com.android.purebilibili.data.model.response.ReplyItem?) {
        _replyingToComment.value = comment
    }
    
    fun clearReplyingTo() {
        _replyingToComment.value = null
    }

    fun searchCommentMentionUsers(query: String) {
        if (_commentMentionSearchState.value.query == query && commentMentionSearchJob?.isActive == true) return

        commentMentionSearchJob?.cancel()
        _commentMentionSearchState.update {
            it.copy(query = query, isLoading = true, errorMessage = null)
        }
        commentMentionSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(250L)
            }
            com.android.purebilibili.data.repository.CommentRepository
                .searchMentionUsers(query)
                .onSuccess { users ->
                    _commentMentionSearchState.update {
                        if (it.query == query) {
                            it.copy(users = users, isLoading = false, errorMessage = null)
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    _commentMentionSearchState.update {
                        if (it.query == query) {
                            it.copy(
                                users = emptyList(),
                                isLoading = false,
                                errorMessage = error.message ?: "搜索@好友失败"
                            )
                        } else {
                            it
                        }
                    }
                }
        }
    }

    fun clearCommentMentionSearch() {
        commentMentionSearchJob?.cancel()
        _commentMentionSearchState.value = CommentMentionSearchUiState()
    }
    
    /**
     * 发送评论
     * @param inputMessage 可选直接传入的内容，如果不传则使用 state 中的内容
     */
    fun sendComment(
        inputMessage: String? = null,
        imageUris: List<Uri> = emptyList(),
        syncToDynamic: Boolean = false,
        targetAid: Long? = null
    ) {
        if (_isSendingComment.value) return
        if (inputMessage != null) {
            _commentInput.value = inputMessage
        }
        val current = _uiState.value as? VideoPlaybackUiState.Success
        val sendAid = resolveCommentSendTargetAid(
            requestedAid = targetAid,
            currentAid = current?.info?.aid
        ) ?: return
        val message = _commentInput.value.trim()
        
        if (message.isEmpty() && imageUris.isEmpty()) {
            viewModelScope.launch { toast("请输入评论内容") }
            return
        }

        // Capture before launching. The dialog can be dismissed/recomposed immediately after
        // this call; reading the StateFlow later used to lose the reply target and publish a
        // new root comment instead.
        val replyTo = _replyingToComment.value
        ensureComposerDraftVideo()
        val draftState = _composerDrafts.value
        val sendVideoId = draftState.videoId
        val sendSessionId = draftState.comments.sessionId
        val sentDraft = draftState.comments.drafts[commentComposerDraftKey(replyTo?.rpid)]
        val outgoingMessage = resolveCommentReplyMessage(
            message = message,
            replyName = replyTo?.member?.uname,
            replyRoot = replyTo?.root
        )

        if (!_isSendingComment.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val (root, parent) = resolveCommentReplyTargets(
                    replyRpid = replyTo?.rpid,
                    replyRoot = replyTo?.root
                )
                val picturesResult = uploadCommentPictures(imageUris)
                val pictures = picturesResult.getOrElse { uploadError ->
                    Logger.e(
                        "PlayerVM",
                        "Comment image upload failed: aid=$sendAid, imageCount=${imageUris.size}, message=${uploadError.message}",
                        uploadError
                    )
                    toast(uploadError.message ?: "图片上传失败")
                    return@launch
                }

                com.android.purebilibili.data.repository.CommentRepository
                    .addComment(
                        aid = sendAid,
                        message = outgoingMessage,
                        root = root,
                        parent = parent,
                        pictures = pictures,
                        syncToDynamic = syncToDynamic
                    )
                    .onSuccess { reply ->
                        toast(if (replyTo != null) "回复成功" else "评论成功")
                        val currentVideoId = (_uiState.value as? VideoPlaybackUiState.Success)
                            ?.info?.bvid.orEmpty()
                        val isSameVideo = currentVideoId.isBlank() || currentVideoId == sendVideoId
                        _composerDrafts.update { state ->
                            if (!isSameVideo || state.videoId != sendVideoId) state
                            else state.copy(
                                comments = state.comments.complete(
                                    sendSessionId,
                                    replyTo?.rpid,
                                    sentDraft
                                )
                            )
                        }
                        val activeDraftState = _composerDrafts.value
                        if (
                            isSameVideo &&
                            activeDraftState.videoId == sendVideoId &&
                            activeDraftState.comments.sessionId == sendSessionId
                        ) {
                            _commentInput.value = ""
                            _replyingToComment.value = null
                            _showCommentDialog.value = false
                            clearCommentMentionSearch()
                        }

                        // 通知 UI 刷新评论列表
                        _commentSentEvent.trySend(reply)
                    }
                    .onFailure { error ->
                        Logger.e(
                            "PlayerVM",
                            "Comment send failed: aid=$sendAid, root=$root, parent=$parent, pictureCount=${pictures.size}, message=${error.message}",
                            error
                        )
                        toast(error.message ?: "发送失败")
                    }
            } finally {
                _isSendingComment.value = false
            }
        }
    }

    private suspend fun uploadCommentPictures(imageUris: List<Uri>): Result<List<ReplyPicture>> {
        if (imageUris.isEmpty()) return Result.success(emptyList())
        val context = appContext ?: return Result.failure(Exception("应用上下文不可用"))
        val selectedUris = imageUris.take(9)
        return withContext(Dispatchers.IO) {
            runCatching {
                selectedUris.mapIndexed { index, uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val fileName = queryDisplayName(context, uri)
                        ?: "comment_${System.currentTimeMillis()}_${index + 1}.jpg"

                    // 流式上传:空/15MB 校验在 CommentRepository 内完成,不再整文件读入内存。
                    val uploadResult = com.android.purebilibili.data.repository.CommentRepository
                        .uploadCommentImage(
                            fileName = fileName,
                            mimeType = mimeType,
                            resolver = context.contentResolver,
                            uri = uri
                        )
                    uploadResult.getOrElse { throw it }
                }
            }
        }
    }

    private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
    }
    
    // 评论发送成功事件
    private val _commentSentEvent = Channel<com.android.purebilibili.data.model.response.ReplyItem?>(
        capacity = resolvePlayerTransientEventChannelCapacity()
    )
    val commentSentEvent = _commentSentEvent.receiveAsFlow()

    
    // ========== Settings: Codec & Audio ==========
    
    // ========== Settings: Codec & Audio ==========
    
    // Preferences StateFlows (Initialized in initWithContext)
    private val _videoCodecPreference = MutableStateFlow("hev1")
    val videoCodecPreference = _videoCodecPreference.asStateFlow()

    private val _videoSecondCodecPreference = MutableStateFlow("avc1")
    val videoSecondCodecPreference = _videoSecondCodecPreference.asStateFlow()
    
    private val _audioQualityPreference = MutableStateFlow(-1)
    val audioQualityPreference = _audioQualityPreference.asStateFlow()
    private var premiumAudioFallbackInProgress = false
    
    fun setVideoCodec(codec: String) {
        _videoCodecPreference.value = codec // Optimistic update
        viewModelScope.launch {
            appContext?.let { 
                com.android.purebilibili.core.store.SettingsManager.setVideoCodec(it, codec)
                // Reload to apply changes if playing
                reloadVideo()
            }
        }
    }

    fun setVideoSecondCodec(codec: String) {
        _videoSecondCodecPreference.value = codec // Optimistic update
        viewModelScope.launch {
            appContext?.let {
                com.android.purebilibili.core.store.SettingsManager.setVideoSecondCodec(it, codec)
                reloadVideo()
            }
        }
    }

    fun setAudioQuality(audioQuality: Int) {
        playbackRecoveryGeneration++
        val previousAudioQuality = _audioQualityPreference.value
        _audioQualityPreference.value = audioQuality
        Logger.d("VideoPlaybackViewModel", "🎵 setAudioQuality called with: $audioQuality")
        viewModelScope.launch {
            val current = _uiState.value as? VideoPlaybackUiState.Success
            val player = exoPlayer
            if (current == null || player == null) {
                appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.setAudioQuality(it, audioQuality)
                }
                return@launch
            }

            val switched = refreshPlaybackAudioForSpeedCompatibility(
                current = current,
                audioPreference = audioQuality,
                currentPos = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = player.playWhenReady
            )
            if (!switched) {
                _audioQualityPreference.value = previousAudioQuality
                toast("音质切换失败，请稍后重试")
                return@launch
            }

            appContext?.let {
                com.android.purebilibili.core.store.SettingsManager.setAudioQuality(it, audioQuality)
            }
            val nextState = _uiState.value as? VideoPlaybackUiState.Success
            val selectedLabel = nextState?.availableAudioQualities
                ?.firstOrNull { it.preferenceId == nextState.selectedAudioQuality }
                ?.label
                ?: when (nextState?.selectedAudioQuality) {
                    30250 -> "杜比全景声"
                    30251 -> "Hi-Res 无损"
                    else -> "AAC"
                }
            val message = when (nextState?.audioFallbackReason) {
                AudioFallbackReason.SPEED_INCOMPATIBLE ->
                    "当前倍速暂不支持所选音质，已临时使用 $selectedLabel"
                AudioFallbackReason.REQUESTED_UNAVAILABLE ->
                    "当前视频不支持所选音质，已使用 $selectedLabel"
                AudioFallbackReason.DECODER_ERROR ->
                    "当前设备无法稳定解码所选音质，已临时使用 $selectedLabel"
                AudioFallbackReason.NO_PLAYABLE_AUDIO ->
                    "当前视频没有可用音轨"
                null -> "✓ 已切换至 $selectedLabel"
            }
            toast(message, PlayerToastPresentation.CenteredHighlight)
        }
    }

    internal fun fallbackFromPremiumAudioPlaybackError() {
        if (premiumAudioFallbackInProgress) return

        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val player = exoPlayer ?: return
        val hasStandardAudio = current.availableAudioQualities.any { option ->
            option.preferenceId == AUDIO_QUALITY_AUTO
        }
        if (!hasStandardAudio) {
            player.pause()
            viewModelScope.launch {
                toast("当前设备无法解码该 Hi-Res 音轨，且没有可回退的 AAC 音轨")
            }
            return
        }

        val requestedAudioQuality = current.requestedAudioQuality
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = resolvePlaybackIntentForSourceReplacement(
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying
        )
        premiumAudioFallbackInProgress = true
        playbackCdnFallbackJob?.cancel()
        playbackCdnFallbackJob = null
        playbackCdnFallbackState = PlaybackCdnFallbackState.Inactive

        viewModelScope.launch {
            try {
                val switched = refreshPlaybackAudioForSpeedCompatibility(
                    current = current,
                    audioPreference = AUDIO_QUALITY_AUTO,
                    currentPos = currentPos,
                    playWhenReady = playWhenReady
                )
                val updated = _uiState.value as? VideoPlaybackUiState.Success
                if (switched && updated?.selectedAudioQuality == AUDIO_QUALITY_AUTO) {
                    _uiState.value = updated.copy(
                        requestedAudioQuality = requestedAudioQuality,
                        audioFallbackReason = AudioFallbackReason.DECODER_ERROR
                    )
                    toast(
                        "当前设备无法稳定解码 Hi-Res，已临时切换至 AAC",
                        PlayerToastPresentation.CenteredHighlight
                    )
                } else {
                    player.pause()
                    toast("Hi-Res 解码失败，AAC 回退也未能完成")
                }
            } catch (error: Exception) {
                Logger.w("PlayerVM", "Hi-Res audio fallback failed: ${error.message}")
                player.pause()
                toast("Hi-Res 解码失败，AAC 回退也未能完成")
            } finally {
                premiumAudioFallbackInProgress = false
            }
        }
    }

    //  相互作用
    
    //  稍后再看
    fun toggleWatchLater() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleWatchLater(current.info.aid, current.isInWatchLater, currentBvid)
                .onSuccess { inWatchLater ->
                    _uiState.value = current.copy(isInWatchLater = inWatchLater)
                    toast(if (inWatchLater) "已添加到稍后再看" else "已从稍后再看移除")
                }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }

    /**
     * 首帧优先：播放启动后异步补齐交互态与 VIP 状态，避免阻塞自动播放。
     */
    private fun refreshDeferredPlaybackSignals(
        bvid: String,
        aid: Long,
        ownerMid: Long,
        requestToken: Long
    ) {
        if (currentLoadRequestToken != requestToken) return
        val repository = com.android.purebilibili.data.repository.ActionRepository
        val session = repository.interactionSession()
        val followVersion = repository.followStatusVersion(ownerMid)
        val likeVersion = repository.likeStatusVersion(aid)
        viewModelScope.launch(Dispatchers.IO) {
            launch {
                queryVideoInteractionStatusWithRetry(
                    isCurrent = {
                        currentLoadRequestToken == requestToken && repository.interactionSession() == session &&
                            repository.followStatusVersion(ownerMid) == followVersion
                    },
                    query = { repository.queryFollowStatus(ownerMid) },
                ).onSuccess { following ->
                    withContext(Dispatchers.Main.immediate) {
                        if (currentLoadRequestToken != requestToken || repository.interactionSession() != session ||
                            repository.followStatusVersion(ownerMid) != followVersion) return@withContext
                        _uiState.update { state ->
                            val success = state as? VideoPlaybackUiState.Success ?: return@update state
                            if (success.info.bvid != bvid || success.info.aid != aid || success.info.owner.mid != ownerMid) return@update state
                            success.copy(isFollowing = following, followStatusKnown = true,
                                followingMids = if (following) success.followingMids + ownerMid else success.followingMids - ownerMid)
                        }
                    }
                }
            }
            launch {
                queryVideoInteractionStatusWithRetry(
                    isCurrent = {
                        currentLoadRequestToken == requestToken && repository.interactionSession() == session &&
                            repository.likeStatusVersion(aid) == likeVersion
                    },
                    query = { repository.queryLikeStatus(aid) },
                ).onSuccess { liked ->
                    withContext(Dispatchers.Main.immediate) {
                        if (currentLoadRequestToken != requestToken || repository.interactionSession() != session ||
                            repository.likeStatusVersion(aid) != likeVersion) return@withContext
                        _uiState.update { state ->
                            val success = state as? VideoPlaybackUiState.Success ?: return@update state
                            if (success.info.bvid != bvid || success.info.aid != aid || success.info.owner.mid != ownerMid) state else success.copy(isLiked = liked, likeStatusKnown = true)
                        }
                    }
                }
            }
            val favoriteDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkFavoriteStatus(aid) }
            val watchLaterDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkWatchLaterStatus(aid) }
            val dislikeDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkDislikeStatus(aid) }
            val coinDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkCoinStatus(aid) }
            val vipDeferred = async {
                if (com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip()) {
                    true
                } else {
                    com.android.purebilibili.data.repository.VideoRepository.getPlaybackNavInfo()
                        .getOrNull()
                        ?.vip
                        ?.status == 1
                }
            }

            val fetchedFavorite = favoriteDeferred.await()
            val fetchedWatchLater = watchLaterDeferred.await()
            val fetchedDislike = dislikeDeferred.await()
            val fetchedCoinCount = coinDeferred.await()
            val fetchedVip = vipDeferred.await()

            if (fetchedVip && !com.android.purebilibili.data.repository.VideoRepository.isUsingDedicatedPlaybackAccount()) {
                com.android.purebilibili.core.store.TokenManager.isVipCache = true
            }

            withContext(Dispatchers.Main.immediate) {
                _uiState.update { state ->
                    val success = state as? VideoPlaybackUiState.Success ?: return@update state
                    if (success.info.bvid != bvid || success.info.aid != aid || success.info.owner.mid != ownerMid) return@update state

                    if (currentLoadRequestToken != requestToken || repository.interactionSession() != session) return@update state

                    success.copy(
                        isVip = success.isVip || fetchedVip,
                        isFavorited = success.isFavorited || fetchedFavorite,
                        isInWatchLater = success.isInWatchLater || fetchedWatchLater,
                        isDisliked = success.isDisliked || fetchedDislike,
                        coinCount = maxOf(success.coinCount, fetchedCoinCount)
                    )
                }
            }
        }
    }
    
    /**
     *  [新增] 检查特定用户的关注状态
     *  解决 loadFollowingMids 分页限制导致的状态不准问题
     */
    fun ensureFollowStatus(mid: Long, force: Boolean = false) {
        if (mid == 0L) return

        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()) return
        if (!force && mid == current.info.owner.mid && current.followStatusKnown) return

        synchronized(followStatusCheckInFlight) {
            if (!force && followStatusCheckInFlight.contains(mid)) return
            followStatusCheckInFlight.add(mid)
        }

        val repository = com.android.purebilibili.data.repository.ActionRepository
        val requestToken = currentLoadRequestToken
        val session = repository.interactionSession()
        val version = repository.followStatusVersion(mid)
        val bvid = current.info.bvid
        viewModelScope.launch {
            try {
                repository.queryFollowStatus(mid).onSuccess { following ->
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (currentLoadRequestToken != requestToken || success.info.bvid != bvid ||
                            success.info.aid != current.info.aid || success.info.owner.mid != current.info.owner.mid ||
                            repository.interactionSession() != session ||
                            repository.followStatusVersion(mid) != version) return@update state
                        val isOwner = success.info.owner.mid == mid
                        success.copy(
                            followingMids = if (following) success.followingMids + mid else success.followingMids - mid,
                            isFollowing = if (isOwner) following else success.isFollowing,
                            followStatusKnown = if (isOwner) true else success.followStatusKnown
                        )
                    }
                }.onFailure { error ->
                    Logger.e("PlayerVM", "Failed to check relation for mid=$mid", error)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                synchronized(followStatusCheckInFlight) {
                    followStatusCheckInFlight.remove(mid)
                }
            }
        }
    }

    //  异步加载关注列表（用于推荐视频的已关注标签）
    private fun loadFollowingMids() {
        val repository = com.android.purebilibili.data.repository.ActionRepository
        val loginMid = com.android.purebilibili.core.store.TokenManager.midCache ?: return
        if (com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()) return
        val session = repository.interactionSession()
        val requestToken = currentLoadRequestToken
        val request = session to requestToken
        val followingRevision = repository.followingStatusRevision(session) ?: return
        if (followingMidsRequest == request) return
        val now = System.currentTimeMillis()
        val cacheValid = hasFollowingCache && cachedFollowingSession == session &&
            cachedFollowingRevision == followingRevision &&
            cachedFollowingOwnerMid == loginMid &&
            (now - cachedFollowingLoadedAtMs) in 0 until followingMidsCacheTtlMs

        fun applyMids(mids: Set<Long>) {
            repository.acceptFollowingSnapshot(session, followingRevision) {
                _uiState.update { state ->
                    if (state !is VideoPlaybackUiState.Success || currentLoadRequestToken != requestToken ||
                        repository.interactionSession() != session) state
                    else state.copy(followingMids = repository.mergeKnownFollowingMids(session, mids))
                }
            }
        }
        if (cacheValid) {
            applyMids(cachedFollowingMids)
            return
        }

        followingMidsRequest = request
        isFollowingMidsLoading = true
        viewModelScope.launch {
            try {
                val allMids = mutableSetOf<Long>()
                val pageSize = 50
                for (page in 1..4) {
                    if (currentLoadRequestToken != requestToken || repository.interactionSession() != session ||
                        repository.followingStatusRevision(session) != followingRevision) return@launch
                    val result = com.android.purebilibili.core.network.NetworkModule.api.getFollowings(loginMid, page, pageSize)
                    if (result.code != 0 || result.data == null) return@launch
                    val list = requireNotNull(result.data).list.orEmpty()
                    allMids.addAll(list.map { it.mid })
                    if (list.size < pageSize) break
                }
                if (currentLoadRequestToken != requestToken || repository.interactionSession() != session ||
                    repository.followingStatusRevision(session) != followingRevision) return@launch
                repository.acceptFollowingSnapshot(session, followingRevision) {
                    if (currentLoadRequestToken == requestToken && repository.interactionSession() == session) {
                        cachedFollowingOwnerMid = loginMid
                        cachedFollowingRevision = followingRevision
                        cachedFollowingSession = session
                        cachedFollowingMids = allMids.toSet()
                        cachedFollowingLoadedAtMs = System.currentTimeMillis()
                        hasFollowingCache = true
                        applyMids(allMids)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.d("PlayerVM", " Failed to load following mids: ${e.message}")
            } finally {
                if (followingMidsRequest == request) {
                    followingMidsRequest = null
                    isFollowingMidsLoading = false
                }
            }
        }
    }
    
    //  异步加载视频标签
    /**
     *  保存封面到相册
     */
    fun saveCover(context: android.content.Context) {
        val current = _uiState.value as? VideoPlaybackUiState.Success
        val coverUrl = current?.info?.pic ?: return
        val title = current.info.title
        
        viewModelScope.launch {
            val success = com.android.purebilibili.feature.download.DownloadManager.saveImageToGallery(context, coverUrl, title)
            if (success) toast("封面已保存到相册")
            else toast("保存失败")
        }
    }

    /**
     *  下载音频
     */
    fun downloadAudio(context: android.content.Context) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: run {
            toast("无法获取视频信息")
            return
        }
        val audioUrl = current.audioUrl
        if (audioUrl.isNullOrEmpty()) {
            toast("无法获取音频地址")
            return
        }
        
        val task = com.android.purebilibili.feature.download.DownloadTask(
            bvid = current.info.bvid,
            cid = current.info.cid,
            title = current.info.title,
            cover = current.info.pic,
            ownerName = current.info.owner.name,
            ownerFace = current.info.owner.face,
            duration = exoPlayer?.duration?.toInt()?.div(1000) ?: 0,
            quality = 0,
            qualityDesc = "音频",
            videoUrl = "",
            audioUrl = audioUrl,
            isAudioOnly = true,
            isVerticalVideo = false
        )
        
        enqueueAudioDownload(task)
    }

    internal fun enqueueAudioDownload(task: com.android.purebilibili.feature.download.DownloadTask) {
        viewModelScope.launch {
            val started = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
            if (started) {
                toast("已开始下载音频")
            } else {
                toast("该任务已在下载中或已完成")
            }
        }
    }

    private fun loadOwnerStats(
        bvid: String,
        ownerMid: Long
    ) {
        if (ownerMid <= 0L) return
        val requestToken = currentLoadRequestToken
        viewModelScope.launch {
            VideoRepository.getCreatorCardStats(ownerMid).onSuccess { stats ->
                _uiState.update { current ->
                    if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid &&
                        current.info.owner.mid == ownerMid && currentLoadRequestToken == requestToken) {
                        current.copy(
                            ownerFollowerCount = stats.followerCount,
                            ownerVideoCount = stats.videoCount
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun loadVideoTags(bvid: String) {
        viewModelScope.launch {
            try {
                val response = com.android.purebilibili.core.network.NetworkModule.api.getVideoTags(bvid)
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    _uiState.update { current ->
                        if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                            current.copy(videoTags = checkedResponseData)
                        } else current
                    }
                    MiniPlayerManager.getInstance(getApplication<Application>()).updateCachedVideoTags(bvid, checkedResponseData)
                    Logger.d("PlayerVM", "🏷️ Loaded ${checkedResponseData.size} video tags")
                }
            } catch (e: Exception) {
                Logger.d("PlayerVM", " Failed to load video tags: ${e.message}")
            }
        }
    }
    
    // 🖼️ 异步加载视频预览图数据（用于进度条拖动预览）
    private fun loadVideoshot(bvid: String, cid: Long) {
        viewModelScope.launch {
            try {
                val videoshotData = VideoRepository.getVideoshot(bvid, cid)
                if (videoshotData != null && videoshotData.isValid) {
                    _uiState.update { current ->
                        if (current is VideoPlaybackUiState.Success &&
                            shouldApplyVideoshotResult(
                                currentState = current,
                                videoshotBvid = bvid,
                                videoshotCid = cid
                            )
                        ) {
                            current.copy(videoshotData = videoshotData)
                        } else current
                    }
                    Logger.d("PlayerVM", "🖼️ Loaded videoshot: ${videoshotData.image.size} images, ${videoshotData.index.size} frames")
                }
            } catch (e: Exception) {
                Logger.d("PlayerVM", "🖼️ Failed to load videoshot: ${e.message}")
            }
        }
    }
    
    // 👀 [新增] 在线观看人数定时刷新 Job
    private var onlineCountJob: Job? = null
    private var playbackTransitionMonitorJob: Job? = null
    
    // 👀 [新增] 获取并更新在线观看人数
    private fun startOnlineCountPolling(bvid: String, cid: Long) {
        // 取消之前的轮询
        onlineCountJob?.cancel()
        
        onlineCountJob = viewModelScope.launch {
            while (true) {
                try {
                    val context = appContext
                    val enabled = context?.let {
                        com.android.purebilibili.core.store.SettingsManager
                            .getShowOnlineCountSync(it)
                    } ?: false
                    if (!enabled) {
                        _uiState.update { current ->
                            if (current is VideoPlaybackUiState.Success) {
                                current.copy(onlineCount = "")
                            } else current
                        }
                        break
                    }
                    if (shouldRefreshOnlineCount(
                            showOnlineCountEnabled = enabled,
                            isInBackground = BackgroundManager.isInBackground,
                            currentBvid = currentBvid,
                            currentCid = currentCid
                        )
                    ) {
                        val response = com.android.purebilibili.core.network.NetworkModule.api.getOnlineCount(bvid, cid)
                        if (response.code == 0 && response.data != null) {
                            val checkedResponseData = requireNotNull(response.data)
                            val onlineText = "${checkedResponseData.total}人正在看"
                            _uiState.update { current ->
                                if (current is VideoPlaybackUiState.Success) {
                                    current.copy(onlineCount = onlineText)
                                } else current
                            }
                            Logger.d("PlayerVM", "👀 Online count: ${checkedResponseData.total}")
                        }
                    }
                } catch (e: Exception) {
                    Logger.d("PlayerVM", "👀 Failed to fetch online count: ${e.message}")
                }
                delay(resolveOnlineCountPollingDelayMs(isInBackground = BackgroundManager.isInBackground))
            }
        }
    }
    
    //  [新增] 异步加载播放器额外信息 (章节/看点 + BGM + 互动剧情图)
    /**
     * 官方 pbp 热度接口失效（404/空数据）时的兜底：用全部分段弹幕本地聚合密度曲线。
     * 仅在进度条热度曲线开关开启时执行；同一 cid 只构建一次。
     */
    private fun loadDanmakuDensityFallback(
        bvid: String,
        cid: Long,
        requestToken: Long,
    ) {
        if (densityFallbackLoadedCid == cid) return
        val state = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (state.info.bvid != bvid || state.info.cid != cid) return
        val durationSeconds = state.info.pages.firstOrNull { it.cid == cid }?.duration
            ?: state.info.pages.maxOfOrNull { it.duration } ?: 0L
        if (durationSeconds <= 0L) return
        densityFallbackJob?.cancel()
        densityFallbackJob = viewModelScope.launch {
            val appContextRef = appContext ?: getApplication<Application>()
            val enabled = SettingsManager.getProgressPeakDanmakuEnabled(appContextRef).first()
            if (!enabled) return@launch
            VideoRepository.getDanmakuDensityProgressData(cid = cid, durationSeconds = durationSeconds)
                .onSuccess { localData ->
                    if (shouldApplyPlayerInfoResult(
                            activeRequestToken = currentLoadRequestToken,
                            resultRequestToken = requestToken,
                            expectedBvid = bvid,
                            expectedCid = cid,
                            currentBvid = currentBvid,
                            currentCid = currentCid
                        )
                    ) {
                        densityFallbackLoadedCid = cid
                        _pbpProgressData.value = localData
                        Logger.d(
                            "PlayerVM",
                            "📈 Loaded local danmaku density: step=${localData.stepSeconds}s points=${localData.values.size}"
                        )
                    }
                }
                .onFailure { error ->
                    Logger.d("PlayerVM", "📈 Local danmaku density failed: ${error.message}")
                }
        }
    }

    private fun loadPlayerInfo(
        bvid: String,
        cid: Long,
        preferredEdgeId: Long? = null,
        requestToken: Long = currentLoadRequestToken
    ) {
        Logger.d(
            "PlayerVM",
            "SUB_DBG loadPlayerInfo start: request=$bvid/$cid, token=$requestToken, current=$currentBvid/$currentCid"
        )
        playerInfoJob?.cancel()
        _pbpProgressData.value = null
        densityFallbackJob?.cancel()
        densityFallbackLoadedCid = 0L
        playerInfoJob = viewModelScope.launch {
            try {
                val result = VideoRepository.getPlayerInfo(bvid, cid)

                result.onSuccess { data ->
                    if (!shouldApplyPlayerInfoResult(
                            activeRequestToken = currentLoadRequestToken,
                            resultRequestToken = requestToken,
                            expectedBvid = bvid,
                            expectedCid = cid,
                            currentBvid = currentBvid,
                            currentCid = currentCid
                        )
                    ) {
                        Logger.d("PlayerVM", "📖 Ignore stale player info by token/context: bvid=$bvid cid=$cid")
                        return@onSuccess
                    }

                    val currentState = _uiState.value as? VideoPlaybackUiState.Success
                    if (currentState == null ||
                        currentState.info.bvid != bvid ||
                        currentState.info.cid != cid
                    ) {
                        Logger.d("PlayerVM", "📖 Ignore stale player info by ui state: bvid=$bvid cid=$cid")
                        return@onSuccess
                    }

                    // 1. 处理章节信息
                    val points = data.viewPoints
                    if (points.isNotEmpty()) {
                        _viewPoints.value = points
                        Logger.d("PlayerVM", "📖 Loaded ${points.size} chapter points")
                    } else {
                        _viewPoints.value = emptyList()
                    }

                    VideoRepository.getPbpProgressData(
                        bvid = bvid,
                        cid = cid,
                        aid = currentState.info.aid
                    ).onSuccess { pbpData ->
                        if (shouldApplyPlayerInfoResult(
                                activeRequestToken = currentLoadRequestToken,
                                resultRequestToken = requestToken,
                                expectedBvid = bvid,
                                expectedCid = cid,
                                currentBvid = currentBvid,
                                currentCid = currentCid
                            ) && pbpData.values.isNotEmpty()
                        ) {
                            _pbpProgressData.value = pbpData
                            Logger.d(
                                "PlayerVM",
                                "📈 Loaded PBP progress: step=${pbpData.stepSeconds}s points=${pbpData.values.size}"
                            )
                        } else if (shouldApplyPlayerInfoResult(
                                activeRequestToken = currentLoadRequestToken,
                                resultRequestToken = requestToken,
                                expectedBvid = bvid,
                                expectedCid = cid,
                                currentBvid = currentBvid,
                                currentCid = currentCid
                            )
                        ) {
                            // 官方 pbp 接口已 404 / 返回空数据：回落到本地弹幕密度聚合
                            loadDanmakuDensityFallback(
                                bvid = bvid,
                                cid = cid,
                                requestToken = requestToken,
                            )
                        }
                    }.onFailure { e ->
                        if (shouldApplyPlayerInfoResult(
                                activeRequestToken = currentLoadRequestToken,
                                resultRequestToken = requestToken,
                                expectedBvid = bvid,
                                expectedCid = cid,
                                currentBvid = currentBvid,
                                currentCid = currentCid
                            )
                        ) {
                            _pbpProgressData.value = null
                            Logger.d("PlayerVM", "📈 PBP unavailable (${e.message}), falling back to local danmaku density")
                            loadDanmakuDensityFallback(
                                bvid = bvid,
                                cid = cid,
                                requestToken = requestToken,
                            )
                        } else {
                            Logger.d("PlayerVM", "📈 Failed to load PBP progress: ${e.message}")
                        }
                    }

                    // 2. 处理 BGM 信息
                    if (data.bgmInfo != null) {
                        val checkedDataBgmInfo = requireNotNull(data.bgmInfo)
                        _uiState.update { current ->
                            if (current is VideoPlaybackUiState.Success) {
                                current.copy(bgmInfo = checkedDataBgmInfo)
                            } else current
                        }
                        Logger.d("PlayerVM", "🎵 Loaded BGM: ${checkedDataBgmInfo.musicTitle}")
                    }

                    // 2b. gRPC BGM list (multi-song support)
                    val grpcAid = currentState.info.aid
                    if (grpcAid > 0) {
                        ViewGrpcRepository.getBgmList(grpcAid, bvid, cid).onSuccess { bgmList ->
                            Logger.w("PlayerVM", "gRPC BGM result: ${bgmList.size} entries for aid=$grpcAid")
                            if (bgmList.isNotEmpty()) {
                                _uiState.update { current ->
                                    if (current is VideoPlaybackUiState.Success) {
                                        current.copy(bgmInfoList = bgmList)
                                    } else current
                                }
                            }
                        }.onFailure { e ->
                            Logger.w("PlayerVM", "gRPC BGM fetch failed: ${e.message}")
                        }
                    }

                    // 3. 字幕信息（优先中文主字幕 + 英文副字幕）
                    if (isSubtitleFeatureEnabledForUser()) {
                        loadSubtitleTracksFromPlayerInfo(
                            bvid = bvid,
                            cid = cid,
                            subtitles = data.subtitle?.subtitles.orEmpty(),
                            preferredPrimaryLanguage = data.subtitle?.lan,
                            requestToken = requestToken
                        )
                    } else {
                        clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
                    }

                    // 4. 互动剧情图
                    interactiveGraphVersion = data.interaction?.graphVersion ?: 0L
                    val current = _uiState.value as? VideoPlaybackUiState.Success
                    val shouldEnableInteractive = current != null &&
                        current.info.bvid == bvid &&
                        current.info.isSteinGate == 1 &&
                        interactiveGraphVersion > 0L
                    if (shouldEnableInteractive) {
                        val edgeId = preferredEdgeId ?: interactiveCurrentEdgeId.takeIf { it > 0L }
                        loadInteractiveEdgeInfo(edgeId = edgeId)
                    } else {
                        clearInteractiveChoiceRuntime()
                    }
                }.onFailure { e ->
                    if (!shouldApplyPlayerInfoResult(
                            activeRequestToken = currentLoadRequestToken,
                            resultRequestToken = requestToken,
                            expectedBvid = bvid,
                            expectedCid = cid,
                            currentBvid = currentBvid,
                            currentCid = currentCid
                        )
                    ) {
                        Logger.d("PlayerVM", "📖 Ignore stale player info failure: bvid=$bvid cid=$cid")
                        return@onFailure
                    }
                    Logger.d("PlayerVM", "📖 Failed to load player info: ${e.message}")
                    Logger.d(
                        "PlayerVM",
                        "SUB_DBG playerInfo failed: bvid=$bvid, cid=$cid, token=$requestToken, err=${e.message}"
                    )
                    _viewPoints.value = emptyList()
                    _pbpProgressData.value = null
                    clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
                }
            } catch (e: Exception) {
                if (!shouldApplyPlayerInfoResult(
                        activeRequestToken = currentLoadRequestToken,
                        resultRequestToken = requestToken,
                        expectedBvid = bvid,
                        expectedCid = cid,
                        currentBvid = currentBvid,
                        currentCid = currentCid
                    )
                ) {
                    Logger.d("PlayerVM", "📖 Ignore stale player info exception: bvid=$bvid cid=$cid")
                    return@launch
                }
                Logger.d("PlayerVM", "📖 Exception loading player info: ${e.message}")
                Logger.d(
                    "PlayerVM",
                    "SUB_DBG playerInfo exception: bvid=$bvid, cid=$cid, token=$requestToken, err=${e.message}"
                )
                _viewPoints.value = emptyList()
                _pbpProgressData.value = null
                clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
            }
        }
    }

    private fun clearSubtitleTracksForCurrentVideo(bvid: String, cid: Long) {
        if (currentBvid == bvid && currentCid == cid) {
            subtitleLoadToken += 1
        }
        _uiState.update { current ->
            if (current is VideoPlaybackUiState.Success &&
                current.info.bvid == bvid &&
                current.info.cid == cid
            ) {
                current.copy(
                    subtitleEnabled = false,
                    subtitleOwnerBvid = null,
                    subtitleOwnerCid = 0L,
                    subtitlePrimaryLanguage = null,
                    subtitleSecondaryLanguage = null,
                    subtitlePrimaryTrackKey = null,
                    subtitleSecondaryTrackKey = null,
                    subtitleTracks = emptyList(),
                    subtitlePrimaryLikelyAi = false,
                    subtitleSecondaryLikelyAi = false,
                    subtitlePrimaryCues = emptyList(),
                    subtitleSecondaryCues = emptyList()
                )
            } else {
                current
            }
        }
    }

    private fun mapSubtitleTracksForPlayback(subtitles: List<SubtitleItem>): List<SubtitleTrackMeta> {
        val tracks = mapPlayerInfoSubtitleTracks(subtitles)
        val dropped = subtitles.size - tracks.size
        if (dropped > 0) {
            Logger.d("PlayerVM", "SUB_DBG ignored $dropped invalid/untrusted subtitle tracks")
        }
        return tracks
    }

    private fun resolveSecondarySubtitleTrack(
        tracks: List<SubtitleTrackMeta>,
        primaryTrack: SubtitleTrackMeta
    ): SubtitleTrackMeta? {
        return tracks.firstOrNull { track ->
            track.lan.startsWith("en", ignoreCase = true) && track.trackKey != primaryTrack.trackKey
        } ?: tracks.firstOrNull { track ->
            track.trackKey != primaryTrack.trackKey
        }
    }

    fun selectSubtitleTrack(trackKey: String) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val normalizedTrackKey = trackKey.trim()
        if (normalizedTrackKey.isBlank() || current.subtitleTracks.isEmpty()) return
        if (current.subtitleOwnerBvid != current.info.bvid || current.subtitleOwnerCid != current.info.cid) return

        loadSubtitleTracksFromPlayerInfo(
            bvid = current.info.bvid,
            cid = current.info.cid,
            subtitles = emptyList(),
            preferredPrimaryLanguage = null,
            requestToken = currentLoadRequestToken,
            precomputedTracks = current.subtitleTracks,
            selectedPrimaryTrackKey = normalizedTrackKey
        )
    }

    private fun loadSubtitleTracksFromPlayerInfo(
        bvid: String,
        cid: Long,
        subtitles: List<SubtitleItem>,
        preferredPrimaryLanguage: String? = null,
        requestToken: Long = currentLoadRequestToken,
        precomputedTracks: List<SubtitleTrackMeta>? = null,
        selectedPrimaryTrackKey: String? = null
    ) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.info.bvid != bvid || current.info.cid != cid) return
        if (!shouldApplyPlayerInfoResult(
                activeRequestToken = currentLoadRequestToken,
                resultRequestToken = requestToken,
                expectedBvid = bvid,
                expectedCid = cid,
                currentBvid = currentBvid,
                currentCid = currentCid
            )
        ) {
            return
        }

        val trackMetas = precomputedTracks ?: mapSubtitleTracksForPlayback(subtitles)
        if (trackMetas.isEmpty()) {
            clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
            return
        }

        val selectedPrimaryTrack = selectedPrimaryTrackKey
            ?.let { key -> trackMetas.firstOrNull { it.trackKey == key } }
        val selection = resolveDefaultSubtitleLanguages(
            tracks = trackMetas,
            preferredPrimaryLanguage = preferredPrimaryLanguage
        )
        var primaryTrack = selectedPrimaryTrack
            ?: trackMetas.firstOrNull { it.lan == selection.primaryLanguage }
            ?: trackMetas.first()
        var secondaryTrack = if (selectedPrimaryTrack != null) {
            resolveSecondarySubtitleTrack(trackMetas, primaryTrack)
        } else {
            selection.secondaryLanguage
                ?.let { targetLan ->
                    trackMetas.firstOrNull { it.lan == targetLan && it.trackKey != primaryTrack.trackKey }
                }
                ?: resolveSecondarySubtitleTrack(trackMetas, primaryTrack)
        }
        var primaryTrackKey = buildSubtitleTrackBindingKey(
            subtitleId = primaryTrack.id,
            subtitleIdStr = primaryTrack.idStr,
            languageCode = primaryTrack.lan,
            subtitleUrl = primaryTrack.subtitleUrl
        )
        var secondaryTrackKey = secondaryTrack?.let {
            buildSubtitleTrackBindingKey(
                subtitleId = it.id,
                subtitleIdStr = it.idStr,
                languageCode = it.lan,
                subtitleUrl = it.subtitleUrl
            )
        }
        subtitleLoadToken += 1
        val currentToken = subtitleLoadToken

        if (!shouldApplySubtitleLoadResult(
                activeSubtitleToken = subtitleLoadToken,
                resultSubtitleToken = currentToken,
                expectedBvid = bvid,
                expectedCid = cid,
                currentBvid = currentBvid,
                currentCid = currentCid
            )
        ) {
            return
        }

        _uiState.update { state ->
            if (state is VideoPlaybackUiState.Success &&
                state.info.bvid == bvid &&
                state.info.cid == cid
            ) {
                state.copy(
                    subtitleEnabled = true,
                    subtitleOwnerBvid = bvid,
                    subtitleOwnerCid = cid,
                    subtitlePrimaryLanguage = primaryTrack.lan,
                    subtitleSecondaryLanguage = secondaryTrack?.lan,
                    subtitlePrimaryTrackKey = primaryTrackKey,
                    subtitleSecondaryTrackKey = secondaryTrackKey,
                    subtitleTracks = trackMetas,
                    subtitlePrimaryLikelyAi = isLikelyAiSubtitleTrack(primaryTrack),
                    subtitleSecondaryLikelyAi = secondaryTrack?.let(::isLikelyAiSubtitleTrack) ?: false,
                    subtitlePrimaryCues = emptyList(),
                    subtitleSecondaryCues = emptyList()
                )
            } else {
                state
            }
        }

        viewModelScope.launch {
            var primaryResult = VideoRepository.getSubtitleCues(
                subtitleUrl = primaryTrack.subtitleUrl,
                bvid = bvid,
                cid = cid,
                subtitleId = primaryTrack.id,
                subtitleIdStr = primaryTrack.idStr,
                subtitleLan = primaryTrack.lan
            )
            var secondaryResult = secondaryTrack?.let { track ->
                VideoRepository.getSubtitleCues(
                    subtitleUrl = track.subtitleUrl,
                    bvid = bvid,
                    cid = cid,
                    subtitleId = track.id,
                    subtitleIdStr = track.idStr,
                    subtitleLan = track.lan
                )
            } ?: Result.success(emptyList())
            var activeTrackMetas = trackMetas

            val shouldRetryWithFreshPlayerInfo = shouldRetrySubtitleLoadWithPlayerInfo(
                primaryResult.exceptionOrNull()?.message
            ) || shouldRetrySubtitleLoadWithPlayerInfo(
                secondaryResult.exceptionOrNull()?.message
            )
            if (shouldRetryWithFreshPlayerInfo) {
                Logger.d(
                    "PlayerVM",
                    "SUB_DBG subtitle load got auth-like failure, retry with refreshed player info: bvid=$bvid cid=$cid"
                )
                val refreshedTracks = VideoRepository.getPlayerInfo(bvid, cid)
                    .getOrNull()
                    ?.subtitle
                    ?.subtitles
                    .orEmpty()
                    .let(::mapSubtitleTracksForPlayback)
                if (refreshedTracks.isNotEmpty()) {
                    activeTrackMetas = refreshedTracks
                    val retryPrimaryTrack = refreshedTracks.firstOrNull { track ->
                        buildSubtitleTrackBindingKey(
                            subtitleId = track.id,
                            subtitleIdStr = track.idStr,
                            languageCode = track.lan,
                            subtitleUrl = track.subtitleUrl
                        ) == primaryTrackKey
                    } ?: refreshedTracks.firstOrNull { it.lan == primaryTrack.lan }
                    if (retryPrimaryTrack != null && primaryResult.isFailure) {
                        primaryTrack = retryPrimaryTrack
                        primaryTrackKey = buildSubtitleTrackBindingKey(
                            subtitleId = primaryTrack.id,
                            subtitleIdStr = primaryTrack.idStr,
                            languageCode = primaryTrack.lan,
                            subtitleUrl = primaryTrack.subtitleUrl
                        )
                        primaryResult = VideoRepository.getSubtitleCues(
                            subtitleUrl = primaryTrack.subtitleUrl,
                            bvid = bvid,
                            cid = cid,
                            subtitleId = primaryTrack.id,
                            subtitleIdStr = primaryTrack.idStr,
                            subtitleLan = primaryTrack.lan
                        )
                    }

                    if (secondaryTrack != null) {
                        val retrySecondaryTrack = refreshedTracks.firstOrNull { track ->
                            buildSubtitleTrackBindingKey(
                                subtitleId = track.id,
                                subtitleIdStr = track.idStr,
                                languageCode = track.lan,
                                subtitleUrl = track.subtitleUrl
                            ) == secondaryTrackKey
                        } ?: refreshedTracks.firstOrNull { track ->
                            track.lan == secondaryTrack?.lan && track.trackKey != primaryTrack.trackKey
                        }
                        if (retrySecondaryTrack != null && secondaryResult.isFailure) {
                            secondaryTrack = retrySecondaryTrack
                            secondaryTrackKey = buildSubtitleTrackBindingKey(
                                subtitleId = retrySecondaryTrack.id,
                                subtitleIdStr = retrySecondaryTrack.idStr,
                                languageCode = retrySecondaryTrack.lan,
                                subtitleUrl = retrySecondaryTrack.subtitleUrl
                            )
                            secondaryResult = VideoRepository.getSubtitleCues(
                                subtitleUrl = retrySecondaryTrack.subtitleUrl,
                                bvid = bvid,
                                cid = cid,
                                subtitleId = retrySecondaryTrack.id,
                                subtitleIdStr = retrySecondaryTrack.idStr,
                                subtitleLan = retrySecondaryTrack.lan
                            )
                        }
                    }
                }
            }

            if (!shouldApplySubtitleLoadResult(
                    activeSubtitleToken = subtitleLoadToken,
                    resultSubtitleToken = currentToken,
                    expectedBvid = bvid,
                    expectedCid = cid,
                    currentBvid = currentBvid,
                    currentCid = currentCid
                )
            ) {
                return@launch
            }

            _uiState.update { state ->
                if (state is VideoPlaybackUiState.Success &&
                    state.info.bvid == bvid &&
                    state.info.cid == cid
                ) {
                    state.copy(
                        subtitlePrimaryTrackKey = primaryTrackKey,
                        subtitleSecondaryTrackKey = secondaryTrackKey,
                        subtitleTracks = activeTrackMetas,
                        subtitlePrimaryLikelyAi = isLikelyAiSubtitleTrack(primaryTrack),
                        subtitleSecondaryLikelyAi = secondaryTrack?.let(::isLikelyAiSubtitleTrack) ?: false
                    )
                } else {
                    state
                }
            }

            val primaryCues = primaryResult.getOrElse {
                emptyList()
            }
            val secondaryCues = secondaryResult.getOrElse {
                emptyList()
            }

            val subtitleDecision = resolveSubtitleTrackLoadDecision(
                primaryLanguage = primaryTrack.lan,
                primaryCues = primaryCues,
                primaryLikelyAi = isLikelyAiSubtitleTrack(primaryTrack),
                secondaryLanguage = secondaryTrack?.lan,
                secondaryCues = secondaryCues,
                secondaryLikelyAi = secondaryTrack?.let(::isLikelyAiSubtitleTrack) ?: false
            )

            _uiState.update { state ->
                val primaryMismatchReason = if (state is VideoPlaybackUiState.Success) {
                    resolveSubtitleTrackBindingMismatchReason(
                        expectedTrackKey = primaryTrackKey,
                        currentTrackKey = state.subtitlePrimaryTrackKey,
                        expectedLanguage = primaryTrack.lan,
                        currentLanguage = state.subtitlePrimaryLanguage
                    )
                } else {
                    "ui-not-success"
                }
                val secondaryMismatchReason = if (state is VideoPlaybackUiState.Success) {
                    resolveSubtitleTrackBindingMismatchReason(
                        expectedTrackKey = secondaryTrackKey,
                        currentTrackKey = state.subtitleSecondaryTrackKey,
                        expectedLanguage = secondaryTrack?.lan,
                        currentLanguage = state.subtitleSecondaryLanguage
                    )
                } else {
                    "ui-not-success"
                }
                if (state is VideoPlaybackUiState.Success &&
                    state.info.bvid == bvid &&
                    state.info.cid == cid &&
                    primaryMismatchReason == null &&
                    secondaryMismatchReason == null
                ) {
                    Logger.d(
                        "PlayerVM",
                        "SUB_DBG apply subtitle: owner=$bvid/$cid primaryLang=${subtitleDecision.primaryLanguage} secondaryLang=${subtitleDecision.secondaryLanguage} primaryCues=${subtitleDecision.primaryCues.size} secondaryCues=${subtitleDecision.secondaryCues.size}"
                    )
                    state.copy(
                        subtitleEnabled = subtitleDecision.primaryCues.isNotEmpty() ||
                            subtitleDecision.secondaryCues.isNotEmpty(),
                        subtitleOwnerBvid = bvid,
                        subtitleOwnerCid = cid,
                        subtitlePrimaryLanguage = subtitleDecision.primaryLanguage,
                        subtitleSecondaryLanguage = subtitleDecision.secondaryLanguage,
                        subtitlePrimaryLikelyAi = subtitleDecision.primaryLikelyAi,
                        subtitleSecondaryLikelyAi = subtitleDecision.secondaryLikelyAi,
                        subtitlePrimaryCues = subtitleDecision.primaryCues,
                        subtitleSecondaryCues = subtitleDecision.secondaryCues
                    )
                } else {
                    if (state is VideoPlaybackUiState.Success &&
                        state.info.bvid == bvid &&
                        state.info.cid == cid
                    ) {
                        Logger.d(
                            "PlayerVM",
                            "SUB_DBG drop subtitle apply by binding: bvid=$bvid cid=$cid primary=${primaryMismatchReason ?: "ok"} secondary=${secondaryMismatchReason ?: "ok"}"
                        )
                    }
                    state
                }
            }
        }
    }

    private fun clearInteractiveChoiceRuntime() {
        interactiveQuestionMonitorJob?.cancel()
        interactiveCountdownJob?.cancel()
        interactiveGraphVersion = 0L
        interactiveCurrentEdgeId = 0L
        interactivePausedByQuestion = false
        interactiveHiddenVariables.clear()
        interactiveEdgeStartPositionMs.clear()
        _interactiveChoicePanel.value = InteractiveChoicePanelUiState()
    }

    private suspend fun loadInteractiveEdgeInfo(edgeId: Long?) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.info.isSteinGate != 1 || interactiveGraphVersion <= 0L) {
            clearInteractiveChoiceRuntime()
            return
        }

        VideoRepository.getInteractEdgeInfo(
            bvid = current.info.bvid,
            graphVersion = interactiveGraphVersion,
            edgeId = edgeId
        ).onSuccess { data ->
            processInteractiveEdgeData(current, data)
        }.onFailure { e ->
            Logger.w("PlayerVM", "Interactive edge load failed: ${e.message}")
        }
    }

    private fun processInteractiveEdgeData(
        current: VideoPlaybackUiState.Success,
        data: InteractEdgeInfoData
    ) {
        interactiveCurrentEdgeId = data.edgeId.takeIf { it > 0L } ?: interactiveCurrentEdgeId
        data.hiddenVars.forEach { variable ->
            val key = variable.idV2.ifBlank { variable.id }
            if (key.isNotBlank()) {
                interactiveHiddenVariables[key] = variable.value
            }
        }
        data.storyList.forEach { node ->
            if (node.edgeId > 0L && node.startPos >= 0L) {
                interactiveEdgeStartPositionMs[node.edgeId] = node.startPos
            }
        }

        if (data.isLeaf == 1) {
            _interactiveChoicePanel.value = InteractiveChoicePanelUiState()
            return
        }

        val questionWithChoices = data.edges?.questions
            ?.asSequence()
            ?.map { question -> question to buildInteractiveChoices(question, current.info.cid) }
            ?.firstOrNull { (_, choices) -> choices.isNotEmpty() }
            ?: run {
                _interactiveChoicePanel.value = InteractiveChoicePanelUiState()
                return
            }
        val question = questionWithChoices.first
        val uiChoices = questionWithChoices.second

        val resolvedEdgeId = data.edgeId.takeIf { it > 0L } ?: interactiveCurrentEdgeId
        val edgeStartMs = resolveInteractiveEdgeStartPositionMs(data, resolvedEdgeId)
        val triggerOffsetMs = question.startTimeR.toLong().coerceAtLeast(0L)
        val absoluteTriggerMs = resolveInteractiveQuestionTriggerMs(edgeStartMs, triggerOffsetMs)
        val dimension = data.edges?.dimension

        scheduleInteractiveQuestion(
            edgeId = resolvedEdgeId,
            questionId = question.id,
            title = if (question.title.isBlank()) "剧情分支" else question.title,
            questionType = question.type,
            triggerMs = absoluteTriggerMs,
            durationMs = normalizeInteractiveCountdownMs(question.duration),
            pauseVideo = question.pauseVideo == 1,
            sourceVideoWidth = dimension?.width ?: 0,
            sourceVideoHeight = dimension?.height ?: 0,
            choices = uiChoices
        )
    }

    private fun buildInteractiveChoices(
        question: InteractQuestion,
        currentCid: Long
    ): List<InteractiveChoiceUiModel> {
        return question.choices
            .filter { choice ->
                val resolvedEdgeId = resolveInteractiveChoiceEdgeId(
                    choiceEdgeId = choice.id,
                    platformAction = choice.platformAction
                )
                resolvedEdgeId != null &&
                    choice.isHidden != 1 &&
                    evaluateInteractiveChoiceCondition(
                        condition = choice.condition,
                        variables = interactiveHiddenVariables
                    )
            }
            .mapNotNull { choice ->
                val resolvedEdgeId = resolveInteractiveChoiceEdgeId(
                    choiceEdgeId = choice.id,
                    platformAction = choice.platformAction
                ) ?: return@mapNotNull null
                val resolvedCid = resolveInteractiveChoiceCid(
                    choiceCid = choice.cid,
                    platformAction = choice.platformAction,
                    currentCid = currentCid
                ) ?: return@mapNotNull null
                InteractiveChoiceUiModel(
                    edgeId = resolvedEdgeId,
                    cid = resolvedCid,
                    text = choice.option.ifBlank { "继续" },
                    isDefault = choice.isDefault == 1,
                    nativeAction = choice.nativeAction,
                    x = choice.x.takeIf { it > 0 },
                    y = choice.y.takeIf { it > 0 },
                    textAlign = choice.textAlign
                )
            }
    }

    private fun resolveInteractiveEdgeStartPositionMs(
        data: InteractEdgeInfoData,
        edgeId: Long
    ): Long {
        val currentNodeStart = data.storyList
            .firstOrNull { it.isCurrent == 1 && it.startPos >= 0L }
            ?.startPos
        if (currentNodeStart != null) return currentNodeStart

        val edgeNodeStart = data.storyList
            .firstOrNull { it.edgeId == edgeId && it.startPos >= 0L }
            ?.startPos
        if (edgeNodeStart != null) return edgeNodeStart

        return interactiveEdgeStartPositionMs[edgeId]?.coerceAtLeast(0L) ?: 0L
    }

    private fun scheduleInteractiveQuestion(
        edgeId: Long,
        questionId: Long,
        title: String,
        questionType: Int,
        triggerMs: Long,
        durationMs: Long?,
        pauseVideo: Boolean,
        sourceVideoWidth: Int,
        sourceVideoHeight: Int,
        choices: List<InteractiveChoiceUiModel>
    ) {
        interactiveQuestionMonitorJob?.cancel()
        interactiveCountdownJob?.cancel()
        _interactiveChoicePanel.value = InteractiveChoicePanelUiState(
            visible = false,
            title = title,
            edgeId = edgeId,
            questionId = questionId,
            questionType = questionType,
            choices = choices,
            remainingMs = durationMs,
            pauseVideo = pauseVideo,
            sourceVideoWidth = sourceVideoWidth,
            sourceVideoHeight = sourceVideoHeight
        )

        interactiveQuestionMonitorJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value as? VideoPlaybackUiState.Success ?: return@launch
                if (current.info.cid != currentCid) return@launch
                val currentPosition = playbackUseCase.getCurrentPosition().coerceAtLeast(0L)
                if (shouldTriggerInteractiveQuestion(currentPosition, triggerMs)) {
                    showInteractiveChoicePanel(durationMs = durationMs, pauseVideo = pauseVideo)
                    return@launch
                }
                delay(
                    resolveInteractiveQuestionPollingIntervalMs(
                        currentPositionMs = currentPosition,
                        triggerTimeMs = triggerMs,
                        isPlaying = exoPlayer?.isPlaying == true
                    )
                )
            }
        }
    }

    private fun showInteractiveChoicePanel(durationMs: Long?, pauseVideo: Boolean) {
        if (pauseVideo) {
            exoPlayer?.pause()
            interactivePausedByQuestion = true
        } else {
            interactivePausedByQuestion = false
        }
        _interactiveChoicePanel.update { panel ->
            panel.copy(visible = true, remainingMs = durationMs)
        }

        if (durationMs == null) return
        interactiveCountdownJob?.cancel()
        interactiveCountdownJob = viewModelScope.launch {
            val startAt = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startAt
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                _interactiveChoicePanel.update { panel ->
                    if (!panel.visible) panel else panel.copy(remainingMs = remaining)
                }
                if (remaining <= 0L) break
                delay(resolveInteractiveCountdownUpdateIntervalMs(remainingMs = remaining).coerceAtMost(remaining))
            }

            val panel = _interactiveChoicePanel.value
            if (!panel.visible) return@launch
            val autoChoice = resolveInteractiveAutoChoice(panel.choices)
            if (autoChoice != null) {
                selectInteractiveChoice(autoChoice.edgeId, autoChoice.cid)
            } else {
                dismissInteractiveChoicePanel()
            }
        }
    }

    // [新增] 加载 AI 视频总结
    private fun loadAiSummary(
        bvid: String,
        cid: Long,
        upMid: Long
    ) {
        aiSummaryJob?.cancel()
        aiSummaryJob = viewModelScope.launch {
            var queuedRetryCount = 0
            var requestRetryCount = 0
            val loadingPrompt = initialAiSummaryPromptState()
            _uiState.update { current ->
                if (
                    current is VideoPlaybackUiState.Success &&
                    current.info.bvid == bvid &&
                    current.aiSummary?.modelResult == null
                ) {
                    current.copy(aiSummaryPrompt = loadingPrompt)
                } else current
            }

            while (true) {
                try {
                    if (BackgroundManager.isInBackground) {
                        delay(
                            resolveAiSummaryRetryDelayMs(
                                queuedRetryCount = queuedRetryCount,
                                isInBackground = true
                            )
                        )
                        continue
                    }
                    val result = VideoRepository.getAiSummary(bvid, cid, upMid)
                    var shouldPollAgain = false
                    var nextDelayMs = 0L
                    var completedQueuedRetry = false
                    var completedRequestRetry = false

                    result.onSuccess { response ->
                        val diagnosis =
                            com.android.purebilibili.data.repository.diagnoseAiSummaryResponse(response)
                        when {
                            diagnosis.status ==
                                com.android.purebilibili.data.repository.AiSummaryFetchStatus.AVAILABLE &&
                                response.data != null -> {
                                _uiState.update { current ->
                                    if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                                        current.copy(
                                            aiSummary = response.data,
                                            aiSummaryPrompt = null
                                        )
                                    } else current
                                }
                                Logger.i(
                                    "PlayerVM",
                                    "🤖 Loaded AI Summary: bvid=$bvid cid=$cid status=${diagnosis.status}"
                                )
                            }

                            shouldContinueAiSummaryAutoRetry(
                                status = diagnosis.status,
                                queuedRetryCount = queuedRetryCount
                            ) && diagnosis.shouldRetryLater -> {
                                val prompt = resolveAiSummaryPromptState(diagnosis)
                                _uiState.update { current ->
                                    if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                                        current.copy(aiSummaryPrompt = prompt)
                                    } else current
                                }
                                nextDelayMs = resolveAiSummaryRetryDelayMs(
                                    queuedRetryCount = queuedRetryCount,
                                    isInBackground = BackgroundManager.isInBackground
                                )
                                shouldPollAgain = true
                                completedQueuedRetry = true
                                Logger.i(
                                    "PlayerVM",
                                    "🤖 AI Summary queued, retry later: bvid=$bvid cid=$cid stid=${diagnosis.stid ?: ""} retryInMs=$nextDelayMs retryCount=$queuedRetryCount"
                                )
                            }

                            diagnosis.status ==
                                com.android.purebilibili.data.repository.AiSummaryFetchStatus.QUEUED -> {
                                val prompt = queuedAiSummaryPendingPromptState()
                                _uiState.update { current ->
                                    if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                                        current.copy(aiSummaryPrompt = prompt)
                                    } else current
                                }
                                Logger.i(
                                    "PlayerVM",
                                    "🤖 AI Summary still queued after auto retries: bvid=$bvid cid=$cid stid=${diagnosis.stid ?: ""} retryCount=$queuedRetryCount"
                                )
                            }

                            else -> {
                                val prompt = resolveAiSummaryPromptState(diagnosis)
                                _uiState.update { current ->
                                    if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                                        current.copy(aiSummaryPrompt = prompt)
                                    } else current
                                }
                                Logger.i(
                                    "PlayerVM",
                                    "🤖 AI Summary unavailable: bvid=$bvid cid=$cid status=${diagnosis.status} reason=${diagnosis.reason} rootCode=${diagnosis.rootCode} dataCode=${diagnosis.dataCode} stid=${diagnosis.stid ?: ""}"
                                )
                            }
                        }
                    }.onFailure { throwable ->
                        val diagnosis =
                            com.android.purebilibili.data.repository.diagnoseAiSummaryFailure(throwable)
                        if (shouldRetryAiSummaryRequestFailure(diagnosis.status, requestRetryCount)) {
                            nextDelayMs = resolveAiSummaryRetryDelayMs(
                                queuedRetryCount = requestRetryCount,
                                isInBackground = BackgroundManager.isInBackground
                            )
                            shouldPollAgain = true
                            completedRequestRetry = true
                            Logger.i(
                                "PlayerVM",
                                "🤖 AI Summary retryable failure, retry scheduled: bvid=$bvid cid=$cid retryInMs=$nextDelayMs retryCount=$requestRetryCount"
                            )
                            return@onFailure
                        }
                        val prompt = resolveAiSummaryPromptState(diagnosis)
                        _uiState.update { current ->
                            if (current is VideoPlaybackUiState.Success && current.info.bvid == bvid) {
                                current.copy(aiSummaryPrompt = prompt)
                            } else current
                        }
                        Logger.w(
                            "PlayerVM",
                            "🤖 Failed to load AI Summary: bvid=$bvid cid=$cid status=${diagnosis.status} reason=${diagnosis.reason}"
                        )
                    }

                    if (!shouldPollAgain) {
                        return@launch
                    }

                    if (completedQueuedRetry) queuedRetryCount += 1
                    if (completedRequestRetry) requestRetryCount += 1
                    delay(nextDelayMs)
                    val currentSuccess = _uiState.value as? VideoPlaybackUiState.Success
                    if (
                        currentSuccess?.info?.bvid != bvid ||
                        currentSuccess.info.cid != cid ||
                        currentSuccess.aiSummary?.modelResult != null
                    ) {
                        return@launch
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.d("PlayerVM", "🤖 Failed to load AI Summary: ${e.message}")
                    return@launch
                }
            }
        }
    }

    private fun loadVideoNote(
        loadedBvid: String,
        loadedAid: Long
    ) {
        val videoNoteEnabled = appContext?.let {
            com.android.purebilibili.core.store.SettingsManager.getVideoNoteEnabledSync(it)
        } ?: true
        if (!shouldLoadVideoNote(videoNoteEnabled, loadedAid)) return
        videoNoteJob?.cancel()
        videoNoteJob = viewModelScope.launch {
            _uiState.update { state ->
                val success = state as? VideoPlaybackUiState.Success ?: return@update state
                if (success.info.bvid != loadedBvid) return@update state
                success.copy(
                    videoNoteState = success.videoNoteState.copy(
                        status = VideoNoteLoadStatus.LOADING,
                        errorMessage = null,
                        feedbackMessage = null
                    )
                )
            }

            VideoNoteRepository.getVideoNoteSnapshot(loadedAid)
                .onSuccess { snapshot ->
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (success.info.bvid != loadedBvid || success.info.aid != loadedAid) return@update state
                        val privateNote = snapshot.privateNote
                        val privateDocument = privateNote?.let {
                            VideoNoteContentCodec.decode(
                                title = it.title.ifBlank { success.info.title },
                                content = it.content
                            )
                        }
                        success.copy(
                            videoNoteState = VideoNoteUiState(
                                status = VideoNoteLoadStatus.READY,
                                forbidNoteEntrance = snapshot.forbidNoteEntrance,
                                privateNoteId = snapshot.privateNoteId,
                                privateNoteTitle = privateNote?.title.orEmpty(),
                                privateNoteSummary = privateNote?.summary.orEmpty(),
                                privateNoteDocument = privateDocument,
                                publicNoteCount = snapshot.publicNoteTotal,
                                publicNotes = snapshot.publicNotes.map { note ->
                                    VideoNotePublicPreview(
                                        cvid = note.cvid,
                                        title = note.title,
                                        summary = note.summary,
                                        authorName = note.author?.name.orEmpty(),
                                        authorMid = note.author?.mid ?: 0L,
                                        authorFace = note.author?.face.orEmpty(),
                                        authorLevel = note.author?.level ?: 0,
                                        authorSenior = (note.author?.isSeniorMember ?: 0) == 1,
                                        pubtime = note.pubtime,
                                        webUrl = note.webUrl,
                                        likes = note.likes
                                    )
                                },
                                publicNotesEnd = snapshot.publicNotes.size >= snapshot.publicNoteTotal
                            )
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (success.info.bvid != loadedBvid || success.info.aid != loadedAid) return@update state
                        success.copy(
                            videoNoteState = success.videoNoteState.copy(
                                status = VideoNoteLoadStatus.ERROR,
                                errorMessage = throwable.message ?: "笔记加载失败"
                            )
                        )
                    }
                }
        }
    }

    fun retryVideoNote() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val videoNoteEnabled = appContext?.let {
            com.android.purebilibili.core.store.SettingsManager.getVideoNoteEnabledSync(it)
        } ?: true
        if (!shouldLoadVideoNote(videoNoteEnabled, current.info.aid)) return
        loadVideoNote(loadedBvid = current.info.bvid, loadedAid = current.info.aid)
    }

    fun loadMorePublicVideoNotes() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val noteState = current.videoNoteState
        if (noteState.publicNotesLoadingMore || noteState.publicNotesEnd) return
        val nextPage = noteState.publicNotes.size / PUBLIC_VIDEO_NOTE_PAGE_SIZE + 1
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(
                videoNoteState = success.videoNoteState.copy(publicNotesLoadingMore = true)
            )
        }
        viewModelScope.launch {
            VideoNoteRepository.getPublicVideoNotePage(current.info.aid, nextPage)
                .onSuccess { page ->
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (success.info.aid != current.info.aid) return@update state
                        val existing = success.videoNoteState.publicNotes
                        val knownCvids = existing.mapTo(mutableSetOf()) { it.cvid }
                        val appended = page.notes
                            .filter { it.cvid !in knownCvids }
                            .map { note ->
                                VideoNotePublicPreview(
                                    cvid = note.cvid,
                                    title = note.title,
                                    summary = note.summary,
                                    authorName = note.author?.name.orEmpty(),
                                    authorMid = note.author?.mid ?: 0L,
                                    authorFace = note.author?.face.orEmpty(),
                                    authorLevel = note.author?.level ?: 0,
                                    pubtime = note.pubtime,
                                    webUrl = note.webUrl,
                                    likes = note.likes
                                )
                            }
                        val merged = existing + appended
                        success.copy(
                            videoNoteState = success.videoNoteState.copy(
                                publicNotes = merged,
                                publicNoteCount = page.total,
                                publicNotesLoadingMore = false,
                                publicNotesEnd = merged.size >= page.total || page.notes.isEmpty()
                            )
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        success.copy(
                            videoNoteState = success.videoNoteState.copy(publicNotesLoadingMore = false)
                        )
                    }
                }
        }
    }

    fun openVideoNoteEditor() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.videoNoteState.forbidNoteEntrance) return
        val noteState = current.videoNoteState
        val document = resolveVideoNoteEditableDocument(
            noteState = noteState,
            defaultTitle = current.info.title
        )
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(
                videoNoteState = success.videoNoteState.copy(
                    editorVisible = true,
                    editorDocument = document,
                    editorFromAiSummary = noteState.editorFromAiSummary && noteState.privateNoteDocument != document,
                    errorMessage = null,
                    feedbackMessage = null
                )
            )
        }
    }

    fun closeVideoNoteEditor() {
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(videoNoteState = success.videoNoteState.copy(editorVisible = false))
        }
    }

    fun updateVideoNoteEditorDocument(document: VideoNoteEditorDocument) {
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(videoNoteState = success.videoNoteState.copy(editorDocument = document))
        }
    }

    fun currentVideoNoteTimestamp(): VideoNoteBlock.Timestamp? {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return null
        val positionSeconds = ((exoPlayer?.currentPosition ?: 0L) / 1000L).coerceAtLeast(0L)
        val pageIndex = current.info.pages.indexOfFirst { it.cid == current.info.cid }.coerceAtLeast(0)
        return VideoNoteBlock.Timestamp(
            seconds = positionSeconds,
            cid = current.info.cid,
            index = pageIndex,
            cidCount = current.info.pages.size.coerceAtLeast(1)
        )
    }

    fun createVideoNoteDraftFromAiSummary() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val aiSummary = current.aiSummary ?: return
        val pageIndex = current.info.pages.indexOfFirst { it.cid == current.info.cid }.coerceAtLeast(0)
        val draft = buildVideoNoteDraftFromAiSummary(
            title = current.videoNoteState.privateNoteDocument?.title ?: current.info.title,
            aiSummary = aiSummary,
            cid = current.info.cid,
            pageIndex = pageIndex,
            cidCount = current.info.pages.size.coerceAtLeast(1),
            existingDocument = current.videoNoteState.privateNoteDocument
        )
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(
                videoNoteState = success.videoNoteState.copy(
                    editorVisible = true,
                    editorDocument = draft,
                    editorFromAiSummary = true,
                    feedbackMessage = resolveVideoNoteConflictMessage(
                        hasExistingPrivateNote = success.videoNoteState.privateNoteDocument != null
                    ),
                    errorMessage = null
                )
            )
        }
    }

    fun saveVideoNote(updatedDocument: VideoNoteEditorDocument? = null) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val noteState = current.videoNoteState
        if (noteState.saving || noteState.forbidNoteEntrance) return
        val sourceDocument = updatedDocument ?: noteState.editorDocument
        val document = sourceDocument.copy(
            title = sourceDocument.title.ifBlank { current.info.title }
        )
        val encoded = VideoNoteContentCodec.encode(document)
        if (encoded.contentLength <= 0) {
            _uiState.update { state ->
                val success = state as? VideoPlaybackUiState.Success ?: return@update state
                success.copy(videoNoteState = success.videoNoteState.copy(errorMessage = "先写一点内容再保存。"))
            }
            return
        }
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(videoNoteState = success.videoNoteState.copy(saving = true, errorMessage = null))
        }
        viewModelScope.launch {
            VideoNoteRepository.savePrivateNote(
                VideoNoteSavePayload(
                    aid = current.info.aid,
                    noteId = noteState.privateNoteId,
                    title = document.title,
                    summary = encoded.summary,
                    content = encoded.content,
                    tags = encoded.tags,
                    contentLength = encoded.contentLength
                )
            ).onSuccess { noteId ->
                _uiState.update { state ->
                    val success = state as? VideoPlaybackUiState.Success ?: return@update state
                    if (success.info.bvid != current.info.bvid) return@update state
                    success.copy(
                        videoNoteState = success.videoNoteState.copy(
                            status = VideoNoteLoadStatus.READY,
                            privateNoteId = noteId,
                            privateNoteTitle = document.title,
                            privateNoteSummary = encoded.summary,
                            privateNoteDocument = document,
                            editorVisible = false,
                            saving = false,
                            feedbackMessage = resolveVideoNoteSaveFeedback(noteState.editorFromAiSummary),
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    val success = state as? VideoPlaybackUiState.Success ?: return@update state
                    success.copy(
                        videoNoteState = success.videoNoteState.copy(
                            saving = false,
                            errorMessage = throwable.message ?: "笔记保存失败"
                        )
                    )
                }
            }
        }
    }

    fun deleteVideoNote() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val noteId = current.videoNoteState.privateNoteId ?: return
        if (current.videoNoteState.deleting) return
        _uiState.update { state ->
            val success = state as? VideoPlaybackUiState.Success ?: return@update state
            success.copy(videoNoteState = success.videoNoteState.copy(deleting = true, errorMessage = null))
        }
        viewModelScope.launch {
            VideoNoteRepository.deletePrivateNote(aid = current.info.aid, noteId = noteId)
                .onSuccess {
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (success.info.bvid != current.info.bvid) return@update state
                        success.copy(
                            videoNoteState = success.videoNoteState.copy(
                                privateNoteId = null,
                                privateNoteTitle = "",
                                privateNoteSummary = "",
                                privateNoteDocument = null,
                                deleting = false,
                                feedbackMessage = "笔记已删除。"
                            )
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        success.copy(
                            videoNoteState = success.videoNoteState.copy(
                                deleting = false,
                                errorMessage = throwable.message ?: "笔记删除失败"
                            )
                        )
                    }
                }
        }
    }
    
    
    // ========== Download ==========
    
    //  下载对话框状态
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog = _showDownloadDialog.asStateFlow()
    
    fun openDownloadDialog() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        _showDownloadDialog.value = true
    }
    
    fun closeDownloadDialog() {
        _showDownloadDialog.value = false
    }

    private fun resolveDownloadQualityDescription(
        current: VideoPlaybackUiState.Success,
        qualityId: Int
    ): String {
        return current.qualityLabels.getOrNull(
            current.qualityIds.indexOf(qualityId)
        ) ?: "${qualityId}P"
    }

    private fun resolveBatchDownloadTaskTitle(
        rootTitle: String,
        candidateTitle: String,
        candidateLabel: String
    ): String {
        val normalizedRootTitle = rootTitle.trim()
        val normalizedCandidateTitle = candidateTitle.trim()
        val normalizedCandidateLabel = candidateLabel.trim()
        return when {
            normalizedCandidateTitle.isBlank() -> normalizedRootTitle
            normalizedCandidateTitle == normalizedRootTitle && normalizedCandidateLabel.isNotBlank() ->
                "$normalizedRootTitle - $normalizedCandidateLabel"
            else -> normalizedCandidateTitle
        }
    }

    private suspend fun buildDownloadTaskForTarget(
        current: VideoPlaybackUiState.Success,
        targetBvid: String,
        targetCid: Long,
        targetTitle: String,
        targetLabel: String,
        targetCover: String,
        qualityId: Int,
        options: com.android.purebilibili.feature.download.DownloadOptions = com.android.purebilibili.feature.download.DownloadOptions()
    ): com.android.purebilibili.feature.download.DownloadTask? {
        val qualityDesc = resolveDownloadQualityDescription(current, qualityId)
        val isCurrentTarget = targetBvid == currentBvid && targetCid == currentCid
        val candidate = com.android.purebilibili.feature.download.resolveBatchDownloadCandidate(
            info = current.info,
            targetBvid = targetBvid,
            targetCid = targetCid
        )
        val effectiveLabel = candidate?.label?.takeIf { it.isNotBlank() } ?: targetLabel
        val resolvedTitle = resolveBatchDownloadTaskTitle(
            rootTitle = current.info.title,
            candidateTitle = targetTitle,
            candidateLabel = effectiveLabel
        )

        val currentDashVideo = current.cachedDashVideos.find { it.id == qualityId }
        val currentDashAudio = current.cachedDashAudios.firstOrNull()

        val directVideoUrl = when {
            isCurrentTarget && qualityId == current.currentQuality -> current.playUrl
            isCurrentTarget && currentDashVideo != null -> currentDashVideo.getValidUrl()
            else -> ""
        }
        val directAudioUrl = when {
            isCurrentTarget && qualityId == current.currentQuality -> current.audioUrl.orEmpty()
            isCurrentTarget && currentDashAudio != null -> currentDashAudio.getValidUrl()
            else -> ""
        }

        val resolvedUrls = if (directVideoUrl.isNotBlank() && directAudioUrl.isNotBlank()) {
            directVideoUrl to directAudioUrl
        } else {
            val playUrlData = VideoRepository.getPlayUrlData(targetBvid, targetCid, qualityId)
            val selection = playUrlData?.let {
                playbackUseCase.resolvePlaybackSelection(
                    playUrlData = it,
                    targetQuality = qualityId
                )
            }
            val videoUrl = selection?.videoUrl.orEmpty()
            val audioUrl = selection?.audioUrl.orEmpty()
            if (videoUrl.isBlank() || audioUrl.isBlank()) {
                return null
            }
            videoUrl to audioUrl
        }

        return com.android.purebilibili.feature.download.DownloadTask(
            aid = current.info.aid,
            bvid = targetBvid,
            cid = targetCid,
            title = resolvedTitle,
            episodeLabel = effectiveLabel.takeIf { it.isNotBlank() && it != resolvedTitle },
            groupKey = candidate?.groupKey,
            groupTitle = candidate?.groupTitle,
            episodeSortIndex = candidate?.episodeSortIndex ?: 0,
            episodeCount = candidate?.episodeCount ?: 1,
            cover = targetCover.ifBlank { current.info.pic },
            ownerName = current.info.owner.name,
            ownerFace = current.info.owner.face,
            duration = candidate?.durationSeconds ?: 0,
            quality = qualityId,
            qualityDesc = qualityDesc,
            videoUrl = resolvedUrls.first,
            audioUrl = resolvedUrls.second,
            isVerticalVideo = candidate?.isVerticalVideo ?: (current.info.dimension?.isVertical == true),
            options = options
        )
    }
    
    fun downloadWithQuality(
        qualityId: Int,
        options: com.android.purebilibili.feature.download.DownloadOptions = com.android.purebilibili.feature.download.DownloadOptions()
    ) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        _showDownloadDialog.value = false
        
        viewModelScope.launch {
            val task = buildDownloadTaskForTarget(
                current = current,
                targetBvid = currentBvid,
                targetCid = currentCid,
                targetTitle = current.info.title,
                targetLabel = current.info.title,
                targetCover = current.info.pic,
                qualityId = qualityId,
                options = options
            )
            
            if (task == null) {
                toast("无法获取下载地址")
                return@launch
            }

            val added = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
            if (added) {
                toast("开始下载: ${task.title} [${task.qualityDesc}]")
                watchDownloadProgress(task.id)
            } else {
                toast("下载任务已存在")
            }
        }
    }

    internal fun downloadBatchWithQuality(
        qualityId: Int,
        options: com.android.purebilibili.feature.download.DownloadOptions = com.android.purebilibili.feature.download.DownloadOptions(),
        candidates: List<com.android.purebilibili.feature.download.BatchDownloadCandidate>
    ) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        _showDownloadDialog.value = false

        viewModelScope.launch {
            var addedCount = 0
            var skippedExistingCount = 0
            var failedCount = 0

            candidates.filter { it.selected }.forEach { candidate ->
                val existingTask = com.android.purebilibili.feature.download.DownloadManager.getVideoTask(
                    candidate.bvid,
                    candidate.cid
                )
                if (existingTask != null && !existingTask.isFailed) {
                    skippedExistingCount += 1
                    return@forEach
                }

                val task = buildDownloadTaskForTarget(
                    current = current,
                    targetBvid = candidate.bvid,
                    targetCid = candidate.cid,
                    targetTitle = candidate.title,
                    targetLabel = candidate.label,
                    targetCover = candidate.cover,
                    qualityId = qualityId,
                    options = options
                )
                if (task == null) {
                    failedCount += 1
                    return@forEach
                }

                val added = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
                if (added) {
                    addedCount += 1
                } else {
                    skippedExistingCount += 1
                }
            }

            toast(
                com.android.purebilibili.feature.download.summarizeBatchDownloadQueueResult(
                    com.android.purebilibili.feature.download.BatchDownloadQueueResult(
                        addedCount = addedCount,
                        skippedExistingCount = skippedExistingCount,
                        failedCount = failedCount
                    )
                )
            )
        }
    }
    
    // ========== Quality ==========

    private fun monitorPlaybackTransitionPosition(targetPositionMs: Long) {
        playbackTransitionMonitorJob?.cancel()
        playbackTransitionMonitorJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value as? VideoPlaybackUiState.Success ?: return@launch
                val pendingPositionMs = current.pendingPlaybackTransitionPositionMs ?: return@launch
                if (pendingPositionMs != targetPositionMs) return@launch
                val playerPositionMs = playbackUseCase.getCurrentPosition().coerceAtLeast(0L)
                if (!shouldHoldPlaybackResumeTransitionPosition(playerPositionMs, targetPositionMs)) {
                    _uiState.update { state ->
                        val success = state as? VideoPlaybackUiState.Success ?: return@update state
                        if (success.pendingPlaybackTransitionPositionMs != targetPositionMs) {
                            state
                        } else {
                            success.copy(pendingPlaybackTransitionPositionMs = null)
                        }
                    }
                    return@launch
                }
                delay(50)
            }
        }
    }
    
    fun changeQuality(qualityId: Int) = requestQuality(qualityId, source = "user")

    private fun requestQuality(qualityId: Int, source: String) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.isQualitySwitching) {
            toast("正在切换中...", PlayerToastPresentation.CenteredHighlight)
            return
        }
        if (current.currentQuality == qualityId) {
            toast("已是当前清晰度", PlayerToastPresentation.CenteredHighlight)
            return
        }
        finishQualitySwitchTelemetry("canceled", "superseded")
        val requestBvid = current.info.bvid
        val requestCid = current.info.cid
        val requestToken = currentLoadRequestToken
        val telemetryAttempt = if (AnalyticsHelper.isCollectionEnabled()) {
            com.android.purebilibili.core.util.QualitySwitchTelemetryAttempt(
                current.currentQuality, qualityId, source, android.os.SystemClock.elapsedRealtime()
            ).also { attempt ->
                pendingQualityTelemetry = PendingQualityTelemetry(
                    requestBvid, requestCid, requestToken, AnalyticsHelper.collectionGeneration(), attempt
                )
                AnalyticsHelper.logQualitySwitchRequest(current.currentQuality, qualityId, source)
            }
        } else null
        fun finishAttempt(outcome: String, reason: String) {
            if (pendingQualityTelemetry?.attempt === telemetryAttempt) {
                finishQualitySwitchTelemetry(outcome, reason)
            }
        }
        fun isCurrentRequest(): Boolean {
            val latest = _uiState.value as? VideoPlaybackUiState.Success ?: return false
            return currentLoadRequestToken == requestToken && currentBvid == requestBvid &&
                currentCid == requestCid && latest.info.bvid == requestBvid && latest.info.cid == requestCid
        }
        playbackRecoveryGeneration++

        // Cancel only the current playback's pending auto-upgrade. Other videos remain eligible.
        explicitQualitySelectionKeys += buildPremiumAutoUpgradePlaybackKey(
            currentBvid,
            currentCid,
            current.currentAudioLang
        )
        explicitQualitySelectionGeneration++
        hdrAutoUpgradeJob?.cancel()

        val isHdrSupported = appContext?.let {
            com.android.purebilibili.core.util.MediaUtils.isHdrSupported(it)
        } ?: com.android.purebilibili.core.util.MediaUtils.isHdrSupported()
        val isDolbyVisionSupported = appContext?.let {
            com.android.purebilibili.core.util.MediaUtils.isDolbyVisionSupported(it)
        } ?: com.android.purebilibili.core.util.MediaUtils.isDolbyVisionSupported()
        
        //  [新增] 权限检查
        val permissionResult = qualityManager.checkQualityPermission(
            qualityId = qualityId,
            isLoggedIn = current.isLoggedIn,
            isVip = current.isVip,
            isHdrSupported = isHdrSupported,
            isDolbyVisionSupported = isDolbyVisionSupported,
            serverAdvertisedQualities = current.qualityIds,
            serverPlayableQualities = current.cachedDashVideos
                .filter { it.getValidUrl().isNotEmpty() }
                .map { it.id }
                .distinct()
        )

        val currentPos = playbackUseCase.getCurrentPosition().coerceAtLeast(0L)
        val playWhenReadyAfterSwitch = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true

        when (permissionResult) {
            is QualityPermissionResult.RequiresVip -> {
                finishAttempt("blocked", "vip_required")
                showQualitySwitchFailureDialog(
                    requestedQualityId = qualityId,
                    permissionResult = permissionResult
                )
                // 自动降级到最高可用画质
                val fallbackQuality = qualityManager.getMaxAvailableQuality(
                    availableQualities = current.qualityIds,
                    isLoggedIn = current.isLoggedIn,
                    isVip = current.isVip,
                    isHdrSupported = isHdrSupported,
                    isDolbyVisionSupported = isDolbyVisionSupported
                )
                if (fallbackQuality != current.currentQuality) {
                    requestQuality(fallbackQuality, source = "permission_fallback")
                }
                return
            }
            is QualityPermissionResult.RequiresLogin -> {
                finishAttempt("blocked", "login_required")
                showQualitySwitchFailureDialog(
                    requestedQualityId = qualityId,
                    permissionResult = permissionResult
                )
                return
            }
            is QualityPermissionResult.UnsupportedByDevice -> {
                finishAttempt("blocked", "unsupported_device")
                showQualitySwitchFailureDialog(
                    requestedQualityId = qualityId,
                    permissionResult = permissionResult
                )
                val fallbackQuality = qualityManager.getMaxAvailableQuality(
                    availableQualities = current.qualityIds,
                    isLoggedIn = current.isLoggedIn,
                    isVip = current.isVip,
                    isHdrSupported = isHdrSupported,
                    isDolbyVisionSupported = isDolbyVisionSupported
                )
                if (fallbackQuality != current.currentQuality && fallbackQuality != qualityId) {
                    requestQuality(fallbackQuality, source = "permission_fallback")
                }
                return
            }
            is QualityPermissionResult.Permitted -> {
                // 继续切换
            }
        }

        val hasCachedDashTracks = current.cachedDashVideos.isNotEmpty()
        val cacheContainsRequestedQuality = current.cachedDashVideos.any { it.id == qualityId }
        val appApiCooldownRemainingMs = VideoRepository.getAppApiCooldownRemainingMs()
        if (
            shouldBlockPremiumQualitySwitchDuringCooldown(
                requestedQualityId = qualityId,
                cacheContainsRequestedQuality = cacheContainsRequestedQuality,
                appApiCooldownRemainingMs = appApiCooldownRemainingMs
            )
        ) {
            finishAttempt("blocked", "cooldown")
            showQualitySwitchFailureDialog(
                requestedQualityId = qualityId,
                hasCachedDashTracks = hasCachedDashTracks,
                cacheContainsRequestedQuality = cacheContainsRequestedQuality,
                qualityRefetchCooldownRemainingMs = appApiCooldownRemainingMs
            )
            return
        }

        val transitionPositionMs = currentPos.coerceAtLeast(0L)
        val playbackQualityMode = resolvePlaybackQualityModeForQualitySelection(qualityId)
        _qualitySwitchFailureDialog.value = null
        _uiState.value = current.copy(
            isQualitySwitching = true,
            requestedQuality = qualityId,
            playbackQualityMode = playbackQualityMode,
            pendingPlaybackTransitionPositionMs = transitionPositionMs
        )
        
        viewModelScope.launch {
            try {
                val audioPref = current.requestedAudioQuality
                val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
                val videoCodecPreference = resolveEffectiveVideoCodecPreference(
                    requestCodecOverride = null,
                    settingsCodecPreference = _videoCodecPreference.value,
                    sessionBlockedCodecs = sessionBlockedCodecs
                )
                val videoSecondCodecPreference = _videoSecondCodecPreference.value
                val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
                val isAv1Supported = resolveEffectiveAv1Support(
                    deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                    sessionBlockedCodecs = sessionBlockedCodecs
                )

                val result = playbackUseCase.changeQualityFromCache(
                    qualityId = qualityId,
                    cachedVideos = current.cachedDashVideos,
                    cachedAudios = current.cachedDashAudios,
                    cachedDash = current.cachedDash,
                    currentPos = currentPos,
                    durationMs = current.videoDurationMs,
                    playbackQualityMode = playbackQualityMode,
                    audioQualityPreference = audioPref,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    isHevcSupported = isHevcSupported,
                    isAv1Supported = isAv1Supported,
                    playWhenReady = playWhenReadyAfterSwitch,
                    applyToPlayer = false
                ) ?: playbackUseCase.changeQualityFromApi(
                    bvid = requestBvid,
                    cid = requestCid,
                    qualityId = qualityId,
                    currentPos = currentPos,
                    playbackQualityMode = playbackQualityMode,
                    audioQualityPreference = audioPref,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    isHevcSupported = isHevcSupported,
                    isAv1Supported = isAv1Supported,
                    playWhenReady = playWhenReadyAfterSwitch,
                    applyToPlayer = false
                )
                
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!isCurrentRequest()) {
                    finishAttempt("canceled", "media_changed")
                    return@launch
                }
                if (result != null) {
                    val nextCachedDashVideos = result.cachedDashVideos.ifEmpty { current.cachedDashVideos }
                    val nextCachedDashAudios = result.cachedDashAudios.ifEmpty { current.cachedDashAudios }
                    val nextCachedDash = result.cachedDash ?: current.cachedDash
                    val cdnSelection = resolvePlaybackCdnCandidateSelection(
                        videoUrl = result.videoUrl,
                        audioUrl = result.audioUrl,
                        quality = result.actualQuality,
                        cachedDashVideos = nextCachedDashVideos,
                        cachedDashAudios = nextCachedDashAudios,
                        adaptiveDashSource = result.adaptiveDashSource
                    )
                    applyQualityChangeResult(
                        payload = QualityChangePayload(
                            playUrl = result.videoUrl,
                            audioUrl = result.audioUrl,
                            actualQuality = result.actualQuality,
                            adaptiveDashSource = result.adaptiveDashSource,
                            cachedDashVideos = nextCachedDashVideos,
                            cachedDashAudios = nextCachedDashAudios,
                            cachedDash = nextCachedDash,
                            requestedAudioQuality = result.requestedAudioQuality,
                            selectedAudioQuality = result.selectedAudioQuality,
                            availableAudioQualities = result.availableAudioQualities,
                            audioFallbackReason = result.audioFallbackReason,
                            qualityIds = result.qualityIds,
                            qualityLabels = result.qualityLabels,
                            switchableQualityIds = result.switchableQualityIds,
                            wasFallback = result.wasFallback,
                            currentPos = currentPos,
                            playWhenReady = playWhenReadyAfterSwitch,
                            playbackQualityMode = playbackQualityMode
                        ),
                        reason = QualityChangeReason.USER_EXPLICIT,
                        cdnSelection = cdnSelection,
                        sourceAlreadyApplied = false
                    )
                    telemetryAttempt?.applied(result.actualQuality, result.wasFallback)
                    pollQualitySwitchTelemetry()
                } else {
                    finishAttempt("failed", "no_playable_quality")
                    _uiState.value = current.copy(
                        isQualitySwitching = false,
                        requestedQuality = null,
                        pendingPlaybackTransitionPositionMs = null
                    )
                    showQualitySwitchFailureDialog(
                        requestedQualityId = qualityId,
                        hasCachedDashTracks = hasCachedDashTracks,
                        cacheContainsRequestedQuality = cacheContainsRequestedQuality
                    )
                }
            } catch (e: CancellationException) {
                finishAttempt("canceled", "request_canceled")
                if (isCurrentRequest()) {
                    _uiState.value = current.copy(
                        isQualitySwitching = false,
                        requestedQuality = null,
                        pendingPlaybackTransitionPositionMs = null
                    )
                }
                throw e
            } catch (e: Exception) {
                finishAttempt("failed", com.android.purebilibili.core.util.loadErrorCategory(VideoLoadError.fromException(e)))
                if (!isCurrentRequest()) return@launch
                _uiState.value = current.copy(
                    isQualitySwitching = false,
                    requestedQuality = null,
                    pendingPlaybackTransitionPositionMs = null
                )
                showQualitySwitchFailureDialog(
                    requestedQualityId = qualityId,
                    loadError = VideoLoadError.fromException(e)
                )
            }
        }
    }
    
    // ========== Page Switch ==========

    internal fun setPageIdentityCommitListener(listener: ((String, Long) -> Unit)?) {
        onPageIdentityCommitted = listener
    }
    
    fun switchPage(
        pageIndex: Int,
        ignoreSavedProgress: Boolean = false,
        autoPlay: Boolean = true,
    ) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val page = current.info.pages.getOrNull(pageIndex) ?: return
        // The playurl API identifies a part by the bvid/cid pair. Keep both values from the
        // same detail snapshot so a stale mutable session field cannot request another video's
        // stream while the UI is already showing the newly loaded detail.
        val targetBvid = current.info.bvid
        // currentCid used to be changed before the play-url request completed. A second tap was
        // then mistaken for the current page, while concurrent requests could commit/rollback in
        // any order. Keep the committed identity unchanged and allow only the latest request to win.
        if (page.cid == current.info.cid && pendingPageSwitchCid == null) {
            toast("\u5df2\u662f\u5f53\u524d\u5206P")
            return
        }
        if (page.cid == pendingPageSwitchCid && pageSwitchJob?.isActive == true) return
        playbackRecoveryGeneration++
        pageSwitchJob?.cancel()
        val switchGeneration = ++pageSwitchGeneration
        pendingPageSwitchCid = page.cid
        playbackCoordinator.dismissResumeSuggestion()
        subtitleLoadToken += 1
        val subtitleClearedState = clearTransientPlaybackPreviewData(clearSubtitleFields(current))
        val previousCid = currentCid
        if (targetBvid.isNotEmpty() && previousCid > 0L) {
            flushPlaybackHeartbeatSnapshot(reason = "switch_page")
            playbackUseCase.savePosition(targetBvid, previousCid)
        }
        _uiState.value = subtitleClearedState.copy(
            isQualitySwitching = true,
            pendingPlaybackTransitionPositionMs = 0L
        )
        
        pageSwitchJob = viewModelScope.launch {
            try {
                val playUrlData = VideoRepository.getPlayUrlDataForPlaybackTransition(
                    bvid = targetBvid,
                    cid = page.cid,
                    qn = current.currentQuality,
                    audioLang = current.currentAudioLang
                )
                if (playUrlData != null) {
                    //  [新增] 获取音频/视频偏好
                    val settingsCodecPreference = appContext?.let { 
                        com.android.purebilibili.core.store.SettingsManager.getVideoCodecSync(it) 
                    } ?: HEVC_CODEC_KEY
                    val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
                    val videoCodecPreference = resolveEffectiveVideoCodecPreference(
                        requestCodecOverride = null,
                        settingsCodecPreference = settingsCodecPreference,
                        sessionBlockedCodecs = sessionBlockedCodecs
                    )
                    val videoSecondCodecPreference = appContext?.let {
                        com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodecSync(it)
                    } ?: AVC_CODEC_KEY
                    val audioQualityPreference = appContext?.let { context ->
                        resolveRequestedAudioQuality(
                            defaultAudioQuality = com.android.purebilibili.core.store.player.PlayerSettingsStore
                                .getCachedDefaultAudioQuality(context),
                            rememberedAudioQuality = com.android.purebilibili.core.store.player.PlayerSettingsStore
                                .getCachedLastSelectedAudioQuality(context)
                        )
                    } ?: -1
                    
                    val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
                    val isAv1Supported = resolveEffectiveAv1Support(
                        deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                        sessionBlockedCodecs = sessionBlockedCodecs
                    )
                    
                    val selection = playbackUseCase.resolvePlaybackSelection(
                        playUrlData = playUrlData,
                        targetQuality = current.currentQuality,
                        audioQualityPreference = audioQualityPreference,
                        videoCodecPreference = videoCodecPreference,
                        videoSecondCodecPreference = videoSecondCodecPreference,
                        playbackQualityMode = current.playbackQualityMode,
                        isHevcSupported = isHevcSupported,
                        isAv1Supported = isAv1Supported
                    )
                    val restoredPosition = resolvePageSwitchStartPositionMs(
                        cachedPositionMs = playbackUseCase.getCachedPosition(targetBvid, page.cid),
                        pageDurationSeconds = page.duration,
                        ignoreSavedProgress = ignoreSavedProgress
                    )
                    
                    if (selection != null) {
                        if (switchGeneration != pageSwitchGeneration) return@launch
                        val cdnSelection = resolvePlaybackCdnCandidateSelection(
                            videoUrl = selection.videoUrl,
                            audioUrl = selection.audioUrl,
                            quality = selection.actualQuality,
                            cachedDashVideos = selection.cachedDashVideos,
                            cachedDashAudios = selection.cachedDashAudios,
                            adaptiveDashSource = selection.adaptiveDashSource
                        )
                        val switchedState = subtitleClearedState.copy(
                            info = current.info.copy(cid = page.cid), playUrl = cdnSelection.playUrl, audioUrl = cdnSelection.audioUrl,
                            startPosition = restoredPosition, isQualitySwitching = false,
                            pendingPlaybackTransitionPositionMs = restoredPosition.coerceAtLeast(0L),
                            currentQuality = selection.actualQuality,
                            adaptiveDashSource = cdnSelection.adaptiveDashSource,
                            qualityIds = selection.qualityIds,
                            qualityLabels = selection.qualityLabels,
                            switchableQualityIds = selection.switchableQualityIds,
                            cachedDashVideos = selection.cachedDashVideos,
                            cachedDashAudios = selection.cachedDashAudios,
                            cachedDash = selection.cachedDash,
                            requestedAudioQuality = selection.requestedAudioQuality,
                            selectedAudioQuality = selection.selectedAudioQuality,
                            availableAudioQualities = selection.availableAudioQualities,
                            audioFallbackReason = selection.audioFallbackReason,
                            currentCdnIndex = 0,
                            allVideoUrls = cdnSelection.allVideoUrls,
                            allAudioUrls = cdnSelection.allAudioUrls,
                            cdnCandidateSources = cdnSelection.candidateSources,
                            cdnLineDiagnostics = cdnSelection.lineDiagnostics
                        )
                        // Commit both the media identity and its UI state before replacing the
                        // player source. Media3 may dispatch synchronous source callbacks; those
                        // callbacks must already observe the target page rather than stale P1.
                        // Automatic continuation and media controls also call switchPage directly.
                        // Notify the mounted detail screen for every successful switch, otherwise
                        // its internal-CID guard can reload the previous part after auto-advance.
                        onPageIdentityCommitted?.invoke(targetBvid, page.cid)
                        currentCid = page.cid
                        _uiState.value = switchedState
                        playResolvedPlayback(
                            videoUrl = cdnSelection.playUrl,
                            audioUrl = cdnSelection.audioUrl,
                            adaptiveDashSource = cdnSelection.adaptiveDashSource,
                            startPositionMs = restoredPosition,
                            playWhenReady = autoPlay,
                            cdnFallbackState = cdnSelection.fallbackState,
                            cdnCacheKeysByUrl = cdnSelection.cdnCacheKeysByUrl
                        )
                        publishSubjectSnapshot(switchedState)
                        monitorPlaybackTransitionPosition(restoredPosition.coerceAtLeast(0L))
                        startHeartbeat()
                        interactiveCurrentEdgeId = 0L
                        loadPlayerInfo(targetBvid, page.cid)
                        loadVideoshot(targetBvid, page.cid)
                        toast("\u5df2\u5207\u6362\u81f3 P${pageIndex + 1}")
                        return@launch
                    }
                }
                if (switchGeneration != pageSwitchGeneration) return@launch
                currentCid = previousCid
                _uiState.value = current.copy(
                    isQualitySwitching = false,
                    pendingPlaybackTransitionPositionMs = null
                )
                toast("\u5206P\u5207\u6362\u5931\u8d25")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (switchGeneration != pageSwitchGeneration) return@launch
                currentCid = previousCid
                _uiState.value = current.copy(
                    isQualitySwitching = false,
                    pendingPlaybackTransitionPositionMs = null
                )
                toast("\u5206P\u5207\u6362\u5931\u8d25")
            } finally {
                if (switchGeneration == pageSwitchGeneration) {
                    pendingPageSwitchCid = null
                    pageSwitchJob = null
                }
            }
        }
    }

    fun dismissResumePlaybackSuggestion() {
        playbackCoordinator.dismissResumeSuggestion()
    }

    fun continueResumePlaybackSuggestion() {
        val suggestion = playbackCoordinator.consumeResumeSuggestion() ?: return

        val current = _uiState.value as? VideoPlaybackUiState.Success
        if (current != null &&
            current.info.bvid == suggestion.targetBvid &&
            current.info.pages.isNotEmpty()
        ) {
            val pageIndex = current.info.pages.indexOfFirst { page ->
                page.cid == suggestion.targetCid
            }
            if (pageIndex >= 0) {
                switchPage(pageIndex)
                return
            }
        }

        markInPageInitiatedPlayback(suggestion.targetBvid, suggestion.targetCid)
        loadVideo(
            bvid = suggestion.targetBvid,
            cid = suggestion.targetCid,
            autoPlay = true
        )
    }

    private fun maybeEmitResumePlaybackSuggestion(
        requestCid: Long,
        loadedInfo: ViewInfo
    ) {
        val context = appContext
        val promptEnabled = context?.let {
            com.android.purebilibili.core.store.SettingsManager.getResumePlaybackPromptEnabledSync(it)
        } ?: true

        playbackCoordinator.refreshResumeSuggestion(
            requestCid = requestCid,
            loadedInfo = loadedInfo,
            promptEnabled = promptEnabled,
            hasPromptedBefore = { key ->
                context?.let {
                    com.android.purebilibili.core.store.SettingsManager.hasResumePlaybackPromptShown(it, key)
                } ?: false
            },
            markPromptShown = { promptKey ->
                context?.let {
                    com.android.purebilibili.core.store.SettingsManager.markResumePlaybackPromptShown(
                        context = it,
                        promptKey = promptKey
                    )
                }
            },
            progressLookup = { bvid, cid ->
                playbackUseCase.getCachedPosition(bvid, cid)
            }
        )
    }

    private suspend fun switchToInteractiveCid(targetCid: Long, targetEdgeId: Long? = null): Boolean {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return false
        if (targetCid <= 0L) return false
        if (targetCid == currentCid) {
            val edgeId = targetEdgeId?.takeIf { it > 0L } ?: interactiveCurrentEdgeId.takeIf { it > 0L }
            if (edgeId == null || interactiveGraphVersion <= 0L || current.info.isSteinGate != 1) return false

            var applied = false
            VideoRepository.getInteractEdgeInfo(
                bvid = current.info.bvid,
                graphVersion = interactiveGraphVersion,
                edgeId = edgeId
            ).onSuccess { data ->
                val resolvedEdgeId = data.edgeId.takeIf { it > 0L } ?: edgeId
                val startPositionMs = resolveInteractiveEdgeStartPositionMs(data, resolvedEdgeId)
                if (startPositionMs >= 0L) {
                    playbackUseCase.seekTo(startPositionMs)
                }
                processInteractiveEdgeData(current, data)
                applied = true
            }.onFailure { e ->
                Logger.w("PlayerVM", "Interactive same-cid edge load failed: ${e.message}")
            }
            return applied
        }

        return try {
            val playUrlData = VideoRepository.getPlayUrlData(
                bvid = currentBvid,
                cid = targetCid,
                qn = current.currentQuality,
                audioLang = current.currentAudioLang
            ) ?: return false

            val settingsCodecPreference = appContext?.let {
                com.android.purebilibili.core.store.SettingsManager.getVideoCodecSync(it)
            } ?: HEVC_CODEC_KEY
            val sessionBlockedCodecs = playbackSessionStore.state.value.blockedVideoCodecs
            val videoCodecPreference = resolveEffectiveVideoCodecPreference(
                requestCodecOverride = null,
                settingsCodecPreference = settingsCodecPreference,
                sessionBlockedCodecs = sessionBlockedCodecs
            )
            val videoSecondCodecPreference = appContext?.let {
                com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodecSync(it)
            } ?: AVC_CODEC_KEY
            val audioQualityPreference = current.requestedAudioQuality

            val isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupported()
            val isAv1Supported = resolveEffectiveAv1Support(
                deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                sessionBlockedCodecs = sessionBlockedCodecs
            )

            val selection = playbackUseCase.resolvePlaybackSelection(
                playUrlData = playUrlData,
                targetQuality = current.currentQuality,
                audioQualityPreference = audioQualityPreference,
                videoCodecPreference = videoCodecPreference,
                videoSecondCodecPreference = videoSecondCodecPreference,
                playbackQualityMode = current.playbackQualityMode,
                isHevcSupported = isHevcSupported,
                isAv1Supported = isAv1Supported
            ) ?: return false

            val cdnSelection = resolvePlaybackCdnCandidateSelection(
                videoUrl = selection.videoUrl,
                audioUrl = selection.audioUrl,
                quality = selection.actualQuality,
                cachedDashVideos = selection.cachedDashVideos,
                cachedDashAudios = selection.cachedDashAudios,
                adaptiveDashSource = selection.adaptiveDashSource
            )
            playResolvedPlayback(
                videoUrl = cdnSelection.playUrl,
                audioUrl = cdnSelection.audioUrl,
                adaptiveDashSource = cdnSelection.adaptiveDashSource,
                startPositionMs = 0L,
                cdnFallbackState = cdnSelection.fallbackState,
                cdnCacheKeysByUrl = cdnSelection.cdnCacheKeysByUrl
            )

            currentCid = targetCid
            subtitleLoadToken += 1
            _uiState.value = clearTransientPlaybackPreviewData(clearSubtitleFields(current)).copy(
                info = current.info.copy(cid = targetCid),
                playUrl = cdnSelection.playUrl,
                audioUrl = cdnSelection.audioUrl,
                startPosition = 0L,
                adaptiveDashSource = cdnSelection.adaptiveDashSource,
                videoDurationMs = playUrlData.timelength.coerceAtLeast(0L),
                qualityIds = selection.qualityIds,
                qualityLabels = selection.qualityLabels,
                switchableQualityIds = selection.switchableQualityIds,
                cachedDashVideos = selection.cachedDashVideos,
                cachedDashAudios = selection.cachedDashAudios,
                cachedDash = selection.cachedDash,
                requestedAudioQuality = selection.requestedAudioQuality,
                selectedAudioQuality = selection.selectedAudioQuality,
                availableAudioQualities = selection.availableAudioQualities,
                audioFallbackReason = selection.audioFallbackReason,
                currentCdnIndex = 0,
                allVideoUrls = cdnSelection.allVideoUrls,
                allAudioUrls = cdnSelection.allAudioUrls,
                cdnCandidateSources = cdnSelection.candidateSources,
                cdnLineDiagnostics = cdnSelection.lineDiagnostics
            )
            loadPlayerInfo(
                currentBvid,
                targetCid,
                preferredEdgeId = targetEdgeId ?: interactiveCurrentEdgeId.takeIf { it > 0L }
            )
            loadVideoshot(currentBvid, targetCid)
            true
        } catch (e: Exception) {
            Logger.w("PlayerVM", "switchToInteractiveCid failed: ${e.message}")
            false
        }
    }
    
    // ==========  Plugin System (SponsorBlock等) ==========

    /** Cached detail restoration bypasses the normal post-load plugin plan. */
    private fun restoreSponsorPlaybackState(state: VideoPlaybackUiState.Success) {
        val sponsorEnabled = getSessionPlayerPlugins().any {
            it is com.android.purebilibili.feature.plugin.SponsorBlockPlugin
        }
        if (sponsorEnabled && state.sponsorVideoLabel.isNotEmpty()) {
            val key = state.info.bvid to state.info.cid
            sponsorVideoLabels.update { labels ->
                if (labels.containsKey(key)) labels else {
                    (labels + (key to state.sponsorVideoLabel)).entries.toList().takeLast(16)
                        .associate { it.key to it.value }
                }
            }
        }
        // Repeated reconciliation must not cancel an in-flight segment request.
        if (pluginCheckJob?.isActive != true) startPluginCheck()
    }

    private suspend fun relatedVideoLoadSnapshot(bvid: String): RelatedVideoLoadSnapshot {
        val player = exoPlayer
        val prepared = relatedPlaybackPreparedToken == currentLoadRequestToken
        return RelatedVideoLoadSnapshot(
            requestToken = currentLoadRequestToken,
            constrainedNetwork = videoNetworkPressureObserver.isConstrained(),
            hasCachedRelated = VideoRepository.getCachedRelatedVideosResult(bvid) != null,
            playbackPrepared = prepared,
            firstFrameRendered = prepared && relatedFirstFrameToken == currentLoadRequestToken,
            playbackReady = prepared && player?.playbackState == Player.STATE_READY,
            buffering = prepared && player?.playbackState == Player.STATE_BUFFERING,
            playWhenReady = player?.playWhenReady ?: true,
            bufferedDurationMs = if (prepared && player != null) {
                (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            } else 0L,
            playbackFailed = _uiState.value is VideoPlaybackUiState.Error || (prepared && player?.playerError != null),
            playbackEnded = prepared && player?.playbackState == Player.STATE_ENDED
        )
    }

    private fun loadRelatedVideosForRequest(
        bvid: String,
        requestToken: Long
    ) {
        relatedLoadJob?.cancel()
        relatedLoadJob = viewModelScope.launch {
            repeat(2) { attempt ->
                if (currentLoadRequestToken != requestToken) return@launch
                val canLoadRelated = relatedLoadScheduler.awaitPermit(
                    requestToken = requestToken,
                    snapshot = { relatedVideoLoadSnapshot(bvid) },
                    onDeferred = { logVideoLoadStage(VideoLoadStage.RELATED_DEFERRED, requestToken) }
                )
                if (!canLoadRelated || currentLoadRequestToken != requestToken) return@launch
                logVideoLoadStage(VideoLoadStage.RELATED_REQUEST_STARTED, requestToken)
                val result = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    VideoRepository.getRelatedVideosResult(bvid)
                } ?: Result.failure(IllegalStateException("相关推荐请求超时"))
                if (result.isFailure && attempt == 0) {
                    delay(500L)
                    return@repeat
                }
                val related = result.getOrDefault(emptyList())
                val loadState = if (result.isSuccess) VideoPlaybackUiState.RelatedLoadState.READY
                    else VideoPlaybackUiState.RelatedLoadState.FAILED
                if (currentLoadRequestToken != requestToken) return@launch
                _detailState.update { state ->
                    if (state.requestToken == requestToken) {
                        state.copy(related = related, relatedLoadState = loadState)
                    } else state
                }
                logVideoLoadStage(VideoLoadStage.RELATED_RESULT, requestToken)
                _uiState.update { state ->
                    if (state is VideoPlaybackUiState.Success &&
                        state.info.bvid == bvid &&
                        currentLoadRequestToken == requestToken
                    ) state.copy(related = related, relatedLoadState = loadState) else state
                }
                val latest = _uiState.value as? VideoPlaybackUiState.Success ?: return@launch
                if (latest.info.bvid == bvid &&
                    currentLoadRequestToken == requestToken && result.isSuccess
                ) updatePlaylist(latest.info, related)
                return@launch
            }
        }
    }

    private fun scheduleDeferredPostLoadWork(
        loadedBvid: String,
        loadedCid: Long,
        loadedAid: Long,
        loadedOwnerMid: Long,
        isLoggedIn: Boolean,
        requestToken: Long
    ) {
        val context = appContext
        val shouldShowOnlineCount = context?.let {
            com.android.purebilibili.core.store.SettingsManager
                .getShowOnlineCountSync(it)
        } ?: false
        if (!shouldShowOnlineCount) {
            onlineCountJob?.cancel()
            _uiState.update { current ->
                if (current is VideoPlaybackUiState.Success) {
                    current.copy(onlineCount = "")
                } else current
            }
        }

        buildPlaybackPostLoadPlan(
            isLoggedIn = isLoggedIn,
            shouldShowOnlineCount = shouldShowOnlineCount
        )
            .groupBy { spec -> spec.delayMs }
            .toSortedMap()
            .forEach { (delayMs, tasks) ->
                viewModelScope.launch {
                    delay(delayMs)
                    val currentSuccess = _uiState.value as? VideoPlaybackUiState.Success ?: return@launch
                    if (currentLoadRequestToken != requestToken ||
                        currentSuccess.info.bvid != loadedBvid || currentSuccess.info.cid != loadedCid) {
                        return@launch
                    }
                    tasks.forEach { spec ->
                        when (spec.task) {
                            PlaybackPostLoadTask.PLAYER_INFO -> loadPlayerInfo(
                                bvid = loadedBvid,
                                cid = loadedCid,
                                requestToken = requestToken
                            )
                            PlaybackPostLoadTask.VIDEO_SHOT -> loadVideoshot(loadedBvid, loadedCid)
                            PlaybackPostLoadTask.REFRESH_DEFERRED_SIGNALS -> refreshDeferredPlaybackSignals(
                                bvid = loadedBvid,
                                aid = loadedAid,
                                ownerMid = loadedOwnerMid,
                                requestToken = requestToken
                            )
                            PlaybackPostLoadTask.LOAD_FOLLOWING_MIDS -> loadFollowingMids()
                            PlaybackPostLoadTask.OWNER_STATS -> loadOwnerStats(
                                bvid = loadedBvid,
                                ownerMid = loadedOwnerMid
                            )
                            PlaybackPostLoadTask.VIDEO_TAGS -> loadVideoTags(loadedBvid)
                            PlaybackPostLoadTask.AI_SUMMARY -> loadAiSummary(
                                loadedBvid,
                                loadedCid,
                                loadedOwnerMid
                            )
                            PlaybackPostLoadTask.ONLINE_COUNT -> startOnlineCountPolling(
                                loadedBvid,
                                loadedCid
                            )
                            PlaybackPostLoadTask.HEARTBEAT -> startHeartbeat()
                            PlaybackPostLoadTask.PLUGIN_ON_VIDEO_LOAD -> {
                                sponsorContributionRequest = null
                                _sponsorContributionUiState.value = SponsorContributionUiState()
                                sponsorBlockLoadedVideo = null
                                refreshSponsorContributionAvailability()
                            }
                            PlaybackPostLoadTask.START_PLUGIN_CHECK -> startPluginCheck()
                        }
                    }
                }
            }
    }

    private fun finishQualitySwitchTelemetry(outcome: String, reason: String) {
        val pending = pendingQualityTelemetry ?: return
        pendingQualityTelemetry = null
        if (!AnalyticsHelper.isCollectionEnabled() ||
            pending.collectionGeneration != AnalyticsHelper.collectionGeneration()
        ) return
        val isCurrent = pending.bvid == currentBvid && pending.cid == currentCid &&
            pending.requestToken == currentLoadRequestToken
        pending.attempt.finish(
            if (isCurrent) outcome else "canceled",
            if (isCurrent) reason else "media_changed",
            android.os.SystemClock.elapsedRealtime()
        )?.let(AnalyticsHelper::logQualitySwitchResult)
    }

    private fun pollQualitySwitchTelemetry() {
        val pending = pendingQualityTelemetry ?: return
        if (!AnalyticsHelper.isCollectionEnabled() ||
            pending.collectionGeneration != AnalyticsHelper.collectionGeneration()
        ) {
            pendingQualityTelemetry = null
            return
        }
        val result = pending.attempt.poll(
            nowMs = android.os.SystemClock.elapsedRealtime(),
            isCurrent = pending.bvid == currentBvid && pending.cid == currentCid &&
                pending.requestToken == currentLoadRequestToken,
            ready = exoPlayer?.playbackState == Player.STATE_READY && exoPlayer?.playerError == null
        ) ?: return
        pendingQualityTelemetry = null
        AnalyticsHelper.logQualitySwitchResult(result)
    }

    /** Sample locally using existing player callbacks and the plugin polling tick. */
    private fun samplePlaybackAnalytics(
        discontinuity: Boolean = false,
        ended: Boolean = false,
        positionMs: Long? = null
    ) {
        if (!AnalyticsHelper.isCollectionEnabled()) {
            playbackAnalyticsTracker.reset()
            return
        }
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        if (current.info.bvid != currentBvid || current.info.cid != currentCid) return
        val player = exoPlayer ?: return
        val durationMs = player.duration
        val update = playbackAnalyticsTracker.sample(
            key = "${current.info.bvid}:${current.info.cid}",
            positionMs = positionMs ?: player.currentPosition,
            durationMs = durationMs,
            isPlaying = player.isPlaying,
            nowMs = android.os.SystemClock.elapsedRealtime(),
            discontinuity = discontinuity,
            ended = ended
        )
        if (update.started) {
            AnalyticsHelper.logVideoPlay(
                videoId = current.info.bvid,
                title = "",
                duration = durationMs / 1000L
            )
        }
        update.checkpoints.forEach { checkpoint ->
            AnalyticsHelper.logVideoProgress(
                videoId = "${current.info.bvid}:${current.info.cid}",
                progress = checkpoint,
                watchTime = update.watchTimeSeconds
            )
        }
        if (update.completed) {
            AnalyticsHelper.logVideoComplete(current.info.bvid, update.watchTimeSeconds)
        }
    }

    private fun startPluginCheck() {
        pluginCheckJob?.cancel()
        lastPluginDispatchPositionMs = null
        pluginCheckJob = viewModelScope.launch {
            while (true) {
                // Reuse the existing progress tick, including sessions without active plugins.
                samplePlaybackAnalytics()
                pollQualitySwitchTelemetry()
                if (CdnTransferRuntime.enabled) {
                    exoPlayer?.let { player ->
                        CdnTransferRuntime.playback(
                            (player.bufferedPosition - player.currentPosition).coerceAtLeast(0),
                            (player.videoFormat?.bitrate?.coerceAtLeast(0)?.toLong() ?: 0L) +
                                (player.audioFormat?.bitrate?.coerceAtLeast(0)?.toLong() ?: 0L)
                        )
                    }
                }
                val plugins = getSessionPlayerPlugins()
                refreshSponsorContributionAvailability(plugins)
                val currentVideo = _uiState.value as? VideoPlaybackUiState.Success
                val sponsorPlugin = plugins
                    .filterIsInstance<com.android.purebilibili.feature.plugin.SponsorBlockPlugin>()
                    .firstOrNull()
                if (sponsorPlugin == null) {
                    sponsorBlockLoadedVideo = null
                    sponsorBlockRetryVideo = null
                    sponsorBlockLoadFailures = 0
                    sponsorBlockRetryAtMs = 0L
                    sponsorPlaybackPlugin.onVideoEnd()
                    _sponsorProgressMarkers.value = emptyList()
                    clearSponsorSkipUi()
                    sponsorVideoLabels.value = emptyMap()
                    currentVideo?.let { updateSponsorVideoLabel(it.info.bvid, it.info.cid, emptyList()) }
                } else if (currentVideo != null) {
                    val bvid = currentVideo.info.bvid
                    val cid = currentVideo.info.cid
                    val videoKey = bvid to cid
                    if (sponsorBlockRetryVideo != videoKey) {
                        sponsorBlockRetryVideo = videoKey
                        sponsorBlockLoadFailures = 0
                        sponsorBlockRetryAtMs = 0L
                        _sponsorProgressMarkers.value = emptyList()
                        clearSponsorSkipUi()
                    }
                    if (sponsorBlockLoadedVideo != videoKey &&
                        android.os.SystemClock.elapsedRealtime() >= sponsorBlockRetryAtMs
                    ) {
                        try {
                            sponsorPlugin.onVideoLoad(bvid, cid)
                            sponsorBlockLoadedVideo = videoKey
                            sponsorBlockLoadFailures = 0
                            sponsorBlockRetryAtMs = 0L
                            val stillCurrent = _uiState.value as? VideoPlaybackUiState.Success
                            if (stillCurrent?.info?.bvid == bvid && stillCurrent.info.cid == cid) {
                                _sponsorProgressMarkers.value = sponsorPlugin.getProgressMarkers()
                                updateSponsorVideoLabel(bvid, cid, sponsorPlugin.getSegments())
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            sponsorBlockLoadFailures += 1
                            val retryDelayMs = (2_000L shl (sponsorBlockLoadFailures - 1).coerceAtMost(4))
                                .coerceAtMost(30_000L)
                            sponsorBlockRetryAtMs = android.os.SystemClock.elapsedRealtime() + retryDelayMs
                            Logger.e("PlayerVM", "SponsorBlock $bvid/$cid load failed; retry in ${retryDelayMs}ms", e)
                        }
                    }
                }
                if (plugins.none { it is com.android.purebilibili.feature.plugin.SponsorBlockPlugin } &&
                    _sponsorProgressMarkers.value.isNotEmpty()
                ) {
                    _sponsorProgressMarkers.value = emptyList()
                    clearSponsorSkipUi()
                }
                val intervalMs = resolvePluginPollingIntervalMs(
                    hasPlugins = plugins.isNotEmpty(),
                    isPlaying = exoPlayer?.isPlaying == true
                )
                delay(intervalMs)
                if (plugins.isEmpty()) continue

                val currentPos = playbackUseCase.getCurrentPosition()
                val restoreAt = sponsorMuteRestoreAtMs
                if (restoreAt != null && currentPos >= restoreAt) {
                    sponsorMutedOriginalVolume?.let { exoPlayer?.volume = it }
                    sponsorMutedOriginalVolume = null
                    sponsorMuteRestoreAtMs = null
                }
                if (!shouldDispatchPluginPositionUpdate(
                        lastDispatchedPositionMs = lastPluginDispatchPositionMs,
                        currentPositionMs = currentPos
                    )
                ) {
                    continue
                }
                lastPluginDispatchPositionMs = currentPos
                
                for (plugin in plugins) {
                    if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                        val activeVideo = _uiState.value as? VideoPlaybackUiState.Success
                        val activeKey = activeVideo?.let { it.info.bvid to it.info.cid }
                        if (activeKey == null || sponsorBlockLoadedVideo != activeKey) continue
                    }
                    try {
                        when (val action = plugin.onPositionUpdate(currentPos)) {
                            is SkipAction.SkipTo -> {
                                val snapshot = buildSponsorBlockVideoSnapshot(_uiState.value)
                                val playerDurationMs = playbackUseCase.getDuration()
                                val resolvedTargetPositionMs = resolveSponsorBlockSkipTargetPositionMs(
                                    requestedPositionMs = action.positionMs,
                                    durationMs = playerDurationMs,
                                    category = action.category,
                                )
                                clearSponsorSkipUi()
                                playbackUseCase.seekTo(
                                    position = resolvedTargetPositionMs,
                                    resumePlayback = shouldResumePlaybackAfterSponsorBlockSkip(
                                        playWhenReadyBeforeSkip = exoPlayer?.playWhenReady == true
                                    )
                                )
                                recordSponsorBlockSkip(
                                    snapshot = snapshot,
                                    segmentId = action.segmentId,
                                    segmentCategoryName = action.categoryName,
                                    startMs = action.startMs,
                                    endMs = resolvedTargetPositionMs,
                                    trigger = SponsorBlockSkipTrigger.AUTO
                                )
                                if (action.showToast) toast(action.reason)
                                Logger.d(
                                    "PlayerVM",
                                    "Plugin ${plugin.name} skip: requested=${action.positionMs}ms, " +
                                        "resolved=${resolvedTargetPositionMs}ms, duration=${playerDurationMs}ms, " +
                                        "category=${action.category}, state=${exoPlayer?.playbackState}, " +
                                        "playWhenReady=${exoPlayer?.playWhenReady}"
                                )
                            }
                            is SkipAction.ShowButton -> {
                                val nextUiState = reduceSponsorSkipUiState(
                                    previous = _sponsorSkipUiState.value,
                                    action = action
                                )
                                _sponsorSkipUiState.value = nextUiState
                                _showSkipButton.value = nextUiState.visible
                                _currentSkipReason.value = action.label
                                if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                                    _currentSponsorSegment.value = plugin.getActiveSegment()
                                }
                            }
                            is SkipAction.Mute -> {
                                if (sponsorMutedOriginalVolume == null) {
                                    sponsorMutedOriginalVolume = exoPlayer?.volume
                                    exoPlayer?.volume = 0f
                                    if (action.showToast) toast(action.reason)
                                }
                                sponsorMuteRestoreAtMs = action.untilMs
                            }
                            SkipAction.None -> {
                                clearSponsorSkipUi()
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Logger.e("PlayerVM", "Plugin ${plugin.name} onPositionUpdate failed", e)
                    }
                }
            }
        }
    }
    
    fun dismissSponsorSkipButton() {
        getSessionPlayerPlugins().forEach { plugin ->
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                plugin.markAsSkipped(_sponsorSkipUiState.value.segmentId ?: return@forEach)
            }
        }
        clearSponsorSkipUi()
    }

    /** Explicit voting is only available for the segment the user is currently reviewing. */
    fun voteCurrentSponsorSegment(voteType: Int) {
        val segmentId = _sponsorSkipUiState.value.segmentId ?: return
        val plugin = getSessionPlayerPlugins()
            .filterIsInstance<com.android.purebilibili.feature.plugin.SponsorBlockPlugin>()
            .firstOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            plugin.voteOnCommunitySegment(segmentId, voteType)
                .onSuccess { toast("已提交社区投票") }
                .onFailure { error -> toast(error.message ?: "社区投票失败") }
        }
    }

    fun skipCurrentSponsorSegment() {
        val targetPosition = _sponsorSkipUiState.value.skipToMs.takeIf { it > 0L } ?: return
        val segmentId = _sponsorSkipUiState.value.segmentId
        val snapshot = buildSponsorBlockVideoSnapshot(_uiState.value)
        var segmentCategory: String? = null
        getSessionPlayerPlugins().forEach { plugin ->
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin && segmentId != null) {
                val segment = plugin.markAsSkipped(segmentId)
                segmentCategory = segment?.category
                recordSponsorBlockSkip(
                    snapshot = snapshot,
                    segmentId = segment?.UUID ?: segmentId,
                    segmentCategoryName = segment?.categoryName,
                    startMs = segment?.startTimeMs,
                    endMs = segment?.endTimeMs ?: targetPosition,
                    trigger = SponsorBlockSkipTrigger.MANUAL
                )
            }
        }
        val resolvedTargetPosition = resolveSponsorBlockSkipTargetPositionMs(
            requestedPositionMs = targetPosition,
            durationMs = playbackUseCase.getDuration(),
            category = segmentCategory,
        )
        playbackUseCase.seekTo(
            position = resolvedTargetPosition,
            resumePlayback = shouldResumePlaybackAfterSponsorBlockSkip(
                playWhenReadyBeforeSkip = exoPlayer?.playWhenReady == true
            )
        )
        clearSponsorSkipUi()
    }

    private fun recordSponsorBlockSkip(
        snapshot: SponsorBlockVideoSnapshot?,
        segmentId: String?,
        segmentCategoryName: String?,
        startMs: Long?,
        endMs: Long,
        trigger: SponsorBlockSkipTrigger
    ) {
        val context = appContext ?: return
        val capturedSnapshot = snapshot ?: return
        val capturedSegmentId = segmentId ?: return
        val record = buildSponsorBlockSkipRecord(
            snapshot = capturedSnapshot,
            segmentId = capturedSegmentId,
            segmentCategoryName = segmentCategoryName.orEmpty().ifBlank { "可跳过片段" },
            startMs = startMs ?: 0L,
            endMs = endMs,
            trigger = trigger,
            timestampMs = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SponsorBlockInsightStore.appendRecord(context, record)
            }.onFailure { error ->
                Logger.w("PlayerVM", "记录空降助手跳过历史失败: ${error.message}")
            }
            getSessionPlayerPlugins()
                .filterIsInstance<com.android.purebilibili.feature.plugin.SponsorBlockPlugin>()
                .forEach { plugin ->
                    plugin.uploadViewedSegmentIfEnabled(capturedSegmentId)
                }
        }
    }

    fun notifyPluginsOfExplicitSeek(positionMs: Long) {
        getSessionPlayerPlugins().forEach { plugin ->
            plugin.onUserSeek(positionMs)
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                _currentSponsorSegment.value = plugin.getActiveSegment()
            }
        }
        val currentSegment = _currentSponsorSegment.value
        if (currentSegment != null) {
            val action = SkipAction.ShowButton(
                skipToMs = currentSegment.endTimeMs,
                label = "跳过${currentSegment.categoryName}",
                segmentId = currentSegment.UUID
            )
            val nextUiState = reduceSponsorSkipUiState(_sponsorSkipUiState.value, action)
            _sponsorSkipUiState.value = nextUiState
            _showSkipButton.value = nextUiState.visible
            _currentSkipReason.value = action.label
        } else {
            clearSponsorSkipUi()
        }
    }

    private fun clearSponsorSkipUi() {
        _sponsorSkipUiState.value = reduceSponsorSkipUiState(_sponsorSkipUiState.value, SkipAction.None)
        _showSkipButton.value = false
        _currentSkipReason.value = null
        _currentSponsorSegment.value = null
    }

    fun markSponsorContributionBoundary() {
        val currentState = _sponsorContributionUiState.value
        val context = resolveSponsorContributionContext() ?: run {
            _sponsorContributionUiState.value = SponsorContributionUiState(
                phase = SponsorContributionPhase.HIDDEN,
                message = "请先启用空降助手的社区投稿，并等待视频信息加载完成",
            )
            return
        }
        val currentPositionMs = playbackUseCase.getCurrentPosition().coerceAtLeast(0L)
        when (currentState.phase) {
            SponsorContributionPhase.READY -> {
                _sponsorContributionUiState.value = SponsorContributionUiState(
                    phase = SponsorContributionPhase.MARKING,
                    startMs = currentPositionMs,
                    category = currentState.category,
                    serverBaseUrl = context.plugin.getCommunityServerBaseUrl(),
                    message = "已记录起点，再次点按结束标记",
                )
            }

            SponsorContributionPhase.MARKING -> {
                val startMs = currentState.startMs ?: return
                if (currentPositionMs <= startMs) {
                    _sponsorContributionUiState.value = currentState.copy(
                        message = "结束时间需要晚于起点",
                    )
                    return
                }
                sponsorContributionRequest = context.copy(startMs = startMs, endMs = currentPositionMs)
                _sponsorContributionUiState.value = currentState.copy(
                    phase = SponsorContributionPhase.REVIEW,
                    endMs = currentPositionMs,
                    serverBaseUrl = context.plugin.getCommunityServerBaseUrl(),
                    message = null,
                )
            }

            else -> Unit
        }
    }

    fun markWholeVideoAsSponsor() {
        val context = resolveSponsorContributionContext() ?: run {
            _sponsorContributionUiState.value = SponsorContributionUiState(
                phase = SponsorContributionPhase.HIDDEN,
                message = "请先启用空降助手的社区投稿，并等待视频信息加载完成",
            )
            return
        }
        sponsorContributionRequest = context.copy(startMs = 0L, endMs = 0L)
        _sponsorContributionUiState.value = SponsorContributionUiState(
            phase = SponsorContributionPhase.REVIEW,
            startMs = 0L,
            endMs = 0L,
            category = SponsorCategory.SPONSOR,
            actionType = com.android.purebilibili.data.model.response.SponsorActionType.FULL,
            serverBaseUrl = context.plugin.getCommunityServerBaseUrl(),
        )
    }

    fun setSponsorContributionCategory(category: String) {
        if (category !in SponsorCategory.ALL_CATEGORIES) return
        val current = _sponsorContributionUiState.value
        if (current.phase != SponsorContributionPhase.REVIEW) return
        val allowedActionTypes = com.android.purebilibili.feature.plugin.sponsorBlockAllowedActionTypes(category)
        val actionType = if (
            current.actionType == com.android.purebilibili.data.model.response.SponsorActionType.FULL &&
            com.android.purebilibili.data.model.response.SponsorActionType.FULL in allowedActionTypes
        ) {
            com.android.purebilibili.data.model.response.SponsorActionType.FULL
        } else {
            allowedActionTypes.first()
        }
        _sponsorContributionUiState.value = current.copy(category = category, actionType = actionType, message = null)
    }

    fun setSponsorContributionActionType(actionType: String) {
        val current = _sponsorContributionUiState.value
        if (current.phase != SponsorContributionPhase.REVIEW) return
        if (actionType !in com.android.purebilibili.feature.plugin.sponsorBlockAllowedActionTypes(current.category)) return
        if (actionType == com.android.purebilibili.data.model.response.SponsorActionType.FULL) {
            sponsorContributionRequest = sponsorContributionRequest?.copy(startMs = 0L, endMs = 0L)
            _sponsorContributionUiState.value = current.copy(
                startMs = 0L,
                endMs = 0L,
                actionType = actionType,
                message = null,
            )
        } else {
            _sponsorContributionUiState.value = current.copy(actionType = actionType, message = null)
        }
    }

    fun submitSponsorContribution() {
        val current = _sponsorContributionUiState.value
        val request = sponsorContributionRequest ?: return
        if (current.phase != SponsorContributionPhase.REVIEW) return
        _sponsorContributionUiState.value = current.copy(
            phase = SponsorContributionPhase.SUBMITTING,
            message = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            request.plugin.submitCommunitySegment(
                bvid = request.bvid,
                cid = request.cid,
                videoDurationSeconds = request.durationSeconds,
                startMs = request.startMs,
                endMs = request.endMs,
                category = current.category,
                actionType = current.actionType,
            ).onSuccess {
                _sponsorContributionUiState.value = _sponsorContributionUiState.value.copy(
                    phase = SponsorContributionPhase.SUCCESS,
                    message = "社区片段已提交",
                )
            }.onFailure { error ->
                _sponsorContributionUiState.value = _sponsorContributionUiState.value.copy(
                    phase = SponsorContributionPhase.REVIEW,
                    message = error.message ?: "提交失败，请检查服务器后重试",
                )
            }
        }
    }

    fun cancelSponsorContribution() {
        sponsorContributionRequest = null
        // REVIEW/SUCCESS are intentionally ignored by the availability refresher.
        // Reset the dialog phase first so both the “完成” button and outside dismiss
        // can close it, then restore the contribution marker when it is still available.
        _sponsorContributionUiState.value = SponsorContributionUiState()
        refreshSponsorContributionAvailability()
    }

    private fun refreshSponsorContributionAvailability(
        enabledPlugins: List<com.android.purebilibili.core.plugin.PlayerPlugin> =
            getSessionPlayerPlugins(),
    ) {
        val current = _sponsorContributionUiState.value
        if (current.phase !in setOf(SponsorContributionPhase.HIDDEN, SponsorContributionPhase.READY)) return
        val context = resolveSponsorContributionContext(enabledPlugins)
        _sponsorContributionUiState.value = if (context == null) {
            SponsorContributionUiState()
        } else {
            SponsorContributionUiState(
                phase = SponsorContributionPhase.READY,
                serverBaseUrl = context.plugin.getCommunityServerBaseUrl(),
            )
        }
    }

    private fun resolveSponsorContributionContext(
        enabledPlugins: List<com.android.purebilibili.core.plugin.PlayerPlugin> =
            getSessionPlayerPlugins(),
    ): SponsorContributionRequest? {
        val plugin = enabledPlugins
            .filterIsInstance<com.android.purebilibili.feature.plugin.SponsorBlockPlugin>()
            .firstOrNull { it.isCommunityContributionEnabled() }
            ?: return null
        val snapshot = buildSponsorBlockVideoSnapshot(_uiState.value) ?: return null
        val durationMs = playbackUseCase.getDuration()
        if (snapshot.bvid.isBlank() || snapshot.cid <= 0L || durationMs <= 0L) return null
        return SponsorContributionRequest(
            plugin = plugin,
            bvid = snapshot.bvid,
            cid = snapshot.cid,
            durationSeconds = durationMs / 1000f,
            startMs = 0L,
            endMs = 0L,
        )
    }
    
    // ========== Playback Control ==========
    
    fun seekTo(pos: Long) { playbackUseCase.seekTo(pos) }
    fun getPlayerCurrentPosition() = playbackUseCase.getCurrentPosition()
    fun getPlayerDuration() = playbackUseCase.getDuration()
    fun saveCurrentPosition() { playbackUseCase.savePosition(currentBvid, currentCid) }
    fun flushPlaybackHeartbeatSnapshot(reason: String = "manual") {
        if (currentBvid.isBlank() || currentCid <= 0L) return
        viewModelScope.launch {
            reportPlaybackHeartbeatSnapshot(forceFlush = true, reason = reason)
        }
    }

    private fun beginHeartbeatSession(nowEpochSec: Long = System.currentTimeMillis() / 1000L) {
        heartbeatSessionStartTsSec = resolvePlaybackHeartbeatSessionStartTsSec(
            existingStartTsSec = 0L,
            nowEpochSec = nowEpochSec
        )
        heartbeatAccumulatedPlayMs = 0L
        heartbeatActivePlayStartElapsedMs = null
        lastReportedHeartbeatSnapshot = null
    }

    private fun clearHeartbeatSession() {
        heartbeatSessionStartTsSec = 0L
        heartbeatAccumulatedPlayMs = 0L
        heartbeatActivePlayStartElapsedMs = null
        lastReportedHeartbeatSnapshot = null
    }

    private fun syncHeartbeatPlaybackTracking(
        isActivelyPlaying: Boolean,
        nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()
    ) {
        if (isActivelyPlaying) {
            if (heartbeatActivePlayStartElapsedMs == null) {
                heartbeatActivePlayStartElapsedMs = nowElapsedMs
            }
            return
        }

        val activePlayStartElapsedMs = heartbeatActivePlayStartElapsedMs ?: return
        heartbeatAccumulatedPlayMs += (nowElapsedMs - activePlayStartElapsedMs).coerceAtLeast(0L)
        heartbeatActivePlayStartElapsedMs = null
    }

    private suspend fun reportPlaybackHeartbeatSnapshot(
        forceFlush: Boolean,
        reason: String
    ): Boolean {
        if (currentBvid.isBlank() || currentCid <= 0L) return false

        val nowEpochSec = System.currentTimeMillis() / 1000L
        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        val isActivelyPlaying = exoPlayer?.isPlaying == true && !BackgroundManager.isInBackground
        syncHeartbeatPlaybackTracking(
            isActivelyPlaying = isActivelyPlaying,
            nowElapsedMs = nowElapsedMs
        )

        if (heartbeatSessionStartTsSec <= 0L) {
            heartbeatSessionStartTsSec = resolvePlaybackHeartbeatSessionStartTsSec(
                existingStartTsSec = heartbeatSessionStartTsSec,
                nowEpochSec = nowEpochSec
            )
        }

        val snapshot = resolvePlaybackHeartbeatSnapshot(
            currentPositionMs = playbackUseCase.getCurrentPosition(),
            accumulatedPlayMs = heartbeatAccumulatedPlayMs,
            activePlayStartElapsedMs = heartbeatActivePlayStartElapsedMs,
            nowElapsedMs = nowElapsedMs
        )

        val shouldSend = if (forceFlush) {
            shouldFlushPlaybackHeartbeatSnapshot(
                currentBvid = currentBvid,
                currentCid = currentCid,
                snapshot = snapshot,
                lastReportedSnapshot = lastReportedHeartbeatSnapshot
            )
        } else {
            shouldSendPlaybackHeartbeat(
                isPlaying = exoPlayer?.isPlaying == true,
                isInBackground = BackgroundManager.isInBackground,
                currentBvid = currentBvid,
                currentCid = currentCid
            )
        }
        if (!shouldSend) return false

        try {
            val reported = VideoRepository.reportPlayHeartbeat(
                bvid = currentBvid,
                cid = currentCid,
                aid = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.aid ?: 0L,
                playedTime = snapshot.playedTimeSec,
                realPlayedTime = snapshot.realPlayedTimeSec,
                startTsSec = heartbeatSessionStartTsSec
            )
            if (reported) {
                lastReportedHeartbeatSnapshot = snapshot
                Logger.d(
                    "PlayerVM",
                        "🔴 Heartbeat snapshot sent: reason=$reason, bvid=$currentBvid, cid=$currentCid, " +
                            "played=${snapshot.playedTimeSec}, real=${snapshot.realPlayedTimeSec}, startTs=$heartbeatSessionStartTsSec"
                )
            }
            return reported
        } catch (e: Exception) {
            Logger.d("PlayerVM", " Heartbeat snapshot failed($reason): ${e.message}")
            return false
        }
    }

    private data class PlaybackCdnCandidateSelection(
        val playUrl: String,
        val audioUrl: String?,
        val adaptiveDashSource: AdaptiveDashPlaybackSource?,
        val allVideoUrls: List<String>,
        val allAudioUrls: List<String>,
        val cdnCacheKeysByUrl: Map<String, String>,
        val candidateSources: List<com.android.purebilibili.feature.plugin.PlaybackCdnCandidateSource>,
        val regionLabel: String?,
        val lineDiagnostics: List<CdnLineDiagnostic>,
        val fallbackState: PlaybackCdnFallbackState
    )

    /**
     * Payload for [applyQualityChangeResult] — the minimal set of fields needed to
     * apply a quality change (manual switch or auto-upgrade) to the player and UI state.
     */
    private data class QualityChangePayload(
        val playUrl: String,
        val audioUrl: String?,
        val actualQuality: Int,
        val adaptiveDashSource: AdaptiveDashPlaybackSource?,
        val cachedDashVideos: List<DashVideo>,
        val cachedDashAudios: List<DashAudio>,
        val cachedDash: Dash? = null,
        val requestedAudioQuality: Int = -1,
        val selectedAudioQuality: Int = -1,
        val availableAudioQualities: List<AudioQualityOption> = emptyList(),
        val audioFallbackReason: AudioFallbackReason? = null,
        val qualityIds: List<Int>,
        val qualityLabels: List<String>,
        val switchableQualityIds: List<Int>,
        val wasFallback: Boolean = false,
        val currentPos: Long,
        val playWhenReady: Boolean,
        val playbackQualityMode: PlaybackQualityMode
    )

    private fun resolvePlaybackCdnCandidateSelection(
        videoUrl: String,
        audioUrl: String?,
        quality: Int,
        cachedDashVideos: List<DashVideo>,
        cachedDashAudios: List<DashAudio>,
        adaptiveDashSource: AdaptiveDashPlaybackSource?
    ): PlaybackCdnCandidateSelection {
        val rawVideoUrls = buildPlaybackVideoUrlCandidates(
            videoUrl = videoUrl,
            quality = quality,
            cachedDashVideos = cachedDashVideos
        )

        val rawAudioUrls = buildPlaybackAudioUrlCandidates(
            audioUrl = audioUrl,
            cachedDashAudios = cachedDashAudios
        )
        val originalCandidates = com.android.purebilibili.feature.plugin.buildPlaybackCdnCandidates(
            videoUrls = rawVideoUrls,
            audioUrls = rawAudioUrls
        )

        val cdnPlugin = PluginManager
            .getEnabledPlugins(PlaybackCdnPlugin::class)
            .firstOrNull()
        val cdnRewrite = cdnPlugin?.rewritePlaybackCandidates(rawVideoUrls, rawAudioUrls)

        val preferredCandidates = applyPlaybackCdnPreference(
            candidates = cdnRewrite?.candidates ?: originalCandidates,
            preference = playbackCdnPreference.value,
        )
        val allVideoUrls = preferredCandidates.map { it.videoUrl }
        val allAudioUrls = preferredCandidates.map { it.audioUrl.orEmpty() }
        val selectedVideoUrl = allVideoUrls.firstOrNull() ?: videoUrl
        val selectedAudioUrl = allAudioUrls.firstOrNull()?.takeIf { it.isNotBlank() } ?: audioUrl
        val selectedAdaptiveDashSource =
            if (selectedVideoUrl == videoUrl && selectedAudioUrl == audioUrl) {
                adaptiveDashSource
            } else {
                null
            }

        return PlaybackCdnCandidateSelection(
            playUrl = selectedVideoUrl,
            audioUrl = selectedAudioUrl,
            adaptiveDashSource = selectedAdaptiveDashSource,
            allVideoUrls = allVideoUrls,
            allAudioUrls = allAudioUrls,
            cdnCacheKeysByUrl = buildPlaybackCdnCacheKeys(preferredCandidates),
            candidateSources = preferredCandidates.map { it.source },
            regionLabel = cdnRewrite?.regionLabel,
            lineDiagnostics = cdnPlugin?.buildPlaybackCdnDiagnostics(
                videoUrls = allVideoUrls,
                sources = preferredCandidates.map { it.source }
            ).orEmpty().ifEmpty {
                buildCdnLineDiagnostics(
                    urls = allVideoUrls,
                    healthByHost = emptyMap(),
                    sources = preferredCandidates.map { it.source }
                )
            },
            fallbackState = buildPlaybackCdnFallbackState(
                selectedVideoUrl = selectedVideoUrl,
                selectedAudioUrl = selectedAudioUrl,
                originalVideoUrl = videoUrl,
                originalAudioUrl = audioUrl,
                regionLabel = cdnRewrite?.regionLabel,
                audioFallbackUrl = rawAudioUrls.drop(1).firstOrNull(),
                fallbackCandidates = originalCandidates,
                usesCustomRule = cdnRewrite?.sources?.firstOrNull() ==
                    com.android.purebilibili.feature.plugin.PlaybackCdnCandidateSource.CUSTOM
            )
        )
    }

    private fun playResolvedPlayback(
        videoUrl: String,
        audioUrl: String?,
        adaptiveDashSource: AdaptiveDashPlaybackSource?,
        startPositionMs: Long,
        playWhenReady: Boolean = true,
        cdnFallbackState: PlaybackCdnFallbackState = PlaybackCdnFallbackState.Inactive,
        cdnCacheKeysByUrl: Map<String, String> = emptyMap()
    ) {
        playbackRecoveryGeneration++
        cancelPlaybackStallRecovery()
        armPlaybackCdnFallback(cdnFallbackState, playWhenReady)
        if (adaptiveDashSource != null || audioUrl != null) {
            playbackUseCase.playDashVideo(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                adaptiveDashSource = adaptiveDashSource,
                seekTo = startPositionMs,
                playWhenReady = playWhenReady,
                cdnCacheKeysByUrl = cdnCacheKeysByUrl
            )
        } else {
            playbackUseCase.playVideo(videoUrl, startPositionMs, playWhenReady = playWhenReady)
        }
    }

    private fun armPlaybackCdnFallback(
        state: PlaybackCdnFallbackState,
        playWhenReady: Boolean
    ) {
        playbackCdnFallbackJob?.cancel()
        playbackCdnFallbackState = state
        if (!playWhenReady || !state.usesCdnRewrite) return
        val ticket = playbackRecoveryGeneration
        val scheduledPlayer = exoPlayer

        playbackCdnFallbackJob = viewModelScope.launch {
            delay(PLAYBACK_CDN_FIRST_FRAME_FALLBACK_TIMEOUT_MS)
            if (ticket != playbackRecoveryGeneration || exoPlayer !== scheduledPlayer) return@launch
            val playbackReady = exoPlayer?.playbackState == Player.STATE_READY
            val expectedAudioTrack = playbackCdnFallbackState.selectedAudioUrl != null
            val hasSelectedAudioTrack = hasSelectedAudioTrack(exoPlayer)
            if (shouldFallbackFromCdnRewrite(
                    state = playbackCdnFallbackState,
                    playbackReady = playbackReady,
                    expectedAudioTrack = expectedAudioTrack,
                    hasSelectedAudioTrack = hasSelectedAudioTrack,
                    audioRendererError = false
                )
            ) {
                val reason = if (playbackReady && expectedAudioTrack && !hasSelectedAudioTrack) {
                    "audio_track_timeout"
                } else {
                    "first_frame_timeout"
                }
                fallbackFromCdnRewrite(reason = reason)
            }
        }
    }

    private fun markPlaybackCdnReadyIfMediaReady() {
        val state = playbackCdnFallbackState
        if (state.usesCdnRewrite && state.selectedAudioUrl != null && !hasSelectedAudioTrack(exoPlayer)) {
            recordCurrentCdnHealthEvent(CdnHealthEvent.AUDIO_TRACK_MISSING)
            Logger.d(
                "PlayerVM",
                "CDN fallback remains armed: region=${state.regionLabel ?: "unknown"}, " +
                    "audio=${hostForPlaybackLog(state.selectedAudioUrl)}, fallbackAudio=${hostForPlaybackLog(state.fallbackAudioUrl)}"
            )
            return
        }
        playbackCdnFallbackJob?.cancel()
        playbackCdnFallbackJob = null
    }

    private fun scheduleCdnDashPrefetch() {
        playbackCdnPrefetchJob?.cancel()
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val player = exoPlayer ?: return
        val plugin = PluginManager.getEnabledPlugins(PlaybackCdnPlugin::class).firstOrNull() ?: return
        if (!plugin.isAdaptivePrefetchEnabled()) return
        val bufferedDurationMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        val track = current.cachedDashVideos.firstOrNull { video ->
            video.id == current.currentQuality &&
                (video.baseUrl == current.playUrl || video.backupUrl.orEmpty().contains(current.playUrl))
        } ?: return
        val indexRange = parseCdnByteRange(track.segmentBase?.indexRange) ?: return
        val context = appContext ?: return
        // 蜂窝网络下不做后台整段预取：跳看的视频每跳一次就会预取一批新分段，
        // 是「流量机制太激进」反馈的主要来源。正常播放的随播缓存不受影响。
        if (!NetworkUtils.isWifi(context)) return
        val candidates = current.allVideoUrls.ifEmpty { listOf(current.playUrl) }
            .filter { it.isNotBlank() }
        if (candidates.isEmpty()) return
        playbackCdnPrefetchJob = viewModelScope.launch {
            CdnDashSegmentPrefetcher(
                context = context,
                client = com.android.purebilibili.core.network.NetworkModule.playbackOkHttpClient
            ).prefetch(
                CdnDashPrefetchRequest(
                    candidates = candidates,
                    indexRange = indexRange,
                    trackCacheKey = buildCdnTrackCacheKey("video", candidates.first()),
                    bufferedDurationMs = bufferedDurationMs,
                    frontierPositionMs = player.bufferedPosition
                )
            )
        }
    }

    private fun fallbackFromCdnRewrite(reason: String) {
        val state = playbackCdnFallbackState
        if (!shouldFallbackFromCdnRewrite(state, playbackReady = false)) return
        val event = if (reason == "first_frame_timeout") {
            CdnHealthEvent.FIRST_FRAME_TIMEOUT
        } else {
            CdnHealthEvent.PLAYER_ERROR
        }
        recordCurrentCdnHealthEvent(event)
        val fallbackVideoUrl = state.fallbackVideoUrl ?: return
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val playWhenReadyAfterFallback = exoPlayer?.let { player ->
            resolvePlaybackIntentForSourceReplacement(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying
            )
        } ?: true
        val nextState = state.advanceFallback(
            currentFallbackVideoUrl = fallbackVideoUrl,
            currentFallbackAudioUrl = state.fallbackAudioUrl
        )
        playbackCdnFallbackState = nextState
        playbackCdnFallbackJob?.cancel()
        playbackCdnFallbackJob = null

        Logger.w(
            "PlayerVM",
            "CDN fallback: reason=$reason, region=${state.regionLabel ?: "unknown"}, " +
                "selected=${hostForPlaybackLog(state.selectedVideoUrl)}, fallback=${hostForPlaybackLog(fallbackVideoUrl)}, " +
                "audio=${hostForPlaybackLog(state.selectedAudioUrl)}, fallbackAudio=${hostForPlaybackLog(state.fallbackAudioUrl)}, " +
                "quality=${_audioQualityPreference.value}"
        )
        playResolvedPlayback(
            videoUrl = fallbackVideoUrl,
            audioUrl = state.fallbackAudioUrl,
            adaptiveDashSource = null,
            startPositionMs = currentPos,
            playWhenReady = playWhenReadyAfterFallback,
            cdnFallbackState = nextState,
            cdnCacheKeysByUrl = com.android.purebilibili.feature.plugin
                .buildPlaybackCdnCacheKeys(state.fallbackCandidates)
        )
        _uiState.update { current ->
            if (current is VideoPlaybackUiState.Success) {
                val fallbackIndex = current.allVideoUrls.indexOf(fallbackVideoUrl)
                    .takeIf { it >= 0 }
                    ?: current.currentCdnIndex
                current.copy(
                    playUrl = fallbackVideoUrl,
                    audioUrl = state.fallbackAudioUrl,
                    adaptiveDashSource = null,
                    currentCdnIndex = fallbackIndex
                )
            } else {
                current
            }
        }
        if (state.usesCustomRule) {
            viewModelScope.launch { toast("自定义线路不可用，已切换至原始线路") }
        }
    }

    private fun hasSelectedAudioTrack(player: Player?): Boolean {
        return player?.currentTracks?.groups.orEmpty().any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.isSelected
        }
    }

    private fun recordCurrentCdnHealthEvent(event: CdnHealthEvent) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val plugin = PluginManager.getEnabledPlugins(PlaybackCdnPlugin::class).firstOrNull() ?: return
        plugin.recordPlaybackCdnEvent(current.playUrl, event)
        val diagnostics = plugin.buildPlaybackCdnDiagnostics(
            videoUrls = current.allVideoUrls,
            sources = current.cdnCandidateSources
        )
        if (diagnostics.isNotEmpty()) {
            _uiState.value = current.copy(cdnLineDiagnostics = diagnostics)
        }
    }

    fun probeCurrentCdnCandidates() {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val plugin = PluginManager.getEnabledPlugins(PlaybackCdnPlugin::class).firstOrNull() ?: run {
            viewModelScope.launch { toast("CDN 优选插件未启用") }
            return
        }
        if (current.allVideoUrls.isEmpty()) {
            viewModelScope.launch { toast("没有可检测的线路") }
            return
        }
        _uiState.value = current.copy(isCdnProbing = true)
        viewModelScope.launch {
            val diagnostics = withContext(Dispatchers.IO) {
                plugin.probePlaybackCdnCandidates(
                    videoUrls = current.allVideoUrls,
                    sources = current.cdnCandidateSources
                )
            }
            _uiState.update { state ->
                if (state is VideoPlaybackUiState.Success) {
                    state.copy(
                        cdnLineDiagnostics = diagnostics,
                        isCdnProbing = false
                    )
                } else {
                    state
                }
            }
            toast("线路检测完成")
        }
    }

    private fun showQualitySwitchFailureDialog(
        requestedQualityId: Int,
        permissionResult: QualityPermissionResult? = null,
        loadError: VideoLoadError? = null,
        hasCachedDashTracks: Boolean = true,
        cacheContainsRequestedQuality: Boolean = true,
        qualityRefetchCooldownRemainingMs: Long? = null,
        initialUnavailableReason: InitialQualityUnavailableReason? = null
    ) {
        val requestedQualityLabel = if (requestedQualityId > 0) {
            qualityManager.getQualityLabel(requestedQualityId)
        } else {
            "自动"
        }
        val dialogState = buildQualitySwitchFailureDialogState(
            requestedQualityId = requestedQualityId,
            requestedQualityLabel = requestedQualityLabel,
            permissionResult = permissionResult,
            loadError = loadError,
            hasCachedDashTracks = hasCachedDashTracks,
            cacheContainsRequestedQuality = cacheContainsRequestedQuality,
            qualityRefetchCooldownRemainingMs = qualityRefetchCooldownRemainingMs,
            initialUnavailableReason = initialUnavailableReason
        )
        Logger.w(
            "PlayerVM",
            "QUALITY_SWITCH_FAILURE requested=$requestedQualityId label=$requestedQualityLabel " +
                "permission=$permissionResult error=$loadError hasCache=$hasCachedDashTracks " +
                "containsRequested=$cacheContainsRequestedQuality cooldownMs=${qualityRefetchCooldownRemainingMs ?: 0L} " +
                "initialReason=$initialUnavailableReason " +
                "message=${dialogState.message}"
        )
        _qualitySwitchFailureDialog.value = dialogState
    }
    
    fun restoreFromCache(cachedState: VideoPlaybackUiState.Success, startPosition: Long = -1L) {
        relatedPlaybackPreparedToken = -1L
        relatedFirstFrameToken = -1L
        currentBvid = cachedState.info.bvid
        currentCid = cachedState.info.cid
        val restoredState = if (startPosition >= 0) {
            cachedState.copy(startPosition = startPosition)
        } else {
            cachedState
        }
        _uiState.value = restoredState
        _detailState.value = VideoDetailUiState(
            requestBvid = restoredState.info.bvid,
            requestCid = restoredState.info.cid,
            requestToken = currentLoadRequestToken,
            info = restoredState.info,
            related = restoredState.related,
            relatedLoadState = restoredState.relatedLoadState,
            isLoggedIn = restoredState.isLoggedIn,
            isVip = restoredState.isVip
        )
        publishSubjectSnapshot(restoredState)
        restoreSponsorPlaybackState(restoredState)
        if (restoredState.relatedLoadState == VideoPlaybackUiState.RelatedLoadState.LOADING) {
            loadRelatedVideosForRequest(restoredState.info.bvid, currentLoadRequestToken)
        }
    }

    private fun publishSubjectSnapshot(state: VideoPlaybackUiState.Success) {
        val previous = _subjectSnapshot.value
        if (shouldAdvanceVideoSubjectGeneration(previous, state)) {
            subjectGeneration += 1L
        }
        val next = state.toSubjectSnapshot(subjectGeneration)
        if (next != previous) {
            _subjectSnapshot.value = next
        }
    }
    
    // ========== Private ==========
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        beginHeartbeatSession()
        heartbeatJob = viewModelScope.launch {
            if (shouldSendInitialPlaybackHeartbeat(
                    isActivelyPlaying = exoPlayer?.isPlaying == true,
                    isInBackground = BackgroundManager.isInBackground,
                    currentBvid = currentBvid,
                    currentCid = currentCid
                )
            ) {
                try {
                    val reported = VideoRepository.reportPlayHeartbeat(
                        bvid = currentBvid,
                        cid = currentCid,
                        aid = (_uiState.value as? VideoPlaybackUiState.Success)?.info?.aid ?: 0L,
                        playedTime = 0L,
                        realPlayedTime = 0L,
                        startTsSec = heartbeatSessionStartTsSec
                    )
                    if (reported) {
                        lastReportedHeartbeatSnapshot = PlaybackHeartbeatSnapshot(
                            playedTimeSec = 0L,
                            realPlayedTimeSec = 0L
                        )
                        Logger.d(
                            "PlayerVM",
                            " Initial heartbeat reported for $currentBvid startTs=$heartbeatSessionStartTsSec"
                        )
                    }
                } catch (e: Exception) {
                    Logger.d("PlayerVM", " Initial heartbeat failed: ${e.message}")
                }
            }
            syncHeartbeatPlaybackTracking(isActivelyPlaying = exoPlayer?.isPlaying == true)
            
            while (true) {
                delay(30_000)
                if (reportPlaybackHeartbeatSnapshot(forceFlush = false, reason = "interval")) {
                    recordCreatorWatchProgressSnapshot()
                }
            }
        }
    }

    private fun recordCreatorWatchProgressSnapshot() {
        val context = appContext ?: return
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return
        val mid = current.info.owner.mid
        if (mid <= 0L) return

        val currentPositionSec = playbackUseCase.getCurrentPosition() / 1000L
        if (currentPositionSec <= 0L) return

        val rawDelta = if (lastCreatorSignalPositionSec < 0L) {
            currentPositionSec
        } else {
            currentPositionSec - lastCreatorSignalPositionSec
        }
        val safeDelta = rawDelta.coerceIn(0L, 45L)
        lastCreatorSignalPositionSec = currentPositionSec
        if (safeDelta <= 0L) return

        TodayWatchProfileStore.recordWatchProgress(
            context = context,
            mid = mid,
            creatorName = current.info.owner.name,
            deltaWatchSec = safeDelta
        )
    }
    
    fun toast(
        msg: String,
        presentation: PlayerToastPresentation = PlayerToastPresentation.Standard
    ) {
        viewModelScope.launch {
            val payload = if (presentation == PlayerToastPresentation.CenteredHighlight) {
                buildQualityToastMessage(msg)
            } else {
                buildPlayerToastMessage(msg)
            }
            _toastEvent.send(payload)
        }
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun onCleared() {
        finishQualitySwitchTelemetry("canceled", "screen_closed")
        cancelPendingVideoLoad()
        exoPlayer?.removeAnalyticsListener(videoBandwidthListener)
        // A player retained for listening must not retain this cleared screen through its listener.
        exoPlayer?.removeListener(playbackEndListener)
        super.onCleared()
        onPageIdentityCommitted = null
        recordCreatorWatchProgressSnapshot()
        heartbeatJob?.cancel()
        clearHeartbeatSession()
        pluginCheckJob?.cancel()
        playbackCdnFallbackJob?.cancel()
        onlineCountJob?.cancel()  // 👀 取消在线人数轮询
        playbackTransitionMonitorJob?.cancel()
        aiSummaryJob?.cancel()
        videoNoteJob?.cancel()
        activeLoadJob?.cancel()
        playerInfoJob?.cancel()
        appContext?.let { context ->
            val miniPlayerManager = MiniPlayerManager.getInstance(context)
            miniPlayerManager.onNavigateNextCallback = null
            miniPlayerManager.onNavigatePreviousCallback = null
            miniPlayerManager.onHasNextNavigationCallback = null
            miniPlayerManager.onHasPreviousNavigationCallback = null
        }
        
        //  通知插件系统：视频结束
        getSessionPlayerPlugins().forEach { plugin ->
            try {
                plugin.onVideoEnd()
            } catch (e: Exception) {
                Logger.e("PlayerVM", "Plugin ${plugin.name} onVideoEnd failed", e)
            }
        }
        
        exoPlayer = null
    }

    /**
     * Apply a quality change result to the player and UI state.
     *
     * Shared path for both:
     * - [QualityChangeReason.USER_EXPLICIT] (manual quality switch from [changeQuality])
     * - [QualityChangeReason.INITIAL_AUTO_UPGRADE] (HDR auto-upgrade)
     *
     * Preserves playback position and state. CDN selection, fallback arming,
     * UI state update, and transition position monitoring are all handled here.
     * Callers are responsible for pre-validation and for their own post-apply
     * UI feedback (toast, analytics, logging).
     */
    private fun applyQualityChangeResult(
        payload: QualityChangePayload,
        reason: QualityChangeReason,
        cdnSelection: PlaybackCdnCandidateSelection,
        sourceAlreadyApplied: Boolean = true
    ) {
        val current = _uiState.value as? VideoPlaybackUiState.Success ?: return

        val cdnSelectionChangedUrl =
            cdnSelection.playUrl != payload.playUrl || cdnSelection.audioUrl != payload.audioUrl
        // Manual requests select first, validate media identity, then apply the source once here.
        if (!sourceAlreadyApplied || shouldReplacePlaybackSourceForQualityChange(reason, cdnSelectionChangedUrl)) {
            playResolvedPlayback(
                videoUrl = cdnSelection.playUrl,
                audioUrl = cdnSelection.audioUrl,
                adaptiveDashSource = cdnSelection.adaptiveDashSource,
                startPositionMs = payload.currentPos,
                playWhenReady = payload.playWhenReady,
                cdnFallbackState = cdnSelection.fallbackState,
                cdnCacheKeysByUrl = cdnSelection.cdnCacheKeysByUrl
            )
        } else {
            armPlaybackCdnFallback(cdnSelection.fallbackState, payload.playWhenReady)
        }

        _qualitySwitchFailureDialog.value = null

        _uiState.value = current.copy(
            playUrl = cdnSelection.playUrl,
            audioUrl = cdnSelection.audioUrl,
            currentQuality = payload.actualQuality,
            isQualitySwitching = false,
            requestedQuality = null,
            playbackQualityMode = payload.playbackQualityMode,
            adaptiveDashSource = cdnSelection.adaptiveDashSource,
            pendingPlaybackTransitionPositionMs = payload.currentPos,
            qualityIds = payload.qualityIds.ifEmpty { current.qualityIds },
            qualityLabels = payload.qualityLabels.ifEmpty { current.qualityLabels },
            switchableQualityIds = payload.switchableQualityIds
                .ifEmpty { current.switchableQualityIds },
            cachedDashVideos = payload.cachedDashVideos,
            cachedDashAudios = payload.cachedDashAudios,
            cachedDash = payload.cachedDash ?: current.cachedDash,
            requestedAudioQuality = payload.requestedAudioQuality,
            selectedAudioQuality = payload.selectedAudioQuality,
            availableAudioQualities = payload.availableAudioQualities
                .ifEmpty { current.availableAudioQualities },
            audioFallbackReason = payload.audioFallbackReason,
            currentCdnIndex = 0,
            allVideoUrls = cdnSelection.allVideoUrls,
            allAudioUrls = cdnSelection.allAudioUrls,
            cdnCandidateSources = cdnSelection.candidateSources,
            cdnLineDiagnostics = cdnSelection.lineDiagnostics
        )

        monitorPlaybackTransitionPosition(payload.currentPos)

        when (reason) {
            QualityChangeReason.USER_EXPLICIT -> {
                val label = payload.qualityLabels.getOrNull(
                    payload.qualityIds.indexOf(payload.actualQuality)
                ) ?: qualityManager.getQualityLabel(payload.actualQuality)
                toast(
                    if (payload.wasFallback) {
                        "目标清晰度不可用，已切换至 $label"
                    } else {
                        "✓ 已切换至 $label"
                    },
                    PlayerToastPresentation.CenteredHighlight
                )
            }

            QualityChangeReason.INITIAL_AUTO_UPGRADE -> {
                Logger.d(
                    "PlayerVM",
                    "PLAY_DIAG premium_upgrade_applied " +
                        "quality=${payload.actualQuality} position=${payload.currentPos} " +
                        "wasFallback=${payload.wasFallback}"
                )
                // Silent upgrade — no toast, no user-visible analytics event
            }
        }
    }
}
