package com.android.purebilibili.feature.live.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.feature.live.encodeLiveDanmakuInfo
import com.android.purebilibili.feature.live.resolveLiveMessageMedal
import com.android.purebilibili.core.ui.adaptive.onSecondaryClick
import androidx.compose.foundation.text.appendInlineContent
import com.android.purebilibili.data.model.response.splitLiveMessageEmotes
import com.android.purebilibili.data.model.response.LiveMessageEmote
import androidx.compose.ui.platform.LocalDensity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.LiveChatMessage
import com.android.purebilibili.feature.live.rememberLiveChromePalette
import com.android.purebilibili.feature.live.resolveLiveBiliPaiChatBubbleTokens
import com.android.purebilibili.feature.live.resolveLiveBiliPaiRoomColorTokens
import com.android.purebilibili.feature.live.resolveLiveChatInputVisualSpec
import com.android.purebilibili.feature.live.shouldRenderLiveDanmakuImageEmoticon
import com.android.purebilibili.feature.live.LIVE_INPUT_HEIGHT_DP
import com.android.purebilibili.feature.live.LiveStatusPalette
import com.android.purebilibili.feature.live.resolveLiveLevelColor
import com.android.purebilibili.feature.live.resolveLiveSuperChatColor
import com.android.purebilibili.feature.live.shouldRenderLiveDanmaku
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Send
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppFilledIconButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import com.android.purebilibili.core.ui.components.AppSurface

/**
 * 直播聊天区域组件
 * 包含：
 * 1. 聊天列表 (LazyColumn)
 * 2. 底部输入栏
 */
