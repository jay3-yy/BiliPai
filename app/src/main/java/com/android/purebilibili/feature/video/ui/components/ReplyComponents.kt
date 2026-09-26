package com.android.purebilibili.feature.video.ui.components

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import coil3.request.transformations
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.transform.Transformation
import coil3.imageLoader
//  已改用 MaterialTheme.colorScheme.primary
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.BilibiliUrlParser
import com.android.purebilibili.core.util.rememberStoragePermissionState
import com.android.purebilibili.data.model.response.ReplyFansDetail
import com.android.purebilibili.data.model.response.ReplyCardLabel
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyContentUrl
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplySailingCardBg
import com.android.purebilibili.data.model.response.ReplySailingFan
import com.android.purebilibili.data.model.response.ReplyUpAction
import com.android.purebilibili.data.repository.BlockedUpRelationSource
import com.android.purebilibili.data.repository.BlockedUpRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextPlacement
import com.android.purebilibili.feature.dynamic.components.ImagePreviewCommentContext
import com.android.purebilibili.feature.dynamic.components.ImageDecodeTarget
import com.android.purebilibili.feature.dynamic.components.resolveCommentImageOriginalSizeLabel
import com.android.purebilibili.feature.dynamic.components.resolveImageDecodeSize
import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.core.ui.common.TextSelectionBottomSheet
import com.android.purebilibili.core.ui.common.TextSelectionPolicy
import com.android.purebilibili.core.ui.common.detectTapWithSelectionFriendly
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.OfficialVerifyBadge
import com.android.purebilibili.core.ui.OfficialVerifyBadgeSpec
import com.android.purebilibili.core.ui.OfficialVerifyBadgeTone
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import androidx.compose.foundation.text.selection.SelectionContainer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.components.UserUpBadge
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private val EMOTE_TOKEN_PATTERN = """\[(.*?)\]""".toRegex()
private const val COMMENT_INLINE_UP_BADGE_ID = "comment_inline_up_badge"
private const val COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID = "comment_inline_verify_personal_badge"
private const val COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID = "comment_inline_verify_organization_badge"
internal const val COMMENT_INLINE_TOP_BADGE_ID = "comment_inline_top_badge"
internal const val COMMENT_URL_TAG = "URL"
internal const val COMMENT_TIMESTAMP_TAG = "TIMESTAMP"
internal const val COMMENT_USER_TAG = "USER"
internal const val COMMENT_TOPIC_TAG = "TOPIC"
internal const val COMMENT_VOTE_TAG = "VOTE"
internal val COMMENT_TIMESTAMP_PATTERN =
    """(?<!\d)(\d{1,2})\s*[:：]\s*(\d{2})(?:\s*[:：]\s*(\d{2}))?(?!\d)""".toRegex()
internal val COMMENT_URL_PATTERN =
    """((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""".toRegex()
