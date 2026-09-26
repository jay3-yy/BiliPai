package com.android.purebilibili.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle

/**
 * 顶 Tab 与首个内容的紧凑收紧量。
 *
 * 顶部 Dock 的槽位沿用底栏 Dock 的 64dp 壳高,而 MD3 非玻璃下的可见下划线行明显矮于
 * 该槽位,导致 Tab 下划线与内容之间出现额外的空隙。MD3 非玻璃时从预留 padding 中
 * 收紧这一差值;玻璃 Dock 与 MIUIX 的可见壳高与槽位一致,不收紧。
 */
internal fun resolveHomeTabsToContentTighteningDp(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean,
): Dp = if (uiStyle == AppUiStyle.MATERIAL3 && !liquidGlassEnabled) {
    12.dp
} else {
    0.dp
}
