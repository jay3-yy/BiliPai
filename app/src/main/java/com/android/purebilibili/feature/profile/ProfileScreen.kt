package com.android.purebilibili.feature.profile

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSYellow
import com.android.purebilibili.core.theme.DarkBackground
import com.android.purebilibili.core.theme.DarkSurface
import com.android.purebilibili.core.theme.DarkSurfaceVariant
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.BiliGradientButton
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AdaptiveTopAppBarStyle
import com.android.purebilibili.core.ui.AdaptiveSplitLayout
import com.android.purebilibili.core.ui.TopReadabilityChrome
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppFolderIcon
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppInboxIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppLockIcon
import com.android.purebilibili.core.ui.rememberAppPhotoIcon
import com.android.purebilibili.core.ui.rememberAppProfileAddIcon
import com.android.purebilibili.core.ui.rememberAppRefreshIcon
import com.android.purebilibili.core.ui.rememberAppRestoreIcon
import com.android.purebilibili.core.ui.rememberAppSettingsIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.rememberAppWarningIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.ui.components.IOSGroup
import com.android.purebilibili.core.ui.components.IOSClickableItem
import com.android.purebilibili.core.ui.components.IOSDivider
import com.android.purebilibili.core.ui.components.IOSSwitchItem
import com.android.purebilibili.core.ui.components.IOSSectionTitle
import com.android.purebilibili.core.ui.components.IOSGridItem
import com.android.purebilibili.core.store.StoredAccountSession
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.times
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


internal fun shouldEnableProfileHeaderLoginClick(isLogin: Boolean): Boolean = !isLogin

internal fun resolveProfileWallpaperActionColumnCount(screenWidthDp: Int): Int {
    return if (screenWidthDp < 360) 2 else 3
}

internal enum class ProfileWallpaperActionLabelMode {
    SINGLE_LINE,
    TWO_LINE
}

internal fun resolveProfileWallpaperActionLabelMode(
    screenWidthDp: Int,
    columnCount: Int
): ProfileWallpaperActionLabelMode {
    return if (columnCount == 3 && screenWidthDp in 360 until 430) {
        ProfileWallpaperActionLabelMode.TWO_LINE
    } else {
        ProfileWallpaperActionLabelMode.SINGLE_LINE
    }
}

internal fun resolveProfileWallpaperActionTitleLines(
    title: String,
    labelMode: ProfileWallpaperActionLabelMode
): List<String> {
    if (labelMode == ProfileWallpaperActionLabelMode.SINGLE_LINE || title.length <= 2) {
        return listOf(title)
    }
    return listOf(title.take(2), title.drop(2))
}

internal fun resolveProfileWallpaperBlendBandDp(topBannerHeightDp: Float): Float {
    return 196f
}

internal fun resolveProfileWallpaperDecodeSizePx(screenWidthDp: Int, density: Float): Pair<Int, Int> {
    val widthPx = (screenWidthDp.coerceAtLeast(320) * density)
        .toInt()
        .coerceIn(720, 1440)
    return widthPx to (widthPx * 16 / 9).coerceAtLeast(1280)
}

internal fun resolveProfileWallpaperActionBlurEnabled(
    headerBlurEnabled: Boolean,
    bottomBarBlurEnabled: Boolean
): Boolean {
    return headerBlurEnabled || bottomBarBlurEnabled
}

internal fun shouldRenderProfileImmersiveBackground(
    hasTopPhoto: Boolean,
    deferImmersiveRenderBudget: Boolean
): Boolean {
    return hasTopPhoto && !deferImmersiveRenderBudget
}

internal fun resolveProfileTopBarScrimAlpha(
    isImmersive: Boolean,
    collapsedFraction: Float
): Float {
    return 0f
}

internal fun resolveProfileLightStatusBars(
    isImmersive: Boolean,
    useSplitLayout: Boolean,
    isDarkTheme: Boolean
): Boolean {
    if (!useSplitLayout && isImmersive) return false
    return !isDarkTheme
}

internal fun shouldPinProfileTopBarOnScroll(useSplitLayout: Boolean): Boolean = true

