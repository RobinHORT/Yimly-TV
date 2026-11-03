package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "YimlyWebView"

/**
 * Handles media shutdown and website session termination via the website's existing mechanisms.
 */
object YimlyMediaShutdown {
    /**
     * Pauses all audio and video elements on the page without modifying state or sessions.
     */
    const val PAUSE_MEDIA_JAVASCRIPT = """
        (function() {
            try {
                document.querySelectorAll('video, audio').forEach(function(el) {
                    try { el.pause(); } catch(e) {}
                });
            } catch(e) {}
        })();
    """

    /**
     * Ends the active karaoke room session using the website's existing UI and API methods,
     * then halts all audio and video playback elements immediately.
     */
    const val END_SESSION_JAVASCRIPT = """
        (async function() {
            try {
                // 1. If confirm button is already in DOM, click it to trigger website's native leave flow
                var confirmBtn = document.querySelector('[data-tv-id="end-session-confirm"]');
                if (confirmBtn) {
                    confirmBtn.click();
                } else {
                    var endBtn = document.querySelector('[data-tv-id="session-info-end-session"]');
                    if (endBtn) {
                        endBtn.click();
                        setTimeout(function() {
                            var c = document.querySelector('[data-tv-id="end-session-confirm"]');
                            if (c) c.click();
                        }, 50);
                    }
                }

                // 2. If on a room page, end session via the website's exact endpoint with same-origin credentials
                var match = window.location.pathname.match(/\/rooms\/([^/?#]+)/);
                if (match && match[1]) {
                    var sid = match[1];
                    try {
                        await fetch('/api/karaoke/sessions/' + sid + '/end', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            credentials: 'same-origin'
                        });
                    } catch (err) {
                        console.error('[Yimly-TV] Session end request error:', err);
                    }
                }
            } catch (e) {
                console.error('[Yimly-TV] Exit session error:', e);
            } finally {
                // 3. Stop all media playback elements
                try {
                    document.querySelectorAll('video, audio').forEach(function(el) {
                        try {
                            el.pause();
                            el.currentTime = 0;
                        } catch (e) {}
                    });
                } catch(e) {}
            }
        })();
    """

    fun executeSessionLeaveAndMediaShutdown(webView: WebView?, onCompleted: () -> Unit = {}) {
        if (webView == null) {
            onCompleted()
            return
        }
        try {
            webView.evaluateJavascript(END_SESSION_JAVASCRIPT) {
                onCompleted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error executing session leave JavaScript: ${e.message}")
            onCompleted()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YimlyHostRoomWebView(
    url: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    onExitRequested: () -> Unit = {},
    onPageStarted: () -> Unit = {},
    onPageFinished: () -> Unit = {},
    onError: (description: String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val webView = remember(context) {
        createConfiguredWebView(
            context = context,
            onExitRequested = onExitRequested,
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onError = onError
        )
    }

    DisposableEffect(webView) {
        onWebViewCreated(webView)
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try {
                        webView.evaluateJavascript(YimlyMediaShutdown.PAUSE_MEDIA_JAVASCRIPT, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error pausing media on ON_PAUSE: ${e.message}")
                    }
                    webView.onPause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    // Resume web engine without restarting playback
                    webView.onResume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying WebView: ${e.message}")
            }
        }
    }

    DisposableEffect(url) {
        Log.i(TAG, "Loading Host Room URL in WebView: $url")
        webView.loadUrl(url)
        onDispose { }
    }

    // AndroidView setup
    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = {
            // Ensure focus is given to WebView for TV remote control input
            it.requestFocus()
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    onExitRequested: () -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onError: (description: String) -> Unit
): WebView {
    var isDestroyed = false

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // TV Remote / D-Pad focus setup
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        keepScreenOn = true
        overScrollMode = View.OVER_SCROLL_NEVER
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        settings.apply {
            // Essential for modern interactive Web Apps & WebSockets
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // Critical for Android TV karaoke: Allow video/audio to auto-play without user touch
            mediaPlaybackRequiresUserGesture = false

            // Layout & Viewport for 1080p / 4K TV screens
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            // Cache & Network
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
        }

        // Enable cookies for session state and auth
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                Log.d(TAG, "WebChromeClient granting permissions: ${request?.resources?.joinToString()}")
                try {
                    request?.grant(request.resources)
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting web permission: ${e.message}")
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(
                        "YimlyWebConsole",
                        "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                    )
                }
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Host room page started loading: $url")
                if (!isDestroyed) {
                    onPageStarted()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "Host room page finished loading: $url")
                if (!isDestroyed) {
                    onPageFinished()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: return false
                Log.d(TAG, "Navigating within WebView: $nextUrl")
                // Keep all navigation inside the TV WebView
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val description = error?.description?.toString() ?: "WebView resource error"
                val isMainFrame = request?.isForMainFrame == true
                Log.w(TAG, "WebView error (mainFrame=$isMainFrame): $description on ${request?.url}")
                if (isMainFrame && !isDestroyed) {
                    onError(description)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.w(TAG, "WebView HTTP error ${errorResponse?.statusCode} on ${request?.url}")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.w(TAG, "WebView SSL warning: $error")
                super.onReceivedSslError(view, handler, error)
            }
        }

        // Intercept TV remote back or media keys if necessary
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                if (canGoBack()) {
                    goBack()
                    return@setOnKeyListener true
                } else {
                    onExitRequested()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }
}
