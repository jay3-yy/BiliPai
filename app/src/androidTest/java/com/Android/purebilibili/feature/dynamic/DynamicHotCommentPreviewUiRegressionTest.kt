package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import com.android.purebilibili.core.ui.transition.LocalDynamicCommentPreviewVisible
import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.RichTextNode
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardInteractionActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardPresentation
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.feature.space.resolveSpaceDynamicCardItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicHotCommentPreviewUiRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    private val destinations = mutableStateListOf<String>()
    private var density = 1f

    @Test
    fun plainBodyIconAndRowBlankOpenCommentsInsteadOfTheVideoCard() {
        val body = "这是一条热门评论"
        showCard(DynamicDesc(text = body))

        tapCharacter(body, "热门")
        assertDestinations("link:$COMMENTS_URL")

        // The icon has no semantics label. Hit its actual position left of the text.
        val textBounds = previewText(body).fetchSemanticsNode().boundsInRoot
        tapInRoot(Offset(textBounds.left - 10.5f * density, textBounds.center.y))
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL")

        tapRowBlank(body)
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL", "link:$COMMENTS_URL")

        // Positive control: the real archive remains actionable outside the preview row.
        tapCharacter(VIDEO_TITLE, "视频")
        assertDestinations(
            "link:$COMMENTS_URL", "link:$COMMENTS_URL", "link:$COMMENTS_URL", "video:$VIDEO_ID",
        )
    }

    @Test
    fun spaceApiHotPreviewIsVisibleAndOpensCommentsInsteadOfPlainDetail() {
        val spaceItem = Json { ignoreUnknownKeys = true }.decodeFromString<SpaceDynamicItem>(
            """{
                "id_str":"$DYNAMIC_ID","type":"DYNAMIC_TYPE_AV",
                "modules":{
                    "module_author":{"mid":123,"name":"动态作者"},
                    "module_dynamic":{"major":{"archive":{"bvid":"$VIDEO_ID","title":"$VIDEO_TITLE"}}},
                    "module_interaction":{"items":[{"type":1,"extend_click_param":{"comment_root_id":"701"},"desc":{"rich_text_nodes":[
                        {"type":"RICH_TEXT_NODE_TYPE_AT","text":"@评论作者","rid":"$USER_ID"},
                        {"type":"RICH_TEXT_NODE_TYPE_TEXT","text":"：个人空间接口热评"}
                    ]}}]}
                }
            }""",
        )
        val body = "@评论作者：个人空间接口热评"
        showCard(
            DynamicDesc(),
            itemOverride = resolveSpaceDynamicCardItem(spaceItem),
        )

        tapCharacter(body, "评论作者")
        assertDestinations("link:$COMMENTS_URL")
        tapCharacter(body, "接口热评")
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL")
        tapRowBlank(body)
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL", "link:$COMMENTS_URL")
    }

    @Test
    fun nativeAtAuthorAndCommentBodyBothOpenCurrentDynamicComments() {
        val author = "@评论作者"
        val body = "$author：热门正文"
        showCard(DynamicDesc(
            text = body,
            rich_text_nodes = listOf(
                RichTextNode(type = "RICH_TEXT_NODE_TYPE_AT", text = author, rid = USER_ID.toString()),
                RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = "：热门正文"),
            ),
        ))

        // A character-bound tap exercises BasicText's native LinkAnnotation child.
        tapCharacter(body, "评论作者")
        assertDestinations("link:$COMMENTS_URL")

        tapCharacter(body, "热门正文")
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL")
    }

    @Test
    fun sameDynamicBodyLinkDoesNotInventTheSummaryLocation() {
        val linkedText = "查看原评论"
        val body = "$linkedText 普通正文"
        val explicitUrl = "bilibili://following/detail/$DYNAMIC_ID?root_reply_id=701&target_id=702"
        showCard(DynamicDesc(
            text = body,
            rich_text_nodes = listOf(
                RichTextNode(type = "WEB", text = linkedText, jump_url = explicitUrl),
                RichTextNode(type = "TEXT", text = " 普通正文"),
            ),
        ))

        tapCharacter(body, linkedText)
        assertDestinations("link:$COMMENTS_URL")

        tapRowBlank(body)
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL")
    }

    @Test
    fun summaryUriAndOverrideParamsLocateTheImageComment() {
        val body = "@评论作者：摘要携带楼层"
        val spaceItem = Json { ignoreUnknownKeys = true }.decodeFromString<SpaceDynamicItem>(
            """{
                "id_str":"$DYNAMIC_ID","type":"DYNAMIC_TYPE_DRAW",
                "basic":{"comment_id_str":"413967238","comment_type":11},
                "modules":{"module_interaction":{"items":[{
                    "type":1,
                    "uri":"bilibili://comment3/main/413967238/11?rp_id=10&sub_rp_id=20",
                    "extend_click_param":{"rp_id":"322575690544","sub_rp_id":"318863949761"},
                    "desc":{"text":"$body","rich_text_nodes":[
                        {"type":"AT","text":"@评论作者","rid":"$USER_ID"},
                        {"type":"TEXT","text":"：摘要携带楼层"}
                    ]}
                }]}}
            }""",
        )
        showCard(
            DynamicDesc(),
            itemOverride = resolveSpaceDynamicCardItem(spaceItem),
        )
        val locatedUrl = "$BASE_COMMENTS_URL&root_reply_id=322575690544&target_id=318863949761"

        tapCharacter(body, "评论作者")
        tapCharacter(body, "携带楼层")
        tapRowBlank(body)
        assertDestinations("link:$locatedUrl", "link:$locatedUrl", "link:$locatedUrl")
    }

    @Test
    fun unrelatedDynamicAndUserLinksCannotNavigateAwayFromHotCommentPreview() {
        val body = "其他动态 用户主页 无ID提及"
        showCard(DynamicDesc(
            text = body,
            rich_text_nodes = listOf(
                RichTextNode(type = "WEB", text = "其他动态 ", jump_url = "bilibili://following/detail/999?root=701&comment_id=702"),
                RichTextNode(type = "WEB", text = "用户主页 ", jump_url = "https://space.bilibili.com/$USER_ID"),
                RichTextNode(type = "AT", text = "无ID提及"),
            ),
        ))

        tapCharacter(body, "其他动态")
        tapCharacter(body, "用户主页")
        tapCharacter(body, "无ID提及")
        assertDestinations("link:$COMMENTS_URL", "link:$COMMENTS_URL", "link:$COMMENTS_URL")
    }

    @Test
    fun hotPreviewOverrideDoesNotChangeBodyMentionsOrVideoClicks() {
        val preview = "热评里的@用户"
        val body = "正文里的@用户"
        showCard(
            desc = DynamicDesc(text = preview, rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "热评里的"),
                RichTextNode(type = "AT", text = "@用户", rid = USER_ID.toString()),
            )),
            bodyDesc = DynamicDesc(text = body, rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "正文里的"),
                RichTextNode(type = "AT", text = "@用户", rid = USER_ID.toString()),
            )),
        )

        tapCharacter(preview, "@用户")
        tapCharacter(body, "@用户")
        tapCharacter(VIDEO_TITLE, "视频")
        assertDestinations("link:$COMMENTS_URL", "user:$USER_ID", "video:$VIDEO_ID")
    }

    @Test
    fun longPressOnNativeAtLinkOpensTextSelectionWithoutNavigating() {
        val body = "@评论作者：可以选择的热门评论"
        showCard(DynamicDesc(
            text = body,
            rich_text_nodes = listOf(
                RichTextNode(type = "AT", text = "@评论作者", rid = USER_ID.toString()),
                RichTextNode(type = "TEXT", text = "：可以选择的热门评论"),
            ),
        ))

        val point = characterInRoot(body, "评论作者")
        val host = compose.onNodeWithTag(HOST_TAG)
        val origin = host.fetchSemanticsNode().boundsInRoot.topLeft
        host.performTouchInput { longClick(point - origin) }

        compose.onNodeWithText("选择动态内容").assertIsDisplayed()
        compose.runOnIdle { assertTrue("Long press must not dispatch any navigation", destinations.isEmpty()) }
        compose.onNodeWithTag(DESTINATION_TAG).assertTextEquals("目的地：未导航")
    }

    @Test
    fun typeZeroLikeHintDoesNotBecomeACommentEntry() {
        val hint = "点赞提示不是评论"
        showCard(DynamicDesc(text = hint), interactionType = 0)

        tapCharacter(hint, "点赞提示")

        compose.runOnIdle {
            // A like hint may inherit the surrounding card's normal video action.
            assertTrue("Like hints must not acquire hot-comment navigation", destinations.all { it == "video:$VIDEO_ID" })
            assertTrue("A single pointer tap must not dispatch twice", destinations.size <= 1)
        }
        compose.onNodeWithText("目的地：link:$COMMENTS_URL").assertDoesNotExist()
    }

    @Test
    fun disablingCommentPreviewHidesHotRowButKeepsLikeHintAndVideo() {
        val visible = mutableStateOf(true)
        val body = "可隐藏的热门评论"
        showCard(DynamicDesc(text = body), previewVisible = visible, includeLikeHint = true)
        previewText(body).assertIsDisplayed()
        previewText(LIKE_HINT).assertIsDisplayed()

        compose.runOnIdle { visible.value = false }

        previewText(body).assertDoesNotExist()
        previewText(LIKE_HINT).assertIsDisplayed()
        tapCharacter(VIDEO_TITLE, "视频")
        assertDestinations("video:$VIDEO_ID")
    }

    @Test
    fun detailDoesNotRepeatTheHotPreviewButKeepsOtherCardContent() {
        val body = "详情页不重复显示的热门评论"
        showCard(DynamicDesc(text = body), includeLikeHint = true, isDetail = true)

        previewText(body).assertDoesNotExist()
        previewText(LIKE_HINT).assertIsDisplayed()
        previewText(VIDEO_TITLE).assertIsDisplayed()
    }

    @Test
    fun cachedUnlocatedPreviewIsHiddenButTheVideoRemainsActionable() {
        val body = "没有楼层的旧摘要"
        showCard(DynamicDesc(text = body), rootId = 0L)
        previewText(body).assertDoesNotExist()
        tapCharacter(VIDEO_TITLE, "视频")
        assertDestinations("video:$VIDEO_ID")
    }

    private fun showCard(
        desc: DynamicDesc,
        interactionType: Int = 1,
        previewVisible: State<Boolean> = mutableStateOf(true),
        includeLikeHint: Boolean = false,
        isDetail: Boolean = false,
        itemOverride: DynamicItem? = null,
        bodyDesc: DynamicDesc? = null,
        rootId: Long = 701L,
    ) {
        val interactions = buildList {
            add(DynamicInteractionItem(
                type = interactionType,
                desc = desc,
                extend_click_param = if (rootId > 0L) mapOf("comment_root_id" to rootId.toString()) else emptyMap(),
            ))
            if (includeLikeHint) add(DynamicInteractionItem(type = 0, desc = DynamicDesc(text = LIKE_HINT)))
        }
        val item = itemOverride ?: DynamicItem(
            id_str = DYNAMIC_ID,
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 123L, name = "动态作者"),
                module_dynamic = DynamicContentModule(
                    desc = bodyDesc,
                    major = DynamicMajor(archive = ArchiveMajor(bvid = VIDEO_ID, title = VIDEO_TITLE)),
                ),
                module_interaction = DynamicInteractionModule(items = interactions),
            ),
        )
        compose.setContent {
            val context = LocalContext.current
            val loader = remember(context) { ImageLoader.Builder(context).build() }
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDynamicCommentPreviewVisible provides previewVisible.value) {
                MaterialTheme {
                    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().testTag(HOST_TAG)) {
                        Text(
                            text = "目的地：${destinations.lastOrNull() ?: "未导航"}",
                            modifier = Modifier.testTag(DESTINATION_TAG),
                        )
                        DynamicCardV2(
                            item = item,
                            gifImageLoader = loader,
                            actions = DynamicCardActions(
                                navigation = DynamicCardNavigationActions(
                                    onVideoClick = { destinations += "video:$it" },
                                    onUserClick = { destinations += "user:$it" },
                                    onDynamicDetailClick = { destinations += "dynamic:$it" },
                                    onOpenLink = { destinations += "link:$it" },
                                ),
                                interaction = DynamicCardInteractionActions(
                                    onCommentClick = { destinations += "dynamic:$it" },
                                ),
                            ),
                            presentation = DynamicCardPresentation(isDetail = isDetail),
                        )
                    }
                }
            }
        }
    }

    private fun previewText(text: String): SemanticsNodeInteraction =
        compose.onNodeWithText(text, useUnmergedTree = true)

    private fun characterInRoot(text: String, substring: String): Offset {
        val node = previewText(text)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Expected one real text layout for $text", 1, layouts.size)
        val layout = layouts.single()
        val start = layout.layoutInput.text.text.indexOf(substring)
        assertTrue("Missing tap text $substring in ${layout.layoutInput.text.text}", start >= 0)
        val character = layout.getBoundingBox(start + substring.length / 2).center
        return node.fetchSemanticsNode().boundsInRoot.topLeft + character
    }

    private fun tapCharacter(text: String, substring: String) = tapInRoot(characterInRoot(text, substring))

    private fun tapRowBlank(text: String) {
        val node = previewText(text)
        val textBounds = node.fetchSemanticsNode().boundsInRoot
        val rowBounds = node.onParent().fetchSemanticsNode().boundsInRoot
        val point = Offset(rowBounds.right - 8f * density, textBounds.center.y)
        // Ensure this exercises row hit testing, not a character or LinkAnnotation.
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val lastCharacter = layout.getBoundingBox(layout.layoutInput.text.length - 1)
        assertTrue("The fixture must leave a blank region after the comment", point.x > textBounds.left + lastCharacter.right)
        tapInRoot(point)
    }

    private fun tapInRoot(point: Offset) {
        val host = compose.onNodeWithTag(HOST_TAG)
        val origin = host.fetchSemanticsNode().boundsInRoot.topLeft
        host.performTouchInput { click(point - origin) }
    }

    private fun assertDestinations(vararg expected: String) {
        compose.runOnIdle { assertEquals(expected.toList(), destinations.toList()) }
        compose.onNodeWithTag(DESTINATION_TAG).assertTextEquals("目的地：${expected.last()}")
    }

    private companion object {
        const val HOST_TAG = "hot-comment-card-host"
        const val DESTINATION_TAG = "hot-comment-destination"
        const val DYNAMIC_ID = "112233445566778899"
        const val BASE_COMMENTS_URL = "bilibili://following/detail/$DYNAMIC_ID?tab=comment"
        const val COMMENTS_URL = "$BASE_COMMENTS_URL&root_reply_id=701"
        const val VIDEO_ID = "BV1xx411c7mD"
        const val VIDEO_TITLE = "主卡片视频"
        const val USER_ID = 4422L
        const val LIKE_HINT = "UP主觉得很赞"
    }
}
