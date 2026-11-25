package com.android.purebilibili.feature.following

import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.rememberSystemWindowNavigationControl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.data.model.response.FollowingUser

@Composable
fun FansListScreen(mid: Long, onBack: () -> Unit, onUserClick: (Long) -> Unit, onFansClick: (Long) -> Unit) {
    val model: FansListViewModel = viewModel(
        key = "fans_$mid",
        factory = viewModelFactory { initializer { FansListViewModel(mid) } },
    )
    val context = LocalContext.current
    val identity by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val rawState by model.state.collectAsStateWithLifecycle()
    val state = rawState.takeIf { it.identity == identity } ?: FansListState(identity = identity, loading = true)
    LaunchedEffect(model) { model.load() }
    LaunchedEffect(model, context) {
        model.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    FansListContent(state, onBack, onUserClick, onFansClick, onRefresh = { model.load(true) }, onLoadMore = { model.load() }, onRemoveFan = model::removeFan)
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
    onRemoveFan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmTarget by remember(state.identity) { mutableStateOf<FollowingUser?>(null) }
    ImmersiveAppScaffold(
        modifier = modifier,
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
            item { AppText(state.total?.let { "共 $it 位粉丝" } ?: "粉丝数量待加载", modifier = Modifier.padding(16.dp)) }
            items(state.users, key = { it.mid }) { user ->
                val userControl = rememberSystemWindowNavigationControl(
                    BiliPaiNavKey.Space(user.mid),
                    AppShapes.container(ContainerLevel.Card),
                ) {
                    onUserClick(user.mid)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = userControl.onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).then(userControl.modifier),
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
                    }

                    if (state.canManage && user.mid != state.identity.mid) {
                        AppWindowActionMenu(groups = listOf(listOf(
                            AppWindowAction(label = "查看粉丝", icon = Icons.Outlined.People, onClick = { onFansClick(user.mid) }),
                            AppWindowAction(
                                label = if (state.removingMid == user.mid) "移除中…" else "移除粉丝",
                                icon = Icons.Outlined.PersonRemove,
                                enabled = state.removingMid == null,
                                onClick = { confirmTarget = user },
                            ),
                        ))) { AppIcon(Icons.Outlined.MoreVert, "粉丝操作") }
                    } else {
                        AppTextButton(onClick = { onFansClick(user.mid) }) { AppText("粉丝") }
                    }
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
    confirmTarget?.let { target ->
        RemoveFanConfirmDialog(
            name = target.uname,
            onDismiss = { confirmTarget = null },
            onConfirm = {
                confirmTarget = null
                onRemoveFan(target.mid)
            },
        )
    }

}
