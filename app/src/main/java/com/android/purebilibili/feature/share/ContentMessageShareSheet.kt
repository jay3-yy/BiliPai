package com.android.purebilibili.feature.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.data.repository.MessageShareGrpcRepository
import com.android.purebilibili.feature.message.MessageUserInfoLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class ShareContact(val mid: Long, val name: String, val avatar: String)

/** 每次打开分享独立持有发送进度；附言失败只重发附言，避免重复投递卡片。 */
private class MessageShareState(val payload: ContentSharePayload, private val senderMid: Long?) {
    var contacts by mutableStateOf<List<ShareContact>>(emptyList())
    var selected by mutableStateOf<Set<Long>>(emptySet())
    var query by mutableStateOf("")
    var note by mutableStateOf("")
    var loading by mutableStateOf(false)
    var sending by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>(null)
    var more by mutableStateOf(false)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    private val cardSent = mutableSetOf<Long>()
    private val completed = mutableSetOf<Long>()
    private var card: String? = null

    suspend fun load(recent: Boolean) {
        if (loading) return
        loading = true
        error = null
        try {
            check(TokenManager.midCache == senderMid) { "账号已切换，请重新打开分享" }
            val self = senderMid?.takeIf { it > 0 } ?: kotlin.error("请先登录 B 站账号")
            val users = if (recent) {
                val suggested = MessageShareGrpcRepository.getShareTargets(5).getOrNull().orEmpty()
                if (suggested.isNotEmpty()) suggested.map { ShareContact(it.mid, it.name, it.avatarUrl) }
                else MessageRepository.getSessions(size = 30).getOrThrow().session_list.orEmpty()
                    .filter { it.session_type == 1 && it.talker_id > 0 }
                    .map { session ->
                        val info = session.account_info
                        val resolved = if (info?.name.isNullOrBlank()) MessageUserInfoLoader.fetch(session.talker_id) else null
                        ShareContact(session.talker_id, info?.name.orEmpty().ifBlank { resolved?.name.orEmpty().ifBlank { "用户${session.talker_id}" } }, info?.avatarUrl.orEmpty().ifBlank { resolved?.face.orEmpty() })
                    }
            } else {
                val result = NetworkModule.api.getFollowings(self, pn = page, ps = 50)
                check(result.code == 0) { result.message.ifBlank { "加载关注列表失败" } }
                val data = result.data ?: kotlin.error("关注列表不可用")
                val list = data.list.orEmpty()
                hasMore = list.isNotEmpty() && page * 50 < data.total
                page++
                list.map { ShareContact(it.mid, it.uname, it.face) }
            }
            contacts = (contacts + users).filter { it.mid > 0 && it.mid != self }.distinctBy { it.mid }
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "加载联系人失败" } finally { loading = false }
    }

    suspend fun addMid() {
        val mid = query.trim().toLongOrNull()?.takeIf { it > 0 && it != TokenManager.midCache } ?: return
        loading = true
        error = null
        try {
            val info = MessageUserInfoLoader.fetch(mid) ?: kotlin.error("未找到该用户")
            contacts = (listOf(ShareContact(mid, info.name, info.face)) + contacts).distinctBy { it.mid }
            selected = selected + mid
            query = ""
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "获取用户失败" } finally { loading = false }
    }

