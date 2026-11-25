package com.android.purebilibili.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.SpaceUpowerRankItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UP主充电排行页（对齐 PiliPlus member_upower_rank）。 */
class SpaceUpowerRankViewModel(
    private val upMid: Long,
    upName: String,
    initialCount: Long = 0L,
) : ViewModel() {

    data class LevelTab(
        val privilegeType: Int,
        val name: String,
        val memberTotal: Int,
    )

    data class UiState(
        val upName: String = "",
        val totalCount: Long = 0L,
        val isLoading: Boolean = true,
        val error: String? = null,
        val items: List<SpaceUpowerRankItem> = emptyList(),
        val levelTabs: List<LevelTab> = emptyList(),
        // null 表示「全部」
        val selectedPrivilegeType: Int? = null,
    )

    private val spaceApi = NetworkModule.spaceApi
    private val _uiState = MutableStateFlow(UiState(upName = upName, totalCount = initialCount))
    val uiState = _uiState.asStateFlow()

    private var hasResolvedTabs = false

    init {
        load()
    }

    fun selectLevel(privilegeType: Int?) {
        var changed = false
        _uiState.update { current ->
            if (current.selectedPrivilegeType == privilegeType) {
                current
            } else {
                changed = true
                current.copy(
                    selectedPrivilegeType = privilegeType,
                    items = emptyList(),
                    isLoading = true,
                    error = null,
                )
            }
        }
        if (!changed) return
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        load()
    }

    /** App 端 elec 充电用户列表回退：与空间头部"N人为TA充电"同源。 */
    private suspend fun fetchElecFallback(): Pair<List<SpaceUpowerRankItem>, Long>? {
        return runCatching {
            val params = mapOf(
                "vmid" to upMid.toString(),
                "build" to "8430300",
                "mobi_app" to "android",
                "platform" to "android",
                "channel" to "master",
                "s_locale" to "zh_CN",
                "c_locale" to "zh_CN",
                "appkey" to com.android.purebilibili.core.network.AppSignUtils.ANDROID_APP_KEY,
                "ts" to com.android.purebilibili.core.network.AppSignUtils.getTimestamp().toString(),
            )
            val response = NetworkModule.spaceApi.getAppSpaceSupporters(
                com.android.purebilibili.core.network.AppSignUtils.signForAndroidApi(params)
            )
            val elec = response.data?.elec
            if (response.code != 0 || elec == null || elec.total <= 0L) return@runCatching null
            elec.list.map { user ->
                SpaceUpowerRankItem(
                    mid = user.mid,
                    nickname = user.uname,
                    avatar = user.avatar.ifBlank { user.face },
                    day = 0,
                )
            }.takeIf { it.isNotEmpty() }?.let { it to elec.total }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            android.util.Log.d("SpaceUpowerRank", "elec fallback failed: ${error.message}")
        }.getOrNull()
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val selectedType = _uiState.value.selectedPrivilegeType
            try {
                val params = buildMap {
                    put("up_mid", upMid.toString())
                    put("pn", "1")
                    put("ps", "100")
                    selectedType?.let { put("privilege_type", it.toString()) }
                    put("mobi_app", "web")
                    put("web_location", "333.1196")
                }
                val response = spaceApi.getUpowerRank(params)
                val data = response.data
                if (response.code != 0 || data == null) {
                    val errorMsg = response.message.ifBlank { "获取充电排行失败" }
                    _uiState.update { current ->
                        if (current.selectedPrivilegeType != selectedType) current
                        else current.copy(
                            isLoading = false,
                            error = errorMsg,
                        )
                    }
                    return@launch
                }
                // 部分 UP（upower_state=3 等）web 排行接口为空但 App 端 elec 有数据，
                // 头部" N人为TA充电"正是来自 elec.total。此时回退用 elec 列表渲染。
                if (data.rankInfo.isEmpty() && data.levelInfo.isEmpty()) {
                    val fallback = fetchElecFallback()
                    if (fallback != null) {
                        _uiState.update { current ->
                            if (current.selectedPrivilegeType != selectedType) current
                            else current.copy(
                                isLoading = false,
                                error = null,
                                items = fallback.first,
                                totalCount = fallback.second,
                            )
                        }
                        return@launch
                    }
                }
                val resolvedTabs = if (!hasResolvedTabs && selectedType == null && data.levelInfo.size > 1) {
                    hasResolvedTabs = true
                    data.levelInfo.map { LevelTab(it.privilegeType, it.name, it.memberTotal) }
                } else {
                    null
                }
                val computedTotal = data.levelInfo.sumOf { it.memberTotal.toLong() }
                    .takeIf { it > 0L }
                _uiState.update { current ->
                    if (current.selectedPrivilegeType != selectedType) current
                    else current.copy(
                        isLoading = false,
                        error = null,
                        items = data.rankInfo,
                        levelTabs = resolvedTabs ?: current.levelTabs,
                        totalCount = computedTotal ?: current.totalCount,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SpaceUpowerRank", "load failed: ${e.message}")
                val errorMsg = e.message ?: "网络请求失败"
                _uiState.update { current ->
                    if (current.selectedPrivilegeType != selectedType) current
                    else current.copy(
                        isLoading = false,
                        error = errorMsg,
                    )
                }
            }
        }
    }
}
