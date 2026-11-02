@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.AppModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.player.PlayerVolumeController
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.feature.audio.lyrics.BiliSubtitleLyricsPolicy
import com.android.purebilibili.feature.audio.lyrics.LyricSource
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.audio.player.MusicPlayerUiState
import com.android.purebilibili.feature.audio.player.MusicLyricCandidateUi
import com.android.purebilibili.feature.audio.player.MusicQueueItemUi
import com.android.purebilibili.feature.audio.screen.MusicPlayerContent
import com.android.purebilibili.feature.audio.viewmodel.MusicViewModel
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.core.store.PlayHistoryEntry
import com.android.purebilibili.core.store.PlayHistoryStore
import com.android.purebilibili.core.store.PlayLastSession
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.subtitle.buildSubtitleTrackOptions
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.playback.audio.resolveAudioQualityControlPresentation
import com.android.purebilibili.feature.video.share.VideoShareSheet
import com.android.purebilibili.feature.video.share.buildVideoSharePayload
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PlayerEpisodeMenu
import com.android.purebilibili.feature.video.ui.components.PlaybackSpeed
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuDialog
import com.android.purebilibili.feature.video.ui.components.VideoCommentSheetHost
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive

private data class AudioPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f
)

internal fun resolveAudioModeTrackTitle(
    videoTitle: String,
    currentCid: Long,
    pages: List<Page>
): String {
    return pages.firstOrNull { it.cid == currentCid }
        ?.part
        ?.takeIf { it.isNotBlank() }
        ?: videoTitle
}

/** 对外分享用视频标题，避免分 P 名/曲目 override 顶掉真实标题。 */
internal fun resolveAudioModeShareTitle(
    videoTitle: String,
    displayTitle: String,
): String {
    return videoTitle.trim().ifBlank { displayTitle.trim() }
}

internal data class AudioModeLyricMetadata(
    val title: String,
    val artist: String
)

