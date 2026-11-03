package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyContentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReplyRichTextInteractionTest(private val style: AppUiStyle, private val glass: Boolean) {
    @get:Rule val rule = createComposeRule()
    private val generation = mutableStateOf(0)
    private val clicks = mutableListOf<String>()
    private var menuOpens = 0
    private var rowClicks = 0
    private var displayedText = DISPLAY

    @Test
    fun pinnedWrappedVideoLinkOpensFromBothLinesAndLongPressOpensMenuOnly() {
        showComment(pinned = true)
        val layout = layout()
        val start = displayedText.indexOf(TITLE, displayedText.indexOf(PREFIX) + PREFIX.length)
        val last = start + TITLE.length - 1
        assertTrue(layout.getLineForOffset(start) < layout.getLineForOffset(last))
        tapAt(layout.getBoundingBox(start).center)
        tapAt(layout.getBoundingBox(last).center)
        tapAt(layout.placeholderRects.last()!!.center)
        assertEquals(List(3) { "0:$DESTINATION" }, clicks)
        node().performTouchInput { longClick(layout.getBoundingBox(last).center) }
        rule.runOnIdle {
            assertEquals(1, menuOpens)
            assertEquals(3, clicks.size)
            assertEquals(0, rowClicks)
        }
    }

    @Test
    fun singleLineVideoTitleStillOpens() {
        val title = "真·美式路怒症"
        showComment(prefix = "原版 ", title = title)
        val result = layout()
        val start = displayedText.indexOf(title)
        assertEquals(result.getLineForOffset(start), result.getLineForOffset(start + title.length - 1))
        tapAt(result.getBoundingBox(start).center)
        assertEquals(listOf("0:$DESTINATION"), clicks)
    }

    @Test
    fun pinnedEmojiVideoTitleAndPlayIconOpenSameDestination() {
        val title = "🍺 飞 宇 の 酒 🍺"
        showComment(prefix = "", title = title, pinned = true)
        val result = layout()
        val start = displayedText.indexOf(title)
        // 使用完整 emoji 字符簇的包围区域，避免将代理项当成独立字符。
        val firstEmoji = result.getPathForRange(start, start + 2).getBounds()
        tapAt(firstEmoji.center)
        tapAt(result.getBoundingBox(displayedText.indexOf("飞")).center)
        tapAt(result.placeholderRects.last()!!.center)
        rule.runOnIdle {
            assertEquals(List(3) { "0:$DESTINATION" }, clicks)
            assertEquals(0, rowClicks)
            assertEquals(0, menuOpens)
        }
    }

    @Test
    fun pinnedRichLinkKeepsPressAcrossCallbackRecomposition() {
        showComment(pinned = true)
        val titleStart = displayedText.indexOf(TITLE, displayedText.indexOf(PREFIX) + PREFIX.length)
        val point = layout().getBoundingBox(titleStart).center
        node().performTouchInput { down(point) }
        rule.runOnIdle { generation.value = 1 }
        node().performTouchInput { advanceEventTime(50); up() }
        rule.runOnIdle {
            assertEquals(listOf("1:$DESTINATION"), clicks)
            assertEquals(0, rowClicks)
            assertEquals(0, menuOpens)
        }
    }

    @Test
    fun plainTextStillBelongsToCommentRow() {
        showComment()
        val point = layout().getBoundingBox(1).center
        tapAt(point)
        node().performTouchInput { longClick(point) }
        rule.runOnIdle {
            assertEquals(1, rowClicks)
            assertEquals(1, menuOpens)
            assertTrue(clicks.isEmpty())
        }
    }

    @Test
    fun pinnedStandaloneVideoTitleUsesLinkGestureAndLatestCallback() {
        val title = "飞宇的酒"
        displayedText = "TOP $title"
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides style,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                ) {
                    val currentGeneration = generation.value
                    Box(Modifier.width(220.dp).combinedClickable(
                        onClick = { rowClicks++ },
                        onLongClick = { menuOpens++ },
                    )) {
                        ReplyVideoReferenceText(
                            text = title,
                            fontSize = 16.sp,
                            maxLines = Int.MAX_VALUE,
                            url = DESTINATION,
                            prefix = buildAnnotatedString {
                                appendInlineContent(COMMENT_INLINE_TOP_BADGE_ID, "TOP")
                                append(" ")
                            },
                            onUrlClick = { clicks.add("$currentGeneration:$it") },
                            onLongPress = { menuOpens++ },
                        )
                    }
                }
            }
        }
        val start = displayedText.indexOf(title)
        tapAt(layout().getBoundingBox(start).center)
        val last = layout().getBoundingBox(displayedText.length - 1).center
        node().performTouchInput { down(last) }
        rule.runOnIdle { generation.value = 1 }
        node().performTouchInput { advanceEventTime(50); up() }
        node().performTouchInput { longClick(last) }
        rule.runOnIdle {
            assertEquals(listOf("0:$DESTINATION", "1:$DESTINATION"), clicks)
            assertEquals(1, menuOpens)
            assertEquals(0, rowClicks)
        }
    }

    private fun showComment(prefix: String = PREFIX, title: String = TITLE, pinned: Boolean = false) {
        displayedText = (if (pinned) "TOP " else "") + "$prefix $title\n哈哈哈"
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides style,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                ) {
                    val currentGeneration = generation.value
                    Box(Modifier.width(220.dp).combinedClickable(
                        onClick = { rowClicks++ },
                        onLongClick = { menuOpens++ },
                    )) {
                        ReplyMessageText(
                            text = "$prefix$TOKEN\n哈哈哈",
                            fontSize = 16.sp,
                            emoteMap = emptyMap(),
                            prefix = if (pinned) buildAnnotatedString {
                                appendInlineContent(COMMENT_INLINE_TOP_BADGE_ID, "TOP")
                                append(" ")
                            } else null,
                            content = ReplyContent(urls = mapOf(
                                TOKEN to ReplyContentUrl(
                                    title = title,
                                    url = DESTINATION,
                                    // 本地资源保留内联图标几何，不发起网络请求。
                                    prefixIcon = "android.resource://android/drawable/ic_media_play",
                                ),
                            )),
                            onUrlClick = { clicks.add("$currentGeneration:$it") },
                            onLongPress = { menuOpens++ },
                        )
                    }
                }
            }
        }
    }

    private fun node() = rule.onNodeWithText(displayedText, useUnmergedTree = true)

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun tapAt(point: Offset) = node().performTouchInput { click(point) }

    companion object {
        private const val PREFIX = "【26年在老挝，穿女装当街火拼】 "
        private const val TITLE = "26年在老挝，穿女装当街火拼"
        private const val TOKEN = "https://b23.tv/example"
        private const val DESTINATION = "https://www.bilibili.com/video/BV1xx411c7mD"
        private const val DISPLAY = "$PREFIX $TITLE\n哈哈哈"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}, glass={1}")
        fun styles(): List<Array<Any>> = AppUiStyle.entries.flatMap { style ->
            listOf(arrayOf<Any>(style, false), arrayOf<Any>(style, true))
        }
    }
}
