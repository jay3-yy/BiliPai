// 文件路径: feature/dynamic/components/DynamicSidebar.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppDynamicIcon
import com.android.purebilibili.feature.dynamic.isDynamicUpPanelAllShortcut
import com.android.purebilibili.core.ui.rememberAppVisibilityOnIcon
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.feature.dynamic.isDynamicUpPanelItemSelected
import com.android.purebilibili.feature.dynamic.isDynamicUpPanelShortcut
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarWidth
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarDividerTopOffset
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarReturnHeaderHeightDp
import com.android.purebilibili.feature.dynamic.shouldShowDynamicUserLiveBadge
import com.android.purebilibili.feature.dynamic.SidebarUser
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.derivedStateOf
import com.android.purebilibili.feature.dynamic.prefetchDynamicSidebarNeighborAvatars
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarFlingDampingFactor
import com.android.purebilibili.feature.dynamic.resolveDynamicSidebarUserAvatarUrl
import com.android.purebilibili.feature.dynamic.shouldAnimateSidebarItemCascade

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

@Composable
internal fun rememberDynamicSidebarFlingBehavior(
    velocityMultiplier: Float = resolveDynamicSidebarFlingDampingFactor()
): FlingBehavior {
    val defaultFling = ScrollableDefaults.flingBehavior()
    return remember(defaultFling, velocityMultiplier) {
        DynamicSidebarDampedFlingBehavior(defaultFling, velocityMultiplier)
    }
}

