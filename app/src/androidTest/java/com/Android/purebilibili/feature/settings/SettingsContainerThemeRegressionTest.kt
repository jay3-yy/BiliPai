package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation3.BiliPaiAndroidXNavDisplay
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.SettingsSharedContainerTransition
import com.android.purebilibili.navigation3.SettingsSharedTransitionLayout
import com.android.purebilibili.navigation3.settingsSharedContainerDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.nav.core.LocalNavEntryTransition
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope

@RunWith(AndroidJUnit4::class)
class SettingsContainerThemeRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun materialContainerOpensFromRowAndReturnsWithCurrentTheme() =
        checkContainerMotion(AppUiStyle.MATERIAL3)

    @Test
    fun miuixContainerOpensFromRowAndReturnsWithCurrentTheme() =
        checkContainerMotion(AppUiStyle.MIUIX)

    private fun checkContainerMotion(style: AppUiStyle) {
        val rowColor = mutableStateOf(Color.Green)
        val sources = SettingsSharedContainerSources()
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.Settings)
        val target = BiliPaiNavKey.AppearanceSettings
        rule.setContent {
            val transition = sources.sources[target]?.let { origin ->
                SettingsSharedContainerTransition(
                    origin = origin,
                    pageColor = { Color.Red },
                    realtimeBlurEnabled = false,
                    blurMotionTier = { MotionTier.Normal },
                )
            }
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = rowColor.value,
                    surfaceContainer = rowColor.value,
                    surfaceContainerLow = Color.Red,
                ),
            ) {
                Box(
                    Modifier.fillMaxSize().testTag("settings-container-host").onGloballyPositioned {
                        sources.hostOrigin = it.positionInRoot()
                        sources.hostSize = it.size
                    }
                ) {
                    SettingsSharedTransitionLayout(enabled = true) {
                        BiliPaiAndroidXNavDisplay(
                            backStack = stack,
                            onBack = { stack.removeLastOrNull() },
                            motion = {
                                entry<BiliPaiNavKey.Settings> {}
                                entry<BiliPaiNavKey.AppearanceSettings>(transition = transition) {}
                            },
                        ) { route ->
                            CompositionLocalProvider(
                                LocalAppUiStyle provides style,
                                LocalSettingsContainerSources provides sources,
                                LocalSettingsContainerOwner provides route,
                            ) {
                                if (route == target) {
                                    val current = LocalNavEntryTransition.current as SettingsSharedContainerTransition
                                    val bounds = settingsSharedContainerDestination(
                                        origin = current.origin,
                                        navScope = LocalNavTransitionScope.current,
                                        color = Color.Red,
                                    )
                                    Box(Modifier.fillMaxSize().then(bounds).background(Color.Red))
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                                        Box(
                                            Modifier.align(Alignment.Center)
                                                .width(120.dp)
                                                .background(rowColor.value),
                                        ) {
                                            SettingsNavigationContainerOrigin(
                                                target = target,
                                                onClick = { stack.add(target) },
                                                flatGroup = style == AppUiStyle.MATERIAL3,
                                            ) { contentModifier, open ->
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("settings-row")
                                                        // 逐帧取色只检查共享层交接，避免把主题按压反馈算入底色。
                                                        .clickable(
                                                            interactionSource = null,
                                                            indication = null,
                                                            onClick = open,
                                                        )
                                                        .then(contentModifier),
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .align(Alignment.TopCenter)
                                                            .width(24.dp)
                                                            .height(6.dp)
                                                            .background(Color.White),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun frameColors(): Pair<Color, Color> {
            val pixels = rule.onNodeWithTag("settings-container-host").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2] to
                pixels[pixels.width / 16, pixels.height / 16]
        }

        data class OpeningFrame(
            val top: Int,
            val center: Color,
            val belowRow: Color,
            val rowContentVisible: Boolean,
        )

        fun openingFrame(): OpeningFrame {
            val pixels = rule.onNodeWithTag("settings-container-host").captureToImage().toPixelMap()
            val top = (0 until pixels.height).first { y ->
                val pixel = pixels[pixels.width / 2, y]
                pixel.red > 0.08f || pixel.green > 0.08f
            }
            val rowContentVisible = (top until pixels.height).any { y ->
                val pixel = pixels[pixels.width / 2, y]
                pixel.red > 0.98f && pixel.green > 0.98f && pixel.blue > 0.98f
            }
            return OpeningFrame(
                top,
                pixels[pixels.width / 2, pixels.height / 2],
                pixels[pixels.width / 2, 5 * pixels.height / 8],
                rowContentVisible,
            )
        }

        val rowTop = openingFrame().top
        assertEquals(Color.Green to Color.Black, frameColors())
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("settings-row").performTouchInput { click() }
        rule.mainClock.advanceTimeByFrame()
        val firstFrame = openingFrame()
        assertEquals(rowTop, firstFrame.top)
        assertTrue("Source content should still be visible after release", firstFrame.rowContentVisible)
        assertEquals(Color.Green to Color.Black, frameColors())
        val openingFrames = List(12) {
            rule.mainClock.advanceTimeByFrame()
            openingFrame()
        }
        val beforeExpansion = openingFrames.takeWhile { it.top == rowTop }
        assertTrue(
            "Source content should stay visible while the container is still at the row: $beforeExpansion",
            beforeExpansion.all { it.rowContentVisible },
        )
        val earlyExpansion = openingFrames.filter { it.top in (9 * rowTop / 10) until rowTop }
        assertTrue("Expected to capture the start of expansion: $openingFrames", earlyExpansion.isNotEmpty())
        assertTrue(
            "The row color should stay stable as the container starts expanding: $earlyExpansion",
            earlyExpansion.all { it.center.green > 0.98f && it.center.red < 0.02f },
        )
        assertTrue(
            "Source content should stay visible before the destination takes over: $earlyExpansion",
            earlyExpansion.all { it.rowContentVisible },
        )
        val midExpansion = openingFrames.filter { it.top in (3 * rowTop / 5)..(3 * rowTop / 4) }
        assertTrue("Expected to capture the container in flight: $openingFrames", midExpansion.isNotEmpty())
        assertTrue(
            "The expanding surface should cover the black page under it: $midExpansion",
            midExpansion.all { (_, _, fill) -> fill.red > 0.5f || fill.green > 0.5f },
        )
        val openingTops = openingFrames.map { it.top }
        assertTrue(
            "Expected the container to move upward without jumping back: $openingTops",
            (listOf(rowTop) + openingTops).zipWithNext().all { (previous, next) -> next <= previous },
        )

        rule.mainClock.autoAdvance = true
        assertEquals(Color.Red to Color.Red, frameColors())
        rule.runOnIdle { rowColor.value = Color.Blue }

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { stack.removeLastOrNull() }
        val returnFrames = List(8) {
            rule.mainClock.advanceTimeBy(32)
            frameColors()
        }
        // The row picks up the new theme as it returns while the corner reveals the parent.
        assertTrue(returnFrames.any { (center, corner) ->
            center.blue > 0.5f && corner.red < 0.08f
        })

        rule.mainClock.autoAdvance = true
        assertEquals(Color.Blue to Color.Black, frameColors())
    }
}
