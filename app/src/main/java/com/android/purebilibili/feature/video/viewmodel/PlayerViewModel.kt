// File: feature/video/PlayerViewModel.kt
//  [重构] 简化版 PlayerViewModel - 使用 UseCase 层
package com.android.purebilibili.feature.video.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import com.android.purebilibili.feature.video.usecase.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.cache.PlayUrlCache
import com.android.purebilibili.core.cooldown.PlaybackCooldownManager
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.store.TodayWatchFeedbackSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.TodayWatchProfileStore
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.video.controller.QualityManager
import com.android.purebilibili.feature.video.controller.QualityPermissionResult
import com.android.purebilibili.feature.video.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
import com.android.purebilibili.feature.video.playback.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.feature.video.playback.policy.PlaybackPostLoadTask
import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode
import com.android.purebilibili.feature.video.playback.policy.PlaybackHeartbeatSnapshot
import com.android.purebilibili.feature.video.playback.policy.resolveOnlineCountPollingDelayMs
import com.android.purebilibili.feature.video.playback.policy.buildPlaybackPostLoadPlan
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackHeartbeatSessionStartTsSec
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackHeartbeatSnapshot
import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackTransitionPosition
import com.android.purebilibili.feature.video.playback.policy.resolvePluginPollingIntervalMs
import com.android.purebilibili.feature.video.playback.policy.shouldRefreshOnlineCount
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
import com.android.purebilibili.feature.video.ui.feedback.resolveTripleActionFeedbackMessage
import com.android.purebilibili.feature.video.ui.feedback.resolveTripleActionVisualState
import com.android.purebilibili.feature.video.subtitle.SubtitleCue
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackMeta
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.isLikelyAiSubtitleTrack
import com.android.purebilibili.feature.video.subtitle.isTrustedBilibiliSubtitleUrl
import com.android.purebilibili.feature.video.subtitle.normalizeBilibiliSubtitleUrl
import com.android.purebilibili.feature.video.subtitle.orderSubtitleTracksByPreference
import com.android.purebilibili.feature.video.subtitle.resolveDefaultSubtitleLanguages

data class SponsorSkipUiState(
    val visible: Boolean = false,
    val segmentId: String? = null,
    val skipToMs: Long = 0L,
    val label: String? = null
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

// ========== UI State ==========
sealed class PlayerUiState {
    data class Loading(
        val retryAttempt: Int = 0,
        val maxAttempts: Int = 4,
        val message: String = "\u52a0\u8f7d\u4e2d..."
    ) : PlayerUiState() {
        companion object { val Initial = Loading() }
    }
    
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val audioUrl: String? = null,
        val related: List<RelatedVideo> = emptyList(),
        val currentQuality: Int = 64,
        val playbackQualityMode: PlaybackQualityMode = PlaybackQualityMode.AUTO,
        val adaptiveDashSource: AdaptiveDashPlaybackSource? = null,
        val qualityLabels: List<String> = emptyList(),
        val qualityIds: List<Int> = emptyList(),
        val startPosition: Long = 0L,
        val pendingPlaybackTransitionPositionMs: Long? = null,
        val cachedDashVideos: List<DashVideo> = emptyList(),
        val cachedDashAudios: List<DashAudio> = emptyList(),
        val isQualitySwitching: Boolean = false,
        val requestedQuality: Int? = null,
        val isLoggedIn: Boolean = false,
        val isVip: Boolean = false,
        val isFollowing: Boolean = false,
        val isFavorited: Boolean = false,
        val isLiked: Boolean = false,
        val coinCount: Int = 0,
        val emoteMap: Map<String, String> = emptyMap(),
        val isInWatchLater: Boolean = false,  //  稍后再看状态
        val followingMids: Set<Long> = emptySet(),  //  已关注用户 ID 列表
        val videoTags: List<VideoTag> = emptyList(),  //  视频标签列表
        //  CDN 线路切换
        val currentCdnIndex: Int = 0,  // 当前使用的 CDN 索引 (0=主线路)
        val allVideoUrls: List<String> = emptyList(),  // 所有可用视频 URL (主+备用)
        val allAudioUrls: List<String> = emptyList(),   // 所有可用音频 URL (主+备用)
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
        val bgmInfo: BgmInfo? = null,
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
        val subtitlePrimaryLikelyAi: Boolean = false,
        val subtitleSecondaryLikelyAi: Boolean = false,
        val subtitlePrimaryCues: List<SubtitleCue> = emptyList(),
        val subtitleSecondaryCues: List<SubtitleCue> = emptyList(),
        val ownerFollowerCount: Int? = null,
        val ownerVideoCount: Int? = null
    ) : PlayerUiState() {
        val cdnCount: Int get() = allVideoUrls.size.coerceAtLeast(1)
        val currentCdnLabel: String get() = "线路${currentCdnIndex + 1}"
    }
    
    data class Error(
        val error: VideoLoadError,
        val canRetry: Boolean = true
    ) : PlayerUiState() {
        val msg: String get() = error.toUserMessage()
    }
}

