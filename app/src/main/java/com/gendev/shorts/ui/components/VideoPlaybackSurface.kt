package com.gendev.shorts.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.gendev.shorts.R
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.player.ShortsPlayerController
import com.gendev.shorts.ui.theme.AmoledBlack
import com.gendev.shorts.ui.theme.CoralError
import com.gendev.shorts.ui.theme.ElectricViolet
import com.gendev.shorts.ui.theme.ObsidianCard
import com.gendev.shorts.ui.theme.PureWhite
import com.gendev.shorts.ui.theme.SlateMuted

@OptIn(UnstableApi::class)
@Composable
fun VideoPlaybackSurface(
    item: ShortVideoItem,
    isActive: Boolean,
    isMuted: Boolean,
    isFullscreen: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    playerController: ShortsPlayerController,
    onRetry: () -> Unit,
    onSwitchToWebFallback: () -> Unit,
    onWebViewError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPausedIcon by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !isActive -> {
                // Keep inactive pages ultra-lightweight for 2 GB RAM devices
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            item.playbackEngine == PlaybackEngine.UNAVAILABLE -> {
                UnavailableVideoOverlay(
                    reason = item.unavailableReason
                        ?: stringResource(R.string.error_video_unavailable_desc),
                    onRetry = onRetry,
                    onTryWeb = if (item.embedUrl != null) onSwitchToWebFallback else null,
                )
            }

            item.playbackEngine == PlaybackEngine.WEBVIEW_EMBED -> {
                EmbeddedShortsWebView(
                    item = item,
                    isActive = isActive,
                    isMuted = isMuted,
                    onEmbedError = onWebViewError,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            item.playbackEngine == PlaybackEngine.EXOPLAYER && item.videoStreamUrl != null -> {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val nowPlaying = playerController.togglePlayPause()
                            showPausedIcon = !nowPlaying
                        },
                    factory = { context ->
                        PlayerView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setKeepContentOnPlayerReset(false)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            resizeMode = if (isFullscreen) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            player = playerController.player
                        }
                    },
                    update = { playerView ->
                        playerView.player = playerController.player
                        playerView.resizeMode = if (isFullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                )

                if (isBuffering) {
                    if (!item.thumbnailUrl.isNullOrBlank() && !isPlaying) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            alpha = 0.35f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    OrbitalLoadingIndicator(label = stringResource(R.string.loading_stream))
                }

                AnimatedVisibility(
                    visible = showPausedIcon && !isPlaying && !isBuffering,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AmoledBlack.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.tap_to_play),
                            tint = PureWhite,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            else -> {
                // Stream resolution in progress
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OrbitalLoadingIndicator(label = stringResource(R.string.loading_stream))
            }
        }
    }
}

@Composable
private fun UnavailableVideoOverlay(
    reason: String,
    onRetry: () -> Unit,
    onTryWeb: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .padding(28.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ObsidianCard)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = CoralError,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.error_video_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = SlateMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier.testTag("unavailable_retry_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_retry))
            }
            if (onTryWeb != null) {
                OutlinedButton(
                    onClick = onTryWeb,
                    modifier = Modifier.testTag("unavailable_web_fallback_button"),
                ) {
                    Text(stringResource(R.string.action_switch_fallback), color = PureWhite)
                }
            }
        }
    }
}
