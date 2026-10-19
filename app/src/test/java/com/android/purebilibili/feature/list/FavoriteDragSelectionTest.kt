package com.android.purebilibili.feature.list

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteDragSelectionTest {
    private val keys = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun `dragging covers every item between anchor and current card`() {
        val selection = FavoriteDragSelection(keys, "b", emptySet())
        assertEquals(setOf("b"), selection.selectionThrough("b"))
        assertEquals(setOf("b", "c", "d", "e"), selection.selectionThrough("e"))
        assertEquals(setOf("a", "b"), selection.selectionThrough("a"))
    }

    @Test
    fun `reversing shrinks the gesture range and restores preexisting choices`() {
        val selection = FavoriteDragSelection(keys, "b", setOf("a", "e"))
        assertEquals(setOf("a", "b", "c", "d", "e"), selection.selectionThrough("d"))
        assertEquals(setOf("a", "b", "c", "e"), selection.selectionThrough("c"))
        assertEquals(setOf("a", "b", "e"), selection.selectionThrough("b"))
    }

    @Test
    fun `missing hit or removed anchor never selects unrelated resources`() {
        assertEquals(setOf("f"), FavoriteDragSelection(keys, "b", setOf("f")).selectionThrough("gone"))
        assertEquals(setOf("f"), FavoriteDragSelection(keys, "gone", setOf("f")).selectionThrough("d"))
    }
}
