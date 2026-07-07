package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.android.purebilibili.feature.settings.SETTINGS_SUBTREE_ROUTE_BASES
import com.android.purebilibili.feature.settings.isSettingsNavHierarchyTransition

private const val BILI_PAI_NAV_ROUTE_BASE_METADATA_KEY = "biliPaiNavRouteBase"
private const val VIDEO_ROUTE_BASE = "video"
private const val SPACE_ROUTE_BASE = "space"
private const val SETTINGS_ROUTE_BASE = "settings"
private const val INBOX_ROUTE_BASE = "inbox"
private const val LIVE_LIST_ROUTE_BASE = "live_list"
private const val SEARCH_ROUTE_BASE = "search"
// 必须与 [isCardReturnTargetNavKey] 保持同步覆盖。
// 注意 "main_host" 必须在内——实际栈是 [MainHost, VideoDetail]（Home/Dynamic/... tab 渲染在
// MainHost 的 bottom pager 里），pop 时 entry-level popTransitionSpec 的 targetState.routeBase
// 是 "main_host" 而非 "home"。漏掉会导致关闭共享元素后 VideoDetail → 首页退化成 fade。
private val CARD_RETURN_TARGET_ROUTE_BASES = setOf(
    "main_host",
    "home",
    "dynamic",
    "search",
    "history",
    "favorite",
    "liked_videos",
    "watch_later",
    "partition",
    "dynamic_detail",
    "space",
    "category",
    "season_series_detail"
)
private val SETTINGS_LIGHT_SIBLING_ROUTE_BASES = SETTINGS_SUBTREE_ROUTE_BASES - SETTINGS_ROUTE_BASE
private val MESSAGE_LIGHT_SIBLING_ROUTE_BASES = setOf(
    "message/reply_me",
    "message/at_me",
    "message/like_me",
    "message/system_notice",
    "chat"
)
private val LIVE_LIGHT_SIBLING_ROUTE_BASES = setOf(
    "live_area",
    "live_search",
    "live_following"
)
private val SEARCH_LIGHT_SIBLING_ROUTE_BASES = setOf(
    "search_trending",
    "topic"
)

internal fun biliPaiNavEntryProvider(
    sourceMetadata: BiliPaiNavSourceMetadata,
    cardTransitionEnabled: Boolean = true,
    visibleBottomBarRoutes: Set<String> = emptySet(),
    activeMainHostRoute: String? = null,
    content: @Composable (BiliPaiNavKey) -> Unit
): (BiliPaiNavKey) -> NavEntry<BiliPaiNavKey> {
    val entryMetadata: (BiliPaiNavKey) -> Map<String, Any> = { key ->
        biliPaiNavEntryMetadata(
            key = key,
            sourceMetadata = sourceMetadata,
            cardTransitionEnabled = cardTransitionEnabled,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            activeMainHostRoute = activeMainHostRoute
        )
    }
    return entryProvider(
        fallback = { key ->
            NavEntry(
                key = key,
                metadata = entryMetadata(key),
                content = content
            )
        }
    ) {
        entry<BiliPaiNavKey.MainHost>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Home>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Dynamic>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Search>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SearchTrending>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.TopicDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Settings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SettingsSearch>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SettingsCategory>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.OpenSourceLicenses>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.AppearanceSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.IconSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.AnimationSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.PlaybackSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.PermissionSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.PluginsSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.JsPluginContent>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.ExternalMedia>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.BottomBarSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SettingsShare>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.WebDavBackup>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.TipsSettings>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Login>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Profile>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.History>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Favorite>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.WatchLater>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Onboarding>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Following>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.DownloadList>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.OfflineVideoPlayer>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LiveList>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LiveSearch>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LiveArea>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LiveAreaDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LiveFollowing>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Inbox>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.ReplyMe>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.AtMe>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.LikeMe>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SystemNotice>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Chat>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Partition>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Story>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.AudioMode>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.SeasonSeriesDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Bangumi>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.BangumiPlayer>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.MusicDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.NativeMusic>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.VideoDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.ArticleDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.DynamicDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Space>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Category>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Live>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.BangumiDetail>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Web>(metadata = entryMetadata, content = content)
        entry<BiliPaiNavKey.Unknown>(metadata = entryMetadata, content = content)
    }
}

