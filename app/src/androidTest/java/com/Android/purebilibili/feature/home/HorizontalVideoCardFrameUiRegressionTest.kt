package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HorizontalVideoCardFrameUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun seasonPostersKeepCompactSizeAcrossNarrowAndWideRows() {
        val width = mutableStateOf(280)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    HorizontalVideoCardFrame(
                        modifier = Modifier.width(width.value.dp).testTag("poster_row"),
                        coverModifier = Modifier.testTag("poster_cover"),
                        infoModifier = Modifier.testTag("poster_info"),
                        coverAspectRatio = 3f / 4f,
                        allowPortraitCover = true,
                        coverContent = { Box(Modifier.fillMaxSize()) },
                        infoContent = { Text("剧集标题\n更新至第197话\n21.5亿播放") },
                    )
                }
            }
        }
        for (rowWidth in listOf(280, 480, 100)) {
            rule.runOnIdle { width.value = rowWidth }
            val cover = bounds("poster_cover")
            val info = bounds("poster_info")
            assertTrue(cover.width <= 90f)
            assertTrue(cover.height <= 120f)
            assertEquals(3f / 4f, cover.width / cover.height, 0.02f)
            assertTrue(info.left >= cover.right)
            if (rowWidth >= 280) {
                assertEquals(90f, cover.width, 1f)
                assertEquals(120f, cover.height, 1f)
            }
        }
    }

    @Test
    fun ordinaryCoversKeepBaselineWidthAcrossPhoneAndWidePanes() {
        val widthDp = mutableStateOf(320)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    card("card", "视频标题", widthDp = widthDp.value)
                }
            }
        }
        val initialCover = bounds("card_cover")
        val initialInfoWidth = bounds("card_info").width
        assertEquals(160f, initialCover.width, 1f)
        assertEquals(100f, initialCover.height, 1f)
        assertEquals(bounds("card").bottom, initialCover.bottom, 1f)

        rule.runOnIdle { widthDp.value = 480 }
        val wideCover = bounds("card_cover")
        assertEquals(initialCover.width, wideCover.width, 1f)
        assertEquals(initialCover.height, wideCover.height, 1f)
        assertEquals(bounds("card_info").left, wideCover.right + 10f, 1f)
        assertTrue(bounds("card_info").width > initialInfoWidth)
    }

    @Test
    fun narrowHorizontalCardShrinksCoverAndLeavesRoomForMetadata() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    card("card", "窄窗口视频标题", widthDp = 200)
                }
            }
        }
        val cover = bounds("card_cover")
        val info = bounds("card_info")
        val card = bounds("card")
        assertTrue(cover.width < 140f)
        assertTrue(info.width > 0f)
        assertEquals(cover.right + 10f, info.left, 1f)
        assertTrue(info.right <= card.right)
    }

    @Test
    fun coverAndDurationReachCardBottomAcrossMaterialsAndRatios() {
        val material = mutableStateOf(0)
        val ratio = mutableStateOf(16f / 9f)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1.25f, 1f),
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = material.value == 2),
                LocalAppUiStyle provides if (material.value == 1) AppUiStyle.MIUIX else AppUiStyle.MATERIAL3,
            ) {
                MaterialTheme {
                    card("card", "两行视频标题用于检查封面底部背景", ratio = ratio.value)
                }
            }
        }
        for (style in 0..2) {
            for (aspect in listOf(16f / 9f, 16f / 10f, 4f / 3f)) {
                rule.runOnIdle { material.value = style; ratio.value = aspect }
                val cover = bounds("card_cover")
                val card = bounds("card")
                assertTrue(cover.height <= 126f)
                assertEquals(aspect, cover.width / cover.height, 0.02f)
                assertEquals(card.bottom, cover.bottom, 1f)
                assertEquals(cover.bottom, bounds("card_duration").bottom, 1f)
                assertEquals(cover.right + 13f, bounds("card_info").left, 1f)
            }
        }
    }

    @Test
    fun compactAndStackedLayoutsKeepTheirCoverRatio() {
        val stacked = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    card("card", "视频标题", compact = true, stacked = stacked.value)
                }
            }
        }
        val compactCover = bounds("card_cover")
        assertEquals(16f / 10f, compactCover.width / compactCover.height, 0.02f)
        rule.runOnIdle { stacked.value = true }
        val stackedCover = bounds("card_cover")
        assertEquals(16f / 10f, stackedCover.width / stackedCover.height, 0.02f)
        assertTrue(bounds("card_info").top >= stackedCover.bottom)
    }

    @Test
    fun singleAndTwoLineTitlesKeepCoverAndInfoAligned() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    card("short", "短标题")
                    card("long", "这是一个会换行的视频标题，用于检查搜索和相关推荐的封面大小")
                }
            }
        }
        val shortCover = bounds("short_cover")
        val longCover = bounds("long_cover")
        assertEquals(shortCover.width, longCover.width)
        assertEquals(shortCover.height, longCover.height)
        assertEquals(bounds("short_info").left, bounds("long_info").left)
        assertEquals(bounds("short").height, bounds("long").height)
    }

    @Test
    fun metadataUpdatesGrowTextWithoutChangingCoverOrTextOrigin() {
        val metadata = mutableStateOf("UP主")
        rule.setContent {
            MaterialTheme { card("card", "视频标题", metadata = metadata.value) }
        }
        val initial = bounds("card_cover")
        val initialInfoLeft = bounds("card_info").left
        rule.runOnIdle { metadata.value = "昨天 18:00\n已关注的UP主\n播放 13.3万\n弹幕 19" }
        val updated = bounds("card_cover")
        assertEquals(initial.width, updated.width)
        assertEquals(initialInfoLeft, bounds("card_info").left, 1f)
        assertEquals(initial.height, updated.height, 1f)
        assertTrue(bounds("card").bottom > updated.bottom)
        assertEquals(updated.bottom, bounds("card_duration").bottom, 1f)
    }

    @Test
    fun largeFontAndFullTitleGrowTextWithoutChangingCoverRatioOrClippingContent() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    card(
                        "card",
                        "完整标题在大字体和窄窗口下仍应全部显示，不能通过固定卡片高度裁掉文字",
                        maxLines = Int.MAX_VALUE,
                        widthDp = 260,
                        ratio = 4f / 3f,
                    )
                }
            }
        }
        val cover = bounds("card_cover")
        val info = bounds("card_info")
        val card = bounds("card")
        assertEquals(4f / 3f, cover.width / cover.height, 0.02f)
        assertTrue(info.left >= cover.right)
        assertTrue(info.bottom > cover.bottom)
        assertTrue(card.bottom >= info.bottom)
    }

    @Test
    fun actionsOnlyShareFooterWidthAcrossRowsStackedCardsAndLargeFonts() {
        val width = mutableStateOf(320)
        val stacked = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val actionCount = mutableStateOf(1)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                MaterialTheme {
                    actionCard(width.value, stacked.value, actionCount.value, showActions = true)
                }
            }
        }
        for ((widthDp, isStacked, scale) in listOf(
            Triple(320, false, 1f),
            Triple(480, false, 1f),
            Triple(220, false, 2f),
            Triple(160, true, 1f),
            Triple(160, true, 2f),
        )) {
            for (count in 1..2) {
                rule.runOnIdle {
                    width.value = widthDp
                    stacked.value = isStacked
                    fontScale.value = scale
                    actionCount.value = count
                }
                val card = bounds("action_card")
                val title = bounds("action_title")
                val footer = bounds("action_footer")
                val actions = bounds("actions")
                assertEquals(card.right - if (isStacked) 8f else 0f, title.right, 1f)
                assertTrue("Actions must stay below the complete title", actions.top >= title.bottom)
                assertTrue("Footer must not enter the action touch targets", footer.right <= actions.left)
                assertTrue("Every action retains its full touch target", actions.width >= 48f && actions.height >= 48f * count)
                assertTrue("Tall actions must remain inside the card", card.bottom >= actions.bottom)
                assertTrue("Metadata must remain inside the card", card.bottom >= footer.bottom)
            }
        }
    }

    @Test
    fun hidingActionsReleasesFooterWidthWithoutChangingHeadingWidth() {
        val showActions = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme { actionCard(320, false, 1, showActions.value) }
            }
        }
        val heading = bounds("action_title")
        rule.runOnIdle { showActions.value = false }
        rule.onNodeWithTag("actions").assertDoesNotExist()
        assertEquals(heading.width, bounds("action_title").width, 1f)
        assertEquals(heading.right, bounds("action_footer").right, 1f)
        assertEquals(heading.left, bounds("action_footer").left, 1f)
    }

    @androidx.compose.runtime.Composable
    private fun actionCard(widthDp: Int, stacked: Boolean, actionCount: Int, showActions: Boolean) {
        HorizontalVideoCardFrame(
            plainHorizontal = true,
            stacked = stacked,
            modifier = Modifier.width(widthDp.dp).testTag("action_card"),
            coverContent = { Box(Modifier.fillMaxSize()) },
            infoContent = {
                Text(
                    "长标题应当使用信息区完整宽度，不因右下角操作区缩窄",
                    Modifier.fillMaxWidth().testTag("action_title"),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                )
            },
            supportingContent = {
                Text(
                    "较长的作者名称与统计信息不能被操作按钮遮挡",
                    Modifier.fillMaxWidth().testTag("action_footer"),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                )
            },
            trailingContent = if (!showActions) null else ({
                Column(Modifier.testTag("actions")) {
                    repeat(actionCount) { Box(Modifier.size(48.dp)) }
                }
            }),
        )
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @androidx.compose.runtime.Composable
    private fun card(
        tag: String,
        title: String,
        metadata: String = "10-29 UP主\n播放 13.3万 弹幕 19",
        maxLines: Int = 2,
        widthDp: Int = 320,
        ratio: Float = 16f / 10f,
        compact: Boolean = false,
        stacked: Boolean = false,
    ) {
        HorizontalVideoCardFrame(
            plainHorizontal = true,
            modifier = Modifier.width(widthDp.dp).testTag(tag),
            coverModifier = Modifier.testTag("${tag}_cover"),
            infoModifier = Modifier.testTag("${tag}_info"),
            coverAspectRatio = ratio,
            compactPartition = compact,
            stacked = stacked,
            fillInfoHeight = true,
            coverContent = { Box(Modifier.fillMaxSize()) },
            coverOverlayContent = {
                Box(Modifier.align(Alignment.BottomEnd).size(8.dp).testTag("${tag}_duration"))
            },
            infoContent = {
                Text(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = maxLines,
                    minLines = minOf(2, maxLines),
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(metadata, fontSize = 12.sp, lineHeight = 16.sp)
            },
        )
    }
}
