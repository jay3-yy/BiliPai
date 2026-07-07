// 文件路径: feature/settings/AnimationSettingsScreen.kt
package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeed
import com.android.purebilibili.core.ui.transition.normalizeVideoSharedTransitionCustomDurationMillis
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.animation.rememberEffectiveEntranceMotionSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 *  动画与效果设置二级页面
 * 管理卡片动画、过渡效果、磨砂效果等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screenTitle = stringResource(R.string.animation_effects_title)
    val backLabel = stringResource(R.string.common_back)
    val scope = rememberCoroutineScope()
    val blurLevel = when (state.blurIntensity) {
        BlurIntensity.THIN -> 0.5f
        BlurIntensity.THICK -> 0.8f
        BlurIntensity.APPLE_DOCK -> 1.0f  //  玻璃拟态风格
    }
    val animationInteractionLevel = (
        0.2f +
            if (state.cardAnimationEnabled) 0.25f else 0f +
            if (state.cardTransitionEnabled) 0.25f else 0f +
            if (state.bottomBarBlurEnabled) 0.2f else 0f +
            blurLevel * 0.2f
        ).coerceIn(0f, 1f)

    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = screenTitle,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = globalWallpaperAwareChromeColor(AppSurfaceTokens.groupedListContainer())
                )
            )
        },
        containerColor = globalWallpaperAwareChromeColor(AppSurfaceTokens.groupedListContainer()),
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides state.isLiquidGlassEnabled) {
            AnimationSettingsContent(
                modifier = Modifier.padding(padding),
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun AnimationSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    val windowSizeClass = LocalWindowSizeClass.current
    val warningTint = rememberAdaptiveSemanticIconTint(iOSOrange)
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardMotionTier = resolveAnimationSettingsCardMotionTier(
        baseTier = deviceUiProfile.motionTier,
        cardAnimationEnabled = state.cardAnimationEnabled
    )
    val motionTierLabel = remember(cardMotionTier) {
        when (cardMotionTier) {
            MotionTier.Reduced -> "低动效"
            MotionTier.Normal -> "标准"
            MotionTier.Enhanced -> "增强"
        }
    }
    val motionTierHint = remember(cardMotionTier) {
        when (cardMotionTier) {
            MotionTier.Reduced -> "更短延迟与更弱位移，优先稳定和性能"
            MotionTier.Normal -> "平衡性能与动效，适合大多数设备"
            MotionTier.Enhanced -> "更明显的层级与动势，适合大屏展示"
        }
    }
    val isLiquidGlassAvailable = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val bottomBarLiquidGlassEnabled = state.bottomBarLiquidGlassEnabled
    val appNavigationSettings by SettingsManager.getAppNavigationSettings(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.AppNavigationSettings())
    val uiEntranceAnimationEnabled by SettingsManager.getUiEntranceAnimationEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val predictiveBackStyleOptions = remember { resolvePredictiveBackStyleOptions() }
    val predictiveBackExitDirectionOptions = remember { resolvePredictiveBackExitDirectionOptions() }
    val effectiveEntranceSpec = rememberEffectiveEntranceMotionSpec()
    // 开关开着、但有效参数被降级为不动画 → 系统减弱动效在生效。
    val entranceDowngradedBySystem = uiEntranceAnimationEnabled && !effectiveEntranceSpec.animate
    val sharedTransitionSpeedOptions = remember {
        listOf(
            PlaybackSegmentOption(VideoSharedTransitionSpeed.FAST, "快速"),
            PlaybackSegmentOption(VideoSharedTransitionSpeed.STANDARD, "标准"),
            PlaybackSegmentOption(VideoSharedTransitionSpeed.SLOW, "慢速"),
            PlaybackSegmentOption(VideoSharedTransitionSpeed.CUSTOM, "自定")
        )
    }
    var customTransitionDurationMillis by remember(state.videoSharedTransitionCustomDurationMillis) {
        mutableStateOf(state.videoSharedTransitionCustomDurationMillis)
    }
    fun snapCustomTransitionDuration(value: Float): Int {
        val stepMillis = 20
        val min = VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS
        val snapped = min + (((value - min) / stepMillis).roundToInt() * stepMillis)
        return normalizeVideoSharedTransitionCustomDurationMillis(snapped)
    }
    LaunchedEffect(focusRequest?.token) {
        val request = focusRequest ?: return@LaunchedEffect
        if (request.target != SettingsSearchTarget.ANIMATION) return@LaunchedEffect
        val index = resolveAnimationSettingsScrollIndex(request.focusId) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        SettingsSearchFocusController.clear(request.token)
    }

    EntranceGroup {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {

            //  界面动效（全 App 入场）
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("界面动效")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.CARD_ENTRANCE_ANIMATION),
                            title = "界面入场动画",
                            subtitle = "进入页面时内容逐条淡入浮现",
                            checked = uiEntranceAnimationEnabled,
                            onCheckedChange = { value ->
                                scope.launch {
                                    SettingsManager.setUiEntranceAnimationEnabled(context, value)
                                }
                            },
                            iconTint = iOSGreen
                        )
                        if (entranceDowngradedBySystem) {
                            IOSDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "系统已开启「减弱动效」，入场动画已自动关闭。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            //  卡片动画
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("卡片动画")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.CARD_ENTRANCE_ANIMATION),
                            title = "进场动画",
                            subtitle = "首页视频卡片的入场动画效果",
                            checked = state.cardAnimationEnabled,
                            onCheckedChange = { viewModel.toggleCardAnimation(it) },
                            iconTint = iOSPink
                        )
                        IOSDivider()
	                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.CARD_TRANSITION_ANIMATION),
                            title = "过渡动画",
                            subtitle = "全局视频卡片与详情页的共享元素过渡效果",
                            checked = state.cardTransitionEnabled,
                            onCheckedChange = { viewModel.toggleCardTransition(it) },
                            iconTint = iOSTeal
                        )
                        IOSDivider()
                        IOSSlidingSegmentedSetting(
                            title = "共享元素速度：${state.videoSharedTransitionSpeed.label}",
                            subtitle = "先快后慢的统一曲线；自定义只调整时长",
                            options = sharedTransitionSpeedOptions,
                            selectedValue = state.videoSharedTransitionSpeed,
                            onSelectionChange = viewModel::setVideoSharedTransitionSpeed
                        )
                        if (state.videoSharedTransitionSpeed == VideoSharedTransitionSpeed.CUSTOM) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "自定义时长",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${customTransitionDurationMillis}ms",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Slider(
                                    value = customTransitionDurationMillis.toFloat(),
                                    onValueChange = { value ->
                                        customTransitionDurationMillis = snapCustomTransitionDuration(value)
                                    },
                                    onValueChangeFinished = {
                                        viewModel.setVideoSharedTransitionCustomDurationMillis(
                                            customTransitionDurationMillis
                                        )
                                    },
                                    valueRange = VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS.toFloat()..
                                        VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS.toFloat(),
                                    steps = 30
                                )
                            }
                        }
                        IOSDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "首页卡片动画档位",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = motionTierLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = motionTierHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "设置页使用独立轻量入场动效，不跟随此开关关闭。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("返回手势")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PREDICTIVE_BACK),
                            title = "预测性返回",
                            subtitle = "边缘滑动时预览返回动画（Android 13+）",
                            checked = appNavigationSettings.predictiveBackEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsManager.setPredictiveBackEnabled(context, enabled)
                                }
                            },
                            iconTint = iOSBlue
                        )
                        if (appNavigationSettings.predictiveBackEnabled) {
                            IOSDivider()
                            IOSSlidingSegmentedSetting(
                                title = "返回动画：${resolvePredictiveBackStyleLabel(appNavigationSettings.predictiveBackAnimationStyle)}",
                                subtitle = "卡片缩放适合共享元素返场；系统跨页更接近原生体验",
                                options = predictiveBackStyleOptions,
                                selectedValue = appNavigationSettings.predictiveBackAnimationStyle,
                                onSelectionChange = { style ->
                                    scope.launch {
                                        SettingsManager.setPredictiveBackAnimationStyle(context, style)
                                    }
                                }
                            )
                            IOSDivider()
                            IOSSlidingSegmentedSetting(
                                title = "退出方向：${resolvePredictiveBackExitDirectionLabel(appNavigationSettings.predictiveBackExitDirection)}",
                                subtitle = "跟随卡片时按来源位置决定滑出方向；共享元素返场始终跟随手势",
                                options = predictiveBackExitDirectionOptions,
                                selectedValue = appNavigationSettings.predictiveBackExitDirection,
                                onSelectionChange = { direction ->
                                    scope.launch {
                                        SettingsManager.setPredictiveBackExitDirection(context, direction)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // ✨ 视觉效果
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("玻璃效果")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
                        if (isLiquidGlassAvailable) {
                            IOSSwitchItem(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.TOP_DOCK_GLASS),
                                title = "顶部 Dock 液态玻璃",
                                subtitle = "首页顶部 dock 栏的独立液态玻璃效果",
                                checked = state.topBarLiquidGlassEnabled,
                                onCheckedChange = { viewModel.toggleTopBarLiquidGlass(it) },
                                iconTint = iOSBlue
                            )
                            IOSDivider()
                            IOSSwitchItem(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_SEARCH_GLASS),
                                title = "首页搜索框液态玻璃",
                                subtitle = "首页搜索框上下滑动时的液态玻璃折射效果",
                                checked = state.homeSearchLiquidGlassEnabled,
                                onCheckedChange = { viewModel.toggleHomeSearchLiquidGlass(it) },
                                iconTint = iOSBlue
                            )
                            IOSDivider()
                            IOSSwitchItem(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.BOTTOM_BAR_GLASS),
                                title = "底栏液态玻璃",
                                subtitle = "底部导航栏的液态玻璃折射效果",
                                checked = bottomBarLiquidGlassEnabled,
                                onCheckedChange = { viewModel.toggleBottomBarLiquidGlass(it) },
                                iconTint = iOSBlue
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = bottomBarLiquidGlassEnabled,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column {
                                    IOSDivider()
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "当前使用固定材质策略",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "底栏使用独立液态玻璃材质配方；顶部栏保留毛玻璃模糊。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            IOSDivider()
                        }
                        // 磨砂效果 (始终显示)
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.TOP_BAR_BLUR),
                            title = "顶部栏磨砂",
                            subtitle = "顶部导航栏的毛玻璃模糊效果",
                            checked = state.headerBlurEnabled,
                            onCheckedChange = { viewModel.toggleHeaderBlur(it) },
                            iconTint = iOSBlue
                        )
                        IOSDivider()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.BOTTOM_BAR_BLUR),
                            title = "底栏磨砂",
                            subtitle = "底部导航栏的毛玻璃模糊效果",
                            checked = state.bottomBarBlurEnabled,
                            onCheckedChange = { viewModel.toggleBottomBarBlur(it) },
                            iconTint = iOSBlue
                        )
                        
                        // 模糊强度（仅在任意模糊开启时显示）
                        if (state.headerBlurEnabled || state.bottomBarBlurEnabled) {
                            IOSDivider()
                            BlurIntensitySelector(
                                selectedIntensity = state.blurIntensity,
                                onIntensityChange = { viewModel.setBlurIntensity(it) }
                            )
                        }
                    }
                }
            }
            
            // 📐 底栏样式
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("底栏入口")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.FLOATING_BOTTOM_BAR),
                            title = "悬浮底栏",
                            subtitle = "关闭后底栏将沉浸式贴底显示",
                            checked = state.isBottomBarFloating,
                            onCheckedChange = { viewModel.toggleBottomBarFloating(it) },
                            iconTint = iOSPurple
                        )
                    }
                }
            }
            
            //  提示
            item {
                Box(modifier = Modifier.entrance()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                CupertinoIcons.Default.Lightbulb,
                                contentDescription = null,
                                tint = warningTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "关闭动画可以减少电量消耗，提升流畅度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
    }
