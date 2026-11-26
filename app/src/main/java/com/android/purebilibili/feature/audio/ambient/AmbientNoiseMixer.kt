package com.android.purebilibili.feature.audio.ambient

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 听视频本地轻量白噪音混音器。
 * 独立使用 AudioTrack 与主播放器混音播放，定时关闭触发时自动同步淡停释放。
 */
object AmbientNoiseMixer {
    private const val TAG = "AmbientNoiseMixer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AmbientNoiseUiState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    @Volatile
    private var activeTrack: AudioTrack? = null

    fun selectPreset(preset: AmbientNoisePreset) {
        if (preset == AmbientNoisePreset.NONE) {
            stop()
            return
        }
        val current = _state.value
        if (current.preset == preset && current.isPlaying && streamJob?.isActive == true) {
            return
        }
        stopInternal(keepPreset = false)
        val volume = current.volume
        _state.update { it.copy(preset = preset, isPlaying = true) }
        streamJob = scope.launch {
            runAmbientStream(preset = preset, initialVolume = volume)
        }
    }

    fun setVolume(volume: Float) {
        val normalized = normalizeAmbientNoiseVolume(volume)
        _state.update { it.copy(volume = normalized) }
        runCatching { activeTrack?.setVolume(normalized) }
    }

    fun stop() {
        stopInternal(keepPreset = false)
    }

    private fun stopInternal(keepPreset: Boolean) {
        streamJob?.cancel()
        streamJob = null
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.release()
            }
        }
        _state.update {
            it.copy(
                preset = if (keepPreset) it.preset else AmbientNoisePreset.NONE,
                isPlaying = false,
            )
        }
    }

    private fun runAmbientStream(preset: AmbientNoisePreset, initialVolume: Float) {
        val minBufBytes = AudioTrack.getMinBufferSize(
            AMBIENT_NOISE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val bufferBytes = (minBufBytes * 2).coerceAtLeast(8192)
        val chunkSamples = (bufferBytes / 2).coerceIn(1024, 4096)
        val pcmChunk = ShortArray(chunkSamples)
        val synth = AmbientNoiseSynthesizer()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AMBIENT_NOISE_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (error: Exception) {
            Logger.w(TAG, "Failed to initialize ambient AudioTrack", error)
            _state.update { it.copy(preset = AmbientNoisePreset.NONE, isPlaying = false) }
            return
        }

        activeTrack = track
        try {
            track.setVolume(normalizeAmbientNoiseVolume(initialVolume))
            track.play()
            while (scope.isActive && activeTrack === track) {
                synth.fillBuffer(preset, pcmChunk)
                val written = track.write(pcmChunk, 0, pcmChunk.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(TAG, "Ambient audio stream stopped", error)
        } finally {
            if (activeTrack === track) {
                activeTrack = null
                _state.update { it.copy(preset = AmbientNoisePreset.NONE, isPlaying = false) }
            }
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.release()
            }
        }
    }
}
