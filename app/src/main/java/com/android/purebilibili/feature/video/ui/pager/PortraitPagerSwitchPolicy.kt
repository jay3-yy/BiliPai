package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random
import kotlin.math.abs

private const val PORTRAIT_RECOMMENDATION_PREFETCH_THRESHOLD = 1
private const val PORTRAIT_VERTICAL_RECOMMENDATION_PREFETCH_THRESHOLD = 4
private const val PORTRAIT_VERTICAL_FILTER_CONCURRENCY = 8
private const val PORTRAIT_VERTICAL_RECOMMENDATION_FETCH_ATTEMPTS = 3
private const val PORTRAIT_RECENT_DIVERSITY_WINDOW_SIZE = 12
private const val PORTRAIT_MAX_RECENT_ITEMS_PER_OWNER = 2
private val PORTRAIT_RECOMMENDATION_STOP_WORDS = setOf(
    "视频", "合集", "最新", "一个", "我们", "你们", "今天", "真的", "这个",
    "竖屏", "横屏", "官方", "完整版", "全集", "合集版"
)

private data class PortraitRecommendationSignature(
    val normalizedTitle: String,
    val coverKey: String,
    val titleKeywords: Set<String>,
    val ownerMid: Long,
    val duration: Int
)

internal fun resolveCommittedPage(
    isScrollInProgress: Boolean,
    currentPage: Int,
    lastCommittedPage: Int
): Int? {
    if (isScrollInProgress) return null
    if (currentPage == lastCommittedPage) return null
    return currentPage
}

internal fun shouldApplyLoadResult(
    requestGeneration: Int,
    activeGeneration: Int,
    expectedBvid: String,
    currentPlayingBvid: String?
): Boolean {
    if (requestGeneration != activeGeneration) return false
    if (expectedBvid != currentPlayingBvid) return false
    return true
}

internal fun shouldSkipPortraitReloadForCurrentMedia(
    currentPlayingBvid: String?,
    targetBvid: String,
    currentPlayerMediaId: String?,
    targetCid: Long = 0L,
    currentPlayingCid: Long = 0L
): Boolean {
    val normalizedMediaId = currentPlayerMediaId?.trim().orEmpty()
    if (normalizedMediaId.isBlank()) return false
    if (currentPlayingBvid != targetBvid) return false
    // Multi-P shares bvid; media id encodes cid so part switches still reload.
    val expectedMediaId = resolvePortraitMediaId(targetBvid, targetCid)
    if (normalizedMediaId == expectedMediaId) return true
    // Legacy media ids were plain bvid (cid-less). Only skip when cid is also unchanged.
    if (normalizedMediaId == targetBvid) {
        return (targetCid > 0L && targetCid == currentPlayingCid) ||
            (targetCid <= 0L && currentPlayingCid <= 0L)
    }
    return false
}

internal fun resolvePortraitMediaId(bvid: String, cid: Long = 0L): String {
    val normalized = bvid.trim()
    return if (cid > 0L) "$normalized#$cid" else normalized
}

internal fun shouldShowPortraitCover(
    isLoading: Boolean,
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasRenderedSurfaceFrame: Boolean,
): Boolean {
    if (!isCurrentPage) return true
    // 首帧出现后不因缓冲重新盖回封面，避免播放中画面跳变。
    if (isLoading && !hasRenderedFirstFrame) return true
    return !isPlayerReadyForThisVideo || !hasRenderedFirstFrame || !hasRenderedSurfaceFrame
}

internal fun shouldShowPortraitPauseIcon(
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    isLoading: Boolean,
    isSeekGesture: Boolean
): Boolean {
    if (!isCurrentPage) return false
    if (isLoading) return false
    if (isSeekGesture) return false
    if (isPlaying) return false
    if (playWhenReady) return false
    return true
}

internal fun shouldHandlePortraitSeekGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldHandlePortraitTapGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldEnablePortraitPagerUserScroll(
    scale: Float,
    commentOverlayActive: Boolean,
    upPreviewActive: Boolean
): Boolean {
    return shouldHandlePortraitTapGesture(scale = scale) &&
        !commentOverlayActive &&
        !upPreviewActive
}

internal fun shouldBlockPortraitPagerScrollForCommentOverlay(
    commentSheetVisible: Boolean,
    subReplyVisible: Boolean,
    commentVisibilityProgress: Float,
    progressEpsilon: Float = 0.001f,
    detailSheetVisible: Boolean = false,
): Boolean {
    return detailSheetVisible || commentSheetVisible ||
        subReplyVisible ||
        commentVisibilityProgress > progressEpsilon
}

internal fun shouldHandlePortraitLongPressGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldRestorePortraitLongPressSpeed(
    isLongPressing: Boolean,
    isCurrentPage: Boolean
): Boolean {
    return isLongPressing && !isCurrentPage
}

