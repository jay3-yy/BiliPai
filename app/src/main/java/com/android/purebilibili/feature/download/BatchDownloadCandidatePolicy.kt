package com.android.purebilibili.feature.download

import com.android.purebilibili.data.model.response.ViewInfo

internal data class BatchDownloadCandidate(
    val id: String,
    val bvid: String,
    val cid: Long,
    val title: String,
    val label: String,
    val groupKey: String = "",
    val groupTitle: String = "",
    val episodeSortIndex: Int = 0,
    val episodeCount: Int = 1,
    val cover: String,
    val ownerName: String,
    val durationSeconds: Int = 0,
    val isVerticalVideo: Boolean = false,
    val selected: Boolean,
    val aid: Long = 0L,
    val ownerFace: String = "",
)

internal fun resolveBatchDownloadCandidates(
    info: ViewInfo,
    detailsByBvid: Map<String, ViewInfo> = emptyMap(),
): List<BatchDownloadCandidate> {
    val groupTitle = info.ugc_season?.title?.trim().orEmpty().ifBlank { info.title.trim() }
    val groupKey = info.ugc_season?.id
        ?.takeIf { it > 0L }
        ?.let { "ugc:$it" }
        ?: "ugc:${info.bvid}"
    val episodes = info.ugc_season?.sections.orEmpty().flatMap { it.episodes }
    val knownPages = episodes.groupBy { it.bvid.trim() }.mapValues { (bvid, entries) ->
        // 详情分 P 优先；同稿件重复出现在不同分组时合并，不能重复加入任务。
        val detailPages = detailsByBvid[bvid]?.pages.orEmpty() +
            if (bvid == info.bvid) info.pages else emptyList()
        (detailPages + entries.flatMap { it.pages })
            .filter { it.cid > 0L }
            .distinctBy { it.cid }
            .sortedBy { it.page.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
    }
    val seasonCandidates = episodes.flatMap { episode ->
        val bvid = episode.bvid.trim()
        if (bvid.isBlank()) return@flatMap emptyList()
        val detail = detailsByBvid[bvid]
        val title = detail?.title?.trim().orEmpty().ifBlank {
            episode.arc?.title?.trim().orEmpty().ifBlank {
                episode.title.trim().ifBlank { info.title }
            }
        }
        val cover = detail?.pic?.takeIf { it.isNotBlank() }
            ?: episode.arc?.pic?.takeIf { it.isNotBlank() } ?: info.pic
        val pages = knownPages[bvid].orEmpty()
        val seed = BatchDownloadCandidate(
            id = "$bvid#${episode.cid}",
            bvid = bvid,
            cid = episode.cid,
            title = title,
            label = title,
            groupKey = groupKey,
            groupTitle = groupTitle,
            cover = cover,
            ownerName = detail?.owner?.name?.takeIf { it.isNotBlank() } ?: info.owner.name,
            durationSeconds = episode.arc?.duration?.coerceAtLeast(0) ?: 0,
            isVerticalVideo = detail?.dimension?.isVertical ?: (info.dimension?.isVertical == true),
            selected = bvid == info.bvid && episode.cid == info.cid,
            aid = detail?.aid?.takeIf { it > 0L } ?: episode.aid.takeIf { it > 0L }
                ?: episode.arc?.aid ?: 0L,
        )
        if (pages.isEmpty()) {
            // 未取得分 P 资料时保留原条目，界面会单独提示尚未展开的稿件。
            if (episode.cid > 0L) listOf(seed) else emptyList()
        } else {
            pages.mapIndexed { index, page ->
                val pageNo = page.page.takeIf { it > 0 } ?: index + 1
                val partLabel = page.part.trim().ifBlank { title }
                seed.copy(
                    id = "$bvid#${page.cid}",
                    cid = page.cid,
                    label = if (pages.size > 1) "P$pageNo $partLabel" else title,
                    durationSeconds = page.duration.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    selected = bvid == info.bvid && page.cid == info.cid,
                )
            }
        }
    }.distinctBy { it.id }

    if (seasonCandidates.isNotEmpty()) {
        return seasonCandidates.mapIndexed { index, candidate ->
            candidate.copy(episodeSortIndex = index + 1, episodeCount = seasonCandidates.size)
        }
    }

    val pageCandidates = info.pages.mapIndexedNotNull { index, page ->
        val cid = page.cid.takeIf { it > 0L } ?: return@mapIndexedNotNull null
        val pageNo = page.page.takeIf { it > 0 } ?: (index + 1)
        val partLabel = page.part.trim().ifBlank { info.title }
        BatchDownloadCandidate(
            id = "${info.bvid}#$cid",
            bvid = info.bvid,
            cid = cid,
            title = info.title,
            label = "P$pageNo $partLabel".trim(),
            groupKey = "bvid:${info.bvid}",
            groupTitle = info.title.trim(),
            episodeSortIndex = pageNo,
            episodeCount = 1,
            cover = info.pic,
            ownerName = info.owner.name,
            durationSeconds = page.duration.toInt().coerceAtLeast(0),
            isVerticalVideo = info.dimension?.isVertical == true,
            selected = cid == info.cid,
            aid = info.aid,
        )
    }.distinctBy { it.id }
    if (pageCandidates.isNotEmpty()) {
        return pageCandidates.map { candidate ->
            candidate.copy(episodeCount = pageCandidates.size)
        }
    }
    return emptyList()
}

internal fun resolveBatchDownloadCandidate(
    info: ViewInfo,
    targetBvid: String,
    targetCid: Long
): BatchDownloadCandidate? {
    return resolveBatchDownloadCandidates(info)
        .firstOrNull { it.bvid == targetBvid && it.cid == targetCid }
}
