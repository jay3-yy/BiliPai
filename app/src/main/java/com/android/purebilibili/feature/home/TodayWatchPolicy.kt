package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.plugin.sdk.RecommendationStrategy
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

private const val RECENT_CREATOR_WEIGHT = 0.65
private const val PERSISTED_CREATOR_WEIGHT = 0.35
private const val COMPLETED_WATCH_THRESHOLD = 0.8
private const val TOP_PREVIEW_LIMIT = 6
private const val TOP_PREVIEW_REPEAT_LIMIT = 2
private const val AFFINITY_CONFIDENCE_PRIOR = 0.65

enum class TodayWatchDurationFilter(val label: String) {
    ALL("全部"),
    QUICK("10分内"),
    MEALTIME("10–25分"),
    DEEP("25分+"),
}

internal fun filterTodayWatchQueueByDuration(
    videos: List<VideoItem>,
    filter: TodayWatchDurationFilter,
): List<VideoItem> = when (filter) {
    TodayWatchDurationFilter.ALL -> videos
    TodayWatchDurationFilter.QUICK -> videos.filter { it.duration in 1..600 }
    TodayWatchDurationFilter.MEALTIME -> videos.filter { it.duration in 601..1500 }
    TodayWatchDurationFilter.DEEP -> videos.filter { it.duration > 1500 }
}

internal fun estimateTodayWatchQueueMinutes(videos: List<VideoItem>): Int {
    val totalSec = videos.sumOf { it.duration.coerceAtLeast(0).toLong() }
    if (totalSec <= 0L) return 0
    return ((totalSec + 30L) / 60L).toInt().coerceAtLeast(1)
}

internal fun buildTodayWatchPlaylistItems(videos: List<VideoItem>): List<PlaylistItem> =
    videos.asSequence()
        .filter { it.bvid.isNotBlank() }
        .distinctBy { it.bvid }
        .map { video ->
            PlaylistItem(
                bvid = video.bvid,
                cid = video.cid.coerceAtLeast(0L),
                title = video.title.ifBlank { video.bvid },
                cover = video.pic,
                owner = video.owner.name,
                ownerFace = video.owner.face,
                duration = video.duration.coerceAtLeast(0).toLong(),
            )
        }
        .toList()

internal fun resolveTodayWatchContextHeadline(
    mode: TodayWatchMode,
    nightSignalUsed: Boolean,
    hourOfDay: Int,
): String = when {
    nightSignalUsed || hourOfDay >= 23 || hourOfDay < 5 ->
        if (mode == TodayWatchMode.RELAX) "夜深了，为你挑了轻松短片" else "深夜轻学习，节奏更柔和"
    hourOfDay in 11..13 && mode == TodayWatchMode.RELAX ->
        "午间下饭时光，挑一支顺眼的慢慢看"
    hourOfDay in 6..10 ->
        if (mode == TodayWatchMode.LEARN) "清晨状态正好，来点干货内容" else "早安，先看支轻快视频醒醒神"
    mode == TodayWatchMode.LEARN ->
        "按你的常看领域与完播节奏挑出深度学习单"
    else ->
        "结合近期完播习惯与宝藏互动率为你定制"
}

private data class CreatorAggregate(
    val mid: Long,
    val name: String,
    val score: Double,
    val watchCount: Int,
)

private data class StrategyWeights(
    val interest: Double,
    val mode: Double,
    val freshness: Double,
    val quality: Double,
    val exploration: Double,
    val diversity: Double,
)

private data class CandidateFeatures(
    val creatorAffinity: Double,
    val topicAffinity: Double,
    val interest: Double,
    val modeFit: Double,
    val durationComfort: Double,
    val freshness: Double,
    val quality: Double,
    val gemScore: Double,
    val exploration: Double,
    val partialWatchPenalty: Double,
    val clickbaitPenalty: Double,
    val topicKeys: Set<String>,
    val titleBigrams: Set<String>,
)

private data class ScoredCandidate(
    val video: VideoItem,
    val originalIndex: Int,
    val baseScore: Double,
    val confidence: Float,
    val features: CandidateFeatures,
    val explanation: String,
    val finalScore: Double = baseScore,
)

internal data class TodayWatchCreatorSignal(
    val mid: Long,
    val name: String = "",
    val score: Double,
    val watchCount: Int = 1,
)