internal fun resolveAudioModeLyricMetadata(
    trackTitle: String,
    fallbackArtist: String
): AudioModeLyricMetadata {
    val match = Regex(
        """^\s*(?:P?\d{1,4}\s*[.、:：_-]\s*)?(.+?)\s+[-–—]\s+(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(trackTitle)
    return AudioModeLyricMetadata(
        title = match?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { trackTitle },
        artist = match?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank { fallbackArtist }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioModeMusicPlayer(
    viewModel: VideoPlaybackViewModel,
    successState: VideoPlaybackUiState.Success?,
    player: Player?,
    onBack: () -> Unit,
    onVideoModeClick: (String, Long) -> Unit,
    isInPipMode: Boolean,
    showPipButton: Boolean,
    onEnterPip: () -> Unit,
    sleepTimerMinutes: Int?,
    titleOverride: String?,
    liquidGlassEffectsEnabled: Boolean,
    onToggleOrientation: (() -> Unit)? = null,
    orientationActionLabel: String = "横屏",
    engagementViewModel: VideoEngagementViewModel = viewModel()
) {
    if (successState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveLoadingIndicator(color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val info = successState.info
    val displayTitle = resolveAudioModeTrackTitle(
        videoTitle = titleOverride?.takeIf { it.isNotBlank() } ?: info.title,
        currentCid = info.cid,
        pages = info.pages
    )
    val playlist by PlaylistManager.playlist.collectAsStateWithLifecycle()
    val playlistIndex by PlaylistManager.currentIndex.collectAsStateWithLifecycle()
    val playMode by PlaylistManager.playMode.collectAsStateWithLifecycle()
    val shuffleEnabled by PlaylistManager.shuffleEnabled.collectAsStateWithLifecycle()
    val playback = rememberAudioPlaybackSnapshot(player)
    val lyricsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(
        key = "audio_mode_lyrics"
    )
    val suppliedLyricsState by lyricsViewModel.uiState.collectAsStateWithLifecycle()
    val lyricsState = suppliedLyricsState.takeIf { it.lyricsOwnerKey == "video:${info.bvid}:${info.cid}" }
        ?: com.android.purebilibili.feature.audio.viewmodel.MusicUiState(isLyricsSearching = true)
    var showCollectionSheet by remember { mutableStateOf(false) }
    var showPageSelector by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    val engagementState by engagementViewModel.uiState.collectAsStateWithLifecycle()

    // ---- 播放历史记录与冷启动续播 ----
    val historyScope = rememberCoroutineScope()
    LaunchedEffect(info.bvid, info.cid) {
        PlayHistoryStore.record(
            context = context,
            entry = PlayHistoryEntry(
                bvid = info.bvid,
                cid = info.cid,
                title = displayTitle,
                cover = info.pic,
                owner = info.owner.name,
                durationSec = 0L,
                lastPlayedAtMs = System.currentTimeMillis()
            )
        )
    }
    // 播放中每 5 秒保存续播会话，供「启动自动播放」使用
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(player, playback.isPlaying, info.bvid, info.cid, lifecycleOwner) {
        if (player == null || !playback.isPlaying) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(5_000L)
                if (!player.isPlaying) continue
                PlayHistoryStore.saveLastSession(
                    context,
                    PlayLastSession(
                        bvid = info.bvid,
                        cid = info.cid,
                        title = displayTitle,
                        cover = info.pic,
                        owner = info.owner.name,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        savedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    LaunchedEffect(player) {
        if (AudioStartupAutoPlayGuard.handled) return@LaunchedEffect
        AudioStartupAutoPlayGuard.handled = true
        val enabled = SettingsManager.getStartupAutoPlayEnabledSync(context)
        if (!enabled) return@LaunchedEffect
        if (PlaylistManager.playlist.value.isNotEmpty()) return@LaunchedEffect
        val session = PlayHistoryStore.lastSession(context).firstOrNull() ?: return@LaunchedEffect
        if (session.bvid.isBlank()) return@LaunchedEffect
        PlaylistManager.addToPlaylist(
            PlaylistItem(
                bvid = session.bvid,
                cid = session.cid,
                title = session.title,
                cover = session.cover,
                owner = session.owner
            )
        )
        viewModel.loadVideo(bvid = session.bvid, cid = session.cid, autoPlay = true)
    }
    val coinSuccessHaptic = rememberUpdatedState(
        com.android.purebilibili.feature.video.ui.components.rememberVideoActionSuccessHaptic()
    )
    LaunchedEffect(engagementViewModel) {
        engagementViewModel.events.collect { event ->
            if (event is com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent.Message) {
                viewModel.toast(event.text)
            } else if (event is com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent.CoinSucceeded &&
                engagementViewModel.uiState.value.subject == event.subject &&
                com.android.purebilibili.feature.video.viewmodel.matchesVideoEngagementAccount(event.accountSession)
            ) {
                coinSuccessHaptic.value()
            }
        }
    }
    val commentViewModel: VideoCommentViewModel = viewModel()
    val currentSpeed = player?.playbackParameters?.speed ?: 1f

    val metadataDurationMs = info.pages
        .firstOrNull { it.cid == info.cid }
        ?.duration
        ?.times(1_000L)
        ?: 0L
    val lyricsDurationMs = playback.durationMs.takeIf { it > 0L } ?: metadataDurationMs
    val lyricMetadata = remember(displayTitle, info.owner.name) {
        resolveAudioModeLyricMetadata(displayTitle, info.owner.name)
    }

    LaunchedEffect(Unit) {
        lyricsViewModel.initPlayer(context)
    }
    LaunchedEffect(info.bvid, info.cid, displayTitle, info.owner.name, lyricsDurationMs) {
        lyricsViewModel.loadLyricsForVideo(
            title = lyricMetadata.title,
            artist = lyricMetadata.artist,
            bvid = info.bvid,
            cid = info.cid,
            durationMs = lyricsDurationMs,
            originalTitle = displayTitle,
            artistIsUploader = lyricMetadata.artist == info.owner.name,
        )
    }

    val queue = remember(playlist, info, displayTitle, successState.related) {
        if (playlist.isEmpty()) {
            if (info.pages.size > 1) {
                info.pages.mapIndexed { index, page ->
                    MusicQueueItemUi(
                        stableId = "video:${info.bvid}:${page.cid}",
                        title = if (page.part.isNotBlank()) page.part else "${displayTitle} P${page.page}",
                        artist = info.owner.name,
                        coverUrl = FormatUtils.fixImageUrl(info.pic)
                    )
                }
            } else if (successState.related.isNotEmpty()) {
                val currentItem = MusicQueueItemUi(
                    stableId = "video:${info.bvid}:${info.cid}",
                    title = displayTitle,
                    artist = info.owner.name,
                    coverUrl = FormatUtils.fixImageUrl(info.pic)
                )
                listOf(currentItem) + successState.related.take(10).map { rel ->
                    MusicQueueItemUi(
                        stableId = "video:${rel.bvid}:${rel.cid}",
                        title = rel.title,
                        artist = rel.owner.name,
                        coverUrl = FormatUtils.fixImageUrl(rel.pic)
                    )
                }
            } else {
                listOf(
                    MusicQueueItemUi(
                        stableId = "video:${info.bvid}:${info.cid}",
                        title = displayTitle,
                        artist = info.owner.name,
                        coverUrl = FormatUtils.fixImageUrl(info.pic)
                    )
                )
            }
        } else {
            val occurrences = mutableMapOf<Pair<String, Long>, Int>()
            playlist.map { item ->
                val identity = item.bvid to item.cid
                val occurrence = occurrences.getOrDefault(identity, 0)
                occurrences[identity] = occurrence + 1
                MusicQueueItemUi(
                    stableId = "video:${item.bvid}:${item.cid}:$occurrence",
                    title = item.title,
                    artist = item.owner,
                    coverUrl = FormatUtils.fixImageUrl(item.cover)
                )
            }
        }
    }
    val sleepAfterCurrent by viewModel.sleepAfterCurrent.collectAsStateWithLifecycle()
    val currentIndex = if (playlist.isNotEmpty()) {
        playlistIndex.takeIf { it in queue.indices } ?: 0
    } else if (info.pages.size > 1) {
        info.pages.indexOfFirst { it.cid == info.cid }.takeIf { it >= 0 } ?: 0
    } else {
        0
    }
    val coverUrl = queue.getOrNull(currentIndex)?.coverUrl ?: FormatUtils.fixImageUrl(info.pic)
    val audioNowPlayingBarEnabled by SettingsManager
        .getAudioNowPlayingBarEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(player, info.bvid, info.cid, displayTitle, coverUrl, audioNowPlayingBarEnabled) {
        if (!audioNowPlayingBarEnabled) return@LaunchedEffect
        val exoPlayer = player as? ExoPlayer ?: return@LaunchedEffect
        MiniPlayerManager.getInstance(context).setVideoInfo(
            bvid = info.bvid,
            title = displayTitle,
            cover = coverUrl,
            owner = info.owner.name,
            cid = info.cid,
            aid = info.aid,
            externalPlayer = exoPlayer
        )
    }
    val audioQualityPresentation = remember(
        successState.availableAudioQualities,
        successState.selectedAudioQuality
    ) {
        resolveAudioQualityControlPresentation(
            options = successState.availableAudioQualities,
            selectedAudioQuality = successState.selectedAudioQuality
        )
    }
    val favoriteQuickSaveDefaultFolder by com.android.purebilibili.core.store.FavoriteInteractionSettingsStore
        .getQuickSaveDefaultFolder(LocalContext.current)
        .collectAsStateWithLifecycle(initialValue = false)

    var audioSecondaryTrackKey by remember(info.bvid, info.cid) { mutableStateOf<String?>(null) }
    var audioSecondaryRequestId by remember(info.bvid, info.cid) { androidx.compose.runtime.mutableIntStateOf(0) }
    val secondaryTrack = successState.subtitleTracks.firstOrNull {
        it.trackKey == (audioSecondaryTrackKey ?: successState.subtitleSecondaryTrackKey) &&
            it.trackKey != successState.subtitlePrimaryTrackKey
    }
    val selectedSecondaryCues = androidx.compose.runtime.key(info.bvid, info.cid, secondaryTrack?.trackKey, audioSecondaryTrackKey, audioSecondaryRequestId) {
        androidx.compose.runtime.produceState<Result<List<com.android.purebilibili.feature.video.subtitle.SubtitleCue>>?>(initialValue = null) {
            if (audioSecondaryTrackKey != null && secondaryTrack != null) {
                value = com.android.purebilibili.data.repository.VideoRepository.getSubtitleCues(
                    subtitleUrl = secondaryTrack.subtitleUrl,
                    bvid = info.bvid,
                    cid = info.cid,
                    subtitleId = secondaryTrack.id,
                    subtitleIdStr = secondaryTrack.idStr,
                    subtitleLan = secondaryTrack.lan,
                )
            }
        }
    }
    val audioSecondaryCues = when {
        secondaryTrack == null -> emptyList()
        audioSecondaryTrackKey == null -> successState.subtitleSecondaryCues
        else -> selectedSecondaryCues.value?.getOrNull().orEmpty()
    }
    val subtitleLyrics = remember(
        successState.subtitlePrimaryCues,
        audioSecondaryCues,
        successState.subtitlePrimaryLikelyAi,
        successState.subtitlePrimaryLanguage,
        successState.subtitleTracks,
        successState.subtitlePrimaryTrackKey
    ) {
        val selectedTrack = successState.subtitleTracks.firstOrNull {
            it.trackKey == successState.subtitlePrimaryTrackKey
        }
        BiliSubtitleLyricsPolicy.convertSubtitlesToLyricDocument(
            primaryCues = successState.subtitlePrimaryCues,
            secondaryCues = audioSecondaryCues,
            isAiGenerated = successState.subtitlePrimaryLikelyAi,
            languageLabel = selectedTrack?.lanDoc ?: successState.subtitlePrimaryLanguage
        )
    }
    val subtitleLanguageOptions = remember(successState.subtitleTracks, successState.subtitlePrimaryTrackKey) {
        buildSubtitleTrackOptions(
            tracks = successState.subtitleTracks,
            selectedTrackKey = successState.subtitlePrimaryTrackKey
        ).map { it.trackKey to it.label }
    }
    var subtitleLanguageManuallySelected by remember(info.bvid, info.cid) { mutableStateOf(false) }
    var videoSubtitlesEnabled by rememberSaveable { mutableStateOf(true) }
    var subtitleOffsetMs by remember(info.bvid, info.cid) { mutableStateOf(0L) }
    val adjustedSubtitleLyrics = remember(subtitleLyrics, subtitleOffsetMs) {
        subtitleLyrics?.withOffset(subtitleOffsetMs)
    }

    val effectiveLyrics = remember(
        lyricsState.lyricsDocument,
        adjustedSubtitleLyrics,
        subtitleLanguageManuallySelected,
        videoSubtitlesEnabled,
    ) {
        // 视频字幕与搜索歌词都存在时，按时间轴对齐度取舍，避免错位的搜索歌词盖过本地字幕
        if (!videoSubtitlesEnabled) {
            lyricsState.lyricsDocument
        } else if (subtitleLanguageManuallySelected) {
            BiliSubtitleLyricsPolicy.resolveEffectiveLyrics(
                musicLyrics = null,
                subtitleLyrics = adjustedSubtitleLyrics
            )
        } else {
            BiliSubtitleLyricsPolicy.resolveEffectiveLyricsWithAlignment(
                musicLyrics = lyricsState.lyricsDocument,
                subtitleLyrics = adjustedSubtitleLyrics
            )
        }
    }

    MusicPlayerContent(
        sourceState = MusicPlayerUiState(
            title = displayTitle,
            artist = info.owner.name,
            coverUrl = coverUrl,
            isLoading = player == null,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs.takeIf { it > 0L } ?: metadataDurationMs,
            lyrics = effectiveLyrics,
            lyricsError = if (effectiveLyrics != null) null else lyricsState.lyricsError,
            lyricCandidates = lyricsState.lyricCandidates.map {
                MusicLyricCandidateUi(it.title, it.artist, it.source.name)
            },
            isLyricsSearching = lyricsState.isLyricsSearching && effectiveLyrics == null,
            queue = queue,
            currentQueueIndex = currentIndex,
            playMode = playMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playback.playbackSpeed
        ),
        // Video audio mode owns this player; the position clock stays inside content.
        playbackPositionSource = player,
        playbackPositionIdentity = "${info.bvid}:${info.cid}",
        onBack = onBack,
        onPlayPause = { player?.handleAudioModePlayPause() },
        onSeek = { positionMs ->
            player?.seekTo(positionMs)
            player?.let(PlayerVolumeController::applyPreferredVolume)
        },
        onPrevious = { viewModel.playPreviousAudioModeTrack() },
        onNext = { viewModel.playNextAudioModeTrack() },
        onQueueMove = if (playlist.isNotEmpty()) PlaylistManager::moveQueueItem else null,
        onQueueRemove = if (playlist.isNotEmpty()) PlaylistManager::removeQueueItem else null,
        onQueuePlayNext = if (playlist.isNotEmpty()) PlaylistManager::playQueueItemNext else null,
        onQueueClear = if (playlist.size > 1) PlaylistManager::clearUpcomingQueue else null,
        onImportToQueue = { importedItems ->
            PlaylistManager.setPlaylist(importedItems)
            importedItems.firstOrNull()?.let { first ->
                viewModel.loadVideo(
                    bvid = first.bvid,
                    cid = first.cid,
                    autoPlay = resolveAudioModePageSwitchAutoPlay()
                )
            }
        },
        onPlayFromHistory = { historyBvid, historyCid ->
            viewModel.loadVideo(bvid = historyBvid, cid = historyCid, autoPlay = true)
        },
        onQueueItemSelected = { index ->
            if (playlist.isNotEmpty()) {
                PlaylistManager.playAt(index)?.let { item ->
                    if (item.bvid == info.bvid && (item.cid == 0L || item.cid == info.cid)) {
                        if (player != null && !player.isPlaying) {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            playPlayerFromUserAction(player)
                        }
                    } else {
                        viewModel.loadVideo(
                            bvid = item.bvid,
                            cid = item.cid,
                            autoPlay = resolveAudioModePageSwitchAutoPlay()
                        )
                    }
                }
            } else if (info.pages.size > 1) {
                info.pages.getOrNull(index)?.let { page ->
                    viewModel.loadVideo(
                        bvid = info.bvid,
                        cid = page.cid,
                        autoPlay = resolveAudioModePageSwitchAutoPlay()
                    )
                }
            } else if (index > 0) {
                successState.related.getOrNull(index - 1)?.let { rel ->
                    viewModel.loadVideo(
                        bvid = rel.bvid,
                        cid = rel.cid,
                        autoPlay = resolveAudioModePageSwitchAutoPlay()
                    )
                }
            }
        },
        onPlayModeChange = PlaylistManager::setPlayMode,
        onShuffleEnabledChange = PlaylistManager::setShuffleEnabled,
        onLyricsOffsetChange = { deltaMs ->
            if (effectiveLyrics?.source == LyricSource.BILIBILI) {
                subtitleOffsetMs = (subtitleOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
            } else {
                lyricsViewModel.adjustLyricsOffset(deltaMs)
            }
        },
        subtitleLanguageOptions = subtitleLanguageOptions,
        secondarySubtitleLanguageOptions = subtitleLanguageOptions.filter { it.first != successState.subtitlePrimaryTrackKey },
        selectedSecondarySubtitleTrackKey = secondaryTrack?.trackKey,
        onSecondarySubtitleTrackSelected = { trackKey ->
            videoSubtitlesEnabled = true
            subtitleLanguageManuallySelected = true
            audioSecondaryTrackKey = trackKey
            audioSecondaryRequestId += 1
        },
        secondarySubtitleError = selectedSecondaryCues.value?.exceptionOrNull()?.let { "附加字幕加载失败，请重新选择语言重试" },
        videoSubtitlesEnabled = videoSubtitlesEnabled,
        onVideoSubtitlesEnabledChange = { videoSubtitlesEnabled = it },
        selectedSubtitleTrackKey = successState.subtitlePrimaryTrackKey,
        onSubtitleTrackSelected = { trackKey ->
            videoSubtitlesEnabled = true
            subtitleLanguageManuallySelected = true
            viewModel.selectSubtitleTrack(trackKey)
        },
        onLyricsRetry = lyricsViewModel::retryLyrics,
        onLyricsSearch = lyricsViewModel::searchLyrics,
        onLyricsCandidateSelected = { index ->
            subtitleLanguageManuallySelected = false
            lyricsViewModel.selectLyricsCandidate(index)
        },
        onVideoModeClick = {
            AudioNowPlayingSession.dismiss()
            onVideoModeClick(info.bvid, info.cid)
        },
        onCollectionClick = when {
            info.pages.size > 1 -> ({ showPageSelector = true })
            info.ugc_season != null -> ({ showCollectionSheet = true })
            else -> null
        },
        onSleepTimerClick = { showSleepTimerDialog = true },
        sleepTimerLabel = if (sleepAfterCurrent) "播完当前首后停止" else formatAudioModeSleepTimerButtonLabel(sleepTimerMinutes),
        audioQualityLabel = audioQualityPresentation.label,
        audioQualityOptions = successState.availableAudioQualities,
        requestedAudioQuality = successState.requestedAudioQuality,
        selectedAudioQuality = successState.selectedAudioQuality,
        isHiResAudioSelected = audioQualityPresentation.showHiResBadge,
        isDolbyAudioSelected = audioQualityPresentation.showDolbyBadge,
        onAudioQualitySelected = viewModel::setAudioQuality,
        onPipClick = if (showPipButton) onEnterPip else null,
        onToggleOrientation = onToggleOrientation,
        orientationActionLabel = orientationActionLabel,
        isLiked = engagementState.likeStatusKnown &&
            engagementState.subject?.aid == info.aid &&
            engagementState.subject?.bvid == info.bvid && engagementState.isLiked,
        isLikeStatusKnown = engagementState.likeStatusKnown &&
            engagementState.subject?.aid == info.aid &&
            engagementState.subject?.bvid == info.bvid,
        onLikeClick = {
            if (engagementState.likeStatusKnown && engagementState.subject?.aid == info.aid &&
                engagementState.subject?.bvid == info.bvid) {
                engagementViewModel.toggleLike(
                    aid = info.aid,
                    bvid = info.bvid,
                    currentlyLiked = engagementState.isLiked
                )
            }
        },
        onCommentsClick = { showComments = true },
        isFavorited = engagementState.isFavorited,
        onFavoriteClick = {
            when (
                resolveVideoFavoriteAction(
                    entryPoint = VideoFavoriteEntryPoint.AudioMode,
                    isLongPress = false,
                    quickSaveDefaultFolder = favoriteQuickSaveDefaultFolder,
                )
            ) {
                VideoFavoriteAction.ToggleFavorite -> engagementViewModel.toggleFavorite()
                VideoFavoriteAction.OpenFavoriteFolders -> viewModel.showFavoriteFolderDialog()
            }
        },
        onDownloadClick = { viewModel.downloadAudio(context) },
        onShareClick = { showShare = true },
        onSpeedClick = { showSpeedMenu = true },
        speedLabel = PlaybackSpeed.formatSpeed(currentSpeed),
        isInPipMode = isInPipMode,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled
    )

    if (showPageSelector && info.pages.size > 1) {
        PlayerEpisodeMenu(
            pages = info.pages,
            cover = info.pic,
            currentPageIndex = info.pages.indexOfFirst { it.cid == info.cid }.coerceAtLeast(0),
            onDismiss = { showPageSelector = false },
            onPageSelect = { index ->
                info.pages.getOrNull(index)?.let { page ->
                    showPageSelector = false
                    viewModel.loadVideo(
                        bvid = info.bvid,
                        cid = page.cid,
                        autoPlay = resolveAudioModePageSwitchAutoPlay()
                    )
                }
            }
        )
    }

    info.ugc_season?.let { season ->
        if (showCollectionSheet) {
            CollectionSheet(
                ugcSeason = season,
                currentVideoInfo = info,
                currentBvid = info.bvid,
                currentCid = info.cid,
                onDismiss = { showCollectionSheet = false },
                onEpisodeClick = { episode ->
                    showCollectionSheet = false
                    viewModel.loadVideo(
                        bvid = episode.bvid,
                        cid = episode.cid,
                        autoPlay = resolveAudioModeCollectionSwitchAutoPlay()
                    )
                }
            )
        }
    }

    LaunchedEffect(showComments, info.aid, info.owner.mid) {
        if (showComments) {
            commentViewModel.init(
                aid = info.aid,
                upMid = info.owner.mid,
                expectedReplyCount = info.stat.reply
            )
        }
    }
    if (showComments) {
        VideoCommentSheetHost(
            mainSheetVisible = true,
            onDismiss = { showComments = false },
            commentViewModel = commentViewModel,
            aid = info.aid,
            upMid = info.owner.mid,
            expectedReplyCount = info.stat.reply,
            onUserClick = {},
            onTimestampClick = { timestampMs -> player?.seekTo(timestampMs) }
        )
    }

    if (showSpeedMenu) {
        SpeedSelectionMenuDialog(
            currentSpeed = currentSpeed,
            onSpeedSelected = { speed ->
                viewModel.applyPlaybackSpeedFromUi(speed)
                showSpeedMenu = false
            },
            onDismiss = { showSpeedMenu = false }
        )
    }

    if (showShare) {
        VideoShareSheet(
            payload = buildVideoSharePayload(
                title = resolveAudioModeShareTitle(
                    videoTitle = info.title,
                    displayTitle = displayTitle,
                ),
                bvid = info.bvid,
                page = info.pages.indexOfFirst { it.cid == info.cid }.coerceAtLeast(0) + 1,
                positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                authorId = info.owner.mid,
                coverUrl = coverUrl,
                upName = info.owner.name,
                playCountText = com.android.purebilibili.core.util.FormatUtils.formatStat(
                    info.stat.view.toLong()
                ),
            ),
            onDismiss = { showShare = false }
        )
    }

    if (showSleepTimerDialog) {
        AudioModeSleepTimerDialog(
            currentMinutes = sleepTimerMinutes,
            sleepAfterCurrent = sleepAfterCurrent,
            onSleepAfterCurrent = {
                viewModel.setSleepAfterCurrent(!sleepAfterCurrent)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
            onSelectPreset = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onConfirmCustom = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            }
        )
    }
}

@Composable
private fun rememberAudioPlaybackSnapshot(player: Player?): AudioPlaybackSnapshot {
    var snapshot by remember(player) { mutableStateOf(player.readAudioPlaybackSnapshot()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                snapshot = player.readAudioPlaybackSnapshot()
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    return snapshot
}

private fun Player?.readAudioPlaybackSnapshot(): AudioPlaybackSnapshot {
    val player = this ?: return AudioPlaybackSnapshot()
    return AudioPlaybackSnapshot(
        isPlaying = player.isPlaying,
        isBuffering = player.playbackState == Player.STATE_BUFFERING,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        durationMs = player.duration.coerceAtLeast(0L),
        playbackSpeed = player.playbackParameters.speed
    )
}

/** 进程级标记：冷启动自动续播只在每次进程生命周期内触发一次。 */
internal object AudioStartupAutoPlayGuard {
    var handled: Boolean = false
}
