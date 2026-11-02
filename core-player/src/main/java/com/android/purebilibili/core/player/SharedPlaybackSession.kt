@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import android.content.Context
import android.media.MediaCodecList
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.dash.buildLocalDashManifest
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.getBestAudio
import com.android.purebilibili.data.model.response.getBestVideo
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class PlaybackStatus { Idle, Loading, Recovering, Ready, Ended, Failed }
enum class PlaybackFailureReason { Network, Decoder, Audio, Authentication, Restricted, Source }
data class PlaybackFailure(val reason: PlaybackFailureReason, val code: Int, val episode: Long)
data class PlaybackAudioOption(val id: Int, val label: String)


data class SharedPlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val info: ViewInfo? = null,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val actualQuality: Int = 0,
    val qualities: List<Pair<Int, String>> = emptyList(),
    val error: String? = null,
    val realPlayedMs: Long = 0,
    val startTsSec: Long = 0,
    val bufferedPositionMs: Long = 0,
    val canSeek: Boolean = false,
    val failure: PlaybackFailure? = null,
    val recoveryStage: String? = null,
    val audioOptions: List<PlaybackAudioOption> = emptyList(),
    val audioLanguages: List<Pair<String, String>> = emptyList(),
    val audioQuality: Int = -1,
    val audioLanguage: String? = null,

)

