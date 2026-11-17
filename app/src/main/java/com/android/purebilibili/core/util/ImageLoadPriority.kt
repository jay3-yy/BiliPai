package com.android.purebilibili.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal val AppImageLoadPriority = ImageLoadPriority()

/**
 * 后台车道。每条车道拥有独立的并发许可，彼此不排队，也不与前台可见图抢同一把锁。
 *
 * @property settleMillis 拿到许可后要求的"前台静默"时长，0 表示只要出现空闲瞬间就放行。
 * @property permits 车道内允许同时执行的加载数，用于限制后台对带宽的占用比例。
 */
internal enum class BackgroundLane(val settleMillis: Long, val permits: Int) {
    /**
     * 紧贴可见区的下一行：不等完整的静默窗口，只占 1 路，保证滚动中也能推进。
     *
     * 注意"不等静默"不等于"完全不等"：`settleMillis = 0` 仍要求出现**瞬时空闲**
     * （在飞前台请求数瞬时归零），只是不再要求这段空闲持续满若干毫秒。保留这一瞬
     * 是为了不与可见图同时压满同 host 的连接池；真正的优先级前提是"可见图先走"。
     */
    NEAR(settleMillis = 0L, permits = 1),
    /** 静止后铺开的机会性预取：等 220ms 静默，2 路并发。 */
    FAR(settleMillis = 220L, permits = 2),
}

/**
 * 图片加载的优先级闸门：前台可见图优先，后台预取只在"让出"之后开跑。
 *
 * 设计要点（与旧实现的差别）：
 * 1. 后台车道有独立许可，[BackgroundLane.NEAR] 与 [BackgroundLane.FAR] 互不阻塞，
 *    也不与前台共用一把锁；
 * 2. 后台任务只在**开始之前**让出（等前台静默），**一旦 [load] 开始就绝不取消**；
 * 3. 取色等本地工作走 [backgroundLocalWork]，不占网络许可。
 *
 * 关于"开始后不取消"的理由：后台加载最终走的是 OkHttp / Coil 的真实请求，请求已经在飞、
 * 字节已经在收。此时若因为新的前台请求到达而取消，代价是已下载字节全部丢弃，并且这一轮
 * 预取 pass 会从队首重新排一遍——而新的前台请求往往在几百毫秒后又会再次打断它。旧实现用
 * `transformLatest` 做的正是这种抢占，结果是可见封面源源不断时预取**永远走不出队首**，
 * 等于被饿死。"开始前让出、开始后跑完"既保留了前台优先（前台到达时后台不会新开任务），
 * 又保证后台一旦有进展就能线性推进直至完成。
 */
internal class ImageLoadPriority {
    private data class ForegroundActivity(val active: Int = 0, val generation: Long = 0)

    private val foregroundRequests = MutableStateFlow(ForegroundActivity())

    /** 车道 -> 许可。枚举常量数量固定，构造期一次性建表即可，无需并发容器。 */
    private val lanePermits: Map<BackgroundLane, Semaphore> =
        BackgroundLane.entries.associateWith { lane -> Semaphore(lane.permits) }

    /**
     * 前台可见图专用。除了维护在飞计数之外不做任何节流，语义与旧实现保持一致。
     *
     * 计数在 `finally` 中回退，保证协程被取消（例如 Compose 离开组合）时不会把
     * [foregroundRequests] 永久卡在非零值上，否则后台车道将永远等不到静默。
     */
    suspend fun <T> foreground(load: suspend () -> T): T {
        foregroundRequests.update { it.copy(active = it.active + 1, generation = it.generation + 1) }
        return try {
            load()
        } finally {
            foregroundRequests.update { it.copy(active = it.active - 1) }
        }
    }

    /**
     * 在指定 [lane] 上执行后台加载。
     *
     * 执行顺序被刻意固定为：**先拿车道许可 → 等前台静默 → 执行 [load]**。
     * 先拿许可意味着同一车道最多只有 `permits` 个任务在"等待静默"，避免前台一空闲
     * 就有几十个预取同时涌出把带宽再一次占满；拿到许可之后不再检查任何取消条件。
     *
     * 注意：`lane.settleMillis` 的等待发生在持有许可期间，因此它是车道级的背压而不是全局阻塞；
     * 且等待只受 [foreground] 计数驱动，不依赖任何其他锁，不存在锁序反转。
     */
    suspend fun <T> background(lane: BackgroundLane, load: suspend () -> T): T =
        laneSemaphore(lane).withPermit {
            awaitForegroundQuiet(lane.settleMillis)
            load()
        }

    /**
     * 本地后台工作（取色、采样等）：**不占网络许可**，避免本地 CPU 任务和图片下载互抢车道，
     * 但同样遵守"开始前让出"的约定。
     *
     * 默认 [settleMillis] 为 0，即等到第一个前台空闲瞬间就开始；取色通常跟在图片可见之后，
     * 不需要额外的静默惩罚。
     */
    suspend fun <T> backgroundLocalWork(
        settleMillis: Long = 0L,
        load: suspend () -> T,
    ): T {
        awaitForegroundQuiet(settleMillis)
        return load()
    }

    /**
     * 兼容壳：旧调用点（`HomeFeedCoverPreload`、`DynamicSidebar`）仍按旧签名调用。
     *
     * 内部转调 [background] 的 [BackgroundLane.FAR] 车道，因此 **[settleMillis] 参数不再生效**，
     * 静默窗口由 [BackgroundLane.FAR] 的 220ms 决定。新代码请直接使用
     * `background(BackgroundLane.NEAR) { ... }` 或 `background(BackgroundLane.FAR) { ... }`
     * 以便按距离可见区的远近区分优先级。
     */
    @Deprecated("改用 background(BackgroundLane.FAR, load)")
    suspend fun <T> prefetch(settleMillis: Long = 360L, load: suspend () -> T): T =
        // 参数保留以兼容旧调用签名；实际静默时长由 BackgroundLane.FAR 决定。
        background(BackgroundLane.FAR, load)

    private fun laneSemaphore(lane: BackgroundLane): Semaphore =
        requireNotNull(lanePermits[lane]) { "未注册的后台车道: $lane" }

    /**
     * 等到前台空闲，并在 [settleMillis] > 0 时要求这段空闲持续满该时长。
     *
     * [settleMillis] <= 0 时只要出现第一个空闲瞬间就返回（滚动中的 NEAR 车道用这个分支，
     * 它要的是"别和可见图同时开跑"，而不是"等用户停下"）。[settleMillis] > 0 时若在
     * 静默期内又有前台请求到达，则回到"等待空闲"重新计时，直到完整撑过一个静默窗口。
     */
    private suspend fun awaitForegroundQuiet(settleMillis: Long) {
        while (true) {
            val idle = foregroundRequests.first { it.active == 0 }
            if (settleMillis <= 0L) return
            // 即使短请求在等待期间已经结束，也必须重新等待完整静默窗口。
            val interrupted = withTimeoutOrNull(settleMillis) {
                foregroundRequests.first { it != idle }
            }
            if (interrupted == null) return
        }
    }
}
