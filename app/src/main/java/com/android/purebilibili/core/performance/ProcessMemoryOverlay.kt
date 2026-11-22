package com.android.purebilibili.core.performance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.ProcessMemoryOverlayPreferences
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appTabularNumberTextStyle
import kotlinx.coroutines.launch

@Composable
internal fun ProcessMemoryOverlayHost(
    canShow: Boolean,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canShow) return
    val context = LocalContext.current.applicationContext
    val enabledFlow = remember(context) { ProcessMemoryOverlayPreferences.observeEnabled(context) }
    val enabled by enabledFlow.collectAsStateWithLifecycle(initialValue = false)
    if (!enabled) return

    val snapshot by ProcessMemoryMonitor.snapshots.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED,
    )
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    Box(
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier.padding(top = 56.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val pss = snapshot?.let { processMemorySizeLabel(it.pssKiB) } ?: "—"
            AppSurface(
                onClick = { expanded = !expanded },
                shape = CircleShape,
                color = AppSurfaceTokens.cardContainer(),
                contentColor = AppContentColors.primaryText(),
                shadowElevation = 4.dp,
            ) {
                AppText(
                    text = stringResource(R.string.process_memory_overlay_pss, pss),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = appTabularNumberTextStyle(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    tapToCopyEnabled = false,
                )
            }

            if (expanded) {
                AppCard(Modifier.widthIn(max = 260.dp)) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val current = snapshot
                        if (current == null) {
                            AppText(stringResource(R.string.process_memory_sampling), fontSize = 13.sp)
                        } else {
                            for ((label, kib) in current.summaryMetrics()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppText(stringResource(label), Modifier.weight(1f), fontSize = 13.sp)
                                    AppText(
                                        text = processMemorySizeLabel(kib),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End,
                                        style = appTabularNumberTextStyle(),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                        AppTextButton(onClick = {
                            expanded = false
                            onOpenDetails()
                        }) {
                            AppText(stringResource(R.string.process_memory_overlay_details))
                        }
                        AppTextButton(onClick = {
                            expanded = false
                            scope.launch { ProcessMemoryOverlayPreferences.setEnabled(context, false) }
                        }) {
                            AppText(stringResource(R.string.process_memory_overlay_close))
                        }
                    }
                }
            }
        }
    }
}
