package com.android.purebilibili.feature.list

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun favoriteVideoTabShowsFolderCardsWithSubscribedEntry() {
        val listSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt"
        )
        val cardListSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/list/FavoriteFolderCardList.kt"
        )

        assertTrue(listSource.contains("FavoriteFolderCardList("))
        assertTrue(cardListSource.contains("订阅收藏夹"))
        assertTrue(cardListSource.contains("onSubscribedClick"))
        assertFalse(listSource.contains("selectedValue = favoriteBrowseSection"))
        assertFalse(listSource.contains("FavoriteFolderSummary("))
        assertFalse(listSource.contains("AppSegmentOption(FavoriteBrowseSection.OWNED"))
    }

    @Test
    fun sharedSegmentedControlUsesGlobalLiquidSettingAndForwardsInteractionOptions() {
        val segmentedSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/settings/AppSegmentedControl.kt"
        )
        val bottomBarSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarLiquidSegmentedControl.kt"
        )

        assertFalse(segmentedSource.contains("forceLiquidIndicator"))
        assertTrue(
            segmentedSource.contains("tapPressRefractionEnabled: Boolean = true"),
            "Shared segmented control should expose tap refraction control to callers"
        )
        assertTrue(
            segmentedSource.contains("tapPressRefractionEnabled = tapPressRefractionEnabled"),
            "Shared iOS segmented control should forward tap refraction control into the bottom-bar liquid implementation"
        )
        assertTrue(
            segmentedSource.contains("dragSelectionEnabled = dragSelectionEnabled"),
            "Shared segmented control should forward drag-selection policy to its liquid implementation"
        )
        assertFalse(bottomBarSource.contains("forceLiquidChrome"))
        assertTrue(bottomBarSource.contains("homeSettings.androidNativeLiquidGlassEnabled"))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
