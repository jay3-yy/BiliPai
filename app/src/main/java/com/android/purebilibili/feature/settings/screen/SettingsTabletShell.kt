package com.android.purebilibili.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.ui.AdaptiveSplitLayout
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.feature.settings.SettingsHomeSearchEntry
import com.android.purebilibili.feature.settings.SettingsRootCategory
import com.android.purebilibili.feature.settings.rememberSettingsEntryVisual
import com.android.purebilibili.feature.settings.resolveSettingsRootCategoryOrder
import com.android.purebilibili.feature.settings.resolveSettingsTabletLayoutPolicy
import com.android.purebilibili.feature.settings.resolveSettingsVisualSpec

@Composable
fun SettingsTabletShell(
    selectedCategory: SettingsRootCategory?,
    onCategoryClick: (SettingsRootCategory) -> Unit,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    modifier: Modifier = Modifier,
    rightPane: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveSettingsTabletLayoutPolicy(widthDp = configuration.screenWidthDp)
    }
    val categories = remember { resolveSettingsRootCategoryOrder() }
    val uiPreset = LocalUiPreset.current

    AdaptiveSplitLayout(
        modifier = modifier.fillMaxSize(),
        primaryRatio = layoutPolicy.primaryRatio,
        primaryContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())
                    .padding(layoutPolicy.masterPanePaddingDp.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = rememberAppBackIcon(),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingsHomeSearchEntry(onClick = onSearchOpen)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(categories) { category ->
                        val visual = rememberSettingsEntryVisual(category.searchTarget, uiPreset)
                        val selected = selectedCategory == category
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(category.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = category.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            },
                            selected = selected,
                            onClick = { onCategoryClick(category) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(resolveSettingsVisualSpec().categoryIconBubbleSize)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(visual.iconTint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (visual.icon != null) {
                                        Icon(
                                            imageVector = visual.icon,
                                            contentDescription = null,
                                            tint = visual.iconTint,
                                            modifier = Modifier.size(resolveSettingsVisualSpec().categoryIconSize),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        secondaryContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())
                    .padding(layoutPolicy.detailPanePaddingDp.dp),
            ) {
                rightPane()
            }
        },
    )
}
