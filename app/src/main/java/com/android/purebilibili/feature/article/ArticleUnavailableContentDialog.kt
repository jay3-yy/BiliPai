package com.android.purebilibili.feature.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.OpusLinkCard

@Composable
internal fun ArticleUnavailableContentDialog(
    card: OpusLinkCard,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contentId = remember(card.oid) { resolveArticleUnavailableContentId(card.oid) }
    val targets = remember(contentId) { resolveArticleUnavailableTargets(contentId.orEmpty()) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { AppText("内容已失效") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.title.takeIf { it.isNotBlank() && it != "内容已失效" }?.let { AppText(it) }
                AppText(when {
                    contentId == null -> "暂无可用的内容号"
                    targets.isEmpty() -> "暂不支持按此内容号打开"
                    else -> "选择内容类型尝试打开"
                }, color = AppContentColors.secondaryText())
                if (contentId != null) {
                    AppText("内容号：$contentId")
                    targets.forEach { target ->
                        AppTextButton(onClick = { onDismiss(); onOpenUrl(target.url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            AppText(target.label, tapToCopyEnabled = false)
                        }
                    }
                    AppTextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("内容号", contentId))
                        Toast.makeText(context, "已复制内容号", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { AppText("复制内容号", tapToCopyEnabled = false) }
                }
            }
        },
        confirmButton = { AppDialogAction(onClick = onDismiss) { AppText("关闭", tapToCopyEnabled = false) } },
    )
}
