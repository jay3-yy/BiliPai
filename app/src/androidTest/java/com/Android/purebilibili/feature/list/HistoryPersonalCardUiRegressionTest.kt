package com.android.purebilibili.feature.list

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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryPersonalCardUiRegressionTest {
    @get:Rule val rule = createComposeRule()

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

    private companion object {
        const val TITLE = "这是一个用于检查历史记录标题右侧空间的较长视频标题，不应为底部菜单留下整列空白"
        const val OWNER = "这是一个比较长的UP主名称"
    }
}