internal class DynamicSidebarDampedFlingBehavior(
    private val baseFling: FlingBehavior,
    private val velocityMultiplier: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val dampedVelocity = initialVelocity * velocityMultiplier
        val leftoverDamped = with(baseFling) {
            performFling(dampedVelocity)
        }
        return if (velocityMultiplier > 0f) leftoverDamped / velocityMultiplier else 0f
    }
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
    chromeBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
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
    val themeConfig = LocalAppThemeConfig.current
    val glassWallpaperSidebar = themeConfig.liquidGlassEnabled && globalWallpaperVisible
    // 独立记录列表，顶部效果只读取它，不记录返回按钮，避免反馈采样。
    val progressiveRequested = themeConfig.progressiveTopBlurEnabled && !themeConfig.headerBlurEnabled
    val sidebarBackdropSource = if (
        themeConfig.liquidGlassEnabled ||
        shouldUseBiliPaiProgressiveTopBlur(progressiveRequested, hasBackdrop = true)
    ) rememberChromeBackdropSource() else null
    val sidebarBackdrop = sidebarBackdropSource?.takeIf { it.isReady }?.backdrop
    val headerBlurActive = themeConfig.headerBlurEnabled && !glassWallpaperSidebar &&
        recoverableBlurEnabled(sidebarHazeState)
    val wallpaperSource = LocalGlobalWallpaperBackdropSource.current
    val returnIconBackdrop = if (themeConfig.liquidGlassEnabled) {
        // Sample the sidebar list so avatars behind the back button remain visible
        // through its glass. The wallpaper source alone does not contain page content.
        sidebarBackdrop ?: wallpaperSource?.takeIf { it.isReady }?.backdrop ?: chromeBackdrop
    } else chromeBackdrop
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
        users.filter { isDynamicUpPanelShortcut(it.uid, selfUid) && !isDynamicUpPanelAllShortcut(it.uid) }
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

    var initialEntranceActive by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        initialEntranceActive = false
    }
    val hasScrolled by remember {
        derivedStateOf {
            userListState.firstVisibleItemIndex > 0 || userListState.firstVisibleItemScrollOffset > 0
        }
    }
    val flingBehavior = rememberDynamicSidebarFlingBehavior()

    val context = LocalContext.current
    val avatarDecodeSizePx = rememberDynamicUserAvatarDecodeSizePx()
    val pageImageLoadingAllowed = LocalPageImageLoadingAllowed.current
    // Observe scrolling inside the effect: scrolling does not recompose the whole sidebar.
    // Only one small decode runs at a time, and a new viewport or inactive page cancels it.
    LaunchedEffect(userListState, visibleUsers, context, avatarDecodeSizePx, pageImageLoadingAllowed) {
        if (!pageImageLoadingAllowed) return@LaunchedEffect
        val imageLoader = coil3.SingletonImageLoader.get(context)
        prefetchDynamicSidebarNeighborAvatars(
            users = visibleUsers,
            visibleUserIds = snapshotFlow {
                if (userListState.isScrollInProgress) emptyList() else {
                    userListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                        (item.key as? String)?.removePrefix("sidebar_")?.toLongOrNull()
                    }
                }
            },
        ) { url ->
            imageLoader.execute(buildDynamicUserAvatarRequest(context, url, avatarDecodeSizePx))
        }
    }

    // 侧边栏容器 - Glassmorphism 升级版
    Box(
        modifier = modifier
            .width(animatedWidth.dp)
            .fillMaxHeight()
            .clip(androidx.compose.ui.graphics.RectangleShape) // [修复] 直角
            // Glass GLOBAL mode replays the same full-window wallpaper as the feed. Keep
            // the native sidebar's protective fill and header blur when glass is disabled.
            .then(
                if (glassWallpaperSidebar) {
                    Modifier
                        .then(
                            if (wallpaperSource != null) {
                                Modifier.sharedGlobalWallpaperBackground(AppSurfaceTokens.surface())
                            } else {
                                // Animated wallpapers stay in the full-window root player.
                                // Reveal it directly rather than painting a separate panel.
                                Modifier
                            }
                        )
                        .background(AppSurfaceTokens.surface().copy(alpha = 0.12f))
                } else Modifier.background(sidebarContainerColor)
            )
    ) {
        // 内容层 - 使用 Box 重新组织布局以支持模糊
        Box(modifier = Modifier.fillMaxSize()) {
            // 可滚动内容 - 作为模糊源
            LazyColumn(
                state = userListState,
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = topPadding + returnHeaderHeight, // 与右侧动态顶栏同高，保证视觉中线一致
                    bottom = AppSpacingTokens.Large
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .then(sidebarBackdropSource?.modifier ?: Modifier)
                    .then(
                        if (sidebarBackdropSource != null) {
                            // 将壁纸一起录入背景；玻璃关闭时仍使用原生侧栏底色。
                            if (glassWallpaperSidebar && wallpaperSource != null) {
                                Modifier.sharedGlobalWallpaperBackground(AppSurfaceTokens.surface())
                                    .background(AppSurfaceTokens.surface().copy(alpha = 0.12f))
                            } else Modifier.background(
                                if (glassWallpaperSidebar) Color.Transparent else sidebarContainerColor
                            )
                        } else Modifier
                    )
                    .then(
                        if (glassWallpaperSidebar ||
                            !com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                        ) Modifier
                        else Modifier.hazeSourceCompat(sidebarHazeState)
                    )
            ) {
                users.firstOrNull { isDynamicUpPanelAllShortcut(it.uid) }?.let { allUser ->
                    item(key = "all_dynamics") {
                        SidebarUserItem(
                            user = allUser,
                            selfUid = selfUid,
                            isSelected = selectedUserId == null,
                            showLabel = isExpanded,
                            allowManageMenu = false,
                            onClick = { onUserClick(allUser.uid) },
                            onTogglePin = { onTogglePin(allUser.uid) },
                            onToggleHidden = { onToggleHidden(allUser.uid) }
                        )
                    }
                }
                // 隐藏用户切换按钮 (胶囊样式)
                if (hiddenCount > 0 || showHiddenUsers) {
                    item(key = "hidden_toggle") {
                        Box(
                            modifier = Modifier
                                .padding(bottom = AppSpacingTokens.Medium)
                                .size(AppChromeSizeTokens.MinimumTouchTarget)
                                .clip(CircleShape)
                                .appActionClickable { onToggleShowHidden() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                                    .clip(CircleShape)
                                    .background(
                                        if (showHiddenUsers) AppContentColors.accent().copy(alpha = 0.2f)
                                        else AppContentColors.primaryText().copy(alpha = 0.05f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(
                                    imageVector = if (showHiddenUsers) rememberAppVisibilityOnIcon() else rememberAppVisibilityOffIcon(),
                                    contentDescription = if (showHiddenUsers) "隐藏已隐藏用户" else "显示隐藏用户",
                                    tint = if (showHiddenUsers) AppContentColors.accent() else AppContentColors.secondaryText(),
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
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                AppIcon(
                                    imageVector = if (showLiveUsers) rememberAppChevronUpIcon() else rememberAppChevronDownIcon(),
                                    contentDescription = if (showLiveUsers) "收起直播" else "展开直播",
                                    modifier = Modifier.size(AppSpacingTokens.Medium)
                                )
                            }
                        }
                    }
                }
                itemsIndexed(visibleUsers, key = { _, u -> "sidebar_${u.uid}" }) { index, user ->
                    val shouldAnimate = shouldAnimateSidebarItemCascade(
                        index = index,
                        hasScrolled = hasScrolled,
                        initialEntranceActive = initialEntranceActive
                    )
                    CascadeSidebarItem(
                        index = index,
                        enabled = shouldAnimate,
                        content = {
                            val isShortcut = isDynamicUpPanelShortcut(user.uid, selfUid)
                            SidebarUserItem(
                                user = user,
                                selfUid = selfUid,
                                isSelected = isDynamicUpPanelItemSelected(selectedUserId, user.uid),
                                showLabel = isExpanded,
                                showUnreadBadge = user.uid in uplistUpdateMids,
                                allowManageMenu = !isShortcut,
                                onClick = appActionWithFeedback { onUserClick(user.uid) },
                                onTogglePin = { onTogglePin(user.uid) },
                                onToggleHidden = { onToggleHidden(user.uid) }
                            )
                        }
                    )
                }
            }

            // 与动态顶栏共用渐进模糊 / 纯色渐变设置，不改变原生按钮和区域尺寸。
            BiliPaiImmersiveTopBar(
                backdrop = sidebarBackdrop,
                enabled = progressiveRequested,
                headerBlurActive = headerBlurActive,
                liquidGlassActive = themeConfig.liquidGlassEnabled,
                fadeEnabled = themeConfig.progressiveTopFadeEnabled && !themeConfig.headerBlurEnabled,
                surfaceColor = AppSurfaceTokens.surface(),
                opaqueBackgroundFallback = false,
                iconSurfaceColor = if (glassWallpaperSidebar || progressiveRequested ||
                    (themeConfig.progressiveTopFadeEnabled && !themeConfig.headerBlurEnabled)
                ) Color.Transparent else returnHeaderColor,
                extendBelowBounds = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPadding + returnHeaderHeight)
                    .then(
                        if (headerBlurActive) Modifier.unifiedBlur(sidebarHazeState)
                        else Modifier
                    )
                    .background(
                        if (glassWallpaperSidebar || progressiveRequested ||
                            (themeConfig.progressiveTopFadeEnabled && !themeConfig.headerBlurEnabled)
                        ) Color.Transparent else returnHeaderColor
                    )
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(returnHeaderHeight)
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(
                        LocalAppTopBarIconDecorationEnabled provides true,
                        LocalAppTopBarIconBackdrop provides returnIconBackdrop,
                    ) {
                        AppIconButton(onClick = onBackClick) {
                            AppIcon(
                                imageVector = rememberAppBackIcon(),
                                contentDescription = "返回",
                                tint = AppContentColors.primaryText(),
                                modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
                            )
                        }
                    }
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
 * 仅在首次入场且未滑动时对前几个项目使用递增延迟，形成瀑布展开效果
 */
@Composable
private fun CascadeSidebarItem(
    index: Int,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

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
    val nativeNavigation = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .appActionClickable(onClick = onClick)
            .padding(vertical = AppSpacingTokens.Small)
    ) {
        Box(
            modifier = Modifier
                .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                .clip(CircleShape)
                .background(
                    if (isSelected) nativeNavigation?.selectedContainer ?: MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = icon,
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) nativeNavigation?.selectedContent ?: MaterialTheme.colorScheme.onPrimaryContainer else AppContentColors.secondaryText()
            )
        }

        if (label != null) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            AppText(
                text = label,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                // The label sits outside the selected icon container, so it uses the
                // accent role rather than the container's paired content role.
                color = if (isSelected) nativeNavigation?.selectedContent ?: AppContentColors.accent() else AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DynamicSelfAvatar(face: String, isSelected: Boolean) {
    val faceUrl = remember(face) { resolveDynamicSidebarUserAvatarUrl(face) }
    var avatarLoaded by remember(faceUrl) { mutableStateOf(false) }
    val request = rememberDynamicUserAvatarRequest(face)
    Box(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "我" },
        contentAlignment = Alignment.Center
    ) {
        // 本地图标仅作加载/失败占位，资料刷新后以真实头像替换。
        if (!avatarLoaded) {
            AppIcon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = if (isSelected) AppContentColors.accent() else AppContentColors.secondaryText(),
                modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
            )
        }
        if (faceUrl.isNotEmpty()) {
            PageAwareAsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
                onSuccess = { avatarLoaded = true },
                onError = { avatarLoaded = false }
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
    selfUid: Long,
    isSelected: Boolean,
    showLabel: Boolean,
    showUnreadBadge: Boolean = false,
    allowManageMenu: Boolean = true,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val glassMenuAvailable = com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
    val displayName = if (user.isHidden) "${user.name}(隐)" else user.name
    val isAllShortcut = isDynamicUpPanelAllShortcut(user.uid)
    val navigationColors = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation()
    val accentColor = navigationColors?.selectedContent
        ?: AppContentColors.selectionAccent(LocalAppThemeConfig.current.liquidGlassEnabled)
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)

    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { showMenu = true }, enabled = allowManageMenu) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacingTokens.ExtraSmall, horizontal = AppSpacingTokens.ExtraSmall)
                // 玻璃与原生侧栏共用局部选中提示；整项裁切仅约束按压反馈。
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = appActionWithFeedback {
                        performDynamicSidebarUserAvatarClick(
                            haptic = haptic,
                            onClick = onClick
                        )
                    },
                    onLongClick = if (glassMenuAvailable) null else ({ if (allowManageMenu) showMenu = true })
                )
                .padding(vertical = AppSpacingTokens.Small) // 内部间距
                .alpha(if (user.isHidden) 0.5f else 1f)
        ) {
            Box {
                // 头像
                Box(
                    modifier = Modifier
                        .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium)
                        .then(
                            when {
                                isAllShortcut && isSelected -> Modifier.background(
                                    navigationColors?.selectedContainer ?: accentColor.copy(alpha = 0.12f),
                                    CircleShape
                                )
                                isSelected -> Modifier.border(AppSpacingTokens.Micro / 2, accentColor, CircleShape)
                                else -> Modifier.border(AppSpacingTokens.Micro / 2, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                            }
                        )
                        .padding(AppSpacingTokens.Micro),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAllShortcut) {
                        AppIcon(
                            imageVector = rememberAppDynamicIcon(),
                            contentDescription = user.name,
                            tint = if (isSelected) accentColor else AppContentColors.secondaryText(),
                            modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
                        )
                    } else if (selfUid > 0L && user.uid == selfUid) {
                        DynamicSelfAvatar(face = user.face, isSelected = isSelected)
                    } else {
                        PageAwareAsyncImage(
                            model = rememberDynamicUserAvatarRequest(user.face),
                            contentDescription = user.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (showUnreadBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(AppSpacingTokens.Small)
                            .clip(CircleShape)
                            .background(AppContentColors.accent())
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
                    color = if (isSelected) accentColor else AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppSpacingTokens.Micro)
                )
            }
        }

        AppDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = if (com.android.purebilibili.core.ui.components.appGlassMenuAvailable()) Modifier
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainer) // 自适应菜单背景
        ) {
            AppDropdownMenuItem(
                text = { AppText(if (user.isPinned) "取消置顶" else "置顶", color = AppContentColors.primaryText()) },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
            AppDropdownMenuItem(
                text = { AppText(if (user.isHidden) "取消隐藏" else "隐藏", color = AppContentColors.primaryText()) },
                onClick = {
                    showMenu = false
                    onToggleHidden()
                }
            )
        }
    }
}
