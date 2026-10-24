package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.android.purebilibili.core.ui.findHostActivity

@RunWith(AndroidJUnit4::class)
class ThemeColorTransitionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun themeDefaultDoesNotOverwritePlayerSystemIconsAfterThemeRemount() {
        val generation = mutableIntStateOf(0)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val fullscreen = mutableStateOf(false)
        lateinit var controller: androidx.core.view.WindowInsetsControllerCompat
        rule.setContent {
            // A fresh theme models the default effect restarting with a recreated host view.
            androidx.compose.runtime.key(generation.intValue) {
                PureBiliBiliTheme(
                    uiStyle = style.value,
                    darkTheme = false,
                    liquidGlassEnabled = false,
                    themeTransitionEnabled = false,
                ) {
                    val view = androidx.compose.ui.platform.LocalView.current
                    val window = checkNotNull(view.context.findHostActivity()).window
                    val spec = com.android.purebilibili.feature.video.screen.resolveVideoDetailSystemBarsApplySpec(
                        visibilityPolicy = com.android.purebilibili.feature.video.screen.resolveVideoDetailSystemBarsVisibilityPolicy(
                            isFullscreenMode = fullscreen.value,
                            hideVideoPageStatusBar = false,
                            isInPipMode = false,
                            isScreenActive = true,
                        ),
                        useTabletLayout = false,
                        isLightBackground = true,
                        backgroundColor = android.graphics.Color.WHITE,
                        transparentColor = android.graphics.Color.TRANSPARENT,
                        blackColor = android.graphics.Color.BLACK,
                        transientBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                    )
                    controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    com.android.purebilibili.feature.video.screen.VideoDetailSystemBarsEffect(
                        view = view,
                        window = window,
                        insetsController = controller,
                        isScreenActive = true,
                        spec = spec,
                    )
                }
            }
        }
        rule.runOnIdle {
            org.junit.Assert.assertFalse(controller.isAppearanceLightStatusBars)
        }
        for (nativeStyle in AppUiStyle.entries) {
            for (isFullscreen in listOf(true, false)) {
                rule.runOnIdle {
                    style.value = nativeStyle
                    fullscreen.value = isFullscreen
                    generation.intValue++
                }
                rule.waitForIdle()
                rule.runOnIdle {
                    org.junit.Assert.assertFalse(
                        "Player must retain light system icons after $nativeStyle fullscreen=$isFullscreen",
                        controller.isAppearanceLightStatusBars,
                    )
                }
            }
        }
    }

    @Test
    fun playerReassertsIconsAfterConfigurationRelayoutButReleasesInactiveWindow() {
        val orientation = mutableIntStateOf(android.content.res.Configuration.ORIENTATION_PORTRAIT)
        val active = mutableStateOf(true)
        val windowResetRevision = mutableIntStateOf(0)
        val spec = com.android.purebilibili.feature.video.screen.VideoDetailSystemBarsApplySpec(
            hiddenBars = com.android.purebilibili.feature.video.screen.VideoDetailHiddenSystemBars.NONE,
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
            statusBarColor = android.graphics.Color.TRANSPARENT,
            navigationBarColor = android.graphics.Color.TRANSPARENT,
            lightStatusBars = false,
            lightNavigationBars = false,
        )
        lateinit var controller: androidx.core.view.WindowInsetsControllerCompat
        rule.setContent {
            val hostConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
            val configuration = androidx.compose.runtime.remember(orientation.intValue) {
                android.content.res.Configuration(hostConfiguration).apply {
                    this.orientation = orientation.intValue
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
            ) {
                val view = androidx.compose.ui.platform.LocalView.current
                val window = checkNotNull(view.context.findHostActivity()).window
                controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                com.android.purebilibili.feature.video.screen.VideoDetailSystemBarsEffect(
                    view = view,
                    window = window,
                    insetsController = controller,
                    isScreenActive = active.value,
                    spec = spec,
                )
                if (windowResetRevision.intValue > 0) {
                    // Model decor restoring its light theme after the page's Compose commit.
                    SideEffect { controller.isAppearanceLightStatusBars = true }
                }
            }
        }
        rule.runOnIdle { org.junit.Assert.assertFalse(controller.isAppearanceLightStatusBars) }
        for (nextOrientation in listOf(
            android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            android.content.res.Configuration.ORIENTATION_PORTRAIT,
        )) {
            rule.runOnIdle {
                orientation.intValue = nextOrientation
                windowResetRevision.intValue++
            }
            rule.waitForIdle()
            rule.runOnIdle { org.junit.Assert.assertFalse(controller.isAppearanceLightStatusBars) }
        }
        rule.runOnIdle {
            active.value = false
            orientation.intValue = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            windowResetRevision.intValue++
        }
        rule.waitForIdle()
        rule.runOnIdle { org.junit.Assert.assertTrue(controller.isAppearanceLightStatusBars) }
    }

    private fun palette(dark: Boolean): AppThemePalette {
        val material = if (dark) darkColorScheme() else lightColorScheme()
        return AppThemePalette(
            material = material,
            miuix = resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(material), dark),
            liquidAccent = if (dark) Color(0xFF34C759) else Color(0xFF007AFF),
        )
    }

    @Test
    fun colorsShareProgressAndReverseToExactEndpointWithoutRemountingContent() {
        val light = palette(false)
        val dark = palette(true)
        val target = mutableStateOf(light)
        lateinit var visible: AppThemePalette
        var mounts = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val frame = rememberAnimatedAppThemePalette(target.value, enabled = true)
            androidx.compose.runtime.DisposableEffect(Unit) {
                mounts++
                onDispose { }
            }
            SideEffect { visible = frame }
        }
        rule.runOnIdle {
            assertEquals(light.material.background, visible.material.background)
            assertEquals(light.liquidAccent, visible.liquidAccent)
            target.value = dark
        }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle {
            assertNotEquals(light.material.background, visible.material.background)
            assertNotEquals(dark.material.background, visible.material.background)
            // Matching endpoint roles must remain identical on every intermediate frame.
            assertEquals(visible.material.primary, visible.miuix.primary)
            assertNotEquals(light.liquidAccent, visible.liquidAccent)
            assertNotEquals(dark.liquidAccent, visible.liquidAccent)
            target.value = light
        }
        rule.mainClock.advanceTimeBy(APP_THEME_COLOR_TRANSITION_DURATION_MS.toLong() + 64)
        rule.runOnIdle {
            assertEquals(light.material.background, visible.material.background)
            assertEquals(light.material.onSurface, visible.material.onSurface)
            assertEquals(light.miuix.surface, visible.miuix.surface)
            assertEquals(light.liquidAccent, visible.liquidAccent)
            assertEquals(1, mounts)
        }
    }

    @Test
    fun disablingMotionDuringTransitionImmediatelyExposesExactTarget() {
        val light = palette(false)
        val dark = palette(true)
        val target = mutableStateOf(light)
        val enabled = mutableStateOf(true)
        lateinit var visible: AppThemePalette
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val frame = rememberAnimatedAppThemePalette(target.value, enabled.value)
            SideEffect { visible = frame }
        }
        rule.runOnIdle { target.value = dark }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { enabled.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertEquals(dark, visible)
            target.value = light
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(light, visible) }
    }

    @Test
    fun disabledMotionDoesNotKeepRecomposingWhenEndpointInstancesChange() {
        val refresh = mutableIntStateOf(0)
        var compositions = 0
        lateinit var visible: AppThemePalette
        lateinit var latestTarget: AppThemePalette
        rule.mainClock.autoAdvance = false
        rule.setContent {
            // Model a resource palette factory returning new but visually identical objects.
            refresh.intValue
            val target = palette(false)
            val frame = rememberAnimatedAppThemePalette(target, enabled = false)
            SideEffect {
                compositions++
                latestTarget = target
                visible = frame
            }
        }
        rule.runOnIdle {
            assertEquals(1, compositions)
            org.junit.Assert.assertSame(latestTarget, visible)
            refresh.intValue++
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle {
            assertEquals("Static themes must not install a self-invalidating transition", 2, compositions)
            org.junit.Assert.assertSame(latestTarget, visible)
        }
    }

    @Test
    fun rootWallpaperThemesSettleAfterParentRefreshAndRoleEditsInBothRenderers() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31)
        val source = mutableStateOf(com.android.purebilibili.feature.settings.Md3ColorSource.FOLLOW_WALLPAPER)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val motion = mutableStateOf(true)
        val refresh = mutableIntStateOf(0)
        val edited = mapOf(com.android.purebilibili.core.store.WallpaperPaletteRole.PRIMARY to "#007AFF")
        val overrides = com.android.purebilibili.core.store.WallpaperPaletteOverrides(edited, edited)
        var compositions = 0
        var visibleAccent = Color.Unspecified
        var materialAccent = Color.Unspecified
        var miuixAccent = Color.Unspecified
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PureBiliBiliTheme(
                uiStyle = style.value,
                md3ColorSource = source.value,
                darkTheme = false,
                liquidGlassEnabled = false,
                themeTransitionEnabled = motion.value,
                themeColorIndex = refresh.intValue,
                md3CustomColorHex = "#FF5722",
                wallpaperPaletteOverrides = overrides,
            ) {
                val material = androidx.compose.material3.MaterialTheme.colorScheme.primary
                val miuix = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
                val accent = com.android.purebilibili.core.ui.AppContentColors.accent()
                SideEffect {
                    compositions++
                    visibleAccent = accent
                    materialAccent = material
                    miuixAccent = miuix
                }
            }
        }
        for (nextSource in listOf(
            com.android.purebilibili.feature.settings.Md3ColorSource.FOLLOW_WALLPAPER,
            com.android.purebilibili.feature.settings.Md3ColorSource.WALLPAPER_RETONED,
            com.android.purebilibili.feature.settings.Md3ColorSource.CUSTOM,
        )) for (nextStyle in AppUiStyle.entries) for (nextMotion in listOf(true, false)) {
            rule.runOnIdle {
                source.value = nextSource
                style.value = nextStyle
                motion.value = nextMotion
                // Forces the actual theme entry to recompose while keeping its system seed.
                refresh.intValue++
            }
            rule.mainClock.advanceTimeBy(1_000)
            var settledCount = 0
            rule.runOnIdle {
                settledCount = compositions
                assertEquals(materialAccent, miuixAccent)
                assertEquals(materialAccent, visibleAccent)
                if (nextSource == com.android.purebilibili.feature.settings.Md3ColorSource.WALLPAPER_RETONED) {
                    assertEquals(Color(0xFF007AFF), visibleAccent)
                } else if (nextSource == com.android.purebilibili.feature.settings.Md3ColorSource.CUSTOM) {
                    assertEquals(Color(0xFFFF5722), visibleAccent)
                }
            }
            rule.mainClock.advanceTimeBy(1_000)
            rule.runOnIdle {
                assertEquals("An idle wallpaper theme must stop producing frames", settledCount, compositions)
            }
        }
    }
}