@Composable
fun LiveChatSection(
    messages: List<LiveChatMessage>,
    canInteract: Boolean = true,
    anchorUid: Long = 0L,
    isOverlay: Boolean = false,
    onUserClick: (Long) -> Unit = {},
    onAtUser: (LiveDanmakuItem) -> Unit = {},
    onBlockUser: (LiveDanmakuItem) -> Unit = {},
    onReportDanmaku: (LiveDanmakuItem) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    compact: Boolean = false,
    followingLatest: Boolean = true,
    onFollowingLatestChange: (Boolean) -> Unit = {},
    active: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = rememberLiveChromePalette()
    val darkOverlay = isOverlay && palette.isDark
    val latestMessages by rememberUpdatedState(messages)
    var openMenuSequence by remember { mutableStateOf<Long?>(null) }
    val isAnyMenuOpen = openMenuSequence != null
    val latestFollowing by rememberUpdatedState(followingLatest)
    val isAwayFromBottom by remember {
        derivedStateOf { !latestFollowing && latestMessages.isNotEmpty() }
    }
    LiveChatAutoFollow(
        listState = listState,
        lastSequence = messages.lastOrNull()?.sequence,
        messageCount = messages.size,
        followingLatest = followingLatest,
        paused = isAnyMenuOpen,
        active = active && androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused,
        onFollowingLatestChange = onFollowingLatestChange,
    )
    LaunchedEffect(messages.lastOrNull()?.sequence) {
        if (openMenuSequence != null && messages.none { it.sequence == openMenuSequence }) openMenuSequence = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 1. 聊天列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = if (compact) 4.dp else 12.dp,
                    vertical = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(
                    space = if (compact) 3.dp else 8.dp,
                    alignment = Alignment.Top
                )
            ) {
                items(messages, key = { it.sequence }, contentType = { "live_chat" }) { entry ->
                    ChatMessageItem(
                        item = entry.item,
                        compact = compact,
                        canInteract = canInteract,
                        isAnchor = anchorUid > 0L && entry.item.uid == anchorUid,
                        isOverlay = isOverlay,
                        onUserClick = onUserClick,
                        onAtUser = onAtUser,
                        onBlockUser = onBlockUser,
                        onReportDanmaku = onReportDanmaku,
                        onMenuVisibilityChange = { shown ->
                            if (shown) openMenuSequence = entry.sequence
                            else if (openMenuSequence == entry.sequence) openMenuSequence = null
                        }
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isAwayFromBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = AppSpacingTokens.Medium,
                        bottom = AppSpacingTokens.Small
                    )
            ) {
                AppSurface(
                    onClick = { onFollowingLatestChange(true) },
                    shape = AppShapes.container(ContainerLevel.Pill),
                    color = if (darkOverlay) palette.bubbleStrong else palette.surfaceMuted,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = AppSpacingTokens.Medium,
                            vertical = AppSpacingTokens.Small
                        )
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (darkOverlay) LiveStatusPalette.MediaContent else palette.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = "回到底部",
                            color = if (darkOverlay) LiveStatusPalette.MediaContent else palette.primaryText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

    }
}


@Composable
private fun ChatMessageItem(
    item: LiveDanmakuItem,
    compact: Boolean = false,
    canInteract: Boolean,
    isAnchor: Boolean,
    isOverlay: Boolean,
    onUserClick: (Long) -> Unit,
    onAtUser: (LiveDanmakuItem) -> Unit,
    onBlockUser: (LiveDanmakuItem) -> Unit,
    onReportDanmaku: (LiveDanmakuItem) -> Unit,
    onMenuVisibilityChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    if (item.isSuperChat) {
        LiveSuperChatCard(
            item = item, persistent = true, onExpired = {},
            onMenuVisibilityChange = onMenuVisibilityChange,
            onUserClick = { onUserClick(item.uid) },
            onReport = {
                if (canInteract) onReportDanmaku(item)
                else Toast.makeText(context, "账号未登录", Toast.LENGTH_SHORT).show()
            },
        )
        return
    }
    if (!shouldRenderLiveDanmaku(item.text, item.emoticonUrl)) {
        return
    }
    val palette = rememberLiveChromePalette()
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(showMenu) {
        onMenuVisibilityChange(showMenu)
    }
    val tokens = resolveLiveBiliPaiChatBubbleTokens(isOverlay = isOverlay, isDark = palette.isDark)
    val bubbleShape = RoundedCornerShape(tokens.cornerRadiusDp.dp)
    val colorScheme = MaterialTheme.colorScheme
    val bubbleBackground = when {
        isOverlay -> LiveStatusPalette.MediaScrim.copy(alpha = tokens.backgroundAlpha)
        else -> Color.White.copy(alpha = 0.08f)
    }

    // Match video/dynamic comments: semantic theme colors keep contrast consistent in both
    // light and dark themes instead of relying on a separate hardcoded live palette.
    val readableText = Color.White
    val readableSecondaryText = Color.White.copy(alpha = 0.6f)
    val usernameColor = if (item.isAdmin) {
        if (isOverlay) LiveStatusPalette.AdminName else colorScheme.tertiary
    } else if (item.isSelf) {
        if (isOverlay) colorScheme.primaryFixed else colorScheme.primary
    } else if (isOverlay) {
        LiveStatusPalette.MediaContent.copy(alpha = tokens.nameAlpha)
    } else {
        readableText.copy(alpha = tokens.nameAlpha)
    }
    val bodyColor = if (isOverlay) LiveStatusPalette.MediaContent else readableText
    val latestOnUserClick by rememberUpdatedState(onUserClick)
    val emoticonMap by DanmakuEmoticonMapper.emoticonMap.collectAsStateWithLifecycle()
    val replyColor = if (isOverlay) LiveStatusPalette.Reply else readableSecondaryText

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleWidthFraction = 1f
        val bubbleModifier = Modifier
            .widthIn(max = maxWidth * bubbleWidthFraction)
            .clip(bubbleShape)
            .onSecondaryClick(if (item.uid > 0L) { _: Offset -> showMenu = true } else null)
            .background(bubbleBackground)
            .padding(horizontal = if (compact) 6.dp else tokens.horizontalPaddingDp.dp, vertical = if (compact) 2.dp else tokens.verticalPaddingDp.dp)

        Box(modifier = bubbleModifier) {
            val density = LocalDensity.current
            val resources = remember(emoticonMap, item.inlineEmotes) {
                buildMap {
                    emoticonMap.forEach { (key, url) ->
                        put(if (key.startsWith("[")) key else "[$key]", LiveMessageEmote(url))
                    }
                    putAll(item.inlineEmotes)
                }
            }
            val direct = item.emoticon ?: item.emoticonUrl?.takeIf { it.isNotBlank() && item.inlineEmotes[item.text] == null }?.let { LiveMessageEmote(it) }
            val segments = remember(item.text, resources) { splitLiveMessageEmotes(item.text, resources.keys) }
            val medal = remember(item.medal, item.medalName, item.medalLevel, item.medalColor) { resolveLiveMessageMedal(item) }
            val medalSize = medal?.let { rememberLiveMedalSize(it) }
            val anchorColor = MaterialTheme.colorScheme.primary
            val annotatedText = remember(item, segments, usernameColor, replyColor, direct, isAnchor, anchorColor, medal) {
                androidx.compose.ui.text.buildAnnotatedString {
                    if (isAnchor) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = anchorColor, fontWeight = FontWeight.Medium))
                        append("主播 ")
                        pop()
                    }
                    if (medal != null) appendInlineContent("sender-medal", "${medal.name} ${medal.level} ")
                    with(androidx.compose.ui.text.SpanStyle(color = usernameColor, fontWeight = FontWeight.Medium)) {
                        pushStyle(this)
                        val start = length
                        append("${item.uname.ifBlank { "直播观众" }}: ")
                        pop()
                        addLink(androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = "sender", linkInteractionListener = { showMenu = true },
                        ), start, length)
                    }
                    if (item.replyToName.isNotBlank()) {
                        val start = length
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = replyColor))
                        append("@${item.replyToName} ")
                        pop()
                        if (item.replyToUid > 0L) addLink(androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = "reply", linkInteractionListener = { latestOnUserClick(item.replyToUid) },
                        ), start, length)
                    }
                    if (direct != null) appendInlineContent("direct-emote", item.text)
                    else segments.forEach { (text, emote) ->
                        if (emote) appendInlineContent("emote:$text", text) else append(text)
                    }
                }
            }
            val inlineContentMap = remember(resources, direct, density.density, density.fontScale, medal, medalSize, compact) {
                buildMap<String, androidx.compose.foundation.text.InlineTextContent> {
                    fun addEmote(key: String, emote: LiveMessageEmote, standalone: Boolean) {
                        // 独立房间表情使用像素尺寸，混排表情使用消息提供的逻辑尺寸。
                        val width = if (standalone) (if (emote.official) emote.width else 162) / density.density else emote.width.toFloat()
                        val height = if (standalone) (if (emote.official) emote.height else 162) / density.density else emote.height.toFloat()
                        // 紧凑互动窗限制表情高度并保持比例，避免单个表情占满消息视口。
                        val scale = if (compact) minOf(1f, (if (standalone) 28f else 18f) / height.coerceAtLeast(1f)) else 1f
                        put(key, androidx.compose.foundation.text.InlineTextContent(
                            androidx.compose.ui.text.Placeholder(
                                width = (width * scale / density.fontScale).sp,
                                height = (height * scale / density.fontScale).sp,
                                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
                            )
                        ) { AsyncImage(model = emote.url, contentDescription = null, modifier = Modifier.fillMaxSize()) })
                    }
                    resources.forEach { (key, emote) -> addEmote("emote:$key", emote, false) }
                    if (direct != null) addEmote("direct-emote", direct, true)
                    if (medal != null && medalSize != null) put("sender-medal", androidx.compose.foundation.text.InlineTextContent(
                        androidx.compose.ui.text.Placeholder(
                            width = ((medalSize.width.value + 4f) / density.fontScale).sp,
                            height = (medalSize.height.value / density.fontScale).sp,
                            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
                        )
                    ) { Box(Modifier.fillMaxSize().padding(end = 4.dp), contentAlignment = Alignment.CenterStart) { LiveMedalBadge(medal) } })
                }
            }
            androidx.compose.foundation.text.BasicText(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 12.sp else tokens.fontSizeSp.sp,
                    lineHeight = if (compact) 16.sp else MaterialTheme.typography.bodyMedium.lineHeight,
                    color = bodyColor, fontWeight = FontWeight.Normal,
                ),
                inlineContent = inlineContentMap,
            )
        }

        LiveDanmakuUserMenu(
            expanded = showMenu,
            item = item,
            canInteract = canInteract,
            onDismiss = { showMenu = false },
            onCopyInfo = {
                copyLiveDanmakuInfo(context, item)
            },
            onUserClick = onUserClick,
            onAtUser = onAtUser,
            onBlockUser = onBlockUser,
            onReportDanmaku = onReportDanmaku
        )
    }
}