internal fun resolvePortraitInitialProgressPosition(
    isFirstPage: Boolean,
    initialStartPositionMs: Long
): Long {
    if (!isFirstPage) return 0L
    return initialStartPositionMs.coerceAtLeast(0L)
}

internal fun shouldLoadMorePortraitRecommendations(
    committedPage: Int,
    totalItemsCount: Int,
    isLoadingMoreRecommendations: Boolean,
    prefetchThreshold: Int = PORTRAIT_RECOMMENDATION_PREFETCH_THRESHOLD
): Boolean {
    if (isLoadingMoreRecommendations) return false
    if (committedPage < 0 || totalItemsCount <= 0) return false
    val lastTriggerIndex = (totalItemsCount - 1 - prefetchThreshold).coerceAtLeast(0)
    return committedPage >= lastTriggerIndex
}

internal fun resolvePortraitRecommendationPrefetchThreshold(
    onlyVerticalRecommendations: Boolean,
): Int = if (onlyVerticalRecommendations) {
    PORTRAIT_VERTICAL_RECOMMENDATION_PREFETCH_THRESHOLD
} else {
    PORTRAIT_RECOMMENDATION_PREFETCH_THRESHOLD
}

internal fun resolvePortraitRecommendationFetchAttemptLimit(
    onlyVerticalRecommendations: Boolean,
): Int = if (onlyVerticalRecommendations) {
    PORTRAIT_VERTICAL_RECOMMENDATION_FETCH_ATTEMPTS
} else {
    1
}

internal fun mergePortraitRecommendationAppendItems(
    currentBvid: String,
    existingBvids: Set<String>,
    existingRecommendations: List<RelatedVideo>,
    fetchedRecommendations: List<RelatedVideo>
): List<RelatedVideo> {
    val accepted = existingRecommendations
        .filter { it.bvid.isNotBlank() }
        .toMutableList()

    val signatureCache = HashMap<String, PortraitRecommendationSignature>()
    fun signatureOf(video: RelatedVideo): PortraitRecommendationSignature {
        return signatureCache.getOrPut(video.bvid) { buildPortraitRecommendationSignature(video) }
    }
    fun isSimilar(left: RelatedVideo, right: RelatedVideo): Boolean {
        if (left.bvid == right.bvid) return true
        return arePortraitRecommendationSignaturesSimilar(
            firstSignature = signatureOf(left),
            secondSignature = signatureOf(right),
        )
    }

    return fetchedRecommendations.fold(mutableListOf<RelatedVideo>()) { appended, candidate ->
        val canAppend = candidate.bvid.isNotBlank() &&
            candidate.bvid != currentBvid &&
            candidate.bvid !in existingBvids &&
            appended.none { it.bvid == candidate.bvid } &&
            accepted.none { existing -> isSimilar(existing, candidate) } &&
            !violatesPortraitRecentOwnerDiversity(
                acceptedRecommendations = accepted,
                candidate = candidate
            )

        if (canAppend) {
            appended += candidate
            accepted += candidate
        }
        appended
    }
}

/**
 * Append parent-owned recommendation updates (e.g. Story feed load-more) without reshuffling
 * or recreating the pager list. Preserves the user's current page.
 */
internal fun resolvePortraitExternalRecommendationAppendItems(
    currentInitialBvid: String,
    existingBvids: Set<String>,
    externalRecommendations: List<RelatedVideo>
): List<RelatedVideo> {
    val seen = existingBvids.toMutableSet()
    val append = mutableListOf<RelatedVideo>()
    externalRecommendations.forEach { candidate ->
        val bvid = candidate.bvid.trim()
        if (bvid.isEmpty() || bvid == currentInitialBvid || bvid in seen) return@forEach
        append += candidate
        seen += bvid
    }
    return append
}

internal fun resolvePortraitRecommendationShuffleSeed(
    initialBvid: String,
    initialAid: Long
): Int {
    var seed = 17
    seed = 31 * seed + initialBvid.hashCode()
    seed = 31 * seed + initialAid.hashCode()
    return seed
}

internal fun resolvePortraitRecommendationAppendSeed(
    baseSeed: Int,
    currentBvid: String
): Int {
    return 31 * baseSeed + currentBvid.hashCode()
}

