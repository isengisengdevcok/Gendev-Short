package com.gendev.shorts.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gendev.shorts.R
import com.gendev.shorts.domain.model.FeedUiState
import com.gendev.shorts.domain.model.PlaybackEngine
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.VideoSource
import com.gendev.shorts.ui.components.GendevWordmark
import com.gendev.shorts.ui.components.OrbitalLoadingIndicator
import com.gendev.shorts.ui.components.PrivacyAndHealthSheet
import com.gendev.shorts.ui.components.SearchAndLinkSheet
import com.gendev.shorts.ui.components.VideoPlaybackSurface
import com.gendev.shorts.ui.theme.AmoledBlack
import com.gendev.shorts.ui.theme.CoralError
import com.gendev.shorts.ui.theme.ElectricViolet
import com.gendev.shorts.ui.theme.ElectricVioletBright
import com.gendev.shorts.ui.theme.GlassBorder
import com.gendev.shorts.ui.theme.GlassOverlay
import com.gendev.shorts.ui.theme.ObsidianCard
import com.gendev.shorts.ui.theme.ObsidianSurface
import com.gendev.shorts.ui.theme.PureWhite
import com.gendev.shorts.ui.theme.SlateMuted
import com.gendev.shorts.ui.viewmodel.ShortsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ShortsHomeScreen(
    viewModel: ShortsViewModel,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBuffering by viewModel.playerController.isBuffering.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playerController.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Automatically stop video playback when app enters background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAppBackgrounded()
    }

    LaunchedEffect(uiState.isFullscreen) {
        onFullscreenChanged(uiState.isFullscreen)
    }

    LaunchedEffect(uiState.transientNotice) {
        val message = uiState.transientNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearTransientNotice()
    }

    BackHandler(
        enabled = uiState.isFullscreen || uiState.isSearchSheetOpen || uiState.isPrivacySheetOpen,
    ) {
        when {
            uiState.isSearchSheetOpen -> viewModel.closeSearchSheet()
            uiState.isPrivacySheetOpen -> viewModel.closePrivacySheet()
            uiState.isFullscreen -> viewModel.setFullscreen(false)
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { _ ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack),
            contentAlignment = Alignment.Center,
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val stageModifier = if (isWideScreen) {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(0.dp))
            } else {
                Modifier.fillMaxSize()
            }

            Box(
                modifier = stageModifier.background(AmoledBlack),
            ) {
                when (val feed = uiState.feedState) {
                    is FeedUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            OrbitalLoadingIndicator(
                                label = stringResource(R.string.loading_feed),
                            )
                        }
                    }

                    is FeedUiState.Error -> {
                        FeedErrorState(
                            message = feed.message,
                            activeSource = uiState.activeSource,
                            onRetry = { viewModel.loadFeed(uiState.activeSource) },
                            onOpenSearch = { viewModel.openSearchSheet() },
                            onSwitchSource = {
                                val next = if (uiState.activeSource == VideoSource.YOUTUBE_SHORTS) {
                                    VideoSource.TIKTOK
                                } else {
                                    VideoSource.YOUTUBE_SHORTS
                                }
                                viewModel.selectSource(next)
                            },
                        )
                    }

                    is FeedUiState.Ready -> {
                        val pagerState = rememberPagerState(
                            initialPage = uiState.currentIndex.coerceIn(0, (feed.items.size - 1).coerceAtLeast(0)),
                            pageCount = { feed.items.size },
                        )

                        LaunchedEffect(uiState.currentIndex, feed.items.size) {
                            if (feed.items.isNotEmpty() &&
                                uiState.currentIndex in feed.items.indices &&
                                pagerState.currentPage != uiState.currentIndex
                            ) {
                                pagerState.scrollToPage(uiState.currentIndex)
                            }
                        }

                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { settled ->
                                    viewModel.onPageSettled(settled)
                                }
                        }

                        VerticalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("shorts_vertical_pager"),
                        ) { pageIndex ->
                            val item = feed.items[pageIndex]
                            val isActive = pageIndex == uiState.currentIndex

                            Box(modifier = Modifier.fillMaxSize()) {
                                VideoPlaybackSurface(
                                    item = item,
                                    isActive = isActive,
                                    isMuted = uiState.isMuted,
                                    isFullscreen = uiState.isFullscreen,
                                    isBuffering = isBuffering,
                                    isPlaying = isPlaying,
                                    playerController = viewModel.playerController,
                                    onRetry = { viewModel.retryActiveVideo() },
                                    onSwitchToWebFallback = { viewModel.switchActiveToWebFallback() },
                                    onWebViewError = { viewModel.onWebViewEmbedError(pageIndex) },
                                )

                                // Bottom gradient scrim for readable minimal metadata
                                AnimatedVisibility(
                                    visible = !uiState.isFullscreen,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        AmoledBlack.copy(alpha = 0.85f),
                                                        AmoledBlack,
                                                    ),
                                                ),
                                            ),
                                    )
                                }

                                // Right-edge functional controls rail (no social buttons)
                                RightActionRail(
                                    item = item,
                                    isMuted = uiState.isMuted,
                                    isFullscreen = uiState.isFullscreen,
                                    onToggleMute = { viewModel.toggleMute() },
                                    onToggleFullscreen = { viewModel.toggleFullscreen() },
                                    onRetryOrFallback = {
                                        if (item.playbackEngine == PlaybackEngine.EXOPLAYER) {
                                            viewModel.switchActiveToWebFallback()
                                        } else {
                                            viewModel.retryActiveVideo()
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .navigationBarsPadding()
                                        .padding(end = 14.dp, bottom = 24.dp),
                                )

                                // Bottom-left video info + thin progress bar
                                AnimatedVisibility(
                                    visible = !uiState.isFullscreen,
                                    enter = fadeIn() + slideInVertically { it / 4 },
                                    exit = fadeOut() + slideOutVertically { it / 4 },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .navigationBarsPadding()
                                        .padding(start = 16.dp, end = 80.dp, bottom = 22.dp),
                                ) {
                                    VideoMetadataBlock(
                                        item = item,
                                        progress = if (isActive && item.playbackEngine == PlaybackEngine.EXOPLAYER) progress else 0f,
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Minimalist Floating HUD
                AnimatedVisibility(
                    visible = !uiState.isFullscreen,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    TopFloatingHeader(
                        activeSource = uiState.activeSource,
                        onSelectSource = { viewModel.selectSource(it) },
                        onOpenSearch = { viewModel.openSearchSheet() },
                        onOpenPrivacy = { viewModel.openPrivacySheet() },
                    )
                }
            }
        }
    }

    if (uiState.isSearchSheetOpen) {
        SearchAndLinkSheet(
            query = uiState.searchQuery,
            isResolving = uiState.isResolvingLink,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onSubmit = { viewModel.submitSearchOrLink(it) },
            onDismiss = { viewModel.closeSearchSheet() },
        )
    }

    if (uiState.isPrivacySheetOpen) {
        PrivacyAndHealthSheet(
            cacheSizeBytes = uiState.cacheSizeBytes,
            endpoints = uiState.endpoints,
            isCheckingEndpoints = uiState.isCheckingEndpoints,
            onClearCache = { viewModel.clearTemporaryCache() },
            onRecheckEndpoints = { viewModel.recheckEndpoints() },
            onDismiss = { viewModel.closePrivacySheet() },
        )
    }
}

