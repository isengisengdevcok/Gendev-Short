package com.gendev.shorts.data.repository

import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.data.parser.ParsedVideoInput
import com.gendev.shorts.data.parser.VideoUrlParser
import com.gendev.shorts.data.remote.TikTokDataSource
import com.gendev.shorts.data.remote.YouTubeShortsDataSource
import com.gendev.shorts.domain.model.EndpointNode
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.VideoSource
import kotlinx.coroutines.flow.StateFlow

class ShortsRepository(
    private val healthManager: EndpointHealthManager,
    private val youTubeDataSource: YouTubeShortsDataSource,
    private val tikTokDataSource: TikTokDataSource,
) {
    val endpointsFlow: StateFlow<List<EndpointNode>> = healthManager.endpoints

    suspend fun probeEndpoints(): List<EndpointNode> = healthManager.probeAll()

    suspend fun loadDiscoveryFeed(source: VideoSource): List<ShortVideoItem> {
        healthManager.ensureProbedIfNeeded()
        return when (source) {
            VideoSource.YOUTUBE_SHORTS -> youTubeDataSource.fetchDiscoveryFeed()
            VideoSource.TIKTOK -> tikTokDataSource.fetchDiscoveryFeed()
        }
    }

    suspend fun search(source: VideoSource, query: String): List<ShortVideoItem> {
        healthManager.ensureProbedIfNeeded()
        return when (source) {
            VideoSource.YOUTUBE_SHORTS -> youTubeDataSource.searchShorts(query)
            VideoSource.TIKTOK -> tikTokDataSource.searchPublicVideos(query)
        }
    }

    suspend fun resolveDirectInput(input: ParsedVideoInput): ShortVideoItem? {
        return when (input) {
            is ParsedVideoInput.DirectYouTube -> youTubeDataSource.resolveDirectUrl(input.videoId)
            is ParsedVideoInput.DirectTikTok ->
                tikTokDataSource.resolvePublicTikTokUrl(input.canonicalUrl, input.needsRedirectExpansion)
            is ParsedVideoInput.SearchQuery, ParsedVideoInput.Invalid -> null
        }
    }

    suspend fun resolvePlaybackForItem(item: ShortVideoItem): ShortVideoItem {
        return when (item.source) {
            VideoSource.YOUTUBE_SHORTS -> youTubeDataSource.resolveVideoStreams(item)
            VideoSource.TIKTOK -> {
                if (item.isStreamResolved) item
                else tikTokDataSource.resolvePublicTikTokUrl(item.canonicalWebUrl, needsRedirect = false)
            }
        }
    }

    fun promoteToWebFallback(item: ShortVideoItem): ShortVideoItem {
        val embed = when (item.source) {
            VideoSource.YOUTUBE_SHORTS -> "https://www.youtube.com/embed/${item.id}"
            VideoSource.TIKTOK -> item.embedUrl ?: "https://www.tiktok.com/embed/v2/${item.id}"
        }
        return item.copy(
            playbackEngine = PlaybackEngine.WEBVIEW_EMBED,
            embedUrl = embed,
            isStreamResolved = true,
        )
    }

    fun markUnavailable(item: ShortVideoItem, reason: String): ShortVideoItem {
        return item.copy(
            playbackEngine = PlaybackEngine.UNAVAILABLE,
            unavailableReason = reason,
            isStreamResolved = true,
        )
    }

    fun parseInput(raw: String): ParsedVideoInput = VideoUrlParser.parseUserInput(raw)
}
