package com.android.purebilibili.core.performance

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.R

internal fun ProcessMemorySnapshot.summaryMetrics(): List<Pair<Int, Int?>> = listOf(
    R.string.process_memory_pss to pssKiB,
    R.string.process_memory_rss to rssKiB,
    R.string.process_memory_swap to swapKiB,
)

@Composable
internal fun processMemoryReading(kib: Int?): Pair<String, String> = when {
    kib == null || kib < 0 -> stringResource(R.string.process_memory_unavailable) to ""
    kib < 1024 -> stringResource(R.string.process_memory_whole, kib) to "KiB"
    else -> stringResource(R.string.process_memory_decimal, kib / 1024.0) to "MiB"
}

@Composable
internal fun processMemorySizeLabel(kib: Int?): String {
    val (value, unit) = processMemoryReading(kib)
    return if (unit.isEmpty()) value else stringResource(R.string.process_memory_value_with_unit, value, unit)
}
