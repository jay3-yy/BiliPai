package com.android.purebilibili.core.util

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPresentationPolicyTest {
    @Test
    fun `tablet inset below 600dp stays inline on entry and after fullscreen back`() {
        // 这是阈值回归输入，不是特定型号的实测尺寸。
        for ((width, height) in listOf(1000 to 576, 576 to 1000)) {
            val display = resolveAppDisplayContext(AppDisplayContextInput(
                currentWindowWidthDp = width,
                currentWindowHeightDp = height,
                maximumWindowWidthDp = 1000,
                maximumWindowHeightDp = 600,
                smallestScreenWidthDp = 600,
                configurationOrientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT,
                displayRotation = if (width > height) Surface.ROTATION_90 else Surface.ROTATION_0,
            ))
            assertEquals(AppFoldableDisplayRole.Standard, display.foldableDisplayRole)
            assertFalse(shouldUsePhonePlayerOrientation(display))
            for (manualFullscreen in listOf(false, true, false)) {
                val presentation = resolvePlayerPresentationPolicy(
                    display, isLandscape = width > height, userFullscreenIntent = manualFullscreen,
                    prefersManualFullscreen = false, isInMultiWindowMode = false,
                )
                assertFalse(presentation.isOrientationDriven)
                assertFalse(presentation.orientationGeneratedFullscreen)
                assertEquals(manualFullscreen, presentation.isFullscreen)
            }
            for (appRotate in listOf(false, true)) {
                for (systemRotate in listOf(false, true)) {
                    val request = com.android.purebilibili.feature.video.screen.resolvePhoneVideoRequestedOrientation(
                        autoRotateEnabled = appRotate,
                        systemAutoRotateEnabled = systemRotate,
                        fullscreenMode = com.android.purebilibili.core.store.FullscreenMode.AUTO,
                        isCompactDevice = shouldUsePhonePlayerOrientation(display),
                        isOrientationDrivenFullscreen = false,
                        isFullscreenMode = false,
                        manualFullscreenRequested = false,
                        manualPortraitHoldActive = false,
                        isVerticalVideo = false,
                        isPortraitFullscreen = false,
                        isCurrentlyLandscape = width > height,
                    )
                    assertEquals(if (appRotate) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, request)
                }
            }
        }
    }

    @Test
    fun `stable tablet identity wins over transient narrow maximum window metrics`() {
        val display = resolveAppDisplayContext(AppDisplayContextInput(
            currentWindowWidthDp = 900,
            currentWindowHeightDp = 550,
            maximumWindowWidthDp = 900,
            maximumWindowHeightDp = 550,
            smallestScreenWidthDp = 720,
        ))
        assertFalse(shouldUsePhonePlayerOrientation(display))
        assertFalse(resolvePlayerPresentationPolicy(display, true, false, false, false).isFullscreen)
    }


    @Test
    fun `Pura X Max cover stays in-window across landscape, portrait grip, and letterboxed lock`() {
        listOf(
            puraCover(widthDp = 616, heightDp = 421, orientation = Configuration.ORIENTATION_LANDSCAPE, rotation = Surface.ROTATION_0),
            puraCover(widthDp = 421, heightDp = 616, orientation = Configuration.ORIENTATION_PORTRAIT, rotation = Surface.ROTATION_90),
            puraCover(widthDp = 421, heightDp = 616, orientation = Configuration.ORIENTATION_PORTRAIT, rotation = Surface.ROTATION_0),
        ).forEach { displayContext ->
            val presentation = resolvePlayerPresentationPolicy(
                displayContext = displayContext,
                isLandscape = displayContext.currentWindowWidthDp > displayContext.currentWindowHeightDp,
                userFullscreenIntent = false,
                prefersManualFullscreen = false,
                isInMultiWindowMode = false,
            )
            assertEquals(AppFoldableDisplayRole.Cover, displayContext.foldableDisplayRole)
            assertTrue(presentation.usesInWindowFullscreen)
            assertFalse(presentation.isOrientationDriven)
            assertFalse(presentation.isFullscreen)
            assertFalse(presentation.shouldHandleBackAsExitFullscreen)
            listOf(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ).forEach { requested ->
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    resolvePlayerAxisOrientationRequest(
                        requestedOrientation = requested,
                        usesInWindowFullscreen = presentation.usesInWindowFullscreen,
                    ),
                )
            }
        }
    }

    @Test
    fun `user fullscreen on landscape-natural cover is in-window and back exits that layer`() {
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = puraCover(
                widthDp = 616,
                heightDp = 421,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                rotation = Surface.ROTATION_0,
            ),
            isLandscape = true,
            userFullscreenIntent = true,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertTrue(presentation.usesInWindowFullscreen)
        assertTrue(presentation.userFullscreenIntent)
        assertTrue(presentation.isFullscreen)
        assertTrue(presentation.shouldHandleBackAsExitFullscreen)
        assertFalse(presentation.orientationGeneratedFullscreen)
    }

    @Test
    fun `Pura inner display is not treated as a cover and keeps user-driven fullscreen`() {
        val displayContext = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 861,
                currentWindowHeightDp = 609,
                maximumWindowWidthDp = 861,
                maximumWindowHeightDp = 609,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_0,
                displayModeWidthPx = 2584,
                displayModeHeightPx = 1828,
                hasCurrentFoldingFeature = true,
                hasHingeAngleSensor = true,
            )
        )
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = true,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertEquals(AppFoldableDisplayRole.Inner, displayContext.foldableDisplayRole)
        assertFalse(presentation.usesInWindowFullscreen)
        assertFalse(presentation.isOrientationDriven)
        assertFalse(presentation.isFullscreen)
    }

    @Test
    fun `ordinary landscape phone still auto-generates fullscreen`() {
        val displayContext = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 844,
                currentWindowHeightDp = 397,
                maximumWindowWidthDp = 397,
                maximumWindowHeightDp = 844,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_90,
                displayModeWidthPx = 904,
                displayModeHeightPx = 2316,
            )
        )
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = true,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertEquals(AppFoldableDisplayRole.Standard, displayContext.foldableDisplayRole)
        assertTrue(presentation.isOrientationDriven)
        assertTrue(presentation.orientationGeneratedFullscreen)
        assertTrue(presentation.isFullscreen)
        assertFalse(presentation.usesInWindowFullscreen)
    }

    @Test
    fun `ordinary tablet does not auto-enter fullscreen from landscape`() {
        val displayContext = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 1280,
                currentWindowHeightDp = 800,
                maximumWindowWidthDp = 1280,
                maximumWindowHeightDp = 800,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_90,
            )
        )
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = true,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertEquals(AppFoldableDisplayRole.Standard, displayContext.foldableDisplayRole)
        assertFalse(presentation.isOrientationDriven)
        assertFalse(presentation.isFullscreen)
    }

    @Test
    fun `portrait-natural foldable cover keeps phone orientation-driven fullscreen`() {
        val displayContext = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 844,
                currentWindowHeightDp = 397,
                maximumWindowWidthDp = 660,
                maximumWindowHeightDp = 884,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_90,
                displayModeWidthPx = 904,
                displayModeHeightPx = 2316,
                hasHingeAngleSensor = true,
            )
        )
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = true,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertEquals(AppFoldableDisplayRole.Cover, displayContext.foldableDisplayRole)
        assertFalse(presentation.usesInWindowFullscreen)
        assertTrue(presentation.isOrientationDriven)
        assertTrue(presentation.orientationGeneratedFullscreen)
    }

    @Test
    fun `split-screen foldable is unknown and does not consume back as fullscreen`() {
        val displayContext = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 420,
                currentWindowHeightDp = 700,
                maximumWindowWidthDp = 861,
                maximumWindowHeightDp = 609,
                configurationOrientation = Configuration.ORIENTATION_PORTRAIT,
                displayRotation = Surface.ROTATION_90,
                hasHingeAngleSensor = true,
                isInMultiWindowMode = true,
            )
        )
        val presentation = resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = false,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = true,
        )
        assertEquals(AppFoldableDisplayRole.UnknownFoldable, displayContext.foldableDisplayRole)
        assertFalse(presentation.usesInWindowFullscreen)
        assertFalse(presentation.isFullscreen)
        assertFalse(presentation.shouldHandleBackAsExitFullscreen)
        assertFalse(resolvePlayerBackConsumption(isFullscreen = false))
    }

    @Test
    fun `fold and unfold keep user fullscreen intent and drop orientation-generated fullscreen`() {
        val cover = puraCover(
            widthDp = 616,
            heightDp = 421,
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            rotation = Surface.ROTATION_0,
        )
        val inner = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 861,
                currentWindowHeightDp = 609,
                maximumWindowWidthDp = 861,
                maximumWindowHeightDp = 609,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_0,
                displayModeWidthPx = 2584,
                displayModeHeightPx = 1828,
                hasCurrentFoldingFeature = true,
                hasHingeAngleSensor = true,
            )
        )
        val autoOnPhone = resolvePlayerPresentationPolicy(
            displayContext = cover,
            isLandscape = true,
            userFullscreenIntent = false,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertFalse(autoOnPhone.userFullscreenIntent)
        assertEquals(
            false,
            resolveUserFullscreenIntentAfterDisplayRoleChange(
                previousRole = cover.foldableDisplayRole,
                nextRole = inner.foldableDisplayRole,
                previousUserFullscreenIntent = autoOnPhone.userFullscreenIntent,
            ),
        )

        val userOnCover = resolvePlayerPresentationPolicy(
            displayContext = cover,
            isLandscape = true,
            userFullscreenIntent = true,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        val retainedIntent = resolveUserFullscreenIntentAfterDisplayRoleChange(
            previousRole = cover.foldableDisplayRole,
            nextRole = inner.foldableDisplayRole,
            previousUserFullscreenIntent = userOnCover.userFullscreenIntent,
        )
        val afterUnfold = resolvePlayerPresentationPolicy(
            displayContext = inner,
            isLandscape = true,
            userFullscreenIntent = retainedIntent,
            prefersManualFullscreen = false,
            isInMultiWindowMode = false,
        )
        assertTrue(afterUnfold.userFullscreenIntent)
        assertTrue(afterUnfold.isFullscreen)
        assertFalse(afterUnfold.orientationGeneratedFullscreen)
        assertTrue(
            shouldReleaseOrientationLockOnDisplayRoleChange(
                previousRole = cover.foldableDisplayRole,
                nextRole = inner.foldableDisplayRole,
            )
        )
    }

    @Test
    fun `one back press only exits a real fullscreen layer`() {
        assertTrue(resolvePlayerBackConsumption(isFullscreen = true))
        assertFalse(resolvePlayerBackConsumption(isFullscreen = false))
    }

    private fun puraCover(
        widthDp: Int,
        heightDp: Int,
        orientation: Int,
        rotation: Int,
    ): AppDisplayContext = resolveAppDisplayContext(
        AppDisplayContextInput(
            currentWindowWidthDp = widthDp,
            currentWindowHeightDp = heightDp,
            maximumWindowWidthDp = 861,
            maximumWindowHeightDp = 609,
            configurationOrientation = orientation,
            displayRotation = rotation,
            displayModeWidthPx = 1848,
            displayModeHeightPx = 1264,
            hasHingeAngleSensor = true,
        )
    )
}
