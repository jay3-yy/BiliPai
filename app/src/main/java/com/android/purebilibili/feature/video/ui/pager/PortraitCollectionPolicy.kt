package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.ViewInfo

/**
 * Follow-up segments for a single multi-P video inside portrait immersive.
 * UGC season episodes deliberately stay out of the recommendation queue: inserting the whole
 * season makes normal swiping cluster videos from the same uploader. Explicit season selection
 * remains available through the detail sheet.
 */
internal fun resolvePortraitCollectionFollowUps(
    info: ViewInfo,
    currentCid: Long = info.cid,
    onlyVertical: Boolean = false,
    minVerticalRatio: Float = 1.0f,
): List<RelatedVideo> {
    return resolvePortraitMultiPageFollowUps(
        info = info,
        currentCid = currentCid,
        onlyVertical = onlyVertical,
        minVerticalRatio = minVerticalRatio,
    )
}

internal fun resolvePortraitMultiPageFollowUps(
    info: ViewInfo,
    currentCid: Long = info.cid,
    onlyVertical: Boolean = false,
    minVerticalRatio: Float = 1.0f,
): List<RelatedVideo> {
    val pages = info.pages.filter { it.cid > 0L }
    if (pages.size <= 1) return emptyList()

    val currentIndex = pages.indexOfFirst { it.cid == currentCid }
        .takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.cid == info.cid }.coerceAtLeast(0)

    return pages.drop(currentIndex + 1).mapNotNull { page ->
        val effectiveDimension = page.dimension?.takeIf { it.width > 0 && it.height > 0 } ?: info.dimension
        val pageIsVertical = effectiveDimension?.takeIf { it.width > 0 && it.height > 0 }
            ?.isVerticalAtRatio(minVerticalRatio)
        if (onlyVertical && pageIsVertical != true) return@mapNotNull null
        RelatedVideo(
            aid = info.aid,
            bvid = info.bvid,
            cid = page.cid,
            title = page.part.ifBlank { "P${page.page.coerceAtLeast(1)}" },
            pic = info.pic,
            owner = info.owner,
            stat = info.stat,
            duration = page.duration.toInt().coerceAtLeast(0),
            pubdate = info.pubdate,
            isVertical = pageIsVertical,
        )
    }
}

/**
 * Whether auto-continue (CONTINUE_CURRENT_LOGIC) should advance to [nextItem].
 * True for multi-P or an explicitly selected season episode already present in the pager;
 * false for the normal related feed.
 */
internal fun shouldPortraitAutoContinueToNextItem(
    currentItem: Any?,
    nextItem: Any?,
    currentLoadedInfo: ViewInfo? = null
): Boolean {
    val current = currentItem?.let(::resolvePortraitPagePlaybackIdentity) ?: return false
    val next = nextItem?.let(::resolvePortraitPagePlaybackIdentity) ?: return false
    if (current.bvid.isBlank() || next.bvid.isBlank()) return false

    // Multi-P: same bvid, different cid.
    if (current.bvid == next.bvid && next.cid > 0L && next.cid != current.cid) {
        return true
    }

    // Season: next episode listed in current video's ugc_season.
    val season = currentLoadedInfo?.ugc_season ?: return false
    if (currentLoadedInfo.bvid.trim() != current.bvid) return false
    val episodes = season.sections.flatMap { it.episodes }
    return episodes.any { episode ->
        val episodeBvid = episode.bvid.trim().ifBlank {
            if (episode.aid > 0L) "av${episode.aid}" else ""
        }
        episodeBvid == next.bvid || (next.cid > 0L && episode.cid == next.cid)
    }
}

/**
 * Insert collection follow-ups immediately after [currentPage], before unrelated feed items.
 * Skips bvids/cids already present later in the list.
 */
internal fun resolvePortraitCollectionInjectionPlan(
    pageItems: List<Any>,
    currentPage: Int,
    followUps: List<RelatedVideo>
): List<RelatedVideo> {
    if (followUps.isEmpty()) return emptyList()
    if (currentPage !in pageItems.indices) return emptyList()

    val existingKeys = pageItems.mapNotNull { item ->
        resolvePortraitPagePlaybackIdentity(item)?.let { identity ->
            portraitCollectionIdentityKey(identity.bvid, identity.cid)
        }
    }.toMutableSet()

    return followUps.filter { candidate ->
        val bvid = candidate.bvid.trim()
        if (bvid.isEmpty()) return@filter false
        val key = portraitCollectionIdentityKey(bvid, candidate.cid)
        if (key in existingKeys) return@filter false
        existingKeys += key
        true
    }
}

internal fun portraitCollectionIdentityKey(bvid: String, cid: Long): String {
    val normalized = bvid.trim()
    return if (cid > 0L) "$normalized#$cid" else normalized
}

/**
 * Find a pager index for a multi-P / season selection from the detail sheet.
 * Prefer exact bvid+cid; fall back to bvid-only when cid is unknown.
 */
internal fun resolvePortraitCollectionPageIndex(
    pageItems: List<Any>,
    targetBvid: String,
    targetCid: Long
): Int {
    val normalizedBvid = targetBvid.trim()
    if (normalizedBvid.isEmpty()) return -1
    if (targetCid > 0L) {
        val exact = pageItems.indexOfFirst { candidate ->
            val identity = resolvePortraitPagePlaybackIdentity(candidate) ?: return@indexOfFirst false
            identity.bvid == normalizedBvid && identity.cid == targetCid
        }
        if (exact >= 0) return exact
    }
    return pageItems.indexOfFirst { candidate ->
        val identity = resolvePortraitPagePlaybackIdentity(candidate) ?: return@indexOfFirst false
        identity.bvid == normalizedBvid
    }
}

/**
 * Keep the UGC-season identity on an explicitly selected collection page.
 * Recommendation pages normally stay lightweight [RelatedVideo] values, but a collection click
 * carries screen data that [RelatedVideo] cannot represent and therefore promotes the target to
 * [ViewInfo]. The subsequent detail response will enrich this seed without dropping the season.
 */
internal fun buildPortraitCollectionPageItem(
    existing: Any?,
    targetBvid: String,
    targetCid: Long,
    collectionContext: UgcSeason?,
): Any {
    val normalizedBvid = targetBvid.trim()
    val normalizedCid = targetCid.coerceAtLeast(0L)
    return when (existing) {
        is ViewInfo -> existing.copy(
            bvid = normalizedBvid.ifBlank { existing.bvid },
            cid = normalizedCid.takeIf { it > 0L } ?: existing.cid,
            ugc_season = collectionContext ?: existing.ugc_season,
        )
        is RelatedVideo -> {
            if (collectionContext == null) {
                existing
            } else {
                toViewInfoForPortraitDetail(existing).copy(
                    cid = normalizedCid.takeIf { it > 0L } ?: existing.cid,
                    ugc_season = collectionContext,
                )
            }
        }
        else -> {
            if (collectionContext == null) {
                RelatedVideo(bvid = normalizedBvid, cid = normalizedCid, title = normalizedBvid)
            } else {
                ViewInfo(
                    bvid = normalizedBvid,
                    cid = normalizedCid,
                    title = normalizedBvid,
                    ugc_season = collectionContext,
                )
            }
        }
    }
}
