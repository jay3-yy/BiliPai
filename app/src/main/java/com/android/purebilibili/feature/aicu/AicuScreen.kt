package com.android.purebilibili.feature.aicu

import com.android.purebilibili.core.ui.AppContentColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.android.purebilibili.core.ui.common.AppSelectionContainer as SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.DataStoreAicuConsentStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.AicuRepository
import com.android.purebilibili.feature.video.ui.components.RichCommentText
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AicuRoute(
    uid: Long?,
    initialCategory: AicuCategory,
    onBack: () -> Unit,
    onOpenTarget: (BiliPaiNavKey) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context.applicationContext) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AicuViewModel(
                    AicuRepository(),
                    DataStoreAicuConsentStore(context),
                    trendingLoader = { AicuRepository().getTrending() },
                ) as T
        }
    }
    val model: AicuViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var emoteMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val renderEmoteMap = remember(emoteMap) {
        emoteMap.mapValues { (_, url) -> FormatUtils.fixImageUrl(url) }
    }
    val settings by SettingsManager.getHomeSettings(context)
        .map { it as com.android.purebilibili.core.store.HomeSettings? }
        .collectAsStateWithLifecycle(initialValue = null)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(model, uid, initialCategory) { model.initialize(uid, initialCategory) }
    LaunchedEffect(state.consent) {
        if (state.consent == AicuConsentState.ACCEPTED && emoteMap.isEmpty()) {
            emoteMap = CommentRepository.getEmoteMap()
        }
    }
    LaunchedEffect(owner, model, state.consent) {
        model.setDisclaimerVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner, model) {
        fun updateVisibility() {
            val visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            model.setForeground(visible)
            model.setDisclaimerVisible(visible)
        }
        val observer = LifecycleEventObserver { _, event ->
            updateVisibility()
            if (event == Lifecycle.Event.ON_STOP) model.cancelQuery()
        }
        owner.lifecycle.addObserver(observer)
        updateVisibility()
        onDispose {
            model.setForeground(false)
            owner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(model) { onDispose { model.cancelQuery() } }
    val back = { model.leave(); onBack() }
    BackHandler(onBack = back)
    AicuScreen(
        state = state,
        liquidEnabled = settings?.otherGlassEnabled == true,
        emoteMap = renderEmoteMap,
        onBack = back,
        onUidChange = model::editUid,
        onCategoryChange = model::selectCategory,
        onFilterChange = model::editFilter,
        onSubmit = model::submit,
        onReset = model::resetFilter,
        onPageChange = model::changePage,
        onCancel = model::cancelQuery,
        onRetry = model::retry,
        onAccept = model::acceptDisclaimer,
        onRetryConsent = { model.initialize(uid, initialCategory) },
        onLoadTrending = model::loadTrending,
        onOpenRecord = { record ->
            aicuNativeTarget(state.category, record)?.let { target ->
                model.cancelQuery()
                onOpenTarget(target)
            }
        },
        onCopyRecord = { record ->
            val text = buildString {
                append(record.text)
                append("\nUID: ${state.uid}\n${formatAicuTime(record.timestampSeconds)}")
                if (record.id.isNotBlank()) append("\n记录 ID: ${record.id}")
                if (record.objectId.isNotBlank()) append("\n内容 ID: ${record.objectId}")
                if (record.roomId.isNotBlank()) append("\n直播间: ${record.roomId}")
                append("\n数据来自 Aicu，非实时且可能不完整")
            }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("查询记录", text))
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AicuScreen(
    state: AicuUiState,
    liquidEnabled: Boolean,
    emoteMap: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onUidChange: (String) -> Unit,
    onCategoryChange: (AicuCategory) -> Unit,
    onFilterChange: (AicuFilter) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onPageChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
    onRetryConsent: () -> Unit,
    onLoadTrending: () -> Unit,
    onOpenRecord: (AicuRecord) -> Unit,
    onCopyRecord: (AicuRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInformation by remember { mutableStateOf(false) }
    var showTrending by remember { mutableStateOf(false) }
    val loadedPage = state.page
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); onSubmit() }
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "评论与弹幕查询",
                navigationIcon = { AppIconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                } },
                actions = {
                    AppTextButton(onClick = { showTrending = true; onLoadTrending() }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("Aicu 热搜") }
                    AppTextButton(onClick = { showInformation = true }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("使用说明") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth())
                    .fillMaxSize()
            ) {
                if (state.consent == AicuConsentState.CHECKING) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { AdaptiveLoadingIndicator() }
                } else if (state.consent == AicuConsentState.ERROR) {
                    AppText(state.consentError.orEmpty(), modifier = Modifier.padding(16.dp))
                    AppTextButton(onClick = onRetryConsent, modifier = Modifier.heightIn(min = 48.dp)) { AppText("重新读取") }
                } else if (state.consent == AicuConsentState.ACCEPTED) {
                    AicuCategoryTabs(state.category, liquidEnabled, onCategoryChange)
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.category, state.page?.query) { listState.scrollToItem(0) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item("filters") {
                            AicuFilters(state, liquidEnabled, onUidChange, onFilterChange, submit, onReset)
                        }
                        item("status") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppText("数据来自 Aicu · 非实时且可能不完整", style = MaterialTheme.typography.bodySmall)
                                if (state.busy) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AdaptiveLoadingIndicator(size = 24.dp)
                                        AppText(if (state.loadState == AicuLoadState.QUEUING) state.queueAhead?.let { "排队中，前面还有 $it 人" } ?: "正在申请查询…" else "正在加载…", modifier = Modifier.weight(1f))
                                        AppTextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { AppText("取消") }
                                    }
                                }
                                state.error?.let { AppText(it, color = AppContentColors.error()) }
                                if (state.loadState == AicuLoadState.ERROR || state.retrySeconds > 0) {
                                    AppTextButton(onClick = onRetry, enabled = state.retrySeconds == 0 && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                        AppText(if (state.retrySeconds > 0) "${state.retrySeconds} 秒后可重试" else "重试")
                                    }
                                }
                                if (state.loadState == AicuLoadState.IDLE) AppText("输入 UID 后点击查询。")
                                if (state.loadState == AicuLoadState.EMPTY) AppText("未收录符合条件的记录，不代表该用户从未发布内容。")
                                state.page?.let {
                                    AppText("第 ${it.query.page} 页" + (it.total?.let { total -> " · 已收录 $total 条" } ?: ""))
                                    if (it.query.filter != state.filter) AppText("筛选已修改，点击查询后生效。", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        itemsIndexed(state.page?.records.orEmpty(), key = { index, item -> "${state.category}:${item.id}:${item.groupKey}:$index" }) { index, record ->
                            val records = state.page?.records.orEmpty()
                            val showRoom = state.category == AicuCategory.LIVE_DANMAKU && (index == 0 || records[index - 1].groupKey != record.groupKey)
                            if (showRoom) AppText("${record.roomName.ifBlank { "直播间 ${record.roomId}" }} · ${record.upName}", modifier = Modifier.padding(vertical = 8.dp))
                            AicuRecordCard(record, state.category, emoteMap, { onOpenRecord(record) }, { onCopyRecord(record) })
                        }
                        if (loadedPage != null) item("pagination") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                AppTextButton(onClick = { onPageChange(-1) }, enabled = !state.busy && state.retrySeconds == 0 && loadedPage.query.page > 1, modifier = Modifier.heightIn(min = 48.dp)) { AppText("上一页") }
                                AppTextButton(onClick = { onPageChange(1) }, enabled = !state.busy && state.retrySeconds == 0 && !loadedPage.isEnd, modifier = Modifier.heightIn(min = 48.dp)) { AppText(if (loadedPage.isEnd) "没有更多" else "下一页") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.consent == AicuConsentState.REQUIRED) {
        AicuDisclaimerDialog(state.consentSeconds, state.savingConsent, state.consentError, onAccept, onBack)
    } else if (showInformation) {
        AppAlertDialog(
            onDismissRequest = { showInformation = false },
            title = { AppText("使用说明") },
            text = {
                SelectionContainer {
                    AppText(AICU_DISCLAIMER_TEXT, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { AppTextButton(onClick = { showInformation = false }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("关闭") } },
        )
    }
    if (showTrending) {
        AppAlertDialog(
            onDismissRequest = { showTrending = false },
            title = { AppText("Aicu 24 小时热搜") },
            text = {
                if (state.trendingLoading) {
                    Box(
                        Modifier.fillMaxWidth().height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveLoadingIndicator()
                    }
                }
                else if (state.trendingError != null) AppText(state.trendingError, color = AppContentColors.error())
                else Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.trending.forEachIndexed { index, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTrending = false
                                    onUidChange(item.uid)
                                    onSubmit()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText("${index + 1}", modifier = Modifier.width(28.dp))
                            coil3.compose.AsyncImage(model = item.avatar, contentDescription = null, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                AppText(item.display_name, maxLines = 1)
                                AppText("UID ${item.uid} · 搜索 ${item.search_count} 次 · 热度 ${item.hot_value}", style = MaterialTheme.typography.bodySmall)
                            }
                            AppText(when (item.trend) { "up" -> "上升"; "down" -> "下降"; else -> "持平" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showTrending = false }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("关闭") } },
        )
    }
}

@Composable
private fun AicuCategoryTabs(category: AicuCategory, liquidEnabled: Boolean, onSelect: (AicuCategory) -> Unit) {
    val options = remember { AicuCategory.entries.map { AppSegmentOption(it, it.label) } }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val useGlass = shouldUseAicuLiquidTabs(liquidEnabled, Build.VERSION.SDK_INT, maxWidth.value, fontScale, hasBackdrop = true)
        if (useGlass) {
            // Let the shared dock own its bounded fallback backdrop. Supplying a synthetic
            // gradient here separates the shell and moving indicator capture pipelines.
            AppThemeAdaptiveTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                options = options,
                selectedValue = category,
                onSelectionChange = onSelect,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
                indicatorHeight = 36.dp,
                dragSelectionEnabled = true,
                tapPressRefractionEnabled = true,
            )
        } else {
            AppNativeTabRow(options, category, onSelectionChange = onSelect, indicatorPresentation = AppTabRowIndicatorPresentation.TONAL_PILL, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                scrollable = maxWidth < 360.dp || fontScale > 1.3f, allowLabelOverflow = true, minTabWidth = 96.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AicuFilters(
    state: AicuUiState,
    liquidEnabled: Boolean,
    onUid: (String) -> Unit,
    onFilter: (AicuFilter) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppOutlinedTextField(state.uid, onUid, labelText = "B 站用户 UID", singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }), modifier = Modifier.fillMaxWidth())
        AppOutlinedTextField(state.filter.keyword, { onFilter(state.filter.copy(keyword = it)) }, labelText = "关键词", singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }), modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedTextField(state.filter.startDate, { onFilter(state.filter.copy(startDate = it)) }, labelText = "开始日期", placeholderText = "YYYY-MM-DD", singleLine = true, modifier = Modifier.widthIn(min = 140.dp, max = 240.dp).weight(1f))
            AppOutlinedTextField(state.filter.endDate, { onFilter(state.filter.copy(endDate = it)) }, labelText = "结束日期", placeholderText = "YYYY-MM-DD", singleLine = true, modifier = Modifier.widthIn(min = 140.dp, max = 240.dp).weight(1f))
        }
        if (state.category == AicuCategory.COMMENT) {
            AppText("评论类型", style = MaterialTheme.typography.labelLarge)
            AicuCommentModeDock(
                selectedMode = state.filter.commentMode,
                liquidEnabled = liquidEnabled,
                onSelect = { onFilter(state.filter.copy(commentMode = it)) },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(onClick = onSubmit, enabled = !state.busy && state.retrySeconds == 0, modifier = Modifier.heightIn(min = 48.dp)) { AppText("查询") }
            AppTextButton(onClick = onReset, enabled = !state.busy && state.retrySeconds == 0, modifier = Modifier.heightIn(min = 48.dp)) { AppText("重置筛选") }
        }
    }
}

@Composable
private fun AicuCommentModeDock(
    selectedMode: Int,
    liquidEnabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val options = remember {
        listOf(AppSegmentOption(0, "全部"), AppSegmentOption(1, "一级"), AppSegmentOption(2, "二级"))
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val useGlass = shouldUseAicuLiquidTabs(
            enabled = liquidEnabled,
            sdkInt = Build.VERSION.SDK_INT,
            availableWidthDp = maxWidth.value,
            fontScale = fontScale,
            hasBackdrop = true,
        )
        if (useGlass) {
            AppThemeAdaptiveTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                options = options,
                selectedValue = selectedMode,
                onSelectionChange = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
                minTabWidth = 72.dp,
                height = 48.dp,
                indicatorHeight = 36.dp,
                dragSelectionEnabled = true,
                tapPressRefractionEnabled = true,
            )
        } else {
            AppNativeSegmentedControl(
                options = options,
                selectedValue = selectedMode,
                onSelectionChange = onSelect,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun AicuDisclaimerDialog(seconds: Int, saving: Boolean, error: String?, onAccept: () -> Unit, onCancel: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { AppText("第三方查询免责声明") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    AppText(AICU_DISCLAIMER_TEXT)
                }
                error?.let { AppText(it, color = AppContentColors.error()) }
            }
        },
        confirmButton = { AppTextButton(onClick = onAccept, enabled = seconds == 0 && !saving, modifier = Modifier.heightIn(min = 48.dp)) {
            AppText(when { saving -> "正在保存…"; seconds > 0 -> "我已知晓并继续（$seconds 秒）"; else -> "我已知晓并继续" })
        } },
        dismissButton = { AppTextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { AppText("取消") } },
    )
}

@Composable
private fun AicuRecordCard(
    record: AicuRecord,
    category: AicuCategory,
    emoteMap: Map<String, String>,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val target = remember(category, record) { aicuNativeTarget(category, record) }
    val progressMs = record.progressMs
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(formatAicuTime(record.timestampSeconds) + when {
                category == AicuCategory.COMMENT && record.rank in 1..2 -> " · ${if (record.rank == 1) "一级" else "二级"}评论"
                category == AicuCategory.VIDEO_DANMAKU && progressMs != null -> " · 视频内 ${progressMs / 1000.0} 秒"
                else -> ""
            }, style = MaterialTheme.typography.bodySmall)
            if (record.authorName.isNotBlank()) AppText(record.authorName, style = MaterialTheme.typography.labelLarge)
            RichCommentText(
                text = record.text.ifBlank { "（正文缺失）" },
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                color = AppContentColors.primaryText(),
                emoteMap = emoteMap,
            )
            if (target != null && category == AicuCategory.COMMENT && (record.objectType == 12 || (record.rank == 2 && record.rootId.toLongOrNull() == null))) {
                AppText("缺少完整评论定位信息，将打开原内容。", style = MaterialTheme.typography.bodySmall)
            }
            if (target == null) AppText("暂不支持原生打开此记录，可复制信息。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton(onClick = onCopy, modifier = Modifier.heightIn(min = 48.dp)) { AppText("复制") }
                if (target != null) AppTextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { AppText(if (category == AicuCategory.LIVE_DANMAKU) "打开直播间" else "打开原内容") }
            }
        }
    }
}

private val aicuTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private fun formatAicuTime(seconds: Long?): String = seconds?.let {
    runCatching { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(aicuTimeFormatter) }.getOrNull()
} ?: "时间未知"
