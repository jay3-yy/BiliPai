package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.repository.StoryRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.story.storyItemToRelatedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PortraitRecommendationBatchRequest(
    val currentBvid: String,
    val currentAid: Long,
    val startingStoryDisplayId: Int,
    val startingHomeFeedCursor: Int,
    val onlyVertical: Boolean,
    val minVerticalRatio: Float,
    val recommendationShuffleSeed: Int,
    val existingBvids: Set<String>,
    val existingRecommendations: List<RelatedVideo>,
    val precedingOwnerMid: Long,
    val targetBatchSize: Int = 8,
)

internal data class PortraitRecommendationBatchResult(
    val appendItems: List<RelatedVideo>,
    val nextStoryDisplayId: Int,
    val nextHomeFeedCursor: Int,
)

/**
 * 统一详情页竖屏流（非外部 Story 会话托管）的续播推荐拉取管线：
 * 优先 Story 短视频流，不足时依次回退首页推荐与相关推荐，
 * 在后台线程完成竖屏过滤、洗牌与相似度去重后返回可直接追加的结果。
 */
internal suspend fun fetchPortraitRecommendationAppendBatch(
    request: PortraitRecommendationBatchRequest,
    fetchStoryPage: suspend (aid: Long, bvid: String, displayId: Int) -> List<RelatedVideo> = { aid, bvid, displayId ->
        StoryRepository.getStoryFeed(aid = aid, bvid = bvid, displayId = displayId)
            .getOrNull()
            .orEmpty()
            .mapNotNull(::storyItemToRelatedVideo)
    },
    fetchHomePage: suspend (cursor: Int) -> List<RelatedVideo> = { cursor ->
        VideoRepository.getHomeVideos(idx = cursor)
            .getOrNull()
            .orEmpty()
            .mapNotNull(::toRelatedVideoForPortraitRecommendation)
    },
    fetchRelated: suspend (bvid: String) -> List<RelatedVideo> = { bvid ->
        VideoRepository.getRelatedVideos(bvid)
    },
    filterVertical: suspend (List<RelatedVideo>, Boolean, Float) -> List<RelatedVideo> = { items, enabled, ratio ->
        filterPortraitOnlyVerticalRecommendations(
            recommendations = items,
            enabled = enabled,
            minVerticalRatio = ratio,
        )
    },
): PortraitRecommendationBatchResult {
    val verifiedRecommendations = mutableListOf<RelatedVideo>()
    var nextStoryDisplayId = request.startingStoryDisplayId
    var nextHomeFeedCursor = request.startingHomeFeedCursor
    val fetchAttemptLimit = resolvePortraitRecommendationFetchAttemptLimit(
        onlyVerticalRecommendations = request.onlyVertical,
    )

    var storyAttempts = 0
    while (storyAttempts < fetchAttemptLimit && verifiedRecommendations.size < request.targetBatchSize) {
        val storyPage = fetchStoryPage(request.currentAid, request.currentBvid, nextStoryDisplayId)
        nextStoryDisplayId += 1
        storyAttempts += 1
        if (storyPage.isEmpty()) break
        verifiedRecommendations += filterVertical(storyPage, request.onlyVertical, request.minVerticalRatio)
    }

    var homeAttempts = 0
    while (homeAttempts < fetchAttemptLimit && verifiedRecommendations.size < request.targetBatchSize) {
        val homePage = fetchHomePage(nextHomeFeedCursor)
        nextHomeFeedCursor += 1
        homeAttempts += 1
        if (homePage.isEmpty()) break
        verifiedRecommendations += filterVertical(homePage, request.onlyVertical, request.minVerticalRatio)
    }

    val relatedFallback = if (verifiedRecommendations.size < request.targetBatchSize) {
        filterVertical(fetchRelated(request.currentBvid), request.onlyVertical, request.minVerticalRatio)
    } else {
        emptyList()
    }

    val candidates = verifiedRecommendations + relatedFallback
    val appendItems = withContext(Dispatchers.Default) {
        val shuffled = shufflePortraitRecommendations(
            seed = resolvePortraitRecommendationAppendSeed(
                baseSeed = request.recommendationShuffleSeed,
                currentBvid = request.currentBvid,
            ),
            recommendations = candidates,
            precedingOwnerMid = request.precedingOwnerMid,
        )
        mergePortraitRecommendationAppendItems(
            currentBvid = request.currentBvid,
            existingBvids = request.existingBvids,
            existingRecommendations = request.existingRecommendations,
            fetchedRecommendations = shuffled,
        )
    }

    return PortraitRecommendationBatchResult(
        appendItems = appendItems,
        nextStoryDisplayId = nextStoryDisplayId,
        nextHomeFeedCursor = nextHomeFeedCursor,
    )
}
