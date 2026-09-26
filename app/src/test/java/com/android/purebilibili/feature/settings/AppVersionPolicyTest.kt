package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AppVersionPolicyTest {

    @Test
    fun appVersion_usesSemanticVersionScheme() {
        val buildFile = listOf(
            File("app/build.gradle.kts"),
            File("build.gradle.kts")
        ).first { it.exists() }.readText()

        assertTrue(buildFile.contains("versionCode = 396"))
        assertTrue(buildFile.contains("versionName = \"0.2.3-alpha.6\""))
        // 语义化 X.Y.Z，不用日历日/四位年当版本号
        assertTrue(!buildFile.contains("versionName = \"26."))
        assertTrue(!buildFile.contains("versionName = \"2026."))
        assertTrue(
            buildFile.contains("语义化") ||
                buildFile.contains("MAJOR.MINOR.PATCH") ||
                buildFile.contains("X.Y.Z")
        )
    }
}
