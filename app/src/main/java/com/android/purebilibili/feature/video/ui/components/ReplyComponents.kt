package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.AppContentColors

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.transform.Transformation
import coil3.imageLoader
//  已改用 AppContentColors.accent()
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.LocalDetailedCommentTimeEnabled
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.BilibiliUrlParser
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
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
import com.android.purebilibili.data.repository.COMMENT_EMOTE_TOKEN_PATTERN
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.resolveCommentPictureUrls
import com.android.purebilibili.data.repository.resolveCommentPictureGridColumns
import com.android.purebilibili.data.repository.resolveCommentRenderableEmoteKeys
import com.android.purebilibili.data.repository.resolveCommentSinglePictureAspectRatio
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceImage
import com.android.purebilibili.feature.dynamic.components.isImagePreviewOpen
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextPlacement
import com.android.purebilibili.feature.dynamic.components.ImagePreviewCommentContext
import com.android.purebilibili.feature.dynamic.components.ImageDecodeTarget
import com.android.purebilibili.feature.dynamic.components.ImageDecodeSize
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
import com.android.purebilibili.core.ui.components.resolveUpNameColor
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.common.AppSelectionContainer as SelectionContainer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.components.UserUpBadge
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

// 表情 token 规则下沉 core-data（CommentMediaPolicy），手机原入口保留并委托。
internal val EMOTE_TOKEN_PATTERN = COMMENT_EMOTE_TOKEN_PATTERN
private const val COMMENT_INLINE_UP_BADGE_ID = "comment_inline_up_badge"
private const val COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID = "comment_inline_verify_personal_badge"
private const val COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID = "comment_inline_verify_organization_badge"
internal const val COMMENT_INLINE_TOP_BADGE_ID = "comment_inline_top_badge"
private const val COMMENT_INLINE_CHARGED_BADGE_ID = "comment_inline_charged_badge"
internal const val COMMENT_URL_TAG = "URL"
internal const val COMMENT_TIMESTAMP_TAG = "TIMESTAMP"
internal const val COMMENT_USER_TAG = "USER"
internal const val COMMENT_TOPIC_TAG = "TOPIC"
internal const val COMMENT_VOTE_TAG = "VOTE"

/** 富评论原生链接 payload 前缀：LinkAnnotation.Clickable 用单一 tag 承载「类型:载荷」。 */
internal const val RICH_COMMENT_LINK_URL_PREFIX = "URL:"
internal const val RICH_COMMENT_LINK_USER_PREFIX = "USER:"
internal const val RICH_COMMENT_LINK_TOPIC_PREFIX = "TOPIC:"
internal const val RICH_COMMENT_LINK_VOTE_PREFIX = "VOTE:"
internal const val RICH_COMMENT_LINK_TS_PREFIX = "TS:"

/** 构建原生 [LinkAnnotation.Clickable]；框架在 Text 内部处理点击，天然优先于划选/条目长按。 */
internal fun commentLinkAnnotation(
    payload: String,
    listener: LinkInteractionListener?,
): LinkAnnotation = LinkAnnotation.Clickable(
    tag = payload,
    styles = null,
    linkInteractionListener = listener,
)

/** 评论富文本链接动作（纯数据，供分发与测试）。 */
internal sealed interface RichCommentLinkAction {
    data class Url(val url: String) : RichCommentLinkAction
    data class User(val mid: Long) : RichCommentLinkAction
    data class Topic(val topic: String) : RichCommentLinkAction
    data class Vote(val voteId: Long) : RichCommentLinkAction
    data class Timestamp(val seconds: Long) : RichCommentLinkAction
}

internal fun resolveRichCommentLinkAction(tag: String): RichCommentLinkAction? {
    return when {
        tag.startsWith(RICH_COMMENT_LINK_URL_PREFIX) ->
            RichCommentLinkAction.Url(tag.removePrefix(RICH_COMMENT_LINK_URL_PREFIX))
        tag.startsWith(RICH_COMMENT_LINK_USER_PREFIX) ->
            tag.removePrefix(RICH_COMMENT_LINK_USER_PREFIX).toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let(RichCommentLinkAction::User)
        tag.startsWith(RICH_COMMENT_LINK_TOPIC_PREFIX) ->
            tag.removePrefix(RICH_COMMENT_LINK_TOPIC_PREFIX)
                .takeIf { it.isNotBlank() }
                ?.let(RichCommentLinkAction::Topic)
        tag.startsWith(RICH_COMMENT_LINK_VOTE_PREFIX) ->
            tag.removePrefix(RICH_COMMENT_LINK_VOTE_PREFIX).toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let(RichCommentLinkAction::Vote)
        tag.startsWith(RICH_COMMENT_LINK_TS_PREFIX) ->
            tag.removePrefix(RICH_COMMENT_LINK_TS_PREFIX).toLongOrNull()
                ?.let(RichCommentLinkAction::Timestamp)
        else -> null
    }
}
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
): Set<String> = resolveCommentRenderableEmoteKeys(text, emoteMap)

/**
 * 是否挂载 SelectionContainer。
 *
 * 有可交互注解（@/链接/话题/投票/时间戳）时必须关闭划选：SelectionContainer
 * 在存在选区（长按复制后）会消费后续点击来清除选区，把注解点击静默吞掉；
 * 长按复制走条目级操作面板，不依赖划选容器。
 */
internal fun shouldEnableRichCommentSelection(
    hasRenderableEmotes: Boolean = false,
    hasInteractiveAnnotations: Boolean = false
): Boolean = !hasInteractiveAnnotations

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

