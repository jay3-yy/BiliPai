package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.grpc.DynamicReactionApi
import com.android.purebilibili.core.network.grpc.DynamicReactionUser
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.feature.video.playback.policy.VideoUiWorkPermission
import com.android.purebilibili.feature.video.playback.policy.runVisibleVideoUiWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicReactionSheet(item: DynamicItem, onDismiss: () -> Unit, onUserClick: (Long) -> Unit) {
    var users by remember(item.id_str) { mutableStateOf<List<DynamicReactionUser>>(emptyList()) }
    var offset by remember(item.id_str) { mutableStateOf("") }
    var hasMore by remember(item.id_str) { mutableStateOf(true) }
    var loading by remember(item.id_str) { mutableStateOf(false) }
    var loaded by remember(item.id_str) { mutableStateOf(false) }
    var error by remember(item.id_str) { mutableStateOf<String?>(null) }
    var title by remember(item.id_str) { mutableStateOf("赞和转发") }
    val requestPermission = remember(item.id_str) { MutableStateFlow(VideoUiWorkPermission.Hidden) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    suspend fun load() {
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            val page = runVisibleVideoUiWork(requestPermission) {
                DynamicReactionApi.load(item, offset)
            }
            users = (users + page.users).distinctBy { it.mid to it.action }
            hasMore = page.hasMore && page.offset.isNotBlank() && page.offset != offset
            offset = page.offset
            loaded = true
            if (page.title.isNotBlank()) title = page.title
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val message = failure.message ?: "加载失败"
            error = when {
                "-101" in message -> "此接口需要 App 登录凭据，请检查当前登录方式。\n$message"
                "-352" in message -> "请求被风控拦截，可稍后重试。\n$message"
                "-403" in message -> "服务器拒绝访问该列表。\n$message"
                else -> message
            }
        } finally {
            loading = false
        }
    }
    LaunchedEffect(item.id_str) { load() }
    LaunchedEffect(item.id_str, listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = layout.totalItemsCount > 0 &&
                lastVisible >= (layout.totalItemsCount - 4).coerceAtLeast(0)
            // Cursor changes also trigger a check when a page does not fill the viewport.
            offset to (loaded && hasMore && !loading && error == null && nearEnd)
        }.collect { (_, shouldLoad) ->
            if (shouldLoad) load()
        }
    }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        // 弹层使用自己的窗口焦点；自动分页和手动重试共用许可，恢复后继续当前游标。
        val workActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
        DisposableEffect(requestPermission, workActive) {
            requestPermission.value = if (workActive) VideoUiWorkPermission.Visible else VideoUiWorkPermission.Hidden
            onDispose { requestPermission.value = VideoUiWorkPermission.Hidden }
        }
        AppText(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).appScrollBoundaryHaptics(listState),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(
                items = users,
                key = { index, user -> "${user.mid}_${user.action}_$index" },
                contentType = { _, _ -> "reaction_user" },
            ) { _, user ->
                Row(
                    Modifier.fillMaxWidth().appActionClickable { onDismiss(); onUserClick(user.mid) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(model = user.face, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                    Column {
                        AppText(user.name, style = MaterialTheme.typography.bodyLarge)
                        AppText(user.action, color = AppContentColors.secondaryText())
                    }
                }
            }
            item {
                when {
                    loading -> AppText("正在加载…", modifier = Modifier.padding(16.dp))
                    error != null -> Column {
                        AppText(error.orEmpty(), color = AppContentColors.error())
                        AppTextButton(onClick = { scope.launch { load() } }) { AppText("重试") }
                    }
                    loaded && users.isEmpty() -> AppText("暂无赞和转发用户")
                    hasMore -> AppText("继续滑动加载", color = AppContentColors.secondaryText())
                    else -> AppText("已显示全部", color = AppContentColors.secondaryText())
                }
            }
        }
    }
}
