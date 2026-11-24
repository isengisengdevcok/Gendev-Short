package com.gendev.shorts.data.network

import com.gendev.shorts.domain.model.EndpointNode
import com.gendev.shorts.domain.model.EndpointProviderType
import com.gendev.shorts.domain.model.EndpointStatus
import com.gendev.shorts.domain.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class EndpointHealthManager(
    baseClient: OkHttpClient,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val probeClient = baseClient.newBuilder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .callTimeout(5000, TimeUnit.MILLISECONDS)
        .build()

    private val initialNodes = listOf(
        EndpointNode(
            id = "piped_kavin",
            name = "Piped Kavin",
            baseUrl = "https://pipedapi.kavin.rocks",
            providerType = EndpointProviderType.PIPED,
        ),
        EndpointNode(
            id = "piped_adminforge",
            name = "Piped AdminForge",
            baseUrl = "https://pipedapi.adminforge.de",
            providerType = EndpointProviderType.PIPED,
        ),
        EndpointNode(
            id = "piped_private_coffee",
            name = "Piped Coffee",
            baseUrl = "https://api.piped.private.coffee",
            providerType = EndpointProviderType.PIPED,
        ),
        EndpointNode(
            id = "inv_nadeko",
            name = "Invidious Nadeko",
            baseUrl = "https://inv.nadeko.net",
            providerType = EndpointProviderType.INVIDIOUS,
        ),
        EndpointNode(
            id = "inv_yewtu",
            name = "Invidious Yewtu",
            baseUrl = "https://yewtu.be",
            providerType = EndpointProviderType.INVIDIOUS,
        ),
        EndpointNode(
            id = "inv_nerdvpn",
            name = "Invidious NerdVPN",
            baseUrl = "https://invidious.nerdvpn.de",
            providerType = EndpointProviderType.INVIDIOUS,
        ),
        EndpointNode(
            id = "yt_oembed",
            name = "YouTube oEmbed",
            baseUrl = "https://www.youtube.com",
            providerType = EndpointProviderType.YOUTUBE_OEMBED,
        ),
        EndpointNode(
            id = "tiktok_oembed",
            name = "TikTok oEmbed",
            baseUrl = "https://www.tiktok.com",
            providerType = EndpointProviderType.TIKTOK_OEMBED,
        ),
        EndpointNode(
            id = "tiktok_web",
            name = "TikTok Public Web",
            baseUrl = "https://www.tiktok.com",
            providerType = EndpointProviderType.TIKTOK_WEB,
        ),
    )

    private val _endpoints = MutableStateFlow(initialNodes)
    val endpoints: StateFlow<List<EndpointNode>> = _endpoints.asStateFlow()

    suspend fun probeAll(): List<EndpointNode> = coroutineScope {
        val current = _endpoints.value
        val updated = current.map { node ->
            async(Dispatchers.IO) { probeNode(node) }
        }.awaitAll()
        _endpoints.value = updated
        updated
    }

    private fun probeNode(node: EndpointNode): EndpointNode {
        val probeUrl = when (node.providerType) {
            EndpointProviderType.PIPED -> "${node.baseUrl}/healthcheck"
            EndpointProviderType.INVIDIOUS -> "${node.baseUrl}/api/v1/stats"
            EndpointProviderType.YOUTUBE_OEMBED ->
                "${node.baseUrl}/oembed?url=https://www.youtube.com/watch?v=jNQXAC9IVRw&format=json"
            EndpointProviderType.TIKTOK_OEMBED ->
                "${node.baseUrl}/oembed?url=https://www.tiktok.com/@tiktok/video/7106594312292453675"
            EndpointProviderType.TIKTOK_WEB -> "${node.baseUrl}/robots.txt"
        }
        val start = clockMs()
        return try {
            val request = Request.Builder()
                .url(probeUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .get()
                .build()
            probeClient.newCall(request).execute().use { response ->
                val latency = (clockMs() - start).coerceAtLeast(1L)
                if (response.isSuccessful || response.code == 404) {
                    val status = if (latency > 2200L) EndpointStatus.DEGRADED else EndpointStatus.HEALTHY
                    node.copy(
                        status = status,
                        lastLatencyMs = latency,
                        consecutiveFailures = 0,
                        cooldownUntilMs = 0L,
                    )
                } else {
                    node.copy(
                        status = EndpointStatus.DEGRADED,
                        lastLatencyMs = latency,
                        consecutiveFailures = node.consecutiveFailures + 1,
                    )
                }
            }
        } catch (_: Exception) {
            node.copy(
                status = EndpointStatus.UNREACHABLE,
                lastLatencyMs = null,
                consecutiveFailures = node.consecutiveFailures + 1,
                cooldownUntilMs = clockMs() + COOLDOWN_MS,
            )
        }
    }

    fun getOrderedNodesFor(source: VideoSource): List<EndpointNode> {
        val now = clockMs()
        val matching = _endpoints.value.filter { node ->
            when (source) {
                VideoSource.YOUTUBE_SHORTS -> node.providerType in setOf(
                    EndpointProviderType.PIPED,
                    EndpointProviderType.INVIDIOUS,
                    EndpointProviderType.YOUTUBE_OEMBED,
                )
                VideoSource.TIKTOK -> node.providerType in setOf(
                    EndpointProviderType.TIKTOK_OEMBED,
                    EndpointProviderType.TIKTOK_WEB,
                )
            }
        }
        val active = matching.filter { it.cooldownUntilMs <= now }
        val pool = active.ifEmpty { matching }
        return pool.sortedWith(
            compareBy<EndpointNode>(
                {
                    when (it.status) {
                        EndpointStatus.HEALTHY -> 0
                        EndpointStatus.UNKNOWN -> 1
                        EndpointStatus.DEGRADED -> 2
                        EndpointStatus.UNREACHABLE -> 3
                    }
                },
                { it.consecutiveFailures },
                { it.lastLatencyMs ?: Long.MAX_VALUE },
            ),
        )
    }

    fun recordSuccess(nodeId: String, latencyMs: Long) {
        _endpoints.value = _endpoints.value.map { node ->
            if (node.id == nodeId) {
                node.copy(
                    status = if (latencyMs > 2500L) EndpointStatus.DEGRADED else EndpointStatus.HEALTHY,
                    lastLatencyMs = latencyMs,
                    consecutiveFailures = 0,
                    cooldownUntilMs = 0L,
                )
            } else {
                node
            }
        }
    }

    fun recordFailure(nodeId: String) {
        val now = clockMs()
        _endpoints.value = _endpoints.value.map { node ->
            if (node.id == nodeId) {
                val failures = node.consecutiveFailures + 1
                node.copy(
                    status = if (failures >= 2) EndpointStatus.UNREACHABLE else EndpointStatus.DEGRADED,
                    consecutiveFailures = failures,
                    cooldownUntilMs = if (failures >= 2) now + COOLDOWN_MS else 0L,
                )
            } else {
                node
            }
        }
    }

    suspend fun ensureProbedIfNeeded() = withContext(Dispatchers.IO) {
        val allUnknown = _endpoints.value.all { it.status == EndpointStatus.UNKNOWN }
        if (allUnknown) {
            probeAll()
        }
    }

    companion object {
        private const val COOLDOWN_MS = 60_000L
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
