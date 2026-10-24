package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VideoDetailCommentStateScopeTest {
    @get:Rule val rule = createComposeRule()
    private var playerCompositions = 0
    private var hostCompositions = 0

    @Composable
    private fun Player() {
        SideEffect { playerCompositions++ }
        BasicText("player")
    }

    @Composable
    private fun Host(
        comments: State<CommentUiState>,
        thread: State<SubReplyUiState>,
        visible: State<Boolean>,
    ) {
        SideEffect { hostCompositions++ }
        Column {
            Player()
            if (visible.value) {
                VideoDetailCommentStateScope(comments, thread) { list, replies ->
                    BasicText("count=${list.replyCount}, thread=${replies.totalCount}")
                }
            }
        }
    }

    @Test
    fun listAndThreadUpdatesLeavePlayerAndLayoutHostUnchanged() {
        val comments = mutableStateOf(CommentUiState())
        val thread = mutableStateOf(SubReplyUiState())
        val visible = mutableStateOf(true)
        rule.setContent { Host(comments, thread, visible) }
        rule.onNodeWithText("count=0, thread=0").assertExists()
        var playerBaseline = 0
        var hostBaseline = 0
        rule.runOnIdle {
            playerBaseline = playerCompositions
            hostBaseline = hostCompositions
            comments.value = comments.value.copy(replyCount = 120, isRepliesLoading = true)
        }
        rule.onNodeWithText("count=120, thread=0").assertExists()
        rule.runOnIdle { thread.value = thread.value.copy(totalCount = 15) }
        rule.onNodeWithText("count=120, thread=15").assertExists()
        rule.runOnIdle {
            assertEquals(playerBaseline, playerCompositions)
            assertEquals(hostBaseline, hostCompositions)
        }
    }

    @Test
    fun closedCommentSurfaceResumesWithLatestPayload() {
        val comments = mutableStateOf(CommentUiState())
        val thread = mutableStateOf(SubReplyUiState())
        val visible = mutableStateOf(false)
        rule.setContent { Host(comments, thread, visible) }
        var playerBaseline = 0
        rule.runOnIdle {
            playerBaseline = playerCompositions
            comments.value = comments.value.copy(replyCount = 230)
            thread.value = thread.value.copy(totalCount = 19)
        }
        rule.onNodeWithText("count=230, thread=19").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(playerBaseline, playerCompositions)
            visible.value = true
        }
        rule.onNodeWithText("count=230, thread=19").assertExists()
        rule.runOnIdle { assertEquals(playerBaseline, playerCompositions) }
    }
}
