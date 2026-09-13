// 文件路径: feature/dynamic/components/DynamicSidebar.kt
package com.android.purebilibili.feature.dynamic.components

import coil3.request.crossfade

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.AppMotionTokens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOnIcon
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.feature.dynamic.isDynamicUpPanelItemSelected
import com.android.purebilibili.feature.dynamic.isDynamicUpPanelShortcut
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarWidth
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarDividerTopOffset
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarReturnHeaderHeightDp
import com.android.purebilibili.feature.dynamic.shouldShowDynamicUserLiveBadge
import com.android.purebilibili.feature.dynamic.SidebarUser
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback

internal fun performDynamicSidebarUserAvatarClick(
    haptic: (HapticType) -> Unit,
    onClick: () -> Unit
) {
    haptic(HapticType.LIGHT)
    onClick()
}

internal fun resolveDynamicSidebarContainerColor(
    surfaceColor: Color,
    globalWallpaperVisible: Boolean
): Color {
    return if (globalWallpaperVisible) {
        resolveGlobalWallpaperProtectiveColor(
            baseColor = surfaceColor,
            lightAlpha = 0.74f,
            darkAlpha = 0.80f
        )
    } else {
        surfaceColor
    }
}

internal fun resolveDynamicSidebarReturnHeaderColor(
    surfaceColor: Color,
    backgroundAlpha: Float,
    globalWallpaperVisible: Boolean
): Color {
    val rawColor = surfaceColor.copy(alpha = backgroundAlpha)
    if (!globalWallpaperVisible) return rawColor
    val protectiveColor = resolveGlobalWallpaperProtectiveColor(
        baseColor = surfaceColor,
        lightAlpha = 0.74f,
        darkAlpha = 0.80f
    )
    return rawColor.copy(alpha = maxOf(rawColor.alpha, protectiveColor.alpha))
}

/**
 *  动态侧边栏 - 显示关注的UP主（支持展开/收起、在线状态）
 */
@Composable
fun DynamicSidebar(
    users: List<SidebarUser>,
    selectedUserId: Long?,
    selfUid: Long = 0L,
    isExpanded: Boolean,
    userListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Long?) -> Unit,
    showHiddenUsers: Boolean,
    hiddenCount: Int,
    uplistUpdateMids: Set<Long> = emptySet(),
    onToggleShowHidden: () -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    onToggleExpand: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp, // 新增：内部处理顶部间距
    onBackClick: () -> Unit, // 新增：返回按钮回调
    modifier: Modifier = Modifier
) {
    val animatedWidth by animateFloatAsState(
        targetValue = resolveDynamicSidebarWidth(isExpanded).value,
        label = "sidebarWidth"
    )
    
    // 模糊状态
    val sidebarHazeState = rememberRecoverableHazeState()
    
    // 读取模糊强度设置
    val blurIntensity = currentUnifiedBlurIntensity()
    val backgroundAlpha = BlurStyles.getBackgroundAlpha(blurIntensity)
    val returnHeaderHeight = resolveDynamicSidebarReturnHeaderHeightDp().dp
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val sidebarContainerColor = resolveDynamicSidebarContainerColor(
        surfaceColor = AppSurfaceTokens.surface(),
        globalWallpaperVisible = globalWallpaperVisible
    )
    val returnHeaderColor = resolveDynamicSidebarReturnHeaderColor(
        surfaceColor = AppSurfaceTokens.surface(),
        backgroundAlpha = backgroundAlpha,
        globalWallpaperVisible = globalWallpaperVisible
    )
    val liveUsers = remember(users, selfUid) {
        users.filter { it.isLive && !isDynamicUpPanelShortcut(it.uid, selfUid) }
    }
    val shortcutUsers = remember(users, selfUid) {
        users.filter { isDynamicUpPanelShortcut(it.uid, selfUid) }
    }
    val restUsers = remember(users, selfUid) {
        users.filter { !it.isLive && !isDynamicUpPanelShortcut(it.uid, selfUid) }
    }
    var showLiveUsers by remember { mutableStateOf(true) }
    val visibleUsers = remember(liveUsers, shortcutUsers, restUsers, showLiveUsers) {
        buildList {
            if (showLiveUsers) addAll(liveUsers)
            addAll(shortcutUsers)
            addAll(restUsers)
        }
    }
    
    // 侧边栏容器 - Glassmorphism 升级版
    Box(
        modifier = modifier
            .width(animatedWidth.dp)
            .fillMaxHeight()
            .clip(androidx.compose.ui.graphics.RectangleShape) // [修复] 直角
            .background(
                sidebarContainerColor
            )
    ) {
        // 内容层 - 使用 Box 重新组织布局以支持模糊
        Box(modifier = Modifier.fillMaxSize()) {
            // 可滚动内容 - 作为模糊源
            LazyColumn(
                state = userListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = topPadding + returnHeaderHeight, // 与右侧动态顶栏同高，保证视觉中线一致
                    bottom = AppSpacingTokens.Large
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSourceCompat(sidebarHazeState) // 设置模糊源
            ) {
                // 隐藏用户切换按钮 (胶囊样式)
                if (hiddenCount > 0 || showHiddenUsers) {
                    item(key = "hidden_toggle") {
                        Box(
                            modifier = Modifier
                                .padding(bottom = AppSpacingTokens.Medium)
                                .size(AppChromeSizeTokens.MinimumTouchTarget)
                                .clip(CircleShape)
                                .clickable { onToggleShowHidden() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                                    .clip(CircleShape)
                                    .background(
                                        if (showHiddenUsers) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(
                                    imageVector = if (showHiddenUsers) rememberAppVisibilityOnIcon() else rememberAppVisibilityOffIcon(),
                                    contentDescription = if (showHiddenUsers) "隐藏已隐藏用户" else "显示隐藏用户",
                                    tint = if (showHiddenUsers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall)
                                )
                            }
                        }
                    }
                }

                if (liveUsers.isNotEmpty()) {
                    item(key = "live_fold") {
                        AppTextButton(
                            onClick = { showLiveUsers = !showLiveUsers },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                horizontal = AppSpacingTokens.None,
                                vertical = AppSpacingTokens.ExtraSmall,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppText(
                                    text = "Live(${liveUsers.size})",
                                    modifier = Modifier.fillMaxWidth(),
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        stepSize = 0.5.sp,
                                    ),
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                AppIcon(
                                    imageVector = if (showLiveUsers) rememberAppChevronUpIcon() else rememberAppChevronDownIcon(),
                                    contentDescription = if (showLiveUsers) "收起直播" else "展开直播",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(AppSpacingTokens.Medium)
                                )
                            }
                        }
                    }
                }
                itemsIndexed(visibleUsers, key = { _, u -> "sidebar_${u.uid}" }) { index, user ->
                    CascadeSidebarItem(
                        index = index,
                        content = {
                            val isShortcut = isDynamicUpPanelShortcut(user.uid, selfUid)
                            SidebarUserItem(
                                user = user,
                                isSelected = isDynamicUpPanelItemSelected(selectedUserId, user.uid),
                                showLabel = isExpanded,
                                showUnreadBadge = user.uid in uplistUpdateMids,
                                allowManageMenu = !isShortcut,
                                onClick = { onUserClick(user.uid) },
                                onTogglePin = { onTogglePin(user.uid) },
                                onToggleHidden = { onToggleHidden(user.uid) }
                            )
                        }
                    )
                }
            }
            
            // 顶部返回按钮区域 - 应用模糊效果
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPadding + returnHeaderHeight)
                    .unifiedBlur(sidebarHazeState) // 应用模糊
                    .background(returnHeaderColor)
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(returnHeaderHeight)
                        .align(Alignment.BottomCenter)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = rememberAppBackIcon(),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
                    )
                }
            }
        }
        
        // 右侧边框线 - 极细
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = resolveDynamicSidebarDividerTopOffset(topPadding))
                .fillMaxHeight()
                .width(AppSpacingTokens.Micro / 4)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        )
    }
}

