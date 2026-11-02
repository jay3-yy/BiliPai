package com.android.purebilibili.feature.profile

import com.android.purebilibili.core.util.responsiveContentWidth

import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.data.model.AccountLedgerKind
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.feature.personal.PersonalMediaCardFrame
import com.android.purebilibili.core.ui.components.VideoListLayoutToggle
import com.android.purebilibili.core.ui.components.resolveVideoListColumns
import com.android.purebilibili.core.ui.components.rememberVideoListLayoutControl
import com.android.purebilibili.core.ui.components.videoListBoundsAnimation
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.runtime.key
import coil3.request.crossfade
import coil3.request.placeholder
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appGentleActionClickable
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.AppSpacingTokens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.android.purebilibili.feature.login.OfficialQrAuthorizationDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.transition.LocalDynamicImagePreviewTextVisible
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import java.io.File
import com.android.purebilibili.core.theme.DarkBackground
import com.android.purebilibili.core.theme.DarkSurface
import com.android.purebilibili.core.theme.DarkSurfaceVariant
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.PickGalleryVisualMedia
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceRect
import com.android.purebilibili.feature.dynamic.components.imagePreviewSourceBounds
import com.android.purebilibili.feature.dynamic.components.resolveImagePreviewPlaceholderCacheKey
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppTopBarStyle
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.AppSplitLayout
import com.android.purebilibili.core.ui.TopReadabilityChrome
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
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
import com.android.purebilibili.core.ui.rememberAppChevronForwardIcon
import com.android.purebilibili.core.ui.rememberAppDeleteIcon
import com.android.purebilibili.core.ui.rememberAppLinkIcon
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.AppSemanticAccentRole
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.rememberAppWarningIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceGridItem
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.store.StoredAccountSession
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.space.resolveSpaceAggregateVideoId
import com.android.purebilibili.core.ui.common.contentTabPagerGestures
import com.android.purebilibili.core.ui.common.rememberContentTabPager
import com.android.purebilibili.core.ui.common.stationaryPagerHeader
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import androidx.compose.foundation.pager.PagerState
import com.android.purebilibili.feature.settings.AppThemeMode
import androidx.compose.ui.input.nestedscroll.nestedScroll

import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.times
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel


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
    // A decoded static wallpaper is cheap to retain and is part of the persistent profile
    // chrome. Dropping it during a bottom-tab transition produces a visible blank flash.
    // The transition budget still applies to the skin video layer below.
    return hasTopPhoto
}

internal fun resolveProfileTopBarScrimAlpha(
    isImmersive: Boolean,
    collapsedFraction: Float
): Float {
    return 0f
}

internal const val PROFILE_PINNED_TOP_CHROME_FADE_RANGE_PX = 120

