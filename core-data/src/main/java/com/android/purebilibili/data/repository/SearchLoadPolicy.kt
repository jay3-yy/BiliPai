package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.VideoItem

/** all/v2 的用户卡是精选结果；没有卡片不代表用户分类没有匹配项。 */
internal suspend fun resolveSearchAllUpPreview(
    page: Int,
    userResultCount: Int,
    featuredUsers: List<SearchUpItem>,
    loadUsers: suspend () -> List<SearchUpItem>
): List<SearchUpItem> {
    if (page != 1 || featuredUsers.isNotEmpty() || userResultCount <= 0) return featuredUsers
    // 综合页只预览相关性排序前三项，完整结果、排序和分页仍由 UP 分类承载。
    return buildList(3) {
        for (user in loadUsers()) {
            if (none { it.mid == user.mid }) add(user)
            if (size == 3) break
        }
    }
}

/** 风控也可能以 code=0 返回，不能把验证码响应展示成“没有搜索结果”。 */
internal fun requireSearchPayload(hasData: Boolean, riskVoucher: String?) {
    check(riskVoucher.isNullOrBlank()) { "搜索请求需要验证，请在哔哩哔哩网页完成验证后重试" }
    check(hasData) { "搜索接口未返回结果数据，请重试" }
}

fun resolveVideoSearchPageInfo(
    requestedPage: Int,
    responsePage: Int,
    totalPages: Int,
    totalResults: Int,
    pageSize: Int,
    resultCount: Int
): SearchRepository.SearchPageInfo {
    val currentPage = resolveSearchLoadedPage(requestedPage, responsePage)
    val size = pageSize.takeIf { it > 0 } ?: 20
    val resolvedTotalPages = when {
        totalPages > 0 -> totalPages
        totalResults > 0 -> ((totalResults.toLong() + size - 1) / size).toInt()
        resultCount >= size -> currentPage + 1
        else -> currentPage
    }
    return SearchRepository.SearchPageInfo(
        currentPage = currentPage,
        totalPages = resolvedTotalPages,
        totalResults = totalResults.takeIf { it > 0 } ?: resultCount,
        // 优先服务端总页数和有效分页大小，缺少元数据时保守探测下一页。
        hasMore = resultCount > 0 && when {
            totalPages > 0 -> currentPage < totalPages
            totalResults > 0 && pageSize > 0 -> currentPage.toLong() * pageSize < totalResults
            else -> true
        }
    )
}

fun resolveSearchLoadedPage(
    requestedPage: Int,
    responsePage: Int
): Int {
    return maxOf(requestedPage, responsePage.coerceAtLeast(1))
}

fun shouldApplySearchResult(
    requestSessionId: Long,
    activeSessionId: Long,
    requestQuery: String,
    activeQuery: String,
    requestType: SearchType,
    activeType: SearchType
): Boolean {
    return requestSessionId == activeSessionId &&
        requestQuery == activeQuery &&
        requestType == activeType
}

fun <T, K> mergeSearchPageResults(
    existing: List<T>,
    incoming: List<T>,
    keySelector: (T) -> K
): List<T> {
    val seen = LinkedHashSet<K>()
    val merged = ArrayList<T>(existing.size + incoming.size)
    for (item in existing) {
        if (seen.add(keySelector(item))) {
            merged += item
        }
    }
    for (item in incoming) {
        if (seen.add(keySelector(item))) {
            merged += item
        }
    }
    return merged
}

fun resolveSearchDurationRequests(
    durations: Set<SearchDuration>
): List<SearchDuration> {
    if (SearchDuration.ALL in durations) return listOf(SearchDuration.ALL)
    val normalized = durations.filter { it != SearchDuration.ALL }.distinct()
    return normalized.ifEmpty { listOf(SearchDuration.ALL) }
}

fun toggleSearchDurationSelection(
    current: Set<SearchDuration>,
    duration: SearchDuration
): Set<SearchDuration> {
    if (duration == SearchDuration.ALL) return emptySet()
    return if (duration in current) {
        current - duration
    } else {
        current + duration
    }
}

fun resolveSearchDurationFilterLabel(
    durations: Set<SearchDuration>
): String {
    val requests = resolveSearchDurationRequests(durations)
    return when (requests.size) {
        1 -> requests.single().displayName
        else -> "时长 ${requests.size} 项"
    }
}

fun mergeSearchDurationResultPages(
    pages: List<Pair<List<VideoItem>, SearchRepository.SearchPageInfo>>
): Pair<List<VideoItem>, SearchRepository.SearchPageInfo> {
    val merged = mergeSearchPageResults(
        existing = emptyList(),
        incoming = pages.flatMap { it.first },
        keySelector = ::resolveSearchVideoMergeKey
    )
    val pageInfos = pages.map { it.second }
    val pageInfo = SearchRepository.SearchPageInfo(
        currentPage = pageInfos.maxOfOrNull { it.currentPage } ?: 1,
        totalPages = pageInfos.maxOfOrNull { it.totalPages } ?: 1,
        totalResults = pageInfos.sumOf { it.totalResults },
        hasMore = pageInfos.any { it.hasMore }
    )
    return merged to pageInfo
}

private fun resolveSearchVideoMergeKey(video: VideoItem): String {
    return when {
        video.bvid.isNotBlank() -> "bvid:${video.bvid}"
        video.aid > 0L -> "aid:${video.aid}"
        video.id > 0L -> "id:${video.id}"
        else -> "title:${video.title}:${video.owner.mid}"
    }
}
