package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.AppIcons
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAnimatedAsset
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.assetPath
import kotlinx.coroutines.delay

@Composable
fun LikeBurstAnimation(
    visible: Boolean,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 144.dp,
    onAnimationEnd: () -> Unit = {}
) {
    if (!visible) return

    // 点赞反馈入口先服从女仆开关，皮肤提供的替代特效也不能绕过。
    // 关闭时立即消费本次请求，不加载素材，也不保留等待补播的事件。
    if (!com.android.purebilibili.core.ui.LocalMaidLottieAnimationEnabled.current) {
        val finish by rememberUpdatedState(onAnimationEnd)
        LaunchedEffect(Unit) { finish() }
        return
    }

    val uiSkinState = LocalUiSkinState.current
    val skinLikeEffectPath = uiSkinState.assetPath(UiSkinSurface.LIKE_EFFECT) {
        it.likeEffectAnimation ?: it.likeEffectPreview
    }
    if (skinLikeEffectPath != null) {
        LaunchedEffect(skinLikeEffectPath, visible) {
            delay(if (reducedMotion) 300 else 800)
            onAnimationEnd()
        }
        UiSkinAnimatedAsset(
            path = skinLikeEffectPath,
            modifier = modifier,
            size = if (reducedMotion) 72.dp else 88.dp,
            iterations = 1,
            contentDescription = null,
        )
        return
    }

    com.android.purebilibili.core.ui.BlueSnowMaidAnimation(
        animation = com.android.purebilibili.core.ui.MaidAnimation.LIKE_SUCCESS,
        modifier = modifier.size(size),
        reducedMotion = reducedMotion,
        staticDisplayDurationMs = 1_000L,
        completionHoldDurationMs = 500L,
        onFinished = onAnimationEnd
    )
}

@Composable
fun TripleSuccessAnimation(
    visible: Boolean,
    isCompact: Boolean = false,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = if (isCompact) 200.dp else 240.dp,
    onAnimationEnd: () -> Unit = {}
) {
    if (!visible) return

    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.android.purebilibili.core.ui.BlueSnowMaidAnimation(
            animation = com.android.purebilibili.core.ui.MaidAnimation.TRIPLE_SUCCESS,
            modifier = Modifier.size(size),
            reducedMotion = reducedMotion,
            staticDisplayDurationMs = 1_000L,
            completionHoldDurationMs = 600L,
            onFinished = onAnimationEnd
        )
        AppText(
            text = "三连完成！",
            color = AppContentColors.accent(),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CoinSuccessAnimation(
    visible: Boolean,
    coinCount: Int = 1,
    onAnimationEnd: () -> Unit = {}
) {
    if (!visible) return

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = AppMotionEasing.M1Standard)
            )
            delay(100)
            onAnimationEnd()
        }
    }

    val progress = animatedProgress.value
    val scale by animateFloatAsState(
        targetValue = if (progress < 0.5f) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "coinScale"
    )

    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            imageVector = AppIcons.BiliCoin,
            contentDescription = null,
            tint = AppContentColors.accent().copy(alpha = 1f - progress * 0.12f),
            modifier = Modifier
                .size(if (coinCount >= 2) 32.dp else 28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -progress * 12f
                }
        )
    }
}
