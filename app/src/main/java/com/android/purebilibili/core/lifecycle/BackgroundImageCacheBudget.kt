package com.android.purebilibili.core.lifecycle

import coil3.memory.MemoryCache

/** 容量上限在后台持续生效，新的通知封面写入也不能重新填满前台预算。 */
internal class BackgroundImageCacheBudget(private val cache: MemoryCache) {
    private val foregroundMaxBytes = cache.maxSize

    fun background(elapsedMs: Long) {
        cache.maxSize = resolveBackgroundImageCacheTrimTargetBytes(foregroundMaxBytes, elapsedMs)
        cache.trimToSize(cache.maxSize)
    }

    fun foreground() { cache.maxSize = foregroundMaxBytes }
}
