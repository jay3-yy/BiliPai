package com.android.purebilibili.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerPersistencePolicyTest {

    @Test
    fun verboseRuntimeLogsRequireDebugBuildAndExplicitOptIn() {
        assertFalse(
            shouldEnableVerboseRuntimeLogs(
                isDebugBuild = false,
                verboseDebugLogsEnabled = true
            )
        )
        assertFalse(
            shouldEnableVerboseRuntimeLogs(
                isDebugBuild = true,
                verboseDebugLogsEnabled = false
            )
        )
        assertTrue(
            shouldEnableVerboseRuntimeLogs(
                isDebugBuild = true,
                verboseDebugLogsEnabled = true
            )
        )
    }

    @Test
    fun runtimeLogDiskPersistenceRequiresExplicitOptIn() {
        assertFalse(
            shouldPersistRuntimeLogEntry(
                level = "D",
                verboseRuntimeLogPersistenceEnabled = false
            )
        )
        assertFalse(
            shouldPersistRuntimeLogEntry(
                level = "W",
                verboseRuntimeLogPersistenceEnabled = false
            )
        )
        assertFalse(
            shouldPersistRuntimeLogEntry(
                level = "E",
                verboseRuntimeLogPersistenceEnabled = false
            )
        )
        assertTrue(
            shouldPersistRuntimeLogEntry(
                level = "D",
                verboseRuntimeLogPersistenceEnabled = true
            )
        )
        assertTrue(
            shouldPersistRuntimeLogEntry(
                level = "E",
                verboseRuntimeLogPersistenceEnabled = true
            )
        )
    }

    @Test
    fun runtimeLogCaptureKeepsWarningsAndErrorsInMemory() {
        assertFalse(
            shouldCaptureRuntimeLogEntry(
                level = "D",
                verboseRuntimeLogsEnabled = false
            )
        )
        assertTrue(
            shouldCaptureRuntimeLogEntry(
                level = "W",
                verboseRuntimeLogsEnabled = false
            )
        )
        assertTrue(
            shouldCaptureRuntimeLogEntry(
                level = "D",
                verboseRuntimeLogsEnabled = true
            )
        )
    }

    @Test
    fun resolvesStableFilePathsUnderLogDirectory() {
        val baseDir = File("/tmp/bilipai")

        assertEquals(
            File("/tmp/bilipai/logs"),
            resolveLogPersistenceDir(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/runtime.log"),
            resolveRuntimeLogFile(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/last_crash_log.txt"),
            resolveCrashSnapshotFile(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/pending_crash.marker"),
            resolveCrashSnapshotMarkerFile(baseDir)
        )
        assertEquals(
            "Download/BiliPai/logs/last_crash_log.txt",
            resolveCrashSnapshotExportRelativePath()
        )
        assertEquals(
            "player_diagnostic_20260329_155725.txt",
            resolvePlayerDiagnosticExportFileName(1_774_771_045_000L)
        )
    }

    @Test
    fun pendingCrashSnapshotRequiresMarkerAndSnapshotFile() {
        assertTrue(
            hasPendingCrashSnapshot(
                markerExists = true,
                snapshotExists = true
            )
        )
        assertFalse(
            hasPendingCrashSnapshot(
                markerExists = true,
                snapshotExists = false
            )
        )
        assertFalse(
            hasPendingCrashSnapshot(
                markerExists = false,
                snapshotExists = true
            )
        )
    }

    @Test
    fun crashSnapshotContentIncludesThrowableAndRecentLogs() {
        val entries = listOf(
            LogCollector.LogEntry(
                timestamp = 1_741_334_800_000L,
                level = "D",
                tag = "MainActivity",
                message = "before crash"
            ),
            LogCollector.LogEntry(
                timestamp = 1_741_334_801_000L,
                level = "E",
                tag = "VideoDetailScreen",
                message = "boom soon"
            )
        )

        val content = buildCrashSnapshotContent(
            throwable = IllegalStateException("Player exploded"),
            entries = entries,
            exportedAtMillis = 1_741_334_802_000L,
            appVersionName = "6.9.0",
            versionCode = 103,
            manufacturer = "nubia",
            model = "NX769J",
            androidRelease = "16",
            apiLevel = 36
        )

        assertTrue(content.contains("BiliPai 崩溃日志快照"))
        assertTrue(content.contains("IllegalStateException"))
        assertTrue(content.contains("Player exploded"))
        assertTrue(content.contains("before crash"))
        assertTrue(content.contains("boom soon"))
    }

    @Test
    fun logArtifactsToClear_includePrivateFilesAndCacheLogDirs() {
        val filesDir = File("/tmp/bilipai/files")
        val cacheDir = File("/tmp/bilipai/cache")

        assertEquals(
            listOf(
                File("/tmp/bilipai/files/logs"),
                File("/tmp/bilipai/cache/logs")
            ),
            resolveLogArtifactDirsToClear(
                filesDir = filesDir,
                cacheDir = cacheDir
            )
        )
    }
}
