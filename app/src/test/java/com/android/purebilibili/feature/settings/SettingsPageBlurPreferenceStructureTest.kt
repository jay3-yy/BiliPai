package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsPageBlurPreferenceStructureTest {
    @Test
    fun settingsPageChromeKeepsFrostedAndProgressivePreferencesSeparate() {
        val source = locate(
            "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsPageScaffold.kt"
        ).readText()

        assertTrue(source.contains("topBarBlurEnabled: Boolean? = null"))
        assertTrue(source.contains("val headerBlurEnabled = topBarBlurEnabled ?: appThemeConfig.headerBlurEnabled"))
        assertTrue(source.contains("appThemeConfig.progressiveTopBlurEnabled && !headerBlurEnabled"))
        assertTrue(source.contains(".unifiedBlur("))
        assertTrue(source.contains("Modifier.hazeSourceCompat(hazeState)"))
        assertTrue(source.contains("surfaceType = BlurSurfaceType.HEADER"))
        assertTrue(source.contains("BiliPaiImmersiveTopBar("))
        assertTrue(source.contains("Modifier.layerBackdrop(backdrop)"))
        assertFalse(source.contains("TopReadabilityChrome"))
        assertTrue(source.contains("top = padding.calculateTopPadding()"))
        assertTrue(source.contains("if (progressiveBlurEnabled) rememberLayerBackdrop()"))
        assertTrue(source.contains("Column(modifier = scrollModifier)"))
        assertTrue(source.contains("fun settingsScrollContentPadding("))
        assertFalse(source.contains("scrollModifier.padding(padding)"))
        assertFalse(source.contains(".fillMaxSize()\n                .hazeSourceCompat(state = hazeState)"))
        val plugins = locate(
            "src/main/java/com/android/purebilibili/feature/settings/screen/PluginsScreen.kt"
        ).readText()
        assertTrue(plugins.contains("settingsScrollContentPadding("))
        assertTrue(plugins.contains("SettingsPageScaffold("))
    }

    @Test
    fun nonGlassMiuixSettingsRootUsesCollapsibleLargeTitleAndSolidFallback() {
        val scaffold = locate(
            "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsPageScaffold.kt"
        ).readText()
        val settings = locate(
            "src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt"
        ).readText()

        assertTrue(scaffold.contains("topBarStyle: AppTopBarStyle = AppTopBarStyle.CENTERED"))
        assertTrue(scaffold.contains("rememberAppTopBarCollapseBehavior()"))
        assertTrue(scaffold.contains("modifier.appTopBarNestedScroll(collapseBehavior)"))
        assertTrue(scaffold.contains("!topBarBlurActive"))
        assertTrue(settings.contains("destination == SettingsNavDestination.Home"))
        assertTrue(settings.contains("AppTopBarStyle.LARGE"))
    }

    @Test
    fun miuixSettingsPageUsesUpstreamSurfaceIndependentlyFromGlassChrome() {
        val scaffold = locate(
            "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsPageScaffold.kt"
        ).readText()

        assertTrue(scaffold.contains("AppUiStyle.MIUIX -> AppSurfaceTokens.surface()"))
        assertTrue(scaffold.contains("containerColor = if (!topBarBlurActive)"))
        assertTrue(scaffold.contains("Color.Transparent"))
    }

    private fun locate(path: String): File {
        return listOf(File(path), File("app/$path"))
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $path from cwd")
    }
}
