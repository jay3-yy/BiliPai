package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.rememberAdaptiveListVisualCapabilities
import com.android.purebilibili.core.ui.components.AppPreferenceGroupPresentation
import com.android.purebilibili.core.ui.components.LocalAppPreferenceGroupPresentation
import com.android.purebilibili.core.ui.components.adaptivePreferenceGroupContainerColor
import com.android.purebilibili.navigation3.BiliPaiNavKey

/** 只捕获可导航的设置行，开关、编辑器和弹窗不经过该入口。 */
@Composable
internal fun SettingsNavigationContainerOrigin(
    target: BiliPaiNavKey?,
    onClick: () -> Unit,
    first: Boolean = true,
    last: Boolean = true,
    flatGroup: Boolean = false,
    content: @Composable (() -> Unit) -> Unit,
) {
    val sources = LocalSettingsContainerSources.current
    val owner = LocalSettingsContainerOwner.current
    if (sources == null || owner == null || target == null || target == owner) {
        content(onClick)
        return
    }
    val anchor = remember(target, sources, owner) {
        sources.sources[target]?.takeIf { it.owner == owner }?.anchor ?: SettingsContainerAnchor()
    }
    val layer = rememberGraphicsLayer()
    val isFlatGroup = flatGroup ||
        LocalAppPreferenceGroupPresentation.current == AppPreferenceGroupPresentation.FLAT
    val color = if (isFlatGroup) {
        AppSurfaceTokens.groupedListContainer()
    } else {
        adaptivePreferenceGroupContainerColor()
    }
    val density = LocalDensity.current
    val groupRadius = rememberAdaptiveListVisualCapabilities().componentSpec.groupCornerRadiusDp
    val radius = if (isFlatGroup) 0f else with(density) { groupRadius.dp.toPx() }
    SideEffect {
        sources.sources[target]?.takeIf { it.anchor === anchor }?.let { source ->
            source.layer = layer
            source.color = color
        }
    }
    DisposableEffect(anchor) {
        onDispose { anchor.detach() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned(anchor::update)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                anchor.recorded = true
                if (sources.sources[target]?.takeIf { it.anchor === anchor }?.returnInFlight?.invoke() != true) {
                    drawLayer(layer)
                }
            },
    ) {
        content {
            sources.capture(
                target, owner, anchor, layer, color,
                topRadius = if (first) radius else 0f,
                bottomRadius = if (last) radius else 0f,
            )
            onClick()
        }
    }
}

/** 页面入口使用明确的目的地；外链、文件选择、清理等原地操作保持原行为。 */
internal fun resolveSettingsContainerTarget(target: SettingsSearchTarget): BiliPaiNavKey? =
    when (resolveSettingsSceneDetailFocus(target)?.target ?: target) {
        SettingsSearchTarget.APPEARANCE -> BiliPaiNavKey.AppearanceSettings
        SettingsSearchTarget.HOME_FEED -> BiliPaiNavKey.HomeSettings
        SettingsSearchTarget.ANIMATION -> BiliPaiNavKey.AnimationSettings
        SettingsSearchTarget.PLAYBACK -> BiliPaiNavKey.PlaybackSettings
        SettingsSearchTarget.BOTTOM_BAR -> BiliPaiNavKey.BottomBarSettings
        SettingsSearchTarget.PERMISSION -> BiliPaiNavKey.PermissionSettings
        SettingsSearchTarget.SPACE_PRIVACY -> BiliPaiNavKey.SpacePrivacySettings
        SettingsSearchTarget.LOGIN_ACTIVITY -> BiliPaiNavKey.LoginActivitySettings
        SettingsSearchTarget.MESSAGE_NOTIFICATION -> BiliPaiNavKey.MessageNotificationSettings
        SettingsSearchTarget.PLUGINS -> BiliPaiNavKey.PluginsSettings()
        SettingsSearchTarget.SETTINGS_SHARE -> BiliPaiNavKey.SettingsShare
        SettingsSearchTarget.WEBDAV_BACKUP -> BiliPaiNavKey.WebDavBackup
        SettingsSearchTarget.OPEN_SOURCE_LICENSES -> BiliPaiNavKey.OpenSourceLicenses
        SettingsSearchTarget.TIPS -> BiliPaiNavKey.TipsSettings
        SettingsSearchTarget.KEYBOARD_SHORTCUTS -> BiliPaiNavKey.KeyboardShortcutsSettings
        else -> null
    }
