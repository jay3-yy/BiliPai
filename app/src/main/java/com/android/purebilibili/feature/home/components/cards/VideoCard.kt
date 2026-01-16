package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.clickable
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
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.iOSCornerRadius
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
//  [预览播放] 相关引用已移除

// 显式导入 collectAsState 以避免 ambiguity 或 missing reference
import androidx.compose.runtime.collectAsState

/**
 *  官方 B 站风格视频卡片
 * 采用与 Bilibili 官方 App 一致的设计：
 * - 封面 16:10 比例
 * - 左下角：播放量 + 弹幕数
 * - 右下角：时长
 * - 标题：2行
 * - 底部：「已关注」标签 + UP主名称
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ElegantVideoCard(
    video: VideoItem,
    index: Int,
    refreshKey: Long = 0L,
    isFollowing: Boolean = false,  //  是否已关注该 UP 主
    animationEnabled: Boolean = true,   //  卡片进场动画开关
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    showPublishTime: Boolean = false,   //  是否显示发布时间（搜索结果用）
    isDataSaverActive: Boolean = false, // 🚀 [性能优化] 从父级传入，避免每个卡片重复计算
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onClick: (String, Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    //  [HIG] 动态圆角 - 12dp 标准
    val cornerRadiusScale = LocalCornerRadiusScale.current
    val cardCornerRadius = 12.dp * cornerRadiusScale  // HIG 标准圆角
    val smallCornerRadius = iOSCornerRadius.Tiny * cornerRadiusScale  // 4.dp * scale
    
    //  [新增] 长按删除菜单状态
    var showDismissMenu by remember { mutableStateOf(false) }
    
    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }
    
    //  判断是否为竖屏视频（通过封面图 URL 中的尺寸信息或默认不显示）
    // B站封面 URL 通常包含尺寸信息，如 width=X&height=Y
    // 简单方案：暂不显示竖屏标签（因推荐API不提供视频尺寸信息）

    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val densityValue = density.density  //  [新增] 屏幕密度值
    
    //  记录卡片位置
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            //  [修复] 进场动画 - 使用 Unit 作为 key，只在首次挂载时播放
            // 原问题：使用 video.bvid 作为 key，分类切换时所有卡片重新触发动画（缩放收缩效果）
            .animateEnter(index = index, key = Unit, animationEnabled = animationEnabled)
            //  [新增] 记录卡片位置
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            //  [修改] 父级容器仅处理点击跳转 (或者点击由子 View 分别处理)
            //  为了避免冲突，我们将手势下放到子 View
            .padding(bottom = 12.dp)
    ) {
        //  尝试获取共享元素作用域
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
        
        //  封面容器 - 官方 B 站风格，支持共享元素过渡（受开关控制）
        val coverModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
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
                            RoundedCornerShape(cardCornerRadius)  //  过渡时保持动态圆角
                        )
                    )
            }
        } else {
            Modifier
        }
        
        Box(
            modifier = coverModifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(cardCornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(cardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                //  [交互优化] 封面区域：点击跳转
                .clickable {
                    cardBounds?.let { bounds ->
                        CardPositionManager.recordCardPosition(bounds, screenWidthPx, screenHeightPx, density = densityValue)
                    }
                    onClick(video.bvid, 0)
                }
        ) {
            // 🚀 [性能优化] 使用从父级传入的 isDataSaverActive，避免每个卡片重复计算
            val imageWidth = if (isDataSaverActive) 240 else 360
            val imageHeight = if (isDataSaverActive) 150 else 225
            
            // 封面图 -  [性能优化] 降低图片尺寸
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .size(imageWidth, imageHeight)  // � 省流量时使用更小尺寸
                    .crossfade(100)  //  缩短淡入时间
                    .memoryCacheKey("cover_${video.bvid}_${if (isDataSaverActive) "s" else "n"}")
                    .diskCacheKey("cover_${video.bvid}_${if (isDataSaverActive) "s" else "n"}")
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            

            
            //  底部渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
            
            //  时长标签 - 右下角 (官方风格)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shape = RoundedCornerShape(smallCornerRadius),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            
            //  播放量和弹幕数 - 左下角 (官方风格)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 播放量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "▶",
                        color = Color.White.copy(0.9f),
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (video.stat.view > 0) FormatUtils.formatStat(video.stat.view.toLong())
                               else FormatUtils.formatProgress(video.progress, video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                
                // 弹幕数
                if (video.stat.view > 0 && video.stat.danmaku > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "",
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                            color = Color.White.copy(0.9f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        //  [HIG] 标题 - 15sp Medium, 行高 20sp
        Text(
            text = video.title,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,  // HIG body 标准
                lineHeight = 20.sp,  // HIG 行高
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .semantics { contentDescription = "视频标题: ${video.title}" }
                //  [交互优化] 标题区域：长按弹出惨淡，点击跳转
                .pointerInput(onDismiss) {
                    if (onDismiss != null) {
                        detectTapGestures(
                            onLongPress = {
                                haptic(HapticType.HEAVY)
                                showDismissMenu = true
                            },
                            onTap = {
                                cardBounds?.let { bounds ->
                                    CardPositionManager.recordCardPosition(bounds, screenWidthPx, screenHeightPx, density = densityValue)
                                }
                                onClick(video.bvid, 0)
                            }
                        )
                    } else {
                        // 如果没有 onDismiss 回调，则只处理点击
                        detectTapGestures(
                            onTap = {
                                cardBounds?.let { bounds ->
                                    CardPositionManager.recordCardPosition(bounds, screenWidthPx, screenHeightPx, density = densityValue)
                                }
                                onClick(video.bvid, 0)
                            }
                        )
                    }
                }
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        //  底部信息行 - 官方 B 站风格
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //  已关注标签（红色文字，官方风格）
            if (isFollowing) {
                Text(
                    text = "已关注",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFB7299)  // B站粉红色
                )
            }
            
            //  UP主头像（小圆形，官方风格）
            if (video.owner.face.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(video.owner.face))
                        .crossfade(100)
                        .size(32, 32)
                        //  [修复] 使用 face URL hashCode 作为缓存 key
                        // 原因: 历史记录的 owner.mid 可能为空，导致所有头像共享同一缓存
                        .memoryCacheKey("avatar_${video.owner.face.hashCode()}")
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
            
            //  [HIG] UP主名称 - 13sp footnote 标准
            Text(
                text = video.owner.name,
                fontSize = 13.sp,  // HIG footnote 标准
                fontWeight = FontWeight.Normal,
                color = iOSSystemGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            
            //  发布时间（搜索结果显示）
            if (showPublishTime && video.pubdate > 0) {
                Text(
                    text = " · ${FormatUtils.formatPublishTime(video.pubdate)}",
                    fontSize = 11.sp,
                    color = iOSSystemGray.copy(alpha = 0.7f)
                )
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

/**
 * 简化版视频网格项 (用于搜索结果等)
 * 注意: onClick 只接收 bvid，不接收 cid
 */
@Composable
fun VideoGridItem(video: VideoItem, index: Int, onClick: (String) -> Unit) {
    ElegantVideoCard(video, index) { bvid, _ -> onClick(bvid) }
}
