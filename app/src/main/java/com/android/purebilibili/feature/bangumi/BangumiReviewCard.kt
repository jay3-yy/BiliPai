@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.android.purebilibili.feature.bangumi

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiReviewItem
import com.android.purebilibili.data.model.response.BangumiReviewType

@Composable
internal fun BangumiReviewCard(
    item: BangumiReviewItem,
    reviewType: BangumiReviewType,
    busy: Boolean,
    onAuthorClick: () -> Unit,
    onArticleClick: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val longReview = reviewType == BangumiReviewType.LONG
    val secondary = AppContentColors.secondaryText()
    Column(
        modifier = modifier.fillMaxWidth()
            .combinedClickable(
                enabled = !busy,
                onClick = { if (longReview) onArticleClick() },
                onLongClick = if (longReview) null else onShowMore,
                onLongClickLabel = if (longReview) null else "短评操作",
            )
            .padding(12.dp),
    ) {
        item.author?.let { author ->
            Row(
                modifier = Modifier.clickable(onClick = onAuthorClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(author.avatar),
                    contentDescription = "${author.uname}的头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(
                            author.uname,
                            fontSize = 13.sp,
                            color = if ((author.vip?.status ?: 0) > 0 && author.vip?.type == 2) userVipPink() else secondary,
                        )
                        UserLevelBadge(level = author.level, height = 11.dp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.push_time_str.isNotBlank()) {
                            AppText(item.push_time_str, fontSize = 12.sp, color = secondary)
                            Spacer(Modifier.width(10.dp))
                        }
                        repeat(5) { index ->
                            val filled = index < item.score / 2
                            AppIcon(
                                if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (index == 0) "${item.score / 2}星" else null,
                                tint = if (filled) Color(0xFFFFAD35) else secondary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        if (item.title.isNotBlank()) {
            AppText(item.title, fontSize = 15.sp, lineHeight = 26.25.sp, fontWeight = FontWeight.Bold)
        }
        if (longReview) {
            AppText(item.content, fontSize = 14.sp, lineHeight = 24.5.sp)
        } else {
            SelectionContainer { AppText(item.content, fontSize = 14.sp, lineHeight = 24.5.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!longReview) {
                AppIconButton(onClick = onDislike, enabled = !busy) {
                    AppIcon(
                        if (item.stat?.disliked == 1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = if (item.stat?.disliked == 1) "取消点踩" else "点踩",
                        tint = if (item.stat?.disliked == 1) AppContentColors.accent() else secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // 长评点赞量只展示，点击整条内容打开文章；短评支持点赞与点踩。
            val likeContent: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppIcon(
                        if (item.stat?.liked == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = if (longReview) "点赞量" else if (item.stat?.liked == 1) "取消点赞" else "点赞",
                        tint = if (item.stat?.liked == 1) AppContentColors.accent() else secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    AppText(
                        FormatUtils.formatStat((item.stat?.likes ?: 0).toLong()),
                        fontSize = 12.sp,
                        color = if (item.stat?.liked == 1) AppContentColors.accent() else secondary,
                    )
                }
            }
            if (longReview) {
                Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.Center) { likeContent() }
            } else {
                AppTextButton(onClick = onLike, enabled = !busy) { likeContent() }
            }
        }
    }
}
