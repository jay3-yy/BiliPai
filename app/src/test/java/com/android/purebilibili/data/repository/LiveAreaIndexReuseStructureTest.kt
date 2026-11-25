package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.LiveAreaParent
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 直播分区索引（area list）复用约束：
 * - 并发去重：多个调用共享同一个 in-flight 请求，不重复打接口；
 * - 短 TTL：缓存有过期时间，过期后重新请求；
 * - 失败不缓存：只有成功结果写缓存，失败后下次仍会重新请求；
 * - LiveListScreen 首次仍需数据：首屏 areaList 为空时依然会拉取。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveAreaIndexReuseStructureTest {

    @Test
    fun `area index reuses a single in-flight request with short ttl`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = createLiveAreaIndexCache(
            scope = backgroundScope,
            nowMs = { testScheduler.currentTime },
        )
        val expected = listOf(LiveAreaParent(id = 2, name = "网游"))

        val first = async {
            cache.getOrLoad(Unit) {
                calls += 1
                gate.await()
                Result.success(expected)
            }
        }
        val second = async {
            cache.getOrLoad(Unit) {
                calls += 1
                Result.success(emptyList())
            }
        }
        runCurrent()
        assertEquals(1, calls)

        first.cancelAndJoin()
        gate.complete(Unit)
        assertEquals(expected, second.await().getOrThrow())
        assertEquals(1, calls)

        advanceTimeBy(AREA_INDEX_TTL_MS - 1L)
        assertEquals(expected, cache.getOrLoad(Unit) { error("Must hit TTL cache") }.getOrThrow())

        advanceTimeBy(1L)
        val refreshed = listOf(LiveAreaParent(id = 3, name = "手游"))
        assertEquals(refreshed, cache.getOrLoad(Unit) { calls += 1; Result.success(refreshed) }.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun `area index caches only successful results and never caches failures`() = runTest {
        var calls = 0
        val cache = createLiveAreaIndexCache(
            scope = backgroundScope,
            nowMs = { testScheduler.currentTime },
        )

        val failure = cache.getOrLoad(Unit) {
            calls += 1
            Result.failure(IllegalStateException("network failed"))
        }
        assertTrue(failure.isFailure)

        val expected = listOf(LiveAreaParent(id = 1, name = "娱乐"))
        val recovered = cache.getOrLoad(Unit) {
            calls += 1
            Result.success(expected)
        }
        assertEquals(expected, recovered.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun `live list screen still requires area data on first load`() {
        val screen = loadSource("app/src/main/java/com/android/purebilibili/feature/live/LiveListScreen.kt")

        // 首屏 areaList 为空时仍然要走一次 getLiveAreaIndex()，不能因为缓存而跳过首次数据。
        assertTrue(screen.contains("if (_uiState.value.areaList.isEmpty())"))
        assertTrue(screen.contains("LiveRepository.getLiveAreaIndex()"))
        // 不再直接裸调 web area list 接口。
        assertFalse(screen.contains("NetworkModule.api.getLiveAreaList()"))
    }

    private fun loadSource(path: String): String {
        val candidates = listOf(
            File(path),
            File("app/$path"),
            File("../$path"),
            File("../core-data/$path"),
            File("core-data/$path"),
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText().replace("\r\n", "\n")
    }
}
