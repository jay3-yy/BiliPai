package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SearchRepository {
    private val api = NetworkModule.searchApi
    private val navApi = NetworkModule.api

    // 🔥 返回类型必须是 Result<List<VideoItem>>
    suspend fun search(keyword: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            // Wbi 签名逻辑 (如果有)
            val navResp = navApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""

            val params = mapOf(
                "keyword" to keyword,
                "search_type" to "video" // 指定搜索视频
            )
            val signedParams = if (imgKey.isNotEmpty()) WbiUtils.sign(params, imgKey, subKey) else params

            val response = api.search(signedParams)

            // 提取 video 分类的数据
            // 注意：这里需要配合你之前修改过的 SearchVideoItem.toVideoItem()
            val videoList = response.data?.result
                ?.find { it.result_type == "video" }
                ?.data
                ?.map { it.toVideoItem() }
                ?: emptyList()

            Result.success(videoList)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥 返回类型必须是 Result<List<HotItem>>
    suspend fun getHotSearch(): Result<List<HotItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHotSearch()
            val list = response.data?.trending?.list ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}