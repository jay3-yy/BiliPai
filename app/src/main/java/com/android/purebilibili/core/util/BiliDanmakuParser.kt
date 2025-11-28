package com.android.purebilibili.core.util

import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.IDataSource
import master.flame.danmaku.danmaku.util.DanmakuUtils
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

class BiliDanmakuParser : BaseDanmakuParser() {

    override fun parse(): IDanmakus {
        // 修正逻辑：mDataSource 是一个 Wrapper，需要调用 data() 才能拿到 InputStream
        if (mDataSource != null && mDataSource.data() is InputStream) {
            val source = mDataSource.data() as InputStream
            try {
                val factory = SAXParserFactory.newInstance()
                val parser = factory.newSAXParser()
                val handler = XmlHandler()
                parser.parse(InputSource(source), handler)
                return handler.danmakus
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Danmakus()
    }

    inner class XmlHandler : DefaultHandler() {
        val danmakus = Danmakus()
        private var item: BaseDanmaku? = null
        private var index = 0

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (qName.equals("d", ignoreCase = true)) {
                // p属性格式: 出现时间,模式,字号,颜色,发送时间,弹幕池,用户Hash,dmid
                val p = attributes.getValue("p")?.split(",") ?: return
                if (p.isNotEmpty()) {
                    val time = (p[0].toFloat() * 1000).toLong()
                    val type = p[1].toInt()
                    val textSize = p[2].toFloat()
                    val color = p[3].toInt() or -0x1000000

                    // 1:滚动 4:底端 5:顶端
                    val itemType = when (type) {
                        4 -> BaseDanmaku.TYPE_FIX_BOTTOM
                        5 -> BaseDanmaku.TYPE_FIX_TOP
                        else -> BaseDanmaku.TYPE_SCROLL_RL
                    }

                    item = mContext.mDanmakuFactory.createDanmaku(itemType, mContext)?.apply {
                        this.time = time
                        this.textSize = textSize * (mContext.displayer.density - 0.6f)
                        this.textColor = color
                        this.textShadowColor = -0x1000000
                        this.index = this@XmlHandler.index++
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            item?.let {
                val text = String(ch, start, length)
                DanmakuUtils.fillText(it, text)
                danmakus.addItem(it)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (qName.equals("d", ignoreCase = true)) {
                item = null
            }
        }
    }
}

// 👇👇👇 新增这个包装类，用来解决类型不匹配报错 👇👇👇
class StreamDataSource(private val stream: InputStream) : IDataSource<InputStream> {
    override fun data(): InputStream = stream
    override fun release() {
        try { stream.close() } catch (e: Exception) {}
    }
}