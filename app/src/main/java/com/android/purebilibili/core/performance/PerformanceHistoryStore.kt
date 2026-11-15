package com.android.purebilibili.core.performance

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 调用方在 IO 线程持有报告文件锁；每次采集固定一个文件，检查点只更新本次记录。 */
internal class PerformanceHistoryStore(private val directory: File) {
    private val latest = File(directory, "latest.json")
    private val history = File(directory, "history")

    private fun ensureHistory() {
        if (history.exists()) return
        // 首次启用历史时保留升级前的最近报告，不改写其 schema 与测量说明。
        val bytes = if (latest.exists()) AtomicFile(latest).readFully() else null
        val previousId = bytes?.let {
            try {
                performanceHistoryId(JSONObject(it.toString(Charsets.UTF_8)))
            } catch (error: org.json.JSONException) {
                throw IOException("已有性能报告无法解析，未覆盖原数据", error)
            }
        }
        history.mkdirs()
        if (bytes != null) writeAtomic(File(history, "$previousId.json"), bytes)
    }

    fun writeCheckpoint(report: JSONObject) {
        ensureHistory()
        val bytes = report.toString(2).toByteArray(Charsets.UTF_8)
        val target = File(history, "${performanceHistoryId(report)}.json")
        writeAtomic(latest, bytes)
        writeAtomic(target, bytes)
        val files = historyFiles()
        files.filter { it != target }.sortedBy { it.name }.take((files.size - PERFORMANCE_HISTORY_LIMIT).coerceAtLeast(0))
            .forEach { file ->
                if (!file.delete()) throw IOException("无法清理过期性能记录：${file.name}")
            }
    }

    fun read(): List<PerformanceHistoryEntry> {
        ensureHistory()
        return historyFiles().map { file ->
            parsePerformanceHistory(JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8)))
        }.sortedByDescending { it.startedWallMs }
    }

    private fun historyFiles(): List<File> =
        (history.listFiles() ?: throw IOException("无法读取性能历史目录")).filter { it.extension == "json" }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile!!.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: IOException) {
            atomic.failWrite(stream)
            throw error
        }
    }
}