internal data class TodayWatchPenaltySignals(
    val consumedBvids: Set<String> = emptySet(),
    val dislikedBvids: Set<String> = emptySet(),
    val dislikedCreatorMids: Set<Long> = emptySet(),
    val dislikedKeywords: Set<String> = emptySet(),
)

internal fun shouldEnableTodayWatchUpRankClick(upRank: TodayUpRank): Boolean = upRank.mid > 0L

/**
 * 今日推荐单 V3 算法：
 * 1. 对数饱和 + 置信阻尼归一化，避免单次浅尝覆盖长期常看 UP 主。
 * 2. 结合高完播历史的时长舒适区与冷启动权重再分配。
 * 3. B 站「投币+收藏+分享」贝叶斯宝藏质量分与标题党软惩罚。
 * 4. 14 领域主题分类 + 标题字符二元组 MMR 多样性重排，打散同热点扎堆内容。
 */
internal fun buildTodayWatchPlan(
    historyVideos: List<VideoItem>,
    candidateVideos: List<VideoItem>,
    mode: TodayWatchMode,
    eyeCareNightActive: Boolean,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L,
    upRankLimit: Int = 5,
    queueLimit: Int = 20,
    creatorSignals: List<TodayWatchCreatorSignal> = emptyList(),
    penaltySignals: TodayWatchPenaltySignals = TodayWatchPenaltySignals(),
    strategy: RecommendationStrategy = RecommendationStrategy.BALANCED,
): TodayWatchPlan {
    val cleanedHistory = historyVideos
        .filter { it.bvid.isNotBlank() }
        .sortedByDescending { it.view_at }
    val completionByBvid = cleanedHistory.associate { it.bvid to estimateCompletionRatio(it) }
    val recentCreatorScores = linkedMapOf<Long, Double>()
    val recentCreatorCounts = linkedMapOf<Long, Int>()
    val creatorNames = linkedMapOf<Long, String>()
    val rawTopicScores = linkedMapOf<String, Double>()

    cleanedHistory.forEach { item ->
        val completion = estimateCompletionRatio(item)
        val affinity = watchAffinityScore(completion, recencyBonus(item.view_at, nowEpochSec))
        if (item.owner.mid > 0L) {
            recentCreatorScores[item.owner.mid] = (recentCreatorScores[item.owner.mid] ?: 0.0) + affinity
            recentCreatorCounts[item.owner.mid] = (recentCreatorCounts[item.owner.mid] ?: 0) + 1
            creatorNames[item.owner.mid] = item.owner.name.ifBlank { "UP主${item.owner.mid}" }
        }
        resolveTodayWatchTopicKeys(item).forEach { topic ->
            rawTopicScores[topic] = (rawTopicScores[topic] ?: 0.0) + affinity
        }
    }

    val persistedCreatorScores = linkedMapOf<Long, Double>()
    val persistedCreatorCounts = linkedMapOf<Long, Int>()
    creatorSignals.filter { it.mid > 0L }.forEach { signal ->
        persistedCreatorScores[signal.mid] = maxOf(persistedCreatorScores[signal.mid] ?: 0.0, signal.score)
        persistedCreatorCounts[signal.mid] = maxOf(persistedCreatorCounts[signal.mid] ?: 0, signal.watchCount)
        creatorNames.putIfAbsent(signal.mid, signal.name.ifBlank { "UP主${signal.mid}" })
    }

    val normalizedRecentCreators = normalizeLogSaturatedScores(recentCreatorScores)
    val normalizedPersistedCreators = normalizeLogSaturatedScores(persistedCreatorScores)
    val creatorAffinity = (normalizedRecentCreators.keys + normalizedPersistedCreators.keys).associateWith { mid ->
        val recent = normalizedRecentCreators[mid]
        val persisted = normalizedPersistedCreators[mid]
        when {
            recent != null && persisted != null ->
                recent * RECENT_CREATOR_WEIGHT + persisted * PERSISTED_CREATOR_WEIGHT
            recent != null -> recent
            else -> persisted ?: 0.0
        }
    }
    val topicAffinity = normalizeLogSaturatedScores(rawTopicScores)
    val preferredDurationMin = estimatePreferredDurationMinutes(cleanedHistory, completionByBvid)

    val creators = creatorAffinity.map { (mid, score) ->
        CreatorAggregate(
            mid = mid,
            name = creatorNames[mid].orEmpty().ifBlank { "UP主$mid" },
            score = score,
            watchCount = maxOf(recentCreatorCounts[mid] ?: 0, persistedCreatorCounts[mid] ?: 0, 1),
        )
    }

    val normalizedDislikedKeywords = penaltySignals.dislikedKeywords
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    val eligibleCandidates = candidateVideos
        .asSequence()
        .filter { it.bvid.isNotBlank() && it.title.isNotBlank() }
        .filter { it.bvid !in penaltySignals.consumedBvids }
        .filter { it.bvid !in penaltySignals.dislikedBvids }
        .filter { it.owner.mid !in penaltySignals.dislikedCreatorMids }
        .filterNot { matchesDislikedKeyword(it, normalizedDislikedKeywords) }
        .filter { (completionByBvid[it.bvid] ?: 0.0) < COMPLETED_WATCH_THRESHOLD }
        .distinctBy { it.bvid }
        .toList()

    val qualityBreakdown = buildCandidateQualityScores(eligibleCandidates)
    val hasPersonalSignals = creatorAffinity.isNotEmpty() || topicAffinity.isNotEmpty()
    val weights = resolveEffectiveStrategyWeights(strategy, hasPersonalSignals)

    val scoredCandidates = eligibleCandidates.mapIndexed { index, video ->
        val topics = resolveTodayWatchTopicKeys(video)
        val creatorScore = creatorAffinity[video.owner.mid] ?: 0.0
        val topicScore = topics.maxOfOrNull { topicAffinity[it] ?: 0.0 } ?: 0.0
        val qualityPair = qualityBreakdown[video.bvid] ?: (0.5 to 0.5)
        val durationComfort = durationComfortScore(video.duration, preferredDurationMin)
        val rawModeFit = modeFitScore(video, mode, eyeCareNightActive)
        val blendedModeFit = if (preferredDurationMin != null) {
            (rawModeFit * 0.84 + durationComfort * 0.16).coerceIn(0.0, 1.0)
        } else {
            rawModeFit
        }
        val clickbaitPenalty = if (creatorScore < 0.35 && isClickbaitTitle(video.title)) 0.14 else 0.0

        val features = CandidateFeatures(
            creatorAffinity = creatorScore,
            topicAffinity = topicScore,
            interest = (creatorScore * 0.65 + topicScore * 0.35).coerceIn(0.0, 1.0),
            modeFit = blendedModeFit,
            durationComfort = durationComfort,
            freshness = continuousFreshnessScore(video.pubdate, nowEpochSec),
            quality = qualityPair.first,
            gemScore = qualityPair.second,
            exploration = explorationScore(creatorScore, topicScore, topics),
            partialWatchPenalty = ((completionByBvid[video.bvid] ?: 0.0) * 0.38).coerceIn(0.0, 0.38),
            clickbaitPenalty = clickbaitPenalty,
            topicKeys = topics,
            titleBigrams = extractTitleBigrams(video.title),
        )
        val score = (
            features.interest * weights.interest +
                features.modeFit * weights.mode +
                features.freshness * weights.freshness +
                features.quality * weights.quality +
                features.exploration * weights.exploration -
                features.partialWatchPenalty -
                features.clickbaitPenalty
            ).coerceIn(0.0, 1.0)

        ScoredCandidate(
            video = video,
            originalIndex = index,
            baseScore = score,
            confidence = score.toFloat(),
            features = features,
            explanation = buildRecommendationExplanation(video, mode, eyeCareNightActive, features, preferredDurationMin != null),
        )
    }.sortedWith(compareByDescending<ScoredCandidate> { it.baseScore }.thenBy { it.originalIndex })

    val selected = buildDiverseQueue(scoredCandidates, queueLimit.coerceIn(1, 60), weights.diversity)
    return TodayWatchPlan(
        mode = mode,
        upRanks = creators
            .sortedWith(compareByDescending<CreatorAggregate> { it.score }.thenByDescending { it.watchCount })
            .take(upRankLimit.coerceIn(1, 20))
            .map { TodayUpRank(it.mid, it.name, it.score, it.watchCount) }
            .toImmutableList(),
        videoQueue = selected.map { it.video }.toImmutableList(),
        explanationByBvid = selected.associate { it.video.bvid to it.explanation }.toImmutableMap(),
        scoreByBvid = selected.associate { it.video.bvid to it.finalScore }.toImmutableMap(),
        confidenceByBvid = selected.associate { it.video.bvid to it.confidence }.toImmutableMap(),
        historySampleCount = cleanedHistory.size,
        nightSignalUsed = eyeCareNightActive,
        generatedAt = System.currentTimeMillis(),
    )
}

