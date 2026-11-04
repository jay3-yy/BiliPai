package com.android.purebilibili.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.MemberAccountData
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicRichText
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.feature.list.resolveFavoriteFolderMediaId
import com.android.purebilibili.core.util.FormatUtils

enum class ProfileSpaceMainTab(val title: String) {
    HOME("主页"),
    DYNAMIC("动态"),
    CONTRIBUTION("投稿"),
    FAVORITE("收藏"),
    BANGUMI("番剧")
}

data class ProfileSpaceTabItem(
    val tab: ProfileSpaceMainTab,
    val title: String
)

data class ProfileSpaceTabChromeSpec(
    val rowContainerAlpha: Float,
    val controlContainerAlpha: Float,
    val selectedIndicatorAlpha: Float,
    val selectedTextAlpha: Float,
    val unselectedTextAlpha: Float,
    val rowHorizontalInsetDp: Int,
    val controlHorizontalInsetDp: Int,
    val rowVerticalInsetDp: Int
)

data class ProfileSpaceContentPanelSpec(
    val horizontalInsetDp: Int,
    val topOverlapDp: Int,
    val topPaddingDp: Int,
    val bottomPaddingDp: Int,
    val heroBottomInsetDp: Int
)

data class ProfileSpaceWallpaperChromePalette(
    val rowContainerColor: Color,
    val controlContainerColor: Color,
    val selectedTextColor: Color,
    val unselectedTextColor: Color,
    val indicatorColor: Color,
    val serviceContainerColor: Color,
    val serviceBorderColor: Color,
    val serviceTextColor: Color,
    val contentPanelColor: Color,
    val contentPanelBorderColor: Color,
    val sectionTextColor: Color,
    val cardContainerColor: Color
)

enum class ProfileSpaceHomeSection {
    FAVORITES,
    BANGUMI,
    COIN_VIDEOS,
    LIKE_VIDEOS,
    CONTRIBUTIONS,
    SERVICES
}

enum class ProfileContributionLoadState {
    IDLE,
    LOADING,
    LOADED,
    ERROR
}

enum class ProfileContributionContentState {
    CONTENT,
    LOADING,
    EMPTY,
    ERROR
}

data class ProfileSpaceUiState(
    val selectedTab: ProfileSpaceMainTab = ProfileSpaceMainTab.HOME,
    val isLoading: Boolean = false,
    val favoriteFolders: List<FavFolder> = emptyList(),
    val bangumiItems: List<FollowBangumiItem> = emptyList(),
    val contributionVideos: List<SpaceVideoItem> = emptyList(),
    val contributionLoadState: ProfileContributionLoadState = ProfileContributionLoadState.IDLE,
    val coinVideos: List<SpaceAggregateArchiveItem> = emptyList(),
    val likeVideos: List<SpaceAggregateArchiveItem> = emptyList(),
    val dynamicItems: List<SpaceDynamicItem> = emptyList(),
    val favoriteFolderCount: Int = 0,
    val bangumiCount: Int = 0,
    val contributionVideoCount: Int = 0,
    val coinVideoCount: Int = 0,
    val likeVideoCount: Int = 0,
    val message: String? = null,
    val signSaveMessage: String? = null,
    val isSavingSign: Boolean = false
)

data class ProfileFavoritePreviewCoverTarget(
    val mediaId: Long,
    val folderId: Long
)

data class ProfileEditableAccountState(
    val name: String = "",
    val birthday: String = "",
    val sex: String = "",
    val sign: String = "",
    val ipLocation: String = "",
    val canEditName: Boolean = false,
    val canEditBirthday: Boolean = false,
    val canEditSex: Boolean = false
) {
    val normalizedSign: String = sign.trim()
    val canSubmitSign: Boolean = validateProfileSign(sign) == null
}

data class ProfileSpaceIdentityMeta(
    val signText: String,
    val signPlaceholder: Boolean,
    val ipText: String?,
    val sexText: String?
)

