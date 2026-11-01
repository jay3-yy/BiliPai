package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.cards.VideoCardDurationPublishRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeRegularCardMetadataTest {
    @get:Rule val rule = createComposeRule()

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
