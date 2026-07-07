package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.lint.StyleLintAllowlist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins which `feature/settings` files have been migrated off hardcoded
 * RoundedCornerShape / MaterialTheme.colorScheme.surface usage onto AppShapes /
 * AppSurfaceTokens, and verifies that the token APIs are actually wired in
 * (not just unused imports).
 *
 * Note: the original plan asked for a Compose UI render assertion on
 * AppearanceSettingsScreen across the three presets. AppearanceSettingsScreen
 * is one of three large files (~13+4 hits) that remain in the lint allowlist
 * and are queued for a follow-up Task 4 sweep; full preset-rendering coverage
 * waits for Task 5+ once Robolectric is wired or instrumentation is opened.
 */
class SettingsScreenTokenAdoptionTest {

    /**
     * Settings files migrated in this pass. Each MUST be absent from the
     * style lint allowlist for both shape and surface; the lint tests block
     * regressions, this test blocks accidental re-additions to the allowlist.
     */
    private val migratedSettingsFiles = listOf(
        "src/main/java/com/android/purebilibili/feature/settings/IOSSlidingSegmentedControl.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/AnimationSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/AppearanceSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/BottomBarSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/BlockedListScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/IconSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/JsonPluginEditorScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/OpenSourceLicensesScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/PermissionSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/PlaybackSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt",
        "src/main/java/com/android/purebilibili/feature/settings/screen/TipsSettingsScreen.kt",
        "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsComponents.kt",
        "src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"
    )

    @Test
    fun migratedSettingsFiles_areNotInShapeAllowlist() {
        val leaked = migratedSettingsFiles.filter { it in StyleLintAllowlist.SHAPE_HITS }
        assertTrue(
            leaked.isEmpty(),
            "Migrated settings files should not appear in StyleLintAllowlist.SHAPE_HITS; " +
                "remove them once token migration lands.\nLeaked:\n" +
                leaked.joinToString("\n")
        )
    }

    @Test
    fun migratedSettingsFiles_areNotInSurfaceAllowlist() {
        val leaked = migratedSettingsFiles.filter { it in StyleLintAllowlist.SURFACE_HITS }
        assertTrue(
            leaked.isEmpty(),
            "Migrated settings files should not appear in StyleLintAllowlist.SURFACE_HITS.\n" +
                "Leaked:\n" + leaked.joinToString("\n")
        )
    }

    @Test
    fun migratedSettingsFiles_haveNoResidualHardcodedShapeOrSurface() {
        val shapePattern = Regex("""RoundedCornerShape\(\s*\d+""")
        val surfacePattern = Regex("""MaterialTheme\.colorScheme\.(surface|background)\b""")
        val residual = mutableListOf<String>()
        migratedSettingsFiles.forEach { path ->
            val file = locate(path) ?: error("Cannot locate $path from cwd")
            file.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    if (shapePattern.containsMatchIn(line) || surfacePattern.containsMatchIn(line)) {
                        residual.add("$path:${idx + 1}: ${line.trim()}")
                    }
                }
            }
        }
        assertTrue(
            residual.isEmpty(),
            "Migrated settings files must not contain leftover hardcoded shape/surface:\n" +
                residual.joinToString("\n")
        )
    }

    @Test
    fun settingsFeature_wiresAppShapesAndAppSurfaceTokens() {
        val settingsRoot = locate("src/main/java/com/android/purebilibili/feature/settings")
            ?: error("settings dir not found")
        var appShapesCount = 0
        var appSurfaceTokensCount = 0
        settingsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                appShapesCount += Regex("""\bAppShapes\.container\(""").findAll(text).count()
                appSurfaceTokensCount += Regex("""\bAppSurfaceTokens\.""").findAll(text).count()
            }
        assertTrue(
            appShapesCount >= 5,
            "Expected feature/settings to consume AppShapes.container in >= 5 places, got $appShapesCount"
        )
        assertTrue(
            appSurfaceTokensCount >= 5,
            "Expected feature/settings to consume AppSurfaceTokens in >= 5 places, got $appSurfaceTokensCount"
        )
    }

    private fun locate(path: String): File? {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() }
    }
}
