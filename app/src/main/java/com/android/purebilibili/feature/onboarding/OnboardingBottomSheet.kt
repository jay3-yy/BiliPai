// 文件路径: feature/onboarding/OnboardingBottomSheet.kt
package com.android.purebilibili.feature.onboarding

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.AppText

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
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.blur.unifiedBlur
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
//  Lottie 动画
import com.airbnb.lottie.compose.*
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.LottieUrls
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
import com.android.purebilibili.core.theme.opaqueCompositeOver
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel

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
    val sheetMotion = rememberAppBottomSheetMotion()

    // 3 页引导
    val pagerState = rememberPagerState(pageCount = { 3 })

    //  弹窗局部 Haze 状态 (用于内部元素)
    val localHazeState = com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()

    //  控制进出场动画
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = sheetMotion.scrimEnter,
        exit = sheetMotion.scrimExit
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
        enter = sheetMotion.contentEnter,
        exit = sheetMotion.contentExit
    ) {
        //  2. 内容层 (点击透传)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetShape = AppShapes.container(ContainerLevel.Sheet)
            val sheetTopColor = AppSurfaceTokens.cardContainer()
            val sheetBottomColor = AppSurfaceTokens.groupedListContainer()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f) //  占 85% 屏幕高度
                    .clip(sheetShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                sheetTopColor,
                                sheetBottomColor,
                            )
                        ),
                        shape = sheetShape
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
                            .clip(AppShapes.container(ContainerLevel.Tag))
                            .background(AppContentColors.primaryText().copy(alpha = 0.2f))
                    )

                    //  多页轮播内容
                    // 注意：不再作为 Haze 源，而是作为 Haze 的 Child 的内容
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalPriorityHorizontalPagerSwipe(pagerState),
                    ) { page ->
                        val pageVisualWorkActive = remember(pagerState, page) {
                            derivedStateOf {
                                pagerState.settledPage == page && !pagerState.isScrollInProgress
                            }
                        }
                        CompositionLocalProvider(
                            com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides (
                                com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current &&
                                    pageVisualWorkActive.value
                            ),
                        ) {
                            when (page) {
                                0 -> WelcomePage(hazeState = localHazeState)
                                1 -> AppearanceSettingsPage(hazeState = localHazeState)
                                2 -> PlaybackSettingsPage(hazeState = localHazeState)
                            }
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
                                    .clip(AppShapes.container(ContainerLevel.Tag))
                                    .background(
                                        if (isSelected) AppContentColors.accent()
                                        else AppContentColors.primaryText().copy(alpha = 0.2f)
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
                            AppOutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = AppShapes.container(ContainerLevel.Card),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AppContentColors.primaryText()
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                AppText(stringResource(R.string.onboarding_skip), fontWeight = FontWeight.Medium)
                            }

                            // 下一步按钮
                            AppButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = AppShapes.container(ContainerLevel.Card),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                    contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                                )
                            ) {
                                AppText(stringResource(R.string.onboarding_next), fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            // 最后一页：开始使用
                            AppButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = AppShapes.container(ContainerLevel.Card),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                    contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                                )
                            ) {
                                AppText(
                                    stringResource(R.string.onboarding_start_exploring),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    //  GitHub 链接
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                    AppText(
                        "github.com/jay3-yy/BiliPai",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent().copy(alpha = 0.8f),
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
        val welcomeGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        val welcomeComposition by rememberLottieComposition(
            LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_touohxv0.json") // 欢迎/庆祝动画
        )
        val welcomeProgress = animateLottieCompositionAsState(
            composition = welcomeComposition,
            iterations = LottieConstants.IterateForever,
            isPlaying = welcomeGate.active,
            restartOnPlay = false,
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .then(welcomeGate.modifier)
                .graphicsLayer {
                    alpha = animatedItems[0].value
                    scaleX = 0.5f + animatedItems[0].value * 0.5f
                    scaleY = 0.5f + animatedItems[0].value * 0.5f
                },
            contentAlignment = Alignment.Center
        ) {
            // 主 Logo
            AsyncImage(
                model = R.mipmap.ic_launcher_3d,
                contentDescription = "BiliPai Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(AppShapes.container(ContainerLevel.Floating))
            )
            //  Lottie 装饰动画 (环绕效果)
            LottieAnimation(
                composition = welcomeComposition,
                progress = { welcomeProgress.value },
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraLarge))

        // 标题 - 动画项 1
        AppText(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = AppContentColors.primaryText(),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

        // 副标题 - 动画项 2
        AppText(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = AppContentColors.secondaryText(),
            letterSpacing = 3.sp,
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.DoubleExtraLarge))

        // 特性标签 - 动画项 3, 4, 5
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = animatedItems[3].value },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureBadge(
                emoji = "🎨",
                label = stringResource(R.string.onboarding_feature_appearance),
                animationProgress = animatedItems[3].value
            )
            FeatureBadge(
                emoji = "⚡",
                label = stringResource(R.string.onboarding_feature_fast_playback),
                animationProgress = animatedItems[4].value
            )
            FeatureBadge(
                emoji = "🔒",
                label = stringResource(R.string.onboarding_feature_privacy),
                animationProgress = animatedItems[5].value
            )
        }

        Spacer(modifier = Modifier.height(AppSpacingTokens.DoubleExtraLarge))

        val disclaimerColors = resolveAccessibleContainerColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            backgroundColor = MaterialTheme.colorScheme.surface,
            fallbackContentColors = listOf(
                AppContentColors.primaryText(),
                MaterialTheme.colorScheme.onBackground,
            ),
        )

        // 免责声明
        AppSurface(
            color = disclaimerColors.containerColor,
            shape = AppShapes.container(ContainerLevel.Card),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[5].value
                translationY = (1f - animatedItems[5].value) * 20f
            }
        ) {
            AppText(
                stringResource(R.string.onboarding_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = disclaimerColors.contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(AppSpacingTokens.Medium)
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
    val appearanceGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val iconScale = if (appearanceGate.active) {
        val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    //  Lottie 外观主题动画 - 彩虹渐变
    val themeComposition by rememberLottieComposition(
        LottieCompositionSpec.Url(LottieUrls.THEME_COLORS)
    )
    val themeProgress = animateLottieCompositionAsState(
        composition = themeComposition,
        iterations = LottieConstants.IterateForever,
        isPlaying = appearanceGate.active,
        restartOnPlay = false,
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
                .then(appearanceGate.modifier)
                .graphicsLayer {
                    val pulse = iconScale.value
                    alpha = animatedItems[0].value
                    scaleX = pulse * (0.5f + animatedItems[0].value * 0.5f)
                    scaleY = pulse * (0.5f + animatedItems[0].value * 0.5f)
                },
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = themeComposition,
                progress = { themeProgress.value },
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraLarge))

        // 标题 - 动画项 1
        AppText(
            stringResource(R.string.onboarding_appearance_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppContentColors.primaryText(),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

        // 副标题 - 动画项 2
        AppText(
            stringResource(R.string.onboarding_appearance_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.DoubleExtraLarge))

        // 功能列表 - 动画项 3, 4, 5, 6
        FeatureListItem(
            icon = "🎨",
            title = stringResource(R.string.onboarding_theme_colors_title),
            description = stringResource(R.string.onboarding_theme_colors_description),
            animationProgress = animatedItems[3].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "🌙",
            title = stringResource(R.string.onboarding_dark_mode_title),
            description = stringResource(R.string.onboarding_dark_mode_description),
            animationProgress = animatedItems[4].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "✨",
            title = stringResource(R.string.onboarding_dynamic_color_title),
            description = stringResource(R.string.onboarding_dynamic_color_description),
            animationProgress = animatedItems[5].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "👁️",
            title = stringResource(R.string.onboarding_eye_comfort_title),
            description = stringResource(R.string.onboarding_eye_comfort_description),
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
    val playbackGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val iconScale = if (playbackGate.active) {
        val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    //  Lottie 播放动画 - 视频播放按钮
    val playComposition by rememberLottieComposition(
        LottieCompositionSpec.Url(LottieUrls.VIDEO_PLAY)
    )
    val playProgress = animateLottieCompositionAsState(
        composition = playComposition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playbackGate.active,
        restartOnPlay = false,
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
                .then(playbackGate.modifier)
                .graphicsLayer {
                    val pulse = iconScale.value
                    alpha = animatedItems[0].value
                    scaleX = pulse * (0.5f + animatedItems[0].value * 0.5f)
                    scaleY = pulse * (0.5f + animatedItems[0].value * 0.5f)
                },
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = playComposition,
                progress = { playProgress.value },
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraLarge))

        // 标题 - 动画项 1
        AppText(
            stringResource(R.string.onboarding_playback_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppContentColors.primaryText(),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[1].value
                translationY = (1f - animatedItems[1].value) * 30f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

        // 副标题 - 动画项 2
        AppText(
            stringResource(R.string.onboarding_playback_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
            modifier = Modifier.graphicsLayer {
                alpha = animatedItems[2].value
                translationY = (1f - animatedItems[2].value) * 20f
            }
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.DoubleExtraLarge))

        // 功能列表 - 动画项 3, 4, 5, 6
        FeatureListItem(
            icon = "📺",
            title = stringResource(R.string.onboarding_adaptive_quality_title),
            description = stringResource(R.string.onboarding_adaptive_quality_description),
            animationProgress = animatedItems[3].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "🖼️",
            title = stringResource(R.string.onboarding_picture_in_picture_title),
            description = stringResource(R.string.onboarding_picture_in_picture_description),
            animationProgress = animatedItems[4].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "👆",
            title = stringResource(R.string.onboarding_gestures_title),
            description = stringResource(R.string.onboarding_gestures_description),
            animationProgress = animatedItems[5].value,
            hazeState = hazeState
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureListItem(
            icon = "👍",
            title = stringResource(R.string.onboarding_double_tap_like_title),
            description = stringResource(R.string.onboarding_double_tap_like_description),
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
    val badgeGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val badgeScale = if (badgeGate.active) {
        val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "badgeScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(badgeGate.modifier)
            .graphicsLayer {
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
                        val scale = badgeScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .blur(12.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppContentColors.accent().copy(alpha = 0.35f),
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
                        val scale = badgeScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        color = opaqueCompositeOver(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surface,
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppText(emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        AppText(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = AppContentColors.primaryText()
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
    AppSurface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = AppShapes.container(ContainerLevel.Card),
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
            modifier = Modifier.padding(AppSpacingTokens.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = AppShapes.container(ContainerLevel.Card)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppText(icon, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))

            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.primaryText()
                )
                AppText(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
        }
    }
}
