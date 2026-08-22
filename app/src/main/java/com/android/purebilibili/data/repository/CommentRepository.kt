package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.BilibiliApi
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.TreeMap

/**
 * 评论相关数据仓库
 * 从 VideoRepository 拆分出来，专注于评论功能
 */
object CommentRepository {
    private val api = NetworkModule.api
    private val guestApi = NetworkModule.guestApi
    private val commentJson = Json { ignoreUnknownKeys = true }

    // WBI Key 缓存
    private var wbiKeysCache: Pair<String, String>? = null
    private var wbiKeysTimestamp: Long = 0
    private const val WBI_CACHE_DURATION = 1000 * 60 * 30 // 30分钟缓存

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
    private suspend fun getWbiKeys(navApi: BilibiliApi = api): Pair<String, String> {
        val currentCheck = System.currentTimeMillis()
        val cached = wbiKeysCache
        if (cached != null && (currentCheck - wbiKeysTimestamp < WBI_CACHE_DURATION)) {
            return cached
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
                    
                    wbiKeysCache = Pair(imgKey, subKey)
                    wbiKeysTimestamp = System.currentTimeMillis()
                    com.android.purebilibili.core.util.Logger.d("CommentRepo", " WBI Keys obtained successfully (attempt $attempt)")
                    return wbiKeysCache!!
                }
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

    private suspend fun fetchCommentsByApi(
        apiClient: BilibiliApi,
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mode: Int,
        paginationOffset: String? = null
    ): ReplyResponse {
        return when (mode) {
            2 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=0 (时间)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 0
                )
            }
            1 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=2 (回复数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 2
                )
            }
            4 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=1 (点赞数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 1
                )
            }
            else -> {
                val (imgKey, subKey) = getWbiKeys(apiClient)
                Logger.d("CommentRepo", " getComments (WBI): oid=$oid, type=$type, page=$page, mode=3 (热度)")
                val params = TreeMap<String, String>()
                params["oid"] = oid.toString()
                params["type"] = type.toString()
                params["mode"] = "3"
                params["ps"] = ps.toString()
                params["plat"] = "1"
                params["web_location"] = "1315875"
                params.putAll(resolveCommentMainListPaginationParameters(page, paginationOffset))
                val signedParams = WbiUtils.sign(params, imgKey, subKey)
                apiClient.getReplyList(signedParams)
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
        Logger.d("CommentRepo", " getComments (CompatMain): oid=$oid, type=$type, page=$page, mode=3 (热度)")
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
        Logger.d(
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
            Logger.w(
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
            Logger.w(
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
     * 3=最热(WBI mode=3), 2=最新(legacy sort=0), 4=点赞(legacy sort=1), 1=回复(legacy sort=2)
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
        fallbackOnMissingLocation: Boolean = true
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            // 确保 buvid3 已初始化
            VideoRepository.ensureBuvid3()

            if (shouldTryGrpcMainList(type = type, page = page, mode = mode, paginationOffset = paginationOffset)) {
                val grpcResult = CommentGrpcRepository.getMainList(
                    oid = oid,
                    type = type,
                    mode = mode,
                    nextOffset = paginationOffset
                )
                if (grpcResult.isSuccess) {
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
                        Logger.w(
                            "CommentRepo",
                            "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, reason=empty-renderable-success"
                        )
                    } else if (!fallbackOnMissingLocation || !shouldFallbackGrpcCommentReadOnMissingLocation(grpcData)) {
                        Logger.d("CommentRepo", " getComments (gRPC MainList): oid=$oid, type=$type, page=$page, mode=$mode")
                        return@withContext grpcResult
                    } else {
                        Logger.w(
                            "CommentRepo",
                            "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, reason=missing-location"
                        )
                    }
                } else {
                    Logger.w(
                        "CommentRepo",
                        "getComments gRPC fallback to REST: oid=$oid, type=$type, page=$page, mode=$mode, error=${grpcResult.exceptionOrNull()?.message}"
                    )
                }
            }

            val hasSession = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
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
                Logger.w(
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
                val fallbackMode = readPlan.fallback
                Logger.w(
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
            Logger.d(
                "CommentRepo",
                " getComments result: oid=$oid, type=$type, mode=$mode($sortLabel), replies=${finalResponse.data?.replies?.size ?: 0}, code=${finalResponse.code}"
            )

            if (finalResponse.code == 0) {
                val data = finalResponse.data ?: ReplyData()
                Result.success(
                    data.copy(
                        grpcNextOffset = data.cursor.paginationReply?.nextOffset.orEmpty()
                    )
                )
            } else {
                val errorMsg = resolveCommentReadErrorMessage(finalResponse.code)
                android.util.Log.e("CommentRepo", " getComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommentRepo", " getComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCommentCountForSubject(
        oid: Long,
        type: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            VideoRepository.ensureBuvid3()
            val response = api.getReplyCount(oid = oid, type = type)
            if (response.code == 0) {
                Result.success(response.data?.count ?: 0)
            } else {
                Result.failure(Exception(resolveCommentReadErrorMessage(response.code)))
            }
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

    suspend fun getSubCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        page: Int,
        ps: Int = 20,
        paginationOffset: String? = null,
        preferRestPaging: Boolean = true
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            // 确保 buvid3 已初始化
            VideoRepository.ensureBuvid3()

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
                        Logger.d("CommentRepo", " getSubComments (gRPC DetailList): oid=$oid, type=$type, root=$rootId, page=$page")
                        return@withContext grpcResult
                    }
                    Logger.w(
                        "CommentRepo",
                        "getSubComments gRPC fallback to REST: oid=$oid, type=$type, root=$rootId, page=$page, reason=missing-location"
                    )
                } else {
                    Logger.w(
                        "CommentRepo",
                        "getSubComments gRPC fallback to REST: oid=$oid, type=$type, root=$rootId, page=$page, error=${grpcResult.exceptionOrNull()?.message}"
                    )
                }
            }
            
            Logger.d("CommentRepo", " getSubComments: oid=$oid, type=$type, rootId=$rootId, page=$page")
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
                val fallbackMode = readPlan.fallback
                Logger.w(
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
            
            Logger.d("CommentRepo", " getSubComments response: oid=$oid, type=$type, code=${finalResponse.code}, replies=${finalResponse.data?.replies?.size ?: 0}")
            
            if (finalResponse.code == 0) {
                Result.success(finalResponse.data ?: ReplyData())
            } else {
                android.util.Log.e("CommentRepo", " getSubComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                val errorMsg = resolveCommentReadErrorMessage(finalResponse.code)
                    .replace("评论", "回复")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommentRepo", " getSubComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDialogCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        dialogId: Long,
        page: Int,
        paginationOffset: String? = null
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            VideoRepository.ensureBuvid3()
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
            if (grpcResult.isSuccess) {
                Logger.d("CommentRepo", " getDialogComments (gRPC DialogList): oid=$oid, type=$type, root=$rootId, dialog=$dialogId, page=$page")
            }
            grpcResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取表情包映射
     */
    suspend fun getEmoteMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        // 默认表情
        map["[doge]"] = "http://i0.hdslb.com/bfs/emote/6f8743c3c13009f4705307b2750e32f5068225e3.png"
        map["[笑哭]"] = "http://i0.hdslb.com/bfs/emote/500b63b2f293309a909403a746566fdd6104d498.png"
        map["[妙啊]"] = "http://i0.hdslb.com/bfs/emote/03c39c8eb009f63568971032b49c716259c72441.png"
        try {
            val params = mutableMapOf("business" to "reply")
            
            val response = api.getEmotes(params)
            val packages = response.data?.packages ?: response.data?.all_packages
            packages?.forEach { pkg ->
                pkg.emote?.forEach { emote -> map[emote.text] = emote.url }
            }
        } catch (e: Exception) { e.printStackTrace() }
        map
    }

    /**
     * 获取表情包列表 (用于UI展示)
     */
    suspend fun getEmotePackages(): Result<List<EmotePackage>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf("business" to "reply")
            
            val response = api.getEmotes(params)
            if (response.code == 0) {
                val data = response.data
                val pkgs = data?.packages ?: data?.all_packages ?: emptyList()
                Result.success(pkgs)
            } else {
                Result.failure(Exception(response.message))
            }
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
        } catch (e: Exception) {
            Logger.e("CommentRepo", "searchMentionUsers exception: keyword=$keyword", e)
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
                    val serverPostTime = if (reply.ctime > 0L) reply.ctime * 1000L else System.currentTimeMillis()
                    val userUid = reply.mid
                    // [纯异步旁路] 在后台全局协程中静默存库，完全不卡主流程，零延迟返回
                    AppScope.ioScope.launch {
                        CommentFraudRepository.saveRecord(
                            rpid = reply.rpid,
                            oid = oid,
                            type = type,
                            root = root,
                            parent = parent,
                            uid = userUid,
                            message = message,
                            status = CommentFraudStatus.UNKNOWN, // 当前状态未知（检测中）
                            initialStatus = null, // 初始状态先置为 null (等待 5 秒后初检回填)
                            postTime = serverPostTime
                        )
                    }
                }
                // 立刻返回给 UI 渲染
                Result.success(reply)
            } else {
                Logger.e(
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
            Logger.e("CommentRepo", "addComment exception: oid=$oid, type=$type, root=$root, parent=$parent", e)
            Result.failure(e)
        }
    }

    /**
     * 上传评论图片，返回可用于评论 pictures 字段的元数据
     */
    suspend fun uploadCommentImage(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<ReplyPicture> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val mediaType = mimeType.toMediaType()
            val fileBody = bytes.toRequestBody(mediaType)
            val part = okhttp3.MultipartBody.Part.createFormData(
                "file_up",
                fileName.ifBlank { "comment_image.jpg" },
                fileBody
            )
            val textMedia = "text/plain".toMediaType()
            val categoryBody = "daily".toRequestBody(textMedia)
            val bizBody = "new_dyn".toRequestBody(textMedia)
            val csrfBody = csrf.toRequestBody(textMedia)

            val response = api.uploadCommentImage(
                fileUp = part,
                category = categoryBody,
                biz = bizBody,
                csrf = csrfBody
            )

            if (response.code == 0 && response.data != null) {
                val data = response.data
                Result.success(
                    ReplyPicture(
                        imgSrc = data.imageUrl,
                        imgWidth = data.imageWidth,
                        imgHeight = data.imageHeight,
                        imgSize = data.imgSize
                    )
                )
            } else {
                Logger.e(
                    "CommentRepo",
                    "uploadCommentImage failed: fileName=$fileName, mimeType=$mimeType, size=${bytes.size}, code=${response.code}, message=${response.message}"
                )
                Result.failure(Exception(response.message.ifEmpty { "图片上传失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            Logger.e(
                "CommentRepo",
                "uploadCommentImage exception: fileName=$fileName, mimeType=$mimeType, size=${bytes.size}",
                e
            )
            Result.failure(e)
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
        if (type == 17) return false
        val supportedMode = mode == CommentGrpcRepository.MODE_HOT || mode == CommentGrpcRepository.MODE_TIME
        if (!supportedMode) return false
        return shouldTryGrpcPagedRequest(page = page, paginationOffset = paginationOffset)
    }

    internal fun resolveCommentMainListPaginationParameters(
        page: Int,
        paginationOffset: String?
    ): Map<String, String> {
        if (page <= 1) {
            return mapOf(
                "seek_rpid" to "0",
                "pagination_str" to """{"offset":""}"""
            )
        }
        if (!paginationOffset.isNullOrBlank()) {
            return mapOf(
                "pagination_str" to commentJson.encodeToString(mapOf("offset" to paginationOffset))
            )
        }
        return mapOf("next" to page.toString())
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 点踩评论
     */
    suspend fun hateComment(aid: Long, rpid: Long, hate: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.hateReply(
                oid = aid,
                type = 1,
                rpid = rpid,
                action = if (hate) 1 else 0,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 删除评论
     */
    suspend fun deleteComment(aid: Long, rpid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.deleteReply(
                oid = aid,
                type = 1,
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val response = api.setReplyTop(
                oid = aid,
                type = 1,
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
    suspend fun reportComment(aid: Long, rpid: Long, reason: Int, content: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.reportReply(
                oid = aid,
                type = 1,
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

    // ==================== 评论反诈检测 (混合精准架构) ====================

    /** 默认等待时间（毫秒），发送评论后等待 B 站服务器主从数据库同步与初步风控处理 */
    private const val DEFAULT_WAIT_MS = 5000L
    /** 带图评论额外等待时间（图片涉及额外的人工/机器鉴黄与敏感图扫描队列） */
    private const val IMAGE_EXTRA_WAIT_MS = 10000L
    /** 删除判定前的二次确认等待（防止因 B 站分布式缓存短暂未命中引发的“假秒删”误报） */
    private const val DELETE_CONFIRM_RETRY_DELAY_MS = 2200L

    /**
     * 【专用路人 rawCurl 发包器】
     * 
     * 设计初衷与原理:
     * 1. 规避 Retrofit / CookieJar 封装层对纯匿名请求的上下文污染与伪匿名空数据拦截；
     * 2. 纯路人视角下，直通 B 站底层网络层，不携带任何用户登录凭证 (SESSDATA)；
     * 3. 强制注入经过官方 SPI 接口激活的合法设备访客指纹 (buvid3) 与标准浏览器请求头，
     *    使发包行为与终端真实 `curl` 完全一致，100% 还原纯路人视角的客观真值。
     *
     * @param url 请求的完整目标 URL
     * @return 服务器返回的原始 JSON 字符串；若请求失败或超时则返回 null
     */
    private suspend fun rawCurlGuest(url: String): String? = withContext(Dispatchers.IO) {
        val buvid = com.android.purebilibili.core.store.TokenManager.buvid3Cache
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Origin", "https://www.bilibili.com")
            .header("Referer", "https://www.bilibili.com")
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                if (!buvid.isNullOrBlank()) {
                    header("Cookie", "buvid3=$buvid;")
                }
            }
            .build()

        try {
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "rawCurlGuest 异常: ${e.message}")
            null
        }
    }

    /**
     * 启动评论反诈全生命周期状态检测
     *
     * 核心裁决模型 (参考 biliSendCommAntifraud 规范):
     * - 评论正常: 路人视角在公开评论列表中可直接检索到目标 rpid；
     * - 仅自己可见 (ShadowBan): 路人视角不可见（或接口提示已删除），但作者自身账号视角可见；
     * - 系统秒删: 路人视角与作者账号视角均提示“已被删除”或完全未命中；
     * - 疑似审核中: 主列表路人不可见，但通过单条回复页接口路人与作者均可正常获取；
     * - 软屏蔽 (Invisible): 接口数据客观存在，但字段标记 `invisible=true`（前端被强制隐藏）。
     *
     * @param aid 稿件/视频 ID (oid)
     * @param rpid 待检测的评论唯一标识 ID
     * @param rootId 根评论 rpid (0 表示本身即为一级根评论；非 0 表示楼中楼子回复)
     * @param hasPictures 评论是否包含图片（包含图片将延长初始等待时间）
     * @param sentAtSeconds 官方发评时间戳（秒级 ctime），用于远古楼层时序二分收敛算法
     * @param waitMs 自定义等待缓冲时间（毫秒，传入 0 时表示立即发起检测，如手动复检场景）
     * @return CommentFraudStatus 裁决状态枚举
     */
    suspend fun checkCommentStatus(
        aid: Long,
        rpid: Long,
        rootId: Long = 0,
        hasPictures: Boolean = false,
        sentAtSeconds: Long = 0,
        waitMs: Long = -1
    ): Result<CommentFraudStatus> = withContext(Dispatchers.IO) {
        try {
            // 确保本地设备访客指纹库 (buvid3) 已准备就绪
            VideoRepository.ensureBuvid3()

            // 等待分布式系统主从同步缓冲期
            val actualWait = when {
                waitMs >= 0 -> waitMs
                hasPictures -> DEFAULT_WAIT_MS + IMAGE_EXTRA_WAIT_MS
                else -> DEFAULT_WAIT_MS
            }
            if (actualWait > 0) {
                Logger.d("CommentFraud", "等待 ${actualWait}ms 后开始检测...")
                delay(actualWait)
            }

            val isReply = rootId > 0
            Logger.d("CommentFraud", "开始检测: aid=$aid, rpid=$rpid, root=$rootId, isReply=$isReply")

            if (isReply) {
                Result.success(checkReplyComment(aid, rpid, rootId, sentAtSeconds))
            } else {
                Result.success(checkRootComment(aid, rpid))
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "检测异常: ${e.message}", e)
            Result.success(CommentFraudStatus.UNKNOWN)
        }
    }

    /**
     * [混合精准架构] 检查二级评论（楼中楼子回复）的风控存活状态
     *
     * 算法步骤与数学模型:
     * 1. 【第 1 页元数据探测】：使用 rawCurl 探测第 1 页，读取楼层总量。
     *    特别注意：B 站接口的 `page.count` 往往仅返回单页窗口值（如 20），真实的楼层总数记录在
     *    `root.rcount` 或 `root.count` 中，必须取最大值避免跳页偏倚；
     * 2. 【末页直跳（极速通道）】：对于新发评论（占 95% 场景），由于 `sort=0`（时间正序）规则，
     *    最新回复在数学上必定排在最末尾，直跳末页（及前一页容错）即可在 2 次请求内瞬间命中；
     * 3. 【单调时序折半二分算法（远古回溯通道）】：对于数月前发布且楼层大幅暴涨的远古评论，
     *    利用评论时间戳 `ctime` 严格单调递增的物理法则，在 $O(\log N)$ 复杂度内（最多 5 次二分折半）
     *    自适应快速收敛定位至历史所在页，彻底攻克非均匀时间分布下的远古评论定位难题；
     * 4. 【账号视角精准复验】：若路人端未命中，使用带登录凭证的原生 Retrofit API 对目标页进行复查，
     *    严格区分 ShadowBan 与真实秒删。
     */
    private suspend fun checkReplyComment(
        aid: Long,
        rpid: Long,
        rootId: Long,
        sentAtSeconds: Long = 0L
    ): CommentFraudStatus {
        Logger.d("CommentFraud", "[楼中楼] Step1: rawCurl 获取路人视角总量 aid=$aid root=$rootId rpid=$rpid")

        // 1. 路人 rawCurl 请求第 1 页
        val firstPageUrl = "https://api.bilibili.com/x/v2/reply/reply?oid=$aid&type=1&root=$rootId&pn=1&ps=20"
        val firstPageJson = rawCurlGuest(firstPageUrl) ?: return CommentFraudStatus.UNKNOWN

        // 根评论不存在或已被主站物理删除
        if (firstPageJson.contains("\"code\":12022") || firstPageJson.contains("\"code\": 12022")) {
            Logger.d("CommentFraud", "[楼中楼] 根评论已失效(12022)，判定秒删")
            return CommentFraudStatus.DELETED
        }

        // 2. 提取真实总数（防 page.count=20 分页窗口假象陷阱）并计算最后一页
        val rcountMatch = Regex(""""rcount":\s*(\d+)""").find(firstPageJson)
        val countMatch = Regex(""""count":\s*(\d+)""").find(firstPageJson)
        val totalCount = maxOf(
            rcountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            20
        )
        val lastPage = maxOf(1, (totalCount + 19) / 20)
        Logger.d("CommentFraud", "[楼中楼] Step2: 动态计算总量=$totalCount, 末页=第${lastPage}页")

        val rpidPattern = Regex(""""rpid":\s*${rpid}""")
        var guestFound = false
        var guestInvisible = false
        var targetPage = lastPage

        // 3. 路人 rawCurl 优先探测最后一页
        val lastPageUrl = "https://api.bilibili.com/x/v2/reply/reply?oid=$aid&type=1&root=$rootId&pn=$lastPage&ps=20"
        val lastPageJson = rawCurlGuest(lastPageUrl)

        if (lastPageJson != null && rpidPattern.containsMatchIn(lastPageJson)) {
            guestFound = true
            guestInvisible = lastPageJson.contains(""""rpid":\s*${rpid}[^}]*?"invisible":\s*true""")
        } else if (lastPage > 1) {
            // 倒数第 2 页容差探测（应对高频并发发评导致的页码临界偏移）
            val prevPageUrl = "https://api.bilibili.com/x/v2/reply/reply?oid=$aid&type=1&root=$rootId&pn=${lastPage - 1}&ps=20"
            val prevPageJson = rawCurlGuest(prevPageUrl)
            if (prevPageJson != null && rpidPattern.containsMatchIn(prevPageJson)) {
                guestFound = true
                guestInvisible = prevPageJson.contains(""""rpid":\s*${rpid}[^}]*?"invisible":\s*true""")
                targetPage = lastPage - 1
            }
        }

        // 4. 单调时序折半二分定位（若楼层暴涨且末页未命中）
        if (!guestFound && lastPage > 2 && sentAtSeconds > 0L) {
            var low = 1
            var high = lastPage - 2
            var steps = 0
            val maxBinarySteps = 5 // 限制最大二分探测次数为 5 次（覆盖 32 页/640 楼，兼顾性能与防频控）

            Logger.d("CommentFraud", "[时序二分] 末页未命中，启动二分收敛定位: 目标时间=$sentAtSeconds, 区间=[$low, $high]")

            while (low <= high && steps < maxBinarySteps) {
                steps++
                val mid = (low + high) / 2
                val midUrl = "https://api.bilibili.com/x/v2/reply/reply?oid=$aid&type=1&root=$rootId&pn=$mid&ps=20"
                val midJson = rawCurlGuest(midUrl) ?: break
                
                if (rpidPattern.containsMatchIn(midJson)) {
                    guestFound = true
                    guestInvisible = midJson.contains(""""rpid":\s*${rpid}[^}]*?"invisible":\s*true""")
                    targetPage = mid
                    Logger.d("CommentFraud", "[时序二分] 🎯 命中！在第 $mid 页成功捕获历史目标！")
                    break
                }

                // 提取本页首尾时间戳，按单调性调整二分搜索区间
                val ctimeMatches = Regex(""""ctime":\s*(\d+)""").findAll(midJson).mapNotNull { it.groupValues[1].toLongOrNull() }.toList()
                val firstCtime = ctimeMatches.firstOrNull() ?: 0L
                val lastCtime = ctimeMatches.lastOrNull() ?: 0L

                if (firstCtime > 0L && lastCtime > 0L) {
                    if (sentAtSeconds < firstCtime) {
                        high = mid - 1 // 目标时间更早，收缩到左半区
                    } else if (sentAtSeconds > lastCtime) {
                        low = mid + 1  // 目标时间更晚，收缩到右半区
                    } else {
                        targetPage = mid // 目标时间落于本页时间跨度内但未匹配上，标记本页并结束二分
                        break
                    }
                } else {
                    break
                }
            }
        }

        val guestProbe = CommentPresenceProbe(
            requestSucceeded = lastPageJson != null,
            found = guestFound,
            invisible = guestInvisible
        )

        // 若路人视角在目标页成功搜出，直接裁决状态
        if (guestProbe.requestSucceeded && guestProbe.found) {
            val status = resolveReplyFraudStatus(
                guestProbe = guestProbe,
                authProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
                confirmedNotFoundAfterRetry = false
            )
            Logger.d("CommentFraud", "[楼中楼] ✅ 路人 rawCurl 命中(第 ${targetPage} 页)，最终判定=$status")
            return status
        }

        // 5. 路人端未命中，使用带有用户登录态的原生 Retrofit API 对收敛的目标页进行账号视角复验
        Logger.d("CommentFraud", "[楼中楼] Step3: 原生 auth 账号视角对第 $targetPage 页复验 rpid=$rpid")
        var authFound = false
        var authInvisible = false
        var authRequestSucceeded = false
        try {
            val authResp = api.getReplyReply(oid = aid, type = 1, root = rootId, pn = targetPage, ps = 20)
            if (authResp.code == 0) {
                authRequestSucceeded = true
                var match = findTargetRpid(authResp.data, rpid)
                if (!match.found && targetPage > 1) {
                    val authPrevResp = api.getReplyReply(oid = aid, type = 1, root = rootId, pn = targetPage - 1, ps = 20)
                    if (authPrevResp.code == 0) match = findTargetRpid(authPrevResp.data, rpid)
                }
                authFound = match.found
                authInvisible = match.invisible
            }
        } catch (e: Exception) {
            Logger.w("CommentFraud", "auth 探测异常: ${e.message}")
        }

        val authProbe = CommentPresenceProbe(
            requestSucceeded = authRequestSucceeded,
            found = authFound,
            invisible = authInvisible
        )

        val finalStatus = resolveReplyFraudStatus(
            guestProbe = guestProbe,
            authProbe = authProbe,
            confirmedNotFoundAfterRetry = !authFound
        )
        Logger.d(
            "CommentFraud",
            "[楼中楼] 判定结果=$finalStatus guest=$guestProbe auth=$authProbe (目标页: $targetPage)"
        )
        return finalStatus
    }

    /**
     * [混合精准架构] 检查根评论（一级主评论）的风控存活状态
     *
     * 流程:
     * 1) guest 视角：使用 rawCurl 请求主列表第 1 页 (next=0, mode=2 时间倒序)，纯正则匹配绝对真值；
     * 2) guest 未命中时：auth 账号视角使用带有官方 WBI 签名的 seek_rpid 接口精确复验；
     * 3) auth 可见而 guest 不可见时：使用 guest 视角请求单条回复详情页，精确区分 ShadowBan / 疑似审核中；
     * 4) 仅在双端持续未命中时：触发 2.2 秒延迟二次探测以防数据库缓存抖动，最终定性为系统秒删。
     */
    private suspend fun checkRootComment(aid: Long, rpid: Long): CommentFraudStatus {
        Logger.d("CommentFraud", "[根评论] Step1: guest rawCurl 探测 rpid=$rpid")

        val rpidPattern = Regex(""""rpid":\s*${rpid}""")

        // 1. 路人 rawCurl 请求第 1 页时间倒序 (next=0 为官方第 1 页起始)
        val guestRootUrl = "https://api.bilibili.com/x/v2/reply/main?oid=$aid&type=1&mode=2&next=0&ps=20"
        val guestRootJson = rawCurlGuest(guestRootUrl)

        val guestSeekProbe = CommentPresenceProbe(
            requestSucceeded = guestRootJson != null,
            found = guestRootJson != null && rpidPattern.containsMatchIn(guestRootJson),
            invisible = guestRootJson?.contains(""""rpid":\s*${rpid}[^}]*?"invisible":\s*true""") == true
        )

        if (guestSeekProbe.requestSucceeded && guestSeekProbe.found) {
            Logger.d("CommentFraud", "[根评论] ✅ 根评论路人 rawCurl 命中！")
            return resolveRootFraudStatus(
                guestSeekProbe = guestSeekProbe,
                authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
                guestReplyPageVisible = null,
                confirmedNotFoundAfterRetry = false
            )
        }

        // 2. 路人未找到，原生 Retrofit API 账号视角复验
        Logger.d("CommentFraud", "[根评论] Step2: 原生 auth 账号视角复查 rpid=$rpid")
        val authSeekProbe = probeCommentPresenceBySeekRpid(
            apiClient = api,
            aid = aid,
            targetRpid = rpid
        )

        // 3. 区分 ShadowBan 与疑似审核中（通过单条回复页探测）
        var guestReplyPageVisible: Boolean? = null
        if (authSeekProbe.requestSucceeded && authSeekProbe.found) {
            Logger.d("CommentFraud", "[根评论] Step3: guest 回复页检测 root=$rpid")
            val guestReplyUrl = "https://api.bilibili.com/x/v2/reply/reply?oid=$aid&root=$rpid&pn=1&ps=1"
            val guestReplyJson = rawCurlGuest(guestReplyUrl)
            if (guestReplyJson != null) {
                guestReplyPageVisible = when {
                    guestReplyJson.contains("\"code\":12022") || guestReplyJson.contains("\"code\": 12022") -> false
                    guestReplyJson.contains("\"code\":0") || guestReplyJson.contains("\"code\": 0") -> true
                    else -> null
                }
            }
        }

        // 4. 二次确认防止假秒删
        val confirmedNotFoundAfterRetry = if (guestSeekProbe.requestSucceeded &&
            !guestSeekProbe.found &&
            authSeekProbe.requestSucceeded &&
            !authSeekProbe.found &&
            !authSeekProbe.deletedHint
        ) {
            Logger.d("CommentFraud", "[根评论] Step4: 二次确认未命中，避免瞬时误判")
            confirmDeletedBySecondProbe(aid = aid, rpid = rpid)
        } else {
            false
        }

        val status = resolveRootFraudStatus(
            guestSeekProbe = guestSeekProbe,
            authSeekProbe = authSeekProbe,
            guestReplyPageVisible = guestReplyPageVisible,
            confirmedNotFoundAfterRetry = confirmedNotFoundAfterRetry
        )
        Logger.d(
            "CommentFraud",
            "[根评论] 判定结果=$status guestSeek=$guestSeekProbe authSeek=$authSeekProbe guestReply=$guestReplyPageVisible"
        )
        return status
    }

    /**
     * 使用 WBI 签名和 seek_rpid 参数精确探测评论是否存在
     * (专用于带有合法 SESSDATA 凭证的 auth 账号视角复验，规避风控拦截)
     */
    private suspend fun probeCommentPresenceBySeekRpid(
        apiClient: BilibiliApi,
        aid: Long,
        targetRpid: Long
    ): CommentPresenceProbe {
        return try {
            val (imgKey, subKey) = getWbiKeys()
            val params = TreeMap<String, String>().apply {
                put("oid", aid.toString())
                put("type", "1")
                put("mode", "2") // 时间排序
                put("next", "0") // 必须是 0（第 1 页起始）
                put("ps", "20")
                put("seek_rpid", targetRpid.toString())
            }
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = apiClient.getReplyList(signedParams)
            when (response.code) {
                0 -> {
                    val match = findTargetRpid(response.data, targetRpid)
                    CommentPresenceProbe(
                        requestSucceeded = true,
                        found = match.found,
                        deletedHint = false,
                        invisible = match.invisible
                    )
                }
                12022, 12009 -> {
                    CommentPresenceProbe(
                        requestSucceeded = true,
                        found = false,
                        deletedHint = true
                    )
                }
                else -> {
                    Logger.w("CommentFraud", "seek_rpid probe failed: code=${response.code}, message=${response.message}")
                    CommentPresenceProbe(
                        requestSucceeded = false,
                        found = false,
                        deletedHint = false
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "seek_rpid probe exception: ${e.message}")
            CommentPresenceProbe(
                requestSucceeded = false,
                found = false,
                deletedHint = false
            )
        }
    }

    /**
     * 评论匹配结果包装类
     */
    private data class CommentTargetMatch(
        val found: Boolean,
        val invisible: Boolean
    )

    /**
     * 在解析好的 ReplyData 数据树中深度递归查找目标 rpid
     * （支持扫描主列表、热门列表、置顶列表与楼中楼嵌套预览子集）
     */
    private fun findTargetRpid(data: ReplyData?, targetRpid: Long): CommentTargetMatch {
        if (targetRpid <= 0L || data == null) return CommentTargetMatch(false, false)

        fun match(reply: ReplyItem): CommentTargetMatch? {
            if (reply.rpid == targetRpid) return CommentTargetMatch(true, reply.invisible)
            reply.replies.orEmpty().forEach { sub ->
                if (sub.rpid == targetRpid) return CommentTargetMatch(true, sub.invisible)
            }
            return null
        }

        data.replies.orEmpty().forEach { reply -> match(reply)?.let { return it } }
        data.hots.orEmpty().forEach { reply -> match(reply)?.let { return it } }
        data.collectTopReplies().forEach { reply -> match(reply)?.let { return it } }
        return CommentTargetMatch(false, false)
    }

    /**
     * [防抖容差机制] 二次确认评论是否被真实删除
     * 延迟 2.2 秒后再次发起双端探测，避免由于 B 站主从数据库复制延迟导致的“假秒删”误报
     */
    private suspend fun confirmDeletedBySecondProbe(aid: Long, rpid: Long): Boolean {
        delay(DELETE_CONFIRM_RETRY_DELAY_MS)
        val guestRootUrl = "https://api.bilibili.com/x/v2/reply/main?oid=$aid&type=1&mode=2&next=0&ps=20"
        val guestRetryJson = rawCurlGuest(guestRootUrl)
        val rpidPattern = Regex(""""rpid":\s*${rpid}""")
        if (guestRetryJson == null || rpidPattern.containsMatchIn(guestRetryJson)) {
            return false
        }
        val authRetryProbe = probeCommentPresenceBySeekRpid(
            apiClient = api,
            aid = aid,
            targetRpid = rpid
        )
        return authRetryProbe.requestSucceeded && !authRetryProbe.found
    }
}
