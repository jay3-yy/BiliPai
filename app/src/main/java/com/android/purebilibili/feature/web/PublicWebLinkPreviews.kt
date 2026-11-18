package com.android.purebilibili.feature.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.R
import com.android.purebilibili.core.plugin.feed.BilibiliPreviewLookup
import com.android.purebilibili.core.plugin.feed.PublicContentImageLoader
import com.android.purebilibili.core.plugin.feed.PublicLinkPreview
import com.android.purebilibili.core.plugin.feed.fetchBilibiliLinkPreview
import com.android.purebilibili.core.plugin.feed.fetchBilibiliShortLinkPreview
import com.android.purebilibili.core.plugin.feed.fetchPublicLinkPreview
import com.android.purebilibili.core.plugin.feed.isPublicBilibiliShortLink
import com.android.purebilibili.core.plugin.feed.isPublicBilibiliToyPage
import com.android.purebilibili.core.plugin.feed.publicLinkPreviewCache
import com.android.purebilibili.core.plugin.feed.publicLinkPreviewUrl
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.ui.rememberAppLinkIcon
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import okhttp3.HttpUrl

// 先读取保存的选择，避免已关闭的用户在启动时发出预览请求。
internal val LocalLinkPreviewEnabled = compositionLocalOf { false }

private data class PreviewTarget(
    val url: HttpUrl,
    val lookup: BilibiliPreviewLookup?,
    val isBilibiliShortLink: Boolean,
    val end: Int,
    val link: LinkAnnotation,
) {
    val requestKey: HttpUrl get() = lookup?.url ?: url
}

@Composable
internal fun PublicWebLinkText(
    text: AnnotatedString,
    enabled: Boolean = true,
    resolveUrl: (LinkAnnotation.Clickable) -> String? = { null },
    content: @Composable (Modifier, (TextLayoutResult) -> Unit) -> Unit,
) {
    if (!enabled || !LocalLinkPreviewEnabled.current) {
        content(Modifier) {}
        return
    }
    val targets = remember(text, resolveUrl) {
        text.getLinkAnnotations(0, text.length).mapNotNull { annotation ->
            val link = annotation.item
            if (link.linkInteractionListener == null) return@mapNotNull null
            val url = when (link) {
                is LinkAnnotation.Url -> link.url
                is LinkAnnotation.Clickable -> resolveUrl(link)
                else -> null
            } ?: return@mapNotNull null
            val destination = BilibiliNavigationTargetParser.unwrapBrowserUrl(url) ?: url
            val page = publicLinkPreviewUrl(destination) ?: return@mapNotNull null
            val lookup = BilibiliPreviewLookup.forPage(page)
            val isShortLink = isPublicBilibiliShortLink(page)
            if (lookup == null && !isShortLink && !isPublicBilibiliToyPage(page) && !shouldAttemptInstantArticle(page.toString())) {
                return@mapNotNull null
            }
            PreviewTarget(page, lookup, isShortLink, annotation.end, link)
        }
    }
    if (targets.isEmpty()) {
        content(Modifier) {}
        return
    }

    var visibleTextEnd by remember(text.text) { mutableStateOf(0) }
    var textVisible by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val pageAllowsLoading = LocalPageImageLoadingAllowed.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    Column(modifier = Modifier.fillMaxWidth()) {
        content(Modifier.onVisibilityChanged(minDurationMs = 250L, minFractionVisible = 0f) { textVisible = it }) { layout ->
            visibleTextEnd = when {
                layout.lineCount == 0 -> 0
                layout.hasVisualOverflow -> layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                else -> text.length
            }
        }
        val visibleTargets = targets.asSequence().filter { it.end <= visibleTextEnd }
            .distinctBy { it.requestKey }.take(2)
        for (target in visibleTargets) {
            key(target.requestKey) {
                var preview by remember { mutableStateOf(publicLinkPreviewCache.peek(target.requestKey)) }
                val canLoad = pageAllowsLoading && textVisible
                LaunchedEffect(canLoad) {
                    if (canLoad && preview == null) {
                        preview = when {
                            target.lookup != null -> fetchBilibiliLinkPreview(target.lookup)
                            target.isBilibiliShortLink -> fetchBilibiliShortLinkPreview(target.url)
                            else -> fetchPublicLinkPreview(target.url)
                        }
                    }
                }
                preview?.let {
                    PublicWebLinkPreviewCard(
                        preview = it,
                        host = target.url.host,
                        pageAllowsLoading = pageAllowsLoading,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(
                                start = AppSpacingTokens.Small,
                                top = AppSpacingTokens.ExtraSmall,
                                end = AppSpacingTokens.Small,
                            )
                            .widthIn(max = 440.dp)
                            .fillMaxWidth(),
                        onClick = { target.link.linkInteractionListener?.onClick(target.link) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicWebLinkPreviewCard(
    preview: PublicLinkPreview,
    host: String,
    pageAllowsLoading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val linkColor = AppContentColors.link()
    var cardVisible by remember { mutableStateOf(false) }
    var imageFailed by remember(preview.imageUrl) { mutableStateOf(false) }
    AppSurface(
        onClick = onClick,
        modifier = modifier.onVisibilityChanged(minFractionVisible = 0f) { cardVisible = it },
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.surfaceContainer(),
    ) {
        Column(
            modifier = Modifier.drawWithContent {
                drawContent()
                val railWidth = 3.dp.toPx()
                val railStart = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - railWidth
                drawRect(
                    color = linkColor,
                    topLeft = Offset(railStart, 0f),
                    size = Size(railWidth, size.height),
                )
            },
        ) {
            Column(
                modifier = Modifier.padding(
                    start = AppSpacingTokens.Medium,
                    top = AppSpacingTokens.Small,
                    end = AppSpacingTokens.Small,
                    bottom = AppSpacingTokens.Small,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = rememberAppLinkIcon(),
                            contentDescription = null,
                            tint = linkColor,
                            modifier = Modifier.size(14.dp),
                        )
                        AppText(
                            text = stringResource(R.string.link_preview_card_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = linkColor,
                            tapToCopyEnabled = false,
                        )
                    }
                    AppText(
                        text = host,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        tapToCopyEnabled = false,
                    )
                }
                AppText(
                    text = preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    tapToCopyEnabled = false,
                )
                if (preview.description.isNotBlank()) {
                    AppText(
                        text = preview.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        tapToCopyEnabled = false,
                    )
                }
            }
            preview.imageUrl?.takeUnless { imageFailed }?.let { image ->
                PageAwareAsyncImage(
                    model = image.toString(),
                    contentDescription = null,
                    imageLoader = PublicContentImageLoader.forLinkPreview(LocalContext.current),
                    contentScale = ContentScale.Crop,
                    loadingAllowed = pageAllowsLoading && cardVisible,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
        }
    }
}
