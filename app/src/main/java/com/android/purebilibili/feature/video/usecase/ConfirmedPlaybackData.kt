package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.PlayerInfoData
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class PlaybackDataLoadStage {
    // 当前分 P 的播放数据已选定，不含观看进度等待；不是原始 HTTP 响应时刻。
    PLAY_URL_READY,
    HISTORY_REQUEST_STARTED,
    // 读取结束包含返回未知进度的失败结果，不等同于成功获得进度。
    HISTORY_REQUEST_FINISHED,
}

internal data class ConfirmedPlaybackData(
    val info: ViewInfo,
    val playData: PlayUrlData,
    val history: PlayerInfoData?,
)

/** 详情已确认当前分 P；观看进度与该分 P 的取流并行，起播前仍等待进度确认。 */
internal suspend fun loadConfirmedPlaybackData(
    info: ViewInfo,
    loadPlayData: suspend () -> PlayUrlData?,
    loadHistory: suspend (ViewInfo) -> PlayerInfoData?,
    onStage: (PlaybackDataLoadStage) -> Unit = {},
): Result<ConfirmedPlaybackData> = coroutineScope {
    val historyDeferred = async {
        onStage(PlaybackDataLoadStage.HISTORY_REQUEST_STARTED)
        loadHistory(info).also {
            onStage(PlaybackDataLoadStage.HISTORY_REQUEST_FINISHED)
        }
    }
    val playData = loadPlayData()
    if (playData == null) {
        historyDeferred.cancel()
        Result.failure(Exception("无法获取播放地址"))
    } else {
        onStage(PlaybackDataLoadStage.PLAY_URL_READY)
        Result.success(ConfirmedPlaybackData(info, playData, historyDeferred.await()))
    }
}
