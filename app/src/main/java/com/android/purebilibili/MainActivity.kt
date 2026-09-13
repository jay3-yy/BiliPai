// 文件路径: app/src/main/java/com/android/purebilibili/MainActivity.kt
package com.android.purebilibili

import androidx.compose.runtime.collectAsState

import android.animation.ValueAnimator
import android.app.PictureInPictureParams
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.metrics.performance.JankStats
import androidx.window.layout.WindowMetrics
import androidx.window.layout.WindowMetricsCalculator
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.coroutines.AppScope

import com.android.purebilibili.core.theme.LocalDisplayMetricsSnapshot
import com.android.purebilibili.core.theme.PureBiliBiliTheme
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.performance.AppRuntimeVisualGuardTracker
import com.android.purebilibili.core.ui.wallpaper.SplashWallpaperLayout
import com.android.purebilibili.core.ui.wallpaper.resolveSplashWallpaperLayout
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.plugin.EyeProtectionOverlay
import com.android.purebilibili.feature.plugin.PluginEffectHintHost
import com.android.purebilibili.app.StartupRecovery
import com.android.purebilibili.feature.settings.diagnostics.StartupRecoveryActivity
import com.android.purebilibili.feature.settings.AppUpdateAutoCheckGate
import com.android.purebilibili.feature.settings.AppUpdateCheckResult
import com.android.purebilibili.feature.settings.AppUpdateChecker
import com.android.purebilibili.feature.settings.AppUpdateDialogHost
import com.android.purebilibili.feature.settings.AppUpdateDownloadState
import com.android.purebilibili.feature.settings.AppUpdateDownloadStatus
import com.android.purebilibili.feature.settings.AppUpdateInstallAction
import com.android.purebilibili.feature.settings.AppLanguage
import com.android.purebilibili.feature.settings.applyAppLanguage
import com.android.purebilibili.core.theme.resolveEffectiveDynamicColorEnabled
import com.android.purebilibili.core.theme.buildDisplayMetricsSnapshot
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.AppWindowSystemUiController
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.LocalAppSingleChoicePresentation
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.ProvideUnifiedBlurIntensity
import com.android.purebilibili.core.ui.performance.ProvideRuntimeVisualGuard
import com.android.purebilibili.core.util.BilibiliUrlParser
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.calculateWindowSizeClass
import com.android.purebilibili.core.util.rememberAppWindowAdaptiveInfo
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.cast.LocalProxyServer
import com.android.purebilibili.feature.onboarding.USER_AGREEMENT_ACK_KEY
import com.android.purebilibili.feature.settings.RELEASE_DISCLAIMER_ACK_KEY
import com.android.purebilibili.feature.settings.completeAppUpdateDownload
import com.android.purebilibili.feature.settings.downloadAppUpdateApk
import com.android.purebilibili.feature.settings.failAppUpdateDownload
import com.android.purebilibili.feature.settings.installDownloadedAppUpdate
import com.android.purebilibili.feature.settings.resolveAppUpdateDialogTextColors
import com.android.purebilibili.feature.settings.resolveBuildSourceSubtitle
import com.android.purebilibili.feature.settings.resolveBuildSourceValue
import com.android.purebilibili.feature.settings.resolveUpdateReleaseNotesText
import com.android.purebilibili.feature.settings.selectPreferredAppUpdateAsset
import com.android.purebilibili.feature.settings.shouldRunAppEntryAutoCheck
import com.android.purebilibili.feature.settings.resolveThemePreferenceState
import com.android.purebilibili.core.theme.resolveMd3DynamicColorEnabled
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureBlockState
import com.android.purebilibili.feature.screenshot.AppScreenshotResult
import com.android.purebilibili.feature.screenshot.AppScreenshotSavedImage
import com.android.purebilibili.feature.screenshot.AppScreenshotRegionOverlay
import com.android.purebilibili.feature.screenshot.appScreenshotGestureDetector
import com.android.purebilibili.feature.screenshot.captureAndSaveAppScreenshotImage
import com.android.purebilibili.feature.screenshot.captureCurrentAppWindow
import com.android.purebilibili.feature.screenshot.cropAppScreenshotBitmap
import com.android.purebilibili.feature.screenshot.saveAppScreenshotBitmapToGalleryUri
import com.android.purebilibili.feature.screenshot.shareAppScreenshot
import com.android.purebilibili.feature.screenshot.shouldOfferAppScreenshotShare
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationReason
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationRequest
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationResult
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.controller.PlaybackProgressManager
import com.android.purebilibili.feature.video.handoff.PlaybackHandoffCodec
import com.android.purebilibili.feature.video.handoff.PlaybackHandoffPayload
import com.android.purebilibili.feature.video.handoff.PlaybackHandoffRegistry
import com.android.purebilibili.feature.video.player.buildPipPlaybackRemoteActions
import com.android.purebilibili.feature.video.ui.overlay.FullscreenPlayerOverlay
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingVisible
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.ui.overlay.MiniPlayerOverlay
import com.android.purebilibili.navigation.AppNavigation
import com.android.purebilibili.navigation.ScreenRoutes
import com.android.purebilibili.navigation.VideoRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private const val TAG = "MainActivity"
private const val PREFS_NAME = "app_welcome"
private const val KEY_FIRST_LAUNCH = "first_launch_shown"
internal const val EXTRA_PENDING_NAVIGATION_ROUTE = "pending_navigation_route"
internal const val EXTRA_OPEN_ACTIVE_PLAYBACK = "open_active_playback"
private val PLUGIN_INSTALL_HTTPS_HOSTS = setOf(
    "bilipai.app",
    "www.bilipai.app",
    "plugins.bilipai.app"
)

internal fun resolveDrawableAspectRatio(width: Int, height: Int): Float? {
    if (width <= 0 || height <= 0) return null
    return width.toFloat() / height.toFloat()
}

internal fun resolveShortcutRoute(host: String): String? {
    return when (host) {
        "search" -> ScreenRoutes.Search.route
        "dynamic" -> ScreenRoutes.Dynamic.route
        "favorite" -> ScreenRoutes.Favorite.route
        "history" -> ScreenRoutes.History.route
        "watch_later" -> ScreenRoutes.WatchLater.route
        "login" -> ScreenRoutes.Login.route
        "playback" -> ScreenRoutes.PlaybackSettings.route
        "plugins" -> ScreenRoutes.PluginsSettings.createRoute()
        else -> null
    }
}

internal data class PluginInstallDeepLinkRequest(
    val pluginUrl: String
)

internal fun resolvePluginInstallDeepLink(rawDeepLink: String): PluginInstallDeepLinkRequest? {
    val uri = runCatching { URI(rawDeepLink) }.getOrNull() ?: return null
    val normalizedScheme = uri.scheme?.lowercase() ?: return null
    val normalizedHost = uri.host?.lowercase() ?: return null
    val normalizedPath = uri.path?.trim()?.trimEnd('/') ?: ""

    val installLinkMatched = when (normalizedScheme) {
        "bilipai" -> normalizedHost == "plugin" && normalizedPath == "/install"
        "https", "http" -> normalizedHost in PLUGIN_INSTALL_HTTPS_HOSTS &&
            normalizedPath in setOf("/plugin/install", "/plugins/install")
        else -> false
    }
    if (!installLinkMatched) return null

    val queryMap = uri.rawQuery
        ?.split("&")
        ?.mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val pair = part.split("=", limit = 2)
            val key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
            key to value
        }
        ?.toMap()
        ?: emptyMap()

    val rawUrl = queryMap["url"]?.trim().orEmpty()
    if (rawUrl.isBlank()) return null

    val targetUri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    val scheme = targetUri.scheme?.lowercase()
    if (scheme !in listOf("http", "https") || targetUri.host.isNullOrBlank()) {
        return null
    }
    return PluginInstallDeepLinkRequest(pluginUrl = rawUrl)
}

internal fun shouldNavigateToVideoFromNotification(
    currentRoute: String?,
    currentBvid: String?,
    targetBvid: String
): Boolean {
    val isInVideoRoute = currentRoute?.substringBefore("/") == VideoRoute.base
    return !(isInVideoRoute && currentBvid == targetBvid)
}

internal fun resolveMainActivityVideoRoute(
    bvid: String,
    cid: Long,
    startFullscreen: Boolean = false,
    resumePositionMs: Long = 0L,
): String {
    return VideoRoute.resolveVideoRoutePath(
        bvid = bvid,
        cid = cid,
        encodedCover = "",
        startAudio = false,
        autoPortrait = true,
        fullscreen = startFullscreen,
        resumePositionMs = resumePositionMs.coerceAtLeast(0L)
    )
}

internal fun resolveActivePlaybackReturnRoute(
    isActive: Boolean,
    bvid: String?,
    cid: Long,
    currentPositionMs: Long,
): String? {
    val safeBvid = bvid?.trim().orEmpty()
    if (!isActive || safeBvid.isBlank() || cid <= 0L) return null
    return resolveMainActivityVideoRoute(
        bvid = safeBvid,
        cid = cid,
        resumePositionMs = currentPositionMs,
    )
}

internal fun resolveMiniPlayerExpandVideoRoute(
    bvid: String,
    cid: Long
): String {
    return resolveMainActivityVideoRoute(
        bvid = bvid,
        cid = cid,
        startFullscreen = true
    )
}

internal fun resolveMainActivityDynamicRoute(dynamicId: String): String {
    val encodedDynamicId = URLEncoder.encode(dynamicId, StandardCharsets.UTF_8.toString())
    return "dynamic_detail/$encodedDynamicId"
}

internal fun resolveIntentLinkFallbackRoute(rawInput: String): String? {
    val fallbackUrl = resolveIntentLinkFallbackUrl(rawInput) ?: return null
    return ScreenRoutes.Web.createRoute(fallbackUrl)
}

internal fun resolveIntentLinkFallbackUrl(rawInput: String): String? {
    val directCandidate = normalizeIntentLinkWebCandidate(rawInput)
    if (directCandidate != null) return directCandidate

    return BilibiliUrlParser.extractUrls(rawInput)
        .firstNotNullOfOrNull(::normalizeIntentLinkWebCandidate)
}

private fun normalizeIntentLinkWebCandidate(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) return null
    val candidate = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        "://" in trimmed -> trimmed
        trimmed.startsWith("b23.tv/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("www.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("m.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        else -> return null
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme !in setOf("http", "https")) return null
    val host = uri.host?.lowercase().orEmpty()
    if (!host.contains("b23.tv") && !host.contains("bilibili.com")) return null
    return candidate
}

internal data class MainActivityLinkNavigation(
    val pendingVideoId: String? = null,
    val pendingNavigationRoute: String? = null,
    val pendingSearchKeyword: String? = null
)

internal fun resolveMainActivityLinkNavigation(
    target: BilibiliNavigationTarget
): MainActivityLinkNavigation? {
    return when (target) {
        is BilibiliNavigationTarget.Video -> MainActivityLinkNavigation(
            pendingVideoId = target.videoId
        )

        is BilibiliNavigationTarget.Dynamic -> MainActivityLinkNavigation(
            pendingNavigationRoute = resolveMainActivityDynamicRoute(target.dynamicId)
        )

        is BilibiliNavigationTarget.Search -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.Search.route,
            pendingSearchKeyword = target.keyword
        )

        is BilibiliNavigationTarget.Space -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.Space.createRoute(target.mid)
        )

        is BilibiliNavigationTarget.Live -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.Live.createRoute(
                roomId = target.roomId,
                title = "",
                uname = ""
            )
        )

        is BilibiliNavigationTarget.BangumiSeason -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.BangumiDetail.createRoute(
                seasonId = target.seasonId,
                mediaId = target.mediaId
            )
        )

        is BilibiliNavigationTarget.BangumiEpisode -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.BangumiDetail.createRoute(
                seasonId = 0,
                epId = target.epId
            )
        )

        is BilibiliNavigationTarget.Music -> {
            val auSid = target.musicId.removePrefix("au").removePrefix("AU").toLongOrNull() ?: return null
            MainActivityLinkNavigation(
                pendingNavigationRoute = ScreenRoutes.MusicDetail.createRoute(auSid)
            )
        }

        is BilibiliNavigationTarget.Article -> MainActivityLinkNavigation(
            pendingNavigationRoute = ScreenRoutes.ArticleDetail.createRoute(target.articleId)
        )
    }
}

