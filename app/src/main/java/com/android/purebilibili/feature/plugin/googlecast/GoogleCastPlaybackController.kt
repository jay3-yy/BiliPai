package com.android.purebilibili.feature.plugin.googlecast

import com.android.purebilibili.plugin.sdk.CastPluginPlaybackState
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object GoogleCastPlaybackController {

    private const val POLL_INTERVAL_MS = 500L
    private const val MISSING_MEDIA_GRACE_MS = 10_000L
    private val MAX_MISSING_MEDIA_ATTEMPTS = (MISSING_MEDIA_GRACE_MS / POLL_INTERVAL_MS).toInt()

    private val _playbackState = MutableStateFlow(CastPluginPlaybackState())
    val playbackState: StateFlow<CastPluginPlaybackState> = _playbackState.asStateFlow()

    private var session: CastSession? = null
    private var pollJob: Job? = null
    private var missingMediaAttempts = 0
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    internal fun shouldKeepPollingForMediaStatus(
        remoteClientExists: Boolean,
        missingMediaAttempts: Int,
        maxAttempts: Int
    ): Boolean {
        return remoteClientExists && missingMediaAttempts < maxAttempts
    }

    fun attach(session: CastSession, title: String = "", deviceLabel: String = "") {
        detach()
        missingMediaAttempts = 0
        this.session = session
        val remoteClient = session.remoteMediaClient ?: run {
            _playbackState.value = CastPluginPlaybackState()
            return
        }
        _playbackState.value = CastPluginPlaybackState(
            isActive = true,
            deviceLabel = deviceLabel.ifBlank { session.castDevice?.friendlyName ?: "" },
            title = title.ifBlank { remoteClient.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE) ?: "" },
            isPlaying = remoteClient.isPlaying,
            isBuffering = remoteClient.isBuffering,
            currentPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = remoteClient.streamDuration.coerceAtLeast(0L),
            bufferedPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            canSeek = true
        )
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val client = this@GoogleCastPlaybackController.session?.remoteMediaClient
                if (client == null) {
                    _playbackState.value = CastPluginPlaybackState()
                    return@launch
                }
                if (client.mediaInfo == null) {
                    if (!shouldKeepPollingForMediaStatus(
                            remoteClientExists = true,
                            missingMediaAttempts = missingMediaAttempts,
                            maxAttempts = MAX_MISSING_MEDIA_ATTEMPTS
                        )) {
                        _playbackState.value = CastPluginPlaybackState()
                        return@launch
                    }
                    missingMediaAttempts++
                    _playbackState.update {
                        it.copy(
                            isActive = true,
                            isBuffering = true
                        )
                    }
                    continue
                }
                missingMediaAttempts = 0
                val isPlaying = client.isPlaying
                val isBuffering = client.isBuffering
                val positionMs = client.approximateStreamPosition.coerceAtLeast(0L)
                val durationMs = client.streamDuration.coerceAtLeast(0L)
                _playbackState.update {
                    it.copy(
                        isActive = true,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        currentPositionMs = positionMs,
                        durationMs = durationMs,
                        bufferedPositionMs = positionMs
                    )
                }
            }
        }
    }

    fun detach() {
        pollJob?.cancel()
        pollJob = null
        session = null
        missingMediaAttempts = 0
        _playbackState.value = CastPluginPlaybackState()
    }

    suspend fun play(): Result<Unit> = runCatching {
        val client = session?.remoteMediaClient
            ?: error("无活动投屏会话")
        client.play()
    }

    suspend fun pause(): Result<Unit> = runCatching {
        val client = session?.remoteMediaClient
            ?: error("无活动投屏会话")
        client.pause()
    }

    suspend fun seek(positionMs: Long): Result<Unit> = runCatching {
        val client = session?.remoteMediaClient
            ?: error("无活动投屏会话")
        val options = com.google.android.gms.cast.MediaSeekOptions.Builder()
            .setPosition(positionMs)
            .build()
        client.seek(options)
    }
}
