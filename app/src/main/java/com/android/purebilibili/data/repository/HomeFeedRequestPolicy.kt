package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.HomeFeedMergePolicy
import kotlinx.coroutines.CancellationException

internal enum class HomeFeedSource { WEB, MOBILE, HD }

/** 来源随本次结果一起缓存、消费，不通过全局状态跨请求传递。 */
internal data class HomeFeedPage(
    val videos: List<VideoItem>,
    val sources: Set<HomeFeedSource>,
    val fallbackMessage: String? = null,
) {
    val notice: String?
        get() = fallbackMessage ?: when {
            HomeFeedSource.HD !in sources -> null
            HomeFeedSource.WEB in sources -> "当前推荐来自网页端与平板端，平板端与登录授权匹配"
            else -> "当前使用与登录授权匹配的平板端推荐"
        }
}

/** 首次进入 / 冷启动。 */
internal const val HOME_FEED_FLUSH_DEFAULT = 5
/** 下拉刷新。 */
internal const val HOME_FEED_FLUSH_PULL_DOWN = 6
/** 上滑加载更多。 */
internal const val HOME_FEED_FLUSH_PULL_UP = 8

internal fun homeMobileFeedSource(platform: String): HomeFeedSource =
    if (platform == TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD) HomeFeedSource.HD else HomeFeedSource.MOBILE

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
    mobileSource: HomeFeedSource,
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
        if (mobileVideos.isNotEmpty()) add(mobileSource)
    }
    val fallbackMessage = when {
        mobileVideos.isEmpty() -> "手机端推荐不可用，当前仅使用网页端推荐"
        webVideos.isEmpty() -> if (mobileSource == HomeFeedSource.HD) {
            "网页端推荐不可用，当前仅使用与登录授权匹配的平板端推荐"
        } else "网页端推荐不可用，当前仅使用手机端推荐"
        else -> null
    }
    return Result.success(HomeFeedPage(
        HomeFeedMergePolicy.mergeFeeds(webVideos, mobileVideos), sources, fallbackMessage,
    ))
}
