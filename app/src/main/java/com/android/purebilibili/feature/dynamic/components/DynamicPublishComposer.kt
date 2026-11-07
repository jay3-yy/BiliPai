package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.android.purebilibili.feature.video.ui.components.CommentEmoteTextField
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppContentColors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppDeleteIcon
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.util.PickMultipleGalleryVisualMedia
import com.android.purebilibili.data.model.response.DynamicCreatedReserve
import com.android.purebilibili.data.model.response.DynamicCreatedVote
import com.android.purebilibili.data.model.response.DynamicReplyOption
import com.android.purebilibili.data.model.response.DynamicPublishDraft
import com.android.purebilibili.data.model.response.DynamicPublishToken
import com.android.purebilibili.data.model.response.DynamicRichTextIssue
import com.android.purebilibili.data.model.response.DynamicUnlocatedRichText

private const val MAX_DYNAMIC_IMAGES = 18

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DynamicPublishComposer(
    initialDraft: DynamicPublishDraft,
    isEditing: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    replyOid: Long? = null,
    replyType: Int = 0,
    draftAccountId: Long? = null,
    draftKey: String = "main",
    onDismiss: () -> Unit,
    onSubmit: (DynamicPublishDraft) -> Unit
) {
    var textValue by remember(initialDraft) { mutableStateOf(TextFieldValue(initialDraft.text, TextRange(initialDraft.text.length))) }
    val text = textValue.text
    var title by remember(initialDraft) { mutableStateOf(initialDraft.title) }
    var imageUris by remember(initialDraft) { mutableStateOf(initialDraft.imageUris) }
    var vote by remember(initialDraft) {
        mutableStateOf(
            initialDraft.voteId.takeIf { it > 0L }?.let {
                DynamicCreatedVote(it, initialDraft.voteTitle.ifBlank { "投票" })
            }
        )
    }
    var reserve by remember(initialDraft) {
        mutableStateOf(
            initialDraft.reserveId.takeIf { it > 0L }?.let {
                DynamicCreatedReserve(it, "预约")
            }
        )
    }
    val originalTokens = remember(initialDraft) { initialDraft.richTokens ?: initialDynamicComposerTokens(initialDraft) }
    var richTokens by remember(initialDraft) { mutableStateOf(originalTokens) }
    var richTextIssues by remember(initialDraft) { mutableStateOf(initialDraft.unresolvedRichTextRanges) }
    val originalUnlocatedItems = remember(initialDraft) {
        initialDraft.unlocatedRichTextItems.ifEmpty {
            if (initialDraft.hasUnlocatedRichText || (initialDraft.hasUnresolvedRichText && initialDraft.unresolvedRichTextRanges.isEmpty()))
                listOf(DynamicUnlocatedRichText("未定位的 @ 或表情")) else emptyList()
        }
    }
    var unlocatedItems by remember(initialDraft) { mutableStateOf(originalUnlocatedItems) }
    var pendingUnlocatedIndex by remember(initialDraft) { mutableStateOf<Int?>(null) }
    val hasUnlocatedRichText = unlocatedItems.isNotEmpty()
    val hasUnresolvedRichText = hasUnlocatedRichText || richTextIssues.isNotEmpty()
    var mentions by remember(initialDraft) { mutableStateOf(initialDraft.mentions) }
    var emotes by remember(initialDraft) { mutableStateOf(initialDraft.emotes) }
    var topic by remember(initialDraft) { mutableStateOf(initialDraft.topic) }
    var privatePublish by remember(initialDraft) { mutableStateOf(initialDraft.private) }
    val context = LocalContext.current
    var publishAtSeconds by remember(initialDraft) { mutableStateOf(initialDraft.publishAtSeconds) }
    var replyOption by remember(initialDraft) { mutableStateOf(initialDraft.replyOption) }
    var loadedReplyOption by remember(replyOid, replyType) { mutableStateOf<DynamicReplyOption?>(null) }
    LaunchedEffect(replyOid, replyType) {
        if (isEditing && replyOid != null && replyOid > 0 && replyType > 0) {
            try {
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi.getReplyInteractionStatus(replyOid, replyType)
                val data = response.data
                val replyStatus = data?.up_reply
                val selectionStatus = data?.up_reply_selection
                if (response.code == 0 && replyStatus != null && selectionStatus != null) {
                    loadedReplyOption = when {
                        replyStatus.status != 1 -> DynamicReplyOption.CLOSED
                        selectionStatus.status == 1 -> DynamicReplyOption.SELECTED
                        else -> DynamicReplyOption.ALLOW
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                loadedReplyOption = null
            }
        }
    }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var cropImage by remember { mutableStateOf<String?>(null) }
    var showReplyMenu by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnresolvedRichTextDialog by remember { mutableStateOf(false) }
    var savingDraft by remember { mutableStateOf(false) }
    var draftError by remember { mutableStateOf<String?>(null) }
    val draftScope = rememberCoroutineScope()
    var scheduleError by remember { mutableStateOf<String?>(null) }
    var showSchedulePicker by remember { mutableStateOf(false) }
    var showVoteDialog by remember { mutableStateOf(false) }
    var showReserveDialog by remember { mutableStateOf(false) }
    var showMentionDialog by remember { mutableStateOf(false) }
    var mentionTrigger by remember { mutableStateOf<Int?>(null) }
    var showTopicDialog by remember { mutableStateOf(false) }
    var showEmotePanel by remember { mutableStateOf(false) }
    var showMorePanel by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val bodyFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    var lastKeyboardHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(keyboardHeight) {
        if (keyboardHeight > 0.dp) lastKeyboardHeight = keyboardHeight
    }
    val customPanelVisible = showEmotePanel || showMorePanel
    var restoreKeyboard by remember { mutableStateOf(false) }
    fun togglePanel(emote: Boolean) {
        val closing = if (emote) showEmotePanel else showMorePanel
        showEmotePanel = !closing && emote
        showMorePanel = !closing && !emote
        restoreKeyboard = closing
        if (!closing) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(customPanelVisible, restoreKeyboard) {
        if (customPanelVisible) keyboard?.hide()
        else if (restoreKeyboard) {
            bodyFocus.requestFocus()
            keyboard?.show()
            restoreKeyboard = false
        }
    }
    var showVisibilityMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        PickMultipleGalleryVisualMedia(maxItems = MAX_DYNAMIC_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageUris = (imageUris + uris.map { it.toString() }).distinct().take(MAX_DYNAMIC_IMAGES)
        }
    }

    val canPublish = !submitting &&
        (text.isNotBlank() || imageUris.isNotEmpty() || vote != null || reserve != null)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun currentDraft(): DynamicPublishDraft = DynamicPublishDraft(
                text = text,
                title = title,
                imageUris = imageUris,
                voteId = vote?.voteId ?: 0L,
                voteTitle = vote?.title.orEmpty(),
                reserveId = reserve?.reserveId ?: 0L,
                private = privatePublish,
                mentions = mentions,
                emotes = emotes,
                topic = topic,
                existingImages = initialDraft.existingImages.filter { it.img_src in imageUris },
                publishAtSeconds = publishAtSeconds,
                replyOption = replyOption,
                richTokens = richTokens,
                hasUnresolvedRichText = hasUnresolvedRichText,
                unresolvedRichTextRanges = richTextIssues,
                hasUnlocatedRichText = hasUnlocatedRichText,
                unlocatedRichTextItems = unlocatedItems,
            )
    fun submitDraft() {
        if (canPublish && !savingDraft) {
            if (hasUnresolvedRichText) showUnresolvedRichTextDialog = true
            else onSubmit(currentDraft())
        }
    }

    fun requestDismiss() {
        if (submitting || savingDraft) return
        if (customPanelVisible) {
            showEmotePanel = false
            showMorePanel = false
            return
        }
        val changed = text != initialDraft.text || title != initialDraft.title ||
            imageUris != initialDraft.imageUris || vote?.voteId != initialDraft.voteId.takeIf { it > 0L } ||
            reserve?.reserveId != initialDraft.reserveId.takeIf { it > 0L } ||
            topic != initialDraft.topic || privatePublish != initialDraft.private ||
            publishAtSeconds != initialDraft.publishAtSeconds || replyOption != initialDraft.replyOption || richTokens != originalTokens ||
            unlocatedItems != originalUnlocatedItems
        if (changed) showDiscardDialog = true else onDismiss()
    }

    // Keep each theme's native modal host and the shared window backdrop policy.
    com.android.purebilibili.core.ui.AppModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = AppSurfaceTokens.background(),
        dragHandle = null,
        dismissOnBackPress = false,
    ) {
        com.android.purebilibili.core.ui.AppModalSheetNavigationHost(
            dismissOnBackPress = true,
            onDismissRequest = ::requestDismiss,
        ) { backModifier ->
            Column(
                modifier = Modifier
                    .then(backModifier)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .then(if (customPanelVisible) Modifier else Modifier.imePadding()),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        if (isEditing) "编辑动态" else "发布动态",
                        modifier = Modifier.padding(horizontal = 88.dp),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    AppIconButton(
                        onClick = ::requestDismiss,
                        enabled = !submitting && !savingDraft,
                        modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                    ) {
                        AppSurface(
                            modifier = Modifier.size(34.dp),
                            shape = AppShapes.container(ContainerLevel.Pill),
                            color = AppSurfaceTokens.secondaryContainer(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIcon(rememberAppBackIcon(), contentDescription = "返回", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    AppButton(
                        onClick = ::submitDraft,
                        enabled = canPublish && !savingDraft,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        shape = AppShapes.container(ContainerLevel.Pill),
                    ) {
                        AppText(if (submitting) "发布中…" else if (publishAtSeconds != null) "定时发布" else "发布", maxLines = 1)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppTextButton(onClick = { showTopicDialog = true }, enabled = !submitting, modifier = Modifier.weight(1f, fill = false)) {
                                AppText(topic?.name?.let { "#$it#" } ?: "# 选择话题", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (topic != null) {
                                AppIconButton(onClick = { topic = null }, enabled = !submitting) {
                                    AppIcon(rememberAppDeleteIcon(), contentDescription = "移除话题")
                                }
                            }
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = title,
                            onValueChange = { if (!submitting) title = it.take(20) },
                            modifier = Modifier.fillMaxWidth().onFocusChanged {
                                if (it.isFocused) { showEmotePanel = false; showMorePanel = false }
                            },
                            enabled = !submitting,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = AppContentColors.primaryText(),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppContentColors.accent()),
                            singleLine = true,
                            decorationBox = { field ->
                                Box {
                                    if (title.isEmpty()) AppText(
                                        "标题，选填20字",
                                        color = AppContentColors.secondaryText(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        ),
                                    )
                                    field()
                                }
                            },
                        )
                        CommentEmoteTextField(
                            value = textValue,
                            onValueChange = { next ->
                                if (!submitting) {
                                    richTokens = updateDynamicComposerTokens(textValue.text, next.text, richTokens)
                                    richTextIssues = updateDynamicRichTextIssues(textValue.text, next.text, richTextIssues)
                                    val caret = next.selection.start
                                    if (next.text.length == textValue.text.length + 1 &&
                                        next.selection.collapsed && caret > 0 && next.text[caret - 1] == '@') {
                                        mentionTrigger = caret - 1
                                        showMentionDialog = true
                                    }
                                    textValue = next
                                }
                            },
                            emoteUrls = DynamicEmoteCatalog.snapshot(),
                            enabled = !submitting,
                            readOnly = submitting || customPanelVisible,
                            hint = "说点什么吧",
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppContentColors.primaryText()),
                            hintColor = AppContentColors.secondaryText(),
                            cursorColor = AppContentColors.accent(),
                            onBeginEditing = { showEmotePanel = false; showMorePanel = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(bodyFocus),
                        )
                        if (hasUnresolvedRichText) {
                            val examples = richTextIssues.mapNotNull { issue ->
                                if (issue.start >= 0 && issue.end <= text.length && issue.start < issue.end) text.substring(issue.start, issue.end) else null
                            }.distinct().take(3)
                            AppText(
                                text = if (examples.isEmpty()) "部分原有 @ 或表情无法定位，请检查正文。"
                                    else "请检查 ${examples.joinToString("、")}：选中文字并重新插入对应 @ 或表情，或删除它。",
                                color = AppContentColors.error(),
                            )
                            unlocatedItems.forEachIndexed { index, item ->
                                AppTextButton(onClick = { pendingUnlocatedIndex = index }, enabled = !submitting && !savingDraft) {
                                    AppText("${item.label} · 检查")
                                }
                            }
                        }
                        if (vote != null) {
                            PublishAttachedItem(
                                label = "投票：${vote?.title.orEmpty()}",
                                onEdit = { showVoteDialog = true },
                                onRemove = { vote = null },
                                enabled = !submitting,
                            )
                        }
                        if (reserve != null) {
                            PublishAttachedItem(
                                label = "预约：${reserve?.title.orEmpty()}",
                                onEdit = { showReserveDialog = true },
                                onRemove = { reserve = null },
                                enabled = !submitting,
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AppTextButton(
                                    onClick = { if (publishAtSeconds != null) { publishAtSeconds = null; scheduleError = null } else showSchedulePicker = true },
                                    enabled = !submitting && !isEditing && !privatePublish,
                                ) {
                                    AppText(publishAtSeconds?.let {
                                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it * 1000)) + " ×"
                                    } ?: "定时发布", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            Column(horizontalAlignment = Alignment.Start) {
                                com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { showReplyMenu = true }, enabled = !submitting) {
                                    AppTextButton(onClick = { showReplyMenu = true }, enabled = !submitting) {
                                        AppIcon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(19.dp))
                                        AppText(when (replyOption ?: loadedReplyOption) {
                                            DynamicReplyOption.ALLOW -> "允许评论"
                                            DynamicReplyOption.CLOSED -> "关闭评论"
                                            DynamicReplyOption.SELECTED -> "精选评论"
                                            null -> "评论设置"
                                        })
                                        AppIcon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                    AppDropdownMenu(expanded = showReplyMenu, onDismissRequest = { showReplyMenu = false }) {
                                        listOf(DynamicReplyOption.ALLOW to "允许评论", DynamicReplyOption.CLOSED to "关闭评论", DynamicReplyOption.SELECTED to "精选评论").forEach { (option, label) ->
                                            AppDropdownMenuItem(text = { AppText(label) }, onClick = { replyOption = option; showReplyMenu = false })
                                        }
                                    }
                                }
                                com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { showVisibilityMenu = true }, enabled = !submitting) {
                                    AppTextButton(
                                        onClick = { showVisibilityMenu = true },
                                        enabled = !submitting,
                                    ) {
                                        AppIcon(
                                            if (privatePublish) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = null,
                                        )
                                        AppText(if (privatePublish) "仅自己可见" else "所有人可见")
                                        AppIcon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                    AppDropdownMenu(
                                        expanded = showVisibilityMenu,
                                        onDismissRequest = { showVisibilityMenu = false },
                                    ) {
                                        AppDropdownMenuItem(
                                            text = { AppText("所有人可见") },
                                            onClick = { privatePublish = false; showVisibilityMenu = false },
                                        )
                                        AppDropdownMenuItem(
                                            text = { AppText("仅自己可见") },
                                            onClick = { privatePublish = true; showVisibilityMenu = false },
                                            enabled = publishAtSeconds == null,
                                        )
                                    }
                                }
                            }
                        }
                        scheduleError?.let { AppText(it, color = AppContentColors.error()) }
                        Spacer(Modifier.height(5.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                        ) {
                            items(imageUris, key = { it }) { uri ->
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "待发布图片",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(AppShapes.container(ContainerLevel.Chip))
                                            .combinedClickable(
                                                enabled = !submitting,
                                                onClick = { previewImage = uri },
                                                onLongClickLabel = "移除图片",
                                                onLongClick = { imageUris = imageUris - uri },
                                            ),
                                        contentScale = ContentScale.Crop,
                                    )
                                    AppIconButton(
                                        onClick = { cropImage = uri },
                                        enabled = !submitting,
                                        modifier = Modifier.align(Alignment.BottomEnd).size(AppChromeSizeTokens.MinimumTouchTarget),
                                    ) {
                                        AppIcon(Icons.Outlined.Edit, contentDescription = "裁剪图片")
                                    }
                                    AppIconButton(
                                        onClick = { imageUris = imageUris - uri },
                                        enabled = !submitting,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(AppChromeSizeTokens.MinimumTouchTarget),
                                    ) {
                                        AppIcon(rememberAppDeleteIcon(), contentDescription = "移除图片")
                                    }
                                }
                            }
                            if (imageUris.size < MAX_DYNAMIC_IMAGES) {
                                item {
                                    AppSurface(
                                        onClick = {
                                            picker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !submitting,
                                        modifier = Modifier.size(100.dp),
                                        shape = AppShapes.container(ContainerLevel.Chip),
                                        color = AppSurfaceTokens.secondaryContainer(),
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppIcon(Icons.Outlined.Add, contentDescription = "添加图片")
                                        }
                                    }
                                }
                            }
                        }
                        errorMessage?.let { AppText(it, color = AppContentColors.error()) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = { togglePanel(true) }, enabled = !submitting) {
                        AppIcon(if (showEmotePanel) Icons.Outlined.Keyboard else Icons.Outlined.Mood,
                            contentDescription = if (showEmotePanel) "输入" else "表情",
                            tint = if (showEmotePanel) AppContentColors.accent() else AppContentColors.secondaryText())
                    }
                    AppIconButton(onClick = { mentionTrigger = null; showMentionDialog = true }, enabled = !submitting) {
                        AppIcon(Icons.Outlined.AlternateEmail, contentDescription = "@ 用户")
                    }
                    if (!isEditing) {
                        AppIconButton(onClick = { showVoteDialog = true }, enabled = !submitting) {
                            AppIcon(Icons.Outlined.Poll, contentDescription = "投票")
                        }
                        AppIconButton(onClick = { togglePanel(false) }, enabled = !submitting) {
                            AppIcon(if (showMorePanel) Icons.Outlined.Keyboard else Icons.Outlined.AddCircleOutline,
                                contentDescription = if (showMorePanel) "输入" else "更多")
                        }
                    }
                }
                if (customPanelVisible) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val panelHeight = maxOf(lastKeyboardHeight, if (maxWidth >= 600.dp) 300.dp else 170.dp)
                        if (showEmotePanel) {
                            DynamicEmotePanel(Modifier.fillMaxWidth().height(panelHeight), enabled = !submitting) { emote ->
                                emotes = (emotes + emote).distinct()
                                val start = textValue.selection.min
                                richTextIssues = replaceDynamicRichTextIssues(richTextIssues, start, textValue.selection.max, emote.length)
                                richTokens = replaceDynamicComposerTokens(richTokens, start, textValue.selection.max, emote.length) +
                                    DynamicPublishToken(start, start + emote.length, 9, "", emote)
                                textValue = insertDynamicComposerToken(textValue, emote)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().height(panelHeight).padding(12.dp)) {
                                AppTextButton(onClick = { showReserveDialog = true }, enabled = !submitting) { AppText("直播预约") }
                            }
                        }
                    }
                }

            }
        }
    }

    if (showSchedulePicker) {
        val initialTime = remember { publishAtSeconds?.times(1000L) ?: (System.currentTimeMillis() + 7 * 60 * 1000L) }
        val onScheduleConfirmed = androidx.compose.runtime.rememberUpdatedState<(Long) -> Unit> { selectedMillis ->
            val selected = selectedMillis / 1000L
            val now = System.currentTimeMillis() / 1000L
            if (selected < now + 360 || selected > now + 7 * 24 * 60 * 60) {
                scheduleError = "请选择 6 分钟后至 7 天内的时间"
            } else {
                publishAtSeconds = selected
                scheduleError = null
            }
            showSchedulePicker = false
        }
        DisposableEffect(context, initialTime) {
            val initial = Calendar.getInstance().apply { timeInMillis = initialTime }
            var timeDialog: TimePickerDialog? = null
            val dateDialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    timeDialog = TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val selected = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            onScheduleConfirmed.value(selected)
                        },
                        initial.get(Calendar.HOUR_OF_DAY),
                        initial.get(Calendar.MINUTE),
                        true,
                    ).apply {
                        setOnCancelListener { showSchedulePicker = false }
                        show()
                    }
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH),
            ).apply {
                setOnCancelListener { showSchedulePicker = false }
                show()
            }
            onDispose {
                dateDialog.dismiss()
                timeDialog?.dismiss()
            }
        }
    }
    cropImage?.let { image ->
        DynamicImageCropDialog(
            source = image,
            onDismiss = { cropImage = null },
            onCropped = { cropped ->
                imageUris = imageUris.map { if (it == image) cropped else it }
                cropImage = null
            },
        )
    }
    previewImage?.let { image ->
        val index = imageUris.indexOf(image)
        if (index >= 0) ImagePreviewDialog(images = imageUris, initialIndex = index, onDismiss = { previewImage = null })
    }
    pendingUnlocatedIndex?.let { index ->
        unlocatedItems.getOrNull(index)?.let { item ->
            AppAlertDialog(
                onDismissRequest = { pendingUnlocatedIndex = null },
                title = { AppText("确认已检查") },
                text = { AppText("${item.label}无法在正文中定位。确认后将不再提醒，保存时该原有节点不会保留。请先检查正文，必要时重新插入对应内容。") },
                confirmButton = {
                    AppDialogAction(onClick = {
                        unlocatedItems = unlocatedItems.toMutableList().also { it.removeAt(index) }
                        pendingUnlocatedIndex = null
                    }) { AppText("已检查") }
                },
                dismissButton = { AppDialogAction(onClick = { pendingUnlocatedIndex = null }) { AppText("返回") } },
            )
        }
    }
    if (showUnresolvedRichTextDialog) {
        AppAlertDialog(
            onDismissRequest = { showUnresolvedRichTextDialog = false },
            title = { AppText("部分内容可能变为普通文字") },
            text = { AppText("服务端未提供完整的 @ 或表情信息，或无法确认其位置。继续保存可能使相关文字失去关联；你可以返回编辑并重新插入。") },
            confirmButton = { AppDialogAction(onClick = { showUnresolvedRichTextDialog = false; onSubmit(currentDraft()) }) { AppText("继续保存") } },
            dismissButton = { AppDialogAction(onClick = { showUnresolvedRichTextDialog = false }) { AppText("返回检查") } },
        )
    }
    if (showDiscardDialog) {
        AppAlertDialog(
            onDismissRequest = { if (!savingDraft) showDiscardDialog = false },
            title = { AppText("保存草稿？") },
            text = {
                Column {
                    AppText(if (draftAccountId != null && !isEditing) "保存后可在发布入口继续编辑。" else "尚未保存的内容将被丢弃。")
                    draftError?.let { AppText(it, color = AppContentColors.error()) }
                    AppTextButton(enabled = !savingDraft, onClick = {
                        if (draftAccountId != null && !isEditing) {
                            savingDraft = true
                            draftScope.launch {
                                DynamicDraftStore.clear(context, draftAccountId, draftKey).fold(
                                    onSuccess = { savingDraft = false; showDiscardDialog = false; onDismiss() },
                                    onFailure = { savingDraft = false; draftError = it.message ?: "无法清除草稿" },
                                )
                            }
                        } else {
                            showDiscardDialog = false
                            onDismiss()
                        }
                    }) { AppText("放弃内容") }
                }
            },
            confirmButton = {
                if (draftAccountId != null && !isEditing) {
                    AppDialogAction(onClick = {
                        if (!savingDraft) {
                            savingDraft = true
                            draftError = null
                            draftScope.launch {
                                DynamicDraftStore.save(context, draftAccountId, currentDraft(), draftKey).fold(
                                    onSuccess = { savingDraft = false; showDiscardDialog = false; onDismiss() },
                                    onFailure = { savingDraft = false; draftError = it.message ?: "草稿保存失败" },
                                )
                            }
                        }
                    }) { AppText(if (savingDraft) "保存中…" else "保存草稿") }
                }
            },
            dismissButton = { AppDialogAction(onClick = { if (!savingDraft) showDiscardDialog = false }) { AppText("继续编辑") } },
        )
    }
    if (showVoteDialog) {
        DynamicCreateVoteDialog(
            voteId = vote?.voteId,
            onDismiss = { showVoteDialog = false },
            onCreated = { created ->
                vote = created
                showVoteDialog = false
            }
        )
    }
    if (showReserveDialog) {
        DynamicCreateReserveDialog(
            reserveId = reserve?.reserveId,
            onDismiss = { showReserveDialog = false },
            onCreated = { created ->
                reserve = created
                showReserveDialog = false
            }
        )
    }
    if (showMentionDialog) {
        DynamicMentionPickerDialog(
            onDismiss = { mentionTrigger = null; showMentionDialog = false },
            onSelected = { selected ->
                mentions = (mentions + selected).distinctBy { it.uid }
                val start = mentionTrigger ?: textValue.selection.min
                val mentionValue = textValue.copy(selection = TextRange(start, textValue.selection.max))
                val replacement = selected.joinToString(separator = "", transform = { "@${it.name} " })
                val shifted = replaceDynamicComposerTokens(richTokens, start, textValue.selection.max, replacement.length)
                richTextIssues = replaceDynamicRichTextIssues(richTextIssues, start, textValue.selection.max, replacement.length)
                var offset = start
                val inserted = selected.map { mention ->
                    val token = "@${mention.name}"
                    DynamicPublishToken(offset, offset + token.length, 2, mention.uid.toString(), token).also { offset += token.length + 1 }
                }
                textValue = insertDynamicComposerToken(mentionValue, replacement)
                mentionTrigger = null
                richTokens = shifted + inserted
                showMentionDialog = false
            },
        )
    }
    if (showTopicDialog) {
        DynamicTopicPickerDialog(
            onDismiss = { showTopicDialog = false },
            onSelected = { selectedTopic ->
                topic = selectedTopic
                showTopicDialog = false
            },
        )
    }

}

