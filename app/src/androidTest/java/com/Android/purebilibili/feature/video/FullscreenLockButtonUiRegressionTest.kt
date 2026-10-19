package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.ui.overlay.FullscreenLockButton
import com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop
import com.android.purebilibili.feature.video.ui.overlay.PlayerControlsBackdropHost
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenLockButtonVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

@RunWith(AndroidJUnit4::class)
class FullscreenLockButtonUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    private data class Scenario(
        val style: AppUiStyle = AppUiStyle.MATERIAL3,
        val dark: Boolean = false,
        val video: Color = Color.Black,
        // Native, glass without capture, and glass with a real captured sibling layer.
        val material: Int = 0,
    )

    @Test
    fun lockRemainsReadableAndToggleableAcrossThemesAndMediaMaterials() {
        val scenario = mutableStateOf(Scenario())
        val locked = mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            val current = scenario.value
            val nativeColors = if (current.dark) miuixDarkColorScheme() else miuixLightColorScheme()
            val controller = remember(current.dark) {
                ThemeController(
                    colorSchemeMode = if (current.dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                    lightColors = nativeColors,
                    darkColors = nativeColors,
                    isDark = current.dark,
                )
            }
            CompositionLocalProvider(
                LocalAppUiStyle provides current.style,
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = current.material != 0),
            ) {
                MaterialTheme(colorScheme = if (current.dark) darkColorScheme() else lightColorScheme()) {
                    MiuixTheme(controller = controller) {
                        val backdrop = rememberLayerBackdrop()
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(current.video))
                            CompositionLocalProvider(
                                LocalPlayerPageBackdrop provides backdrop.takeIf { current.material == 2 },
                            ) {
                                PlayerControlsBackdropHost(enabled = true) {
                                    FullscreenLockButton(
                                        visualState = resolveFullscreenLockButtonVisualState(locked.value),
                                        onClick = {
                                            clicks++
                                            locked.value = !locked.value
                                        },
                                        cornerRadiusDp = 24,
                                        iconSizeDp = 24,
                                        modifier = Modifier.testTag("lock_surface"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        for (style in AppUiStyle.entries) {
            for (dark in listOf(false, true)) {
                for (material in 0..2) {
                    val mediaBackings = listOf(Color.Black, Color.White).map { video ->
                        val current = Scenario(style, dark, video, material)
                        rule.runOnIdle {
                            scenario.value = current
                            locked.value = false
                        }
                        val unlockedBacking = assertRenderedContrast(locked = false, scenario = current)
                        rule.onNodeWithContentDescription("未锁定")
                            .assertIsDisplayed().assertHasClickAction().performClick()
                        val lockedBacking = assertRenderedContrast(locked = true, scenario = current)
                        rule.onNodeWithContentDescription("已锁定")
                            .assertIsDisplayed().assertHasClickAction().performClick()
                        rule.runOnIdle { assertTrue(!locked.value) }
                        listOf(unlockedBacking, lockedBacking)
                    }
                    if (material != 0) {
                        for (state in 0..1) {
                            val blackBacking = mediaBackings[0][state]
                            val whiteBacking = mediaBackings[1][state]
                            // Both fallback and captured glass must transmit the video.
                            // An opaque dark/light plate could pass contrast but not this.
                            assertTrue(
                                "$style dark=$dark material=$material locked=${state == 1} " +
                                    "must transmit video: black=$blackBacking white=$whiteBacking",
                                whiteBacking - blackBacking > 0.02f,
                            )
                        }
                    }
                }
            }
        }
        rule.runOnIdle { assertEquals(AppUiStyle.entries.size * 2 * 2 * 3 * 2, clicks) }
    }

    private fun assertRenderedContrast(locked: Boolean, scenario: Scenario): Float {
        val pixels = rule.onNodeWithTag("lock_surface").captureToImage().toPixelMap()
        // Sample inside the backing but outside the centered 24dp glyph, not the video.
        val backing = pixels[pixels.width / 8, pixels.height / 2].luminance()
        val darkGlyph = locked && scenario.material == 0
        var glyph = if (darkGlyph) 1f else 0f
        for (y in pixels.height / 3 until pixels.height * 2 / 3) {
            for (x in pixels.width / 3 until pixels.width * 2 / 3) {
                val luminance = pixels[x, y].luminance()
                glyph = if (darkGlyph) minOf(glyph, luminance) else maxOf(glyph, luminance)
            }
        }
        val contrast = (maxOf(glyph, backing) + 0.05f) / (minOf(glyph, backing) + 0.05f)
        assertTrue("$scenario locked=$locked glyph contrast=$contrast", contrast >= 4.5f)
        assertTrue(
            "$scenario locked=$locked glyph=$glyph",
            if (darkGlyph) glyph <= 0.1f else glyph >= 0.9f,
        )
        // Only native locked mode inverts to a light plate; glass stays dark and translucent.
        assertTrue(
            "$scenario locked=$locked backing=$backing",
            if (darkGlyph) backing >= 0.9f else backing <= 0.14f,
        )
        return backing
    }
}
