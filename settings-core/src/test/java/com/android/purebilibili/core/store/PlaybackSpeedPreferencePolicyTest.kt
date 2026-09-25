package com.android.purebilibili.core.store

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSpeedPreferencePolicyTest {

    @Test
    fun `preferred speed should use default when remember-last disabled`() {
        assertEquals(
            1.3f,
            resolvePreferredPlaybackSpeed(
                defaultSpeed = 1.3f,
                rememberLastSpeed = false,
                lastSpeed = 1.8f
            )
        )
    }

    @Test
    fun `preferred speed should use last when remember-last enabled`() {
        assertEquals(
            1.8f,
            resolvePreferredPlaybackSpeed(
                defaultSpeed = 1.3f,
                rememberLastSpeed = true,
                lastSpeed = 1.8f
            )
        )
    }

    @Test
    fun `playback speed should be clamped into supported range`() {
        assertEquals(0.1f, normalizePlaybackSpeed(0.0f))
        assertEquals(8.0f, normalizePlaybackSpeed(9.5f))
    }

    @Test
    fun `legacy playback selections are retained when first creating a shared list`() {
        val options = resolvePlaybackSpeedOptions(
            storedValues = null,
            legacyDefaultSpeed = 1.37f,
            legacyLastSpeed = 1.8f
        )
        assertTrue(options.containsAll(listOf(1f, 1.37f, 1.8f)))
        assertEquals(options.sorted().distinct(), options)
    }

    @Test
    fun `saved list discards invalid entries and never resurrects removed speeds`() {
        assertEquals(
            listOf(1f, 1.25f, 2.5f),
            resolvePlaybackSpeedOptions(
                storedValues = "2.5,1.25,NaN,8.1,1.25",
                legacyDefaultSpeed = 1.37f
            )
        )
        assertEquals(
            listOf(0.1f, 1f, 1.3f),
            normalizePlaybackSpeedOptions(listOf(0.1001f, 1.3f, 1.3f, -1f, Float.NaN))
        )
    }

    @Test
    fun `removing a selected playback speed reassigns it to a remaining option`() {
        val options = listOf(1f, 1.5f, 2f)
        assertEquals(2f, nearestPlaybackSpeed(3f, options))
        assertEquals(1f, nearestPlaybackSpeed(Float.NaN, options))
    }

    @Test
    fun `long-press speed retains non-menu values within slider limits`() {
        assertEquals(2.75f, normalizeLongPressSpeed(2.75f))
        assertEquals(1.37f, normalizeLongPressSpeed(1.37f))
        assertEquals(1f, normalizeLongPressSpeed(0.5f))
        assertEquals(8f, normalizeLongPressSpeed(9f))
        assertEquals(DEFAULT_LONG_PRESS_SPEED, normalizeLongPressSpeed(Float.NaN))
    }

    @Test
    fun `new menu entries reject duplicates and invalid precision or range`() {
        val options = listOf(1f, 1.25f, 2f)
        assertEquals(1.35f, parseNewPlaybackSpeedOption(" 1.35x ", options))
        assertEquals(8f, parseNewPlaybackSpeedOption("8", options))
        assertNull(parseNewPlaybackSpeedOption("1.25", options))
        assertNull(parseNewPlaybackSpeedOption("1.234", options))
        assertNull(parseNewPlaybackSpeedOption("1.230", options))
        assertNull(parseNewPlaybackSpeedOption("1e0", options))
        assertNull(parseNewPlaybackSpeedOption("0.09", options))
        assertNull(parseNewPlaybackSpeedOption("NaN", options))
    }

    @Test
    fun `direct long-press input accepts off-menu speeds but rejects invalid values`() {
        assertEquals(1.37f, parseLongPressSpeedInput("1.37"))
        assertEquals(3.15f, parseLongPressSpeedInput(" 3.15x "))
        assertEquals(8f, parseLongPressSpeedInput("8"))
        assertNull(parseLongPressSpeedInput("0.99"))
        assertNull(parseLongPressSpeedInput("8.01"))
        assertNull(parseLongPressSpeedInput("1.234"))
        assertNull(parseLongPressSpeedInput("NaN"))
        assertNull(parseLongPressSpeedInput(""))
    }

}
