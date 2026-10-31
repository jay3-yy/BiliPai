// 文件路径: feature/dynamic/components/DynamicCard.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.AppContentColors

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppContentCard
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.resolveUpNameColor

import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.transition.LocalDynamicImagePreviewTextVisible
import com.android.purebilibili.core.ui.transition.LocalDynamicCommentPreviewVisible

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import coil3.ImageLoader
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import kotlinx.coroutines.launch
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.hasCommentLocation

import com.android.purebilibili.core.ui.common.TextSelectionBottomSheet
import com.android.purebilibili.core.ui.common.TextSelectionPolicy
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import com.android.purebilibili.core.ui.common.detectTapWithSelectionFriendly
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppWarningIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOnIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppDeleteIcon
import com.android.purebilibili.core.ui.rememberAppLinkIcon
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.repository.SearchRepository
import com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.data.model.response.ReplyInteractionData
import com.android.purebilibili.feature.dynamic.resolveDynamicActionButtonSlotWeight
import com.android.purebilibili.feature.dynamic.resolveDynamicActionButtonSpacing
import com.android.purebilibili.feature.dynamic.resolveDynamicCardContentPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicCardOuterPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicLikeState
import com.android.purebilibili.data.model.response.DynamicStatModule
import com.android.purebilibili.data.model.response.DynamicType
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.OpusLinkCard
import com.android.purebilibili.data.model.response.resolveDynamicCardButtonText
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.dynamic.resolveDynamicDeleteAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Immutable
data class DynamicCardNavigationActions(
    val onVideoClick: (String) -> Unit,
    val onUserClick: (Long) -> Unit,
    val onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    val onTopicClick: (Long) -> Unit = {},
    val onTopicKeywordClick: ((String) -> Unit)? = null,
    val onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> },
    val onMusicClick: ((Long) -> Unit)? = null,
    val onCollectionClick: ((Long, Long, String, String) -> Unit)? = null,
    val onCourseClick: ((String, String) -> Unit)? = null,
    val onOpenLink: ((String) -> Unit)? = null,
    val onArticleClick: ((articleId: Long, title: String) -> Unit)? = null,
    val onMatchClick: ((contestId: Long) -> Unit)? = null,
    val onDynamicDetailClick: ((dynamicId: String) -> Unit)? = null,
    val onUnfoldRelatedClick: ((dynamicId: String) -> Unit)? = null,
    val onPrimaryClickOverride: ((DynamicItem) -> Unit)? = null,
)

@Immutable
data class DynamicCardInteractionActions(
    val onCommentClick: (dynamicId: String) -> Unit = {},
    /** 仅由已展示本条动态评论区的宿主提供；与卡片展开样式无关。 */
    val onShowCommentsInPlace: (() -> Unit)? = null,
    val onRepostClick: (dynamicId: String) -> Unit = {},
    val onLikeClick: (dynamicId: String) -> Unit = {},
    val onLikeClickWithState: ((dynamicId: String, isLiked: Boolean) -> Unit)? = null,
    val onWatchLaterClick: ((aid: Long) -> Unit)? = null,
    val onSaveDynamicClick: (() -> Unit)? = null,
    val onShareToMessageClick: (() -> Unit)? = null,
    val onCheckDynamicClick: (() -> Unit)? = null,
    val onReserveClick: ((DynamicReserveAction, (Result<DynamicReserveResult>) -> Unit) -> Unit)? = null,
    val onDeleteClick: ((DynamicDeleteAction) -> Unit)? = null,
    val onManageAction: (DynamicManageAction) -> Unit = {},
    val onLoadReplyInteractionStatus: ((oid: Long, type: Int, onLoaded: (ReplyInteractionData?) -> Unit) -> Unit)? = null,
)

@Immutable
data class DynamicCardActions(
    val navigation: DynamicCardNavigationActions,
    val interaction: DynamicCardInteractionActions = DynamicCardInteractionActions(),
)

@Immutable
data class DynamicCardPresentation(
    val isDetail: Boolean = false,
    val currentUserMid: Long? = TokenManager.midCache,
    val isLiked: Boolean = false,
    val likeOverride: Boolean? = null,
    val forwardCountDelta: Int = 0,
    val detailImageLayout: DynamicDetailImageLayout = DynamicDetailImageLayout.EXPANDED,
)

/** Feed 卡正文折叠参数：超过该长度折叠到 6 行，用「展开更多」进详情。 */
private const val DYNAMIC_FEED_TEXT_FOLD_THRESHOLD = 108
private const val DYNAMIC_FEED_TEXT_MAX_LINES = 6

/**
 * 动态卡片 V2。将导航、交互和展示状态分组，避免 Compose/R8 处理超大参数签名。
 */