/**
 * 注入到每个 [NavEntry.metadata] 的过渡描述。
 *
 * 按官方 NavDisplay 优先级（NavDisplay.kt:219）：
 *   transitioning NavEntry.metadata > current Scene.metadata > NavDisplay defaults
 * 而 [androidx.navigation3.scene.SinglePaneScene] 的 `Scene.metadata` 默认就是栈顶 entry 的 metadata。
 *
 * 所以这里注入的 [NavDisplay.transitionSpec] 与 [NavDisplay.popTransitionSpec] 是**系统返回 / 普通 pop /
 * 普通 push 路径的实际生效路径**——[BiliPaiNavDisplayHost] 那两个同名全局 lambda 只是兜底。
 *
 * 注意：此处刻意**不**注入预测式返回的过渡（即不写 PREDICTIVE_POP_TRANSITION_SPEC 那个 key），
 * 因此预测式返回（Android 13+ swipe-back）落到 [BiliPaiNavDisplayHost] 上 NavDisplay 的全局值。
 * 该约束被 `BiliPaiNavEntryProviderPolicyTest.providerDoesNotOwnPredictivePopTransition` 守护
 * （直接文本断言 "NavDisplay.predictivePop" 不出现在本文件中）。
 */
internal fun biliPaiNavEntryMetadata(
    key: BiliPaiNavKey,
    sourceMetadata: BiliPaiNavSourceMetadata,
    cardTransitionEnabled: Boolean = true,
    visibleBottomBarRoutes: Set<String> = emptySet(),
    activeMainHostRoute: String? = null
): Map<String, Any> {
    val transitions = resolveBiliPaiNavEntryRouteTransitions(
        key = key,
        cardTransitionEnabled = cardTransitionEnabled,
        sourceMetadata = sourceMetadata
    )
    return mapOf(
        BILI_PAI_NAV_ROUTE_BASE_METADATA_KEY to key.routeBase
    ) + NavDisplay.transitionSpec {
        val transition = resolveBiliPaiNavEntryForwardRouteTransition(
            defaultTransition = transitions.forward,
            fromRoute = initialState.biliPaiRouteBase(),
            toRoute = targetState.biliPaiRouteBase(),
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            activeMainHostRoute = activeMainHostRoute
        )
        resolveBiliPaiNavContentTransform(transition)
    } + NavDisplay.popTransitionSpec {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = transitions.pop,
            fromRoute = initialState.biliPaiRouteBase(),
            toRoute = targetState.biliPaiRouteBase(),
            cardTransitionEnabled = cardTransitionEnabled,
            sharedElementPopReady = key is BiliPaiNavKey.SeasonSeriesDetail &&
                key.sharedElementTransition,
            sourceMetadata = sourceMetadata,
            activeMainHostRoute = activeMainHostRoute
        )
        resolveBiliPaiNavContentTransform(transition)
    }
}

internal fun resolveBiliPaiNavEntryForwardRouteTransition(
    defaultTransition: BiliPaiNavRouteTransition,
    fromRoute: String?,
    toRoute: String?,
    visibleBottomBarRoutes: Set<String>,
    activeMainHostRoute: String? = null
): BiliPaiNavRouteTransition {
    val normalizedFromRoute = normalizeBiliPaiNavEntryRouteBase(fromRoute)
    val normalizedToRoute = normalizeBiliPaiNavEntryRouteBase(toRoute)
    val normalizedActiveMainHostRoute = normalizeBiliPaiNavEntryRouteBase(activeMainHostRoute)
    if (
        isSpaceRouteBase(normalizedToRoute) &&
        isMainHostOrVisibleBottomRoute(
            routeBase = normalizedFromRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes
        )
    ) {
        return BiliPaiNavRouteTransition.SPACE_FORWARD
    }
    if (
        defaultTransition == BiliPaiNavRouteTransition.FALLBACK &&
        isLightSiblingForwardRoute(
            fromRoute = normalizedFromRoute,
            toRoute = normalizedToRoute,
            activeMainHostRoute = normalizedActiveMainHostRoute
        )
    ) {
        return if (
            isSettingsNavHierarchyRoute(
                parentRoute = normalizedFromRoute,
                childRoute = normalizedToRoute,
                activeMainHostRoute = normalizedActiveMainHostRoute
            )
        ) {
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_FORWARD
        } else {
            BiliPaiNavRouteTransition.LIGHT_SIBLING_FORWARD
        }
    }
    return defaultTransition
}

