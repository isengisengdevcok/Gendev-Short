package com.gendev.shorts.data.parser

import com.gendev.shorts.domain.model.VideoSource

sealed interface ParsedVideoInput {
    data class DirectYouTube(val videoId: String, val canonicalUrl: String) : ParsedVideoInput
    data class DirectTikTok(val videoId: String?, val canonicalUrl: String, val needsRedirectExpansion: Boolean) : ParsedVideoInput
    data class SearchQuery(val query: String) : ParsedVideoInput
    data object Invalid : ParsedVideoInput
}

object VideoUrlParser {

    private val youtubeShortsRegex = Regex(
        """(?:https?://)?(?:www\.|m\.)?youtube\.com/shorts/([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE,
    )
    private val youtubeShortLinkRegex = Regex(
        """(?:https?://)?youtu\.be/([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE,
    )
    private val youtubeWatchRegex = Regex(
        """(?:https?://)?(?:www\.|m\.)?youtube\.com/watch\?[^#\s]*v=([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE,
    )
    private val youtubeEmbedRegex = Regex(
        """(?:https?://)?(?:www\.)?youtube(?:-nocookie)?\.com/embed/([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE,
    )

    private val tiktokFullRegex = Regex(
        """https?://(?:www\.|m\.)?tiktok\.com/@([^/?#\s]+)/video/(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val tiktokMobileRegex = Regex(
        """https?://(?:m\.)?tiktok\.com/v/(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val tiktokShortRegex = Regex(
        """https?://(?:vt|vm)\.tiktok\.com/([A-Za-z0-9_-]+)/?|https?://(?:www\.)?tiktok\.com/t/([A-Za-z0-9_-]+)/?""",
        RegexOption.IGNORE_CASE,
    )

    fun parseUserInput(rawInput: String): ParsedVideoInput {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ParsedVideoInput.Invalid

        extractYouTubeId(trimmed)?.let { id ->
            return ParsedVideoInput.DirectYouTube(
                videoId = id,
                canonicalUrl = "https://www.youtube.com/shorts/$id",
            )
        }

        tiktokFullRegex.find(trimmed)?.let { match ->
            val user = match.groupValues[1]
            val id = match.groupValues[2]
            return ParsedVideoInput.DirectTikTok(
                videoId = id,
                canonicalUrl = "https://www.tiktok.com/@$user/video/$id",
                needsRedirectExpansion = false,
            )
        }

        tiktokMobileRegex.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            return ParsedVideoInput.DirectTikTok(
                videoId = id,
                canonicalUrl = "https://www.tiktok.com/@tiktok/video/$id",
                needsRedirectExpansion = false,
            )
        }

        tiktokShortRegex.find(trimmed)?.let { match ->
            return ParsedVideoInput.DirectTikTok(
                videoId = null,
                canonicalUrl = match.value,
                needsRedirectExpansion = true,
            )
        }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return ParsedVideoInput.Invalid
        }

        return ParsedVideoInput.SearchQuery(trimmed)
    }

    fun extractYouTubeId(url: String): String? {
        youtubeShortsRegex.find(url)?.let { return it.groupValues[1] }
        youtubeShortLinkRegex.find(url)?.let { return it.groupValues[1] }
        youtubeWatchRegex.find(url)?.let { return it.groupValues[1] }
        youtubeEmbedRegex.find(url)?.let { return it.groupValues[1] }
        return null
    }

    fun extractTikTokId(url: String): String? {
        tiktokFullRegex.find(url)?.let { return it.groupValues[2] }
        tiktokMobileRegex.find(url)?.let { return it.groupValues[1] }
        return null
    }

    fun detectSourceFromUrl(url: String): VideoSource? {
        if (extractYouTubeId(url) != null) return VideoSource.YOUTUBE_SHORTS
        if (tiktokFullRegex.containsMatchIn(url) ||
            tiktokMobileRegex.containsMatchIn(url) ||
            tiktokShortRegex.containsMatchIn(url)
        ) {
            return VideoSource.TIKTOK
        }
        return null
    }

    fun buildYouTubeEmbedHtml(videoId: String, muted: Boolean): String {
        val muteParam = if (muted) 1 else 0
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
              <style>
                html, body {
                  margin: 0;
                  padding: 0;
                  width: 100%;
                  height: 100%;
                  background-color: #000000;
                  overflow: hidden;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                #player-wrap {
                  position: relative;
                  width: 100%;
                  height: 100%;
                  background: #000000;
                }
                iframe, #player {
                  position: absolute;
                  top: 0;
                  left: 0;
                  width: 100%;
                  height: 100%;
                  border: 0;
                }
              </style>
            </head>
            <body>
              <div id="player-wrap"><div id="player"></div></div>
              <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var ytPlayer = null;
                function onYouTubeIframeAPIReady() {
                  ytPlayer = new YT.Player('player', {
                    videoId: '$videoId',
                    playerVars: {
                      autoplay: 1,
                      playsinline: 1,
                      controls: 0,
                      rel: 0,
                      modestbranding: 1,
                      loop: 1,
                      playlist: '$videoId',
                      mute: $muteParam,
                      fs: 0,
                      iv_load_policy: 3
                    },
                    events: {
                      onReady: function(event) {
                        if ($muteParam === 1) { event.target.mute(); } else { event.target.unMute(); }
                        event.target.playVideo();
                        if (window.GendevBridge) { window.GendevBridge.onPlayerReady(); }
                      },
                      onError: function(event) {
                        if (window.GendevBridge) { window.GendevBridge.onPlayerError(event.data); }
                      }
                    }
                  });
                }

                function gendevSetMuted(muted) {
                  if (!ytPlayer) return;
                  if (muted) { ytPlayer.mute(); } else { ytPlayer.unMute(); }
                }

                function gendevSetPlaying(playing) {
                  if (!ytPlayer) return;
                  if (playing) { ytPlayer.playVideo(); } else { ytPlayer.pauseVideo(); }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
