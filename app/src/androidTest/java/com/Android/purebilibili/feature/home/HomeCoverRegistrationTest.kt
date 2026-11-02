package com.android.purebilibili.feature.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchEntry
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry
import com.android.purebilibili.feature.home.components.cards.RegisterVisibleHomeCover
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class HomeCoverRegistrationTest {
    @get:Rule val rule = createComposeRule()

    @Before fun clearRegistry() = HomeCoverReturnPrefetchRegistry.clearForTest()
    @After fun cleanRegistry() = HomeCoverReturnPrefetchRegistry.clearForTest()

    @Test
    fun unrelatedRecompositionKeepsRecencyAndReentryRefreshesItWithoutDiscardingReturnSnapshot() {
        val visible = mutableStateOf(true)
        val label = mutableStateOf("initial")
        val first = HomeCoverReturnPrefetchEntry("BV_A", "a", "a", 320, 200)
        val second = HomeCoverReturnPrefetchEntry("BV_B", "b", "b", 320, 200)
        rule.setContent {
            // Update surrounding UI without changing cover visibility or identity.
            val text = label.value
            RegisterVisibleHomeCover(visible.value, "BV_A", "a", "a", 320, 200)
            BasicText(text)
        }
        rule.runOnIdle {
            assertEquals(listOf(first), HomeCoverReturnPrefetchRegistry.snapshot())
            HomeCoverReturnPrefetchRegistry.onCardVisible(second)
            label.value = "updated"
        }
        rule.runOnIdle {
            assertEquals(listOf(second, first), HomeCoverReturnPrefetchRegistry.snapshot())
            visible.value = false
        }
        rule.runOnIdle {
            assertEquals(listOf(second, first), HomeCoverReturnPrefetchRegistry.snapshot())
            visible.value = true
        }
        rule.runOnIdle {
            assertEquals(listOf(first, second), HomeCoverReturnPrefetchRegistry.snapshot())
        }
    }

    @Test
    fun measurementAndCoverIdentityChangesPublishExactDecodeSource() {
        val measured = mutableStateOf(false)
        val cover = mutableStateOf("a")
        rule.setContent {
            RegisterVisibleHomeCover(
                allowed = true,
                bvid = "BV_${cover.value}",
                url = cover.value,
                cacheKey = cover.value,
                widthPx = if (measured.value) 640 else 0,
                heightPx = if (measured.value) 400 else 0,
            )
        }
        rule.runOnIdle {
            assertTrue(HomeCoverReturnPrefetchRegistry.snapshot().isEmpty())
            measured.value = true
        }
        rule.runOnIdle {
            assertEquals(
                HomeCoverReturnPrefetchEntry("BV_a", "a", "a", 640, 400),
                HomeCoverReturnPrefetchRegistry.snapshot().single(),
            )
            cover.value = "b"
        }
        rule.runOnIdle {
            assertEquals(
                listOf(
                    HomeCoverReturnPrefetchEntry("BV_b", "b", "b", 640, 400),
                    HomeCoverReturnPrefetchEntry("BV_a", "a", "a", 640, 400),
                ),
                HomeCoverReturnPrefetchRegistry.snapshot(),
            )
        }
    }
}
