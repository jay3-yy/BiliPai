package com.android.purebilibili.feature.live.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.blur.hazeEffectCompat
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.LiveChatMessage
import com.android.purebilibili.feature.live.LiveStatusPalette
import com.android.purebilibili.feature.live.rememberLiveChromePalette
import com.android.purebilibili.feature.live.resolveLiveMedalBadgeVisualSpec
import com.android.purebilibili.feature.live.resolveLiveMedalColor
import com.android.purebilibili.feature.live.resolveLiveSuperChatColor
import com.android.purebilibili.feature.live.shouldRenderLiveDanmaku
import com.android.purebilibili.feature.live.shouldRenderLiveDanmakuImageEmoticon
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 竖屏直播悬浮聊天流组件（对齐 PiliPlus `LiveRoomChatPanel(isPP: true)`）
 *
 * 特性：
 * 1. 实时消息列表平滑滚动与自动跟底。
 * 2. 用户向上翻看时暂停自动滚动，并在右下角出现「回到底部」胶囊按钮。
 * 3. 消息气泡包含：粉丝勋章、用户名、@TA 提示、表情图与富文本解析、SC 专属高亮。
 * 4. 点击弹幕弹出操作菜单（@TA、进空间、复制、屏蔽发送者、举报）。
 * 5. 直播间有醒目留言时，右上角常驻 `SC(数量) >` 快捷入口。
 */
