// 文件路径: data/repository/VideoRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.InputStream
import java.util.TreeMap // 🔥 引入 TreeMap 用于参数排序

object VideoRepository {
    private val api = NetworkModule.api

    private val QUALITY_CHAIN = listOf(120, 116, 112, 80, 64, 32, 16)

    // 1. 首页推荐
    suspend fun getHomeVideos(idx: Int = 0): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: throw Exception("无法获取 Key")
            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

            val params = mapOf(
                "ps" to "10", "fresh_type" to "3", "fresh_idx" to idx.toString(),
                "feed_version" to System.currentTimeMillis().toString(), "y_num" to idx.toString()
            )
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val feedResp = api.getRecommendParams(signedParams)
            val list = feedResp.data?.item?.map { it.toVideoItem() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getNavInfo(): Result<NavData> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getNavInfo()
            if (resp.code == 0 && resp.data != null) {
                Result.success(resp.data)
            } else {
                if (resp.code == -101) {
                    Result.success(NavData(isLogin = false))
                } else {
                    Result.failure(Exception("错误码: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetails(bvid: String): Result<Pair<ViewInfo, PlayUrlData>> = withContext(Dispatchers.IO) {
        try {
            val viewResp = api.getVideoInfo(bvid)
            val info = viewResp.data ?: throw Exception("视频详情为空: ${viewResp.code}")
            val cid = info.cid
            if (cid == 0L) throw Exception("CID 获取失败")

            val isLogin = !TokenManager.sessDataCache.isNullOrEmpty()
            val startQuality = if (isLogin) 120 else 80

            val playData = fetchPlayUrlRecursive(bvid, cid, startQuality)
                ?: throw Exception("无法获取任何画质的播放地址")

            if (playData.durl.isNullOrEmpty()) throw Exception("播放地址解析失败 (无 durl)")

            Result.success(Pair(info, playData))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getPlayUrlData(bvid: String, cid: Long, qn: Int): PlayUrlData? = withContext(Dispatchers.IO) {
        fetchPlayUrlWithWbi(bvid, cid, qn) ?: fetchPlayUrlRecursive(bvid, cid, qn)
    }

    // 🔥🔥 [稳定版核心修复] 获取评论列表
    suspend fun getComments(aid: Long, page: Int, ps: Int = 20): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img

            if (wbiImg == null) {
                return@withContext Result.failure(Exception("无法获取 Wbi 签名密钥"))
            }

            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

            // 🔥 使用 TreeMap 保证签名顺序绝对正确
            val params = TreeMap<String, String>()
            params["oid"] = aid.toString()
            params["type"] = "1"     // 1: 视频评论区
            params["mode"] = "3"     // 3: 按热度排序
            params["next"] = page.toString()
            params["ps"] = ps.toString()

            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = api.getReplyList(signedParams)

            if (response.code == 0) {
                Result.success(response.data ?: ReplyData())
            } else {
                Result.failure(Exception("B站接口错误: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥🔥 [新增] 获取二级评论 (楼中楼)
    suspend fun getSubComments(aid: Long, rootId: Long, page: Int, ps: Int = 20): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            // 注意：需要在 ApiClient.kt 中定义 getReplyReply 接口
            val response = api.getReplyReply(
                oid = aid,
                root = rootId,
                pn = page,
                ps = ps
            )
            if (response.code == 0) {
                Result.success(response.data ?: ReplyData())
            } else {
                Result.failure(Exception("接口错误: ${response.code}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getEmoteMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        map["[doge]"] = "http://i0.hdslb.com/bfs/emote/6f8743c3c13009f4705307b2750e32f5068225e3.png"
        map["[笑哭]"] = "http://i0.hdslb.com/bfs/emote/500b63b2f293309a909403a746566fdd6104d498.png"
        map["[妙啊]"] = "http://i0.hdslb.com/bfs/emote/03c39c8eb009f63568971032b49c716259c72441.png"
        try {
            val response = api.getEmotes()
            response.data?.packages?.forEach { pkg ->
                pkg.emote?.forEach { emote -> map[emote.text] = emote.url }
            }
        } catch (e: Exception) { e.printStackTrace() }
        map
    }

    private suspend fun fetchPlayUrlRecursive(bvid: String, cid: Long, targetQn: Int): PlayUrlData? {
        try {
            val data = fetchPlayUrlWithWbi(bvid, cid, targetQn)
            if (data != null && !data.durl.isNullOrEmpty()) return data
        } catch (e: Exception) {}
        var nextIndex = -1
        if (targetQn in QUALITY_CHAIN) {
            nextIndex = QUALITY_CHAIN.indexOf(targetQn) + 1
        } else {
            for (i in QUALITY_CHAIN.indices) {
                if (QUALITY_CHAIN[i] < targetQn) { nextIndex = i; break }
            }
        }
        if (nextIndex == -1 || nextIndex >= QUALITY_CHAIN.size) return null
        return fetchPlayUrlRecursive(bvid, cid, QUALITY_CHAIN[nextIndex])
    }

    private suspend fun fetchPlayUrlWithWbi(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: throw Exception("Key Error")
            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
            val params = mapOf(
                "bvid" to bvid, "cid" to cid.toString(), "qn" to qn.toString(),
                "fnval" to "1", "fnver" to "0", "fourk" to "1", "platform" to "html5", "high_quality" to "1"
            )
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = api.getPlayUrl(signedParams)
            if (response.code == 0) return response.data
            return null
        } catch (e: HttpException) {
            if (e.code() in listOf(402, 403, 404, 412)) return null
            throw e
        } catch (e: Exception) { return null }
    }

    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = withContext(Dispatchers.IO) {
        try { api.getRelatedVideos(bvid).data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getDanmakuStream(cid: Long): InputStream? = withContext(Dispatchers.IO) {
        try { api.getDanmakuXml(cid).byteStream() } catch (e: Exception) { null }
    }
}