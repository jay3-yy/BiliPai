package com.android.purebilibili.feature.video.viewmodel

import androidx.compose.runtime.Immutable
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.usecase.resolveVideoLoadDurationMs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class VideoDetailUiState(
    val requestBvid: String = "",
    val requestCid: Long = 0L,
    val requestToken: Long = 0L,
    val info: ViewInfo? = null,
    val related: ImmutableList<RelatedVideo> = persistentListOf(),
    val relatedLoadState: VideoPlaybackUiState.RelatedLoadState = VideoPlaybackUiState.RelatedLoadState.LOADING,
    val isLoggedIn: Boolean = false,
    val isVip: Boolean = false
) {
    /** 同一稿件保留正文；已知分 P 复用详情，只更新选中的 CID。 */
    fun forNewRequest(
        bvid: String,
        cid: Long,
        token: Long,
        playbackState: VideoPlaybackUiState
    ): VideoDetailUiState {
        val content = contentSnapshot(playbackState, bvid, cid)
        return VideoDetailUiState(
            requestBvid = bvid,
            requestCid = cid,
            requestToken = token,
            info = content?.info,
            related = content?.related?.toImmutableList() ?: persistentListOf(),
            relatedLoadState = content?.relatedLoadState ?: VideoPlaybackUiState.RelatedLoadState.LOADING,
            isLoggedIn = content?.isLoggedIn ?: false,
            isVip = content?.isVip ?: false
        )
    }

    fun contentSnapshot(
        playbackState: VideoPlaybackUiState,
        bvid: String,
        cid: Long
    ): VideoPlaybackUiState.Success? {
        val playback = (playbackState as? VideoPlaybackUiState.Success)?.takeIf {
            val matchesVideo = it.info.bvid == bvid ||
                (requestBvid == bvid && info?.bvid == it.info.bvid)
            matchesVideo && (cid <= 0L || it.info.cid == cid)
        }
        if (playback != null) return playback
        if (requestBvid != bvid) return null
        val detail = info ?: return null
        // 简介、评论及选集属于稿件。切换到详情已包含的分 P 时，即使播放投影
        // 尚未到达，也保留正文并选中目标 CID；字幕、AI 等分 P 数据不沿用旧快照。
        val selectedDetail = if (cid > 0L && detail.pages.any { it.cid == cid }) {
            detail.copy(cid = cid)
        } else {
            // 未指定分 P 的请求解析出 CID 后仍属于同一次详情。
            if (cid > 0L && requestCid != cid && detail.cid != cid) return null
            detail
        }
        return VideoPlaybackUiState.Success(
            info = selectedDetail,
            playUrl = "",
            related = related,
            relatedLoadState = relatedLoadState,
            isLoggedIn = isLoggedIn,
            isVip = isVip,
            videoDurationMs = resolveVideoLoadDurationMs(0L, selectedDetail)
        )
    }
}