internal fun shouldForceStopPlaybackOnUserLeaveHint(
    isInVideoDetail: Boolean,
    stopPlaybackOnExit: Boolean,
    shouldTriggerPip: Boolean
): Boolean {
    // `onUserLeaveHint()` also fires when the user temporarily switches apps.
    // "Stop playback when leaving the playback page" should only apply to
    // explicit in-app navigation, which is handled by dedicated navigation hooks.
    return false
}

internal fun shouldRestorePlaybackRouteStateOnResume(
    isPlaybackRouteActive: Boolean
): Boolean = isPlaybackRouteActive

internal fun shouldRestoreMutedPlaybackPlayerVolumeOnResume(
    playerVolume: Float
): Boolean = playerVolume <= 0f

internal fun isPlaybackRouteActive(
    isInVideoDetail: Boolean,
    isInAudioMode: Boolean
): Boolean = isInVideoDetail || isInAudioMode

internal fun shouldTriggerPlaybackRoutePip(
    isInVideoDetail: Boolean,
    isInAudioMode: Boolean,
    isInMiniMode: Boolean,
    audioModeAutoPipEnabled: Boolean,
    shouldEnterPip: Boolean,
    isActuallyPlaying: Boolean
): Boolean {
    if (!shouldEnterPip || !isActuallyPlaying) return false
    if (isInVideoDetail || isInMiniMode) return true
    return isInAudioMode && audioModeAutoPipEnabled
}

internal data class MainActivityPlaybackOverlayState(
    val showMiniPlayerOverlay: Boolean,
    val showDedicatedPipPlayer: Boolean
)

internal fun resolveMainActivityPlaybackOverlayState(
    isInPipMode: Boolean,
    isMiniMode: Boolean,
    showAudioNowPlaying: Boolean = false
): MainActivityPlaybackOverlayState {
    return MainActivityPlaybackOverlayState(
        showMiniPlayerOverlay = !isInPipMode && !showAudioNowPlaying,
        // 从首页小窗进入系统 PiP 时，原详情页已销毁，需要独立渲染面承接同一个 Player。
        showDedicatedPipPlayer = isInPipMode && isMiniMode
    )
}

internal enum class CrashLogPromptAction {
    SHARE,
    DISMISS,
    IGNORE
}

internal fun shouldShowPendingCrashLogPrompt(
    hasPendingCrashSnapshot: Boolean,
    hasPromptBeenHandled: Boolean
): Boolean = hasPendingCrashSnapshot && !hasPromptBeenHandled

internal fun shouldClearPendingCrashLogAfterAction(
    action: CrashLogPromptAction
): Boolean = action != CrashLogPromptAction.IGNORE

internal fun shouldUseRealtimeSplashBlur(
    sdkInt: Int,
    manufacturer: String = ""
): Boolean {
    // Samsung One UI 5 (Android 13 / API 33) has a known HWUI/Vulkan driver issue where dynamic
    // RenderEffect blur creation triggers SIGSEGV (status 11) in libhwui / vulkan.adreno.so.
    if (sdkInt == 33 && manufacturer.equals("samsung", ignoreCase = true)) {
        return false
    }
    return sdkInt >= Build.VERSION_CODES.S && sdkInt < 36
}

internal fun resolveSplashIconResIdForComponentClassName(className: String?): Int {
    return when (className?.substringAfterLast('.')) {
        "MainActivityAliasBlueSnowMaid",
        "MainActivityAliasBlueSnowMaidNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid
        "MainActivitySplashBlueSnowMaid" -> R.drawable.splash_icon_blue_snow_maid
        "MainActivityAliasBlueSnowMaidAnnouncement",
        "MainActivityAliasBlueSnowMaidAnnouncementNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_announcement
        "MainActivitySplashBlueSnowMaidAnnouncement" -> R.drawable.splash_icon_blue_snow_maid_announcement
        "MainActivityAliasBlueSnowMaidAnnouncementLight",
        "MainActivityAliasBlueSnowMaidAnnouncementLightNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_announcement_light
        "MainActivitySplashBlueSnowMaidAnnouncementLight" -> R.drawable.splash_icon_blue_snow_maid_announcement_light
        "MainActivityAliasBlueSnowMaidAnnouncementDark",
        "MainActivityAliasBlueSnowMaidAnnouncementDarkNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_announcement_dark
        "MainActivitySplashBlueSnowMaidAnnouncementDark" -> R.drawable.splash_icon_blue_snow_maid_announcement_dark
        "MainActivityAliasBlueSnowMaidLight",
        "MainActivityAliasBlueSnowMaidLightNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_light
        "MainActivitySplashBlueSnowMaidLight" -> R.drawable.splash_icon_blue_snow_maid_light
        "MainActivityAliasBlueSnowMaidDark",
        "MainActivityAliasBlueSnowMaidDarkNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_dark
        "MainActivitySplashBlueSnowMaidDark" -> R.drawable.splash_icon_blue_snow_maid_dark
        "MainActivityAliasBlueSnowMaidFront",
        "MainActivityAliasBlueSnowMaidFrontNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_front
        "MainActivitySplashBlueSnowMaidFront" -> R.drawable.splash_icon_blue_snow_maid_front
        "MainActivityAliasBlueSnowMaidFrontLight",
        "MainActivityAliasBlueSnowMaidFrontLightNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_front_light
        "MainActivitySplashBlueSnowMaidFrontLight" -> R.drawable.splash_icon_blue_snow_maid_front_light
        "MainActivityAliasBlueSnowMaidFrontDark",
        "MainActivityAliasBlueSnowMaidFrontDarkNoIcon" -> R.mipmap.ic_launcher_blue_snow_maid_front_dark
        "MainActivitySplashBlueSnowMaidFrontDark" -> R.drawable.splash_icon_blue_snow_maid_front_dark
        "MainActivityAlias3DLauncher",
        "MainActivityAlias3D",
        "MainActivityAlias3DNoIcon",
        "MainActivitySplashIcon3D" -> R.mipmap.ic_launcher_3d
        "MainActivityAliasBiliPai",
        "MainActivityAliasBiliPaiNoIcon",
        "MainActivitySplashBiliPai" -> R.mipmap.ic_launcher_bilipai
        "MainActivityAliasBiliPaiPink",
        "MainActivityAliasBiliPaiPinkNoIcon",
        "MainActivitySplashBiliPaiPink" -> R.mipmap.ic_launcher_bilipai_pink
        "MainActivityAliasBiliPaiWhite",
        "MainActivityAliasBiliPaiWhiteNoIcon",
        "MainActivitySplashBiliPaiWhite" -> R.mipmap.ic_launcher_bilipai_white
        "MainActivityAliasBiliPaiMonet",
        "MainActivityAliasBiliPaiMonetNoIcon" -> R.mipmap.ic_launcher_bilipai_monet
        "MainActivitySplashBiliPaiMonet" -> R.mipmap.splash_icon_bilipai_monet
        "MainActivityAliasFlat",
        "MainActivityAliasFlatNoIcon",
        "MainActivityAliasTelegramBlue",
        "MainActivityAliasTelegramBlueNoIcon",
        "MainActivityAliasDark",
        "MainActivityAliasDarkNoIcon",
        "MainActivityAliasYuki",
        "MainActivityAliasYukiNoIcon",
        "MainActivityAliasAnime",
        "MainActivityAliasAnimeNoIcon",
        "MainActivityAliasHeadphone",
        "MainActivityAliasHeadphoneNoIcon" -> R.mipmap.ic_launcher_3d
        else -> 0
    }
}

@Suppress("DEPRECATION")
internal fun resolveLaunchIconResId(context: Context, launchIntent: Intent?): Int {
    resolveSplashIconResIdForComponentClassName(context::class.java.name)
        .takeIf { it != 0 }
        ?.let { return it }

    resolveSplashIconResIdForComponentClassName(launchIntent?.component?.className)
        .takeIf { it != 0 }
        ?.let { return it }

    val fromLaunchComponent = runCatching {
        launchIntent?.component
            ?.let { context.packageManager.getActivityInfo(it, 0).getIconResource() }
            ?: 0
    }.getOrDefault(0)
    if (fromLaunchComponent != 0) return fromLaunchComponent

    return context.applicationInfo.icon
}

internal fun shouldShowCustomSplashOverlay(
    customSplashEnabled: Boolean,
    splashUri: String
): Boolean {
    // Flyout animation and custom splash wallpaper can coexist:
    // system splash flyout exits first, then custom wallpaper overlay fades out.
    return customSplashEnabled && splashUri.isNotEmpty()
}

internal fun shouldReadCustomSplashPreferences(): Boolean {
    return true
}

internal fun resolveSplashWallpaperAlignmentBias(
    isTabletLayout: Boolean,
    mobileBias: Float,
    tabletBias: Float
): Float {
    return if (isTabletLayout) tabletBias else mobileBias
}

internal fun resolveSplashWallpaperUriForLaunch(
    randomEnabled: Boolean,
    fixedSplashUri: String,
    poolUris: List<String>,
    launchSeed: Long
): String {
    if (!randomEnabled) return fixedSplashUri
    val candidates = poolUris
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (candidates.isEmpty()) return fixedSplashUri
    val index = Math.floorMod(launchSeed, candidates.size.toLong()).toInt()
    return candidates[index]
}

internal fun shouldStartLocalProxyOnAppLaunch(): Boolean = false

internal fun shouldEnableSplashFlyoutAnimation(
    sdkInt: Int,
    hasCompletedOnboarding: Boolean,
    hasAcceptedReleaseDisclaimer: Boolean,
    splashIconAnimationEnabled: Boolean
): Boolean {
    if (!splashIconAnimationEnabled) return false
    if (sdkInt < Build.VERSION_CODES.S) return false
    return hasCompletedOnboarding && hasAcceptedReleaseDisclaimer
}

internal fun shouldKeepSystemSplashForPreload(
    runColdStartSplash: Boolean
): Boolean {
    // The native splash background is still the cold-start handoff when both the optional
    // launcher icon animation and custom wallpaper are disabled.
    return runColdStartSplash
}

internal fun shouldApplySplashRealtimeBlur(
    useRealtimeBlur: Boolean,
    progress: Float
): Boolean = useRealtimeBlur && splashExitBlurProgress(progress) > 0f

internal fun shouldRunColdStartSplash(savedInstanceStatePresent: Boolean): Boolean = !savedInstanceStatePresent

internal fun splashExitDurationMs(): Long = 920L
internal fun splashExitTranslateYDp(): Float = 220f
internal fun splashExitScaleEnd(): Float = 1.12f
internal fun splashExitBlurRadiusEnd(): Float = 24f
internal fun splashMaxKeepOnScreenMs(): Long = 1000L
internal fun customSplashHoldDurationMs(): Long = 1900L
internal fun customSplashFadeDurationMs(): Int = 1450

internal fun customSplashShouldRender(
    showSplash: Boolean,
    overlayAlpha: Float
): Boolean = showSplash || overlayAlpha > 0.01f

internal fun customSplashFadeProgress(overlayAlpha: Float): Float {
    return (1f - overlayAlpha).coerceIn(0f, 1f)
}