/**
 *  [新增] 瀑布入场动画包装器
 * 每个项目有递增的延迟，形成瀑布展开效果
 */
@Composable
private fun CascadeSidebarItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val delay = 30 * index  // 每个项目延迟 30ms
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = AppMotionTokens.emphasizedSpec(),
        label = "cascadeOffsetY"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "cascadeAlpha"
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = offsetY
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/**
 *  侧边栏项目（文字图标）
 */
@Composable
fun SidebarItem(
    icon: String,
    label: String?,
    isSelected: Boolean,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacingTokens.Small)
    ) {
        Box(
            modifier = Modifier
                .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = icon,
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (label != null) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            AppText(
                text = label,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                // The label sits outside the selected icon container, so it uses the
                // accent role rather than the container's paired content role.
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 *  侧边栏用户项（头像 + 在线状态）
 */
@Composable
fun SidebarUserItem(
    user: SidebarUser,
    isSelected: Boolean,
    showLabel: Boolean,
    showUnreadBadge: Boolean = false,
    allowManageMenu: Boolean = true,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = if (user.isHidden) "${user.name}(隐)" else user.name
    val haptic = rememberHapticFeedback()

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacingTokens.ExtraSmall, horizontal = AppSpacingTokens.ExtraSmall) // 增加水平间距以适应选中背景
                .clip(AppShapes.container(ContainerLevel.Card)) // 选中态圆角背景
                .then(
                    if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    else Modifier
                )
                .combinedClickable(
                    onClick = {
                        performDynamicSidebarUserAvatarClick(
                            haptic = haptic,
                            onClick = onClick
                        )
                    },
                    onLongClick = { if (allowManageMenu) showMenu = true }
                )
                .padding(vertical = AppSpacingTokens.Small) // 内部间距
                .alpha(if (user.isHidden) 0.5f else 1f)
        ) {
            Box {
                // 头像
                val faceUrl = remember(user.face) {
                    val raw = user.face.trim()
                    when {
                        raw.isEmpty() -> ""
                        raw.startsWith("https://") -> raw
                        raw.startsWith("http://") -> raw.replace("http://", "https://")
                        raw.startsWith("//") -> "https:$raw"
                        else -> "https://$raw"
                    }
                }

                Box(
                    modifier = Modifier
                        .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium)
                        .then(
                            when {
                                isSelected -> Modifier.border(AppSpacingTokens.Micro, MaterialTheme.colorScheme.primary, CircleShape)
                                else -> Modifier.border(AppSpacingTokens.Micro / 2, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                            }
                        )
                        .padding(AppSpacingTokens.Micro),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(faceUrl.ifEmpty { null })
                            .crossfade(true)
                            .build(),
                        contentDescription = user.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                if (showUnreadBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(AppSpacingTokens.Small)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            if (shouldShowDynamicUserLiveBadge(user.isLive)) {
                DynamicUserLiveBadge(modifier = Modifier.padding(top = AppSpacingTokens.Micro))
            }

            if (showLabel) {
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                AppText(
                    text = displayName,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, // 自适应文字
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppSpacingTokens.Micro)
                )
            }
        }

        AppDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer) // 自适应菜单背景
        ) {
            AppDropdownMenuItem(
                text = { AppText(if (user.isPinned) "取消置顶" else "置顶", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
            AppDropdownMenuItem(
                text = { AppText(if (user.isHidden) "取消隐藏" else "隐藏", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    showMenu = false
                    onToggleHidden()
                }
            )
        }
    }
}
