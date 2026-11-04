package com.android.purebilibili.feature.story

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.model.response.StoryOwner
import com.android.purebilibili.data.model.response.StoryPlayerArgs
import com.android.purebilibili.data.model.response.StoryStat
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo

internal data class StoryPortraitFeed(
    val initialInfo: ViewInfo,
    val recommendations: List<RelatedVideo>
)

internal data class StoryFeedSeed(
    val bvid: String,
    val cid: Long,
    val cover: String,
    val title: String = ""
) {
    fun toViewInfo(): ViewInfo {
        return ViewInfo(
            bvid = bvid,
            cid = cid,
            title = title,
            pic = cover,
            pages = listOf(
                Page(
                    cid = cid,
                    page = 1,
                    part = title,
                    duration = 0L
                )
            )
        )
    }
}

internal fun resolveNextStoryFeedAid(
    previousAid: Long,
    items: List<StoryItem>
): Long {
    return items.asReversed()
        .firstNotNullOfOrNull { item -> item.playerArgs?.aid?.takeIf { it > 0L } }
        ?: previousAid
}

internal fun storyItemIdentityKeys(item: StoryItem): List<String> = buildList {
    resolveStoryItemAid(item)?.let { add("aid:$it") }
    item.bvid.ifBlank { item.playerArgs?.bvid.orEmpty() }.trim()
        .takeIf { it.isNotEmpty() }?.let { add("bvid:$it") }
    if (item.id > 0L) add("id:${item.id}")
}

internal fun mergeStoryFeedItems(
    existingItems: List<StoryItem>,
    newItems: List<StoryItem>
): List<StoryItem> {
    val seenAids = existingItems.mapNotNull(::resolveStoryItemAid).toMutableSet()
    val seenBvids = existingItems.mapNotNull {
        it.bvid.ifBlank { it.playerArgs?.bvid.orEmpty() }.trim().takeIf { bvid -> bvid.isNotEmpty() }
    }.toMutableSet()
    val seenIds = existingItems.mapNotNull { it.id.takeIf { id -> id > 0L } }.toMutableSet()
    val merged = existingItems.toMutableList()

    newItems.forEach { item ->
        val aid = resolveStoryItemAid(item) ?: 0L
        val bvid = item.bvid.ifBlank { item.playerArgs?.bvid.orEmpty() }.trim()
        val id = item.id
        val alreadySeen = (aid > 0L && aid in seenAids) ||
            (bvid.isNotEmpty() && bvid in seenBvids) ||
            (id > 0L && id in seenIds)

        if (!alreadySeen) {
            merged += item
            if (aid > 0L) seenAids += aid
            if (bvid.isNotEmpty()) seenBvids += bvid
            if (id > 0L) seenIds += id
        }
    }

    return merged
}

internal fun buildStoryPortraitFeed(
    items: List<StoryItem>,
    seed: StoryFeedSeed? = null
): StoryPortraitFeed? {
    val playableItems = items.mapNotNull(::toPlayableStoryItem)
    if (seed != null && seed.bvid.isNotBlank()) {
        val recommendations = playableItems
            .filter { item -> item.playbackId != seed.bvid }
            .map { item -> item.toRelatedVideo() }
        return StoryPortraitFeed(
            initialInfo = seed.toViewInfo(),
            recommendations = recommendations
        )
    }
    val initial = playableItems.firstOrNull() ?: return null
    return StoryPortraitFeed(
        initialInfo = initial.toViewInfo(),
        recommendations = playableItems.drop(1).map { it.toRelatedVideo() }
    )
}

/**
 * Map a portrait-pager bvid back to Story feed index for load-more thresholds.
 * Seed-only videos (not yet present in the story feed) report 0.
 */
internal fun resolveStoryPortraitIndexForBvid(
    bvid: String,
    items: List<StoryItem>,
    seedBvid: String = ""
): Int {
    val normalized = bvid.trim()
    if (normalized.isEmpty()) return -1
    val playable = items.mapNotNull(::toPlayableStoryItem)
    val feedIndex = playable.indexOfFirst { it.playbackId == normalized }
    if (feedIndex >= 0) return feedIndex
    if (seedBvid.isNotBlank() && normalized == seedBvid.trim()) return 0
    return -1
}

