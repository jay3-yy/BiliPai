package com.android.purebilibili.feature.partition

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalNativeArticleCardMotionEnabled
import com.android.purebilibili.core.ui.LocalNativeCardNavigate
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.navigation3.BiliPaiNavKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PartitionSeasonCardTransitionTest(private val style: AppUiStyle, private val glass: Boolean) {
    @get:Rule val rule = createComposeRule()

    @After fun clearSource() = rule.runOnIdle { CardPositionManager.clear() }

    @Test fun seasonCardRecordsSystemSourceEvenWhenContainerAnimationIsOff() {
        val hasNativeHost = mutableStateOf(true)
        var target: BiliPaiNavKey? = null
        var fallbackClicks = 0
        rule.setContent {
            val navigate: ((BiliPaiNavKey) -> Unit)? = if (hasNativeHost.value) ({ target = it }) else null
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides style,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                    LocalVideoCardSourceTrackingEnabled provides true,
                    LocalVideoCardSharedElementSourceRoute provides "partition",
                    LocalNativeArticleCardMotionEnabled provides false,
                    LocalNativeCardNavigate provides navigate,
                ) {
                    PartitionSeasonRankCard(
                        video = VideoItem(id = 42L, title = "分区剧集", contentType = "pgc_rank"),
                        onClick = { fallbackClicks++ },
                        modifier = Modifier.width(280.dp).testTag("partition_season"),
                    )
                }
            }
        }
        for (hasHost in listOf(true, false)) {
            rule.runOnIdle { hasNativeHost.value = hasHost }
            rule.onNodeWithTag("partition_season").performClick()
            rule.runOnIdle {
                assertEquals("partition:bangumi_42", CardPositionManager.lastClickedVideoSourceKey)
                val anchor = requireNotNull(VideoCardSourceInstances.takeAnchor("bangumi_42"))
                assertNotNull(anchor.bounds(windowCoordinates = true))
                if (hasHost) {
                    assertEquals(BiliPaiNavKey.BangumiPlayer(seasonId = 42L, epId = 0L), target)
                    assertEquals(0, fallbackClicks)
                } else assertEquals(1, fallbackClicks)
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, glass={1}")
        fun materials(): List<Array<Any>> = AppUiStyle.entries.flatMap { style ->
            listOf(false, true).map { glass -> arrayOf<Any>(style, glass) }
        }
    }
}
