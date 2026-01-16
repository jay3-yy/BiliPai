// 文件路径: feature/onboarding/OnboardingBottomSheet.kt
package com.android.purebilibili.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.blur.unifiedBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
//  Lottie 动画
import com.airbnb.lottie.compose.*
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.LottieUrls

/**
 *  iOS 风格新手引导底部弹窗
 * 
 * 特色功能：
 * - 多页轮播引导
 * - 突出外观设置和播放设置
 * - 精美动画效果
 */


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnboardingBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    mainHazeState: HazeState //  接收来自 MainActivity 的全局 Haze 状态
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    
    // 3 页引导
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    //  弹窗局部 Haze 状态 (用于内部元素)
    val localHazeState = remember { HazeState() }
    
    //  控制进出场动画
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        //  1. 半透明遮罩层 (点击关闭)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
        ),
        exit = androidx.compose.animation.slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
        )
    ) {
        //  2. 内容层 (点击透传)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            //  iOS 风格毛玻璃效果
            // 使用多层渐变 + 高透明度模拟真实的毛玻璃质感
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f) //  占 85% 屏幕高度
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    //  [新方案] 多层背景模拟毛玻璃
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0xFF2C2C2E).copy(alpha = 0.95f),  // 深色主体
                                    Color(0xFF1C1C1E).copy(alpha = 0.98f)   // 底部更深
                                )
                            } else {
                                listOf(
                                    Color(0xFFF2F2F7).copy(alpha = 0.95f),  // iOS 浅灰
                                    Color(0xFFFFFFFF).copy(alpha = 0.98f)   // 底部更白
                                )
                            }
                        ),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    // 防止点击穿透到遮罩层
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} 
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                        .responsiveContentWidth(), // 📱 平板适配：限制内容宽度
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    //  iOS 风格拖拽指示器
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 8.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    
                    //  多页轮播内容
                    // 注意：不再作为 Haze 源，而是作为 Haze 的 Child 的内容
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // .haze(state = hazeState) //  移除旧的 Haze 源，因为现在它是全局 Haze 的一部分
                    ) { page ->
                        when (page) {
                            // 传入局部 Haze 状态给内部组件使用 (如果需要)
                            // 或者在这个场景下，内部列表项的模糊可能不需要了，或者可以改为普通的半透明
                            // 这里我们暂时保留 localHazeState 传递，虽然它现在没有连接到 Haze 源
                            // TODO: 如果需要内部元素再模糊背景，需要再套一层 Haze
                            0 -> WelcomePage(hazeState = localHazeState)
                            1 -> AppearanceSettingsPage(hazeState = localHazeState)
                            2 -> PlaybackSettingsPage(hazeState = localHazeState)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    //  页面指示器
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(3) { index ->
                            val isSelected = pagerState.currentPage == index
                            val width by animateFloatAsState(
                                targetValue = if (isSelected) 24f else 8f,
                                animationSpec = spring()
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(width = width.dp, height = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    //  底部按钮区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pagerState.currentPage < 2) {
                            // 跳过按钮
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Text("跳过", fontWeight = FontWeight.Medium)
                            }
                            
                            // 下一步按钮
                            Button(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("下一步", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            // 最后一页：开始使用
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    "开始探索 BiliPai",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    
                    //  GitHub 链接
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "github.com/jay3-yy/BiliPai",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/jay3-yy/BiliPai")
                        }
                    )
                }
            }
        }
    }
}

/**
 *  第一页：欢迎页
 */
