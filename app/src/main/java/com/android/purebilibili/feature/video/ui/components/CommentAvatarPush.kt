package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.ReplyMember

/**
 * A pushed avatar can enter the next record's top padding to meet its neighbour.
 * Keep that gutter transparent so later lazy items cannot paint over the previous avatar.
 * This boundary is specific to avatar push; disabled MD3/Miuix rows retain full backgrounds.
 */
internal fun Modifier.commentAvatarRowBackground(
    color: Color,
    pushEnabled: Boolean,
    gutterWidth: Dp,
): Modifier = if (pushEnabled) {
    drawBehind {
        val left = gutterWidth.toPx().coerceIn(0f, size.width)
        drawRect(color, topLeft = Offset(left, 0f), size = Size(size.width - left, size.height))
    }
} else {
    background(color)
}

/** Compose adapter for the comment avatar placement kernel. */
internal fun resolveCommentAvatarPushOffset(
    itemOffsetPx: Int,
    itemHeightPx: Int,
    topInsetPx: Int,
    bottomInsetPx: Int,
    viewportTopPx: Int,
    contentHeightPx: Int? = null,
    minimumSlotPx: Int = 48,
    imageBottomOffsetPx: Int = 44,
    nextAvatarTopPx: Int? = null,
    avatarDiameterPx: Int = 42,
): Int {
    val boundedHeight = (contentHeightPx?.coerceAtMost(itemHeightPx) ?: itemHeightPx)
        .coerceAtLeast(0)
    if (boundedHeight <= topInsetPx + bottomInsetPx) return 0
    val groupTop = itemOffsetPx + topInsetPx
    // A neighbouring avatar is the contact boundary, not the padded item edge.
    // Compensating the source solver's slot preserves it without an invisible gap.
    val cellBottom = nextAvatarTopPx?.let { it - avatarDiameterPx + minimumSlotPx }
        ?: (itemOffsetPx + boundedHeight - bottomInsetPx)
    // Chat avatars belong at a message's bottom; comment avatars belong beside
    // their author. Reflect the original boundary solver, then remove its draw
    // inset so a visible author header always yields exactly zero displacement.
    val reflectedTop = resolveNagramChatAvatarTop(
        groupTopPx = -cellBottom,
        cellBottomPx = -groupTop,
        viewportBottomPx = -viewportTopPx,
        minimumSlotPx = minimumSlotPx,
        imageBottomOffsetPx = imageBottomOffsetPx,
    )
    return -reflectedTop - imageBottomOffsetPx - groupTop
}

@Composable
internal fun PushingReplyMemberAvatar(
    member: ReplyMember,
    placeholderColor: Color,
    lightweightMode: Boolean,
    listState: LazyListState,
    itemKey: Any,
    modifier: Modifier = Modifier,
    contentHeightPxProvider: (() -> Int)? = null,
    allowNeighborContact: Boolean = true,
    onClickAtHeader: () -> Unit,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val avatarOffset: Density.() -> IntOffset = {
        commentAvatarPushPlacement(listState, itemKey, 10.dp, 1.dp, contentHeightPxProvider, allowNeighborContact)
    }
    ReplyMemberAvatar(
        member = member,
        placeholderColor = placeholderColor,
        lightweightMode = lightweightMode,
        modifier = modifier.offset(avatarOffset),
        onClick = {
            // 头像已经离开作者栏时，普通导航保留这次点击，避免只缩回静止的文字。
            if (avatarOffset(density).y == 0) onClickAtHeader() else onClick()
        },
    )
}

/** Geometry is read during placement, including measured content bounds for mixed lazy items. */
internal fun Modifier.commentAvatarPushOffset(
    listState: LazyListState,
    itemKey: Any,
    topInset: Dp = 10.dp,
    bottomInset: Dp = 1.dp,
    contentHeightPxProvider: (() -> Int)? = null,
    allowNeighborContact: Boolean = true,
): Modifier = offset {
    commentAvatarPushPlacement(listState, itemKey, topInset, bottomInset, contentHeightPxProvider, allowNeighborContact)
}

private fun Density.commentAvatarPushPlacement(
    listState: LazyListState,
    itemKey: Any,
    topInset: Dp,
    bottomInset: Dp,
    contentHeightPxProvider: (() -> Int)?,
    allowNeighborContact: Boolean,
): IntOffset {
    val layout = listState.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.key == itemKey }
    val offset = if (item == null || layout.reverseLayout) {
        0
    } else {
        val nextComment = if (allowNeighborContact) {
            layout.visibleItemsInfo.firstOrNull {
                it.index == item.index + 1 && isCommentAvatarItemKey(it.key)
            }
        } else null
        resolveCommentAvatarPushOffset(
            nextAvatarTopPx = nextComment?.let { it.offset + topInset.roundToPx() },
            avatarDiameterPx = NAGRAM_CHAT_AVATAR_SIZE_DP.dp.roundToPx(),
            itemOffsetPx = item.offset,
            // A root comment may share its lazy item with a section title. Its avatar
            // must leave with the comment rather than drift into that title.
            itemHeightPx = item.size,
            contentHeightPx = contentHeightPxProvider?.invoke(),
            topInsetPx = topInset.roundToPx(),
            bottomInsetPx = bottomInset.roundToPx(),
            // Respect the existing top content inset below floating chrome.
            viewportTopPx = layout.viewportStartOffset + layout.beforeContentPadding,
            minimumSlotPx = 48.dp.roundToPx(),
            imageBottomOffsetPx = 44.dp.roundToPx(),
        )
    }
    return IntOffset(0, offset)
}

@Composable
internal fun rememberCommentAvatarPushEnabled(): Boolean {
    val context = LocalContext.current
    val enabled by remember(context) { SettingsManager.getCommentAvatarPushEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    return enabled
}

/** Restrict neighbour contact to comment records, excluding vote cards and footers. */
internal fun isCommentAvatarItemKey(key: Any): Boolean = when (key) {
    is Long -> key > 0L
    is String -> when {
        key.startsWith("curtain_reply_") -> key.removePrefix("curtain_reply_").toLongOrNull() != null
        key.startsWith("reply_") -> key.removePrefix("reply_").toLongOrNull() != null
        key.endsWith("_true") || key.endsWith("_false") -> key.substringBeforeLast('_').toLongOrNull() != null
        else -> false
    }
    else -> false
}
