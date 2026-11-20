package com.android.purebilibili.data.repository

import android.content.Context
import com.android.purebilibili.app.PureApplication
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.database.entity.CommentFraudRecord
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.CommentFraudStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CommentFraudRepository {
    private val recordMutex = Mutex()

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
        recordMutex.withLock {
            try {
                val existing = getDao(context).getRecordByRpid(rpid)
                val record = CommentFraudRecord(
                    rpid = rpid,
                    oid = existing?.oid ?: oid,
                    type = existing?.type ?: type,
                    root = root.takeIf { it > 0L } ?: existing?.root ?: 0L,
                    parent = parent.takeIf { it > 0L } ?: existing?.parent ?: 0L,
                    uid = if (uid > 0L) uid else (existing?.uid ?: 0L),
                    source_id = sourceId ?: existing?.source_id,
                    origin_url = originUrl ?: existing?.origin_url,
                    message = message.ifBlank { existing?.message.orEmpty() },
                    proto3_payload = existing?.proto3_payload,
                    initial_status = initialStatus?.name ?: existing?.initial_status
                        ?: status.takeUnless { it == CommentFraudStatus.UNKNOWN }?.name,
                    status = if (status == CommentFraudStatus.UNKNOWN && existing != null) existing.status else status.name,
                    post_time = if (postTime > 0L) postTime else (existing?.post_time ?: 0L),
                    timestamp = timestamp
                )
                getDao(context).insertOrUpdate(record)
                Logger.d("CommentFraudRepo", "✅ 反诈记录已入库: rpid=$rpid, status=$status")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("CommentFraudRepo", "反诈记录入库失败", e)
            }
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
            require(com.android.purebilibili.core.store.TokenManager.accountIdentity.value.mid == record.uid && record.uid > 0L) { "请切换到发布评论的账号" }
            Logger.d("CommentFraudRepo", "🔄 开始复检历史评论: rpid=${record.rpid}, oid=${record.oid}")
            val result = CommentRepository.checkCommentStatus(
                aid = record.oid,
                rpid = record.rpid,
                rootId = record.root,
                type = record.type,
            )

            val newStatus = result.getOrThrow()
            val updatedRecord = record.copy(
                status = if (newStatus == CommentFraudStatus.UNKNOWN) record.status else newStatus.name,
                timestamp = System.currentTimeMillis()
            )
            getDao(context).insertOrUpdate(updatedRecord)
            Logger.d("CommentFraudRepo", "✅ 历史评论复检完成: rpid=${record.rpid}, 新状态=$newStatus")
            Result.success(newStatus)
        } catch (e: CancellationException) {
            throw e
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
            require(com.android.purebilibili.core.store.TokenManager.accountIdentity.value.mid == record.uid && record.uid > 0L) { "请切换到发布评论的账号" }
            Logger.d("CommentFraudRepo", "🗑️ 正在调用B站接口删除评论: rpid=${record.rpid}")
            val deleteResult = CommentRepository.deleteCommentForSubject(
                oid = record.oid,
                type = record.type,
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
        } catch (e: CancellationException) {
            throw e
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

    suspend fun exportToJson(context: Context = PureApplication.instance, ownerMid: Long? = null, compatibleFormat: Boolean = false): String = withContext(Dispatchers.IO) {
        val records = getDao(context).getAllRecords()
        val selected = records.filter { ownerMid == null || (ownerMid > 0L && it.uid == ownerMid) }
        if (compatibleFormat) encodeProto3CommentBackup(selected) else json.encodeToString(selected)
    }

    suspend fun importFromJson(jsonContent: String, context: Context = PureApplication.instance): Result<Int> = withContext(Dispatchers.IO) {
        try {
            require(jsonContent.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "备份文件不能超过 8 MB" }
            val importedList = decodeCommentBackup(jsonContent)
            // 已有记录保留本地最新的检查状态与元数据，重复导入不会覆盖。
            val inserted = getDao(context).importMissing(importedList.distinctBy { it.rpid })
            Result.success(inserted.count { it != -1L })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("CommentFraudRepo", "导入 JSON 备份失败", e)
            Result.failure(e)
        }
    }
}
