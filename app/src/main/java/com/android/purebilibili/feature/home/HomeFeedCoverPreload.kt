package com.android.purebilibili.feature.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.android.purebilibili.core.util.BackgroundLane
import com.android.purebilibili.core.util.backgroundImagePreload
import com.android.purebilibili.core.util.homeCoverDecodePolicy
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.warmHomeCardCoverTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Headers have no video range; aligned rows and waterfall cards share this mapping. */
internal fun resolveHomeVisibleVideoRange(
    visibleKeys: Set<Any>,
    videoRangesByLazyKey: Map<String, IntRange>,
): IntRange? {
    var first = Int.MAX_VALUE
    var last = -1
    for (key in visibleKeys) {
        if (key !is String) continue
        val range = videoRangesByLazyKey[key] ?: continue
        first = minOf(first, range.first)
        last = maxOf(last, range.last)
    }
    return if (last < 0) null else first..last
}

/**
 * 由可见 item 数除以列数推导可见行数（向上取整）。
 *
 * 脏参数必须不崩：`columns <= 0`（布局还没测量完）按 1 列处理，`visibleItemCount <= 0`
 * （整屏 header / 首帧）按 1 行处理。真正的夹取由 [resolveHomeCoverPreloadWindow] 负责，
 * 这里只保证除法安全。
 */
internal fun resolveHomeCoverVisibleRowCount(visibleItemCount: Int, columns: Int): Int {
    if (visibleItemCount <= 0) return 1
    val safeColumns = if (columns <= 0) 1 else columns
    return (visibleItemCount + safeColumns - 1) / safeColumns
}

/**
 * 一次采样：滚动窗口 + 可见视频区间 + 是否在滚动。
 *
 * 三者必须一起进 `distinctUntilChanged`：只比索引会让"停下但索引没变"的样本被吞掉，
 * 而远场窗口恰恰只在停下之后才允许铺开。
 */
@Immutable
private data class HomeCoverPreloadSample(
    val scroll: HomeFeedScrollSample,
    val visibleVideoRange: IntRange?,
    val isScrolling: Boolean,
)

/**
 * 首页封面预取泵：把"可见区"翻译成"近场 / 远场两条后台车道上的加载"。
 *
 * 结构与旧实现的根本差别（旧实现见 8f37735a2 之前的版本）：
 * 1. **效果身份稳定**。[LaunchedEffect] 的 key 里只有"换掉就该整体重来"的东西
 *    （loader、列表、尺寸、策略开关）；`visibleVideoRange` / `isScrolling` 这些**每帧都变**的
 *    状态只允许出现在 `snapshotFlow` 内部，否则滚动会不断取消并重建整个效果，预取永远走不出第一项。
 * 2. **生产 / 消费分离**。生产者把窗口写成 [MutableStateFlow]，两条车道独立消费最新窗口；
 *    窗口更新不打断已经在飞的请求，尚未开始的旧窗口请求在放行前丢弃。
 * 3. **近场优先、远场机会性**。近场走 [BackgroundLane.NEAR]（滚动中也能推进），
 *    远场走 [BackgroundLane.FAR]（等 220ms 前台静默），远场等待不阻塞近场。
 * 4. **去重**。[HomeCoverPrefetchLedger] 记住已完成的 cacheKey，`inFlight` 挡住同一轮里的重复项，
 *    因此"窗口抖动"不会变成"重复请求"。
 *
 * 请求身份（url / size / 解码策略 / memoryCacheKey / diskCacheKey / 预取标记）一个字节都不能改：
 * 它必须与卡片自己发出的可见请求逐字段一致，否则预取解出来的位图永远命不中内存缓存，
 * 表现为"预取了但滑过去还是要转圈"。
 *
 * @param videos 已做轮播排除与行分组的展示列表。
 * @param visibleVideoRange "lazy key -> 视频下标"映射之后的可见视频闭区间。
 * @param gridState 行级采样来源（只读索引，不读像素偏移）。
 * @param columns 网格列数，用于把"可见 item 数"折成行数。
 * @param requestSpec 封面请求尺寸，同时决定单张解码预算。
 * @param allowed 上游放行开关（页面在屏、图片工作允许等）。
 * @param isDataSaverActive 省流模式；开启时整条泵不启动。
 * @param isLowRamDevice 低端机；只砍远场。
 * @param isMeteredNetwork 计费网络；只砍远场。
 * @param aheadCount 兼容参数：`<= 0` 时整条泵关闭，张数不再由它决定。
 * @param isScrolling 额外的滚动来源（首页 pager 的拖动等）。**不得**进入 [LaunchedEffect] 的 key。
 * @param imageLoader 测试注入用。
 * @param warmCoverTint 当前卡片的信息区使用封面配色时，近场预取完成后预热代表色。
 */
