package com.android.purebilibili.core.database.dao

import androidx.room.*
import com.android.purebilibili.core.database.entity.CommentFraudRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentFraudDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: CommentFraudRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CommentFraudRecord>)

    /** 
     * 优先按真实的官方发评时间 post_time 倒序排列；
     * 复检老评论只会更新状态，防止老条目跳到列表顶部！
     */
    @Query("SELECT * FROM comment_fraud_records ORDER BY CASE WHEN post_time > 0 THEN post_time ELSE timestamp END DESC")
    fun getAllRecordsFlow(): Flow<List<CommentFraudRecord>>

    @Query("SELECT * FROM comment_fraud_records ORDER BY CASE WHEN post_time > 0 THEN post_time ELSE timestamp END DESC")
    suspend fun getAllRecords(): List<CommentFraudRecord>

    @Query("SELECT * FROM comment_fraud_records WHERE rpid = :rpid LIMIT 1")
    suspend fun getRecordByRpid(rpid: Long): CommentFraudRecord?

    @Query("DELETE FROM comment_fraud_records WHERE rpid = :rpid")
    suspend fun deleteByRpid(rpid: Long)

    @Query("DELETE FROM comment_fraud_records")
    suspend fun clearAll()
}
