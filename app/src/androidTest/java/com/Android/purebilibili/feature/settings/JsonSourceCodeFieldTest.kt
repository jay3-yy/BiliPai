package com.android.purebilibili.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.plugin.json.JsonSourceDiagnostic
import com.android.purebilibili.core.plugin.json.JsonSourceSpan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonSourceCodeFieldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun completingFieldKeepsKeyboardFocusAndCursorBeforeClosingQuote() {
        val initial = """{"field":"owner.n","op":"regex"}"""
        val cursor = initial.indexOf("owner.n") + "owner.n".length
        rule.setContent {
            var source by remember { mutableStateOf(initial) }
            MaterialTheme {
                JsonSourceCodeField(source, { source = it }, "插件 JSON", null, pluginType = "feed")
            }
        }
        val input = rule.onNodeWithContentDescription("插件 JSON")
        input.performTextInputSelection(TextRange(cursor))
        rule.onNodeWithText("owner.name", useUnmergedTree = true).performClick()
        input.assertIsFocused().assertTextEquals(initial.replace("owner.n", "owner.name"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(cursor + 3)))
        input.performKeyInput { pressKey(Key.Backspace) }
        input.assertTextEquals(initial.replace("owner.n", "owner.nam"))
    }

    @Test fun jumpToErrorSelectsExactSpanAndReportsErrorToAccessibility() {
        val source = """{"field":"wrong"}"""
        val start = source.indexOf("\"wrong\"")
        val issue = JsonSourceDiagnostic("不支持的字段", JsonSourceSpan(start, start + 7), 1, start + 1)
        rule.setContent {
            MaterialTheme { JsonSourceCodeField(source, {}, "插件 JSON", issue) }
        }
        rule.onNodeWithText("跳到错误").performClick()
        rule.onNodeWithContentDescription("插件 JSON")
            .assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(start, start + 7)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "不支持的字段"))
    }
}