@Composable
private fun LiveDanmakuUserMenu(
    expanded: Boolean,
    canInteract: Boolean,
    item: LiveDanmakuItem,
    onDismiss: () -> Unit,
    onCopyInfo: () -> Unit,
    onUserClick: (Long) -> Unit,
    onAtUser: (LiveDanmakuItem) -> Unit,
    onBlockUser: (LiveDanmakuItem) -> Unit,
    onReportDanmaku: (LiveDanmakuItem) -> Unit
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        AppDropdownMenuItem(
            text = {
                AppText(
                    text = item.uname.ifBlank { "弹幕" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = {}
        )
        AppHorizontalDivider()
        AppDropdownMenuItem(
            text = { AppText("复制弹幕信息") },
            onClick = {
                onDismiss()
                onCopyInfo()
            }
        )
        AppDropdownMenuItem(
            text = { AppText("去TA的个人空间") },
            enabled = item.uid > 0L,
            onClick = {
                onDismiss()
                onUserClick(item.uid)
            }
        )
        if (!canInteract) return@AppDropdownMenu
        AppDropdownMenuItem(
            text = { AppText("@TA") },
            enabled = item.uname.isNotBlank(),
            onClick = {
                onDismiss()
                onAtUser(item)
            }
        )
        AppDropdownMenuItem(
            text = { AppText("屏蔽发送者") },
            enabled = item.uname.isNotBlank() || item.uid > 0L,
            onClick = {
                onDismiss()
                onBlockUser(item)
            }
        )
        AppDropdownMenuItem(
            text = { AppText("举报选中弹幕") },
            enabled = item.text.isNotBlank(),
            onClick = {
                onDismiss()
                onReportDanmaku(item)
            }
        )
    }
}

private fun copyLiveDanmakuInfo(
    context: Context,
    item: LiveDanmakuItem
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "直播弹幕信息",
            encodeLiveDanmakuInfo(item)
        )
    )
    Toast.makeText(context, "已复制弹幕信息", Toast.LENGTH_SHORT).show()
}

