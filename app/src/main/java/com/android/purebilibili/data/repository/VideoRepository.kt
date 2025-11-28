// 文件路径: data/repository/VideoRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.PlayUrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.InputStream

object VideoRepository {
    private val api = NetworkModule.api

    // 清晰度降级链 (从高到低)
    // 120:4K, 116:1080P60, 112:1080P+, 80:1080P, 64:720P, 32:480P, 16:360P
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
            Result.success(feedResp.data?.item ?: emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 2. 视频详情 + 智能画质获取
    suspend fun getVideoDetails(bvid: String): Result<Pair<ViewInfo, String>> = withContext(Dispatchers.IO) {
        try {
            // A. 获取详情
            val viewResp = api.getVideoInfo(bvid)
            val info = viewResp.data ?: throw Exception("视频详情为空: ${viewResp.message}")
            val cid = info.cid
            if (cid == 0L) throw Exception("CID 获取失败")

            // B. 确定起手画质
            val isLogin = !TokenManager.sessDataCache.isNullOrEmpty()
            // 如果登录了，尝试 4K (120)；没登录，尝试 1080P (80)
            val startQuality = if (isLogin) 120 else 80

            // C. 递归获取最佳链接
            val playData = fetchPlayUrlRecursive(bvid, cid, startQuality)
                ?: throw Exception("无法获取任何画质的播放地址")

            val url = playData.durl?.firstOrNull()?.url
                ?: playData.dash?.video?.firstOrNull()?.baseUrl
                ?: throw Exception("播放地址解析失败")

            Result.success(Pair(info, url))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 3. 切换清晰度 (指定 qn)
    suspend fun getPlayUrlData(bvid: String, cid: Long, qn: Int): PlayUrlData? = withContext(Dispatchers.IO) {
        // 切换时，我们严格尝试用户指定的 qn。如果失败，则回退到降级策略
        fetchPlayUrlWithWbi(bvid, cid, qn) ?: fetchPlayUrlRecursive(bvid, cid, qn)
    }

    // 🔥 核心：递归降级算法 🔥
    // 尝试请求 targetQn，如果失败，自动在 QUALITY_CHAIN 里找下一个更低的清晰度重试
    private suspend fun fetchPlayUrlRecursive(bvid: String, cid: Long, targetQn: Int): PlayUrlData? {
        // 1. 尝试请求
        try {
            val data = fetchPlayUrlWithWbi(bvid, cid, targetQn)
            // 只有当 durl 不为空时才算成功 (因为我们目前主要用 mp4 格式)
            if (data != null && !data.durl.isNullOrEmpty()) {
                return data
            }
        } catch (e: Exception) {
            // 忽略异常，准备降级
        }

        // 2. 失败了，寻找下一个备选方案
        // 找到 targetQn 在链表中的位置
        // 如果 targetQn 不在链表中（比如是 74 这种非标准清晰度），我们从头开始找小于它的第一个标准清晰度
        var nextIndex = -1
        if (targetQn in QUALITY_CHAIN) {
            nextIndex = QUALITY_CHAIN.indexOf(targetQn) + 1
        } else {
            // 比如请求 100，不在链表里，我们要找第一个 <= 100 的，即 80
            for (i in QUALITY_CHAIN.indices) {
                if (QUALITY_CHAIN[i] < targetQn) {
                    nextIndex = i
                    break
                }
            }
        }

        if (nextIndex == -1 || nextIndex >= QUALITY_CHAIN.size) {
            // 已经到底了
            return null
        }

        // 3. 递归调用下一个清晰度
        val nextQn = QUALITY_CHAIN[nextIndex]
        return fetchPlayUrlRecursive(bvid, cid, nextQn)
    }

    // 基础请求方法 (带 Wbi 签名)
    private suspend fun fetchPlayUrlWithWbi(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: throw Exception("Key Error")
            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

            val params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnval" to "1", // MP4 格式 (Legacy)
                "fnver" to "0",
                "fourk" to "1", // 开启 4K 支持
                "platform" to "html5", // 伪装成 HTML5 播放器
                "high_quality" to "1"
            )

            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            // 请求并获取 data
            val response = api.getPlayUrl(signedParams)
            if (response.code == 0) {
                return response.data
            }
            return null
        } catch (e: HttpException) {
            // 只有 402(付费/权限) 和 404(资源不存在) 需要降级，其他错误直接抛出
            if (e.code() == 402 || e.code() == 403 || e.code() == 404 || e.code() == 412) {
                return null // 返回 null 触发递归降级
            }
            throw e
        } catch (e: Exception) {
            return null
        }
    }

    // 其他辅助方法
    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = withContext(Dispatchers.IO) {
        try { api.getRelatedVideos(bvid).data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getDanmakuStream(cid: Long): InputStream? = withContext(Dispatchers.IO) {
        try { api.getDanmakuXml(cid).byteStream() } catch (e: Exception) { null }
    }
}