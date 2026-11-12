package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.data.model.response.DynamicPublishMention
import com.android.purebilibili.data.model.response.DynamicPublishTopic
import com.android.purebilibili.data.model.response.DynamicTopicSearchItem
import com.android.purebilibili.data.model.response.MentionSearchUser
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.DynamicCreateRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.Role

@Composable
internal fun DynamicMentionPickerDialog(
    onDismiss: () -> Unit,
    onSelected: (List<DynamicPublishMention>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<MentionSearchUser>>(emptyList()) }
    var selected by remember { mutableStateOf<List<DynamicPublishMention>>(emptyList()) }
    fun toggle(user: MentionSearchUser) {
        selected = if (selected.any { it.uid == user.uid }) selected.filterNot { it.uid == user.uid }
        else selected + DynamicPublishMention(user.uid, user.name)
    }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    fun dismiss() {
        searchJob?.cancel()
        onDismiss()
    }
    fun search() {
        searchJob?.cancel()
        loading = true
        message = null
        val requestedQuery = query
        searchJob = scope.launch {
            CommentRepository.searchMentionUsers(requestedQuery).fold(
                onSuccess = {
                    if (query == requestedQuery) {
                        users = it
                        message = if (it.isEmpty()) "没有找到用户" else null
                    }
                },
                onFailure = { if (query == requestedQuery) { users = emptyList(); message = it.message ?: "搜索失败" } },
            )
            loading = false
        }
    }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) kotlinx.coroutines.delay(300)
        search()
    }
    PublishPickerSheet(
        onDismissRequest = ::dismiss,
        title = { AppText("@ 用户") },
        text = {
            PublishPickerContent(
                query = query,
                onQueryChange = { query = it; searchJob?.cancel(); loading = false; users = emptyList(); message = null },
                placeholder = "输入你想@的人",
                actionLabel = if (loading) "搜索中…" else "搜索",
                onSearch = ::search,
                message = message,
            ) {
                if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    selected.forEach { user ->
                        AppTextButton(onClick = { selected = selected.filterNot { it.uid == user.uid } }) {
                            AppText("${user.name} ×")
                        }
                    }
                }
                users.forEach { user ->
                    AppListItem(
                        headlineContent = { AppText(user.name) },
                        supportingContent = { AppText("${user.fans} 粉丝") },
                        leadingContent = {
                            AsyncImage(
                                model = user.face,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(AppSpacingTokens.TripleExtraLarge)
                                    .clip(AppShapes.container(ContainerLevel.Pill)),
                                contentScale = ContentScale.Crop,
                            )
                        },
                        trailingContent = {
                            AppCheckbox(checked = selected.any { it.uid == user.uid }, onCheckedChange = null)
                        },
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = selected.any { it.uid == user.uid },
                            role = Role.Checkbox,
                            onValueChange = { toggle(user) },
                        ),
                    )
                }
            }
        },
        confirmButton = { AppTextButton(enabled = selected.isNotEmpty(), onClick = { onSelected(selected) }) { AppText("确定（${selected.size}）") } },
        dismissButton = { AppTextButton(onClick = ::dismiss) { AppText("取消") } },
    )
}

@Composable
internal fun DynamicTopicPickerDialog(
    onDismiss: () -> Unit,
    onSelected: (DynamicPublishTopic) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf<List<DynamicTopicSearchItem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var activeQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    fun search(loadMore: Boolean = false) {
        if (loading) return
        loading = true
        message = null
        val requestedQuery = if (loadMore) activeQuery else query
        val requestedPage = if (loadMore) page + 1 else 1
        searchJob = scope.launch {
            DynamicCreateRepository.searchPublishTopics(requestedQuery, requestedPage).fold(
                onSuccess = { result ->
                    if (query == requestedQuery) {
                        topics = (if (loadMore) topics + result.topic_items else result.topic_items).distinctBy { it.id }
                        page = requestedPage
                        activeQuery = requestedQuery
                        hasMore = result.page_info?.has_more ?: (result.topic_items.size >= 20)
                        message = if (topics.isEmpty()) "没有找到话题" else null
                    }
                },
                onFailure = { if (query == requestedQuery) message = it.message ?: "搜索失败" },
            )
            loading = false
        }
    }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) kotlinx.coroutines.delay(300)
        search(false)
    }
    PublishPickerSheet(
        onDismissRequest = onDismiss,
        title = { AppText("选择话题") },
        text = {
            PublishPickerContent(
                query = query,
                onQueryChange = { searchJob?.cancel(); loading = false; query = it; topics = emptyList(); hasMore = false; message = null },
                placeholder = "搜索话题",
                actionLabel = if (loading) "搜索中…" else "搜索",
                onSearch = { search(false) },
                message = message,
            ) {
                topics.forEach { topic ->
                    AppListItem(
                        headlineContent = { AppText("#${topic.name}#") },
                        supportingContent = topic.stat_desc.takeIf(String::isNotBlank)?.let { stat ->
                            { AppText(stat) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .appActionClickable { onSelected(DynamicPublishTopic(topic.id, topic.name)) },
                    )
                }
                if (hasMore) AppTextButton(onClick = { search(true) }, enabled = !loading) { AppText(if (loading) "加载中…" else "加载更多") }
            }
        },
        confirmButton = {},
        dismissButton = { AppDialogAction(onClick = onDismiss) { AppText("取消") } },
    )
}

@Composable
internal fun DynamicEmotePanel(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var emotes by remember { mutableStateOf(DynamicEmoteCatalog.snapshot()) }
    var packages by remember { mutableStateOf(DynamicEmoteCatalog.snapshotPackages()) }
    var selectedPackageId by remember { mutableStateOf(packages.firstOrNull()?.id) }
    var loading by remember { mutableStateOf(emotes.isEmpty()) }
    val sessionKey = DynamicEmoteCatalog.currentSessionKey()
    LaunchedEffect(sessionKey) {
        loading = true
        emotes = DynamicEmoteCatalog.ensureLoaded()
        packages = DynamicEmoteCatalog.snapshotPackages()
        if (packages.none { it.id == selectedPackageId }) selectedPackageId = packages.firstOrNull()?.id
        loading = false
    }
    if (emotes.isEmpty()) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
            AppText(if (loading) "正在加载表情…" else "暂无表情")
        }
    } else {
        Column(modifier) {
            val displayedEmotes = packages.firstOrNull { it.id == selectedPackageId }?.emote
                ?.associate { it.text to it.url } ?: emotes
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(displayedEmotes.entries.toList(), key = { it.key }) { (text, url) ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(48.dp).appActionClickable(enabled = enabled) { onSelected(text) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(model = emotes[text] ?: url, contentDescription = text, modifier = Modifier.size(32.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth()) {
                items(packages.size, key = { packages[it].id }) { index ->
                    val pkg = packages[index]
                    AppTextButton(onClick = { selectedPackageId = pkg.id }) {
                        AppText(pkg.text, color = if (selectedPackageId == pkg.id) AppContentColors.accent() else AppContentColors.secondaryText())
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishPickerContent(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    actionLabel: String,
    onSearch: () -> Unit,
    message: String?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            singleLine = true,
        )
        AppTextButton(onClick = onSearch) { AppText(actionLabel) }
        message?.let { AppText(it, color = AppContentColors.secondaryText()) }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            content = { content() },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PublishPickerSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    com.android.purebilibili.core.ui.AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = com.android.purebilibili.core.ui.AppSurfaceTokens.background(),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f).imePadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                dismissButton()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
                confirmButton()
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { text() }
        }
    }
}
