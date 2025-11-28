package com.android.purebilibili.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.android.purebilibili.core.database.dao.SearchHistoryDao
import com.android.purebilibili.core.database.entity.SearchHistory

@Database(entities = [SearchHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
}