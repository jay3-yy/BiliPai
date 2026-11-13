package com.android.purebilibili.feature.live.components

import com.android.purebilibili.core.ui.adaptive.onSecondaryClick
import com.android.purebilibili.feature.live.encodeLiveDanmakuInfo
import com.android.purebilibili.feature.live.resolveLiveMessageMedal
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.formatLiveSuperChatCountdown
import com.android.purebilibili.feature.live.rememberLiveChromePalette
import com.android.purebilibili.feature.live.resolveLiveSuperChatColor
import com.android.purebilibili.feature.live.remainingLiveSuperChatSeconds
import kotlinx.coroutines.delay

@Composable
fun LiveSuperChatSection(
    items: List<LiveDanmakuItem>,
    persistent: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    modifier: Modifier = Modifier,
    onExpired: (LiveDanmakuItem) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onReport: (LiveDanmakuItem) -> Unit = {},
    onCountdownPauseChange: (LiveDanmakuItem, Boolean) -> Unit = { _, _ -> },
) {
    val palette = rememberLiveChromePalette()
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = items,
            key = { "${it.superChatId}_${it.uid}_${it.text}_${it.superChatPrice}" }
        ) { item ->
            LiveSuperChatCard(
                item = item,
                persistent = persistent,
                onCountdownPauseChange = { paused -> onCountdownPauseChange(item, paused) },
                onExpired = { onExpired(item) },
                onUserClick = { onUserClick(item.uid) },
                onReport = { onReport(item) },
            )
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                AppText(
                    text = "暂无醒目留言",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = AppSpacingTokens.Large),
                )
            }
        }
    }
}

