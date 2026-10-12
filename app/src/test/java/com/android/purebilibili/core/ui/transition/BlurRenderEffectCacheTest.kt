package com.android.purebilibili.core.ui.transition

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlurRenderEffectCacheTest {
    @Test
    fun unchangedRadiusRetainsTheCurrentEffect() {
        val cache = BlurRenderEffectCache()
        val effect = cache.resolve(8f)
        assertNotNull(effect)
        repeat(20) { assertSame(effect, cache.resolve(8f)) }
    }

    @Test
    fun radiusChangesReplaceTheSingleRetainedEffect() {
        val cache = BlurRenderEffectCache()
        val first = cache.resolve(8f)
        val second = cache.resolve(16f)
        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
        assertSame(second, cache.resolve(16f))

        val returned = cache.resolve(8f)
        assertNotNull(returned)
        assertNotSame(first, returned)
        assertNotSame(second, returned)
        assertSame(returned, cache.resolve(8f))
    }

    @Test
    fun disabledAndInvalidRadiiDoNotExposeThePreviousEffect() {
        val cache = BlurRenderEffectCache()
        val effect = cache.resolve(8f)
        assertNotNull(effect)
        for (radius in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(cache.resolve(radius))
        }
        assertSame(effect, cache.resolve(8f))
    }

    @Test
    @Config(sdk = [30])
    fun androidBeforeRenderEffectSupportReturnsNoEffect() {
        val cache = BlurRenderEffectCache()
        assertNull(cache.resolve(8f))
        assertNull(cache.resolve(16f))
    }
}
