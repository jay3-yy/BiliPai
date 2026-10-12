// 文件路径: feature/video/ui/components/EmotePanelSheet.kt
package com.android.purebilibili.feature.video.ui.components

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.model.response.EmoteItem
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * [新增] 表情选择面板组件
 * 底部弹出的表情选择器，支持多个表情包分类
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotePanelSheet(
    visible: Boolean,
    packages: List<EmotePackage>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onEmoteSelect: (EmoteItem) -> Unit
) {
    if (!visible) return
    
    com.android.purebilibili.core.ui.AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(bottom = 16.dp)
        ) {
            // 标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AppText(
                    text = "表情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveLoadingIndicator()
                }
            } else if (packages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = "暂无表情包",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // 表情包 Tab 栏
                var selectedPackageIndex by remember { mutableIntStateOf(0) }
                
                AppThemeAdaptiveTabRow(
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                    centerContent = false,
                    options = packages.mapIndexed { index, pkg ->
                        AppSegmentOption(index, pkg.text)
                    },
                    selectedValue = selectedPackageIndex,
                    onSelectionChange = { selectedPackageIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                    scrollable = true,
                    labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                )
                
                AppHorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
                
                // 表情网格
                val selectedPackage = packages.getOrNull(selectedPackageIndex)
                val emotes = selectedPackage?.emote ?: emptyList()
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(emotes, key = { it.id }) { emote ->
                        EmoteGridItem(
                            emote = emote,
                            onClick = { onEmoteSelect(emote) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个表情项
 */
@Composable
private fun EmoteGridItem(
    emote: EmoteItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(emote.url)
                .crossfade(true)
                .build(),
            contentDescription = emote.text,
            modifier = Modifier.fillMaxSize()
        )
    }
}