internal fun customSplashOverlayScale(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return 1f + (0.024f * normalized.pow(1.08f))
}

internal fun customSplashOverlayScrimAlpha(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return (0.14f * normalized.pow(1.2f)).coerceIn(0f, 0.16f)
}

internal fun customSplashExtraBlurDp(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return (14f * normalized.pow(1.1f)).coerceAtLeast(0f)
}

internal fun splashExitTravelDistancePx(
    splashHeightPx: Int,
    targetSizePx: Int,
    minTravelPx: Float
): Float {
    if (splashHeightPx <= 0) return minTravelPx
    // Center icon needs to pass the top edge and leave some margin to feel like a full fly-out.
    val dynamicTravel = (splashHeightPx / 2f) + targetSizePx + 24f
    return max(minTravelPx, dynamicTravel)
}

internal fun splashExitBlurProgress(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    val sharpHoldProgress = 0.10f
    if (normalized <= sharpHoldProgress) return 0f
    val blurProgress = ((normalized - sharpHoldProgress) / (1f - sharpHoldProgress))
        .coerceIn(0f, 1f)
    return blurProgress.pow(1.45f)
}

internal fun splashExitIconAlpha(progress: Float): Float {
    if (progress <= 0.12f) return 1f
    val normalized = ((progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
    return (1f - normalized.pow(1.6f)).coerceIn(0f, 1f)
}

internal fun splashExitBackgroundAlpha(progress: Float): Float {
    if (progress <= 0.18f) return 1f
    val normalized = ((progress - 0.18f) / 0.82f).coerceIn(0f, 1f)
    return (1f - normalized.pow(1.1f)).coerceIn(0f, 1f)
}

internal fun splashFlyoutCornerRadiusPx(sizePx: Int): Float {
    return sizePx.coerceAtLeast(0) * 0.24f
}

internal fun resolveSplashFlyoutTargetSizePx(
    systemIconWidthPx: Int,
    systemIconHeightPx: Int,
    density: Float,
): Int {
    val safeDensity = density.coerceAtLeast(0.1f)
    val fallbackSizePx = (112f * safeDensity).roundToInt().coerceAtLeast(1)
    if (systemIconWidthPx <= 0 || systemIconHeightPx <= 0) return fallbackSizePx

    val shortSidePx = minOf(systemIconWidthPx, systemIconHeightPx)
    val longSidePx = maxOf(systemIconWidthPx, systemIconHeightPx)
    val isLauncherLikeSquare = longSidePx <= shortSidePx * 1.2f
    val shortSideDp = shortSidePx / safeDensity
    return if (isLauncherLikeSquare && shortSideDp in 72f..160f) {
        shortSidePx
    } else {
        fallbackSizePx
    }
}

private fun applySplashFlyoutRoundedClip(view: View) {
    view.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val sizePx = minOf(view.width, view.height)
            outline.setRoundRect(
                0,
                0,
                view.width,
                view.height,
                splashFlyoutCornerRadiusPx(sizePx)
            )
        }
    }
    view.clipToOutline = true
    view.invalidateOutline()
}

internal fun splashTrailPrimaryAlpha(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    if (normalized <= 0.08f) return 0f
    val trailProgress = ((normalized - 0.08f) / 0.92f).coerceIn(0f, 1f)
    return (0.34f * (1f - trailProgress).pow(1.15f)).coerceIn(0f, 1f)
}