/** 0 at rest over the banner, 1 after the header has scrolled under the pinned chrome. */
internal fun resolveProfilePinnedTopChromeScrim(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeRangePx: Int = PROFILE_PINNED_TOP_CHROME_FADE_RANGE_PX,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (fadeRangePx <= 0) return 0f
    return (firstVisibleItemScrollOffset.toFloat() / fadeRangePx).coerceIn(0f, 1f)
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

internal fun resolveProfileSkinBackgroundImagePath(
    useSplitLayout: Boolean,
    wideImagePath: String?,
    squaredImagePath: String?,
): String? {
    val preferred = if (useSplitLayout) squaredImagePath else wideImagePath
    val fallback = if (useSplitLayout) wideImagePath else squaredImagePath
    return preferred?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    isCurrentPage: Boolean = true,
    accountSessionRefreshGeneration: Int = 0,
    initialEditAccount: Boolean = false,
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onAccountSwitchSuccess: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onAccountLedgerClick: ((AccountLedgerKind) -> Unit)? = null,
    onCommentSourceClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    onSubscriptionClick: () -> Unit = {},
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},  //  关注列表点击
    onFansClick: (Long) -> Unit = {},
    onDownloadClick: () -> Unit = {},  //  离线缓存点击
    onWatchLaterClick: () -> Unit = {}, // 稍后再看点击
    onInboxClick: () -> Unit = {},  //  [新增] 私信入口点击
    onVideoClick: (String) -> Unit = {},  // [新增] 视频点击（三连彩蛋跳转用）
    onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    onBangumiMoreClick: () -> Unit = {},
    skinBackgroundImagePath: String? = null,
    skinSquaredBackgroundImagePath: String? = null,
    skinVideoBackgroundPath: String? = null,
    skinVideoPlayMode: String? = null,
    deferImmersiveRenderBudget: Boolean = false,
    scrollToTopChannel: kotlinx.coroutines.channels.Channel<Unit>? = null
    // [注意] 移除了 globalHazeState - 双 hazeSource 模式与 Haze 库冲突
) {
    var showMyComments by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showMyComments && isCurrentPage) { showMyComments = false }
    if (showMyComments) {
        com.android.purebilibili.feature.settings.screen.CommentFraudHistoryScreen(
            onBack = { showMyComments = false },
            archiveMode = true,
            onOpenSource = onCommentSourceClick,
        )
        return
    }
    CompositionLocalProvider(
        LocalProfileLedgerNavigation provides onAccountLedgerClick,
        LocalProfileCommentsNavigation provides { showMyComments = true },
    ) {
        ProfileScreenContent(
            viewModel = viewModel,
            isCurrentPage = isCurrentPage,
            accountSessionRefreshGeneration = accountSessionRefreshGeneration,
            initialEditAccount = initialEditAccount,
            onBack = onBack,
            onGoToLogin = onGoToLogin,
            onLogoutSuccess = onLogoutSuccess,
            onAccountSwitchSuccess = onAccountSwitchSuccess,
            onSettingsClick = onSettingsClick,
            onSearchClick = onSearchClick,
            onHistoryClick = onHistoryClick,
            showHistoryService = showHistoryService,
            onFavoriteClick = onFavoriteClick,
            onSubscriptionClick = onSubscriptionClick,
            onFavoriteFolderClick = onFavoriteFolderClick,
            onFollowingClick = onFollowingClick,
            onFansClick = onFansClick,
            onDownloadClick = onDownloadClick,
            onWatchLaterClick = onWatchLaterClick,
            onInboxClick = onInboxClick,
            onVideoClick = onVideoClick,
            onBangumiClick = onBangumiClick,
            onBangumiMoreClick = onBangumiMoreClick,
            skinBackgroundImagePath = skinBackgroundImagePath,
            skinSquaredBackgroundImagePath = skinSquaredBackgroundImagePath,
            skinVideoBackgroundPath = skinVideoBackgroundPath,
            skinVideoPlayMode = skinVideoPlayMode,
            deferImmersiveRenderBudget = deferImmersiveRenderBudget,
            scrollToTopChannel = scrollToTopChannel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreenContent(
    viewModel: ProfileViewModel = viewModel(),
    isCurrentPage: Boolean = true,
    accountSessionRefreshGeneration: Int = 0,
    initialEditAccount: Boolean = false,
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onAccountSwitchSuccess: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    showHistoryService: Boolean = true,
    onFavoriteClick: () -> Unit,
    onSubscriptionClick: () -> Unit = {},
    onFavoriteFolderClick: (Long, Long, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},  //  关注列表点击
    onFansClick: (Long) -> Unit = {},
    onDownloadClick: () -> Unit = {},  //  离线缓存点击
    onWatchLaterClick: () -> Unit = {}, // 稍后再看点击
    onInboxClick: () -> Unit = {},  //  [新增] 私信入口点击
    onVideoClick: (String) -> Unit = {},  // [新增] 视频点击（三连彩蛋跳转用）
    onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    onBangumiMoreClick: () -> Unit = {},
    skinBackgroundImagePath: String? = null,
    skinSquaredBackgroundImagePath: String? = null,
    skinVideoBackgroundPath: String? = null,
    skinVideoPlayMode: String? = null,
    deferImmersiveRenderBudget: Boolean = false,
    scrollToTopChannel: kotlinx.coroutines.channels.Channel<Unit>? = null
    // [注意] 移除了 globalHazeState - 双 hazeSource 模式与 Haze 库冲突
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val headerBlurEnabled =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.headerBlurEnabled
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountMid by viewModel.activeAccountMid.collectAsStateWithLifecycle()
    val playbackAccountMid by viewModel.playbackAccountMid.collectAsStateWithLifecycle()
    val purposeAccountMids by viewModel.purposeAccountMids.collectAsStateWithLifecycle()
    val view = LocalView.current
    var showAccountSwitchDialog by remember { mutableStateOf(false) }
    val windowSizeClass = LocalWindowSizeClass.current
    val skinProfileBackgroundPath = remember(
        windowSizeClass.shouldUseSplitLayout,
        skinBackgroundImagePath,
        skinSquaredBackgroundImagePath,
    ) {
        resolveProfileSkinBackgroundImagePath(
            useSplitLayout = windowSizeClass.shouldUseSplitLayout,
            wideImagePath = skinBackgroundImagePath,
            squaredImagePath = skinSquaredBackgroundImagePath,
        )
    }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isLoggedOut = state is ProfileUiState.LoggedOut
    val privacyModeEnabled by SettingsManager.getPrivacyModeEnabled(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.isPrivacyModeEnabledSync(context))
    val profileActionScope = androidx.compose.runtime.rememberCoroutineScope()
    val togglePrivacyMode: () -> Unit = {
        // 固定本次切换目标，避免保存期间状态更新导致提示反转。
        val targetPrivacyModeEnabled = !privacyModeEnabled
        profileActionScope.launch {
            SettingsManager.setPrivacyModeEnabled(context, targetPrivacyModeEnabled)
            Toast.makeText(
                context,
                if (targetPrivacyModeEnabled) "已开启无痕模式" else "已关闭无痕模式",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val toggleThemeMode: () -> Unit = {
        profileActionScope.launch {
            SettingsManager.setThemeMode(
                context,
                if (isDarkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK,
            )
        }
    }
    val isImmersiveMobileProfile = !windowSizeClass.shouldUseSplitLayout &&
        (skinProfileBackgroundPath != null || !skinVideoBackgroundPath.isNullOrBlank() ||
            (state as? ProfileUiState.Success)?.user?.topPhoto?.isNotEmpty() == true)
    val shouldControlSystemBars = isLoggedOut || isImmersiveMobileProfile
    var handledAccountSessionRefreshGeneration by remember(viewModel) {
        mutableIntStateOf(0)
    }
    val lightStatusBars = resolveProfileLightStatusBars(
        isImmersive = shouldControlSystemBars,
        useSplitLayout = windowSizeClass.shouldUseSplitLayout,
        isDarkTheme = isDarkTheme
    )

    // [Blur] Haze State: only expose a source when the platform and runtime guard
    // can actually render it; otherwise every top bar keeps its solid fallback.
    val hazeState = if (headerBlurEnabled &&
        shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
        !isLowBlurBudgetForced()
    ) {
        rememberRecoverableHazeState()
    } else {
        null
    }
    var profileScrollToTopRequestId by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopChannel) {
        scrollToTopChannel?.receiveAsFlow()?.collect {
            profileScrollToTopRequestId += 1
        }
    }

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

        if (shouldControlSystemBars && window != null) {
            com.android.purebilibili.core.ui.AppWindowSystemUiController.ensureEdgeToEdge(window)
            com.android.purebilibili.core.ui.setWindowStatusBarColor(window, Color.Transparent.toArgb())
            com.android.purebilibili.core.ui.setWindowNavigationBarColor(window, Color.Transparent.toArgb())
            insetsController?.isAppearanceLightStatusBars = lightStatusBars
            insetsController?.isAppearanceLightNavigationBars = lightStatusBars
        }

        onDispose {
            // 离开时恢复原始配置
            if (shouldControlSystemBars && window != null && insetsController != null) {
                com.android.purebilibili.core.ui.setWindowStatusBarColor(window, originalStatusBarColor)
                com.android.purebilibili.core.ui.setWindowNavigationBarColor(window, originalNavBarColor)
                insetsController.isAppearanceLightStatusBars = originalLightStatusBars
                insetsController.isAppearanceLightNavigationBars = originalLightNavigationBars
            }
        }
    }

    DisposableEffect(viewModel, isCurrentPage) {
        viewModel.setProfilePageActive(isCurrentPage)
        onDispose { viewModel.setProfilePageActive(false) }
    }

    LaunchedEffect(viewModel, isCurrentPage, accountSessionRefreshGeneration) {
        if (isCurrentPage) {
            val force = shouldForceProfileLoadForAccountSessionRefresh(
                isCurrentPage = isCurrentPage,
                accountSessionRefreshGeneration = accountSessionRefreshGeneration,
                handledAccountSessionRefreshGeneration = handledAccountSessionRefreshGeneration
            )
            viewModel.loadProfile(force = force)
            if (force) {
                handledAccountSessionRefreshGeneration = accountSessionRefreshGeneration
            }
        }
    }

    LaunchedEffect(Unit) {
        //  [埋点] 页面浏览追踪
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("ProfileScreen")
    }

    if (showAccountSwitchDialog) {
        AccountSwitchDialog(
            accounts = accounts,
            activeAccountMid = activeAccountMid,
            playbackAccountMid = playbackAccountMid,
            purposeAccountMids = purposeAccountMids,
            onSetPurpose = { purpose, mid ->
                viewModel.setPurposeAccount(purpose, mid,
                    onSuccess = {
                        onAccountSwitchSuccess()
                        Toast.makeText(context, "账号用途已更新，下次请求生效", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                )
            },
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
            onSetPlayback = { mid ->
                viewModel.setPlaybackAccount(
                    mid = mid,
                    onSuccess = {
                        Toast.makeText(context, if (mid == null) "播放授权已跟随当前账号" else "已设置播放授权账号", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
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
    val profileProgressiveChrome = rememberProfileProgressiveTopChrome()
    val profileHeaderBlurActive = false
    when (currentUiState) {
        is ProfileUiState.Loading -> {
            ProfileLoadingSkeleton(animated = isCurrentPage)
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
                topPhoto = skinProfileBackgroundPath ?: currentUiState.topPhoto
            )


            Box(modifier = Modifier.fillMaxSize()) {
                MobileProfileContent(
                    captureBackground = {
                        ProfileBackground(
                            user = guestUser,
                            viewModel = viewModel,
                            deferImmersiveRenderBudget = deferImmersiveRenderBudget,
                            skinVideoBackgroundPath = skinVideoBackgroundPath,
                            skinVideoPlayMode = skinVideoPlayMode,
                            playSkinVideo = isCurrentPage && !initialEditAccount,
                        )
                    },
                    progressiveTopChrome = profileProgressiveChrome,
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
                    paddingValues = PaddingValues(0.dp), // 全屏
                    scrollToTopRequestId = profileScrollToTopRequestId
                )
            }

        }
        is ProfileUiState.Error -> {
            // 🔧 [新增] 离线/错误状态 - 显示错误信息并提供重试和离线缓存入口
            AppScaffold(
                topBarIconBackdropSource = profileProgressiveChrome.source,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    BiliPaiImmersiveTopBar(
                        backdrop = profileProgressiveChrome.backdrop,
                        enabled = profileProgressiveChrome.enabled,
                        headerBlurActive = profileHeaderBlurActive,
                    ) {
                    Box {
                        if (!profileProgressiveChrome.enabled) {
                        TopReadabilityChrome(
                            height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                            surfaceColor = MaterialTheme.colorScheme.background,
                            surfaceAlpha = 0.86f
                        )
                        }
                        AppTopBar(
                            title = "我的",
                            style = AppTopBarStyle.CENTERED,
                            navigationIcon = {
                                AppIconButton(onClick = onBack) {
                                    AppIcon(rememberAppBackIcon(), contentDescription = "Back")
                                }
                            },
                            actions = {
                                AppIconButton(onClick = onSettingsClick) {
                                    AppIcon(rememberAppSettingsIcon(), contentDescription = "Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .profileProgressiveBackdrop(profileProgressiveChrome)
                        .globalWallpaperAwareBackground(MaterialTheme.colorScheme.background)
                        .padding(padding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 错误图标
                    AppIcon(
                        rememberAppWarningIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AppContentColors.secondaryText()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppText(
                        text = currentUiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppContentColors.secondaryText()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 重试按钮
                    AppButton(
                        onClick = { viewModel.loadProfile(force = true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                            contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                        )
                    ) {
                        AppIcon(rememberAppRefreshIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        AppText("重试")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 离线缓存入口
                    AppOutlinedButton(onClick = onDownloadClick) {
                        AppIcon(rememberAppDownloadIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        AppText("查看离线缓存")
                    }
                }
            }
        }
        is ProfileUiState.Success -> {
            val decoratedUser = remember(currentUiState.user, skinProfileBackgroundPath) {
                currentUiState.user.copy(
                    topPhoto = skinProfileBackgroundPath ?: currentUiState.user.topPhoto
                )
            }
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

            AppScaffold(
                topBarIconBackdropSource = profileProgressiveChrome.source,
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background,
                // [Immersive] Mobile hides default TopBar, Tablet keeps it
                topBar = {
                    if (windowSizeClass.shouldUseSplitLayout) {
                        BiliPaiImmersiveTopBar(
                            backdrop = profileProgressiveChrome.backdrop,
                            enabled = profileProgressiveChrome.enabled,
                            headerBlurActive = false,
                        ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            if (!profileProgressiveChrome.enabled) {
                            TopReadabilityChrome(
                                height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 104.dp,
                                surfaceColor = MaterialTheme.colorScheme.background,
                                surfaceAlpha = 0.82f,
                                hazeState = null,
                                hazeEnabled = false
                            )
                            }
                            AppTopBar(
                                title = "我的",
                                largeTitle = "我的",
                                style = AppTopBarStyle.LARGE,
                                navigationIcon = {
                                    AppIconButton(onClick = onBack) {
                                        AppIcon(rememberAppBackIcon(), contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    ProfileTopActions(
                                        compact = false,
                                        privacyModeEnabled = privacyModeEnabled,
                                        onSearchClick = onSearchClick,
                                        onInboxClick = onInboxClick,
                                        onPrivacyClick = togglePrivacyMode,
                                        onAccountClick = { showAccountSwitchDialog = true },
                                        onThemeClick = toggleThemeMode,
                                        onSettingsClick = onSettingsClick,
                                        tint = androidx.compose.material3.LocalContentColor.current,
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent
                                )
                            )
                        }
                        }
                    }
                },
                contentWindowInsets = if (!windowSizeClass.shouldUseSplitLayout) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (windowSizeClass.shouldUseSplitLayout) {
                                Modifier.profileProgressiveBackdrop(profileProgressiveChrome)
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    if (windowSizeClass.shouldUseSplitLayout) {
                        ProfileBackground(
                            user = decoratedUser,
                            viewModel = viewModel,
                            deferImmersiveRenderBudget = deferImmersiveRenderBudget,
                            skinVideoBackgroundPath = skinVideoBackgroundPath,
                            skinVideoPlayMode = skinVideoPlayMode,
                            playSkinVideo = isCurrentPage && !initialEditAccount,
                        )
                    }

                    ProfileSpaceContent(
                        viewModel = viewModel,
                        initialEditAccount = initialEditAccount,
                        user = decoratedUser,
                        accountFace = currentUiState.user.face,
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
                        onSubscriptionClick = onSubscriptionClick,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        onFollowingClick = { onFollowingClick(currentUiState.user.mid) },
                        onFansClick = { onFansClick(currentUiState.user.mid) },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onVideoClick = onVideoClick,
                        onContributionRetry = viewModel::retryProfileContributions,
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
                        onSearchClick = onSearchClick,
                        privacyModeEnabled = privacyModeEnabled,
                        onPrivacyClick = togglePrivacyMode,
                        onThemeClick = toggleThemeMode,
                        hazeState = hazeState,
                        paddingValues = padding,
                        isTablet = windowSizeClass.shouldUseSplitLayout,
                        scrollToTopRequestId = profileScrollToTopRequestId,
                        progressiveTopChrome = profileProgressiveChrome,
                        captureBackground = {
                            if (!windowSizeClass.shouldUseSplitLayout) {
                                ProfileBackground(
                                    user = decoratedUser,
                                    viewModel = viewModel,
                                    deferImmersiveRenderBudget = deferImmersiveRenderBudget,
                                    skinVideoBackgroundPath = skinVideoBackgroundPath,
                                    skinVideoPlayMode = skinVideoPlayMode,
                                    playSkinVideo = isCurrentPage && !initialEditAccount,
                                )
                            }
                        },
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
    deferImmersiveRenderBudget: Boolean,
    skinVideoBackgroundPath: String? = null,
    skinVideoPlayMode: String? = null,
    playSkinVideo: Boolean = true,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.shouldUseSplitLayout
    val hasSkinVideo = !skinVideoBackgroundPath.isNullOrBlank()
    val hasWallpaper = user.topPhoto.isNotEmpty() || hasSkinVideo
    val headerMotion = LocalProfileBackgroundMotion.current
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
    val backgroundVisible by remember(headerMotion, heroHeight, density) {
        derivedStateOf {
            // Expanded photos leave the statistics background visible. Pause media
            // only when scrolling has actually removed the whole header.
            headerMotion == null || headerMotion.scrollOffsetPx() <
                with(density) { heroHeight.toPx() } + headerMotion.pullOffsetPx()
        }
    }
    val wallpaperDecodeSize = remember(configuration.screenWidthDp, density.density) {
        resolveProfileWallpaperDecodeSizePx(
            screenWidthDp = configuration.screenWidthDp,
            density = density.density
        )
    }
    val wallpaperModel = remember(user.topPhoto) {
        File(user.topPhoto).takeIf(File::isAbsolute) ?: user.topPhoto
    }
    val wallpaperRequest = remember(context, wallpaperModel, wallpaperDecodeSize) {
        ImageRequest.Builder(context)
            .data(wallpaperModel)
            .size(wallpaperDecodeSize.first, wallpaperDecodeSize.second)
            .scale(Scale.FILL)
            // Profile wallpaper is persistent chrome. Fading a local skin asset in from the
            // surface color makes every bottom-tab entry look like a full-screen flash.
            .crossfade(false)
            .build()
    }
    val heroChrome = remember(hasWallpaper, isDarkTheme, colorScheme.onSurface, colorScheme.onSurfaceVariant) {
        resolveProfileHeroChrome(
            hasWallpaper = hasWallpaper,
            isDarkTheme = isDarkTheme,
            onSurfaceColor = colorScheme.onSurface,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .globalWallpaperAwareBackground(colorScheme.surface)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .profileHeaderBackgroundMotion(heroHeight)
            .align(Alignment.TopCenter)
    ) {
        if (shouldRenderProfileImmersiveBackground(user.topPhoto.isNotEmpty(), deferImmersiveRenderBudget)) {
            if (user.topPhoto.isNotEmpty()) {
                com.android.purebilibili.core.ui.wallpaper.WallpaperMedia(
                    uri = user.topPhoto,
                    imageModel = wallpaperRequest,
                    playbackEnabled = playSkinVideo && backgroundVisible && !deferImmersiveRenderBudget,
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
            }
            if (hasSkinVideo && !deferImmersiveRenderBudget) {
                ProfileSkinVideoBackground(
                    videoPath = requireNotNull(skinVideoBackgroundPath),
                    playMode = skinVideoPlayMode,
                    playbackEnabled = playSkinVideo && backgroundVisible,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (hasSkinVideo && !deferImmersiveRenderBudget) {
            ProfileSkinVideoBackground(
                videoPath = requireNotNull(skinVideoBackgroundPath),
                playMode = skinVideoPlayMode,
                playbackEnabled = playSkinVideo && backgroundVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (heroChrome.scrimBottomAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
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

internal fun resolveProfileSkinVideoRepeatMode(playMode: String?): Int {
    return if (playMode?.trim()?.equals("once", ignoreCase = true) == true) {
        Player.REPEAT_MODE_OFF
    } else {
        Player.REPEAT_MODE_ONE
    }
}

@Composable
@android.annotation.SuppressLint("UnsafeOptInUsageError")
private fun ProfileSkinVideoBackground(
    videoPath: String,
    playMode: String?,
    playbackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repeatMode = remember(playMode) { resolveProfileSkinVideoRepeatMode(playMode) }
    val player = remember(videoPath) {
        com.android.purebilibili.core.lifecycle.AbandonableResource(
            ExoPlayer.Builder(context).build(),
        ) { it.release() }
    }.value

    DisposableEffect(player, videoPath) {
        player.volume = 0f
        player.repeatMode = repeatMode
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
        player.prepare()
        onDispose {
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player, playbackEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (playbackEnabled) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (playbackEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.play()
        } else {
            player.pause()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.pause()
        }
    }

    SideEffect {
        player.repeatMode = repeatMode
        if (!playbackEnabled) player.pause()
    }

    AndroidView(
        factory = { viewContext -> ProfileSkinVideoView(viewContext).apply { this.player = player } },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.clipToBounds(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSpaceContent(
    viewModel: ProfileViewModel,
    initialEditAccount: Boolean = false,
    user: UserState,
    accountFace: String,
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
    onSubscriptionClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onInboxClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onContributionRetry: () -> Unit,
    onDynamicDeleteClick: (DynamicDeleteAction) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onBangumiMoreClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    privacyModeEnabled: Boolean,
    onPrivacyClick: () -> Unit,
    onThemeClick: () -> Unit,
    hazeState: HazeState?,
    paddingValues: PaddingValues,
    isTablet: Boolean,
    scrollToTopRequestId: Int = 0,
    progressiveTopChrome: ProfileProgressiveTopChrome = ProfileProgressiveTopChrome(null, false),
    captureBackground: @Composable BoxScope.() -> Unit = {},
) {
    var showEditDialog by rememberSaveable(user.mid) { mutableStateOf(initialEditAccount) }
    var avatarPreview by remember(user.mid) { mutableStateOf<String?>(null) }
    val avatarSourceRect = rememberImagePreviewSourceRect()
    val openAvatarPreview: () -> Unit = {
        if (user.face.isNotBlank()) {
            prepareImagePreviewSourceTransition(avatarSourceRect.value)
            avatarPreview = user.face
        }
    }
    var accountAvatarPickerMid by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountAvatarCrop by rememberSaveable(user.mid) { mutableStateOf<String?>(null) }
    var accountAvatarConfirmation by rememberSaveable(user.mid) { mutableStateOf<String?>(null) }
    var editingAccountField by remember(user.mid) { mutableStateOf<ProfileAccountField?>(null) }
    var showAccountSignDialog by remember(user.mid) { mutableStateOf(false) }
    var showAccountSexDialog by remember(user.mid) { mutableStateOf(false) }
    var showAdjustmentSheet by remember { mutableStateOf(false) }
    var tempSelectedUri by remember { mutableStateOf<Uri?>(null) }
    val customBackgroundUri by viewModel.getProfileBgUri().collectAsStateWithLifecycle(initialValue = null
        )
    val context = LocalContext.current
    val showProfileEditButton by SettingsManager.getShowProfileEditButton(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickGalleryVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            tempSelectedUri = uri
            showAdjustmentSheet = true
        }
    }
    val accountAvatarPicker = rememberLauncherForActivityResult(contract = PickGalleryVisualMedia()) { uri: Uri? ->
        val mid = accountAvatarPickerMid
        accountAvatarPickerMid = null
        if (uri != null && mid == user.mid) {
            if (context.contentResolver.getType(uri)?.equals("image/gif", ignoreCase = true) == true) {
                Toast.makeText(context, "不能选择 GIF 头像", Toast.LENGTH_SHORT).show()
            } else {
                accountAvatarCrop = uri.toString()
            }
        }
    }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showWallpaperActionSheet by remember { mutableStateOf(false) }

    if (showEditDialog) {
        ProfileEditAccountDialog(
            state = editableAccount,
            mid = user.mid,
            accountFace = accountFace,
            isSaving = space.isSavingSign,
            onDismiss = { showEditDialog = false; if (initialEditAccount) onBack() },
            onEditSign = { showEditDialog = false; showAccountSignDialog = true },
            // 子编辑流程（昵称/生日、性别、头像）叠加在编辑资料之上，返回时回到编辑资料。
            onEditField = { field -> editingAccountField = field },
            onEditSex = { showAccountSexDialog = true },
            onEditAvatar = {
                accountAvatarPickerMid = user.mid
                accountAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
    avatarPreview?.let { image ->
        val density = LocalDensity.current
        val radius = avatarSourceRect.value?.let { with(density) { (minOf(it.width, it.height) / 2f).toDp().value } } ?: 0f
        ImagePreviewDialog(images = listOf(image), initialIndex = 0, sourceRect = avatarSourceRect.value,
            sourceCornerRadiusDp = radius, onDismiss = { avatarPreview = null })
    }
    accountAvatarCrop?.let { source ->
        com.android.purebilibili.feature.dynamic.components.DynamicImageCropDialog(
            source = source,
            onDismiss = { accountAvatarCrop = null },
            onCropped = { cropped ->
                accountAvatarCrop = null
                accountAvatarConfirmation = cropped
            }
        )
    }
    if (showAccountSignDialog) {
        ProfileAccountSignDialog(
            initialValue = editableAccount.sign,
            onDismiss = { showAccountSignDialog = false; if (initialEditAccount) showEditDialog = true },
            onSave = { sign ->
                showAccountSignDialog = false
                if (initialEditAccount) showEditDialog = true
                onSignSave(sign)
            },
        )
    }
    editingAccountField?.let { field ->
        ProfileAccountFieldDialog(
            field = field,
            initialValue = if (field == ProfileAccountField.NAME) editableAccount.name else editableAccount.birthday,
            onDismiss = { editingAccountField = null },
            onSubmit = { value ->
                editingAccountField = null
                viewModel.updateAccountField(field, value)
            }
        )
    }
    if (showAccountSexDialog) {
        AppAlertDialog(
            onDismissRequest = { showAccountSexDialog = false },
            title = { AppText("修改账号性别") },
            text = {
                Column {
                    listOf("男" to "1", "女" to "2", "保密" to "0").forEach { (label, value) ->
                        AppTextButton(onClick = {
                            showAccountSexDialog = false
                            viewModel.updateAccountField(ProfileAccountField.SEX, value)
                        }) { AppText(label) }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showAccountSexDialog = false }) { AppText("取消") } }
        )
    }
    accountAvatarConfirmation?.let { image ->
        ProfileAvatarConfirmDialog(
            image = image,
            onDismiss = { accountAvatarConfirmation = null },
            onConfirm = { accountAvatarConfirmation = null; viewModel.updateAccountAvatar(Uri.parse(image)) },
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
        AppAlertDialog(
            onDismissRequest = { showPhotoPickerDialog = false },
            title = { AppText("选择照片", fontWeight = FontWeight.Bold) },
            text = { AppText("将打开系统相册选择一张照片作为背景。\n\n仅获取选中照片的访问权限，不会访问其他照片。") },
            confirmButton = {
                AppButton(
                    onClick = {
                        showPhotoPickerDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ) {
                    AppText("选择照片")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showPhotoPickerDialog = false }) {
                    AppText("取消")
                }
            }
        )
    }

    val hasWallpaper = user.topPhoto.isNotEmpty()
    val tabletWallpaperRevealHeight = resolveProfileTabletWallpaperRevealHeightDp(
        useSplitLayout = isTablet,
        hasWallpaper = hasWallpaper
    ).dp
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
    val tabletRailScrollState = rememberScrollState()
    val profileTabs = remember { defaultProfileSpaceTabs() }
    val mobilePageListStates = profileTabs.associate { it.tab to rememberLazyListState() }
    val tabletPageListStates = profileTabs.associate { it.tab to rememberLazyListState() }
    val tabletFeedListState = tabletPageListStates.getValue(space.selectedTab)
    val mobileListState = mobilePageListStates.getValue(space.selectedTab)
    val selectedPage = profileTabs.indexOfFirst { it.tab == space.selectedTab }.coerceAtLeast(0)
    val pagerState = rememberContentTabPager(
        pageCount = profileTabs.size,
        selectedPage = selectedPage,
        onPageSelected = { onTabSelected(profileTabs[it].tab) },
        prepareNeighbors = { sourcePage ->
            val states = if (isTablet) tabletPageListStates else mobilePageListStates
            val source = states.getValue(profileTabs[sourcePage].tab)
            profileTabs.forEachIndexed { index, tab ->
                if (index != sourcePage) {
                    // Phone content is one existing sheet item after the hero. Its pixel
                    // offset must match across pages so the dashboard/rail stay stationary.
                    states.getValue(tab.tab).requestScrollToItem(
                        source.firstVisibleItemIndex,
                        source.firstVisibleItemScrollOffset,
                    )
                }
            }
        },
    )
    val density = LocalDensity.current
    var identityStatsTopPx by remember(density, statusBarTopPadding) {
        mutableStateOf(with(density) { (statusBarTopPadding + 215.dp).toPx() })
    }
    // Measured text may grow this reserve downward; expanding account details must not move it.
    val profileStatsTopProvider: () -> Float = { identityStatsTopPx }
    // Finish identity collapse before the statistics enter the toolbar.
    val profileCollapseRangeProvider: () -> Float = {
        with(density) {
            (profileStatsTopProvider() - (statusBarTopPadding + 56.dp + 24.dp).toPx())
                .coerceAtLeast(1f)
        }
    }
    val profileExpandedFractionProvider: () -> Float = {
        if (mobileListState.firstVisibleItemIndex > 0) 0f else resolveProfileHeaderExpandedFraction(
            mobileListState.firstVisibleItemScrollOffset.toFloat(), profileCollapseRangeProvider(),
        )
    }
    val photoConfiguration = LocalConfiguration.current
    // Pull-down avatar expansion belongs to the portrait phone header.
    // Both MD3/Miuix and both glass states use this same geometry and gesture logic.
    val photoExpansionEnabled = !isTablet &&
        photoConfiguration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE && user.face.isNotBlank()
    val photoState = remember(user.mid, user.face, isTablet, photoConfiguration.orientation, density) {
        ProfileAvatarExpansionState()
    }
    val photoRangeProvider: () -> Float = {
        (photoState.viewportWidthPx - profileStatsTopProvider()).coerceAtLeast(0f)
    }
    val photoFractionProvider: () -> Float = { photoState.fraction(photoRangeProvider()) }
    val photoBottomProvider: () -> Float = { profileStatsTopProvider() + photoState.visualOffsetPx }
    val backgroundMotion = ProfileBackgroundMotion(
        scrollOffsetPx = {
            if (mobileListState.firstVisibleItemIndex > 0) Float.POSITIVE_INFINITY
            else mobileListState.firstVisibleItemScrollOffset.toFloat()
        },
        pullOffsetPx = { photoState.visualOffsetPx },
        avatarShiftYPx = {
            with(density) {
                -(statusBarTopPadding + (PROFILE_EXPANDED_AVATAR_TOP_DP + 29f).dp).toPx() *
                    (1f - profileExpandedFractionProvider())
            }
        },
    )
    val photoConnection = rememberProfileAvatarExpansionConnection(photoState, mobileListState, photoExpansionEnabled, photoRangeProvider)
    val photoScope = rememberCoroutineScope()
    val photoMotion = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion() &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val collapsePhoto: () -> Unit = {
        photoScope.launch { photoState.settle(false, photoRangeProvider(), 0f, density.density, photoMotion) }
    }
    val photoVisible by remember(photoState) { derivedStateOf { photoState.offsetPx > 0f } }
    androidx.activity.compose.BackHandler(enabled = photoVisible) { collapsePhoto() }
    LaunchedEffect(photoState, scrollToTopRequestId, space.selectedTab) { photoState.reset() }
    val profileHeaderFling = rememberProfileHeaderFlingBehavior(mobileListState, profileCollapseRangeProvider)
    val isMobileScrolling by remember(mobileListState) {
        derivedStateOf { mobileListState.isScrollInProgress }
    }
    ObserveProfileScrollToTop(
        requestId = scrollToTopRequestId,
        listState = if (isTablet) tabletFeedListState else mobileListState,
        scrollState = if (isTablet) tabletRailScrollState else null
    )

    var mobileFeedRootTopPx by remember { mutableStateOf(0f) }
    var mobileTabsRootBottomPx by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(photoConnection)
            .onSizeChanged {
                if (photoState.viewportWidthPx != it.width) {
                    photoState.reset()
                    photoState.viewportWidthPx = it.width
                }
            },
    ) {
        if (isTablet) {
            // 悬浮底栏会盖在双栏内容上，列表尾部需要与手机分支同等的让位。
            val bottomDockClearance = LocalBottomBarContentPadding.current
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
                        .verticalScroll(tabletRailScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileSpaceHeader(
                        user = user,
                        editableAccount = editableAccount,
                        onAvatarClick = openAvatarPreview,
                        avatarSourceRect = avatarSourceRect,
                        compact = true,
                        heroChrome = heroChrome,
                        showWallpaperAction = true,
                        showEditProfileButton = showProfileEditButton,
                        onEditClick = { showEditDialog = true },
                        onWallpaperActionClick = { showWallpaperActionSheet = true },
                        onFollowingClick = onFollowingClick,
                        onFansClick = onFansClick,
                        onDynamicsClick = { onTabSelected(ProfileSpaceMainTab.DYNAMIC) },
                        modifier = Modifier.heightIn(min = tabletWallpaperRevealHeight)
                    )
                    ProfileQuickAccessDashboard(
                        favoriteFolderShortcuts = favoriteFolderShortcuts,
                        onDownloadClick = onDownloadClick,
                        onHistoryClick = onHistoryClick,
                        onSubscriptionClick = onSubscriptionClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onFavoriteClick = onFavoriteClick,
                        onFavoriteFolderClick = onFavoriteFolderClick,
                        contentColor = contentChrome.onSurfaceColor,
                    )
                    Spacer(modifier = Modifier.height(bottomDockClearance))
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
                    onContributionRetry = onContributionRetry,
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
                    contentPadding = PaddingValues(
                        top = tabletWallpaperRevealHeight,
                        bottom = maxOf(48.dp, bottomDockClearance)
                    ),
                    listState = tabletFeedListState,
                    pageListStates = tabletPageListStates,
                    pagerState = pagerState,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .profileProgressiveBackdrop(progressiveTopChrome)
                    .then(if (hazeState != null) Modifier.hazeSourceCompat(hazeState) else Modifier)
                    .globalWallpaperAwareBackground(colorScheme.surface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    CompositionLocalProvider(LocalProfileBackgroundMotion provides backgroundMotion) {
                        captureBackground()
                    }
                }
                ProfileSpaceTabsHost(
                    sharedGlassTabs = LocalAppThemeConfig.current.liquidGlassEnabled,
                    currentPage = { pagerState.currentPage },
                    tabsVisible = {
                        mobilePageListStates.getValue(profileTabs[pagerState.currentPage].tab)
                            .firstVisibleItemIndex <= 1
                    },
                    tabs = { tabModifier ->
                        ProfileSpaceTabs(space.selectedTab, onTabSelected, tabModifier, pagerState)
                    },
                    nativeTabs = { page, tabModifier ->
                        ProfileSpaceTabs(profileTabs[page].tab, onTabSelected, tabModifier, pagerState)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { tabSlot ->
                AppHorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    key = { profileTabs[it].tab },
                    modifier = Modifier
                        .fillMaxSize()
                        .contentTabPagerGestures(
                            pager = pagerState,
                            enabled = true,
                            contentTopPx = { mobileTabsRootBottomPx - mobileFeedRootTopPx },
                        ),
                ) { page ->
                val mobileListState = mobilePageListStates.getValue(profileTabs[page].tab)
                val space = space.copy(selectedTab = profileTabs[page].tab)
                val profileHeaderFling = rememberProfileHeaderFlingBehavior(mobileListState, profileCollapseRangeProvider)
                LazyColumn(
                    state = mobileListState,
                    flingBehavior = profileHeaderFling,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            if (page == selectedPage) mobileFeedRootTopPx = it.boundsInRoot().top
                        },
                    contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 120.dp)
                ) {
                item {
                    Box(
                        Modifier
                            .stationaryPagerHeader(pagerState, page)
                            .layout { measurable, constraints ->
                                val extra = photoState.visualOffsetPx.toInt()
                                val child = measurable.measure(constraints)
                                layout(child.width, child.height + extra) { child.placeRelative(0, extra) }
                            },
                    ) {
                    ProfileSpaceHeroHeader(
                        user = user,
                        editableAccount = editableAccount,
                        onAvatarClick = openAvatarPreview,
                        avatarSourceRect = avatarSourceRect,
                        heroChrome = heroChrome,
                        layoutTokens = layoutTokens,
                        showWallpaperAction = false,
                        showEditProfileButton = showProfileEditButton,
                        onEditClick = { showEditDialog = true },
                        onWallpaperActionClick = { showWallpaperActionSheet = true },
                        onFollowingClick = onFollowingClick,
                        onFansClick = onFansClick,
                        onDynamicsClick = { onTabSelected(ProfileSpaceMainTab.DYNAMIC) },
                        showIdentity = false,
                        identityReserve = with(density) { profileStatsTopProvider().toDp() },
                        topClipProvider = {
                            with(density) { (statusBarTopPadding + 56.dp).toPx() } +
                                mobileListState.firstVisibleItemScrollOffset.toFloat() - photoState.visualOffsetPx
                        },
                    )
                }
                }
                item {
                    ProfileContentSheet(
                        contentChrome = contentChrome,
                        layoutTokens = layoutTokens
                    ) {
                        Box(Modifier.stationaryPagerHeader(pagerState, page)) {
                        ProfileQuickAccessDashboard(
                            favoriteFolderShortcuts = favoriteFolderShortcuts,
                            onDownloadClick = onDownloadClick,
                            onHistoryClick = onHistoryClick,
                            onSubscriptionClick = onSubscriptionClick,
                            onWatchLaterClick = onWatchLaterClick,
                            onFavoriteClick = onFavoriteClick,
                            onFavoriteFolderClick = onFavoriteFolderClick,
                            contentColor = contentChrome.onSurfaceColor,
                        )
                        }
                        tabSlot(
                            page,
                            Modifier.stationaryPagerHeader(pagerState, page).onGloballyPositioned {
                                if (page == selectedPage) mobileTabsRootBottomPx = it.boundsInRoot().bottom
                            },
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
                            onContributionRetry = onContributionRetry,
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
                } // AppHorizontalPager
                } // ProfileSpaceTabsHost

            }
            ProfilePinnedSpaceTopBarBackground(
                progressiveTopChrome = progressiveTopChrome,
                statusBarTopPadding = statusBarTopPadding,
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
            ProfilePinnedSpaceTopBar(
                listState = mobileListState,
                heroTextColor = heroChrome.textColor,
                contentColor = contentChrome.onSurfaceColor,
                statusBarTopPadding = statusBarTopPadding,
                privacyModeEnabled = privacyModeEnabled,
                onBack = if (photoVisible) collapsePhoto else onBack,
                onSearchClick = onSearchClick,
                onInboxClick = onInboxClick,
                onPrivacyClick = onPrivacyClick,
                onAccountManageClick = onAccountManageClick,
                onThemeClick = onThemeClick,
                onSettingsClick = onSettingsClick,
                onWallpaperClick = { showWallpaperActionSheet = true },
                onEditClick = { showEditDialog = true },
                photoFractionProvider = photoFractionProvider,
                // Only transparent actions sit above the avatar; the chrome background stays below it.
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
            ProfileIdentityHeader(
                user = user,
                heroChrome = heroChrome,
                collapsedTextColor = contentChrome.onSurfaceColor,
                statusBarTopPadding = statusBarTopPadding,
                expandedFractionProvider = profileExpandedFractionProvider,
                statsTopProvider = profileStatsTopProvider,
                collapseRangeProvider = profileCollapseRangeProvider,
                onIdentityMeasured = { nameHeight, uidHeight ->
                    identityStatsTopPx = resolveProfileIdentityStatsTopPx(
                        statusBarTopPx = with(density) { statusBarTopPadding.toPx() },
                        density = density.density,
                        nameHeightPx = nameHeight,
                        uidHeightPx = uidHeight,
                    )
                },
                listState = mobileListState,
                flingBehavior = profileHeaderFling,
                onAvatarClick = openAvatarPreview,
                avatarSourceRect = avatarSourceRect,
                photoState = photoState.takeIf { photoExpansionEnabled },
                photoFractionProvider = photoFractionProvider,
                photoBottomProvider = photoBottomProvider,
                photoScrollConnection = photoConnection,
                onCollapsePhoto = collapsePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .layout { measurable, constraints ->
                        // The native avatar host must stop above the scrolling statistics.
                        // A full-hero AndroidView intercepts taps even in its transparent area.
                        val toolbarHeight = (statusBarTopPadding + 56.dp).roundToPx()
                        val statsTop = if (mobileListState.firstVisibleItemIndex > 0) {
                            toolbarHeight
                        } else {
                            (profileStatsTopProvider() + photoState.visualOffsetPx).toInt() - mobileListState.firstVisibleItemScrollOffset
                        }
                        val height = statsTop.coerceIn(
                            toolbarHeight.coerceAtMost(constraints.maxHeight),
                            constraints.maxHeight,
                        )
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = height, maxHeight = height)
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
            )
        }
    }
}

@Composable
private fun ProfilePinnedSpaceTopBarBackground(
    progressiveTopChrome: ProfileProgressiveTopChrome,
    statusBarTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    BiliPaiImmersiveTopBar(
        backdrop = progressiveTopChrome.backdrop,
        enabled = progressiveTopChrome.enabled,
        headerBlurActive = false,
        fadeEnabled = if (progressiveTopChrome.enabled) false else null,
        opaqueBackgroundFallback = false,
        modifier = modifier,
    ) {
        Spacer(Modifier.fillMaxWidth().height(statusBarTopPadding + 56.dp))
    }
}

@Composable
private fun ProfilePinnedSpaceTopBar(
    listState: LazyListState,
    heroTextColor: Color,
    contentColor: Color,
    statusBarTopPadding: Dp,
    privacyModeEnabled: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onInboxClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAccountManageClick: () -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onWallpaperClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    photoFractionProvider: () -> Float = { 0f },
) {
    val openLedger = LocalProfileLedgerNavigation.current
    val openComments = LocalProfileCommentsNavigation.current
    val topChromeScrim = remember(listState) {
        derivedStateOf {
            resolveProfilePinnedTopChromeScrim(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
    val topBarIconColor = androidx.compose.ui.graphics.lerp(
        heroTextColor,
        contentColor,
        topChromeScrim.value,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarTopPadding)
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var showQrAuthorization by remember { mutableStateOf(false) }
        if (showQrAuthorization) {
            OfficialQrAuthorizationDialog(onDismiss = { showQrAuthorization = false })
        }
        AppIconButton(onClick = { showQrAuthorization = true }) {
            ProfilePhotoToolbarIcon(Icons.Rounded.QrCodeScanner, "扫码授权登录", topBarIconColor, photoFractionProvider)
        }
        Spacer(modifier = Modifier.weight(1f))
        AppWindowActionMenu(
            groups = listOf(
                listOf(
                    AppWindowAction(label = "搜索", onClick = onSearchClick),
                    AppWindowAction(label = "消息", onClick = onInboxClick),
                    AppWindowAction(label = "编辑资料", onClick = onEditClick),
                    AppWindowAction(
                        label = "账号与记录",
                        children = listOf(
                            AppWindowAction(label = "切换账号", onClick = onAccountManageClick),
                        ) + ledgerMenuActions(openLedger, openComments),
                    ),
                ),
                listOf(
                    AppWindowAction(label = if (privacyModeEnabled) "关闭无痕模式" else "开启无痕模式", onClick = onPrivacyClick),
                    AppWindowAction(
                        label = "主题与背景",
                        children = listOf(
                            AppWindowAction(label = "切换主题", onClick = onThemeClick),
                            AppWindowAction(label = "更换背景", onClick = onWallpaperClick),
                        ),
                    ),
                    AppWindowAction(label = "设置", onClick = onSettingsClick),
                    AppWindowAction(label = "返回", onClick = onBack),
                ),
            ),
        ) {
            ProfilePhotoToolbarIcon(Icons.Rounded.MoreVert, "更多", topBarIconColor, photoFractionProvider)
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
    onContributionRetry: () -> Unit,
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
    contentPadding: PaddingValues,
    listState: LazyListState,
    pageListStates: Map<ProfileSpaceMainTab, LazyListState>,
    pagerState: PagerState,
) {
    var feedRootTopPx by remember { mutableStateOf(0f) }
    var tabsRootBottomPx by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    val tabs = remember { defaultProfileSpaceTabs() }
    ProfileSpaceTabsHost(
        sharedGlassTabs = LocalAppThemeConfig.current.liquidGlassEnabled,
        currentPage = { pagerState.currentPage },
        tabsVisible = {
            pageListStates.getValue(tabs[pagerState.currentPage].tab).firstVisibleItemIndex == 0
        },
        tabs = { tabModifier ->
            ProfileSpaceTabs(space.selectedTab, onTabSelected, tabModifier, pagerState)
        },
        nativeTabs = { page, tabModifier ->
            ProfileSpaceTabs(tabs[page].tab, onTabSelected, tabModifier, pagerState)
        },
        modifier = modifier.fillMaxHeight(),
    ) { tabSlot ->
    AppHorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        key = { tabs[it].tab },
        modifier = Modifier.fillMaxSize().contentTabPagerGestures(
            pager = pagerState,
            enabled = true,
            contentTopPx = {
                if (listState.firstVisibleItemIndex > 0) 0f else tabsRootBottomPx - feedRootTopPx
            },
        ),
    ) { page ->
    val isPageActive = tabs[page].tab == space.selectedTab
    val space = space.copy(selectedTab = tabs[page].tab)
    val listState = pageListStates.getValue(space.selectedTab)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { if (isPageActive) feedRootTopPx = it.boundsInRoot().top },
        contentPadding = contentPadding
    ) {
        item {
            tabSlot(
                page,
                Modifier.stationaryPagerHeader(pagerState, page).onGloballyPositioned {
                    if (isPageActive) tabsRootBottomPx = it.boundsInRoot().bottom
                },
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
                onContributionRetry = onContributionRetry,
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
    } // AppHorizontalPager
    } // ProfileSpaceTabsHost
}

@Composable
private fun ProfileTopActions(
    compact: Boolean,
    privacyModeEnabled: Boolean,
    onSearchClick: () -> Unit,
    onInboxClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAccountClick: () -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    tint: Color,
    onWallpaperClick: (() -> Unit)? = null,
) {
    val openLedger = LocalProfileLedgerNavigation.current
    val openComments = LocalProfileCommentsNavigation.current
    var showQrAuthorization by remember { mutableStateOf(false) }
    if (showQrAuthorization) {
        OfficialQrAuthorizationDialog(onDismiss = { showQrAuthorization = false })
    }
    AppIconButton(onClick = { showQrAuthorization = true }) {
        AppIcon(Icons.Rounded.QrCodeScanner, contentDescription = "扫码授权登录", tint = tint)
    }
    AppIconButton(onClick = onSearchClick) {
        AppIcon(Icons.Rounded.Search, contentDescription = "搜索", tint = tint)
    }
    AppIconButton(onClick = onInboxClick) {
        AppIcon(rememberAppInboxIcon(), contentDescription = "消息", tint = tint)
    }
    if (!compact) {
        AppIconButton(onClick = onPrivacyClick) {
            AppIcon(
                rememberAppLockIcon(),
                contentDescription = if (privacyModeEnabled) "关闭无痕" else "开启无痕",
                tint = tint,
            )
        }
        AppIconButton(onClick = onAccountClick) {
            AppIcon(rememberAppProfileAddIcon(), contentDescription = "切换账号", tint = tint)
        }
        AppIconButton(onClick = onThemeClick) {
            AppIcon(Icons.Rounded.DarkMode, contentDescription = "切换主题", tint = tint)
        }
        AppIconButton(onClick = onSettingsClick) {
            AppIcon(rememberAppSettingsIcon(), contentDescription = "设置", tint = tint)
        }
    } else {
        AppWindowActionMenu(
            groups = listOf(
                buildList {
                    add(
                        AppWindowAction(
                            label = if (privacyModeEnabled) "关闭无痕模式" else "开启无痕模式",
                            onClick = onPrivacyClick,
                        )
                    )
                    add(AppWindowAction(label = "切换账号", onClick = onAccountClick))
                    addAll(ledgerMenuActions(openLedger, openComments))
                    add(AppWindowAction(label = "切换主题", onClick = onThemeClick))
                    onWallpaperClick?.let { action ->
                        add(AppWindowAction(label = "更换背景", onClick = action))
                    }
                    add(AppWindowAction(label = "设置", onClick = onSettingsClick))
                }
            ),
        ) {
                AppIcon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = tint)
        }
    }
    if (!compact && (openLedger != null || openComments != null)) {
        AppWindowActionMenu(groups = listOf(ledgerMenuActions(openLedger, openComments))) {
            AppIcon(Icons.Rounded.MoreVert, contentDescription = "评论与账号明细", tint = tint)
        }
    }

}

@Composable
private fun ProfileQuickAccessDashboard(
    favoriteFolderShortcuts: List<ProfileFavoriteFolderShortcut>,
    onDownloadClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSubscriptionClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFavoriteFolderClick: (Long, Long, String) -> Unit,
    contentColor: Color,
) {
    val shortcuts = listOf(
        Triple("离线缓存", rememberAppDownloadIcon(), onDownloadClick),
        Triple("历史", rememberAppHistoryIcon(), onHistoryClick),
        Triple("订阅", rememberAppBookmarkIcon(), onSubscriptionClick),
        Triple("稍后再看", rememberAppWatchLaterIcon(), onWatchLaterClick),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shortcuts.forEach { (label, icon, action) ->
                AppSurface(
                    onClick = action,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(icon, contentDescription = null, tint = AppContentColors.accent())
                        AppText(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (favoriteFolderShortcuts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    "收藏夹",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                AppTextButton(onClick = onFavoriteClick) { AppText("全部") }
            }
            ProfileFavoriteFolderShortcutGrid(
                shortcuts = favoriteFolderShortcuts,
                onFavoriteFolderClick = onFavoriteFolderClick,
                contentColor = contentColor,
                compactHorizontal = true,
                onMoreClick = onFavoriteClick,
            )
        }
    }
}

@Composable
private fun ProfileSpaceHeroHeader(
    user: UserState,
    editableAccount: ProfileEditableAccountState,
    onAvatarClick: () -> Unit,
    avatarSourceRect: androidx.compose.runtime.MutableState<Rect?>,
    heroChrome: ProfileHeroChrome,
    layoutTokens: ProfileLayoutTokens,
    showWallpaperAction: Boolean,
    showEditProfileButton: Boolean = false,
    onEditClick: () -> Unit,
    onWallpaperActionClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onDynamicsClick: () -> Unit,
    showIdentity: Boolean = true,
    topClipProvider: () -> Float = { 0f },
    identityReserve: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 215.dp,
) {
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    val colorScheme = MaterialTheme.colorScheme
    // The separate phone identity overlay owns wallpaper/photo colors. Statistics
    // use an opaque page surface in both native themes and both glass states.
    val statisticsChrome = if (!showIdentity) resolveProfileHeroChrome(
        hasWallpaper = false,
        isDarkTheme = colorScheme.surface.luminance() < 0.5f,
        onSurfaceColor = colorScheme.onSurface,
        onSurfaceVariantColor = colorScheme.onSurfaceVariant,
    ) else heroChrome
    val heroHeight = resolveProfileHeroHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        widthSizeClass = windowSizeClass.widthSizeClass
    ).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = heroHeight)
            .drawWithContent {
                // Clip only hero metadata; the feed remains available to the backdrop blur.
                clipRect(top = topClipProvider().coerceIn(0f, size.height)) {
                    if (!showIdentity) {
                        val top = identityReserve.toPx().coerceIn(0f, size.height)
                        drawRect(
                            color = colorScheme.surface,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
                        )
                    }
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        ProfileSpaceHeader(
            user = user,
            editableAccount = editableAccount,
            onAvatarClick = onAvatarClick,
            avatarSourceRect = avatarSourceRect,
            compact = false,
            showIdentity = showIdentity,
            heroChrome = statisticsChrome,
            showWallpaperAction = showWallpaperAction,
            showEditProfileButton = showEditProfileButton,
            onEditClick = onEditClick,
            onWallpaperActionClick = onWallpaperActionClick,
            onFollowingClick = onFollowingClick,
            onFansClick = onFansClick,
            onDynamicsClick = onDynamicsClick,
            modifier = Modifier
                .align(if (showIdentity) Alignment.BottomStart else Alignment.TopStart)
                .padding(
                    top = if (showIdentity) 72.dp else identityReserve,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // An opaque, uniform content plane meets the
            // clipped colored header. It does not inherit the header's gradient.
            .background(contentChrome.surfaceColor)
            .padding(
                top = layoutTokens.contentSheetTopPaddingDp.dp,
                bottom = layoutTokens.contentSheetBottomPaddingDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun ProfileSpaceHeader(
    user: UserState,
    editableAccount: ProfileEditableAccountState,
    onAvatarClick: () -> Unit,
    avatarSourceRect: androidx.compose.runtime.MutableState<Rect?>,
    compact: Boolean,
    heroChrome: ProfileHeroChrome,
    showWallpaperAction: Boolean,
    showEditProfileButton: Boolean = false,
    onEditClick: () -> Unit,
    onWallpaperActionClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onDynamicsClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIdentity: Boolean = true,
) {
    val openLedger = LocalProfileLedgerNavigation.current
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
        if (showIdentity) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = user.face,
                    contentDescription = "${user.name}的头像，查看大图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .imagePreviewSourceBounds(avatarSourceRect)
                        .alpha(if (isImagePreviewSourceHidden(avatarSourceRect.value)) 0f else 1f)
                        .clip(CircleShape)
                        .border(2.dp, heroChrome.avatarBorderColor, CircleShape)
                        .clickable(onClick = onAvatarClick),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = user.name,
                        color = textColor,
                        fontSize = (if (compact) 22 else 24).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    UserLevelBadge(level = user.level)
                    if (user.isVip) com.android.purebilibili.core.ui.components.UserVipBadge(label = user.vipLabel, compact = true)
                }
                AppText("UID ${user.mid}", color = secondaryColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileSpaceStat("动态", user.dynamic, textColor, onClick = onDynamicsClick)
            ProfileSpaceStat("关注", user.following, textColor, onClick = onFollowingClick)
            ProfileSpaceStat("粉丝", user.follower, textColor, onClick = onFansClick)
            val identityMotionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
                !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
            val identityArrowRotation = androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (identityExpanded) 180f else 0f,
                animationSpec = if (identityMotionEnabled) androidx.compose.animation.core.spring(
                    dampingRatio = 1f, stiffness = 700f,
                ) else androidx.compose.animation.core.snap(),
                label = "profileIdentityArrow",
            )
            AppIconButton(onClick = { identityExpanded = !identityExpanded }) {
                AppIcon(
                    imageVector = rememberAppChevronDownIcon(),
                    contentDescription = if (identityExpanded) "收起个人资料" else "展开个人资料",
                    tint = secondaryColor,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = identityArrowRotation.value },
                )
            }
        }
        val levelProgress = remember(
            user.currentLevelMinExp,
            user.currentLevelExp,
            user.nextLevelExp,
        ) {
            resolveProfileLevelProgress(
                currentMinExp = user.currentLevelMinExp,
                currentExp = user.currentLevelExp,
                nextExp = user.nextLevelExp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "硬币 ${FormatUtils.formatStat(user.coin.toLong())}",
                    modifier = if (openLedger != null) Modifier.clickable { openLedger(AccountLedgerKind.COINS) } else Modifier,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                )
                AppText(
                    text = "经验 ${levelProgress.currentExp}/${levelProgress.nextExp}",
                    modifier = if (openLedger != null) Modifier.clickable { openLedger(AccountLedgerKind.EXPERIENCE) } else Modifier,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                )
            }
            AppLinearProgressIndicator(
                progress = { levelProgress.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = AppContentColors.accent(),
                trackColor = secondaryColor.copy(alpha = 0.22f),
            )
        }
        ProfileIdentityDrawer(
            meta = meta,
            expanded = identityExpanded,
            contentColor = textColor,
            secondaryColor = secondaryColor,
            containerColor = metaChipContainer,
            borderColor = metaChipBorder,
            onEditClick = onEditClick,
        )
        if (showEditProfileButton || showWallpaperAction) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showEditProfileButton) {
                    AppOutlinedButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = heroChrome.actionButtonContentColor),
                        border = BorderStroke(1.dp, heroChrome.actionButtonContentColor.copy(alpha = heroChrome.actionButtonBorderAlpha)),
                        shape = AppShapes.container(ContainerLevel.Card)
                    ) {
                        AppText("编辑资料")
                    }
                }
                if (showWallpaperAction) {
                    ProfileWallpaperMenuButton(
                        contentColor = heroChrome.actionButtonContentColor,
                        borderColor = heroChrome.actionButtonContentColor.copy(alpha = heroChrome.actionButtonBorderAlpha),
                        onClick = onWallpaperActionClick,
                        modifier = if (!showEditProfileButton) Modifier.weight(1f) else Modifier
                    )
                }
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
    borderColor: Color,
    onEditClick: (() -> Unit)? = null
) {
    val shape = AppShapes.container(ContainerLevel.Card)
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(if (onEditClick != null) Modifier.clickable(onClick = onEditClick) else Modifier),
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
                AppText(
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
    AppSurface(
        shape = AppShapes.container(ContainerLevel.Pill),
        color = containerColor,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        AppText(
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
private fun ProfileSpaceStat(
    label: String,
    value: Int,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val width = if (LocalConfiguration.current.screenWidthDp < 360) 60.dp else 72.dp
    Column(
        modifier = modifier
            .width(width)
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) Modifier.appGentleActionClickable(
                    shape = AppShapes.container(ContainerLevel.Card),
                    onClick = onClick,
                ) else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = FormatUtils.formatStat(value.toLong()),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        AppText(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun ProfileSpaceTabs(
    selectedTab: ProfileSpaceMainTab,
    onTabSelected: (ProfileSpaceMainTab) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState,
) {
    val tabs = remember { defaultProfileSpaceTabs() }
    val chromeSpec = remember { resolveProfileSpaceTabChromeSpec() }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    AppThemeAdaptiveTabRow(
        // Glass indicator fills the measured slot; native geometry keeps its defaults.
        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
        options = tabs.map { AppSegmentOption(it.tab, it.title) },
        selectedValue = tabs[selectedIndex].tab,
        onSelectionChange = onTabSelected,
        indicatorPositionProvider = { pagerState.tabIndicatorPosition },
        isScrollInProgressProvider = { pagerState.isScrollInProgress },
        scrollable = false,
        dragSelectionEnabled = tabs.size > 1,
        tapPressRefractionEnabled = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = chromeSpec.rowHorizontalInsetDp.dp, vertical = 6.dp),
    )
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
    onContributionRetry: () -> Unit,
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
        ProfileSpaceMainTab.CONTRIBUTION -> ProfileVideoList(
            videos = space.contributionVideos,
            loadState = space.contributionLoadState,
            onVideoClick = onVideoClick,
            onRetry = onContributionRetry
        )
        ProfileSpaceMainTab.FAVORITE -> ProfileFavoriteFolderList(user.mid, space.favoriteFolders, onFavoriteFolderClick)
        ProfileSpaceMainTab.BANGUMI -> ProfileBangumiList(
            items = space.bangumiItems,
            onBangumiClick = onBangumiClick,
            contentChrome = contentChrome,
        )
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
            contributionVideos = space.contributionVideos,
            includeDashboardOwnedSections = false,
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
        AppText(
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
                onClick = {
                    resolveSpaceAggregateVideoId(video)?.let(onVideoClick)
                }
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
    textColor: Color = AppContentColors.primaryText(),
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = if (count > 0) "$title  ${FormatUtils.formatStat(count.toLong())}" else title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            AppTextButton(onClick = onMoreClick) {
                AppText(
                    text = "查看更多",
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
    val cardShape = AppShapes.borderedContainer(ContainerLevel.Card)
    val cardWidth = cardTokens.widthDp.dp
    val coverHeight = resolveProfileCardCoverHeightDp(cardTokens).dp
    val cardHeight = resolveProfileCardHeightDp(cardTokens).dp
    AppSurface(
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
                    AppIcon(
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
                AppText(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentChrome.onSurfaceColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppText(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileBangumiList(
    items: List<FollowBangumiItem>,
    onBangumiClick: (Long, Long) -> Unit,
    contentChrome: ProfileContentChrome,
) {
    if (items.isEmpty()) {
        ProfileSpaceEmpty("暂无追番")
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileVideoList(
    videos: List<SpaceVideoItem>,
    loadState: ProfileContributionLoadState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (resolveProfileContributionContentState(loadState, videos.isNotEmpty())) {
        ProfileContributionContentState.LOADING -> {
            Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                AdaptiveLoadingIndicator()
            }
            return
        }
        ProfileContributionContentState.EMPTY -> {
            ProfileSpaceEmpty("暂无投稿")
            return
        }
        ProfileContributionContentState.ERROR -> {
            Column(
                modifier = modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppText("投稿加载失败", color = AppContentColors.secondaryText())
                AppOutlinedButton(onClick = onRetry) { AppText("重试") }
            }
            return
        }
        ProfileContributionContentState.CONTENT -> Unit
    }
    val listLayout = rememberVideoListLayoutControl(defaultSingleColumn = true)
    val columns = resolveVideoListColumns(
        listLayout.singleColumn, LocalConfiguration.current.screenWidthDp.toFloat(),
    )
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            VideoListLayoutToggle(
                singleColumn = listLayout.singleColumn,
                onClick = listLayout.toggle,
            )
        }
        BoxWithConstraints(
            modifier = if (columns == 1) {
                Modifier.responsiveContentWidth(com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth())
            } else Modifier.fillMaxWidth(),
        ) {
            val cardWidth = (maxWidth - 10.dp * (columns - 1)) / columns
            LookaheadScope {
                FlowRow(
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    videos.forEach { video ->
                        key(video.bvid, video.aid) {
                            Box(modifier = Modifier.videoListBoundsAnimation(
                                scope = this@LookaheadScope,
                                targetModifier = Modifier.width(cardWidth),
                            )) {
                                if (columns == 1) {
                                    PersonalMediaCardFrame(
                                        horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
                                        headlineContent = {
                                            AppText(
                                                video.title,
                                                style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title,
                                                maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                                                overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
                                            )
                                        },
                                        supportingContent = {
                                            com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow(
                                                playText = FormatUtils.formatStat(video.play.toLong()),
                                                danmakuText = "",
                                            )
                                        },
                                        coverContent = {
                                            AsyncImage(
                                                model = video.pic,
                                                contentDescription = video.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        },
                                        coverOverlayContent = {
                                            com.android.purebilibili.core.ui.components.VideoStatusBadges(
                                                labels = video.statusLabels,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                            )
                                            com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText(
                                                text = video.length,
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                                            )
                                        },
                                        onClick = { video.bvid.takeIf { it.isNotBlank() }?.let(onVideoClick) },
                                    )
                                } else {
                                    PersonalMediaCardFrame(
                                        stacked = true,
                                        headlineContent = {
                                            AppText(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            AppText("${FormatUtils.formatStat(video.play.toLong())} 播放 · ${video.length}",
                                                style = MaterialTheme.typography.bodySmall)
                                        },
                                        coverContent = {
                                            AsyncImage(model = video.pic, contentDescription = null,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        },
                                        onClick = { video.bvid.takeIf { it.isNotBlank() }?.let(onVideoClick) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                AppHorizontalDivider(
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
    val deleteIcon = rememberAppDeleteIcon()
    val linkIcon = rememberAppLinkIcon()
    val context = LocalContext.current
    val deleteAction = remember(item) { resolveProfileDynamicDeleteAction(item) }
    var showMoreMenu by remember(item.id_str) { mutableStateOf(false) }
    var pendingDeleteAction by remember(item.id_str) { mutableStateOf<DynamicDeleteAction?>(null) }

    pendingDeleteAction?.let { action ->
        AppAlertDialog(
            onDismissRequest = { pendingDeleteAction = null },
            icon = { AppIcon(deleteIcon, contentDescription = null) },
            title = { AppText(action.title) },
            text = { AppText(action.content) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        pendingDeleteAction = null
                        onDeleteClick(action)
                    }
                ) {
                    AppText(action.confirmText, color = AppContentColors.error())
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingDeleteAction = null }) {
                    AppText(action.cancelText)
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
                AppText(
                    text = authorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.accent(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (publishText.isNotBlank()) {
                    AppText(
                        text = publishText,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.secondaryText()
                    )
                }
            }
            com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { showMoreMenu = true }) {
                AppIconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(40.dp)) {
                    AppIcon(
                        imageVector = moreIcon,
                        contentDescription = "更多",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(22.dp)
                    )
                }
                AppDropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = if (com.android.purebilibili.core.ui.components.appGlassMenuAvailable()) Modifier
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    AppDropdownMenuItem(
                        text = { AppText("复制链接") },
                        leadingIcon = {
                            AppIcon(
                                linkIcon,
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
                        AppDropdownMenuItem(
                            text = { AppText(deleteAction.label) },
                            leadingIcon = {
                                AppIcon(
                                    deleteIcon,
                                    contentDescription = null,
                                    tint = AppContentColors.error(),
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
            AppText(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge,
                color = AppContentColors.primaryText(),

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

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Chip),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (authorName.isNotBlank()) {
                AppText(
                    text = "@$authorName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.accent(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (text.isNotBlank()) {
                AppText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),

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
    val dynamicPreviewTextVisible = LocalDynamicImagePreviewTextVisible.current
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
            AppText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.primaryText(),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (cover.isNotBlank()) {
            val coverSourceHidden = isImagePreviewSourceHidden(sourceRect)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cover)
                    .memoryCacheKey(resolveImagePreviewPlaceholderCacheKey(cover) ?: cover)
                    // This image participates in the Hero flight; don't add a second fade
                    // when the source node becomes visible after the return animation.
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .alpha(if (coverSourceHidden) 0f else 1f)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .onGloballyPositioned { coordinates ->
                        sourceRect = coordinates.boundsInWindow()
                    }
                    .then(
                        if (clickableBvid == null && imageUrls.isNotEmpty()) {
                            Modifier.clickable(interactionSource = null, indication = null) {
                                prepareImagePreviewSourceTransition(sourceRect)
                                selectedImageIndex = 0
                            }
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
            sourceCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Chip).value,
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
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        AppText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText(),
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileSpaceListRow(title: String, subtitle: String, imageUrl: String, onClick: () -> Unit) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Chip))
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
                    .clip(AppShapes.container(ContainerLevel.Chip))
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
                    AppIcon(
                        rememberAppFolderIcon(),
                        contentDescription = null,
                        tint = AppContentColors.secondaryText().copy(alpha = 0.42f)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppContentColors.primaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = subtitle.ifBlank { "公开" },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
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
        AppText(text = text, style = MaterialTheme.typography.bodyMedium, color = AppContentColors.secondaryText())
    }
}

@Composable
private fun ProfileEditAccountDialog(
    state: ProfileEditableAccountState,
    mid: Long,
    accountFace: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onEditSign: () -> Unit,
    onEditField: (ProfileAccountField) -> Unit,
    onEditSex: () -> Unit,
    onEditAvatar: () -> Unit
) {
    val context = LocalContext.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("编辑资料") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppText("B 站账号资料", color = AppContentColors.secondaryText(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                ProfileAccountEditRow("头像", "", avatar = accountFace, enabled = !isSaving, onClick = onEditAvatar)
                ProfileAccountEditRow("昵称", state.name.ifBlank { "未展示" }, enabled = !isSaving) {
                    onEditField(ProfileAccountField.NAME)
                }
                ProfileAccountEditRow("性别", state.sex.ifBlank { "未展示" }, enabled = !isSaving, onClick = onEditSex)
                ProfileAccountEditRow("生日", state.birthday.ifBlank { "未展示" }, enabled = !isSaving) {
                    onEditField(ProfileAccountField.BIRTHDAY)
                }
                ProfileAccountEditRow("签名", state.sign.ifBlank { "未设置" }, enabled = !isSaving, onClick = onEditSign)
                ProfileAccountEditRow("UID", mid.toString(), onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UID", mid.toString()))
                    Toast.makeText(context, "UID 已复制", Toast.LENGTH_SHORT).show()
                })
                listOf(
                    "头像挂件" to "https://www.bilibili.com/h5/mall/pendant/home",
                    "哔哩哔哩认证" to "https://account.bilibili.com/official/mobile/home"
                ).forEach { (label, url) ->
                    ProfileAccountEditRow(label, "", onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        },
        confirmButton = { AppTextButton(onClick = onDismiss) { AppText("关闭") } },
    )
}

@Composable
private fun ProfileAccountEditRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    avatar: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AppText(title, color = AppContentColors.primaryText(), style = MaterialTheme.typography.bodyLarge)
        if (!avatar.isNullOrBlank()) {
            // 头像行与其他行一致：头像与右箭头右对齐，标题保持居左。
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(model = avatar, contentDescription = "B 站账号头像", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape))
                Spacer(Modifier.width(4.dp))
                AppIcon(com.android.purebilibili.core.ui.resolveAppChevronForwardIcon(), contentDescription = null,
                    tint = AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value.isNotBlank()) {
                    AppText(
                        text = value,
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                AppIcon(com.android.purebilibili.core.ui.resolveAppChevronForwardIcon(), contentDescription = null,
                    tint = AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileAvatarConfirmDialog(
    image: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("预览头像") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = "裁剪后的头像预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(144.dp).clip(CircleShape),
                )
                AppText(
                    "确认后将提交到 B 站账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
        },
        confirmButton = { AppButton(onClick = onConfirm) { AppText("提交头像") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } },
    )
}

@Composable
private fun ProfileAccountSignDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var sign by remember(initialValue) { mutableStateOf(initialValue) }
    val error = validateProfileSign(sign)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("修改账号签名") },
        text = {
            AppOutlinedTextField(
                value = sign,
                onValueChange = { sign = it.take(70) },
                label = { AppText("签名") },
                minLines = 3,
                maxLines = 4,
                isError = error != null,
                supportingText = { AppText(error ?: "${sign.length}/70") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { AppButton(onClick = { onSave(sign) }, enabled = error == null && sign != initialValue) { AppText("提交") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } },
    )
}

@Composable
private fun ProfileAccountFieldDialog(
    field: ProfileAccountField,
    initialValue: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var value by remember(field, initialValue) { mutableStateOf(initialValue) }
    val isName = field == ProfileAccountField.NAME
    val trimmed = value.trim()
    val valid = if (isName) {
        trimmed.isNotEmpty() && trimmed.length <= 16 && trimmed != initialValue
    } else {
        val date = runCatching { java.time.LocalDate.parse(trimmed) }.getOrNull()
        date != null && !date.isAfter(java.time.LocalDate.now()) && trimmed != initialValue
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(if (isName) "修改账号昵称" else "修改账号生日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppOutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(if (isName) 16 else 10) },
                    label = { AppText(if (isName) "昵称" else "生日（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppText(
                    if (isName) "改名可能需要消耗 6 枚硬币，实际费用和限制以 B 站返回为准。确认后将提交账号修改。"
                    else "请输入有效日期，提交后会修改 B 站账号生日。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { AppButton(onClick = { onSubmit(trimmed) }, enabled = valid) { AppText("确认修改") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } }
    )
}

private data class ProfileProgressiveTopChrome(
    val source: ChromeBackdropSource?,
    val enabled: Boolean,
) {
    val backdrop: top.yukonga.miuix.kmp.blur.Backdrop?
        get() = source?.takeIf { it.isReady }?.backdrop
}

@Composable
private fun rememberProfileProgressiveTopChrome(): ProfileProgressiveTopChrome {
    val themeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val enabled = shouldUseBiliPaiProgressiveTopBlur(
        enabled = themeConfig.progressiveTopBlurEnabled,
        hasBackdrop = true,
    ) && !isLowBlurBudgetForced()
    val source = if (enabled) rememberChromeBackdropSource() else null
    return remember(source, enabled) { ProfileProgressiveTopChrome(source, enabled) }
}

private fun Modifier.profileProgressiveBackdrop(chrome: ProfileProgressiveTopChrome): Modifier {
    // 录制不依赖就绪状态，消费者只在首次录制完成后读取背景。
    return then(chrome.source?.modifier ?: Modifier)
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
    AppSplitLayout(
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
                    .clip(AppShapes.container(ContainerLevel.Floating))
                    .background(glassContainerColor)
                    .border(1.dp, glassBorderColor, AppShapes.container(ContainerLevel.Floating))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText(
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
                        AppOutlinedButton(
                            onClick = onAccountManageClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppText("切换账号")
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        AppButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            AppText("退出登录")
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
private fun MobileProfileContent(
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
    paddingValues: PaddingValues = PaddingValues(0.dp),
    scrollToTopRequestId: Int = 0,
    progressiveTopChrome: ProfileProgressiveTopChrome = ProfileProgressiveTopChrome(null, false),
    captureBackground: @Composable BoxScope.() -> Unit = {},
) {
    val windowSizeClass = LocalWindowSizeClass.current

    // 📸 图片选择器
    // [New] Adjustment Sheet State
    var showAdjustmentSheet by remember { mutableStateOf(false) }
    var tempSelectedUri by remember { mutableStateOf<Uri?>(null) }
    val customBackgroundUri by viewModel.getProfileBgUri().collectAsStateWithLifecycle(initialValue = null
        )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickGalleryVisualMedia()
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
        AppAlertDialog(
            onDismissRequest = { showPhotoPickerDialog = false },
            icon = {
                AppIcon(
                    rememberAppPhotoIcon(),
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                AppText("选择照片", fontWeight = FontWeight.Bold)
            },
            text = {
                AppText(
                    "将打开系统相册选择一张照片作为背景。\n\n" +
                        "📸 仅获取您选中照片的访问权限\n" +
                        "🔒 不会访问您的其他照片",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        showPhotoPickerDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ) {
                    AppText("选择照片")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showPhotoPickerDialog = false }) {
                    AppText("取消")
                }
            }
        )
    }

    val isImmersive = user.topPhoto.isNotEmpty()
    val contentColor = if (isImmersive) Color.White else AppContentColors.primaryText()
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

    val guestListState = rememberLazyListState()
    val isGuestScrolling by remember {
        derivedStateOf { guestListState.isScrollInProgress }
    }
    val guestTopChromeScrim = remember(guestListState) {
        derivedStateOf {
            resolveProfilePinnedTopChromeScrim(
                firstVisibleItemIndex = guestListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = guestListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val showGuestPinnedTitle by remember(guestTopChromeScrim) {
        derivedStateOf { guestTopChromeScrim.value > 0.4f }
    }
    ObserveProfileScrollToTop(
        requestId = scrollToTopRequestId,
        listState = guestListState
    )
    Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .profileProgressiveBackdrop(progressiveTopChrome)
                    .then(if (hazeState != null) Modifier.hazeSourceCompat(hazeState) else Modifier)
                    .globalWallpaperAwareBackground(MaterialTheme.colorScheme.surface),
            ) {
                captureBackground()
            // 📜 滚动内容
            LazyColumn(
                state = guestListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // [Modified] 顶部留白，适配居中顶部栏（64dp + Status Bar ~ 30-40dp）
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
                    contentColor = if (isImmersive) glassContentColor else AppContentColors.primaryText(),
                    borderColor = if (isImmersive) glassBorderColor else null,
                    isLogin = user.isLogin // [New] Pass login status
                )

            }
            // item { Spacer(...) } // Removed
        }
            }

        BiliPaiImmersiveTopBar(
            backdrop = progressiveTopChrome.backdrop,
            enabled = progressiveTopChrome.enabled,
            headerBlurActive = false,
            fadeEnabled = if (progressiveTopChrome.enabled) false else null,
            opaqueBackgroundFallback = false,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
        AppTopBar(
            title = if (showGuestPinnedTitle) "我的" else "",
            style = AppTopBarStyle.CENTERED,
            navigationIcon = {
                AppIconButton(onClick = onBack) {
                    AppIcon(rememberAppBackIcon(), contentDescription = "Back", tint = contentColor)
                }
            },
            actions = {
                AppIconButton(onClick = { showWallpaperActionSheet = true }) {
                    AppIcon(rememberAppPhotoIcon(), contentDescription = "背景装扮", tint = contentColor)
                }
                AppIconButton(onClick = onSettingsClick) {
                    AppIcon(rememberAppSettingsIcon(), contentDescription = "Settings", tint = contentColor)
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
            AppIconButton(onClick = onBack) {
                AppIcon(rememberAppBackIcon(), contentDescription = "Back", tint = Color.White)
            }
            AppIconButton(onClick = onSettingsClick) {
                AppIcon(rememberAppSettingsIcon(), contentDescription = "Settings", tint = Color.White)
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
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Floating),
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

            Spacer(modifier = Modifier.height(AppSpacingTokens.DoubleExtraLarge))

            AppText(
                text = "欢迎使用 BiliPai",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            AppText(
                text = "登录后享受完整的 B站 体验",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = AppSpacingTokens.Small)
            )

            Spacer(modifier = Modifier.height(AppSpacingTokens.TripleExtraLarge))

            //  登录按钮 - 使用现代化渐变按钮
            AppPrimaryButton(
                text = "安全登录",
                onClick = onGoToLogin,
                leadingIcon = loginIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(AppSpacingTokens.Large))

            // 安全提示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(0.5f)
            ) {
                AppIcon(
                    lockIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                AppText(
                    text = "支持扫码登录和网页登录",
                    style = MaterialTheme.typography.bodySmall,
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
        // 自身头像与评论区头像对齐：右下角叠加认证/大会员角标（对齐 PiliPlus 我的页）。
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(user.face)).crossfade(true).placeholder(android.R.color.darker_gray).build(),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            UserAvatarCornerMarkBadge(
                mark = resolveUserAvatarCornerMark(
                    officialType = user.officialVerifyType,
                    vipStatus = if (user.isVip) 1 else 0,
                ),
                modifier = Modifier.align(Alignment.BottomEnd),
                badgeSize = 20.dp,
            )
        }
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
    val shape = AppShapes.container(ContainerLevel.Card)
    AppSurface(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .appGentleActionClickable(
                shape = shape,
                onClick = onClick,
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(
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
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = AppShapes.container(ContainerLevel.Sheet)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppText(
                text = "背景装扮",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppContentColors.primaryText(),
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
    val shape = AppShapes.container(ContainerLevel.Card)
    val contentColor = if (enabled) {
        AppContentColors.primaryText()
    } else {
        AppContentColors.primaryText().copy(alpha = 0.38f)
    }
    AppSurface(
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
            AppSurface(
                modifier = Modifier.size(34.dp),
                shape = AppShapes.container(ContainerLevel.Field),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.82f else 0.46f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            AppText(
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
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val headerBlurEnabled = appThemeConfig.headerBlurEnabled
    val bottomBarBlurEnabled = appThemeConfig.bottomBarBlurEnabled
    val blurEnabled = remember(headerBlurEnabled, bottomBarBlurEnabled) {
        resolveProfileWallpaperActionBlurEnabled(
            headerBlurEnabled = headerBlurEnabled,
            bottomBarBlurEnabled = bottomBarBlurEnabled
        )
    }
    val sectionLabelColor = if (isImmersive) {
        Color.White.copy(alpha = 0.76f)
    } else {
        AppContentColors.secondaryText()
    }
    val buttonColor = if (isImmersive) {
        if (blurEnabled) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
    val contentColor = if (isImmersive) Color.White else AppContentColors.primaryText()
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
            AppText(
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
    val shape = AppShapes.container(ContainerLevel.Floating)
    val effectiveContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    val effectiveSecondaryColor = if (enabled) secondaryColor else secondaryColor.copy(alpha = 0.5f)
    val displayTitle = titleLines.joinToString("\n")
    val titleMaxLines = titleLines.size.coerceAtLeast(1)
    AppSurface(
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
            AppSurface(
                color = Color.White.copy(alpha = if (effectiveContentColor == Color.White) 0.16f else 0.55f),
                shape = AppShapes.container(ContainerLevel.Card)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
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
                AppText(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveContentColor,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
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
    // 对齐 PiliPlus 我的页：年度大会员用户名染 VIP 粉色（透明表头保持白色保证可读）。
    val contentColor = when {
        forceWhite -> Color.White
        user.isVip && user.vipType == 2 -> com.android.purebilibili.core.ui.components.userVipPink()
        else -> AppContentColors.primaryText()
    }
    val shadow = if (forceWhite) Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f) else null

    AppText(
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
        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
        if (user.isVip) {
            // Theme soft pill (same as space header / side drawer), not brand primary pink.
            com.android.purebilibili.core.ui.components.UserVipBadge(
                label = user.vipLabel,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                compact = true,
            )
        } else {
            AppSurface(color = MaterialTheme.colorScheme.surfaceVariant, shape = AppShapes.container(ContainerLevel.Tag)) {
                AppText("正式会员", style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText(), modifier = Modifier.padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Micro))
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
    val textColor = if (transparent) Color.White else AppContentColors.primaryText()
    val labelColor = if (transparent) Color.White.copy(alpha = 0.8f) else AppContentColors.secondaryText()

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
    textColor: Color = AppContentColors.primaryText(),
    labelColor: Color = AppContentColors.secondaryText()
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
        AppText(
            text = count,
            fontWeight = FontWeight.Bold,
            color = textColor,
            style = MaterialTheme.typography.titleMedium.copy(shadow = shadow)
        )
        AppText(
            text = label,
            color = if (useShadow) Color.White.copy(alpha = 0.9f) else labelColor, // Whiter label
            style = MaterialTheme.typography.bodySmall.copy(shadow = shadow) // Apply same shadow to label
        )
    }
}

@Composable
fun VipBannerSection(user: UserState) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium)
            .height(60.dp)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .background(
                colorScheme.tertiaryContainer
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacingTokens.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                AppText(
                    text = if (user.isVip) "尊贵的大会员" else "成为大会员",
                    color = colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                AppText(
                    text = "硬币: ${user.coin}   B币: ${user.bcoin}",
                    color = colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AppText(
                text = if (user.isVip) "续费 >" else "开通 >",
                color = colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.labelMedium
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
    contentColor: Color = AppContentColors.primaryText(),
    borderColor: Color? = null,
    embeddedInPanel: Boolean = false,
    isLogin: Boolean = true,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val queryAicu = com.android.purebilibili.feature.aicu.LocalAicuNavigation.current
    val downloadIcon = rememberAppDownloadIcon()
    val historyIcon = rememberAppHistoryIcon()
    val bookmarkIcon = rememberAppBookmarkIcon()
    val watchLaterIcon = rememberAppWatchLaterIcon()
    val inboxIcon = rememberAppInboxIcon()
    val accountIcon = rememberAppProfileAddIcon()
    val semanticVisualPolicy = rememberAppSemanticVisualPolicy()
    val primaryAccent = semanticVisualPolicy.resolveAccent(
        AppSemanticAccentRole.PRIMARY,
        AppContentColors.accent(),
    )
    val secondaryAccent = semanticVisualPolicy.resolveAccent(
        AppSemanticAccentRole.SECONDARY,
        MaterialTheme.colorScheme.secondary,
    )
    val tertiaryAccent = semanticVisualPolicy.resolveAccent(
        AppSemanticAccentRole.TERTIARY,
        MaterialTheme.colorScheme.tertiary,
    )

    if (isTablet) {
        val items = buildList {
            add(Triple("离线缓存", downloadIcon, onDownloadClick))
            if (showHistoryService) add(Triple("历史记录", historyIcon, onHistoryClick))
            if (showFavoriteService) add(Triple("我的收藏", bookmarkIcon, onFavoriteClick))
            add(Triple("稍后再看", watchLaterIcon, onWatchLaterClick))
            add(Triple("消息中心", inboxIcon, onInboxClick))
            if (queryAicu != null) add(Triple("评论与弹幕查询", historyIcon) { queryAicu(null) })
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
                            AppPreferenceGridItem(
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
                    if (queryAicu != null) {
                        ProfileServiceRow(
                            icon = historyIcon,
                            title = "评论与弹幕查询",
                            onClick = { queryAicu(null) },
                            iconTint = secondaryAccent,
                            textColor = contentColor,
                        )
                        ProfileServiceDivider(contentColor)
                    }
                    ProfileServiceRow(
                        icon = downloadIcon,
                        title = "离线缓存",
                        onClick = onDownloadClick,
                        iconTint = AppContentColors.accent(),
                        textColor = contentColor,
                    )
                    ProfileServiceDivider(contentColor)
                    if (showHistoryService) {
                        ProfileServiceRow(
                            icon = historyIcon,
                            title = "历史记录",
                            onClick = onHistoryClick,
                            iconTint = primaryAccent,
                            textColor = contentColor,
                        )
                        ProfileServiceDivider(contentColor)
                    }
                    if (showFavoriteService) {
                        ProfileServiceRow(
                            icon = bookmarkIcon,
                            title = "我的收藏",
                            onClick = onFavoriteClick,
                            iconTint = tertiaryAccent,
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
                        iconTint = tertiaryAccent,
                        textColor = contentColor,
                    )
                    ProfileServiceDivider(contentColor)
                    ProfileServiceRow(
                        icon = inboxIcon,
                        title = "消息中心",
                        onClick = onInboxClick,
                        iconTint = secondaryAccent,
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
            AppSurface(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .clip(AppShapes.container(ContainerLevel.Floating)),
                color = containerColor,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp // Ensure no extra overlay
            ) {
                Column {
                    if (queryAicu != null) {
                        AppPreference(icon = historyIcon, title = "评论与弹幕查询",
                            onClick = { queryAicu(null) }, iconTint = secondaryAccent, textColor = contentColor)
                    }
                    AppPreference(
                        icon = downloadIcon,
                        title = "离线缓存",
                        onClick = onDownloadClick,
                        iconTint = AppContentColors.accent(),
                        textColor = contentColor
                    )
                    if (showHistoryService) {
                        AppPreference(
                            icon = historyIcon,
                            title = "历史记录",
                            onClick = onHistoryClick,
                            iconTint = primaryAccent,
                            textColor = contentColor
                        )
                    }
                    if (showFavoriteService) {
                        AppPreference(
                            icon = bookmarkIcon,
                            title = "我的收藏",
                            onClick = onFavoriteClick,
                            iconTint = tertiaryAccent,
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
                    AppPreference(
                        icon = watchLaterIcon,
                        title = "稍后再看",
                        onClick = onWatchLaterClick,
                        iconTint = tertiaryAccent,
                        textColor = contentColor
                    )
                    AppPreference(
                        icon = inboxIcon,
                        title = "消息中心",
                        onClick = onInboxClick,
                        iconTint = secondaryAccent,
                        textColor = contentColor
                    )
                    AppPreference(
                        icon = accountIcon,
                        title = "账号切换",
                        onClick = onAccountManageClick,
                        iconTint = secondaryAccent,
                        textColor = contentColor
                    )
                    AppPreference(
                        title = if (isLogin) "退出登录" else "立即登录",
                        onClick = onLogout,
                        textColor = if (isLogin) AppContentColors.error() else AppContentColors.accent(),
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
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Floating),
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
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AppIcon(
            imageVector = rememberAppChevronForwardIcon(),
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
                iconTint = MaterialTheme.colorScheme.secondary,
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
                    iconTint = MaterialTheme.colorScheme.secondary,
                    textColor = textColor
                )
            }
        }
        val logoutShape = AppShapes.container(ContainerLevel.Floating)
        val logoutContainer = if (embeddedInPanel) {
            textColor.copy(alpha = 0.08f)
        } else {
            containerColor.copy(alpha = 0.72f)
        }
        AppSurface(
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
                AppText(
                    text = if (isLogin) "退出登录" else "立即登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLogin) AppContentColors.error() else AppContentColors.accent(),
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
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.22f else 0.28f))
            .appGentleActionClickable(
                shape = AppShapes.container(ContainerLevel.Card),
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = shortcut.title,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            AppText(
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
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f))
            .appGentleActionClickable(
                shape = AppShapes.container(ContainerLevel.Card),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
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
internal fun AccountSwitchDialog(
    accounts: List<StoredAccountSession>,
    activeAccountMid: Long?,
    playbackAccountMid: Long?,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitch: (Long) -> Unit,
    onSetPlayback: (Long?) -> Unit,
    onRemove: (Long) -> Unit,
    purposeAccountMids: Map<com.android.purebilibili.core.store.AccountPurpose, Long?>? = null,
    onSetPurpose: ((com.android.purebilibili.core.store.AccountPurpose, Long?) -> Unit)? = null,
) {
    val useGlassPurposeControls = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        android.os.Build.VERSION.SDK_INT >= 33 && com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
    val activeAccount = accounts.firstOrNull { it.mid == activeAccountMid }
    val playbackAccount = accounts.firstOrNull { it.mid == playbackAccountMid }
    val activeIsVip = activeAccount?.isVip == true
    val hasVipCandidate = accounts.any { it.isVip && it.mid != activeAccountMid }
    val showPlaybackGuide = !activeIsVip && hasVipCandidate && playbackAccountMid == null
    val playbackAccountColors = resolveAccessibleContainerColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        backgroundColor = MaterialTheme.colorScheme.surface,
        fallbackContentColors = listOf(
            AppContentColors.primaryText(),
            MaterialTheme.colorScheme.onBackground,
        ),
    )
    val playbackGuideColors = resolveAccessibleContainerColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        backgroundColor = MaterialTheme.colorScheme.surface,
        fallbackContentColors = listOf(
            AppContentColors.primaryText(),
            MaterialTheme.colorScheme.onBackground,
        ),
    )

    AccountPurposeDialogSurface(
        onDismissRequest = onDismiss,
        title = "账号与用途",
        contentLayout = resolveAppContentDialogLayoutPolicy(maxWidthDp = 440, minWidthDp = 320),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppText(
                    text = "主账号用于点赞、收藏、评论和个人数据；可分别指定推荐、记录观看和视频取流账号。未指定时跟随主账号，指定账号失效时请重新登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )

                if (onSetPurpose != null && purposeAccountMids == null) {
                    AppText("正在读取账号用途…", style = MaterialTheme.typography.bodySmall)
                }
                if (onSetPurpose != null && purposeAccountMids != null) {
                    listOf(
                        com.android.purebilibili.core.store.AccountPurpose.RECOMMENDATION to "推荐与搜索",
                        com.android.purebilibili.core.store.AccountPurpose.WATCH_HISTORY to "记录观看",
                        com.android.purebilibili.core.store.AccountPurpose.PLAYBACK to "视频取流",
                    ).forEach { (purpose, title) ->
                        val selectedMid = if (purpose == com.android.purebilibili.core.store.AccountPurpose.PLAYBACK) playbackAccountMid else purposeAccountMids[purpose]
                        val selected = accounts.firstOrNull { it.mid == selectedMid }
                        val label = if (selectedMid == null) "跟随主账号" else selected?.name?.ifBlank { "UID $selectedMid" } ?: "账号不可用"
                        val actions = listOf(AppWindowAction(label = "跟随主账号", selected = useGlassPurposeControls && selectedMid == null, onClick = { onSetPurpose(purpose, null) })) +
                            accounts.filter { it.sessData.isNotBlank() }.map { account ->
                                AppWindowAction(label = account.name.ifBlank { "UID ${account.mid}" }, selected = useGlassPurposeControls && selectedMid == account.mid, onClick = { onSetPurpose(purpose, account.mid) })
                            }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (useGlassPurposeControls) {
                                AccountPurposeGlassAnchor(label, actions)
                            } else {
                                AppWindowActionMenu(groups = listOf(actions)) {
                                    AppText(label, modifier = Modifier.widthIn(max = 180.dp).padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    AppText("推荐与搜索覆盖推荐流、热门、相关视频及网页搜索；记录观看只改变视频/专栏上报，不改变历史列表、互动账号和隐私模式。", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                }

                if (playbackAccount != null) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = playbackAccountColors.containerColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText("🎬", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(AppSpacingTokens.Small))
                            AppText(
                                text = "正在用「${playbackAccount.name.ifBlank { "UID ${playbackAccount.mid}" }}」${if (playbackAccount.isVip) "的大会员" else "的账号"}播放",
                                style = MaterialTheme.typography.bodyMedium,
                                color = playbackAccountColors.contentColor,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(AppSpacingTokens.Small))
                            AppTextButton(
                                onClick = { onSetPlayback(null) }
                            ) {
                                AppText(
                                    text = "取消",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = playbackAccountColors.contentColor
                                )
                            }
                        }
                    }
                } else if (showPlaybackGuide) {
                    val guideAccount = accounts.firstOrNull { it.isVip && it.mid != activeAccountMid }
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = playbackGuideColors.containerColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText("💎", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(AppSpacingTokens.Small))
                            AppText(
                                text = guideAccount?.let {
                                    "「${it.name.ifBlank { "UID ${it.mid}" }}」是大会员，设为播放账号即可观看大会员视频"
                                } ?: "可将大会员账号设为播放账号",
                                style = MaterialTheme.typography.bodyMedium,
                                color = playbackGuideColors.contentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (accounts.isEmpty()) {
                    AppText(
                        text = "暂无已保存账号，先添加一个账号后即可快速切换。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText()
                    )
                } else {
                    accounts.forEach { account ->
                        val isActive = account.mid == activeAccountMid
                        val isPlayback = account.mid == playbackAccountMid
                        AppSurface(
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = if (isPlayback) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isActive) {
                                        onSwitch(account.mid)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AppText(
                                                text = account.name.ifBlank { "UID ${account.mid}" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isActive) {
                                                AppSurface(
                                                    shape = AppShapes.container(ContainerLevel.Tag),
                                                    color = AppContentColors.accent().copy(alpha = 0.14f)
                                                ) {
                                                    AppText(
                                                        text = "当前",
                                                        color = AppContentColors.accent(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (isPlayback) {
                                                AppSurface(
                                                    shape = AppShapes.container(ContainerLevel.Tag),
                                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                                ) {
                                                    AppText(
                                                        text = "🎬 播放中",
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        AppText(
                                            text = buildString {
                                                append("UID ${account.mid}")
                                                if (account.isVip) {
                                                    append(" · ")
                                                    append(account.vipLabel.ifBlank { "大会员" })
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (account.isVip) {
                                                AppContentColors.accent()
                                            } else {
                                                AppContentColors.secondaryText()
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (onSetPurpose == null) {
                                        if (isPlayback) {
                                            AppTextButton(
                                                onClick = { onSetPlayback(null) }
                                            ) {
                                                AppText(
                                                    text = "取消播放账号",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = AppContentColors.secondaryText()
                                                )
                                            }
                                        } else {
                                            AppTextButton(
                                                onClick = { onSetPlayback(account.mid) }
                                            ) {
                                                AppText(
                                                    text = if (account.isVip) "设为播放(大会员)" else "设为播放",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (account.isVip) {
                                                        AppContentColors.accent()
                                                    } else {
                                                        AppContentColors.secondaryText()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (!isActive) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppTextButton(
                                            onClick = { onSwitch(account.mid) }
                                        ) {
                                            AppText(
                                                text = "切换",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppTextButton(
                                            onClick = { onRemove(account.mid) }
                                        ) {
                                            AppText(
                                                text = "移除",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AppContentColors.error()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onAddAccount) {
                AppText("添加账号")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("关闭")
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

    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)

    // 进度动画
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = if (isLongPressing) {
            androidx.compose.animation.core.tween(durationMillis = progressDuration, easing = androidx.compose.animation.core.LinearEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 200, easing = AppMotionEasing.M1Standard)
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
        AppAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { AppText("🎉 三连成功！") },
            text = { AppText("请选择你想解锁的功能：") },
            confirmButton = {
                AppTextButton(onClick = {
                    showDialog = false
                    onVipClick()
                }) {
                    AppText("解锁大会员")
                }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    showDialog = false
                    on4KClick()
                }) {
                    AppText("4K 画质")
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
                progressColor = AppContentColors.accent(),
                isActive = false
            )

            // 投币图标
            com.android.purebilibili.feature.video.ui.section.TripleProgressIcon(
                icon = com.android.purebilibili.core.ui.AppIcons.BiliCoin,
                text = "25",
                progress = longPressProgress,
                progressColor = AppContentColors.accent(),
                isActive = false
            )

            // 收藏图标
            com.android.purebilibili.feature.video.ui.section.TripleProgressIcon(
                icon = bookmarkIcon,
                text = "7",
                progress = longPressProgress,
                progressColor = AppContentColors.accent(),
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

@Composable
private fun ObserveProfileScrollToTop(
    requestId: Int,
    listState: LazyListState? = null,
    scrollState: androidx.compose.foundation.ScrollState? = null
) {
    LaunchedEffect(requestId) {
        if (requestId <= 0) return@LaunchedEffect
        listState?.let { state ->
            if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) {
                state.animateScrollToItem(0)
            }
        }
        scrollState?.let { state ->
            if (state.value > 0) {
                state.animateScrollTo(0)
            }
        }
    }
}