@Composable
internal fun LivePortraitChatStream(
    messages: List<LiveChatMessage>,
    danmakuSequence: Long = 0L,
    superChatCount: Int = 0,
    onOpenSuperChat: (() -> Unit)? = null,
    onUserClick: (Long) -> Unit = {},
    onAtUser: (LiveDanmakuItem) -> Unit = {},
    onBlockUser: (LiveDanmakuItem) -> Unit = {},
    onReportDanmaku: (LiveDanmakuItem) -> Unit = {},
    onOpenHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isAnyMenuOpen by remember { mutableStateOf(false) }
    var userScrolledUp by remember { mutableStateOf(false) }

    val latestMessages by rememberUpdatedState(messages)
    val isAtBottom by remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty() || latestMessages.isEmpty()) {
                true
            } else {
                visible.last().index >= latestMessages.lastIndex - 1
            }
        }
    }

    // 用户主动向上翻动时暂停自动跟底；滑回最底或甩到底部时自动恢复
    LaunchedEffect(isDragged, isAtBottom) {
        if (isDragged && !isAtBottom) {
            userScrolledUp = true
        } else if (isAtBottom && !isDragged) {
            userScrolledUp = false
        }
    }

    // 新弹幕到来时平滑滚动到最新（60ms 防抖批处理，高频弹幕不掉帧）
    LaunchedEffect(danmakuSequence, messages.lastOrNull()) {
        if (messages.isNotEmpty() && !userScrolledUp && !isAnyMenuOpen) {
            delay(60L)
            if (!userScrolledUp && !isAnyMenuOpen && !isDragged && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    // 弹幕弹窗关闭时若未脱离底部则自动滚回最新
    LaunchedEffect(isAnyMenuOpen) {
        if (!isAnyMenuOpen && !userScrolledUp && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = modifier) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clip(AppShapes.container(ContainerLevel.Pill))
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffectCompat(
                                state = hazeState,
                                style = HazeMaterials.ultraThin()
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(LiveStatusPalette.MediaScrim.copy(alpha = if (hazeState != null) 0.38f else 0.52f))
                    .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall)
                    .clickable(
                        enabled = onOpenHistory != null,
                        role = Role.Button,
                        onClickLabel = "查看聊天记录",
                        onClick = { onOpenHistory?.invoke() }
                    )
            ) {
                AppText(
                    text = "暂无消息，发送弹幕与主播互动",
                    color = LiveStatusPalette.MediaContent.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    space = AppSpacingTokens.ExtraSmall,
                    alignment = Alignment.Bottom
                ),
                contentPadding = PaddingValues(vertical = AppSpacingTokens.ExtraSmall)
            ) {
                items(items = messages, key = { it.sequence }) { message ->
                    LivePortraitDanmakuBubble(
                        item = message.item,
                        hazeState = hazeState,
                        onUserClick = onUserClick,
                        onAtUser = onAtUser,
                        onBlockUser = onBlockUser,
                        onReportDanmaku = onReportDanmaku,
                        onOpenSuperChat = { onOpenSuperChat?.invoke() },
                        onMenuVisibilityChange = { isAnyMenuOpen = it }
                    )
                }
            }
        }

        if (messages.isNotEmpty() && onOpenHistory != null) {
            AppSurface(
                onClick = onOpenHistory,
                shape = AppShapes.container(ContainerLevel.Pill),
                color = LiveStatusPalette.MediaScrim.copy(alpha = 0.68f),
                contentColor = LiveStatusPalette.MediaContent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = AppSpacingTokens.ExtraSmall, top = AppSpacingTokens.ExtraSmall)
                    .semantics { contentDescription = "查看完整聊天记录" },
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .padding(horizontal = AppSpacingTokens.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = "完整聊天",
                        color = LiveStatusPalette.MediaContent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // SC 醒目留言徽章（右上角）
        if (superChatCount > 0 && onOpenSuperChat != null) {
            AppSurface(
                onClick = onOpenSuperChat,
                shape = AppShapes.container(ContainerLevel.Pill),
                color = LiveStatusPalette.MediaScrim.copy(alpha = 0.68f),
                contentColor = LiveStatusPalette.MediaContent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = AppSpacingTokens.ExtraSmall, top = AppSpacingTokens.ExtraSmall)
                    .semantics { contentDescription = "查看 $superChatCount 条醒目留言" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppText(
                        text = "SC($superChatCount)",
                        color = Color(0xFFFFD54F),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = LiveStatusPalette.MediaContent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 回到底部按钮（右下角）
        AnimatedVisibility(
            visible = userScrolledUp && !isAtBottom,
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppSpacingTokens.Small, bottom = AppSpacingTokens.Small)
        ) {
            AppSurface(
                onClick = {
                    userScrolledUp = false
                    scope.launch {
                        listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
                    }
                },
                shape = AppShapes.container(ContainerLevel.Pill),
                color = LiveStatusPalette.MediaScrim.copy(alpha = 0.82f),
                contentColor = LiveStatusPalette.MediaContent,
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = 4.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = LiveStatusPalette.MediaContent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    AppText(
                        text = "回到底部",
                        color = LiveStatusPalette.MediaContent,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** 兼容旧版调用的别名 / 包装器 */
@Composable
internal fun LivePortraitChatPreview(
    messages: List<LiveDanmakuItem>,
    maxMessages: Int = 200,
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    superChatCount: Int = 0,
    onOpenSuperChat: (() -> Unit)? = null,
    onUserClick: (Long) -> Unit = {},
    onAtUser: (LiveDanmakuItem) -> Unit = {},
    onBlockUser: (LiveDanmakuItem) -> Unit = {},
    onReportDanmaku: (LiveDanmakuItem) -> Unit = {},
) {
    LivePortraitChatStream(
        messages = messages.mapIndexed { index, item -> LiveChatMessage(index.toLong(), item) },
        superChatCount = superChatCount,
        onOpenSuperChat = onOpenSuperChat,
        onUserClick = onUserClick,
        onAtUser = onAtUser,
        onBlockUser = onBlockUser,
        onReportDanmaku = onReportDanmaku,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
        hazeState = hazeState,
    )
}

/** 单条弹幕气泡（对齐 PiliPlus 视觉与交互） */
@Composable
private fun LivePortraitDanmakuBubble(
    item: LiveDanmakuItem,
    hazeState: HazeState?,
    onUserClick: (Long) -> Unit,
    onAtUser: (LiveDanmakuItem) -> Unit,
    onBlockUser: (LiveDanmakuItem) -> Unit,
    onReportDanmaku: (LiveDanmakuItem) -> Unit,
    onOpenSuperChat: () -> Unit,
    onMenuVisibilityChange: (Boolean) -> Unit,
) {
    if (item.isSuperChat) {
        LivePortraitSuperChatBubble(
            item = item,
            hazeState = hazeState,
            onClick = onOpenSuperChat,
        )
        return
    }
    if (!shouldRenderLiveDanmaku(item.text, item.emoticonUrl)) {
        return
    }

    val context = LocalContext.current
    val palette = rememberLiveChromePalette()
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(showMenu) {
        onMenuVisibilityChange(showMenu)
    }

    val bubbleShape = AppShapes.container(ContainerLevel.Pill)
    val usernameColor = when {
        item.isAdmin -> LiveStatusPalette.AdminName
        item.isSelf -> palette.accentStrong
        else -> LiveStatusPalette.MediaContent.copy(alpha = 0.92f)
    }
    val emoticonMap by DanmakuEmoticonMapper.emoticonMap.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .clip(bubbleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffectCompat(
                        state = hazeState,
                        style = HazeMaterials.ultraThin()
                    )
                } else {
                    Modifier
                }
            )
            .background(LiveStatusPalette.MediaScrim.copy(alpha = if (hazeState != null) 0.38f else 0.52f))
            .clickable(
                role = Role.Button,
                onClickLabel = "操作弹幕：${item.uname}",
                onClick = { showMenu = true }
            )
            .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall)
    ) {
        if (shouldRenderLiveDanmakuImageEmoticon(item.emoticonUrl)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)
            ) {
                if (item.medalLevel > 0 && item.medalName.isNotBlank()) {
                    LivePortraitMedalBadge(name = item.medalName, level = item.medalLevel, color = item.medalColor)
                }
                AppText(
                    text = "${item.uname.ifBlank { "直播观众" }}: ",
                    color = usernameColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                AsyncImage(
                    model = item.emoticonUrl,
                    contentDescription = item.text,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            val chatFontSize = MaterialTheme.typography.bodySmall.fontSize
            val annotatedText = remember(item.text, item.uname, item.replyToName, emoticonMap, usernameColor, chatFontSize) {
                val builder = androidx.compose.ui.text.AnnotatedString.Builder()
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = usernameColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = chatFontSize
                    )
                )
                builder.append("${item.uname.ifBlank { "直播观众" }}: ")
                builder.pop()

                if (item.replyToName.isNotBlank()) {
                    builder.pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = LiveStatusPalette.Reply,
                            fontWeight = FontWeight.Medium,
                            fontSize = chatFontSize
                        )
                    )
                    builder.append("@${item.replyToName} ")
                    builder.pop()
                }

                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = LiveStatusPalette.MediaContent,
                        fontWeight = FontWeight.Normal,
                        fontSize = chatFontSize
                    )
                )
                builder.append(DanmakuEmoticonMapper.parse(item.text, emoticonMap))
                builder.pop()
                builder.toAnnotatedString()
            }

            val inlineContentMap = remember(item.text, emoticonMap) {
                val usedKeys = Regex("\\[(.*?)\\]").findAll(item.text).map { it.value }.toSet()
                emoticonMap.filterKeys { it in usedKeys }.mapValues { (_, url) ->
                    androidx.compose.foundation.text.InlineTextContent(
                        androidx.compose.ui.text.Placeholder(
                            width = 1.35.em,
                            height = 1.35.em,
                            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center
                        )
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)
            ) {
                if (item.medalLevel > 0 && item.medalName.isNotBlank()) {
                    LivePortraitMedalBadge(name = item.medalName, level = item.medalLevel, color = item.medalColor)
                }
                androidx.compose.foundation.text.BasicText(
                    text = annotatedText,
                    inlineContent = inlineContentMap,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LivePortraitUserMenu(
            expanded = showMenu,
            item = item,
            onDismiss = { showMenu = false },
            onCopyInfo = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("弹幕内容", item.text))
                Toast.makeText(context, "已复制弹幕内容", Toast.LENGTH_SHORT).show()
            },
            onUserClick = onUserClick,
            onAtUser = onAtUser,
            onBlockUser = onBlockUser,
            onReportDanmaku = onReportDanmaku
        )
    }
}

/** 粉丝勋章 */
@Composable
private fun LivePortraitMedalBadge(
    name: String,
    level: Int,
    color: Int,
) {
    val medalColor = resolveLiveMedalColor(color)
    val visualSpec = resolveLiveMedalBadgeVisualSpec()
    Row(
        modifier = Modifier
            .height(visualSpec.heightDp.dp)
            .clip(RoundedCornerShape(visualSpec.cornerRadiusDp.dp))
            .background(medalColor.copy(alpha = 0.85f))
            .padding(horizontal = visualSpec.horizontalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppText(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = visualSpec.fontSizeSp.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        AppText(
            text = "$level",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = visualSpec.fontSizeSp.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/** 醒目留言小气泡 */
@Composable
private fun LivePortraitSuperChatBubble(
    item: LiveDanmakuItem,
    hazeState: HazeState?,
    onClick: () -> Unit,
) {
    val bg = resolveLiveSuperChatColor(item.superChatBackgroundColor)
    AppSurface(
        color = bg.copy(alpha = 0.88f),
        shape = AppShapes.container(ContainerLevel.Card),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable(onClickLabel = "查看醒目留言", onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppText(
                    text = item.uname.ifBlank { "醒目留言" },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                AppText(
                    text = "¥${item.superChatPrice}",
                    color = Color.White.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            AppText(
                text = item.text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 弹幕作者操作菜单 */
@Composable
private fun LivePortraitUserMenu(
    expanded: Boolean,
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
                    text = item.uname.ifBlank { "弹幕操作" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = {}
        )
        AppHorizontalDivider()
        AppDropdownMenuItem(
            text = { AppText("@TA") },
            enabled = item.uname.isNotBlank(),
            onClick = {
                onDismiss()
                onAtUser(item)
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
        AppDropdownMenuItem(
            text = { AppText("复制弹幕内容") },
            onClick = {
                onDismiss()
                onCopyInfo()
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

/**
 * 竖屏直播底部控制栏（对齐 PiliPlus `_buildInputWidget`）
 *
 * 布局：
 * [弹幕开关] [发送弹幕…输入条] [点赞(连击动效)] [表情面板快捷] [聊天流开关] [更多操作]
 */
@Composable
internal fun LivePortraitBottomBar(
    isDanmakuEnabled: Boolean = true,
    chatVisible: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    onOpenSend: () -> Unit,
    onOpenEmote: (() -> Unit)? = null,
    onToggleChat: () -> Unit,
    onOpenMore: () -> Unit,
    onLike: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val barShape = AppShapes.container(ContainerLevel.Pill)
    val mediaColors = AppIconButtonDefaults.colors(
        containerColor = LiveStatusPalette.MediaScrim.copy(alpha = if (hazeState != null) 0.38f else 0.56f),
        contentColor = LiveStatusPalette.MediaContent,
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. 弹幕开关
        NativeDanmakuToggleButton(
            enabled = isDanmakuEnabled,
            onToggle = onToggleDanmaku,
            activeTint = LiveStatusPalette.MediaContent,
            inactiveTint = LiveStatusPalette.MediaContent.copy(alpha = 0.55f),
            modifier = Modifier.size(30.dp)
        )

        // 2. 发送弹幕输入条
        AppSurface(
            onClick = onOpenSend,
            shape = barShape,
            color = LiveStatusPalette.MediaScrim.copy(alpha = if (hazeState != null) 0.38f else 0.56f),
            contentColor = LiveStatusPalette.MediaContent,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 30.dp)
                .clip(barShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffectCompat(
                            state = hazeState,
                            style = HazeMaterials.ultraThin()
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics { contentDescription = "发送弹幕" },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large),
                contentAlignment = Alignment.CenterStart,
            ) {
                AppText(
                    text = "说点什么…",
                    color = LiveStatusPalette.MediaContent.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 3. 连击点赞按钮
        if (onLike != null) {
            LiveLikeButton(
                tint = LiveStatusPalette.MediaContent,
                onLike = onLike,
                modifier = Modifier.size(30.dp)
            )
        }

        // 4. 表情包快捷入口
        if (onOpenEmote != null) {
            AppIconButton(
                onClick = onOpenEmote,
                colors = mediaColors,
                modifier = Modifier.size(30.dp)
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.EmojiEmotions,
                    contentDescription = "选择表情",
                    tint = LiveStatusPalette.MediaContent,
                )
            }
        }

        // 5. 聊天显隐开关
        AppIconButton(
            onClick = onToggleChat,
            colors = mediaColors,
            modifier = Modifier
                .size(30.dp)
                .semantics { stateDescription = if (chatVisible) "聊天已显示" else "聊天已隐藏" },
        ) {
            AppIcon(
                imageVector = rememberAppCommentIcon(),
                contentDescription = if (chatVisible) "隐藏聊天" else "显示聊天",
                tint = LiveStatusPalette.MediaContent.copy(alpha = if (chatVisible) 1f else 0.65f),
            )
        }

        // 6. 更多操作
        AppIconButton(
            onClick = onOpenMore,
            colors = mediaColors,
            modifier = Modifier.size(30.dp)
        ) {
            AppIcon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = "更多直播操作",
                tint = LiveStatusPalette.MediaContent,
            )
        }
    }
}

/** 竖屏更多操作面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LivePortraitMoreSheet(
    actions: List<AppWindowAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(
                horizontal = AppSpacingTokens.Large,
                vertical = AppSpacingTokens.Small,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        ) {
            item {
                AppText(
                    text = "直播操作",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = AppSpacingTokens.Small),
                )
                AppText(
                    text = "直播播放与互动设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(actions, key = { it.label }) { action ->
                AppSurface(
                    onClick = {
                        onDismiss()
                        action.onClick?.invoke()
                    },
                    enabled = action.enabled,
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = AppSurfaceTokens.cardContainer(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(
                        text = action.label,
                        color = if (action.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .padding(AppSpacingTokens.Medium),
                    )
                }
            }
        }
    }
}
