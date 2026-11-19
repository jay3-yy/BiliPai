package com.android.purebilibili.navigation3

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BiliPaiNavDisplayHostNativeTransitionStructureTest {

    @Test
    fun videoCardMorphOwnsCornersWithoutHostLeadingClip() {
        val source = loadSource()

        assertTrue(source.contains("val videoCardMorphOwnsCorners = cardMorphAvailable"))
        assertTrue(source.contains("isCardMorphDestinationNavKey(currentKey)"))
        assertTrue(source.contains("VideoCardTransitionExposure.Returning"))
        assertTrue(source.contains("val enableHostCornerClip = !videoCardMorphOwnsCorners"))
        assertTrue(source.contains("enableCornerClip = enableHostCornerClip"))
        assertTrue(source.contains("val hostDimAmount = if (videoCardMorphOwnsCorners) 0f else 0.5f"))
        assertTrue(source.contains("dimAmount = hostDimAmount"))
    }

    @Test
    fun settledOrUnavailableCardMorphReleasesNativeCardLayers() {
        val source = loadSource()

        assertTrue(source.contains("if (!cardMorphAvailable)"))
        assertTrue(source.contains("state == VideoCardTransitionSettleState.Idle"))
        assertTrue(source.countOccurrences("CardPositionManager.clearNativeVideoCardLayers()") == 2)
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(size = needle.length, step = 1).count { it == needle }

    private fun loadSource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/BiliPaiNavDisplayHost.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/BiliPaiNavDisplayHost.kt")
        ).first { it.exists() }.readText()
    }
}