@Composable
internal fun LiveSuperChatCard(
    item: LiveDanmakuItem,
    onExpired: () -> Unit,
    onUserClick: () -> Unit,
    onReport: () -> Unit,
    persistent: Boolean = false,
    onMenuVisibilityChange: (Boolean) -> Unit = {},
    onCountdownPauseChange: (Boolean) -> Unit = {},
) {
    val palette = rememberLiveChromePalette()
    val scope = rememberCoroutineScope()
    val cardLayer = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var menuExpanded by remember(item.superChatId) { mutableStateOf(false) }
    var previewFiles by remember(item.superChatId) { mutableStateOf<List<java.io.File>>(emptyList()) }
    if (previewFiles.isNotEmpty()) {
        com.android.purebilibili.feature.share.ShareImagePreviewSheet(
            document = com.android.purebilibili.feature.share.ShareImageDocument(
                key = "sc:${item.superChatId}", title = "醒目留言", url = "", blocks = emptyList(),
            ),
            capturedFiles = previewFiles,
            onDismiss = { previewFiles = emptyList() },
        )
    }

    val latestOnMenuVisibilityChange by rememberUpdatedState(onMenuVisibilityChange)
    androidx.compose.runtime.DisposableEffect(item.superChatId, menuExpanded) {
        latestOnMenuVisibilityChange(menuExpanded)
        onDispose { if (menuExpanded) latestOnMenuVisibilityChange(false) }
    }
    androidx.compose.runtime.DisposableEffect(item.superChatId, menuExpanded, persistent, previewFiles.isNotEmpty()) {
        val holdsCountdown = (menuExpanded || previewFiles.isNotEmpty()) && !persistent
        val notifyPause = onCountdownPauseChange
        if (holdsCountdown) notifyPause(true)
        onDispose { if (holdsCountdown) notifyPause(false) }
    }
    val endTime = item.superChatEndTime
    val currentOnExpired by rememberUpdatedState(onExpired)
    var remainingSec by remember(item.superChatId, endTime, item.text) {
        mutableIntStateOf(remainingLiveSuperChatSeconds(endTime, System.currentTimeMillis() / 1_000L))
    }

    LaunchedEffect(item.superChatId, endTime, item.text, persistent, menuExpanded, previewFiles.isNotEmpty()) {
        if (persistent || menuExpanded || previewFiles.isNotEmpty()) return@LaunchedEffect
        while (remainingSec > 0) {
            delay(1_000)
            remainingSec -= 1
        }
        currentOnExpired()
    }
    if (!persistent && remainingSec <= 0) return

    Box {
      val bottom = resolveLiveSuperChatColor(item.superChatBackgroundColor)
      val header = if (item.superChatHeaderColor != 0) Color(item.superChatHeaderColor) else Color.White
      val messageColor = if (item.superChatMessageColor != 0) Color(item.superChatMessageColor) else Color.White
      val nameColor = if (item.superChatNameColor != 0) Color(item.superChatNameColor) else Color.Black
      AppSurface(
          shape = RoundedCornerShape(8.dp),
          color = bottom,
          border = BorderStroke(1.dp, bottom),
          modifier = Modifier.fillMaxWidth().drawWithContent {
              cardLayer.record { this@drawWithContent.drawContent() }
              drawLayer(cardLayer)
          },
      ) {
          Column {
              Box(Modifier.fillMaxWidth().background(header)) {
              if (item.superChatHeaderImage.isNotBlank()) AsyncImage(
                  model = item.superChatHeaderImage, contentDescription = null,
                  modifier = Modifier.matchParentSize(), alignment = Alignment.TopEnd, contentScale = ContentScale.Inside,
              )
              Row(
                  modifier = Modifier.fillMaxWidth()
                      .onSecondaryClick { menuExpanded = true }
                      .clickable(onClickLabel = "醒目留言操作") { menuExpanded = true }
                      .padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                  Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                      AsyncImage(model = item.superChatFace, contentDescription = null,
                          modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                      if (item.superChatFaceFrame.isNotBlank()) AsyncImage(
                          model = item.superChatFaceFrame, contentDescription = null,
                          modifier = Modifier.size(52.2.dp), contentScale = ContentScale.Fit)
                  }
                  Column(Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                          resolveLiveMessageMedal(item)?.let { medal -> LiveMedalBadge(medal) }
                          AppText(item.uname, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                      }
                      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                          AppText(item.superChatPrice, color = bottom)
                          if (persistent && item.superChatStartTime > 0L) AppText(
                              android.text.format.DateFormat.format("MM-dd HH:mm", item.superChatStartTime * 1_000L).toString(),
                              color = bottom, fontSize = 13.5.sp,
                          )
                      }
                  }
                  if (!persistent) AppText(remainingSec.toString(), color = Color.Gray, fontSize = 14.sp)
              }
              }
              SelectionContainer {
                  AppText(item.text, color = messageColor, modifier = Modifier.fillMaxWidth().padding(8.dp))
              }
          }
      }
      AppDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          AppDropdownMenuItem(
              text = { AppText("访问：${item.uname.ifBlank { "醒目留言用户" }}") },
              enabled = item.uid > 0L,
              onClick = { menuExpanded = false; onUserClick() },
          )
          AppDropdownMenuItem(
              text = { AppText("复制 SC 信息") },
              onClick = {
                  menuExpanded = false
                  val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(
                      ClipData.newPlainText(
                          "SC 信息",
                          item.superChatRawInfo.ifBlank { encodeLiveDanmakuInfo(item) },
                      ),
                  )
                  Toast.makeText(context, "已复制 SC 信息", Toast.LENGTH_SHORT).show()
              },
          )
          AppDropdownMenuItem(
              text = { AppText(if (saving) "保存中…" else "保存为图片") },
              enabled = !saving,
              onClick = {
                  menuExpanded = false
                  saving = true
                  scope.launch {
                      try {
                          val bitmap = cardLayer.toImageBitmap().asAndroidBitmap()
                          val saved = com.android.purebilibili.feature.screenshot.saveAppScreenshotBitmapToGallery(
                              context, bitmap, "SC_${item.superChatId}_${System.currentTimeMillis()}.png",
                          )
                          Toast.makeText(context, if (saved) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                      } catch (cancelled: kotlinx.coroutines.CancellationException) {
                          throw cancelled
                      } catch (_: Exception) {
                          Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                      } finally { saving = false }
                  }
              },
          )
          AppDropdownMenuItem(
              text = { AppText(if (saving) "生成中…" else "预览 / 分享图片") },
              enabled = !saving,
              onClick = {
                  saving = true
                  scope.launch {
                      try {
                          val bitmap = cardLayer.toImageBitmap().asAndroidBitmap()
                          previewFiles = listOf(com.android.purebilibili.feature.share.cacheCapturedShareImage(context, bitmap))
                          menuExpanded = false
                      } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { Toast.makeText(context, "生成图片失败，请重试", Toast.LENGTH_SHORT).show() } finally { saving = false }
                  }
              },
          )
          AppDropdownMenuItem(
              text = { AppText("举报") },
              onClick = { menuExpanded = false; onReport() },
          )
      }
    }
}
