package com.android.purebilibili.feature.web

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.plugin.feed.FeedBlock
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.home.subscription.FeedReadingBlock

@Composable
internal fun InstantArticleScreen(
    url: String,
    initialTitle: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onNativeRedirect: (String) -> Unit,
) {
    val model: InstantArticleViewModel = viewModel(key = "instant-article:$url")
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url, model) { model.load(url) }
    LaunchedEffect(state) {
        (state as? InstantArticleUiState.NativeRedirect)?.let { onNativeRedirect(it.url) }
    }
    InstantArticleContent(
        state = state,
        initialTitle = initialTitle,
        modifier = modifier,
        onBack = onBack,
        onRetry = { model.load(url, force = true) },
        onOpenLink = onOpenLink,
        onOpenOriginal = {
            val original = (state as? InstantArticleUiState.Content)?.url ?: url
            runCatching { uriHandler.openUri(original) }.onFailure {
                Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

private data class InstantArticleImageRequest(
    val images: List<String>,
    val index: Int,
    val rect: Rect?,
    val rects: Map<Int, Rect>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstantArticleContent(
    state: InstantArticleUiState,
    initialTitle: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenOriginal: () -> Unit,
) {
    val content = state as? InstantArticleUiState.Content
    val imageUrls = remember(content) {
        content?.blocks?.filterIsInstance<FeedBlock.Image>()?.map { it.url }?.distinct().orEmpty()
    }
    val imageRects = remember(content) { mutableStateMapOf<Int, Rect>() }
    var imageRequest by remember(content) { mutableStateOf<InstantArticleImageRequest?>(null) }
    val listState = rememberLazyListState()

    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "即时阅读",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    AppTextButton(onClick = onOpenOriginal) { AppText("打开原文") }
                },
            )
        },
    ) { padding ->
        when (state) {
            InstantArticleUiState.Loading, is InstantArticleUiState.NativeRedirect -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { AdaptiveLoadingIndicator() }
            is InstantArticleUiState.Unavailable -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                AppText(state.message)
                AppText(
                    "可以重试，或打开原文继续查看。",
                    color = AppContentColors.secondaryText(),
                )
                AppTextButton(onClick = onRetry) { AppText("重新读取") }
                AppTextButton(onClick = onOpenOriginal) { AppText("打开原文") }
            }
            is InstantArticleUiState.Content -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).appScrollBoundaryHaptics(listState),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            state.title.ifBlank { initialTitle }.ifBlank { "网页正文" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        AppText(
                            listOf(state.author, runCatching { java.net.URI(state.url).host }.getOrNull().orEmpty())
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
                items(state.blocks) { block ->
                    FeedReadingBlock(
                        block = block,
                        textScale = 1f,
                        imageUrls = imageUrls,
                        imageSourceRects = imageRects,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        onOpenUrl = onOpenLink,
                        onOpenImages = { images, index, rect, rects ->
                            imageRequest = InstantArticleImageRequest(images, index, rect, rects)
                        },
                    )
                }
            }
        }
    }
    imageRequest?.let { request ->
        ImagePreviewDialog(
            images = request.images,
            publicContent = true,
            initialIndex = request.index,
            sourceRect = request.rect,
            sourceRects = request.rects,
            sourceCornerRadiusDp = 12f,
            onDismiss = { imageRequest = null },
        )
    }
}