internal fun resolveCommentReplyTargets(replyRpid: Long?, replyRoot: Long?): Pair<Long, Long> {
    val parent = replyRpid?.takeIf { it > 0L } ?: 0L
    if (parent == 0L) return 0L to 0L
    val root = replyRoot?.takeIf { it > 0L } ?: parent
    return root to parent
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

internal fun resolveRequestedStartPositionMs(
    cachedPositionMs: Long,
    fallbackResumePositionMs: Long
): Long {
    val safeCachedPositionMs = cachedPositionMs.coerceAtLeast(0L)
    if (safeCachedPositionMs > 0L) return safeCachedPositionMs
    return fallbackResumePositionMs.coerceAtLeast(0L)
}

internal fun resolveInitialPlaybackQualityMode(): PlaybackQualityMode = PlaybackQualityMode.AUTO

internal fun resolvePlaybackQualityModeForQualitySelection(qualityId: Int): PlaybackQualityMode {
    return PlaybackQualityMode.fromQualityId(qualityId)
}

internal data class QualitySwitchFailureDialogState(
    val requestedQualityId: Int,
    val requestedQualityLabel: String,
    val title: String,
    val message: String
)

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
    qualityRefetchCooldownRemainingMs: Long? = null
): String {
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
    qualityRefetchCooldownRemainingMs: Long? = null
): QualitySwitchFailureDialogState {
    return QualitySwitchFailureDialogState(
        requestedQualityId = requestedQualityId,
        requestedQualityLabel = requestedQualityLabel,
        title = "切换到 $requestedQualityLabel 失败",
        message = resolveQualitySwitchFailureMessage(
            requestedQualityLabel = requestedQualityLabel,
            permissionResult = permissionResult,
            loadError = loadError,
            hasCachedDashTracks = hasCachedDashTracks,
            cacheContainsRequestedQuality = cacheContainsRequestedQuality,
            qualityRefetchCooldownRemainingMs = qualityRefetchCooldownRemainingMs
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

internal fun clearSubtitleFields(state: PlayerUiState.Success): PlayerUiState.Success {
    return state.copy(
        subtitleEnabled = false,
        subtitleOwnerBvid = null,
        subtitleOwnerCid = 0L,
        subtitlePrimaryLanguage = null,
        subtitleSecondaryLanguage = null,
        subtitlePrimaryTrackKey = null,
        subtitleSecondaryTrackKey = null,
        subtitlePrimaryLikelyAi = false,
        subtitleSecondaryLikelyAi = false,
        subtitlePrimaryCues = emptyList(),
        subtitleSecondaryCues = emptyList()
    )
}

internal fun clearTransientPlaybackPreviewData(state: PlayerUiState.Success): PlayerUiState.Success {
    return if (state.videoshotData == null) state else state.copy(videoshotData = null)
}

internal fun shouldApplyVideoshotResult(
    currentState: PlayerUiState.Success,
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

// ========== ViewModel ==========
class PlayerViewModel : ViewModel() {
    // UseCases
    private val playbackUseCase = VideoPlaybackUseCase()
    private val playbackLoader = PlaybackLoader.from(playbackUseCase)
    private val playbackSessionStore = PlaybackSessionStore()
    private val playbackCoordinator = PlaybackCoordinator(playbackSessionStore)
    private val interactionUseCase = VideoInteractionUseCase()
    private val qualityManager = QualityManager()
    
    //  插件系统（替代旧的SponsorBlockUseCase）
    private var pluginCheckJob: Job? = null
    
    // State
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading.Initial)
    val uiState = _uiState.asStateFlow()
    
    private val _toastEvent = Channel<PlayerToastMessage>()
    val toastEvent = _toastEvent.receiveAsFlow()
    private val _qualitySwitchFailureDialog = MutableStateFlow<QualitySwitchFailureDialogState?>(null)
    internal val qualitySwitchFailureDialog = _qualitySwitchFailureDialog.asStateFlow()

    val resumePlaybackSuggestion = playbackSessionStore.state
        .map { session -> session.resumeSuggestion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // Celebration animations
    private val _likeBurstVisible = MutableStateFlow(false)
    val likeBurstVisible = _likeBurstVisible.asStateFlow()
    
    private val _tripleCelebrationVisible = MutableStateFlow(false)
    val tripleCelebrationVisible = _tripleCelebrationVisible.asStateFlow()
    
    // Coin dialog
    private val _coinDialogVisible = MutableStateFlow(false)
    val coinDialogVisible = _coinDialogVisible.asStateFlow()

    
    // [New] User Coin Balance
    // [New] User Coin Balance
    private val _userCoinBalance = MutableStateFlow<Double?>(null)
    val userCoinBalance = _userCoinBalance.asStateFlow()

    fun showCoinDialog() {
        _coinDialogVisible.value = true
        fetchUserCoins()
    }
    
    private fun fetchUserCoins() {
        viewModelScope.launch {
            _userCoinBalance.value = null // Loading
            try {
                // Check if we even have a local token
                if (com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()) {
                     com.android.purebilibili.core.util.Logger.e("PlayerViewModel", "fetchUserCoins: No local token found")
                    _userCoinBalance.value = -4.0 // Local Token Missing
                    return@launch
                }

                com.android.purebilibili.core.util.Logger.d("PlayerViewModel", "fetchUserCoins calls getNavInfo")
                
                // [Fix] Use IO dispatcher and timeout to prevent hanging
                val result = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(5000L) {
                        com.android.purebilibili.core.network.NetworkModule.api.getNavInfo()
                    }
                }
                
                com.android.purebilibili.core.util.Logger.d("PlayerViewModel", 
                    "NavInfo: code=${result.code}, isLogin=${result.data?.isLogin}, money=${result.data?.money}, wallet=${result.data?.wallet?.bcoin_balance}")
                
                if (result.code == 0 && result.data != null) {
                    if (result.data.isLogin) {
                        _userCoinBalance.value = result.data.money
                    } else {
                        com.android.purebilibili.core.util.Logger.w("PlayerViewModel", "User not logged in according to getNavInfo")
                        _userCoinBalance.value = -3.0 // API says Not Logged In
                    }
                } else {
                    com.android.purebilibili.core.util.Logger.e("PlayerViewModel", "getNavInfo failed: code=${result.code}")
                    _userCoinBalance.value = -1.0 // Network/API Error
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("PlayerViewModel", "fetchUserCoins Error: ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
                _userCoinBalance.value = -2.0 // Exception (Network or Timeout)
            }
        }
    }



    fun dismissCoinDialog() {
        _coinDialogVisible.value = false
    }

    fun dismissQualitySwitchFailureDialog() {
        _qualitySwitchFailureDialog.value = null
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
    
    //  Download state
    private val _downloadProgress = MutableStateFlow(-1f)
    val downloadProgress = _downloadProgress.asStateFlow()
    
    //  [新增] 视频章节/看点数据
    private val _viewPoints = MutableStateFlow<List<ViewPoint>>(emptyList())
    val viewPoints = _viewPoints.asStateFlow()

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
    private var heartbeatJob: Job? = null
    private var heartbeatSessionStartTsSec: Long = 0L
    private var heartbeatAccumulatedPlayMs: Long = 0L
    private var heartbeatActivePlayStartElapsedMs: Long? = null
    private var lastReportedHeartbeatSnapshot: PlaybackHeartbeatSnapshot? = null
    private var lastPluginDispatchPositionMs: Long? = null
    private var appContext: android.content.Context? = null  //  [新增] 保存 Context 用于网络检测
    private var hasUserStartedPlayback = false  // 🛡️ [修复] 用户是否主动开始播放（用于区分“加载已看完视频”和“自然播放结束”）
    private var isPortraitPlaybackSessionActive = false
    private val followStatusCheckInFlight = mutableSetOf<Long>()
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
    private var playerInfoJob: Job? = null
    private var aiSummaryJob: Job? = null
    
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
    }

    fun setPortraitPlaybackSessionActive(active: Boolean) {
        isPortraitPlaybackSessionActive = active
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
                withContext(kotlinx.coroutines.Dispatchers.Main) {
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
        val currentAid = (_uiState.value as? PlayerUiState.Success)?.info?.aid
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
        val currentAid = (_uiState.value as? PlayerUiState.Success)?.info?.aid
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
            if (state is PlayerUiState.Success) {
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
        val currentAid = (_uiState.value as? PlayerUiState.Success)?.info?.aid
        if (!shouldSyncFavoriteFolderUiState(targetAid = targetAid, currentAid = currentAid)) {
            return
        }
        _uiState.update { state ->
            if (state is PlayerUiState.Success) {
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
                    com.android.purebilibili.core.util.Logger.d("PlayerViewModel", "🎵 Audio preference updated from Settings to: $it")
                    _audioQualityPreference.value = it 
                }
        }
    }

    private fun bootstrapContextIfNeeded() {
        val globalContext = com.android.purebilibili.core.network.NetworkModule.appContext
        if (shouldBootstrapPlayerContext(
                hasBoundContext = appContext != null,
                hasGlobalContext = globalContext != null
            )
        ) {
            initWithContext(requireNotNull(globalContext))
            Logger.d("PlayerVM", "♻️ Bootstrapped PlayerViewModel context from NetworkModule")
        }
    }
    
    fun attachPlayer(player: ExoPlayer) {
        val changed = exoPlayer !== player
        val previousPlayer = exoPlayer

        if (changed && previousPlayer != null) {
            flushPlaybackHeartbeatSnapshot(reason = "replace_player")
            saveCurrentPosition()
            // 切换播放器时立即停止旧实例，避免转场期间双播
            previousPlayer.removeListener(playbackEndListener)
            previousPlayer.playWhenReady = false
            previousPlayer.pause()
        }

        exoPlayer = player
        playbackUseCase.attachPlayer(player)
        player.volume = 1.0f
        
        // 防止重复添加同一个 listener（同一 player 多次 attach 的场景）
        player.removeListener(playbackEndListener)
        player.addListener(playbackEndListener)
    }
    
    //  [新增] 播放完成监听器
    private val playbackEndListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
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

                val behavior = com.android.purebilibili.core.store.SettingsManager
                    .getPlaybackCompletionBehaviorSync(context)
                val action = playbackCoordinator.resolvePlaybackEnded(
                    behavior = behavior,
                    autoPlayEnabled = autoPlayEnabled,
                    isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
                    externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled,
                    externalPlaylistSource = PlaylistManager.externalPlaylistSource.value,
                    playMode = PlaylistManager.playMode.value
                )
                val outcome = playbackCoordinator.executePlaybackEndAction(
                    action = action,
                    repeatCurrent = {
                        exoPlayer?.seekTo(0)
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                    },
                    playNextInOrder = { ignoreSavedProgress ->
                        playNextInOrder(ignoreSavedProgress = ignoreSavedProgress)
                    },
                    playNextFromPlaylistLoop = { ignoreSavedProgress ->
                        playNextFromPlaylist(
                            loopAtEnd = true,
                            ignoreSavedProgress = ignoreSavedProgress
                        )
                    },
                    autoContinue = { ignoreSavedProgress ->
                        playNextPageOrRecommended(ignoreSavedProgress = ignoreSavedProgress)
                    }
                )
                if (outcome.shouldHidePlaybackEndedDialog) {
                    // 自动播放关闭或后续无法连播：保持结束态，不弹窗打断
                    _showPlaybackEndedDialog.value = false
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncHeartbeatPlaybackTracking(
                isActivelyPlaying = isPlaying && !BackgroundManager.isInBackground
            )
            if (isPlaying) {
                // 🛡️ [修复] 用户开始播放时设置标志
                hasUserStartedPlayback = true
            }
        }
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
    fun playNextRecommended(ignoreSavedProgress: Boolean = false): Boolean {
        // 使用 PlaylistManager 获取下一曲
        val nextItem = PlaylistManager.playNext()
        
        if (nextItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${nextItem.title}")
            }
            // 加载新视频 (Auto-play next always forces true)
            loadVideo(
                nextItem.bvid,
                autoPlay = true,
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
        val currentInfo = (_uiState.value as? PlayerUiState.Success)?.info
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
        ignoreSavedProgress: Boolean = false
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
        loadVideo(
            target.bvid,
            autoPlay = true,
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
        loadVideo(
            target.bvid,
            autoPlay = true,
            ignoreSavedProgress = ignoreSavedProgress
        )
        return true
    }

    private fun resolveCurrentNextAvailability(): Triple<Boolean, Boolean, Boolean> {
        val current = _uiState.value as? PlayerUiState.Success
        val hasNextPage = current?.let { success ->
            val pages = success.info.pages
            if (pages.size <= 1) {
                false
            } else {
                val nextPageIndex = pages.indexOfFirst { it.cid == currentCid } + 1
                nextPageIndex < pages.size
            }
        } ?: false

        val hasNextSeasonEpisode = current?.info?.ugc_season?.let { season ->
            val allEpisodes = season.sections.flatMap { it.episodes }
            val nextEpIndex = allEpisodes.indexOfFirst { it.bvid == current.info.bvid } + 1
            nextEpIndex < allEpisodes.size
        } ?: false

        return Triple(hasNextPage, hasNextSeasonEpisode, hasNextInPlaylist(loopAtEnd = false))
    }

    private fun resolveCurrentPreviousAvailability(): Triple<Boolean, Boolean, Boolean> {
        val current = _uiState.value as? PlayerUiState.Success
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
            val previousEpIndex = allEpisodes.indexOfFirst { it.bvid == current.info.bvid } - 1
            previousEpIndex >= 0
        } ?: false

        return Triple(hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousInPlaylist())
    }

    private fun playNextInOrder(ignoreSavedProgress: Boolean = false): Boolean {
        val (hasNextPage, hasNextSeasonEpisode, hasNextPlaylistItem) = resolveCurrentNextAvailability()
        return when (
            resolvePlayInOrderNextSource(
                hasNextPage = hasNextPage,
                hasNextSeasonEpisode = hasNextSeasonEpisode,
                hasNextPlaylistItem = hasNextPlaylistItem
            )
        ) {
            PlayInOrderNextSource.PAGE_OR_SEASON ->
                playNextPageOrSeason(ignoreSavedProgress = ignoreSavedProgress) ||
                    playNextFromPlaylist(
                        loopAtEnd = false,
                        ignoreSavedProgress = ignoreSavedProgress
                    )
            PlayInOrderNextSource.PLAYLIST ->
                playNextFromPlaylist(
                    loopAtEnd = false,
                    ignoreSavedProgress = ignoreSavedProgress
                )
            PlayInOrderNextSource.NONE -> false
        }
    }

    private fun playNextPageOrSeason(ignoreSavedProgress: Boolean = false): Boolean {
        val current = _uiState.value as? PlayerUiState.Success ?: return false

        // 1. 优先检查分P
        val pages = current.info.pages
        if (pages.size > 1) {
            val currentPageIndex = pages.indexOfFirst { it.cid == currentCid }
            val nextPageIndex = currentPageIndex + 1

            if (nextPageIndex < pages.size) {
                val nextPage = pages[nextPageIndex]
                Logger.d("PlayerVM", "🎵 播放下一个分P: P${nextPageIndex + 1} - ${nextPage.part}")
                switchPage(nextPageIndex, ignoreSavedProgress = ignoreSavedProgress)
                return true
            }
        }

        // 2. 检查合集 (UGC Season)
        current.info.ugc_season?.let { season ->
            val allEpisodes = season.sections.flatMap { it.episodes }
            val currentEpIndex = allEpisodes.indexOfFirst { it.bvid == current.info.bvid }
            val nextEpIndex = currentEpIndex + 1

            if (nextEpIndex < allEpisodes.size) {
                val nextEpisode = allEpisodes[nextEpIndex]
                Logger.d("PlayerVM", "📂 播放合集下一集: ${nextEpisode.title}")
                viewModelScope.launch {
                    toast("播放合集下一集: ${nextEpisode.title}")
                }
                loadVideo(
                    nextEpisode.bvid,
                    autoPlay = true,
                    ignoreSavedProgress = ignoreSavedProgress,
                    cid = nextEpisode.cid
                )
                return true
            }
            Logger.d("PlayerVM", "📂 合集全部播放完成")
        }

        return false
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
        val current = _uiState.value as? PlayerUiState.Success ?: return false

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
            val currentEpIndex = allEpisodes.indexOfFirst { it.bvid == current.info.bvid }
            val previousEpIndex = currentEpIndex - 1

            if (previousEpIndex >= 0) {
                val previousEpisode = allEpisodes[previousEpIndex]
                Logger.d("PlayerVM", "📂 播放合集上一集: ${previousEpisode.title}")
                viewModelScope.launch {
                    toast("播放合集上一集: ${previousEpisode.title}")
                }
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
        val nextStrategy = resolveAudioNextPlaybackStrategy(
            isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
            externalPlaylistSource = PlaylistManager.externalPlaylistSource.value
        )
        if (nextStrategy == AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST) {
            return hasNextInPlaylist(loopAtEnd = false)
        }
        val (hasNextPage, hasNextSeasonEpisode, hasNextPlaylistItem) = resolveCurrentNextAvailability()
        return hasNextPage || hasNextSeasonEpisode || hasNextPlaylistItem
    }

    private fun hasPreviousPageOrRecommended(): Boolean {
        val previousStrategy = resolveAudioNextPlaybackStrategy(
            isExternalPlaylist = PlaylistManager.isExternalPlaylist.value,
            externalPlaylistSource = PlaylistManager.externalPlaylistSource.value
        )
        if (previousStrategy == AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST) {
            return hasPreviousInPlaylist()
        }
        val (hasPreviousPage, hasPreviousSeasonEpisode, hasPreviousPlaylistItem) =
            resolveCurrentPreviousAvailability()
        return hasPreviousPage || hasPreviousSeasonEpisode || hasPreviousPlaylistItem
    }

    /**
     * 🎵 [新增] 优先播放下一个分P，如果没有分P则检查合集，最后播放推荐视频
     * 用于分集视频（如音乐合集）的连续播放
     * 优先级: 分P > 合集下一集 > 推荐视频
     */
    fun playNextPageOrRecommended(ignoreSavedProgress: Boolean = false): Boolean {
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
                playNextPageOrSeason(ignoreSavedProgress = ignoreSavedProgress)
            },
            playPlaylist = {
                playNextFromPlaylist(
                    loopAtEnd = false,
                    ignoreSavedProgress = ignoreSavedProgress
                )
            },
            playDirectQueue = {
                Logger.d("PlayerVM", "🎵 播放推荐视频")
                playNextRecommended(ignoreSavedProgress = ignoreSavedProgress)
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

    private fun playPreviousFromRecommendedQueue(ignoreSavedProgress: Boolean = false): Boolean {
        val prevItem = PlaylistManager.playPrevious()

        if (prevItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${prevItem.title}")
            }
            loadVideo(
                prevItem.bvid,
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
        loadVideo(bvid, force = true, autoPlay = true, cid = currentCid)
        
        // 如果之前有进度，尝试恢复
        // 注意：loadVideo 是异步的，这里只是一个兜底，主要还是靠 loadVideo 内部读取 cachedPosition
        if (currentPos > 1000) {
             viewModelScope.launch {
                 delay(500)
                 if (exoPlayer?.currentPosition ?: 0L < 1000) {
                     seekTo(currentPos)
                 }
             }
        }
    }

    fun retryAiSummary() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
        Logger.d(
            "PlayerVM",
            "SUB_DBG loadVideo start: request=${playbackRequest.bvid}/${playbackRequest.cid}, aid=${playbackRequest.aid}, force=${playbackRequest.force}, current=$currentBvid/$currentCid, ui=${(_uiState.value as? PlayerUiState.Success)?.info?.bvid}/${(_uiState.value as? PlayerUiState.Success)?.info?.cid}"
        )
        
        //  防止重复加载：只有在正在加载同一视频时才跳过 (且语言未改变)
        val currentLang = (_uiState.value as? PlayerUiState.Success)?.currentAudioLang
        val isSameLang = currentLang == playbackRequest.audioLang
        
        if (!playbackRequest.force &&
            currentBvid == playbackRequest.bvid &&
            isSameLang &&
            _uiState.value is PlayerUiState.Loading
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
        
        val currentSuccess = _uiState.value as? PlayerUiState.Success
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

        if (!playbackRequest.force && isPlayerPlayingSameVideo && isUiLoaded) {
            Logger.d("PlayerVM", "🎯 ${playbackRequest.bvid} already playing healthy + UI loaded, skip reload")
            // 补全 ViewModel 状态：currentBvid 可能为空，需要同步
            if (currentBvid.isEmpty()) {
                currentBvid = playbackRequest.bvid
            }
            if (currentCid <= 0L && currentSuccess.info.cid > 0L) {
                currentCid = currentSuccess.info.cid
            }
            
            //  确保音量正常
            player?.volume = 1.0f
            if (player?.isPlaying == false) {
                player.play()
            }
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
        playerInfoJob?.cancel()
        activeLoadJob?.cancel()
        
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
            _uiState.value = PlayerUiState.Loading.Initial
            
                val defaultQuality = appContext?.let {
                    NetworkUtils.getPlayableDefaultQualityId(
                        context = it,
                        isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty() ||
                            !com.android.purebilibili.core.store.TokenManager.accessTokenCache.isNullOrEmpty(),
                        isVip = com.android.purebilibili.core.store.TokenManager.isVipCache
                    )
                } ?: 64
                //  [新增] 获取音频/视频偏好
                val audioQualityPreference = appContext?.let { 
                    com.android.purebilibili.core.store.SettingsManager.getAudioQualitySync(it) 
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
                val videoSecondCodecPreference = appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getVideoSecondCodecSync(it)
                } ?: AVC_CODEC_KEY
                val isHdrSupported = appContext?.let {
                    com.android.purebilibili.core.util.MediaUtils.isHdrSupported(it)
                } ?: com.android.purebilibili.core.util.MediaUtils.isHdrSupported()
                val isDolbyVisionSupported = appContext?.let {
                    com.android.purebilibili.core.util.MediaUtils.isDolbyVisionSupported(it)
                } ?: com.android.purebilibili.core.util.MediaUtils.isDolbyVisionSupported()
                val isAv1Supported = resolveEffectiveAv1Support(
                    deviceSupportsAv1 = com.android.purebilibili.core.util.MediaUtils.isAv1Supported(),
                    sessionBlockedCodecs = sessionBlockedCodecs
                )
                
                // [Added] Determine auto-play behavior
                // If autoPlay arg is present, use it. Otherwise reset to "Click to Play" setting
                val shouldAutoPlay = playbackRequest.autoPlay ?: appContext?.let {
                    com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(it)
                } ?: true
                
                Logger.d(
                    "PlayerViewModel",
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
            if (shouldLimitQuality && finalQuality > 32) {
                finalQuality = 32
                com.android.purebilibili.core.util.Logger.d("PlayerViewModel", "📉 省流量模式(${dataSaverMode.label}): 限制画质为480P")
            }
            
            try {
                val loadConfig = PlaybackLoadConfig(
                    defaultQuality = finalQuality,
                    audioQualityPreference = audioQualityPreference,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    playWhenReady = shouldAutoPlay,
                    isAv1Supported = isAv1Supported,
                    isHdrSupported = isHdrSupported,
                    isDolbyVisionSupported = isDolbyVisionSupported
                )
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

                        // Play video
                        if (!shouldSkipPlayerPrepare) {
                            playResolvedPlayback(
                                videoUrl = result.playUrl,
                                audioUrl = result.audioUrl,
                                adaptiveDashSource = result.adaptiveDashSource,
                                startPositionMs = startPos,
                                playWhenReady = shouldAutoPlay
                            )
                        } else {
                             // 🎯 Skip preparing player, but ensure it's playing if needed
                             Logger.d("PlayerVM", "🎯 Skipping player preparation (already playing)")
                             exoPlayer?.let { p ->
                                 p.volume = 1.0f
                                 if (!p.isPlaying) p.play()
                             }
                        }
                        
                        //  收集所有 CDN URL (主+备用)
                        val allVideoUrls = buildList {
                            add(result.playUrl)
                            result.cachedDashVideos
                                .find { it.id == result.quality }
                                ?.backupUrl
                                ?.filterNotNull()
                                ?.filter { it.isNotEmpty() }
                                ?.let { addAll(it) }
                        }.distinct()
                        
                        val allAudioUrls = buildList {
                            result.audioUrl?.let { add(it) }
                            result.cachedDashAudios.firstOrNull()
                                ?.backupUrl
                                ?.filterNotNull()
                                ?.filter { it.isNotEmpty() }
                                ?.let { addAll(it) }
                        }.distinct()
                        
                        Logger.d("PlayerVM", "📡 CDN 线路: 视频${allVideoUrls.size}个, 音频${allAudioUrls.size}个")
                        
                        _uiState.value = PlayerUiState.Success(
                            info = result.info,
                            playUrl = result.playUrl,
                            audioUrl = result.audioUrl,
                            related = result.related,
                            currentQuality = result.quality,
                            playbackQualityMode = resolveInitialPlaybackQualityMode(),
                            adaptiveDashSource = result.adaptiveDashSource,
                            qualityIds = result.qualityIds,
                            qualityLabels = result.qualityLabels,
                            cachedDashVideos = result.cachedDashVideos,
                            cachedDashAudios = result.cachedDashAudios,
                            emoteMap = result.emoteMap,
                            isLoggedIn = result.isLoggedIn,
                            isVip = result.isVip,
                            isFollowing = result.isFollowing,
                            isFavorited = result.isFavorited,
                            isLiked = result.isLiked,
                            coinCount = result.coinCount,
                            //  CDN 线路
                            currentCdnIndex = 0,
                            allVideoUrls = allVideoUrls,

                            allAudioUrls = allAudioUrls,

                            // [New] Codec/Audio info
                            videoCodecId = result.videoCodecId,
                            audioCodecId = result.audioCodecId,
                            // [New] AI Audio
                            aiAudio = result.aiAudio,
                            currentAudioLang = result.curAudioLang,
                            videoDurationMs = result.duration
                        )
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

                        //  [新增] 更新播放列表
                        updatePlaylist(result.info, result.related)

                        AnalyticsHelper.logVideoPlay(
                            playbackRequest.bvid,
                            result.info.title,
                            result.info.owner.name
                        )
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
                        CrashReporter.reportVideoError(
                            playbackRequest.bvid,
                            "load_failed",
                            loadResult.error.toUserMessage()
                        )
                        Logger.d(
                            "PlayerVM",
                            "SUB_DBG loadVideo error: requested=${playbackRequest.bvid}/${playbackRequest.cid}, token=$requestToken, error=${loadResult.error}"
                        )
                        _uiState.value = PlayerUiState.Error(loadResult.error, loadResult.canRetry)
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
                Logger.e("PlayerVM", "⚠️ Video load timed out for ${playbackRequest.bvid}")
                PlaybackCooldownManager.recordFailure(playbackRequest.bvid, "timeout")
                _uiState.value = PlayerUiState.Error(VideoLoadError.Timeout)
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
                Logger.e("PlayerVM", "⚠️ Unexpected load exception", e)
                _uiState.value = PlayerUiState.Error(VideoLoadError.UnknownError(e))
            } finally {
                if (activeLoadJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    activeLoadJob = null
                }
            }
        }
    }
    
    /**
     * [New] Change Audio Language (AI Translation)
     */
    fun changeAudioLanguage(lang: String?) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
            viewModelScope.launch { toast("当前弹幕不支持投票") }
            return
        }

        val menuState = _danmakuMenuState.value
        val shouldLike = if (menuState.visible && menuState.dmid == dmid && menuState.canVote) {
            !menuState.hasLiked
        } else {
            true
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

        // 🔒 [修复] 检查是否为外部播放列表（稍后再看、UP主页等）
        // 如果是外部播放列表，只更新当前索引，不覆盖列表
        if (externalDecision.keepExternalPlaylist) {
            val matchIndex = externalDecision.matchedIndex
            if (matchIndex in currentPlaylist.indices) {
                // 找到当前视频在列表中的位置，更新索引
                PlaylistManager.playAt(matchIndex)
                Logger.d("PlayerVM", "🔒 外部播放列表模式: 更新索引到 $matchIndex/${currentPlaylist.size}")
            }
            return
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
                title = video.title,
                cover = video.pic,
                owner = video.owner.name,
                duration = video.duration.toLong()
            )
        }
        
        // 创建当前视频的播放项 (updated with full info)
        val currentFullItem = PlaylistItem(
            bvid = currentInfo.bvid,
            title = currentInfo.title,
            cover = currentInfo.pic,
            owner = currentInfo.owner.name,
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
        
        // 首播优先：仅在 Wi-Fi 下预加载 1 条，避免与当前视频抢带宽。
        preloadRelatedPlayUrls(related.take(1))
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
                    val defaultQuality = appContext?.let {
                        com.android.purebilibili.core.util.NetworkUtils.getPlayableDefaultQualityId(
                            context = it,
                            isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty() ||
                                !com.android.purebilibili.core.store.TokenManager.accessTokenCache.isNullOrEmpty(),
                            isVip = com.android.purebilibili.core.store.TokenManager.isVipCache
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
        
        //  检查当前错误类型，如果是全局冷却则清除所有冷却
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Error && 
            currentState.error is VideoLoadError.GlobalCooldown) {
            PlaybackCooldownManager.clearAll()
        } else {
            // 清除该视频的冷却状态，允许用户强制重试
            PlaybackCooldownManager.clearForVideo(bvid)
        }
        
        PlayUrlCache.invalidate(bvid, currentCid)
        playbackSessionStore.clearCurrentMedia()
        loadVideo(bvid, autoPlay = true) // Retry should auto-play
    }

    /**
     * 解码类错误时的安全重试：强制使用 AVC，规避特定机型 HEVC/AV1 解码异常导致的卡死。
     */
    fun retryWithCodecFallback() {
        val current = _uiState.value as? PlayerUiState.Success ?: run {
            retry()
            return
        }

        val bvid = current.info.bvid.takeIf { it.isNotBlank() } ?: return
        playbackSessionStore.blockVideoCodec(AV1_CODEC_KEY)
        PlaybackCooldownManager.clearForVideo(bvid)
        PlayUrlCache.invalidate(bvid, current.info.cid)
        playbackSessionStore.clearCurrentMedia()
        Logger.w("PlayerVM", "🛟 Retrying with safe codec fallback: AVC")
        loadVideo(
            bvid = bvid,
            aid = current.info.aid,
            force = true,
            autoPlay = true,
            audioLang = current.currentAudioLang,
            videoCodecOverride = AVC_CODEC_KEY
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
        val current = _uiState.value as? PlayerUiState.Success ?: return
        
        if (current.cdnCount <= 1) {
            viewModelScope.launch { toast("没有其他可用线路") }
            return
        }
        
        // 计算下一个 CDN 索引（循环）
        val nextIndex = (current.currentCdnIndex + 1) % current.cdnCount
        val nextVideoUrl = current.allVideoUrls.getOrNull(nextIndex) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(nextIndex)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换线路: ${current.currentCdnIndex + 1} → ${nextIndex + 1}")
            
            // 使用新的 URL 播放
            playResolvedPlayback(
                videoUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                startPositionMs = currentPos
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
        val current = _uiState.value as? PlayerUiState.Success ?: return
        
        if (index < 0 || index >= current.cdnCount) return
        if (index == current.currentCdnIndex) {
            viewModelScope.launch { toast("已是当前线路") }
            return
        }
        
        val nextVideoUrl = current.allVideoUrls.getOrNull(index) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(index)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换到线路: ${index + 1}")
            
            playResolvedPlayback(
                videoUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                adaptiveDashSource = null,
                startPositionMs = currentPos
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
    fun restoreUiState(state: PlayerUiState.Success) {
        // 只有当前是非成功状态，或者虽然是成功状态但 BVID 不同时，才允许恢复
        // 这样可以避免覆盖当前可能更新的状态
        if (_uiState.value !is PlayerUiState.Success || 
            (_uiState.value as? PlayerUiState.Success)?.info?.bvid != state.info.bvid) {
            
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
    }

    // ========== Interaction ==========
    
    fun toggleFollow() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleFollow(current.info.owner.mid, current.isFollowing)
                .onSuccess {
                    _uiState.update { state ->
                        if (state is PlayerUiState.Success) {
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
                        if (state is PlayerUiState.Success) {
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
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleFavorite(
                aid = current.info.aid,
                currentlyFavorited = current.isFavorited,
                bvid = current.info.bvid
            ).onSuccess { favorited ->
                _uiState.update { state ->
                    if (state is PlayerUiState.Success) {
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
                toast(if (favorited) "已收藏" else "已取消收藏")
            }.onFailure { e ->
                toast(e.message ?: "收藏操作失败")
            }
        }
    }
    
    fun toggleLike() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
                    val current = _uiState.value as? PlayerUiState.Success
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
                    if (liked) _likeBurstVisible.value = true
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
        val current = _uiState.value as? PlayerUiState.Success
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
                "PlayerViewModel",
                "Recorded not interested feedback: bvid=${current.info.bvid}, mid=${current.info.owner.mid}"
            )
            withContext(Dispatchers.Main) {
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
                    android.util.Log.d("PlayerViewModel", "📦 Emotes loaded: ${it.size} packages")
                }
                .onFailure { Logger.e("PlayerViewModel", "Failed to load emotes", it) }
        }
    }
    
    fun showCommentInputDialog() {
        android.util.Log.d("PlayerViewModel", "📝 showCommentInputDialog called")
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
    }

    // ========== 弹幕发送 ==========
    
    private val _showDanmakuDialog = MutableStateFlow(false)
    val showDanmakuDialog = _showDanmakuDialog.asStateFlow()
    
    private val _isSendingDanmaku = MutableStateFlow(false)
    val isSendingDanmaku = _isSendingDanmaku.asStateFlow()
    
    fun showDanmakuSendDialog() {
        _showDanmakuDialog.value = true
    }
    
    fun hideDanmakuSendDialog() {
        _showDanmakuDialog.value = false
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
        fontSize: Int = 25
    ) {
        val current = _uiState.value as? PlayerUiState.Success ?: run {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        
        if (currentCid == 0L) {
            viewModelScope.launch { toast("视频未加载") }
            return
        }
        
        val progress = exoPlayer?.currentPosition ?: 0L
        
        viewModelScope.launch {
            _isSendingDanmaku.value = true
            
            com.android.purebilibili.data.repository.DanmakuRepository
                .sendDanmaku(
                    aid = current.info.aid,
                    cid = currentCid,
                    message = message,
                    progress = progress,
                    color = color,
                    fontSize = fontSize,
                    mode = mode
                )
                .onSuccess {
                    toast("发送成功")
                    _showDanmakuDialog.value = false
                    
                    // 本地即时显示弹幕
                    // 注意：这需要在 Composable 中通过 DanmakuManager 调用
                    // 这里只发送事件通知
                    _danmakuSentEvent.trySend(DanmakuSentData(message, color, mode, fontSize))
                }
                .onFailure { error ->
                    toast(error.message ?: "发送失败")
                }
            
            _isSendingDanmaku.value = false
        }
    }
    
    // 弹幕发送成功事件（用于本地显示）
    data class DanmakuSentData(val text: String, val color: Int, val mode: Int, val fontSize: Int)
    private val _danmakuSentEvent = Channel<DanmakuSentData>(
        capacity = resolvePlayerTransientEventChannelCapacity()
    )
    val danmakuSentEvent = _danmakuSentEvent.receiveAsFlow()
    
    // ========== 弹幕上下文菜单 ==========
    data class DanmakuMenuState(
        val visible: Boolean = false,
        val text: String = "",
        val dmid: Long = 0,
        val uid: Long = 0, // 发送者 UID (如果可用)
        val isSelf: Boolean = false, // 是否是自己发送的
        val voteCount: Int = 0,
        val hasLiked: Boolean = false,
        val voteLoading: Boolean = false,
        val canVote: Boolean = false
    )
    
    private val _danmakuMenuState = MutableStateFlow(DanmakuMenuState())
    val danmakuMenuState = _danmakuMenuState.asStateFlow()
    
    fun showDanmakuMenu(dmid: Long, text: String, uid: Long = 0, isSelf: Boolean = false) {
        val supportsVote = dmid > 0L && currentCid > 0L
        _danmakuMenuState.value = DanmakuMenuState(
            visible = true,
            text = text,
            dmid = dmid,
            uid = uid,
            isSelf = isSelf,
            voteLoading = supportsVote,
            canVote = supportsVote
        )
        if (supportsVote) {
            refreshDanmakuThumbupState(dmid)
        }
        // 暂停播放 (可选，防止弹幕飘走)
        // if (exoPlayer?.isPlaying == true) exoPlayer?.pause()
    }
    
    fun hideDanmakuMenu() {
        _danmakuMenuState.value = _danmakuMenuState.value.copy(visible = false)
        // 恢复播放?
    }

    private fun refreshDanmakuThumbupState(dmid: Long) {
        if (dmid <= 0L || currentCid <= 0L) return

        viewModelScope.launch {
            com.android.purebilibili.data.repository.DanmakuRepository
                .getDanmakuThumbupState(cid = currentCid, dmid = dmid)
                .onSuccess { thumbupState ->
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
                .onFailure {
                    _danmakuMenuState.update { current ->
                        if (!current.visible || current.dmid != dmid) current
                        else current.copy(voteLoading = false, canVote = false)
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
            viewModelScope.launch { toast("当前弹幕不支持投票") }
            return
        }

        _danmakuMenuState.update { current ->
            if (!current.visible || current.dmid != dmid) current
            else current.copy(voteLoading = true)
        }
        
        viewModelScope.launch {
            com.android.purebilibili.data.repository.DanmakuRepository
                .likeDanmaku(cid = currentCid, dmid = dmid, like = like)
                .onSuccess {
                    _danmakuMenuState.update { current ->
                        if (!current.visible || current.dmid != dmid) current
                        else {
                            val delta = when {
                                like && !current.hasLiked -> 1
                                !like && current.hasLiked -> -1
                                else -> 0
                            }
                            current.copy(
                                hasLiked = like,
                                voteCount = (current.voteCount + delta).coerceAtLeast(0),
                                voteLoading = false,
                                canVote = true
                            )
                        }
                    }
                    toast(if (like) "点赞成功" else "已取消点赞")
                    refreshDanmakuThumbupState(dmid)
                }
                .onFailure { error ->
                    _danmakuMenuState.update { current ->
                        if (!current.visible || current.dmid != dmid) current
                        else current.copy(voteLoading = false)
                    }
                    toast(error.message ?: "操作失败")
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
    
    fun setCommentInput(text: String) {
        _commentInput.value = text
    }
    
    fun setReplyingTo(comment: com.android.purebilibili.data.model.response.ReplyItem?) {
        _replyingToComment.value = comment
    }
    
    fun clearReplyingTo() {
        _replyingToComment.value = null
    }
    
    /**
     * 发送评论
     * @param inputMessage 可选直接传入的内容，如果不传则使用 state 中的内容
     */
    fun sendComment(inputMessage: String? = null, imageUris: List<Uri> = emptyList()) {
        if (inputMessage != null) {
            _commentInput.value = inputMessage
        }
        val current = _uiState.value as? PlayerUiState.Success ?: return
        val message = _commentInput.value.trim()
        
        if (message.isEmpty()) {
            viewModelScope.launch { toast("请输入评论内容") }
            return
        }
        
        viewModelScope.launch {
            _isSendingComment.value = true
            
            val replyTo = _replyingToComment.value
            val (root, parent) = resolveCommentReplyTargets(
                replyRpid = replyTo?.rpid,
                replyRoot = replyTo?.root
            )
            val picturesResult = uploadCommentPictures(imageUris)
            val pictures = picturesResult.getOrElse { uploadError ->
                Logger.e(
                    "PlayerVM",
                    "Comment image upload failed: aid=${current.info.aid}, imageCount=${imageUris.size}, message=${uploadError.message}",
                    uploadError
                )
                toast(uploadError.message ?: "图片上传失败")
                _isSendingComment.value = false
                return@launch
            }
            
            com.android.purebilibili.data.repository.CommentRepository
                .addComment(
                    aid = current.info.aid,
                    message = message,
                    root = root,
                    parent = parent,
                    pictures = pictures
                )
                .onSuccess { reply ->
                    toast(if (replyTo != null) "回复成功" else "评论成功")
                    _commentInput.value = ""
                    _replyingToComment.value = null
                    
                    // 通知 UI 刷新评论列表
                    _commentSentEvent.trySend(reply)
                }
                .onFailure { error ->
                    Logger.e(
                        "PlayerVM",
                        "Comment send failed: aid=${current.info.aid}, root=$root, parent=$parent, pictureCount=${pictures.size}, message=${error.message}",
                        error
                    )
                    toast(error.message ?: "发送失败")
                }
            
            _isSendingComment.value = false
        }
    }

    private suspend fun uploadCommentPictures(imageUris: List<Uri>): Result<List<ReplyPicture>> {
        if (imageUris.isEmpty()) return Result.success(emptyList())
        val context = appContext ?: return Result.failure(Exception("应用上下文不可用"))
        val selectedUris = imageUris.take(9)
        return withContext(Dispatchers.IO) {
            runCatching {
                selectedUris.mapIndexed { index, uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    } ?: error("无法读取图片文件")

                    if (bytes.isEmpty()) {
                        error("图片内容为空")
                    }
                    if (bytes.size > 15 * 1024 * 1024) {
                        error("图片过大（单张最大 15MB）")
                    }

                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val fileName = queryDisplayName(context, uri)
                        ?: "comment_${System.currentTimeMillis()}_${index + 1}.jpg"

                    val uploadResult = com.android.purebilibili.data.repository.CommentRepository
                        .uploadCommentImage(
                            fileName = fileName,
                            mimeType = mimeType,
                            bytes = bytes
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
        _audioQualityPreference.value = audioQuality // Optimistic update
        com.android.purebilibili.core.util.Logger.d("PlayerViewModel", "🎵 setAudioQuality called with: $audioQuality")
        //  [调试] 显示 Toast 提示
        val label = when(audioQuality) {
            -1 -> "自动"
            30280 -> "192K"
            30250 -> "杜比全景声"
            30251 -> "Hi-Res无损"
            else -> "未知($audioQuality)"
        }
        toast("切换音质为: $label")

        viewModelScope.launch {
            appContext?.let { 
                com.android.purebilibili.core.store.SettingsManager.setAudioQuality(it, audioQuality)
                reloadVideo() // Reload to apply new audio quality
            }
        }
    }

    //  相互作用
    
    //  稍后再看
    fun toggleWatchLater() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
        ownerMid: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val followDeferred = async {
                if (ownerMid > 0L) com.android.purebilibili.data.repository.ActionRepository.checkFollowStatus(ownerMid)
                else false
            }
            val favoriteDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkFavoriteStatus(aid) }
            val likeDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkLikeStatus(aid) }
            val coinDeferred = async { com.android.purebilibili.data.repository.ActionRepository.checkCoinStatus(aid) }
            val vipDeferred = async {
                if (com.android.purebilibili.core.store.TokenManager.isVipCache) {
                    true
                } else {
                    com.android.purebilibili.data.repository.VideoRepository.getNavInfo()
                        .getOrNull()
                        ?.vip
                        ?.status == 1
                }
            }

            val fetchedFollow = followDeferred.await()
            val fetchedFavorite = favoriteDeferred.await()
            val fetchedLike = likeDeferred.await()
            val fetchedCoinCount = coinDeferred.await()
            val fetchedVip = vipDeferred.await()

            if (fetchedVip) {
                com.android.purebilibili.core.store.TokenManager.isVipCache = true
            }

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    val success = state as? PlayerUiState.Success ?: return@update state
                    if (success.info.bvid != bvid) return@update state

                    val mergedFollowingMids = success.followingMids.toMutableSet()
                    val resolvedFollow = success.isFollowing || fetchedFollow
                    if (ownerMid > 0L) {
                        if (resolvedFollow) mergedFollowingMids.add(ownerMid) else mergedFollowingMids.remove(ownerMid)
                    }

                    success.copy(
                        isVip = success.isVip || fetchedVip,
                        isFollowing = resolvedFollow,
                        isFavorited = success.isFavorited || fetchedFavorite,
                        isLiked = success.isLiked || fetchedLike,
                        coinCount = maxOf(success.coinCount, fetchedCoinCount),
                        followingMids = mergedFollowingMids
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

        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (!current.isLoggedIn) return
        if (!force && current.followingMids.contains(mid)) return

        synchronized(followStatusCheckInFlight) {
            if (!force && followStatusCheckInFlight.contains(mid)) return
            followStatusCheckInFlight.add(mid)
        }

        val currentApi = com.android.purebilibili.core.network.NetworkModule.api
        viewModelScope.launch {
            try {
                // 使用 Relation 接口精准查询
                val response = currentApi.getRelation(mid)
                if (response.code == 0 && response.data != null) {
                    val isFollowing = response.data.attribute == 2 || response.data.attribute == 6

                    _uiState.update { state ->
                        if (state is PlayerUiState.Success) {
                            val newSet = state.followingMids.toMutableSet()
                            if (isFollowing) newSet.add(mid) else newSet.remove(mid)
                            // 刷新当前状态
                            val newIsFollowing = if (state.info.owner.mid == mid) isFollowing else state.isFollowing
                            state.copy(followingMids = newSet, isFollowing = newIsFollowing)
                        } else state
                    }
                    Logger.d("PlayerVM", "Checked relation for mid=$mid: isFollowing=$isFollowing")
                }
            } catch (e: Exception) {
                Logger.e("PlayerVM", "Failed to check relation for mid=$mid", e)
            } finally {
                synchronized(followStatusCheckInFlight) {
                    followStatusCheckInFlight.remove(mid)
                }
            }
        }
    }

    //  异步加载关注列表（用于推荐视频的已关注标签）
    private fun loadFollowingMids() {
        if (isFollowingMidsLoading) return

        val loginMid = com.android.purebilibili.core.store.TokenManager.midCache ?: return
        val now = System.currentTimeMillis()
        val cacheValid = hasFollowingCache &&
            cachedFollowingOwnerMid == loginMid &&
            (now - cachedFollowingLoadedAtMs) in 0..followingMidsCacheTtlMs

        if (cacheValid) {
            _uiState.update { state ->
                if (state is PlayerUiState.Success && state.followingMids != cachedFollowingMids) {
                    state.copy(followingMids = cachedFollowingMids)
                } else {
                    state
                }
            }
            return
        }

        isFollowingMidsLoading = true
        viewModelScope.launch {
            try {
                val allMids = mutableSetOf<Long>()
                var page = 1
                val pageSize = 50
                
                // 只加载前 200 个关注（4页），避免请求过多
                while (page <= 4) {
                    try {
                        val result = com.android.purebilibili.core.network.NetworkModule.api.getFollowings(loginMid, page, pageSize)
                        if (result.code == 0 && result.data != null) {
                            val list = result.data.list ?: break
                            if (list.isEmpty()) break
                            allMids.addAll(list.map { it.mid })
                            if (list.size < pageSize) break
                            page++
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }

                cachedFollowingOwnerMid = loginMid
                cachedFollowingMids = allMids
                cachedFollowingLoadedAtMs = System.currentTimeMillis()
                hasFollowingCache = true
                
                // 更新 UI 状态
                val current = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = current.copy(followingMids = allMids)
                Logger.d("PlayerVM", " Loaded ${allMids.size} following mids")
            } catch (e: Exception) {
                Logger.d("PlayerVM", " Failed to load following mids: ${e.message}")
            } finally {
                isFollowingMidsLoading = false
            }
        }
    }
    
    //  异步加载视频标签
    /**
     *  保存封面到相册
     */
    fun saveCover(context: android.content.Context) {
        val current = _uiState.value as? PlayerUiState.Success
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
        val current = _uiState.value as? PlayerUiState.Success ?: run {
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
            isAudioOnly = true
        )
        
        val started = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
        if (started) {
            toast("已开始下载音频")
        } else {
            toast("该任务已在下载中或已完成")
        }
    }

    private fun loadOwnerStats(
        bvid: String,
        ownerMid: Long
    ) {
        if (ownerMid <= 0L) return
        viewModelScope.launch {
            VideoRepository.getCreatorCardStats(ownerMid).onSuccess { stats ->
                _uiState.update { current ->
                    if (current is PlayerUiState.Success && current.info.bvid == bvid) {
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
                    _uiState.update { current ->
                        if (current is PlayerUiState.Success) {
                            current.copy(videoTags = response.data)
                        } else current
                    }
                    Logger.d("PlayerVM", "🏷️ Loaded ${response.data.size} video tags")
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
                        if (current is PlayerUiState.Success &&
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
                            if (current is PlayerUiState.Success) {
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
                            val onlineText = "${response.data.total}人正在看"
                            _uiState.update { current ->
                                if (current is PlayerUiState.Success) {
                                    current.copy(onlineCount = onlineText)
                                } else current
                            }
                            Logger.d("PlayerVM", "👀 Online count: ${response.data.total}")
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

                    val currentState = _uiState.value as? PlayerUiState.Success
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

                    // 2. 处理 BGM 信息
                    if (data.bgmInfo != null) {
                        _uiState.update { current ->
                            if (current is PlayerUiState.Success) {
                                current.copy(bgmInfo = data.bgmInfo)
                            } else current
                        }
                        Logger.d("PlayerVM", "🎵 Loaded BGM: ${data.bgmInfo?.musicTitle}")
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
                    val current = _uiState.value as? PlayerUiState.Success
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
                clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
            }
        }
    }

    private fun clearSubtitleTracksForCurrentVideo(bvid: String, cid: Long) {
        if (currentBvid == bvid && currentCid == cid) {
            subtitleLoadToken += 1
        }
        _uiState.update { current ->
            if (current is PlayerUiState.Success &&
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
        return orderSubtitleTracksByPreference(
            subtitles.mapNotNull { item ->
                val normalizedUrl = normalizeBilibiliSubtitleUrl(item.subtitleUrl)
                if (!isTrustedBilibiliSubtitleUrl(normalizedUrl)) {
                    Logger.d(
                        "PlayerVM",
                        "SUB_DBG ignore untrusted subtitle track: lan=${item.lan}, url=${item.subtitleUrl.take(80)}"
                    )
                    return@mapNotNull null
                }
                SubtitleTrackMeta(
                    id = item.id,
                    idStr = item.idStr,
                    lan = item.lan,
                    lanDoc = item.lanDoc,
                    subtitleUrl = normalizedUrl,
                    aiStatus = item.aiStatus,
                    aiType = item.aiType,
                    type = item.type
                )
            }.distinctBy { meta -> "${meta.lan}|${meta.idStr}|${meta.id}|${meta.subtitleUrl}" }
        )
    }

    private fun loadSubtitleTracksFromPlayerInfo(
        bvid: String,
        cid: Long,
        subtitles: List<SubtitleItem>,
        preferredPrimaryLanguage: String? = null,
        requestToken: Long = currentLoadRequestToken
    ) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
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

        val trackMetas = mapSubtitleTracksForPlayback(subtitles)
        if (trackMetas.isEmpty()) {
            clearSubtitleTracksForCurrentVideo(bvid = bvid, cid = cid)
            return
        }

        val selection = resolveDefaultSubtitleLanguages(
            tracks = trackMetas,
            preferredPrimaryLanguage = preferredPrimaryLanguage
        )
        var primaryTrack = trackMetas.firstOrNull { it.lan == selection.primaryLanguage } ?: trackMetas.first()
        var secondaryTrack = selection.secondaryLanguage
            ?.let { targetLan ->
                trackMetas.firstOrNull { it.lan == targetLan && it.lan != primaryTrack.lan }
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
            if (state is PlayerUiState.Success &&
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
                            track.lan == secondaryTrack?.lan && track.lan != primaryTrack.lan
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
                if (state is PlayerUiState.Success &&
                    state.info.bvid == bvid &&
                    state.info.cid == cid
                ) {
                    state.copy(
                        subtitlePrimaryTrackKey = primaryTrackKey,
                        subtitleSecondaryTrackKey = secondaryTrackKey,
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
                val primaryMismatchReason = if (state is PlayerUiState.Success) {
                    resolveSubtitleTrackBindingMismatchReason(
                        expectedTrackKey = primaryTrackKey,
                        currentTrackKey = state.subtitlePrimaryTrackKey,
                        expectedLanguage = primaryTrack.lan,
                        currentLanguage = state.subtitlePrimaryLanguage
                    )
                } else {
                    "ui-not-success"
                }
                val secondaryMismatchReason = if (state is PlayerUiState.Success) {
                    resolveSubtitleTrackBindingMismatchReason(
                        expectedTrackKey = secondaryTrackKey,
                        currentTrackKey = state.subtitleSecondaryTrackKey,
                        expectedLanguage = secondaryTrack?.lan,
                        currentLanguage = state.subtitleSecondaryLanguage
                    )
                } else {
                    "ui-not-success"
                }
                if (state is PlayerUiState.Success &&
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
                    if (state is PlayerUiState.Success &&
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
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
        current: PlayerUiState.Success,
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
                val current = _uiState.value as? PlayerUiState.Success ?: return@launch
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
            val loadingPrompt = initialAiSummaryPromptState()
            _uiState.update { current ->
                if (
                    current is PlayerUiState.Success &&
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

                    result.onSuccess { response ->
                        val diagnosis =
                            com.android.purebilibili.data.repository.diagnoseAiSummaryResponse(response)
                        when {
                            diagnosis.status ==
                                com.android.purebilibili.data.repository.AiSummaryFetchStatus.AVAILABLE &&
                                response.data != null -> {
                                _uiState.update { current ->
                                    if (current is PlayerUiState.Success && current.info.bvid == bvid) {
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
                                    if (current is PlayerUiState.Success && current.info.bvid == bvid) {
                                        current.copy(aiSummaryPrompt = prompt)
                                    } else current
                                }
                                nextDelayMs = resolveAiSummaryRetryDelayMs(
                                    queuedRetryCount = queuedRetryCount,
                                    isInBackground = BackgroundManager.isInBackground
                                )
                                shouldPollAgain = true
                                Logger.i(
                                    "PlayerVM",
                                    "🤖 AI Summary queued, retry later: bvid=$bvid cid=$cid stid=${diagnosis.stid ?: ""} retryInMs=$nextDelayMs retryCount=$queuedRetryCount"
                                )
                            }

                            diagnosis.status ==
                                com.android.purebilibili.data.repository.AiSummaryFetchStatus.QUEUED -> {
                                val prompt = queuedAiSummaryPendingPromptState()
                                _uiState.update { current ->
                                    if (current is PlayerUiState.Success && current.info.bvid == bvid) {
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
                                    if (current is PlayerUiState.Success && current.info.bvid == bvid) {
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
                        val prompt = resolveAiSummaryPromptState(diagnosis)
                        _uiState.update { current ->
                            if (current is PlayerUiState.Success && current.info.bvid == bvid) {
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

                    queuedRetryCount += 1
                    delay(nextDelayMs)
                    val currentSuccess = _uiState.value as? PlayerUiState.Success
                    if (
                        currentSuccess?.info?.bvid != bvid ||
                        currentSuccess.info.cid != cid ||
                        currentSuccess.aiSummary?.modelResult != null
                    ) {
                        return@launch
                    }
                } catch (e: Exception) {
                    Logger.d("PlayerVM", "🤖 Failed to load AI Summary: ${e.message}")
                    return@launch
                }
            }
        }
    }
    
    fun openCoinDialog() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.coinCount >= 2) { toast("\u5df2\u6295\u6ee12\u4e2a\u786c\u5e01"); return }
        _coinDialogVisible.value = true
        fetchUserCoins()
    }
    
    fun closeCoinDialog() { _coinDialogVisible.value = false }
    
    fun doCoin(count: Int, alsoLike: Boolean) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _coinDialogVisible.value = false
        viewModelScope.launch {
            interactionUseCase.doCoin(current.info.aid, count, alsoLike, currentBvid)
                .onSuccess { 
                    var newState = current.copy(coinCount = minOf(current.coinCount + count, 2))
                    if (alsoLike && !current.isLiked) newState = newState.copy(isLiked = true)
                    _uiState.value = newState
                    //  彩蛋：使用趣味消息（如果设置开启）
                    val message = if (appContext?.let { ctx -> com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(ctx) } == true) {
                        com.android.purebilibili.core.util.EasterEggs.getCoinMessage()
                    } else {
                        "投币成功"
                    }
                    toast(message)
                }
                .onFailure { toast(it.message ?: "\u6295\u5e01\u5931\u8d25") }
        }
    }
    
    fun doTripleAction() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            toast("正在三连")
            interactionUseCase.doTripleAction(current.info.aid)
                .onSuccess { result ->
                    val visualState = resolveTripleActionVisualState(
                        currentLiked = current.isLiked,
                        currentCoinCount = current.coinCount,
                        currentFavorited = current.isFavorited,
                        likeSuccess = result.likeSuccess,
                        coinSuccess = result.coinSuccess,
                        coinFailureMessage = result.coinMessage,
                        favoriteSuccess = result.favoriteSuccess
                    )
                    _uiState.value = current.copy(
                        isLiked = visualState.isLiked,
                        coinCount = visualState.coinCount,
                        isFavorited = visualState.isFavorited
                    )
                    if (result.allSuccess) _tripleCelebrationVisible.value = true
                    toast(
                        resolveTripleActionFeedbackMessage(
                            likeSuccess = result.likeSuccess,
                            coinSuccess = result.coinSuccess,
                            favoriteSuccess = result.favoriteSuccess,
                            coinFailureMessage = result.coinMessage
                        )
                    )

                    // [New] Easter Egg: Auto Jump after Triple Action
                    viewModelScope.launch {
                        val context = appContext ?: return@launch
                        val isJumpEnabled = com.android.purebilibili.core.store.SettingsManager.getTripleJumpEnabled(context).first()
                        if (result.allSuccess && isJumpEnabled) {
                             // Wait a bit for the celebration to show
                            delay(2000)
                            loadVideo("BV1JsK5eyEuB", autoPlay = true)
                        }
                    }
                }
                .onFailure { toast(it.message ?: "\u4e09\u8fde\u5931\u8d25") }
        }
    }
    
    fun dismissLikeBurst() { _likeBurstVisible.value = false }
    fun dismissTripleCelebration() { _tripleCelebrationVisible.value = false }
    
    // ========== Download ==========
    
    //  下载对话框状态
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog = _showDownloadDialog.asStateFlow()
    
    fun openDownloadDialog() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _showDownloadDialog.value = true
    }
    
    fun closeDownloadDialog() {
        _showDownloadDialog.value = false
    }

    private fun resolveDownloadQualityDescription(
        current: PlayerUiState.Success,
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
        current: PlayerUiState.Success,
        targetBvid: String,
        targetCid: Long,
        targetTitle: String,
        targetLabel: String,
        targetCover: String,
        qualityId: Int
    ): com.android.purebilibili.feature.download.DownloadTask? {
        val qualityDesc = resolveDownloadQualityDescription(current, qualityId)
        val isCurrentTarget = targetBvid == currentBvid && targetCid == currentCid
        val resolvedTitle = resolveBatchDownloadTaskTitle(
            rootTitle = current.info.title,
            candidateTitle = targetTitle,
            candidateLabel = targetLabel
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
            bvid = targetBvid,
            cid = targetCid,
            title = resolvedTitle,
            cover = targetCover.ifBlank { current.info.pic },
            ownerName = current.info.owner.name,
            ownerFace = current.info.owner.face,
            duration = 0,
            quality = qualityId,
            qualityDesc = qualityDesc,
            videoUrl = resolvedUrls.first,
            audioUrl = resolvedUrls.second
        )
    }
    
    fun downloadWithQuality(qualityId: Int) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _showDownloadDialog.value = false
        
        viewModelScope.launch {
            val task = buildDownloadTaskForTarget(
                current = current,
                targetBvid = currentBvid,
                targetCid = currentCid,
                targetTitle = current.info.title,
                targetLabel = current.info.title,
                targetCover = current.info.pic,
                qualityId = qualityId
            )
            
            if (task == null) {
                toast("无法获取下载地址")
                return@launch
            }

            val added = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
            if (added) {
                toast("开始下载: ${task.title} [${task.qualityDesc}]")
                // 开始监听下载进度
                com.android.purebilibili.feature.download.DownloadManager.tasks.collect { tasks ->
                    val downloadTask = tasks[task.id]
                    _downloadProgress.value = downloadTask?.progress ?: -1f
                }
            } else {
                toast("下载任务已存在")
            }
        }
    }

    internal fun downloadBatchWithQuality(
        qualityId: Int,
        candidates: List<com.android.purebilibili.feature.download.BatchDownloadCandidate>
    ) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _showDownloadDialog.value = false

        viewModelScope.launch {
            var addedCount = 0
            var skippedExistingCount = 0
            var failedCount = 0

            candidates.filter { it.selected }.forEach { candidate ->
                val existingTask = com.android.purebilibili.feature.download.DownloadManager.getTask(
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
                    qualityId = qualityId
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
                val current = _uiState.value as? PlayerUiState.Success ?: return@launch
                val pendingPositionMs = current.pendingPlaybackTransitionPositionMs ?: return@launch
                if (pendingPositionMs != targetPositionMs) return@launch
                val playerPositionMs = playbackUseCase.getCurrentPosition().coerceAtLeast(0L)
                if (!shouldHoldPlaybackTransitionPosition(playerPositionMs, targetPositionMs)) {
                    _uiState.update { state ->
                        val success = state as? PlayerUiState.Success ?: return@update state
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
    
    fun changeQuality(qualityId: Int, currentPos: Long) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.isQualitySwitching) {
            toast("正在切换中...", PlayerToastPresentation.CenteredHighlight)
            return
        }
        if (current.currentQuality == qualityId) {
            toast("已是当前清晰度", PlayerToastPresentation.CenteredHighlight)
            return
        }

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
            serverAdvertisedQualities = current.qualityIds
        )
        
        when (permissionResult) {
            is QualityPermissionResult.RequiresVip -> {
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
                    changeQuality(fallbackQuality, currentPos)
                }
                return
            }
            is QualityPermissionResult.RequiresLogin -> {
                showQualitySwitchFailureDialog(
                    requestedQualityId = qualityId,
                    permissionResult = permissionResult
                )
                return
            }
            is QualityPermissionResult.UnsupportedByDevice -> {
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
                    changeQuality(fallbackQuality, currentPos)
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
                // [新增] 获取当前音频偏好
                val audioPref = appContext?.let { 
                    com.android.purebilibili.core.store.SettingsManager.getAudioQualitySync(it) 
                } ?: -1
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
                    currentPos = currentPos,
                    durationMs = current.videoDurationMs,
                    playbackQualityMode = playbackQualityMode,
                    audioQualityPreference = audioPref,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    isHevcSupported = isHevcSupported,
                    isAv1Supported = isAv1Supported
                ) ?: playbackUseCase.changeQualityFromApi(
                    bvid = currentBvid,
                    cid = currentCid,
                    qualityId = qualityId,
                    currentPos = currentPos,
                    playbackQualityMode = playbackQualityMode,
                    audioQualityPreference = audioPref,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    isHevcSupported = isHevcSupported,
                    isAv1Supported = isAv1Supported
                )
                
                if (result != null) {
                    _qualitySwitchFailureDialog.value = null
                    _uiState.value = current.copy(
                        playUrl = result.videoUrl, audioUrl = result.audioUrl,
                        currentQuality = result.actualQuality, isQualitySwitching = false, requestedQuality = null,
                        playbackQualityMode = playbackQualityMode,
                        adaptiveDashSource = result.adaptiveDashSource,
                        pendingPlaybackTransitionPositionMs = transitionPositionMs,
                        qualityIds = result.qualityIds.ifEmpty { current.qualityIds },
                        qualityLabels = result.qualityLabels.ifEmpty { current.qualityLabels },
                        //  [修复] 更新缓存的DASH流，否则后续画质切换可能失败
                        cachedDashVideos = result.cachedDashVideos.ifEmpty { current.cachedDashVideos },
                        cachedDashAudios = result.cachedDashAudios.ifEmpty { current.cachedDashAudios }
                    )
                    monitorPlaybackTransitionPosition(transitionPositionMs)
                    val label = current.qualityLabels.getOrNull(
                        current.qualityIds.indexOf(result.actualQuality)
                    ) ?: qualityManager.getQualityLabel(result.actualQuality)
                    toast(
                        if (result.wasFallback) {
                            "目标清晰度不可用，已切换至 $label"
                        } else {
                            "✓ 已切换至 $label"
                        },
                        PlayerToastPresentation.CenteredHighlight
                    )
                    //  记录画质切换事件
                    AnalyticsHelper.logQualityChange(currentBvid, current.currentQuality, result.actualQuality)
                } else {
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
            } catch (e: Exception) {
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
    
    fun switchPage(pageIndex: Int, ignoreSavedProgress: Boolean = false) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        val page = current.info.pages.getOrNull(pageIndex) ?: return
        if (page.cid == currentCid) { toast("\u5df2\u662f\u5f53\u524d\u5206P"); return }
        playbackCoordinator.dismissResumeSuggestion()
        subtitleLoadToken += 1
        val subtitleClearedState = clearTransientPlaybackPreviewData(clearSubtitleFields(current))
        val previousCid = currentCid
        if (currentBvid.isNotEmpty() && previousCid > 0L) {
            flushPlaybackHeartbeatSnapshot(reason = "switch_page")
            playbackUseCase.savePosition(currentBvid, previousCid)
        }
        currentCid = page.cid
        _uiState.value = subtitleClearedState.copy(
            isQualitySwitching = true,
            pendingPlaybackTransitionPositionMs = 0L
        )
        
        viewModelScope.launch {
            try {
                val playUrlData = VideoRepository.getPlayUrlData(currentBvid, page.cid, current.currentQuality)
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
                    val audioQualityPreference = appContext?.let { 
                        com.android.purebilibili.core.store.SettingsManager.getAudioQualitySync(it) 
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
                    val restoredPosition = if (ignoreSavedProgress) {
                        0L
                    } else {
                        playbackUseCase.getCachedPosition(currentBvid, page.cid)
                    }
                    
                    if (selection != null) {
                        playResolvedPlayback(
                            videoUrl = selection.videoUrl,
                            audioUrl = selection.audioUrl,
                            adaptiveDashSource = selection.adaptiveDashSource,
                            startPositionMs = restoredPosition
                        )
                        
                        _uiState.value = subtitleClearedState.copy(
                            info = current.info.copy(cid = page.cid), playUrl = selection.videoUrl, audioUrl = selection.audioUrl,
                            startPosition = restoredPosition, isQualitySwitching = false,
                            pendingPlaybackTransitionPositionMs = restoredPosition.coerceAtLeast(0L),
                            adaptiveDashSource = selection.adaptiveDashSource,
                            qualityIds = selection.qualityIds,
                            qualityLabels = selection.qualityLabels,
                            cachedDashVideos = selection.cachedDashVideos,
                            cachedDashAudios = selection.cachedDashAudios
                        )
                        monitorPlaybackTransitionPosition(restoredPosition.coerceAtLeast(0L))
                        startHeartbeat()
                        interactiveCurrentEdgeId = 0L
                        loadPlayerInfo(currentBvid, page.cid)
                        loadVideoshot(currentBvid, page.cid)
                        toast("\u5df2\u5207\u6362\u81f3 P${pageIndex + 1}")
                        return@launch
                    }
                }
                currentCid = previousCid
                _uiState.value = current.copy(
                    isQualitySwitching = false,
                    pendingPlaybackTransitionPositionMs = null
                )
                toast("\u5206P\u5207\u6362\u5931\u8d25")
            } catch (e: Exception) {
                currentCid = previousCid
                _uiState.value = current.copy(
                    isQualitySwitching = false,
                    pendingPlaybackTransitionPositionMs = null
                )
            }
        }
    }

    fun dismissResumePlaybackSuggestion() {
        playbackCoordinator.dismissResumeSuggestion()
    }

    fun continueResumePlaybackSuggestion() {
        val suggestion = playbackCoordinator.consumeResumeSuggestion() ?: return

        val current = _uiState.value as? PlayerUiState.Success
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
        val current = _uiState.value as? PlayerUiState.Success ?: return false
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
            val audioQualityPreference = appContext?.let {
                com.android.purebilibili.core.store.SettingsManager.getAudioQualitySync(it)
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
            ) ?: return false

            playResolvedPlayback(
                videoUrl = selection.videoUrl,
                audioUrl = selection.audioUrl,
                adaptiveDashSource = selection.adaptiveDashSource,
                startPositionMs = 0L
            )

            currentCid = targetCid
            subtitleLoadToken += 1
            _uiState.value = clearTransientPlaybackPreviewData(clearSubtitleFields(current)).copy(
                info = current.info.copy(cid = targetCid),
                playUrl = selection.videoUrl,
                audioUrl = selection.audioUrl,
                startPosition = 0L,
                adaptiveDashSource = selection.adaptiveDashSource,
                videoDurationMs = playUrlData.timelength.coerceAtLeast(0L),
                qualityIds = selection.qualityIds,
                qualityLabels = selection.qualityLabels,
                cachedDashVideos = selection.cachedDashVideos,
                cachedDashAudios = selection.cachedDashAudios
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
                if (current is PlayerUiState.Success) {
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
                    val currentSuccess = _uiState.value as? PlayerUiState.Success ?: return@launch
                    if (currentSuccess.info.bvid != loadedBvid || currentSuccess.info.cid != loadedCid) {
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
                                ownerMid = loadedOwnerMid
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
                                _sponsorProgressMarkers.value = emptyList()
                                PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
                                    try {
                                        plugin.onVideoLoad(loadedBvid, loadedCid)
                                        if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                                            _sponsorProgressMarkers.value = plugin.getProgressMarkers()
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("PlayerVM", "Plugin ${plugin.name} onVideoLoad failed", e)
                                    }
                                }
                            }
                            PlaybackPostLoadTask.START_PLUGIN_CHECK -> startPluginCheck()
                        }
                    }
                }
            }
    }

    /**
     * 定期检查插件（约500ms一次）
     */
    private fun startPluginCheck() {
        pluginCheckJob?.cancel()
        lastPluginDispatchPositionMs = null
        pluginCheckJob = viewModelScope.launch {
            while (true) {
                val plugins = PluginManager.getEnabledPlayerPlugins()
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
                if (!shouldDispatchPluginPositionUpdate(
                        lastDispatchedPositionMs = lastPluginDispatchPositionMs,
                        currentPositionMs = currentPos
                    )
                ) {
                    continue
                }
                lastPluginDispatchPositionMs = currentPos
                
                for (plugin in plugins) {
                    try {
                        when (val action = plugin.onPositionUpdate(currentPos)) {
                            is SkipAction.SkipTo -> {
                                clearSponsorSkipUi()
                                playbackUseCase.seekTo(action.positionMs)
                                toast(action.reason)
                                Logger.d("PlayerVM", " Plugin ${plugin.name} skipped to ${action.positionMs}ms")
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
        PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin) {
                plugin.markAsSkipped(_sponsorSkipUiState.value.segmentId ?: return@forEach)
            }
        }
        clearSponsorSkipUi()
    }

    fun skipCurrentSponsorSegment() {
        val targetPosition = _sponsorSkipUiState.value.skipToMs.takeIf { it > 0L } ?: return
        val segmentId = _sponsorSkipUiState.value.segmentId
        PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
            if (plugin is com.android.purebilibili.feature.plugin.SponsorBlockPlugin && segmentId != null) {
                plugin.markAsSkipped(segmentId)
            }
        }
        playbackUseCase.seekTo(targetPosition)
        clearSponsorSkipUi()
    }

    fun notifyPluginsOfExplicitSeek(positionMs: Long) {
        PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
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

    private fun playResolvedPlayback(
        videoUrl: String,
        audioUrl: String?,
        adaptiveDashSource: AdaptiveDashPlaybackSource?,
        startPositionMs: Long,
        playWhenReady: Boolean = true
    ) {
        if (adaptiveDashSource != null || audioUrl != null) {
            playbackUseCase.playDashVideo(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                adaptiveDashSource = adaptiveDashSource,
                seekTo = startPositionMs,
                playWhenReady = playWhenReady
            )
        } else {
            playbackUseCase.playVideo(videoUrl, startPositionMs, playWhenReady = playWhenReady)
        }
    }

    private fun showQualitySwitchFailureDialog(
        requestedQualityId: Int,
        permissionResult: QualityPermissionResult? = null,
        loadError: VideoLoadError? = null,
        hasCachedDashTracks: Boolean = true,
        cacheContainsRequestedQuality: Boolean = true,
        qualityRefetchCooldownRemainingMs: Long? = null
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
            qualityRefetchCooldownRemainingMs = qualityRefetchCooldownRemainingMs
        )
        Logger.w(
            "PlayerVM",
            "QUALITY_SWITCH_FAILURE requested=$requestedQualityId label=$requestedQualityLabel " +
                "permission=$permissionResult error=$loadError hasCache=$hasCachedDashTracks " +
                "containsRequested=$cacheContainsRequestedQuality cooldownMs=${qualityRefetchCooldownRemainingMs ?: 0L} " +
                "message=${dialogState.message}"
        )
        _qualitySwitchFailureDialog.value = dialogState
    }
    
    fun restoreFromCache(cachedState: PlayerUiState.Success, startPosition: Long = -1L) {
        currentBvid = cachedState.info.bvid
        currentCid = cachedState.info.cid
        _uiState.value = if (startPosition >= 0) cachedState.copy(startPosition = startPosition) else cachedState
    }
    
    // ========== Private ==========
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        beginHeartbeatSession()
        heartbeatJob = viewModelScope.launch {
            if (shouldSendPlaybackHeartbeat(
                    isPlaying = true,
                    isInBackground = BackgroundManager.isInBackground,
                    currentBvid = currentBvid,
                    currentCid = currentCid
                )
            ) {
                try {
                    val reported = VideoRepository.reportPlayHeartbeat(
                        bvid = currentBvid,
                        cid = currentCid,
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
        val current = _uiState.value as? PlayerUiState.Success ?: return
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
    
    override fun onCleared() {
        super.onCleared()
        recordCreatorWatchProgressSnapshot()
        heartbeatJob?.cancel()
        clearHeartbeatSession()
        pluginCheckJob?.cancel()
        onlineCountJob?.cancel()  // 👀 取消在线人数轮询
        playbackTransitionMonitorJob?.cancel()
        aiSummaryJob?.cancel()
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
        PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
            try {
                plugin.onVideoEnd()
            } catch (e: Exception) {
                Logger.e("PlayerVM", "Plugin ${plugin.name} onVideoEnd failed", e)
            }
        }
        
        exoPlayer = null
    }
}
