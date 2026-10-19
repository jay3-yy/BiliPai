// 文件路径: app/PureApplication.kt
package com.android.purebilibili.app

import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.disk.directory
import coil3.request.addLastModifiedToFileCacheKey
import coil3.request.maxBitmapSize

import coil3.request.crossfade
import coil3.request.allowRgb565

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.profileinstaller.ProfileInstaller
import com.android.purebilibili.BuildConfig
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.lifecycle.BACKGROUND_COVER_COLOR_CACHE_MAX_ENTRIES
import com.android.purebilibili.core.lifecycle.BACKGROUND_IMAGE_CACHE_MAX_BYTES
import com.android.purebilibili.core.lifecycle.BACKGROUND_IMAGE_TRIM_DELAY_MS
import com.android.purebilibili.core.lifecycle.resolveBackgroundImageCacheTrimTargetBytes
import com.android.purebilibili.core.lifecycle.shouldTrimImageCacheAfterBackgroundDelay
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.store.DEFAULT_ANALYTICS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_CRASH_TRACKING_ENABLED
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.allManagedAppIconLauncherAliases
import com.android.purebilibili.core.store.DEFAULT_APP_ICON_KEY
import com.android.purebilibili.core.store.AppIconAppearance
import com.android.purebilibili.core.store.normalizeAppIconKey
import com.android.purebilibili.core.store.resolveAppIconLauncherAlias
import com.android.purebilibili.core.store.supportsAppIconAppearance
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CacheUtils
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.LogCollector
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.settings.applyAppLanguage
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.resolveThemeModePreference
import com.android.purebilibili.feature.plugin.AdFilterPlugin
import com.android.purebilibili.feature.plugin.Anime4KPlugin
import com.android.purebilibili.feature.plugin.CdnRegionPlugin
import com.android.purebilibili.feature.plugin.DanmakuEnhancePlugin
import com.android.purebilibili.feature.plugin.EyeProtectionPlugin
import com.android.purebilibili.feature.plugin.HomeFeedAnonymizerPlugin
import com.android.purebilibili.feature.plugin.SponsorBlockPlugin
import com.android.purebilibili.feature.plugin.dlna.DlnaCastPlugin
import com.android.purebilibili.feature.plugin.googlecast.GoogleCastPlugin
import com.android.purebilibili.feature.plugin.TodayWatchPlugin
import com.android.purebilibili.feature.settings.share.SettingsShareService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun shouldRefreshLauncherIconForNightModeChange(
    previousUiMode: Int,
    currentUiMode: Int
): Boolean {
    val previousNightMode = previousUiMode and Configuration.UI_MODE_NIGHT_MASK
    val currentNightMode = currentUiMode and Configuration.UI_MODE_NIGHT_MASK
    return previousNightMode != currentNightMode
}

//  实现 SingletonImageLoader.Factory 以提供自定义 Coil 配置
//  实现 ComponentCallbacks2 响应系统内存警告
class PureApplication : Application(), SingletonImageLoader.Factory, ComponentCallbacks2 {
    
    companion object {
        lateinit var instance: PureApplication
            private set
    }

    //  保存 ImageLoader 引用以便在 onTrimMemory 中使用
    private var _imageLoader: ImageLoader? = null
    private var launcherIconUiModeSnapshot: Int? = null

