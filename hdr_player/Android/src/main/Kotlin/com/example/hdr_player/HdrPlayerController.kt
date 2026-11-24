package com.example.hdr_player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import io.flutter.plugin.common.EventChannel

class HdrPlayerController(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val hdrDisplaySupported: Boolean
) {
    companion object {
        private const val TAG = "HdrPlayerController"
    }
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var renderThread: GlRenderThread? = null
    private var pendingPrepare: PrepareRequest? = null
    private var toneMapOptions: ToneMapOptions = ToneMapOptions()
    private var surfaceReady = false
    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventTicker: Runnable? = null
    private var lastVideoSize: VideoSize = VideoSize.UNKNOWN

    init {
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                initRenderThread(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                renderThread?.setViewportSize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                player.setVideoSurface(null)
                renderThread?.shutdown()
                renderThread = null
            }
        }
        surfaceView.holder.addCallback(callback)
        if (surfaceView.holder.surface?.isValid == true) {
            initRenderThread(surfaceView.holder)
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "player error: ${error.errorCodeName}", error)
                sendEvent(mapOf("error" to error.errorCodeName))
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                lastVideoSize = videoSize
                renderThread?.setVideoSize(
                    videoSize.width,
                    videoSize.height,
                    videoSize.pixelWidthHeightRatio,
                    videoSize.unappliedRotationDegrees
                )
                sendEvent(mapOf(
                    "width" to videoSize.width,
                    "height" to videoSize.height,
                    "pixelRatio" to videoSize.pixelWidthHeightRatio,
                    "rotation" to videoSize.unappliedRotationDegrees
                ))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "playbackState=$playbackState")
                sendStateEvent()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying=$isPlaying")
                sendStateEvent()
            }
        })
    }

    fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
        if (sink == null) {
            stopEventTicker()
        } else {
            startEventTicker()
        }
    }

    fun prepare(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>?,
        autoplay: Boolean,
        startPositionMs: Long?
    ) {
        Log.d(TAG, "prepare: videoUrl=${videoUrl.take(120)}, audioUrl=${audioUrl?.take(120)}, autoplay=$autoplay, surfaceReady=$surfaceReady")
        if (!surfaceReady) {
            pendingPrepare = PrepareRequest(videoUrl, audioUrl, headers, autoplay, startPositionMs)
            return
        }

        val httpFactory = DefaultHttpDataSource.Factory()
        if (!headers.isNullOrEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val videoItem = MediaItem.fromUri(toUri(videoUrl))
        val videoSource = mediaSourceFactory.createMediaSource(videoItem)
        val mediaSource = if (!audioUrl.isNullOrBlank()) {
            val audioItem = MediaItem.fromUri(toUri(audioUrl))
            val audioSource = mediaSourceFactory.createMediaSource(audioItem)
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        player.setMediaSource(mediaSource)
        if (startPositionMs != null && startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = autoplay
        sendStateEvent()
    }

    fun play() {
        Log.d(TAG, "play")
        player.playWhenReady = true
    }

    fun pause() {
        Log.d(TAG, "pause")
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setRate(rate: Double) {
        player.setPlaybackSpeed(rate.toFloat())
    }

    fun setVolume(volume: Double) {
        player.volume = volume.toFloat().coerceIn(0f, 1f)
    }

    fun setToneMapOptions(options: ToneMapOptions) {
        toneMapOptions = options
        renderThread?.setToneMapOptions(options)
    }

    fun release() {
        stopEventTicker()
        player.release()
        renderThread?.shutdown()
        renderThread = null
    }

    private fun initRenderThread(holder: SurfaceHolder) {
        if (surfaceReady) return
        renderThread = GlRenderThread(holder.surface, hdrDisplaySupported).also { thread ->
            thread.start()
            if (!thread.awaitReady()) {
                Log.e(TAG, "renderer_init_failed (timeout)")
                sendEvent(mapOf("error" to "renderer_init_failed"))
                return
            }
            if (thread.getInputSurface() == null) {
                Log.e(TAG, "renderer_surface_missing")
                sendEvent(mapOf("error" to "renderer_surface_missing"))
                return
            }
            if (hdrDisplaySupported && !thread.isHdrRenderSupported()) {
                Log.w(TAG, "hdr_render_not_supported")
                sendEvent(mapOf("error" to "hdr_render_not_supported"))
            }
            surfaceReady = true
            player.setVideoSurface(thread.getInputSurface())
            thread.setToneMapOptions(toneMapOptions)
            pendingPrepare?.let {
                prepare(it.videoUrl, it.audioUrl, it.headers, it.autoplay, it.startPositionMs)
                pendingPrepare = null
            }
        }
    }

    private fun startEventTicker() {
        if (eventTicker != null) return
        val runnable = object : Runnable {
            override fun run() {
                sendStateEvent()
                mainHandler.postDelayed(this, 250)
            }
        }
        eventTicker = runnable
        mainHandler.post(runnable)
    }

    private fun stopEventTicker() {
        eventTicker?.let { mainHandler.removeCallbacks(it) }
        eventTicker = null
    }

    private fun sendStateEvent() {
        val duration = player.duration
        val position = player.currentPosition
        val buffered = player.bufferedPosition
        val buffering = player.playbackState == Player.STATE_BUFFERING
        val completed = player.playbackState == Player.STATE_ENDED
        sendEvent(
            mapOf(
                "positionMs" to position,
                "durationMs" to duration,
                "bufferedMs" to buffered,
                "playing" to player.isPlaying,
                "buffering" to buffering,
                "completed" to completed,
                "width" to lastVideoSize.width,
                "height" to lastVideoSize.height,
                "pixelRatio" to lastVideoSize.pixelWidthHeightRatio,
                "rotation" to lastVideoSize.unappliedRotationDegrees
            )
        )
    }

    private fun sendEvent(payload: Map<String, Any?>) {
        mainHandler.post {
            eventSink?.success(payload)
        }
    }

    private fun toUri(input: String): Uri {
        return if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://")) {
            Uri.parse(input)
        } else {
            Uri.fromFile(java.io.File(input))
        }
    }

    private data class PrepareRequest(
        val videoUrl: String,
        val audioUrl: String?,
        val headers: Map<String, String>?,
        val autoplay: Boolean,
        val startPositionMs: Long?
    )
}
