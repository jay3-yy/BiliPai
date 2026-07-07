package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchArticleItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.model.response.SearchPhotoItem
import com.android.purebilibili.data.model.response.SearchSuggestTag
import com.android.purebilibili.data.model.response.SearchTopicItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchTrendingBundle(
    val pinnedItems: List<HotItem> = emptyList(),
    val items: List<HotItem> = emptyList()
) {
    val allItems: List<HotItem>
        get() = pinnedItems + items
}

object SearchRepository {
    private val api = NetworkModule.searchApi
    private val navApi = NetworkModule.api

    //  [新增] 搜索分页信息
    data class SearchPageInfo(
        val currentPage: Int,
        val totalPages: Int,
        val totalResults: Int,
        val hasMore: Boolean
    )

    private fun createPageInfo(
        requestedPage: Int,
        responsePage: Int,
        totalPages: Int,
        totalResults: Int,
        fallbackResultCount: Int
    ): SearchPageInfo {
        val resolvedPage = resolveSearchLoadedPage(
            requestedPage = requestedPage,
            responsePage = responsePage
        )
        val resolvedTotalPages = totalPages.takeIf { it > 0 } ?: 1
        return SearchPageInfo(
            currentPage = resolvedPage,
            totalPages = resolvedTotalPages,
            totalResults = totalResults.takeIf { it > 0 } ?: fallbackResultCount,
            hasMore = resolvedPage < resolvedTotalPages
        )
    }

    private fun searchTypeParams(
        keyword: String,
        searchType: String,
        page: Int,
        extra: Map<String, String> = emptyMap()
    ): MutableMap<String, String> {
        return mutableMapOf(
            "keyword" to keyword,
            "search_type" to searchType,
            "page" to page.toString(),
            "page_size" to "20",
            "platform" to "pc",
            "web_location" to "1430654"
        ).apply { putAll(extra) }
    }

    //  视频搜索 - 支持排序、时长过滤和分页
    suspend fun search(
        keyword: String,
        order: SearchOrder = SearchOrder.TOTALRANK,
        duration: SearchDuration = SearchDuration.ALL,
        tids: Int = 0,
        page: Int = 1
    ): Result<Pair<List<VideoItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf(
                "keyword" to keyword,
                "search_type" to "video",
                "order" to order.value,
                "duration" to duration.value.toString(),
                "tids" to tids.toString(),
                "page" to page.toString(),
                "page_size" to "20",
                "platform" to "pc",
                "web_location" to "1430654"
            )
            
            com.android.purebilibili.core.util.Logger.d(
                "SearchRepo",
                " search(video): keyword=$keyword, order=${order.value}, duration=${duration.value}, tids=$tids, page=$page"
            )

            val signedParams = signWithWbi(params)

            val response = api.search(signedParams)
            if (response.code != 0) {
                com.android.purebilibili.core.util.Logger.w(
                    "SearchRepo",
                    "search(video) primary api failed: code=${response.code}, msg=${response.message}, fallback=all/v2"
                )
                return@withContext searchVideoFallback(keyword = keyword, page = page)
            }
            
