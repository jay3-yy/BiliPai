package com.android.purebilibili.feature.search

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SearchInputUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun movingSelectionAndUpdatingSuggestionsKeepsTheSameImeSession() {
        val state = TextFieldState(initialText = "abcdef")
        val suggestion = mutableStateOf("推荐词")
        var connection: InputConnection? = null
        var sessionStarts = 0
        var submitted = ""
        val interceptor = PlatformTextInputInterceptor { request, _ ->
            sessionStarts++
            connection = request.createInputConnection(EditorInfo())
            awaitCancellation()
        }
        rule.setContent {
            MaterialTheme {
                InterceptPlatformTextInput(interceptor) {
                    SearchTopBar(
                        textFieldState = state,
                        onBack = {},
                        onSearch = { submitted = it },
                        onClearQuery = {},
                        suggestedKeyword = suggestion.value,
                        autoFocusEnabled = false,
                    )
                }
            }
        }
        rule.onNode(hasSetTextAction()).performClick()
        rule.waitUntil { connection != null }
        rule.runOnIdle {
            checkNotNull(connection).setSelection(2, 2)
            suggestion.value = "更新后的推荐词"
        }
        rule.runOnIdle {
            assertEquals(TextRange(2), state.selection)
            assertEquals("abcdef", state.text.toString())
            assertEquals(1, sessionStarts)
            checkNotNull(connection).commitText("X", 1)
        }
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.runOnIdle {
            assertEquals("abXcdef", state.text.toString())
            assertEquals("abXcdef", submitted)
            assertEquals(1, sessionStarts)
        }
    }

    @Test
    fun suggestionRecompositionDoesNotCommitOrReplaceImeComposition() {
        val state = TextFieldState(initialText = "搜索")
        val suggestion = mutableStateOf("推荐词")
        var connection: InputConnection? = null
        var sessionStarts = 0
        val interceptor = PlatformTextInputInterceptor { request, _ ->
            sessionStarts++
            connection = request.createInputConnection(EditorInfo())
            awaitCancellation()
        }
        rule.setContent {
            MaterialTheme {
                InterceptPlatformTextInput(interceptor) {
                    SearchTopBar(
                        textFieldState = state,
                        onBack = {},
                        onSearch = {},
                        onClearQuery = {},
                        suggestedKeyword = suggestion.value,
                        autoFocusEnabled = false,
                    )
                }
            }
        }
        rule.onNode(hasSetTextAction()).performClick()
        rule.waitUntil { connection != null }
        rule.runOnIdle {
            checkNotNull(connection).setComposingText("zhong", 1)
            suggestion.value = "新的推荐词"
        }
        rule.runOnIdle {
            assertEquals("搜索zhong", state.text.toString())
            assertEquals(TextRange(2, 7), state.composition)
            assertEquals(1, sessionStarts)
            checkNotNull(connection).setComposingText("中国", 1)
            checkNotNull(connection).finishComposingText()
        }
        rule.runOnIdle {
            assertEquals("搜索中国", state.text.toString())
            assertNull(state.composition)
            assertEquals(1, sessionStarts)
        }
    }
}
