package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.VideoRights
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.WatchLaterItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WatchLaterPage(
    val items: List<VideoItem>,
    val totalCount: Int,
    val hasMore: Boolean,
)

fun buildWatchLaterPageParams(
    page: Int,
    viewed: Int,
    keyword: String,
    ascending: Boolean,
): Map<String, String> = mapOf(
    "pn" to page.coerceAtLeast(1).toString(),
    "ps" to "30",
    "viewed" to viewed.toString(),
    "key" to keyword.trim(),
    "asc" to ascending.toString(),
    "need_split" to "true",
    "web_location" to "333.881",
)

object WatchLaterRepository {
    private val watchLaterListCache = OnDemandRequestCache<Any, Result<List<WatchLaterItem>>>(
        maxEntries = 2,
        maxAgeMs = 15_000L,
        canCache = { it.isSuccess },
    )

    private fun currentWatchLaterSession(): Any =
        TokenManager.accountIdentity.value to TokenManager.sessDataCache

    fun invalidateWatchLaterListCache() {
        watchLaterListCache.invalidateWhere { true }
    }

    suspend fun getWatchLaterItems(session: Any = currentWatchLaterSession()): Result<List<WatchLaterItem>> =
        withContext(Dispatchers.IO) {
            try {
                TokenManager.awaitRestore()
                val effectiveSession = if (session == currentWatchLaterSession()) session else currentWatchLaterSession()
                if (TokenManager.sessDataCache.isNullOrEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                val result = watchLaterListCache.getOrLoad(effectiveSession) {
                    dataRequest {
                        val response = api.getWatchLaterList()
                        if (response.code != 0) throw ContentRequestException(response.code, response.message)
                        check(effectiveSession == currentWatchLaterSession()) { "Account changed" }
                        response.data?.list.orEmpty()
                    }
                }
                if (effectiveSession == currentWatchLaterSession()) result
                else Result.failure(IllegalStateException("Account changed"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    suspend fun add(aid: Long): Result<Boolean> = dataRequest {
        val response = api.addToWatchLater(aid, requireCsrf())
        if (response.code != 0) throw ContentRequestException(response.code, when (response.code) {
            90001 -> "稍后再看列表已满"; 90003 -> "视频已被删除"; else -> response.message
        })
        invalidateWatchLaterListCache()
        true
    }
    suspend fun remove(aid: Long): Result<Boolean> = dataRequest {
        val response = api.deleteFromWatchLater(aid = aid, csrf = requireCsrf())
        if (response.code != 0) throw ContentRequestException(response.code, when (response.code) {
            90001 -> "稍后再看列表已满"; 90003 -> "视频已被删除"; else -> response.message
        })
        invalidateWatchLaterListCache()
        true
    }
    private const val PAGE_SIZE = 30
    private val api = NetworkModule.api

    suspend fun getPage(
        page: Int,
        viewed: Int,
        keyword: String,
        ascending: Boolean,
    ): Result<WatchLaterPage> = withContext(Dispatchers.IO) {
        apiCall {
            val params = buildWatchLaterPageParams(page, viewed, keyword, ascending)
            val keys = WbiKeyManager.getWbiKeys().getOrNull()
                ?: WbiKeyManager.refreshKeys().getOrThrow()
            val response = api.getWatchLaterPage(WbiUtils.sign(params, keys.first, keys.second))
            if (response.code != 0) throw ContentRequestException(response.code, response.message)
            val data = response.data
            val items = data?.list.orEmpty().map { item ->
                VideoItem(
                    id = item.aid,
                    aid = item.aid,
                    bvid = item.bvid.orEmpty(),
                    cid = item.cid ?: 0L,
                    title = item.title.orEmpty(),
                    pic = item.pic.orEmpty(),
                    duration = item.duration ?: 0,
                    progress = item.progress ?: -1,
                    owner = Owner(
                        mid = item.owner?.mid ?: 0L,
                        name = item.owner?.name.orEmpty(),
                        face = item.owner?.face.orEmpty(),
                    ),
                    stat = Stat(
                        view = item.stat?.view ?: 0,
                        danmaku = item.stat?.danmaku ?: 0,
                        reply = item.stat?.reply ?: 0,
                        like = item.stat?.like ?: 0,
                        coin = item.stat?.coin ?: 0,
                        favorite = item.stat?.favorite ?: 0,
                        share = item.stat?.share ?: 0,
                    ),
                    pubdate = item.pubdate ?: 0L,
                    rights = VideoRights(
                        isCooperation = item.rights?.isCooperation ?: 0,
                        isSteinGate = item.rights?.isSteinGate ?: 0,
                    ),
                    isLessonVideo = item.isPugv == true,
                    chargingPay = item.chargingPay,
                    pgcLabel = item.pgcLabel,
                    navigationUrl = item.redirectUrl.orEmpty(),
                )
            }
            val total = data?.count?.coerceAtLeast(items.size) ?: items.size
            WatchLaterPage(
                items = items,
                totalCount = total,
                hasMore = items.isNotEmpty() && page.toLong() *
                    (data?.ps?.takeIf { it > 0 } ?: minOf(PAGE_SIZE, items.size).coerceAtLeast(1)) < total,
            )
        }
    }

    suspend fun clear(cleanType: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            val response = api.clearWatchLater(cleanType = cleanType, csrf = requireCsrf())
            check(response.code == 0) { response.message.ifBlank { "清理稍后再看失败" } }
            invalidateWatchLaterListCache()
        }
    }

    suspend fun copyOrMoveToFavorite(
        targetMediaId: Long,
        aids: Set<Long>,
        copy: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            check(aids.isNotEmpty()) { "请选择视频" }
            val csrf = requireCsrf()
            val resources = aids.joinToString(",")
            val response = if (copy) {
                api.copyWatchLaterToFavorite(
                    targetMediaId = targetMediaId,
                    mid = TokenManager.midCache ?: error("请先登录"),
                    resources = resources,
                    csrf = csrf,
                )
            } else {
                api.moveWatchLaterToFavorite(
                    targetMediaId = targetMediaId,
                    resources = resources,
                    csrf = csrf,
                )
            }
            check(response.code == 0) {
                response.message.ifBlank { if (copy) "复制失败" else "移动失败" }
            }
            if (!copy) invalidateWatchLaterListCache()
        }
    }

    private fun requireCsrf(): String = TokenManager.csrfCache.orEmpty().ifBlank { throw ContentRequestException(-101, "") }

    private suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }
}
