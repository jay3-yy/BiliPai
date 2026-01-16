package com.android.purebilibili.feature.settings


import android.widget.Toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.*
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.ChevronBackward
import io.github.alexzhirkevich.cupertino.icons.outlined.Info
import io.github.alexzhirkevich.cupertino.icons.filled.CheckmarkCircle

/**
 *  应用图标设置二级页面
 *  iOS 风格设计优化
 */

// 图标选项数据
data class IconOption(val key: String, val name: String, val desc: String, val iconRes: Int)

// 分组定义
data class IconGroup(val title: String, val icons: List<IconOption>)

// This function was implicitly requested to be moved here.
// Assuming its content based on common patterns for such a function.
fun getIconGroups(): List<IconGroup> {
    return listOf(
        IconGroup(
            title = "精选",
            icons = listOf(
                IconOption("icon_3d", "3D立体", "全新3D设计", R.mipmap.ic_launcher_3d_foreground),
                IconOption("Yuki", "比心少女", "经典二次元", R.mipmap.ic_launcher_round),
                IconOption("icon_anime", "蓝发电视", "bilibili风格", R.mipmap.ic_launcher_anime)
            )
        ),
        IconGroup(
            title = "经典设计",
            icons = listOf(
                IconOption("icon_blue", "经典蓝", "纯净蓝色", R.mipmap.ic_launcher_blue_round),
                IconOption("icon_retro", "复古", "怀旧电视", R.mipmap.ic_launcher_retro_round),
                IconOption("icon_neon", "霓虹", "赛博朋克", R.mipmap.ic_launcher_neon_round),
                IconOption("icon_flat", "扁平", "现代极简", R.mipmap.ic_launcher_flat_round),
            )
        ),
        IconGroup(
            title = "Telegram 风格",
            icons = listOf(
                IconOption("icon_telegram_blue", "纸飞机蓝", "Telegram 蓝", R.mipmap.ic_launcher_telegram_blue_round),
                IconOption("icon_telegram_green", "薄荷绿", "Telegram 绿", R.mipmap.ic_launcher_telegram_green_round),
                IconOption("icon_telegram_pink", "樱花粉", "Telegram 粉", R.mipmap.ic_launcher_telegram_pink_round),
                IconOption("icon_telegram_purple", "香芋紫", "Telegram 紫", R.mipmap.ic_launcher_telegram_purple_round),
                IconOption("icon_telegram_dark", "暗夜蓝", "Telegram 黑", R.mipmap.ic_launcher_telegram_dark_round),
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    val iconGroups = getIconGroups()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用图标", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), // iOS 分组背景色风格
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        IconSettingsContent(
            modifier = Modifier.padding(padding),
            state = state,
            viewModel = viewModel,
            context = context,
            iconGroups = iconGroups
        )
    }
}

// 提取 IconOption 和 IconGroup 以便 Content 使用 (如果它们在 Screen 内部定义，需要移出来或传递)
// 原代码中它们是在 Screen 内部定义的。我应该把它们移到顶层或 companion object，或者作为参数传递。
// 为简单起见，我把它们作为参数传递，或者在 Content 内部重新定义（如果有必要）。
// 但最好是把数据定义移出去。
// 不过为了最小化改动，我还是在 Content 重新定义或者接受 pass-in。
// 原代码 line 54-83 定义了数据。
// 我直接把 LazyVerticalGrid 的内容提取到 Content。

@Composable
fun IconSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    context: android.content.Context,
    iconGroups: List<IconGroup>
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(
            top = 16.dp, // Removed padding.calculateTopPadding() because modifier handles it? 
            // Warning: modifier.padding(padding) applies padding to the container. 
            // LazyVerticalGrid contentPadding joins with that?
            // Usually we want contentPadding to include the system bars if strictly necessary, 
            // but here `padding` passed from Scaffold includes TopBar height.
            // If I apply `modifier.padding(padding)` to `IconSettingsContent`, 
            // then `LazyVerticalGrid` starts BELOW the TopBar.
            // So `contentPadding.top` should just be the extra spacing (16.dp).
            
            bottom = 24.dp, // Similarly for bottom
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize()
    ) {
            // 提示信息
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        CupertinoIcons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "图标切换可能需要几秒钟生效，系统可能会短暂卡顿。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            iconGroups.forEach { group ->
                // 分组标题
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                    )
                }

                items(group.icons) { option ->
                    val isSelected = state.appIcon == option.key
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!isSelected) {
                                    Toast.makeText(context, "正在切换图标...", Toast.LENGTH_SHORT).show()
                                    viewModel.setAppIcon(option.key)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 图标主体
                            // iOS App Icon 形状: 连续曲率圆角 (Squircle)
                            // 这里用 RoundedCornerShape(22%) 模拟
                            AsyncImage(
                                model = option.iconRes,
                                contentDescription = option.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        spotColor = Color.Black.copy(alpha = 0.15f)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                            )
                            
                            // 选中标记 (右下角悬浮)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                            ) {
                                Icon(
                                    CupertinoIcons.Filled.CheckmarkCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

