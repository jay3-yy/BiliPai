// 文件路径: feature/video/ui/components/CommentFraudDialog.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.CommentFraudStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

/**
 * [新增] 评论反诈检测结果弹窗
 * 发送评论后检测到异常状态时显示
 */
@Composable
fun CommentFraudResultDialog(
    status: CommentFraudStatus,
    onDismiss: () -> Unit,
    onDeleteComment: (() -> Unit)? = null,
    message: String? = null,
    onAppeal: (() -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
) {
    val (icon, title, description, color) = remember(status) {
        when (status) {
            CommentFraudStatus.NORMAL -> FraudDialogInfo(
                icon = Icons.Outlined.CheckCircle,
                title = "评论正常",
                description = "您的评论可以被其他用户正常看到。",
                color = FraudStatusColor.GREEN
            )
            CommentFraudStatus.SHADOW_BANNED -> FraudDialogInfo(
                icon = Icons.Outlined.VisibilityOff,
                title = "评论仅自己可见",
                description = "发表账号可以查到这条评论，游客无法查到。",
                color = FraudStatusColor.RED
            )
            CommentFraudStatus.DELETED -> FraudDialogInfo(
                icon = Icons.Outlined.Delete,
                title = "评论已不可见",
                description = "发表账号查询时，接口返回评论或所在楼层已失效。",
                color = FraudStatusColor.RED
            )
            CommentFraudStatus.UNDER_REVIEW -> FraudDialogInfo(
                icon = Icons.Outlined.Schedule,
                title = "评论疑似审核中",
                description = "游客在最新评论列表里没有找到这条评论，但可以打开它的回复页。",
                color = FraudStatusColor.ORANGE
            )
            CommentFraudStatus.INVISIBLE -> FraudDialogInfo(
                icon = Icons.Outlined.Visibility,
                title = "评论被前端隐藏",
                description = "接口返回了这条评论，并标记为隐藏。",
                color = FraudStatusColor.ORANGE
            )
            CommentFraudStatus.UNKNOWN -> FraudDialogInfo(
                icon = Icons.Outlined.HelpOutline,
                title = "检测结果未知",
                description = "无法确定评论状态，可能是网络问题导致检测失败。",
                color = FraudStatusColor.GRAY
            )
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
        icon = {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = when (color) {
                    FraudStatusColor.GREEN -> AppContentColors.accent()
                    FraudStatusColor.RED -> AppContentColors.error()
                    FraudStatusColor.ORANGE -> MaterialTheme.colorScheme.tertiary
                    FraudStatusColor.GRAY -> AppContentColors.secondaryText()
                }
            )
        },
        title = {
            AppText(
                text = title,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column {
                AppText(
                    text = if (message.isNullOrBlank()) description else "$description\n\n评论内容：$message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                )
                if (onDeleteComment != null) {
                    AppTextButton(onClick = {
                        onDeleteComment()
                        onDismiss()
                    }) {
                        AppText("删除评论", color = AppContentColors.error())
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { AppText("知道了") }
        },
        dismissButton = onAppeal?.let { appeal ->
            { AppTextButton(onClick = appeal) { AppText("申诉") } }
        },
    )
}

/**
 * [新增] 评论反诈检测中的 Snackbar 提示条
 */
@Composable
fun CommentFraudDetectingBanner(
    isDetecting: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isDetecting,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AdaptiveLoadingIndicator(
                size = 12.dp,
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = "正在检测评论可见性…",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
        }
    }
}

// --- 内部数据类 ---

private enum class FraudStatusColor { GREEN, RED, ORANGE, GRAY }

private data class FraudDialogInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val color: FraudStatusColor
)
