package com.android.purebilibili.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.HomeCardInfoGlassMode
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeGlassVisualPolicyTest {

    @Test
    fun prefersLightStructuralTintWhenGlassAndBlurAreEnabled() {
        val style = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = true
        )

        assertEquals(0.16f, style.containerAlpha)
        assertEquals(0.22f, style.highlightAlpha)
        assertEquals(0.18f, style.borderAlpha)
    }

    @Test
    fun fallsBackToDenserChromeWhenBlurIsDisabled() {
        val style = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = false
        )

        assertTrue(style.containerAlpha > 0.8f)
        assertEquals(0.08f, style.highlightAlpha)
        assertEquals(0.10f, style.borderAlpha)
    }

    @Test
    fun givesPillsStrongerFillThanChromeToProtectReadability() {
        val chromeStyle = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = true
        )
        val pillStyle = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = false
        )

        assertTrue(pillStyle.containerAlpha > chromeStyle.containerAlpha)
        assertEquals(0.24f, pillStyle.containerAlpha)
        assertEquals(0.16f, pillStyle.borderAlpha)
    }

    @Test
    fun emphasizedPillsGetSlightlyStrongerHighlight() {
        val normal = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = false
        )
        val emphasized = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = true
        )

        assertTrue(emphasized.highlightAlpha > normal.highlightAlpha)
        assertEquals(0.20f, emphasized.highlightAlpha)
    }

    @Test
    fun coverOverlayPillsStayDarkToProtectThumbnailContrast() {
        val baseColor = resolveHomeGlassCoverPillBaseColor()

        assertEquals(Color.Black, baseColor)
        assertTrue(baseColor.luminance() < 0.01f)
    }

    @Test
    fun refreshTipUsesPlainMaterialStyleWhenGlassAndBlurAreDisabled() {
        val appearance = resolveHomeRefreshTipAppearance(
            liquidGlassEnabled = false,
            blurEnabled = false
        )

        assertEquals(HomeRefreshTipSurfaceStyle.PLAIN, appearance.surfaceStyle)
        assertEquals(0f, appearance.borderWidthDp)
        assertEquals(1f, appearance.shadowElevationDp)
    }

    @Test
    fun refreshTipUsesPlainMaterialStyleWhenLiquidGlassIsDisabledEvenIfBlurIsActive() {
        val appearance = resolveHomeRefreshTipAppearance(
            liquidGlassEnabled = false,
            blurEnabled = true
        )

        assertEquals(HomeRefreshTipSurfaceStyle.PLAIN, appearance.surfaceStyle)
        assertEquals(0f, appearance.borderWidthDp)
        assertEquals(1f, appearance.tonalElevationDp)
        assertEquals(1f, appearance.shadowElevationDp)
    }

    @Test
    fun refreshTipUsesPlainMaterialStyleWhenLiquidGlassIsEnabledButBlurIsDisabled() {
        val appearance = resolveHomeRefreshTipAppearance(
            liquidGlassEnabled = true,
            blurEnabled = false
        )

        assertEquals(HomeRefreshTipSurfaceStyle.PLAIN, appearance.surfaceStyle)
        assertEquals(0f, appearance.borderWidthDp)
        assertEquals(1f, appearance.tonalElevationDp)
        assertEquals(1f, appearance.shadowElevationDp)
    }

    @Test
    fun refreshTipKeepsGlassStyleWhenLiquidGlassAndBlurAreEnabled() {
        val appearance = resolveHomeRefreshTipAppearance(
            liquidGlassEnabled = true,
            blurEnabled = true
        )

        assertEquals(HomeRefreshTipSurfaceStyle.GLASS, appearance.surfaceStyle)
        assertEquals(0.8f, appearance.borderWidthDp)
        assertEquals(0f, appearance.tonalElevationDp)
        assertEquals(0f, appearance.shadowElevationDp)
    }

    @Test
    fun homeWallpaperBackdropIsDisabledWithoutWallpaper() {
        val appearance = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = false,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertEquals(false, appearance.visible)
        assertEquals(1f, appearance.baseBackgroundAlpha)
        assertEquals(0f, appearance.detailAlpha)
    }

    @Test
    fun homeWallpaperBackdropKeepsReadableScrimInLightTheme() {
        val appearance = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertEquals(true, appearance.visible)
        assertEquals(0.22f, appearance.baseBackgroundAlpha)
        assertEquals(0.32f, appearance.detailAlpha)
        assertTrue(appearance.scrimAlpha <= 0.08f)
        assertTrue(appearance.blurRadiusDp in 16f..28f)
    }

    @Test
    fun homeWallpaperBackdropUsesConservativeTintInDataSaver() {
        val normal = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val dataSaver = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = true
        )

        assertTrue(dataSaver.detailAlpha < normal.detailAlpha)
        assertTrue(dataSaver.baseBackgroundAlpha > normal.baseBackgroundAlpha)
    }

    @Test
    fun homeWallpaperBackdropCanUseOriginalWallpaperWithoutBlur() {
        val appearance = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.ORIGINAL,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertEquals(true, appearance.visible)
        assertEquals(0f, appearance.blurRadiusDp)
        assertTrue(appearance.baseBackgroundAlpha < 0.2f)
    }

    @Test
    fun homeWallpaperBackdropCanUseStrongBlur() {
        val softBlur = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val strongBlur = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.STRONG_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertTrue(strongBlur.blurRadiusDp > softBlur.blurRadiusDp)
        assertTrue(strongBlur.blurRadiusDp >= 56f)
        assertTrue(strongBlur.detailAlpha < softBlur.detailAlpha)
        assertTrue(strongBlur.baseBackgroundAlpha > softBlur.baseBackgroundAlpha)
    }

    @Test
    fun globalWallpaperCoversSupportedPagesAndRetainsMediaBoundaries() {
        val supportedRoutes = listOf(
            "home", "dynamic", "profile", "settings", "settings_category/HOME",
            "appearance_settings", "history", "favorite", "watch_later", "inbox",
            "message/reply_me", "chat/123/1?name=UP主", "search?keyword=壁纸", "bangumi",
        )
        supportedRoutes.forEach { route ->
            assertTrue(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.GLOBAL, route), route)
            assertFalse(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.HOME_ONLY, route), route)
        }
        listOf(null, "", "main_host", "video/BV1", "story", "live/123", "bangumi/play/1/2",
            "offline_video/1", "external_media/123", "web", "login", "space/123", "unknown"
        ).forEach { route ->
            assertFalse(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.GLOBAL, route), route)
        }
        assertTrue(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.GLOBAL, "main_host", "home"))
        assertTrue(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.GLOBAL, "main_host", "dynamic"))
        assertFalse(shouldRenderGlobalHomeWallpaperBackdrop(HomeWallpaperEffectScope.GLOBAL, "main_host", "story"))
        assertTrue(shouldExposeGlobalHomeWallpaperChrome(HomeWallpaperEffectScope.GLOBAL, true, "history"))
        assertFalse(shouldExposeGlobalHomeWallpaperChrome(HomeWallpaperEffectScope.GLOBAL, false, "history"))
        assertEquals("home", resolveGlobalHomeWallpaperRoute("main_host", "home"))
        assertEquals("video/BV1", resolveGlobalHomeWallpaperRoute("video/BV1", "home"))
    }

    @Test
    fun rootMediaDefersToExistingHomeAndChatPlayers() {
        listOf("home", "chat/123/1").forEach { route ->
            assertFalse(shouldRenderRootHomeWallpaperMedia(true, false, route))
            assertTrue(shouldRenderRootHomeWallpaperMedia(true, true, route))
        }
        assertFalse(shouldRenderRootHomeWallpaperMedia(true, false, "main_host", "home"))
        assertTrue(shouldRenderRootHomeWallpaperMedia(true, false, "main_host", "dynamic"))
        assertTrue(shouldRenderRootHomeWallpaperMedia(true, false, "settings"))
        assertFalse(shouldRenderRootHomeWallpaperMedia(false, true, "settings"))
    }

    @Test
    fun globalHomeWallpaperDoesNotFollowVideoCardDepth() {
        assertFalse(
            shouldApplyVideoCardDepthToGlobalHomeWallpaper(
                wallpaperVisible = true,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
            )
        )
        assertFalse(
            shouldApplyVideoCardDepthToGlobalHomeWallpaper(
                wallpaperVisible = true,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
            )
        )
        assertFalse(
            shouldApplyVideoCardDepthToGlobalHomeWallpaper(
                wallpaperVisible = true,
                phase = VideoCardTransitionBackgroundPhase.HELD,
            )
        )
        assertFalse(
            shouldApplyVideoCardDepthToGlobalHomeWallpaper(
                wallpaperVisible = true,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
            )
        )
        assertFalse(
            shouldApplyVideoCardDepthToGlobalHomeWallpaper(
                wallpaperVisible = false,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
            )
        )
    }

    @Test
    fun globalHomeWallpaperBackdropUsesWeakPresenceProtection() {
        val homeOnly = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val global = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            globalWallpaper = true
        )

        assertTrue(global.baseBackgroundAlpha > homeOnly.baseBackgroundAlpha)
        assertTrue(global.scrimAlpha > homeOnly.scrimAlpha)
        assertTrue(global.blurRadiusDp < homeOnly.blurRadiusDp)
        assertTrue(global.blurRadiusDp <= 14f)
    }

    @Test
    fun globalHomeWallpaperBackdropGetsMoreConservativeInDataSaver() {
        val normal = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            globalWallpaper = true
        )
        val dataSaver = resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = true,
            effectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = true,
            globalWallpaper = true
        )

        assertTrue(dataSaver.baseBackgroundAlpha > normal.baseBackgroundAlpha)
        assertTrue(dataSaver.detailAlpha < normal.detailAlpha)
        assertTrue(dataSaver.blurRadiusDp <= 8f)
    }

    @Test
    fun homeWallpaperDecodeSizeCapsLargeScreensAndDataSaver() {
        assertEquals(
            1080 to 1920,
            resolveHomeWallpaperDecodeSizePx(
                screenWidthDp = 1200,
                screenHeightDp = 900,
                density = 3f,
                isDataSaverActive = false
            )
        )
        assertEquals(
            720 to 1280,
            resolveHomeWallpaperDecodeSizePx(
                screenWidthDp = 1200,
                screenHeightDp = 900,
                density = 3f,
                isDataSaverActive = true
            )
        )
        assertEquals(
            540 to 960,
            resolveHomeWallpaperDecodeSizePx(
                screenWidthDp = 1200,
                screenHeightDp = 900,
                density = 3f,
                isDataSaverActive = false,
                blurRadiusDp = 24f
            )
        )
    }

    @Test
    fun cardInfoSurfaceLetsWallpaperTintThroughWhenEnabled() {
        val appearance = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertEquals(true, appearance.useTintedSurface)
        assertEquals(0.16f, appearance.containerAlpha)
        assertTrue(appearance.borderAlpha > 0f)
    }

    @Test
    fun cardInfoSurfaceGetsDenserInDarkThemeAndDataSaver() {
        val light = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val dark = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = true,
            isDataSaverActive = false
        )
        val dataSaver = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = true
        )

        assertTrue(dark.containerAlpha > light.containerAlpha)
        assertTrue(dataSaver.containerAlpha > light.containerAlpha)
    }

    @Test
    fun originalWallpaperModeKeepsReadableCardInfoSurfaceWithoutHidingWallpaper() {
        val softBlur = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val original = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.ORIGINAL,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertTrue(original.containerAlpha < softBlur.containerAlpha)
        assertTrue(original.containerAlpha > 0.10f)
        assertTrue(original.borderAlpha > softBlur.borderAlpha)
    }

    @Test
    fun strongBlurUsesDenserCardInfoSurfaceThanSoftBlur() {
        val softBlur = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )
        val strongBlur = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.STRONG_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false
        )

        assertTrue(strongBlur.containerAlpha > softBlur.containerAlpha)
    }

    @Test
    fun cardInfoRealtimeBlurAndLiquidGlassAreIndependent() {
        val blurOnly = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            infoGlassMode = HomeCardInfoGlassMode.REALTIME_BLUR,
            hasWallpaperHazeState = true,
            hasLayerBackdrop = true,
            blurEnabled = true
        )
        val liquidOnly = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            infoGlassMode = HomeCardInfoGlassMode.REALTIME_LIQUID_GLASS,
            hasWallpaperHazeState = true,
            hasLayerBackdrop = true,
            blurEnabled = true
        )
        val both = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            infoGlassMode = HomeCardInfoGlassMode.BLUR_AND_LIQUID,
            hasWallpaperHazeState = true,
            hasLayerBackdrop = true,
            blurEnabled = true
        )
        assertTrue(blurOnly.useRealtimeHaze)
        assertFalse(blurOnly.useRealtimeLiquidGlass)
        assertFalse(liquidOnly.useRealtimeHaze)
        assertTrue(liquidOnly.useRealtimeLiquidGlass)
        assertTrue(both.useRealtimeHaze)
        assertTrue(both.useRealtimeLiquidGlass)
        // Keep fill light so frosted wallpaper is visible (was invisible before).
        assertTrue(blurOnly.containerAlpha <= 0.14f)
    }

    @Test
    fun cardInfoRealtimeBlurRequiresWallpaperHazeState() {
        val appearance = resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = true,
            wallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
            isDarkTheme = false,
            isDataSaverActive = false,
            infoGlassMode = HomeCardInfoGlassMode.REALTIME_BLUR,
            hasWallpaperHazeState = false,
            hasLayerBackdrop = true,
            blurEnabled = true
        )
        assertFalse(appearance.useRealtimeHaze)
        assertEquals(0.16f, appearance.containerAlpha)
    }

    @Test
    fun cardInfoLiquidGlassRequiresLayerBackdrop() {
        assertFalse(
            shouldUseRealtimeHomeCardInfoLiquidGlass(
                infoGlassMode = HomeCardInfoGlassMode.REALTIME_LIQUID_GLASS,
                hasLayerBackdrop = false,
                blurEnabled = true,
                isDataSaverActive = false
            )
        )
        assertTrue(
            shouldUseRealtimeHomeCardInfoLiquidGlass(
                infoGlassMode = HomeCardInfoGlassMode.REALTIME_LIQUID_GLASS,
                hasLayerBackdrop = true,
                blurEnabled = true,
                isDataSaverActive = false
            )
        )
    }

    @Test
    fun cardInfoRealtimeEffectsDisabledInDataSaver() {
        assertFalse(
            shouldUseRealtimeHomeCardInfoBlur(
                infoGlassMode = HomeCardInfoGlassMode.BLUR_AND_LIQUID,
                hasWallpaperHazeState = true,
                blurEnabled = true,
                isDataSaverActive = true
            )
        )
        assertFalse(
            shouldUseRealtimeHomeCardInfoLiquidGlass(
                infoGlassMode = HomeCardInfoGlassMode.BLUR_AND_LIQUID,
                hasLayerBackdrop = true,
                blurEnabled = true,
                isDataSaverActive = true
            )
        )
    }

    @Test
    fun homeWallpaperUriPrefersDedicatedHomeWallpaper() {
        val resolved = resolveHomeWallpaperUri(
            homeWallpaperUri = "content://home-wallpaper",
            splashWallpaperUri = "file://splash-wallpaper"
        )

        assertEquals("content://home-wallpaper", resolved)
    }

    @Test
    fun homeWallpaperUriFallsBackToSplashWallpaperUntilConfigured() {
        val resolved = resolveHomeWallpaperUri(
            homeWallpaperUri = "",
            splashWallpaperUri = "file://splash-wallpaper"
        )

        assertEquals("file://splash-wallpaper", resolved)
    }

    @Test
    fun homeWallpaperUriCanBeEmptyWhenNoWallpaperExists() {
        val resolved = resolveHomeWallpaperUri(
            homeWallpaperUri = " ",
            splashWallpaperUri = ""
        )

        assertEquals("", resolved)
    }

    @Test
    fun overlayPillsShareOneGlassCapsuleComponent() {
        // 「撤销刷新」「定位上次刷新」必须共用 HomeOverlayPillButton，
        // 避免同屏出现实心 primaryContainer 与毛玻璃两种胶囊。
        listOf(
            "app/src/main/java/com/android/purebilibili/feature/home/HomeRefreshOverlays.kt",
            "app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt",
            "app/src/main/java/com/android/purebilibili/feature/dynamic/DynamicScreen.kt",
        ).forEach { path ->
            val source = listOf(File(path), File(path.removePrefix("app/"))).first(File::exists).readText()
            assertTrue(
                source.contains("HomeOverlayPillButton("),
                "$path 未接入统一的悬浮玻璃胶囊组件",
            )
        }
        val pill = File(
            "app/src/main/java/com/android/purebilibili/feature/home/components/HomeOverlayPillButton.kt"
        ).readText()
        assertTrue(pill.contains("AppShapes.container(ContainerLevel.Pill)"))
        assertTrue(pill.contains("overlayPillColors.borderColor"))
        // 动态页在本地解析同一套颜色，玻璃/模糊开关关闭时与首页退化形态一致。
        val dynamic = File(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/DynamicScreen.kt"
        ).readText()
        assertTrue(dynamic.contains("rememberHomeGlassPillColors("))
    }
}
