package com.android.purebilibili.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.home.components.HomeTopAvatarButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeHeaderAvatarMotionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun visibleAvatarScalesWhileClickAreaAndSystemAnchorStayFixed() {
        val scale = mutableFloatStateOf(1f)
        rule.setContent {
            Box(Modifier.size(100.dp)) {
                HomeTopAvatarButton(
                    touchSize = 48.dp,
                    visualSize = 36.dp,
                    anchor = Modifier.testTag("anchor"),
                    returnMotion = Modifier.graphicsLayer {
                        scaleX = scale.floatValue
                        scaleY = scale.floatValue
                    },
                    onClick = {},
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Red).testTag("avatar"))
                }
            }
        }
        fun avatarBounds() = rule.onNodeWithTag("avatar", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val resting = avatarBounds()
        rule.runOnIdle { scale.floatValue = 0.9f }
        rule.waitForIdle()
        val contracted = avatarBounds()
        assertTrue(contracted.width < resting.width)
        assertTrue(contracted.height < resting.height)
        assertEquals(resting.center.x, contracted.center.x, 0.5f)
        assertEquals(resting.center.y, contracted.center.y, 0.5f)
        rule.onNodeWithContentDescription("个人中心").assertWidthIsEqualTo(48.dp)
        rule.onNodeWithTag("anchor", useUnmergedTree = true).assertWidthIsEqualTo(36.dp)
        rule.runOnIdle { scale.floatValue = 1f }
        rule.waitForIdle()
        assertEquals(resting.width, avatarBounds().width, 0.5f)
    }
}
