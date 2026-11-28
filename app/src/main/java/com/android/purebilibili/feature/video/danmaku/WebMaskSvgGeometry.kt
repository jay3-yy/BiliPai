package com.android.purebilibili.feature.video.danmaku

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

// SVG matrix(a b c d x y), applied to column vectors.
internal data class WebMaskTransform(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f, val d: Float = 1f,
    val x: Float = 0f, val y: Float = 0f,
) {
    operator fun times(other: WebMaskTransform) = WebMaskTransform(
        a * other.a + c * other.b,
        b * other.a + d * other.b,
        a * other.c + c * other.d,
        b * other.c + d * other.d,
        a * other.x + c * other.y + x,
        b * other.x + d * other.y + y,
    )
}

internal sealed interface WebMaskOutline {
    data class PathData(val value: String) : WebMaskOutline
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) : WebMaskOutline
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : WebMaskOutline
    data class Polygon(val points: List<Float>) : WebMaskOutline
}

internal data class WebMaskSvgShape(
    val outline: WebMaskOutline,
    val transform: WebMaskTransform,
    val evenOdd: Boolean,
)

internal data class WebMaskSvgGeometry(val width: Int, val height: Int, val shapes: List<WebMaskSvgShape>)

private data class SvgElementState(
    val transform: WebMaskTransform = WebMaskTransform(),
    val evenOdd: Boolean = false,
    val fill: String = "black",
    val fillOpacity: Float = 1f,
    val visible: Boolean = true,
    val renderable: Boolean = true,
)

private val svgReaders = ThreadLocal.withInitial {
    SAXParserFactory.newInstance().newSAXParser().xmlReader.apply {
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }
}

internal fun parseWebMaskSvg(svg: String): WebMaskSvgGeometry? {
    val shapes = ArrayList<WebMaskSvgShape>()
    val states = ArrayDeque<SvgElementState>()
    var width = 1920
    var height = 1080
    val handler = object : DefaultHandler() {
        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            val tag = qName.substringAfter(':')
            val parent = states.lastOrNull() ?: SvgElementState()
            var transform = parent.transform * parseTransform(attrs.getValue("transform"))
            if (states.isEmpty()) {
                if (tag != "svg") throw SAXException("Expected an SVG root")
                val viewBox = attrs.getValue("viewBox")?.let(::svgNumbers)
                    ?.takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f }
                val viewportWidth = attrs.length("width")?.takeIf { it > 0f } ?: viewBox?.get(2) ?: 1920f
                val viewportHeight = attrs.length("height")?.takeIf { it > 0f } ?: viewBox?.get(3) ?: 1080f
                width = viewportWidth.roundToInt().coerceAtLeast(1)
                height = viewportHeight.roundToInt().coerceAtLeast(1)
                if (viewBox != null) {
                    transform *= viewBoxTransform(viewBox, viewportWidth, viewportHeight, attrs.getValue("preserveAspectRatio"))
                }
            }

            val style = attrs.getValue("style")?.split(';')?.mapNotNull { entry ->
                val separator = entry.indexOf(':')
                if (separator < 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
            }?.toMap()
            fun presentation(name: String): String? =
                (style?.get(name) ?: attrs.getValue(name))?.takeUnless { it == "inherit" }
            val state = SvgElementState(
                transform = transform,
                evenOdd = presentation("fill-rule")?.let { it == "evenodd" } ?: parent.evenOdd,
                fill = presentation("fill") ?: parent.fill,
                fillOpacity = presentation("fill-opacity")?.toFloatOrNull() ?: parent.fillOpacity,
                visible = presentation("visibility")?.let { it == "visible" } ?: parent.visible,
                renderable = parent.renderable && tag !in nonRenderingTags &&
                    presentation("display") != "none" && presentation("opacity")?.toFloatOrNull() != 0f,
            )
            states.addLast(state)
            if (!state.renderable || !state.visible || state.fill == "none" ||
                state.fill == "transparent" || state.fillOpacity <= 0f
            ) return
            parseOutline(tag, attrs)?.let { shapes += WebMaskSvgShape(it, state.transform, state.evenOdd) }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            states.removeLast()
        }
    }
    try {
        val reader = svgReaders.get()
        reader.contentHandler = handler
        reader.errorHandler = handler
        try {
            reader.parse(InputSource(StringReader(svg)))
        } finally {
            reader.contentHandler = null
            reader.errorHandler = null
        }
    } catch (_: SAXException) {
        return null
    }
    return WebMaskSvgGeometry(width, height, shapes)
}

