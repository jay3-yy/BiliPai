// 文件路径: core/ui/LottieComponents.kt
package com.android.purebilibili.core.ui

import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAnimatedAsset
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.assetPath

/**
 *  Lottie 动画加载器
 * 使用在线 Lottie 动画 URL
 */
object LottieUrls {
    //  通用状态动画
    const val SUCCESS = "https://assets4.lottiefiles.com/packages/lf20_jbrw3hcz.json"
    const val ERROR = "https://assets1.lottiefiles.com/packages/lf20_cr9slsdh.json"

    //  新手引导页面动画
    const val THEME_COLORS = "https://assets5.lottiefiles.com/packages/lf20_jtbfg2nb.json"  // 彩虹渐变
    const val VIDEO_PLAY = "https://assets8.lottiefiles.com/packages/lf20_khzniaya.json"  // 播放按钮
}

/**
 *  通用 Lottie 动画组件
 */
@Composable
fun LottieAnimation(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    iterations: Int = LottieConstants.IterateForever,
    autoPlay: Boolean = true
) {
    val animationGate = rememberDecorativeAnimationGate()
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.Url(url)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        isPlaying = autoPlay && animationGate.active,
        restartOnPlay = false,
    )
    
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size).then(animationGate.modifier)
    )
}
/**
 *  加载动画组件（按 UI 预设分发：iOS 吉祥物 / MD3 LoadingIndicator / Miuix 进度环）
 */
@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    text: String? = null
) {
    val uiSkinState = LocalUiSkinState.current
    val skinLoadingPath = uiSkinState.assetPath(UiSkinSurface.LOADING_INDICATOR) {
        it.loadingAnimation ?: it.loadingFrame
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (skinLoadingPath != null) {
            UiSkinAnimatedAsset(
                path = skinLoadingPath,
                size = size,
                contentDescription = "加载中",
            )
        } else {
            AdaptiveLoadingIndicator(
                size = size,
                strokeWidth = 2.4.dp,
            )
        }
        if (text != null) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            AppText(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Theme-aware loading indicator entry used across feature screens.
 *
 * Historically iOS-only cute person; now routes through [AdaptiveLoadingIndicator]
 * so MD3 uses the official morphing [androidx.compose.material3.LoadingIndicator]
 * (dynamic primary) and Miuix uses native progress chrome. iOS keeps the mascot.
 *
 * @param size optional visual size. Prefer this over [Modifier.size] so compact
 *   slots (≤ 32.dp) can select the compact circular recipe on MD3/Miuix.
 */
@Composable
fun CutePersonLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
    size: Dp? = null,
) {
    AdaptiveLoadingIndicator(
        modifier = modifier,
        size = size,
        color = color,
        strokeWidth = strokeWidth,
    )
}

/** Local brand empty feedback. Tapping the character explicitly replays one cycle. */
@Composable
fun EmptyState(
    message: String = "暂无内容",
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    enableEasterEgg: Boolean = true,
    subtitle: String? = null,
    animation: MaidAnimation = MaidAnimation.EMPTY,
    isVisible: Boolean = true
) {
    var replayKey by remember(animation, message) { mutableIntStateOf(0) }
    var easterEggMessage by remember(message) { mutableStateOf<String?>(null) }
    MaidStateViewport(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacingTokens.DoubleExtraLarge)
    ) {
        BlueSnowMaidAnimation(
            animation = animation,
            isVisible = isVisible,
            replayKey = replayKey,
            modifier = Modifier.size(maidStateIllustrationSize()).clickable(
                role = Role.Button,
                onClickLabel = "重播蓝雪女仆动画"
            ) {
                replayKey++
                if (enableEasterEgg && replayKey % 3 == 0) easterEggMessage = "别戳我啦～ 😆"
            }
        )
        Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
        AppText(
            text = easterEggMessage ?: message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
            com.android.purebilibili.core.ui.components.AppTextButton(onClick = onAction) {
                AppText(text = actionText)
            }
        }
    }
}

/**
 *  错误状态组件
 *  支持点击彩蛋：连续点击会显示鼓励消息
 */
@Composable
fun ErrorState(
    message: String = "加载失败",
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    //  [彩蛋] 是否启用点击彩蛋
    enableEasterEgg: Boolean = true,
    isVisible: Boolean = true
) {
    var replayKey by remember(message) { mutableIntStateOf(0) }
    //  点击计数器触发彩蛋
    var clickCount by remember { mutableIntStateOf(0) }
    var showEncouragement by remember { mutableStateOf(false) }
    
    //  鼓励消息列表
    val encouragements = remember {
        listOf(
            "别灰心！再试一次～ 💪",
            "网络可能在打盹... 😴",
            "加载失败也要保持微笑！😊",
            "休息一下再试试？☕",
            "服务器正在努力中... 🏃",
            "别担心，问题不大！👌"
        )
    }
    
    val displayMessage = if (showEncouragement) {
        encouragements.random()
    } else message
    
    MaidStateViewport(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacingTokens.DoubleExtraLarge)
            .then(
                if (enableEasterEgg) {
                    Modifier.clickable {
                        clickCount++
                        if (clickCount >= 3) {
                            showEncouragement = true
                        }
                        if (clickCount >= 5) {
                            clickCount = 0
                            showEncouragement = false
                        }
                    }
                } else Modifier
            )
    ) {
        BlueSnowMaidAnimation(
            animation = MaidAnimation.RETRY,
            isVisible = isVisible,
            replayKey = replayKey,
            modifier = Modifier.size(maidStateIllustrationSize()).clickable(
                role = Role.Button,
                onClickLabel = "重播蓝雪女仆动画"
            ) { replayKey++ }
        )
        Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
        AppText(
            text = displayMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = if (showEncouragement)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
            AppButton(onClick = onRetry) {
                AppText("重试")
            }
        }
    }
}

/**
 *  成功动画
 */
@Composable
fun SuccessAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    onFinished: () -> Unit = {}
) {
    var finished by remember { mutableStateOf(false) }
    
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.Url(LottieUrls.SUCCESS)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )
    
    LaunchedEffect(progress) {
        if (progress == 1f && !finished) {
            finished = true
            onFinished()
        }
    }
    
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size)
    )
}


/** Content states can be prominent; short windows keep room for copy and retry controls. */
@Composable
internal fun maidStateIllustrationSize(): Dp {
    val window = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.windowSizeClass
    return when {
        window.heightDp < 480.dp -> 128.dp
        window.widthDp >= 600.dp -> 220.dp
        else -> 200.dp
    }
}
