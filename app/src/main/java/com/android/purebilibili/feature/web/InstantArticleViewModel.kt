package com.android.purebilibili.feature.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.plugin.feed.ArticleContentUnavailable
import com.android.purebilibili.core.plugin.feed.ArticleHttpFailure
import com.android.purebilibili.core.plugin.feed.FeedBlock
import com.android.purebilibili.core.plugin.feed.NativeArticleRedirect
import com.android.purebilibili.core.plugin.feed.fetchReadableArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException

internal sealed interface InstantArticleUiState {
    data object Loading : InstantArticleUiState
    data class Content(
        val url: String,
        val title: String,
        val author: String,
        val blocks: List<FeedBlock>,
    ) : InstantArticleUiState
    data class Unavailable(val message: String) : InstantArticleUiState
    data class NativeRedirect(val url: String) : InstantArticleUiState
}

internal class InstantArticleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<InstantArticleUiState>(InstantArticleUiState.Loading)
    val state = mutableState.asStateFlow()
    private var requestedUrl: String? = null
    private var request: Job? = null

    fun load(url: String, force: Boolean = false) {
        if (!force && requestedUrl == url) return
        requestedUrl = url
        request?.cancel()
        mutableState.value = InstantArticleUiState.Loading
        request = viewModelScope.launch {
            val result = fetchReadableArticle(url)
            val next = result.fold(
                onSuccess = { article ->
                    val blocks = withContext(Dispatchers.Default) { article.readingBlocks() }
                    if (blocks.isEmpty()) {
                        InstantArticleUiState.Unavailable("已提取正文，但暂时无法转换为阅读内容")
                    } else {
                        InstantArticleUiState.Content(article.url, article.title, article.author, blocks)
                    }
                },
                onFailure = { error ->
                    when (error) {
                        is NativeArticleRedirect -> InstantArticleUiState.NativeRedirect(error.targetUrl)
                        is ArticleHttpFailure -> InstantArticleUiState.Unavailable("原文请求失败（HTTP ${error.statusCode}）")
                        is ArticleContentUnavailable -> InstantArticleUiState.Unavailable(error.userMessage)
                        is InterruptedIOException -> InstantArticleUiState.Unavailable("连接原文超时")
                        else -> InstantArticleUiState.Unavailable("暂时无法获取原文")
                    }
                },
            )
            ensureActive()
            mutableState.value = next
        }
    }
}
