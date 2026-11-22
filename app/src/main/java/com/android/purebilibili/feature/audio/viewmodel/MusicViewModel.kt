package com.android.purebilibili.feature.audio.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.SongInfoData
import com.android.purebilibili.data.repository.AudioRepository
import com.android.purebilibili.feature.audio.lyrics.FileLyricsCache
import com.android.purebilibili.feature.audio.lyrics.KugouLyricsProvider
import com.android.purebilibili.feature.audio.lyrics.LyricDocument
import com.android.purebilibili.feature.audio.lyrics.LyricCandidate
import com.android.purebilibili.feature.audio.lyrics.LyricQuery
import com.android.purebilibili.feature.audio.lyrics.LyricsLoadResult
import com.android.purebilibili.feature.audio.lyrics.LyricsRepository
import com.android.purebilibili.feature.audio.lyrics.NeteaseLyricsProvider
import com.android.purebilibili.feature.audio.lyrics.QqMusicLyricsProvider
import com.android.purebilibili.feature.audio.player.MusicPlaybackSource
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class MusicUiState(
    val isLoading: Boolean = false,
    val songInfo: SongInfoData? = null,
    val lyrics: String? = null,
    val lyricsDocument: LyricDocument? = null,
    val lyricsOwnerKey: String? = null,
    val lyricsError: String? = null,
    val lyricCandidates: List<LyricCandidate> = emptyList(),
    val isLyricsSearching: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val currentPositionMs: Long = 0,
    val musicTitle: String? = null,
    val musicCover: String? = null,
    val musicArtist: String? = null,
    val source: MusicPlaybackSource? = null
)

