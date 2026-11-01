package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.refresh.WatchLaterRefreshBus
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.model.response.RecommendationFeedbackMetadata
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.RecommendationFeedbackType
import com.android.purebilibili.data.model.response.WatchLaterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

private const val FAVORITE_SEASON_PATH = "x/v3/fav/season/fav"
private const val UNFAVORITE_SEASON_PATH = "x/v3/fav/season/unfav"
private const val COLLECTION_SUBSCRIPTION_PLATFORM = "web"

internal fun isWatchLaterAid(items: List<WatchLaterItem>, aid: Long): Boolean {
    return aid > 0L && items.any { it.aid == aid }
}

internal data class CollectionSubscriptionRequest(
    val path: String,
    val seasonId: Long,
    val platform: String,
    val csrf: String
)

internal data class RecommendationFeedbackRequest(
    val goto: String,
    val resourceId: String,
    val reasonId: Long?,
    val feedbackId: Long?,
    val trackId: String = "",
    val spmid: String = "tm.recommend.0.0",
    val creatorMid: Long = 0L,
)

internal fun buildRecommendationFeedbackRequest(
    metadata: RecommendationFeedbackMetadata,
    reason: RecommendationFeedbackReason
): RecommendationFeedbackRequest? {
    val reasonId = reason.id?.takeIf { it > 0L } ?: return null
    if (!metadata.supportsServerSync || metadata.goto.isBlank() || metadata.param.isBlank()) {
        return null
    }
    return RecommendationFeedbackRequest(
        goto = metadata.goto,
        resourceId = metadata.param,
        reasonId = reasonId.takeIf { reason.type == RecommendationFeedbackType.DISLIKE },
        feedbackId = reasonId.takeIf { reason.type == RecommendationFeedbackType.FEEDBACK },
        trackId = metadata.trackId,
        spmid = metadata.spmid,
        creatorMid = metadata.creatorMid,
    )
}

internal fun buildRecommendationFeedbackParams(
    request: RecommendationFeedbackRequest,
    accessToken: String,
    timestamp: Long,
    tokenPlatform: String = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
): Map<String, String> {
    require((request.reasonId != null) xor (request.feedbackId != null)) {
        "reason_id 与 feedback_id 必须且只能提供一个"
    }
    return buildMap {
        put("goto", request.goto)
        put("id", request.resourceId)
        request.reasonId?.let { put("reason_id", it.toString()) }
        request.feedbackId?.let { put("feedback_id", it.toString()) }
        // 官方 /x/feed/dislike 还带这几个：没有 spmid 服务端不知道反馈来自首页推荐，
        // 没有 track_id 就无法把反馈关联到具体那条推荐卡片。
        put("spmid", request.spmid)
        request.trackId.takeIf { it.isNotBlank() }?.let { put("track_id", it) }
        request.creatorMid.takeIf { it > 0L }?.let { put("mid", it.toString()) }
        request.resourceId.takeIf { it.isNotBlank() }?.let { put("rid", it) }
        put("build", "1")
        put("mobi_app", if (tokenPlatform == TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD) "android_hd" else "android")
        put("access_key", accessToken)
        put("appkey", when (tokenPlatform) {
            TokenManager.ACCESS_TOKEN_PLATFORM_TV -> AppSignUtils.TV_APP_KEY
            TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID -> AppSignUtils.ANDROID_APP_KEY
            TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD -> AppSignUtils.ANDROID_HD_APP_KEY
            else -> throw IllegalArgumentException("当前授权不支持推荐反馈")
        })
        put("ts", timestamp.toString())
    }
}

internal fun buildCollectionSubscriptionRequest(
    seasonId: Long,
    subscribe: Boolean,
    csrf: String
): CollectionSubscriptionRequest {
    return CollectionSubscriptionRequest(
        path = if (subscribe) FAVORITE_SEASON_PATH else UNFAVORITE_SEASON_PATH,
        seasonId = seasonId,
        platform = COLLECTION_SUBSCRIPTION_PLATFORM,
        csrf = csrf
    )
}

data class FollowStateChange(
    val mid: Long,
    val isFollowing: Boolean
)

/**
 * 用户操作相关 Repository
 * - 关注/取关 UP 主
 * - 收藏/取消收藏视频
 */
