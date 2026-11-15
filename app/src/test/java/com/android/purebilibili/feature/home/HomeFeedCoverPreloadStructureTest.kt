package com.android.purebilibili.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 源码护栏：防止首页封面预取泵退回"滚动中零预取 + collectLatest 取消在途 + 逐帧重算"的老实现。
 *
 * 这些断言读源码而不是跑 Compose，因为本次回归是**结构性**的：问题不在某个算式，而在
 * "效果 key 里放了每帧都变的状态"和"用了会取消在途请求的 flow 操作符"。只要这两个写法回来，
 * 无论窗口算得多准，用户看到的仍然是"滑到才加载"。
 *
 * 禁止项在**去注释后的代码**上断言，原因见 [code]：KDoc 里故意写着旧实现用了什么，
 * 直接对原文断言会把这些解释判成违规，逼着后人删注释——那正好丢掉唯一能防回归的文档。
 */
class HomeFeedCoverPreloadStructureTest {

    @Test
    fun forbidsZeroPrefetchWhileScrolling() {
        val code = code()

        // 旧写法：滚动中恒发 null，等于滚动期间完全不预取——这就是"滑到才加载"的直接原因。
        assertFalse(code.contains("if (isScrolling.value) null"), "滚动中恒发 null 回归：滚动期间又变成零预取")
    }

    @Test
    fun forbidsCancellingInFlightPrefetchOnWindowChange() {
        val code = code()

        // collectLatest 会在新窗口到达时取消上一轮 pass，已下载字节全部作废。
        assertFalse(code.contains("collectLatest"), "collectLatest 回归：窗口更新会取消在途预取")
        // 老的兼容壳内部转调 FAR 车道并让 settleMillis 参数失效，新代码必须直接按车道调度。
        assertFalse(code.contains("AppImageLoadPriority.prefetch("), "又用回 prefetch( 兼容壳，无法区分 NEAR / FAR")
        assertFalse(code.contains("HOME_COVER_PRELOAD_SETTLE_MS"), "旧静默常量回归：静默时长应只由 BackgroundLane 决定")
    }

    @Test
    fun keepsLaunchedEffectKeysToStableIdentity() {
        val code = code()

        // key 只允许放"换掉就该整体重来"的身份；滚动相关状态进 key 会导致滚动时反复重建效果。
        val keyBlock = code.substringAfter("LaunchedEffect(", "").substringBefore(") {", "")
        assertTrue(keyBlock.isNotBlank(), "未能定位 LaunchedEffect 的 key 列表，断言失效")
        assertFalse(
            keyBlock.contains("visibleVideoRange"),
            "visibleVideoRange 进入了 LaunchedEffect key：滚动时会不断取消并重建整条预取泵",
        )
        assertFalse(
            keyBlock.contains("isScrolling"),
            "isScrolling 进入了 LaunchedEffect key：滚动开始 / 结束都会重建整条预取泵",
        )
        assertFalse(
            keyBlock.contains("gridState"),
            "gridState 进入了 LaunchedEffect key：布局切换会重建泵，而泵本来就能自行采样新状态",
        )
    }

    @Test
    fun samplesRowsInsteadOfScrollPixels() {
        val code = code()

        // 读 offset 就会每帧产生新样本，预取退化成逐帧重算；采样必须只读索引。
        assertFalse(
            code.contains("firstVisibleItemScrollOffset"),
            "读 firstVisibleItemScrollOffset 说明采样退化成逐帧，滑一次会重算上百次窗口",
        )
        assertTrue(code.contains(".distinctUntilChanged()"), "缺少 distinctUntilChanged，重复样本会反复触发窗口计算")
        assertTrue(code.contains("scrollSample()"), "缺少行级采样调用，窗口无法跟随视口")
        assertTrue(code.contains("resolveHomeScrollDirection("), "缺少方向推导调用，反向滚动会朝错误方向预取")
        assertTrue(
            code.contains("resolveHomeCoverPreloadWindow("),
            "缺少窗口策略调用，预取范围又回到手写算式",
        )
    }

    @Test
    fun keepsTwoLaneBackgroundScheduling() {
        val code = code()

        assertTrue(code.contains("BackgroundLane.NEAR"), "缺少 NEAR 车道，滚动中无法推进预取")
        assertTrue(code.contains("BackgroundLane.FAR"), "缺少 FAR 车道，静止后的机会性预取没有通道")
        assertTrue(code.contains("PrefetchLedger"), "缺少预取去重账本，窗口抖动会变成重复请求")
        // 消费者必须是 collect（每轮都跑完），不能是会取消上一轮的变体。
        assertTrue(code.contains(".collect {"), "消费者必须用 collect 消费窗口")
    }

    @Test
    fun keepsRequestIdentityIdenticalToVisibleCards() {
        val code = code()

        // 这六个字段决定了预取出的位图能否被可见请求命中；缺一个都会表现为"预取了还是要转圈"。
        assertTrue(code.contains("size(requestSpec.widthPx"), "缺少尺寸声明，预取与可见请求的解码尺寸不一致")
        assertTrue(code.contains("homeCoverDecodePolicy()"), "缺少封面解码策略，位图格式与卡片不一致")
        assertTrue(code.contains("memoryCacheKey("), "缺少 memoryCacheKey，预取结果不会命中内存缓存")
        assertTrue(code.contains("diskCacheKey("), "缺少 diskCacheKey，预取与可见请求会落到不同磁盘条目")
        assertTrue(code.contains("backgroundImagePreload()"), "缺少后台预取标记")
    }

    /**
     * 去掉注释后的源码，用于"禁止某 API 出现"这一类断言。
     *
     * 必须去注释：本文件的 KDoc 会**故意**提到 `collectLatest`、`HOME_COVER_PRELOAD_SETTLE_MS`
     * 这些被移除的符号并解释它们为何有害。对原文断言等于要求后人删掉解释，
     * 而那段解释是唯一能阻止下一次回归的东西。
     * 已知局限：正则不区分字符串字面量；被检查的文件没有把注释记号写进字符串，够用。
     */
    private fun code(): String = source()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun source(): String = listOf(
        File("app/src/main/java/com/android/purebilibili/feature/home/HomeFeedCoverPreload.kt"),
        File("src/main/java/com/android/purebilibili/feature/home/HomeFeedCoverPreload.kt"),
    ).first { it.exists() }.readText()
}