@Composable
fun DynamicCardV2(
    item: DynamicItem,
    gifImageLoader: ImageLoader,
    actions: DynamicCardActions,
    presentation: DynamicCardPresentation = DynamicCardPresentation(),
) {
    val onVideoClick = actions.navigation.onVideoClick
    val onBangumiClick = actions.navigation.onBangumiClick
    val onUserClick = actions.navigation.onUserClick
    val onTopicClick = actions.navigation.onTopicClick
    val onTopicKeywordClick = actions.navigation.onTopicKeywordClick
    val onLiveClick = actions.navigation.onLiveClick
    val onMusicClick = actions.navigation.onMusicClick
    val onCollectionClick = actions.navigation.onCollectionClick
    val onOpenLink = actions.navigation.onOpenLink
    val onCourseClick = actions.navigation.onCourseClick
    val onArticleClick = actions.navigation.onArticleClick
    val onMatchClick = actions.navigation.onMatchClick
    val onDynamicDetailClick = actions.navigation.onDynamicDetailClick
    val onUnfoldRelatedClick = actions.navigation.onUnfoldRelatedClick
    val onPrimaryClickOverride = actions.navigation.onPrimaryClickOverride
    val onCommentClick = actions.interaction.onCommentClick
    val onShowCommentsInPlace = actions.interaction.onShowCommentsInPlace
    val onRepostClick = actions.interaction.onRepostClick
    val onLikeClick = actions.interaction.onLikeClick
    val onLikeClickWithState = actions.interaction.onLikeClickWithState
    val onWatchLaterClick = actions.interaction.onWatchLaterClick
    val onSaveDynamicClick = actions.interaction.onSaveDynamicClick
    val onShareToMessageClick = actions.interaction.onShareToMessageClick
    val onCheckDynamicClick = actions.interaction.onCheckDynamicClick
    val onReserveClick = actions.interaction.onReserveClick
    val onDeleteClick = actions.interaction.onDeleteClick
    val onManageAction = actions.interaction.onManageAction
    val onLoadReplyInteractionStatus = actions.interaction.onLoadReplyInteractionStatus
    val isDetail = presentation.isDetail
    val currentUserMid = presentation.currentUserMid
    val isLiked = presentation.isLiked
    val likeOverride = presentation.likeOverride
    val forwardCountDelta = presentation.forwardCountDelta
    val detailImageLayout = presentation.detailImageLayout

    if (!item.visible) return
    var shareImagePreview by remember(item.id_str) { mutableStateOf(false) }
    if (shareImagePreview) {
        com.android.purebilibili.feature.share.ShareImagePreviewSheet(
            document = com.android.purebilibili.feature.share.dynamicShareDocument(item),
            loadDocument = {
                val complete = DynamicRepository.getDynamicDetail(item.id_str).getOrThrow()
                com.android.purebilibili.feature.share.dynamicShareDocument(complete)
            },
            onDismiss = { shareImagePreview = false },
        )
    }
    val openDynamicDetail = remember(item, onDynamicDetailClick) {
        onDynamicDetailClick?.let { callback ->
            { id: String ->
                DynamicRepository.rememberDynamicDetailSeed(item)
                callback(id)
            }
        }
    }
    val author = item.modules.module_author
    val content = item.modules.module_dynamic
    val stat = item.modules.module_stat
    val effectiveIsLiked = resolveDynamicLikeState(
        localOverride = likeOverride,
        localLiked = isLiked,
        serverLiked = stat?.like?.status,
    )
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dynamicPreviewTextVisible = LocalDynamicImagePreviewTextVisible.current
    val authorTimeText = remember(author?.pub_time, author?.pub_ts) {
        author?.let {
            resolveDynamicAuthorTimeText(
                pubTime = it.pub_time,
                pubTs = it.pub_ts
            )
        }.orEmpty()
    }
    val opus = content?.major?.opus
    val fullOpusContentBlocks = opus?.let { currentOpus ->
        resolveDynamicOpusPresentationBlocks(opus = currentOpus, isDetail = isDetail)
    }.orEmpty()
    val renderableOpusPics = remember(opus, fullOpusContentBlocks) {
        opus?.let { currentOpus ->
            resolveDynamicOpusPreviewPics(currentOpus, fullOpusContentBlocks)
        }.orEmpty()
    }
    val contentHasImages = content?.major?.draw?.items?.let(::resolveRenderableDrawItems)?.isNotEmpty() == true ||
        renderableOpusPics.isNotEmpty()
    val visibleDynamicDesc = content?.desc?.let { desc ->
        resolveDynamicDescForImages(desc, hasImages = contentHasImages)
    }
    // A detail response can provide rich text blocks without embedding the
    // documented `opus.pics` entries in those blocks. In that case use the
    // image-grid path below so pictures are not hidden after the network
    // response replaces the cached feed item.
    val hasFullOpusDetailContent = opus?.let {
        shouldRenderDynamicOpusBlocksAsFullBody(
            opus = it,
            presentationBlocks = fullOpusContentBlocks,
        )
    } == true
    val type = DynamicType.fromApiValue(item.type)
    val cardClickAction = remember(item) { resolveDynamicCardPrimaryAction(item) }
    val watchLaterAid = remember(item) { resolveDynamicWatchLaterAid(item) }
    val deleteAction = remember(item) { resolveDynamicDeleteAction(item) }
    val menuCapabilities = remember(item, currentUserMid) {
        resolveDynamicMenuCapabilities(item, currentUserMid)
    }
    var pendingDeleteAction by remember(item.id_str) { mutableStateOf<DynamicDeleteAction?>(null) }
    var pendingBlockAuthor by remember(item.id_str) { mutableStateOf<DynamicManageAction.BlockAuthor?>(null) }
    var pendingVoteId by remember(item.id_str) { mutableStateOf<Long?>(null) }
    //  附加内容卡（相关游戏 / 投稿 / 商品 / 预约 / 投票 / 赛事 / 充电抽奖）统一映射，
    //  与详情页 opus 正文段落里的 link_card 走同一个模型和同一个组件，
    //  这样信息流与详情页的版式、高度、交互完全一致，进入详情不再闪烁。
    val resolvedLinkCard = remember(content?.additional) {
        resolveDynamicLinkCard(content?.additional)
    }
    //  详情返回里可能同时带 additional 和 opus 正文块里的同一张卡，
    //  按卡片身份去重，避免统一渲染后出现两张一样的卡片。
    val opusLinkCardIdentities = remember(fullOpusContentBlocks) {
        fullOpusContentBlocks
            .filterIsInstance<OpusContentBlock.LinkCard>()
            .map { block -> block.card.linkCardIdentity() }
            .toSet()
    }
    val additionalLinkCard = resolvedLinkCard
        ?.takeUnless { card -> card.linkCardIdentity() in opusLinkCardIdentities }
    //  附加内容卡的跳转分发：信息流与详情页共用同一套解析，
    //  避免同一个链接在列表里走外链、进详情却打开 App 内页面。
    val openContentUrl: (String) -> Unit = { raw ->
        val target = if (raw.startsWith("//")) "https:$raw" else raw
        if (onOpenLink != null && com.android.purebilibili.feature.web.shouldAttemptInstantArticle(target)) {
            onOpenLink(target)
        } else {
            openDynamicUrl(uriHandler, target)
        }
    }
    val openLinkCardTarget: (OpusLinkCard) -> Unit = { linkCard ->
        when (val action = resolveDynamicOpusLinkCardAction(linkCard)) {
            is DynamicOpusLinkCardAction.OpenVideo -> onVideoClick(action.videoId)
            is DynamicOpusLinkCardAction.OpenDynamicDetail -> openDynamicDetail?.invoke(action.dynamicId)
            is DynamicOpusLinkCardAction.OpenArticle -> onArticleClick?.invoke(action.articleId, action.title)
            is DynamicOpusLinkCardAction.OpenLive -> onLiveClick(
                action.roomId,
                linkCard.title.ifBlank { "直播间" },
                author?.name.orEmpty()
            )
            is DynamicOpusLinkCardAction.OpenUser -> onUserClick(action.mid)
            is DynamicOpusLinkCardAction.OpenBangumi -> onBangumiClick(action.seasonId, action.epId)
            is DynamicOpusLinkCardAction.OpenMatch -> {
                // 赛事卡优先走宿主注入的原生详情回调；动态宿主的 onOpenLink 也会把
                // match 链接解析到原生页面，两者都未接线时退回原外链行为。
                when {
                    onMatchClick != null && action.contestId > 0L -> onMatchClick(action.contestId)
                    onOpenLink != null -> onOpenLink(linkCard.jumpUrl)
                    else -> openContentUrl(linkCard.jumpUrl)
                }
            }
            is DynamicOpusLinkCardAction.OpenExternalUrl -> openContentUrl(action.url)
            DynamicOpusLinkCardAction.None -> Unit
        }
    }
    //  [新增] 评论互动设置弹窗状态
    var showReplyInteractionDialog by remember(item.id_str) { mutableStateOf<ReplyInteractionData?>(null) }
    var replyInteractionOid by remember(item.id_str) { mutableStateOf(0L) }
    var replyInteractionType by remember(item.id_str) { mutableStateOf(0) }
    val isCurrentlyTop = item.modules.module_tag?.text == "置顶"
    val isPrimaryClickEnabled = remember(cardClickAction, onArticleClick, openDynamicDetail, onPrimaryClickOverride) {
        shouldEnableDynamicCardPrimaryClick(
            action = cardClickAction,
            hasArticleClick = onArticleClick != null,
            hasDynamicDetailClick = openDynamicDetail != null,
            hasPrimaryClickOverride = onPrimaryClickOverride != null
        )
    }

    val openPrimaryArticle: () -> Unit = {
        dispatchDynamicCardPrimaryClick(
            item = item,
            action = cardClickAction,
            onPrimaryClickOverride = onPrimaryClickOverride,
            onVideoClick = onVideoClick,
            onBangumiClick = onBangumiClick,
            onArticleClick = onArticleClick,
            onDynamicDetailClick = openDynamicDetail,
            onUserClick = onUserClick,
            onLiveClick = onLiveClick,
        )
    }
    val articleContainer = if (!isDetail && onPrimaryClickOverride == null &&
        onArticleClick != null && cardClickAction is DynamicCardPrimaryAction.OpenArticle
    ) {
        com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(
            com.android.purebilibili.navigation3.BiliPaiNavKey.ArticleDetail(cardClickAction.articleId, cardClickAction.title),
            openPrimaryArticle,
        )
    } else null

    pendingVoteId?.let { voteId ->
        DynamicVoteDialog(
            voteId = voteId,
            dynamicId = item.id_str,
            onDismiss = { pendingVoteId = null }
        )
    }

    pendingDeleteAction?.let { action ->
        AppAlertDialog(
            onDismissRequest = { pendingDeleteAction = null },
            title = { AppText(action.title) },
            text = { AppText(action.content) },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        pendingDeleteAction = null
                        onDeleteClick?.invoke(action)
                    }
                ) {
                    AppText(action.confirmText, color = AppContentColors.error())
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { pendingDeleteAction = null }) {
                    AppText(action.cancelText)
                }
            }
        )
    }

    pendingBlockAuthor?.let { action ->
        AppAlertDialog(
            onDismissRequest = { pendingBlockAuthor = null },
            title = { AppText("屏蔽 UP 主") },
            text = { AppText("屏蔽后将不再推荐 ${action.authorName} 的内容，并同步到哔哩哔哩黑名单。") },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        pendingBlockAuthor = null
                        onManageAction(action)
                    },
                ) { AppText("屏蔽", color = AppContentColors.error()) }
            },
            dismissButton = {
                AppDialogAction(onClick = { pendingBlockAuthor = null }) { AppText("取消") }
            },
        )
    }

    //  [新增] 评论互动设置弹窗（评论精选 / 评论开关，对齐 BiliPai）
    showReplyInteractionDialog?.let { interactionData ->
        val selection = interactionData.up_reply_selection
        val reply = interactionData.up_reply
        val selectionEnabled = selection?.status == 1
        val replyEnabled = reply?.status == 1
        AppAlertDialog(
            onDismissRequest = { showReplyInteractionDialog = null },
            title = { AppText("评论互动设置") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .appActionClickable(enabled = selection?.can_modify == true) {
                                showReplyInteractionDialog = null
                                onManageAction(
                                    DynamicManageAction.SetReplySubject(
                                        oid = replyInteractionOid,
                                        replyType = replyInteractionType,
                                        action = resolveDynamicReplySelectionAction(selectionEnabled)
                                    )
                                )
                            }
                            .padding(vertical = AppSpacingTokens.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            rememberAppCommentIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Large),
                            tint = AppContentColors.primaryText()
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                        AppText(
                            if (selectionEnabled) "停止评论精选" else "开启评论精选",
                            color = if (selection?.can_modify == true) {
                                AppContentColors.primaryText()
                            } else {
                                AppContentColors.secondaryText().copy(0.5f)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .appActionClickable(enabled = reply?.can_modify == true) {
                                showReplyInteractionDialog = null
                                onManageAction(
                                    DynamicManageAction.SetReplySubject(
                                        oid = replyInteractionOid,
                                        replyType = replyInteractionType,
                                        action = resolveDynamicReplyOpenAction(replyEnabled)
                                    )
                                )
                            }
                            .padding(vertical = AppSpacingTokens.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            rememberAppVisibilityOffIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Large),
                            tint = AppContentColors.primaryText()
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                        AppText(
                            if (replyEnabled) "关闭评论" else "恢复评论",
                            color = if (reply?.can_modify == true) {
                                AppContentColors.primaryText()
                            } else {
                                AppContentColors.secondaryText().copy(0.5f)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppDialogAction(onClick = { showReplyInteractionDialog = null }) {
                    AppText("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = resolveDynamicCardOuterPadding())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = resolveDynamicCardContentPadding())
                .padding(
                    top = if (isDetail) AppSpacingTokens.Medium else AppSpacingTokens.ExtraSmall,
                    bottom = if (isDetail) AppSpacingTokens.None else AppSpacingTokens.ExtraSmall,
                )
                .then(articleContainer?.modifier ?: Modifier)
                .appActionClickable(enabled = isPrimaryClickEnabled) {
                    if (articleContainer != null) articleContainer.onClick() else openPrimaryArticle()
                }
        ) {
        val context = LocalContext.current

        //  用户头部（头像 + 名称 + 时间 + 更多）
        if (author != null) {
            val authorClickMid = remember(item) { resolveDynamicAuthorClickMid(item) }
            val ugcSeason = item.modules.module_dynamic?.major?.ugc_season
            val onAuthorHeaderClick = {
                if (authorClickMid != null) {
                    dispatchDynamicCardPrimaryAction(
                        action = DynamicCardPrimaryAction.OpenUser(authorClickMid),
                        onVideoClick = onVideoClick,
                        onBangumiClick = onBangumiClick,
                        onArticleClick = onArticleClick,
                        onDynamicDetailClick = openDynamicDetail,
                        onUserClick = onUserClick,
                        onLiveClick = onLiveClick
                    )
                } else if (ugcSeason != null && ugcSeason.id > 0L && onCollectionClick != null) {
                    onCollectionClick(ugcSeason.id, ugcSeason.mid, ugcSeason.title, ugcSeason.jump_url)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                DynamicAuthorFace(
                    faceUrl = author.face,
                    officialType = author.official_verify?.type,
                    vipStatus = author.vip?.status,
                    faceSize = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small,
                    modifier = Modifier
                        .size(AppChromeSizeTokens.MinimumTouchTarget)
                        .semantics { contentDescription = "查看${author.name}的个人主页" }
                        .appActionClickable(enabled = authorClickMid != null || (ugcSeason != null && ugcSeason.id > 0L && onCollectionClick != null)) {
                            onAuthorHeaderClick()
                        },
                )

                Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))

                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        author.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        color = resolveUpNameColor(
                            vipStatus = author.vip?.status ?: 0,
                            vipType = author.vip?.type ?: 0,
                            onSurface = AppContentColors.primaryText(),
                            secondary = com.android.purebilibili.core.ui.components.userVipPink(),
                        ),
                        modifier = Modifier.appActionClickable(enabled = authorClickMid != null || (ugcSeason != null && ugcSeason.id > 0L && onCollectionClick != null)) {
                            onAuthorHeaderClick()
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            authorTimeText,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = AppContentColors.secondaryText().copy(0.6f)
                        )
                        if (author.pub_action.isNotBlank()) {
                            AppText(
                                " · ${author.pub_action}",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = AppContentColors.secondaryText().copy(0.6f)
                            )
                        }
                        // 粉丝装扮牌（作者行时间旁的小徽章）
                        author.decorate?.card_url?.takeIf { it.isNotBlank() }?.let { badgeUrl ->
                            PageAwareAsyncImage(
                                model = badgeUrl,
                                contentDescription = author.decorate?.name,
                                modifier = Modifier
                                    .padding(start = AppSpacingTokens.ExtraSmall)
                                    .height(14.dp),
                                contentScale = ContentScale.FillHeight
                            )
                        }
                    }
                }

                //  置顶标（module_tag：B 站固定返回 "置顶"，对齐 BiliPai 作者区头部样式）
                if (shouldShowDynamicPinnedTag(item.modules.module_tag)) {
                    Box(
                        modifier = Modifier
                            .padding(start = AppSpacingTokens.ExtraSmall)
                            .border(
                                width = AppSurfaceTokens.OutlineWidth,
                                color = AppContentColors.accent(),
                                shape = AppShapes.container(ContainerLevel.Tag)
                            )
                            .padding(
                                horizontal = AppSpacingTokens.ExtraSmall,
                                vertical = AppSpacingTokens.Micro
                            )
                    ) {
                        AppText(
                            item.modules.module_tag?.text.orEmpty(),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = AppContentColors.accent()
                        )
                    }
                }

                val moreIcon = rememberAppMoreIcon()
                val linkIcon = rememberAppLinkIcon()
                val shareIcon = rememberAppShareIcon()
                val historyIcon = rememberAppHistoryIcon()
                val deleteIcon = rememberAppDeleteIcon()
                val visibilityOffIcon = rememberAppVisibilityOffIcon()
                val chevronUpIcon = rememberAppChevronUpIcon()
                val visibilityOnIcon = rememberAppVisibilityOnIcon()
                val commentIcon = rememberAppCommentIcon()
                val warningIcon = rememberAppWarningIcon()
                val errorTint = AppContentColors.error()
                AppWindowActionMenu(
                    groups = listOf(
                        buildList {
                            add(
                                AppWindowAction(
                                    label = "复制链接",
                                    icon = linkIcon,
                                    onClick = {
                                        val dynamicUrl = "https://t.bilibili.com/${item.id_str}"
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(
                                            android.content.ClipData.newPlainText("动态链接", dynamicUrl)
                                        )
                                        android.widget.Toast.makeText(
                                            context,
                                            "已复制链接",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                )
                            )
                            add(
                                AppWindowAction(
                                    label = "分享动态",
                                    icon = shareIcon,
                                    onClick = {
                                        val dynamicUrl = "https://t.bilibili.com/${item.id_str}"
                                        val descText = content?.desc?.text?.take(100).orEmpty()
                                        val shareText = if (descText.isNotBlank()) {
                                            "$descText\n$dynamicUrl"
                                        } else {
                                            "分享动态\n$dynamicUrl"
                                        }
                                        val shareIntent = android.content.Intent(
                                            android.content.Intent.ACTION_SEND
                                        ).apply {
                                            this.type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(shareIntent, "分享动态")
                                        )
                                    },
                                )
                            )
                            if (watchLaterAid != null && onWatchLaterClick != null) {
                                add(
                                    AppWindowAction(
                                        label = "稍后再看",
                                        icon = historyIcon,
                                        onClick = { onWatchLaterClick(watchLaterAid) },
                                    )
                                )
                            }
                            run {
                                add(
                                    AppWindowAction(
                                        label = "存图 / 分享图片",
                                        icon = historyIcon,
                                        onClick = { shareImagePreview = true },
                                    )
                                )
                            }
                            val canShareToMessage = item.basic?.comment_type in setOf(11, 17) &&
                                item.modules.module_author?.mid != null
                            if (onShareToMessageClick != null && canShareToMessage) {
                                add(
                                    AppWindowAction(
                                        label = "分享至消息",
                                        icon = shareIcon,
                                        onClick = onShareToMessageClick,
                                    )
                                )
                            }
                            if (onCheckDynamicClick != null && menuCapabilities.isOwnDynamic) {
                                add(
                                    AppWindowAction(
                                        label = "检查动态",
                                        icon = warningIcon,
                                        onClick = onCheckDynamicClick,
                                    )
                                )
                            }
                        },
                        buildList {
                            if (!menuCapabilities.isOwnDynamic) {
                                add(
                                    AppWindowAction(
                                        label = "不感兴趣",
                                        icon = visibilityOffIcon,
                                        onClick = {
                                            onManageAction(DynamicManageAction.NotInterested(item.id_str))
                                        },
                                    )
                                )
                            }
                            if (menuCapabilities.canToggleTop) {
                                add(
                                    AppWindowAction(
                                        label = resolveDynamicPinnedMenuLabel(isCurrentlyTop),
                                        icon = chevronUpIcon,
                                        onClick = {
                                            onManageAction(
                                                DynamicManageAction.ToggleTop(item.id_str, isCurrentlyTop)
                                            )
                                        },
                                    )
                                )
                            }
                            if (menuCapabilities.canSetVisibility) {
                                add(
                                    AppWindowAction(
                                        label = resolveDynamicVisibilityMenuLabel(
                                            isPrivate = menuCapabilities.isPrivate
                                        ),
                                        icon = visibilityOnIcon,
                                        onClick = {
                                            onManageAction(
                                                DynamicManageAction.SetVisibility(
                                                    dynamicId = item.id_str,
                                                    dynType = resolveDynamicDynType(item),
                                                    isPrivate = !menuCapabilities.isPrivate
                                                )
                                            )
                                        },
                                    )
                                )
                            }
                            if (menuCapabilities.canManageComments) {
                                add(
                                    AppWindowAction(
                                        label = "评论互动设置",
                                        icon = commentIcon,
                                        onClick = {
                                            val oid = resolveDynamicReplySubjectOid(item)
                                            if (oid == null || onLoadReplyInteractionStatus == null) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "该动态暂不支持互动设置",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                onLoadReplyInteractionStatus(
                                                    oid,
                                                    resolveDynamicReplySubjectType(item)
                                                ) { data ->
                                                    if (data == null) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "获取互动设置失败",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        replyInteractionOid = oid
                                                        replyInteractionType = resolveDynamicReplySubjectType(item)
                                                        showReplyInteractionDialog = data
                                                    }
                                                }
                                            }
                                        },
                                    )
                                )
                            }
                            if (menuCapabilities.canBlockAuthor) {
                                add(
                                    AppWindowAction(
                                        label = "屏蔽该 UP 主",
                                        icon = visibilityOffIcon,
                                        onClick = {
                                            pendingBlockAuthor = DynamicManageAction.BlockAuthor(
                                                authorMid = author?.mid ?: 0L,
                                                authorName = author?.name.orEmpty().ifBlank { "该用户" },
                                                authorFace = author?.face.orEmpty(),
                                            )
                                        },
                                    )
                                )
                            }
                        },
                        buildList {
                            if (menuCapabilities.canEdit) {
                                add(
                                    AppWindowAction(
                                        label = "编辑动态",
                                        onClick = {
                                            onManageAction(
                                                DynamicManageAction.Edit(
                                                    dynamicId = item.id_str,
                                                    initialDraft = resolveDynamicEditDraft(item),
                                                    replyOid = resolveDynamicReplySubjectOid(item),
                                                    replyType = resolveDynamicReplySubjectType(item),
                                                )
                                            )
                                        },
                                    )
                                )
                            }
                            if (deleteAction != null && onDeleteClick != null) {
                                add(
                                    AppWindowAction(
                                        label = deleteAction.label,
                                        icon = deleteIcon,
                                        iconTint = errorTint,
                                        onClick = { pendingDeleteAction = deleteAction },
                                    )
                                )
                            }
                            if (menuCapabilities.canReport) {
                                add(
                                    AppWindowAction(
                                        label = "举报",
                                        icon = warningIcon,
                                        iconTint = errorTint,
                                        onClick = {
                                            onManageAction(
                                                DynamicManageAction.Report(
                                                    dynamicId = item.id_str,
                                                    authorMid = author?.mid ?: 0L
                                                )
                                            )
                                        },
                                    )
                                )
                            }
                        },
                    ),
                ) {
                    AppIcon(
                        moreIcon,
                        contentDescription = "更多",
                        tint = AppContentColors.secondaryText().copy(0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        //  风险提示条（module_dispute：如“视频内含有危险行为，请勿模仿”，点击打开 jump_url）
        val showDispute by androidx.compose.runtime.remember(context) {
            com.android.purebilibili.core.store.ContentDisplaySettings.observe(
                context, com.android.purebilibili.core.store.ContentDisplaySettings.DYNAMIC_WARNING
            )
        }.collectAsStateWithLifecycle(initialValue = false)
        item.modules.module_dispute?.takeIf { showDispute }?.let { dispute ->
            if (shouldShowDynamicDispute(dispute)) {
                val disputeColors = resolveAccessibleContainerColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = AppContentColors.secondaryText(),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    fallbackContentColors = listOf(
                        AppContentColors.primaryText(),
                        MaterialTheme.colorScheme.onBackground,
                    ),
                )
                val disputeClickModifier = if (dispute.jump_url.isNotBlank()) {
                    Modifier.appActionClickable {
                        val target = if (dispute.jump_url.startsWith("//")) {
                            "https:${dispute.jump_url}"
                        } else {
                            dispute.jump_url
                        }
                        uriHandler.openUri(target)
                    }
                } else {
                    Modifier
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacingTokens.Medium)
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .background(disputeColors.containerColor)
                        .then(disputeClickModifier)
                        .padding(
                            horizontal = AppSpacingTokens.Small,
                            vertical = AppSpacingTokens.Small
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        rememberAppWarningIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                        tint = disputeColors.contentColor
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        dispute.title.ifBlank { dispute.desc },
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        color = disputeColors.contentColor
                    )
                }
            }
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

        // 动态标题 (Opus / 专栏 / 带标题图文，统一定位在正文与媒体卡片最上方，对齐 PiliPlus)
        val dynamicCardTitle = remember(opus?.title, content?.major?.article?.title) {
            resolveDynamicHeadlineTitle(
                opus = opus,
                article = content?.major?.article
            )
        }
        if (dynamicCardTitle != null) {
            AppText(
                text = dynamicCardTitle,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Bold,
                color = AppContentColors.primaryText(),
                maxLines = if (isDetail) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = AppSpacingTokens.Small)
            )
        }

        // 失效/删除动态占位提示（对齐 PiliPlus 的 noneWidget 处理）
        val isNoneMajor = content?.major?.type == "MAJOR_TYPE_NONE" || item.type == "DYNAMIC_TYPE_NONE"
        if (isNoneMajor) {
            val tips = content?.major?.none?.tips?.trim()?.takeIf { it.isNotEmpty() } ?: "该动态已失效或被删除"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacingTokens.Small)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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

        //  动态内容文字（支持@高亮 / 表情）；优先可渲染表情的 desc 或 opus summary
        val visibleOpusSummaryDescForBody = remember(opus?.summary, renderableOpusPics) {
            val currentOpus = opus ?: return@remember null
            currentOpus.summary?.let { summary ->
                resolveDynamicOpusSummaryDescForImages(
                    text = summary.text,
                    richTextNodes = summary.rich_text_nodes,
                    hasImages = renderableOpusPics.isNotEmpty()
                )
            }
        }
        val preferredBodyDesc = resolvePreferredDynamicDesc(
            primary = visibleDynamicDesc,
            fallback = visibleOpusSummaryDescForBody
        )
        val dynamicCardEmoteMap = remember(content?.desc, opus?.summary, preferredBodyDesc, fullOpusContentBlocks) {
            buildMap {
                putAll(collectDynamicEmojiUrlMap(content?.desc?.rich_text_nodes.orEmpty()))
                putAll(collectDynamicEmojiUrlMap(opus?.summary?.rich_text_nodes.orEmpty()))
                putAll(collectDynamicEmojiUrlMap(preferredBodyDesc?.rich_text_nodes.orEmpty()))
                fullOpusContentBlocks.forEach { block ->
                    if (block is OpusContentBlock.Text) {
                        putAll(collectDynamicEmojiUrlMap(block.richTextNodes))
                    }
                }
            }
        }
        if (!hasFullOpusDetailContent) preferredBodyDesc?.let { desc ->
            if (shouldRenderDynamicRichText(desc)) {
                // Feed 卡长文折叠：超过 6 行只显示部分正文，用「展开更多」打开详情。
                // 详情页全文展示，不折叠。
                val foldBodyText = !isDetail &&
                    (desc.text.length > DYNAMIC_FEED_TEXT_FOLD_THRESHOLD)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacingTokens.Medium),
                ) {
                    Column {
                        RichTextContent(
                            onLinkClick = onOpenLink,
                            desc = desc,
                            collapseWebLinks = isDetail,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onTopicKeywordClick = onTopicKeywordClick,
                            onVoteClick = { voteId -> pendingVoteId = voteId },
                            onVideoClick = onVideoClick,
                            onDynamicDetailClick = openDynamicDetail,
                            onBangumiClick = onBangumiClick,
                            onArticleClick = onArticleClick,
                            onLiveClick = onLiveClick,
                            onMusicClick = onMusicClick,
                            maxLines = if (foldBodyText) DYNAMIC_FEED_TEXT_MAX_LINES else Int.MAX_VALUE,
                            overflow = if (foldBodyText) TextOverflow.Ellipsis else TextOverflow.Clip,
                            extraEmoteUrlMap = dynamicCardEmoteMap,
                        )
                        if (foldBodyText && openDynamicDetail != null) {
                            AppText(
                                text = "展开更多",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                color = AppContentColors.accent(),
                                modifier = Modifier
                                    .padding(top = AppSpacingTokens.ExtraSmall)
                                    .clip(AppShapes.container(ContainerLevel.Chip))
                                    .appActionClickable(onClick = { openDynamicDetail?.invoke(item.id_str) })
                                    .padding(vertical = AppSpacingTokens.Micro),
                            )
                        }
                    }
                }
            }
        }

        //  互动条：UP 主觉得很赞 / 相关评论提示
        val interactionItems = resolveVisibleDynamicInteractionItems(
            items = item.modules.module_interaction?.items.orEmpty(),
            commentPreviewVisible = LocalDynamicCommentPreviewVisible.current,
        )
        if (interactionItems.isNotEmpty()) {
            val interactionBarColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacingTokens.ExtraSmall, bottom = AppSpacingTokens.Medium)
                    .drawBehind {
                        val strokeWidth = 1.5.dp.toPx()
                        drawLine(
                            color = interactionBarColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(start = AppSpacingTokens.Small),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)
            ) {
                interactionItems.forEach { interactionItem ->
                    val desc = interactionItem.desc ?: return@forEach
                    val onPreviewClick: (() -> Unit)? =
                        if (interactionItem.type == 1 && item.id_str.isNotBlank()) {
                            {
                                DynamicRepository.rememberDynamicDetailSeed(item)
                                if (onShowCommentsInPlace != null) {
                                    onShowCommentsInPlace()
                                } else {
                                    // 热评载荷通常没有 rpid；AT 节点的 rid 是用户 ID，不能拿来猜楼层。
                                    val url = "bilibili://following/detail/${item.id_str}?tab=comment"
                                    if (onOpenLink != null) {
                                        onOpenLink(url)
                                    } else {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url),
                                        ).setPackage(context.packageName)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        if (runCatching { context.startActivity(intent) }.isFailure) {
                                            android.widget.Toast.makeText(
                                                context, "无法打开评论区", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        } else null
                    Row(
                        modifier = if (onPreviewClick != null) {
                            Modifier
                                .fillMaxWidth()
                                .appActionClickable(
                                    onClickLabel = "查看评论",
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = onPreviewClick,
                                )
                        } else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = if (interactionItem.type == 1) {
                                Icons.Outlined.ChatBubbleOutline
                            } else {
                                Icons.Outlined.ThumbUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = AppContentColors.secondaryText()
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        RichTextContent(
                            onLinkClick = onOpenLink,
                            desc = desc,
                            collapseWebLinks = isDetail,
                            onUserClick = onUserClick,
                            onBlankTap = onPreviewClick,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            extraEmoteUrlMap = dynamicCardEmoteMap,
                        )
                    }
                }
            }
        }

        //  视频类型动态 - 大图预览
        content?.major?.archive?.let { archive ->
            val playableBvid = resolveArchivePlayableBvid(archive)
            VideoCardLarge(
                archive = archive,
                publishTs = author?.pub_ts ?: 0L,
                cornerBadgeText = resolveDynamicArchiveBadgeLabel(archive),
                onClick = {
                    playableBvid?.let(onVideoClick)
                        ?: openDynamicDetail?.invoke(item.id_str)
                },
                sharedElementKey = com.android.purebilibili.core.ui.transition.videoPlayerSharedElementKey(archive.bvid)
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.pgc?.let { pgc ->
            val bangumiTarget = resolveArchiveBangumiTarget(pgc)
            VideoCardLarge(
                archive = pgc,
                publishTs = author?.pub_ts ?: 0L,
                cornerBadgeText = "番剧",
                onClick = {
                    bangumiTarget?.let { onBangumiClick(it.seasonId, it.epId) }
                        ?: openDynamicDetail?.invoke(item.id_str)
                },
                sharedElementKey = com.android.purebilibili.core.ui.transition.videoPlayerSharedElementKey(
                    pgc.bvid.ifBlank { item.id_str }
                )
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        //  图片类型动态（支持GIF + 点击预览）。详情若已拉到完整 opus 正文，不再叠一层九宫格预览。
        content?.major?.draw?.takeIf {
            shouldRenderDynamicDrawGrid(
                hasFullOpusImageContent = hasFullOpusDetailContent &&
                    fullOpusContentBlocks.any {
                        it is OpusContentBlock.Image ||
                            (it is OpusContentBlock.Divider && it.pic != null)
                    },
                opusPics = renderableOpusPics,
            )
        }?.let { draw ->
            var selectedImageIndex by remember { mutableIntStateOf(-1) }
            var sourceAnchor by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
            val renderableDrawItems = remember(draw.items) {
                resolveRenderableDrawItems(draw.items)
            }
            val drawPreviewText = remember(author?.name, visibleDynamicDesc?.text) {
                ImagePreviewTextContent(
                    headline = author?.name.orEmpty(),
                    body = visibleDynamicDesc?.text.orEmpty()
                )
            }

            DrawGridV2(
                items = renderableDrawItems,
                gifImageLoader = gifImageLoader,
                maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail),
                onImagePreviewClick = { index, anchor ->
                    val action = resolveDynamicCardMediaAction(item, index)
                    if (action is DynamicCardMediaAction.PreviewImages) {
                        selectedImageIndex = action.initialIndex
                        sourceAnchor = anchor
                    }
                }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))

            // 全屏图片预览
            if (selectedImageIndex >= 0) {
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
                    images = renderableDrawItems.map { it.src },
                    initialIndex = selectedImageIndex,
                    sourceRect = sourceAnchor?.rect,
                    sourceRects = sourceAnchor?.galleryRects.orEmpty(),
                    sourceKey = sourceAnchor?.sourceKey,
                    sourceCornerRadiusDp = sourceAnchor?.cornerRadiusDp
                        ?: resolveDrawGridCornerRadiusDp().toFloat(),
                    textContent = drawPreviewText,
                    defaultTextVisible = dynamicPreviewTextVisible,
                    onDismiss = { selectedImageIndex = -1 }
                )
            }
        }

        //  [新增] Opus 图文动态 (新版格式)
        content?.major?.opus?.let { opus ->
            var selectedImageIndex by remember { mutableIntStateOf(-1) }
            var sourceAnchor by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
            val opusExpandedSourceRects = remember(renderableOpusPics) {
                mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
            }
            val opusExpandedImageCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value
            val visibleOpusSummaryDesc = remember(opus.summary, renderableOpusPics) {
                opus.summary?.let { summary ->
                    resolveDynamicOpusSummaryDescForImages(
                        text = summary.text,
                        richTextNodes = summary.rich_text_nodes,
                        hasImages = renderableOpusPics.isNotEmpty()
                    )
                }
            }
            val opusPreviewText = remember(author?.name, visibleDynamicDesc?.text, visibleOpusSummaryDesc?.text) {
                val body = visibleDynamicDesc?.text.takeUnless { it.isNullOrBlank() }
                    ?: visibleOpusSummaryDesc?.text.orEmpty()
                ImagePreviewTextContent(
                    headline = author?.name.orEmpty(),
                    body = body
                )
            }

            // 正文与标题已在上方统一渲染；此处按需渲染正文内容块与图片列表

            // 显示图片 (转换为 DrawItem 格式复用现有组件)
            if (hasFullOpusDetailContent) {
                val expandOpusDetailImages = shouldExpandDynamicOpusDetailImages(detailImageLayout)
                val previewImages = remember(renderableOpusPics) {
                    renderableOpusPics.map { it.url }
                }
                // The desktop opus API documents width/height as nullable. The
                // paragraph image can therefore have dimensions while the same
                // URL in opus.pics does not (or vice versa). Resolve dimensions
                // once from both payload locations so a recomposition never
                // leaves an AsyncImage without a measurable height.
                val opusPicDimensionsByUrl = remember(renderableOpusPics) {
                    renderableOpusPics
                        .filter { it.url.isNotBlank() && it.width > 0 && it.height > 0 }
                        .associateBy { it.url }
                }
                var fullContentSelectedImageIndex by remember { mutableIntStateOf(-1) }
                var thumbnailSourceAnchor by remember {
                    mutableStateOf<ImagePreviewSourceAnchor?>(null)
                }
                val thumbnailItems = remember(renderableOpusPics) {
                    renderableOpusPics.map { pic ->
                        DrawItem(
                            src = pic.url,
                            width = pic.width,
                            height = pic.height,
                            live_url = pic.live_url,
                        )
                    }
                }
                var thumbnailGridEmitted = false
                fullOpusContentBlocks.forEach { block ->
                    if (shouldEmitOpusThumbnailGridAtBlock(
                            block = block,
                            thumbnailGridEmitted = thumbnailGridEmitted,
                            hasThumbnailItems = thumbnailItems.isNotEmpty(),
                            expandImages = expandOpusDetailImages,
                        )
                    ) {
                        thumbnailGridEmitted = true
                        DrawGridV2(
                            items = thumbnailItems,
                            gifImageLoader = gifImageLoader,
                            maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail),
                            onImagePreviewClick = { index, anchor ->
                                fullContentSelectedImageIndex = index
                                thumbnailSourceAnchor = anchor
                            }
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                    }
                    when (block) {
                        is OpusContentBlock.Text -> {
                            val blockText = normalizeDynamicBodyText(block.text)
                            val richBlockDesc = resolveDynamicOpusTextBlockRichDesc(
                                blockText = blockText,
                                preferredDesc = preferredBodyDesc,
                                blockRichTextNodes = block.richTextNodes,
                            )
                            if (richBlockDesc != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = AppSpacingTokens.Medium),
                                ) {
                                    RichTextContent(
                                        onLinkClick = onOpenLink,
                                        desc = richBlockDesc,
                                        collapseWebLinks = isDetail,
                                        onUserClick = onUserClick,
                                        onTopicClick = onTopicClick,
                                        onTopicKeywordClick = onTopicKeywordClick,
                                        onVoteClick = { voteId -> pendingVoteId = voteId },
                                        onVideoClick = onVideoClick,
                                        onDynamicDetailClick = openDynamicDetail,
                                        onBangumiClick = onBangumiClick,
                                        onArticleClick = onArticleClick,
                                        onLiveClick = onLiveClick,
                                        onMusicClick = onMusicClick,
                                        extraEmoteUrlMap = dynamicCardEmoteMap,
                                    )
                                }
                            } else if (blockText.isNotBlank()) {
                                AppText(
                                    text = blockText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppContentColors.primaryText(),
                                    textAlign = resolveOpusTextAlign(block.alignment),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = AppSpacingTokens.Medium)
                                )
                            }
                        }
                        is OpusContentBlock.Heading -> {
                            AppText(
                                text = block.text,
                                style = when (block.level) {
                                    1 -> MaterialTheme.typography.headlineSmall
                                    3 -> MaterialTheme.typography.titleMedium
                                    else -> MaterialTheme.typography.titleLarge
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = AppContentColors.primaryText(),
                                textAlign = resolveOpusTextAlign(block.alignment),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = AppSpacingTokens.Small,
                                        bottom = AppSpacingTokens.Medium,
                                    ),
                            )
                        }
                        is OpusContentBlock.Quote -> {
                            AppContentCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = AppSpacingTokens.Medium),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                contentPadding = PaddingValues(AppSpacingTokens.Medium),
                            ) {
                                AppText(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppContentColors.secondaryText(),
                                    textAlign = resolveOpusTextAlign(block.alignment),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        is OpusContentBlock.ListBlock -> {
                            AppText(
                                text = block.items.mapIndexed { index, listItem ->
                                    if (block.ordered) "${index + 1}. $listItem" else "• $listItem"
                                }.joinToString("\n"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppContentColors.primaryText(),
                                textAlign = resolveOpusTextAlign(block.alignment),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = AppSpacingTokens.Medium,
                                        bottom = AppSpacingTokens.Medium,
                                    ),
                            )
                        }
                        is OpusContentBlock.Code -> {
                            AppContentCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = AppSpacingTokens.Medium),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentPadding = PaddingValues(AppSpacingTokens.Medium),
                            ) {
                                AppText(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppContentColors.primaryText(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        is OpusContentBlock.Divider -> {
                            val dividerPic = block.pic
                            if (dividerPic != null) {
                                val resolvedDividerPic = remember(dividerPic, opusPicDimensionsByUrl) {
                                    opusPicDimensionsByUrl[dividerPic.url]?.let { known ->
                                        if (dividerPic.width > 0 && dividerPic.height > 0) dividerPic
                                        else dividerPic.copy(width = known.width, height = known.height)
                                    } ?: dividerPic
                                }
                                val dividerAspectRatio = if (resolvedDividerPic.width > 0 && resolvedDividerPic.height > 0) {
                                    resolvedDividerPic.width.toFloat() / resolvedDividerPic.height.toFloat()
                                } else {
                                    16f / 9f
                                }
                                val dividerRequest = remember(resolvedDividerPic.url) {
                                    coil3.request.ImageRequest.Builder(context)
                                        .data(resolvedDividerPic.url)
                                        .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                                        .build()
                                }
                                if (expandOpusDetailImages) {
                                    PageAwareAsyncImage(
                                        model = dividerRequest,
                                        contentDescription = "分割线",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                Modifier.aspectRatio(dividerAspectRatio)
                                            )
                                            .padding(vertical = AppSpacingTokens.Small),
                                        contentScale = ContentScale.FillWidth,
                                    )
                                }
                            } else {
                                AppHorizontalDivider(
                                    modifier = Modifier.padding(vertical = AppSpacingTokens.Medium),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        is OpusContentBlock.Image -> {
                            val resolvedPic = remember(block.pic, opusPicDimensionsByUrl) {
                                opusPicDimensionsByUrl[block.pic.url]?.let { known ->
                                    if (block.pic.width > 0 && block.pic.height > 0) block.pic
                                    else block.pic.copy(width = known.width, height = known.height)
                                } ?: block.pic
                            }
                            val currentImageIndex = previewImages.indexOf(resolvedPic.url)
                            val aspectRatio = remember(resolvedPic.width, resolvedPic.height) {
                                if (resolvedPic.width > 0 && resolvedPic.height > 0) {
                                    resolvedPic.width.toFloat() / resolvedPic.height.toFloat()
                                } else {
                                    // Keep the node measurable while the API omits
                                    // dimensions; the image can then load without
                                    // collapsing and disappearing on recomposition.
                                    4f / 3f
                                }
                            }
                            val imageRequest = remember(resolvedPic.url) {
                                coil3.request.ImageRequest.Builder(context)
                                    .data(resolvedPic.url)
                                    .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                                    .build()
                            }
                            if (expandOpusDetailImages) {
                                val expandedImageSourceRect = rememberImagePreviewSourceRect()
                                PageAwareAsyncImage(
                                    dimInDarkTheme = true,
                                    model = imageRequest,
                                    contentDescription = opus.title.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (aspectRatio > 0f) {
                                                Modifier.aspectRatio(aspectRatio)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(AppShapes.container(ContainerLevel.Card))
                                        .imagePreviewSourceBounds(expandedImageSourceRect)
                                        .imagePreviewGallerySourceBounds(
                                            target = opusExpandedSourceRects,
                                            pageIndex = currentImageIndex,
                                        )
                                        .alpha(if (isImagePreviewSourceHidden(expandedImageSourceRect.value)) 0f else 1f)
                                        .appActionClickable(
                                            interactionSource = null,
                                            indication = null,
                                            enabled = currentImageIndex in previewImages.indices,
                                        ) {
                                            fullContentSelectedImageIndex = currentImageIndex
                                            val anchor = expandedImageSourceRect.value?.let {
                                                ImagePreviewSourceAnchor(
                                                    rect = it,
                                                    cornerRadiusDp = opusExpandedImageCornerRadiusDp,
                                                    galleryRects = opusExpandedSourceRects.toMap(),
                                                )
                                            }
                                            prepareImagePreviewSourceTransition(anchor?.rect)
                                            thumbnailSourceAnchor = anchor
                                        },
                                    contentScale = ContentScale.FillWidth
                                )
                                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                            }
                        }
                        is OpusContentBlock.LinkCard -> {
                            DynamicLinkCardSlot(
                                card = block.card,
                                dynamicId = item.id_str,
                                modifier = Modifier.padding(bottom = AppSpacingTokens.Medium),
                                onReserveClick = onReserveClick,
                                onVoteClick = { voteId -> pendingVoteId = voteId },
                                onOpenCard = openLinkCardTarget,
                                onOpenUrl = { url -> openContentUrl(url) },
                            )
                        }
                    }
                }

                if (!expandOpusDetailImages && !thumbnailGridEmitted && thumbnailItems.isNotEmpty()) {
                    thumbnailGridEmitted = true
                    DrawGridV2(
                        items = thumbnailItems,
                        gifImageLoader = gifImageLoader,
                        maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail),
                        onImagePreviewClick = { index, anchor ->
                            fullContentSelectedImageIndex = index
                            thumbnailSourceAnchor = anchor
                        }
                    )
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                }

                if (fullContentSelectedImageIndex >= 0) {
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
                        images = previewImages,
                        initialIndex = fullContentSelectedImageIndex,
                        sourceRect = thumbnailSourceAnchor?.rect,
                        sourceRects = thumbnailSourceAnchor?.galleryRects.orEmpty(),
                        sourceCornerRadiusDp = thumbnailSourceAnchor?.cornerRadiusDp
                            ?: resolveDrawGridCornerRadiusDp().toFloat(),
                        textContent = opusPreviewText,
                        defaultTextVisible = dynamicPreviewTextVisible,
                        onDismiss = { fullContentSelectedImageIndex = -1 }
                    )
                }
            } else if (renderableOpusPics.isNotEmpty()) {
                val expandOpusFallbackImages = shouldExpandDynamicOpusFallbackImages(
                    isDetail = isDetail,
                    imageLayout = detailImageLayout,
                )
                if (expandOpusFallbackImages) {
                    renderableOpusPics.forEachIndexed { index, pic ->
                        val expandedImageSourceRect = rememberImagePreviewSourceRect()
                        val aspectRatio = if (pic.width > 0 && pic.height > 0) {
                            pic.width.toFloat() / pic.height.toFloat()
                        } else {
                            4f / 3f
                        }
                        val imageRequest = remember(pic.url) {
                            coil3.request.ImageRequest.Builder(context)
                                .data(pic.url)
                                .httpHeaders(
                                    NetworkHeaders.Builder()
                                        .set("Referer", "https://www.bilibili.com/")
                                        .build()
                                )
                                .build()
                        }
                        PageAwareAsyncImage(
                            dimInDarkTheme = true,
                            model = imageRequest,
                            contentDescription = opus.title.orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .imagePreviewSourceBounds(expandedImageSourceRect)
                                .imagePreviewGallerySourceBounds(
                                    target = opusExpandedSourceRects,
                                    pageIndex = index,
                                )
                                .alpha(if (isImagePreviewSourceHidden(expandedImageSourceRect.value)) 0f else 1f)
                                .appActionClickable(interactionSource = null, indication = null) {
                                    selectedImageIndex = index
                                    val anchor = expandedImageSourceRect.value?.let {
                                        ImagePreviewSourceAnchor(
                                            rect = it,
                                            cornerRadiusDp = opusExpandedImageCornerRadiusDp,
                                            galleryRects = opusExpandedSourceRects.toMap(),
                                        )
                                    }
                                    prepareImagePreviewSourceTransition(anchor?.rect)
                                    sourceAnchor = anchor
                                },
                            contentScale = ContentScale.FillWidth,
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                    }
                } else {
                    val drawItems = renderableOpusPics.map { pic ->
                        DrawItem(
                            src = pic.url,
                            width = pic.width,
                            height = pic.height,
                            live_url = pic.live_url
                        )
                    }
                    DrawGridV2(
                        items = drawItems,
                        gifImageLoader = gifImageLoader,
                        maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail),
                        onImagePreviewClick = { index, anchor ->
                            val action = resolveDynamicCardMediaAction(item, index)
                            if (action is DynamicCardMediaAction.PreviewImages) {
                                selectedImageIndex = action.initialIndex
                                sourceAnchor = anchor
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                }

                // 全屏图片预览
                if (selectedImageIndex >= 0) {
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
                        images = renderableOpusPics.map { it.url },
                        initialIndex = selectedImageIndex,
                        sourceRect = sourceAnchor?.rect,
                        sourceRects = sourceAnchor?.galleryRects.orEmpty(),
                        sourceCornerRadiusDp = sourceAnchor?.cornerRadiusDp ?: if (expandOpusFallbackImages) {
                            AppShapes.containerCornerDp(ContainerLevel.Card).value
                        } else {
                            resolveDrawGridCornerRadiusDp().toFloat()
                        },
                        textContent = opusPreviewText,
                        defaultTextVisible = dynamicPreviewTextVisible,
                        onDismiss = { selectedImageIndex = -1 }
                    )
                }
            }
        }

        content?.major?.article?.let { article ->
            val articleCovers = remember(article.covers) { resolveArticleCoverUrls(article) }
            if (articleCovers.isNotEmpty()) {
                var selectedImageIndex by remember { mutableIntStateOf(-1) }
                var sourceAnchor by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
                val articlePreviewText = remember(author?.name, visibleDynamicDesc?.text, article.title, article.desc) {
                    val body = visibleDynamicDesc?.text
                        .takeUnless { it.isNullOrBlank() }
                        ?: article.desc.ifBlank { article.title }
                    ImagePreviewTextContent(
                        headline = author?.name.orEmpty(),
                        body = body
                    )
                }
                val drawItems = remember(article.covers) { resolveArticleCoverDrawItems(article) }
                DrawGridV2(
                    items = drawItems,
                    gifImageLoader = gifImageLoader,
                    maxDisplayImages = resolveDynamicOpusPreviewImageLimit(isDetail),
                    onImagePreviewClick = { index, anchor ->
                        when (val action = resolveDynamicCardMediaAction(item, index, isDetail = isDetail)) {
                            is DynamicCardMediaAction.PreviewImages -> {
                                selectedImageIndex = action.initialIndex
                                sourceAnchor = anchor
                            }
                            is DynamicCardMediaAction.OpenDynamicDetail -> {
                                openDynamicDetail?.invoke(action.dynamicId)
                            }
                            DynamicCardMediaAction.None -> Unit
                        }
                    }
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))

                if (selectedImageIndex >= 0) {
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
                        images = articleCovers,
                        initialIndex = selectedImageIndex,
                        sourceRect = sourceAnchor?.rect,
                        sourceRects = sourceAnchor?.galleryRects.orEmpty(),
                        sourceCornerRadiusDp = sourceAnchor?.cornerRadiusDp
                            ?: resolveDrawGridCornerRadiusDp().toFloat(),
                        textContent = articlePreviewText,
                        defaultTextVisible = dynamicPreviewTextVisible,
                        onDismiss = { selectedImageIndex = -1 }
                    )
                }
            }
        }

        //  [新增] 合集/剧集动态
        content?.major?.ugc_season?.let { season ->
            val seasonArchive = resolveUgcSeasonArchiveFallback(season)
            val playableBvid = resolveUgcSeasonPlayableBvid(season)
            if (seasonArchive != null) {
                VideoCardLarge(
                    archive = seasonArchive,
                    publishTs = author?.pub_ts ?: 0L,
                    onClick = {
                        playableBvid?.let(onVideoClick)
                            ?: openDynamicDetail?.invoke(item.id_str)
                    },
                    isCollection = true,
                    collectionTitle = season.title,
                    sharedElementKey = com.android.purebilibili.core.ui.transition.videoPlayerSharedElementKey(
                        seasonArchive.bvid
                    )
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
            } else {
                AppText(
                     "合集：${season.title}",
                     fontWeight = FontWeight.Bold,
                     color = AppContentColors.accent()
                )
                 Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            }
        }

        //  直播推荐动态
        content?.major?.live_rcmd?.let { liveRcmd ->
            LiveCard(
                liveRcmd = liveRcmd,
                onLiveClick = { roomId, title, uname ->
                    dispatchDynamicCardPrimaryAction(
                        action = DynamicCardPrimaryAction.OpenLive(roomId, title, uname),
                        onVideoClick = onVideoClick,
                        onBangumiClick = onBangumiClick,
                        onArticleClick = onArticleClick,
                        onDynamicDetailClick = openDynamicDetail,
                        onUserClick = onUserClick,
                        onLiveClick = onLiveClick
                    )
                }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.live?.let { live ->
            LiveMajorCard(
                live = live,
                onLiveClick = { roomId, title, uname ->
                    dispatchDynamicCardPrimaryAction(
                        action = DynamicCardPrimaryAction.OpenLive(roomId, title, uname),
                        onVideoClick = onVideoClick,
                        onBangumiClick = onBangumiClick,
                        onArticleClick = onArticleClick,
                        onDynamicDetailClick = openDynamicDetail,
                        onUserClick = onUserClick,
                        onLiveClick = onLiveClick
                    )
                }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.subscription_new?.live_rcmd?.let { liveRcmd ->
            LiveCard(
                liveRcmd = liveRcmd,
                onLiveClick = { roomId, title, uname ->
                    dispatchDynamicCardPrimaryAction(
                        action = DynamicCardPrimaryAction.OpenLive(roomId, title, uname),
                        onVideoClick = onVideoClick,
                        onBangumiClick = onBangumiClick,
                        onArticleClick = onArticleClick,
                        onDynamicDetailClick = openDynamicDetail,
                        onUserClick = onUserClick,
                        onLiveClick = onLiveClick
                    )
                }
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.music?.takeIf { it.title.isNotBlank() }?.let { music ->
            val musicId = music.id.removePrefix("au").removePrefix("AU").toLongOrNull()
            DynamicNativeLinkCard(
                title = music.title,
                subtitle = music.label,
                cover = music.cover,
                kindLabel = "音乐",
                actionLabel = "播放",
                enabled = musicId != null || music.jump_url.isNotBlank(),
                onClick = {
                    if (musicId != null && onMusicClick != null) {
                        onMusicClick(musicId)
                    } else if (music.jump_url.isNotBlank()) {
                        openContentUrl(music.jump_url)
                    }
                },
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.medialist?.takeIf { it.title.isNotBlank() }?.let { mediaList ->
            DynamicNativeLinkCard(
                title = mediaList.title,
                subtitle = mediaList.sub_title.ifBlank { "收藏夹" },
                cover = mediaList.cover,
                kindLabel = mediaList.badge?.text.orEmpty().ifBlank { "收藏夹" },
                actionLabel = "打开",
                enabled = mediaList.id.toLongOrNull() != null || mediaList.jump_url.isNotBlank(),
                onClick = {
                    onCollectionClick?.invoke(
                        mediaList.id.toLongOrNull() ?: 0L,
                        item.modules.module_author?.mid ?: 0L,
                        mediaList.title,
                        mediaList.jump_url,
                    )
                        ?: openContentUrl(mediaList.jump_url)
                },
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        content?.major?.courses?.takeIf { it.title.isNotBlank() }?.let { course ->
            DynamicNativeLinkCard(
                title = course.title,
                subtitle = listOf(course.sub_title, course.desc)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" · "),
                cover = course.cover,
                kindLabel = course.badge?.text.orEmpty().ifBlank { "课程" },
                actionLabel = "打开",
                enabled = course.jump_url.isNotBlank(),
                onClick = {
                    onCourseClick?.invoke(course.jump_url, course.title)
                        ?: openContentUrl(course.jump_url)
                },
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        resolveDynamicMajorCard(
            // Music and subscription cards have dedicated native renderers above.
            major = content?.major?.takeUnless {
                it.music != null || it.subscription_new != null || it.medialist != null || it.courses != null
            },
            darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme(),
        )?.let { majorCard ->
            DynamicNativeLinkCard(
                title = majorCard.title,
                subtitle = majorCard.subtitle,
                cover = majorCard.cover,
                kindLabel = majorCard.kindLabel,
                actionLabel = majorCard.actionLabel,
                enabled = majorCard.enabled && majorCard.jumpUrl.isNotBlank(),
                onClick = { openContentUrl(majorCard.jumpUrl) },
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        //  转发动态 - 嵌套显示原始内容
        if (type == DynamicType.FORWARD && item.orig != null) {
            val checkedItemOrig = requireNotNull(item.orig)
            ForwardedContent(
                orig = checkedItemOrig,
                onVideoClick = onVideoClick,
                onBangumiClick = onBangumiClick,
                onUserClick = onUserClick,
                onTopicClick = onTopicClick,
                onTopicKeywordClick = onTopicKeywordClick,
                onDynamicDetailClick = openDynamicDetail,
                onArticleClick = onArticleClick,
                onLiveClick = onLiveClick,
                onMusicClick = onMusicClick,
                gifImageLoader = gifImageLoader,
                defaultPreviewTextVisible = dynamicPreviewTextVisible,
                collapseWebLinks = isDetail,
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        additionalLinkCard?.let { linkCard ->
            DynamicLinkCardSlot(
                card = linkCard,
                dynamicId = item.id_str,
                onReserveClick = onReserveClick,
                onVoteClick = { voteId -> pendingVoteId = voteId },
                onOpenCard = openLinkCardTarget,
                onOpenUrl = { url -> openContentUrl(url) },
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        }

        //  [修复] 底部操作栏：转发、评论、点赞 - 始终显示
        val statModule = stat ?: DynamicStatModule()  // 使用默认值避免按钮消失
        val actionButtonWeight = resolveDynamicActionButtonSlotWeight()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacingTokens.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(resolveDynamicActionButtonSpacing())
        ) {
            // 转发按钮
            ActionButton(
                count = (statModule.forward.count + forwardCountDelta).coerceAtLeast(0),
                label = "转发",
                enabled = !statModule.forward.forbidden,
                onClick = { onRepostClick(item.id_str) },
                modifier = Modifier.weight(actionButtonWeight)
            )

            // 评论按钮
            ActionButton(
                count = statModule.comment.count,
                label = "评论",
                // forbidden 代表评论操作受限；仍允许进入详情查看已有评论。
                enabled = item.id_str.isNotBlank(),
                onClick = {
                    DynamicRepository.rememberDynamicDetailSeed(item)
                    onCommentClick(item.id_str)
                },
                modifier = Modifier.weight(actionButtonWeight)
            )

            // 点赞按钮
            ActionButton(
                count = statModule.like.count,
                label = "点赞",
                isActive = effectiveIsLiked,
                onClick = {
                    onLikeClickWithState?.invoke(item.id_str, effectiveIsLiked)
                        ?: onLikeClick(item.id_str)
                },
                modifier = Modifier.weight(actionButtonWeight)
            )
        }
        }

        // PiliPlus: fold InkWell is a sibling of the card body, not the card's
        // primary tap target. Nested clickable here still opened dynamic detail.
        if (!isDetail) {
            val foldStatement = resolveDynamicFoldStatement(item.modules.module_fold)
            if (foldStatement != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = resolveDynamicCardContentPadding())
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .appActionClickable(enabled = onUnfoldRelatedClick != null) {
                            onUnfoldRelatedClick?.invoke(item.id_str)
                        }
                        .padding(vertical = AppSpacingTokens.Small),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val foldUsers = item.modules.module_fold?.users.orEmpty().take(3)
                    if (foldUsers.isNotEmpty()) {
                        Box(modifier = Modifier.height(22.dp)) {
                            foldUsers.forEachIndexed { index, user ->
                                PageAwareAsyncImage(
                                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                        .data(user.face.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .offset(x = (index * 14).dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .border(AppSurfaceTokens.OutlineWidth, AppSurfaceTokens.surface(), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    }
                    AppText(
                        foldStatement,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = AppContentColors.secondaryText()
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppIcon(
                        rememberAppChevronDownIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.ExtraSmall),
                        tint = AppContentColors.secondaryText()
                    )
                }
            }
        }

        if (!isDetail) {
            AppHorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                thickness = AppSpacingTokens.Micro * 0.35f
            )
        }
    }
}

private fun resolveOpusTextAlign(alignment: Int): TextAlign = when (alignment) {
    1 -> TextAlign.Center
    2 -> TextAlign.End
    else -> TextAlign.Start
}

/**
 * 信息流、图文详情与专栏正文共用的附加内容卡片入口。
 *
 * 这里只负责预约会话与点击分发，展示与版式交给 [DynamicLinkCard]。
 * 页面传入会话时由页面负责关闭，滚出视口不会丢失预约回写。
 */
@Composable
internal fun DynamicLinkCardSlot(
    card: OpusLinkCard,
    dynamicId: String,
    modifier: Modifier = Modifier,
    onReserveClick: ((DynamicReserveAction, (Result<DynamicReserveResult>) -> Unit) -> Unit)?,
    onVoteClick: (Long) -> Unit,
    onOpenCard: (OpusLinkCard) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUnavailableClick: ((OpusLinkCard) -> Unit)? = null,
    reserveSession: DynamicReserveCardSession? = null,
) {
    val context = LocalContext.current
    val account by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val session = remember(card, dynamicId, account, reserveSession) { reserveSession ?: DynamicReserveCardSession(card) }
    val feedbackActive = remember(session) { java.util.concurrent.atomic.AtomicBoolean(true) }
    val visualWorkAllowed by androidx.compose.runtime.rememberUpdatedState(com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current)
    androidx.compose.runtime.DisposableEffect(session, reserveSession) {
        onDispose {
            feedbackActive.set(false)
            if (reserveSession == null) session.close()
        }
    }
    val shownCard = session.shownCard
    val reserveUi = remember(shownCard, dynamicId, onReserveClick != null) {
        resolveDynamicReserveUi(shownCard, dynamicId)?.let { ui ->
            ui.copy(actionEnabled = ui.actionEnabled &&
                (ui.actionJumpUrl.isNotBlank() || (onReserveClick != null && dynamicId.isNotBlank())))
        }
    }
    val matchUi = remember(shownCard) { resolveDynamicMatchUi(shownCard) }
    val onReserveAction: (() -> Unit)? = when {
        reserveUi == null -> null
        reserveUi.actionJumpUrl.isNotBlank() -> ({ onOpenUrl(reserveUi.actionJumpUrl) })
        onReserveClick != null -> ({
            if (com.android.purebilibili.core.store.TokenManager.accountIdentity.value == account &&
                reserveUi.actionEnabled && session.begin()) {
                onReserveClick(reserveUi.action) { result ->
                    if (com.android.purebilibili.core.store.TokenManager.accountIdentity.value == account && session.complete(result)) {
                        if (feedbackActive.get() && visualWorkAllowed) {
                            result.onFailure { error ->
                                android.widget.Toast.makeText(context, error.message ?: "预约操作失败", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
        else -> null
    }
    DynamicLinkCard(
        card = shownCard,
        modifier = modifier,
        reserveUi = reserveUi,
        matchUi = matchUi,
        reserveSubmitting = session.submitting,
        onReserveAction = onReserveAction,
        clickEnabled = shownCard.jumpUrl.isNotBlank() || resolveDynamicLinkCardVoteId(shownCard) != null ||
            reserveUi?.descriptionJumpUrl?.isNotBlank() == true ||
            (shownCard.type == "LINK_CARD_TYPE_ITEM_NULL" && onUnavailableClick != null),
        onClick = {
            val voteId = resolveDynamicLinkCardVoteId(shownCard)
            when {
                shownCard.type == "LINK_CARD_TYPE_ITEM_NULL" && onUnavailableClick != null -> onUnavailableClick(shownCard)
                voteId != null -> onVoteClick(voteId)
                shownCard.jumpUrl.isNotBlank() -> onOpenCard(shownCard)
                reserveUi?.descriptionJumpUrl?.isNotBlank() == true -> onOpenUrl(reserveUi.descriptionJumpUrl)
            }
        },
    )
}

@Composable
internal fun DynamicNativeLinkCard(
    title: String,
    subtitle: String,
    cover: String,
    kindLabel: String,
    actionLabel: String,
    enabled: Boolean,
    actionEnabled: Boolean = true,
    onActionClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    AppContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .appActionClickable(enabled = enabled, onClick = onClick),
        contentPadding = PaddingValues(horizontal = AppSpacingTokens.ExtraSmall)
    ) {
        AppListItem(
            overlineContent = {
                AppText(
                    text = kindLabel,
                    color = AppContentColors.accent()
                )
            },
            headlineContent = {
                AppText(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = subtitle.takeIf { it.isNotBlank() }?.let { supportingText ->
                {
                    AppText(
                        text = supportingText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = AppContentColors.secondaryText()
                    )
                }
            },
            leadingContent = if (cover.isNotBlank()) {
                {
                    PageAwareAsyncImage(
                        dimInDarkTheme = true,
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 88.dp, height = 56.dp)
                            .clip(AppShapes.container(ContainerLevel.Chip)),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                null
            },
            trailingContent = actionLabel.takeIf(String::isNotBlank)?.let { label ->
                {
                    AppText(
                        text = label,
                        color = if (actionEnabled) AppContentColors.accent()
                        else AppContentColors.secondaryText(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
                            .semantics { contentDescription = "操作：$label" }
                            .then(
                                if (onActionClick != null) Modifier.appActionClickable(
                                    enabled = actionEnabled,
                                    onClick = onActionClick,
                                ) else Modifier
                            )
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            },
        )
    }
}

@Composable
internal fun DynamicTopicLabel(
    topicName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 话题入口按文字高度占位，命中范围也与可见框一致：clickable 会按
    // ViewConfiguration.minimumTouchTargetSize 把低于 48dp 的布局向外扩命中，
    // 这里显式清零，避免又撑出一条点得到、看不到的空白。
    val baseViewConfiguration = LocalViewConfiguration.current
    val contentSizedViewConfiguration = remember(baseViewConfiguration) {
        object : ViewConfiguration by baseViewConfiguration {
            override val minimumTouchTargetSize: DpSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides contentSizedViewConfiguration) {
        Row(
            modifier = modifier
                .clip(AppShapes.container(ContainerLevel.Chip))
                .appActionClickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppSpacingTokens.ExtraSmall))
                    .background(AppContentColors.accent().copy(alpha = 0.18f))
                    .padding(horizontal = AppSpacingTokens.Micro),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = "#",
                    color = AppContentColors.accent(),
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            AppText(
                text = topicName,
                color = AppContentColors.accent(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openDynamicUrl(
    uriHandler: androidx.compose.ui.platform.UriHandler,
    rawUrl: String,
) {
    if (rawUrl.isBlank()) return
    val target = when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        else -> rawUrl
    }
    runCatching { uriHandler.openUri(target) }
}

/**
 *  富文本内容（支持表情、@提及、话题高亮）
 *  解析 API 返回的 rich_text_nodes 来正确渲染表情图片；
 *  若节点仅为纯文本短码，则用表情面板缓存补全图片。
 *
 *  @param onBlankTap 点击非 @/链接区域时回调（转发原文点击跳原动态）
 */
@Composable
fun RichTextContent(
    desc: DynamicDesc,
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onVoteClick: (Long) -> Unit = {},
    onBlankTap: (() -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onDynamicDetailClick: ((String) -> Unit)? = null,
    onBangumiClick: ((Long, Long) -> Unit)? = null,
    onArticleClick: ((Long, String) -> Unit)? = null,
    onLiveClick: ((Long, String, String) -> Unit)? = null,
    onMusicClick: ((Long) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = MaterialTheme.typography.bodyLarge.lineHeight,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    extraEmoteUrlMap: Map<String, String> = emptyMap(),
    collapseWebLinks: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var catalogEmoteMap by remember { mutableStateOf(DynamicEmoteCatalog.snapshot()) }
    val emoteCatalogSessionKey = DynamicEmoteCatalog.currentSessionKey()
    LaunchedEffect(emoteCatalogSessionKey) {
        catalogEmoteMap = DynamicEmoteCatalog.ensureLoaded()
    }
    val primaryColor = AppContentColors.accent()
    val textColor = AppContentColors.primaryText()
    // 原生链接分发：BasicText 在 Text 内部处理 LinkAnnotation 点击，不再依赖
    // 外层 pointerInput 查表，与划选/卡片长按不再竞争。每次组合重建 dispatch
    // 闭包以捕获最新回调，并作为 remember key 同步重建富文本。
    val dispatchDynamicLink: (String) -> Unit = { payload ->
        dispatchDynamicRichTextLinkPayload(
            payload = payload,
            context = context,
            uriHandler = uriHandler,
            scope = scope,
            onUserClick = onUserClick,
            onVoteClick = onVoteClick,
            onTopicClick = onTopicClick,
            onTopicKeywordClick = onTopicKeywordClick,
            onVideoClick = onVideoClick,
            onDynamicDetailClick = onDynamicDetailClick,
            onBangumiClick = onBangumiClick,
            onArticleClick = onArticleClick,
            onLiveClick = onLiveClick,
            onMusicClick = onMusicClick,
            onLinkClick = onLinkClick,
        )
    }
    val richText = remember(desc, primaryColor, textColor, catalogEmoteMap, extraEmoteUrlMap, dispatchDynamicLink, collapseWebLinks) {
        buildDynamicRichText(
            desc = desc,
            primaryColor = primaryColor,
            textColor = textColor,
            extraEmoteUrlMap = buildMap {
                putAll(catalogEmoteMap)
                putAll(extraEmoteUrlMap)
            },
            linkListener = LinkInteractionListener { link ->
                dispatchDynamicLink((link as LinkAnnotation.Clickable).tag)
            },
            collapseWebLinks = collapseWebLinks,
        )
    }
    val annotatedText = richText.annotatedString

    // 仅对实际用到的表情 id 建 InlineContent，避免整包表情占内存
    val inlineContent = remember(richText.emojiUrlById) {
        richText.emojiUrlById.mapValues { (_, iconUrl) ->
            InlineTextContent(
                Placeholder(
                    width = 1.4.em,
                    height = 1.4.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                PageAwareAsyncImage(
                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    val copyText = remember(desc.rich_text_nodes, desc.text) {
        val richNodeText = resolveDynamicRichTextNodeDisplayText(desc.rich_text_nodes)
        desc.text.ifBlank { richNodeText }.trim()
    }
    var showTextSelectionSheet by remember(copyText) { mutableStateOf(false) }
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.LONG_PRESS)

    // 保留 BasicText 原生链接点击；长按打开全文选择面板，轻触空白才走转发回调。
    val blankTapWithFeedback = appActionWithFeedback { onBlankTap?.invoke() }
    val latestBlankTapWithFeedback by rememberUpdatedState(blankTapWithFeedback)
    val textGestureModifier = Modifier.pointerInput(annotatedText, onBlankTap, copyText) {
        detectTapWithSelectionFriendly(
            onLongPress = {
                if (copyText.isNotBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showTextSelectionSheet = true
                }
            },
            onTap = onBlankTap?.let { { _: Offset -> latestBlankTapWithFeedback() } },
        )
    }
    AppText(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        color = textColor,
        modifier = modifier.then(textGestureModifier)
    )

    if (showTextSelectionSheet) {
        TextSelectionBottomSheet(
            text = copyText,
            title = "选择动态内容",
            onDismiss = { showTextSelectionSheet = false }
        )
    }
}

/**
 * 原生链接点击的集中分发：payload 由 [resolveDynamicRichTextLinkAction] 解析，
 * 链接载荷复用旧的 BilibiliNavigationTargetParser 路由（in-app 优先，外部兜底）。
 */
private fun dispatchDynamicRichTextLinkPayload(
    payload: String,
    context: android.content.Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    scope: kotlinx.coroutines.CoroutineScope,
    onUserClick: ((Long) -> Unit)?,
    onVoteClick: ((Long) -> Unit)?,
    onTopicClick: ((Long) -> Unit)?,
    onTopicKeywordClick: ((String) -> Unit)?,
    onVideoClick: ((String) -> Unit)?,
    onDynamicDetailClick: ((String) -> Unit)?,
    onBangumiClick: ((Long, Long) -> Unit)?,
    onArticleClick: ((Long, String) -> Unit)?,
    onLiveClick: ((Long, String, String) -> Unit)?,
    onMusicClick: ((Long) -> Unit)?,
    onLinkClick: ((String) -> Unit)?,
) {
    when (val action = resolveDynamicRichTextLinkAction(payload)) {
        is DynamicRichTextLinkAction.User ->
            onUserClick?.invoke(action.mid)
        is DynamicRichTextLinkAction.UserName -> scope.launch {
            // Missing AT IDs cannot be inferred from the display text. Resolve an exact
            // account match on tap; ambiguous or unavailable results open user search.
            val matches = SearchRepository.searchUp(action.name).getOrNull()
                ?.first.orEmpty()
                .filter { it.uname == action.name && it.mid > 0L }
                .distinctBy { it.mid }
            val mid = matches.singleOrNull()?.mid
            if (mid != null && onUserClick != null) {
                onUserClick(mid)
            } else {
                val searchUrl = "bilibili://search?keyword=" +
                    java.net.URLEncoder.encode(action.name, java.nio.charset.StandardCharsets.UTF_8.name())
                if (onTopicKeywordClick != null) {
                    onTopicKeywordClick(action.name)
                } else if (onLinkClick != null) {
                    onLinkClick(searchUrl)
                } else {
                    val inAppIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(searchUrl)
                    ).setPackage(context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launched = runCatching { context.startActivity(inAppIntent) }.isSuccess
                    if (!launched) {
                        openDynamicRichTextLinkExternally(context, searchUrl, uriHandler)
                    }
                }
            }
        }
        is DynamicRichTextLinkAction.Vote ->
            onVoteClick?.invoke(action.voteId)
        is DynamicRichTextLinkAction.TopicId ->
            onTopicClick?.invoke(action.topicId)
        is DynamicRichTextLinkAction.TopicKeyword -> {
            val keyword = action.keyword
            if (onTopicKeywordClick != null) {
                onTopicKeywordClick(keyword)
            } else {
                val searchUrl = "bilibili://search?keyword=" +
                    java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8.name())
                if (onLinkClick != null) {
                    onLinkClick(searchUrl)
                } else {
                    val inAppIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(searchUrl)
                    ).setPackage(context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launched = runCatching { context.startActivity(inAppIntent) }.isSuccess
                    if (!launched) {
                        openDynamicRichTextLinkExternally(context, searchUrl, uriHandler)
                    }
                }
            }
        }
        is DynamicRichTextLinkAction.Url -> {
            val rawUrl = action.url
            scope.launch {
                val target = BilibiliNavigationTargetParser.parse(rawUrl)
                    ?: if (rawUrl.contains("b23.tv", ignoreCase = true)) {
                        BilibiliNavigationTargetParser.resolve(rawUrl)
                    } else null
                if (target != null) {
                    val handled = if (target.hasCommentLocation) false else when (target) {
                        is BilibiliNavigationTarget.Dynamic -> {
                            if (onDynamicDetailClick != null) {
                                onDynamicDetailClick(target.dynamicId)
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.Video -> {
                            if (onVideoClick != null) {
                                onVideoClick(target.videoId)
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.Space -> {
                            if (target.mid > 0L) {
                                onUserClick?.invoke(target.mid)
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.BangumiSeason -> {
                            if (onBangumiClick != null) {
                                onBangumiClick(target.seasonId, target.mediaId)
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.BangumiEpisode -> {
                            if (onBangumiClick != null) {
                                onBangumiClick(0L, target.epId)
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.Article -> {
                            if (onArticleClick != null) {
                                onArticleClick(target.articleId, "")
                                true
                            } else false
                        }
                        // 文集交给宿主链接分发，保留文集身份进入对应页面。
                        is BilibiliNavigationTarget.ArticleCollection -> false
                        is BilibiliNavigationTarget.PopularFeed -> false
                        is BilibiliNavigationTarget.CommentDetail -> false
                        is BilibiliNavigationTarget.Live -> {
                            if (onLiveClick != null) {
                                onLiveClick(target.roomId, "", "")
                                true
                            } else false
                        }
                        is BilibiliNavigationTarget.Music -> {
                            val auSid = target.musicId.removePrefix("au").removePrefix("AU").toLongOrNull()
                            if (auSid != null && onMusicClick != null) {
                                onMusicClick(auSid)
                                true
                            } else false
                        }
                        // 富文本里的赛事链接没有原生详情回调，统一落到末尾的 onLinkClick 兜底。
                        is BilibiliNavigationTarget.Match -> false
                        is BilibiliNavigationTarget.Search -> {
                            if (onTopicKeywordClick != null) {
                                onTopicKeywordClick(target.keyword)
                                true
                            } else {
                                val searchUrl = "bilibili://search?keyword=" +
                                    java.net.URLEncoder.encode(target.keyword, java.nio.charset.StandardCharsets.UTF_8.name())
                                val inAppIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(searchUrl)
                                ).setPackage(context.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(inAppIntent) }.isSuccess
                            }
                        }
                    }
                    if (handled) return@launch
                }

                if (onLinkClick != null) {
                    onLinkClick(rawUrl)
                } else {
                    when (resolveDynamicRichTextOpenMode(rawUrl)) {
                        DynamicRichTextOpenMode.IN_APP -> {
                            val inAppIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(rawUrl)
                            ).setPackage(context.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            val launchedInApp = runCatching {
                                context.startActivity(inAppIntent)
                            }.isSuccess
                            if (!launchedInApp) {
                                openDynamicRichTextLinkExternally(
                                    context,
                                    rawUrl,
                                    uriHandler
                                )
                            }
                        }
                        DynamicRichTextOpenMode.EXTERNAL -> {
                            openDynamicRichTextLinkExternally(
                                context,
                                rawUrl,
                                uriHandler
                            )
                        }
                        null -> Unit
                    }
                }
            }
        }
        null -> Unit
    }
}

private fun openDynamicRichTextLinkExternally(
    context: android.content.Context,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val externalIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    val packageManager = context.packageManager
    val externalPackage = packageManager.queryIntentActivities(
        externalIntent,
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
    ).firstOrNull { it.activityInfo?.packageName != context.packageName }
        ?.activityInfo
        ?.packageName

    val launchedExternally = if (!externalPackage.isNullOrBlank()) {
        runCatching {
            context.startActivity(externalIntent.setPackage(externalPackage))
        }.isSuccess
    } else {
        false
    }

    if (!launchedExternally) {
        runCatching { uriHandler.openUri(url) }
    }
}

@Composable
private fun DynamicAuthorFace(
    faceUrl: String,
    officialType: Int?,
    vipStatus: Int?,
    faceSize: Dp,
    modifier: Modifier = Modifier,
) {
    val normalizedFace = faceUrl.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(faceSize)) {
            PageAwareAsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(normalizedFace)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            UserAvatarCornerMarkBadge(
                mark = resolveUserAvatarCornerMark(
                    officialType = officialType,
                    vipStatus = vipStatus,
                ),
                modifier = Modifier.align(Alignment.BottomEnd),
                badgeSize = 14.dp,
            )
        }
    }
}

/**
 *  紧凑列表卡片 - 单行显示
 */
@Composable
fun DynamicCardCompact(
    item: DynamicItem,
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit
) {
    val author = item.modules.module_author
    val content = item.modules.module_dynamic
    val stat = item.modules.module_stat

    // 获取内容预览文本
    val previewText = content?.desc?.text?.take(50)
        ?: content?.major?.archive?.title
        ?: "动态"

    val authorClickMid = remember(item) { resolveDynamicAuthorClickMid(item) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appActionClickable {
                // 如果有视频则跳转视频
                content?.major?.archive
                    ?.let(::resolveArchivePlayableBvid)
                    ?.let(onVideoClick)
                    ?: authorClickMid?.let(onUserClick)
            }
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium),  //  优化间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        if (author != null) {
            DynamicAuthorFace(
                faceUrl = author.face,
                officialType = author.official_verify?.type,
                vipStatus = author.vip?.status,
                faceSize = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium,
                modifier = Modifier
                    .size(AppChromeSizeTokens.MinimumTouchTarget)
                    .semantics { contentDescription = "查看${author.name}的个人主页" }
                    .appActionClickable(enabled = authorClickMid != null) {
                        authorClickMid?.let(onUserClick)
                    },
            )

            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
        }

        // 内容区
        Column(modifier = Modifier.weight(1f)) {
            // 用户名 + 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    author?.name ?: "",
                    fontWeight = FontWeight.Medium,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = AppContentColors.primaryText(),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    author?.let {
                        resolveDynamicAuthorTimeText(
                            pubTime = it.pub_time,
                            pubTs = it.pub_ts
                        )
                    }.orEmpty(),
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = AppContentColors.secondaryText().copy(0.5f)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))

            // 内容预览
            AppText(
                previewText,
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        // 封面缩略图（如果有视频）
        content?.major?.archive?.let { archive ->
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            PageAwareAsyncImage(
                dimInDarkTheme = true,
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(archive.cover.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.DoubleExtraLarge, height = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Micro)
                    .clip(AppShapes.container(ContainerLevel.Chip)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
