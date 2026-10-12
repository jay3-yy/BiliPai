package com.android.purebilibili.feature.home.components
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.theme.iOSYellow
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppChevronForwardIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.ui.rememberAppInboxIcon
import com.android.purebilibili.core.ui.rememberAppLogoutIcon
import com.android.purebilibili.core.ui.rememberAppTvIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.ui.AppDrawerContainerTreatment
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.components.UserVipBadge
import com.android.purebilibili.core.ui.components.resolveUserVipBadgeLabel
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.rememberAppDrawerVisualPolicy
import com.android.purebilibili.feature.home.UserState
import dev.chrisbanes.haze.HazeState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import kotlinx.coroutines.launch
import java.io.File
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

/**
 * 首页侧边栏 - 优化版 (带毛玻璃效果)
 * 采用更紧凑的布局和更现代的视觉风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineSideDrawer(
    drawerState: DrawerState,
    user: UserState,
    onLogout: () -> Unit,
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikedVideosClick: () -> Unit,
    onBangumiClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAccountSwitchClick: (() -> Unit)? = null,
    hazeState: HazeState? = null, // 毛玻璃效果状态
    isBlurEnabled: Boolean = true, // [新增] 模糊开关状态
    bottomOverlayHeight: Dp = AppSpacingTokens.None,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassEnabled: Boolean = false,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    skinBackgroundImagePath: String? = null,
    skinBottomTrimImagePath: String? = null,
    skinBackgroundTint: Color? = null,
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveMineSideDrawerLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    // 侧边栏宽度自适应：中屏/大屏不再沿用手机上限 360dp
    val drawerWidth = remember(configuration.screenWidthDp, layoutPolicy) {
        resolveMineSideDrawerWidthDp(
            screenWidthDp = configuration.screenWidthDp,
            policy = layoutPolicy
        ).dp
    }
    val footerSpacerHeight = remember(layoutPolicy, bottomOverlayHeight) {
        resolveMineSideDrawerFooterSpacerHeightDp(
            policy = layoutPolicy,
            bottomOverlayHeightDp = bottomOverlayHeight.value.toInt()
        ).dp
    }
    
    // 辅助函数：关闭侧边栏并执行回调
    fun closeAndRun(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }
    
    // 检测深色模式
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val liquidGlassActive = liquidGlassEnabled && miuixBackdrop != null
    val blurActive = hazeState != null && isBlurEnabled && !liquidGlassActive
    val drawerMotionBudget = resolveDrawerMotionBudget(
        isDrawerTransitionRunning = drawerState.currentValue != drawerState.targetValue
    )
    val forceLowBlurBudget = shouldForceLowDrawerBlurBudget(drawerMotionBudget)
    val effectiveBlurActive = shouldEnableDrawerBlur(
        blurActive = blurActive,
        budget = drawerMotionBudget
    )
    val glassVisualActive = effectiveBlurActive || liquidGlassActive
    val visualPolicy = rememberAppDrawerVisualPolicy(blurEnabled = glassVisualActive)
    val palette = resolveDrawerGlassPalette(
        isDark = isDark,
        blurEnabled = glassVisualActive,
        budget = drawerMotionBudget
    )
    val colorScheme = MaterialTheme.colorScheme
    val downloadIcon = rememberAppDownloadIcon()
    val historyIcon = rememberAppHistoryIcon()
    val likeIcon = rememberAppLikeIcon()
    val tvIcon = rememberAppTvIcon()
    val bookmarkIcon = rememberAppBookmarkIcon()
    val watchLaterIcon = rememberAppWatchLaterIcon()
    val inboxIcon = rememberAppInboxIcon()
    val logoutIcon = rememberAppLogoutIcon()
    val chevronForwardIcon = rememberAppChevronForwardIcon()
    val accountSwitchIcon = Icons.Outlined.SwapHoriz
    val hasSkinBackground = !skinBackgroundImagePath.isNullOrBlank()

    // 动态文字颜色
    val activeContentColor = colorScheme.onSurface
    // 动态次级文字/图标颜色
    val secondaryContentColor = colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.92f else 0.86f)
    // 动态分割线颜色
    val dividerColor = colorScheme.outlineVariant.copy(alpha = palette.dividerAlpha)
    val useOpaqueContainers = visualPolicy.containerTreatment == AppDrawerContainerTreatment.OPAQUE
    val drawerBaseColor = if (useOpaqueContainers) {
        colorScheme.surfaceContainer
    } else if (isDark) {
        colorScheme.surface.copy(alpha = palette.drawerBaseAlpha)
    } else {
        colorScheme.surface.copy(alpha = palette.drawerBaseAlpha)
    }
    val itemSurfaceColor = if (useOpaqueContainers) {
        colorScheme.surfaceContainerHigh
    } else if (isDark) {
        colorScheme.surfaceContainerHigh.copy(alpha = palette.itemSurfaceAlpha)
    } else {
        colorScheme.surfaceContainerHigh.copy(alpha = palette.itemSurfaceAlpha)
    }
    val itemBorderColor = colorScheme.outlineVariant.copy(alpha = palette.itemBorderAlpha)
    val chevronColor = secondaryContentColor.copy(alpha = if (isDark) 0.92f else 0.84f)

    val drawerShape = AppShapes.endRounded(layoutPolicy.drawerEdgeRadiusDp.dp)
    val profileCardShape = AppShapes.borderedContainer(ContainerLevel.Card)
    val sectionShape = AppShapes.borderedContainer(ContainerLevel.Card)

    // 使用 Surface 替代 ModalDrawerSheet 以绕过最小宽度限制 (240dp)
    AppSurface(
        color = if (liquidGlassActive) Color.Transparent else drawerBaseColor,
        contentColor = activeContentColor,
        shape = drawerShape,
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .then(
                if (liquidGlassActive) {
                    Modifier.biliPaiFloatingDockShell(
                        backdrop = miuixBackdrop,
                        containerColor = colorScheme.surfaceContainer,
                        pressProgress = 0f,
                        shape = drawerShape,
                        liquidGlassTuning = liquidGlassTuning,
                    )
                } else if (effectiveBlurActive) {
                    Modifier.unifiedBlur(
                        hazeState = requireNotNull(hazeState),
                        enabled = true,
                        shape = drawerShape,
                        forceLowBudget = forceLowBlurBudget
                    )
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (hasSkinBackground) {
                AsyncImage(
                    model = File(requireNotNull(skinBackgroundImagePath)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(if (isDark) 0.58f else 0.72f)
                        .clearAndSetSemantics {}
                )
            }
            if (!skinBottomTrimImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(skinBottomTrimImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .alpha(0.90f)
                        .clearAndSetSemantics {}
                )
            }
            if (skinBackgroundTint != null && skinBackgroundTint != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(skinBackgroundTint.copy(alpha = 0.16f))
                        .clearAndSetSemantics {}
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (hasSkinBackground && isDark) {
                                listOf(
                                    colorScheme.surface.copy(alpha = 0.48f),
                                    colorScheme.surface.copy(alpha = 0.28f),
                                    colorScheme.scrim.copy(alpha = 0.36f)
                                )
                            } else if (hasSkinBackground) {
                                listOf(
                                    colorScheme.surface.copy(alpha = 0.30f),
                                    colorScheme.surface.copy(alpha = 0.18f),
                                    colorScheme.surface.copy(alpha = 0.34f)
                                )
                            } else if (isDark) {
                                listOf(
                                    colorScheme.onSurface.copy(alpha = 0.05f),
                                    Color.Transparent,
                                    colorScheme.scrim.copy(alpha = 0.08f)
                                )
                            } else {
                                listOf(
                                    colorScheme.onSurface.copy(alpha = 0.18f),
                                    colorScheme.onSurface.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            }
                        )
                    )
                    .clearAndSetSemantics {}
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                    .padding(vertical = layoutPolicy.contentVerticalPaddingDp.dp)
            ) {
            // 1. 用户信息区域 - 可点击进入个人主页
            // 移除 Surface 背景，只保留点击区域和内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layoutPolicy.sectionHorizontalPaddingDp.dp)
                    .clip(profileCardShape)
                    .background(itemSurfaceColor)
                    .border(
                        BorderStroke(AppSpacingTokens.Micro * 0.4f, itemBorderColor),
                        profileCardShape
                    )
                    .clickable { closeAndRun(onProfileClick) }
                    // 背景完全透明，依靠下方毛玻璃效果
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(layoutPolicy.profileRowPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像 (尺寸再次微调，适应更窄的栏宽)
                    AsyncImage(
                        model = user.face,
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .size(layoutPolicy.profileAvatarSizeDp.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    
                    Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                    
                    // 用户名 + 等级 / 大会员（与个人空间同一行：LV + 主题色软胶囊）
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = user.name.ifEmpty { "未登录" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = activeContentColor,
                            maxLines = 1,
                        )

                        if (user.isLogin) {
                            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (user.level > 0) {
                                    UserLevelBadge(level = user.level)
                                }
                                if (user.isVip) {
                                    UserVipBadge(
                                        label = resolveUserVipBadgeLabel(
                                            label = user.vipLabel,
                                            vipType = user.vipType,
                                        ),
                                        fontSize = androidx.compose.material3.MaterialTheme.typography.labelSmall.fontSize,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                    
                    // 右箭头
                    AppIcon(
                        imageVector = chevronForwardIcon,
                        contentDescription = null,
                        tint = secondaryContentColor,
                        modifier = Modifier.size(visualPolicy.profileChevronSizeDp.dp)
                    )
                }
            }

            if (onAccountSwitchClick != null) {
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layoutPolicy.sectionHorizontalPaddingDp.dp),
                    shape = sectionShape,
                    color = itemSurfaceColor,
                    border = BorderStroke(AppSpacingTokens.Micro * 0.4f, itemBorderColor)
                ) {
                    AppPreference(
                        icon = accountSwitchIcon,
                        title = "切换账号",
                        onClick = { closeAndRun(onAccountSwitchClick) },
                        iconTint = iOSBlue,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                }
            }
            
            // 分割线样式
            val dividerThickness = AppSpacingTokens.Micro / 4
            
            // 组间分割线 (全宽带padding)
            AppHorizontalDivider(
                modifier = Modifier.padding(
                    horizontal = layoutPolicy.dividerHorizontalPaddingDp.dp,
                    vertical = layoutPolicy.dividerVerticalPaddingDp.dp
                ),
                thickness = dividerThickness,
                color = dividerColor
            )

            // 2. 常用服务 - iOS 风格列表
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layoutPolicy.sectionHorizontalPaddingDp.dp),
                shape = sectionShape,
                color = itemSurfaceColor,
                border = BorderStroke(AppSpacingTokens.Micro * 0.4f, itemBorderColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppPreference(
                        icon = downloadIcon,
                        title = "离线缓存",
                        onClick = { closeAndRun(onDownloadClick) },
                        iconTint = MaterialTheme.colorScheme.primary,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = historyIcon,
                        title = "历史记录",
                        onClick = { closeAndRun(onHistoryClick) },
                        iconTint = iOSBlue,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = tvIcon,
                        title = "番剧影视",
                        onClick = { closeAndRun(onBangumiClick) },
                        iconTint = iOSPink,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = bookmarkIcon,
                        title = "我的收藏",
                        onClick = { closeAndRun(onFavoriteClick) },
                        iconTint = iOSYellow,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = likeIcon,
                        title = "我的点赞",
                        onClick = { closeAndRun(onLikedVideosClick) },
                        iconTint = iOSPink,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = watchLaterIcon,
                        title = "稍后再看",
                        onClick = { closeAndRun(onWatchLaterClick) },
                        iconTint = iOSGreen,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                    AppHorizontalDivider(modifier = Modifier.padding(start = AppSpacingTokens.TripleExtraLarge), thickness = dividerThickness, color = dividerColor)
                    AppPreference(
                        icon = inboxIcon,
                        title = "消息中心",
                        onClick = { closeAndRun(onInboxClick) },
                        iconTint = iOSPink,
                        textColor = activeContentColor,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                }
            }
            
            // 组间分割线
            AppHorizontalDivider(
                modifier = Modifier.padding(
                    horizontal = layoutPolicy.dividerHorizontalPaddingDp.dp,
                    vertical = layoutPolicy.dividerVerticalPaddingDp.dp
                ),
                thickness = dividerThickness,
                color = dividerColor
            )
            
            // 3. 退出登录按钮
            if (user.isLogin) {
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layoutPolicy.sectionHorizontalPaddingDp.dp),
                    shape = sectionShape,
                    color = itemSurfaceColor,
                    border = BorderStroke(AppSpacingTokens.Micro * 0.4f, itemBorderColor)
                ) {
                    AppPreference(
                        icon = logoutIcon,
                        title = "退出登录",
                        onClick = { closeAndRun(onLogout) },
                        iconTint = colorScheme.error,
                        textColor = colorScheme.error,
                        valueColor = secondaryContentColor,
                        chevronTint = chevronColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(footerSpacerHeight))
        }
        }
    }
}
