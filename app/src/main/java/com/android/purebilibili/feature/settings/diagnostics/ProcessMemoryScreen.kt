package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.performance.ProcessMemoryCategory
import com.android.purebilibili.core.performance.ProcessMemorySnapshot
import com.android.purebilibili.core.performance.ProcessMemoryMonitor
import com.android.purebilibili.core.performance.processMemoryReading
import com.android.purebilibili.core.performance.processMemorySizeLabel
import com.android.purebilibili.core.performance.summaryMetrics
import com.android.purebilibili.core.store.ProcessMemoryOverlayPreferences
import com.android.purebilibili.core.ui.AdaptivePlainTooltipBox
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appTabularNumberTextStyle
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import java.text.NumberFormat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.LocalNavEntryContentVisible

internal const val PROCESS_MEMORY_SETTING_ID = "diagnostics.process_memory"
internal const val PROCESS_MEMORY_TITLE = "实时内存占用"
private val memoryLegendColumnsMinWidth = 250.dp

private val lightCategoryColors = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF6250D6), Color(0xFF008300),
)
private val darkCategoryColors = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF9085E9), Color(0xFF008300),
)

@Composable
internal fun ProcessMemoryScreen(onBack: () -> Unit) {
    var sample by remember { mutableStateOf<ProcessMemorySnapshot?>(null) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    val active = rememberVisualWorkActive() && LocalNavEntryContentVisible.current && !showGlossary
    LaunchedEffect(active) {
        if (active) {
            ProcessMemoryMonitor.snapshots.filterNotNull().collect { sample = it }
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.process_memory_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = resolveBottomSafeAreaPadding(
            navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            extraBottomPadding = 24.dp,
        ),
        actions = {
            val label = stringResource(R.string.process_memory_glossary)
            AdaptivePlainTooltipBox(
                text = label,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                AppIconButton(onClick = { showGlossary = true }) {
                    AppIcon(
                        imageVector = rememberSettingsVector(R.drawable.ph_info_24, R.drawable.ms_info_24),
                        contentDescription = label,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snapshot = sample
            if (snapshot == null) {
                AppCard(Modifier.fillMaxWidth()) {
                    AppText(
                        text = stringResource(R.string.process_memory_sampling),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                MemoryTotals(snapshot, Modifier.fillMaxWidth())
            }
            MemoryOverlayPreference(Modifier.fillMaxWidth())
            if (snapshot != null) {
                MemoryBreakdown(snapshot, Modifier.fillMaxWidth())
            }
        }
    }
    if (showGlossary) {
        MemoryGlossaryDialog(onDismiss = { showGlossary = false })
    }
}

@Composable
private fun MemoryOverlayPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val enabledFlow = remember(context) { ProcessMemoryOverlayPreferences.observeEnabled(context) }
    val enabled by enabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    AppCard(modifier) {
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PROCESS_MEMORY),
            title = stringResource(R.string.process_memory_overlay_title),
            subtitle = stringResource(R.string.process_memory_overlay_summary),
            checked = enabled,
            onCheckedChange = { checked ->
                scope.launch { ProcessMemoryOverlayPreferences.setEnabled(context, checked) }
            },
        )
    }
}

@Composable
private fun MemoryGlossaryDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.process_memory_glossary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = stringResource(R.string.process_memory_scope_description),
                    color = AppContentColors.secondaryText(),
                    fontSize = 13.sp,
                )
                MemoryTerm(R.string.process_memory_pss, R.string.process_memory_pss_description)
                MemoryTerm(R.string.process_memory_rss, R.string.process_memory_rss_description)
                MemoryTerm(R.string.process_memory_swap, R.string.process_memory_swap_description)
                AppText(
                    text = stringResource(R.string.process_memory_breakdown),
                    modifier = Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                for (category in ProcessMemoryCategory.entries) {
                    MemoryTerm(category.label, category.description)
                }
                AppText(
                    text = stringResource(R.string.process_memory_units_description),
                    color = AppContentColors.secondaryText(),
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            AppDialogAction(onClick = onDismiss) { AppText(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun MemoryTerm(@StringRes label: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppText(stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        AppText(
            text = stringResource(description),
            color = AppContentColors.secondaryText(),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun MemoryTotals(snapshot: ProcessMemorySnapshot, modifier: Modifier = Modifier) {
    val metrics = snapshot.summaryMetrics()
    AppCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppText(
                text = stringResource(R.string.process_memory_scope),
                color = AppContentColors.secondaryText(),
                fontSize = 12.sp,
                tapToCopyEnabled = false,
            )
            AppHorizontalDivider()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 8.dp
                if (maxWidth >= 264.dp * LocalDensity.current.fontScale + gap * 2f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for ((label, kib) in metrics) {
                            val (value, unit) = processMemoryReading(kib)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                AppText(stringResource(label), fontSize = 13.sp)
                                AppText(
                                    text = value,
                                    style = appTabularNumberTextStyle(),
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 26.sp,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 22.sp),
                                    maxLines = 1,
                                )
                                if (unit.isNotEmpty()) {
                                    AppText(unit, color = AppContentColors.secondaryText(), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, kib) in metrics) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(stringResource(label), Modifier.weight(1f), fontSize = 13.sp)
                                AppText(
                                    text = processMemorySizeLabel(kib),
                                    modifier = Modifier.weight(1f),
                                    style = appTabularNumberTextStyle(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryBreakdown(snapshot: ProcessMemorySnapshot, modifier: Modifier = Modifier) {
    val colors = if (isAppInDarkTheme()) darkCategoryColors else lightCategoryColors
    val categories = ProcessMemoryCategory.entries
    val amounts = categories.map { snapshot.breakdownKiB[it] }
    val total = snapshot.pssKiB
    // Android's seven summary buckets partition total PSS. Only plot a complete partition.
    val partition = amounts.filterNotNull().takeIf { values ->
        total > 0 && values.size == categories.size && values.all { it >= 0 } && values.sum() == total
    }
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    AppCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                stringResource(R.string.process_memory_breakdown),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val legend: @Composable (Modifier) -> Unit = { legendModifier ->
                MemoryLegend(
                    amounts = amounts,
                    colors = colors,
                    total = total.takeIf { partition != null },
                    percentFormat = percentFormat,
                    modifier = legendModifier,
                )
            }
            if (partition == null) {
                legend(Modifier.fillMaxWidth())
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 228.dp + memoryLegendColumnsMinWidth * LocalDensity.current.fontScale) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            MemoryPieChart(partition, total, colors, Modifier.size(204.dp))
                            legend(Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MemoryPieChart(
                                partition, total, colors,
                                Modifier
                                    .widthIn(max = 168.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                            legend(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPieChart(
    amounts: List<Int>,
    total: Int,
    colors: List<Color>,
    modifier: Modifier,
) {
    Canvas(modifier) {
        var start = -90f
        amounts.forEachIndexed { index, amount ->
            if (amount > 0) {
                val sweep = 360f * (amount.toFloat() / total)
                val gap = if (amount == total) 0f else minOf(1.6f, sweep * 0.12f)
                drawArc(
                    color = colors[index],
                    startAngle = start + gap / 2f,
                    sweepAngle = sweep - gap,
                    useCenter = true,
                )
                start += sweep
            }
        }
    }
}

@Composable
private fun MemoryLegend(
    amounts: List<Int?>,
    colors: List<Color>,
    total: Int?,
    percentFormat: NumberFormat,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val useColumns = maxWidth >= memoryLegendColumnsMinWidth * fontScale
        val amountWidth = 96.dp * fontScale
        val percentWidth = 48.dp * fontScale
        val gap = 8.dp
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (useColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    AppText(
                        text = stringResource(R.string.process_memory_category_column),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        color = AppContentColors.secondaryText(),
                        fontSize = 12.sp,
                    )
                    MemoryLegendValue(
                        stringResource(R.string.process_memory_amount_column),
                        Modifier.width(amountWidth),
                        secondary = true,
                    )
                    if (total != null) {
                        MemoryLegendValue(
                            stringResource(R.string.process_memory_percent_column),
                            Modifier.width(percentWidth),
                            secondary = true,
                        )
                    }
                }
                AppHorizontalDivider()
            }
            ProcessMemoryCategory.entries.forEachIndexed { index, category ->
                val amount = amounts[index]
                val size = processMemorySizeLabel(amount)
                val percent = if (total != null && amount != null) {
                    percentFormat.format(amount.toDouble() / total)
                } else null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(colors[index], CircleShape))
                        AppText(stringResource(category.label), fontSize = 13.sp)
                    }
                    if (useColumns) {
                        MemoryLegendValue(size, Modifier.width(amountWidth))
                        if (percent != null) {
                            MemoryLegendValue(
                                percent,
                                Modifier.width(percentWidth),
                                secondary = true,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                        ) {
                            MemoryLegendValue(size)
                            if (percent != null) MemoryLegendValue(percent, secondary = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryLegendValue(text: String, modifier: Modifier = Modifier, secondary: Boolean = false) {
    AppText(
        text = text,
        modifier = modifier,
        style = appTabularNumberTextStyle(),
        color = if (secondary) AppContentColors.secondaryText() else AppContentColors.primaryText(),
        fontSize = if (secondary) 12.sp else 13.sp,
        textAlign = TextAlign.End,
    )
}

private val ProcessMemoryCategory.label: Int
    @StringRes get() = when (this) {
        ProcessMemoryCategory.JAVA_HEAP -> R.string.process_memory_java_heap
        ProcessMemoryCategory.NATIVE_HEAP -> R.string.process_memory_native_heap
        ProcessMemoryCategory.CODE -> R.string.process_memory_code
        ProcessMemoryCategory.STACK -> R.string.process_memory_stack
        ProcessMemoryCategory.GRAPHICS -> R.string.process_memory_graphics
        ProcessMemoryCategory.PRIVATE_OTHER -> R.string.process_memory_private_other
        ProcessMemoryCategory.SYSTEM -> R.string.process_memory_system
    }

private val ProcessMemoryCategory.description: Int
    @StringRes get() = when (this) {
        ProcessMemoryCategory.JAVA_HEAP -> R.string.process_memory_java_heap_description
        ProcessMemoryCategory.NATIVE_HEAP -> R.string.process_memory_native_heap_description
        ProcessMemoryCategory.CODE -> R.string.process_memory_code_description
        ProcessMemoryCategory.STACK -> R.string.process_memory_stack_description
        ProcessMemoryCategory.GRAPHICS -> R.string.process_memory_graphics_description
        ProcessMemoryCategory.PRIVATE_OTHER -> R.string.process_memory_private_other_description
        ProcessMemoryCategory.SYSTEM -> R.string.process_memory_system_description
    }
