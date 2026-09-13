package com.android.purebilibili.feature.download
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.purebilibili.core.ui.appContentDialogWidth
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.resolveAppContentDialogProperties
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 *  下载画质选择对话框
 */
@Composable
fun DownloadQualityDialog(
    title: String,
    qualityOptions: List<Pair<Int, String>>,  // (qualityId, qualityLabel)
    currentQuality: Int,
    onQualitySelected: (Int, DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var includeDanmaku by remember { mutableStateOf(true) }
    val dialogLayout = remember { resolveAppContentDialogLayoutPolicy(maxWidthDp = 420) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = resolveAppContentDialogProperties(
            usePlatformDefaultWidth = dialogLayout.usePlatformDefaultWidth,
        ),
    ) {
        AppCard(
            modifier = Modifier.appContentDialogWidth(policy = dialogLayout),
            shape = AppCardShape.Semantic(ContainerLevel.Card),
            colors = AppCardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "选择下载画质",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppIconButton(onClick = onDismiss) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "取消",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 视频标题
                AppText(
                    text = title,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .clickable { includeDanmaku = !includeDanmaku }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCheckbox(
                        checked = includeDanmaku,
                        onCheckedChange = { includeDanmaku = it }
                    )
                    AppText(
                        text = "同时缓存弹幕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // 画质列表
                qualityOptions.forEach { (qualityId, qualityLabel) ->
                    val isSelected = qualityId == currentQuality
                    val isVip = qualityLabel.contains("4K") || qualityLabel.contains("HDR") || qualityLabel.contains("杜比")
                    
                    AppSingleChoiceRow(
                        selected = isSelected,
                        onClick = {
                            onQualitySelected(qualityId, DownloadOptions(includeDanmaku = includeDanmaku))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                text = qualityLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = AppSurfaceTokens.onSurfaceContainerHigh()
                            )
                            if (isVip) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AppSurface(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = AppShapes.container(ContainerLevel.Tag)
                                ) {
                                    AppText(
                                        text = "VIP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 取消按钮
                AppOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.container(ContainerLevel.Chip)
                ) {
                    AppText("取消")
                }
            }
        }
    }
}
