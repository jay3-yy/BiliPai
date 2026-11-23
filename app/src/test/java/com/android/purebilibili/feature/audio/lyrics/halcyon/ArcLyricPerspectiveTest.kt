package com.android.purebilibili.feature.audio.lyrics.halcyon

import com.android.purebilibili.core.store.ArcLyricsCurveDirection
import com.android.purebilibili.core.store.ArcLyricsTiltIntensity
import com.android.purebilibili.core.store.LyricsTranslationFontScale
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.audio.screen.MusicPlayerLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcLyricPerspectiveTest {

    @Test
    fun `center line stays level opaque and unscaled`() {
        val center = resolveArcLyricTransform(normalizedOffset = 0f, reduceMotion = false)

        assertEquals(0f, center.rotationZ, 0.001f)
        assertEquals(0f, center.rotationX, 0.001f)
        assertEquals(1f, center.scale, 0.001f)
        assertEquals(0f, center.translationXDp, 0.001f)
        assertEquals(1f, center.alpha, 0.001f)
    }

    @Test
    fun `rows above and below center tilt in opposite directions and fade smoothly`() {
        val above = resolveArcLyricTransform(normalizedOffset = -0.8f, reduceMotion = false)
        val below = resolveArcLyricTransform(normalizedOffset = 0.8f, reduceMotion = false)

        assertTrue(above.rotationZ < 0f)
        assertTrue(below.rotationZ > 0f)
        assertEquals(-above.rotationZ, below.rotationZ, 0.001f)
        assertTrue(above.rotationX > 0f)
        assertTrue(below.rotationX < 0f)
        assertTrue(above.scale < 1f && below.scale < 1f)
        assertTrue(above.translationXDp < 0f && below.translationXDp < 0f)
        assertTrue(above.alpha in 0.48f..0.95f && below.alpha in 0.48f..0.95f)
    }

    @Test
    fun `reduce motion disables 3d tilt and scale while keeping focus alpha`() {
        val reduced = resolveArcLyricTransform(normalizedOffset = 0.9f, reduceMotion = true)

        assertEquals(0f, reduced.rotationZ, 0.001f)
        assertEquals(0f, reduced.rotationX, 0.001f)
        assertEquals(0f, reduced.rotationY, 0.001f)
        assertEquals(1f, reduced.scale, 0.001f)
        assertEquals(0f, reduced.translationXDp, 0.001f)
        assertTrue(reduced.alpha < 1f)
    }

    @Test
    fun `music lyrics ui style cycles through classic immersive and arc`() {
        assertEquals(SettingsManager.MusicLyricsUiStyle.IMMERSIVE, SettingsManager.MusicLyricsUiStyle.CLASSIC.next())
        assertEquals(SettingsManager.MusicLyricsUiStyle.ARC, SettingsManager.MusicLyricsUiStyle.IMMERSIVE.next())
        assertEquals(SettingsManager.MusicLyricsUiStyle.CLASSIC, SettingsManager.MusicLyricsUiStyle.ARC.next())
        assertEquals(SettingsManager.MusicLyricsUiStyle.ARC, SettingsManager.MusicLyricsUiStyle.fromValue(2))
    }

    @Test
    fun `arc viewport profile adapts across mobile portrait tablet split and foldable tabletop`() {
        val mobile = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.COMPACT_PAGER,
            widthDp = 390,
            heightDp = 844,
            fullScreenLyrics = true,
        )
        val foldableExpanded = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 720,
            heightDp = 800,
            fullScreenLyrics = false,
        )
        val tablet = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
        )
        val tabletop = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.TABLETOP,
            widthDp = 720,
            heightDp = 360,
            fullScreenLyrics = false,
        )

        assertTrue(mobile.applySystemBarInsets)
        assertTrue(mobile.arcCurveIndentDp < tablet.arcCurveIndentDp)
        assertTrue(foldableExpanded.primaryTextSizeSp < tablet.primaryTextSizeSp)
        assertFalse(foldableExpanded.applySystemBarInsets)
        assertFalse(tablet.applySystemBarInsets)
        assertFalse(tabletop.applySystemBarInsets)
        assertTrue(tabletop.topContentPaddingDp < mobile.topContentPaddingDp)
        assertTrue(tabletop.primaryTextSizeSp < foldableExpanded.primaryTextSizeSp)
    }

    @Test
    fun `tilt intensity scales arc angles monotonically and clamps upper bounds`() {
        val soft = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
            tiltScale = ArcLyricsTiltIntensity.SOFT.tiltScale,
        )
        val standard = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
            tiltScale = ArcLyricsTiltIntensity.STANDARD.tiltScale,
        )
        val vivid = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
            tiltScale = ArcLyricsTiltIntensity.VIVID.tiltScale,
        )

        assertTrue(soft.maxRotationZDeg < standard.maxRotationZDeg)
        assertTrue(standard.maxRotationZDeg < vivid.maxRotationZDeg)
        assertTrue(vivid.maxRotationZDeg <= 15f)
        assertTrue(vivid.arcCurveIndentDp <= 20f)
        assertEquals(ArcLyricsTiltIntensity.SOFT, ArcLyricsTiltIntensity.fromValue(0))
        assertEquals(ArcLyricsTiltIntensity.STANDARD, ArcLyricsTiltIntensity.fromValue(1))
        assertEquals(ArcLyricsTiltIntensity.VIVID, ArcLyricsTiltIntensity.fromValue(2))
        assertEquals(LyricsTranslationFontScale.SMALL, LyricsTranslationFontScale.fromValue(0))
        assertEquals(LyricsTranslationFontScale.STANDARD, LyricsTranslationFontScale.fromValue(1))
        assertEquals(LyricsTranslationFontScale.LARGE, LyricsTranslationFontScale.fromValue(2))
        assertEquals(ArcLyricsCurveDirection.LEFT, ArcLyricsCurveDirection.fromValue(0))
        assertEquals(ArcLyricsCurveDirection.RIGHT, ArcLyricsCurveDirection.fromValue(1))

        val leftProfile = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
            curveDirection = ArcLyricsCurveDirection.LEFT,
        )
        val rightProfile = resolveArcLyricViewportProfile(
            layout = MusicPlayerLayout.EXPANDED_SPLIT,
            widthDp = 1100,
            heightDp = 780,
            fullScreenLyrics = false,
            curveDirection = ArcLyricsCurveDirection.RIGHT,
        )
        assertEquals(-1f, leftProfile.curveDirectionSign, 0.001f)
        assertEquals(1f, rightProfile.curveDirectionSign, 0.001f)
        assertTrue(leftProfile.horizontalPaddingStartDp > leftProfile.arcCurveIndentDp)
        assertTrue(
            resolveArcLyricTransform(
                normalizedOffset = 0.8f,
                curveDirectionSign = rightProfile.curveDirectionSign,
            ).translationXDp > 0f
        )
    }
}
