// 文件路径: feature/home/components/iOSHomeHeader.kt
package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.iOSTapEffect
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.core.theme.iOSSystemGray
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurIntensity

/**
 *  简洁版首页头部 (带滚动隐藏/显示动画)
 * 
 * 注意：Header 不使用 hazeChild 模糊效果（会导致渲染问题）
 * 磨砂效果仅保留给 BottomBar（在屏幕底部可以正常工作）
 * hazeState 参数保留以保持 API 兼容性
 */
@Composable
fun iOSHomeHeader(
    scrollOffset: Float,
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    categoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onPartitionClick: () -> Unit = {},  //  新增：分区按钮回调
    isScrollingUp: Boolean = true,
    collapseThreshold: androidx.compose.ui.unit.Dp = 60.dp,
    hazeState: HazeState? = null,  // 保留参数兼容性，但不用于模糊
    onStatusBarDoubleTap: () -> Unit = {},
    //  [新增] 下拉刷新状态
    isRefreshing: Boolean = false,
    pullProgress: Float = 0f  // 0.0 ~ 1.0+ 下拉进度
) {
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current

    // 计算滚动进度
    // 计算滚动进度
    val maxOffsetPx = with(density) { 50.dp.toPx() }
    val scrollProgress = (scrollOffset / maxOffsetPx).coerceIn(0f, 1f)
    
    //  [优化] 下拉刷新时强制展开标签页
    //  防止下拉回弹时的微小滚动偏移以及刷新状态下标签页消失
    val progress = if (pullProgress > 0f || isRefreshing) 0f else scrollProgress
    
    // 状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val searchBarHeight = 52.dp
    val totalHeaderTopPadding = statusBarHeight + searchBarHeight
    
    // 背景颜色 - 始终使用实心背景
    // 背景颜色 - 始终使用实心背景
    // val bgColor = MaterialTheme.colorScheme.surface // [Deleted]

    //  读取当前模糊强度以确定背景透明度
    val blurIntensity by SettingsManager.getBlurIntensity(LocalContext.current)
        .collectAsState(initial = BlurIntensity.THIN)
    val backgroundAlpha = BlurStyles.getBackgroundAlpha(blurIntensity)

    //  [优化] 使用 blurIntensity 对应的背景透明度实现毛玻璃质感
    val headerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (hazeState != null) backgroundAlpha else 1f)
    
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ===== 分类标签栏 =====
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = totalHeaderTopPadding)
                .then(if (hazeState != null) Modifier.unifiedBlur(hazeState) else Modifier)
                .background(headerColor)
        ) {
            CategoryTabRow(
                selectedIndex = categoryIndex,
                onCategorySelected = onCategorySelected,
                onPartitionClick = onPartitionClick
            )
        }

        // ===== 搜索栏区域 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .then(if (hazeState != null) Modifier.unifiedBlur(hazeState) else Modifier)
                .background(headerColor)
        ) {
            // 状态栏占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)  // [修复] 明确设置高度以接收点击事件
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic(HapticType.LIGHT) // 单击震动反馈
                                onStatusBarDoubleTap()
                            }
                        )
                    }
            )
            
            // 搜索栏 + 头像 + 设置
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像 - HIG: 最小触摸目标 44pt
                Box(
                    modifier = Modifier
                        .size(44.dp)  // HIG 最小触摸目标
                        .iOSTapEffect { onAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)  // 实际头像尺寸
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        if (user.isLogin && user.face.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(FormatUtils.fixImageUrl(user.face))
                                    .crossfade(true).build(),
                                contentDescription = "用户头像",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("未", fontSize = 11.sp, fontWeight = FontWeight.Bold, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))  // 调整间距适配更大触摸区域
                
                //  搜索框 - iOS 风格 (HIG: 圆角 10pt) - 优化柔和半透明效果
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))  // HIG 标准圆角
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))  // 更柔和的半透明
                        .clickable { 
                            haptic(HapticType.LIGHT)
                            onSearchClick() 
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            CupertinoIcons.Default.MagnifyingGlass,
                            contentDescription = "搜索",
                            tint = iOSSystemGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "搜索视频、UP主...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = iOSSystemGray,
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 设置按钮 - HIG: 最小触摸目标 44pt
                IconButton(
                    onClick = { 
                        haptic(HapticType.LIGHT)
                        onSettingsClick() 
                    },
                    modifier = Modifier.size(44.dp)  // HIG 最小触摸目标
                ) {
                    Icon(
                        CupertinoIcons.Default.Gear,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)  // 稍大图标
                    )
                }
            }
            }
        }
    }

