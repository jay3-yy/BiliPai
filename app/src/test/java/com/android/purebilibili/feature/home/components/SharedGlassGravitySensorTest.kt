package com.android.purebilibili.feature.home.components

import androidx.compose.runtime.snapshots.Snapshot
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
        assertEquals(285, sensor.state.value * GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES)

        sensor.release(shell)
        sensor.release(Any())
        assertEquals(0, backend.stops)
        backend.callbacks.single()(0.6f, -0.4f)
        assertEquals(288, sensor.state.value * GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES)

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
        val lastDirection = sensor.state.value
        sensor.release(token)
        sensor.acquire(token)
        assertEquals(2, backend.starts)

        backend.callbacks[0](1f, 1f)
        assertEquals(lastDirection, sensor.state.value)
        backend.callbacks[1](-0.6f, 0.4f)
        assertEquals(147, sensor.state.value * GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES)
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
        assertEquals(270, sensor.state.value * GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES)
        sensor.release(token)
        assertEquals(1, backend.stops)
    }

    @Test
    fun `sensor noise only publishes when the visible highlight direction changes`() {
        val backend = FakeBackend()
        val sensor = SharedGlassGravitySensor(backend)
        sensor.acquire(Any())
        val sample = backend.callbacks.single()
        Snapshot.withMutableSnapshot { sample(0.20f, -0.98f) }
        var updates = 0
        val observer = Snapshot.registerApplyObserver { changed, _ ->
            if (sensor.state in changed) updates++
        }
        try {
            repeat(8) {
                Snapshot.withMutableSnapshot { sample(0.22f, -0.97f) }
                Snapshot.withMutableSnapshot { sample(0.20f, -0.98f) }
            }
            assertEquals(0, updates)

            Snapshot.withMutableSnapshot { sample(0.4f, 0.8f) }
            assertEquals(1, updates)
        } finally {
            observer.dispose()
        }
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
