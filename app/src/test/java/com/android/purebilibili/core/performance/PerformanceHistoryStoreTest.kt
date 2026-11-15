package com.android.purebilibili.core.performance

import android.app.Application
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class PerformanceHistoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `migration retains old provenance and checkpoints update one session`() {
        val root = temporary.newFolder("performance")
        val legacy = historyReportFixture(schema = 4)
        File(root, "latest.json").writeText(legacy.toString())
        val store = PerformanceHistoryStore(root)
        val current = historyReportFixture(2000)
        store.writeCheckpoint(current)
        current.put("elapsedMs", 10000).put("stopReason", "time_limit")
        store.writeCheckpoint(current)
        val entries = store.read()
        assertEquals(2, entries.size)
        assertEquals(10000L, entries.first().elapsedMs)
        assertEquals(4, entries.last().conditions.schema)
        val savedLegacy = JSONObject(File(root, "history/${performanceHistoryId(legacy)}.json").readText())
        assertEquals("采集时的原始说明", savedLegacy.getString("measurementNotes"))
        assertEquals(4, savedLegacy.getInt("schema"))
    }

    @Test
    fun `retention keeps thirty sessions and latest remains the current report`() {
        val root = temporary.newFolder("performance")
        val store = PerformanceHistoryStore(root)
        repeat(33) { store.writeCheckpoint(historyReportFixture(1000L + it)) }
        val entries = store.read()
        assertEquals(PERFORMANCE_HISTORY_LIMIT, entries.size)
        assertEquals(1032L, entries.first().startedWallMs)
        assertTrue(entries.none { it.startedWallMs < 1003 })
        assertEquals(1032L, JSONObject(File(root, "latest.json").readText()).getLong("startedWallMs"))
    }
}
