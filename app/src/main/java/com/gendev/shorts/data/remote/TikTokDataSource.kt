package com.gendev.shorts.data.remote

import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.data.parser.VideoUrlParser
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class TikTokSearchRestrictedException : Exception("TikTok public search requires anti-bot verification")

class TikTokDataSource(
    private val client: OkHttpClient,
    private val healthManager: EndpointHealthManager,
) {
    /**
     * Publicly accessible TikTok showcase/creator URLs hydrated live via TikTok's official
     * public oEmbed API (zero login, zero cookies).
     */
    private val publicDiscoveryUrls = listOf(
        "https://www.tiktok.com/@tiktok/video/7106594312292453675",
        "https://www.tiktok.com/@nasa/video/7324455939445607726",
        "https://www.tiktok.com/@natgeo/video/7293961913067654442",
        "https://www.tiktok.com/@ted/video/7314783553656933675",
        "https://www.tiktok.com/@google/video/7338142178306542891",
    )

    suspend fun fetchDiscoveryFeed(): List<ShortVideoItem> = coroutineScope {
        val hydrated = publicDiscoveryUrls.map { url ->
            async(Dispatchers.IO) {
                runCatching { resolvePublicTikTokUrl(url, needsRedirect = false) }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        if (hydrated.isNotEmpty()) {
            return@coroutineScope hydrated
        }
        emptyList()
    }

    suspend fun searchPublicVideos(query: String): List<ShortVideoItem> = withContext(Dispatchers.IO) {
        val tag = query.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        if (tag.isEmpty()) return@withContext emptyList()

        val tagUrl = "https://www.tiktok.com/tag/$tag"
        val start = System.currentTimeMillis()
        val html = runCatching { executeWebGet(tagUrl) }.getOrNull()

        if (html == null || containsAntiBotChallenge(html)) {
            healthManager.recordFailure("tiktok_web")
            throw TikTokSearchRestrictedException()
        }

        val discoveredUrls = extractVideoUrlsFromHtml(html)
        if (discoveredUrls.isEmpty()) {
            throw TikTokSearchRestrictedException()
        }

        healthManager.recordSuccess("tiktok_web", System.currentTimeMillis() - start)
        discoveredUrls.take(8).mapNotNull { url ->
            runCatching { resolvePublicTikTokUrl(url, needsRedirect = false) }.getOrNull()
        }
    }

    suspend fun resolvePublicTikTokUrl(
        rawUrl: String,
        needsRedirect: Boolean,
    ): ShortVideoItem = withContext(Dispatchers.IO) {
        val canonicalUrl = if (needsRedirect) expandShortUrl(rawUrl) else rawUrl
        val extractedId = VideoUrlParser.extractTikTokId(canonicalUrl)

        val oEmbedMeta = fetchTikTokOEmbed(canonicalUrl)
        val videoId = extractedId ?: oEmbedMeta?.embedProductId ?: canonicalUrl.hashCode().toString()
        val embedUrl = "https://www.tiktok.com/embed/v2/$videoId"

        val publicWebData = runCatching { inspectPublicPageWithoutAuth(canonicalUrl) }.getOrNull()

        val title = publicWebData?.title?.takeIf { it.isNotBlank() }
            ?: oEmbedMeta?.title?.takeIf { it.isNotBlank() }
            ?: "TikTok Short"
        val author = publicWebData?.authorHandle?.takeIf { it.isNotBlank() }
            ?: oEmbedMeta?.authorUniqueId?.takeIf { it.isNotBlank() }
            ?: "@tiktok"
        val thumb = publicWebData?.thumbnailUrl ?: oEmbedMeta?.thumbnailUrl

        val directMp4 = publicWebData?.directMp4Url
        if (directMp4 != null) {
            ShortVideoItem(
                id = videoId,
                source = VideoSource.TIKTOK,
                title = title,
                authorHandle = if (author.startsWith("@")) author else "@$author",
                thumbnailUrl = thumb,
                canonicalWebUrl = canonicalUrl,
                videoStreamUrl = directMp4,
                embedUrl = embedUrl,
                playbackEngine = PlaybackEngine.EXOPLAYER,
                providerUsed = "TikTok Public Stream",
                isStreamResolved = true,
            )
        } else if (extractedId != null || oEmbedMeta != null) {
            ShortVideoItem(
                id = videoId,
                source = VideoSource.TIKTOK,
                title = title,
                authorHandle = if (author.startsWith("@")) author else "@$author",
                thumbnailUrl = thumb,
                canonicalWebUrl = canonicalUrl,
                embedUrl = embedUrl,
                playbackEngine = PlaybackEngine.WEBVIEW_EMBED,
                providerUsed = "TikTok Embed v2",
                isStreamResolved = true,
            )
        } else {
            ShortVideoItem(
                id = videoId,
                source = VideoSource.TIKTOK,
                title = title,
                authorHandle = "@tiktok",
                canonicalWebUrl = canonicalUrl,
                embedUrl = null,
                playbackEngine = PlaybackEngine.UNAVAILABLE,
                providerUsed = "TikTok",
                isStreamResolved = true,
                unavailableReason = "TikTok restricted public access or video ID could not be verified.",
            )
        }
    }

    private fun expandShortUrl(shortUrl: String): String {
        val request = Request.Builder()
            .url(shortUrl)
            .header("User-Agent", EndpointHealthManager.MOBILE_USER_AGENT)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    private data class TikTokOEmbed(
        val title: String,
        val authorUniqueId: String,
        val thumbnailUrl: String?,
        val embedProductId: String?,
    )

    private fun fetchTikTokOEmbed(canonicalUrl: String): TikTokOEmbed? {
        val start = System.currentTimeMillis()
        val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
        val request = Request.Builder()
            .url("https://www.tiktok.com/oembed?url=$encoded")
            .header("User-Agent", EndpointHealthManager.MOBILE_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    healthManager.recordFailure("tiktok_oembed")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                healthManager.recordSuccess("tiktok_oembed", System.currentTimeMillis() - start)
                TikTokOEmbed(
                    title = json.optString("title"),
                    authorUniqueId = json.optString("author_unique_id").ifBlank {
                        json.optString("author_name")
                    },
                    thumbnailUrl = json.optString("thumbnail_url").takeIf { it.startsWith("http") },
                    embedProductId = json.optString("embed_product_id").takeIf { it.isNotBlank() },
                )
            }
        }.onFailure {
            healthManager.recordFailure("tiktok_oembed")
        }.getOrNull()
    }

    private data class PublicTikTokPageData(
        val title: String?,
        val authorHandle: String?,
        val thumbnailUrl: String?,
        val directMp4Url: String?,
    )

    private fun inspectPublicPageWithoutAuth(canonicalUrl: String): PublicTikTokPageData? {
        val start = System.currentTimeMillis()
        val html = executeWebGet(canonicalUrl) ?: run {
            healthManager.recordFailure("tiktok_web")
            return null
        }
        if (containsAntiBotChallenge(html)) {
            healthManager.recordFailure("tiktok_web")
            return null
        }

        val marker = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">"""
        val startIdx = html.indexOf(marker)
        if (startIdx == -1) return null
        val jsonStart = startIdx + marker.length
        val endIdx = html.indexOf("</script>", jsonStart)
        if (endIdx == -1) return null

        val jsonText = html.substring(jsonStart, endIdx)
        val root = JSONObject(jsonText)
        val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__") ?: return null
        val videoDetail = defaultScope.optJSONObject("webapp.video-detail") ?: return null
        val itemInfo = videoDetail.optJSONObject("itemInfo") ?: return null
        val itemStruct = itemInfo.optJSONObject("itemStruct") ?: return null

        val desc = itemStruct.optString("desc")
        val author = itemStruct.optJSONObject("author")?.optString("uniqueId")
        val videoObj = itemStruct.optJSONObject("video")
        val cover = videoObj?.optString("cover")?.takeIf { it.startsWith("http") }
        val playAddr = videoObj?.optString("playAddr")?.takeIf { it.startsWith("http") }

        healthManager.recordSuccess("tiktok_web", System.currentTimeMillis() - start)
        return PublicTikTokPageData(
            title = desc,
            authorHandle = author,
            thumbnailUrl = cover,
            directMp4Url = playAddr,
        )
    }

    private fun extractVideoUrlsFromHtml(html: String): List<String> {
        val regex = Regex("""https://www\.tiktok\.com/@[A-Za-z0-9_.-]+/video/\d+""")
        return regex.findAll(html).map { it.value }.distinct().toList()
    }

    private fun containsAntiBotChallenge(html: String): Boolean {
        return html.contains("captcha", ignoreCase = true) &&
            html.contains("verify", ignoreCase = true)
    }

    private fun executeWebGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", EndpointHealthManager.MOBILE_USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }
}
