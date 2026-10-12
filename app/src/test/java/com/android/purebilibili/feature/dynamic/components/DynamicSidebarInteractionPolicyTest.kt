package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.util.HapticType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicSidebarInteractionPolicyTest {
    @Test
    fun userAvatarClick_triggersLightHapticBeforeFilteringUser() {
        val events = mutableListOf<String>()

        performDynamicSidebarUserAvatarClick(
            haptic = { type ->
                events += "haptic:${type.name}"
            },
            onClick = {
                events += "filter_user"
            }
        )

        assertEquals(listOf("haptic:${HapticType.LIGHT.name}", "filter_user"), events)
    }

    @Test
    fun globalWallpaperProtectsDynamicSidebarContainer() {
        val color = resolveDynamicSidebarContainerColor(
            surfaceColor = Color.White,
            globalWallpaperVisible = true
        )

        assertTrue(color.alpha >= 0.73f)
        assertTrue(color.alpha < 1f)
    }

    @Test
    fun globalWallpaperProtectsDynamicSidebarReturnHeader() {
        val color = resolveDynamicSidebarReturnHeaderColor(
            surfaceColor = Color.White,
            backgroundAlpha = 0.4f,
            globalWallpaperVisible = true
        )

        assertTrue(color.alpha >= 0.73f)
        assertTrue(color.alpha < 1f)
    }
}