private fun resolveEffectiveStrategyWeights(
    strategy: RecommendationStrategy,
    hasPersonalSignals: Boolean,
): StrategyWeights {
    val base = when (strategy) {
        RecommendationStrategy.BALANCED -> StrategyWeights(0.26, 0.36, 0.12, 0.15, 0.11, 0.20)
        RecommendationStrategy.AFFINITY -> StrategyWeights(0.46, 0.24, 0.10, 0.15, 0.05, 0.10)
        RecommendationStrategy.EXPLORE -> StrategyWeights(0.18, 0.22, 0.22, 0.13, 0.25, 0.32)
    }
    if (hasPersonalSignals) return base
    // 冷启动无历史信号时，把闲置的兴趣权重重分配到模式契合、宝藏质量与时效性上
    val free = base.interest
    return base.copy(
        interest = 0.0,
        mode = base.mode + free * 0.55,
        quality = base.quality + free * 0.27,
        freshness = base.freshness + free * 0.18,
    )
}

private fun <K> normalizeLogSaturatedScores(scores: Map<K, Double>): Map<K, Double> {
    val max = scores.values.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyMap()
    val logMax = ln(1.0 + max).coerceAtLeast(1e-6)
    return scores.mapValues { (_, value) ->
        if (value <= 0.0) {
            0.0
        } else {
            val logNorm = ln(1.0 + value) / logMax
            val confidence = value / (value + AFFINITY_CONFIDENCE_PRIOR)
            (logNorm * 0.72 + confidence * 0.28).coerceIn(0.0, 1.0)
        }
    }
}

