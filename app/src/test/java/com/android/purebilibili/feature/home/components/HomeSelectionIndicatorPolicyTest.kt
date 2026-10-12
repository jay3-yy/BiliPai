package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.theme.AppUiStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSelectionIndicatorPolicyTest {

    @Test
    fun `miuix keeps capsule when liquid glass is disabled`() {
        assertEquals(
            HomeSelectionIndicatorStyle.CAPSULE,
            resolveHomeSelectionIndicatorStyle(
                uiStyle = AppUiStyle.MIUIX,
                liquidGlassEnabled = false,
            ),
        )
    }

    @Test
    fun `material3 uses underline when liquid glass is disabled`() {
        assertEquals(
            HomeSelectionIndicatorStyle.MD3_UNDERLINE,
            resolveHomeSelectionIndicatorStyle(
                uiStyle = AppUiStyle.MATERIAL3,
                liquidGlassEnabled = false,
            ),
        )
    }

    @Test
    fun `liquid glass uses capsule in every theme`() {
        AppUiStyle.entries.forEach { uiStyle ->
            assertEquals(
                HomeSelectionIndicatorStyle.CAPSULE,
                resolveHomeSelectionIndicatorStyle(
                    uiStyle = uiStyle,
                    liquidGlassEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `liquid glass takes priority over forced material underline`() {
        AppUiStyle.entries.forEach { uiStyle ->
            assertEquals(
                HomeSelectionIndicatorStyle.CAPSULE,
                resolveHomeSelectionIndicatorStyle(
                    uiStyle = uiStyle,
                    liquidGlassEnabled = true,
                    forceMaterialUnderline = true,
                ),
            )
        }
    }

    @Test
    fun `legacy home tabs force underline in every theme`() {
        AppUiStyle.entries.forEach { uiStyle ->
            assertEquals(
                HomeSelectionIndicatorStyle.MD3_UNDERLINE,
                resolveHomeSelectionIndicatorStyle(
                    uiStyle = uiStyle,
                    liquidGlassEnabled = false,
                    forceMaterialUnderline = true,
                ),
            )
        }
    }
}
