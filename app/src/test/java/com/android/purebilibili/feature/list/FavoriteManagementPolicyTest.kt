package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteManagementPolicyTest {
    @Test
    fun `privacy ignores unrelated folder attribute bits`() {
        listOf(0, 2, 6, 22, 54).forEach { attr ->
            assertFalse(isFavoriteFolderPrivate(attr))
            assertTrue(isFavoriteFolderPrivate(attr or 1))
        }
    }

    @Test
    fun `favorite sort order maps to bilibili api values`() {
        assertEquals("mtime", FavoriteResourceOrder.FAVORITE_TIME.apiValue)
        assertEquals("view", FavoriteResourceOrder.PLAY_COUNT.apiValue)
        assertEquals("pubtime", FavoriteResourceOrder.PUBLISH_TIME.apiValue)
    }

    @Test
    fun `favorite folder content page size respects api limit`() {
        assertEquals(20, resolveFavoriteFolderContentPageSize())
        assertEquals(20, normalizeFavoriteResourcePageSize(99))
        assertEquals(1, normalizeFavoriteResourcePageSize(0))
        assertEquals(12, normalizeFavoriteResourcePageSize(12))
    }

    @Test
    fun `no selected folders grants no management permission`() {
        assertEquals(FavoriteFolderBatchPermissions(false, false, false), permissions(emptyList()))
    }

    @Test
    fun `owned public folders permit batch deletion sharing and cleaning`() {
        assertEquals(FavoriteFolderBatchPermissions(true, true, true), permissions(listOf(folder(2), folder(3))))
    }

    @Test
    fun `default folder in a mixed selection blocks deletion without blocking sharing or cleaning`() {
        assertEquals(FavoriteFolderBatchPermissions(false, true, true), permissions(listOf(folder(1), folder(2))))
    }

    @Test
    fun `one private folder blocks sharing the batch but retains owner management`() {
        assertEquals(FavoriteFolderBatchPermissions(true, false, true), permissions(listOf(folder(2), folder(3).copy(attr = 3))))
    }

    @Test
    fun `foreign subscribed and logged out selections cannot mutate folders`() {
        val invalidFolders = listOf(
            folder(2).copy(mid = 202),
            folder(2).copy(source = FavFolderSource.SUBSCRIBED),
            folder(0),
        )
        for (invalid in invalidFolders) {
            assertEquals(FavoriteFolderBatchPermissions(false, false, false), permissions(listOf(folder(3), invalid)))
        }
        assertEquals(FavoriteFolderBatchPermissions(false, false, false), favoriteFolderBatchPermissions(listOf(folder(2)), 1, null))
    }

    @Test
    fun `batch sharing includes every selected folder with its media id rather than fid`() {
        assertEquals(
            "收藏2\nhttps://www.bilibili.com/medialist/detail/ml2\n\n收藏3\nhttps://www.bilibili.com/medialist/detail/ml3",
            favoriteFolderShareText(listOf(folder(2).copy(fid = 200), folder(3).copy(fid = 300))),
        )
    }

    private fun permissions(folders: List<FavFolder>) = favoriteFolderBatchPermissions(folders, 1, 101)
    private fun folder(id: Long) = FavFolder(id = id, mid = 101, title = "收藏$id")
}
