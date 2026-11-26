package com.android.purebilibili.feature.settings.screen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.settings.SettingsSearchHistorySection
import com.android.purebilibili.feature.settings.SettingsSearchQuickTopicsSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.animation.LocalAppNavigationEntranceReady
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.SettingsRootCategory
import com.android.purebilibili.feature.settings.SettingsRootCategoryEntranceSection
import com.android.purebilibili.feature.settings.SettingsSearchBarSection
import com.android.purebilibili.feature.settings.useSettingsSearchContentBackdrop
import com.android.purebilibili.feature.settings.SettingsSearchEmptyState
import com.android.purebilibili.feature.settings.PROCESS_MEMORY_SETTING_ID
import com.android.purebilibili.feature.settings.SettingsSearchFocusController
import com.android.purebilibili.feature.settings.SettingsSearchResult
import com.android.purebilibili.feature.settings.SettingsSearchResultRow
import com.android.purebilibili.feature.settings.SettingsViewModel
import com.android.purebilibili.feature.settings.resolveSettingsContentBottomPadding
import com.android.purebilibili.feature.settings.resolveSettingsSearchResults
import com.android.purebilibili.feature.settings.shouldStartSettingsEntrance
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
fun SettingsSearchScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onSearchResultClick: (SettingsSearchResult) -> Unit,
    onCategoryClick: (SettingsRootCategory) -> Unit = {},
    mainHazeState: HazeState? = null,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val nearbyHandoff = com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current
    val searchInput = rememberTextFieldState()
    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val windowSizeClass = LocalWindowSizeClass.current
    val bottomBarVisible = LocalBottomBarVisible.current
    val navigationTransitionRunning =
        !LocalAppNavigationEntranceReady.current.value
    val rootEntranceStartWhen = shouldStartSettingsEntrance(
        entranceEnabled = true,
        navigationTransitionRunning = navigationTransitionRunning,
    )
    val bottomInset = resolveSettingsContentBottomPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        bottomBarVisible = bottomBarVisible,
        isBottomBarFloating = false,
        bottomBarLabelMode = 0,
        isTablet = windowSizeClass.isTablet,
    )
    val contentBottomPadding = maxOf(bottomInset, LocalBottomBarContentPadding.current)

    val pinGlassSearch = useSettingsSearchContentBackdrop()
    val searchHeader: @Composable (Backdrop?) -> Unit = { backdrop ->
        SettingsSearchBarSection(
            state = searchInput,
            onSearch = { viewModel.recordSearchQuery(searchInput.text.toString()) },
            autoFocusEnabled = !navigationTransitionRunning,
            contentBackdrop = backdrop,
        )
    }

    val inlineSearchHeader: @Composable () -> Unit = { searchHeader(null) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_search_results_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = bottomInset,
        scrollHost = SettingsPageScrollHost.External,
        header = inlineSearchHeader.takeUnless { pinGlassSearch },
        glassSearchHeader = searchHeader.takeIf { pinGlassSearch },
    ) {
        val searchQuery by remember(searchInput) {
            derivedStateOf { searchInput.text.toString() }
        }
        val listState = rememberSaveable(searchQuery, saver = LazyListState.Saver) {
            LazyListState()
        }
        val searchResults = remember(searchQuery) {
            resolveSettingsSearchResults(query = searchQuery, maxResults = Int.MAX_VALUE)
        }
        AppLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = settingsScrollContentPadding(
                extraTop = 8.dp,
                extraBottom = contentBottomPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                searchQuery.isBlank() -> {
                    item(key = "hidden_gems") {
                        EntranceGroup(startWhen = rootEntranceStartWhen) {
                            SettingsRootCategoryEntranceSection {
                                com.android.purebilibili.feature.settings.SettingsHiddenGemsSpotlightCard(
                                    onExploreGem = { gem ->
                                        searchInput.setTextAndPlaceCursorAtEnd(gem.searchQuery)
                                        viewModel.recordSearchQuery(gem.searchQuery)
                                    },
                                )
                            }
                        }
                    }
                    item(key = "quick_topics") {
                        EntranceGroup(startWhen = rootEntranceStartWhen) {
                            SettingsRootCategoryEntranceSection {
                                SettingsSearchQuickTopicsSection(
                                    onTopicClick = { topic ->
                                        searchInput.setTextAndPlaceCursorAtEnd(topic)
                                        viewModel.recordSearchQuery(topic)
                                    },
                                )
                            }
                        }
                    }
                    item(key = "history") {
                        EntranceGroup(startWhen = rootEntranceStartWhen) {
                            SettingsRootCategoryEntranceSection {
                                SettingsSearchHistorySection(
                                    history = searchHistory,
                                    onQueryClick = {
                                        searchInput.setTextAndPlaceCursorAtEnd(it)
                                        viewModel.recordSearchQuery(it)
                                    },
                                    onDelete = viewModel::deleteSearchHistory,
                                    onClear = viewModel::clearSearchHistory,
                                )
                            }
                        }
                    }
                }
                searchResults.isEmpty() -> item(key = "empty") {
                    SettingsSearchEmptyState(
                        onClearQuery = { searchInput.clearText() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> items(
                    items = searchResults,
                    key = { it.settingId },
                    contentType = { "setting_result" },
                ) { result ->
                    SettingsSearchResultRow(
                        result = result,
                        settingsState = settingsState,
                        query = searchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.recordSearchQuery(searchQuery)
                            keyboard?.hide()
                            if (result.settingId == PROCESS_MEMORY_SETTING_ID) {
                                SettingsSearchFocusController.clear()
                            } else {
                                SettingsSearchFocusController.submit(
                                    result.target, result.focusId,
                                    result.settingId.takeUnless { it.startsWith("legacy.") },
                                    query = searchQuery,
                                )
                            }
                            if (result.target == com.android.purebilibili.feature.settings.SettingsSearchTarget.NEARBY_HANDOFF) {
                                nearbyHandoff?.openDeviceManager?.invoke()
                            } else onSearchResultClick(result)
                        },
                    )
                }
            }
        }
    }
}
