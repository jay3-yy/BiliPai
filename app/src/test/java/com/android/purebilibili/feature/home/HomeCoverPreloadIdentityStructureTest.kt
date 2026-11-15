package com.android.purebilibili.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 跨文件护栏：锁死「显示 / 预取 / 取色三处必须共用同一套请求身份」这条不变式。
 *
 * 跨文件断言用于检查预取请求与可见卡片请求的身份关系，它可以发现
 * "卡片那边自己拼了一份 URL"或"取色那边换了一个磁盘条目"——而这两件事的后果完全相同且极难
 * 靠肉眼 review 发现：预取确实在跑、日志里也有命中记录，但用户滑到卡片上仍然要转圈，
 * 因为 Coil 认为那是**另一个**请求身份，内存 / 磁盘缓存都命不中。
 *
 * 身份由三处共同承担：
 * 1. 唯一来源：[resolveHomeCoverImageSource]（`HomeCoverRequestPolicy.kt`）与它内部使用的
 *    [resolveVideoCardCoverCacheKey] —— url 与 cacheKey 必须由同一处一起产出，否则 url 换了
 *    而 cacheKey 没换，缓存会串图。
 * 2. 显示端：卡片必须调用工厂，不许自己 `video.pic` + 拼尺寸。
 * 3. 预取端：六个请求字段逐字对齐显示端；取色端：复用同一个磁盘条目。
 *
 * 禁止项在**去注释后的代码**上断言，原因见 [code]：本文件的 KDoc 与源码 KDoc 会故意提到
 * 被禁止的写法并解释它为何有害，对原文断言等于要求后人删掉那段解释，而那正是唯一能防回归的文档。
 */
class HomeCoverPreloadIdentityStructureTest {

    @Test
    fun policyRemainsTheSingleSourceOfRequestIdentity() {
        val code = code(REQUEST_POLICY)

        // 工厂是 url 与 cacheKey 的唯一产出点；两个都断言，避免有人只删掉其中一个的产出逻辑
        // 而把另一个挪回调用方，那会让 url / cacheKey 出现两个真相来源。
        assertTrue(
            code.contains("internal fun resolveHomeCoverImageSource("),
            "身份工厂 resolveHomeCoverImageSource( 被删除或降级为非 internal：预取端和显示端将各自拼 URL",
        )
        assertTrue(
            code.contains("resolveVideoCardCoverCacheKey("),
            "工厂内部不再复用 resolveVideoCardCoverCacheKey：cacheKey 与卡片自己算的可能不一致，缓存会串图",
        )
    }

    @Test
    fun visibleCardGoesThroughTheSameFactory() {
        val code = code(VIDEO_CARD)

        // 显示端是"命中与否"的基准：如果卡片自己拼 URL 或自己算 cacheKey，预取再精确也白费。
        assertTrue(
            code.contains("resolveHomeCoverImageSource("),
            "VideoCard.kt 不再调用身份工厂：卡片自行拼出的 URL / cacheKey 与预取端不同，滑到必然重新解码",
        )
    }

    @Test
    fun preloadMirrorsEveryRequestFieldOfVisibleCard() {
        val code = code(HOME_FEED_COVER_PRELOAD)

        // 这六个字段共同构成 Coil 的请求身份，缺任何一个都会让预取解出的位图命不中内存缓存，
        // 外部表现就是"预取了但滑过去还是要转圈"。
        assertTrue(
            code.contains("resolveHomeCoverImageSource("),
            "预取端绕开了身份工厂：自己拼的 URL / cacheKey 与卡片不一致，预取结果永远命不中",
        )
        assertTrue(
            code.contains(".data(source.url)"),
            "预取请求的 data 不取自 source.url：url 与 cacheKey 可能来自两个不同的计算路径",
        )
        assertTrue(
            code.contains("size(requestSpec.widthPx, requestSpec.heightPx)"),
            "缺少 size(requestSpec.widthPx, requestSpec.heightPx)：解码尺寸与卡片不一致，Coil 会重新采样而放弃缓存命中",
        )
        assertTrue(
            code.contains("homeCoverDecodePolicy()"),
            "缺少 homeCoverDecodePolicy()：位图格式 / 色彩空间与卡片不同，缓存条目无法复用",
        )
        assertTrue(
            code.contains("memoryCacheKey(source.cacheKey)"),
            "缺少 memoryCacheKey(source.cacheKey)：Coil 会改按请求参数哈希，预取结果不落进卡片查询的那个内存条目",
        )
        assertTrue(
            code.contains("diskCacheKey(source.cacheKey)"),
            "缺少 diskCacheKey(source.cacheKey)：预取与可见请求落到不同磁盘条目，等于下载了两次",
        )
    }

