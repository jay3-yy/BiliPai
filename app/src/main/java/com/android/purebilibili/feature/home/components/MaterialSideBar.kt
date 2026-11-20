package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AdaptivePlainTooltipBox
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback

@Composable
internal fun MaterialSideBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onHomeDoubleTap: () -> Unit,
    onDynamicDoubleTap: () -> Unit,
    onToggleSidebar: (() -> Unit)?,
    onAccountSwitchClick: (() -> Unit)?,
    firstItemModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    var lastHomeClickMs by remember { mutableLongStateOf(0L) }
    var lastDynamicClickMs by remember { mutableLongStateOf(0L) }
    val onItemSelected: (BottomNavItem) -> Unit = { item ->
        val now = android.os.SystemClock.elapsedRealtime()
        when {
            resolveDynamicSideBarClickAction(item, now, lastDynamicClickMs) -> {
                lastDynamicClickMs = 0L
                haptic(HapticType.MEDIUM)
                onDynamicDoubleTap()
            }
            resolveHomeSideBarClickAction(item, now, lastHomeClickMs) == HomeSideBarClickAction.HOME_DOUBLE_TAP -> {
                lastHomeClickMs = 0L
                haptic(HapticType.MEDIUM)
                onHomeDoubleTap()
            }
            else -> {
                if (item == BottomNavItem.HOME) lastHomeClickMs = now
                if (item == BottomNavItem.DYNAMIC) lastDynamicClickMs = now
                performHomeSideBarItemTap(haptic = haptic, onClick = { onItemClick(item) })
            }
        }
    }
    val header: @Composable () -> Unit = {
        Column(
            modifier = Modifier.wrapContentWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AdaptivePlainTooltipBox(text = if (expanded) "收起导航" else "展开导航") {
                AppIconButton(onClick = { onExpandedChange(!expanded) }) {
                    AppIcon(Icons.Outlined.ViewSidebar, contentDescription = if (expanded) "收起导航" else "展开导航")
                }
            }
            if (onAccountSwitchClick != null) {
                AdaptivePlainTooltipBox(text = "切换账号") {
                    AppIconButton(onClick = onAccountSwitchClick) {
                        AppIcon(Icons.Outlined.SwapHoriz, contentDescription = "切换账号")
                    }
                }
            }
        }
    }
    // 两种宽度均使用常规 MD3 导航组件，保留原生选中语义和键盘焦点。
    if (expanded) {
        PermanentDrawerSheet(
            modifier = modifier.width(240.dp).fillMaxHeight(),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                header()
                visibleItems.forEachIndexed { index, item ->
                    val selected = currentItem == item
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = { onItemSelected(item) },
                        modifier = (if (index == 0) firstItemModifier else Modifier)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        icon = { AppIcon(resolveMaterialBottomBarIcon(item, selected), contentDescription = null) },
                        label = { AppText(resolveBottomNavItemLabel(item, itemLabels)) },
                    )
                }
            }
        }
    } else {
        NavigationRail(
            modifier = modifier.verticalScroll(rememberScrollState()),
            header = { header() },
        ) {
            visibleItems.forEachIndexed { index, item ->
                val selected = currentItem == item
                NavigationRailItem(
                    selected = selected,
                    onClick = { onItemSelected(item) },
                    modifier = if (index == 0) firstItemModifier else Modifier,
                    icon = { AppIcon(resolveMaterialBottomBarIcon(item, selected), contentDescription = null) },
                    label = { AppText(resolveBottomNavItemLabel(item, itemLabels)) },
                )
            }
        }
    }
}
