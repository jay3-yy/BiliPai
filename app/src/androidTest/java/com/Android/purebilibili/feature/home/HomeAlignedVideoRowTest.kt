package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeAlignedVideoRowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun cardsShareTallestNaturalHeightAndRowCanShrink() {
        var tallHeight by mutableStateOf(96.dp)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                HomeAlignedVideoRow(
                    indices = 0..1,
                    itemKeys = listOf("short", "tall"),
                    columns = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(300.dp).testTag("row"),
                ) { index, slotModifier ->
                    Box(slotModifier.heightIn(min = if (index == 0) 40.dp else tallHeight).testTag("card_$index"))
                }
            }
        }
        rule.onNodeWithTag("card_0").assertWidthIsEqualTo(144.dp).assertHeightIsEqualTo(96.dp)
        rule.onNodeWithTag("card_1").assertWidthIsEqualTo(144.dp).assertHeightIsEqualTo(96.dp)
        rule.runOnIdle { tallHeight = 48.dp }
        rule.onNodeWithTag("row").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("card_0").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("card_1").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun partialRowKeepsColumnWidthAndRtlPlacement() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                HomeAlignedVideoRow(
                    indices = 0..1,
                    itemKeys = listOf("first", "second"),
                    columns = 3,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(300.dp).testTag("row"),
                ) { index, slotModifier ->
                    Box(slotModifier.heightIn(min = 40.dp).testTag("card_$index"))
                }
            }
        }
        rule.onNodeWithTag("card_0").assertWidthIsEqualTo(92.dp)
        rule.onNodeWithTag("card_1").assertWidthIsEqualTo(92.dp)
        val first = rule.onNodeWithTag("card_0").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("card_1").getUnclippedBoundsInRoot()
        assertEquals(12.dp, first.left - second.right)
    }

    @Test
    fun sevenColumnsDistributeRoundingPixelsWithinTheAvailableWidth() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                HomeAlignedVideoRow(
                    indices = 0..6,
                    itemKeys = List(7) { "card_$it" },
                    columns = 7,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(300.dp).testTag("row"),
                ) { index, slotModifier ->
                    Box(slotModifier.heightIn(min = 40.dp).testTag("card_$index"))
                }
            }
        }
        val row = rule.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val cards = (0..6).map { rule.onNodeWithTag("card_$it").getUnclippedBoundsInRoot() }
        assertEquals(row.left, cards.first().left)
        assertEquals(row.right, cards.last().right)
        cards.zipWithNext().forEach { (left, right) -> assertEquals(12.dp, right.left - left.right) }
    }
}
