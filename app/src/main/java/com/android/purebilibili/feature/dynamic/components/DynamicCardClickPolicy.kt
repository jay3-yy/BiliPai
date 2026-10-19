package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout
import com.android.purebilibili.core.util.BilibiliUrlParser
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.ArticleMajor
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.LiveRcmdMajor
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.OpusLinkCard
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.OpusPic
import com.android.purebilibili.data.model.response.UgcSeasonMajor
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.feature.dynamic.model.LiveContentInfo
import kotlinx.serialization.json.Json

internal sealed interface DynamicCardPrimaryAction {
    data class OpenVideo(val bvid: String) : DynamicCardPrimaryAction
    data class OpenBangumi(val seasonId: Long, val epId: Long) : DynamicCardPrimaryAction
    data class OpenArticle(val articleId: Long, val title: String) : DynamicCardPrimaryAction
    data class OpenDynamicDetail(val dynamicId: String) : DynamicCardPrimaryAction
    data class OpenLive(val roomId: Long, val title: String, val uname: String) : DynamicCardPrimaryAction
    data class OpenUser(val mid: Long) : DynamicCardPrimaryAction
    data object None : DynamicCardPrimaryAction
}

internal sealed interface DynamicCardMediaAction {
    data class PreviewImages(
        val images: List<String>,
        val initialIndex: Int
    ) : DynamicCardMediaAction

    data class OpenDynamicDetail(val dynamicId: String) : DynamicCardMediaAction

    data object None : DynamicCardMediaAction
}

internal sealed interface DynamicOpusLinkCardAction {
    data class OpenVideo(val videoId: String) : DynamicOpusLinkCardAction
    data class OpenDynamicDetail(val dynamicId: String) : DynamicOpusLinkCardAction
    data class OpenArticle(val articleId: Long, val title: String) : DynamicOpusLinkCardAction
    data class OpenLive(val roomId: Long) : DynamicOpusLinkCardAction
    data class OpenUser(val mid: Long) : DynamicOpusLinkCardAction
    data class OpenBangumi(val seasonId: Long, val epId: Long) : DynamicOpusLinkCardAction
    data class OpenExternalUrl(val url: String) : DynamicOpusLinkCardAction
    data object None : DynamicOpusLinkCardAction
}

internal fun resolveBvidFromRawVideoTarget(rawValue: String?): String? {
    val target = rawValue?.trim().orEmpty()
    if (target.isEmpty()) return null
    val parsed = BilibiliUrlParser.parse(target)
    val bvid = parsed.bvid?.trim()
    if (!bvid.isNullOrEmpty()) return bvid
    return parsed.aid?.takeIf { it > 0 }?.let { "av$it" }
}

internal fun resolveArchivePlayableBvid(archive: ArchiveMajor): String? {
    return resolveBvidFromRawVideoTarget(archive.bvid)
        ?: resolveBvidFromRawVideoTarget(archive.jump_url)
        ?: archive.aid.trim().toLongOrNull()?.takeIf { it > 0L }?.let { "av$it" }
}

internal fun resolveArchiveBangumiTarget(archive: ArchiveMajor): DynamicCardPrimaryAction.OpenBangumi? {
    var seasonId = archive.season_id.takeIf { it > 0L } ?: 0L
    var epId = archive.epid.takeIf { it > 0L } ?: 0L

    when (val target = BilibiliNavigationTargetParser.parse(archive.jump_url)) {
        is BilibiliNavigationTarget.BangumiSeason -> {
            if (seasonId <= 0L) seasonId = target.seasonId
        }
        is BilibiliNavigationTarget.BangumiEpisode -> {
            if (epId <= 0L) epId = target.epId
        }
        else -> Unit
    }

    return if (seasonId > 0L || epId > 0L) {
        DynamicCardPrimaryAction.OpenBangumi(
            seasonId = seasonId,
            epId = epId
        )
    } else {
        null
    }
}

internal fun resolveUgcSeasonPlayableBvid(season: UgcSeasonMajor): String? {
    return season.archive?.let(::resolveArchivePlayableBvid)
        ?: resolveBvidFromRawVideoTarget(season.jump_url)
        ?: season.aid.takeIf { it > 0L }?.let { "av$it" }
}

internal fun resolveDynamicWatchLaterAid(item: DynamicItem): Long? {
    val target = item.orig ?: item
    val major = target.modules.module_dynamic?.major ?: return null
    major.archive?.aid
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { return it }
    major.ugc_season?.archive?.aid
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { return it }
    return major.ugc_season?.aid?.takeIf { it > 0L }
}

