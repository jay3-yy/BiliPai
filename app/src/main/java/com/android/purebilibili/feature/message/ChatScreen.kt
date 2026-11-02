// 聊天详情页面
package com.android.purebilibili.feature.message

import com.android.purebilibili.core.ui.AppContentColors
import android.os.Build

import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.FeedTitleHierarchy
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.resolveAppPlayIcon
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSnackbar
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.PickGalleryVisualMedia
import com.android.purebilibili.data.model.response.EmoteInfo
import com.android.purebilibili.data.model.response.PrivateMessageItem
import com.android.purebilibili.data.repository.MessageSessionControlInfo
import com.android.purebilibili.feature.home.HomeWallpaperBackdrop
import com.android.purebilibili.feature.home.resolveHomeWallpaperBackdropAppearance
import com.android.purebilibili.feature.home.resolveHomeWallpaperUri
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.feature.common.ListLoadError
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private const val MESSAGE_LARGE_VIDEO_COVER_ASPECT_RATIO = 4f / 3f
private val CHAT_INPUT_DOCK_HEIGHT = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small
private val CHAT_INPUT_VERTICAL_PADDING = AppSpacingTokens.Small
private val CHAT_MESSAGE_LIST_BOTTOM_PADDING = CHAT_INPUT_DOCK_HEIGHT +
    CHAT_INPUT_VERTICAL_PADDING +
    CHAT_INPUT_VERTICAL_PADDING +
    AppSpacingTokens.Small

