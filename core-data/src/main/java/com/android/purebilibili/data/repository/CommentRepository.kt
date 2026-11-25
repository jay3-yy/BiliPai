package com.android.purebilibili.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.network.BilibiliApi
import com.android.purebilibili.core.network.BuvidManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.network.CoreDataLog
import com.android.purebilibili.core.network.grpc.BiliGrpcException
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.util.TreeMap

private val COMMENT_STANDARD_EMOTE_IDS = listOf(1L, 2L, 53L, 4L)

internal fun mergeCommentEmotePackages(
    userData: EmoteData?,
    standardPackages: List<EmotePackage>,
): List<EmotePackage> {
    val userPackages = userData?.packages?.takeIf { it.isNotEmpty() }
        ?: userData?.all_packages.orEmpty()
    val packages = userPackages.associateByTo(linkedMapOf()) { it.id }
    standardPackages.forEach { pkg ->
        if (packages[pkg.id]?.emote.isNullOrEmpty()) {
            packages[pkg.id] = pkg
        }
    }
    return packages.values.toList()
}

/**
 * A failed full thread does not establish that its requested child is unreadable.
 * Only a first-page 12006 can recover that exact child; no REST pagination is introduced.
 */
internal suspend fun recoverUnavailableSubReplyTarget(
    oid: Long,
    type: Int,
    rootId: Long,
    paginationOffset: String?,
    targetReplyId: Long,
    threadResult: Result<ReplyData>,
    readTarget: suspend () -> ReplyResponse,
): Result<ReplyData> {
    currentCoroutineContext().ensureActive()
    val failure = threadResult.exceptionOrNull() ?: return threadResult
    if (failure is CancellationException) throw failure
    val grpcFailure = failure as? BiliGrpcException ?: return threadResult
    if (grpcFailure.businessCode != 12006) return threadResult
    if (!paginationOffset.isNullOrEmpty() || oid <= 0 || type <= 0 ||
        rootId <= 0 || targetReplyId <= 0 || targetReplyId == rootId
    ) return Result.failure(CommentReadException(12006).apply { initCause(failure) })

    return try {
        val response = readTarget()
        currentCoroutineContext().ensureActive()
        if (response.code != 0) {
            Result.failure(CommentReadException(response.code, response.message))
        } else {
            val target = response.data?.root
                ?: return Result.failure(IllegalStateException("目标回复响应缺少数据，请重试"))
            require(target.rpid == targetReplyId && target.oid == oid &&
                target.replyType == type && target.root == rootId
            ) { "目标回复与当前楼层不匹配，请重试" }
            Result.success(ReplyData(
                replies = listOf(target),
                root = null,
                cursor = ReplyCursor(isEnd = true),
                isTargetOnly = true,
            ))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * 评论相关数据仓库
 * 从 VideoRepository 拆分出来，专注于评论功能
 */
object CommentRepository {
    private val api = NetworkModule.api
    private val guestApi = NetworkModule.guestApi
    private val commentJson = Json { ignoreUnknownKeys = true }

    /**
     * 评论发布成功后的宿主旁路（如手机端反诈存库）。共享层不持有其实现与作用域，
     * 由各端在启动时注入；TV 端只读不发布，保持为空即可。
     */
    var onCommentPosted: ((reply: ReplyItem, oid: Long, type: Int, root: Long, parent: Long, message: String, account: AccountSessionIdentity) -> Unit)? = null

    private const val MAX_COMMENT_IMAGE_BYTES = 15L * 1024 * 1024
    private const val IMAGE_TOO_LARGE_MESSAGE = "图片过大（单张最大 15MB）"

    private data class CachedCommentWbiKeys(
        val keys: Pair<String, String>,
        val timestampMs: Long,
    )

    // WBI Key 缓存（单次不可变快照，避免多线程 IO 并发读写撕裂）
    @Volatile
    private var cachedWbiKeys: CachedCommentWbiKeys? = null
    private const val WBI_CACHE_DURATION = 1000 * 60 * 30 // 30分钟缓存

    private inline fun <T> Result<T>.rethrowCancellation(): Result<T> = onFailure { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
    }

    @Serializable
    private data class CommentPicturePayload(
        @SerialName("img_src") val imgSrc: String,
        @SerialName("img_width") val imgWidth: Int,
        @SerialName("img_height") val imgHeight: Int,
        @SerialName("img_size") val imgSize: Float
    )

    /**
     * 获取 WBI Keys（用于 WBI 签名）
     */
    private suspend fun getWbiKeysOrNull(navApi: BilibiliApi = api): Pair<String, String>? {
        val currentCheck = System.currentTimeMillis()
        val cached = cachedWbiKeys
        if (cached != null && (currentCheck - cached.timestampMs < WBI_CACHE_DURATION)) {
            return cached.keys
        }
        val fromManager = runCatching {
            com.android.purebilibili.core.network.WbiKeyManager.getWbiKeys().getOrNull()
        }.rethrowCancellation().getOrNull()
        if (fromManager != null) {
            cachedWbiKeys = CachedCommentWbiKeys(fromManager, currentCheck)
            return fromManager
        }
        return runCatching { getWbiKeys(navApi) }.rethrowCancellation().getOrNull()
    }

    private suspend fun getWbiKeys(navApi: BilibiliApi = api): Pair<String, String> {
        val currentCheck = System.currentTimeMillis()
        val cached = cachedWbiKeys
        if (cached != null && (currentCheck - cached.timestampMs < WBI_CACHE_DURATION)) {
            return cached.keys
        }

        val fromManager = runCatching {
            com.android.purebilibili.core.network.WbiKeyManager.getWbiKeys().getOrNull()
        }.rethrowCancellation().getOrNull()
        if (fromManager != null) {
            cachedWbiKeys = CachedCommentWbiKeys(fromManager, currentCheck)
            return fromManager
        }

        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val navResp = navApi.getNavInfo()
                val wbiImg = navResp.data?.wbi_img
                
                if (wbiImg != null) {
                    val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                    val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
                    val resolved = Pair(imgKey, subKey)
                    cachedWbiKeys = CachedCommentWbiKeys(resolved, System.currentTimeMillis())
                    CoreDataLog.d("CommentRepo", " WBI Keys obtained successfully (attempt $attempt)")
                    return resolved
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("CommentRepo", "getWbiKeys attempt $attempt failed: ${e.message}")
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(200L * attempt) // 递增延迟
                }
            }
        }
        
        throw Exception("Wbi Keys Error after $maxRetries attempts: ${lastError?.message}")
    }

    private fun resolveReadApi(mode: CommentReadApiMode): BilibiliApi {
        return when (mode) {
            CommentReadApiMode.AUTH -> api
            CommentReadApiMode.GUEST -> guestApi
        }
    }

    private suspend fun fetchNonWbiCommentFallback(
        apiClient: BilibiliApi,
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mainListMode: Int,
        params: Map<String, String>
    ): ReplyResponse {
        val mainResponse = runCatching { apiClient.getReplyListMain(params) }.rethrowCancellation().getOrNull()
        if (mainResponse != null && (mainResponse.code == 0 || hasRenderableCommentPayload(mainResponse.data))) {
            return mainResponse
        }
        val guestMainResponse = runCatching { guestApi.getReplyListMain(params) }.rethrowCancellation().getOrNull()
        if (guestMainResponse != null && (guestMainResponse.code == 0 || hasRenderableCommentPayload(guestMainResponse.data))) {
            return guestMainResponse
        }
        val legacySort = if (mainListMode == CommentGrpcRepository.MODE_TIME) 0 else 1
        val legacyResponse = runCatching {
            apiClient.getReplyListLegacy(oid = oid, type = type, pn = page, ps = ps, sort = legacySort)
        }.rethrowCancellation().getOrNull()
        if (legacyResponse != null && (legacyResponse.code == 0 || hasRenderableCommentPayload(legacyResponse.data))) {
            return legacyResponse
        }
        return guestApi.getReplyListLegacy(oid = oid, type = type, pn = page, ps = ps, sort = legacySort)
    }

    private suspend fun fetchCommentsByApi(
        apiClient: BilibiliApi,
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mode: Int,
        paginationOffset: String? = null,
        cursorNext: Long? = null,
    ): ReplyResponse {
        return when (mode) {
            1 -> {
                CoreDataLog.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=2 (回复数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 2
                )
            }
            4 -> {
                CoreDataLog.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=1 (点赞数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 1
                )
            }
            else -> {
                val mainListMode = resolveCommentMainListMode(mode)
                val params = TreeMap<String, String>()
                params["oid"] = oid.toString()
                params["type"] = type.toString()
                params["mode"] = mainListMode.toString()
                params["ps"] = ps.toString()
                params["plat"] = "1"
                params["web_location"] = "1315875"
                params.putAll(resolveCommentMainListPaginationParameters(page, paginationOffset, cursorNext))

                // REST 兜底保持非 WBI，避免 WBI key 过期拖垮评论。
                CoreDataLog.d(
                    "CommentRepo",
                    " getComments (non-WBI main): oid=$oid, type=$type, page=$page, mode=$mainListMode"
                )
                fetchNonWbiCommentFallback(apiClient, oid, type, page, ps, mainListMode, params)
            }
        }
    }

    private suspend fun fetchGuestHotCommentsCompat(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        paginationOffset: String? = null
    ): ReplyResponse {
        CoreDataLog.d("CommentRepo", " getComments (CompatMain): oid=$oid, type=$type, page=$page, mode=3 (热度)")
        val params = TreeMap<String, String>()
        params["oid"] = oid.toString()
        params["type"] = type.toString()
        params["mode"] = "3"
        params["ps"] = ps.toString()
        params["plat"] = "1"
        params["web_location"] = "1315875"
        params.putAll(resolveCommentMainListPaginationParameters(page, paginationOffset))
        return guestApi.getReplyListMain(params)
    }

    /**
     * Legacy `x/v2/reply` still returns the separate `hots[]` bucket that mode=3
     * `wbi/main` / guest `main` sometimes omit on empty-success (code=0, replies=null).
     * sort=1 ≈ 点赞序，与桌面「按热度」最接近；同时 nohot 默认 0 会带上热评。
     */
    private suspend fun fetchLegacyHotCommentsCompat(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
    ): ReplyResponse {
        CoreDataLog.d(
            "CommentRepo",
            " getComments (LegacyHotCompat): oid=$oid, type=$type, page=$page, sort=1 (点赞/热评)"
        )
        // Prefer guest first: empty-success is most common on restricted / guest-like sessions.
        val guestResponse = guestApi.getReplyListLegacy(
            oid = oid,
            type = type,
            pn = page,
            ps = ps,
            sort = 1,
        )
        if (
            guestResponse.code == 0 &&
            hasRenderableCommentPayload(guestResponse.data)
        ) {
            return guestResponse
        }
        return api.getReplyListLegacy(
            oid = oid,
            type = type,
            pn = page,
            ps = ps,
            sort = 1,
        )
    }

    private suspend fun fetchCommentEmptySuccessFallback(
        readPlan: CommentReadPlan,
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mode: Int,
        paginationOffset: String?
    ): ReplyResponse {
        var compatResponse: ReplyResponse? = null
        if (mode == 3) {
            compatResponse = fetchGuestHotCommentsCompat(
                oid = oid,
                type = type,
                page = page,
                ps = ps,
                paginationOffset = paginationOffset
            )
            val compatNeedsFallback =
                shouldFallbackHotCommentReadOnEmptySuccess(
                    page = page,
                    mode = mode,
                    responseCode = compatResponse.code,
                    data = compatResponse.data,
                ) || (
                    compatResponse.code != 0 &&
                        readPlan.fallback != null &&
                        shouldFallbackCommentRead(compatResponse.code)
                    )
            if (!compatNeedsFallback) return compatResponse
        }

        val fallbackMode = readPlan.fallback
        val identityResponse = if (fallbackMode != null) {
            CoreDataLog.w(
                "CommentRepo",
                "getComments empty-success identity fallback: to=$fallbackMode, oid=$oid, type=$type, page=$page, mode=$mode"
            )
            fetchCommentsByApi(
                apiClient = resolveReadApi(fallbackMode),
                oid = oid,
                type = type,
                page = page,
                ps = ps,
                mode = mode,
                paginationOffset = paginationOffset
            )
        } else {
            null
        }

        val preferred = identityResponse ?: compatResponse
        // Residual hot empty: wbi/main + guest main + identity all returned code=0 with no
        // renderable replies/hots/tops. Legacy list still exposes hots[] for many of these.
        if (
            mode == 3 &&
            page == 1 &&
            (
                preferred == null ||
                    shouldFallbackHotCommentReadOnEmptySuccess(
                        page = page,
                        mode = mode,
                        responseCode = preferred.code,
                        data = preferred.data,
                    ) ||
                    shouldFallbackCommentReadOnEmptyRenderableSuccess(
                        responseCode = preferred.code,
                        data = preferred.data,
                    )
                )
        ) {
            CoreDataLog.w(
                "CommentRepo",
                "getComments empty-success legacy hot fallback: oid=$oid, type=$type, page=$page"
            )
            val legacyResponse = fetchLegacyHotCommentsCompat(
                oid = oid,
                type = type,
                page = page,
                ps = ps,
            )
            if (
                legacyResponse.code == 0 &&
                hasRenderableCommentPayload(legacyResponse.data)
            ) {
                return legacyResponse
            }
            // Prefer a non-empty-count payload for UI/retry signals when legacy also blank.
            if (
                preferred != null &&
                preferred.code == 0 &&
                (preferred.data?.getAllCount() ?: 0) > 0
            ) {
                return preferred
            }
            if (legacyResponse.code == 0) return legacyResponse
        }

        return preferred
            ?: compatResponse
            ?: ReplyResponse(code = -1, message = "empty comment payload")
    }

    /**
     * 获取评论列表
     * @param mode 排序模式:
     * 3=最热(WBI mode=3), 2=最新(WBI mode=2), 4=点赞(legacy sort=1), 1=回复(legacy sort=2)
     */
    suspend fun getComments(
        aid: Long,
        page: Int,
        ps: Int = 20,
        mode: Int = 3,
        paginationOffset: String? = null
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        getCommentsForSubject(
            oid = aid,
            type = 1,
            page = page,
            ps = ps,
            mode = mode,
            paginationOffset = paginationOffset
        )
    }

    suspend fun getCommentsForSubject(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int = 20,
        mode: Int = 3,
        paginationOffset: String? = null,
        fallbackOnMissingLocation: Boolean = false
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        var fallbackGrpcResult: Result<ReplyData>? = null
        try {
            // 确保 buvid3 已初始化
            BuvidManager.ensureBuvid3()

            val hasSession = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            if (
                shouldTryGrpcMainList(
                    type = type,
                    page = page,
                    mode = mode,
                    paginationOffset = paginationOffset
                ) &&
                // gRPC 刚刚失败过（TTL 内）就直接走 REST，省掉一次注定失败的往返。
                // 只减少请求、不增加并发；成功后记忆立刻清除。
                !commentReadRouteMemo.shouldSkipGrpcAttempt()
            ) {
                val grpcResult = CommentGrpcRepository.getMainList(
                    oid = oid,
                    type = type,
                    mode = mode,
                    nextOffset = paginationOffset
                )
                resolveGrpcCommentReadRestriction(grpcResult.exceptionOrNull())?.let {
                    return@withContext Result.failure(it)
                }
                if (grpcResult.isSuccess) {
                    commentReadRouteMemo.recordGrpcSuccess()
                    val grpcData = grpcResult.getOrNull()
                    if (
                        shouldFallbackHotCommentReadOnEmptySuccess(
                            page = page,
                            mode = mode,
                            responseCode = 0,
                            data = grpcData,
                        ) || shouldFallbackCommentReadOnEmptyRenderableSuccess(
                            responseCode = 0,
                            data = grpcData,
                        )
                    ) {
                        CoreDataLog.w(
                            "CommentRepo",
                            "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, reason=empty-renderable-success"
                        )
                    } else if (!fallbackOnMissingLocation || !shouldFallbackGrpcCommentReadOnMissingLocation(grpcData)) {
                        CoreDataLog.d("CommentRepo", " getComments (gRPC MainList): oid=$oid, type=$type, page=$page, mode=$mode")
                        return@withContext grpcResult
                    } else {
                        fallbackGrpcResult = grpcResult
                        CoreDataLog.w(
                            "CommentRepo",
                            "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, reason=missing-location"
                        )
                    }
                } else {
                    commentReadRouteMemo.recordGrpcFailure()
                    CoreDataLog.w(
                        "CommentRepo",
                        "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, error=${grpcResult.exceptionOrNull()?.message}"
                    )
                }
            }

            val readPlan = resolveCommentReadPlan(hasSession = hasSession)
            val primaryMode = readPlan.primary
            val primaryResponse = fetchCommentsByApi(
                apiClient = resolveReadApi(primaryMode),
                oid = oid,
                type = type,
                page = page,
                ps = ps,
                mode = mode,
                paginationOffset = paginationOffset
            )
            val finalResponse = if (
                shouldFallbackHotCommentReadOnEmptySuccess(
                    page = page,
                    mode = mode,
                    responseCode = primaryResponse.code,
                    data = primaryResponse.data,
                ) || shouldFallbackCommentReadOnEmptyRenderableSuccess(
                    responseCode = primaryResponse.code,
                    data = primaryResponse.data,
                )
            ) {
                CoreDataLog.w(
                    "CommentRepo",
                    "getComments empty-success fallback triggered: from=$primaryMode, oid=$oid, type=$type, page=$page, mode=$mode, total=${primaryResponse.data?.getAllCount() ?: 0}"
                )
                fetchCommentEmptySuccessFallback(
                    readPlan = readPlan,
                    oid = oid,
                    type = type,
                    page = page,
                    ps = ps,
                    mode = mode,
                    paginationOffset = paginationOffset
                )
            } else if (
                primaryResponse.code != 0 &&
                readPlan.fallback != null &&
                shouldFallbackCommentRead(primaryResponse.code)
            ) {
                val checkedReadPlanFallback = requireNotNull(readPlan.fallback)
                val fallbackMode = checkedReadPlanFallback
                CoreDataLog.w(
                    "CommentRepo",
                    "getComments fallback triggered: code=${primaryResponse.code}, from=$primaryMode to=$fallbackMode, oid=$oid, type=$type, page=$page, mode=$mode"
                )
                fetchCommentsByApi(
                    apiClient = resolveReadApi(fallbackMode),
                    oid = oid,
                    type = type,
                    page = page,
                    ps = ps,
                    mode = mode,
                    paginationOffset = paginationOffset
                )
            } else {
                primaryResponse
            }
            
            val sortLabel = when (mode) {
                2 -> "时间"
                4 -> "点赞数"
                1 -> "回复数"
                else -> "热度"
            }
            CoreDataLog.d(
                "CommentRepo",
                " getComments result: oid=$oid, type=$type, mode=$mode($sortLabel), replies=${finalResponse.data?.replies?.size ?: 0}, code=${finalResponse.code}"
            )

            if (finalResponse.code == 0) {
                val data = finalResponse.data ?: ReplyData()
                Result.success(
                    data.copy(
                        // REST 补全评论 IP 属地时保留 gRPC 独有的评论区投票卡片。
                        voteCard = data.voteCard ?: fallbackGrpcResult?.getOrNull()?.voteCard,
                        grpcNextOffset = data.cursor.paginationReply?.nextOffset.orEmpty()
                    )
                )
            } else {
                if (fallbackGrpcResult != null) {
                    CoreDataLog.w("CommentRepo", "getComments REST failed with code ${finalResponse.code}, restoring valid gRPC payload")
                    return@withContext fallbackGrpcResult
                }
                val error = CommentReadException(finalResponse.code, finalResponse.message)
                android.util.Log.e("CommentRepo", " getComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                Result.failure(error)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (fallbackGrpcResult != null) {
                CoreDataLog.w("CommentRepo", "getComments REST exception (${e.message}), restoring valid gRPC payload")
                return@withContext fallbackGrpcResult
            }
            android.util.Log.e("CommentRepo", " getComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }.map { filterCommentRepliesIfEnabled(it) }

    /**
     * 手机主评论通过独立排序字段与服务端 offset 分页。分页来源随缓存保存，
     * 中途失败保留原 offset 重试，不将 gRPC 游标交给 REST。
     */
    suspend fun getMobileCommentsForSubject(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int = 20,
        mode: Int = 3,
        continuation: CommentMainListContinuation? = null,
        fallbackOnMissingLocation: Boolean = false,
        expectedCount: Int = 0,
        applyDisplayFilter: Boolean = true,
        continueAfterGoodsFilter: Boolean = false,
    ): Result<ReplyData> {
        val initial = recoverEmptyCommentFirstPage(page, expectedCount) {
            readMobileCommentsForSubject(oid, type, page, ps, mode, continuation, fallbackOnMissingLocation)
        }
        val first = initial.getOrElse { return initial }
        val context = NetworkModule.appContext
        val result = if (continueAfterGoodsFilter && context != null &&
            ContentFilterRules.snapshot(context).blockGoodsReplies
        ) {
            var nextPage = page + 1
            continueGoodsFilteredCommentPage(first, includeFeatured = page == 1) { next ->
                readMobileCommentsForSubject(oid, type, nextPage++, ps, mode, next, fallbackOnMissingLocation)
            }
        } else initial
        return result.map { if (applyDisplayFilter) filterCommentRepliesIfEnabled(it) else it }
    }

    private suspend fun readMobileCommentsForSubject(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mode: Int,
        continuation: CommentMainListContinuation?,
        fallbackOnMissingLocation: Boolean,
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            require(page <= 1 || continuation != null) { "评论缺少分页游标，请刷新后重试" }
            BuvidManager.ensureBuvid3()
            val supportsGrpc = mode == CommentGrpcRepository.MODE_TIME || mode == CommentGrpcRepository.MODE_HOT
            val useGrpc = continuation?.route == CommentMainListRoute.GRPC ||
                (page <= 1 && supportsGrpc && !commentReadRouteMemo.shouldSkipGrpcAttempt())
            if (useGrpc) {
                val result = CommentGrpcRepository.getMainList(
                    oid = oid, type = type, mode = mode, nextOffset = continuation?.offset,
                )
                currentCoroutineContext().ensureActive()
                resolveGrpcCommentReadRestriction(result.exceptionOrNull())?.let {
                    return@withContext Result.failure(it)
                }
                val data = result.getOrNull()
                val missingCursor = data != null && !data.cursor.isEnd &&
                    data.mainListContinuation?.offset.isNullOrBlank()
                val needsFirstPageFallback = page <= 1 && (missingCursor ||
                    shouldFallbackHotCommentReadOnEmptySuccess(page, mode, 0, data) ||
                    shouldFallbackCommentReadOnEmptyRenderableSuccess(0, data))
                if (data != null && !needsFirstPageFallback) {
                    commentReadRouteMemo.recordGrpcSuccess()
                    requireAdvancingMobileCommentCursor(data, continuation)
                    // IP 属地仅作为补充，不替换 gRPC 排序、游标、控制项和投票卡片。
                    val supplements = if (fallbackOnMissingLocation && shouldFallbackGrpcCommentReadOnMissingLocation(data)) {
                        withTimeoutOrNull(4_500L) {
                            fetchMobileRestCommentPage(oid, type, 1, ps, mode, null).getOrNull()
                        }
                    } else null
                    currentCoroutineContext().ensureActive()
                    return@withContext Result.success(if (supplements != null) {
                        mergeCommentReplyMetadata(data, collectReplyLocationCandidates(supplements))
                    } else data)
                }
                // 已开始的分页绝不能换接口；刷新才允许重新选择读取来源。
                if (page > 1) return@withContext Result.failure(
                    result.exceptionOrNull() ?: IllegalStateException("评论分页响应异常，请重试"),
                )
                if (result.isFailure) commentReadRouteMemo.recordGrpcFailure()
            }
            fetchMobileRestCommentPage(oid, type, page, ps, mode, continuation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchMobileRestCommentPage(
        oid: Long, type: Int, page: Int, ps: Int, mode: Int,
        continuation: CommentMainListContinuation?,
    ): Result<ReplyData> {
        return try {
            val plan = resolveCommentReadPlan(!com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty())
            val serverPage = if (continuation?.route == CommentMainListRoute.REST_LEGACY) {
                continuation.next.toInt()
            } else page
            suspend fun fetch(identity: CommentReadApiMode): ReplyResponse {
                val client = resolveReadApi(identity)
                if (continuation?.route == CommentMainListRoute.REST_LEGACY) {
                    val sort = when (mode) { 1 -> 2; 2 -> 0; else -> 1 }
                    return client.getReplyListLegacy(oid = oid, type = type, pn = serverPage, ps = ps, sort = sort)
                }
                return fetchCommentsByApi(
                    client, oid, type, page, ps, mode,
                    paginationOffset = continuation?.offset, cursorNext = continuation?.next,
                )
            }
            var response = fetch(plan.primary)
            if (response.code != 0 && shouldFallbackCommentRead(response.code)) {
                plan.fallback?.let { response = fetch(it) }
            }
            if (page <= 1 && (shouldFallbackHotCommentReadOnEmptySuccess(page, mode, response.code, response.data) ||
                    shouldFallbackCommentReadOnEmptyRenderableSuccess(response.code, response.data))) {
                response = fetchCommentEmptySuccessFallback(plan, oid, type, page, ps, mode, null)
            }
            currentCoroutineContext().ensureActive()
            if (response.code != 0) return Result.failure(CommentReadException(response.code, response.message))
            val rawData = response.data ?: return Result.failure(Exception("评论响应缺少数据，请重试"))
            require(continuation == null || continuation.route == mobileRestCommentRoute(rawData)) {
                "评论分页接口发生变化，请刷新后重试"
            }
            val data = resolveMobileRestCommentPage(rawData, serverPage, ps)
            requireAdvancingMobileCommentCursor(data, continuation)
            Result.success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommentCountForSubject(
        oid: Long,
        type: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            val response = api.getReplyCount(oid = oid, type = type)
            if (response.code == 0) {
                Result.success(response.data?.count ?: 0)
            } else {
                Result.failure(CommentReadException(response.code, response.message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取二级评论（楼中楼）
     */
    suspend fun getSubComments(aid: Long, rootId: Long, page: Int, ps: Int = 20): Result<ReplyData> = withContext(Dispatchers.IO) {
        getSubCommentsForSubject(
            oid = aid,
            type = 1,
            rootId = rootId,
            page = page,
            ps = ps
        )
    }

    private suspend fun readSortedSubCommentPage(
        oid: Long,
        type: Int,
        rootId: Long,
        mode: Int,
        paginationOffset: String?,
    ): Result<ReplyData> {
        require(mode == CommentGrpcRepository.MODE_TIME || mode == CommentGrpcRepository.MODE_HOT)
        BuvidManager.ensureBuvid3()
        val result = CommentGrpcRepository.getDetailList(
            oid = oid,
            type = type,
            root = rootId,
            mode = mode,
            nextOffset = paginationOffset,
        )
        currentCoroutineContext().ensureActive()
        return result
    }

    // PiliPlus uses DetailList mode + cursor for thread sorting. REST pn pages cannot be
    // mixed into this sequence: they do not preserve the selected server ranking.
    suspend fun getSortedSubCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        mode: Int,
        paginationOffset: String? = null,
        supplementLocations: Boolean = true,
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            val result = readSortedSubCommentPage(oid, type, rootId, mode, paginationOffset)
            val data = result.getOrNull() ?: return@withContext result
            Result.success(if (supplementLocations) supplementSortedSubReplyLocations(oid, type, data) else data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }.map { filterCommentRepliesIfEnabled(it) }

    /** Resolve routed reply identities before reading a complete, sorted thread. */
    suspend fun getMobileSortedSubCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        mode: Int,
        paginationOffset: String? = null,
        targetReplyId: Long = 0L,
        supplementLocations: Boolean = true,
        applyDisplayFilter: Boolean = true,
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            val account = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
            fun requireAccount() {
                if (account != com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
                    throw CancellationException("Comment account changed")
                }
            }
            val recoveredResult = readLocatedSubReplyPage(
                oid = oid, type = type, rootId = rootId, targetReplyId = targetReplyId,
                paginationOffset = paginationOffset,
                readReply = { id ->
                    requireAccount()
                    api.getReplyDetail(oid = oid, type = type, replyId = id).also { requireAccount() }
                },
                readThread = { id ->
                    requireAccount()
                    readSortedSubCommentPage(oid, type, id, mode, paginationOffset).also { requireAccount() }
                },
            )
            currentCoroutineContext().ensureActive()
            val data = recoveredResult.getOrNull() ?: return@withContext recoveredResult
            requireAdvancingSubReplyCursor(data, paginationOffset)
            val withLocations = if (supplementLocations && !data.isTargetOnly) {
                supplementSortedSubReplyLocations(oid, type, data)
            } else data
            currentCoroutineContext().ensureActive()
            requireAccount()
            Result.success(withLocations)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }.map { if (applyDisplayFilter) filterCommentRepliesIfEnabled(it) else it }.let { result ->
        val data = result.getOrNull()
        if (data?.isTargetOnly == true && data.replies.isNullOrEmpty()) {
            Result.failure(IllegalStateException("该回复已按过滤设置隐藏"))
        } else result
    }

    private suspend fun supplementSortedSubReplyLocations(
        oid: Long,
        type: Int,
        data: ReplyData,
    ): ReplyData {
        val missing = collectReplyLocationCandidates(data).filter {
            it.rpid > 0 && it.replyControl?.location.isNullOrBlank()
        }
        if (missing.isEmpty()) return data
        // 楼中楼子回复的 IP 属地只随 x/v2/reply/reply 下发：主列表 seek_rpid 命中的嵌套
        // 回复不带 location，按 root 分组走二级评论接口补全（属地字段需要登录态）。
        val supplements = mutableListOf<ReplyItem>()
        withTimeoutOrNull(4_500L) {
            val childRoots = missing.map { it.root }.filter { it != 0L }.distinct()
            for (rootId in childRoots) {
                var pn = 1
                while (pn <= 3) {
                    currentCoroutineContext().ensureActive()
                    val response = try {
                        api.getReplyReply(oid = oid, type = type, root = rootId, pn = pn, ps = 20)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CoreDataLog.w("CommentRepo", "Sub-reply location supplement failed: ${e.message}")
                        null
                    } ?: break
                    if (response.code != 0) break
                    val pageData = response.data ?: break
                    supplements += collectReplyLocationCandidates(pageData)
                    if (missing.all { item -> supplements.any {
                            it.rpid == item.rpid && !it.replyControl?.location.isNullOrBlank()
                        } }) {
                        return@withTimeoutOrNull
                    }
                    val total = pageData.page.count
                    if (pageData.replies.isNullOrEmpty() || pn * 20 >= total) break
                    pn++
                }
            }
        }
        return mergeCommentReplyMetadata(data, supplements)
    }

    suspend fun getSubCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        page: Int,
        ps: Int = 20,
        paginationOffset: String? = null,
        preferRestPaging: Boolean = true,
        applyDisplayFilter: Boolean = true,
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        var fallbackGrpcResult: Result<ReplyData>? = null
        try {
            // 确保 buvid3 已初始化
            BuvidManager.ensureBuvid3()

            val useRestSubReplyPaging = preferRestPaging ||
                (page > 1 && paginationOffset.isNullOrBlank())
            if (!useRestSubReplyPaging && shouldTryGrpcPagedRequest(page = page, paginationOffset = paginationOffset)) {
                val grpcResult = CommentGrpcRepository.getDetailList(
                    oid = oid,
                    type = type,
                    root = rootId,
                    nextOffset = paginationOffset
                )
                if (grpcResult.isSuccess) {
                    val grpcData = grpcResult.getOrNull()
                    if (!shouldFallbackGrpcCommentReadOnMissingLocation(grpcData)) {
                        CoreDataLog.d("CommentRepo", " getSubComments (gRPC DetailList): oid=$oid, type=$type, root=$rootId, page=$page")
                        return@withContext grpcResult
                    }
                    fallbackGrpcResult = grpcResult
                    CoreDataLog.w(
                        "CommentRepo",
                        "getSubComments gRPC fallback to REST: oid=$oid, type=$type, root=$rootId, page=$page, reason=missing-location"
                    )
                } else {
                    CoreDataLog.w(
                        "CommentRepo",
                        "getSubComments gRPC fallback to REST: oid=$oid, type=$type, root=$rootId, page=$page, error=${grpcResult.exceptionOrNull()?.message}"
                    )
                }
            }
            
            CoreDataLog.d("CommentRepo", " getSubComments: oid=$oid, type=$type, rootId=$rootId, page=$page")
            val hasSession = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            val readPlan = resolveCommentReadPlan(hasSession = hasSession)
            val primaryMode = readPlan.primary
            val primaryResponse = resolveReadApi(primaryMode).getReplyReply(
                oid = oid,
                type = type,
                root = rootId,
                pn = page,
                ps = ps
            )
            val finalResponse = if (
                primaryResponse.code != 0 &&
                readPlan.fallback != null &&
                shouldFallbackCommentRead(primaryResponse.code)
            ) {
                val checkedReadPlanFallback = requireNotNull(readPlan.fallback)
                val fallbackMode = checkedReadPlanFallback
                CoreDataLog.w(
                    "CommentRepo",
                    "getSubComments fallback triggered: code=${primaryResponse.code}, from=$primaryMode to=$fallbackMode, oid=$oid, type=$type, root=$rootId, page=$page"
                )
                resolveReadApi(fallbackMode).getReplyReply(
                    oid = oid,
                    type = type,
                    root = rootId,
                    pn = page,
                    ps = ps
                )
            } else {
                primaryResponse
            }
            
            CoreDataLog.d("CommentRepo", " getSubComments response: oid=$oid, type=$type, code=${finalResponse.code}, replies=${finalResponse.data?.replies?.size ?: 0}")
            
            if (finalResponse.code == 0) {
                Result.success(finalResponse.data ?: ReplyData())
            } else {
                if (fallbackGrpcResult != null) {
                    CoreDataLog.w("CommentRepo", "getSubComments REST failed with code ${finalResponse.code}, restoring valid gRPC payload")
                    return@withContext fallbackGrpcResult
                }
                android.util.Log.e("CommentRepo", " getSubComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                Result.failure(CommentReadException(finalResponse.code, finalResponse.message))
            }
        } catch (e: Exception) {
            if (fallbackGrpcResult != null) {
                CoreDataLog.w("CommentRepo", "getSubComments REST exception (${e.message}), restoring valid gRPC payload")
                return@withContext fallbackGrpcResult
            }
            android.util.Log.e("CommentRepo", " getSubComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }.map { if (applyDisplayFilter) filterCommentRepliesIfEnabled(it) else it }

    suspend fun getDialogCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        dialogId: Long,
        page: Int,
        paginationOffset: String? = null,
        applyDisplayFilter: Boolean = true,
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            if (!shouldTryGrpcPagedRequest(page = page, paginationOffset = paginationOffset)) {
                return@withContext Result.failure(Exception("对话列表缺少分页参数"))
            }
            val grpcResult = CommentGrpcRepository.getDialogList(
                oid = oid,
                type = type,
                root = rootId,
                dialog = dialogId,
                nextOffset = paginationOffset
            )
            currentCoroutineContext().ensureActive()
            grpcResult.getOrNull()?.let { requireAdvancingSubReplyCursor(it, paginationOffset) }
            if (grpcResult.isSuccess) {
                CoreDataLog.d("CommentRepo", " getDialogComments (gRPC DialogList): oid=$oid, type=$type, root=$rootId, dialog=$dialogId, page=$page")
            }
            grpcResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }.map { if (applyDisplayFilter) filterCommentRepliesIfEnabled(it) else it }

    /**
     * 获取表情包映射
     */
    suspend fun getEmoteMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        // 默认表情
        map["[doge]"] = "http://i0.hdslb.com/bfs/emote/6f8743c3c13009f4705307b2750e32f5068225e3.png"
        map["[笑哭]"] = "http://i0.hdslb.com/bfs/emote/500b63b2f293309a909403a746566fdd6104d498.png"
        map["[妙啊]"] = "http://i0.hdslb.com/bfs/emote/03c39c8eb009f63568971032b49c716259c72441.png"
        getEmotePackages().getOrNull()?.forEach { pkg ->
            pkg.emote?.forEach { emote -> map[emote.text] = emote.url }
        }
        map
    }

    /**
     * 获取表情包列表 (用于UI展示)
     */
    suspend fun getEmotePackages(): Result<List<EmotePackage>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEmotes(mapOf("business" to "reply"))
            val userData = if (response.code == 0) response.data else null
            val userPackages = userData?.packages?.takeIf { it.isNotEmpty() }
                ?: userData?.all_packages.orEmpty()
            val missingIds = COMMENT_STANDARD_EMOTE_IDS.filter { id ->
                userPackages.none { it.id == id && !it.emote.isNullOrEmpty() }
            }
            if (missingIds.isEmpty()) {
                return@withContext Result.success(userPackages)
            }

            // 用户面板可能成功返回空列表；从 B 站明细接口取回缺失的原始基础包。
            val details = api.getEmotePackageDetails(
                mapOf("business" to "reply", "ids" to missingIds.joinToString(","))
            )
            if (details.code != 0) {
                return@withContext Result.failure(Exception(details.message))
            }
            val packages = mergeCommentEmotePackages(userData, details.data?.packages.orEmpty())
            if (missingIds.any { id -> packages.none { it.id == id && !it.emote.isNullOrEmpty() } }) {
                Result.failure(Exception("基础表情包加载失败"))
            } else {
                Result.success(packages)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchMentionUsers(keyword: String = ""): Result<List<MentionSearchUser>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchMentionUsers(keyword.trim().takeIf { it.isNotEmpty() })
            if (response.code == 0) {
                val users = response.data
                    ?.groups
                    .orEmpty()
                    .flatMap { it.items }
                    .filter { it.uid > 0L && it.name.isNotBlank() }
                    .distinctBy { it.uid }
                Result.success(users)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录后使用@好友"
                    else -> response.message.ifEmpty { "搜索@好友失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CoreDataLog.e("CommentRepo", "searchMentionUsers exception: keyword=$keyword", e)
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 发送评论
     * @param aid 视频 aid
     * @param message 评论内容
     * @param root 根评论 rpid（回复时需要）
     * @param parent 父评论 rpid
     * @return 新评论的 rpid
     */
    suspend fun addComment(
        aid: Long,
        message: String,
        root: Long = 0,
        parent: Long = 0,
        pictures: List<ReplyPicture> = emptyList(),
        syncToDynamic: Boolean = false
    ): Result<ReplyItem?> = withContext(Dispatchers.IO) {
        addCommentForSubject(
            oid = aid,
            type = 1,
            message = message,
            root = root,
            parent = parent,
            pictures = pictures,
            syncToDynamic = syncToDynamic
        )
    }

    suspend fun addCommentForSubject(
        oid: Long,
        type: Int,
        message: String,
        root: Long = 0,
        parent: Long = 0,
        pictures: List<ReplyPicture> = emptyList(),
        syncToDynamic: Boolean = false
    ): Result<ReplyItem?> = withContext(Dispatchers.IO) {
        try {
            val postingAccount = TokenManager.accountIdentity.value
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            val picturePayload = buildPicturesPayload(pictures)
            
            val response = api.addReply(
                oid = oid,
                type = type,
                message = message,
                root = root.takeIf { it > 0L },
                parent = parent.takeIf { it > 0L },
                pictures = picturePayload,
                syncToDynamic = resolveSyncToDynamicField(syncToDynamic),
                csrf = csrf
            )
            
            if (response.code == 0) {
                val reply = response.data?.reply
                if (reply != null && reply.rpid > 0L) {
                    // 宿主旁路（如手机端反诈存库）在注入方自己的作用域里执行，不阻塞发布主流程。
                    onCommentPosted?.invoke(reply, oid, type, root, parent, message, postingAccount)
                }
                // 立刻返回给 UI 渲染
                Result.success(reply)
            } else {
                CoreDataLog.e(
                    "CommentRepo",
                    "addComment failed: oid=$oid, type=$type, root=$root, parent=$parent, pictureCount=${pictures.size}, code=${response.code}, message=${response.message}"
                )
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -102 -> "账号被封禁"
                    -509 -> "请求过于频繁"
                    12002 -> "评论区已关闭"
                    12015 -> "需要评论验证码"
                    12016 -> "评论内容包含敏感信息"
                    12025 -> "评论字数过多"
                    12035 -> "您已被UP主拉黑"
                    12051 -> "重复评论，请勿刷屏"
                    else -> response.message.ifEmpty { "发送失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            CoreDataLog.e("CommentRepo", "addComment exception: oid=$oid, type=$type, root=$root, parent=$parent", e)
            Result.failure(e)
        }
    }

    /**
     * 上传评论图片，返回可用于评论 pictures 字段的元数据
     */
    suspend fun uploadCommentImage(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        biz: String = "new_dyn",
    ): Result<ReplyPicture> = withContext(Dispatchers.IO) {
        val mediaType = mimeType.toMediaType()
        uploadCommentImagePart(
            fileName = fileName.ifBlank { "comment_image.jpg" },
            mimeType = mimeType,
            fileBody = bytes.toRequestBody(mediaType),
            biz = biz,
        )
    }

    /**
     * 流式上传本地图片:不把整个文件读入内存,请求体直接从 ContentResolver 流写入。
     * 大小校验基于 [OpenableColumns.SIZE] 在读文件**之前**完成;无法获取尺寸时
     * (自定义 DocumentsProvider 等)回退到整读的旧路径,保持原校验行为。
     */
    suspend fun uploadCommentImage(
        fileName: String,
        mimeType: String,
        resolver: ContentResolver,
        uri: Uri,
        biz: String = "new_dyn",
    ): Result<ReplyPicture> = withContext(Dispatchers.IO) {
        val size = queryContentImageSize(resolver, uri)
        if (size == null) {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("无法读取图片文件"))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("图片内容为空"))
            if (bytes.size > MAX_COMMENT_IMAGE_BYTES) {
                return@withContext Result.failure(Exception(IMAGE_TOO_LARGE_MESSAGE))
            }
            uploadCommentImage(
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                biz = biz,
            )
        } else {
            if (size <= 0L) return@withContext Result.failure(Exception("图片内容为空"))
            if (size > MAX_COMMENT_IMAGE_BYTES) {
                return@withContext Result.failure(Exception(IMAGE_TOO_LARGE_MESSAGE))
            }
            uploadCommentImagePart(
                fileName = fileName.ifBlank { "comment_image.jpg" },
                mimeType = mimeType,
                fileBody = ContentUriRequestBody(
                    resolver = resolver,
                    uri = uri,
                    mediaType = mimeType.toMediaType(),
                    contentLength = size
                ),
                biz = biz,
            )
        }
    }

    private suspend fun uploadCommentImagePart(
        fileName: String,
        mimeType: String,
        fileBody: RequestBody,
        biz: String,
    ): Result<ReplyPicture> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val part = okhttp3.MultipartBody.Part.createFormData(
                "file_up",
                fileName,
                fileBody
            )
            val textMedia = "text/plain".toMediaType()
            val categoryBody = "daily".toRequestBody(textMedia)
            val bizBody = biz.toRequestBody(textMedia)
            val csrfBody = csrf.toRequestBody(textMedia)

            val response = api.uploadCommentImage(
                fileUp = part,
                category = categoryBody,
                biz = bizBody,
                csrf = csrfBody
            )

            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                val data = checkedResponseData
                Result.success(
                    ReplyPicture(
                        imgSrc = data.imageUrl,
                        imgWidth = data.imageWidth,
                        imgHeight = data.imageHeight,
                        imgSize = data.imgSize
                    )
                )
            } else {
                CoreDataLog.e(
                    "CommentRepo",
                    "uploadCommentImage failed: fileName=$fileName, mimeType=$mimeType, size=${fileBody.contentLength()}, code=${response.code}, message=${response.message}"
                )
                Result.failure(Exception(response.message.ifEmpty { "图片上传失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            CoreDataLog.e(
                "CommentRepo",
                "uploadCommentImage exception: fileName=$fileName, mimeType=$mimeType, size=${fileBody.contentLength()}",
                e
            )
            Result.failure(e)
        }
    }

    private fun queryContentImageSize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }
    }.getOrNull()

    /**
     * 一次性请求体:每次 [writeTo] 重新打开输入流,边读边写,不缓存整份字节。
     * [isOneShot] 禁止 OkHttp 在连接失败后重放,避免二次上传。
     */
    private class ContentUriRequestBody(
        private val resolver: ContentResolver,
        private val uri: Uri,
        private val mediaType: MediaType,
        private val contentLength: Long
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = contentLength
        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            resolver.openInputStream(uri)?.use { input ->
                sink.writeAll(input.source())
            } ?: error("无法读取图片文件")
        }
    }

    internal fun buildPicturesPayload(pictures: List<ReplyPicture>): String? {
        if (pictures.isEmpty()) return null
        val payload = pictures.map { picture ->
            CommentPicturePayload(
                imgSrc = picture.imgSrc,
                imgWidth = picture.imgWidth,
                imgHeight = picture.imgHeight,
                imgSize = picture.imgSize
            )
        }
        return commentJson.encodeToString(payload)
    }

    internal fun resolveSyncToDynamicField(syncToDynamic: Boolean): Int? {
        return if (syncToDynamic) 1 else null
    }

    internal fun shouldTryGrpcMainList(
        type: Int,
        page: Int,
        mode: Int,
        paginationOffset: String?
    ): Boolean {
        // Match the official-app/PiliPlus flow: MainList is also available without a
        // logged-in session. Keeping it as the first read path avoids intermittent WBI
        // key/signature failures; empty or incomplete payloads still fall back to REST.
        if (type == 17) return false
        val supportedMode = mode == CommentGrpcRepository.MODE_HOT || mode == CommentGrpcRepository.MODE_TIME
        if (!supportedMode) return false
        return shouldTryGrpcPagedRequest(page = page, paginationOffset = paginationOffset)
    }

    internal fun resolveCommentMainListPaginationParameters(
        page: Int,
        paginationOffset: String?,
        cursorNext: Long? = null,
    ): Map<String, String> {
        if (page <= 1) {
            return mapOf("pagination_str" to """{"offset":""}""")
        }
        if (!paginationOffset.isNullOrBlank()) {
            return mapOf(
                "pagination_str" to commentJson.encodeToString(mapOf("offset" to paginationOffset))
            )
        }
        return mapOf("next" to (cursorNext ?: page.toLong()).toString())
    }

    internal fun resolveCommentMainListMode(mode: Int): Int {
        return if (mode == CommentGrpcRepository.MODE_TIME) {
            CommentGrpcRepository.MODE_TIME
        } else {
            CommentGrpcRepository.MODE_HOT
        }
    }

    internal fun shouldTryGrpcPagedRequest(
        page: Int,
        paginationOffset: String?
    ): Boolean {
        return page <= 1 || !paginationOffset.isNullOrBlank()
    }
    
    /**
     * [新增] 点赞评论
     */
    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean): Result<Unit> {
        return likeCommentForSubject(oid = aid, type = 1, rpid = rpid, like = like)
    }

    suspend fun likeCommentForSubject(
        oid: Long,
        type: Int,
        rpid: Long,
        like: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.likeReply(
                oid = oid,
                type = type,
                rpid = rpid,
                action = if (like) 1 else 0,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 点踩评论
     */
    suspend fun hateComment(aid: Long, rpid: Long, hate: Boolean): Result<Unit> =
        hateCommentForSubject(oid = aid, type = 1, rpid = rpid, hate = hate)

    suspend fun hateCommentForSubject(
        oid: Long,
        type: Int,
        rpid: Long,
        hate: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.hateReply(
                oid = oid,
                type = type,
                rpid = rpid,
                action = if (hate) 1 else 0,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 删除评论
     */
    suspend fun deleteComment(aid: Long, rpid: Long): Result<Unit> =
        deleteCommentForSubject(oid = aid, type = 1, rpid = rpid)

    suspend fun deleteCommentForSubject(
        oid: Long,
        type: Int,
        rpid: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.deleteReply(
                oid = oid,
                type = type,
                rpid = rpid,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    -403 -> "无权删除此评论"
                    12022 -> "评论已被删除"
                    else -> response.message.ifEmpty { "删除失败" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCommentTop(
        aid: Long,
        rpid: Long,
        isCurrentlyTop: Boolean
    ): Result<Unit> = setCommentTopForSubject(
        oid = aid,
        type = 1,
        rpid = rpid,
        isCurrentlyTop = isCurrentlyTop,
    )

    suspend fun setCommentTopForSubject(
        oid: Long,
        type: Int,
        rpid: Long,
        isCurrentlyTop: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val response = api.setReplyTop(
                oid = oid,
                type = type,
                rpid = rpid,
                action = resolveReplyTopActionField(isCurrentlyTop),
                csrf = csrf
            )

            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "置顶操作失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun resolveReplyTopActionField(isCurrentlyTop: Boolean): Int {
        return if (isCurrentlyTop) 0 else 1
    }
    
    /**
     * [新增] 举报评论
     * @param reason 举报原因: 0=其他, 1=垃圾广告, 2=色情, 3=刷屏, 4=引战, 5=剧透, 6=政治, 7=人身攻击
     */
    suspend fun reportComment(aid: Long, rpid: Long, reason: Int, content: String = ""): Result<Unit> =
        reportCommentForSubject(oid = aid, type = 1, rpid = rpid, reason = reason, content = content)

    suspend fun reportCommentForSubject(
        oid: Long,
        type: Int,
        rpid: Long,
        reason: Int,
        content: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.reportReply(
                oid = oid,
                type = type,
                rpid = rpid,
                reason = reason,
                content = content,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    12008 -> "已经举报过了"
                    12019 -> "举报过于频繁"
                    else -> response.message.ifEmpty { "举报失败" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 按游客和作者视角检查一条已发布的评论。 */
    suspend fun checkCommentStatus(
        aid: Long,
        rpid: Long,
        rootId: Long = 0,
        type: Int = 1,
        manual: Boolean = true,
    ): Result<CommentFraudStatus> = withContext(Dispatchers.IO) {
        try {
            val status = if (rootId == 0L) {
                checkRootComment(aid, type, rpid, manual)
            } else {
                checkThreadReply(aid, type, rootId, rpid)
            }
            Result.success(status)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CoreDataLog.w("CommentFraud", "评论可见性查询失败: ${error.message}")
            Result.success(CommentFraudStatus.UNKNOWN)
        }
    }

    private fun ReplyItem.publicFraudStatus(): CommentFraudStatus =
        if (invisible) CommentFraudStatus.INVISIBLE else CommentFraudStatus.NORMAL

    private suspend fun checkRootComment(oid: Long, type: Int, rpid: Long, manual: Boolean): CommentFraudStatus {
        val publicList = guestApi.getReplyListMain(
            mapOf("oid" to oid.toString(), "type" to type.toString(), "mode" to "2", "pagination_str" to """{"offset":""}"""),
        )
        if (publicList.code != 0 || publicList.data == null) return CommentFraudStatus.UNKNOWN
        val listedReply = (publicList.data.replies.orEmpty() + publicList.data.collectTopReplies())
            .firstOrNull { it.rpid == rpid }
        if (listedReply != null) return listedReply.publicFraudStatus()

        val authorThread = api.getReplyReply(oid = oid, type = type, root = rpid, pn = 1)
        val authorRoot = authorThread.data?.root?.takeIf { authorThread.code == 0 && it.rpid == rpid }
        val authorProbe = CommentPresenceProbe(
            requestSucceeded = authorThread.code == 0 || authorThread.code == 12022,
            found = authorRoot != null,
            deletedHint = authorThread.code == 12022,
            invisible = authorRoot?.invisible == true,
        )
        val publicThread = if (authorRoot != null) {
            guestApi.getReplyReply(oid = oid, type = type, root = rpid, pn = 1)
        } else null
        val publicRoot = publicThread?.takeIf { it.code == 0 }?.data?.root?.takeIf { it.rpid == rpid }
        if (publicRoot?.invisible == true) return CommentFraudStatus.INVISIBLE
        return resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = authorProbe,
            guestReplyPageVisible = when {
                publicRoot != null -> true
                publicThread?.code == 12022 -> false
                else -> null
            },
            confirmedNotFoundAfterRetry = false,
            manual = manual,
        )
    }

    private suspend fun checkThreadReply(oid: Long, type: Int, root: Long, rpid: Long): CommentFraudStatus {
        val public = scanThreadForReply(guestApi, oid, type, root, rpid)
        if (!public.requestSucceeded) return CommentFraudStatus.UNKNOWN
        if (public.found) return resolveReplyFraudStatus(public, public, confirmedNotFoundAfterRetry = false)

        val author = scanThreadForReply(api, oid, type, root, rpid)
        return resolveReplyFraudStatus(public, author, confirmedNotFoundAfterRetry = false)
    }

    private suspend fun scanThreadForReply(
        client: BilibiliApi,
        oid: Long,
        type: Int,
        root: Long,
        rpid: Long,
    ): CommentPresenceProbe {
        var page = 1
        while (true) {
            val response = client.getReplyReply(oid = oid, type = type, root = root, pn = page, sort = 1)
            if (response.code == 12022) return CommentPresenceProbe(true, false, deletedHint = true)
            val data = response.data
            if (response.code != 0 || data == null) return CommentPresenceProbe(false, false)
            val replies = data.replies.orEmpty()
            val match = replies.firstOrNull { it.rpid == rpid }
            if (match != null) return CommentPresenceProbe(true, true, invisible = match.invisible)
            if (replies.isEmpty()) return CommentPresenceProbe(true, false)
            page++
        }
    }
}
