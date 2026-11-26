package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppPreferenceGroupPresentation
import com.android.purebilibili.core.ui.components.LocalAppPreferenceGroupPresentation
import com.android.purebilibili.core.ui.components.adaptivePreferenceGroupContainerColor
import com.android.purebilibili.core.ui.components.rememberAdaptiveListVisualCapabilities
import com.android.purebilibili.core.ui.transition.isVisibleReturnLanding
import com.android.purebilibili.core.ui.transition.navigationReturnLanding
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.settingsSharedContainerBounds

/** 仅登记实际点击的设置行，开关、编辑器和弹窗保持原有操作。 */
@Composable
internal fun SettingsNavigationContainerOrigin(
    target: BiliPaiNavKey?,
    onClick: () -> Unit,
    first: Boolean = true,
    last: Boolean = true,
    flatGroup: Boolean = false,
    content: @Composable (Modifier, () -> Unit) -> Unit,
) {
    val sources = LocalSettingsContainerSources.current
    val owner = LocalSettingsContainerOwner.current
    if (sources == null || owner == null || target == null || target == owner) {
        content(Modifier, onClick)
        return
    }
    val isFlatGroup = flatGroup ||
        LocalAppPreferenceGroupPresentation.current == AppPreferenceGroupPresentation.FLAT
    val color = if (isFlatGroup) {
        AppSurfaceTokens.groupedListContainer()
    } else {
        adaptivePreferenceGroupContainerColor()
    }
    val origin = remember(target, sources, owner) {
        sources.sources[target]?.takeIf { it.owner == owner && it.coordinates?.isAttached != true }
            ?: SettingsSharedContainerOrigin(owner, color)
    }
    val ownedCoordinates = remember(origin) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val radius = if (isFlatGroup) 0.dp else
        rememberAdaptiveListVisualCapabilities().componentSpec.groupCornerRadiusDp.dp
    SideEffect {
        origin.color = color
        origin.topRadius = if (first) radius else 0.dp
        origin.bottomRadius = if (last) radius else 0.dp
    }
    DisposableEffect(origin) {
        onDispose {
            // 列表项重组复用同一 origin 时，只释放仍属于当前实例的坐标，
            // 避免旧节点晚一步 dispose 把新节点的 coordinates 与 available 误清导致返回退化为淡出。
            if (origin.coordinates === ownedCoordinates[0]) {
                origin.coordinates = null
                origin.available = false
                origin.returnLanding.clear()
            }
            ownedCoordinates[0] = null
        }
    }
    content(
        Modifier
            .onGloballyPositioned {
                ownedCoordinates[0] = it
                origin.coordinates = it
                if (origin.sharedBounds != null) {
                    origin.available = sources.isAvailable(it)
                }
            }
            .navigationReturnLanding(origin.returnLanding, sharedBoundsReleased = origin.sharedBounds == null) {
                origin.coordinates.isVisibleReturnLanding()
            }
            .then(settingsSharedContainerBounds(origin.sharedBounds)),
    ) {
        sources.capture(target, origin)
        onClick()
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
        SettingsSearchTarget.CUSTOM_PUSH -> BiliPaiNavKey.CustomPushSettings
        SettingsSearchTarget.PLUGINS -> BiliPaiNavKey.PluginsSettings()
        SettingsSearchTarget.SETTINGS_SHARE -> BiliPaiNavKey.SettingsShare
        SettingsSearchTarget.WEBDAV_BACKUP -> BiliPaiNavKey.WebDavBackup
        SettingsSearchTarget.OPEN_SOURCE_LICENSES -> BiliPaiNavKey.OpenSourceLicenses
        SettingsSearchTarget.TIPS -> BiliPaiNavKey.TipsSettings
        SettingsSearchTarget.KEYBOARD_SHORTCUTS -> BiliPaiNavKey.KeyboardShortcutsSettings
        SettingsSearchTarget.GAMEPAD_CONTROL -> BiliPaiNavKey.GamepadControlSettings
        else -> null
    }
