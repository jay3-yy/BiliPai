package com.android.purebilibili.feature.home

import androidx.compose.runtime.Immutable

// 近场固定只铺一行：再少就接不上下一屏，再多就会和用户滚动速度赛跑。
internal const val HOME_COVER_PRELOAD_NEAR_ROWS = 1

// 远场行数上限：手机上一屏通常 2~3 行，按 3 行外推已经能覆盖一次快速滑动的落点。
internal const val HOME_COVER_PRELOAD_FAR_ROWS_MAX = 3

// 单个窗口的总张数上限，避免平板 / 大列数下把整页封面一次全部解码。
internal const val HOME_COVER_PRELOAD_MAX_ITEMS = 12

// 预加载解码字节预算（10MB，ARGB_8888）。超过这个量级就会挤掉可见卡自身的解码。
internal const val HOME_COVER_PRELOAD_MAX_DECODE_BYTES = 10L * 1024 * 1024

// 滚动中重新计算窗口的最小间隔，用来把 fling 期间每帧的窗口重算压到可接受频率。
internal const val HOME_COVER_PRELOAD_SCROLL_THROTTLE_MS = 140L

// 可见行数脏值的兜底上限：超过 8 行的“可见区”一定不是真实布局，按 8 行估算即可。
internal const val HOME_COVER_PRELOAD_VISIBLE_ROW_MAX = 8

/**
 * 首页信息流最近一次的滚动方向。
 *
 * 需要区分方向是因为预取必须“顺着用户的方向”铺：
 * 往上滑时下一屏在下方，往下回滚时下一屏在上方，方向搞反就是纯浪费流量。
 */
internal enum class HomeScrollDirection { FORWARD, BACKWARD, IDLE }

/**
 * 一次预取决策的结果窗口，两个区间都是**闭区间**，且保证落在 `[0, totalItemCount)` 内。
 *
 * 拆成近场 / 远场而不是单个区间，是因为两者的收益和代价完全不同：
 * - [near]：紧贴可见区、下一次滚动立刻要用的封面，属于体验底线，任何省电/省流策略都不能砍。
 * - [far]：机会性预取，只在设备与网络都宽裕时铺开，随时可以被牺牲。
 */
@Immutable
internal data class HomeCoverPreloadWindow(
    val near: IntRange? = null,
    val far: IntRange? = null,
) {
    /** 两个窗口都为空时，调用方可以直接跳过整条预取链路。 */
    val isEmpty: Boolean
        get() = near == null && far == null

    /**
     * 是否允许为预取窗口提前解析主题色。
     *
     * 取色只跟随 [near]：远场是机会性预取，提前算色会白烧 CPU 却大概率在真正可见前就被替换；
     * 而可见卡片自身的取色由卡片内既有路径负责，不并入本窗口，避免两条路径重复计算同一种颜色。
     */
    val tintEnabled: Boolean
        get() = near != null

    /** 近场张数（闭区间元素个数，`null` 视为 0）。 */
    val nearCount: Int
        get() = near.countInclusive()

    /** 远场张数（闭区间元素个数，`null` 视为 0）。 */
    val farCount: Int
        get() = far.countInclusive()
}

/** 闭区间元素个数。用算术语义而不是 `count()`，避免对大区间做迭代。 */
private fun IntRange?.countInclusive(): Int {
    val range = this ?: return 0
    return (range.last - range.first + 1).coerceAtLeast(0)
}

/**
 * 由“上一次可见首项”和“本次可见首项”推导滚动方向。
 *
 * 首项变小说明内容在往上走（用户往下回滚），此时下一屏在可见区上方；
 * 没有上一次样本（首次布局 / 切换 tab 后重建）时返回 [HomeScrollDirection.IDLE]，
 * 让调用方落到默认的向下预取，而不是猜一个方向。
 */