    @Test
    fun colorExtractionReusesTheSameDiskEntry() {
        val code = code(COVER_COLOR_STORE)

        // 取色端复用 cacheKey 对应的磁盘条目，是"零二次网络"的唯一前提：
        // 若这里改回按 URL 哈希，每张封面的取色都会再发一次请求或再解一次码。
        assertTrue(
            code.contains("diskCacheKey(cacheKey)"),
            "取色请求不再复用 diskCacheKey(cacheKey)：封面会被二次下载或二次解码，取色延迟与流量同时劣化",
        )
    }

    @Test
    fun colorSamplingSizeStaysExplicitlyDecided() {
        val code = code(COVER_COLOR_STORE)

        // 采样尺寸直接决定 Palette 输入的像素分布，改大改小都会让代表色漂移（同一张图算出不同颜色）。
        // 这类改动必须是一次显式决策 + 视觉验收，因此用断言把它挡住，而不是让它悄无声息地改掉。
        assertTrue(
            code.contains(".size(96, 96)"),
            "取色采样尺寸不再是 96x96：Palette 结果会漂移，颜色与已缓存的旧条目不一致（属于需显式决策的改动）",
        )
    }

    @Test
    fun preloadNeverBuildsItsOwnCoverUrl() {
        val code = code(HOME_FEED_COVER_PRELOAD)

        // 反向断言：只要预取文件里重新出现 URL 拼接或直接读 video.pic，就说明有人绕开了身份工厂。
        // 这种写法能编译、能跑、日志也正常，只有缓存命中率会静默归零 —— 必须静态拦住。
        assertFalse(
            code.contains("FormatUtils.buildSizedImageUrl("),
            "预取端直接调用 FormatUtils.buildSizedImageUrl(：绕开身份工厂自拼 URL，cacheKey 将与之脱节",
        )
        assertFalse(
            code.contains("video.pic"),
            "预取端直接读取 video.pic：说明它没走 resolveHomeCoverImageSource，低画质策略与尺寸分桶都会被跳过",
        )
    }

    /**
     * 去掉注释后的源码，用于"禁止某 API 出现"这一类断言。
     *
     * 必须去注释：源码 KDoc 会**故意**提到 `video.pic` 这类被禁止的写法并解释它为何有害
     * （见 [HomeFeedCoverPreload] 的六个字段说明）。若直接对原文断言"不含 video.pic"，
     * 就会把这段有价值的说明判成违规，逼着后人为了过测试去删注释——那正好丢掉了唯一能
     * 阻止回归的文档。
     * 已知局限：正则不区分字符串字面量；被检查的文件没有把注释记号写进字符串，够用。
     */
    private fun code(relativePath: String): String = source(relativePath)
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * 读取 `app/` 模块下的源码。单测的工作目录随执行方式变化（有时是仓库根，有时是模块根），
     * 因此两个候选路径都试一遍；都不存在时由 `first` 抛出，暴露成"断言失效"而不是静默通过。
     */
    private fun source(relativePath: String): String = listOf(
        File("app/src/main/java/com/android/purebilibili/$relativePath"),
        File("src/main/java/com/android/purebilibili/$relativePath"),
    ).first { it.exists() }.readText()

    private companion object {
        const val REQUEST_POLICY = "feature/home/HomeCoverRequestPolicy.kt"
        const val VIDEO_CARD = "feature/home/components/cards/VideoCard.kt"
        const val HOME_FEED_COVER_PRELOAD = "feature/home/HomeFeedCoverPreload.kt"
        const val COVER_COLOR_STORE = "feature/home/components/cards/VideoCardCoverColorStore.kt"
    }
}
