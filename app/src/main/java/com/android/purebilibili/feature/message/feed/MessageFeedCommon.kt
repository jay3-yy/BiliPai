package com.android.purebilibili.feature.message.feed

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberContentCardSurfaceSpec
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberStaticSkeletonBlockColor
import com.android.purebilibili.feature.message.messageGlassContainer
import com.android.purebilibili.core.util.FormatUtils

/**
 * 消息通知卡片的 lazy item 身份。骨架卡片与真实卡片共用同一个 contentType，
 * 加载完成时 LazyList 才能在原地替换 item，而不是整批删除 + 整批新增。
 */
internal const val MESSAGE_FEED_CARD_CONTENT_TYPE = "message_feed_card"

/** 消息通知首屏骨架卡片数量。 */
internal const val MESSAGE_FEED_SKELETON_ITEM_COUNT = 8

internal fun formatMessageFeedTime(timestampSeconds: Int): String =
    FormatUtils.formatPublishTime(timestampSeconds.toLong())

internal fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}

internal fun buildMessageFeedCommentNavigationLink(
    nativeUri: String?,
    uri: String?,
    businessId: Int,
    subjectId: Long,
    rootId: Long,
    sourceId: Long,
    targetId: Long,
    business: String? = null
): String? {
    val trimmedNative = nativeUri?.trim().orEmpty()
    val trimmedUri = uri?.trim().orEmpty()

    // Extract any IDs from query if nativeUri or uri is a query string or has parameters
    val queryCandidate = when {
        trimmedNative.startsWith("?") -> trimmedNative.removePrefix("?")
        trimmedNative.contains("?") -> trimmedNative.substringAfter("?")
        trimmedUri.contains("?") -> trimmedUri.substringAfter("?")
        else -> null
    }
    val queryParams = queryCandidate?.split("&")?.mapNotNull { param ->
        val pair = param.split("=", limit = 2)
        if (pair.isEmpty() || pair[0].isBlank()) null
        else pair[0].trim() to pair.getOrElse(1) { "" }.trim()
    }?.toMap().orEmpty()

    val queryRootId = listOf("comment_root_id", "root_reply_id", "root_id")
        .firstNotNullOfOrNull { key -> queryParams[key]?.toLongOrNull()?.takeIf { it > 0L } } ?: 0L
    val queryTargetId = listOf("comment_secondary_id", "comment_id", "reply_id", "rpid", "target_id", "anchor", "source_id")
        .firstNotNullOfOrNull { key -> queryParams[key]?.toLongOrNull()?.takeIf { it > 0L } } ?: 0L

    val resolvedRootId = listOf(rootId, queryRootId, sourceId, targetId, queryTargetId)
        .firstOrNull { it > 0L } ?: 0L
    val resolvedTargetId = listOf(targetId, queryTargetId, sourceId)
        .firstOrNull { it > 0L && it != resolvedRootId } ?: 0L

    // If nativeUri is already a dedicated comment deep link or already contains comment parameters, prioritize it
    val nativeIsAlreadyComment = trimmedNative.contains("comment") ||
        trimmedNative.contains("comment_root_id") ||
        trimmedNative.contains("root_reply_id")
    if (trimmedNative.contains("://") && nativeIsAlreadyComment) {
        return trimmedNative
    }

    val resolvedBusinessId = when {
        businessId > 0 -> businessId
        business?.contains("视频") == true || trimmedUri.contains("/video/") || trimmedNative.contains("/video/") -> 1
        business?.contains("动态") == true || trimmedUri.contains("t.bilibili.com") || trimmedUri.contains("/opus/") || trimmedNative.contains("/opus/") -> 17
        business?.contains("专栏") == true || trimmedUri.contains("/read/") || trimmedNative.contains("/read/") -> 12
        else -> 0
    }

    // If we have businessId, subjectId and rootReplyId, build the canonical comment deep link
    if (resolvedBusinessId > 0 && subjectId > 0L && resolvedRootId > 0L) {
        val targetQuery = if (resolvedTargetId > 0L) "?comment_id=$resolvedTargetId" else ""
        val enterCandidate = when {
            trimmedNative.contains("://") -> trimmedNative
            trimmedUri.contains("://") -> trimmedUri
            else -> when (resolvedBusinessId) {
                11, 16, 17 -> "bilibili://following/detail/$subjectId"
                12 -> "bilibili://read/cv$subjectId"
                else -> "bilibili://video/$subjectId"
            }
        }
        val enterUriParam = if (enterCandidate.isNotBlank()) {
            val sep = if (targetQuery.isEmpty()) "?" else "&"
            val encodedEnterUri = runCatching {
                java.net.URLEncoder.encode(enterCandidate, "UTF-8")
            }.getOrDefault(enterCandidate)
            "${sep}enterUri=$encodedEnterUri"
        } else ""
        return "bilibili://comment/detail/$resolvedBusinessId/$subjectId/$resolvedRootId$targetQuery$enterUriParam"
    }

    // If we have an absolute link and comment IDs, augment it with comment query parameters
    val baseCandidate = when {
        trimmedNative.contains("://") -> trimmedNative
        trimmedUri.contains("://") -> trimmedUri
        else -> null
    }
    if (baseCandidate != null && resolvedRootId > 0L) {
        val sep = if (baseCandidate.contains("?")) "&" else "?"
        val commentParams = buildString {
            append("${sep}comment_root_id=$resolvedRootId")
            if (resolvedTargetId > 0L) {
                append("&comment_secondary_id=$resolvedTargetId")
            }
        }
        return "$baseCandidate$commentParams"
    }

    // Fallback: if nativeUri or uri is an absolute web or scheme link, use it
    if (trimmedNative.contains("://")) {
        return trimmedNative
    }
    if (trimmedUri.contains("://")) {
        return trimmedUri
    }

    return null
}

