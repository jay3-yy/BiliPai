package com.android.purebilibili.core.plugin.json

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class JsonRuleAuthoringTest {
    private val source = """{"id":"authoring_test","name":"UP名字过滤","type":"feed","rules":[{"condition":{"and":[{"field":"owner.name","op":"regex","value":"纪录片$"},{"field":"duration","op":"gt","value":60}]},"action":"hide"}]}"""

    @Test fun previewUsesCompoundRulesAndSuffixBoundaryWithoutInstalling() {
        val plugin = JsonPluginManager.parsePluginSource(source)
        val result = previewJsonRules(plugin, """[
            {"owner":{"name":"探索纪录片"},"duration":120},
            {"owner":{"name":"纪录片爱好者"},"duration":120},
            {"owner":{"name":"探索纪录片"},"duration":30}
        ]""")
        assertEquals(listOf(true, false, false), result.map { it.hidden })
        assertEquals(listOf(1), result.first().matchedRules)
        assertTrue(result.drop(1).all { it.matchedRules.isEmpty() })
        assertFalse(JsonPluginManager.plugins.value.any { it.plugin.id == plugin.id })
    }

    @Test fun invalidRegexFieldAndSampleProduceErrorsInsteadOfFalsePasses() {
        assertFailsWith<IllegalArgumentException> {
            JsonPluginManager.parsePluginSource(source.replace("纪录片$", "["))
        }
        assertFailsWith<IllegalArgumentException> {
            JsonPluginManager.parsePluginSource(source.replace("owner.name", "owner.nickname"))
        }
        val plugin = JsonPluginManager.parsePluginSource(source)
        assertFails { previewJsonRules(plugin, """[{"content":"弹幕不是视频样例"}]""") }
    }

    @Test fun danmakuPreviewReportsHiddenAndHighlightedRules() {
        val plugin = JsonPluginManager.parsePluginSource("""{
            "id":"danmaku_preview","name":"弹幕预览","type":"danmaku","rules":[
                {"field":"content","op":"contains","value":"广告","action":"hide"},
                {"field":"content","op":"contains","value":"精彩","action":"highlight","style":{"bold":true}}
            ]
        }""")
        val result = previewJsonRules(plugin, """[{"content":"广告"},{"content":"精彩"},{"content":"普通弹幕"}]""")
        assertTrue(result[0].hidden)
        assertEquals(listOf(1), result[0].matchedRules)
        assertFalse(result[1].hidden)
        assertTrue(result[1].highlighted)
        assertEquals(listOf(2), result[1].matchedRules)
        assertFalse(result[2].highlighted)
    }

    @Test fun localInstallationPersistsAndEditingPreservesDisabledState() = runTest {
        val context = RuntimeEnvironment.getApplication<Application>()
        JsonPluginManager.initialize(context)
        try {
            JsonPluginManager.installFromSource(source)
            assertTrue(JsonPluginManager.plugins.value.single { it.plugin.id == "authoring_test" }.enabled)
            JsonPluginManager.setEnabled("authoring_test", false)
            val updated = source.replace("UP名字过滤", "新的名字")
            JsonPluginManager.installFromSource(updated, "authoring_test")
            val loaded = JsonPluginManager.plugins.value.single { it.plugin.id == "authoring_test" }
            assertEquals("新的名字", loaded.plugin.name)
            assertFalse(loaded.enabled)
            assertTrue(java.io.File(context.filesDir, "json_plugins/authoring_test.json").readText().contains("新的名字"))
            assertFailsWith<IllegalArgumentException> { JsonPluginManager.installFromSource(source) }
            assertFailsWith<IllegalArgumentException> { JsonPluginManager.installFromSource(updated, "another_id") }
        } finally {
            JsonPluginManager.removePlugin("authoring_test")
        }
    }
}
