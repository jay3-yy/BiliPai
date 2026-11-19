package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.HomeCardInfoGlassMode
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import kotlin.math.max
import kotlin.math.min

private const val HOME_WALLPAPER_HOME_ROUTE_BASE = "home"
private const val HOME_WALLPAPER_MAIN_HOST_ROUTE = "main_host"
private const val HOME_WALLPAPER_CHAT_ROUTE_BASE = "chat"

/**
 * 底栏宿主的当前路由是 `main_host`，真正的首页/动态等 tab 在 [mainHostTabRoute]。
 * 推入详情后则直接使用 [currentRoute]。
 */
internal fun resolveGlobalHomeWallpaperRoute(
    currentRoute: String?,
    mainHostTabRoute: String? = null,
): String? {
    val normalized = normalizeHomeWallpaperRoute(currentRoute)
    if (normalized == HOME_WALLPAPER_MAIN_HOST_ROUTE) {
        val tabRoute = normalizeHomeWallpaperRoute(mainHostTabRoute)
        if (!tabRoute.isNullOrBlank()) return tabRoute
    }
    return normalized
}

// Explicit opt-in keeps players, web content and destinations with their own artwork opaque.
private val GLOBAL_WALLPAPER_PAGE_ROUTES = setOf(
    "home", "dynamic", "profile", "settings", "settings_category", "settings_search",
    "appearance_settings", "home_settings", "icon_settings", "animation_settings",
    "playback_settings", "permission_settings", "space_privacy_settings", "login_activity_settings", "message_notification_settings",
    "plugins_settings", "content_filter_history", "bottom_bar_settings", "settings_share", "webdav_backup", "tips_settings",
    "history", "history_search", "favorite", "favorite_subscribed", "favorite_search",
    "liked_videos", "watch_later", "watch_later_search", "following", "download_list",
    "live_list", "live_search", "live_area", "live_area_detail", "live_following",
    "inbox", "message", "chat", "partition", "search", "search_trending", "topic",
    "bangumi", "weekly_series", "season_series_detail",
)

/** Whether a destination uses the shared wallpaper rather than its own media background. */
internal fun shouldRenderGlobalHomeWallpaperBackdrop(
    effectScope: HomeWallpaperEffectScope,
    currentRoute: String?,
    mainHostTabRoute: String? = null,
): Boolean {
    if (effectScope != HomeWallpaperEffectScope.GLOBAL) return false
    val route = resolveGlobalHomeWallpaperRoute(
        currentRoute = currentRoute,
        mainHostTabRoute = mainHostTabRoute,
    )
    if (route.isNullOrBlank()) return false
    // bangumi/play is a player even though bangumi itself is a catalog page.
    if (route == "bangumi/play" || route.startsWith("bangumi/play/")) return false
    return route.substringBefore('/') in GLOBAL_WALLPAPER_PAGE_ROUTES
}

/**
 * 全局壁纸是否跟随卡片景深（缩/糊/压暗）。
 *
 * 始终关闭：壁纸钉在根层不动；景深只作用在来源页内容上。
 * 壁纸跟缩/放大时（尤其全屏壁纸）会出现「猛的一下」的缩放感。
 */
internal fun shouldApplyVideoCardDepthToGlobalHomeWallpaper(
    wallpaperVisible: Boolean,
    phase: VideoCardTransitionBackgroundPhase,
): Boolean {
    @Suppress("UNUSED_PARAMETER")
    val ignoredVisible = wallpaperVisible
    @Suppress("UNUSED_PARAMETER")
    val ignoredPhase = phase
    return false
}

/**
 * 全局壁纸 Chrome 透传：与 LocalGlobalWallpaperBackdropVisible 语义对齐。
 */
internal fun shouldExposeGlobalHomeWallpaperChrome(
    effectScope: HomeWallpaperEffectScope,
    hasWallpaperUri: Boolean,
    currentRoute: String?,
    mainHostTabRoute: String? = null,
): Boolean = shouldRenderGlobalHomeWallpaperBackdrop(
    effectScope = effectScope,
    currentRoute = currentRoute,
    mainHostTabRoute = mainHostTabRoute,
) && hasWallpaperUri

private fun normalizeHomeWallpaperRoute(route: String?): String? {
    return route?.trim()?.takeIf { it.isNotBlank() }?.substringBefore("?")
}

