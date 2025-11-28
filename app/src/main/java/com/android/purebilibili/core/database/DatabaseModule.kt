package com.android.purebilibili.core.database

import android.content.Context
import androidx.room.Room

object DatabaseModule {
    @Volatile
    private var database: AppDatabase? = null

    // 移除手动 init 方法，改为提供一个带 Context 的获取方法
    // 这样谁想用数据库，谁就必须提供 Context，确保安全
    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pure_bilibili.db"
            )
                .fallbackToDestructiveMigration()
                .build()
            database = instance
            instance
        }
    }
}