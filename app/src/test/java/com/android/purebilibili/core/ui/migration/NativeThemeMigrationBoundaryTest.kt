package com.android.purebilibili.core.ui.migration

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 双主题原生组件迁移的存量棘轮。
 *
 * 这些上限冻结 2026-08-24 的生产源码现状。每完成一个迁移批次，都应同步调低对应上限；
 * 不允许为了让测试通过而提高上限。共享 Miuix navigation 子系统不属于本轮组件迁移，
 * 因此使用精确文件集合而不是数量上限，防止导航例外继续扩散。
 */
class NativeThemeMigrationBoundaryTest {

    @Test
    fun featureVendorImportsOnlyDecrease() {
        val featureFiles = kotlinFiles("app/src/main/java/com/android/purebilibili/feature")

        assertAtMost(
            actual = featureFiles.countFilesWithImportPrefix(MATERIAL3_IMPORT),
            maximum = MAX_FEATURE_MATERIAL3_FILES,
            label = "feature Material3 import files",
        )
        assertAtMost(
            actual = featureFiles.countImportLines(MATERIAL3_IMPORT),
            maximum = MAX_FEATURE_MATERIAL3_IMPORTS,
            label = "feature Material3 imports",
        )
        assertAtMost(
            actual = featureFiles.countImportLines("import androidx.compose.material3.*"),
            maximum = MAX_FEATURE_MATERIAL3_WILDCARD_IMPORTS,
            label = "feature Material3 wildcard imports",
        )
        assertAtMost(
            actual = featureFiles.countFilesWithAnyImportPrefix(MIUIX_COMPONENT_IMPORTS),
            maximum = MAX_FEATURE_MIUIX_COMPONENT_FILES,
            label = "feature Miuix visible-component import files",
        )
        assertAtMost(
            actual = featureFiles.countImportLines(MIUIX_COMPONENT_IMPORTS),
            maximum = MAX_FEATURE_MIUIX_COMPONENT_IMPORTS,
            label = "feature Miuix visible-component imports",
        )
        assertAtMost(
            actual = featureFiles.countFilesWithImportPrefix(MIUIX_ICON_IMPORT),
            maximum = MAX_FEATURE_MIUIX_ICON_FILES,
            label = "feature Miuix icon import files",
        )
        assertAtMost(
            actual = featureFiles.countImportLines(MIUIX_ICON_IMPORT),
            maximum = MAX_FEATURE_MIUIX_ICON_IMPORTS,
            label = "feature Miuix icon imports",
        )
    }

    @Test
    fun featureThemeBranchingOnlyDecreases() {
        val featureFiles = kotlinFiles("app/src/main/java/com/android/purebilibili/feature")
        val branchLines = featureFiles.flatMap { file ->
            file.readLines().filter { line ->
                line.contains("LocalAppUiStyle") || APP_UI_STYLE_BRANCH.containsMatchIn(line)
            }
        }
        val branchFiles = featureFiles.count { file ->
            file.useLines { lines ->
                lines.any { line ->
                    line.contains("LocalAppUiStyle") || APP_UI_STYLE_BRANCH.containsMatchIn(line)
                }
            }
        }

        assertAtMost(branchFiles, MAX_FEATURE_THEME_BRANCH_FILES, "feature theme-branch files")
        assertAtMost(branchLines.size, MAX_FEATURE_THEME_BRANCH_LINES, "feature theme-branch lines")
    }

