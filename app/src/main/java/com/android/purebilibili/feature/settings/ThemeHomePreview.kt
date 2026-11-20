package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppInteractionColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.resolveBottomNavItemLabel
import com.android.purebilibili.feature.home.components.resolveMaterialBottomBarIcon

/** 预览里示意的底栏项目；只用于体现配色，不触发真实导航。 */
private val THEME_PREVIEW_BOTTOM_ITEMS = listOf(
    BottomNavItem.HOME,
    BottomNavItem.DYNAMIC,
    BottomNavItem.HISTORY,
    BottomNavItem.PROFILE,
)

/** 预览里的示意文案：写成真实内容而不是占位色条，观感接近渲染完成的首页。 */
private val THEME_PREVIEW_TITLES = listOf("城市的另一面", "听见生活的节奏")
private const val THEME_PREVIEW_META = "UP主 · 12.3万播放"
private const val THEME_PREVIEW_SEARCH_HINT = "搜索感兴趣的视频"

/**
 * 首页配色预览。
 *
 * 只体现当前主题的角色分配：不请求数据、不包含液态玻璃修饰符或循环动态效果。
 * 默认是静态小样；展开模式显式增大示例尺寸，底栏点击仅更新调用方的预览选中态。
 * Miuix 非玻璃底栏复用实际导航的选中轮廓；外部圆角、水平边距与容器色由
 * `AppPreferenceGroup` 承载，MD3 与玻璃预览保留原有示意样式。
 *
 * 角色分配：搜索条取搜索容器色、头像位与卡片封面取主色容器、文字取文字层级色、
 * 底栏取卡片容器色与选中态强调色，因此三种颜色来源下都能直接看出效果。
 */
@Composable
internal fun ThemeHomePreview(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    selectedNavigationIndex: Int = 0,
    onNavigationSelected: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "首页配色预览" },
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
    ) {
        ThemePreviewTopBar(expanded = expanded)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)) {
            ThemePreviewCard(coverVariant = 0, expanded = expanded, modifier = Modifier.weight(1f))
            ThemePreviewCard(coverVariant = 1, expanded = expanded, modifier = Modifier.weight(1f))
        }
        ThemePreviewBottomBar(
            expanded = expanded,
            selectedIndex = selectedNavigationIndex,
            onSelected = onNavigationSelected,
        )
    }
}

/** 顶栏：搜索条 + 头像位（头像底取主色容器，避免纯色圆球显得突兀）。 */
@Composable
private fun ThemePreviewTopBar(modifier: Modifier = Modifier, expanded: Boolean = false) {
    val searchContent = AppSurfaceTokens.searchContent()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(if (expanded) 44.dp else 32.dp)
                .clip(AppShapes.container(ContainerLevel.Field))
                .background(AppSurfaceTokens.searchContainer())
                .padding(horizontal = AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
        ) {
            AppIcon(
                imageVector = rememberSettingsVector(R.drawable.ms_search_24),
                contentDescription = null,
                tint = searchContent,
                modifier = Modifier.size(16.dp),
            )
            AppText(
                text = THEME_PREVIEW_SEARCH_HINT,
                color = searchContent,
                fontSize = if (expanded) 15.sp else 12.sp,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(if (expanded) 44.dp else 32.dp)
                .clip(CircleShape)
                .background(AppSurfaceTokens.primaryContainer()),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                imageVector = rememberSettingsVector(R.drawable.ms_person_24),
                contentDescription = null,
                tint = AppSurfaceTokens.onPrimaryContainer(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 卡片：封面取主色容器并加一圈描边，标题与信息行用文字层级色。 */
@Composable
private fun ThemePreviewCard(coverVariant: Int, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val coverShape = AppShapes.container(ContainerLevel.Field)
    val highlight = AppSurfaceTokens.primary()
    val ridge = MaterialTheme.colorScheme.tertiary
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(coverShape)
                .background(AppSurfaceTokens.primaryContainer())
                .border(AppSurfaceTokens.OutlineWidth, AppSurfaceTokens.divider(), coverShape),
        ) {
            drawThemePreviewCoverArt(Rect(Offset.Zero, size), coverVariant, highlight, ridge)
        }
        AppText(
            text = THEME_PREVIEW_TITLES[coverVariant.mod(THEME_PREVIEW_TITLES.size)],
            color = AppContentColors.primaryText(),
            fontSize = if (expanded) 16.sp else 12.sp,
            maxLines = 1,
        )
        AppText(
            text = THEME_PREVIEW_META,
            color = AppContentColors.secondaryText(),
            fontSize = if (expanded) 12.sp else 10.sp,
            maxLines = 1,
        )
    }
}

/** 非玻璃 Miuix 预览与实际原生底栏共用选中语义，其余材质保留已有小样。 */
@Composable
private fun ThemePreviewBottomBar(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    selectedIndex: Int = 0,
    onSelected: ((Int) -> Unit)? = null,
) {
    val nativeMiuix = isMiuixNonGlassEnabled()
    val navigationColors = AppInteractionColors.nativeNavigation(
        com.android.purebilibili.core.ui.AppNavigationColorRole.BAR,
    )
    val selectedContainer = if (nativeMiuix) {
        AppInteractionColors.miuixNavigationBarIndicatorColor()
    } else {
        AppSurfaceTokens.selectionContainer()
    }
    val selectedContent = navigationColors?.selectedContent ?: AppSurfaceTokens.primary()
    val idleContent = navigationColors?.unselectedContent ?: AppContentColors.secondaryText()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onSelected != null) Modifier.selectableGroup() else Modifier)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(AppSurfaceTokens.cardContainer())
            .padding(vertical = 6.dp, horizontal = AppSpacingTokens.ExtraSmall),
    ) {
        THEME_PREVIEW_BOTTOM_ITEMS.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onSelected != null) Modifier.selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelected(index) },
                    ) else Modifier)
                    .then(if (nativeMiuix) Modifier
                        .clip(AppShapes.SelectionIndicator)
                        .background(if (selected) selectedContainer else Color.Transparent)
                        .padding(vertical = 6.dp)
                    else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (expanded) 44.dp else 30.dp)
                        .clip(CircleShape)
                        .background(if (selected && !nativeMiuix) selectedContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = if (nativeMiuix) com.android.purebilibili.feature.home.components.resolveHomeNavigationBarIcon(item, selected)
                            else resolveMaterialBottomBarIcon(item, selected),
                        contentDescription = null,
                        tint = if (selected) selectedContent else idleContent,
                        modifier = Modifier.size(if (expanded) 24.dp else 18.dp),
                    )
                }
                AppText(
                    text = resolveBottomNavItemLabel(item),
                    color = if (selected) selectedContent else idleContent,
                    fontSize = if (expanded) 12.sp else 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
