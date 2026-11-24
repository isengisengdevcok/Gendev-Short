package com.gendev.shorts.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gendev.shorts.data.network.EndpointHealthManager
import com.gendev.shorts.data.parser.VideoUrlParser
import com.gendev.shorts.domain.model.ShortVideoItem
import com.gendev.shorts.domain.model.VideoSource
import com.gendev.shorts.ui.theme.AmoledBlack

class EmbedPlayerBridge(
    private val onReady: () -> Unit,
    private val onError: () -> Unit,
) {
    @JavascriptInterface
    fun onPlayerReady() {
        onReady()
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        onError()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedShortsWebView(
    item: ShortVideoItem,
    isActive: Boolean,
    isMuted: Boolean,
    onEmbedError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember(item.id, item.source) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        webViewRef?.onPause()
        webViewRef?.evaluateJavascript("if (window.gendevSetPlaying) gendevSetPlaying(false);", null)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isActive) {
            webViewRef?.onResume()
            webViewRef?.evaluateJavascript("if (window.gendevSetPlaying) gendevSetPlaying(true);", null)
        }
    }

    LaunchedEffect(isActive, isMuted, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isActive) {
            wv.onResume()
            wv.evaluateJavascript("if (window.gendevSetPlaying) gendevSetPlaying(true);", null)
            wv.evaluateJavascript("if (window.gendevSetMuted) gendevSetMuted($isMuted);", null)
        } else {
            wv.evaluateJavascript("if (window.gendevSetPlaying) gendevSetPlaying(false);", null)
            wv.onPause()
        }
    }

    DisposableEffect(item.id, item.source) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("GendevBridge")
                destroy()
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CookieManager.getInstance().apply {
                    setAcceptCookie(false)
                    removeAllCookies(null)
                }
                WebView(ctx).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = false
                        saveFormData = false
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString = EndpointHealthManager.MOBILE_USER_AGENT
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                onEmbedError()
                            }
                        }
                    }

                    addJavascriptInterface(
                        EmbedPlayerBridge(
                            onReady = { post { isLoading = false } },
                            onError = { post { onEmbedError() } },
                        ),
                        "GendevBridge",
                    )

                    when (item.source) {
                        VideoSource.YOUTUBE_SHORTS -> {
                            val html = VideoUrlParser.buildYouTubeEmbedHtml(item.id, isMuted)
                            loadDataWithBaseURL(
                                "https://www.youtube.com",
                                html,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                        VideoSource.TIKTOK -> {
                            val targetUrl = item.embedUrl ?: "https://www.tiktok.com/embed/v2/${item.id}"
                            loadUrl(targetUrl)
                        }
                    }

                    webViewRef = this
                }
            },
        )

        if (isLoading) {
            OrbitalLoadingIndicator()
        }
    }
}
