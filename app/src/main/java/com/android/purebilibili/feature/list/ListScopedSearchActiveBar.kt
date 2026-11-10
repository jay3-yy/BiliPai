package com.android.purebilibili.feature.list

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale

/**
 * 「列表精简搜索」激活后顶部轻量结果条。
 * 液态玻璃时共用悬浮 Dock；非玻璃时走 [AppSurfaceTokens] 搜索容器语义，
 * 随 MIUIX / MD3 主题各自适配。
 */
@Composable
internal fun ListScopedSearchActiveBar(
    searchQuery: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
) {
    BottomBarMatchedReusableLiquidDock(
        shape = AppShapes.container(ContainerLevel.Pill),
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        reuseEnabled = true,
        elasticFeedbackEnabled = true,
        elasticFeedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
        useNeutralLiquidContainer = true,
        shellLensIntensity = resolveFloatingDockGeometryScale(44f),
        // 内容只含文字与图标，不需要再录制玻璃外壳。
        captureShellForContent = false,
    ) { liquidChromeActive ->
        AppSurface(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.container(ContainerLevel.Pill),
            color = if (liquidChromeActive) {
                Color.Transparent
            } else {
                AppSurfaceTokens.searchContainer()
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppSpacingTokens.Medium,
                        vertical = AppSpacingTokens.Small
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = resolveListScopedSearchActiveBarLabel(searchQuery),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    color = if (liquidChromeActive) {
                        Color.Unspecified
                    } else {
                        AppSurfaceTokens.searchContent()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppIcon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清除搜索",
                )
            }
        }
    }
}
