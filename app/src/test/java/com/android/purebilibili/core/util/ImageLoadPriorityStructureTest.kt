package com.android.purebilibili.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 源码护栏：防止 [ImageLoadPriority] 退回"全局单许可 + 前台抢占"的老实现。
 *
 * 这些断言读源码而不是跑协程，因为并发时序很难在单测里稳定复现；
 * 而本次回归的根因恰恰是"某个 API 被重新引入"，静态检查足够拦住它。
 *
 * 禁止项（`transformLatest` / `Semaphore(1)` / `foregroundRequests.map {`）在**去注释后的代码**
 * 上断言，原因见 [code]；允许项在原文上断言即可（代码与 KDoc 都允许提及这些名字）。
 */
class ImageLoadPriorityStructureTest {

    @Test
    fun exposesLaneBasedBackgroundEntryPoints() {
        val source = source()

        // 双通道的前提：车道枚举存在，NEAR / FAR 各自带静默时长与许可数。
        assertTrue(source.contains("BackgroundLane"), "缺少车道枚举，后台任务无法区分远近优先级")
        assertTrue(source.contains("NEAR("), "缺少 NEAR 车道，紧贴可见区的预取将没有独立通道")
        assertTrue(source.contains("FAR("), "缺少 FAR 车道，静止后的机会性预取将没有独立通道")
        assertTrue(source.contains("background("), "缺少 background(lane, load) 入口，调用方无法按车道调度")
        // 取色/本地采样必须有不占网络许可的入口，否则会和下载互抢带宽。
        assertTrue(source.contains("backgroundLocalWork("), "缺少 backgroundLocalWork，本地工作只能去抢网络许可")
    }

    @Test
    fun keepsPrefetchAsDeprecatedCompatibilityShell() {
        val source = source()

        // HomeFeedCoverPreload / DynamicSidebar 仍在调用 prefetch，删掉会编译失败。
        assertTrue(source.contains("prefetch("), "兼容壳 prefetch 被删除，旧调用点会编译失败")
        // 标 Deprecated 才能让新代码走向 background(lane)，同时不打断 T03 的接线节奏。
        assertTrue(source.contains("@Deprecated"), "prefetch 未标记 @Deprecated，调用方没有迁移提示")
    }

    @Test
    fun forbidsForegroundPreemptionOfRunningBackgroundLoad() {
        val code = code()

        // transformLatest 会取消已启动的 load，丢掉已下字节并从队首重排 —— 饿死的直接原因。
        assertFalse(code.contains("transformLatest"), "transformLatest 回归：前台到达会取消已开始的预取")
        // 映射成"空闲布尔"再 emit 的老写法同理，把"等静默"和"执行"塞进同一个 flow 变换里。
        assertFalse(code.contains("foregroundRequests.map {"), "foregroundRequests.map 回归：静默判断被折叠进可取消的 flow 变换")
    }

    @Test
    fun forbidsSingleGlobalPermit() {
        val code = code()

        // 全局单许可会让预取与取色互相排队，后台只剩一条串行通道。
        assertFalse(code.contains("Semaphore(1)"), "Semaphore(1) 回归：全局单许可会让所有后台任务串行排队")
    }

    @Test
    fun documentsWhyRunningBackgroundLoadIsNeverCancelled() {
        val source = source()

        // KDoc 里的"为什么"是防止后人被上面三个禁止项误导回去的唯一线索，不许当作冗余注释删掉。
        assertTrue(source.contains("开始前让出"), "缺少「开始前让出」的语义说明，后人可能再次引入抢占")
        assertTrue(source.contains("绝不取消"), "缺少「开始后绝不取消」的理由说明，等于没有解释为何删掉抢占逻辑")
    }

    /**
     * 去掉注释后的源码，用于"禁止某 API 出现"这一类断言。
     *
     * 必须去注释：类 KDoc 里**故意**写着旧实现用了 `transformLatest`，并在解释它为何有害。
     * 若直接对原文断言"不含 transformLatest"，就会把这段有价值的说明判成违规，逼着后人
     * 为了过测试去删注释——那正好丢掉了唯一能阻止回归的文档。
     * 已知局限：正则不区分字符串字面量，本文件不含带 `//` 或 `/*` 的字符串，够用。
     */
    private fun code(): String = source()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun source(): String = listOf(
        File("app/src/main/java/com/android/purebilibili/core/util/ImageLoadPriority.kt"),
        File("src/main/java/com/android/purebilibili/core/util/ImageLoadPriority.kt"),
    ).first { it.exists() }.readText()
}
