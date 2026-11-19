package com.android.purebilibili.core.network.policy

private const val APP_FEED_HOST = "app.bilibili.com"
private const val APP_FEED_PATH = "/x/v2/feed/index"

/** 使用 HD 签名的手机推荐请求标识。 */
const val HD_SIGNED_HOME_FEED_MOBI_APP = "android_i"

/** 仅给对应的首页推荐请求注入这套客户端与账号请求头。 */
fun isHdSignedHomeFeedRequest(
    host: String,
    encodedPath: String,
    mobiApp: String?
): Boolean {
    return host == APP_FEED_HOST &&
        encodedPath == APP_FEED_PATH &&
        mobiApp == HD_SIGNED_HOME_FEED_MOBI_APP
}
