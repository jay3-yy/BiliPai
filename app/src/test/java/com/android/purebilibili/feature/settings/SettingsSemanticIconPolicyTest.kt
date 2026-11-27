package com.android.purebilibili.feature.settings

import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSemanticIconPolicyTest {
    @Test
    fun materialPreset_usesSharedSettingsVectors() {
        assertEquals(R.drawable.ph_house_24, resolveSettingsIconResource(SettingsIconRole.HOME_FEED))
        assertEquals(R.drawable.ph_archive_24, resolveSettingsIconResource(SettingsIconRole.DATA_BACKUP))
        assertEquals(R.drawable.ph_terminal_window_24, resolveSettingsIconResource(SettingsIconRole.DIAGNOSTICS))
    }

    @Test
    fun miuixPreset_keepsMiuixSizingAroundSemanticVectors() {
        assertEquals(19, resolveSettingsSemanticIconSizeDp(SettingsIconRole.HOME_FEED, AppSemanticIconFamily.MIUIX))
    }

    @Test
    fun batteryStatus_usesBatteryResourceInsteadOfPhoneGlyph() {
        assertEquals(
            R.drawable.ph_battery_full_24,
            resolveSettingsIconResource(SettingsIconRole.BATTERY_STATUS),
        )
    }

    @Test
    fun commentCheckHistory_usesDedicatedIconDistinctFromTelemetryAndPermission() {
        val history = resolveSettingsIconResource(SettingsIconRole.COMMENT_CHECK_HISTORY)
        assertEquals(R.drawable.ic_settings_comment_check_history_24, history)
        assertTrue(history != resolveSettingsIconResource(SettingsIconRole.TELEMETRY_PRIVACY))
        assertTrue(history != resolveSettingsIconResource(SettingsIconRole.PERMISSION))
        assertTrue(history != resolveSettingsIconResource(SettingsIconRole.COMMENT_VISIBILITY_CHECK))
    }

    @Test
    fun settingsSource_hasNoLegacyComposeMaterialIconReferences() {
        val source = File("app/src/main/java/com/android/purebilibili/feature/settings")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue("androidx.compose.material.icons" !in source)
        assertTrue(Regex("""(?<!App)(?<!Miuix)Icons\.""").find(source) == null)
    }
}