@Composable
internal fun HomeFeedCoverPreload(
    videos: List<VideoItem>,
    visibleVideoRange: State<IntRange?>,
    gridState: HomeFeedGridState,
    columns: Int,
    requestSpec: HomeCoverRequestSpec,
    allowed: Boolean,
    isDataSaverActive: Boolean,
    isLowRamDevice: Boolean,
    isMeteredNetwork: Boolean,
    aheadCount: Int,
    isScrolling: State<Boolean>? = null,
    imageLoader: ImageLoader? = null,
    warmCoverTint: Boolean = false,
) {
    val context = LocalContext.current
    val loader = imageLoader ?: context.imageLoader
    // ledger 放在效果之外：省流开关、旋转、数据刷新都会重建效果，但"已经预取过"是跨效果的既成事实。
    val ledger = remember { HomeCoverPrefetchLedger() }
    // 配色来源切换时只取消取色子任务，图片预取继续消费当前窗口。
    val tintScope = if (warmCoverTint && allowed && !isDataSaverActive && aheadCount > 0) {
        key(videos, requestSpec, loader) { rememberCoroutineScope() }
    } else null
    val latestTintScope by rememberUpdatedState(tintScope)

    LaunchedEffect(
        context,
        loader,
        videos,
        requestSpec,
        allowed,
        columns,
        isDataSaverActive,
        isLowRamDevice,
        isMeteredNetwork,
        aheadCount,
    ) {
        // 兜底判断：调用方的 allowed 已含省流条件，这里再判一次，避免上游漏传时省流形同虚设。
        if (!allowed || isDataSaverActive || aheadCount <= 0) return@LaunchedEffect

        val maxItems = resolveCoverPreloadItemBudget(requestSpec.widthPx, requestSpec.heightPx)
        // maxItems 为 0（尺寸还没测量完）时不提前退出：窗口函数会返回空窗口，等尺寸就绪后
        // requestSpec 变化会重建效果，自然恢复预取。
        val windowFlow = MutableStateFlow(HomeCoverPreloadWindow())
        val inFlight = mutableSetOf<String>()

        coroutineScope {
            launch {
                produceHomeCoverPreloadWindows(
                    gridState = gridState,
                    visibleVideoRange = visibleVideoRange,
                    isScrolling = isScrolling,
                    totalItemCount = videos.size,
                    columns = columns,
                    isDataSaverActive = isDataSaverActive,
                    isLowRamDevice = isLowRamDevice,
                    isMeteredNetwork = isMeteredNetwork,
                    maxItems = maxItems,
                    windowFlow = windowFlow,
                )
            }
            launch {
                consumeHomeCoverPreloadWindows(
                    windowFlow = windowFlow,
                    needsPreload = { index ->
                        val source = resolveHomeCoverImageSource(videos[index], false, requestSpec)
                        source.url.isNotBlank() && !ledger.isPrefetched(source.cacheKey)
                    },
                ) { lane, index, isCurrent ->
                    preloadHomeCover(
                        index = index,
                        lane = lane,
                        isCurrent = isCurrent,
                        videos = videos,
                        requestSpec = requestSpec,
                        ledger = ledger,
                        inFlight = inFlight,
                        context = context,
                        loader = loader,
                        tintScope = { latestTintScope },
                    )
                }
            }
        }
    }
}

/**
 * 生产者：把"现在看到哪几行"折成窗口，写进 [windowFlow]。
 *
 * 采样只含索引级状态，`snapshotFlow` 自带 conflate，加上滚动期间主动节流
 * [HOME_COVER_PRELOAD_SCROLL_THROTTLE_MS]，一次 fling 里最多产生 7~8 次窗口重算，
 * 而不是每帧一次。节流用"消费完再 delay"实现：delay 期间的新状态会被 conflate 成最新一个，
 * 最后一个状态**永远不会丢**，这也保证停下后的远场窗口一定能发出来。
 */
