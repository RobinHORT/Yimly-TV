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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YimlyHostRoomWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageStarted: () -> Unit = {},
    onPageFinished: () -> Unit = {},
    onError: (description: String) -> Unit = {}
) {
    val context = LocalContext.current
    val webView = remember(context) {
        createConfiguredWebView(
            context = context,
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onError = onError
        )
    }

    DisposableEffect(url) {
        Log.i(TAG, "Loading Host Room URL in WebView: $url")
        webView.loadUrl(url)

        onDispose {
            try {
                webView.stopLoading()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping WebView: ${e.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
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
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onError: (description: String) -> Unit
): WebView {
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
                onPageStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "Host room page finished loading: $url")
                onPageFinished()
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
                if (isMainFrame) {
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
                // By default proceed or rely on standard handler
                super.onReceivedSslError(view, handler, error)
            }
        }

        // Intercept TV remote back or media keys if necessary
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (canGoBack()) {
                            goBack()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }
}
