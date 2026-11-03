package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.resolveNowPlayingBarReturnVisibility
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioNowPlayingBarMotionPolicyTest {

    @Test
    fun sourceHideRequiresTheRealSharedReturnOwner() {
        val returning = NowPlayingBarHandoffState.Returning(
            targetBvid = "BV123",
            isSourceOwner = true,
        )
        assertEquals(
            0f,
            resolveNowPlayingBarReturnVisibility(handoff = returning, currentBvid = "BV123"),
        )
        assertEquals(
            1f,
            resolveNowPlayingBarReturnVisibility(
                handoff = NowPlayingBarHandoffState.Idle,
                currentBvid = "BV123",
            ),
        )
    }

    @Test
    fun sourceCannotOpenUntilLayoutAndImeAreSettled() {
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = false))
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = true, imeSettled = false))
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = true, sharedTransitionRunning = true))
        assertTrue(canOpenAudioNowPlayingBarSource(layoutStable = true))
    }

    @Test
    fun sourceBarUsesOneSharedGeometryTimeline() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/audio/screen/AudioNowPlayingBar.kt"
        )
        assertTrue(source.contains("onCompactClick"))
        assertTrue(source.contains("handoff"))
        assertFalse(source.contains("landingProgress"))
        assertFalse(source.contains("AudioNowPlayingBarLandingEasing"))
    }

    @Test
    fun expandedRowKeepsTitleBetweenCoverAndActions() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 0f,
            searchProgress = 0f,
            density = 1f,
        )
        assertTrue(metrics.titleWidthPx > 0)
        assertTrue(metrics.playWidthPx > 0)
        assertTrue(metrics.extraWidthPx > 0)
        // 封面、文字与两个操作占满可用宽度，不为已移除的第三个按钮预留空白。
        assertEquals(
            400 - metrics.horizontalPaddingPx,
            metrics.contentStartPx + metrics.coverPx + metrics.spacerPx +
                metrics.titleWidthPx + metrics.playWidthPx + metrics.extraWidthPx,
        )
    }

    @Test
    fun playbackRowDropsSupplementalWidthWithoutRecenteringCover() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 1f,
            searchProgress = 0f,
            density = 1f,
        )
        assertEquals(metrics.horizontalPaddingPx, metrics.contentStartPx)
        assertEquals(0, metrics.extraWidthPx)
        assertEquals(0, metrics.artistHeightPx)
        assertEquals(
            400 - metrics.horizontalPaddingPx,
            metrics.contentStartPx + metrics.coverPx + metrics.spacerPx +
                metrics.titleWidthPx + metrics.playWidthPx,
        )
        val expanded = resolveAudioNowPlayingBarRowMetrics(400, 0f, 0f, 1f)
        assertTrue(metrics.controlHeightPx < expanded.controlHeightPx)
        assertTrue(metrics.coverPx < expanded.coverPx)
    }

    @Test
    fun searchRowCollapsesChromeAndCentersCover() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 1f,
            searchProgress = 1f,
            density = 1f,
        )
        assertEquals(32, metrics.coverPx)
        assertEquals(0, metrics.horizontalPaddingPx)
        assertEquals(0, metrics.spacerPx)
        assertEquals(0, metrics.playWidthPx)
        assertEquals(0, metrics.extraWidthPx)
        assertEquals(0, metrics.titleWidthPx)
        assertEquals(184, metrics.contentStartPx)
        assertEquals(0, metrics.artistHeightPx)
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        return listOf(File(path), File(normalizedPath)).firstOrNull(File::exists)?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
