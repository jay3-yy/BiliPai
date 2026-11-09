package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.network.DynamicRepostContentItem
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.DynamicCreateTopic
import com.android.purebilibili.feature.share.ShareRichTextTools
import com.android.purebilibili.feature.share.buildShareRichContents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostDialog(
    onDismiss: () -> Unit,
    contentKey: String = "repost",
    modifier: Modifier = Modifier,
    onRepost: (String, Boolean, List<DynamicRepostContentItem>, DynamicCreateTopic?, (Boolean) -> Unit) -> Unit,
) {
    var text by remember(contentKey) { mutableStateOf("") }
    var mentions by remember(contentKey) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var topic by remember(contentKey) { mutableStateOf<DynamicCreateTopic?>(null) }
    var alsoComment by remember(contentKey) { mutableStateOf(false) }
    var posting by remember(contentKey) { mutableStateOf(false) }
    AppModalBottomSheet(modifier = modifier, onDismissRequest = { if (!posting) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText("转发动态")
            AppTextField(value = text, onValueChange = { if (!posting) text = it.take(2000) }, placeholder = "说点什么吧…（可选）", singleLine = false, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            ShareRichTextTools(
                contentKey = contentKey, enabled = !posting,
                onAppend = { text = (text + it).take(2000) },
                onMention = { user -> text = (text + "@${user.name} ").take(2000); mentions = mentions + (user.name to user.uid) },
                onTopic = { selected -> text = (text + "#${selected.name}# ").take(2000); topic = DynamicCreateTopic(selected.id, selected.name) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppCheckbox(checked = alsoComment, onCheckedChange = { if (!posting) alsoComment = it })
                AppText("同时转发到评论区")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppTextButton(onClick = onDismiss, enabled = !posting, modifier = Modifier.weight(1f)) { AppText("取消") }
                AppButton(onClick = {
                    if (!posting) {
                        posting = true
                        onRepost(text, alsoComment, buildShareRichContents(text, mentions), topic?.takeIf { "#${it.name}#" in text }) { success -> if (!success) posting = false }
                    }
                }, enabled = !posting, modifier = Modifier.weight(1f)) { AppText(if (posting) "转发中…" else "转发") }
            }
        }
    }
}
