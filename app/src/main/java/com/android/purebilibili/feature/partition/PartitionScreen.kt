// 文件路径: feature/partition/PartitionScreen.kt
package com.android.purebilibili.feature.partition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.util.responsiveContentWidth

/**
 *  分区数据类
 */
data class PartitionCategory(
    val id: Int,
    val name: String,
    val emoji: String,
    val color: Color
)

/**
 *  所有分区列表 (参考官方 Bilibili API)
 * tid 是 Bilibili 官方的分区 ID，用于 x/web-interface/newlist 接口
 * 注意：番剧/国创/电影/电视剧/纪录片是特殊分区，使用不同的 API
 */
val allPartitions = listOf(
    // === 视频分区（支持 newlist API）===
    PartitionCategory(1, "动画", "🎬", Color(0xFF7BBEEC)),
    PartitionCategory(13, "番剧", "📺", Color(0xFFFF6B9D)),      // 特殊分区
    PartitionCategory(167, "国创", "🇨🇳", Color(0xFFFF7575)),     // 特殊分区
    PartitionCategory(3, "音乐", "🎵", Color(0xFF6BB5FF)),
    PartitionCategory(129, "舞蹈", "💃", Color(0xFFFF7777)),
    PartitionCategory(4, "游戏", "🎮", Color(0xFF7FD37F)),
    PartitionCategory(36, "知识", "📚", Color(0xFFFFD166)),
    PartitionCategory(188, "科技", "💻", Color(0xFF6ECFFF)),
    PartitionCategory(234, "运动", "⚽", Color(0xFF7BC96F)),
    PartitionCategory(223, "汽车", "🚗", Color(0xFF74C0FC)),
    PartitionCategory(160, "生活", "🏠", Color(0xFFFFB366)),
    PartitionCategory(211, "美食", "🍜", Color(0xFFFFAB5C)),
    PartitionCategory(217, "动物圈", "🐾", Color(0xFFB5D9A8)),
    PartitionCategory(119, "鬼畜", "👻", Color(0xFFA8E6CF)),
    PartitionCategory(155, "时尚", "👗", Color(0xFFFF9ECD)),
    PartitionCategory(202, "资讯", "📰", Color(0xFF98D8C8)),
    PartitionCategory(5, "娱乐", "🎪", Color(0xFFFFB347)),
    // === 特殊分区（番剧/电影等使用不同 API）===
    PartitionCategory(23, "电影", "🎬", Color(0xFFFF9E7A)),      // 特殊分区
    PartitionCategory(11, "电视剧", "📺", Color(0xFFFF85A2)),    // 特殊分区
    PartitionCategory(177, "纪录片", "🎥", Color(0xFF7BC8F6)),   // 特殊分区
    PartitionCategory(181, "影视", "🎦", Color(0xFFC7A4FF))      // 特殊分区
)

/**
 *  分区页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionScreen(
    onBack: () -> Unit,
    onPartitionClick: (Int, String) -> Unit = { _, _ -> }  // 分区ID + 分区名
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分区") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .responsiveContentWidth(maxWidth = 1000.dp) // 📐 [Tablet Adaptation] Limit content width
        ) {
            //  快捷访问
            Text(
                text = "快捷访问",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { /* TODO: 编辑快捷访问 */ },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ 编辑",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            //  全部分区
            Text(
                text = "全部分区",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            //  分区网格
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(allPartitions) { partition ->
                    PartitionItem(
                        partition = partition,
                        onClick = { onPartitionClick(partition.id, partition.name) }
                    )
                }
            }
        }
    }
}

/**
 *  分区项目
 */
@Composable
private fun PartitionItem(
    partition: PartitionCategory,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        // 图标
        Text(
            text = partition.emoji,
            fontSize = 28.sp
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // 名称
        Text(
            text = partition.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
