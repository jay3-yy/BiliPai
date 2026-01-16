// 文件路径: feature/home/components/cards/StoryVideoCard.kt
package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.iOSCardTapEffect
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring

import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.iOSCornerRadius

/**
 *  故事卡片 - Apple TV+ 风格
 * 
 * 特点：
 * - 2:1 电影宽屏比例
 * - 大圆角 (24dp)
 * - 标题叠加在封面底部
 * - 沉浸电影感
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StoryVideoCard(
    video: VideoItem,
    index: Int = 0,  //  [新增] 索引用于动画延迟
    animationEnabled: Boolean = true,  //  卡片动画开关
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onClick: (String, Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    
    // [新增] 获取圆角缩放比例
    val cornerRadiusScale = LocalCornerRadiusScale.current
    val cardCornerRadius = iOSCornerRadius.ExtraLarge * cornerRadiusScale  // 20.dp * scale
    val smallCornerRadius = iOSCornerRadius.Small * cornerRadiusScale - 2.dp  // 8.dp * scale
    
    //  [新增] 长按删除菜单状态
    var showDismissMenu by remember { mutableStateOf(false) }
    
    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }
    
    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    //  记录卡片位置
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    //  尝试获取共享元素作用域
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    //  卡片容器 - 支持共享元素过渡（受开关控制）
    val cardModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "video_cover_${video.bvid}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    //  添加回弹效果的 spring 动画
                    boundsTransform = { _, _ ->
                        spring(
                            dampingRatio = 0.7f,   // 轻微回弹
                            stiffness = 300f       // 适中速度
                        )
                    },
                    clipInOverlayDuringTransition = OverlayClip(
                        RoundedCornerShape(cardCornerRadius)  // 过渡时保持动态圆角
                    )
                )
        }
    } else {
        Modifier
    }

    Box(
        modifier = cardModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            //  [修复] 进场动画 - 使用 Unit 作为 key，避免分类切换时重新动画
            .animateEnter(index = index, key = Unit, animationEnabled = animationEnabled)
            //  [新增] 记录卡片位置
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(cardCornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(cardCornerRadius))
            .background(Color.Black)
            //  [新增] 长按手势检测
            .pointerInput(onDismiss) {
                if (onDismiss != null) {
                    detectTapGestures(
                        onLongPress = {
                            haptic(HapticType.HEAVY)
                            showDismissMenu = true
                        },
                        onTap = {
                            cardBounds?.let { bounds ->
                                CardPositionManager.recordCardPosition(
                                    bounds, screenWidthPx, screenHeightPx, 
                                    isSingleColumn = !transitionEnabled
                                )
                            }
                            onClick(video.bvid, 0)
                        }
                    )
                }
            }
            .then(
                if (onDismiss == null) {
                    Modifier.iOSCardTapEffect(
                        pressScale = 0.97f,
                        pressTranslationY = 10f,
                        hapticEnabled = true
                    ) {
                        cardBounds?.let { bounds ->
                            CardPositionManager.recordCardPosition(
                                bounds, screenWidthPx, screenHeightPx, 
                                isSingleColumn = !transitionEnabled
                            )
                        }
                        onClick(video.bvid, 0)
                    }
                } else Modifier
            )
    ) {
        //  封面 - 2:1 电影宽屏
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(150)
                .memoryCacheKey("story_${video.bvid}")
                .diskCacheKey("story_${video.bvid}")
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 1f)
                .clip(RoundedCornerShape(cardCornerRadius)),  // 图片也要 clip
            contentScale = ContentScale.Crop
        )
        
        //  底部渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 1f)
                .clip(RoundedCornerShape(cardCornerRadius))  // 遮罩也要 clip
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        
        //  时长标签
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(smallCornerRadius)  // 动态圆角
        ) {
            Text(
                text = FormatUtils.formatDuration(video.duration),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        
        //  底部信息区
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题 - 大字体
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // UP主信息 + 数据
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // UP主头像
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(video.owner.face))
                        .crossfade(100)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // UP主名称
                Text(
                    text = video.owner.name,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 播放量 -  [修复] 只在有播放量时显示
                if (video.stat.view > 0) {
                    Text(
                        text = "${FormatUtils.formatStat(video.stat.view.toLong())}播放",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // 弹幕 -  [修复] 只在有弹幕时显示
                if (video.stat.danmaku > 0) {
                    Text(
                        text = "${FormatUtils.formatStat(video.stat.danmaku.toLong())}弹幕",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
    
    //  [新增] 长按删除菜单
    DropdownMenu(
        expanded = showDismissMenu,
        onDismissRequest = { showDismissMenu = false }
    ) {
        DropdownMenuItem(
            text = { 
                Text(
                    "🚫 不感兴趣",
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            onClick = {
                showDismissMenu = false
                onDismiss?.invoke()
            }
        )
    }
}