internal class MusicViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    // 高频播放进度与顶层 MusicUiState 解耦，避免 4Hz 进度轮询驱动外层页面整树重组。
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var miniPlayerManager: MiniPlayerManager? = null
    private var lyricsRepository: LyricsRepository? = null
    private var observedPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val pageActive = MutableStateFlow(false)

    /** UI sampling only; the MiniPlayerManager continues owning audio playback. */
    fun setPageActive(active: Boolean) {
        pageActive.value = active
        if (active) updateProgress()
    }

    private var lyricsJob: Job? = null
    private var lyricsOffsetSaveJob: Job? = null
    private var lastLyricsCacheKey: String? = null
    private var lastLyricsQuery: LyricQuery? = null
    private var lastBilibiliLyrics: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (pageActive.value) updateProgress()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (pageActive.value) updateProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = observedPlayer
            val nextPosition = player?.currentPosition?.coerceAtLeast(0L)
            if (nextPosition != null) {
                _positionMs.value = nextPosition
            }
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = player?.duration?.coerceAtLeast(0L) ?: it.durationMs,
                )
            }
        }
    }

    fun initPlayer(context: Context) {
        if (miniPlayerManager != null) return
        val appContext = context.applicationContext
        miniPlayerManager = MiniPlayerManager.getInstance(appContext)
        val client = NetworkModule.okHttpClient
        lyricsRepository = LyricsRepository(
            providers = listOf(
                NeteaseLyricsProvider(client),
                QqMusicLyricsProvider(client),
                KugouLyricsProvider(client)
            ),
            cache = FileLyricsCache(File(appContext.filesDir, "lyrics"))
        )
    }

    fun loadMusic(sid: Long) {
        val manager = miniPlayerManager ?: return
        viewModelScope.launch {
            _uiState.update {
                MusicUiState(
                    isLoading = true,
                    source = MusicPlaybackSource.AudioSong(sid)
                )
            }

            val infoResponse = AudioRepository.getSongInfo(sid)
            val songInfo = infoResponse.data
            if (infoResponse.code != 0 || songInfo == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "加载歌曲信息失败: ${infoResponse.msg}")
                }
                return@launch
            }
            val streamResponse = AudioRepository.getSongStream(sid)
            val streamUrl = streamResponse.data?.cdns?.firstOrNull()
            if (streamResponse.code != 0 || streamUrl.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "加载音频流失败: ${streamResponse.msg}")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    songInfo = songInfo,
                    musicTitle = songInfo.title,
                    musicCover = songInfo.cover,
                    musicArtist = songInfo.author.ifBlank { songInfo.uname },
                    durationMs = songInfo.duration.coerceAtLeast(0) * 1_000L
                )
            }
            val player = manager.startAudio(
                mediaId = MusicPlaybackSource.AudioSong(sid).stableId,
                title = songInfo.title,
                cover = songInfo.cover,
                artist = songInfo.author.ifBlank { songInfo.uname },
                audioUrl = streamUrl
            )
            observePlayer(player)

            val bilibiliLyrics = AudioRepository.getSongLyric(sid).data
            loadLyrics(
                cacheKey = MusicPlaybackSource.AudioSong(sid).stableId,
                query = LyricQuery(
                    title = songInfo.title,
                    artist = songInfo.author.ifBlank { songInfo.uname },
                    durationMs = songInfo.duration.coerceAtLeast(0) * 1_000L
                ),
                bilibiliLyrics = bilibiliLyrics
            )
        }
    }

    fun togglePlayPause() {
        miniPlayerManager?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        miniPlayerManager?.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    fun updateProgress() {
        val player = observedPlayer ?: return
        val nextPosition = player.currentPosition.coerceAtLeast(0L)
        _positionMs.value = nextPosition
        val isPlaying = player.isPlaying
        val durationMs = player.duration.coerceAtLeast(0L)
        _uiState.update { current ->
            if (current.isPlaying == isPlaying && current.durationMs == durationMs) {
                current
            } else {
                current.copy(
                    isPlaying = isPlaying,
                    durationMs = durationMs,
                )
            }
        }
    }

    fun adjustLyricsOffset(offsetMs: Long) {
        val document = _uiState.value.lyricsDocument ?: return
        val adjusted = document.withOffset(document.offsetMs + offsetMs)
        _uiState.update { it.copy(lyricsDocument = adjusted) }
        val cacheKey = lastLyricsCacheKey ?: return
        val repository = lyricsRepository ?: return
        lyricsOffsetSaveJob?.cancel()
        lyricsOffsetSaveJob = viewModelScope.launch {
            repository.save(cacheKey, adjusted)
        }
    }

    fun loadLyricsForVideo(
        title: String,
        artist: String,
        bvid: String,
        cid: Long,
        durationMs: Long,
        originalTitle: String = title,
        artistIsUploader: Boolean = true,
    ) {
        val cacheKey = MusicPlaybackSource.VideoAudio(bvid, cid, title).stableId
        val query = LyricQuery(title, artist, durationMs, originalTitle, artistIsUploader)
        if (lastLyricsCacheKey == cacheKey && _uiState.value.lyricsDocument?.manuallySelected == true) {
            lastLyricsQuery = query
            return
        }
        lyricsJob?.cancel()
        lyricsOffsetSaveJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(lyricsDocument = null, lyricsOwnerKey = cacheKey, lyricsError = null, lyricCandidates = emptyList(), isLyricsSearching = true)
            }
            loadLyrics(
                cacheKey = cacheKey,
                query = query,
                bilibiliLyrics = null
            )
        }
    }

    fun retryLyrics() {
        val cacheKey = lastLyricsCacheKey ?: return
        val query = lastLyricsQuery ?: return
        lyricsJob?.cancel()
        lyricsOffsetSaveJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(lyricsDocument = null, lyricCandidates = emptyList(), isLyricsSearching = true)
            }
            loadLyrics(cacheKey, query, lastBilibiliLyrics, forceRefresh = true)
        }
    }

    fun searchLyrics(title: String) {
        val cacheKey = lastLyricsCacheKey ?: return
        val previousQuery = lastLyricsQuery ?: return
        val repository = lyricsRepository ?: return
        lyricsJob?.cancel()
        lyricsOffsetSaveJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLyricsSearching = true, lyricCandidates = emptyList()) }
            val candidates = repository.search(previousQuery.copy(title = title.ifBlank { previousQuery.title }))
            currentCoroutineContext().ensureActive()
            if (lastLyricsCacheKey != cacheKey) return@launch
            lastLyricsCacheKey = cacheKey
            _uiState.update { it.copy(isLyricsSearching = false, lyricCandidates = candidates) }
        }
    }

    fun selectLyricsCandidate(index: Int) {
        val cacheKey = lastLyricsCacheKey ?: return
        val candidate = _uiState.value.lyricCandidates.getOrNull(index) ?: return
        val repository = lyricsRepository ?: return
        lyricsJob?.cancel()
        lyricsOffsetSaveJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLyricsSearching = true) }
            val result = repository.select(cacheKey, candidate)
            currentCoroutineContext().ensureActive()
            if (lastLyricsCacheKey != cacheKey) return@launch
            when (result) {
                is LyricsLoadResult.Found -> _uiState.update {
                    it.copy(
                        lyricsDocument = result.document,
                        lyricsError = null,
                        lyricCandidates = emptyList(),
                        isLyricsSearching = false
                    )
                }
                LyricsLoadResult.NotFound -> _uiState.update { it.copy(isLyricsSearching = false) }
                LyricsLoadResult.Failed -> _uiState.update {
                    it.copy(isLyricsSearching = false, lyricsError = "歌词加载失败，请检查网络后重试")
                }
            }
        }
    }

    private suspend fun loadLyrics(
        cacheKey: String,
        query: LyricQuery,
        bilibiliLyrics: String?,
        forceRefresh: Boolean = false
    ) {
        val repository = lyricsRepository ?: return
        currentCoroutineContext().ensureActive()
        _uiState.update { it.copy(isLyricsSearching = true, lyricsError = null, lyricsOwnerKey = cacheKey) }
        lastLyricsCacheKey = cacheKey
        lastLyricsQuery = query
        lastBilibiliLyrics = bilibiliLyrics
        val result = repository.load(cacheKey, query, bilibiliLyrics, forceRefresh)
        currentCoroutineContext().ensureActive()
        if (lastLyricsCacheKey != cacheKey) return
        when (result) {
            is LyricsLoadResult.Found -> {
                val document = result.document
                _uiState.update {
                    it.copy(
                        lyricsDocument = document,
                        lyrics = document.lines.joinToString("\n") { line -> line.text },
                        lyricsError = null,
                        isLyricsSearching = false
                    )
                }
            }
            LyricsLoadResult.NotFound -> _uiState.update {
                it.copy(isLyricsSearching = false, lyricsError = null)
            }
            LyricsLoadResult.Failed -> _uiState.update {
                it.copy(isLyricsSearching = false, lyricsError = "歌词加载失败，请检查网络后重试")
            }
        }
    }

    private fun observePlayer(player: ExoPlayer) {
        if (observedPlayer === player) return
        observedPlayer?.removeListener(playerListener)
        observedPlayer = player
        player.addListener(playerListener)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            combine(pageActive, uiState) { visible, state -> visible && state.isPlaying }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (isActive) {
                        updateProgress()
                        delay(250L)
                    }
                }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        lyricsJob?.cancel()
        lyricsOffsetSaveJob?.cancel()
        observedPlayer?.removeListener(playerListener)
        observedPlayer = null
        super.onCleared()
    }
}
