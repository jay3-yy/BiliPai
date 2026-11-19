package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DonationPreviewMotionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun dialogContentTravelsWithTheContainerAndClosingRestoresTheSourceRow() {
        val origin = DonationDialogOrigin()
        val show = mutableStateOf(false)
        val progress = mutableStateOf(0f)
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides AppUiStyle.MATERIAL3,
                    LocalComponentMotionEnabled provides true,
                    LocalDonationDialogOrigin provides origin,
                ) {
                    Box(Modifier.size(240.dp, 180.dp).background(Color.Black).testTag("donation-motion")) {
                        Box(Modifier.align(Alignment.BottomStart).width(80.dp)) {
                            DonationDialogOrigin {
                                Box(Modifier.fillMaxWidth().height(32.dp).background(Color.Red))
                            }
                        }
                        if (show.value) {
                            DonationDialogContainer(
                                origin = origin,
                                onPositioned = {},
                                progress = { progress.value },
                                previewProgress = { 0f },
                                compactRadiusPx = 0f,
                                previewShape = RoundedCornerShape(0.dp),
                                modifier = Modifier.align(Alignment.TopEnd).size(100.dp, 88.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(18.dp).background(Color.Blue))
                                }
                            }
                        }
                    }
                }
            }
        }

        fun sample(x: Int, y: Int): Color {
            val image = rule.onNodeWithTag("donation-motion").captureToImage().toPixelMap()
            return image[image.width * x / 240, image.height * y / 180]
        }

        assertTrue(sample(40, 164).red > 0.9f)
        rule.runOnIdle { show.value = true; progress.value = 0.5f }
        val travelling = sample(115, 104)
        assertTrue(travelling.blue > travelling.red + 0.2f)
        assertTrue(sample(190, 44).blue < 0.1f)

        rule.runOnIdle { progress.value = 1f }
        assertTrue(sample(190, 44).blue > 0.9f)
        rule.runOnIdle { show.value = false }
        assertTrue(sample(40, 164).red > 0.9f)
    }

    @Test
    fun backCanInterruptOpeningCancelRestoreAndCommitWithoutRemountingEitherContent() {
        lateinit var motion: DonationPreviewMotion
        var cardMounts = 0
        var previewMounts = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                val current = rememberDonationPreviewMotion()
                SideEffect { motion = current }
                DonationPreviewContainer(
                    progress = { current.progress.value },
                    previewActive = current.expanded,
                    compactMaxWidth = 280.dp,
                    compact = {
                        DisposableEffect(Unit) { cardMounts++; onDispose {} }
                        Box(Modifier.height(220.dp))
                    },
                    preview = {
                        DisposableEffect(Unit) { previewMounts++; onDispose {} }
                        Box(Modifier.fillMaxSize())
                    },
                )
            }
        }
        rule.runOnIdle { motion.open() }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle {
            val interrupted = motion.progress.value
            assertTrue(interrupted > 0f && interrupted < 1f)
            motion.onBackStarted()
            motion.onBackProgress(0.4f)
            assertEquals(interrupted * 0.6f, motion.progress.value, 0.0001f)
            motion.onBackCancelled()
        }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle {
            assertTrue(motion.expanded)
            assertEquals(1f, motion.progress.value, 0.0001f)
            motion.onBackStarted()
            motion.onBackProgress(0.75f)
            motion.close()
        }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle {
            assertFalse(motion.expanded)
            assertEquals(0f, motion.progress.value, 0.0001f)
            assertEquals(1, cardMounts)
            assertEquals(1, previewMounts)
        }
    }

    @Test
    fun disablingMotionDuringOpeningSnapsToCurrentTargetAndKeepsOrdinaryReturn() {
        val enabled = mutableStateOf(true)
        lateinit var motion: DonationPreviewMotion
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalComponentMotionEnabled provides enabled.value) {
                val current = rememberDonationPreviewMotion()
                SideEffect { motion = current }
            }
        }
        rule.runOnIdle { motion.open() }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle { enabled.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertTrue(motion.expanded)
            assertFalse(motion.predictive)
            assertEquals(1f, motion.progress.value, 0.0001f)
            motion.close()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertFalse(motion.expanded)
            assertEquals(0f, motion.progress.value, 0.0001f)
        }
    }
}
