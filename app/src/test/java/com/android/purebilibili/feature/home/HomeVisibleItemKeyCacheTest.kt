package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HomeVisibleItemKeyCacheTest {
    private data class Item(val key: String, val offset: Int)

    @Test
    fun scrollingWithinTheSameItemsReusesKeysDespiteOffsetsAndLaneOrder() {
        val cache = HomeVisibleItemKeyCache()
        val before = cache.keys(listOf(Item("a", 0), Item("b", 120))) { it.key }
        val after = cache.keys(listOf(Item("b", 80), Item("a", -40))) { it.key }

        assertSame(before, after)
        assertEquals(setOf("a", "b"), after)
    }

    @Test
    fun replacingAnItemUpdatesVisibilityWithoutChangingEarlierSnapshots() {
        val cache = HomeVisibleItemKeyCache()
        val before = cache.keys(listOf("a", "b")) { it }
        val after = cache.keys(listOf("b", "c")) { it }

        assertNotSame(before, after)
        assertEquals(setOf("a", "b"), before)
        assertEquals(setOf("b", "c"), after)
        // Re-evaluating an earlier viewport must not return the newer cached membership.
        assertEquals(setOf("a", "b"), cache.keys(listOf("a", "b")) { it })
        assertEquals(setOf("b", "c"), after)
    }

    @Test
    fun viewportCanShrinkBecomeEmptyAndReappear() {
        val cache = HomeVisibleItemKeyCache()
        val initial = cache.keys(listOf("a", "b")) { it }

        assertEquals(setOf("b"), cache.keys(listOf("b")) { it })
        assertEquals(emptySet(), cache.keys(emptyList<String>()) { it })
        assertEquals(setOf("a", "b"), cache.keys(listOf("a", "b")) { it })
        assertEquals(setOf("a", "b"), initial)
    }
}
