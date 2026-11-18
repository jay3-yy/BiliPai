package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
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
            AppText(
                text = "本应用仅用于学习与交流。\n\n" +
                    "官方渠道：GitHub、Telegram 频道与交流群。\n" +
                    "除上述渠道外，不存在任何其他官方发布途径。\n\n" +
                    "请勿安装来源不明的安装包，以避免账号与设备安全风险。"
            )
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("我已知晓")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppTextButton(onClick = onOpenGithub) { AppText("GitHub") }
                AppTextButton(onClick = onOpenTelegram) { AppText("频道") }
            }
        }
    )
}
