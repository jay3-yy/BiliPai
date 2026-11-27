package com.android.purebilibili.feature.home.components.cards

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.coroutines.AppScope
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COVER_COLOR_CACHE_MAGIC = 0x42504343 // "BPCC"
private const val COVER_COLOR_CACHE_VERSION = 2
private const val COVER_COLOR_HEADER_BYTES = 12
private const val COVER_COLOR_ENTRY_BYTES = 12
private const val FNV1A_64_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325
private const val FNV1A_64_PRIME = 1099511628211L // 0x100000001b3
internal const val COVER_COLOR_DISK_MAX_ENTRIES = 1024

internal fun hashCoverColorKey64(cacheKey: String): Long {
    var hash = FNV1A_64_OFFSET_BASIS
    for (index in 0 until cacheKey.length) {
        val code = cacheKey[index].code
        hash = (hash xor (code and 0xFF).toLong()) * FNV1A_64_PRIME
        if (code > 0xFF) {
            hash = (hash xor (code ushr 8).toLong()) * FNV1A_64_PRIME
        }
    }
    return hash
}

internal fun encodeCoverColorDiskEntries(entries: Map<Long, Int>): ByteArray {
    val buffer = ByteBuffer
        .allocate(COVER_COLOR_HEADER_BYTES + entries.size * COVER_COLOR_ENTRY_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
    buffer.putInt(COVER_COLOR_CACHE_MAGIC)
    buffer.putInt(COVER_COLOR_CACHE_VERSION)
    buffer.putInt(entries.size)
    entries.forEach { (hash, argb) ->
        buffer.putLong(hash)
        buffer.putInt(argb)
    }
    return buffer.array()
}

internal fun decodeCoverColorDiskEntries(
    raw: ByteArray,
    maxEntries: Int = COVER_COLOR_DISK_MAX_ENTRIES,
): LinkedHashMap<Long, Int> {
    val result = LinkedHashMap<Long, Int>(maxEntries + 1, 0.75f, true)
    if (raw.size < COVER_COLOR_HEADER_BYTES) return result
    val buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
    if (buffer.int != COVER_COLOR_CACHE_MAGIC) return result
    if (buffer.int != COVER_COLOR_CACHE_VERSION) return result
    val count = buffer.int
    val available = (raw.size - COVER_COLOR_HEADER_BYTES) / COVER_COLOR_ENTRY_BYTES
    val safeCount = count.coerceIn(0, minOf(available, maxEntries))
    repeat(safeCount) {
        val hash = buffer.long
        val argb = buffer.int
        result[hash] = argb
    }
    return result
}

/**
 * 极轻量封面代表色持久化 LRU（单条 12 字节，1024 条约 12KB）。
 * 仅在后台线程懒加载与防抖落盘，命中后跳过 CPU 密集的 Palette 聚类。
 */
internal object VideoCardCoverColorDiskCache {
    private const val CACHE_FILE_NAME = "cover_representative_colors.bin"
    private const val FLUSH_DEBOUNCE_MS = 4_000L
    private const val FLUSH_BATCH_THRESHOLD = 12

    private val lock = Any()
    private val fileWriteLock = Any()
    private var loaded = false
    private var entries = LinkedHashMap<Long, Int>(COVER_COLOR_DISK_MAX_ENTRIES + 1, 0.75f, true)
    private var pendingWrites = 0
    private var flushJob: Job? = null

    fun get(context: Context, cacheKey: String): Color? {
        val file = resolveCacheFile(context) ?: return null
        ensureLoaded(file)
        val hash = hashCoverColorKey64(cacheKey)
        val argb = synchronized(lock) { entries[hash] } ?: return null
        return Color(argb)
    }

    fun put(context: Context, cacheKey: String, color: Color) {
        val file = resolveCacheFile(context) ?: return
        ensureLoaded(file)
        val hash = hashCoverColorKey64(cacheKey)
        val argb = color.toArgb()
        val shouldFlushImmediately: Boolean
        synchronized(lock) {
            if (entries[hash] == argb) return
            entries[hash] = argb
            while (entries.size > COVER_COLOR_DISK_MAX_ENTRIES) {
                val eldest = entries.entries.iterator().next()
                entries.remove(eldest.key)
            }
            pendingWrites++
            shouldFlushImmediately = pendingWrites >= FLUSH_BATCH_THRESHOLD
        }
        scheduleFlush(file, immediate = shouldFlushImmediately)
    }

    private fun resolveCacheFile(context: Context): File? =
        context.applicationContext.cacheDir?.let { File(it, CACHE_FILE_NAME) }

    private fun ensureLoaded(file: File) {
        synchronized(lock) {
            if (loaded) return
        }
        val decoded = if (file.isFile) {
            runCatching { decodeCoverColorDiskEntries(file.readBytes()) }.getOrNull()
        } else {
            null
        }
        synchronized(lock) {
            if (!loaded) {
                if (decoded != null) {
                    decoded.forEach { (hash, argb) ->
                        entries.putIfAbsent(hash, argb)
                    }
                    while (entries.size > COVER_COLOR_DISK_MAX_ENTRIES) {
                        val eldest = entries.entries.iterator().next()
                        entries.remove(eldest.key)
                    }
                }
                loaded = true
            }
        }
    }

    private fun scheduleFlush(file: File, immediate: Boolean) {
        synchronized(lock) {
            if (immediate) {
                flushJob?.cancel()
                flushJob = AppScope.ioScope.launch { flushNow(file) }
            } else if (flushJob?.isActive != true) {
                flushJob = AppScope.ioScope.launch {
                    delay(FLUSH_DEBOUNCE_MS)
                    flushNow(file)
                }
            }
        }
    }

    private fun flushNow(file: File) {
        synchronized(fileWriteLock) {
            val snapshot: LinkedHashMap<Long, Int> = synchronized(lock) {
                if (pendingWrites == 0) return
                pendingWrites = 0
                LinkedHashMap(entries)
            }
            val bytes = encodeCoverColorDiskEntries(snapshot)
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(file)) {
                    file.writeBytes(bytes)
                    temp.delete()
                }
            }
        }
    }
}
