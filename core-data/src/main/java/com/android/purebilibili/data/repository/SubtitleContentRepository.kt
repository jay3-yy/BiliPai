package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.subtitle.*
import com.android.purebilibili.data.model.response.PlayerInfoData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

object SubtitleContentRepository {
    private data class PlayerInfoKey(
        val accountScope: String,
        val bvid: String,
        val cid: Long,
    )

    private val cache = SubtitleCueMemoryCache()
    private val playerInfoCache = OnDemandRequestCache<PlayerInfoKey, Result<PlayerInfoData>>(
        maxEntries = 32,
        maxAgeMs = 30_000L,
        canCache = { it.isSuccess },
    )
    private val cueRequests = OnDemandRequestCache<String, Result<List<SubtitleCue>>>(
        maxEntries = 8,
        maxAgeMs = 0L,
    )

    fun cacheStats(): SubtitleCueMemoryCacheStats = cache.stats()
    fun clearCache() = cache.clear()
    fun clearPlayerInfoCache() = playerInfoCache.clearCompleted()

    suspend fun playerInfo(bvid: String, cid: Long): Result<PlayerInfoData> = withContext(Dispatchers.IO) {
        try {
            TokenManager.awaitRestore()
            val key = PlayerInfoKey(
                accountScope = videoMetadataAccountScope(),
                bvid = bvid.trim(),
                cid = cid,
            )
            val result = playerInfoCache.getOrLoad(key) {
                dataRequest {
                    val keys = WbiKeyManager.getWbiKeys().getOrThrow()
                    val response = NetworkModule.api.getPlayerInfo(
                        WbiUtils.sign(mapOf("bvid" to key.bvid, "cid" to key.cid.toString()), keys.first, keys.second)
                    )
                    if (response.code != 0) throw ContentRequestException(response.code, response.message)
                    check(key.accountScope == videoMetadataAccountScope()) { "Account changed" }
                    response.data ?: error("播放器信息为空")
                }
            }
            if (key.accountScope == videoMetadataAccountScope()) result
            else Result.failure(IllegalStateException("Account changed"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun cues(
        subtitleUrl: String,
        bvid: String,
        cid: Long,
        subtitleId: Long = 0,
        subtitleIdStr: String = "",
        subtitleLan: String = "",
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        try {
            require(bvid.isNotBlank() && cid > 0) { "字幕归属视频信息缺失" }
            val url = normalizeBilibiliSubtitleUrl(subtitleUrl)
            require(isTrustedBilibiliSubtitleUrl(url)) { "字幕地址无效" }
            val key = "$bvid:$cid:$subtitleId:$subtitleIdStr:$subtitleLan:$url"
            cache[key]?.let { return@withContext Result.success(it) }
            cueRequests.getOrLoad(key) {
                dataRequest {
                    val generation = cache.generation()
                    cache[key] ?: NetworkModule.okHttpClient.newCall(
                        Request.Builder()
                            .url(url)
                            .cacheControl(CacheControl.FORCE_NETWORK)
                            .header("Referer", "https://www.bilibili.com")
                            .build()
                    ).execute().use { response ->
                        check(response.isSuccessful) { "字幕请求失败（HTTP ${response.code}）" }
                        parseBiliSubtitleBody(response.body.string()).also {
                            cache.put(key, it, generation)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
