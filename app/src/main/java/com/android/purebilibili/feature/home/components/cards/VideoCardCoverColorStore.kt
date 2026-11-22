package com.android.purebilibili.feature.home.components.cards

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.materialkolor.hct.Hct
import kotlin.math.abs
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

internal class CoverCardSurface(
    val color: Color?,
    val blurredCover: ImageBitmap,
)

/** 封面代表色与磨砂底图共用一次磁盘解码和缓存。 */
object VideoCardCoverColorStore {
    private const val MAX_CACHE_SIZE = 512

    private data class SurfaceKey(val cover: String, val includeTint: Boolean)

    private val surfaceCache = LruCache<SurfaceKey, CoverCardSurface>(MAX_CACHE_SIZE)
    private val extractionPermits = Semaphore(COVER_COLOR_EXTRACTION_PARALLELISM)

    /**
     * 同一封面的预热和可见卡片共享在途结果。
     *
     * 为什么需要：预取预热、卡片自己、返回预热这三路可能对**同一张封面**几乎同时发起提取。
     * 没有去重时它们会各自走一遍「磁盘读 + 96×96 解码 + Palette 聚类」，既白烧 CPU，
     * 又互相抢那 2 路许可把真正该先算的卡挤到队尾。
     *
     * 用 [ConcurrentHashMap] 而不是 `synchronized` 的 `mutableSet`：标记必须在挂起（抢许可、
     * await）之前完成，而 `synchronized` 块内不允许挂起，`putIfAbsent` 天然满足"原子抢标记"。
     */
    private val inFlight = ConcurrentHashMap<SurfaceKey, CompletableDeferred<CoverCardSurface?>>()

    internal fun getCachedSurface(cacheKey: String, includeTint: Boolean = true): CoverCardSurface? =
        surfaceCache.get(SurfaceKey(cacheKey, includeTint))
            ?: if (!includeTint) surfaceCache.get(SurfaceKey(cacheKey, true)) else null

    suspend fun extractColor(context: Context, cacheKey: String, coverUrl: String): Color? =
        extractSurface(context, cacheKey, coverUrl)?.color

    /**
     * 提取（或复用）封面代表色。
     *
     * 调用方拥有取消权。顺序是：查缓存 → 抢 in-flight 标记 → 拿取色许可 → 再查一次缓存 → 真正算。
     * 缓存命中不排队，避免一次回前台后的几十张卡片被队首那几张的「磁盘读 + 解码 + 聚类」拖住。
     */
    internal suspend fun extractSurface(
        context: Context,
        cacheKey: String,
        coverUrl: String,
        includeTint: Boolean = true,
    ): CoverCardSurface? = withContext(Dispatchers.Default) {
        if (cacheKey.isBlank() || coverUrl.isBlank()) return@withContext null
        val surfaceKey = SurfaceKey(cacheKey, includeTint)
        getCachedSurface(cacheKey, includeTint)?.let { return@withContext it }

        // 最多两轮：第一轮若并入别人的在途提取却拿到空结果（对方被取消），第二轮自己当 owner。
        var attempt = 0
        while (attempt < 2) {
            val claim = CompletableDeferred<CoverCardSurface?>()
            val existing = inFlight.putIfAbsent(surfaceKey, claim)
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
                getCachedSurface(cacheKey, includeTint)?.let { return@withContext it }
                attempt++
                continue
            }
            try {
                val surface = extractionPermits.withPermit {
                    // 拿到许可后再查一次：排队期间别人可能已经把颜色算好了。
                    getCachedSurface(cacheKey, includeTint)
                        ?: extractUncachedSurface(context = context, surfaceKey = surfaceKey, coverUrl = coverUrl)
                }
                claim.complete(surface)
                return@withContext surface
            } catch (cancelled: CancellationException) {
                // 先叫醒等待者再抛：否则并入本 key 的协程会一直挂在一个永不完结的通道上。
                claim.complete(null)
                throw cancelled
            } catch (error: Throwable) {
                claim.complete(null)
                throw error
            } finally {
                inFlight.remove(surfaceKey, claim)
            }
        }
        null
    }

    /** 调用方负责持有 [extractionPermits]；进入时已确认缓存未命中。 */
    private suspend fun extractUncachedSurface(
        context: Context,
        surfaceKey: SurfaceKey,
        coverUrl: String,
    ): CoverCardSurface? {
        // Reuse the displayed cover's encoded disk entry, never copy its full hardware bitmap.
        // Memory reads/writes are disabled so this software sample is privately owned.
        val request = ImageRequest.Builder(context.applicationContext)
            .data(coverUrl)
            .diskCacheKey(surfaceKey.cover)
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
            val color = if (surfaceKey.includeTint) {
                extractRepresentativeColor(bitmap) ?: return null
            } else null
            val blurred = surfaceCache.get(SurfaceKey(surfaceKey.cover, false))?.blurredCover
                ?: createBlurredCardImage(bitmap).asImageBitmap()
            CoverCardSurface(color, blurred).also { surfaceCache.put(surfaceKey, it) }
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

    /** 相近色相合计面积，避免大片低饱和画面被一小片彩色字幕或角标染色。 */
    internal fun resolveRepresentativeSwatch(
        swatches: List<Palette.Swatch>,
    ): Palette.Swatch? {
        val dominant = swatches.maxByOrNull { it.population } ?: return null
        val minimumColorPopulation = swatches.sumOf { it.population } * 0.20
        var neutral: Palette.Swatch? = null
        val colorful = swatches.mapNotNull { swatch ->
            val color = Hct.fromInt(swatch.rgb)
            if (color.chroma < 12.0) {
                if (swatch.population > (neutral?.population ?: 0)) neutral = swatch
                null
            } else if (color.tone in 12.0..94.0) {
                swatch to color.hue
            } else null
        }
        var best = neutral ?: dominant
        var bestPopulation = 0
        for ((swatch, hue) in colorful) {
            val population = colorful.sumOf { (neighbor, neighborHue) ->
                val distance = abs(hue - neighborHue)
                if (minOf(distance, 360.0 - distance) <= 24.0) neighbor.population else 0
            }
            if (population >= minimumColorPopulation &&
                (population > bestPopulation || (population == bestPopulation && swatch.population > best.population))
            ) {
                best = swatch
                bestPopulation = population
            }
        }
        return best
    }

    fun trimToSize(maxSize: Int) {
        surfaceCache.trimToSize(maxSize)
    }

    fun clear() {
        surfaceCache.evictAll()
    }
}
