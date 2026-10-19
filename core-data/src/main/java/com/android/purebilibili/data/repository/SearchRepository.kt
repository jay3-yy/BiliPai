package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchDefaultData
import com.android.purebilibili.data.model.response.SearchArticleItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.model.response.SearchPhotoItem
import com.android.purebilibili.data.model.response.SearchSuggestTag
import com.android.purebilibili.data.model.response.SearchTopicItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import com.android.purebilibili.data.model.response.SearchVideoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchTrendingBundle(
    val pinnedItems: List<HotItem> = emptyList(),
    val items: List<HotItem> = emptyList()
) {
    val allItems: List<HotItem>
        get() = pinnedItems + items
}

data class SearchAllResults(
    val videos: List<VideoItem> = emptyList(),
    val ups: List<SearchUpItem> = emptyList(),
    val bangumi: List<com.android.purebilibili.data.model.response.BangumiSearchItem> = emptyList(),
    val pageInfo: SearchRepository.SearchPageInfo
)

internal fun buildSearchRecommendParams(
    accessToken: String?,
    accessTokenPlatform: String,
    timestampSeconds: Long
): Map<String, String> {
    val params = linkedMapOf(
        "build" to "8430300",
        "channel" to "master",
        "version" to "8.43.0",
        "c_locale" to "zh_CN",
        "mobi_app" to "android",
        "platform" to "android",
        "s_locale" to "zh_CN",
        "from" to "2",
        "ts" to timestampSeconds.toString()
    )
    accessToken?.takeIf { it.isNotBlank() }?.let { params["access_key"] = it }
    return if (!accessToken.isNullOrBlank() &&
        accessTokenPlatform == TokenManager.ACCESS_TOKEN_PLATFORM_TV
    ) {
        AppSignUtils.signForTvApi(params)
    } else {
        AppSignUtils.signForAndroidHdLogin(params)
    }
}

