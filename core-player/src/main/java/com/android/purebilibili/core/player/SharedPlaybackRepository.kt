package com.android.purebilibili.core.player

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.TokenRefreshHelper
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.ContentRequestException
import com.android.purebilibili.data.repository.PlaybackStreamDataSource
import com.android.purebilibili.data.repository.SharedContentRepository
import com.android.purebilibili.data.repository.buildPlayUrlWbiBaseParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SharedPlaybackRequest(
    val bvid: String,
    val aid: Long = 0,
    val cid: Long = 0,
    val quality: Int = 64,
    val startPositionMs: Long? = null,
    val audioQuality: Int = -1,
    val audioLanguage: String? = null,
)

class PlaybackLoadException(val info: ViewInfo, cause: Throwable) : Exception(cause.message, cause)

data class LoadedPlayback(val info: ViewInfo, val streams: PlayUrlData)

object SharedPlaybackRepository {
    suspend fun load(
        request: SharedPlaybackRequest,
        allowTokenRefresh: Boolean = true,
        usePlaybackAccount: Boolean = true
    ): Result<LoadedPlayback> = withContext(Dispatchers.IO) {
        try {
            val info = SharedContentRepository.detail(request.bvid, request.aid, request.cid).getOrThrow()
            val streams = try {
                loadStreams(info.bvid, info.cid, request.quality, request.audioLanguage, allowTokenRefresh, usePlaybackAccount)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw PlaybackLoadException(info, error)
            }
            Result.success(LoadedPlayback(info, streams))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun loadStreams(
        bvid: String, cid: Long, quality: Int, audioLanguage: String?,
        allowTokenRefresh: Boolean, usePlaybackAccount: Boolean
    ): PlayUrlData {
        val account = if (usePlaybackAccount) NetworkModule.playbackAccount() else null
        var accessToken = account?.accessToken ?: TokenManager.accessTokenCache.orEmpty()
        if (accessToken.isNotBlank()) {
            val platform = account?.accessTokenPlatform ?: TokenManager.accessTokenPlatformCache
            suspend fun appRequest(token: String) = PlaybackStreamDataSource.appPlayUrl(bvid, cid, quality, token, platform, audioLanguage)
            try {
                var response = appRequest(accessToken)
                val context = NetworkModule.appContext
                if (response.code == -101 && allowTokenRefresh && account == null && context != null && TokenRefreshHelper.refresh(context)) {
                    accessToken = TokenManager.accessTokenCache.orEmpty()
                    response = appRequest(accessToken)
                }
                val data = response.data
                if (response.code == 0 && data != null && data.hasPlayableStream()) return data
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Web playback is the same account's fallback; entitlements stay server controlled.
            }
        }
        val keys = WbiKeyManager.getWbiKeys().getOrThrow()
        val api = if (usePlaybackAccount) NetworkModule.playbackApi() else NetworkModule.api
        val response = api.getPlayUrl(WbiUtils.sign(
            buildPlayUrlWbiBaseParams(bvid, cid, quality) + (audioLanguage?.let { mapOf("cur_language" to it, "lang" to it) } ?: emptyMap()), keys.first, keys.second,
        ))
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        return response.data?.takeIf { it.hasPlayableStream() } ?: error("没有可播放的视频流，内容可能受访问限制")
    }

    private fun PlayUrlData.hasPlayableStream() =
        dash?.video.orEmpty().any { it.getValidUrl().isNotBlank() } || durl.orEmpty().any { it.url.isNotBlank() }
}
