// 文件路径: data/repository/DynamicRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.OPUS_DETAIL_FEATURES
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.DynamicFeedResponse
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.feature.article.shouldFetchArticleFallbackForOpus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 *  动态数据仓库
 * 
 * 负责从 B站 API 获取动态 Feed 数据
 *
 * 分页语义对齐 bilibili-API-collect `docs/dynamic/all.md`：
 * - `offset`：翻页偏移，等于末条动态 id
 * - `update_baseline`：更新基线，等于首条动态 id；获取新动态时传入
 * - `update_num`：本次在更新基线以上的新动态条数
 */
object DynamicRepository {
    private data class AccountPagination(
        val identity: AccountSessionIdentity,
        val feed: DynamicFeedPaginationRegistry = DynamicFeedPaginationRegistry(),
        val users: DynamicUserPaginationRegistry = DynamicUserPaginationRegistry(),
    )
    private var accountPagination: AccountPagination? = null
    private val feedPagination get() = paginationForAccount().feed
    private val userFeedPagination get() = paginationForAccount().users
    private val detailSeeds = object : LinkedHashMap<String, DynamicItem>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DynamicItem>?): Boolean {
            return size > 32
        }
    }
    private val detailSeedsLock = Any()

    private fun paginationForAccount(): AccountPagination = synchronized(detailSeedsLock) {
        val identity = TokenManager.accountIdentity.value
        accountPagination?.takeIf { it.identity == identity } ?: AccountPagination(identity).also {
            accountPagination = it
            detailSeeds.clear()
        }
    }

    private suspend fun ensureCurrentAccount(identity: AccountSessionIdentity) {
        currentCoroutineContext().ensureActive()
        if (!isDynamicAccountRequestCurrent(identity, TokenManager.accountIdentity.value, TokenManager.midCache)) {
            throw IllegalStateException("账号已切换，请重新刷新动态")
        }
    }

    fun rememberDynamicDetailSeed(item: DynamicItem) {
        val id = item.id_str.trim()
        if (id.isEmpty()) return
        synchronized(detailSeedsLock) {
            paginationForAccount()
            detailSeeds[id] = item
        }
        item.orig?.let(::rememberDynamicDetailSeed)
    }

    fun peekDynamicDetailSeed(dynamicId: String): DynamicItem? {
        val id = dynamicId.trim()
        if (id.isEmpty()) return null
        return synchronized(detailSeedsLock) {
            paginationForAccount()
            detailSeeds[id]
        }
    }
    
    /**
     * 获取动态列表
     * @param refresh 是否刷新 (重置分页)
     * @param incrementalRefresh 是否保留现有时间线，仅拉取更新基线之后的内容
     */
    suspend fun getDynamicFeed(
        refresh: Boolean = false,
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        type: String = "all",
        incrementalRefresh: Boolean = false
    ): Result<DynamicFeedFetchResult> = withContext(Dispatchers.IO) {
        val account = paginationForAccount()
        val feedPagination = account.feed
        try {
            ensureCurrentAccount(account.identity)
            val paginationBeforeRefresh = feedPagination.snapshot(scope, type)
            val useIncrementalRefresh = shouldUseDynamicIncrementalRefresh(
                refresh = refresh,
                incrementalRefreshEnabled = incrementalRefresh,
                updateBaseline = paginationBeforeRefresh.updateBaseline
            )
            if (refresh && !useIncrementalRefresh) {
                feedPagination.reset(scope, type)
            }
            val paginationForPageUpdate = if (refresh && !useIncrementalRefresh) {
                DynamicPaginationState()
            } else {
                paginationBeforeRefresh
            }
            if (!feedPagination.hasMore(scope, type) && !refresh) {
                return@withContext Result.success(
                    DynamicFeedFetchResult(
                        items = emptyList(),
                        updateNum = 0,
                        usedUpdateBaseline = false,
                        nextOffset = feedPagination.offset(scope, type),
                        hasMore = false
                    )
                )
            }

            val page = loadDynamicPage(
                type = type,
                offset = if (refresh) "" else paginationBeforeRefresh.offset,
                updateBaseline = paginationBeforeRefresh.updateBaseline.takeIf { useIncrementalRefresh }.orEmpty()
            ).getOrThrow()
            ensureCurrentAccount(account.identity)
            feedPagination.updateState(
                scope = scope,
                type = type,
                state = resolveDynamicPaginationStateAfterPage(
                    paginationBeforeRefresh = paginationForPageUpdate,
                    responseOffset = page.nextOffset,
                    responseUpdateBaseline = page.updateBaseline,
                    responseHasMore = page.hasMore,
                    preserveExistingPagination = useIncrementalRefresh,
                    reportedUpdateNum = page.updateNum
                )
            )
            Result.success(page.copy(hasMore = feedPagination.hasMore(scope, type)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun currentUpdateBaseline(
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        type: String = "all"
    ): String = feedPagination.updateBaseline(scope, type)
    
    /**
     *  [新增] 获取指定用户的动态列表
     * @param hostMid UP主 mid
     * @param refresh 是否刷新 (重置分页)
     */
    suspend fun getUserDynamicFeed(hostMid: Long, refresh: Boolean = false): Result<List<DynamicItem>> = withContext(Dispatchers.IO) {
        val account = paginationForAccount()
        val userFeedPagination = account.users
        try {
            ensureCurrentAccount(account.identity)
            if (refresh) {
                userFeedPagination.reset(hostMid)
            }
            
            if (!userFeedPagination.hasMore(hostMid) && !refresh) {
                return@withContext Result.success(emptyList())
            }

            val page = loadDynamicPage(
                hostMid = hostMid,
                offset = userFeedPagination.offset(hostMid)
            ).getOrThrow()
            ensureCurrentAccount(account.identity)
            userFeedPagination.update(hostMid, offset = page.nextOffset, hasMore = page.hasMore)
            Result.success(page.items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** 无共享游标的页面请求；列表的继续加载与失败重试由调用方拥有。 */
    internal suspend fun loadDynamicPage(
        type: String = "all",
        offset: String = "",
        updateBaseline: String = "",
        hostMid: Long? = null,
    ): Result<DynamicFeedFetchResult> = withContext(Dispatchers.IO) {
        val identity = TokenManager.accountIdentity.value
        try {
            val blockGoods = goodsDynamicFilterEnabled()
            val items = mutableListOf<DynamicItem>()
            val useBaseline = hostMid == null && offset.isBlank() && updateBaseline.isNotBlank()
            var requestOffset = offset
            var baseline = updateBaseline
            var updateNum = 0
            var pagesFetched = 0
            var receivedItemCount = 0
            var hasMore: Boolean
            while (true) {
                val previousOffset = requestOffset
                val response = fetchDynamicFeedPageWithRetry {
                    ensureCurrentAccount(identity)
                    if (hostMid != null) {
                        NetworkModule.dynamicApi.getUserDynamicFeed(
                            params = buildSelectedUserDynamicFeedParams(hostMid, previousOffset)
                        )
                    } else {
                        NetworkModule.dynamicApi.getDynamicFeed(
                            type = type,
                            offset = previousOffset,
                            updateBaseline = updateBaseline.takeIf { useBaseline && pagesFetched == 0 }.orEmpty()
                        )
                    }
                }.getOrThrow()
                ensureCurrentAccount(identity)
                val data = response.data
                if (data == null) {
                    hasMore = false
                    break
                }
                if (pagesFetched == 0) updateNum = data.update_num.coerceAtLeast(0)
                baseline = resolveDynamicFeedUpdateBaseline(baseline, data.update_baseline, pagesFetched)
                requestOffset = data.offset
                hasMore = data.has_more
                receivedItemCount += data.items.size
                // 折叠卡片仍保留在时间线中，供展开操作恢复对应内容。
                items += if (blockGoods) data.items.filterNot(::isGoodsDynamic) else data.items
                pagesFetched += 1
                val fetchNext = if (useBaseline) {
                    shouldContinueDynamicIncrementalFetch(receivedItemCount, updateNum, hasMore, previousOffset, requestOffset)
                } else {
                    shouldContinueDynamicFetchAfterFilter(items.size, hasMore, previousOffset, requestOffset, pagesFetched)
                }
                if (!fetchNext) break
            }
            val enriched = DynamicCommentPreviewRepository.enrich(items)
            ensureCurrentAccount(identity)
            Result.success(DynamicFeedFetchResult(
                items = enriched,
                updateNum = updateNum,
                usedUpdateBaseline = useBaseline,
                nextOffset = requestOffset,
                hasMore = hasMore,
                updateBaseline = baseline
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /**
     * 获取单条动态详情。主路径对齐 PiliPlus 的 web `/v1/detail`，
     * 再按需降级到 opus、desktop，以及列表卡片缓存。
     */
    suspend fun getDynamicDetail(dynamicId: String): Result<DynamicItem> = withContext(Dispatchers.IO) {
        val account = paginationForAccount()
        try {
            ensureCurrentAccount(account.identity)
            val cleanedId = dynamicId.trim()
            if (cleanedId.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("dynamicId 不能为空"))
            }

            val seed = peekDynamicDetailSeed(cleanedId)
            val candidates = mutableListOf<DynamicItem>()

            val webItem = fetchWebDetailItem(id = cleanedId)
            webItem?.let(candidates::add)

            var opusFallbackCvId: Long? = null
            if (shouldRequestOpusDetailForDynamicDetail(webItem = webItem, seedItem = seed)) {
                val opusFetch = fetchOpusDetail(cleanedId)
                opusFetch.item?.let(candidates::add)
                opusFallbackCvId = opusFetch.fallbackCvId
            }

            val preferredAfterWeb = resolvePreferredDynamicDetailItem(candidates)
            if (preferredAfterWeb != null &&
                shouldFetchStandardDetailForPlainTextDynamic(preferredAfterWeb)
            ) {
                fetchDesktopDetailItem(cleanedId)?.let { desktopItem ->
                    candidates += mergeDynamicDetailWithLongerDesc(
                        desktopItem = preferredAfterWeb,
                        standardItem = desktopItem,
                    )
                }
            }

            if (preferredAfterWeb == null || shouldFallbackForDynamicDetail(preferredAfterWeb)) {
                fetchDesktopDetailItem(cleanedId)?.let(candidates::add)
            }

            val rid = seed?.basic?.rid_str.orEmpty()
            if (shouldFetchDynamicDetailByRid(resolvePreferredDynamicDetailItem(candidates), rid)) {
                fetchWebDetailItem(id = null, rid = rid, type = 2)?.let(candidates::add)
            }

            seed?.let(candidates::add)
            val resolved = resolvePreferredDynamicDetailItem(candidates)
            if (resolved != null) {
                var merged = mergeRicherOpusDetailContent(resolved, candidates)
                val opusBlocks = merged.modules.module_dynamic?.major?.opus?.contentBlocks.orEmpty()
                val cvId = resolveOpusArticleFallbackCvId(
                    fallbackId = opusFallbackCvId,
                    commentType = merged.basic?.comment_type ?: 0,
                    commentIdStr = merged.basic?.comment_id_str.orEmpty()
                )
                if (cvId != null && shouldFetchArticleFallbackForOpus(opusBlocks, opusFallbackCvId)) {
                    ArticleRepository.getArticleDetail(cvId).getOrNull()?.let { article ->
                        merged = mergeArticleDetailIntoOpus(
                            base = merged,
                            title = article.title,
                            blocks = article.blocks
                        )
                    }
                }
                val withMetadata = restoreLocatedDynamicCommentPreviewMetadata(
                    mergeDynamicDetailInteractionMetadata(detailItem = merged, seedItem = seed),
                    seed,
                )
                ensureCurrentAccount(account.identity)
                return@withContext Result.success(withMetadata)
            }

            Result.failure(Exception("动态详情为空"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun fetchWebDetailItem(
        id: String? = null,
        rid: String? = null,
        type: Int? = null
    ): DynamicItem? {
        return runCatching {
            val response = NetworkModule.dynamicApi.getDynamicDetail(
                id = id,
                rid = rid,
                type = type
            )
            response.data?.item?.takeIf { response.code == 0 }
        }.getOrNull()
    }

    private data class OpusDetailFetch(
        val item: DynamicItem?,
        val fallbackCvId: Long?
    )

    private suspend fun fetchOpusDetail(dynamicId: String): OpusDetailFetch {
        return runCatching {
            val response = NetworkModule.dynamicApi.getOpusDetail(
                signDynamicWbi(
                    mapOf(
                        "id" to dynamicId,
                        "timezone_offset" to "-480",
                        "features" to OPUS_DETAIL_FEATURES
                    )
                )
            )
            if (response.code != 0) {
                return@runCatching OpusDetailFetch(item = null, fallbackCvId = null)
            }
            OpusDetailFetch(
                item = response.data?.item,
                fallbackCvId = response.data?.fallback?.id?.takeIf { it > 0L }
            )
        }.getOrElse { error ->
            Logger.w(
                tag = "DynamicRepository",
                message = "解析图文动态全文失败: dynamicId=$dynamicId",
                throwable = error
            )
            OpusDetailFetch(item = null, fallbackCvId = null)
        }
    }

    private suspend fun signDynamicWbi(params: Map<String, String>): Map<String, String> {
        return try {
            val navResp = NetworkModule.api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                WbiUtils.sign(params, imgKey, subKey)
            } else {
                params
            }
        } catch (_: Exception) {
            params
        }
    }

    private suspend fun fetchDesktopDetailItem(dynamicId: String): DynamicItem? {
        return runCatching {
            val response = NetworkModule.dynamicApi.getDynamicDetailFallback(id = dynamicId)
            response.data?.item?.takeIf { response.code == 0 }
        }.getOrNull()
    }
    
    /**
     * 是否还有更多数据
     */
    fun hasMoreData(
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        type: String = "all"
    ): Boolean {
        return feedPagination.hasMore(scope, type)
    }

    internal fun snapshotPagination(scope: DynamicFeedScope, type: String = "all"): DynamicPaginationState =
        feedPagination.snapshot(scope, type)

    fun syncPaginationAfterRefresh(
        scope: DynamicFeedScope,
        type: String = "all",
        offset: String,
        updateBaseline: String = "",
        hasMore: Boolean = true
    ) {
        feedPagination.update(
            scope = scope,
            type = type,
            offset = offset,
            updateBaseline = updateBaseline.ifBlank { feedPagination.updateBaseline(scope, type) },
            hasMore = hasMore
        )
    }

    suspend fun getDynamicUpdateCount(
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        type: String = "all",
        advanceBaseline: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val updateBaseline = feedPagination.updateBaseline(scope, type)
            if (!advanceBaseline) {
                // 轻量未读接口：只返回新动态条数，避免轮询时拉全量 feed。
                val updateResponse = NetworkModule.dynamicApi.getDynamicUpdateCount(
                    type = type,
                    updateBaseline = updateBaseline
                )
                if (updateResponse.code != 0) {
                    return@withContext Result.failure(
                        Exception(
                            resolveDynamicFriendlyErrorMessage(
                                updateResponse.code,
                                updateResponse.message
                            )
                        )
                    )
                }
                val updateData = updateResponse.data
                    ?: return@withContext Result.failure(Exception("动态更新数为空"))
                return@withContext Result.success(updateData.update_num.coerceAtLeast(0))
            }
            val response = fetchDynamicFeedPageWithRetry {
                NetworkModule.dynamicApi.getDynamicFeed(
                    type = type,
                    offset = "",
                    updateBaseline = updateBaseline
                )
            }.getOrElse { error ->
                return@withContext Result.failure(error)
            }
            val data = response.data ?: return@withContext Result.failure(Exception("动态更新数为空"))
            val nextBaseline = resolveDynamicUpdateCountBaseline(
                currentBaseline = updateBaseline,
                responseBaseline = data.update_baseline,
                advanceBaseline = advanceBaseline
            )
            if (nextBaseline.isNotBlank() && nextBaseline != updateBaseline) {
                feedPagination.updateBaseline(
                    scope = scope,
                    type = type,
                    updateBaseline = nextBaseline
                )
            }
            Result.success(data.update_num.coerceAtLeast(0))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 用户动态是否还有更多
     */
    fun userHasMoreData(hostMid: Long?): Boolean {
        if (hostMid == null || hostMid <= 0L) return true
        return userFeedPagination.hasMore(hostMid)
    }
    
    /**
     * 重置分页状态
     */
    fun resetPagination(
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        type: String = "all"
    ) {
        feedPagination.reset(scope, type)
    }
    
    /**
     *  [新增] 重置用户动态分页状态
     */
    fun resetUserPagination(hostMid: Long? = null) {
        if (hostMid == null || hostMid <= 0L) {
            userFeedPagination.resetAll()
        } else {
            userFeedPagination.reset(hostMid)
        }
    }

    internal fun buildSelectedUserDynamicFeedParams(
        hostMid: Long,
        offset: String
    ): Map<String, String> {
        return mapOf(
            "host_mid" to hostMid.toString(),
            "offset" to offset,
            "features" to com.android.purebilibili.core.network.SPACE_DYNAMIC_FEATURES,
            "timezone_offset" to "-480",
            "platform" to "web",
            "web_location" to "333.1387"
        )
    }

    private suspend fun fetchDynamicFeedPageWithRetry(request: suspend () -> DynamicFeedResponse): Result<DynamicFeedResponse> =
        DynamicFeedPageDataSource.fetch(request)

}

internal fun resolveDynamicUpdateCountBaseline(
    currentBaseline: String,
    responseBaseline: String,
    advanceBaseline: Boolean
): String {
    if (responseBaseline.isBlank()) return currentBaseline
    if (advanceBaseline) return responseBaseline
    return currentBaseline
}

internal fun shouldUseDynamicIncrementalRefresh(
    refresh: Boolean,
    incrementalRefreshEnabled: Boolean,
    updateBaseline: String
): Boolean {
    return refresh && incrementalRefreshEnabled && updateBaseline.isNotBlank()
}

internal fun resolveDynamicPaginationStateAfterPage(
    paginationBeforeRefresh: DynamicPaginationState,
    responseOffset: String,
    responseUpdateBaseline: String,
    responseHasMore: Boolean,
    preserveExistingPagination: Boolean,
    reportedUpdateNum: Int = -1
): DynamicPaginationState {
    val nextBaseline = responseUpdateBaseline.ifBlank {
        paginationBeforeRefresh.updateBaseline
    }
    val canPreserve = preserveExistingPagination &&
        paginationBeforeRefresh.offset.isNotBlank() &&
        reportedUpdateNum != 0

    return if (canPreserve) {
        paginationBeforeRefresh.copy(updateBaseline = nextBaseline)
    } else {
        DynamicPaginationState(
            offset = responseOffset,
            updateBaseline = nextBaseline,
            hasMore = responseHasMore
        )
    }
}

internal fun isDynamicAccountRequestCurrent(
    requested: AccountSessionIdentity,
    current: AccountSessionIdentity,
    cachedMid: Long?,
): Boolean = requested == current && requested.mid == cachedMid

enum class DynamicFeedScope {
    DYNAMIC_SCREEN,
    HOME_FOLLOW
}

data class DynamicFeedFetchResult(
    val items: List<DynamicItem>,
    val updateNum: Int = 0,
    val usedUpdateBaseline: Boolean = false,
    val nextOffset: String = "",
    val hasMore: Boolean = true,
    val updateBaseline: String = ""
)

internal data class DynamicPaginationState(
    var offset: String = "",
    var updateBaseline: String = "",
    var hasMore: Boolean = true
)

internal data class DynamicFeedPaginationKey(
    val scope: DynamicFeedScope,
    val type: String
)

internal class DynamicFeedPaginationRegistry {
    private val stateByScope = mutableMapOf<DynamicFeedPaginationKey, DynamicPaginationState>()

    fun reset(scope: DynamicFeedScope, type: String = "all") {
        stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)] = DynamicPaginationState()
    }

    fun update(
        scope: DynamicFeedScope,
        type: String = "all",
        offset: String,
        updateBaseline: String = "",
        hasMore: Boolean
    ) {
        stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)] =
            DynamicPaginationState(
                offset = offset,
                updateBaseline = updateBaseline,
                hasMore = hasMore
            )
    }

    fun updateState(
        scope: DynamicFeedScope,
        type: String = "all",
        state: DynamicPaginationState
    ) {
        stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)] = state.copy()
    }

    fun snapshot(
        scope: DynamicFeedScope,
        type: String = "all"
    ): DynamicPaginationState {
        return stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)]?.copy()
            ?: DynamicPaginationState()
    }

    fun offset(scope: DynamicFeedScope, type: String = "all"): String {
        return stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)]?.offset.orEmpty()
    }

    fun updateBaseline(scope: DynamicFeedScope, type: String = "all"): String {
        return stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)]?.updateBaseline.orEmpty()
    }

    fun updateBaseline(
        scope: DynamicFeedScope,
        type: String = "all",
        updateBaseline: String
    ) {
        val key = DynamicFeedPaginationKey(scope = scope, type = type)
        val current = stateByScope[key] ?: DynamicPaginationState()
        stateByScope[key] = current.copy(updateBaseline = updateBaseline)
    }

    fun hasMore(scope: DynamicFeedScope, type: String = "all"): Boolean {
        return stateByScope[DynamicFeedPaginationKey(scope = scope, type = type)]?.hasMore ?: true
    }
}

internal class DynamicUserPaginationRegistry {
    private val stateByUser = mutableMapOf<Long, DynamicPaginationState>()

    fun reset(hostMid: Long) {
        stateByUser[hostMid] = DynamicPaginationState()
    }

    fun resetAll() {
        stateByUser.clear()
    }

    fun update(hostMid: Long, offset: String, hasMore: Boolean) {
        stateByUser[hostMid] = DynamicPaginationState(offset = offset, hasMore = hasMore)
    }

    fun offset(hostMid: Long): String {
        return stateByUser[hostMid]?.offset.orEmpty()
    }

    fun hasMore(hostMid: Long): Boolean {
        return stateByUser[hostMid]?.hasMore ?: true
    }
}
