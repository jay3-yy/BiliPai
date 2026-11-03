@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.bangumi

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.BangumiReviewItem
import com.android.purebilibili.data.model.response.BangumiReviewType
import com.android.purebilibili.data.repository.BangumiReviewRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

private data class CachedBangumiReviews(
    val items: List<BangumiReviewItem>,
    val nextCursor: String,
    val hasMore: Boolean,
    val count: Int?,
)

@Composable
fun BangumiReviewScreen(
    mediaId: Long,
    title: String,
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit = { _, _ -> },
    onOpenArticle: (Long, String) -> Unit = { _, _ -> },
    embedded: Boolean = false,
    contentActive: Boolean = true,
    onUserClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val scope = rememberCoroutineScope()
    val reviewTypes = remember { listOf(BangumiReviewType.LONG, BangumiReviewType.SHORT) }
    val sortByType = remember(mediaId) { mutableStateMapOf<BangumiReviewType, Int>() }
    val shortListState = rememberLazyListState()
    val longListState = rememberLazyListState()
    var reviewType by remember { mutableStateOf(BangumiReviewType.LONG) }
    val listState = if (reviewType == BangumiReviewType.SHORT) shortListState else longListState
    val shortHeaderCollapse = remember { mutableFloatStateOf(0f) }
    val longHeaderCollapse = remember { mutableFloatStateOf(0f) }
    val headerCollapse = if (reviewType == BangumiReviewType.SHORT) shortHeaderCollapse else longHeaderCollapse
    val headerHeight = remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<BangumiReviewItem>>(emptyList()) }
    var reviewCount by remember { mutableStateOf<Int?>(null) }
    var showPostMenu by remember { mutableStateOf(false) }
    var actionReview by remember { mutableStateOf<BangumiReviewItem?>(null) }
    var nextCursor by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var editingReview by remember { mutableStateOf<BangumiReviewItem?>(null) }
    var deletingReview by remember { mutableStateOf<BangumiReviewItem?>(null) }
    var busyReviews by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var submittingReview by remember { mutableStateOf(false) }
    var reloadGeneration by remember { mutableIntStateOf(0) }
    val reviewCache = remember(mediaId) { mutableStateMapOf<Pair<BangumiReviewType, Int>, CachedBangumiReviews>() }
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive() && contentActive
    val reduceMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()

    fun reload() {
        reviewCache.remove(reviewType to sort)
        reviewCount = null
        loading = true
        loadingMore = false
        errorMessage = null
        reloadGeneration++
    }

    fun selectQuery(type: BangumiReviewType, order: Int) {
        if (type == reviewType && order == sort) return
        val sortChanged = type == reviewType && order != sort
        if (!loading && errorMessage == null && busyReviews.isEmpty()) {
            reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore, reviewCount)
        } else if (busyReviews.isNotEmpty()) {
            reviewCache.remove(reviewType to sort)
        }
        val cached = reviewCache[type to order]
        reviewCount = cached?.count
        items = cached?.items.orEmpty()
        nextCursor = cached?.nextCursor.orEmpty()
        hasMore = cached?.hasMore ?: false
        loading = cached == null
        loadingMore = false
        errorMessage = null
        reviewType = type
        sort = order
        sortByType[type] = order
        if (sortChanged) {
            headerCollapse.floatValue = 0f
            listState.requestScrollToItem(0)
        }
    }

    LaunchedEffect(mediaId, reviewType, sort, reloadGeneration) {
        if (!loading) return@LaunchedEffect
        val result = BangumiReviewRepository.getReviews(mediaId = mediaId, type = reviewType, sort = sort)
        currentCoroutineContext().ensureActive()
        result.fold(
            onSuccess = { page ->
                reviewCount = if (reviewType == BangumiReviewType.LONG && sort == 1) null else page.count
                items = page.items
                nextCursor = page.next
                hasMore = page.hasMore && (reviewCount?.let { items.size < it } ?: true)
                reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore, reviewCount)
            },
            onFailure = { error ->
                if (items.isEmpty()) errorMessage = error.message ?: "点评加载失败"
                else Toast.makeText(context, error.message ?: "点评刷新失败", Toast.LENGTH_SHORT).show()
            },
        )
        loading = false
    }

    // 预取另一种点评，类型与排序分别缓存，切换后保留各自列表位置。
    LaunchedEffect(mediaId, reviewType, sort, loading) {
        if (loading) return@LaunchedEffect
        val other = reviewTypes.firstOrNull { it != reviewType } ?: return@LaunchedEffect
        val query = other to sort
        if (reviewCache.containsKey(query)) return@LaunchedEffect
        val result = BangumiReviewRepository.getReviews(mediaId = mediaId, type = other, sort = sort)
        currentCoroutineContext().ensureActive()
        result.onSuccess { page ->
            if (!reviewCache.containsKey(query)) reviewCache[query] = CachedBangumiReviews(page.items, page.next, page.hasMore, if (other == BangumiReviewType.LONG && sort == 1) null else page.count)
        }
    }

    LaunchedEffect(mediaId, reviewType, sort, listState, hasMore, nextCursor, loading, contentActive) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to listState.layoutInfo.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, total) ->
            if (!contentActive || !hasMore || loading || loadingMore || nextCursor.isBlank()) return@collect
            if (total > 0 && lastVisible >= total - 3) {
                loadingMore = true
                val result = BangumiReviewRepository.getReviews(
                    mediaId = mediaId,
                    type = reviewType,
                    cursor = nextCursor,
                    sort = sort
                )
                currentCoroutineContext().ensureActive()
                result.onSuccess { page ->
                    items = items + page.items
                    nextCursor = page.next
                    hasMore = page.hasMore && (reviewCount?.let { items.size < it } ?: true)
                    reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore, reviewCount)
                }
                loadingMore = false
            }
        }
    }

    fun dislikeReview(item: BangumiReviewItem) {
        val requestQuery = reviewType to sort
        busyReviews = busyReviews + item.review_id
        scope.launch {
            BangumiReviewRepository.dislikeReview(mediaId, item.review_id)
                .onSuccess {
                    if (requestQuery != (reviewType to sort)) {
                        reviewCache.remove(requestQuery)
                        return@onSuccess
                    }
                    items = items.map { current ->
                        if (current.review_id != item.review_id) current else {
                            val stat = current.stat ?: com.android.purebilibili.data.model.response.BangumiReviewStat()
                            val disliked = stat.disliked == 1
                            current.copy(stat = stat.copy(
                                disliked = if (disliked) 0 else 1,
                                liked = if (disliked) stat.liked else 0,
                                likes = (stat.likes - if (!disliked && stat.liked == 1) 1 else 0).coerceAtLeast(0),
                            ))
                        }
                    }
                }.onFailure { Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show() }
            busyReviews = busyReviews - item.review_id
        }
    }

    fun likeReview(item: BangumiReviewItem) {
        if (item.review_id <= 0L || item.review_id in busyReviews) return
        val requestQuery = reviewType to sort
        busyReviews = busyReviews + item.review_id
        scope.launch {
            BangumiReviewRepository.likeReview(mediaId, item.review_id)
                .onSuccess {
                    if (requestQuery != (reviewType to sort)) {
                        reviewCache.remove(requestQuery)
                        return@onSuccess
                    }
                    items = items.map { current ->
                        if (current.review_id != item.review_id) {
                            current
                        } else {
                            val stat = current.stat ?: com.android.purebilibili.data.model.response.BangumiReviewStat()
                            val liked = stat.liked == 1
                            current.copy(
                                stat = stat.copy(
                                    liked = if (liked) 0 else 1,
                                    disliked = if (liked) stat.disliked else 0,
                                    likes = (stat.likes + if (liked) -1 else 1)
                                        .coerceAtLeast(0)
                                )
                            )
                        }
                    }
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "点赞失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            busyReviews = busyReviews - item.review_id
        }
    }

    val headerScroll = remember(listState, headerCollapse, contentActive) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (contentActive) {
                    headerCollapse.floatValue = (headerCollapse.floatValue - available.y)
                        .coerceIn(0f, headerHeight.intValue.toFloat())
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!listState.canScrollBackward) headerCollapse.floatValue = 0f
                return Offset.Zero
            }
        }
    }
    val reviewHeader: @Composable (Modifier) -> Unit = { headerModifier ->
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                reviewCount?.let { "${com.android.purebilibili.core.util.FormatUtils.formatStat(it.toLong())}条点评" }.orEmpty(),
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
            )
            AppTextButton(onClick = { selectQuery(reviewType, 1 - sort) }, enabled = !loading) {
                AppIcon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                AppText(if (sort == 0) "默认" else "最新", fontSize = 13.sp)
            }
        }
    }

    AppScaffold(
        blurContentReady = !loading,
        topBar = if (embedded) null else ({
            AppTopBar(
                title = title.ifBlank { "点评" },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
            )
        }),
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    reviewTypes.forEach { type ->
                        val selected = reviewType == type
                        Box(
                            Modifier.heightIn(min = 48.dp)
                                .clickable {
                                    if (selected) scope.launch { listState.animateScrollToItem(0) }
                                    else selectQuery(type, sortByType[type] ?: 0)
                                }
                                .semantics { this.selected = selected },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppSurface(
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = AppShapes.container(ContainerLevel.Pill),
                            ) {
                                AppText(
                                    type.label,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
                AdaptivePullToRefreshBox(
                    isRefreshing = loading && items.isNotEmpty() && visualWorkActive && !reduceMotion,
                    onRefresh = { reload() },
                    modifier = Modifier.weight(1f).clipToBounds(),
                    enabled = contentActive,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().nestedScroll(headerScroll),
                        contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 100.dp),
                    ) {
                        item(key = "review_header_space") {
                            Spacer(Modifier.layout { _, constraints ->
                                layout(constraints.maxWidth, headerHeight.intValue.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
                            })
                        }
                        when {
                            loading && items.isEmpty() -> item {
                                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                                    // 隐藏或滑动中的点评页不启动装饰性加载动画。
                                    if (visualWorkActive && !reduceMotion) com.android.purebilibili.core.ui.CutePersonLoadingIndicator()
                                    else AppText("加载中…", color = AppContentColors.secondaryText())
                                }
                            }
                            errorMessage != null && items.isEmpty() -> item {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AppText(errorMessage.orEmpty())
                                    AppTextButton(onClick = { reload() }) { AppText("重试") }
                                }
                            }
                            items.isEmpty() -> item {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { AppText("还没有点评") }
                            }
                            else -> items(items, key = { it.review_id.takeIf { id -> id > 0L } ?: it.article_id }) { item ->
                                BangumiReviewCard(
                                    item = item,
                                    reviewType = reviewType,
                                    busy = item.review_id in busyReviews,
                                    onAuthorClick = { item.author?.let { onUserClick(it.mid) } },
                                    onArticleClick = { onOpenArticle(item.article_id, item.title) },
                                    onLike = { likeReview(item) },
                                    onDislike = { dislikeReview(item) },
                                    onShowMore = { actionReview = item },
                                )
                                AppHorizontalDivider(color = AppContentColors.secondaryText().copy(alpha = 0.1f))
                            }
                        }
                        if (loadingMore) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppText("正在加载更多…") }
                        }
                    }
                    reviewHeader(
                        Modifier.align(Alignment.TopStart)
                            .onSizeChanged { headerHeight.intValue = it.height }
                            .offset { IntOffset(0, -headerCollapse.floatValue.roundToInt()) },
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()) {
                AppFloatingActionButton(onClick = { showPostMenu = true }) {
                    AppIcon(Icons.Outlined.Edit, contentDescription = "写点评")
                }
                AppDropdownMenu(expanded = showPostMenu, onDismissRequest = { showPostMenu = false }) {
                    AppDropdownMenuItem(text = { AppText("写短评") }, onClick = {
                        showPostMenu = false
                        editingReview = null
                        showComposer = true
                    })
                    AppDropdownMenuItem(text = { AppText("写长评") }, onClick = {
                        showPostMenu = false
                        onOpenWeb("https://member.bilibili.com/article-text/mobile?theme=${if (darkTheme) 1 else 0}&media_id=$mediaId", "写长评")
                    })
                }
            }
        }
    }

    actionReview?.let { review ->
        AppAlertDialog(
            onDismissRequest = { actionReview = null },
            title = { AppText("短评操作") },
            text = {
                Column {
                    if (review.author?.mid == TokenManager.midCache) {
                        AppTextButton(onClick = { actionReview = null; editingReview = review; showComposer = true }) { AppText("编辑") }
                        AppTextButton(onClick = { actionReview = null; deletingReview = review }) { AppText("删除") }
                    }
                    AppTextButton(onClick = {
                        actionReview = null
                        onOpenWeb("https://www.bilibili.com/appeal/?reviewId=${review.review_id}&type=shortComment&mediaId=$mediaId", "举报短评")
                    }) { AppText("举报") }
                }
            },
            confirmButton = { AppDialogAction(onClick = { actionReview = null }) { AppText("取消") } },
        )
    }

    if (showComposer) {
        BangumiShortReviewComposer(
            title = title,
            initialReview = editingReview,
            submitting = submittingReview,
            onDismiss = { if (!submittingReview) { showComposer = false; editingReview = null } },
            onSubmit = { score, content, shareFeed ->
                val editing = editingReview
                submittingReview = true
                scope.launch {
                    val result = if (editing == null) BangumiReviewRepository.postShortReview(mediaId, score, content, shareFeed)
                        else BangumiReviewRepository.modifyShortReview(mediaId, editing.review_id, score, content)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(context, if (editing == null) "已发布短评" else "已更新短评", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            editingReview = null
                            reviewCache.keys.filter { it.first == BangumiReviewType.SHORT }.toList().forEach(reviewCache::remove)
                            if (reviewType == BangumiReviewType.SHORT) reload()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, error.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                        }
                    )
                    submittingReview = false
                }
            }
        )
    }
    deletingReview?.let { review ->
        AppAlertDialog(
            onDismissRequest = { deletingReview = null },
            title = { AppText("删除短评") },
            text = { AppText("删除短评时会同时删除评分。") },
            confirmButton = {
                AppDialogAction(onClick = {
                    deletingReview = null
                    busyReviews = busyReviews + review.review_id
                    scope.launch {
                        BangumiReviewRepository.deleteShortReview(mediaId, review.review_id)
                            .onSuccess {
                                reviewCache.keys.filter { it.first == BangumiReviewType.SHORT }.toList().forEach(reviewCache::remove)
                                if (reviewType == BangumiReviewType.SHORT) {
                                    items = items.filterNot { it.review_id == review.review_id }
                                    reviewCount = reviewCount?.minus(1)
                                }
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show() }
                        busyReviews = busyReviews - review.review_id
                    }
                }) { AppText("删除") }
            },
            dismissButton = { AppDialogAction(onClick = { deletingReview = null }) { AppText("取消") } },
        )
    }
}

