package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentInputDialogLayoutPolicyTest {

    @Test
    fun emojiPanel_reusesMeasuredKeyboardHeight() {
        assertEquals(
            420,
            resolveCommentEmojiPanelHeightDp(
                fallbackHeightDp = 280,
                keyboardHeightDp = 420,
                availableHeightDp = 600,
            ),
        )
    }

    @Test
    fun emojiPanel_hasUsableHeightBeforeKeyboardMeasurement() {
        assertEquals(
            280,
            resolveCommentEmojiPanelHeightDp(
                fallbackHeightDp = 280,
                keyboardHeightDp = 0,
                availableHeightDp = 600,
            ),
        )
    }

    @Test
    fun emojiPanel_doesNotPushEditorOutsideShortViewport() {
        assertEquals(
            150,
            resolveCommentEmojiPanelHeightDp(
                fallbackHeightDp = 280,
                keyboardHeightDp = 420,
                availableHeightDp = 150,
            ),
        )
        assertEquals(
            0,
            resolveCommentEmojiPanelHeightDp(
                fallbackHeightDp = 280,
                keyboardHeightDp = 420,
                availableHeightDp = -20,
            ),
        )
    }

    @Test
    fun bottomSlot_holdsHeightDuringEmojiToKeyboardHandoffAndTracksImeOtherwise() {
        // 常规键盘态：直接跟随 IME 高度
        assertEquals(
            310,
            resolveCommentBottomSlotHeightDp(
                showEmojiPanel = false,
                emojiPanelHeightDp = 310,
                imeHeightDp = 310,
                handoffHoldHeightDp = 0,
            ),
        )
        // 键盘 -> 表情面板过渡：IME 尚未收完时取两者较大值，不出现高度塌陷或双重叠加
        assertEquals(
            320,
            resolveCommentBottomSlotHeightDp(
                showEmojiPanel = true,
                emojiPanelHeightDp = 300,
                imeHeightDp = 320,
                handoffHoldHeightDp = 0,
            ),
        )
        // 表情面板 -> 键盘过渡：IME 刚启动仍为 0 时锁住原表情面板高度，防止先坠底再二次顶起
        assertEquals(
            300,
            resolveCommentBottomSlotHeightDp(
                showEmojiPanel = false,
                emojiPanelHeightDp = 300,
                imeHeightDp = 0,
                handoffHoldHeightDp = 300,
            ),
        )
        // IME 升起超过或达到锁定高度后无缝由 IME 接管
        assertEquals(
            312,
            resolveCommentBottomSlotHeightDp(
                showEmojiPanel = false,
                emojiPanelHeightDp = 300,
                imeHeightDp = 312,
                handoffHoldHeightDp = 300,
            ),
        )
    }

    @Test
    fun progressInsertText_wrapsFormattedPlaybackTimeWithSpaces() {
        assertEquals(" 01:05 ", resolveCommentProgressInsertText(65_000L))
        assertEquals(" 00:00 ", resolveCommentProgressInsertText(-1L))
    }

    @Test
    fun restoredDraft_placesCursorAtTextEnd() {
        val value = commentDraftTextFieldValue("未发送草稿")

        assertEquals("未发送草稿", value.text)
        assertEquals(value.text.length, value.selection.start)
        assertEquals(value.text.length, value.selection.end)
    }

    @Test
    fun imageOnlyDraft_canBePublishedLikePiliPlus() {
        assertTrue(
            canPublishCommentDraft(
                text = "",
                selectedImageCount = 1,
                canInputComment = true,
                isSending = false
            )
        )
        assertFalse(
            canPublishCommentDraft(
                text = "",
                selectedImageCount = 0,
                canInputComment = true,
                isSending = false
            )
        )
    }


    @Test
    fun activeMentionQuery_readsTextAfterLastAtBeforeCursor() {
        val query = resolveActiveCommentMentionQuery("一起 @社会", cursor = 6)

        assertEquals(3, query?.atIndex)
        assertEquals("社会", query?.query)
    }

    @Test
    fun activeMentionQuery_ignoresWhitespaceSeparatedAt() {
        val query = resolveActiveCommentMentionQuery("@社会 易", cursor = 5)

        assertEquals(null, query)
    }

    @Test
    fun mentionInsert_replacesActiveQueryAtCursor() {
        val (text, selection) = insertCommentMentionText(
            text = "一起 @社会",
            cursor = 6,
            mentionName = "社会易姐QwQ"
        )

        assertEquals("一起 @社会易姐QwQ ", text)
        assertEquals(text.length, selection.start)
        assertEquals(text.length, selection.end)
    }

}
