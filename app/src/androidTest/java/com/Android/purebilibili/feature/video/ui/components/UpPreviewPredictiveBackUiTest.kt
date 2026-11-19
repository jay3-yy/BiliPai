package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.data.model.response.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpPreviewPredictiveBackUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun returnGestureMovesSheetAndPlayerThenCancelsOrDismissesLocally() {
        val visible = mutableStateOf(true)
        val author = Owner(name = "投稿作者")
        var playerProgress = 0f
        var dismissals = 0
        var pageBacks = 0
        lateinit var dispatcher: NavigationEventDispatcher
        rule.setContent {
            val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current)
            SideEffect { dispatcher = owner.navigationEventDispatcher }
            MaterialTheme {
                LocalNavigationBackHandler(enabled = true) { pageBacks++ }
                UpPreviewSheet(
                    visible = visible.value,
                    owner = author,
                    isFollowing = false,
                    followStatusKnown = true,
                    onDismiss = {
                        dismissals++
                        visible.value = false
                    },
                    onFollowClick = {},
                    onEnterSpace = {},
                    onVideoClick = { _, _ -> },
                    onVisibilityProgressChange = { playerProgress = it },
                )
            }
        }

        val title = rule.onNodeWithText(author.name)
        val openTop = title.getUnclippedBoundsInRoot().top.value
        val input = DirectNavigationEventInput()
        rule.runOnIdle { dispatcher.addInput(input) }
        try {
            rule.runOnIdle {
                input.backStarted(NavigationEvent(progress = 0f))
                input.backProgressed(NavigationEvent(progress = 0.8f))
            }
            assertTrue(title.getUnclippedBoundsInRoot().top.value > openTop)
            rule.runOnIdle {
                assertTrue(playerProgress > 0f && playerProgress < 1f)
                assertEquals(0, dismissals)
                input.backCancelled()
            }
            rule.waitForIdle()
            assertEquals(openTop, title.getUnclippedBoundsInRoot().top.value, 1f)
            rule.runOnIdle {
                assertEquals(1f, playerProgress, 0.001f)
                assertEquals(0, dismissals)
                input.backStarted(NavigationEvent(progress = 0f))
                input.backProgressed(NavigationEvent(progress = 0.8f))
                input.backCompleted()
            }
            title.assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(1, dismissals)
                assertEquals(0, pageBacks)
                input.backCompleted()
                assertEquals(1, pageBacks)
            }
        } finally {
            rule.runOnIdle { dispatcher.removeInput(input) }
        }
    }
}
