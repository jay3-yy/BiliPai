package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.HandThumbsup
import io.github.alexzhirkevich.cupertino.icons.outlined.BubbleLeft
import io.github.alexzhirkevich.cupertino.icons.outlined.HandThumbsup
import io.github.alexzhirkevich.cupertino.icons.outlined.MinusCircle
import io.github.alexzhirkevich.cupertino.icons.outlined.Trash

const val SUB_REPLY_DETAIL_HEADER_TAG = "subreply_detail_header"
const val SUB_REPLY_DETAIL_CLOSE_TAG = "subreply_detail_close"
const val SUB_REPLY_DETAIL_ROOT_TAG = "subreply_detail_root"
const val SUB_REPLY_DETAIL_LIST_TAG = "subreply_detail_reply_list"
const val SUB_REPLY_DETAIL_SECTION_TAG = "subreply_detail_section"
const val SUB_REPLY_DETAIL_SORT_TAG = "subreply_detail_sort"
const val SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX = "subreply_detail_conversation_"
const val SUB_REPLY_DETAIL_IMAGE_TAG_PREFIX = "subreply_detail_image_"

private val SUB_REPLY_DIRECTED_MESSAGE_PATTERN = Regex("""^\s*回复\s*@.+?[：:]""")

internal data class SubReplyDetailLayoutPolicy(
    val listBottomPaddingDp: Int,
    val footerTopPaddingDp: Int,
    val overlayRootCommentEntry: Boolean
)

internal typealias SubReplyDetailAppearance = VideoCommentAppearance

internal fun resolveSubReplyDetailLayoutPolicy(
    showRootCommentEntry: Boolean
): SubReplyDetailLayoutPolicy {
    return SubReplyDetailLayoutPolicy(
        listBottomPaddingDp = 16,
        footerTopPaddingDp = 0,
        overlayRootCommentEntry = false
    )
}

internal fun resolveSubReplyDetailSectionTitle(replyCount: Int): String {
    return "相关回复共${replyCount.coerceAtLeast(0)}条"
}

internal fun resolveSubReplyDetailAppearance(
    surfaceColor: Color,
    surfaceVariantColor: Color,
    surfaceContainerHighColor: Color,
    outlineVariantColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    onPrimaryColor: Color
): SubReplyDetailAppearance {
    return resolveVideoCommentAppearance(
        surfaceColor = surfaceColor,
        surfaceVariantColor = surfaceVariantColor,
        surfaceContainerHighColor = surfaceContainerHighColor,
        outlineVariantColor = outlineVariantColor,
        onSurfaceColor = onSurfaceColor,
        onSurfaceVariantColor = onSurfaceVariantColor,
        primaryColor = primaryColor,
        onPrimaryColor = onPrimaryColor
    )
}

internal fun shouldShowSubReplyConversationAction(item: ReplyItem): Boolean {
    return SUB_REPLY_DIRECTED_MESSAGE_PATTERN.containsMatchIn(item.content.message)
}

internal fun resolveSubReplyAuxiliaryLabel(item: ReplyItem): String? {
    val candidateNumber = item.member.garbCardNumber
        .filter(Char::isDigit)
        .takeIf { it.isNotEmpty() }
        ?: return null
    return "NO.${candidateNumber.padStart(6, '0')}"
}

internal fun resolveSubReplyAuxiliaryImageUrl(item: ReplyItem): String? {
    return listOf(
        item.member.garbCardImageWithFocus,
        item.member.garbCardImage
    ).firstOrNull { it.isNotBlank() }
}

