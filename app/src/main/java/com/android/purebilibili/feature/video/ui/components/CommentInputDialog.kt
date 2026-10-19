// 文件路径: feature/video/ui/components/CommentInputDialog.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appSearchElasticPress
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.appDesktopFocusableItemVisuals

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.core.util.PickMultipleGalleryVisualMedia
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import androidx.compose.animation.core.MutableTransitionState
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.File
import com.android.purebilibili.core.ui.HingeSafeInputOverlayHost
import com.android.purebilibili.core.ui.motion.resolveCommentVerticalContentRevealMotionSpec
import com.android.purebilibili.core.ui.motion.verticalContentRevealEnterTransition
import com.android.purebilibili.core.ui.motion.verticalContentRevealExitTransition
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.data.model.response.MentionSearchUser
import kotlinx.coroutines.delay
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private const val COMMENT_INPUT_FOCUS_RETRY_COUNT = 3
private const val COMMENT_INPUT_FOCUS_RETRY_DELAY_MS = 80L
private const val COMMENT_INPUT_HEADER_HEIGHT_DP = 48

private val COMMENT_INPUT_KAOMOJIS = listOf(
    "(⌒▽⌒)", "（￣▽￣）", "(=・ω・=)", "(｀・ω・´)",
    "(〜￣△￣)〜", "(･∀･)", "(°∀°)ﾉ", "(￣3￣)",
    "╮(￣▽￣)╭", "( ´_ゝ｀)", "_(:3」∠)_", "(;¬_¬)",
    "(ﾟДﾟ≡ﾟДﾟ)", "(ノ=Д=)ノ┻━┻", "Σ( ￣□￣||)", "(´；ω；`)",
    "（/TДT)/", "(^・ω・^ )", "(●￣(ｴ)￣●)", "ε=ε=(ノ≧∇≦)ノ",
    "( >﹏<。)", "( *・ω・)✄╰ひ╯", "(╬￣皿￣)凸", "⊙__⊙"
)

private fun commentEmoteTabPriority(id: Long): Int = when (id) {
    1L -> 0
    2L -> 1
    53L -> 2
    4L, -1L -> 3
    -2L -> 5
    else -> 4
}

internal data class CommentInputDialogLayoutPolicy(
    val inputBoxMinHeightDp: Int,
    val inputBoxMaxHeightDp: Int,
    val emojiPanelHeightDp: Int,
    val sheetHorizontalPaddingDp: Int,
    val toolbarToolButtonSizeDp: Int,
    val toolbarToolSpacingDp: Int,
    val sendButtonHorizontalPaddingDp: Int
)

/**
 * 评论输入弹层尺寸策略。
 *
 * 平板在 48dp 触控下限之上再抬高输入舒适区（对标 BiliPai isTablet ? 300 : 170 的加高思路），
 * 横屏仍相对竖屏略压缩，避免遮挡过多视频区域。
 */
internal fun resolveCommentInputDialogLayoutPolicy(
    isLandscape: Boolean,
    isTablet: Boolean = false
): CommentInputDialogLayoutPolicy {
    return when {
        isTablet && isLandscape -> CommentInputDialogLayoutPolicy(
            inputBoxMinHeightDp = 96,
            inputBoxMaxHeightDp = 168,
            emojiPanelHeightDp = 240,
            sheetHorizontalPaddingDp = 20,
            toolbarToolButtonSizeDp = 44,
            toolbarToolSpacingDp = 4,
            sendButtonHorizontalPaddingDp = 20
        )
        isTablet -> CommentInputDialogLayoutPolicy(
            inputBoxMinHeightDp = 120,
            inputBoxMaxHeightDp = 200,
            emojiPanelHeightDp = 320,
            sheetHorizontalPaddingDp = 20,
            toolbarToolButtonSizeDp = 44,
            toolbarToolSpacingDp = 4,
            sendButtonHorizontalPaddingDp = 18
        )
        isLandscape -> CommentInputDialogLayoutPolicy(
            inputBoxMinHeightDp = 64,
            inputBoxMaxHeightDp = 112,
            emojiPanelHeightDp = 196,
            sheetHorizontalPaddingDp = 16,
            toolbarToolButtonSizeDp = 40,
            toolbarToolSpacingDp = 2,
            sendButtonHorizontalPaddingDp = 18
        )
        else -> CommentInputDialogLayoutPolicy(
            inputBoxMinHeightDp = 84,
            inputBoxMaxHeightDp = 136,
            emojiPanelHeightDp = 280,
            sheetHorizontalPaddingDp = 16,
            toolbarToolButtonSizeDp = 40,
            toolbarToolSpacingDp = 2,
            sendButtonHorizontalPaddingDp = 16
        )
    }
}