internal fun shufflePortraitRecommendations(
    seed: Int,
    recommendations: List<RelatedVideo>,
    precedingOwnerMid: Long = 0L
): List<RelatedVideo> {
    val shuffled = recommendations
        .filter { it.bvid.isNotBlank() }
        .distinctBy { it.bvid }
        .shuffled(Random(seed))

    // Pairwise title matching is quadratic; build each signature once per list pass.
    val signatureCache = HashMap<String, PortraitRecommendationSignature>(shuffled.size)
    fun signatureOf(video: RelatedVideo): PortraitRecommendationSignature {
        return signatureCache.getOrPut(video.bvid) { buildPortraitRecommendationSignature(video) }
    }
    fun isSimilar(left: RelatedVideo, right: RelatedVideo): Boolean {
        if (left.bvid == right.bvid) return true
        return arePortraitRecommendationSignaturesSimilar(
            firstSignature = signatureOf(left),
            secondSignature = signatureOf(right),
        )
    }

    val deduplicated = mutableListOf<RelatedVideo>()
    shuffled.forEach { candidate ->
        if (deduplicated.none { existing -> isSimilar(existing, candidate) }) {
            deduplicated += candidate
        }
    }
    if (deduplicated.size <= 1) return deduplicated

    val remaining = deduplicated.toMutableList()
    val arranged = mutableListOf<RelatedVideo>()
    while (remaining.isNotEmpty()) {
        val last = arranged.lastOrNull()
        val previousOwnerMid = last?.owner?.mid?.takeIf { it > 0L }
            ?: precedingOwnerMid.takeIf { arranged.isEmpty() && it > 0L }
        val candidateIndex = remaining.indexOfFirst { candidate ->
            val candidateOwnerMid = candidate.owner.mid
            (last == null || !isSimilar(last, candidate)) &&
                (previousOwnerMid == null || candidateOwnerMid <= 0L || candidateOwnerMid != previousOwnerMid)
        }.takeIf { it >= 0 }
            ?: remaining.indexOfFirst { candidate ->
                last == null || !isSimilar(last, candidate)
            }.takeIf { it >= 0 }
            ?: 0

        arranged += remaining.removeAt(candidateIndex)
    }
    return arranged
}

