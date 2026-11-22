package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppTextButton

const val OFFICIAL_GITHUB_URL = "https://github.com/jay3-yy/BiliPai/"
/** 官方 Telegram 频道（公告 / 发布） */
const val OFFICIAL_TELEGRAM_CHANNEL_URL = "https://t.me/bilipai666"
/** 官方 Telegram 交流群（话题入口） */
const val OFFICIAL_TELEGRAM_GROUP_URL = "https://t.me/bilipai888/1"
/** 兼容旧调用：默认打开频道 */
const val OFFICIAL_TELEGRAM_URL = OFFICIAL_TELEGRAM_CHANNEL_URL
@Composable
fun ReleaseChannelDisclaimerDialog(
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenTelegram: () -> Unit,
    title: String = "免责声明"
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    text = "本应用仅用于学习与交流。\n\n" +
                        "官方渠道：GitHub、Telegram 频道与交流群。\n" +
                        "除上述渠道外，不存在任何其他官方发布途径。\n\n" +
                        "请勿安装来源不明的安装包，以避免账号与设备安全风险。"
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 渠道入口独立成行，避免在 Miuix 等分操作槽中与「我已知晓」共挤导致文字折行错位。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(onClick = onOpenGithub) {
                        AppText("GitHub", maxLines = 1, softWrap = false)
                    }
                    AppTextButton(onClick = onOpenTelegram) {
                        AppText("频道", maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("我已知晓", maxLines = 1, softWrap = false)
            }
        },
    )
}