internal fun resolveHomeScrollDirection(
    previousFirstVisibleIndex: Int?,
    currentFirstVisibleIndex: Int,
): HomeScrollDirection {
    if (previousFirstVisibleIndex == null) return HomeScrollDirection.IDLE
    return when {
        currentFirstVisibleIndex > previousFirstVisibleIndex -> HomeScrollDirection.FORWARD
        currentFirstVisibleIndex < previousFirstVisibleIndex -> HomeScrollDirection.BACKWARD
        else -> HomeScrollDirection.IDLE
    }
}

/**
 * 计算一次封面预取窗口。
 *
 * 决策顺序刻意固定，因为每一步的代价不同：
 * 1. 省流 / 没有可见视频卡 / 列表非法 → 直接空窗口。省流优先级最高，且 header-only
 *    时**不允许**假设 index 0 可见，否则会把首页拽回顶部重新预取一遍。
 * 2. 把脏参数归一化（列数、可见行数、容量上限），保证后面所有算术都在安全区间。
 * 3. 先算 [HomeCoverPreloadWindow.near]：它是体验底线，低端机 / 计费网络也不砍。
 * 4. 再算 [HomeCoverPreloadWindow.far]：只在静止、非低端机、非计费网络时铺开。
 * 5. 最后按 [maxItems] 收口，先保 near，再从 far 的远端截断。
 *
 * @param visibleVideoRange 当前可见的视频卡下标闭区间；`null` 表示整屏都是 header / 轮播。
 * @param totalItemCount 列表项总数（含 header 等非视频项）。
 * @param columns 网格列数，脏值（0 / 负数）按 1 列处理。
 * @param visibleRowCount 当前可见行数，用于推导远场要铺几行；脏值会被夹到 `[1, 8]`。
 * @param direction 最近一次滚动方向；滚动中但方向未知时按向下预取处理。
 * @param isScrolling 是否正在滚动。滚动中只预取紧邻的一行，静止后才铺远场。
 * @param isDataSaverActive 省流模式，开启时 near / far 全部关闭。
 * @param isLowRamDevice 低端机，只砍 far。
 * @param isMeteredNetwork 计费网络，只砍 far。
 * @param maxItems near + far 的张数上限。
 */
