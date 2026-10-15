package com.android.purebilibili.feature.following

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.data.model.response.FollowingUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class FansListState(
    val users: List<FollowingUser> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

internal class FansListViewModel(private val mid: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(FansListState())
    val state = mutableState.asStateFlow()
    private var page = 1

    fun load(refresh: Boolean = false) {
        val current = mutableState.value
        if (current.loading || (!refresh && !current.hasMore && current.error == null)) return
        if (mid <= 0) {
            mutableState.value = current.copy(error = "无效的用户 UID", hasMore = false)
            return
        }
        val requestPage = if (refresh) 1 else page
        page = requestPage
        mutableState.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val response = NetworkModule.api.getFollowers(vmid = mid, pn = requestPage)
                val data = response.data
                if (response.code != 0 || data == null) {
                    error(response.message.ifBlank { "加载粉丝失败（${response.code}）" })
                }
                val incoming = data.list.orEmpty()
                val users = ((if (requestPage == 1) emptyList() else current.users) + incoming)
                    .distinctBy { it.mid }
                mutableState.value = FansListState(
                    users = users,
                    total = data.total,
                    hasMore = incoming.isNotEmpty() && users.size < data.total &&
                        (requestPage == 1 || users.size > current.users.size),
                )
                page = requestPage + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = current.copy(
                    loading = false,
                    error = failure.message ?: "加载粉丝失败，请重试",
                )
            }
        }
    }
}

@Composable
fun FansListScreen(mid: Long, onBack: () -> Unit, onUserClick: (Long) -> Unit, onFansClick: (Long) -> Unit) {
    val model: FansListViewModel = viewModel(
        key = "fans_$mid",
        factory = viewModelFactory { initializer { FansListViewModel(mid) } },
    )
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    FansListContent(state, onBack, onUserClick, onFansClick, onRefresh = { model.load(true) }, onLoadMore = { model.load() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FansListContent(
    state: FansListState,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    onFansClick: (Long) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    ImmersiveAppScaffold(
        topBar = {
            AppTopBar(
                title = "粉丝",
                navigationIcon = {
                    AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), contentDescription = "返回") }
                },
                actions = { AppTextButton(onClick = onRefresh, enabled = !state.loading) { AppText("刷新") } },
            )
        },
    ) { padding ->
        AppLazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { AppText("共 ${state.total} 位粉丝", modifier = Modifier.padding(16.dp)) }
            items(state.users, key = { it.mid }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUserClick(user.mid) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        model = user.face,
                        contentDescription = "${user.uname}的头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(user.uname, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.sign.isNotBlank()) {
                            AppText(user.sign, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AppTextButton(onClick = { onFansClick(user.mid) }) { AppText("粉丝") }
                }
            }
            item(key = "page_status") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        state.loading -> AppCircularProgressIndicator()
                        state.error != null -> {
                            AppText(state.error)
                            AppTextButton(onClick = onLoadMore) { AppText("重试") }
                        }
                        state.users.isEmpty() -> AppText("暂无粉丝")
                        state.hasMore -> {
                            LaunchedEffect(state.users.size) { onLoadMore() }
                            AppTextButton(onClick = onLoadMore) { AppText("加载更多") }
                        }
                        else -> AppText("已显示全部粉丝")
                    }
                }
            }
        }
    }
}
