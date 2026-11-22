package com.android.bilipai.tv.ui

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.android.bilipai.tv.TvUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvSearchInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardEnterSubmitsTheQuery() {
        var submitted = ""
        compose.setContent { TvTheme { TvSearchInput(TvUiState(), FocusRequester(), onSearch = { submitted = it }) } }
        compose.onNodeWithTag("tv-search-input").assertIsFocused().performTextInput("BiliPai")
        compose.onNodeWithTag("tv-search-input").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("BiliPai", submitted) }
    }

    @Test fun downMovesToSearchAndConfirmationSubmits() {
        var submitted = ""
        compose.setContent { TvTheme { TvSearchInput(TvUiState(), FocusRequester(), onSearch = { submitted = it }) } }
        compose.onNodeWithTag("tv-search-input").performTextInput("BiliPai")
        compose.onNodeWithTag("tv-search-input").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("搜索").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("BiliPai", submitted) }
    }
}
