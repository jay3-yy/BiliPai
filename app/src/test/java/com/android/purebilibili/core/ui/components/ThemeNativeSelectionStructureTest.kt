package com.android.purebilibili.core.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeNativeSelectionStructureTest {

    @Test
    fun singleChoiceListsUseTheSharedThemeNativeRow() {
        val targets = listOf(
            "feature/video/ui/components/VideoSettingsPanel.kt",
            "feature/live/components/LiveStreamSourceSheet.kt",
            "feature/bangumi/ui/player/BangumiPlayerContent.kt",
            "feature/bangumi/BangumiDetailScreen.kt",
            "feature/download/DownloadQualityDialog.kt",
            "feature/watchlater/WatchLaterScreen.kt",
            "feature/video/screen/TabletVideoLayout.kt",
            "feature/live/LivePlayerScreen.kt",
            "feature/video/ui/components/PagesSelector.kt",
        )

        targets.forEach { relativePath ->
            val source = loadAppSource(relativePath)
            assertTrue(
                source.contains("AppSingleChoiceRow("),
                "$relativePath must express selection through the shared native row",
            )
        }
    }

    @Test
    fun sharedMediaCardsUseNativeCheckboxesInsteadOfPaintedSelectionOverlays() {
        val source = loadAppSource("feature/personal/PersonalMediaCard.kt")

        assertTrue(source.contains("AppCheckbox("))
        assertFalse(source.contains("PersonalMediaCardDefaults.selectionOverlayColor"))
        assertFalse(source.contains("color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)"))
    }

    private fun loadAppSource(relativePath: String): String {
        val path = "src/main/java/com/android/purebilibili/$relativePath"
        return listOf(File(path), File("app/$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
