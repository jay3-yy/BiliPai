package com.android.purebilibili.feature.video.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.ViewInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackSnapshotTest {
    @Test
    fun delayedPartSnapshotsCannotDrivePlaybackAfterAnotherPartIsCommitted() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(SettingsManager)
        val application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application
        every { SettingsManager.getPlaybackCdnPreference(application) } returns flowOf("base_url")
        val owner = VideoPlaybackViewModel(application)
        try {
            @Suppress("UNCHECKED_CAST")
            val committed = owner.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }
                .get(owner) as MutableStateFlow<VideoPlaybackUiState>
            val p2 = VideoPlaybackUiState.Success(ViewInfo(bvid = "BV1", cid = 22L), "p2")
            val p3 = p2.copy(info = p2.info.copy(cid = 33L), playUrl = "p3")
            committed.value = p2
            assertTrue(owner.isCurrentPlaybackSnapshot(p2))

            // 不调度异步 UI 投影，保留页面收到旧 P2、播放器已提交 P3 的窗口。
            committed.value = p3
            assertFalse(owner.isCurrentPlaybackSnapshot(p2))
            assertTrue(owner.isCurrentPlaybackSnapshot(p3))
            assertFalse(owner.isCurrentPlaybackSnapshot(p3.copy(info = p3.info.copy(bvid = "BV2"))))

            committed.value = VideoPlaybackUiState.Loading.Initial
            assertFalse(owner.isCurrentPlaybackSnapshot(p3))
        } finally {
            owner.viewModelScope.cancel()
            unmockkObject(SettingsManager)
            Dispatchers.resetMain()
        }
    }
}
