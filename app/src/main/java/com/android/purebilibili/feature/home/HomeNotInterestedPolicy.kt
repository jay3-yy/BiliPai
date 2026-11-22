package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.RecommendationFeedbackLocalAction
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.RecommendationFeedbackType

internal data class HomeNotInterestedAction(
    val bvid: String,
    val shouldBlockCreator: Boolean,
    val shouldSyncCreatorToBilibiliBlockedList: Boolean,
    val creatorMid: Long,
    val creatorName: String,
    val creatorFace: String,
    val keywords: Set<String>
)

internal data class HomeNotInterestedVisualTransition(
    val shouldStartDissolve: Boolean,
    val shouldRemoveImmediately: Boolean
)

internal fun resolveHomeNotInterestedVisualTransition(
    isFeedbackRecorded: Boolean,
    isDissolveAnimationAvailable: Boolean
): HomeNotInterestedVisualTransition {
    return HomeNotInterestedVisualTransition(
        shouldStartDissolve = isFeedbackRecorded && isDissolveAnimationAvailable,
        shouldRemoveImmediately = isFeedbackRecorded && !isDissolveAnimationAvailable
    )
}

internal fun resolveHomeNotInterestedAction(
    video: VideoItem,
    reason: RecommendationFeedbackReason
): HomeNotInterestedAction {
    val effectiveReason = reason.withInferredLocalActionIfNeeded()
    val creatorMid = video.owner.mid
    val shouldBlockCreator = effectiveReason.localAction == RecommendationFeedbackLocalAction.CREATOR &&
        creatorMid > 0L
    return HomeNotInterestedAction(
        bvid = video.bvid,
        shouldBlockCreator = shouldBlockCreator,
        shouldSyncCreatorToBilibiliBlockedList = shouldBlockCreator,
        creatorMid = creatorMid,
        creatorName = video.owner.name.ifBlank {
            if (creatorMid > 0L) "UP主$creatorMid" else ""
        },
        creatorFace = video.owner.face,
        keywords = resolveHomeNotInterestedKeywords(video, effectiveReason)
    )
}

/**
 * 已落盘的 reason 可能仍是 VIDEO_ONLY（旧映射/未知 id）。
 * 在真正写反馈前再按文案推断一次，避免分区/UP 主选择失效。
 */
private fun RecommendationFeedbackReason.withInferredLocalActionIfNeeded(): RecommendationFeedbackReason {
    if (type == RecommendationFeedbackType.FEEDBACK) return this
    if (localAction != RecommendationFeedbackLocalAction.VIDEO_ONLY) return this
    val inferred = com.android.purebilibili.data.model.response
        .inferRecommendationFeedbackLocalActionFromName(name)
    return if (inferred == RecommendationFeedbackLocalAction.VIDEO_ONLY) {
        this
    } else {
        copy(localAction = inferred)
    }
}

internal fun resolveDefaultHomeNotInterestedReason(): RecommendationFeedbackReason {
    return RecommendationFeedbackReason(
        name = "这个内容",
        localAction = RecommendationFeedbackLocalAction.VIDEO_ONLY,
    )
}

internal fun resolveHomeNotInterestedReasons(video: VideoItem): List<RecommendationFeedbackReason> {
    val remoteReasons = video.recommendationFeedback?.reasons.orEmpty()
        .filter { it.name.isNotBlank() }
        .distinctBy { it.type to it.id }
    if (remoteReasons.isNotEmpty()) return remoteReasons

    return buildList {
        add(
            RecommendationFeedbackReason(
                name = "这个内容",
                localAction = RecommendationFeedbackLocalAction.VIDEO_ONLY
            )
        )
        if (video.owner.mid > 0L) {
            add(
                RecommendationFeedbackReason(
                    name = "UP主：${video.owner.name.ifBlank { video.owner.mid.toString() }}",
                    localAction = RecommendationFeedbackLocalAction.CREATOR
                )
            )
        }
        if (video.tname.isNotBlank()) {
            add(
                RecommendationFeedbackReason(
                    name = "分区：${video.tname}",
                    localAction = RecommendationFeedbackLocalAction.CATEGORY
                )
            )
        }
        add(
            RecommendationFeedbackReason(
                name = "此类内容过多",
                localAction = RecommendationFeedbackLocalAction.SIMILAR_CONTENT
            )
        )
        add(
            RecommendationFeedbackReason(
                name = "推荐过",
                localAction = RecommendationFeedbackLocalAction.VIDEO_ONLY
            )
        )
    }
}