internal val COMMENT_INLINE_BVID_PATTERN =
    Regex("""(?<![A-Za-z0-9])BV[a-zA-Z0-9]{10}(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
internal val COMMENT_VOTE_PATTERN = Regex("""\{vote:(\d+)\}""")
internal const val COLLAPSED_SUB_REPLY_PREVIEW_LIMIT = 3
const val COMMENT_PICTURE_TAG_PREFIX = "comment_picture_"
const val COMMENT_ACTION_BUTTON_TAG_PREFIX = "comment_action_button_"
const val COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX = "comment_sub_reply_preview_"
const val COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX = "comment_view_all_replies_"
internal const val COMMENT_DECORATION_DECODE_MAX_PX = 512

// 标题缓存有界化：长会话里评论区引用的 BV 号会持续累积且永不重复使用，
// 超过上限整体清空即可（标题可重新拉取），条目本身很小但不可见地无限增长。
private const val REPLY_VIDEO_TITLE_CACHE_MAX_ENTRIES = 512

private val replyVideoTitleCache = object : ConcurrentHashMap<String, String>() {
    override fun put(key: String, value: String): String? {
        if (size >= REPLY_VIDEO_TITLE_CACHE_MAX_ENTRIES) clear()
        return super.put(key, value)
    }
}

/**
 * 官方 cardbg 经常是 972×162 的透明画布，实际角色图案只占其中一小部分。
 * 在解码线程裁掉全透明边缘，才能以官方预期的视觉尺寸显示内容而不裁掉图案。
 */
internal object TransparentBoundsCropTransformation : Transformation() {
    override val cacheKey: String = "comment_transparent_bounds_crop_v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input

        var left = input.width
        var top = input.height
        var right = -1
        var bottom = -1
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                if ((input.getPixel(x, y) ushr 24) > 4) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left || bottom < top) return input

        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        return if (cropWidth == input.width && cropHeight == input.height) {
            input
        } else {
            Bitmap.createBitmap(input, left, top, cropWidth, cropHeight)
        }
    }
}

internal data class ReplyItemLayoutPolicy(
    val horizontalPaddingDp: Int,
    val avatarSizeDp: Int,
    val avatarContentSpacingDp: Int,
    val actionButtonSizeDp: Int,
    val decorationWidthReserveDp: Int,
    val decorationImageWidthDp: Int,
    val decorationImageHeightDp: Int,
    val decorationMinWidthDp: Int
) {
    val dividerStartPaddingDp: Int
        get() = horizontalPaddingDp + avatarSizeDp + avatarContentSpacingDp
    val contentSpacingDp: Int
        get() = avatarSizeDp + avatarContentSpacingDp
}

internal fun resolveReplyItemLayoutPolicy(): ReplyItemLayoutPolicy {
    return ReplyItemLayoutPolicy(
        horizontalPaddingDp = 12,
        avatarSizeDp = 36,
        avatarContentSpacingDp = 8,
        actionButtonSizeDp = 40,
        decorationWidthReserveDp = 64,
        decorationImageWidthDp = 44,
        decorationImageHeightDp = 36,
        decorationMinWidthDp = 64
    )
}

/**
 * B站头像框素材按「外框画布 > 脸部圆形」绘制：框铺满外层，脸居中缩小，
 * 否则框与脸同尺寸会挤在边缘、前后层看起来错位。
 */
internal const val REPLY_AVATAR_FACE_FRACTION_WITH_PENDANT = 0.72f

internal fun resolveReplyAvatarFaceFraction(hasPendant: Boolean): Float {
    return if (hasPendant) REPLY_AVATAR_FACE_FRACTION_WITH_PENDANT else 1f
}

internal fun resolveReplyItemHeaderEndPaddingDp(
    hasBiliPaiDecoration: Boolean,
    policy: ReplyItemLayoutPolicy = resolveReplyItemLayoutPolicy()
): Int {
    return policy.actionButtonSizeDp + if (hasBiliPaiDecoration) policy.decorationWidthReserveDp else 0
}

internal fun resolveReplyItemContentStartPaddingDp(
    containerWidth: Dp,
    policy: ReplyItemLayoutPolicy = resolveReplyItemLayoutPolicy()
): Int {
    return if (containerWidth >= 280.dp) policy.contentSpacingDp else policy.horizontalPaddingDp
}

internal fun resolveReplyItemTextColumnWidthDp(
    containerWidthDp: Int,
    policy: ReplyItemLayoutPolicy = resolveReplyItemLayoutPolicy()
): Int {
    return (
        containerWidthDp -
            policy.horizontalPaddingDp * 2 -
            policy.avatarSizeDp -
            policy.avatarContentSpacingDp
        ).coerceAtLeast(0)
}

internal enum class ReplyLevelBadgeAsset {
    LEVEL_0,
    LEVEL_1,
    LEVEL_2,
    LEVEL_3,
    LEVEL_4,
    LEVEL_5,
    LEVEL_6,
    LEVEL_6_SENIOR
}

internal fun resolveReplyLevelBadgeAsset(
    level: Int,
    isSeniorMember: Boolean
): ReplyLevelBadgeAsset? {
    return when {
        isSeniorMember && level == 6 -> ReplyLevelBadgeAsset.LEVEL_6_SENIOR
        level == 0 -> ReplyLevelBadgeAsset.LEVEL_0
        level == 1 -> ReplyLevelBadgeAsset.LEVEL_1
        level == 2 -> ReplyLevelBadgeAsset.LEVEL_2
        level == 3 -> ReplyLevelBadgeAsset.LEVEL_3
        level == 4 -> ReplyLevelBadgeAsset.LEVEL_4
        level == 5 -> ReplyLevelBadgeAsset.LEVEL_5
        level == 6 -> ReplyLevelBadgeAsset.LEVEL_6
        else -> null
    }
}

internal fun parseCommentTimestampSeconds(match: MatchResult): Long? {
    val first = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val second = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val third = match.groupValues.getOrNull(3)?.toIntOrNull()
    return if (third != null) {
        first * 3600L + second * 60L + third
    } else {
        first * 60L + second
    }
}

internal fun collectRenderableEmoteKeys(
    text: String,
    emoteMap: Map<String, String>
): Set<String> {
    if (text.isEmpty() || emoteMap.isEmpty()) return emptySet()
    return EMOTE_TOKEN_PATTERN.findAll(text)
        .map { it.value }
        .filter { emoteMap.containsKey(it) }
        .toSet()
}

internal fun shouldEnableRichCommentSelection(
    hasRenderableEmotes: Boolean = false,
    hasInteractiveAnnotations: Boolean = false
): Boolean = true

// 纯 Text 标签渲染成本低；滚动/播放期间保持稳定显示，对齐底栏 dragFloor 不因 motion 切换可见性。
@Suppress("UNUSED_PARAMETER")
internal fun shouldShowReplySpecialLabel(
    lightweightMode: Boolean
): Boolean = true

internal fun shouldShowReplyIdentityDecorations(enabled: Boolean): Boolean = enabled

internal fun shouldShowReplySubPreview(
    hideSubPreview: Boolean,
    lightweightMode: Boolean
): Boolean = !hideSubPreview

internal fun normalizeCollapsedSubReplyPreviewLimit(value: Int): Int = value.coerceIn(1, 10)

internal fun resolveReplySpecialLabelText(
    cardLabels: List<ReplyCardLabel>?,
    showUpFlag: Boolean,
    upAction: ReplyUpAction?
): String? {
    val serverLabel = cardLabels.orEmpty()
        .asSequence()
        .map { it.textContent.trim() }
        .firstOrNull { it.isNotEmpty() }
    if (!serverLabel.isNullOrEmpty()) return serverLabel
    return if (showUpFlag && upAction?.like == true) "UP主觉得很赞" else null
}

internal fun resolveReplyDisplayLikeCount(
    baseLikeCount: Int,
    initialAction: Int,
    isLiked: Boolean
): Int {
    return when {
        isLiked && initialAction != 1 -> baseLikeCount + 1
        !isLiked && initialAction == 1 -> baseLikeCount - 1
        else -> baseLikeCount
    }
}

internal fun resolveReplyLocationText(location: String?): String? {
    if (location.isNullOrBlank()) return null
    val cleanLocation = location
        .removePrefix("IP属地：")
        .removePrefix("IP属地")
        .trim()
    return if (cleanLocation.isNotEmpty()) "IP归属地：$cleanLocation" else null
}

internal fun buildSubReplyPreviewPrefix(
    userName: String,
    isUpComment: Boolean,
    officialVerifyTone: OfficialVerifyBadgeTone? = null
): List<String> {
    return buildList {
        add(userName)
        when (officialVerifyTone) {
            OfficialVerifyBadgeTone.PERSONAL -> {
                add(" ")
                add("[VERIFY_PERSONAL]")
            }
            OfficialVerifyBadgeTone.ORGANIZATION -> {
                add(" ")
                add("[VERIFY_ORGANIZATION]")
            }
            null -> Unit
        }
        if (isUpComment) {
            add(" ")
            add("[UP]")
        }
        add(": ")
    }
}

internal fun resolveReplyItemContentType(item: ReplyItem): String {
    return when {
        !item.cardLabels.isNullOrEmpty() -> "reply_labeled"
        !item.content.pictures.isNullOrEmpty() -> "reply_media"
        !item.replies.isNullOrEmpty() || item.rcount > 0 -> "reply_thread"
        else -> "reply_plain"
    }
}

internal fun shouldShowReplyTopBadge(
    item: ReplyItem,
    isPinned: Boolean
): Boolean = isPinned || item.replyControl?.isUpTop == true

internal fun shouldShowReplyTopAction(
    currentMid: Long,
    upMid: Long,
    item: ReplyItem
): Boolean {
    return currentMid > 0L && currentMid == upMid && item.root == 0L
}

internal fun resolveReplyTopActionLabel(isCurrentlyTop: Boolean): String {
    return if (isCurrentlyTop) "取消置顶" else "置顶"
}

internal fun resolveReplyThreadCount(item: ReplyItem): Int {
    return maxOf(
        item.count,
        item.rcount,
        item.replies.orEmpty().size
    ).coerceAtLeast(0)
}

internal fun shouldOpenReplyThreadFromRootClick(item: ReplyItem): Boolean {
    return resolveReplyThreadCount(item) > 0
}

internal fun resolveSubReplyPreviewSummaryLabel(
    replyCount: Int,
    hasUpReply: Boolean
): String {
    val count = replyCount.coerceAtLeast(0)
    return if (hasUpReply) {
        "UP主等人 共${count}条回复"
    } else {
        "共${count}条回复"
    }
}

internal fun resolveSubReplyOpenTargetId(rootReplyId: Long, clickedReplyId: Long): Long {
    return clickedReplyId.takeIf { it > 0L && it != rootReplyId } ?: 0L
}

internal fun resolveReplyCommentShareUrl(item: ReplyItem): String {
    val rootId = if (item.root > 0L) item.root else item.rpid
    return buildString {
        append("https://www.bilibili.com/video/av")
        append(item.oid)
        append("?comment_on=1&comment_root_id=")
        append(rootId)
        if (item.root > 0L && item.rpid > 0L) {
            append("&comment_secondary_id=")
            append(item.rpid)
        }
    }
}

internal fun buildReplyCommentShareText(item: ReplyItem): String {
    return buildString {
        append(item.member.uname.ifBlank { "未知用户" })
        append(": ")
        append(item.content.message.trim())
        val url = resolveReplyCommentShareUrl(item)
        if (url.isNotBlank()) {
            append('\n')
            append(url)
        }
    }
}

internal fun resolveReplyMemberMid(item: ReplyItem): Long {
    return item.member.mid.toLongOrNull()?.takeIf { it > 0L }
        ?: item.mid.takeIf { it > 0L }
        ?: 0L
}

internal fun shouldSupportReplyShare(item: ReplyItem): Boolean {
    return item.replyControl?.supportShare ?: true
}

internal enum class ReplyActionSheetAction {
    COPY_ALL,
    FREE_COPY,
    COPY_USERNAME,
    QUERY_AUTHOR_HISTORY,
    SAVE,
    SHARE,
    REPLY,
    BLOCK_USER,
    REPORT,
    CHECK_FRAUD,
    TOGGLE_TOP,
    DELETE
}

internal fun buildReplyActionSheetActions(
    canDelete: Boolean,
    canReport: Boolean,
    canShare: Boolean,
    canBlockUser: Boolean,
    topActionLabel: String? = null,
    canCopyUsername: Boolean = true,
    canQueryAuthorHistory: Boolean = false,
): List<ReplyActionSheetAction> {
    return buildList {
        add(ReplyActionSheetAction.COPY_ALL)
        add(ReplyActionSheetAction.FREE_COPY)
        if (canCopyUsername) {
            add(ReplyActionSheetAction.COPY_USERNAME)
        }
        if (canQueryAuthorHistory) add(ReplyActionSheetAction.QUERY_AUTHOR_HISTORY)
        add(ReplyActionSheetAction.SAVE)
        if (canShare) {
            add(ReplyActionSheetAction.SHARE)
        }
        add(ReplyActionSheetAction.REPLY)
        if (canBlockUser) {
            add(ReplyActionSheetAction.BLOCK_USER)
        }
        if (canReport) {
            add(ReplyActionSheetAction.REPORT)
        }
        if (canDelete) {
            add(ReplyActionSheetAction.CHECK_FRAUD)
        }
        if (!topActionLabel.isNullOrBlank()) {
            add(ReplyActionSheetAction.TOGGLE_TOP)
        }
        if (canDelete) {
            add(ReplyActionSheetAction.DELETE)
        }
    }
}

private fun resolveReplyActionSheetLabel(
    action: ReplyActionSheetAction,
    topActionLabel: String?
): String {
    return when (action) {
        ReplyActionSheetAction.COPY_ALL -> "复制全部"
        ReplyActionSheetAction.FREE_COPY -> "自由复制"
        ReplyActionSheetAction.COPY_USERNAME -> "复制用户名"
        ReplyActionSheetAction.QUERY_AUTHOR_HISTORY -> "查询作者历史"
        ReplyActionSheetAction.SAVE -> "保存评论"
        ReplyActionSheetAction.SHARE -> "分享评论"
        ReplyActionSheetAction.REPLY -> "回复"
        ReplyActionSheetAction.BLOCK_USER -> "屏蔽用户"
        ReplyActionSheetAction.REPORT -> "举报"
        ReplyActionSheetAction.CHECK_FRAUD -> "检测评论状态"
        ReplyActionSheetAction.TOGGLE_TOP -> topActionLabel.orEmpty()
        ReplyActionSheetAction.DELETE -> "删除"
    }
}

private fun isReplyActionDestructive(action: ReplyActionSheetAction): Boolean {
    return action == ReplyActionSheetAction.REPORT ||
        action == ReplyActionSheetAction.BLOCK_USER ||
        action == ReplyActionSheetAction.DELETE
}

internal fun resolveReplyCommentImageSaveToast(success: Boolean): String {
    return if (success) "评论图片已保存到相册" else "保存评论图片失败"
}

internal data class ReplyVideoReference(
    val bvid: String,
    val navigationUrl: String
)

internal fun resolveReplyVideoReference(text: String): ReplyVideoReference? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val parsed = BilibiliUrlParser.parse(trimmed)
    val bvid = parsed.bvid?.takeIf { it.isNotBlank() } ?: return null
    val standaloneReference = trimmed.equals(bvid, ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("bilibili://", ignoreCase = true) ||
        trimmed.startsWith("b23.tv", ignoreCase = true) ||
        trimmed.startsWith("www.bilibili.com", ignoreCase = true) ||
        trimmed.startsWith("m.bilibili.com", ignoreCase = true)
    if (!standaloneReference) return null

    return ReplyVideoReference(
        bvid = bvid,
        navigationUrl = resolveReplyVideoNavigationUrl(bvid)
    )
}

internal fun resolveReplyVideoDisplayText(
    resolvedTitle: String?,
    fallbackText: String
): String = resolvedTitle?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackText.trim()

internal fun resolveReplyVideoNavigationUrl(bvid: String): String =
    "https://www.bilibili.com/video/$bvid"

internal fun resolveReplyTopicNavigationUrl(topic: String): String {
    val encoded = URLEncoder.encode(topic, StandardCharsets.UTF_8.name())
    return "bilibili://search?keyword=$encoded"
}

internal fun resolveReplyContentUrlNavigationUrl(
    rawToken: String,
    url: ReplyContentUrl
): String {
    // 动态/图文链接的 app_url_schema 偶尔会被服务端下发成 bilibili://video/{动态ID}。
    // 先保留可解析为动态的 Web URL，避免把动态 ID 当视频 aid 打开。
    listOf(url.url, rawToken).firstOrNull(::isReplyDynamicNavigationUrl)?.let { return it }
    return listOf(
        url.appUrlSchema,
        url.url,
        rawToken
    ).firstOrNull { it.isNotBlank() }.orEmpty()
}

private fun isReplyDynamicNavigationUrl(value: String): Boolean {
    val target = value.trim().takeIf { it.isNotEmpty() } ?: return false
    val parsed = BilibiliUrlParser.parse(target)
    return parsed.getVideoId() == null && parsed.getDynamicTargetId() != null
}

internal fun resolveReplyContentUrlDisplayText(
    rawToken: String,
    url: ReplyContentUrl
): String {
    return url.title.trim().takeIf { it.isNotEmpty() } ?: rawToken
}

internal fun resolveReplyContentUrlPrefixInlineId(rawToken: String): String {
    return "comment_url_prefix_${rawToken.hashCode()}"
}

internal data class ReplyLeadingRichTextReference(
    val label: String,
    val navigationUrl: String
)

internal fun resolveReplyNoteNavigationUrl(noteCvidStr: String): String {
    val cvid = noteCvidStr.trim().removePrefix("cv").toLongOrNull()?.takeIf { it > 0L }
    return if (cvid != null) "https://www.bilibili.com/read/cv$cvid" else ""
}

internal fun resolveReplyOpusNavigationUrl(opusId: Long): String {
    return if (opusId > 0L) "https://www.bilibili.com/opus/$opusId" else ""
}

internal fun resolveReplyLeadingRichTextReferences(
    content: ReplyContent?,
    noteCvidStr: String = ""
): List<ReplyLeadingRichTextReference> {
    content ?: return emptyList()
    val noteUrl = content.richText.note?.clickUrl?.takeIf { it.isNotBlank() }
        ?: resolveReplyNoteNavigationUrl(noteCvidStr).takeIf { it.isNotBlank() }
    if (!noteUrl.isNullOrBlank()) {
        return listOf(ReplyLeadingRichTextReference(label = "笔记 ", navigationUrl = noteUrl))
    }

    val opusUrl = resolveReplyOpusNavigationUrl(content.richText.opus?.opusId ?: 0L)
    return if (opusUrl.isNotBlank()) {
        listOf(ReplyLeadingRichTextReference(label = "笔记 ", navigationUrl = opusUrl))
    } else {
        emptyList()
    }
}

internal fun resolveReplyVoteDisplayText(
    voteId: Long,
    title: String?
): String {
    val cleanTitle = title?.trim().orEmpty()
    return if (cleanTitle.isNotEmpty()) "投票: $cleanTitle" else "投票: $voteId"
}

internal suspend fun resolveReplyVideoTitle(
    reference: ReplyVideoReference?,
    cache: MutableMap<String, String>,
    titleProvider: suspend (String) -> String?
): String? {
    reference ?: return null
    val cached = cache[reference.bvid]?.trim().takeUnless { it.isNullOrEmpty() }
    if (cached != null) return cached

    val resolved = titleProvider(reference.bvid)?.trim().takeUnless { it.isNullOrEmpty() }
    if (resolved != null) {
        cache[reference.bvid] = resolved
    }
    return resolved
}

internal fun buildRichCommentAnnotatedString(
    text: String,
    prefix: AnnotatedString? = null,
    renderableEmoteKeys: Set<String> = emptySet(),
    richUrls: Map<String, ReplyContentUrl> = emptyMap(),
    atNameToMid: Map<String, Long> = emptyMap(),
    topics: Set<String> = emptySet(),
    voteTitle: String? = null,
    leadingReferences: List<ReplyLeadingRichTextReference> = emptyList(),
    maxTimestampSeconds: Long? = null,
    color: Color = Color.Unspecified,
    timestampColor: Color = Color.Unspecified,
    urlColor: Color = Color.Unspecified
): AnnotatedString {
    return buildAnnotatedString {
        if (prefix != null) {
            append(prefix)
        }
        leadingReferences.forEach { reference ->
            if (reference.navigationUrl.isNotBlank()) {
                pushStringAnnotation(tag = COMMENT_URL_TAG, annotation = reference.navigationUrl)
                withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                    append(reference.label)
                }
                pop()
            }
        }

        val replyPattern = "^回复 @(.*?) :".toRegex()
        val replyMatch = replyPattern.find(text)
        var startIndex = 0
        if (replyMatch != null) {
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                append(replyMatch.value)
            }
            startIndex = replyMatch.range.last + 1
        }

        val remainingText = text.substring(startIndex)

        data class MatchInfo(
            val range: IntRange,
            val type: String,
            val value: String,
            val seconds: Long = 0L,
            val annotation: String = value,
            val displayText: String = value,
            val inlineContentId: String? = null,
            val priority: Int = 5
        )

        val allMatches = mutableListOf<MatchInfo>()

        fun addExactTokenMatch(
            token: String,
            type: String,
            annotation: String = token,
            displayText: String = token,
            inlineContentId: String? = null,
            priority: Int = 0
        ) {
            if (token.isBlank()) return
            var searchStart = 0
            while (searchStart < remainingText.length) {
                val index = remainingText.indexOf(token, startIndex = searchStart)
                if (index < 0) break
                allMatches.add(
                    MatchInfo(
                        range = index until index + token.length,
                        type = type,
                        value = token,
                        annotation = annotation,
                        displayText = displayText,
                        inlineContentId = inlineContentId,
                        priority = priority
                    )
                )
                searchStart = index + token.length
            }
        }

        richUrls.forEach { (token, url) ->
            addExactTokenMatch(
                token = token,
                type = "rich_url",
                annotation = resolveReplyContentUrlNavigationUrl(token, url),
                displayText = resolveReplyContentUrlDisplayText(token, url),
                inlineContentId = url.prefixIcon.takeIf { it.isNotBlank() }?.let {
                    resolveReplyContentUrlPrefixInlineId(token)
                },
                priority = 0
            )
        }

        atNameToMid.forEach { (name, mid) ->
            if (mid > 0L) {
                addExactTokenMatch(
                    token = "@$name",
                    type = "user",
                    annotation = mid.toString(),
                    priority = 0
                )
            }
        }

        topics.forEach { topic ->
            addExactTokenMatch(
                token = "#$topic#",
                type = "topic",
                annotation = topic,
                priority = 0
            )
        }

        EMOTE_TOKEN_PATTERN.findAll(remainingText).forEach { match ->
            allMatches.add(MatchInfo(match.range, "emote", match.value, priority = 1))
        }

        COMMENT_TIMESTAMP_PATTERN.findAll(remainingText).forEach { match ->
            val totalSeconds = parseCommentTimestampSeconds(match) ?: return@forEach
            if (maxTimestampSeconds != null && totalSeconds > maxTimestampSeconds) return@forEach
            allMatches.add(MatchInfo(match.range, "timestamp", match.value, totalSeconds, priority = 2))
        }

        COMMENT_VOTE_PATTERN.findAll(remainingText).forEach { match ->
            val voteId = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@forEach
            allMatches.add(
                MatchInfo(
                    range = match.range,
                    type = "vote",
                    value = match.value,
                    annotation = voteId.toString(),
                    displayText = resolveReplyVoteDisplayText(voteId, voteTitle),
                    priority = 2
                )
            )
        }

        COMMENT_URL_PATTERN.findAll(remainingText).forEach { match ->
            allMatches.add(MatchInfo(match.range, "url", match.value, priority = 3))
        }

        COMMENT_INLINE_BVID_PATTERN.findAll(remainingText).forEach { match ->
            val bvid = BilibiliUrlParser.parse(match.value).bvid?.takeIf { it.isNotBlank() } ?: return@forEach
            allMatches.add(
                MatchInfo(
                    range = match.range,
                    type = "video",
                    value = match.value,
                    annotation = resolveReplyVideoNavigationUrl(bvid),
                    priority = 4
                )
            )
        }

        allMatches.sortWith(compareBy<MatchInfo> { it.range.first }.thenBy { it.priority })

        var lastIndex = 0
        allMatches.forEach { matchInfo ->
            if (lastIndex < matchInfo.range.first) {
                append(remainingText.substring(lastIndex, matchInfo.range.first))
            }
            if (matchInfo.range.first >= lastIndex) {
                when (matchInfo.type) {
                    "emote" -> {
                        if (matchInfo.value in renderableEmoteKeys) {
                            appendInlineContent(id = matchInfo.value, alternateText = matchInfo.value)
                        } else {
                            append(matchInfo.value)
                        }
                    }

                    "timestamp" -> {
                        pushStringAnnotation(tag = COMMENT_TIMESTAMP_TAG, annotation = matchInfo.seconds.toString())
                        withStyle(SpanStyle(color = timestampColor, fontWeight = FontWeight.Medium)) {
                            append(matchInfo.value)
                        }
                        pop()
                    }

                    "user" -> {
                        pushStringAnnotation(tag = COMMENT_USER_TAG, annotation = matchInfo.annotation)
                        withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                            append(matchInfo.value)
                        }
                        pop()
                    }

                    "topic" -> {
                        pushStringAnnotation(tag = COMMENT_TOPIC_TAG, annotation = matchInfo.annotation)
                        withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                            append(matchInfo.value)
                        }
                        pop()
                    }

                    "vote" -> {
                        pushStringAnnotation(tag = COMMENT_VOTE_TAG, annotation = matchInfo.annotation)
                        withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                            append(matchInfo.displayText)
                        }
                        pop()
                    }

                    "url",
                    "rich_url",
                    "video" -> {
                        pushStringAnnotation(tag = COMMENT_URL_TAG, annotation = matchInfo.annotation)
                        withStyle(SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)) {
                            if (matchInfo.inlineContentId != null) {
                                appendInlineContent(id = matchInfo.inlineContentId, alternateText = " ")
                            }
                            append(matchInfo.displayText)
                        }
                        pop()
                    }
                }
                lastIndex = matchInfo.range.last + 1
            }
        }

        if (lastIndex < remainingText.length) {
            append(remainingText.substring(lastIndex))
        }
    }
}

internal fun resolveVisibleSubReplies(
    replies: List<ReplyItem>?,
    expanded: Boolean,
    collapsedLimit: Int = COLLAPSED_SUB_REPLY_PREVIEW_LIMIT
): List<ReplyItem> {
    val previewReplies = replies.orEmpty()
    val limit = normalizeCollapsedSubReplyPreviewLimit(collapsedLimit)
    return if (expanded) previewReplies else previewReplies.take(limit)
}

internal fun resolveInitialSubReplyPreviewExpanded(
    @Suppress("UNUSED_PARAMETER") previewReplyCount: Int
): Boolean = false

internal fun shouldShowInlineSubReplyToggle(
    previewReplyCount: Int,
    collapsedLimit: Int = COLLAPSED_SUB_REPLY_PREVIEW_LIMIT
): Boolean = previewReplyCount > normalizeCollapsedSubReplyPreviewLimit(collapsedLimit)

internal fun resolveInlineSubReplyToggleLabel(expanded: Boolean): String {
    return if (expanded) "收起回复" else "展开回复"
}

internal data class FanGroupTagVisual(
    val fanNumber: String,
    val cardBgImageUrl: String?,
    val fanColorHex: String = ""
)

internal fun resolveSailingFan(cardBgs: List<ReplySailingCardBg>): ReplySailingFan? {
    return cardBgs.asSequence()
        .mapNotNull { it.fan }
        .firstOrNull { it.numDesc.isNotBlank() || it.number > 0 }
}

internal fun resolveSailingDecorationImage(cardBgs: List<ReplySailingCardBg>): String? {
    return cardBgs.asSequence()
        .map { it.image }
        .firstOrNull { it.isNotBlank() }
}

internal fun resolveFanGroupDecorationCardBgs(member: ReplyMember): List<ReplySailingCardBg> {
    return listOfNotNull(
        member.userSailingV2?.cardBgWithFocus,
        member.userSailing?.cardBgWithFocus,
        member.userSailingV2?.cardBg,
        member.userSailing?.cardBg
    )
}

/**
 * 评论接口会同时携带传统 [ReplyMember.pendant] 与 user_sailing 的新挂件。
 * 优先使用 v2 的增强帧，能保留官方透明挂件的完整轮廓；旧字段作为兼容回退。
 */
internal fun resolveReplyMemberPendantImage(member: ReplyMember): String? {
    return sequenceOf(
        member.userSailingV2?.pendant,
        member.userSailing?.pendant,
        member.pendant
    )
        .flatMap { pendant ->
            sequenceOf(
                pendant?.imageEnhanceFrame,
                pendant?.imageEnhance,
                pendant?.image
            )
        }
        .map(::normalizeHttpImageUrl)
        .firstOrNull { it.isNotBlank() }
}

internal fun resolveFanGroupTagVisual(
    fan: ReplySailingFan?,
    cardBgImage: String?,
    fanColorHex: String? = null
): FanGroupTagVisual? {
    fan ?: return null
    val fanNumber = fan.numDesc.ifBlank {
        if (fan.number > 0) fan.number.toString().padStart(6, '0') else ""
    }
    if (fanNumber.isBlank()) return null
    return FanGroupTagVisual(
        fanNumber = fanNumber,
        cardBgImageUrl = cardBgImage?.takeIf { it.isNotBlank() },
        fanColorHex = fanColorHex?.takeIf { it.isNotBlank() } ?: fan.color
    )
}

internal fun resolveFanGroupLabelText(fanNumber: String): String {
    val digits = fanNumber.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return "CO.${digits.padStart(6, '0')}"
}

internal fun resolveFanGroupNumberText(fanNumber: String): String {
    val digits = fanNumber.filter(Char::isDigit)
    return digits.takeIf { it.isNotBlank() }?.padStart(6, '0').orEmpty()
}

internal fun resolveFanGroupLabelTextColor(
    fanColorHex: String?,
    backgroundColor: Color,
    fallbackColor: Color,
    minimumContrast: Float = 4.5f
): Color {
    val candidate = parseHexColorOrNull(fanColorHex) ?: return fallbackColor
    return if (calculateContrastRatio(candidate, backgroundColor) >= minimumContrast) {
        candidate
    } else {
        fallbackColor
    }
}

internal fun resolveFanGroupVisualFromMemberAndSailing(
    member: ReplyMember,
    cardBgs: List<ReplySailingCardBg>
): FanGroupTagVisual? {
    val sailingFan = resolveSailingFan(cardBgs)
    val legacyNumber = member.garbCardNumber.trim()
    val sailingNumber = sailingFan?.numDesc?.ifBlank {
        if (sailingFan.number > 0) sailingFan.number.toString().padStart(6, '0') else ""
    }.orEmpty()
    val fanNumber = when {
        sailingNumber.isNotBlank() -> sailingNumber
        legacyNumber.isNotBlank() -> legacyNumber
        else -> ""
    }
    if (fanNumber.isBlank()) return null

    val image = when {
        member.garbCardImageWithFocus.isNotBlank() -> member.garbCardImageWithFocus
        member.garbCardImage.isNotBlank() -> member.garbCardImage
        else -> resolveSailingDecorationImage(cardBgs).orEmpty()
    }
    val fanColorHex = member.garbCardFanColor
        .takeIf { it.isNotBlank() }
        ?: sailingFan?.color
        ?: ""

    return FanGroupTagVisual(
        fanNumber = fanNumber,
        cardBgImageUrl = image.takeIf { it.isNotBlank() },
        fanColorHex = fanColorHex
    )
}

internal fun resolveReplyPreviewTextContent(
    item: ReplyItem,
    isLiked: Boolean = item.action == 1,
    onLikeClick: (() -> Unit)? = null,
    onReplyClick: (() -> Unit)? = null
): ImagePreviewTextContent {
    val originalSizeLabels = item.content.pictures.orEmpty().map { picture ->
        resolveCommentImageOriginalSizeLabel(picture.imgSize.takeIf { it > 0f })
    }
    return ImagePreviewTextContent(
        headline = item.member.uname,
        body = item.content.message,
        placement = ImagePreviewTextPlacement.TOP_BAR,
        commentContext = ImagePreviewCommentContext(
            replyId = item.rpid,
            authorName = item.member.uname,
            avatarUrl = item.member.avatar,
            timeText = formatTime(item.ctime),
            body = item.content.message,
            originalSizeLabels = originalSizeLabels,
            likeCount = item.like,
            liked = isLiked,
            onLikeClick = onLikeClick,
            onReplyClick = onReplyClick
        )
    )
}

//  优化后的颜色常量 (使用 MaterialTheme 替代硬编码)
// private val SubReplyBgColor = Color(0xFFF7F8FA)  // OLD
// private val TextSecondaryColor = Color(0xFF9499A0)  // OLD
// private val TextTertiaryColor = Color(0xFFB2B7BF)   // OLD

@Composable
fun ReplyHeader(count: Int) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = "评论",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            text = FormatUtils.formatStat(count.toLong()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReplyItemView(
    item: ReplyItem,
    upMid: Long = 0,
    showUpFlag: Boolean = false,
    isPinned: Boolean = false,
    emoteMap: Map<String, String> = emptyMap(),
    lightweightMode: Boolean = false,
    showIdentityDecorations: Boolean = true,
    onClick: () -> Unit,
    onSubClick: (ReplyItem, Long) -> Unit,
    onTimestampClick: ((Long) -> Unit)? = null,
    onImagePreview: ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit)? = null,
    isLiked: Boolean = item.action == 1,
    onLikeClick: (() -> Unit)? = null,
    isHated: Boolean = item.action == 2,
    onHateClick: (() -> Unit)? = null,
    onReplyClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onCheckFraudClick: (() -> Unit)? = null,
    onReportClick: ((Int) -> Unit)? = null,
    canToggleTop: Boolean = false,
    onToggleTopClick: (() -> Unit)? = null,
    location: String? = item.replyControl?.location,
    onUrlClick: ((String) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    hideSubPreview: Boolean = false,
    onAvatarClick: (String) -> Unit
) {
    val appearance = rememberVideoCommentAppearance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isUpComment = upMid > 0 && item.mid == upMid
    val showResolvedIdentityDecorations = shouldShowReplyIdentityDecorations(showIdentityDecorations)
    val showSubPreview = shouldShowReplySubPreview(
        hideSubPreview = hideSubPreview,
        lightweightMode = lightweightMode
    )
    val collapsedSubReplyPreviewLimit = remember(context) {
        SettingsManager.getCommentCollapsedReplyPreviewLimitSync(context)
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
    val displayLocation = remember(location) {
        resolveReplyLocationText(location)
    }
    val metadataText = remember(item.ctime, displayLocation) {
        buildString {
            append(formatTime(item.ctime))
            if (!displayLocation.isNullOrEmpty()) {
                append(" · $displayLocation")
            }
        }
    }
    val showTopBadge = shouldShowReplyTopBadge(item = item, isPinned = isPinned)
    val layoutPolicy = remember { resolveReplyItemLayoutPolicy() }
    val contentPrefix = remember(showTopBadge) {
        if (!showTopBadge) {
            null
        } else {
            buildAnnotatedString {
                appendInlineContent(COMMENT_INLINE_TOP_BADGE_ID, "TOP")
                append(" ")
            }
        }
    }
    val specialLabelText = remember(item.cardLabels, showUpFlag, item.upAction) {
        if (!shouldShowReplySpecialLabel(lightweightMode)) return@remember null
        resolveReplySpecialLabelText(
            cardLabels = item.cardLabels,
            showUpFlag = showUpFlag,
            upAction = item.upAction
        )
    }
    val displayLikeCount = remember(item.like, item.action, isLiked) {
        resolveReplyDisplayLikeCount(
            baseLikeCount = item.like,
            initialAction = item.action,
            isLiked = isLiked
        )
    }
    val fansDetail = if (showResolvedIdentityDecorations) {
        item.member.fansDetail?.takeIf { it.medalName.isNotBlank() && it.level > 0 }
    } else {
        null
    }
    val nameplateImage = if (showResolvedIdentityDecorations) {
        item.member.nameplate?.imageSmall?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val sailingCardBgs = if (showResolvedIdentityDecorations) {
        resolveFanGroupDecorationCardBgs(item.member)
    } else {
        emptyList()
    }
    val fanGroupVisual = if (showResolvedIdentityDecorations) {
        resolveFanGroupVisualFromMemberAndSailing(
            member = item.member,
            cardBgs = sailingCardBgs
        )
    } else {
        null
    }
    val biliPaiDecoration = fanGroupVisual
    val replyOfficialBadge = remember(item.member.officialVerify) {
        resolveOfficialVerifyBadge(
            type = item.member.officialVerify.type,
            desc = item.member.officialVerify.desc,
            compact = true
        )
    }
    var isSubPreviewExpanded by remember(item.rpid, item.replies) {
        mutableStateOf(
            resolveInitialSubReplyPreviewExpanded(
                previewReplyCount = item.replies.orEmpty().size
            )
        )
    }
    val visibleSubReplies = remember(item.replies, isSubPreviewExpanded, collapsedSubReplyPreviewLimit) {
        resolveVisibleSubReplies(
            replies = item.replies,
            expanded = isSubPreviewExpanded,
            collapsedLimit = collapsedSubReplyPreviewLimit
        )
    }
    val showInlineSubReplyToggle = remember(item.replies, collapsedSubReplyPreviewLimit) {
        shouldShowInlineSubReplyToggle(
            previewReplyCount = item.replies.orEmpty().size,
            collapsedLimit = collapsedSubReplyPreviewLimit
        )
    }
    val threadReplyCount = remember(item.count, item.rcount, item.replies) {
        resolveReplyThreadCount(item)
    }
    val hasUpSubReply = remember(item.replyControl?.upReply, item.replies, upMid) {
        item.replyControl?.upReply == true || (upMid > 0 && item.replies.orEmpty().any { it.mid == upMid })
    }
    val subReplySummaryLabel = remember(threadReplyCount, hasUpSubReply) {
        resolveSubReplyPreviewSummaryLabel(
            replyCount = threadReplyCount,
            hasUpReply = hasUpSubReply
        )
    }
    val openThreadFromRootClick = remember(item.count, item.rcount, item.replies) {
        shouldOpenReplyThreadFromRootClick(item)
    }
    val copyToClipboard = rememberClipboardCopyHandler()
    val blockedUpRepository = remember(context) { BlockedUpRepository(context) }
    var showActionSheet by remember(item.rpid) { mutableStateOf(false) }
    var showFreeCopyDialog by remember(item.rpid) { mutableStateOf(false) }
    var showReportDialog by remember(item.rpid) { mutableStateOf(false) }
    var pendingSaveReply by remember(item.rpid) { mutableStateOf<ReplyItem?>(null) }
    // [新增] 评论翻译状态
    val canTranslate = item.replyControl?.translationSwitch == 2
    var translatedMessage by remember(item.rpid) { mutableStateOf<String?>(null) }
    var isTranslating by remember(item.rpid) { mutableStateOf(false) }
    val displayMessage = remember(translatedMessage, item.content.message) {
        translatedMessage ?: item.content.message
    }
    val copyText = remember(item.content.message) { item.content.message.trim() }
    val replyMemberMid = remember(item.member.mid, item.mid) { resolveReplyMemberMid(item) }
    fun launchSaveReplyCommentImage(reply: ReplyItem) {
        scope.launch {
            val success = saveReplyCommentImageToGallery(context, reply)
            Toast.makeText(
                context,
                resolveReplyCommentImageSaveToast(success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val storagePermission = rememberStoragePermissionState { granted ->
        val pending = pendingSaveReply
        pendingSaveReply = null
        if (granted && pending != null) {
            launchSaveReplyCommentImage(pending)
        }
    }
    fun requestSaveReplyCommentImage() {
        if (storagePermission.isGranted) {
            launchSaveReplyCommentImage(item)
        } else {
            pendingSaveReply = item
            storagePermission.request()
        }
    }
    fun shareReplyComment() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "分享评论")
            putExtra(Intent.EXTRA_TEXT, buildReplyCommentShareText(item))
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享评论"))
    }
    fun blockReplyUser() {
        scope.launch {
            val result = blockedUpRepository.blockUpWithBilibiliSync(
                mid = replyMemberMid,
                name = item.member.uname,
                face = item.member.avatar,
                relationSource = BlockedUpRelationSource.COMMENT
            )
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showActionSheet) {
        ReplyActionSheet(
            queryAuthorUid = replyMemberMid,
            canDelete = onDeleteClick != null,
            canReport = onReportClick != null,
            canShare = shouldSupportReplyShare(item),
            canBlockUser = replyMemberMid > 0L,
            canCopyUsername = item.member.uname.isNotBlank(),
            topActionLabel = if (canToggleTop) resolveReplyTopActionLabel(showTopBadge) else null,
            onDismiss = { showActionSheet = false },
            onCopyAll = {
                copyToClipboard(copyText, "评论内容")
            },
            onFreeCopy = {
                showFreeCopyDialog = true
            },
            onCopyUsername = {
                copyToClipboard(item.member.uname, "用户名")
            },
            onSave = {
                requestSaveReplyCommentImage()
            },
            onShare = {
                shareReplyComment()
            },
            onReply = {
                onReplyClick?.invoke() ?: onSubClick(item, 0L)
            },
            onBlockUser = {
                blockReplyUser()
            },
            onReport = {
                showReportDialog = true
            },
            onToggleTop = {
                onToggleTopClick?.invoke()
            },
            onCheckFraud = {
                onCheckFraudClick?.invoke()
            },
            onDelete = {
                onDeleteClick?.invoke()
            }
        )
    }

    if (showFreeCopyDialog) {
        TextSelectionBottomSheet(
            text = copyText,
            title = "选择评论内容",
            onDismiss = { showFreeCopyDialog = false }
        )
    }

    ReportReasonDialog(
        visible = showReportDialog,
        onDismiss = { showReportDialog = false },
        onReport = { reason ->
            onReportClick?.invoke(reason)
            showReportDialog = false
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.panelColor)
            .combinedClickable(
                onClick = {
                    if (openThreadFromRootClick) {
                        onSubClick(item, 0L)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                    showActionSheet = true
                }
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 10.dp,
                    bottom = 10.dp,
                    start = layoutPolicy.horizontalPaddingDp.dp,
                    end = layoutPolicy.horizontalPaddingDp.dp
                )
        ) {
            val headerEndPadding = resolveReplyItemHeaderEndPaddingDp(
                hasBiliPaiDecoration = biliPaiDecoration != null,
                policy = layoutPolicy
            ).dp
            val startPadding = resolveReplyItemContentStartPaddingDp(
                containerWidth = maxWidth,
                policy = layoutPolicy
            ).dp

            Column(modifier = Modifier.fillMaxWidth()) {
                // User Info Header
                Row() {
                    ReplyMemberAvatar(
                        member = item.member,
                        placeholderColor = appearance.placeholderColor,
                        lightweightMode = lightweightMode,
                        modifier = Modifier.size(layoutPolicy.avatarSizeDp.dp),
                        onClick = { onAvatarClick(item.member.mid) }
                    )

                    Spacer(modifier = Modifier.width(layoutPolicy.avatarContentSpacingDp.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = headerEndPadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 用户名不挂长按复制：列表滑动时易误触连复制多个用户名。
                            // 需要时用评论长按菜单「复制用户名」。
                            AppText(
                                text = item.member.uname,
                                fontSize = VideoCommentTypographyTokens.author,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.member.vip?.vipStatus == 1) {
                                    appearance.accentColor
                                } else {
                                    appearance.primaryTextColor.copy(alpha = 0.9f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (replyOfficialBadge != null) {
                                OfficialVerifyBadge(
                                    badge = replyOfficialBadge,
                                    compact = true
                                )
                            }

                            if (item.member.levelInfo.currentLevel > 0) {
                                LevelTag(
                                    level = item.member.levelInfo.currentLevel,
                                    isSeniorMember = item.member.isSeniorMember == 1
                                )
                            }

                            if (isUpComment) {
                                UpTag()
                            }

                            if (fansDetail != null) {
                                FansMedalTag(detail = fansDetail)
                            }

                            if (!nameplateImage.isNullOrBlank()) {
                                NameplateTag(imageUrl = nameplateImage)
                            }
                        }

                        AppText(
                            text = metadataText,
                            fontSize = VideoCommentTypographyTokens.metadata,
                            lineHeight = 16.sp,
                            color = appearance.secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = startPadding)
                ) {
                    ReplyMessageText(
                        text = displayMessage,
                        fontSize = VideoCommentTypographyTokens.body,
                        color = appearance.primaryTextColor,
                        emoteMap = localEmoteMap,
                        content = item.content,
                        onTimestampClick = onTimestampClick,
                        maxTimestampMs = maxTimestampMs,
                        onUrlClick = onUrlClick,
                        onUserClick = { mid -> onAvatarClick(mid.toString()) },
                        onTopicClick = { topic -> onUrlClick?.invoke(resolveReplyTopicNavigationUrl(topic)) },
                        onVoteClick = { voteId -> onUrlClick?.invoke("bilibili://vote?id=$voteId") },
                        noteCvidStr = item.noteCvidStr,
                        prefix = contentPrefix,
                        onPlainTextClick = if (openThreadFromRootClick) {
                            { onSubClick(item, 0L) }
                        } else {
                            null
                        }
                    )

                    // Images
                    if (!item.content.pictures.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CommentPictures(
                            pictures = item.content.pictures,
                            onImageClick = { images, index, rect ->
                                onImagePreview?.invoke(
                                    images,
                                    index,
                                    rect,
                                    resolveReplyPreviewTextContent(
                                        item = item,
                                        isLiked = isLiked,
                                        onLikeClick = onLikeClick,
                                        onReplyClick = onReplyClick
                                    )
                                )
                            }
                        )
                    }

                    // Footer Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    ReplyTextAction(
                        label = "回复",
                        appearance = appearance,
                        onClick = { onReplyClick?.invoke() ?: onSubClick(item, 0L) }
                    )

                    // [新增] 翻译按钮 (胶囊样式)
                    if (canTranslate) {
                        val isTranslated = translatedMessage != null
                        val translateLabel = if (isTranslating) "翻译中" else if (isTranslated) "原文" else "翻译"
                        AppSurface(
                            shape = AppShapes.container(ContainerLevel.Pill),
                            color = if (isTranslated) appearance.accentColor.copy(alpha = 0.14f) else appearance.actionTint.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clickable(enabled = !isTranslating) {
                                    if (isTranslated) {
                                        translatedMessage = null
                                    } else {
                                        scope.launch {
                                            isTranslating = true
                                            val result = com.android.purebilibili.data.repository.CommentGrpcRepository.translateReply(
                                                type = item.replyType.toLong(),
                                                oid = item.oid,
                                                rpid = item.rpid
                                            )
                                            result.onSuccess { translated ->
                                                if (!translated.isNullOrBlank()) {
                                                    translatedMessage = translated
                                                } else {
                                                    Toast.makeText(context, "翻译结果为空", Toast.LENGTH_SHORT).show()
                                                }
                                            }.onFailure { e ->
                                                Toast.makeText(context, "${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                            isTranslating = false
                                        }
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Translate,
                                    contentDescription = null,
                                    tint = if (isTranslated) appearance.accentColor else appearance.actionTint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                AppText(
                                    text = translateLabel,
                                    fontSize = VideoCommentTypographyTokens.action,
                                    color = if (isTranslated) appearance.accentColor else appearance.actionTint
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (!specialLabelText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        ReplySpecialLabelChip(text = specialLabelText)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val likeFilledIcon = rememberAppLikeFilledIcon()

                    // Like
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = onLikeClick != null) { onLikeClick?.invoke() }
                            .padding(4.dp)
                    ) {
                        AppIcon(
                            imageVector = likeFilledIcon,
                            contentDescription = "Like",
                            tint = if (isLiked) appearance.accentColor else appearance.actionTint,
                            modifier = Modifier.size(16.dp)
                        )
                        if (displayLikeCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(
                                text = FormatUtils.formatStat(displayLikeCount.toLong()),
                                fontSize = VideoCommentTypographyTokens.actionCount,
                                color = if (isLiked) appearance.accentColor else appearance.actionTint
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    AppIconButton(
                        onClick = { onHateClick?.invoke() },
                        enabled = onHateClick != null
                    ) {
                        AppIcon(
                            imageVector = Icons.Filled.ThumbDown,
                            contentDescription = if (isHated) "取消点踩" else "点踩评论",
                            tint = if (isHated) MaterialTheme.colorScheme.error else appearance.actionTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // [新增] 删除按钮 (仅显示给本人)
                    if (onDeleteClick != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        AppIconButton(onClick = onDeleteClick) {
                            AppIcon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = appearance.actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Sub-comments (Threaded view)
                if (showSubPreview && (!item.replies.isNullOrEmpty() || item.rcount > 0)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(durationMillis = 180))
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .background(appearance.composerHintBackgroundColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        visibleSubReplies.forEach { subReply ->
                            val subEmoteMap = remember(subReply.content.emote, emoteMap) {
                                val inlineEmotes = subReply.content.emote.orEmpty()
                                if (inlineEmotes.isEmpty()) {
                                    emoteMap
                                } else {
                                    buildMap(emoteMap.size + inlineEmotes.size) {
                                        putAll(emoteMap)
                                        inlineEmotes.forEach { (key, value) -> put(key, value.url) }
                                    }
                                }
                            }

                            val subReplyOfficialBadge = remember(subReply.member.officialVerify) {
                                resolveOfficialVerifyBadge(
                                    type = subReply.member.officialVerify.type,
                                    desc = subReply.member.officialVerify.desc,
                                    compact = true
                                )
                            }
                            val prefixTokens = remember(
                                subReply.member.uname,
                                upMid,
                                subReply.mid,
                                subReplyOfficialBadge?.tone
                            ) {
                                buildSubReplyPreviewPrefix(
                                    userName = subReply.member.uname,
                                    isUpComment = upMid > 0 && subReply.mid == upMid,
                                    officialVerifyTone = subReplyOfficialBadge?.tone
                                )
                            }
                            val prefixTextColor = appearance.primaryTextColor.copy(alpha = 0.8f)
                            val prefixSeparatorColor = appearance.secondaryTextColor
                            val upTagColor = appearance.accentColor
                            val prefix = remember(prefixTokens, prefixTextColor, prefixSeparatorColor, upTagColor) {
                                buildAnnotatedString {
                                    prefixTokens.forEach { token ->
                                        when (token) {
                                            "[VERIFY_PERSONAL]" -> appendInlineContent(
                                                COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID,
                                                "个人"
                                            )
                                            "[VERIFY_ORGANIZATION]" -> appendInlineContent(
                                                COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID,
                                                "机构"
                                            )
                                            "[UP]" -> withStyle(
                                                SpanStyle(color = upTagColor)
                                            ) {
                                                appendInlineContent(COMMENT_INLINE_UP_BADGE_ID, "UP")
                                            }
                                            ": " -> withStyle(
                                                SpanStyle(color = prefixSeparatorColor)
                                            ) {
                                                append(token)
                                            }
                                            else -> withStyle(
                                                SpanStyle(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = prefixTextColor
                                                )
                                            ) {
                                                append(token)
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX${subReply.rpid}")
                                    .combinedClickable(
                                        onClick = {
                                            onSubClick(
                                                item,
                                                resolveSubReplyOpenTargetId(item.rpid, subReply.rpid)
                                            )
                                        },
                                        onLongClick = {
                                            copyToClipboard(
                                                subReply.content.message,
                                                "回复内容"
                                            )
                                        }
                                    )
                            ) {
                                ReplyMessageText(
                                    text = subReply.content.message,
                                    fontSize = VideoCommentTypographyTokens.subReply,
                                    color = appearance.primaryTextColor.copy(alpha = 0.8f),
                                    emoteMap = subEmoteMap,
                                    content = subReply.content,
                                    maxLines = 2,
                                    onTimestampClick = onTimestampClick,
                                    maxTimestampMs = maxTimestampMs,
                                    onUrlClick = onUrlClick,
                                    onUserClick = { mid -> onAvatarClick(mid.toString()) },
                                    onTopicClick = { topic -> onUrlClick?.invoke(resolveReplyTopicNavigationUrl(topic)) },
                                    onVoteClick = { voteId -> onUrlClick?.invoke("bilibili://vote?id=$voteId") },
                                    noteCvidStr = subReply.noteCvidStr,
                                    prefix = prefix,
                                    onPlainTextClick = {
                                        onSubClick(
                                            item,
                                            resolveSubReplyOpenTargetId(item.rpid, subReply.rpid)
                                        )
                                    }
                                )
                            }
                        }

                        if (showInlineSubReplyToggle) {
                            AppText(
                                text = resolveInlineSubReplyToggleLabel(expanded = isSubPreviewExpanded),
                                fontSize = VideoCommentTypographyTokens.subReply,
                                color = appearance.accentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { isSubPreviewExpanded = !isSubPreviewExpanded }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        if (threadReplyCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX${item.rpid}")
                                    .clickable { onSubClick(item, 0L) },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                AppText(
                                    text = subReplySummaryLabel,
                                    fontSize = VideoCommentTypographyTokens.subReply,
                                    color = appearance.accentColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        if (biliPaiDecoration != null) {
            FanGroupDecorationBadge(
                visual = biliPaiDecoration,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 2.dp,
                        end = layoutPolicy.actionButtonSizeDp.dp
                    )
            )
        }

        AppIconButton(
            onClick = { showActionSheet = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(layoutPolicy.actionButtonSizeDp.dp)
                .testTag("$COMMENT_ACTION_BUTTON_TAG_PREFIX${item.rpid}")
        ) {
            AppIcon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "评论操作",
                tint = appearance.actionTint,
                modifier = Modifier.size(18.dp)
            )
        }
        
        AppHorizontalDivider(
            modifier = Modifier.padding(start = layoutPolicy.dividerStartPaddingDp.dp),
            thickness = 0.5.dp,
            color = appearance.dividerColor.copy(alpha = 0.25f)
        )
    }
}

/**
 * 评论文本：普通文本继续走 RichCommentText；
 * 若整条评论是视频引用，则异步解析标题后替换为主题色标题。
 */
@Composable
internal fun ReplyMessageText(
    text: String,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emoteMap: Map<String, String>,
    content: ReplyContent? = null,
    maxLines: Int = Int.MAX_VALUE,
    onTimestampClick: ((Long) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    onUrlClick: ((String) -> Unit)? = null,
    onUserClick: ((Long) -> Unit)? = null,
    onTopicClick: ((String) -> Unit)? = null,
    onVoteClick: ((Long) -> Unit)? = null,
    noteCvidStr: String = "",
    prefix: AnnotatedString? = null,
    onPlainTextClick: (() -> Unit)? = null
) {
    val videoReference = remember(text) { resolveReplyVideoReference(text) }
    var resolvedTitle by remember(videoReference?.bvid) {
        mutableStateOf(videoReference?.bvid?.let(replyVideoTitleCache::get))
    }

    LaunchedEffect(videoReference?.bvid) {
        val reference = videoReference ?: return@LaunchedEffect
        if (!resolvedTitle.isNullOrBlank()) return@LaunchedEffect
        resolvedTitle = resolveReplyVideoTitle(
            reference = reference,
            cache = replyVideoTitleCache,
            titleProvider = { bvid ->
                VideoRepository.getVideoTitle(bvid).getOrNull()
            }
        )
    }

    if (videoReference != null && !resolvedTitle.isNullOrBlank()) {
        ReplyVideoReferenceText(
            text = resolveReplyVideoDisplayText(
                resolvedTitle = resolvedTitle,
                fallbackText = text
            ),
            fontSize = fontSize,
            maxLines = maxLines,
            url = videoReference.navigationUrl,
            onUrlClick = onUrlClick,
            prefix = prefix,
            copyText = text.trim()
        )
    } else {
        RichCommentText(
            text = text,
            fontSize = fontSize,
            color = color,
            emoteMap = emoteMap,
            content = content,
            maxLines = maxLines,
            onTimestampClick = onTimestampClick,
            maxTimestampMs = maxTimestampMs,
            onUrlClick = onUrlClick,
            onUserClick = onUserClick,
            onTopicClick = onTopicClick,
            onVoteClick = onVoteClick,
            noteCvidStr = noteCvidStr,
            prefix = prefix,
            onPlainTextClick = onPlainTextClick
        )
    }
}

@Composable
private fun ReplyVideoReferenceText(
    text: String,
    fontSize: TextUnit,
    maxLines: Int,
    url: String,
    onUrlClick: ((String) -> Unit)?,
    prefix: AnnotatedString? = null,
    copyText: String = text
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(text, url, prefix, primaryColor) {
        buildAnnotatedString {
            if (prefix != null) {
                append(prefix)
            }
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(text)
            }
            pop()
        }
    }
    val upBadgeInlineContent = rememberInlineUpBadgeContent()
    val topBadgeInlineContent = rememberInlineTopBadgeContent()
    val personalVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.PERSONAL)
    val organizationVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.ORGANIZATION)
    val inlineContent = remember(
        upBadgeInlineContent,
        topBadgeInlineContent,
        personalVerifyInlineContent,
        organizationVerifyInlineContent
    ) {
        mapOf(
            COMMENT_INLINE_UP_BADGE_ID to upBadgeInlineContent,
            COMMENT_INLINE_TOP_BADGE_ID to topBadgeInlineContent,
            COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID to personalVerifyInlineContent,
            COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID to organizationVerifyInlineContent
        )
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showTextSelectionSheet by remember(copyText) { mutableStateOf(false) }
    val modifier = if (onUrlClick != null) {
        Modifier.pointerInput(annotatedString, url) {
            detectTapGestures(
                onLongPress = {
                    if (copyText.isNotEmpty()) {
                        showTextSelectionSheet = true
                    }
                },
                onTap = { offset ->
                    textLayoutResult?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        annotatedString.getStringAnnotations(
                            tag = COMMENT_URL_TAG,
                            start = maxOf(0, position - 1),
                            end = minOf(annotatedString.length, position + 1)
                        ).firstOrNull()?.let { annotation ->
                            onUrlClick(annotation.item)
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    AppText(
        text = annotatedString,
        inlineContent = inlineContent,
        fontSize = fontSize,
        color = primaryColor,
        lineHeight = (fontSize.value * 1.5).sp,
        maxLines = maxLines,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier
    )

    if (showTextSelectionSheet) {
        TextSelectionBottomSheet(
            text = copyText,
            title = "选择评论内容",
            onDismiss = { showTextSelectionSheet = false }
        )
    }
}

/**
 *  [新增] 富文本评论组件
 * 支持：表情渲染、时间戳点击跳转
 */
@Composable
fun RichCommentText(
    text: String,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emoteMap: Map<String, String>,
    content: ReplyContent? = null,
    maxLines: Int = Int.MAX_VALUE,
    onTimestampClick: ((Long) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    onUrlClick: ((String) -> Unit)? = null,
    onUserClick: ((Long) -> Unit)? = null,
    onTopicClick: ((String) -> Unit)? = null,
    onVoteClick: ((Long) -> Unit)? = null,
    noteCvidStr: String = "",
    prefix: AnnotatedString? = null,
    onPlainTextClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val timestampColor = MaterialTheme.colorScheme.primary
    val urlColor = MaterialTheme.colorScheme.primary
    
    val renderableEmoteKeys = remember(text, emoteMap) {
        collectRenderableEmoteKeys(text, emoteMap)
    }
    
    val annotatedString = remember(
        text,
        prefix,
        renderableEmoteKeys,
        content?.urls,
        content?.atNameToMid,
        content?.topics,
        content?.vote,
        content?.richText,
        noteCvidStr,
        maxTimestampMs,
        timestampColor,
        color,
        urlColor
    ) {
        buildRichCommentAnnotatedString(
            text = text,
            prefix = prefix,
            renderableEmoteKeys = renderableEmoteKeys,
            richUrls = content?.urls.orEmpty(),
            atNameToMid = content?.atNameToMid.orEmpty(),
            topics = content?.topics.orEmpty().keys,
            voteTitle = content?.vote?.title,
            leadingReferences = resolveReplyLeadingRichTextReferences(content, noteCvidStr),
            maxTimestampSeconds = maxTimestampMs?.let { it / 1000L },
            color = color,
            timestampColor = timestampColor,
            urlColor = urlColor
        )
    }

    val upBadgeInlineContent = rememberInlineUpBadgeContent()
    val topBadgeInlineContent = rememberInlineTopBadgeContent()
    val personalVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.PERSONAL)
    val organizationVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.ORGANIZATION)
    val inlineContent = remember(
        renderableEmoteKeys,
        emoteMap,
        content?.urls,
        context,
        urlColor,
        upBadgeInlineContent,
        topBadgeInlineContent,
        personalVerifyInlineContent,
        organizationVerifyInlineContent
    ) {
        buildMap {
            renderableEmoteKeys.forEach { key ->
                val url = emoteMap[key].orEmpty()
                put(
                    key,
                    InlineTextContent(
                        Placeholder(width = 1.4.em, height = 1.4.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
            content?.urls.orEmpty().forEach { (token, richUrl) ->
                val prefixIcon = richUrl.prefixIcon.takeIf { it.isNotBlank() } ?: return@forEach
                put(
                    resolveReplyContentUrlPrefixInlineId(token),
                    InlineTextContent(
                        Placeholder(
                            width = 1.15.em,
                            height = 1.15.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(FormatUtils.fixImageUrl(prefixIcon))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(urlColor),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
            put(COMMENT_INLINE_UP_BADGE_ID, upBadgeInlineContent)
            put(COMMENT_INLINE_TOP_BADGE_ID, topBadgeInlineContent)
            put(COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID, personalVerifyInlineContent)
            put(COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID, organizationVerifyInlineContent)
        }
    }
    
    val hasInteractiveAnnotations =
        onTimestampClick != null ||
            onUrlClick != null ||
            onUserClick != null ||
            onTopicClick != null ||
            onVoteClick != null
    val hasTapHandler = hasInteractiveAnnotations || onPlainTextClick != null
    val selectionEnabled = remember(renderableEmoteKeys, hasTapHandler) {
        shouldEnableRichCommentSelection(
            hasRenderableEmotes = renderableEmoteKeys.isNotEmpty(),
            hasInteractiveAnnotations = hasTapHandler
        )
    }
    val copyText = remember(text) { text.trim() }
    var showTextSelectionSheet by remember(copyText) { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        //  使用 Text + pointerInput 实现带表情的可点击文本
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        val textModifier = if (hasTapHandler) {
            Modifier.pointerInput(annotatedString, text, onPlainTextClick) {
                detectTapWithSelectionFriendly { offset ->
                    textLayoutResult?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        val searchStart = maxOf(0, position - 1)
                        val searchEnd = minOf(annotatedString.length, position + 1)

                        annotatedString.getStringAnnotations(
                            tag = COMMENT_URL_TAG,
                            start = searchStart,
                            end = searchEnd
                        ).firstOrNull()?.let { annotation ->
                            onUrlClick?.invoke(annotation.item)
                            return@detectTapWithSelectionFriendly
                        }

                        annotatedString.getStringAnnotations(
                            tag = COMMENT_USER_TAG,
                            start = searchStart,
                            end = searchEnd
                        ).firstOrNull()?.let { annotation ->
                            annotation.item.toLongOrNull()?.let { onUserClick?.invoke(it) }
                            return@detectTapWithSelectionFriendly
                        }

                        annotatedString.getStringAnnotations(
                            tag = COMMENT_TOPIC_TAG,
                            start = searchStart,
                            end = searchEnd
                        ).firstOrNull()?.let { annotation ->
                            onTopicClick?.invoke(annotation.item)
                            return@detectTapWithSelectionFriendly
                        }

                        annotatedString.getStringAnnotations(
                            tag = COMMENT_VOTE_TAG,
                            start = searchStart,
                            end = searchEnd
                        ).firstOrNull()?.let { annotation ->
                            annotation.item.toLongOrNull()?.let { onVoteClick?.invoke(it) }
                            return@detectTapWithSelectionFriendly
                        }

                        annotatedString.getStringAnnotations(
                            tag = COMMENT_TIMESTAMP_TAG,
                            start = searchStart,
                            end = searchEnd
                        )
                        .firstOrNull()?.let { annotation ->
                            val secondsValue = annotation.item.toLongOrNull() ?: 0L
                            onTimestampClick?.invoke(secondsValue * 1000)
                            return@detectTapWithSelectionFriendly
                        }
                    }
                    onPlainTextClick?.invoke()
                }
            }
        } else {
            Modifier
        }

        AppText(
            text = annotatedString,
            inlineContent = inlineContent,
            fontSize = fontSize,
            color = color,
            lineHeight = (fontSize.value * 1.5).sp,
            maxLines = maxLines,
            onTextLayout = { textLayoutResult = it },
            modifier = textModifier
        )
    }

    SelectionContainer {
        content()
    }

    if (showTextSelectionSheet) {
        TextSelectionBottomSheet(
            text = copyText,
            title = "选择评论内容",
            onDismiss = { showTextSelectionSheet = false }
        )
    }
}

@Composable
private fun rememberInlineTopBadgeContent(): InlineTextContent {
    return remember {
        InlineTextContent(
            Placeholder(
                width = 2.5.em,
                height = 1.15.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TopTag()
            }
        }
    }
}

@Composable
private fun rememberInlineUpBadgeContent(): InlineTextContent {
    return remember {
        InlineTextContent(
            Placeholder(
                width = 1.7.em,
                height = 1.15.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                UserUpBadge()
            }
        }
    }
}

@Composable
private fun rememberInlineOfficialVerifyBadgeContent(
    tone: OfficialVerifyBadgeTone
): InlineTextContent {
    return remember(tone) {
        InlineTextContent(
            Placeholder(
                width = 2.2.em,
                height = 1.15.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OfficialVerifyBadge(
                    badge = OfficialVerifyBadgeSpec(
                        text = when (tone) {
                            OfficialVerifyBadgeTone.PERSONAL -> "个人"
                            OfficialVerifyBadgeTone.ORGANIZATION -> "机构"
                        },
                        contentDescription = when (tone) {
                            OfficialVerifyBadgeTone.PERSONAL -> "个人认证"
                            OfficialVerifyBadgeTone.ORGANIZATION -> "机构认证"
                        },
                        tone = tone
                    ),
                    compact = true
                )
            }
        }
    }
}

/**
 *  [兼容] 旧版 EmojiText (保持向后兼容)
 */
@Composable
fun EmojiText(
    text: String,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emoteMap: Map<String, String>
) {
    RichCommentText(
        text = text,
        fontSize = fontSize,
        color = color,
        emoteMap = emoteMap,
        onTimestampClick = null
    )
}

//  评论等级标签（BiliPai pixel badge with text fallback）
@Composable
fun LevelTag(level: Int, isSeniorMember: Boolean = false) {
    UserLevelBadge(
        level = level,
        isSeniorMember = isSeniorMember
    )
}

@Composable
private fun FansMedalTag(detail: ReplyFansDetail) {
    val accentColor = resolveFansMedalColor(detail.level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(AppShapes.container(ContainerLevel.Tag))
            .border(
                width = 0.8.dp,
                color = accentColor.copy(alpha = 0.75f),
                shape = AppShapes.container(ContainerLevel.Tag)
            )
            .background(accentColor.copy(alpha = 0.14f))
    ) {
        AppText(
            text = detail.medalName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = accentColor.copy(alpha = 0.95f),
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, end = 3.dp, top = 1.dp, bottom = 1.dp)
        )
        Box(
            modifier = Modifier
                .background(accentColor)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = detail.level.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun NameplateTag(imageUrl: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(FormatUtils.fixImageUrl(imageUrl))
            .crossfade(true)
            .build(),
        contentDescription = "Nameplate",
        modifier = Modifier
            .size(width = 20.dp, height = 12.dp)
            .clip(AppShapes.container(ContainerLevel.Tag))
    )
}

@Composable
internal fun ReplyMemberAvatar(
    member: ReplyMember,
    placeholderColor: Color,
    lightweightMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val pendantImageUrl = remember(member) { resolveReplyMemberPendantImage(member) }
    val hasPendant = !pendantImageUrl.isNullOrBlank()
    val faceFraction = resolveReplyAvatarFaceFraction(hasPendant)
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        // Face first (under), slightly smaller when a frame is present.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(FormatUtils.fixImageUrl(member.avatar))
                .crossfade(!lightweightMode)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(faceFraction)
                .clip(CircleShape)
                .background(placeholderColor)
        )
        // Frame/pendant on top so the ring sits around the face.
        if (hasPendant) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(pendantImageUrl)
                    .crossfade(!lightweightMode)
                    .build(),
                contentDescription = "Avatar pendant",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        UserAvatarCornerMarkBadge(
            mark = resolveUserAvatarCornerMark(
                officialType = member.officialVerify.type,
                vipStatus = member.vip?.vipStatus,
            ),
            modifier = Modifier.align(Alignment.BottomEnd),
            badgeSize = 14.dp,
        )
    }
}

@Composable
internal fun FanGroupDecorationBadge(
    visual: FanGroupTagVisual,
    modifier: Modifier = Modifier
) {
    val layoutPolicy = remember { resolveReplyItemLayoutPolicy() }
    val primaryImageUrl = resolveDecorationImageUrl(visual.cardBgImageUrl)

    val fanNumberText = remember(visual.fanNumber) { resolveFanGroupNumberText(visual.fanNumber) }
    if (fanNumberText.isBlank() && primaryImageUrl.isBlank()) return
    Row(
        modifier = modifier.widthIn(min = layoutPolicy.decorationMinWidthDp.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (primaryImageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(primaryImageUrl)
                    // 身份装扮只在小尺寸区域显示；限制解码大小，避免异常原图在 Canvas 绘制时崩溃。
                    .size(COMMENT_DECORATION_DECODE_MAX_PX, COMMENT_DECORATION_DECODE_MAX_PX)
                    .transformations(TransparentBoundsCropTransformation)
                    .crossfade(true)
                    .build(),
                contentDescription = "Fan group decoration",
                // cardbg 是包含编号与角色图案的透明整图，裁剪会截掉官方素材边缘。
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .size(
                        width = layoutPolicy.decorationImageWidthDp.dp,
                        height = layoutPolicy.decorationImageHeightDp.dp
                    )
            )
        }
        if (fanNumberText.isNotBlank()) {
            val textColor = resolveFanGroupLabelTextColor(
                fanColorHex = visual.fanColorHex,
                backgroundColor = MaterialTheme.colorScheme.surface,
                fallbackColor = MaterialTheme.colorScheme.onSurface
            )
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                AppText(
                    text = "NO.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor,
                    maxLines = 1
                )
                AppText(
                    text = fanNumberText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BiliPaiGarbCardDecoration(
    visual: FanGroupTagVisual,
    modifier: Modifier = Modifier
) {
    FanGroupDecorationBadge(
        visual = visual,
        modifier = modifier
    )
}

internal fun normalizeHttpImageUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val text = url.trim()
    val lower = text.lowercase(Locale.ROOT)
    val looksLikeHostPath = !text.startsWith("/") &&
        text.substringBefore('/').contains('.')
    return when {
        text.startsWith("//") -> "https:$text"
        lower.startsWith("http://") -> "https://${text.substringAfter("://")}"
        lower.startsWith("https://") -> text
        looksLikeHostPath -> "https://$text"
        else -> text
    }
}

internal fun resolveDecorationImageUrl(url: String?): String {
    return normalizeHttpImageUrl(url)
}

private fun resolveFansMedalColor(level: Int): Color {
    return when {
        level >= 30 -> Color(0xFFE67A2B)
        level >= 20 -> Color(0xFFD9963B)
        level >= 10 -> Color(0xFFCFA657)
        else -> Color(0xFFB6B6B6)
    }
}

private fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val text = hex.trim().removePrefix("#")
    val argb = when (text.length) {
        6 -> "FF$text"
        8 -> text
        else -> return null
    }
    return runCatching { Color(argb.toLong(16).toInt()) }.getOrNull()
}

// 评论行组合期热路径：共享 formatter，避免每条评论格式化时间都新建 SimpleDateFormat。
// 仅主线程（Compose 组合）调用，不涉及 SimpleDateFormat 的线程安全问题。
private val replyPublishDayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

fun formatTime(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    return replyPublishDayFormatter.format(date)
}

@Composable
internal fun ReplySpecialLabelChip(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ReplyTextAction(
    label: String,
    appearance: VideoCommentAppearance,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clickable { onClick() }
            .padding(end = 8.dp)
    ) {
        AppIcon(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            contentDescription = null,
            tint = appearance.actionTint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        AppText(
            text = label,
            fontSize = VideoCommentTypographyTokens.action,
            color = appearance.actionTint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyActionSheet(
    queryAuthorUid: Long = 0L,
    canDelete: Boolean,
    canReport: Boolean,
    canShare: Boolean = true,
    canBlockUser: Boolean = false,
    canCopyUsername: Boolean = true,
    topActionLabel: String? = null,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onFreeCopy: () -> Unit,
    onCopyUsername: () -> Unit = {},
    onSave: () -> Unit,
    onShare: () -> Unit = {},
    onReply: () -> Unit,
    onBlockUser: () -> Unit = {},
    onReport: () -> Unit,
    onCheckFraud: () -> Unit = {},
    onToggleTop: () -> Unit,
    onDelete: () -> Unit
) {
    val queryAicu = com.android.purebilibili.feature.aicu.LocalAicuNavigation.current
    val canQueryAuthorHistory = queryAicu != null && queryAuthorUid > 0
    val actions = remember(
        canQueryAuthorHistory,
        canDelete,
        canReport,
        canShare,
        canBlockUser,
        canCopyUsername,
        topActionLabel,
    ) {
        buildReplyActionSheetActions(
            canDelete = canDelete,
            canReport = canReport,
            canShare = canShare,
            canBlockUser = canBlockUser,
            topActionLabel = topActionLabel,
            canCopyUsername = canCopyUsername,
            canQueryAuthorHistory = canQueryAuthorHistory,
        )
    }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            actions.forEach { action ->
                ReplyActionSheetItem(
                    label = resolveReplyActionSheetLabel(action, topActionLabel),
                    isDestructive = isReplyActionDestructive(action),
                    onClick = {
                        when (action) {
                            ReplyActionSheetAction.COPY_ALL -> onCopyAll()
                            ReplyActionSheetAction.FREE_COPY -> onFreeCopy()
                            ReplyActionSheetAction.COPY_USERNAME -> onCopyUsername()
                            ReplyActionSheetAction.QUERY_AUTHOR_HISTORY -> queryAicu?.invoke(queryAuthorUid)
                            ReplyActionSheetAction.SAVE -> onSave()
                            ReplyActionSheetAction.SHARE -> onShare()
                            ReplyActionSheetAction.REPLY -> onReply()
                            ReplyActionSheetAction.BLOCK_USER -> onBlockUser()
                            ReplyActionSheetAction.REPORT -> onReport()
                            ReplyActionSheetAction.CHECK_FRAUD -> onCheckFraud()
                            ReplyActionSheetAction.TOGGLE_TOP -> onToggleTop()
                            ReplyActionSheetAction.DELETE -> onDelete()
                        }
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ReplyActionSheetItem(
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    AppText(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

//  UP 标签 - BiliPai small badge style
@Composable
fun UpTag() {
    UserUpBadge()
}

@Composable
fun TopTag() {
    Box(
        modifier = Modifier
            .clip(AppShapes.container(ContainerLevel.Tag))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = AppShapes.container(ContainerLevel.Tag)
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        AppText(
            text = "TOP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

//  评论图片网格组件 - 支持 GIF 动画
//  [优化] 更新为匹配动态页面的视觉风格
@Composable
fun CommentPictures(
    pictures: List<ReplyPicture>,
    onImageClick: (List<String>, Int, ImagePreviewSourceAnchor?) -> Unit,
    testTagPrefix: String = COMMENT_PICTURE_TAG_PREFIX
) {
    //  获取高质量图片URL（移除分辨率限制参数）
    val imageUrls = remember(pictures) {
        pictures.map { pic ->
            var url = pic.imgSrc
            // 修复协议
            if (url.startsWith("//")) {
                url = "https:$url"
            } else if (url.startsWith("http://")) {
                url = url.replace("http://", "https://")
            }
            //  移除尺寸参数以获取原图（避免模糊）
            if (url.contains("@")) {
                url = url.substringBefore("@")
            }
            url
        }
    }
    val context = LocalContext.current
    val totalCount = pictures.size  //  [优化] 保存总图片数用于角标显示
    // 单图 Card / 九宫格 Field 的真实圆角不同，捕获时构造锚点供回位 morph 使用
    val singleImageCornerDp = AppShapes.containerCornerDp(ContainerLevel.Card).value
    val gridImageCornerDp = AppShapes.containerCornerDp(ContainerLevel.Field).value
    val thumbnailDecodeSize = remember {
        resolveImageDecodeSize(ImageDecodeTarget.COMMENT_THUMBNAIL)
    }
    
    //  GIF 图片加载器
    val gifImageLoader = context.imageLoader
    
    // 检测是否是 GIF
    fun isGif(url: String) = url.contains(".gif", ignoreCase = true)
    
    // 根据图片数量选择不同的布局
    when (pictures.size) {
        1 -> {
            // 单张图片：保持原始比例，限制最大尺寸
            val pic = pictures[0]
            //  [优化] 更好的比例计算
            val aspectRatio = if (pic.imgHeight > 0 && pic.imgWidth > 0) {
                (pic.imgWidth.toFloat() / pic.imgHeight.toFloat()).coerceIn(0.5f, 2f)
            } else {
                1.33f  // 默认 4:3 比例
            }
            var imageRect by remember { mutableStateOf<Rect?>(null) }
            val sourceHidden = isImagePreviewSourceHidden(imageRect)
            
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)  //  [优化] 增大最大宽度
                    .heightIn(max = 220.dp)
                    .testTag("${testTagPrefix}0")
                    .aspectRatio(aspectRatio)
                    .alpha(if (sourceHidden) 0f else 1f)
                    .clip(AppShapes.container(ContainerLevel.Card))  //  [优化] 更大圆角 8dp → 12dp
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onGloballyPositioned { coordinates ->
                        imageRect = coordinates.boundsInWindow()
                    }
                    .clickable(enabled = !sourceHidden) {
                        onImageClick(
                            imageUrls,
                            0,
                            imageRect?.let { ImagePreviewSourceAnchor(it, singleImageCornerDp) }
                        )
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrls[0])
                        .size(thumbnailDecodeSize.widthPx, thumbnailDecodeSize.heightPx)
                        .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())  //  必需
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    imageLoader = gifImageLoader,  //  支持 GIF 和其他格式
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        else -> {
            // 多张图片：网格布局
            val displayItems = pictures.take(9)  //  [优化] 最多显示9张
            val columns = when {
                displayItems.size <= 4 -> 2
                else -> 3
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {  //  [优化] 增加间距 4dp → 6dp
                displayItems.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, pic ->
                            val globalIndex = rowIndex * columns + colIndex
                            var imageRect by remember { mutableStateOf<Rect?>(null) }
                            val sourceHidden = isImagePreviewSourceHidden(imageRect)
                            
                            Box(
                                modifier = Modifier
                                    .size(85.dp)  //  [优化] 增大尺寸 80dp → 85dp
                                    .testTag("${testTagPrefix}$globalIndex")
                                    .alpha(if (sourceHidden) 0f else 1f)
                                    .clip(AppShapes.container(ContainerLevel.Field))  //  [优化] 更大圆角 6dp → 10dp
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .onGloballyPositioned { coordinates ->
                                        imageRect = coordinates.boundsInWindow()
                                    }
                                    .clickable(enabled = !sourceHidden) {
                                        onImageClick(
                                            imageUrls,
                                            globalIndex,
                                            imageRect?.let { ImagePreviewSourceAnchor(it, gridImageCornerDp) }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrls[globalIndex])
                                        .size(thumbnailDecodeSize.widthPx, thumbnailDecodeSize.heightPx)
                                        .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())  //  必需
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    imageLoader = gifImageLoader,  //  支持 GIF
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                //  [新增] 最后一张图片显示多图角标（如 +3）
                                if (globalIndex == displayItems.size - 1 && totalCount > 9) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppText(
                                            "+${totalCount - 9}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
