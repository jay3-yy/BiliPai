package com.android.purebilibili.feature.video

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.ui.components.DanmakuSendDialog
import com.android.purebilibili.feature.video.ui.components.LandscapeDanmakuComposer
import org.junit.Assert.assertEquals
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
        composeTestRule.onNodeWithText("颜色、位置与大小").performClick()
        composeTestRule.onNodeWithTag("danmaku_compact_input")
            .performTextReplacement("继续编辑")
        composeTestRule.onNodeWithText("颜色").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("颜色").assertDoesNotExist()

        composeTestRule.onNodeWithText("颜色、位置与大小").performClick()
        composeTestRule.onNodeWithText("颜色").assertIsDisplayed()
    }

    @Test
    fun landscapeComposer_nativeStyleSwitchesPreserveDraftAndSendSelectedOptions() {
        assertLandscapeComposerPreservesDraftAndOptions(liquidGlassEnabled = false)
    }

    @Test
    fun landscapeComposer_glassStyleSwitchesPreserveDraftAndSendSelectedOptions() {
        assertLandscapeComposerPreservesDraftAndOptions(liquidGlassEnabled = true)
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
