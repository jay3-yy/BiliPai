package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppHapticSettings
import com.android.purebilibili.core.ui.components.AppGlassMenu
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import kotlinx.coroutines.launch

internal const val HAPTIC_SCENES_SETTING_ID = "animation.haptic_scenes"
internal const val HAPTIC_SCENES_TITLE = "振动场景"

/** 只有主开关打开时显示；细分偏好独立保存，关闭主开关不会清空选择。 */
@Composable
internal fun HapticScenePreference(enabled: Boolean) {
    if (!enabled) return
    val context = LocalContext.current
    val settings by remember(context) { SettingsManager.getHapticSettings(context) }
        .collectAsStateWithLifecycle(initialValue = LocalAppThemeConfig.current.hapticSettings)
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    LaunchedEffect(focus?.token) {
        if (AppHapticScene.entries.any { it.settingId == focus?.settingId }) expanded = true
    }
    val popupSource = rememberAppGlassMenuSource()
    val glassAvailable = com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
    val countLabel = "${AppHapticScene.entries.count(settings::isEnabled)}/${AppHapticScene.entries.size}"
    SettingsItemAnchor(HAPTIC_SCENES_SETTING_ID) {
        AppPreference(
            icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.HAPTIC_FEEDBACK),
            title = HAPTIC_SCENES_TITLE,
            subtitle = "分别控制不同操作的振动，关闭总开关后保留选择",
            value = countLabel.takeUnless { glassAvailable },
            showChevron = !glassAvailable,
            onClick = { expanded = true },
            trailingContent = if (glassAvailable) {
                {
                    Row(
                        modifier = Modifier.appGlassMenuSource(popupSource, onOpen = { expanded = true }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppText(countLabel, color = AppContentColors.secondaryText(),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        AppIcon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null,
                            tint = AppContentColors.secondaryText().copy(alpha = 0.72f), modifier = Modifier.size(14.dp))
                    }
                }
            } else null,
        )
    }
    val scrollState = rememberScrollState()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val dismiss = { expanded = false }
    val onSceneChanged: (AppHapticScene, Boolean) -> Unit = { scene, value ->
        scope.launch { SettingsManager.setHapticSceneEnabled(context, scene, value) }
    }
    // 玻璃交互面板由设置页同窗宿主接管；关闭玻璃或宿主不可用时保留原生 Dialog。
    // 列表的滚动状态显式交给宿主，内部开关不参与菜单滑选或点选后自动关闭。
    val glassHandled = AppGlassMenu(
        expanded = expanded,
        onDismissRequest = dismiss,
        title = HAPTIC_SCENES_TITLE,
        // 与公共玻璃菜单一致，由真实入口展开并在关闭时收回。
        placement = AppGlassMenuPlacement.ANCHOR_END,
        source = popupSource,
        minWidth = 280.dp,
        maxWidth = 420.dp,
        maxHeight = maxHeight,
        scrollState = scrollState,
        interactiveContent = true,
    ) {
        // 标题、列表和完成按钮共用滚动内容，避免固定页脚截断最后一行。
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // 只在仍可向下滚动时柔化视口边缘，不遮盖真正的列表末尾。
                    if (scrollState.canScrollForward && size.height > 0f) {
                        val fadeHeight = 24.dp.toPx().coerceAtMost(size.height)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeHeight,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .verticalScroll(scrollState),
        ) {
            AppText(
                HAPTIC_SCENES_TITLE,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            HapticSceneList(
                settings = settings,
                onSceneChanged = onSceneChanged,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AppTextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 8.dp)) { AppText("完成") }
        }
    }
    if (expanded && !glassHandled) {
        AppAlertDialog(
            onDismissRequest = dismiss,
            title = { AppText(HAPTIC_SCENES_TITLE) },
            text = {
                HapticSceneList(
                    settings = settings,
                    onSceneChanged = onSceneChanged,
                    modifier = Modifier.heightIn(max = maxHeight).verticalScroll(scrollState),
                )
            },
            confirmButton = { AppDialogAction(onClick = dismiss) { AppText("完成") } },
        )
    }
}

/** 原生对话框与玻璃面板仅分开外壳，开关状态、搜索锚点与保存行为共用。 */
@Composable
private fun HapticSceneList(
    settings: AppHapticSettings,
    onSceneChanged: (AppHapticScene, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AppHapticScene.entries.forEachIndexed { index, scene ->
            SettingsItemAnchor(scene.settingId) {
                AppSwitchPreference(
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.HAPTIC_SCENE_SWITCH),
                    title = scene.title,
                    subtitle = scene.description,
                    checked = settings.isEnabled(scene),
                    onCheckedChange = { onSceneChanged(scene, it) },
                )
            }
            if (index < AppHapticScene.entries.lastIndex) AppPreferenceDivider(startIndent = 0.dp)
        }
    }
}
