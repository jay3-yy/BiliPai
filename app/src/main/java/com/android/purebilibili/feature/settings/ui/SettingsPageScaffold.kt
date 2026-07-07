package com.android.purebilibili.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.TopReadabilityChrome
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.feature.settings.resolveSettingsVisualSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    lazyListContent: (LazyListScope.() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val visualSpec = resolveSettingsVisualSpec()
    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            Box {
                TopReadabilityChrome(
                    height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                    surfaceColor = AppSurfaceTokens.groupedListContainer(),
                    surfaceAlpha = 0.86f,
                )
                AdaptiveTopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = rememberAppBackIcon(),
                                contentDescription = backContentDescription,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        containerColor = AppSurfaceTokens.groupedListContainer(),
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            if (header != null) {
                item {
                    header()
                }
            }
            if (lazyListContent != null) {
                lazyListContent()
            } else {
                item {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun SettingsLargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val visualSpec = resolveSettingsVisualSpec()
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = visualSpec.largeTitleFontSize,
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(
            start = visualSpec.screenHorizontalPadding,
            end = visualSpec.screenHorizontalPadding,
            top = 4.dp,
            bottom = visualSpec.largeTitleBottomPadding,
        ),
    )
}
