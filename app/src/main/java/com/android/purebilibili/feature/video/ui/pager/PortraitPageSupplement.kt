package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.purebilibili.data.model.response.PlayerInfoData
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.video.progress.PbpProgressData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 字幕目录与章节共用一份信息；隐藏取消未完成请求，已知空目录也可复用。 */
@Composable
internal fun rememberPortraitPagePlayerInfo(
    bvid: String,
    cid: Long,
    active: Boolean,
    hasSharedState: Boolean,
    load: suspend (String, Long) -> PlayerInfoData? = { video, part ->
        VideoRepository.getPlayerInfo(video, part).getOrNull()
    },
): State<PlayerInfoData?> = key(bvid, cid) {
    produceState<PlayerInfoData?>(null, active, hasSharedState) {
        if (!active || hasSharedState || cid <= 0L || value != null) return@produceState
        val loaded = load(bvid, cid)
        currentCoroutineContext().ensureActive()
        value = loaded
    }
}

/** 热度关闭或页面离屏时不请求；同一内容的官方曲线和弹幕密度结果分别复用。 */
@Composable
internal fun rememberPortraitPageHeatmap(
    bvid: String,
    cid: Long,
    aid: Long,
    durationMs: Long,
    enabled: Boolean,
    active: Boolean,
    hasSharedState: Boolean,
    loadOfficial: suspend (String, Long, Long) -> PbpProgressData? = { video, part, archive ->
        VideoRepository.getPbpProgressData(video, part, archive).getOrNull()
    },
    loadDensity: suspend (Long, Long) -> PbpProgressData? = { part, seconds ->
        VideoRepository.getDanmakuDensityProgressData(part, seconds).getOrNull()
    },
): State<PbpProgressData?> = key(bvid, cid, aid) {
    var official by remember { mutableStateOf<PbpProgressData?>(null) }
    var density by remember { mutableStateOf<PbpProgressData?>(null) }
    produceState<PbpProgressData?>(null, active, hasSharedState, enabled, durationMs) {
        if (!active || hasSharedState || !enabled || cid <= 0L) return@produceState
        if (official == null) {
            val loaded = loadOfficial(bvid, cid, aid)
            currentCoroutineContext().ensureActive()
            official = loaded
        }
        value = official
        if (official?.values.isNullOrEmpty() && durationMs > 0L) {
            if (density == null) {
                val loaded = loadDensity(cid, durationMs / 1000L)
                currentCoroutineContext().ensureActive()
                density = loaded
            }
            value = density ?: official
        }
    }
}
