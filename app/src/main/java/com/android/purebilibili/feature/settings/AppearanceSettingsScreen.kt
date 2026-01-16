// 文件路径: feature/settings/AppearanceSettingsScreen.kt
package com.android.purebilibili.feature.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.blur.BlurIntensity
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*

/**
 *  外观设置二级页面
 * iOS 风格设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit = {},  //  底栏设置导航

    onNavigateToIconSettings: () -> Unit = {},  //  [新增] 图标设置导航
    onNavigateToAnimationSettings: () -> Unit = {}  //  [新增] 动画设置导航
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    

    val displayLevel = when (state.displayMode) {
        0 -> 0.35f
        1 -> 0.6f
        else -> 0.85f
    }
    val appearanceInteractionLevel = (
        displayLevel +
            if (state.headerBlurEnabled) 0.1f else 0f +
            if (state.isBottomBarFloating) 0.1f else 0f
        ).coerceIn(0f, 1f)
    val appearanceAnimationSpeed = if (state.dynamicColor) 1.1f else 1f
    
    //  [修复] 设置导航栏透明，确保底部手势栏沉浸式效果
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val originalNavBarColor = window?.navigationBarColor ?: android.graphics.Color.TRANSPARENT
        
        if (window != null) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        onDispose {
            if (window != null) {
                window.navigationBarColor = originalNavBarColor
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        //  [修复] 禁用 Scaffold 默认的 WindowInsets 消耗，避免底部填充
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        AppearanceSettingsContent(
            modifier = Modifier.padding(padding),
            state = state,
            onNavigateToIconSettings = onNavigateToIconSettings,
            onNavigateToAnimationSettings = onNavigateToAnimationSettings,
            viewModel = viewModel,
            context = context
        )
    }
}

@Composable
fun AppearanceSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToIconSettings: () -> Unit,
    onNavigateToAnimationSettings: () -> Unit,
    viewModel: SettingsViewModel,
    context: android.content.Context
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        //  [修复] 添加底部导航栏内边距，确保沉浸式效果
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        
        //  主题与颜色
        item { IOSSectionTitle("主题与颜色") }
        item {
            IOSGroup {
                // 主题模式选择 (横向卡片)
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = state.themeMode == mode
                            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color)
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 动态取色开关
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                         IOSSwitchItem(
                            icon = CupertinoIcons.Default.PaintbrushPointed,
                            title = "Material You",
                            subtitle = "跟随系统壁纸变换应用主题色",
                            checked = state.dynamicColor,
                            onCheckedChange = { viewModel.toggleDynamicColor(it) },
                            iconTint = iOSPink
                        )
                    }

                    // 主题色选择 (仅当动态取色关闭时显示)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !state.dynamicColor,
                        enter =   androidx.compose.animation.expandVertically() +   androidx.compose.animation.fadeIn(),
                        exit =   androidx.compose.animation.shrinkVertically() +   androidx.compose.animation.fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                "主题色", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ThemeColors.size) { index ->
                                    val color = ThemeColors[index]
                                    val isSelected = state.themeColorIndex == index
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { viewModel.setThemeColorIndex(index) }
                                            .then(
                                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) 
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                CupertinoIcons.Default.Checkmark,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
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
        
        //  个性化
        item { IOSSectionTitle("个性化") }
        item {
            IOSGroup {
                // 图标设置
                IOSClickableItem(
                    icon = CupertinoIcons.Default.SquareStack3dUp,
                    title = "应用图标",
                    value = when(state.appIcon) {
                        // 🎀 二次元少女系列
                        "Yuki" -> "比心少女"
                        "Anime", "icon_anime" -> "蓝发电视"
                        "Tv" -> "双马尾"
                        "Headphone" -> "耳机少女"
                        // 经典系列
                        "3D", "icon_3d" -> "3D立体"
                        "Blue", "icon_blue" -> "经典蓝"
                        "Retro", "icon_retro" -> "复古怀旧"
                        "Flat", "icon_flat" -> "扁平现代"
                        "Flat Material" -> "扁平材质"
                        "Neon", "icon_neon" -> "霓虹"
                        "Telegram Blue", "icon_telegram_blue" -> "纸飞机蓝"
                        "Pink", "icon_telegram_pink" -> "樱花粉"
                        "Purple", "icon_telegram_purple" -> "香芋紫"
                        "Green", "icon_telegram_green" -> "薄荷绿"
                        "Dark", "icon_telegram_dark" -> "暗夜蓝"
                        else -> "3D立体"  // 默认显示 3D立体 (对应默认 icon_3d)
                    },
                    onClick = onNavigateToIconSettings,
                    iconTint = iOSPurple
                )
                Divider()
                // 动画设置
                IOSClickableItem(
                    icon = CupertinoIcons.Default.WandAndStars,
                    title = "动画与效果",
                    value = if (state.cardAnimationEnabled) "已开启" else "已关闭",
                    onClick = onNavigateToAnimationSettings,
                    iconTint = iOSPink
                )
            }
        }
            
            //  首页展示 - 抽屉式选择
            item { IOSSectionTitle("首页展示") }
            item {
                IOSGroup {
                    val displayMode = state.displayMode
                    var isExpanded by remember { mutableStateOf(false) }
                    
                    // 当前选中模式的名称
                    val currentModeName = DisplayMode.entries.find { it.value == displayMode }?.title ?: "双列网格"
                    
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题行 - 可点击展开/收起
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                CupertinoIcons.Default.SquareOnSquare,
                                contentDescription = null,
                                tint = iOSBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "展示样式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentModeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // 展开后的选项 - 带动画
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DisplayMode.entries.forEach { mode ->
                                    val isSelected = displayMode == mode.value
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable {
                                                viewModel.setDisplayMode(mode.value)
                                                isExpanded = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                mode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                mode.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                CupertinoIcons.Default.Checkmark,
                                                contentDescription = "已选择",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { IOSSectionTitle("界面自定义") }
            item {
                IOSGroup {
                    // 字体大小 (0.8x - 1.4x)
                    SliderSettingItem(
                        title = "字体大小",
                        value = state.fontScale,
                        range = 0.8f..1.4f,
                        onValueChange = { viewModel.setFontScale(it) },
                        steps = 11, // 0.05 per step
                        icon = CupertinoIcons.Default.Character
                    )

                    Divider()

                    // UI 缩放 (0.9x - 1.2x)
                    SliderSettingItem(
                        title = "UI 缩放",
                        value = state.uiScale,
                        range = 0.9f..1.2f,
                        onValueChange = { viewModel.setUIScale(it) },
                        steps = 5, // 0.05 per step
                        icon = CupertinoIcons.Default.Gear
                    )
                    
                    Divider()
                    
                    // 实时预览卡片
                    Box(modifier = Modifier.padding(16.dp)) {
                        UICustomizationPreviewCard(
                            fontScale = state.fontScale,
                            uiScale = state.uiScale
                        )
                    }
                }
            }
        }
    }




/**
 *  动态取色预览组件
 * 显示从壁纸提取的 Material You 颜色
 */


