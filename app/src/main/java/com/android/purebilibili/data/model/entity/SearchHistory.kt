package com.android.purebilibili.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    // 用于按时间倒序排列
    val timestamp: Long = System.currentTimeMillis()
)