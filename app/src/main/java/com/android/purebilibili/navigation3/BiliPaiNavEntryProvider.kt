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
    sharedControlEntry<BiliPaiNavKey.Search>(navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.SearchTrending>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.TopicDetail>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.Settings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, navigationContainerTransitions = navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SettingsCategory>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.SettingsSearch>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, navigationContainerTransitions = navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    settingsEntry<BiliPaiNavKey.ProcessMemory>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
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
    settingsEntry<BiliPaiNavKey.CustomPushSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, swipeDismiss = swipeBackDirection, content = content)
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
    settingsEntry<BiliPaiNavKey.GamepadControlSettings>(settingsBackStack, settingsPersistentPanes, activeMainHostRoute, settingsContainerTransitions, settingsMorphFallback, navigationContainerTransitions = navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Login>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Profile>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.ProfileAccountEdit>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.AccountLedger>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.AicuQuery>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.History>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.PersonalRecap>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.PersonalRecapUps>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.HistorySearch>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Favorite>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.FavoritePgc>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.FavoriteSubscribed>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.FavoriteSearch>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.LikedVideos>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.WatchLater>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.WatchLaterSearch>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.Onboarding>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Following>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.UpowerRank>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.UpowerRank.Companion>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.MemberGuard>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    entry<BiliPaiNavKey.MemberGuard.Companion>(swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.DownloadList>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.OfflineVideoPlayer>(navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.LiveList>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveSearch>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveArea>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveAreaDetail>(navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.LiveAreaDetail }
            ?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.LiveMatch>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.MatchInfo>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.LiveFollowing>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Inbox>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.ReplyMe>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.AtMe>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.LikeMe>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.SystemNotice>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Chat>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.Partition>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    entry<BiliPaiNavKey.Story>(
        transition = fullscreenVideoCardTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.AudioMode>(navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.SeasonSeriesDetail>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Bangumi>(navigationContainerTransitions,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.BangumiPlayer>(navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.BangumiPlayer }
            ?.let(liveContainerTransitions::get) ?: predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.WeeklySeries>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.BgmDetail>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.MusicDetail>(navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.NativeMusic>(navigationContainerTransitions,
        transition = predictiveBackExcludedTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.VideoDetail>(navigationContainerTransitions,
        transition = videoCardTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.ArticleCollection>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.ArticleDetail>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.DynamicDetail>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.CommentDetail>(navigationContainerTransitions, swipeDismiss = swipeBackDirection, content = content)
    sharedControlEntry<BiliPaiNavKey.SpaceWebVideos>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Space>(navigationContainerTransitions,
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Space }?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = swipeBackDirection, content = content,
    )
    sharedControlEntry<BiliPaiNavKey.Category>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    sharedControlEntry<BiliPaiNavKey.Live>(navigationContainerTransitions,
        // 卡片来源优先；深链等普通入口与卡片动效关闭时完整继承页面切换设置。
        transition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Live }
            ?.let(liveContainerTransitions::get) ?: pageTransition,
        swipeDismiss = NavSwipeDirection.None,
        content = content,
    )
    val bangumiContainer = settingsBackStack.lastOrNull { it is BiliPaiNavKey.BangumiDetail }?.let(liveContainerTransitions::get)
    sharedControlEntry<BiliPaiNavKey.BangumiDetail>(navigationContainerTransitions,
        transition = bangumiContainer ?: pageTransition,
        swipeDismiss = if (bangumiContainer != null) swipeBackDirection else NavSwipeDirection.None,
        content = content,
    )
    sharedControlEntry<BiliPaiNavKey.BangumiReview>(navigationContainerTransitions, swipeDismiss = NavSwipeDirection.None, content = content)
    val webContainerTransition = settingsBackStack.lastOrNull { it is BiliPaiNavKey.Web }
        ?.let(liveContainerTransitions::get)
    sharedControlEntry<BiliPaiNavKey.Web>(navigationContainerTransitions,
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
    navigationContainerTransitions: Map<BiliPaiNavKey, NavTransition> = emptyMap(),
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
            backStack.getOrNull(index)?.let { containerTransitions[it] ?: navigationContainerTransitions[it] }
                ?: settingsMorphFallback ?: transition,
        swipeDismiss = swipeDismiss,
        content = content,
    )
}

/** 同类型页面可同时在栈中，共享转场绑定到登记来源的具体页面。 */
private inline fun <reified T : BiliPaiNavKey> NavEntryBuilder.sharedControlEntry(
    transitions: Map<BiliPaiNavKey, NavTransition>,
    transition: NavTransition? = null,
    swipeDismiss: NavSwipeDirection,
    noinline content: @Composable (BiliPaiNavKey) -> Unit,
) {
    entry<T>(
        transition = transition,
        transitionForKey = { key -> transitions[key] },
        swipeDismiss = swipeDismiss,
        content = content,
    )
}
