package com.android.purebilibili.feature.dynamic.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicVideoCardSharedTransitionStructureTest {



    @Test
    fun dynamicVideoCard_recordsCoverBoundsForReturnTarget() {
        val source = File("src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt")
            .readText()

        assertTrue(source.contains("val coverBoundsRef = remember"))
        assertTrue(source.contains("coverBoundsRef.value?.let { bounds ->"))
        assertTrue(source.contains(".onGloballyPositioned { coordinates ->"))
    }

    @Test
    fun dynamicVideoCard_durationUsesCoverOverlayShadowNotCapsule() {
        val source = File("src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt")
            .readText()

        assertTrue(source.contains("resolveVideoCardCoverOverlayTextShadow()"))
        assertTrue(source.contains("feedContentTypography().coverBadge"))
        assertFalse(source.contains("MediaContrastPalette.Scrim.copy(alpha = 0.45f)"))
        assertTrue(source.contains("resolveAppTvIcon()"))
        assertTrue(source.contains("tint = MaterialTheme.colorScheme.onPrimaryContainer"))
        assertFalse(source.contains("rememberAppPlayIcon()"))
        assertFalse(source.contains("CircleShape"))
    }

    @Test
    fun dynamicVideoCard_obeysGlobalSharedTransitionSwitch() {
        val source = File("src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt")
            .readText()

        assertTrue(source.contains("val sharedTransitionEnabled = LocalSharedTransitionEnabled.current"))
        assertTrue(source.contains("val sharedElementReady = sharedTransitionEnabled &&"))
        assertTrue(source.contains("transitionEnabled = sharedTransitionEnabled"))
    }

    @Test
    fun dynamicVideoCard_freezesCoverChromeForReturn() {
        val source = File("src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt")
            .readText()

        assertTrue(source.contains("showGradientMask = true"))
        assertTrue(source.contains("showStatsOnCover = true"))
        assertTrue(source.contains("showSecondaryStatOnCover = true"))
        assertTrue(source.contains("showDurationOnCover = true"))
        assertTrue(source.contains("showStatsInInfo = false"))
    }
}
