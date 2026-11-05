package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.feature.dynamic.components.DynamicCommentSheet
import com.android.purebilibili.feature.dynamic.components.DynamicSubReplyPreviewHost
import com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_PAGE_TAG
import com.android.purebilibili.feature.dynamic.components.ImagePreviewOverlayHost
import com.android.purebilibili.feature.video.ui.components.COMMENT_PICTURE_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_ITEM_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_ROOT_TAG
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicSubReplyImagePreviewUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingPictureInsideDynamicSubReply_opensImagePreview() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    DynamicSubReplyPreviewHost(
                        onSortModeChange = {},
                        state = SubReplyUiState(
                            visible = true,
                            rootReply = ReplyItem(
                                rpid = 1L,
                                mid = 2L,
                                ctime = 1_700_000_000L,
                                member = ReplyMember(
                                    mid = "2",
                                    uname = "DynamicTester"
                                ),
                                content = ReplyContent(
                                    message = "root with picture",
                                    pictures = listOf(
                                        ReplyPicture(
                                            imgSrc = "https://example.com/dynamic-subreply.jpg",
                                            imgWidth = 600,
                                            imgHeight = 600
                                        )
                                    )
                                )
                            ),
                            items = persistentListOf(
                                ReplyItem(
                                    rpid = 2L,
                                    mid = 3L,
                                    root = 1L,
                                    ctime = 1_700_000_030L,
                                    member = ReplyMember(
                                        mid = "3",
                                        uname = "ChildTester"
                                    ),
                                    content = ReplyContent(
                                        message = "child with picture",
                                        pictures = listOf(
                                            ReplyPicture(
                                                imgSrc = "https://example.com/dynamic-child-subreply.jpg",
                                                imgWidth = 640,
                                                imgHeight = 640
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        onDismiss = {},
                        onRefresh = {},
                        onUserClick = {},
                        onLoadMore = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule
            .onNode(
                hasTestTag("${COMMENT_PICTURE_TAG_PREFIX}0") and
                    hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_ROOT_TAG))
            )
            .performClick()

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun clickingChildPictureInsideDynamicSubReply_opensChildImage() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    DynamicSubReplyPreviewHost(
                        onSortModeChange = {},
                        state = SubReplyUiState(
                            visible = true,
                            rootReply = ReplyItem(
                                rpid = 1L,
                                mid = 2L,
                                ctime = 1_700_000_000L,
                                member = ReplyMember(
                                    mid = "2",
                                    uname = "DynamicTester"
                                ),
                                content = ReplyContent(
                                    message = "root with picture",
                                    pictures = listOf(
                                        ReplyPicture(
                                            imgSrc = "https://example.com/dynamic-subreply.jpg",
                                            imgWidth = 600,
                                            imgHeight = 600
                                        )
                                    )
                                )
                            ),
                            items = persistentListOf(
                                ReplyItem(
                                    rpid = 2L,
                                    mid = 3L,
                                    root = 1L,
                                    ctime = 1_700_000_030L,
                                    member = ReplyMember(
                                        mid = "3",
                                        uname = "ChildTester"
                                    ),
                                    content = ReplyContent(
                                        message = "child with picture",
                                        pictures = listOf(
                                            ReplyPicture(
                                                imgSrc = "https://example.com/dynamic-child-subreply.jpg",
                                                imgWidth = 640,
                                                imgHeight = 640
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        onDismiss = {},
                        onRefresh = {},
                        onUserClick = {},
                        onLoadMore = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule
            .onNode(
                hasTestTag("${COMMENT_PICTURE_TAG_PREFIX}0") and
                    hasAnyAncestor(hasTestTag("${SUB_REPLY_DETAIL_ITEM_TAG_PREFIX}2"))
            )
            .performClick()

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun dynamicDetailThreadLikes_reflectServerStateAndToggleRootAndChildWithoutLosingCounts() {
        assertThreadLikeBehavior(inCommentSheet = false)
    }

    @Test
    fun dynamicCommentSheetThreadLikes_reflectServerStateAndToggleRootAndChildWithoutLosingCounts() {
        assertThreadLikeBehavior(inCommentSheet = true)
    }

    private fun assertThreadLikeBehavior(inCommentSheet: Boolean) {
        val root = ReplyItem(
            rpid = 1L, like = 7, action = 1,
            member = ReplyMember(mid = "2", uname = "RootTester"),
            content = ReplyContent(message = "root"),
        )
        val child = ReplyItem(
            rpid = 2L, root = 1L, like = 4,
            member = ReplyMember(mid = "3", uname = "ChildTester"),
            content = ReplyContent(message = "child"),
        )
        val state = mutableStateOf(SubReplyUiState(
            visible = true, rootReply = root, items = persistentListOf(child), isEnd = true,
        ))
        val onLike: (Long) -> Unit = { rpid ->
            fun toggle(reply: ReplyItem): ReplyItem {
                if (reply.rpid != rpid) return reply
                val liked = reply.action == 1
                return reply.copy(action = if (liked) 0 else 1, like = reply.like + if (liked) -1 else 1)
            }
            val current = state.value
            state.value = current.copy(
                rootReply = current.rootReply?.let(::toggle),
                items = persistentListOf(toggle(current.items.single())),
            )
        }
        composeTestRule.setContent {
            MaterialTheme {
                if (inCommentSheet) {
                    DynamicCommentSheet(
                        comments = emptyList(),
                        totalCount = 2,
                        isLoading = false,
                        isRefreshing = false,
                        hasRefreshError = false,
                        isLoadingMore = false,
                        onDismiss = {},
                        onRefresh = {},
                        onOpenComposer = {},
                        onUserClick = {},
                        subReplyState = state.value,
                        onRefreshSubReplies = {},
                        onThreadCommentLike = onLike,
                    )
                } else {
                DynamicSubReplyPreviewHost(
                    state = state.value,
                    onDismiss = {},
                    onLoadMore = {},
                    onRefresh = {},
                    onSortModeChange = {},
                    onUserClick = {},
                    onCommentLike = onLike,
                )
                }
            }
        }
        val rootRow = hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_ROOT_TAG))
        val childRow = hasAnyAncestor(hasTestTag("${SUB_REPLY_DETAIL_ITEM_TAG_PREFIX}2"))
        val reaction = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

        // The server's action=1 must not be treated as an unliked row (which also subtracts one).
        composeTestRule.onNode(rootRow and reaction).assertIsSelected()
        composeTestRule.onNode(rootRow and hasText("7")).assertIsDisplayed()
        composeTestRule.onNode(childRow and reaction).assertIsNotSelected().performClick()
        composeTestRule.onNode(childRow and reaction).assertIsSelected()
        composeTestRule.onNode(childRow and hasText("5")).assertIsDisplayed()
        composeTestRule.onNode(rootRow and hasText("7")).assertIsDisplayed()

        composeTestRule.onNode(childRow and reaction).performClick()
        composeTestRule.onNode(childRow and reaction).assertIsNotSelected()
        composeTestRule.onNode(childRow and hasText("4")).assertIsDisplayed()
        composeTestRule.onNode(rootRow and reaction).performClick()
        composeTestRule.onNode(rootRow and reaction).assertIsNotSelected()
        composeTestRule.onNode(rootRow and hasText("6")).assertIsDisplayed()
    }
}