internal fun resolveUgcSeasonArchiveFallback(season: UgcSeasonMajor): ArchiveMajor? {
    season.archive?.let { return it }
    val hasRenderableContent = season.title.isNotBlank() || season.cover.isNotBlank()
    if (!hasRenderableContent) return null
    val bvid = resolveUgcSeasonPlayableBvid(season).orEmpty()
    return ArchiveMajor(
        aid = season.aid.takeIf { it > 0 }?.toString().orEmpty(),
        bvid = bvid,
        title = season.title,
        cover = season.cover,
        desc = season.desc.ifBlank { season.intro },
        duration_text = season.duration_text,
        stat = com.android.purebilibili.data.model.response.ArchiveStat(
            play = season.stat.play,
            danmaku = season.stat.danmaku
        ),
        jump_url = season.jump_url
    )
}

internal fun resolveArticleCoverUrls(article: ArticleMajor): List<String> {
    return article.covers
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

internal fun resolveArticleCoverDrawItems(article: ArticleMajor): List<DrawItem> {
    return resolveArticleCoverUrls(article).map { cover ->
        DrawItem(src = cover)
    }
}

internal fun resolveRenderableDrawItems(items: List<DrawItem>): List<DrawItem> =
    items.mapNotNull { item ->
        val source = item.src.trim()
        source.takeIf(String::isNotEmpty)?.let { item.copy(src = it) }
    }.distinctBy { normalizeDynamicImageIdentity(it.src) }

internal fun resolveRenderableOpusPics(pics: List<OpusPic>): List<OpusPic> =
    pics.mapNotNull { pic ->
        val url = pic.url.trim()
        url.takeIf(String::isNotEmpty)?.let { pic.copy(url = it) }
    }.distinctBy { normalizeDynamicImageIdentity(it.url) }

internal fun resolveDynamicOpusPreviewPics(
    opus: OpusMajor,
    presentationBlocks: List<OpusContentBlock>,
): List<OpusPic> {
    val bodyPics = presentationBlocks.mapNotNull { block ->
        when (block) {
            is OpusContentBlock.Image -> block.pic
            is OpusContentBlock.Divider -> block.pic
            else -> null
        }
    }
    return if (shouldRenderDynamicOpusBlocksAsFullBody(opus, presentationBlocks) && bodyPics.isNotEmpty()) {
        resolveRenderableOpusPics(bodyPics)
    } else {
        resolveRenderableOpusPics(opus.pics)
    }
}

internal fun shouldRenderDynamicDrawGrid(
    hasFullOpusImageContent: Boolean,
    opusPics: List<OpusPic>,
): Boolean = !hasFullOpusImageContent && resolveRenderableOpusPics(opusPics).isEmpty()

private fun normalizeDynamicImageIdentity(rawUrl: String): String = when {
    rawUrl.startsWith("http://", ignoreCase = true) -> "https://${rawUrl.substringAfter("://")}"
    rawUrl.startsWith("//") -> "https:$rawUrl"
    else -> rawUrl
}

internal fun resolveDynamicOpusPresentationBlocks(
    opus: OpusMajor,
    isDetail: Boolean
): List<OpusContentBlock> {
    if (!isDetail) return emptyList()
    val renderedImageIds = mutableSetOf<String>()
    fun keepImage(pic: OpusPic): OpusPic? {
        val url = pic.url.trim()
        if (url.isEmpty()) return null
        if (!renderedImageIds.add(normalizeDynamicImageIdentity(url))) return null
        return pic.copy(url = url)
    }
    return buildList {
        opus.contentBlocks.forEach { block ->
            when (block) {
                is OpusContentBlock.Text -> {
                    // 空白段落不参与布局，避免在文末/图前多出一行高度。
                    if (normalizeDynamicBodyText(block.text).isNotBlank() ||
                        block.richTextNodes.any { resolveDynamicRichTextNodeToken(it).isNotBlank() }
                    ) {
                        add(block.copy(text = normalizeDynamicBodyText(block.text)))
                    }
                }
                is OpusContentBlock.Image -> keepImage(block.pic)?.let { add(block.copy(pic = it)) }
                is OpusContentBlock.Divider -> {
                    val dividerPic = block.pic
                    if (dividerPic == null) {
                        add(block)
                    } else {
                        add(block.copy(pic = keepImage(dividerPic)))
                    }
                }
                else -> add(block)
            }
        }
    }
}

internal fun shouldRenderDynamicOpusBlocksAsFullBody(
    opus: OpusMajor,
    presentationBlocks: List<OpusContentBlock>,
): Boolean {
    return presentationBlocks.isNotEmpty() &&
        (presentationBlocks.any {
            it is OpusContentBlock.Image || (it is OpusContentBlock.Divider && it.pic != null)
        } || opus.pics.isEmpty())
}

internal fun resolveDynamicOpusPreviewImageLimit(isDetail: Boolean): Int? {
    return if (isDetail) null else DYNAMIC_FEED_PREVIEW_MAX_IMAGES
}

internal fun shouldExpandDynamicOpusDetailImages(
    imageLayout: DynamicDetailImageLayout,
): Boolean {
    return imageLayout == DynamicDetailImageLayout.EXPANDED
}

/**
 * 仅有 opus.pics、尚未解析出正文块时（seed / 部分详情），详情页仍按图片布局设置展开，
 * 避免先画九宫格再在完整详情到达后整块跳成大图。
 */
internal fun shouldExpandDynamicOpusFallbackImages(
    isDetail: Boolean,
    imageLayout: DynamicDetailImageLayout,
): Boolean {
    return isDetail && shouldExpandDynamicOpusDetailImages(imageLayout)
}

internal fun toggleDynamicDetailImageLayout(
    current: DynamicDetailImageLayout,
): DynamicDetailImageLayout {
    return when (current) {
        DynamicDetailImageLayout.EXPANDED -> DynamicDetailImageLayout.THUMBNAIL
        DynamicDetailImageLayout.THUMBNAIL -> DynamicDetailImageLayout.EXPANDED
    }
}

/** 详情页缩略图模式：把 Opus 正文里的图片/带图分割线收成九宫格素材。 */
internal fun resolveOpusThumbnailDrawItems(
    blocks: List<OpusContentBlock>,
): List<DrawItem> {
    return buildList {
        blocks.forEach { block ->
            when (block) {
                is OpusContentBlock.Image -> add(block.pic.toDrawItem())
                is OpusContentBlock.Divider -> block.pic?.let { add(it.toDrawItem()) }
                else -> Unit
            }
        }
    }
}

internal fun isOpusImageContentBlock(block: OpusContentBlock): Boolean {
    return block is OpusContentBlock.Image ||
        (block is OpusContentBlock.Divider && block.pic != null)
}

/**
 * 缩略图网格插在第一张图块位置，使图后的 LinkCard/横幅仍落在网格下面。
 */
internal fun shouldEmitOpusThumbnailGridAtBlock(
    block: OpusContentBlock,
    thumbnailGridEmitted: Boolean,
    hasThumbnailItems: Boolean,
    expandImages: Boolean,
): Boolean {
    if (expandImages || thumbnailGridEmitted || !hasThumbnailItems) return false
    return isOpusImageContentBlock(block)
}

private fun OpusPic.toDrawItem(): DrawItem {
    return DrawItem(
        src = url,
        width = width,
        height = height,
        live_url = live_url,
    )
}

internal fun shouldShowDynamicDetailImageLayoutToggle(
    item: DynamicItem,
): Boolean {
    val major = item.modules.module_dynamic?.major
    if (major?.draw?.items?.isNotEmpty() == true) return true
    if (major?.opus?.pics?.isNotEmpty() == true) return true
    val blocks = major?.opus?.contentBlocks.orEmpty()
    return blocks.any { block ->
        block is OpusContentBlock.Image ||
            (block is OpusContentBlock.Divider && block.pic != null)
    }
}

internal fun resolveDynamicOpusLinkCardAction(card: OpusLinkCard): DynamicOpusLinkCardAction {
    val url = card.jumpUrl.trim()
    if (url.isBlank()) return DynamicOpusLinkCardAction.None
    return when (val target = BilibiliNavigationTargetParser.parse(url)) {
        is BilibiliNavigationTarget.Video -> DynamicOpusLinkCardAction.OpenVideo(target.videoId)
        is BilibiliNavigationTarget.Dynamic -> DynamicOpusLinkCardAction.OpenDynamicDetail(target.dynamicId)
        is BilibiliNavigationTarget.Article -> DynamicOpusLinkCardAction.OpenArticle(
            articleId = target.articleId,
            title = card.title
        )
        is BilibiliNavigationTarget.Live -> DynamicOpusLinkCardAction.OpenLive(target.roomId)
        is BilibiliNavigationTarget.Space -> DynamicOpusLinkCardAction.OpenUser(target.mid)
        is BilibiliNavigationTarget.BangumiSeason -> DynamicOpusLinkCardAction.OpenBangumi(
            seasonId = target.seasonId,
            epId = 0L
        )
        is BilibiliNavigationTarget.BangumiEpisode -> DynamicOpusLinkCardAction.OpenBangumi(
            seasonId = 0L,
            epId = target.epId
        )
        is BilibiliNavigationTarget.Music,
        is BilibiliNavigationTarget.Search,
        is BilibiliNavigationTarget.PopularFeed,
        null -> DynamicOpusLinkCardAction.OpenExternalUrl(url)
    }
}

/**
 * 解析卡片头部作者点击跳转的目标 UID。
 * 对于合集/系列/番剧等非独立 UP 主发布的动态，避免将虚拟 ID / 赛季 ID 误当作个人 UID 打开个人空间导致“获取用户信息失败”报错。
 */
internal fun resolveDynamicAuthorClickMid(item: DynamicItem): Long? {
    // Resolve the author of this rendered header, not the forwarded content's author.
    val target = item
    val type = target.type.trim()
    val major = target.modules.module_dynamic?.major

    // 合集/剧集：检查是否有真实的 UP 主 mid
    if (type == "DYNAMIC_TYPE_UGC_SEASON" || major?.ugc_season != null) {
        val seasonMid = major?.ugc_season?.mid?.takeIf { it > 0L }
        return seasonMid
    }

    // 番剧/影视：不具备个人空间主页
    if (type in setOf("DYNAMIC_TYPE_PGC", "DYNAMIC_TYPE_PGC_UNION") || major?.pgc != null) {
        return null
    }

    // 普通用户动态
    return target.modules.module_author?.mid?.takeIf { it > 0L }
}

internal fun resolveDynamicCardPrimaryAction(item: DynamicItem): DynamicCardPrimaryAction {
    val target = item.orig ?: item
    val authorMid = resolveDynamicAuthorClickMid(target) ?: 0L
    val major = target.modules.module_dynamic?.major
    major?.pgc?.let(::resolveArchiveBangumiTarget)?.let { return it }
    val bvid = major?.archive?.let(::resolveArchivePlayableBvid)
        ?: major?.ugc_season?.let(::resolveUgcSeasonPlayableBvid)
    if (bvid != null) {
        return DynamicCardPrimaryAction.OpenVideo(bvid)
    }

    major?.article?.let { article ->
            // 专栏卡片统一打开专栏渲染器。新专栏的 jump_url 常是 /opus/ 链接，
            // 不能据此把专栏当图文动态打开详情页。
            val jumpTarget = BilibiliNavigationTargetParser.parse(article.jump_url)
            val articleId = (jumpTarget as? BilibiliNavigationTarget.Article)?.articleId
                ?: article.id
            if (articleId > 0L) {
                return DynamicCardPrimaryAction.OpenArticle(
                    articleId = articleId,
                    title = article.title.ifBlank { article.desc }
                )
            }
        }

    major?.live_rcmd?.let { live ->
        resolveLivePrimaryAction(
            liveRcmd = live,
            fallbackName = target.modules.module_author?.name.orEmpty()
        )?.let { return it }
    }

    major?.subscription_new?.live_rcmd?.let { live ->
        resolveLivePrimaryAction(
            liveRcmd = live,
            fallbackName = target.modules.module_author?.name.orEmpty()
        )?.let { return it }
    }

    val live = major?.live
    live?.id?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { roomId ->
        return DynamicCardPrimaryAction.OpenLive(
            roomId = roomId,
            title = live.title.ifBlank { "直播间" },
            uname = target.modules.module_author?.name.orEmpty()
        )
    }

    val dynamicId = target.id_str.trim().takeIf { it.isNotEmpty() }
    if (dynamicId != null) {
        return DynamicCardPrimaryAction.OpenDynamicDetail(dynamicId)
    }

    if (authorMid > 0) {
        return DynamicCardPrimaryAction.OpenUser(authorMid)
    }

    return DynamicCardPrimaryAction.None
}

internal fun resolveDynamicCardMediaAction(
    item: DynamicItem,
    clickedIndex: Int,
    isDetail: Boolean = true
): DynamicCardMediaAction {
    val target = item.orig ?: item
    val major = target.modules.module_dynamic?.major
    if (major == null) return DynamicCardMediaAction.None
    if (!isDetail && major.opus != null) {
        val dynamicId = target.id_str.trim()
        return if (dynamicId.isNotEmpty()) {
            DynamicCardMediaAction.OpenDynamicDetail(dynamicId)
        } else {
            DynamicCardMediaAction.None
        }
    }
    val opusImages = major.opus?.let { resolveRenderableOpusPics(it.pics) }.orEmpty()
    val drawImages = major.draw?.let { resolveRenderableDrawItems(it.items) }.orEmpty()
    val images = when {
        opusImages.isNotEmpty() -> opusImages.map { it.url }
        drawImages.isNotEmpty() -> drawImages.map { it.src }
        major.article != null -> resolveArticleCoverUrls(requireNotNull(major.article))
        else -> emptyList()
    }
    if (clickedIndex !in images.indices) return DynamicCardMediaAction.None
    return DynamicCardMediaAction.PreviewImages(
        images = images,
        initialIndex = clickedIndex
    )
}

internal fun dispatchDynamicCardPrimaryAction(
    action: DynamicCardPrimaryAction,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onArticleClick: ((Long, String) -> Unit)? = null,
    onDynamicDetailClick: ((String) -> Unit)?,
    onUserClick: (Long) -> Unit,
    onLiveClick: (Long, String, String) -> Unit
) {
    when (action) {
        is DynamicCardPrimaryAction.OpenVideo -> onVideoClick(action.bvid)
        is DynamicCardPrimaryAction.OpenBangumi -> onBangumiClick(action.seasonId, action.epId)
        is DynamicCardPrimaryAction.OpenArticle -> onArticleClick?.invoke(action.articleId, action.title)
        is DynamicCardPrimaryAction.OpenDynamicDetail -> onDynamicDetailClick?.invoke(action.dynamicId)
        is DynamicCardPrimaryAction.OpenLive -> onLiveClick(action.roomId, action.title, action.uname)
        is DynamicCardPrimaryAction.OpenUser -> onUserClick(action.mid)
        DynamicCardPrimaryAction.None -> Unit
    }
}

internal fun shouldEnableDynamicCardPrimaryClick(
    action: DynamicCardPrimaryAction,
    hasArticleClick: Boolean,
    hasDynamicDetailClick: Boolean,
    hasPrimaryClickOverride: Boolean
): Boolean {
    if (hasPrimaryClickOverride) return true
    return when (action) {
        is DynamicCardPrimaryAction.OpenArticle -> hasArticleClick
        is DynamicCardPrimaryAction.OpenDynamicDetail -> hasDynamicDetailClick
        DynamicCardPrimaryAction.None -> false
        else -> true
    }
}

internal fun dispatchDynamicCardPrimaryClick(
    item: DynamicItem,
    action: DynamicCardPrimaryAction,
    onPrimaryClickOverride: ((DynamicItem) -> Unit)?,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onArticleClick: ((Long, String) -> Unit)? = null,
    onDynamicDetailClick: ((String) -> Unit)?,
    onUserClick: (Long) -> Unit,
    onLiveClick: (Long, String, String) -> Unit
) {
    if (action is DynamicCardPrimaryAction.OpenDynamicDetail) {
        DynamicRepository.rememberDynamicDetailSeed(item)
    }
    if (onPrimaryClickOverride != null) {
        onPrimaryClickOverride(item)
        return
    }
    dispatchDynamicCardPrimaryAction(
        action = action,
        onVideoClick = onVideoClick,
        onBangumiClick = onBangumiClick,
        onArticleClick = onArticleClick,
        onDynamicDetailClick = onDynamicDetailClick,
        onUserClick = onUserClick,
        onLiveClick = onLiveClick
    )
}

private val dynamicLiveJson = Json { ignoreUnknownKeys = true }

private fun resolveLivePrimaryAction(
    liveRcmd: LiveRcmdMajor,
    fallbackName: String = ""
): DynamicCardPrimaryAction.OpenLive? {
    val payload = runCatching {
        dynamicLiveJson.decodeFromString<LiveContentInfo>(liveRcmd.content)
    }.getOrNull()

    val liveInfo = payload?.live_play_info ?: return null
    val roomId = liveInfo.room_id.takeIf { it > 0 } ?: return null
    val title = liveInfo.title.ifBlank { liveInfo.link }
    val uname = fallbackName.ifBlank { liveInfo.uid.takeIf { it > 0 }?.toString().orEmpty() }
    return DynamicCardPrimaryAction.OpenLive(
        roomId = roomId,
        title = title.ifBlank { "直播间" },
        uname = uname
    )
}

/**
 * Resolves headline title for Opus or Article dynamic items.
 * Guaranteed to be rendered at the very top of dynamic content (above body text and media).
 */
internal fun resolveDynamicHeadlineTitle(
    opus: OpusMajor?,
    article: ArticleMajor?
): String? {
    return opus?.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: article?.title?.trim()?.takeIf { it.isNotEmpty() }
}
