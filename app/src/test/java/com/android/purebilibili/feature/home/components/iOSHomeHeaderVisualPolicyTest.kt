package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.HomeTopRightAction
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class iOSHomeHeaderVisualPolicyTest {

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
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt"),
            File("src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt")
        ).first { it.exists() }.readText()
        val topRightButtonSource = source
            .substringAfter("Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap")
            .substringBefore("if (drawTopSearchDivider)")

        assertEquals(0.dp, layout.offsetX)
        assertEquals(0.dp, layout.offsetY)
        assertEquals(9.dp, layout.reservedEndWidth)
        assertEquals(18.dp, layout.minWidth)
        assertEquals(18.dp, layout.minHeight)
        assertEquals(5.dp, layout.horizontalPadding)
        assertEquals(1.dp, layout.verticalPadding)
        assertTrue(topRightButtonSource.contains("resolveHomeTopRightActionSlotWidth"))
        assertTrue(topRightButtonSource.contains(".size(topRightActionButtonSize)"))
        assertTrue(topRightButtonSource.contains("Box("))
        assertTrue(topRightButtonSource.contains(".clip(edgeButtonShape)"))
        assertTrue(topRightButtonSource.contains("topRightUnreadBadgeLayout.offsetX"))
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
    fun `search content export layer stays disabled to avoid scroll flicker`() {
        val active = resolveHomeTopSearchRefractionLayerPolicy(
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            hasBackdrop = true,
            searchRevealFraction = 0.82f,
            isScrolling = true,
            isTransitionRunning = false
        )
        val idle = resolveHomeTopSearchRefractionLayerPolicy(
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            hasBackdrop = true,
            searchRevealFraction = 0.82f,
            isScrolling = false,
            isTransitionRunning = false
        )
        val blur = resolveHomeTopSearchRefractionLayerPolicy(
            renderMode = HomeTopChromeRenderMode.BLUR,
            hasBackdrop = true,
            searchRevealFraction = 0.82f,
            isScrolling = true,
            isTransitionRunning = false
        )

        assertFalse(active.captureContentLayer)
        assertFalse(active.useExportedBackdrop)
        assertEquals(0f, active.overlayAlpha, 0.0001f)
        assertEquals(1f, active.visibleContentAlpha, 0.0001f)
        assertEquals(0f, active.exportTranslationMultiplier, 0.0001f)
        assertFalse(idle.captureContentLayer)
        assertFalse(idle.useExportedBackdrop)
        assertEquals(0f, idle.overlayAlpha, 0.0001f)
        assertEquals(1f, idle.visibleContentAlpha, 0.0001f)
        assertEquals(0f, idle.exportTranslationMultiplier, 0.0001f)
        assertFalse(blur.captureContentLayer)
        assertFalse(blur.useExportedBackdrop)
        assertEquals(0f, blur.overlayAlpha, 0.0001f)
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
    fun `home header trims top chrome heights for better content density`() {
        assertEquals(48.dp, resolveHomeTopSearchBarHeight())
        assertEquals(52.dp, resolveHomeTopSearchBarHeight(UiPreset.MD3))
        assertEquals(62.dp, resolveHomeTopTabRowHeight(isTabFloating = true))
        assertEquals(52.dp, resolveHomeTopTabRowHeight(isTabFloating = true, uiPreset = UiPreset.MD3))
        assertEquals(56.dp, resolveHomeTopTabRowHeight(isTabFloating = false))
        assertEquals(48.dp, resolveHomeTopTabRowHeight(isTabFloating = false, uiPreset = UiPreset.MD3))
    }

    @Test
    fun `md3 home header expands top tab row for icon plus text`() {
        assertEquals(
            60.dp,
            resolveHomeTopTabRowHeight(
                isTabFloating = false,
                uiPreset = UiPreset.MD3,
                labelMode = 0
            )
        )
        assertEquals(
            62.dp,
            resolveHomeTopTabRowHeight(
                isTabFloating = true,
                uiPreset = UiPreset.MD3,
                labelMode = 0
            )
        )
    }

    @Test
    fun `ios home header expands docked top tab row for icon plus text`() {
        assertEquals(
            58.dp,
            resolveHomeTopTabRowHeight(
                isTabFloating = false,
                uiPreset = UiPreset.IOS,
                labelMode = 0
            )
        )
        assertEquals(
            62.dp,
            resolveHomeTopTabRowHeight(
                isTabFloating = true,
                uiPreset = UiPreset.IOS,
                labelMode = 0
            )
        )
    }

    @Test
    fun `header scroll hides search row while keeping top tabs pinned`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -96f,
            searchBarHeightPx = 48f,
            headerCollapseDistancePx = 54f,
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
            headerCollapseDistancePx = 54f,
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
    fun `tiny reverse scroll keeps collapsed search row hidden until reveal threshold is crossed`() {
        val layout = resolveHomeHeaderScrollLayout(
            headerOffsetPx = -44f,
            searchBarHeightPx = 48f,
            headerCollapseDistancePx = 54f,
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
            headerCollapseDistancePx = 63f,
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
            headerCollapseDistancePx = 63f,
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
            100.dp,
            resolveHomeHeaderCollapseDistance(
                searchBarHeight = 48.dp,
                tabRowHeight = 48.dp,
                uiPreset = UiPreset.IOS
            )
        )
        assertEquals(
            115.dp,
            resolveHomeHeaderCollapseDistance(
                searchBarHeight = 52.dp,
                tabRowHeight = 52.dp,
                uiPreset = UiPreset.MD3
            )
        )
    }

    @Test
    fun `home header trims horizontal spacing without cramping controls`() {
        assertEquals(14.dp, resolveHomeTopSearchRowHorizontalPadding())
        assertEquals(16.dp, resolveHomeTopSearchRowHorizontalPadding(UiPreset.MD3))
        assertEquals(44.dp, resolveHomeTopSearchPillHeight())
        assertEquals(44.dp, resolveHomeTopSearchPillHeight(UiPreset.MD3))
        assertEquals(14.dp, resolveHomeTopTabHorizontalPadding(isTabFloating = true))
        assertEquals(10.dp, resolveHomeTopTabHorizontalPadding(isTabFloating = true, uiPreset = UiPreset.MD3))
        assertEquals(4.dp, resolveHomeTopSearchToTabsSpacing())
        assertEquals(6.dp, resolveHomeTopSearchToTabsSpacing(UiPreset.MD3))
    }

    @Test
    fun `home header uses unified panel with embedded tabs for ios and md3`() {
        assertTrue(shouldUseUnifiedHomeTopPanel(UiPreset.IOS))
        assertTrue(shouldUseUnifiedHomeTopPanel(UiPreset.MD3))
        assertFalse(shouldShowUnifiedHomeTopPanelDivider(UiPreset.IOS))
        assertTrue(
            shouldShowUnifiedHomeTopPanelDivider(
                UiPreset.MD3,
                AndroidNativeVariant.MATERIAL3
            )
        )
        assertFalse(
            shouldShowUnifiedHomeTopPanelDivider(
                UiPreset.MD3,
                AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(0.dp, resolveHomeTopUnifiedPanelHorizontalPadding())
        assertEquals(0.dp, resolveHomeTopUnifiedPanelHorizontalPadding(UiPreset.MD3))
        assertEquals(6.dp, resolveHomeTopUnifiedPanelInnerPadding())
        assertEquals(10.dp, resolveHomeTopUnifiedPanelInnerPadding(UiPreset.MD3))
        assertEquals(32.dp, resolveHomeTopUnifiedPanelCornerRadius())
        assertEquals(16.dp, resolveHomeTopUnifiedPanelCornerRadius(UiPreset.MD3))
        assertEquals(
            18.dp,
            resolveHomeTopUnifiedPanelCornerRadius(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(0.dp, resolveHomeTopEmbeddedTabHorizontalPadding())
        assertEquals(0.dp, resolveHomeTopEmbeddedTabHorizontalPadding(UiPreset.MD3))
    }

    @Test
    fun `home top preset style owns whole header spacing for three presets`() {
        val ios = resolveHomeTopPresetStyle(UiPreset.IOS, AndroidNativeVariant.MATERIAL3, labelMode = 2)
        val material3 = resolveHomeTopPresetStyle(UiPreset.MD3, AndroidNativeVariant.MATERIAL3, labelMode = 2)
        val miuix = resolveHomeTopPresetStyle(UiPreset.MD3, AndroidNativeVariant.MIUIX, labelMode = 2)

        assertEquals(48.dp, ios.searchBarHeight)
        assertEquals(52.dp, material3.searchBarHeight)
        assertEquals(50.dp, miuix.searchBarHeight)
        assertEquals(32.dp, ios.unifiedPanelCornerRadius)
        assertEquals(16.dp, material3.unifiedPanelCornerRadius)
        assertEquals(18.dp, miuix.unifiedPanelCornerRadius)
        assertEquals(4.dp, ios.searchToTabsSpacing)
        assertEquals(6.dp, material3.searchToTabsSpacing)
        assertEquals(4.dp, miuix.searchToTabsSpacing)
        assertFalse(ios.showUnifiedPanelDivider)
        assertTrue(material3.showUnifiedPanelDivider)
        assertFalse(miuix.showUnifiedPanelDivider)
    }

    @Test
    fun `collapsed ios header integrates with status bar instead of floating as a card`() {
        assertTrue(
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
    fun `home list top padding reserves full unified header height without underlapping md3 tabs`() {
        assertEquals(
            169.dp,
            resolveHomeTopReservedListPadding(
                statusBarHeight = 44.dp,
                searchBarHeight = 48.dp,
                tabRowHeight = 56.dp,
                uiPreset = UiPreset.IOS
            )
        )
        assertEquals(
            171.dp,
            resolveHomeTopReservedListPadding(
                statusBarHeight = 44.dp,
                searchBarHeight = 52.dp,
                tabRowHeight = 44.dp,
                uiPreset = UiPreset.MD3
            )
        )
    }

    @Test
    fun `home header uses symmetrical edge controls around search bar`() {
        assertEquals(40.dp, resolveHomeTopAvatarOuterSize())
        assertEquals(40.dp, resolveHomeTopSettingsButtonSize())
        assertEquals(
            resolveHomeTopSettingsButtonSize(),
            resolveHomeTopAvatarInnerSize()
        )
        assertEquals(20.dp, resolveHomeTopSettingsIconSize())
        assertEquals(6.dp, resolveHomeTopEdgeControlGap())
        assertEquals(8.dp, resolveHomeTopEdgeControlGap(UiPreset.MD3))
    }

    @Test
    fun `ios home search pill uses the same capsule shape as bottom bar`() {
        assertEquals(
            resolveSharedBottomBarCapsuleShape(),
            resolveHomeTopSearchContainerShape(UiPreset.IOS)
        )
    }

    @Test
    fun `md3 home header keeps search pill and edge controls less circular`() {
        val searchShape = resolveHomeTopSearchContainerShape(UiPreset.MD3)
        val edgeShape = resolveHomeTopEdgeButtonShape(UiPreset.MD3)

        assertTrue(searchShape is RoundedCornerShape)
        assertTrue(edgeShape is RoundedCornerShape)
        assertNotEquals(CircleShape, edgeShape)
        assertEquals(44.dp, resolveHomeTopSearchPillHeight(UiPreset.MD3))
        assertEquals(16.dp, resolveHomeTopSearchContentHorizontalPadding(UiPreset.MD3))
        assertEquals(8.dp, resolveHomeTopSearchIconTextGap(UiPreset.MD3))
    }

    @Test
    fun `android native miuix home header uses denser search and edge geometry`() {
        val searchShape = resolveHomeTopSearchContainerShape(
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )
        val edgeShape = resolveHomeTopEdgeButtonShape(
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )

        assertTrue(searchShape is RoundedCornerShape)
        assertTrue(edgeShape is RoundedCornerShape)
        assertEquals(
            50.dp,
            resolveHomeTopSearchBarHeight(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            44.dp,
            resolveHomeTopSearchPillHeight(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            14.dp,
            resolveHomeTopSearchContentHorizontalPadding(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            8.dp,
            resolveHomeTopSearchIconTextGap(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            7.dp,
            resolveHomeTopEdgeControlGap(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }

    @Test
    fun `android native miuix home header keeps unified panel compact with softer radius`() {
        assertEquals(
            9.dp,
            resolveHomeTopUnifiedPanelInnerPadding(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            18.dp,
            resolveHomeTopUnifiedPanelCornerRadius(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            4.dp,
            resolveHomeTopSearchToTabsSpacing(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            109.dp,
            resolveHomeHeaderCollapseDistance(
                searchBarHeight = 50.dp,
                tabRowHeight = 50.dp,
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
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
    fun `top chrome uses blur when liquid glass was enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
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
    fun `top chrome uses blur from linked bottom bar when liquid glass was enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
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
    fun `home top md3 shell keeps liquid glass off by default`() {
        val appearance = resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = HomeSettings(),
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MATERIAL3
        )

        assertTrue(appearance.isFloating)
        assertTrue(appearance.blurEnabled)
        assertFalse(appearance.liquidGlassEnabled)
    }

    @Test
    fun `home top respects disabled bottom bar liquid glass`() {
        val appearance = resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = HomeSettings(isBottomBarLiquidGlassEnabled = false),
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )

        assertTrue(appearance.isFloating)
        assertTrue(appearance.blurEnabled)
        assertFalse(appearance.liquidGlassEnabled)
    }

    @Test
    fun `top chrome liquid glass setting is ignored after top liquid removal`() {
        assertFalse(
            resolveHomeTopChromeLiquidGlassEnabled(
                homeSettings = HomeSettings(
                    isTopBarLiquidGlassEnabled = false,
                    isBottomBarLiquidGlassEnabled = true,
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
                    androidNativeLiquidGlassEnabled = true
                ),
                uiPreset = UiPreset.MD3
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
    fun `md3 unified home header uses subtle outer panel rounding`() {
        assertEquals(
            16.dp,
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
    fun `miuix top chrome keeps blur when global liquid glass is enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
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

        assertEquals(bottomBarAlpha, alpha, 0.0001f)
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
            HomeTopChromeRenderMode.PLAIN,
            resolveHomeTopContinuousSlabRenderMode(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
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
        assertEquals(2f, resolveHomeTopTabVerticalPaddingDp(isTabFloating = true), 0.0001f)
        assertEquals(-4f, resolveHomeTopTabYOffsetDp(isTabFloating = true), 0.0001f)
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
    fun `home header consumes skin atmosphere and search tint`() {
        val homeScreenSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val headerSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt")

        assertTrue(homeScreenSource.contains("uiSkinDecoration = homeUiSkinDecoration"))
        assertTrue(headerSource.contains("uiSkinDecoration: HomeUiSkinDecoration? = null"))
        assertFalse(headerSource.contains("HomeSkinAtmosphere("))
        assertFalse(headerSource.contains("resolveHomeSkinAtmospherePinnedHeight("))
        assertFalse(headerSource.contains("modifier = Modifier.matchParentSize()"))
        assertTrue(headerSource.contains("skinTint = uiSkinDecoration?.searchCapsuleTint"))
        assertTrue(headerSource.contains("uiSkinDecoration?.topAtmosphereTint"))
    }

    @Test
    fun `home header skin search tint is applied as capsule surface not inner padded child`() {
        val headerSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt")
        val searchCapsuleSource = headerSource
            .substringAfter(".height(resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant))")
            .substringBefore("val searchFieldContent")

        assertTrue(searchCapsuleSource.contains("val skinSearchSurfaceColor = resolveHomeSkinSearchSurfaceColor("))
        assertTrue(searchCapsuleSource.contains("surfaceColor = skinSearchSurfaceColor"))
        assertFalse(searchCapsuleSource.contains(".matchParentSize()\n                                                .background("))
        assertFalse(searchCapsuleSource.contains("uiSkinDecoration.searchCapsuleTint.copy(alpha = 0.22f)"))
    }

    @Test
    fun `home header skin top tabs keep host readability and only pass sticker icons`() {
        val headerSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt")
        val topBarSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/TopBar.kt")

        assertTrue(headerSource.contains("val shouldUseSkinPlainTopTabs = shouldUseHomeSkinPlainTopTabs(uiSkinDecoration)"))
        assertTrue(headerSource.contains("skinPlainStyle = shouldUseSkinPlainTopTabs"))
        assertTrue(headerSource.contains("topTabSkinIconPaths = uiSkinDecoration?.topTabSkinIconPaths.orEmpty()"))
        assertTrue(headerSource.contains("partitionSkinIconPath = uiSkinDecoration?.topTabPartitionIconPath()"))
        assertFalse(headerSource.contains("val topTabBackgroundImagePath = uiSkinDecoration?.topTabBackgroundImagePath"))
        assertFalse(headerSource.contains("model = File(topTabBackgroundImagePath)"))
        assertFalse(headerSource.contains("val tabRowHeightDp = if (shouldUseSkinPlainTopTabs)"))
        assertTrue(topBarSource.contains("val effectiveRenderer = if (skinPlainStyle) HomeTopTabRenderer.MD3 else renderer"))
        assertTrue(topBarSource.contains("if (!hasSkinStickerIcons && !skinPlainStyle && presetStyle.renderer == HomeTopTabRenderer.MIUIX)"))
    }

    @Test
    fun `home screen passes android native variant into top reserved padding`() {
        val homeScreenSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val reservedPaddingCall = homeScreenSource
            .substringAfter("val listTopPadding = resolveHomeTopReservedListPadding(")
            .substringBefore(")")

        assertTrue(reservedPaddingCall.contains("androidNativeVariant = androidNativeVariant"))
    }

    @Test
    fun `home header settings button uses preset aware sizing`() {
        val headerSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/iOSHomeHeader.kt")

        assertTrue(headerSource.contains("val topRightActionButtonSize = resolveHomeTopSettingsButtonSize(uiPreset, androidNativeVariant)"))
        assertTrue(headerSource.contains(".size(topRightActionButtonSize)"))
        assertTrue(headerSource.contains("modifier = Modifier.size(resolveHomeTopSettingsIconSize(uiPreset, androidNativeVariant))"))
        assertFalse(headerSource.contains(".size(resolveHomeTopSettingsButtonSize())"))
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

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
