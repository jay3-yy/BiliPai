// 文件路径: feature/home/components/TopBar.kt
package com.android.purebilibili.feature.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.UserState

/**
 * Q弹点击效果
 */
fun Modifier.premiumClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "scale"
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/**
 *  iOS 风格悬浮顶栏
 * - 不贴边，有水平边距
 * - 圆角 + 毛玻璃效果
 */
@Composable
fun FluidHomeTopBar(
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        //  悬浮式导航栏容器 - 增强视觉层次
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,  //  使用主题色，适配深色模式
            shadowElevation = 6.dp,  // 添加阴影增加层次感
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp) // 稍微减小高度
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //  左侧：头像
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .premiumClickable { onAvatarClick() }
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    if (user.isLogin && user.face.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(user.face))
                                .crossfade(true).build(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                //  中间：搜索框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSearchClick() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            CupertinoIcons.Default.MagnifyingGlass,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "搜索视频、UP主...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                //  右侧：设置按钮
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        CupertinoIcons.Default.Gear,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 *  [HIG] iOS 风格分类标签栏
 * - 限制可见标签为 4 个主要分类 (HIG 建议 3-5 个)
 * - 其余分类收入"更多"下拉菜单
 * - 圆角胶囊选中指示器
 * - 最小触摸目标 44pt
 */
@Composable
fun CategoryTabRow(
    //  [修复] 分类列表必须与 HomeCategory 枚举顺序完全匹配！
    // HomeCategory: RECOMMEND, FOLLOW, POPULAR, LIVE, ANIME, MOVIE, GAME, KNOWLEDGE, TECH
    categories: List<String> = listOf("推荐", "关注", "热门", "直播", "追番", "影视", "游戏", "知识", "科技"),
    selectedIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    onPartitionClick: () -> Unit = {}  //  新增：分区按钮回调
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    
    //  [HIG] 限制主要可见标签数量 - 前4个为主要分类
    val primaryCount = 4
    val primaryCategories = categories.take(primaryCount)
    val moreCategories = categories.drop(primaryCount)
    
    //  "更多"菜单展开状态
    var showMoreMenu by remember { mutableStateOf(false) }
    
    //  判断当前选中项是否在"更多"菜单中
    val isMoreSelected = selectedIndex >= primaryCount
    val moreLabel = if (isMoreSelected && selectedIndex < categories.size) {
        categories[selectedIndex]
    } else {
        "更多"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //  主要分类标签 - 固定显示
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            primaryCategories.forEachIndexed { index, category ->
                val isSelected = index == selectedIndex
                
                //  [HIG] iOS 风格胶囊选中背景
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "bgColor"
                )
                
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor else unselectedColor,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "textColor"
                )
                
                Box(
                    modifier = Modifier
                        .height(36.dp)  // HIG 触摸目标
                        .clip(RoundedCornerShape(18.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCategorySelected(index) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        color = textColor,
                        fontSize = if (isSelected) 15.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            
            //  "更多" 下拉菜单按钮 (只有当有额外分类时显示)
            if (moreCategories.isNotEmpty()) {
                Box {
                    val isMoreActive = isMoreSelected || showMoreMenu
                    
                    val moreBgColor by animateColorAsState(
                        targetValue = if (isMoreActive) primaryColor.copy(alpha = 0.12f) else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "moreBgColor"
                    )
                    
                    val moreTextColor by animateColorAsState(
                        targetValue = if (isMoreActive) primaryColor else unselectedColor,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "moreTextColor"
                    )
                    
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(moreBgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMoreMenu = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = moreLabel,
                            color = moreTextColor,
                            fontSize = if (isMoreSelected) 15.sp else 14.sp,
                            fontWeight = if (isMoreSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            CupertinoIcons.Default.ChevronDown,
                            contentDescription = "展开更多分类",
                            tint = moreTextColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    
                    //  iOS 风格下拉菜单 - 毛玻璃风格
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier
                            .width(150.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        // 分类图标映射
                        val categoryIcons = mapOf(
                            "追番" to CupertinoIcons.Default.Film,
                            "影视" to CupertinoIcons.Default.Tv,
                            "游戏" to CupertinoIcons.Default.Gamecontroller,
                            "知识" to CupertinoIcons.Default.Book,
                            "科技" to CupertinoIcons.Default.Cpu
                        )
                        
                        moreCategories.forEachIndexed { index, category ->
                            val actualIndex = primaryCount + index
                            val isThisSelected = actualIndex == selectedIndex
                            val icon = categoryIcons[category] ?: CupertinoIcons.Default.Folder
                            
                            // 菜单项
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isThisSelected) primaryColor.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onCategorySelected(actualIndex)
                                        showMoreMenu = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 图标
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isThisSelected) primaryColor 
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                // 文字
                                Text(
                                    text = category,
                                    color = if (isThisSelected) primaryColor 
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isThisSelected) FontWeight.SemiBold 
                                                 else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // 选中勾选
                                if (isThisSelected) {
                                    Icon(
                                        CupertinoIcons.Default.Checkmark,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        //  分区按钮 - HIG 44dp 触摸目标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPartitionClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                CupertinoIcons.Default.ListBullet,
                contentDescription = "浏览全部分区",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
