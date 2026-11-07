package com.android.purebilibili.feature.article

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.repository.ArticleDetailUiModel
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.dynamic.components.*

@Composable
internal fun rememberArticleCommentContent(
    article: ArticleDetailUiModel,
    viewModel: DynamicViewModel,
    onUserClick: (Long) -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onImagePreview: (List<String>, Int) -> Unit,
): LazyListScope.() -> Unit {
    val context = LocalContext.current
    val state by viewModel.commentUiState.collectAsStateWithLifecycle()
    val sort by viewModel.dynamicCommentSortMode.collectAsStateWithLifecycle()
    val commentId = article.dynamicId.ifBlank { "article:${article.articleId}" }
    val belongsToArticle = state.dynamicId == commentId
    val loading = !belongsToArticle || state.isLoading
    val count = if (belongsToArticle && !loading && state.refreshError == null) state.totalCount.toLong() else article.stats?.reply
    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    return {
        item(key = "article-comments-header") {
            Column {
                if (count == null) {
                    AppText("评论", style = MaterialTheme.typography.titleMedium)
                } else {
                    DynamicInlineCommentHeader(
                        totalCount = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                        sortMode = sort,
                        onSortModeChange = viewModel::setDynamicCommentSortMode,
                    )
                }
                Row {
                    AppTextButton(onClick = viewModel::openCommentComposer) { AppText("发表评论", tapToCopyEnabled = false) }
                    AppTextButton(onClick = viewModel::refreshComments, enabled = !loading && !state.isRefreshing) {
                        AppText(if (state.isRefreshing) "刷新中…" else "刷新评论", tapToCopyEnabled = false)
                    }
                }
            }
        }
        if (belongsToArticle && state.refreshError != null) {
            item(key = "article-comments-error") {
                Column(Modifier.padding(vertical = 12.dp)) {
                    AppText(state.refreshError.orEmpty())
                    AppTextButton(onClick = viewModel::refreshComments) { AppText("重试") }
                }
            }
        }
        if (loading || state.refreshError == null || state.comments.isNotEmpty()) {
            dynamicInlineCommentItems(
                comments = if (belongsToArticle) state.comments else emptyList(),
                isLoading = loading,
                isLoadingMore = belongsToArticle && state.isLoadingMore,
                onViewReplies = viewModel::openSubReply,
                onReply = viewModel::startCommentReply,
                onLike = { viewModel.likeComment(it.rpid) },
                onHate = { viewModel.hateComment(it.rpid) { _, message -> notify(message) } },
                dynamicAuthorMid = article.authorMid,
                currentUserMid = TokenManager.midCache,
                onDelete = { viewModel.deleteDynamicComment(it.rpid) { _, message -> notify(message) } },
                onToggleTop = { viewModel.toggleDynamicCommentTop(it) { _, message -> notify(message) } },
                onReport = { reply, reason -> viewModel.reportDynamicComment(reply.rpid, reason) { _, message -> notify(message) } },
                onUserClick = onUserClick,
                onUrlClick = onLinkClick,
                onImagePreview = { images, index, _, _ -> onImagePreview(images, index) },
            )
        }
        if (belongsToArticle && state.hasMore && !state.isLoadingMore && !loading) {
            item(key = "article-comments-more") {
                AppTextButton(onClick = viewModel::loadMoreComments, modifier = Modifier.fillMaxWidth()) { AppText("加载更多评论") }
            }
        }
    }
}

@Composable
internal fun ArticleCommentOverlays(viewModel: DynamicViewModel, article: ArticleDetailUiModel, onUserClick: (Long) -> Unit, onLinkClick: ((String) -> Unit)?) {
    val context = LocalContext.current
    val subReplies by viewModel.subReplyState.collectAsStateWithLifecycle()
    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    if (subReplies.visible) {
        DynamicSubReplyPreviewHost(
            state = subReplies,
            onDismiss = viewModel::closeSubReply,
            onLoadMore = viewModel::loadMoreSubReplies,
            onRefresh = viewModel::refreshSubReplies,
            onSortModeChange = viewModel::setSubReplySortMode,
            onUserClick = onUserClick,
            onUrlClick = onLinkClick,
            onReplyClick = viewModel::startCommentReply,
            onCommentLike = { viewModel.likeComment(it) },
            onCommentHate = { viewModel.hateComment(it) { _, message -> notify(message) } },
            currentMid = TokenManager.midCache ?: 0L,
            onDeleteComment = { viewModel.deleteDynamicComment(it) { _, message -> notify(message) } },
        )
    }
    DynamicCommentInputHost(draftKeyPrefix = "article:${article.articleId}", viewModel = viewModel) { message, images, done ->
        viewModel.postComment(article.dynamicId.ifBlank { "article:${article.articleId}" }, message, images) { success, toast ->
            notify(toast)
            done(success)
        }
    }
}
