package com.android.purebilibili.feature.article

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.ArticleDetailUiModel
import com.android.purebilibili.data.repository.ArticleRepository
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
internal fun ArticleInteractionBar(
    article: ArticleDetailUiModel,
    comments: DynamicViewModel,
    onCommentClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    openCommentRootRpid: Long = 0L,
    openCommentTargetRpid: Long = 0L,
    visible: Boolean = true,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
) {
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    // 操作协程与状态同时绑定文章、账号，切换后不接收旧文章的异步结果。
    key(article.articleId, account) {
        ArticleInteractionSession(article, comments, onCommentClick, onUserClick, onLinkClick, modifier, openCommentRootRpid, openCommentTargetRpid, visible, backdrop, hazeState)
    }
}

@Composable
private fun ArticleInteractionSession(
    article: ArticleDetailUiModel,
    comments: DynamicViewModel,
    onCommentClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    openCommentRootRpid: Long = 0L,
    openCommentTargetRpid: Long = 0L,
    visible: Boolean = true,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ArticleInfoData?>(null) }
    var infoLoading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var likeBusy by remember { mutableStateOf(false) }
    var favoriteBusy by remember { mutableStateOf(false) }
    var showRepost by remember { mutableStateOf(false) }
    val commentState by comments.commentUiState.collectAsStateWithLifecycle()
    val commentId = article.dynamicId.ifBlank { "article:${article.articleId}" }
    val knownCommentCount = commentState.totalCount.toLong().takeIf {
        commentState.dynamicId == commentId && !commentState.isLoading && commentState.refreshError == null
    }
    LaunchedEffect(knownCommentCount) {
        knownCommentCount?.let { count ->
            info = info?.let { it.copy(stats = (it.stats ?: ArticleStats()).copy(reply = count)) }
        }
    }
    val commentItem = remember(article, info) {
        DynamicItem(
            id_str = article.dynamicId.ifBlank { "article:${article.articleId}" },
            type = "DYNAMIC_TYPE_ARTICLE",
            basic = DynamicBasic(comment_id_str = article.articleId.toString(), comment_type = 12),
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = article.authorMid, name = article.authorName, face = article.authorFace),
                module_stat = DynamicStatModule(comment = StatItem(count = (info?.stats?.reply ?: article.stats?.reply)?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0)),
            ),
        )
    }
    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    LaunchedEffect(retry) {
        infoLoading = true
        ArticleRepository.getArticleInfo(article.articleId).fold(
            onSuccess = { info = it.copy(stats = mergeArticleInteractionStats(it.stats, article.stats)) },
            onFailure = { notify(it.message ?: "互动信息加载失败，点击点赞或收藏可重试") },
        )
        infoLoading = false
    }
    val selectedCommentId by comments.selectedDynamicId.collectAsStateWithLifecycle()
    var routeHandled by remember(article.articleId, openCommentRootRpid, openCommentTargetRpid) { mutableStateOf(false) }
    LaunchedEffect(article.articleId, selectedCommentId, openCommentRootRpid, openCommentTargetRpid) {
        if (!routeHandled || selectedCommentId != commentId) {
            val root = if (!routeHandled) {
                openCommentRootRpid.coerceAtLeast(0L)
            } else 0L
            val target = if (!routeHandled) openCommentTargetRpid.coerceAtLeast(0L) else 0L
            routeHandled = true
            comments.openCommentSheet(commentItem, rootReplyId = root, targetReplyId = target)
        }
    }
    DisposableEffect(comments) {
        onDispose { comments.closeCommentSheet() }
    }
    fun onRepostClick() {
        if (TokenManager.csrfCache.isNullOrBlank()) notify("请先登录") else showRepost = true
    }

    fun onFavoriteClick() {
        if (favoriteBusy || infoLoading) return
        if (TokenManager.csrfCache.isNullOrBlank()) { notify("请先登录"); return }
        val current = info
        if (current == null) { retry++; return }
        favoriteBusy = true
        scope.launch {
            try {
                val next = !current.favorite
                ArticleRepository.setFavorite(article.articleId, next).fold(
                    onSuccess = {
                        val latest = info ?: current
                        info = latest.copy(favorite = next, stats = latest.stats?.copy(
                            favorite = latest.stats?.favorite?.let { (it + if (next) 1L else -1L).coerceAtLeast(0L) },
                        ))
                        notify(if (next) "收藏成功" else "已取消收藏")
                    },
                    onFailure = { notify(it.message ?: "收藏失败") },
                )
            } finally { favoriteBusy = false }
        }
    }

    fun onCommentJump() {
        onCommentClick()
    }

    fun onLikeClick() {
        if (likeBusy || infoLoading) return
        if (TokenManager.csrfCache.isNullOrBlank()) { notify("请先登录"); return }
        val current = info
        if (current == null) { retry++; return }
        likeBusy = true
        scope.launch {
            try {
                val next = !current.like
                ArticleRepository.setLike(article, next).fold(
                    onSuccess = {
                        val latest = info ?: current
                        info = latest.copy(like = next, stats = latest.stats?.copy(
                            like = latest.stats?.like?.let { (it + if (next) 1L else -1L).coerceAtLeast(0L) },
                        ))
                        notify(if (next) "点赞成功" else "已取消点赞")
                    },
                    onFailure = { notify(it.message ?: "点赞失败") },
                )
            } finally { likeBusy = false }
        }
    }

    ArticleInteractionBarContent(
        state = ArticleActionsUiState(
            info = info,
            stats = info?.stats ?: article.stats,
            commentCount = knownCommentCount ?: info?.stats?.reply ?: article.stats?.reply,
            canRepost = article.dynamicId.isNotBlank(),
            likeEnabled = !likeBusy && !infoLoading,
            favoriteEnabled = !favoriteBusy && !infoLoading,
        ),
        onRepost = ::onRepostClick,
        onFavorite = ::onFavoriteClick,
        onComment = ::onCommentJump,
        onLike = ::onLikeClick,
        visible = visible,
        backdrop = backdrop,
        hazeState = hazeState,
        modifier = modifier,
    )
    ArticleCommentOverlays(comments, article, onUserClick, onLinkClick)
    if (showRepost) {
        RepostDialog(onDismiss = { showRepost = false }, contentKey = "article:${article.articleId}") { text, alsoComment, richContents, topic, done ->
            // 显式按专栏评论目标同步，不能按动态 ID 推断专栏的评论区。
            comments.repostDynamic(article.dynamicId, text, false, richContents, topic) { success, message ->
                if (success) {
                    showRepost = false
                    info = info?.let { current -> current.copy(stats = current.stats?.copy(share = current.stats?.share?.plus(1))) }
                    if (alsoComment && text.isNotBlank()) {
                        scope.launch {
                            com.android.purebilibili.data.repository.CommentRepository.addCommentForSubject(
                                oid = article.articleId, type = 12, message = text,
                            ).fold(
                                onSuccess = { notify("转发成功，已同步评论") },
                                onFailure = { notify("转发成功，评论同步失败") },
                            )
                        }
                    } else notify(message)
                } else notify(message)
                done(success)
            }
        }
    }
}