private fun estimatePreferredDurationMinutes(
    history: List<VideoItem>,
    completionByBvid: Map<String, Double>,
): Double? {
    val completedDurations = history.mapNotNull { item ->
        val completion = completionByBvid[item.bvid] ?: 0.0
        if (completion >= 0.6 && item.duration in 60..7200) {
            item.duration / 60.0 to completion
        } else {
            null
        }
    }
    if (completedDurations.size < 3) return null
    val totalWeight = completedDurations.sumOf { it.second }.coerceAtLeast(1e-6)
    val weightedLogMean = completedDurations.sumOf { (min, w) -> ln(min) * w } / totalWeight
    return exp(weightedLogMean).coerceIn(2.0, 60.0)
}

private fun durationComfortScore(durationSec: Int, preferredMin: Double?): Double {
    if (preferredMin == null || durationSec <= 0) return 0.5
    val actualMin = (durationSec / 60.0).coerceAtLeast(0.5)
    val logDiff = ln(actualMin) - ln(preferredMin)
    return exp(-0.5 * (logDiff / 0.85).pow(2.0)).coerceIn(0.0, 1.0)
}

private fun buildCandidateQualityScores(candidates: List<VideoItem>): Map<String, Pair<Double, Double>> {
    if (candidates.isEmpty()) return emptyMap()
    val viewMetrics = candidates.map { ln(it.stat.view.coerceAtLeast(0).toDouble() + 1.0) }.sorted()
    val gemMetrics = candidates.map(::bayesianGemRate).sorted()
    return candidates.associate { video ->
        val viewPercentile = percentileRank(viewMetrics, ln(video.stat.view.coerceAtLeast(0).toDouble() + 1.0))
        val gemPercentile = percentileRank(gemMetrics, bayesianGemRate(video))
        val combined = (viewPercentile * 0.45 + gemPercentile * 0.55).coerceIn(0.0, 1.0)
        video.bvid to (combined to gemPercentile)
    }
}

private fun bayesianGemRate(video: VideoItem): Double {
    val weightedEngagement = video.stat.like.toDouble() +
        video.stat.coin * 3.0 +
        video.stat.favorite * 2.5 +
        video.stat.share * 3.0 +
        video.stat.reply * 0.8
    return (weightedEngagement / (video.stat.view.coerceAtLeast(0) + 1_500.0)).coerceIn(0.0, 1.0)
}

private fun percentileRank(sortedValues: List<Double>, value: Double): Double {
    if (sortedValues.size <= 1) return 0.5
    val lessOrEqual = sortedValues.count { it <= value }
    return ((lessOrEqual - 1).toDouble() / (sortedValues.size - 1)).coerceIn(0.0, 1.0)
}

