package com.android.purebilibili.feature.home.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeNavigationMiuixStructureTest {

    @Test
    fun `home navigation runtime does not select Cupertino or Material icons`() {
        val strictSources = listOf(
            "HomeHeader.kt",
            "HomeNavigationIconPolicy.kt",
        ).map(::sourceText)

        strictSources.forEach { source ->
            assertFalse(source.contains("CupertinoIcons"))
            assertFalse(source.contains("androidx.compose.material.icons"))
            assertFalse(source.contains("fallbackIconFamily"))
        }

        val topBar = sourceText("TopBar.kt")
        assertFalse(topBar.contains("CupertinoIcons"))
        assertFalse(topBar.contains("fallbackIconFamily"))
    }

    @Test
    fun `bottom bar keeps Material pairs only for explicit MD3 style`() {
        val source = sourceText("BottomBar.kt")

        assertTrue(source.contains("enum class BottomNavItem"))
        assertTrue(source.contains("internal fun resolveMaterialBottomBarIcon("))
        assertTrue(source.contains("if (selected) Icons.Filled.Home else Icons.Outlined.Home"))
        assertFalse(source.contains("CupertinoIcons"))
        assertFalse(source.contains("val selectedIcon:"))
        assertFalse(source.contains("val unselectedIcon:"))
    }

    @Test
    fun `miuix auto floating bottom bar uses native Miuix icon pairs`() {
        val source = sourceText("BottomBar.kt")
        val floatingSource = sourceText("FloatingBottomBar.kt")
        val iconPolicySource = sourceText("HomeNavigationIconPolicy.kt")

        assertTrue(source.contains("SharedFloatingBottomBarIconStyle.MIUIX"))
        assertTrue(source.contains("BottomBarBlendedMiuixIcon("))
        assertTrue(source.contains("resolveHomeNavigationBarIcon(item, selected = false)"))
        assertTrue(source.contains("resolveHomeNavigationBarIcon(item, selected = true)"))
        assertTrue(source.contains("resolveMiuixPreferredHomeNavigationIcon(tabId = \"PARTITION\")"))
        assertFalse(source.contains("SharedFloatingBottomBarIconStyle.CUPERTINO"))
        assertFalse(source.contains("BottomBarBlendedCupertinoIcon("))
        assertTrue(source.contains("LocalFloatingBottomBarActiveContent.current"))
        assertTrue(source.contains("MiuixIcons.Search"))
        assertTrue(source.contains("resolveHomeNavigationBarIcon("))
        assertTrue(floatingSource.contains("LocalFloatingBottomBarActiveContent provides true"))
        assertTrue(source.contains("resolveMiuixBottomNavigationIcon(item, selected)"))
        assertTrue(iconPolicySource.contains("BottomNavItem.HOME -> MiuixIcons.Regular.Home"))
        assertTrue(iconPolicySource.contains("BottomNavItem.HISTORY -> MiuixIcons.Regular.Recent"))
        assertTrue(iconPolicySource.contains("BottomNavItem.SETTINGS -> MiuixIcons.Regular.Settings"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.LIVE -> MiuixIcons.Light.Recording"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.HOME -> R.drawable.ms_home_fill_24"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.HISTORY -> R.drawable.ms_history_fill_24"))
        assertFalse(iconPolicySource.contains("HomeNavigationIconRole.DYNAMIC -> R.drawable.ms_notifications_fill_24"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.DYNAMIC -> MiuixIcons.Light.Community"))
        assertFalse(iconPolicySource.contains("HomeNavigationIconRole.LISTEN_VIDEO -> R.drawable.ms_library_music_fill_24"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.LISTEN_VIDEO -> MiuixIcons.Light.Music"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.SETTINGS -> if (selected) MiuixIcons.Demibold.Settings else MiuixIcons.Light.Settings"))
        assertFalse(iconPolicySource.contains("R.drawable.ms_settings"))
        assertFalse(iconPolicySource.contains("HomeNavigationIconRole.PARTITION -> R.drawable.ms_grid_view_fill_24"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.PARTITION -> MiuixIcons.Light.GridView"))
        assertTrue(iconPolicySource.contains("return ImageVector.vectorResource(filledResource)"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.HOME -> ImageVector.vectorResource(R.drawable.bp_nav_home_outline_24)"))
        assertTrue(iconPolicySource.contains("HomeNavigationIconRole.HISTORY -> ImageVector.vectorResource(R.drawable.bp_nav_history_outline_24)"))
    }

    @Test
    fun `home header actions resolve native icons from the active theme`() {
        val source = sourceText("HomeHeader.kt")

        assertTrue(source.contains("resolveHomeTopActionIcons(semanticVisualPolicy.effectiveIconFamily)"))
        assertTrue(source.contains("search = MiuixIcons.Search"))
        assertTrue(source.contains("settings = resolveAppSettingsIcon()"))
        assertTrue(source.contains("inbox = resolveAppInboxIcon()"))
    }

    @Test
    fun `top category bar resolves native Miuix icons for Miuix theme`() {
        val source = sourceText("TopBar.kt")

        assertTrue(source.contains("AppSemanticIconFamily.MIUIX -> resolveMiuixPreferredHomeNavigationIcon("))
        assertTrue(source.contains("resolveMiuixPreferredHomeNavigationIcon(tabId = \"PARTITION\")"))
    }

    private fun sourceText(fileName: String): String = listOf(
        File("app/src/main/java/com/android/purebilibili/feature/home/components/$fileName"),
        File("src/main/java/com/android/purebilibili/feature/home/components/$fileName"),
    ).first { it.exists() }.readText()
}
