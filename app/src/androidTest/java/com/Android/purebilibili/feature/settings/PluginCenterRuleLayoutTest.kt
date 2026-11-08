package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.json.JsonRulePlugin
import com.android.purebilibili.core.plugin.json.LoadedJsonPlugin
import com.android.purebilibili.core.plugin.json.Rule
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule as JunitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PluginCenterRuleLayoutTest(private val style: AppUiStyle, private val glass: Boolean) {
    @get:JunitRule val compose = createComposeRule()

    @Test fun longRuleNameDoesNotSqueezeVersionAndManagementActionsWrap() {
        val loaded = LoadedJsonPlugin(
            JsonRulePlugin(
                id = "long_name", name = "过滤纪录片后缀UP主", version = "1.0.0", type = "feed",
                rules = listOf(Rule(field = "owner.name", op = "regex", value = JsonPrimitive("纪录片$"), action = "hide")),
            ),
            enabled = true, sourceUrl = null,
        )
        var edited = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides style,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                ) {
                    Box(Modifier.width(280.dp)) {
                        JsonPluginItem(loaded, 4, 0, {}, { edited = true }, {})
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("v1.0.0", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
        val title = compose.onNodeWithText("过滤纪录片后缀UP主", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNode(isToggleable()).fetchSemanticsNode().boundsInRoot
        assertTrue(title.right <= toggle.left)
        compose.onNodeWithText("管理规则").performClick()
        listOf("测试规则", "重置统计", "编辑", "删除").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("编辑").performClick()
        assertTrue(edited)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, glass={1}")
        fun materials(): List<Array<Any>> = AppUiStyle.entries.flatMap { style ->
            listOf(false, true).map { glass -> arrayOf<Any>(style, glass) }
        }
    }
}