// 小横条停靠在底部时的默认占位高度（横条高度 + 外边距），用于让输入框避开它
@Composable
private fun resolveChatMiniPlayerBottomAvoidance(): Dp {
    val miniPlayerManager = com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstanceOrNull()
        ?: return AppSpacingTokens.None
    if (!miniPlayerManager.isMiniMode) return AppSpacingTokens.None
    val policy = com.android.purebilibili.feature.video.ui.overlay.resolveMiniPlayerOverlayLayoutPolicy(
        widthDp = LocalConfiguration.current.screenWidthDp
    )
    return (policy.miniPlayerHeightDp + policy.outerPaddingDp).dp + AppSpacingTokens.Small
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    talkerId: Long,
    sessionType: Int,
    userName: String,
    userFace: String = "",
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenBilibiliLink: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(talkerId, sessionType))
) {
    val accountIdentity by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val rawUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = rawUiState.takeIf { it.accountIdentity == accountIdentity }
        ?: ChatUiState(accountIdentity = accountIdentity)
    LaunchedEffect(viewModel, accountIdentity, userName) {
        viewModel.loadChatUser(userName)
    }
    var inputValue by rememberSaveable(talkerId, sessionType, accountIdentity, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val inputText = inputValue.text
    LaunchedEffect(viewModel, uiState.sentText) {
        val sentText = uiState.sentText ?: return@LaunchedEffect
        if (inputText == sentText) inputValue = TextFieldValue("")
        viewModel.consumeSentText(sentText)
    }
    var showSettings by rememberSaveable(talkerId, sessionType, accountIdentity) { mutableStateOf(false) }
    var showEmotes by rememberSaveable(talkerId, sessionType, accountIdentity) { mutableStateOf(false) }
    var previewImage by remember(talkerId, sessionType, accountIdentity) { mutableStateOf<String?>(null) }
    var actionMessage by remember(talkerId, sessionType, accountIdentity) { mutableStateOf<PrivateMessageItem?>(null) }
    var reportMessage by remember(talkerId, sessionType, accountIdentity) { mutableStateOf<PrivateMessageItem?>(null) }
    val listState = androidx.compose.runtime.key(accountIdentity) { rememberLazyListState() }
    val scrollScope = rememberCoroutineScope()
    var followLatest by rememberSaveable(talkerId, sessionType, accountIdentity) { mutableStateOf(true) }
    var handledScrollVersion by remember(viewModel, accountIdentity) { mutableStateOf(uiState.scrollToLatestVersion) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, accountIdentity, showSettings) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (showSettings) return@repeatOnLifecycle
            while (isActive) {
                viewModel.refreshMessages()
                delay(5_000L)
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollBackward }
            .collect { (scrolling, canScrollToNewer) ->
                // Data insertion must not change the user's intention to follow the latest message.
                if (scrolling || !canScrollToNewer) followLatest = !canScrollToNewer
            }
    }
    var showInterceptConfirm by remember(accountIdentity) { mutableStateOf(false) }
    var showBlockConfirm by remember(accountIdentity) { mutableStateOf(false) }
    var imagePickIdentity by remember { mutableStateOf<com.android.purebilibili.core.store.AccountSessionIdentity?>(null) }
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = PickGalleryVisualMedia()
    ) { uri ->
        if (uri != null && imagePickIdentity == com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
            viewModel.sendImageMessage(context, uri)
        }
        imagePickIdentity = null
    }

    val latestMessageKey = uiState.messages.lastOrNull()?.let(::chatMessageKey)
    LaunchedEffect(latestMessageKey, uiState.scrollToLatestVersion) {
        val sentMessage = uiState.scrollToLatestVersion > handledScrollVersion
        if (uiState.messages.isNotEmpty() && (followLatest || sentMessage)) {
            followLatest = true
            listState.scrollToItem(0)
        }
        handledScrollVersion = uiState.scrollToLatestVersion
    }

    LaunchedEffect(uiState.reportSuccessVersion) { if (uiState.reportSuccessVersion > 0) reportMessage = null }
    if (showSettings && sessionType == 1) {
        ChatSettingsScreen(talkerId, userName, uiState, viewModel, onBack = { showSettings = false }, onOpenLink = onOpenBilibiliLink)
        return
    }
    previewImage?.let { url ->
        com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog(images = listOf(url), initialIndex = 0, publicContent = false, onDismiss = { previewImage = null })
    }
    actionMessage?.let { message ->
        val own = message.sender_uid == viewModel.currentUserMid
        MessageActionDialog(actions = listOf(MessageAction(if (own) "撤回" else "举报") {
            if (own) viewModel.withdrawMessage(message) else { viewModel.clearReportError(); reportMessage = message }
        }), onDismiss = { actionMessage = null })
    }
    reportMessage?.let { message ->
        MessageReportDialog(busy = uiState.isReporting, error = uiState.reportError, onDismiss = { reportMessage = null }, onSubmit = { reason, description -> viewModel.report(message.msg_key, reason, description, accusedUid = message.sender_uid) })
    }
    if (showEmotes) {
        androidx.activity.compose.BackHandler { showEmotes = false }
    }
    val chatThemeConfig = LocalAppThemeConfig.current
    val lowBlurBudget = isLowBlurBudgetForced()
    val chatChromeSource = if (
        chatThemeConfig.progressiveTopBlurEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !lowBlurBudget
    ) {
        rememberChromeBackdropSource()
    } else {
        null
    }
    val chatHazeState = if (
        chatThemeConfig.headerBlurEnabled &&
            !chatThemeConfig.progressiveTopBlurEnabled &&
            !lowBlurBudget &&
            shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT)
    ) {
        rememberRecoverableHazeState(initialBlurEnabled = true)
    } else {
        null
    }

    val chatWallpaperBackdrop = if (chatThemeConfig.liquidGlassEnabled) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val chatContentBackdrop = if (chatThemeConfig.liquidGlassEnabled) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val chatInputBackdrop = if (chatWallpaperBackdrop != null && chatContentBackdrop != null) {
        rememberCombinedBackdrop(chatWallpaperBackdrop, chatContentBackdrop)
    } else {
        null
    }
    val miniPlayerBottomAvoidance = resolveChatMiniPlayerBottomAvoidance()
    val emotePanelHeight = (LocalConfiguration.current.screenHeightDp * 0.36f).dp.coerceIn(140.dp, 260.dp)
    val emoteBottomAvoidance = if (showEmotes) emotePanelHeight else 0.dp

    ChatWallpaperHost(
        wallpaperBackdrop = chatWallpaperBackdrop,
    ) { wallpaperContent ->
    AppScaffold(
        containerColor = Color.Transparent,
        topBarSurfaceColor = AppSurfaceTokens.chromeBackground(),
        preferProgressiveTopBlur = chatThemeConfig.progressiveTopBlurEnabled,
        chromeBackdropSource = chatChromeSource,
        externalHazeState = chatHazeState,
        blurContentReady = !uiState.isLoading,
        topBar = {
            AppTopBar(
                title = if (sessionType == 1) uiState.userInfo?.name?.takeIf { it.isNotBlank() } ?: userName else userName,
                modifier = if (sessionType == 1) Modifier.clickable { onOpenBilibiliLink("bilibili://space/$talkerId") } else Modifier,
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconButton(onClick = onBack) {
                            AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                        }
                        if (sessionType == 1) {
                            val face = uiState.userInfo?.face?.takeIf { it.isNotBlank() } ?: userFace
                            AppIconButton(onClick = { onOpenBilibiliLink("bilibili://space/$talkerId") }) {
                                if (face.isBlank()) AppIcon(Icons.Default.Person, "对方资料")
                                else AsyncImage(model = face, contentDescription = "对方头像", modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                },
                actions = {
                    if (sessionType == 1) {
                        if (chatThemeConfig.liquidGlassEnabled) {
                            val info = uiState.sessionControlInfo
                            val enabled = !uiState.isSessionControlLoading && !uiState.isSessionControlUpdating
                            AppWindowActionMenu(groups = listOf(buildList {
                                add(AppWindowAction(label = if (info.isDnd == true) "关闭免打扰" else "开启免打扰", enabled = enabled && info.isDnd != null, onClick = viewModel::toggleDnd))
                                if (info.showPushSetting) add(AppWindowAction(label = if (info.pushMuted == true) "接收推送" else "关闭推送", enabled = enabled && info.pushMuted != null, onClick = viewModel::togglePushMuted))
                                add(AppWindowAction(label = if (info.isPinned == true) "取消置顶" else "置顶聊天", enabled = enabled && info.isPinned != null, onClick = viewModel::togglePinned))
                                add(AppWindowAction(label = if (info.followStatus == 128) "移出黑名单" else "加入黑名单", enabled = enabled && info.followStatus != null, onClick = { if (info.followStatus == 128) viewModel.toggleBlocked() else showBlockConfirm = true }))
                                add(AppWindowAction(label = if (info.isIntercept == true) "移出拦截" else "移入拦截", enabled = enabled && info.isIntercept != null, onClick = { if (info.isIntercept == true) viewModel.toggleIntercept() else showInterceptConfirm = true }))
                                add(AppWindowAction(label = "聊天设置", onClick = { showSettings = true }))
                                if (info.isLimit == true) add(AppWindowAction(label = "会话受限", enabled = false))
                                if (info.reportLimit == true) add(AppWindowAction(label = "举报受限", enabled = false))
                                add(AppWindowAction(label = "刷新状态", enabled = enabled, onClick = viewModel::loadSessionControlInfo))
                            })) { AppIcon(Icons.Default.Settings, "聊天设置") }
                        } else {
                            AppIconButton(onClick = { showSettings = true }) { AppIcon(Icons.Default.Settings, "聊天设置") }
                        }
                    } else ChatSessionControlMenu(
                        sessionType = sessionType,
                        controlInfo = uiState.sessionControlInfo,
                        isUpdating = uiState.isSessionControlUpdating,
                        onToggleDnd = viewModel::toggleDnd,
                        onTogglePush = viewModel::togglePushMuted,
                        onToggleIntercept = {
                                if (uiState.sessionControlInfo.isIntercept == true) {
                                    viewModel.toggleIntercept()
                                } else {
                                    showInterceptConfirm = true
                                }
                        },
                        onRefresh = viewModel::loadSessionControlInfo,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        // Capture wallpaper and messages together, excluding the scaffold's top bar.
        Box(
            Modifier.fillMaxSize()
                .then(chatChromeSource?.modifier ?: Modifier)
                .then(chatHazeState?.let { Modifier.hazeSourceCompat(it) } ?: Modifier)
        ) {
            wallpaperContent()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep side/navigation-bar insets here, but let messages scroll
                    // behind top chrome. The initial top gap belongs to LazyColumn.
                    .padding(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding(),
                    )
                    .consumeWindowInsets(paddingValues)
                    // 消息列表和输入栏共同避让键盘，避免仅抬高输入栏后遮住消息；
                    // 玻璃与原生样式共用此布局，已消费的导航栏高度不重复计入。
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(chatContentBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                ) {
                    when {
                        uiState.isLoading -> {
                            com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        uiState.error != null && uiState.messages.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppText(uiState.error ?: "加载失败")
                                Spacer(modifier = Modifier.height(8.dp))
                                AppButton(onClick = { viewModel.loadMessages() }) {
                                    AppText("重试")
                                }
                            }
                        }
                        uiState.refreshError != null && uiState.messages.isEmpty() -> {
                            ListLoadError(
                                message = uiState.refreshError ?: "刷新消息失败",
                                onRetry = { viewModel.loadMessages() },
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            )
                        }
                        uiState.messages.isEmpty() -> {
                            AppText(
                                text = "暂无消息",
                                modifier = Modifier.align(Alignment.Center),
                                color = AppContentColors.secondaryText()
                            )
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                reverseLayout = true,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = paddingValues.calculateTopPadding() + 8.dp,
                                    end = 16.dp,
                                    bottom = CHAT_MESSAGE_LIST_BOTTOM_PADDING +
                                        miniPlayerBottomAvoidance + emoteBottomAvoidance,
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    if (uiState.messages.size == 1 && uiState.messages[0].msg_type == 18) Alignment.Top else Alignment.Bottom,
                                )
                            ) {
                                uiState.refreshError?.let { error ->
                                    item(key = "message_refresh_error") {
                                        ListLoadError(
                                            message = error,
                                            onRetry = { viewModel.loadMessages() },
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        )
                                    }
                                }
                                items(
                                    items = uiState.messages.asReversed(),
                                    key = ::chatMessageKey,
                                ) { message ->
                                    MessageBubble(
                                        message = message,
                                        isOwnMessage = message.sender_uid == viewModel.currentUserMid,
                                        emoteInfos = uiState.emoteInfos,
                                        videoPreviews = uiState.videoPreviews,
                                        canWithdraw = message.sender_uid == viewModel.currentUserMid && message.msg_status != 1,
                                        onLongPress = {
                                            actionMessage = message
                                        },
                                        onImageClick = { previewImage = it },
                                        onVideoClick = { bvid ->
                                            onNavigateToVideo(bvid)
                                        },
                                        onLinkClick = { link ->
                                            onOpenBilibiliLink(link)
                                        }
                                    )
                                }
                                // Reverse layout keeps older pages above the current reading position.
                                if (uiState.hasMore) {
                                    item(key = "load_older_messages") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.isLoadingMore) {
                                                AdaptiveLoadingIndicator(
                                                    size = 24.dp
                                                )
                                            } else if (uiState.loadMoreError != null) {
                                                ListLoadError(
                                                    message = uiState.loadMoreError ?: "加载历史消息失败",
                                                    onRetry = viewModel::loadMoreMessages,
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                )
                                            } else {
                                                AppTextButton(onClick = { viewModel.loadMoreMessages() }) {
                                                    AppText("加载更多")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!followLatest && uiState.messages.isNotEmpty()) {
                        AppButton(
                            onClick = {
                                followLatest = true
                                scrollScope.launch { listState.scrollToItem(0) }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(
                                end = 16.dp,
                                bottom = CHAT_MESSAGE_LIST_BOTTOM_PADDING + miniPlayerBottomAvoidance + emoteBottomAvoidance,
                            ),
                        ) {
                            AppText("回到最新消息")
                        }
                    }

                    // 发送错误提示
                    (uiState.sendError ?: uiState.notice)?.let { error ->
                        AppSnackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            action = {
                                AppTextButton(onClick = { viewModel.clearSendError() }) {
                                    AppText("知道了")
                                }
                            }
                        ) {
                            AppText(error)
                        }
                    }
                }

                ChatInputBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = miniPlayerBottomAvoidance),
                    text = inputValue,
                    onTextChange = { inputValue = it },
                    showEmotes = showEmotes,
                    emotePanelHeight = emotePanelHeight,
                    onInputFocus = { showEmotes = false },
                    emotePackages = uiState.emotePackages,
                    isEmoteLoading = uiState.isEmoteLoading,
                    emoteError = uiState.emoteError,
                    onToggleEmotes = {
                        showEmotes = !showEmotes
                        if (showEmotes) { keyboard?.hide(); focusManager.clearFocus(); viewModel.loadEmotes() }
                    },
                    onRetryEmotes = viewModel::loadEmotes,
                    onChooseEmote = { emote -> inputValue = insertChatEmote(inputValue, emote) },
                    onSend = {
                        if (inputText.isNotBlank() && !uiState.isSending && !uiState.isUploadingImage) {
                            viewModel.sendMessage(inputText)
                        }
                    },
                    onPickImage = {
                        imagePickIdentity = accountIdentity
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    isSending = uiState.isSending,
                    isUploadingImage = uiState.isUploadingImage,
                    backdrop = chatInputBackdrop,
                )
            }
        }
    }
    }

    if (showBlockConfirm) {
        MessageConfirmDialog("确认拉黑该用户", "加入黑名单后，将自动解除关注和合集订阅关系，并限制对方与你互动或查看你的空间。", onDismiss = { showBlockConfirm = false }) {
            showBlockConfirm = false
            viewModel.toggleBlocked()
        }
    }
    if (showInterceptConfirm) {
        AppAlertDialog(
            onDismissRequest = { showInterceptConfirm = false },
            title = { AppText("移入拦截") },
            text = { AppText("后续这类会话会进入拦截分类，仍可在拦截列表中查看和恢复。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        viewModel.toggleIntercept()
                        showInterceptConfirm = false
                    }
                ) {
                    AppText("移入")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showInterceptConfirm = false }) {
                    AppText("取消")
                }
            }
        )
    }
}

/**
 * Chat is a retained navigation entry, so it needs its own opaque visual root. Otherwise a
 * transparent chat scaffold composites with the inbox entry underneath instead of with the
 * same wallpaper that HomeScreen uses.
 */
@Composable
private fun ChatWallpaperHost(
    wallpaperBackdrop: LayerBackdrop?,
    content: @Composable (@Composable () -> Unit) -> Unit,
) {
    val wallpaperAllowed = LocalGlobalWallpaperBackdropVisible.current
    val context = LocalContext.current
    val configuredHomeWallpaperUri = com.android.purebilibili.core.ui.wallpaper.LocalHomeWallpaperSelection.current.uri
    val splashWallpaperUri by SettingsManager.getSplashWallpaperUri(context)
        .collectAsStateWithLifecycle(initialValue = "")
    val wallpaperEffectMode by SettingsManager.getHomeWallpaperEffectMode(context)
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectMode.SOFT_BLUR)
    val wallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
        resolveHomeWallpaperUri(
            homeWallpaperUri = configuredHomeWallpaperUri,
            splashWallpaperUri = splashWallpaperUri,
        )
    }
    val wallpaperPalette = LocalWallpaperPalette.current

    val baseColor = AppSurfaceTokens.chromeBackground()
    val isLightBackground = remember(baseColor) { baseColor.luminance() > 0.5f }
    val isDataSaverActive = remember(context) {
        SettingsManager.isDataSaverActive(context)
    }
    val wallpaperAppearance = remember(
        wallpaperUri,
        wallpaperEffectMode,
        wallpaperAllowed,
        isLightBackground,
        isDataSaverActive,
    ) {
        resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = wallpaperAllowed && wallpaperUri.isNotBlank(),
            effectMode = wallpaperEffectMode,
            isDarkTheme = !isLightBackground,
            isDataSaverActive = isDataSaverActive,
        )
    }
    val wallpaperVisible = wallpaperAppearance.visible && wallpaperUri.isNotBlank()

    // Keep an opaque retained-navigation root. Its wallpaper is drawn inside the
    // scaffold body so the external chrome source includes message content as well.
    Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
        CompositionLocalProvider(
            LocalGlobalWallpaperBackdropVisible provides wallpaperVisible,
            LocalWallpaperPalette provides wallpaperPalette,
        ) {
            content {
                Box(
                    Modifier.fillMaxSize()
                        .then(wallpaperBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                ) {
                    HomeWallpaperBackdrop(
                        wallpaperUri = wallpaperUri,
                        appearance = wallpaperAppearance,
                        baseColor = baseColor,
                        isDataSaverActive = isDataSaverActive,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSessionControlMenu(
    sessionType: Int,
    controlInfo: MessageSessionControlInfo,
    isUpdating: Boolean,
    onToggleDnd: () -> Unit,
    onTogglePush: () -> Unit,
    onToggleIntercept: () -> Unit,
    onRefresh: () -> Unit
) {
    val limitLabel = when {
        controlInfo.isLimit == true && controlInfo.reportLimit == true -> "会话受限，举报也受限"
        controlInfo.isLimit == true -> "会话受限"
        controlInfo.reportLimit == true -> "举报受限"
        else -> null
    }
    AppWindowActionMenu(
        groups = listOf(
            buildList {
                add(
                    AppWindowAction(
                        label = if (controlInfo.isDnd == true) "关闭免打扰" else "开启免打扰",
                        enabled = !isUpdating,
                        onClick = onToggleDnd,
                    )
                )
                if (sessionType == 1 && controlInfo.showPushSetting) {
                    add(
                        AppWindowAction(
                            label = if (controlInfo.pushMuted == true) "接收推送" else "关闭推送",
                            enabled = !isUpdating,
                            onClick = onTogglePush,
                        )
                    )
                }
                if (sessionType == 1) {
                    add(
                        AppWindowAction(
                            label = if (controlInfo.isIntercept == true) "移出拦截" else "移入拦截",
                            enabled = !isUpdating,
                            onClick = onToggleIntercept,
                        )
                    )
                }
                limitLabel?.let { add(AppWindowAction(label = it, enabled = false)) }
                add(
                    AppWindowAction(
                        label = "刷新状态",
                        enabled = !isUpdating,
                        onClick = onRefresh,
                    )
                )
            }
        ),
    ) {
        AppIcon(Icons.Default.MoreVert, contentDescription = "会话设置")
    }
}

@Composable
fun ChatInputBar(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    isSending: Boolean,
    isUploadingImage: Boolean,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    showEmotes: Boolean = false,
    emotePanelHeight: Dp = 260.dp,
    onInputFocus: () -> Unit = {},
    emotePackages: List<com.android.purebilibili.data.model.response.EmotePackage> = emptyList(),
    isEmoteLoading: Boolean = false,
    emoteError: String? = null,
    onToggleEmotes: () -> Unit = {},
    onRetryEmotes: () -> Unit = {},
    onChooseEmote: (String) -> Unit = {},
) {
    val inputFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val showSendAction = text.text.isNotBlank()
    val isBusy = isSending || isUploadingImage

    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val dockShape = resolveSharedBottomBarCapsuleShape()
    // All materials use compact input geometry; multiline messages can grow on demand.
    val visibleDockHeight = 40.dp
    val shellLensIntensity = resolveFloatingDockGeometryScale(visibleDockHeight.value)
    val panelColor = com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(
        AppSurfaceTokens.surface()
    )
    val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
    val keyboardActions = KeyboardActions(onSend = { if (!isBusy && text.text.isNotBlank()) onSend() })

    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = CHAT_INPUT_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIconButton(onClick = {
            onToggleEmotes()
            if (showEmotes) { inputFocus.requestFocus(); keyboard?.show() }
        }, enabled = !isBusy) {
            AppIcon(if (showEmotes) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions, if (showEmotes) "键盘" else "表情")
        }
        BottomBarMatchedReusableLiquidDock(
            shape = dockShape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 112.dp),
            backdrop = backdrop,
            reuseEnabled = liquidGlassEnabled,
            drawShellLens = true,
            shellLensIntensity = shellLensIntensity,
        ) { liquidChromeActive ->
            val fieldColor = if (liquidChromeActive) Color.Transparent else panelColor
            val fieldTextColor = AppContentColors.primaryText()
            val placeholderColor = if (liquidChromeActive) {
                fieldTextColor.copy(alpha = 0.82f)
            } else {
                AppContentColors.secondaryText()
            }
            if (liquidChromeActive) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    readOnly = isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 112.dp)
                        .focusRequester(inputFocus)
                        .onFocusChanged { if (it.isFocused) onInputFocus() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 4,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = fieldTextColor),
                    cursorBrush = SolidColor(AppContentColors.accent()),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.text.isEmpty()) {
                                AppText(
                                    "发个消息聊聊呗~",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = placeholderColor,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                com.android.purebilibili.core.ui.components.AppCompactInputField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp).onFocusChanged { if (it.hasFocus) onInputFocus() },
                    focusRequester = inputFocus,
                    placeholder = "发个消息聊聊呗~",
                    containerColor = panelColor,
                    readOnly = isBusy,
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
        }

        BottomBarMatchedReusableLiquidDock(
            shape = CircleShape,
            modifier = Modifier
                .size(visibleDockHeight)
                .then(
                    if (!liquidGlassEnabled) {
                        Modifier
                            .clip(CircleShape)
                            .background(panelColor)
                    } else {
                        Modifier
                    }
                ),
            backdrop = backdrop,
            reuseEnabled = liquidGlassEnabled,
            drawShellLens = true,
            shellLensIntensity = shellLensIntensity,
        ) {
            AppIconButton(
                onClick = {
                    if (showSendAction) {
                        onSend()
                    } else {
                        onPickImage()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                enabled = !isBusy,
            ) {
                if (isBusy) {
                    com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                        size = 24.dp,
                        strokeWidth = 2.dp,
                    )
                } else {
                    AppIcon(
                        imageVector = if (showSendAction) {
                            Icons.AutoMirrored.Filled.Send
                        } else {
                            Icons.Outlined.AddPhotoAlternate
                        },
                        contentDescription = if (showSendAction) "发送" else "图片",
                        tint = if (showSendAction) {
                            AppContentColors.accent()
                        } else {
                            AppContentColors.secondaryText()
                        },
                    )
                }
            }
        }
    }
    if (showEmotes) ChatEmotePanel(panelHeight = emotePanelHeight, packages = emotePackages, loading = isEmoteLoading, error = emoteError, onRetry = onRetryEmotes, onChoose = onChooseEmote, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MessageBubble(
    message: PrivateMessageItem,
    isOwnMessage: Boolean,
    emoteInfos: List<EmoteInfo> = emptyList(),
    videoPreviews: Map<String, VideoPreviewInfo> = emptyMap(),
    canWithdraw: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
) {
    val bubbleShape = AppShapes.container(ContainerLevel.Card)
    val fallbackContainerColor = resolveMessageBubbleFallbackContainerColor(
        isOwnMessage = isOwnMessage,
        primary = AppContentColors.accent(),
        surfaceVariant = MaterialTheme.colorScheme.surfaceVariant,
    )
    val fallbackContentColor = resolveMessageBubbleFallbackContentColor(
        isOwnMessage = isOwnMessage,
        onPrimary = AppContentColors.onAccent(),
        onSurfaceVariant = AppContentColors.secondaryText(),
    )
    val textColor = fallbackContentColor

    // BV号正则匹配
    val bvPattern = remember { Regex("BV[a-zA-Z0-9]{10}") }

    // 从消息内容中提取BV号
    val detectedBvids = remember(message.content) {
        if (message.msg_type == 1) {
            val content = parseTextContent(message.content)
            bvPattern.findAll(content).map { it.value }.toList()
        } else {
            emptyList()
        }
    }
    val parsedCard = remember(message.content, message.msg_type) {
        MessagePreviewParser.parseMessageCard(message.content, message.msg_type)
    }
    val linkedVideoPreviews = remember(detectedBvids, videoPreviews) {
        detectedBvids.distinct().mapNotNull { bvid ->
            videoPreviews[bvid]?.let { preview -> bvid to preview }
        }
    }
    val shouldUseLargeVideoLinkCard = message.msg_type == 1 && linkedVideoPreviews.isNotEmpty()
    val isLargeVideoMessage = message.msg_status != 1 && parsedCard?.kind == MessageCardKind.Video

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.msg_type in setOf(10, 11, 13, 16, 18)) Alignment.CenterHorizontally else if (isOwnMessage) Alignment.End else Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        AppText(
            text = formatMessageTime(message.timestamp),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center,
        )

        if (message.msg_type == 18 && message.msg_status != 1) {
            Spacer(modifier = Modifier.height(18.dp))
            AppText(
                text = parseChatTip(message.content).ifBlank { "[会话提示]" },
                color = AppContentColors.secondaryText(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        if (isLargeVideoMessage && parsedCard != null) {
            MessageLargeVideoCard(
                preview = parsedCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            when {
                                parsedCard.bvid.isNotBlank() -> onVideoClick?.invoke(parsedCard.bvid)
                                parsedCard.targetUrl.isNotBlank() -> onLinkClick?.invoke(parsedCard.targetUrl)
                            }
                        },
                        onLongClick = if (message.msg_status != 1) onLongPress else null,
                    )
                    .messageSecondaryClick { if (message.msg_status != 1) onLongPress?.invoke() },
            )
        } else if (!shouldUseLargeVideoLinkCard) {
            // 消息气泡
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (message.msg_type in setOf(10, 13, 16, 18)) Alignment.TopCenter else if (isOwnMessage) Alignment.TopEnd else Alignment.TopStart,
            ) {
                AppSurface(
                    modifier = Modifier
                        .widthIn(max = resolveMessageBubbleMaxWidth(maxWidth))
                        .then(
                            if (message.msg_status != 1 && onLongPress != null) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = onLongPress
                                ).messageSecondaryClick { onLongPress() }
                            } else {
                                Modifier
                            }
                        ),
                    shape = bubbleShape,
                    color = fallbackContainerColor,
                    contentColor = fallbackContentColor,
                ) {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = AppSpacingTokens.Medium,
                            vertical = AppSpacingTokens.Small,
                        )
                    ) {
                        when {
                        message.msg_status == 1 -> {
                            // 已撤回消息
                            AppText(
                                text = "[消息已撤回]",
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        message.msg_type == 1 -> {
                            // 文字消息 - 支持表情渲染
                            val content = parseTextContent(message.content)
                            androidx.compose.foundation.text.selection.SelectionContainer {
                            EmoteText(
                                text = content,
                                emoteInfos = emoteInfos,
                                color = textColor,
                                linkColor = AppContentColors.accent(),
                                style = MaterialTheme.typography.bodyLarge,
                                onLinkClick = onLinkClick
                            )
                            }
                        }
                        message.msg_type == 2 -> {
                            // 图片消息
                            val imageUrl = parseImageUrl(message.content)
                            if (imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "图片",
                                    modifier = Modifier
                                        .widthIn(max = 200.dp)
                                        .heightIn(max = 300.dp)
                                        .clip(AppShapes.container(ContainerLevel.Chip))
                                        .combinedClickable(onClick = { onImageClick?.invoke(imageUrl) }, onLongClick = onLongPress)
                                        .messageSecondaryClick { onLongPress?.invoke() },
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AppText(
                                    text = "[图片]",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        message.msg_type == 6 -> {
                            // 表情消息 (大表情)
                            val emoteUrl = parseEmoteUrl(message.content)
                            if (emoteUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = emoteUrl,
                                    contentDescription = "表情",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(AppShapes.container(ContainerLevel.Chip))
                                        .combinedClickable(onClick = { onImageClick?.invoke(emoteUrl) }, onLongClick = onLongPress)
                                        .messageSecondaryClick { onLongPress?.invoke() },
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AppText(
                                    text = "[表情]",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        message.msg_type == 10 -> {
                            ChatNoticeMessage(message.content, onOpenLink = { onLinkClick?.invoke(it) })
                        }
                        message.msg_type == 16 -> {
                            ChatRecommendationMessage(message.content, onOpenLink = { onLinkClick?.invoke(it) })
                        }
                        message.msg_type == 18 -> {
                            AppText(parseChatTip(message.content).ifBlank { "[会话提示]" }, color = textColor, style = MaterialTheme.typography.bodySmall)
                        }
                        message.msg_type == 11 -> {
                            parsedCard?.let { card ->
                                MessageCardPreviewCard(
                                    preview = card,
                                    onLongPress = onLongPress,
                                    onClick = {
                                        when {
                                            card.bvid.isNotBlank() -> onVideoClick?.invoke(card.bvid)
                                            card.targetUrl.isNotBlank() -> onLinkClick?.invoke(card.targetUrl)
                                        }
                                    }
                                )
                            } ?: AppText(
                                text = "[视频]",
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        message.msg_type in setOf(7, 12, 13, 14) -> {
                            parsedCard?.let { card ->
                                MessageCardPreviewCard(
                                    preview = card,
                                    onLongPress = onLongPress,
                                    onClick = {
                                        when {
                                            card.bvid.isNotBlank() -> onVideoClick?.invoke(card.bvid)
                                            card.targetUrl.isNotBlank() -> onLinkClick?.invoke(card.targetUrl)
                                        }
                                    }
                                )
                            } ?: AppText(
                                text = "[${getMessageTypeName(message.msg_type)}]",
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        else -> {
                            AppText(
                                text = "[${getMessageTypeName(message.msg_type)}]",
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        }
                    }
                }
            }
        }

        if (message.msg_source in 8..11 && message.msg_status != 1) {
            AppText("此条消息为自动回复", color = AppContentColors.secondaryText(), style = MaterialTheme.typography.labelSmall)
        }

        // 视频链接预览卡片
        linkedVideoPreviews.forEach { (bvid, preview) ->
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            VideoLinkPreviewCard(
                preview = preview,
                onLongPress = onLongPress,
                onClick = { onVideoClick?.invoke(bvid) }
            )
        }


    }
}

/**
 * Full-width video message card matching the large stacked presentation used by the chat design.
 * It keeps the cover ratio, duration treatment, typography and theme-aware surface from the feed.
 */
@Composable
private fun MessageLargeVideoCard(
    preview: MessageCardPreview,
    modifier: Modifier = Modifier,
) {
    val cardShape = AppShapes.container(ContainerLevel.ProminentCard)
    val cardCorner = AppShapes.containerCornerDp(ContainerLevel.ProminentCard)
    val contentTypography = feedContentTypography(FeedTitleHierarchy.Standard)
    val glassContentColors = rememberMessageGlassContentColors(
        defaultOnSurface = AppContentColors.primaryText(),
        defaultOnSurfaceVariant = AppContentColors.secondaryText(),
    )

    Column(
        modifier = modifier
            .clip(cardShape)
            .messageGlassContainer(
                defaultContainerColor = AppSurfaceTokens.cardContainer(),
                shape = cardShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MESSAGE_LARGE_VIDEO_COVER_ASPECT_RATIO)
                .clip(AppShapes.topRounded(cardCorner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (preview.cover.isNotBlank()) {
                AsyncImage(
                    model = preview.cover,
                    contentDescription = preview.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            if (preview.duration > 0) {
                VideoCardCoverDurationText(
                    text = formatDuration(preview.duration),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(AppSpacingTokens.Small),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppSpacingTokens.Small)
                    .size(AppSpacingTokens.TripleExtraLarge),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = resolveAppPlayIcon(),
                    contentDescription = "播放",
                    modifier = Modifier.fillMaxSize(),
                    tint = MediaContrastPalette.Foreground,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.bottomRounded(cardCorner))
                .background(AppSurfaceTokens.cardContainer())
                .padding(
                    horizontal = AppSpacingTokens.Medium,
                    vertical = AppSpacingTokens.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
        ) {
            AppText(
                text = preview.title.ifBlank { preview.kind.label },
                style = contentTypography.title,
                color = glassContentColors.titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            preview.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                AppText(
                    text = subtitle,
                    style = contentTypography.author,
                    color = glassContentColors.subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Shared horizontal video-card presentation for private-message previews.
 * The frame and typography intentionally follow the related-video cards.
 */
@Composable
private fun MessageHorizontalVideoCard(
    coverUrl: String,
    title: String,
    duration: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlineText: String? = null,
    supportingText: String? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val contentTypography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    val glassContentColors = rememberMessageGlassContentColors(
        defaultOnSurface = AppContentColors.primaryText(),
        defaultOnSurfaceVariant = AppContentColors.secondaryText(),
    )

    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .clip(cardShape)
            .messageGlassContainer(
                defaultContainerColor = AppSurfaceTokens.cardContainer(),
                shape = cardShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .messageSecondaryClick { onLongPress?.invoke() },
    ) {
        HorizontalVideoCardFrame(
            coverContent = {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            },
            coverOverlayContent = {
                if (duration > 0) {
                    VideoCardCoverDurationText(
                        text = formatDuration(duration),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    )
                }
            },
            infoContent = {
                overlineText?.takeIf { it.isNotBlank() }?.let {
                    AppText(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppText(
                    text = title,
                    style = contentTypography.title,
                    maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                    overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
                    color = glassContentColors.titleColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                supportingText?.takeIf { it.isNotBlank() }?.let {
                    AppText(
                        text = it,
                        style = contentTypography.author,
                        color = glassContentColors.subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}

@Composable
fun VideoLinkPreviewCard(
    preview: VideoPreviewInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val supportingText = buildString {
        if (preview.ownerName.isNotBlank()) append(preview.ownerName)
        if (preview.viewCount > 0) {
            if (isNotEmpty()) append(" · ")
            append(FormatUtils.formatStat(preview.viewCount))
            append("播放")
        }
        if (preview.danmakuCount > 0) {
            if (isNotEmpty()) append(" · ")
            append(FormatUtils.formatStat(preview.danmakuCount))
            append("弹幕")
        }
    }
    MessageLargeVideoCard(
        preview = MessageCardPreview(
            kind = MessageCardKind.Video,
            title = preview.title,
            subtitle = supportingText,
            cover = preview.cover,
            bvid = preview.bvid,
            duration = preview.duration,
        ),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .messageSecondaryClick { onLongPress?.invoke() },
    )
}

@Composable
fun MessageCardPreviewCard(
    preview: MessageCardPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    MessageHorizontalVideoCard(
        coverUrl = preview.cover,
        title = preview.title.ifBlank { preview.kind.label },
        duration = preview.duration,
        overlineText = preview.kind.label,
        supportingText = preview.subtitle,
        onClick = onClick,
        onLongPress = onLongPress,
        modifier = modifier,
    )
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Long): String {
    return FormatUtils.formatDuration(seconds.coerceAtLeast(0L).toInt())
}

/**
 * 支持表情和链接渲染的富文本组件
 */
@Composable
fun RichMessageText(
    text: String,
    emoteInfos: List<EmoteInfo>,
    color: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    linkColor: Color = AppContentColors.accent(),  // 使用主题色
    onLinkClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    // 构建表情映射 (text -> EmoteInfo)
    val emoteMap = remember(emoteInfos) {
        emoteInfos.associateBy { it.text }
    }

    // 匹配模式
    val emotePattern = remember { "\\[([^\\[\\]]+)\\]".toRegex() }
    val urlPattern = remember {
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()
    }

    // 扫描所有特殊内容 (表情和URL)
    data class ContentMatch(val range: IntRange, val type: String, val value: String)

    val allMatches = remember(text) {
        val matches = mutableListOf<ContentMatch>()

        // 收集表情
        emotePattern.findAll(text).forEach { match ->
            matches.add(ContentMatch(match.range, "emote", match.value))
        }

        // 收集 URL
        urlPattern.findAll(text).forEach { match ->
            matches.add(ContentMatch(match.range, "url", match.value))
        }

        // 按位置排序
        matches.sortedBy { it.range.first }
    }

    // 如果没有特殊内容，直接显示文本
    if (allMatches.isEmpty()) {
        AppText(text = text, color = color, style = style, fontSize = fontSize)
        return
    }

    // 构建 AnnotatedString
    val annotatedString = buildAnnotatedString {
        var lastEnd = 0

        allMatches.forEach { match ->
            // 添加前面的普通文本
            if (match.range.first > lastEnd) {
                append(text.substring(lastEnd, match.range.first))
            }

            when (match.type) {
                "emote" -> {
                    val emote = emoteMap[match.value]
                    if (emote != null && emote.url.isNotEmpty()) {
                        appendInlineContent(match.value, match.value)
                    } else {
                        append(match.value)
                    }
                }
                "url" -> {
                    // 添加链接样式和注解
                    pushStringAnnotation(tag = "URL", annotation = match.value)
                    withStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append(match.value)
                    }
                    pop()
                }
            }

            lastEnd = match.range.last + 1
        }

        // 添加剩余文本
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }

    // 构建 InlineContent 映射 (表情图片)
    val inlineContentMap = remember(emoteInfos) {
        emoteInfos.filter { it.url.isNotEmpty() }.associate { emote ->
            emote.text to InlineTextContent(
                placeholder = Placeholder(
                    width = 1.4.em,
                    height = 1.4.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = emote.url,
                    contentDescription = emote.text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // 用于检测点击位置
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    AppText(
        text = annotatedString,
        color = color,
        style = style,
        fontSize = fontSize,
        inlineContent = inlineContentMap,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                layoutResult?.let { layout ->
                    val position = layout.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations(
                        tag = "URL",
                        start = position,
                        end = position
                    ).firstOrNull()?.let { annotation ->
                        val url = annotation.item
                        if (onLinkClick != null) {
                            onLinkClick(url)
                        } else {
                            // 默认: 用浏览器打开
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("RichMessageText", "Failed to open URL: $url", e)
                            }
                        }
                    }
                }
            }
        }
    )
}

// 保留旧函数名的兼容性别名
@Composable
fun EmoteText(
    text: String,
    emoteInfos: List<EmoteInfo>,
    color: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    linkColor: Color = AppContentColors.accent(),
    onLinkClick: ((String) -> Unit)? = null
) {
    RichMessageText(
        text = text,
        emoteInfos = emoteInfos,
        color = color,
        style = style,
        fontSize = fontSize,
        linkColor = linkColor,
        onLinkClick = onLinkClick
    )
}


/**
 * 解析文字消息内容
 */
private fun parseTextContent(content: String): String {
    if (content.isBlank()) return ""
    if (!content.trim().startsWith("{")) return content

    return try {
        val json = Json.parseToJsonElement(content)
        json.jsonObject["content"]?.jsonPrimitive?.content ?: content
    } catch (e: Exception) {
        content
    }
}

/**
 * 解析图片URL
 */
private fun parseImageUrl(content: String): String {
    return try {
        val json = Json.parseToJsonElement(content)
        json.jsonObject["url"]?.jsonPrimitive?.content ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * 解析表情URL
 */
private fun parseEmoteUrl(content: String): String {
    return try {
        val json = Json.parseToJsonElement(content)
        json.jsonObject["url"]?.jsonPrimitive?.content ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * 解析通知消息
 */
private fun parseNotificationContent(content: String): String {
    return try {
        val json = Json.parseToJsonElement(content)
        val title = json.jsonObject["title"]?.jsonPrimitive?.content ?: ""
        val text = json.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        if (title.isNotEmpty()) "$title\n$text" else text
    } catch (e: Exception) {
        "[通知]"
    }
}

/**
 * 获取消息类型名称
 */
private fun getMessageTypeName(msgType: Int): String {
    return when (msgType) {
        1 -> "文字"
        2 -> "图片"
        5 -> "撤回"
        6 -> "表情"
        7 -> "分享"
        10 -> "通知"
        11 -> "视频"
        12 -> "专栏"
        13 -> "图片卡片"
        14 -> "分享"
        else -> "消息"
    }
}

// 会话列表组合期热路径：共享 formatter，避免每行每帧新建 SimpleDateFormat。
// 仅主线程（Compose 组合）调用，不涉及 SimpleDateFormat 的线程安全问题。
private val chatTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val chatDateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 格式化消息时间：与 PiliPlus `DateFormatUtils.chatFormat` 对齐——
 * 今天 HH:mm / 昨天 HH:mm / 更早 yyyy-MM-dd HH:mm。
 */
private fun formatMessageTime(timestamp: Long): String {
    if (timestamp == 0L) return ""

    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }

    val sameDay = now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return chatTimeFormatter.format(Date(timestamp * 1000))

    val yesterday = now.apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天 ${chatTimeFormatter.format(Date(timestamp * 1000))}"

    return chatDateTimeFormatter.format(Date(timestamp * 1000))
}
