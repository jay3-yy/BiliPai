package com.android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.ui.components.DanmakuSendDialogContent
import com.android.purebilibili.feature.video.ui.components.LandscapeDanmakuComposerEditor
import com.android.purebilibili.feature.video.ui.components.glass.LandscapeDanmakuComposerEditor as GlassComposerEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 直接约束键盘上方的剩余区域；系统 IME 弹出流程由宿主负责。 */
@RunWith(AndroidJUnit4::class)
class DanmakuSplitWindowUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun landscapeInputAndSendStayUsableAcrossResizeAndMaterials() {
        val viewport = mutableStateOf(320 to 200)
        val glass = mutableStateOf(false)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        var sent = ""
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides null,
                LocalAppUiStyle provides style.value,
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
            ) {
                MaterialTheme { MiuixTheme {
                    Box(Modifier.size(viewport.value.first.dp, viewport.value.second.dp)
                        .consumeWindowInsets(WindowInsets.safeDrawing).testTag("composer_viewport")) {
                        if (glass.value) {
                            GlassComposerEditor(
                                visible = true, onSend = { text, _, _, _, _ -> sent = text },
                                isSending = false, initialColor = 0xFFFFFF, initialMode = 1,
                                initialFontSize = 25, initialText = "草稿", initialAttentionCommand = false,
                                onDraftChange = { _, _ -> }, onSelectionChange = { _, _, _ -> },
                            )
                        } else {
                            LandscapeDanmakuComposerEditor(
                                visible = true, onSend = { text, _, _, _, _ -> sent = text },
                                isSending = false, initialColor = 0xFFFFFF, initialMode = 1,
                                initialFontSize = 25, initialText = "草稿", initialAttentionCommand = false,
                                onDraftChange = { _, _ -> }, onSelectionChange = { _, _, _ -> },
                            )
                        }
                    }
                } }
            }
        }
        for (uiStyle in listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX)) {
            for (useGlass in listOf(false, true)) {
                rule.runOnIdle { style.value = uiStyle; glass.value = useGlass; viewport.value = 320 to 200 }
                rule.onNodeWithTag("danmaku_landscape_composer_input").performTextReplacement("分屏草稿")
                for (size in listOf(320 to 200, 220 to 160, 320 to 200)) {
                    rule.runOnIdle { viewport.value = size }
                    rule.onNodeWithTag("danmaku_landscape_composer_input").assertIsDisplayed().assertTextEquals("分屏草稿")
                    val host = rule.onNodeWithTag("composer_viewport").fetchSemanticsNode().boundsInRoot
                    val send = rule.onNodeWithTag("danmaku_landscape_send").assertIsDisplayed().assertIsEnabled()
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(send.left >= host.left - 1f && send.right <= host.right + 1f)
                    assertTrue(send.top >= host.top - 1f && send.bottom <= host.bottom + 1f)
                }
                rule.onNodeWithTag("danmaku_landscape_send").performClick()
                rule.runOnIdle { assertEquals("分屏草稿", sent) }
            }
        }
    }

    @Test
    fun compactComposerPinsSendWhileAvailableHeightChanges() {
        val height = mutableStateOf(280)
        rule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides null) {
                MaterialTheme {
                    Box(Modifier.size(280.dp, height.value.dp).consumeWindowInsets(WindowInsets.safeDrawing)
                        .testTag("composer_viewport")) {
                        DanmakuSendDialogContent(
                            visible = true, onSend = { _, _, _, _, _ -> }, initialText = "保留草稿",
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("danmaku_compact_input").performTextReplacement("键盘上方继续发送")
        for (remainingHeight in listOf(280, 180, 280)) {
            rule.runOnIdle { height.value = remainingHeight }
            rule.onNodeWithTag("danmaku_compact_input").assertTextEquals("键盘上方继续发送")
            val host = rule.onNodeWithTag("composer_viewport").fetchSemanticsNode().boundsInRoot
            val send = rule.onNodeWithTag("danmaku_compact_send").assertIsDisplayed().assertIsEnabled()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(send.top >= host.top - 1f && send.bottom <= host.bottom + 1f)
        }
    }
}
