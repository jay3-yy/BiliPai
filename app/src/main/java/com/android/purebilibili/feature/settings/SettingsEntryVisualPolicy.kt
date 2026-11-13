package com.android.purebilibili.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.AppContentColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.theme.iOSRed
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.core.ui.AppIcons
import com.android.purebilibili.core.ui.AppSemanticAccentRole
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.AppSemanticVisualPolicy
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy

internal data class SettingsEntryVisual(
    val icon: ImageVector? = null,
    @DrawableRes val iconResId: Int? = null,
    val iconTint: Color,
    val iconSizeDp: Int = 20,
)

/**
 * 设置入口图标色：Miuix 沿用按入口分配的七彩色，Material 3 统一使用中性内容色。
 */
@Composable
internal fun rememberSettingsEntryTint(
    role: AppSemanticAccentRole,
    iosTint: Color,
): Color {
    val uiStyle = LocalAppUiStyle.current
    val neutralColor = AppContentColors.secondaryText()
    return remember(role, iosTint, uiStyle, neutralColor) {
        if (uiStyle == AppUiStyle.MIUIX) iosTint else neutralColor
    }
}

private fun resolveIosSettingsEntryTint(
    target: SettingsSearchTarget
): Color = when (target) {
    SettingsSearchTarget.INTERFACE_THEME -> iOSPink
    SettingsSearchTarget.HOME_FEED -> iOSOrange
    SettingsSearchTarget.NAVIGATION -> iOSBlue
    SettingsSearchTarget.PLAYBACK_QUALITY -> iOSGreen
    SettingsSearchTarget.FULLSCREEN_GESTURE -> iOSPurple
    SettingsSearchTarget.INTERACTION_COMMENT -> iOSTeal
    SettingsSearchTarget.DATA_BACKUP -> iOSBlue
    SettingsSearchTarget.PRIVACY_PERMISSION -> iOSPurple
    SettingsSearchTarget.DIAGNOSTICS -> iOSTeal
    SettingsSearchTarget.ABOUT_SUPPORT -> iOSOrange
    SettingsSearchTarget.APPEARANCE -> iOSPink
    SettingsSearchTarget.ANIMATION -> iOSPink
    SettingsSearchTarget.PLAYBACK -> iOSGreen
    SettingsSearchTarget.BOTTOM_BAR -> iOSBlue
    SettingsSearchTarget.PERMISSION -> iOSTeal
    SettingsSearchTarget.SPACE_PRIVACY -> iOSGreen
    SettingsSearchTarget.LOGIN_ACTIVITY -> iOSGreen
    SettingsSearchTarget.MESSAGE_NOTIFICATION -> iOSBlue
    SettingsSearchTarget.BLOCKED_LIST -> iOSBlue
    SettingsSearchTarget.SETTINGS_SHARE -> iOSGreen
    SettingsSearchTarget.WEBDAV_BACKUP -> iOSBlue
    SettingsSearchTarget.DOWNLOAD_PATH -> iOSBlue
    SettingsSearchTarget.IMAGE_SAVE_PATH -> iOSTeal
    SettingsSearchTarget.CLEAR_CACHE -> iOSBlue
    SettingsSearchTarget.PLUGINS -> iOSPurple
    SettingsSearchTarget.NEARBY_HANDOFF -> iOSTeal
    SettingsSearchTarget.EXPORT_LOGS -> iOSTeal
    SettingsSearchTarget.OPEN_SOURCE_LICENSES -> iOSOrange
    SettingsSearchTarget.OPEN_SOURCE_HOME -> iOSPurple
    SettingsSearchTarget.CHECK_UPDATE -> iOSBlue
    SettingsSearchTarget.VIEW_RELEASE_NOTES -> iOSTeal
    SettingsSearchTarget.REPLAY_ONBOARDING -> iOSPink
    SettingsSearchTarget.TIPS -> iOSOrange
    SettingsSearchTarget.KEYBOARD_SHORTCUTS -> iOSBlue
    SettingsSearchTarget.OPEN_LINKS -> iOSTeal
    SettingsSearchTarget.DONATE -> iOSRed
    SettingsSearchTarget.TELEGRAM -> iOSBlue
    SettingsSearchTarget.TWITTER -> iOSBlue
    SettingsSearchTarget.DISCLAIMER -> iOSBlue
}

@Composable
internal fun rememberSettingsEntryVisual(
    target: SettingsSearchTarget,
): SettingsEntryVisual {
    val policy = rememberAppSemanticVisualPolicy()
    val neutralColor = AppContentColors.secondaryText()
    val colorful = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val resolved = remember(target, policy, neutralColor, colorful) {
        resolveSettingsEntryVisual(target, policy, neutralColor, colorful)
    }
    // Material 3's native ListItem renders ImageVector leading content more
    // consistently than a VectorDrawable painter (whose XML tint can be
    // overridden by the platform theme). Keep the resource for legacy skins,
    // but also expose the vector form so MD3 rows never lose their glyph.
    return if (resolved.icon == null && resolved.iconResId != null) {
        resolved.copy(icon = rememberMaterialSymbol(resolved.iconResId))
    } else {
        resolved
    }
}

/**
 * @param neutralColor 非七彩主题（Material 3）使用的中性图标色。
 * @param colorful Miuix 传 true 取按入口分配的彩色，其余主题传 false 使用中性色。
 */
internal fun resolveSettingsEntryVisual(
    target: SettingsSearchTarget,
    policy: AppSemanticVisualPolicy = AppSemanticVisualPolicy(
        iconFamily = AppSemanticIconFamily.MATERIAL,
        accentPalette = null,
        prefersNativeChrome = false,
        supportsIndependentLiquidGlass = true,
    ),
    neutralColor: Color = Color.Unspecified,
    colorful: Boolean = false,
): SettingsEntryVisual {
    val iconTint = if (colorful) resolveIosSettingsEntryTint(target) else neutralColor
    return when (target) {
        SettingsSearchTarget.TELEGRAM -> SettingsEntryVisual(
            iconResId = R.drawable.ic_telegram_mono,
            iconTint = iconTint
        )
        SettingsSearchTarget.TWITTER -> SettingsEntryVisual(
            icon = AppIcons.Twitter,
            iconTint = iconTint
        )
        else -> {
            val role = resolveSettingsSearchTargetIconRole(target)
            SettingsEntryVisual(
                iconResId = resolveSettingsMaterialSymbolResource(role),
                iconTint = iconTint,
                iconSizeDp = resolveSettingsSemanticIconSizeDp(role, policy.effectiveIconFamily),
            )
        }
    }
}
