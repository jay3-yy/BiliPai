package com.android.purebilibili.core.ui.adaptive

import android.net.Uri
import android.os.Build
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiInstanceAndDropPolicyTest {

    @Test
    fun adjacentMultiInstanceRequiresLargeScreenOrMultiWindowMode() {
        assertTrue(canLaunchAdjacentMultiInstance(isLargeScreen = true, isInMultiWindowMode = false, sdkInt = Build.VERSION_CODES.N))
        assertTrue(canLaunchAdjacentMultiInstance(isLargeScreen = false, isInMultiWindowMode = true, sdkInt = Build.VERSION_CODES.N))
        assertFalse(canLaunchAdjacentMultiInstance(isLargeScreen = false, isInMultiWindowMode = false, sdkInt = Build.VERSION_CODES.N))
    }

    @Test
    fun subtitleDropRecognizerMatchesExtensionsAndMimeTypes() {
        assertTrue(isSupportedSubtitleDropFileName("episode01.srt"))
        assertTrue(isSupportedSubtitleDropFileName("subs.ASS"))
        assertTrue(isSupportedSubtitleDropFileName("captions.vtt"))
        assertTrue(isSupportedSubtitleDropFileName("unknown", "application/x-subrip"))
        assertFalse(isSupportedSubtitleDropFileName("photo.png", "image/png"))
        assertFalse(isSupportedSubtitleDropFileName(null, null))
    }

    @Test
    fun bilibiliLinkDropExtractorFiltersValidBilibiliTargets() {
        assertNotNull(extractDroppableBilibiliLink("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertNotNull(extractDroppableBilibiliLink("BV1xx411c7mD"))
        assertNull(extractDroppableBilibiliLink("   "))
        assertNull(extractDroppableBilibiliLink("https://example.com/unrelated"))
    }

    @Test
    fun subtitleDropBridgeRoutesOnlyToTargetWindowAndRejectsWithoutSubscriber() {
        val windowA = 10101
        val windowB = 20202
        val fakeUri = mockk<Uri>(relaxed = true)
        var receivedA = 0
        var receivedB = 0

        assertFalse(ExternalSubtitleDropBridge.hasHandler(windowA))
        assertFalse(
            ExternalSubtitleDropBridge.dispatchToWindow(
                windowA,
                DroppedExternalSubtitle(windowToken = windowA, uri = fakeUri, displayName = "a.srt"),
            )
        )

        val unregisterA = ExternalSubtitleDropBridge.registerHandler(windowA) {
            receivedA += 1
            true
        }
        val unregisterB = ExternalSubtitleDropBridge.registerHandler(windowB) {
            receivedB += 1
            true
        }
        try {
            assertTrue(
                ExternalSubtitleDropBridge.dispatchToWindow(
                    windowA,
                    DroppedExternalSubtitle(windowToken = windowA, uri = fakeUri, displayName = "a.srt"),
                )
            )
            assertEquals(1, receivedA)
            assertEquals(0, receivedB)
        } finally {
            unregisterA()
            unregisterB()
        }
        assertFalse(ExternalSubtitleDropBridge.hasHandler(windowA))
    }
}