// [新增] 用户等级组件
@Composable
private fun UserLevelBadge(level: Int) {
    // 简单的胶囊样式
    // 颜色根据等级变化 (简化处理：低等级灰/蓝，高等级橙/红)
    val color = MaterialTheme.colorScheme.secondary

    AppSurface(
                border = androidx.compose.foundation.BorderStroke(AppSpacingTokens.Micro / 2, color),
        shape = AppShapes.borderedContainer(ContainerLevel.Tag),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(top = AppSpacingTokens.Micro)
    ) {
         AppText(
            text = "UL$level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = AppSpacingTokens.Micro)
        )
    }
}

@Composable
internal fun LiveChatInputBar(
    isOverlay: Boolean,
    isDanmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onLike: (Int) -> Unit,
    onOpenEmote: () -> Unit,
    onOpenSend: () -> Unit,
    compact: Boolean = false,
    selectedPage: Int = 0,
    showPageIndicator: Boolean = false,
    canLike: Boolean = true,
    onLikeUnavailable: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = rememberLiveChromePalette()
    val foreground = if (isOverlay) LiveStatusPalette.MediaContent else palette.primaryText
    AppSurface(
        modifier = modifier,
        color = if (compact) Color.Transparent else if (isOverlay) foreground.copy(alpha = 0.10f) else palette.surfaceElevated,
        shape = if (compact) RectangleShape else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = null,
    ) {
        Box {
        if (!compact) androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val radius = 20.dp.toPx()
            val outline = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, radius)
                quadraticBezierTo(0f, 0f, radius, 0f)
                lineTo(size.width - radius, 0f)
                quadraticBezierTo(size.width, 0f, size.width, radius)
            }
            drawPath(outline, foreground.copy(alpha = 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            if (showPageIndicator) {
                val indicator = androidx.compose.ui.graphics.Path().apply {
                    if (selectedPage == 0) {
                        moveTo(0f, radius)
                        quadraticBezierTo(0f, 0f, radius, 0f)
                        lineTo(size.width / 2, 0f)
                    } else {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width - radius, 0f)
                        quadraticBezierTo(size.width, 0f, size.width, radius)
                    }
                }
                drawPath(indicator, foreground.copy(alpha = 0.38f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }
        Row(
            // 悬浮窗内部不再消费系统栏，也不重复铺设圆角底板。
            modifier = Modifier.fillMaxWidth()
                .then(if (compact) Modifier.height(44.dp) else Modifier.navigationBarsPadding().height(LIVE_INPUT_HEIGHT_DP.dp))
                .clickable(onClick = onOpenSend)
                .padding(if (compact) PaddingValues(horizontal = 4.dp) else PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 25.dp)),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIconButton(onClick = onToggleDanmaku, modifier = Modifier.size(34.dp)) {
                LiveDanmakuControlIcon(
                    enabled = isDanmakuEnabled,
                    tint = if (isDanmakuEnabled) AppContentColors.selectionAccent(
                        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
                    ) else foreground,
                    modifier = Modifier.size(22.dp),
                )
            }
            AppText("发送弹幕", modifier = Modifier.weight(1f).padding(top = if (compact) 0.dp else 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = foreground.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            LiveLikeButton(tint = foreground, onLike = onLike, modifier = Modifier.size(34.dp), canLike = canLike, onUnavailable = onLikeUnavailable)
            AppIconButton(onClick = onOpenEmote, modifier = Modifier.size(34.dp)) {
                AppIcon(Icons.Outlined.EmojiEmotions, "表情", tint = foreground)
            }
        }
        }
    }
}



@Composable
internal fun LiveLikeButton(
    tint: Color,
    onLike: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canLike: Boolean = true,
    onUnavailable: () -> Unit = {},
) {
    var likeCount by remember { mutableIntStateOf(0) }
    var flushJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestOnLike by rememberUpdatedState(onLike)
    val latestCanLike by rememberUpdatedState(canLike)
    val pressSource = remember { MutableInteractionSource() }
    var handledPress by remember { mutableStateOf(false) }
    val palette = rememberLiveChromePalette()
    val visualSpec = remember { resolveLiveChatInputVisualSpec() }

    DisposableEffect(Unit) {
        onDispose { flushJob?.cancel() }
    }

    LaunchedEffect(pressSource) {
        pressSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    if (!latestCanLike) return@collect
                    handledPress = true
                    flushJob?.cancel()
                    likeCount += 1
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                    if (!latestCanLike) return@collect
                    flushJob = scope.launch {
                        delay(800)
                        val count = likeCount
                        likeCount = 0
                        handledPress = false
                        if (count > 0) latestOnLike(count)
                    }
                }
                else -> Unit
            }
        }
    }

    Box(modifier = modifier) {
        AppIconButton(
            onClick = likeClick@{
                if (!canLike) { onUnavailable(); return@likeClick }
                if (!handledPress) {
                    likeCount += 1
                    flushJob?.cancel()
                    flushJob = scope.launch {
                        delay(800)
                        val count = likeCount
                        likeCount = 0
                        if (count > 0) latestOnLike(count)
                    }
                }
                handledPress = false
            },
            interactionSource = pressSource,
            modifier = Modifier.size(34.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.ThumbUpOffAlt,
                contentDescription = "点赞",
                tint = tint,
                modifier = Modifier.size(visualSpec.iconSizeDp.dp)
            )
        }
        AnimatedContent(
            targetState = likeCount,
            transitionSpec = {
                (scaleIn(initialScale = 0.72f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 1.18f) + fadeOut())
            },
            label = "live-like-burst",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = visualSpec.likeBadgeOffsetXDp.dp,
                    y = visualSpec.likeBadgeOffsetYDp.dp,
                ),
        ) { count ->
            if (count <= 0) {
                Spacer(Modifier)
            } else {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Tag),
                    color = palette.accent.copy(alpha = 0.96f),
                ) {
                    AppText(
                        text = "x$count",
                        color = palette.onAccent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = AppSpacingTokens.ExtraSmall,
                            vertical = AppSpacingTokens.Micro
                        )
                    )
                }
            }
        }
    }
}
