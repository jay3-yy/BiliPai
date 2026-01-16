// 文件路径: feature/video/danmaku/DanmakuParser.kt
package com.android.purebilibili.feature.video.danmaku

import android.util.Log
import android.util.Xml
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

/**
 * 弹幕解析器
 * 
 * 支持两种格式：
 * 1. XML 格式 (旧版 API)
 * 2. Protobuf 格式 (新版 seg.so API)
 */
object DanmakuParser {
    
    private const val TAG = "DanmakuParser"
    
    /**
     *  [新增] 解析 Protobuf 弹幕数据 (推荐)
     * 
     * @param segments Protobuf 分段数据列表
     * @return DanmakuData 列表（TextData）
     */
    fun parseProtobuf(segments: List<ByteArray>): List<DanmakuData> {
        val danmakuList = mutableListOf<DanmakuData>()
        
        if (segments.isEmpty()) {
            Log.w(TAG, " No segments to parse")
            return danmakuList
        }
        
        Log.d(TAG, " Parsing ${segments.size} Protobuf segments...")
        
        var totalParsed = 0
        for ((index, segment) in segments.withIndex()) {
            try {
                val elems = DanmakuProto.parse(segment)
                Log.d(TAG, " Segment ${index + 1}: parsed ${elems.size} danmakus")
                
                for (elem in elems) {
                    val textData = createTextDataFromProto(elem)
                    if (textData != null) {
                        danmakuList.add(textData)
                        totalParsed++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, " Failed to parse segment ${index + 1}: ${e.message}")
            }
        }
        
        //  [关键] 按时间排序 - DanmakuRenderEngine 需要有序数据
        danmakuList.sortBy { it.showAtTime }
        
        // 统计信息
        if (danmakuList.isNotEmpty()) {
            val times = danmakuList.map { it.showAtTime }
            val minTime = times.minOrNull() ?: 0
            val maxTime = times.maxOrNull() ?: 0
            val first10s = danmakuList.count { it.showAtTime < 10000 }
            Log.w(TAG, " Protobuf parsed $totalParsed danmakus (sorted) | Time range: ${minTime}ms ~ ${maxTime}ms | First 10s: $first10s items")
        } else {
            Log.w(TAG, " No danmakus parsed from Protobuf!")
        }
        
        return danmakuList
    }
    
    /**
     * 从 Protobuf DanmakuElem 创建 TextData
     */
    private fun createTextDataFromProto(elem: DanmakuProto.DanmakuElem): TextData? {
        if (elem.content.isEmpty()) return null
        
        val layerType = mapLayerType(elem.mode)
        val colorWithAlpha = elem.color or 0xFF000000.toInt()  // 添加透明度
        
        //  调试日志：查看前几条弹幕的数据
        val debugCount = 5
        if (debugLogCount < debugCount) {
            Log.w(TAG, "📝 Proto #${debugLogCount + 1}: time=${elem.progress}ms, mode=${elem.mode}->layer=$layerType, color=${Integer.toHexString(colorWithAlpha)}, size=${elem.fontsize}, text='${elem.content.take(20)}'")
            debugLogCount++
        }
        
        return TextData().apply {
            this.text = elem.content
            this.showAtTime = elem.progress.toLong()  // progress 已经是毫秒
            this.layerType = layerType
            this.textColor = colorWithAlpha
            //  [修复] 不设置 textSize，让引擎使用 config.text.size（支持 fontScale 调节）
        }
    }
    
    private var debugLogCount = 0  // 用于限制调试日志数量
    
    /**
     * 解析 XML 弹幕数据 (旧版 API，作为后备方案)
     * 
     * @param rawData 原始 XML 数据
     * @return DanmakuData 列表（TextData）
     */
    fun parse(rawData: ByteArray): List<DanmakuData> {
        val danmakuList = mutableListOf<DanmakuData>()
        
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(rawData), "UTF-8")
            
            var eventType = parser.eventType
            var count = 0
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "d") {
                    val pAttr = parser.getAttributeValue(null, "p")
                    parser.next()
                    val content = if (parser.eventType == XmlPullParser.TEXT) parser.text else ""
                    
                    if (pAttr != null && content.isNotEmpty()) {
                        val danmaku = createTextData(pAttr, content)
                        if (danmaku != null) {
                            danmakuList.add(danmaku)
                            count++
                            //  用 Log.w 确保可见
                            if (count <= 5) {
                                Log.w(TAG, "📝 Danmaku #$count: time=${danmaku.showAtTime}ms, layer=${danmaku.layerType}, color=${String.format("#%08X", danmaku.textColor)}, text='${danmaku.text?.take(20)}'")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            //  统计弹幕时间分布
            if (danmakuList.isNotEmpty()) {
                val times = danmakuList.map { it.showAtTime }
                val minTime = times.minOrNull() ?: 0
                val maxTime = times.maxOrNull() ?: 0
                val first10s = danmakuList.count { it.showAtTime < 10000 }
                Log.w(TAG, " XML parsed $count danmakus | Time range: ${minTime}ms ~ ${maxTime}ms | First 10s: $first10s items")
            } else {
                Log.w(TAG, " No danmakus parsed from XML!")
            }
        } catch (e: Exception) {
            Log.e(TAG, " XML parse error: ${e.message}", e)
        }
        
        return danmakuList
    }
    
    /**
     * 从属性字符串创建 TextData
     * 
     * @param pAttr p 属性值 "time,type,fontSize,color,..."
     * @param content 弹幕文本内容
     * @return TextData 对象，解析失败返回 null
     */
    private fun createTextData(pAttr: String, content: String): TextData? {
        try {
            val parts = pAttr.split(",")
            if (parts.size < 4) return null
            
            val timeSeconds = parts[0].toFloatOrNull() ?: 0f
            val timeMs = (timeSeconds * 1000).toLong()  // 转换为毫秒
            val biliType = parts[1].toIntOrNull() ?: 1
            val fontSize = parts[2].toFloatOrNull() ?: 25f
            val colorInt = parts[3].toLongOrNull() ?: 0xFFFFFF
            
            // 映射弹幕类型到 DanmakuRenderEngine 的 LayerType 常量
            val layerType = mapLayerType(biliType)
            
            return TextData().apply {
                this.text = content
                this.showAtTime = timeMs
                this.layerType = layerType
                // 设置颜色（带透明度）
                this.textColor = (colorInt.toInt() or 0xFF000000.toInt())
                //  [修复] 不设置 textSize，让引擎使用 config.text.size（支持 fontScale 调节）
            }
        } catch (e: Exception) {
            Log.w(TAG, " Failed to parse danmaku: ${e.message}")
            return null
        }
    }
    
    /**
     * 映射 Bilibili 弹幕类型到 DanmakuRenderEngine LayerType
     * 
     * 使用 DanmakuRenderEngine 的官方常量
     * 
     * Bilibili 类型:
     * 1,2,3 = 滚动弹幕（从右到左）
     * 4 = 底部弹幕
     * 5 = 顶部弹幕
     * 6 = 逆向滚动（从左到右）- 不常用
     * 7 = 高级弹幕（定位/动画）- 暂不支持
     */
    private fun mapLayerType(biliType: Int): Int = when (biliType) {
        1, 2, 3, 6 -> LAYER_TYPE_SCROLL    // 滚动弹幕（包括逆向）
        4 -> LAYER_TYPE_BOTTOM_CENTER      // 底部固定
        5 -> LAYER_TYPE_TOP_CENTER         // 顶部固定
        else -> LAYER_TYPE_SCROLL          // 默认滚动
    }
}

