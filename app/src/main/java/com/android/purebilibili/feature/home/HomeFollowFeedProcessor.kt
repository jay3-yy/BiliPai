package com.android.purebilibili.feature.home

import com.android.purebilibili.core.util.appendDistinctByKey
import com.android.purebilibili.core.util.prependDistinctByKey
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun mapHomeFollowDynamicItemsToVideos(
    items: List<com.android.purebilibili.data.model.response.DynamicItem>,
    blockedMids: Set<Long>,
): List<com.android.purebilibili.data.model.response.VideoItem> {
    return items.mapNotNull { item ->
        if ((item.modules.module_author?.mid ?: 0) in blockedMids) return@mapNotNull null

        val archive = item.modules.module_dynamic?.major?.archive ?: return@mapNotNull null
        if (!shouldIncludeHomeFollowDynamicInVideoFeed(archive.bvid)) {
            return@mapNotNull null
        }

        val resolvedAid = resolveDynamicArchiveAid(
            archiveAid = archive.aid,
            fallbackId = 0L
        )
        com.android.purebilibili.data.model.response.VideoItem(
            id = resolvedAid,
            bvid = archive.bvid,
            dynamicId = item.id_str.trim(),
            aid = resolvedAid,
            title = archive.title,
            pic = archive.cover,
            duration = parseDurationText(archive.duration_text),
            owner = com.android.purebilibili.data.model.response.Owner(
                mid = item.modules.module_author?.mid ?: 0,
                name = item.modules.module_author?.name ?: "",
                face = item.modules.module_author?.face ?: ""
            ),
            stat = com.android.purebilibili.data.model.response.Stat(
                view = parseStatText(archive.stat.play),
                danmaku = parseStatText(archive.stat.danmaku)
            )
        )
    }
}

//  解析时长文本 "10:24" -> 624 秒
private fun parseDurationText(text: String): Int {
    val parts = text.split(":")
    return try {
        when (parts.size) {
            2 -> parts[0].toInt() * 60 + parts[1].toInt()
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            else -> 0
        }
    } catch (e: Exception) { 0 }
}

//  解析统计文本 "123.4万" -> 1234000
private fun parseStatText(text: String): Int {
    return try {
        if (text.contains("万")) {
            (text.replace("万", "").toFloat() * 10000).toInt()
        } else if (text.contains("亿")) {
            (text.replace("亿", "").toFloat() * 100000000).toInt()
        } else {
            text.toIntOrNull() ?: 0
        }
    } catch (e: Exception) { 0 }
}

internal data class HomeFollowFeedMerge(
    val videos: ImmutableList<VideoItem>,
    val insertedCount: Int,
    val canPrepend: Boolean,
)

internal fun mergeHomeFollowFeed(
    currentVideos: List<VideoItem>,
    incomingVideos: List<VideoItem>,
    isLoadMore: Boolean,
    incrementalRefreshEnabled: Boolean,
    usedUpdateBaseline: Boolean,
    forceReplace: Boolean,
    requestVideos: List<VideoItem> = currentVideos,
): HomeFollowFeedMerge {
    val removedKeys = if (requestVideos === currentVideos) emptySet() else {
        val currentKeys = currentVideos.mapTo(HashSet(), HomeFeedMergePolicy::keyOf)
        requestVideos.mapNotNullTo(HashSet()) { video ->
            HomeFeedMergePolicy.keyOf(video).takeUnless { it in currentKeys }
        }
    }
    val eligibleVideos = if (removedKeys.isEmpty()) incomingVideos else {
        incomingVideos.filterNot { HomeFeedMergePolicy.keyOf(it) in removedKeys }
    }
    val canPrepend = !forceReplace &&
        (usedUpdateBaseline || (!isLoadMore && incrementalRefreshEnabled)) &&
        (currentVideos.isEmpty() || run {
            val currentBvids = currentVideos.mapTo(HashSet()) { it.bvid }
            val currentIds = currentVideos.mapTo(HashSet()) { it.id }
            eligibleVideos.any { it.bvid in currentBvids || it.id in currentIds }
        })
    val merged = when {
        forceReplace -> eligibleVideos
        isLoadMore -> appendDistinctByKey(currentVideos, eligibleVideos, HomeFeedMergePolicy::keyOf)
        canPrepend -> prependDistinctByKey(currentVideos, eligibleVideos, HomeFeedMergePolicy::keyOf)
        else -> eligibleVideos
    }.toImmutableList()
    return HomeFollowFeedMerge(merged, (merged.size - currentVideos.size).coerceAtLeast(0), canPrepend)
}
