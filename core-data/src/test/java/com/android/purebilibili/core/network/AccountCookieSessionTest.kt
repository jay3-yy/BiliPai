package com.android.purebilibili.core.network

import com.android.purebilibili.core.store.StoredAccountSession
import java.lang.reflect.Proxy
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountCookieSessionTest {
    private fun session(mid: Long) = AccountCookieSession.from(
        "session-${UUID.randomUUID()}", "csrf-$mid", mid, "device-$mid"
    )

    @Test
    fun `all cookies survive reloads within a session and rotations win over old seed`() {
        val account = session(1)
        AccountCookieStore.replace(null, account, account.seed + mapOf("DedeUserID__ckMd5" to "check", "sid" to "sid"))
        AccountCookieStore.update(null, account, mapOf("SESSDATA" to "rotated", "bili_jct" to "new-csrf"))
        val restored = AccountCookieStore.read(null, account)
        assertEquals("check", restored["DedeUserID__ckMd5"])
        assertEquals("sid", restored["sid"])
        assertEquals("rotated", restored["SESSDATA"])
        assertEquals("new-csrf", restored["bili_jct"])
        assertEquals("device-1", restored["buvid3"])
    }

    @Test
    fun `late response updates the bound account and does not contaminate another account`() {
        val first = session(1)
        val second = session(2)
        AccountCookieStore.replace(null, first, first.seed)
        AccountCookieStore.replace(null, second, second.seed)
        val request = Request.Builder().url("https://api.bilibili.com/x/player/wbi/playurl")
            .tag(AccountCookieSession::class.java, first).build()
        intercept(request) { sent ->
            assertTrue(sent.header("Cookie").orEmpty().contains("SESSDATA=${first.sessData}"))
            assertEquals("1", sent.header("x-bili-mid"))
            // 另一个账号已经在使用；迟到的首账号响应仍必须写回首账号。
            AccountCookieStore.update(null, second, mapOf("sid" to "second"))
            response(sent, "sid=first-response; Domain=.bilibili.com; Path=/")
        }.close()
        assertEquals("first-response", AccountCookieStore.read(null, first)["sid"])
        assertEquals("second", AccountCookieStore.read(null, second)["sid"])
        assertNotEquals(first.storageKey, second.storageKey)
        assertFalse(first.storageKey.contains(first.sessData))
    }

    @Test
    fun `VIP playback account overrides main cookie binding without changing main session`() {
        val main = session(10)
        val vipAccount = StoredAccountSession(
            mid = 20L, sessData = "vip-${UUID.randomUUID()}", csrf = "vip-csrf",
            buvid3 = "vip-device", isVip = true, accessToken = "",
        )
        AccountCookieStore.replace(null, main, main.seed)
        val original = Request.Builder().url("https://api.bilibili.com/x/player/wbi/playurl")
            .tag(AccountCookieSession::class.java, main).build()
        val playback = bindPlaybackAccountCookieSession(original, vipAccount)
        assertTrue(playback.cacheControl.noCache)
        val bound = playback.tag(AccountCookieSession::class.java)!!
        assertEquals(vipAccount.sessData, bound.sessData)
        assertTrue(vipAccount.isVip)
        intercept(playback) { sent ->
            val cookies = sent.header("Cookie").orEmpty()
            assertTrue(cookies.contains("SESSDATA=${vipAccount.sessData}"))
            assertTrue(cookies.contains("bili_jct=vip-csrf"))
            assertFalse(cookies.contains(main.sessData))
            assertEquals("20", sent.header("x-bili-mid"))
            response(sent, "sid=vip-response; Path=/")
        }.close()
        assertEquals(main.sessData, AccountCookieStore.read(null, main)["SESSDATA"])
        assertEquals("10", AccountCookieStore.read(null, main)["DedeUserID"])
        assertEquals(null, AccountCookieStore.read(null, main)["sid"])
        assertEquals("vip-response", AccountCookieStore.read(null, bound)["sid"])
    }

    @Test
    fun `removed account rejects late cookie updates until explicitly restored`() {
        val account = session(30)
        AccountCookieStore.replace(null, account, account.seed)
        assertEquals("csrf-30", AccountCookieStore.cachedCookie(account.sessData, "bili_jct"))
        AccountCookieStore.remove(null, account)
        AccountCookieStore.update(null, account, mapOf("sid" to "late-response"))
        assertTrue(AccountCookieStore.read(null, account).isEmpty())
        assertEquals(null, AccountCookieStore.cachedCookie(account.sessData, "bili_jct"))
        AccountCookieStore.replace(null, account, account.seed)
        assertEquals(account.sessData, AccountCookieStore.read(null, account)["SESSDATA"])
        assertEquals(null, AccountCookieStore.read(null, account)["sid"])
    }

    @Test
    fun `explicit validation cookies are sent unchanged and do not update the bound account`() {
        val account = session(3)
        val request = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav")
            .header(FORCE_COOKIE_HEADER, "SESSDATA=imported")
            .tag(AccountCookieSession::class.java, account).build()
        intercept(request) { sent ->
            assertEquals("SESSDATA=imported", sent.header("Cookie"))
            assertEquals(null, sent.header(FORCE_COOKIE_HEADER))
            response(sent, "sid=import-only; Path=/")
        }.close()
        assertEquals(null, AccountCookieStore.read(null, account)["sid"])
    }

    @Test
    fun `web playback headers match the final cookie account and guests clear account headers`() {
        val request = Request.Builder().url("https://api.bilibili.com/x/player/wbi/playurl")
            .header("Origin", "https://old.example").header("x-bili-mid", "999").build()
        val loggedIn = applyWebPlaybackHeaders(request, mapOf("DedeUserID" to "123"))
        assertEquals("123", loggedIn.header("x-bili-mid"))
        assertEquals("Dart/3.6 (dart:io)", loggedIn.header("User-Agent"))
        assertEquals("https://www.bilibili.com", loggedIn.header("Referer"))
        assertEquals(null, loggedIn.header("Origin"))
        val guest = applyWebPlaybackHeaders(loggedIn, emptyMap())
        assertEquals(null, guest.header("x-bili-mid"))
        assertEquals(null, guest.header("x-bili-aurora-eid"))
    }

    @Test
    fun `third party redirects never receive the session cookie collection`() {
        val request = Request.Builder().url("https://cdn.example/video")
            .tag(AccountCookieSession::class.java, session(4)).build()
        intercept(request) { sent ->
            assertEquals(null, sent.header("Cookie"))
            response(sent)
        }.close()
    }

    @Test
    fun `expired or blank set-cookie response does not wipe stored bili_jct`() {
        val account = session(31)
        AccountCookieStore.replace(null, account, account.seed)
        val request = Request.Builder().url("https://api.bilibili.com/x/v3/fav/folder/add")
            .tag(AccountCookieSession::class.java, account).build()
        intercept(request) { sent ->
            response(sent, "bili_jct=; Max-Age=0; Domain=.bilibili.com; Path=/")
        }.close()
        assertEquals("csrf-31", AccountCookieStore.read(null, account)["bili_jct"])
        assertEquals("csrf-31", AccountCookieStore.cachedCookie(account.sessData, "bili_jct"))
    }

    @Test
    fun `grpc credential snapshot rejects mismatched expected sender mid`() {
        assertFailsWith<IllegalStateException> {
            com.android.purebilibili.core.network.grpc.BiliGrpcClient.snapshotAccountCredentials(
                includeAccount = true,
                expectedAccountMid = 999_999_999L,
            )
        }
    }

    private fun response(request: Request, cookie: String? = null): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("".toResponseBody()).apply { if (cookie != null) header("Set-Cookie", cookie) }.build()

    private fun intercept(request: Request, proceed: (Request) -> Response): Response {
        val chain = Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader, arrayOf(Interceptor.Chain::class.java)
        ) { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> proceed(args!![0] as Request)
                else -> error("Unexpected chain method: ${method.name}")
            }
        } as Interceptor.Chain
        return AccountCookieInterceptor().intercept(chain)
    }
}
