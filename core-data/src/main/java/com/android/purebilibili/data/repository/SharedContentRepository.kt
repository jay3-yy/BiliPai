package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.RecommendResponse
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.resolveCollectionEpisodeAccessLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ContentRedirectException(val url: String) : Exception("该内容应在番剧详情页打开")

class ContentRequestException(val code: Int, message: String) : Exception(
    when (code) {
        -101 -> "登录已失效，请重新扫码登录"
        -403 -> message.ifBlank { "当前账号没有访问权限" }
        -404 -> "内容不存在或已被删除"
        -412 -> "请求暂时受限，请稍后重试"
        else -> message.ifBlank { "请求失败（$code）" }
    },
) {
    val apiMessage: String = message
}

/** Shared calls used by both frontends; mobile retains its feed merge/filter policies. */
object SharedContentRepository {
    private val collectionAccessRequests = Semaphore(3)

    /** 仅展示缺少角标的条目时查询，复用详情缓存和请求合并，不批量预取整个合集。 */
    suspend fun collectionEpisodeAccessLabel(episode: UgcEpisode): String? {
        episode.accessLabel?.let { return it }
        if (episode.bvid.isBlank() && episode.aid <= 0) return null
        return collectionAccessRequests.withPermit {
            val info = detail(episode.bvid, episode.aid).getOrNull()
            resolveCollectionEpisodeAccessLabel(episode, info)
        }
    }

    private data class DetailKey(val accountScope: String, val bvid: String, val aid: Long)
    private val detailCache = OnDemandRequestCache<DetailKey, Result<ViewInfo>>(
        maxEntries = 48,
        maxAgeMs = 120_000L,
        canCache = { it.isSuccess }
    )
    suspend fun recommendationResponse(signedParams: Map<String, String>): RecommendResponse =
        NetworkModule.api.getRecommendParams(signedParams)

    suspend fun recommendations(page: Int): Result<List<VideoItem>> = dataRequest {
        val keys = WbiKeyManager.getWbiKeys().getOrThrow()
        val params = mapOf(
            "ps" to "20", "fresh_idx" to page.coerceAtLeast(1).toString(),
            "fresh_idx_1h" to page.coerceAtLeast(1).toString(), "feed_version" to "V8",
            "fresh_type" to "3", "homepage_ver" to "1", "web_location" to "1430650",
        )
        val response = recommendationResponse(WbiUtils.sign(params, keys.first, keys.second))
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data?.item.orEmpty()
            .filter { it.goto == "av" || it.goto.isNullOrBlank() }
            .map { it.toVideoItem() }
            .filter { it.bvid.isNotBlank() || it.aid > 0L }
    }

    suspend fun detail(bvid: String, aid: Long = 0, requestedCid: Long = 0): Result<ViewInfo> = dataRequest {
        val lookup = resolveVideoInfoLookupInput(bvid, aid) ?: error("无效的视频标识")
        TokenManager.awaitRestore()
        val accountScope = videoMetadataAccountScope()
        val key = DetailKey(accountScope, lookup.bvid, lookup.aid)
        val info = detailCache.getOrLoad(key) {
            dataRequest {
                val response = if (lookup.bvid.isNotEmpty()) NetworkModule.api.getVideoInfo(lookup.bvid)
                else NetworkModule.api.getVideoInfoByAid(lookup.aid)
                if (response.code != 0) throw ContentRequestException(response.code, response.message)
                check(accountScope == videoMetadataAccountScope()) { "账号已切换，请重新加载" }
                response.data ?: error("视频详情为空")
            }
        }.getOrThrow()
        check(accountScope == videoMetadataAccountScope()) { "账号已切换，请重新加载" }
        info.redirectUrl?.takeIf { it.isNotBlank() }?.let { throw ContentRedirectException(it) }
        val cid = resolveRequestedVideoCid(requestedCid, info.cid, info.pages)
        check(cid > 0) { "未找到可播放的分 P" }
        info.copy(cid = cid)
    }

    fun cachedDetailInfo(bvid: String, aid: Long = 0L): ViewInfo? {
        val lookup = resolveVideoInfoLookupInput(bvid, aid) ?: return null
        return detailCache.peek(DetailKey(videoMetadataAccountScope(), lookup.bvid, lookup.aid))
            ?.getOrNull()
    }

    fun cachedDetailCid(bvid: String, aid: Long = 0L): Long =
        cachedDetailInfo(bvid, aid)?.cid?.coerceAtLeast(0L) ?: 0L

    fun invalidateDetail(bvid: String, aid: Long = 0L) {
        val lookup = resolveVideoInfoLookupInput(bvid, aid) ?: return
        detailCache.invalidate(DetailKey(videoMetadataAccountScope(), lookup.bvid, lookup.aid))
    }

    fun clearDetailCache() {
        detailCache.clearCompleted()
    }
}

internal suspend fun <T> dataRequest(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
