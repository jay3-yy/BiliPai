package com.android.purebilibili.data.repository

import android.content.Context
import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.AccountCookieSession
import com.android.purebilibili.core.network.AccountCookieStore
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.NavData
import com.android.purebilibili.data.model.response.TvPollResponse
import com.android.purebilibili.data.model.response.TvQrCodeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionRepository {
    suspend fun account(): Result<NavData?> = dataRequest {
        val response = NetworkModule.api.getNavInfo()
        if (response.code == -101) return@dataRequest null
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data?.takeIf { it.isLogin }
    }

    suspend fun save(
        context: Context, sessData: String, csrf: String = "", buvid3: String = "",
        mid: Long = 0, accessToken: String = "", refreshToken: String = "",
        accessTokenPlatform: String = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
        cookies: Map<String, String> = emptyMap(),
    ) = withContext(Dispatchers.IO) {
        require(sessData.isNotBlank()) { "登录数据缺少 SESSDATA" }
        val resolvedMid = mid.takeIf { it > 0L } ?: cookies["DedeUserID"]?.toLongOrNull() ?: 0L
        val selectedBuvid = buvid3.ifBlank { cookies["buvid3"].orEmpty() }
            .ifBlank { java.util.UUID.randomUUID().toString() + "infoc" }
        val session = AccountCookieSession.from(sessData, csrf, resolvedMid, selectedBuvid)
        AccountCookieStore.replace(context, session, cookies + session.seed)
        TokenManager.applyStoredSession(
            context = context, sessData = sessData, csrf = csrf, mid = resolvedMid,
            accessToken = accessToken, refreshToken = refreshToken,
            accessTokenPlatform = accessTokenPlatform, buvid3 = selectedBuvid, isVip = false,
            forceEvictHttpCache = true,
        )
    }

    suspend fun signOut(
        context: Context,
        expectedIdentity: AccountSessionIdentity? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        TokenManager.clear(
            context = context,
            expectedIdentity = expectedIdentity,
            shouldClearPlaybackAccountClient = { true },
            onClearedLocked = {
                AccountSessionStore.clearActiveAccount(context, clearNetworkState = false)
                AccountSessionStore.setPlaybackAccountMid(context, null)
            },
        )
    }

    suspend fun completeQrLogin(context: Context, response: TvPollResponse) = withContext(Dispatchers.IO) {
        check(response.code == 0) { "扫码尚未确认" }
        val data = response.data ?: error("登录数据为空")
        val cookies = data.cookieInfo?.cookies.orEmpty().associate { it.name to it.value }
        save(context, cookies["SESSDATA"].orEmpty(), cookies["bili_jct"].orEmpty(),
            cookies["buvid3"].orEmpty(), data.mid, data.accessToken, data.refreshToken,
            accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD, cookies = cookies)
        AccountSessionStore.upsertCurrentAccount(context)
    }
}

/** The polling lifecycle belongs to the caller so refresh/back cancels it immediately. */
object QrLoginRepository {
    suspend fun generate(): TvQrCodeResponse = NetworkModule.passportApi.generateHdQrCode(
        buildHdQrLoginParams(authCode = null)
    )

    suspend fun poll(authCode: String): TvPollResponse = NetworkModule.passportApi.pollHdQrCode(
        buildHdQrLoginParams(authCode)
    )
}

internal fun buildHdQrLoginParams(
    authCode: String?, timestampSeconds: Long = AppSignUtils.getTimestamp()
): Map<String, String> {
    val params = mutableMapOf("local_id" to "0", "ts" to timestampSeconds.toString())
    if (authCode == null) {
        params["platform"] = "android"
        params["mobi_app"] = "android_hd"
    } else {
        params["auth_code"] = authCode
    }
    return AppSignUtils.signForAndroidHdLogin(params)
}
