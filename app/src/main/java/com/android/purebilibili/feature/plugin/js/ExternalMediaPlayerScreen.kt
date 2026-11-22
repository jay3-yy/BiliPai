@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.plugin.js

import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import com.android.purebilibili.core.ui.AppContentColors

import android.view.ViewGroup
import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.plugin.js.BiliPaiJsPluginInstallStore
import com.android.purebilibili.core.plugin.js.BiliPaiJsRuntime
import com.android.purebilibili.core.plugin.js.ExternalMediaLaunchStore
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.layoutHinges
import com.android.purebilibili.danmaku.engine.DanmakuItem
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.danmaku.engine.DanmakuRenderConfig
import com.android.purebilibili.danmaku.engine.DanmakuWindow
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuStrokeWidthPx
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuTextSizePx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalMediaPlayerScreen(
    launchId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appWindowAdaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val request = remember(launchId) { ExternalMediaLaunchStore.get(launchId) }
    var selectedIndex by remember(request) { mutableIntStateOf(request?.selectedStreamIndex ?: 0) }
    val stream = request?.streams?.getOrNull(selectedIndex)
    val dataSourceFactory = remember(stream?.headers) {
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(stream?.headers.orEmpty())
        )
    }
    val player = remember(context, dataSourceFactory) {
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                androidx.media3.exoplayer.DefaultRenderersFactory(context).withAppVideoDecoderPreference(context)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().withAppVideoDecoderDiagnostics(context)
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    val jsStore = remember(context) { BiliPaiJsPluginInstallStore.createDefault(context) }
    val jsRuntime = remember(context) { BiliPaiJsRuntime(context) }
    val danmakuPlugin = remember(request?.danmakuPluginId) {
        request?.danmakuPluginId?.let { pluginId ->
            jsStore.listInstalledPlugins().firstOrNull {
                it.manifest.id == pluginId && it.enabled && it.manifest.supportsDanmaku
            }
        }
    }
    var danmakuEnabled by remember(request?.launchId) { mutableStateOf(true) }
    var danmakuItems by remember(request?.launchId) { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    val danmakuRenderView = remember(context) { DanmakuRenderView(context) }
    val systemDensity = context.resources.displayMetrics.density
    val danmakuRenderConfig = remember(systemDensity) {
        // The top bar and stream selector remain visible; this surface is not fullscreen.
        val textSizePx = resolveDanmakuTextSizePx(systemDensity, fontScale = 1f, isFullscreen = false)
        DanmakuRenderConfig(
            textSizePx = textSizePx,
            strokeWidthPx = resolveDanmakuStrokeWidthPx(systemDensity, strokeWidth = 1.5f),
            lineHeightPx = textSizePx * (64f / 48f)
        )
    }

    DisposableEffect(danmakuRenderView) {
        onDispose {
            danmakuRenderView.releaseRenderer()
        }
    }

    LaunchedEffect(danmakuPlugin, request?.title) {
        val plugin = danmakuPlugin ?: return@LaunchedEffect
        val title = request?.title.orEmpty()
        if (title.isBlank()) return@LaunchedEffect
        jsRuntime.loadDanmuComments(installed = plugin, title = title).onSuccess { comments ->
            danmakuItems = mapJsDanmuCommentsToItems(comments)
        }.onFailure { error ->
            danmakuItems = emptyList()
        }
    }

    LaunchedEffect(danmakuItems, danmakuEnabled, stream?.url) {
        val engine = danmakuRenderView.engine
        if (danmakuEnabled && danmakuItems.isNotEmpty()) {
            engine.replaceWindow(
                DanmakuWindow(anchorSegment = 0, segmentIndices = listOf(0), items = danmakuItems),
                player.currentPosition
            )
            if (player.isPlaying) {
                engine.start(player.currentPosition)
            } else {
                engine.pause()
            }
        } else {
            engine.clear()
        }
    }

    DisposableEffect(player, danmakuEnabled, danmakuItems.isNotEmpty()) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!danmakuEnabled || danmakuItems.isEmpty()) return
                if (isPlaying) {
                    danmakuRenderView.engine.start(player.currentPosition)
                } else {
                    danmakuRenderView.engine.pause()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!danmakuEnabled || danmakuItems.isEmpty()) return
                danmakuRenderView.engine.seekTo(newPosition.positionMs)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(stream?.url) {
        val current = stream ?: return@LaunchedEffect
        val mediaItem = MediaItem.Builder()
            .setUri(current.url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(request?.title.orEmpty())
                    .build()
            )
            .setMimeType(resolveExternalMediaMimeType(current.url, current.contentType))
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = request?.title ?: "外部媒体",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (request == null || request.streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = "播放请求已失效，请从插件内容重新打开",
                    color = AppContentColors.secondaryText()
                )
            }
            return@AppScaffold
        }

        // 半开折叠姿态：媒体与线路控件整体收进首个安全区，不跨物理铰链。
        val externalMediaSurface: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.player = player
                    }
                )
                if (danmakuEnabled && danmakuItems.isNotEmpty()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize(),
                        factory = {
                            danmakuRenderView.apply {
                                setRendererTouchable(false)
                                engine.updateConfig(danmakuRenderConfig)
                            }
                        },
                        update = { view ->
                            view.engine.updateConfig(danmakuRenderConfig)
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 线路可能很多：限定在可横向滚动的有界区域，避免撑破矮窗口或安全分区。
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (danmakuPlugin != null) {
                    AppFilterChip(
                        selected = danmakuEnabled,
                        onClick = { danmakuEnabled = !danmakuEnabled },
                        label = { AppText("弹幕") }
                    )
                }
                request.streams.forEachIndexed { index, mediaStream ->
                    AppFilterChip(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        label = { AppText(mediaStream.title.ifBlank { "线路 ${index + 1}" }) }
                    )
                }
            }
        }
        }
        // 无二级内容的播放器仅避让物理遮挡铰链；软折痕跨整窗，避免半开下半屏留黑。
        if (appWindowAdaptiveInfo.foldingFeature.layoutHinges().any { it.isOccluding }) {
            com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
                modifier = Modifier.fillMaxSize(),
                primaryContent = externalMediaSurface,
            )
        } else {
            externalMediaSurface()
        }
    }
}

private fun resolveExternalMediaMimeType(
    url: String,
    contentType: String?
): String? {
    val declared = contentType?.lowercase()?.takeIf { it.isNotBlank() }
    return when {
        declared?.contains("mpegurl") == true || declared?.contains("hls") == true -> MimeTypes.APPLICATION_M3U8
        url.substringBefore("?").endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
