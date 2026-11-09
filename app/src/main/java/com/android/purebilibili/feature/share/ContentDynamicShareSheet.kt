package com.android.purebilibili.feature.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.DynamicRepostContentItem
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.repository.VideoDynamicShareRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun buildShareRichContents(text: String, mentions: Map<String, Long>): List<DynamicRepostContentItem> {
    if (text.isEmpty()) return emptyList()
    val keys = mentions.keys.filter { it.isNotBlank() }.sortedByDescending { it.length }
    if (keys.isEmpty()) return listOf(DynamicRepostContentItem(text, 1, ""))
    val regex = Regex(keys.joinToString("|") { Regex.escape("@$it") + "(?=\\s|$)" })
    return buildList {
        var offset = 0
        regex.findAll(text).forEach { match ->
            if (match.range.first > offset) add(DynamicRepostContentItem(text.substring(offset, match.range.first), 1, ""))
            add(DynamicRepostContentItem(match.value, 2, mentions.getValue(match.value.drop(1)).toString()))
            offset = match.range.last + 1
        }
        if (offset < text.length) add(DynamicRepostContentItem(text.substring(offset), 1, ""))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentDynamicShareSheet(payload: ContentSharePayload, onDismiss: () -> Unit, modifier: Modifier = Modifier, presentationOverride: com.android.purebilibili.core.ui.AppModalPresentation? = null, onSuccess: () -> Unit = {}) {
    var text by remember(payload.key) { mutableStateOf("") }
    var mentions by remember(payload.key) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var alsoComment by remember(payload.key) { mutableStateOf(false) }
    var posting by remember(payload.key) { mutableStateOf(false) }
    var published by remember(payload.key) { mutableStateOf(false) }
    var error by remember(payload.key) { mutableStateOf<String?>(null) }
    var selectedTopic by remember(payload.key) { mutableStateOf<com.android.purebilibili.data.model.response.DynamicCreateTopic?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    AppModalBottomSheet(modifier = modifier, presentationOverride = presentationOverride, onDismissRequest = { if (!posting) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (presentationOverride == com.android.purebilibili.core.ui.AppModalPresentation.SideSheet) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                })
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText("分享到动态")
            Column(
                Modifier
                    .weight(1f, fill = presentationOverride == com.android.purebilibili.core.ui.AppModalPresentation.SideSheet)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (payload.cover.isNotBlank()) AsyncImage(payload.cover, null, Modifier.size(56.dp))
                    AppText(payload.title, maxLines = 2, modifier = Modifier.weight(1f))
                }
                AppTextField(value = text, onValueChange = { if (!posting && !published) text = it.take(2000) }, placeholder = "说点什么吧…（可选）", singleLine = false, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                if (!published) ShareRichTextTools(
                    contentKey = payload.key, enabled = !posting,
                    onAppend = { text = (text + it).take(2000) },
                    onMention = { user -> text = (text + "@${user.name} ").take(2000); mentions = mentions + (user.name to user.uid) },
                    onTopic = { topic -> text = (text + "#${topic.name}# ").take(2000); selectedTopic = com.android.purebilibili.data.model.response.DynamicCreateTopic(topic.id, topic.name) },
                )
                if (payload.replyOid > 0 || payload.bvid.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    AppCheckbox(checked = alsoComment, onCheckedChange = { if (!posting && !published) alsoComment = it })
                    AppText("同时评论原内容")
                }
                error?.let { AppText(it) }
                AppText("${text.length}/2000")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppTextButton(onClick = onDismiss, enabled = !posting, modifier = Modifier.weight(1f)) { AppText(if (published) "完成" else "取消") }
                AppButton(enabled = !posting, modifier = Modifier.weight(1f), onClick = {
                    if (posting) return@AppButton
                    posting = true
                    error = null
                    scope.launch {
                        try {
                            val csrf = TokenManager.csrfCache.orEmpty()
                            check(csrf.isNotBlank()) { "请先登录后分享到动态" }
                            val resource = payload.resourceId.takeIf { it > 0 } ?: if (payload.bvid.isNotBlank()) {
                                val response = NetworkModule.api.getVideoInfo(payload.bvid)
                                check(response.code == 0) { response.message }
                                response.data?.aid ?: kotlin.error("视频编号不可用")
                            } else kotlin.error("内容编号不可用")
                            if (!published) {
                                VideoDynamicShareRepository.shareResource(resource, payload.dynamicType ?: kotlin.error("当前内容不支持分享到动态"), text, buildShareRichContents(text, mentions), selectedTopic?.takeIf { "#${it.name}#" in text }).getOrThrow()
                                published = true
                                onSuccess()
                            }
                            if (alsoComment && text.isNotBlank()) {
                                val response = NetworkModule.api.addReply(oid = payload.replyOid.takeIf { it > 0 } ?: resource, type = payload.replyType, message = text, csrf = csrf)
                                check(response.code == 0) { "动态已发布，评论失败：${response.message}；可单独重试评论" }
                            }
                            android.widget.Toast.makeText(context, if (alsoComment && text.isNotBlank()) "已分享到动态并同步评论" else "已分享到动态", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "分享失败，请重试" } finally { posting = false }
                    }
                }) { AppText(if (posting) "发布中…" else if (published) "重试评论" else "发布") }
            }
        }
    }
}
