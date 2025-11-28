package com.android.purebilibili.data.repository

import android.content.Context
import com.android.purebilibili.core.database.DatabaseModule
import com.android.purebilibili.core.database.dao.SearchHistoryDao
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

object SearchRepository {
    private val api = NetworkModule.searchApi
    private val mainApi = NetworkModule.api
    private var historyDao: SearchHistoryDao? = null

    fun init(context: Context) {
        historyDao = DatabaseModule.getDatabase(context).searchHistoryDao()
    }

    // --- 1. 历史记录 ---
    fun getHistory(): Flow<List<SearchHistory>> = historyDao?.getAll() ?: emptyFlow()

    suspend fun addHistory(keyword: String) = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext
        historyDao?.deleteByKeyword(keyword)
        historyDao?.insert(SearchHistory(keyword = keyword))
    }

    suspend fun deleteHistory(history: SearchHistory) = withContext(Dispatchers.IO) {
        historyDao?.delete(history)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao?.clearAll()
    }

    // --- 2. 网络请求 ---
    suspend fun getHotSearch(): List<HotItem> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getHotSearch()
            resp.data?.trending?.list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 👇👇👇 核心修改 👇👇👇
    suspend fun search(keyword: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 Wbi Key
            val navResp = mainApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: throw Exception("无法获取 Wbi Key")

            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

            // 2. 构造参数：只传 keyword，去掉 search_type
            // 这样签名最不容易出错，解决了 412 问题
            val params = mapOf(
                "keyword" to keyword
            )

            // 3. 签名
            val signedParams = WbiUtils.sign(params, imgKey, subKey)

            // 4. 请求
            val resp = api.search(signedParams)

            // 5. 解析：从分类列表中找到 video 这一项
            val videoCategory = resp.data?.result?.find { it.result_type == "video" }
            val videos = videoCategory?.data ?: emptyList()

            Result.success(videos)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}