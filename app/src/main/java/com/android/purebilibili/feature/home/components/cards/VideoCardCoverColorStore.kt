package com.android.purebilibili.feature.home.components.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.size.Precision
import coil3.size.Scale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 代表色提取的并行度。
 *
 * 原来的单 Mutex 把提取完全串行化：回前台后几十张卡片会排成一条队，每张都要走
 * 「磁盘读 + 解码 + Palette 聚类」，队列尾部要等几百毫秒才拿到颜色，掉帧窗口被整体拉长。
 * 放开到 2 路既能让队列快速排空，又不会挤占 UI 线程的渲染预算。
 */
internal const val COVER_COLOR_EXTRACTION_PARALLELISM = 2

/**
 * 视频封面代表色提取与缓存管理
 * 复用官方 androidx.palette:palette-ktx 能力，纯异步单向流，严防循环采样。
 */
object VideoCardCoverColorStore {
    /**
     * 内存缓存条目上限。
     *
     * 单条是 `String -> Color`，实际占用不到 100B，512 条也就几十 KB —— 相对一屏首页的封面
     * 位图预算可以忽略。缓存条目越多，"滑回来"和"预取预热"能命中的概率越高，取色就越是
     * 纯查表而不是重新解码 + 聚类。
     *
     * 内存压力仍然由 `PureApplication` 的后台 / `onTrimMemory` 回调兜底：它调用
     * [trimToSize] 砍到 `BACKGROUND_COVER_COLOR_CACHE_MAX_ENTRIES = 64`，也就是本表的 1/8。
     */
    private const val MAX_CACHE_SIZE = 512

    private val colorCache = LruCache<String, Color>(MAX_CACHE_SIZE)
    private val extractionPermits = Semaphore(COVER_COLOR_EXTRACTION_PARALLELISM)

    /**
     * 同 key 的 in-flight 去重表：`cacheKey -> 该次提取的共享结果通道`。
     *
     * 为什么需要：预取预热、卡片自己、返回预热这三路可能对**同一张封面**几乎同时发起提取。
     * 没有去重时它们会各自走一遍「磁盘读 + 96×96 解码 + Palette 聚类」，既白烧 CPU，
     * 又互相抢那 2 路许可把真正该先算的卡挤到队尾。
     *
     * 用 [ConcurrentHashMap] 而不是 `synchronized` 的 `mutableSet`：标记必须在挂起（抢许可、
     * await）之前完成，而 `synchronized` 块内不允许挂起，`putIfAbsent` 天然满足"原子抢标记"。
     */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Color?>>()

    /** 同步获取已缓存的封面代表色。 */
    fun getCachedColor(cacheKey: String): Color? {
        if (cacheKey.isBlank()) return null
        return synchronized(colorCache) {
            colorCache.get(cacheKey)
        }
    }

    /**
     * 提取（或复用）封面代表色。
     *
     * 调用方拥有取消权。顺序是：查缓存 → 抢 in-flight 标记 → 拿取色许可 → 再查一次缓存 → 真正算。
     * 缓存命中不排队，避免一次回前台后的几十张卡片被队首那几张的「磁盘读 + 解码 + 聚类」拖住。
     */
    suspend fun extractColor(
        context: Context,
        cacheKey: String,
        coverUrl: String,
    ): Color? = withContext(Dispatchers.Default) {
        if (cacheKey.isBlank() || coverUrl.isBlank()) return@withContext null
        getCachedColor(cacheKey)?.let { return@withContext it }

        // 最多两轮：第一轮若并入别人的在途提取却拿到空结果（对方被取消），第二轮自己当 owner。
        var attempt = 0
        while (attempt < 2) {
            val claim = CompletableDeferred<Color?>()
            val existing = inFlight.putIfAbsent(cacheKey, claim)
            if (existing != null) {
                val shared = try {
                    existing.await()
                } catch (cancelled: CancellationException) {
                    // 只可能来自"自己所在协程被取消"：共享通道永远不会被 cancel，只会被 complete。
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                shared?.let { return@withContext it }
                getCachedColor(cacheKey)?.let { return@withContext it }
                attempt++
                continue
            }
            try {
                val color = extractionPermits.withPermit {
                    // 拿到许可后再查一次：排队期间别人可能已经把颜色算好了。
                    getCachedColor(cacheKey)
                        ?: extractUncachedColor(context = context, cacheKey = cacheKey, coverUrl = coverUrl)
                }
                claim.complete(color)
                return@withContext color
            } catch (cancelled: CancellationException) {
                // 先叫醒等待者再抛：否则并入本 key 的协程会一直挂在一个永不完结的通道上。
                claim.complete(null)
                throw cancelled
            } catch (error: Throwable) {
                claim.complete(null)
                throw error
            } finally {
                inFlight.remove(cacheKey, claim)
            }
        }
        null
    }

    /** 调用方负责持有 [extractionPermits]；进入时已确认缓存未命中。 */
    private suspend fun extractUncachedColor(
        context: Context,
        cacheKey: String,
        coverUrl: String,
    ): Color? {
        // Reuse the displayed cover's encoded disk entry, never copy its full hardware bitmap.
        // Memory reads/writes are disabled so this software sample is privately owned.
        val request = ImageRequest.Builder(context.applicationContext)
            .data(coverUrl)
            .diskCacheKey(cacheKey)
            .size(96, 96)
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .allowRgb565(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        var sample: Bitmap? = null
        return try {
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
            val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
            sample = bitmap
            val color = extractRepresentativeColor(bitmap) ?: return null
            synchronized(colorCache) { colorCache.put(cacheKey, color) }
            color
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            sample?.recycle()
        }
    }

    /**
     * 对整张封面聚类，按 swatch 面积选择代表色，避免小面积字幕色压过主体画面。
     */
    internal fun extractRepresentativeColor(bitmap: Bitmap): Color? {
        return runCatching {
            if (bitmap.isRecycled) return@runCatching null

            val palette = Palette.from(bitmap)
                .resizeBitmapArea(48 * 48)
                .maximumColorCount(16)
                .clearFilters()
                .generate()

            resolveRepresentativeSwatch(palette.swatches)?.rgb?.let { Color(it) }
        }.getOrNull()
    }

    /** Select the largest useful color; saturated color wins only when it covers area. */
    internal fun resolveRepresentativeSwatch(
        swatches: List<Palette.Swatch>,
    ): Palette.Swatch? {
        if (swatches.isEmpty()) return null
        val useful = swatches.filterNot { swatch ->
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(swatch.rgb, hsv)
            hsv[1] < 0.08f || hsv[2] < 0.08f || hsv[2] > 0.96f
        }
        return (useful.ifEmpty { swatches }).maxByOrNull { it.population }
    }

    fun trimToSize(maxSize: Int) {
        synchronized(colorCache) {
            colorCache.trimToSize(maxSize)
        }
    }

    fun clear() {
        synchronized(colorCache) {
            colorCache.evictAll()
        }
    }
}
