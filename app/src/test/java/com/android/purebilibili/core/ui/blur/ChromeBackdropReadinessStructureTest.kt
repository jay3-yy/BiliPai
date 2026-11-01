package com.android.purebilibili.core.ui.blur

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeBackdropReadinessStructureTest {
    @Test
    fun scaffoldWarmsDuringSkeletonsWithoutExposingAnUnrecordedSource() {
        val source = source("core/ui/ImmersiveAppScaffold.kt")
        assertTrue(source.contains("if (progressive) rememberChromeBackdropSource()"))
        assertTrue(source.contains("source?.takeIf { blurContentReady && it.isReady }?.backdrop"))
        assertTrue(source.contains("if (blurActive) Color.Transparent else globalWallpaperAwareChromeColor(containerColor)"))
        assertTrue(source.indexOf(".then(source?.modifier ?: Modifier)") <
            source.indexOf(".globalWallpaperAwareBackground(containerColor)"))
    }

    @Test
    fun readinessComesFromRecordingRatherThanAnAssumedFrameDelay() {
        val source = source("core/ui/blur/ChromeBackdropSource.kt")
        assertTrue(source.contains("firstRecording.await()"))
        val drawCallback = source.substringAfter("rememberLayerBackdrop(onDraw = {").substringBefore("})")
        assertTrue(drawCallback.contains("drawLayer(contentLayer)"))
        assertTrue(drawCallback.contains("firstRecording.complete(Unit)"))
        assertFalse(drawCallback.contains("recorded.value ="))
        assertFalse(source.contains("withFrameNanos"))
    }

    @Test
    fun inboxAndEveryMessageFeedSuspendCaptureDuringSkeletonLoading() {
        listOf("InboxScreen", "feed/AtMeScreen", "feed/LikeMeScreen", "feed/ReplyMeScreen", "feed/SystemNoticeScreen")
            .forEach { screen ->
                assertTrue(screen, source("feature/message/$screen.kt").contains("blurContentReady = !uiState.isLoading"))
            }
    }

    private fun source(path: String): String = listOf(
        File("app/src/main/java/com/android/purebilibili/$path"),
        File("src/main/java/com/android/purebilibili/$path"),
    ).first { it.exists() }.readText()
}
