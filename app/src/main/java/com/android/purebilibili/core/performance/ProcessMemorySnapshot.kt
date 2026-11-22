package com.android.purebilibili.core.performance

import android.os.Debug
import java.io.File
import java.io.IOException

internal enum class ProcessMemoryCategory(val stat: String) {
    JAVA_HEAP("summary.java-heap"),
    NATIVE_HEAP("summary.native-heap"),
    CODE("summary.code"),
    STACK("summary.stack"),
    GRAPHICS("summary.graphics"),
    PRIVATE_OTHER("summary.private-other"),
    SYSTEM("summary.system"),
}

internal data class ProcessMemorySnapshot(
    val pssKiB: Int,
    val rssKiB: Int?,
    val swapKiB: Int?,
    val breakdownKiB: Map<ProcessMemoryCategory, Int?>,
)

internal fun readProcessMemorySnapshot(): ProcessMemorySnapshot {
    val memory = Debug.MemoryInfo()
    Debug.getMemoryInfo(memory)
    return ProcessMemorySnapshot(
        pssKiB = memory.totalPss,
        rssKiB = readResidentMemoryKiB(),
        swapKiB = memory.getMemoryStat("summary.total-swap")?.toIntOrNull(),
        breakdownKiB = ProcessMemoryCategory.entries.associateWith { category ->
            memory.getMemoryStat(category.stat)?.toIntOrNull()
        },
    )
}

// VmRSS is the kernel's resident-memory estimate for the current process.
private fun readResidentMemoryKiB(): Int? = try {
    File("/proc/self/status").useLines { lines ->
        lines.firstOrNull { it.startsWith("VmRSS:") }
            ?.substringAfter(':')?.trimStart()?.substringBefore(' ')?.toIntOrNull()
    }
} catch (_: IOException) {
    // Some kernels restrict procfs access. Preserve the Android memory summary.
    null
}
