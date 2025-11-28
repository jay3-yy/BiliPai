package com.android.purebilibili.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.purebilibili.core.database.entity.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistory)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getAll(): Flow<List<SearchHistory>>

    @Delete
    suspend fun delete(history: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
    
    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)
}
