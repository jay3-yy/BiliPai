package com.android.purebilibili.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.ui.blur.BlurIntensity
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*

/**
 *  模糊强度选择器 (可展开/收起)
 *  Used in AnimationSettingsScreen and potentially others.
 */
@Composable
fun BlurIntensitySelector(
    selectedIntensity: BlurIntensity,
    onIntensityChange: (BlurIntensity) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // 获取当前选中项的显示文本
    val currentTitle = when (selectedIntensity) {
        BlurIntensity.THIN -> "标准"
        BlurIntensity.THICK -> "浓郁"
        BlurIntensity.APPLE_DOCK -> "玻璃拟态"
    }
    
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // 标题行 - 可点击展开/收起
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                CupertinoIcons.Default.Sparkles,
                contentDescription = null,
                tint = iOSBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "模糊强度",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 展开/收起箭头
            Icon(
                imageVector = if (isExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 展开后的选项 - 带动画
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 8.dp)) {
                //  [调整] 顺序：标准 → 玻璃拟态 → 浓郁
                BlurIntensityOption(
                    icon = CupertinoIcons.Default.CheckmarkCircle,
                    iconTint = iOSBlue,
                    title = "标准",
                    description = "平衡美观与性能（推荐）",
                    isSelected = selectedIntensity == BlurIntensity.THIN,
                    onClick = { 
                        onIntensityChange(BlurIntensity.THIN)
                        isExpanded = false
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                //  玻璃拟态风格 - 移到中间
                BlurIntensityOption(
                    icon = CupertinoIcons.Default.Desktopcomputer,
                    iconTint = com.android.purebilibili.core.theme.iOSSystemGray,
                    title = "玻璃拟态",
                    description = "强烈模糊，完全遮盖背景",
                    isSelected = selectedIntensity == BlurIntensity.APPLE_DOCK,
                    onClick = { 
                        onIntensityChange(BlurIntensity.APPLE_DOCK)
                        isExpanded = false
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                //  浓郁 - 移到最后，有背景透色
                BlurIntensityOption(
                    icon = CupertinoIcons.Default.Sparkle,
                    iconTint = iOSPurple,
                    title = "浓郁",
                    description = "背景颜色透出 + 磨砂质感",
                    isSelected = selectedIntensity == BlurIntensity.THICK,
                    onClick = { 
                        onIntensityChange(BlurIntensity.THICK)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun BlurIntensityOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                CupertinoIcons.Default.Checkmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