private val nonRenderingTags = setOf("defs", "clipPath", "mask", "pattern", "marker", "symbol")
private val numberPattern = Regex("""[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?""")
private val transformPattern = Regex("""([A-Za-z]+)\s*\(([^)]*)\)""")

private fun svgNumbers(value: String): List<Float> =
    numberPattern.findAll(value).mapNotNull { it.value.toFloatOrNull()?.takeIf(Float::isFinite) }.toList()

private fun Attributes.length(name: String): Float? =
    getValue(name)?.trim()?.removeSuffix("px")?.toFloatOrNull()?.takeIf(Float::isFinite)

private fun parseOutline(tag: String, attrs: Attributes): WebMaskOutline? = when (tag) {
    "path" -> attrs.getValue("d")?.takeIf(String::isNotBlank)?.let { WebMaskOutline.PathData(it) }
    "rect" -> {
        val width = attrs.length("width") ?: 0f
        val height = attrs.length("height") ?: 0f
        if (width > 0f && height > 0f) {
            WebMaskOutline.Rect(attrs.length("x") ?: 0f, attrs.length("y") ?: 0f, width, height)
        } else null
    }
    "circle", "ellipse" -> {
        val r = attrs.length("r")
        val rx = (if (tag == "circle") r else attrs.length("rx")) ?: 0f
        val ry = (if (tag == "circle") r else attrs.length("ry")) ?: 0f
        if (rx > 0f && ry > 0f) {
            WebMaskOutline.Ellipse(attrs.length("cx") ?: 0f, attrs.length("cy") ?: 0f, rx, ry)
        } else null
    }
    "polygon", "polyline" -> attrs.getValue("points")?.let(::svgNumbers)
        ?.takeIf { it.size >= 6 && it.size % 2 == 0 }?.let { WebMaskOutline.Polygon(it) }
    else -> null
}

private fun viewBoxTransform(box: List<Float>, width: Float, height: Float, aspect: String?): WebMaskTransform {
    val (left, top, boxWidth, boxHeight) = box
    val sx = width / boxWidth
    val sy = height / boxHeight
    val parts = aspect.orEmpty().trim().split(Regex("\\s+")).filterNot { it == "defer" }
    val align = parts.firstOrNull().orEmpty()
    if (align == "none") return WebMaskTransform(a = sx, d = sy, x = -left * sx, y = -top * sy)
    val scale = if (parts.getOrNull(1) == "slice") maxOf(sx, sy) else minOf(sx, sy)
    val xAlign = when {
        align.startsWith("xMin") -> 0f
        align.startsWith("xMax") -> 1f
        else -> 0.5f
    }
    val yAlign = when {
        align.endsWith("YMin") -> 0f
        align.endsWith("YMax") -> 1f
        else -> 0.5f
    }
    return WebMaskTransform(
        a = scale, d = scale,
        x = (width - boxWidth * scale) * xAlign - left * scale,
        y = (height - boxHeight * scale) * yAlign - top * scale,
    )
}

private fun parseTransform(value: String?): WebMaskTransform {
    var result = WebMaskTransform()
    for (match in transformPattern.findAll(value.orEmpty())) {
        val args = svgNumbers(match.groupValues[2])
        val first = args.firstOrNull() ?: continue
        val second = args.getOrNull(1)
        val next = when (match.groupValues[1]) {
            "matrix" -> if (args.size == 6) WebMaskTransform(args[0], args[1], args[2], args[3], args[4], args[5]) else continue
            "translate" -> WebMaskTransform(x = first, y = second ?: 0f)
            "scale" -> WebMaskTransform(a = first, d = second ?: first)
            "rotate" -> {
                val radians = Math.toRadians(first.toDouble())
                val sine = sin(radians).toFloat()
                val cosine = cos(radians).toFloat()
                val rotation = WebMaskTransform(cosine, sine, -sine, cosine)
                if (args.size == 3) {
                    WebMaskTransform(x = args[1], y = args[2]) * rotation *
                        WebMaskTransform(x = -args[1], y = -args[2])
                } else rotation
            }
            "skewX" -> WebMaskTransform(c = tan(Math.toRadians(first.toDouble())).toFloat())
            "skewY" -> WebMaskTransform(b = tan(Math.toRadians(first.toDouble())).toFloat())
            else -> continue
        }
        result *= next
    }
    return result
}