@Composable
private fun WelcomePage(hazeState: HazeState) {
    //  iOS 风格交错入场动画
    val animatedItems = remember { List(6) { Animatable(0f) } }
    
    LaunchedEffect(Unit) {
        animatedItems.forEachIndexed { index, animatable ->
            delay(index * 80L) // 交错延迟
            launch {
                animatable.animateTo(
                    1f,
                    spring(dampingRatio = 0.65f, stiffness = 300f)
                )
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) //  可滚动
            .padding(horizontal = 32.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //  Lottie 欢迎动画 - 动画项 0
        val welcomeComposition by rememberLottieComposition(
            LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_touohxv0.json") // 欢迎/庆祝动画
        )
        val welcomeProgress by animateLottieCompositionAsState(
            composition = welcomeComposition,
            iterations = LottieConstants.IterateForever
        )
        
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    alpha = animatedItems[0].value
                    scaleX = 0.5f + animatedItems[0].value * 0.5f
                    scaleY = 0.5f + animatedItems[0].value * 0.5f
                },
            contentAlignment = Alignment.Center
        ) {
            // 主 Logo
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = "BiliPai Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            //  Lottie 装饰动画 (环绕效果)
            LottieAnimation(
                composition = welcomeComposition,
                progress = { welcomeProgress },
                modifier = Modifier.size(120.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 标题 - 动画项 1
        Text(
            "欢迎使用 BiliPai",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 副标题 - 动画项 2
        Text(
            "简洁 · 流畅 · 开源",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 3.sp,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 特性标签 - 动画项 3, 4, 5
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = animatedItems[3].value },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureBadge(
                emoji = "🎨", 
                label = "个性外观",
                animationProgress = animatedItems[3].value
            )
            FeatureBadge(
                emoji = "⚡", 
                label = "极速播放",
                animationProgress = animatedItems[4].value
            )
            FeatureBadge(
                emoji = "🔒", 
                label = "隐私优先",
                animationProgress = animatedItems[5].value
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 免责声明
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[5].value
                translationY = (1f - animatedItems[5].value) * 20f
            }
        ) {
            Text(
                "本应用仅供学习交流，所有内容版权归 Bilibili 及原作者。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                lineHeight = 16.sp
            )
        }
    }
}

/**
 *  第二页：外观设置介绍
 */
@Composable
private fun AppearanceSettingsPage(hazeState: HazeState) {
    //  iOS 风格交错入场动画
    val animatedItems = remember { List(7) { Animatable(0f) } }
    
    LaunchedEffect(Unit) {
        animatedItems.forEachIndexed { index, animatable ->
            delay(index * 80L)
            launch {
                animatable.animateTo(
                    1f,
                    spring(dampingRatio = 0.65f, stiffness = 300f)
                )
            }
        }
    }
    
    //  图标呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    //  Lottie 外观主题动画 - 彩虹渐变
    val themeComposition by rememberLottieComposition(
        LottieCompositionSpec.Url(LottieUrls.THEME_COLORS)
    )
    val themeProgress by animateLottieCompositionAsState(
        composition = themeComposition,
        iterations = LottieConstants.IterateForever
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) //  可滚动
            .padding(horizontal = 32.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //  Lottie 动画 - 动画项 0
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    alpha = animatedItems[0].value
                    scaleX = iconScale * (0.5f + animatedItems[0].value * 0.5f)
                    scaleY = iconScale * (0.5f + animatedItems[0].value * 0.5f)
                },
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = themeComposition,
                progress = { themeProgress },
                modifier = Modifier.size(100.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 标题 - 动画项 1
        Text(
            "个性化外观",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 副标题 - 动画项 2
        Text(
            "打造专属于你的界面风格",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 功能列表 - 动画项 3, 4, 5, 6
        FeatureListItem(
            icon = "🎨",
            title = "多种主题色",
            description = "粉色、蓝色、紫色...随心切换",
            animationProgress = animatedItems[3].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "🌙",
            title = "深色模式",
            description = "护眼夜间模式，跟随系统或手动切换",
            animationProgress = animatedItems[4].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "✨",
            title = "动态取色",
            description = "Android 12+ 支持系统壁纸取色",
            animationProgress = animatedItems[5].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "👁️",
            title = "护眼模式",
            description = "柔和屏幕色调，保护视力",
            animationProgress = animatedItems[6].value,
            hazeState = hazeState
        )
    }
}

/**
 *  第三页：播放设置介绍
 */
@Composable
private fun PlaybackSettingsPage(hazeState: HazeState) {
    //  iOS 风格交错入场动画
    val animatedItems = remember { List(7) { Animatable(0f) } }
    
    LaunchedEffect(Unit) {
        animatedItems.forEachIndexed { index, animatable ->
            delay(index * 80L)
            launch {
                animatable.animateTo(
                    1f,
                    spring(dampingRatio = 0.65f, stiffness = 300f)
                )
            }
        }
    }
    
    //  图标呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    //  Lottie 播放动画 - 视频播放按钮
    val playComposition by rememberLottieComposition(
        LottieCompositionSpec.Url(LottieUrls.VIDEO_PLAY)
    )
    val playProgress by animateLottieCompositionAsState(
        composition = playComposition,
        iterations = LottieConstants.IterateForever
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) //  可滚动
            .padding(horizontal = 32.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //  Lottie 动画 - 动画项 0
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    alpha = animatedItems[0].value
                    scaleX = iconScale * (0.5f + animatedItems[0].value * 0.5f)
                    scaleY = iconScale * (0.5f + animatedItems[0].value * 0.5f)
                },
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = playComposition,
                progress = { playProgress },
                modifier = Modifier.size(100.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 标题 - 动画项 1
        Text(
            "智能播放体验",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 副标题 - 动画项 2
        Text(
            "流畅观看，省流省电",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 功能列表 - 动画项 3, 4, 5, 6
        FeatureListItem(
            icon = "📺",
            title = "智能画质",
            description = "WiFi/流量自动切换画质，省流量模式可用",
            animationProgress = animatedItems[3].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "🖼️",
            title = "小窗播放",
            description = "边刷视频边聊天，多任务神器",
            animationProgress = animatedItems[4].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "👆",
            title = "手势控制",
            description = "左右滑动快进，上下调节音量亮度",
            animationProgress = animatedItems[5].value,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureListItem(
            icon = "❤️",
            title = "双击点赞",
            description = "双击画面快速点赞，设置中可开关",
            animationProgress = animatedItems[6].value,
            hazeState = hazeState
        )
    }
}

/**
 *  特性徽章（毛玻璃效果）
 */
@Composable
private fun FeatureBadge(
    emoji: String, 
    label: String,
    animationProgress: Float = 1f
) {
    //  徽章呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            alpha = animationProgress
            scaleX = 0.5f + animationProgress * 0.5f
            scaleY = 0.5f + animationProgress * 0.5f
        }
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            //  毛玻璃光晕背景
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .blur(12.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // 徽章主体
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 *  功能列表项（毛玻璃效果）
 */
@Composable
private fun FeatureListItem(
    icon: String,
    title: String,
    description: String,
    animationProgress: Float = 1f,
    hazeState: HazeState? = null
) {
    //  真正的毛玻璃卡片效果
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animationProgress
                translationX = (1f - animationProgress) * 50f // 从右侧滑入
                scaleX = 0.9f + animationProgress * 0.1f
                scaleY = 0.9f + animationProgress * 0.1f
            }
            .then(
                if (hazeState != null) {
                    Modifier.unifiedBlur(hazeState) //  应用 Haze 毛玻璃
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