internal class VideoInteractionStatusCache(
    private val capacity: Int = 256,
    /**
     * 已知结果的保鲜期。详情页首帧只依赖这份缓存预填点赞/关注状态，
     * 原先 60s 太短，冷启动或稍后重进都会退化成"状态未知"。
     * 真实状态仍由后台请求校正，长保鲜期只影响首帧默认值。
     */
    private val ttlMs: Long = 30 * 60_000L,
    /** 进行中操作的兜底失效时间：操作卡死时不能长期挡住后续查询与回填。 */
    private val pendingTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Entry(
        val value: Boolean?,
        val version: Long,
        val createdAt: Long,
        val pending: Boolean = false,
        val operation: Boolean = false,
        val operationStartedAt: Long? = null
    )
    private var session: Any? = null
    private var sequence = 0L
    private val values = LinkedHashMap<String, Entry>()

    init {
        require(capacity > 0 && ttlMs > 0 && pendingTtlMs > 0)
    }

    @Synchronized
    fun read(account: Any, key: String): Pair<Boolean?, Long> {
        if (session != account) {
            session = account
            values.clear()
            ++sequence
        }
        val now = nowMs()
        val cached = values[key]
        val entry = when {
            cached == null -> Entry(null, ++sequence, now)
            // 进行中的操作按 pendingTtlMs 判定新鲜度，与值的保鲜期解耦：
            // 延长值保鲜期不能让一个卡死的操作长期挡住后续查询与回填。
            cached.pending -> {
                val operationFresh = cached.operationStartedAt?.let { now - it in 0 until pendingTtlMs } == true
                when {
                    !operationFresh -> Entry(null, ++sequence, now)
                    now - cached.createdAt in 0 until pendingTtlMs -> cached
                    else -> cached.copy(value = null)
                }
            }
            now - cached.createdAt !in 0 until ttlMs -> Entry(null, ++sequence, now)
            else -> cached
        }
        put(key, entry)
        return entry.value to entry.version
    }

    @Synchronized
    fun beginOperation(account: Any, key: String): Long {
        read(account, key)
        val entry = requireNotNull(values[key])
        val version = ++sequence
        put(key, entry.copy(version = version, pending = true, operation = true, operationStartedAt = nowMs()))
        return version
    }

    @Synchronized
    fun publish(account: Any, key: String, value: Boolean, version: Long, query: Boolean = false): Boolean {
        if (session != account || read(account, key).second != version) return false
        val entry = values[key] ?: return false
        if (query && (entry.pending || entry.operation)) return false
        if (!query && entry.operation && !entry.pending) return false
        put(key, Entry(value, version, nowMs(), operation = entry.operation))
        return true
    }

    @Synchronized
    fun queryVersion(account: Any, key: String): Long? {
        val version = read(account, key).second
        values[key]?.let {
            if (it.pending || (it.operation && it.value != null)) return null
            if (it.operation) put(key, it.copy(version = ++sequence, operation = false))
        }
        return values[key]?.version ?: version
    }

    @Synchronized
    fun confirmedOperationValue(account: Any, key: String): Boolean? {
        read(account, key)
        return values[key]?.takeIf { it.operation && !it.pending }?.value
    }

    @Synchronized
    fun finishOperation(account: Any, key: String, version: Long) {
        if (session != account || read(account, key).second != version) return
        values[key]?.let {
            put(key, if (it.pending) it.copy(version = ++sequence, pending = false, operation = false)
                else it)
        }
    }

    @Synchronized
    fun knownValues(account: Any, prefix: String): Map<String, Boolean> {
        if (session != account) return emptyMap()
        return values.keys.filter { it.startsWith(prefix) }.mapNotNull { key ->
            read(account, key).first?.let { key to it }
        }.toMap()
    }

    private fun put(key: String, value: Entry) {
        values.remove(key)
        values[key] = value
        while (values.size > capacity) values.remove(values.keys.first())
    }
}

internal class FollowingSnapshotGuard {
    private data class State(
        var revision: Long = 0L,
        var pending: Int = 0
    )

    private val states = mutableMapOf<Any, State>()
    private var sequence = 0L

    @Synchronized
    fun beginOperation(account: Any) {
        val state = states.getOrPut(account) { State(revision = ++sequence) }
        ++state.pending
        state.revision = ++sequence
    }

    @Synchronized
    fun finishOperation(account: Any) {
        val state = states[account] ?: return
        check(state.pending > 0)
        --state.pending
        state.revision = ++sequence
        if (state.pending == 0 && states.size > 1) {
            states.remove(account)
        }
    }

    @Synchronized
    fun snapshotVersion(account: Any): Long? {
        val state = states.getOrPut(account) { State(revision = ++sequence) }
        return state.revision.takeIf { state.pending == 0 }
    }

