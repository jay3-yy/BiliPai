package com.android.purebilibili.feature.video.danmaku

import com.android.purebilibili.danmaku.engine.DanmakuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotDanmakuPolicyTest {
    @Test
    fun `short entries share the row while a long entry is left in the normal timeline`() {
        val items = listOf(item(1, 0, 100, "很长的热门弹幕"), item(2, 0, 30, "短句"), item(3, 0, 20, "短"))
        val selected = selectFittingHotDanmaku(items, 50f, 2f) { it.text.orEmpty().length * 10f }
        assertEquals(listOf(3L, 2L), selected.map { it.danmakuId })
    }

    @Test
    fun `row count changes at complete width and equal gap boundaries`() {
        val items = listOf(item(1, 0, 100), item(2, 0, 30), item(3, 0, 20), item(4, 0, 10))
        fun selectedIds(width: Float) =
            selectFittingHotDanmaku(items, width, 2f) { 10f }.map { it.danmakuId }
        assertTrue(selectedIds(9f).isEmpty())
        assertEquals(listOf(1L), selectedIds(10f))
        assertEquals(listOf(1L), selectedIds(25f))
        assertEquals(listOf(1L, 2L), selectedIds(26f))
        assertEquals(listOf(1L, 2L), selectedIds(37f))
        assertEquals(listOf(1L, 2L, 3L), selectedIds(38f))
        assertEquals(listOf(1L, 2L, 3L), selectedIds(100f))
    }

    @Test
    fun `narrow row keeps only complete entries or stays empty`() {
        val items = listOf(item(1, 0, 100), item(2, 0, 20))
        assertEquals(listOf(1L), selectFittingHotDanmaku(items, 35f, 2f) { 30f }.map { it.danmakuId })
        assertTrue(selectFittingHotDanmaku(items, 20f, 2f) { 30f }.isEmpty())
    }

    @Test
    fun `invalid measurements do not crowd out a complete entry`() {
        val items = listOf(item(1, 0, 100), item(2, 0, 30), item(3, 0, 20), item(4, 0, 10))
        val selected = selectFittingHotDanmaku(items, 35f, 2f) {
            when (it.danmakuId) {
                1L -> Float.NaN
                2L -> Float.POSITIVE_INFINITY
                3L -> 0f
                else -> 30f
            }
        }
        assertEquals(listOf(4L), selected.map { it.danmakuId })
    }

    private fun item(id: Long, time: Long, likes: Long, content: String = "弹幕$id") =
        DanmakuItem().apply {
            danmakuId = id
            showAtTime = time
            likeCount = likes
            text = content
        }

    @Test
    fun `window includes endpoints but excludes old and future comments`() {
        val index = HotDanmakuIndex(listOf(
            item(4, 20_001, 999), item(3, 20_000, 11), item(1, 4_999, 999),
            item(2, 5_000, 10), item(5, 20_000, 12), item(6, 5_000, 10),
        ))
        assertEquals(listOf(5L, 3L, 2L, 6L), index.select(20_000).map { it.danmakuId })
    }

    @Test
    fun `ranking uses stable ties and deduplicates ids and text`() {
        val list = listOf(item(4, 0, 30), item(3, 0, 30), item(2, 0, 40, "同款"),
            item(1, 100, 50, "同款"), item(1, 0, 50), item(5, 0, 10))
        val selected = HotDanmakuIndex(list).select(100)
        assertEquals(listOf(1L, 3L, 4L, 5L), selected.map { it.danmakuId })
        assertEquals("同款", selected.first().text)
    }

    @Test
    fun `invalid ids blank text and low likes are omitted`() {
        assertTrue(HotDanmakuIndex(listOf(item(0, 0, 100), item(1, 0, 100, " "),
            item(2, 0, 9), item(3, -1, 100))).select(0).isEmpty())
    }

    @Test
    fun `seek and equal sized replacement use fresh data`() {
        val first = HotDanmakuIndex(listOf(item(1, 0, 10)))
        val replacement = HotDanmakuIndex(listOf(item(2, 30_000, 20)))
        assertEquals(listOf(1L), first.select(0).map { it.danmakuId })
        assertTrue(first.select(30_000).isEmpty())
        assertEquals(listOf(2L), replacement.select(30_000).map { it.danmakuId })
        assertTrue(replacement.select(0).isEmpty())
    }
}
