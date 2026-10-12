package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.HomeTopRightAction
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.ui.resolveAppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopTabPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderVisualPolicyTest {
    @Test
    fun `collapsed header preserves ordinary status bar blur`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveEffectiveHomeTopContinuousSlabRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                liquidGlassEnabled = false,
                integratedCollapsedTopBar = true,
            ),
        )
    }

    @Test
    fun `local glass controls do not remove requested header blur`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveEffectiveHomeTopContinuousSlabRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                liquidGlassEnabled = true,
                integratedCollapsedTopBar = false,
            ),
        )
    }

    @Test
    fun `glass header still delegates material to its local controls`() {
        assertEquals(
            HomeTopChromeRenderMode.PLAIN,
            resolveEffectiveHomeTopContinuousSlabRenderMode(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                liquidGlassEnabled = true,
                integratedCollapsedTopBar = false,
            ),
        )
    }

    @Test
    fun `legacy top tabs require liquid glass and floating bottom bar both disabled`() {
        assertTrue(
            shouldUseLegacyHomeTopTabs(
                liquidGlassEnabled = false,
                bottomBarFloating = false,
            ),
        )
        assertFalse(
            shouldUseLegacyHomeTopTabs(
                liquidGlassEnabled = true,
                bottomBarFloating = false,
            ),
        )
        assertFalse(
            shouldUseLegacyHomeTopTabs(
                liquidGlassEnabled = false,
                bottomBarFloating = true,
            ),
        )
    }

    @Test
    fun topActionIcon_exportsOnlyThroughActiveLiquidGlassBackdrop() {
        assertTrue(
            shouldExportHomeTopActionIconThroughLiquidGlass(
                usesMatchedTopControls = true,
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                hasBackdrop = true,
            )
        )
        assertFalse(
            shouldExportHomeTopActionIconThroughLiquidGlass(
                usesMatchedTopControls = true,
                renderMode = HomeTopChromeRenderMode.PLAIN,
                hasBackdrop = true,
            )
        )
        assertFalse(
            shouldExportHomeTopActionIconThroughLiquidGlass(
                usesMatchedTopControls = true,
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                hasBackdrop = false,
            )
        )
    }


    @Test
    fun `top right unread badge only appears for inbox action`() {
        assertEquals("8", formatHomeTopRightUnreadBadge(HomeTopRightAction.INBOX, 8))
        assertEquals("99+", formatHomeTopRightUnreadBadge(HomeTopRightAction.INBOX, 120))
        assertEquals(null, formatHomeTopRightUnreadBadge(HomeTopRightAction.INBOX, 0))
        assertEquals(null, formatHomeTopRightUnreadBadge(HomeTopRightAction.SETTINGS, 8))
    }

    @Test
    fun `top right unread badge escapes icon center without clipping`() {
        val layout = resolveHomeTopRightUnreadBadgeLayout()
        assertEquals(0.dp, layout.offsetX)
        assertEquals(0.dp, layout.offsetY)
        assertEquals(9.dp, layout.reservedEndWidth)
        assertEquals(18.dp, layout.minWidth)
        assertEquals(18.dp, layout.minHeight)
        assertEquals(5.dp, layout.horizontalPadding)
        assertEquals(1.dp, layout.verticalPadding)
    }

    @Test
    fun `top right inbox content description includes unread count`() {
        assertEquals(
            "消息，8 条未读",
            resolveHomeTopRightActionContentDescription(HomeTopRightAction.INBOX, 8)
        )
        assertEquals(
            "消息，99+ 条未读",
            resolveHomeTopRightActionContentDescription(HomeTopRightAction.INBOX, 120)
        )
        assertEquals(
            HomeTopRightAction.INBOX.label,
            resolveHomeTopRightActionContentDescription(HomeTopRightAction.INBOX, 0)
        )
        assertEquals(
            HomeTopRightAction.SETTINGS.label,
            resolveHomeTopRightActionContentDescription(HomeTopRightAction.SETTINGS, 8)
        )
    }

    @Test
    fun `wide liquid glass chrome prefers flat treatment to avoid center seam`() {
        assertEquals(
            HomeTopChromeSurfaceTreatment.FLAT_GLASS,
            resolveHomeTopChromeSurfaceTreatment(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                preferFlatGlass = true
            )
        )
        assertEquals(
            HomeTopChromeSurfaceTreatment.FLAT_GLASS,
            resolveHomeTopChromeSurfaceTreatment(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
                preferFlatGlass = true
            )
        )
    }

    @Test
    fun `wide top chrome keeps structured liquid glass to match bottom bar renderer`() {
        assertFalse(
            resolveHomeTopWideChromePreferFlatGlass(
                HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
            )
        )
        assertFalse(
            resolveHomeTopWideChromePreferFlatGlass(
                HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            )
        )
        assertTrue(
            resolveHomeTopWideChromePreferFlatGlass(
                HomeTopChromeRenderMode.BLUR
            )
        )
    }

    @Test
    fun `unified top panel adds a readability scrim over liquid glass`() {
        val liquidDark = resolveHomeTopUnifiedPanelReadabilityColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
        )
        val blurLight = resolveHomeTopUnifiedPanelReadabilityColor(
            isLightMode = true,
            renderMode = HomeTopChromeRenderMode.BLUR
        )

        assertTrue(liquidDark.alpha > 0f)
        assertTrue(blurLight.alpha > 0f)
        assertTrue(liquidDark.red < 0.1f)
        assertTrue(blurLight.red > 0.9f)
    }

    @Test
    fun `liquid glass unified top panel does not draw a center divider`() {
        assertEquals(
            0f,
            resolveHomeTopUnifiedPanelDividerAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP),
            0.0001f
        )
        assertEquals(
            0f,
            resolveHomeTopUnifiedPanelDividerAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_HAZE),
            0.0001f
        )
        assertTrue(resolveHomeTopUnifiedPanelDividerAlpha(HomeTopChromeRenderMode.BLUR) > 0f)
    }

    @Test
    fun `compact controls keep structured glass treatment`() {
        assertEquals(
            HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS,
            resolveHomeTopChromeSurfaceTreatment(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                preferFlatGlass = false
            )
        )
        assertEquals(
            HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS,
            resolveHomeTopChromeSurfaceTreatment(
                renderMode = HomeTopChromeRenderMode.BLUR,
                preferFlatGlass = true
            )
        )
    }

    @Test
    fun `wide blur chrome softens highlight and underlay to avoid banding`() {
        val baseHighlight = Color.White.copy(alpha = 0.10f)
        val softenedHighlight = resolveHomeTopChromeHighlightOverlayColor(
            baseColor = baseHighlight,
            renderMode = HomeTopChromeRenderMode.BLUR,
            softenWideChrome = true
        )
        val defaultUnderlay = resolveHomeTopInnerUnderlayColor(
            isLightMode = true,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val softenedUnderlay = resolveHomeTopInnerUnderlayColor(
            isLightMode = true,
            renderMode = HomeTopChromeRenderMode.BLUR,
            softenWideChrome = true
        )

        assertTrue(softenedHighlight.alpha < baseHighlight.alpha)
        assertTrue(softenedUnderlay.alpha < defaultUnderlay.alpha)
        assertTrue(softenedUnderlay.alpha > 0f)
    }


    @Test
    fun `header scroll hides search row while keeping top tabs pinned`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -96f,
            searchBarHeightPx = 48f,
            searchCollapseDistancePx = 54f,
            tabRowHeightPx = 56f,
            isHeaderCollapseEnabled = true
        )

        assertEquals(0f, layout.searchBarHeightPx, 0.0001f)
        assertEquals(0f, layout.searchAlpha, 0.0001f)
        assertEquals(56f, layout.tabRowHeightPx, 0.0001f)
        assertEquals(1f, layout.tabAlpha, 0.0001f)
    }

    @Test
    fun `header scroll partially collapses search row while keeping top tabs fully visible`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -24f,
            searchBarHeightPx = 48f,
            searchCollapseDistancePx = 54f,
            tabRowHeightPx = 56f,
            isHeaderCollapseEnabled = true
        )

        assertEquals(24f, layout.searchBarHeightPx, 0.0001f)
        assertEquals(0.5f, layout.searchAlpha, 0.0001f)
        assertEquals(56f, layout.tabRowHeightPx, 0.0001f)
        assertEquals(1f, layout.tabAlpha, 0.0001f)
    }

    @Test
    fun `top tab layout keeps screenshot order and pins tabs after search disappears`() {
        val collapsed = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 0.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val expanded = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 48.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.BLUR
        )

        assertEquals(44.dp, collapsed.tabTop)
        assertEquals(96.dp, expanded.tabTop)
        assertEquals(44.dp, collapsed.searchTop)
        assertEquals(collapsed.searchTop, expanded.searchTop)
    }

    @Test
    fun `top blur height covers pinned tabs and only visible search area`() {
        val collapsed = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 0.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val expanded = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 48.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val plain = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 48.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.PLAIN
        )

        assertEquals(100.dp, collapsed.blurHeight)
        assertEquals(152.dp, expanded.blurHeight)
        assertEquals(0.dp, plain.blurHeight)
    }

    @Test
    fun `tab row can be excluded from parent blur slab for non pinned surfaces`() {
        val layout = resolveHomeTopPinnedChromeLayout(
            statusBarHeight = 44.dp,
            visibleSearchHeight = 48.dp,
            tabRowHeight = 56.dp,
            searchToTabsSpacing = 4.dp,
            renderMode = HomeTopChromeRenderMode.BLUR,
            includeTabInBlur = false,
        )

        assertEquals(96.dp, layout.tabTop)
        assertEquals(96.dp, layout.blurHeight)
    }

    @Test
    fun `tiny reverse scroll keeps collapsed search row hidden until reveal threshold is crossed`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -44f,
            searchBarHeightPx = 48f,
            searchCollapseDistancePx = 54f,
            tabRowHeightPx = 56f,
            isHeaderCollapseEnabled = true,
            searchRevealDeadZonePx = 8f
        )

        assertEquals(0f, layout.searchBarHeightPx, 0.0001f)
        assertEquals(0f, layout.searchAlpha, 0.0001f)
        assertEquals(56f, layout.tabRowHeightPx, 0.0001f)
        assertEquals(1f, layout.tabAlpha, 0.0001f)
    }

    @Test
    fun `md3 search row starts revealing with small reverse scroll`() {
        assertEquals(0.dp, resolveHomeTopSearchRevealDeadZone(UiPreset.MD3))

        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -44f,
            searchBarHeightPx = 52f,
            searchCollapseDistancePx = 63f,
            tabRowHeightPx = 48f,
            isHeaderCollapseEnabled = true,
            searchRevealDeadZonePx = resolveHomeTopSearchRevealDeadZone(UiPreset.MD3).value,
            usesImmediateSearchReveal = true
        )

        assertEquals(8f, layout.searchBarHeightPx, 0.0001f)
        assertEquals(
            resolveHomeTopSearchContentRevealFraction(
                searchRevealFraction = 8f / 52f,
                usesImmediateReveal = true
            ),
            layout.searchAlpha,
            0.0001f
        )
        assertEquals(48f, layout.tabRowHeightPx, 0.0001f)
        assertEquals(1f, layout.tabAlpha, 0.0001f)
    }

    @Test
    fun `miuix unified search keeps blur and liquid glass render modes`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopSearchChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                useUnifiedPanel = true,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopSearchChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                uiPreset = UiPreset.MD3,
                useUnifiedPanel = true,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }

    @Test
    fun `miuix unified top tabs keep their local glass render mode and surface`() {
        val tabColor = Color.White.copy(alpha = 0.42f)

        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopUnifiedTabChromeRenderMode(
                localTabChromeRenderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                useUnifiedLiquidChrome = false
            )
        )
        assertEquals(
            tabColor.copy(alpha = 0.5f),
            resolveHomeTopUnifiedTabSurfaceColor(
                tabContainerColor = tabColor,
                tabOverlayAlpha = 0.5f,
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                useUnifiedLiquidChrome = false
            )
        )
    }

    @Test
    fun `unified top tabs keep blur area for all presets`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopUnifiedLocalTabChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.IOS,
                androidNativeVariant = AndroidNativeVariant.MATERIAL3
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopUnifiedLocalTabChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MATERIAL3
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopUnifiedLocalTabChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }

    @Test
    fun `unified top tab blur surface keeps visible backdrop fill`() {
        val tabColor = Color.Black.copy(alpha = 0.38f)

        assertEquals(
            tabColor.copy(alpha = 0.38f),
            resolveHomeTopUnifiedTabSurfaceColor(
                tabContainerColor = tabColor,
                tabOverlayAlpha = 0.38f,
                uiPreset = UiPreset.IOS,
                androidNativeVariant = AndroidNativeVariant.MATERIAL3,
                useUnifiedLiquidChrome = false,
                tabChromeRenderMode = HomeTopChromeRenderMode.BLUR
            )
        )
        assertEquals(
            Color.Transparent,
            resolveHomeTopUnifiedTabSurfaceColor(
                tabContainerColor = tabColor,
                tabOverlayAlpha = 0.38f,
                uiPreset = UiPreset.IOS,
                androidNativeVariant = AndroidNativeVariant.MATERIAL3,
                useUnifiedLiquidChrome = false,
                tabChromeRenderMode = HomeTopChromeRenderMode.PLAIN
            )
        )
    }

    @Test
    fun `md3 partial collapse softens search content alpha instead of using linear fade`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -26f,
            searchBarHeightPx = 52f,
            searchCollapseDistancePx = 63f,
            tabRowHeightPx = 48f,
            isHeaderCollapseEnabled = true,
            searchRevealDeadZonePx = 0f,
            usesImmediateSearchReveal = true
        )

        assertEquals(26f, layout.searchBarHeightPx, 0.0001f)
        assertEquals(0.43f, layout.searchAlpha, 0.0001f)
        assertEquals(48f, layout.tabRowHeightPx, 0.0001f)
        assertEquals(1f, layout.tabAlpha, 0.0001f)
    }

    @Test
    fun `home header collapse distance includes search spacing before pinned tabs`() {
        assertEquals(
            59.dp, // 48 search + 6 searchToTabs + 5 collapseExtra（2B 迁移：iOS 输入并入 MIUIX）
            resolveHomeTopSearchCollapseDistance(
                searchBarHeight = 48.dp,
                uiPreset = UiPreset.IOS
            )
        )
        assertEquals(
            63.dp, // 52 search + 6 searchToTabs + 5 collapseExtra
            resolveHomeTopSearchCollapseDistance(
                searchBarHeight = 52.dp,
                uiPreset = UiPreset.MD3
            )
        )
    }


    @Test
    fun `top controls width tracks container changes and clamps narrow containers`() {
        for (style in listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX)) {
            val policy = resolveAppTopChromePolicy(style)
            assertEquals(0.dp, resolveHomeTopControlsContentWidthDp(0.dp, policy))
            val narrower = resolveHomeTopControlsContentWidthDp(320.dp, policy)
            val wider = resolveHomeTopControlsContentWidthDp(360.dp, policy)
            assertEquals(40.dp, wider - narrower)
        }
    }

    @Test
    fun `detached tab dock requires liquid glass and a capsule presentation`() {
        assertTrue(
            shouldUseDetachedHomeTopTabDock(
                presentation = AppTopTabPresentation.MOVING_CAPSULE,
                liquidGlassEnabled = true,
            )
        )
        assertFalse(
            shouldUseDetachedHomeTopTabDock(
                presentation = AppTopTabPresentation.MOVING_CAPSULE,
                liquidGlassEnabled = false,
            )
        )
        assertFalse(
            shouldUseDetachedHomeTopTabDock(
                presentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
                liquidGlassEnabled = true,
            )
        )
    }


    @Test
    fun `legacy ios collapsed header no longer integrates after migration`() {
        // 2B 迁移：integrated 折叠是 iOS 专属行为，已随单向迁移删除（iOS 输入并入 MIUIX）。
        assertFalse(
            shouldUseIntegratedCollapsedHomeTopBar(
                searchRevealFraction = 0f,
                uiPreset = UiPreset.IOS
            )
        )
        assertFalse(
            shouldUseIntegratedCollapsedHomeTopBar(
                searchRevealFraction = 0.35f,
                uiPreset = UiPreset.IOS
            )
        )
        assertFalse(
            shouldUseIntegratedCollapsedHomeTopBar(
                searchRevealFraction = 0f,
                uiPreset = UiPreset.MD3
            )
        )
    }

    @Test
    fun `integrated collapsed ios header removes floating panel corner radius and shrinks padding`() {
        assertEquals(
            0.dp,
            resolveHomeTopUnifiedPanelCornerRadius(collapsedIntoStatusBar = true)
        )
        assertEquals(
            2.dp,
            resolveHomeTopUnifiedPanelInnerPadding(collapsedIntoStatusBar = true)
        )
    }

    @Test
    fun `reserved list padding includes each measured header height`() {
        for (style in listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX)) {
            val policy = resolveAppTopChromePolicy(style)
            val base = resolveHomeTopReservedListPadding(
                statusBarHeight = 17.dp,
                searchBarHeight = 37.dp,
                tabRowHeight = 43.dp,
                chromePolicy = policy,
            )
            assertTrue(base >= 97.dp)
            assertEquals(
                base + 13.dp,
                resolveHomeTopReservedListPadding(30.dp, 37.dp, 43.dp, policy),
            )
            assertEquals(
                base + 23.dp,
                resolveHomeTopReservedListPadding(17.dp, 60.dp, 43.dp, policy),
            )
            assertEquals(
                base + 19.dp,
                resolveHomeTopReservedListPadding(17.dp, 37.dp, 62.dp, policy),
            )
        }
    }




    @Test
    fun `md3 home header prefers material surface container tiers`() {
        val chromeColors = resolveHomeTopContainerColors(
            uiPreset = UiPreset.MD3,
            emphasized = false,
            blurEnabled = false,
            fallbackColors = HomeGlassResolvedColors(
                containerColor = Color.Red,
                borderColor = Color.Blue,
                highlightColor = Color.White
            ),
            surfaceContainerColor = Color(0xFFF3F3F3),
            surfaceContainerHighColor = Color(0xFFE7E7E7),
            outlineVariantColor = Color(0xFF777777)
        )
        val searchColors = resolveHomeTopContainerColors(
            uiPreset = UiPreset.MD3,
            emphasized = true,
            blurEnabled = false,
            fallbackColors = HomeGlassResolvedColors(
                containerColor = Color.Red,
                borderColor = Color.Blue,
                highlightColor = Color.White
            ),
            surfaceContainerColor = Color(0xFFF3F3F3),
            surfaceContainerHighColor = Color(0xFFE7E7E7),
            outlineVariantColor = Color(0xFF777777)
        )

        assertEquals(Color(0xFFF3F3F3), chromeColors.containerColor)
        assertEquals(Color(0xFFE7E7E7), searchColors.containerColor)
        assertEquals(Color.Transparent, chromeColors.highlightColor)
        assertTrue(searchColors.borderColor.alpha >= chromeColors.borderColor.alpha)
    }

    @Test
    fun `md3 blur container colors keep fallback blur alpha instead of becoming opaque`() {
        val fallback = HomeGlassResolvedColors(
            containerColor = Color(0xFFF3F3F3).copy(alpha = 0.62f),
            borderColor = Color(0xFF777777).copy(alpha = 0.24f),
            highlightColor = Color.Transparent
        )

        val chromeColors = resolveHomeTopContainerColors(
            uiPreset = UiPreset.MD3,
            emphasized = false,
            blurEnabled = true,
            fallbackColors = fallback,
            surfaceContainerColor = Color(0xFFF3F3F3),
            surfaceContainerHighColor = Color(0xFFE7E7E7),
            outlineVariantColor = Color(0xFF777777)
        )

        assertEquals(fallback.containerColor.alpha, chromeColors.containerColor.alpha, 0.0001f)
        assertEquals(Color(0xFFF3F3F3).red, chromeColors.containerColor.red, 0.0001f)
        assertTrue(chromeColors.borderColor.alpha > 0f)
    }

    @Test
    fun `top chrome uses liquid glass when enabled`() {
        assertEquals(
            TopTabMaterialMode.LIQUID_GLASS,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = true,
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `top chrome uses blur when only blur is enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = true,
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `top chrome uses blur when only header blur is enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = true,
                isBottomBarBlurEnabled = false,
                isLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `top chrome uses liquid glass from linked bottom bar when enabled`() {
        assertEquals(
            TopTabMaterialMode.LIQUID_GLASS,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = false,
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `top chrome uses blur when linked bottom bar blur is enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = false,
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = false
            )
        )
    }


    @Test
    fun `home top global liquid glass enables top dock chrome`() {
        val appearance = resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = HomeSettings(
                isTopBarLiquidGlassEnabled = false,
                isBottomBarLiquidGlassEnabled = true,
                isBottomBarBlurEnabled = true,
                androidNativeLiquidGlassEnabled = true
            ),
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )

        assertTrue(appearance.liquidGlassEnabled)
    }

    @Test
    fun `android native global liquid glass enables top tab capsule indicator`() {
        assertTrue(
            resolveHomeTopTabIndicatorLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = true
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertFalse(
            resolveHomeTopTabIndicatorLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.MD3
            )
        )
        // The top dock now follows the bottom bar exactly: per-surface toggles do not
        // bypass the shared Android-native liquid-glass master in either style.
        assertFalse(
            resolveHomeTopTabIndicatorLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = true,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertFalse(
            resolveHomeTopTabIndicatorLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = true,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `top chrome follows shared liquid glass reuse contract`() {
        assertFalse(
            resolveHomeTopChromeLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = true,
                    isBottomBarLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertTrue(
            resolveHomeTopChromeLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    isBottomBarLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = true
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertFalse(
            resolveHomeTopChromeLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = true,
                    isBottomBarLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `home search follows shared liquid glass reuse contract`() {
        assertFalse(
            resolveHomeTopSearchLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = true,
                    isHomeSearchLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertFalse(
            resolveHomeTopSearchLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    isHomeSearchLiquidGlassEnabled = true,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertTrue(
            resolveHomeTopSearchLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    isHomeSearchLiquidGlassEnabled = false,
                    androidNativeLiquidGlassEnabled = true
                ),
                uiPreset = UiPreset.MD3
            )
        )
        assertFalse(
            resolveHomeTopSearchLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    isHomeSearchLiquidGlassEnabled = true,
                    androidNativeLiquidGlassEnabled = false
                ),
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `md3 top chrome keeps status bar blur slab while local panel stays blurred`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopContinuousSlabRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopLocalChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3
            )
        )
    }

    @Test
    fun `continuous top slab covers pinned tab and visible search area`() {
        assertEquals(
            120.dp,
            resolveHomeTopContinuousSlabHeight(
                statusBarHeight = 24.dp,
                searchBarHeight = 52.dp,
                tabRowHeight = 44.dp,
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3
            )
        )
        assertEquals(
            120.dp,
            resolveHomeTopContinuousSlabHeight(
                statusBarHeight = 24.dp,
                searchBarHeight = 52.dp,
                tabRowHeight = 44.dp,
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `collapsed top slab keeps only status bar height when tabs are hidden`() {
        assertEquals(
            24.dp,
            resolveHomeTopContinuousSlabHeight(
                statusBarHeight = 24.dp,
                searchBarHeight = 0.dp,
                tabRowHeight = 0.dp,
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                hasVisibleTopContent = false
            )
        )
    }

    @Test
    fun `unified top panel chrome is skipped when search and tabs are fully collapsed`() {
        assertFalse(
            shouldRenderHomeTopUnifiedPanelChrome(
                searchHeightDp = 0f,
                tabHeightDp = 0f,
                integratedCollapsedTopBar = false
            )
        )
        assertTrue(
            shouldRenderHomeTopUnifiedPanelChrome(
                searchHeightDp = 8f,
                tabHeightDp = 0f,
                integratedCollapsedTopBar = false
            )
        )
        assertTrue(
            shouldRenderHomeTopUnifiedPanelChrome(
                searchHeightDp = 0f,
                tabHeightDp = 0f,
                integratedCollapsedTopBar = true
            )
        )
    }

    @Test
    fun `md3 continuous top slab uses a non transparent surface fill to avoid black seams`() {
        assertTrue(
            resolveHomeTopContinuousSlabSurfaceColor(
                baseColor = Color.White.copy(alpha = 0.72f),
                blurAlpha = 0.64f,
                uiPreset = UiPreset.MD3,
                renderMode = HomeTopChromeRenderMode.BLUR
            ).alpha > 0.6f
        )
        assertEquals(
            0f,
            resolveHomeTopContinuousSlabSurfaceColor(
                baseColor = Color.White.copy(alpha = 0.72f),
                blurAlpha = 0.64f,
                uiPreset = UiPreset.IOS,
                renderMode = HomeTopChromeRenderMode.BLUR
            ).alpha,
            0.0001f
        )
    }

    @Test
    fun `ios unified home header keeps outer panel transparent while inner controls own blur`() {
        assertEquals(
            HomeTopChromeRenderMode.PLAIN,
            resolveHomeTopPanelChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.IOS,
                useUnifiedPanel = true
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopContinuousSlabRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `ios unified home header keeps search blur when blur is enabled`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopSearchChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.IOS,
                useUnifiedPanel = true
            )
        )
        assertTrue(resolveHomeTopUnifiedSearchContainerColor(isLightMode = true).alpha < 0.4f)
        assertTrue(resolveHomeTopUnifiedSearchBorderColor(isLightMode = true).alpha < 0.25f)
    }

    @Test
    fun `dark unified search pill uses dark surface instead of white lift`() {
        val darkContainer = resolveHomeTopUnifiedSearchContainerColor(isLightMode = false)

        assertEquals(Color.Black.red, darkContainer.red)
        assertEquals(Color.Black.green, darkContainer.green)
        assertEquals(Color.Black.blue, darkContainer.blue)
        assertTrue(darkContainer.alpha >= 0.30f)
        assertTrue(resolveHomeTopSearchDarkWhiteOverlayMultiplier(isLightMode = false) < 0.5f)
        assertEquals(0.86f, resolveHomeTopSearchDarkWhiteOverlayMultiplier(isLightMode = true), 0.0001f)
    }

    @Test
    fun `plain top search keeps an opaque readable surface when liquid glass is disabled`() {
        val darkPlain = resolveHomeTopUnifiedSearchContainerColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.PLAIN
        )
        val darkBlur = resolveHomeTopUnifiedSearchContainerColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val lightPlainBorder = resolveHomeTopUnifiedSearchBorderColor(
            isLightMode = true,
            renderMode = HomeTopChromeRenderMode.PLAIN
        )

        assertEquals(Color.Black.red, darkPlain.red)
        assertTrue(darkPlain.alpha >= 0.40f)
        assertTrue(darkPlain.alpha > darkBlur.alpha)
        assertTrue(lightPlainBorder.alpha > 0f)
    }

    @Test
    fun `legacy ios search highlight is fully disabled after migration`() {
        // 2B 迁移：legacy 高亮仅属于 iOS 的 MOVING_CAPSULE 分支，已随单向迁移删除。
        assertFalse(
            shouldDrawHomeTopSearchLegacyHighlight(
                uiPreset = UiPreset.IOS,
                useUnifiedTopPanel = false,
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                refractionOverlayAlpha = 1f
            )
        )
        assertFalse(
            shouldDrawHomeTopSearchLegacyHighlight(
                uiPreset = UiPreset.IOS,
                useUnifiedTopPanel = false,
                renderMode = HomeTopChromeRenderMode.BLUR,
                refractionOverlayAlpha = 0f
            )
        )
    }

    @Test
    fun `plain top edge controls keep a contrasting visible container`() {
        val darkPlain = resolveHomeTopEdgeControlContainerColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.PLAIN
        )
        val darkBlur = resolveHomeTopEdgeControlContainerColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val darkLiquid = resolveHomeTopEdgeControlContainerColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
        )
        val darkBlurBorder = resolveHomeTopEdgeControlBorderColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val darkPlainBorder = resolveHomeTopEdgeControlBorderColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.PLAIN
        )

        assertEquals(Color.Black.red, darkPlain.red)
        assertTrue(darkPlain.alpha >= 0.38f)
        assertTrue(darkBlur.alpha >= 0.30f)
        assertTrue(darkPlain.alpha > darkLiquid.alpha)
        assertTrue(darkBlur.alpha > darkLiquid.alpha)
        assertTrue(darkBlurBorder.alpha > 0f)
        assertTrue(darkPlainBorder.alpha > 0f)
    }

    @Test
    fun `md3 unified home header uses flat edge-to-edge panel`() {
        assertEquals(
            0.dp,
            resolveHomeTopUnifiedPanelCornerRadius(
                uiPreset = UiPreset.MD3,
                collapsedIntoStatusBar = false
            )
        )
        assertEquals(
            0.dp,
            resolveHomeTopUnifiedPanelCornerRadius(
                uiPreset = UiPreset.MD3,
                collapsedIntoStatusBar = true
            )
        )
    }

    @Test
    fun `md3 unified home header also keeps search blur on the search pill`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopSearchChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.BLUR,
                uiPreset = UiPreset.MD3,
                useUnifiedPanel = true
            )
        )
    }

    @Test
    fun `miuix top chrome follows global liquid glass when enabled`() {
        assertEquals(
            TopTabMaterialMode.LIQUID_GLASS,
            resolveHomeTopChromeMaterialMode(
                isHeaderBlurEnabled = true,
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = true,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }

    @Test
    fun `unified home header keeps liquid search chrome when top shell uses liquid glass`() {
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopSearchChromeRenderMode(
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                uiPreset = UiPreset.MD3,
                useUnifiedPanel = true
            )
        )
    }

    @Test
    fun `liquid glass top chrome prefers captured backdrop rendering`() {
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = true,
                hasBackdrop = true,
                hasHazeState = true
            )
        )
    }

    @Test
    fun `liquid glass top chrome falls back to haze liquid glass when backdrop is unavailable`() {
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = true,
                hasBackdrop = false,
                hasHazeState = true,
                allowHazeLiquidGlassFallback = true
            )
        )
    }

    @Test
    fun `android 16 liquid glass top chrome falls back to blur when backdrop is unavailable`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = true,
                hasBackdrop = false,
                hasHazeState = true,
                allowHazeLiquidGlassFallback = false
            )
        )
    }

    @Test
    fun `unsupported liquid glass top chrome falls back to blur`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = false,
                hasBackdrop = true,
                hasHazeState = true,
                allowHazeLiquidGlassFallback = true
            )
        )
    }

    @Test
    fun `circle top chrome shape resolves to a lens safe rounded shape`() {
        assertTrue(resolveHomeTopChromeLensShape(CircleShape) is CornerBasedShape)
    }

    @Test
    fun `rectangle top chrome shape resolves to a lens safe rounded shape`() {
        assertTrue(resolveHomeTopChromeLensShape(RectangleShape) is CornerBasedShape)
    }

    @Test
    fun `rounded top chrome shape is preserved for lens rendering`() {
        val shape = RoundedCornerShape(10)

        assertEquals(shape, resolveHomeTopChromeLensShape(shape))
    }

    @Test
    fun `continuous top slab uses subtle bottom corners`() {
        assertEquals(
            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            resolveHomeTopContinuousSlabShape()
        )
        assertEquals(
            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            resolveHomeTopContinuousSlabShape(UiPreset.MD3)
        )
    }

    @Test
    fun `liquid glass readability layer stays lighter than blur`() {
        val liquidAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        val blurAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.BLUR)

        assertTrue(liquidAlpha > 0.24f)
        assertTrue(liquidAlpha < blurAlpha)
    }

    @Test
    fun `blur readability layer stays stronger than plain`() {
        val blurAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.BLUR)
        val plainAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.PLAIN)

        assertTrue(blurAlpha > plainAlpha)
    }

    @Test
    fun `top search content alpha is strengthened for readability`() {
        assertTrue(
            resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP) >= 0.88f
        )
        assertTrue(
            resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.BLUR) >
                resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.PLAIN)
        )
    }

    @Test
    fun `top action icon alpha is strengthened for readability`() {
        assertTrue(
            resolveHomeTopActionIconAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP) >= 0.86f
        )
    }

    @Test
    fun `top tab content underlay stays lighter than main readability layer`() {
        val underlay = resolveHomeTopTabContentUnderlayAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        val readability = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)

        assertTrue(underlay < readability)
        assertTrue(underlay > 0f)
    }

    @Test
    fun `top tab unselected alpha is strengthened for readability`() {
        assertTrue(resolveTopTabUnselectedAlpha() > 0.65f)
    }

    @Test
    fun `light mode top search content uses black like bottom bar`() {
        assertEquals(
            Color.Black,
            resolveHomeTopForegroundColor(isLightMode = true)
        )
    }

    @Test
    fun `light mode top action icons use black like bottom bar`() {
        assertEquals(
            Color.Black,
            resolveHomeTopForegroundColor(isLightMode = true)
        )
    }

    @Test
    fun `light mode top tab unselected color keeps softer bottom bar style contrast`() {
        assertEquals(
            Color.Black.copy(alpha = 0.72f),
            resolveTopTabUnselectedColor(isLightMode = true)
        )
    }

    @Test
    fun `dark mode top foreground uses bright text`() {
        assertEquals(
            Color.White.copy(alpha = 0.92f),
            resolveHomeTopForegroundColor(isLightMode = false)
        )
    }

    @Test
    fun `dark mode top tab underlay uses dark tint instead of white`() {
        val underlay = resolveHomeTopInnerUnderlayColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
        )

        assertNotEquals(Color.White, underlay)
        assertTrue(underlay.red < 0.2f && underlay.green < 0.2f && underlay.blue < 0.2f)
        assertTrue(underlay.alpha > 0f)
    }

    @Test
    fun `dark mode top glass accents are dimmed`() {
        val base = HomeGlassResolvedColors(
            containerColor = Color.White.copy(alpha = 0.28f),
            borderColor = Color.White.copy(alpha = 0.18f),
            highlightColor = Color.White.copy(alpha = 0.20f)
        )

        val tuned = tuneHomeTopGlassColors(
            colors = base,
            isLightMode = false,
            emphasized = true
        )

        assertTrue(tuned.borderColor.alpha < base.borderColor.alpha)
        assertTrue(tuned.highlightColor.alpha < base.highlightColor.alpha)
    }

    @Test
    fun `liquid glass header uses same base alpha as bottom bar`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = true,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.10f, alpha, 0.0001f)
    }

    @Test
    fun `blur disabled header falls back to opaque`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = true,
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(1f, alpha, 0.0001f)
    }

    @Test
    fun `non-glass header keeps tuned blur-based alpha`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THICK
        )
        val bottomBarAlpha = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THICK
        ).alpha

        // Color packing can quantize alpha slightly; keep the policy values aligned.
        assertEquals(bottomBarAlpha, alpha, 0.01f)
    }

    @Test
    fun `top blur surface type uses header budget in blur mode`() {
        assertEquals(
            BlurSurfaceType.HEADER,
            resolveHomeTopBlurSurfaceType(HomeTopChromeRenderMode.BLUR)
        )
    }

    @Test
    fun `blur mode uses continuous top slab instead of plain background`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopContinuousSlabRenderMode(HomeTopChromeRenderMode.BLUR)
        )
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopContinuousSlabRenderMode(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        )
    }

    @Test
    fun `liquid continuous slab samples backdrop without drawing another tinted panel`() {
        assertEquals(
            Color.Transparent,
            resolveHomeTopContinuousSlabSurfaceColor(
                baseColor = Color.White.copy(alpha = 0.72f),
                blurAlpha = 0.64f,
                renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            ),
        )
    }

    @Test
    fun `blur mode keeps local search and tabs plain to avoid stacked blur lag`() {
        assertEquals(
            HomeTopChromeRenderMode.PLAIN,
            resolveHomeTopLocalChromeRenderMode(HomeTopChromeRenderMode.BLUR)
        )
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopLocalChromeRenderMode(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        )
    }

    @Test
    fun `blur mode ignores scroll and transition budget jitter`() {
        val blurPolicy = resolveHomeTopChromeMotionPolicy(
            renderMode = HomeTopChromeRenderMode.BLUR,
            isScrolling = true,
            isTransitionRunning = true
        )
        val liquidPolicy = resolveHomeTopChromeMotionPolicy(
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            isScrolling = true,
            isTransitionRunning = true
        )

        assertFalse(blurPolicy.isScrolling)
        assertFalse(blurPolicy.isTransitionRunning)
        assertTrue(liquidPolicy.isScrolling)
        assertTrue(liquidPolicy.isTransitionRunning)
    }

    @Test
    fun `top tab chrome ignores vertical scroll motion to avoid stop flicker`() {
        val liquidPolicy = resolveHomeTopTabChromeMotionPolicy(
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            isScrolling = true,
            isTransitionRunning = true
        )
        val hazePolicy = resolveHomeTopTabChromeMotionPolicy(
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            isScrolling = true,
            isTransitionRunning = false
        )

        assertFalse(liquidPolicy.isScrolling)
        assertFalse(liquidPolicy.isTransitionRunning)
        assertFalse(hazePolicy.isScrolling)
        assertFalse(hazePolicy.isTransitionRunning)
    }

    @Test
    fun `top blur container color reuses bottom bar surface color rule`() {
        val colors = resolveHomeTopBlurContainerColors(
            colors = HomeGlassResolvedColors(
                containerColor = Color.Transparent,
                borderColor = Color.White.copy(alpha = 0.12f),
                highlightColor = Color.White.copy(alpha = 0.1f)
            ),
            surfaceColor = Color.Black,
            blurIntensity = BlurIntensity.APPLE_DOCK
        )

        assertEquals(
            resolveBottomBarSurfaceColor(
                surfaceColor = Color.Black,
                blurEnabled = true,
                blurIntensity = BlurIntensity.APPLE_DOCK
            ),
            colors.containerColor
        )
    }

    @Test
    fun `plain dark header keeps dark surface when blur and glass are disabled`() {
        val surfaceColor = Color(0xFF121212)
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = false,
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(surfaceColor, surfaceColor.copy(alpha = alpha))
    }

    @Test
    fun `docked blur top tabs use same overlay alpha as blur chrome container`() {
        assertEquals(
            0.4f,
            resolveHomeTopTabOverlayAlpha(
                materialMode = TopTabMaterialMode.BLUR,
                isTabFloating = false,
                containerAlpha = 0.4f
            ),
            0.0001f
        )
    }

    @Test
    fun `detached top tab dock uses inverted surfaces in light and dark mode`() {
        val lightDock = resolveHomeTopDetachedTabDockSurfaceColor(
            isLightMode = true,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val darkDock = resolveHomeTopDetachedTabDockSurfaceColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.BLUR
        )
        val darkSelectedCapsule = resolveIosTopTabCapsuleContainerColor(
            isDarkTheme = true,
            selectionFraction = 1f
        )

        val bottomIndicatorColor = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = true)

        assertTrue(lightDock.luminance() > 0.8f)
        assertTrue(darkDock.luminance() < 0.1f)
        assertEquals(bottomIndicatorColor.red, darkSelectedCapsule.red, 0.001f)
        assertEquals(bottomIndicatorColor.green, darkSelectedCapsule.green, 0.001f)
        assertEquals(bottomIndicatorColor.blue, darkSelectedCapsule.blue, 0.001f)
        assertEquals(0.28f, darkSelectedCapsule.alpha, 0.002f)
    }

    @Test
    fun `top tab secondary blur enabled only in static state`() {
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = false,
                isTransitionRunning = false
            )
        )
    }

    @Test
    fun `embedded top tabs do not duplicate the unified header haze pass`() {
        assertFalse(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = false,
                isTransitionRunning = false,
                isEmbeddedInUnifiedPanel = true
            )
        )
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopTabDockChromeRenderMode(
                detachedTopTabDock = false,
                localTabChromeRenderMode = HomeTopChromeRenderMode.BLUR,
                hasHazeState = true,
            )
        )
        assertFalse(
            shouldApplyHomeTopTabDockHaze(
                embeddedInUnifiedPanel = true,
                continuousSlabRenderMode = HomeTopChromeRenderMode.BLUR,
            )
        )
    }

    @Test
    fun `detached top tab dock keeps its own haze without a parent blur slab`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopTabDockChromeRenderMode(
                detachedTopTabDock = true,
                localTabChromeRenderMode = HomeTopChromeRenderMode.PLAIN,
                hasHazeState = true,
            )
        )
        assertTrue(
            shouldApplyHomeTopTabDockHaze(
                embeddedInUnifiedPanel = false,
                continuousSlabRenderMode = HomeTopChromeRenderMode.PLAIN,
            )
        )
    }

    @Test
    fun `liquid glass top tab secondary blur disabled during motion to reduce duplicate blur passes`() {
        assertFalse(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.LIQUID_GLASS,
                isScrolling = true,
                isTransitionRunning = false
            )
        )
        assertFalse(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.LIQUID_GLASS,
                isScrolling = false,
                isTransitionRunning = true
            )
        )
    }

    @Test
    fun `blur mode keeps top tab secondary blur during motion`() {
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = true,
                isTransitionRunning = false
            )
        )
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = false,
                isTransitionRunning = true
            )
        )
    }

    @Test
    fun `floating top tabs use tighter spacing beneath search`() {
        assertEquals(1f, resolveHomeTopTabVerticalPaddingDp(isTabFloating = true), 0.0001f)
        assertEquals(-2f, resolveHomeTopTabYOffsetDp(isTabFloating = true), 0.0001f)
    }

    @Test
    fun `top tab chrome padding never passes negative values into compose`() {
        assertEquals(0.dp, resolveNonNegativeHomeTopPadding((-4).dp))
        assertEquals(0.dp, resolveNonNegativeHomeTopPadding(0.dp))
        assertEquals(8.dp, resolveNonNegativeHomeTopPadding(8.dp))
    }

    @Test
    fun `docked top tabs keep neutral spacing beneath search`() {
        assertEquals(0f, resolveHomeTopTabVerticalPaddingDp(isTabFloating = false), 0.0001f)
        assertEquals(0f, resolveHomeTopTabYOffsetDp(isTabFloating = false), 0.0001f)
    }

    @Test
    fun `floating top tabs no longer use highlighted border`() {
        assertEquals(
            0f,
            resolveHomeHeaderTabBorderAlpha(
                isTabFloating = true,
                isTabGlassEnabled = true
            ),
            0.0001f
        )
        assertEquals(
            0f,
            resolveHomeHeaderTabBorderAlpha(
                isTabFloating = true,
                isTabGlassEnabled = false
            ),
            0.0001f
        )
    }

    @Test
    fun `skin top tab content color follows skin atmosphere brightness`() {
        assertEquals(
            Color.White.copy(alpha = 0.98f),
            resolveHomeSkinTopTabContentColor(Color(0xFF2E2A1E))
        )
        assertEquals(
            Color.White.copy(alpha = 0.98f),
            resolveHomeSkinTopTabContentColor(Color(0xFF778675))
        )
        assertEquals(
            Color(0xFF111820).copy(alpha = 0.96f),
            resolveHomeSkinTopTabContentColor(Color(0xFFE4F6FF))
        )
    }

}
