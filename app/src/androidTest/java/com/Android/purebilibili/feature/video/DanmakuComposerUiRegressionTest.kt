package com.android.purebilibili.feature.video

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.feature.video.ui.components.PlainLandscapeDanmakuComposer
import java.io.File
import com.android.purebilibili.feature.video.ui.components.DanmakuSendDialog
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
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
    fun plainLandscapeComposer_styleAndKeyboardSwitchesPreserveDraftAndSelection() {
        val draft = mutableStateOf("")
        composeTestRule.setContent {
            MaterialTheme {
                PlainLandscapeDanmakuComposer(
                    visible = true,
                    onDismiss = {},
                    onSend = { _, _, _, _, _ -> },
                    isSending = false,
                    initialColor = 16777215,
                    initialMode = 1,
                    initialFontSize = 25,
                    initialText = draft.value,
                    initialAttentionCommand = false,
                    onDraftChange = { text, _ -> draft.value = text },
                    onSelectionChange = { _, _, _ -> },
                )
            }
        }
        val input = composeTestRule.onNodeWithTag("danmaku_landscape_composer_input")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            input.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        input.assertIsFocused().performTextInput("保留这条弹幕草稿")
        capturePlayerScreenshot("landscape-composer-input")
        composeTestRule.onNodeWithTag("danmaku_landscape_style_toggle").performClick()
        input.assertTextEquals("保留这条弹幕草稿")
        composeTestRule.onNodeWithText("字体大小").assertIsDisplayed()
        composeTestRule.onNodeWithText("弹幕位置").assertIsDisplayed()
        composeTestRule.onNodeWithText("弹幕颜色").assertIsDisplayed()
        composeTestRule.onNodeWithText("置顶").performClick().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色").performClick().assertIsSelected()
        capturePlayerScreenshot("landscape-composer-style")
        input.performClick().assertIsFocused().assertTextEquals("保留这条弹幕草稿")
        composeTestRule.onNodeWithText("字体大小").assertDoesNotExist()
        capturePlayerScreenshot("landscape-composer-return-to-input")
        composeTestRule.onNodeWithTag("danmaku_landscape_style_toggle").performClick()
        composeTestRule.onNodeWithText("置顶").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("红色").assertIsSelected()
        input.assertTextEquals("保留这条弹幕草稿")
    }

    private fun capturePlayerScreenshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("playerScreenshotDir") ?: return
        composeTestRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = File(directory).apply { mkdirs() }.resolve("$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
