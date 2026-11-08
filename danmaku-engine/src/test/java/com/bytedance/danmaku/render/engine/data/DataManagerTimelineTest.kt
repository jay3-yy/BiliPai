package com.bytedance.danmaku.render.engine.data

import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataManagerTimelineTest {
    private fun text(time: Long) = object : DanmakuData() {
        init { showAtTime = time; layerType = LAYER_TYPE_SCROLL }
    }
    private fun mask(start: Long, end: Long) = MaskData().apply {
        showAtTime = start
        this.start = start
        this.end = end
    }

    @Test
    fun `mask refresh neither replays text nor changes its next due time`() {
        var clock = 0L
        val data = DataManager(DanmakuConfig()) { clock }
        val first = text(100)
        val next = text(200)
        data.setData(listOf(first, next))
        data.onPlay(100)
        assertEquals(listOf(first), data.queryDanmaku().toList())
        val textTimeline = data.getData()
        val newMask = mask(80, 180)
        data.replaceMasks(listOf(newMask), 120)
        assertSame(textTimeline, data.getData())
        assertEquals(listOf(newMask), data.queryMasks(120).toList())
        clock = 20
        data.queryPlayTime()
        assertTrue(data.queryDanmaku().isEmpty())
        assertEquals(80L, data.nextDanmakuShowAfter(includeMask = false))
        clock = 100
        data.queryPlayTime()
        assertEquals(listOf(next), data.queryDanmaku().toList())
    }

    @Test
    fun `backward seek restores current mask and correct ordinary cursor`() {
        val data = DataManager(DanmakuConfig()) { 0L }
        val early = text(100)
        val late = text(500)
        val currentMask = mask(50, 250)
        data.setData(listOf(late, currentMask, early))
        data.onPlay(500)
        assertEquals(listOf(late), data.queryDanmaku().toList())
        data.seekTo(100)
        assertEquals(listOf(early, currentMask), data.queryDanmaku().toList())
        assertTrue(data.queryDanmaku().isEmpty())
    }

    @Test
    fun `soft correction skips forward without replaying consumed ordinary data`() {
        val data = DataManager(DanmakuConfig()) { 0L }
        val first = text(100)
        val next = text(200)
        data.setData(listOf(first, next))
        data.onPlay(100)
        data.queryDanmaku()
        data.synchronizePlayTime(90)
        assertTrue(data.queryDanmaku().isEmpty())
        assertEquals(110L, data.nextDanmakuShowAfter(false))
    }

    @Test
    fun `future append is consumed once and masked history can be trimmed`() {
        var clock = 0L
        val data = DataManager(DanmakuConfig()) { clock }
        val first = text(10)
        val next = text(20)
        data.setData(listOf(first, mask(0, 15)))
        data.onPlay(10)
        data.queryDanmaku()
        data.appendData(listOf(next))
        clock = 10
        data.queryPlayTime()
        assertEquals(listOf(next), data.queryDanmaku().toList())
        data.discardBefore(20)
        assertEquals(listOf(next), data.getData())
        assertEquals(0, data.getRemainDanmakuCount())
    }

    @Test
    fun `speed changes between frames preserve elapsed media time and due items`() {
        var clock = 0L
        val config = DanmakuConfig()
        val data = DataManager(config) { clock }
        val first = text(1_000)
        val next = text(3_000)
        data.setData(listOf(first, next))
        data.onPlay(1_000)
        assertEquals(listOf(first), data.queryDanmaku().toList())

        // A blank renderer can sleep for seconds without sampling the media clock.
        clock = 1_000
        config.common.playSpeed = 200
        data.onPlaySpeedChanged()
        assertEquals(2_000L, data.queryPlayTime())
        assertTrue(data.queryDanmaku().isEmpty())
        clock = 1_500
        assertEquals(3_000L, data.queryPlayTime())
        assertEquals(listOf(next), data.queryDanmaku().toList())
        assertTrue(data.queryDanmaku().isEmpty())

        clock = 1_750
        config.common.playSpeed = 50
        data.onPlaySpeedChanged()
        assertEquals(3_500L, data.queryPlayTime())
        clock = 2_750
        assertEquals(4_000L, data.queryPlayTime())
        assertTrue(data.queryDanmaku().isEmpty())
    }

    @Test
    fun `rapid speed toggles without draws do not accumulate clock lag`() {
        var clock = 0L
        val config = DanmakuConfig()
        val data = DataManager(config) { clock }
        data.onPlay(10_000)
        repeat(10) {
            clock += 100
            config.common.playSpeed = 200
            data.onPlaySpeedChanged()
            clock += 100
            config.common.playSpeed = 100
            data.onPlaySpeedChanged()
        }
        assertEquals(13_000L, data.queryPlayTime())
    }

    @Test
    fun `paused speed changes retain the position at pause until resumed`() {
        var clock = 0L
        val config = DanmakuConfig()
        val data = DataManager(config) { clock }
        data.onPlay(1_000)
        clock = 500
        data.onPause()
        assertEquals(1_500L, data.queryPlayTime())
        clock = 2_000
        config.common.playSpeed = 200
        data.onPlaySpeedChanged()
        clock = 3_000
        assertEquals(1_500L, data.queryPlayTime())
        data.onPlay(data.queryPlayTime())
        clock = 3_500
        assertEquals(2_500L, data.queryPlayTime())
    }

    @Test
    fun `media position remains authoritative while requested speed takes effect`() {
        var clock = 0L
        var mediaPosition = 1_000L
        val config = DanmakuConfig()
        val data = DataManager(config) { clock }
        val next = text(2_000)
        data.setData(listOf(next))
        data.setPlaybackPositionProvider { mediaPosition }
        data.onPlay(mediaPosition)

        config.common.playSpeed = 300
        data.onPlaySpeedChanged()
        clock = 1_000
        mediaPosition = 1_500
        assertEquals(1_500L, data.queryPlayTime())
        assertTrue(data.queryDanmaku().isEmpty())

        config.common.playSpeed = 50
        data.onPlaySpeedChanged()
        clock = 1_400
        mediaPosition = 2_100
        assertEquals(2_100L, data.queryPlayTime())
        assertEquals(listOf(next), data.queryDanmaku().toList())
        assertTrue(data.queryDanmaku().isEmpty())
        data.onPause()
        mediaPosition = 3_000
        clock = 5_000
        assertEquals(2_100L, data.queryPlayTime())
    }

    @Test
    fun `detaching a media clock preserves position and consumed items`() {
        var clock = 0L
        var mediaPosition = 1_000L
        val config = DanmakuConfig().apply { common.playSpeed = 200 }
        val data = DataManager(config) { clock }
        val first = text(1_000)
        val next = text(2_000)
        data.setData(listOf(first, next))
        data.setPlaybackPositionProvider { mediaPosition }
        data.onPlay(mediaPosition)
        assertEquals(listOf(first), data.queryDanmaku().toList())
        clock = 500
        mediaPosition = 1_400
        data.setPlaybackPositionProvider(null)
        mediaPosition = 9_000
        clock = 800
        assertEquals(2_000L, data.queryPlayTime())
        assertEquals(listOf(next), data.queryDanmaku().toList())
        data.setPlaybackPositionProvider { 2_200L }
        assertEquals(2_200L, data.queryPlayTime())
        assertTrue(data.queryDanmaku().isEmpty())
    }
}
