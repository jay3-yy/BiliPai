package com.android.purebilibili.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedGridColumnPreferenceUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun independentPageGesturesSurviveRotationSplitWindowAndStateRestoration() {
        val portrait = WindowSizeClass(
            WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded, 900.dp, 1200.dp,
        )
        val landscape = portrait.copy(widthDp = 1200.dp, heightDp = 900.dp)
        val narrow = portrait.copy(widthSizeClass = WindowWidthSizeClass.Compact, widthDp = 450.dp)
        val window = mutableStateOf(portrait)
        val settings = HomeSettings(
            gridColumnCount = 6, gridColumnCountWidePortrait = 5, gridColumnCountCompact = 2,
            gridColumnsSharedAcrossPages = false,
        )
        lateinit var preference: FeedGridColumnPreference
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalWindowSizeClass provides window.value) {
                val current = rememberFeedGridColumnPreference(
                    settings = settings,
                    widthSizeClass = window.value.widthSizeClass,
                    defaultColumns = settings.effectiveGridColumnCountForWindow(window.value),
                    bounds = resolveHomeFeedPinchColumnBounds(
                        window.value.widthSizeClass, window.value.widthDp.value.toInt(),
                    ),
                )
                SideEffect { preference = current }
            }
        }
        rule.runOnIdle {
            assertEquals(5, preference.columns)
            preference.onGestureEnd(3)
        }
        rule.runOnIdle { window.value = landscape }
        rule.runOnIdle {
            assertEquals(6, preference.columns)
            preference.onGestureEnd(4)
        }
        rule.runOnIdle { window.value = narrow }
        rule.runOnIdle {
            assertEquals(2, preference.columns)
            preference.onGestureEnd(1)
        }
        rule.runOnIdle { window.value = portrait }
        rule.runOnIdle { assertEquals(3, preference.columns) }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(3, preference.columns) }
        rule.runOnIdle { window.value = landscape }
        rule.runOnIdle { assertEquals(4, preference.columns) }
        rule.runOnIdle { window.value = narrow }
        rule.runOnIdle { assertEquals(1, preference.columns) }
    }
}