private fun resolveHomeNotInterestedKeywords(
    video: VideoItem,
    reason: RecommendationFeedbackReason
): Set<String> {
    return when (reason.localAction) {
        RecommendationFeedbackLocalAction.CATEGORY -> {
            val reasonKeyword = reason.name
                .substringAfter(':')
                .substringAfter('：')
                .trim()
            setOf(reasonKeyword, video.tname.trim())
                .filter { it.isNotBlank() }
                .toSet()
        }
        RecommendationFeedbackLocalAction.SIMILAR_CONTENT -> extractHomeFeedbackKeywords(video.title)
        RecommendationFeedbackLocalAction.VIDEO_ONLY,
        RecommendationFeedbackLocalAction.CREATOR -> emptySet()
    }
}

private val HOME_FEEDBACK_STOP_WORDS = setOf("视频", "合集", "最新", "一个", "我们", "你们", "今天", "真的", "这个")
private val HOME_FEEDBACK_ZH_TOKEN_REGEX = Regex("[\\u4e00-\\u9fa5]{2,6}")
private val HOME_FEEDBACK_EN_TOKEN_REGEX = Regex("[a-z0-9]{3,}")

internal fun extractHomeFeedbackKeywords(title: String): Set<String> {
    if (title.isBlank()) return emptySet()
    val normalized = title.lowercase()
    val zhTokens = HOME_FEEDBACK_ZH_TOKEN_REGEX
        .findAll(normalized)
        .map { it.value }
        .filter { it !in HOME_FEEDBACK_STOP_WORDS }
        .take(6)
        .toList()
    val enTokens = HOME_FEEDBACK_EN_TOKEN_REGEX
        .findAll(normalized)
        .map { it.value }
        .take(4)
        .toList()
    return (zhTokens + enTokens).toSet()
}

internal fun filterHomeVideosByNotInterestedFeedback(
    videos: List<VideoItem>,
    dislikedBvids: Set<String> = emptySet(),
    dislikedCreatorMids: Set<Long> = emptySet(),
    dislikedKeywords: Set<String> = emptySet()
): List<VideoItem> {
    if (videos.isEmpty()) return videos
    val normalizedBvids = dislikedBvids
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val normalizedCreators = dislikedCreatorMids.filter { it > 0L }.toSet()
    val normalizedKeywords = dislikedKeywords
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 }
        .toSet()

    if (normalizedBvids.isEmpty() && normalizedCreators.isEmpty() && normalizedKeywords.isEmpty()) {
        return videos
    }

    return videos.filter { video ->
        val bvid = video.bvid.trim()
        (bvid.isBlank() || bvid !in normalizedBvids) &&
            video.owner.mid !in normalizedCreators &&
            !matchesDislikedPartition(video.tname, normalizedKeywords) &&
            !shouldFilterByDislikedKeywords(video.title, normalizedKeywords)
    }
}

/**
 * 分区名反馈必须按 tname 精确/包含匹配。
 * 中文分区名常见 2–3 字（游戏、知识、动物圈），不能套用标题短词防误伤规则。
 */
private fun matchesDislikedPartition(
    tname: String,
    dislikedKeywords: Set<String>
): Boolean {
    val partition = tname.trim().lowercase()
    if (partition.isBlank() || dislikedKeywords.isEmpty()) return false
    return dislikedKeywords.any { keyword ->
        partition == keyword ||
            (keyword.length >= 2 && partition.contains(keyword)) ||
            (partition.length >= 2 && keyword.contains(partition))
    }
}

private fun shouldFilterByDislikedKeywords(
    title: String,
    dislikedKeywords: Set<String>
): Boolean {
    if (title.isBlank() || dislikedKeywords.isEmpty()) return false
    val normalizedTitle = title.lowercase()
    val hitKeywords = dislikedKeywords.filter { keyword ->
        normalizedTitle.contains(keyword)
    }
    if (hitKeywords.isEmpty()) return false

    // 标题侧：单个短词容易误伤（如“日常”）；长短语或多个命中才认为是同类型内容。
    return hitKeywords.any { it.length >= 4 } || hitKeywords.size >= 2
}