@Composable
private fun TopFloatingHeader(
    activeSource: VideoSource,
    onSelectSource: (VideoSource) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AmoledBlack.copy(alpha = 0.85f),
                        AmoledBlack.copy(alpha = 0.4f),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GendevWordmark()

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GlassOverlay)
                        .testTag("search_open_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.cd_search_or_link),
                        tint = PureWhite,
                    )
                }
                IconButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GlassOverlay)
                        .testTag("privacy_open_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = stringResource(R.string.cd_privacy_and_health),
                        tint = ElectricVioletBright,
                    )
                }
            }
        }

        // Source Switcher Pill (YouTube Shorts | TikTok)
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassOverlay)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SourceTabPill(
                label = stringResource(R.string.source_youtube_shorts),
                selected = activeSource == VideoSource.YOUTUBE_SHORTS,
                onClick = { onSelectSource(VideoSource.YOUTUBE_SHORTS) },
                testTag = "source_youtube_pill",
            )
            SourceTabPill(
                label = stringResource(R.string.source_tiktok),
                selected = activeSource == VideoSource.TIKTOK,
                onClick = { onSelectSource(VideoSource.TIKTOK) },
                testTag = "source_tiktok_pill",
            )
        }
    }
}

@Composable
private fun SourceTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ElectricViolet else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PureWhite else SlateMuted,
        )
    }
}

@Composable
private fun RightActionRail(
    item: ShortVideoItem,
    isMuted: Boolean,
    isFullscreen: Boolean,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetryOrFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isFullscreen) {
            // Engine Status Badge (EXO / WEB / OFF)
            val engineLabel = when (item.playbackEngine) {
                PlaybackEngine.EXOPLAYER -> stringResource(R.string.engine_exo)
                PlaybackEngine.WEBVIEW_EMBED -> stringResource(R.string.engine_web)
                PlaybackEngine.UNAVAILABLE -> stringResource(R.string.engine_unavailable)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassOverlay)
                    .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = engineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricVioletBright,
                )
            }

            RailCircleButton(
                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(if (isMuted) R.string.cd_unmute else R.string.cd_mute),
                onClick = onToggleMute,
                testTag = "mute_toggle_button",
            )

            RailCircleButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_retry_playback),
                onClick = onRetryOrFallback,
                testTag = "retry_fallback_button",
            )
        }

        RailCircleButton(
            icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            contentDescription = stringResource(if (isFullscreen) R.string.cd_exit_fullscreen else R.string.cd_enter_fullscreen),
            onClick = onToggleFullscreen,
            tint = if (isFullscreen) ElectricVioletBright else PureWhite,
            testTag = "fullscreen_toggle_button",
        )
    }
}

@Composable
private fun RailCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    tint: Color = PureWhite,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(GlassOverlay)
            .border(1.dp, GlassBorder, CircleShape)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun VideoMetadataBlock(
    item: ShortVideoItem,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.authorHandle,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.providerUsed.isNotBlank()) {
                Text(
                    text = stringResource(R.string.provider_label, item.providerUsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricVioletBright,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianSurface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                color = ElectricVioletBright,
                trackColor = PureWhite.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun FeedErrorState(
    message: String,
    activeSource: VideoSource,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchSource: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = CoralError,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = stringResource(R.string.error_feed_title),
                style = MaterialTheme.typography.titleLarge,
                color = PureWhite,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("feed_error_retry_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_retry))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("feed_error_search_button"),
                ) {
                    Text(
                        text = stringResource(R.string.action_open_search),
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onSwitchSource,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("feed_error_switch_source_button"),
                ) {
                    val altLabel = if (activeSource == VideoSource.YOUTUBE_SHORTS) {
                        stringResource(R.string.source_tiktok)
                    } else {
                        stringResource(R.string.source_youtube_shorts)
                    }
                    Text(
                        text = altLabel,
                        color = ElectricVioletBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
