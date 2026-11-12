package com.android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.ui.components.DanmakuSendDialog
import com.android.purebilibili.feature.video.ui.components.LandscapeDanmakuComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DanmakuComposerUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun commentComposer_restoresDraftAndSelectionWithoutReseedingTheVisibleSession() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            MaterialTheme {
                CommentInputDialog(
                    visible = true,
                    draftKey = "restored-comment",
                    initialText = "旧评论",
                    onDismiss = {},
                    onSend = { _, _, _ -> },
                )
            }
        }
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("折叠前的评论草稿")
        composeTestRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(2, 4))
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNode(hasSetTextAction())
            .assertTextEquals("折叠前的评论草稿")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2, 4)))
    }

    @Test
    fun compactComposer_restoresEditedDraftWithoutReplacingItWithInitialText() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            MaterialTheme {
                DanmakuSendDialog(
                    visible = true,
                    initialText = "旧草稿",
                    onDismiss = {},
                    onSend = { _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("danmaku_compact_input")
            .performTextReplacement("折叠前编辑的草稿")
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithTag("danmaku_compact_input")
            .assertTextEquals("折叠前编辑的草稿")
    }

    @Test
    fun compactComposer_draftFeedbackDoesNotCloseAdvancedSettings() {
        val draft = mutableStateOf("")
        composeTestRule.setContent {
            MaterialTheme {
                DanmakuSendDialog(
                    visible = true,
                    initialText = draft.value,
                    onDraftChange = { text, _ -> draft.value = text },
                    onDismiss = {},
                    onSend = { _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("danmaku_compact_input").performTextReplacement("继续编辑")
        composeTestRule.onNodeWithContentDescription("弹幕样式").performClick()
        composeTestRule.runOnIdle { draft.value = "外部草稿回流" }
        composeTestRule.onNodeWithText("弹幕颜色").assertIsDisplayed()
        composeTestRule.onNodeWithTag("danmaku_compact_input").assertTextEquals("继续编辑")
    }

    @Test
    fun compactComposer_focusesInputAndKeepsAdvancedSettingsCollapsed() {
        composeTestRule.setContent {
            MaterialTheme {
                DanmakuSendDialog(
                    visible = true,
                    initialText = "未发送草稿",
                    onDismiss = {},
                    onSend = { _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("danmaku_compact_input").assertIsFocused()
        composeTestRule.onNodeWithText("未发送草稿").assertIsDisplayed()
        composeTestRule.onNodeWithText("弹幕颜色").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("弹幕样式").performClick()
        composeTestRule.onNodeWithText("弹幕颜色").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("会员渐变色").assertDoesNotExist()
    }

    @Test
    fun compactComposer_materialStylePanelReplacesKeyboardAndSendsSelectedOptions() {
        assertCompactComposerInteraction(AppUiStyle.MATERIAL3, false)
    }

    @Test
    fun compactComposer_miuixStylePanelReplacesKeyboardAndSendsSelectedOptions() {
        assertCompactComposerInteraction(AppUiStyle.MIUIX, false)
    }

    @Test
    fun compactComposer_glassStylePanelReplacesKeyboardAndSendsSelectedOptions() {
        assertCompactComposerInteraction(AppUiStyle.MATERIAL3, true)
    }

    @Test
    fun compactComposer_miuixGlassStylePanelReplacesKeyboardAndSendsSelectedOptions() {
        assertCompactComposerInteraction(AppUiStyle.MIUIX, true)
    }

    private fun waitForCompactKeyboard(visible: Boolean) {
        composeTestRule.waitUntil(5_000) {
            var matches = false
            onView(isRoot()).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                val insets = ViewCompat.getRootWindowInsets(view)
                matches = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            matches
        }
    }

    private fun assertCompactComposerInteraction(style: AppUiStyle, glass: Boolean) {
        var sentText: String? = null
        var sentSelection: Triple<Int, Int, Int>? = null
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                LocalAppUiStyle provides style,
            ) {
                MaterialTheme { MiuixTheme {
                    DanmakuSendDialog(
                        visible = true,
                        initialText = "待发送弹幕",
                        vipColorAvailable = true,
                        onDismiss = {},
                        onSend = { text, color, mode, size, _ ->
                            sentText = text
                            sentSelection = Triple(color, mode, size)
                        },
                    )
                } }
            }
        }
        val input = composeTestRule.onNodeWithTag("danmaku_compact_input")
        composeTestRule.waitUntil(5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        composeTestRule.onNodeWithTag("danmaku_compact_style_panel").assertDoesNotExist()
        waitForCompactKeyboard(true)
        composeTestRule.onNodeWithContentDescription("弹幕样式").performClick()
        waitForCompactKeyboard(false)
        input.assertIsNotFocused()
        composeTestRule.onNodeWithText("顶部").performClick().assertIsSelected()
        composeTestRule.onNodeWithText("小").performClick().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色").performScrollTo().performClick().assertIsSelected()
        input.assertTextEquals("待发送弹幕")
        input.performTouchInput { click() }
        composeTestRule.onNodeWithTag("danmaku_compact_style_panel").assertDoesNotExist()
        waitForCompactKeyboard(true)
        composeTestRule.waitUntil(5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        input.performTextReplacement("  样式已保留  ")
        composeTestRule.onNodeWithContentDescription("弹幕样式").performClick()
        composeTestRule.onNodeWithText("顶部").assertIsSelected()
        composeTestRule.onNodeWithText("小").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("会员渐变色").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("danmaku_compact_send").performClick()
        composeTestRule.runOnIdle {
            assertEquals("样式已保留", sentText)
            assertEquals(Triple(-1, 5, 18), sentSelection)
        }
    }

    @Test
    fun landscapeComposer_nativeStyleSwitchesPreserveDraftAndSendSelectedOptions() {
        assertLandscapeComposerPreservesDraftAndOptions(liquidGlassEnabled = false)
    }

    @Test
    fun landscapeComposer_glassStyleSwitchesPreserveDraftAndSendSelectedOptions() {
        assertLandscapeComposerPreservesDraftAndOptions(liquidGlassEnabled = true)
    }

    @Test
    fun glassPlayerComposer_materialSheetHasNoLightTopStrip() {
        assertGlassPlayerComposerHasContinuousSurface(AppUiStyle.MATERIAL3)
    }

    @Test
    fun glassPlayerComposer_miuixSheetHasNoLightTopStrip() {
        assertGlassPlayerComposerHasContinuousSurface(AppUiStyle.MIUIX)
    }

    private fun assertGlassPlayerComposerHasContinuousSurface(style: AppUiStyle) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = true),
                LocalAppUiStyle provides style,
            ) {
                MaterialTheme { MiuixTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        com.android.purebilibili.feature.video.ui.components.glass.LandscapeDanmakuComposer(
                            visible = true,
                            onDismiss = {},
                            onSend = { _, _, _, _, _ -> },
                            isSending = false,
                            initialColor = 16777215,
                            initialMode = 1,
                            initialFontSize = 25,
                            initialText = "弹幕草稿",
                            initialAttentionCommand = false,
                            onDraftChange = { _, _ -> },
                            onSelectionChange = { _, _, _ -> },
                        )
                    }
                } }
            }
        }
        val input = composeTestRule.onNodeWithTag("danmaku_landscape_composer_input")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        fun assertTopSurface() {
            input.assertIsDisplayed()
            val bounds = input.fetchSemanticsNode().boundsInRoot
            val headerHeight = with(composeTestRule.density) { 48.dp.roundToPx() }
            // Native modal sheets apply their offset outside the tagged node's coordinates.
            // Inspect the actual window above the input, including the host's handle area.
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try {
                for (y in (bounds.top.toInt() - headerHeight).coerceAtLeast(0) until bounds.top.toInt()) {
                    val color = Color(screenshot.getPixel(bounds.left.toInt(), y))
                    assertTrue("Light strip above the $style editor at y=$y: $color", color.luminance() < 0.1f)
                }
            } finally {
                screenshot.recycle()
            }
        }
        assertTopSurface()
        composeTestRule.onNodeWithTag("danmaku_landscape_style_toggle").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onNodeWithText("字体大小").isDisplayed()
        }
        composeTestRule.onNodeWithText("字体大小").assertIsDisplayed()
        assertTopSurface()
        input.assertTextEquals("弹幕草稿")
    }

    private fun assertLandscapeComposerPreservesDraftAndOptions(liquidGlassEnabled: Boolean) {
        val draft = mutableStateOf("")
        val selection = mutableStateOf(Triple(16777215, 1, 25))
        var sentText: String? = null
        var sentSelection: Triple<Int, Int, Int>? = null
        var sentAttention: Boolean? = null
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = liquidGlassEnabled),
            ) {
                MaterialTheme {
                    LandscapeDanmakuComposer(
                        visible = true,
                        onDismiss = {},
                        onSend = { text, color, mode, size, attention ->
                            sentText = text
                            sentSelection = Triple(color, mode, size)
                            sentAttention = attention
                        },
                        isSending = false,
                        initialColor = selection.value.first,
                        initialMode = selection.value.second,
                        initialFontSize = selection.value.third,
                        initialText = draft.value,
                        initialAttentionCommand = false,
                        onDraftChange = { text, _ -> draft.value = text },
                        onSelectionChange = { color, mode, size ->
                            selection.value = Triple(color, mode, size)
                        },
                    )
                }
            }
        }
        val input = composeTestRule.onNodeWithTag("danmaku_landscape_composer_input")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        input.assertIsFocused().performTextReplacement("保留这条弹幕草稿")
        composeTestRule.onNodeWithTag("danmaku_landscape_style_toggle").performClick()
        composeTestRule.onNodeWithText("字体大小").assertIsDisplayed()
        composeTestRule.onNodeWithText("置顶").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色")
            .performScrollTo().performClick().assertIsSelected()
        input.assertTextEquals("保留这条弹幕草稿").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        input.assertIsFocused().assertTextEquals("保留这条弹幕草稿")
        composeTestRule.onNodeWithText("字体大小").assertDoesNotExist()
        composeTestRule.onNodeWithTag("danmaku_landscape_style_toggle").performClick()
        composeTestRule.onNodeWithText("置顶").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色").performScrollTo().assertIsSelected()
        input.assertTextEquals("保留这条弹幕草稿")
        composeTestRule.onNodeWithContentDescription("发送弹幕").performClick()
        composeTestRule.runOnIdle {
            assertEquals("保留这条弹幕草稿", draft.value)
            assertEquals("保留这条弹幕草稿", sentText)
            assertEquals(Triple(16646914, 5, 25), sentSelection)
            assertEquals(false, sentAttention)
        }
    }
}
