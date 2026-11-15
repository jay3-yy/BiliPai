package com.android.purebilibili.feature.home.components.cards

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 源码护栏：防止"卡片跟随取色延迟"的两条根因被悄悄改回去。
 *
 * 本次规约的三条不变量（都是静态可检的）：
 * 1. 取色必须有不占网络许可的本地入口（[backgroundLocalWork]），否则滚动中它根本启动不了；
 * 2. 卡片这一侧**不得**再有 180ms 空闲门控，也不得有写死的 350ms 渐入 —— 时长必须走策略函数；
 * 3. 取色缓存要能跨屏命中，并做同 key 在途去重。
 *
 * 禁止项一律在**去注释后的代码**上断言，原因见 [code]：这些文件名/API 名在 KDoc 里被
 * 反复提及（解释为什么删掉），直接对原文断言会把有价值的说明判成违规。
 */
class HomeCardTintTimingStructureTest {

    @Test
    fun warmPathUsesLocalWorkChannelOnly() {
        val code = code(HOME_CARD_TINT_WARM)

        // 预热是纯本地 CPU 活（磁盘解码 96×96 + Palette），走网络车道会和封面下载互抢许可。
        assertTrue(code.contains("backgroundLocalWork("), "取色预热必须走 backgroundLocalWork，不能占网络许可")
        assertTrue(code.contains("VideoCardCoverColorStore.extractColor("), "预热必须复用取色缓存，而不是另起一条提取路径")
        // 预热失败必须静默：它挂在预取 pass 的成功分支里，抛异常会打断整趟预取。
        assertTrue(code.contains("return null"), "预热失败时必须返回 null，不得向预取流程抛异常")
    }

    @Test
    fun coverColorStoreKeepsLargeCacheAndDeduplicates() {
        val code = code(COVER_COLOR_STORE)

        // 128 条在"滑回上一屏"时就开始漏，512 条只多几十 KB。
        assertTrue(code.contains("MAX_CACHE_SIZE = 512"), "取色缓存必须保持 512 条，否则滑回来仍需重算")
        // 预取预热 / 卡片自身 / 返回预热三路会对同一 key 同时聚类。
        assertTrue(code.contains("inFlight"), "缺少 inFlight 去重，同一张封面的取色会被重复计算")
    }

    @Test
    fun cardUsesTintTimingPolicy() {
        val code = code(VIDEO_CARD)

        assertTrue(
            code.contains("resolveCardTintAnimationMillis("),
            "渐入时长必须走 resolveCardTintAnimationMillis，写死 350ms 会把晚到的首色拖成 0.6~1s",
        )
    }

    @Test
    fun cardDropsAuxiliaryWorkGateForTintExtraction() {
        val code = code(VIDEO_CARD)

        // 该 Local 的语义是"非滚动 + 空闲 180ms"，取色挂在它下面等于"滚动中永远不算颜色"。
        assertFalse(
            code.contains("LocalHomeCardAuxiliaryWorkAllowed"),
            "VideoCard 不得再用 LocalHomeCardAuxiliaryWorkAllowed 门控取色：它把取色推迟到滚动停止之后",
        )
        // 写死的 350ms 必须消失，改由策略函数决定（晚到 140 / 变色 350 / 禁止动效 0）。
        assertFalse(
            code.contains("tween(durationMillis = 350)"),
            "渐入时长仍写死 350ms，晚到的首色依旧会慢半拍",
        )
    }

    @Test
    fun timingPolicyStaysFreeOfGraphicsTypes() {
        val code = code(TINT_TIMING_POLICY)

        // 纯函数只吃布尔量：一旦引入 Compose 的 Color 类型（`androidx.compose.ui.graphics.Color`）
        // 就必须进 Robolectric，这条策略也就没法在普通 JVM 单测里被覆盖了。
        // 注意不能用「不含 Color」这种粗断言：参数名 isFirstColorForCard 本身就含这个词。
        assertFalse(
            code.contains("androidx.compose"),
            "取色时长策略必须保持纯函数，不得依赖任何 Compose 类型（含 Color）",
        )
        assertFalse(code.contains("graphics.Color"), "取色时长策略不得引用 Compose 的 Color 类型")
        assertTrue(code.contains("fun resolveCardTintAnimationMillis("), "策略函数签名被改名，调用方会失配")
    }

    /**
     * 去掉注释后的源码，用于"禁止某 API 出现"这一类断言。
     *
     * 必须去注释：这些文件的 KDoc 里**故意**写着被删除的旧 API 名（`LocalHomeCardAuxiliaryWorkAllowed`、
     * 写死的 350ms），并在解释它们为何有害。若直接对原文断言"不含 X"，就会把这段说明判成违规，
     * 逼后人为了过测试去删注释 —— 正好丢掉唯一能阻止回归的文档。
     * 已知局限：正则不区分字符串字面量；被检查的文件没有把注释记号写进字符串，够用。
     */
    private fun code(path: String): String = read(path)
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun read(path: String): String = listOf(
        File("app/src/main/java/$path"),
        File("src/main/java/$path"),
    ).first { it.exists() }.readText()

    private companion object {
        private const val BASE = "com/android/purebilibili/feature/home"
        private const val CARDS = "$BASE/components/cards"
        private const val HOME_CARD_TINT_WARM = "$CARDS/HomeCardTintWarm.kt"
        private const val COVER_COLOR_STORE = "$CARDS/VideoCardCoverColorStore.kt"
        private const val VIDEO_CARD = "$CARDS/VideoCard.kt"
        private const val TINT_TIMING_POLICY = "$CARDS/HomeCardTintTimingPolicy.kt"
    }
}
