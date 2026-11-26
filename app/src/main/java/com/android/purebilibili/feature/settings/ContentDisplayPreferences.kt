package com.android.purebilibili.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.ContentDisplaySettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import kotlinx.coroutines.launch

@Composable
internal fun ContentDisplayPreferences() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusViewingHideStats by remember(context) {
        SettingsManager.getFocusViewingHideStatsEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    SettingsCardGroup {
        SettingsItemAnchor("content.focus_viewing_hide_stats") {
            AppSwitchPreference(
                title = "专注观赏（隐藏互动数字）",
                subtitle = "隐藏卡片与视频详情中的播放量、弹幕数和点赞投币计数，专注作品本身",
                icon = Icons.Outlined.VisibilityOff,
                checked = focusViewingHideStats,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setFocusViewingHideStatsEnabled(context, enabled) }
                },
            )
        }
        AppPreferenceDivider()
        listOf(
            Triple(ContentDisplaySettings.SHOW_MEMBER_SHOP, SettingsIconRole.MEMBER_SHOP, "开启后显示 UP 主提供的小店入口"),
            Triple(ContentDisplaySettings.DYNAMIC_WARNING, SettingsIconRole.DYNAMIC_CONTENT_NOTICE, "显示动态中的风险与争议提示"),
            Triple(ContentDisplaySettings.BLOCK_GOODS_DYNAMIC, SettingsIconRole.BLOCK_GOODS_DYNAMIC, "过滤商品卡片动态及其转发，刷新列表后生效"),
            Triple(ContentDisplaySettings.BLOCK_GOODS_REPLY, SettingsIconRole.BLOCK_GOODS_REPLY, "过滤包含商品信息的评论及回复，重新加载评论后生效"),
        ).forEachIndexed { index, (id, role, description) ->
            val enabled by remember(context, id) { ContentDisplaySettings.observe(context, id) }
                .collectAsStateWithLifecycle(initialValue = ContentDisplaySettings.defaultEnabled(id))
            if (index > 0) AppPreferenceDivider()
            SettingsItemAnchor(id) {
                AppSwitchPreference(
                    title = ContentDisplaySettings.title(id),
                    subtitle = description,
                    icon = rememberSettingsSemanticIcon(role),
                    checked = enabled,
                    onCheckedChange = { scope.launch { ContentDisplaySettings.set(context, id, it) } },
                )
            }
        }
    }
}
