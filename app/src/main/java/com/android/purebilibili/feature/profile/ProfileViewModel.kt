package com.android.purebilibili.feature.profile

import com.android.purebilibili.core.store.home.HomeWallpaperSelection
import com.android.purebilibili.core.ui.wallpaper.WallpaperTransform
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.network.getSpaceAggregate
import com.android.purebilibili.core.network.DynamicDeleteRequest
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.StoredAccountSession
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.refresh.FavoriteFolderUpdateBus
import com.android.purebilibili.core.store.AccountSessionIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource
import com.android.purebilibili.data.model.response.NavData
import com.android.purebilibili.data.model.response.SpaceAggregateImages
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.data.model.response.WbiImg
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.bangumi.MY_FOLLOW_TYPE_BANGUMI
import com.android.purebilibili.feature.list.resolveFavoriteFolderMediaId
import com.android.purebilibili.feature.home.UserState
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.net.SocketTimeoutException

import android.net.Uri
import android.content.Context
import com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform
import com.android.purebilibili.core.store.SettingsManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(
        val user: UserState,
        val favoriteFolders: List<FavFolder> = emptyList(),
        val space: ProfileSpaceUiState = ProfileSpaceUiState(),
        val editableAccount: ProfileEditableAccountState = ProfileEditableAccountState(),
        val accountHeaderImages: SpaceAggregateImages? = null,
    ) : ProfileUiState()
    // LoggedOut 代表“当前是游客/未登录状态”，UI 应该显示“去登录”
    // [Modified] Support wallpaper in guest mode
    data class LoggedOut(val topPhoto: String = "") : ProfileUiState()
    // 🔧 [新增] 网络错误状态 — 保持登录但显示离线提示
    data class Error(val message: String) : ProfileUiState()
}


internal fun shouldStartProfileLoad(
    hasLoadedOnce: Boolean,
    isLoadInFlight: Boolean,
    force: Boolean
): Boolean {
    if (force) return true
    return !hasLoadedOnce && !isLoadInFlight
}