internal fun resolveSubReplyPreviewContent(content: ReplyContent): ReplyContent {
    var previewUrls: MutableMap<String, ReplyContentUrl>? = null
    content.urls.forEach { (token, url) ->
        val target = BilibiliNavigationTargetParser.parse(resolveReplyContentUrlNavigationUrl(token, url))
        if (target is BilibiliNavigationTarget.Search) {
            // Keep the original keyword as plain text, without its search icon or link styling.
            val filtered = previewUrls ?: content.urls.toMutableMap().also { previewUrls = it }
            filtered.remove(token)
        }
    }
    return previewUrls?.let { content.copy(urls = it) } ?: content
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

/**
 * 已缓存全部回复时只使用本地展开/收起；折叠隐藏的预览不代表还有回复需要加载。
 */
internal fun shouldShowSubReplyPreviewSummary(
    replyCount: Int,
    previewReplyCount: Int
): Boolean = replyCount > 0 && previewReplyCount < replyCount

internal fun resolveSubReplyOpenTargetId(rootReplyId: Long, clickedReplyId: Long): Long {
    return clickedReplyId.takeIf { it > 0L && it != rootReplyId } ?: 0L
}

internal fun resolveReplyCommentShareUrl(
    item: ReplyItem,
    context: com.android.purebilibili.feature.share.ReplyShareContext? = null,
): String {
    val rootId = if (item.root > 0L) item.root else item.rpid
    val episodeId = context?.url?.let { Regex("/bangumi/play/ep(\\d+)").find(it)?.groupValues?.get(1) }
    if (item.replyType != 1 || episodeId != null) {
        val enterUri = if (episodeId != null) "bilibili://pgc/season/ep/$episodeId" else
            context?.url?.takeIf { it.isNotBlank() } ?: when (item.replyType) {
                11, 17 -> "bilibili://following/detail/${item.oid}"
                12 -> "https://www.bilibili.com/read/cv${item.oid}"
                14 -> "https://www.bilibili.com/audio/au${item.oid}"
                else -> ""
            }
        return buildString {
            append("bilibili://comment/detail/${item.replyType}/${item.oid}/$rootId/?")
            if (item.root > 0L && item.rpid > 0L) append("anchor=${item.rpid}&")
            append("enterUri=")
            append(java.net.URLEncoder.encode(enterUri, "UTF-8"))
        }
    }
    val base = context?.url?.takeIf { it.contains("/video/") }
        ?: "https://www.bilibili.com/video/av${item.oid}"
    return buildString {
        append(base)
        append(if ('?' in base) '&' else '?')
        append("comment_on=1&comment_root_id=$rootId")
        if (item.root > 0L && item.rpid > 0L) append("&comment_secondary_id=${item.rpid}")
        if (context != null && context.page > 1 && !Regex("[?&]p=\\d+").containsMatchIn(base)) append("&p=${context.page}")
    }
}

internal fun buildReplyCommentShareText(item: ReplyItem, context: com.android.purebilibili.feature.share.ReplyShareContext? = null): String {
    return buildString {
        append(item.member.uname.ifBlank { "未知用户" })
        append(": ")
        append(item.content.message.trim())
        val url = resolveReplyCommentShareUrl(item, context)
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

internal fun shouldShowReplyBlockUserAction(authorMid: Long, currentMid: Long?): Boolean {
    return authorMid > 0L && authorMid != currentMid
}

internal fun shouldSupportReplyShare(item: ReplyItem): Boolean {
    return item.replyControl?.supportShare ?: true
}

internal enum class ReplyActionSheetAction {
    COPY_ALL,
    FREE_COPY,
    COPY_USERNAME,
    QUERY_AUTHOR_HISTORY,
    TIME_STYLE,
    TOGGLE_REPLY_PREVIEW,
    SAVE,
    SHARE,
    REPLY,
    BLOCK_USER,
    REPORT,
    CHECK_FRAUD,
    TOGGLE_TOP
}

internal fun buildReplyActionSheetActions(
    canCheckFraud: Boolean,
    canReport: Boolean,
    canShare: Boolean,
    canBlockUser: Boolean,
    topActionLabel: String? = null,
    canCopyUsername: Boolean = true,
    canQueryAuthorHistory: Boolean = false,
    canToggleReplyPreview: Boolean = false,
): List<ReplyActionSheetAction> {
    return buildList {
        add(ReplyActionSheetAction.COPY_ALL)
        add(ReplyActionSheetAction.FREE_COPY)
        if (canCopyUsername) {
            add(ReplyActionSheetAction.COPY_USERNAME)
        }
        if (canQueryAuthorHistory) add(ReplyActionSheetAction.QUERY_AUTHOR_HISTORY)
        add(ReplyActionSheetAction.TIME_STYLE)
        if (canToggleReplyPreview) {
            add(ReplyActionSheetAction.TOGGLE_REPLY_PREVIEW)
        }
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
        if (canCheckFraud) {
            add(ReplyActionSheetAction.CHECK_FRAUD)
        }
        if (!topActionLabel.isNullOrBlank()) {
            add(ReplyActionSheetAction.TOGGLE_TOP)
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
        ReplyActionSheetAction.TIME_STYLE -> "评论时间样式"
        ReplyActionSheetAction.TOGGLE_REPLY_PREVIEW -> "显示回复"
        ReplyActionSheetAction.SAVE -> "存图 / 分享图片"
        ReplyActionSheetAction.SHARE -> "分享评论"
        ReplyActionSheetAction.REPLY -> "回复"
        ReplyActionSheetAction.BLOCK_USER -> "屏蔽用户"
        ReplyActionSheetAction.REPORT -> "举报"
        ReplyActionSheetAction.CHECK_FRAUD -> "检测评论状态"
        ReplyActionSheetAction.TOGGLE_TOP -> topActionLabel.orEmpty()
    }
}

private fun isReplyActionDestructive(action: ReplyActionSheetAction): Boolean {
    return action == ReplyActionSheetAction.REPORT ||
        action == ReplyActionSheetAction.BLOCK_USER
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
    // 服务端给商业链接附带的视频 schema 可能指向来源视频；有明确网页目标时优先保留。
    val webUrl = url.url.trim()
    val webUri = runCatching { java.net.URI(webUrl) }.getOrNull()
    if (webUri?.scheme?.lowercase() in listOf("http", "https") &&
        !com.android.purebilibili.core.util.isBilibiliShortHost(webUri?.host)
    ) return webUrl
    // 商业富链接可能提供宿主不支持的 app schema；不能让它覆盖可用的网页地址。
    val appSchema = url.appUrlSchema.trim()
    if (appSchema.isNotEmpty() &&
        com.android.purebilibili.core.util.BilibiliNavigationTargetParser.parse(appSchema) != null
    ) return appSchema
    listOf(url.url, rawToken, appSchema).firstOrNull { candidate ->
        val scheme = runCatching { java.net.URI(candidate.trim()).scheme }.getOrNull()
        scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
    }?.let { return it.trim() }
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
    urlColor: Color = Color.Unspecified,
    linkListener: LinkInteractionListener? = null
): AnnotatedString {
    return buildAnnotatedString {
        if (prefix != null) {
            append(prefix)
        }
        leadingReferences.forEach { reference ->
            if (reference.navigationUrl.isNotBlank()) {
                withLink(
                    commentLinkAnnotation(
                        payload = RICH_COMMENT_LINK_URL_PREFIX + reference.navigationUrl,
                        listener = linkListener,
                    )
                ) {
                    withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                        append(reference.label)
                    }
                }
            }
        }

        val replyPattern = "^回复 (@.+?)(\\s*[:：])".toRegex()
        val replyMatch = replyPattern.find(text)
        var startIndex = 0
        if (replyMatch != null) {
            val mention = replyMatch.groupValues[1]
            val mid = atNameToMid[mention.removePrefix("@")]
            withStyle(SpanStyle(color = color)) { append("回复 ") }
            // 回复前缀中的 @ 与正文提及共用强调色；只有有效用户 ID 才附加跳转。
            withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                if (mid != null && mid > 0L) {
                    withLink(commentLinkAnnotation(
                        payload = RICH_COMMENT_LINK_USER_PREFIX + mid,
                        listener = linkListener,
                    )) { append(mention) }
                } else {
                    append(mention)
                }
            }
            withStyle(SpanStyle(color = color)) { append(replyMatch.groupValues[2]) }
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
                        withLink(
                            commentLinkAnnotation(
                                payload = RICH_COMMENT_LINK_TS_PREFIX + matchInfo.seconds,
                                listener = linkListener,
                            )
                        ) {
                            withStyle(SpanStyle(color = timestampColor, fontWeight = FontWeight.Medium)) {
                                append(matchInfo.value)
                            }
                        }
                    }

                    "user" -> {
                        withLink(
                            commentLinkAnnotation(
                                payload = RICH_COMMENT_LINK_USER_PREFIX + matchInfo.annotation,
                                listener = linkListener,
                            )
                        ) {
                            withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                                append(matchInfo.value)
                            }
                        }
                    }

                    "topic" -> {
                        withLink(
                            commentLinkAnnotation(
                                payload = RICH_COMMENT_LINK_TOPIC_PREFIX + matchInfo.annotation,
                                listener = linkListener,
                            )
                        ) {
                            withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                                append(matchInfo.value)
                            }
                        }
                    }

                    "vote" -> {
                        withLink(
                            commentLinkAnnotation(
                                payload = RICH_COMMENT_LINK_VOTE_PREFIX + matchInfo.annotation,
                                listener = linkListener,
                            )
                        ) {
                            withStyle(SpanStyle(color = urlColor, fontWeight = FontWeight.Medium)) {
                                append(matchInfo.displayText)
                            }
                        }
                    }

                    "url",
                    "rich_url",
                    "video" -> {
                        withLink(
                            commentLinkAnnotation(
                                payload = RICH_COMMENT_LINK_URL_PREFIX + matchInfo.annotation,
                                listener = linkListener,
                            )
                        ) {
                            withStyle(SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)) {
                                if (matchInfo.inlineContentId != null) {
                                    appendInlineContent(id = matchInfo.inlineContentId, alternateText = " ")
                                }
                                append(matchInfo.displayText)
                            }
                        }
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
    onReplyClick: (() -> Unit)? = null,
    detailedTimeEnabled: Boolean = false
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
            timeText = FormatUtils.formatCommentTime(
                timestampSeconds = item.ctime,
                detailedTimeEnabled = detailedTimeEnabled
            ),
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
            color = AppContentColors.primaryText()
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            text = FormatUtils.formatStat(count.toLong()),
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyItemView(
    modifier: Modifier = Modifier,
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
    // Thread detail reuses this row in every material; defaults keep native main-list behavior.
    containerColor: Color = Color.Unspecified,
    onConversationClick: (() -> Unit)? = null,
    // A thread root shares its lazy item with the section header; its avatar must stop above it.
    boundAvatarToItem: Boolean = false,
    // Independent opt-in for comment lists, in every material style.
    // Null preserves the native MD3/Miuix layout and all other comment callers.
    avatarListState: LazyListState? = null,
    avatarItemKey: Any = item.rpid,
    onAvatarClick: (String) -> Unit
) {
    val replyShareContext = com.android.purebilibili.feature.share.LocalReplyShareContext.current
    var showImagePreview by remember(item.oid, item.rpid) { mutableStateOf(false) }
    if (showImagePreview) {
        com.android.purebilibili.feature.share.ShareImagePreviewSheet(
            document = com.android.purebilibili.feature.share.replyShareDocument(item, replyShareContext, emoteMap),
            onDismiss = { showImagePreview = false },
        )
    }
    val appearance = rememberVideoCommentAppearance()
    val context = LocalContext.current
    val detailedCommentTimeEnabled = LocalDetailedCommentTimeEnabled.current
    val scope = rememberCoroutineScope()
    val isUpComment = upMid > 0 && item.mid == upMid
    val showResolvedIdentityDecorations = shouldShowReplyIdentityDecorations(showIdentityDecorations)
    // 右上角菜单的全局开关只控制"回复"快捷按钮本身，楼中楼预览不受影响。
    val replyButtonVisible = com.android.purebilibili.core.ui.LocalCommentReplyPreviewVisible.current
    val showSubPreview = shouldShowReplySubPreview(
        hideSubPreview = hideSubPreview,
        lightweightMode = lightweightMode
    )
    val preview = rememberReplyPreviewState(item = item, enabled = showSubPreview)
    val collapsedSubReplyPreviewLimit = preview.collapsedLimit
    val previewReplies = preview.replies
    val previewRoot = remember(item, previewReplies) {
        if (item.replies.orEmpty() == previewReplies) item else item.copy(replies = previewReplies)
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
    val metadataText = remember(item.ctime, displayLocation, detailedCommentTimeEnabled) {
        buildString {
            append(
                FormatUtils.formatCommentTime(
                    timestampSeconds = item.ctime,
                    detailedTimeEnabled = detailedCommentTimeEnabled
                )
            )
            if (!displayLocation.isNullOrEmpty()) {
                append(" · $displayLocation")
            }
        }
    }
    val showTopBadge = shouldShowReplyTopBadge(item = item, isPinned = isPinned)
    val layoutPolicy = remember { resolveReplyItemLayoutPolicy() }
    // 充电专属评论：优先取服务端 charged_desc，兜底识别 cardLabels 中的“充电”标签
    val chargedLabel = remember(item.replyControl, item.cardLabels) {
        resolveChargedReplyLabel(item)
    }
    val contentPrefix = remember(showTopBadge, chargedLabel) {
        if (!showTopBadge && chargedLabel == null) {
            null
        } else {
            buildAnnotatedString {
                if (showTopBadge) {
                    appendInlineContent(COMMENT_INLINE_TOP_BADGE_ID, "TOP")
                    append(" ")
                }
                if (chargedLabel != null) {
                    appendInlineContent(COMMENT_INLINE_CHARGED_BADGE_ID, chargedLabel)
                    append(" ")
                }
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
    var isSubPreviewExpanded by remember(item.rpid, previewReplies) {
        mutableStateOf(
            resolveInitialSubReplyPreviewExpanded(
                previewReplyCount = previewReplies.size
            )
        )
    }
    val visibleSubReplies = remember(previewReplies, isSubPreviewExpanded, collapsedSubReplyPreviewLimit) {
        resolveVisibleSubReplies(
            replies = previewReplies,
            expanded = isSubPreviewExpanded,
            collapsedLimit = collapsedSubReplyPreviewLimit
        )
    }
    val threadReplyCount = remember(item.count, item.rcount, item.replies, previewReplies) {
        maxOf(resolveReplyThreadCount(item), previewReplies.size)
    }
    val showSubReplySummary = shouldShowSubReplyPreviewSummary(
        replyCount = threadReplyCount,
        previewReplyCount = previewReplies.size,
    )
    val showInlineSubReplyToggle = !showSubReplySummary && shouldShowInlineSubReplyToggle(
        previewReplyCount = previewReplies.size,
        collapsedLimit = collapsedSubReplyPreviewLimit,
    )
    val hasUpSubReply = remember(item.replyControl?.upReply, previewReplies, upMid) {
        item.replyControl?.upReply == true || (upMid > 0 && previewReplies.any { it.mid == upMid })
    }
    val subReplySummaryLabel = remember(threadReplyCount, hasUpSubReply) {
        resolveSubReplyPreviewSummaryLabel(
            replyCount = threadReplyCount,
            hasUpReply = hasUpSubReply
        )
    }
    val openThreadFromRootClick = remember(hideSubPreview, previewRoot) {
        !hideSubPreview && shouldOpenReplyThreadFromRootClick(previewRoot)
    }
    val copyToClipboard = rememberClipboardCopyHandler()
    val blockedUpRepository = remember { BlockedUpRepository.getInstance(context) }
    var showActionSheet by remember(item.rpid) { mutableStateOf(false) }
    val glassActionsAvailable = com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
    val actionButtonSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    var actionSource by remember(item.rpid) { mutableStateOf<com.android.purebilibili.core.ui.components.AppGlassMenuSource?>(null) }
    var actionAnchor by remember(item.rpid) { mutableStateOf<Rect?>(null) }
    val actionCoordinates = remember(item.rpid) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val actionPressPosition = remember(item.rpid) { arrayOfNulls<androidx.compose.ui.geometry.Offset>(1) }
    val actionPressRelay = remember(item.rpid) { arrayOfNulls<com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress>(1) }
    var actionCarriedPress by remember(item.rpid) { mutableStateOf<com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress?>(null) }

    val openButtonActions = { actionCarriedPress = null; actionSource = actionButtonSource; actionAnchor = null; showActionSheet = true }
    val openLongPressActions = {
        onLongClick?.invoke()
        actionSource = null
        actionCarriedPress = actionPressRelay[0]
        val point = actionPressPosition[0] ?: actionCoordinates[0]?.boundsInWindow()?.center
        actionAnchor = point?.let { Rect(it, androidx.compose.ui.geometry.Size(1f, 1f)) }
        showActionSheet = true
    }

    var showFreeCopyDialog by remember(item.rpid) { mutableStateOf(false) }
    var showReportDialog by remember(item.rpid) { mutableStateOf(false) }
    // [新增] 评论翻译状态
    val canTranslate = remember(item.replyControl?.translationSwitch, item.content.message, item.oid, item.rpid) {
        shouldOfferCommentTranslation(item.replyControl?.translationSwitch, item.content.message, item.oid, item.rpid)
    }
    var translatedMessage by remember(item.rpid) { mutableStateOf<String?>(null) }
    var isTranslating by remember(item.rpid) { mutableStateOf(false) }
    // [新增] 点踩折叠：已点踩的评论正文收起为一行，点击展开；取消点踩自动恢复
    var hatedBodyExpanded by remember(item.rpid, isHated) { mutableStateOf(false) }
    val collapseHatedBody = isHated && !hatedBodyExpanded
    var hatePromptHandled by remember(item.rpid) { mutableStateOf(false) }
    // 与仓库既有回弹手感一致（bouncyClickable 等使用的同组弹簧参数）
    val hateCollapseSpring: SpringSpec<androidx.compose.ui.unit.IntSize> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val displayMessage = remember(translatedMessage, item.content.message) {
        translatedMessage ?: item.content.message
    }
    val copyText = remember(item.content.message) { item.content.message.trim() }
    val replyMemberMid = remember(item.member.mid, item.mid) { resolveReplyMemberMid(item) }
    fun requestSaveReplyCommentImage() { showImagePreview = true }

    fun shareReplyComment() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "分享评论")
            putExtra(Intent.EXTRA_TEXT, buildReplyCommentShareText(item, replyShareContext))
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

    var confirmBlockUser by remember(item.rpid) { mutableStateOf(false) }

    if (showActionSheet) {
        val accountIdentity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
        ReplyActionSheet(
            source = actionSource,
            carriedPress = actionCarriedPress,
            anchorBounds = actionAnchor,
            queryAuthorUid = replyMemberMid,
            canCheckFraud = onCheckFraudClick != null,
            // 楼中楼详情页的行也复用同一开关：控制其下方"回复"快捷按钮的显示。
            canToggleReplyPreview = true,
            canReport = onReportClick != null,
            canShare = shouldSupportReplyShare(item),
            canBlockUser = shouldShowReplyBlockUserAction(replyMemberMid, accountIdentity.mid),
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
                onReplyClick?.invoke() ?: onSubClick(previewRoot, 0L)
            },
            onBlockUser = {
                confirmBlockUser = true
            },
            onReport = {
                showReportDialog = true
            },
            onToggleTop = {
                onToggleTopClick?.invoke()
            },
            onCheckFraud = {
                onCheckFraudClick?.invoke()
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

    if (confirmBlockUser) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { confirmBlockUser = false },
            title = { AppText("拉黑该用户？") },
            text = { AppText("拉黑「${item.member.uname}」后将不再显示 TA 的评论和动态，可在设置中解除。") },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    confirmBlockUser = false
                    hatePromptHandled = true
                    blockReplyUser()
                }) { AppText("确认拉黑") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = { confirmBlockUser = false }) { AppText("取消") }
            },
        )
    }

    val avatarContentHeightPx = if (boundAvatarToItem && avatarListState != null) {
        remember(item.rpid) { mutableIntStateOf(0) }
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (avatarContentHeightPx != null) {
                    Modifier.onSizeChanged { avatarContentHeightPx.intValue = it.height }
                } else Modifier
            )
            .commentAvatarRowBackground(
                color = if (containerColor == Color.Unspecified) appearance.panelColor else containerColor,
                pushEnabled = avatarListState != null,
                gutterWidth = layoutPolicy.horizontalPaddingDp.dp + NAGRAM_CHAT_AVATAR_SIZE_DP.dp,
            )
            .then(
                if (glassActionsAvailable) Modifier
                    .onGloballyPositioned { actionCoordinates[0] = it }
                    .pointerInput(item.rpid) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false,
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            actionPressPosition[0] = actionCoordinates[0]?.localToWindow(down.position)
                            val press = com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress()
                            actionPressRelay[0] = press
                            var moved = false
                            var cancelled = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val carrying = actionCarriedPress === press
                                    if (event.changes.any { it.id != down.id && it.pressed }) {
                                        cancelled = true
                                        press.cancel()
                                    }
                                    if (carrying) {
                                        event.changes.forEach { it.consume() }
                                        val point = actionCoordinates[0]?.localToWindow(change.position)
                                        moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                        if (!cancelled && point != null) {
                                            if (!change.pressed) {
                                                if (moved) press.move(point)
                                                press.release(point)
                                            }
                                            else if (moved) press.move(point)
                                        }
                                    }
                                    if (!change.pressed) break
                                }
                            } finally {
                                press.cancel()
                                if (actionPressRelay[0] === press) actionPressRelay[0] = null
                            }
                        }
                    }
                else Modifier
            )
            .combinedClickable(
                onClick = appActionWithFeedback {
                    // 主列表空白可直接回复；详情正文不冒泡到回复输入框。
                    if (onReplyClick != null && !hideSubPreview) {
                        onReplyClick()
                    } else if (openThreadFromRootClick) {
                        onSubClick(previewRoot, 0L)
                    } else {
                        onClick()
                    }
                },
                onLongClick = openLongPressActions
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 10.dp,
                    bottom = 2.dp,
                    start = layoutPolicy.horizontalPaddingDp.dp,
                    end = layoutPolicy.horizontalPaddingDp.dp
                )
        ) {
            // Avatar push owns avatar placement only. Keep author metadata, badges and
            // the trailing decoration in the same header slots as the native comment row.
            val headerEndPadding = resolveReplyItemHeaderEndPaddingDp(
                hasBiliPaiDecoration = biliPaiDecoration != null,
                policy = layoutPolicy,
            ).dp
            // Avatar push geometry is explicitly opt-in in every material style.
            // Disabled retains native avatar dimensions and narrow-window defaults.
            val pushAvatar = avatarListState != null
            val avatarSize = if (pushAvatar) NAGRAM_CHAT_AVATAR_SIZE_DP.dp else layoutPolicy.avatarSizeDp.dp
            val startPadding = if (pushAvatar) {
                avatarSize + layoutPolicy.avatarContentSpacingDp.dp
            } else {
                resolveReplyItemContentStartPaddingDp(maxWidth, layoutPolicy).dp
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                // User Info Header
                Row() {
                    if (pushAvatar) {
                        Spacer(modifier = Modifier.size(avatarSize))
                    } else {
                        ReplyMemberAvatar(
                            member = item.member,
                            placeholderColor = appearance.placeholderColor,
                            lightweightMode = lightweightMode,
                            modifier = Modifier.size(layoutPolicy.avatarSizeDp.dp),
                            onClick = { if (replyMemberMid > 0L) onAvatarClick(replyMemberMid.toString()) }
                        )
                    }

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
                                color = resolveUpNameColor(
                                    vipStatus = item.member.vip?.vipStatus ?: 0,
                                    vipType = item.member.vip?.vipType ?: 0,
                                    // 用户名用主题强调色与正文区分层次，大会员保留专属粉色。
                                    onSurface = appearance.accentColor,
                                    secondary = com.android.purebilibili.core.ui.components.userVipPink(),
                                ),
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
                            lineHeight = VideoCommentTypographyTokens.metadataLineHeight,
                            color = appearance.secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = hateCollapseSpring)
                        .padding(start = startPadding)
                ) {
                    if (collapseHatedBody) {
                        AppText(
                            text = "已点踩的评论 · 点击展开",
                            fontSize = VideoCommentTypographyTokens.body,
                            color = appearance.secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .appActionClickable { hatedBodyExpanded = true }
                                .padding(vertical = 2.dp)
                        )
                        if (!hatePromptHandled) {
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ReplyTextAction(
                                    label = "屏蔽该用户",
                                    appearance = appearance,
                                    onClick = { confirmBlockUser = true },
                                    icon = Icons.Outlined.Block
                                )
                                ReplyTextAction(
                                    label = "举报",
                                    appearance = appearance,
                                    onClick = {
                                        hatePromptHandled = true
                                        showReportDialog = true
                                    },
                                    icon = Icons.Outlined.Flag
                                )
                            }
                        }
                    } else {
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
                        onLongPress = openLongPressActions,
                        onPlainTextClick = if (openThreadFromRootClick) {
                            { onSubClick(previewRoot, 0L) }
                        } else {
                            null
                        }
                    )

                    // Images
                    if (!item.content.pictures.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CommentPictures(
                            pictures = item.content.pictures.orEmpty(),
                            onImageClick = { images, index, rect ->
                                onImagePreview?.invoke(
                                    images,
                                    index,
                                    rect,
                                    resolveReplyPreviewTextContent(
                                        item = item,
                                        isLiked = isLiked,
                                        onLikeClick = onLikeClick,
                                        onReplyClick = onReplyClick,
                                        detailedTimeEnabled = detailedCommentTimeEnabled
                                    )
                                )
                            }
                        )
                    }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Footer Actions；"隐藏回复"开启时收起回复快捷入口（长按菜单仍可回复）。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    if (replyButtonVisible) {
                        ReplyTextAction(
                            label = "回复",
                            appearance = appearance,
                            onClick = { onReplyClick?.invoke() ?: onSubClick(previewRoot, 0L) }
                        )
                    }

                    // [新增] 翻译按钮 (胶囊样式)
                    if (canTranslate) {
                        val isTranslated = translatedMessage != null
                        val translateLabel = if (isTranslating) "翻译中" else if (isTranslated) "原文" else "翻译"
                        AppSurface(
                            shape = AppShapes.container(ContainerLevel.Pill),
                            color = if (isTranslated) appearance.accentColor.copy(alpha = 0.14f) else appearance.actionTint.copy(alpha = 0.10f),
                            modifier = Modifier
                                .appActionClickable(enabled = !isTranslating) {
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

                    if (onConversationClick != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AppText(
                            text = "查看对话",
                            fontSize = VideoCommentTypographyTokens.action,
                            color = appearance.actionTint,
                            modifier = Modifier
                                .sizeIn(minHeight = 32.dp)
                                .testTag("$SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX${item.rpid}")
                                .appActionClickable(onClick = onConversationClick)
                                .wrapContentHeight(),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (onDeleteClick != null) {
                        Box(modifier = Modifier.height(32.dp)) {
                            AppIconButton(onClick = onDeleteClick) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = appearance.actionTint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val likeFilledIcon = rememberAppLikeFilledIcon()

                    // Like
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .appActionClickable(enabled = onLikeClick != null) { onLikeClick?.invoke() }
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
                    Box(modifier = Modifier.height(32.dp)) {
                        AppIconButton(
                            onClick = { onHateClick?.invoke() },
                            enabled = onHateClick != null
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.ThumbDown,
                                contentDescription = if (isHated) "取消点踩" else "点踩评论",
                                tint = if (isHated) AppContentColors.error() else appearance.actionTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                }

                // Sub-comments (Threaded view)
                if (showSubPreview && (previewReplies.isNotEmpty() || threadReplyCount > 0)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // This preview is already inside the indented body column.
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .background(appearance.composerHintBackgroundColor)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewReplies.forEachIndexed { index, subReply ->
                            key(subReply.rpid) {
                                CommentContentReveal(visible = index < visibleSubReplies.size) {
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
                                    val prefixTextColor = resolveUpNameColor(
                                        vipStatus = subReply.member.vip?.vipStatus ?: 0,
                                        vipType = subReply.member.vip?.vipType ?: 0,
                                        // 楼中楼用户名同样用主题强调色区分正文，大会员保留专属粉色。
                                        onSurface = appearance.accentColor,
                                        secondary = com.android.purebilibili.core.ui.components.userVipPink(),
                                    )
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
                                    // Native text links consume their own tap; previews route them
                                    // to this reply's floor instead of navigating out of the thread.
                                    val openPreviewReply = {
                                        onSubClick(
                                            previewRoot,
                                            resolveSubReplyOpenTargetId(item.rpid, subReply.rpid)
                                        )
                                    }
                                    val previewContent = remember(subReply.content) {
                                        resolveSubReplyPreviewContent(subReply.content)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("$COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX${subReply.rpid}")
                                            .combinedClickable(
                                                onClick = appActionWithFeedback(openPreviewReply),
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
                                            // 预览单独放宽行高，主评论与楼中楼详情保留原有排版。
                                            lineHeight = VideoCommentTypographyTokens.subReply * 1.6f,
                                            color = appearance.primaryTextColor.copy(alpha = 0.8f),
                                            emoteMap = subEmoteMap,
                                            content = previewContent,
                                            maxLines = 2,
                                            onTimestampClick = { openPreviewReply() },
                                            maxTimestampMs = maxTimestampMs,
                                            onUrlClick = { openPreviewReply() },
                                            onUserClick = { openPreviewReply() },
                                            onTopicClick = { openPreviewReply() },
                                            onVoteClick = { openPreviewReply() },
                                            noteCvidStr = subReply.noteCvidStr,
                                            prefix = prefix,
                                            onPlainTextClick = openPreviewReply
                                        )
                                    }
                                }
                            }
                        }

                        if (showInlineSubReplyToggle) {
                            AppText(
                                text = resolveInlineSubReplyToggleLabel(expanded = isSubPreviewExpanded),
                                fontSize = VideoCommentTypographyTokens.subReply,
                                color = appearance.accentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .appActionClickable { isSubPreviewExpanded = !isSubPreviewExpanded }
                                    .padding(vertical = 4.dp)
                            )
                        } else if (showSubReplySummary) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX${item.rpid}")
                                    .appActionClickable { onSubClick(previewRoot, 0L) },
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
            if (pushAvatar && avatarListState != null) {
                PushingReplyMemberAvatar(
                    member = item.member,
                    placeholderColor = appearance.placeholderColor,
                    lightweightMode = lightweightMode,
                    listState = avatarListState,
                    itemKey = avatarItemKey,
                    contentHeightPxProvider = if (avatarContentHeightPx == null) null else {
                        { avatarContentHeightPx.intValue }
                    },
                    allowNeighborContact = !boundAvatarToItem,
                    modifier = Modifier.size(avatarSize),
                    onClick = { if (replyMemberMid > 0L) onAvatarClick(replyMemberMid.toString()) }
                )
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
            onClick = openButtonActions,
            modifier = Modifier
                .appGlassMenuSource(actionButtonSource, onOpen = openButtonActions)
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
            modifier = Modifier.padding(
                start = if (avatarListState != null) {
                    layoutPolicy.horizontalPaddingDp.dp + NAGRAM_CHAT_AVATAR_SIZE_DP.dp +
                        layoutPolicy.avatarContentSpacingDp.dp
                } else layoutPolicy.dividerStartPaddingDp.dp
            ),
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
    color: Color = AppContentColors.primaryText(),
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
    lineHeight: TextUnit = fontSize * 1.5f,
    onPlainTextClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
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
            lineHeight = lineHeight,
            maxLines = maxLines,
            url = videoReference.navigationUrl,
            onUrlClick = onUrlClick,
            prefix = prefix,
            copyText = text.trim(),
            onLongPress = onLongPress,
        )
    } else {
        RichCommentText(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
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
            onPlainTextClick = onPlainTextClick,
            onLongPress = onLongPress,
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
    copyText: String = text,
    lineHeight: TextUnit = fontSize * 1.5f,
    onLongPress: (() -> Unit)? = null
) {
    val primaryColor = AppContentColors.accent()
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
    val chargedBadgeInlineContent = rememberInlineChargedBadgeContent()
    val personalVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.PERSONAL)
    val organizationVerifyInlineContent = rememberInlineOfficialVerifyBadgeContent(OfficialVerifyBadgeTone.ORGANIZATION)
    val inlineContent = remember(
        upBadgeInlineContent,
        topBadgeInlineContent,
        chargedBadgeInlineContent,
        personalVerifyInlineContent,
        organizationVerifyInlineContent
    ) {
        mapOf(
            COMMENT_INLINE_UP_BADGE_ID to upBadgeInlineContent,
            COMMENT_INLINE_TOP_BADGE_ID to topBadgeInlineContent,
            COMMENT_INLINE_CHARGED_BADGE_ID to chargedBadgeInlineContent,
            COMMENT_INLINE_VERIFY_PERSONAL_BADGE_ID to personalVerifyInlineContent,
            COMMENT_INLINE_VERIFY_ORGANIZATION_BADGE_ID to organizationVerifyInlineContent
        )
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showTextSelectionSheet by remember(copyText) { mutableStateOf(false) }
    val modifier = if (onUrlClick != null) {
        Modifier.pointerInput(annotatedString, url, onUrlClick, onLongPress) {
            detectTapGestures(
                onLongPress = {
                    if (onLongPress != null) onLongPress()
                    else if (copyText.isNotEmpty()) showTextSelectionSheet = true
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
        lineHeight = lineHeight,
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
    color: Color = AppContentColors.primaryText(),
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
    lineHeight: TextUnit = fontSize * 1.5f,
    onPlainTextClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val timestampColor = AppContentColors.accent()
    val urlColor = AppContentColors.accent()

    val renderableEmoteKeys = remember(text, emoteMap) {
        collectRenderableEmoteKeys(text, emoteMap)
    }
    // 触摸、键盘和无障碍入口共用链接动作，始终绑定当前页面的回调。
    val linkListener = remember(onUrlClick, onUserClick, onTopicClick, onVoteClick, onTimestampClick) {
        LinkInteractionListener { link ->
            when (val action = resolveRichCommentLinkAction((link as LinkAnnotation.Clickable).tag)) {
                is RichCommentLinkAction.Url -> onUrlClick?.invoke(action.url)
                is RichCommentLinkAction.User -> onUserClick?.invoke(action.mid)
                is RichCommentLinkAction.Topic -> onTopicClick?.invoke(action.topic)
                is RichCommentLinkAction.Vote -> onVoteClick?.invoke(action.voteId)
                is RichCommentLinkAction.Timestamp ->
                    onTimestampClick?.invoke(action.seconds * 1000)
                null -> Unit
            }
        }
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
        urlColor,
        linkListener
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
            urlColor = urlColor,
            linkListener = linkListener
        )
    }

    val upBadgeInlineContent = rememberInlineUpBadgeContent()
    val topBadgeInlineContent = rememberInlineTopBadgeContent()
    val chargedBadgeInlineContent = rememberInlineChargedBadgeContent()
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
        chargedBadgeInlineContent,
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
            put(COMMENT_INLINE_CHARGED_BADGE_ID, chargedBadgeInlineContent)
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
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val content: @Composable () -> Unit = {
        // 以实际字符位置命中链接，跨行标题与内联图标共用同一跳转；
        // 只接管链接手势，普通文字仍交给条目点击、长按或原生划选。
        val textModifier = Modifier
            .pointerInput(annotatedString, onLongPress) {
                detectReplyLinkGestures(
                    linkAt = { position -> textLayoutResult?.replyLinkAt(position) },
                    onLinkClick = { link -> link.linkInteractionListener?.onClick(link) },
                    onLongPress = {
                        if (onLongPress != null) onLongPress()
                        else if (copyText.isNotEmpty()) showTextSelectionSheet = true
                    },
                )
            }
            .then(
                if (onPlainTextClick != null) {
                    Modifier.pointerInput(annotatedString, onPlainTextClick) {
                        detectTapWithSelectionFriendly { _ -> onPlainTextClick() }
                    }
                } else Modifier
            )

        AppText(
            text = annotatedString,
            inlineContent = inlineContent,
            fontSize = fontSize,
            color = color,
            lineHeight = lineHeight,
            style = com.android.purebilibili.core.ui.appParagraphTextStyle(),
            maxLines = maxLines,
            onTextLayout = { textLayoutResult = it },
            modifier = textModifier
        )
    }

    if (selectionEnabled) {
        SelectionContainer {
            content()
        }
    } else {
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
    color: Color = AppContentColors.primaryText(),
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
    val accentColor = detail.medalColor.takeIf { it > 0 }?.let {
        Color((it and 0xFFFFFFL).toInt() or 0xFF000000.toInt())
    } ?: resolveFansMedalColor(detail.level)
    val nameColor = detail.medalColorName.takeIf { detail.medalColor > 0 }?.let {
        Color((it and 0xFFFFFFL).toInt() or 0xFF000000.toInt())
    } ?: accentColor.copy(alpha = 0.95f)
    if (detail.medalColor > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(AppShapes.container(ContainerLevel.Tag))
                .background(accentColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            AppText(
                text = detail.medalName,
                style = MaterialTheme.typography.labelSmall,
                color = nameColor,
                maxLines = 1,
            )
            AppText(
                text = " ${detail.level}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                maxLines = 1,
            )
        }
        return
    }
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
            color = nameColor,
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
    val displayFace = member.avatar
    val hasPendant = !pendantImageUrl.isNullOrBlank()
    val faceFraction = resolveReplyAvatarFaceFraction(hasPendant)
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.appActionClickable(onClick = onClick) else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        // Face first (under), slightly smaller when a frame is present.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(FormatUtils.fixImageUrl(displayFace))
                //  [防闪烁] crossfade 不能随 lightweightMode 翻转：改请求构造会让
                //  Coil 视为新请求重新执行，切回评论页瞬间全部头像重载闪一下。
                //  内存缓存命中本身不播淡入，恒定开启不损失滚动性能。
                .crossfade(true)
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
                    .crossfade(true)
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
                fallbackColor = AppContentColors.primaryText()
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

@Composable
internal fun ReplySpecialLabelChip(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = AppContentColors.accent()
    )
}

@Composable
internal fun ReplyTextAction(
    label: String,
    appearance: VideoCommentAppearance,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Outlined.Reply
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .appActionClickable { onClick() }
            .padding(end = 8.dp)
    ) {
        AppIcon(
            imageVector = icon,
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
    canCheckFraud: Boolean,
    canReport: Boolean,
    canShare: Boolean = true,
    canBlockUser: Boolean = false,
    canCopyUsername: Boolean = true,
    canToggleReplyPreview: Boolean = false,
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
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    carriedPress: com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress? = null,
    anchorBounds: Rect? = null,
    onToggleTop: () -> Unit
) {
    val queryAicu = com.android.purebilibili.feature.aicu.LocalAicuNavigation.current
    val canQueryAuthorHistory = queryAicu != null && queryAuthorUid > 0
    val sheetContext = LocalContext.current
    val sheetScope = rememberCoroutineScope()
    //  [评论时间样式] 长按菜单直达开关：相对时间（默认，PiliPlus 规则）⇄ 绝对时间
    //  （yyyy-MM-dd HH:mm:ss）。设置项全局持久化，评论区经 CompositionLocal 即时刷新。
    val detailedTimeEnabled by SettingsManager
        .getDetailedCommentTimeEnabled(sheetContext)
        .collectAsStateWithLifecycle(initialValue = false)
    val replyPreviewVisible by SettingsManager
        .getCommentReplyPreviewVisible(sheetContext)
        .collectAsStateWithLifecycle(initialValue = true)
    val actions = remember(
        canQueryAuthorHistory,
        canCheckFraud,
        canReport,
        canShare,
        canBlockUser,
        canCopyUsername,
        canToggleReplyPreview,
        topActionLabel,
    ) {
        buildReplyActionSheetActions(
            canCheckFraud = canCheckFraud,
            canReport = canReport,
            canShare = canShare,
            canBlockUser = canBlockUser,
            topActionLabel = topActionLabel,
            canCopyUsername = canCopyUsername,
            canQueryAuthorHistory = canQueryAuthorHistory,
            canToggleReplyPreview = canToggleReplyPreview,
        )
    }
    val actionItems: @Composable () -> Unit = {
        actions.forEach { action ->
            ReplyActionSheetItem(
                label = if (action == ReplyActionSheetAction.TIME_STYLE) {
                    if (detailedTimeEnabled) "评论时间样式：绝对（yyyy-MM-dd HH:mm:ss）"
                    else "评论时间样式：相对"
                } else if (action == ReplyActionSheetAction.TOGGLE_REPLY_PREVIEW) {
                    if (replyPreviewVisible) "隐藏回复按钮" else "显示回复按钮"
                } else {
                    resolveReplyActionSheetLabel(action, topActionLabel)
                },
                isDestructive = isReplyActionDestructive(action),
                onClick = {
                    when (action) {
                        ReplyActionSheetAction.COPY_ALL -> onCopyAll()
                        ReplyActionSheetAction.FREE_COPY -> onFreeCopy()
                        ReplyActionSheetAction.COPY_USERNAME -> onCopyUsername()
                        ReplyActionSheetAction.QUERY_AUTHOR_HISTORY -> queryAicu?.invoke(queryAuthorUid)
                        ReplyActionSheetAction.TIME_STYLE -> {
                            // 与回复按钮开关同因：写盘不挂弹层作用域，避免关闭竞态落空。
                            val next = !detailedTimeEnabled
                            SettingsManager.setDetailedCommentTimeEnabledDetached(sheetContext, next)
                            Toast.makeText(
                                sheetContext,
                                if (next) {
                                    "已切换为绝对时间：评论显示 yyyy-MM-dd HH:mm:ss"
                                } else {
                                    "已切换为相对时间：评论按相对时间显示"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        ReplyActionSheetAction.TOGGLE_REPLY_PREVIEW -> {
                            // 写盘走独立作用域：弹层随即关闭并取消 sheetScope，
                            // 挂起写入若挂在它下面会与关闭竞态导致开关落空。
                            val next = !replyPreviewVisible
                            SettingsManager.setCommentReplyPreviewVisibleDetached(sheetContext, next)
                            Toast.makeText(
                                sheetContext,
                                if (next) "已显示回复按钮" else "已隐藏回复按钮",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        ReplyActionSheetAction.SAVE -> onSave()
                        ReplyActionSheetAction.SHARE -> onShare()
                        ReplyActionSheetAction.REPLY -> onReply()
                        ReplyActionSheetAction.BLOCK_USER -> onBlockUser()
                        ReplyActionSheetAction.REPORT -> onReport()
                        ReplyActionSheetAction.CHECK_FRAUD -> onCheckFraud()
                        ReplyActionSheetAction.TOGGLE_TOP -> onToggleTop()
                    }
                    onDismiss()
                }
            )
        }
    }
    if (com.android.purebilibili.core.ui.components.AppGlassMenu(
            expanded = true, onDismissRequest = onDismiss, title = "评论操作",
            source = source, carriedPress = carriedPress, anchorBounds = anchorBounds, minWidth = 220.dp, maxWidth = 320.dp,
        ) { Column { actionItems() } }) return
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 保留原生面板和平板居中路径的导航栏避让。
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) { actionItems() }
    }
}

@Composable
private fun ReplyActionSheetItem(
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    if (com.android.purebilibili.core.ui.components.LocalAppGlassMenuScope.current != null) {
        com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
            text = { AppText(label, color = if (isDestructive) AppContentColors.error() else AppContentColors.primaryText()) },
            onClick = onClick,
        )
        return
    }
    AppText(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDestructive) AppContentColors.error() else AppContentColors.primaryText(),
        modifier = Modifier
            .fillMaxWidth()
            .appActionClickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

//  UP 标签 - BiliPai small badge style
@Composable
fun UpTag() {
    UserUpBadge()
}

@Composable
fun TopTag(modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelSmall
    // TOP 没有下伸部，行框居中后仍会显得偏下；按字号补偿视觉中心。
    val opticalOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
        (labelStyle.fontSize * -0.08f).toDp()
    }
    Box(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Tag))
            .border(
                width = 1.dp,
                color = AppContentColors.accent(),
                shape = AppShapes.container(ContainerLevel.Tag)
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = "TOP",
            modifier = Modifier.offset(y = opticalOffset),
            style = labelStyle.copy(
                lineHeight = labelStyle.fontSize,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
            fontWeight = FontWeight.Bold,
            color = AppContentColors.accent(),
            maxLines = 1,
            tapToCopyEnabled = false,
        )
    }
}

// 充电评论使用主题 tertiaryContainer / onTertiaryContainer，并保留闪电身份标识。
@Composable
fun ChargedReplyTag(text: String, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val chargedContainer = MaterialTheme.colorScheme.tertiaryContainer
    val chargedContent = MaterialTheme.colorScheme.onTertiaryContainer
    val opticalOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
        (labelStyle.fontSize * -0.08f).toDp()
    }
    Box(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Tag))
            .background(chargedContainer)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = "⚡$text",
            modifier = Modifier.offset(y = opticalOffset),
            style = labelStyle.copy(
                lineHeight = labelStyle.fontSize,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
            color = chargedContent,
            maxLines = 1,
            tapToCopyEnabled = false,
        )
    }
}

@Composable
private fun rememberInlineChargedBadgeContent(): InlineTextContent {
    return remember {
        InlineTextContent(
            Placeholder(
                width = 3.2.em,
                height = 1.15.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ChargedReplyTag(text = "充电评论")
            }
        }
    }
}

// 充电评论识别：reply_control.charged_desc 非空，或 cardLabels 中出现“充电”标签
internal fun resolveChargedReplyLabel(item: ReplyItem): String? {
    val desc = item.replyControl?.chargedDesc?.takeIf { it.isNotBlank() }
    if (desc != null) return desc
    val label = item.cardLabels
        ?.firstOrNull { it.textContent.contains("充电") }
        ?.textContent
        ?.takeIf { it.isNotBlank() }
    return label
}

private const val COMMENT_PICTURE_MAX_RETRIES = 3

@Composable
private fun CommentPictureThumbnail(
    imageUrl: String,
    imageLoader: ImageLoader,
    decodeSize: ImageDecodeSize,
) {
    val context = LocalContext.current
    val request = remember(context, imageUrl, decodeSize) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            // Preserve the source cache key used by the preview's first-frame placeholder.
            .memoryCacheKey(imageUrl)
            .size(decodeSize.widthPx, decodeSize.heightPx)
            .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
            .crossfade(false)
            .build()
    }
    var retryAttempt by remember(imageUrl) { mutableIntStateOf(0) }
    var loadError by remember(imageUrl) { mutableStateOf<Throwable?>(null) }
    var hasLoadedImage by remember(imageUrl) { mutableStateOf(false) }
    val previewImage = if (!hasLoadedImage) rememberImagePreviewSourceImage(imageUrl) else null
    if (previewImage != null) {
        // Wait until the overlay releases its painter, especially for animated drawables.
        if (!isImagePreviewOpen()) {
            val painter = remember(previewImage, context) { previewImage.asPainter(context) }
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val error = loadError
    if (error != null && retryAttempt < COMMENT_PICTURE_MAX_RETRIES) {
        LaunchedEffect(imageUrl, retryAttempt, error) {
            if (retryAttempt == 0) {
                Logger.w("CommentPictures", "缩略图加载失败，最多重试3次: $imageUrl", error)
            }
            delay(350L * (retryAttempt + 1))
            loadError = null
            retryAttempt++
        }
    }

    // A failed painter does not reload an unchanged model. Replace only the image subtree;
    // disposing the thumbnail also cancels any pending retry through LaunchedEffect.
    key(imageUrl, retryAttempt) {
        AsyncImage(
            model = request,
            contentDescription = null,
            imageLoader = imageLoader,
            onError = { loadError = it.result.throwable },
            onSuccess = {
                hasLoadedImage = true
                loadError = null
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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
    //  获取高质量图片URL（移除分辨率限制参数）；URL 归一化规则下沉 core-data 后委托共享实现
    val imageUrls = remember(pictures) {
        resolveCommentPictureUrls(pictures)
    }
    val galleryRects = remember(imageUrls) { mutableMapOf<Int, Rect>() }
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


    // 根据图片数量选择不同的布局
    when (pictures.size) {
        1 -> {
            // 单张图片：保持原始比例，限制最大尺寸
            val pic = pictures[0]
            //  [优化] 更好的比例计算；比例规则下沉 core-data 后委托共享实现
            val aspectRatio = resolveCommentSinglePictureAspectRatio(pic)
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
                        imageRect?.let { galleryRects[0] = it }
                    }
                    .appActionClickable(
                        interactionSource = null,
                        indication = null,
                        enabled = !sourceHidden,
                    ) {
                        val anchor = imageRect?.let {
                            ImagePreviewSourceAnchor(
                                it,
                                singleImageCornerDp,
                                galleryRects = galleryRects.toMap()
                            )
                        }
                        prepareImagePreviewSourceTransition(anchor?.rect)
                        onImageClick(
                            imageUrls,
                            0,
                            anchor
                        )
                    }
            ) {
                CommentPictureThumbnail(
                    imageUrl = imageUrls[0],
                    imageLoader = gifImageLoader,
                    decodeSize = thumbnailDecodeSize,
                )
            }
        }
        else -> {
            // 多张图片：网格布局
            val displayItems = pictures.take(9)  //  [优化] 最多显示9张
            // 列数规则下沉 core-data 后委托共享实现
            val columns = resolveCommentPictureGridColumns(displayItems.size)

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
                                        imageRect?.let { galleryRects[globalIndex] = it }
                                    }
                                    .appActionClickable(
                                        interactionSource = null,
                                        indication = null,
                                        enabled = !sourceHidden,
                                    ) {
                                        val anchor = imageRect?.let {
                                            ImagePreviewSourceAnchor(
                                                it,
                                                gridImageCornerDp,
                                                galleryRects = galleryRects.toMap()
                                            )
                                        }
                                        prepareImagePreviewSourceTransition(anchor?.rect)
                                        onImageClick(
                                            imageUrls,
                                            globalIndex,
                                            anchor
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CommentPictureThumbnail(
                                    imageUrl = imageUrls[globalIndex],
                                    imageLoader = gifImageLoader,
                                    decodeSize = thumbnailDecodeSize,
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