    private val backgroundImageTrimHandler by lazy { Handler(Looper.getMainLooper()) }
    private var enteredBackgroundForImageTrimAtMs = 0L
    private val delayedBackgroundImageTrim = Runnable {
        val miniPlayer = com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstanceOrNull()
        if (!StartupRecovery.isRecoveryMode && shouldTrimImageCacheAfterBackgroundDelay(
                isInBackground = BackgroundManager.isInBackground,
                isPipActiveOrPending = miniPlayer?.shouldKeepPlaybackForPipTransition() == true,
                backgroundElapsedMs = SystemClock.elapsedRealtime() - enteredBackgroundForImageTrimAtMs,
            )
        ) {
            // Keep a warm cache for return-to-home: covers must stay decodable without a
            // disk round-trip, otherwise the first bottom-tab switch after resume competes
            // with a decode storm. This never stops playback or clears a video surface.
            // 封面代表色表与壁纸调色板只占几 KB，重建代价却是「解码 + Palette 聚类」，
            // 因此不在这里清理。
            _imageLoader?.memoryCache?.apply {
                trimToSize(resolveBackgroundImageCacheTrimTargetBytes(size, BACKGROUND_IMAGE_TRIM_DELAY_MS))
            }
            // Raw bytes are reconstructible; active sessions retain their parsed playback window.
            // Run on sustained background as modern Android may not send pressure callbacks.
            com.android.purebilibili.data.repository.DanmakuContentRepository.clearCache()
            Logger.d(
                PureApplicationRuntimeConfig.TAG,
                "Sustained background: trimmed image cache to " +
                    "${BACKGROUND_IMAGE_CACHE_MAX_BYTES / (1024 * 1024)} MiB"
            )
        }
    }
    private val backgroundImageTrimListener = object : BackgroundManager.BackgroundStateListener {
        override fun onEnterBackground() {
            enteredBackgroundForImageTrimAtMs = SystemClock.elapsedRealtime()
            backgroundImageTrimHandler.removeCallbacks(delayedBackgroundImageTrim)
            // A cached process may be frozen before the delayed task can execute. Apply
            // the light budget synchronously while the process is still running.
            val miniPlayer = com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstanceOrNull()
            if (!StartupRecovery.isRecoveryMode && miniPlayer?.shouldKeepPlaybackForPipTransition() != true) {
                _imageLoader?.memoryCache?.apply {
                    trimToSize(resolveBackgroundImageCacheTrimTargetBytes(size, 0L))
                }
            }
            backgroundImageTrimHandler.postDelayed(delayedBackgroundImageTrim, BACKGROUND_IMAGE_TRIM_DELAY_MS)
        }

        override fun onEnterForeground() {
            backgroundImageTrimHandler.removeCallbacks(delayedBackgroundImageTrim)
        }
    }

    private val telemetryListener by lazy {
        PureApplicationRuntimeConfig.createTelemetryBackgroundStateListener()
    }

    private val startupOrchestrator by lazy { AppStartupOrchestrator() }
    