internal fun shouldAutoShowCommentKeyboard(
    visible: Boolean,
    canInputComment: Boolean,
    showEmojiPanel: Boolean
): Boolean {
    return visible && canInputComment && !showEmojiPanel
}

internal fun resolveCommentEmojiPanelHeightDp(
    fallbackHeightDp: Int,
    keyboardHeightDp: Int,
    availableHeightDp: Int
): Int {
    return maxOf(fallbackHeightDp, keyboardHeightDp)
        .coerceIn(0, availableHeightDp.coerceAtLeast(0))
}

internal fun resolveCommentProgressInsertText(positionMs: Long): String {
    return " ${FormatUtils.formatDuration(positionMs.coerceAtLeast(0L))} "
}

internal fun commentDraftTextFieldValue(text: String): TextFieldValue {
    return TextFieldValue(
        text = text,
        selection = TextRange(text.length)
    )
}

internal fun canPublishCommentDraft(
    text: String,
    selectedImageCount: Int,
    canInputComment: Boolean,
    isSending: Boolean
): Boolean = canInputComment && !isSending &&
    (text.isNotBlank() || selectedImageCount > 0)

/**
 * 评论输入对话框
 *
 * 提供评论输入功能，支持回复指定评论
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommentInputDialog(
    visible: Boolean,
    draftKey: String,
    onDismiss: () -> Unit,
    onSend: (String, List<Uri>, Boolean) -> Unit,
    isSending: Boolean = false,
    replyToName: String? = null,
    inputHint: String = "进来唠会嗑呗~",
    canInputComment: Boolean = true,
    modifier: Modifier = Modifier,
    currentVideoPositionMsProvider: () -> Long = { 0L },
    mentionUsers: List<MentionSearchUser> = emptyList(),
    isMentionSearching: Boolean = false,
    mentionSearchError: String? = null,
    onMentionSearchQueryChange: (String) -> Unit = {},
    initialText: String = "",
    initialImageUris: List<Uri> = emptyList(),
    initialSyncToDynamic: Boolean = false,
    onDraftChange: (String, List<Uri>, Boolean) -> Unit = { _, _, _ -> },
    emotePackages: List<com.android.purebilibili.data.model.response.EmotePackage> = emptyList() // [新增] 表情包列表
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTablet = LocalWindowSizeClass.current.isTablet
    val uiSkinState = LocalUiSkinState.current
    val activeUiSkin = uiSkinState.activeSkin
    val skinEmojiImages = if (uiSkinState.enabled && activeUiSkin != null) {
        activeUiSkin.manifest.assets.emojiImages.mapNotNull { (name, path) ->
            activeUiSkin.assetFilePath(path)?.let { name to it }
        }.toMap()
    } else {
        emptyMap()
    }
    val inputEmoteUrls = remember(emotePackages) {
        buildMap {
            emotePackages.forEach { pkg ->
                pkg.emote.orEmpty().forEach { emote ->
                    if (emote.url.isNotBlank()) put(emote.text, emote.url)
                }
            }
        }
    }
    val layoutPolicy = remember(isLandscape, isTablet) {
        resolveCommentInputDialogLayoutPolicy(
            isLandscape = isLandscape,
            isTablet = isTablet
        )
    }
    // 宽窗口限宽居中；半开折叠时弹层整体由 HingeSafeInputOverlayHost 收进铰链安全区。
    val inputOverlayMaxWidthDp = remember(configuration.screenWidthDp) {
        resolveBottomInputOverlayMaxWidthDp(configuration.screenWidthDp)
    }

    // 状态
    var textFieldValue by rememberSaveable(draftKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(commentDraftTextFieldValue(initialText))
    }
    var isForwardToDynamic by rememberSaveable(draftKey) { mutableStateOf(initialSyncToDynamic) } // 转发到动态
    var showEmojiPanel by remember { mutableStateOf(false) }    // 表情面板
    var showMentionPanel by remember { mutableStateOf(false) }
    var mentionSearchText by remember { mutableStateOf("") }
    var currentTab by remember { mutableLongStateOf(1L) }
    var selectedImageUris by rememberSaveable(
        draftKey,
        stateSaver = listSaver<List<Uri>, String>(
            save = { uris -> uris.map(Uri::toString) },
            restore = { uris -> uris.map(Uri::parse) },
        )
    ) { mutableStateOf(initialImageUris) }
    var lastKeyboardHeightDp by remember(configuration.orientation) { mutableIntStateOf(0) }
    var inputWasVisible by rememberSaveable { mutableStateOf(false) }
    var restoredDraftKey by rememberSaveable { mutableStateOf<String?>(null) }
    var inputExpanded by rememberSaveable { mutableStateOf(false) }
    val text = textFieldValue.text
    val canPublish = canPublishCommentDraft(
        text = text,
        selectedImageCount = selectedImageUris.size,
        canInputComment = canInputComment,
        isSending = isSending
    )

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetMotion = rememberAppBottomSheetMotion()
    val inputVisibility = remember { MutableTransitionState(false) }
    var dismissRequested by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val inputActive = visible && !dismissRequested
    LaunchedEffect(visible, draftKey) {
        dismissRequested = false
        inputVisibility.targetState = visible
    }
    LaunchedEffect(inputActive) {
        inputVisibility.targetState = inputActive
    }
    // Keep the native window mounted until the actual sheet inside it finishes exiting.
    // External closure (including send success) follows the same path without a second callback.
    LaunchedEffect(inputVisibility.isIdle, inputVisibility.currentState, dismissRequested, draftKey) {
        if (inputVisibility.isIdle && !inputVisibility.currentState && dismissRequested) {
            latestOnDismiss()
        }
    }
    val dismissDialog: () -> Unit = {
        if (!dismissRequested) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            dismissRequested = true
            inputVisibility.targetState = false
        }
    }
    var imagePickerDraftKey by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        PickMultipleGalleryVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isNotEmpty() && imagePickerDraftKey == draftKey) {
            selectedImageUris = (selectedImageUris + uris)
                .distinct()
                .take(9)
            onDraftChange(textFieldValue.text, selectedImageUris, isForwardToDynamic)
        }
        imagePickerDraftKey = null
    }

    fun updateTextFieldValue(nextValue: TextFieldValue) {
        if (nextValue.text.length > 1000) return
        textFieldValue = nextValue
        onDraftChange(nextValue.text, selectedImageUris, isForwardToDynamic)
        if (showEmojiPanel) return
        val mentionQuery = resolveActiveCommentMentionQuery(
            text = nextValue.text,
            cursor = nextValue.selection.end
        )
        if (mentionQuery != null) {
            showMentionPanel = true
            showEmojiPanel = false
            mentionSearchText = mentionQuery.query
            onMentionSearchQueryChange(mentionQuery.query)
        } else {
            showMentionPanel = false
        }
    }

    fun insertTextAtCursor(insertText: String) {
        val cursor = textFieldValue.selection.end.coerceIn(0, textFieldValue.text.length)
        val nextText = textFieldValue.text.replaceRange(cursor, cursor, insertText)
        val nextCursor = cursor + insertText.length
        updateTextFieldValue(TextFieldValue(nextText, TextRange(nextCursor)))
    }


    // 重置状态
    LaunchedEffect(visible, draftKey) {
        if (!visible) {
            // 外部关闭（例如导航离开详情页）也要立即撤销输入焦点，避免输入光标
            // 在弹层退场期间继续显示在播放详情页上。
            if (inputWasVisible) {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                inputWasVisible = false
            }
            return@LaunchedEffect
        }

        // 同一会话恢复保留选区；换目标或重新打开时只从该会话的草稿恢复。
        if (inputWasVisible && restoredDraftKey == draftKey) return@LaunchedEffect
        inputWasVisible = true
        restoredDraftKey = draftKey
        // 关闭时保留展开高度完成退场，只有新开输入会话才恢复紧凑布局。
        inputExpanded = false
        // 正文回流不参与 effect key，避免每次输入都重置 IME 选区。
        textFieldValue = commentDraftTextFieldValue(initialText)
        isForwardToDynamic = initialSyncToDynamic
        showEmojiPanel = false
        showMentionPanel = false
        mentionSearchText = ""
        selectedImageUris = initialImageUris
    }


    if (inputVisibility.currentState || inputVisibility.targetState) {
        Dialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false, // 允许全宽
                decorFitsSystemWindows = false   // 沉浸式：内容延伸到状态栏/导航栏下
            )
        ) {
            val dialogView = LocalView.current
            SideEffect {
                // The sheet owns its transition; avoid a second platform window animation.
                ((dialogView.parent as? DialogWindowProvider) ?: (dialogView as? DialogWindowProvider))
                    ?.window?.setWindowAnimations(0)
            }
            // 写评论时需要参照下层评论；输入窗在两种主题及玻璃开关下均不模糊背景。
            // 只退出本输入窗的模糊，不修改其他弹层的默认材质或模糊行为。
            com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect(enabled = false)
            val density = LocalDensity.current
            val imeBottomPx = WindowInsets.ime.getBottom(density)
            val navigationBarsBottomPx = WindowInsets.navigationBars.getBottom(density)
            val keyboardHeightDp = with(density) {
                (imeBottomPx - navigationBarsBottomPx).coerceAtLeast(0).toDp().value.roundToInt()
            }
            LaunchedEffect(keyboardHeightDp, showEmojiPanel) {
                if (!showEmojiPanel) {
                    lastKeyboardHeightDp = maxOf(lastKeyboardHeightDp, keyboardHeightDp)
                }
            }
            val availablePanelHeightDp = configuration.screenHeightDp -
                layoutPolicy.inputBoxMaxHeightDp -
                layoutPolicy.toolbarToolButtonSizeDp -
                COMMENT_INPUT_HEADER_HEIGHT_DP - layoutPolicy.sheetHorizontalPaddingDp - 12 -
                with(density) {
                    (navigationBarsBottomPx + WindowInsets.statusBars.getTop(density))
                        .toDp().value.roundToInt()
                } -
                // 已选图片的数量提示和缩略图也需要保留空间。
                (if (selectedImageUris.isEmpty()) 0 else 112)
            val emojiPanelHeightDp = resolveCommentEmojiPanelHeightDp(
                fallbackHeightDp = layoutPolicy.emojiPanelHeightDp,
                keyboardHeightDp = maxOf(lastKeyboardHeightDp, keyboardHeightDp),
                availableHeightDp = availablePanelHeightDp,
            )
            LaunchedEffect(showEmojiPanel, inputActive, canInputComment, draftKey) {
                if (!shouldAutoShowCommentKeyboard(inputActive, canInputComment, showEmojiPanel)) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                } else {
                    repeat(COMMENT_INPUT_FOCUS_RETRY_COUNT) { index ->
                        delay(COMMENT_INPUT_FOCUS_RETRY_DELAY_MS + index * 40L)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
            }
            DisposableEffect(focusManager, keyboardController) {
                onDispose {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }
            }
            // 统一输入弹层宿主：半开折叠时弹层整体收进铰链安全区，避免输入控件跨缝；
            // 平铺窗口时点击空白处关闭、底部对齐，宽窗口由 widthIn 限宽并水平居中。
            // 表情面板替代键盘时不再避让 IME，避免双重底部空隙。
            HingeSafeInputOverlayHost(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showEmojiPanel) Modifier else Modifier.imePadding())
                    .then(if (inputExpanded) Modifier.statusBarsPadding() else Modifier),
                onDismissRequest = dismissDialog,
            ) {
                AnimatedVisibility(
                    visibleState = inputVisibility,
                    enter = sheetMotion.contentEnter,
                    exit = sheetMotion.contentExit,
                    label = "commentInputSheet",
                ) {
                    // 输入区域
                    AppSurface(
                        modifier = modifier
                            .widthIn(max = inputOverlayMaxWidthDp.dp)
                            .fillMaxWidth()
                            .then(if (inputExpanded) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()),
                        shape = AppShapes.container(ContainerLevel.Sheet),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (inputExpanded) Modifier.fillMaxHeight() else Modifier)
                                .padding(
                                    start = layoutPolicy.sheetHorizontalPaddingDp.dp,
                                    end = layoutPolicy.sheetHorizontalPaddingDp.dp,
                                    bottom = layoutPolicy.sheetHorizontalPaddingDp.dp
                                )
                                .navigationBarsPadding() // 避让底部导航栏(手势条)
                        ) {
                            // 顶部操作使用原生按钮；各材质共用留白布局，不占用正文右侧宽度。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(COMMENT_INPUT_HEADER_HEIGHT_DP.dp)
                            ) {
                                AppIconButton(
                                    onClick = { inputExpanded = !inputExpanded },
                                    enabled = inputActive,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(COMMENT_INPUT_HEADER_HEIGHT_DP.dp)
                                        .testTag("comment_composer_expand_toggle")
                                ) {
                                    AppIcon(
                                        imageVector = if (inputExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = if (inputExpanded) "收起输入区" else "展开输入区",
                                        modifier = Modifier.size(16.dp),
                                        tint = AppContentColors.secondaryText()
                                    )
                                }
                            }
                            // 紧凑态只取原生输入高度；展开态填满剩余视口，均先给头部与发布栏留空间。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = inputExpanded)
                                    .then(
                                        if (inputExpanded) Modifier else Modifier.heightIn(
                                            min = layoutPolicy.inputBoxMinHeightDp.dp,
                                            max = layoutPolicy.inputBoxMaxHeightDp.dp
                                        )
                                    )
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), AppShapes.container(ContainerLevel.Chip))
                                    .padding(12.dp)
                                    .testTag("comment_composer_editor")
                            ) {
                                // 原生编辑器也按会话隔离，旧 TextWatcher 不能回写新回复目标。
                                key(draftKey) {
                                    CommentEmoteTextField(
                                        value = textFieldValue,
                                        onValueChange = ::updateTextFieldValue,
                                        emoteUrls = inputEmoteUrls,
                                        enabled = canInputComment && !isSending,
                                        readOnly = showEmojiPanel,
                                        onBeginEditing = { showEmojiPanel = false },
                                        hint = inputHint.ifBlank { "进来唠会嗑呗~" }.let { resolvedHint ->
                                            if (replyToName != null) "回复 @$replyToName: $resolvedHint" else resolvedHint
                                        },
                                        hintColor = AppContentColors.secondaryText().copy(alpha = 0.7f),
                                        cursorColor = AppContentColors.accent(),
                                        modifier = Modifier.fillMaxSize()
                                            .focusRequester(focusRequester)
                                            .testTag("comment_composer_input"),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = AppContentColors.primaryText()
                                        ),
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showMentionPanel && canInputComment,
                                enter = verticalContentRevealEnterTransition(resolveCommentVerticalContentRevealMotionSpec()),
                                exit = verticalContentRevealExitTransition(resolveCommentVerticalContentRevealMotionSpec())
                            ) {
                                CommentMentionSearchPanel(
                                    query = mentionSearchText,
                                    onQueryChange = { query ->
                                        mentionSearchText = query
                                        onMentionSearchQueryChange(query)
                                    },
                                    users = mentionUsers,
                                    isLoading = isMentionSearching,
                                    errorMessage = mentionSearchError,
                                    onUserClick = { user ->
                                        val cursor = textFieldValue.selection.end
                                        val (nextText, nextSelection) = insertCommentMentionText(
                                            text = textFieldValue.text,
                                            cursor = cursor,
                                            mentionName = user.name
                                        )
                                        updateTextFieldValue(TextFieldValue(nextText, nextSelection))
                                        showMentionPanel = false
                                        mentionSearchText = ""
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (selectedImageUris.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppText(
                                        text = "已选 ${selectedImageUris.size}/9 张",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(selectedImageUris, key = { it.toString() }) { uri ->
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(AppShapes.container(ContainerLevel.Chip))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    shape = AppShapes.container(ContainerLevel.Chip)
                                                )
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "已选图片",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            AppSurface(
                                                shape = AppShapes.container(ContainerLevel.Pill),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .clickable {
                                                        selectedImageUris = selectedImageUris.filterNot { it == uri }
                                                        onDraftChange(
                                                            textFieldValue.text,
                                                            selectedImageUris,
                                                            isForwardToDynamic
                                                        )
                                                    }
                                            ) {
                                                AppIcon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = "移除",
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .padding(1.dp),
                                                    tint = AppContentColors.primaryText()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. 底部工具栏
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(layoutPolicy.toolbarToolSpacingDp.dp)
                                ) {
                                    // 转发到动态
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(AppShapes.container(ContainerLevel.Tag))
                                            .clickable(enabled = canInputComment && !isSending) {
                                                isForwardToDynamic = !isForwardToDynamic
                                                onDraftChange(
                                                    textFieldValue.text,
                                                    selectedImageUris,
                                                    isForwardToDynamic
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        // 模拟 RadioButton/Checkbox
                                        AppIcon(
                                            imageVector = if (isForwardToDynamic) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isForwardToDynamic) AppContentColors.accent() else AppContentColors.secondaryText(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppText(
                                            text = "转发到动态",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText(),
                                            maxLines = 1
                                        )
                                    }

                                    // 图标栏: 表情 @ 图片
                                    AppIconButton(
                                        onClick = {
                                            val opening = !showEmojiPanel
                                            if (opening) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                showMentionPanel = false
                                            }
                                            showEmojiPanel = opening
                                        },
                                        enabled = canInputComment && !isSending,
                                        modifier = Modifier.size(layoutPolicy.toolbarToolButtonSizeDp.dp)
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Filled.Face,
                                            contentDescription = "表情",
                                            tint = if (showEmojiPanel) AppContentColors.accent() else AppContentColors.secondaryText(),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    AppIconButton(
                                        onClick = {
                                            insertTextAtCursor("@")
                                            showEmojiPanel = false
                                            showMentionPanel = true
                                            mentionSearchText = ""
                                            onMentionSearchQueryChange("")
                                        },
                                        enabled = canInputComment && !isSending,
                                        modifier = Modifier.size(layoutPolicy.toolbarToolButtonSizeDp.dp)
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Outlined.AlternateEmail,
                                            contentDescription = "提及用户",
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    AppIconButton(
                                        onClick = {
                                            insertTextAtCursor(resolveCommentProgressInsertText(currentVideoPositionMsProvider()))
                                            showEmojiPanel = false
                                            showMentionPanel = false
                                        },
                                        enabled = canInputComment && !isSending,
                                        modifier = Modifier.size(layoutPolicy.toolbarToolButtonSizeDp.dp)
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = "插入当前播放进度",
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    AppIconButton(
                                        onClick = {
                                            imagePickerDraftKey = draftKey
                                            imagePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = canInputComment && !isSending,
                                        modifier = Modifier.size(layoutPolicy.toolbarToolButtonSizeDp.dp)
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = "图片",
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 发送按钮
                                AppButton(
                                    onClick = {
                                        if (canPublish) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            android.util.Log.d("CommentInputDialog", "📤 Sending comment: $text")
                                            if (inputActive) onSend(text.trim(), selectedImageUris, isForwardToDynamic)
                                        }
                                    },
                                    enabled = canPublish && inputActive,
                                    shape = AppShapes.container(ContainerLevel.Floating),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                        contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme), // 应该是粉色
                                        disabledContainerColor = AppContentColors.accent().copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = layoutPolicy.sendButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    if (isSending) {
                                        AppCircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = AppContentColors.onAccent()
                                        )
                                    } else {
                                        AppText(
                                            text = "发布",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (!canInputComment) {
                                AppText(
                                    text = "当前评论区暂不可评论",
                                    color = AppContentColors.secondaryText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            // 3. 表情面板區域
                            if (showEmojiPanel) {
                                val apiKaomojiPackage = remember(emotePackages) {
                                    emotePackages.firstOrNull { it.text == "颜文字" }
                                }
                                val selectedEmotePackage = when {
                                    currentTab == -1L -> apiKaomojiPackage
                                    currentTab == -2L && skinEmojiImages.isNotEmpty() -> null
                                    else -> emotePackages.firstOrNull { it.id == currentTab }
                                        ?: emotePackages.firstOrNull { it.id == 1L }
                                        ?: emotePackages.firstOrNull()
                                }
                                val selectedEmoteTab = selectedEmotePackage?.id
                                    ?: if (currentTab == -2L && skinEmojiImages.isNotEmpty()) -2L else -1L
                                val showingKaomojis = selectedEmoteTab == -1L ||
                                    selectedEmotePackage?.text == "颜文字"
                                val emoteTabOptions = remember(emotePackages, skinEmojiImages.isNotEmpty()) {
                                    buildList {
                                        emotePackages.forEach { pkg ->
                                            val label = when (pkg.id) {
                                                2L -> "小电视"
                                                53L -> "热词系列"
                                                else -> pkg.text
                                            }
                                            add(AppSegmentOption(pkg.id, label))
                                        }
                                        if (apiKaomojiPackage == null) {
                                            add(AppSegmentOption(-1L, "颜文字"))
                                        }
                                        if (skinEmojiImages.isNotEmpty()) {
                                            add(AppSegmentOption(-2L, "皮肤"))
                                        }
                                    }.sortedBy { commentEmoteTabPriority(it.value) }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(emojiPanelHeightDp.dp)
                                        .testTag("comment_emote_panel")
                                        .padding(top = 8.dp)
                                ) {
                                    AppThemeAdaptiveTabRow(
                                        // Glass indicator fills the measured slot; native geometry keeps its defaults.
                                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                                        centerContent = false,
                                        options = emoteTabOptions,
                                        selectedValue = selectedEmoteTab,
                                        onSelectionChange = { currentTab = it },
                                        height = 48.dp,
                                        scrollable = true,
                                    )

                                    // 内容区域
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .testTag("comment_emote_content")
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        if (showingKaomojis) {
                                            CommentKaomojiGrid(
                                                emotes = selectedEmotePackage?.let { it.emote.orEmpty() },
                                                enabled = canInputComment && !isSending,
                                                onChoose = ::insertTextAtCursor,
                                            )
                                        } else if (selectedEmoteTab == -2L && skinEmojiImages.isNotEmpty()) {
                                            val emotes = skinEmojiImages.toList()
                                            com.android.purebilibili.core.ui.components.AppLazyVerticalGrid(
                                                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(60.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                items(emotes.size, key = { emotes[it].first }) { index ->
                                                    val (emoteText, imagePath) = emotes[index]
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.clickable {
                                                            insertTextAtCursor(emoteText)
                                                        },
                                                    ) {
                                                        AsyncImage(
                                                            model = File(imagePath),
                                                            contentDescription = emoteText,
                                                            modifier = Modifier.size(50.dp),
                                                        )
                                                        AppText(
                                                            text = emoteText.removePrefix("[").removeSuffix("]"),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppContentColors.secondaryText(),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            val pkg = selectedEmotePackage
                                            if (pkg == null) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AppText(
                                                        text = "暂无表情包",
                                                        color = AppContentColors.secondaryText(),
                                                    )
                                                }
                                            } else {
                                                val emotes = pkg.emote.orEmpty()
                                                val compactEmotes = when (pkg.text) {
                                                    "小黄脸", "小电视", "tv_小电视" -> true
                                                    else -> false
                                                }
                                                val showEmoteLabels = pkg.id != 53L && !pkg.text.startsWith("热词系列")
                                                com.android.purebilibili.core.ui.components.AppLazyVerticalGrid(
                                                    columns = if (compactEmotes) {
                                                        androidx.compose.foundation.lazy.grid.GridCells.Fixed(8)
                                                    } else {
                                                        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(60.dp)
                                                    },
                                                    verticalArrangement = Arrangement.spacedBy(if (compactEmotes) 8.dp else 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(if (compactEmotes) 4.dp else 12.dp),
                                                ) {
                                                    items(emotes.size, key = { emotes[it].id }) { i ->
                                                        val emote = emotes[i]
                                                        if (compactEmotes) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .aspectRatio(1f)
                                                                    .appDesktopFocusableItemVisuals()
                                                                    .clickable(role = Role.Button) {
                                                                        insertTextAtCursor(emote.text)
                                                                    },
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                AsyncImage(
                                                                    model = emote.url,
                                                                    contentDescription = emote.text,
                                                                    modifier = Modifier.size(32.dp),
                                                                )
                                                            }
                                                        } else {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                modifier = Modifier.clickable {
                                                                    insertTextAtCursor(emote.text)
                                                                },
                                                            ) {
                                                                AsyncImage(
                                                                    model = emote.url,
                                                                    contentDescription = emote.text,
                                                                    modifier = Modifier.size(50.dp),
                                                                )
                                                                if (showEmoteLabels) {
                                                                    AppText(
                                                                        text = emote.text.removePrefix("[").removeSuffix("]"),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = AppContentColors.secondaryText(),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentKaomojiGrid(
    emotes: List<com.android.purebilibili.data.model.response.EmoteItem>?,
    enabled: Boolean,
    onChoose: (String) -> Unit,
) {
    com.android.purebilibili.core.ui.components.AppLazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(100.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("comment_kaomoji_grid"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = emotes?.size ?: COMMENT_INPUT_KAOMOJIS.size,
            key = { index -> emotes?.get(index)?.id ?: COMMENT_INPUT_KAOMOJIS[index] },
        ) { index ->
            val kaomoji = emotes?.get(index)?.text ?: COMMENT_INPUT_KAOMOJIS[index]
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .appDesktopFocusableItemVisuals()
                    .clip(AppShapes.container(ContainerLevel.Tag))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable(enabled = enabled, role = Role.Button) {
                        onChoose(kaomoji)
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = kaomoji,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CommentMentionSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    users: List<MentionSearchUser>,
    isLoading: Boolean,
    errorMessage: String?,
    onUserClick: (MentionSearchUser) -> Unit,
    modifier: Modifier = Modifier
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        shape = AppShapes.container(ContainerLevel.Chip),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            AppOutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .appSearchElasticPress(),
                singleLine = true,
                leadingIcon = {
                    AppIcon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                placeholder = { AppText("搜索好友昵称") },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = AppShapes.container(ContainerLevel.Card),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                    focusedBorderColor = AppContentColors.accent(),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        AppText(
                            text = "正在搜索好友",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }

                errorMessage != null -> {
                    AppText(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                users.isEmpty() -> {
                    AppText(
                        text = if (query.isBlank()) "输入好友昵称搜索" else "没有找到匹配的用户",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(users, key = { it.uid }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserClick(user) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = user.face,
                                    contentDescription = user.name,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(AppShapes.container(ContainerLevel.Card))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    AppText(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppContentColors.primaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    AppText(
                                        text = "${FormatUtils.formatStat(user.fans.toLong())} 粉丝",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
