package com.android.purebilibili.feature.settings

import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSemanticIconPolicyTest {
    @Test
    fun materialPreset_usesMaterialSymbolResources() {
        assertEquals(R.drawable.ms_home_24, resolveSettingsMaterialSymbolResource(SettingsIconRole.HOME_FEED))
        assertEquals(R.drawable.ms_backup_24, resolveSettingsMaterialSymbolResource(SettingsIconRole.DATA_BACKUP))
        assertEquals(R.drawable.ms_terminal_24, resolveSettingsMaterialSymbolResource(SettingsIconRole.DIAGNOSTICS))
    }

    @Test
    fun everyMaterialRole_hasAUniqueResource() {
        val duplicates = SettingsIconRole.entries.groupBy(::resolveSettingsMaterialSymbolResource).filterValues { it.size > 1 }
        assertTrue(duplicates.isEmpty(), duplicates.toString())
    }

    @Test
    fun miuixPreset_keepsMiuixSizingAroundSemanticVectors() {
        assertEquals(19, resolveSettingsSemanticIconSizeDp(SettingsIconRole.HOME_FEED, AppSemanticIconFamily.MIUIX))
    }

    @Test
    fun batteryStatus_usesBatteryResourceInsteadOfPhoneGlyph() {
        assertEquals(
            R.drawable.ms_battery_full_24,
            resolveSettingsMaterialSymbolResource(SettingsIconRole.BATTERY_STATUS),
        )
    }

    @Test
    fun allSettingsSkinsUseRoleSpecificLocalVectors() {
        val source = File(
            "app/src/main/java/com/android/purebilibili/feature/settings/SettingsSemanticIconPolicy.kt"
        ).readText()
        val rememberFunction = source
            .substringAfter("internal fun rememberSettingsSemanticIcon(")
            .substringBefore("internal fun resolveSettingsMaterialSymbolResource(")

        assertTrue(rememberFunction.contains("resolveSettingsMaterialSymbolResource(role)"))
        assertTrue(!rememberFunction.contains("resolveMiuixSettingsSemanticIcon(role)"))
    }

    @Test
    fun settingsSource_hasNoLegacyComposeMaterialIconReferences() {
        val source = File("app/src/main/java/com/android/purebilibili/feature/settings")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue("androidx.compose.material.icons" !in source)
        assertTrue(Regex("""(?<!App)(?<!Miuix)Icons\.""").find(source) == null)
    }
}