private fun buildDiverseQueue(
    candidates: List<ScoredCandidate>,
    queueLimit: Int,
    diversityStrength: Double,
): List<ScoredCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val remaining = candidates.toMutableList()
    val selected = mutableListOf<ScoredCandidate>()
    val creatorCounts = mutableMapOf<Long, Int>()
    val topicCounts = mutableMapOf<String, Int>()

    while (selected.size < queueLimit && remaining.isNotEmpty()) {
        val capped = if (selected.size < TOP_PREVIEW_LIMIT) {
            remaining.filter { candidate ->
                val creatorAllowed = candidate.video.owner.mid <= 0L ||
                    (creatorCounts[candidate.video.owner.mid] ?: 0) < TOP_PREVIEW_REPEAT_LIMIT
                val primaryTopic = candidate.features.topicKeys.firstOrNull()
                val topicAllowed = primaryTopic == null ||
                    (topicCounts[primaryTopic] ?: 0) < TOP_PREVIEW_REPEAT_LIMIT
                creatorAllowed && topicAllowed
            }
        } else {
            remaining
        }
        val pool = capped.ifEmpty { remaining }
        val picked = pool.maxWithOrNull(
            compareBy<ScoredCandidate> {
                it.baseScore - diversityStrength * maximumSimilarity(it, selected)
            }.thenBy { -it.originalIndex }
        ) ?: break
        val adjusted = (picked.baseScore - diversityStrength * maximumSimilarity(picked, selected))
            .coerceIn(0.0, 1.0)
        selected += picked.copy(finalScore = adjusted, confidence = adjusted.toFloat())
        remaining.remove(picked)
        if (picked.video.owner.mid > 0L) {
            creatorCounts[picked.video.owner.mid] = (creatorCounts[picked.video.owner.mid] ?: 0) + 1
        }
        picked.features.topicKeys.firstOrNull()?.let { topic ->
            topicCounts[topic] = (topicCounts[topic] ?: 0) + 1
        }
    }
    return selected
}

private fun maximumSimilarity(candidate: ScoredCandidate, selected: List<ScoredCandidate>): Double {
    return selected.maxOfOrNull { existing ->
        val sameCreator = candidate.video.owner.mid > 0L && candidate.video.owner.mid == existing.video.owner.mid
        val topicOverlap = candidate.features.topicKeys.intersect(existing.features.topicKeys).isNotEmpty()
        val titleOverlap = jaccardSimilarity(candidate.features.titleBigrams, existing.features.titleBigrams)
        ((if (sameCreator) 0.55 else 0.0) +
            (if (topicOverlap) 0.30 else 0.0) +
            titleOverlap * 0.35).coerceAtMost(1.0)
    } ?: 0.0
}

private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val inter = a.count { it in b }
    if (inter == 0) return 0.0
    return inter.toDouble() / (a.size + b.size - inter).toDouble()
}

internal fun extractTitleBigrams(title: String): Set<String> {
    val cleaned = buildString(title.length) {
        for (ch in title.lowercase()) {
            if (ch.isLetterOrDigit() || ch in '\u4e00'..'\u9fff') append(ch)
        }
    }
    if (cleaned.length < 2) return emptySet()
    val result = hashSetOf<String>()
    val limit = (cleaned.length - 1).coerceAtMost(32)
    for (i in 0 until limit) {
        result += cleaned.substring(i, i + 2)
    }
    return result
}

private fun buildRecommendationExplanation(
    video: VideoItem,
    mode: TodayWatchMode,
    eyeCareNightActive: Boolean,
    features: CandidateFeatures,
    hasDurationProfile: Boolean,
): String {
    val reasons = mutableListOf<String>()
    if (features.creatorAffinity >= 0.45) reasons += "常看UP"
    if (features.modeFit >= 0.68) reasons += if (mode == TodayWatchMode.RELAX) "轻松向" else "学习向"
    if (eyeCareNightActive && nightFriendlyScore(video) >= 0.7) reasons += "夜间友好"
    if (features.freshness >= 0.75) reasons += "近期更新"
    if (features.gemScore >= 0.78 && features.quality >= 0.7) reasons += "高赞宝藏"
    if (features.topicAffinity >= 0.45) reasons += "常看分区"
    if (hasDurationProfile && features.durationComfort >= 0.82) reasons += "时长合拍"
    if (features.exploration >= 0.8) reasons += "新UP探索"
    return reasons.distinct().take(3).ifEmpty {
        listOf(if (mode == TodayWatchMode.RELAX) "轻松向" else "学习向")
    }.joinToString(" · ")
}

