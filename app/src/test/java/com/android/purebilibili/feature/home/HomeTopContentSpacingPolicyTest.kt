package com.android.purebilibili.feature.home

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTopContentSpacingPolicyTest {

    @Test
    fun `md3 non glass tightens the reserved tabs to content padding`() {
        assertEquals(
            12.dp,
            resolveHomeTabsToContentTighteningDp(AppUiStyle.MATERIAL3, liquidGlassEnabled = false),
        )
    }

    @Test
    fun `glass dock and miuix keep the full reservation`() {
        assertEquals(0.dp, resolveHomeTabsToContentTighteningDp(AppUiStyle.MATERIAL3, liquidGlassEnabled = true))
        assertEquals(0.dp, resolveHomeTabsToContentTighteningDp(AppUiStyle.MIUIX, liquidGlassEnabled = false))
        assertEquals(0.dp, resolveHomeTabsToContentTighteningDp(AppUiStyle.MIUIX, liquidGlassEnabled = true))
    }
}