    @Synchronized
    fun accept(account: Any, version: Long, publish: () -> Unit): Boolean {
        val state = states[account] ?: return false
        if (state.pending != 0 || state.revision != version) return false
        publish()
        return true
    }
}

object ActionRepository {
    private val interactionCache = VideoInteractionStatusCache()
    private val followingGuard = FollowingSnapshotGuard()
    fun followingStatusRevision(account: Any): Long? = followingGuard.snapshotVersion(account)
    fun acceptFollowingSnapshot(account: Any, version: Long, publish: () -> Unit): Boolean =
        followingGuard.accept(account, version, publish)
    fun interactionSession(): Any = TokenManager.accountIdentity.value to TokenManager.sessDataCache
    fun cachedLikeStatus(aid: Long): Boolean? = interactionCache.read(interactionSession(), "like:$aid").first
    fun cachedFollowStatus(mid: Long): Boolean? = interactionCache.read(interactionSession(), "follow:$mid").first
    fun likeStatusVersion(aid: Long): Long = interactionCache.read(interactionSession(), "like:$aid").second
    fun followStatusVersion(mid: Long): Long = interactionCache.read(interactionSession(), "follow:$mid").second

    fun mergeKnownFollowingMids(account: Any, mids: Set<Long>): Set<Long> {
        val merged = mids.toMutableSet()
        interactionCache.knownValues(account, "follow:").forEach { (key, following) ->
            val mid = key.removePrefix("follow:").toLong()
            if (following) merged.add(mid) else merged.remove(mid)
        }
        return merged
    }

    private fun <T> Result<T>.rethrowCancellation(): Result<T> {
        (exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let { throw it }
        return this
    }

    suspend fun queryLikeStatus(aid: Long): Result<Boolean> {
        val session = interactionSession()
        val version = interactionCache.queryVersion(session, "like:$aid")
            ?: return interactionCache.confirmedOperationValue(session, "like:$aid")
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("点赞操作尚未完成"))
        return UserActionRepository.liked(aid).rethrowCancellation().mapCatching {
            if (session != interactionSession() || !interactionCache.publish(session, "like:$aid", it, version, query = true)) {
                throw IllegalStateException("点赞查询已过期")
            }
            it
        }
    }

