package com.android.purebilibili.feature.live.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppRadioButton
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.repository.LiveMedalVisibility
import com.android.purebilibili.data.repository.LiveMedalVisibilityOption
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.data.repository.LiveRoomMedalEntry
import com.android.purebilibili.data.repository.LiveRoomMedalStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
internal fun rememberLiveRoomMedalControl(
    userId: Long,
    anchorId: Long,
    onUserClick: (Long) -> Unit,
): (@Composable () -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<LiveRoomMedalStatus?>(null) }
    var panelVisible by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(userId, anchorId) {
        val autoWear = SettingsManager.getLiveAutoWearMedal(context).first()
        LiveRepository.getLiveRoomMedal(userId, anchorId).onSuccess { current ->
            val medalId = current.roomMedalId
            status = if (autoWear && medalId != null && medalId != current.wearingMedalId &&
                LiveRepository.updateLiveMedalWearing(medalId).isSuccess
            ) {
                LiveRepository.getLiveRoomMedal(userId, anchorId).getOrNull() ?: current
            } else {
                current
            }
        }
    }
    if (panelVisible) {
        LiveRoomMedalPanel(
            updating = updating,
            onDismiss = { panelVisible = false },
            onUserClick = {
                panelVisible = false
                onUserClick(it)
            },
            onSelect = { wearingId, onApplied ->
                if (!updating) {
                    updating = true
                    scope.launch {
                        LiveRepository.updateLiveMedalWearing(wearingId)
                            .onSuccess {
                                onApplied()
                                LiveRepository.getLiveRoomMedal(userId, anchorId).onSuccess { status = it }
                            }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                        updating = false
                    }
                }
            },
        )
    }
    val current = status ?: return null
    if (current.displayed == null && !current.hasRoomMedal) return null
    return {
        Box(
            modifier = Modifier
                .widthIn(max = 82.dp)
                .height(34.dp)
                .clickable(onClickLabel = "我的粉丝勋章") { panelVisible = true },
            contentAlignment = Alignment.Center,
        ) {
            val medal = current.displayed
            if (medal != null) {
                LiveMedalBadge(medal)
            } else {
                AppText("未佩戴", style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveRoomMedalPanel(
    updating: Boolean,
    onDismiss: () -> Unit,
    onUserClick: (Long) -> Unit,
    onSelect: (Long?, () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var entries by remember { mutableStateOf<List<LiveRoomMedalEntry>>(emptyList()) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf<Int?>(null) }
    var managing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    fun load() {
        if (loading || updating || !hasMore) return
        loading = true
        error = null
        scope.launch {
            LiveRepository.getLiveMedalPanel(nextPage)
                .onSuccess { page ->
                    entries = (entries + page.items).distinctBy { it.id }
                    hasMore = page.hasMore
                    total = page.total
                    nextPage++
                }
                .onFailure { error = it.message ?: "读取粉丝勋章失败" }
            loading = false
        }
    }

    fun select(entry: LiveRoomMedalEntry) {
        val wearingId = entry.id.takeUnless { entry.wearing }
        onSelect(wearingId) { entries = entries.map { it.copy(wearing = it.id == wearingId) } }
    }

    LaunchedEffect(updating) {
        if (!updating && nextPage == 1 && error == null) load()
    }
    LaunchedEffect(nextPage, loading, updating, error, showSettings) {
        if (nextPage == 1 || loading || updating || !hasMore || error != null || showSettings) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.any {
                it.key == "live_medal_more" && it.offset < layout.viewportEndOffset
            }
        }.collect { atEnd -> if (atEnd) load() }
    }

    val height = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    AppModalBottomSheet(onDismissRequest = onDismiss, useWindowPredictiveBack = true) {
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp)
                .fillMaxWidth().height(height),
        ) {
            if (showSettings) {
                LiveMedalVisibilitySettings(onBack = { showSettings = false })
            } else {
                val title = if (managing) {
                    total?.let { "粉丝勋章 ($it)" } ?: "粉丝勋章"
                } else {
                    "我的粉丝勋章"
                }
                AppText(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                AppLazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .then(if (managing) Modifier else Modifier.clickable(enabled = !updating && !loading) { select(entry) })
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!managing) AppRadioButton(selected = entry.wearing, onClick = null)
                            LiveMedalBadge(entry.medal, modifier = Modifier.widthIn(max = 100.dp))
                            Column(Modifier.weight(1f)) {
                                AppText(
                                    entry.anchorName,
                                    modifier = if (managing && entry.anchorId > 0L) {
                                        Modifier.clickable { onUserClick(entry.anchorId) }
                                    } else Modifier,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (managing) MedalProgress(entry)
                            }
                            if (managing) AppTextButton(onClick = { select(entry) }, enabled = !updating && !loading) {
                                AppText(if (entry.wearing) "摘下" else "佩戴")
                            }
                        }
                    }
                    item(key = "live_medal_more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                error != null -> AppTextButton(onClick = ::load) { AppText("${error.orEmpty()} · 重试") }
                                loading || hasMore -> AppText("加载中…")
                                entries.isEmpty() -> AppText("暂无粉丝勋章")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppTextButton(onClick = { showSettings = true }) { AppText("展示设置") }
                    AppTextButton(onClick = { managing = !managing }) {
                        AppText(if (managing) "返回勋章列表" else "勋章管理")
                    }
                }
            }
        }
    }
}


@Composable
private fun MedalProgress(entry: LiveRoomMedalEntry) {
    val lines = listOfNotNull(
        entry.intimacy?.let { "累计 $it/${entry.nextIntimacy ?: "—"}" },
        entry.todayFeed?.let { "今日 $it/${entry.dailyLimit ?: "—"}" },
    )
    if (lines.isNotEmpty()) {
        AppText(
            lines.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
    }
}

@Composable
private fun LiveMedalVisibilitySettings(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var settings by remember { mutableStateOf<LiveMedalVisibility?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(loadAttempt) {
        error = null
        LiveRepository.getLiveMedalVisibility()
            .onSuccess { settings = it }
            .onFailure { error = it.message ?: "读取勋章展示设置失败" }
    }

    fun save(option: LiveMedalVisibilityOption, enabled: Boolean, updated: LiveMedalVisibility) {
        if (saving) return
        saving = true
        scope.launch {
            LiveRepository.setLiveMedalVisibility(option, enabled)
                .onSuccess { settings = updated }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
            saving = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("勋章展示设置", style = MaterialTheme.typography.titleMedium)
            AppTextButton(onClick = onBack, enabled = !saving) { AppText("返回") }
        }
        val current = settings
        when {
            error != null -> AppTextButton(onClick = { loadAttempt++ }) { AppText("${error.orEmpty()} · 重试") }
            current == null -> AppText("加载中…", modifier = Modifier.padding(16.dp))
            else -> {
                AppSwitchPreference(
                    title = "个人空间公开显示佩戴的粉丝勋章",
                    checked = !current.hideSpaceMedal,
                    enabled = !saving,
                    onCheckedChange = { enabled ->
                        save(
                            LiveMedalVisibilityOption.HIDE_SPACE_MEDAL, !enabled,
                            current.copy(hideSpaceMedal = !enabled),
                        )
                    },
                )
                AppSwitchPreference(
                    title = "勋章墙公开显示所有粉丝勋章",
                    subtitle = "关闭后仅显示佩戴中的勋章",
                    checked = !current.hideSpaceMedal && !current.showWearingOnly,
                    enabled = !saving && !current.hideSpaceMedal,
                    onCheckedChange = { enabled ->
                        save(
                            LiveMedalVisibilityOption.SHOW_WEARING_ONLY, !enabled,
                            current.copy(showWearingOnly = !enabled),
                        )
                    },
                )
            }
        }
    }
}
