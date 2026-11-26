package com.android.purebilibili.feature.personal

import com.android.purebilibili.data.model.response.VideoItem
import kotlin.random.Random

enum class MealtimeDurationFilter(
    val label: String,
    val rangeHint: String,
) {
    MEALTIME("下饭优选", "10–30 分钟"),
    QUICK_BITE("短小精悍", "10 分钟内"),
    ALL("全部随缘", "不限时长"),
}

data class MealtimeGachaCandidate(
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val upName: String,
    val durationSec: Int,
    val viewCount: Long = 0L,
    val isVertical: Boolean = false,
)

data class MealtimeGachaSession(
    val current: MealtimeGachaCandidate? = null,
    val remainingQueue: List<MealtimeGachaCandidate> = emptyList(),
    val drawnCount: Int = 0,
    val poolSize: Int = 0,
)

internal fun VideoItem.toMealtimeGachaCandidate(): MealtimeGachaCandidate? {
    val cleanBvid = bvid.trim()
    if (cleanBvid.isEmpty()) return null
    val normalizedCover = when {
        pic.startsWith("//") -> "https:$pic"
        pic.startsWith("http://") -> pic.replaceFirst("http://", "https://")
        else -> pic
    }
    return MealtimeGachaCandidate(
        bvid = cleanBvid,
        cid = cid,
        title = title.ifBlank { "未命名视频" },
        coverUrl = normalizedCover,
        upName = owner.name,
        durationSec = duration.coerceAtLeast(0),
        viewCount = stat.view.toLong().coerceAtLeast(0L),
        isVertical = isVertical,
    )
}

internal fun filterMealtimeCandidates(
    candidates: List<MealtimeGachaCandidate>,
    filter: MealtimeDurationFilter,
): List<MealtimeGachaCandidate> {
    val distinct = candidates.distinctBy { it.bvid }
    return when (filter) {
        MealtimeDurationFilter.MEALTIME -> distinct.filter { it.durationSec in 600..1800 }
        MealtimeDurationFilter.QUICK_BITE -> distinct.filter { it.durationSec in 1 until 600 }
        MealtimeDurationFilter.ALL -> distinct
    }
}

internal fun startMealtimeGachaSession(
    candidates: List<MealtimeGachaCandidate>,
    filter: MealtimeDurationFilter,
    random: Random = Random.Default,
): MealtimeGachaSession {
    val pool = filterMealtimeCandidates(candidates, filter)
    if (pool.isEmpty()) {
        return MealtimeGachaSession(current = null, remainingQueue = emptyList(), drawnCount = 0, poolSize = 0)
    }
    val shuffled = pool.shuffled(random)
    return MealtimeGachaSession(
        current = shuffled.first(),
        remainingQueue = shuffled.drop(1),
        drawnCount = 1,
        poolSize = pool.size,
    )
}

/**
 * 抽取下一张卡片：本轮未抽过的优先出列；抽完一整轮后自动洗牌重来，并尽量避免连续重复同一张。
 */
internal fun drawNextMealtimeCandidate(
    session: MealtimeGachaSession,
    allCandidates: List<MealtimeGachaCandidate>,
    filter: MealtimeDurationFilter,
    random: Random = Random.Default,
): MealtimeGachaSession {
    val pool = filterMealtimeCandidates(allCandidates, filter)
    if (pool.isEmpty()) {
        return MealtimeGachaSession(current = null, remainingQueue = emptyList(), drawnCount = 0, poolSize = 0)
    }
    if (session.remainingQueue.isNotEmpty()) {
        return session.copy(
            current = session.remainingQueue.first(),
            remainingQueue = session.remainingQueue.drop(1),
            drawnCount = session.drawnCount + 1,
            poolSize = pool.size,
        )
    }
    var reshuffled = pool.shuffled(random)
    val previousBvid = session.current?.bvid
    if (reshuffled.size > 1 && previousBvid != null && reshuffled.first().bvid == previousBvid) {
        reshuffled = reshuffled.drop(1) + reshuffled.first()
    }
    return MealtimeGachaSession(
        current = reshuffled.first(),
        remainingQueue = reshuffled.drop(1),
        drawnCount = 1,
        poolSize = pool.size,
    )
}

internal fun resolveMealtimeGachaFlavorText(durationSec: Int): String = when {
    durationSec in 600..1800 -> "刚好配一顿饭的黄金片长"
    durationSec in 1 until 600 -> "轻巧爽口，几分钟就能看完"
    durationSec > 1800 -> "分量十足，适合泡杯茶慢慢看"
    else -> "交给缘分，点开看看惊喜"
}
