package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.RecommendationGroup
import com.android.purebilibili.plugin.sdk.RecommendationMode
import com.android.purebilibili.plugin.sdk.RecommendationPluginApi
import com.android.purebilibili.plugin.sdk.RecommendationRequest
import com.android.purebilibili.plugin.sdk.RecommendationResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只消费本轮不可变输入；评分、排序和结果映射不占用首页主线程。 */
internal suspend fun buildTodayWatchRecommendation(
    plugin: RecommendationPluginApi<VideoItem>,
    request: RecommendationRequest<VideoItem>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): TodayWatchPlan = withContext(dispatcher) {
    plugin.buildRecommendations(request).toTodayWatchPlan()
}

private fun RecommendationMode.toTodayWatchMode(): TodayWatchMode {
    return when (this) {
        RecommendationMode.RELAX -> TodayWatchMode.RELAX
        RecommendationMode.LEARN -> TodayWatchMode.LEARN
    }
}

private fun RecommendationResult<VideoItem>.toTodayWatchPlan(): TodayWatchPlan {
    val creatorGroup = groups.firstOrNull { it.id == "preferred_creators" }
    return TodayWatchPlan(
        mode = mode.toTodayWatchMode(),
        upRanks = creatorGroup.toTodayUpRanks().toImmutableList(),
        videoQueue = items.map { it.video }.toImmutableList(),
        explanationByBvid = items.associate { it.video.bvid to it.explanation }.toImmutableMap(),
        scoreByBvid = items.associate { it.video.bvid to it.score }.toImmutableMap(),
        confidenceByBvid = items.associate { it.video.bvid to it.confidence }.toImmutableMap(),
        historySampleCount = historySampleCount,
        nightSignalUsed = sceneSignals.eyeCareNightActive,
        generatedAt = generatedAt
    )
}

private fun RecommendationGroup?.toTodayUpRanks(): List<TodayUpRank> {
    return this?.items.orEmpty().mapNotNull { item ->
        val mid = item.id.toLongOrNull() ?: return@mapNotNull null
        TodayUpRank(
            mid = mid,
            name = item.title,
            score = item.score ?: 0.0,
            watchCount = item.watchCount ?: 1
        )
    }
}
