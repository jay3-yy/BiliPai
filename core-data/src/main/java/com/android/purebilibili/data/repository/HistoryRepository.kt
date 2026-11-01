package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.HistoryData
import com.android.purebilibili.data.model.response.HistoryCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 历史记录数据结果（包含列表和游标）
 */
data class HistoryResult(
    val list: List<HistoryData>,
    val cursor: HistoryCursor?
)

data class HistoryCursorQuery(
    val max: Long?,
    val viewAt: Long?,
    val business: String?
)

fun resolveHistoryCursorQuery(
    max: Long,
    viewAt: Long,
    business: String?
): HistoryCursorQuery {
    val normalizedBusiness = business?.trim()?.takeIf { it.isNotEmpty() }
    val hasCursor = max > 0L || viewAt > 0L || normalizedBusiness != null
    if (!hasCursor) {
        return HistoryCursorQuery(
            max = null,
            viewAt = null,
            business = null
        )
    }
    return HistoryCursorQuery(
        max = max.takeIf { it > 0L },
        viewAt = viewAt.takeIf { it > 0L },
        business = normalizedBusiness
    )
}

object HistoryRepository {
    private val api = NetworkModule.api

    /**
     * 获取历史记录列表（支持游标分页）
     * @param ps 每页数量
     * @param max 游标: 上一页最后一条的 oid (首次请求传 0)
     * @param viewAt 游标: 上一页最后一条的 view_at (首次请求传 0)
     */
    suspend fun getHistoryList(
        ps: Int = 30,
        max: Long = 0,
        viewAt: Long = 0,
        business: String? = null,
        type: String? = null
    ): Result<HistoryResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cursorQuery = resolveHistoryCursorQuery(
                    max = max,
                    viewAt = viewAt,
                    business = business
                )
                val typeQuery = type?.trim()?.takeIf { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }
                com.android.purebilibili.core.network.CoreDataLog.d(
                    "HistoryRepo",
                    "🔴 Fetching history: ps=$ps, max=${cursorQuery.max}, viewAt=${cursorQuery.viewAt}, business=${cursorQuery.business}, type=$typeQuery"
                )
                val response = api.getHistoryList(
                    ps = ps,
                    max = cursorQuery.max,
                    viewAt = cursorQuery.viewAt,
                    business = cursorQuery.business,
                    type = typeQuery
                )
                com.android.purebilibili.core.network.CoreDataLog.d("HistoryRepo", "🔴 Response code=${response.code}, items=${response.data?.list?.size ?: 0}")
                
                if (response.code == 0) {
                    val list = response.data?.list ?: emptyList()
                    val cursor = response.data?.cursor
                    com.android.purebilibili.core.network.CoreDataLog.d(
                        "HistoryRepo",
                        "🔴 Cursor: max=${cursor?.max}, view_at=${cursor?.view_at}, business=${cursor?.business}"
                    )
                    Result.success(HistoryResult(list, cursor))
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("HistoryRepo", " Error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun searchHistory(page: Int, keyword: String): Result<HistoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.searchHistory(page = page, keyword = keyword.trim())
                if (response.code == 0) {
                    Result.success(
                        HistoryResult(
                            list = response.data?.list.orEmpty(),
                            cursor = response.data?.cursor,
                        )
                    )
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }

    suspend fun deleteHistoryItem(
        kid: String,
        csrf: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.deleteHistoryItem(kid = kid, csrf = csrf)
                if (response.code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun clearHistory(csrf: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.clearHistory(csrf = csrf)
                if (response.code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun getHistoryPaused(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getHistoryShadow()
                if (response.code == 0) {
                    Result.success(response.data)
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun reportPlayback(
        bvid: String, cid: Long, playedTime: Long = 0, realPlayedTime: Long = playedTime,
        startTsSec: Long = System.currentTimeMillis() / 1000L, aid: Long = 0,
        epid: Long = 0, sid: Long = 0, videoType: Int = 3, subType: Int? = null,
        videoDurationSec: Long = 0L, lastProgressSec: Long = 0L,
        maxProgressSec: Long = 0L, session: String = "",
    ): Result<Unit> = dataRequest {
        val context = com.android.purebilibili.core.network.NetworkModule.appContext
        if (context != null && com.android.purebilibili.core.network.CoreNetworkRuntime.config.isPrivacyModeEnabled(context)) return@dataRequest
        val fields = buildPlaybackHeartbeatFields(bvid, aid, cid, epid, sid,
            com.android.purebilibili.core.store.TokenManager.midCache, playedTime, realPlayedTime, startTsSec,
            com.android.purebilibili.core.store.TokenManager.csrfCache.orEmpty(), videoType, subType,
            videoDurationSec = videoDurationSec, lastProgressSec = lastProgressSec,
            maxProgressSec = maxProgressSec, session = session)
        val response = api.reportHeartbeat(fields)
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
    }

    /**
     * 上报视频观看进度。推荐系统判断「已看过」靠的是这个接口，
     * 而不是 click-interface 那套心跳（官方客户端已不再请求它）。
     */
    suspend fun reportVideoProgress(
        aid: Long,
        cid: Long,
        progressMs: Long,
        durationMs: Long,
        startTsSec: Long,
        scene: String = "front",
    ): Result<Unit> = dataRequest {
        val context = com.android.purebilibili.core.network.NetworkModule.appContext
        if (context != null && com.android.purebilibili.core.network.CoreNetworkRuntime.config.isPrivacyModeEnabled(context)) return@dataRequest
        val fields = buildVideoHistoryReportFields(
            aid = aid,
            cid = cid,
            progressMs = progressMs,
            durationMs = durationMs,
            startTsSec = startTsSec,
            deviceTsSec = System.currentTimeMillis() / 1000L,
            csrf = com.android.purebilibili.core.store.TokenManager.csrfCache.orEmpty(),
            scene = scene,
        )
        if (fields == null) return@dataRequest
        val response = api.reportHistory(fields)
        com.android.purebilibili.core.util.CoreLogger.d("HistoryReport",
            "response code=${response.code} msg=${response.message} aid=$aid cid=$cid",
        )
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
    }

    suspend fun reportArticleView(articleId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val context = com.android.purebilibili.core.network.NetworkModule.appContext
                if (
                    context != null &&
                    com.android.purebilibili.core.network.CoreNetworkRuntime.config.isPrivacyModeEnabled(context)
                ) {
                    return@withContext Result.success(Unit)
                }
                val fields = buildArticleHistoryReportFields(
                    articleId = articleId,
                    csrf = com.android.purebilibili.core.store.TokenManager.csrfCache.orEmpty()
                ) ?: return@withContext Result.success(Unit)
                val response = api.reportHistory(fields)
                if (response.code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun setHistoryPaused(
        paused: Boolean,
        csrf: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.setHistoryShadow(shadowSwitch = paused, csrf = csrf)
                if (response.code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(ContentRequestException(response.code, response.message))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }
}
