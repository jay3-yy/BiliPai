package com.android.purebilibili.feature.download

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import java.io.File
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 文件夹选择面板：手机使用底部弹层，大屏沿用共用居中呈现策略。
 *
 * @param initialPath 初始路径
 * @param onPathSelected 选中回调
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorySelectionDialog(
    initialPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    var fileList by remember { mutableStateOf<List<File>>(emptyList()) }
    var readableParentPath by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // 加载文件列表（放至 IO 线程，避免主线程目录遍历与 stat/access 系统调用阻塞）
    LaunchedEffect(currentPath) {
        listState.scrollToItem(0)
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = File(currentPath)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()?.filter {
                    it.isDirectory && !it.name.startsWith(".") // 仅显示目录且不以.开头
                }?.sortedBy { it.name } ?: emptyList()
                val parentPath = dir.parentFile?.takeIf { it.canRead() }?.absolutePath
                Triple(true, files, parentPath)
            } else {
                val root = android.os.Environment.getExternalStorageDirectory()
                val fallback = root.takeIf { it.exists() }?.absolutePath
                Triple(false, emptyList<File>(), fallback)
            }
        }
        val (validDir, files, pathOrFallback) = result
        if (validDir) {
            fileList = files
            readableParentPath = pathOrFallback
        } else if (pathOrFallback != null && pathOrFallback != currentPath) {
            currentPath = pathOrFallback
        } else {
            fileList = emptyList()
            readableParentPath = null
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        useWindowPredictiveBack = true,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item(key = "directory_header") {
                Column {
                    // 1. 顶部标题和当前路径
                    AppText(
                        text = "选择存储位置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 当前路径显示
                    AppSurface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = AppShapes.container(ContainerLevel.Chip),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppText(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                maxLines = 2
                            )
                        }
                    }

                    // 返回上级按钮
                    val parentPath = readableParentPath
                    if (parentPath != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = parentPath }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上级目录",
                                tint = AppContentColors.accent()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AppText(
                                text = ".. (返回上级)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AppHorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            if (fileList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = "空文件夹",
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            } else {
                items(fileList, key = { it.absolutePath }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentPath = file.absolutePath }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        AppText(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.primaryText(),
                            modifier = Modifier.weight(1f)
                        )
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText().copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AppHorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            item(key = "directory_actions") {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 底部按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppOutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.container(ContainerLevel.Chip)
                        ) {
                            AppText("取消")
                        }

                        AppButton(
                            onClick = { onPathSelected(currentPath) },
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.container(ContainerLevel.Chip)
                        ) {
                            AppText("选择此目录")
                        }
                    }
                }
            }
        }
    }
}
