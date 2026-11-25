package com.android.purebilibili.core.network

import android.content.Context
import com.android.purebilibili.core.network.policy.HomeFeedAnonymizerRuntime
import com.android.purebilibili.core.network.policy.resolveHomeFeedCookieAnonymizerDecision
import com.android.purebilibili.core.store.SessionStorageCipher
import com.android.purebilibili.core.store.StoredAccountSession
import com.android.purebilibili.core.store.TokenManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.util.UUID

/** 请求发送时固定会话；迟到的响应只能更新该会话的 Cookie。 */
data class AccountCookieSession(
    val sessData: String,
    val seed: Map<String, String>,
    val explicitCookie: Boolean = false,
) {
    val storageKey: String = MessageDigest.getInstance("SHA-256")
        .digest(sessData.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        fun main(): AccountCookieSession {
            TokenManager.awaitRestore()
            return synchronized(TokenManager) {
                from(
                    TokenManager.sessDataCache.orEmpty(), TokenManager.csrfCache.orEmpty(),
                    TokenManager.midCache ?: 0L, TokenManager.buvid3Cache.orEmpty(),
                )
            }
        }

        fun from(account: StoredAccountSession): AccountCookieSession =
            from(account.sessData, account.csrf, account.mid, account.buvid3)

        fun from(sessData: String, csrf: String, mid: Long, buvid3: String) = AccountCookieSession(
            sessData,
            buildMap {
                if (sessData.isNotBlank()) put("SESSDATA", sessData)
                if (csrf.isNotBlank()) put("bili_jct", csrf)
                if (mid > 0L) put("DedeUserID", mid.toString())
                if (buvid3.isNotBlank()) put("buvid3", buvid3)
            },
        )
    }
}

fun mergeAccountCookies(seed: Map<String, String>, stored: Map<String, String>): Map<String, String> =
    seed + stored

/** 完整 Cookie 集合按会话加密保存，旧账号仅有的指定字段作为首次迁移种子。 */
object AccountCookieStore {
    private const val STORE = "account_cookie_sessions"
    private val cache = mutableMapOf<String, Map<String, String>>()
    private val removedSessions = mutableSetOf<String>()

    @Synchronized
    fun read(context: Context?, session: AccountCookieSession): Map<String, String> {
        if (session.storageKey in removedSessions) return emptyMap()
        cache[session.storageKey]?.let { cached ->
            val merged = mergeAccountCookies(session.seed, cached)
            if (merged != cached) write(context, session, merged)
            return merged
        }
        val raw = context?.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            ?.getString(session.storageKey, null)
        val decoded = raw?.let(SessionStorageCipher::decrypt)
        val stored = decoded?.let {
            runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        }
        // 解密临时失败不覆盖持久数据，也不缓存失败，下一次请求可以重试。
        if (raw != null && stored == null) return session.seed
        val cookies = mergeAccountCookies(session.seed, stored.orEmpty()).toMutableMap()
        cookies.putIfAbsent("buvid3", UUID.randomUUID().toString() + "infoc")
        write(context, session, cookies)
        return cookies.toMap()
    }

    @Synchronized
    fun replace(context: Context?, session: AccountCookieSession, cookies: Map<String, String>) {
        removedSessions.remove(session.storageKey)
        write(context, session, mergeAccountCookies(session.seed, cookies))
    }

    @Synchronized
    fun update(context: Context?, session: AccountCookieSession, cookies: Map<String, String>) {
        if (cookies.isEmpty() || session.storageKey in removedSessions) return
        val previous = read(context, session)
        val updated = previous + cookies
        if (updated != previous) write(context, session, updated)
    }

    @Synchronized
    fun remove(context: Context?, session: AccountCookieSession) {
        removedSessions.add(session.storageKey)
        cache.remove(session.storageKey)
        context?.getSharedPreferences(STORE, Context.MODE_PRIVATE)?.edit()
            ?.remove(session.storageKey)?.apply()
    }