internal fun resolveHomeCoverPreloadWindow(
    visibleVideoRange: IntRange?,
    totalItemCount: Int,
    columns: Int,
    visibleRowCount: Int,
    direction: HomeScrollDirection,
    isScrolling: Boolean,
    isDataSaverActive: Boolean,
    isLowRamDevice: Boolean,
    isMeteredNetwork: Boolean = false,
    maxItems: Int = HOME_COVER_PRELOAD_MAX_ITEMS,
): HomeCoverPreloadWindow {
    // 省流是第一优先级：连“紧邻一行”也不预取，否则省流开关形同虚设。
    if (isDataSaverActive) return HomeCoverPreloadWindow()
    // 整屏 header / 轮播时没有可见视频卡：此时任何猜测都会把预取锚点弄错。
    if (visibleVideoRange == null) return HomeCoverPreloadWindow()
    if (totalItemCount <= 0) return HomeCoverPreloadWindow()

    val firstVisibleIndex = visibleVideoRange.first
    val lastVisibleIndex = visibleVideoRange.last
    if (firstVisibleIndex < 0 || lastVisibleIndex >= totalItemCount || firstVisibleIndex > lastVisibleIndex) {
        return HomeCoverPreloadWindow()
    }

    val effectiveColumns = columns.coerceAtLeast(1)
    val effectiveRows = visibleRowCount.coerceIn(1, HOME_COVER_PRELOAD_VISIBLE_ROW_MAX)
    val effectiveFarRows = effectiveRows.coerceAtMost(HOME_COVER_PRELOAD_FAR_ROWS_MAX)
    val effectiveMaxItems = maxItems.coerceAtLeast(0)
    // 容量为 0 时什么都不该预取，提前返回可以省掉后面的全部算术。
    if (effectiveMaxItems <= 0) return HomeCoverPreloadWindow()

    // 正在滚动但方向未知（首次布局 / 刚 settle）时按向下预取，这是信息流的主方向。
    val effectiveDirection = if (isScrolling && direction == HomeScrollDirection.IDLE) {
        HomeScrollDirection.FORWARD
    } else {
        direction
    }
    val isForward = effectiveDirection != HomeScrollDirection.BACKWARD
    val maxIndex = totalItemCount - 1

    val nearCandidate = if (isForward) {
        // 已经在底部时没有“下一屏”，返回 null 而不是夹成一个残窗。
        val nearStart = lastVisibleIndex + 1
        if (nearStart > maxIndex) {
            null
        } else {
            val nearEnd = minOf(lastVisibleIndex.toLong() + effectiveColumns.toLong(), maxIndex.toLong()).toInt()
            nearStart..nearEnd
        }
    } else {
        // 已经在顶部时没有“上一屏”。
        val nearEnd = firstVisibleIndex - 1
        if (nearEnd < 0) {
            null
        } else {
            val nearStart = maxOf(firstVisibleIndex.toLong() - effectiveColumns.toLong(), 0L).toInt()
            nearStart..nearEnd
        }
    }

    // 容量不够时先保 near：贴近可见区的索引收益远高于远场。
    val near = if (nearCandidate != null && nearCandidate.countInclusive() > effectiveMaxItems) {
        if (isForward) {
            // 向下：保留离可见区最近的头部。
            nearCandidate.first..(nearCandidate.first + effectiveMaxItems - 1)
        } else {
            // 向上：保留离可见区最近的尾部。
            (nearCandidate.last - effectiveMaxItems + 1)..nearCandidate.last
        }
    } else {
        nearCandidate
    }

    var far: IntRange? = null
    // 远场只在“静止 + 不省电 + 不计费”时铺：它随时可能被浪费，必须没有额外代价才做。
    if (near != null && !isScrolling && !isLowRamDevice && !isMeteredNetwork) {
        val remainingBudget = effectiveMaxItems - near.countInclusive()
        if (remainingBudget > 0) {
            val farSpan = effectiveColumns.toLong() * effectiveFarRows.toLong()
            if (isForward) {
                val farStart = near.last + 1
                if (farStart <= maxIndex) {
                    val farEnd = minOf(farStart.toLong() + farSpan - 1L, maxIndex.toLong()).toInt()
                    if (farEnd >= farStart) {
                        val truncatedEnd = minOf(farEnd, farStart + remainingBudget - 1)
                        far = farStart..truncatedEnd
                    }
                }
            } else {
                val farEnd = near.first - 1
                if (farEnd >= 0) {
                    val farStart = maxOf(farEnd.toLong() - farSpan + 1L, 0L).toInt()
                    if (farStart <= farEnd) {
                        // 向上时“远端”是头部，容量不够从头部截断，保留贴近可见区的尾部。
                        val truncatedStart = maxOf(farStart, farEnd - remainingBudget + 1)
                        far = truncatedStart..farEnd
                    }
                }
            }
        }
    }

    return HomeCoverPreloadWindow(near = near, far = far)
}

/**
 * 按单张解码字节数把总字节预算换算成张数上限。
 *
 * 这里按 ARGB_8888（4 字节/像素）估算，因为封面解码最终都落在该格式上；
 * 宽高任意一个非正数说明布局还没测量完，此时返回 0 而不是猜一个尺寸。
 */
internal fun resolveCoverPreloadItemBudget(
    decodeWidthPx: Int,
    decodeHeightPx: Int,
    maxDecodeBytes: Long = HOME_COVER_PRELOAD_MAX_DECODE_BYTES,
): Int {
    if (decodeWidthPx <= 0 || decodeHeightPx <= 0) return 0
    if (maxDecodeBytes <= 0L) return 0
    // 用 Long 相乘，避免大尺寸下 Int 溢出成负数。
    val bytesPerItem = decodeWidthPx.toLong() * decodeHeightPx.toLong() * 4L
    if (bytesPerItem <= 0L) return 0
    return minOf(maxDecodeBytes / bytesPerItem, HOME_COVER_PRELOAD_MAX_ITEMS.toLong()).toInt()
}
