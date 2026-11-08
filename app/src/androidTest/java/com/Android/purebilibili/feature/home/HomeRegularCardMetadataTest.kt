package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.VideoCardDurationPublishRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRegularCardMetadataTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun equalHeightSearchCardsKeepFooterAtBottomWhenTitleChanges() {
        var title by mutableStateOf("短标题")
        var cardHeight by mutableStateOf(300)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalPageImageLoadingAllowed provides false,
            ) {
                MaterialTheme {
                    ElegantVideoCard(
                        video = VideoItem(
                            bvid = "BV_search_footer",
                            title = title,
                            owner = Owner(name = "作者"),
                            duration = 180,
                        ),
                        index = 0,
                        modifier = Modifier.width(180.dp).height(cardHeight.dp).testTag("search_card"),
                        animationEnabled = false,
                        transitionEnabled = false,
                        titleMinLines = 1,
                        bottomAlignedMetadata = true,
                        showPublishTime = true,
                        homeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
                        onWatchLater = {},
                        onClick = { _, _ -> },
                    )
                }
            }
        }
        for ((nextTitle, nextHeight) in listOf(
            "短标题" to 300,
            "这是一个需要换行显示的搜索视频标题" to 300,
            "短标题" to 360,
        )) {
            rule.runOnIdle { title = nextTitle; cardHeight = nextHeight }
            val card = rule.onNodeWithTag("search_card").getUnclippedBoundsInRoot()
            val duration = rule.onNodeWithText(FormatUtils.formatDuration(180)).getUnclippedBoundsInRoot()
            val menu = rule.onNodeWithContentDescription("更多操作").getUnclippedBoundsInRoot()
            assertTrue(menu.left.value > card.left.value + card.width.value / 2f)
            assertEquals(card.bottom.value - 6f, duration.bottom.value, 1f)
        }
    }

    @Test
    fun statisticsStayOneSpaceApartInWrappingAndSingleLineRows() {
        var singleLine by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        HorizontalVideoStatRow(
                            "123",
                            "45",
                            textStyle = style,
                            singleLine = singleLine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppText(" ", style = style, modifier = Modifier.testTag("one_space"))
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            for (oneLine in listOf(false, true)) {
                rule.runOnIdle { fontScale = scale; singleLine = oneLine }
                val play = rule.onNodeWithText("123").getUnclippedBoundsInRoot()
                val danmaku = rule.onNodeWithText("45").getUnclippedBoundsInRoot()
                val space = rule.onNodeWithTag("one_space").getUnclippedBoundsInRoot()
                assertEquals(play.top, danmaku.top)
                assertEquals(space.width.value, danmaku.left.value - play.right.value - 13f - 2f, 1f)
            }
        }
    }

    @Test
    fun largeFontAndLongCountsKeepOneRowAndRetainFullSemanticText() {
        var count by mutableStateOf("1")
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(112.dp).testTag("statistics")) {
                        HorizontalVideoStatRow(count, "123456789", singleLine = true)
                    }
                }
            }
        }
        val initialHeight = rule.onNodeWithTag("statistics").getUnclippedBoundsInRoot().height
        rule.runOnIdle { count = "12345678901234567890" }
        assertEquals(initialHeight, rule.onNodeWithTag("statistics").getUnclippedBoundsInRoot().height)
        rule.onNodeWithText("12345678901234567890").assertExists()
    }

    @Test
    fun publishTimeArrivalDoesNotAddAnotherFooterRowInNarrowColumns() {
        var date by mutableStateOf("发布时间未知")
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Column(Modifier.width(112.dp).testTag("footer")) {
                        VideoCardDurationPublishRow(
                            durationText = "12:34:56",
                            publishTimeText = date,
                            emphasizePublishTime = false,
                            publishTimeColor = Color.Gray,
                            topSpacing = 4.dp,
                            singleLine = true,
                        )
                    }
                }
            }
        }
        val initialHeight = rule.onNodeWithTag("footer").getUnclippedBoundsInRoot().height
        rule.runOnIdle { date = "发布于 2026-11-01" }
        assertEquals(initialHeight, rule.onNodeWithTag("footer").getUnclippedBoundsInRoot().height)
        rule.onNodeWithText("发布于 2026-11-01 · 12:34:56").assertExists()
    }
}
