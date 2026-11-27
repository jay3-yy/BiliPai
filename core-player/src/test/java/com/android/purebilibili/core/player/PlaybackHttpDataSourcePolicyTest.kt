package com.android.purebilibili.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackHttpDataSourcePolicyTest {

    @Test
    fun `enables platform HttpEngine on Android 14 plus with S extension 7 and direct network`() {
        assertTrue(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION,
                customProxyEnabled = false,
                trustAllCertificates = false,
                systemHttpProxyActive = false,
            )
        )
    }

    @Test
    fun `falls back to OkHttp on older SDK, missing extension, proxy, or trust-all debug mode`() {
        assertFalse(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT - 1,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION,
                customProxyEnabled = false,
                trustAllCertificates = false,
            )
        )
        assertFalse(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION - 1,
                customProxyEnabled = false,
                trustAllCertificates = false,
            )
        )
        assertFalse(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION,
                customProxyEnabled = true,
                trustAllCertificates = false,
            )
        )
        assertFalse(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION,
                customProxyEnabled = false,
                trustAllCertificates = false,
                systemHttpProxyActive = true,
            )
        )
        assertFalse(
            shouldUsePlatformHttpEngine(
                sdkInt = HTTP_ENGINE_MIN_SDK_INT,
                sExtensionVersion = HTTP_ENGINE_MIN_S_EXTENSION_VERSION,
                customProxyEnabled = false,
                trustAllCertificates = true,
            )
        )
    }

    @Test
    fun `extracts User-Agent case-insensitively while preserving remaining request headers`() {
        val (ua, remaining) = splitHttpEngineHeaders(
            mapOf(
                "user-agent" to "BiliPai-Test/1.0",
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com",
            )
        )
        assertEquals("BiliPai-Test/1.0", ua)
        assertEquals(
            mapOf(
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com",
            ),
            remaining,
        )

        val (emptyUa, emptyRemaining) = splitHttpEngineHeaders(emptyMap())
        assertNull(emptyUa)
        assertTrue(emptyRemaining.isEmpty())
    }
}
