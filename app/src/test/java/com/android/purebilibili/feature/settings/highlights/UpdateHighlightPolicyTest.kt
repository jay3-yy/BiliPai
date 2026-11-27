package com.android.purebilibili.feature.settings.highlights

import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.SeedPaletteStyle
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.materialkolor.PaletteStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateHighlightPolicyTest {
    private val feature = updateHighlightCatalog.first { it.id == "cmf_spec_2026" }

    @Test
    fun newInstallationEstablishesBaselineWithoutInvitations() {
        val result = prepareUpdateHighlights(UpdateHighlightHistory(), feature.introducedVersionCode, false)
        assertTrue(result.pendingIds.isEmpty())
        assertTrue(feature.id in result.handledIds)
        assertEquals(feature.introducedVersionCode, result.lastVersionCode)
    }

    @Test
    fun existingInstallCanReceiveTheFirstReleaseAndReentryDoesNotRequeueHandledItems() {
        val initial = prepareUpdateHighlights(UpdateHighlightHistory(), feature.introducedVersionCode, true)
        assertEquals(setOf(feature.id), initial.pendingIds)
        val dismissed = initial.copy(pendingIds = emptySet(), handledIds = setOf(feature.id))
        assertEquals(dismissed, prepareUpdateHighlights(dismissed, feature.introducedVersionCode, true))
        assertTrue(prepareUpdateHighlights(dismissed, feature.introducedVersionCode + 1, true).pendingIds.isEmpty())
    }

    @Test
    fun skippedVersionsMergeNewFeaturesAndDowngradesRetainTheHighWaterMark() {
        val catalog = (100..104).map { UpdateHighlight("feature_$it", it, "Title", "Description") }
        val result = prepareUpdateHighlights(UpdateHighlightHistory(lastVersionCode = 99), 104, true, catalog)
        assertEquals(catalog.map { it.id }.toSet(), result.pendingIds)
        assertEquals(result, prepareUpdateHighlights(result, 102, true, catalog))
        assertEquals(listOf(catalog.last()), latestReleaseHighlights(104, catalog))
        assertEquals(listOf(catalog[2]), latestReleaseHighlights(102, catalog))
    }

    @Test
    fun eligibilityNeverChangesTheUsersStyleOrColorSource() {
        val base = AppThemeSettings(uiStyle = AppUiStyle.MATERIAL3, md3ColorSource = Md3ColorSource.CUSTOM)
        assertTrue(canPreviewUpdateHighlight(feature, base))
        assertFalse(canPreviewUpdateHighlight(feature, base.copy(uiStyle = AppUiStyle.MIUIX)))
        assertFalse(canPreviewUpdateHighlight(feature, base.copy(md3ColorSource = Md3ColorSource.FOLLOW_WALLPAPER)))
        assertTrue(isUpdateHighlightAlreadyEnabled(feature, base.copy(colorStyle = PaletteStyle.Cmf())))
        assertFalse(isUpdateHighlightAlreadyEnabled(feature, base.copy(
            uiStyle = AppUiStyle.MIUIX, colorStyle = PaletteStyle.Cmf(),
        )))
        assertFalse(isUpdateHighlightAlreadyEnabled(feature, base.copy(
            colorStyle = PaletteStyle.Cmf(), seedPaletteStyle = SeedPaletteStyle.CandyPop,
        )))
    }

    @Test
    fun stableFeatureIdsAreUniqueAndReleaseOverviewIsBounded() {
        assertEquals(updateHighlightCatalog.size, updateHighlightCatalog.map { it.id }.toSet().size)
        val catalog = (0..12).map { UpdateHighlight("item_$it", 486, "Title", "Description") }
        assertEquals(MAX_RELEASE_HIGHLIGHTS, latestReleaseHighlights(486, catalog).size)
    }

    @Test
    fun newerReleaseReplacesPreviousHighlightsInAutomaticAndManualPreview() {
        val baseSettings = AppThemeSettings(uiStyle = AppUiStyle.MIUIX)
        val currentHighlights = latestReleaseHighlights(617)
        assertEquals(
            listOf(
                "donate_qr_refresh_617",
                "cdn_parallel_chunk_617",
                "offline_cache_refresh_617",
                "custom_icon_zero_diff_617",
                "listen_ambient_winddown_617",
                "personal_recap_keepsake_617",
                "mealtime_gacha_watchlater_617",
                "custom_push_nearby_617",
                "appearance_scene_recipes_617",
            ),
            currentHighlights.map { it.id },
        )

        val upgraded = prepareUpdateHighlights(
            history = UpdateHighlightHistory(lastVersionCode = 616),
            currentVersionCode = 617,
            isExistingInstall = true,
        )
        val auto617 = resolveAutomaticReleaseHighlights(617, upgraded.pendingIds, baseSettings)
        assertEquals(currentHighlights.map { it.id }, auto617.map { it.id })

        val nextReleaseCatalog = updateHighlightCatalog + UpdateHighlight(
            id = "next_release_618",
            introducedVersionCode = 618,
            title = "下一版本新能力",
            description = "替换上一版本的更新要点。",
            action = UpdateHighlightAction.OPEN_SETTINGS,
        )
        val upgradedNext = prepareUpdateHighlights(
            history = upgraded,
            currentVersionCode = 618,
            isExistingInstall = true,
            catalog = nextReleaseCatalog,
        )
        val auto618 = resolveAutomaticReleaseHighlights(618, upgradedNext.pendingIds, baseSettings, nextReleaseCatalog)
        assertEquals(listOf("next_release_618"), auto618.map { it.id })
    }

    @Test
    fun automaticHighlightsRecognizeNavigation3MainHostHomeTabAndRejectOtherDestinations() {
        assertTrue(
            isHomeRouteForAutomaticHighlights(
                isMainHostTop = true,
                isHomeBottomTab = true,
                currentRoute = "main_host",
                homeRoute = "home",
            )
        )
        assertFalse(
            isHomeRouteForAutomaticHighlights(
                isMainHostTop = true,
                isHomeBottomTab = false,
                currentRoute = "main_host",
                homeRoute = "home",
            )
        )
        assertFalse(
            isHomeRouteForAutomaticHighlights(
                isMainHostTop = false,
                isHomeBottomTab = true,
                currentRoute = "settings",
                homeRoute = "home",
            )
        )
        assertTrue(
            isHomeRouteForAutomaticHighlights(
                isMainHostTop = false,
                isHomeBottomTab = false,
                currentRoute = "home",
                homeRoute = "home",
            )
        )
    }

    @Test
    fun release622HighlightsExcludePinnedDonateAndUseDistinctSemanticIcons() {
        val highlights = latestReleaseHighlights(622)
        assertEquals(6, highlights.size)
        assertTrue(highlights.none { it.action == UpdateHighlightAction.OPEN_DONATE })
        val icons = highlights.map { it.icon }
        assertTrue(icons.all { it != null })
        assertEquals(highlights.size, icons.toSet().size)
    }

    @Test
    fun release623HighlightsExcludePinnedDonateAndUseDistinctSemanticIcons() {
        val highlights = latestReleaseHighlights(623)
        assertEquals(4, highlights.size)
        assertTrue(highlights.none { it.action == UpdateHighlightAction.OPEN_DONATE })
        val icons = highlights.map { it.icon }
        assertTrue(icons.all { it != null })
        assertEquals(highlights.size, icons.toSet().size)
    }

    @Test
    fun release624HighlightsExcludePinnedDonateAndUseDistinctSemanticIcons() {
        val highlights = latestReleaseHighlights(624)
        assertEquals(6, highlights.size)
        assertTrue(highlights.none { it.action == UpdateHighlightAction.OPEN_DONATE })
        val icons = highlights.map { it.icon }
        assertTrue(icons.all { it != null })
        assertEquals(highlights.size, icons.toSet().size)
    }

    @Test
    fun formatDisplayVersionNameStripsBuildVariantSuffix() {
        assertEquals("0.10.6", formatDisplayVersionName("0.10.6-debug"))
        assertEquals("0.10.6", formatDisplayVersionName("0.10.6-dev"))
        assertEquals("0.10.6", formatDisplayVersionName("0.10.6"))
    }
}