@Composable
internal fun MessageFeedAvatar(
    avatarUrl: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = avatarUrl,
        contentDescription = "头像",
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

@Composable
internal fun MessageFeedEmpty(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppText(text = text, color = AppContentColors.secondaryText())
    }
}

@Composable
internal fun MessageFeedError(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppText(text = text, color = AppContentColors.secondaryText())
        AppButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            AppText("重试")
        }
    }
}

@Composable
internal fun MessageFeedLoadMore(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (!hasMore && !isLoadingMore) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingMore) {
            com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                size = 24.dp
            )
        } else {
            AppTextButton(onClick = onLoadMore) {
                AppText("加载更多")
            }
        }
    }
}

@Composable
internal fun MessageFeedSectionHeader(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = AppContentColors.accent(),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun MessageFeedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val surfaceSpec = rememberContentCardSurfaceSpec()
    val shape = AppShapes.borderedContainer(surfaceSpec.cornerLevel)
    val defaultContainerColor = if (surfaceSpec.usesTonalContainerTreatment) {
        AppSurfaceTokens.surfaceContainer()
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .messageGlassContainer(
                defaultContainerColor = defaultContainerColor,
                shape = shape,
            )
    ) {
        content()
    }
}

/**
 * 通知卡片骨架（头像版，对齐 @我 / 回复我的 / 收到的赞 的卡片布局）。
 *
 * 复用 [MessageFeedCard] 的容器与 14dp 内边距、46dp 头像，因此骨架与真实卡片行高一致；
 * 颜色用 [rememberStaticSkeletonBlockColor] 固定色，不参与呼吸脉冲——呼吸中的灰块与随后的
 * 内容淡入叠加，观感上就是「闪」。
 */
@Composable
internal fun MessageFeedCardSkeleton(
    modifier: Modifier = Modifier,
    slotIndex: Int = 0,
) {
    val blockColor = rememberStaticSkeletonBlockColor()
    MessageFeedCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp)) {
            ContentSkeletonBlock(
                color = blockColor,
                shape = CircleShape,
                modifier = Modifier.size(46.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(if (slotIndex % 2 == 0) 0.78f else 0.62f)
                        .height(17.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(14.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .height(14.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContentSkeletonBlock(
                        color = blockColor,
                        modifier = Modifier
                            .width(52.dp)
                            .height(12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ContentSkeletonBlock(
                        color = blockColor,
                        modifier = Modifier
                            .width(28.dp)
                            .height(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * 系统通知骨架：标题 + 两行正文 + 右对齐时间，对齐 [MessageFeedCard] 的 14dp 内边距与 8dp 行距。
 */
@Composable
internal fun MessageFeedNoticeSkeleton(
    modifier: Modifier = Modifier,
    slotIndex: Int = 0,
) {
    val blockColor = rememberStaticSkeletonBlockColor()
    MessageFeedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .fillMaxWidth(if (slotIndex % 2 == 0) 0.46f else 0.36f)
                    .height(17.dp),
            )
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(14.dp),
            )
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(14.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .align(Alignment.End)
                    .fillMaxWidth(0.28f)
                    .height(12.dp),
            )
        }
    }
}
