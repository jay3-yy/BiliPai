package com.android.purebilibili.data.repository

import android.content.Context
import com.android.purebilibili.app.PureApplication
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.database.entity.CommentFraudRecord
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.CommentFraudStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CommentFraudRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun getDao(context: Context = PureApplication.instance) = 
        AppDatabase.getDatabase(context).commentFraudDao()

    /**
     * 保存或更新评论反诈记录 (支持完整元数据)
     */
    suspend fun saveRecord(
        rpid: Long,
        oid: Long,
        type: Int = 1,
        root: Long = 0L,
        parent: Long = 0L,
        uid: Long = 0L,
        sourceId: String? = null,
        message: String,
        status: CommentFraudStatus,
        initialStatus: CommentFraudStatus? = null,
        postTime: Long = 0L,
        originUrl: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        context: Context = PureApplication.instance
    ) = withContext(Dispatchers.IO) {
        if (rpid <= 0L) return@withContext
        try {
            val existing = getDao(context).getRecordByRpid(rpid)
            val record = CommentFraudRecord(
                rpid = rpid,
                oid = oid,
                type = type,
                root = root,
                parent = parent,
                uid = if (uid > 0L) uid else (existing?.uid ?: 0L),
                source_id = sourceId ?: existing?.source_id,
                origin_url = originUrl ?: existing?.origin_url,
                message = message.ifBlank { existing?.message.orEmpty() },
                initial_status = (initialStatus?.name ?: existing?.initial_status) ?: status.name,
                status = status.name,
                post_time = if (postTime > 0L) postTime else (existing?.post_time ?: 0L),
                timestamp = timestamp
            )
            getDao(context).insertOrUpdate(record)
            Logger.d("CommentFraudRepo", "✅ 反诈记录已入库: rpid=$rpid, status=$status")
        } catch (e: Exception) {
            Logger.e("CommentFraudRepo", "反诈记录入库失败", e)
        }
    }

    fun getAllRecordsFlow(context: Context = PureApplication.instance): Flow<List<CommentFraudRecord>> {
        return getDao(context).getAllRecordsFlow()
    }

    /**
     * 再次复检（复检成功若包含官方 ctime 顺便自愈补齐）
     */
    suspend fun recheckRecord(
        record: CommentFraudRecord,
        context: Context = PureApplication.instance
    ): Result<CommentFraudStatus> = withContext(Dispatchers.IO) {
        try {
            Logger.d("CommentFraudRepo", "🔄 开始复检历史评论: rpid=${record.rpid}, oid=${record.oid}")
            val result = CommentRepository.checkCommentStatus(
                aid = record.oid,
                rpid = record.rpid,
                rootId = record.root,
                waitMs = 0L
            )

            val newStatus = result.getOrDefault(CommentFraudStatus.UNKNOWN)
            val updatedRecord = record.copy(
                status = newStatus.name,
                timestamp = System.currentTimeMillis()
            )
            getDao(context).insertOrUpdate(updatedRecord)
            Logger.d("CommentFraudRepo", "✅ 历史评论复检完成: rpid=${record.rpid}, 新状态=$newStatus")
            Result.success(newStatus)
        } catch (e: Exception) {
            Logger.e("CommentFraudRepo", "历史评论复检异常", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBiliComment(
        record: CommentFraudRecord,
        context: Context = PureApplication.instance
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.d("CommentFraudRepo", "🗑️ 正在调用B站接口删除评论: rpid=${record.rpid}")
            val deleteResult = CommentRepository.deleteComment(
                aid = record.oid,
                rpid = record.rpid
            )
            if (deleteResult.isSuccess) {
                getDao(context).deleteByRpid(record.rpid)
                Logger.d("CommentFraudRepo", "✅ 删评成功并移除本地记录")
                Result.success(Unit)
            } else {
                val error = deleteResult.exceptionOrNull() ?: Exception("删评失败")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLocalRecord(rpid: Long, context: Context = PureApplication.instance) = withContext(Dispatchers.IO) {
        getDao(context).deleteByRpid(rpid)
    }

    suspend fun clearAllRecords(context: Context = PureApplication.instance) = withContext(Dispatchers.IO) {
        getDao(context).clearAll()
    }

    suspend fun exportToJson(context: Context = PureApplication.instance): String = withContext(Dispatchers.IO) {
        val records = getDao(context).getAllRecords()
        json.encodeToString(records)
    }

    suspend fun importFromJson(jsonContent: String, context: Context = PureApplication.instance): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val importedList = json.decodeFromString<List<CommentFraudRecord>>(jsonContent)
            if (importedList.isNotEmpty()) {
                getDao(context).insertAll(importedList)
            }
            Result.success(importedList.size)
        } catch (e: Exception) {
            Logger.e("CommentFraudRepo", "导入 JSON 备份失败", e)
            Result.failure(e)
        }
    }
}
