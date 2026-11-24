package com.gendev.shorts.data.remote

import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.data.parser.VideoUrlParser
import com.gendev.shorts.domain.model.EndpointNode
import com.gendev.shorts.domain.model.EndpointProviderType
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class YouTubeShortsDataSource(
    private val client: OkHttpClient,
    private val healthManager: EndpointHealthManager,
) {
    suspend fun fetchDiscoveryFeed(): List<ShortVideoItem> = withContext(Dispatchers.IO) {
        val region = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val nodes = healthManager.getOrderedNodesFor(VideoSource.YOUTUBE_SHORTS)
            .filter { it.providerType == EndpointProviderType.PIPED || it.providerType == EndpointProviderType.INVIDIOUS }

        for (node in nodes) {
            val start = System.currentTimeMillis()
            val results = runCatching {
                when (node.providerType) {
                    EndpointProviderType.PIPED -> fetchPipedDiscovery(node, region)
                    EndpointProviderType.INVIDIOUS -> fetchInvidiousDiscovery(node, region)
                    else -> emptyList()
                }
            }.getOrNull()

            if (!results.isNullOrEmpty()) {
                healthManager.recordSuccess(node.id, System.currentTimeMillis() - start)
                return@withContext results
            } else {
                healthManager.recordFailure(node.id)
            }
        }
        emptyList()
    }

    suspend fun searchShorts(query: String): List<ShortVideoItem> = withContext(Dispatchers.IO) {
        val sanitized = query.trim()
        if (sanitized.isEmpty()) return@withContext emptyList()
        val shortsQuery = if (sanitized.contains("short", ignoreCase = true)) sanitized else "$sanitized #shorts"
        val nodes = healthManager.getOrderedNodesFor(VideoSource.YOUTUBE_SHORTS)
            .filter { it.providerType == EndpointProviderType.PIPED || it.providerType == EndpointProviderType.INVIDIOUS }

        for (node in nodes) {
            val start = System.currentTimeMillis()
            val results = runCatching {
                when (node.providerType) {
                    EndpointProviderType.PIPED -> searchPiped(node, shortsQuery)
                    EndpointProviderType.INVIDIOUS -> searchInvidious(node, shortsQuery)
                    else -> emptyList()
                }
            }.getOrNull()

            if (!results.isNullOrEmpty()) {
                healthManager.recordSuccess(node.id, System.currentTimeMillis() - start)
                return@withContext results
            } else {
                healthManager.recordFailure(node.id)
            }
        }
        emptyList()
    }

    suspend fun resolveVideoStreams(item: ShortVideoItem): ShortVideoItem = withContext(Dispatchers.IO) {
        if (item.isStreamResolved && (item.videoStreamUrl != null || item.playbackEngine == PlaybackEngine.WEBVIEW_EMBED)) {
            return@withContext item
        }
        val nodes = healthManager.getOrderedNodesFor(VideoSource.YOUTUBE_SHORTS)
        for (node in nodes) {
            val start = System.currentTimeMillis()
            val resolved = runCatching {
                when (node.providerType) {
                    EndpointProviderType.PIPED -> resolvePipedStreams(node, item)
                    EndpointProviderType.INVIDIOUS -> resolveInvidiousStreams(node, item)
                    EndpointProviderType.YOUTUBE_OEMBED -> resolveYouTubeOEmbedFallback(node, item)
                    else -> null
                }
            }.getOrNull()

            if (resolved != null) {
                healthManager.recordSuccess(node.id, System.currentTimeMillis() - start)
                return@withContext resolved
            } else {
                healthManager.recordFailure(node.id)
            }
        }

        // Guaranteed IFrame embed fallback for valid public YouTube IDs
        item.copy(
            playbackEngine = PlaybackEngine.WEBVIEW_EMBED,
            embedUrl = "https://www.youtube.com/embed/${item.id}",
            providerUsed = "YouTube IFrame",
            isStreamResolved = true,
        )
    }

    suspend fun resolveDirectUrl(videoId: String): ShortVideoItem = withContext(Dispatchers.IO) {
        val seed = ShortVideoItem(
            id = videoId,
            source = VideoSource.YOUTUBE_SHORTS,
            title = "YouTube Shorts ($videoId)",
            authorHandle = "@youtube",
            canonicalWebUrl = "https://www.youtube.com/shorts/$videoId",
            embedUrl = "https://www.youtube.com/embed/$videoId",
        )
        resolveVideoStreams(seed)
    }

    private fun fetchPipedDiscovery(node: EndpointNode, region: String): List<ShortVideoItem> {
        val searchItems = searchPiped(node, "#shorts")
        if (searchItems.isNotEmpty()) return searchItems

        val body = executeGet("${node.baseUrl}/trending?region=$region") ?: return emptyList()
        val array = JSONArray(body)
        return parsePipedItems(array, node.name).ifEmpty {
            parsePipedItems(array, node.name, allowLongerFallback = true)
        }
    }

    private fun fetchInvidiousDiscovery(node: EndpointNode, region: String): List<ShortVideoItem> {
        val searchItems = searchInvidious(node, "#shorts")
        if (searchItems.isNotEmpty()) return searchItems

        val body = executeGet("${node.baseUrl}/api/v1/trending?region=$region") ?: return emptyList()
        val array = JSONArray(body)
        return parseInvidiousItems(array, node.name).ifEmpty {
            parseInvidiousItems(array, node.name, allowLongerFallback = true)
        }
    }

    private fun searchPiped(node: EndpointNode, query: String): List<ShortVideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = executeGet("${node.baseUrl}/search?q=$encoded&filter=videos") ?: return emptyList()
        val root = JSONObject(body)
        val items = root.optJSONArray("items") ?: return emptyList()
        return parsePipedItems(items, node.name)
    }

    private fun searchInvidious(node: EndpointNode, query: String): List<ShortVideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = executeGet("${node.baseUrl}/api/v1/search?q=$encoded&type=video") ?: return emptyList()
        val array = JSONArray(body)
        return parseInvidiousItems(array, node.name)
    }

    private fun parsePipedItems(
        array: JSONArray,
        providerName: String,
        allowLongerFallback: Boolean = false,
    ): List<ShortVideoItem> {
        val list = mutableListOf<ShortVideoItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val urlPath = obj.optString("url")
            val id = VideoUrlParser.extractYouTubeId("https://www.youtube.com$urlPath")
                ?: urlPath.substringAfter("v=").substringBefore("&").takeIf { it.length == 11 }
                ?: continue
            val duration = obj.optInt("duration", 0)
            val isShort = obj.optBoolean("isShort", false) ||
                urlPath.contains("/shorts/", ignoreCase = true) ||
                (duration in 1..95)
            if (!isShort && !allowLongerFallback) continue

            val title = obj.optString("title").ifBlank { "YouTube Shorts" }
            val uploader = obj.optString("uploaderName").ifBlank { "Creator" }
            val handle = if (uploader.startsWith("@")) uploader else "@${uploader.replace(" ", "")}"
            val thumb = obj.optString("thumbnail").takeIf { it.startsWith("http") }

            list.add(
                ShortVideoItem(
                    id = id,
                    source = VideoSource.YOUTUBE_SHORTS,
                    title = title,
                    authorHandle = handle,
                    durationSeconds = duration,
                    thumbnailUrl = thumb ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
                    canonicalWebUrl = "https://www.youtube.com/shorts/$id",
                    embedUrl = "https://www.youtube.com/embed/$id",
                    playbackEngine = PlaybackEngine.EXOPLAYER,
                    providerUsed = providerName,
                ),
            )
        }
        return list.distinctBy { it.id }
    }

    private fun parseInvidiousItems(
        array: JSONArray,
        providerName: String,
        allowLongerFallback: Boolean = false,
    ): List<ShortVideoItem> {
        val list = mutableListOf<ShortVideoItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("videoId").takeIf { it.length == 11 } ?: continue
            val duration = obj.optInt("lengthSeconds", 0)
            val title = obj.optString("title").ifBlank { "YouTube Shorts" }
            val isShort = (duration in 1..95) || title.contains("#short", ignoreCase = true)
            if (!isShort && !allowLongerFallback) continue

            val author = obj.optString("author").ifBlank { "Creator" }
            val handle = if (author.startsWith("@")) author else "@${author.replace(" ", "")}"
            val thumbs = obj.optJSONArray("videoThumbnails")
            val thumbUrl = thumbs?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.startsWith("http") }
                ?: "https://i.ytimg.com/vi/$id/hq720.jpg"

            list.add(
                ShortVideoItem(
                    id = id,
                    source = VideoSource.YOUTUBE_SHORTS,
                    title = title,
                    authorHandle = handle,
                    durationSeconds = duration,
                    thumbnailUrl = thumbUrl,
                    canonicalWebUrl = "https://www.youtube.com/shorts/$id",
                    embedUrl = "https://www.youtube.com/embed/$id",
                    playbackEngine = PlaybackEngine.EXOPLAYER,
                    providerUsed = providerName,
                ),
            )
        }
        return list.distinctBy { it.id }
    }

    private fun resolvePipedStreams(node: EndpointNode, item: ShortVideoItem): ShortVideoItem? {
        val body = executeGet("${node.baseUrl}/streams/${item.id}") ?: return null
        val root = JSONObject(body)
        if (root.has("error")) return null

        val title = root.optString("title").ifBlank { item.title }
        val uploader = root.optString("uploader").ifBlank { item.authorHandle }
        val duration = root.optInt("duration", item.durationSeconds)
        val thumb = root.optString("thumbnailUrl").takeIf { it.startsWith("http") } ?: item.thumbnailUrl
        val hls = root.optString("hls").takeIf { it.startsWith("http") }

        val videoStreams = root.optJSONArray("videoStreams")
        val audioStreams = root.optJSONArray("audioStreams")

        // 1. Prefer muxed MP4 <= 1080p (videoOnly == false) for lowest memory & fastest start
        val muxedUrl = pickBestPipedStream(videoStreams, videoOnlyWanted = false)
        if (muxedUrl != null) {
            return item.copy(
                title = title,
                authorHandle = formatHandle(uploader),
                durationSeconds = duration,
                thumbnailUrl = thumb,
                videoStreamUrl = muxedUrl,
                audioStreamUrl = null,
                isHls = false,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = node.name,
                isStreamResolved = true,
            )
        }

        // 2. Adaptive video + audio via MergingMediaSource
        val adaptiveVideo = pickBestPipedStream(videoStreams, videoOnlyWanted = true)
        val adaptiveAudio = pickBestPipedAudio(audioStreams)
        if (adaptiveVideo != null && adaptiveAudio != null) {
            return item.copy(
                title = title,
                authorHandle = formatHandle(uploader),
                durationSeconds = duration,
                thumbnailUrl = thumb,
                videoStreamUrl = adaptiveVideo,
                audioStreamUrl = adaptiveAudio,
                isHls = false,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = node.name,
                isStreamResolved = true,
            )
        }

        // 3. HLS manifest fallback
        if (hls != null) {
            return item.copy(
                title = title,
                authorHandle = formatHandle(uploader),
                durationSeconds = duration,
                thumbnailUrl = thumb,
                videoStreamUrl = hls,
                audioStreamUrl = null,
                isHls = true,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = node.name,
                isStreamResolved = true,
            )
        }

        return null
    }

    private fun resolveInvidiousStreams(node: EndpointNode, item: ShortVideoItem): ShortVideoItem? {
        val body = executeGet("${node.baseUrl}/api/v1/videos/${item.id}") ?: return null
        val root = JSONObject(body)
        if (root.has("error")) return null

        val title = root.optString("title").ifBlank { item.title }
        val author = root.optString("author").ifBlank { item.authorHandle }
        val duration = root.optInt("lengthSeconds", item.durationSeconds)
        val hls = root.optString("hlsUrl").takeIf { it.startsWith("http") }

        val formatStreams = root.optJSONArray("formatStreams")
        var bestMuxedUrl: String? = null
        var bestScore = -1
        if (formatStreams != null) {
            for (i in 0 until formatStreams.length()) {
                val stream = formatStreams.optJSONObject(i) ?: continue
                val url = stream.optString("url").takeIf { it.startsWith("http") } ?: continue
                val type = stream.optString("type")
                val qualityLabel = stream.optString("qualityLabel")
                val height = qualityLabel.substringBefore("p").toIntOrNull() ?: 360
                val score = (if (type.contains("mp4", ignoreCase = true)) 1000 else 0) +
                    if (height <= 1080) height else (1080 - (height - 1080))
                if (score > bestScore) {
                    bestScore = score
                    bestMuxedUrl = url
                }
            }
        }

        if (bestMuxedUrl != null) {
            return item.copy(
                title = title,
                authorHandle = formatHandle(author),
                durationSeconds = duration,
                videoStreamUrl = bestMuxedUrl,
                audioStreamUrl = null,
                isHls = false,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = node.name,
                isStreamResolved = true,
            )
        }

        if (hls != null) {
            return item.copy(
                title = title,
                authorHandle = formatHandle(author),
                durationSeconds = duration,
                videoStreamUrl = hls,
                audioStreamUrl = null,
                isHls = true,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = node.name,
                isStreamResolved = true,
            )
        }

        return null
    }

    private fun resolveYouTubeOEmbedFallback(node: EndpointNode, item: ShortVideoItem): ShortVideoItem? {
        val target = URLEncoder.encode(item.canonicalWebUrl, "UTF-8")
        val body = executeGet("${node.baseUrl}/oembed?url=$target&format=json") ?: return null
        val root = JSONObject(body)
        val title = root.optString("title").ifBlank { item.title }
        val author = root.optString("author_name").ifBlank { item.authorHandle }
        val thumb = root.optString("thumbnail_url").takeIf { it.startsWith("http") } ?: item.thumbnailUrl
        return item.copy(
            title = title,
            authorHandle = formatHandle(author),
            thumbnailUrl = thumb,
            embedUrl = "https://www.youtube.com/embed/${item.id}",
            playbackEngine = PlaybackEngine.WEBVIEW_EMBED,
            providerUsed = node.name,
            isStreamResolved = true,
        )
    }

    private fun pickBestPipedStream(array: JSONArray?, videoOnlyWanted: Boolean): String? {
        if (array == null) return null
        var bestUrl: String? = null
        var bestScore = -1
        for (i in 0 until array.length()) {
            val stream = array.optJSONObject(i) ?: continue
            val videoOnly = stream.optBoolean("videoOnly", false)
            if (videoOnly != videoOnlyWanted) continue
            val url = stream.optString("url").takeIf { it.startsWith("http") } ?: continue
            val format = stream.optString("format")
            val height = stream.optInt("height", 480)
            // Cap preferred resolution at 1080p for 2GB RAM efficiency
            val resScore = if (height in 360..1080) height else 200
            val codecBonus = if (format.contains("MPEG_4", ignoreCase = true) || format.contains("MP4", ignoreCase = true)) 1000 else 0
            val score = codecBonus + resScore
            if (score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun pickBestPipedAudio(array: JSONArray?): String? {
        if (array == null) return null
        var bestUrl: String? = null
        var bestBitrate = -1
        for (i in 0 until array.length()) {
            val stream = array.optJSONObject(i) ?: continue
            val url = stream.optString("url").takeIf { it.startsWith("http") } ?: continue
            val format = stream.optString("format")
            val bitrate = stream.optInt("bitrate", 0)
            val bonus = if (format.contains("M4A", ignoreCase = true) || format.contains("MP4", ignoreCase = true)) 100_000 else 0
            val score = bonus + bitrate
            if (score > bestBitrate) {
                bestBitrate = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun formatHandle(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", EndpointHealthManager.MOBILE_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }
}
