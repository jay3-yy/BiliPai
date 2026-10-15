package com.android.purebilibili.core.network

import com.android.purebilibili.data.model.response.DynamicAuthorUpdatesPageResponse
import com.android.purebilibili.data.model.response.DynamicAuthorUpdatesPortalResponse

/** Recent-author red dots, not a complete following list or per-dynamic read receipts. */
object DynamicAuthorUpdatesApi {
    suspend fun load(): Map<Long, Boolean> = collectDynamicAuthorUpdates(
        loadPortal = { NetworkModule.dynamicApi.getDynamicAuthorUpdatesPortal() },
        loadPage = { offset -> NetworkModule.dynamicApi.getDynamicAuthorUpdatesPage(offset) }
    )
}

/** Publish a snapshot only after every page has passed validation. */
internal suspend fun collectDynamicAuthorUpdates(
    loadPortal: suspend () -> DynamicAuthorUpdatesPortalResponse,
    loadPage: suspend (String) -> DynamicAuthorUpdatesPageResponse
): Map<Long, Boolean> {
    val portal = loadPortal()
    check(portal.code == 0) { "Dynamic author portal failed: code=${portal.code}" }
    var page = checkNotNull(portal.data?.upList) { "Dynamic author portal is missing data.up_list" }
    val updates = mutableMapOf<Long, Boolean>()
    val seenAuthors = mutableSetOf<Long>()
    val seenOffsets = mutableSetOf<String>()

    while (true) {
        val hasMore = checkNotNull(page.hasMore) { "Dynamic author page is missing has_more" }
        val items = checkNotNull(page.items) { "Dynamic author page is missing items" }
        var madeProgress = false
        for (item in items) {
            val mid = item.mid?.takeIf { it > 0L } ?: continue
            if (seenAuthors.add(mid)) madeProgress = true
            val hasUpdate = item.hasUpdate ?: continue
            val previous = updates.put(mid, hasUpdate)
            check(previous == null || previous == hasUpdate) { "Dynamic author pages contain conflicting flags" }
        }
        if (!hasMore) return updates
        check(madeProgress) { "Dynamic author pagination made no progress" }
        val offset = checkNotNull(page.offset?.takeIf { it.isNotBlank() }) {
            "Dynamic author pagination is missing its next offset"
        }
        check(seenOffsets.add(offset)) { "Dynamic author pagination repeated an offset" }
        val response = loadPage(offset)
        check(response.code == 0) { "Dynamic author page failed: code=${response.code}" }
        page = checkNotNull(response.data) { "Dynamic author page is missing data" }
    }
}
