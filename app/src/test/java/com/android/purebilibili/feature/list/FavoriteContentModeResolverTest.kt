package com.android.purebilibili.feature.list

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteContentModeResolverTest {

    @Test
    fun nonFavoritePageUsesBaseMode() {
        assertEquals(
            FavoriteContentMode.BASE_LIST,
            resolveFavoriteContentMode(isFavoritePage = false, folderCount = 3)
        )
    }

    @Test
    fun singleFolderUsesFolderStateMode() {
        assertEquals(
            FavoriteContentMode.SINGLE_FOLDER,
            resolveFavoriteContentMode(isFavoritePage = true, folderCount = 1)
        )
    }

    @Test
    fun multipleFoldersUsePagerMode() {
        assertEquals(
            FavoriteContentMode.PAGER,
            resolveFavoriteContentMode(isFavoritePage = true, folderCount = 2)
        )
    }

}
