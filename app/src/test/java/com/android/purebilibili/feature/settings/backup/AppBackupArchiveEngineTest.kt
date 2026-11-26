package com.android.purebilibili.feature.settings.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppBackupArchiveEngineTest {

    @Test
    fun `round trip archives and restores settings plugins and personal keepsakes`() {
        val srcRoot = Files.createTempDirectory("bilipai_backup_src").toFile()
        val dstRoot = Files.createTempDirectory("bilipai_backup_dst").toFile()
        try {
            val srcFiles = File(srcRoot, "files").apply { mkdirs() }
            val srcPrefs = File(srcRoot, "shared_prefs").apply { mkdirs() }
            File(srcFiles, "datastore/settings_prefs.preferences_pb").apply {
                parentFile?.mkdirs()
                writeText("settings-pb-v2")
            }
            File(srcFiles, "settings-profiles/custom.json").apply {
                parentFile?.mkdirs()
                writeText("""{"name":"custom"}""")
            }
            File(srcFiles, "json_plugins/demo.json").apply {
                parentFile?.mkdirs()
                writeText("""{"id":"demo"}""")
            }
            File(srcPrefs, "completion_keepsakes.xml").writeText("<map><string name=\"records_1\">ok</string></map>")
            // Sensitive credentials must never be included
            File(srcPrefs, "token_prefs.xml").writeText("<map><string name=\"cookie\">secret</string></map>")

            val srcEngine = AppBackupArchiveEngine(srcFiles, srcPrefs, "7.0.0")
            val archiveBytes = srcEngine.createArchive(nowEpochMs = 1_767_225_600_000L)

            val dstFiles = File(dstRoot, "files").apply { mkdirs() }
            val dstPrefs = File(dstRoot, "shared_prefs").apply { mkdirs() }
            val dstEngine = AppBackupArchiveEngine(dstFiles, dstPrefs, "7.0.0")

            val inspection = dstEngine.inspectArchive(archiveBytes)
            assertFalse(inspection.isLegacyV1)
            assertNotNull(inspection.manifest)
            assertEquals(4, inspection.restorableEntryCount)
            assertEquals(AppBackupScope.entries.toSet(), inspection.detectedScopes)

            val report = dstEngine.restoreArchive(archiveBytes)
            assertEquals(4, report.restoredCount)
            assertEquals("settings-pb-v2", File(dstFiles, "datastore/settings_prefs.preferences_pb").readText())
            assertEquals("""{"name":"custom"}""", File(dstFiles, "settings-profiles/custom.json").readText())
            assertEquals("""{"id":"demo"}""", File(dstFiles, "json_plugins/demo.json").readText())
            assertTrue(File(dstPrefs, "completion_keepsakes.xml").readText().contains("records_1"))
            assertFalse(File(dstPrefs, "token_prefs.xml").exists())
        } finally {
            srcRoot.deleteRecursively()
            dstRoot.deleteRecursively()
        }
    }

    @Test
    fun `selective scope restore only overwrites chosen scope`() {
        val srcRoot = Files.createTempDirectory("bilipai_backup_sel_src").toFile()
        val dstRoot = Files.createTempDirectory("bilipai_backup_sel_dst").toFile()
        try {
            val srcFiles = File(srcRoot, "files").apply { mkdirs() }
            val srcPrefs = File(srcRoot, "shared_prefs").apply { mkdirs() }
            File(srcFiles, "datastore/settings_prefs.preferences_pb").apply {
                parentFile?.mkdirs()
                writeText("new-settings")
            }
            File(srcPrefs, "completion_keepsakes.xml").writeText("new-keepsakes")

            val archive = AppBackupArchiveEngine(srcFiles, srcPrefs, "7.0.0").createArchive()

            val dstFiles = File(dstRoot, "files").apply { mkdirs() }
            val dstPrefs = File(dstRoot, "shared_prefs").apply { mkdirs() }
            File(dstFiles, "datastore/settings_prefs.preferences_pb").apply {
                parentFile?.mkdirs()
                writeText("existing-settings")
            }

            val report = AppBackupArchiveEngine(dstFiles, dstPrefs, "7.0.0").restoreArchive(
                zipBytes = archive,
                allowedScopes = setOf(AppBackupScope.PERSONAL),
            )

            assertEquals(1, report.restoredCount)
            assertEquals(setOf(AppBackupScope.PERSONAL), report.restoredScopes)
            assertEquals("existing-settings", File(dstFiles, "datastore/settings_prefs.preferences_pb").readText())
            assertEquals("new-keepsakes", File(dstPrefs, "completion_keepsakes.xml").readText())
        } finally {
            srcRoot.deleteRecursively()
            dstRoot.deleteRecursively()
        }
    }

    @Test
    fun `rejects path traversal and sensitive credential entries while remaining compatible with v1`() {
        assertNull(classifyBackupEntryScope("../escape.xml"))
        assertNull(classifyBackupEntryScope("/etc/passwd"))
        assertNull(classifyBackupEntryScope("datastore/webdav_backup_prefs.preferences_pb"))
        assertNull(classifyBackupEntryScope("shared_prefs/token_prefs.xml"))
        assertEquals(AppBackupScope.SETTINGS, classifyBackupEntryScope("datastore/settings_prefs.preferences_pb"))
        assertEquals(AppBackupScope.PERSONAL, classifyBackupEntryScope("shared_prefs/completion_keepsakes.xml"))

        val legacyZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("datastore/settings_prefs.preferences_pb"))
                zip.write("legacy-pb".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("../evil.txt"))
                zip.write("nope".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val dstRoot = Files.createTempDirectory("bilipai_backup_legacy").toFile()
        try {
            val dstFiles = File(dstRoot, "files").apply { mkdirs() }
            val dstPrefs = File(dstRoot, "shared_prefs").apply { mkdirs() }
            val engine = AppBackupArchiveEngine(dstFiles, dstPrefs, "7.0.0")

            val inspection = engine.inspectArchive(legacyZip)
            assertTrue(inspection.isLegacyV1)
            assertEquals(1, inspection.restorableEntryCount)

            val report = engine.restoreArchive(legacyZip)
            assertEquals(1, report.restoredCount)
            assertEquals("legacy-pb", File(dstFiles, "datastore/settings_prefs.preferences_pb").readText())
            assertFalse(File(dstRoot, "evil.txt").exists())
        } finally {
            dstRoot.deleteRecursively()
        }
    }

    @Test
    fun `formats backup sizes and timestamps cleanly`() {
        assertEquals("512 B", formatBackupSize(512L))
        assertEquals("2.5 KB", formatBackupSize(2560L))
        assertEquals("1.50 MB", formatBackupSize(1572864L))
        assertEquals("2026-01-01 00:00", formatBackupTimestamp(1_767_225_600_000L, ZoneOffset.UTC))
        assertEquals("未知时间", formatBackupTimestamp(0L, ZoneOffset.UTC))
    }
}
