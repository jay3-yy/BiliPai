package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.LocalWindowSizeClass
import kotlinx.coroutines.launch

internal val HomeSettings.effectiveGridColumnCountCompact: Int
    get() = if (gridColumnCustomizationEnabled) gridColumnCountCompact else 0

internal enum class FeedGridColumnScope { COMPACT, WIDE_PORTRAIT, WIDE_LANDSCAPE }

internal fun resolveFeedGridColumnScope(
    widthSizeClass: WindowWidthSizeClass,
    landscape: Boolean,
): FeedGridColumnScope = when {
    isCompactHomeFeedScreen(widthSizeClass) -> FeedGridColumnScope.COMPACT
    landscape -> FeedGridColumnScope.WIDE_LANDSCAPE
    else -> FeedGridColumnScope.WIDE_PORTRAIT
}

/** 使用当前窗口比例，分屏与折叠后跟随实际可用窗口，不按设备物理朝向分档。 */
internal fun HomeSettings.effectiveGridColumnCountForWindow(window: WindowSizeClass): Int =
    if (!gridColumnCustomizationEnabled) 0 else when (resolveFeedGridColumnScope(
        window.widthSizeClass, window.widthDp > window.heightDp,
    )) {
        FeedGridColumnScope.COMPACT -> gridColumnCountCompact
        FeedGridColumnScope.WIDE_PORTRAIT -> gridColumnCountWidePortrait
        FeedGridColumnScope.WIDE_LANDSCAPE -> gridColumnCount
    }

internal val HomeSettings.gridColumnPinchEnabled: Boolean
    get() = gridColumnCustomizationEnabled && pinchToChangeGridColumnsEnabled

internal class FeedGridColumnPreference(
    val columns: Int,
    val onChange: (Int) -> Unit,
    val onGestureEnd: (Int) -> Unit,
)

internal fun resolveFeedGridColumnPreference(
    customizationEnabled: Boolean,
    defaultColumns: Int,
    pageColumns: Int?,
    bounds: IntRange,
    minimumColumns: Int = 1,
): Int {
    val requestedColumns = if (customizationEnabled) pageColumns ?: defaultColumns else defaultColumns
    // 默认列数也受当前窗口上限约束；临时降列不修改保存值，放大后恢复。
    val effectiveMinimum = if (customizationEnabled) minimumColumns.coerceIn(1, bounds.last) else 1
    return requestedColumns.coerceIn(effectiveMinimum, bounds.last)
}

/** 网格行为与材质无关；窄屏、宽屏竖屏和宽屏横屏分别记忆。 */
@Composable
internal fun rememberFeedGridColumnPreference(
    settings: HomeSettings,
    widthSizeClass: WindowWidthSizeClass,
    defaultColumns: Int,
    bounds: IntRange,
    customizationApplies: Boolean = true,
    minimumColumns: Int = 1,
): FeedGridColumnPreference {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val window = LocalWindowSizeClass.current
    val columnScope = resolveFeedGridColumnScope(widthSizeClass, window.widthDp > window.heightDp)
    var pageColumns by rememberSaveable(
        settings.gridColumnCountCompact,
        settings.gridColumnCount,
        settings.gridColumnCountWidePortrait,
        settings.gridColumnsSharedAcrossPages,
        settings.gridColumnCustomizationEnabled,
    ) { mutableStateOf(mapOf<FeedGridColumnScope, Int>()) }
    val customizationEnabled = settings.gridColumnCustomizationEnabled && customizationApplies
    val columns = resolveFeedGridColumnPreference(
        customizationEnabled = customizationEnabled,
        defaultColumns = defaultColumns,
        pageColumns = pageColumns[columnScope],
        bounds = bounds,
        minimumColumns = minimumColumns,
    )
    return FeedGridColumnPreference(
        columns = columns,
        onChange = { pageColumns = pageColumns + (columnScope to it.coerceIn(bounds)) },
        onGestureEnd = { finalColumns ->
            val count = finalColumns.coerceIn(bounds)
            pageColumns = pageColumns + (columnScope to count)
            if (customizationEnabled && settings.gridColumnsSharedAcrossPages) {
                scope.launch {
                    when (columnScope) {
                        FeedGridColumnScope.COMPACT -> SettingsManager.setGridColumnCountCompact(context, count)
                        FeedGridColumnScope.WIDE_PORTRAIT -> SettingsManager.setGridColumnCountWidePortrait(context, count)
                        FeedGridColumnScope.WIDE_LANDSCAPE -> SettingsManager.setGridColumnCount(context, count)
                    }
                }
            }
        },
    )
}
