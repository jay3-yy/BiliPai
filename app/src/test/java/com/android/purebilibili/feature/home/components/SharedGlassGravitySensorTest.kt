package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedGlassGravitySensorTest {
    @Test
    fun `visible highlights share subscription until the last consumer leaves`() {
        val backend = FakeBackend()
        val sensor = SharedGlassGravitySensor(backend)
        val shell = Any()
        val indicator = Any()

        sensor.acquire(shell)
        sensor.acquire(shell)
        sensor.acquire(indicator)
        assertEquals(1, backend.starts)
        backend.callbacks.single()(0.2f, -0.8f)
        assertEquals(GlassGravity(0.2f, -0.8f), sensor.state.value)

        sensor.release(shell)
        sensor.release(Any())
        assertEquals(0, backend.stops)
        backend.callbacks.single()(0.6f, -0.4f)
        assertEquals(0.26f, sensor.state.value.gravityX, 0.0001f)
        assertEquals(-0.74f, sensor.state.value.gravityY, 0.0001f)

        sensor.release(indicator)
        assertEquals(1, backend.stops)
        val stopped = sensor.state.value
        backend.callbacks.single()(1f, 1f)
        assertEquals(stopped, sensor.state.value)
    }

    @Test
    fun `restoring visibility starts with current orientation and ignores old queued samples`() {
        val backend = FakeBackend()
        val sensor = SharedGlassGravitySensor(backend)
        val token = Any()
        sensor.acquire(token)
        backend.callbacks[0](0.1f, -0.9f)
        sensor.release(token)
        sensor.acquire(token)
        assertEquals(2, backend.starts)

        backend.callbacks[0](1f, 1f)
        assertEquals(GlassGravity(0.1f, -0.9f), sensor.state.value)
        backend.callbacks[1](-0.6f, 0.4f)
        assertEquals(GlassGravity(-0.6f, 0.4f), sensor.state.value)
        sensor.release(token)
        assertEquals(2, backend.stops)
    }

    @Test
    fun `unavailable sensor leaves highlight direction unchanged`() {
        val backend = FakeBackend(available = false)
        val sensor = SharedGlassGravitySensor(backend)
        val token = Any()
        sensor.acquire(token)
        backend.callbacks.single()(1f, 1f)
        assertEquals(GlassGravity(), sensor.state.value)
        sensor.release(token)
        assertEquals(1, backend.stops)
    }

    private class FakeBackend(private val available: Boolean = true) : GlassGravitySensorBackend {
        var starts = 0
        var stops = 0
        val callbacks = mutableListOf<(Float, Float) -> Unit>()

        override fun start(onSample: (Float, Float) -> Unit): Boolean {
            starts++
            callbacks += onSample
            return available
        }

        override fun stop() { stops++ }
    }
}
