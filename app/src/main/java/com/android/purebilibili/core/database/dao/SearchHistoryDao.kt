package com.android.purebilibili.core.database.dao

import androidx.room.*
import com.android.purebilibili.core.database.entity.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    // 1. 查询所有历史，按时间倒序
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getAll(): Flow<List<SearchHistory>>

    // 2. 插入或替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistory)

    // 3. 删除单条
    @Delete
    suspend fun delete(history: SearchHistory)

    // 4. 清空所有
    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}