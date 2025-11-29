package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.InputStream

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

    // 2. 视频详情
    suspend fun getVideoDetails(bvid: String): Result<Pair<ViewInfo, PlayUrlData>> = withContext(Dispatchers.IO) {
        try {
            val viewResp = api.getVideoInfo(bvid)
            val info = viewResp.data ?: throw Exception("视频详情为空: ${viewResp.message}")
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

    // 3. 切换清晰度
    suspend fun getPlayUrlData(bvid: String, cid: Long, qn: Int): PlayUrlData? = withContext(Dispatchers.IO) {
        fetchPlayUrlWithWbi(bvid, cid, qn) ?: fetchPlayUrlRecursive(bvid, cid, qn)
    }

    // 4. 获取评论列表
    suspend fun getComments(aid: Long, page: Int, mode: Int = 3): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReplyList(oid = aid, next = page, mode = mode)
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥🔥 [核心修复] 获取表情映射表
    // 增加了兜底数据，确保常用表情一定能显示
    suspend fun getEmoteMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()

        // 1. 预置常用表情 (防止 API 失败或 key 不匹配)
        map["[doge]"] = "http://i0.hdslb.com/bfs/emote/6f8743c3c13009f4705307b2750e32f5068225e3.png"
        map["[笑哭]"] = "http://i0.hdslb.com/bfs/emote/500b63b2f293309a909403a746566fdd6104d498.png"
        map["[吃瓜]"] = "http://i0.hdslb.com/bfs/emote/010eb5f5f9a644265538e788c6e26b15865231c5.png"
        map["[OK]"] = "http://i0.hdslb.com/bfs/emote/296765792d47b678234842d593003b53f3e3e0c0.png"
        map["[星星眼]"] = "http://i0.hdslb.com/bfs/emote/0e1e9447432c6383626ce14c5520970a248f731d.png"
        map["[辣眼睛]"] = "http://i0.hdslb.com/bfs/emote/34c760443d3b95a864701297f66a9d20c5861195.png"
        map["[妙啊]"] = "http://i0.hdslb.com/bfs/emote/2f534346896200234b3e34347783b92f913d2a71.png"

        try {
            // 2. 尝试从 API 获取完整列表并覆盖
            val response = api.getEmotes()
            response.data?.packages?.forEach { pkg ->
                pkg.emote?.forEach { emote ->
                    map[emote.text] = emote.url
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // API 失败时，依然返回上面的预置 map
        }
        map
    }

    // --- 内部私有方法 ---

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
                if (QUALITY_CHAIN[i] < targetQn) {
                    nextIndex = i
                    break
                }
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
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = withContext(Dispatchers.IO) {
        try { api.getRelatedVideos(bvid).data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getDanmakuStream(cid: Long): InputStream? = withContext(Dispatchers.IO) {
        try { api.getDanmakuXml(cid).byteStream() } catch (e: Exception) { null }
    }
}