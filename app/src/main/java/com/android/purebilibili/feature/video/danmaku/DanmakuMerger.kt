package com.android.purebilibili.feature.video.danmaku

import com.android.purebilibili.danmaku.parser.*

import com.android.purebilibili.danmaku.engine.DanmakuItem
import android.util.Log

/**
 * 弹幕合并工具
 *
 * 用于合并短时间内出现的重复弹幕，减少屏幕遮挡
 */
object DanmakuMerger {
    private const val TAG = "DanmakuMerger"

    /**
     * 合并重复弹幕
     *
     * @param list 原始弹幕列表 (必须已按时间排序)
     * @param intervalMs 以每批首条为起点的固定时间窗口 (毫秒)，默认 15000ms
     * @param countThreshold 显示 xN 计数的最小重复数，默认 2
     * @return Pair(标准弹幕列表, 高级合并弹幕列表)
     *         标准弹幕继续在原层渲染；不再将重复弹幕转换为黄色高级弹幕。
     */
    fun merge(
        list: List<DanmakuItem>,
        intervalMs: Long = 15_000,
        countThreshold: Int = 2,
        suppressThreshold: Int = 0,
        mergeIntoSingle: Boolean = true,
    ): Pair<List<DanmakuItem>, List<AdvancedDanmakuData>> {
        if (list.isEmpty()) return Pair(list, emptyList())
        val normalizedCountThreshold = countThreshold.coerceAtLeast(2)
        val normalizedSuppressThreshold = suppressThreshold.coerceAtLeast(0)

        val standardList = mutableListOf<DanmakuItem>()
        
        // 1. 分离 TextData 和其他数据
        val textDanmakus = mutableListOf<DanmakuItem>()
        
        textDanmakus.addAll(list)
        
        // 2. 对 TextData 按内容分组
        val groupedByContent = textDanmakus.groupBy { it.text }
        
        var mergedCount = 0
        
        groupedByContent.forEach { (text, items) ->
            if (items.size == 1) {
                // 没有重复的，保持为标准弹幕
                standardList.add(items[0])
            } else {
                // 对同一内容的弹幕进行时间聚类
                var currentBatch = mutableListOf<DanmakuItem>()

                for (item in items) {
                    if (currentBatch.isEmpty()) {
                        currentBatch.add(item)
                    } else {
                        val firstItem = currentBatch.first()
                        if (item.showAtTime - firstItem.showAtTime <= intervalMs) {
                            currentBatch.add(item)
                        } else {
                            // 结算上一批
                            processBatch(currentBatch, standardList, normalizedCountThreshold, normalizedSuppressThreshold, mergeIntoSingle)
                            if (currentBatch.size > 1) mergedCount += (currentBatch.size - 1)
                            
                            // 开启新的一批
                            currentBatch = mutableListOf()
                            currentBatch.add(item)
                        }
                    }
                }
                
                // 结算最后一批
                if (currentBatch.isNotEmpty()) {
                    processBatch(currentBatch, standardList, normalizedCountThreshold, normalizedSuppressThreshold, mergeIntoSingle)
                    if (currentBatch.size > 1) mergedCount += (currentBatch.size - 1)
                }
            }
        }
        
        // 3. 重新按时间排序
        standardList.sortBy { it.showAtTime }

        Log.d(TAG, "Merged result: ${standardList.size} standard. Reduced $mergedCount items.")

        return Pair(standardList, emptyList())
    }
    
    /**
     * 处理一批重复弹幕
     */
    private fun processBatch(
        batch: List<DanmakuItem>,
        standardOut: MutableList<DanmakuItem>,
        countThreshold: Int,
        suppressThreshold: Int = 0,
        mergeIntoSingle: Boolean = true,
    ) {
        if (batch.isEmpty()) return
        if (suppressThreshold > 0 && batch.size >= suppressThreshold) {
            return
        }
        
        if (batch.size == 1) {
            standardOut.add(batch[0])
        } else if (!mergeIntoSingle) {
            standardOut.addAll(batch)
        } else {
            // 继续以标准弹幕形式存在，但带上 xN 标记，不再升级为中央黄色高级弹幕。
            standardOut.add(combineBatch(batch, countThreshold))
        }
    }
    /**
     * 将一批 TextData 合并为一个
     */
    private fun combineBatch(batch: List<DanmakuItem>, countThreshold: Int): DanmakuItem {
        if (batch.size == 1) return batch[0]
        
        // 取第一个作为基准
        val base = batch[0]
        val count = batch.size
        
        val mergedText = base.copy().also { it.duplicateCount = count }
        mergedText.mergedOriginalText = base.mergedOriginalText ?: base.text
        mergedText.likeCount = batch.sumOf { it.likeCount }
        mergedText.text = if (count >= countThreshold) {
            "${base.text} x$count"
        } else {
            base.text
        }
        mergedText.showAtTime = base.showAtTime
        mergedText.textColor = base.textColor
        mergedText.textSizeScale = base.textSizeScale
        mergedText.textSize = base.textSize
        mergedText.layerType = base.layerType
        mergedText.typeface = base.typeface
            
        return mergedText
    }
}
