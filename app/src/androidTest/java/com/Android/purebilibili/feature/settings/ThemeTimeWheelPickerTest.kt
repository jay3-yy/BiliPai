package com.android.purebilibili.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.feature.settings.screen.ThemeTimeWheelPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTimeWheelPickerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun centeredSelectionStartsAtSavedTimeAndWrapsHoursAndMinutesIndependently() {
        val hour = mutableIntStateOf(23)
        val minute = mutableIntStateOf(59)
        rule.setContent {
            MaterialTheme {
                ProvideAppThemeConfig(AppThemeConfig(hapticFeedbackEnabled = false)) {
                    ThemeTimeWheelPicker(hour.intValue, minute.intValue,
                        { hour.intValue = it }, { minute.intValue = it }, {}, {})
                }
            }
        }
        rule.runOnIdle {
            assertEquals(23, hour.intValue)
            assertEquals(59, minute.intValue)
        }
        rule.onNodeWithContentDescription("小时").performCustomAccessibilityActionWithLabel("增加小时")
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, hour.intValue); assertEquals(59, minute.intValue) }
        rule.onNodeWithContentDescription("分钟").performCustomAccessibilityActionWithLabel("增加分钟")
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, hour.intValue); assertEquals(0, minute.intValue) }
        rule.onNodeWithContentDescription("小时").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00"),
        )
    }

    @Test
    fun quickSwipeChangesCenteredValueAndSettlesWithScrollingCleared() {
        val hour = mutableIntStateOf(7)
        val minute = mutableIntStateOf(15)
        val changes = mutableListOf<Int>()
        val scrolling = mutableListOf<Boolean>()
        rule.setContent {
            MaterialTheme {
                ProvideAppThemeConfig(AppThemeConfig(hapticFeedbackEnabled = false)) {
                    ThemeTimeWheelPicker(hour.intValue, minute.intValue,
                        { hour.intValue = it }, { minute.intValue = it; changes.add(it) },
                        {}, { scrolling.add(it) })
                }
            }
        }
        rule.onNodeWithContentDescription("分钟").performTouchInput { swipeUp(durationMillis = 100) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(7, hour.intValue)
            assertTrue(changes.any { it != 15 })
            assertTrue(scrolling.contains(true))
            assertFalse(scrolling.last())
            assertTrue(minute.intValue in 0..59)
        }
    }
}
