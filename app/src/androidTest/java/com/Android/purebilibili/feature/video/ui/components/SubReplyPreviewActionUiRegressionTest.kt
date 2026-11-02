package com.Android.purebilibili.feature.video.ui.components

import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.feature.dynamic.components.dynamicInlineCommentItems
import com.android.purebilibili.feature.video.ui.components.COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SubReplyPreviewActionUiRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalLimit = 0
    private var originalMid: Long? = null
    private var accountChanged = false

    @Before
    fun configurePreviewLimit() = runBlocking {
        TokenManager.awaitRestore()
        originalMid = TokenManager.accountIdentity.value.mid
        originalLimit = SettingsManager.getCommentCollapsedReplyPreviewLimit(context).first()
        SettingsManager.setCommentCollapsedReplyPreviewLimit(context, 3)
    }

    @After
    fun restorePreviewLimit() = runBlocking {
        SettingsManager.setCommentCollapsedReplyPreviewLimit(context, originalLimit)
        if (accountChanged) TokenManager.saveMid(context, originalMid ?: 0L)
    }

    @Test
    fun completePreviewOnlyTogglesLocallyAcrossExpandAndCollapse() {
        var openedThreads = 0
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = reply(total = 6, loaded = 6),
                    onClick = {},
                    onSubClick = { _, _ -> openedThreads++ },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithText("展开回复").assertIsDisplayed()
        viewAll().assertDoesNotExist()
        fourthPreview().assertDoesNotExist()
        rule.onNodeWithText("展开回复").performClick()
        rule.onNodeWithText("收起回复").assertIsDisplayed()
        fourthPreview().assertIsDisplayed()
        viewAll().assertDoesNotExist()
        rule.onNodeWithText("收起回复").performClick()
        fourthPreview().assertDoesNotExist()
        viewAll().assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, openedThreads) }
    }

    @Test
    fun incompletePreviewOnlyOpensTheFullThread() {
        var openedThread: Pair<Long, Long>? = null
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = reply(total = 6, loaded = 4),
                    onClick = {},
                    onSubClick = { root, target -> openedThread = root.rpid to target },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.onNodeWithText("收起回复").assertDoesNotExist()
        fourthPreview().assertDoesNotExist()
        viewAll().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(100L to 0L, openedThread) }
    }

    @Test
    fun previewUpdatesSwitchBetweenActionsWithoutShowingBoth() {
        val current = mutableStateOf(reply(total = 6, loaded = 4))
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = current.value,
                    onClick = {},
                    onSubClick = { _, _ -> },
                    onAvatarClick = {},
                )
            }
        }

        viewAll().assertIsDisplayed()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.runOnIdle { current.value = reply(total = 6, loaded = 6) }
        rule.onNodeWithText("展开回复").assertIsDisplayed()
        viewAll().assertDoesNotExist()
        rule.runOnIdle { current.value = current.value.copy(rcount = 7) }
        viewAll().assertIsDisplayed()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.runOnIdle { current.value = reply(total = 2, loaded = 2) }
        viewAll().assertDoesNotExist()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
    }

    @Test
    fun tappingMentionOrUrlInPreview_opensThatReplyFloorInsteadOfLeavingThread() {
        val openedThreads = mutableListOf<Pair<Long, Long>>()
        val openedSpaces = mutableListOf<String>()
        val openedUrls = mutableListOf<String>()
        val child = mentionReply().let {
            it.copy(content = it.content.copy(message = "回复 @目标用户：https://example.com"))
        }
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = reply(total = 1, loaded = 1).copy(replies = listOf(child)),
                    onClick = {},
                    onSubClick = { root, target -> openedThreads += root.rpid to target },
                    onAvatarClick = { openedSpaces += it },
                    onUrlClick = { openedUrls += it },
                )
            }
        }

        tapCharacter(child.content.message, "@目标用户")
        tapCharacter(child.content.message, "https://example.com")
        rule.runOnIdle {
            assertEquals(listOf(100L to 301L, 100L to 301L), openedThreads)
            assertTrue(openedSpaces.isEmpty())
            assertTrue(openedUrls.isEmpty())
        }
    }

    @Test
    fun dynamicCommentMenu_hidesSelfButAllowsBlockingAnotherUserAfterAccountSwitch() {
        changeAccount(42L)
        rule.setContent {
            MaterialTheme {
                LazyColumn {
                    dynamicInlineCommentItems(
                        comments = listOf(
                            ReplyItem(
                                rpid = 501L,
                                mid = 42L,
                                member = ReplyMember(mid = "42", uname = "动态评论作者"),
                                content = ReplyContent(message = "动态评论菜单回归"),
                            )
                        ),
                        isLoading = false,
                        isLoadingMore = false,
                        dynamicAuthorMid = 99L,
                        currentUserMid = 42L,
                        onViewReplies = { _, _ -> },
                        onUserClick = {},
                        onImagePreview = { _, _, _, _ -> },
                    )
                }
            }
        }

        rule.onNodeWithContentDescription("评论操作").performClick()
        rule.onNodeWithText("复制全部").assertIsDisplayed()
        rule.onNodeWithText("屏蔽用户").assertDoesNotExist()
        changeAccount(77L)
        rule.onNodeWithText("屏蔽用户").assertIsDisplayed()
    }

    @Test
    fun fullMainComment_keepsMentionAndUrlNavigation() = assertFullCommentLinks(hideSubPreview = false)

    @Test
    fun fullChildDetail_keepsMentionAndUrlNavigation() = assertFullCommentLinks(hideSubPreview = true)

    @Test
    fun mainCommentMenu_hidesSelfAndTracksCurrentAccount() = assertBlockMenuTracksAccount(hideSubPreview = false)

    @Test
    fun childDetailMenu_hidesSelfAndTracksCurrentAccount() = assertBlockMenuTracksAccount(hideSubPreview = true)

    @Test
    fun invalidAuthorMenu_doesNotOfferBlockUser() {
        changeAccount(42L)
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = ReplyItem(rpid = 501L, mid = 0L, member = ReplyMember(mid = "invalid")),
                    onClick = {},
                    onSubClick = { _, _ -> },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithContentDescription("评论操作").performClick()
        rule.onNodeWithText("复制全部").assertIsDisplayed()
        rule.onNodeWithText("屏蔽用户").assertDoesNotExist()
    }

    private fun assertFullCommentLinks(hideSubPreview: Boolean) {
        val spaces = mutableListOf<String>()
        val urls = mutableListOf<String>()
        var openedThreads = 0
        val content = mentionReply().content.copy(message = "回复 @目标用户：https://example.com")
        val item = if (hideSubPreview) {
            mentionReply().copy(content = content)
        } else {
            reply(total = 1, loaded = 1).copy(content = content)
        }
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = item,
                    hideSubPreview = hideSubPreview,
                    onClick = {},
                    onSubClick = { _, _ -> openedThreads++ },
                    onAvatarClick = { spaces += it },
                    onUrlClick = { urls += it },
                )
            }
        }

        tapCharacter(content.message, "@目标用户")
        tapCharacter(content.message, "https://example.com")
        rule.runOnIdle {
            assertEquals(listOf("99"), spaces)
            assertEquals(listOf("https://example.com"), urls)
            assertEquals(0, openedThreads)
        }
    }

    private fun assertBlockMenuTracksAccount(hideSubPreview: Boolean) {
        changeAccount(42L)
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    // Missing member.mid must still identify our own comment through item.mid.
                    item = ReplyItem(
                        rpid = 501L,
                        root = if (hideSubPreview) 100L else 0L,
                        mid = 42L,
                        member = ReplyMember(mid = "", uname = "自己的评论"),
                        content = ReplyContent(message = "账号切换菜单回归"),
                    ),
                    hideSubPreview = hideSubPreview,
                    onClick = {},
                    onSubClick = { _, _ -> },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithContentDescription("评论操作").performClick()
        rule.onNodeWithText("复制全部").assertIsDisplayed()
        rule.onNodeWithText("屏蔽用户").assertDoesNotExist()
        changeAccount(77L)
        rule.onNodeWithText("屏蔽用户").assertIsDisplayed()
        changeAccount(42L)
        rule.onNodeWithText("屏蔽用户").assertDoesNotExist()
    }

    private fun changeAccount(mid: Long) {
        accountChanged = true
        runBlocking { TokenManager.saveMid(context, mid) }
        rule.waitForIdle()
    }

    private fun characterInRoot(text: String, substring: String): Offset {
        val node = rule.onNodeWithText(text, substring = true, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        val start = layout.layoutInput.text.text.indexOf(substring)
        assertTrue(start >= 0)
        return node.fetchSemanticsNode().boundsInRoot.topLeft +
            layout.getBoundingBox(start + substring.length / 2).center
    }

    private fun tapCharacter(text: String, substring: String) {
        val point = characterInRoot(text, substring)
        rule.onRoot().performTouchInput { click(point) }
    }

    private fun mentionReply() = ReplyItem(
        rpid = 301L,
        root = 100L,
        mid = 31L,
        member = ReplyMember(mid = "31", uname = "回复者1"),
        content = ReplyContent(message = "回复 @目标用户：这是一条回复", atNameToMid = mapOf("目标用户" to 99L)),
    )

    private fun viewAll() = rule.onNodeWithTag("${COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX}100")
    private fun fourthPreview() = rule.onNodeWithTag("${COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX}304")

    private fun reply(total: Int, loaded: Int) = ReplyItem(
        rpid = 100L,
        mid = 10L,
        rcount = total,
        member = ReplyMember(mid = "10", uname = "评论作者"),
        content = ReplyContent(message = "测试楼中楼入口"),
        replies = (1..loaded).map { index ->
            ReplyItem(
                rpid = 300L + index,
                root = 100L,
                mid = 30L + index,
                member = ReplyMember(mid = (30L + index).toString(), uname = "回复者$index"),
                content = ReplyContent(message = "预览回复$index"),
            )
        },
    )
}
