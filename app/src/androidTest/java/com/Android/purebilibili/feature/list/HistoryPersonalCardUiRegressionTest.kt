package com.android.purebilibili.feature.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryData
import com.android.purebilibili.feature.home.HomeAlignedVideoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryPersonalCardUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun stackedCardsAlignAuthorViewedTimeAndOverflowAcrossDifferentTitleHeights() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glassEnabled = mutableStateOf(false)
        val owners = listOf("UP 甲", "UP 乙")
        val viewedAt = listOf(1_700_000_000L, 1_700_003_600L)
        val titles = listOf("短标题", TITLE)
        val keys = listOf("short_title_card", "long_title_card")
        val items = titles.indices.map { index ->
            HistoryData(title = titles[index], author_name = owners[index], view_at = viewedAt[index]).toHistoryItem()
        }
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalAppUiStyle provides style.value,
            ) {
                ProvideAppThemeConfig(AppThemeConfig(liquidGlassEnabled = glassEnabled.value)) {
                    MaterialTheme {
                        HomeAlignedVideoRow(
                            indices = items.indices,
                            itemKeys = keys,
                            columns = 2,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.width(320.dp),
                        ) { index, modifier ->
                            HistoryPersonalCard(
                                item = items[index],
                                selected = false,
                                batchMode = false,
                                transitionEnabled = false,
                                onClick = {},
                                onLongClick = {},
                                onUpClick = null,
                                onAddToWatchLater = null,
                                onDelete = {},
                                modifier = modifier.testTag(keys[index]),
                                stacked = true,
                                matchRowHeight = true,
                            )
                        }
                    }
                }
            }
        }

        for (uiStyle in listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX)) {
            for (glass in listOf(false, true)) {
                rule.runOnIdle {
                    style.value = uiStyle
                    glassEnabled.value = glass
                }
                val authorBounds = owners.map { textBounds(it) }
                val timeBounds = viewedAt.map { textBounds(FormatUtils.formatHistoryViewTime(it)) }
                assertTrue(textBounds(TITLE).height > textBounds(titles.first()).height)
                assertEquals(authorBounds[0].bottom, authorBounds[1].bottom, 1f)
                assertEquals(timeBounds[0].bottom, timeBounds[1].bottom, 1f)
                val menuInsets = keys.map { key ->
                    val card = bounds(key)
                    val menu = rule.onNode(
                        hasContentDescription("历史记录操作") and hasAnyAncestor(hasTestTag(key)),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode().boundsInRoot
                    assertTrue(menu.center.x > card.center.x && menu.center.y > card.center.y)
                    (card.right - menu.right) to (card.bottom - menu.bottom)
                }
                assertEquals(menuInsets[0].first, menuInsets[1].first, 1f)
                assertEquals(menuInsets[0].second, menuInsets[1].second, 1f)
            }
        }
    }

    @Test
    fun titleUsesFullInfoWidthWhileMetadataStaysOutsideMenuTouchTarget() {
        val width = mutableStateOf(320)
        val fontScale = mutableStateOf(1f)
        setCard { width.value to fontScale.value }

        for ((widthDp, scale) in listOf(320 to 1f, 480 to 1f, 320 to 1.5f)) {
            rule.runOnIdle { width.value = widthDp; fontScale.value = scale }
            val card = bounds("history_card")
            val title = rule.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val owner = rule.onNodeWithText(OWNER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val menu = rule.onNode(
                hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertEquals("Title must not reserve a full-height menu lane", card.right - 8f, title.right, 1f)
            assertTrue("Menu must stay below the title", menu.top >= title.bottom)
            assertTrue("Menu must retain its minimum touch target", menu.width >= 48f && menu.height >= 48f)
            assertTrue("Metadata must stay outside the menu touch target", owner.right <= menu.left)
            assertTrue("Card must contain its menu", card.bottom >= menu.bottom)
        }
    }

    @Test
    fun enteringBatchModeDoesNotChangeTitleWidthOrKeepHiddenActionLane() {
        val batchMode = mutableStateOf(false)
        setCard(batchMode = { batchMode.value }) { 320 to 1f }
        val before = rule.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { batchMode.value = true }
        rule.onNodeWithContentDescription("历史记录操作", useUnmergedTree = true).assertDoesNotExist()
        val after = rule.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, after.width, 1f)
        assertEquals(bounds("history_card").right - 8f, after.right, 1f)
    }

    private fun setCard(batchMode: () -> Boolean = { false }, size: () -> Pair<Int, Float>) {
        val item = HistoryData(
            title = TITLE,
            author_name = OWNER,
            duration = 120,
            progress = 30,
            view_at = 1_700_000_000,
        ).toHistoryItem().copy(business = HistoryBusiness.ARCHIVE)
        rule.setContent {
            val (width, scale) = size()
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                MaterialTheme {
                    Box(Modifier.width(width.dp).testTag("history_card")) {
                        HistoryPersonalCard(
                            item = item,
                            selected = false,
                            batchMode = batchMode(),
                            transitionEnabled = false,
                            onClick = {},
                            onLongClick = {},
                            onUpClick = null,
                            onAddToWatchLater = null,
                            onDelete = {},
                        )
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun textBounds(text: String) =
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val TITLE = "这是一个用于检查历史记录标题右侧空间的较长视频标题，不应为底部菜单留下整列空白"
        const val OWNER = "这是一个比较长的UP主名称"
    }
}
