package com.android.purebilibili.feature.settings

import com.android.purebilibili.R
import com.android.purebilibili.core.store.AppIconAppearance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconGroupsTest {

    @Test
    fun getIconGroups_onlyContainsCurrentBrandIcons() {
        val keys = getIconGroups().flatMap { group -> group.icons }.map { option -> option.key }.toSet()

        assertEquals(
            setOf(
                "icon_blue_snow_maid",
                "icon_blue_snow_maid_announcement",
                "icon_blue_snow_maid_front",
                "icon_3d",
                "icon_bilipai",
                "icon_bilipai_pink",
                "icon_bilipai_white",
                "icon_bilipai_monet"
            ),
            keys
        )
    }

    @Test
    fun resolveIconOptionPreviewRes_usesUnifiedAppearanceForMaidAndPlayWindow() {
        assertEquals(
            R.mipmap.ic_launcher_blue_snow_maid_dark_round,
            resolveIconOptionPreviewRes("icon_blue_snow_maid", AppIconAppearance.DARK)
        )
        assertEquals(
            R.mipmap.ic_launcher_blue_snow_maid_front_light_round,
            resolveIconOptionPreviewRes("icon_blue_snow_maid_front", AppIconAppearance.LIGHT)
        )
        assertEquals(
            R.mipmap.ic_launcher_blue_snow_maid_announcement_dark_round,
            resolveIconOptionPreviewRes("icon_blue_snow_maid_announcement", AppIconAppearance.DARK)
        )
        assertEquals(
            R.mipmap.ic_launcher_3d_light_round,
            resolveIconOptionPreviewRes("icon_3d", AppIconAppearance.LIGHT)
        )
        assertEquals(
            R.mipmap.ic_launcher_3d_dark_round,
            resolveIconOptionPreviewRes("icon_3d", AppIconAppearance.DARK)
        )
        assertEquals(
            R.mipmap.ic_launcher_bilipai_round,
            resolveIconOptionPreviewRes("icon_bilipai", AppIconAppearance.DARK)
        )
    }

    @Test
    fun systemAboutOverview_usesTheSelectedAppIconAndAppearance() {
        val source = java.io.File(
            "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"
        ).readText()

        assertTrue(source.contains("appIconKey = state.appIcon"))
        assertTrue(source.contains("resolveIconOptionPreviewRes(appIconKey, appIconAppearance)"))
        assertTrue(source.contains("model = appIconRes"))
    }
}
