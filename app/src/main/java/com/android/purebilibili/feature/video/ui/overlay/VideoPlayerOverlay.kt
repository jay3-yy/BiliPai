// 文件路径: feature/video/VideoPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils
// Import reusable components from standalone files
import com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenu
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.AspectRatioMenu
import com.android.purebilibili.feature.video.ui.components.VideoSettingsPanel
import com.android.purebilibili.feature.video.ui.components.ChapterListPanel
import com.android.purebilibili.data.model.response.ViewPoint
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.util.ShareUtils


@Composable
fun VideoPlayerOverlay(
    player: Player,
    title: String,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    isFullscreen: Boolean,
    currentQualityLabel: String,
    qualityLabels: List<String>,
    qualityIds: List<Int> = emptyList(),
    isLoggedIn: Boolean = false,
    onQualitySelected: (Int) -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    showStats: Boolean = false,
    realResolution: String = "",
    isQualitySwitching: Boolean = false,
    isBuffering: Boolean = false,  // 缓冲状态
    isVip: Boolean = false,
    //  [新增] 弹幕开关和设置
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    danmakuOpacity: Float = 0.85f,
    danmakuFontScale: Float = 1.0f,
    danmakuSpeed: Float = 1.0f,
    danmakuDisplayArea: Float = 0.5f,
    onDanmakuOpacityChange: (Float) -> Unit = {},
    onDanmakuFontScaleChange: (Float) -> Unit = {},
    onDanmakuSpeedChange: (Float) -> Unit = {},
    onDanmakuDisplayAreaChange: (Float) -> Unit = {},
    //  [实验性功能] 双击点赞
    doubleTapLikeEnabled: Boolean = true,
    onDoubleTapLike: () -> Unit = {},
    //  视频比例调节
    currentAspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    onAspectRatioChange: (VideoAspectRatio) -> Unit = {},
    // 🔗 [新增] 分享功能
    bvid: String = "",
    onShare: (() -> Unit)? = null,
    //  [新增] 视频设置面板回调
    onReloadVideo: () -> Unit = {},
    sleepTimerMinutes: Int? = null,
    onSleepTimerChange: (Int?) -> Unit = {},
    isFlippedHorizontal: Boolean = false,
    isFlippedVertical: Boolean = false,
    onFlipHorizontal: () -> Unit = {},
    onFlipVertical: () -> Unit = {},
    isAudioOnly: Boolean = false,
    onAudioOnlyToggle: () -> Unit = {},
    //  [新增] 画质列表和回调
    onQualityChange: (Int, Long) -> Unit = { _, _ -> },
    //  [新增] CDN 线路切换
    currentCdnIndex: Int = 0,
    cdnCount: Int = 1,
    onSwitchCdn: () -> Unit = {},
    onSwitchCdnTo: (Int) -> Unit = {},
    // 🖼️ [新增] 视频预览图数据
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    // 📖 [新增] 视频章节数据
    viewPoints: List<ViewPoint> = emptyList(),
    // 📱 [新增] 竖屏全屏模式
    isVerticalVideo: Boolean = false,
    onPortraitFullscreen: () -> Unit = {},
    // 📲 [新增] 小窗模式
    onPipClick: () -> Unit = {}
) {
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }  //  新增
    var showChapterList by remember { mutableStateOf(false) }  // 📖 章节列表
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    //  使用传入的比例状态
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    
    //  双击检测状态
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showLikeAnimation by remember { mutableStateOf(false) }

    val progressState by produceState(initialValue = PlayerProgress(), key1 = player, key2 = isVisible) {
        while (isActive) {
            //  [修复] 始终更新进度，不仅在播放时
            // 这样横竖屏切换后也能显示正确的进度
            val duration = if (player.duration < 0) 0L else player.duration
            value = PlayerProgress(
                current = player.currentPosition,
                duration = duration,
                buffered = player.bufferedPosition
            )
            isPlaying = player.isPlaying
            val delayMs = if (isVisible && player.isPlaying) 200L else 500L
            delay(delayMs)
        }
    }
    
    // 📖 计算当前章节（必须在 progressState 之后定义）
    val currentChapter = remember(progressState.current, viewPoints) {
        if (viewPoints.isEmpty()) null
        else viewPoints.lastOrNull { progressState.current >= it.fromMs }?.content
    }

    LaunchedEffect(isVisible, isPlaying) {
        if (isVisible && isPlaying) {
            delay(4000)
            if (isVisible) {
                onToggleVisible()
            }
        }
    }
    
    //  双击点赞动画自动消失
    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(800)
            showLikeAnimation = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. 顶部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            //  [修复] align 必须在 AnimatedVisibility 的 modifier 上，而不是内部 Box 上
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // --- 2. 底部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }

        // --- 3. 控制栏内容 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            //  [修复] 确保 AnimatedVisibility 填充整个父容器
            modifier = Modifier.fillMaxSize()
        ) {
            //  [修复] 使用 Box 分别定位顶部和底部控制栏
            Box(modifier = Modifier.fillMaxSize()) {
                //  顶部控制栏 - 仅在横屏（全屏）模式显示标题和清晰度
                if (isFullscreen) {
                    TopControlBar(
                        title = title,
                        isFullscreen = isFullscreen,
                        currentQualityLabel = currentQualityLabel,
                        onBack = onBack,
                        onQualityClick = { showQualityMenu = true },
                        //  弹幕开关和设置
                        danmakuEnabled = danmakuEnabled,
                        onDanmakuToggle = onDanmakuToggle,
                        onDanmakuSettingsClick = { showDanmakuSettings = true },
                        //  [修复] 传入 modifier 确保在顶部
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else {
                    //  [新增] 竖屏模式顶部栏（返回 + 画质 + 设置 + 分享按钮）
                    val context = LocalContext.current
                    PortraitTopBar(
                        onBack = onBack,
                        onSettings = { showVideoSettings = true },
                        onShare = onShare ?: {
                            if (bvid.isNotEmpty()) {
                                ShareUtils.shareVideo(context, title, bvid)
                            }
                        },
                        onAudioMode = onAudioOnlyToggle,
                        isAudioOnly = isAudioOnly,
                        // 📱 [新增] 画质选择移到左上角
                        currentQualityLabel = currentQualityLabel,
                        onQualityClick = { showQualityMenu = true },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                
                //  [修复] 底部控制栏 - 固定在底部
                BottomControlBar(
                    isPlaying = isPlaying,
                    progress = progressState,
                    isFullscreen = isFullscreen,
                    currentSpeed = currentSpeed,
                    currentRatio = currentAspectRatio,
                    onPlayPauseClick = {
                        // 检查播放器是否处于完成状态
                        if (player.playbackState == Player.STATE_ENDED) {
                            // 如果播放完成，先重置到开头，再重新播放
                            player.seekTo(0)
                            player.play()
                            isPlaying = true
                        } else if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.play()
                            isPlaying = true
                        }
                    },
                    onSeek = { position -> player.seekTo(position) },
                    onSpeedClick = { showSpeedMenu = true },
                    onRatioClick = { showRatioMenu = true },
                    onToggleFullscreen = onToggleFullscreen,
                    //  [新增] 竖屏模式弹幕和清晰度控制
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = onDanmakuToggle,
                    currentQualityLabel = currentQualityLabel,
                    onQualityClick = { showQualityMenu = true },
                    // 🖼️ [新增] 视频预览图数据
                    videoshotData = videoshotData,
                    // 📖 [新增] 视频章节数据
                    viewPoints = viewPoints,
                    currentChapter = currentChapter,
                    onChapterClick = { showChapterList = true },
                    // 📱 [新增] 竖屏全屏模式
                    isVerticalVideo = isVerticalVideo,
                    onPortraitFullscreen = onPortraitFullscreen,
                    // 📲 [新增] 小窗模式
                    onPipClick = onPipClick,
                    //  [修复] 传入 modifier 确保在底部
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }

        // --- 4.  [新增] 真实分辨率统计信息 (仅在设置开启时显示) ---
        if (showStats && realResolution.isNotEmpty() && isVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 24.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Resolution: $realResolution",
                    color = Color.Green,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // --- 5. 中央播放/暂停大图标 (仅全屏模式显示) ---
        AnimatedVisibility(
            visible = isVisible && !isPlaying && !isQualitySwitching && isFullscreen,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(tween(250)) + fadeIn(tween(200)),
            exit = scaleOut(tween(200)) + fadeOut(tween(200))
        ) {
            Surface(
                onClick = { player.play(); isPlaying = true },
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        CupertinoIcons.Default.Play,
                        contentDescription = "播放",
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        // --- 5.4  缓冲加载指示器 ---
        AnimatedVisibility(
            visible = isBuffering && !isQualitySwitching && !isVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            CupertinoActivityIndicator()
        }

        // --- 5.5  清晰度切换中 Loading 指示器 ---
        AnimatedVisibility(
            visible = isQualitySwitching,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    //  iOS 风格加载器
                    CupertinoActivityIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在切换清晰度...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 6. 清晰度菜单 ---
        if (showQualityMenu) {
            QualitySelectionMenu(
                qualities = qualityLabels,
                qualityIds = qualityIds,
                currentQuality = currentQualityLabel,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                onQualitySelected = { index ->
                    onQualitySelected(index)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false }
            )
        }
        
        // --- 7.  [新增] 倍速选择菜单 ---
        if (showSpeedMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showSpeedMenu = false },
                contentAlignment = Alignment.Center
            ) {
                SpeedSelectionMenu(
                    currentSpeed = currentSpeed,
                    onSpeedSelected = { speed ->
                        currentSpeed = speed
                        player.setPlaybackSpeed(speed)
                        showSpeedMenu = false
                    },
                    onDismiss = { showSpeedMenu = false }
                )
            }
        }
        
        // --- 7.5  [新增] 视频比例选择菜单 ---
        if (showRatioMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showRatioMenu = false },
                contentAlignment = Alignment.Center
            ) {
                AspectRatioMenu(
                    currentRatio = currentAspectRatio,
                    onRatioSelected = { ratio ->
                        onAspectRatioChange(ratio)
                        showRatioMenu = false
                    },
                    onDismiss = { showRatioMenu = false }
                )
            }
        }
        
        // --- 8.  [新增] 弹幕设置面板 ---
        if (showDanmakuSettings) {
            DanmakuSettingsPanel(
                opacity = danmakuOpacity,
                fontScale = danmakuFontScale,
                speed = danmakuSpeed,
                displayArea = danmakuDisplayArea,
                onOpacityChange = onDanmakuOpacityChange,
                onFontScaleChange = onDanmakuFontScaleChange,
                onSpeedChange = onDanmakuSpeedChange,
                onDisplayAreaChange = onDanmakuDisplayAreaChange,
                onDismiss = { showDanmakuSettings = false }
            )
        }
        
        // --- 9.  [新增] 视频设置面板 ---
        if (showVideoSettings) {
            VideoSettingsPanel(
                sleepTimerMinutes = sleepTimerMinutes,
                onSleepTimerChange = onSleepTimerChange,
                onReload = onReloadVideo,
                currentQualityLabel = currentQualityLabel,
                qualityLabels = qualityLabels,
                qualityIds = qualityIds,
                onQualitySelected = { index ->
                    val id = qualityIds.getOrNull(index) ?: 0
                    onQualityChange(id, 0L)  // 位置由上层处理
                    showVideoSettings = false
                },
                currentSpeed = currentSpeed,
                onSpeedChange = { speed ->
                    currentSpeed = speed
                    player.setPlaybackSpeed(speed)
                },
                isFlippedHorizontal = isFlippedHorizontal,
                isFlippedVertical = isFlippedVertical,
                onFlipHorizontal = onFlipHorizontal,
                onFlipVertical = onFlipVertical,
                isAudioOnly = isAudioOnly,
                onAudioOnlyToggle = onAudioOnlyToggle,
                //  CDN 线路切换
                currentCdnIndex = currentCdnIndex,
                cdnCount = cdnCount,
                onSwitchCdn = onSwitchCdn,
                onSwitchCdnTo = { index ->
                    onSwitchCdnTo(index)
                    showVideoSettings = false
                },
                onDismiss = { showVideoSettings = false }
            )
        }
        
        // --- 10. 📖 [新增] 章节列表面板 ---
        if (showChapterList && viewPoints.isNotEmpty()) {
            ChapterListPanel(
                viewPoints = viewPoints,
                currentPositionMs = progressState.current,
                onSeek = { position -> player.seekTo(position) },
                onDismiss = { showChapterList = false }
            )
        }
    }
}

/**
 *  竖屏模式顶部控制栏
 * 
 * 包含返回首页按钮、画质选择、设置按钮和分享按钮
 */
@Composable
private fun PortraitTopBar(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onAudioMode: () -> Unit,
    isAudioOnly: Boolean,
    // 📱 [新增] 画质选择 - 移到左上角
    currentQualityLabel: String = "",
    onQualityClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：返回按钮 + 画质选择
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 返回按钮 - 简洁无背景
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.ChevronBackward,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // 📱 画质选择按钮 - 移到左上角
            if (currentQualityLabel.isNotEmpty()) {
                Surface(
                    onClick = onQualityClick,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = currentQualityLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        // 右侧按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //  听视频模式按钮 - 激活时保留背景色
            IconButton(
                onClick = onAudioMode,
                modifier = Modifier
                    .size(32.dp)
                    .then(
                        if (isAudioOnly) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.Headphones,
                    contentDescription = "听视频",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            //  设置按钮 - 无背景
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.Ellipsis,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // 分享按钮 - 无背景
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.SquareAndArrowUp,
                    contentDescription = "分享",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