    suspend fun send(onSuccess: (Int) -> Unit) {
        if (sending || selected.isEmpty()) return
        sending = true
        error = null
        val pending = selected.toList()
        try {
            check(TokenManager.midCache == senderMid) { "账号已切换，请重新打开分享" }
            val content = card ?: payload.cardContent().also { card = it }
            val failed = mutableSetOf<Long>()
            pending.forEach { mid ->
                try {
                    if (mid !in cardSent) {
                        MessageShareGrpcRepository.sendDynamicShare(mid, content, payload.messageType).getOrThrow()
                        cardSent += mid
                    }
                    if (mid !in completed && note.isNotBlank()) {
                        MessageRepository.sendTextMessage(mid, note.trim()).getOrThrow()
                    }
                    completed += mid
                } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { failed += mid; error = failure.message ?: "发送失败" }
            }
            selected = failed
            status = "已发送 ${completed.size} 人" + if (failed.isEmpty()) "" else "，${failed.size} 人失败；可重试"
            if (failed.isEmpty()) onSuccess(completed.size)
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "分享失败，请重试" } finally { sending = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentMessageShareSheet(payload: ContentSharePayload, onDismiss: () -> Unit, modifier: Modifier = Modifier, presentationOverride: com.android.purebilibili.core.ui.AppModalPresentation? = null, onSuccess: (Int) -> Unit = {}) {
    val senderMid = TokenManager.midCache
    val state = remember(payload.key, senderMid) { MessageShareState(payload, senderMid) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.load(recent = true) }
    val visible = state.contacts.filter { state.query.isBlank() || it.name.contains(state.query.trim(), true) || it.mid.toString().contains(state.query.trim()) }
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.38f).coerceIn(100f, 360f).dp
    AppModalBottomSheet(modifier = modifier, presentationOverride = presentationOverride, onDismissRequest = { if (!state.sending) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (presentationOverride == com.android.purebilibili.core.ui.AppModalPresentation.SideSheet) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp)
                })
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText("分享给 B 站好友", style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier
                    .weight(1f, fill = presentationOverride == com.android.purebilibili.core.ui.AppModalPresentation.SideSheet)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(payload.title, maxLines = 2)
                AppSearchField(query = state.query, onQueryChange = { state.query = it }, placeholder = "搜索联系人或输入 UID", modifier = Modifier.fillMaxWidth())
                AppLazyColumn(Modifier.fillMaxWidth().heightIn(max = listHeight)) {
                    items(visible, key = { it.mid }) { user ->
                        AppListItem(
                            headlineContent = { AppText(user.name.ifBlank { "用户${user.mid}" }) },
                            leadingContent = { AsyncImage(user.avatar, null, modifier = Modifier.size(40.dp)) },
                            trailingContent = { AppCheckbox(checked = user.mid in state.selected, onCheckedChange = { checked -> if (!state.sending) state.selected = if (checked) state.selected + user.mid else state.selected - user.mid }) },
                            modifier = Modifier.clickable(enabled = !state.sending) { state.selected = if (user.mid in state.selected) state.selected - user.mid else state.selected + user.mid },
                        )
                    }
                    if (visible.isEmpty() && !state.loading) item { AppText("暂无匹配联系人，可加载关注用户或通过 UID 添加") }
                    if (state.loading) item { AppText("正在加载联系人…") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.more || state.hasMore) AppTextButton(enabled = !state.loading && !state.sending, onClick = { state.more = true; scope.launch { state.load(recent = false) } }) { AppText(if (state.more) "加载更多关注" else "更多联系人") }
                    if (state.query.trim().toLongOrNull() != null) AppTextButton(enabled = !state.loading && !state.sending, onClick = { scope.launch { state.addMid() } }) { AppText("添加此 UID") }
                    if (state.error != null) AppTextButton(enabled = !state.loading && !state.sending, onClick = { scope.launch { state.load(recent = !state.more) } }) { AppText("重试加载") }
                }
                AppTextField(value = state.note, onValueChange = { if (!state.sending) state.note = it.take(100) }, placeholder = "说说你的想法…（可选，100 字以内）", modifier = Modifier.fillMaxWidth())
                state.error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
                state.status?.let { AppText(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(onClick = onDismiss, enabled = !state.sending, modifier = Modifier.weight(1f)) { AppText("取消") }
                AppButton(onClick = { scope.launch { state.send { onSuccess(it); onDismiss() } } }, enabled = !state.sending && state.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { AppText(if (state.sending) "发送中…" else "发送给 ${state.selected.size} 人") }
            }
        }
    }
}
