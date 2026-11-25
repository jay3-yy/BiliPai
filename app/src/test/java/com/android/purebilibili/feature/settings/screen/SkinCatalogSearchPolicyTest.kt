package com.android.purebilibili.feature.settings.screen

import com.android.purebilibili.core.plugin.skin.SkinCatalog
import com.android.purebilibili.core.plugin.skin.SkinCatalogCapabilities
import com.android.purebilibili.core.plugin.skin.SkinCatalogEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class SkinCatalogSearchPolicyTest {

    @Test
    fun searchMatchesDisplayNameAndIdIgnoringCase() {
        val themes = listOf(
            catalogEntry(id = "winter-cloud", name = "冬日云朵"),
            catalogEntry(id = "summer-sea", name = "夏日海岸"),
        )

        assertEquals(
            listOf("winter-cloud"),
            SkinCatalogUiState(catalog = catalog(themes), searchQuery = "云朵")
                .filteredThemes.map { it.id },
        )
        assertEquals(
            listOf("summer-sea"),
            SkinCatalogUiState(catalog = catalog(themes), searchQuery = "SUMMER")
                .filteredThemes.map { it.id },
        )
    }

    @Test
    fun categoryFiltersMatchLightDarkDynamicAndInstalledEntries() {
        val themes = listOf(
            catalogEntry(id = "light-plain", name = "浅色装扮", colorMode = "light"),
            catalogEntry(
                id = "dark-animated",
                name = "深色动效装扮",
                colorMode = "dark",
                capabilities = SkinCatalogCapabilities(animatedIcons = true),
            ),
        )

        assertEquals(
            listOf("light-plain"),
            SkinCatalogUiState(catalog = catalog(themes), filter = SkinCatalogFilter.LIGHT)
                .resolveFilteredThemes(emptySet()).map { it.id },
        )
        assertEquals(
            listOf("dark-animated"),
            SkinCatalogUiState(catalog = catalog(themes), filter = SkinCatalogFilter.DARK)
                .resolveFilteredThemes(emptySet()).map { it.id },
        )
        assertEquals(
            listOf("dark-animated"),
            SkinCatalogUiState(catalog = catalog(themes), filter = SkinCatalogFilter.DYNAMIC_EFFECT)
                .resolveFilteredThemes(emptySet()).map { it.id },
        )
        assertEquals(
            listOf("light-plain"),
            SkinCatalogUiState(catalog = catalog(themes), filter = SkinCatalogFilter.INSTALLED)
                .resolveFilteredThemes(setOf("浅色装扮")).map { it.id },
        )
    }

    private fun catalog(themes: List<SkinCatalogEntry>) = SkinCatalog(
        catalogVersion = 1,
        sourceRepo = "test/repo",
        sourceBranch = "main",
        themes = themes,
    )

    private fun catalogEntry(
        id: String,
        name: String,
        colorMode: String? = null,
        capabilities: SkinCatalogCapabilities = SkinCatalogCapabilities(),
    ) = SkinCatalogEntry(
        id = id,
        name = name,
        previewUrl = "https://example.com/$id.jpg",
        packageZipUrl = "https://example.com/$id.zip",
        colorMode = colorMode,
        capabilities = capabilities,
    )
}
