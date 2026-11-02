package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardVisibility
import com.android.purebilibili.feature.home.components.cards.VideoCardVisibilityState
import com.android.purebilibili.feature.home.components.cards.rememberVideoCardVisibility
import org.junit.Rule
import org.junit.Test

class VideoCardVisibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun homeAndCardAgreeAcrossClippingHidingAndRestoringWithPagePermissionKeptSeparate() {
        var offset by mutableStateOf(220)
        var pageActive by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val visibility = remember { VideoCardVisibilityState() }
                Column {
                    BasicText("home:${visibility.visible && pageActive}")
                    Box(Modifier.size(200.dp).clipToBounds()) {
                        CompositionLocalProvider(LocalHomeCardVisibility provides visibility) {
                            VisibilityCard(Modifier.offset { IntOffset(0, offset) }, pageActive)
                        }
                    }
                }
            }
        }
        assertPermission(false)
        rule.runOnIdle { offset = 0 }
        assertPermission(true)
        // Less than 1% visible remains in the layout but must not enable card work.
        rule.runOnIdle { offset = 199 }
        assertPermission(false)
        rule.runOnIdle { offset = 196 }
        assertPermission(true)
        rule.runOnIdle { pageActive = false }
        assertPermission(false)
        rule.runOnIdle { pageActive = true }
        assertPermission(true)
        rule.runOnIdle { offset = 220 }
        assertPermission(false)
        rule.runOnIdle { offset = 0 }
        assertPermission(true)
    }

    @Test
    fun cardsOutsideHomeKeepIndependentVisibilityEvenForTheSameVideo() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Box(Modifier.size(200.dp).clipToBounds()) {
                        VisibilityCard(label = "visible")
                    }
                    Box(Modifier.size(200.dp).clipToBounds()) {
                        VisibilityCard(Modifier.offset { IntOffset(0, 220) }, label = "hidden")
                    }
                }
            }
        }
        rule.onNodeWithText("visible:true").assertExists()
        rule.onNodeWithText("hidden:false").assertExists()
    }

    private fun assertPermission(allowed: Boolean) {
        rule.onNodeWithText("home:$allowed").assertExists()
        rule.onNodeWithText("card:$allowed", useUnmergedTree = true).assertExists()
    }

    @Composable
    private fun VisibilityCard(
        modifier: Modifier = Modifier,
        pageActive: Boolean = true,
        label: String = "card",
    ) {
        val visibility = rememberVideoCardVisibility("BV_same", 1L)
        Box(modifier.size(200.dp).then(visibility.observerModifier)) {
            BasicText("$label:${visibility.visible && pageActive}")
        }
    }
}
