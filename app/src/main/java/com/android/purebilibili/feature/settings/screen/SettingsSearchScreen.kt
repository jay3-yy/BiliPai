package com.android.purebilibili.feature.settings.screen


import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.settings.SettingsSearchHistorySection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.android.purebilibili.feature.settings.SettingsSearchEmptyState
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

@Composable
fun SettingsSearchScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onSearchResultClick: (SettingsSearchResult) -> Unit,
    onCategoryClick: (SettingsRootCategory) -> Unit = {},
    mainHazeState: HazeState? = null,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberSaveable(searchQuery, saver = LazyListState.Saver) {
        LazyListState()
    }
    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchResults = remember(searchQuery) {
        resolveSettingsSearchResults(query = searchQuery, maxResults = Int.MAX_VALUE)
    }
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

    SettingsPageScaffold(
        title = stringResource(R.string.settings_search_results_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = bottomInset,
        scrollHost = SettingsPageScrollHost.External,
        header = {
            SettingsSearchBarSection(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { viewModel.recordSearchQuery(searchQuery) },
                autoFocusEnabled = !navigationTransitionRunning,
            )
        },
    ) {
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
                searchQuery.isBlank() -> item(key = "history") {
                    EntranceGroup(startWhen = rootEntranceStartWhen) {
                        SettingsRootCategoryEntranceSection {
                            SettingsSearchHistorySection(
                                history = searchHistory,
                                onQueryClick = {
                                    searchQuery = it
                                    viewModel.recordSearchQuery(it)
                                },
                                onDelete = viewModel::deleteSearchHistory,
                                onClear = viewModel::clearSearchHistory,
                            )
                        }
                    }
                }
                searchResults.isEmpty() -> item(key = "empty") {
                    SettingsSearchEmptyState(onClearQuery = { searchQuery = "" })
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
                        onClick = {
                            viewModel.recordSearchQuery(searchQuery)
                            keyboard?.hide()
                            SettingsSearchFocusController.submit(result.target, result.focusId, result.settingId.takeUnless { it.startsWith("legacy.") }, query = searchQuery)
                            onSearchResultClick(result)
                        },
                    )
                }
            }
        }
    }
}
