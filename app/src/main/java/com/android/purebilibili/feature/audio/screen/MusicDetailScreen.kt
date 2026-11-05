package com.android.purebilibili.feature.audio.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.audio.player.MusicPlayerUiState
import com.android.purebilibili.feature.audio.player.MusicLyricCandidateUi
import com.android.purebilibili.feature.audio.player.MusicQueueItemUi
import com.android.purebilibili.feature.audio.viewmodel.MusicUiState
import com.android.purebilibili.feature.audio.viewmodel.MusicViewModel
import com.android.purebilibili.feature.video.screen.AudioModeScreen
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel

/** AU 音频入口。播放器由 MiniPlayerManager 持有，离开页面后继续播放。 */
@Composable
internal fun MusicDetailScreen(
    sid: Long,
    onBack: () -> Unit,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pageActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    LaunchedEffect(viewModel, pageActive) { viewModel.setPageActive(pageActive) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.setPageActive(false) }
    }

    LaunchedEffect(sid) {
        viewModel.initPlayer(context)
        viewModel.loadMusic(sid)
    }

    MusicDetailContent(state, onBack, viewModel)
}

/** 视频 DASH 音轨入口。 */
@Composable
internal fun MusicDetailScreen(
    musicTitle: String,
    bvid: String,
    cid: Long,
    onBack: () -> Unit,
    onVideoModeClick: (String, Long) -> Unit,
    playerViewModel: VideoPlaybackViewModel = viewModel()
) {
    AudioModeScreen(
        viewModel = playerViewModel,
        onBack = onBack,
        onVideoModeClick = onVideoModeClick,
        initialBvid = bvid,
        initialCid = cid,
        titleOverride = musicTitle
    )
}

@Composable
private fun MusicDetailContent(
    state: MusicUiState,
    onBack: () -> Unit,
    viewModel: MusicViewModel
) {
    val context = LocalContext.current
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(
        initialValue = HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val title = state.songInfo?.title ?: state.musicTitle ?: "未知歌曲"
    val artist = state.songInfo?.author
        ?.ifBlank { state.songInfo.uname }
        ?: state.musicArtist.orEmpty()
    val cover = state.songInfo?.cover ?: state.musicCover.orEmpty()
    val stableId = state.source?.stableId ?: "loading"
    val item = MusicQueueItemUi(
        stableId = stableId,
        title = title,
        artist = artist,
        coverUrl = cover
    )

    MusicPlayerContent(
        sourceState = MusicPlayerUiState(
            title = title,
            artist = artist,
            coverUrl = cover,
            isLoading = state.isLoading,
            error = state.error,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            lyrics = state.lyricsDocument,
            lyricsError = state.lyricsError,
            lyricCandidates = state.lyricCandidates.map {
                MusicLyricCandidateUi(it.title, it.artist, it.source.name)
            },
            isLyricsSearching = state.isLyricsSearching,
            queue = listOf(item),
            currentQueueIndex = 0
        ),
        onBack = onBack,
        onPlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onLyricsOffsetChange = viewModel::adjustLyricsOffset,
        onLyricsRetry = viewModel::retryLyrics,
        onLyricsSearch = viewModel::searchLyrics,
        onLyricsCandidateSelected = viewModel::selectLyricsCandidate,
        liquidGlassEffectsEnabled = homeSettings.otherGlassEnabled
    )
}
