package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition

private val emptyRouteContent: @Composable (BiliPaiNavKey) -> Unit = {}

// The scene supplies the page content. This registry only selects transition and swipe settings.
internal fun NavEntryBuilder.biliPaiNavEntries(
    swipeBackDirection: NavSwipeDirection,
    settingsBackStack: List<BiliPaiNavKey> = emptyList(),
    settingsPersistentPanes: Boolean = false,
    settingsContainerTransitions: Map<BiliPaiNavKey, NavTransition> = emptyMap(),
    navigationContainerTransitions: Map<BiliPaiNavKey, NavTransition> = emptyMap(),
    liveContainerTransitions: Map<BiliPaiNavKey, NavTransition> = emptyMap(),
    settingsMorphFallback: NavTransition? = null,
    activeMainHostRoute: String? = null,
    pageTransition: NavTransition,
    predictiveBackExcludedTransition: NavTransition,
    videoCardTransition: NavTransition,
    fullscreenVideoCardTransition: NavTransition,
) {
    val content = emptyRouteContent
    entry<BiliPaiNavKey.MainHost>(swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.Home>(swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.ListenVideo>(swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.Dynamic>(swipeDismiss = NavSwipeDirection.None, content = content)
    // Search owns a bottom-search-slot morph. A generic horizontal swipe would turn the whole
    // destination into a shrinking card and conflict with that spatial relationship.
    sharedControlEntry<BiliPaiNavKey.Search>(settingsBackStack, navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.SearchTrending>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.TopicDetail>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.Settings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SettingsCategory>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SettingsSearch>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.PerformanceHistory>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.OpenSourceLicenses>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.AppearanceSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.HomeSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.IconSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.AnimationSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.PlaybackSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.PermissionSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SpacePrivacySettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.LoginActivitySettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.MessageNotificationSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.PluginsSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.BuiltinPluginSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.JsonPluginEditor>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.PluginSkinCatalog>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.ContentFilterHistory>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.JsPluginContent>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    settingsEntry<BiliPaiNavKey.ExternalMedia>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    settingsEntry<BiliPaiNavKey.BottomBarSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SettingsShare>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.WebDavBackup>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.TipsSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.KeyboardShortcutsSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.Login>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Profile>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.ProfileAccountEdit>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.AccountLedger>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.AicuQuery>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.History>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.PersonalRecapUps>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.HistorySearch>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Favorite>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.FavoritePgc>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.FavoriteSubscribed>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.FavoriteSearch>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.LikedVideos>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.WatchLater>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.WatchLaterSearch>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.Onboarding>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Following>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.UpowerRank>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.UpowerRank.Companion>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.MemberGuard>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.MemberGuard.Companion>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.DownloadList>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.OfflineVideoPlayer>(
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.LiveList>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveSearch>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveArea>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveAreaDetail>(settingsBackStack, navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.LiveAreaDetail }
            ?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.LiveMatch>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.MatchInfo>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.LiveFollowing>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Inbox>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.ReplyMe>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.AtMe>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.LikeMe>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.SystemNotice>(settingsBackStack, navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.Chat>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Partition>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.Story>(
        transition = fullscreenVideoCardTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.AudioMode>(settingsBackStack, navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.SeasonSeriesDetail>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Bangumi>(
        settingsBackStack,
        navigationContainerTransitions,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.BangumiPlayer>(settingsBackStack, navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.BangumiPlayer }
            ?.let(liveContainerTransitions::get) ?: predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.WeeklySeries>(content = content)
    entry<BiliPaiNavKey.BgmDetail>(content = content)
    entry<BiliPaiNavKey.MusicDetail>(
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.NativeMusic>(
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.VideoDetail>(settingsBackStack, navigationContainerTransitions,
        transition = videoCardTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.ArticleCollection>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.ArticleDetail>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.DynamicDetail>(settingsBackStack, navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.CommentDetail>(swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.SpaceWebVideos>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Space>(settingsBackStack, navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Space }?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = swipeBackDirection, content = content,
    )
    entry<BiliPaiNavKey.Category>(swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Live>(settingsBackStack, navigationContainerTransitions,
        // 卡片来源优先；深链等普通入口与卡片动效关闭时完整继承页面切换设置。
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Live }
            ?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    val bangumiContainer = settingsBackStack.lastOrNull { it is BiliPaiNavKey.BangumiDetail }?.let(liveContainerTransitions::get)
    sharedControlEntry<BiliPaiNavKey.BangumiDetail>(settingsBackStack, navigationContainerTransitions,
        transition = bangumiContainer ?: pageTransition,
        swipeDismiss = if (bangumiContainer != null) swipeBackDirection else NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.BangumiReview>(swipeDismiss = NavSwipeDirection.None, content = content)
    val webContainerTransition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Web }
        ?.let(liveContainerTransitions::get)
    sharedControlEntry<BiliPaiNavKey.Web>(settingsBackStack, navigationContainerTransitions,
        transition = webContainerTransition ?: predictiveBackExcludedTransition,
        swipeDismiss = if (webContainerTransition != null) swipeBackDirection else NavSwipeDirection.None,
        content = content,
    )
    entry<BiliPaiNavKey.Unknown>(swipeDismiss = NavSwipeDirection.None, content = content)
}

/** Entry metadata is retained for departing pages, so pop uses the same boundary as push. */
private inline fun <reified T : BiliPaiNavKey> NavEntryBuilder.settingsEntry(
    backStack: List<BiliPaiNavKey>,
    persistentPanes: Boolean,
    activeMainHostRoute: String?,
    containerTransitions: Map<BiliPaiNavKey, NavTransition>,
    settingsMorphFallback: NavTransition?,
    transition: NavTransition? = null,
    swipeDismiss: NavSwipeDirection,
    noinline content: @Composable (BiliPaiNavKey) -> Unit,
) {
    val index = backStack.indexOfLast { it is T }
    val paneNavigation = index >= 0 && isSettingsPaneNavigation(
        persistentPanes = persistentPanes,
        fromKey = backStack.getOrNull(index - 1),
        toKey = backStack.getOrNull(index),
        activeMainHostRoute = activeMainHostRoute,
    )
    entry<T>(
        transition = if (paneNavigation) SettingsPaneTransition else
            backStack.getOrNull(index)?.let(containerTransitions::get) ?: settingsMorphFallback ?: transition,
        swipeDismiss = swipeDismiss,
        content = content,
    )
}

/** 离场页面保留选定的共享转场，返回不能按设备能力重新选一次。 */
private inline fun <reified T : BiliPaiNavKey> NavEntryBuilder.sharedControlEntry(
    backStack: List<BiliPaiNavKey>,
    transitions: Map<BiliPaiNavKey, NavTransition>,
    transition: NavTransition? = null,
    swipeDismiss: NavSwipeDirection,
    noinline content: @Composable (BiliPaiNavKey) -> Unit,
) {
    entry<T>(
        transition = backStack.lastOrNull { it is T }?.let(transitions::get) ?: transition,
        swipeDismiss = swipeDismiss,
        content = content,
    )
}
