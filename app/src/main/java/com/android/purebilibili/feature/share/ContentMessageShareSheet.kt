package com.android.purebilibili.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppModalPresentation
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
    val isSideSheet = presentationOverride == AppModalPresentation.SideSheet
    AppModalBottomSheet(
        modifier = modifier,
        presentationOverride = presentationOverride,
        dragHandle = if (isSideSheet) null else ({ BottomSheetDefaults.DragHandle() }),
        onDismissRequest = { if (!state.sending) onDismiss() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (isSideSheet) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
                })
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = if (isSideSheet) 12.dp else 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "分享给 B 站好友",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (state.selected.isNotEmpty()) {
                    AppText(
                        text = "已选 ${state.selected.size} 人",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = isSideSheet)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (payload.cover.isNotBlank()) {
                        AsyncImage(
                            model = payload.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 76.dp, height = 48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    AppText(
                        text = payload.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = AppContentColors.primaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                AppSearchField(query = state.query, onQueryChange = { state.query = it }, placeholder = "搜索联系人或输入 UID", modifier = Modifier.fillMaxWidth())
                AppLazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visible, key = { it.mid }) { user ->
                        val isSelected = user.mid in state.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    else Color.Transparent,
                                )
                                .clickable(enabled = !state.sending) {
                                    state.selected = if (isSelected) state.selected - user.mid else state.selected + user.mid
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = user.avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            AppText(
                                text = user.name.ifBlank { "用户${user.mid}" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppContentColors.primaryText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            AppCheckbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (!state.sending) {
                                        state.selected = if (checked) state.selected + user.mid else state.selected - user.mid
                                    }
                                },
                            )
                        }
                    }
                    if (visible.isEmpty() && !state.loading) {
                        item {
                            AppText(
                                text = "暂无匹配联系人，可加载关注用户或通过 UID 添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    if (state.loading) {
                        item {
                            AppText(
                                text = "正在加载联系人…",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.more || state.hasMore) AppTextButton(enabled = !state.loading && !state.sending, onClick = { state.more = true; scope.launch { state.load(recent = false) } }) { AppText(if (state.more) "加载更多关注" else "更多联系人") }
                    if (state.query.trim().toLongOrNull() != null) AppTextButton(enabled = !state.loading && !state.sending, onClick = { scope.launch { state.addMid() } }) { AppText("添加此 UID") }
                    if (state.error != null) AppTextButton(enabled = !state.loading && !state.sending, onClick = { scope.launch { state.load(recent = !state.more) } }) { AppText("重试加载") }
                }
                AppTextField(value = state.note, onValueChange = { if (!state.sending) state.note = it.take(100) }, placeholder = "说说你的想法…（可选，100 字以内）", modifier = Modifier.fillMaxWidth())
                state.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.status?.let { AppText(it, color = AppContentColors.secondaryText(), style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
            }
            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(onClick = onDismiss, enabled = !state.sending, modifier = Modifier.weight(1f)) { AppText("取消") }
                AppButton(onClick = { scope.launch { state.send { onSuccess(it); onDismiss() } } }, enabled = !state.sending && state.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { AppText(if (state.sending) "发送中…" else "发送给 ${state.selected.size} 人") }
            }
        }
    }
}