@Composable
fun DynamicColorPreview() {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "当前取色预览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary
            ColorPreviewItem(
                color = colorScheme.primary,
                label = "主色",
                modifier = Modifier.weight(1f)
            )
            // Secondary
            ColorPreviewItem(
                color = colorScheme.secondary,
                label = "辅色",
                modifier = Modifier.weight(1f)
            )
            // Tertiary
            ColorPreviewItem(
                color = colorScheme.tertiary,
                label = "第三色",
                modifier = Modifier.weight(1f)
            )
            // Primary Container
            ColorPreviewItem(
                color = colorScheme.primaryContainer,
                label = "容器",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ColorPreviewItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SliderSettingItem(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = String.format("%.2fx", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 *  UI 自定义预览卡片 - 简化版（固定圆角）
 */
@Composable
fun UICustomizationPreviewCard(
    fontScale: Float,
    uiScale: Float
) {
    val cornerRadius = 12.dp * uiScale  // 固定圆角，仅受 UI 缩放影响
    val padding = 16.dp * uiScale
    
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = iOSTeal.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "预览效果",
                style = MaterialTheme.typography.titleMedium,
                fontSize = MaterialTheme.typography.titleMedium.fontSize * fontScale * uiScale,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp * uiScale))
            Text(
                text = "调整滑块查看实时变化。",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale * uiScale
            )
            Spacer(modifier = Modifier.height(12.dp * uiScale))
            Button(
                onClick = {},
                shape = RoundedCornerShape(8.dp * uiScale),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "确认",
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * fontScale * uiScale
                )
            }
        }
    }
}