@Composable
private fun PublishAttachedItem(
    label: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.surfaceContainer(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacingTokens.Medium, end = AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(label, modifier = Modifier.weight(1f))
            AppTextButton(onClick = onEdit, enabled = enabled) { AppText("编辑") }
            AppIconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(AppChromeSizeTokens.MinimumTouchTarget),
            ) {
                AppIcon(rememberAppDeleteIcon(), contentDescription = "移除附件")
            }
        }
    }
}

internal fun appendDynamicComposerToken(text: String, token: String): String {
    if (token.isBlank()) return text
    return when {
        text.isBlank() -> token
        text.last().isWhitespace() || token.first().isWhitespace() -> text + token
        else -> "$text $token"
    }
}

internal fun insertDynamicComposerToken(value: TextFieldValue, token: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val result = value.text.replaceRange(start, end, token)
    return TextFieldValue(result, TextRange(start + token.length))
}

internal fun initialDynamicComposerTokens(draft: DynamicPublishDraft): List<DynamicPublishToken> {
    val candidates = draft.mentions.map { Triple("@${it.name}", 2, it.uid.toString()) } +
        draft.emotes.map { Triple(it, 9, "") }
    val result = mutableListOf<DynamicPublishToken>()
    var cursor = 0
    while (cursor < draft.text.length) {
        val match = candidates.filter { it.first.isNotEmpty() }.mapNotNull { candidate ->
            draft.text.indexOf(candidate.first, cursor).takeIf { it >= 0 }?.let { it to candidate }
        }.minWithOrNull(compareBy<Pair<Int, Triple<String, Int, String>>> { it.first }.thenByDescending { it.second.first.length }) ?: break
        val (start, candidate) = match
        result += DynamicPublishToken(start, start + candidate.first.length, candidate.second, candidate.third, candidate.first)
        cursor = start + candidate.first.length
    }
    return result
}

