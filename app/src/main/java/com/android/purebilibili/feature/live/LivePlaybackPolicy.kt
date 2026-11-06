package com.android.purebilibili.feature.live

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.android.purebilibili.data.model.response.CodecInfo
import com.android.purebilibili.data.model.response.LivePlayUrlData
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio

internal data class LivePlaybackCandidate(
    val protocolName: String,
    val formatName: String,
    val codecName: String,
    val urls: List<String>,
    val currentQuality: Int,
    val qualityList: List<LiveQuality>
)

internal data class ResolvedLivePlayback(
    val requestedQuality: Int,
    val candidates: List<LivePlaybackCandidate>
) {
    val primaryUrl: String?
        get() = candidates.firstOrNull()?.urls?.firstOrNull()
    val currentQuality: Int
        get() = candidates.first().currentQuality
    val qualityList: List<LiveQuality>
        get() = candidates.first().qualityList
}

internal sealed interface LiveAdvanceResult {
    data class NextSource(
        val candidateIndex: Int,
        val urlIndex: Int,
        val playUrl: String
    ) : LiveAdvanceResult

    data class ReloadCurrentQuality(
        val qualityQn: Int
    ) : LiveAdvanceResult
}

internal enum class LivePlaybackErrorRecovery {
    SEEK_TO_LIVE_EDGE,
    TRY_NEXT_SOURCE,
    NONE
}

internal fun resolveLivePlaybackErrorRecovery(
    errorCode: Int,
    httpResponseCode: Int?
): LivePlaybackErrorRecovery {
    if (errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        return LivePlaybackErrorRecovery.SEEK_TO_LIVE_EDGE
    }
    if (httpResponseCode in setOf(403, 404, 412, 500, 502, 503, 504)) {
        return LivePlaybackErrorRecovery.TRY_NEXT_SOURCE
    }
    return if (errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            // 音频/视频解码器瞬时失败（硬件解码器被系统回收等）：切换源会重新 prepare，
            // 强制解码器重新初始化并走软件兜底，避免"画面正常、声音丢失"后一直哑着。
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )
    ) {
        LivePlaybackErrorRecovery.TRY_NEXT_SOURCE
    } else {
        LivePlaybackErrorRecovery.NONE
    }
}

internal fun shouldRecoverUnexpectedLiveEnd(
    playbackState: Int,
    playWhenReady: Boolean,
    isRoomLive: Boolean,
    isMiniLiveMode: Boolean
): Boolean {
    return playbackState == Player.STATE_ENDED &&
        playWhenReady &&
        isRoomLive &&
        !isMiniLiveMode
}

/** Maps the app's Wi-Fi/mobile video-quality preference to a live-room quality tier. */
internal fun resolveLiveDefaultQualityQn(videoQualityId: Int): Int = when {
    videoQualityId >= 80 -> 400
    videoQualityId >= 64 -> 250
    videoQualityId >= 32 -> 150
    else -> 80
}

internal fun resolveLivePlayback(
    data: LivePlayUrlData,
    requestedQn: Int
): ResolvedLivePlayback? {
    val qualities = (data.playurl_info?.playurl?.gQnDesc.orEmpty() + data.quality_description.orEmpty())
        .filter { it.qn > 0 }
        .distinctBy { it.qn }
    val candidates = data.playurl_info?.playurl?.stream
        .orEmpty()
        .flatMap { stream ->
            stream.format.orEmpty().flatMap { format ->
                format.codec.orEmpty().mapNotNull { codec ->
                    val urls = codec.url_info
                        .orEmpty()
                        .mapNotNull { urlInfo ->
                            val host = urlInfo.host.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val baseUrl = codec.baseUrl.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val extra = urlInfo.extra.orEmpty()
                            host + baseUrl + extra
                        }
                        .distinct()
                    if (urls.isEmpty()) {
                        null
                    } else {
                        LivePlaybackCandidate(
                            protocolName = stream.protocolName,
                            formatName = format.formatName,
                            codecName = codec.codecName,
                            urls = urls,
                            currentQuality = codec.currentQn.takeIf { it > 0 }
                                ?: data.current_quality.takeIf { it > 0 }
                                ?: requestedQn,
                            qualityList = resolveLiveQualityList(codec, qualities)
                        )
                    }
                }
            }
        }
        .sortedWith(
            compareBy<LivePlaybackCandidate> { streamProtocolPriority(it.protocolName) }
                .thenBy { streamFormatPriority(it.formatName) }
                .thenBy { streamCodecPriority(it.codecName) }
        )

    if (candidates.isEmpty()) {
        val urls = data.durl.orEmpty().map { it.url }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) return null
        return ResolvedLivePlayback(
            requestedQuality = requestedQn,
            candidates = listOf(
                LivePlaybackCandidate(
                    protocolName = "http_stream",
                    formatName = "flv",
                    codecName = "",
                    urls = urls,
                    currentQuality = data.current_quality.takeIf { it > 0 } ?: requestedQn,
                    qualityList = qualities
                )
            )
        )
    }


    return ResolvedLivePlayback(
        requestedQuality = requestedQn,
        candidates = candidates
    )
}

