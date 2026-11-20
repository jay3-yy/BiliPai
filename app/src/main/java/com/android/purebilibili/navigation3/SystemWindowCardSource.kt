package com.android.purebilibili.navigation3

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.navigationReturnLanding
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.CardPositionManager
import kotlin.math.roundToInt

/**
 * 系统一镜到底窗口来源：内容卡片点击时向视频转场管线登记几何，
 * 与视频卡片共用锚点、像素归属与返回落点。identity 不含冒号，与 route:identity 来源键规则兼容。
 */
internal class SystemWindowCardSource internal constructor(
    val identity: String,
    private val sourceRoute: String?,
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    private val densityValue: Float,
    internal val neighbor: SystemWindowControlNeighbor,
) {
    var coordinates: androidx.compose.ui.layout.LayoutCoordinates? = null

    fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()

    /** 点击时先于路由跳转调用；未登记成功时调用方继续原窗口内导航。 */
    fun record(cornerRadiusPx: Float = 0f, layer: GraphicsLayer? = null): Boolean {
        val bounds = boundsInRoot() ?: return false
        SystemWindowControlNeighbors.beginGroupNavigation(neighbor)
        CardPositionManager.recordVideoCardPosition(
            bvid = identity,
            sourceRoute = sourceRoute,
            bounds = bounds,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            density = densityValue,
            sourceCornerDp = cornerRadiusPx.takeIf { it > 0f }
                ?.let { (it / densityValue).roundToInt() },
        )
        // 登记当前点击自己的整卡像素，视口裁切时不从窗口截图补齐被遮挡区域。
        CardPositionManager.recordNativeCardLayer(
            layer?.takeIf { !it.isReleased && it.size == coordinates?.size },
        )
        return true
    }
}

/** 窗口可承接的容器目标键；其余键返回 null，调用方保持原窗口内导航。 */
internal fun resolveSystemWindowCardIdentity(target: BiliPaiNavKey?): String? = when (target) {
    BiliPaiNavKey.Settings -> "settings"
    BiliPaiNavKey.SettingsSearch -> "settings_search"
    BiliPaiNavKey.Inbox -> "inbox"
    BiliPaiNavKey.Profile -> "profile"
    is BiliPaiNavKey.Search -> "search"
    BiliPaiNavKey.SearchTrending -> "search_trending"
    is BiliPaiNavKey.WeeklySeries -> "weekly_series_${target.number ?: 0}"
    BiliPaiNavKey.LiveSearch -> "live_search"
    BiliPaiNavKey.LiveArea -> "live_area"
    BiliPaiNavKey.LiveFollowing -> "live_following"
    BiliPaiNavKey.History -> "history"
    BiliPaiNavKey.Favorite -> "favorite"
    BiliPaiNavKey.FavoriteSubscribed -> "favorite_subscribed"
    BiliPaiNavKey.WatchLater -> "watch_later"
    BiliPaiNavKey.DownloadList -> "download_list"
    is BiliPaiNavKey.OfflineVideoPlayer -> "offline_video_${target.taskId}"
    BiliPaiNavKey.Partition -> "partition"
    BiliPaiNavKey.ReplyMe -> "reply_me"
    BiliPaiNavKey.AtMe -> "at_me"
    BiliPaiNavKey.LikeMe -> "like_me"
    BiliPaiNavKey.SystemNotice -> "system_notice"
    is BiliPaiNavKey.Chat -> target.talkerId.takeIf { it > 0L }?.let { "chat_${target.sessionType}_$it" }
    is BiliPaiNavKey.Following -> target.mid.takeIf { it > 0L }?.let { "following_${it}_${target.fans}" }
    is BiliPaiNavKey.TopicDetail -> target.topicId.takeIf { it > 0L }?.let { "topic_$it" }
    is BiliPaiNavKey.MatchInfo -> target.cid.takeIf { it > 0L }?.let { "match_$it" }
    is BiliPaiNavKey.SeasonSeriesDetail -> "series_${target.type}_${target.id}_${target.mid}"
    is BiliPaiNavKey.AudioMode -> target.sourceBvid.takeIf(String::isNotBlank)
    is BiliPaiNavKey.VideoDetail -> target.bvid.takeIf(String::isNotBlank)
    is BiliPaiNavKey.Live -> "live_${target.roomId}"
    is BiliPaiNavKey.BangumiPlayer -> "bangumi_${target.seasonId}"
    is BiliPaiNavKey.BangumiDetail -> "bangumi_${target.seasonId}"
    is BiliPaiNavKey.BangumiReview -> target.mediaId.takeIf { it > 0L }?.let { "bangumi_review_$it" }
    is BiliPaiNavKey.Bangumi -> "bangumi_library_${target.initialType}"
    is BiliPaiNavKey.ArticleDetail -> "article_${target.articleId}"
    is BiliPaiNavKey.ArticleCollection -> target.collectionId.takeIf { it > 0L }?.let { "article_collection_$it" }
    is BiliPaiNavKey.DynamicDetail -> "dynamic_${target.dynamicId}"
    is BiliPaiNavKey.Space -> target.mid.takeIf { it > 0L }?.let { "space_$it" }
    is BiliPaiNavKey.LiveAreaDetail -> "live_area_${target.parentAreaId}_${target.areaId}"
    is BiliPaiNavKey.Web -> if (com.android.purebilibili.feature.web.shouldOpenWebUrlExternally(target.url)) {
        null
    } else {
        "web_${java.util.UUID.nameUUIDFromBytes(target.url.toByteArray(Charsets.UTF_8))}"
    }
    else -> null
}

