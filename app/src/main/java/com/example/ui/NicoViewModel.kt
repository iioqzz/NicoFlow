package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NicoFlowApp
import com.example.data.local.AccountPreferences
import com.example.data.local.DownloadedVideoEntity
import com.example.data.local.MylistVideoEntity
import com.example.data.local.NicoAccount
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DanmakuSettings
import com.example.data.model.NicoCategory
import com.example.data.model.NicoLiveCategory
import com.example.data.model.NicoSortOrder
import com.example.data.model.NicoVideo
import com.example.data.remote.NicoLiveClient
import com.example.player.PlayerController
import com.example.player.PlayerUiState
import okhttp3.WebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    LIVE,
    SEARCH,
    DOWNLOADS,
    LIBRARY
}

data class LiveUiState(
    val selectedCategory: NicoLiveCategory = NicoLiveCategory.ALL,
    val lives: List<NicoVideo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class HomeFeedTab(val label: String) {
    LIVE("直播"),
    RECOMMEND("推荐"),
    HOT("热门")
}

data class HomeUiState(
    val feedTab: HomeFeedTab = HomeFeedTab.RECOMMEND,
    val selectedCategory: NicoCategory = NicoCategory.ALL,
    val hotRecentDays: Int? = null,
    val videos: List<NicoVideo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val sortOrder: NicoSortOrder = NicoSortOrder.VIEWS,
    val results: List<NicoVideo> = emptyList(),
    val recentQueries: List<String> = listOf("VOCALOID", "初音ミク", "東方", "ゲーム実況", "アニメ"),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

class NicoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NicoFlowApp
    private val repository = app.repository
    private val accountPrefs = AccountPreferences(application)
    private val liveClient = NicoLiveClient(app.apiClient.okHttpClient)
    val playerController: PlayerController = app.playerController

    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _isPlayerScreenOpen = MutableStateFlow(false)
    val isPlayerScreenOpen: StateFlow<Boolean> = _isPlayerScreenOpen.asStateFlow()

    private val _isLoginScreenOpen = MutableStateFlow(false)
    val isLoginScreenOpen: StateFlow<Boolean> = _isLoginScreenOpen.asStateFlow()

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    private val _accountSyncMessage = MutableStateFlow<String?>(null)
    val accountSyncMessage: StateFlow<String?> = _accountSyncMessage.asStateFlow()

    val account: StateFlow<NicoAccount> = accountPrefs.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NicoAccount())

    private val _homeState = MutableStateFlow(HomeUiState(isLoading = true))
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _liveState = MutableStateFlow(LiveUiState(isLoading = true))
    val liveState: StateFlow<LiveUiState> = _liveState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.state

    private val _danmakuSettings = MutableStateFlow(DanmakuSettings())
    val danmakuSettings: StateFlow<DanmakuSettings> = _danmakuSettings.asStateFlow()

    private val _backgroundPlaybackEnabled = MutableStateFlow(true)
    val backgroundPlaybackEnabled: StateFlow<Boolean> = _backgroundPlaybackEnabled.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _currentComments = MutableStateFlow<List<DanmakuComment>>(emptyList())
    val currentComments: StateFlow<List<DanmakuComment>> = _currentComments.asStateFlow()

    private val _isLoadingVideo = MutableStateFlow(false)
    val isLoadingVideo: StateFlow<Boolean> = _isLoadingVideo.asStateFlow()

    private val _isCurrentInMylist = MutableStateFlow(false)
    val isCurrentInMylist: StateFlow<Boolean> = _isCurrentInMylist.asStateFlow()

    val downloads: StateFlow<List<DownloadedVideoEntity>> = repository.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<WatchHistoryEntity>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mylist: StateFlow<List<MylistVideoEntity>> = repository.mylistFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var commentsObserverJob: Job? = null
    private var mylistObserverJob: Job? = null
    private var activeLiveSocket: WebSocket? = null

    private val seenVideoIds = mutableSetOf<String>()
    private val dislikedVideoIds = mutableSetOf<String>()
    private val dislikedTags = mutableSetOf<String>()
    private val userInterestTags = mutableListOf<String>()
    private var homePageIndex = 0

    init {
        viewModelScope.launch {
            accountPrefs.accountFlow.collect { acc ->
                app.apiClient.cookieJar.setUserSession(acc.userSession.ifBlank { null })
            }
        }
        loadCategory(NicoCategory.ALL)
        loadLiveCategory(NicoLiveCategory.ALL)
    }

    fun openLoginScreen() {
        _isLoginScreenOpen.value = true
    }

    fun closeLoginScreen() {
        _isLoginScreenOpen.value = false
    }

    fun onLoginSuccess(userSession: String) {
        _isLoginScreenOpen.value = false
        syncAccountWithSession(userSession)
    }

    fun syncAccountNow() {
        val session = account.value.userSession
        if (session.isNotBlank()) {
            syncAccountWithSession(session)
        }
    }

    private fun syncAccountWithSession(userSession: String) {
        _isSyncingAccount.value = true
        _accountSyncMessage.value = "正在同步账号数据..."
        viewModelScope.launch {
            val result = repository.syncAccountData(userSession)
            result.onSuccess { syncedAccount ->
                accountPrefs.saveAccount(syncedAccount)
                _accountSyncMessage.value = "已同步官方观看历史与收藏列表"
            }.onFailure { err ->
                _accountSyncMessage.value = err.localizedMessage ?: "同步失败，请重新登录"
            }
            _isSyncingAccount.value = false
        }
    }

    fun logoutAccount() {
        viewModelScope.launch {
            accountPrefs.clearAccount()
            app.apiClient.cookieJar.setUserSession(null)
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            _accountSyncMessage.value = "已退出官方账号"
        }
    }

    fun loadLiveCategory(category: NicoLiveCategory) {
        _liveState.update { it.copy(selectedCategory = category, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = liveClient.fetchOnAirLives(category = category, limit = 30)
            result.onSuccess { list ->
                _liveState.update { it.copy(lives = list, isLoading = false, errorMessage = null) }
            }.onFailure { err ->
                _liveState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "直播列表加载失败，请检查网络"
                    )
                }
            }
        }
    }

    private fun closeLiveSession() {
        activeLiveSocket?.close(1000, "Switching media")
        activeLiveSocket = null
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun openPlayerScreen() {
        if (playerState.value.currentVideo != null) {
            _isPlayerScreenOpen.value = true
        }
    }

    fun minimizePlayerScreen() {
        _isPlayerScreenOpen.value = false
        if (!_backgroundPlaybackEnabled.value && playerState.value.isPlaying) {
            playerController.togglePlayPause()
        }
    }

    fun selectHomeFeedTab(tab: HomeFeedTab) {
        _homeState.update { it.copy(feedTab = tab) }
        if (tab == HomeFeedTab.LIVE) {
            if (_liveState.value.lives.isEmpty()) {
                loadLiveCategory(_liveState.value.selectedCategory)
            }
        } else {
            loadCategory(_homeState.value.selectedCategory, tab)
        }
    }

    fun setHotRecentDays(days: Int?) {
        val validDays = days?.takeIf { it > 0 }
        _homeState.update { it.copy(hotRecentDays = validDays) }
        loadCategory(_homeState.value.selectedCategory, _homeState.value.feedTab)
    }

    fun loadCategory(category: NicoCategory, feedTab: HomeFeedTab = _homeState.value.feedTab) {
        homePageIndex = 0
        _homeState.update {
            it.copy(
                feedTab = feedTab,
                selectedCategory = category,
                isLoading = true,
                isRefreshing = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.fetchRecommendedFeed(
                category = category,
                isHotTab = feedTab == HomeFeedTab.HOT,
                pageIndex = homePageIndex,
                interestTags = userInterestTags.toList(),
                excludedIds = dislikedVideoIds,
                dislikedTags = dislikedTags,
                hotRecentDays = _homeState.value.hotRecentDays
            )
            result.onSuccess { list ->
                seenVideoIds.addAll(list.map { it.contentId })
                _homeState.update {
                    it.copy(videos = list, isLoading = false, isRefreshing = false, errorMessage = null)
                }
            }.onFailure { err ->
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = err.localizedMessage ?: "加载失败，请检查网络连接"
                    )
                }
            }
        }
    }

    /**
     * Bilibili-style pull-to-refresh ("换一批"): fetches a brand-new batch of unseen recommendations.
     */
    fun refreshHomeFeed() {
        val current = _homeState.value
        if (current.feedTab == HomeFeedTab.LIVE) {
            loadLiveCategory(_liveState.value.selectedCategory)
            return
        }
        homePageIndex++
        _homeState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.fetchRecommendedFeed(
                category = current.selectedCategory,
                isHotTab = current.feedTab == HomeFeedTab.HOT,
                pageIndex = homePageIndex,
                interestTags = userInterestTags.toList(),
                excludedIds = seenVideoIds + dislikedVideoIds,
                dislikedTags = dislikedTags,
                hotRecentDays = current.hotRecentDays
            )
            result.onSuccess { freshList ->
                if (freshList.isNotEmpty()) {
                    seenVideoIds.addAll(freshList.map { it.contentId })
                    _homeState.update {
                        it.copy(videos = freshList, isRefreshing = false, errorMessage = null)
                    }
                } else {
                    // Reset seen pool if exhausted
                    seenVideoIds.clear()
                    _homeState.update { it.copy(isRefreshing = false) }
                }
            }.onFailure { err ->
                _homeState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = err.localizedMessage ?: "刷新失败"
                    )
                }
            }
        }
    }

    /**
     * Infinite scroll: loads and appends the next batch of recommended videos.
     */
    fun loadMoreHomeFeed() {
        val current = _homeState.value
        if (current.feedTab == HomeFeedTab.LIVE || current.isLoading || current.isLoadingMore) return
        homePageIndex++
        _homeState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val existingIds = current.videos.map { it.contentId }.toSet()
            val result = repository.fetchRecommendedFeed(
                category = current.selectedCategory,
                isHotTab = current.feedTab == HomeFeedTab.HOT,
                pageIndex = homePageIndex,
                interestTags = userInterestTags.toList(),
                excludedIds = existingIds + dislikedVideoIds,
                dislikedTags = dislikedTags,
                hotRecentDays = current.hotRecentDays
            )
            result.onSuccess { more ->
                seenVideoIds.addAll(more.map { it.contentId })
                _homeState.update {
                    it.copy(
                        videos = (it.videos + more).distinctBy { v -> v.contentId },
                        isLoadingMore = false
                    )
                }
            }.onFailure {
                _homeState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Bilibili-style "不感兴趣": immediately removes the video and down-weights its primary tag.
     */
    fun markNotInterested(video: NicoVideo) {
        dislikedVideoIds.add(video.contentId)
        video.tags.firstOrNull()?.let { tag ->
            dislikedTags.add(tag)
            userInterestTags.remove(tag)
        }
        _homeState.update { state ->
            state.copy(videos = state.videos.filterNot { it.contentId == video.contentId })
        }
    }

    fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(query = query) }
    }

    fun updateSearchSort(sortOrder: NicoSortOrder) {
        _searchState.update { it.copy(sortOrder = sortOrder) }
        if (_searchState.value.query.isNotBlank()) {
            performSearch(_searchState.value.query)
        }
    }

    fun performSearch(queryOverride: String? = null) {
        val q = (queryOverride ?: _searchState.value.query).trim()
        if (q.isBlank()) return
        val updatedRecent = (listOf(q) + _searchState.value.recentQueries.filterNot { it == q }).take(8)
        _searchState.update {
            it.copy(
                query = q,
                recentQueries = updatedRecent,
                isLoading = true,
                hasSearched = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.searchVideos(
                query = q,
                sortOrder = _searchState.value.sortOrder,
                limit = 30
            )
            result.onSuccess { list ->
                _searchState.update { it.copy(results = list, isLoading = false, errorMessage = null) }
            }.onFailure { err ->
                _searchState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "搜索失败"
                    )
                }
            }
        }
    }

    fun searchByTag(tag: String) {
        _isPlayerScreenOpen.value = false
        _selectedTab.value = MainTab.SEARCH
        performSearch(tag)
    }

    fun playVideo(video: NicoVideo) {
        closeLiveSession()
        // Record user interest tags from watched videos for personalized recommendations
        video.tags.take(2).forEach { tag ->
            if (tag.isNotBlank() && tag !in dislikedTags) {
                userInterestTags.remove(tag)
                userInterestTags.add(0, tag)
                if (userInterestTags.size > 10) userInterestTags.removeAt(userInterestTags.lastIndex)
            }
        }
        if (video.isLive) {
            playLive(video)
            return
        }
        _isPlayerScreenOpen.value = true
        _isLoadingVideo.value = true

        commentsObserverJob?.cancel()
        commentsObserverJob = viewModelScope.launch {
            repository.observeComments(video.contentId).collect { list ->
                _currentComments.value = list
            }
        }

        mylistObserverJob?.cancel()
        mylistObserverJob = viewModelScope.launch {
            repository.observeIsInMylist(video.contentId).collect { inMylist ->
                _isCurrentInMylist.value = inMylist
            }
        }

        viewModelScope.launch {
            repository.recordHistory(video)
            val payload = repository.loadWatchPayload(video)
            val enrichedVideo = video.copy(
                ownerName = payload.streamInfo.ownerName.ifBlank { video.ownerName },
                description = payload.streamInfo.fullDescription.ifBlank { video.description }
            )
            _isLoadingVideo.value = false
            playerController.playStream(
                video = enrichedVideo,
                streamUrl = payload.streamInfo.streamUrl,
                isOffline = payload.streamInfo.isOffline,
                statusMessage = payload.streamInfo.statusMessage
            )
        }
    }

    fun playLive(live: NicoVideo) {
        closeLiveSession()
        _isPlayerScreenOpen.value = true
        _isLoadingVideo.value = true
        commentsObserverJob?.cancel()
        _currentComments.value = emptyList()

        mylistObserverJob?.cancel()
        mylistObserverJob = viewModelScope.launch {
            repository.observeIsInMylist(live.contentId).collect { inMylist ->
                _isCurrentInMylist.value = inMylist
            }
        }

        viewModelScope.launch {
            val (wsUrl, errorMsg) = liveClient.fetchLiveWebSocketUrl(live.contentId)
            if (wsUrl.isNullOrBlank()) {
                _isLoadingVideo.value = false
                playerController.playStream(
                    video = live,
                    streamUrl = null,
                    isOffline = false,
                    statusMessage = errorMsg ?: "无法连接到该生放送"
                )
                return@launch
            }

            activeLiveSocket = liveClient.openLiveSession(
                liveId = live.contentId,
                wsUrl = wsUrl,
                onStreamReady = { hlsUri ->
                    viewModelScope.launch {
                        _isLoadingVideo.value = false
                        playerController.playStream(
                            video = live,
                            streamUrl = hlsUri,
                            isOffline = false,
                            statusMessage = null
                        )
                    }
                },
                onLiveComment = { comment ->
                    viewModelScope.launch {
                        val posMs = playerState.value.currentPositionMs
                        _currentComments.update { list ->
                            (list + comment.copy(vposMs = posMs)).takeLast(120)
                        }
                    }
                },
                onError = { err ->
                    viewModelScope.launch {
                        _isLoadingVideo.value = false
                        playerController.playStream(
                            video = live,
                            streamUrl = null,
                            isOffline = false,
                            statusMessage = err
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        closeLiveSession()
        super.onCleared()
    }

    fun onWebServerResponse(videoId: String, serverResponseJson: String) {
        viewModelScope.launch {
            repository.syncCommentsFromServerResponse(videoId, serverResponseJson)
        }
    }

    fun onWebStreamResolved(videoId: String, hlsUrl: String, rawCookies: String) {
        val currentVideo = playerState.value.currentVideo ?: return
        if (currentVideo.contentId != videoId || hlsUrl.isBlank()) return
        if (!playerState.value.useWebEmbedFallback) return
        if (rawCookies.isNotBlank()) {
            app.apiClient.cookieJar.importRawCookies(rawCookies)
        }
        _isLoadingVideo.value = false
        playerController.playStream(
            video = currentVideo,
            streamUrl = hlsUrl,
            isOffline = false,
            statusMessage = null
        )
    }

    fun postDanmaku(text: String, colorHex: Long, position: DanmakuPosition) {
        val video = playerState.value.currentVideo ?: return
        if (text.isBlank()) return
        val vpos = playerState.value.currentPositionMs
        viewModelScope.launch {
            repository.postLocalDanmaku(
                videoId = video.contentId,
                vposMs = vpos,
                text = text,
                colorHex = colorHex,
                position = position
            )
        }
    }

    fun toggleDanmakuEnabled() {
        _danmakuSettings.update { it.copy(enabled = !it.enabled) }
    }

    fun updateDanmakuSettings(transform: (DanmakuSettings) -> DanmakuSettings) {
        _danmakuSettings.update(transform)
    }

    fun toggleCurrentMylist() {
        val video = playerState.value.currentVideo ?: return
        val current = _isCurrentInMylist.value
        viewModelScope.launch {
            repository.toggleMylist(video, current)
        }
    }

    fun downloadVideo(video: NicoVideo) {
        repository.startOfflineDownload(video)
    }

    fun deleteDownload(contentId: String) {
        viewModelScope.launch {
            repository.cancelOrDeleteDownload(contentId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _backgroundPlaybackEnabled.value = enabled
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
    }
}
