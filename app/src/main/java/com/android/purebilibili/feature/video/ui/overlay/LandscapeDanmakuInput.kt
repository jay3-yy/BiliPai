// 文件路径: feature/video/ui/overlay/LandscapeDanmakuInput.kt
package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.handwriting.handwritingDetector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.appDesktopFocusableItemVisuals

/**
 *  横屏弹幕输入框组件
 * 
 * 仿官方 B 站设计，显示为可点击的文本框
 * 点击后弹出弹幕发送界面
 */
@Composable
fun LandscapeDanmakuInput(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "发个友善的弹幕见证当下"
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(Color.White.copy(alpha = 0.15f))
            .appDesktopFocusableItemVisuals()
            .handwritingDetector { onClick() }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AppText(
            text = placeholder,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 *  横屏底部互动按钮（点赞/投币小按钮）
 */
@Composable
fun LandscapeQuickActionButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .appDesktopFocusableItemVisuals()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = emoji,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
