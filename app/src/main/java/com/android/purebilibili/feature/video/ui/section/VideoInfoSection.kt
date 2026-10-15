// File: feature/video/ui/section/VideoInfoSection.kt
package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.motion.folmeExpandEnterTransition
import com.android.purebilibili.core.ui.motion.folmeExpandExitTransition
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
//  已改用 AppContentColors.accent()
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.VideoStaff
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.core.ui.common.TextSelectionPolicy
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.feature.video.ui.components.VideoCardSkeleton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.rotate
import com.android.purebilibili.core.ui.common.copyOnClick
import com.android.purebilibili.core.ui.OfficialVerifyBadge
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.resolveUpNameColor
import com.android.purebilibili.core.ui.components.UserUpBadge
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadgeFromRole
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.resolveVideoMetadataSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldEnableVideoMetadataSharedTransition
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoMetadataSharedElementBoundsTransformSpec
import com.android.purebilibili.data.model.response.BgmDetailData
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.data.model.response.BgmRecommendVideo
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.video.screen.buildVideoNavigationOptions
import com.android.purebilibili.feature.video.ui.FollowButtonTone
import com.android.purebilibili.feature.video.ui.FollowTextTone
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
import com.android.purebilibili.feature.video.ui.resolveVideoFollowVisualPolicy
import com.android.purebilibili.data.repository.ViewGrpcRepository
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.ui.components.ShimmerContainer
import com.android.purebilibili.feature.video.ui.components.SkeletonBox
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private val useMiuixSpring: Boolean
    @Composable get() = com.android.purebilibili.core.theme.LocalAppUiStyle.current ==
        com.android.purebilibili.core.theme.AppUiStyle.MIUIX

internal const val VIDEO_DESCRIPTION_URL_TAG = "VIDEO_DESCRIPTION_URL"
private val VIDEO_DESCRIPTION_URL_PATTERN =
    """((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""".toRegex()
private val VIDEO_DESCRIPTION_INLINE_BVID_PATTERN =
    Regex("""(?<![A-Za-z0-9])BV[a-zA-Z0-9]{10}(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
private val VIDEO_DESCRIPTION_TOPIC_PATTERN =
    Regex("""#([^#\n\r\t]+)#""")
private val VIDEO_DESCRIPTION_MENTION_PATTERN =
    Regex("""@[^\s@,，。:：;；!！?？/\\]{1,32}""")

internal fun buildVideoDescriptionAnnotatedString(
    desc: String,
    urlColor: Color,
    linkListener: androidx.compose.ui.text.LinkInteractionListener? = null
): AnnotatedString = buildVideoDescriptionAnnotatedString(
    desc = desc,
    descV2 = emptyList(),
    urlColor = urlColor,
    linkListener = linkListener
)

/**
 * 构建简介富文本。descV2 非空时按分段渲染:type=2 的 @提及带 biz_id,
 * 点击直达 space.bilibili.com/{mid};纯文本回退时 @xxx 高亮并跳用户搜索。
 */
internal fun buildVideoDescriptionAnnotatedString(
    desc: String,
    descV2: List<com.android.purebilibili.data.model.response.VideoDescSegment>,
    urlColor: Color,
    linkListener: androidx.compose.ui.text.LinkInteractionListener? = null
): AnnotatedString {
    if (descV2.isEmpty()) {
        return buildRawDescriptionAnnotatedString(desc, urlColor, linkListener)
    }
    return buildAnnotatedString {
        descV2.forEach { segment ->
            if (segment.type == 2 && segment.bizId > 0 && segment.rawText.isNotBlank()) {
                withLink(
                    androidx.compose.ui.text.LinkAnnotation.Clickable(
                        tag = "https://space.bilibili.com/${segment.bizId}",
                        styles = null,
                        linkInteractionListener = linkListener,
                    )
                ) {
                    withStyle(SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)) {
                        append("@${segment.rawText}")
                    }
                }
            } else {
                append(buildRawDescriptionAnnotatedString(segment.rawText, urlColor, linkListener))
            }
        }
    }
}

private fun buildRawDescriptionAnnotatedString(
    desc: String,
    urlColor: Color,
    linkListener: androidx.compose.ui.text.LinkInteractionListener?
): AnnotatedString {
    data class LinkMatch(
        val range: IntRange,
        val annotation: String,
        val displayText: String,
        val priority: Int
    )

    val matches = mutableListOf<LinkMatch>()
    VIDEO_DESCRIPTION_URL_PATTERN.findAll(desc).forEach { match ->
        matches += LinkMatch(
            range = match.range,
            annotation = match.value,
            displayText = match.value,
            priority = 0
        )
    }
    VIDEO_DESCRIPTION_INLINE_BVID_PATTERN.findAll(desc).forEach { match ->
        val overlapsUrl = matches.any { existing ->
            match.range.first <= existing.range.last && match.range.last >= existing.range.first
        }
        if (!overlapsUrl) {
            matches += LinkMatch(
                range = match.range,
                annotation = "https://www.bilibili.com/video/${match.value}",
                displayText = match.value,
                priority = 1
            )
        }
    }
    VIDEO_DESCRIPTION_TOPIC_PATTERN.findAll(desc).forEach { match ->
        val overlapsUrl = matches.any { existing ->
            match.range.first <= existing.range.last && match.range.last >= existing.range.first
        }
        if (!overlapsUrl) {
            val topic = match.groupValues[1].trim()
            if (topic.isNotEmpty()) {
                val encoded = java.net.URLEncoder.encode(topic, java.nio.charset.StandardCharsets.UTF_8.name())
                matches += LinkMatch(
                    range = match.range,
                    annotation = "bilibili://search?keyword=$encoded",
                    displayText = match.value,
                    priority = 2
                )
            }
        }
    }
    VIDEO_DESCRIPTION_MENTION_PATTERN.findAll(desc).forEach { match ->
        val overlapsUrl = matches.any { existing ->
            match.range.first <= existing.range.last && match.range.last >= existing.range.first
        }
        if (!overlapsUrl) {
            val mention = match.value.removePrefix("@").trim()
            if (mention.isNotEmpty()) {
                val encoded = java.net.URLEncoder.encode(mention, java.nio.charset.StandardCharsets.UTF_8.name())
                matches += LinkMatch(
                    range = match.range,
                    // desc_v2 缺失时拿不到 mid,回退到站内用户搜索页。
                    annotation = "https://search.bilibili.com/upuser?keyword=$encoded",
                    displayText = match.value,
                    priority = 2
                )
            }
        }
    }
    matches.sortWith(compareBy<LinkMatch> { it.range.first }.thenBy { it.priority })

    return buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { match ->
            if (lastIndex < match.range.first) {
                append(desc.substring(lastIndex, match.range.first))
            }
            withLink(
                androidx.compose.ui.text.LinkAnnotation.Clickable(
                    tag = match.annotation,
                    styles = null,
                    linkInteractionListener = linkListener,
                )
            ) {
                withStyle(SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)) {
                    append(match.displayText)
                }
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < desc.length) {
            append(desc.substring(lastIndex))
        }
    }
}

/**
 * Video Info Section Components
 *
 * Contains components for displaying video information:
 * - VideoTitleSection: Video title with expand/collapse
 * - VideoTitleWithDesc: Title + stats + description
 * - UpInfoSection: UP owner info with follow button
 * - DescriptionSection: Video description
 *
 * Requirement Reference: AC3.1 - Video info components in dedicated file
 */

internal fun resolveVideoInfoInitialExpandedState(
    hasDescription: Boolean,
    hasTags: Boolean,
    defaultExpanded: Boolean = false
): Boolean = defaultExpanded && (hasDescription || hasTags)

private const val BGM_DISCOVERY_LOAD_DELAY_MS = 420L
private const val BGM_RECOMMEND_PAGE_SIZE = 5
private const val BGM_RECOMMEND_ROW_START_INDEX = 4
/** 悬浮音频播放条的高度余量，避免底部面板内容被遮挡。 */
private const val AUDIO_NOW_PLAYING_BAR_CLEARANCE_DP = 64
private val BGM_DETAIL_CARD_MIN_HEIGHT = 168.dp

