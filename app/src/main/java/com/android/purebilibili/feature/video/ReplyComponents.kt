package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.theme.TextPrimary
import com.android.purebilibili.core.theme.TextSecondary
import com.android.purebilibili.core.theme.TextTertiary
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ReplyItem
import java.text.SimpleDateFormat
import java.util.*

// ReplyHeader 保持不变
@Composable
fun ReplyHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "评论",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = FormatUtils.formatStat(count.toLong()),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

// 单条评论组件
@Composable
fun ReplyItemView(
    item: ReplyItem,
    emoteMap: Map<String, String> = emptyMap(), // 全局兜底 Map
    onClick: () -> Unit
) {
    // 🔥🔥 [核心逻辑] 动态合并表情 Map
    // 优先使用评论自带的 emote 数据 (item.content.emote)，覆盖全局兜底数据
    val localEmoteMap = remember(item.content.emote, emoteMap) {
        val mergedMap = emoteMap.toMutableMap()
        item.content.emote?.forEach { (key, value) ->
            mergedMap[key] = value.url
        }
        mergedMap
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(item.member.avatar))
                    .crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF0F0F0))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 昵称 + 等级
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.member.uname,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (item.member.vip?.status == 1) BiliPink else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LevelTag(level = item.member.level_info.current_level)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 🔥🔥 使用 EmojiText 解析表情 (传入合并后的 Map)
                EmojiText(
                    text = item.content.message,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    emoteMap = localEmoteMap
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 底部信息
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(item.ctime), fontSize = 11.sp, color = TextTertiary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Outlined.ThumbUp, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (item.like == 0) "点赞" else item.like.toString(), fontSize = 11.sp, color = TextTertiary)
                }

                // 二级评论
                if (!item.replies.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF6F7F8), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        item.replies.take(3).forEach { subReply ->
                            // 二级评论处理：如果二级评论也有 emote 字段，最好也做合并
                            // 这里简化处理，直接复用 localEmoteMap (通常够用)
                            // 如果二级评论有独有表情，可参照上面逻辑再做一次 merge
                            Row {
                                Text(
                                    text = "${subReply.member.uname}: ",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                                // 复用 EmojiText
                                EmojiText(
                                    text = subReply.content.message,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    emoteMap = localEmoteMap
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (item.rcount > 3) {
                            Text("共${item.rcount}条回复 >", fontSize = 12.sp, color = BiliPink, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
    }
}

// 🔥🔥 [核心组件] 支持网络图片的 EmojiText
@Composable
fun EmojiText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    emoteMap: Map<String, String> // 传入 [doge] -> url
) {
    val annotatedString = buildAnnotatedString {
        val pattern = "\\[(.*?)\\]".toRegex()
        var lastIndex = 0

        pattern.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            val emojiKey = matchResult.value

            // 查找 Map 中是否有该表情 URL
            if (emoteMap.containsKey(emojiKey)) {
                appendInlineContent(id = emojiKey, alternateText = emojiKey)
            } else {
                append(emojiKey)
            }
            lastIndex = matchResult.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    // 动态生成 InlineContent
    val inlineContent = emoteMap.mapValues { (_, url) ->
        InlineTextContent(
            // 设置表情大小，1.4em 垂直居中
            Placeholder(width = 1.4.em, height = 1.4.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
        ) {
            // 使用 Coil 加载网络图片
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        fontSize = fontSize,
        color = color,
        lineHeight = (fontSize.value * 1.5).sp
    )
}

// LevelTag 和 formatTime 保持不变
@Composable
fun LevelTag(level: Int) {
    Text(
        text = "LV$level",
        fontSize = 8.sp,
        color = Color(0xFF909090),
        modifier = Modifier
            .border(0.5.dp, Color(0xFFC0C0C0), RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp, vertical = 0.dp)
    )
}

fun formatTime(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
    return sdf.format(date)
}