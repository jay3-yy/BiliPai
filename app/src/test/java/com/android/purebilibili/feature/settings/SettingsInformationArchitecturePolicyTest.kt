package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsInformationArchitecturePolicyTest {

    @Test
    fun appearanceHomeAndInteractionSettingsHaveIndependentOwners() {
        val appearance = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/screen/AppearanceSettingsScreen.kt")
        val animation = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/screen/AnimationSettingsScreen.kt")
        val navigation = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/screen/BottomBarSettingsScreen.kt")

        assertTrue(appearance.contains("contentMode == AppearanceSettingsContentMode.APPEARANCE"))
        assertTrue(appearance.contains("contentMode == AppearanceSettingsContentMode.HOME"))
        assertFalse(appearance.contains("onNavigateToAnimationSettings"))
        assertFalse(appearance.contains("setBottomBarSearchLayoutMode"))
        assertTrue(animation.contains("R.string.animation_title_haptic"))
        assertFalse(animation.contains("setBottomBarFloating"))
        assertTrue(navigation.contains("setBottomBarFloating"))
        assertTrue(navigation.contains("title = \"底栏搜索按钮\""))
        assertFalse(navigation.contains("setBottomBarSearchLayoutMode"))
    }

    @Test
    fun pluginsOnlyOwnPluginToolsWhileSystemOwnsDiagnosticsAndHelp() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        val pluginsBlock = source
            .substringAfter("SettingsRootCategory.PLUGINS_EXTENSIONS -> {")
            .substringBefore("SettingsRootCategory.SYSTEM_ABOUT -> {")
        val systemBlock = source
            .substringAfter("SettingsRootCategory.SYSTEM_ABOUT -> {")
            .substringBefore("@Composable\nfun SupportToolsSection(")

        assertTrue(pluginsBlock.contains("PluginCenterSection("))
        assertFalse(pluginsBlock.contains("DiagnosticsSection("))
        assertFalse(pluginsBlock.contains("SupportToolsSection("))
        assertTrue(systemBlock.contains("DiagnosticsSection("))
        assertTrue(systemBlock.contains("SupportToolsSection("))
        assertTrue(systemBlock.contains("AboutSection("))
    }

    @Test
    fun homeSearchResultNavigatesToDedicatedHomeSettingsPage() {
        val result = resolveSettingsSearchResults("推荐流卡片宽度").first {
            it.target == SettingsSearchTarget.HOME_FEED
        }

        assertEquals(BiliPaiNavKey.HomeSettings, resolveSettingsSearchNavigation(result))
    }

    @Test
    fun systemActionSearchResultsNavigateToTheirOwningCategory() {
        listOf(
            SettingsSearchTarget.OPEN_SOURCE_HOME,
            SettingsSearchTarget.CHECK_UPDATE,
            SettingsSearchTarget.TELEGRAM,
        ).forEach { target ->
            val result = SettingsSearchResult(
                target = target,
                title = target.name,
                subtitle = "",
                section = "",
            )

            assertEquals(
                BiliPaiNavKey.SettingsCategory(SettingsRootCategory.SYSTEM_ABOUT),
                resolveSettingsSearchNavigation(result),
            )
        }
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(File(path), File(normalizedPath)).firstOrNull { it.exists() }
        requireNotNull(sourceFile) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText().replace("\r\n", "\n")
    }
}
