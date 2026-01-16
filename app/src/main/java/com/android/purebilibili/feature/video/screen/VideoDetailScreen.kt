// 文件路径: feature/video/screen/VideoDetailScreen.kt
package com.android.purebilibili.feature.video.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
//  已改用 MaterialTheme.colorScheme.primary

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.ViewPoint
// Refactored UI components
import com.android.purebilibili.feature.video.ui.section.VideoTitleSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.DescriptionSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.ActionButton
import com.android.purebilibili.feature.video.ui.components.RelatedVideosHeader
import com.android.purebilibili.feature.video.ui.components.RelatedVideoItem
import com.android.purebilibili.feature.video.ui.components.CoinDialog
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
// Imports for moved classes
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.state.rememberVideoPlayerState
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.components.SubReplySheet
import com.android.purebilibili.feature.video.ui.components.ReplyHeader
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.CommentSortFilterBar  //  新增
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode  //  新增
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.LikeBurstAnimation
import com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation
import com.android.purebilibili.feature.video.ui.components.VideoDetailSkeleton
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog  //  评论图片预览
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.feature.video.player.MiniPlayerManager
// 📱 [新增] 竖屏全屏
import com.android.purebilibili.feature.video.ui.overlay.PortraitFullscreenOverlay
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager  //  弹幕管理器

@OptIn(ExperimentalSharedTransitionApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun VideoDetailScreen(
    bvid: String,
    coverUrl: String,
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit = {},  //  点击 UP 主头像
    onNavigateToAudioMode: () -> Unit = {}, //  [新增] 导航到音频模式
    miniPlayerManager: MiniPlayerManager? = null,
    isInPipMode: Boolean = false,
    isVisible: Boolean = true,
    startInFullscreen: Boolean = false,  //  从小窗展开时自动进入全屏
    transitionEnabled: Boolean = false,  //  卡片过渡动画开关
    viewModel: PlayerViewModel = viewModel(),
    commentViewModel: VideoCommentViewModel = viewModel() // 
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsState()
    
    //  监听评论状态
    val commentState by commentViewModel.commentState.collectAsState()
    val subReplyState by commentViewModel.subReplyState.collectAsState()
    
    //  空降助手 - 已由插件系统自动处理
    // val sponsorSegment by viewModel.currentSponsorSegment.collectAsState()
    // val showSponsorSkipButton by viewModel.showSkipButton.collectAsState()
    // val sponsorBlockEnabled by com.android.purebilibili.core.store.SettingsManager
    //     .getSponsorBlockEnabled(context)
    //     .collectAsState(initial = false)

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // 📐 [大屏适配] 仅 Expanded 才启用平板分栏布局
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val useTabletLayout = windowSizeClass.isExpandedScreen
    
    // 🔧 [修复] 追踪用户是否主动请求全屏（点击全屏按钮）
    // 使用 rememberSaveable 确保状态在横竖屏切换时保持
    var userRequestedFullscreen by rememberSaveable { mutableStateOf(false) }
    
    // 📐 全屏模式逻辑：
    // - 手机：横屏时自动进入全屏
    // - 大屏（Expanded）：只有用户主动点击全屏按钮后才进入全屏
    val isFullscreenMode = if (useTabletLayout) {
        userRequestedFullscreen
    } else {
        isLandscape
    }

    var isPipMode by remember { mutableStateOf(isInPipMode) }
    LaunchedEffect(isInPipMode) { isPipMode = isInPipMode }
    
    //  [新增] 监听定时关闭状态
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    
    // 📖 [新增] 监听视频章节数据
    // 📖 [新增] 监听视频章节数据
    val viewPoints by viewModel.viewPoints.collectAsState()
    
    // [New] Codec & Audio Preferences
    val codecPreference by viewModel.videoCodecPreference.collectAsState(initial = "hev1")
    val audioQualityPreference by viewModel.audioQualityPreference.collectAsState(initial = -1)
    
    //  [PiP修复] 记录视频播放器在屏幕上的位置，用于PiP窗口只显示视频区域
    var videoPlayerBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    
    // 📱 [优化] isPortraitFullscreen 和 isVerticalVideo 现在从 playerState 获取（见 playerState 定义后）
    
    //  从小窗展开时自动进入全屏
    LaunchedEffect(startInFullscreen) {
        if (startInFullscreen) {
            if (useTabletLayout) {
                userRequestedFullscreen = true
            } else if (!isLandscape) {
                context.findActivity()?.let { activity ->
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    //  用于跟踪组件是否正在退出，防止 SideEffect 覆盖恢复操作
    var isScreenActive by remember { mutableStateOf(true) }
    
    //  [关键] 保存进入前的状态栏配置（在 DisposableEffect 外部定义以便复用）
    val activity = remember { context.findActivity() }
    val window = remember { activity?.window }
    val insetsController = remember {
        if (window != null && activity != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null
    }
    val originalStatusBarColor = remember { window?.statusBarColor ?: android.graphics.Color.TRANSPARENT }
    val originalLightStatusBars = remember { insetsController?.isAppearanceLightStatusBars ?: true }
    
    //  [新增] 恢复状态栏的函数（可复用）
    val restoreStatusBar = remember {
        {
            if (window != null && insetsController != null) {
                insetsController.isAppearanceLightStatusBars = originalLightStatusBars
                window.statusBarColor = originalStatusBarColor
            }
        }
    }
    
    //  [新增] 包装的 onBack，在导航之前立即恢复状态栏
    val handleBack = remember(onBack) {
        {
            isScreenActive = false  // 标记页面正在退出
            restoreStatusBar()      //  立即恢复状态栏（动画开始前）
            onBack()                // 执行实际的返回导航
        }
    }
    
    // 退出重置亮度 +  屏幕常亮管理 + 状态栏恢复（作为安全网）
    DisposableEffect(Unit) {
        //  [沉浸式] 启用边到边显示，让内容延伸到状态栏下方
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        
        //  [修复] 进入视频页时保持屏幕常亮，防止自动熄屏
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            //  [关键] 标记页面正在退出，防止 SideEffect 覆盖
            isScreenActive = false
            
            val layoutParams = window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window?.attributes = layoutParams
            
            //  [修复] 离开视频页时取消屏幕常亮
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            //  [安全网] 确保状态栏被恢复（以防 handleBack 未被调用，如系统返回）
            restoreStatusBar()

            // 恢复屏幕方向
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    //  新增：监听消息事件（关注/收藏反馈）- 使用居中弹窗
    var popupMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            popupMessage = message
            // 2秒后自动隐藏
            kotlinx.coroutines.delay(2000)
            popupMessage = null
        }
    }
    
    //  初始化进度持久化存储
    LaunchedEffect(Unit) {
        viewModel.initWithContext(context)
        //  [埋点] 页面浏览追踪
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("VideoDetailScreen")
    }
    
    //  [PiP修复] 当视频播放器位置更新时，同步更新PiP参数
    //  [修复] 只有 SYSTEM_PIP 模式才启用自动进入PiP
    val pipModeEnabled = remember { 
        com.android.purebilibili.core.store.SettingsManager.getMiniPlayerModeSync(context) == 
            com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP
    }
    
    LaunchedEffect(videoPlayerBounds, pipModeEnabled) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity?.let { act ->
                val pipParamsBuilder = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                
                //  设置源矩形区域 - PiP只显示视频播放器区域
                videoPlayerBounds?.let { bounds ->
                    pipParamsBuilder.setSourceRectHint(bounds)
                }
                
                // Android 12+ 支持手势自动进入 PiP -  只有 SYSTEM_PIP 模式才启用
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    pipParamsBuilder.setAutoEnterEnabled(pipModeEnabled)  //  受设置控制
                    pipParamsBuilder.setSeamlessResizeEnabled(pipModeEnabled)
                }
                
                act.setPictureInPictureParams(pipParamsBuilder.build())
                com.android.purebilibili.core.util.Logger.d("VideoDetailScreen", 
                    " PiP参数更新: autoEnterEnabled=$pipModeEnabled")
            }
        }
    }

    // 初始化播放器状态
    val playerState = rememberVideoPlayerState(
        context = context,
        viewModel = viewModel,
        bvid = bvid
    )
    
    //  [性能优化] 生命周期感知：进入后台时暂停播放，返回前台时继续
    //  [修复] 此处逻辑已移至 VideoPlayerState.kt 统一处理
    // 删除冗余的暂停逻辑，避免与 VideoPlayerState 中的生命周期处理冲突
    // VideoPlayerState 会检查 PiP/小窗模式来决定是否暂停
    
    // 📱 [优化] 竖屏视频检测已移至 VideoPlayerState 集中管理
    val isVerticalVideo by playerState.isVerticalVideo.collectAsState()
    
    // 📱 [优化] 竖屏全屏状态现在由 playerState 集中管理
    val isPortraitFullscreen by playerState.isPortraitFullscreen.collectAsState()

    // 📲 小窗模式（手机/平板统一逻辑）
    val handlePipClick = {
        // 使用 MiniPlayerManager 进入应用内小窗模式
        miniPlayerManager?.let { manager ->
            //  [埋点] PiP 进入事件
            com.android.purebilibili.core.util.AnalyticsHelper.logPictureInPicture(
                videoId = bvid,
                action = "enter_mini"
            )

            // 1. 将当前播放器信息传递给小窗管理器
            val info = uiState as? PlayerUiState.Success
            manager.setVideoInfo(
                bvid = bvid,
                title = info?.info?.title ?: "",
                cover = info?.info?.pic ?: "",
                owner = info?.info?.owner?.name ?: "",
                cid = info?.info?.cid ?: 0L,
                externalPlayer = playerState.player
            )

            // 2. 进入小窗模式（强制，不管当前模式设置）
            manager.enterMiniMode(forced = true)

            // 3. 返回上一页（首页）
            onBack()
        } ?: run {
            // 如果 miniPlayerManager 不存在，直接返回
            com.android.purebilibili.core.util.Logger.w("VideoDetailScreen", "⚠️ miniPlayerManager 为 null，无法进入小窗")
            onBack()
        }
    }

    //  核心修改：初始化评论 & 媒体中心信息
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Success) {
            val info = (uiState as PlayerUiState.Success).info
            val success = uiState as PlayerUiState.Success
            
            // 初始化评论（传入 UP 主 mid 用于筛选）
            commentViewModel.init(info.aid, info.owner.mid)
            
            playerState.updateMediaMetadata(
                title = info.title,
                artist = info.owner.name,
                coverUrl = info.pic
            )
            
            // 📱 [双重验证] 从 API dimension 字段设置预判断值
            info.dimension?.let { dim ->
                playerState.setApiDimension(dim.width, dim.height)
            }
            
            //  同步视频信息到小窗管理器（为小窗模式做准备）
            com.android.purebilibili.core.util.Logger.d("VideoDetailScreen", " miniPlayerManager=${if (miniPlayerManager != null) "存在" else "null"}, bvid=$bvid")
            if (miniPlayerManager != null) {
                com.android.purebilibili.core.util.Logger.d("VideoDetailScreen", " 调用 setVideoInfo: title=${info.title}")
                miniPlayerManager.setVideoInfo(
                    bvid = bvid,
                    title = info.title,
                    cover = info.pic,
                    owner = info.owner.name,
                    cid = info.cid,  //  传递 cid 用于弹幕加载
                    externalPlayer = playerState.player,
                    fromLeft = com.android.purebilibili.core.util.CardPositionManager.isCardOnLeft  //  传递入场方向
                )
                //  [新增] 缓存完整 UI 状态，用于从小窗返回时恢复
                miniPlayerManager.cacheUiState(success)
                com.android.purebilibili.core.util.Logger.d("VideoDetailScreen", " setVideoInfo + cacheUiState 调用完成")
            } else {
                android.util.Log.w("VideoDetailScreen", " miniPlayerManager 是 null!")
            }
        } else if (uiState is PlayerUiState.Loading) {
            playerState.updateMediaMetadata(
                title = "加载中...",
                artist = "",
                coverUrl = coverUrl
            )
        }
    }
    
    //  弹幕加载逻辑已移至 VideoPlayerState 内部处理
    // 避免在此处重复消耗 InputStream

    // 辅助函数：切换全屏状态
    val toggleFullscreen = {
        val activity = context.findActivity()
        if (activity != null) {
            if (useTabletLayout) {
                // 🖥️ 平板：仅切换 UI 状态，不改变屏幕方向
                // [修复] 如果退出全屏且是手机（sw < 600），强制转回竖屏
                val wasFullscreen = userRequestedFullscreen
                userRequestedFullscreen = !userRequestedFullscreen
                
                if (wasFullscreen && !userRequestedFullscreen) {
                    // check if it is a phone
                    if (configuration.smallestScreenWidthDp < 600) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            } else {
                // 📱 手机：通过旋转屏幕触发全屏
                if (isLandscape) {
                    userRequestedFullscreen = false
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    userRequestedFullscreen = true
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    //  拦截系统返回键：如果是全屏模式，则先退出全屏
    BackHandler(enabled = isFullscreenMode) {
        toggleFullscreen()
    }
    
    // 📱 拦截系统返回键：如果是竖屏全屏模式，则先退出竖屏全屏
    BackHandler(enabled = isPortraitFullscreen) {
        playerState.setPortraitFullscreen(false)
    }
    
    // 📱 [新增] 拦截系统返回键：手机横屏进入了平板分栏模式，应切换回竖屏而非退出
    val isPhoneInLandscapeSplitView = useTabletLayout && 
        configuration.smallestScreenWidthDp < 600 && 
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    BackHandler(enabled = isPhoneInLandscapeSplitView && !isFullscreenMode && !isPortraitFullscreen) {
        com.android.purebilibili.core.util.Logger.d(
            "VideoDetailScreen", 
            "📱 System back pressed in phone landscape split-view, rotating to PORTRAIT"
        )
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // 沉浸式状态栏控制
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }

    //  iOS风格：竖屏时状态栏黑色背景（与播放器融为一体）
    //  只在页面活跃时修改状态栏，避免退出时覆盖恢复操作
    if (!view.isInEditMode && isScreenActive) {
        SideEffect {
            val window = (view.context.findActivity())?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (isFullscreenMode) {
                // 📱 手机全屏隐藏状态栏
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.statusBarColor = Color.Black.toArgb()
                window.navigationBarColor = Color.Black.toArgb()
            } else {
                //  [沉浸式] 非全屏模式：状态栏透明，让视频延伸到状态栏下方
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = false  // 白色图标（视频区域是深色的）
                window.statusBarColor = Color.Transparent.toArgb()  // 透明状态栏
                window.navigationBarColor = Color.Transparent.toArgb()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreenMode) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // 📐 [平板适配] 全屏模式过渡动画（只有手机横屏才进入全屏）
        AnimatedContent(
            targetState = isFullscreenMode,
            transitionSpec = {
                if (targetState) {
                    // 进入全屏：放大 + 渐入
                    (fadeIn(animationSpec = tween(400)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(400)) +
                                    scaleOut(targetScale = 1.1f, animationSpec = tween(400))
                        )
                } else {
                    // 退出全屏：缩小 + 渐出
                    (fadeIn(animationSpec = tween(400)) +
                            scaleIn(initialScale = 1.1f, animationSpec = tween(400)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(400)) +
                                    scaleOut(targetScale = 0.9f, animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        )
                }
            },
            label = "fullscreen_transition"
        ) { targetIsFullscreen ->
            if (targetIsFullscreen) {
                VideoPlayerSection(
                    playerState = playerState,
                    uiState = uiState,
                    isFullscreen = true,
                    isInPipMode = isPipMode,
                    onToggleFullscreen = { toggleFullscreen() },
                    onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                    onBack = { toggleFullscreen() },
                    // 🔗 [新增] 分享功能
                    bvid = bvid,
                    //  实验性功能：双击点赞
                    onDoubleTapLike = { viewModel.toggleLike() },
                    //  [新增] 重载视频
                    onReloadVideo = { viewModel.reloadVideo() },
                    //  [新增] CDN 线路切换
                    cdnCount = (uiState as? PlayerUiState.Success)?.cdnCount ?: 1,
                    onSwitchCdn = { viewModel.switchCdn() },
                    onSwitchCdnTo = { viewModel.switchCdnTo(it) },

                    // [New] Codec & Audio (Fullscreen)
                    currentCodec = codecPreference,
                    onCodecChange = { viewModel.setVideoCodec(it) },
                    currentAudioQuality = audioQualityPreference,
                    onAudioQualityChange = { viewModel.setAudioQuality(it) },
                    
                    //  [新增] 音频模式
                    isAudioOnly = false, // 全屏模式只有视频
                    onAudioOnlyToggle = { 
                        viewModel.setAudioMode(true)
                        onNavigateToAudioMode()
                    },
                    
                    //  [新增] 定时关闭
                    sleepTimerMinutes = sleepTimerMinutes,
                    onSleepTimerChange = { viewModel.setSleepTimer(it) },
                    
                    // 🖼️ [新增] 视频预览图数据
                    videoshotData = (uiState as? PlayerUiState.Success)?.videoshotData,
                    
                    // 📖 [新增] 视频章节数据
                    viewPoints = viewPoints,
                    isPortraitFullscreen = isPortraitFullscreen
                )
            } else {
                //  沉浸式布局：视频延伸到状态栏 + 内容区域
                //  📐 [大屏适配] 仅 Expanded 使用分栏布局
                
                //  📐 [大屏适配] 根据设备类型选择布局
                if (useTabletLayout) {
                    // 🖥️ 平板：左右分栏布局（视频+信息 | 评论/推荐）
                    TabletVideoLayout(
                        playerState = playerState,
                        uiState = uiState,
                        commentState = commentState,
                        viewModel = viewModel,
                        commentViewModel = commentViewModel,
                        configuration = configuration,
                        isVerticalVideo = isVerticalVideo,
                        sleepTimerMinutes = sleepTimerMinutes,
                        viewPoints = viewPoints,
                        bvid = bvid,
                        onBack = {
                            // 📱 手机误入平板模式（如横屏宽度触发 Expanded），点击返回应切换回竖屏
                            // 🔧 [修复] 检查 smallestScreenWidthDp 确保这不是真正的平板
                            val smallestWidth = configuration.smallestScreenWidthDp
                            val isPhone = smallestWidth < 600
                            val currentOrientation = configuration.orientation
                            val isInLandscape = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
                            
                            com.android.purebilibili.core.util.Logger.d(
                                "VideoDetailScreen", 
                                "📱 onBack clicked: smallestWidth=$smallestWidth, isPhone=$isPhone, " +
                                "orientation=$currentOrientation, isLandscape=$isInLandscape, " +
                                "activity=${activity != null}"
                            )
                            
                            if (isPhone && isInLandscape) {
                                com.android.purebilibili.core.util.Logger.d(
                                    "VideoDetailScreen", 
                                    "📱 Rotating to PORTRAIT"
                                )
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                com.android.purebilibili.core.util.Logger.d(
                                    "VideoDetailScreen", 
                                    "📱 Calling handleBack()"
                                )
                                handleBack()
                            }
                        },
                        onUpClick = onUpClick,
                        onNavigateToAudioMode = onNavigateToAudioMode,
                        onToggleFullscreen = { toggleFullscreen() },  // 📺 平板全屏切换
                        isInPipMode = isPipMode,
                        onPipClick = handlePipClick,
                        isPortraitFullscreen = isPortraitFullscreen,

                        transitionEnabled = transitionEnabled,  //  传递过渡动画开关
                        // [New] Codec & Audio
                        currentCodec = codecPreference,
                        onCodecChange = { viewModel.setVideoCodec(it) },
                        currentAudioQuality = audioQualityPreference,
                        onAudioQualityChange = { viewModel.setAudioQuality(it) }
                    )
                } else {
                    // 📱 手机竖屏：原有单列布局
                Column(modifier = Modifier.fillMaxSize()) {
                    //  [沉浸式] 获取状态栏高度
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val screenWidthDp = configuration.screenWidthDp.dp
                    val videoHeight = screenWidthDp * 9f / 16f  // 16:9 比例
                    
                    //  读取上滑隐藏播放器设置
                    val swipeHidePlayerEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getSwipeHidePlayerEnabled(context).collectAsState(initial = false)
                    
                    //  播放器隐藏状态（用于动画）
                    var isPlayerHidden by remember { mutableStateOf(false) }
                    val animatedPlayerHeight by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isPlayerHidden && swipeHidePlayerEnabled) 0.dp else videoHeight + statusBarHeight,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 300f
                        ),
                        label = "playerHeight"
                    )
                    
                    //  注意：移除了状态栏黑色 Spacer
                    // 播放器将延伸到状态栏下方，共享元素过渡更流畅
                    
                    //  视频播放器区域 - 包含状态栏高度
                    //  尝试获取共享元素作用域
                    val sharedTransitionScope = LocalSharedTransitionScope.current
                    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                    
                    //  为播放器容器添加共享元素标记（受开关控制）
                    val playerContainerModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_cover_$bvid"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    //  添加回弹效果的 spring 动画
                                    boundsTransform = { _, _ ->
                                        spring(
                                            dampingRatio = 0.7f,   // 轻微回弹
                                            stiffness = 300f       // 适中速度
                                        )
                                    },
                                    clipInOverlayDuringTransition = OverlayClip(
                                        RoundedCornerShape(0.dp)  //  播放器无圆角
                                    )
                                )
                        }
                    } else {
                        Modifier
                    }
                    
                    //  播放器容器包含状态栏高度，让视频延伸到顶部
                    //  [修复] 始终保持播放器在 Composition 中，避免隐藏时重新创建导致重载
                    Box(
                        modifier = playerContainerModifier
                            .fillMaxWidth()
                            .height(animatedPlayerHeight)  //  使用动画高度（包含0高度）
                            .background(Color.Black)  // 黑色背景
                            .clipToBounds()
                            //  [PiP修复] 捕获视频播放器在屏幕上的位置
                            .onGloballyPositioned { layoutCoordinates ->
                                val position = layoutCoordinates.positionInWindow()
                                val size = layoutCoordinates.size
                                videoPlayerBounds = android.graphics.Rect(
                                    position.x.toInt(),
                                    position.y.toInt(),
                                    position.x.toInt() + size.width,
                                    position.y.toInt() + size.height
                                )
                            }
                    ) {
                        //  播放器内部使用 padding 避开状态栏
                        //  [关键] 即使高度为0也保持播放器渲染，避免重载
                        //  [修复] 高度需要包含statusBarHeight，扣除padding后视频内容才是完整的16:9
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(videoHeight + statusBarHeight)  //  修复：包含状态栏高度
                                .padding(top = statusBarHeight)  //  顶部 padding 避开状态栏
                        ) {
                            VideoPlayerSection(
                                playerState = playerState,
                                uiState = uiState,
                                isFullscreen = false,
                                isInPipMode = isPipMode,
                                onToggleFullscreen = { toggleFullscreen() },
                                onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                                onBack = handleBack,
                                // 🔗 [新增] 分享功能
                                bvid = bvid,
                                onDoubleTapLike = { viewModel.toggleLike() },
                                //  [新增] 重载视频
                                onReloadVideo = { viewModel.reloadVideo() },
                                //  [新增] CDN 线路切换
                                currentCdnIndex = (uiState as? PlayerUiState.Success)?.currentCdnIndex ?: 0,
                                cdnCount = (uiState as? PlayerUiState.Success)?.cdnCount ?: 1,
                                onSwitchCdn = { viewModel.switchCdn() },
                                onSwitchCdnTo = { viewModel.switchCdnTo(it) },
                                
                                //  [新增] 音频模式
                                isAudioOnly = false,
                                onAudioOnlyToggle = { 
                                    viewModel.setAudioMode(true)
                                    onNavigateToAudioMode()
                                },
                                
                                //  [新增] 定时关闭
                                sleepTimerMinutes = sleepTimerMinutes,
                                onSleepTimerChange = { viewModel.setSleepTimer(it) },
                                
                                // 🖼️ [新增] 视频预览图数据
                                videoshotData = (uiState as? PlayerUiState.Success)?.videoshotData,
                                
                                // 📖 [新增] 视频章节数据
                        viewPoints = viewPoints,
                        
                        // 📱 [新增] 竖屏全屏模式
                        isVerticalVideo = isVerticalVideo,
                        onPortraitFullscreen = { playerState.setPortraitFullscreen(true) },
                        isPortraitFullscreen = isPortraitFullscreen,

                                // 📲 [修复] 小窗模式 - 转移到应用内小窗而非直接进入系统 PiP
                                onPipClick = handlePipClick,
                                // [New] Codec & Audio
                                currentCodec = codecPreference,
                                onCodecChange = { viewModel.setVideoCodec(it) },
                                currentAudioQuality = audioQualityPreference,
                                onAudioQualityChange = { viewModel.setAudioQuality(it) }
                                //  空降助手 - 已由插件系统自动处理
                                // sponsorSegment = sponsorSegment,
                                // showSponsorSkipButton = showSponsorSkipButton,
                                // onSponsorSkip = { viewModel.skipCurrentSponsorSegment() },
                                // onSponsorDismiss = { viewModel.dismissSponsorSkipButton() }
                            )
                        }
                    }
                    
                    //  播放器隐藏/恢复切换栏 - 仅在播放器被隐藏时显示（避免播放器显示时多出一块区域）
                    if (swipeHidePlayerEnabled && isPlayerHidden) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPlayerHidden = !isPlayerHidden },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isPlayerHidden) CupertinoIcons.Default.ChevronDown else CupertinoIcons.Default.ChevronUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isPlayerHidden) "展开播放器" else "收起播放器",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    //  第3层：内容区域
                    //  创建嵌套滚动连接用于监听内容滑动
                    val nestedScrollConnection = remember {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            override fun onPreScroll(
                                available: androidx.compose.ui.geometry.Offset,
                                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                            ): androidx.compose.ui.geometry.Offset {
                                // 只有开启设置时才处理
                                if (swipeHidePlayerEnabled) {
                                    // 向上滑动（正值）且超过阈值时隐藏播放器
                                    if (available.y < -20f && !isPlayerHidden) {
                                        isPlayerHidden = true
                                    }
                                    // 向下滑动（负值）且超过阈值时显示播放器
                                    if (available.y > 40f && isPlayerHidden) {
                                        isPlayerHidden = false
                                    }
                                }
                                return androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        when (uiState) {
                            is PlayerUiState.Loading -> {
                                val loadingState = uiState as PlayerUiState.Loading
                                //  显示重试进度
                                if (loadingState.retryAttempt > 0) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            //  iOS 风格加载
                                            CupertinoActivityIndicator()
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = "正在重试 ${loadingState.retryAttempt}/${loadingState.maxAttempts}...",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                } else {
                                    VideoDetailSkeleton()
                                }
                            }

                            is PlayerUiState.Success -> {
                                val success = uiState as PlayerUiState.Success
                                //  计算当前分P索引
                                val currentPageIndex = success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0)
                                
                                //  下载进度
                                val downloadProgress by viewModel.downloadProgress.collectAsState()
                                
                                // 📱 [优化] 视频切换过渡动画
                                AnimatedContent(
                                    targetState = success.info.bvid,
                                    transitionSpec = {
                                        // 左右滑动 + 淡入淡出过渡动画
                                        (slideInHorizontally { width -> width / 4 } + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(
                                                slideOutHorizontally { width -> -width / 4 } + fadeOut(animationSpec = tween(300))
                                            )
                                    },
                                    label = "video_content_transition"
                                ) { currentBvid ->
                                    // 使用 currentBvid 确保动画正确触发（实际仍使用 success.info）
                                    VideoContentSection(
                                        info = success.info,
                                        relatedVideos = success.related,
                                        replies = commentState.replies,
                                        replyCount = commentState.replyCount,
                                        emoteMap = success.emoteMap,
                                        isRepliesLoading = commentState.isRepliesLoading,
                                        isFollowing = success.isFollowing,
                                        isFavorited = success.isFavorited,
                                        isLiked = success.isLiked,
                                        coinCount = success.coinCount,
                                        currentPageIndex = currentPageIndex,
                                        downloadProgress = downloadProgress,
                                        isInWatchLater = success.isInWatchLater,
                                        followingMids = success.followingMids,
                                        videoTags = success.videoTags,
                                        //  [新增] 评论排序/筛选参数
                                        sortMode = commentState.sortMode,
                                        upOnlyFilter = commentState.upOnlyFilter,
                                        onSortModeChange = { commentViewModel.setSortMode(it) },
                                        onUpOnlyToggle = { commentViewModel.toggleUpOnly() },
                                        onFollowClick = { viewModel.toggleFollow() },
                                        onFavoriteClick = { viewModel.toggleFavorite() },
                                        onLikeClick = { viewModel.toggleLike() },
                                        onCoinClick = { viewModel.openCoinDialog() },
                                        onTripleClick = { viewModel.doTripleAction() },
                                        onPageSelect = { viewModel.switchPage(it) },
                                        onUpClick = onUpClick,
                                        onRelatedVideoClick = { vid -> viewModel.loadVideo(vid) },
                                        onSubReplyClick = { commentViewModel.openSubReply(it) },
                                        onLoadMoreReplies = { commentViewModel.loadComments() },
                                        onDownloadClick = { viewModel.openDownloadDialog() },
                                        onWatchLaterClick = { viewModel.toggleWatchLater() },
                                        //  [新增] 时间戳点击跳转
                                        onTimestampClick = { positionMs ->
                                            playerState.player.seekTo(positionMs)
                                            playerState.player.play()
                                        }
                                    )
                                }
                            }

                            is PlayerUiState.Error -> {
                                val errorState = uiState as PlayerUiState.Error
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp)
                                    ) {
                                        //  根据错误类型显示不同图标
                                        Text(
                                            text = when (errorState.error) {
                                                is com.android.purebilibili.data.model.VideoLoadError.NetworkError -> "📡"
                                                is com.android.purebilibili.data.model.VideoLoadError.VideoNotFound -> "🔍"
                                                is com.android.purebilibili.data.model.VideoLoadError.RegionRestricted -> "🌐"
                                                is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> "⏳"
                                                is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown -> ""
                                                is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty -> "⚡"
                                                else -> ""
                                            },
                                            fontSize = 48.sp
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = errorState.msg,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        
                                        //  针对风控错误显示额外建议
                                        when (errorState.error) {
                                            is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown,
                                            is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty -> {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = " 建议：切换 WiFi/移动数据 或 清除缓存后重试",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                            is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = " 该视频可能暂时不可用，请尝试其他视频",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                            else -> {}
                                        }
                                        
                                        //  只有可重试的错误才显示重试按钮（或者风控错误允许强制重试）
                                        val showRetryButton = errorState.canRetry || 
                                            errorState.error is com.android.purebilibili.data.model.VideoLoadError.RateLimited ||
                                            errorState.error is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty
                                        if (showRetryButton) {
                                            Spacer(Modifier.height(24.dp))
                                            Button(
                                                onClick = { viewModel.retry() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Text(
                                                    text = when (errorState.error) {
                                                        is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> "强制重试"
                                                        is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown -> "清除冷却并重试"
                                                        else -> "重试"
                                                    }
                                                )
                                            }
                                        }
                                }
                            }
                        }
                }
                }  // 📱 手机竖屏布局结束（Column）
                }  // Box with nested scroll
            }  // else shouldUseSplitLayout
        }  // else targetIsLandscape
        }  // AnimatedContent
        
        // 📱 [新增] 竖屏全屏覆盖层
        if (isPortraitFullscreen && !isLandscape && uiState is PlayerUiState.Success) {
            val success = uiState as PlayerUiState.Success
            
            // 监听播放器进度
            val progressState by produceState(
                initialValue = PlayerProgress(),
                key1 = playerState.player,
                key2 = isPortraitFullscreen
            ) {
                while (isPortraitFullscreen) {
                    val duration = if (playerState.player.duration < 0) 0L else playerState.player.duration
                    value = PlayerProgress(
                        current = playerState.player.currentPosition,
                        duration = duration,
                        buffered = playerState.player.bufferedPosition
                    )
                    kotlinx.coroutines.delay(200L)
                }
            }
            
            var isPlaying by remember { mutableStateOf(playerState.player.isPlaying) }
            LaunchedEffect(playerState.player.isPlaying) {
                isPlaying = playerState.player.isPlaying
            }
            
            // 弹幕开关状态
            val danmakuEnabled by com.android.purebilibili.core.store.SettingsManager
                .getDanmakuEnabled(context)
                .collectAsState(initial = true)
            val scope = rememberCoroutineScope()
            
            //  弹幕管理器（用于进度条拖动时清除弹幕）
            val danmakuManager = rememberDanmakuManager()

            // 弹幕设置
            val danmakuOpacity by com.android.purebilibili.core.store.SettingsManager
                .getDanmakuOpacity(context)
                .collectAsState(initial = 0.85f)
            val danmakuFontScale by com.android.purebilibili.core.store.SettingsManager
                .getDanmakuFontScale(context)
                .collectAsState(initial = 1.0f)
            val danmakuSpeed by com.android.purebilibili.core.store.SettingsManager
                .getDanmakuSpeed(context)
                .collectAsState(initial = 1.0f)
            val danmakuDisplayArea by com.android.purebilibili.core.store.SettingsManager
                .getDanmakuArea(context)
                .collectAsState(initial = 0.5f)

            // 绑定 Player（单例保持状态）
            DisposableEffect(playerState.player) {
                danmakuManager.attachPlayer(playerState.player)
                onDispose { }
            }

            // 使用 LifecycleOwner 在 Activity 销毁时清理引用
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                        com.android.purebilibili.core.util.Logger.d("PortraitDanmaku", " ON_DESTROY: Clearing danmaku references")
                        danmakuManager.clearViewReference()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // 弹幕开关变化时更新
            LaunchedEffect(danmakuEnabled) {
                danmakuManager.isEnabled = danmakuEnabled
            }

            // 弹幕设置变化时实时应用
            LaunchedEffect(danmakuOpacity, danmakuFontScale, danmakuSpeed, danmakuDisplayArea) {
                danmakuManager.updateSettings(
                    opacity = danmakuOpacity,
                    fontScale = danmakuFontScale,
                    speed = danmakuSpeed,
                    displayArea = danmakuDisplayArea
                )
            }

            // 加载弹幕数据（等待 duration 可用）
            val portraitCid = success.info.cid
            LaunchedEffect(portraitCid) {
                if (portraitCid > 0) {
                    danmakuManager.isEnabled = danmakuEnabled

                    var durationMs = 0L
                    var retries = 0
                    while (durationMs <= 0 && retries < 50) {
                        durationMs = playerState.player.duration.takeIf { it > 0 } ?: 0L
                        if (durationMs <= 0) {
                            kotlinx.coroutines.delay(100)
                            retries++
                        }
                    }

                    com.android.purebilibili.core.util.Logger.d(
                        "PortraitDanmaku",
                        " Loading danmaku: cid=$portraitCid, duration=${durationMs}ms (after $retries retries)"
                    )
                    danmakuManager.loadDanmaku(portraitCid, durationMs)
                }
            }
            
            // 状态栏隐藏控制
            var isStatusBarHidden by remember { mutableStateOf(false) }
            
            // 📱 [修复] 沉浸式全屏效果
            val activity = context.findActivity()
            
            // 📱 [新增] 进入竖屏全屏时设置沉浸式模式
            LaunchedEffect(Unit) {
                activity?.let { act ->
                    val window = act.window
                    // 启用边到边模式，让内容延伸到系统栏区域
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    // 状态栏透明
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                }
            }
            
            // 📱 [修复] 控制状态栏显示/隐藏
            LaunchedEffect(isStatusBarHidden) {
                activity?.let { act ->
                    val window = act.window
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    
                    if (isStatusBarHidden) {
                        // 隐藏状态栏，实现完全沉浸模式
                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        insetsController.systemBarsBehavior = 
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        // 显示状态栏（但保持透明）
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
            
            // 📱 [修复] 退出竖屏全屏时恢复正常模式
            DisposableEffect(Unit) {
                onDispose {
                    activity?.let { act ->
                        val window = act.window
                        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        // 恢复状态栏
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        // 恢复边到边设置（由外层管理）
                    }
                }
            }
            
            // 控制选项状态
            var showSpeedMenu by remember { mutableStateOf(false) }
            var showQualityMenu by remember { mutableStateOf(false) }
            var showRatioMenu by remember { mutableStateOf(false) }
            var currentSpeed by remember { mutableFloatStateOf(playerState.player.playbackParameters.speed) }
            var currentRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                // 视频播放器
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = playerState.player
                            useController = false
                            setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)  // 禁用系统缓冲指示器
                        }
                    },
                    update = { view ->
                        view.player = playerState.player
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 弹幕视图（覆盖在 PlayerView 上方）
                if (danmakuEnabled) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            com.bytedance.danmaku.render.engine.DanmakuView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                danmakuManager.attachView(this)
                                com.android.purebilibili.core.util.Logger.d("PortraitDanmaku", " DanmakuView created")
                            }
                        },
                        update = { view ->
                            if (view.width > 0 && view.height > 0) {
                                danmakuManager.attachView(view)
                                com.android.purebilibili.core.util.Logger.d(
                                    "PortraitDanmaku",
                                    " DanmakuView update: size=${view.width}x${view.height}"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // 竖屏全屏控件覆盖层
                PortraitFullscreenOverlay(
                    title = success.info.title,
                    isPlaying = isPlaying,
                    progress = progressState,
                    
                    // 互动状态
                    isLiked = success.isLiked,
                    isCoined = success.coinCount > 0,
                    isFavorited = success.isFavorited,
                    onLikeClick = { viewModel.toggleLike() },
                    onCoinClick = { viewModel.openCoinDialog() },
                    onFavoriteClick = { viewModel.toggleFavorite() },
                    
                    // 控制状态
                    currentSpeed = currentSpeed,
                    currentQualityLabel = success.qualityLabels.getOrNull(
                        success.qualityIds.indexOf(success.currentQuality)
                    ) ?: "自动",
                    currentRatio = currentRatio,
                    danmakuEnabled = danmakuEnabled,
                    isStatusBarHidden = isStatusBarHidden,
                    
                    // 回调
                    onBack = { playerState.setPortraitFullscreen(false) },
                    onPlayPause = {
                        if (isPlaying) playerState.player.pause() else playerState.player.play()
                        isPlaying = !isPlaying
                    },
                    onSeek = { playerState.player.seekTo(it) },
                    onSeekStart = { danmakuManager.clear() },  //  拖动进度条时清除弹幕
                    onSpeedClick = { showSpeedMenu = true },
                    onQualityClick = { showQualityMenu = true },
                    onRatioClick = { showRatioMenu = true },
                    onDanmakuToggle = {
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setDanmakuEnabled(context, !danmakuEnabled)
                        }
                    },
                    onDanmakuInputClick = { /* TODO: 弹幕输入 */ },
                    onToggleStatusBar = { isStatusBarHidden = !isStatusBarHidden }
                )
                
                // 倍速选择菜单
                if (showSpeedMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                            .clickable { showSpeedMenu = false },
                        contentAlignment = Alignment.Center
                    ) {
                        com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenu(
                            currentSpeed = currentSpeed,
                            onSpeedSelected = { speed ->
                                currentSpeed = speed
                                playerState.player.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                            },
                            onDismiss = { showSpeedMenu = false }
                        )
                    }
                }
                
                // 画质选择菜单
                if (showQualityMenu) {
                    com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu(
                        qualities = success.qualityLabels,
                        qualityIds = success.qualityIds,
                        currentQuality = success.qualityLabels.getOrNull(
                            success.qualityIds.indexOf(success.currentQuality)
                        ) ?: "自动",
                        isLoggedIn = success.isLoggedIn,
                        isVip = success.isVip,
                        onQualitySelected = { index ->
                            val id = success.qualityIds.getOrNull(index) ?: 0
                            viewModel.changeQuality(id, playerState.player.currentPosition)
                            showQualityMenu = false
                        },
                        onDismiss = { showQualityMenu = false }
                    )
                }
                
                // 画面比例选择菜单
                if (showRatioMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                            .clickable { showRatioMenu = false },
                        contentAlignment = Alignment.Center
                    ) {
                        com.android.purebilibili.feature.video.ui.components.AspectRatioMenu(
                            currentRatio = currentRatio,
                            onRatioSelected = { ratio ->
                                currentRatio = ratio
                                showRatioMenu = false
                            },
                            onDismiss = { showRatioMenu = false }
                        )
                    }
                }
            }
        }
        //  [新增] 投币对话框
        val coinDialogVisible by viewModel.coinDialogVisible.collectAsState()
        val currentCoinCount = (uiState as? PlayerUiState.Success)?.coinCount ?: 0
        CoinDialog(
            visible = coinDialogVisible,
            currentCoinCount = currentCoinCount,
            onDismiss = { viewModel.closeCoinDialog() },
            onConfirm = { count, alsoLike -> viewModel.doCoin(count, alsoLike) }
        )
        
        //  [新增] 下载画质选择对话框
        val showDownloadDialog by viewModel.showDownloadDialog.collectAsState()
        val successForDownload = uiState as? PlayerUiState.Success
        if (showDownloadDialog && successForDownload != null) {
            //  按画质从高到低排序（qualityId 越大画质越高）
            val sortedQualityOptions = successForDownload.qualityIds
                .zip(successForDownload.qualityLabels)
                .sortedByDescending { it.first }
            //  默认选中最高画质
            val highestQuality = sortedQualityOptions.firstOrNull()?.first ?: successForDownload.currentQuality
            
            com.android.purebilibili.feature.download.DownloadQualityDialog(
                title = successForDownload.info.title,
                qualityOptions = sortedQualityOptions,
                currentQuality = highestQuality,  // 默认选中最高画质
                onQualitySelected = { viewModel.downloadWithQuality(it) },
                onDismiss = { viewModel.closeDownloadDialog() }
            )
        }
        
        //  评论二级弹窗
        // [#14修复] 添加图片预览状态
        var subReplyShowImagePreview by remember { mutableStateOf(false) }
        var subReplyPreviewImages by remember { mutableStateOf<List<String>>(emptyList()) }
        var subReplyPreviewIndex by remember { mutableIntStateOf(0) }
        var subReplySourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        
        // [#14修复] 评论详情图片预览对话框
        if (subReplyShowImagePreview && subReplyPreviewImages.isNotEmpty()) {
            ImagePreviewDialog(
                images = subReplyPreviewImages,
                initialIndex = subReplyPreviewIndex,
                sourceRect = subReplySourceRect,
                onDismiss = { subReplyShowImagePreview = false }
            )
        }
        
        if (subReplyState.visible) {
            BackHandler {
                commentViewModel.closeSubReply()
            }
            val successState = uiState as? PlayerUiState.Success
            SubReplySheet(
                state = subReplyState,
                emoteMap = successState?.emoteMap ?: emptyMap(),
                onDismiss = { commentViewModel.closeSubReply() },
                onLoadMore = { commentViewModel.loadMoreSubReplies() },
                //  [新增] 时间戳点击跳转
                onTimestampClick = { positionMs ->
                    playerState.player.seekTo(positionMs)
                    playerState.player.play()
                    commentViewModel.closeSubReply()  // 关闭弹窗以便看视频
                },
                // [#14修复] 图片预览回调
                onImagePreview = { images, index, rect ->
                    subReplyPreviewImages = images
                    subReplyPreviewIndex = index
                    subReplySourceRect = rect
                    subReplyShowImagePreview = true
                }
            )
        }
        
        // 🎉 点赞成功爆裂动画
        val likeBurstVisible by viewModel.likeBurstVisible.collectAsState()
        if (likeBurstVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-50).dp)
            ) {
                LikeBurstAnimation(
                    visible = true,
                    onAnimationEnd = { viewModel.dismissLikeBurst() }
                )
            }
        }
        
        // 🎉 三连成功庆祝动画
        val tripleCelebrationVisible by viewModel.tripleCelebrationVisible.collectAsState()
        if (tripleCelebrationVisible) {
            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                TripleSuccessAnimation(
                    visible = true,
                    onAnimationEnd = { viewModel.dismissTripleCelebration() }
                )
            }
        }
        
        //  居中弹窗提示（关注/收藏反馈）
        androidx.compose.animation.AnimatedVisibility(
            visible = popupMessage != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                tonalElevation = 8.dp
            ) {
                Text(
                    text = popupMessage ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// VideoContentSection 已提取到 VideoContentSection.kt
// VideoTagsRow 和 VideoTagChip 也已提取到 VideoContentSection.kt