internal fun shouldForceProfileLoadForAccountSessionRefresh(
    isCurrentPage: Boolean,
    accountSessionRefreshGeneration: Int,
    handledAccountSessionRefreshGeneration: Int
): Boolean {
    return isCurrentPage &&
        accountSessionRefreshGeneration > handledAccountSessionRefreshGeneration
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _accounts = MutableStateFlow<List<StoredAccountSession>>(emptyList())
    val accounts = _accounts.asStateFlow()
    private val _activeAccountMid = MutableStateFlow<Long?>(null)
    val activeAccountMid = _activeAccountMid.asStateFlow()
    private val _playbackAccountMid = MutableStateFlow<Long?>(null)
    val playbackAccountMid = _playbackAccountMid.asStateFlow()
    private val _purposeAccountMids = MutableStateFlow<Map<com.android.purebilibili.core.store.AccountPurpose, Long?>?>(null)
    val purposeAccountMids = _purposeAccountMids.asStateFlow()
    private val accountSnapshotGeneration = java.util.concurrent.atomic.AtomicLong()
    private var hasLoadedProfileOnce = false
    private var isProfileLoadInFlight = false
    private var profileLoadGeneration = 0L
    private var currentProfileWbiImg: WbiImg? = null
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var profileLoadJob: Job? = null
    private var isProfilePageActive = false
    private var contentReadyGeneration = -1L
    private var aggregateReadyGeneration = -1L
    private val requestedContent = mutableSetOf<ProfileSpaceMainTab>()
    private val contentJobs = mutableMapOf<ProfileSpaceMainTab, Job>()

    fun setProfilePageActive(active: Boolean) {
        isProfilePageActive = active
        if (active) loadVisibleProfileContent()
    }

    private fun resetProfileContentRequests() {
        contentReadyGeneration = -1L
        aggregateReadyGeneration = -1L
        contentJobs.values.forEach { it.cancel() }
        contentJobs.clear()
        requestedContent.clear()
    }

    private fun loadVisibleProfileContent() {
        if (!isProfilePageActive || contentReadyGeneration != profileLoadGeneration) return
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (current.space.selectedTab == ProfileSpaceMainTab.HOME && aggregateReadyGeneration != profileLoadGeneration) return
        resolveProfileContentLoadTargets(current.space).forEach(::requestProfileContent)
    }

    private fun requestProfileContent(tab: ProfileSpaceMainTab) {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (!requestedContent.add(tab)) return
        val generation = profileLoadGeneration
        val mid = current.user.mid
        updateProfileSuccess(generation, mid) { it.copy(space = it.space.copy(loadingTabs = it.space.loadingTabs + tab, contentErrors = it.space.contentErrors - tab)) }
        contentJobs[tab] = accountRequests.launch {
            try {
                when (tab) {
                    ProfileSpaceMainTab.CONTRIBUTION -> loadProfileContributions(generation, mid, currentProfileWbiImg)
                    ProfileSpaceMainTab.FAVORITE -> loadProfileFavoriteFolders(generation, mid)
                    ProfileSpaceMainTab.BANGUMI -> loadProfileBangumi(generation, mid)
                    ProfileSpaceMainTab.DYNAMIC -> loadProfileDynamics(generation, mid)
                    ProfileSpaceMainTab.HOME -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateProfileSuccess(generation, mid) {
                    it.copy(space = it.space.copy(contentErrors = it.space.contentErrors + (tab to (e.message ?: "加载失败"))))
                }
            } finally {
                updateProfileSuccess(generation, mid) { it.copy(space = it.space.copy(loadingTabs = it.space.loadingTabs - tab)) }
            }
        }
    }

    private fun isCurrentProfileLoad(generation: Long): Boolean =
        generation == profileLoadGeneration && accountRequests.isCurrent()

    init {
        refreshSavedAccounts()
        accountRequests.observe {
            profileLoadGeneration += 1L
            resetProfileContentRequests()
            hasLoadedProfileOnce = false
            isProfileLoadInFlight = false
            currentProfileWbiImg = null
            _uiState.value = if (TokenManager.sessDataCache.isNullOrEmpty()) {
                ProfileUiState.LoggedOut()
            } else {
                ProfileUiState.Loading
            }
            refreshSavedAccounts()
            if (isProfilePageActive) loadProfile()
        }
        viewModelScope.launch {
            FavoriteFolderUpdateBus.changes.collect { update ->
                val current = _uiState.value as? ProfileUiState.Success ?: return@collect
                if (!accountRequests.isCurrent() || update.ownerMid <= 0L ||
                    TokenManager.midCache != update.ownerMid || current.user.mid != update.ownerMid
                ) return@collect
                val folderIndex = current.space.favoriteFolders.indexOfFirst { folder ->
                    folder.source == FavFolderSource.OWNED &&
                        resolveFavoriteFolderMediaId(folder) == update.mediaId
                }
                if (folderIndex < 0) return@collect
                val updatedFolders = current.space.favoriteFolders.toMutableList()
                updatedFolders[folderIndex] = updatedFolders[folderIndex].copy(
                    title = update.info.title,
                    intro = update.info.intro,
                    cover = update.info.cover,
                    attr = update.info.attr,
                )
                _uiState.value = current.copy(
                    favoriteFolders = updatedFolders,
                    space = current.space.copy(favoriteFolders = updatedFolders),
                )
            }
        }
    }

    fun refreshSavedAccounts() {
        val generation = accountSnapshotGeneration.incrementAndGet()
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = AccountSessionStore.getAccounts(context)
            val activeAccountMid = AccountSessionStore.getActiveAccountMid(context)
            val playbackAccountMid = AccountSessionStore.getPlaybackAccountMid(context)
            val purposes = com.android.purebilibili.core.store.AccountPurpose.entries.associateWith {
                AccountSessionStore.getPurposeAccountMid(context, it)
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation != accountSnapshotGeneration.get()) return@withContext
                _accounts.value = accounts
                _activeAccountMid.value = activeAccountMid
                _playbackAccountMid.value = playbackAccountMid
                _purposeAccountMids.value = purposes
            }
        }
    }

    fun setPlaybackAccount(mid: Long?, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = AccountSessionStore.setPlaybackAccountMid(context, mid)
            withContext(Dispatchers.Main.immediate) {
                if (updated) {
                    refreshSavedAccounts()
                    onSuccess()
                } else {
                    onFailure("播放账号不可用，请重新登录后再试")
                }
            }
        }
    }

    fun setPurposeAccount(
        purpose: com.android.purebilibili.core.store.AccountPurpose,
        mid: Long?, onSuccess: () -> Unit, onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = AccountSessionStore.setPurposeAccountMid(getApplication(), purpose, mid)
            withContext(Dispatchers.Main.immediate) {
                if (updated) {
                    refreshSavedAccounts()
                    com.android.purebilibili.data.repository.VideoRepository.clearVideoDetailCaches()
                    onSuccess()
                } else onFailure("账号不可用，请重新登录后再试")
            }
        }
    }

    fun loadProfile(force: Boolean = false) {
        if (!accountRequests.isCurrent()) return
        if (!shouldStartProfileLoad(
                hasLoadedOnce = hasLoadedProfileOnce,
                isLoadInFlight = isProfileLoadInFlight,
                force = force
            )
        ) {
            return
        }
        hasLoadedProfileOnce = true
        profileLoadJob?.cancel()
        isProfileLoadInFlight = true
        val generation = ++profileLoadGeneration
        resetProfileContentRequests()
        val requestedMid = TokenManager.midCache
        val current = _uiState.value as? ProfileUiState.Success
        _uiState.value = if (current != null && current.user.mid == requestedMid) {
            current.copy(space = current.space.copy(isLoading = true, message = null))
        } else {
            ProfileUiState.Loading
        }
        val requestedIdentity = accountRequests.identity
        profileLoadJob = accountRequests.launch {
            performProfileLoad(generation, requestedMid, requestedIdentity)
        }
    }

    private suspend fun performProfileLoad(
        generation: Long,
        requestedMid: Long?,
        requestedIdentity: AccountSessionIdentity
    ) {
        var customBgUri = ""
        try {
            customBgUri = readProfileBackgroundUri()
            if (TokenManager.sessDataCache.isNullOrEmpty()) {
                if (isCurrentProfileLoad(generation)) {
                    _uiState.value = ProfileUiState.LoggedOut(
                        topPhoto = resolveProfileTopPhoto(customBgUri)
                    )
                }
                return
            }
            val data = NetworkModule.api.getNavInfo().data
            currentCoroutineContext().ensureActive()
            if (!isCurrentProfileLoad(generation)) return
            if (requestedMid != null && data?.isLogin == true && data.mid != requestedMid) return
            if (data == null || !data.isLogin) {
                clearInvalidProfileSession(generation, requestedIdentity, customBgUri)
                return
            }
            showProfileIdentity(generation, data, customBgUri)
            currentProfileWbiImg = data.wbi_img
            contentReadyGeneration = generation
            loadVisibleProfileContent()
            profileRequestOrNull { persistProfileSession(generation, data) }
            currentCoroutineContext().ensureActive()
            if (!isCurrentProfileLoad(generation)) return
            loadProfileEnrichment(generation, data.mid, data.wbi_img)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleProfileLoadFailure(generation, requestedMid, customBgUri, e)
        } finally {
            if (isCurrentProfileLoad(generation)) {
                isProfileLoadInFlight = false
            }
        }
    }

    private fun showProfileIdentity(
        generation: Long,
        data: NavData,
        customBgUri: String
    ) {
        if (!isCurrentProfileLoad(generation)) return
        val cached = (_uiState.value as? ProfileUiState.Success)
            ?.takeIf { it.user.mid == data.mid }
        val user = UserState(
            isLogin = true,
            face = data.face,
            name = data.uname,
            mid = data.mid,
            level = data.level_info.current_level,
            currentLevelMinExp = data.level_info.current_min ?: 0,
            currentLevelExp = data.level_info.current_exp ?: 0,
            nextLevelExp = data.level_info.next_exp ?: 0,
            coin = data.money,
            bcoin = data.wallet.bcoin_balance,
            following = cached?.user?.following ?: 0,
            follower = cached?.user?.follower ?: 0,
            dynamic = cached?.user?.dynamic ?: 0,
            isVip = data.vip.status == 1,
            vipLabel = data.vip.label.text,
            topPhoto = resolveProfileTopPhoto(customBgUri)
        )
        _uiState.value = ProfileUiState.Success(
            user = user,
            favoriteFolders = cached?.favoriteFolders.orEmpty(),
            space = cached?.space?.copy(
                isLoading = true,
                message = null,
                contributionLoadState = ProfileContributionLoadState.IDLE,
                loadingTabs = emptySet(),
                contentErrors = emptyMap()
            ) ?: ProfileSpaceUiState(
                isLoading = true,
                contributionLoadState = ProfileContributionLoadState.IDLE
            ),
            editableAccount = cached?.editableAccount
                ?: resolveProfileEditableAccountState(account = null, user = user),
            accountHeaderImages = cached?.accountHeaderImages,
        )
    }

    private suspend fun persistProfileSession(generation: Long, data: NavData) {
        if (!isCurrentProfileLoad(generation)) return
        if (TokenManager.midCache != data.mid) {
            TokenManager.saveMid(getApplication(), data.mid)
            return
        }
        TokenManager.saveVipStatus(data.vip.status == 1)
        AccountSessionStore.upsertCurrentAccount(getApplication(), data)
        refreshSavedAccounts()
    }

    private suspend fun loadProfileEnrichment(
        generation: Long,
        mid: Long,
        wbiImg: WbiImg?
    ) {
        try {
            supervisorScope {
                listOf(
                    launch { loadProfileStats(generation, mid) },
                    launch { loadProfileAccount(generation, mid) },
                    launch { loadProfileSpaceInfo(generation, mid, wbiImg) },
                    launch {
                        loadProfileAggregate(generation, mid)
                        if (isCurrentProfileLoad(generation)) {
                            aggregateReadyGeneration = generation
                            loadVisibleProfileContent()
                        }
                    }
                ).joinAll()
            }
        } finally {
            updateProfileSuccess(generation, mid) { current ->
                current.copy(space = current.space.copy(isLoading = false))
            }
        }
    }

    private suspend fun loadProfileStats(generation: Long, mid: Long) {
        val stats = profileRequestOrNull { NetworkModule.api.getNavStat().data } ?: return
        updateProfileSuccess(generation, mid) { current ->
            current.copy(
                user = current.user.copy(
                    following = stats.following,
                    follower = stats.follower,
                    dynamic = stats.dynamic_count
                )
            )
        }
    }

    private suspend fun loadProfileAccount(generation: Long, mid: Long) {
        val account = profileRequestOrNull { NetworkModule.api.getMemberAccount().data } ?: return
        updateProfileSuccess(generation, mid) { current ->
            current.copy(
                editableAccount = current.editableAccount.copy(
                    name = account.uname.ifBlank { current.user.name },
                    birthday = account.birthday,
                    sex = account.sex,
                    sign = account.sign.ifBlank { current.editableAccount.sign }
                )
            )
        }
    }

    private suspend fun loadProfileSpaceInfo(
        generation: Long,
        mid: Long,
        wbiImg: WbiImg?
    ) {
        val info = profileRequestOrNull { fetchProfileSpaceInfo(mid, wbiImg) } ?: return
        updateProfileSuccess(generation, mid) { current ->
            current.copy(
                editableAccount = current.editableAccount.copy(
                    sign = current.editableAccount.sign.ifBlank { info.sign },
                    ipLocation = info.ipLocation.orEmpty()
                )
            )
        }
    }

    private suspend fun loadProfileAggregate(generation: Long, mid: Long) {
        val aggregate = profileRequestOrNull {
            NetworkModule.spaceApi.getSpaceAggregate(mid).takeIf { it.code == 0 }?.data
        } ?: return
        updateProfileSuccess(generation, mid) { current ->
            val nextSpace = mergeProfileAggregateState(current.space, aggregate)
            current.copy(
                favoriteFolders = nextSpace.favoriteFolders,
                space = nextSpace,
                accountHeaderImages = aggregate.images,
                editableAccount = current.editableAccount.copy(
                    sign = current.editableAccount.sign.ifBlank {
                        aggregate.card?.sign.orEmpty()
                    }
                )
            )
        }
    }

    private suspend fun loadProfileContributions(
        generation: Long,
        mid: Long,
        wbiImg: WbiImg?
    ) {
        updateProfileSuccess(generation, mid) { current ->
            current.copy(
                space = current.space.copy(
                    contributionLoadState = ProfileContributionLoadState.LOADING
                )
            )
        }
        val videos = profileRequestOrNull {
            fetchProfileContributionVideos(mid, wbiImg)
        }
        if (videos == null) {
            updateProfileSuccess(generation, mid) { current ->
                current.copy(
                    space = current.space.copy(
                        contributionLoadState = ProfileContributionLoadState.ERROR
                    )
                )
            }
            return
        }
        updateProfileSuccess(generation, mid) { latest ->
            latest.copy(
                space = mergeProfileContributionVideoState(
                    current = latest.space,
                    videos = videos.first,
                    totalCount = videos.second
                )
            )
        }
    }

    fun retrySelectedProfileContent() {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        val tab = current.space.selectedTab
        contentJobs[tab]?.cancel()
        requestedContent.remove(tab)
        requestProfileContent(tab)
    }

    private suspend fun fetchProfileContributionVideos(
        mid: Long,
        wbiImg: WbiImg?
    ): Pair<List<SpaceVideoItem>, Int>? {
        val imgUrl = wbiImg?.img_url.orEmpty()
        val subUrl = wbiImg?.sub_url.orEmpty()
        val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
        val subKey = subUrl.substringAfterLast("/").substringBefore(".")
        if (imgKey.isBlank() || subKey.isBlank()) return null
        val params = WbiUtils.sign(
            mapOf(
                "mid" to mid.toString(),
                "pn" to "1",
                "ps" to PROFILE_CONTRIBUTION_PAGE_SIZE.toString(),
                "order" to "pubdate"
            ),
            imgKey,
            subKey
        )
        val response = NetworkModule.spaceApi.getSpaceVideos(params)
        val data = response.data ?: return null
        if (response.code != 0) return null
        return data.list.vlist to data.page.count
    }

    private suspend fun loadProfileFavoriteFolders(generation: Long, mid: Long) {
        val folders = FavoriteRepository.getFavFolders(mid).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isCurrentProfileLoad(generation)) return
        var mergedFolders = emptyList<FavFolder>()
        updateProfileSuccess(generation, mid) { current ->
            val nextSpace = mergeProfileFavoriteFolderState(current.space, folders)
            mergedFolders = nextSpace.favoriteFolders
            current.copy(
                favoriteFolders = nextSpace.favoriteFolders,
                space = nextSpace
            )
        }
        loadProfileFavoritePreviewCovers(generation, mid, mergedFolders)
    }

    private suspend fun loadProfileFavoritePreviewCovers(
        generation: Long,
        mid: Long,
        folders: List<FavFolder>
    ) {
        val targets = resolveProfileFavoritePreviewCoverTargets(folders)
        if (targets.isEmpty()) return

        // 限制并发：收藏夹 resource/list 对突发并发较敏感，易触发 412/429
        val coverFetchSemaphore = Semaphore(2)
        val coversByMediaId = supervisorScope {
            targets.map { target ->
                async {
                    coverFetchSemaphore.withPermit {
                        val cover = FavoriteRepository.getFavoriteList(
                            mediaId = target.mediaId,
                            pn = 1,
                            ps = 1
                        ).getOrNull()
                            ?.medias
                            ?.firstOrNull { it.cover.isNotBlank() }
                            ?.cover
                            .orEmpty()
                        target.mediaId to cover
                    }
                }
            }.awaitAll()
        }.filter { (_, cover) -> cover.isNotBlank() }
            .toMap()
        if (coversByMediaId.isEmpty()) return

        updateProfileSuccess(generation, mid) { current ->
            val updatedFolders = mergeProfileFavoritePreviewCovers(
                folders = current.space.favoriteFolders,
                coversByMediaId = coversByMediaId
            )
            current.copy(
                favoriteFolders = updatedFolders,
                space = current.space.copy(favoriteFolders = updatedFolders)
            )
        }
    }

    private suspend fun loadProfileBangumi(generation: Long, mid: Long) {
        val items = BangumiRepository.getMyFollowBangumi(
            type = MY_FOLLOW_TYPE_BANGUMI,
            page = 1,
            pageSize = 12,
            vmid = mid
        ).getOrThrow().list.orEmpty()
        updateProfileSuccess(generation, mid) { current ->
            current.copy(space = mergeProfileBangumiState(current.space, items))
        }
    }

    private suspend fun loadProfileDynamics(generation: Long, mid: Long) {
        val response = NetworkModule.spaceApi.getSpaceDynamic(mid)
        check(response.code == 0) { response.message.ifBlank { "动态加载失败" } }
        val data = checkNotNull(response.data) { "动态响应不完整" }
        val items = com.android.purebilibili.data.repository.filterGoodsSpaceDynamics(data.items)
        updateProfileSuccess(generation, mid) { current ->
            current.copy(space = mergeProfileDynamicState(current.space, items))
        }
    }

    private fun updateProfileSuccess(
        generation: Long,
        mid: Long,
        transform: (ProfileUiState.Success) -> ProfileUiState.Success
    ) {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (!isCurrentProfileLoad(generation) ||
            !shouldApplyProfileLoadResult(generation, profileLoadGeneration, mid, current.user.mid)
        ) {
            return
        }
        _uiState.value = transform(current)
    }

    private suspend fun clearInvalidProfileSession(
        generation: Long,
        requestedIdentity: AccountSessionIdentity,
        customBgUri: String
    ) {
        if (!isCurrentProfileLoad(generation)) return
        // Session mutation must outlive the account event it publishes.
        viewModelScope.launch {
            if (!isCurrentProfileLoad(generation) ||
                !accountRequests.isCurrent(requestedIdentity)
            ) return@launch
            TokenManager.clear(getApplication())
            val clearedIdentity = AccountSessionIdentity(
                mid = null,
                generation = requestedIdentity.generation + if (requestedIdentity.mid != null) 1L else 0L
            )
            if (TokenManager.accountIdentity.value != clearedIdentity) return@launch
            AccountSessionStore.clearActiveAccount(getApplication())
            refreshSavedAccounts()
            _uiState.value = ProfileUiState.LoggedOut(
                topPhoto = resolveProfileTopPhoto(customBgUri)
            )
        }
    }

    private fun handleProfileLoadFailure(
        generation: Long,
        requestedMid: Long?,
        customBgUri: String,
        error: Exception
    ) {
        if (!isCurrentProfileLoad(generation)) return
        val cached = (_uiState.value as? ProfileUiState.Success)
            ?.takeIf { it.user.mid == requestedMid }
        if (cached != null) {
            _uiState.value = cached.copy(
                space = cached.space.copy(
                    isLoading = false,
                    message = if (isNetworkError(error)) {
                        "网络不可用，请检查网络连接"
                    } else {
                        "刷新失败，请稍后重试"
                    }
                )
            )
            return
        }
        _uiState.value = if (TokenManager.sessDataCache.isNullOrEmpty()) {
            ProfileUiState.LoggedOut(topPhoto = resolveProfileTopPhoto(customBgUri))
        } else {
            ProfileUiState.Error(
                if (isNetworkError(error)) "网络不可用，请检查网络连接" else "加载失败，点击重试"
            )
        }
    }

    private suspend fun readProfileBackgroundUri(): String {
        return try {
            SettingsManager.getProfileBgUri(getApplication()).first().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun <T> profileRequestOrNull(request: suspend () -> T): T? {
        return try {
            currentCoroutineContext().ensureActive()
            val result = request()
            currentCoroutineContext().ensureActive()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveProfileTopPhoto(customBgUri: String): String {
        if (customBgUri.isBlank()) return ""
        if (!customBgUri.startsWith("file://")) return customBgUri
        return runCatching {
            val file = File(Uri.parse(customBgUri).path.orEmpty())
            if (file.exists()) "$customBgUri?t=${file.lastModified()}" else customBgUri
        }.getOrDefault(customBgUri)
    }

    private suspend fun fetchProfileSpaceInfo(mid: Long, wbiImg: WbiImg?): SpaceUserInfo? {
        val imgUrl = wbiImg?.img_url.orEmpty()
        val subUrl = wbiImg?.sub_url.orEmpty()
        val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
        val subKey = subUrl.substringAfterLast("/").substringBefore(".")
        if (imgKey.isBlank() || subKey.isBlank()) return null
        val params = WbiUtils.sign(mapOf("mid" to mid.toString()), imgKey, subKey)
        val response = NetworkModule.spaceApi.getSpaceInfo(params)
        return if (response.code == 0) response.data else null
    }

    fun selectProfileSpaceTab(tab: ProfileSpaceMainTab) {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (current.space.selectedTab == tab) return
        _uiState.value = current.copy(space = current.space.copy(selectedTab = tab))
        loadVisibleProfileContent()
    }

    fun clearProfileSpaceMessage() {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        _uiState.value = current.copy(space = current.space.copy(signSaveMessage = null, message = null))
    }

    fun deleteProfileDynamic(action: DynamicDeleteAction, onResult: (Boolean, String) -> Unit) {
        val identity = accountRequests.identity
        accountRequests.launch {
            try {
                if (action.dynamicId.isBlank()) {
                    onResult(false, "无法删除该动态")
                    return@launch
                }
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrBlank()) {
                    onResult(false, "请先登录")
                    return@launch
                }

                val response = NetworkModule.dynamicApi.deleteDynamic(
                    csrf = csrf,
                    body = DynamicDeleteRequest(
                        dyn_id_str = action.dynamicId,
                        dyn_type = action.dynType,
                        rid_str = action.rid
                    )
                )
                currentCoroutineContext().ensureActive()
                if (!accountRequests.isCurrent(identity)) return@launch
                if (response.code == 0) {
                    removeProfileDynamic(action.dynamicId)
                    onResult(true, "已删除动态")
                } else {
                    onResult(false, response.message.ifBlank { "删除失败" })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!accountRequests.isCurrent(identity)) return@launch
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    private fun removeProfileDynamic(dynamicId: String) {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        val updatedItems = current.space.dynamicItems.filterNot { item ->
            item.id_str == dynamicId ||
                item.modules.module_more?.three_point_items.orEmpty().any { menu ->
                    menu.params?.dyn_id_str == dynamicId
                }
        }
        _uiState.value = current.copy(
            space = current.space.copy(dynamicItems = updatedItems)
        )
    }

    fun updateProfileSign(sign: String) {
        val validationError = validateProfileSign(sign)
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (validationError != null) {
            _uiState.value = current.copy(
                space = current.space.copy(signSaveMessage = validationError)
            )
            return
        }

        performAccountEdit("签名已提交，等待审核后生效") { identity ->
            ProfileAccountRepository.updateSign(identity, sign.trim())
        }
    }

    internal fun updateAccountField(field: ProfileAccountField, value: String) {
        val normalized = value.trim()
        if (field == ProfileAccountField.NAME && (normalized.isEmpty() || normalized.length > 16)) {
            val current = _uiState.value as? ProfileUiState.Success ?: return
            _uiState.value = current.copy(space = current.space.copy(signSaveMessage = "昵称须为 1 至 16 个字符"))
            return
        }
        performAccountEdit("资料修改成功") { identity ->
            ProfileAccountRepository.update(identity, field, normalized)
        }
    }

    fun updateAccountAvatar(uri: Uri) {
        performAccountEdit("头像已提交，资料刷新可能需要一些时间") { identity ->
            ProfileAccountRepository.uploadAvatar(getApplication(), identity, uri)
        }
    }

    private fun performAccountEdit(message: String, action: suspend (AccountSessionIdentity) -> Unit) {
        val identity = accountRequests.identity
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (current.space.isSavingSign) return
        _uiState.value = current.copy(space = current.space.copy(isSavingSign = true, signSaveMessage = null))
        accountRequests.launch {
            try {
                action(identity)
                currentCoroutineContext().ensureActive()
                if (!accountRequests.isCurrent(identity)) return@launch
                val latest = _uiState.value as? ProfileUiState.Success ?: return@launch
                _uiState.value = latest.copy(space = latest.space.copy(isSavingSign = false, signSaveMessage = message))
                loadProfile(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!accountRequests.isCurrent(identity)) return@launch
                val latest = _uiState.value as? ProfileUiState.Success ?: return@launch
                _uiState.value = latest.copy(space = latest.space.copy(isSavingSign = false, signSaveMessage = error.message ?: "资料修改失败"))
            }
        }
    }
    
    /**
     * 更新自定义背景图
     * 将选中的图片复制到应用私有目录，并更新设置
     */
    fun updateCustomBackground(
        uri: Uri, 
        mobileTransform: ProfileWallpaperTransform = ProfileWallpaperTransform(),
        tabletTransform: ProfileWallpaperTransform = ProfileWallpaperTransform()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val wallpaper = importWallpaperMedia(context, uri, File(context.filesDir, "profile_wallpaper"))
                SettingsManager.setProfileBgTransform(context, false, mobileTransform)
                SettingsManager.setProfileBgTransform(context, true, tabletTransform)
                SettingsManager.setProfileBgUri(context, Uri.fromFile(wallpaper).toString())
                loadProfile(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    android.widget.Toast.makeText(getApplication(), error.message ?: "壁纸导入失败", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 判断是否为网络相关错误
     */
    private fun isNetworkError(e: Exception): Boolean {
        return e is UnknownHostException ||
               e is SocketTimeoutException ||
               e is java.net.ConnectException ||
               e.cause is UnknownHostException ||
               e.cause is SocketTimeoutException
    }

    fun logout() {
        viewModelScope.launch {
            val identity = TokenManager.accountIdentity.value
            profileLoadGeneration += 1L
            isProfileLoadInFlight = false
            // retain background
            val customBgUri = SettingsManager.getProfileBgUri(getApplication()).first() ?: ""
            if (TokenManager.accountIdentity.value != identity) return@launch
            AccountSessionStore.upsertCurrentAccount(getApplication())
            if (TokenManager.accountIdentity.value != identity) return@launch
            TokenManager.clear(getApplication())
            val clearedIdentity = AccountSessionIdentity(
                mid = null,
                generation = identity.generation + if (identity.mid != null) 1L else 0L
            )
            if (TokenManager.accountIdentity.value != clearedIdentity) return@launch
            AccountSessionStore.clearActiveAccount(getApplication())
            refreshSavedAccounts()
            _uiState.value = ProfileUiState.LoggedOut(topPhoto = customBgUri)
            com.android.purebilibili.core.util.AnalyticsHelper.syncUserContext(
                mid = null,
                isVip = false,
                privacyModeEnabled = SettingsManager.isPrivacyModeEnabledSync(getApplication())
            )
            //  记录登出事件
            com.android.purebilibili.core.util.AnalyticsHelper.logLogout()
        }
    }

    fun switchAccount(
        mid: Long,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (mid == TokenManager.midCache) {
                onSuccess()
                return@launch
            }

            val switched = AccountSessionStore.activateAccount(getApplication(), mid)
            if (!switched) {
                onFailure("切换账号失败")
                return@launch
            }

            refreshSavedAccounts()
            onSuccess()
        }
    }

    fun removeStoredAccount(
        mid: Long,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (TokenManager.midCache == mid) {
            onFailure("请先切换到其他账号后再移除当前账号")
            return
        }

        val removed = AccountSessionStore.removeAccount(getApplication(), mid)
        if (removed) {
            refreshSavedAccounts()
            onSuccess()
        } else {
            onFailure("移除账号失败")
        }
    }
    
    // [新增] 官方壁纸列表
    private val _officialWallpapers = MutableStateFlow<List<com.android.purebilibili.data.model.response.SplashItem>>(emptyList())
    val officialWallpapers = _officialWallpapers.asStateFlow()
    private val _officialWallpapersLoading = MutableStateFlow(false)
    val officialWallpapersLoading = _officialWallpapersLoading.asStateFlow()
    private val _officialWallpapersError = MutableStateFlow<String?>(null)
    val officialWallpapersError = _officialWallpapersError.asStateFlow()

    fun loadOfficialWallpapers() {
        if (_officialWallpapersLoading.value) return
        viewModelScope.launch {
            _officialWallpapersLoading.value = true
            _officialWallpapersError.value = null
            try {
                val result = com.android.purebilibili.data.repository.SplashRepository.getOfficialWallpapers()
                if (result.isSuccess) {
                    val catalog = result.getOrThrow()
                    if (catalog.items.isNotEmpty() || catalog.failedSources.isEmpty()) {
                        _officialWallpapers.value = catalog.items
                    }
                    _officialWallpapersError.value = catalog.failedSources.takeIf { it.isNotEmpty() }
                        ?.joinToString("、")?.let { "${it}未加载成功，当前列表不完整，请刷新重试" }
                } else {
                    _officialWallpapersError.value = result.exceptionOrNull()?.message ?: "加载失败，点击重试"
                }
            } finally {
                _officialWallpapersLoading.value = false
            }
        }
    }

    // [新增] 搜索壁纸
    private val _searchWallpapers = MutableStateFlow<List<com.android.purebilibili.data.model.response.SplashItem>>(emptyList())
    val searchWallpapers = _searchWallpapers.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading = _searchLoading.asStateFlow()

    fun searchWallpapers(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) return@launch
            _searchLoading.value = true
            try {
                // 使用通用搜索接口搜索 "query + 壁纸"
                val searchApi = NetworkModule.searchApi
                // 这里调用 searchAll 或 searchType 接口，假设 searchAll 可用
                // 注意：B站搜索 API 比较复杂，这里简化处理，假设搜索 "壁纸" 相关内容
                // 实际可能需要解析 SearchResponse 并转换为 SplashItem
                
                // 构造搜索参数
                val params = mutableMapOf<String, String>()
                params["keyword"] = "$query 壁纸"
                
                // 模拟：由于没有直接的 searchWallpaper API，我们这里临时复用 searchAll
                // 真实场景下需解析 SearchResponse 中的 result.video 或 result.article
                // 为了演示，这里先留空或模拟一些数据，或者如果 SearchApi 返回结构匹配的话
                
                // [暂缓] 实际搜索逻辑需要详细解析 SearchResponse。
                // 鉴于 SearchResponse 结构较复杂，我们先模拟一个空列表或 TODO
                // 等待 SearchResponse 结构完全确认。
                
                // 既然用户想要 "搜索B站开屏壁纸"，通常这些资源不在标准搜索里直接以图片形式提供。
                // 我们可以搜 "垂直" 视频的封面? 
                // 让我们尝试搜 "draw" 栏目?
                
                // 简易方案：调用 searchAll，取 result.result 里的数据（需适配）
                // 暂时: 仅作为 UI 展示，不做真实网络请求以免崩溃，或者请求后打 Log
                
                // 真实实现：
                 val result = searchApi.searchAll(params)
                 // TODO: Parse result to SplashItem list
                 // _searchWallpapers.value = parsedList
                 
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _searchLoading.value = false
            }
        }
    }

    // [New] 壁纸保存状态
    private val _wallpaperSaveState = MutableStateFlow<WallpaperSaveState>(WallpaperSaveState.Idle)
    val wallpaperSaveState = _wallpaperSaveState.asStateFlow()

    /**
     * 保存壁纸 (下载并设置为背景)
     */
    // [New] Alignment State
    fun getProfileBgAlignment(isTablet: Boolean) = SettingsManager.getProfileBgAlignment(getApplication(), isTablet)
    fun getProfileBgTransform(isTablet: Boolean) = SettingsManager.getProfileBgTransform(getApplication(), isTablet)
    fun getProfileBgUri() = SettingsManager.getProfileBgUri(getApplication())

    fun clearCustomBackground() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SettingsManager.setProfileBgUri(context, null)
            SettingsManager.resetProfileBgTransform(context)
            runCatching {
                File(context.filesDir, "images/profile_bg.jpg").delete()
            }
            _uiState.update { state ->
                when (state) {
                    is ProfileUiState.Success -> state.copy(user = state.user.copy(topPhoto = ""))
                    is ProfileUiState.LoggedOut -> state.copy(topPhoto = "")
                    else -> state
                }
            }
            loadProfile(force = true)
        }
    }

    /**
     * 保存壁纸 (下载并设置为背景)
     * 支持传入对齐参数
     */
    fun saveWallpaper(
        url: String, 
        mobileTransform: ProfileWallpaperTransform = ProfileWallpaperTransform(),
        tabletTransform: ProfileWallpaperTransform = ProfileWallpaperTransform(),
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _wallpaperSaveState.value = WallpaperSaveState.Loading
            try {
                // 保存对齐设置
                val context = getApplication<Application>()
                SettingsManager.setProfileBgTransform(context, false, mobileTransform)
                SettingsManager.setProfileBgTransform(context, true, tabletTransform)
                
                // 修复 URL 协议 (强制 HTTPS)
                var finalUrl = url
                if (finalUrl.startsWith("//")) {
                    finalUrl = "https:$finalUrl"
                } else if (finalUrl.startsWith("http://")) {
                    finalUrl = finalUrl.replace("http://", "https://")
                }
                
                val request = okhttp3.Request.Builder().url(finalUrl).build()
                val response = NetworkModule.okHttpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val imagesDir = File(context.filesDir, "images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val destFile = File(imagesDir, "profile_bg.jpg")
                    
                    FileOutputStream(destFile).use { output ->
                        response.body.byteStream().copyTo(output)
                    }
                    
                    val savedUri = Uri.fromFile(destFile).toString()
                    SettingsManager.setProfileBgUri(context, savedUri)
                    
                    loadProfile(force = true) // 刷新
                    
                    withContext(Dispatchers.Main.immediate) {
                        _wallpaperSaveState.value = WallpaperSaveState.Success
                        onComplete()
                    }
                } else {
                    _wallpaperSaveState.value = WallpaperSaveState.Error("下载失败: ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _wallpaperSaveState.value = WallpaperSaveState.Error(e.message ?: "保存出错")
            } finally {
                // Delay reset to allow UI to show success checkmark if needed, but for now we rely on onDismiss
                if (_wallpaperSaveState.value is WallpaperSaveState.Success) {
                     _wallpaperSaveState.value = WallpaperSaveState.Idle
                }
            }
        }
    }

    fun selectOfficialWallpaper(url: String) {
        saveWallpaper(url)
    }
    
    // [New] Splash Wallpaper Logic
    private val _splashSaveState = MutableStateFlow<WallpaperSaveState>(WallpaperSaveState.Idle)
    val splashSaveState = _splashSaveState.asStateFlow()

    fun getSplashAlignment(isTablet: Boolean) = SettingsManager.getSplashAlignment(getApplication(), isTablet)

    fun getSplashScale(isTablet: Boolean) = SettingsManager.getSplashScale(getApplication(), isTablet)

    fun setAsSplashWallpaper(
        url: String,
        saveToGallery: Boolean = false,
        mobileBias: Float? = null,
        tabletBias: Float? = null,
        mobileScale: Float? = null,
        tabletScale: Float? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _splashSaveState.value = WallpaperSaveState.Loading
            try {
                val context = getApplication<Application>()
                var finalUrl = url
                if (finalUrl.startsWith("//")) {
                    finalUrl = "https:$finalUrl"
                } else if (finalUrl.startsWith("http://")) {
                    finalUrl = finalUrl.replace("http://", "https://")
                }

                val request = okhttp3.Request.Builder().url(finalUrl).build()
                val response = NetworkModule.okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    // Read bytes once
                    val bytes = response.body.bytes()
                    
                    // 1. Save to internal splash directory
                    val splashDir = File(context.filesDir, "splash")
                    if (!splashDir.exists()) splashDir.mkdirs()
                    val destFile = File(splashDir, "splash_bg_${System.currentTimeMillis()}.jpg")

                    FileOutputStream(destFile).use { output ->
                        output.write(bytes)
                    }

                    // 2. Update Settings
                    val savedUri = Uri.fromFile(destFile).toString()
                    SettingsManager.setSplashWallpaperUri(context, savedUri)
                    SettingsManager.setSplashEnabled(context, true)
                    mobileBias?.let { SettingsManager.setSplashAlignment(context, isTablet = false, bias = it) }
                    tabletBias?.let { SettingsManager.setSplashAlignment(context, isTablet = true, bias = it) }
                    mobileScale?.let { SettingsManager.setSplashScale(context, isTablet = false, scale = it) }
                    tabletScale?.let { SettingsManager.setSplashScale(context, isTablet = true, scale = it) }

                    // 3. Save to Gallery if requested
                    if (saveToGallery) {
                         saveImageToGallery(context, bytes, "bili_splash_${System.currentTimeMillis()}.jpg")
                    }

                    withContext(Dispatchers.Main.immediate) {
                        _splashSaveState.value = WallpaperSaveState.Success
                        onComplete()
                    }
                } else {
                    _splashSaveState.value = WallpaperSaveState.Error("下载失败: ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _splashSaveState.value = WallpaperSaveState.Error(e.message ?: "保存出错")
            } finally {
                // Delay reset slightly to let UI react if needed, or just reset logic
                 if (_splashSaveState.value is WallpaperSaveState.Success) {
                     _splashSaveState.value = WallpaperSaveState.Idle
                }
            }
        }
    }

    fun setCustomSplashWallpaper(
        uri: String,
        mobileBias: Float? = null,
        tabletBias: Float? = null,
        mobileScale: Float? = null,
        tabletScale: Float? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _splashSaveState.value = WallpaperSaveState.Loading
            try {
                val context = getApplication<Application>()
                val wallpaper = importWallpaperImage(context, Uri.parse(uri), File(context.filesDir, "splash"))
                SettingsManager.setSplashWallpaperUri(context, Uri.fromFile(wallpaper).toString())
                SettingsManager.setSplashEnabled(context, true)
                SettingsManager.setSplashRandomEnabled(context, false)
                mobileBias?.let { SettingsManager.setSplashAlignment(context, isTablet = false, bias = it) }
                tabletBias?.let { SettingsManager.setSplashAlignment(context, isTablet = true, bias = it) }
                mobileScale?.let { SettingsManager.setSplashScale(context, isTablet = false, scale = it) }
                tabletScale?.let { SettingsManager.setSplashScale(context, isTablet = true, scale = it) }

                withContext(Dispatchers.Main.immediate) {
                    _splashSaveState.value = WallpaperSaveState.Success
                    onComplete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                e.printStackTrace()
                _splashSaveState.value = WallpaperSaveState.Error(e.message ?: "保存出错")
            } finally {
                if (_splashSaveState.value is WallpaperSaveState.Success) {
                    _splashSaveState.value = WallpaperSaveState.Idle
                }
            }
        }
    }

    fun setAsHomeWallpaper(
        url: String,
        saveToGallery: Boolean = false,
        mobileTransform: WallpaperTransform = WallpaperTransform(),
        tabletTransform: WallpaperTransform = WallpaperTransform(),
        onComplete: () -> Unit = {}
    ) {
        if (_splashSaveState.value is WallpaperSaveState.Loading) return
        _splashSaveState.value = WallpaperSaveState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            var pendingFile: File? = null
            var committed = false
            try {
                val context = getApplication<Application>()
                val finalUrl = normalizeSplashWallpaperUrl(url)
                val request = okhttp3.Request.Builder().url(finalUrl).build()
                val bytes = NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "下载失败: ${response.code}" }
                    response.body.bytes()
                }
                val directory = File(context.filesDir, "home_wallpaper")
                check(directory.isDirectory || directory.mkdirs()) { "无法创建壁纸目录" }
                val file = File.createTempFile("home_bg_", ".jpg", directory)
                pendingFile = file
                file.outputStream().use { it.write(bytes) }
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                check(options.outWidth > 0 && options.outHeight > 0) { "壁纸图片无效" }
                if (saveToGallery) saveImageToGallery(context, bytes, "bili_home_${System.currentTimeMillis()}.jpg")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                withContext(kotlinx.coroutines.NonCancellable) {
                    SettingsManager.setHomeWallpaperSelection(context, HomeWallpaperSelection(
                        uri = Uri.fromFile(file).toString(),
                        mobileTransform = mobileTransform,
                        tabletTransform = tabletTransform,
                    ))
                    committed = true
                }
                withContext(Dispatchers.Main.immediate) {
                    _splashSaveState.value = WallpaperSaveState.Success
                    onComplete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _splashSaveState.value = WallpaperSaveState.Error(error.message ?: "保存出错")
            } finally {
                if (!committed) pendingFile?.delete()
                if (_splashSaveState.value is WallpaperSaveState.Success) _splashSaveState.value = WallpaperSaveState.Idle
            }
        }
    }

    internal fun setLocalHomeWallpaper(
        uri: String,
        mobileTransform: WallpaperTransform,
        tabletTransform: WallpaperTransform,
        onComplete: () -> Unit,
    ) {
        if (_splashSaveState.value is WallpaperSaveState.Loading) return
        _splashSaveState.value = WallpaperSaveState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            var pendingFile: File? = null
            var committed = false
            try {
                val context = getApplication<Application>()
                val isBundled = uri == ALPINE_PAGE_WALLPAPER_URI
                val savedUri = if (isBundled) {
                    uri
                } else {
                    val file = importWallpaperMedia(context, Uri.parse(uri), File(context.filesDir, "home_wallpaper"))
                    pendingFile = file
                    Uri.fromFile(file).toString()
                }
                withContext(kotlinx.coroutines.NonCancellable) {
                    SettingsManager.setHomeWallpaperSelection(
                        context = context,
                        selection = HomeWallpaperSelection(
                            uri = savedUri,
                            mobileTransform = mobileTransform,
                            tabletTransform = tabletTransform,
                        ),
                        enableOriginalIfOff = isBundled,
                    )
                    committed = true
                }
                withContext(Dispatchers.Main.immediate) {
                    _splashSaveState.value = WallpaperSaveState.Success
                    onComplete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _splashSaveState.value = WallpaperSaveState.Error(error.message ?: "保存出错")
            } finally {
                if (!committed) pendingFile?.delete()
                if (_splashSaveState.value is WallpaperSaveState.Success) _splashSaveState.value = WallpaperSaveState.Idle
            }
        }
    }

    fun setAsSubscriptionArticleWallpaper(
        url: String,
        saveToGallery: Boolean = false,
        onComplete: () -> Unit = {},
    ) {
        if (_splashSaveState.value is WallpaperSaveState.Loading) return
        _splashSaveState.value = WallpaperSaveState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            var downloadedFile: File? = null
            var wallpaper: File? = null
            var saved = false
            try {
                val context = getApplication<Application>()
                val source = if (isUserSelectedSplashWallpaperUri(url)) {
                    Uri.parse(url)
                } else {
                    val request = okhttp3.Request.Builder()
                        .url(normalizeSplashWallpaperUrl(url))
                        .build()
                    val temporaryFile = File.createTempFile("article_wallpaper_", ".img", context.cacheDir)
                    downloadedFile = temporaryFile
                    NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "下载失败: ${response.code}" }
                        response.body.byteStream().use { input ->
                            temporaryFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    Uri.fromFile(temporaryFile)
                }
                val imported = importWallpaperImage(context, source, File(context.filesDir, "subscription_article_wallpaper"))
                wallpaper = imported
                SettingsManager.setSubscriptionArticleWallpaperUri(context, Uri.fromFile(imported).toString())
                saved = true
                SettingsManager.setSubscriptionArticleWallpaperEnabled(context, true)
                if (saveToGallery && !isUserSelectedSplashWallpaperUri(url)) {
                    saveImageToGallery(context, imported.readBytes(), "bili_article_${System.currentTimeMillis()}.jpg")
                }
                withContext(Dispatchers.Main.immediate) {
                    _splashSaveState.value = WallpaperSaveState.Success
                    onComplete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _splashSaveState.value = WallpaperSaveState.Error(error.message ?: "正文壁纸保存失败")
            } finally {
                downloadedFile?.delete()
                if (!saved) wallpaper?.delete()
                if (_splashSaveState.value is WallpaperSaveState.Success ||
                    _splashSaveState.value is WallpaperSaveState.Loading
                ) {
                    _splashSaveState.value = WallpaperSaveState.Idle
                }
            }
        }
    }

    private fun saveImageToGallery(context: Context, bytes: ByteArray, fileName: String) {
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                     put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/BiliPai")
                }
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { output: java.io.OutputStream ->
                    output.write(bytes)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // [New] Easter Egg: Triple Jump Setting
    val tripleJumpEnabled = SettingsManager.getTripleJumpEnabled(application).stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setTripleJumpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            SettingsManager.setTripleJumpEnabled(getApplication(), enabled)
        }
    }
}

sealed class WallpaperSaveState {
    object Idle : WallpaperSaveState()
    object Loading : WallpaperSaveState()
    object Success : WallpaperSaveState()
    data class Error(val message: String) : WallpaperSaveState()
}
