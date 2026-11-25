package com.android.purebilibili.feature.audio.lyrics

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

internal const val LYRIC_OFFSET_LIMIT_MS = 10_000L

@Serializable
internal enum class LyricSource {
    BILIBILI,
    NETEASE,
    QQ_MUSIC,
    KUGOU,
    MANUAL
}

@Immutable
@Serializable
internal data class LyricSpan(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Immutable
@Serializable
internal data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val translations: List<String> = emptyList(),
    val romanization: String? = null,
    val spans: List<LyricSpan> = emptyList()
)

@Immutable
@Serializable
internal data class LyricDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList(),
    val source: LyricSource = LyricSource.BILIBILI,
    val remoteId: String? = null,
    val offsetMs: Long = 0L,
    val manuallySelected: Boolean = false,
    val fetchedAtMs: Long = 0L
) {
    fun withOffset(offsetMs: Long): LyricDocument {
        return copy(offsetMs = offsetMs.coerceIn(-LYRIC_OFFSET_LIMIT_MS, LYRIC_OFFSET_LIMIT_MS))
    }
}

internal data class LyricQuery(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val originalTitle: String = title,
    val artistIsUploader: Boolean = false,
)

internal data class LyricCandidate(
    val source: LyricSource,
    val remoteId: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)
