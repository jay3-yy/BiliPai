package com.android.purebilibili.feature.space

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.model.response.SpaceVip
import com.android.purebilibili.data.model.response.SpaceVipLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaceHeaderIdentityUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun annualVipDoesNotSqueezeOrCoverNicknameAcrossWidthsAndFontScales() {
        val scenario = mutableStateOf(Scenario())
        rule.setContent {
            val current = scenario.value
            CompositionLocalProvider(
                LocalDensity provides Density(1f, current.fontScale),
                LocalAppUiStyle provides current.style,
            ) {
                MaterialTheme {
                    Box(Modifier.width(current.width.dp).testTag("identity")) {
                        SpaceHeaderIdentityInfo(
                            userInfo = SpaceUserInfo(
                                name = current.name,
                                level = 6,
                                vip = SpaceVip(
                                    status = 1,
                                    type = 2,
                                    label = SpaceVipLabel("年度大会员"),
                                ),
                            ),
                            officialBadge = null,
                            onLiveClick = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        for (style in AppUiStyle.entries) {
            for (width in listOf(132, 180, 360)) {
                for (fontScale in listOf(1f, 1.5f, 2f)) {
                    for (name in listOf("测试昵称", "这是一个需要省略显示的很长的用户昵称")) {
                        val current = Scenario(width, fontScale, style, name)
                        rule.runOnIdle { scenario.value = current }
                        val nickname = rule.onNodeWithText(name, useUnmergedTree = true)
                            .assertIsDisplayed()
                        val vip = rule.onNodeWithText("年度大会员", useUnmergedTree = true)
                            .assertIsDisplayed()
                        val level = rule.onNodeWithContentDescription("等级6", useUnmergedTree = true)
                            .assertIsDisplayed()
                        val nameBounds = nickname.fetchSemanticsNode().boundsInRoot
                        val vipBounds = vip.fetchSemanticsNode().boundsInRoot
                        val levelBounds = level.fetchSemanticsNode().boundsInRoot
                        val parent = rule.onNodeWithTag("identity").fetchSemanticsNode().boundsInRoot

                        assertDisjoint(nameBounds, vipBounds, current)
                        assertDisjoint(nameBounds, levelBounds, current)
                        assertDisjoint(levelBounds, vipBounds, current)
                        for (bounds in listOf(nameBounds, vipBounds, levelBounds)) {
                            assertTrue("Identity must stay inside its column: $current", parent.contains(bounds.topLeft))
                            assertTrue("Identity must not overflow its column: $current", bounds.right <= parent.right + 1f)
                        }

                        val layouts = mutableListOf<TextLayoutResult>()
                        nickname.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single()
                        // maxLines=1 的省略会令 didOverflowHeight 为 true；检查实际显示行的边界。
                        assertTrue("Nickname line must fit its allocated height: $current",
                            layout.getLineTop(0) >= 0f && layout.getLineBottom(0) <= layout.size.height + 1f)
                        if (name == "测试昵称" && width >= 180) {
                            assertFalse("Badges must not force a fitting nickname to ellipsize: $current", layout.isLineEllipsized(0))
                        }
                    }
                }
            }
        }
    }

    private fun assertDisjoint(first: Rect, second: Rect, scenario: Scenario) {
        assertFalse("Identity elements must not overlap: $scenario", first.overlaps(second))
    }

    private data class Scenario(
        val width: Int = 180,
        val fontScale: Float = 1f,
        val style: AppUiStyle = AppUiStyle.MATERIAL3,
        val name: String = "测试昵称",
    )
}
