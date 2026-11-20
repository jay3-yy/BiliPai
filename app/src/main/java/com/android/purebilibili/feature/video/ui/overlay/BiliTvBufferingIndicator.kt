package com.android.purebilibili.feature.video.ui.overlay

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.asDrawable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppText

/** 视频播放共用的小电视动画；仅改变图案和文案，不叠加背景遮罩。 */
@Composable
internal fun BiliTvAnimatedBufferingIndicator(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val active = com.android.purebilibili.core.ui.rememberVisualWorkActive() && !reduceMotion
    var failed by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || reduceMotion || failed) {
        // 静态回退使用独立首帧，避免 painterResource 将动画 WebP 强转为 BitmapDrawable。
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.bili_tv_buffering_static),
            contentDescription = "加载中",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier.size(size),
        )
        return
    }
    val model = remember(context) {
        coil3.request.ImageRequest.Builder(context)
            .data("android.resource://${context.packageName}/${R.raw.bili_tv_buffering}")
            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
            .build()
    }
    var animation by remember(model) { mutableStateOf<android.graphics.drawable.Animatable?>(null) }
    DisposableEffect(animation, active) {
        val current = animation
        if (active) current?.start() else current?.stop()
        onDispose { current?.stop() }
    }
    AsyncImage(
        model = model,
        contentDescription = "加载中",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
        onSuccess = {
            val drawable = it.result.image.asDrawable(context.resources)
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
            val current = drawable as? android.graphics.drawable.Animatable
            current?.stop()
            animation = current
        },
        onError = { failed = true },
    )
}

/** 播放器起播与再缓冲共用同一套小电视动画和文案。 */
@Composable
internal fun PlayerLoadingIndicator(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        BiliTvAnimatedBufferingIndicator(tint = tint, size = size)
        AppText(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 保持既有调用名，内部与起播指示器使用同一套动画和文案。 */
@Composable
internal fun BiliTvBufferingIndicator(
    tint: Color,
    modifier: Modifier = Modifier,
    bufferedProvider: () -> Long = { 0L },
) {
    PlayerLoadingIndicator(
        label = if (bufferedProvider() > 0L) "正在缓冲" else "正在加载",
        tint = tint,
        modifier = modifier.padding(20.dp),
    )
}

/** 原生播放视图不再自行显示进度环，由同一动画响应真实缓冲状态。 */
@Composable
internal fun PlayerBufferingIndicator(
    player: androidx.media3.common.Player,
    modifier: Modifier = Modifier,
    tint: Color = com.android.purebilibili.core.ui.AppContentColors.accent(),
    size: Dp = 32.dp,
) {
    var buffering by remember(player) {
        mutableStateOf(player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                buffering = player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (buffering) BiliTvAnimatedBufferingIndicator(tint = tint, modifier = modifier, size = size)
}