fun defaultProfileSpaceTabs(): List<ProfileSpaceTabItem> {
    return ProfileSpaceMainTab.entries.map { tab ->
        ProfileSpaceTabItem(tab = tab, title = tab.title)
    }
}

fun resolveProfileSpaceTabChromeSpec(): ProfileSpaceTabChromeSpec {
    return ProfileSpaceTabChromeSpec(
        rowContainerAlpha = 0f,
        controlContainerAlpha = 0.24f,
        selectedIndicatorAlpha = 0.18f,
        selectedTextAlpha = 1f,
        unselectedTextAlpha = 0.72f,
        rowHorizontalInsetDp = 2,
        controlHorizontalInsetDp = 2,
        rowVerticalInsetDp = 0
    )
}

fun resolveProfileSpaceContentPanelSpec(): ProfileSpaceContentPanelSpec {
    return ProfileSpaceContentPanelSpec(
        horizontalInsetDp = 12,
        topOverlapDp = 28,
        topPaddingDp = 14,
        bottomPaddingDp = 24,
        heroBottomInsetDp = 40
    )
}

fun resolveProfileSpaceWallpaperChromePalette(
    wallpaperColor: Color,
    fallbackSurfaceColor: Color,
    fallbackContentColor: Color
): ProfileSpaceWallpaperChromePalette {
    val hasWallpaper = wallpaperColor.alpha > 0f
    val source = if (hasWallpaper) wallpaperColor else fallbackSurfaceColor
    val isDarkWallpaper = source.luminance() < 0.45f
    val glassBase = if (isDarkWallpaper) Color.Black else Color.White
    val readableText = if (isDarkWallpaper) Color.White else Color.Black
    val chromeSpec = resolveProfileSpaceTabChromeSpec()
    val serviceText = if (hasWallpaper) {
        readableText
    } else {
        fallbackContentColor
    }
    val panelAlpha = if (isDarkWallpaper) 0.34f else 0.30f
    val controlAlpha = if (isDarkWallpaper) 0.26f else 0.22f
    val cardAlpha = if (isDarkWallpaper) 0.22f else 0.18f
    return ProfileSpaceWallpaperChromePalette(
        rowContainerColor = Color.Transparent,
        controlContainerColor = if (hasWallpaper) {
            glassBase.copy(alpha = controlAlpha)
        } else {
            fallbackSurfaceColor.copy(alpha = chromeSpec.controlContainerAlpha)
        },
        selectedTextColor = serviceText.copy(alpha = chromeSpec.selectedTextAlpha),
        unselectedTextColor = serviceText.copy(alpha = chromeSpec.unselectedTextAlpha),
        indicatorColor = serviceText.copy(alpha = chromeSpec.selectedIndicatorAlpha),
        serviceContainerColor = if (hasWallpaper) Color.Transparent else fallbackSurfaceColor,
        serviceBorderColor = if (hasWallpaper) {
            readableText.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        serviceTextColor = serviceText,
        contentPanelColor = if (hasWallpaper) {
            glassBase.copy(alpha = panelAlpha)
        } else {
            fallbackSurfaceColor
        },
        contentPanelBorderColor = if (hasWallpaper) {
            readableText.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        sectionTextColor = serviceText,
        cardContainerColor = if (hasWallpaper) {
            glassBase.copy(alpha = cardAlpha)
        } else {
            fallbackSurfaceColor.copy(alpha = 0.72f)
        }
    )
}

private fun calculateProfileSpaceContrast(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun resolveProfileSpaceHomeSections(
    favoriteFolders: List<FavFolder>,
    bangumiItems: List<FollowBangumiItem>,
    coinVideos: List<SpaceAggregateArchiveItem>,
    likeVideos: List<SpaceAggregateArchiveItem>,
    contributionVideos: List<SpaceVideoItem>,
    includeDashboardOwnedSections: Boolean = true,
): List<ProfileSpaceHomeSection> {
    return buildList {
        if (
            includeDashboardOwnedSections &&
            favoriteFolders.any { it.id > 0L && it.title.isNotBlank() }
        ) {
            add(ProfileSpaceHomeSection.FAVORITES)
        }
        if (bangumiItems.any { it.seasonId > 0L && it.title.isNotBlank() }) add(ProfileSpaceHomeSection.BANGUMI)
        if (coinVideos.any { it.aid > 0L && it.title.isNotBlank() }) add(ProfileSpaceHomeSection.COIN_VIDEOS)
        if (likeVideos.any { it.aid > 0L && it.title.isNotBlank() }) add(ProfileSpaceHomeSection.LIKE_VIDEOS)
        if (contributionVideos.any { it.bvid.isNotBlank() || it.aid > 0L }) add(ProfileSpaceHomeSection.CONTRIBUTIONS)
        if (includeDashboardOwnedSections) add(ProfileSpaceHomeSection.SERVICES)
    }
}

fun resolveProfileContributionContentState(
    loadState: ProfileContributionLoadState,
    hasVideos: Boolean
): ProfileContributionContentState {
    if (hasVideos) return ProfileContributionContentState.CONTENT
    return when (loadState) {
        ProfileContributionLoadState.IDLE,
        ProfileContributionLoadState.LOADING -> ProfileContributionContentState.LOADING
        ProfileContributionLoadState.LOADED -> ProfileContributionContentState.EMPTY
        ProfileContributionLoadState.ERROR -> ProfileContributionContentState.ERROR
    }
}

fun validateProfileSign(sign: String): String? {
    return if (sign.length > 70) "签名最多支持 70 个字符" else null
}

fun resolveProfileSpaceIdentityMeta(
    sign: String,
    ipLocation: String?,
    sex: String
): ProfileSpaceIdentityMeta {
    val normalizedSign = sign.trim()
    val normalizedIp = ipLocation.orEmpty()
        .replace("IP属地：", "")
        .replace("IP 属地：", "")
        .trim()
    return ProfileSpaceIdentityMeta(
        signText = normalizedSign.ifBlank { "这个人很神秘，什么都没有写" },
        signPlaceholder = normalizedSign.isBlank(),
        ipText = if (normalizedIp.isBlank()) "IP 属地 · 保密" else "IP 属地 · $normalizedIp",
        sexText = sex.trim().takeIf { it.isNotBlank() && it != "保密" }
    )
}

fun resolveProfileEditableAccountState(
    account: MemberAccountData?,
    user: UserState,
    aggregateSign: String = "",
    ipLocation: String? = null
): ProfileEditableAccountState {
    return ProfileEditableAccountState(
        name = account?.uname?.ifBlank { user.name } ?: user.name,
        birthday = account?.birthday.orEmpty(),
        sex = account?.sex.orEmpty(),
        sign = account?.sign?.ifBlank { aggregateSign }.orEmpty(),
        ipLocation = ipLocation.orEmpty(),
        canEditName = false,
        canEditBirthday = false,
        canEditSex = false
    )
}

fun resolveProfileSpaceStateFromAggregate(
    aggregate: SpaceAggregateData?,
    favoriteFoldersFallback: List<FavFolder>,
    bangumiItems: List<FollowBangumiItem>,
    dynamicItems: List<SpaceDynamicItem>
): ProfileSpaceUiState {
    val aggregateFavoriteFolders = aggregate?.favourite2?.item.orEmpty().map(::mapProfileAggregateFavoriteFolder)
    val contributionVideos = aggregate?.archive?.item.orEmpty().map(::mapProfileAggregateVideoItem)
    return ProfileSpaceUiState(
        favoriteFolders = aggregateFavoriteFolders.ifEmpty { favoriteFoldersFallback },
        favoriteFolderCount = aggregate?.favourite2?.count ?: favoriteFoldersFallback.size,
        bangumiItems = bangumiItems,
        bangumiCount = bangumiItems.size,
        contributionVideos = contributionVideos,
        contributionVideoCount = aggregate?.archive?.count ?: contributionVideos.size,
        coinVideos = aggregate?.coinArchive?.item.orEmpty(),
        coinVideoCount = aggregate?.coinArchive?.count ?: aggregate?.coinArchive?.item.orEmpty().size,
        likeVideos = aggregate?.likeArchive?.item.orEmpty(),
        likeVideoCount = aggregate?.likeArchive?.count ?: aggregate?.likeArchive?.item.orEmpty().size,
        dynamicItems = dynamicItems.filter { it.visible }
    )
}

internal fun mergeProfileAggregateState(
    current: ProfileSpaceUiState,
    aggregate: SpaceAggregateData
): ProfileSpaceUiState {
    val aggregateFavoriteFolders = aggregate.favourite2?.item.orEmpty()
        .map(::mapProfileAggregateFavoriteFolder)
    val contributionVideos = aggregate.archive?.item.orEmpty().map(::mapProfileAggregateVideoItem)
    val shouldSeedContributions = current.contributionLoadState != ProfileContributionLoadState.LOADED &&
        current.contributionVideos.isEmpty()
    return current.copy(
        favoriteFolders = if (aggregate.favourite2 != null) {
            mergeProfileFavoriteFolders(current.favoriteFolders, aggregateFavoriteFolders)
        } else {
            current.favoriteFolders
        },
        favoriteFolderCount = aggregate.favourite2?.count ?: current.favoriteFolderCount,
        contributionVideos = if (aggregate.archive != null && shouldSeedContributions) {
            contributionVideos
        } else {
            current.contributionVideos
        },
        contributionVideoCount = if (aggregate.archive != null && shouldSeedContributions) {
            val checkedAggregateArchive = requireNotNull(aggregate.archive)
            checkedAggregateArchive.count
        } else {
            current.contributionVideoCount
        },
        coinVideos = aggregate.coinArchive?.item ?: current.coinVideos,
        coinVideoCount = aggregate.coinArchive?.count ?: current.coinVideoCount,
        likeVideos = aggregate.likeArchive?.item ?: current.likeVideos,
        likeVideoCount = aggregate.likeArchive?.count ?: current.likeVideoCount
    )
}

/**
 * 聚合接口常只给 count、不带 item（本人空间更常见）。
 * count 大于已有列表时，需要再走 arc/search 补齐投稿。
 */
internal fun shouldHydrateProfileContributionVideos(
    contributionVideoCount: Int,
    seededVideoCount: Int,
    pageSize: Int = PROFILE_CONTRIBUTION_PAGE_SIZE
): Boolean {
    if (contributionVideoCount <= 0) return false
    val expectedVisibleCount = minOf(contributionVideoCount, pageSize.coerceAtLeast(1))
    return seededVideoCount < expectedVisibleCount
}

internal fun mergeProfileContributionVideoState(
    current: ProfileSpaceUiState,
    videos: List<SpaceVideoItem>,
    totalCount: Int
): ProfileSpaceUiState {
    return current.copy(
        contributionVideos = videos,
        contributionVideoCount = maxOf(totalCount, videos.size),
        contributionLoadState = ProfileContributionLoadState.LOADED
    )
}

internal const val PROFILE_CONTRIBUTION_PAGE_SIZE = 30

internal fun mergeProfileFavoriteFolderState(
    current: ProfileSpaceUiState,
    folders: List<FavFolder>
): ProfileSpaceUiState {
    val merged = mergeProfileFavoriteFolders(current.favoriteFolders, folders)
    return current.copy(
        favoriteFolders = merged,
        favoriteFolderCount = maxOf(current.favoriteFolderCount, folders.size)
    )
}

internal fun resolveProfileFavoritePreviewCoverTargets(
    folders: List<FavFolder>,
    maxVisibleFolders: Int = 6
): List<ProfileFavoritePreviewCoverTarget> {
    return folders.asSequence()
        .take(maxVisibleFolders)
        .filter { folder -> folder.cover.isBlank() && folder.media_count > 0 }
        .mapNotNull { folder ->
            val mediaId = resolveFavoriteFolderMediaId(folder)
            mediaId.takeIf { it > 0L }?.let {
                ProfileFavoritePreviewCoverTarget(
                    mediaId = mediaId,
                    folderId = folder.id
                )
            }
        }
        .toList()
}

internal fun mergeProfileFavoritePreviewCovers(
    folders: List<FavFolder>,
    coversByMediaId: Map<Long, String>
): List<FavFolder> {
    if (folders.isEmpty() || coversByMediaId.isEmpty()) return folders
    return folders.map { folder ->
        if (folder.cover.isNotBlank()) {
            folder
        } else {
            val mediaId = resolveFavoriteFolderMediaId(folder)
            val cover = coversByMediaId[mediaId]?.trim().orEmpty()
            if (cover.isBlank()) folder else folder.copy(cover = cover)
        }
    }
}

internal fun mergeProfileBangumiState(
    current: ProfileSpaceUiState,
    items: List<FollowBangumiItem>
): ProfileSpaceUiState {
    return current.copy(
        bangumiItems = items,
        bangumiCount = items.size
    )
}

internal fun mergeProfileDynamicState(
    current: ProfileSpaceUiState,
    items: List<SpaceDynamicItem>
): ProfileSpaceUiState {
    return current.copy(dynamicItems = items.filter { it.visible })
}

internal fun shouldApplyProfileLoadResult(
    requestGeneration: Long,
    currentGeneration: Long,
    requestedMid: Long,
    currentMid: Long?
): Boolean {
    return requestGeneration == currentGeneration && requestedMid == currentMid
}

private fun mergeProfileFavoriteFolders(
    current: List<FavFolder>,
    incoming: List<FavFolder>
): List<FavFolder> {
    if (current.isEmpty()) return incoming
    if (incoming.isEmpty()) return current

    val mergedById = current.associateByTo(LinkedHashMap()) { it.id }
    incoming.forEach { folder ->
        val existing = mergedById[folder.id]
        mergedById[folder.id] = if (existing == null) {
            folder
        } else {
            folder.copy(
                fid = folder.fid.takeIf { it > 0L } ?: existing.fid,
                mid = folder.mid.takeIf { it > 0L } ?: existing.mid,
                title = folder.title.ifBlank { existing.title },
                cover = folder.cover.ifBlank { existing.cover },
                media_count = maxOf(folder.media_count, existing.media_count)
            )
        }
    }
    return mergedById.values.toList()
}

private fun mapProfileAggregateVideoItem(item: SpaceAggregateArchiveItem): SpaceVideoItem {
    return SpaceVideoItem(
        aid = item.aid,
        bvid = item.bvid,
        title = item.title,
        pic = item.cover,
        play = item.play,
        comment = item.reply,
        length = item.length,
        created = item.ctime,
        author = item.author,
        typename = item.tname
    )
}

private fun mapProfileAggregateFavoriteFolder(item: SpaceAggregateFavoriteItem): FavFolder {
    val resolvedId = item.mediaId.takeIf { it > 0L } ?: item.id.takeIf { it > 0L } ?: item.fid
    return FavFolder(
        id = resolvedId,
        fid = item.fid,
        mid = item.mid,
        title = item.title,
        cover = item.cover,
        media_count = item.media_count.takeIf { it > 0 } ?: item.count
    )
}

fun resolveProfileDynamicCover(item: SpaceDynamicItem): String {
    val major = item.modules.module_dynamic?.major
    return major?.archive?.cover
        ?.takeIf { it.isNotBlank() }
        ?: major?.draw?.items?.firstOrNull { it.src.isNotBlank() }?.src
        ?: major?.opus?.pics?.firstOrNull { it.src.isNotBlank() }?.src
        ?: major?.article?.covers?.firstOrNull { it.isNotBlank() }
        ?: ""
}

fun resolveProfileDynamicImageUrls(item: SpaceDynamicItem): List<String> {
    val major = item.modules.module_dynamic?.major
    return when {
        major?.draw?.items?.isNotEmpty() == true -> major?.draw?.items.orEmpty().mapNotNull { it.src.takeIf(String::isNotBlank) }
        major?.opus?.pics?.isNotEmpty() == true -> major?.opus?.pics.orEmpty().mapNotNull { it.src.takeIf(String::isNotBlank) }
        major?.article?.covers?.isNotEmpty() == true -> major?.article?.covers.orEmpty().mapNotNull { it.takeIf(String::isNotBlank) }
        else -> emptyList()
    }
}

fun resolveProfileDynamicText(item: SpaceDynamicItem): String {
    val dynamic = item.modules.module_dynamic
    val major = dynamic?.major
    return listOf(
        dynamic?.desc?.rich_text_nodes?.toProfileDynamicText(),
        dynamic?.desc?.text,
        major?.opus?.summary?.rich_text_nodes?.toProfileDynamicText(),
        major?.opus?.summary?.text,
        major?.article?.desc,
        major?.archive?.desc,
        major?.opus?.title,
        major?.article?.title,
        major?.archive?.title
    ).firstNotBlank()
}

fun resolveProfileDynamicAuthorName(item: SpaceDynamicItem): String {
    return item.modules.module_author?.name?.takeIf { it.isNotBlank() } ?: "动态"
}

fun resolveProfileDynamicPublishText(item: SpaceDynamicItem): String {
    return item.modules.module_author?.pub_time?.takeIf { it.isNotBlank() } ?: ""
}

fun resolveProfileDynamicActionText(label: String, count: Int): String {
    return if (count > 0) "$label ${formatProfileDynamicCount(count)}" else label
}

fun resolveProfileDynamicDeleteAction(item: SpaceDynamicItem): DynamicDeleteAction? {
    val deleteItem = item.modules.module_more
        ?.three_point_items
        ?.firstOrNull { it.type == "THREE_POINT_DELETE" }
        ?: return null
    val params = deleteItem.params
    val dynamicId = params?.dyn_id_str
        ?.takeIf { it.isNotBlank() }
        ?: item.id_str.takeIf { it.isNotBlank() }
        ?: return null

    return DynamicDeleteAction(
        dynamicId = dynamicId,
        dynType = params?.dyn_type?.takeIf { it > 0 },
        rid = params?.rid_str?.takeIf { it.isNotBlank() },
        label = deleteItem.label.takeIf { it.isNotBlank() } ?: "删除",
        title = deleteItem.modal?.title?.takeIf { it.isNotBlank() } ?: "要删除动态吗？",
        content = deleteItem.modal?.content?.takeIf { it.isNotBlank() }
            ?: "动态删除后将无法恢复，请谨慎操作",
        confirmText = deleteItem.modal?.confirm?.takeIf { it.isNotBlank() } ?: "删除",
        cancelText = deleteItem.modal?.cancel?.takeIf { it.isNotBlank() } ?: "取消"
    )
}

private fun List<SpaceDynamicRichText>.toProfileDynamicText(): String {
    return joinToString(separator = "") { node ->
        node.text
            .ifBlank { node.orig_text }
            .ifBlank { node.emoji?.text.orEmpty() }
    }.trim()
}

private fun List<String?>.firstNotBlank(): String {
    return firstOrNull { !it.isNullOrBlank() }?.orEmpty() ?: ""
}

private fun formatProfileDynamicCount(count: Int): String =
    FormatUtils.formatStat(count.toLong())
