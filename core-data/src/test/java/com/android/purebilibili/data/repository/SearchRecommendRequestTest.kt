package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.TokenManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SearchRecommendRequestTest {
    @Test
    fun `phone login token is included in the HD signature`() {
        val params = buildSearchRecommendParams(
            accessToken = "android-session",
            accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID,
            timestampSeconds = 1700000000L
        )

        assertEquals("android-session", params["access_key"])
        assertEquals("dfca71928277209b", params["appkey"])
        assertEquals("f379c4b24903e76ecd679aa5a7368916", params["sign"])
    }

    @Test
    fun `TV login token retains its issuing application signature`() {
        val params = buildSearchRecommendParams(
            accessToken = "tv-session",
            accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
            timestampSeconds = 1700000000L
        )

        assertEquals("tv-session", params["access_key"])
        assertEquals("4409e2ce8ffd12b8", params["appkey"])
        assertEquals("dc0127b5af38129a0ea00d8d111a9cf5", params["sign"])
    }

    @Test
    fun `Cookie-only login does not send an empty access key or select TV signing`() {
        val params = buildSearchRecommendParams(
            accessToken = "   ",
            accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
            timestampSeconds = 1700000000L
        )

        assertFalse(params.containsKey("access_key"))
        assertEquals("dfca71928277209b", params["appkey"])
        assertEquals("870ae289c25a99a77c6ed3c8aa4ee39e", params["sign"])
    }
}