internal fun resolveBiliPaiNavEntryPopRouteTransition(
    defaultTransition: BiliPaiNavRouteTransition,
    fromRoute: String?,
    toRoute: String?,
    cardTransitionEnabled: Boolean = true,
    sharedElementPopReady: Boolean = false,
    sourceMetadata: BiliPaiNavSourceMetadata,
    activeMainHostRoute: String? = null
): BiliPaiNavRouteTransition {
    val normalizedFromRoute = normalizeBiliPaiNavEntryRouteBase(fromRoute)
    val normalizedToRoute = normalizeBiliPaiNavEntryRouteBase(toRoute)
    val normalizedSourceRoute = normalizeBiliPaiNavCardSourceRouteBase(sourceMetadata.sourceRoute)
    val normalizedActiveMainHostRoute = normalizeBiliPaiNavEntryRouteBase(activeMainHostRoute)
    val videoToCardReturnTarget = normalizedFromRoute == VIDEO_ROUTE_BASE &&
        normalizedToRoute != null &&
        isCardReturnTargetRouteBase(normalizedToRoute)

    if (cardTransitionEnabled && sharedElementPopReady) {
        return BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
    }

    if (cardTransitionEnabled) {
        val sharedReadyVideoToSourceCard = sourceMetadata.sharedTransitionEntryReady &&
            videoToCardReturnTarget &&
            (
                normalizedToRoute == normalizedSourceRoute ||
                    (
                        normalizedToRoute == BiliPaiNavKey.MainHost.routeBase &&
                            normalizedActiveMainHostRoute == normalizedSourceRoute
                    )
                )
        if (sharedReadyVideoToSourceCard) {
            return BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        }
    } else if (videoToCardReturnTarget) {
        // 关闭共享元素时：VideoDetail → 任意 card-return-target 一律走方向化横向过渡。
        return resolveCardDisabledReturnTransition(sourceMetadata.cardSourceDirection)
    }

    if (
        defaultTransition == BiliPaiNavRouteTransition.FALLBACK &&
        isLightSiblingPopRoute(
            fromRoute = normalizedFromRoute,
            toRoute = normalizedToRoute,
            activeMainHostRoute = normalizedActiveMainHostRoute
        )
    ) {
        return if (
            isSettingsNavHierarchyRoute(
                parentRoute = normalizedToRoute,
                childRoute = normalizedFromRoute,
                activeMainHostRoute = normalizedActiveMainHostRoute
            )
        ) {
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP
        } else {
            BiliPaiNavRouteTransition.LIGHT_SIBLING_POP
        }
    }

    return if (defaultTransition == BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT) {
        BiliPaiNavRouteTransition.FALLBACK
    } else {
        defaultTransition
    }
}

private fun androidx.navigation3.scene.Scene<*>.biliPaiRouteBase(): String? {
    return entries
        .lastOrNull()
        ?.metadata
        ?.get(BILI_PAI_NAV_ROUTE_BASE_METADATA_KEY) as? String
}

internal data class BiliPaiNavEntryRouteTransitions(
    val forward: BiliPaiNavRouteTransition,
    val pop: BiliPaiNavRouteTransition,
    val predictivePop: BiliPaiNavRouteTransition
)

internal fun resolveBiliPaiNavEntryRouteTransitions(
    key: BiliPaiNavKey,
    cardTransitionEnabled: Boolean = true,
    sourceMetadata: BiliPaiNavSourceMetadata
): BiliPaiNavEntryRouteTransitions {
    val recordedMatchingVideoSource = key is BiliPaiNavKey.VideoDetail &&
        sourceMetadata.clickedBoundsRecorded &&
        sourceMetadata.sourceRoute != null &&
        sourceMetadata.sourceRoute == key.sourceRoute &&
        sourceMetadata.sourceKey == "${sourceMetadata.sourceRoute}:${key.bvid}"
    val sharedReadyVideoPush = recordedMatchingVideoSource &&
        sourceMetadata.sharedTransitionEntryReady
    val directionalVideoPushReady = recordedMatchingVideoSource &&
        sourceMetadata.sharedTransitionReady
    val sharedReadyFavoriteCollection =
        key is BiliPaiNavKey.SeasonSeriesDetail && key.sharedElementTransition
    val forward = when {
        cardTransitionEnabled && sharedReadyFavoriteCollection ->
            BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        cardTransitionEnabled && sharedReadyVideoPush -> BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        !cardTransitionEnabled && directionalVideoPushReady ->
            resolveCardDisabledVideoForwardTransition(sourceMetadata.cardSourceDirection)
                ?: BiliPaiNavRouteTransition.FALLBACK
        else -> BiliPaiNavRouteTransition.FALLBACK
    }
    val pop = if (cardTransitionEnabled && sharedReadyFavoriteCollection) {
        BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
    } else {
        BiliPaiNavRouteTransition.FALLBACK
    }
    return BiliPaiNavEntryRouteTransitions(
        forward = forward,
        pop = pop,
        predictivePop = pop
    )
}

private fun normalizeBiliPaiNavEntryRouteBase(route: String?): String? {
    return route
        ?.substringBefore("?")
        ?.takeIf { it.isNotBlank() }
}

