package com.android.purebilibili.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PersonalRecapUpsUiState(
    val window: PersonalRecapWindow,
    val stats: PersonalVideoRecapStats? = null,
    val error: String? = null,
    val sortOrder: PersonalRecapUpSortOrder = PersonalRecapUpSortOrder.VIDEO_COUNT,
)

internal class PersonalRecapUpsViewModel(
    initialWindow: PersonalRecapWindow,
    initialStats: PersonalVideoRecapStats,
    private val loadStats: suspend (PersonalRecapWindow) -> PersonalVideoRecapStats = { window ->
        PersonalRecapRepository.videoRecap(
            resolvePersonalRecapWindowStart(System.currentTimeMillis(), window),
        )
    },
) : ViewModel() {
    private val snapshots = mutableMapOf(initialWindow to initialStats)
    private val _uiState = MutableStateFlow(PersonalRecapUpsUiState(initialWindow, initialStats))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun selectWindow(window: PersonalRecapWindow) {
        val current = _uiState.value
        if (window == current.window && (current.stats != null || loadJob?.isActive == true)) return
        loadJob?.cancel()
        val cached = snapshots[window]
        _uiState.update { it.copy(window = window, stats = cached, error = null) }
        if (cached != null) return
        loadJob = viewModelScope.launch {
            try {
                val stats = loadStats(window)
                snapshots[window] = stats
                _uiState.update { it.copy(stats = stats, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.message ?: "无法读取观看记录"
                _uiState.update { it.copy(stats = null, error = message) }
            }
        }
    }

    fun selectSortOrder(order: PersonalRecapUpSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun retry() = selectWindow(_uiState.value.window)
}
