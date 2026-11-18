package com.android.purebilibili.feature.settings

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.R
import com.android.purebilibili.core.store.SettingsIconGlyphs
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.theme.iOSRed
import com.android.purebilibili.core.ui.AppSemanticAccentPalette
import com.android.purebilibili.core.ui.AppSemanticVisualPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsEntryVisualPolicyTest {
    @Test
    fun originalGlyphsRestoreSearchResultIconsWithoutChangingPresentation() {
        val phosphor = resolveSettingsEntryVisual(SettingsSearchTarget.HOME_FEED)
        val original = resolveSettingsEntryVisual(
            SettingsSearchTarget.HOME_FEED,
            glyphs = SettingsIconGlyphs.ORIGINAL,
        )

        assertEquals(R.drawable.ph_house_24, phosphor.iconResId)
        assertEquals(phosphor.copy(iconResId = R.drawable.ms_home_24), original)
    }

    private val materialPolicy = AppSemanticVisualPolicy.material(
        AppSemanticAccentPalette(
            primary = Color(0xFF112233),
            secondary = Color(0xFF223344),
            tertiary = Color(0xFF334455),
            error = Color(0xFF445566),
        )
    )

    @Test
    fun materialEntries_useSharedSettingsVectors() {
        val expected = mapOf(
            SettingsSearchTarget.HOME_FEED to R.drawable.ph_house_24,
            SettingsSearchTarget.INTERACTION_COMMENT to R.drawable.ph_chat_circle_24,
            SettingsSearchTarget.DATA_BACKUP to R.drawable.ph_archive_24,
            SettingsSearchTarget.DIAGNOSTICS to R.drawable.ph_terminal_window_24,
            SettingsSearchTarget.APPEARANCE to R.drawable.ph_paint_brush_broad_24,
            SettingsSearchTarget.PERMISSION to R.drawable.ph_shield_check_24,
            SettingsSearchTarget.FULLSCREEN_GESTURE to R.drawable.ph_hand_tap_24,
            SettingsSearchTarget.BLOCKED_LIST to R.drawable.ph_prohibit_24,
            SettingsSearchTarget.DONATE to R.drawable.ph_gift_24,
        )
        expected.forEach { (target, resource) ->
            val visual = resolveSettingsEntryVisual(target, materialPolicy)
            assertEquals(resource, visual.iconResId)
            assertNull(visual.icon)
        }
    }

    @Test
    fun materialEntries_shareTheSuppliedNeutralColor() {
        val neutral = Color(0xFF61666D)
        SettingsSearchTarget.entries.forEach { target ->
            assertEquals(neutral, resolveSettingsEntryVisual(target, materialPolicy, neutral).iconTint)
        }
    }

    @Test
    fun miuixEntries_keepCategoryColors() {
        assertEquals(
            iOSPink,
            resolveSettingsEntryVisual(SettingsSearchTarget.APPEARANCE, materialPolicy, colorful = true).iconTint,
        )
        assertEquals(
            iOSBlue,
            resolveSettingsEntryVisual(SettingsSearchTarget.CLEAR_CACHE, materialPolicy, colorful = true).iconTint,
        )
        assertEquals(
            iOSRed,
            resolveSettingsEntryVisual(SettingsSearchTarget.DONATE, materialPolicy, colorful = true).iconTint,
        )
    }

    @Test
    fun helpCategory_usesInformationIconInsteadOfDiagnosticTerminal() {
        val category = SettingsRootCategory.SYSTEM_ABOUT
        assertEquals(SettingsSearchTarget.ABOUT_SUPPORT, category.searchTarget)
        assertEquals(
            R.drawable.ph_info_24,
            resolveSettingsEntryVisual(category.searchTarget, materialPolicy).iconResId,
        )
        assertEquals(category, resolveSettingsRootCategoryForSearchTarget(category.searchTarget))
    }
}
