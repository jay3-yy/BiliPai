package com.android.purebilibili.feature.audio.player

import android.os.Bundle
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val AUDIO_PREPARE = "bilipai.audio.PREPARE"
internal const val AUDIO_COMMIT = "bilipai.audio.COMMIT"
internal const val AUDIO_RETURN = "bilipai.audio.RETURN"
internal const val AUDIO_SNAPSHOT = "bilipai.audio.SNAPSHOT"
internal const val AUDIO_TRANSPORT = "bilipai.audio.TRANSPORT"
internal const val AUDIO_CLOSE = "bilipai.audio.CLOSE"
internal const val AUDIO_PAYLOAD = "payload"
internal val backgroundAudioJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
internal data class BackgroundAudioSnapshot(
    val queue: List<PlaylistItem>,
    val index: Int,
    val positionMs: Long,
    val playing: Boolean,
    val speed: Float,
    val accountMid: Long?,
    val playMode: PlayMode,
    val shuffle: Boolean = false,
    val quality: Int = 64,
    val audioQuality: Int = -1,
    val audioUrl: String = "",
    val audioLanguage: String? = null,
    val volume: Float = 1f,
    val handleAudioFocus: Boolean = true,
    val reportHistory: Boolean = false,
    val savedPlayMode: PlayMode? = null,
    val savedShuffle: Boolean? = null,
    val backgroundPlaybackOverride: Boolean? = null,
    val backgroundOverrideBvid: String? = null,
) {
    val current: PlaylistItem? get() = queue.getOrNull(index)
}

/** 恢复记录使用已保存的顺序，临时后台播放只随正在运行的接力传递。 */
internal fun BackgroundAudioSnapshot.forPersistence(): BackgroundAudioSnapshot = copy(
    playMode = savedPlayMode ?: playMode,
    shuffle = savedShuffle ?: shuffle,
    backgroundPlaybackOverride = null,
    backgroundOverrideBvid = null,
)

internal fun BackgroundAudioSnapshot.toAudioBundle(): Bundle = Bundle().apply {
    val json = backgroundAudioJson.encodeToString(BackgroundAudioSnapshot.serializer(), this@toAudioBundle)
    require(json.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "Audio queue exceeds transfer budget" }
    putString(AUDIO_PAYLOAD, json)
}

internal fun Bundle.audioSnapshot(): BackgroundAudioSnapshot? = getString(AUDIO_PAYLOAD)?.let {
    runCatching { backgroundAudioJson.decodeFromString(BackgroundAudioSnapshot.serializer(), it) }.getOrNull()
}

internal fun canTransferBackgroundAudio(
    listening: Boolean,
    hasPageOwner: Boolean,
    liveOrPip: Boolean,
    hasSeparatePlaybackAccount: Boolean,
    snapshot: BackgroundAudioSnapshot
): Boolean = listening && !hasPageOwner && !liveOrPip && !hasSeparatePlaybackAccount &&
    snapshot.queue.isNotEmpty() && snapshot.queue.size <= 128 && snapshot.current != null &&
    snapshot.queue.none { it.isBangumi || it.bvid.isBlank() } &&
    snapshot.queue.distinctBy { it.bvid }.size == snapshot.queue.size && snapshot.audioUrl.startsWith("https://") &&
    snapshot.speed.isFinite() && snapshot.speed in 0.25f..4f

internal fun nextBackgroundAudioIndex(size: Int, index: Int, mode: PlayMode, shuffle: Boolean): Int? {
    if (index !in 0 until size) return null
    if (mode == PlayMode.REPEAT_ONE || size == 1 && mode == PlayMode.REPEAT_ALL) return index
    if (shuffle || mode == PlayMode.SHUFFLE) {
        if (size <= 1) return index
        val offset = kotlin.random.Random.nextInt(1, size)
        return (index + offset) % size
    }
    return if (index + 1 < size) index + 1 else if (mode == PlayMode.REPEAT_ALL) 0 else null
}
