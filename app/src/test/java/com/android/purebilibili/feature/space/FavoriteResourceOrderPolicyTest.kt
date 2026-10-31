package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.FavoriteData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FavoriteResourceOrderPolicyTest {
    private fun item(id: Long, type: Int = 2) = FavoriteData(id = id, type = type)

    private fun applyPayload(items: List<FavoriteData>, payload: String): List<String> {
        val working = items.map { it.orderKey }.toMutableList()
        if (payload.isBlank()) return working
        payload.split(',').forEach { operation ->
            val fields = operation.split(':')
            assertEquals(4, fields.size)
            val predecessor = "${fields[0]}:${fields[1]}"
            val moved = "${fields[2]}:${fields[3]}"
            check(working.remove(moved))
            val index = if (predecessor == "0:0") 0 else working.indexOf(predecessor).also { check(it >= 0) } + 1
            working.add(index, moved)
        }
        return working
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.isEmpty()) listOf(emptyList())
        else items.flatMap { item -> permutations(items - item).map { listOf(item) + it } }

    @Test
    fun `payload produces every target order and preserves unloaded suffix`() {
        // 同一数字 ID 的不同资源类型也必须保持独立身份。
        val original = listOf(item(1), item(1, 12), item(2), item(3))
        val unloaded = listOf(item(4), item(5, 12))
        permutations(original).forEach { draft ->
            assertEquals(
                (draft + unloaded).map { it.orderKey },
                applyPayload(original + unloaded, favoriteResourceOrderPayload(original, draft)),
            )
        }
    }

    @Test
    fun `move supports up down first last and ignores missing identity`() {
        val original = listOf(item(1), item(2), item(3))
        assertEquals(listOf(3L, 1L, 2L), moveFavoriteResource(original, "3:2", "1:2").map { it.id })
        assertEquals(listOf(2L, 3L, 1L), moveFavoriteResource(original, "1:2", "3:2").map { it.id })
        assertEquals(listOf(2L, 1L, 3L), moveFavoriteResource(original, "1:2", "2:2").map { it.id })
        assertEquals(original, moveFavoriteResource(original, "9:2", "1:2"))
    }

    @Test
    fun `unchanged order produces no operation and first move uses zero predecessor`() {
        val original = listOf(item(1), item(2))
        assertEquals("", favoriteResourceOrderPayload(original, original))
        assertEquals("0:0:2:2", favoriteResourceOrderPayload(original, original.reversed()))
    }

    @Test
    fun `changed sets duplicates and invalid resource identity are rejected`() {
        assertFailsWith<IllegalArgumentException> { favoriteResourceOrderPayload(listOf(item(1)), listOf(item(2))) }
        assertFailsWith<IllegalArgumentException> { favoriteResourceOrderPayload(listOf(item(1), item(1)), listOf(item(1), item(1))) }
        assertFailsWith<IllegalArgumentException> { favoriteResourceOrderPayload(listOf(item(0)), listOf(item(0))) }
        assertFailsWith<IllegalArgumentException> { favoriteResourceOrderPayload(listOf(item(1, 0)), listOf(item(1, 0))) }
    }

    @Test
    fun `sort modes use the correct server order`() {
        assertEquals("mtime", FavoriteDetailSortOrder.NEWEST_FIRST.serverOrder)
        assertEquals("mtime", FavoriteDetailSortOrder.OLDEST_FIRST.serverOrder)
        assertEquals("view", FavoriteDetailSortOrder.MOST_PLAYED.serverOrder)
        assertEquals("pubtime", FavoriteDetailSortOrder.LATEST_PUBLISHED.serverOrder)
    }
}