internal fun shouldShowProfileHistoryService(bottomBarVisibleTabIds: Collection<String>): Boolean {
    return bottomBarVisibleTabIds.none { it.equals("HISTORY", ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    isCurrentPage: Boolean = true,
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onAccountSwitchSuccess: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},  //  关注列表点击
    onDownloadClick: () -> Unit = {},  //  离线缓存点击
    onWatchLaterClick: () -> Unit = {}, // 稍后再看点击
    onInboxClick: () -> Unit = {},  //  [新增] 私信入口点击
    onVideoClick: (String) -> Unit = {},  // [新增] 视频点击（三连彩蛋跳转用）
    onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    onBangumiMoreClick: () -> Unit = {},
    deferImmersiveRenderBudget: Boolean = false
    // [注意] 移除了 globalHazeState - 双 hazeSource 模式与 Haze 库冲突
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountMid by viewModel.activeAccountMid.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var showAccountSwitchDialog by remember { mutableStateOf(false) }
    val windowSizeClass = LocalWindowSizeClass.current
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isLoggedOut = state is ProfileUiState.LoggedOut
    val isImmersiveMobileProfile = !windowSizeClass.shouldUseSplitLayout &&
        (state as? ProfileUiState.Success)?.user?.topPhoto?.isNotEmpty() == true
    val shouldControlSystemBars = isLoggedOut || isImmersiveMobileProfile
    val lightStatusBars = resolveProfileLightStatusBars(
        isImmersive = shouldControlSystemBars,
        useSplitLayout = windowSizeClass.shouldUseSplitLayout,
        isDarkTheme = isDarkTheme
    )
    
    // [Blur] Haze State
    val hazeState = rememberRecoverableHazeState()

    //  设置沉浸式状态栏和导航栏（进入时修改，离开时恢复）
    DisposableEffect(shouldControlSystemBars, lightStatusBars) {
        val window = (context as? Activity)?.window
        val insetsController = if (window != null) {
            WindowInsetsControllerCompat(window, view)
        } else null
        
        // 保存原始配置
        val originalStatusBarColor = window?.let {
            com.android.purebilibili.core.ui.getWindowStatusBarColor(it)
        } ?: android.graphics.Color.TRANSPARENT
        val originalNavBarColor = window?.let {
            com.android.purebilibili.core.ui.getWindowNavigationBarColor(it)
        } ?: android.graphics.Color.TRANSPARENT
        val originalLightStatusBars = insetsController?.isAppearanceLightStatusBars ?: true
        val originalLightNavigationBars = insetsController?.isAppearanceLightNavigationBars ?: true
        val originalDecorFits = window?.decorView?.fitsSystemWindows ?: true
        
        if (shouldControlSystemBars && window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            com.android.purebilibili.core.ui.setWindowStatusBarColor(window, Color.Transparent.toArgb())
            com.android.purebilibili.core.ui.setWindowNavigationBarColor(window, Color.Transparent.toArgb())
            insetsController?.isAppearanceLightStatusBars = lightStatusBars
            insetsController?.isAppearanceLightNavigationBars = lightStatusBars
        }
        
        onDispose {
            // 离开时恢复原始配置
            if (shouldControlSystemBars && window != null && insetsController != null) {
                WindowCompat.setDecorFitsSystemWindows(window, originalDecorFits)
                com.android.purebilibili.core.ui.setWindowStatusBarColor(window, originalStatusBarColor)
                com.android.purebilibili.core.ui.setWindowNavigationBarColor(window, originalNavBarColor)
                insetsController.isAppearanceLightStatusBars = originalLightStatusBars
                insetsController.isAppearanceLightNavigationBars = originalLightNavigationBars
            }
        }
    }

    LaunchedEffect(viewModel, isCurrentPage) {
        if (isCurrentPage) {
            viewModel.loadProfile()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSavedAccounts()
        //  [埋点] 页面浏览追踪
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("ProfileScreen")
    }

    if (showAccountSwitchDialog) {
        AccountSwitchDialog(
            accounts = accounts,
            activeAccountMid = activeAccountMid,
            onDismiss = { showAccountSwitchDialog = false },
            onAddAccount = {
                showAccountSwitchDialog = false
                onGoToLogin()
            },
            onSwitch = { mid ->
                showAccountSwitchDialog = false
                viewModel.switchAccount(
                    mid = mid,
                    onSuccess = {
                        onAccountSwitchSuccess()
                        Toast.makeText(context, "已切换账号", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onRemove = { mid ->
                viewModel.removeStoredAccount(
                    mid = mid,
                    onSuccess = {
                        Toast.makeText(context, "已移除账号", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    //  未登录状态使用沉浸式全屏布局，已登录使用正常 Scaffold
    val currentUiState = state
    when (currentUiState) {
        is ProfileUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .globalWallpaperAwareBackground(),
                contentAlignment = Alignment.Center
            ) {
                LoadingAnimation(size = 80.dp)
            }
        }
        is ProfileUiState.LoggedOut -> {
            // [Modified] 游客模式：复用统一 UI，但使用虚拟游客数据
            val guestUser = UserState(
                isLogin = false,
                name = "点击登录/注册",
                face = "", // 空头像，UserInfoSection 会处理为默认或占位符
                mid = 0,
                level = 0,
                coin = 0.0,
                bcoin = 0.0,
                isVip = false,
                vipLabel = "",
                following = 0,
                follower = 0,

                dynamic = 0,
                topPhoto = currentUiState.topPhoto // [Modified] Use photo from state
            )
            
            
            Box(modifier = Modifier.fillMaxSize()) {
                ProfileBackground(
                    user = guestUser,
                    viewModel = viewModel,
                    deferImmersiveRenderBudget = deferImmersiveRenderBudget
                )
                
                MobileProfileContent(
                    user = guestUser,
                    onLogout = onGoToLogin, // "退出登录" 变为 "登录"
                    onAccountManageClick = { showAccountSwitchDialog = true },
                    onHistoryClick = onGoToLogin, // 游客点击功能需登录
                    showHistoryService = showHistoryService,
                    onFavoriteClick = onGoToLogin,
                    onFollowingClick = { onGoToLogin() },
                    onDownloadClick = onGoToLogin,
                    onWatchLaterClick = onGoToLogin,
                    onInboxClick = onGoToLogin,  //  [新增] 游客点击需登录
                    onVideoClick = { },  // 游客模式不显示三连
                    scrollBehavior = if (shouldPinProfileTopBarOnScroll(useSplitLayout = false)) {
                        TopAppBarDefaults.pinnedScrollBehavior()
                    } else {
                        TopAppBarDefaults.enterAlwaysScrollBehavior()
                    },
                    onBack = onBack,
                    onSettingsClick = onSettingsClick,
                    hazeState = hazeState,
                    // [New] 传递点击头部去登录的回调 (需修改 MobileProfileContent 支持)
                    onHeaderClick = onGoToLogin,
                    paddingValues = PaddingValues(0.dp) // 全屏
                )
            }

        }
        is ProfileUiState.Error -> {
            // 🔧 [新增] 离线/错误状态 - 显示错误信息并提供重试和离线缓存入口
            AdaptiveScaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Box {
                        TopReadabilityChrome(
                            height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                            surfaceColor = MaterialTheme.colorScheme.background,
                            surfaceAlpha = 0.86f
                        )
                        AdaptiveTopAppBar(
                            title = "我的",
                            style = AdaptiveTopAppBarStyle.CENTERED,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(rememberAppBackIcon(), contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = onSettingsClick) {
                                    Icon(rememberAppSettingsIcon(), contentDescription = "Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 错误图标
                    Icon(
                        rememberAppWarningIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = currentUiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 重试按钮
                    Button(
                        onClick = { viewModel.loadProfile(force = true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(rememberAppRefreshIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重试")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 离线缓存入口
                    OutlinedButton(onClick = onDownloadClick) {
                        Icon(rememberAppDownloadIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("查看离线缓存")
                    }
                }
            }
        }
        is ProfileUiState.Success -> {
            val scrollBehavior = if (shouldPinProfileTopBarOnScroll(windowSizeClass.shouldUseSplitLayout)) {
                TopAppBarDefaults.pinnedScrollBehavior()
            } else {
                TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            }
            val favoriteFolderShortcuts = remember(currentUiState.favoriteFolders, currentUiState.user.mid) {
                resolveProfileFavoriteFolderShortcuts(
                    folders = currentUiState.favoriteFolders,
                    ownerMid = currentUiState.user.mid
                )
            }
            LaunchedEffect(currentUiState.space.signSaveMessage) {
                currentUiState.space.signSaveMessage?.let { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearProfileSpaceMessage()
                }
            }
            
            AdaptiveScaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background,
                // [Immersive] Mobile hides default TopBar, Tablet keeps it
                topBar = {
                    if (windowSizeClass.shouldUseSplitLayout) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            TopReadabilityChrome(
                                height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 104.dp,
                                surfaceColor = MaterialTheme.colorScheme.background,
                                surfaceAlpha = 0.82f,
                                hazeState = hazeState,
                                hazeEnabled = true
                            )
                            AdaptiveTopAppBar(
                                title = "我的",
                                largeTitle = "我的",
                                style = AdaptiveTopAppBarStyle.LARGE,
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(rememberAppBackIcon(), contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = onSettingsClick) {
                                        Icon(rememberAppSettingsIcon(), contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent
                                )
                            )
                        }
                    }
                },
                contentWindowInsets = if (!windowSizeClass.shouldUseSplitLayout) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // [Refactor] Lift background to root
                    ProfileBackground(
                        user = currentUiState.user,
                        viewModel = viewModel,
                        deferImmersiveRenderBudget = deferImmersiveRenderBudget
                    )
                    
                    ProfileSpaceContent(
                        viewModel = viewModel,
                        user = currentUiState.user,
                        space = currentUiState.space,
                        editableAccount = currentUiState.editableAccount,
                        favoriteFolderShortcuts = favoriteFolderShortcuts,
                        onTabSelected = viewModel::selectProfileSpaceTab,
                        onSignSave = viewModel::updateProfileSign,
                        onLogout = {
                            viewModel.logout()
                            onLogoutSuccess()
                        },
                        onAccountManageClick = { showAccountSwitchDialog = true },
                        onHistoryClick = onHistoryClick,
                        showHistoryService = showHistoryService,
                        onFavoriteClick = onFavoriteClick,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        onFollowingClick = { onFollowingClick(currentUiState.user.mid) },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onVideoClick = onVideoClick,
                        onDynamicDeleteClick = { action ->
                            viewModel.deleteProfileDynamic(action) { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBangumiClick = onBangumiClick,
                        onBangumiMoreClick = onBangumiMoreClick,
                        scrollBehavior = scrollBehavior,
                        onBack = onBack,
                        onSettingsClick = onSettingsClick,
                        hazeState = hazeState,
                        paddingValues = padding,
                        isTablet = windowSizeClass.shouldUseSplitLayout
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ProfileBackground(
    user: UserState,
    viewModel: ProfileViewModel,
    deferImmersiveRenderBudget: Boolean
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.shouldUseSplitLayout
    val hasWallpaper = user.topPhoto.isNotEmpty()
    val bgTransform by viewModel.getProfileBgTransform(isTablet).collectAsStateWithLifecycle(initialValue = ProfileWallpaperTransform())
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() < 0.5f
    val heroHeight = resolveProfileHeroHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        widthSizeClass = windowSizeClass.widthSizeClass
    ).dp
    val wallpaperDecodeSize = remember(configuration.screenWidthDp, density.density) {
        resolveProfileWallpaperDecodeSizePx(
            screenWidthDp = configuration.screenWidthDp,
            density = density.density
        )
    }
    val heroChrome = remember(hasWallpaper, isDarkTheme, colorScheme.onSurface, colorScheme.onSurfaceVariant) {
        resolveProfileHeroChrome(
            hasWallpaper = hasWallpaper,
            isDarkTheme = isDarkTheme,
            onSurfaceColor = colorScheme.onSurface,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant
        )
    }
    val heroFallbackGradient = remember(hasWallpaper, isDarkTheme, colorScheme.surface, colorScheme.surfaceVariant, colorScheme.primaryContainer) {
        resolveProfileHeroFallbackGradient(
            hasWallpaper = hasWallpaper,
            isDarkTheme = isDarkTheme,
            surfaceColor = colorScheme.surface,
            surfaceVariantColor = colorScheme.surfaceVariant,
            primaryContainerColor = colorScheme.primaryContainer
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .align(Alignment.TopCenter)
    ) {
        if (shouldRenderProfileImmersiveBackground(hasWallpaper, deferImmersiveRenderBudget)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(user.topPhoto)
                    .size(wallpaperDecodeSize.first, wallpaperDecodeSize.second)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(
                    bgTransform.offsetX,
                    bgTransform.offsetY
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = bgTransform.scale,
                        scaleY = bgTransform.scale
                    )
            )
        } else if (heroFallbackGradient != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                heroFallbackGradient.topColor,
                                heroFallbackGradient.bottomColor
                            )
                        )
                    )
            )
        }

        if (heroChrome.scrimBottomAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = heroChrome.scrimTopAlpha),
                                Color.Black.copy(alpha = heroChrome.scrimBottomAlpha)
                            )
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSpaceContent(
    viewModel: ProfileViewModel,
    user: UserState,
    space: ProfileSpaceUiState,
    editableAccount: ProfileEditableAccountState,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onTabSelected: (ProfileSpaceMainTab) -> Unit,
    onSignSave: (String) -> Unit,
    onLogout: () -> Unit,
    onAccountManageClick: () -> Unit,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onFollowingClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onDynamicDeleteClick: (DynamicDeleteAction) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onBangumiMoreClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    hazeState: HazeState?,
    paddingValues: PaddingValues,
    isTablet: Boolean
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showAdjustmentSheet by remember { mutableStateOf(false) }
    var tempSelectedUri by remember { mutableStateOf<Uri?>(null) }
    val customBackgroundUri by viewModel.getProfileBgUri().collectAsStateWithLifecycle(initialValue = null
        )
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            tempSelectedUri = uri
            showAdjustmentSheet = true
        }
    }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showWallpaperActionSheet by remember { mutableStateOf(false) }

    if (showEditDialog) {
        ProfileEditAccountDialog(
            state = editableAccount,
            isSaving = space.isSavingSign,
            onDismiss = { showEditDialog = false },
            onSaveSign = onSignSave
        )
    }
    if (showAdjustmentSheet && tempSelectedUri != null) {
        ProfileWallpaperAdjustmentSheet(
            imageUri = tempSelectedUri.toString(),
            initialMobileTransform = ProfileWallpaperTransform(),
            initialTabletTransform = ProfileWallpaperTransform(),
            onDismiss = { showAdjustmentSheet = false },
            onSave = { mobileTransform, tabletTransform ->
                showAdjustmentSheet = false
                tempSelectedUri?.let { uri ->
                    viewModel.updateCustomBackground(uri, mobileTransform, tabletTransform)
                }
            }
        )
    }
    if (showWallpaperSheet) {
        OfficialWallpaperSheet(viewModel = viewModel, onDismiss = { showWallpaperSheet = false })
    }
    if (showWallpaperActionSheet) {
        ProfileWallpaperActionSheet(
            onDismiss = { showWallpaperActionSheet = false },
            onOfficialWallpaperClick = {
                showWallpaperActionSheet = false
                showWallpaperSheet = true
            },
            onLocalAlbumClick = {
                showWallpaperActionSheet = false
                showPhotoPickerDialog = true
            },
            onResetWallpaperClick = {
                showWallpaperActionSheet = false
                viewModel.clearCustomBackground()
            },
            isResetEnabled = !customBackgroundUri.isNullOrEmpty()
        )
    }
    if (showPhotoPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerDialog = false },
            title = { Text("选择照片", fontWeight = FontWeight.Bold) },
            text = { Text("将打开系统相册选择一张照片作为背景。\n\n仅获取选中照片的访问权限，不会访问其他照片。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPhotoPickerDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("选择照片")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoPickerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    val hasWallpaper = user.topPhoto.isNotEmpty()
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() < 0.5f
    val layoutTokens = remember { resolveProfileLayoutTokens() }
    val contentChrome = remember(colorScheme, isDarkTheme) {
        resolveProfileContentChrome(
            surfaceColor = colorScheme.surface,
            onSurfaceColor = colorScheme.onSurface,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant,
            primaryColor = colorScheme.primary,
            surfaceContainerLowColor = colorScheme.surfaceContainerLow,
            surfaceContainerHighColor = colorScheme.surfaceContainerHigh,
            surfaceContainerHighestColor = colorScheme.surfaceContainerHighest,
            outlineVariantColor = colorScheme.outlineVariant,
            isDarkTheme = isDarkTheme
        )
    }
    val heroChrome = remember(hasWallpaper, isDarkTheme, colorScheme.onSurface, colorScheme.onSurfaceVariant) {
        resolveProfileHeroChrome(
            hasWallpaper = hasWallpaper,
            isDarkTheme = isDarkTheme,
            onSurfaceColor = colorScheme.onSurface,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSourceCompat(hazeState) else Modifier)
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileSpaceHeader(
                        user = user,
                        editableAccount = editableAccount,
                        compact = true,
                        heroChrome = heroChrome,
                        showWallpaperAction = true,
                        onEditClick = { showEditDialog = true },
                        onWallpaperActionClick = { showWallpaperActionSheet = true },
                        onFollowingClick = onFollowingClick
                    )
                    ProfileSpaceServices(
                        favoriteFolderShortcuts = favoriteFolderShortcuts,
                        onHistoryClick = onHistoryClick,
                        showHistoryService = showHistoryService,
                        onFavoriteClick = onFavoriteClick,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onAccountManageClick = onAccountManageClick,
                        onLogout = onLogout,
                        contentChrome = contentChrome
                    )
                }
                ProfileSpaceFeedColumn(
                    user = user,
                    space = space,
                    showServicesInHome = false,
                    favoriteFolderShortcuts = favoriteFolderShortcuts,
                    onTabSelected = onTabSelected,
                    onFavoriteClick = onFavoriteClick,
                    onFavoriteFolderClick = onFavoriteFolderClick,
                    onBangumiClick = onBangumiClick,
                    onBangumiMoreClick = onBangumiMoreClick,
                    onVideoClick = onVideoClick,
                    onHistoryClick = onHistoryClick,
                    showHistoryService = showHistoryService,
                    onDownloadClick = onDownloadClick,
                    onWatchLaterClick = onWatchLaterClick,
                    onInboxClick = onInboxClick,
                    onAccountManageClick = onAccountManageClick,
                    onLogout = onLogout,
                    onDynamicDeleteClick = onDynamicDeleteClick,
                    contentChrome = contentChrome,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 48.dp)
                )
            }
        } else {
            val heroListState = rememberLazyListState()
            val heroFullInvertPullPx = with(LocalDensity.current) {
                ProfileHeroPullFullInvertDp.dp.toPx()
            }
            val heroPullState = rememberProfileHeroPullState(
                listState = heroListState,
                fullInvertPullPx = heroFullInvertPullPx,
                enabled = hasWallpaper
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(heroPullState),
                state = heroListState,
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 120.dp)
            ) {
                item {
                    ProfileSpaceHeroHeader(
                        user = user,
                        editableAccount = editableAccount,
                        heroChrome = heroChrome,
                        onSurfaceColor = colorScheme.onSurface,
                        onSurfaceVariantColor = colorScheme.onSurfaceVariant,
                        pullInvertFractionProvider = { heroPullState.fraction() },
                        layoutTokens = layoutTokens,
                        showWallpaperAction = false,
                        onEditClick = { showEditDialog = true },
                        onWallpaperActionClick = { showWallpaperActionSheet = true },
                        onFollowingClick = onFollowingClick
                    )
                }
                item {
                    ProfileContentSheet(
                        contentChrome = contentChrome,
                        layoutTokens = layoutTokens
                    ) {
                        ProfileSpaceTabs(
                            selectedTab = space.selectedTab,
                            onTabSelected = onTabSelected,
                            contentChrome = contentChrome,
                            embeddedInPanel = true
                        )
                        ProfileSpaceTabBody(
                            user = user,
                            space = space,
                            showServicesInHome = true,
                            favoriteFolderShortcuts = favoriteFolderShortcuts,
                            onFavoriteClick = onFavoriteClick,
                            onFavoriteFolderClick = onFavoriteFolderClick,
                            onBangumiClick = onBangumiClick,
                            onBangumiMoreClick = onBangumiMoreClick,
                            onVideoClick = onVideoClick,
                            onHistoryClick = onHistoryClick,
                            showHistoryService = showHistoryService,
                            onDownloadClick = onDownloadClick,
                            onWatchLaterClick = onWatchLaterClick,
                            onInboxClick = onInboxClick,
                            onAccountManageClick = onAccountManageClick,
                            onLogout = onLogout,
                            onDynamicDeleteClick = onDynamicDeleteClick,
                            contentChrome = contentChrome,
                            embeddedInPanel = true
                        )
                    }
                }
            }
            ProfileHeroTopActionBar(
                onBack = onBack,
                onWallpaperActionClick = { showWallpaperActionSheet = true },
                onSettingsClick = onSettingsClick,
                baseIconColor = heroChrome.textColor,
                statusBarTopPadding = statusBarTopPadding,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun ProfileSpaceFeedColumn(
    user: UserState,
    space: ProfileSpaceUiState,
    showServicesInHome: Boolean,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onTabSelected: (ProfileSpaceMainTab) -> Unit,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onBangumiMoreClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAccountManageClick: () -> Unit,
    onLogout: () -> Unit,
    onDynamicDeleteClick: (DynamicDeleteAction) -> Unit,
    contentChrome: ProfileContentChrome,
    modifier: Modifier,
    contentPadding: PaddingValues
) {
    LazyColumn(modifier = modifier.fillMaxHeight(), contentPadding = contentPadding) {
        item {
            ProfileSpaceTabs(
                selectedTab = space.selectedTab,
                onTabSelected = onTabSelected,
                contentChrome = contentChrome
            )
        }
        item {
            ProfileSpaceTabBody(
                user = user,
                space = space,
                showServicesInHome = showServicesInHome,
                favoriteFolderShortcuts = favoriteFolderShortcuts,
                onFavoriteClick = onFavoriteClick,
                onFavoriteFolderClick = onFavoriteFolderClick,
                onBangumiClick = onBangumiClick,
                onBangumiMoreClick = onBangumiMoreClick,
                onVideoClick = onVideoClick,
                onHistoryClick = onHistoryClick,
                showHistoryService = showHistoryService,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                onInboxClick = onInboxClick,
                onAccountManageClick = onAccountManageClick,
                onLogout = onLogout,
                onDynamicDeleteClick = onDynamicDeleteClick,
                contentChrome = contentChrome
            )
        }
    }
}

/**
 * Pull distance (dp) at which the hero header text is fully inverted toward
 * onSurface when the user pulls the profile list down past the wallpaper.
 */
private const val ProfileHeroPullFullInvertDp = 64

/**
 * Tracks the pull-down overscroll distance of the profile LazyColumn so the hero
 * header text can dynamically invert color when pulled into the surface area
 * below the wallpaper. Implements [NestedScrollConnection] so it can be attached
 * directly via [Modifier.nestedScroll]. The default stretch overscroll still
 * applies because this connection consumes nothing.
 */
private class ProfileHeroPullState(
    val listState: LazyListState,
    private val pullPx: MutableFloatState,
    private val scope: CoroutineScope,
    private val fullInvertPullPx: Float,
    private val enabled: Boolean
) : NestedScrollConnection {
    private var settleJob: Job? = null

    fun fraction(): Float {
        if (!enabled || fullInvertPullPx <= 0f) return 0f
        return resolveProfileHeroPullInvertFraction(
            pullPx = pullPx.floatValue,
            fullInvertPullPx = fullInvertPullPx
        )
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || source != NestedScrollSource.Drag) return Offset.Zero
        val atTop = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        val dy = available.y
        if (atTop) {
            // Pull-down at top is a positive delta (finger moves down). Pulling
            // back up (negative) releases the accumulated overscroll.
            if (dy > 0f || (dy < 0f && pullPx.floatValue > 0f)) {
                settleJob?.cancel()
                pullPx.floatValue = (pullPx.floatValue + dy).coerceAtLeast(0f)
            }
        } else if (pullPx.floatValue > 0f) {
            settleJob?.cancel()
            pullPx.floatValue = 0f
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(velocity: Velocity): Velocity {
        if (!enabled) return Velocity.Zero
        val current = pullPx.floatValue
        if (current <= 0f) return Velocity.Zero
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = current,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            ) { value, _ ->
                pullPx.floatValue = value
            }
        }
        return Velocity.Zero
    }
}

@Composable
private fun rememberProfileHeroPullState(
    listState: LazyListState,
    fullInvertPullPx: Float,
    enabled: Boolean
): ProfileHeroPullState {
    val pullPx = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    return remember(listState, scope, fullInvertPullPx, enabled) {
        ProfileHeroPullState(listState, pullPx, scope, fullInvertPullPx, enabled)
    }
}

/**
 * 顶部常驻操作栏（返回 / 换壁纸 / 设置）。
 * 固定覆盖在壁纸上，图标始终保持英雄区 chrome 颜色以保证深色壁纸可读性。
 */
@Composable
private fun ProfileHeroTopActionBar(
    onBack: () -> Unit,
    onWallpaperActionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    baseIconColor: Color,
    statusBarTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    val iconTint = baseIconColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarTopPadding)
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(rememberAppBackIcon(), contentDescription = "返回", tint = iconTint)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onWallpaperActionClick) {
            Icon(rememberAppPhotoIcon(), contentDescription = "换壁纸", tint = iconTint)
        }
        IconButton(onClick = onSettingsClick) {
            Icon(rememberAppSettingsIcon(), contentDescription = "设置", tint = iconTint)
        }
    }
}

@Composable
private fun ProfileSpaceHeroHeader(
    user: UserState,
    editableAccount: ProfileEditableAccountState,
    heroChrome: ProfileHeroChrome,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    pullInvertFractionProvider: () -> Float,
    layoutTokens: ProfileLayoutTokens,
    showWallpaperAction: Boolean,
    onEditClick: () -> Unit,
    onWallpaperActionClick: () -> Unit,
    onFollowingClick: () -> Unit
) {
    // Read pull fraction here (not in the parent) so only this header item
    // recomposes while the user pulls down, keeping the LazyColumn cheap.
    val effectiveHeroChrome = resolveProfileHeroInvertedChrome(
        heroChrome = heroChrome,
        onSurfaceColor = onSurfaceColor,
        onSurfaceVariantColor = onSurfaceVariantColor,
        invertFraction = pullInvertFractionProvider()
    )
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    val heroHeight = resolveProfileHeroHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        widthSizeClass = windowSizeClass.widthSizeClass
    ).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        ProfileSpaceHeader(
            user = user,
            editableAccount = editableAccount,
            compact = false,
            heroChrome = effectiveHeroChrome,
            showWallpaperAction = showWallpaperAction,
            onEditClick = onEditClick,
            onWallpaperActionClick = onWallpaperActionClick,
            onFollowingClick = onFollowingClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    top = 112.dp,
                    bottom = layoutTokens.heroBottomInsetDp.dp
                )
        )
    }
}

@Composable
private fun ProfileContentSheet(
    contentChrome: ProfileContentChrome,
    layoutTokens: ProfileLayoutTokens,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(layoutTokens.contentSheetTopRadiusDp) {
        RoundedCornerShape(
            topStart = layoutTokens.contentSheetTopRadiusDp.dp,
            topEnd = layoutTokens.contentSheetTopRadiusDp.dp
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-layoutTokens.contentSheetTopOverlapDp).dp),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, contentChrome.sheetBorderColor),
        shadowElevation = contentChrome.sheetShadowElevationDp.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            contentChrome.sheetGradientTopColor,
                            contentChrome.sheetGradientBottomColor
                        ),
                        startY = 0f,
                        endY = 360f
                    ),
                    shape = shape
                )
                .padding(
                    top = layoutTokens.contentSheetTopPaddingDp.dp,
                    bottom = layoutTokens.contentSheetBottomPaddingDp.dp
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileSpaceHeader(
    user: UserState,
    editableAccount: ProfileEditableAccountState,
    compact: Boolean,
    heroChrome: ProfileHeroChrome,
    showWallpaperAction: Boolean,
    onEditClick: () -> Unit,
    onWallpaperActionClick: () -> Unit,
    onFollowingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = heroChrome.textColor
    val secondaryColor = heroChrome.secondaryTextColor
    val meta = remember(editableAccount.sign, editableAccount.ipLocation, editableAccount.sex) {
        resolveProfileSpaceIdentityMeta(
            sign = editableAccount.sign,
            ipLocation = editableAccount.ipLocation,
            sex = editableAccount.sex
        )
    }
    val metaChipContainer = heroChrome.metaChipContainerColor
    val metaChipBorder = heroChrome.metaChipBorderColor
    var identityExpanded by remember(user.mid, editableAccount.sign, editableAccount.ipLocation, editableAccount.sex) {
        mutableStateOf(false)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = user.face,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (compact) 72.dp else 80.dp)
                    .clip(CircleShape)
                    .border(2.dp, heroChrome.avatarBorderColor, CircleShape)
            )
            Spacer(modifier = Modifier.weight(1f))
            ProfileSpaceStat("粉丝", user.follower, textColor)
            ProfileSpaceStat("关注", user.following, textColor, onClick = onFollowingClick)
            ProfileSpaceStat("获赞", user.dynamic, textColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                UserLevelBadge(level = user.level)
                if (user.isVip) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = user.vipLabel.ifBlank { "大会员" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(com.android.purebilibili.core.theme.iOSPink)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(
                onClick = { identityExpanded = !identityExpanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (identityExpanded) rememberAppChevronUpIcon() else rememberAppChevronDownIcon(),
                    contentDescription = if (identityExpanded) "收起个人资料" else "展开个人资料",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        ProfileIdentityDrawer(
            meta = meta,
            expanded = identityExpanded,
            contentColor = textColor,
            secondaryColor = secondaryColor,
            containerColor = metaChipContainer,
            borderColor = metaChipBorder
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = heroChrome.actionButtonContentColor),
                border = BorderStroke(1.dp, heroChrome.actionButtonContentColor.copy(alpha = heroChrome.actionButtonBorderAlpha)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("编辑资料")
            }
            if (showWallpaperAction) {
                ProfileWallpaperMenuButton(
                    contentColor = heroChrome.actionButtonContentColor,
                    borderColor = heroChrome.actionButtonContentColor.copy(alpha = heroChrome.actionButtonBorderAlpha),
                    onClick = onWallpaperActionClick
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentityDrawer(
    meta: ProfileSpaceIdentityMeta,
    expanded: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    containerColor: Color,
    borderColor: Color
) {
    val shape = RoundedCornerShape(16.dp)
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            shape = shape,
            color = containerColor,
            border = BorderStroke(0.6.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = meta.signText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (meta.signPlaceholder) secondaryColor else contentColor.copy(alpha = 0.86f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileSpaceMetaChip(
                        text = meta.ipText.orEmpty(),
                        contentColor = contentColor.copy(alpha = 0.78f),
                        containerColor = Color.Transparent,
                        borderColor = borderColor
                    )
                    meta.sexText?.let { sexText ->
                        ProfileSpaceMetaChip(
                            text = sexText,
                            contentColor = contentColor.copy(alpha = 0.78f),
                            containerColor = Color.Transparent,
                            borderColor = borderColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSpaceMetaChip(
    text: String,
    contentColor: Color,
    containerColor: Color,
    borderColor: Color
) {
    if (text.isBlank()) return
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileSpaceStat(label: String, value: Int, color: Color, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = FormatUtils.formatStat(value.toLong()),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun ProfileSpaceTabs(
    selectedTab: ProfileSpaceMainTab,
    onTabSelected: (ProfileSpaceMainTab) -> Unit,
    contentChrome: ProfileContentChrome,
    embeddedInPanel: Boolean = false
) {
    val tabs = remember { defaultProfileSpaceTabs() }
    val context = LocalContext.current
    val layoutTokens = remember { resolveProfileLayoutTokens() }
    val chromeSpec = remember { resolveProfileSpaceTabChromeSpec() }
    val rowContainerShape = remember(chromeSpec.rowCornerRadiusDp) {
        RoundedCornerShape(chromeSpec.rowCornerRadiusDp.dp)
    }
    val bottomBarLiquidGlassEnabled by SettingsManager
        .getBottomBarLiquidGlassEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    val tabModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = chromeSpec.rowHorizontalInsetDp.dp)
    val useUnderlineTabs = embeddedInPanel || !bottomBarLiquidGlassEnabled
    if (!useUnderlineTabs) {
        BottomBarLiquidSegmentedControl(
            items = tabs.map { it.title },
            selectedIndex = selectedIndex,
            onSelected = { index -> tabs.getOrNull(index)?.let { onTabSelected(it.tab) } },
            modifier = tabModifier
                .padding(vertical = 6.dp)
                .background(contentChrome.cardContainerColor, rowContainerShape)
                .padding(horizontal = chromeSpec.controlHorizontalInsetDp.dp, vertical = 8.dp),
            height = 46.dp,
            indicatorHeight = 40.dp,
            labelFontSize = 16.sp,
            forceLiquidChrome = false,
            containerColorOverride = contentChrome.surfaceColor,
            selectedTextColorOverride = contentChrome.onSurfaceColor,
            unselectedTextColorOverride = contentChrome.onSurfaceVariantColor,
            indicatorIdleSurfaceColorOverride = contentChrome.primaryColor.copy(alpha = 0.14f)
        )
        return
    }

    Row(
        modifier = tabModifier
            .height(layoutTokens.tabHeightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { item ->
            val selected = item.tab == selectedTab
            Column(
                modifier = Modifier
                    .clickable { onTabSelected(item.tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) contentChrome.onSurfaceColor else contentChrome.onSurfaceVariantColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) contentChrome.primaryColor else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ProfileSpaceTabBody(
    user: UserState,
    space: ProfileSpaceUiState,
    showServicesInHome: Boolean,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onBangumiMoreClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAccountManageClick: () -> Unit,
    onLogout: () -> Unit,
    onDynamicDeleteClick: (DynamicDeleteAction) -> Unit,
    contentChrome: ProfileContentChrome,
    embeddedInPanel: Boolean = false
) {
    when (space.selectedTab) {
        ProfileSpaceMainTab.HOME -> ProfileSpaceHome(
            user = user,
            space = space,
            showServices = showServicesInHome,
            favoriteFolderShortcuts = favoriteFolderShortcuts,
            onFavoriteClick = onFavoriteClick,
            onFavoriteFolderClick = onFavoriteFolderClick,
            onBangumiClick = onBangumiClick,
            onBangumiMoreClick = onBangumiMoreClick,
            onVideoClick = onVideoClick,
            onHistoryClick = onHistoryClick,
            showHistoryService = showHistoryService,
            onDownloadClick = onDownloadClick,
            onWatchLaterClick = onWatchLaterClick,
            onInboxClick = onInboxClick,
            onAccountManageClick = onAccountManageClick,
            onLogout = onLogout,
            contentChrome = contentChrome,
            embeddedInPanel = embeddedInPanel
        )
        ProfileSpaceMainTab.DYNAMIC -> ProfileDynamicList(
            items = space.dynamicItems,
            onVideoClick = onVideoClick,
            onDeleteClick = onDynamicDeleteClick
        )
        ProfileSpaceMainTab.CONTRIBUTION -> ProfileVideoList(space.contributionVideos, onVideoClick)
        ProfileSpaceMainTab.FAVORITE -> ProfileFavoriteFolderList(user.mid, space.favoriteFolders, onFavoriteFolderClick)
        ProfileSpaceMainTab.BANGUMI -> ProfileBangumiList(space.bangumiItems, onBangumiClick)
    }
}

@Composable
private fun ProfileSpaceHome(
    user: UserState,
    space: ProfileSpaceUiState,
    showServices: Boolean,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onBangumiMoreClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAccountManageClick: () -> Unit,
    onLogout: () -> Unit,
    contentChrome: ProfileContentChrome,
    embeddedInPanel: Boolean = false
) {
    val layoutTokens = remember { resolveProfileLayoutTokens() }
    Column(
        modifier = Modifier.padding(
            top = if (embeddedInPanel) 6.dp else 10.dp,
            bottom = if (embeddedInPanel) 0.dp else 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(layoutTokens.sectionSpacingDp.dp)
    ) {
        resolveProfileSpaceHomeSections(
            favoriteFolders = space.favoriteFolders,
            bangumiItems = space.bangumiItems,
            coinVideos = space.coinVideos,
            likeVideos = space.likeVideos,
            contributionVideos = space.contributionVideos
        ).forEach { section ->
            when (section) {
                ProfileSpaceHomeSection.FAVORITES -> ProfileFavoriteFolderStrip(
                    ownerMid = user.mid,
                    folders = space.favoriteFolders,
                    count = space.favoriteFolderCount,
                    onMoreClick = onFavoriteClick,
                    onFolderClick = onFavoriteFolderClick,
                    contentChrome = contentChrome
                )
                ProfileSpaceHomeSection.BANGUMI -> ProfileBangumiStrip(
                    items = space.bangumiItems,
                    count = space.bangumiCount,
                    onMoreClick = onBangumiMoreClick,
                    onBangumiClick = onBangumiClick,
                    contentChrome = contentChrome
                )
                ProfileSpaceHomeSection.COIN_VIDEOS -> ProfileAggregateVideoStrip(
                    title = "最近投币的视频",
                    count = space.coinVideoCount,
                    videos = space.coinVideos,
                    onVideoClick = onVideoClick,
                    contentChrome = contentChrome
                )
                ProfileSpaceHomeSection.LIKE_VIDEOS -> ProfileAggregateVideoStrip(
                    title = "最近点赞的视频",
                    count = space.likeVideoCount,
                    videos = space.likeVideos,
                    onVideoClick = onVideoClick,
                    contentChrome = contentChrome
                )
                ProfileSpaceHomeSection.CONTRIBUTIONS -> ProfileVideoStrip(
                    title = "投稿预览",
                    count = space.contributionVideoCount,
                    videos = space.contributionVideos,
                    onVideoClick = onVideoClick,
                    contentChrome = contentChrome
                )
                ProfileSpaceHomeSection.SERVICES -> if (showServices) {
                    ProfileSpaceServices(
                        favoriteFolderShortcuts = favoriteFolderShortcuts,
                        onHistoryClick = onHistoryClick,
                        showHistoryService = showHistoryService,
                        onFavoriteClick = onFavoriteClick,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onAccountManageClick = onAccountManageClick,
                        onLogout = onLogout,
                        contentChrome = contentChrome,
                        embeddedInPanel = embeddedInPanel
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSpaceServices(
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAccountManageClick: () -> Unit,
    onLogout: () -> Unit,
    contentChrome: ProfileContentChrome,
    embeddedInPanel: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "我的服务",
            style = MaterialTheme.typography.titleMedium,
            color = contentChrome.onSurfaceColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        ServicesSection(
            onHistoryClick = onHistoryClick,
            showHistoryService = showHistoryService,
            onFavoriteClick = onFavoriteClick,
            favoriteFolderShortcuts = favoriteFolderShortcuts,
            onFavoriteFolderClick = onFavoriteFolderClick,
            showFavoriteService = false,
            onDownloadClick = onDownloadClick,
            onWatchLaterClick = onWatchLaterClick,
            onInboxClick = onInboxClick,
            onAccountManageClick = onAccountManageClick,
            onLogout = onLogout,
            containerColor = contentChrome.surfaceColor,
            contentColor = contentChrome.onSurfaceColor,
            borderColor = null,
            embeddedInPanel = embeddedInPanel,
            isLogin = true
        )
    }
}

@Composable
private fun ProfileFavoriteFolderStrip(
    ownerMid: Long,
    folders: List<FavFolder>,
    count: Int,
    onMoreClick: () -> Unit,
    onFolderClick: (Long, Long, String) -> Unit,
    contentChrome: ProfileContentChrome
) {
    ProfileSpaceSection(
        title = "收藏",
        count = count,
        onMoreClick = onMoreClick,
        textColor = contentChrome.onSurfaceColor
    ) {
        folders.take(6).forEach { folder ->
            ProfileSpacePosterCard(
                title = folder.title,
                subtitle = "${folder.media_count} 个内容",
                imageUrl = folder.cover,
                contentChrome = contentChrome,
                onClick = { onFolderClick(folder.id, ownerMid, folder.title) }
            )
        }
    }
}

@Composable
private fun ProfileBangumiStrip(
    items: List<FollowBangumiItem>,
    count: Int,
    onMoreClick: () -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    contentChrome: ProfileContentChrome
) {
    ProfileSpaceSection(
        title = "追番",
        count = count,
        onMoreClick = onMoreClick,
        textColor = contentChrome.onSurfaceColor
    ) {
        items.take(8).forEach { item ->
            ProfileSpacePosterCard(
                title = item.title,
                subtitle = item.progress.ifBlank { item.newEp?.indexShow.orEmpty() },
                imageUrl = item.cover,
                contentChrome = contentChrome,
                onClick = { onBangumiClick(item.seasonId, item.firstEp) }
            )
        }
    }
}

@Composable
private fun ProfileAggregateVideoStrip(
    title: String,
    count: Int,
    videos: List<SpaceAggregateArchiveItem>,
    onVideoClick: (String) -> Unit,
    contentChrome: ProfileContentChrome
) {
    ProfileSpaceSection(
        title = title,
        count = count,
        onMoreClick = {},
        textColor = contentChrome.onSurfaceColor
    ) {
        videos.take(8).forEach { video ->
            ProfileSpacePosterCard(
                title = video.title,
                subtitle = video.length,
                imageUrl = video.cover,
                contentChrome = contentChrome,
                onClick = { video.bvid.takeIf { it.isNotBlank() }?.let(onVideoClick) }
            )
        }
    }
}

@Composable
private fun ProfileVideoStrip(
    title: String,
    count: Int,
    videos: List<SpaceVideoItem>,
    onVideoClick: (String) -> Unit,
    contentChrome: ProfileContentChrome
) {
    ProfileSpaceSection(
        title = title,
        count = count,
        onMoreClick = {},
        textColor = contentChrome.onSurfaceColor
    ) {
        videos.take(8).forEach { video ->
            ProfileSpacePosterCard(
                title = video.title,
                subtitle = video.length,
                imageUrl = video.pic,
                contentChrome = contentChrome,
                onClick = { video.bvid.takeIf { it.isNotBlank() }?.let(onVideoClick) }
            )
        }
    }
}

@Composable
private fun ProfileSpaceSection(
    title: String,
    count: Int,
    onMoreClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (count > 0) "$title  ${FormatUtils.formatStat(count.toLong())}" else title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMoreClick) {
                Text(
                    text = "查看更多",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileSpacePosterCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    contentChrome: ProfileContentChrome,
    onClick: () -> Unit
) {
    val cardTokens = remember { resolveProfileCardTokens() }
    val cardShape = RoundedCornerShape(cardTokens.cornerRadiusDp.dp)
    val cardWidth = cardTokens.widthDp.dp
    val coverHeight = resolveProfileCardCoverHeightDp(cardTokens).dp
    val cardHeight = resolveProfileCardHeightDp(cardTokens).dp
    Surface(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        color = contentChrome.cardMetadataColor,
        border = BorderStroke(0.5.dp, contentChrome.cardBorderColor),
        shadowElevation = contentChrome.cardShadowElevationDp.dp,
        tonalElevation = 0.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight)
                    .background(contentChrome.cardContainerColor)
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        rememberAppFolderIcon(),
                        contentDescription = null,
                        tint = contentChrome.onSurfaceVariantColor.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardTokens.metadataHeightDp.dp)
                    .background(contentChrome.cardMetadataColor)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentChrome.onSurfaceColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle.ifBlank { "公开" },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentChrome.onSurfaceVariantColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileFavoriteFolderList(ownerMid: Long, folders: List<FavFolder>, onFolderClick: (Long, Long, String) -> Unit) {
    if (folders.isEmpty()) {
        ProfileSpaceEmpty("暂无公开收藏夹")
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        folders.forEach { folder ->
            ProfileSpaceListRow(
                title = folder.title,
                subtitle = "${folder.media_count} 个内容",
                imageUrl = folder.cover,
                onClick = { onFolderClick(folder.id, ownerMid, folder.title) }
            )
        }
    }
}

@Composable
private fun ProfileBangumiList(items: List<FollowBangumiItem>, onBangumiClick: (Long, Long) -> Unit) {
    if (items.isEmpty()) {
        ProfileSpaceEmpty("暂无追番")
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            ProfileSpaceListRow(
                title = item.title,
                subtitle = item.progress.ifBlank { item.newEp?.indexShow.orEmpty() },
                imageUrl = item.cover,
                onClick = { onBangumiClick(item.seasonId, item.firstEp) }
            )
        }
    }
}

@Composable
private fun ProfileVideoList(videos: List<SpaceVideoItem>, onVideoClick: (String) -> Unit) {
    if (videos.isEmpty()) {
        ProfileSpaceEmpty("暂无投稿")
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        videos.forEach { video ->
            ProfileSpaceListRow(
                title = video.title,
                subtitle = "${FormatUtils.formatStat(video.play.toLong())} 播放 · ${video.length}",
                imageUrl = video.pic,
                onClick = { video.bvid.takeIf { it.isNotBlank() }?.let(onVideoClick) }
            )
        }
    }
}

@Composable
private fun ProfileDynamicList(
    items: List<SpaceDynamicItem>,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (DynamicDeleteAction) -> Unit
) {
    if (items.isEmpty()) {
        ProfileSpaceEmpty("暂无动态")
        return
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        items.forEachIndexed { index, item ->
            ProfileDynamicCard(
                item = item,
                onVideoClick = onVideoClick,
                onDeleteClick = onDeleteClick
            )
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    thickness = 0.7.dp
                )
            }
        }
    }
}

@Composable
private fun ProfileDynamicCard(
    item: SpaceDynamicItem,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (DynamicDeleteAction) -> Unit
) {
    val author = item.modules.module_author
    val authorName = resolveProfileDynamicAuthorName(item)
    val publishText = resolveProfileDynamicPublishText(item)
    val bodyText = resolveProfileDynamicText(item)
    val orig = item.orig
    val moreIcon = rememberAppMoreIcon()
    val context = LocalContext.current
    val deleteAction = remember(item) { resolveProfileDynamicDeleteAction(item) }
    var showMoreMenu by remember(item.id_str) { mutableStateOf(false) }
    var pendingDeleteAction by remember(item.id_str) { mutableStateOf<DynamicDeleteAction?>(null) }

    pendingDeleteAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAction = null },
            icon = { Icon(CupertinoIcons.Default.Trash, contentDescription = null) },
            title = { Text(action.title) },
            text = { Text(action.content) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteAction = null
                        onDeleteClick(action)
                    }
                ) {
                    Text(action.confirmText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAction = null }) {
                    Text(action.cancelText)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = author?.face.orEmpty(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (publishText.isNotBlank()) {
                    Text(
                        text = publishText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = moreIcon,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    DropdownMenuItem(
                        text = { Text("复制链接") },
                        leadingIcon = {
                            Icon(
                                CupertinoIcons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            val dynamicUrl = "https://t.bilibili.com/${item.id_str}"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("动态链接", dynamicUrl)
                            )
                            Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                        }
                    )
                    if (deleteAction != null) {
                        DropdownMenuItem(
                            text = { Text(deleteAction.label) },
                            leadingIcon = {
                                Icon(
                                    CupertinoIcons.Default.Trash,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                pendingDeleteAction = deleteAction
                            }
                        )
                    }
                }
            }
        }

        if (bodyText.isNotBlank()) {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (orig != null) {
            ProfileDynamicOriginalContent(item = orig, onVideoClick = onVideoClick)
        } else {
            ProfileDynamicMajorContent(item = item, onVideoClick = onVideoClick)
        }

        ProfileDynamicActionRow(item = item)
    }
}

@Composable
private fun ProfileDynamicOriginalContent(item: SpaceDynamicItem, onVideoClick: (String) -> Unit) {
    val authorName = resolveProfileDynamicAuthorName(item)
    val text = resolveProfileDynamicText(item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (authorName.isNotBlank()) {
                Text(
                    text = "@$authorName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProfileDynamicMajorContent(item = item, onVideoClick = onVideoClick)
        }
    }
}

@Composable
private fun ProfileDynamicMajorContent(item: SpaceDynamicItem, onVideoClick: (String) -> Unit) {
    val dynamic = item.modules.module_dynamic
    val major = dynamic?.major
    val cover = resolveProfileDynamicCover(item)
    val imageUrls = remember(item) { resolveProfileDynamicImageUrls(item) }
    val title = major?.archive?.title
        ?.takeIf { it.isNotBlank() }
        ?: major?.opus?.title?.takeIf { it.isNotBlank() }
        ?: major?.article?.title?.takeIf { it.isNotBlank() }
    val clickableBvid = major?.archive?.bvid?.takeIf { it.isNotBlank() }
    var selectedImageIndex by remember(item.id_str, imageUrls) { mutableIntStateOf(-1) }
    var sourceRect by remember(item.id_str, imageUrls) { mutableStateOf<Rect?>(null) }
    val context = LocalContext.current
    val dynamicPreviewTextVisible by SettingsManager.getDynamicImagePreviewTextVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val previewText = remember(item, title) {
        ImagePreviewTextContent(
            headline = resolveProfileDynamicAuthorName(item),
            body = resolveProfileDynamicText(item).ifBlank { title.orEmpty() }
        )
    }

    if (cover.isBlank() && title.isNullOrBlank()) return

    Column(
        modifier = Modifier.then(
            if (clickableBvid != null) Modifier.clickable { onVideoClick(clickableBvid) } else Modifier
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank() && title != resolveProfileDynamicText(item)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (cover.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cover)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .onGloballyPositioned { coordinates ->
                        sourceRect = coordinates.boundsInWindow()
                    }
                    .then(
                        if (clickableBvid == null && imageUrls.isNotEmpty()) {
                            Modifier.clickable { selectedImageIndex = 0 }
                        } else {
                            Modifier
                        }
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }

    if (selectedImageIndex >= 0 && imageUrls.isNotEmpty()) {
        ImagePreviewDialog(
            images = imageUrls,
            initialIndex = selectedImageIndex.coerceIn(imageUrls.indices),
            sourceRect = sourceRect,
            sourceCornerRadiusDp = 6f,
            textContent = previewText,
            defaultTextVisible = dynamicPreviewTextVisible,
            onDismiss = { selectedImageIndex = -1 }
        )
    }
}

@Composable
private fun ProfileDynamicActionRow(item: SpaceDynamicItem) {
    val stat = item.modules.module_stat
    val shareIcon = rememberAppShareIcon()
    val commentIcon = rememberAppCommentIcon()
    val likeIcon = rememberAppLikeIcon()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileDynamicAction(
            icon = shareIcon,
            text = resolveProfileDynamicActionText("转发", stat?.forward?.count ?: 0)
        )
        ProfileDynamicAction(
            icon = commentIcon,
            text = resolveProfileDynamicActionText("评论", stat?.comment?.count ?: 0)
        )
        ProfileDynamicAction(
            icon = likeIcon,
            text = resolveProfileDynamicActionText("点赞", stat?.like?.count ?: 0)
        )
    }
}

@Composable
private fun ProfileDynamicAction(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileSpaceListRow(title: String, subtitle: String, imageUrl: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        rememberAppFolderIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle.ifBlank { "公开" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileSpaceEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileEditAccountDialog(
    state: ProfileEditableAccountState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveSign: (String) -> Unit
) {
    var sign by remember(state.sign) { mutableStateOf(state.sign) }
    val signError = validateProfileSign(sign)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileReadonlyAccountField("昵称", state.name)
                ProfileReadonlyAccountField("生日", state.birthday.ifBlank { "未展示" })
                ProfileReadonlyAccountField("性别", state.sex.ifBlank { "未展示" })
                OutlinedTextField(
                    value = sign,
                    onValueChange = { sign = it },
                    label = { Text("签名") },
                    minLines = 3,
                    maxLines = 4,
                    isError = signError != null,
                    supportingText = { Text(signError ?: "${sign.length}/70") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSign(sign) },
                enabled = !isSaving && signError == null
            ) {
                Text(if (isSaving) "保存中" else "保存签名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ProfileReadonlyAccountField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}


@Composable
fun TabletProfileContent(
    user: UserState,
    onLogout: () -> Unit,
    onAccountManageClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut> = emptyList(),
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    onFollowingClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit = {},
    paddingValues: PaddingValues
) {
    AdaptiveSplitLayout(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        primaryRatio = 0.4f,
        primaryContent = {
            // Left Pane: User Info & Stats & VIP
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent) // [Modified] Transparent for immersive bg
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserInfoSection(user, centered = true)
                Spacer(modifier = Modifier.height(24.dp))
                UserStatsSection(user, onFollowingClick)
                Spacer(modifier = Modifier.height(24.dp))
                VipBannerSection(user)
                Spacer(modifier = Modifier.weight(1f))
            }
        },
        secondaryContent = {
            // Right Pane: Services
            // [Modified] Glassy Background for Readability
            // Detect theme via MaterialTheme properties
            val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val glassContainerColor = if (isDarkTheme) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f)
            val glassBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f)
            val contentColor = if (isDarkTheme) Color.White else Color.Black

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp) // Outer padding
                    .clip(RoundedCornerShape(32.dp))
                    .background(glassContainerColor)
                    .border(1.dp, glassBorderColor, RoundedCornerShape(32.dp))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "我的服务",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // [Modified] Use new grid layout
                    ServicesSection(
                        onHistoryClick = onHistoryClick, 
                        showHistoryService = showHistoryService,
                        onFavoriteClick = onFavoriteClick, 
                        favoriteFolderShortcuts = favoriteFolderShortcuts,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        onDownloadClick = onDownloadClick, 
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onAccountManageClick = onAccountManageClick,
                        isTablet = true, // Force tablet mode
                        containerColor = Color.Transparent, // Grid items handle bg
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onAccountManageClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("切换账号")
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("退出登录")
                        }
                    }
                }
            }
        }
    )
}

// Imports moved to top

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProfileContent(
    viewModel: ProfileViewModel = viewModel(),
    user: UserState,
    onLogout: () -> Unit,
    onAccountManageClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut> = emptyList(),
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    onFollowingClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit = {},  //  [新增] 私信入口
    onVideoClick: (String) -> Unit = {},  // [新增] 三连彩蛋跳转
    // [New] Params
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    hazeState: HazeState? = null,
    onHeaderClick: () -> Unit = {}, // [New] Support header click for guest login
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val windowSizeClass = LocalWindowSizeClass.current
    
    // 📸 图片选择器
    // [New] Adjustment Sheet State
    var showAdjustmentSheet by remember { mutableStateOf(false) }
    var tempSelectedUri by remember { mutableStateOf<Uri?>(null) }
    val customBackgroundUri by viewModel.getProfileBgUri().collectAsStateWithLifecycle(initialValue = null
        )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // [Modified] Don't save immediately, show adjustment sheet
            tempSelectedUri = uri
            showAdjustmentSheet = true
        }
    }
    
    // [New] Adjustment Sheet
    if (showAdjustmentSheet && tempSelectedUri != null) {
        ProfileWallpaperAdjustmentSheet(
            imageUri = tempSelectedUri.toString(),
            initialMobileTransform = ProfileWallpaperTransform(),
            initialTabletTransform = ProfileWallpaperTransform(),
            onDismiss = { showAdjustmentSheet = false },
            onSave = { mobileTransform, tabletTransform ->
                showAdjustmentSheet = false
                tempSelectedUri?.let { uri ->
                    viewModel.updateCustomBackground(uri, mobileTransform, tabletTransform)
                }
            }
        )
    }
    
    // [New] State for Official Wallpaper Sheet
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showWallpaperActionSheet by remember { mutableStateOf(false) }
    
    // [New] Sheet
    if (showWallpaperSheet) {
        OfficialWallpaperSheet(viewModel = viewModel, onDismiss = { showWallpaperSheet = false })
    }
    if (showWallpaperActionSheet) {
        ProfileWallpaperActionSheet(
            onDismiss = { showWallpaperActionSheet = false },
            onOfficialWallpaperClick = {
                showWallpaperActionSheet = false
                showWallpaperSheet = true
            },
            onLocalAlbumClick = {
                showWallpaperActionSheet = false
                showPhotoPickerDialog = true
            },
            onResetWallpaperClick = {
                showWallpaperActionSheet = false
                viewModel.clearCustomBackground()
            },
            isResetEnabled = !customBackgroundUri.isNullOrEmpty()
        )
    }

    if (showPhotoPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerDialog = false },
            icon = {
                Icon(
                    rememberAppPhotoIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("选择照片", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "将打开系统相册选择一张照片作为背景。\n\n" +
                        "📸 仅获取您选中照片的访问权限\n" +
                        "🔒 不会访问您的其他照片",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPhotoPickerDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("选择照片")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoPickerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    val isImmersive = user.topPhoto.isNotEmpty()
    val contentColor = if (isImmersive) Color.White else MaterialTheme.colorScheme.onSurface
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // [Modified] Background logic moved to ProfileBackground()
        // No need to duplicate here, but MobileProfileContent is called separately in Split Layout?
        // Ah, MobileProfileContent is independent. Let's keep it simple: 
        // Remove background rendering from here since it's now at root?
        // NO, MobileProfileContent is used in the "else" branch of Scaffold content.
        // If we move background to root, it covers everything.
        // So we should REMOVE the background logic from here.
        
        // However, ProfileBackground checks user.topPhoto.
        // MobileProfileContent already has `isImmersive`.
        // Let's remove the background rendering here.
        // BUT wait, MobileProfileContent is used in LoggedOut state too, where ProfileScreen wrapper might not have user info?
        // LoggedOut wrapper passes guestUser to MobileProfileContent.
        // So ProfileBackground at root should prefer `currentUiState.user` or fallback?
        // ProfileUiState.LoggedOut also has topPhoto. 
        // The root Scaffold logic handles Success state. 
        // LoggedOut state uses MobileProfileContent directly without Scaffold in "when (currentUiState)".
        // So for LoggedOut, we still need background here, OR wrap LoggedOut in ProfileBackground too.
        
        // Let's keep duplicate logic for now (safest) OR refactor LoggedOut to use ProfileBackground?
        // Refactoring LoggedOut is better but complex.
        // Let's simply hide the background here IF the parent is already rendering it?
        // No, ProfileScreen calls MobileProfileContent only when !shouldUseSplitLayout.
        // And we added ProfileBackground in the Success branch main Box.
        // So for Mobile + Success, we have double background if we don't remove it here.
        // YES, remove background here.


        // YES, remove background here.
        
        Box(modifier = Modifier.fillMaxSize()) {
            // 📜 滚动内容
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSourceCompat(hazeState) else Modifier),
            contentPadding = PaddingValues(
                // [Modified] 顶部留白，适配 CenterAlignedTopAppBar (64dp + Status Bar ~ 30-40dp)
                top = 120.dp, 
                bottom = paddingValues.calculateBottomPadding() + 120.dp
            )
        ) {
            item { 
                Column {
                    // [UI优化] 移除背景色，透明显示下方 Header 图
                    UserInfoSection(
                        user = user,
                        transparent = isImmersive,
                        onClick = if (shouldEnableProfileHeaderLoginClick(user.isLogin)) {
                            onHeaderClick
                        } else {
                            {}
                        }
                    )
                }
            }
            if (user.isLogin) {
                item { UserStatsSection(user, onFollowingClick, transparent = isImmersive) }
                // [Modified] 删除 VIP 横幅，改为三连图标入口
                item {
                    ProfileTripleActionEntry(
                        onVipClick = { onVideoClick("BV1GJ411x7h7") },
                        on4KClick = { onVideoClick("BV1JsK5eyEuB") }
                    )
                }
            } else {
                 // [Fix] Guest mode spacer to compensate for missing stats section
                 item { Spacer(modifier = Modifier.height(56.dp)) }
            }

            
            item { 

                // [Adaptive Frost] 自适应磨砂玻璃逻辑
                // [Fix] Detect theme via MaterialTheme properties
                val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                
                // 玻璃颜色：深色模式用黑透，浅色模式用白透
                val glassContainerColor = if (isDarkTheme) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.28f)
                
                // 文字颜色：深色背景用白字，浅色背景用黑字
                val glassContentColor = if (isDarkTheme) Color.White else Color.Black
                
                // 边框颜色：深色用微白边框，浅色用稍明显白边框(增强质感)
                val glassBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f)

                ServicesSection(
                    onHistoryClick = onHistoryClick, 
                    showHistoryService = showHistoryService,
                    onFavoriteClick = onFavoriteClick, 
                    favoriteFolderShortcuts = favoriteFolderShortcuts,
                    onFavoriteFolderClick = onFavoriteFolderClick,
                    onDownloadClick = onDownloadClick, 
                    onWatchLaterClick = onWatchLaterClick,
                    onInboxClick = onInboxClick,  //  [新增]
                    onAccountManageClick = onAccountManageClick,
                    onLogout = onLogout,
                    containerColor = if (isImmersive) glassContainerColor else MaterialTheme.colorScheme.surface,
                    contentColor = if (isImmersive) glassContentColor else MaterialTheme.colorScheme.onSurface,
                    borderColor = if (isImmersive) glassBorderColor else null,
                    isLogin = user.isLogin // [New] Pass login status
                )
                
            }
            // item { Spacer(...) } // Removed
            // item { IOSGroup { ... } } // Removed
        }
        
        AdaptiveTopAppBar(
            title = "我的",
            style = AdaptiveTopAppBarStyle.CENTERED,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(rememberAppBackIcon(), contentDescription = "Back", tint = contentColor)
                }
            },
            actions = {
                IconButton(onClick = { showWallpaperActionSheet = true }) {
                    Icon(rememberAppPhotoIcon(), contentDescription = "背景装扮", tint = contentColor)
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(rememberAppSettingsIcon(), contentDescription = "Settings", tint = contentColor)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = contentColor,
                actionIconContentColor = contentColor,
                navigationIconContentColor = contentColor
            )
        )
    }
}

@Composable
fun GuestProfileContent(
    onGoToLogin: () -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val loginIcon = rememberAppProfileAddIcon()
    val lockIcon = rememberAppLockIcon()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkSurfaceVariant,
                        DarkSurface,
                        DarkBackground
                    )
                )
            )
    ) {
        //  沉浸式顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(rememberAppBackIcon(), contentDescription = "Back", tint = Color.White)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(rememberAppSettingsIcon(), contentDescription = "Settings", tint = Color.White)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo - 使用 3D 蓝色图标
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 16.dp,
                modifier = Modifier.size(100.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(com.android.purebilibili.R.mipmap.ic_launcher_3d)
                        .crossfade(true)
                        .build(),
                    contentDescription = "BiliPai",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "欢迎使用 BiliPai",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "登录后享受完整的 B站 体验",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            //  登录按钮 - 使用现代化渐变按钮
            BiliGradientButton(
                text = "安全登录",
                onClick = onGoToLogin,
                leadingIcon = loginIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 安全提示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(0.5f)
            ) {
                Icon(
                    lockIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "支持扫码登录和网页登录",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun UserInfoSection(
    user: UserState, 
    centered: Boolean = false, 
    transparent: Boolean = false,
    onClick: () -> Unit = {} // [New]
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            //  修复：背景色 (支持透明)
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick) // [New] Make it clickable
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(user.face)).crossfade(true).placeholder(android.R.color.darker_gray).build(),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        if (!centered) {
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                UserInfoText(user, forceWhite = transparent)
            }
        }
    }
    if (centered) {
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UserInfoText(user, centered = true)
        }
    }
}

@Composable
private fun ProfileWallpaperMenuButton(
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = rememberAppPhotoIcon(),
                contentDescription = "背景装扮",
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileWallpaperActionSheet(
    onDismiss: () -> Unit,
    onOfficialWallpaperClick: () -> Unit,
    onLocalAlbumClick: () -> Unit,
    onResetWallpaperClick: () -> Unit,
    isResetEnabled: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "背景装扮",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            ProfileWallpaperSheetActionRow(
                title = "官方壁纸",
                icon = rememberAppPhotoIcon(),
                onClick = onOfficialWallpaperClick
            )
            ProfileWallpaperSheetActionRow(
                title = "本地相册",
                icon = rememberAppFolderIcon(),
                onClick = onLocalAlbumClick
            )
            ProfileWallpaperSheetActionRow(
                title = "恢复默认",
                icon = rememberAppRestoreIcon(),
                enabled = isResetEnabled,
                onClick = onResetWallpaperClick
            )
        }
    }
}

@Composable
private fun ProfileWallpaperSheetActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.54f else 0.30f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.82f else 0.46f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileWallpaperActionCard(
    isImmersive: Boolean,
    hazeState: HazeState? = null,
    onOfficialWallpaperClick: () -> Unit,
    onLocalAlbumClick: () -> Unit,
    onResetWallpaperClick: () -> Unit,
    isResetEnabled: Boolean
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val columnCount = remember(configuration.screenWidthDp) {
        resolveProfileWallpaperActionColumnCount(configuration.screenWidthDp)
    }
    val labelMode = remember(configuration.screenWidthDp, columnCount) {
        resolveProfileWallpaperActionLabelMode(
            screenWidthDp = configuration.screenWidthDp,
            columnCount = columnCount
        )
    }
    val headerBlurEnabled by com.android.purebilibili.core.store.SettingsManager
        .getHeaderBlurEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val bottomBarBlurEnabled by com.android.purebilibili.core.store.SettingsManager
        .getBottomBarBlurEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val blurEnabled = remember(headerBlurEnabled, bottomBarBlurEnabled) {
        resolveProfileWallpaperActionBlurEnabled(
            headerBlurEnabled = headerBlurEnabled,
            bottomBarBlurEnabled = bottomBarBlurEnabled
        )
    }
    val sectionLabelColor = if (isImmersive) {
        Color.White.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val buttonColor = if (isImmersive) {
        if (blurEnabled) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
    val contentColor = if (isImmersive) Color.White else MaterialTheme.colorScheme.onSurface
    val buttonBorderColor = if (isImmersive) {
        Color.White.copy(alpha = if (blurEnabled) 0.16f else 0.10f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    }
    val showSectionLabel = !isImmersive
    val actionRows = listOf(
        ProfileWallpaperActionItem(
            title = "官方壁纸",
            icon = rememberAppPhotoIcon(),
            onClick = onOfficialWallpaperClick
        ),
        ProfileWallpaperActionItem(
            title = "本地相册",
            icon = rememberAppFolderIcon(),
            onClick = onLocalAlbumClick
        ),
        ProfileWallpaperActionItem(
            title = "恢复默认",
            icon = rememberAppRestoreIcon(),
            enabled = isResetEnabled,
            onClick = onResetWallpaperClick
        )
    ).chunked(columnCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = if (isImmersive) 2.dp else 10.dp,
                bottom = if (isImmersive) 4.dp else 10.dp
            )
    ) {
        if (showSectionLabel) {
            Text(
                text = "背景装扮",
                style = MaterialTheme.typography.labelMedium,
                color = sectionLabelColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionRows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowActions.forEach { action ->
                        ProfileWallpaperActionButton(
                            modifier = Modifier.weight(1f),
                            title = action.title,
                            titleLines = resolveProfileWallpaperActionTitleLines(
                                title = action.title,
                                labelMode = labelMode
                            ),
                            subtitle = "",
                            icon = action.icon,
                            containerColor = buttonColor,
                            contentColor = contentColor,
                            secondaryColor = Color.Transparent,
                            enabled = action.enabled,
                            blurEnabled = blurEnabled,
                            hazeState = hazeState,
                            borderColor = buttonBorderColor,
                            onClick = action.onClick
                        )
                    }
                    repeat(columnCount - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileWallpaperActionButton(
    title: String,
    titleLines: List<String>,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    borderColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val effectiveContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    val effectiveSecondaryColor = if (enabled) secondaryColor else secondaryColor.copy(alpha = 0.5f)
    val displayTitle = titleLines.joinToString("\n")
    val titleMaxLines = titleLines.size.coerceAtLeast(1)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled && hazeState != null) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.55f),
        border = BorderStroke(0.5.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (titleMaxLines > 1) 58.dp else 46.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = if (effectiveContentColor == Color.White) 0.16f else 0.55f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = effectiveContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveContentColor,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = effectiveSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun UserInfoText(user: UserState, centered: Boolean = false, forceWhite: Boolean = false) {
    //  修复：用户名颜色 + 阴影
    val contentColor = if (forceWhite) Color.White else MaterialTheme.colorScheme.onSurface
    val shadow = if (forceWhite) Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f) else null
    
    Text(
        text = user.name,
        style = MaterialTheme.typography.titleLarge.copy(
            shadow = shadow
        ),
        fontWeight = FontWeight.Bold,
        color = contentColor
    )
    Spacer(modifier = Modifier.height(8.dp)) // Increased spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        LevelTag(level = user.level)
        Spacer(modifier = Modifier.width(8.dp))
        if (user.isVip) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                Text(
                    user.vipLabel.ifEmpty { "大会员" },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                Text("正式会员", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

private data class ProfileWallpaperActionItem(
    val title: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun LevelTag(level: Int) {
    UserLevelBadge(level = level)
}

@Composable
fun UserStatsSection(user: UserState, onFollowingClick: () -> Unit = {}, transparent: Boolean = false) {
    // 如果背景透明，文字强制为白色
    val textColor = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface
    val labelColor = if (transparent) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            //  修复：背景色 (支持透明)
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface)
            .padding(bottom = 8.dp), // [Modified] 减少底部间距，使下方服务紧贴 (16.dp -> 8.dp)
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatItem(count = FormatUtils.formatStat(user.dynamic.toLong()), label = "动态", textColor = textColor, labelColor = labelColor)
        StatItem(count = FormatUtils.formatStat(user.following.toLong()), label = "关注", onClick = onFollowingClick, textColor = textColor, labelColor = labelColor)
        StatItem(count = FormatUtils.formatStat(user.follower.toLong()), label = "粉丝", textColor = textColor, labelColor = labelColor)
        StatItem(count = FormatUtils.formatStat(user.coin.toLong()), label = "硬币", textColor = textColor, labelColor = labelColor)
    }
}


@Composable
fun StatItem(
    count: String, 
    label: String, 
    onClick: (() -> Unit)? = null, 
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    // Detect if we need shadow (heuristic: if text is white)
    val useShadow = textColor == Color.White
    // Stronger shadow for better legibility against bright backgrounds
    val shadow = if (useShadow) Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 4f) else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable { onClick() }
        } else Modifier
    ) {
        //  修复：数字和标签颜色 + 阴影
        Text(
            text = count, 
            fontWeight = FontWeight.Bold, 
            fontSize = 18.sp, 
            color = textColor,
            style = LocalTextStyle.current.copy(shadow = shadow)
        )
        Text(
            text = label, 
            fontSize = 12.sp, 
            color = if (useShadow) Color.White.copy(alpha = 0.9f) else labelColor, // Whiter label
            style = LocalTextStyle.current.copy(shadow = shadow) // Apply same shadow to label
        )
    }
}

@Composable
fun VipBannerSection(user: UserState) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        colorScheme.tertiaryContainer,
                        colorScheme.primaryContainer
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (user.isVip) "尊贵的大会员" else "成为大会员",
                    color = colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "硬币: ${user.coin}   B币: ${user.bcoin}",
                    color = colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
            Text(
                text = if (user.isVip) "续费 >" else "开通 >",
                color = colorScheme.onTertiaryContainer,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ServicesSection(
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut> = emptyList(),
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    showFavoriteService: Boolean = true,
    onDownloadClick: () -> Unit = {},
    onWatchLaterClick: () -> Unit = {},
    onInboxClick: () -> Unit = {},  //  [新增] 私信入口
    onAccountManageClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    embeddedInPanel: Boolean = false,
    isLogin: Boolean = true,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val downloadIcon = rememberAppDownloadIcon()
    val historyIcon = rememberAppHistoryIcon()
    val bookmarkIcon = rememberAppBookmarkIcon()
    val watchLaterIcon = rememberAppWatchLaterIcon()
    val inboxIcon = rememberAppInboxIcon()
    val accountIcon = rememberAppProfileAddIcon()

    if (isTablet) {
        val items = buildList {
            add(Triple("离线缓存", downloadIcon, onDownloadClick))
            if (showHistoryService) add(Triple("历史记录", historyIcon, onHistoryClick))
            if (showFavoriteService) add(Triple("我的收藏", bookmarkIcon, onFavoriteClick))
            add(Triple("稍后再看", watchLaterIcon, onWatchLaterClick))
            add(Triple("消息中心", inboxIcon, onInboxClick))
            add(Triple("账号切换", accountIcon, onAccountManageClick))
        }

        Column(modifier = modifier) {
            Column(
                modifier = Modifier
                    .heightIn(max = ((items.size + 1) / 2) * 160.dp)
            ) {
                items.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowItems.forEach { (title, icon, onClick) ->
                            IOSGridItem(
                                icon = icon,
                                title = title,
                                onClick = onClick,
                                iconTint = contentColor,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                contentColor = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (showFavoriteService && favoriteFolderShortcuts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                ProfileFavoriteFolderShortcutGrid(
                    shortcuts = favoriteFolderShortcuts,
                    onFavoriteFolderClick = onFavoriteFolderClick,
                    contentColor = contentColor
                )
            }
        }

    } else {
        val useImmersiveServiceLayout = borderColor != null || embeddedInPanel
        if (useImmersiveServiceLayout) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val serviceRows: @Composable ColumnScope.() -> Unit = {
                    ProfileServiceRow(
                        icon = downloadIcon,
                        title = "离线缓存",
                        onClick = onDownloadClick,
                        iconTint = MaterialTheme.colorScheme.primary,
                        textColor = contentColor,
                    )
                    ProfileServiceDivider(contentColor)
                    if (showHistoryService) {
                        ProfileServiceRow(
                            icon = historyIcon,
                            title = "历史记录",
                            onClick = onHistoryClick,
                            iconTint = iOSBlue,
                            textColor = contentColor,
                        )
                        ProfileServiceDivider(contentColor)
                    }
                    if (showFavoriteService) {
                        ProfileServiceRow(
                            icon = bookmarkIcon,
                            title = "我的收藏",
                            onClick = onFavoriteClick,
                            iconTint = iOSYellow,
                            textColor = contentColor,
                        )
                        if (favoriteFolderShortcuts.isNotEmpty()) {
                            ProfileFavoriteFolderShortcutGrid(
                                shortcuts = favoriteFolderShortcuts,
                                onFavoriteFolderClick = onFavoriteFolderClick,
                                contentColor = contentColor,
                                compactHorizontal = true,
                                onMoreClick = onFavoriteClick,
                                modifier = Modifier.padding(start = 58.dp, end = 14.dp, bottom = 10.dp)
                            )
                        }
                        ProfileServiceDivider(contentColor)
                    }
                    ProfileServiceRow(
                        icon = watchLaterIcon,
                        title = "稍后再看",
                        onClick = onWatchLaterClick,
                        iconTint = iOSGreen,
                        textColor = contentColor,
                    )
                    ProfileServiceDivider(contentColor)
                    ProfileServiceRow(
                        icon = inboxIcon,
                        title = "消息中心",
                        onClick = onInboxClick,
                        iconTint = com.android.purebilibili.core.theme.iOSPink,
                        textColor = contentColor,
                    )
                }
                if (embeddedInPanel) {
                    Column(content = serviceRows)
                } else {
                    ProfileServicesListIsland(
                        containerColor = containerColor,
                        borderColor = borderColor
                    ) {
                        serviceRows()
                    }
                }
                ProfileAccountActionArea(
                    accountIcon = accountIcon,
                    onAccountManageClick = onAccountManageClick,
                    onLogout = onLogout,
                    isLogin = isLogin,
                    textColor = contentColor,
                    containerColor = containerColor,
                    borderColor = if (embeddedInPanel) null else borderColor,
                    embeddedInPanel = embeddedInPanel
                )
            }
        } else {
            Surface(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = containerColor,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp // Ensure no extra overlay
            ) {
                Column {
                    IOSClickableItem(
                        icon = downloadIcon,
                        title = "离线缓存",
                        onClick = onDownloadClick,
                        iconTint = MaterialTheme.colorScheme.primary,
                        textColor = contentColor
                    )
                    if (showHistoryService) {
                        IOSClickableItem(
                            icon = historyIcon,
                            title = "历史记录",
                            onClick = onHistoryClick,
                            iconTint = iOSBlue,
                            textColor = contentColor
                        )
                    }
                    if (showFavoriteService) {
                        IOSClickableItem(
                            icon = bookmarkIcon,
                            title = "我的收藏",
                            onClick = onFavoriteClick,
                            iconTint = iOSYellow,
                            textColor = contentColor
                        )
                        if (favoriteFolderShortcuts.isNotEmpty()) {
                            ProfileFavoriteFolderShortcutGrid(
                                shortcuts = favoriteFolderShortcuts,
                                onFavoriteFolderClick = onFavoriteFolderClick,
                                contentColor = contentColor,
                                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }
                    IOSClickableItem(
                        icon = watchLaterIcon,
                        title = "稍后再看",
                        onClick = onWatchLaterClick,
                        iconTint = iOSGreen,
                        textColor = contentColor
                    )
                    IOSClickableItem(
                        icon = inboxIcon,
                        title = "消息中心",
                        onClick = onInboxClick,
                        iconTint = com.android.purebilibili.core.theme.iOSPink,
                        textColor = contentColor
                    )
                    IOSClickableItem(
                        icon = accountIcon,
                        title = "账号切换",
                        onClick = onAccountManageClick,
                        iconTint = iOSOrange,
                        textColor = contentColor
                    )
                    IOSClickableItem(
                        title = if (isLogin) "退出登录" else "立即登录",
                        onClick = onLogout,
                        textColor = if (isLogin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        centered = true,
                        showChevron = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileServicesListIsland(
    containerColor: Color,
    borderColor: Color?,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = borderColor?.let { BorderStroke(0.5.dp, it) },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun ProfileServiceRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = CupertinoIcons.Default.ChevronForward,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.46f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProfileServiceDivider(contentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(0.5.dp)
            .background(contentColor.copy(alpha = 0.12f))
    )
}

@Composable
private fun ProfileAccountActionArea(
    accountIcon: ImageVector,
    onAccountManageClick: () -> Unit,
    onLogout: () -> Unit,
    isLogin: Boolean,
    textColor: Color,
    containerColor: Color,
    borderColor: Color?,
    embeddedInPanel: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (embeddedInPanel) {
            ProfileServiceRow(
                icon = accountIcon,
                title = "账号切换",
                onClick = onAccountManageClick,
                iconTint = iOSOrange,
                textColor = textColor
            )
            ProfileServiceDivider(textColor)
        } else {
            ProfileServicesListIsland(
                containerColor = containerColor,
                borderColor = borderColor
            ) {
                ProfileServiceRow(
                    icon = accountIcon,
                    title = "账号切换",
                    onClick = onAccountManageClick,
                    iconTint = iOSOrange,
                    textColor = textColor
                )
            }
        }
        val logoutShape = RoundedCornerShape(22.dp)
        val logoutContainer = if (embeddedInPanel) {
            textColor.copy(alpha = 0.08f)
        } else {
            containerColor.copy(alpha = 0.72f)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = logoutShape,
            color = logoutContainer,
            border = if (embeddedInPanel) {
                null
            } else {
                borderColor?.let { BorderStroke(0.5.dp, it.copy(alpha = 0.72f)) }
            },
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLogin) "退出登录" else "立即登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLogin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProfileFavoriteFolderShortcutGrid(
    shortcuts: List<ProfileFavoriteFolderShortcut>,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    contentColor: Color,
    compactHorizontal: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val folderIcon = rememberAppFolderIcon()
    if (compactHorizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shortcuts.take(3).forEach { shortcut ->
                ProfileFavoriteFolderShortcutChip(
                    shortcut = shortcut,
                    icon = folderIcon,
                    contentColor = contentColor,
                    compact = true,
                    onClick = {
                        onFavoriteFolderClick(shortcut.mediaId, shortcut.ownerMid, shortcut.title)
                    },
                    modifier = Modifier.width(148.dp)
                )
            }
            if (shortcuts.size > 3 && onMoreClick != null) {
                ProfileFavoriteFolderMoreChip(
                    contentColor = contentColor,
                    onClick = onMoreClick,
                    modifier = Modifier.width(112.dp)
                )
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        shortcuts.chunked(2).forEach { rowShortcuts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowShortcuts.forEach { shortcut ->
                    ProfileFavoriteFolderShortcutChip(
                        shortcut = shortcut,
                        icon = folderIcon,
                        contentColor = contentColor,
                        onClick = {
                            onFavoriteFolderClick(shortcut.mediaId, shortcut.ownerMid, shortcut.title)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowShortcuts.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileFavoriteFolderShortcutChip(
    shortcut: ProfileFavoriteFolderShortcut,
    icon: ImageVector,
    contentColor: Color,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 42.dp else 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.22f else 0.28f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iOSYellow,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.title,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${shortcut.mediaCount} 个内容",
                color = contentColor.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ProfileFavoriteFolderMoreChip(
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "更多收藏夹",
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AccountSwitchDialog(
    accounts: List<StoredAccountSession>,
    activeAccountMid: Long?,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitch: (Long) -> Unit,
    onRemove: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账号切换", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        text = "暂无已保存账号，先添加一个账号后即可快速切换。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    accounts.forEach { account ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = account.mid != activeAccountMid) {
                                        onSwitch(account.mid)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = account.face,
                                    contentDescription = account.name,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.name.ifBlank { "UID ${account.mid}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = buildString {
                                            append("UID ${account.mid}")
                                            if (account.vipLabel.isNotBlank()) {
                                                append(" · ${account.vipLabel}")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (account.mid == activeAccountMid) {
                                    Text(
                                        text = "当前",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                } else {
                                    TextButton(onClick = { onSwitch(account.mid) }) {
                                        Text("切换")
                                    }
                                    TextButton(onClick = { onRemove(account.mid) }) {
                                        Text("移除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddAccount) {
                Text("添加账号")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 个人空间三连彩蛋入口 - 图三样式
 * 显示点赞+投币+收藏三个图标，长按大拇指触发三连动画后弹窗选择
 */
@Composable
fun ProfileTripleActionEntry(
    onVipClick: () -> Unit,
    on4KClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 引入动画和手势相关
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }
    var showDialog by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    val progressDuration = 1500 // 1.5 秒
    
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()
    
    // 进度动画
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = if (isLongPressing) {
            androidx.compose.animation.core.tween(durationMillis = progressDuration, easing = androidx.compose.animation.core.LinearEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        },
        label = "tripleProgress",
        finishedListener = { progress ->
            if (progress >= 1f && isLongPressing) {
                haptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                showCelebration = true
                isLongPressing = false
            }
        }
    )
    
    LaunchedEffect(animatedProgress) {
        longPressProgress = animatedProgress
    }

    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
        }
    }
    
    // 选择弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("🎉 三连成功！") },
            text = { Text("请选择你想解锁的功能：") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onVipClick()
                }) {
                    Text("解锁大会员")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    on4KClick()
                }) {
                    Text("4K 画质")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isLongPressing = true
                            tryAwaitRelease()
                            isLongPressing = false
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val likeIcon = rememberAppLikeIcon()
            val bookmarkIcon = rememberAppBookmarkIcon()
            // 点赞图标 (带进度环)
            com.android.purebilibili.feature.video.ui.section.TripleProgressIcon(
                icon = likeIcon,
                text = "149",
                progress = longPressProgress,
                progressColor = MaterialTheme.colorScheme.primary,
                isActive = false
            )

            // 投币图标
            com.android.purebilibili.feature.video.ui.section.TripleProgressIcon(
                icon = com.android.purebilibili.core.ui.AppIcons.BiliCoin,
                text = "25",
                progress = longPressProgress,
                progressColor = MaterialTheme.colorScheme.primary,
                isActive = false
            )

            // 收藏图标
            com.android.purebilibili.feature.video.ui.section.TripleProgressIcon(
                icon = bookmarkIcon,
                text = "7",
                progress = longPressProgress,
                progressColor = MaterialTheme.colorScheme.primary,
                isActive = false
            )
        }

        if (showCelebration) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation(
                    visible = true,
                    reducedMotion = false,
                    onAnimationEnd = {
                        showCelebration = false
                        showDialog = true
                    }
                )
            }
        }
    }
}
