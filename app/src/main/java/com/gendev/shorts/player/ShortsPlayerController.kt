package com.gendev.shorts.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.gendev.shorts.data.cache.MediaCacheManager
import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.domain.model.ShortVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class ShortsPlayerController(
    context: Context,
    okHttpClient: OkHttpClient,
    private val cacheManager: MediaCacheManager,
) {
    private val appContext = context.applicationContext

    private val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(EndpointHealthManager.MOBILE_USER_AGENT)

    private val trackSelector = DefaultTrackSelector(appContext).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSize(1080, 1920)
                .setPreferredAudioLanguage("id")
                .setForceLowestBitrate(false),
        )
    }

    // Tuned for fast startup, minimal buffering, and low RAM footprint on 2GB+ devices
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2_500,
            /* maxBufferMs = */ 12_000,
            /* bufferForPlaybackMs = */ 600,
            /* bufferForPlaybackAfterRebufferMs = */ 1_200,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            playWhenReady = true
        }

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var currentBoundKey: String? = null
    private var onPlaybackErrorCallback: ((ShortVideoItem, String) -> Unit)? = null
    private var currentItem: ShortVideoItem? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) {
                        _progress.value = 1f
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlayerError(error: PlaybackException) {
                    _isBuffering.value = false
                    val item = currentItem ?: return
                    onPlaybackErrorCallback?.invoke(item, error.errorCodeName)
                }
            },
        )
    }

    fun bindVideo(
        item: ShortVideoItem,
        muted: Boolean,
        onError: (ShortVideoItem, String) -> Unit,
    ) {
        val streamUrl = item.videoStreamUrl ?: return
        val bindKey = "${item.source.name}:${item.id}:$streamUrl:${item.audioStreamUrl.orEmpty()}"
        onPlaybackErrorCallback = onError
        currentItem = item
        player.volume = if (muted) 0f else 1f

        if (currentBoundKey == bindKey) {
            if (!player.isPlaying) {
                player.playWhenReady = true
            }
            return
        }

        currentBoundKey = bindKey
        _isBuffering.value = true
        _progress.value = 0f

        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(cacheManager.getOrCreateSimpleCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSource = if (item.isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))
        } else if (!item.audioStreamUrl.isNullOrBlank()) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(item.audioStreamUrl))
            MergingMediaSource(true, videoSource, audioSource)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))
        }

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    fun updateMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    fun togglePlayPause(): Boolean {
        return if (player.isPlaying) {
            player.pause()
            false
        } else {
            player.play()
            true
        }
    }

    fun pausePlayback() {
        player.playWhenReady = false
        player.pause()
    }

    fun stopAndClear() {
        currentBoundKey = null
        currentItem = null
        player.stop()
        player.clearMediaItems()
        _isBuffering.value = false
        _isPlaying.value = false
        _progress.value = 0f
    }

    fun refreshProgress() {
        val duration = player.duration
        val position = player.currentPosition
        _progress.value = if (duration > 0L) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun release() {
        currentBoundKey = null
        currentItem = null
        player.release()
    }
}
