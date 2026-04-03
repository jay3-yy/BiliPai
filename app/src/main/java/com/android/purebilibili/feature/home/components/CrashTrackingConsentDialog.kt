package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.theme.BiliPink
import kotlinx.coroutines.launch

/**
 *  首次启动隐私提示弹窗
 * 告知用户关于崩溃追踪的用途，并让用户选择是否开启
 */
@Composable
fun CrashTrackingConsentDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(true) }  // 默认开启
    
    Dialog(onDismissRequest = { /* 不允许点击外部关闭 */ }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "🛡️ 帮助我们改进应用",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 说明文字
                Text(
                    text = "为了快速发现和修复应用问题，BiliPai 会收集崩溃报告和错误日志。\n\n" +
                           "默认仅启用崩溃追踪；使用情况统计默认关闭。播放器诊断日志保持可用，方便排查黑屏、卡顿等播放问题。\n\n" +
                           "这些数据仅用于改善应用稳定性，你也可以随时在「设置」中调整相关开关。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 开关选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启用崩溃追踪",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = BiliPink
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 确认按钮
                Button(
                    onClick = {
                        scope.launch {
                            // 保存用户选择
                            SettingsManager.setCrashTrackingEnabled(context, isEnabled)
                            SettingsManager.setCrashTrackingConsentShown(context, true)
                            
                            // 应用设置到 Crashlytics
                            CrashReporter.setEnabled(isEnabled)
                            
                            //  [修复] 确保设置保存后再关闭弹窗
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BiliPink
                    )
                ) {
                    Text(
                        text = "确定",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
