package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import kotlinx.coroutines.launch

internal data class TabletDanmakuChromeState(
    val enabled: Boolean,
    val onToggle: () -> Unit,
)

/**
 * 平板/大屏的弹幕开关。
 *
 * [danmakuManager] 必须是详情页共享的那一份（由 `VideoDetailScreenStateHolder` 以
 * `currentBvid` 建立）。此前这里以路由参数 `bvid` 自行 acquire，会在同页切集后与共享会话
 * 分叉：开关实际改的是另一个 manager，并额外产生一次 acquire/release 与渲染器重建。
 */
@Composable
internal fun rememberTabletDanmakuChromeState(
    danmakuManager: DanmakuManager,
): TabletDanmakuChromeState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val danmakuSettings by SettingsManager
        .getDanmakuSettings(context, DanmakuSettingsScope.LANDSCAPE)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val latestEnabled = rememberUpdatedState(danmakuSettings.enabled)
    val onToggle = remember(danmakuManager, context, scope) {
        {
            val newValue = !latestEnabled.value
            danmakuManager.isEnabled = newValue
            if (!newValue) {
                danmakuManager.clear()
            }
            scope.launch {
                SettingsManager.setDanmakuEnabled(
                    context,
                    newValue,
                    DanmakuSettingsScope.LANDSCAPE,
                )
            }
            Unit
        }
    }
    return TabletDanmakuChromeState(
        enabled = danmakuSettings.enabled,
        onToggle = onToggle,
    )
}