    @Synchronized
    fun cachedCookie(sessData: String?, name: String): String? {
        val session = AccountCookieSession(sessData.orEmpty(), emptyMap())
        return cache[session.storageKey]?.get(name)
    }

    private fun write(context: Context?, session: AccountCookieSession, cookies: Map<String, String>) {
        cache[session.storageKey] = cookies.toMap()
        if (context == null) return
        val encrypted = SessionStorageCipher.encrypt(Json.encodeToString(cookies)) ?: return
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(session.storageKey, encrypted).apply()
    }
}

/** 在 CookieJar 之后写入绑定账号的 Cookie，响应也保存到同一账号。 */
class AccountCookieInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val isBiliHost = url.host == "bilibili.com" || url.host.endsWith(".bilibili.com")
        if (!isBiliHost) return chain.proceed(request)
        val session = request.tag(AccountCookieSession::class.java) ?: AccountCookieSession.main()
        if (session.explicitCookie || request.header(FORCE_COOKIE_HEADER) != null) {
            return chain.proceed(applyForcedCookieHeader(request))
        }
        val anonymous = resolveHomeFeedCookieAnonymizerDecision(
            pluginEnabled = HomeFeedAnonymizerRuntime.enabled,
            host = url.host, encodedPath = url.encodedPath,
        )
        if (anonymous || url.host == "app.bilibili.com") {
            return chain.proceed(request.newBuilder().removeHeader("Cookie").build())
        }
        val cookies = AccountCookieStore.read(NetworkModule.appContext, session)
        val boundRequest = applyWebPlaybackHeaders(
            request.newBuilder()
                .header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                .build(),
            cookies,
        )
        val response = chain.proceed(boundRequest)
        AccountCookieStore.update(
            NetworkModule.appContext, session,
            Cookie.parseAll(url, response.headers).associate { it.name to it.value },
        )
        return response
    }
}

/** 独立播放账号覆盖主账号绑定，显式导入校验请求仍保留自身 Cookie。 */
internal fun bindPlaybackAccountCookieSession(
    request: okhttp3.Request,
    account: StoredAccountSession,
): okhttp3.Request {
    val session = AccountCookieSession.from(account).copy(
        explicitCookie = request.tag(AccountCookieSession::class.java)?.explicitCookie == true,
    )
    return request.newBuilder()
        .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
        .tag(AccountCookieSession::class.java, session)
        .build()
}

/** 播放与设备激活使用同一请求身份，账号头跟随最终发送的 Cookie。 */
internal fun applyWebPlaybackHeaders(request: okhttp3.Request, cookies: Map<String, String>): okhttp3.Request {
    if (request.url.encodedPath !in setOf("/x/player/wbi/playurl", "/x/internal/gaia-gateway/ExClimbWuzhi")) return request
    val builder = request.newBuilder()
        .header("User-Agent", "Dart/3.6 (dart:io)")
        .header("Referer", "https://www.bilibili.com")
        .header("env", "prod")
        .header("app-key", "android64")
        .header("x-bili-aurora-zone", "sh001")
        .removeHeader("Origin")
    val mid = cookies["DedeUserID"]?.takeIf { (it.toLongOrNull() ?: 0L) > 0L }
    if (mid != null) {
        builder.header("x-bili-mid", mid)
            .header("x-bili-aurora-eid", encodeBiliAuroraEid(mid))
    } else {
        builder.removeHeader("x-bili-mid").removeHeader("x-bili-aurora-eid")
    }
    return builder.build()
}

internal fun encodeBiliAuroraEid(mid: String): String {
    val key = "ad1va46a7lza"
    val bytes = mid.toByteArray(Charsets.US_ASCII)
    for (index in bytes.indices) bytes[index] = (bytes[index].toInt() xor key[index % key.length].code).toByte()
    return java.util.Base64.getEncoder().encodeToString(bytes).trimEnd('=')
}
