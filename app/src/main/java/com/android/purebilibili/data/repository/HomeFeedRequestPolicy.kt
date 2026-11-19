package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.network.policy.HD_SIGNED_HOME_FEED_MOBI_APP
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.HomeFeedMergePolicy
import kotlinx.coroutines.CancellationException

internal enum class HomeFeedSource { WEB, MOBILE }

/** 来源随本次结果一起缓存、消费，不通过全局状态跨请求传递。 */
internal data class HomeFeedPage(
    val videos: List<VideoItem>,
    val sources: Set<HomeFeedSource>,
    val notice: String? = null,
)

/** 首次进入 / 冷启动。 */
internal const val HOME_FEED_FLUSH_DEFAULT = 5
/** 下拉刷新。 */
internal const val HOME_FEED_FLUSH_PULL_DOWN = 6
/** 上滑加载更多。 */
internal const val HOME_FEED_FLUSH_PULL_UP = 8

internal fun buildSignedHomeHdCredentialFeedParams(
    pageIndex: Int,
    accessToken: String?,
    timestampSec: Long = AppSignUtils.getTimestamp(),
): Map<String, String> {
    val params = buildMap {
        put("build", "8430300")
        put("c_locale", "zh_CN")
        put("channel", "master")
        put("column", "2")
        put("device", "phone")
        put("device_name", "android")
        put("device_type", "0")
        put("disable_rcmd", "0")
        put("flush", "8")
        put("fnval", "976")
        put("fnver", "0")
        put("force_host", "2")
        put("fourk", "1")
        put("guidance", "1")
        put("https_url_req", "1")
        put("idx", pageIndex.toString())
        put("mobi_app", HD_SIGNED_HOME_FEED_MOBI_APP)
        put("network", "wifi")
        put("platform", "android")
        put("player_net", "1")
        put("pull", (pageIndex == 0).toString())
        put("qn", "32")
        put("recsys_mode", "0")
        put("s_locale", "zh_CN")
        put("splash_id", "")
        put("statistics", """{"appId":1,"platform":3,"version":"8.43.0","abtest":""}""")
        put("ts", timestampSec.toString())
        put("voice_balance", "0")
        if (accessToken != null) put("access_key", accessToken)
    }
    return AppSignUtils.signForAndroidHdLogin(params)
        .mapValues { (_, value) -> AppSignUtils.percentEncode(value) }
}

/** 手机端请求保留独立参数；签名必须使用颁发当前凭证的密钥。 */
internal fun buildSignedHomePhoneFeedParams(
    idx: Int,
    accessToken: String,
    platform: String,
    /** 当前网格列数，服务端据此决定按几列排版和下发多少卡。 */
    columns: Int = 4,
    /** 请求场景：5=首次进入，6=下拉刷新，8=上滑加载更多。 */
    flush: Int = HOME_FEED_FLUSH_DEFAULT,
    timestampSec: Long = AppSignUtils.getTimestamp(),
): Map<String, String> {
    require(accessToken.isNotBlank()) { "缺少移动端登录凭证" }
    require(platform == TokenManager.ACCESS_TOKEN_PLATFORM_TV ||
        platform == TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID) { "当前授权不支持手机端推荐" }
    val android = platform == TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID
    val params = mapOf(
        "idx" to idx.toString(),
        "pull" to if (flush == HOME_FEED_FLUSH_PULL_UP) "false" else "true",
        "column" to columns.toString(),
        "flush" to flush.toString(),
        "autoplay_card" to "11",
        "access_key" to accessToken,
        "appkey" to if (android) AppSignUtils.ANDROID_APP_KEY else AppSignUtils.TV_APP_KEY,
        "ts" to timestampSec.toString(),
        "mobi_app" to "android",
        "device" to "android",
        "build" to "8130300",
    )
    return if (android) AppSignUtils.signForAndroidApi(params) else AppSignUtils.signForTvLogin(params)
}

internal fun mergeHomeFeedResults(
    web: Result<List<VideoItem>>,
    mobile: Result<List<VideoItem>>,
): Result<HomeFeedPage> {
    listOf(web, mobile).forEach { result ->
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
    }
    val webVideos = web.getOrNull().orEmpty()
    val mobileVideos = mobile.getOrNull().orEmpty()
    if (webVideos.isEmpty() && mobileVideos.isEmpty()) {
        return Result.failure(web.exceptionOrNull() ?: mobile.exceptionOrNull()
            ?: IllegalStateException("获取合并推荐流失败"))
    }
    val sources = buildSet {
        if (webVideos.isNotEmpty()) add(HomeFeedSource.WEB)
        if (mobileVideos.isNotEmpty()) add(HomeFeedSource.MOBILE)
    }
    val fallbackMessage = when {
        mobileVideos.isEmpty() -> "手机端推荐不可用，当前仅使用网页端推荐"
        webVideos.isEmpty() -> "网页端推荐不可用，当前仅使用手机端推荐"
        else -> null
    }
    return Result.success(HomeFeedPage(
        HomeFeedMergePolicy.mergeFeeds(webVideos, mobileVideos), sources, fallbackMessage,
    ))
}
