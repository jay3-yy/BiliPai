package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.adaptive.APP_SHORTCUT_DEFINITIONS
import com.android.purebilibili.core.ui.adaptive.AppShortcutAction
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold

private data class PlayerShortcutRow(
    @StringRes val titleResId: Int,
    @StringRes val keysResId: Int,
)

private val playerShortcutRows = listOf(
    PlayerShortcutRow(R.string.keyboard_shortcut_player_play_pause, R.string.keyboard_shortcut_player_play_pause_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_seek_short, R.string.keyboard_shortcut_player_seek_short_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_seek_long, R.string.keyboard_shortcut_player_seek_long_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_volume, R.string.keyboard_shortcut_player_volume_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_fullscreen, R.string.keyboard_shortcut_player_fullscreen_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_mute, R.string.keyboard_shortcut_player_mute_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_danmaku, R.string.keyboard_shortcut_player_danmaku_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_like_coin_fav, R.string.keyboard_shortcut_player_like_coin_fav_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_screenshot, R.string.keyboard_shortcut_player_screenshot_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_lock, R.string.keyboard_shortcut_player_lock_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_parts, R.string.keyboard_shortcut_player_parts_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_percent, R.string.keyboard_shortcut_player_percent_keys),
    PlayerShortcutRow(R.string.keyboard_shortcut_player_speed, R.string.keyboard_shortcut_player_speed_keys),
)

private fun resolveAppShortcutTitleRes(action: AppShortcutAction): Int = when (action) {
    AppShortcutAction.OpenSearch -> R.string.keyboard_shortcut_open_search
    AppShortcutAction.OpenSettings -> R.string.keyboard_shortcut_open_settings
    is AppShortcutAction.SelectBottomNavItem -> R.string.keyboard_shortcut_select_bottom_nav
}

@Composable
fun KeyboardShortcutsSettingsScreen(
    onBack: () -> Unit
) {
    val screenTitle = stringResource(R.string.keyboard_shortcuts_title)
    val backLabel = stringResource(R.string.common_back)
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp
    )
    // 与 AppShortcutPolicy、系统快捷键助手共用同一数据源，保证展示与解析一致。
    val appShortcutRows = remember {
        APP_SHORTCUT_DEFINITIONS.map { definition ->
            resolveAppShortcutTitleRes(definition.action) to definition.keysLabel
        }
    }

    EntranceGroup {
        SettingsPageScaffold(
            title = screenTitle,
            onBack = onBack,
            backContentDescription = backLabel,
            bottomContentPadding = contentBottomPadding,
            lazyListContent = {
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppPreferenceSectionTitle(stringResource(R.string.keyboard_shortcuts_section_app))
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppShortcutSection(
                            rows = appShortcutRows.map { (titleRes, keys) ->
                                stringResource(titleRes) to keys
                            }
                        )
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppPreferenceSectionTitle(stringResource(R.string.keyboard_shortcuts_section_player))
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        Column {
                            AppText(
                                text = stringResource(R.string.keyboard_shortcuts_player_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            PlayerShortcutSection()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun AppShortcutSection(rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(AppSurfaceTokens.cardContainer())
    ) {
        rows.forEachIndexed { index, (title, keys) ->
            ShortcutRow(title = title, keysLabel = keys)
            if (index != rows.lastIndex) {
                AppHorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun PlayerShortcutSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(AppSurfaceTokens.cardContainer())
    ) {
        playerShortcutRows.forEachIndexed { index, row ->
            ShortcutRow(
                title = stringResource(row.titleResId),
                keysLabel = stringResource(row.keysResId),
            )
            if (index != playerShortcutRows.lastIndex) {
                AppHorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(title: String, keysLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppContentColors.primaryText(),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
        AppText(
            text = keysLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
        )
    }
}