internal fun BiliPaiNavKey?.isPlaybackWindowKey(): Boolean =
    this is BiliPaiNavKey.VideoDetail || this is BiliPaiNavKey.Live ||
        this is BiliPaiNavKey.BangumiPlayer || this is BiliPaiNavKey.AudioMode ||
        this is BiliPaiNavKey.Story || this is BiliPaiNavKey.OfflineVideoPlayer

internal data class SystemWindowNavigationControl(val modifier: Modifier, val onClick: () -> Unit)

@Composable
internal fun rememberSystemWindowNavigationControl(
    target: BiliPaiNavKey?,
    shape: Shape,
    onClick: () -> Unit,
): SystemWindowNavigationControl {
    val source = rememberSystemWindowControlSource(target)
    return SystemWindowNavigationControl(source.anchorModifier(shape)) {
        if (source != null && source.record()) {
            // 普通导航只在这次点击内认领来源，认证或后续跳转不能捡到旧锚点。
            try {
                onClick()
            } finally {
                VideoCardSourceInstances.clearClickedAnchor(source.identity)
            }
        } else {
            onClick()
        }
    }
}

@Composable
internal fun rememberSystemWindowControlSource(target: BiliPaiNavKey?): SystemWindowCardSource? =
    if (LocalVideoCardSourceTrackingEnabled.current) rememberSystemWindowCardSource(target) else null

@Composable
internal fun SystemWindowCardSource?.anchorModifier(
    shape: Shape,
    animateNeighbors: Boolean = true,
    neighborActive: Boolean = true,
    returnHandledByOwner: Boolean = false,
    motionGroup: Any? = null,
): Modifier {
    if (this == null) return Modifier
    val group = motionGroup ?: LocalNavigationControlMotionGroup.current
    val grouped = group as? NavigationControlMotionGroupState
    ObserveSystemWindowControlNeighbor(neighbor, neighborActive, returnHandledByOwner, group)
    return neighbor.groupLandingModifier(grouped)
        .then(if (animateNeighbors) neighbor.modifier else Modifier)
        .videoCardSourceAnchor(
            identity, shape,
            onPositioned = { coordinates = it; neighbor.coordinates = it },
            returnHandledByOwner = returnHandledByOwner || grouped != null,
        )
        .onGloballyPositioned {
            neighbor.updateViewport(it)
        }
}

/** 同组的原地操作只参与联动，点击时仍执行自己的操作。 */
@Composable
internal fun rememberSystemWindowControlNeighborModifier(): Modifier {
    if (!LocalVideoCardSourceTrackingEnabled.current) return Modifier
    val group = LocalNavigationControlMotionGroup.current ?: return Modifier
    val view = LocalView.current
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val density = LocalDensity.current.density
    val neighbor = remember(group, view, sourceRoute, density) {
        SystemWindowControlNeighbor(view, sourceRoute, density)
    }
    ObserveSystemWindowControlNeighbor(neighbor, pageActive = true, returnHandledByOwner = false, motionGroup = group)
    return neighbor.groupLandingModifier(group)
        .then(neighbor.modifier)
        .onGloballyPositioned {
            neighbor.coordinates = it
            neighbor.updateViewport(it)
        }
}

@Composable
private fun SystemWindowControlNeighbor.groupLandingModifier(group: NavigationControlMotionGroupState?): Modifier =
    if (group == null) Modifier else Modifier.navigationReturnLanding(
        groupLanding,
        isSourceVisible = { visibleBounds() != null },
    )

private fun SystemWindowControlNeighbor.updateViewport(coordinates: androidx.compose.ui.layout.LayoutCoordinates) {
    val visible = coordinates.boundsInWindow()
    inViewport = visible.width > 1f && visible.height > 1f
}

// 空锚点与可见内容分离的入口，把联动层显式放到可见内容上。
internal fun SystemWindowCardSource?.neighborMotionModifier(): Modifier = this?.neighbor?.modifier ?: Modifier

@Composable
internal fun rememberSystemWindowCardSource(target: BiliPaiNavKey?): SystemWindowCardSource? {
    val identity = resolveSystemWindowCardIdentity(target) ?: return null
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val configuration: Configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    return remember(
        identity, sourceRoute, configuration.screenWidthDp, configuration.screenHeightDp, density, view,
    ) {
        SystemWindowCardSource(
            identity = identity,
            sourceRoute = sourceRoute,
            screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() },
            screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() },
            densityValue = density.density,
            neighbor = SystemWindowControlNeighbor(view, sourceRoute, density.density),
        )
    }
}
