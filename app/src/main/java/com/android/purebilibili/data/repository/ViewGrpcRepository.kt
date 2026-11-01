package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.BgmDetailData
import com.android.purebilibili.data.model.response.BgmRecommendVideo
import kotlinx.coroutines.CancellationException
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ViewGrpcRepository {
    private const val TAG = "BgmList"

    suspend fun getBgmDetail(musicId: String, aid: Long = 0, cid: Long = 0): Result<BgmDetailData?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (imgKey, subKey) = WbiKeyManager.getWbiKeys().getOrThrow()
                val params = buildMap {
                    put("music_id", musicId)
                    put("relation_from", "bgm_page")
                    if (aid > 0) put("aid", aid.toString())
                    if (cid > 0) put("cid", cid.toString())
                }
                val response = NetworkModule.api.getBgmDetail(WbiUtils.sign(params, imgKey, subKey))
                check(response.code == 0) { response.message.ifBlank { "音乐详情加载失败 (${response.code})" } }
                requireNotNull(response.data) { "暂无音乐详情" }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Logger.w(TAG, "BGM detail API failed: ${e.message}")
            }
        }

    suspend fun getBgmRecommendVideos(
        musicId: String,
        aid: Long,
        cid: Long,
        page: Int = 1,
        pageSize: Int = 5
    ): Result<List<BgmRecommendVideo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = NetworkModule.api.getBgmRecommendList(
                    musicId = musicId,
                    aid = aid,
                    cid = cid,
                    pn = page,
                    ps = pageSize
                )
                check(response.code == 0) { response.message.ifBlank { "推荐视频加载失败 (${response.code})" } }
                response.data?.list.orEmpty()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Logger.w(TAG, "BGM recommend API failed: ${e.message}")
            }
        }

    suspend fun getAllBgmRecommendVideos(musicId: String): Result<List<BgmRecommendVideo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = NetworkModule.api.getAllBgmRecommendList(musicId)
                check(response.code == 0) { response.message.ifBlank { "推荐视频加载失败 (${response.code})" } }
                response.data?.list.orEmpty().filter { it.bvid.isNotBlank() }.distinctBy { it.bvid to it.cid }
            }.onFailure { if (it is CancellationException) throw it }
        }

}
