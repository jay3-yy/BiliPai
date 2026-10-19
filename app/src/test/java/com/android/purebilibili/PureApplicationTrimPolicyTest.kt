package com.android.purebilibili

import android.content.ComponentCallbacks2
import com.android.purebilibili.app.PureApplicationRuntimeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureApplicationTrimPolicyTest {

    @Test
    fun `ui hidden should leave caches untouched for a fast foreground return`() {
        assertEquals(
            null,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldClearImageMemoryCacheOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldReleaseDerivedCachesOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldReleaseIdleNetworkConnectionsOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
    }

    @Test
    fun `low memory levels should forward trim pressure to image cache`() {
        assertEquals(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            )
        )
        assertEquals(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            )
        )
        assertEquals(
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            )
        )
    }

    @Test
    fun `background pressure should clear image memory cache`() {
        assertEquals(
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            )
        )
        assertEquals(
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_MODERATE
            )
        )
        assertTrue(
            PureApplicationRuntimeConfig.shouldClearImageMemoryCacheOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            )
        )
        assertTrue(
            PureApplicationRuntimeConfig.shouldClearImageMemoryCacheOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_MODERATE
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldClearImageMemoryCacheOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
    }

    @Test
    fun `non pressure trim levels should not touch image cache`() {
        assertEquals(
            null,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
            )
        )
    }

    @Test
    fun `derived caches and idle connections only follow real background pressure`() {
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ).forEach { level ->
            assertFalse(
                "level=$level",
                PureApplicationRuntimeConfig.shouldReleaseDerivedCachesOnTrimLevel(level)
            )
            assertFalse(
                "level=$level",
                PureApplicationRuntimeConfig.shouldReleaseIdleNetworkConnectionsOnTrimLevel(level)
            )
        }

        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ).forEach { level ->
            assertTrue(
                "level=$level",
                PureApplicationRuntimeConfig.shouldReleaseDerivedCachesOnTrimLevel(level)
            )
            assertTrue(
                "level=$level",
                PureApplicationRuntimeConfig.shouldReleaseIdleNetworkConnectionsOnTrimLevel(level)
            )
        }
    }

    @Test
    fun `image memory cache percent uses lower background friendly budget`() {
        assertEquals(0.10, PureApplicationRuntimeConfig.resolveImageMemoryCachePercent(), 0.0001)
    }

    @Test
    fun `image cache retains proportional budget on modest heaps`() {
        val heapBytes = 256L * 1024 * 1024
        assertEquals(
            (heapBytes * 0.10).toLong(),
            PureApplicationRuntimeConfig.resolveImageMemoryCacheMaxBytes(256, false)
        )
    }

    @Test
    fun `large heaps cannot grow image cache beyond absolute budget`() {
        assertEquals(
            96L * 1024 * 1024,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheMaxBytes(2048, false)
        )
        assertEquals(
            32L * 1024 * 1024,
            PureApplicationRuntimeConfig.resolveImageMemoryCacheMaxBytes(1024, true)
        )
        assertTrue(PureApplicationRuntimeConfig.resolveImageMemoryCacheMaxBytes(0, true) > 0L)
    }

    @Test
    fun `background memory pressure should notify player heavy optimization`() {
        assertTrue(
            PureApplicationRuntimeConfig.shouldNotifyPlayerHeavyOptimizationOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            )
        )
        assertTrue(
            PureApplicationRuntimeConfig.shouldNotifyPlayerHeavyOptimizationOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldNotifyPlayerHeavyOptimizationOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
        assertFalse(
            PureApplicationRuntimeConfig.shouldNotifyPlayerHeavyOptimizationOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
            )
        )
    }

    @Test
    fun `trim plan matches legacy image and player notify decisions`() {
        val uiHidden = PureApplicationRuntimeConfig.resolveBackgroundMemoryTrimPlan(
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        )
        assertEquals(
            PureApplicationRuntimeConfig.resolveImageMemoryCacheTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            ),
            uiHidden.imageCacheTrimLevel
        )
        assertEquals(
            PureApplicationRuntimeConfig.shouldClearImageMemoryCacheOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            ),
            uiHidden.clearImageMemoryCache
        )
        assertEquals(
            PureApplicationRuntimeConfig.shouldNotifyPlayerHeavyOptimizationOnTrimLevel(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            ),
            uiHidden.notifyPlayerHeavyOptimization
        )

        val complete = PureApplicationRuntimeConfig.resolveBackgroundMemoryTrimPlan(
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        )
        assertTrue(complete.requestIdlePlaybackRelease)
        assertTrue(complete.notifyPlayerHeavyOptimization)
        assertTrue(complete.clearImageMemoryCache)
    }
}