/** Dynamic home/chat already own a media renderer; the root must not start another player. */
internal fun shouldRenderRootHomeWallpaperMedia(
    wallpaperVisible: Boolean,
    isStaticWallpaper: Boolean,
    currentRoute: String?,
    mainHostTabRoute: String? = null,
): Boolean {
    if (!wallpaperVisible) return false
    if (isStaticWallpaper) return true
    val route = resolveGlobalHomeWallpaperRoute(currentRoute, mainHostTabRoute)?.substringBefore('/')
    return route != HOME_WALLPAPER_HOME_ROUTE_BASE && route != HOME_WALLPAPER_CHAT_ROUTE_BASE
}

data class HomeGlassChromeStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val shadowAlpha: Float
)

data class HomeGlassPillStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val contentAlpha: Float
)

data class HomeGlassResolvedColors(
    val containerColor: Color,
    val borderColor: Color,
    val highlightColor: Color
)

enum class HomeRefreshTipSurfaceStyle {
    GLASS,
    PLAIN
}

data class HomeRefreshTipAppearance(
    val surfaceStyle: HomeRefreshTipSurfaceStyle,
    val borderWidthDp: Float,
    val tonalElevationDp: Float,
    val shadowElevationDp: Float
)

data class HomeWallpaperBackdropAppearance(
    val visible: Boolean,
    val baseBackgroundAlpha: Float,
    val detailAlpha: Float,
    val scrimAlpha: Float,
    val bottomScrimAlpha: Float,
    val blurRadiusDp: Float
)

data class HomeCardInfoSurfaceAppearance(
    val useTintedSurface: Boolean,
    val containerAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    /**
     * Sample [LocalWallpaperHazeState] (wallpaper-only source) like the bottom bar.
     * Must never use the main content HazeState — cards live inside that source tree.
     */
    val useRealtimeHaze: Boolean = false,
    /** LayerBackdrop liquid-glass path (refraction), independent of Haze. */
    val useRealtimeLiquidGlass: Boolean = false
)

internal enum class HomeCardWallpaperSurfaceMode {
    STANDARD,
    LIGHTWEIGHT_TINT,
    REALTIME_FROSTED,
}

/**
 * Select the card-info material without reading frame-rate state during composition. The
 * caller supplies source readiness and the current performance budget as stable inputs; scroll
 * position is resolved by the backdrop draw node itself.
 */
internal fun resolveHomeCardWallpaperSurfaceMode(
    dynamicTintEnabled: Boolean,
    frostedGlassEnabled: Boolean,
    wallpaperVisible: Boolean,
    wallpaperIsStatic: Boolean,
    backdropReady: Boolean,
    blurEnabled: Boolean,
    isDataSaverActive: Boolean,
    lowBlurBudgetForced: Boolean,
    sdkInt: Int,
): HomeCardWallpaperSurfaceMode {
    if (!wallpaperVisible) {
        return HomeCardWallpaperSurfaceMode.STANDARD
    }
    if (
        frostedGlassEnabled &&
            wallpaperIsStatic &&
            backdropReady &&
            blurEnabled &&
            !isDataSaverActive &&
            !lowBlurBudgetForced &&
            sdkInt >= 31
    ) {
        return HomeCardWallpaperSurfaceMode.REALTIME_FROSTED
    }
    return if (dynamicTintEnabled) {
        HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT
    } else {
        HomeCardWallpaperSurfaceMode.STANDARD
    }
}

internal fun resolveHomeGlassChromeStyle(
    glassEnabled: Boolean,
    blurEnabled: Boolean
): HomeGlassChromeStyle {
    return when {
        !blurEnabled -> HomeGlassChromeStyle(
            containerAlpha = 0.88f,
            borderAlpha = 0.10f,
            highlightAlpha = 0.08f,
            shadowAlpha = 0.08f
        )

        glassEnabled -> HomeGlassChromeStyle(
            containerAlpha = 0.16f,
            borderAlpha = 0.18f,
            highlightAlpha = 0.22f,
            shadowAlpha = 0.14f
        )

        else -> HomeGlassChromeStyle(
            containerAlpha = 0.72f,
            borderAlpha = 0.12f,
            highlightAlpha = 0.10f,
            shadowAlpha = 0.10f
        )
    }
}