    @Test
    fun featureSliderColorsUseNeutralAppTokens() {
        val offenders = kotlinFiles("app/src/main/java/com/android/purebilibili/feature")
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (MATERIAL_SLIDER_DEFAULTS_CALL.containsMatchIn(line)) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "AppSlider colors must use AppSliderDefaults, not Material3 SliderDefaults:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun appDirectSliderCallsUseFacade() {
        val offenders = kotlinFiles("app/src/main/java")
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (DIRECT_NATIVE_SLIDER_CALL.containsMatchIn(line)) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "App production sources must use AppSlider, not a direct native Slider:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun appDirectButtonCallsUseFacade() {
        val offenders = kotlinFiles("app/src/main/java")
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (DIRECT_NATIVE_BUTTON_CALL.containsMatchIn(line)) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "App production sources must use App*Button, not a direct native Button:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun appIconButtonVisualGeometryDoesNotReuseTouchTargetDimensions() {
        val offenders = kotlinFiles("app/src/main/java")
            .flatMap { file ->
                file.callHeaders("AppIconButton").mapNotNull { (lineNumber, header) ->
                    if (FIXED_ICON_BUTTON_DIMENSION.containsMatchIn(header)) {
                        "${repoRelativePath(file)}:$lineNumber: ${header.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "AppIconButton visual geometry must come from the native component or a role policy, " +
                "not a fixed 48dp touch-target dimension:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun appDirectIconButtonCallsAndDefaultsUseFacade() {
        val offenders = kotlinFiles("app/src/main/java")
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (
                        DIRECT_NATIVE_ICON_BUTTON_CALL.containsMatchIn(line) ||
                        MATERIAL_ICON_BUTTON_DEFAULTS_REFERENCE.containsMatchIn(line) ||
                        DIRECT_ICON_BUTTON_VENDOR_IMPORT.containsMatchIn(line)
                    ) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "App production sources must use App*IconButton and AppIconButtonDefaults:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun featureDirectProgressCallsOnlyDecrease() {
        val directCalls = kotlinFiles("app/src/main/java/com/android/purebilibili/feature")
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (DIRECT_PROGRESS_INDICATOR_CALL.containsMatchIn(line)) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertAtMost(
            actual = directCalls.size,
            maximum = MAX_FEATURE_DIRECT_PROGRESS_CALLS,
            label = "feature direct native progress calls",
        )
    }

    @Test
    fun designSystemComponentsUseTheProgressFacade() {
        val offenders = kotlinFiles(
            "design-system/src/main/java/com/android/purebilibili/core/ui/components"
        )
            .filterNot { it.name == "AppProgressIndicator.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (DIRECT_PROGRESS_INDICATOR_CALL.containsMatchIn(line)) {
                        "${repoRelativePath(file)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "Design-system components must use App* progress facades:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun appNativeCardCallsUseOnlyAppFacades() {
        val directCalls = kotlinFiles("app/src/main/java")
            .flatMap { file ->
                var enclosingFunction = "<top-level>"
                buildList {
                    file.readLines().forEach { line ->
                        FUNCTION_DECLARATION.find(line)?.groupValues?.get(1)?.let {
                            enclosingFunction = it
                        }
                        DIRECT_NATIVE_CARD_CALL.findAll(line).forEach { match ->
                            add("${repoRelativePath(file)}#$enclosingFunction#${match.value.removeSuffix("(")}")
                        }
                    }
                }
            }
            .sorted()

        assertEquals(MD3_CARD_PREVIEW_EXCEPTIONS, directCalls)
    }

    @Test
    fun primitiveFacadeVendorImportsOnlyDecrease() {
        val source = repoFile(
            "design-system/src/main/java/com/android/purebilibili/core/ui/components/" +
                "AppPrimitiveComponents.kt"
        )
        val imports = source.readLines().count { it.startsWith(MATERIAL3_IMPORT) }

        assertAtMost(
            actual = imports,
            maximum = MAX_PRIMITIVE_FACADE_MATERIAL3_IMPORTS,
            label = "AppPrimitiveComponents Material3 imports",
        )
    }

    @Test
    fun rendererPackagesCannotCrossVendorBoundaries() {
        val materialRenderers = kotlinFiles(
            "design-system/src/main/java/com/android/purebilibili/core/ui/renderer/material3"
        )
        val miuixRenderers = kotlinFiles(
            "design-system/src/main/java/com/android/purebilibili/core/ui/renderer/miuix"
        )

        val materialOffenders = materialRenderers.importOffenders(MIUIX_VENDOR_IMPORT)
        val miuixOffenders = miuixRenderers.importOffenders(MATERIAL3_IMPORT)

        assertTrue(
            materialOffenders.isEmpty(),
            "Material3 renderer must not import Miuix:\n${materialOffenders.joinToString("\n")}",
        )
        assertTrue(
            miuixOffenders.isEmpty(),
            "Miuix renderer must not import Material3:\n${miuixOffenders.joinToString("\n")}",
        )
    }


    @Test
    fun sharedMiuixNavigationExceptionSetCannotExpandOrChangeIdentity() {
        val sourceFiles = kotlinFiles("app/src/main/java") + kotlinFiles("design-system/src/main/java")
        val navigationFiles = sourceFiles
            .filter { it.hasAnyImportPrefix(listOf(MIUIX_NAVIGATION_IMPORT)) }
            .map(::repoRelativePath)
            .toSet()

        assertEquals(MIUIX_NAVIGATION_EXCEPTION_FILE_COUNT, navigationFiles.size)
        assertEquals(MIUIX_NAVIGATION_EXCEPTION_PATHS_SHA256, sha256(navigationFiles))
    }

    private fun List<File>.countFilesWithImportPrefix(prefix: String): Int =
        count { it.hasAnyImportPrefix(listOf(prefix)) }

    private fun List<File>.countFilesWithAnyImportPrefix(prefixes: List<String>): Int =
        count { it.hasAnyImportPrefix(prefixes) }

    private fun List<File>.countImportLines(prefix: String): Int = countImportLines(listOf(prefix))

    private fun List<File>.countImportLines(prefixes: List<String>): Int = sumOf { file ->
        file.useLines { lines ->
            lines.count { line -> prefixes.any(line::startsWith) }
        }
    }

    private fun List<File>.importOffenders(prefix: String): List<String> = flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
            if (line.startsWith(prefix)) {
                "${repoRelativePath(file)}:${index + 1}: $line"
            } else {
                null
            }
        }
    }

    private fun File.hasAnyImportPrefix(prefixes: List<String>): Boolean = useLines { lines ->
        lines.any { line -> prefixes.any(line::startsWith) }
    }

    private fun File.callHeaders(callName: String): List<Pair<Int, String>> {
        val marker = "$callName("
        var startLine = -1
        var parenthesisDepth = 0
        var header = StringBuilder()

        return buildList {
            readLines().forEachIndexed { index, line ->
                if (startLine < 0) {
                    val markerIndex = line.indexOf(marker)
                    if (markerIndex < 0) return@forEachIndexed
                    startLine = index + 1
                    val firstLine = line.substring(markerIndex)
                    header.appendLine(firstLine)
                    parenthesisDepth += firstLine.count { it == '(' } - firstLine.count { it == ')' }
                } else {
                    header.appendLine(line)
                    parenthesisDepth += line.count { it == '(' } - line.count { it == ')' }
                }

                if (parenthesisDepth <= 0) {
                    add(startLine to header.toString())
                    startLine = -1
                    parenthesisDepth = 0
                    header = StringBuilder()
                }
            }
            check(startLine < 0) { "Unterminated $callName call header in ${path}" }
        }
    }

    private fun assertAtMost(actual: Int, maximum: Int, label: String) {
        assertTrue(
            actual <= maximum,
            "$label increased to $actual (frozen maximum: $maximum). " +
                "Route feature UI through App* and lower the migration budget after each batch.",
        )
    }

    private fun kotlinFiles(path: String): List<File> = repoFile(path)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun repoFile(path: String): File = File(repositoryRoot(), path)

    private fun repoRelativePath(file: File): String = file
        .relativeTo(repositoryRoot())
        .invariantSeparatorsPath

    private fun repositoryRoot(): File {
        val cwd = File(".").absoluteFile.canonicalFile
        return when {
            File(cwd, "app/src/main/java").isDirectory -> cwd
            File(cwd, "src/main/java").isDirectory && File(cwd.parentFile, "design-system").isDirectory ->
                cwd.parentFile.canonicalFile
            else -> error("Cannot locate repository root from ${cwd.path}")
        }
    }

    private fun sha256(values: Set<String>): String {
        val bytes = values.sorted().joinToString("\n").toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private companion object {
        const val MATERIAL3_IMPORT = "import androidx.compose.material3."
        const val MIUIX_VENDOR_IMPORT = "import top.yukonga.miuix."
        const val MIUIX_ICON_IMPORT = "import top.yukonga.miuix.kmp.icon."
        const val MIUIX_NAVIGATION_IMPORT = "import top.yukonga.miuix.kmp.nav."

        val MIUIX_COMPONENT_IMPORTS = listOf(
            "import top.yukonga.miuix.kmp.basic.",
            "import top.yukonga.miuix.kmp.preference.",
            "import top.yukonga.miuix.kmp.overlay.",
        )
        val APP_UI_STYLE_BRANCH = Regex("\\bAppUiStyle\\.")
        val MATERIAL_SLIDER_DEFAULTS_CALL = Regex("(?<![A-Za-z0-9_])SliderDefaults\\.colors\\(")
        val DIRECT_NATIVE_SLIDER_CALL = Regex("(?<![A-Za-z0-9_])Slider\\(")
        val DIRECT_NATIVE_BUTTON_CALL =
            Regex("(?<![A-Za-z0-9_])(?:Button|TextButton|OutlinedButton)\\(")
        val FIXED_ICON_BUTTON_DIMENSION = Regex(
            "\\.(?:size|width|height|sizeIn|widthIn|heightIn)\\s*\\([^)]*" +
                "(?:48\\.dp|(?:AppChromeSizeTokens\\.)?MinimumTouchTarget)",
            RegexOption.DOT_MATCHES_ALL,
        )
        val DIRECT_NATIVE_ICON_BUTTON_CALL = Regex(
            "(?<![A-Za-z0-9_])(?:IconButton|FilledIconButton|FilledTonalIconButton|" +
                "OutlinedIconButton)\\s*\\(",
        )
        val MATERIAL_ICON_BUTTON_DEFAULTS_REFERENCE =
            Regex("(?<![A-Za-z0-9_])IconButtonDefaults\\.")
        val DIRECT_ICON_BUTTON_VENDOR_IMPORT = Regex(
            "^import (?:androidx\\.compose\\.material3\\.(?:IconButton|FilledIconButton|" +
                "FilledTonalIconButton|OutlinedIconButton|IconButtonDefaults)|" +
                "top\\.yukonga\\.miuix\\.kmp\\.basic\\.IconButton)(?:\\s+as\\s+\\w+)?$",
        )
        val DIRECT_PROGRESS_INDICATOR_CALL =
            Regex("(?<!App)(?:Circular|Linear)ProgressIndicator\\(")
        val DIRECT_NATIVE_CARD_CALL =
            Regex("(?<![A-Za-z0-9_])(?:Card|ElevatedCard|OutlinedCard)\\(")
        val FUNCTION_DECLARATION = Regex("\\bfun\\s+([A-Za-z0-9_]+)\\s*\\(")

        // 首页配色预览重做后，原 Md3ThemeColorPreview 的 ElevatedCard 例外已移除：
        // app 源码不应再直接调用原生 Card / ElevatedCard / OutlinedCard，一律走 App* 外观。
        val MD3_CARD_PREVIEW_EXCEPTIONS = emptyList<String>()

        // Frozen from production sources on 2026-08-24; lower after each migration batch.
        const val MAX_FEATURE_MATERIAL3_FILES = 237
        const val MAX_FEATURE_MATERIAL3_IMPORTS = 332
        const val MAX_FEATURE_MATERIAL3_WILDCARD_IMPORTS = 89
        const val MAX_FEATURE_MIUIX_COMPONENT_FILES = 1
        const val MAX_FEATURE_MIUIX_COMPONENT_IMPORTS = 1
        const val MAX_FEATURE_MIUIX_ICON_FILES = 5
        const val MAX_FEATURE_MIUIX_ICON_IMPORTS = 26
        const val MAX_FEATURE_THEME_BRANCH_FILES = 8
        const val MAX_FEATURE_THEME_BRANCH_LINES = 34
        const val MAX_FEATURE_DIRECT_PROGRESS_CALLS = 0
        const val MAX_PRIMITIVE_FACADE_MATERIAL3_IMPORTS = 47

        const val MIUIX_NAVIGATION_EXCEPTION_FILE_COUNT = 12
        const val MIUIX_NAVIGATION_EXCEPTION_PATHS_SHA256 =
            "44a6cdfe8e3e7963aa014c981ca69c4af61f536a51ecf860532872d762d1e088"
    }
}