private fun normalizeBiliPaiNavCardSourceRouteBase(route: String?): String? {
    val routeBase = normalizeBiliPaiNavEntryRouteBase(route) ?: return null
    return when {
        routeBase.startsWith("dynamic_detail/") -> "dynamic_detail"
        routeBase.startsWith("space/") -> "space"
        routeBase.startsWith("category/") -> "category"
        routeBase.startsWith("season_series_detail/") -> "season_series_detail"
        else -> routeBase
    }
}

private fun isCardReturnTargetRouteBase(routeBase: String): Boolean {
    return routeBase in CARD_RETURN_TARGET_ROUTE_BASES
}

private fun isSpaceRouteBase(routeBase: String?): Boolean {
    return routeBase == SPACE_ROUTE_BASE || routeBase?.startsWith("$SPACE_ROUTE_BASE/") == true
}

private fun isLightSiblingForwardRoute(
    fromRoute: String?,
    toRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    return isLightSiblingRoute(
        parentRoute = fromRoute,
        childRoute = toRoute,
        activeMainHostRoute = activeMainHostRoute
    )
}

private fun isLightSiblingPopRoute(
    fromRoute: String?,
    toRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    return isLightSiblingRoute(
        parentRoute = toRoute,
        childRoute = fromRoute,
        activeMainHostRoute = activeMainHostRoute
    )
}

private fun isLightSiblingRoute(
    parentRoute: String?,
    childRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    return isSettingsNavHierarchyRoute(
        parentRoute = parentRoute,
        childRoute = childRoute,
        activeMainHostRoute = activeMainHostRoute
    ) || isMessageLightSiblingRoute(
        parentRoute = parentRoute,
        childRoute = childRoute,
        activeMainHostRoute = activeMainHostRoute
    ) || isLiveLightSiblingRoute(
        parentRoute = parentRoute,
        childRoute = childRoute,
        activeMainHostRoute = activeMainHostRoute
    ) || isSearchLightSiblingRoute(
        parentRoute = parentRoute,
        childRoute = childRoute,
        activeMainHostRoute = activeMainHostRoute
    )
}

private fun isSettingsNavHierarchyRoute(
    parentRoute: String?,
    childRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    val effectiveParentRoute = if (parentRoute == BiliPaiNavKey.MainHost.routeBase) {
        activeMainHostRoute
    } else {
        parentRoute
    }
    return isSettingsNavHierarchyTransition(
        parentRoute = effectiveParentRoute,
        childRoute = childRoute,
    )
}

private fun isMessageLightSiblingRoute(
    parentRoute: String?,
    childRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    val effectiveParentRoute = if (parentRoute == BiliPaiNavKey.MainHost.routeBase) {
        activeMainHostRoute
    } else {
        parentRoute
    }
    return effectiveParentRoute == INBOX_ROUTE_BASE &&
        childRoute in MESSAGE_LIGHT_SIBLING_ROUTE_BASES
}

private fun isLiveLightSiblingRoute(
    parentRoute: String?,
    childRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    val effectiveParentRoute = if (parentRoute == BiliPaiNavKey.MainHost.routeBase) {
        activeMainHostRoute
    } else {
        parentRoute
    }
    return effectiveParentRoute == LIVE_LIST_ROUTE_BASE &&
        childRoute in LIVE_LIGHT_SIBLING_ROUTE_BASES
}

private fun isSearchLightSiblingRoute(
    parentRoute: String?,
    childRoute: String?,
    activeMainHostRoute: String?
): Boolean {
    val effectiveParentRoute = if (parentRoute == BiliPaiNavKey.MainHost.routeBase) {
        activeMainHostRoute
    } else {
        parentRoute
    }
    return effectiveParentRoute == SEARCH_ROUTE_BASE &&
        childRoute in SEARCH_LIGHT_SIBLING_ROUTE_BASES
}

private fun isMainHostOrVisibleBottomRoute(
    routeBase: String?,
    visibleBottomBarRoutes: Set<String>
): Boolean {
    val visibleBottomRouteBases = visibleBottomBarRoutes
        .mapNotNull(::normalizeBiliPaiNavEntryRouteBase)
        .toSet()
    return routeBase == BiliPaiNavKey.MainHost.routeBase || routeBase in visibleBottomRouteBases
}

private fun resolveCardDisabledVideoForwardTransition(
    sourceDirection: BiliPaiNavCardSourceDirection
): BiliPaiNavRouteTransition? {
    return when (sourceDirection) {
        BiliPaiNavCardSourceDirection.SOURCE_LEFT ->
            BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_FORWARD_FROM_LEFT
        BiliPaiNavCardSourceDirection.SOURCE_RIGHT ->
            BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_FORWARD_FROM_RIGHT
        BiliPaiNavCardSourceDirection.NONE -> null
    }
}
