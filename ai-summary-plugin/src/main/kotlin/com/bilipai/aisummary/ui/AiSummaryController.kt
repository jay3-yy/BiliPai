package com.bilipai.aisummary.ui

import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.model.SummaryUiState
import com.bilipai.aisummary.pipeline.SummaryPipeline
import com.bilipai.aisummary.pipeline.SummaryRequest
import com.bilipai.aisummary.settings.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 抽屉的状态控制器。刻意不继承 AndroidX ViewModel，
 * 这样宿主无论是 Compose remember、ViewModel 还是自己的状态容器都能挂上去。
 */
class AiSummaryController(
    private val pipeline: SummaryPipeline,
    private val configStore: ConfigStore,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(request: SummaryRequest, forceRefresh: Boolean = false) {
        val cfg = configStore.load()
        // 没填 Key 但开启官方兜底时仍允许启动：无字幕视频可以走 B 站官方总结。
        if (!cfg.isConfigured && !cfg.fallbackToOfficial) {
            _state.value = SummaryUiState.NeedConfig
            return
        }
        job?.cancel()
        job = scope.launch {
            _state.value = SummaryUiState.Loading("准备中…", 0, 0)
            try {
                val result: SummaryResult = pipeline.run(
                    request = request,
                    forceRefresh = forceRefresh
                ) { stage, done, total ->
                    _state.value = SummaryUiState.Loading(stage, done, total)
                }
                _state.value = SummaryUiState.Success(result, fromCache = result.source == SummaryResult.Source.CACHE)
            } catch (e: SummaryException) {
                _state.value = SummaryUiState.Error(e.message ?: "总结失败", e.retryable)
            } catch (e: Exception) {
                _state.value = SummaryUiState.Error(
                    "总结失败：${e.javaClass.simpleName} ${e.message?.take(120).orEmpty()}"
                )
            }
        }
    }

    fun refreshConfig(request: SummaryRequest) = start(request, forceRefresh = true)

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SummaryUiState.Idle
    }

    fun showNeedConfig() { _state.value = SummaryUiState.NeedConfig }

    /** 供诊断展示，不含 Key。 */
    fun diagnostics(): String = configStore.load().safeDescription() +
        " @${clockMillis()}"
}