internal fun resolveHomeGlassPillStyle(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    emphasized: Boolean
): HomeGlassPillStyle {
    return when {
        // 液态玻璃开关优先于模糊开关：悬浮胶囊不接真实 backdrop 采样（见
        // HomeOverlayPillButton 的 MIUI 爆栈约束），玻璃开启时无论模糊开关
        // 如何都用玻璃半透明 + 白描边，避免「玻璃开 + 模糊关」时退化成实心。
        glassEnabled -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.28f else 0.24f,
            borderAlpha = 0.16f,
            highlightAlpha = if (emphasized) 0.20f else 0.16f,
            contentAlpha = 1f
        )

        !blurEnabled -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.92f else 0.88f,
            borderAlpha = 0.12f,
            highlightAlpha = if (emphasized) 0.12f else 0.08f,
            contentAlpha = 0.96f
        )

        else -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.64f else 0.58f,
            borderAlpha = 0.14f,
            highlightAlpha = if (emphasized) 0.12f else 0.08f,
            contentAlpha = 0.98f
        )
    }
}

internal fun resolveHomeRefreshTipAppearance(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): HomeRefreshTipAppearance {
    return if (!(liquidGlassEnabled && blurEnabled)) {
        HomeRefreshTipAppearance(
            surfaceStyle = HomeRefreshTipSurfaceStyle.PLAIN,
            borderWidthDp = 0f,
            tonalElevationDp = 1f,
            shadowElevationDp = 1f
        )
    } else {
        HomeRefreshTipAppearance(
            surfaceStyle = HomeRefreshTipSurfaceStyle.GLASS,
            borderWidthDp = 0.8f,
            tonalElevationDp = 0f,
            shadowElevationDp = 0f
        )
    }
}

internal fun resolveHomeWallpaperBackdropAppearance(
    hasWallpaper: Boolean,
    effectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    isDarkTheme: Boolean,
    isDataSaverActive: Boolean,
    globalWallpaper: Boolean = false
): HomeWallpaperBackdropAppearance {
    if (!hasWallpaper || effectMode == HomeWallpaperEffectMode.OFF) {
        return HomeWallpaperBackdropAppearance(
            visible = false,
            baseBackgroundAlpha = 1f,
            detailAlpha = 0f,
            scrimAlpha = 0f,
            bottomScrimAlpha = 0f,
            blurRadiusDp = 0f
        )
    }

    if (globalWallpaper) {
        if (isDataSaverActive) {
            return HomeWallpaperBackdropAppearance(
                visible = true,
                baseBackgroundAlpha = if (isDarkTheme) 0.70f else 0.58f,
                detailAlpha = 0.08f,
                scrimAlpha = if (isDarkTheme) 0.28f else 0.14f,
                bottomScrimAlpha = if (isDarkTheme) 0.38f else 0.26f,
                blurRadiusDp = 8f
            )
        }

        return when (effectMode) {
            HomeWallpaperEffectMode.ORIGINAL -> HomeWallpaperBackdropAppearance(
                visible = true,
                baseBackgroundAlpha = if (isDarkTheme) 0.58f else 0.46f,
                detailAlpha = 0f,
                scrimAlpha = if (isDarkTheme) 0.22f else 0.10f,
                bottomScrimAlpha = if (isDarkTheme) 0.30f else 0.22f,
                blurRadiusDp = 0f
            )
            HomeWallpaperEffectMode.STRONG_BLUR -> HomeWallpaperBackdropAppearance(
                visible = true,
                baseBackgroundAlpha = if (isDarkTheme) 0.66f else 0.52f,
                detailAlpha = 0.04f,
                scrimAlpha = if (isDarkTheme) 0.30f else 0.14f,
                bottomScrimAlpha = if (isDarkTheme) 0.40f else 0.28f,
                blurRadiusDp = 32f
            )
            HomeWallpaperEffectMode.SOFT_BLUR,
            HomeWallpaperEffectMode.OFF -> HomeWallpaperBackdropAppearance(
                visible = true,
                baseBackgroundAlpha = if (isDarkTheme) 0.56f else 0.44f,
                detailAlpha = 0.12f,
                scrimAlpha = if (isDarkTheme) 0.26f else 0.12f,
                bottomScrimAlpha = if (isDarkTheme) 0.34f else 0.24f,
                blurRadiusDp = 14f
            )
        }
    }

    return when {
        effectMode == HomeWallpaperEffectMode.ORIGINAL -> HomeWallpaperBackdropAppearance(
            visible = true,
            baseBackgroundAlpha = if (isDarkTheme) 0.24f else 0.14f,
            detailAlpha = 0f,
            scrimAlpha = if (isDarkTheme) 0.16f else 0.04f,
            bottomScrimAlpha = if (isDarkTheme) 0.22f else 0.12f,
            blurRadiusDp = 0f
        )

        effectMode == HomeWallpaperEffectMode.STRONG_BLUR -> HomeWallpaperBackdropAppearance(
            visible = true,
            baseBackgroundAlpha = if (isDarkTheme) 0.50f else 0.34f,
            detailAlpha = 0.05f,
            scrimAlpha = if (isDarkTheme) 0.28f else 0.12f,
            bottomScrimAlpha = if (isDarkTheme) 0.40f else 0.26f,
            blurRadiusDp = 60f
        )

        isDataSaverActive -> HomeWallpaperBackdropAppearance(
            visible = true,
            baseBackgroundAlpha = if (isDarkTheme) 0.48f else 0.34f,
            detailAlpha = 0.16f,
            scrimAlpha = if (isDarkTheme) 0.20f else 0.08f,
            bottomScrimAlpha = if (isDarkTheme) 0.28f else 0.18f,
            blurRadiusDp = 18f
        )

        isDarkTheme -> HomeWallpaperBackdropAppearance(
            visible = true,
            baseBackgroundAlpha = 0.34f,
            detailAlpha = 0.24f,
            scrimAlpha = 0.24f,
            bottomScrimAlpha = 0.34f,
            blurRadiusDp = 24f
        )

        else -> HomeWallpaperBackdropAppearance(
            visible = true,
            baseBackgroundAlpha = 0.22f,
            detailAlpha = 0.32f,
            scrimAlpha = 0.06f,
            bottomScrimAlpha = 0.18f,
            blurRadiusDp = 22f
        )
    }
}

