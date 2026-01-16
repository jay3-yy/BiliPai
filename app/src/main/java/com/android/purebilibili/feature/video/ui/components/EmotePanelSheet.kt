// 文件路径: feature/video/ui/components/EmotePanelSheet.kt
package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.model.response.EmoteItem
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator

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
    
    com.android.purebilibili.core.ui.IOSModalBottomSheet(
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
                Text(
                    text = "表情",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
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
                    CupertinoActivityIndicator()
                }
            } else if (packages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无表情包",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                // 表情包 Tab 栏
                var selectedPackageIndex by remember { mutableIntStateOf(0) }
                
                ScrollableTabRow(
                    selectedTabIndex = selectedPackageIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        if (tabPositions.isNotEmpty() && selectedPackageIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedPackageIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    packages.forEachIndexed { index, pkg ->
                        Tab(
                            selected = selectedPackageIndex == index,
                            onClick = { selectedPackageIndex = index },
                            text = {
                                Text(
                                    text = pkg.text,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                
                HorizontalDivider(
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
                    items(emotes) { emote ->
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
            .clip(RoundedCornerShape(8.dp))
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
