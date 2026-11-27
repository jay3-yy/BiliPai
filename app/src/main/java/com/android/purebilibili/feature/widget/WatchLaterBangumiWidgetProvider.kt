package com.android.purebilibili.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.android.purebilibili.EXTRA_PENDING_NAVIGATION_ROUTE
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.data.repository.WatchLaterRepository
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 桌面与锁屏交互式「稍后再看 · 今日追番」小组件。
 * - API 31+ 使用原生 RemoteViews.RemoteCollectionItems 构建真·可滚动列表；
 * - 全事件驱动（支持切换标签、点击单条直达、手动刷新、账号/隐私变更同步），无后台定时轮询。
 */
class WatchLaterBangumiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        refreshWidgets(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refreshWidgets(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = (context.applicationContext ?: context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            appWidgetIds.forEach { widgetId ->
                remove(tabKey(widgetId))
                activeWidgetJobs.remove(widgetId)?.cancel()
                widgetGenerations.remove(widgetId)
            }
        }.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SWITCH_TAB -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val tab = WatchLaterBangumiWidgetTab.fromKey(intent.getStringExtra(EXTRA_TAB))
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    saveSelectedTab(context, widgetId, tab)
                    refreshWidgets(context, intArrayOf(widgetId))
                }
            }
            ACTION_REFRESH -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val ids = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    intArrayOf(widgetId)
                } else {
                    allWidgetIds(context)
                }
                if (ids.isNotEmpty()) refreshWidgets(context, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun refreshWidgets(context: Context, widgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        val pending = goAsync()
        val generationsByWidget = widgetIds.associateWith { widgetId ->
            val nextGen = widgetGenerations.computeIfAbsent(widgetId) { AtomicLong(0L) }.incrementAndGet()
            activeWidgetJobs.remove(widgetId)?.cancel()
            nextGen
        }
        val batchJob = providerScope.launch {
            try {
                withTimeout(8_000L) {
                    val manager = AppWidgetManager.getInstance(appContext)
                    TokenManager.refreshServiceSession(appContext)
                    val initialProtected = SystemMediaPreferences.libraryProtected(appContext)
                    val initialLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty()
                    val initialAccount = TokenManager.accountIdentity.value

                    var cachedLater: Pair<List<WidgetFeedItem>, Boolean>? = null
                    var cachedBangumi: Pair<List<WidgetFeedItem>, Boolean>? = null

                    widgetIds.forEach { widgetId ->
                        val requestGen = generationsByWidget[widgetId] ?: return@forEach
                        val latestGen = widgetGenerations[widgetId]?.get() ?: 0L
                        if (requestGen != latestGen) return@forEach

                        val startedTab = loadSelectedTab(appContext, widgetId)
                        val startedSnapshot = WidgetPublishSnapshot(
                            tab = startedTab,
                            libraryProtected = initialProtected,
                            isLoggedIn = initialLoggedIn,
                            accountIdentity = initialAccount,
                        )
                        val (items, loadFailed) = when (startedTab) {
                            WatchLaterBangumiWidgetTab.WATCH_LATER -> {
                                cachedLater ?: fetchWatchLater(initialProtected, initialLoggedIn)
                                    .also { cachedLater = it }
                            }
                            WatchLaterBangumiWidgetTab.BANGUMI -> {
                                cachedBangumi ?: fetchTodayBangumi().also { cachedBangumi = it }
                            }
                        }

                        // 发布前再次核验实时隐私锁、账号身份、选中标签与最新刷新世代，防止旧请求回写过期数据。
                        val currentSnapshot = WidgetPublishSnapshot(
                            tab = loadSelectedTab(appContext, widgetId),
                            libraryProtected = SystemMediaPreferences.libraryProtected(appContext),
                            isLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty(),
                            accountIdentity = TokenManager.accountIdentity.value,
                        )
                        val publishLatestGen = widgetGenerations[widgetId]?.get() ?: 0L
                        when (
                            resolveWidgetPublishDecision(
                                requestGeneration = requestGen,
                                latestGeneration = publishLatestGen,
                                started = startedSnapshot,
                                current = currentSnapshot,
                            )
                        ) {
                            WidgetPublishDecision.DROP_STALE -> return@forEach
                            WidgetPublishDecision.APPLY_EMPTY_PROTECTED -> {
                                val views = buildRemoteViews(
                                    context = appContext,
                                    widgetId = widgetId,
                                    tab = currentSnapshot.tab,
                                    items = emptyList(),
                                    libraryProtected = currentSnapshot.libraryProtected,
                                    isLoggedIn = currentSnapshot.isLoggedIn,
                                    loadFailed = false,
                                )
                                manager.updateAppWidget(widgetId, views)
                            }
                            WidgetPublishDecision.APPLY_FETCHED -> {
                                val views = buildRemoteViews(
                                    context = appContext,
                                    widgetId = widgetId,
                                    tab = currentSnapshot.tab,
                                    items = items,
                                    libraryProtected = currentSnapshot.libraryProtected,
                                    isLoggedIn = currentSnapshot.isLoggedIn,
                                    loadFailed = loadFailed,
                                )
                                manager.updateAppWidget(widgetId, views)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (timeout: TimeoutCancellationException) {
                Logger.w(TAG, "WatchLater/Bangumi widget update timed out", timeout)
            } catch (error: Exception) {
                Logger.w(TAG, "WatchLater/Bangumi widget update failed", error)
            } finally {
                widgetIds.forEach { widgetId ->
                    val currentJob = activeWidgetJobs[widgetId]
                    if (currentJob?.isCompleted == true) {
                        activeWidgetJobs.remove(widgetId, currentJob)
                    }
                }
                pending.finish()
            }
        }
        widgetIds.forEach { widgetId ->
            activeWidgetJobs[widgetId] = batchJob
        }
    }

    private suspend fun fetchWatchLater(
        libraryProtected: Boolean,
        loggedIn: Boolean,
    ): Pair<List<WidgetFeedItem>, Boolean> {
        if (libraryProtected || !loggedIn) return emptyList<WidgetFeedItem>() to false
        return WatchLaterRepository.getPage(page = 1, viewed = 0, keyword = "", ascending = false).fold(
            onSuccess = { page ->
                val items = page.items
                    .filter { it.bvid.isNotBlank() }
                    .take(MAX_WIDGET_ITEMS)
                    .mapIndexed { index, item ->
                        val stableId = (item.bvid.hashCode().toLong() shl 32) or
                            ((item.cid.takeIf { it > 0L } ?: index.toLong()) and 0xffffffffL)
                        WidgetFeedItem(
                            id = stableId,
                            title = item.title.trim(),
                            subtitle = item.owner.name.trim().ifBlank { "稍后再看" },
                            targetRoute = ScreenRoutes.VideoPlayer.createRoute(
                                bvid = item.bvid,
                                cid = item.cid,
                            ),
                        )
                    }
                items to false
            },
            onFailure = { emptyList<WidgetFeedItem>() to true },
        )
    }

    private suspend fun fetchTodayBangumi(): Pair<List<WidgetFeedItem>, Boolean> {
        return BangumiRepository.getTimeline(type = 1, before = 0, after = 1).fold(
            onSuccess = { days -> mapBangumiTimelineToWidgetItems(days, MAX_WIDGET_ITEMS) to false },
            onFailure = { emptyList<WidgetFeedItem>() to true },
        )
    }

    private fun buildRemoteViews(
        context: Context,
        widgetId: Int,
        tab: WatchLaterBangumiWidgetTab,
        items: List<WidgetFeedItem>,
        libraryProtected: Boolean,
        isLoggedIn: Boolean,
        loadFailed: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_watchlater_bangumi)
        val accent = ContextCompat.getColor(context, R.color.widget_playback_accent)
        val normal = ContextCompat.getColor(context, R.color.widget_playback_text)

        views.setTextColor(
            R.id.widget_tab_watch_later,
            if (tab == WatchLaterBangumiWidgetTab.WATCH_LATER) accent else normal,
        )
        views.setTextColor(
            R.id.widget_tab_bangumi,
            if (tab == WatchLaterBangumiWidgetTab.BANGUMI) accent else normal,
        )

        views.setOnClickPendingIntent(
            R.id.widget_tab_watch_later,
            tabSwitchPendingIntent(context, widgetId, WatchLaterBangumiWidgetTab.WATCH_LATER, requestCode = widgetId * 10 + 1),
        )
        views.setOnClickPendingIntent(
            R.id.widget_tab_bangumi,
            tabSwitchPendingIntent(context, widgetId, WatchLaterBangumiWidgetTab.BANGUMI, requestCode = widgetId * 10 + 2),
        )
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            refreshPendingIntent(context, widgetId, requestCode = widgetId * 10 + 3),
        )

        if (items.isEmpty()) {
            views.setViewVisibility(R.id.widget_feed_list, View.GONE)
            views.setViewVisibility(R.id.widget_feed_empty, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_feed_empty,
                resolveWidgetEmptyStateMessage(
                    tab = tab,
                    libraryProtected = libraryProtected,
                    isLoggedIn = isLoggedIn,
                    loadFailed = loadFailed,
                ),
            )
            val fallbackRoute = if (tab == WatchLaterBangumiWidgetTab.WATCH_LATER) {
                ScreenRoutes.WatchLater.route
            } else {
                ScreenRoutes.Home.route
            }
            views.setOnClickPendingIntent(
                R.id.widget_feed_empty,
                openRoutePendingIntent(context, fallbackRoute, requestCode = widgetId * 10 + 4),
            )
            return views
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewVisibility(R.id.widget_feed_empty, View.GONE)
            views.setViewVisibility(R.id.widget_feed_list, View.VISIBLE)
            val collectionBuilder = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            items.forEach { item ->
                val row = RemoteViews(context.packageName, R.layout.widget_watchlater_bangumi_item)
                row.setTextViewText(R.id.widget_feed_item_title, item.title)
                row.setTextViewText(R.id.widget_feed_item_subtitle, item.subtitle)
                val fillIn = Intent().putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, item.targetRoute)
                row.setOnClickFillInIntent(R.id.widget_feed_item_root, fillIn)
                collectionBuilder.addItem(item.id, row)
            }
            views.setRemoteAdapter(R.id.widget_feed_list, collectionBuilder.build())
            val templateIntent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val templatePendingIntent = PendingIntent.getActivity(
                context,
                widgetId * 10 + 5,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.widget_feed_list, templatePendingIntent)
        } else {
            // API 26–30 无 RemoteCollectionItems，展示前 3 条紧凑摘要并点击直达首条内容。
            views.setViewVisibility(R.id.widget_feed_list, View.GONE)
            views.setViewVisibility(R.id.widget_feed_empty, View.VISIBLE)
            val summary = items.take(3).joinToString("\n") { "• ${it.title}" }
            views.setTextViewText(R.id.widget_feed_empty, summary)
            views.setOnClickPendingIntent(
                R.id.widget_feed_empty,
                openRoutePendingIntent(context, items.first().targetRoute, requestCode = widgetId * 10 + 6),
            )
        }
        return views
    }

    private fun tabSwitchPendingIntent(
        context: Context,
        widgetId: Int,
        tab: WatchLaterBangumiWidgetTab,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, WatchLaterBangumiWidgetProvider::class.java)
            .setAction(ACTION_SWITCH_TAB)
            .setData(Uri.parse("bilipai-feed-widget://tab/$widgetId/${tab.storageKey}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(EXTRA_TAB, tab.storageKey)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshPendingIntent(
        context: Context,
        widgetId: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, WatchLaterBangumiWidgetProvider::class.java)
            .setAction(ACTION_REFRESH)
            .setData(Uri.parse("bilipai-feed-widget://refresh/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openRoutePendingIntent(
        context: Context,
        route: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun saveSelectedTab(context: Context, widgetId: Int, tab: WatchLaterBangumiWidgetTab) {
        (context.applicationContext ?: context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(tabKey(widgetId), tab.storageKey)
            .apply()
    }

    private fun loadSelectedTab(context: Context, widgetId: Int): WatchLaterBangumiWidgetTab {
        val raw = (context.applicationContext ?: context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(tabKey(widgetId), null)
        return WatchLaterBangumiWidgetTab.fromKey(raw)
    }

    private fun tabKey(widgetId: Int): String = "tab_$widgetId"

    companion object {
        private const val TAG = "WatchLaterBangumiWidget"
        private const val PREFS_NAME = "widget_watchlater_bangumi_prefs"
        private const val ACTION_SWITCH_TAB = "com.android.purebilibili.widget.SWITCH_FEED_TAB"
        private const val ACTION_REFRESH = "com.android.purebilibili.widget.REFRESH_FEED"
        private const val EXTRA_TAB = "feed_tab"
        private const val MAX_WIDGET_ITEMS = 10
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val widgetGenerations = ConcurrentHashMap<Int, AtomicLong>()
        private val activeWidgetJobs = ConcurrentHashMap<Int, Job>()

        private fun allWidgetIds(context: Context): IntArray {
            val appContext = context.applicationContext ?: context
            return AppWidgetManager.getInstance(appContext).getAppWidgetIds(
                ComponentName(appContext, WatchLaterBangumiWidgetProvider::class.java)
            )
        }

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext ?: context
            val ids = allWidgetIds(appContext)
            if (ids.isEmpty()) return
            appContext.sendBroadcast(
                Intent(appContext, WatchLaterBangumiWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
