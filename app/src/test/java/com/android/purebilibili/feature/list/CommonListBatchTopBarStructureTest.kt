package com.android.purebilibili.feature.list

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonListBatchTopBarStructureTest {
    @Test
    fun batchModeHidesBrowsingActionsBeforeRenderingBatchActions() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt"),
        ).first(File::exists).readText()
        val actions = source.substringAfter("actions = {").substringBefore("if (favoriteViewModel != null")

        assertTrue(actions.contains("val isBatchActionMode = isFavoriteBatchMode || isHistoryBatchMode"))
        assertTrue(actions.contains("if (!isBatchActionMode)"))
    }

    @Test
    fun personalListsUsePinchZoomColumnsInsteadOfLayoutToggle() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt"),
        ).first(File::exists).readText()

        assertFalse(source.contains("VideoListLayoutToggle("))
        assertTrue(source.contains("homeFeedPinchZoom("))
        assertTrue(source.contains("resolveHomeFeedPinchColumnBounds("))
        assertTrue(source.contains("GridPinchColumnHudPill("))
    }
}
