package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.plugin.TodayWatchPlugin
import com.android.purebilibili.plugin.sdk.RecommendationFeedbackSignals
import com.android.purebilibili.plugin.sdk.RecommendationMode
import com.android.purebilibili.plugin.sdk.RecommendationPluginApi
import com.android.purebilibili.plugin.sdk.RecommendationRequest
import com.android.purebilibili.plugin.sdk.RecommendationResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TodayWatchRecommendationBuilderTest {
    @Test
    fun scoringRunsOutsideTheCallerAndKeepsFeedbackAndPublishedPlanFields() = runTest {
        val actualPlugin = TodayWatchPlugin()
        val caller = Thread.currentThread().id
        var calculationThread = caller
        val plugin = object : RecommendationPluginApi<VideoItem> by actualPlugin {
            override fun buildRecommendations(request: RecommendationRequest<VideoItem>): RecommendationResult<VideoItem> {
                calculationThread = Thread.currentThread().id
                return actualPlugin.buildRecommendations(request)
            }
        }
        val candidates = listOf(
            VideoItem(bvid = "BV_ALLOWED", title = "轻松短视频", duration = 420, owner = Owner(mid = 1, name = "UP")),
            VideoItem(bvid = "BV_DISLIKED", title = "不感兴趣", duration = 420, owner = Owner(mid = 2, name = "Other")),
        )
        val request = RecommendationRequest(
            candidateVideos = candidates,
            historyVideos = emptyList(),
            feedbackSignals = RecommendationFeedbackSignals(dislikedBvids = setOf("BV_DISLIKED")),
            mode = RecommendationMode.RELAX,
            queueLimit = 6,
            groupLimit = 5,
        )
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val plan = buildTodayWatchRecommendation(plugin, request, worker)
            assertNotEquals(caller, calculationThread)
            assertEquals(TodayWatchMode.RELAX, plan.mode)
            assertTrue(plan.videoQueue.any { it.bvid == "BV_ALLOWED" })
            assertFalse(plan.videoQueue.any { it.bvid == "BV_DISLIKED" })
            assertEquals(plan.videoQueue.map { it.bvid }.toSet(), plan.scoreByBvid.keys)
            assertEquals(plan.videoQueue.map { it.bvid }.toSet(), plan.explanationByBvid.keys)
            assertTrue(plan.generatedAt > 0L)
        }
    }
}