/** Filters a portrait feed to videos whose actual media dimensions are portrait. */
internal suspend fun filterPortraitOnlyVerticalRecommendations(
    recommendations: List<RelatedVideo>,
    enabled: Boolean,
    minVerticalRatio: Float = 1.0f,
): List<RelatedVideo> {
    if (!enabled || recommendations.isEmpty()) return recommendations
    val strictRatio = minVerticalRatio.coerceIn(0.8f, 1.5f) > 1.0f
    // Detail lookup is required because lightweight recommendation cards do not consistently
    // carry dimensions. Bound concurrency so filtering finishes before insertion without
    // turning a feed page into an unbounded request burst.
    return recommendations.chunked(PORTRAIT_VERTICAL_FILTER_CONCURRENCY).flatMap { batch ->
        coroutineScope {
            batch.map { candidate ->
                async {
                    val trustPositiveFlag = !strictRatio
                    when {
                        candidate.isVertical == false -> null
                        candidate.isVertical == true && trustPositiveFlag -> candidate
                        else -> candidate.takeIf {
                            VideoRepository.isVerticalVideo(
                                bvid = candidate.bvid,
                                aid = candidate.aid,
                                minVerticalRatio = minVerticalRatio,
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}

private fun violatesPortraitRecentOwnerDiversity(
    acceptedRecommendations: List<RelatedVideo>,
    candidate: RelatedVideo,
    recentWindowSize: Int = PORTRAIT_RECENT_DIVERSITY_WINDOW_SIZE,
    maxRecentItemsPerOwner: Int = PORTRAIT_MAX_RECENT_ITEMS_PER_OWNER
): Boolean {
    val ownerMid = candidate.owner.mid
    if (ownerMid <= 0L) return false
    val recentOwnerCount = acceptedRecommendations
        .takeLast(recentWindowSize.coerceAtLeast(1))
        .count { it.owner.mid == ownerMid }
    return recentOwnerCount >= maxRecentItemsPerOwner.coerceAtLeast(1)
}

internal fun toRelatedVideoForPortraitRecommendation(item: VideoItem): RelatedVideo? {
    val bvid = item.bvid.trim()
    if (bvid.isEmpty()) return null
    return RelatedVideo(
        aid = item.aid.takeIf { it > 0L } ?: item.id,
        bvid = bvid,
        cid = item.cid,
        title = item.title,
        pic = item.pic,
        owner = item.owner,
        stat = item.stat,
        duration = item.duration,
        pubdate = item.pubdate,
        // false can also mean the lightweight feed omitted dimensions, so only propagate a
        // positive fact; unknown candidates still receive the detail lookup below.
        isVertical = true.takeIf { item.isVertical },
    )
}

internal fun arePortraitRecommendationsContentSimilar(
    first: RelatedVideo,
    second: RelatedVideo
): Boolean {
    if (first.bvid.isBlank() || second.bvid.isBlank()) return false
    if (first.bvid == second.bvid) return true

    return arePortraitRecommendationSignaturesSimilar(
        firstSignature = buildPortraitRecommendationSignature(first),
        secondSignature = buildPortraitRecommendationSignature(second),
    )
}

private fun arePortraitRecommendationSignaturesSimilar(
    firstSignature: PortraitRecommendationSignature,
    secondSignature: PortraitRecommendationSignature
): Boolean {

    if (
        firstSignature.normalizedTitle.isNotBlank() &&
            firstSignature.normalizedTitle == secondSignature.normalizedTitle
    ) {
        return true
    }

    if (
        firstSignature.coverKey.isNotBlank() &&
            firstSignature.coverKey == secondSignature.coverKey
    ) {
        return true
    }

    val keywordOverlap = firstSignature.titleKeywords
        .intersect(secondSignature.titleKeywords)
        .size
    val durationDelta = abs(firstSignature.duration - secondSignature.duration)
    if (keywordOverlap >= 3) return true
    if (keywordOverlap >= 2 && durationDelta <= 30) return true
    if (
        firstSignature.ownerMid > 0L &&
            firstSignature.ownerMid == secondSignature.ownerMid &&
            keywordOverlap >= 1 &&
            durationDelta <= 45
    ) {
        return true
    }

    return false
}

private fun buildPortraitRecommendationSignature(
    video: RelatedVideo
): PortraitRecommendationSignature {
    return PortraitRecommendationSignature(
        normalizedTitle = normalizePortraitRecommendationTitle(video.title),
        coverKey = normalizePortraitRecommendationCoverKey(video.pic),
        titleKeywords = extractPortraitRecommendationKeywords(video.title),
        ownerMid = video.owner.mid,
        duration = video.duration.coerceAtLeast(0)
    )
}

// Title similarity is O(n^2) during portrait shuffle. Compile regex once so the
// main thread does not rebuild Matcher/ICU state for every pair comparison.
private val PORTRAIT_TITLE_BRACKET_PATTERN = Regex("[\\[{（(【].*?[\\]})）)】]")
private val PORTRAIT_TITLE_NON_WORD_PATTERN = Regex("[^\\u4e00-\\u9fa5a-z0-9]+")
private val PORTRAIT_TITLE_WHITESPACE_PATTERN = Regex("\\s+")
private val PORTRAIT_TITLE_ZH_TOKEN_PATTERN = Regex("[\\u4e00-\\u9fa5]{2,6}")
private val PORTRAIT_TITLE_EN_TOKEN_PATTERN = Regex("[a-z0-9]{3,}")

private fun normalizePortraitRecommendationTitle(title: String): String {
    return title.lowercase()
        .replace(PORTRAIT_TITLE_BRACKET_PATTERN, " ")
        .replace(PORTRAIT_TITLE_NON_WORD_PATTERN, " ")
        .replace(PORTRAIT_TITLE_WHITESPACE_PATTERN, " ")
        .trim()
}

private fun normalizePortraitRecommendationCoverKey(pic: String): String {
    return pic.trim()
        .substringBefore('?')
        .substringAfterLast('/')
        .lowercase()
}

private fun extractPortraitRecommendationKeywords(title: String): Set<String> {
    val normalized = normalizePortraitRecommendationTitle(title)
    if (normalized.isBlank()) return emptySet()

    val zhTokens = PORTRAIT_TITLE_ZH_TOKEN_PATTERN
        .findAll(normalized)
        .map { it.value }
        .filter { it !in PORTRAIT_RECOMMENDATION_STOP_WORDS }
        .take(6)
        .toList()

    val enTokens = PORTRAIT_TITLE_EN_TOKEN_PATTERN
        .findAll(normalized)
        .map { it.value }
        .take(4)
        .toList()

    return (zhTokens + enTokens).toSet()
}

internal fun snapshotPortraitPageBvids(
    items: List<Any>
): Set<String> {
    return items.mapNotNull { candidate ->
        when (candidate) {
            is ViewInfo -> candidate.bvid
            is RelatedVideo -> candidate.bvid
            else -> null
        }
    }.toSet()
}

internal fun shouldRecoverPortraitPagerSurfaceOnResume(
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasPlayerView: Boolean
): Boolean {
    return isCurrentPage && isPlayerReadyForThisVideo && hasPlayerView
}

internal fun toViewInfoForPortraitDetail(related: RelatedVideo): ViewInfo {
    return ViewInfo(
        bvid = related.bvid,
        aid = related.aid,
        title = related.title,
        desc = "",
        pic = related.pic,
        owner = related.owner,
        stat = related.stat,
        pubdate = related.pubdate,
        pages = listOf(
            Page(duration = related.duration.toLong())
        )
    )
}
