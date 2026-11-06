package com.android.purebilibili.feature.video.player

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class PlaylistMemoryCheckpointTest {
    @Test
    fun returningFromCheckpointLeavesTheCurrentQueueAndSelectionPersisted() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val context = RuntimeEnvironment.getApplication()
            PlaylistManager.init(context)
            val queue = listOf(
                PlaylistItem("BV1", 10L, "first", "", ""),
                PlaylistItem("BV2", 20L, "second", "", ""),
            )
            PlaylistManager.setPlaylist(queue, startIndex = 1)
            PlaylistManager.checkpointForMemoryTermination()

            val raw = requireNotNull(context.getSharedPreferences("playlist_manager_state", Context.MODE_PRIVATE)
                .getString("snapshot_json", null))
            val stored = Json.parseToJsonElement(raw).jsonObject
            assertEquals(listOf("BV1", "BV2"), stored.getValue("playlist").jsonArray.map {
                it.jsonObject.getValue("bvid").jsonPrimitive.content
            })
            assertEquals("1", stored.getValue("currentIndex").jsonPrimitive.content)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
