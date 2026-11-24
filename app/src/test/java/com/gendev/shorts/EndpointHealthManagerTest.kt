package com.gendev.shorts

import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.domain.model.EndpointStatus
import com.gendev.shorts.domain.model.VideoSource
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointHealthManagerTest {

    @Test
    fun `orders healthy lowest-latency youtube endpoints first and demotes failed ones`() {
        var now = 1_000L
        val manager = EndpointHealthManager(
            baseClient = OkHttpClient(),
            clockMs = { now },
        )

        manager.recordSuccess("inv_nadeko", 120L)
        manager.recordSuccess("piped_kavin", 450L)
        manager.recordFailure("piped_adminforge")
        manager.recordFailure("piped_adminforge") // triggers 60s cooldown + UNREACHABLE

        val ordered = manager.getOrderedNodesFor(VideoSource.YOUTUBE_SHORTS)
        assertEquals("inv_nadeko", ordered.first().id)
        assertEquals(EndpointStatus.HEALTHY, ordered.first().status)
        assertTrue(ordered.none { it.id == "piped_adminforge" })

        // Advance clock past 60s cooldown
        now += 65_000L
        val afterCooldown = manager.getOrderedNodesFor(VideoSource.YOUTUBE_SHORTS)
        assertTrue(afterCooldown.any { it.id == "piped_adminforge" })
    }

    @Test
    fun `returns tiktok nodes for tiktok source`() {
        val manager = EndpointHealthManager(baseClient = OkHttpClient())
        val nodes = manager.getOrderedNodesFor(VideoSource.TIKTOK)
        assertEquals(2, nodes.size)
        assertTrue(nodes.any { it.id == "tiktok_oembed" })
        assertTrue(nodes.any { it.id == "tiktok_web" })
    }
}