object SearchRepository {
    private val api = NetworkModule.searchApi
    private val searchJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }
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
        fallbackResultCount: Int,
        pageSize: Int
    ): SearchPageInfo = resolveVideoSearchPageInfo(
        requestedPage = requestedPage,
        responsePage = responsePage,
        totalPages = totalPages,
        totalResults = totalResults,
        pageSize = pageSize,
        resultCount = fallbackResultCount
    )

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
            "page_size" to "30",
            "platform" to "pc",
            "web_location" to "1430654"
        ).apply { putAll(extra) }
    }

    /** 综合页按站点 search/all/v2 返回的分类结构，优先展示视频、UP 主和番剧。 */
    suspend fun searchAll(keyword: String, page: Int = 1): Result<SearchAllResults> = withContext(Dispatchers.IO) {
        try {
            val params = signWithWbi(
                mapOf(
                    "keyword" to keyword,
                    "search_type" to "all",
                    "page" to page.toString(),
                    "page_size" to "20",
                    "platform" to "pc",
                    "web_location" to "1430654"
                )
            )
            val response = api.searchAll(params)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val categories = response.data?.result.orEmpty()
            val videos = categories.firstOrNull { it.result_type == "video" }
                ?.data.orEmpty()
                .mapNotNull { runCatching { searchJson.decodeFromJsonElement<SearchVideoItem>(it).toVideoItem() }.getOrNull() }
            val ups = categories.firstOrNull { it.result_type == "bili_user" }
                ?.data.orEmpty()
                .mapNotNull { runCatching { searchJson.decodeFromJsonElement<SearchUpItem>(it).cleanupFields() }.getOrNull() }
            val bangumi = categories.filter { it.result_type == "media_bangumi" || it.result_type == "media_ft" }
                .flatMap { it.data.orEmpty() }
                .mapNotNull { runCatching { searchJson.decodeFromJsonElement<com.android.purebilibili.data.model.response.BangumiSearchItem>(it) }.getOrNull() }
                .map { item ->
                    item.copy(
                        title = com.android.purebilibili.data.model.response.cleanSearchText(item.title),
                        cover = com.android.purebilibili.data.model.response.normalizeSearchImageUrl(item.cover)
                    )
                }
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: videos.size + ups.size + bangumi.size,
                fallbackResultCount = videos.size + ups.size + bangumi.size,
                pageSize = response.data?.pagesize ?: 20
            )
            Result.success(SearchAllResults(videos, ups, bangumi, pageInfo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //  视频搜索 - 支持排序、时长/分区/发布时间过滤和分页
    suspend fun search(
        keyword: String,
        order: SearchOrder = SearchOrder.TOTALRANK,
        duration: SearchDuration = SearchDuration.ALL,
        tids: Int = 0,
        page: Int = 1,
        pubBegin: Long? = null,
        pubEnd: Long? = null
    ): Result<Pair<List<VideoItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf(
                "keyword" to keyword,
                "search_type" to "video",
                "order" to order.value,
                "duration" to duration.value.toString(),
                "page" to page.toString(),
                "page_size" to "30",
                "platform" to "pc",
                "web_location" to "1430654"
            )
            if (tids != 0) {
                params["tids"] = tids.toString()
            }
            if (pubBegin != null) {
                params["pubtime_begin_s"] = pubBegin.toString()
            }
            if (pubEnd != null) {
                params["pubtime_end_s"] = pubEnd.toString()
            }

            com.android.purebilibili.core.network.CoreDataLog.d(
                "SearchRepo",
                " search(video): keyword=$keyword, order=${order.value}, duration=${duration.value}, tids=$tids, pubBegin=$pubBegin, pubEnd=$pubEnd, page=$page"
            )

            val signedParams = signWithWbi(params)

            val response = api.search(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }

            val videoList = response.data?.result
                ?.map { it.toVideoItem() }
                ?: emptyList()
            val pageInfo = resolveVideoSearchPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: videoList.size,
                pageSize = response.data?.pagesize ?: 20,
                resultCount = videoList.size
            )
            
            com.android.purebilibili.core.network.CoreDataLog.d(
                "SearchRepo",
                " search(video) result: size=${videoList.size}, page=${pageInfo.currentPage}/${pageInfo.totalPages}, hasMore=${pageInfo.hasMore}"
            )

            Result.success(Pair(videoList, pageInfo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            com.android.purebilibili.core.network.CoreDataLog.e("SearchRepo", "search(video) failed", e)
            Result.failure(e)
        }
    }

    suspend fun searchWithDurations(
        keyword: String,
        order: SearchOrder = SearchOrder.TOTALRANK,
        durations: Set<SearchDuration> = emptySet(),
        tids: Int = 0,
        page: Int = 1,
        pubBegin: Long? = null,
        pubEnd: Long? = null
    ): Result<Pair<List<VideoItem>, SearchPageInfo>> {
        val requests = resolveSearchDurationRequests(durations)
        if (requests.size == 1) {
            return search(
                keyword = keyword,
                order = order,
                duration = requests.single(),
                tids = tids,
                page = page,
                pubBegin = pubBegin,
                pubEnd = pubEnd
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
                page = page,
                pubBegin = pubBegin,
                pubEnd = pubEnd
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

    /** PiliPlus 综合页无筛选时用 all/v2；排序或筛选后切到 video/type API。 */
    suspend fun searchAllPage(
        keyword: String,
        page: Int = 1,
        order: SearchOrder = SearchOrder.TOTALRANK,
        durations: Set<SearchDuration> = emptySet(),
        tids: Int = 0,
        pubBegin: Long? = null,
        pubEnd: Long? = null,
    ): Result<SearchAllResults> {
        val hasVideoFilters = order != SearchOrder.TOTALRANK || durations.isNotEmpty() ||
            tids != 0 || pubBegin != null || pubEnd != null
        if (!hasVideoFilters) return searchAll(keyword, page)

        return searchWithDurations(
            keyword = keyword,
            order = order,
            durations = durations,
            tids = tids,
            page = page,
            pubBegin = pubBegin,
            pubEnd = pubEnd,
        ).map { (videos, pageInfo) -> SearchAllResults(videos = videos, pageInfo = pageInfo) }
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: upList.size,
                fallbackResultCount = upList.size,
                pageSize = response.data?.pagesize ?: 0
            )
            
            com.android.purebilibili.core.network.CoreDataLog.d(
                "SearchRepo",
                " search(up): size=${upList.size}, page=${pageInfo.currentPage}/${pageInfo.totalPages}, hasMore=${pageInfo.hasMore}"
            )

            Result.success(Pair(upList, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            com.android.purebilibili.core.network.CoreDataLog.e("SearchRepo", "UP Search failed", e)
            Result.failure(e)
        }
    }

    // 默认搜索占位词
    suspend fun getDefaultSearchHint(): Result<SearchDefaultData> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(emptyMap())
            val wbiResp = api.getDefaultSearch(signedParams)
            if (wbiResp.code == 0) {
                val hint = wbiResp.data
                if (hint != null && hint.keyword.isNotEmpty()) {
                    return@withContext Result.success(hint)
                }
            }

            val legacyResp = api.getDefaultSearchLegacy()
            if (legacyResp.code == 0) {
                val hint = legacyResp.data
                if (hint != null && hint.keyword.isNotEmpty()) {
                    return@withContext Result.success(hint)
                }
            }

            Result.failure(Exception("获取默认搜索词失败"))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    //  热搜榜单（BiliPai 同源）
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: bangumiList.size,
                fallbackResultCount = bangumiList.size,
                pageSize = response.data?.pageSize ?: 0
            )
            
            com.android.purebilibili.core.network.CoreDataLog.d("SearchRepo", "🔍 Bangumi search result: ${bangumiList.size} items, page ${pageInfo.currentPage}/${pageInfo.totalPages}")

            Result.success(Pair(bangumiList, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: resultList.size,
                fallbackResultCount = resultList.size,
                pageSize = response.data?.pageSize ?: 0
            )

            com.android.purebilibili.core.network.CoreDataLog.d(
                "SearchRepo",
                "🔍 MediaFT search result: ${resultList.size} items, page ${pageInfo.currentPage}/${pageInfo.totalPages}"
            )

            Result.success(Pair(resultList, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: liveList.size,
                fallbackResultCount = liveList.size,
                pageSize = response.data?.pagesize ?: 0
            )
            
            com.android.purebilibili.core.network.CoreDataLog.d("SearchRepo", "🔍 Live search result: ${liveList.size} rooms, page ${pageInfo.currentPage}/${pageInfo.totalPages}")

            Result.success(Pair(liveList, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchArticle(
        keyword: String,
        page: Int = 1,
        order: SearchOrder = SearchOrder.TOTALRANK,
        categoryId: Int = 0
    ): Result<Pair<List<SearchArticleItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val params = searchTypeParams(
                keyword = keyword,
                searchType = "article",
                page = page,
                extra = mapOf(
                    "order" to order.value,
                    "category_id" to categoryId.toString()
                )
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: articleList.size,
                fallbackResultCount = articleList.size,
                pageSize = response.data?.pagesize ?: 0
            )

            Result.success(Pair(articleList, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size,
                pageSize = response.data?.pagesize ?: 0
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size,
                pageSize = response.data?.pagesize ?: 0
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchPhoto(
        keyword: String,
        page: Int = 1,
        order: SearchOrder = SearchOrder.TOTALRANK,
        categoryId: Int = 0
    ): Result<Pair<List<SearchPhotoItem>, SearchPageInfo>> = withContext(Dispatchers.IO) {
        try {
            val signedParams = signWithWbi(
                searchTypeParams(
                    keyword = keyword,
                    searchType = "photo",
                    page = page,
                    extra = mapOf(
                        "order" to order.value,
                        "category_id" to categoryId.toString()
                    )
                )
            )
            val response = api.searchPhoto(signedParams)
            if (response.code != 0) {
                return@withContext Result.failure(createSearchError(response.code, response.message))
            }
            val items = response.data?.result?.map { it.cleanupFields() } ?: emptyList()
            val pageInfo = createPageInfo(
                requestedPage = page,
                responsePage = response.data?.page ?: page,
                totalPages = response.data?.numPages ?: 0,
                totalResults = response.data?.numResults ?: items.size,
                fallbackResultCount = items.size,
                pageSize = response.data?.pagesize ?: 0
            )
            Result.success(Pair(items, pageInfo))
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 保持官方推荐的原始顺序；关闭个性化时只使用公开热搜，不请求推荐接口。
    suspend fun getSearchRecommend(personalizedEnabled: Boolean): Result<List<HotItem>> {
        if (!personalizedEnabled) {
            return getTrendingKeywords(limit = 12).map { bundle -> bundle.allItems.take(12) }
        }
        return withContext(Dispatchers.IO) {
            try {
                TokenManager.awaitRestore()
                val response = api.getSearchRecommend(
                    buildSearchRecommendParams(
                        accessToken = TokenManager.accessTokenCache,
                        accessTokenPlatform = TokenManager.accessTokenPlatformCache,
                        timestampSeconds = AppSignUtils.getTimestamp()
                    )
                )
                if (response.code != 0) {
                    return@withContext Result.failure(createSearchError(response.code, response.message))
                }
                Result.success(response.data?.list.orEmpty().filter { item ->
                    item.keyword.isNotBlank() || item.show_name.isNotBlank()
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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
                com.android.purebilibili.core.network.CoreDataLog.w(
                    "SearchRepo",
                    "signWithWbi: missing img/sub key, use unsigned params"
                )
                params
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            com.android.purebilibili.core.network.CoreDataLog.e(
                "SearchRepo",
                "signWithWbi: failed to load nav/wbi keys, use unsigned params",
                e
            )
            params
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
    STOW("stow", "收藏最多"),
    SCORES("scores", "评论最多"),
    ATTENTION("attention", "喜欢最多")
}

enum class SearchArticleCategory(val value: Int, val displayName: String) {
    ALL(0, "全部分区"),
    GAME(1, "游戏"),
    ANIMATION(2, "动画"),
    LIFE(3, "生活"),
    LIGHT_NOVEL(16, "轻小说"),
    TECHNOLOGY(17, "科技"),
    MOVIE(28, "影视"),
    INTEREST(29, "兴趣")
}

enum class SearchPhotoCategory(val value: Int, val displayName: String) {
    ALL(0, "全部分区"),
    ILLUSTRATION(1, "画友"),
    PHOTOGRAPHY(2, "摄影")
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
