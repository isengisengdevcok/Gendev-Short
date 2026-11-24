package com.gendev.shorts.domain.model

enum class VideoSource {
    YOUTUBE_SHORTS,
    TIKTOK,
}

enum class PlaybackEngine {
    EXOPLAYER,
    WEBVIEW_EMBED,
    UNAVAILABLE,
}

enum class EndpointProviderType {
    PIPED,
    INVIDIOUS,
    YOUTUBE_OEMBED,
    TIKTOK_OEMBED,
    TIKTOK_WEB,
}

enum class EndpointStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNREACHABLE,
}

data class EndpointNode(
    val id: String,
    val name: String,
    val baseUrl: String,
    val providerType: EndpointProviderType,
    val status: EndpointStatus = EndpointStatus.UNKNOWN,
    val lastLatencyMs: Long? = null,
    val consecutiveFailures: Int = 0,
    val cooldownUntilMs: Long = 0L,
)

data class ShortVideoItem(
    val id: String,
    val source: VideoSource,
    val title: String,
    val authorHandle: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val canonicalWebUrl: String,
    val videoStreamUrl: String? = null,
    val audioStreamUrl: String? = null,
    val isHls: Boolean = false,
    val embedUrl: String? = null,
    val playbackEngine: PlaybackEngine = PlaybackEngine.EXOPLAYER,
    val providerUsed: String = "",
    val isStreamResolved: Boolean = false,
    val unavailableReason: String? = null,
)

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Ready(
        val items: List<ShortVideoItem>,
        val isLoadingMore: Boolean = false,
    ) : FeedUiState
    data class Error(
        val message: String,
        val isTikTokSearchRestricted: Boolean = false,
    ) : FeedUiState
}

data class ShortsUiState(
    val activeSource: VideoSource = VideoSource.YOUTUBE_SHORTS,
    val feedState: FeedUiState = FeedUiState.Loading,
    val currentIndex: Int = 0,
    val isMuted: Boolean = false,
    val isFullscreen: Boolean = false,
    val isSearchSheetOpen: Boolean = false,
    val isPrivacySheetOpen: Boolean = false,
    val isResolvingLink: Boolean = false,
    val searchQuery: String = "",
    val transientNotice: String? = null,
    val cacheSizeBytes: Long = 0L,
    val endpoints: List<EndpointNode> = emptyList(),
    val isCheckingEndpoints: Boolean = false,
)
