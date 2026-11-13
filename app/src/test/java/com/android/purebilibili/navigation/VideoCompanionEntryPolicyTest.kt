package com.android.purebilibili.navigation

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCompanionEntryPolicyTest {
    private val window = WindowSizeClass(
        WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded, 1000.dp, 900.dp,
    )
    private val video = BiliPaiNavKey.VideoDetail("BV1xx411c7mD")

    @Test
    fun ordinaryVideosCanKeepEveryListSourceBesideThem() {
        listOf(
            BiliPaiNavKey.MainHost, BiliPaiNavKey.Search("测试"), BiliPaiNavKey.History,
            BiliPaiNavKey.Favorite, BiliPaiNavKey.WatchLater, BiliPaiNavKey.Space(42L),
        ).forEach { source ->
            assertTrue(shouldOpenVideoCompanion(video, source, true, window, AppFoldPosture.Flat, false), source.toString())
        }
    }

    @Test
    fun disablingFeatureOrOpeningFromPlayerKeepsNormalNavigation() {
        assertFalse(shouldOpenVideoCompanion(video, BiliPaiNavKey.MainHost, false, window, AppFoldPosture.Flat, false))
        assertFalse(shouldOpenVideoCompanion(video, BiliPaiNavKey.VideoDetail("BV2xx411c7mD"), true, window, AppFoldPosture.Flat, false))
        val compact = window.copy(widthSizeClass = WindowWidthSizeClass.Compact, widthDp = 393.dp)
        assertFalse(shouldOpenVideoCompanion(video, BiliPaiNavKey.History, true, compact, AppFoldPosture.None, false))
    }

    @Test
    fun explicitPlaybackModesArePreservedFromAllSources() {
        listOf(video.copy(fullscreen = true), video.copy(startAudio = true), video.copy(initialVertical = true)).forEach {
            assertFalse(shouldOpenVideoCompanion(it, BiliPaiNavKey.Search("测试"), true, window, AppFoldPosture.Flat, false))
        }
    }
}