@Composable
private fun BangumiShortReviewComposer(
    title: String,
    initialReview: BangumiReviewItem?,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String, Boolean) -> Unit,
) {
    var content by remember(initialReview) { mutableStateOf(initialReview?.content.orEmpty()) }
    var score by remember(initialReview) { mutableIntStateOf(initialReview?.score ?: 0) }
    var shareFeed by remember { mutableStateOf(false) }
    AppModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AppIconButton(onClick = onDismiss, enabled = !submitting) {
                    AppIcon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
            AppHorizontalDivider(color = AppContentColors.secondaryText().copy(alpha = 0.1f))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 250.dp).fillMaxWidth()
                    .pointerInput(submitting) {
                        if (!submitting) detectHorizontalDragGestures(
                            onDragStart = { position ->
                                score = ((position.x / size.width * 5).toInt().coerceIn(0, 4) + 1) * 2
                            },
                        ) { change, _ ->
                            change.consume()
                            score = ((change.position.x / size.width * 5).toInt().coerceIn(0, 4) + 1) * 2
                        }
                    },
            ) {
                repeat(5) { index ->
                    AppIconButton(
                        onClick = { score = (index + 1) * 2 },
                        enabled = !submitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppIcon(
                            if (index < score / 2) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "${index + 1}星",
                            tint = if (index < score / 2) Color(0xFFFFAD35) else AppContentColors.secondaryText(),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            AppText(
                when (score / 2) {
                    1 -> "很差"
                    2 -> "较差"
                    3 -> "还行"
                    4 -> "很好"
                    5 -> "佳作"
                    else -> "轻触评分"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = if (score > 0) Color(0xFFFFAD35) else AppContentColors.secondaryText(),
            )
            AppTextField(
                value = content,
                onValueChange = { content = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "写下你的观后感",
                singleLine = false,
                minLines = 5,
                maxLines = 5,
                supportingText = { AppText("${content.length}/100") },
            )
            if (initialReview == null) {
                Row(
                    modifier = Modifier.toggleable(
                        value = shareFeed,
                        enabled = !submitting,
                        role = Role.Checkbox,
                        onValueChange = { shareFeed = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppCheckbox(checked = shareFeed, onCheckedChange = null, enabled = !submitting)
                    AppText("分享到动态")
                }
            }
            AppButton(
                onClick = { onSubmit(score, content, shareFeed) },
                enabled = !submitting && score > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { AppText(if (submitting) "正在提交…" else if (initialReview == null) "点评" else "编辑") }
        }
    }
}
