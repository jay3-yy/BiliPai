package com.android.purebilibili.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.SettingsContainerTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec

@RunWith(AndroidJUnit4::class)
class SettingsContainerThemeRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun flatRowFollowsThemeAndCustomBackgroundUntilTheNextClick() =
        checkThemeUpdates(flatGroup = true, uiStyle = AppUiStyle.MATERIAL3)

    @Test
    fun materialGroupFollowsThemeAndCustomBackgroundUntilTheNextClick() =
        checkThemeUpdates(flatGroup = false, uiStyle = AppUiStyle.MATERIAL3)

    @Test
    fun miuixGroupFollowsThemeAndCustomBackgroundUntilTheNextClick() =
        checkThemeUpdates(flatGroup = false, uiStyle = AppUiStyle.MIUIX)

    @Test
    fun materialRootCategoryUpdatesTheReturnContainerAfterThemeChanges() =
        checkThemeUpdates(flatGroup = true, uiStyle = AppUiStyle.MATERIAL3, rootCategory = true)

    @Test
    fun miuixRootCategoryUpdatesTheReturnContainerAfterThemeChanges() =
        checkThemeUpdates(flatGroup = false, uiStyle = AppUiStyle.MIUIX, rootCategory = true)

    private fun checkThemeUpdates(
        flatGroup: Boolean,
        uiStyle: AppUiStyle,
        rootCategory: Boolean = false,
    ) {
        val background = mutableStateOf(Color(0xFF101510))
        val sources = SettingsContainerSources()
        val target = BiliPaiNavKey.AppearanceSettings
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                background = background.value,
                surfaceContainer = background.value,
                surfaceContainerLow = background.value,
            )) {
                CompositionLocalProvider(
                    LocalAppUiStyle provides uiStyle,
                    LocalSettingsContainerSources provides sources,
                    LocalSettingsContainerOwner provides BiliPaiNavKey.Settings,
                ) {
                    Box(Modifier.size(240.dp).onGloballyPositioned {
                        sources.hostOrigin = it.positionInRoot()
                        sources.hostSize = it.size
                    }) {
                        if (rootCategory) {
                            SettingsRootCategoryListSection(
                                categories = listOf(SettingsRootCategory.APPEARANCE_THEME),
                                onCategoryClick = {},
                                onDonateClick = {},
                            )
                        } else {
                            SettingsNavigationContainerOrigin(
                                target = target,
                                onClick = {},
                                flatGroup = flatGroup,
                            ) { click ->
                                Box(Modifier.fillMaxWidth().height(48.dp)
                                    .testTag("appearance-row").clickable(onClick = click))
                            }
                        }
                    }
                }
            }
        }
        fun clickAppearance() {
            if (rootCategory) rule.onNodeWithText("外观与动画").performClick()
            else rule.onNodeWithTag("appearance-row").performClick()
        }
        clickAppearance()
        lateinit var captured: SettingsContainerSource
        rule.runOnIdle {
            captured = requireNotNull(sources.sources[target])
            assertEquals(background.value, captured.color)
            captured.hasOpened = true
        }
        // 深色切浅色、自定义背景及恢复深色，均更新同一次点击的来源。
        for (color in listOf(Color(0xFFF8FAF0), Color(0xFFFFE0EC), Color(0xFF101510))) {
            rule.runOnIdle { background.value = color }
            rule.runOnIdle {
                assertSame(captured, sources.sources[target])
                assertEquals(color, captured.color)
                assertTrue(captured.hasOpened)
                // 嵌套目录或动效选项更新可重建转场，但返回仍使用弹簧。
                val rebuilt = SettingsContainerTransition(captured, pageColor = { background.value })
                assertTrue(rebuilt.motion.programmatic is NavSettleSpec.Spring)
            }
        }
        clickAppearance()
        rule.runOnIdle {
            val recaptured = requireNotNull(sources.sources[target])
            assertNotSame(captured, recaptured)
            assertEquals(background.value, recaptured.color)
            assertFalse(recaptured.hasOpened)
            val opening = SettingsContainerTransition(recaptured, pageColor = { background.value })
            assertTrue(opening.motion.programmatic is NavSettleSpec.Tween)
        }
    }
}