internal fun updateDynamicComposerTokens(old: String, new: String, tokens: List<DynamicPublishToken>): List<DynamicPublishToken> {
    if (old == new) return tokens
    var start = 0
    while (start < old.length && start < new.length && old[start] == new[start]) start++
    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) { oldEnd--; newEnd-- }
    return replaceDynamicComposerTokens(tokens, start, oldEnd, newEnd - start)
}

internal fun replaceDynamicComposerTokens(tokens: List<DynamicPublishToken>, start: Int, oldEnd: Int, insertedLength: Int): List<DynamicPublishToken> {
    val delta = insertedLength - (oldEnd - start)
    return tokens.mapNotNull { token ->
        when {
            token.end <= start -> token
            token.start >= oldEnd -> token.copy(start = token.start + delta, end = token.end + delta)
            else -> null
        }
    }
}

internal fun updateDynamicRichTextIssues(old: String, new: String, issues: List<DynamicRichTextIssue>): List<DynamicRichTextIssue> {
    if (old == new) return issues
    var start = 0
    while (start < old.length && start < new.length && old[start] == new[start]) start++
    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) { oldEnd--; newEnd-- }
    return replaceDynamicRichTextIssues(issues, start, oldEnd, newEnd - start)
}

internal fun replaceDynamicRichTextIssues(issues: List<DynamicRichTextIssue>, start: Int, oldEnd: Int, insertedLength: Int): List<DynamicRichTextIssue> {
    val delta = insertedLength - (oldEnd - start)
    return issues.mapNotNull { issue ->
        when {
            issue.end <= start -> issue
            issue.start >= oldEnd -> issue.copy(start = issue.start + delta, end = issue.end + delta)
            start <= issue.start && oldEnd >= issue.end -> null
            else -> {
                val newStart = minOf(issue.start, start)
                val newEnd = if (issue.end > oldEnd) issue.end + delta else start + insertedLength
                if (newEnd > newStart) DynamicRichTextIssue(newStart, newEnd) else null
            }
        }
    }
}
