@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.plugin.bpx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.plugin.bpx.BpxCommentCraftResult
import com.android.purebilibili.core.plugin.bpx.BpxInstaller
import com.android.purebilibili.core.plugin.bpx.BpxMount
import com.android.purebilibili.core.plugin.bpx.BpxPinnedCommentCard
import com.android.purebilibili.core.plugin.bpx.BpxSandboxRuntime
import com.android.purebilibili.core.plugin.bpx.BpxSheetDocument
import com.android.purebilibili.core.plugin.bpx.InstalledBpxRecord
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.appVerticalScroll
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.SubtitleContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

private fun formatSecondsLabel(sec: Double): String {
    val total = sec.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/**
 * `detail.companion` 原位插槽：
 * 在视频详情页渲染快捷扩展胶囊，点击拉起遵循 MD3 / Miuix / 液态玻璃规范的 `AppModalBottomSheet`，
 * 支持 SSE 流式更新正文与 `[秒数]` 时间戳直跳播放器。
 */
@Composable
fun BpxDetailCompanionStrip(
    info: ViewInfo,
    onTimestampClick: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(context) { BpxInstaller.ensureLoaded(context) }
    val allExtensions by BpxInstaller.extensionsFlow.collectAsStateWithLifecycle()

    val companionEntries = remember(allExtensions) {
        allExtensions
            .filter { it.enabled && it.format == "js" }
            .flatMap { rec ->
                rec.manifest.mounts
                    .filter { it.slot == "detail.companion" }
                    .map { mount -> rec to mount }
            }
    }
    if (companionEntries.isEmpty()) return

    var activeTarget by remember(info.bvid, info.cid) {
        mutableStateOf<Pair<InstalledBpxRecord, BpxMount>?>(null)
    }
    var sheetDoc by remember(info.bvid, info.cid) { mutableStateOf<BpxSheetDocument?>(null) }
    var sheetLoading by remember(info.bvid, info.cid) { mutableStateOf(false) }
    var sheetError by remember(info.bvid, info.cid) { mutableStateOf<String?>(null) }

    fun openCompanion(record: InstalledBpxRecord, mount: BpxMount) {
        activeTarget = record to mount
        sheetDoc = null
        sheetError = null
        sheetLoading = true
        scope.launch {
            val needSubtitles = "subtitles" in record.manifest.permissions.videoContext
            val cues = if (needSubtitles && info.bvid.isNotBlank() && info.cid > 0L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val playerInfo = SubtitleContentRepository.playerInfo(info.bvid, info.cid).getOrNull()
                        val firstSub = playerInfo?.subtitle?.subtitles?.firstOrNull()
                        if (firstSub != null && firstSub.subtitleUrl.isNotBlank()) {
                            SubtitleContentRepository.cues(
                                subtitleUrl = firstSub.subtitleUrl,
                                bvid = info.bvid,
                                cid = info.cid,
                                subtitleId = firstSub.id,
                                subtitleIdStr = firstSub.idStr,
                                subtitleLan = firstSub.lan,
                            ).getOrDefault(emptyList())
                        } else emptyList()
                    }.getOrDefault(emptyList())
                }
            } else emptyList()

            val videoContextJson = buildJsonObject {
                put("bvid", JsonPrimitive(info.bvid))
                put("aid", JsonPrimitive(info.aid))
                put("cid", JsonPrimitive(info.cid))
                put("title", JsonPrimitive(info.title))
                put("desc", JsonPrimitive(info.desc))
                put("durationSec", JsonPrimitive(info.pages.firstOrNull { it.cid == info.cid }?.duration ?: 0))
                put("subtitles", buildJsonArray {
                    cues.take(320).forEach { cue ->
                        add(buildJsonObject {
                            put("fromSec", JsonPrimitive(cue.startMs / 1000.0))
                            put("toSec", JsonPrimitive(cue.endMs / 1000.0))
                            put("content", JsonPrimitive(cue.content))
                        })
                    }
                })
            }

            val result = BpxSandboxRuntime.runCompanionSheet(
                context = context,
                record = record,
                mount = mount,
                videoContextJson = videoContextJson,
                onEmit = { partial ->
                    scope.launch(Dispatchers.Main.immediate) {
                        sheetDoc = partial
                    }
                },
            )
            sheetLoading = false
            result.onSuccess { finalDoc ->
                sheetDoc = finalDoc
            }.onFailure { err ->
                sheetError = err.message ?: "扩展执行失败"
            }
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = companionEntries,
            key = { (rec, m) -> "${rec.manifest.id}:${m.id}" },
            contentType = { "bpx_companion_pill" }
        ) { (rec, mount) ->
            AppSurface(
                onClick = { openCompanion(rec, mount) },
                shape = AppShapes.container(ContainerLevel.Chip),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(
                        text = "✦ ${mount.title.ifBlank { rec.manifest.name }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }

    activeTarget?.let { (record, mount) ->
        val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f
        AppModalBottomSheet(
            onDismissRequest = { activeTarget = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .appVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = mount.title.ifBlank { record.manifest.name },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppContentColors.primaryText(),
                        )
                        AppText(
                            text = "${record.manifest.name} · Beta 扩展沙盒",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                    if (sheetLoading) {
                        AppCircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }

                if (sheetError != null) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(
                            text = sheetError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                sheetDoc?.blocks?.forEach { block ->
                    when (block.type) {
                        "timestamps" -> if (block.items.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                block.items.forEach { anchor ->
                                    AppSurface(
                                        onClick = {
                                            onTimestampClick?.invoke((anchor.timeSec * 1000.0).toLong().coerceAtLeast(0L))
                                            activeTarget = null
                                        },
                                        shape = AppShapes.container(ContainerLevel.Chip),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        AppText(
                                            text = "▶ ${formatSecondsLabel(anchor.timeSec)} ${anchor.label}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        "markdown" -> if (!block.content.isNullOrBlank()) {
                            AppText(
                                text = block.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppContentColors.primaryText(),
                            )
                        }
                        "kv" -> block.rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    text = row.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                                AppText(
                                    text = row.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AppContentColors.primaryText(),
                                )
                            }
                        }
                        "actions" -> if (block.buttons.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                block.buttons.forEach { btn ->
                                    AppTextButton(
                                        onClick = {
                                            if (!btn.copyText.isNullOrBlank()) {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText(btn.label, btn.copyText))
                                                Toast.makeText(context, "已复制：${btn.label}", Toast.LENGTH_SHORT).show()
                                            } else if (!btn.openUri.isNullOrBlank()) {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(btn.openUri)))
                                                }
                                            }
                                        }
                                    ) {
                                        AppText(btn.label)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * `community.craft` 原位插槽：异步提炼热评中的时间轴指路并识别需温和折叠的刷屏楼层。
 */
@Composable
fun rememberBpxCommunityCraftResult(
    bvid: String,
    replies: List<ReplyItem>,
): State<BpxCommentCraftResult?> {
    val context = LocalContext.current
    LaunchedEffect(context) { BpxInstaller.ensureLoaded(context) }
    val allExtensions by BpxInstaller.extensionsFlow.collectAsStateWithLifecycle()
    val craftEntries = remember(allExtensions) {
        allExtensions
            .filter { it.enabled && it.format == "js" }
            .flatMap { rec ->
                rec.manifest.mounts
                    .filter { it.slot == "community.craft" }
                    .map { mount -> rec to mount }
            }
    }
    val headSignature = remember(replies) {
        replies.take(18).joinToString(",") { "${it.rpid}:${it.like}" }
    }
    return produceState<BpxCommentCraftResult?>(
        initialValue = null,
        key1 = bvid,
        key2 = headSignature,
        key3 = craftEntries.map { it.first.sha256 },
    ) {
        if (craftEntries.isEmpty() || replies.isEmpty()) {
            value = null
            return@produceState
        }
        val snapshotJson = buildJsonObject {
            put("bvid", JsonPrimitive(bvid))
            put("comments", buildJsonArray {
                replies.take(20).forEach { r ->
                    add(buildJsonObject {
                        put("rpid", JsonPrimitive(r.rpid))
                        put("memberName", JsonPrimitive(r.member.uname))
                        put("content", JsonPrimitive(r.content.message))
                        put("likeCount", JsonPrimitive(r.like))
                    })
                }
            })
        }
        var mergedCard: BpxPinnedCommentCard? = null
        val folded = linkedSetOf<Long>()
        for ((rec, mount) in craftEntries) {
            val res = BpxSandboxRuntime.runCommunityCraft(context, rec, mount, snapshotJson).getOrNull() ?: continue
            if (mergedCard == null && res.pinnedCard != null) {
                mergedCard = res.pinnedCard
            }
            folded.addAll(res.foldRpids)
        }
        value = if (mergedCard != null || folded.isNotEmpty()) {
            BpxCommentCraftResult(pinnedCard = mergedCard, foldRpids = folded.toList())
        } else null
    }
}

@Composable
fun BpxCommentPinnedCard(
    card: BpxPinnedCommentCard,
    onTimestampClick: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppText(
                    text = "📌 ${card.title}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppContentColors.primaryText(),
                )
                AppText(
                    text = "扩展提炼",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            if (!card.summary.isNullOrBlank()) {
                AppText(
                    text = card.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.primaryText(),
                )
            }
            if (card.timestamps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.timestamps.forEach { anchor ->
                        AppSurface(
                            onClick = {
                                onTimestampClick?.invoke((anchor.timeSec * 1000.0).toLong().coerceAtLeast(0L))
                            },
                            shape = AppShapes.container(ContainerLevel.Chip),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            AppText(
                                text = "${formatSecondsLabel(anchor.timeSec)} ${anchor.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
