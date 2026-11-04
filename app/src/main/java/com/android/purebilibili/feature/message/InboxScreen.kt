// 私信收件箱页面
package com.android.purebilibili.feature.message

import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.MessageCenterPreferences
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.feature.message.MessageAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSnackbar
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.core.ui.AppSurfaceTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberStaticSkeletonBlockColor
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth
import com.android.purebilibili.core.util.responsiveContentWidth

/**
 * 会话行的 lazy item 身份。骨架行与真实会话行共用同一个 contentType，加载完成时
 * LazyList 才能在原地替换 item，而不是「整批删除 + 整批新增」重建可视区。
 */
internal const val MESSAGE_SESSION_ROW_CONTENT_TYPE = "message_session_row"

/** 首屏会话骨架行数。与真实首屏页大小同量级，避免加载完成后列表长度突变。 */
private const val MESSAGE_SESSION_SKELETON_ROW_COUNT = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onTopItemClick: (MessageCenterDestination) -> Unit,
    onSessionClick: (talkerId: Long, sessionType: Int, userName: String) -> Unit,
    onOpenLink: (String) -> Unit = {},
    viewModel: InboxViewModel = viewModel()
) {
    val accountIdentity by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val outsideActionLimit = if (LocalConfiguration.current.screenWidthDp < 360) 0 else 2
    val webThemeQuery = if (AppSurfaceTokens.surface().luminance() < 0.5f) "native.theme=2&night=1" else "native.theme=1&night=0"
    val disabledLikesFlow = remember(context) { MessageCenterPreferences.disabledLikes(context) }
    val disabledLikes by disabledLikesFlow.collectAsStateWithLifecycle(initialValue = false)
    var hasResumed by remember(accountIdentity) { mutableStateOf(false) }
    LifecycleResumeEffect(accountIdentity) {
        if (hasResumed) viewModel.refresh() else hasResumed = true
        onPauseOrDispose {}
    }
    val rawUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = rawUiState.takeIf { it.accountIdentity == accountIdentity }
        ?: InboxUiState(accountIdentity = accountIdentity)
    var auxiliaryPages by rememberSaveable(accountIdentity) { mutableStateOf<List<String>>(emptyList()) }
    var pendingBatch by remember(accountIdentity) { mutableStateOf<Int?>(null) }
    val goBack = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = uiState.pageType != 1, onBack = { goBack() })
    if (auxiliaryPages.isNotEmpty()) {
        MessageAuxiliaryPage(
            page = auxiliaryPages.last(),
            onBack = { auxiliaryPages = auxiliaryPages.dropLast(1); if (auxiliaryPages.isEmpty()) viewModel.refresh() },
            onPage = { next -> auxiliaryPages = if (next.startsWith("contacts:") && auxiliaryPages.last().startsWith("contacts:")) auxiliaryPages.dropLast(1) + next else auxiliaryPages + next },
            onOpenLink = onOpenLink,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val openMenuItem: (com.android.purebilibili.data.repository.ImMenuItem) -> Unit = { item ->
        when (item.type) {
            1, 9 -> pendingBatch = item.type
            2 -> auxiliaryPages = listOf("settings:0")
            8 -> auxiliaryPages = listOf("contacts:following")
            4 -> {
                val id = Regex("""/(\d{3,})""").find(item.url)?.groupValues?.get(1)?.toLongOrNull()
                if (id != null) onSessionClick(id, 1, item.title) else if (item.url.isNotBlank()) onOpenLink(item.url)
            }
            else -> if (item.url.isNotBlank()) onOpenLink(item.url) else android.widget.Toast.makeText(context, "当前入口暂不可用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    if (pendingBatch != null) MessageConfirmDialog(
        title = if (pendingBatch == 1) "一键已读" else "清空列表",
        content = if (pendingBatch == 1) "是否清除当前列表全部新消息提醒？" else "确定清空当前会话列表？此操作无法撤销。",
        onDismiss = { pendingBatch = null },
    ) { if (pendingBatch == 1) viewModel.markAllRead() else viewModel.clearSessions(); pendingBatch = null }
    var lastAutoLoadEndTs by remember(accountIdentity) { mutableLongStateOf(0L) }
    var pendingRemoveSession by remember(accountIdentity) { mutableStateOf<SessionItem?>(null) }

    LaunchedEffect(uiState.sessions.firstOrNull()?.talker_id, uiState.isRefreshing, uiState.isLoading) {
        if (uiState.isRefreshing || uiState.isLoading) {
            lastAutoLoadEndTs = 0L
        }
    }

    AppScaffold(
        blurContentReady = !uiState.isLoading,
        topBar = {
            AppTopBar(
                title = uiState.title,
                navigationIcon = {
                    AppIconButton(onClick = goBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.pageType == 1) {
                            AppIconButton(onClick = { onOpenLink("https://www.bilibili.com/h5/follow/newFans?navhide=1&$webThemeQuery") }) { AppIcon(Icons.Outlined.PersonAdd, "新增粉丝") }
                        }
                        val visibleOutside = uiState.outside.filter { it.type in setOf(1, 2, 4, 8, 9) }.take(outsideActionLimit)
                        visibleOutside.forEach { item ->
                            val icon = when (item.type) {
                                1 -> Icons.Outlined.DoneAll
                                2 -> Icons.Outlined.Settings
                                4 -> Icons.Outlined.SmartDisplay
                                8 -> Icons.Outlined.Contacts
                                else -> Icons.Outlined.DeleteSweep
                            }
                            AppIconButton(onClick = { openMenuItem(item) }, enabled = !uiState.isBatchOperating) { AppIcon(icon, item.title) }
                        }
                        val overflow = uiState.menu + uiState.outside.filter { it !in visibleOutside }
                        var expanded by remember { mutableStateOf(false) }
                        if (overflow.isNotEmpty()) {
                            com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { expanded = true }) {
                                AppIconButton(onClick = { expanded = true }, enabled = !uiState.isBatchOperating) { AppIcon(Icons.Default.MoreVert, "更多") }
                                AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    overflow.forEach { item -> AppDropdownMenuItem(text = { AppText(item.title) }, onClick = { expanded = false; openMenuItem(item) }) }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .responsiveContentWidth(maxWidth = resolveSingleColumnFeedMaxWidth())
        ) {
            // 首页快捷入口在加载期间保留，仅替换会话行；分类子页使用独立列表。
            AdaptivePullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                indicatorTopInset = paddingValues.calculateTopPadding()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 8.dp, bottom = paddingValues.calculateBottomPadding() + 8.dp)
                ) {
                    if (uiState.pageType == 1) {
                        item(key = "message_center_shortcut_row") {
                            MessageCenterTopShortcutRow(
                                items = buildMessageCenterTopItems(uiState.feedUnreadData).map { if (disabledLikes && it.destination == MessageCenterDestination.LikeMe) it.copy(unreadCount = 0) else it },
                                onItemClick = { destination ->
                                    if (disabledLikes && destination == MessageCenterDestination.LikeMe) android.widget.Toast.makeText(context, "已禁用", android.widget.Toast.LENGTH_SHORT).show()
                                    else onTopItemClick(destination)
                                }
                            )
                        }
                    }

                    when {
                        uiState.isLoading -> {
                            items(
                                count = MESSAGE_SESSION_SKELETON_ROW_COUNT,
                                key = { index -> "message_session_skeleton_$index" },
                                contentType = { MESSAGE_SESSION_ROW_CONTENT_TYPE }
                            ) { index ->
                                SessionListItemSkeleton(
                                    slotIndex = index,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        uiState.error != null && uiState.sessions.isEmpty() -> {
                            item(key = "message_inbox_error") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.6f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AppText(uiState.error ?: "加载失败")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AppButton(onClick = { viewModel.loadSessions() }) {
                                        AppText("重试")
                                    }
                                }
                            }
                        }

                        uiState.sessions.isEmpty() -> {
                            item(key = "message_inbox_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.5f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText(
                                        text = "暂无私信",
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                            }
                        }

                        else -> {
                            uiState.error?.let { error ->
                                item(key = "message_inbox_refresh_error") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppText(error, modifier = Modifier.weight(1f))
                                        AppTextButton(onClick = { viewModel.refresh() }) {
                                            AppText("重试")
                                        }
                                    }
                                }
                            }
                            items(
                                items = uiState.sessions,
                                key = InboxSessionPaginationPolicy::resolveSessionKey,
                                contentType = { MESSAGE_SESSION_ROW_CONTENT_TYPE }
                            ) { session ->
                                if (
                                    uiState.hasMore &&
                                    uiState.endTs > 0L &&
                                    uiState.endTs != lastAutoLoadEndTs &&
                                    session == uiState.sessions.lastOrNull() &&
                                    !uiState.isLoadingMore &&
                                    uiState.loadMoreError == null
                                ) {
                                    LaunchedEffect(session.talker_id, session.session_type, uiState.endTs) {
                                        lastAutoLoadEndTs = uiState.endTs
                                        viewModel.loadMoreSessions()
                                    }
                                }
                                val userInfo = uiState.userInfoMap[session.talker_id]
                                val imEntry = viewModel.entry(session)
                                SessionListItem(
                                    session = session,
                                    userInfo = userInfo,
                                    onClick = {
                                        val userName = InboxUserInfoResolver.resolveDisplayName(
                                            cached = userInfo,
                                            session = session
                                        )
                                        val entry = viewModel.entry(session)
                                        if (!viewModel.openFolder(session)) {
                                            when {
                                                entry?.systemNotice == true -> onTopItemClick(MessageCenterDestination.SystemNotice)
                                                entry?.isPrivate == true -> onSessionClick(session.talker_id, 1, userName)
                                                !entry?.chatUrl.isNullOrBlank() -> onOpenLink(entry!!.chatUrl)
                                                session.talker_id > 0 -> onSessionClick(session.talker_id, session.session_type, userName)
                                            }
                                        }
                                    },
                                    onAvatarClick = viewModel.entry(session)?.profileMid?.takeIf { it > 0L }?.let { mid -> { onOpenLink("bilibili://space/$mid") } },
                                    onRemove = { pendingRemoveSession = session },
                                    onMarkRead = { viewModel.markRead(session) },
                                    isPrivate = imEntry?.isPrivate == true,
                                    unreadDot = imEntry?.unreadDot == true,
                                    unreadLabel = imEntry?.unreadLabel.orEmpty(),
                                    pendantUrl = imEntry?.pendantUrl.orEmpty(),
                                    officialType = imEntry?.officialType,
                                    vipColor = imEntry?.vipColor == true,
                                    userLabel = imEntry?.userLabel.orEmpty(),
                                    onToggleTop = { viewModel.toggleTop(session) },
                                    onToggleDnd = { viewModel.toggleDnd(session) },
                                )
                            }
                        }
                    }

                    if (uiState.hasMore && !uiState.isLoading) {
                        item(key = "message_inbox_load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isLoadingMore) {
                                    com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                                        size = 24.dp
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        uiState.loadMoreError?.let { error -> AppText(error) }
                                        AppTextButton(onClick = { viewModel.loadMoreSessions() }) {
                                            AppText(if (uiState.loadMoreError != null) "重试" else "加载更多")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.operationError?.let { error ->
                AppSnackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        AppTextButton(onClick = { viewModel.clearOperationError() }) {
                            AppText("知道了")
                        }
                    }
                ) {
                    AppText(error)
                }
            }
        }
    }

    pendingRemoveSession?.let { session ->
        AppAlertDialog(
            onDismissRequest = { pendingRemoveSession = null },
            title = { AppText("删除会话") },
            text = { AppText("会话会从列表中移除，但不会删除聊天记录。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        viewModel.removeSession(session)
                        pendingRemoveSession = null
                    }
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingRemoveSession = null }) {
                    AppText("取消")
                }
            }
        )
    }



}

@Composable
private fun MessageCenterTopShortcutRow(
    items: List<MessageCenterTopItem>,
    onItemClick: (MessageCenterDestination) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val columns = 4

        Column {
            items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        MessageCenterShortcutCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onItemClick(item.destination) }
                        )
                    }

                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (rowIndex != items.chunked(columns).lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageCenterShortcutCard(
    item: MessageCenterTopItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val icon = when (item.destination) {
        MessageCenterDestination.ReplyMe -> androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline
        MessageCenterDestination.AtMe -> androidx.compose.material.icons.Icons.Outlined.AlternateEmail
        MessageCenterDestination.LikeMe -> androidx.compose.material.icons.Icons.Outlined.FavoriteBorder
        MessageCenterDestination.SystemNotice -> androidx.compose.material.icons.Icons.Outlined.NotificationsNone
    }
    Column(modifier.appActionClickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AppSurface(modifier = Modifier.size(44.dp), shape = CircleShape, color = AppSurfaceTokens.surfaceContainer()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppIcon(icon, item.title, tint = AppContentColors.accent(), modifier = Modifier.size(20.dp)) }
            }
            if (item.unreadCount > 0) MessageUnreadBadge(
                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        AppText(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun MessageUnreadBadge(
    text: String,
    modifier: Modifier = Modifier,
    isDot: Boolean = false
) {
    if (isDot) {
        // 纯未读点：8dp 实心圆，骑在头像右上边缘，不走计数胶囊的尺寸与内边距。
        Box(
            modifier = modifier
                .size(8.dp)
                .background(color = AppContentColors.accent(), shape = CircleShape)
        )
        return
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(
                color = AppContentColors.accent(),
                shape = AppShapes.container(ContainerLevel.Chip)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = text,
            color = AppContentColors.onAccent(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 会话行骨架。
 *
 * 尺寸严格对齐 [SessionListItem]：分组卡片模式外层 12dp/3dp、行内 16dp/10dp；非分组模式
 * 外层 0、行内 16dp/12dp；头像 48dp。头像 48dp 高于两侧文字列，因此行高由头像决定，
 * 骨架与真实行高一致——这是消除「换内容时行位置跳」的关键。
 *
 * 颜色用 [rememberStaticSkeletonBlockColor] 固定色，不参与呼吸脉冲：呼吸中的灰块与随后的
 * 内容淡入叠加，观感上就是「闪」。
 */
@Composable
private fun SessionListItemSkeleton(
    modifier: Modifier = Modifier,
    slotIndex: Int = 0,
) {
    val useGroupedListCards = rememberAppSemanticVisualPolicy().prefersGroupedListCards
    val blockColor = rememberStaticSkeletonBlockColor()
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (useGroupedListCards) 12.dp else 0.dp,
                vertical = if (useGroupedListCards) 3.dp else 0.dp
            ),
        shape = if (useGroupedListCards) {
            AppShapes.container(ContainerLevel.Card)
        } else {
            RectangleShape
        },
        color = if (useGroupedListCards) {
            AppSurfaceTokens.surfaceContainer()
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (useGroupedListCards) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContentSkeletonBlock(
                color = blockColor,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(if (slotIndex % 3 == 0) 0.40f else 0.30f)
                        .height(16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(if (slotIndex % 2 == 0) 0.72f else 0.58f)
                        .height(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                ContentSkeletonBlock(
                    color = blockColor,
                    shape = CircleShape,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SessionListItem(
    session: SessionItem,
    userInfo: UserBasicInfo? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleTop: () -> Unit,
    onToggleDnd: () -> Unit,
    onMarkRead: () -> Unit = {},
    isPrivate: Boolean = true,
    unreadDot: Boolean = false,
    unreadLabel: String = "",
    pendantUrl: String = "",
    officialType: Int? = null,
    vipColor: Boolean = false,
    userLabel: String = "",
    onAvatarClick: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val useGroupedListCards = rememberAppSemanticVisualPolicy().prefersGroupedListCards

    val displayName = InboxUserInfoResolver.resolveDisplayName(
        cached = userInfo,
        session = session
    )
    val displayAvatar = InboxUserInfoResolver.resolveDisplayAvatar(
        cached = userInfo,
        session = session
    )

    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = appActionWithFeedback(onClick), onLongClick = { showMenu = true })
            .messageSecondaryClick { showMenu = true }
            .padding(horizontal = if (useGroupedListCards) 12.dp else 0.dp, vertical = if (useGroupedListCards) 3.dp else 0.dp),
        shape = if (useGroupedListCards) {
            AppShapes.container(ContainerLevel.Card)
        } else {
            RectangleShape
        },
        color = when {
            useGroupedListCards && session.top_ts > 0 -> AppSurfaceTokens.secondaryContainer().copy(alpha = 0.55f)
            useGroupedListCards -> AppSurfaceTokens.surfaceContainer()
            session.top_ts > 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> Color.Transparent
        }
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (useGroupedListCards) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val hasPendant = pendantUrl.isNotBlank()
                AsyncImage(
                    model = displayAvatar,
                    contentDescription = "头像",
                    modifier = Modifier
                        .fillMaxSize(if (hasPendant) 0.72f else 1f)
                        .clip(CircleShape)
                        .then(if (onAvatarClick != null) Modifier.appActionClickable(onClick = onAvatarClick) else Modifier)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (hasPendant) {
                    AsyncImage(
                        model = pendantUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                UserAvatarCornerMarkBadge(
                    mark = resolveUserAvatarCornerMark(officialType, if (vipColor) 1 else 0, session.live_status == 1),
                    modifier = Modifier.align(Alignment.BottomEnd),
                    badgeSize = 14.dp,
                )
            }

            if (session.is_dnd != 1 && (session.unread_count > 0 || unreadDot || unreadLabel.isNotBlank())) {
                val badgeText = when {
                    unreadLabel.isNotBlank() -> unreadLabel
                    unreadDot -> "•"
                    session.unread_count > 99 -> "99+"
                    else -> session.unread_count.toString()
                }
                MessageUnreadBadge(
                    text = badgeText,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = if (badgeText == "•") 4.dp else 2.dp,
                            y = if (badgeText == "•") (-4).dp else (-2).dp
                        ),
                    isDot = badgeText == "•"
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (vipColor) com.android.purebilibili.core.ui.components.userVipPink() else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (userLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = userLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        modifier = Modifier
                            .background(
                                AppContentColors.secondaryText().copy(alpha = 0.08f),
                                AppShapes.container(ContainerLevel.Tag)
                            )
                            .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = 1.dp)
                    )
                }

                if (session.top_ts > 0) {
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = "置顶",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.accent(),
                        modifier = Modifier
                            .background(
                                AppContentColors.accent().copy(alpha = 0.1f),
                                AppShapes.container(ContainerLevel.Tag)
                            )
                            .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = 1.dp)
                    )
                }

                if (session.live_status == 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageSmallFlag(text = "直播中")
                }
                if (session.is_dnd == 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageSmallFlag(text = "免打扰")
                }

                if (session.is_intercept == 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageSmallFlag(text = "已拦截")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AppText(
                text = MessagePreviewParser.parseSessionPreview(
                    content = session.last_msg?.content,
                    msgType = session.last_msg?.msg_type ?: 1
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            AppText(
                text = FormatUtils.formatPublishTime(session.last_msg?.timestamp ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )


        }
    }
    }
    if (showMenu) MessageActionDialog(
        actions = buildList {
            add(MessageAction(if (session.top_ts > 0) "移除置顶" else "置顶", onToggleTop))
            if (isPrivate) {
                if (session.unread_count > 0 || unreadDot || unreadLabel.isNotBlank()) add(MessageAction("标为已读", onMarkRead))
                add(MessageAction(if (session.is_dnd == 1) "关闭免打扰" else "开启免打扰", onToggleDnd))
                add(MessageAction("删除", onRemove))
            }
        },
        onDismiss = { showMenu = false },
    )

}

@Composable
private fun MessageSmallFlag(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = AppContentColors.accent(),
        modifier = Modifier
            .background(
                AppContentColors.accent().copy(alpha = 0.1f),
                AppShapes.container(ContainerLevel.Tag)
            )
            .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = 1.dp)
    )
}

