package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.appElasticPress


import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import com.android.purebilibili.core.ui.AppModalBottomSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import com.android.purebilibili.core.ui.components.AppButton as Button
import com.android.purebilibili.core.ui.components.AppCard as Card
import com.android.purebilibili.core.ui.components.AppCardDefaults as CardDefaults
import com.android.purebilibili.core.ui.components.AppCheckbox as Checkbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon as Icon
import com.android.purebilibili.core.ui.components.AppIconButton as IconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedButton as OutlinedButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField as OutlinedTextField
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.LocalPlaylist
import com.android.purebilibili.core.store.LocalPlaylistItem
import com.android.purebilibili.core.store.LocalPlaylistStore
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.ContentRequestException
import com.android.purebilibili.data.repository.ExternalPlaylistRepository
import com.android.purebilibili.data.repository.SearchRepository
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Import an external playlist, match its tracks to Bilibili videos, and save the local playlist.
 * The dialog controls follow the active app theme; the result list is lazy for large playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPlaylistImportDialog(
    onDismiss: () -> Unit,
    onSaved: ((LocalPlaylist) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<ExternalPlaylistRepository.ExternalPlaylistMeta?>(null) }

    var matching by remember { mutableStateOf(false) }
    var matchCompleted by remember { mutableIntStateOf(0) }
    var matchTotal by remember { mutableIntStateOf(0) }
    var matchingTrackTitle by remember { mutableStateOf("") }
    var matchResults by remember {
        mutableStateOf<List<ExternalPlaylistRepository.MatchOutcome>>(emptyList())
    }
    var matchJob by remember { mutableStateOf<Job?>(null) }
    // 未勾选的曲目序号；默认全选，因此只需记录被取消勾选的那些。
    var excludedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showCancelMatchConfirm by remember { mutableStateOf(false) }

    // 收藏到 B 站收藏夹
    var showFavoritePicker by remember { mutableStateOf(false) }
    var favoriteFolders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var favoriteFoldersLoading by remember { mutableStateOf(false) }
    var favoriteFoldersError by remember { mutableStateOf<String?>(null) }
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var favoriting by remember { mutableStateOf(false) }
    var favoriteJob by remember { mutableStateOf<Job?>(null) }
    var favoriteProcessed by remember { mutableIntStateOf(0) }
    var favoriteTotal by remember { mutableIntStateOf(0) }
    var favoriteSucceeded by remember { mutableIntStateOf(0) }
    var favoriteFailed by remember { mutableIntStateOf(0) }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var manualKeyword by remember { mutableStateOf("") }
    var manualSearching by remember { mutableStateOf(false) }
    var manualResults by remember {
        mutableStateOf<List<ExternalPlaylistRepository.MatchedVideo>>(emptyList())
    }

    LaunchedEffect(context) {
        ExternalPlaylistRepository.loadImportCheckpoint(context)?.let { checkpoint ->
            playlist = checkpoint.playlist
            matchResults = checkpoint.outcomes.map { ExternalPlaylistRepository.MatchOutcome(it.track, it.video) }
            matchTotal = checkpoint.playlist.tracks.size
            matchCompleted = checkpoint.completedCount
        }
    }

    fun dismissEditing() {
        editingIndex = null
        manualResults = emptyList()
        manualKeyword = ""
    }

    fun startMatching(resume: Boolean = false) {
        val meta = playlist ?: return
        matching = true
        matchTotal = meta.tracks.size
        val startIndex = if (resume) matchCompleted.coerceIn(0, meta.tracks.size) else 0
        if (!resume || matchResults.size != meta.tracks.size) {
            matchCompleted = 0
            matchResults = meta.tracks.map { ExternalPlaylistRepository.MatchOutcome(it, null) }
        }
        if (!resume) excludedIndices = emptySet()
        scope.launch {
            ExternalPlaylistRepository.saveImportCheckpoint(
                context,
                ExternalPlaylistRepository.ImportCheckpoint(meta, matchResults, matchCompleted),
            )
        }
        matchJob = scope.launch {
            ExternalPlaylistRepository.matchTracks(meta.tracks, startIndex) { completed, _, outcome ->
                matchCompleted = completed
                matchingTrackTitle = outcome.track.title
                matchResults = matchResults.toMutableList().also { list ->
                    if (completed - 1 in list.indices) list[completed - 1] = outcome
                }
                ExternalPlaylistRepository.saveImportCheckpoint(
                    context,
                    ExternalPlaylistRepository.ImportCheckpoint(meta, matchResults, completed),
                )
            }
            matching = false
        }
    }

    fun savePlaylist() {
        val meta = playlist ?: return
        val items = matchResults.mapIndexedNotNull { index, outcome ->
            if (index in excludedIndices) return@mapIndexedNotNull null
            outcome.video?.let { video ->
                LocalPlaylistItem(
                    bvid = video.bvid,
                    title = video.title,
                    cover = video.cover,
                    owner = video.author,
                    durationSec = video.durationSec,
                )
            }
        }
        if (items.isEmpty()) return
        scope.launch {
            val local = LocalPlaylist(
                id = UUID.randomUUID().toString(),
                name = meta.name,
                coverUrl = meta.coverUrl,
                source = meta.source.name.lowercase(),
                createdAtMs = System.currentTimeMillis(),
                items = items,
            )
            LocalPlaylistStore.savePlaylist(context, local)
            ExternalPlaylistRepository.clearImportCheckpoint(context)
            onSaved?.invoke(local)
            val message = if (onSaved != null) {
                "已保存 ${items.size} 首并开始播放"
            } else {
                "已保存 ${items.size} 首到本地歌单"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    fun stopMatching() {
        matchJob?.cancel()
        matchJob = null
        matching = false
    }

    /** 取消匹配：清空当前歌单与匹配结果，回到填写歌单链接的页面。 */
    fun cancelMatchAndReset() {
        showCancelMatchConfirm = false
        matchJob?.cancel()
        matchJob = null
        matching = false
        playlist = null
        matchResults = emptyList()
        matchCompleted = 0
        matchTotal = 0
        matchingTrackTitle = ""
        excludedIndices = emptySet()
        dismissEditing()
        scope.launch { ExternalPlaylistRepository.clearImportCheckpoint(context) }
    }

    fun loadFavoriteFolders() {
        if (favoriteFoldersLoading) return
        favoriteFoldersLoading = true
        favoriteFoldersError = null
        scope.launch {
            ActionRepository.getFavoriteFolders()
                .onSuccess { folders ->
                    favoriteFolders = folders
                    val stillValid = folders.any { it.id in selectedFolderIds }
                    if (!stillValid) {
                        selectedFolderIds = folders.firstOrNull()?.let { setOf(it.id) } ?: emptySet()
                    }
                }
                .onFailure { favoriteFoldersError = it.message ?: "获取收藏夹失败" }
            favoriteFoldersLoading = false
        }
    }

    fun openFavoritePicker() {
        scope.launch {
            withContext(Dispatchers.IO) { TokenManager.awaitRestore() }
            val loggedIn = (TokenManager.midCache ?: 0L) > 0L && !TokenManager.csrfCache.isNullOrBlank()
            if (!loggedIn) {
                Toast.makeText(context, "请先登录 B 站账号后再收藏", Toast.LENGTH_SHORT).show()
                return@launch
            }
            showFavoritePicker = true
            if (favoriteFolders.isEmpty()) loadFavoriteFolders()
        }
    }

    fun stopFavoriting() {
        favoriteJob?.cancel()
        favoriteJob = null
        favoriting = false
    }

    fun startFavoriting() {
        if (favoriting) return
        val videos = matchResults.mapIndexedNotNull { index, outcome ->
            if (index in excludedIndices) return@mapIndexedNotNull null
            outcome.video
        }
        if (videos.isEmpty()) return
        val folderIds = selectedFolderIds
        if (folderIds.isEmpty()) {
            Toast.makeText(context, "请至少选择一个收藏夹", Toast.LENGTH_SHORT).show()
            return
        }
        favoriteProcessed = 0
        favoriteSucceeded = 0
        favoriteFailed = 0
        favoriteTotal = videos.size
        favoriting = true
        favoriteJob = scope.launch {
            var stopReason: String? = null
            try {
                for (video in videos) {
                    if (!isActive) break
                    val aid = video.aid.takeIf { it > 0L }
                        ?: VideoRepository.getVideoInfoOnly(bvid = video.bvid).getOrNull()?.aid
                    if (aid == null || aid <= 0L) {
                        favoriteFailed += 1
                    } else {
                        ActionRepository.updateFavoriteFolders(
                            aid = aid,
                            addFolderIds = folderIds,
                            removeFolderIds = emptySet(),
                        ).onSuccess {
                            favoriteSucceeded += 1
                        }.onFailure { error ->
                            favoriteFailed += 1
                            stopReason = when ((error as? ContentRequestException)?.code) {
                                -101 -> "登录已失效，请重新登录"
                                -509, -412, 34004 -> "操作太频繁，请稍后重试"
                                else -> null
                            }
                        }
                    }
                    favoriteProcessed += 1
                    if (stopReason != null) break
                    delay(FAVORITE_INTERVAL_MS)
                }
            } finally {
                val stopped = !isActive
                favoriting = false
                favoriteJob = null
                val summary = buildString {
                    if (stopped) append("已停止收藏：")
                    append("成功 ${favoriteSucceeded} 首")
                    if (favoriteFailed > 0) append("，失败 ${favoriteFailed} 首")
                    stopReason?.let { append("（$it）") }
                }
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            }
        }
    }

    val panelMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        useWindowPredictiveBack = true,
    ) {
        // 表单、操作区和歌曲共用滚动视口，键盘或短窗口不挤掉歌曲列表。
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = panelMaxHeight),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            item(key = "import_header", contentType = "header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "导入外部歌单",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.appElasticPress()) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            val meta = playlist
            if (meta == null) {
                item(key = "playlist_input", contentType = "input") {
                    Column {
                        Text(
                            text = "支持网易云音乐和 QQ 音乐歌单。复制公开歌单的分享链接，粘贴到下面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.secondaryText(),
                        )
                        Spacer(Modifier.height(16.dp))
                        com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("歌单链接或 ID") },
                                singleLine = true,
                            )
                        }
                        fetchError?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Text(message, color = AppContentColors.error(), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (fetching) return@Button
                                fetching = true
                                fetchError = null
                                scope.launch {
                                    val parsed = ExternalPlaylistRepository.parsePlaylistInput(inputText)
                                    val sourceAndId = when {
                                        parsed != null -> parsed
                                        inputText.trim().matches(Regex("\\d{4,}")) -> {
                                            ExternalPlaylistRepository.Source.NETEASE to inputText.trim()
                                        }
                                        else -> null
                                    }
                                    if (sourceAndId == null) {
                                        fetchError = "无法识别链接，请粘贴网易云或 QQ 音乐的完整分享链接"
                                    } else {
                                        ExternalPlaylistRepository.fetchPlaylist(sourceAndId.first, sourceAndId.second)
                                            .onSuccess { fetched ->
                                                if (fetched.tracks.isEmpty()) fetchError = "歌单为空或为私密歌单"
                                                else {
                                                    playlist = fetched
                                                    matchResults = fetched.tracks.map {
                                                        ExternalPlaylistRepository.MatchOutcome(it, null)
                                                    }
                                                    matchCompleted = 0
                                                    matchTotal = fetched.tracks.size
                                                    excludedIndices = emptySet()
                                                    ExternalPlaylistRepository.saveImportCheckpoint(
                                                        context,
                                                        ExternalPlaylistRepository.ImportCheckpoint(
                                                            fetched,
                                                            matchResults,
                                                            completedCount = 0,
                                                        ),
                                                    )
                                                }
                                            }
                                            .onFailure { fetchError = it.message ?: "获取歌单失败" }
                                    }
                                    fetching = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().appElasticPress(enabled = !fetching),
                            enabled = !fetching,
                        ) {
                            Text(if (fetching) "正在获取…" else "获取歌单信息")
                        }
                    }
                }
            } else {
                val completed = !matching && matchResults.isNotEmpty() && matchCompleted >= matchTotal
                val matchedCount = matchResults.count { it.video != null }
                val matchedIndices = matchResults.indices.filter { matchResults[it].video != null }
                val selectedCount = matchedIndices.count { it !in excludedIndices }
                val allSelected = matchedIndices.isNotEmpty() && selectedCount == matchedIndices.size
                val outcomes = matchResults.ifEmpty {
                    meta.tracks.map { ExternalPlaylistRepository.MatchOutcome(it, null) }
                }
                item(key = "playlist_controls", contentType = "controls") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${meta.author} · ${meta.tracks.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        when {
                            matching -> {
                                LinearProgressIndicator(
                                    progress = { (matchCompleted.toFloat() / matchTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "正在匹配：$matchingTrackTitle ($matchCompleted/$matchTotal)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppContentColors.secondaryText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                                )
                                OutlinedButton(
                                    onClick = { stopMatching() },
                                    modifier = Modifier.fillMaxWidth().appElasticPress(),
                                ) {
                                    Text("停止匹配")
                                }
                            }

                            completed -> {
                                Text(
                                    text = if (matchedCount == 0) {
                                        "没有匹配到可用视频，可点击单曲手动修正，或重新匹配"
                                    } else {
                                        "已匹配 $matchedCount/${matchResults.size} 首，已选 $selectedCount 首"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (matchedCount == 0) AppContentColors.error() else AppContentColors.secondaryText(),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { openFavoritePicker() },
                                        enabled = selectedCount > 0 && !favoriting,
                                        modifier = Modifier.weight(1f).appElasticPress(enabled = selectedCount > 0 && !favoriting),
                                    ) {
                                        Text("收藏到收藏夹")
                                    }
                                    OutlinedButton(
                                        onClick = { startMatching(resume = false) },
                                        enabled = !favoriting,
                                        modifier = Modifier.weight(1f).appElasticPress(enabled = !favoriting),
                                    ) {
                                        Text("重新匹配")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { savePlaylist() },
                                    enabled = selectedCount > 0 && !favoriting,
                                    modifier = Modifier.fillMaxWidth().appElasticPress(enabled = selectedCount > 0 && !favoriting),
                                ) {
                                    Text(if (selectedCount > 0) "保存并播放（$selectedCount 首）" else "保存并播放")
                                }
                            }

                            else -> {
                                if (matchTotal > 0) {
                                    LinearProgressIndicator(
                                        progress = { (matchCompleted.toFloat() / matchTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = if (matchCompleted > 0) {
                                            "已匹配 $matchCompleted/$matchTotal 首，可继续匹配"
                                        } else {
                                            "共 $matchTotal 首，开始匹配后可逐首查看结果"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText(),
                                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                                    )
                                }
                                if (matchCompleted in 1 until matchTotal) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { startMatching(resume = true) },
                                            modifier = Modifier.weight(1f).appElasticPress(),
                                        ) {
                                            Text("继续匹配")
                                        }
                                        OutlinedButton(
                                            onClick = { showCancelMatchConfirm = true },
                                            modifier = Modifier.weight(1f).appElasticPress(),
                                        ) {
                                            Text("取消匹配")
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { startMatching(resume = false) },
                                        modifier = Modifier.fillMaxWidth().appElasticPress(),
                                    ) {
                                        Text("开始匹配")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        if (!matching && matchedIndices.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !favoriting) {
                                        if (!favoriting) {
                                            excludedIndices = if (allSelected) matchedIndices.toSet() else emptySet()
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checked ->
                                        excludedIndices = if (checked) emptySet() else matchedIndices.toSet()
                                    },
                                    enabled = !favoriting,
                                )
                                Text(
                                    text = "全选 · 已选 $selectedCount/${matchedIndices.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
                // 匹配只替换视频，曲目顺序不变；行身份绑定歌单与曲目位置。
                itemsIndexed(
                    outcomes,
                    key = { index, _ -> "${meta.source}:${meta.playlistId}:track:$index" },
                    contentType = { _, _ -> "track" },
                ) { index, outcome ->
                    Column(
                        modifier = Modifier.padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Card(
                            modifier = Modifier.clickable(enabled = matchResults.isNotEmpty() && !matching && !favoriting) {
                                if (matchResults.isNotEmpty() && !matching && !favoriting) {
                                    if (editingIndex == index) {
                                        dismissEditing()
                                    } else {
                                        editingIndex = index
                                        manualKeyword = ExternalPlaylistRepository
                                            .buildSearchQueryForManualMatch(outcome.track)
                                        manualResults = emptyList()
                                    }
                                }
                            },
                            colors = CardDefaults.colors(
                                containerColor = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
                                    com.android.purebilibili.core.ui.AppSurfaceTokens.cardContainer()
                                } else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = outcome.video != null && index !in excludedIndices,
                                    onCheckedChange = { checked ->
                                        excludedIndices = if (checked) {
                                            excludedIndices - index
                                        } else {
                                            excludedIndices + index
                                        }
                                    },
                                    enabled = outcome.video != null && !matching && !favoriting,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${outcome.track.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = outcome.video?.let { "${it.author} · ${it.title}" }
                                            ?: outcome.track.artists.joinToString("/").ifBlank { "未匹配" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (outcome.video != null) AppContentColors.secondaryText()
                                        else AppContentColors.error().copy(alpha = 0.86f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                outcome.video?.cover?.takeIf { it.isNotBlank() }?.let { cover ->
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp).clip(AppShapes.container(ContainerLevel.Chip)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (editingIndex == index) dismissEditing() else {
                                            editingIndex = index
                                            manualKeyword = ExternalPlaylistRepository
                                                .buildSearchQueryForManualMatch(outcome.track)
                                            manualResults = emptyList()
                                        }
                                    },
                                    enabled = matchResults.isNotEmpty() && !matching && !favoriting,
                                    modifier = Modifier.appElasticPress(
                                        enabled = matchResults.isNotEmpty() && !matching && !favoriting,
                                    ),
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "手动修正")
                                }
                            }
                        }

                        if (editingIndex == index) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                                    OutlinedTextField(
                                        value = manualKeyword,
                                        onValueChange = { manualKeyword = it },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("搜索 B 站视频") },
                                        singleLine = true,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (manualKeyword.isNotBlank() && !manualSearching) {
                                            scope.launch {
                                                manualSearching = true
                                                SearchRepository.search(keyword = manualKeyword)
                                                    .onSuccess { (items, _) ->
                                                        manualResults = items.take(8).map {
                                                            ExternalPlaylistRepository.MatchedVideo(
                                                                bvid = it.bvid,
                                                                title = it.title,
                                                                cover = it.pic,
                                                                author = it.owner.name,
                                                                durationSec = it.duration.toLong(),
                                                                aid = it.id,
                                                            )
                                                        }
                                                    }
                                                manualSearching = false
                                            }
                                        }
                                    },
                                    enabled = manualKeyword.isNotBlank() && !manualSearching, modifier = Modifier.appElasticPress(enabled = manualKeyword.isNotBlank() && !manualSearching),
                                ) {
                                    Text(if (manualSearching) "搜索中" else "搜索")
                                }
                            }
                            manualResults.forEach { video ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        matchResults = matchResults.toMutableList().also { list ->
                                            list[index] = ExternalPlaylistRepository.MatchOutcome(list[index].track, video)
                                        }
                                        // 手动修正后默认勾选，避免被之前的“全不选”遗漏。
                                        excludedIndices = excludedIndices - index
                                        scope.launch {
                                            ExternalPlaylistRepository.saveImportCheckpoint(
                                                context,
                                                ExternalPlaylistRepository.ImportCheckpoint(
                                                    meta,
                                                    matchResults,
                                                    matchCompleted,
                                                ),
                                            )
                                        }
                                        dismissEditing()
                                    },
                                    colors = CardDefaults.colors(
                                        containerColor = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
                                            com.android.purebilibili.core.ui.AppSurfaceTokens.cardContainer()
                                        } else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(
                                            model = video.cover,
                                            contentDescription = null,
                                            modifier = Modifier.size(width = 64.dp, height = 42.dp)
                                                .clip(AppShapes.container(ContainerLevel.Chip)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(video.author, style = MaterialTheme.typography.bodySmall,
                                                color = AppContentColors.secondaryText())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (completed) {
                    item(key = "playlist_hint", contentType = "hint") {
                        Text(
                            text = "未匹配或未勾选的曲目不会被保存或收藏。点击任意一行可搜索 B 站视频手动修正。",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCancelMatchConfirm) {
        AppAlertDialog(
            onDismissRequest = { showCancelMatchConfirm = false },
            title = { Text("取消匹配？") },
            text = { Text("将丢弃当前歌单的匹配结果，并返回填写歌单链接的页面。") },
            confirmButton = {
                TextButton(onClick = { cancelMatchAndReset() }) {
                    Text("取消匹配")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelMatchConfirm = false }) {
                    Text("继续匹配")
                }
            },
        )
    }

    if (showFavoritePicker) {
        FavoriteFolderPickerDialog(
            playlistName = playlist?.name.orEmpty(),
            matchedCount = matchResults.withIndex().count { (index, outcome) ->
                outcome.video != null && index !in excludedIndices
            },
            folders = favoriteFolders,
            loading = favoriteFoldersLoading,
            error = favoriteFoldersError,
            selectedIds = selectedFolderIds,
            favoriting = favoriting,
            processed = favoriteProcessed,
            total = favoriteTotal,
            succeeded = favoriteSucceeded,
            failed = favoriteFailed,
            onToggleFolder = { folderId ->
                selectedFolderIds = if (folderId in selectedFolderIds) {
                    selectedFolderIds - folderId
                } else {
                    selectedFolderIds + folderId
                }
            },
            onRetry = { loadFavoriteFolders() },
            onStart = { startFavoriting() },
            onStop = { stopFavoriting() },
            onBack = { if (!favoriting) showFavoritePicker = false },
        )
    }
}

private const val FAVORITE_INTERVAL_MS = 300L

/** 收藏夹选择使用标准对话框，收藏进行中保留停止操作并禁止关闭。 */
@Composable
private fun FavoriteFolderPickerDialog(
    playlistName: String,
    matchedCount: Int,
    folders: List<FavFolder>,
    loading: Boolean,
    error: String?,
    selectedIds: Set<Long>,
    favoriting: Boolean,
    processed: Int,
    total: Int,
    succeeded: Int,
    failed: Int,
    onToggleFolder: (Long) -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = { if (!favoriting) onBack() },
        properties = DialogProperties(
            dismissOnBackPress = !favoriting,
            dismissOnClickOutside = !favoriting,
        ),
        title = { Text("收藏到 B 站收藏夹") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "favorite_summary") {
                    Text(
                        text = "$playlistName · 将收藏 $matchedCount 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (favoriting) {
                    item(key = "favorite_progress") {
                        Column {
                            LinearProgressIndicator(
                                progress = { if (total > 0) processed.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "正在收藏 $processed/$total 首（成功 $succeeded，失败 $failed）",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                when {
                    loading -> item(key = "favorite_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.android.purebilibili.core.ui.components.AppCircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    error != null -> item(key = "favorite_error") {
                        Column {
                            Text(error, color = AppContentColors.error(), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = onRetry, enabled = !favoriting) { Text("重试") }
                        }
                    }
                    folders.isEmpty() -> item(key = "favorite_empty") {
                        Text("当前账号没有可用的收藏夹", color = AppContentColors.secondaryText())
                    }
                    else -> itemsIndexed(folders, key = { _, folder -> folder.id }) { _, folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !favoriting) { onToggleFolder(folder.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = folder.id in selectedIds,
                                onCheckedChange = { onToggleFolder(folder.id) },
                                enabled = !favoriting,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.title.ifBlank { "未命名收藏夹" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${folder.media_count} 个内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (favoriting) onStop() else onStart() },
                enabled = favoriting || (matchedCount > 0 && selectedIds.isNotEmpty()),
            ) {
                Text(
                    when {
                        favoriting -> "停止收藏"
                        matchedCount <= 0 -> "没有可收藏的视频"
                        selectedIds.isEmpty() -> "请选择收藏夹"
                        selectedIds.size == 1 -> "收藏 $matchedCount 首"
                        else -> "收藏 $matchedCount 首到 ${selectedIds.size} 个收藏夹"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBack, enabled = !favoriting) { Text("返回") }
        },
    )
}
