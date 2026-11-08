package com.android.purebilibili.core.plugin.json

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class JsonSourceEditingTest {
    @Test fun syntaxErrorRetainsParserLocationAndExcludesInputDump() {
        val source = """
            {
              "id": "example"
              "name": "缺少逗号",
              "type": "feed",
              "rules": []
            }
        """.trimIndent()
        val error = assertFails { JsonPluginManager.parsePluginSource(source) }
        val issue = diagnoseJsonSource(source, error)
        assertEquals(3, issue.line)
        assertNotNull(issue.column)
        assertNotNull(issue.span)
        assertFalse(issue.message.contains("JSON input:"))
    }

    @Test fun regexProblemLocatesSecondNestedValueInsteadOfFirstRepeatedField() {
        val source = """
            {
              "id": "example", "name": "规则", "type": "feed",
              "rules": [
                {"field":"title","op":"regex","value":"normal","action":"hide"},
                {"condition":{"or":[{"field":"owner.name","op":"regex","value":"["}]},"action":"hide"}
              ]
            }
        """.trimIndent()
        val error = assertFails { JsonPluginManager.parsePluginSource(source) }
        val issue = diagnoseJsonSource(source, error)
        val span = assertNotNull(issue.span)
        assertEquals(5, issue.line)
        assertEquals(source.lastIndexOf("\"[\""), span.start)
        assertEquals("\"[\"", source.substring(span.start, span.end))
    }

    @Test fun sampleTypeErrorPointsIntoOriginalArray() {
        val plugin = JsonPluginManager.parsePluginSource("""{"id":"example","name":"规则","type":"feed","rules":[{"field":"title","op":"contains","value":"test","action":"hide"}]}""")
        val samples = """
            [
              {"title":"有效样例","duration":10},
              {"title":"类型错误","duration":"wrong"}
            ]
        """.trimIndent()
        val issue = diagnoseJsonSource(samples, assertFails { previewJsonRules(plugin, samples) })
        assertEquals(3, issue.line)
        assertNotNull(issue.span)
    }

    @Test fun completionReplacesOnlyCurrentStringAndPreservesSuffixAndOtherRules() {
        val source = """{"rules":[{"field":"owner.name"},{"field":"owner.n","op":"regex","value":"纪录片$"}]}"""
        val cursor = source.indexOf("owner.n\"") + "owner.n".length
        val completion = completeJsonSource(source, cursor, "feed").single { it.label == "owner.name" }
        val edit = applyJsonCompletion(source, completion)
        assertEquals(source.replace("\"owner.n\"", "\"owner.name\""), edit.text)
        assertEquals('"', edit.text[edit.cursor])
        assertEquals('e', edit.text[edit.cursor - 1])
    }

    @Test fun completionHandlesUnclosedStringsAndCurrentPluginType() {
        val source = """{"type":"danmaku","rules":[{"action":"hi"""
        val suggestions = completeJsonSource(source, source.length, "feed")
        assertEquals(listOf("hide", "highlight"), suggestions.map { it.label })
        val edit = applyJsonCompletion(source, suggestions.single { it.label == "highlight" })
        assertTrue(edit.text.endsWith("\"highlight\""))
        val fieldSource = """{"type":"danmaku","rules":[{"field":"co"}]}"""
        assertEquals(listOf("content"), completeJsonSource(fieldSource, fieldSource.indexOf("co\"") + 2, "feed").map { it.label })
    }

    @Test fun descriptionTextAndCursorOutsideStringDoNotOfferRuleCompletions() {
        val source = """{"description":"field op example","rules":[]}"""
        assertTrue(completeJsonSource(source, source.indexOf("example"), "feed").isEmpty())
        assertTrue(completeJsonSource(source, source.length, "feed").isEmpty())
    }
}
