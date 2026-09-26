package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceThemeSegmentPolicyTest {

    @Test
    fun `resolveThemeModeSegmentOptions should keep expected order and use provided labels`() {
        val options = resolveThemeModeSegmentOptions(
            followSystemLabel = "Follow System",
            lightLabel = "Light",
            darkLabel = "Dark"
        )

        assertEquals(3, options.size)
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, options[0].value)
        assertEquals("Follow System", options[0].label)
        assertEquals(AppThemeMode.LIGHT, options[1].value)
        assertEquals("Light", options[1].label)
        assertEquals(AppThemeMode.DARK, options[2].value)
        assertEquals("Dark", options[2].label)
    }

    @Test
    fun `resolveDarkThemeStyleSegmentOptions should keep expected order and use provided labels`() {
        val options = resolveDarkThemeStyleSegmentOptions(
            defaultLabel = "Standard Black",
            amoledLabel = "AMOLED Black"
        )

        assertEquals(2, options.size)
        assertEquals(DarkThemeStyle.DEFAULT, options[0].value)
        assertEquals("Standard Black", options[0].label)
        assertEquals(DarkThemeStyle.AMOLED, options[1].value)
        assertEquals("AMOLED Black", options[1].label)
    }

    @Test
    fun `resolveAppIconStyleOptions exposes auto plus the two icon styles`() {
        val options = resolveAppIconStyleOptions()

        assertEquals(3, options.size)
        assertEquals(AppIconStyle.AUTO, options[0].value)
        assertEquals("跟随预设", options[0].label)
        assertEquals(AppIconStyle.THEME_CONTAINER, options[1].value)
        assertEquals("主题色容器", options[1].label)
        assertEquals(AppIconStyle.MD3_STANDARD, options[2].value)
        assertEquals("MD3 官方推荐", options[2].label)
    }

    @Test
    fun `resolveAppListItemStyleOptions exposes auto plus custom and native`() {
        val options = resolveAppListItemStyleOptions()

        assertEquals(3, options.size)
        assertEquals(AppListItemStyle.AUTO, options[0].value)
        assertEquals("跟随预设", options[0].label)
        assertEquals(AppListItemStyle.CUSTOM, options[1].value)
        assertEquals("自定义条目", options[1].label)
        assertEquals(AppListItemStyle.NATIVE, options[2].value)
        assertEquals("原生组件", options[2].label)
    }

    @Test
    fun `resolveAppLanguageSegmentOptions keeps four compact language slots`() {
        val options = resolveAppLanguageSegmentOptions(
            followSystemLabel = "系统",
            simplifiedChineseLabel = "简体",
            traditionalChineseLabel = "繁體",
            englishLabel = "EN"
        )

        assertEquals(4, options.size)
        assertEquals(listOf("系统", "简体", "繁體", "EN"), options.map { it.label })
        assertTrue(options.all { it.label.length <= 2 })
    }

    @Test
    fun `color preset option labels should expose BiliPai compatible names`() {
        val styleOptions = resolveColorStyleOptions()
        val specOptions = resolveColorSpecOptions()

        assertEquals(PaletteStyle.TonalSpot, styleOptions.first().value)
        assertEquals("TonalSpot", styleOptions.first().label)
        assertEquals(ColorSpec.SpecVersion.SPEC_2021, specOptions.first().value)
        assertEquals("SPEC_2021", specOptions.first().label)
    }

    @Test
    fun `color spec options should not expose duplicate labels`() {
        val specOptions = resolveColorSpecOptions()

        assertEquals(
            specOptions.map { it.label },
            specOptions.map { it.label }.distinct()
        )
        assertEquals(
            listOf(ColorSpec.SpecVersion.SPEC_2021, ColorSpec.SpecVersion.SPEC_2025),
            specOptions.map { it.value }
        )
    }

    @Test
    fun `advanced palette controls are only shown for custom color source`() {
        assertTrue(shouldShowMd3CustomColorControls(Md3ColorSource.CUSTOM))
        assertFalse(shouldShowMd3CustomColorControls(Md3ColorSource.FOLLOW_WALLPAPER))
    }

    @Test
    fun `advanced palette subtitle explains that overrides only affect material3`() {
        assertEquals(
            "已保留配置，仅切换到 Material 3 后生效",
            resolveAdvancedPaletteSubtitle(com.android.purebilibili.core.theme.AppUiStyle.MIUIX),
        )
        assertEquals(
            "自定义明暗模式的背景、文字与控件色",
            resolveAdvancedPaletteSubtitle(com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3),
        )
    }
}