internal fun resolveHomeWallpaperUri(
    homeWallpaperUri: String?,
    splashWallpaperUri: String?
): String {
    val dedicatedHomeUri = homeWallpaperUri?.trim().orEmpty()
    if (dedicatedHomeUri.isNotEmpty()) return dedicatedHomeUri
    return splashWallpaperUri?.trim().orEmpty()
}

internal fun resolveHomeWallpaperDecodeSizePx(
    screenWidthDp: Int,
    screenHeightDp: Int,
    density: Float,
    isDataSaverActive: Boolean,
    blurRadiusDp: Float = 0f,
): Pair<Int, Int> {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val widthPx = (screenWidthDp.coerceAtLeast(320) * safeDensity).toInt().coerceAtLeast(720)
    val heightPx = (screenHeightDp.coerceAtLeast(568) * safeDensity).toInt().coerceAtLeast(1280)
    val shortSide = min(widthPx, heightPx)
    val longSide = max(widthPx, heightPx)
    val maxShortSide = when {
        isDataSaverActive -> 720
        blurRadiusDp >= 24f -> 540
        blurRadiusDp >= 12f -> 720
        else -> 1080
    }
    val maxLongSide = when {
        isDataSaverActive -> 1280
        blurRadiusDp >= 24f -> 960
        blurRadiusDp >= 12f -> 1280
        else -> 1920
    }
    return min(shortSide, maxShortSide) to min(longSide, maxLongSide)
}

/**
 * Realtime Haze on the info strip — wallpaper-only source required to avoid prepareTree SO.
 */
internal fun shouldUseRealtimeHomeCardInfoBlur(
    infoGlassMode: HomeCardInfoGlassMode,
    hasWallpaperHazeState: Boolean,
    blurEnabled: Boolean,
    isDataSaverActive: Boolean
): Boolean {
    if (!infoGlassMode.usesRealtimeBlur) return false
    if (!hasWallpaperHazeState || !blurEnabled || isDataSaverActive) return false
    return true
}

/**
 * Realtime liquid glass on the info strip — needs LayerBackdrop + blur pipeline allowed.
 */
internal fun shouldUseRealtimeHomeCardInfoLiquidGlass(
    infoGlassMode: HomeCardInfoGlassMode,
    hasLayerBackdrop: Boolean,
    blurEnabled: Boolean,
    isDataSaverActive: Boolean
): Boolean {
    if (!infoGlassMode.usesRealtimeLiquidGlass) return false
    if (!hasLayerBackdrop || !blurEnabled || isDataSaverActive) return false
    return true
}

