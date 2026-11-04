// 文件路径: feature/dynamic/components/ForwardedContent.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.rememberAppWarningIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DrawMajor
import com.android.purebilibili.data.model.response.OpusMajor

internal data class ForwardedImagePreviewState(
    val images: List<String>,
    val initialIndex: Int
)

internal fun resolveForwardedDrawPreviewState(
    draw: DrawMajor,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    return resolveForwardedImagePreviewState(
        images = draw.items.map { it.src },
        clickedIndex = clickedIndex
    )
}

internal fun resolveForwardedOpusPreviewState(
    opus: OpusMajor,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    return resolveForwardedImagePreviewState(
        images = opus.pics.map { it.url },
        clickedIndex = clickedIndex
    )
}

private fun resolveForwardedImagePreviewState(
    images: List<String>,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    if (clickedIndex !in images.indices) return null
    if (images.isEmpty()) return null
    return ForwardedImagePreviewState(
        images = images,
        initialIndex = clickedIndex
    )
}

/**
 *  转发的原始内容
 */
@Composable
fun ForwardedContent(
    orig: DynamicItem,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onDynamicDetailClick: ((String) -> Unit)? = null,
    onArticleClick: ((Long, String) -> Unit)? = null,
    onLiveClick: ((Long, String, String) -> Unit)? = null,
    onMusicClick: ((Long) -> Unit)? = null,
    gifImageLoader: ImageLoader,
    defaultPreviewTextVisible: Boolean = true,
    collapseWebLinks: Boolean = false,
    isDetail: Boolean = false,
) {
    val context = LocalContext.current
    val author = orig.modules.module_author
    val content = orig.modules.module_dynamic
    var previewState by remember { mutableStateOf<ForwardedImagePreviewState?>(null) }
    var previewSourceAnchor by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    val contentHasImages = content?.major?.draw?.items?.isNotEmpty() == true ||
        content?.major?.opus?.pics?.isNotEmpty() == true
    val visibleDynamicDesc = content?.desc?.let { desc ->
        resolveDynamicDescForImages(desc, hasImages = contentHasImages)
    }
    val visibleOpusSummaryDesc = remember(content?.major?.opus?.summary, content?.major?.opus?.pics) {
        val opus = content?.major?.opus ?: return@remember null
        opus.summary?.let { summary ->
            resolveDynamicOpusSummaryDescForImages(
                text = summary.text,
                richTextNodes = summary.rich_text_nodes,
                hasImages = opus.pics.isNotEmpty()
            )
        }
    }
    val previewTextContent = remember(author?.name, visibleDynamicDesc?.text, visibleOpusSummaryDesc?.text) {
        val bodyText = visibleDynamicDesc?.text.takeUnless { it.isNullOrBlank() }
            ?: visibleOpusSummaryDesc?.text.orEmpty()
        ImagePreviewTextContent(
            headline = author?.name.orEmpty(),
            body = bodyText
        )
    }
    val origDynamicId = remember(orig.id_str) { orig.id_str.trim() }
    val openOrigDynamic = remember(origDynamicId, orig, onDynamicDetailClick) {
        {
            if (origDynamicId.isNotEmpty()) {
                com.android.purebilibili.data.repository.DynamicRepository.rememberDynamicDetailSeed(orig)
                onDynamicDetailClick?.invoke(origDynamicId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            // Tap the forward card (not @ / video / images) → open the original dynamic.
            .appActionClickable(
                enabled = onDynamicDetailClick != null && origDynamicId.isNotEmpty(),
                onClick = openOrigDynamic
            )
            .padding(horizontal = AppSpacingTokens.Large - AppSpacingTokens.Micro / 2, vertical = AppSpacingTokens.Small)
    ) {
        // 原作者
        if (author != null) {
            val authorTimeText = remember(author.pub_time, author.pub_ts) {
                resolveDynamicAuthorTimeText(
                    pubTime = author.pub_time,
                    pubTs = author.pub_ts
                )
            }
            val origAuthorClickMid = remember(orig) { resolveDynamicAuthorClickMid(orig) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    "@${author.name}",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.accent(), // 主题自适应颜色
                    modifier = Modifier.appActionClickable(enabled = origAuthorClickMid != null) {
                        origAuthorClickMid?.let(onUserClick)
                    }
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    authorTimeText,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = AppContentColors.secondaryText().copy(0.5f)
                )
            }
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        }

        content?.topic?.takeIf { it.name.isNotBlank() }?.let { topic ->
            DynamicTopicLabel(
                topicName = topic.name,
                onClick = {
                    val kw = topic.name.trim().removePrefix("#").removeSuffix("#").trim()
                    if (topic.id > 0L) {
                        onTopicClick(topic.id)
                    } else if (onTopicKeywordClick != null && kw.isNotEmpty()) {
                        onTopicKeywordClick(kw)
                    } else if (kw.isNotEmpty()) {
                        val searchUrl = "bilibili://search?keyword=" + java.net.URLEncoder.encode(kw, java.nio.charset.StandardCharsets.UTF_8.name())
                        val inAppIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(searchUrl)
                        ).setPackage(context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(inAppIntent) }
                    }
                },
                modifier = Modifier.padding(bottom = AppSpacingTokens.Micro),
            )
        }

        // 原动态标题 (Opus / 专栏，位于正文上方，对齐 PiliPlus)
        val forwardedTitle = remember(content?.major?.opus?.title, content?.major?.article?.title) {
            resolveDynamicHeadlineTitle(
                opus = content?.major?.opus,
                article = content?.major?.article
            )
        }
        if (forwardedTitle != null) {
            AppText(
                text = forwardedTitle,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.Bold,
                color = AppContentColors.primaryText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = AppSpacingTokens.ExtraSmall)
            )
        }

        // 原动态失效/删除占位提示（对齐 PiliPlus）
        val isOrigNoneMajor = content?.major?.type == "MAJOR_TYPE_NONE" || orig.type == "DYNAMIC_TYPE_NONE"
        if (isOrigNoneMajor) {
            val tips = content?.major?.none?.tips?.trim()?.takeIf { it.isNotEmpty() } ?: "源动态已被作者删除或已失效"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacingTokens.ExtraSmall)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    rememberAppWarningIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AppContentColors.secondaryText()
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    text = tips,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = AppContentColors.secondaryText()
                )
            }
        }

        // 原文字内容 - 使用 RichTextContent 支持表情；点空白文字打开原动态
        val preferredDesc = resolvePreferredDynamicDesc(
            primary = visibleDynamicDesc,
            fallback = visibleOpusSummaryDesc
        )
        val forwardedEmoteMap = remember(content?.desc, content?.major?.opus?.summary, preferredDesc) {
            buildMap {
                putAll(collectDynamicEmojiUrlMap(content?.desc?.rich_text_nodes.orEmpty()))
                putAll(collectDynamicEmojiUrlMap(content?.major?.opus?.summary?.rich_text_nodes.orEmpty()))
                putAll(collectDynamicEmojiUrlMap(preferredDesc?.rich_text_nodes.orEmpty()))
            }
        }
        preferredDesc?.let { desc ->
            if (shouldRenderDynamicRichText(desc)) {
                var bodyTextOverflow by remember(origDynamicId, desc, isDetail) { mutableStateOf(false) }
                RichTextContent(
                    collapseWebLinks = collapseWebLinks,
                    desc = desc,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onTopicKeywordClick = onTopicKeywordClick,
                    onBlankTap = openOrigDynamic.takeIf {
                        onDynamicDetailClick != null && origDynamicId.isNotEmpty()
                    },
                    onVideoClick = onVideoClick,
                    onDynamicDetailClick = onDynamicDetailClick,
                    onBangumiClick = onBangumiClick,
                    onArticleClick = onArticleClick,
                    onLiveClick = onLiveClick,
                    onMusicClick = onMusicClick,
                    extraEmoteUrlMap = forwardedEmoteMap,
                    maxLines = if (isDetail) Int.MAX_VALUE else DYNAMIC_FEED_TEXT_MAX_LINES,
                    overflow = if (isDetail) TextOverflow.Clip else TextOverflow.Ellipsis,
                    onTextLayout = { bodyTextOverflow = it.hasVisualOverflow },
                )
                if (!isDetail && bodyTextOverflow && onDynamicDetailClick != null && origDynamicId.isNotEmpty()) {
                    AppText(
                        text = "展开更多",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.appActionClickable(onClick = openOrigDynamic),
                    )
                }
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            }
        }

        // 原视频
        content?.major?.archive?.let { archive ->
            val playableBvid = resolveArchivePlayableBvid(archive)
            VideoCardLarge(
                archive = archive,
                publishTs = author?.pub_ts ?: 0L,
                cornerBadgeText = resolveDynamicArchiveBadgeLabel(archive),
                onClick = { playableBvid?.let(onVideoClick) }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        }

        content?.major?.pgc?.let { pgc ->
            val bangumiTarget = resolveArchiveBangumiTarget(pgc)
            VideoCardLarge(
                archive = pgc,
                publishTs = author?.pub_ts ?: 0L,
                cornerBadgeText = "番剧",
                onClick = { bangumiTarget?.let { onBangumiClick(it.seasonId, it.epId) } }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        }

        // 原图片（与主卡一致：列表预览最多 9 张，避免拼大图被裁成 2×2）
        content?.major?.draw?.let { draw ->
            DrawGridV2(
                items = draw.items,
                gifImageLoader = gifImageLoader,
                maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail = false),
                onImagePreviewClick = { index, anchor ->
                    val state = resolveForwardedDrawPreviewState(draw, index) ?: return@DrawGridV2
                    previewState = state
                    previewSourceAnchor = anchor
                }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        }

        //  [新增] 原 Opus 图文动态（正文已在上方 preferredDesc 渲染，这里只补图）
        content?.major?.opus?.let { opus ->
            // 显示图片
            if (opus.pics.isNotEmpty()) {
                val drawItems = opus.pics.map { pic ->
                    com.android.purebilibili.data.model.response.DrawItem(
                        src = pic.url,
                        width = pic.width,
                        height = pic.height,
                        live_url = pic.live_url
                    )
                }
                DrawGridV2(
                    items = drawItems,
                    gifImageLoader = gifImageLoader,
                    maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail = false),
                    onImagePreviewClick = { index, anchor ->
                        val state = resolveForwardedOpusPreviewState(opus, index) ?: return@DrawGridV2
                        previewState = state
                        previewSourceAnchor = anchor
                    }
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            }
        }

        content?.major?.ugc_season?.let { season ->
            val seasonArchive = resolveUgcSeasonArchiveFallback(season)
            val playableBvid = resolveUgcSeasonPlayableBvid(season)
            if (seasonArchive != null) {
                VideoCardLarge(
                    archive = seasonArchive,
                    publishTs = author?.pub_ts ?: 0L,
                    isCollection = true,
                    collectionTitle = season.title,
                    onClick = { playableBvid?.let(onVideoClick) }
                )
            }
        }
    }

    previewState?.let { state ->
        ImagePreviewDialog(
            livePhotoVideos = buildMap {
                content?.major?.opus?.pics.orEmpty().forEach { pic ->
                    normalizeLivePhotoVideoUrl(pic.live_url)?.let { liveUrl ->
                        put(pic.url, liveUrl)
                        put(normalizeImageUrl(pic.url), liveUrl)
                    }
                }
                content?.major?.draw?.items.orEmpty().forEach { pic ->
                    normalizeLivePhotoVideoUrl(pic.live_url)?.let { liveUrl ->
                        put(pic.src, liveUrl)
                        put(normalizeImageUrl(pic.src), liveUrl)
                    }
                }
            },
            images = state.images,
            initialIndex = state.initialIndex,
            sourceRect = previewSourceAnchor?.rect,
            sourceRects = previewSourceAnchor?.galleryRects.orEmpty(),
            sourceCornerRadiusDp = previewSourceAnchor?.cornerRadiusDp
                ?: resolveDrawGridCornerRadiusDp().toFloat(),
            textContent = previewTextContent,
            defaultTextVisible = defaultPreviewTextVisible,
            onDismiss = {
                previewState = null
                previewSourceAnchor = null
            }
        )
    }
}