private fun modeFitScore(video: VideoItem, mode: TodayWatchMode, eyeCareNightActive: Boolean): Double {
    val searchable = "${video.title} ${video.tname}".lowercase()
    val durationMin = video.duration.coerceAtLeast(0) / 60.0
    val intensity = video.stat.danmaku.toDouble() / video.stat.view.coerceAtLeast(1).toDouble()
    val relaxCue = RELAX_KEYWORDS.any(searchable::contains)
    val learnCue = LEARN_KEYWORDS.any(searchable::contains)
    val entertainmentCue = ENTERTAINMENT_CASUAL_KEYWORDS.any(searchable::contains)
    val base = when (mode) {
        TodayWatchMode.RELAX -> {
            val durationFit = when {
                durationMin < 1.5 -> 0.45
                durationMin <= 14.0 -> 1.0
                durationMin <= 22.0 -> 0.62
                durationMin <= 32.0 -> 0.30
                else -> 0.08
            }
            val calmFit = when {
                intensity < 0.004 -> 1.0
                intensity < 0.01 -> 0.65
                else -> 0.2
            }
            (durationFit * 0.48 + calmFit * 0.22 + (if (relaxCue || entertainmentCue) 0.30 else 0.10) -
                (if (learnCue) 0.28 else 0.0)).coerceIn(0.0, 1.0)
        }
        TodayWatchMode.LEARN -> {
            val durationFit = when {
                durationMin < 4.0 -> 0.05
                durationMin < 8.0 -> 0.38
                durationMin <= 45.0 -> 1.0
                durationMin <= 70.0 -> 0.78
                else -> 0.42
            }
            val deepCueBonus = when {
                learnCue -> 0.45
                durationMin >= 10.0 && !entertainmentCue && !relaxCue -> 0.22
                else -> 0.06
            }
            val casualPenalty = when {
                entertainmentCue && !learnCue -> 0.34
                relaxCue && !learnCue && durationMin < 15.0 -> 0.24
                else -> 0.0
            }
            (durationFit * 0.55 + deepCueBonus - casualPenalty).coerceIn(0.0, 1.0)
        }
    }
    return if (eyeCareNightActive) {
        (base * 0.75 + nightFriendlyScore(video) * 0.25).coerceIn(0.0, 1.0)
    } else {
        base
    }
}

private fun nightFriendlyScore(video: VideoItem): Double {
    val durationMin = video.duration.coerceAtLeast(0) / 60.0
    val intensity = video.stat.danmaku.toDouble() / video.stat.view.coerceAtLeast(1).toDouble()
    val durationFit = when {
        durationMin <= 15.0 -> 1.0
        durationMin <= 25.0 -> 0.7
        durationMin <= 45.0 -> 0.35
        else -> 0.1
    }
    val calmFit = when {
        intensity < 0.006 -> 1.0
        intensity < 0.012 -> 0.6
        else -> 0.2
    }
    return durationFit * 0.6 + calmFit * 0.4
}

private fun continuousFreshnessScore(pubdate: Long, nowEpochSec: Long): Double {
    if (pubdate <= 0L) return 0.5
    val ageDays = (nowEpochSec - pubdate).coerceAtLeast(0L) / 86_400.0
    return 2.0.pow(-ageDays / 30.0).coerceIn(0.0, 1.0)
}

private fun explorationScore(creatorAffinity: Double, topicAffinity: Double, topics: Set<String>): Double {
    val unseenCreator = if (creatorAffinity < 0.05) 1.0 else 0.0
    val unseenTopic = if (topics.isEmpty() || topicAffinity < 0.05) 1.0 else 0.0
    return unseenCreator * 0.6 + unseenTopic * 0.4
}

private fun isClickbaitTitle(title: String): Boolean {
    val lower = title.lowercase()
    return CLICKBAIT_KEYWORDS.any(lower::contains)
}

private fun matchesDislikedKeyword(video: VideoItem, keywords: Set<String>): Boolean {
    if (keywords.isEmpty()) return false
    val searchable = "${video.title} ${video.tname}".lowercase()
    return keywords.any(searchable::contains)
}