internal fun splashTrailSecondaryAlpha(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    if (normalized <= 0.16f) return 0f
    val trailProgress = ((normalized - 0.16f) / 0.84f).coerceIn(0f, 1f)
    return (0.2f * (1f - trailProgress).pow(1.22f)).coerceIn(0f, 1f)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applySplashRealtimeBlur(
    animatedTarget: View,
    primaryTrailView: View?,
    secondaryTrailView: View?,
    radius: Float
) {
    if (radius < 0.5f) return
    if (!animatedTarget.isAttachedToWindow) return
    animatedTarget.setRenderEffect(
        RenderEffect.createBlurEffect(
            radius * 0.62f,
            radius * 0.62f,
            Shader.TileMode.CLAMP
        )
    )
    if (primaryTrailView?.isAttachedToWindow == true) {
        primaryTrailView.setRenderEffect(
            RenderEffect.createBlurEffect(
                radius,
                radius,
                Shader.TileMode.CLAMP
            )
        )
    }
    if (secondaryTrailView?.isAttachedToWindow == true) {
        secondaryTrailView.setRenderEffect(
            RenderEffect.createBlurEffect(
                radius * 1.2f,
                radius * 1.2f,
                Shader.TileMode.CLAMP
            )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun clearSplashRealtimeBlur(
    animatedTarget: View,
    primaryTrailView: View?,
    secondaryTrailView: View?
) {
    if (animatedTarget.isAttachedToWindow) {
        animatedTarget.setRenderEffect(null)
    }
    if (primaryTrailView?.isAttachedToWindow == true) {
        primaryTrailView.setRenderEffect(null)
    }
    if (secondaryTrailView?.isAttachedToWindow == true) {
        secondaryTrailView.setRenderEffect(null)
    }
}

internal enum class SplashFlyoutTargetType {
    SYSTEM_ICON,
    FALLBACK_ICON,
    SPLASH_ROOT
}

internal fun resolveSplashFlyoutTargetType(
    hasSystemIcon: Boolean,
    hasFallbackIcon: Boolean
): SplashFlyoutTargetType {
    return when {
        hasSystemIcon -> SplashFlyoutTargetType.SYSTEM_ICON
        hasFallbackIcon -> SplashFlyoutTargetType.FALLBACK_ICON
        else -> SplashFlyoutTargetType.SPLASH_ROOT
    }
}

internal fun shouldLogWarmResume(
    hasCompletedInitialResume: Boolean,
    isChangingConfigurations: Boolean
): Boolean {
    return hasCompletedInitialResume && !isChangingConfigurations
}

internal fun resolveMainActivitySystemInDarkTheme(uiMode: Int): Boolean {
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

internal fun shouldRefreshMainActivitySystemThemeSnapshot(
    previousSystemInDark: Boolean,
    currentSystemInDark: Boolean
): Boolean {
    return previousSystemInDark != currentSystemInDark
}

@OptIn(UnstableApi::class) // 解决 UnsafeOptInUsageError，因为 AppNavigation 内部使用了不稳定的 API
open class MainActivity : AppCompatActivity() {
    private var startupRecoveryRedirected = false
    
    //  PiP 状态
    var isInPipMode by mutableStateOf(false)
        private set
    
    //  是否在视频页面 (用于决定是否进入 PiP)
    var isInVideoDetail by mutableStateOf(false)
    var isInAudioModeRoute by mutableStateOf(false)
    
    //  小窗管理器
    private lateinit var miniPlayerManager: MiniPlayerManager
    private var hasCompletedInitialResume = false
    private var splashFlyoutEnabledAtCreate = false
    private var splashExitCallbackTriggered = false
    private var systemInDarkThemeSnapshot by mutableStateOf(false)
    private var runtimeJankStats: JankStats? = null
    private val runtimeVisualGuardSession = Any()
    private var android17HandoffEnabled = false

    var windowMetrics: WindowMetrics? by mutableStateOf(null)

    private fun currentPlaybackHandoffPayload(): PlaybackHandoffPayload? {
        PlaybackHandoffRegistry.currentBangumiPayload()?.let { return it }
        if (!isInVideoDetail && !isInAudioModeRoute) return null
        if (!::miniPlayerManager.isInitialized || miniPlayerManager.isLiveMode) return null
        if (!miniPlayerManager.isActive) return null
        val bvid = miniPlayerManager.currentBvid?.trim().orEmpty()
        val cid = miniPlayerManager.currentCid
        if (bvid.isBlank() || cid <= 0L) return null
        val durationMs = miniPlayerManager.duration.coerceAtLeast(0L)
        val positionMs = miniPlayerManager.player?.currentPosition
            ?.coerceAtLeast(0L)
            ?.let { position -> if (durationMs > 0L) position.coerceAtMost(durationMs) else position }
            ?: miniPlayerManager.currentPosition.coerceAtLeast(0L)
        return PlaybackHandoffPayload.V1.Video(
            bvid = bvid,
            cid = cid,
            resumePositionMs = positionMs,
            startAudio = isInAudioModeRoute
        )
    }

    private fun refreshAndroid17HandoffAvailability() {
        if (Build.VERSION.SDK_INT < 37) return
        val shouldEnable = currentPlaybackHandoffPayload() != null
        if (android17HandoffEnabled == shouldEnable) return
        val params = if (shouldEnable) {
            HandoffActivityParams.Builder()
                .setAllowHandoffWithoutPackageInstalled(true)
                .build()
        } else {
            null
        }
        setHandoffEnabled(shouldEnable, params)
        android17HandoffEnabled = shouldEnable
    }

    @RequiresApi(37)
    override fun onHandoffActivityDataRequested(
        handoffRequestInfo: HandoffActivityDataRequestInfo
    ): HandoffActivityData {
        val payload = currentPlaybackHandoffPayload()
        return if (payload != null) {
            PlaybackHandoffCodec.toPlatformData(payload, componentName)
        } else {
            HandoffActivityData.createWebHandoff(Uri.parse("https://www.bilibili.com"))
        }
    }

    private fun authenticatePrivacyAccess(
        request: PrivacyAuthenticationRequest,
        onResult: (PrivacyAuthenticationResult) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val availability = BiometricManager.from(this).canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(PrivacyAuthenticationResult.Failure(resolvePrivacyAuthenticationUnavailableMessage(request)))
            return
        }

        var delivered = false
        fun deliver(result: PrivacyAuthenticationResult) {
            if (!delivered) {
                delivered = true
                onResult(result)
            }
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver(PrivacyAuthenticationResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val message = when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> "已取消解锁"
                        else -> errString.toString().ifBlank { "解锁失败，请稍后重试" }
                    }
                    deliver(PrivacyAuthenticationResult.Failure(message))
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(request.reason.title)
            .setSubtitle(request.reason.subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun resolvePrivacyAuthenticationUnavailableMessage(
        request: PrivacyAuthenticationRequest
    ): String {
        return when (request.reason) {
            PrivacyAuthenticationReason.OPEN_PRIVACY_CONTENT -> "请先设置系统锁屏后再解锁隐私内容"
        }
    }

    private fun refreshSystemThemeSnapshot(reason: String) {
        val currentSystemInDark = resolveMainActivitySystemInDarkTheme(
            resources.configuration.uiMode
        )
        if (shouldRefreshMainActivitySystemThemeSnapshot(systemInDarkThemeSnapshot, currentSystemInDark)) {
            Logger.d(
                TAG,
                "🌓 System theme refreshed on $reason: dark=$currentSystemInDark"
            )
            systemInDarkThemeSnapshot = currentSystemInDark
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (StartupRecovery.isRecoveryMode) {
            startupRecoveryRedirected = true
            // Do not restore Compose/navigation/player state on the emergency route.
            // Launcher aliases use a SplashScreen theme until installSplashScreen runs;
            // explicitly select the AppCompat host theme since we bypass that path here.
            setTheme(R.style.Theme_PureBiliBili_Main)
            super.onCreate(null)
            startActivity(Intent(this, StartupRecoveryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }
        Logger.recordStartupStage("main_create")
        applyAppLanguage(SettingsManager.getAppLanguageSync(this))
        //  安装 SplashScreen
        val splashScreen = installSplashScreen()
        val runColdStartSplash = shouldRunColdStartSplash(savedInstanceStatePresent = savedInstanceState != null)
        val welcomePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val splashIconVisible = SettingsManager.isSplashIconAnimationEnabledSync(this)
        val userAgreementAcked = welcomePrefs.getBoolean(USER_AGREEMENT_ACK_KEY, false)
        val splashFlyoutEnabled = runColdStartSplash && shouldEnableSplashFlyoutAnimation(
            sdkInt = Build.VERSION.SDK_INT,
            // Flyout only after mandatory user-agreement gate (covers both new and old users).
            hasCompletedOnboarding = userAgreementAcked,
            hasAcceptedReleaseDisclaimer = userAgreementAcked ||
                welcomePrefs.getBoolean(RELEASE_DISCLAIMER_ACK_KEY, false),
            splashIconAnimationEnabled = splashIconVisible
        )
        val keepSystemSplashForPreload = shouldKeepSystemSplashForPreload(
            runColdStartSplash = runColdStartSplash
        )
        val splashFlyoutIconResId = resolveLaunchIconResId(this, intent)
        splashFlyoutEnabledAtCreate = splashFlyoutEnabled
        Logger.d(
            TAG,
            "🚀 Splash setup. coldStart=$runColdStartSplash, iconVisible=$splashIconVisible, keepForPreload=$keepSystemSplashForPreload, flyoutEnabled=$splashFlyoutEnabled, userAgreementAck=$userAgreementAcked, firstLaunchShown=${welcomePrefs.getBoolean(KEY_FIRST_LAUNCH, false)}, disclaimerAck=${welcomePrefs.getBoolean(RELEASE_DISCLAIMER_ACK_KEY, false)}, taskRoot=$isTaskRoot, savedState=${savedInstanceState != null}, intentFlags=0x${intent?.flags?.toString(16) ?: "0"}, launchIconResId=$splashFlyoutIconResId"
        )
        
        //  🚀 [启动优化] 立即开始预加载首页数据
        // 这个必须尽早调用，利用开屏动画的时间并行加载数据
        VideoRepository.preloadHomeData()
        
        super.onCreate(savedInstanceState)
        //  初始调用，后续会根据主题动态更新
        enableEdgeToEdge()
        AppWindowSystemUiController.configureEdgeToEdgeHost(this)
        
        // 初始化小窗管理器
        miniPlayerManager = MiniPlayerManager.getInstance(this)
        PlaybackHandoffRegistry.setAvailabilityListener {
            window.decorView.post { refreshAndroid17HandoffAvailability() }
        }
        refreshSystemThemeSnapshot(reason = "create")
        
        //  🚀 [启动优化] 保持 Splash 直到数据加载完成或超时
        var isDataReady = false
        val startTime = System.currentTimeMillis()

        windowMetrics = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this)

        splashScreen.setKeepOnScreenCondition {
            if (!keepSystemSplashForPreload) {
                return@setKeepOnScreenCondition false
            }

            // 检查数据是否就绪
            if (VideoRepository.isHomeDataReady()) {
                isDataReady = true
            }
            
            // 计算耗时
            val elapsed = System.currentTimeMillis() - startTime
            
            // 条件：数据未就绪 且 未超时(1400ms)
            // 如果超时，强制进入（会显示骨架屏），避免用户以为死机
            val shouldKeep = !isDataReady && elapsed < splashMaxKeepOnScreenMs()
            
            if (!shouldKeep) {
                 Logger.d(TAG, "🚀 Splash dismissed. DataReady=$isDataReady, Elapsed=${elapsed}ms")
            }
            
            shouldKeep
        }

        if (splashFlyoutEnabled) {
            Logger.d(TAG, "🚀 Splash flyout exit listener registered")
            splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
                splashExitCallbackTriggered = true
                runCatching {
                    val splashView = splashScreenViewProvider.view
                    val systemIconView = splashScreenViewProvider.iconView
                    val frameContainer = splashView as? FrameLayout
                        ?: error("Splash root is not a FrameLayout")
                    val targetDrawableState = (systemIconView as? ImageView)
                        ?.drawable
                        ?.constantState
                    val targetSizePx = resolveSplashFlyoutTargetSizePx(
                        systemIconWidthPx = systemIconView.width,
                        systemIconHeightPx = systemIconView.height,
                        density = resources.displayMetrics.density,
                    )
                    Logger.d(
                        TAG,
                        "🚀 Splash exit target metrics. system=${systemIconView.width}x${systemIconView.height}, targetSizePx=$targetSizePx, useRealtimeBlur=${shouldUseRealtimeSplashBlur(Build.VERSION.SDK_INT, Build.MANUFACTURER.orEmpty())}"
                    )

                    var nextInsertIndex = frameContainer.indexOfChild(systemIconView)
                        .let { if (it >= 0) it + 1 else frameContainer.childCount }
                    fun createFlyoutIconView(initialAlpha: Float): ImageView? {
                        // Prefer the original high-density resource. The OEM iconView drawable can
                        // already be a stretched/rasterized snapshot on customized Android builds.
                        val drawable = if (splashFlyoutIconResId != 0) {
                            AppCompatResources.getDrawable(
                                this@MainActivity,
                                splashFlyoutIconResId,
                            )?.mutate()
                        } else {
                            targetDrawableState?.newDrawable(resources)?.mutate()
                        } ?: return null
                        return ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            alpha = initialAlpha
                            applySplashFlyoutRoundedClip(this)
                            setImageDrawable(drawable)
                            frameContainer.addView(
                                this,
                                nextInsertIndex++,
                                FrameLayout.LayoutParams(
                                    targetSizePx,
                                    targetSizePx,
                                    Gravity.CENTER
                                )
                            )
                        }
                    }
                    val secondaryTrailView = createFlyoutIconView(initialAlpha = 0f)
                    val primaryTrailView = createFlyoutIconView(initialAlpha = 0f)
                    val animatedTarget = createFlyoutIconView(initialAlpha = 1f)
                        ?: error("Unable to create a square splash flyout icon")
                    // Never clip or animate the OEM iconView directly: some devices expose a
                    // full-height rectangular container here, which produces the giant leaking
                    // rounded card seen on first cold start.
                    systemIconView.alpha = 0f
                    val targetType = resolveSplashFlyoutTargetType(
                        hasSystemIcon = false,
                        hasFallbackIcon = true,
                    )
                    Logger.d(
                        TAG,
                        "🚀 Splash exit animation start. targetType=$targetType, dedicatedSquareIcon=true"
                    )
                    val minTranslateYPx = splashExitTranslateYDp() * resources.displayMetrics.density
                    val translateYPx = splashExitTravelDistancePx(
                        splashHeightPx = splashView.height,
                        targetSizePx = targetSizePx,
                        minTravelPx = minTranslateYPx
                    )
                    val supportsRealtimeBlur = shouldUseRealtimeSplashBlur(Build.VERSION.SDK_INT, Build.MANUFACTURER.orEmpty())
                    var blurEffectEnabled = supportsRealtimeBlur
                    var lastAppliedBlurRadius = -1f
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = splashExitDurationMs()
                        interpolator = PathInterpolator(0.12f, 0.98f, 0.2f, 1.0f)
                        addUpdateListener { valueAnimator ->
                            val progress = valueAnimator.animatedValue as Float
                            val trailProgressPrimary = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
                            val trailProgressSecondary = ((progress - 0.16f) / 0.84f).coerceIn(0f, 1f)
                            animatedTarget.translationY = -translateYPx * progress
                            animatedTarget.alpha = splashExitIconAlpha(progress)
                            splashView.alpha = splashExitBackgroundAlpha(progress)

                            val scale = 1f + (splashExitScaleEnd() - 1f) * progress
                            animatedTarget.scaleX = scale
                            animatedTarget.scaleY = scale

                            primaryTrailView?.let { trail ->
                                trail.translationY = -translateYPx * trailProgressPrimary
                                trail.alpha = splashTrailPrimaryAlpha(progress)
                                trail.scaleX = scale * 1.03f
                                trail.scaleY = scale * 1.03f
                            }

                            secondaryTrailView?.let { trail ->
                                trail.translationY = -translateYPx * trailProgressSecondary
                                trail.alpha = splashTrailSecondaryAlpha(progress)
                                trail.scaleX = scale * 1.06f
                                trail.scaleY = scale * 1.06f
                            }

                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                shouldApplySplashRealtimeBlur(blurEffectEnabled, progress)
                            ) {
                                val radius = splashExitBlurRadiusEnd() * splashExitBlurProgress(progress)
                                if (radius >= 0.5f && kotlin.math.abs(radius - lastAppliedBlurRadius) >= 1.0f) {
                                    lastAppliedBlurRadius = radius
                                    runCatching {
                                        applySplashRealtimeBlur(
                                            animatedTarget = animatedTarget,
                                            primaryTrailView = primaryTrailView,
                                            secondaryTrailView = secondaryTrailView,
                                            radius = radius
                                        )
                                    }.onFailure {
                                        blurEffectEnabled = false
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            clearSplashRealtimeBlur(
                                                animatedTarget = animatedTarget,
                                                primaryTrailView = primaryTrailView,
                                                secondaryTrailView = secondaryTrailView
                                            )
                                        }
                                        Logger.w(TAG, "⚠️ Splash realtime blur failed, fallback to non-blur flyout", it)
                                    }
                                }
                            }
                        }
                    }
                    animator.doOnEnd {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsRealtimeBlur) {
                            clearSplashRealtimeBlur(
                                animatedTarget = animatedTarget,
                                primaryTrailView = primaryTrailView,
                                secondaryTrailView = secondaryTrailView
                            )
                        }
                        frameContainer.post {
                            frameContainer.removeView(animatedTarget)
                            primaryTrailView?.let(frameContainer::removeView)
                            secondaryTrailView?.let(frameContainer::removeView)
                            splashScreenViewProvider.remove()
                        }
                    }
                    animator.start()
                }.onFailure {
                    Logger.e(TAG, "❌ Splash exit animation failed, removing splash immediately", it)
                    splashScreenViewProvider.remove()
                }
            }
        }

        //  [新增] 处理 deep link 或分享意图
        handleIntent(intent)
        
        // --- 📺 DLNA Service Init ---
        // Android 12+ 需要运行时权限
        // requestDlnaPermissionsAndBind()
        
        if (shouldStartLocalProxyOnAppLaunch()) {
            // Optional warmup path; default keeps proxy off cold-start critical path.
            AppScope.ioScope.launch {
                try {
                    val started = LocalProxyServer.ensureStarted()
                    if (started) {
                        Logger.d(TAG, "📺 Local Proxy Server started on port 8901")
                    } else {
                        Logger.d(TAG, "📺 Local Proxy Server already running")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "❌ Failed to start Local Proxy Server", e)
                }
            }
        }

        val composeContentView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val rootContainer = FrameLayout(this).apply {
            addView(
                composeContentView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(rootContainer)

        composeContentView.setContent {
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val scope = rememberCoroutineScope()
            var startupUpdateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
            // Legacy state remains only for the retired in-place dialog path below.
            var startupUpdateDownloadState by remember { mutableStateOf(AppUpdateDownloadState()) }
            var pendingCrashSnapshotPath by remember {
                mutableStateOf(Logger.getPendingCrashSnapshotPath(context))
            }
            var hasHandledCrashPrompt by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val autoCheckUpdateEnabled = SettingsManager.getAutoCheckAppUpdate(context).first()
                val updateChannel = SettingsManager.getAppUpdateChannel(context).first()
                val gateAllowsCheck = AppUpdateAutoCheckGate.tryMarkChecked()
                if (shouldRunAppEntryAutoCheck(autoCheckUpdateEnabled, gateAllowsCheck)) {
                    AppUpdateChecker.check(
                        currentVersion = BuildConfig.VERSION_NAME,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        includePrerelease = updateChannel == SettingsManager.AppUpdateChannel.BETA
                    ).onSuccess { info ->
                        if (info.isUpdateAvailable) {
                            startupUpdateCheckResult = info
                        }
                    }
                }
            }
            
            //  首次启动检测已移交 AppNavigation 处理
            // val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
            // var showWelcome by remember { mutableStateOf(!prefs.getBoolean(KEY_FIRST_LAUNCH, false)) }

            val appThemeSettings by SettingsManager
                .getAppThemeSettings(context)
                .collectAsStateWithLifecycle(
                    initialValue = SettingsManager.getInitialAppThemeSettings(context)
                )
            val themeMode = appThemeSettings.themeMode
            val darkThemeStyle = appThemeSettings.darkThemeStyle
            val appLanguage = appThemeSettings.appLanguage

            //  检查并请求所有文件访问权限 (Android 11+)
            //  检查并请求所有文件访问权限 (已移除启动时强制检查，改为按需申请)
            // LaunchedEffect(Unit) { ... }

            val md3ColorSource = appThemeSettings.md3ColorSource
            val md3CustomColorHex = appThemeSettings.md3CustomColorHex
            val themeRoleOverrides = appThemeSettings.themeRoleOverrides
            val colorStyle = appThemeSettings.colorStyle
            val colorSpec = appThemeSettings.colorSpec
            val themeColorIndex = appThemeSettings.themeColorIndex
            val appFontSizePreset = appThemeSettings.appFontSizePreset
            val appFontFileName = appThemeSettings.appFontFileName
            val appUiScalePreset = appThemeSettings.appUiScalePreset
            val appDpiOverridePercent = appThemeSettings.appDpiOverridePercent
            val appGestureScreenshotEnabled = appThemeSettings.appGestureScreenshotEnabled
            val appScreenshotGestureMode = appThemeSettings.appScreenshotGestureMode
            val appScreenshotCaptureMode = appThemeSettings.appScreenshotCaptureMode
            val blurIntensity by SettingsManager.getBlurIntensity(context)
                .collectAsStateWithLifecycle(initialValue = BlurIntensity.THIN)
            val headerBlurEnabled by SettingsManager.getHeaderBlurEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val bottomBarBlurEnabled by SettingsManager.getBottomBarBlurEnabled(context)
                .collectAsStateWithLifecycle(initialValue = false)
            val progressiveTopBlurEnabled by SettingsManager.getProgressiveTopBlurEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val hapticFeedbackEnabled by SettingsManager.getHapticFeedbackEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val globalTextTapCopyEnabled by SettingsManager
                .getGlobalTextTapCopyEnabled(context)
                .collectAsStateWithLifecycle(initialValue = false)
            val uiEntranceAnimationEnabled by SettingsManager
                .getUiEntranceAnimationEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val runtimeVisualGuardEnabled by SettingsManager
                .getRuntimeVisualGuardEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val nativeMiuixPopupsEnabled by com.android.purebilibili.core.store.player.PlayerSettingsStore
                .getNativeMiuixPlayerPopups(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val liquidGlassEnabled by SettingsManager.getAndroidNativeLiquidGlassEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true)
            val appThemeConfig = remember(
                liquidGlassEnabled,
                blurIntensity,
                headerBlurEnabled,
                bottomBarBlurEnabled,
                progressiveTopBlurEnabled,
                hapticFeedbackEnabled,
                globalTextTapCopyEnabled,
                uiEntranceAnimationEnabled,
                runtimeVisualGuardEnabled,
                nativeMiuixPopupsEnabled,
            ) {
                AppThemeConfig(
                    liquidGlassEnabled = liquidGlassEnabled,
                    blurIntensity = blurIntensity,
                    headerBlurEnabled = headerBlurEnabled,
                    bottomBarBlurEnabled = bottomBarBlurEnabled,
                    progressiveTopBlurEnabled = progressiveTopBlurEnabled,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    globalTextTapCopyEnabled = globalTextTapCopyEnabled,
                    uiEntranceAnimationEnabled = uiEntranceAnimationEnabled,
                    runtimeVisualGuardEnabled = runtimeVisualGuardEnabled,
                    nativeMiuixPopupsEnabled = nativeMiuixPopupsEnabled,
                )
            }
            
            // 4. 获取系统当前的深色状态
            val systemInDark = systemInDarkThemeSnapshot

            // 5. 根据枚举值决定是否开启 DarkTheme
            val themePreferenceState = resolveThemePreferenceState(
                themeMode = themeMode,
                darkThemeStyle = darkThemeStyle,
                systemInDark = systemInDark
            )
            val useDarkTheme = themePreferenceState.useDarkTheme
            val useAmoledDarkTheme = themePreferenceState.useAmoledDarkTheme
            val effectiveDynamicColor = resolveEffectiveDynamicColorEnabled(
                dynamicColorEnabled = resolveMd3DynamicColorEnabled(
                    source = md3ColorSource,
                    sdkInt = Build.VERSION.SDK_INT
                ),
                amoledDarkTheme = useAmoledDarkTheme,
                uiStyle = appThemeSettings.uiStyle
            )

            //  [新增] 根据主题动态更新状态栏样式
            LaunchedEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            //  全局 Haze 状态，用于实现毛玻璃效果
            // 强制启用 blur，避免部分设备（如 Android 12）默认降级为仅半透明遮罩
            val mainHazeState = rememberRecoverableHazeState(initialBlurEnabled = true)
            val configuration = LocalConfiguration.current
            val systemDensity = LocalDensity.current
            val displayMetricsSnapshot = remember(
                configuration.densityDpi,
                configuration.smallestScreenWidthDp,
                appFontSizePreset,
                appUiScalePreset,
                appDpiOverridePercent
            ) {
                buildDisplayMetricsSnapshot(
                    systemDensityDpi = configuration.densityDpi,
                    smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                    uiScalePreset = appUiScalePreset,
                    fontSizePreset = appFontSizePreset,
                    dpiOverridePercent = appDpiOverridePercent.takeIf { it > 0 }
                )
            }
            val effectiveDensity = remember(systemDensity, displayMetricsSnapshot.effectiveDensityMultiplier) {
                Density(
                    density = systemDensity.density * displayMetricsSnapshot.effectiveDensityMultiplier,
                    fontScale = systemDensity.fontScale
                )
            }

            //  📐 [平板适配] 计算窗口尺寸类
            val materialWindowAdaptiveInfo =
                androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2()
            val windowSizeClass = calculateWindowSizeClass(
                densityMultiplier = displayMetricsSnapshot.effectiveDensityMultiplier,
                metrics = windowMetrics!!,
                adaptiveWindowSizeClass = materialWindowAdaptiveInfo.windowSizeClass,
            )
            val appWindowAdaptiveInfo = rememberAppWindowAdaptiveInfo(
                windowSizeClass = windowSizeClass,
                windowPosture = materialWindowAdaptiveInfo.windowPosture,
            )

            // 6. 传入参数
            PureBiliBiliTheme(
                liquidGlassEnabled = liquidGlassEnabled,
                uiStyle = appThemeSettings.uiStyle,
                themeMode = themeMode,
                darkTheme = useDarkTheme,
                dynamicColor = effectiveDynamicColor,
                amoledDarkTheme = useAmoledDarkTheme,
                themeColorIndex = themeColorIndex, //  传入主题色索引
                md3ColorSource = md3ColorSource,
                md3CustomColorHex = md3CustomColorHex,
                themeRoleOverrides = themeRoleOverrides,
                colorStyle = colorStyle,
                colorSpec = colorSpec,
                fontSizePreset = appFontSizePreset,
                appFontFileName = appFontFileName,
                appIconStyle = appThemeSettings.appIconStyle,
                appListItemStyle = appThemeSettings.appListItemStyle,
            ) {
                ProvideAppThemeConfig(config = appThemeConfig) {
                ProvideRuntimeVisualGuard(
                    widthSizeClass = windowSizeClass.widthSizeClass
                ) {
                ProvideUnifiedBlurIntensity {
                    //  📐 [平板适配] 提供全局 WindowSizeClass
                    CompositionLocalProvider(
                        LocalDensity provides effectiveDensity,
                        LocalWindowSizeClass provides windowSizeClass,
                        LocalAppWindowAdaptiveInfo provides appWindowAdaptiveInfo,
                        LocalDisplayMetricsSnapshot provides displayMetricsSnapshot,
                        LocalAppSingleChoicePresentation provides
                            appThemeSettings.singleChoicePresentation,
                    ) {
                    val isPipRenderingActive =
                        isInPipMode || miniPlayerManager.shouldKeepPlaybackForPipTransition()
                    val isFullscreenPlayerLocked = AppScreenshotGestureBlockState.fullscreenPlayerLocked
                    var isAppScreenshotBlockedBySplash by remember { mutableStateOf(false) }
                    var isAppScreenshotSaving by remember { mutableStateOf(false) }
                    var appScreenshotRegionBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    val appScreenshotSnackbarHostState = remember { SnackbarHostState() }
                    val isLandscapeAppScreenshot =
                        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val showAppScreenshotSaveFeedback: suspend (AppScreenshotResult, Uri?) -> Unit = { result, uri ->
                        val message = when (result) {
                            AppScreenshotResult.Success -> "截图已保存到相册（PNG）"
                            AppScreenshotResult.Blocked -> "当前状态暂不支持截图"
                            AppScreenshotResult.CaptureFailed,
                            AppScreenshotResult.SaveFailed -> "截图失败，请稍后重试"
                        }
                        if (shouldOfferAppScreenshotShare(isLandscapeAppScreenshot, result, uri != null)) {
                            val snackbarResult = appScreenshotSnackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = "分享",
                                duration = SnackbarDuration.Short
                            )
                            if (snackbarResult == SnackbarResult.ActionPerformed && uri != null) {
                                shareAppScreenshot(context, uri)
                            }
                        } else {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .appScreenshotGestureDetector(
                                enabled = appGestureScreenshotEnabled,
                                mode = appScreenshotGestureMode,
                                blocked = isPipRenderingActive ||
                                    isFullscreenPlayerLocked ||
                                    isAppScreenshotBlockedBySplash ||
                                    isAppScreenshotSaving ||
                                    appScreenshotRegionBitmap != null,
                                onCaptureRequested = {
                                    if (!isAppScreenshotSaving) {
                                        scope.launch {
                                            isAppScreenshotSaving = true
                                            try {
                                                if (appScreenshotCaptureMode == AppScreenshotCaptureMode.SELECT_REGION) {
                                                    val bitmap = runCatching {
                                                        captureCurrentAppWindow(this@MainActivity)
                                                    }.getOrElse {
                                                        Logger.e(TAG, "应用内截图预览捕获失败", it)
                                                        null
                                                    }
                                                    if (bitmap == null) {
                                                        Toast.makeText(context, "截图失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        appScreenshotRegionBitmap?.recycle()
                                                        appScreenshotRegionBitmap = bitmap
                                                    }
                                                } else {
                                                    val savedImage = runCatching {
                                                        captureAndSaveAppScreenshotImage(this@MainActivity)
                                                    }.getOrElse {
                                                        Logger.e(TAG, "应用内截图失败", it)
                                                        AppScreenshotSavedImage(AppScreenshotResult.CaptureFailed)
                                                    }
                                                    showAppScreenshotSaveFeedback(savedImage.result, savedImage.uri)
                                                }
                                            } finally {
                                                isAppScreenshotSaving = false
                                            }
                                        }
                                    }
                                }
                            )
                            .background(MaterialTheme.colorScheme.background)  // 📐 [修复] 防止平板端返回后出现黑边
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        //  [修复] 移除 .haze() 以避免与 hazeSource/hazeEffect 冲突
                        // 每个 Screen 自己管理 hazeSource（内容）和 hazeEffect（头部/底栏）
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LaunchedEffect(isInPipMode, miniPlayerManager.isPlaying) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPipMode) {
                                    val pipParams = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .setActions(
                                            buildPipPlaybackRemoteActions(
                                                context = this@MainActivity,
                                                player = miniPlayerManager.player
                                            )
                                        )
                                        .build()
                                    setPictureInPictureParams(pipParams)
                                }
                            }
                            LaunchedEffect(
                                isInVideoDetail,
                                isInAudioModeRoute,
                                miniPlayerManager.currentBvid,
                                miniPlayerManager.currentCid,
                                miniPlayerManager.isLiveMode
                            ) {
                                refreshAndroid17HandoffAvailability()
                            }
                            AppNavigation(
                                miniPlayerManager = miniPlayerManager,
                                isInPipMode = isPipRenderingActive,
                                pendingVideoId = pendingVideoId,
                                pendingShortcutRoute = pendingRoute,
                                pendingNavigationRoute = pendingNavigationRoute,
                                onPendingVideoIdConsumed = { consumedVideoId ->
                                    if (pendingVideoId == consumedVideoId) {
                                        pendingVideoId = null
                                    }
                                },
                                onPendingShortcutRouteConsumed = { consumedRoute ->
                                    if (pendingRoute == consumedRoute) {
                                        pendingRoute = null
                                    }
                                },
                                onPendingNavigationRouteConsumed = { consumedRoute ->
                                    if (pendingNavigationRoute == consumedRoute) {
                                        pendingNavigationRoute = null
                                    }
                                },
                                initialSearchKeyword = pendingSearchKeyword,
                                onInitialSearchKeywordConsumed = { consumedKeyword ->
                                    if (pendingSearchKeyword == consumedKeyword) {
                                        pendingSearchKeyword = null
                                    }
                                },
                                onVideoDetailEnter = {
                                    isInVideoDetail = true
                                    refreshAndroid17HandoffAvailability()
                                    Logger.d(TAG, " 进入视频详情页")
                                },
                                onVideoDetailExit = {
                                    isInVideoDetail = false
                                    refreshAndroid17HandoffAvailability()
                                    Logger.d(TAG, "🔙 退出视频详情页")
                                },
                                onAudioModeEnter = {
                                    isInAudioModeRoute = true
                                    refreshAndroid17HandoffAvailability()
                                    Logger.d(TAG, "🎧 进入听视频页")
                                },
                                onAudioModeExit = {
                                    isInAudioModeRoute = false
                                    refreshAndroid17HandoffAvailability()
                                    Logger.d(TAG, "🎧 退出听视频页")
                                },
                                onPrivacyAuthenticationRequired = ::authenticatePrivacyAccess,
                                mainHazeState = mainHazeState //  传递全局 Haze 状态
                            )
                            
                            //  OnboardingBottomSheet 等其他 overlay 组件

                        }
                    }
                    //  小窗全屏状态
                    var showFullscreen by remember { mutableStateOf(false) }
                    val audioNowPlayingActive by AudioNowPlayingSession.active.collectAsStateWithLifecycle()
                    val audioNowPlayingBarEnabled by SettingsManager
                        .getAudioNowPlayingBarEnabled(context)
                        .collectAsStateWithLifecycle(initialValue = true)
                    val audioPlaylist by PlaylistManager.playlist.collectAsStateWithLifecycle()
                    val audioPlaylistIndex by PlaylistManager.currentIndex.collectAsStateWithLifecycle()
                    val audioNowPlayingItem = audioPlaylist.getOrNull(audioPlaylistIndex)
                    val showAudioNowPlaying = resolveAudioNowPlayingVisible(
                        sessionActive = audioNowPlayingActive,
                        isOnAudioModeScreen = isInAudioModeRoute,
                        isInPipMode = isInPipMode,
                        hasCurrentItem = audioNowPlayingItem != null,
                        barEnabled = audioNowPlayingBarEnabled,
                        isVideoDetailDestination = isInVideoDetail
                    )
                    val playbackOverlayState = remember(
                        isInPipMode,
                        miniPlayerManager.isMiniMode,
                        showAudioNowPlaying
                    ) {
                        resolveMainActivityPlaybackOverlayState(
                            isInPipMode = isInPipMode,
                            isMiniMode = miniPlayerManager.isMiniMode,
                            showAudioNowPlaying = showAudioNowPlaying
                        )
                    }
                    if (playbackOverlayState.showDedicatedPipPlayer) {
                        miniPlayerManager.player?.let { pipPlayer ->
                            AndroidView(
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        player = pipPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    }
                                },
                                update = { playerView -> playerView.player = pipPlayer },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            )
                        }
                    }
                    //  小窗播放器覆盖层 (非 PiP 模式下显示)
                    if (playbackOverlayState.showMiniPlayerOverlay) {
                        MiniPlayerOverlay(
                            miniPlayerManager = miniPlayerManager,
                            onPictureInPictureClick = if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                miniPlayerManager.shouldEnterPip()
                            ) {
                                { enterMiniPlayerPictureInPicture() }
                            } else {
                                null
                            },
                            onExpandClick = {
                                if (miniPlayerManager.isLiveMode) {
                                    // 📺 直播小窗展开：导航回直播间
                                    val roomId = miniPlayerManager.currentRoomId
                                    val liveTitle = miniPlayerManager.currentTitle
                                    val liveUname = miniPlayerManager.currentLiveUname
                                    miniPlayerManager.exitMiniMode(animate = false)
                                    pendingNavigationRoute =
                                        ScreenRoutes.Live.createRoute(roomId, liveTitle, liveUname)
                                } else {
                                    //  [修改] 导航回详情页，而不是只显示全屏播放器
                                    miniPlayerManager.currentBvid?.let { bvid ->
                                        miniPlayerManager.isNavigatingToVideo = true
                                        miniPlayerManager.exitMiniMode(animate = false)
                                        val cid = miniPlayerManager.currentCid
                                        pendingNavigationRoute = resolveMiniPlayerExpandVideoRoute(bvid = bvid, cid = cid)
                                    }
                                }
                            }
                        )
                    }
                    
                    //  全屏播放器覆盖层（包含亮度、音量、进度调节）
                    if (showFullscreen) {
                        FullscreenPlayerOverlay(
                            miniPlayerManager = miniPlayerManager,
                            onDismiss = { 
                                showFullscreen = false
                                miniPlayerManager.enterMiniMode()
                            },
                            onNavigateToDetail = {
                                //  关闭全屏覆盖层并导航到视频详情页
                                showFullscreen = false
                                miniPlayerManager.currentBvid?.let { bvid ->
                                    miniPlayerManager.isNavigatingToVideo = true
                                    miniPlayerManager.exitMiniMode(animate = false)
                                    //  [修复] 使用正确的 cid，而不是 0
                                    val cid = miniPlayerManager.currentCid
                                    pendingNavigationRoute = resolveMainActivityVideoRoute(bvid = bvid, cid = cid)
                                }
                            }
                        )
                    }
                    
                    //  护眼模式覆盖层（最顶层，应用于所有内容）
                    EyeProtectionOverlay(
                        playbackActive = isInVideoDetail ||
                            showFullscreen ||
                            miniPlayerManager.isPlaying ||
                            isPipRenderingActive
                    )
                    PluginEffectHintHost()
                    
                    // [New] Custom Splash Wallpaper Overlay
                    val readCustomSplashPrefs = remember { shouldReadCustomSplashPreferences() }
                    val splashUri = remember(readCustomSplashPrefs) {
                        val fixedUri = SettingsManager.getSplashWallpaperUriSync(context)
                        val randomEnabled = SettingsManager.isSplashRandomEnabledSync(context)
                        val splashRandomPool = SettingsManager.getSplashRandomPoolUrisSync(context)
                        resolveSplashWallpaperUriForLaunch(
                            randomEnabled = randomEnabled,
                            fixedSplashUri = fixedUri,
                            poolUris = splashRandomPool,
                            launchSeed = System.currentTimeMillis()
                        )
                    }
                    val splashAlignmentBias = remember(readCustomSplashPrefs, windowSizeClass.widthSizeClass) {
                        val mobileBias = SettingsManager.getSplashAlignmentSync(context, isTablet = false)
                        val tabletBias = SettingsManager.getSplashAlignmentSync(context, isTablet = true)
                        resolveSplashWallpaperAlignmentBias(
                            isTabletLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                            mobileBias = mobileBias,
                            tabletBias = tabletBias
                        )
                    }
                    val showCustomSplashInitially = remember(runColdStartSplash, splashUri) {
                        runColdStartSplash && shouldShowCustomSplashOverlay(
                            customSplashEnabled = SettingsManager.isSplashEnabledSync(context),
                            splashUri = splashUri
                        )
                    }
                    var showSplash by remember { mutableStateOf(showCustomSplashInitially) }
                    LaunchedEffect(showSplash) {
                        isAppScreenshotBlockedBySplash = showSplash
                    }
                    // [Optimization] If we delayed enough in splash screen, we might want to skip custom splash or show it briefly?
                    // Logic: If user uses custom splash, system splash shows icon, then custom splash shows wallpaper.
                    // If we use setKeepOnScreenCondition, system splash (icon) stays longer.
                    // This is acceptable behavior: Icon -> Wallpaper (if enabled) -> App.
                    // Or if custom wallpaper is enabled, maybe we shouldn't delay system splash?
                    // User request: "当用户看见遮罩的时候，异步加载首页视频". Mask usually means System Splash (Icon) OR Custom Wallpaper.
                    // Implementing delay on System Splash ensures data is likely ready when ANY content shows.

                    LaunchedEffect(showCustomSplashInitially) {
                        if (showCustomSplashInitially) {
                            showSplash = true
                            delay(customSplashHoldDurationMs())
                            showSplash = false
                        } else {
                            showSplash = false
                        }
                    }
                    val splashOverlayAlpha by animateFloatAsState(
                        targetValue = if (showSplash) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = customSplashFadeDurationMs(),
                            easing = AppMotionEasing.EmphasizedEnter
                        ),
                        label = "customSplashOverlayAlpha"
                    )
                    val splashFadeProgress = customSplashFadeProgress(splashOverlayAlpha)
                    val splashOverlayScale = customSplashOverlayScale(splashFadeProgress)
                    val splashExtraBlur = customSplashExtraBlurDp(splashFadeProgress)
                    val splashTailScrimAlpha = customSplashOverlayScrimAlpha(splashFadeProgress)

                    if (customSplashShouldRender(showSplash, splashOverlayAlpha) && splashUri.isNotEmpty()) {
                        val splashWallpaperLayout = remember(windowSizeClass.widthSizeClass) {
                            resolveSplashWallpaperLayout(
                                widthSizeClass = windowSizeClass.widthSizeClass
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = splashOverlayAlpha)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (splashWallpaperLayout) {
                                SplashWallpaperLayout.FULL_CROP -> {
                                    AsyncImage(
                                        model = splashUri,
                                        contentDescription = "Splash Wallpaper",
                                        alignment = BiasAlignment(0f, splashAlignmentBias),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = splashOverlayScale,
                                                scaleY = splashOverlayScale
                                            )
                                            .blur(splashExtraBlur.dp)
                                    )
                                }

                                SplashWallpaperLayout.POSTER_CARD_BLUR_BG -> {
                                    AsyncImage(
                                        model = splashUri,
                                        contentDescription = null,
                                        alignment = BiasAlignment(0f, splashAlignmentBias),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = splashOverlayScale,
                                                scaleY = splashOverlayScale
                                            )
                                            .blur((56f + splashExtraBlur).dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(
                                                    alpha = (0.16f + splashTailScrimAlpha * 0.5f).coerceAtMost(0.26f)
                                                )
                                            )
                                    )
                                    val posterCardCornerRadius =
                                        AppShapes.containerCornerDp(ContainerLevel.Floating)
                                    AppCard(
                                        shape = AppCardShape.Semantic(ContainerLevel.Floating),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .graphicsLayer(
                                                scaleX = 1f + (splashFadeProgress * 0.015f),
                                                scaleY = 1f + (splashFadeProgress * 0.015f)
                                            )
                                            .fillMaxWidth(
                                                if (windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Expanded) {
                                                    0.34f
                                                } else {
                                                    0.48f
                                                }
                                            )
                                            .widthIn(min = 190.dp, max = 340.dp)
                                            .aspectRatio(9f / 16f)
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = RoundedCornerShape(posterCardCornerRadius),
                                            )
                                    ) {
                                        AsyncImage(
                                            model = splashUri,
                                            contentDescription = "Splash Wallpaper Poster",
                                            alignment = BiasAlignment(0f, splashAlignmentBias),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = splashOverlayScale,
                                                    scaleY = splashOverlayScale
                                                )
                                                .blur((splashExtraBlur * 0.35f).dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = splashTailScrimAlpha))
                            )
                        }
                    }

                    appScreenshotRegionBitmap?.let { bitmap ->
                        AppScreenshotRegionOverlay(
                            bitmap = bitmap,
                            saving = isAppScreenshotSaving,
                            onCancel = {
                                bitmap.recycle()
                                appScreenshotRegionBitmap = null
                            },
                            onSaveRegion = { cropRect ->
                                if (!isAppScreenshotSaving) {
                                    scope.launch {
                                        isAppScreenshotSaving = true
                                        try {
                                            val croppedBitmap = cropAppScreenshotBitmap(bitmap, cropRect)
                                            val savedUri = croppedBitmap?.let { cropped ->
                                                try {
                                                    saveAppScreenshotBitmapToGalleryUri(context, cropped)
                                                } finally {
                                                    cropped.recycle()
                                                }
                                            }
                                            val result = if (savedUri != null) {
                                                AppScreenshotResult.Success
                                            } else {
                                                AppScreenshotResult.SaveFailed
                                            }
                                            if (savedUri != null) {
                                                bitmap.recycle()
                                                appScreenshotRegionBitmap = null
                                            }
                                            showAppScreenshotSaveFeedback(result, savedUri)
                                        } finally {
                                            isAppScreenshotSaving = false
                                        }
                                    }
                                }
                            }
                        )
                    }

                    SnackbarHost(
                        hostState = appScreenshotSnackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(WindowInsets.safeDrawing.asPaddingValues())
                            .padding(16.dp)
                    )

                    startupUpdateCheckResult?.let { info ->
                        AppUpdateDialogHost(
                            update = info,
                            onDismissRequest = { startupUpdateCheckResult = null },
                        )
                    }

                    if (false) {
                    startupUpdateCheckResult?.let { info ->
                        val resolvedReleaseNotes = remember(info.releaseNotes) {
                            resolveUpdateReleaseNotesText(info.releaseNotes)
                        }
                        val preferredAsset = remember(info.assets) {
                            selectPreferredAppUpdateAsset(info.assets)
                        }
                        val releaseCommit = remember(info.buildMetadata?.gitCommitSha) {
                            resolveBuildSourceValue(info.buildMetadata?.gitCommitSha, fallback = "未知")
                        }
                        val releaseWorkflowSubtitle = remember(info.buildMetadata?.workflowRunId, info.buildMetadata?.releaseTag) {
                            resolveBuildSourceSubtitle(
                                workflowRunId = info.buildMetadata?.workflowRunId,
                                releaseTag = info.buildMetadata?.releaseTag
                            )
                        }
                        val releaseVerificationEvidence = remember(info.verificationMetadata?.attestationUrl) {
                            if (info.verificationMetadata?.attestationUrl?.isNotBlank() == true) {
                                "GitHub Attestation"
                            } else {
                                "未提供"
                            }
                        }
                        val isDialogDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val dialogTextColors = remember(isDialogDarkTheme) {
                            resolveAppUpdateDialogTextColors(
                                isDarkTheme = isDialogDarkTheme
                            )
                        }
                        val releaseNotesScrollState = rememberScrollState()
                        AppAlertDialog(
                            onDismissRequest = { startupUpdateCheckResult = null },
                            title = {
                                Text(
                                    text = "发现新版本 v${info.latestVersion}",
                                    color = dialogTextColors.titleColor
                                )
                            },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "当前版本 v${info.currentVersion}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    preferredAsset?.let { asset ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "安装包：${asset.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = dialogTextColors.currentVersionColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Release 锁定：${if (info.releaseIsImmutable) "Immutable" else "可变"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    Text(
                                        text = "源码提交：$releaseCommit",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    Text(
                                        text = "构建来源：$releaseWorkflowSubtitle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    Text(
                                        text = "Provenance：$releaseVerificationEvidence",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    if (startupUpdateDownloadState.status != AppUpdateDownloadStatus.IDLE) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = when (startupUpdateDownloadState.status) {
                                                AppUpdateDownloadStatus.QUEUED -> "等待网络后开始下载"
                                                AppUpdateDownloadStatus.DOWNLOADING ->
                                                    "下载中 ${(startupUpdateDownloadState.progress * 100).toInt()}%"
                                                AppUpdateDownloadStatus.COMPLETED -> "下载完成，正在准备安装"
                                                AppUpdateDownloadStatus.FAILED ->
                                                    startupUpdateDownloadState.errorMessage ?: "下载失败"
                                                AppUpdateDownloadStatus.IDLE -> ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = dialogTextColors.currentVersionColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = resolvedReleaseNotes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dialogTextColors.releaseNotesColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(releaseNotesScrollState)
                                    )
                                }
                            },
                            confirmButton = {
                                AppDialogAction(onClick = {
                                    val downloadedFile = startupUpdateDownloadState.filePath
                                        ?.takeIf { startupUpdateDownloadState.status == AppUpdateDownloadStatus.COMPLETED }
                                        ?.let { path -> File(path) }
                                        ?.takeIf { it.exists() }

                                    if (downloadedFile != null) {
                                        installDownloadedAppUpdate(context, downloadedFile)
                                        return@AppDialogAction
                                    }

                                    val asset = preferredAsset
                                    if (asset == null) {
                                        startupUpdateCheckResult = null
                                        uriHandler.openUri(info.releaseUrl)
                                        return@AppDialogAction
                                    }

                                    if (startupUpdateDownloadState.status == AppUpdateDownloadStatus.DOWNLOADING) {
                                        return@AppDialogAction
                                    }

                                    scope.launch {
                                        downloadAppUpdateApk(
                                            context = context,
                                            asset = asset,
                                            onStateChange = { state -> startupUpdateDownloadState = state }
                                        ).onSuccess { file ->
                                            startupUpdateDownloadState = completeAppUpdateDownload(
                                                current = startupUpdateDownloadState,
                                                filePath = file.absolutePath
                                            )
                                            val installAction = installDownloadedAppUpdate(context, file)
                                            if (installAction == AppUpdateInstallAction.OPEN_UNKNOWN_SOURCES_SETTINGS) {
                                                Toast.makeText(context, "请先允许安装未知来源应用", Toast.LENGTH_SHORT).show()
                                            }
                                        }.onFailure { error ->
                                            startupUpdateDownloadState = failAppUpdateDownload(
                                                current = startupUpdateDownloadState,
                                                errorMessage = error.message ?: "更新下载失败"
                                            )
                                            Toast.makeText(context, error.message ?: "更新下载失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text(
                                        when {
                                            preferredAsset == null -> "前往下载"
                                            startupUpdateDownloadState.status == AppUpdateDownloadStatus.DOWNLOADING ->
                                                "下载中 ${(startupUpdateDownloadState.progress * 100).toInt()}%"
                                            startupUpdateDownloadState.status == AppUpdateDownloadStatus.COMPLETED -> "安装更新"
                                            else -> "立即更新"
                                        }
                                    )
                                }
                            },
                            dismissButton = {
                                AppDialogAction(onClick = {
                                    startupUpdateCheckResult = null
                                    startupUpdateDownloadState = AppUpdateDownloadState()
                                }) { Text("稍后") }
                            }
                        )
                    }
                    }

                    if (
                        shouldShowPendingCrashLogPrompt(
                            hasPendingCrashSnapshot = pendingCrashSnapshotPath != null,
                            hasPromptBeenHandled = hasHandledCrashPrompt
                        )
                    ) {
                        AppAlertDialog(
                            onDismissRequest = {
                                hasHandledCrashPrompt = true
                                if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.DISMISS)) {
                                    Logger.clearPendingCrashSnapshot(context)
                                    pendingCrashSnapshotPath = null
                                }
                            },
                            title = {
                                Text(text = "检测到上次闪退日志")
                            },
                            text = {
                                Text(
                                    text = "应用已在私有目录保存一份脱敏后的崩溃快照，不会自动上传或写入公共下载目录。现在可以主动分享给开发者排查，也可以关闭提示。"
                                )
                            },
                            confirmButton = {
                                AppDialogAction(onClick = {
                                    hasHandledCrashPrompt = true
                                    Logger.sharePendingCrashSnapshot(context)
                                    if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.SHARE)) {
                                        Logger.clearPendingCrashSnapshot(context)
                                        pendingCrashSnapshotPath = null
                                    }
                                }) { Text("分享") }
                            },
                            dismissButton = {
                                AppDialogAction(onClick = {
                                    hasHandledCrashPrompt = true
                                    if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.DISMISS)) {
                                        Logger.clearPendingCrashSnapshot(context)
                                        pendingCrashSnapshotPath = null
                                    }
                                }) { Text("关闭") }
                            }
                        )
                    }

                    }
                }
                }
                }
            }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (
            event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
        ) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (startupRecoveryRedirected) return
        windowMetrics = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this)
        refreshSystemThemeSnapshot(reason = "configuration")
    }

    override fun onStart() {
        super.onStart()
        if (startupRecoveryRedirected) return
        AppRuntimeVisualGuardTracker.activateSession(runtimeVisualGuardSession)
        val existingJankStats = runtimeJankStats
        if (existingJankStats != null) {
            existingJankStats.isTrackingEnabled = true
        } else {
            runtimeJankStats = runCatching {
                JankStats.createAndTrack(window) { frameData ->
                    AppRuntimeVisualGuardTracker.onFrame(
                        session = runtimeVisualGuardSession,
                        frameData = frameData,
                        nowMs = SystemClock.uptimeMillis(),
                    )
                }
            }.onFailure { throwable ->
                Logger.w(TAG, "无法启动页面转场性能采样", throwable)
            }.getOrNull()
        }
        if (shouldLogWarmResume(hasCompletedInitialResume, isChangingConfigurations)) {
            Logger.d(
                TAG,
                "🔁 Warm resume path. splash flyout is not expected (Activity already created). flyoutEnabledAtCreate=$splashFlyoutEnabledAtCreate, splashExitCallbackTriggered=$splashExitCallbackTriggered"
            )
        }
    }

    override fun onStop() {
        if (startupRecoveryRedirected) {
            super.onStop()
            return
        }
        StartupRecovery.onMainStopped(this)
        AppRuntimeVisualGuardTracker.discardActiveWindow(runtimeVisualGuardSession)
        runtimeJankStats?.isTrackingEnabled = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (startupRecoveryRedirected) return
        refreshAndroid17HandoffAvailability()
        refreshSystemThemeSnapshot(reason = "resume")
        miniPlayerManager.clearUserLeaveHint()
        miniPlayerManager.clearPlaybackRoutePipState()
        miniPlayerManager.clearPlaybackNotificationIfIdleOnResume()
        if (
            shouldRestorePlaybackRouteStateOnResume(
                isPlaybackRouteActive = isPlaybackRouteActive(
                    isInVideoDetail = isInVideoDetail,
                    isInAudioMode = isInAudioModeRoute
                )
            )
        ) {
            miniPlayerManager.resetNavigationFlag()
            miniPlayerManager.player?.let { player ->
                if (shouldRestoreMutedPlaybackPlayerVolumeOnResume(player.volume)) {
                    com.android.purebilibili.core.player.PlayerVolumeController
                        .applyPreferredVolume(player)
                }
            }
        }
        if (!hasCompletedInitialResume) {
            hasCompletedInitialResume = true
        }
        StartupRecovery.onMainResumed(this)
    }

    override fun onPause() {
        if (!startupRecoveryRedirected) {
            StartupRecovery.onMainPaused(this)
        }
        super.onPause()
    }
    
    //  用户按 Home 键或切换应用时触发
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (startupRecoveryRedirected) return
        
        Logger.d(
            TAG,
            "👋 onUserLeaveHint 触发, isInVideoDetail=$isInVideoDetail, isInAudioModeRoute=$isInAudioModeRoute, isMiniMode=${miniPlayerManager.isMiniMode}"
        )
        miniPlayerManager.markUserLeaveHint()
        miniPlayerManager.refreshMediaSessionBinding()
        
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(this)
        val audioModeAutoPipEnabled = SettingsManager.getAudioModeAutoPipEnabledSync(this)
        //  [重构] 使用新的模式判断方法
        val shouldEnterPip = miniPlayerManager.shouldEnterPip()
        val currentMode = miniPlayerManager.getCurrentMode()
        val isActuallyPlaying = miniPlayerManager.isPlaying || (miniPlayerManager.player?.isPlaying == true)
        val isPlaybackRouteActive = isPlaybackRouteActive(
            isInVideoDetail = isInVideoDetail,
            isInAudioMode = isInAudioModeRoute
        )
        val shouldTriggerPip = shouldTriggerPlaybackRoutePip(
            isInVideoDetail = isInVideoDetail,
            isInAudioMode = isInAudioModeRoute,
            isInMiniMode = miniPlayerManager.isMiniMode,
            audioModeAutoPipEnabled = audioModeAutoPipEnabled,
            shouldEnterPip = shouldEnterPip,
            isActuallyPlaying = isActuallyPlaying
        )
        miniPlayerManager.updatePlaybackRoutePipRequest(shouldTriggerPip)

        Logger.d(
            TAG,
            " miniPlayerMode=$currentMode, audioModeAutoPipEnabled=$audioModeAutoPipEnabled, shouldEnterPip=$shouldEnterPip, isPlaying=$isActuallyPlaying, shouldTriggerPip=$shouldTriggerPip, API=${Build.VERSION.SDK_INT}"
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldTriggerPip) {
            try {
                Logger.d(TAG, " 尝试进入 PiP 模式...")
                
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(
                        buildPipPlaybackRemoteActions(
                            context = this,
                            player = miniPlayerManager.player
                        )
                    )
                
                // Android 12+: 启用自动进入和无缝调整
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pipParams.setAutoEnterEnabled(true)
                    pipParams.setSeamlessResizeEnabled(true)
                }
                
                enterPictureInPictureMode(pipParams.build())
                Logger.d(TAG, " 成功进入 PiP 模式")
            } catch (e: Exception) {
                miniPlayerManager.updatePlaybackRoutePipRequest(false)
                Logger.e(TAG, " 进入 PiP 失败", e)
            }
        } else {
            Logger.d(TAG, "⏳ 未满足 PiP 条件: API>=${Build.VERSION_CODES.O}=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O}, shouldTriggerPip=$shouldTriggerPip")
        }
    }

    private fun enterMiniPlayerPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!miniPlayerManager.shouldEnterPip() || miniPlayerManager.player == null) return
        miniPlayerManager.updatePlaybackRoutePipRequest(true)
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(
                    buildPipPlaybackRemoteActions(
                        context = this,
                        player = miniPlayerManager.player
                    )
                )
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()
            enterPictureInPictureMode(params)
        } catch (error: Exception) {
            miniPlayerManager.updatePlaybackRoutePipRequest(false)
            Logger.e(TAG, "小窗切换画中画失败", error)
        }
    }
    
    //  PiP 模式变化回调
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (startupRecoveryRedirected) return
        isInPipMode = isInPictureInPictureMode
        miniPlayerManager.updateSystemPipActive(isInPictureInPictureMode)
        Logger.d(TAG, " PiP 模式变化: $isInPictureInPictureMode")
    }
    
    //  [新增] 处理 singleTop 模式下的新 Intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (startupRecoveryRedirected) return
        setIntent(intent)
        handleIntent(intent)
    }
    
    // 📺 [DLNA] 权限请求和服务绑定 - 移除自动请求，改为按需请求
    // private val dlnaPermissionLauncher = ...
    
    // private fun requestDlnaPermissionsAndBind() { ... }
    
    //  待导航的视频 ID（用于在 Compose 中触发导航）
    var pendingVideoId by mutableStateOf<String?>(null)
    var pendingRoute by mutableStateOf<String?>(null)  // 🚀 App Shortcuts: pending route
    var pendingSearchKeyword by mutableStateOf<String?>(null)
    var pendingNavigationRoute by mutableStateOf<String?>(null)
        private set
    
    /**
     *  [新增] 处理 Deep Link 和分享意图
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        PlaybackHandoffCodec.fromIntent(intent)?.let { payload ->
            pendingNavigationRoute = PlaybackHandoffCodec.toRoute(payload)
            Logger.d(TAG, "🔄 Received Android 17 playback handoff")
            return
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_PLAYBACK, false)) {
            val activePlayer = miniPlayerManager.player
            val activeBvid = miniPlayerManager.currentBvid
            val activeCid = miniPlayerManager.currentCid
            val activePositionMs = activePlayer?.currentPosition
                ?.coerceAtLeast(0L)
                ?: miniPlayerManager.currentPosition.coerceAtLeast(0L)
            val activePlaybackRoute = resolveActivePlaybackReturnRoute(
                isActive = miniPlayerManager.isActive && activePlayer != null,
                bvid = activeBvid,
                cid = activeCid,
                currentPositionMs = activePositionMs,
            )
            if (activePlaybackRoute != null) {
                // ON_PAUSE saved the position at the moment the app left the foreground. Refresh
                // that cache before navigation so an emergency re-prepare cannot seek back to it
                // after background playback has continued on the same ExoPlayer.
                PlaybackProgressManager.getInstance(this).savePosition(
                    bvid = requireNotNull(activeBvid),
                    cid = activeCid,
                    positionMs = activePositionMs,
                    durationMs = activePlayer?.duration?.coerceAtLeast(0L) ?: 0L,
                )
                if (!isInVideoDetail) {
                    pendingNavigationRoute = activePlaybackRoute
                }
                Logger.d(
                    TAG,
                    "🎵 Restore active playback from system media entry: " +
                        "bvid=$activeBvid, cid=$activeCid, position=$activePositionMs"
                )
                return
            }
        }

        intent.getStringExtra(EXTRA_PENDING_NAVIGATION_ROUTE)
            ?.takeIf { it.isNotBlank() }
            ?.let { route ->
                Logger.d(TAG, "🧭 restore route from intent extra: $route")
                pendingNavigationRoute = route
                return
            }
        
        Logger.d(TAG, "🔗 handleIntent: action=${intent.action}, data=${intent.data}")
        
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // 点击链接打开
                val uri = intent.data
                if (uri != null) {
                    val scheme = uri.scheme ?: ""
                    val host = uri.host ?: ""

                    val pluginInstallRequest = resolvePluginInstallDeepLink(uri.toString())
                    if (pluginInstallRequest != null) {
                        pendingNavigationRoute = ScreenRoutes.PluginsSettings
                            .createRoute(importUrl = pluginInstallRequest.pluginUrl)
                        Logger.d(TAG, "🚀 Plugin install deep link detected: ${pluginInstallRequest.pluginUrl}")
                        return
                    }
                    
                    // 🚀 App Shortcuts: bilipai:// scheme
                    if (scheme == "bilipai") {
                        pendingRoute = host  // e.g., "search", "dynamic", "favorite", "history"
                        Logger.d(TAG, "🚀 App Shortcut detected: $host")
                    } else {
                        resolveIntentLinkAndNavigate(uri.toString())
                    }
                }
            }
            Intent.ACTION_SEND -> {
                // 分享文本到 app
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    Logger.d(TAG, "📤 收到分享文本: $text")
                    
                    val urls = BilibiliUrlParser.extractUrls(text)
                    val pluginInstallLink = urls.firstOrNull { resolvePluginInstallDeepLink(it) != null }

                    if (pluginInstallLink != null) {
                        val pluginInstallRequest = resolvePluginInstallDeepLink(pluginInstallLink)
                        if (pluginInstallRequest != null) {
                            pendingNavigationRoute = ScreenRoutes.PluginsSettings
                                .createRoute(importUrl = pluginInstallRequest.pluginUrl)
                            Logger.d(TAG, "🚀 Plugin install shared link detected: ${pluginInstallRequest.pluginUrl}")
                            return
                        }
                    }

                    resolveIntentLinkAndNavigate(text)
                }
            }
        }
    }
    
    /**
     *  统一解析入口链接，必要时异步展开 b23.tv
     */
    private fun resolveIntentLinkAndNavigate(rawInput: String) {
        BilibiliNavigationTargetParser.parse(rawInput)?.let { target ->
            applyIntentNavigationTarget(target)
            return
        }

        resolveIntentLinkFallbackRoute(rawInput)?.let { route ->
            Logger.d(TAG, "🌐 入口链接先回退到 WebView: $route")
            pendingNavigationRoute = route
        }

        lifecycleScope.launch {
            val target = BilibiliNavigationTargetParser.resolve(rawInput)
            if (target != null) {
                applyIntentNavigationTarget(target)
            } else {
                resolveIntentLinkFallbackRoute(rawInput)?.let { route ->
                    Logger.d(TAG, "🌐 入口链接回退到 WebView: $route")
                    pendingNavigationRoute = route
                    return@launch
                }
                Logger.w(TAG, "⚠️ 无法解析入口链接: $rawInput")
            }
        }
    }

    private fun applyIntentNavigationTarget(target: BilibiliNavigationTarget) {
        val navigation = resolveMainActivityLinkNavigation(target)
        if (navigation == null) {
            Logger.w(TAG, "⚠️ 暂不支持入口目标: $target")
            return
        }
        navigation.pendingVideoId?.let { videoId ->
            Logger.d(TAG, "📺 入口链接解析到视频: $videoId")
            pendingVideoId = videoId
            return
        }
        navigation.pendingSearchKeyword?.let { keyword ->
            Logger.d(TAG, "🔎 入口链接解析到搜索词: $keyword")
            pendingSearchKeyword = keyword
        }
        navigation.pendingNavigationRoute?.let { route ->
            Logger.d(TAG, "🧭 入口链接解析到路由: $route")
            pendingNavigationRoute = route
        }
    }
    
    override fun onDestroy() {
        if (startupRecoveryRedirected) {
            super.onDestroy()
            return
        }
        PlaybackHandoffRegistry.setAvailabilityListener(null)
        if (Build.VERSION.SDK_INT >= 37 && android17HandoffEnabled) {
            setHandoffEnabled(false, null)
            android17HandoffEnabled = false
        }
        runtimeJankStats?.isTrackingEnabled = false
        runtimeJankStats = null
        super.onDestroy()
    }
}

/**
 *  首次启动欢迎弹窗 - 精美设计版
 */
