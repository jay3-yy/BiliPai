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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SearchInputUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun entryAutoFocusStartsOneImeSessionAndDoesNotReturnAfterDismissal() {
        val state = TextFieldState()
        val entryReady = mutableStateOf(false)
        val consumed = mutableStateOf(false)
        var sessionStarts = 0
        lateinit var focusManager: FocusManager
        val interceptor = PlatformTextInputInterceptor { _, _ ->
            sessionStarts++
            awaitCancellation()
        }
        rule.setContent {
            focusManager = LocalFocusManager.current
            MaterialTheme {
                InterceptPlatformTextInput(interceptor) {
                    SearchTopBar(
                        textFieldState = state,
                        onBack = {},
                        onSearch = {},
                        onClearQuery = {},
                        onFocusChanged = { if (it) consumed.value = true },
                        autoFocusEnabled = shouldAutoFocusSearchField(
                            startupSettled = entryReady.value,
                            query = state.text.toString(),
                            autoFocusConsumed = consumed.value,
                        ),
                    )
                }
            }
        }
        rule.runOnIdle { assertEquals(0, sessionStarts) }
        rule.runOnIdle { entryReady.value = true }
        rule.waitUntil { sessionStarts == 1 }
        rule.onNode(hasSetTextAction()).assertIsFocused()
        rule.runOnIdle {
            focusManager.clearFocus(force = true)
            entryReady.value = false
        }
        rule.runOnIdle { entryReady.value = true }
        rule.onNode(hasSetTextAction()).assertIsNotFocused()
        rule.runOnIdle { assertEquals(1, sessionStarts) }
    }

    @Test
    fun restoringFocusDoesNotRequestEditNavigationButTouchDoes() {
        val state = TextFieldState(initialText = "搜索词")
        val focusRequester = FocusRequester()
        var editRequests = 0
        rule.setContent {
            MaterialTheme {
                SearchTopBar(
                    textFieldState = state,
                    onBack = {},
                    onSearch = {},
                    onClearQuery = {},
                    focusRequester = focusRequester,
                    autoFocusEnabled = false,
                    onEditRequested = { editRequests++ },
                )
            }
        }
        rule.runOnIdle { focusRequester.requestFocus() }
        rule.runOnIdle { assertEquals(0, editRequests) }
        rule.onNode(hasSetTextAction()).performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, editRequests) }
    }

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