private data class PlayableStoryItem(
    val source: StoryItem,
    val aid: Long,
    val cid: Long,
    val playbackId: String
) {
    fun toViewInfo(): ViewInfo {
        return ViewInfo(
            bvid = playbackId,
            aid = aid,
            cid = cid,
            title = source.title,
            desc = source.desc,
            pic = source.cover,
            owner = source.toOwner(),
            stat = source.toStat(),
            dimension = source.dimension,
            pages = listOf(
                Page(
                    cid = cid,
                    page = 1,
                    part = source.title,
                    duration = source.duration.toLong().coerceAtLeast(0L)
                )
            )
        )
    }

    fun toRelatedVideo(): RelatedVideo {
        return RelatedVideo(
            aid = aid,
            bvid = playbackId,
            cid = cid,
            title = source.title,
            pic = source.cover,
            owner = source.toOwner(),
            stat = source.toStat(),
            duration = source.duration.coerceAtLeast(0),
            isVertical = source.dimension?.takeIf { it.width > 0 && it.height > 0 }?.isVertical
        )
    }
}

/**
 * Story 流条目转竖屏流推荐项(供详情页竖屏全屏等场景混合多样内容)。
 * 缺少稿件身份或不是普通视频的条目返回 null；缺 cid 时由播放器解析。
 */
internal fun storyItemToRelatedVideo(item: StoryItem): RelatedVideo? =
    toPlayableStoryItem(item)?.toRelatedVideo()

/**
 * Map home-recommend [VideoItem] into Story feed shape so the portrait pager can reuse
 * existing merge / build / load-more helpers while sourcing more diverse content.
 *
 * Requires a playable id (bvid or aid). [cid] may be 0; the player resolves it on demand.
 */
internal fun videoItemToStoryItem(item: VideoItem): StoryItem? {
    val bvid = item.bvid.trim()
    val aid = item.aid.takeIf { it > 0L } ?: item.id.takeIf { it > 0L } ?: 0L
    if (bvid.isEmpty() && aid <= 0L) return null
    return StoryItem(
        id = aid.takeIf { it > 0L } ?: item.id,
        bvid = bvid,
        goto = "av",
        title = item.title,
        cover = item.pic,
        playerArgs = StoryPlayerArgs(
            aid = aid,
            cid = item.cid,
            bvid = bvid,
        ),
        stat = StoryStat(
            view = item.stat.view,
            like = item.stat.like,
            reply = item.stat.reply,
            share = item.stat.share,
            coin = item.stat.coin,
            favorite = item.stat.favorite,
            danmaku = item.stat.danmaku,
        ),
        owner = StoryOwner(
            mid = item.owner.mid,
            name = item.owner.name,
            face = item.owner.face,
        ),
        duration = item.duration.coerceAtLeast(0),
    )
}

internal fun videoItemsToStoryItems(items: List<VideoItem>): List<StoryItem> =
    items.mapNotNull(::videoItemToStoryItem)

private fun toPlayableStoryItem(item: StoryItem): PlayableStoryItem? {
    if (item.goto.isNotBlank() && item.goto !in setOf("av", "vertical_av")) return null
    val args = item.playerArgs
    val aid = resolveStoryItemAid(item) ?: 0L
    val bvid = item.bvid.ifBlank { args?.bvid.orEmpty() }.trim()
    // Home feed often has bvid without cid; still allow entry — player resolves cid later.
    if (aid <= 0L && bvid.isEmpty()) return null
    val playbackId = bvid.ifBlank {
        if (aid > 0L) "av$aid" else return null
    }
    return PlayableStoryItem(
        source = item,
        aid = aid,
        cid = (args?.cid ?: 0L).coerceAtLeast(0L),
        playbackId = playbackId
    )
}

private fun resolveStoryItemAid(item: StoryItem): Long? =
    item.playerArgs?.aid?.takeIf { it > 0L }
        ?: item.param.toLongOrNull()?.takeIf { it > 0L }

private fun StoryItem.toOwner(): Owner {
    return Owner(
        mid = owner?.mid ?: 0L,
        name = owner?.name.orEmpty(),
        face = owner?.face.orEmpty()
    )
}

private fun StoryItem.toStat(): Stat {
    return Stat(
        view = stat?.view ?: 0,
        danmaku = stat?.danmaku ?: 0,
        reply = stat?.reply ?: 0,
        like = stat?.like ?: 0,
        coin = stat?.coin ?: 0,
        favorite = stat?.favorite ?: 0,
        share = stat?.share ?: 0
    )
}