            val videoList = response.data?.result
                ?.map { it.toVideoItem() }
                ?: emptyList()
            if (shouldFallbackEmptyFirstPageVideoSearch(page = page, primaryResultCount = videoList.size)) {
                com.android.purebilibili.core.util.Logger.d(
                    "SearchRepo",
                    " search(video) primary first page empty, fallback=all/v2"
                )
                return@withContext searchVideoFallback(keyword = keyword, page = page)
            }
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: videoList.size,
                fallbackResultCount = videoList.size
            )
            
            com.android.purebilibili.core.util.Logger.d(
                "SearchRepo",
                " search(video) result: size=${videoList.size}, page=${pageInfo.currentPage}/${pageInfo.totalPages}, hasMore=${pageInfo.hasMore}"
            )

            Result.success(Pair(videoList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            com.android.purebilibili.core.util.Logger.e(
                "SearchRepo",
                "search(video) primary api exception, fallback=all/v2",
                e
            )
            searchVideoFallback(keyword = keyword, page = page)
        }
    }

    suspend fun searchWithDurations(
        keyword: String,
        order: SearchOrder = SearchOrder.TOTALRANK,
        durations: Set<SearchDuration> = emptySet(),
        tids: Int = 0,
        page: Int = 1
    ): Result<Pair<List<VideoItem>, SearchPageInfo>> {
        val requests = resolveSearchDurationRequests(durations)
        if (requests.size == 1) {
            return search(
                keyword = keyword,
                order = order,
                duration = requests.single(),
                tids = tids,
                page = page
            )
        }

        val pages = mutableListOf<Pair<List<VideoItem>, SearchPageInfo>>()
        var firstFailure: Throwable? = null
        for (duration in requests) {
            search(
                keyword = keyword,
                order = order,
                duration = duration,
                tids = tids,
                page = page
            ).fold(
                onSuccess = { pages += it },
                onFailure = { error ->
                    if (firstFailure == null) firstFailure = error
                }
            )
        }

        return if (pages.isNotEmpty()) {
            Result.success(mergeSearchDurationResultPages(pages))
        } else {
            Result.failure(firstFailure ?: IllegalStateException("搜索失败"))
        }
    }
    
    //  UP主 搜索
    suspend fun searchUp(
        keyword: String,
        page: Int = 1,
        order: SearchUpOrder = SearchUpOrder.DEFAULT,
        orderSort: SearchOrderSort = SearchOrderSort.DESC,
        userType: SearchUserType = SearchUserType.ALL
    ): Result<Pair<List<SearchUpItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "bili_user",
                page = page,
                extra = mapOf(
                    "order" to order.value,
                    "order_sort" to orderSort.value.toString(),
                    "user_type" to userType.value.toString()
                )
            )

            val signedParams = signWithWbi(params)

            val response = api.searchUp(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            
            val upList = response.data?.result
                ?.map { it.cleanupFields() }
                ?: emptyList()

            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: upList.size,
                fallbackResultCount = upList.size
            )
            
            com.android.purebilibili.core.util.Logger.d(
                "SearchRepo",
                " search(up): size=${upList.size}, page=${pageInfo.currentPage}/${pageInfo.totalPages}, hasMore=${pageInfo.hasMore}"
            )

            Result.success(Pair(upList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            com.android.purebilibili.core.util.Logger.e("SearchRepo", "UP Search failed", e)
            Result.failure(e)
        }
    }

    // 默认搜索占位词
    suspend fun getDefaultSearchHint(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(emptyMap())
            val wbiResp = api.getDefaultSearch(signedParams)
            if (wbiResp.code == 0) {
                val hint = wbiResp.data?.showName?.trim().orEmpty()
                if (hint.isNotEmpty()) {
                    return@withContext Result.success(hint)
                }
            }

            val legacyResp = api.getDefaultSearchLegacy()
            if (legacyResp.code == 0) {
                val hint = legacyResp.data?.showName?.trim().orEmpty()
                if (hint.isNotEmpty()) {
                    return@withContext Result.success(hint)
                }
            }

            Result.failure(Exception("获取默认搜索词失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //  热搜榜单（PiliPlus 同源）
    suspend fun getTrendingKeywords(limit: Int = 30): Result<SearchTrendingBundle> = withContext(Dispatchers.IO) {
        try {
            val wbiResponse = runCatching {
                api.getHotSearch(
                    signWithWbi(
                        mapOf("limit" to limit.toString())
                    )
                )
            }.getOrNull()
            if (wbiResponse?.code == 0) {
                val wbiItems = wbiResponse.data
                    ?.trending
                    ?.list
                    ?.filter { item -> item.keyword.isNotBlank() || item.show_name.isNotBlank() }
                    .orEmpty()
                if (wbiItems.isNotEmpty()) {
                    return@withContext Result.success(
                        SearchTrendingBundle(items = wbiItems.take(limit))
                    )
                }
            }

            val response = api.getTrendingList(limit)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val pinnedItems = response.topList ?: response.data?.topList ?: emptyList()
            val items = response.list ?: response.data?.list ?: emptyList()
            Result.success(
                SearchTrendingBundle(
                    pinnedItems = pinnedItems,
                    items = items
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  [新增] 番剧搜索
    suspend fun searchBangumi(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<com.android.purebilibili.data.model.response.BangumiSearchItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "media_bangumi",
                page = page
            )
            
            val signedParams = signWithWbi(params)

            val response = api.searchBangumi(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            
            val bangumiList = response.data?.result?.map { item ->
                // 清理 HTML 标签
                item.copy(
                    title = com.android.purebilibili.data.model.response.cleanSearchText(item.title),
                    cover = com.android.purebilibili.data.model.response.normalizeSearchImageUrl(item.cover)
                )
            } ?: emptyList()
            
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: bangumiList.size,
                fallbackResultCount = bangumiList.size
            )
            
            com.android.purebilibili.core.util.Logger.d("SearchRepo", "🔍 Bangumi search result: ${bangumiList.size} items, page ${pageInfo.currentPage}/${pageInfo.totalPages}")

            Result.success(Pair(bangumiList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    //  [新增] 影视搜索
    suspend fun searchMediaFt(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<com.android.purebilibili.data.model.response.BangumiSearchItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "media_ft",
                page = page
            )

            val signedParams = signWithWbi(params)
            val response = api.searchMediaFt(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }

            val resultList = response.data?.result?.map { item ->
                item.copy(
                    title = com.android.purebilibili.data.model.response.cleanSearchText(item.title),
                    cover = com.android.purebilibili.data.model.response.normalizeSearchImageUrl(item.cover)
                )
            } ?: emptyList()

            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: resultList.size,
                fallbackResultCount = resultList.size
            )

            com.android.purebilibili.core.util.Logger.d(
                "SearchRepo",
                "🔍 MediaFT search result: ${resultList.size} items, page ${pageInfo.currentPage}/${pageInfo.totalPages}"
            )

            Result.success(Pair(resultList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  [新增] 直播搜索
    suspend fun searchLive(
        keyword: String,
        page: Int = 1,
        order: SearchLiveOrder = SearchLiveOrder.ONLINE
    ): Result<Pair<List<LiveRoomSearchItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "live_room",
                page = page,
                extra = mapOf("order" to order.value)
            )
            
            val signedParams = signWithWbi(params)

            val response = api.searchLive(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            
            val liveList = response.data?.result?.map { it.cleanupFields() } ?: emptyList()
            
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: liveList.size,
                fallbackResultCount = liveList.size
            )
            
            com.android.purebilibili.core.util.Logger.d("SearchRepo", "🔍 Live search result: ${liveList.size} rooms, page ${pageInfo.currentPage}/${pageInfo.totalPages}")

            Result.success(Pair(liveList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchArticle(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<SearchArticleItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "article",
                page = page
            )

            val signedParams = signWithWbi(params)
            val response = api.searchArticle(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }

            val articleList = response.data?.result
                ?.map { it.cleanupFields() }
                ?: emptyList()

            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: articleList.size,
                fallbackResultCount = articleList.size
            )

            Result.success(Pair(articleList, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchLiveUser(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<SearchLiveUserItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(searchTypeParams(keyword, "live_user", page))
            val response = api.searchLiveUser(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val items = response.data?.result?.map { it.cleanupFields() } ?: emptyList()
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchTopic(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<SearchTopicItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(searchTypeParams(keyword, "topic", page))
            val response = api.searchTopic(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val items = response.data?.result?.map { it.cleanupFields() } ?: emptyList()
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchPhoto(
        keyword: String,
        page: Int = 1
    ): Result<Pair<List<SearchPhotoItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(searchTypeParams(keyword, "photo", page))
            val response = api.searchPhoto(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val items = response.data?.result?.map { it.cleanupFields() } ?: emptyList()
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 1,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  搜索建议/联想
    suspend fun getSuggest(keyword: String): Result<List<SearchSuggestTag>> = withContext(Dispatchers.IO) {
        try {
            if (keyword.isBlank()) return@withContext Result.success(emptyList())
            
            val response = api.getSearchSuggest(keyword)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, "搜索建议加载失败"))
            }
            val suggestions = response.result?.tag
                ?.filter { tag ->
                    tag.term.isNotBlank() || tag.value.isNotBlank() || tag.name.isNotBlank()
                }
                ?: emptyList()
            Result.success(suggestions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    //  获取搜索发现（优先最近搜索/关注 UP，再补官方推荐和热搜）
    suspend fun getSearchRecommend(historyKeywords: List<String>): Result<List<HotItem>> = withContext(Dispatchers.IO) {
        val fallbackKeywords = listOf("黑神话悟空", "原神", "初音未来", "JOJO", "罗翔说刑法", "何同学", "毕业季", "猫咪", "我的世界", "战鹰")
        val historySuggestions = try {
            val lastKeyword = historyKeywords.firstOrNull()
            if (!lastKeyword.isNullOrBlank()) {
                val response = api.getSearchSuggest(lastKeyword)
                response.result?.tag
                    ?.mapNotNull { tag ->
                        tag.term.ifBlank { tag.value.ifBlank { tag.name } }
                            .replace(Regex("<.*?>"), "")
                            .trim()
                            .takeIf { it.isNotBlank() && it != lastKeyword }
                    }
                    ?.take(8)
                    .orEmpty()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        val followedUpNames = try {
            val navResponse = navApi.getNavInfo()
            val mid = navResponse.data?.mid ?: 0L
            if (navResponse.data?.isLogin == true && mid > 0L) {
                navApi.getFollowings(mid, pn = 1, ps = 20)
                    .data
                    ?.list
                    ?.mapNotNull { user -> user.uname.trim().takeIf { it.isNotBlank() } }
                    .orEmpty()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        val officialItems = try {
            val recommendResponse = api.getSearchRecommend()
            if (recommendResponse.code == 0) {
                recommendResponse.data?.list
                    ?.filter { item -> item.keyword.isNotBlank() || item.show_name.isNotBlank() }
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        val trendingItems = getTrendingKeywords(limit = 12).getOrNull()?.allItems?.shuffled()?.take(10).orEmpty()
        val items = buildSearchRecommendItems(
            historySuggestionKeywords = historySuggestions,
            followedUpNames = followedUpNames,
            officialItems = officialItems,
            trendingItems = trendingItems,
            fallbackKeywords = fallbackKeywords,
            limit = 10
        )

        Result.success(items)
    }

    private suspend fun signWithWbi(params: Map<String, String>): Map<String, String> {
        return try {
            val navResp = navApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                WbiUtils.sign(params, imgKey, subKey)
            } else {
                com.android.purebilibili.core.util.Logger.w(
                    "SearchRepo",
                    "signWithWbi: missing img/sub key, use unsigned params"
                )
                params
            }
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(
                "SearchRepo",
                "signWithWbi: failed to load nav/wbi keys, use unsigned params",
                e
            )
            params
        }
    }

    private suspend fun searchVideoFallback(
        keyword: String,
        page: Int
    ): Result<Pair<List<VideoItem>, SearchPageInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.searchAll(
                    signWithWbi(
                        mapOf(
                        "keyword" to keyword,
                        "page" to page.toString(),
                        "page_size" to "20",
                        "platform" to "pc",
                        "web_location" to "1430654"
                        )
                    )
                )
                if (response.code != 0) {
                    return@withContext Result.failure(createSearchError(response.code, response.message))
                }

                val videos = response.data?.result
                    ?.firstOrNull { it.result_type == "video" }
                    ?.data
                    ?.map { it.toVideoItem() }
                    ?: emptyList()

                val pageInfo = SearchPageInfo(
                    currentPage = page,
                    totalPages = response.data?.numPages?.takeIf { it > 0 } ?: if (videos.size >= 20) page + 1 else page,
                    totalResults = response.data?.numResults?.takeIf { it > 0 } ?: videos.size,
                    hasMore = response.data?.numPages?.let { page < it } ?: (videos.size >= 20)
                )

                com.android.purebilibili.core.util.Logger.d(
                    "SearchRepo",
                    "search(video) fallback result: size=${videos.size}, page=${pageInfo.currentPage}, hasMore=${pageInfo.hasMore}"
                )

                Result.success(Pair(videos, pageInfo))
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    private fun createSearchError(code: Int, message: String): Exception {
        val readable = when (code) {
            -412 -> "搜索请求被拦截，请稍后重试"
            -400 -> "搜索参数错误"
            -404 -> "搜索接口不存在"
            -1200 -> "搜索类型不存在或参数被降级过滤"
            else -> message.ifBlank { "搜索失败 ($code)" }
        }
        return Exception(readable)
    }
}

//  搜索排序选项
enum class SearchOrder(val value: String, val displayName: String) {
    TOTALRANK("totalrank", "综合排序"),
    PUBDATE("pubdate", "最新发布"),
    CLICK("click", "播放最多"),
    DM("dm", "弹幕最多"),
    STOW("stow", "收藏最多")
}

//  搜索时长筛选
enum class SearchDuration(val value: Int, val displayName: String) {
    ALL(0, "全部时长"),
    UNDER_10MIN(1, "10分钟以下"),
    TEN_TO_30MIN(2, "10-30分钟"),
    THIRTY_TO_60MIN(3, "30-60分钟"),
    OVER_60MIN(4, "60分钟以上")
}

enum class SearchUpOrder(val value: String, val displayName: String) {
    DEFAULT("0", "默认排序"),
    FANS("fans", "粉丝数"),
    LEVEL("level", "用户等级")
}

enum class SearchOrderSort(val value: Int, val displayName: String) {
    DESC(0, "从高到低"),
    ASC(1, "从低到高")
}

enum class SearchUserType(val value: Int, val displayName: String) {
    ALL(0, "全部类型"),
    UP(1, "仅UP主"),
    NORMAL(2, "普通用户"),
    VERIFIED(3, "认证用户")
}

enum class SearchLiveOrder(val value: String, val displayName: String) {
    ONLINE("online", "人气直播"),
    LIVE_TIME("live_time", "最新开播")
}
