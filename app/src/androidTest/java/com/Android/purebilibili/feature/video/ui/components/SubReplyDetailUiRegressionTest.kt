package com.Android.purebilibili.feature.video.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.feature.dynamic.components.dynamicInlineCommentItems
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.feature.video.ui.components.COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.COMMENT_PICTURE_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_ITEM_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_PARTIAL_TAG
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_HEADER_TAG
import com.android.purebilibili.feature.video.ui.components.SUB_REPLY_DETAIL_CLOSE_TAG
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.SubReplySheet
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SUB_REPLY_DETAIL_LIST_TAG = "subreply_detail_reply_list"
private const val SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX = "subreply_detail_conversation_"

@RunWith(AndroidJUnit4::class)
class SubReplyDetailUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun recoveredTargetWithoutRoot_remainsReadableWithoutFullThreadClaims() {
        val target = buildSubReplyState().items.last().copy(
            content = ReplyContent(message = "readable target without a directed prefix"),
        )
        var replyId: Long? = null
        var loadMoreCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = SubReplyUiState(
                        visible = true, isEnd = true,
                        items = listOf(target).toImmutableList(),
                        baseItems = listOf(target).toImmutableList(),
                        targetReplyId = target.rpid,
                    ),
                    emoteMap = emptyMap(),
                    onDismiss = {}, onSortModeChange = {}, onRefresh = {},
                    onLoadMore = { loadMoreCount++ },
                    onReplyClick = { replyId = it.rpid },
                )
            }
        }
        composeTestRule.onNodeWithText("readable target without a directed prefix").assertIsDisplayed()
        assertPartialIndicatorBesideTitle("评论详情")
        composeTestRule.onNodeWithTag("subreply_detail_root").assertDoesNotExist()
        composeTestRule.onNodeWithTag("subreply_detail_sort").assertDoesNotExist()
        composeTestRule.onNodeWithText("没有更多了").assertDoesNotExist()
        composeTestRule.onNodeWithText("返回全部回复").assertDoesNotExist()
        composeTestRule.onNodeWithText("相关回复共", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("回复").performClick()
        composeTestRule.runOnIdle {
            assertEquals(target.rpid, replyId)
            assertEquals(0, loadMoreCount)
        }
        composeTestRule.onNodeWithTag("$SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX${target.rpid}").performClick()
        composeTestRule.onNodeWithText("对话详情").assertIsDisplayed()
        assertPartialIndicatorBesideTitle("对话详情")
    }

    private fun assertPartialIndicatorBesideTitle(title: String) {
        val indicator = composeTestRule.onNodeWithTag(SUB_REPLY_DETAIL_PARTIAL_TAG)
        indicator.assertIsDisplayed()
        val indicatorBounds = indicator.fetchSemanticsNode().boundsInRoot
        val titleBounds = composeTestRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val headerBounds = composeTestRule.onNodeWithTag(SUB_REPLY_DETAIL_HEADER_TAG)
            .fetchSemanticsNode().boundsInRoot
        val closeBounds = composeTestRule.onNodeWithTag(SUB_REPLY_DETAIL_CLOSE_TAG)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(indicatorBounds.left >= titleBounds.right)
        assertTrue(indicatorBounds.right <= closeBounds.left)
        assertTrue(indicatorBounds.top >= headerBounds.top)
        assertTrue(indicatorBounds.bottom <= headerBounds.bottom)
        assertTrue(kotlin.math.abs(indicatorBounds.center.y - titleBounds.center.y) <= 1f)
    }

    @Test
    fun replyPrefixWithoutDialog_doesNotOfferConversation() {
        val target = buildSubReplyState().items.last().copy(dialog = 0L)
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = SubReplyUiState(
                        visible = true, isEnd = true,
                        items = listOf(target).toImmutableList(),
                    ),
                    emoteMap = emptyMap(), onDismiss = {}, onSortModeChange = {},
                    onRefresh = {}, onLoadMore = {},
                )
            }
        }
        composeTestRule.onNodeWithText(target.content.message).assertIsDisplayed()
        composeTestRule.onNodeWithTag("$SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX${target.rpid}").assertDoesNotExist()
    }

    @Test
    fun dynamicCommentDelete_isOwnerOnlyBeforeVotesAndAbsentFromMoreMenu() {
        val currentMid = mutableStateOf<Long?>(42L)
        var deleteCount = 0
        val reply = ReplyItem(
            rpid = 801L,
            mid = 42L,
            member = ReplyMember(mid = "42", uname = "comment author"),
            content = ReplyContent(message = "ownership regression comment"),
        )
        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    dynamicInlineCommentItems(
                        comments = listOf(reply),
                        isLoading = false,
                        isLoadingMore = false,
                        dynamicAuthorMid = 99L,
                        currentUserMid = currentMid.value,
                        onViewReplies = { _, _ -> },
                        onDelete = { deleteCount++ },
                        onUserClick = {},
                        onImagePreview = { _, _, _, _ -> },
                    )
                }
            }
        }

        val delete = composeTestRule.onNodeWithContentDescription("删除")
        delete.assertIsDisplayed()
        val deleteBounds = delete.fetchSemanticsNode().boundsInRoot
        val likeBounds = composeTestRule.onNodeWithContentDescription("Like", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val hateBounds = composeTestRule.onNodeWithContentDescription("点踩评论", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(deleteBounds.right <= likeBounds.left)
        assertTrue(likeBounds.right <= hateBounds.left)
        delete.performClick()
        composeTestRule.runOnIdle { assertEquals(1, deleteCount) }

        composeTestRule.onNodeWithContentDescription("评论操作").performClick()
        composeTestRule.onNodeWithText("复制全部").assertIsDisplayed()
        composeTestRule.onNodeWithText("删除").assertDoesNotExist()
        composeTestRule.onNodeWithText("删除评论").assertDoesNotExist()
        composeTestRule.onNodeWithText("复制用户名").performClick()

        // A dynamic's owner is still not allowed to delete somebody else's comment.
        composeTestRule.runOnIdle { currentMid.value = 99L }
        composeTestRule.onNodeWithContentDescription("删除").assertDoesNotExist()
        composeTestRule.runOnIdle { currentMid.value = 77L }
        composeTestRule.onNodeWithContentDescription("删除").assertDoesNotExist()
        composeTestRule.runOnIdle { currentMid.value = 0L }
        composeTestRule.onNodeWithContentDescription("删除").assertDoesNotExist()
        composeTestRule.runOnIdle { currentMid.value = null }
        composeTestRule.onNodeWithContentDescription("删除").assertDoesNotExist()
    }

    @Test
    fun threadDeletion_usesRealHandlerForRootAndChildWithoutDissolve() {
        val currentMid = mutableStateOf(30L)
        val deletionAvailable = mutableStateOf(true)
        val deletedIds = mutableListOf<Long>()
        val root = buildReplyWithPreview()
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = SubReplyUiState(
                        visible = true,
                        rootReply = root.copy(replies = null),
                        items = root.replies.orEmpty().toImmutableList(),
                        isEnd = true,
                    ),
                    emoteMap = emptyMap(),
                    currentMid = currentMid.value,
                    onDeleteComment = if (deletionAvailable.value) ({ deletedIds += it }) else null,
                    onDismiss = {},
                    onSortModeChange = {},
                    onRefresh = {},
                    onLoadMore = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("删除").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(301L), deletedIds)
            currentMid.value = 10L
        }
        composeTestRule.onNodeWithContentDescription("删除").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(301L, 100L), deletedIds)
            deletionAvailable.value = false
        }
        composeTestRule.onNodeWithContentDescription("删除").assertDoesNotExist()
    }

    @Test
    fun sortingDispatchesToOwnerAndReflectsItsState() {
        val state = mutableStateOf(buildSubReplyState().copy(isEnd = true))
        val requestedModes = mutableListOf<SubReplySortMode>()
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = state.value,
                    emoteMap = emptyMap(),
                    onDismiss = {},
                    onLoadMore = {},
                    onRefresh = {},
                    onSortModeChange = { mode ->
                        requestedModes += mode
                        state.value = state.value.copy(sortMode = mode)
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("按时间").performClick()
        composeTestRule.onNodeWithText("按热度").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("按时间").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(listOf(SubReplySortMode.HOT, SubReplySortMode.TIME), requestedModes)
            state.value = state.value.copy(isLoading = true)
        }
        composeTestRule.onNodeWithTag("subreply_detail_sort").assertIsNotEnabled()
    }

    @Test
    fun directedChildReply_opensConversationAndReturnsToAllReplies() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(width = 390.dp, height = 844.dp)
                ) {
                    SubReplySheet(
                        state = buildSubReplyState(),
                        emoteMap = emptyMap(),
                        onDismiss = {},
                        onSortModeChange = {},
                        onRefresh = {},
                        onLoadMore = {}
                    )
                }
            }
        }

        composeTestRule
            .onNode(
                hasScrollToIndexAction() and
                    hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_LIST_TAG))
            )
            .performScrollToNode(hasTestTag("${SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX}203"))

        composeTestRule
            .onNodeWithTag("${SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX}203")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("对话详情").assertIsDisplayed()
        composeTestRule.onNodeWithText("返回全部回复").performClick()
        composeTestRule.onNodeWithTag("${SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX}203").assertIsDisplayed()
    }

    @Test
    fun clickingChildReplyPicture_opensThatReplyImage() {
        var previewedImage: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(width = 390.dp, height = 844.dp)
                ) {
                    SubReplySheet(
                        state = buildSubReplyState(),
                        emoteMap = emptyMap(),
                        onDismiss = {},
                        onSortModeChange = {},
                        onLoadMore = {},
                        onRefresh = {},
                        onImagePreview = { images, index, _, _ ->
                            previewedImage = images[index]
                        }
                    )
                }
            }
        }

        composeTestRule
            .onNode(
                hasTestTag("${COMMENT_PICTURE_TAG_PREFIX}0") and
                    hasAnyAncestor(hasTestTag("${SUB_REPLY_DETAIL_ITEM_TAG_PREFIX}201"))
            )
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("https://example.com/reply-image.jpg", previewedImage)
        }
    }

    @Test
    fun clickingSubReplyPreview_opensRootReplyDetail() {
        var openedReplyId: Long? = null

        composeTestRule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { reply, _ -> openedReplyId = reply.rpid },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX}301")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(100L, openedReplyId)
        }
    }

    @Test
    fun clickingRootCommentWithSubReplies_opensRootReplyDetail() {
        var openedReplyId: Long? = null

        composeTestRule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { reply, _ -> openedReplyId = reply.rpid },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onRoot()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(100L, openedReplyId)
        }
    }

    @Test
    fun clickingRootCommentTextWithSubReplies_opensRootReplyDetail() {
        var openedReplyId: Long? = null

        composeTestRule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { reply, _ -> openedReplyId = reply.rpid },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("root")
            .performTouchInput {
                click(center)
            }

        composeTestRule.runOnIdle {
            assertEquals(100L, openedReplyId)
        }
    }

    @Test
    fun clickingSubReplyPreviewText_opensRootReplyDetail() {
        var openedReplyId: Long? = null

        composeTestRule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { reply, _ -> openedReplyId = reply.rpid },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("PreviewReply: preview child reply")
            .performTouchInput {
                click(center)
            }

        composeTestRule.runOnIdle {
            assertEquals(100L, openedReplyId)
        }
    }

    @Test
    fun clickingViewAllRepliesEntry_opensRootReplyDetail() {
        var openedReplyId: Long? = null

        composeTestRule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { reply, _ -> openedReplyId = reply.rpid },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX}100")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(100L, openedReplyId)
        }
    }

    @Test
    fun longPressingSubReplyPreview_copiesPreviewText() {
        lateinit var clipboardManager: ClipboardManager
        lateinit var appContext: android.content.Context

        composeTestRule.setContent {
            val context = LocalContext.current
            appContext = context
            clipboardManager = context.getSystemService(ClipboardManager::class.java)
            MaterialTheme {
                ReplyItemView(
                    item = buildReplyWithPreview(),
                    emoteMap = emptyMap(),
                    onClick = {},
                    onSubClick = { _, _ -> },
                    onAvatarClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX}301")
            .performTouchInput {
                longClick(center)
            }

        composeTestRule.runOnIdle {
            val clip = clipboardManager.primaryClip
            val firstItem = clip?.takeIf { it.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) }
                ?.getItemAt(0)
                ?.coerceToText(appContext)
                ?.toString()
            assertEquals("preview child reply", firstItem)
        }
    }

    @Test
    fun detailRootWithPreview_doesNotNestRepliesOrOpenComposerOnBodyTap() {
        var replyTarget: Long? = null
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = SubReplyUiState(
                        visible = true,
                        rootReply = buildReplyWithPreview(),
                        isEnd = true,
                    ),
                    emoteMap = emptyMap(),
                    onDismiss = {},
                    onSortModeChange = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onReplyClick = { replyTarget = it.rpid },
                )
            }
        }

        composeTestRule.onNodeWithTag("${COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX}301").assertDoesNotExist()
        composeTestRule.onNodeWithTag("${COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX}100").assertDoesNotExist()
        composeTestRule.onNodeWithText("root").performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertEquals(null, replyTarget) }
        composeTestRule.onNodeWithText("回复").performClick()
        composeTestRule.runOnIdle { assertEquals(100L, replyTarget) }
    }

    @Test
    fun missingNavigationTarget_isReportedOnlyAfterSuccessfulFinalPage() {
        val state = mutableStateOf(
            buildSubReplyState().copy(targetReplyId = 999L, error = "网络连接失败")
        )
        var loadMoreCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = state.value,
                    emoteMap = emptyMap(),
                    onDismiss = {},
                    onSortModeChange = {},
                    onRefresh = {},
                    onLoadMore = { loadMoreCount++ },
                )
            }
        }
        composeTestRule.onNodeWithText("未找到目标回复，可能已删除或不可见").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(0, loadMoreCount)
            state.value = state.value.copy(error = null, isEnd = true)
        }
        composeTestRule.onNodeWithText("未找到目标回复，可能已删除或不可见").assertIsDisplayed()
    }

    @Test
    fun laterPageTarget_isLocatedOnceAndDoesNotOverrideSubsequentReading() {
        val initial = buildSubReplyState()
        val firstPage = (1L..20L).map { id ->
            initial.items.first().copy(
                rpid = id + 1000L,
                content = ReplyContent(message = "分页回复 $id"),
            )
        }
        val target = firstPage.last().copy(
            rpid = 2000L,
            content = ReplyContent(message = "需要定位的回复"),
        )
        val state = mutableStateOf(initial.copy(items = firstPage.toImmutableList(), targetReplyId = target.rpid))
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = state.value,
                    emoteMap = emptyMap(),
                    onDismiss = {},
                    onSortModeChange = {},
                    onRefresh = {},
                    onLoadMore = {
                        state.value = state.value.copy(
                            items = (firstPage + target).toImmutableList(),
                            page = 2,
                            isEnd = true,
                        )
                    },
                )
            }
        }
        composeTestRule.onNodeWithText("需要定位的回复").assertIsDisplayed()
        composeTestRule.onNode(
            hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_LIST_TAG))
        ).performScrollToIndex(0)
        composeTestRule.onNodeWithText("root comment with picture").assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value = state.value.copy(isRefreshing = true, isLoading = true)
        }
        composeTestRule.runOnIdle {
            state.value = state.value.copy(isRefreshing = false, isLoading = false)
        }
        composeTestRule.onNodeWithText("root comment with picture").assertIsDisplayed()
        composeTestRule.onNodeWithText("需要定位的回复").assertIsNotDisplayed()
    }

    @Test
    fun fallbackConversation_waitsForContextThenLocatesTargetWithoutReplayingTheJump() {
        val target = buildSubReplyState().items.last().copy(
            content = ReplyContent(message = "对话组中的目标回复"),
        )
        val earlier = (1L..20L).map { id ->
            target.copy(rpid = 1000L + id, content = ReplyContent(message = "对话上文 $id"))
        }
        val state = mutableStateOf(SubReplyUiState(
            visible = true, isLoading = true,
            items = listOf(target).toImmutableList(),
            baseItems = listOf(target).toImmutableList(),
            targetReplyId = target.rpid, conversationAnchor = target,
        ))
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = state.value, emoteMap = emptyMap(),
                    onDismiss = {}, onSortModeChange = {}, onRefresh = {}, onLoadMore = {},
                )
            }
        }
        composeTestRule.runOnIdle {
            state.value = state.value.copy(
                items = (earlier + target).toImmutableList(), isLoading = false, isEnd = true,
            )
        }
        composeTestRule.onNodeWithText("对话组中的目标回复").assertIsDisplayed()
        composeTestRule.onNode(
            hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_LIST_TAG))
        ).performScrollToIndex(0)
        composeTestRule.onNodeWithText("对话上文 1").assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value = state.value.copy(isLoading = true, isRefreshing = true)
        }
        composeTestRule.runOnIdle {
            state.value = state.value.copy(isLoading = false, isRefreshing = false)
        }
        composeTestRule.onNodeWithText("对话上文 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("对话组中的目标回复").assertIsNotDisplayed()
    }

    @Test
    fun failedTargetPage_retryLocatesReplyWithoutResettingTheNavigationRequest() {
        val initial = buildSubReplyState()
        val replies = (1L..20L).map { id ->
            initial.items.first().copy(rpid = 1000L + id, content = ReplyContent(message = "已加载回复 $id"))
        }
        val target = replies.last().copy(rpid = 2000L, content = ReplyContent(message = "重试后定位的回复"))
        val state = mutableStateOf(
            initial.copy(items = replies.toImmutableList(), targetReplyId = target.rpid, error = "目标页读取失败")
        )
        composeTestRule.setContent {
            MaterialTheme {
                SubReplySheet(
                    state = state.value,
                    emoteMap = emptyMap(),
                    onDismiss = {},
                    onSortModeChange = {},
                    onRefresh = { state.value = state.value.copy(targetReplyId = 0L, error = null, isEnd = true) },
                    onLoadMore = {
                        state.value = state.value.copy(items = (replies + target).toImmutableList(), error = null, isEnd = true, page = 2)
                    },
                )
            }
        }
        composeTestRule.onNode(
            hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SUB_REPLY_DETAIL_LIST_TAG))
        ).performScrollToNode(androidx.compose.ui.test.hasText("目标页读取失败，点击重试"))
        composeTestRule.onNodeWithText("目标页读取失败，点击重试").performClick()
        composeTestRule.onNodeWithText("重试后定位的回复").assertIsDisplayed()
    }

    private fun buildSubReplyState(): SubReplyUiState {
        return SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(
                rpid = 101L,
                mid = 11L,
                ctime = 1_700_000_000L,
                member = ReplyMember(
                    mid = "11",
                    uname = "RootAuthor"
                ),
                content = ReplyContent(
                    message = "root comment with picture",
                    pictures = listOf(
                        ReplyPicture(
                            imgSrc = "https://example.com/root-image.jpg",
                            imgWidth = 720,
                            imgHeight = 720
                        )
                    )
                )
            ),
            items = listOf(
                ReplyItem(
                    rpid = 201L,
                    mid = 12L,
                    root = 101L,
                    ctime = 1_700_000_060L,
                    member = ReplyMember(
                        mid = "12",
                        uname = "ReplyWithPicture"
                    ),
                    content = ReplyContent(
                        message = "reply image",
                        pictures = listOf(
                            ReplyPicture(
                                imgSrc = "https://example.com/reply-image.jpg",
                                imgWidth = 640,
                                imgHeight = 640
                            )
                        )
                    )
                ),
                ReplyItem(
                    rpid = 202L,
                    mid = 13L,
                    root = 101L,
                    ctime = 1_700_000_090L,
                    member = ReplyMember(
                        mid = "13",
                        uname = "ReplyTextOnly"
                    ),
                    content = ReplyContent(message = "plain reply")
                ),
                ReplyItem(
                    rpid = 203L,
                    mid = 14L,
                    root = 101L,
                    parent = 202L,
                    dialog = 202L,
                    ctime = 1_700_000_120L,
                    member = ReplyMember(
                        mid = "14",
                        uname = "ReplyDirected",
                        garbCardNumber = "13992"
                    ),
                    content = ReplyContent(message = "回复 @ReplyTextOnly：没错")
                )
            ).toImmutableList()
        )
    }

    private fun buildReplyWithPreview(): ReplyItem {
        return ReplyItem(
            rpid = 100L,
            mid = 10L,
            rcount = 5,
            ctime = 1_700_000_000L,
            member = ReplyMember(
                mid = "10",
                uname = "RootAuthor"
            ),
            content = ReplyContent(message = "root"),
            replies = listOf(
                ReplyItem(
                    rpid = 301L,
                    mid = 30L,
                    root = 100L,
                    ctime = 1_700_000_030L,
                    member = ReplyMember(
                        mid = "30",
                        uname = "PreviewReply"
                    ),
                    content = ReplyContent(message = "preview child reply")
                )
            )
        )
    }
}
