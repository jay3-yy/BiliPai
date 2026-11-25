package com.android.purebilibili.feature.category

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 任务17 结构测试：Category 单一 uiState 采集 + 单次 copy 事务 + generation 回包拒绝 + 有界 tid 缓存。
 *
 * 纯源码/结构断言，不依赖 Compose 运行时。
 */
class CategoryUiStateSingleSourceStructureTest {

    private val source = loadSource(
        "app/src/main/java/com/android/purebilibili/feature/category/CategoryScreen.kt"
    )

    @Test
    fun `only one uiState flow is exposed and collected`() {
        // 仅暴露一个 uiState（asStateFlow）
        val exposed = Regex("""val\s+\w+\s*=\s*_uiState\.asStateFlow\(\)""").findAll(source).count()
        assertTrue(exposed == 1, "expected exactly one exposed uiState flow, found $exposed")

        // UI 层仅用一次 collectAsStateWithLifecycle 采集 uiState
        val collects = Regex("""collectAsStateWithLifecycle\(\)""").findAll(source).count()
        assertTrue(collects >= 1)
        assertTrue(
            source.contains("val uiState by viewModel.uiState.collectAsStateWithLifecycle()"),
            "CategoryScreen must collect the single uiState directly"
        )
        assertFalse(
            Regex("""viewModel\.\w+State\.collectAsStateWithLifecycle\(\)""").containsMatchIn(source),
            "CategoryScreen must not collect per-field StateFlows"
        )
    }

    @Test
    fun `state transitions use single copy transactions`() {
        // 所有状态变更均为单次 copy 事务（_uiState.update / _uiState.value = ...copy）
        assertFalse(
            source.contains("_uiState.value.videos ="),
            "must not mutate fields of the exposed state directly"
        )
        assertTrue(
            Regex("""_uiState\.update\s*\{""").findAll(source).count() >= 3,
            "expected multiple single-copy transactions via _uiState.update"
        )
        // 成功回包时通过原子 CAS 一次性产出最新状态并写回缓存，避免中间帧与并发覆盖
        assertTrue(source.contains("val nextState = _uiState.updateAndGet"))
    }

    @Test
    fun `stale generation responses are rejected`() {
        assertTrue(source.contains("val requestGeneration = ++generation"))
        assertTrue(
            source.contains("if (requestGeneration != generation || requestedTid != currentTid) return@launch"),
            "stale/other-tid responses must be rejected before publishing"
        )
        assertTrue(source.contains("ensureActive()"))
    }

    @Test
    fun `tid cache is bounded LRU`() {
        assertTrue(
            source.contains("LinkedHashMap<Int, CachedCategory>("),
            "tid cache must be a LinkedHashMap"
        )
        // access-order=true 构造第三参
        assertTrue(
            source.contains("LinkedHashMap<Int, CachedCategory>(4, 0.75f, true)"),
            "tid cache must be access-ordered for LRU semantics"
        )
        assertTrue(
            source.contains("removeEldestEntry") && source.contains("size > 4"),
            "tid cache must evict eldest entries to stay bounded"
        )
    }

    @Test
    fun `refresh and blank-response semantics preserved`() {
        assertTrue(
            source.contains("resolveReplaceRefreshPage(nextLoadPage = currentPage, hasMore = hasMore)"),
            "refresh must keep the resolveReplaceRefreshPage contract"
        )
        // 空响应：不推进页码（刷新时回落第1页）
        assertTrue(source.contains("else if (isRefresh) 1 else currentPage"))
        // 缓存状态在分类切换时保留既有刷新/空响应语义
        assertTrue(source.contains("categoryCache[requestedTid] = CachedCategory(nextState, currentPage, hasMore)"))
    }
}

private fun loadSource(path: String): String {
    val normalizedPath = path.removePrefix("app/")
    val sourceFile = listOf(
        File(path),
        File(normalizedPath),
        File("app", normalizedPath)
    ).firstOrNull { it.exists() }
    require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
    return sourceFile.readText()
}