private suspend fun produceHomeCoverPreloadWindows(
    gridState: HomeFeedGridState,
    visibleVideoRange: State<IntRange?>,
    isScrolling: State<Boolean>?,
    totalItemCount: Int,
    columns: Int,
    isDataSaverActive: Boolean,
    isLowRamDevice: Boolean,
    isMeteredNetwork: Boolean,
    maxItems: Int,
    windowFlow: MutableStateFlow<HomeCoverPreloadWindow>,
) {
    var previousFirstVisibleIndex: Int? = null
    snapshotFlow {
        HomeCoverPreloadSample(
            scroll = gridState.scrollSample(),
            visibleVideoRange = visibleVideoRange.value,
            // pager 拖动时 grid 可能没有 isScrollInProgress，两个来源取或，语义仍是"用户正在动"。
            isScrolling = isScrolling?.value == true || gridState.isScrollInProgress,
        )
    }
        .distinctUntilChanged()
        .collect { sample ->
            val direction = resolveHomeScrollDirection(
                previousFirstVisibleIndex = previousFirstVisibleIndex,
                currentFirstVisibleIndex = sample.scroll.firstVisibleIndex,
            )
            previousFirstVisibleIndex = sample.scroll.firstVisibleIndex
            windowFlow.value = resolveHomeCoverPreloadWindow(
                visibleVideoRange = sample.visibleVideoRange,
                totalItemCount = totalItemCount,
                columns = columns,
                visibleRowCount = resolveHomeCoverVisibleRowCount(sample.scroll.visibleItemCount, columns),
                direction = direction,
                isScrolling = sample.isScrolling,
                isDataSaverActive = isDataSaverActive,
                isLowRamDevice = isLowRamDevice,
                isMeteredNetwork = isMeteredNetwork,
                maxItems = maxItems,
            )
            if (sample.isScrolling) delay(HOME_COVER_PRELOAD_SCROLL_THROTTLE_MS)
        }
}

/** 调度器已复核窗口与许可；同键去重在开始加载时占位，避免远场等待阻塞近场。 */
private suspend fun preloadHomeCover(
    index: Int,
    lane: BackgroundLane,
    isCurrent: () -> Boolean,
    videos: List<VideoItem>,
    requestSpec: HomeCoverRequestSpec,
    ledger: HomeCoverPrefetchLedger,
    inFlight: MutableSet<String>,
    context: Context,
    loader: ImageLoader,
    tintScope: () -> CoroutineScope?,
) {
    val video = videos[index]
    val source = resolveHomeCoverImageSource(video, false, requestSpec)
    if (source.url.isBlank() || ledger.isPrefetched(source.cacheKey)) return
    if (!inFlight.add(source.cacheKey)) return
    try {
        val result = loader.execute(
            buildHomeCoverPreloadRequest(
                context = context,
                source = source,
                requestSpec = requestSpec,
            ),
        )
        if (result is SuccessResult) {
            ledger.recordPrefetched(source.cacheKey)
            // 取色挂在页面效果作用域，不阻塞下一张封面；过期窗口不再追加预热。
            if (lane == BackgroundLane.NEAR && isCurrent()) {
                tintScope()?.launch {
                    warmHomeCardCoverTint(context, source.cacheKey, source.url)
                }
            }
        }
    } finally {
        inFlight.remove(source.cacheKey)
    }
}

/**
 * 构造预取请求。
 *
 * 这六个字段是预取能否命中的全部原因，**不允许**在这里增加或删减：
 * - `data(source.url)`：与卡片一致，走同一套 URL 归一化与低画质策略；
 * - `size(requestSpec.widthPx, requestSpec.heightPx)`：尺寸不一致会让 Coil 认为需要重新采样；
 * - `homeCoverDecodePolicy()`：位图格式 / 色彩空间必须与卡片解码一致；
 * - `memoryCacheKey` / `diskCacheKey`：必须显式指定 cacheKey，否则 Coil 会按 request 参数哈希，
 *   预取与可见请求就会落到两个不同的缓存条目上；
 * - `backgroundImagePreload()`：标记为后台预取，让拦截器不做前台才需要的事。
 */
private fun buildHomeCoverPreloadRequest(
    context: Context,
    source: HomeCoverImageSource,
    requestSpec: HomeCoverRequestSpec,
): ImageRequest = ImageRequest.Builder(context)
    .data(source.url)
    .size(requestSpec.widthPx, requestSpec.heightPx)
    .homeCoverDecodePolicy()
    .memoryCacheKey(source.cacheKey)
    .diskCacheKey(source.cacheKey)
    .backgroundImagePreload()
    .build()
