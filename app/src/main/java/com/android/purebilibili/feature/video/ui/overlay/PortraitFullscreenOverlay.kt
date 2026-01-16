package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.core.theme.BiliPink

/**
 * 竖屏全屏覆盖层
 *
 * 为竖屏视频提供沉浸式全屏体验
 * 包含：
 * - 顶部栏：返回 + 标题 + 沉浸模式开关
 * - 右侧栏：点赞/投币/收藏
 * - 底部栏：复用横屏控制栏 (LandscapeBottomControlBar)
 */
@Composable
fun PortraitFullscreenOverlay(
    title: String,
    isPlaying: Boolean,
    progress: PlayerProgress,
    
    // 互动状态
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    
    // 控制状态
    currentSpeed: Float,
    currentQualityLabel: String,
    currentRatio: VideoAspectRatio,
    danmakuEnabled: Boolean,
    isStatusBarHidden: Boolean, // 状态栏显示状态
    
    // 回调
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},  //  [新增] 拖动进度条开始回调（清除弹幕）
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onRatioClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuInputClick: () -> Unit,
    onToggleStatusBar: () -> Unit,
    
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(true) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        
        // 控件层 (带淡入淡出动画)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // 1. 顶部栏
                PortraitFullscreenTopBar(
                    title = title,
                    isStatusBarHidden = isStatusBarHidden,
                    onBack = onBack,
                    onToggleStatusBar = onToggleStatusBar,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                
                // 2. 右侧互动栏
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InteractionButton(
                        icon = if (isLiked) CupertinoIcons.Filled.Heart else CupertinoIcons.Default.Heart,
                        label = "点赞",
                        isActive = isLiked,
                        onClick = onLikeClick
                    )
                    
                    InteractionButton(
                        icon = com.android.purebilibili.core.ui.AppIcons.BiliCoin, // Custom BiliCoin icon
                        label = "投币",
                        isActive = isCoined,
                        activeColor = BiliPink,
                        onClick = onCoinClick
                    )
                    
                    InteractionButton(
                        icon = if (isFavorited) CupertinoIcons.Filled.Star else CupertinoIcons.Outlined.Star,
                        label = "收藏",
                        isActive = isFavorited,
                        activeColor = BiliPink,
                        onClick = onFavoriteClick
                    )
                }
                
                // 3. 底部控制栏
                LandscapeBottomControlBar(
                    isPlaying = isPlaying,
                    progress = progress,
                    currentSpeed = currentSpeed,
                    currentRatio = currentRatio,
                    danmakuEnabled = danmakuEnabled,
                    currentQualityLabel = currentQualityLabel,
                    onQualityClick = onQualityClick,
                    onPlayPauseClick = onPlayPause,
                    onSeek = onSeek,
                    onSeekStart = onSeekStart,  //  传递拖动开始回调
                    onSpeedClick = onSpeedClick,
                    onRatioClick = onRatioClick,
                    onDanmakuToggle = onDanmakuToggle,
                    onDanmakuInputClick = onDanmakuInputClick,
                    onToggleFullscreen = onBack, // 点击全屏按钮也是退出
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * 竖屏全屏顶部栏
 */
@Composable
private fun PortraitFullscreenTopBar(
    title: String,
    isStatusBarHidden: Boolean,
    onBack: () -> Unit,
    onToggleStatusBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            // 📱 [优化] 状态栏隐藏时不需要 padding，让内容贴近顶部
            .then(if (!isStatusBarHidden) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 8.dp, vertical = if (isStatusBarHidden) 12.dp else 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 返回按钮
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = CupertinoIcons.Default.ChevronBackward,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            
            // 标题
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            
            // 状态栏开关 (沉浸模式)
            IconButton(onClick = onToggleStatusBar) {
                Icon(
                    imageVector = if (isStatusBarHidden) CupertinoIcons.Default.EyeSlash else CupertinoIcons.Default.Eye,
                    contentDescription = "切换状态栏",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * 互动按钮组件
 */
@Composable
private fun InteractionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = BiliPink,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        ) { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.background(
                color = Color.Black.copy(alpha = 0.2f), 
                shape = RoundedCornerShape(4.dp)
            ).padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
