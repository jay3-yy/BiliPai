@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `deferred factory falls back before warm-up and switches to primary once ready`() {
        val fallbackSource = ByteArrayDataSource(byteArrayOf(1))
        val primarySource = ByteArrayDataSource(byteArrayOf(2))
        var primaryReady = false
        var allowPrimary = true
        val factory = DeferredPrimaryDataSourceFactory(
            primaryFactoryProvider = {
                if (primaryReady) DataSource.Factory { primarySource } else null
            },
            fallbackFactory = DataSource.Factory { fallbackSource },
            shouldUsePrimary = { allowPrimary },
        )

        assertSame(fallbackSource, factory.createDataSource())
        primaryReady = true
        assertSame(primarySource, factory.createDataSource())
        allowPrimary = false
        assertSame(fallbackSource, factory.createDataSource())
    }
}