    suspend fun queryFollowStatus(mid: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val session = interactionSession()
        val version = interactionCache.queryVersion(session, "follow:$mid")
            ?: return@withContext interactionCache.confirmedOperationValue(session, "follow:$mid")
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("关注操作尚未完成"))
        try {
            val response = api.getRelation(mid)
            val data = response.data
            if (response.code != 0 || data == null) {
                Result.failure(Exception(response.message.ifBlank { "关注状态获取失败" }))
            } else {
                val following = data.isFollowing
                if (session != interactionSession() || !interactionCache.publish(session, "follow:$mid", following, version, query = true)) {
                    Result.failure(IllegalStateException("关注查询已过期"))
                } else Result.success(following)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val api = NetworkModule.api
    private val _followStateChanges = MutableSharedFlow<FollowStateChange>(extraBufferCapacity = 32)
    val followStateChanges = _followStateChanges.asSharedFlow()
    private const val SPECIAL_FOLLOW_TAG_ID = -10L
    private const val ALL_FOLLOW_TAG_ID = -20L
    private const val FOLLOW_GROUP_BATCH_SIZE = 20
    private const val FOLLOW_GROUP_MAX_RETRIES = 3
    private const val FOLLOW_GROUP_REQUEST_INTERVAL_MS = 220L
    private const val FOLLOW_GROUP_RETRY_BASE_DELAY_MS = 900L
    private const val FOLLOW_GROUP_QUERY_MAX_RETRIES = 3
    private const val FOLLOW_GROUP_QUERY_RETRY_BASE_DELAY_MS = 600L
    private const val FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE = 100
    private const val FOLLOW_GROUP_TAG_MEMBERS_MAX_PAGES = 120

    suspend fun submitRecommendationFeedback(
        metadata: RecommendationFeedbackMetadata,
        reason: RecommendationFeedbackReason
    ): Result<Unit> = withContext(Dispatchers.IO) {
        TokenManager.awaitRestore()
        val account = try {
            com.android.purebilibili.core.network.purposeAccount(com.android.purebilibili.core.store.AccountPurpose.RECOMMENDATION)
        } catch (e: java.io.IOException) { return@withContext Result.failure(e) }
        val session = account?.let { com.android.purebilibili.core.network.AccountCookieSession.from(it) }
            ?: com.android.purebilibili.core.network.AccountCookieSession.main()
        val accessToken = (if (account != null) account.accessToken else TokenManager.accessTokenCache)
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(Exception("缺少移动端登录凭证"))
        val request = buildRecommendationFeedbackRequest(metadata, reason)
            ?: return@withContext Result.failure(Exception("当前推荐不支持服务器同步"))
        try {
            val tokenPlatform = account?.accessTokenPlatform ?: TokenManager.accessTokenPlatformCache
            val params = buildRecommendationFeedbackParams(
                request = request,
                accessToken = accessToken,
                timestamp = AppSignUtils.getTimestamp(),
                tokenPlatform = tokenPlatform,
            )
            val signed = when (tokenPlatform) {
                TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD -> AppSignUtils.signForAndroidHdLogin(params)
                TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID -> AppSignUtils.signForAndroidApi(params)
                else -> AppSignUtils.signForTvLogin(params)
            }
            val response = api.submitMobileFeedDislike(signed, session)
            com.android.purebilibili.core.util.Logger.d(
                "FeedDislike",
                "response code=${response.code} msg=${response.message} id=${signed["id"]}",
            )
            if (response.code != 0) {
                throw Exception(response.message.ifBlank { "反馈失败: ${response.code}" })
            }
            Result.success(Unit)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun normalizeRelationTagIds(raw: Set<Long>): Set<Long> {
        return raw.asSequence().filter { it != 0L }.toSet()
    }

    private fun normalizeRelationTags(
        raw: List<com.android.purebilibili.data.model.response.RelationTagItem>
    ): List<com.android.purebilibili.data.model.response.RelationTagItem> {
        val merged = raw.distinctBy { it.tagid }.toMutableList()
        if (merged.none { it.tagid == SPECIAL_FOLLOW_TAG_ID }) {
            merged += com.android.purebilibili.data.model.response.RelationTagItem(
                tagid = SPECIAL_FOLLOW_TAG_ID,
                name = "特别关注",
                count = 0,
                tip = "第一时间收到该分组下用户更新稿件的通知"
            )
        }
        return merged.sortedBy { it.tagid != SPECIAL_FOLLOW_TAG_ID }
    }

    internal fun chunkFollowGroupTargetMids(
        targetMids: Set<Long>,
        chunkSize: Int = FOLLOW_GROUP_BATCH_SIZE
    ): List<List<Long>> {
        if (targetMids.isEmpty()) return emptyList()
        val effectiveChunkSize = chunkSize.coerceAtLeast(1)
        return targetMids
            .asSequence()
            .filter { it > 0L }
            .distinct()
            .toList()
            .chunked(effectiveChunkSize)
    }

    internal fun isFollowGroupRetryableError(code: Int, message: String): Boolean {
        if (code in setOf(-412, -352, -509, 22015)) return true
        if (message.isBlank()) return false
        return message.contains("频繁") ||
            message.contains("过快") ||
            message.contains("风控") ||
            message.contains("稍后") ||
            message.contains("too many", ignoreCase = true) ||
            message.contains("rate", ignoreCase = true)
    }

    private suspend fun addUsersToRelationTagsWithRetry(
        fids: String,
        tagIds: String,
        csrf: String
    ): Result<Unit> {
        var lastCode = Int.MIN_VALUE
        var lastMessage = ""

        repeat(FOLLOW_GROUP_MAX_RETRIES) { attempt ->
            val response = api.addUsersToRelationTags(
                fids = fids,
                tagIds = tagIds,
                csrf = csrf
            )
            if (response.code == 0) {
                return Result.success(Unit)
            }

            lastCode = response.code
            lastMessage = response.message
            val retryable = isFollowGroupRetryableError(response.code, response.message)
            if (!retryable || attempt >= FOLLOW_GROUP_MAX_RETRIES - 1) {
                return Result.failure(
                    Exception(response.message.ifEmpty { "分组设置失败: ${response.code}" })
                )
            }

            val backoffMs = FOLLOW_GROUP_RETRY_BASE_DELAY_MS * (attempt + 1)
            delay(backoffMs)
        }

        return Result.failure(
            Exception(if (lastMessage.isNotEmpty()) lastMessage else "分组设置失败: $lastCode")
        )
    }

    /**
     * 关注/取关 UP 主
     * @param mid UP 主的用户 ID
     * @param follow true=关注, false=取关
     */
    suspend fun followUser(mid: Long, follow: Boolean, emitBrandFeedback: Boolean = true): Result<Boolean> {
        val session = interactionSession()
        followingGuard.beginOperation(session)
        return try {
            val version = interactionCache.beginOperation(session, "follow:$mid")
            try {
                UserActionRepository.follow(mid, follow).rethrowCancellation().mapCatching {
                    if (session != interactionSession() || !interactionCache.publish(session, "follow:$mid", follow, version)) {
                        throw IllegalStateException("关注操作结果已过期")
                    }
                    _followStateChanges.tryEmit(FollowStateChange(mid = mid, isFollowing = follow))
                    if (emitBrandFeedback) com.android.purebilibili.core.events.BrandSuccessEvents.followChanged(follow)
                    follow
                }
            } finally {
                interactionCache.finishOperation(session, "follow:$mid", version)
            }
        } finally {
            followingGuard.finishOperation(session)
        }
    }

    /**
     * 收藏/取消收藏视频
     * @param aid 视频的 aid
     * @param favorite true=收藏, false=取消收藏
     * @param folderId 收藏夹 ID，为空时使用默认收藏夹
     */
    suspend fun favoriteVideo(
        aid: Long,
        favorite: Boolean,
        folderId: Long? = null,
        showSuccessFeedback: Boolean = true
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                // 如果没有指定收藏夹，需要先获取默认收藏夹
                val targetFolderId = folderId ?: getDefaultFolderId()
                if (targetFolderId == null) {
                    return@withContext Result.failure(Exception("无法获取收藏夹"))
                }

                val response = if (favorite) {
                    api.dealFavorite(rid = aid, addIds = targetFolderId.toString(), delIds = "", csrf = csrf)
                } else {
                    api.dealFavorite(rid = aid, addIds = "", delIds = targetFolderId.toString(), csrf = csrf)
                }
                
                if (response.code == 0) {
                    if (favorite && showSuccessFeedback) {
                        com.android.purebilibili.core.events.BrandSuccessEvents.favoriteSaved()
                    }
                    Result.success(favorite)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "favoriteVideo failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 订阅/取消订阅 UGC 合集。
     *
     * 对齐 BiliPai:
     * - 订阅: /x/v3/fav/season/fav
     * - 取消订阅: /x/v3/fav/season/unfav
     * - 表单字段: platform=web, season_id, csrf
     */
    suspend fun setCollectionSubscription(
        seasonId: Long,
        subscribe: Boolean
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (seasonId <= 0L) {
                    return@withContext Result.failure(Exception("合集 ID 无效"))
                }
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val request = buildCollectionSubscriptionRequest(
                    seasonId = seasonId,
                    subscribe = subscribe,
                    csrf = csrf
                )
                val response = if (subscribe) {
                    api.favoriteSeason(
                        platform = request.platform,
                        seasonId = request.seasonId,
                        csrf = request.csrf
                    )
                } else {
                    api.unfavoriteSeason(
                        platform = request.platform,
                        seasonId = request.seasonId,
                        csrf = request.csrf
                    )
                }

                if (response.code == 0) {
                    Result.success(subscribe)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "setCollectionSubscription failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 查询当前视频所在合集是否已订阅。
     *
     * B 站的合集订阅状态来自 archive/relation 的 season_fav 字段。
     */
    suspend fun checkCollectionSubscriptionStatus(
        bvid: String,
        aid: Long = 0L
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedBvid = bvid.trim().takeIf { it.isNotEmpty() }
                val normalizedAid = aid.takeIf { it > 0L }
                if (normalizedBvid == null && normalizedAid == null) {
                    return@withContext Result.failure(Exception("缺少视频标识"))
                }
                val response = api.getVideoRelation(
                    aid = normalizedAid,
                    bvid = normalizedBvid
                )
                if (response.code == 0) {
                    Result.success(response.data?.seasonFav ?: false)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "状态获取失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "checkCollectionSubscriptionStatus failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取用户默认收藏夹 ID
     */
    private suspend fun getDefaultFolderId(): Long? {
        return try {
            val mid = TokenManager.midCache ?: return null
            val response = api.getFavFolders(mid)
            response.data?.list?.firstOrNull()?.id
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ActionRepository", "getDefaultFolderId failed", e)
            null
        }
    }

    /**
     * 批量更新视频所属收藏夹（多选保存）
     */
    suspend fun updateFavoriteFolders(
        aid: Long,
        addFolderIds: Set<Long>,
        removeFolderIds: Set<Long>
    ): Result<Boolean> {
        if (addFolderIds.isEmpty() && removeFolderIds.isEmpty()) return Result.success(true)
        return UserActionRepository.favorite(aid, addFolderIds, removeFolderIds).onSuccess {
            if (addFolderIds.isNotEmpty()) com.android.purebilibili.core.events.BrandSuccessEvents.favoriteSaved()
        }
    }
    
    /**
     * 获取与 UP 主的详细关注关系数据
     */
    suspend fun getRelationDetail(mid: Long): com.android.purebilibili.data.model.response.RelationData? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getRelation(mid)
                if (response.code == 0) {
                    response.data
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "getRelationDetail failed", e)
                null
            }
        }
    }

    /**
     *  检查是否已关注 UP 主
     */
    suspend fun checkFollowStatus(mid: Long): Boolean = queryFollowStatus(mid).getOrDefault(false)

    suspend fun getFollowGroupTags(): Result<List<com.android.purebilibili.data.model.response.RelationTagItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getRelationTags()
                if (response.code == 0) {
                    Result.success(normalizeRelationTags(response.data))
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取关注分组失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun getUserFollowGroupIds(mid: Long): Result<Set<Long>> {
        return withContext(Dispatchers.IO) {
            var lastCode = Int.MIN_VALUE
            var lastMessage = ""
            var lastError: Exception? = null

            repeat(FOLLOW_GROUP_QUERY_MAX_RETRIES) { attempt ->
                try {
                    val response = api.getRelationTagUser(mid)
                    if (response.code == 0) {
                        val ids = response.data.keys.mapNotNull { it.toLongOrNull() }.toSet()
                        return@withContext Result.success(normalizeRelationTagIds(ids))
                    }

                    lastCode = response.code
                    lastMessage = response.message
                    val retryable = isFollowGroupRetryableError(response.code, response.message)
                    if (!retryable || attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1) {
                        return@withContext Result.failure(
                            Exception(response.message.ifEmpty { "获取分组信息失败: ${response.code}" })
                        )
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    lastError = e
                    if (attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1) {
                        return@withContext Result.failure(e)
                    }
                }

                val backoffMs = FOLLOW_GROUP_QUERY_RETRY_BASE_DELAY_MS * (attempt + 1)
                delay(backoffMs)
            }

            val fallbackMessage = if (lastMessage.isNotBlank()) {
                lastMessage
            } else {
                "获取分组信息失败: $lastCode"
            }
            Result.failure(lastError ?: Exception(fallbackMessage))
        }
    }

    suspend fun getFollowGroupMemberMids(
        tagId: Long,
        targetMids: Set<Long> = emptySet()
    ): Result<Set<Long>> {
        return withContext(Dispatchers.IO) {
            try {
                val targetSet = if (targetMids.isEmpty()) null else targetMids
                val result = linkedSetOf<Long>()
                var page = 1

                while (page <= FOLLOW_GROUP_TAG_MEMBERS_MAX_PAGES) {
                    val response = api.getRelationTagMembers(
                        tagId = tagId,
                        pageSize = FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE,
                        page = page
                    )
                    if (response.code != 0) {
                        return@withContext Result.failure(
                            Exception(response.message.ifEmpty { "获取分组成员失败: ${response.code}" })
                        )
                    }

                    val mids = response.data
                        .asSequence()
                        .map { it.mid }
                        .filter { it > 0L }
                        .toList()

                    if (targetSet == null) {
                        result.addAll(mids)
                    } else {
                        mids.filterTo(result) { targetSet.contains(it) }
                    }

                    if (mids.size < FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE) break
                    page += 1
                    delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
                }

                Result.success(result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun getAllFollowGroupUsers(): Result<List<FollowingUser>> {
        return getFollowGroupUsers(tagId = ALL_FOLLOW_TAG_ID)
    }

    private suspend fun getFollowGroupUsers(tagId: Long): Result<List<FollowingUser>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = linkedMapOf<Long, FollowingUser>()
                var page = 1

                while (page <= FOLLOW_GROUP_TAG_MEMBERS_MAX_PAGES) {
                    val response = api.getRelationTagFollowingUsers(
                        tagId = tagId,
                        pageSize = FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE,
                        page = page
                    )
                    if (response.code != 0) {
                        return@withContext Result.failure(
                            Exception(response.message.ifEmpty { "获取分组成员失败: ${response.code}" })
                        )
                    }

                    val users = response.data.filter { it.mid > 0L }
                    users.forEach { user -> result[user.mid] = user }

                    if (users.size < FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE) break
                    page += 1
                    delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
                }

                Result.success(result.values.toList())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun overwriteFollowGroupIds(
        targetMids: Set<Long>,
        selectedTagIds: Set<Long>
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (targetMids.isEmpty()) return@withContext Result.success(true)
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val normalizedSelection = normalizeRelationTagIds(selectedTagIds)
                val midChunks = chunkFollowGroupTargetMids(targetMids)
                if (midChunks.isEmpty()) return@withContext Result.success(true)
                val selectedTagIdsJoined = normalizedSelection.joinToString(",")

                midChunks.forEachIndexed { index, mids ->
                    val fids = mids.joinToString(",")

                    // 先移动到默认分组，确保“完全覆盖”生效。
                    val resetResult = addUsersToRelationTagsWithRetry(
                        fids = fids,
                        tagIds = "0",
                        csrf = csrf
                    )
                    if (resetResult.isFailure) {
                        return@withContext Result.failure(
                            resetResult.exceptionOrNull() ?: Exception("分组设置失败")
                        )
                    }

                    if (normalizedSelection.isNotEmpty()) {
                        val applyResult = addUsersToRelationTagsWithRetry(
                            fids = fids,
                            tagIds = selectedTagIdsJoined,
                            csrf = csrf
                        )
                        if (applyResult.isFailure) {
                            return@withContext Result.failure(
                                applyResult.exceptionOrNull() ?: Exception("分组设置失败")
                            )
                        }
                    }

                    if (index < midChunks.lastIndex) {
                        delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
                    }
                }

                Result.success(true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }
    
    /**
     *  检查视频是否已收藏
     */
    suspend fun checkFavoriteStatus(aid: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.checkFavoured(aid)
                if (response.code == 0) {
                    val isFavoured = response.data?.favoured ?: false
                    com.android.purebilibili.core.util.Logger.d("ActionRepository", " checkFavoriteStatus: aid=$aid, isFavoured=$isFavoured")
                    isFavoured
                } else {
                    false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "checkFavoriteStatus failed", e)
                false
            }
        }
    }
    
    /**
     * 获取用户收藏夹列表
     */
    suspend fun getFavoriteFolders(aid: Long? = null): Result<List<com.android.purebilibili.data.model.response.FavFolder>> { return UserActionRepository.folders(aid) }

    /**
     * 创建收藏夹
     */
    suspend fun createFavFolder(title: String, intro: String = "", isPrivate: Boolean = false): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) return@withContext Result.failure(Exception("标题不能为空"))
                val privacy = if (isPrivate) 1 else 0
                val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
                
                val response = api.createFavFolder(
                    title = title,
                    intro = intro,
                    privacy = privacy,
                    csrf = csrf
                )
                
                if (response.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }
    
    /**
     *  点赞/取消点赞视频
     */
    suspend fun likeVideo(aid: Long, like: Boolean): Result<Boolean> {
        val session = interactionSession()
        val version = interactionCache.beginOperation(session, "like:$aid")
        return try {
            UserActionRepository.like(aid, like).rethrowCancellation().mapCatching {
                if (session != interactionSession() || !interactionCache.publish(session, "like:$aid", like, version)) {
                    throw IllegalStateException("点赞操作结果已过期")
                }
                like
            }
        } finally {
            interactionCache.finishOperation(session, "like:$aid", version)
        }
    }
    
    /**
     *  检查是否已点赞
     */
    suspend fun checkLikeStatus(aid: Long): Boolean { return queryLikeStatus(aid).getOrDefault(false) }

    /**
     *  点踩/取消点踩视频（App 端接口，65007=已踩过、65005=未点踩过视为幂等成功）
     */
    suspend fun dislikeVideo(aid: Long, dislike: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (TokenManager.sessDataCache.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }

                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val dislikeAction = if (dislike) 0 else 1
                val response = api.dislikeVideo(
                    aid = aid,
                    dislike = dislikeAction,
                    csrf = csrf,
                    accessKey = TokenManager.accessTokenCache
                )
                com.android.purebilibili.core.util.Logger.d("ActionRepository", " dislikeVideo: aid=$aid, dislike=$dislike, code=${response.code}")

                when {
                    response.code == 0 || response.code == 65007 || response.code == 65005 ->
                        Result.success(dislike)
                    response.code == -101 -> Result.failure(
                        Exception("视频点踩需要 APP 鉴权，请先在登录页完成高画质（TV）鉴权登录")
                    )
                    else -> Result.failure(Exception(response.message.ifEmpty { "点踩失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "dislikeVideo failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     *  检查是否已点踩（复用 archive/relation 的 dislike 字段）
     */
    suspend fun checkDislikeStatus(aid: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getVideoRelation(aid = aid)
                if (response.code == 0) {
                    response.data?.dislike ?: false
                } else {
                    false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "checkDislikeStatus failed", e)
                false
            }
        }
    }
    
    /**
     *  投币
     */
    suspend fun coinVideo(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> {
        val session = interactionSession()
        val version = if (alsoLike) interactionCache.beginOperation(session, "like:$aid") else null
        return try {
            withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                val selectLike = if (alsoLike) 1 else 0
                val response = api.coinVideo(aid = aid, multiply = count, selectLike = selectLike, csrf = csrf)
                com.android.purebilibili.core.util.Logger.d("ActionRepository", " coinVideo: aid=$aid, count=$count, code=${response.code}")
                
                when (response.code) {
                    0 -> {
                        if (session != interactionSession() ||
                            (version != null && !interactionCache.publish(session, "like:$aid", true, version))) {
                            return@withContext Result.failure(IllegalStateException("投币操作结果已过期"))
                        }
                        Result.success(true)
                    }
                    34004 -> Result.failure(Exception("操作太频繁，请稍后重试"))
                    34005 -> Result.failure(Exception("已投满2个硬币"))
                    -104 -> Result.failure(Exception("硬币余额不足"))
                    else -> Result.failure(Exception(response.message.ifEmpty { "投币失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "coinVideo failed", e)
                Result.failure(e)
            }
            }
        } finally {
            if (version != null) interactionCache.finishOperation(session, "like:$aid", version)
        }
    }
    
    /**
     *  检查已投币数
     */
    suspend fun checkCoinStatus(aid: Long): Int {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.hasCoined(aid)
                if (response.code == 0) {
                    val coinCount = response.data?.multiply ?: 0
                    com.android.purebilibili.core.util.Logger.d("ActionRepository", " checkCoinStatus: aid=$aid, coinCount=$coinCount")
                    coinCount
                } else {
                    0
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ActionRepository", "checkCoinStatus failed", e)
                0
            }
        }
    }
    
    /**
     *  一键三连 (点赞 + 投币 + 收藏)；coinCount 由调用方按视频属性给出（原创 2、转载 1）。
     */
    data class TripleResult(
        val likeSuccess: Boolean,
        val coinSuccess: Boolean,
        val coinMessage: String?,
        val favoriteSuccess: Boolean
    )
    
    suspend fun tripleAction(aid: Long, coinCount: Int = 2): Result<TripleResult> {
        return withContext(Dispatchers.IO) {
            val session = interactionSession()
            val csrf = TokenManager.csrfCache ?: ""
            if (csrf.isEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            // 1. 点赞
            val likeResult = likeVideo(aid, true)
            val likeSuccess = likeResult.isSuccess
            
            if (session != interactionSession()) return@withContext Result.failure(IllegalStateException("交互账号已变更"))
            // 2. 投币 (按上限，同时点赞)
            val coinResult = coinVideo(aid, coinCount, true)
            val coinSuccess = coinResult.isSuccess
            val coinMessage = coinResult.exceptionOrNull()?.message
            
            if (session != interactionSession()) return@withContext Result.failure(IllegalStateException("交互账号已变更"))
            // 3. 收藏
            val favoriteResult = favoriteVideo(aid, true, showSuccessFeedback = false).rethrowCancellation()
            if (session != interactionSession()) return@withContext Result.failure(IllegalStateException("交互账号已变更"))
            val favoriteSuccess = favoriteResult.isSuccess
            
            com.android.purebilibili.core.util.Logger.d("ActionRepository", " tripleAction: like=$likeSuccess, coin=$coinSuccess, fav=$favoriteSuccess")
            
            Result.success(TripleResult(
                likeSuccess = likeSuccess,
                coinSuccess = coinSuccess,
                coinMessage = coinMessage,
                favoriteSuccess = favoriteSuccess
            ))
        }
    }
    
    /**
     *  添加/移除稍后再看
     */
    suspend fun toggleWatchLater(aid: Long, add: Boolean): Result<Boolean> {
        val result = if (add) WatchLaterRepository.add(aid) else WatchLaterRepository.remove(aid)
        return result.onSuccess { WatchLaterRefreshBus.notifyChanged() }.map { add }
    }

    suspend fun checkWatchLaterStatus(aid: Long): Boolean = withContext(Dispatchers.IO) {
        if (aid <= 0L || TokenManager.sessDataCache.isNullOrEmpty()) return@withContext false
        runCatching {
            val response = api.getWatchLaterList()
            response.code == 0 && isWatchLaterAid(response.data?.list.orEmpty(), aid)
        }.getOrDefault(false)
    }
}
