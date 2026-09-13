package com.android.purebilibili.core.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.theme.iOSRed
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.ui.AppIconStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveListComponentPolicyTest {

    @Test
    fun `material3 style should use more native material search and group geometry`() {
        val spec = resolveAdaptiveListComponentVisualSpec(AppUiStyle.MATERIAL3)

        assertEquals(56, spec.searchBarHeightDp)
        assertEquals(28, spec.searchBarCornerRadiusDp)
        assertEquals(40, spec.iconContainerSizeDp)
        assertEquals(22, spec.iconGlyphSizeDp)
        assertEquals(24, spec.groupCornerRadiusDp)
        assertEquals(0.14f, spec.iconBackgroundAlpha, 0.0001f)
        assertEquals(0f, spec.dividerThicknessDp, 0.0001f)
        assertEquals(18, spec.dividerStartIndentDp)
        assertEquals(3, spec.groupTonalElevationDp)
    }

    @Test
    fun `miuix style should soften grouped settings geometry`() {
        val spec = resolveAdaptiveListComponentVisualSpec(AppUiStyle.MIUIX)

        assertEquals(48, spec.searchBarHeightDp)
        assertEquals(22, spec.searchBarCornerRadiusDp)
        assertEquals(20, spec.groupCornerRadiusDp)
        assertEquals(16, spec.sectionStartPaddingDp)
        assertEquals(16, spec.dividerStartIndentDp)
        assertEquals(10, spec.iconCornerRadiusDp)
        assertEquals(38, spec.iconContainerSizeDp)
        assertEquals(20, spec.iconGlyphSizeDp)
        assertEquals(0.18f, spec.iconBackgroundAlpha, 0.0001f)
    }

    @Test
    fun `miuix style should use denser list row spacing`() {
        val spec = resolveAdaptiveListRowVisualSpec(AppUiStyle.MIUIX)

        assertEquals(16, spec.insideHorizontalPaddingDp)
        assertEquals(14, spec.insideVerticalPaddingDp)
        assertEquals(14, spec.trailingIconSizeDp)
        assertEquals(6, spec.trailingSpacingDp)
    }

    @Test
    fun `material3 style should keep roomier shared list row spacing`() {
        val spec = resolveAdaptiveListRowVisualSpec(AppUiStyle.MATERIAL3)

        assertEquals(18, spec.insideHorizontalPaddingDp)
        assertEquals(16, spec.insideVerticalPaddingDp)
        assertEquals(16, spec.trailingIconSizeDp)
        assertEquals(8, spec.trailingSpacingDp)
        assertEquals(48, spec.minTouchTargetHeightDp)
    }

    @Test
    fun `material3 style should map all settings icon tints to theme primary`() {
        val colorScheme = darkColorScheme()

        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSBlue, AppUiStyle.MATERIAL3, colorScheme)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSGreen, AppUiStyle.MATERIAL3, colorScheme)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSPurple, AppUiStyle.MATERIAL3, colorScheme)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSRed, AppUiStyle.MATERIAL3, colorScheme)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSSystemGray, AppUiStyle.MATERIAL3, colorScheme)
        )
    }

    @Test
    fun `filled settings icons should use opaque containers and contrasting glyphs`() {
        val colorScheme = lightColorScheme(primary = Color(0xFF3366CC))

        assertEquals(
            1f,
            resolveAdaptivePreferenceIconBackgroundAlpha(
                treatment = AppPreferenceIconTreatment.FILLED,
                tonalAlpha = 0.14f,
            ),
        )
        assertEquals(
            colorScheme.onPrimary,
            resolveAdaptivePreferenceIconContentColor(colorScheme.primary, colorScheme),
        )
        assertEquals(
            Color.White,
            resolveAdaptivePreferenceIconContentColor(iOSBlue, colorScheme),
        )
        assertEquals(
            colorScheme.secondary,
            resolveAdaptivePreferenceIconContainerColor(
                iconTint = iOSBlue,
                semanticTint = colorScheme.secondary,
                treatment = AppPreferenceIconTreatment.FILLED,
            ),
        )
    }

    @Test
    fun `theme container icon style uses opaque secondary container with on color glyphs`() {
        val colorScheme = lightColorScheme()

        assertEquals(
            colorScheme.secondaryContainer,
            resolveAdaptivePreferenceIconContainerColor(
                iconTint = iOSBlue,
                semanticTint = colorScheme.primary,
                treatment = AppPreferenceIconTreatment.FILLED,
                iconStyle = AppIconStyle.THEME_CONTAINER,
                colorScheme = colorScheme,
            ),
        )
        assertEquals(
            1f,
            resolveAdaptivePreferenceIconBackgroundAlpha(
                treatment = AppPreferenceIconTreatment.FILLED,
                tonalAlpha = 0.14f,
                iconStyle = AppIconStyle.THEME_CONTAINER,
            ),
        )
        assertEquals(
            colorScheme.onSecondaryContainer,
            resolveAdaptivePreferenceIconContentColor(
                containerColor = colorScheme.secondaryContainer,
                colorScheme = colorScheme,
                iconStyle = AppIconStyle.THEME_CONTAINER,
            ),
        )
        assertEquals(
            colorScheme.onSecondaryContainer,
            resolveAdaptivePreferenceIconGlyphColor(
                treatment = AppPreferenceIconTreatment.TONAL,
                iconStyle = AppIconStyle.THEME_CONTAINER,
                containerContentColor = colorScheme.onSecondaryContainer,
                semanticIconColor = colorScheme.secondaryContainer,
            ),
        )
    }

    @Test
    fun `md3 standard icon style is monochrome without container`() {
        val colorScheme = lightColorScheme()

        assertEquals(
            Color.Transparent,
            resolveAdaptivePreferenceIconContainerColor(
                iconTint = iOSBlue,
                semanticTint = colorScheme.primary,
                treatment = AppPreferenceIconTreatment.FILLED,
                iconStyle = AppIconStyle.MD3_STANDARD,
                colorScheme = colorScheme,
            ),
        )
        assertEquals(
            0f,
            resolveAdaptivePreferenceIconBackgroundAlpha(
                treatment = AppPreferenceIconTreatment.FILLED,
                tonalAlpha = 0.14f,
                iconStyle = AppIconStyle.MD3_STANDARD,
            ),
        )
        assertEquals(
            colorScheme.onSurfaceVariant,
            resolveAdaptivePreferenceIconContentColor(
                containerColor = Color.Transparent,
                colorScheme = colorScheme,
                iconStyle = AppIconStyle.MD3_STANDARD,
            ),
        )
        // 回归：MD3_STANDARD 容器色为 Transparent，glyph 必须落到
        // containerContentColor（onSurfaceVariant 单色），否则图标透明消失只剩文字。
        assertEquals(
            colorScheme.onSurfaceVariant,
            resolveAdaptivePreferenceIconGlyphColor(
                treatment = AppPreferenceIconTreatment.TONAL,
                iconStyle = AppIconStyle.MD3_STANDARD,
                containerContentColor = colorScheme.onSurfaceVariant,
                semanticIconColor = Color.Transparent,
            ),
        )
    }

    @Test
    fun `md3 native preference rows keep the selected global icon treatment`() {
        val source = listOf(
            java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
            java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
        ).first { it.exists() }.readText()
        val nativeItemBlock = source
            .substringAfter("private fun Md3NativeListItemContent(")
            .substringBefore("internal fun AdaptivePreferenceContent(")
        val nativeSwitchBlock = source
            .substringAfter("internal fun AdaptiveSwitchPreferenceContent(")
            .substringBefore("fun AdaptivePreferenceSearchBarRenderer(")

        assertTrue(nativeItemBlock.contains("iconContainerColor.copy(alpha = iconBackgroundAlpha)"))
        assertTrue(nativeItemBlock.contains("tint = iconContentColor"))
        assertTrue(nativeSwitchBlock.contains("effectiveIconTint.copy(alpha = iconBackgroundAlpha)"))
        assertTrue(nativeSwitchBlock.contains("tint = iconContentColor"))
        assertFalse(nativeSwitchBlock.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"))
    }

    @Test
    fun `material3 style without dynamic color should collapse legacy accent tints to primary`() {
        val colorScheme = darkColorScheme()

        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSBlue, AppUiStyle.MATERIAL3, colorScheme, useSemanticAccentRoles = false)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSPurple, AppUiStyle.MATERIAL3, colorScheme, useSemanticAccentRoles = false)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSGreen, AppUiStyle.MATERIAL3, colorScheme, useSemanticAccentRoles = false)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSRed, AppUiStyle.MATERIAL3, colorScheme, useSemanticAccentRoles = false)
        )
        assertEquals(
            colorScheme.primary,
            resolveAdaptiveSemanticIconTint(iOSSystemGray, AppUiStyle.MATERIAL3, colorScheme, useSemanticAccentRoles = false)
        )
    }

    @Test
    fun `miuix style should preserve colorful settings icon tints`() {
        val colorScheme = darkColorScheme()

        assertEquals(
            iOSPurple,
            resolveAdaptiveSemanticIconTint(
                iconTint = iOSPurple,
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
            ),
        )
        assertEquals(
            iOSRed,
            resolveAdaptiveSemanticIconTint(
                iconTint = iOSRed,
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
            ),
        )
    }

    @Test
    fun `material3 style should use material container colors for grouped settings and search`() {
        val colorScheme = lightColorScheme(
            surfaceContainer = Color(0xFFF0EBF4),
            surfaceContainerLow = Color(0xFFF4F0F8),
            surfaceContainerHigh = Color(0xFFECE6F0)
        )

        assertEquals(
            colorScheme.surfaceContainerLow,
            resolveAdaptiveGroupContainerColor(
                uiStyle = AppUiStyle.MATERIAL3,
                colorScheme = colorScheme,
            )
        )
        assertEquals(
            colorScheme.surfaceContainerHigh,
            resolveAdaptiveSearchBarContainerColor(
                uiStyle = AppUiStyle.MATERIAL3,
                colorScheme = colorScheme,
            )
        )
    }

    @Test
    fun `miuix style should use upstream search container tone`() {
        val colorScheme = lightColorScheme(
            surfaceContainer = Color(0xFFF0EBF4),
            surfaceContainerLow = Color(0xFFF4F0F8),
            surfaceContainerHigh = Color(0xFFECE6F0)
        )

        assertEquals(
            colorScheme.surfaceContainer,
            resolveAdaptiveGroupContainerColor(
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
            )
        )
        assertEquals(
            colorScheme.surfaceContainerHigh,
            resolveAdaptiveSearchBarContainerColor(
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
            )
        )
    }

    @Test
    fun `search container honors explicit override and otherwise uses semantic default`() {
        val fallback = Color(0xFF242424)
        val explicit = Color(0xFF334455)

        assertEquals(
            fallback,
            resolveAdaptiveSearchBarContainerOverride(Color.Unspecified, fallback),
        )
        assertEquals(
            explicit,
            resolveAdaptiveSearchBarContainerOverride(explicit, fallback),
        )
    }

    @Test
    fun `miuix style should route search to miuix field`() {
        assertTrue(
            shouldUseNativeMiuixSearchBar(
                uiStyle = AppUiStyle.MIUIX
            )
        )
        assertFalse(
            shouldUseNativeMiuixSearchBar(
                uiStyle = AppUiStyle.MATERIAL3
            )
        )
    }

    @Test
    fun `global wallpaper should make default grouped settings translucent`() {
        val colorScheme = lightColorScheme(
            surfaceContainer = Color(0xFFF0EBF4),
            surfaceContainerLow = Color(0xFFF4F0F8),
            surfaceContainerHigh = Color(0xFFECE6F0)
        )

        assertEquals(
            colorScheme.surfaceContainer.copy(alpha = 0.62f),
            resolveAdaptiveGroupContainerColor(
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
                globalWallpaperVisible = true
            )
        )
        assertEquals(
            colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
            resolveAdaptiveGroupContainerColor(
                uiStyle = AppUiStyle.MATERIAL3,
                colorScheme = colorScheme,
                globalWallpaperVisible = true
            )
        )
        assertEquals(
            colorScheme.surfaceContainerHigh.copy(alpha = 0.48f),
            resolveAdaptiveSearchBarContainerColor(
                uiStyle = AppUiStyle.MIUIX,
                colorScheme = colorScheme,
                globalWallpaperVisible = true
            )
        )
    }

    @Test
    fun `miuix search bar implementation uses official input field`() {
        val source = java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
            .takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
        val text = source.readText()
        val miuixSearchBarStart = text.indexOf("private fun MiuixAdaptiveSearchBar")
        assertTrue(miuixSearchBarStart >= 0)
        val miuixSearchBarEnd = text.indexOf("\n}", miuixSearchBarStart).let { if (it < 0) text.length else it + 2 }
        val miuixSearchBarBlock = text.substring(miuixSearchBarStart, miuixSearchBarEnd)
        assertTrue(miuixSearchBarBlock.contains("InputField("))
        assertTrue(miuixSearchBarBlock.contains("color = containerColor"))
        assertFalse(miuixSearchBarBlock.contains("BasicTextField("))
    }

    @Test
    fun `force expanded search bar uses outlined text field on material3`() {
        val source = listOf(
            java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
            java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
        ).first { it.exists() }.readText()
        val forceExpandedStart = source.indexOf("if (forceExpandedInput) {")
        val outlinedFieldStart = source.indexOf("OutlinedTextField(", forceExpandedStart)
        assertTrue(forceExpandedStart >= 0)
        assertTrue(outlinedFieldStart > forceExpandedStart)
        assertTrue(source.substring(forceExpandedStart, outlinedFieldStart).contains("focusRequester"))
    }

    @Test
    fun `settings search screen pins input in scaffold header`() {
        val source = listOf(
            java.io.File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt"),
            java.io.File("../app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt"),
            java.io.File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt"),
        ).first { it.exists() }.readText()
        assertTrue(source.contains("scrollHost = SettingsPageScrollHost.External"))
        assertTrue(source.contains("header = {"))
        assertTrue(source.contains("SettingsSearchBarSection("))
        val headerIndex = source.indexOf("header = {")
        val scrollIndex = source.indexOf(".verticalScroll(", headerIndex)
        assertTrue(headerIndex >= 0)
        assertTrue(scrollIndex > headerIndex)
    }

    @Test
    fun `settings search bar delegates style rendering to neutral field`() {
        val source = listOf(
            java.io.File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchUi.kt"),
            java.io.File("../app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchUi.kt"),
            java.io.File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchUi.kt"),
        ).first { it.exists() }.readText()
        assertTrue(source.contains("fun SettingsSearchBarSection"))
        assertTrue(source.contains("AppSearchField("))
        assertFalse(source.contains("BasicTextField("))
        assertFalse(source.contains("AdaptiveSearchFieldRenderer("))
    }

    @Test
    fun `settings search bar uses expanded miuix input field`() {
        val neutralApiSource = listOf(
            java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AppPreferenceComponents.kt"),
            java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AppPreferenceComponents.kt"),
        ).first { it.exists() }.readText()
        val rendererSource = listOf(
            java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
            java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"),
        ).first { it.exists() }.readText()
        assertTrue(neutralApiSource.contains("fun AppSearchField("))
        assertTrue(
            neutralApiSource.contains(
                "forceExpandedInput = presentation == AppSearchFieldPresentation.TOP_BAR"
            )
        )
        assertTrue(rendererSource.contains("InputField("))
        assertTrue(rendererSource.contains("expanded = true"))
        assertTrue(rendererSource.contains("OutlinedTextField("))
        assertTrue(rendererSource.contains("BasicTextField("))
    }

    @Test
    fun `miuix generic search bar does not auto expand before user interaction`() {
        val source = java.io.File("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
            .takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
        val text = source.readText()
        val miuixSearchBarStart = text.indexOf("private fun MiuixAdaptiveSearchBar")
        assertTrue(miuixSearchBarStart >= 0)
        val collapsedPathStart = text.indexOf("var expanded by rememberSaveable(query.isNotBlank())", miuixSearchBarStart)
        assertTrue(collapsedPathStart >= 0)
        val collapsedPathEnd = text.indexOf("InputField(", collapsedPathStart)
        assertTrue(collapsedPathEnd > collapsedPathStart)
        val collapsedPathBlock = text.substring(collapsedPathStart, collapsedPathEnd)

        assertTrue(collapsedPathBlock.contains("var expanded by rememberSaveable(query.isNotBlank())"))
        assertTrue(text.substring(collapsedPathStart).contains("expanded = expanded || query.isNotBlank()"))
        assertTrue(text.contains("forceExpandedInput"))
    }
}
