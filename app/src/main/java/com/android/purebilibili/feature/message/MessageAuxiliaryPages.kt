package com.android.purebilibili.feature.message

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.repository.*
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.launch
import com.android.purebilibili.core.store.MessageCenterPreferences
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import coil3.compose.AsyncImage

internal data class MessageAuxState(
    val page: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val title: String = "消息设置",
    val settings: List<ImSetting> = emptyList(),
    val keywords: ImBlockedKeywords? = null,
    val users: List<FollowingUser> = emptyList(),
    val hasMore: Boolean = false,
    val userPage: Int = 0,
    val query: String = "",
    val mutationSuccess: Long = 0L,
)

internal class MessageAuxViewModel : ViewModel() {
    private val requests = AccountScopedRequests(viewModelScope)
    private val state = MutableStateFlow(MessageAuxState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var version = 0L
    private var successfulWrites = 0L
    init { requests.observe { loadPage(state.value.page.ifBlank { "settings:0" }) } }

    fun loadPage(page: String, query: String = "") {
        loadJob?.cancel()
        val currentVersion = ++version
        state.value = MessageAuxState(page = page, query = query, mutationSuccess = successfulWrites, title = when {
            page == "keywords" -> "消息屏蔽词"
            page == "blacklist" -> "黑名单"
            page.startsWith("contacts") -> "通讯录"
            else -> "消息设置"
        })
        loadJob = requests.launch {
            when {
                page.startsWith("settings:") -> MessageImRepository.settings(page.substringAfter(':').toIntOrNull() ?: 0).fold(
                    onSuccess = { data -> if (currentVersion == version) state.update { it.copy(loading = false, title = data.title, settings = data.settings) } },
                    onFailure = { fail(it, currentVersion) },
                )
                page == "keywords" -> MessageImRepository.keywords().fold(
                    onSuccess = { data -> if (currentVersion == version) state.update { it.copy(loading = false, keywords = data) } },
                    onFailure = { fail(it, currentVersion) },
                )
                else -> loadUsers(currentVersion, 1)
            }
        }
    }
    private fun fail(error: Throwable, requestedVersion: Long) {
        if (requestedVersion == version) state.update { it.copy(loading = false, busy = false, error = error.message ?: "操作失败") }
    }
    private suspend fun loadUsers(requestedVersion: Long, page: Int) {
        val current = state.value
        val kind = if (current.page == "blacklist") "blacklist" else current.page.substringAfter(':', "following")
        MessageManagementRepository.contacts(kind, page, current.query).fold(onSuccess = { users ->
            if (requestedVersion == version) state.update { state ->
                val merged = (if (page == 1) users else state.users + users).distinctBy { it.mid }
                state.copy(loading = false, busy = false, users = merged, userPage = page,
                    hasMore = users.size >= 50 && (page == 1 || merged.size > state.users.size))
            }
        }, onFailure = { fail(it, requestedVersion) })
    }
    fun more() {
        if (state.value.busy || !state.value.hasMore) return
        val currentVersion = version
        state.update { it.copy(busy = true, error = null) }
        requests.launch { loadUsers(currentVersion, state.value.userPage + 1) }
    }
    fun changeSetting(setting: ImSetting, enabled: Boolean? = null, selectedType: Int? = null) = mutate {
        MessageImRepository.setSetting(setting, enabled, selectedType)
    }
    fun addWord(word: String) = mutate {
        val limits = state.value.keywords ?: return@mutate Result.failure(IllegalStateException("请先加载屏蔽词"))
        val value = word.trim()
        when {
            value.isBlank() -> Result.failure(IllegalArgumentException("请输入屏蔽词"))
            value in limits.words -> Result.failure(IllegalArgumentException("该屏蔽词已存在"))
            limits.listLimit > 0 && limits.words.size >= limits.listLimit -> Result.failure(IllegalArgumentException("屏蔽词数量已达上限"))
            limits.charLimit > 0 && value.codePointCount(0, value.length) > limits.charLimit -> Result.failure(IllegalArgumentException("屏蔽词最多 ${limits.charLimit} 个字"))
            else -> MessageImRepository.addKeyword(value)
        }
    }
    fun removeWord(word: String) = mutate { MessageImRepository.removeKeyword(word) }
    fun unblock(user: FollowingUser) = mutate { MessageManagementRepository.block(user.mid, false) }
    private fun mutate(action: suspend () -> Result<Unit>) {
        if (state.value.busy) return
        val currentVersion = version
        val page = state.value.page
        val query = state.value.query
        state.update { it.copy(busy = true, error = null) }
        requests.launch {
            action().fold(onSuccess = { if (currentVersion == version) { successfulWrites++; loadPage(page, query) } }, onFailure = { fail(it, currentVersion) })
        }
    }
}

/** 消息子页沿用当前材质组件，开关、选择和服务端保存逻辑保持一致。 */
@Composable
internal fun MessageAuxiliaryPage(
    page: String,
    onBack: () -> Unit,
    onPage: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val disabledLikesFlow = remember(context) { MessageCenterPreferences.disabledLikes(context) }
    val disabledLikes by disabledLikesFlow.collectAsStateWithLifecycle(initialValue = false)
    val dynamicBadgeMode by remember(context) { MessageCenterPreferences.dynamicBadgeMode(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.UnreadBadgeMode.NUMBER)
    val messageBadgeMode by remember(context) { MessageCenterPreferences.messageBadgeMode(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.UnreadBadgeMode.NUMBER)
    var badgeEditor by remember { mutableStateOf<Boolean?>(null) }
    val model: MessageAuxViewModel = viewModel(key = "message_aux_$account")
    val rawState by model.uiState.collectAsStateWithLifecycle()
    val state = rawState.takeIf { it.page == page || (page.startsWith("contacts:") && it.page.startsWith("contacts:")) }
        ?: MessageAuxState(page = page)
    LaunchedEffect(page, account) { model.loadPage(page) }
    var selection by remember(page, account) { mutableStateOf<ImSetting?>(null) }
    var wordEditor by remember(page, account) { mutableStateOf(false) }
    var word by remember(page, account) { mutableStateOf("") }
    LaunchedEffect(state.mutationSuccess) { if (state.mutationSuccess > 0) wordEditor = false }
    var pendingWord by remember(page, account) { mutableStateOf<String?>(null) }
    var pendingUnblock by remember(page, account) { mutableStateOf<FollowingUser?>(null) }
    var search by remember(page, account) { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Box(modifier) {
        MessageAppScaffold(topBar = {
            AppTopBar(title = state.title, navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), "返回") } })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                if (page.startsWith("contacts")) {
                    item {
                        AppThemeAdaptiveTabRow(
                            role = AppTabRowRole.SECONDARY,
                            options = listOf(AppSegmentOption("contacts:following", "我的关注"), AppSegmentOption("contacts:fans", "我的粉丝")),
                            selectedValue = state.page.takeIf { it.startsWith("contacts:") } ?: page,
                            onSelectionChange = onPage,
                            // 玻璃指示器覆盖完整槽位；原生路径保留默认策略。
                            liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppCompactInputField(value = search, onValueChange = { search = it }, placeholder = "搜索我的关注", modifier = Modifier.weight(1f))
                            AppTextButton(onClick = { model.loadPage("contacts:following", search.trim()) }) { AppText("搜索") }
                        }
                    }
                }
                if (state.loading) item { AppText("加载中…", Modifier.padding(24.dp)) }
                state.error?.let { error -> item {
                    Column(Modifier.padding(16.dp)) {
                        AppText(error, color = AppContentColors.secondaryText())
                        AppTextButton(onClick = { model.loadPage(page, state.query) }) { AppText("重试") }
                    }
                } }
                if (page == "settings:0") item {
                    MessageSettingRow("禁用收到的赞", "关闭消息页入口及其未读提醒", onClick = { scope.launch { MessageCenterPreferences.setDisabledLikes(context, !disabledLikes) } }) {
                        AppSwitch(disabledLikes, { value -> scope.launch { MessageCenterPreferences.setDisabledLikes(context, value) } })
                    }
                    MessageSettingRow("动态未读标记", "底栏动态入口的未读提醒形态", summary = dynamicBadgeMode.label, onClick = { badgeEditor = true })
                    MessageSettingRow("消息未读标记", "首页顶栏消息入口的未读提醒形态", summary = messageBadgeMode.label, onClick = { badgeEditor = false })
                }
                items(state.settings, key = { it.key }) { setting ->
                    val choose = {
                        when {
                            setting.options.isNotEmpty() -> selection = setting
                            setting.title == "消息屏蔽词" || setting.key == 26 -> onPage("keywords")
                            setting.title == "黑名单" || setting.key == 12 -> onPage("blacklist")
                            setting.pageType != null -> onPage("settings:${setting.pageType}")
                            setting.url.isNotBlank() -> onOpenLink(setting.url)
                            else -> Unit
                        }
                    }
                    MessageSettingRow(
                        title = setting.title.ifBlank { "接收范围" },
                        subtitle = setting.subtitle,
                        summary = setting.summary.ifBlank { setting.options.firstOrNull { it.selected }?.text.orEmpty() },
                        enabled = !state.busy,
                        onClick = { if (setting.kind == 1) model.changeSetting(setting, enabled = !setting.enabled) else choose() },
                    ) {
                        if (setting.kind == 1) AppSwitch(checked = setting.enabled, onCheckedChange = { model.changeSetting(setting, enabled = it) }, enabled = !state.busy)
                    }
                }
                state.keywords?.let { keywords ->
                    item {
                        MessageSettingRow("屏蔽词 ${keywords.words.size}${if (keywords.listLimit > 0) "/${keywords.listLimit}" else ""}",
                            keywords.hint, enabled = !state.busy, onClick = { wordEditor = true; word = "" }) {
                            AppTextButton(onClick = { wordEditor = true; word = "" }, enabled = !state.busy) { AppText("添加") }
                        }
                    }
                    items(keywords.words, key = { it }) { value ->
                        MessageSettingRow(value, onClick = { pendingWord = value }) { AppTextButton(onClick = { pendingWord = value }, enabled = !state.busy) { AppText("删除") } }
                    }
                }
                items(state.users, key = { it.mid }) { user ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenLink("bilibili://space/${user.mid}") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(user.face, "头像", Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            AppText(user.uname, style = MaterialTheme.typography.bodyLarge)
                            AppText(user.sign, maxLines = 2, color = AppContentColors.secondaryText())
                        }
                        if (page == "blacklist") AppTextButton(onClick = { pendingUnblock = user }, enabled = !state.busy) { AppText("移除") }
                    }
                }
                if (state.hasMore) item { AppTextButton(onClick = model::more, enabled = !state.busy) { AppText(if (state.busy) "加载中…" else "加载更多") } }
                if (!state.loading && state.error == null && state.settings.isEmpty() && state.users.isEmpty() && state.keywords == null) item { AppText("暂无内容", Modifier.padding(24.dp)) }
            }
        }
    }
    badgeEditor?.let { editingDynamic ->
        val currentMode = if (editingDynamic) dynamicBadgeMode else messageBadgeMode
        AppAlertDialog(
            onDismissRequest = { badgeEditor = null },
            title = { AppText(if (editingDynamic) "动态未读标记" else "消息未读标记") },
            text = {
                Column {
                    com.android.purebilibili.core.store.UnreadBadgeMode.entries.forEach { mode ->
                        AppTextButton(onClick = {
                            badgeEditor = null
                            scope.launch {
                                if (editingDynamic) MessageCenterPreferences.setDynamicBadgeMode(context, mode)
                                else MessageCenterPreferences.setMessageBadgeMode(context, mode)
                            }
                        }) { AppText((if (mode == currentMode) "✓  " else "") + mode.label) }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { badgeEditor = null }) { AppText("取消") } },
        )
    }
    selection?.let { setting ->
        AppAlertDialog(onDismissRequest = { selection = null }, title = { AppText(setting.title.ifBlank { "接收范围" }) }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                setting.options.forEach { option -> AppTextButton(onClick = {
                    selection = null
                    if (!option.selected) model.changeSetting(setting, selectedType = option.type)
                }) { AppText((if (option.selected) "✓  " else "") + option.text) } }
            }
        }, confirmButton = { AppTextButton(onClick = { selection = null }) { AppText("取消") } })
    }
    if (wordEditor) AppAlertDialog(onDismissRequest = { if (!state.busy) wordEditor = false }, title = { AppText("添加消息屏蔽词") }, text = {
        Column {
            AppCompactInputField(value = word, onValueChange = { word = it }, readOnly = state.busy, placeholder = state.keywords?.charLimit?.takeIf { it > 0 }?.let { "最多 $it 个字" } ?: "屏蔽词")
            state.error?.let { AppText(it) }
        }
    }, confirmButton = { AppTextButton(onClick = { model.addWord(word) }, enabled = word.isNotBlank() && !state.busy) { AppText("添加") } },
        dismissButton = { AppTextButton(onClick = { wordEditor = false }) { AppText("取消") } })
    pendingWord?.let { value -> MessageConfirmDialog("删除屏蔽词", "确定删除“$value”？", onDismiss = { pendingWord = null }) { model.removeWord(value); pendingWord = null } }
    pendingUnblock?.let { user -> MessageConfirmDialog("移出黑名单", "确定将 ${user.uname} 移出黑名单？", onDismiss = { pendingUnblock = null }) { model.unblock(user); pendingUnblock = null } }
}

@Composable
internal fun MessageSettingRow(
    title: String,
    subtitle: String = "",
    summary: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) AppText(subtitle, color = AppContentColors.secondaryText(), style = MaterialTheme.typography.bodySmall)
            if (summary.isNotBlank()) AppText(summary, color = AppContentColors.secondaryText(), style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
internal fun MessageConfirmDialog(title: String, content: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppAlertDialog(onDismissRequest = onDismiss, title = { AppText(title) }, text = { AppText(content) },
        confirmButton = { AppTextButton(onClick = onConfirm) { AppText("确定") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } })
}