@Composable
internal fun SubReplyDetailContent(
    rootReply: ReplyItem,
    subReplies: List<ReplyItem>,
    isLoading: Boolean,
    isEnd: Boolean,
    emoteMap: Map<String, String>,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    onRootCommentClick: (() -> Unit)? = null,
    onTimestampClick: ((Long) -> Unit)? = null,
    upMid: Long = 0,
    showUpFlag: Boolean = false,
    onImagePreview: ((List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit)? = null,
    onReplyClick: ((ReplyItem) -> Unit)? = null,
    dissolvingIds: Set<Long> = emptySet(),
    currentMid: Long = 0,
    onDissolveStart: ((Long) -> Unit)? = null,
    onDeleteComment: ((Long) -> Unit)? = null,
    onCommentLike: ((Long) -> Unit)? = null,
    likedComments: Set<Long> = emptySet(),
    onUrlClick: ((String) -> Unit)? = null,
    onAvatarClick: ((String) -> Unit)? = null
) {
    val layoutPolicy = remember {
        resolveSubReplyDetailLayoutPolicy(showRootCommentEntry = false)
    }
    val appearance = rememberVideoCommentAppearance()
    val unusedShowUpFlag = showUpFlag
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2 && !isLoading && !isEnd
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.panelColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                .testTag(SUB_REPLY_DETAIL_HEADER_TAG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "评论详情",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = appearance.primaryTextColor
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SUB_REPLY_DETAIL_CLOSE_TAG)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = appearance.primaryTextColor
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = appearance.dividerColor)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(SUB_REPLY_DETAIL_LIST_TAG),
            contentPadding = PaddingValues(bottom = layoutPolicy.listBottomPaddingDp.dp)
        ) {
            item(key = "root_reply") {
                Box(modifier = Modifier.testTag(SUB_REPLY_DETAIL_ROOT_TAG)) {
                    SubReplyDetailItem(
                        item = rootReply,
                        appearance = appearance,
                        isRootItem = true,
                        upMid = upMid,
                        emoteMap = emoteMap,
                        showUpFlag = unusedShowUpFlag,
                        onTimestampClick = onTimestampClick,
                        onImagePreview = onImagePreview,
                        onReplyClick = { onReplyClick?.invoke(rootReply) },
                        onDeleteClick = if (currentMid > 0 && rootReply.mid == currentMid) {
                            { onDeleteComment?.invoke(rootReply.rpid) }
                        } else null,
                        onLikeClick = { onCommentLike?.invoke(rootReply.rpid) },
                        isLiked = rootReply.action == 1 || rootReply.rpid in likedComments,
                        onUrlClick = onUrlClick,
                        onAvatarClick = { onAvatarClick?.invoke(it) ?: Unit },
                        showConversationAction = false,
                        auxiliaryLabel = null,
                        showTrailingDivider = false
                    )
                }
                HorizontalDivider(thickness = 8.dp, color = appearance.sectionDividerColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag(SUB_REPLY_DETAIL_SECTION_TAG),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resolveSubReplyDetailSectionTitle(replyCount = subReplies.size),
                        fontSize = 14.sp,
                        color = appearance.primaryTextColor,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.testTag(SUB_REPLY_DETAIL_SORT_TAG),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = "Sort",
                            tint = appearance.sortTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "按时间",
                            fontSize = 14.sp,
                            color = appearance.sortTint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = appearance.dividerColor
                )
            }

            itemsIndexed(
                items = subReplies,
                key = { _, item -> item.rpid }
            ) { index, item ->
                MaybeDissolvableVideoCard(
                    isDissolving = item.rpid in dissolvingIds,
                    onDissolveComplete = { onDeleteComment?.invoke(item.rpid) },
                    cardId = "subreply_detail_${item.rpid}",
                    modifier = Modifier.padding(bottom = 1.dp)
                ) {
                    SubReplyDetailItem(
                        item = item,
                        appearance = appearance,
                        isRootItem = false,
                        upMid = upMid,
                        emoteMap = emoteMap,
                        showUpFlag = unusedShowUpFlag,
                        onTimestampClick = onTimestampClick,
                        onImagePreview = onImagePreview,
                        onReplyClick = { onReplyClick?.invoke(item) },
                        onDeleteClick = if (currentMid > 0 && item.mid == currentMid) {
                            { onDissolveStart?.invoke(item.rpid) }
                        } else null,
                        onLikeClick = { onCommentLike?.invoke(item.rpid) },
                        isLiked = item.action == 1 || item.rpid in likedComments,
                        onUrlClick = onUrlClick,
                        onAvatarClick = { onAvatarClick?.invoke(it) ?: Unit },
                        showConversationAction = shouldShowSubReplyConversationAction(item),
                        auxiliaryLabel = resolveSubReplyAuxiliaryLabel(item),
                        showTrailingDivider = true
                    )
                }
            }

            item(key = "footer") {
                LaunchedEffect(isLoading, isEnd) {
                    if (!isLoading && !isEnd) {
                        onLoadMore()
                    }
                }
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CupertinoActivityIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SubReplyDetailItem(
    item: ReplyItem,
    appearance: SubReplyDetailAppearance,
    isRootItem: Boolean,
    upMid: Long,
    emoteMap: Map<String, String>,
    showUpFlag: Boolean,
    onTimestampClick: ((Long) -> Unit)?,
    onImagePreview: ((List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit)?,
    onReplyClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    onLikeClick: (() -> Unit)?,
    isLiked: Boolean,
    onUrlClick: ((String) -> Unit)?,
    onAvatarClick: (String) -> Unit,
    showConversationAction: Boolean,
    auxiliaryLabel: String?,
    showTrailingDivider: Boolean
) {
    val displayLocation = remember(item.replyControl?.location) {
        resolveReplyLocationText(item.replyControl?.location)
    }
    val displayLikeCount = remember(item.like, item.action, isLiked) {
        resolveReplyDisplayLikeCount(
            baseLikeCount = item.like,
            initialAction = item.action,
            isLiked = isLiked
        )
    }
    val localEmoteMap = remember(item.content.emote, emoteMap) {
        val inlineEmotes = item.content.emote.orEmpty()
        if (inlineEmotes.isEmpty()) {
            emoteMap
        } else {
            buildMap(emoteMap.size + inlineEmotes.size) {
                putAll(emoteMap)
                inlineEmotes.forEach { (key, value) -> put(key, value.url) }
            }
        }
    }
    val specialLabelText = remember(item.cardLabels, showUpFlag, item.upAction) {
        resolveReplySpecialLabelText(
            cardLabels = item.cardLabels,
            showUpFlag = showUpFlag,
            upAction = item.upAction
        )
    }
    val isUpComment = upMid > 0 && item.mid == upMid
    val metadataText = remember(item.ctime, displayLocation) {
        buildString {
            append(formatTime(item.ctime))
            if (!displayLocation.isNullOrEmpty()) {
                append(" · $displayLocation")
            }
        }
    }
    val avatarSize = if (isRootItem) 44.dp else 40.dp
    val nameColor = if (item.member.vip?.vipStatus == 1) {
        appearance.accentColor
    } else {
        appearance.primaryTextColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.panelColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 14.dp, start = 16.dp, end = 16.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(item.member.avatar))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(appearance.placeholderColor)
                    .clickable { onAvatarClick(item.member.mid) }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.member.uname,
                                fontSize = if (isRootItem) 15.sp else 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = nameColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.member.levelInfo.currentLevel > 0) {
                                LevelTag(
                                    level = item.member.levelInfo.currentLevel,
                                    isSeniorMember = item.member.isSeniorMember == 1
                                )
                            }

                            if (isUpComment) {
                                UpTag()
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = metadataText,
                            fontSize = 12.sp,
                            color = appearance.secondaryTextColor
                        )
                    }

                    if (!isRootItem && auxiliaryLabel != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        SubReplyAuxiliaryBadge(
                            item = item,
                            auxiliaryLabel = auxiliaryLabel,
                            appearance = appearance
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                ReplyMessageText(
                    text = item.content.message,
                    fontSize = if (isRootItem) 16.sp else 15.sp,
                    color = appearance.primaryTextColor,
                    emoteMap = localEmoteMap,
                    onTimestampClick = onTimestampClick,
                    onUrlClick = onUrlClick
                )

                if (!item.content.pictures.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.heightIn(max = 220.dp)) {
                        CommentPictures(
                            pictures = item.content.pictures,
                            onImageClick = { images, index, rect ->
                                onImagePreview?.invoke(
                                    images,
                                    index,
                                    rect,
                                    resolveReplyPreviewTextContent(item)
                                )
                            },
                            testTagPrefix = "$SUB_REPLY_DETAIL_IMAGE_TAG_PREFIX${item.rpid}_"
                        )
                    }
                }

                if (!specialLabelText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReplySpecialLabelChip(text = specialLabelText)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubReplyTextAction(
                        label = "回复",
                        appearance = appearance,
                        onClick = onReplyClick
                    )

                    if (showConversationAction) {
                        Spacer(modifier = Modifier.width(18.dp))
                        Text(
                            text = "查看对话",
                            fontSize = 13.sp,
                            color = appearance.actionTint,
                            modifier = Modifier
                                .testTag("$SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX${item.rpid}")
                                .clickable { onReplyClick() }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (onDeleteClick != null) {
                        Icon(
                            imageVector = CupertinoIcons.Outlined.Trash,
                            contentDescription = "Delete",
                            tint = appearance.actionTint,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeleteClick() }
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                    }

                    Icon(
                        imageVector = CupertinoIcons.Outlined.MinusCircle,
                        contentDescription = "Dislike",
                        tint = appearance.actionTint,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(18.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = onLikeClick != null) { onLikeClick?.invoke() }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) CupertinoIcons.Filled.HandThumbsup else CupertinoIcons.Outlined.HandThumbsup,
                            contentDescription = "Like",
                            tint = if (isLiked) appearance.primaryTextColor else appearance.actionTint,
                            modifier = Modifier.size(16.dp)
                        )
                        if (displayLikeCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = FormatUtils.formatStat(displayLikeCount.toLong()),
                                fontSize = 12.sp,
                                color = if (isLiked) appearance.primaryTextColor else appearance.actionTint
                            )
                        }
                    }
                }
            }
        }

        if (showTrailingDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                thickness = 0.5.dp,
                color = appearance.dividerColor
            )
        }
    }
}

@Composable
private fun SubReplyAuxiliaryBadge(
    item: ReplyItem,
    auxiliaryLabel: String,
    appearance: SubReplyDetailAppearance
) {
    Column(
        horizontalAlignment = Alignment.End
    ) {
        val auxiliaryImage = remember(item) { resolveSubReplyAuxiliaryImageUrl(item) }
        if (!auxiliaryImage.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(auxiliaryImage))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(appearance.placeholderColor)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = auxiliaryLabel.replace("NO.", "NO.\n"),
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = appearance.auxiliaryTint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SubReplyTextAction(
    label: String,
    appearance: SubReplyDetailAppearance,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            contentDescription = label,
            tint = appearance.actionTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = appearance.actionTint
        )
    }
}
