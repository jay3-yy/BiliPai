package com.android.purebilibili.core.util

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheUtils {

    // 获取缓存大小
    suspend fun getTotalCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        var cacheSize = 0L
        // App 内部缓存
        cacheSize += getDirSize(context.cacheDir)
        // 外部缓存
        if (context.externalCacheDir != null) {
            cacheSize += getDirSize(context.externalCacheDir)
        }
        return@withContext formatSize(cacheSize.toDouble())
    }

    // 清除所有缓存
    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
        // 1. 清除 Coil 图片内存缓存
        context.imageLoader.memoryCache?.clear()

        // 2. 删除磁盘缓存文件
        deleteDir(context.cacheDir)
        if (context.externalCacheDir != null) {
            deleteDir(context.externalCacheDir)
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        if (dir.isDirectory) {
            dir.listFiles()?.forEach {
                if (!deleteDir(it)) return false
            }
        }
        return dir.delete()
    }

    private fun formatSize(size: Double): String {
        val kiloByte = size / 1024
        if (kiloByte < 1) return "0K"
        val megaByte = kiloByte / 1024
        if (megaByte < 1) return String.format("%.2fK", kiloByte)
        val gigaByte = megaByte / 1024
        if (gigaByte < 1) return String.format("%.2fM", megaByte)
        return String.format("%.2fG", gigaByte)
    }
}