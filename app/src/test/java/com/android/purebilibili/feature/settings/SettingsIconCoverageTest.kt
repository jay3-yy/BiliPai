package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 设置行图标覆盖约束：所有偏好行（开关、滑杆、通用行、网格项、单选）调用点必须传语义图标。
 * 图标槽位是可选参数，历史上一度出现"开关行有图标、单选行没有"的同页断层；该测试防止回归。
 */
class SettingsIconCoverageTest {

    @Test
    fun settingsPreferenceRows_alwaysPassSemanticIcon() {
        val componentNames = listOf(
            "AppSwitchPreference",
            "AppSliderPreference",
            "AppPreference",
            "AppPreferenceGridItem",
            "SettingsSingleChoicePreference",
        )
        val callPattern = Regex(
            componentNames.joinToString("|") { """\b$it\s*\(""" }
        )

        val sourceRoot = listOf(
            File("app/src/main/java/com/android/purebilibili"),
            File("src/main/java/com/android/purebilibili"),
            File("../app/src/main/java/com/android/purebilibili"),
        ).firstOrNull(File::isDirectory)
            ?: error("Cannot locate production sources from ${File(".").absolutePath}")

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText().replace("\r\n", "\n")
                callPattern.findAll(source).forEach { match ->
                    val body = balancedBody(source, match.range.first + match.value.length - 1)
                    assertTrue(
                        body.contains(Regex("""\bicon\s*=""")),
                        "Preference call without semantic icon in ${file.relativeTo(sourceRoot)}: " +
                            match.value.trimEnd('(').trim() + " -> " +
                            source.substring(match.range).lineSequence().firstOrNull().orEmpty().trim(),
                    )
                }
            }
    }

    /** 从左括号下标开始返回括号配对的完整实参文本；括号不平衡时返回原文截断。 */
    private fun balancedBody(source: String, openParenIndex: Int): String {
        var depth = 0
        var inString = false
        var escaped = false
        var i = openParenIndex
        while (i < source.length) {
            val c = source[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return source.substring(openParenIndex, i + 1)
                    }
                }
            }
            i++
        }
        return source.substring(openParenIndex, minOf(source.length, openParenIndex + 2000))
    }
}