internal fun advanceLivePlayback(
    resolved: ResolvedLivePlayback,
    candidateIndex: Int,
    urlIndex: Int
): LiveAdvanceResult {
    val candidate = resolved.candidates.getOrNull(candidateIndex)
        ?: return LiveAdvanceResult.ReloadCurrentQuality(resolved.requestedQuality)

    val nextUrlIndex = urlIndex + 1
    val nextUrl = candidate.urls.getOrNull(nextUrlIndex)
    if (nextUrl != null) {
        return LiveAdvanceResult.NextSource(
            candidateIndex = candidateIndex,
            urlIndex = nextUrlIndex,
            playUrl = nextUrl
        )
    }

    val nextCandidateIndex = candidateIndex + 1
    val nextCandidate = resolved.candidates.getOrNull(nextCandidateIndex)
    if (nextCandidate != null) {
        return LiveAdvanceResult.NextSource(
            candidateIndex = nextCandidateIndex,
            urlIndex = 0,
            playUrl = nextCandidate.urls.first()
        )
    }

    return LiveAdvanceResult.ReloadCurrentQuality(resolved.requestedQuality)
}

private fun resolveLiveQualityList(
    codec: CodecInfo,
    qualities: List<LiveQuality>
): List<LiveQuality> {

    val acceptQn = codec.acceptQn.orEmpty().filter { it > 0 }
    if (acceptQn.isNotEmpty()) {
        return acceptQn.distinct().map { qn ->
            LiveQuality(
                qn = qn,
                desc = qualities.firstOrNull { it.qn == qn }?.desc?.takeIf { it.isNotBlank() }
                    ?: livePreferredQualityOptions.firstOrNull { it.first == qn }?.second
                    ?: qn.toString()
            )
        }
    }

    return qualities
}

private fun streamProtocolPriority(protocolName: String): Int {
    return when (protocolName) {
        "http_hls" -> 0
        "http_stream" -> 1
        else -> 2
    }
}

private fun streamFormatPriority(formatName: String): Int {
    return when (formatName) {
        "fmp4" -> 0
        "ts" -> 1
        "flv" -> 2
        else -> 3
    }
}

private fun streamCodecPriority(codecName: String): Int {
    return when (codecName) {
        "avc" -> 0
        "hevc" -> 1
        else -> 2
    }
}

/** 只记忆协议、格式与编码；房间地址和签名不进入设置。 */
internal fun resolveLivePreferredCandidateIndex(candidates: List<LivePlaybackCandidate>, preference: String?): Int {
    val names = preference?.split('|') ?: return 0
    if (names.size != 3) return 0
    return candidates.indexOfFirst {
        it.protocolName == names[0] && it.formatName == names[1] && it.codecName == names[2]
    }.coerceAtLeast(0)
}

/** 自动小窗只接管仍有播放意图的有效直播，退出即停开关优先。 */
internal fun shouldAutoEnterLiveMiniPlayer(
    enabled: Boolean,
    isActive: Boolean,
    isMatchingLiveRoom: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    stopPlaybackOnExit: Boolean,
): Boolean = enabled && isActive && isMatchingLiveRoom && playWhenReady && !stopPlaybackOnExit &&
    (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
