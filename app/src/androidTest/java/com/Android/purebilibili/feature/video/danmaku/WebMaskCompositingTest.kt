package com.android.purebilibili.feature.video.danmaku

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskData
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskDrawItem
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebMaskCompositingTest {
    @Test
    fun clipAndLuminanceMaskPreserveAlphaWithinTheMappedViewport() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="8" viewBox="0 0 24 8">
              <defs>
                <clipPath id="cut"><rect width="16" height="8"/></clipPath>
                <mask id="fade" maskUnits="userSpaceOnUse" x="0" y="0" width="24" height="8">
                  <rect width="24" height="8" fill="white"/>
                  <rect x="8" width="8" height="8" fill="#808080"/>
                </mask>
              </defs>
              <rect width="24" height="8" clip-path="url(#cut)" mask="url(#fade)"/>
            </svg>
        """.trimIndent()
        val frame = WebMaskParser.parseWindow(webMask(svg), 24, 0, 100).single()
        assertNotNull(frame.visibilityPicture)
        assertEquals(24, frame.sourceWidth)
        assertEquals(8, frame.sourceHeight)

        val mask = MaskDrawItem().apply {
            bindData(MaskData().apply { visibilityPicture = frame.visibilityPicture })
            drawBounds.set(8f, 0f, 32f, 8f)
        }
        val target = Bitmap.createBitmap(40, 8, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(target)
            canvas.drawColor(Color.WHITE)
            mask.draw(canvas, DanmakuConfig())

            assertEquals(255, Color.alpha(target.getPixel(4, 4)))
            assertEquals(255, Color.alpha(target.getPixel(12, 4)))
            assertTrue(Color.alpha(target.getPixel(20, 4)) in 110..145)
            assertEquals(0, Color.alpha(target.getPixel(28, 4)))
            assertEquals(255, Color.alpha(target.getPixel(36, 4)))
        } finally {
            target.recycle()
        }
    }

    private fun webMask(svg: String): ByteArray {
        val payload = svg.encodeToByteArray()
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gzip ->
            gzip.write(ByteBuffer.allocate(12).putInt(payload.size).putInt(0).putInt(0).array())
            gzip.write(payload)
        }
        val chunk = compressed.toByteArray()
        return ByteBuffer.allocate(32 + chunk.size)
            .put("MASK".encodeToByteArray())
            .putInt(1).putInt(0x02000000).putInt(1)
            .putInt(0).putInt(0).putInt(0).putInt(32)
            .put(chunk)
            .array()
    }
}