private fun resolveTodayWatchTopicKeys(video: VideoItem): Set<String> = buildSet {
    video.tid.takeIf { it > 0 }?.let { add("partition-id:$it") }
    video.tname.trim().lowercase().takeIf { it.isNotBlank() }?.let { add("partition:$it") }
    val searchable = "${video.title} ${video.tname}".lowercase()
    TOPIC_KEYWORDS.forEach { (topic, keywords) ->
        if (keywords.any(searchable::contains)) add("topic:$topic")
    }
}

private fun estimateCompletionRatio(item: VideoItem): Double {
    if (item.progress < 0) return 0.35
    if (item.duration <= 0) return (item.progress / 600.0).coerceIn(0.0, 1.0)
    return (item.progress.toDouble() / item.duration.toDouble()).coerceIn(0.0, 1.0)
}

private fun watchAffinityScore(completion: Double, recencyBonus: Double): Double {
    val completionScore = when {
        completion >= 0.9 -> 1.85
        completion >= 0.6 -> 0.9 + completion * 0.75
        completion >= 0.3 -> 0.25 + completion * 0.45
        else -> 0.1
    }
    return completionScore + recencyBonus * if (completion >= 0.6) 1.0 else 0.35
}

private fun recencyBonus(viewAt: Long, nowEpochSec: Long): Double {
    if (viewAt <= 0L) return 0.25
    val days = (nowEpochSec - viewAt).coerceAtLeast(0L) / 86_400.0
    return when {
        days <= 1.0 -> 1.0
        days <= 3.0 -> 0.8
        days <= 7.0 -> 0.6
        days <= 30.0 -> 0.35
        else -> 0.15
    }
}

private val CLICKBAIT_KEYWORDS = listOf(
    "震惊", "不敢相信", "不看后悔", "疯传", "惊呆了", "太离谱了", "全网炸锅",
)

private val RELAX_KEYWORDS = listOf(
    "音乐", "vlog", "日常", "搞笑", "轻松", "治愈", "asmr", "旅行", "美食", "游戏", "开箱", "萌宠", "漫剪", "下饭",
)

private val ENTERTAINMENT_CASUAL_KEYWORDS = listOf(
    "搞笑", "配音", "鬼畜", "整活", "沙雕", "段位", "对抗路", "五杀", "王者", "gta", "吃鸡", "抽卡", "开箱", "实况", "游戏",
)

private val LEARN_KEYWORDS = listOf(
    "教程", "科普", "知识", "学习", "原理", "实战", "复盘", "编程", "数学", "英语",
    "课程", "技术", "分析", "入门", "进阶", "源码", "架构", "论文", "讲解", "深度",
    "历史", "纪录片", "评测", "拆解", "商业", "经济", "物理", "哲学", "拉片",
)

private val TOPIC_KEYWORDS = listOf(
    "music" to listOf("音乐", "唱", "歌", "演奏", "翻唱", "live", "mv", "钢琴", "吉他"),
    "learn" to listOf("教程", "科普", "知识", "学习", "原理", "实战", "复盘", "编程", "数学", "英语", "课程", "技术", "分析", "入门", "进阶", "kotlin", "android"),
    "tech" to listOf("数码", "手机", "评测", "芯片", "电脑", "硬件", "显卡", "相机", "ai", "大模型"),
    "anime" to listOf("番剧", "动漫", "动画", "二次元", "新番", "漫评", "mad", "amv", "声优"),
    "film" to listOf("电影", "影视", "解说", "拉片", "影评", "剧评", "纪录片"),
    "game" to listOf("游戏", "实况", "通关", "原神", "崩坏", "minecraft", "黑神话", "steam", "攻略"),
    "food" to listOf("美食", "做饭", "料理", "探店", "烘焙", "下饭", "小吃"),
    "travel" to listOf("旅行", "旅游", "城市", "徒步", "露营", "vlog", "骑行", "自驾"),
    "science" to listOf("历史", "物理", "天文", "生物", "人文", "地理", "哲学", "心理"),
    "life" to listOf("萌宠", "猫", "狗", "家居", "手工", "diy", "穿搭", "收纳"),
    "sports" to listOf("健身", "篮球", "足球", "跑步", "羽毛球", "游泳", "体育", "赛车"),
    "finance" to listOf("财经", "经济", "商业", "职场", "创业", "投资", "理财"),
    "art" to listOf("绘画", "插画", "设计", "摄影", "剪辑", "配色", "3d", "建模"),
    "relax" to listOf("日常", "搞笑", "轻松", "治愈", "asmr", "助眠", "白噪音"),
)