private data class ArticleActionsUiState(
    val info: ArticleInfoData?,
    val stats: ArticleStats?,
    val commentCount: Long?,
    val canRepost: Boolean,
    val likeEnabled: Boolean,
    val favoriteEnabled: Boolean,
)

@Composable
private fun ArticleInteractionBarContent(
    state: ArticleActionsUiState,
    onRepost: () -> Unit,
    onFavorite: () -> Unit,
    onComment: () -> Unit,
    onLike: () -> Unit,
    visible: Boolean,
    backdrop: Backdrop?,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val config = LocalAppThemeConfig.current
    val reduceMotion = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    val actions: @Composable RowScope.() -> Unit = {
            ArticleAction("转发", state.stats?.share, rememberAppShareIcon(), enabled = state.canRepost, onClick = onRepost)
            ArticleAction("收藏", state.stats?.favorite, rememberAppCollectionIcon(), selected = state.info?.favorite == true, toggleable = true,
                enabled = state.favoriteEnabled, onClick = onFavorite)
            ArticleAction("评论", state.commentCount, rememberAppCommentIcon(), onClick = onComment)
            ArticleAction("点赞", state.stats?.like,
                if (state.info?.like == true) rememberAppLikeFilledIcon() else rememberAppLikeIcon(),
                selected = state.info?.like == true, toggleable = true, enabled = state.likeEnabled, onClick = onLike)
    }
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else slideInVertically { it } + fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else slideOutVertically { it } + fadeOut(),
    ) {
        // 两种材质共用紧凑悬浮几何；玻璃光学参数不参与原生底板绘制。
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape()
            val dockModifier = Modifier.widthIn(max = 304.dp).fillMaxWidth()
            val rowModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
            if (config.liquidGlassEnabled) {
                com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock(
                    shape = shape,
                    modifier = dockModifier,
                    backdrop = backdrop,
                    reuseEnabled = true,
                    elasticFeedbackEnabled = true,
                    shellLensIntensity = com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale(52f),
                    captureShellForContent = false,
                ) {
                    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            } else {
                val frosted = config.bottomBarBlurEnabled && hazeState != null
                val surface = if (frosted) com.android.purebilibili.feature.home.components.resolveBottomBarSurfaceColor(
                    surfaceColor = AppSurfaceTokens.surfaceContainer(), blurEnabled = true,
                    blurIntensity = currentUnifiedBlurIntensity(),
                ) else AppSurfaceTokens.surfaceContainer()
                Box(
                    modifier = dockModifier
                        .clip(shape)
                        .then(if (frosted && hazeState != null) Modifier.unifiedBlur(
                            hazeState = hazeState,
                            surfaceType = BlurSurfaceType.BOTTOM_BAR,
                        ) else Modifier)
                        .background(surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

// 四个操作使用等宽槽位，异步数量只更新槽位内文字，不改变操作栏几何尺寸。
@Composable
private fun RowScope.ArticleAction(
    label: String, count: Long?, icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    toggleable: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    AppTextButton(onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = modifier.weight(1f).heightIn(min = 48.dp).semantics(mergeDescendants = true) {
        contentDescription = if (count != null) "$label，$count" else label
        if (toggleable) this.selected = selected
    }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else AppContentColors.secondaryText())
            AppText(count?.let(::formatArticleActionCount) ?: label, maxLines = 1, tapToCopyEnabled = false,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