    //  Coil 图片加载器 - 优化内存和磁盘缓存
    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryCacheBytes = PureApplicationRuntimeConfig.resolveImageMemoryCacheMaxBytes(
            memoryClassMb = activityManager.memoryClass,
            isLowRamDevice = activityManager.isLowRamDevice
        )
        val diskCacheBytes = 100L * 1024 * 1024
        return ImageLoader.Builder(this)
            .components {
                // 共享网络客户端及 DNS 策略，保留 HTTP 缓存头语义。
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { NetworkModule.okHttpClient },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    )
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            //  内存缓存预算（移动/平板主仓）
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(memoryCacheBytes)
                    .build()
            }
            //  磁盘缓存预算（移动/平板主仓）
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheBytes)
                    .build()
            }
            // 显示用位图必须低于 Android Canvas 的单次绘制上限。长图与雪碧图按比例采样。
            .addLastModifiedToFileCacheKey(true)
            .maxBitmapSize(coil3.size.Size(4608, 4608))
            //  优先使用缓存
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            //  允许适用图片使用 RGB_565，降低内存占用
            .allowRgb565(true)
            .crossfade(true)
            .build()
            .also { _imageLoader = it }  // 保存引用
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        instance = this
        if (com.android.purebilibili.feature.message.notification.isLightweightServiceProcess(this)) {
            super.onCreate()
            com.android.purebilibili.feature.message.notification.initializeMessageNotificationProcess(this)
            return
        }

        // Compose 1.13's staggered-grid cache-window prefetch can assign an invalid lane
        // after rapid scroll-to-top and subsequent scrolling. Keep the established prefetcher
        // until the upstream cache-window path is safe for this feed.
        ComposeFoundationFlags.isUsingCacheWindowInStaggeredGrids = false

        // The matching release stack trace resolves to LazyGridKt.keepAroundItems (line 507)
        // calling LazyGridSpanLayoutProvider.getLineConfiguration with an invalid cache line.
        // Regular grids need their own fallback: the staggered-grid flag above does not apply.
        // Retain normal line prefetching while bypassing the default cache-window keepAround path.
        ComposeFoundationFlags.isPreferDefaultCacheWindowOverPrefetchStrategy = false

        // Install the local crash path before theme, StrictMode, or any other startup work. This
        // ensures even an early initialization exception has a private snapshot for feedback.
        LogCollector.init(this)
        CrashReporter.installGlobalExceptionHandler()
        // Apply stored collection choices before startup work or the first screen can report.
        CrashReporter.prepare(
            getSharedPreferences("crash_tracking", Context.MODE_PRIVATE)
                .getBoolean("enabled", DEFAULT_CRASH_TRACKING_ENABLED)
        )
        AnalyticsHelper.prepare(
            this,
            getSharedPreferences("analytics_tracking", Context.MODE_PRIVATE)
                .getBoolean("enabled", DEFAULT_ANALYTICS_ENABLED)
        )
        StartupRecovery.beginLaunch(this)
        if (StartupRecovery.isRecoveryMode) {
            super.onCreate()
            return
        }
        Logger.init(this)
        // 评论仓库已下沉 core-data；手机端的发评后反诈旁路在此注入，共享层不感知其实现。
        com.android.purebilibili.data.repository.CommentRepository.onCommentPosted =
            { reply, oid, type, root, parent, message ->
                AppScope.ioScope.launch {
                    com.android.purebilibili.data.repository.CommentFraudRepository.saveRecord(
                        rpid = reply.rpid,
                        oid = oid,
                        type = type,
                        root = root,
                        parent = parent,
                        uid = reply.mid,
                        message = message,
                        status = com.android.purebilibili.data.model.CommentFraudStatus.UNKNOWN, // 当前状态未知（检测中）
                        initialStatus = null, // 初始状态先置为 null (等待 5 秒后初检回填)
                        postTime = if (reply.ctime > 0L) reply.ctime * 1000L else System.currentTimeMillis()
                    )
                }
            }
        // 预热启动任务(wbi_key_restore)要在主线程同步读的 SP 文件:
        // IO 线程提前触发磁盘加载,主线程执行恢复时通常已命中内存缓存。
        AppScope.ioScope.launch {
            com.android.purebilibili.core.network.WbiKeyManager.prewarmStorage(this@PureApplication)
        }
        // 系统退出 Trace 的读取与脱敏可能很慢，不能阻塞 Application.onCreate。
        AppScope.ioScope.launch {
            com.android.purebilibili.core.performance.Android17Diagnostics
                .persistLatestAbnormalExitSnapshot(this@PureApplication)
        }

        // StrictMode 必须装在任何业务代码之前，否则紧接着的 applyThemePreference()
        // 里那次同步偏好读取就漏检了——而那恰恰是最该被看见的一处。
        installStrictModeForDebugBuilds()

        //  [关键] 必须在 super.onCreate() 之前设置！
        // 这样系统在初始化时就能读取到正确的夜间模式配置
        // 新用户内置默认值不再阻塞主线程:profile 不含 theme_mode/语言键,
        // applyThemePreference 不依赖它;应用动作移入 initializeNormalRuntime 的
        // 后台协程,并与首页视觉默认值迁移串行,保证内置 profile 先落地。
        applyThemePreference()
        
        super.onCreate()
        initializeNormalRuntime()
    }

    /** Called only by the private recovery Activity after an explicit user retry. */
    internal fun retryStartupFromRecovery() {
        if (!StartupRecovery.isRecoveryMode) return
        StartupRecovery.prepareRetry(this)
        Logger.init(this)
        installStrictModeForDebugBuilds()
        applyThemePreference()
        initializeNormalRuntime()
    }

    private fun initializeNormalRuntime() {
        launcherIconUiModeSnapshot = resources.configuration.uiMode
        AppScope.ioScope.launch {
            CacheUtils.clearCacheAutomaticallyIfDue(this@PureApplication)
        }

        // 启动即确保首页视觉默认值生效：底栏悬浮 + 液态玻璃 + 顶部模糊
        // 冷启动路径不阻塞主线程，迁移改为后台执行。
        // 首次运行的内置默认 profile 在同一协程内先于该迁移应用（串行），
        // 保证最终生效的是内置 profile 的取值,与旧的同步路径语义一致。
        if (PureApplicationRuntimeConfig.shouldBlockStartupForHomeVisualDefaultsMigration()) {
            runBlocking(Dispatchers.IO) {
                SettingsShareService(this@PureApplication)
                    .applyBundledDefaultIfNeeded()
                SettingsManager.ensureHomeVisualDefaults(this@PureApplication)
            }
        } else {
            AppScope.ioScope.launch {
                SettingsShareService(this@PureApplication)
                    .applyBundledDefaultIfNeeded()
                SettingsManager.ensureHomeVisualDefaults(this@PureApplication)
            }
        }
        startupOrchestrator.runImmediate(::runStartupTask)
        startupOrchestrator.scheduleDeferred(::runStartupTask)
    }

    /**
     * debug 包启用 StrictMode。
     *
     * 此前全仓 **0 处 StrictMode 引用**，意味着主线程磁盘 I/O 在开发期永远不会自动
     * 暴露，只能靠人肉 review 抓。这是「性能优化在盲打」的第三条证据——另两条是
     * Baseline Profile 从未产出产物、Compose 指标被注释掉。
     *
     * 刻意用 [StrictMode.ThreadPolicy.Builder.penaltyLog] 而不是 `penaltyDeath()`：
     * 现存违规的数量还未知（`SettingsManager` 的 `*Sync` 家族在 core/store 之外就有
     * 76 个调用点），直接 death 会让 debug 包起不来，结果必然是有人把整段删掉。
     * 等这批清干净后，再单独把 `detectDiskReads` 升级为 death 并配棘轮。
     *
     * 只在 debug 生效：release/smooth 包不受任何影响，这段在 R8 下会被整体裁掉。
     */
    private fun installStrictModeForDebugBuilds() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val previousUiMode = launcherIconUiModeSnapshot
        super.onConfigurationChanged(newConfig)
        if (com.android.purebilibili.feature.message.notification.isLightweightServiceProcess(this)) return
        if (StartupRecovery.isRecoveryMode) return
        launcherIconUiModeSnapshot = newConfig.uiMode
        if (
            previousUiMode != null &&
            shouldRefreshLauncherIconForNightModeChange(previousUiMode, newConfig.uiMode)
        ) {
            refreshActiveLauncherAliasForNightMode()
        }
    }

    private fun runStartupTask(task: AppStartupTask) {
        Logger.recordStartupStage(task.id)
        when (task.id) {
            "plugin_manager_context_init" -> PluginManager.initialize(this)
            "network_module_init" -> NetworkModule.init(
                this,
                com.android.purebilibili.core.network.CoreNetworkConfig(
                    debug = BuildConfig.DEBUG,
                    allowHardcodedDnsFallback = BuildConfig.ALLOW_HARDCODED_DNS_FALLBACK,
                    log = { level, tag, message, error ->
                        when (level) {
                            "D" -> com.android.purebilibili.core.util.Logger.d(tag, message)
                            "W" -> com.android.purebilibili.core.util.Logger.w(tag, message, error)
                            "E" -> com.android.purebilibili.core.util.Logger.e(tag, message, error)
                        }
                    },
                    reportApiError = { endpoint, code, message ->
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = endpoint, httpCode = code, errorMessage = message,
                        )
                    },
                    isPrivacyModeEnabled = SettingsManager::isPrivacyModeEnabledSync,
                ),
            )
            "token_manager_init" -> TokenManager.init(this)
            "wbi_key_restore" -> WbiKeyManager.restoreFromStorage(this)
            "video_repository_init" -> com.android.purebilibili.data.repository.VideoRepository.init(this)
            "background_manager_init" -> {
                BackgroundManager.init(this)
                BackgroundManager.addListener(backgroundImageTrimListener)
            }
            "player_settings_cache_init" -> com.android.purebilibili.core.store.PlayerSettingsCache.init(this)
            "notification_channel_init" -> createNotificationChannel()
            "message_notification_sync" -> AppScope.ioScope.launch {
                try {
                    com.android.purebilibili.feature.message.notification.MessageNotificationSync.sync(this@PureApplication)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("MessageNotification", "Unable to restore notification scheduling", e)
                }
            }
            "playlist_restore" -> initPlaylistRestoreNow()
            "telemetry_init" -> initTelemetryNow()
            "plugin_init" -> initPluginStackNow()
            "dex2oat_profile_install" -> requestDex2OatProfileInstallNow()
        }
    }

    private fun initPlaylistRestoreNow() {
        AppScope.ioScope.launch {
            com.android.purebilibili.feature.video.player.PlaylistManager.init(this@PureApplication)
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstance(this@PureApplication)
                    .recoverRemoteAudioIfPresent()
            }
        }
    }

    private fun initTelemetryNow() {
        initCrashlytics()
        val crashDiagnosticsEnabled = getSharedPreferences("crash_tracking", Context.MODE_PRIVATE)
            .getBoolean("enabled", DEFAULT_CRASH_TRACKING_ENABLED)
        com.android.purebilibili.core.performance.Android17Diagnostics.initialize(
            this,
            crashDiagnosticsEnabled
        )
        initAnalytics()
        attachTelemetryListener()
    }

    private fun initPluginStackNow() {
        PluginManager.register(SponsorBlockPlugin())
        PluginManager.register(AdFilterPlugin())
        PluginManager.register(Anime4KPlugin())
        PluginManager.register(DanmakuEnhancePlugin())
        PluginManager.register(EyeProtectionPlugin())
        PluginManager.register(TodayWatchPlugin())
        PluginManager.register(CdnRegionPlugin())
        PluginManager.register(HomeFeedAnonymizerPlugin())
        PluginManager.register(DlnaCastPlugin())
        PluginManager.register(GoogleCastPlugin())
        //  [BiliPai 移植] 推荐流过滤(默认关闭, 可在插件中心启用)
        PluginManager.register(com.android.purebilibili.feature.plugin.BiliPaiFeedFilterPlugin())
        PluginManager.register(com.android.purebilibili.feature.plugin.SubscriptionFeedPlugin())
        Logger.d(PureApplicationRuntimeConfig.TAG, " Plugin system initialized with 11 built-in plugins")

        com.android.purebilibili.core.plugin.json.JsonPluginManager.initialize(this)
        Logger.d(PureApplicationRuntimeConfig.TAG, " JSON plugin system initialized")

        AppScope.ioScope.launch {
            com.android.purebilibili.feature.download.DownloadManager.init(this@PureApplication)
            com.android.purebilibili.feature.download.DownloadManager.observeDownloadPath(this@PureApplication)
        }

        AppScope.ioScope.launch {
            val sponsorBlockEnabled = com.android.purebilibili.core.store.SettingsManager
                .getSponsorBlockEnabled(this@PureApplication)
                .first()
            PluginManager.setEnabled("sponsor_block", sponsorBlockEnabled)
            Logger.d(PureApplicationRuntimeConfig.TAG, " SponsorBlock plugin synced: enabled=$sponsorBlockEnabled")

            SettingsManager.forceDanmakuDefaults(this@PureApplication)
        }

        syncAppIconState()
    }

    private fun requestDex2OatProfileInstallNow() {
        runCatching {
            ProfileInstaller.writeProfile(this)
        }.onSuccess {
            Logger.d(PureApplicationRuntimeConfig.TAG, "📦 Requested ART profile installation for dex2oat")
        }.onFailure { throwable ->
            Logger.w(PureApplicationRuntimeConfig.TAG, "⚠️ ART profile installation request failed", throwable)
        }
    }

    private fun attachTelemetryListener() {
        // 监听全局前后台状态，增强会话与崩溃上下文
        BackgroundManager.addListener(telemetryListener)
        if (!BackgroundManager.isInBackground) {
            AnalyticsHelper.onAppForeground()
            CrashReporter.setAppForegroundState(true)
        }
    }
    
    //  初始化 Firebase Crashlytics
    private fun initCrashlytics() {
        try {
            //  读取用户设置（默认开启）
            val prefs = getSharedPreferences("crash_tracking", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("enabled", DEFAULT_CRASH_TRACKING_ENABLED)
            
            CrashReporter.setEnabled(enabled)
            CrashReporter.init(this)
            CrashReporter.installGlobalExceptionHandler()
            
            if (enabled) {
                CrashReporter.syncUserContext(
                    mid = TokenManager.midCache,
                    isVip = TokenManager.isVipCache,
                    privacyModeEnabled = SettingsManager.isPrivacyModeEnabledSync(this)
                )
            }
            
            Logger.d(PureApplicationRuntimeConfig.TAG, " Firebase Crashlytics initialized (enabled=$enabled)")
        } catch (e: Exception) {
            android.util.Log.e(PureApplicationRuntimeConfig.TAG, "Failed to init Crashlytics", e)
        }
    }
    
    // � 初始化 Firebase Analytics
    private fun initAnalytics() {
        try {
            //  读取用户设置（默认开启）
            val prefs = getSharedPreferences("analytics_tracking", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("enabled", DEFAULT_ANALYTICS_ENABLED)
            
            //  根据用户设置启用/禁用 Analytics
            AnalyticsHelper.init(this, enabled)
            
            if (enabled) {
                AnalyticsHelper.syncUserContext(
                    mid = TokenManager.midCache,
                    isVip = TokenManager.isVipCache,
                    privacyModeEnabled = SettingsManager.isPrivacyModeEnabledSync(this)
                )
                // 记录应用打开事件
                AnalyticsHelper.logAppOpen()
                AnalyticsHelper.logDailyActive(source = "app_start")
            }
            
            Logger.d(PureApplicationRuntimeConfig.TAG, " Firebase Analytics initialized (enabled=$enabled)")
        } catch (e: Exception) {
            android.util.Log.e(PureApplicationRuntimeConfig.TAG, "Failed to init Analytics", e)
        }
    }
    
    // [后台内存优化] 响应系统内存警告
    //
    // 关键取舍：UI_HIDDEN 只代表「用户切到桌面/多任务」，不是内存吃紧。此处不再清理任何缓存
    // ——后台预算收敛由 backgroundImageTrimListener 统一负责。否则用户切回前台时，封面要重新
    // 磁盘解码、代表色要重新聚类、请求要重新握手，正好和底栏切页动画抢主线程。
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (com.android.purebilibili.feature.message.notification.isLightweightServiceProcess(this)) return
        if (StartupRecovery.isRecoveryMode) return
        val plan = PureApplicationRuntimeConfig.resolveBackgroundMemoryTrimPlan(level)
        if (plan.imageCacheTrimLevel != null) {
            _imageLoader?.memoryCache?.apply {
                if (plan.clearImageMemoryCache) clear() else trimToSize(size / 2)
            }
            if (plan.clearImageMemoryCache) {
                Logger.d(PureApplicationRuntimeConfig.TAG, "🚨 trim(level=$level), released image memory cache")
            } else {
                Logger.d(PureApplicationRuntimeConfig.TAG, " Low memory trim(level=$level), trimmed image memory cache")
            }
        }
        if (plan.notifyPlayerHeavyOptimization) {
            com.android.purebilibili.feature.video.player.MiniPlayerManager
                .getInstanceOrNull()
                ?.onMemoryPressureTrim(
                    level = level,
                    requestIdlePlaybackRelease = plan.requestIdlePlaybackRelease
                )
        }

        // 联动清理全局静态与单例缓存，降低后台 PSS。仅在系统真的判定本进程已进入后台候选区
        // （BACKGROUND / MODERATE / COMPLETE）或前台临界（RUNNING_CRITICAL）时执行。
        // 壁纸调色板与封面代表色表体积可忽略，重建代价却是「解码 + Palette 聚类」，故不清理。
        if (plan.releaseDerivedCaches) {
            com.android.purebilibili.data.repository.DanmakuContentRepository.clearCache()
            com.android.purebilibili.feature.home.components.cards.VideoCardCoverColorStore
                .trimToSize(BACKGROUND_COVER_COLOR_CACHE_MAX_ENTRIES)
            com.android.purebilibili.core.cache.PlayUrlCache.clear()
            com.android.purebilibili.data.repository.VideoRepository.clearVideoDetailCaches()
            com.android.purebilibili.data.repository.VideoRepository.clearSubtitleCueCache()
        }

        // 释放 OkHttp 空闲连接与 socket 缓冲；没有活跃的后台音频播放时才做。
        // 清空连接池会让回前台的所有请求重新走 DNS/TCP/TLS，因此只跟随真实后台压力。
        if (plan.releaseIdleNetworkConnections) {
            val miniPlayer = com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstanceOrNull()
            val isAudioPlaying = miniPlayer?.let { it.isActive && it.player?.isPlaying == true } ?: false
            if (!isAudioPlaying) {
                NetworkModule.evictIdleConnections()
            }
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        if (com.android.purebilibili.feature.message.notification.isLightweightServiceProcess(this)) return
        if (StartupRecovery.isRecoveryMode) return
        val plan = PureApplicationRuntimeConfig.resolveBackgroundMemoryTrimPlan(
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        )
        _imageLoader?.memoryCache?.clear()
        com.android.purebilibili.data.repository.DanmakuContentRepository.clearCache()
        if (plan.notifyPlayerHeavyOptimization) {
            com.android.purebilibili.feature.video.player.MiniPlayerManager
                .getInstanceOrNull()
                ?.onMemoryPressureTrim(
                    level = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
                    requestIdlePlaybackRelease = plan.requestIdlePlaybackRelease
                )
        }
        com.android.purebilibili.feature.home.components.cards.VideoCardCoverColorStore
            .trimToSize(BACKGROUND_COVER_COLOR_CACHE_MAX_ENTRIES)
        com.android.purebilibili.core.cache.PlayUrlCache.clear()
        com.android.purebilibili.data.repository.VideoRepository.clearVideoDetailCaches()
        com.android.purebilibili.data.repository.VideoRepository.clearSubtitleCueCache()
        NetworkModule.evictIdleConnections()
        Logger.d(PureApplicationRuntimeConfig.TAG, "🚨 onLowMemory, cleared all caches")
    }

    private fun createNotificationChannel() {
        // 仅在 Android 8.0 (API 26) 及以上需要通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            resolveAppNotificationChannels().forEach { spec ->
                val channel = NotificationChannel(spec.id, spec.name, spec.importance).apply {
                    description = spec.description
                    setShowBadge(spec.showBadge)
                    if (spec.silent) {
                        setSound(null, null)
                    }
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    /**
     *  应用主题偏好 - 在 Splash Screen 显示前调用
     * 
     * 这解决了：用户在应用内强制深色模式，但系统是浅色时，启动屏仍然是白色的问题。
     * 通过 AppCompatDelegate.setDefaultNightMode() 强制系统使用正确的深色/浅色模式。
     */
    private fun applyThemePreference() {
        // 同步读取保存的主题设置（必须同步，因为 Splash Screen 马上就会显示）
        val prefs = getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
        val themeModeValue = prefs.getInt("theme_mode", 0)  // 0 = FOLLOW_SYSTEM
        val appLanguage = SettingsManager.getAppLanguageSync(this)
        val themeMode = resolveThemeModePreference(themeModeValue)
        
        val schedule = SettingsManager.getCachedDarkThemeSchedule(this)
        val localTime = java.util.Calendar.getInstance()
        val minuteOfDay = localTime.get(java.util.Calendar.HOUR_OF_DAY) * 60 + localTime.get(java.util.Calendar.MINUTE)
        val nightMode = if (schedule.enabled) {
            if (com.android.purebilibili.feature.settings.isScheduledDarkTheme(schedule, minuteOfDay)) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            } else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        } else when (themeMode) {
            AppThemeMode.FOLLOW_SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        
        applyAppLanguage(appLanguage)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
        Logger.d(
            PureApplicationRuntimeConfig.TAG,
            " Applied launch preferences: themeMode=$themeModeValue -> resolvedMode=$themeMode, nightMode=$nightMode, appLanguage=${appLanguage.name}"
        )
    }
    
    /**
     *  同步应用图标状态
     * 
     * 在 Application.onCreate 时调用，确保启动器图标与用户偏好一致。
     * 
     * 修复：重装后检测 icon 偏好与 Manifest 默认状态冲突，自动重置为默认图标。
     */
    private fun syncAppIconState() {
        // [Optim] Use IO dispatcher to prevent ANR during startup (PackageManager is heavy)
        AppScope.ioScope.launch {
            try {
                val pm = packageManager
                val packageName = this@PureApplication.packageName
                // 读取用户保存的图标偏好
                val currentIcon = normalizeAppIconKey(
                    SettingsManager.getAppIcon(this@PureApplication).first()
                )
                val appearance = SettingsManager.getAppIconAppearance(this@PureApplication).first()
                val defaultLauncherAlias = resolveAppIconLauncherAlias(
                    packageName = packageName,
                    rawKey = DEFAULT_APP_ICON_KEY,
                    appearance = appearance
                )
                val splashIconVisible = SettingsManager.isSplashIconAnimationEnabledSync(this@PureApplication)
                val cacheSynced = this@PureApplication
                    .getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putString("current_icon", currentIcon)
                    .putInt("appearance", appearance.storedValue)
                    .commit()
                Logger.d(PureApplicationRuntimeConfig.TAG, " Synced app icon cache from DataStore: $currentIcon (success=$cacheSynced)")

                val allUniqueAliases = allManagedAppIconLauncherAliases(packageName)
                val targetAlias = resolveAppIconLauncherAlias(
                    packageName = packageName,
                    rawKey = currentIcon,
                    splashIconVisible = splashIconVisible,
                    appearance = appearance
                )
                
                val targetAliasComponent = android.content.ComponentName(packageName, targetAlias)
                val targetState = pm.getComponentEnabledSetting(targetAliasComponent)

                // 如果目标 alias 是 disabled（说明之前被禁用了，可能是重装），强制重置为默认图标。
                if (currentIcon != DEFAULT_APP_ICON_KEY && targetState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    Logger.d(PureApplicationRuntimeConfig.TAG, " Detected reinstall: target icon '$currentIcon' is disabled, resetting to '$DEFAULT_APP_ICON_KEY'")
                    
                    SettingsManager.setAppIcon(this@PureApplication, DEFAULT_APP_ICON_KEY)
                    
                    // 确保默认图标被启用
                    val aliasDefault = android.content.ComponentName(packageName, defaultLauncherAlias)
                    pm.setComponentEnabledSetting(
                        aliasDefault,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    // 禁用其他所有alias
                    allUniqueAliases.filter { it != defaultLauncherAlias }.forEach { aliasFullName ->
                        pm.setComponentEnabledSetting(
                            android.content.ComponentName(packageName, aliasFullName),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    }
                    Logger.d(PureApplicationRuntimeConfig.TAG, " Reset to default icon: $DEFAULT_APP_ICON_KEY")
                    return@launch
                }
                
                // 同步所有 alias 状态：只有目标启用，其他禁用
                allUniqueAliases.forEach { aliasFullName ->
                    try {
                        val currentState = pm.getComponentEnabledSetting(
                            android.content.ComponentName(packageName, aliasFullName)
                        )
                        val shouldBeEnabled = aliasFullName == targetAlias
                        val targetState = if (shouldBeEnabled) {
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                        
                        // 只在状态不一致时修改，减少不必要的操作
                        if (currentState != targetState) {
                            pm.setComponentEnabledSetting(
                                android.content.ComponentName(packageName, aliasFullName),
                                targetState,
                                android.content.pm.PackageManager.DONT_KILL_APP
                            )
                        }
                    } catch (e: Exception) {
                        //  [容错] 忽略不存在的组件，防止崩溃
                        Logger.d(PureApplicationRuntimeConfig.TAG, "⚠️ Component $aliasFullName not found, skipping")
                    }
                }
                
                Logger.d(PureApplicationRuntimeConfig.TAG, " Synced app icon state: $currentIcon")
            } catch (e: Exception) {
                android.util.Log.e(PureApplicationRuntimeConfig.TAG, "Failed to sync app icon state", e)
            }
        }
    }

    private fun refreshActiveLauncherAliasForNightMode() {
        AppScope.ioScope.launch {
            val appearance = SettingsManager.getAppIconAppearanceSync(this@PureApplication)
            if (appearance != AppIconAppearance.FOLLOW_SYSTEM) return@launch
            val currentIcon = SettingsManager.getAppIconSync(this@PureApplication)
            if (!supportsAppIconAppearance(currentIcon)) return@launch
            val splashIconVisible = SettingsManager.isSplashIconAnimationEnabledSync(this@PureApplication)
            val alias = resolveAppIconLauncherAlias(
                packageName = packageName,
                rawKey = currentIcon,
                splashIconVisible = splashIconVisible,
                appearance = appearance
            )
            val component = ComponentName(packageName, alias)
            val pm = packageManager
            var aliasDisabled = false
            try {
                if (
                    pm.getComponentEnabledSetting(component) ==
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                ) {
                    Logger.w(
                        PureApplicationRuntimeConfig.TAG,
                        "Launcher icon refresh skipped because alias is disabled: $alias"
                    )
                    return@launch
                }
                pm.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                aliasDisabled = true
                delay(100)
            } catch (throwable: Exception) {
                Logger.e(
                    PureApplicationRuntimeConfig.TAG,
                    "Failed to invalidate launcher icon after night mode change",
                    throwable
                )
            } finally {
                if (aliasDisabled) {
                    runCatching {
                        pm.setComponentEnabledSetting(
                            component,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    }.onSuccess {
                        Logger.d(
                            PureApplicationRuntimeConfig.TAG,
                            "Launcher icon refreshed after night mode change: $alias"
                        )
                    }.onFailure { throwable ->
                        Logger.e(
                            PureApplicationRuntimeConfig.TAG,
                            "Failed to restore launcher icon alias after night mode change",
                            throwable
                        )
                    }
                }
            }
        }
    }
}
