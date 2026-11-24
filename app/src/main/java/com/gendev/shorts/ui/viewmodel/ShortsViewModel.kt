package com.gendev.shorts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gendev.shorts.R
import com.gendev.shorts.data.cache.MediaCacheManager
import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.data.parser.ParsedVideoInput
import com.gendev.shorts.data.remote.TikTokDataSource
import com.gendev.shorts.data.remote.TikTokSearchRestrictedException
import com.gendev.shorts.data.remote.YouTubeShortsDataSource
import com.gendev.shorts.data.repository.ShortsRepository
import com.gendev.shorts.domain.model.FeedUiState
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.ShortsUiState
import com.gendev.shorts.domain.model.VideoSource
import com.gendev.shorts.player.ShortsPlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ShortsViewModel(
    application: Application,
    private val repository: ShortsRepository,
    private val cacheManager: MediaCacheManager,
    val playerController: ShortsPlayerController,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShortsUiState())
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

    private var feedJob: Job? = null
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.endpointsFlow.collect { nodes ->
                _uiState.update { it.copy(endpoints = nodes) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                playerController.refreshProgress()
                delay(250L)
            }
        }
        loadFeed(VideoSource.YOUTUBE_SHORTS)
        refreshCacheSize()
    }

    fun selectSource(source: VideoSource) {
        if (_uiState.value.activeSource == source && _uiState.value.feedState is FeedUiState.Ready) {
            return
        }
        playerController.stopAndClear()
        _uiState.update {
            it.copy(
                activeSource = source,
                currentIndex = 0,
                feedState = FeedUiState.Loading,
            )
        }
        loadFeed(source)
    }

    fun loadFeed(source: VideoSource = _uiState.value.activeSource) {
        feedJob?.cancel()
        resolveJob?.cancel()
        feedJob = viewModelScope.launch {
            _uiState.update { it.copy(feedState = FeedUiState.Loading, currentIndex = 0) }
            val items = runCatching { repository.loadDiscoveryFeed(source) }.getOrDefault(emptyList())
            if (items.isEmpty()) {
                _uiState.update {
                    it.copy(
                        feedState = FeedUiState.Error(
                            message = getApplication<Application>().getString(R.string.error_feed_desc),
                        ),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        feedState = FeedUiState.Ready(items = items),
                        currentIndex = 0,
                    )
                }
                resolveAndBindAtIndex(0)
            }
            refreshCacheSize()
        }
    }

    fun onPageSettled(index: Int) {
        val ready = _uiState.value.feedState as? FeedUiState.Ready ?: return
        if (index !in ready.items.indices) return
        if (_uiState.value.currentIndex != index) {
            playerController.pausePlayback()
            _uiState.update { it.copy(currentIndex = index) }
        }
        resolveAndBindAtIndex(index)
    }

    private fun resolveAndBindAtIndex(index: Int) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val currentReady = _uiState.value.feedState as? FeedUiState.Ready ?: return@launch
            val item = currentReady.items.getOrNull(index) ?: return@launch

            val resolved = if (item.isStreamResolved) {
                item
            } else {
                repository.resolvePlaybackForItem(item)
            }

            updateItemInFeed(index, resolved)

            if (_uiState.value.currentIndex == index) {
                bindItemToPlayer(index, resolved)
            }

            // Pre-resolve next video stream metadata quietly for zero-stall swipe
            val nextIndex = index + 1
            val nextItem = (_uiState.value.feedState as? FeedUiState.Ready)?.items?.getOrNull(nextIndex)
            if (nextItem != null && !nextItem.isStreamResolved) {
                runCatching { repository.resolvePlaybackForItem(nextItem) }
                    .getOrNull()
                    ?.let { preResolved -> updateItemInFeed(nextIndex, preResolved) }
            }
            refreshCacheSize()
        }
    }

    private fun bindItemToPlayer(index: Int, item: ShortVideoItem) {
        when (item.playbackEngine) {
            PlaybackEngine.EXOPLAYER -> {
                if (item.videoStreamUrl != null) {
                    playerController.bindVideo(
                        item = item,
                        muted = _uiState.value.isMuted,
                    ) { failedItem, _ ->
                        onExoPlayerStreamFailed(index, failedItem)
                    }
                } else {
                    onExoPlayerStreamFailed(index, item)
                }
            }
            PlaybackEngine.WEBVIEW_EMBED, PlaybackEngine.UNAVAILABLE -> {
                playerController.stopAndClear()
            }
        }
    }

    private fun onExoPlayerStreamFailed(index: Int, failedItem: ShortVideoItem) {
        val fallbackItem = repository.promoteToWebFallback(failedItem)
        updateItemInFeed(index, fallbackItem)
        playerController.stopAndClear()
        _uiState.update {
            it.copy(transientNotice = getApplication<Application>().getString(R.string.fallback_notice_web))
        }
    }

    fun onWebViewEmbedError(index: Int) {
        val ready = _uiState.value.feedState as? FeedUiState.Ready ?: return
        val item = ready.items.getOrNull(index) ?: return
        val reason = getApplication<Application>().getString(R.string.error_video_unavailable_desc)
        updateItemInFeed(index, repository.markUnavailable(item, reason))
    }

    fun retryActiveVideo() {
        val index = _uiState.value.currentIndex
        val ready = _uiState.value.feedState as? FeedUiState.Ready
        if (ready == null) {
            loadFeed(_uiState.value.activeSource)
            return
        }
        val item = ready.items.getOrNull(index) ?: return
        val resetItem = item.copy(
            isStreamResolved = false,
            videoStreamUrl = null,
            audioStreamUrl = null,
            playbackEngine = PlaybackEngine.EXOPLAYER,
            unavailableReason = null,
        )
        updateItemInFeed(index, resetItem)
        resolveAndBindAtIndex(index)
    }

    fun switchActiveToWebFallback() {
        val index = _uiState.value.currentIndex
        val ready = _uiState.value.feedState as? FeedUiState.Ready ?: return
        val item = ready.items.getOrNull(index) ?: return
        val webItem = repository.promoteToWebFallback(item)
        updateItemInFeed(index, webItem)
        playerController.stopAndClear()
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.isMuted
        playerController.updateMuted(nextMuted)
        _uiState.update { it.copy(isMuted = nextMuted) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun openSearchSheet() {
        _uiState.update { it.copy(isSearchSheetOpen = true) }
    }

    fun closeSearchSheet() {
        _uiState.update { it.copy(isSearchSheetOpen = false) }
    }

    fun openPrivacySheet() {
        refreshCacheSize()
        _uiState.update { it.copy(isPrivacySheetOpen = true) }
    }

    fun closePrivacySheet() {
        _uiState.update { it.copy(isPrivacySheetOpen = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitSearchOrLink(rawInput: String = _uiState.value.searchQuery) {
        val parsed = repository.parseInput(rawInput)
        when (parsed) {
            is ParsedVideoInput.Invalid -> {
                _uiState.update {
                    it.copy(
                        transientNotice = getApplication<Application>().getString(R.string.error_invalid_link),
                    )
                }
            }
            is ParsedVideoInput.DirectYouTube, is ParsedVideoInput.DirectTikTok -> {
                resolveDirectLink(parsed)
            }
            is ParsedVideoInput.SearchQuery -> {
                performKeywordSearch(parsed.query)
            }
        }
    }

    fun handleSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        val parsed = repository.parseInput(text)
        if (parsed is ParsedVideoInput.DirectYouTube || parsed is ParsedVideoInput.DirectTikTok) {
            resolveDirectLink(parsed)
        }
    }

    private fun resolveDirectLink(parsed: ParsedVideoInput) {
        resolveJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingLink = true) }
            val resolved = runCatching { repository.resolveDirectInput(parsed) }.getOrNull()
            if (resolved != null) {
                val existing = (_uiState.value.feedState as? FeedUiState.Ready)?.items.orEmpty()
                val updatedList = listOf(resolved) + existing.filterNot { it.id == resolved.id && it.source == resolved.source }
                _uiState.update {
                    it.copy(
                        activeSource = resolved.source,
                        feedState = FeedUiState.Ready(updatedList),
                        currentIndex = 0,
                        isSearchSheetOpen = false,
                        isResolvingLink = false,
                    )
                }
                bindItemToPlayer(0, resolved)
            } else {
                _uiState.update {
                    it.copy(
                        isResolvingLink = false,
                        transientNotice = getApplication<Application>().getString(R.string.error_video_unavailable_desc),
                    )
                }
            }
        }
    }

    private fun performKeywordSearch(query: String) {
        feedJob?.cancel()
        resolveJob?.cancel()
        val source = _uiState.value.activeSource
        feedJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingLink = true,
                    feedState = FeedUiState.Loading,
                    isSearchSheetOpen = false,
                )
            }
            try {
                val results = repository.search(source, query)
                if (results.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isResolvingLink = false,
                            feedState = FeedUiState.Error(
                                message = getApplication<Application>().getString(R.string.error_feed_desc),
                            ),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isResolvingLink = false,
                            feedState = FeedUiState.Ready(results),
                            currentIndex = 0,
                        )
                    }
                    resolveAndBindAtIndex(0)
                }
            } catch (_: TikTokSearchRestrictedException) {
                _uiState.update {
                    it.copy(
                        isResolvingLink = false,
                        feedState = FeedUiState.Error(
                            message = getApplication<Application>().getString(R.string.error_tiktok_search_restricted),
                            isTikTokSearchRestricted = true,
                        ),
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingLink = false,
                        feedState = FeedUiState.Error(
                            message = getApplication<Application>().getString(R.string.error_feed_desc),
                        ),
                    )
                }
            }
        }
    }

    fun recheckEndpoints() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingEndpoints = true) }
            repository.probeEndpoints()
            _uiState.update { it.copy(isCheckingEndpoints = false) }
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            playerController.stopAndClear()
            val remainingBytes = cacheManager.clearAllTemporaryCache()
            _uiState.update {
                it.copy(
                    cacheSizeBytes = remainingBytes,
                    transientNotice = getApplication<Application>().getString(R.string.cache_cleared_toast),
                )
            }
            // Rebind active item cleanly after cache release
            val ready = _uiState.value.feedState as? FeedUiState.Ready
            val currentItem = ready?.items?.getOrNull(_uiState.value.currentIndex)
            if (currentItem != null) {
                bindItemToPlayer(_uiState.value.currentIndex, currentItem)
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = cacheManager.getTemporaryCacheSizeBytes()
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun clearTransientNotice() {
        _uiState.update { it.copy(transientNotice = null) }
    }

    fun onAppBackgrounded() {
        playerController.pausePlayback()
    }

    private fun updateItemInFeed(index: Int, updated: ShortVideoItem) {
        _uiState.update { state ->
            val ready = state.feedState as? FeedUiState.Ready ?: return@update state
            if (index !in ready.items.indices) return@update state
            val mutable = ready.items.toMutableList()
            mutable[index] = updated
            state.copy(feedState = ready.copy(items = mutable))
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
        cacheManager.release()
    }
}

class ShortsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val healthManager = EndpointHealthManager(okHttpClient)
        val youTubeDataSource = YouTubeShortsDataSource(okHttpClient, healthManager)
        val tikTokDataSource = TikTokDataSource(okHttpClient, healthManager)
        val repository = ShortsRepository(healthManager, youTubeDataSource, tikTokDataSource)
        val cacheManager = MediaCacheManager(application)
        val playerController = ShortsPlayerController(application, okHttpClient, cacheManager)

        return ShortsViewModel(
            application = application,
            repository = repository,
            cacheManager = cacheManager,
            playerController = playerController,
        ) as T
    }
}
