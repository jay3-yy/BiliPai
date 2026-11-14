package com.android.purebilibili.feature.video.ui.section

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * Capture the player page in paint order, excluding all glass consumers.
 * Each source modifier belongs to exactly one sibling container. Miuix owns
 * coordinates and layer lifetime; detached optional layers draw nothing.
 */
@Stable
internal class PlayerPageBackdrop(
    val video: ChromeBackdropSource,
    val cover: ChromeBackdropSource,
    val danmaku: ChromeBackdropSource,
    val subtitles: ChromeBackdropSource,
    val backdrop: Backdrop,
)

@Composable
internal fun rememberPlayerPageBackdrop(): PlayerPageBackdrop {
    val video = rememberChromeBackdropSource()
    val cover = rememberChromeBackdropSource()
    val danmaku = rememberChromeBackdropSource()
    val subtitles = rememberChromeBackdropSource()
    val videoAndCover = rememberCombinedBackdrop(video.backdrop, cover.backdrop)
    val withDanmaku = rememberCombinedBackdrop(videoAndCover, danmaku.backdrop)
    val page = rememberCombinedBackdrop(withDanmaku, subtitles.backdrop)
    return remember(video, cover, danmaku, subtitles, page) {
        PlayerPageBackdrop(video, cover, danmaku, subtitles, page)
    }
}

/**
 * 大屏竖屏舞台互动 dock 的播放页 backdrop 出口：VideoPlayerSection 在控制条
 * 显隐变化时写入（见 LocalStagePageBackdropOutlet），舞台层据此消费真实折射。
 * [visible] 跟随控制条显隐请求，dock 与控制条同起同落；[backdrop] 是当前
 * 采样窗口内的活体捕获，采样停止后即为 null。
 */
@Stable
internal class PlayerPageBackdropOutlet {
    var backdrop by mutableStateOf<Backdrop?>(null)
    var visible by mutableStateOf(false)
}

internal val LocalStagePageBackdropOutlet = compositionLocalOf<PlayerPageBackdropOutlet?> { null }

internal fun supportsPlayerPageGlass(
    liquidGlassEnabled: Boolean,
    fullscreen: Boolean,
    portraitFullscreen: Boolean,
    hdr: Boolean,
    anime4k: Boolean,
    audioOnly: Boolean,
    pip: Boolean,
    detailInline: Boolean = false,
): Boolean = liquidGlassEnabled && (fullscreen || portraitFullscreen || detailInline) &&
    !hdr && !anime4k && !audioOnly && !pip

internal fun shouldUsePlayerPageTexture(
    glassSupported: Boolean,
    retainedGlassTexture: Boolean,
    hdr: Boolean,
    existingTextureRequest: Boolean,
): Boolean = !hdr && (glassSupported || retainedGlassTexture || existingTextureRequest)

/** 采样只服务活跃页面的可见控制层；视觉许可与后台音频播放许可分别控制。 */
internal fun shouldCapturePlayerPageBackdrop(
    glassSupported: Boolean,
    controlsRequestCapture: Boolean,
    controlsExitVisible: Boolean,
    visualWorkActive: Boolean,
    miniMode: Boolean,
    overlaysSuppressed: Boolean,
): Boolean = glassSupported && visualWorkActive && !miniMode && !overlaysSuppressed &&
    (controlsRequestCapture || controlsExitVisible)
