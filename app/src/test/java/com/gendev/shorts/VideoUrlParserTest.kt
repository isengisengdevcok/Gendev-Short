package com.gendev.shorts

import com.gendev.shorts.data.parser.ParsedVideoInput
import com.gendev.shorts.data.parser.VideoUrlParser
import com.gendev.shorts.domain.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUrlParserTest {

    @Test
    fun `parses youtube shorts url into direct youtube input`() {
        val parsed = VideoUrlParser.parseUserInput("https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share")
        assertTrue(parsed is ParsedVideoInput.DirectYouTube)
        val yt = parsed as ParsedVideoInput.DirectYouTube
        assertEquals("dQw4w9WgXcQ", yt.videoId)
        assertEquals("https://www.youtube.com/shorts/dQw4w9WgXcQ", yt.canonicalUrl)
    }

    @Test
    fun `parses youtu_be short link into direct youtube input`() {
        val parsed = VideoUrlParser.parseUserInput("https://youtu.be/aqz-KE-bpKQ")
        assertTrue(parsed is ParsedVideoInput.DirectYouTube)
        assertEquals("aqz-KE-bpKQ", (parsed as ParsedVideoInput.DirectYouTube).videoId)
    }

    @Test
    fun `parses full tiktok video url without redirect flag`() {
        val parsed = VideoUrlParser.parseUserInput("https://www.tiktok.com/@nasa/video/7324455939445607726")
        assertTrue(parsed is ParsedVideoInput.DirectTikTok)
        val tt = parsed as ParsedVideoInput.DirectTikTok
        assertEquals("7324455939445607726", tt.videoId)
        assertFalse(tt.needsRedirectExpansion)
    }

    @Test
    fun `parses vt_tiktok short link with redirect expansion required`() {
        val parsed = VideoUrlParser.parseUserInput("https://vt.tiktok.com/ZSF123abc/")
        assertTrue(parsed is ParsedVideoInput.DirectTikTok)
        val tt = parsed as ParsedVideoInput.DirectTikTok
        assertEquals(null, tt.videoId)
        assertTrue(tt.needsRedirectExpansion)
    }

    @Test
    fun `treats plain text as keyword search query`() {
        val parsed = VideoUrlParser.parseUserInput("kotlin compose tips")
        assertTrue(parsed is ParsedVideoInput.SearchQuery)
        assertEquals("kotlin compose tips", (parsed as ParsedVideoInput.SearchQuery).query)
    }

    @Test
    fun `rejects unsupported http links as invalid`() {
        val parsed = VideoUrlParser.parseUserInput("https://example.com/not-a-short")
        assertEquals(ParsedVideoInput.Invalid, parsed)
    }

    @Test
    fun `detects source platform from url`() {
        assertEquals(VideoSource.YOUTUBE_SHORTS, VideoUrlParser.detectSourceFromUrl("https://youtube.com/shorts/abcdefghijk"))
        assertEquals(VideoSource.TIKTOK, VideoUrlParser.detectSourceFromUrl("https://vt.tiktok.com/ZSFabc/"))
        assertEquals(null, VideoUrlParser.detectSourceFromUrl("random text"))
    }
}