internal fun resolveHomeCardInfoSurfaceAppearance(
    wallpaperTintEnabled: Boolean,
    wallpaperEffectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    isDarkTheme: Boolean,
    isDataSaverActive: Boolean,
    infoGlassMode: HomeCardInfoGlassMode = HomeCardInfoGlassMode.OFF,
    hasWallpaperHazeState: Boolean = false,
    hasLayerBackdrop: Boolean = false,
    blurEnabled: Boolean = true
): HomeCardInfoSurfaceAppearance {
    val useRealtimeHaze = shouldUseRealtimeHomeCardInfoBlur(
        infoGlassMode = infoGlassMode,
        hasWallpaperHazeState = hasWallpaperHazeState,
        blurEnabled = blurEnabled,
        isDataSaverActive = isDataSaverActive
    )
    val useRealtimeLiquidGlass = shouldUseRealtimeHomeCardInfoLiquidGlass(
        infoGlassMode = infoGlassMode,
        hasLayerBackdrop = hasLayerBackdrop,
        blurEnabled = blurEnabled,
        isDataSaverActive = isDataSaverActive
    )
    val glassActive = useRealtimeHaze || useRealtimeLiquidGlass

    // Wallpaper tint alone (no glass mode): keep previous translucent fill without Haze.
    if (!glassActive && (!wallpaperTintEnabled || wallpaperEffectMode == HomeWallpaperEffectMode.OFF)) {
        return HomeCardInfoSurfaceAppearance(
            useTintedSurface = false,
            containerAlpha = 1f,
            borderAlpha = 0f,
            highlightAlpha = 0f,
            useRealtimeHaze = false,
            useRealtimeLiquidGlass = false
        )
    }

    if (!glassActive) {
        val baseContainerAlpha = when {
            wallpaperEffectMode == HomeWallpaperEffectMode.ORIGINAL && isDarkTheme -> 0.26f
            wallpaperEffectMode == HomeWallpaperEffectMode.ORIGINAL -> 0.12f
            wallpaperEffectMode == HomeWallpaperEffectMode.STRONG_BLUR && isDarkTheme -> 0.50f
            wallpaperEffectMode == HomeWallpaperEffectMode.STRONG_BLUR -> 0.32f
            isDataSaverActive -> if (isDarkTheme) 0.56f else 0.36f
            isDarkTheme -> 0.36f
            else -> 0.16f
        }
        return HomeCardInfoSurfaceAppearance(
            useTintedSurface = true,
            containerAlpha = baseContainerAlpha,
            borderAlpha = when {
                wallpaperEffectMode == HomeWallpaperEffectMode.ORIGINAL && isDarkTheme -> 0.18f
                wallpaperEffectMode == HomeWallpaperEffectMode.ORIGINAL -> 0.22f
                isDarkTheme -> 0.12f
                else -> 0.14f
            },
            highlightAlpha = if (isDarkTheme) 0.04f else 0.06f,
            useRealtimeHaze = false,
            useRealtimeLiquidGlass = false
        )
    }

    // Glass modes: keep fill very light so frosted/refracted wallpaper stays readable.
    return HomeCardInfoSurfaceAppearance(
        useTintedSurface = true,
        containerAlpha = when {
            useRealtimeHaze && useRealtimeLiquidGlass -> if (isDarkTheme) 0.10f else 0.06f
            useRealtimeLiquidGlass -> if (isDarkTheme) 0.12f else 0.08f
            else -> if (isDarkTheme) 0.14f else 0.08f
        },
        borderAlpha = if (isDarkTheme) 0.20f else 0.22f,
        highlightAlpha = if (isDarkTheme) 0.08f else 0.12f,
        useRealtimeHaze = useRealtimeHaze,
        useRealtimeLiquidGlass = useRealtimeLiquidGlass
    )
}

internal fun resolveHomeGlassCoverPillBaseColor(): Color {
    // Cover badges sit directly on top of unpredictable thumbnails, so keep the
    // glass tint dark to preserve white text contrast in history/favorites/etc.
    return Color.Black
}

@Composable
internal fun rememberHomeGlassChromeColors(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    baseColor: Color = AppSurfaceTokens.cardContainer()
): HomeGlassResolvedColors {
    val style = remember(glassEnabled, blurEnabled) {
        resolveHomeGlassChromeStyle(
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled
        )
    }
    return remember(style, baseColor) {
        HomeGlassResolvedColors(
            containerColor = baseColor.copy(alpha = style.containerAlpha),
            borderColor = Color.White.copy(alpha = style.borderAlpha),
            highlightColor = Color.White.copy(alpha = style.highlightAlpha)
        )
    }
}
