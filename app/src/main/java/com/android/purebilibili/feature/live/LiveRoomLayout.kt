package com.android.purebilibili.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo

/** 只包含直播间背景，顶栏玻璃不得采样包含自身的页面录制。 */
internal val LocalLiveRoomHeaderBackdrop = compositionLocalOf<top.yukonga.miuix.kmp.blur.Backdrop?> { null }

/** 直播间的窗口布局。播放器、聊天与发送入口由调用方接入同一份房间状态。 */
@Composable
internal fun LiveRoomLayout(
    mode: LiveRoomLayoutMode,
    isFullscreen: Boolean,
    backgroundUrl: String,
    hasObstructingHinge: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    player: @Composable () -> Unit,
    interaction: @Composable (overVideo: Boolean, includeFooter: Boolean) -> Unit,
    inputFooter: @Composable () -> Unit,
    superChat: (@Composable () -> Unit)? = null,
) {
    val headerBackdrop = if (!isFullscreen && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource(recordingBackground = Color.Black)
    } else null
    val roomHeader: @Composable () -> Unit = {
        CompositionLocalProvider(LocalLiveRoomHeaderBackdrop provides headerBackdrop?.takeIf { it.isReady }?.backdrop) {
            header()
        }
    }
    val adaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val tabletopFullscreen = isFullscreen && adaptiveInfo.posture == AppFoldPosture.Tabletop
    Box(modifier.background(Color.Black)) {
        if (!isFullscreen) LiveRoomBackground(
            backgroundUrl,
            Modifier.fillMaxSize().then(headerBackdrop?.modifier ?: Modifier),
        )
        // 全屏悬停态（Tabletop）由播放器内部将画面收进上半屏、控制条留于下半屏，避免整屏下黑。
        if (hasObstructingHinge && !tabletopFullscreen) {
            AppHingePaneLayout(
                modifier = Modifier.fillMaxSize(),
                primaryContent = {
                    Column(Modifier.fillMaxSize().focusGroup()) {
                        if (!isFullscreen) roomHeader()
                        Box(Modifier.weight(1f).fillMaxWidth()) { player() }
                    }
                },
                secondaryContent = if (!isFullscreen) ({
                    if (superChat == null) {
                        Box(Modifier.fillMaxSize().focusGroup()) { interaction(false, true) }
                    } else {
                        Column(Modifier.fillMaxSize().focusGroup()) {
                            superChat()
                            Box(Modifier.weight(1f).fillMaxWidth()) { interaction(false, true) }
                        }
                    }
                }) else null,
            )
        } else {
            // 播放器固定在同一组合位置；横竖屏和全屏只改变测量及放置，不释放视频输出。
            val split = mode == LiveRoomLayoutMode.LandscapeSplit
            val vertical = mode == LiveRoomLayoutMode.PortraitVerticalOverlay
            val showInteraction = !isFullscreen && mode != LiveRoomLayoutMode.LandscapeOverlay
            val density = LocalDensity.current
            val safeInsets = WindowInsets.safeDrawing
            val splitLeft = if (split) safeInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr) else 0
            val splitRight = if (split) safeInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr) else 0
            val navigationBottom = WindowInsets.navigationBars.getBottom(density)
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box { if (!isFullscreen) roomHeader() }
                    Box(Modifier.clipToBounds().focusGroup()) { player() }
                    Box(Modifier.focusGroup()) { if (showInteraction) interaction(vertical, !vertical) }
                    Box { if (vertical && !isFullscreen) inputFooter() }
                    Box { superChat?.invoke() }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val headerPlaceable = measurables[0].measure(
                    Constraints(maxWidth = width, maxHeight = height),
                )
                val top = headerPlaceable.height
                val availableHeight = (height - top).coerceAtLeast(0)
                val availableWidth = (width - splitLeft - splitRight).coerceAtLeast(0)
                val footerHeight = if (vertical && !isFullscreen) {
                    (LIVE_INPUT_HEIGHT_DP.dp.roundToPx() + navigationBottom).coerceAtMost(availableHeight)
                } else 0
                val chatWidth = if (split) {
                    resolveLiveSplitChatPanelWidthDp(
                        screenWidthDp = (availableWidth / density.density).toInt(),
                        screenHeightDp = (availableHeight / density.density).toInt(),
                    ).dp.roundToPx().coerceIn(0, availableWidth)
                } else width
                val playerWidth = if (split) availableWidth - chatWidth else width
                val playerHeight = when (mode) {
                    LiveRoomLayoutMode.PortraitPanel -> (width * 9 / 16).coerceAtMost(availableHeight)
                    LiveRoomLayoutMode.PortraitVerticalOverlay -> availableHeight - footerHeight
                    LiveRoomLayoutMode.LandscapeSplit -> (availableHeight - navigationBottom).coerceAtLeast(0)
                    LiveRoomLayoutMode.LandscapeOverlay -> availableHeight
                }
                val playerPlaceable = measurables[1].measure(Constraints.fixed(playerWidth, playerHeight))
                val chatHeight = when {
                    !showInteraction -> 0
                    split -> availableHeight
                    vertical -> (availableHeight * 0.32f).toInt()
                    else -> availableHeight - playerHeight
                }
                val chatY = when {
                    split -> top
                    vertical -> (height - footerHeight - LIVE_CHAT_INPUT_GAP_DP.dp.roundToPx() - chatHeight).coerceAtLeast(top)
                    else -> top + playerHeight
                }
                val superChatPlaceable = measurables[4].measure(
                    Constraints(
                        maxWidth = chatWidth,
                        maxHeight = if (vertical) chatY - top else chatHeight,
                    ),
                )
                val chatInset = if (vertical) 0 else superChatPlaceable.height
                val chatPlaceable = measurables[2].measure(Constraints.fixed(chatWidth, chatHeight - chatInset))
                val footerPlaceable = measurables[3].measure(Constraints.fixed(width, footerHeight))
                layout(width, height) {
                    headerPlaceable.place(0, 0)
                    playerPlaceable.place(splitLeft, top)
                    val chatX = if (split) splitLeft + playerWidth else 0
                    val superChatY = if (vertical) chatY - superChatPlaceable.height else chatY
                    superChatPlaceable.place(chatX, superChatY)
                    chatPlaceable.place(chatX, chatY + chatInset)
                    footerPlaceable.place(0, height - footerHeight)
                }
            }
        }
    }
}

@Composable
private fun LiveRoomBackground(imageUrl: String, modifier: Modifier = Modifier) {
    val fallback = painterResource(R.drawable.live_room_background)
    AsyncImage(
        model = if (imageUrl.isBlank()) R.drawable.live_room_background else imageUrl,
        placeholder = fallback,
        error = fallback,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer(alpha = 0.6f),
    )
}