/**
 * Video Title Section (Bilibili official style: compact layout)
 */
@Composable
fun VideoTitleSection(
    info: ViewInfo,
    animateLayout: Boolean = true,
    onUpClick: (Long) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val publishTimeRowText = remember(info.pubdate, info.tname, info.title) {
        resolvePublishTimeRowText(
            pubdate = info.pubdate,
            partitionName = info.tname,
            title = info.title
        )
    }
    val emphasizePublishTime = remember(info.tname, info.title) {
        shouldEmphasizePrecisePublishTime(
            partitionName = info.tname,
            title = info.title
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Title row (expandable)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .then(if (animateLayout) Modifier.animateContentSize() else Modifier)
            ) {
                AppText(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier,
                    tapToCopyEnabled = false,
                )
            }
            Spacer(Modifier.width(4.dp))
            com.android.purebilibili.core.ui.components.AppIconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(40.dp),
            ) {
                AppIcon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起标题" else "展开标题",
                    tint = AppContentColors.secondaryText(),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Stats row (views, danmaku)
        AppText(
            text = "${FormatUtils.formatStat(info.stat.view.toLong())}  \u2022  ${FormatUtils.formatStat(info.stat.danmaku.toLong())}\u5f39\u5e55",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1
        )

        if (publishTimeRowText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            if (emphasizePublishTime) {
                AppSurface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                    shape = com.android.purebilibili.core.ui.AppShapes.container(
                        com.android.purebilibili.core.ui.ContainerLevel.Field
                    )
                ) {
                    AppText(
                        text = publishTimeRowText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                AppText(
                    text = publishTimeRowText,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Video Title with Description (Official layout: title + stats + description)
 *  Description and tags hidden by default, shown on expand
 */


/**
 * PiliPlus 风格的标题前缀徽标：盾牌+播放角标图标 + 类别文案（如“赞助/恰饭”）。
 */
@Composable
fun VideoDetailSponsorLabelChip(
    label: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    AppSurface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                AppIcon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp)
                )
            }
            AppText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                maxLines = maxLines
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun VideoTitleWithDesc(
    info: ViewInfo,
    videoTags: List<VideoTag> = emptyList(),  //  视频标签
    bgmList: List<BgmInfo> = emptyList(),
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    isQuickReturnLimitedForSharedElements: Boolean = false,
    sourceRouteForSharedElement: String? = null,
    animateLayout: Boolean = true,
    onDescriptionUrlClick: ((String) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    // PiliPlus 式标题前缀徽标（赞助/恰饭等），空串不展示
    sponsorLabel: String = "",
    showDescriptionAndTags: Boolean = true,
    expandedOverride: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    // 信息行末尾的紧凑入口插槽（AI 总结 / 视频笔记图标）
    trailingStatsContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val isMaterial3 = LocalAppUiStyle.current == AppUiStyle.MATERIAL3
    val horizontalPadding = if (isMaterial3) 16.dp else 12.dp
    val defaultExpanded by com.android.purebilibili.core.store.SettingsManager
        .getVideoInfoDefaultExpanded(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val argueMsgShown by com.android.purebilibili.core.store.SettingsManager
        .getVideoArgueMsgShown(context)
        .collectAsStateWithLifecycle(initialValue = true)
    // 展开状态改为“用户意图优先”：用户手动切换过之后，晚到的描述/标签数据不再重置它。
    // 原实现把 videoTags.size 放进 remember 的 key，标签异步回填时会重算初始值，
    // 既会覆盖用户的手动收起，也会让标签区重新播放展开动画并下推下方内容。
    var localExpanded by remember(info.bvid) { mutableStateOf<Boolean?>(null) }
    val expanded = expandedOverride
        ?: localExpanded
        ?: resolveVideoInfoInitialExpandedState(
            hasDescription = info.desc.isNotBlank(),
            hasTags = videoTags.isNotEmpty(),
            defaultExpanded = defaultExpanded
        )
    val toggleExpanded: () -> Unit = {
        val next = !expanded
        localExpanded = next
        onExpandedChange?.invoke(next)
    }
    val publishTimeRowText = remember(info.pubdate, info.tname, info.title) {
        resolvePublishTimeRowText(
            pubdate = info.pubdate,
            partitionName = info.tname,
            title = info.title
        )
    }
    val emphasizePublishTime = remember(info.tname, info.title) {
        shouldEmphasizePrecisePublishTime(
            partitionName = info.tname,
            title = info.title
        )
    }
    // PiliPlus 同款：信息行直接展示完整 yyyy-MM-dd HH:mm
    val fullPublishTimeText = remember(info.pubdate) {
        FormatUtils.formatPrecisePublishTime(timestampSeconds = info.pubdate)
    }
    val onlineCountText = remember(showOnlineCount, onlineCount) {
        resolveVideoDetailOnlineCountText(
            showOnlineCount = showOnlineCount,
            onlineCount = onlineCount
        )
    }
    val videoBadges = remember(info.isUpowerExclusive, info.isUpowerPreview, info.isCooperation) {
        resolveVideoDetailBadges(info)
    }

    //  尝试获取共享元素作用域
    val sharedTransitionScope = com.android.purebilibili.core.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope.current
    val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null
    )
    val useCardContainerSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRouteForSharedElement,
        transitionEnabled = coverSharedEnabled
    )
    val metadataSharedEnabled = shouldEnableVideoMetadataSharedTransition(
        coverSharedEnabled = coverSharedEnabled,
        isQuickReturnLimited = isQuickReturnLimitedForSharedElements,
        useCardContainerSharedBounds = useCardContainerSharedBounds
    )
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val metadataSharedTransitionMotionSpec = remember(
        metadataSharedEnabled,
        sharedTransitionSpeedSettings
    ) {
        resolveVideoMetadataSharedTransitionMotionSpec(
            transitionEnabled = metadataSharedEnabled,
            speedSettings = sharedTransitionSpeedSettings
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = horizontalPadding, vertical = if (isMaterial3) 4.dp else 3.dp)
    ) {
        val stackSponsorLabel = sponsorLabel.isNotBlank() && shouldStackSponsorLabelAboveTitle(sponsorLabel)
        if (stackSponsorLabel) {
            // 长徽标独立成行，避免挤压标题
            VideoDetailSponsorLabelChip(
                label = sponsorLabel,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        // Tap the full title row, including its spacing; long-press selection stays available.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "收起简介" else "展开简介",
                    onClick = toggleExpanded,
                )
                .padding(bottom = if (isMaterial3) 4.dp else 3.dp),
            verticalAlignment = Alignment.Top
        ) {
            //  共享元素过渡 - 标题
            var titleModifier = if (animateLayout) Modifier.animateContentSize() else Modifier

            //  注意：使用 ExperimentalSharedTransitionApi 注解需要上下文
            if (metadataSharedEnabled) {
                with(requireNotNull(sharedTransitionScope)) {
                     titleModifier = titleModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            key = com.android.purebilibili.core.ui.transition.videoTitleSharedElementKey(
                                info.bvid,
                                sourceRoute = sourceRouteForSharedElement
                            )
                        ),
                        animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                        boundsTransform = { initialBounds, targetBounds ->
                            videoMetadataSharedElementBoundsTransformSpec(
                                motion = metadataSharedTransitionMotionSpec,
                                initialBounds = initialBounds,
                                targetBounds = targetBounds
                            )
                        }
                    )
                }
            }

            if (sponsorLabel.isNotBlank() && !stackSponsorLabel) {
                VideoDetailSponsorLabelChip(
                    label = sponsorLabel,
                    modifier = Modifier.padding(end = 6.dp, top = 2.dp)
                )
            }
            SelectionContainer(modifier = Modifier.weight(1f)) {
                AppText(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    color = AppContentColors.primaryText(),
                    modifier = titleModifier,
                    tapToCopyEnabled = false,
                )
            }
        }

        val argueMsg = info.argueInfo?.argueMsg.orEmpty()
        val noReprint = info.rights.noReprint == 1
        val inlineDeclarations = argueMsgShown && (argueMsg.isNotBlank() || noReprint) &&
            !expanded && videoBadges.isEmpty() && info.honorReply?.honor.isNullOrEmpty()

        // Stats row
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f).clickable(
                onClickLabel = if (expanded) "收起简介" else "展开简介",
                onClick = toggleExpanded,
            ),
        ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            // Stats Row split for shared element transitions
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Views
                var viewsModifier = Modifier.wrapContentSize()
                if (metadataSharedEnabled) {
                    with(requireNotNull(sharedTransitionScope)) {
                        viewsModifier = viewsModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = com.android.purebilibili.core.ui.transition.videoViewsSharedElementKey(
                                    info.bvid,
                                    sourceRoute = sourceRouteForSharedElement
                                )
                            ),
                            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                            boundsTransform = { initialBounds, targetBounds ->
                                videoMetadataSharedElementBoundsTransformSpec(
                                    motion = metadataSharedTransitionMotionSpec,
                                    initialBounds = initialBounds,
                                    targetBounds = targetBounds
                                )
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = viewsModifier) {
                    AppIcon(
                        imageVector = Icons.Outlined.PlayCircleOutline,
                        contentDescription = null,
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    AppText(
                        text = FormatUtils.formatStat(info.stat.view.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Danmaku
                var danmakuModifier = Modifier.wrapContentSize()
                if (metadataSharedEnabled) {
                    with(requireNotNull(sharedTransitionScope)) {
                        danmakuModifier = danmakuModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = com.android.purebilibili.core.ui.transition.videoDanmakuSharedElementKey(
                                    info.bvid,
                                    sourceRoute = sourceRouteForSharedElement
                                )
                            ),
                            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                            boundsTransform = { initialBounds, targetBounds ->
                                videoMetadataSharedElementBoundsTransformSpec(
                                    motion = metadataSharedTransitionMotionSpec,
                                    initialBounds = initialBounds,
                                    targetBounds = targetBounds
                                )
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = danmakuModifier) {
                    AppIcon(
                        imageVector = Icons.Outlined.Subtitles,
                        contentDescription = null,
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    AppText(
                        text = FormatUtils.formatStat(info.stat.danmaku.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }

            }
            if (onlineCountText.isNotBlank()) {
                AppText(
                    text = onlineCountText,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.accent().copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (publishTimeRowText.isNotBlank()) {
                if (emphasizePublishTime) {
                    AppSurface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                        shape = com.android.purebilibili.core.ui.AppShapes.container(
                            com.android.purebilibili.core.ui.ContainerLevel.Field
                        )
                    ) {
                        AppText(
                            text = publishTimeRowText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = AppContentColors.secondaryText(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    AppText(
                        text = fullPublishTimeText.ifBlank { publishTimeRowText },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1
                    )
                }
            }
        }
        if (inlineDeclarations) {
            Spacer(Modifier.height(4.dp))
            if (argueMsg.isNotBlank()) VideoArgueMsgRow(argueMsg = argueMsg)
            if (argueMsg.isNotBlank() && noReprint) Spacer(Modifier.height(2.dp))
            if (noReprint) VideoArgueMsgRow(argueMsg = "未经作者授权，请勿转载")
        }
        }
        trailingStatsContent?.invoke()
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = if (animateLayout) {
                folmeExpandEnterTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.EnterTransition.None
            },
            exit = if (animateLayout) {
                folmeExpandExitTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.ExitTransition.None
            }
        ) {
            AppText(
                text = info.bvid,
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .copyOnClick(info.bvid, "BV号")
            )
        }

        if (videoBadges.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                videoBadges.forEach { badge ->
                    VideoDetailBadgeChip(
                        text = badge,
                        emphasized = badge.startsWith("充电专属")
                    )
                }
            }
        }

        // 视频荣誉徽标(全站排行榜/每周必看/入站必刷/热门):可点击跳转对应榜单页
        val honorChips = info.honorReply?.honor.orEmpty().mapNotNull { honor ->
            resolveVideoHonorChipText(
                type = honor.type,
                honorName = honor.honorName,
                descContent = honor.desc?.content,
                weeklyRecommendNum = honor.weeklyRecommendNum
            )?.let { text ->
                val jumpUrl = resolveVideoHonorJumpUrl(
                    type = honor.type,
                    honorUrl = honor.honorUrl,
                    weeklyRecommendNum = honor.weeklyRecommendNum,
                    honorText = "${honor.honorName} ${honor.desc?.content.orEmpty()}"
                ) ?: return@mapNotNull null
                Triple(honor, text, jumpUrl)
            }
        }
        if (honorChips.isNotEmpty()) {
            // 紧跟统计行/徽标区:上方无徽标时收紧到 3dp,避免与播放量行隔离太远。
            Spacer(Modifier.height(if (videoBadges.isNotEmpty()) 6.dp else 3.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                honorChips.forEach { (honor, text, jumpUrl) ->
                    VideoHonorChip(
                        text = text,
                        onClick = { onDescriptionUrlClick?.invoke(jumpUrl) }
                    )
                }
            }
        }

        // UP 主视频声明(PiliPlus argue_msg)+ 禁止转载(rights.no_reprint):
        // 声明小字置于 BGM 胶囊之上,与荣誉胶囊形成"胶囊区→声明区"的统一观感。
        if (!inlineDeclarations && argueMsgShown && (argueMsg.isNotBlank() || noReprint)) {
            Spacer(Modifier.height(4.dp))
            if (argueMsg.isNotBlank()) {
                VideoArgueMsgRow(argueMsg = argueMsg)
            }
            if (argueMsg.isNotBlank() && noReprint) {
                Spacer(Modifier.height(2.dp))
            }
            if (noReprint) {
                VideoArgueMsgRow(argueMsg = "未经作者授权，请勿转载")
            }
        }

        // [新增] BGM Info Row
        if (bgmList.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            InlineBgmSection(
                bgmList = bgmList,
                onBgmClick = onBgmClick,
                onRelatedVideoClick = onRelatedVideoClick
            )
        }

        if (showDescriptionAndTags) {
            VideoDescriptionAndTags(
                info = info,
                videoTags = videoTags,
                expanded = expanded,
                animateLayout = animateLayout,
                onDescriptionUrlClick = onDescriptionUrlClick,
                onBgmClick = onBgmClick,
                onTagClick = onTagClick,
            )
        }
    }
}

/** Shared rich description and tags; large-screen info panes also show a compact preview. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VideoDescriptionAndTags(
    info: ViewInfo,
    videoTags: List<VideoTag>,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    showCollapsedPreview: Boolean = false,
    animateLayout: Boolean = true,
    onDescriptionUrlClick: ((String) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onTagClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        //  Description - 默认隐藏，展开后显示
        androidx.compose.animation.AnimatedVisibility(
            visible = (expanded || showCollapsedPreview) && info.desc.isNotBlank(),
            enter = if (animateLayout) {
                folmeExpandEnterTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.EnterTransition.None
            },
            exit = if (animateLayout) {
                folmeExpandExitTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.ExitTransition.None
            }
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                val descriptionUrlColor = AppContentColors.accent()
                val descriptionLinkListener = remember(onDescriptionUrlClick) {
                    onDescriptionUrlClick?.let { handler ->
                        androidx.compose.ui.text.LinkInteractionListener { link ->
                            handler((link as androidx.compose.ui.text.LinkAnnotation.Clickable).tag)
                        }
                    }
                }
                val descriptionText = remember(
                    info.desc,
                    info.descV2,
                    descriptionUrlColor,
                    descriptionLinkListener
                ) {
                    buildVideoDescriptionAnnotatedString(
                        desc = info.desc,
                        descV2 = info.descV2,
                        urlColor = descriptionUrlColor,
                        linkListener = descriptionLinkListener
                    )
                }
                val descriptionModifier = if (animateLayout) {
                    Modifier.animateContentSize()
                } else {
                    Modifier
                }
                // 原生链接分发：链接点击在 Text 内部处理，与划选（SelectionContainer）
                // 和外层手势不再竞争，恢复无条件划选容器。
                SelectionContainer {
                    AppText(
                        text = descriptionText,
                        maxLines = if (expanded || !showCollapsedPreview) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = descriptionModifier
                    )
                }
            }
        }

        //  Tags - 默认隐藏，展开后显示
        androidx.compose.animation.AnimatedVisibility(
            visible = (expanded || showCollapsedPreview) && videoTags.isNotEmpty(),
            enter = if (animateLayout) {
                folmeExpandEnterTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.EnterTransition.None
            },
            exit = if (animateLayout) {
                folmeExpandExitTransition(useMiuixSpring)
            } else {
                androidx.compose.animation.ExitTransition.None
            }
        ) {
            val videoTagSize by com.android.purebilibili.core.store.SettingsManager
                .getVideoTagSizePreset(context)
                .collectAsStateWithLifecycle(
                    initialValue = com.android.purebilibili.core.ui.components.AppTagChipSize.STANDARD
                )
            val tagMetrics = com.android.purebilibili.core.ui.components
                .resolveAppTagChipMetrics(videoTagSize)
            Column {
                Spacer(Modifier.height(8.dp))
                // Keep native touch expansion without reserving a 48dp layout box per tag.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    androidx.compose.foundation.layout.FlowRow(
                        maxLines = if (expanded || !showCollapsedPreview) Int.MAX_VALUE else 2,
                        horizontalArrangement = Arrangement.spacedBy(tagMetrics.itemSpacingHorizontal),
                        verticalArrangement = Arrangement.spacedBy(tagMetrics.itemSpacingVertical)
                    ) {
                        videoTags.take(10).forEach { tag ->
                            com.android.purebilibili.core.ui.components.AppTagChip(
                                label = if (tag.tag_type == "bgm") tag.tag_name.replaceFirst("发现", "♫ BGM：") else tag.tag_name,
                                onClick = {
                                    val bgm = resolveBgmTagInfo(tag)
                                    if (bgm != null) onBgmClick(bgm) else onTagClick(tag.tag_name)
                                },
                                modifier = Modifier.copyOnLongPress(tag.tag_name, "标签"),
                                size = videoTagSize,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoDetailBadgeChip(
    text: String,
    emphasized: Boolean
) {
    com.android.purebilibili.core.ui.components.AppStatusBadge(
        label = text,
        emphasized = emphasized,
    )
}

/**
 * UP 主视频声明行(PiliPlus argue_msg 样式):
 * error_outline 小图标 + 12sp 次要色文本,如"虚构演绎,请勿过度解读"。
 */
@Composable
private fun VideoArgueMsgRow(argueMsg: String) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(13.dp)
        )
        AppText(
            text = argueMsg,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText()
        )
    }
}

/**
 * 视频荣誉徽标(全站排行榜最高第N名/每周必看等):
 * 着色小胶囊,有跳转链接时可点击,走通用的 B 站链接路由进入对应原生榜单页面。
 */
@Composable
private fun VideoHonorChip(
    text: String,
    onClick: (() -> Unit)? = null
) {
    AppSurface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * UP Owner Info Section (Bilibili official style: blue UP tag)
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun UpInfoSection(
    info: ViewInfo,
    isFollowing: Boolean = false,
    followStatusKnown: Boolean = false,
    onFollowClick: () -> Unit = {},
    onUpClick: (Long) -> Unit = {},
    showOwnerAvatar: Boolean = true,
    followerCount: Int? = null,
    videoCount: Int? = null,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    isQuickReturnLimitedForSharedElements: Boolean = false,
    sourceRouteForSharedElement: String? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val playerControlVisibility by com.android.purebilibili.core.store.SettingsManager
        .getPlayerControlVisibilitySettings(LocalContext.current)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerControlVisibilitySettings()
        )
    //  尝试获取共享元素作用域
    val sharedTransitionScope = com.android.purebilibili.core.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope.current
    val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null
    )
    val useCardContainerSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRouteForSharedElement,
        transitionEnabled = coverSharedEnabled
    )
    val metadataSharedEnabled = shouldEnableVideoMetadataSharedTransition(
        coverSharedEnabled = coverSharedEnabled,
        isQuickReturnLimited = isQuickReturnLimitedForSharedElements,
        useCardContainerSharedBounds = useCardContainerSharedBounds
    )
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val metadataSharedTransitionMotionSpec = remember(
        metadataSharedEnabled,
        sharedTransitionSpeedSettings
    ) {
        resolveVideoMetadataSharedTransitionMotionSpec(
            transitionEnabled = metadataSharedEnabled,
            speedSettings = sharedTransitionSpeedSettings
        )
    }
    // 统计是所有材质共有的详情信息，不调整 MD3、Miuix 或玻璃的原生控件参数。
    val upStatsLabels = resolveVideoDetailOwnerStatsLabels(
        followerCount = followerCount,
        videoCount = videoCount
    )
    val showInlineOwnerIdentity = shouldShowInlineOwnerIdentity(showOwnerAvatar = showOwnerAvatar)

    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth.isSpecified && shouldUseCompactUpInfoLayout(maxWidth.value.toInt())

        val avatarContent: @Composable () -> Unit = {
            if (showOwnerAvatar) {
                val avatarSize = if (isCompact) 32.dp else 35.dp
                var sharedFaceModifier: Modifier = Modifier
                if (metadataSharedEnabled) {
                    with(requireNotNull(sharedTransitionScope)) {
                        sharedFaceModifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = com.android.purebilibili.core.ui.transition.videoAvatarSharedElementKey(
                                    info.bvid,
                                    sourceRoute = sourceRouteForSharedElement
                                )
                            ),
                            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                            boundsTransform = { initialBounds, targetBounds ->
                                videoMetadataSharedElementBoundsTransformSpec(
                                    motion = metadataSharedTransitionMotionSpec,
                                    initialBounds = initialBounds,
                                    targetBounds = targetBounds
                                )
                            },
                            clipInOverlayDuringTransition = OverlayClip(CircleShape)
                        )
                    }
                }
                val ownerStaff = info.staff.firstOrNull { it.mid == info.owner.mid }

                if (info.owner.face.isNotBlank()) {
                    OwnerDecoratedAvatar(
                        faceUrl = info.owner.face,
                        ownerMid = info.owner.mid,
                        modifier = Modifier.size(avatarSize),
                        badgeSize = if (isCompact) 11.dp else 12.dp,
                        fallbackOfficialType = ownerStaff?.official?.type,
                        fallbackVipStatus = ownerStaff?.vip?.status,
                        faceModifier = sharedFaceModifier,
                        contentDescription = "UP主头像",
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(sharedFaceModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = "UP主标识",
                            tint = AppContentColors.accent(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                UserUpBadge()
            }
        }

        val upNameContent: @Composable (Modifier) -> Unit = { rowModifier ->
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var upNameModifier: Modifier = Modifier

                if (metadataSharedEnabled) {
                    with(requireNotNull(sharedTransitionScope)) {
                        upNameModifier = upNameModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = com.android.purebilibili.core.ui.transition.videoUpNameSharedElementKey(
                                    info.bvid,
                                    sourceRoute = sourceRouteForSharedElement
                                )
                            ),
                            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                            boundsTransform = { initialBounds, targetBounds ->
                                videoMetadataSharedElementBoundsTransformSpec(
                                    motion = metadataSharedTransitionMotionSpec,
                                    initialBounds = initialBounds,
                                    targetBounds = targetBounds
                                )
                            }
                        )
                    }
                }

                upNameModifier = upNameModifier.copyOnLongPress(info.owner.name, "UP主名称")

                if (showInlineOwnerIdentity) {
                    if (info.owner.face.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(info.owner.face))
                                .crossfade(true)
                                .build(),
                            contentDescription = "UP主头像",
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        UserUpBadge(modifier = Modifier.padding(horizontal = 2.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                val ownerStaff = info.staff.firstOrNull { it.mid == info.owner.mid }
                val fallbackVipStatus = ownerStaff?.vip?.status ?: 0
                val fallbackVipType = ownerStaff?.vip?.type ?: 0
                val onSurfaceColor = AppContentColors.primaryText()
                val secondaryColor = com.android.purebilibili.core.ui.components.userVipPink()
                val ownerNameColor by produceState<Color>(
                    initialValue = resolveUpNameColor(
                        vipStatus = fallbackVipStatus,
                        vipType = fallbackVipType,
                        onSurface = onSurfaceColor,
                        secondary = secondaryColor,
                    ),
                    key1 = info.owner.mid,
                ) {
                    val card = if (info.owner.mid > 0L) {
                        VideoRepository.getCreatorCardStats(info.owner.mid).getOrNull()
                    } else {
                        null
                    }
                    value = resolveUpNameColor(
                        vipStatus = card?.vipStatus ?: fallbackVipStatus,
                        vipType = card?.vipType ?: fallbackVipType,
                        onSurface = onSurfaceColor,
                        secondary = secondaryColor,
                    )
                }
                SelectionContainer {
                    AppText(
                        text = info.owner.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ownerNameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = upNameModifier
                    )
                }
            }
        }

        val followButtonContent: @Composable () -> Unit = {
            var followActionModifier = Modifier
                .heightIn(min = if (isCompact) 26.dp else 28.dp)
                .width(if (isCompact) 56.dp else 68.dp)
            if (metadataSharedEnabled) {
                with(requireNotNull(sharedTransitionScope)) {
                    followActionModifier = followActionModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            key = com.android.purebilibili.core.ui.transition.videoUpActionSharedElementKey(
                                info.bvid,
                                sourceRoute = sourceRouteForSharedElement
                            )
                        ),
                        animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                        boundsTransform = { initialBounds, targetBounds ->
                            videoMetadataSharedElementBoundsTransformSpec(
                                motion = metadataSharedTransitionMotionSpec,
                                initialBounds = initialBounds,
                                targetBounds = targetBounds
                            )
                        },
                        clipInOverlayDuringTransition = OverlayClip(VideoDetailShapes.action())
                    )
                }
            }

            val followDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val followVisualPolicy = remember(isFollowing, followDarkTheme) {
                resolveVideoFollowVisualPolicy(
                    isFollowing = isFollowing,
                    darkTheme = followDarkTheme,
                )
            }
            // 关注状态未确认时不再用空格+灰底占位：直接按“未关注”的主题配色渲染完整按钮，
            // 状态回填后仅用 Crossfade 切换文案，避免灰胶囊→蓝色按钮的硬跳与闪烁。
            AppSurface(
                onClick = onFollowClick,
                enabled = followStatusKnown,
                color = when (followVisualPolicy.detailButtonTone) {
                    FollowButtonTone.PRIMARY -> AppContentColors.accent()
                    FollowButtonTone.PRIMARY_CONTAINER -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = VideoDetailShapes.action(),
                modifier = followActionModifier.followActionAnchor()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 8.dp else 12.dp),
                ) {
                    Crossfade(
                        targetState = if (isFollowing) "\u5df2\u5173\u6ce8" else "\u5173\u6ce8",
                        animationSpec = tween(durationMillis = 180),
                        label = "videoDetailFollowLabel"
                    ) { followLabel ->
                        AppText(
                            text = followLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (followVisualPolicy.detailTextTone) {
                                FollowTextTone.ON_PRIMARY -> AppContentColors.onAccent()
                                FollowTextTone.ON_PRIMARY_CONTAINER -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCompact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpClick(info.owner.mid) }
                        .padding(horizontal = horizontalPadding, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    avatarContent()

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        upNameContent(Modifier.fillMaxWidth())

                        if (upStatsLabels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            OwnerStatsRow(labels = upStatsLabels)
                        }

                        if (playerControlVisibility.showFollowButton || trailingContent != null) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (playerControlVisibility.showFollowButton) {
                                    followButtonContent()
                                }
                                if (trailingContent != null) {
                                    trailingContent()
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpClick(info.owner.mid) }
                        .padding(horizontal = horizontalPadding, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    avatarContent()

                    Spacer(Modifier.width(10.dp))

                    // UP owner name row
                    Column(modifier = Modifier.weight(1f)) {
                        upNameContent(Modifier)
                        if (upStatsLabels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            OwnerStatsRow(labels = upStatsLabels)
                        }
                    }

                    if (playerControlVisibility.showFollowButton) {
                        followButtonContent()
                    }
                    if (trailingContent != null) {
                        if (playerControlVisibility.showFollowButton) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        trailingContent()
                    }
                }
            }
            if (shouldShowCreatorTeamSection(info)) {
                CreatorTeamSection(
                    staff = info.staff,
                    ownerMid = info.owner.mid,
                    onMemberClick = onUpClick
                )
            }
        }
    }
}

@Composable
private fun CreatorTeamSection(
    staff: List<VideoStaff>,
    ownerMid: Long,
    onMemberClick: (Long) -> Unit
) {
    if (staff.isEmpty()) return
    // 每个成员的关注状态:null=查询中;经 followStateChanges 与全局动作同步。
    val followStates = remember(staff) { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(staff) {
        staff.filter { it.mid > 0L && it.mid != ownerMid }.forEach { member ->
            followStates[member.mid] =
                com.android.purebilibili.data.repository.ActionRepository
                    .checkFollowStatus(member.mid)
        }
    }
    LaunchedEffect(Unit) {
        com.android.purebilibili.data.repository.ActionRepository.followStateChanges.collect { change ->
            if (followStates.containsKey(change.mid)) {
                followStates[change.mid] = change.isFollowing
            }
        }
    }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "创作团队",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText()
            )
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = "共 ${staff.size} 位",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            staff.forEach { member ->
                CreatorTeamMemberChip(
                    member = member,
                    showFollow = member.mid > 0L && member.mid != ownerMid,
                    isFollowing = followStates[member.mid] ?: false,
                    onFollowToggle = {
                        scope.launch {
                            val target = !(followStates[member.mid] ?: false)
                            val ok = com.android.purebilibili.data.repository.ActionRepository
                                .followUser(member.mid, target)
                                .getOrDefault(false)
                            if (ok) followStates[member.mid] = target
                        }
                    },
                    onClick = { onMemberClick(member.mid) }
                )
            }
        }
    }
}

@Composable
private fun CreatorTeamMemberChip(
    member: VideoStaff,
    showFollow: Boolean,
    isFollowing: Boolean,
    onFollowToggle: () -> Unit,
    onClick: () -> Unit
) {
    val followDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val followVisualPolicy = remember(isFollowing, followDarkTheme) {
        resolveVideoFollowVisualPolicy(
            isFollowing = isFollowing,
            darkTheme = followDarkTheme,
        )
    }
    val officialBadge = remember(member.official) {
        resolveOfficialVerifyBadgeFromRole(
            type = member.official.type,
            role = member.official.role,
            title = member.official.title,
            desc = member.official.desc,
            compact = true
        )
    }
    Row(
        modifier = Modifier
            .clip(AppShapes.container(ContainerLevel.Chip))
            .clickable(enabled = member.mid > 0L, onClick = onClick)
            .padding(end = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(member.face))
                    .crossfade(true)
                    .build(),
                contentDescription = "${member.name} 头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            UserAvatarCornerMarkBadge(
                mark = resolveUserAvatarCornerMark(
                    officialType = member.official.type,
                    vipStatus = member.vip.status,
                ),
                modifier = Modifier.align(Alignment.BottomEnd),
                badgeSize = 14.dp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.widthIn(min = 64.dp, max = 112.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppText(
                    text = member.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (officialBadge != null) {
                    OfficialVerifyBadge(
                        badge = officialBadge,
                        compact = true
                    )
                }
            }
            if (member.title.isNotBlank()) {
                AppText(
                    text = member.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showFollow) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onFollowToggle),
                contentAlignment = Alignment.Center
            ) {
                AppSurface(
                    color = when (followVisualPolicy.detailButtonTone) {
                        FollowButtonTone.PRIMARY -> AppContentColors.accent()
                        FollowButtonTone.PRIMARY_CONTAINER -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = VideoDetailShapes.action(),
                    modifier = Modifier.heightIn(min = 28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        AppText(
                            text = if (isFollowing) "已关注" else "关注",
                            style = MaterialTheme.typography.labelMedium,
                            color = when (followVisualPolicy.detailTextTone) {
                                FollowTextTone.ON_PRIMARY -> AppContentColors.onAccent()
                                FollowTextTone.ON_PRIMARY_CONTAINER -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Description Section (optimized style)
 */
@Composable
fun DescriptionSection(desc: String) {
    var expanded by remember { mutableStateOf(false) }

    if (desc.isBlank()) return

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize()
        ) {
            SelectionContainer {
                AppText(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (desc.length > 100 || desc.lines().size > 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = if (expanded) "\u6536\u8d77" else "\u5c55\u5f00\u66f4\u591a",
                        color = AppContentColors.accent(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    AppIcon(
                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineBgmSection(
    bgmList: List<BgmInfo>,
    onBgmClick: (BgmInfo) -> Unit = {},
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> }
) {
    if (bgmList.isEmpty()) return

    var showSheet by remember(bgmList.map(BgmInfo::musicId)) { mutableStateOf(false) }
    val leadSong = bgmList.first()
    val headerText = remember(bgmList, leadSong) {
        buildString {
            append("发现音乐")
            val title = leadSong.musicTitle.ifBlank { "未知音乐" }
            append("《")
            append(title)
            append("》")
            if (bgmList.size > 1) {
                append("等")
                append(bgmList.size)
                append("首音乐")
            }
            // PiliPlus 式单行：艺人内联，避免双行卡片
            val actor = leadSong.actor.takeIf { it.isNotBlank() && bgmList.size == 1 }
            if (actor != null) {
                append(" · ")
                append(actor)
            }
        }
    }

    BgmInfoRow(
        title = headerText,
        subtitle = null,
        showIndicator = false,
        onClick = {
            if (bgmList.size == 1) onBgmClick(leadSong) else showSheet = true
        }
    )

    if (showSheet) {
        BgmSelectionSheet(
            title = "发现音乐",
            bgmList = bgmList,
            onDismiss = { showSheet = false },
            onBgmClick = { bgm ->
                showSheet = false
                onBgmClick(bgm)
            },
            onRelatedVideoClick = onRelatedVideoClick
        )
    }
}

/**
 * [新增] 背景音乐信息行
 */
@Composable
fun BgmInfoRow(
    title: String,
    subtitle: String? = null,
    expanded: Boolean = false,
    showIndicator: Boolean = false,
    onClick: () -> Unit = {}
) {
    val indicatorRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "BgmExpandIndicator"
    )

    AppSurface(
        color = AppContentColors.accent().copy(alpha = 0.08f),
        shape = AppShapes.container(ContainerLevel.Chip),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "BGM",
                tint = AppContentColors.accent().copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = AppContentColors.accent().copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showIndicator) {
                AppIcon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppContentColors.accent().copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(indicatorRotation)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BgmSelectionSheet(
    title: String,
    bgmList: List<BgmInfo>,
    onDismiss: () -> Unit,
    onBgmClick: (BgmInfo) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val itemKeys = remember(bgmList) {
        bgmList.mapIndexed(::resolveBgmItemKey)
    }
    val aid = remember(bgmList) {
        bgmList.firstOrNull()?.jumpUrl?.let { resolveQueryLongParam(it, "aid") } ?: 0L
    }
    val cid = remember(bgmList) {
        bgmList.firstOrNull()?.jumpUrl?.let { resolveQueryLongParam(it, "cid") } ?: 0L
    }
    val itemStateByKey = remember(itemKeys) {
        mutableStateMapOf<String, BgmSheetItemState>().also { map ->
            itemKeys.forEach { key -> map[key] = BgmSheetItemState() }
        }
    }
    val listState = rememberLazyListState()
    var selectedItemKey by remember(itemKeys) {
        mutableStateOf(itemKeys.firstOrNull().orEmpty())
    }
    val selectedIndex = remember(selectedItemKey, itemKeys) {
        itemKeys.indexOf(selectedItemKey).takeIf { it >= 0 } ?: 0
    }
    val selectedBgm = bgmList.getOrElse(selectedIndex) { bgmList.first() }
    val selectedData = itemStateByKey[selectedItemKey] ?: BgmSheetItemState()
    val shouldShowDetailSkeleton = remember(selectedData) {
        shouldShowBgmDetailSkeleton(selectedData)
    }

    LaunchedEffect(selectedItemKey, selectedBgm.musicId, aid, cid) {
        if (!shouldLoadBgmDiscoveryItem(selectedData, selectedBgm.musicId, aid, cid)) return@LaunchedEffect

        delay(BGM_DISCOVERY_LOAD_DELAY_MS)
        itemStateByKey[selectedItemKey] = selectedData.copy(
            status = BgmDiscoveryLoadStatus.Loading,
            errorMessage = null,
            isAppendingRecommendations = false
        )

        val detail = ViewGrpcRepository.getBgmDetail(
            musicId = selectedBgm.musicId,
            aid = aid,
            cid = cid
        )
        val recommendedVideos = ViewGrpcRepository.getBgmRecommendVideos(
            musicId = selectedBgm.musicId,
            aid = aid,
            cid = cid,
            page = 1,
            pageSize = BGM_RECOMMEND_PAGE_SIZE
        )
        val loadedDetail = detail.getOrNull()
        val loadedVideos = recommendedVideos.getOrDefault(emptyList())
        val errorMessage = detail.exceptionOrNull()?.message
            ?: recommendedVideos.exceptionOrNull()?.message

        itemStateByKey[selectedItemKey] = BgmSheetItemState(
            status = if (detail.isSuccess || recommendedVideos.isSuccess) {
                BgmDiscoveryLoadStatus.Loaded
            } else {
                BgmDiscoveryLoadStatus.Error
            },
            detail = loadedDetail,
            recommendedVideos = loadedVideos,
            errorMessage = errorMessage,
            nextRecommendPage = if (recommendedVideos.isSuccess) 2 else 1,
            hasMoreRecommendations = if (recommendedVideos.isSuccess) {
                loadedVideos.size >= BGM_RECOMMEND_PAGE_SIZE
            } else {
                true
            },
            isAppendingRecommendations = false
        )
    }

    val selectedMusicId = selectedBgm.musicId
    val selectedStatLine = remember(selectedData.detail) {
        resolveBgmStatLine(selectedData.detail)
    }
    val shouldShowInitialRecommendationPlaceholders = remember(selectedData) {
        shouldShowInitialBgmRecommendationPlaceholders(selectedData)
    }
    val recommendedVideoRows = remember(selectedData.recommendedVideos) {
        selectedData.recommendedVideos.chunked(2)
    }

    com.android.purebilibili.core.ui.AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f),
            // 底部额外预留系统导航栏与悬浮播放条的高度，
            // 避免「使用该音乐的视频」最后一张卡片被遮挡。
            contentPadding = PaddingValues(
                bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + AUDIO_NOW_PLAYING_BAR_CLEARANCE_DP.dp
            )
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppIconButton(onClick = onDismiss) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (bgmList.size > 1) {
                item {
                    BgmSelectionStrip(
                        bgmList = bgmList,
                        itemKeys = itemKeys,
                        selectedItemKey = selectedItemKey,
                        onSelect = { selectedItemKey = it }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                BgmDetailCard(
                    bgm = selectedBgm,
                    detail = selectedData.detail,
                    isLoading = shouldShowDetailSkeleton,
                    statLine = selectedStatLine,
                    onOpenMusic = { onBgmClick(selectedBgm) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                BgmDiscoveryRelatedHeader()
            }

            if (shouldShowInitialRecommendationPlaceholders) {
                repeat((BGM_RECOMMEND_PAGE_SIZE + 1) / 2) { placeholderRowIndex ->
                    item(key = "bgm-recommend-placeholder-row-$placeholderRowIndex") {
                        BgmRecommendVideoSkeletonRow(indexBase = placeholderRowIndex * 2)
                    }
                }
            } else if (selectedData.recommendedVideos.isNotEmpty()) {
                itemsIndexed(
                    items = recommendedVideoRows,
                    key = { rowIndex, videos -> resolveBgmRecommendRowKey(rowIndex, videos) }
                ) { rowIndex, rowVideos ->
                    BgmRecommendVideoCardRow(
                        rowVideos = rowVideos,
                        rowIndex = rowIndex,
                        onVideoClick = { video ->
                            onDismiss()
                            onRelatedVideoClick(
                                video.bvid,
                                buildVideoNavigationOptions(targetCid = video.cid)
                            )
                        }
                    )
                }
            } else if (selectedData.errorMessage?.isNotBlank() == true) {
                item(key = "bgm-recommend-error") {
                    AppText(
                        text = selectedData.errorMessage.takeIf { it.isNotBlank() } ?: "音乐推荐加载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                item(key = "bgm-recommend-empty") {
                    AppText(
                        text = "暂无相关视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (selectedData.isAppendingRecommendations) {
                item(key = "bgm-recommend-loading-more") {
                    BgmRecommendVideoSkeletonRow(indexBase = recommendedVideoRows.size * 2)
                }
            }

            if (selectedMusicId.isBlank() || aid <= 0L || cid <= 0L) {
                item {
                    AppText(
                        text = "暂时无法加载更多音乐信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedItemKey) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(selectedItemKey, selectedMusicId, aid, cid) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to layoutInfo.totalItemsCount
        }.collect { (lastVisibleIndex, _) ->
            val currentState = itemStateByKey[selectedItemKey] ?: return@collect
            if (!shouldLoadMoreBgmRecommendations(currentState, selectedMusicId, aid, cid)) return@collect

            val lastRecommendationIndex =
                resolveBgmRecommendRowItemIndex((currentState.recommendedVideos.size - 1) / 2)
            if (lastVisibleIndex < lastRecommendationIndex - 1) return@collect

            loadMoreBgmRecommendations(
                itemStateByKey = itemStateByKey,
                itemKey = selectedItemKey,
                bgm = selectedBgm,
                aid = aid,
                cid = cid
            )
        }
    }
}

@Composable
private fun BgmDiscoveryRelatedHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        AppText(
            text = "使用该音乐的视频",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppContentColors.primaryText()
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun BgmRecommendVideoCardRow(
    rowVideos: List<BgmRecommendVideo>,
    rowIndex: Int,
    onVideoClick: (BgmRecommendVideo) -> Unit
) {
    val context = LocalContext.current
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
    val cardLayout = remember(homeFeedCardStyle) {
        resolveHomeFeedCardLayout(homeFeedCardStyle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardLayout.outerPaddingDp.dp),
        horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp)
    ) {
        rowVideos.forEachIndexed { columnIndex, video ->
            ElegantVideoCard(
                video = bgmRecommendVideoToVideoItem(video),
                index = rowIndex * 2 + columnIndex,
                animationEnabled = false,
                showPublishTime = true,
                isDataSaverActive = true,
                preferLowQualityCover = true,
                coverAspectRatio = cardLayout.coverAspectRatio,
                compactMetadata = cardLayout.compactMetadata,
                modifier = Modifier.weight(1f),
                onClick = { _, _ ->
                    onVideoClick(video)
                }
            )
        }
        if (rowVideos.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BgmRecommendVideoSkeletonRow(
    indexBase: Int
) {
    val context = LocalContext.current
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
    val cardLayout = remember(homeFeedCardStyle) {
        resolveHomeFeedCardLayout(homeFeedCardStyle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardLayout.outerPaddingDp.dp),
        horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp)
    ) {
        VideoCardSkeleton(
            modifier = Modifier.weight(1f),
            index = indexBase,
            coverAspectRatio = cardLayout.coverAspectRatio
        )
        VideoCardSkeleton(
            modifier = Modifier.weight(1f),
            index = indexBase + 1,
            coverAspectRatio = cardLayout.coverAspectRatio
        )
    }
}

private fun bgmRecommendVideoToVideoItem(video: BgmRecommendVideo): VideoItem {
    return VideoItem(
        id = if (video.aid > 0L) video.aid else video.cid,
        bvid = video.bvid,
        aid = video.aid,
        cid = video.cid,
        title = video.title,
        pic = video.cover,
        owner = Owner(
            mid = video.mid,
            name = video.upNickName
        ),
        stat = Stat(
            view = video.play,
            danmaku = 0
        ),
        duration = video.duration
    )
}

@Composable
private fun BgmSelectionStrip(
    bgmList: List<BgmInfo>,
    itemKeys: List<String>,
    selectedItemKey: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverSizePx = remember(density) { with(density) { 76.dp.roundToPx() } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(bgmList, key = { index, bgm -> itemKeys.getOrElse(index) { resolveBgmItemKey(index, bgm) } }) { index, bgm ->
            val itemKey = itemKeys.getOrElse(index) { resolveBgmItemKey(index, bgm) }
            val selected = itemKey == selectedItemKey
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clickable { onSelect(itemKey) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(AppShapes.container(ContainerLevel.Floating))
                        .background(
                            if (selected) {
                                AppContentColors.accent().copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            }
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) {
                                AppContentColors.accent().copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                            },
                            shape = AppShapes.container(ContainerLevel.Floating)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bgm.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(FormatUtils.fixImageUrl(bgm.coverUrl))
                                .size(coverSizePx, coverSizePx)
                                .crossfade(false)
                                .build(),
                            contentDescription = bgm.musicTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AppIcon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = AppContentColors.accent().copy(alpha = 0.78f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = bgm.musicTitle.ifBlank { "未知音乐" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        AppContentColors.accent()
                    } else {
                        AppContentColors.primaryText()
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun BgmDetailCard(
    bgm: BgmInfo,
    detail: BgmDetailData?,
    isLoading: Boolean,
    statLine: String?,
    onOpenMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreText = remember(detail) { resolveBgmScoreText(detail) }
    val displayStatLine = remember(statLine) { statLine ?: resolveUnavailableBgmStatLine() }
    val commentText = remember(detail) { resolveBgmCommentText(detail) }
    val description = remember(detail, bgm) {
        bgm.actor.takeIf { it.isNotBlank() }
            ?: detail?.originArtist?.takeIf { it.isNotBlank() }
            ?: "点击查看这首音乐的完整详情"
    }

    AppSurface(
        onClick = onOpenMusic,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Floating),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        if (isLoading) {
            BgmDetailCardSkeleton()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BGM_DETAIL_CARD_MIN_HEIGHT)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                BgmDetailCover(
                    coverUrl = detail?.mvCover.orEmpty().ifBlank { bgm.coverUrl },
                    title = detail?.musicTitle.orEmpty().ifBlank { bgm.musicTitle }
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top
                ) {
                    AppText(
                        text = detail?.musicTitle.orEmpty().ifBlank { bgm.musicTitle.ifBlank { "未知音乐" } },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = AppContentColors.accent(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AppText(
                            text = scoreText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppContentColors.primaryText()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = displayStatLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = commentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The action follows natural content height instead of receiving only the
                    // remaining pixels of a fixed-height card when the title/artist wraps.
                    Spacer(modifier = Modifier.height(12.dp))
                    AppText(
                        text = "打开音乐详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenMusic)
                    )
                }
            }
        }
    }
}

@Composable
private fun BgmDetailCardSkeleton() {
    ShimmerContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BGM_DETAIL_CARD_MIN_HEIGHT)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(
                modifier = Modifier.size(112.dp),
                height = 112.dp,
                cornerRadius = 22.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.72f),
                    height = 20.dp,
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.46f),
                    height = 16.dp,
                    cornerRadius = 8.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    height = 14.dp,
                    cornerRadius = 7.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.34f),
                    height = 14.dp,
                    cornerRadius = 7.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.94f),
                    height = 14.dp,
                    cornerRadius = 7.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.66f),
                    height = 14.dp,
                    cornerRadius = 7.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                SkeletonBox(
                    modifier = Modifier.width(84.dp),
                    height = 14.dp,
                    cornerRadius = 7.dp
                )
            }
        }
    }
}

@Composable
private fun BgmDetailCover(
    coverUrl: String,
    title: String
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverSizePx = remember(density) { with(density) { 112.dp.roundToPx() } }
    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 112.dp)
            .clip(AppShapes.container(ContainerLevel.Floating))
            .background(AppContentColors.accent().copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(FormatUtils.fixImageUrl(coverUrl))
                    .size(coverSizePx, coverSizePx)
                    .crossfade(false)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AppIcon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = title,
                tint = AppContentColors.accent().copy(alpha = 0.8f),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

private fun resolveBgmScoreText(detail: BgmDetailData?): String {
    detail ?: return "音乐信息"
    return when {
        detail.musicHot > 0L -> "最新热度 ${FormatUtils.formatStat(detail.musicHot)}"
        else -> "播放 ${FormatUtils.formatStat(detail.listenPv)}"
    }
}

private fun resolveBgmStatLine(detail: BgmDetailData?): String? {
    detail ?: return null
    return listOf(
        "${FormatUtils.formatStat(detail.listenPv.coerceAtLeast(0L))}播放",
        "${FormatUtils.formatStat(detail.wishCount.coerceAtLeast(0).toLong())}想听",
        "${FormatUtils.formatStat(detail.musicShares.coerceAtLeast(0).toLong())}分享"
    ).joinToString(" · ")
}

private fun resolveUnavailableBgmStatLine(): String {
    return "--播放 · --想听 · --分享"
}

private fun resolveBgmCommentText(detail: BgmDetailData?): String {
    detail ?: return "--评论"
    return "${FormatUtils.formatStat((detail.musicComment?.nums ?: 0).coerceAtLeast(0).toLong())}评论"
}

private fun resolveBgmRecommendRowKey(
    rowIndex: Int,
    videos: List<BgmRecommendVideo>
): String {
    val stablePart = videos.joinToString(separator = "|") { video ->
        video.bvid.ifBlank { "${video.aid}:${video.cid}:${video.title}" }
    }
    return "bgm-recommend-row-$rowIndex:$stablePart"
}

private fun resolveBgmRecommendRowItemIndex(rowIndex: Int): Int {
    return BGM_RECOMMEND_ROW_START_INDEX + rowIndex
}

internal fun resolveBgmTagInfo(tag: com.android.purebilibili.data.model.response.VideoTag): BgmInfo? =
    if (tag.tag_type == "bgm" && (tag.music_id.isNotBlank() || tag.jump_url.isNotBlank())) {
        BgmInfo(musicId = tag.music_id, musicTitle = tag.tag_name, jumpUrl = tag.jump_url, coverUrl = tag.cover)
    } else null

internal fun resolveDisplayBgmList(
    bgmInfo: BgmInfo?,
    bgmInfoList: List<BgmInfo>
): List<BgmInfo> {
    return bgmInfoList.ifEmpty {
        listOfNotNull(bgmInfo)
    }
}

private fun resolveQueryLongParam(url: String, key: String): Long {
    return android.net.Uri.parse(url).getQueryParameter(key)?.toLongOrNull() ?: 0L
}

internal enum class BgmDiscoveryLoadStatus {
    Idle,
    Loading,
    Loaded,
    Error
}

internal data class BgmSheetItemState(
    val status: BgmDiscoveryLoadStatus = BgmDiscoveryLoadStatus.Idle,
    val detail: BgmDetailData? = null,
    val recommendedVideos: List<BgmRecommendVideo> = emptyList(),
    val errorMessage: String? = null,
    val nextRecommendPage: Int = 2,
    val hasMoreRecommendations: Boolean = true,
    val isAppendingRecommendations: Boolean = false
)

internal fun resolveBgmItemKey(index: Int, bgm: BgmInfo): String {
    val stablePart = bgm.musicId
        .trim()
        .ifBlank {
            bgm.jumpUrl.trim().ifBlank {
                bgm.musicTitle.trim().ifBlank { "unknown" }
            }
        }
    return "$index:$stablePart"
}

internal fun shouldLoadBgmDiscoveryItem(
    state: BgmSheetItemState,
    musicId: String,
    aid: Long,
    cid: Long
): Boolean {
    if (musicId.isBlank() || aid <= 0L || cid <= 0L) return false
    return state.status == BgmDiscoveryLoadStatus.Idle ||
        state.status == BgmDiscoveryLoadStatus.Error
}

internal fun shouldShowBgmDetailSkeleton(
    state: BgmSheetItemState
): Boolean {
    return state.status != BgmDiscoveryLoadStatus.Loaded && state.detail == null
}

internal fun shouldShowInitialBgmRecommendationPlaceholders(
    state: BgmSheetItemState
): Boolean {
    return state.recommendedVideos.isEmpty() &&
        state.status != BgmDiscoveryLoadStatus.Error
}

internal fun shouldLoadMoreBgmRecommendations(
    state: BgmSheetItemState,
    musicId: String,
    aid: Long,
    cid: Long
): Boolean {
    if (musicId.isBlank() || aid <= 0L || cid <= 0L) return false
    if (state.status == BgmDiscoveryLoadStatus.Loading) return false
    if (state.isAppendingRecommendations) return false
    if (!state.hasMoreRecommendations) return false
    return state.recommendedVideos.isNotEmpty()
}

private suspend fun loadMoreBgmRecommendations(
    itemStateByKey: MutableMap<String, BgmSheetItemState>,
    itemKey: String,
    bgm: BgmInfo,
    aid: Long,
    cid: Long
) {
    val currentState = itemStateByKey[itemKey] ?: return
    if (!shouldLoadMoreBgmRecommendations(currentState, bgm.musicId, aid, cid)) return

    itemStateByKey[itemKey] = currentState.copy(
        isAppendingRecommendations = true,
        errorMessage = null
    )

    val result = ViewGrpcRepository.getBgmRecommendVideos(
        musicId = bgm.musicId,
        aid = aid,
        cid = cid,
        page = currentState.nextRecommendPage,
        pageSize = BGM_RECOMMEND_PAGE_SIZE
    )
    val incomingVideos = result.getOrDefault(emptyList())
    val mergedVideos = mergeBgmRecommendedVideos(
        existing = currentState.recommendedVideos,
        incoming = incomingVideos
    )
    val appendedCount = mergedVideos.size - currentState.recommendedVideos.size

    val latestState = itemStateByKey[itemKey] ?: currentState
    itemStateByKey[itemKey] = latestState.copy(
        recommendedVideos = if (result.isSuccess) mergedVideos else latestState.recommendedVideos,
        errorMessage = result.exceptionOrNull()?.message,
        nextRecommendPage = if (result.isSuccess && appendedCount > 0) {
            currentState.nextRecommendPage + 1
        } else {
            currentState.nextRecommendPage
        },
        hasMoreRecommendations = if (result.isSuccess) {
            incomingVideos.size >= BGM_RECOMMEND_PAGE_SIZE && appendedCount > 0
        } else {
            latestState.hasMoreRecommendations
        },
        isAppendingRecommendations = false
    )
}

private fun mergeBgmRecommendedVideos(
    existing: List<BgmRecommendVideo>,
    incoming: List<BgmRecommendVideo>
): List<BgmRecommendVideo> {
    if (incoming.isEmpty()) return existing
    val seenKeys = existing.mapTo(mutableSetOf()) { video ->
        if (video.bvid.isNotBlank()) video.bvid else "${video.aid}:${video.cid}"
    }
    val appended = incoming.filter { video ->
        val key = if (video.bvid.isNotBlank()) video.bvid else "${video.aid}:${video.cid}"
        seenKeys.add(key)
    }
    return existing + appended
}

/** 数量与标签跟随主题字体，项间距使用布局单位，不依赖空格宽度。 */
@Composable
private fun OwnerStatsRow(labels: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            AppText(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
