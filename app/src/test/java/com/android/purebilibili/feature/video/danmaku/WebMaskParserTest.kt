package com.android.purebilibili.feature.video.danmaku

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebMaskParserTest {
    @Test
    fun `index parser reads big endian chunk times and bounded offsets`() {
        val bytes = ByteArray(80)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put("MASK".encodeToByteArray())
        buffer.putInt(1)
        buffer.putInt(0)
        buffer.putInt(2)
        buffer.putInt(9).putInt(0).putInt(7).putInt(48)
        buffer.putInt(8).putInt(10_000).putInt(6).putInt(64)

        val index = WebMaskParser.parseIndex(bytes)

        assertEquals(2, index.size)
        assertEquals(WebMaskChunkIndex(0L, 48, 64), index[0])
        assertEquals(WebMaskChunkIndex(10_000L, 64, 80), index[1])
    }

    @Test
    fun `index parser rejects malformed header and descending offsets`() {
        assertTrue(WebMaskParser.parseIndex(ByteArray(16)).isEmpty())

        val bytes = ByteArray(64)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put("MASK".encodeToByteArray())
        buffer.putInt(1)
        buffer.putInt(0)
        buffer.putInt(2)
        buffer.putLong(0L)
        buffer.putLong(56L)
        buffer.putLong(10_000L)
        buffer.putLong(48L)
        assertTrue(WebMaskParser.parseIndex(bytes).isEmpty())
    }

    @Test
    fun `chunk frame parser reads 12-byte headers without dropping first frame`() {
        val svg1 = """<svg viewBox="0 0 1280 720"><path d="M0 0 L10 0 L10 10 Z"/></svg>"""
        val svg2 = """<svg viewBox="0 0 1280 720"><path fill-rule="evenodd" d="M20 20 L40 20 L40 40 Z"/></svg>"""
        val inflated = packFrames(
            120L to "data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg1.toByteArray())}",
            460L to "data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg2.toByteArray())}",
        )

        val frames = WebMaskParser.parseChunkFrames(inflated)

        assertEquals(2, frames.size)
        assertEquals(120L, frames[0].timeMs)
        assertEquals(svg1, frames[0].svg)
        assertEquals(460L, frames[1].timeMs)
        assertEquals(svg2, frames[1].svg)
    }

    @Test
    fun `svg geometry extracts viewBox dimensions, path data, and evenodd fill rule`() {
        val svg = """<svg viewBox="0 0 640 360"><path fill-rule="evenodd" d="M0 0 L10 10 Z"/><path d="M5 5 L8 8 Z"/></svg>"""
        val geometry = parseWebMaskSvg(svg)

        assertNotNull(geometry)
        assertEquals(640, geometry.width)
        assertEquals(360, geometry.height)
        assertEquals(
            listOf(WebMaskOutline.PathData("M0 0 L10 10 Z"), WebMaskOutline.PathData("M5 5 L8 8 Z")),
            geometry.shapes.map { it.outline },
        )
        assertEquals(listOf(true, false), geometry.shapes.map { it.evenOdd })

        val nonzero = parseWebMaskSvg("""<svg width="800" height="450"><path d="M0 0 L1 1 Z"/></svg>""")
        assertNotNull(nonzero)
        assertEquals(800, nonzero.width)
        assertEquals(450, nonzero.height)
        assertFalse(nonzero.shapes.single().evenOdd)
    }

    @Test
    fun `svg geometry extracts rect, circle, and polygon primitives`() {
        val svg = """
            <svg viewBox="0 0 1280 720">
              <rect x="10" y="20" width="100" height="50"/>
              <circle cx="300" cy="200" r="40"/>
              <polygon points="0,0 100,0 50,80"/>
            </svg>
        """.trimIndent()
        val geometry = parseWebMaskSvg(svg)

        assertNotNull(geometry)
        assertEquals(
            listOf(
                WebMaskOutline.Rect(10f, 20f, 100f, 50f),
                WebMaskOutline.Ellipse(300f, 200f, 40f, 40f),
                WebMaskOutline.Polygon(listOf(0f, 0f, 100f, 0f, 50f, 80f)),
            ),
            geometry.shapes.map { it.outline },
        )
    }

    @Test
    fun `svg geometry extracts potrace translate and inverted-y scale transform`() {
        val svg = """
            <svg width="180.000000px" height="320.000000px" viewBox="0 0 180.000000 320.000000">
              <g transform="translate(0.000000,320.000000) scale(0.100000,-0.100000)" fill="#000000">
                <path d="M0 1605 l0 -1595 900 0z"/>
              </g>
            </svg>
        """.trimIndent()
        val geometry = parseWebMaskSvg(svg)

        assertNotNull(geometry)
        assertEquals(180, geometry.width)
        assertEquals(320, geometry.height)
        assertEquals(WebMaskTransform(a = 0.1f, d = -0.1f, y = 320f), geometry.shapes.single().transform)
    }

    @Test
    fun `viewBox and nested transforms affect only the shapes in scope`() {
        val svg = """
            <svg width="200" height="100" viewBox="10 20 100 50">
              <g transform="translate(5 6) scale(2)" fill-rule="evenodd">
                <path transform="matrix(1 0 0 1 3 4)" d="M0 0 L10 10 Z"/>
              </g>
              <rect x="10" y="20" width="50" height="30"/>
              <defs><circle r="10"/></defs>
            </svg>
        """.trimIndent()
        val geometry = assertNotNull(parseWebMaskSvg(svg))

        assertEquals(200, geometry.width)
        assertEquals(100, geometry.height)
        assertEquals(2, geometry.shapes.size)
        assertEquals(WebMaskTransform(a = 4f, d = 4f, x = 2f, y = -12f), geometry.shapes[0].transform)
        assertTrue(geometry.shapes[0].evenOdd)
        assertEquals(WebMaskTransform(a = 2f, d = 2f, x = -20f, y = -40f), geometry.shapes[1].transform)
        assertFalse(geometry.shapes[1].evenOdd)
    }

    private fun packFrames(vararg entries: Pair<Long, String>): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        entries.forEach { (timeMs, payload) ->
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            header.clear()
            header.putInt(payloadBytes.size)
            header.putInt(0x10203040)
            header.putInt(timeMs.toInt())
            out.write(header.array())
            out.write(payloadBytes)
        }
        return out.toByteArray()
    }
}
