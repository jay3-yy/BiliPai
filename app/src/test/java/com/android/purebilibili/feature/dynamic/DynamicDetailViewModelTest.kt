package com.android.purebilibili.feature.dynamic

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.data.model.response.DrawMajor
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.OpusPic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val item = DynamicItem(id_str = "forwarded-original")

    @BeforeEach
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @AfterEach
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `return from author space retains detail and initializes comments only once`() = runTest(dispatcher) {
        var requests = 0
        var commentRequests = 0
        val model = DynamicDetailViewModel(
            loadDetail = { requests++; Result.success(item) },
            readSeed = { null },
        )
        store.put("detail", model)
        model.load(item.id_str, "failed")
        runCurrent()
        val loaded = model.uiState.value
        model.initializeCommentsOnce(item.id_str, 0L, 0L) { commentRequests++ }

        // The entry ViewModel outlives its composable while the author space covers it.
        model.load(item.id_str, "failed")
        model.initializeCommentsOnce(item.id_str, 0L, 0L) { commentRequests++ }
        runCurrent()
        assertSame(loaded, model.uiState.value)
        assertEquals(1, requests)
        assertEquals(1, commentRequests)
    }

    @Test
    fun `return while initial detail is pending shares the in flight request`() = runTest(dispatcher) {
        var requests = 0
        val response = CompletableDeferred<Result<DynamicItem>>()
        val model = DynamicDetailViewModel(
            loadDetail = { requests++; response.await() },
            readSeed = { item },
        )
        store.put("detail", model)
        model.load(item.id_str, "failed")
        runCurrent()
        model.load(item.id_str, "failed")
        runCurrent()
        assertEquals(DynamicDetailUiState.Success(item, isPreview = true), model.uiState.value)
        assertEquals(1, requests)
        response.complete(Result.success(item))
        runCurrent()
        assertEquals(DynamicDetailUiState.Success(item), model.uiState.value)
    }

    @Test
    fun `image dynamic shows the resolved paragraphs and images as its first detail content`() = runTest(dispatcher) {
        val imageUrl = "https://i0.hdslb.com/bfs/new_dyn/photo.jpg"
        val preview = DynamicItem(
            id_str = "image-dynamic",
            type = "DYNAMIC_TYPE_DRAW",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(draw = DrawMajor(items = listOf(DrawItem(src = imageUrl)))),
                ),
            ),
        )
        val detail = preview.copy(
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        opus = OpusMajor(
                            contentBlocks = listOf(
                                OpusContentBlock.Text("完整正文"),
                                OpusContentBlock.Image(OpusPic(url = imageUrl, width = 1200, height = 800)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val response = CompletableDeferred<Result<DynamicItem>>()
        val model = DynamicDetailViewModel(loadDetail = { response.await() }, readSeed = { preview })
        store.put("detail", model)

        model.load(preview.id_str, "failed")
        runCurrent()
        assertEquals(DynamicDetailUiState.Loading, model.uiState.value)

        response.complete(Result.success(detail))
        runCurrent()
        assertEquals(DynamicDetailUiState.Success(detail), model.uiState.value)
    }

    @Test
    fun `explicit retry can recover an error without automatic retry on return`() = runTest(dispatcher) {
        var requests = 0
        val model = DynamicDetailViewModel(
            loadDetail = {
                requests++
                if (requests == 1) Result.failure(IllegalStateException("offline")) else Result.success(item)
            },
            readSeed = { null },
        )
        store.put("detail", model)
        model.load(item.id_str, "failed")
        runCurrent()
        model.load(item.id_str, "failed")
        runCurrent()
        assertEquals(DynamicDetailUiState.Error("offline"), model.uiState.value)
        assertEquals(1, requests)
        model.load(item.id_str, "failed", force = true)
        runCurrent()
        assertEquals(DynamicDetailUiState.Success(item), model.uiState.value)
        assertEquals(2, requests)
    }

    @Test
    fun `a different comment deep link still initializes its target`() {
        val model = DynamicDetailViewModel(loadDetail = { Result.success(item) }, readSeed = { null })
        store.put("detail", model)
        var initializations = 0
        model.initializeCommentsOnce(item.id_str, 0L, 0L) { initializations++ }
        model.initializeCommentsOnce(item.id_str, 10L, 11L) { initializations++ }
        model.initializeCommentsOnce(item.id_str, 10L, 11L) { initializations++ }
        assertEquals(2, initializations)
    }
}
