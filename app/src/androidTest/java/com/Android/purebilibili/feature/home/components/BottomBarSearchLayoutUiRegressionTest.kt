package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.store.BottomBarSearchLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarSearchLayoutUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun reversingTargetKeepsSearchIconAndTextAtTheCurrentVisualProgress() {
        val expanded = mutableStateOf(true)
        val progress = mutableFloatStateOf(0.5f)
        val width = mutableStateOf(160.dp)
        val glass = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.width(width.value).height(56.dp).testTag("content")) {
                    BiliPaiBottomBarSearchVisualContent(
                        expanded = expanded.value,
                        query = "",
                        onQueryChange = {},
                        onSubmit = {},
                        contentColor = Color.Black,
                        accentColor = Color.Blue,
                        iconScale = { 1f },
                        fieldAlpha = { progress.floatValue },
                        interactive = false,
                        iconStyle = SharedFloatingBottomBarIconStyle.MATERIAL,
                        glassSearchVisuals = glass.value,
                    )
                }
            }
        }
        for (useGlass in listOf(true, false)) {
            rule.runOnIdle {
                glass.value = useGlass
                expanded.value = true
                progress.floatValue = 0.5f
                width.value = 160.dp
            }
            val before = rule.onNodeWithContentDescription("搜索").fetchSemanticsNode().boundsInRoot
            rule.runOnIdle { expanded.value = false }
            val reversed = rule.onNodeWithContentDescription("搜索").fetchSemanticsNode().boundsInRoot
            assertEquals(before.left, reversed.left, 0.01f)
            rule.onNodeWithText("搜索").assertExists()

            rule.runOnIdle {
                progress.floatValue = 0f
                width.value = 56.dp
            }
            val collapsedIcon = rule.onNodeWithContentDescription("搜索").fetchSemanticsNode().boundsInRoot
            val collapsedHost = rule.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
            assertEquals(collapsedHost.center.x, collapsedIcon.center.x, 0.01f)
        }
    }

    @Test
    fun deferredSizeMatchesWidthAndHeightUnderParentConstraints() {
        val width = mutableStateOf(200.dp)
        val height = mutableStateOf(90.dp)
        var compositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SideEffect { compositions += 1 }
                Column {
                    Box(Modifier.size(80.dp, 40.dp)) {
                        Box(
                            Modifier.bottomBarAnimatedSize(width, height).testTag("deferred")
                        )
                    }
                    Box(Modifier.size(80.dp, 40.dp)) {
                        Box(Modifier.width(200.dp).height(90.dp).testTag("reference"))
                    }
                }
            }
        }
        rule.onNodeWithTag("reference").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(40.dp)
        rule.onNodeWithTag("deferred").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(40.dp)
        var before = 0
        rule.runOnIdle {
            before = compositions
            width.value = 56.dp
            height.value = 32.dp
        }
        rule.onNodeWithTag("deferred").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(32.dp)
        rule.runOnIdle { assertEquals(before, compositions) }
    }

    @Test
    fun reversingSearchExpansionReturnsToCollapsedGeometryWithoutPerFrameHostComposition() {
        rule.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        var hostCompositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SideEffect { hostCompositions += 1 }
                val state = rememberBiliPaiBottomBarSearchLayoutState(
                    containerWidth = 320.dp,
                    itemCount = 4,
                    minEdgePadding = 12.dp,
                    searchEnabled = true,
                    searchExpanded = expanded.value,
                    labelMode = 0,
                    searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH,
                    hasUiSkinDecoration = false,
                )
                Row(
                    Modifier.bottomBarAnimatedSize(height = state.shellHeight).testTag("shell"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.bottomBarAnimatedSize(state.dockWidth, state.dockHeight)
                            .testTag("dock")
                    )
                    Spacer(Modifier.bottomBarAnimatedSize(width = state.launchAdjustedSearchGap))
                    Box(
                        Modifier.bottomBarAnimatedSize(state.searchWidth, state.searchHeight)
                            .testTag("search")
                    ) {
                        Box(Modifier.fillMaxSize().testTag("field"))
                    }
                }
            }
        }
        val initialDockWidth = rule.onNodeWithTag("dock").fetchSemanticsNode().size.width
        rule.onNodeWithTag("search").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("shell").assertHeightIsEqualTo(64.dp)
        rule.runOnIdle { expanded.value = true }
        // Apply the target and start the animation before capturing the host count.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        var afterTarget = 0
        rule.runOnIdle { afterTarget = hostCompositions }
        rule.mainClock.advanceTimeBy(96)
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag("dock").fetchSemanticsNode().size.width < initialDockWidth)
        rule.runOnIdle { assertEquals(afterTarget, hostCompositions) }

        // Reverse while the expansion is still running.
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { afterTarget = hostCompositions }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertEquals(initialDockWidth, rule.onNodeWithTag("dock").fetchSemanticsNode().size.width)
        rule.onNodeWithTag("search").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("field").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("shell").assertHeightIsEqualTo(64.dp)
        rule.runOnIdle { assertEquals(afterTarget, hostCompositions) }
    }
}