/** Owned by a screen/service. Caller owns loading coroutines, ticker, and close(). */
class SharedPlaybackSession(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(SharedPlaybackState())
    val state = mutableState.asStateFlow()
    val player: ExoPlayer = ExoPlayer.Builder(appContext, DefaultRenderersFactory(appContext).withVideoDecoderPreference(preferHardware = true)).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val mediaSession = MediaSession.Builder(appContext, player).build()
    private var closed = false
    private var generation = 0L
    private var lastTickMs = android.os.SystemClock.elapsedRealtime()
    private var currentRequest: SharedPlaybackRequest? = null
    private var retries = 0
    private var cdnIndex = 0
    private var alternatives = 0
    private var decoderFallback = false
    private var audioFallback = false
    private var failureEpisode = 0L
    private var desiredPlaying = true
    val requestedPlaying: Boolean get() = desiredPlaying
    private var preservedSpeed = 1f
    private val recent = RecentPlaybackStore(appContext)
    private var accountMid: Long? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            tick()
            if (player.playbackState == Player.STATE_ENDED) {
                persistPosition()
                mutableState.update { it.copy(status = PlaybackStatus.Ended) }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            desiredPlaying = player.playWhenReady
            preservedSpeed = player.playbackParameters.speed
            val audioRenderer = error is ExoPlaybackException && error.rendererFormat?.sampleMimeType?.startsWith("audio/") == true
            val audioError = (error.errorCode in 5000..5999 || audioRenderer) && mutableState.value.audioQuality in listOf(30250, 30255, 30251)
            fail(error, when {
                audioError -> PlaybackFailureReason.Audio
                isNetworkPlaybackError(error.errorCode) -> PlaybackFailureReason.Network
                error.errorCode in 4000..4999 || isDecoderLikeFailure(error.message, error.cause?.javaClass?.name) -> PlaybackFailureReason.Decoder
                else -> PlaybackFailureReason.Source
            }, error.errorCode)
        }
    }

    init { player.addListener(listener) }

    suspend fun load(request: SharedPlaybackRequest, playWhenReady: Boolean = true, recovering: Boolean = false) {
        check(!closed)
        persistPosition()
        if (!recovering) {
            retries = 0; cdnIndex = 0; decoderFallback = false; audioFallback = false
            accountMid = TokenManager.midCache
            desiredPlaying = playWhenReady
            preservedSpeed = player.playbackParameters.speed
        }
        currentRequest = request
        val previous = mutableState.value.let {
            if (it.info?.bvid == request.bvid && (request.cid == 0L || it.info?.cid == request.cid)) it
            else SharedPlaybackState(positionMs = request.startPositionMs ?: 0)
        }
        val ticket = ++generation
        player.stop()
        player.clearMediaItems()
        mutableState.update { previous.copy(status = if (recovering) PlaybackStatus.Recovering else PlaybackStatus.Loading,
            playing = false, canSeek = false, error = null, failure = null) }
        lastTickMs = android.os.SystemClock.elapsedRealtime()
        val result = SharedPlaybackRepository.load(request)
        currentCoroutineContext().ensureActive()
        if (closed || ticket != generation) return
        val loaded = result.getOrElse { loadError ->
            val error = (loadError as? PlaybackLoadException)?.cause ?: loadError
            (loadError as? PlaybackLoadException)?.info?.let { info ->
                val local = recent.items(accountMid).firstOrNull { it.bvid == info.bvid && it.cid == info.cid }?.progress?.times(1000L) ?: 0
                mutableState.update { it.copy(info = info, positionMs = request.startPositionMs ?: local,
                    durationMs = (info.pages.firstOrNull { page -> page.cid == info.cid }?.duration ?: 0) * 1000L) }
            }
            val code = (error as? com.android.purebilibili.data.repository.ContentRequestException)?.code ?: 0
            fail(error, when {
                code == -101 -> PlaybackFailureReason.Authentication
                code in listOf(-403, -404, -10403) -> PlaybackFailureReason.Restricted
                error is java.io.IOException -> PlaybackFailureReason.Network
                else -> PlaybackFailureReason.Source
            }, if (error is java.io.IOException) PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED else code)
            return
        }
        try {
            val streams = loaded.streams
            val info = loaded.info
            val prepared = createMediaSource(streams, request.quality, request.audioQuality)
            val qualityIds = streams.dash?.video.orEmpty().map { it.id }.distinct()
                .ifEmpty { streams.acceptQuality }
            val qualities = qualityIds.map { id ->
                id to (streams.acceptQuality.indexOf(id).takeIf { it >= 0 }
                    ?.let { streams.acceptDescription.getOrNull(it) }
                    ?: com.android.purebilibili.data.model.VideoQuality.fromCode(id)?.description ?: "$id")
            }
            val start = resolvePlaybackResumePosition(
                explicitMs = request.startPositionMs,
                localMs = recent.items(accountMid).firstOrNull { it.bvid == info.bvid && it.cid == info.cid }?.progress?.times(1000L) ?: 0,
                serverMs = loaded.serverPositionMs,
                durationMs = streams.timelength,
            )
            mutableState.update { SharedPlaybackState(
                status = PlaybackStatus.Ready, info = info, speed = preservedSpeed, actualQuality = prepared.quality,
                qualities = qualities, durationMs = streams.timelength, positionMs = start,
                startTsSec = previous.startTsSec.takeIf { recovering } ?: System.currentTimeMillis() / 1000L,
                realPlayedMs = previous.realPlayedMs.takeIf { recovering } ?: 0,
                audioOptions = (streams.dash?.audio.orEmpty() + streams.dash?.dolby?.takeIf { it.type > 0 }?.audio.orEmpty() + listOfNotNull(streams.dash?.flac?.audio))
                    .filter { it.id > 0 && it.getValidUrl().isNotBlank() }
                    .groupBy { it.id }.values.mapNotNull { tracks -> tracks.maxByOrNull { it.bandwidth } }.map { track ->
                        PlaybackAudioOption(track.id, when {
                            track.id == 30250 || track.id == 30255 -> "Dolby · ${track.id}"
                            track.id == 30251 || track.codecs.equals("flac", ignoreCase = true) -> "FLAC"
                            else -> "${track.bandwidth / 1000} kbps"
                        } + track.codecs.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                    },
                audioQuality = prepared.audioId,
                audioLanguages = streams.aiAudio?.items.orEmpty().filter { it.langCode.isNotBlank() }.map { it.langCode to it.langDoc },
                audioLanguage = streams.curLanguage ?: request.audioLanguage,

            ) }
            player.setMediaSource(prepared.source)
            player.seekTo(start)
            player.prepare()
            player.setPlaybackSpeed(preservedSpeed)
            player.playWhenReady = desiredPlaying && playWhenReady
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error, PlaybackFailureReason.Source, 0)
        }
    }

    private data class PreparedSource(val source: androidx.media3.exoplayer.source.MediaSource, val quality: Int, val audioId: Int = -1)

    private fun createMediaSource(data: PlayUrlData, quality: Int, audioQuality: Int): PreparedSource {
        val upstream = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient).setDefaultRequestProperties(mapOf(
            "Referer" to "https://www.bilibili.com/", "User-Agent" to "Mozilla/5.0",
        ))
        val dash = data.dash
        val supported = dash?.let { it.copy(video = it.video.filter { track ->
            val mime = MimeTypes.getMediaMimeType(track.codecs) ?: track.mimeType.ifBlank { MimeTypes.VIDEO_H264 }
            decoderMimeTypes.contains(mime) && (!decoderFallback || mime == MimeTypes.VIDEO_H264)
        }) }
        val rawVideo = supported?.getBestVideo(quality, preferCodec = "avc1", secondPreferCodec = "hev1",
            isHevcSupported = decoderMimeTypes.contains(MimeTypes.VIDEO_H265),
            isAv1Supported = decoderMimeTypes.contains(MimeTypes.VIDEO_AV1))
        val rawAudio = dash?.getBestAudio(if (audioFallback) -1 else audioQuality)
        val videoUrls = listOfNotNull(rawVideo?.baseUrl) + rawVideo?.backupUrl.orEmpty()
        val audioUrls = listOfNotNull(rawAudio?.baseUrl) + rawAudio?.backupUrl.orEmpty()
        alternatives = (videoUrls.filter { it.isNotBlank() }.distinct().size - 1).coerceAtLeast(0)
        val video = rawVideo?.copy(baseUrl = sourceUrl(videoUrls, cdnIndex))
        val audio = rawAudio?.copy(baseUrl = sourceUrl(audioUrls, cdnIndex))
        if (video != null) {
            if (!video.segmentBase?.initialization.isNullOrBlank() && !video.segmentBase?.indexRange.isNullOrBlank()
                && (audio == null || !audio.segmentBase?.initialization.isNullOrBlank() && !audio.segmentBase?.indexRange.isNullOrBlank())) {
                val manifest = buildLocalDashManifest(data.timelength, (requireNotNull(dash).minBufferTime * 1000).toLong(),
                    listOf(video), listOfNotNull(audio)).toByteArray()
                val manifestUri = android.net.Uri.parse("memory://bilipai/manifest.mpd")
                val manifestFactory = DataSource.Factory { ByteArrayDataSource(manifest) }
                val sourceFactory = DataSource.Factory {
                    val remote = upstream.createDataSource()
                    val local = manifestFactory.createDataSource()
                    object : DataSource {
                        private var selected: DataSource = remote
                        override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) {
                            remote.addTransferListener(listener); local.addTransferListener(listener)
                        }
                        override fun open(dataSpec: DataSpec): Long {
                            selected = if (dataSpec.uri == manifestUri) local else remote
                            return selected.open(dataSpec)
                        }
                        override fun read(buffer: ByteArray, offset: Int, length: Int) = selected.read(buffer, offset, length)
                        override fun getUri() = selected.uri
                        override fun getResponseHeaders() = selected.responseHeaders
                        override fun close() = selected.close()
                    }
                }
                return PreparedSource(DashMediaSource.Factory(sourceFactory).setLoadErrorHandlingPolicy(boundedLoadPolicy).createMediaSource(MediaItem.Builder()
                    .setUri(manifestUri).setMimeType(MimeTypes.APPLICATION_MPD).build()), video.id, audio?.id ?: -1)
            }
            val factory = ProgressiveMediaSource.Factory(upstream).setLoadErrorHandlingPolicy(boundedLoadPolicy)
            val videoSource = factory.createMediaSource(MediaItem.fromUri(video.getValidUrl()))
            val source = audio?.let { MergingMediaSource(videoSource, factory.createMediaSource(MediaItem.fromUri(it.getValidUrl()))) }
                ?: videoSource
            return PreparedSource(source, video.id, audio?.id ?: -1)
        }
        val segments = data.durl.orEmpty().filter { it.url.isNotBlank() }.sortedBy { it.order }
        check(segments.isNotEmpty()) { "没有兼容的播放流" }
        alternatives = segments.maxOf { it.backupUrl.orEmpty().size }
        val factory = ProgressiveMediaSource.Factory(upstream).setLoadErrorHandlingPolicy(boundedLoadPolicy)
        val sources = segments.map { factory.createMediaSource(MediaItem.fromUri(sourceUrl(listOf(it.url) + it.backupUrl.orEmpty(), cdnIndex))) }
        return PreparedSource(if (sources.size == 1) sources.first() else ConcatenatingMediaSource(*sources.toTypedArray()), data.quality)
    }

    private val boundedLoadPolicy = object : DefaultLoadErrorHandlingPolicy(3) {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            if (info.errorCount > 3) C.TIME_UNSET else super.getRetryDelayMsFor(info)
    }
    private fun sourceUrl(urls: List<String>, index: Int): String {
        val valid = urls.filter { it.isNotBlank() }.distinct()
        return valid.getOrNull(index) ?: valid.firstOrNull().orEmpty()
    }
    private fun fail(error: Throwable, reason: PlaybackFailureReason, code: Int) {
        mutableState.update { it.copy(status = PlaybackStatus.Failed, playing = false, buffering = false,
            failure = PlaybackFailure(reason, code, ++failureEpisode), error = error.message ?: "播放失败",
            recoveryStage = null) }
    }
    /** Caller owns this coroutine. Counters survive prepare failures and reset only on an explicit load. */
    suspend fun recover() {
        if (closed || mutableState.value.status != PlaybackStatus.Failed) return
        val failure = mutableState.value.failure ?: return
        if (failure.reason in listOf(PlaybackFailureReason.Authentication, PlaybackFailureReason.Restricted)) return
        val request = currentRequest ?: return
        val ticket = generation
        val action = decidePlayerErrorRecovery(failure.code, cdnIndex < alternatives, retries, 3, cdnIndex, 2,
            failure.reason == PlaybackFailureReason.Decoder, failure.reason == PlaybackFailureReason.Audio && !audioFallback)
        if (action == PlayerErrorRecoveryAction.GIVE_UP) return
        val position = mutableState.value.positionMs
        val label = when (action) {
            PlayerErrorRecoveryAction.FALLBACK_PREMIUM_AUDIO -> { audioFallback = true; "正在切换兼容音频…" }
            PlayerErrorRecoveryAction.SWITCH_CDN -> { cdnIndex++; "正在切换备用线路…" }
            PlayerErrorRecoveryAction.RETRY_DECODER_FALLBACK -> { retries++; decoderFallback = true; "正在切换兼容解码…" }
            else -> { retries++; "正在恢复播放（$retries/3）…" }
        }
        mutableState.update { it.copy(status = PlaybackStatus.Recovering, recoveryStage = label) }
        if (action == PlayerErrorRecoveryAction.RETRY_NETWORK) kotlinx.coroutines.delay((1000L shl (retries - 1)).coerceAtMost(8000))
        currentCoroutineContext().ensureActive()
        // A part/quality change, explicit retry or close supersedes this failure, even if
        // the caller did not cancel the recovery coroutine during its backoff.
        if (closed || ticket != generation || mutableState.value.failure?.episode != failure.episode) return
        load(request.copy(startPositionMs = position), desiredPlaying, recovering = true)
    }

    private val decoderMimeTypes by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filterNot { it.isEncoder }.flatMap { it.supportedTypes.toList() }.toSet()
        }.getOrDefault(setOf(MimeTypes.VIDEO_H264))
    }

    fun togglePlayPause() {
        if (closed) return
        when {
            player.playbackState == Player.STATE_ENDED -> replay()
            player.playWhenReady -> { desiredPlaying = false; player.pause() }
            else -> { desiredPlaying = true; player.play() }
        }
    }
    fun replay() {
        mutableState.update { it.copy(status = PlaybackStatus.Ready) }
        desiredPlaying = true
        player.seekTo(0)
        player.play()
    }
    fun pause() { if (!closed) { desiredPlaying = false; player.pause(); persistPosition() } }
    fun seekTo(positionMs: Long) {
        if (closed || !player.isCurrentMediaItemSeekable) return
        val duration = player.duration.takeIf { it > 0 } ?: mutableState.value.durationMs
        if (duration > 0) {
            if (mutableState.value.status == PlaybackStatus.Ended) mutableState.update { it.copy(status = PlaybackStatus.Ready) }
            player.seekTo(positionMs.coerceIn(0, duration))
        }
    }
    fun setSpeed(speed: Float) { if (!closed) { player.setPlaybackSpeed(speed.coerceIn(0.5f, 2f)); mutableState.update { it.copy(speed = player.playbackParameters.speed) } } }

    fun tick() {
        if (closed) return
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsed = (now - lastTickMs).coerceIn(0, 1_000)
        lastTickMs = now
        if (mutableState.value.status in listOf(PlaybackStatus.Loading, PlaybackStatus.Recovering, PlaybackStatus.Failed)) return
        mutableState.update { it.copy(realPlayedMs = it.realPlayedMs + if (it.playing) elapsed else 0,
            playing = player.isPlaying, speed = player.playbackParameters.speed, buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0), durationMs = player.duration.takeIf { it > 0 } ?: it.durationMs,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0),
            canSeek = player.isCurrentMediaItemSeekable) }
    }

    fun persistPosition() {
        if (closed) return
        val info = mutableState.value.info ?: return
        val snapshot = mutableState.value
        val position = if (snapshot.status in listOf(PlaybackStatus.Loading, PlaybackStatus.Recovering, PlaybackStatus.Failed)) snapshot.positionMs else player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: snapshot.durationMs
        // The mobile progress store stays unchanged; TV restore reads its account-scoped metadata.
        recent.save(accountMid, info, position, duration)
    }

    suspend fun reportProgress() {
        val snapshot = mutableState.value
        val info = snapshot.info ?: return
        if (TokenManager.sessDataCache.isNullOrBlank()) return
        HistoryRepository.reportPlayback(info.bvid, info.cid, snapshot.positionMs / 1000,
            snapshot.realPlayedMs / 1000, snapshot.startTsSec, info.aid)
    }

    override fun close() {
        if (closed) return
        persistPosition()
        closed = true
        generation++
        player.removeListener(listener)
        mediaSession.release()
        player.release()
    }
}
