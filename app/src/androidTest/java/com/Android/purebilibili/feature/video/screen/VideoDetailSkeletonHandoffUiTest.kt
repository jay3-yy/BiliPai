package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.video.ui.components.LocalVideoSkeletonAnimationAllowed
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class VideoDetailSkeletonHandoffUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun seededDetailsWaitBehindStaticSkeletonWithoutAnExtraRevealOnRelease() {
        val entryReady = mutableStateOf(false)
        var skeletonAnimationAllowed = true
        rule.setContent {
            MaterialTheme {
                VideoDetailSkeletonHandoff(
                    identity = "video",
                    ready = true,
                    loading = false,
                    entryContentReady = entryReady.value,
                    modifier = Modifier.size(200.dp),
                    skeleton = {
                        val allowed = LocalVideoSkeletonAnimationAllowed.current
                        SideEffect { skeletonAnimationAllowed = allowed }
                        Box(Modifier.size(100.dp).testTag("skeleton"))
                    },
                    content = { Box(Modifier.size(100.dp).testTag("content")) },
                )
            }
        }
        rule.onNodeWithTag("skeleton").assertIsDisplayed()
        rule.onNodeWithTag("content").assertDoesNotExist()
        rule.runOnIdle { assertFalse(skeletonAnimationAllowed) }

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { entryReady.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("content").assertIsDisplayed()
        rule.onNodeWithTag("skeleton").assertDoesNotExist()
    }

    @Test
    fun detailsArrivingBeforeEntryPermissionNeverLeaveAnEmptyPane() {
        val detailsReady = mutableStateOf(false)
        val entryReady = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                VideoDetailSkeletonHandoff(
                    identity = "video",
                    ready = detailsReady.value,
                    loading = !detailsReady.value,
                    entryContentReady = entryReady.value,
                    modifier = Modifier.size(200.dp),
                    skeleton = { Box(Modifier.size(100.dp).testTag("skeleton")) },
                    content = { Box(Modifier.size(100.dp).testTag("content")) },
                )
            }
        }
        rule.onNodeWithTag("skeleton").assertIsDisplayed()
        rule.runOnIdle { detailsReady.value = true }
        rule.onNodeWithTag("skeleton").assertIsDisplayed()
        rule.onNodeWithTag("content").assertDoesNotExist()

        rule.runOnIdle { entryReady.value = true }
        rule.onNodeWithTag("content").assertIsDisplayed()
        rule.onNodeWithTag("skeleton").assertDoesNotExist()
    }
}
